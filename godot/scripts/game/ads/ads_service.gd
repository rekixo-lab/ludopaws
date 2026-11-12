extends RefCounted
## Ads (live RewardedAdsController + PendingAdRewardStore + AdBreakSession +
## PostWinInterstitialExitGate + FamilyAdsPrivacy) through the LudoPawsPlatform plugin
## (Mobile Ads 25.5.0, UMP 4.0.0). Rewarded ads are server-verified: the ad carries the
## account id + custom data (SSV) and only the server's signed callback credits Gems or ad
## progress; the client keeps recovery hints and confirms them against the wallet's
## creditedAdRequestIds. Every user gets the most restrictive family ad treatment.
##
## Enabled only on Android with [features] ads_enabled (CI LUDOPROOF_ADS_ENABLED); debug
## builds always use Google's TEST ad units. Desktop / tests: the DesktopBackend stand-in
## credits directly. UNTESTED ON DEVICE with real ads.

const NativePrefs = preload("res://scripts/game/platform/native_prefs.gd")
const GameApi = preload("res://scripts/game/online/game_api.gd")
const TEST_REWARDED := "ca-app-pub-3940256099942544/5224354917"
const TEST_INTERSTITIAL := "ca-app-pub-3940256099942544/1033173712"
const PENDING_PREFS := "pending_ad_rewards_v1"
const BREAK_PREFS := "ludopaws_ad_breaks"
const MAX_RECORDS := 64
const BETWEEN_ADS_MS := 300000
const REWARDED_QUIET_MS := 90000
const MAX_ENTITLEMENT_AGE_MS := 60000

var store   # StoreService
var removed_ads := false
var _platform = null
var rewarded_unit := TEST_REWARDED
var interstitial_unit := TEST_INTERSTITIAL
var interstitial_enabled := false
var server_available := false
var busy := false
## AdBreakSession (process-local completed matches, last shown time persisted).
var _completed: Array = []
var _reserved := false
var _quiet_until := 0
var _entitlement := {}   # {accountId, adsRemoved, at}

func _init(store_service = null) -> void:
	store = store_service
	if Engine.has_singleton("LudoPawsPlatform"):
		var cfg := ConfigFile.new()
		cfg.load("res://config/release.local.cfg")
		if bool(cfg.get_value("features", "ads_enabled", false)):
			_platform = Engine.get_singleton("LudoPawsPlatform")
			interstitial_enabled = bool(cfg.get_value("features", "interstitial_ads_enabled", false))
			if not OS.is_debug_build():
				rewarded_unit = str(cfg.get_value("admob", "rewarded_unit_id", ""))
				interstitial_unit = str(cfg.get_value("admob", "interstitial_unit_id", ""))

func _account():
	var tree := Engine.get_main_loop() as SceneTree
	return tree.root.get_node_or_null("Account") if tree != null else null

## The next "ads" plugin event of one of `kinds` -> detail.
func _next(kinds: Array) -> Array:
	while true:
		var args: Array = await _platform.ads
		if kinds.has(args[0]):
			return args
	return []

## Consent + SDK + a preloaded rewarded ad (live prepare).
func prepare(show_form := false) -> void:
	if _platform == null:
		return
	_platform.adsPrepare(show_form)
	var r := await _next(["consent"])
	if r[1] == "ready":
		_platform.adsLoadRewarded(rewarded_unit)
		if interstitial_enabled:
			_platform.adsLoadInterstitial(interstitial_unit)

func rewarded_available() -> bool:
	if store != null and store.desktop != null:
		return true
	return _platform != null and server_available

## Live showPrivacyOptions (Settings / Shop).
func privacy_options() -> void:
	if _platform != null:
		_platform.adsPrivacyOptions()

# ---------------------------------------------------------------- rewarded

## Live StoreActivity.requestRewardedAd: Gems ("") or one cosmetic's ad progress.
func show_rewarded(cosmetic_id := "") -> Dictionary:
	if store != null and store.desktop != null:
		store.desktop.credit_rewarded_ad(cosmetic_id)
		await store.refresh_wallet()
		return {"ok": true}
	var custom := {"requestId": GameApi.new_request_id(), "kind": "gems" if cosmetic_id.is_empty() else "cosmetic"}
	if not cosmetic_id.is_empty():
		custom["cosmeticId"] = cosmetic_id
	return await _watch(custom, cosmetic_id)

