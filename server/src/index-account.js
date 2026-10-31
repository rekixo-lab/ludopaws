import { familyResponse, familyCapabilities } from "./family-safety.js";
import { ageSafetyBlockResponse } from "./age-access-guard.js";
import baseWorker, {
  ApiGate,
  FriendConversation,
  FriendDirectory,
  FriendRecentRoom,
  LeaderboardRoom,
  MatchmakerQueue,
  MatchRoom,
  StorePurchaseLedger,
  TeamMatchmakerQueue,
} from "./index-forfeit.js";
import { AccountDirectory } from "./account-directory.js";
import { PlayerAccount } from "./player-account.js";
import {
  billingAccountId,
  hashGoogleSubject,
  verifyAccessToken,
  verifyAccessTokenForDeletionReplay,
  verifyGoogleIdToken,
} from "./account-security.js";
import { GOOGLE_PLAY_PRODUCTS, googlePlayBillingChecks, googlePlayBillingEnabled, verifyAndRedeemGooglePlayPurchase } from "./google-play-billing.js";
import { verifyAdMobSsvRequest } from "./admob-ssv.js";
import { StoreCatalogStore } from "./store-catalog-store.js";
import { evaluateOperationalHealth } from "./operational-health.js";
import { bearerToken, httpError, readJsonObject, sha256Hex } from "./crypto.js";
import { REFERRAL_ORIGIN, REFERRAL_PACKAGE, REFERRAL_REWARD, REFERRAL_TERMS_VERSION, referralConfiguration, referralHealth, referralRequest, referralsConfigured, referralsEnabled, signingCertificates } from "./referral-config.js";

export { BillingReconciliation } from "./billing-reconciliation.js";
export { ReferralCampaign } from "./referral-campaign.js";

export {
  ApiGate, FriendConversation, FriendDirectory, FriendRecentRoom, LeaderboardRoom,
  MatchmakerQueue, MatchRoom, StorePurchaseLedger, StoreCatalogStore, TeamMatchmakerQueue,
  AccountDirectory, PlayerAccount,
};

const MAX_BODY_BYTES = 8 * 1024;
const RATE_WINDOW_MS = 60_000;
const ACCOUNT_RATE_LIMITS = Object.freeze({
  guest: 5, challenge: 20, googleLogin: 10, googleLink: 10, sessionRefresh: 30,
  sessionLogout: 20, profile: 60, wallet: 120, walletMutation: 30, friendLink: 15, leaderboardLink: 15,
  storePurchase: 10, guestDeletionCode: 3, guestDeletion: 3, catalogRead: 120, catalogAdmin: 20,
});

const ACCOUNT_WEB_CORS_PATHS = new Set([
  "/api/account/google/challenge",
  "/api/account/google/login",
  "/api/account/delete",
  "/api/account/guest/delete",
]);

export default {
  async scheduled(_event, env, ctx) {
    if (env?.LUDOPAWS_REFERRALS) ctx.waitUntil(referralRequest(env, "/maintenance").then(response => {
      if (!response.ok) throw new Error("Referral maintenance unavailable");
    }));
    if (!googlePlayBillingEnabled(env)) return;
    const task = env.LUDOPROOF_BILLING_RECONCILIATION.get(
      env.LUDOPROOF_BILLING_RECONCILIATION.idFromName("GOOGLE_PLAY:V1"),
    ).fetch(new Request("https://billing-reconciliation/run", { method: "POST" })).then(response => {
      if (!response.ok) throw new Error("Billing reconciliation failed: HTTP " + response.status);
    });
    ctx.waitUntil(task);
  },
  async fetch(request, env) {
    // Enforce the selected product capabilities before routing old or new clients.
    const safetyResponse = ageSafetyBlockResponse(request, env);
    if (safetyResponse) return safetyResponse;
    const url = new URL(request.url);
    if (url.pathname === "/api/capabilities" && request.method === "GET") {
      return json(200, familyCapabilities(env));
    }
    if (request.method === "OPTIONS" && ACCOUNT_WEB_CORS_PATHS.has(url.pathname)) {
      const expectedOrigin = accountWebOrigin(env);
      const origin = request.headers.get("origin");
      const requestedMethod = String(request.headers.get("access-control-request-method") ?? "").toUpperCase();
      const requestedHeaders = String(request.headers.get("access-control-request-headers") ?? "")
        .split(",").map((header) => header.trim().toLowerCase()).filter(Boolean);
      if (
        !expectedOrigin ||
        origin !== expectedOrigin ||
        requestedMethod !== "POST" ||
        requestedHeaders.some((header) => !["authorization", "content-type"].includes(header))
      ) {
        return new Response(null, { status: 403, headers: { "cache-control": "no-store" } });
      }
      return withAccountWebCors(
        request,
        new Response(null, { status: 204, headers: { "cache-control": "no-store" } }),
        env,
      );
    }
    const startedAt = Date.now();
    try {
      const response = await handleAccountRequest(request, env);
      return withAccountWebCors(request, await familyResponse(response, env), env);
    } finally {
      const elapsedMs = Date.now() - startedAt;
      if (elapsedMs >= 1500) {
        const family = ["account", "store", "referrals", "friends", "matches", "leaderboard"].find(name => url.pathname.startsWith("/api/" + name)) ?? "other";
        console.warn(JSON.stringify({ event: "api-slow", family, elapsedMs }));
      }
    }
  },
};

/**
 * Internal account-routing logic exposed for unit tests only.
 * The ONLY HTTP entrypoint is the default Worker's guarded fetch() above:
 * never wire this function to a public route or service binding.
 * Internal unit tests exercise authentication and billing below the
 * independent, always-on production age-safety gate.
 */
