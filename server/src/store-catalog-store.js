import { assertCatalogCanPublish, validateCatalog, validateCatalogItem } from "./store-catalog.js";
import { GOOGLE_PLAY_PRODUCTS } from "./google-play-billing.js";

// Publish only SKUs whose receipt verifier already grants the exact cosmetic.
const verifiedCosmeticProducts = Object.keys(GOOGLE_PLAY_PRODUCTS).filter(
  (productId) => typeof GOOGLE_PLAY_PRODUCTS[productId].cosmeticId === "string",
);

const STATE_KEY = "store-catalog:state:v1";
const SNAPSHOT_PREFIX = "store-catalog:snapshot:v1:";
const MAX_SNAPSHOTS = 100;
const MAX_AUDIT_EVENTS = 250;
const ACCOUNT_ID = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

export class StoreCatalogStore {
  constructor(ctx) {
    this.ctx = ctx;
    this.tail = Promise.resolve();
  }

  fetch(request) {
    const run = this.tail.then(() => this.#handle(request), () => this.#handle(request));
    this.tail = run.catch(() => {});
    return run;
  }

  async #handle(request) {
    const url = new URL(request.url);
    if (request.method === "GET" && url.pathname === "/catalog/current") {
      const state = await this.#loadState();
      return json(200, publicState(state));
    }
    if (request.method === "GET" && url.pathname === "/catalog/admin-state") {
      return json(200, await this.#loadState());
    }
    if (request.method === "GET" && url.pathname === "/catalog/history") {
      const state = await this.#loadState();
      const requested = Number(url.searchParams.get("limit") ?? 30);
      const limit = Number.isSafeInteger(requested) ? Math.max(1, Math.min(100, requested)) : 30;
      const versions = [];
      const first = state.publishedVersion;
      const last = Math.max(1, first - MAX_SNAPSHOTS + 1);
      for (let version = first; version >= last && versions.length < limit; version -= 1) {
        const snapshot = await this.ctx.storage.get(SNAPSHOT_PREFIX + version);
        if (snapshot) versions.push(versionMetadata(snapshot));
      }
      return json(200, {
        revision: state.revision,
        catalogVersion: state.publishedVersion,
        versions,
        audit: state.audit.slice(-MAX_AUDIT_EVENTS).reverse(),
      });
    }

    if (request.method !== "POST") {
      return json(405, { error: "METHOD_NOT_ALLOWED", message: "method not allowed" }, { allow: "GET, POST" });
    }

    let body;
    try { body = await request.json(); }
    catch { return json(400, { error: "INVALID_JSON", message: "request body must be valid JSON" }); }
    if (!isRecord(body)) return json(400, { error: "INVALID_BODY", message: "request body must be an object" });

    if (url.pathname === "/catalog/draft/save") return this.#saveDraft(body);
    if (url.pathname === "/catalog/publish") return this.#publish(body);
    if (url.pathname === "/catalog/archive") return this.#archive(body);
    if (url.pathname === "/catalog/rollback") return this.#rollback(body);
    return json(404, { error: "NOT_FOUND", message: "route not found" });
  }

  async #loadState() {
    const stored = await this.ctx.storage.get(STATE_KEY);
    if (isRecord(stored) && Number.isSafeInteger(stored.revision) &&
        Number.isSafeInteger(stored.publishedVersion) && Array.isArray(stored.items) &&
        isRecord(stored.drafts) && Array.isArray(stored.audit)) {
      return stored;
    }
    return {
      revision: 0,
      publishedVersion: 0,
      items: [],
      drafts: {},
      updatedAt: null,
      audit: [],
    };
  }

  async #saveDraft(body) {
    const state = await this.#loadState();
    const invalid = validateMutation(body, state);
    if (invalid) return invalid;
    if (!isRecord(body.item) || !["DRAFT", "PREVIEW", "SCHEDULED"].includes(body.item.status)) {
      return json(400, { error: "CATALOG_DRAFT_STATUS_INVALID", message: "draft status must be DRAFT, PREVIEW, or SCHEDULED" });
    }
    const item = body.item;
    // Drafts are structurally checked, but are not checkout authority. A future
    // product ID can be saved for review; publish remains blocked until the
    // purchase verifier explicitly supports cosmetic entitlements.
    const draftPlayProducts = typeof item.unlock?.playProduct?.productId === "string"
      ? [item.unlock.playProduct.productId] : [];
    const validation = validateCatalogItem(item, { playProducts: draftPlayProducts });
    if (!validation.valid) {
      return json(422, { error: "CATALOG_ITEM_INVALID", message: "draft failed validation", details: validation.errors });
    }