## Live RewardsActivity daily ad bonus: a server ticket first, then the ad carrying it.
func show_daily_bonus() -> Dictionary:
	if store != null and store.desktop != null:
		var credited: bool = store.desktop.credit_daily_bonus()
		await store.refresh_wallet()
		return {"ok": credited}
	var ticket: Dictionary = await store.bonus_ticket()
	if not ticket.get("ok", false) or not (ticket.get("receipt") is Dictionary):
		return {"ok": false, "message": str(ticket.get("message", "Today's ad bonus is already claimed."))}
	var custom := {"kind": "gems", "dailyTicket": ticket["receipt"].get("ticket", ""), "requestId": GameApi.new_request_id()}
	return await _watch(custom, "", int(ticket["receipt"].get("day", -1)))

func _watch(custom: Dictionary, cosmetic_id: String, daily_day := -1) -> Dictionary:
	if _platform == null or busy:
		return {"ok": false, "message": "Rewarded ads are currently unavailable." if _platform == null else "An ad request is already loading or open."}
	var account = _account()
	if account == null or not account.available():
		return {"ok": false, "message": "Rewarded ads are currently unavailable."}
	busy = true
	var status: Dictionary = await account.service.store_status()
	if not status.get("ok", false) or not bool(status.get("data", {}).get("rewardedAdsV2Available", false)):
		busy = false
		return {"ok": false, "message": "Rewarded ads are currently unavailable from the service. Please try again later."}
	var v: Dictionary = await account.service.ensure_guest_session()
	var wallet: Dictionary = await store.refresh_wallet()
	if not v.get("ok", false) or not wallet.get("ok", false) or wallet.get("wallet", {}).get("accountId") != v["session"]["accountId"]:
		busy = false
		return {"ok": false, "message": "Your account changed. Reopen the store before watching an ad."}
	var owner: String = v["session"]["accountId"]
	if not cosmetic_id.is_empty() and store.inventory.is_owned(cosmetic_id):
		busy = false
		return {"ok": false, "message": "You already own this item."}
	var text := JSON.stringify(custom)
	if text.to_utf8_buffer().size() > 1024:
		busy = false
		return {"ok": false, "message": "The account reward request is invalid. No ad was requested."}
	if not _platform.adsRewardedReady():
		await prepare(true)
		var loaded := await _next(["rewarded_loaded", "rewarded_failed"])
		if loaded[0] != "rewarded_loaded":
			busy = false
			return {"ok": false, "message": "The ad could not load in time. Tap Watch to retry."}
	var record := {"accountId": owner, "requestId": custom["requestId"], "cosmeticId": cosmetic_id,
		"createdAt": int(Time.get_unix_time_from_system() * 1000.0), "earned": false, "abandoned": false, "dailyRewardDay": daily_day if daily_day >= 0 else null}
	_put_record(record)
	_quiet_until = Time.get_ticks_msec() + REWARDED_QUIET_MS
	load("res://scripts/game/growth/referral_service.gd").fullscreen_interaction()
	_platform.adsShowRewarded(owner, text)
	var earned := false
	while true:
		var ev := await _next(["rewarded_earned", "rewarded_finished"])
		if ev[0] == "rewarded_earned":
			earned = true
			record["earned"] = true
			_put_record(record)
		else:
			break
	if not earned:
		record["abandoned"] = true
		_put_record(record)
	_quiet_until = Time.get_ticks_msec() + REWARDED_QUIET_MS
	busy = false
	await recover_pending(owner)
	_platform.adsLoadRewarded(rewarded_unit)
	return {"ok": earned}

## Live recoverPendingAdRewards: ask the wallet about our receipts, drop confirmed hints.
func recover_pending(owner: String) -> void:
	var ids: Array = records(owner).map(func(r): return r["requestId"])
	var res: Dictionary = await store.refresh_wallet(ids)
	if res.get("ok", false):
		confirm(owner, Array(res.get("wallet", {}).get("creditedAdRequestIds", [])))

# ---------------------------------------------------------------- PendingAdRewardStore

static func _uuid(v) -> bool:
	return RegEx.create_from_string("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$").search(str(v)) != null

