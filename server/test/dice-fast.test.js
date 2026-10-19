import test from "node:test";
import assert from "node:assert/strict";
import { createFastDiceProof, rejectionSampleDie, verifyFastDiceProof } from "../src/dice-fast.js";

const context = {
  matchId: "LPDICEFAST", playerId: "player-1", clientRequestId: "11111111-1111-4111-8111-111111111111",
  eventIndex: 0, previousStateHash: "a".repeat(64), rulesetHash: "b".repeat(64),
};

test("rejection sampling discards 252 through 255 and maps all accepted bytes evenly", () => {
  const rejected = [252, 253, 254, 255, 0];
  assert.equal(rejectionSampleDie(() => rejected.shift()), 1);
  assert.equal(rejected.length, 0);
  const counts = [0, 0, 0, 0, 0, 0];
  for (let byte = 0; byte < 252; byte++) counts[rejectionSampleDie(() => byte) - 1]++;
  assert.deepEqual(counts, [42, 42, 42, 42, 42, 42]);
});

test("local dice receipts reproduce exactly and bind the event, request, seed and result", async () => {
  const proof = await createFastDiceProof(context, "1".repeat(64));
  assert.ok(proof.outcome >= 1 && proof.outcome <= 6);
  assert.equal(await verifyFastDiceProof(proof), true);
  assert.deepEqual(await createFastDiceProof(context, "1".repeat(64)), proof);
  for (const field of ["matchId", "playerId", "clientRequestId", "eventIndex", "previousStateHash", "rulesetHash", "outcome", "serverSeed", "serverCommitment", "proofDigest"]) {
    const altered = { ...proof, [field]: typeof proof[field] === "number" ? proof[field] + 1 : "altered" };
    assert.equal(await verifyFastDiceProof(altered), false, field);
  }
});
