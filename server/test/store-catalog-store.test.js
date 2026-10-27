import test from "node:test";
import assert from "node:assert/strict";
import { StoreCatalogStore } from "../src/store-catalog-store.js";

const ADMIN_ID = "5b4f7a84-21e7-4f84-8fb3-c5986dddc11a";

class MemoryStorage {
  constructor() { this.values = new Map(); }
  async get(key) { return this.values.get(key); }
  async put(key, value) { this.values.set(key, structuredClone(value)); }
  async delete(key) { return this.values.delete(key); }
  async transaction(callback) {
    const staged = new Map([...this.values].map(([key, value]) => [key, structuredClone(value)]));
    const transaction = {
      get: async (key) => staged.get(key),
      put: async (key, value) => { staged.set(key, structuredClone(value)); },
      delete: async (key) => staged.delete(key),
    };
    const result = await callback(transaction);
    this.values = staged;
    return result;
  }
}

function createStore() {
  const storage = new MemoryStorage();
  return { store: new StoreCatalogStore({ storage }), storage };
}

function item(overrides = {}) {
  return {
    id: "paw_safari_01",
    category: "PAW",
    name: "Safari Paw",
    description: "A test cosmetic",
    status: "DRAFT",
    contentReady: true,
    asset: {
      thumbnail: "r2://published/paw_safari_01/v1/thumbnail.png",
      manifest: "r2://published/paw_safari_01/v1/manifest.json",
      version: 1,
      sha256: "a".repeat(64),
      format: "GLB",
    },
    unlock: { channels: ["GEMS"], gems: { enabled: true, price: 250 } },
    availability: {
      worldwide: true, allowedCountries: [], blockedCountries: [],
      startsAt: null, endsAt: null,
    },
    catalogVersion: 1,
    ...overrides,
  };
}

function mutation(extra = {}) {
  return {
    expectedRevision: 0,
    actorId: ADMIN_ID,
    reason: "Initial catalog test",
    ...extra,
  };
}

async function call(store, path, body, method = "POST") {
  const request = new Request("https://catalog.test" + path, {
    method,
    headers: body ? { "content-type": "application/json" } : {},
    ...(body ? { body: JSON.stringify(body) } : {}),
  });
  const response = await store.fetch(request);
  return { response, body: response.status === 204 ? null : await response.json() };
}

test("starts with an empty, version-zero catalog", async () => {
  const { store } = createStore();
  const { response, body } = await call(store, "/catalog/current", null, "GET");
  assert.equal(response.status, 200);
  assert.deepEqual(body, { revision: 0, catalogVersion: 0, items: [], updatedAt: null });
});

test("saving a draft is audited and does not expose it as published content", async () => {
  const { store } = createStore();
  const { response, body } = await call(store, "/catalog/draft/save", mutation({ item: item() }));
  assert.equal(response.status, 200);
  assert.equal(body.revision, 1);
  assert.equal(body.catalogVersion, 0);

  const current = await call(store, "/catalog/current", null, "GET");
  assert.equal(current.body.items.length, 0);
  const history = await call(store, "/catalog/history?limit=10", null, "GET");
  assert.equal(history.body.audit[0].action, "DRAFT_SAVED");
  assert.equal(history.body.audit[0].actorId, ADMIN_ID);
  assert.equal(history.body.audit[0].reason, "Initial catalog test");
});

test("stale admin writes fail with a revision conflict and preserve stored draft", async () => {
  const { store } = createStore();
  const first = await call(store, "/catalog/draft/save", mutation({ item: item() }));
  assert.equal(first.response.status, 200);

  const stale = await call(store, "/catalog/draft/save", mutation({
    item: item({ name: "Stale overwrite" }),
  }));
  assert.equal(stale.response.status, 409);
  assert.equal(stale.body.error, "CATALOG_REVISION_CONFLICT");

  const history = await call(store, "/catalog/history", null, "GET");
  assert.equal(history.body.audit.length, 1);
  assert.equal(history.body.audit[0].action, "DRAFT_SAVED");
});