## Recovery hints (never credits) with live retention: earned 7 days, abandoned 5 min, else 1 day.
static func records(owner: String) -> Array:
	var parsed = JSON.parse_string(str(NativePrefs.open(PENDING_PREFS).get_string("rewards:" + owner, "[]")))
	if not (parsed is Array):
		return []
	var now := int(Time.get_unix_time_from_system() * 1000.0)
	return parsed.filter(func(r): return r is Dictionary and r.get("accountId") == owner and _uuid(r.get("requestId")) \
		and now - int(r.get("createdAt", 0)) < (604800000 if r.get("earned", false) else (300000 if r.get("abandoned", false) else 86400000)))

static func _put_record(record: Dictionary) -> void:
	if not _uuid(record["accountId"]) or not _uuid(record["requestId"]):
		return
	if NativePrefs.open("ludoproof_profile").get_string_set("deleted_profile_accounts", []).has(record["accountId"]):
		return
	var all: Array = records(record["accountId"]).filter(func(r): return r["requestId"] != record["requestId"])
	all.append(record)
	NativePrefs.open(PENDING_PREFS).edit().put_string("rewards:" + record["accountId"], JSON.stringify(all.slice(-MAX_RECORDS))).commit()

static func confirm(owner: String, request_ids: Array) -> void:
	if request_ids.is_empty():
		return
	var kept: Array = records(owner).filter(func(r): return not request_ids.has(r["requestId"]))
	NativePrefs.open(PENDING_PREFS).edit().put_string("rewards:" + owner, JSON.stringify(kept)).commit()

static func remove_account(owner: String) -> void:
	NativePrefs.open(PENDING_PREFS).edit().remove("rewards:" + owner).commit()

# ---------------------------------------------------------------- post-win interstitial

## AdBreakSession.completed (local and online finished matches).
func completed(match_id: String) -> void:
	if not match_id.is_empty() and not _completed.has(match_id):
		_completed.append(match_id)
		while _completed.size() > 64:
			_completed.pop_front()

## AdBreakPolicy.eligible: one completed match, 5 min between ads, 90 s after a rewarded ad.
func can_show_break(now_ms := -1) -> bool:
	var now := now_ms if now_ms >= 0 else int(Time.get_unix_time_from_system() * 1000.0)
	var last: int = NativePrefs.open(BREAK_PREFS).get_long("last_shown_at", 0)
	return not _reserved and Time.get_ticks_msec() >= _quiet_until and _completed.size() >= 1 \
		and (last <= 0 or (now >= last and now - last >= BETWEEN_ADS_MS))

## PostWinInterstitialExitGate.prepare: a recent authenticated adsRemoved answer.
func prepare_exit_gate() -> void:
	var account = _account()
	if _platform == null or account == null or not account.available():
		return
	var v: Dictionary = await account.service.ensure_guest_session()
	var w: Dictionary = await store.refresh_wallet()
	if v.get("ok", false) and w.get("ok", false) and w.get("wallet", {}).get("accountId") == v["session"]["accountId"] and w["wallet"].get("adsRemoved") is bool:
		_entitlement = {"accountId": v["session"]["accountId"], "adsRemoved": w["wallet"]["adsRemoved"], "at": Time.get_ticks_msec()}
	else:
		_entitlement = {}

## interceptIfReady: shows the post-win ad only with a fresh "ads not removed" answer for
## the current account, an ad ready and the break policy met. Returns after the ad.
var _shown_for_match := ""

func post_win_exit(match_id := "") -> bool:
	if not match_id.is_empty() and match_id == _shown_for_match:
		return false   # live: at most one post-win ad per match
	if _platform == null or not interstitial_enabled or not _platform.adsInterstitialReady() or not can_show_break():
		return false
	var account = _account()
	var owner: String = account.session().get("accountId", "") if account != null else ""
	if _entitlement.is_empty() or _entitlement["accountId"] != owner or Time.get_ticks_msec() - int(_entitlement["at"]) > MAX_ENTITLEMENT_AGE_MS \
			or _entitlement["adsRemoved"] or store.inventory.ads_removed:
		prepare_exit_gate()
		return false
	_reserved = true
	load("res://scripts/game/growth/referral_service.gd").fullscreen_interaction()
	_platform.adsShowInterstitial()
	var ev := await _next(["interstitial_finished"])
	_reserved = false
	if ev[1] == "true":
		_shown_for_match = match_id
		NativePrefs.open(BREAK_PREFS).edit().put_long("last_shown_at", int(Time.get_unix_time_from_system() * 1000.0)).commit()
	_platform.adsLoadInterstitial(interstitial_unit)
	return ev[1] == "true"
