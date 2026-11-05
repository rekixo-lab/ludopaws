const SHARE_ORIGIN = "https://api.ludopaws.rekixo.com";
const COVER_PATH = "/share-cover-v1.jpg";
const PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=com.rekixo.ludopaws";

// The cover is shared across entry points. Keep room codes and referral tokens
// out of image URLs and preview metadata.
export function sharePreview(title, description) {
  return `<meta property="og:type" content="website">
<meta property="og:site_name" content="Ludo Paws">
<meta property="og:title" content="${title}">
<meta property="og:description" content="${description}">
<meta name="description" content="${description}">
<meta property="og:image" content="${SHARE_ORIGIN}${COVER_PATH}">
<meta property="og:image:type" content="image/jpeg">
<meta property="og:image:width" content="1200">
<meta property="og:image:height" content="630">
<meta property="og:image:alt" content="Ludo Paws characters alongside the colorful in-game Ludo board">`;
}

export function gameShareLanding(request, pathname) {
  if (pathname !== "/share") return null;
  if (request.method !== "GET" && request.method !== "HEAD") {
    return new Response(null, { status: 405, headers: { allow: "GET, HEAD" } });
  }

  const title = "Play Ludo Paws with me!";
  const description = "Pick your favorite Paw and play Ludo together, online or on the same phone.";
  const html = `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>${title}</title>
<link rel="canonical" href="${SHARE_ORIGIN}/share">
<meta property="og:url" content="${SHARE_ORIGIN}/share">
${sharePreview(title, description)}
<style>
*{box-sizing:border-box}
body{margin:0;min-height:100vh;display:grid;place-items:center;padding:20px;background:#102c40;color:#ffedce;font:16px/1.5 system-ui,sans-serif;text-align:center}
main{width:min(100%,38rem);padding:clamp(14px,4vw,30px);background:#183f50;border:1px solid #bd925a;border-radius:24px}
img{display:block;width:100%;height:auto;border-radius:14px}
h1{line-height:1.15;margin:1rem 0 .5rem}
p{margin:.5rem auto 1rem;max-width:27rem}
a{color:#ffedce}
.play{display:inline-block;padding:.75rem 1.25rem;border-radius:24px;background:#ffd68e;color:#25201b;font-weight:750;text-decoration:none}
</style>
</head>
<body>
<main>
<img src="${COVER_PATH}" width="1200" height="630" alt="Ludo Paws characters and game board">
<h1>${title}</h1>
<p>${description}</p>
<a class="play" href="${PLAY_STORE_URL}">View on Google Play</a>
<p><a href="https://ludopaws.rekixo.com/">Ludo Paws official website</a></p>
</main>
</body>
</html>`;
  return new Response(request.method === "HEAD" ? null : html, {
    headers: {
      "content-type": "text/html; charset=utf-8",
      "cache-control": "public, max-age=300",
      "x-content-type-options": "nosniff",
      "referrer-policy": "no-referrer",
      "content-security-policy": "default-src 'none'; img-src 'self'; style-src 'unsafe-inline'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'",
    },
  });
}
