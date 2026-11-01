import { verifyAccessToken } from "./account-security.js";
import { httpError } from "./crypto.js";
import { characterNeedsOwnership, normalizeCharacterId } from "./ludo-paws-characters.js";

export const CHARACTER_ACCOUNT_HEADER = "x-ludoproof-character-account-token";

/**
 * A human-controlled premium paw must be owned by an active PlayerAccount.
 * Only the authoritative server may create BOT seats, which bypass this helper
 * and may use the entire registered roster (including future additions).
 */
export async function assertHumanCharacterAccess(request, env, requestedCharacterId, identity = {}) {
  const characterId = normalizeCharacterId(requestedCharacterId);
  if (!characterNeedsOwnership(characterId)) return;

  const token = request.headers.get(CHARACTER_ACCOUNT_HEADER);
  if (!token) {
    throw httpError(403, "CHARACTER_ACCOUNT_REQUIRED", "Connect your Ludo Paws account to use this unlocked character.");
  }
  const session = await verifyAccessToken(env, token);

  // The premium entitlement and the human-controlled leaderboard/friend
  // identity must belong to the SAME account. A paid JWT alone is not a
  // transferable license for unrelated social profiles.
  const profileId = identity?.profileId;
  const friendId = identity?.friendId;
  if ((Boolean(profileId) === Boolean(friendId)) || (!profileId && !friendId)) {
    throw httpError(403, "CHARACTER_IDENTITY_REQUIRED", "Link your game account to this player profile before using a paid paw.");
  }
  const directory = env?.LUDOPROOF_ACCOUNT_DIRECTORY;
  if (!directory) {
    throw httpError(503, "CHARACTER_IDENTITY_UNAVAILABLE", "Character ownership verification is temporarily unavailable.");
  }
  let identityResponse;
  try {
    const target = directory.get(directory.idFromName("GLOBAL:V1"));
    identityResponse = await target.fetch(new Request("https://account-directory/referral/identity", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify(profileId ? { profileId } : { friendId }),
    }));
  } catch {
    throw httpError(503, "CHARACTER_IDENTITY_UNAVAILABLE", "Character ownership verification is temporarily unavailable.");
  }
  if (!identityResponse.ok) {
    throw httpError(503, "CHARACTER_IDENTITY_UNAVAILABLE", "Character ownership verification is temporarily unavailable.");
  }
  let linked;
  try { linked = await identityResponse.json(); }
  catch {
    throw httpError(503, "CHARACTER_IDENTITY_UNAVAILABLE", "Character ownership verification is temporarily unavailable.");
  }
  if (linked?.accountId !== session.accountId) {
    throw httpError(403, "CHARACTER_ACCOUNT_MISMATCH", "Restore the game account linked to this profile to use its premium character.");
  }

  const namespace = env?.LUDOPROOF_PLAYER_ACCOUNTS;
  if (!namespace) {
    throw httpError(503, "CHARACTER_WALLET_UNAVAILABLE", "Character ownership verification is temporarily unavailable.");
  }

  const target = namespace.get(namespace.idFromName(session.accountId.toLowerCase()));
  let response;
  try {
    response = await target.fetch(new Request("https://player-account/wallet/get", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ accountId: session.accountId, sessionId: session.sessionId }),
    }));
  } catch {
    throw httpError(503, "CHARACTER_WALLET_UNAVAILABLE", "Character ownership verification is temporarily unavailable.");
  }
  if (response.status === 401 || response.status === 403) {
    throw httpError(403, "CHARACTER_ACCOUNT_SESSION_INVALID", "Reconnect the account that owns this character.");
  }
  if (!response.ok) {
    throw httpError(503, "CHARACTER_WALLET_UNAVAILABLE", "Character ownership verification is temporarily unavailable.");
  }
  let payload;
  try { payload = await response.json(); }
  catch {
    throw httpError(503, "CHARACTER_WALLET_UNAVAILABLE", "Character ownership verification is temporarily unavailable.");
  }
  const wallet = payload?.wallet;
  if (wallet?.accountId !== session.accountId || !Array.isArray(wallet?.ownedCosmetics)) {
    throw httpError(503, "CHARACTER_WALLET_INVALID", "Character ownership could not be verified.");
  }
  if (!wallet.ownedCosmetics.includes("character_" + characterId)) {
    throw httpError(403, "CHARACTER_NOT_OWNED", "Unlock this character in Shop before using it online.");
  }
}

export function forwardCharacterAccountHeader(headers, request) {
  const token = request.headers.get(CHARACTER_ACCOUNT_HEADER);
  if (token) headers.set(CHARACTER_ACCOUNT_HEADER, token);
}
