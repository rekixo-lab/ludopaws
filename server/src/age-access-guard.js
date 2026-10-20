import { familyCapabilities, isFamilyMode } from "./family-safety.js";
/**
 * LOCKDOWN and unknown configuration deny connected capabilities.
 * FAMILY_V1 opens gameplay after removing free-text messaging, voice and
 * personal communications for every player. V2 permits configured commerce.
 * Neither edition asserts age or parental consent.
 * Recovery/deletion remains available for existing accounts.
 */
const RESTRICTED_API = Object.freeze([
  /^\/api\/matches(?:\/|$)/,
  /^\/api\/matchmaking(?:\/|$)/,
  /^\/api\/team-matchmaking(?:\/|$)/,
  /^\/api\/friends(?:\/|$)/,
  /^\/api\/leaderboard(?:\/|$)/,
  /^\/api\/wallet(?:\/|$)/,
  /^\/api\/store\/(?:catalog|purchases)(?:\/|$)/,
  /^\/api\/admob\/ssv$/,
  /^\/api\/account\/(?:guest|profile|google\/link|link\/friend|link\/leaderboard)(?:\/|$)/,
]);

export function isAgeRestrictedEndpoint(pathname) {
  if (typeof pathname !== "string") return false;
  // Do not obstruct statutory account deletion, recovery or delete-only codes.
  if (pathname === "/api/account/guest/delete" ||
      pathname === "/api/account/guest/deletion-code") return false;
  return RESTRICTED_API.some((pattern) => pattern.test(pathname));
}

/**
 * Lockdown is explicit and cannot be overridden by any HTTP header, bearer
 * token, query string, date of birth or client age assertion.
 *
 * Account deletion, logout, recovery, legal pages, health and readiness stay
 * reachable even during lockdown. This is a temporary release gate, NOT a
 * claim that under-age/parental permission is independently implemented.
 */
export function ageSafetyBlockResponse(request, env) {
  const url = new URL(request.url);
  if (isFamilyMode(env)) {
    // The family edition removes these capabilities for EVERY player and client.
    // Headers, age claims, old credentials and monetization flags cannot unlock them.
    const capabilities = familyCapabilities(env);
    const disabled = /^\/api\/friends\/messages(?:\/|$)/.test(url.pathname) ||
      (!capabilities.purchases && /^\/api\/store\/purchases(?:\/|$)/.test(url.pathname)) ||
      (!capabilities.cosmetics && url.pathname === "/api/wallet/cosmetics/purchase") ||
      (!capabilities.advertising && url.pathname === "/api/admob/ssv") ||
      (!capabilities.newGoogleLinking && url.pathname === "/api/account/google/link");
    if (!disabled) return null;
    return new Response(JSON.stringify({ error: "FAMILY_FEATURE_UNAVAILABLE",
      message: "This feature is unavailable in the family edition. Multiplayer and fixed quick-chat remain available." }),
      { status: 403, headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store", "x-content-type-options": "nosniff" } });
  }
  // Missing/unknown edition remains a lockdown; a client cannot select an edition.
  if (!isAgeRestrictedEndpoint(url.pathname)) return null;
  return new Response(JSON.stringify({
    error: "AGE_ACCESS_NOT_AVAILABLE",
    message: "Connected game features are unavailable while age-appropriate access verification is being completed. Offline play remains available.",
  }), {
    status: 403,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store",
      "x-content-type-options": "nosniff",
    },
  });
}
