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
  return new Response(request.method === "HEAD" ? null : `<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta property="og:title" content="Come play Ludo Paws with me!"><meta name="description" content="Little paws. Big fun. Open your friend's Ludo Paws invitation."><title>Play together · Ludo Paws</title><style>
*{box-sizing:border-box}body{margin:0;min-height:100vh;min-height:100svh;display:grid;place-items:center;padding:24px 16px;background:repeating-linear-gradient(2deg,#0000 0 14px,#4b2d1310 15px 16px),linear-gradient(135deg,#a7794b,#714829);color:#422b1c;font:16px/1.6 system-ui,sans-serif}main{width:100%;max-width:460px;padding:32px 26px;border:5px solid #c18a44;border-radius:30px;background:linear-gradient(145deg,#fff1cf,#e7c593);box-shadow:0 14px 40px #38200d66,inset 0 0 0 2px #ffedb3;text-align:center}.brand{font-size:12px;font-weight:800;letter-spacing:.18em;color:#805a30}.paws{width:98px;height:98px;margin:18px auto 12px;padding:20px;border-radius:50%;background:linear-gradient(#73c8e5,#226aab);box-shadow:0 0 0 6px #f4d080,0 5px 0 6px #c59349}svg{width:100%;height:100%;fill:#fff0bd}h1{font-size:clamp(26px,7vw,34px);line-height:1.15;letter-spacing:-.03em;margin:24px 0 12px}.intro{color:#77573b;margin:0 0 22px}.room{padding:15px;border-radius:18px;background:#bc8c4d26;box-shadow:inset 0 2px 6px #4b2c1720;margin:22px 0}.room span{display:block;font-size:11px;font-weight:800;letter-spacing:.12em;color:#805a30}code{display:block;font-size:25px;letter-spacing:.08em;font-weight:800;user-select:all}a.action{display:block;border:2px solid #b8ea85;border-radius:24px;margin:12px 0;padding:13px 18px;color:#18331c;background:linear-gradient(#77ce40,#26943c);font-weight:800;text-decoration:none;text-shadow:0 1px 0 #c9efa4;box-shadow:0 3px 0 #1d6529}a.secondary{color:#fff;text-shadow:0 1px 2px #174b78;border-color:#87cbfa;background:linear-gradient(#398edb,#20589d);box-shadow:0 3px 0 #204e83}a:focus-visible{outline:4px solid #55320f;outline-offset:4px}small{display:block;color:#77573b;font-size:12px;margin-top:18px}.steps{text-align:left;padding:16px 18px;border-radius:18px;background:#fff5dc77;font-size:14px;margin:24px 0 0}.steps strong{display:block;margin-bottom:7px}.steps p{margin:8px 0}.footer{font-size:12px;margin:20px 0 0;color:#77573b}
</style></head><body><main><div class="brand">LUDO PAWS · PLAY TOGETHER</div><div class="paws" aria-hidden="true"><svg viewBox="0 0 100 100"><ellipse cx="50" cy="67" rx="25" ry="19"/><ellipse cx="19" cy="39" rx="9" ry="13" transform="rotate(-25 19 39)"/><ellipse cx="39" cy="25" rx="9" ry="13"/><ellipse cx="61" cy="25" rx="9" ry="13"/><ellipse cx="81" cy="39" rx="9" ry="13" transform="rotate(25 81 39)"/></svg></div><h1>Your next game starts with a friend.</h1><p class="intro">Little paws. Big fun. Come play Ludo together.</p><div class="room"><span>FRIENDS · PRIVATE ROOM</span><code>${room}</code></div><a class="action" href="${open}">Open my friend's room</a><a class="action secondary" href="${play}">Get / update on Google Play</a><div class="steps"><strong>New here? You're welcome.</strong><p>After installing or updating, reopen this invitation link.</p><p>${instructions}</p></div><small>Rooms can fill up, start or expire. Ask your friend for a fresh room if needed.</small><p class="footer">An invitation link does not reserve a seat or grant rewards.</p></main></body></html>`, {
    headers: { "content-type": "text/html; charset=utf-8", "cache-control": "no-store", "x-content-type-options": "nosniff",
      "referrer-policy": "no-referrer", "content-security-policy": "default-src 'none'; style-src 'unsafe-inline'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'" },
  });
}