    const next = clone(state);
    next.drafts[item.id] = clone(item);
    next.revision += 1;
    next.updatedAt = new Date().toISOString();
    appendAudit(next, {
      revision: next.revision,
      catalogVersion: next.publishedVersion,
      action: "DRAFT_SAVED",
      itemId: item.id,
      actorId: body.actorId.toLowerCase(),
      reason: body.reason.trim(),
      at: next.updatedAt,
    });
    await this.ctx.storage.put(STATE_KEY, next);
    return json(200, {
      ok: true,
      revision: next.revision,
      catalogVersion: next.publishedVersion,
      draft: clone(next.drafts[item.id]),
    });
  }

  async #publish(body) {
    const state = await this.#loadState();
    const invalid = validateMutation(body, state);
    if (invalid) return invalid;
    const itemId = validItemId(body.itemId);
    if (!itemId) return json(400, { error: "CATALOG_ITEM_ID_INVALID", message: "itemId is invalid" });
    const draft = state.drafts[itemId];
    if (!draft) return json(404, { error: "CATALOG_DRAFT_NOT_FOUND", message: "save a draft before publishing" });
    if (draft.unlock?.channels?.includes("PLAY_PRODUCT")) {
      const productId = draft.unlock?.playProduct?.productId;
      if (!verifiedCosmeticProducts.includes(productId) ||
          GOOGLE_PLAY_PRODUCTS[productId]?.cosmeticId !== draft.id) {
        return json(409, {
          error: "CATALOG_PLAY_PURCHASE_NOT_READY",
          message: "the Play SKU must already be verified and grant ownership of this exact cosmetic",
        });
      }
    }
    if (draft.category === "BUNDLE") {
      return json(409, {
        error: "CATALOG_BUNDLE_NOT_READY",
        message: "bundle publication is blocked until atomic multi-item entitlement grants are implemented",
      });
    }
    const unsupportedChannels = (draft.unlock?.channels ?? []).filter((channel) => ["LEVEL", "EVENT"].includes(channel));
    if (unsupportedChannels.length) {
      return json(409, {
        error: "CATALOG_UNLOCK_CHANNEL_NOT_READY",
        message: "LEVEL and EVENT unlocks require server-authoritative progression/event verification before publication",
        details: unsupportedChannels,
      });
    }

    const nextVersion = state.publishedVersion + 1;
    let published;
    try {
      published = assertCatalogCanPublish({ ...clone(draft), status: "PUBLISHED", catalogVersion: nextVersion }, { playProducts: verifiedCosmeticProducts });
    } catch (error) {
      return json(422, {
        error: error?.code ?? "CATALOG_ITEM_INVALID",
        message: error?.message ?? "catalog item cannot be published",
        details: Array.isArray(error?.details) ? error.details : [],
      });
    }
    const items = state.items.filter((item) => item.id !== itemId);
    items.push(published);
    const validation = validateCatalog(items, { playProducts: verifiedCosmeticProducts });
    if (!validation.valid) {
      return json(422, { error: "CATALOG_INVALID", message: "published catalog failed validation", details: validation.errors });
    }
    return this.#publishSnapshot(state, body, items, "ITEM_PUBLISHED", itemId, nextVersion, (next) => {
      delete next.drafts[itemId];
    });
  }

  async #archive(body) {
    const state = await this.#loadState();
    const invalid = validateMutation(body, state);
    if (invalid) return invalid;
    const itemId = validItemId(body.itemId);
    if (!itemId) return json(400, { error: "CATALOG_ITEM_ID_INVALID", message: "itemId is invalid" });
    const existing = state.items.find((item) => item.id === itemId);
    if (!existing || existing.status === "ARCHIVED") {
      return json(404, { error: "PUBLISHED_CATALOG_ITEM_NOT_FOUND", message: "published catalog item was not found" });
    }
    const nextVersion = state.publishedVersion + 1;
    const items = state.items.map((item) => item.id === itemId
      ? { ...clone(item), status: "ARCHIVED", catalogVersion: nextVersion }
      : clone(item));
    return this.#publishSnapshot(state, body, items, "ITEM_ARCHIVED", itemId, nextVersion, (next) => {
      delete next.drafts[itemId];
    });
  }

  async #rollback(body) {
    const state = await this.#loadState();
    const invalid = validateMutation(body, state);
    if (invalid) return invalid;
    if (!Number.isSafeInteger(body.targetVersion) || body.targetVersion < 1) {
      return json(400, { error: "CATALOG_TARGET_VERSION_INVALID", message: "targetVersion must be a positive integer" });
    }
    const snapshot = await this.ctx.storage.get(SNAPSHOT_PREFIX + body.targetVersion);
    if (!snapshot) return json(404, { error: "CATALOG_VERSION_NOT_FOUND", message: "requested catalog version is not retained" });
    const nextVersion = state.publishedVersion + 1;
    const items = clone(snapshot.items);
    const restoredIds = new Set(items.map((item) => item.id));
    // A rollback must not erase IDs introduced after the target snapshot. Keep
    // those records as archived tombstones so their ownership remains stable and
    // stale clients cannot fall through to a legacy ID with the same value.
    for (const current of state.items) {
      if (!restoredIds.has(current.id)) {
        items.push({ ...clone(current), status: "ARCHIVED", catalogVersion: nextVersion });
        restoredIds.add(current.id);
      }
    }
    const validation = validateCatalog(items, { playProducts: verifiedCosmeticProducts });
    if (!validation.valid) {
      return json(409, { error: "CATALOG_ROLLBACK_INVALID", message: "saved catalog version failed current validation", details: validation.errors });
    }
    return this.#publishSnapshot(state, body, items, "CATALOG_ROLLED_BACK", null, nextVersion, () => {}, {
      rolledBackFromVersion: body.targetVersion,
    });
  }

  async #publishSnapshot(state, body, items, action, itemId, nextVersion, mutateNext, extra = {}) {
    const next = clone(state);
    next.revision += 1;
    next.publishedVersion = nextVersion;
    next.items = clone(items);
    next.updatedAt = new Date().toISOString();
    mutateNext(next);
    appendAudit(next, {
      revision: next.revision,
      catalogVersion: nextVersion,
      action,
      ...(itemId ? { itemId } : {}),
      actorId: body.actorId.toLowerCase(),
      reason: body.reason.trim(),
      at: next.updatedAt,
      ...extra,
    });
    const snapshot = {
      version: nextVersion,
      createdAt: next.updatedAt,
      actorId: body.actorId.toLowerCase(),
      action,
      reason: body.reason.trim(),
      ...(itemId ? { itemId } : {}),
      ...extra,
      items: clone(items),
    };
    await this.ctx.storage.transaction(async (transaction) => {
      await transaction.put(SNAPSHOT_PREFIX + nextVersion, snapshot);
      await transaction.put(STATE_KEY, next);
      if (nextVersion > MAX_SNAPSHOTS) {
        await transaction.delete(SNAPSHOT_PREFIX + (nextVersion - MAX_SNAPSHOTS));
      }
    });
    return json(200, {
      ok: true,
      revision: next.revision,
      catalogVersion: next.publishedVersion,
      action,
      ...(itemId ? { itemId } : {}),
      ...extra,
    });
  }
}

