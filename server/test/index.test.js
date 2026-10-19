import test from "node:test";
import assert from "node:assert/strict";

import worker from "../src/index.js";

async function body(response) {
  return {
    response,
    json: await response.json(),
  };
}

test("public responses include hardened security headers", async () => {
  const response =
    await worker.fetch(
      new Request(
        "https://ludoproof.example/",
      ),
      {},
    );

  assert.equal(response.status, 200);
  assert.equal(
    response.headers.get(
      "x-content-type-options",
    ),
    "nosniff",
  );
  assert.equal(
    response.headers.get(
      "x-frame-options",
    ),
    "DENY",
  );
  assert.equal(
    response.headers.get(
      "referrer-policy",
    ),
    "no-referrer",
  );
  assert.match(
    response.headers.get(
      "strict-transport-security",
    ) ?? "",
    /max-age=31536000/,
  );
});

test("match creation rejects the wrong method", async () => {
  const result =
    await body(
      await worker.fetch(
        new Request(
          "https://ludoproof.example/api/matches",
        ),
        {},
      ),
    );

  assert.equal(
    result.response.status,
    405,
  );
  assert.equal(
    result.response.headers.get("allow"),
    "POST",
  );
  assert.equal(
    result.json.error,
    "METHOD_NOT_ALLOWED",
  );
});

test("POST endpoints require application/json", async () => {
  const result =
    await body(
      await worker.fetch(
        new Request(
          "https://ludoproof.example/api/matches",
          {
            method: "POST",
            headers: {
              "content-type":
                "text/plain",
            },
            body: "{}",
          },
        ),
        {},
      ),
    );

  assert.equal(
    result.response.status,
    415,
  );
  assert.equal(
    result.json.error,
    "UNSUPPORTED_MEDIA_TYPE",
  );
});

test("oversized JSON bodies fail before allocating match storage", async () => {
  const oversized =
    JSON.stringify({
      displayName:
        "x".repeat(9 * 1024),
    });

  const result =
    await body(
      await worker.fetch(
        new Request(
          "https://ludoproof.example/api/matches",
          {
            method: "POST",
            headers: {
              "content-type":
                "application/json",
            },
            body: oversized,
          },
        ),
        {},
      ),
    );

  assert.equal(
    result.response.status,
    413,
  );
  assert.equal(
    result.json.error,
    "REQUEST_TOO_LARGE",
  );
});


test("readiness checks local bindings and does not call an external dice provider", async (t) => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => { throw new Error("readiness must not use the network"); };
  t.after(() => { globalThis.fetch = originalFetch; });
  const request = () => new Request("https://api.test/ready");
  const missing = await worker.fetch(request(), {});
  assert.equal(missing.status, 503);
  const configured = {
    LUDOPROOF_SESSION_HMAC_KEY: "session-key-".repeat(4),
    LUDOPROOF_MATCHES: {}, LUDOPROOF_API_GATE: {}, LUDOPROOF_LEADERBOARD: {},
    LUDOPROOF_MATCHMAKER: {}, LUDOPROOF_FRIENDS: {},
  };
  const ready = await worker.fetch(request(), configured);
  const json = await ready.json();
  assert.equal(ready.status, 200);
  assert.equal(json.ready, true);
  assert.equal(json.diceProtocol, "ludopaws-server-dice-v1");
  assert.equal(json.productionClaim, false);
  assert.equal(Object.keys(json.checks).some((key) => key.startsWith("entronex")), false);
  for (const key of Object.keys(configured)) {
    const env = { ...configured }; delete env[key];
    assert.equal((await worker.fetch(request(), env)).status, 503, key);
  }
});
