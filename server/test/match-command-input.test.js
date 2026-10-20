import test from "node:test";
import assert from "node:assert/strict";
import { requireMoveIndexes } from "../src/match-command-input.js";

test("valid move indexes are kept exact", () => {
  assert.deepEqual(requireMoveIndexes({ tokenIndex: 0, eventIndex: 0 }), { tokenIndex: 0, eventIndex: 0 });
  assert.deepEqual(requireMoveIndexes({ tokenIndex: 3, eventIndex: Number.MAX_SAFE_INTEGER }), {
    tokenIndex: 3, eventIndex: Number.MAX_SAFE_INTEGER,
  });
});

test("MOVE rejects coercible, missing and out-of-bounds pawn indexes", () => {
  for (const invalid of [null, false, true, "", "0", "2", 0.5, -1, 4, [], {}, undefined]) {
    assert.throws(
      () => requireMoveIndexes({ tokenIndex: invalid, eventIndex: 0 }),
      (error) => error.status === 400 && error.code === "INVALID_TOKEN_INDEX",
      `tokenIndex ${JSON.stringify(invalid)} must not be accepted`,
    );
  }
});

test("MOVE rejects coercible, missing and unsafe event indexes", () => {
  for (const invalid of [null, false, true, "", "0", "2", 0.5, -1, [], {}, undefined, Number.MAX_SAFE_INTEGER + 1]) {
    assert.throws(
      () => requireMoveIndexes({ tokenIndex: 0, eventIndex: invalid }),
      (error) => error.status === 400 && error.code === "INVALID_EVENT_INDEX",
      `eventIndex ${JSON.stringify(invalid)} must not be accepted`,
    );
  }
});
