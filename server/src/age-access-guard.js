/**
 * Explicit server-side safety lockdown for the age-aware release.
 *
 * A client-provided "adult" flag, Google profile, guest session, or a previous
 * Play Age Signals result is NOT trusted age authorization. Until a Google Play
 * Integrity-bound, session-scoped adult proof has been independently validated,
 * the server must not make restricted online or monetized endpoints accessible.
 *
 * Protect the outermost Worker entrypoint so old APKs and direct HTTP / WebSocket
 * clients cannot bypass Android navigation restrictions.
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
  // Deliberately fail CLOSED on missing, misspelled, removed or overridden
  // release flags. There is no server-verifiable age-eligibility issuer yet,
  // so neither an operator flag nor a client claim may unlock social endpoints.
  // Keep the parameter for API compatibility; opening gameplay in the future
  // requires a separate, reviewed per-request trusted-eligibility protocol.
  void env;
  const url = new URL(request.url);
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