test("publishes a valid draft to an immutable version and lists version metadata", async () => {
  const { store } = createStore();
  await call(store, "/catalog/draft/save", mutation({ item: item() }));
  const published = await call(store, "/catalog/publish", mutation({
    expectedRevision: 1, itemId: "paw_safari_01",
  }));
  assert.equal(published.response.status, 200);
  assert.equal(published.body.catalogVersion, 1);
  assert.equal(published.body.action, "ITEM_PUBLISHED");

  const current = await call(store, "/catalog/current", null, "GET");
  assert.equal(current.body.items.length, 1);
  assert.equal(current.body.items[0].status, "PUBLISHED");
  assert.equal(current.body.items[0].catalogVersion, 1);

  const history = await call(store, "/catalog/history?limit=10", null, "GET");
  assert.equal(history.body.versions.length, 1);
  assert.equal(history.body.versions[0].version, 1);
  assert.equal(history.body.versions[0].action, "ITEM_PUBLISHED");
});

test("rejects incomplete content and direct Play checkout before publication", async () => {
  const { store } = createStore();
  const badContent = item({ contentReady: false });
  await call(store, "/catalog/draft/save", mutation({ item: badContent }));
  const publishBadContent = await call(store, "/catalog/publish", mutation({
    expectedRevision: 1, itemId: badContent.id,
  }));
  assert.equal(publishBadContent.response.status, 422);
  assert.equal(publishBadContent.body.error, "CATALOG_ITEM_INVALID");

  const directPurchase = item({
    id: "paw_shop_01",
    name: "Shop Paw",
    unlock: { channels: ["PLAY_PRODUCT"], playProduct: {
      enabled: true, productId: "paw_shop_01", productKind: "NON_CONSUMABLE",
    } },
  });
  await call(store, "/catalog/draft/save", mutation({ expectedRevision: 1, item: directPurchase }));
  const publishDirectPurchase = await call(store, "/catalog/publish", mutation({
    expectedRevision: 2, itemId: directPurchase.id,
  }));
  assert.equal(publishDirectPurchase.response.status, 409);
  assert.equal(publishDirectPurchase.body.error, "CATALOG_PLAY_PURCHASE_NOT_READY");
});

test("blocks bundle, Level, and Event publication until their grant authorities exist", async () => {
  const { store } = createStore();

  const bundle = item({
    id: "bundle_safari_01",
    category: "BUNDLE",
    name: "Safari Bundle",
  });
  await call(store, "/catalog/draft/save", mutation({ item: bundle }));
  const publishBundle = await call(store, "/catalog/publish", mutation({
    expectedRevision: 1, itemId: bundle.id,
  }));
  assert.equal(publishBundle.response.status, 409);
  assert.equal(publishBundle.body.error, "CATALOG_BUNDLE_NOT_READY");

  const levelItem = item({
    id: "paw_level_01",
    unlock: { channels: ["LEVEL"], level: { enabled: true, minimumLevel: 3 } },
  });
  await call(store, "/catalog/draft/save", mutation({ expectedRevision: 1, item: levelItem }));
  const publishLevel = await call(store, "/catalog/publish", mutation({
    expectedRevision: 2, itemId: levelItem.id,
  }));
  assert.equal(publishLevel.response.status, 409);
  assert.equal(publishLevel.body.error, "CATALOG_UNLOCK_CHANNEL_NOT_READY");

  const eventItem = item({
    id: "paw_event_01",
    unlock: { channels: ["EVENT"], event: { enabled: true, eventKey: "halloween-2026" } },
  });
  await call(store, "/catalog/draft/save", mutation({ expectedRevision: 2, item: eventItem }));
  const publishEvent = await call(store, "/catalog/publish", mutation({
    expectedRevision: 3, itemId: eventItem.id,
  }));
  assert.equal(publishEvent.response.status, 409);
  assert.equal(publishEvent.body.error, "CATALOG_UNLOCK_CHANNEL_NOT_READY");

  const current = await call(store, "/catalog/current", null, "GET");
  assert.equal(current.body.catalogVersion, 0);
  assert.equal(current.body.items.length, 0);
});

