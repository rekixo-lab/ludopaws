import { httpError } from "./crypto.js";

/** Reject malformed JSON types instead of coercing null, false or "" to pawn 0. */
export function requireMoveIndexes(body) {
  const tokenIndex = body?.tokenIndex;
  const eventIndex = body?.eventIndex;
  if (!Number.isInteger(tokenIndex) || tokenIndex < 0 || tokenIndex > 3) {
    throw httpError(400, "INVALID_TOKEN_INDEX", "tokenIndex must be 0 through 3");
  }
  if (!Number.isSafeInteger(eventIndex) || eventIndex < 0) {
    throw httpError(400, "INVALID_EVENT_INDEX", "eventIndex must be a non-negative safe integer");
  }
  return { tokenIndex, eventIndex };
}
