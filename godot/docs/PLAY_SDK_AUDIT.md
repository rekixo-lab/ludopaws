# Play SDKs and AdMob placements — audit (2026-11-11, before M6 changes)

Sources: V2 `android/build/build.gradle` + merged release manifest (local unsigned release
export), live `android/app/build.gradle.kts` + source, and the shipped **v16 AAB**
(`~/Downloads/ludopaws-v16-signed-aab`, manifest via bundletool).

## 1. SDKs and versions

| SDK | Live v16 | V2 now | Notes |
|---|---|---|---|
| androidx.credentials (+ play-services-auth) | 1.6.0 | 1.6.0 | same |
| googleid | 1.1.1 | 1.1.1 | same |
| play-services-games-v2 | 22.1.0 | 22.1.0 | same |
| billing | 9.1.0 | 9.1.0 | same |
| play-services-ads | 25.5.0 | 25.5.0 | same |
| user-messaging-platform | 4.0.0 | 4.0.0 | same |
| **app-update** | 2.1.0 | — | M6 (this change) |
| **review** | 2.0.2 | — | M6 |
| **installreferrer** | 2.2 | — | M6 (referrals) |
| **play integrity** | 1.6.0 | — | used by live referral verification (bind) → added with referrals |
| okhttp / guava / activity | 4.12.0 / 33.7.2 / 1.13.0 | — | live HTTP / util; V2 uses Godot HTTPRequest (no equivalent needed) |

Manifest components only in v16: `PlayCoreDialogWrapperActivity` (comes with app-update /
review) — arrives with M6. Everything else from libraries matches (AdActivity, AdService,
OPTIMIZE_* ad flags, games resolution / shortcuts, billing).

## 2. Play Games v2 — same as live
- `com.google.android.gms.games.APP_ID = @string/game_services_project_id` ✓ (id from config / CI).
- `SUPPRESS_GAME_PROFILE_CREATION = true` ✓; `PlayGamesInitProvider` removed ✓.
- Init only after a durable adult age choice (band 3) and only in `com.rekixo.ludopaws`
  with a positive project id (`StartupPlayGames.configured`) ✓. Automatic sign-in is read
  with `isAuthenticated()`; explicit sign-in only from Profile ✓.
- Difference (documented, not behavioural): live recreates Home once after SDK init so the
  SDK's automatic authenticator attaches; V2 initializes before the Godot activity is created
  (same effect, no activity recreate). Untested on device (production package only).

## 3. Guest account flow — same as live
Guest at startup (StartupSessionCoordinator) → Save this account to Google (link, guest kept,
GOOGLE mode) / Restore an account saved to Google (login, identity recovery pending until
Friend / leaderboard identities are restored) — same endpoints, nonce, error texts; live
AccountSessionStore storage reused. Google sign-in untested on device.

## 4. AD_ID / ADSERVICES permissions vs v16
v16 merged manifest permissions: ACCESS_NETWORK_STATE, FOREGROUND_SERVICE, INTERNET,
USE_BIOMETRIC, USE_FINGERPRINT, VIBRATE, WAKE_LOCK, BILLING, BIND_GET_INSTALL_REFERRER_SERVICE,
DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION — **no AD_ID, no ACCESS_ADSERVICES_*, no RECORD_AUDIO**.
V2 release: the same list except BIND_GET_INSTALL_REFERRER_SERVICE (comes with installreferrer
in M6). → **Matches v16.**

## 5. Ad placements (V2 vs live)

| Placement | V2 file | When | Type | Rules | Live |
|---|---|---|---|---|---|
| Wallet Free Gems | shop_pages.gd `_watch_gem_ad` → ads_service.show_rewarded() | Wallet "Watch ad" | Rewarded | server rewardedAdsV2Available; credit only via SSV (2 Gems) | StoreActivity.requestRewardedAd — same |
| Shop ad unlocks | shop_pages.gd `_act WATCH_AD` → show_rewarded(cosmeticId) | Shop item card / chooser | Rewarded | progress from server; astral 15, pearl_paw 20, goat_lumi 10, donkey 15 | same counts (catalog) |
| Daily Ad Bonus | rewards_page.gd `_watch_bonus` → show_daily_bonus() | Rewards card | Rewarded | server bonus ticket, once per reward day, SSV | RewardsActivity.bonus — same |
| Post-win | match_pages.gd `_after_result` → ads_service.post_win_exit() | result card Home / Play Again | Interstitial | AdBreakPolicy (≥1 completed match, 5 min apart, 90 s after a rewarded ad), fresh adsRemoved check ≤ 60 s, demo excluded | OfflineGameActivity / MainActivity exit + rematch — same; live also shows at most once per match (V2 adds that guard in this change) |
| App open / banner / in-match / shop flow | — | never | — | — | never |

### Differences between the brief and live (kept as live, per "live नियम ही रखो")
1. **"Free Gems max 10/day"** — live v16 has **no client cap** for the 2-Gem rewarded ad
   ("No daily quota for updated 2-Gem rewards"); the server credits every signed callback and
   only reports `rewardedAdsToday` for old builds. V2 keeps live (no cap).
2. **"Never for children (family / age)"** — live does **not** hide ads by age band; every
   user gets the most restrictive family treatment (child-directed, max rating G, consent
   under age, non-personalized). V2 keeps live.
3. **"Never after the first match"** — live's AdBreakPolicy allows the post-win ad after the
   first completed match of a session (grace 0). V2 keeps live; the V2-only Demo never shows ads.
