// Navigation only: no reward attribution, credentials, identity lookup or room access.
const ROOM = /^LP[A-Z2-9]{8}$/;
const FRIEND = /^LPF-[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}$/;
const PACKAGE = "com.rekixo.ludopaws";
export function roomLinkLanding(request) {
  const url = new URL(request.url);
  if (!url.pathname.startsWith("/play/")) return null;
  const parts = url.pathname.split("/");
  const host = url.searchParams.get("host");
  if (url.href.length > 512 || parts.length !== 4 || parts[2] !== "friends" ||
      !ROOM.test(parts[3]) || (host !== null && !FRIEND.test(host))) {
    return new Response("This Ludo Paws room link is invalid.", { status: 404, headers: { "cache-control": "no-store" } });
  }
  if (!["GET", "HEAD"].includes(request.method)) return new Response(null, { status: 405, headers: { allow: "GET, HEAD" } });
  const room = parts[3];
  const play = `https://play.google.com/store/apps/details?id=${PACKAGE}`;
  // The optional host is a public Friend ID, never a host's player token.
  const query = host ? `?host=${host}` : "";
  const open = `intent://api.ludopaws.rekixo.com/play/${parts[2]}/${room}${query}#Intent;scheme=ludopaws;package=${PACKAGE};S.browser_fallback_url=${encodeURIComponent(play)};end`;
  const instructions = "Your accepted room invitation appears in Friends. New friends can request the host first, then wait for the host to accept and invite them.";
  return new Response(request.method === "HEAD" ? null : `<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta property="og:title" content="Come play Ludo Paws with me!"><meta name="description" content="Cute pals. Friendly competition. Join a friend in Ludo Paws."><title>Play together · Ludo Paws</title><style>body{margin:0;background:#143f43;color:#fff1d8;font:18px system-ui;display:grid;place-items:center;min-height:100vh}main{max-width:30em;padding:28px}h1{color:#ffd875}a{display:block;margin:16px 0;padding:16px 24px;border-radius:20px;background:#b6f0d5;color:#17342d;text-align:center;font-weight:700}p{line-height:1.6}code{font-size:24px;color:#ffd875}</style></head><body><main><h1>Little paws. Big fun.</h1><p>A friend invited you to play Ludo Paws.</p><p>Room: <code>${room}</code></p><a href="${open}">Open room in Ludo Paws</a><a href="${play}">Get / update on Google Play</a><p>After installing or updating, reopen this invitation link. Keep this page or message handy.</p><p>${instructions}</p><p>Rooms can fill up, start or expire. Ask your friend for a fresh room if needed. This link does not reserve a seat or grant invitation rewards.</p></main></body></html>`, {
    headers: { "content-type": "text/html; charset=utf-8", "cache-control": "no-store", "x-content-type-options": "nosniff",
      "referrer-policy": "no-referrer", "content-security-policy": "default-src 'none'; style-src 'unsafe-inline'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'" },
  });
}