export async function handleAccountRequest(request, env) {
    const url = new URL(request.url);
    try {
      if (url.pathname === "/health/operations" && request.method === "GET") {
        const response = await handleAccountRequest(new Request(new URL("/ready", url)), env);
        const health = evaluateOperationalHealth(await response.json());
        // Failing health probes are HTTP 503 so the scoped Worker 5xx monitor
        // can observe delivery/provider trouble as well as request crashes.
        return json(health.ok ? 200 : 503, health);
      }
      if (url.pathname === "/.well-known/assetlinks.json" && request.method === "GET") {
        return json(200, signingCertificates(env).length ? [{ relation: ["delegate_permission/common.handle_all_urls"],
          target: { namespace: "android_app", package_name: REFERRAL_PACKAGE,
            sha256_cert_fingerprints: signingCertificates(env).map(value => value.match(/../g).join(":").toUpperCase()) } }] : []);
      }
      const invitePath = /^\/r\/([a-f0-9]{64})(\/download)?$/.exec(url.pathname);
      if (invitePath) {
        await rateLimit(env, request, "referral:landing", 120);
        if (!referralsEnabled(env)) return new Response("Friendship rewards are not available yet. Please try again later.", { status: 503, headers: { "cache-control": "no-store" } });
        if (invitePath[2] && request.method === "POST") {
          if (request.headers.get("origin") !== REFERRAL_ORIGIN ||
              ["cross-site", "same-site"].includes(request.headers.get("sec-fetch-site"))) return json(403, { error: "INVITE_ORIGIN_INVALID", message: "Open the invitation page and use its download button." });
          const response = await referralRequest(env, "/click", { token: invitePath[1] });
          if (!response.ok) return response;
          const click = await response.json();
          const destination = new URL("https://play.google.com/store/apps/details");
          destination.searchParams.set("id", REFERRAL_PACKAGE);
          if (typeof click.referrer === "string") destination.searchParams.set("referrer", click.referrer);
          return new Response(null, { status: 303, headers: { location: destination.href, "cache-control": "no-store", "referrer-policy": "no-referrer" } });
        }
        if (!invitePath[2] && request.method === "GET") {
          return new Response(`<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta property="og:title" content="A friend invited you to Ludo Paws"><meta name="description" content="Play a real match together. Your inviter can earn a friendship bundle."><title>Ludo Paws · Friendship invitation</title><style>body{margin:0;background:#142f37;color:#fff;font:18px system-ui;display:grid;place-items:center;min-height:100vh}main{max-width:30em;padding:28px}h1{color:#ffe6a8}button{background:#b6f0d5;border:0;border-radius:24px;padding:18px 26px;font:700 20px system-ui;color:#17342d}p{line-height:1.6}small{color:#c2d6d6}</style><main><h1>A little friendship. A big adventure.</h1><p>You have been invited to Ludo Paws!</p><p>Install through this button and link your Google account. Open Home → Share, read the optional verification disclosure, accept the invitation terms and tap Verify my invitation before playing. Then finish a real Online or Friends match within 14 days of installation. No win needed.</p><form method="post" action="/r/${invitePath[1]}/download"><button>Get Ludo Paws on Google Play</button></form><p><small>Only your inviter receives Pearl Paw Dice, Blossom Garden Board, Rabbit and 100 Gems, once. New players only; install within 7 days of this download click. Only natural Online or Friends matches lasting at least 3 minutes count, with at least one legal move by the invited player. Bots, forfeits, offline and Team Up matches and self-invites do not qualify. Sharing and verification are optional; declining does not restrict gameplay. No purchase, ad view, rating or review is required. Stop verification in the Share page to cancel pending incoming attribution. Existing items are not duplicated or exchanged. Verification can take time. Campaign ends ${new Date(Number(env.LUDOPAWS_REFERRAL_START_MS) + 90 * 86400000).toUTCString()}; your match must finish before then. No reward for the invited player. <a href="https://ludopaws.rekixo.com/referral-terms.html">Full invitation terms &amp; support</a> · <a href="https://ludopaws.rekixo.com/privacy-policy.html">Privacy policy</a></small></p></main></html>`, {
            headers: { "content-type": "text/html; charset=utf-8", "cache-control": "no-store", "x-content-type-options": "nosniff", "referrer-policy": "no-referrer",
              "content-security-policy": "default-src 'none'; style-src 'unsafe-inline'; form-action 'self'; frame-ancestors 'none'; base-uri 'none'" },
          });
        }
        return json(405, { error: "METHOD_NOT_ALLOWED" });
      }
      if (/^\/api\/referrals\/(status|invite|challenge|bind|enroll|withdraw)$/.test(url.pathname)) {
        const operation = url.pathname.split("/").pop();
        if (request.method !== (operation === "status" ? "GET" : "POST")) return json(405, { error: "METHOD_NOT_ALLOWED" });
        await rateLimit(env, request, "referral:" + operation, operation === "status" ? 300 : 60);
        if (operation === "withdraw" && env?.LUDOPAWS_REFERRALS) {
          const session = await authenticate(request, env);
          await readJsonObject(request, 1024);
          return await referralRequest(env, "/withdraw", { accountId: session.accountId });
        }
        if (!(operation === "status" ? referralsConfigured(env) : referralsEnabled(env))) return operation === "status"
          ? json(200, { ok: true, enabled: false, reward: REFERRAL_REWARD, termsVersion: REFERRAL_TERMS_VERSION })
          : json(503, { error: "REFERRALS_UNAVAILABLE", message: "Friendship rewards are not available yet." });
        const session = await authenticate(request, env);
        const body = request.method === "POST" ? await readJsonObject(request, 24 * 1024) : {};
        return await referralRequest(env, "/" + operation, { ...body, accountId: session.accountId });
      }
      if (url.pathname === "/ready" && request.method === "GET") {
        const base = await baseWorker.fetch(request, env);
        let body;
        try { body = await base.clone().json(); }
        catch { return base; }
        const accountDirectoryConfigured = Boolean(env?.LUDOPROOF_ACCOUNT_DIRECTORY);
        const playerAccountsConfigured = Boolean(env?.LUDOPROOF_PLAYER_ACCOUNTS);
        const googleSignInClientIdConfigured = typeof env?.GOOGLE_SIGN_IN_WEB_CLIENT_ID === "string" &&
          env.GOOGLE_SIGN_IN_WEB_CLIENT_ID.trim().length > 0;
        const sessionHmacConfigured = typeof env?.LUDOPROOF_SESSION_HMAC_KEY === "string" &&
          new TextEncoder().encode(env.LUDOPROOF_SESSION_HMAC_KEY).byteLength >= 32;
        const billingReconciliationConfigured = Boolean(env?.LUDOPROOF_BILLING_RECONCILIATION);
        const referralChecks = referralConfiguration(env);
        let referralReady = !referralChecks.enabled || referralsConfigured(env);
        const referralDelivery = await referralHealth(env);
        if (referralChecks.enabled && (referralDelivery?.ok !== true || referralDelivery.configurationMatches !== true)) referralReady = false;
        let billingReconciliationStatus = null;
        if (billingReconciliationConfigured && googlePlayBillingEnabled(env)) {
          const coordinator = env.LUDOPROOF_BILLING_RECONCILIATION.get(env.LUDOPROOF_BILLING_RECONCILIATION.idFromName("GOOGLE_PLAY:V1"));
          const response = await coordinator.fetch(new Request("https://billing-reconciliation/status"));
          if (response.ok) billingReconciliationStatus = await response.json();
        }
        const ready = referralReady && (!googlePlayBillingEnabled(env) || billingReconciliationConfigured) && base.ok && body?.ready === true && accountDirectoryConfigured &&
          playerAccountsConfigured && googleSignInClientIdConfigured && sessionHmacConfigured;
        return json(ready ? 200 : 503, {
          ...body,
          ok: ready,
          ready,
          productionClaim: ready && body?.productionClaim === true,
          deploymentRevision: /^[a-f0-9]{40}$/.test(String(env?.LUDOPROOF_DEPLOY_COMMIT_SHA ?? ""))
            ? env.LUDOPROOF_DEPLOY_COMMIT_SHA : null,
          checks: {
            ...(body?.checks ?? {}),
            accountDirectoryConfigured,
            playerAccountsConfigured,
            googleSignInClientIdConfigured,
            sessionHmacConfigured,
            billingReconciliationConfigured,
            billingReconciliationStatus,
            referralChecks,
            referralMinimumVersionCode: referralChecks.minimumVersion ? Number(env.LUDOPAWS_REFERRAL_MIN_VERSION_CODE) : null,
            referralDelivery,
            playBillingEnabled: googlePlayBillingEnabled(env),
            playBillingConfiguration: googlePlayBillingChecks(env),
                admobSsvEnabled: env?.LUDOPROOF_ADMOB_SSV_ENABLED === "true" &&
              Boolean(env?.LUDOPROOF_ADMOB_REWARDED_AD_UNIT_ID) &&
              (env?.LUDOPROOF_ADMOB_REWARD_ITEM == null || env.LUDOPROOF_ADMOB_REWARD_ITEM === "gems"),
          },
        });
      }

      if (url.pathname === "/api/admin/moderation/reports" && request.method === "GET") {
        await rateLimit(env, request, "moderation:reports:read", 30);
        const session = await authenticate(request, env);
        requireModerationAdmin(session.accountId, env);
        return json(200, await moderationRequest(env, "/moderation/list", {}));
      }
      if (url.pathname === "/api/admin/moderation/reports/evidence" && request.method === "GET") {
        await rateLimit(env, request, "moderation:reports:evidence", 20);
        const session = await authenticate(request, env);
        requireModerationAdmin(session.accountId, env);
        return json(200, await moderationRequest(env, "/moderation/evidence", {
          reportId: url.searchParams.get("reportId"),
        }));
      }
      if (url.pathname === "/api/admin/moderation/reports/resolve" && request.method === "POST") {
        await rateLimit(env, request, "moderation:reports:resolve", 20);
        const session = await authenticate(request, env);
        requireModerationAdmin(session.accountId, env);
        const body = await readJson(request);
        return json(200, await moderationRequest(env, "/moderation/resolve", {
          reportId: body.reportId, outcome: body.outcome,
          suspendHours: body.suspendHours,
          reviewerId: session.accountId,
        }));
      }

      if (url.pathname === "/api/account/guest" && request.method === "POST") {
        await rateLimit(env, request, "account:guest", ACCOUNT_RATE_LIMITS.guest);
        const body = await readJson(request);
        const guest = await directoryRequest(env, "/guest", body);
        const accountId = requireText(guest.accountId, "accountId");
        return await playerResponse(env, accountId, "/session/create", {
          accountId,
          displayName: body.displayName,
        });
      }

      if (url.pathname === "/api/account/google/challenge" && request.method === "POST") {
        requireMethod(request, "POST");
        await rateLimit(env, request, "account:google:challenge", ACCOUNT_RATE_LIMITS.challenge);
        return await directoryResponse(env, "/nonce/create", {});
      }

      if (url.pathname === "/api/account/google/link" && request.method === "POST") {
        await rateLimit(env, request, "account:google:link", ACCOUNT_RATE_LIMITS.googleLink);
        const session = await authenticate(request, env);
        const body = await readJson(request);
        const google = await verifyGoogleIdToken(env, body.idToken, body.nonce);
        await directoryRequest(env, "/nonce/consume", { nonce: body.nonce });
        const subjectHash = await hashGoogleSubject(google.subject);
        const linked = await directoryRequest(env, "/google/link", {
          accountId: session.accountId,
          subjectHash,
        });
        let profile = session.profile;
        // The default guest label is a placeholder, not a player choice. Use the server-verified
        // Google display name once, but never overwrite an existing custom account name.
        if (
          String(profile?.displayName ?? "") === "Guest User" &&
          typeof google.displayName === "string" &&
          google.displayName.trim()
        ) {
          const updated = await playerRequest(env, session.accountId, "/profile/update", {
            accountId: session.accountId,
            sessionId: session.sessionId,
            displayName: google.displayName,
          });
          profile = updated.profile ?? profile;
        }
        return json(200, {
          ok: true,
          accountId: session.accountId,
          linked: linked.linked === true,
          profile,
        });
      }

      if (url.pathname === "/api/account/google/login" && request.method === "POST") {
        await rateLimit(env, request, "account:google:login", ACCOUNT_RATE_LIMITS.googleLogin);
        const body = await readJson(request);
        const google = await verifyGoogleIdToken(env, body.idToken, body.nonce);
        await directoryRequest(env, "/nonce/consume", { nonce: body.nonce });
        const subjectHash = await hashGoogleSubject(google.subject);
        // Login restores only an already-linked account. Creating a fresh guest here would
        // silently switch identities and orphan the current guest's inventory/social state.
        const existing = await directoryRequest(env, "/google/lookup", { subjectHash });
        const accountId = requireAccountId(existing.accountId);
        return await playerResponse(env, accountId, "/session/create", {
          accountId,
          displayName: google.displayName,
        });
      }

      if (url.pathname === "/api/account/refresh" && request.method === "POST") {
        await rateLimit(env, request, "account:session:refresh", ACCOUNT_RATE_LIMITS.sessionRefresh);
        const body = await readJson(request);
        const accountId = requireAccountId(body.accountId);
        const sessionId = requireAccountId(body.sessionId);
        return await playerResponse(env, accountId, "/session/refresh", { ...body, accountId, sessionId });
      }

      if (url.pathname === "/api/account/logout" && request.method === "POST") {
        await rateLimit(env, request, "account:session:logout", ACCOUNT_RATE_LIMITS.sessionLogout);
        const session = await authenticate(request, env);
        return await playerResponse(env, session.accountId, "/session/logout", {
          accountId: session.accountId,
          sessionId: session.sessionId,
        });
      }

      if (url.pathname === "/api/account/guest/deletion-code" && request.method === "POST") {
        await rateLimit(env, request, "account:guest:deletion-code", ACCOUNT_RATE_LIMITS.guestDeletionCode);
        const session = await authenticate(request, env);
        const body = await readJson(request);
        const registration = await directoryRequest(env, "/guest-deletion/register", {
          accountId: session.accountId,
          rotate: body?.rotate === true,
        });
        const credential = normalizeGuestDeletionCredential(registration?.credential);
        return json(200, { ok: true, ready: true, credential });
      }

      if (url.pathname === "/api/account/guest/delete" && request.method === "POST") {
        await rateLimit(env, request, "account:guest:delete", ACCOUNT_RATE_LIMITS.guestDeletion);
        const body = await readJson(request);
        if (body?.confirmation !== "DELETE_MY_ACCOUNT") throw httpError(400, "ACCOUNT_DELETE_CONFIRMATION_REQUIRED", "confirm account deletion to continue");
        const credential = normalizeGuestDeletionCredential(body?.credential);
        const credentialHash = await sha256Hex("ludoproof:guest-deletion-credential:v1:" + credential);
        const claim = await directoryRequest(env, "/guest-deletion/claim", { credentialHash });
        if (claim.deleted === true) return json(200, { ok: true, deleted: true, replay: true });
        const result = await deleteAccountData(env, claim.accountId, null, {
          guestRecoveryVerified: true,
          guestDeletionCredentialHash: credentialHash,
        });
        return json(200, { ok: true, deleted: result.deleted === true, replay: result.replay === true });
      }

      if (url.pathname === "/api/account/delete" && request.method === "POST") {
        await rateLimit(env, request, "account:delete", 3);
        let session;
        try {
          // Session validation is still required, but this route may resume an already
          // claimed deletion. Other account routes remain blocked while the claim exists.
          session = await authenticate(request, env, { allowDeletionPending: true });
        } catch (error) {
          const claims = await verifyAccessTokenForDeletionReplay(env, bearerToken(request));
          const body = await readJson(request);
          let status;
          try {
            // An expired signed token is not allowed to start deletion. PlayerAccount
            // accepts this flag only when a prior deletion claim already exists.
            status = await playerRequest(env, claims.accountId, "/account/delete-preview", {
              accountId: claims.accountId,
              sessionId: claims.sessionId,
              accountDeletionReplayVerified: true,
            });
          } catch {
            throw error;
          }
          if (status.deleted === true) {
            // PlayerAccount is the durable deletion tombstone. Only acknowledge
            // replay after the directory's linked identity cleanup also completes.
            await directoryRequest(env, "/account/delete", { accountId: claims.accountId });
            return json(200, { ok: true, deleted: true, replay: true });
          }
          if (status.deletionPending === true && body?.confirmation === "DELETE_MY_ACCOUNT") {
            const result = await deleteAccountData(env, claims.accountId, claims.sessionId, {
              accountDeletionReplayVerified: true,
            });
            return json(200, { ok: true, deleted: result.deleted === true, replay: true });
          }
          throw error;
        }
        const body = await readJson(request);
        if (body?.confirmation !== "DELETE_MY_ACCOUNT") throw httpError(400, "ACCOUNT_DELETE_CONFIRMATION_REQUIRED", "confirm account deletion to continue");
        const result = await deleteAccountData(env, session.accountId, session.sessionId);
        return json(200, { ok: true, deleted: result.deleted === true, replay: result.replay === true });
      }

      if (url.pathname === "/api/account/me" && request.method === "GET") {
        await rateLimit(env, request, "account:profile:read", ACCOUNT_RATE_LIMITS.profile);
        const session = await authenticate(request, env);
        return await playerResponse(env, session.accountId, "/profile/get", {
          accountId: session.accountId,
          sessionId: session.sessionId,
        });
      }

      if (url.pathname === "/api/account/profile" && request.method === "POST") {
        await rateLimit(env, request, "account:profile:update", ACCOUNT_RATE_LIMITS.profile);
        const session = await authenticate(request, env);
        const body = await readJson(request);
        return await playerResponse(env, session.accountId, "/profile/update", {
          accountId: session.accountId,
          sessionId: session.sessionId,
          displayName: body.displayName,
        });
      }

      if (url.pathname === "/api/account/links" && request.method === "GET") {
        await rateLimit(env, request, "account:links:read", ACCOUNT_RATE_LIMITS.profile);
        const session = await authenticate(request, env);
        return await directoryResponse(env, "/identity/list", { accountId: session.accountId });
      }

      if (url.pathname === "/api/account/identities/recover" && request.method === "GET") {
        await rateLimit(env, request, "account:identity:recover", 10);
        const session = await authenticate(request, env);
        const links = await directoryRequest(env, "/identity/list", { accountId: session.accountId });
        let friend = null;
        let leaderboard = null;
        if (links.friendId) {
          if (!env?.LUDOPROOF_FRIENDS) throw httpError(503, "FRIENDS_NOT_CONFIGURED", "friend directory is not configured");
          const target = env.LUDOPROOF_FRIENDS.get(env.LUDOPROOF_FRIENDS.idFromName("GLOBAL:V1"));
          friend = await parseInternalResponse(await target.fetch(new Request("https://friends/account/recover", {
            method: "POST",
            headers: { "content-type": "application/json" },
            body: JSON.stringify({ friendId: links.friendId }),
          })), "FRIEND_IDENTITY_RECOVERY_FAILED");
        }
        if (links.leaderboardProfileId) {
          if (!env?.LUDOPROOF_LEADERBOARD) throw httpError(503, "LEADERBOARD_NOT_CONFIGURED", "leaderboard is not configured");
          const target = env.LUDOPROOF_LEADERBOARD.get(env.LUDOPROOF_LEADERBOARD.idFromName("global"));
          leaderboard = await parseInternalResponse(await target.fetch(new Request("https://leaderboard/profile/recover", {
            method: "POST",
            headers: { "content-type": "application/json" },
            body: JSON.stringify({ profileId: links.leaderboardProfileId }),
          })), "LEADERBOARD_IDENTITY_RECOVERY_FAILED");
        }
        return json(200, {
          ok: true,
          accountId: session.accountId,
          googleLinked: links.googleLinked === true,
          friend,
          leaderboard,
        });
      }

      if (url.pathname === "/api/account/link/friend" && request.method === "POST") {
        await rateLimit(env, request, "account:identity:friend-link", ACCOUNT_RATE_LIMITS.friendLink);
        const session = await authenticate(request, env);
        const body = await readJson(request);
        const friendToken = requireOpaqueToken(body.friendToken, /^lf_[A-Za-z0-9_-]{32,}$/);
        const friend = await authenticatedIdentity(env, "friends", "/identity", friendToken, "friendId");
        return await directoryResponse(env, "/identity/link", {
          accountId: session.accountId,
          identityType: "friend",
          externalId: friend.friendId,
        });
      }

      if (url.pathname === "/api/account/link/leaderboard" && request.method === "POST") {
        await rateLimit(env, request, "account:identity:leaderboard-link", ACCOUNT_RATE_LIMITS.leaderboardLink);
        const session = await authenticate(request, env);
        const body = await readJson(request);
        const profileToken = requireOpaqueToken(body.profileToken, /^lpp_[A-Za-z0-9_-]{32,}$/);
        const profile = await authenticatedIdentity(env, "leaderboard", "/profile/identity", profileToken, "profileId");
        return await directoryResponse(env, "/identity/link", {
          accountId: session.accountId,
          identityType: "leaderboard",
          externalId: profile.profileId,
        });
      }

      if (url.pathname === "/api/wallet" && request.method === "GET") {
        await rateLimit(env, request, "wallet:read", ACCOUNT_RATE_LIMITS.wallet);
        const session = await authenticate(request, env);
        return await playerResponse(env, session.accountId, "/wallet/get", {
          accountId: session.accountId,
          sessionId: session.sessionId,
        });
      }

      if (url.pathname === "/api/wallet/cosmetics/purchase" && request.method === "POST") {
        await rateLimit(env, request, "wallet:cosmetic:purchase", ACCOUNT_RATE_LIMITS.walletMutation);
        const session = await authenticate(request, env);
        const body = await readJson(request);
        return await playerResponse(env, session.accountId, "/wallet/cosmetic/purchase", {
          accountId: session.accountId,
          sessionId: session.sessionId,
          cosmeticId: body.cosmeticId,
          clientRequestId: body.clientRequestId,
          ...(body.unlockChannel != null ? { unlockChannel: body.unlockChannel } : {}),
          catalogCountry: requestCatalogCountry(request),
        });
      }

      if (url.pathname === "/api/wallet/cosmetics/select" && request.method === "POST") {
        await rateLimit(env, request, "wallet:cosmetic:select", ACCOUNT_RATE_LIMITS.walletMutation);
        const session = await authenticate(request, env);
        const body = await readJson(request);
        return await playerResponse(env, session.accountId, "/wallet/cosmetic/select", {
          accountId: session.accountId,
          sessionId: session.sessionId,
          cosmeticId: body.cosmeticId,
          catalogCountry: requestCatalogCountry(request),
        });
      }

      if (url.pathname === "/api/admob/ssv") {
        if (env?.LUDOPROOF_ADMOB_SSV_ENABLED !== "true" || !env?.LUDOPROOF_ADMOB_REWARDED_AD_UNIT_ID) {
          throw httpError(503, "ADMOB_SSV_NOT_ENABLED", "server-verified ad rewards are not enabled");
        }
        // Verify Google's ECDSA signature before assigning any player-specific
        // rate limit. A single shared Google callback IP must never exhaust one
        // global bucket for unrelated players in 177 supported countries.
        const verified = await verifyAdMobSsvRequest(request, env);
        // AdMob console URL verification must succeed without granting production value.
        if (verified.isTest === true) {
          return json(200, { ok: true, verified: true, test: true, rewardGranted: false });
        }
        await rateLimitSignedAdMobCallback(env, request, verified.accountId);
        const response = await playerResponse(env, verified.accountId, "/wallet/reward-ad", {
          accountId: verified.accountId,
          transactionId: verified.transactionId,
          requestId: verified.requestId,
          kind: verified.kind,
          gemAmount: verified.gemAmount,
          cosmeticId: verified.cosmeticId,
          catalogCountry: requestCatalogCountry(request),
        });
        console.info("admob-ssv-reward-accepted", { kind: verified.kind, status: response.status });
        return response;
      }



      if (url.pathname === "/api/store/catalog" && request.method === "GET") {
        await rateLimit(env, request, "store:catalog:read", ACCOUNT_RATE_LIMITS.catalogRead);
        await authenticate(request, env);
        const state = await catalogRequest(env, "/catalog/current");
        const country = requestCatalogCountry(request);
        const now = Date.now();
        const items = state.items.filter((item) => isCatalogItemAvailable(item, country, now));
        // Send item IDs which exist in the canonical catalog but must not be
        // resurrected from the Android's legacy bundled catalog. This exposes no
        // draft details, only opaque stable IDs, and lets archive/schedule/region
        // decisions safely suppress stale built-in offers on clients.
        const visibleIds = new Set(items.map((item) => item.id));
        const suppressedIds = [...new Set(state.items
          .filter((item) => !visibleIds.has(item.id))
          .map((item) => item.id))].sort();
        // The same catalog version can expose different items when a scheduled
        // availability window opens or closes. Include the visible ID set so
        // conditional requests cannot preserve a stale filtered catalog forever.
        const visibleIdsHash = (await sha256Hex(items.map((item) => item.id).sort().join("\n"))).slice(0, 16);
        const etag = '"ludopaws-catalog-v' + String(state.catalogVersion) + "-" + country + "-" + visibleIdsHash + '"';
        const headers = catalogHeaders(etag);
        const supplied = String(request.headers.get("if-none-match") ?? "")
          .split(",").map((value) => value.trim());
        if (supplied.includes("*") || supplied.includes(etag)) {
          return new Response(null, { status: 304, headers });
        }
        return new Response(JSON.stringify({
          ok: true,
          catalogVersion: state.catalogVersion,
          items,
          suppressedIds,
          updatedAt: state.updatedAt,
        }), { status: 200, headers });
      }

      if (url.pathname === "/api/admin/store/catalog" && request.method === "GET") {
        await rateLimit(env, request, "store:catalog:admin", ACCOUNT_RATE_LIMITS.catalogAdmin);
        const session = await authenticate(request, env);
        requireCatalogAdmin(session.accountId, env);
        return json(200, await catalogRequest(env, "/catalog/admin-state"));
      }

      if (url.pathname === "/api/admin/store/catalog/versions" && request.method === "GET") {
        await rateLimit(env, request, "store:catalog:admin", ACCOUNT_RATE_LIMITS.catalogAdmin);
        const session = await authenticate(request, env);
        requireCatalogAdmin(session.accountId, env);
        const requested = Number(url.searchParams.get("limit") ?? 30);
        const limit = Number.isSafeInteger(requested) ? Math.max(1, Math.min(100, requested)) : 30;
        return json(200, await catalogRequest(env, "/catalog/history?limit=" + String(limit)));
      }

      const catalogDraftMatch = url.pathname.match(/^\/api\/admin\/store\/catalog\/items\/([a-z0-9][a-z0-9_-]{2,63})$/);
      if (catalogDraftMatch && request.method === "PUT") {
        await rateLimit(env, request, "store:catalog:admin", ACCOUNT_RATE_LIMITS.catalogAdmin);
        const session = await authenticate(request, env);
        requireCatalogAdmin(session.accountId, env);
        const body = await readJson(request);
        if (body?.item?.id !== catalogDraftMatch[1]) {
          throw httpError(400, "CATALOG_ITEM_ID_MISMATCH", "item ID must match the URL path");
        }
        return json(200, await catalogRequest(env, "/catalog/draft/save", {
          expectedRevision: body.expectedRevision,
          actorId: session.accountId,
          reason: body.reason,
          item: body.item,
        }));
      }

      const catalogMutationRoutes = new Map([
        ["/api/admin/store/catalog/publish", "/catalog/publish"],
        ["/api/admin/store/catalog/archive", "/catalog/archive"],
        ["/api/admin/store/catalog/rollback", "/catalog/rollback"],
      ]);
      if (catalogMutationRoutes.has(url.pathname) && request.method === "POST") {
        await rateLimit(env, request, "store:catalog:admin", ACCOUNT_RATE_LIMITS.catalogAdmin);
        const session = await authenticate(request, env);
        requireCatalogAdmin(session.accountId, env);
        const body = await readJson(request);
        return json(200, await catalogRequest(env, catalogMutationRoutes.get(url.pathname), {
          expectedRevision: body.expectedRevision,
          actorId: session.accountId,
          reason: body.reason,
          ...(body.itemId != null ? { itemId: body.itemId } : {}),
          ...(body.targetVersion != null ? { targetVersion: body.targetVersion } : {}),
        }));
      }

      if (url.pathname === "/api/store/status" && request.method === "GET") {
        const billingChecks = {
          ...googlePlayBillingChecks(env),
          accountDirectoryConfigured: Boolean(env?.LUDOPROOF_ACCOUNT_DIRECTORY),
          playerAccountsConfigured: Boolean(env?.LUDOPROOF_PLAYER_ACCOUNTS),
          available: googlePlayBillingEnabled(env) &&
            Boolean(env?.LUDOPROOF_ACCOUNT_DIRECTORY) &&
            Boolean(env?.LUDOPROOF_PLAYER_ACCOUNTS),
        };
        const capabilities = familyCapabilities(env);
        const billingReady = capabilities.purchases && billingChecks.available;
        const adsReady = capabilities.advertising && env?.LUDOPROOF_ADMOB_SSV_ENABLED === "true" &&
          Boolean(env?.LUDOPROOF_ADMOB_REWARDED_AD_UNIT_ID) &&
          (env?.LUDOPROOF_ADMOB_REWARD_ITEM == null || env.LUDOPROOF_ADMOB_REWARD_ITEM === "gems");
        // Backward-compatible v2 flag represents this same 2-Gem placement.
        const rewardedAdsV2Available = adsReady;
        return json(200, {
          ok: true,
          billingAvailable: billingReady,
          billingChecks,
          rewardedAdsAvailable: adsReady,
          rewardedAdsV2Available,
        });
      }

      if (url.pathname === "/api/store/purchases/verify" && request.method === "POST") {
        if (!googlePlayBillingEnabled(env)) {
          return await baseWorker.fetch(request, env);
        }
        await rateLimit(env, request, "store:purchases:verify", ACCOUNT_RATE_LIMITS.storePurchase);
        const session = await authenticate(request, env);
        const body = await readJson(request);
        const productId = requireText(body.productId, "productId");
        const purchaseToken = requireOpaqueToken(body.purchaseToken, /^.{8,2048}$/);
        const obfuscatedAccountId = await billingAccountId(session.accountId);
        const verified = await verifyAndRedeemGooglePlayPurchase(env, {
          productId,
          purchaseToken,
          accountId: obfuscatedAccountId,
          ownerAccountId: session.accountId,
        });
        const purchaseTokenHash = await sha256Hex("ludoproof:google-play-wallet-token:v1:" + purchaseToken);
        let grant;
        try {
          grant = await playerRequest(env, session.accountId, "/wallet/play-purchase", {
            accountId: session.accountId,
            sessionId: session.sessionId,
            productId,
            purchaseTokenHash,
            purchaseLedgerId: verified.purchaseLedgerId,
          });
        } catch (error) {
          // Verification may have started before an account-deletion claim was acquired.
          // If PlayerAccount rejects the final grant because deletion has begun, remove the
          // now-orphaned owner-bound ledger record as part of that deletion race.
          let deletionWonRace = ["ACCOUNT_DELETION_IN_PROGRESS", "ACCOUNT_DELETED"].includes(error?.code);
          if (error?.code === "ACCOUNT_SESSION_INVALID") {
            // The tombstone also revokes sessions, so confirm that this is a deletion replay
            // before cleaning up. A logout/ordinary session expiry must not discard a purchase.
            try {
              const deletionState = await playerRequest(env, session.accountId, "/account/delete-preview", {
                accountId: session.accountId,
                accountDeletionReplayVerified: true,
              });
              deletionWonRace = deletionState.deleted === true || deletionState.deletionPending === true;
            } catch {
              deletionWonRace = false;
            }
          }
          if (deletionWonRace) {
            try {
              if (!env?.LUDOPROOF_STORE_PURCHASES || typeof verified.purchaseLedgerId !== "string") {
                throw httpError(503, "STORE_LEDGER_CLEANUP_FAILED", "purchase ledger cleanup is unavailable after deletion began");
              }
              const ledger = env.LUDOPROOF_STORE_PURCHASES.get(
                env.LUDOPROOF_STORE_PURCHASES.idFromName(verified.purchaseLedgerId),
              );
              await parseInternalResponse(await ledger.fetch(new Request("https://store-purchase-ledger/delete", {
                method: "POST",
                headers: { "content-type": "application/json" },
                body: JSON.stringify({ accountId: obfuscatedAccountId }),
              })), "STORE_LEDGER_CLEANUP_FAILED");
            } catch {
              throw httpError(503, "STORE_LEDGER_CLEANUP_FAILED", "purchase ledger cleanup failed while account deletion was in progress");
            }
          }
          throw error;
        }
        const deliveredLedger = env.LUDOPROOF_STORE_PURCHASES.get(
          env.LUDOPROOF_STORE_PURCHASES.idFromName(verified.purchaseLedgerId),
        );
        await parseInternalResponse(await deliveredLedger.fetch(new Request("https://store-purchase-ledger/delivered", {
          method: "POST", headers: { "content-type": "application/json" },
          body: JSON.stringify({ productId, accountId: obfuscatedAccountId }),
        })), "STORE_DELIVERY_CONFIRMATION_FAILED");
        return json(200, {
          ok: true,
          verified: true,
          productId,
          gemAmount: GOOGLE_PLAY_PRODUCTS[productId]?.gemAmount ?? verified.gemAmount,
          removeAds: GOOGLE_PLAY_PRODUCTS[productId]?.removeAds === true,
          replay: verified.replay === true || grant.replay === true,
          wallet: grant.wallet,
        });
      }

      return await baseWorker.fetch(request, env);
    } catch (error) {
      // SSV callback verification failures need a safe diagnostic code: AdMob's
      // console only reports the HTTP status. Never log callback query, user ID,
      // custom data, signature, or transaction ID.
      if (url.pathname === "/api/admob/ssv") {
        console.warn("admob-ssv-request-rejected", {
          status: Number.isInteger(error?.status) ? error.status : 500,
          code: typeof error?.code === "string" ? error.code : "INTERNAL_ERROR",
        });
      }
      return errorResponse(error);
    }
}

