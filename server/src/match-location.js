export const MATCH_LOCATION_HEADER = "x-ludopaws-match-location-hint";
const LOCATION_HINTS = new Set(["wnam", "enam", "sam", "weur", "eeur", "apac", "oc", "afr", "me"]);

export function normalizeMatchLocationHint(value) {
  const hint = typeof value === "string" ? value.toLowerCase() : "";
  return LOCATION_HINTS.has(hint) ? hint : null;
}

export function matchLocationHint(env, request) {
  return normalizeMatchLocationHint(env.LUDOPROOF_MATCH_LOCATION_HINT)
    ?? ({ NA: "enam", SA: "sam", EU: "weur", AS: "apac", OC: "oc", AF: "afr" })[
      request?.cf?.continent
    ] ?? null;
}
