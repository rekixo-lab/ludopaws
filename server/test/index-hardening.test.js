import test from "node:test";
import assert from "node:assert/strict";

import worker from "../src/index-hardening.js";

function gateBinding() {
  return {
    idFromName() {
      return "gate";
    },
    get() {
      return {
        async fetch() {
          return Response.json({
            allowed: true,
          });
        },
      };
    },
  };
}

function receiptBinding(
  receiptBody = {
    protocol:
      "ludoproof-match-receipt-v1",
    matchId:
      "LPABCDEFGH",
    sealed: true,
    receiptDigest:
      "a".repeat(64),
  },
) {
  return {
    idFromName(matchId) {
      return matchId;
    },
    get(id) {
      return {
        async fetch(request) {
          assert.equal(
            id,
            "LPABCDEFGH",
          );
          assert.equal(
            new URL(
              request.url,
            ).pathname,
            "/receipt",
          );
          return Response.json(
            receiptBody,
          );
        },
      };
    },
  };
}

function readyEnv() {
  return {
    LUDOPROOF_SESSION_HMAC_KEY: "lp_test_session_hmac_key_1234567890abcdef",
    LUDOPROOF_MATCHES: {},
    LUDOPROOF_API_GATE: gateBinding(),
    LUDOPROOF_LEADERBOARD: {},
    LUDOPROOF_MATCHMAKER: {},
    LUDOPROOF_FRIENDS: {},
    LUDOPROOF_RECEIPTS: receiptBinding(),
  };
}

async function body(response) {
  return {
    response,
    json:
      await response.json(),
  };
}

test(
  "ready checks native backend bindings without upstream dice probes",
  async () => {
    const env = readyEnv();
    const request = () => new Request("https://ludopaws.example/ready");
    const [first, second] = await Promise.all([
      body(await worker.fetch(request(), env)),
      body(await worker.fetch(request(), env)),
    ]);
    assert.equal(first.response.status, 200);
    assert.equal(second.response.status, 200);
    assert.equal(first.json.ready, true);
    assert.equal(first.json.checks.receiptArchiveConfigured, true);
    assert.equal(first.json.checks.sessionKeyConfigured, true);
  },
);

test(
  "production readiness fails closed when receipt archive binding is missing",
  async () => {
    const env = readyEnv();
    delete env.LUDOPROOF_RECEIPTS;

    const result =
      await body(
        await worker.fetch(
          new Request(
            "https://ludoproof.example/ready",
          ),
          env,
        ),
      );

    assert.equal(
      result.response.status,
      503,
    );
    assert.equal(
      result.json.ready,
      false,
    );
    assert.equal(
      result.json.checks
        .receiptArchiveConfigured,
      false,
    );
  },
);

test(
  "public match receipt route is rate limited and returns hardened proof evidence",
  async () => {
    const env = {
      LUDOPROOF_API_GATE:
        gateBinding(),
      LUDOPROOF_RECEIPTS:
        receiptBinding(),
    };
    const result =
      await body(
        await worker.fetch(
          new Request(
            "https://ludoproof.example/api/matches/LPABCDEFGH/receipt",
            {
              headers: {
                "cf-connecting-ip":
                  "203.0.113.10",
              },
            },
          ),
          env,
        ),
      );

    assert.equal(
      result.response.status,
      200,
    );
    assert.equal(
      result.json.sealed,
      true,
    );
    assert.equal(
      result.response.headers.get(
        "x-frame-options",
      ),
      "DENY",
    );
    assert.equal(
      result.response.headers.get(
        "cache-control",
      ),
      "no-store",
    );
  },
);

test(
  "public receipt endpoint rejects mutations",
  async () => {
    const result =
      await body(
        await worker.fetch(
          new Request(
            "https://ludoproof.example/api/matches/LPABCDEFGH/receipt",
            {
              method: "POST",
            },
          ),
          {
            LUDOPROOF_API_GATE:
              gateBinding(),
            LUDOPROOF_RECEIPTS:
              receiptBinding(),
          },
        ),
      );

    assert.equal(
      result.response.status,
      405,
    );
    assert.equal(
      result.response.headers.get(
        "allow",
      ),
      "GET",
    );
    assert.equal(
      result.json.error,
      "METHOD_NOT_ALLOWED",
    );
  },
);