async function deleteAccountData(env, accountId, sessionId, {
  guestRecoveryVerified = false,
  guestDeletionCredentialHash = null,
  accountDeletionReplayVerified = false,
} = {}) {
  const playerDeleteBody = guestRecoveryVerified
    ? { accountId, guestRecoveryVerified: true }
    : accountDeletionReplayVerified
      ? { accountId, sessionId, accountDeletionReplayVerified: true }
      : { accountId, sessionId };

  // Lock wallet/session-backed changes before reading identity links. Directory prepare
  // then blocks identity changes, so an in-flight link is either included in this snapshot
  // or rejected before the cleanup list is read.
  const wallet = await playerRequest(env, accountId, "/account/delete-preview", playerDeleteBody);
  await directoryRequest(env, "/account/delete-prepare", {
    accountId,
    source: guestRecoveryVerified ? "GUEST_CODE" : "SESSION",
    ...(guestDeletionCredentialHash ? { credentialHash: guestDeletionCredentialHash } : {}),
  });
  const links = await directoryRequest(env, "/identity/list", { accountId });

  if (env?.LUDOPAWS_REFERRALS) {
    await parseInternalResponse(await referralRequest(env, "/delete", { accountId }), "REFERRAL_DELETE_FAILED");
  }

  if (wallet.deleted !== true && wallet.purchaseLedgerIds?.length) {
    if (!env?.LUDOPROOF_STORE_PURCHASES) throw httpError(503, "STORE_LEDGER_NOT_CONFIGURED", "purchase ledger is not configured for account deletion");
    const billingId = await billingAccountId(accountId);
    for (const ledgerId of wallet.purchaseLedgerIds) {
      const ledger = env.LUDOPROOF_STORE_PURCHASES.get(env.LUDOPROOF_STORE_PURCHASES.idFromName(ledgerId));
      await parseInternalResponse(await ledger.fetch(new Request("https://store-purchase-ledger/delete", {
        method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ accountId: billingId }),
      })), "STORE_PURCHASE_DELETE_FAILED");
    }
  }

  if (links.friendId) {
    if (!env?.LUDOPROOF_FRIENDS) throw httpError(503, "FRIENDS_NOT_CONFIGURED", "friend service is not configured for account deletion");
    const target = env.LUDOPROOF_FRIENDS.get(env.LUDOPROOF_FRIENDS.idFromName("GLOBAL:V1"));
    await parseInternalResponse(await target.fetch(new Request("https://friends/account/delete", {
      method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ friendId: links.friendId }),
    })), "FRIEND_ACCOUNT_DELETE_FAILED");
  }

  if (links.leaderboardProfileId) {
    if (!env?.LUDOPROOF_LEADERBOARD) throw httpError(503, "LEADERBOARD_NOT_CONFIGURED", "leaderboard is not configured for account deletion");
    const target = env.LUDOPROOF_LEADERBOARD.get(env.LUDOPROOF_LEADERBOARD.idFromName("global"));
    await parseInternalResponse(await target.fetch(new Request("https://leaderboard/profile/delete", {
      method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ profileId: links.leaderboardProfileId }),
    })), "LEADERBOARD_ACCOUNT_DELETE_FAILED");
  }

  // The PlayerAccount tombstone is durable before AccountDirectory identity unlink.
  const result = await playerRequest(env, accountId, "/account/delete", {
    ...playerDeleteBody,
    confirmation: "DELETE_MY_ACCOUNT",
  });
  await directoryRequest(env, "/account/delete", { accountId });
  return result;
}

