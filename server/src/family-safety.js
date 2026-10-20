/** A restricted product edition, not an assertion that a player is an adult. */
export const FAMILY_MODE = "FAMILY_V1";
export const isFamilyMode = env => env?.LUDOPROOF_AGE_SAFETY_MODE === FAMILY_MODE;

// Names are server-generated from public game IDs. No user text can be encoded in them.
const ANIMALS = ["Panda", "Tiger", "Koala", "Otter", "Rabbit", "Fox", "Bear", "Cat"];
export function familyPlayerName(identity) {
  let hash = 2166136261;
  for (const char of String(identity ?? "player")) hash = Math.imul(hash ^ char.charCodeAt(0), 16777619) >>> 0;
  return `${ANIMALS[hash % ANIMALS.length]} ${String(hash % 10000).padStart(4, "0")}`;
}

/** Also redacts legacy records: switching edition must not expose old custom names. */
export function familyProjection(value, env) {
  if (!isFamilyMode(env) || value === null || typeof value !== "object") return value;
  if (Array.isArray(value)) return value.map(item => familyProjection(item, env));
  const identity = value.playerId ?? value.friendId ?? value.opponentFriendId ?? value.profileId ?? value.accountId;
  return Object.fromEntries(Object.entries(value).map(([key, item]) => [key,
    /displayName$/i.test(key) ? familyPlayerName(identity) : familyProjection(item, env),
  ]));
}

export async function familyResponse(response, env) {
  if (!isFamilyMode(env) || response.status === 101 || !response.headers.get("content-type")?.includes("application/json")) return response;
  const body = await response.json();
  const headers = new Headers(response.headers);
  headers.delete("content-length");
  headers.delete("etag");
  headers.set("cache-control", "no-store");
  return new Response(JSON.stringify(familyProjection(body, env)), {
    status: response.status, statusText: response.statusText, headers,
  });
}