test("published state and snapshot persistence are atomic on storage failure", async () => {
  const { store, storage } = createStore();
  await call(store, "/catalog/draft/save", mutation({ item: item() }));
  const originalTransaction = storage.transaction.bind(storage);
  storage.transaction = async () => { throw new Error("simulated durable storage failure"); };

  await assert.rejects(
    () => call(store, "/catalog/publish", mutation({
      expectedRevision: 1, itemId: "paw_safari_01",
    })),
    /simulated durable storage failure/,
  );

  storage.transaction = originalTransaction;
  const current = await call(store, "/catalog/current", null, "GET");
  assert.equal(current.body.catalogVersion, 0);
  assert.equal(current.body.items.length, 0);
  const history = await call(store, "/catalog/history", null, "GET");
  assert.equal(history.body.versions.length, 0);
  assert.equal(history.body.audit.length, 1);
  assert.equal(history.body.audit[0].action, "DRAFT_SAVED");
});

test("archive excludes an item, and rollback restores a prior snapshot as a new version", async () => {
  const { store } = createStore();
  await call(store, "/catalog/draft/save", mutation({ item: item() }));
  await call(store, "/catalog/publish", mutation({
    expectedRevision: 1, itemId: "paw_safari_01",
  }));
  const archived = await call(store, "/catalog/archive", mutation({
    expectedRevision: 2, itemId: "paw_safari_01", reason: "Temporarily retire item",
  }));
  assert.equal(archived.response.status, 200);
  assert.equal(archived.body.catalogVersion, 2);
  const afterArchive = await call(store, "/catalog/current", null, "GET");
  assert.equal(afterArchive.body.items[0].status, "ARCHIVED");

  const rolledBack = await call(store, "/catalog/rollback", mutation({
    expectedRevision: 3, targetVersion: 1, reason: "Restore previous catalog",
  }));
  assert.equal(rolledBack.response.status, 200);
  assert.equal(rolledBack.body.catalogVersion, 3);
  const restored = await call(store, "/catalog/current", null, "GET");
  assert.equal(restored.body.items[0].status, "PUBLISHED");

  const history = await call(store, "/catalog/history?limit=10", null, "GET");
  assert.deepEqual(history.body.versions.map((version) => version.version), [3, 2, 1]);
  assert.equal(history.body.versions[0].rolledBackFromVersion, 1);
});

test("rollback preserves newer item IDs as archived tombstones", async () => {
  const { store } = createStore();
  await call(store, "/catalog/draft/save", mutation({ item: item() }));
  await call(store, "/catalog/publish", mutation({
    expectedRevision: 1, itemId: "paw_safari_01",
  }));

  const later = item({ id: "paw_newer_01", name: "Newer Paw" });
  await call(store, "/catalog/draft/save", mutation({
    expectedRevision: 2, item: later,
  }));
  await call(store, "/catalog/publish", mutation({
    expectedRevision: 3, itemId: "paw_newer_01",
  }));

  const rolledBack = await call(store, "/catalog/rollback", mutation({
    expectedRevision: 4, targetVersion: 1, reason: "Restore initial catalog",
  }));
  assert.equal(rolledBack.response.status, 200);
  assert.equal(rolledBack.body.catalogVersion, 3);

  const current = await call(store, "/catalog/current", null, "GET");
  assert.equal(current.body.items.length, 2);
  assert.equal(current.body.items.find(entry => entry.id === "paw_safari_01").status, "PUBLISHED");
  const retained = current.body.items.find(entry => entry.id === "paw_newer_01");
  assert.equal(retained.status, "ARCHIVED");
  assert.equal(retained.catalogVersion, 3);
});

test("requires audit reason, authenticated actor identity, and a valid rollback target", async () => {
  const { store } = createStore();
  const noReason = await call(store, "/catalog/draft/save", mutation({ item: item(), reason: " " }));
  assert.equal(noReason.response.status, 400);
  assert.equal(noReason.body.error, "CATALOG_AUDIT_REASON_REQUIRED");

  const badActor = await call(store, "/catalog/draft/save", mutation({ item: item(), actorId: "admin" }));
  assert.equal(badActor.response.status, 400);
  assert.equal(badActor.body.error, "CATALOG_ACTOR_INVALID");

  const badTarget = await call(store, "/catalog/rollback", mutation({ targetVersion: 999 }));
  assert.equal(badTarget.response.status, 404);
  assert.equal(badTarget.body.error, "CATALOG_VERSION_NOT_FOUND");
});
