import test from "node:test";
import assert from "node:assert/strict";
import { compactRollHistory } from "../src/match-events.js";

test("public last-roll projection stays bounded and excludes internal credentials and receipts", () => {
  const event = { eventIndex: 99, eventId: "roll:99", playerId: "player", outcome: 6,
    randomOutcome: 3, openingRollApplied: true, moveTokenIndex: 2, captures: 1,
    proofDigest: "d".repeat(64), clientRequestId: "internal-request", serverSeed: "internal-seed",
    tokenAuthHash: "private-token", proof: { serverSeed: "internal-proof" } };
  const compact = compactRollHistory({ history: [...Array(99).fill({ eventIndex: 1 }), event],
    rollRequests: { secret: "private" } });
  assert.equal(compact.history.length, 1);
  assert.equal(compact.lastRoll.eventIndex, 99);
  assert.equal(compact.lastRoll.randomOutcome, 3);
  assert.equal(compact.lastRoll.moveTokenIndex, 2);
  assert.equal(compact.lastRoll.captures, 1);
  assert.equal(JSON.stringify(compact).includes("internal-"), false);
  assert.equal(JSON.stringify(compact).includes("private"), false);
  assert.ok(JSON.stringify(compact).length < 1_000);
  assert.deepEqual(compactRollHistory({}), { history: [], lastRoll: null });
});
