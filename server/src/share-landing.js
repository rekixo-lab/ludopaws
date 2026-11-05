export const PUBLIC_SHARE_HOST = "ludopaws.rekixo.com";
export const PUBLIC_SHARE_ORIGIN = "https://" + PUBLIC_SHARE_HOST;
const COVER_PATH = "/share-cover-v2.jpg";
export const PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=com.rekixo.ludopaws";

// The cover is shared across entry points. Keep room codes and referral tokens
// out of image URLs and preview metadata.
export function sharePreview(title, description) {
  return `<meta property="og:type" content="website">
<meta property="og:site_name" content="Ludo Paws">
<meta property="og:title" content="${title}">
<meta property="og:description" content="${description}">
<meta name="description" content="${description}">
<meta property="og:image" content="${PUBLIC_SHARE_ORIGIN}${COVER_PATH}">
<meta property="og:image:type" content="image/jpeg">
<meta property="og:image:width" content="1200">
<meta property="og:image:height" content="630">
<meta property="og:image:alt" content="Ludo Paws home hero beside Panda, Monkey, Rooster and Horse on the Winter Haven board">`;
}

export function isShareTap(request) {
  return request.method === "GET" &&
    request.headers.get("sec-fetch-mode") === "navigate" &&
    request.headers.get("sec-fetch-dest") === "document" &&
    request.headers.get("sec-fetch-user") === "?1";
}

export function redirectToPlay(referrer) {
  const destination = new URL(PLAY_STORE_URL);
  if (typeof referrer === "string") destination.searchParams.set("referrer", referrer);
  return new Response(null, {
    status: 303,
    headers: { location: destination.href, "cache-control": "no-store", "referrer-policy": "no-referrer" },
  });
}

export function playSharePage(request, title, description) {
  if (request.method !== "GET" && request.method !== "HEAD") {
    return new Response(null, { status: 405, headers: { allow: "GET, HEAD" } });
  }
  if (isShareTap(request)) return redirectToPlay();

  // Preview requests can read the card. Browsers without Fetch Metadata can
  // still continue to the same Play listing without a separate landing step.
  const html = `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>${title}</title>
${sharePreview(title, description)}
<meta http-equiv="refresh" content="0;url=${PLAY_STORE_URL}">
</head>
<body><p><a href="${PLAY_STORE_URL}">Open Google Play</a></p></body>
</html>`;
  return new Response(request.method === "HEAD" ? null : html, {
    headers: {
      "content-type": "text/html; charset=utf-8",
      "cache-control": "no-store",
      "x-content-type-options": "nosniff",
      "referrer-policy": "no-referrer",
      "content-security-policy": "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'",
    },
  });
}

export function gameShareLanding(request, pathname) {
  if (pathname !== "/share" && pathname !== "/share/") return null;
  return playSharePage(request, "Play Ludo Paws with me!",
    "Pick your favorite Paw and play Ludo together, online or on the same phone.");
}