function normalizeGuestDeletionCredential(value) {
  if (typeof value !== "string" || !/^[a-f0-9]{64}$/i.test(value)) {
    throw httpError(400, "GUEST_DELETION_CODE_INVALID", "guest deletion code must contain 64 hexadecimal characters");
  }
  return value.toLowerCase();
}

function accountWebOrigin(env) {
  const configured = String(env?.LUDOPROOF_ACCOUNT_WEB_ORIGIN ?? "").trim();
  if (!configured) return null;
  try {
    const url = new URL(configured);
    if (url.protocol !== "https:" || url.username || url.password || url.pathname !== "/" || url.search || url.hash) {
      return null;
    }
    return url.origin;
  } catch {
    return null;
  }
}

function withAccountWebCors(request, response, env) {
  const origin = request.headers.get("origin");
  const path = new URL(request.url).pathname;
  const expectedOrigin = accountWebOrigin(env);
  if (!origin || origin !== expectedOrigin || !ACCOUNT_WEB_CORS_PATHS.has(path)) return response;
  const headers = new Headers(response.headers);
  headers.set("access-control-allow-origin", origin);
  headers.set("access-control-allow-methods", "POST, OPTIONS");
  headers.set("access-control-allow-headers", "Authorization, Content-Type");
  headers.set("access-control-max-age", "600");
  const vary = headers.get("vary");
  if (!vary) headers.set("vary", "Origin");
  else if (!vary.split(",").some((value) => value.trim().toLowerCase() === "origin")) {
    headers.set("vary", vary + ", Origin");
  }
  return new Response(response.body, {
    status: response.status,
    statusText: response.statusText,
    headers,
  });
}



