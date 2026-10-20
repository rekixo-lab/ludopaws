import { httpError } from "./crypto.js";
import { normalizeLeaderboardProfileId } from "./leaderboard-core.js";

export function optionalTargetPlayerCount(
  value,
) {
  if (
    value == null
  ) {
    return null;
  }

  const count =
    Number(
      value,
    );
  if (
    !Number.isInteger(
      count,
    ) ||
    count < 2 ||
    count > 4
  ) {
    throw httpError(
      400,
      "INVALID_TARGET_PLAYER_COUNT",
      "targetPlayerCount must be 2, 3, or 4",
    );
  }
  return count;
}

export function normalizeMatchMode(
  value,
  targetPlayerCount,
) {
  const normalized =
    String(
      value ??
        "ONLINE",
    )
      .trim()
      .toUpperCase();

  if (
    normalized !==
      "ONLINE" &&
    normalized !==
      "FRIENDS" &&
    normalized !==
      "TEAM_UP"
  ) {
    throw httpError(
      400,
      "INVALID_MATCH_MODE",
      "matchMode must be ONLINE, FRIENDS, or TEAM_UP",
    );
  }

  if (
    normalized ===
      "FRIENDS" &&
    !Number.isInteger(
      targetPlayerCount,
    )
  ) {
    throw httpError(
      400,
      "FRIEND_ROOM_PLAYER_COUNT_REQUIRED",
      "friend rooms require targetPlayerCount",
    );
  }

  if (
    normalized ===
      "TEAM_UP" &&
    targetPlayerCount !== 4
  ) {
    throw httpError(
      400,
      "TEAM_UP_REQUIRES_FOUR_PLAYERS",
      "team up requires exactly four players",
    );
  }

  return normalized;
}

export function optionalLeaderboardProfileId(
  value,
) {
  if (
    value == null ||
    String(value).trim() === ""
  ) {
    return null;
  }

  const normalized =
    normalizeLeaderboardProfileId(
      value,
    );
  if (!normalized) {
    throw httpError(
      400,
      "INVALID_PROFILE_ID",
      "leaderboard profile ID must be a UUID v4",
    );
  }
  return normalized;
}
