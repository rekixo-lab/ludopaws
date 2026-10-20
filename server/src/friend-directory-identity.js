// Friend identity validation, pair canonicalization and public presence formatting.
import { httpError, sha256Hex } from "./crypto.js";
const FRIEND_ID =
  /^LPF-[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}$/;
const MATCH_ID =
  /^LP[A-Z2-9]{8}$/;
export const PRESENCE_TTL_MS = 75 * 1000;

export function normalizeFriendId(
  value,
) {
  const normalized =
    String(
      value ??
        "",
    )
      .trim()
      .toUpperCase();
  if (
    !FRIEND_ID.test(
      normalized,
    )
  ) {
    throw httpError(
      400,
      "INVALID_FRIEND_ID",
      "Friend ID must look like LPF-ABCD-EFGH-JKLM",
    );
  }
  return normalized;
}

export function normalizeMatchId(
  value,
) {
  const normalized =
    String(
      value ??
        "",
    )
      .trim()
      .toUpperCase();
  if (
    !MATCH_ID.test(
      normalized,
    )
  ) {
    throw httpError(
      400,
      "INVALID_MATCH_ID",
      "invalid LudoProof match ID",
    );
  }
  return normalized;
}

export function normalizeActionId(
  value,
  prefix,
) {
  const normalized =
    String(
      value ??
        "",
    )
      .trim()
      .toUpperCase();
  const pattern =
    new RegExp(
      "^" +
        prefix +
        "-[A-F0-9]{20}$",
    );
  if (
    !pattern.test(
      normalized,
    )
  ) {
    throw httpError(
      400,
      "INVALID_ACTION_ID",
      "invalid friend action ID",
    );
  }
  return normalized;
}

export function canonicalPair(
  first,
  second,
) {
  return first <
    second
    ? [
        first,
        second,
      ]
    : [
        second,
        first,
      ];
}

export function publicProfile(
  row,
  now,
) {
  const lastSeenAt =
    Number(
      row.last_seen_at ??
        0,
    );
  return {
    friendId:
      String(
        row.friend_id,
      ),
    displayName:
      String(
        row.display_name,
      ),
    online:
      lastSeenAt +
        PRESENCE_TTL_MS >
      now,
    lastSeenAt,
  };
}

export async function friendshipRequestId(
  senderId,
  receiverId,
) {
  const digest =
    await sha256Hex(
      "ludoproof:friend-request:v1:" +
        senderId +
        ":" +
        receiverId,
    );
  return (
    "FRQ-" +
    digest
      .slice(
        0,
        20,
      )
      .toUpperCase()
  );
}

export async function friendInviteId(
  clientRequestId,
) {
  const digest =
    await sha256Hex(
      "ludoproof:friend-invite:v1:" +
        clientRequestId,
    );
  return (
    "FIV-" +
    digest
      .slice(
        0,
        20,
      )
      .toUpperCase()
  );
}

export async function friendTokenHash(
  token,
) {
  return sha256Hex(
    "ludoproof:friend-token-hash:v1:" +
      token,
  );
}