async function catalogRequest(env, path, body = null) {
  const namespace = env?.LUDOPROOF_STORE_CATALOG;
  if (!namespace) throw httpError(503, "STORE_CATALOG_NOT_CONFIGURED", "store catalog storage is not configured");
  const target = namespace.get(namespace.idFromName("GLOBAL:V1"));
  const response = await target.fetch(new Request("https://store-catalog" + path, {
    method: body == null ? "GET" : "POST",
    headers: body == null ? {} : { "content-type": "application/json" },
    ...(body == null ? {} : { body: JSON.stringify(body) }),
  }));
  return parseInternalResponse(response, "STORE_CATALOG_UNAVAILABLE");
}

async function moderationRequest(env, path, body) {
  if (!env?.LUDOPROOF_FRIENDS) throw httpError(503, "FRIENDS_NOT_CONFIGURED", "friend storage unavailable");
  const ns = env.LUDOPROOF_FRIENDS;
  const target = ns.get(ns.idFromName("GLOBAL:V1"));
  return parseInternalResponse(await target.fetch(new Request("https://friends" + path, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(body),
  })), "MODERATION_UNAVAILABLE");
}

function requireModerationAdmin(accountId, env) {
  const allowed = String(env?.LUDOPROOF_MODERATION_ADMIN_ACCOUNT_IDS ?? "")
    .split(",").map((id) => id.trim().toLowerCase()).filter(Boolean);
  if (!allowed.length) throw httpError(503, "MODERATION_NOT_CONFIGURED", "moderation access not configured");
  if (!allowed.includes(String(accountId).toLowerCase())) {
    throw httpError(403, "MODERATION_FORBIDDEN", "moderator account required");
  }
}

