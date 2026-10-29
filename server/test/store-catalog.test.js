import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { assertCatalogCanPublish, validateCatalog, validateCatalogItem } from "../src/store-catalog.js";

const ownDir = path.dirname(fileURLToPath(import.meta.url));
const schema = JSON.parse(await readFile(path.resolve(ownDir, "../../docs/STORE_CATALOG_SCHEMA.json"), "utf8"));

const item = (overrides = {}) => ({
  id: "paw_safari_01",
  category: "PAW",
  name: "Safari Paw",
  description: "A cosmetic test item",
  status: "DRAFT",
  contentReady: true,
  asset: {
    thumbnail: "r2://published/paw_safari_01/v1/thumbnail.png",
    manifest: "r2://published/paw_safari_01/v1/manifest.json",
    version: 1,
    sha256: "a".repeat(64),
    format: "GLB",
  },
  unlock: {
    channels: ["REWARDED_ADS", "GEMS", "PLAY_PRODUCT"],
    rewardedAds: { enabled: true, requiredCompletedAds: 8 },
    gems: { enabled: true, price: 700 },
    playProduct: { enabled: true, productId: "paw_safari_01", productKind: "NON_CONSUMABLE" },
  },
  availability: { worldwide: true, allowedCountries: [], blockedCountries: ["KP"], startsAt: null, endsAt: null },
  catalogVersion: 1,
  ...overrides,
});

test("validator schema ID stays aligned with the canonical JSON Schema", () => {
  const result = validateCatalogItem(item(), { playProducts: ["paw_safari_01"] });
  assert.equal(result.schemaId, schema.$id);
});

test("accepts a draft item with independently enabled rewarded ads, Gems and direct purchase", () => {
  const value = item();
  assert.deepEqual(validateCatalogItem(value, { playProducts: ["paw_safari_01"] }).errors, []);
});

test("rejects direct purchase without an allowlisted Play product", () => {
  const result = validateCatalogItem(item(), { playProducts: [] });
  assert.equal(result.valid, false);
  assert.ok(result.errors.some((error) => error.includes("verified server allowlist")));
});

test("rejects zero Gem price and malformed rewarded-ad threshold", () => {
  const bad = item({ unlock: {
    channels: ["GEMS", "REWARDED_ADS"],
    gems: { enabled: true, price: 0 },
    rewardedAds: { enabled: true, requiredCompletedAds: 0 },
  }});
  const result = validateCatalogItem(bad);
  assert.ok(result.errors.some((error) => error.includes("positive integer price")));
  assert.ok(result.errors.some((error) => error.includes("completed-ad threshold")));
});

test("rejects publication when content is incomplete", () => {
  const result = validateCatalogItem(item({ status: "PUBLISHED", contentReady: false }), { playProducts: ["paw_safari_01"] });
  assert.ok(result.errors.includes("published items require contentReady=true"));
});

test("rejects duplicate IDs in a catalog", () => {
  const result = validateCatalog([item(), item()]);
  assert.equal(result.valid, false);
  assert.ok(result.errors.some((error) => error.includes("duplicate stable item ID")));
});

test("rejects invalid country codes and invalid availability windows", () => {
  const result = validateCatalogItem(item({ availability: {
    worldwide: false, allowedCountries: ["USA"], blockedCountries: ["in"], startsAt: "2026-12-31T00:00:00Z", endsAt: "2026-01-01T00:00:00Z",
  }}));
  assert.ok(result.errors.some((error) => error.includes("country codes")));
  assert.ok(result.errors.some((error) => error.includes("end must be later")));
});

test("rejects malformed schedule timestamps even when only one bound is set", () => {
  const baseline = item().availability;
  const cases = [
    {
      availability: { ...baseline, startsAt: "not-a-date", endsAt: null },
      error: "availability.startsAt must be a valid ISO 8601 date-time or null",
    },
    {
      availability: { ...baseline, startsAt: null, endsAt: "not-a-date" },
      error: "availability.endsAt must be a valid ISO 8601 date-time or null",
    },
    {
      availability: { ...baseline, startsAt: "2026-10-14", endsAt: null },
      error: "availability.startsAt must be a valid ISO 8601 date-time or null",
    },
    {
      availability: { ...baseline, startsAt: "2026-02-30T12:00:00Z", endsAt: null },
      error: "availability.startsAt must be a valid ISO 8601 date-time or null",
    },
  ];

  for (const scenario of cases) {
    const result = validateCatalogItem(item({ availability: scenario.availability }));
    assert.ok(result.errors.includes(scenario.error), result.errors.join("; "));
  }
});

test("publishes only complete, validated catalog records", () => {
  const published = assertCatalogCanPublish(item(), { playProducts: ["paw_safari_01"] });
  assert.equal(published.status, "PUBLISHED");
  assert.throws(() => assertCatalogCanPublish(item({ contentReady: false })), /cannot be published/);
});