function validateMutation(body, state) {
  if (!Number.isSafeInteger(body.expectedRevision) || body.expectedRevision < 0) {
    return json(400, { error: "CATALOG_EXPECTED_REVISION_REQUIRED", message: "expectedRevision must be a non-negative integer" });
  }
  if (body.expectedRevision !== state.revision) {
    return json(409, {
      error: "CATALOG_REVISION_CONFLICT",
      message: "catalog changed; refresh the admin state before retrying",
      revision: state.revision,
      catalogVersion: state.publishedVersion,
    });
  }
  if (typeof body.actorId !== "string" || !ACCOUNT_ID.test(body.actorId)) {
    return json(400, { error: "CATALOG_ACTOR_INVALID", message: "authenticated admin account is invalid" });
  }
  if (typeof body.reason !== "string" || body.reason.trim().length < 3 || body.reason.trim().length > 200) {
    return json(400, { error: "CATALOG_AUDIT_REASON_REQUIRED", message: "reason must contain 3 to 200 characters" });
  }
  return null;
}

function validItemId(value) {
  return typeof value === "string" && /^[a-z0-9][a-z0-9_-]{2,63}$/.test(value) ? value : null;
}

function publicState(state) {
  return {
    revision: state.revision,
    catalogVersion: state.publishedVersion,
    items: clone(state.items),
    updatedAt: state.updatedAt,
  };
}

function versionMetadata(snapshot) {
  return {
    version: snapshot.version,
    createdAt: snapshot.createdAt,
    actorId: snapshot.actorId,
    action: snapshot.action,
    reason: snapshot.reason,
    ...(snapshot.itemId ? { itemId: snapshot.itemId } : {}),
    ...(Number.isSafeInteger(snapshot.rolledBackFromVersion)
      ? { rolledBackFromVersion: snapshot.rolledBackFromVersion } : {}),
  };
}

function appendAudit(state, event) {
  state.audit.push(event);
  if (state.audit.length > MAX_AUDIT_EVENTS) state.audit.splice(0, state.audit.length - MAX_AUDIT_EVENTS);
}

function isRecord(value) {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}

function clone(value) {
  return JSON.parse(JSON.stringify(value));
}

function json(status, body, headers = {}) {
  return Response.json(body, {
    status,
    headers: {
      "cache-control": "no-store",
      "x-content-type-options": "nosniff",
      ...headers,
    },
  });
}