function requireCatalogAdmin(accountId, env) {
  const raw = String(env?.LUDOPROOF_CATALOG_ADMIN_ACCOUNT_IDS ?? "").trim();
  const allowed = raw.split(",").map((value) => value.trim().toLowerCase())
    .filter((value) => /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/.test(value));
  if (!allowed.length) {
    throw httpError(503, "CATALOG_ADMIN_NOT_CONFIGURED", "catalog admin account allowlist is not configured");
  }
  if (!allowed.includes(String(accountId).toLowerCase())) {
    throw httpError(403, "CATALOG_ADMIN_FORBIDDEN", "catalog administrator permission is required");
  }
}

function requestCatalogCountry(request) {
  const value = String(request.cf?.country ?? request.headers.get("cf-ipcountry") ?? "").trim().toUpperCase();
  return /^[A-Z]{2}$/.test(value) ? value : "ZZ";
}

function isCatalogItemAvailable(item, country, now) {
  if (item?.status !== "PUBLISHED" || item?.contentReady !== true) return false;
  const availability = item.availability;
  if (!availability || typeof availability !== "object") return false;
  if (availability.startsAt != null && Date.parse(availability.startsAt) > now) return false;
  if (availability.endsAt != null && Date.parse(availability.endsAt) <= now) return false;
  if (Array.isArray(availability.blockedCountries) && availability.blockedCountries.includes(country)) return false;
  if (availability.worldwide !== true) {
    if (!Array.isArray(availability.allowedCountries) || !availability.allowedCountries.includes(country)) return false;
  }
  return true;
}

