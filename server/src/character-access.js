import { verifyAccessToken } from "./account-security.js";
import { httpError } from "./crypto.js";
import { characterNeedsOwnership, normalizeCharacterId } from "./ludo-paws-characters.js";

export const CHARACTER_ACCOUNT_HEADER = "x-ludoproof-character-account-token";

/**
 * A human-controlled premium paw must be owned by an active PlayerAccount.
 * Only the authoritative server may create BOT seats, which bypass this helper
 * and may use the entire registered roster (including future additions).
 */
export async function assertHumanCharacterAccess(request, env, requestedCharacterId) {
  const characterId = normalizeCharacterId(requestedCharacterId);
  if (!characterNeedsOwnership(characterId)) return;

  const token = request.headers.get(CHARACTER_ACCOUNT_HEADER);
  if (!token) {
    throw httpError(403, "CHARACTER_ACCOUNT_REQUIRED", "Connect your Ludo Paws account to use this unlocked character.");
  }
  const session = await verifyAccessToken(env, token);
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