function catalogHeaders(etag) {
  return new Headers({
    "content-type": "application/json; charset=utf-8",
    "cache-control": "private, max-age=60, must-revalidate",
    "etag": etag,
    "vary": "CF-IPCountry",
    "x-content-type-options": "nosniff",
    "x-frame-options": "DENY",
    "referrer-policy": "no-referrer",
  });
}

async function authenticate(request, env, { allowDeletionPending = false } = {}) {
  const token = bearerToken(request);
  const session = await verifyAccessToken(env, token);
  const checked = await playerRequest(env, session.accountId, "/session/check", {
    accountId: session.accountId,
    sessionId: session.sessionId,
    ...(allowDeletionPending ? { allowDeletionPending: true } : {}),
  });
  return { ...session, profile: checked.profile };
}

async function authenticatedIdentity(env, kind, path, token, field) {
  const namespace = kind === "friends" ? env?.LUDOPROOF_FRIENDS : env?.LUDOPROOF_LEADERBOARD;
  if (!namespace) throw httpError(503, kind === "friends" ? "FRIENDS_NOT_CONFIGURED" : "LEADERBOARD_NOT_CONFIGURED", "identity service is not configured");
  const target = namespace.get(namespace.idFromName(kind === "friends" ? "GLOBAL:V1" : "global"));
  const response = await target.fetch(new Request("https://identity" + path, {
    method: "GET",
    headers: { authorization: "Bearer " + token },
  }));
  const value = await parseInternalResponse(response, "IDENTITY_SERVICE_UNAVAILABLE");
  if (typeof value[field] !== "string") throw httpError(503, "IDENTITY_RESPONSE_INVALID", "identity service returned an invalid response");
  return value;
}

async function directoryResponse(env, path, body) {
  return json(200, await directoryRequest(env, path, body));
}

async function playerResponse(env, accountId, path, body) {
  return json(200, await playerRequest(env, accountId, path, body));
}

async function directoryRequest(env, path, body) {
  const namespace = env?.LUDOPROOF_ACCOUNT_DIRECTORY;
  if (!namespace) throw httpError(503, "ACCOUNT_DIRECTORY_NOT_CONFIGURED", "account directory is not configured");
  const target = namespace.get(namespace.idFromName("GLOBAL:V1"));
  const response = await target.fetch(new Request("https://account-directory" + path, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(body),
  }));
  return parseInternalResponse(response, "ACCOUNT_DIRECTORY_UNAVAILABLE");
}

async function playerRequest(env, accountId, path, body) {
  const namespace = env?.LUDOPROOF_PLAYER_ACCOUNTS;
  if (!namespace) throw httpError(503, "PLAYER_ACCOUNT_STORE_NOT_CONFIGURED", "player account storage is not configured");
  const target = namespace.get(namespace.idFromName(accountId.toLowerCase()));
  const response = await target.fetch(new Request("https://player-account" + path, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(body),
  }));
  return parseInternalResponse(response, "PLAYER_ACCOUNT_UNAVAILABLE");
}

async function parseInternalResponse(response, fallbackCode) {
  let value;
  try { value = await response.json(); }
  catch { throw httpError(503, fallbackCode, "account service returned an invalid response"); }
  if (!response.ok) {
    throw httpError(response.status, value?.error ?? fallbackCode, value?.message ?? "account request failed");
  }
  return value;
}

/**
 * Invoked only after AdMob's signed user_id has been cryptographically verified.
 * Keeps unrelated players isolated even when Google delivers SSV from a shared IP.
 * Failed signatures are never allowed to select rate-limit keys.
 */
export async function rateLimitSignedAdMobCallback(env, request, verifiedAccountId) {
  if (typeof verifiedAccountId !== "string" ||
      !/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(verifiedAccountId)) {
    throw httpError(400, "SSV_ACCOUNT_INVALID", "signed reward account is invalid");
  }
  return rateLimit(env, request, "admob:ssv", 120, verifiedAccountId.toLowerCase());
}

async function rateLimit(env, request, scope, limit, verifiedSubject = null) {
  if (!env?.LUDOPROOF_API_GATE) throw httpError(503, "RATE_LIMITER_NOT_CONFIGURED", "API rate limiter is not configured");
  // Anonymous API traffic remains IP-scoped. A cryptographically signed
  // callback uses its verified player ID instead of Google's shared IP.
  const subject = verifiedSubject ?? request.headers.get("cf-connecting-ip") ?? "unknown";
  const key = await sha256Hex("ludoproof:api-rate:v1:" + scope + ":" + subject);
  const id = env.LUDOPROOF_API_GATE.idFromName(key);
  const target = env.LUDOPROOF_API_GATE.get(id);
  let response;
  try {
    response = await target.fetch(new Request("https://gate/check", {
      method: "POST", headers: { "content-type": "application/json" },
      body: JSON.stringify({ limit, periodMs: RATE_WINDOW_MS }),
    }));
  } catch {
    throw httpError(503, "RATE_LIMITER_UNAVAILABLE", "API rate limiter is unavailable");
  }
  let result;
  try { result = await response.json(); }
  catch { throw httpError(503, "RATE_LIMITER_BAD_RESPONSE", "API rate limiter returned invalid data"); }
  if (response.status === 429 || result?.allowed === false) {
    const error = httpError(429, "RATE_LIMITED", "too many requests");
    error.retryAfter = response.headers.get("retry-after") ?? "60";
    throw error;
  }
  if (!response.ok || result?.allowed !== true) throw httpError(503, "RATE_LIMITER_UNAVAILABLE", "API rate limiter rejected the check");
}
async function readJson(request) {
  return readJsonObject(request, MAX_BODY_BYTES);
}
function requireText(value, field) {
  if (typeof value !== "string" || !value.trim()) throw httpError(400, "INVALID_" + field.toUpperCase(), field + " is required");
  return value.trim();
}
function requireAccountId(value) {
  if (typeof value !== "string" || !/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value)) throw httpError(400, "INVALID_ACCOUNT_ID", "account identity is invalid");
  return value.toLowerCase();
}
function requireOpaqueToken(value, regex) {
  if (typeof value !== "string" || !regex.test(value)) throw httpError(400, "INVALID_TOKEN", "token is invalid");
  return value;
}
function requireMethod(request, expected) {
  if (request.method !== expected) {
    const error = httpError(405, "METHOD_NOT_ALLOWED", "method not allowed");
    error.allow = expected;
    throw error;
  }
}
function json(status, body) {
  const headers = new Headers({
    "content-type": "application/json; charset=utf-8",
    "cache-control": "no-store",
    "x-content-type-options": "nosniff",
    "x-frame-options": "DENY",
    "referrer-policy": "no-referrer",
  });
  if (status === 429) headers.set("retry-after", "60");
  return new Response(JSON.stringify(body), { status, headers });
}
function errorResponse(error) {
  const status = Number.isInteger(error?.status) && error.status >= 400 && error.status <= 599 ? error.status : 500;
  const headers = new Headers({
    "content-type": "application/json; charset=utf-8",
    "cache-control": "no-store",
    "x-content-type-options": "nosniff",
    "x-frame-options": "DENY",
    "referrer-policy": "no-referrer",
  });
  if (error?.allow) headers.set("allow", error.allow);
  if (error?.retryAfter) headers.set("retry-after", String(error.retryAfter));
  return new Response(JSON.stringify({
    error: error?.code ?? "INTERNAL_ERROR",
    message: status === 500 && !error?.code ? "internal server error" : String(error?.message ?? "request failed"),
  }), { status, headers });
}
