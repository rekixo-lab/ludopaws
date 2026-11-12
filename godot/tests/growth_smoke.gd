extends SceneTree
## M6 + ads rules with fake Play / server stubs: update prompt states, review gate, install
## referrer -> referral server calls, popup policy, post-win interstitial rules.
##   godot --headless --path . --script res://tests/growth_smoke.gd

const NativePrefs = preload("res://scripts/game/platform/native_prefs.gd")
const Gate = preload("res://scripts/game/growth/prompt_gate.gd")
const Updates = preload("res://scripts/game/growth/update_coordinator.gd")
const Review = preload("res://scripts/game/growth/natural_review.gd")
const Engagement = preload("res://scripts/game/growth/engagement_store.gd")
const Referrals = preload("res://scripts/game/growth/referral_service.gd")
const Ads = preload("res://scripts/game/ads/ads_service.gd")

var failures := 0
var checks := 0

func _check(ok: bool, label: String) -> void:
	checks += 1
	if not ok:
		failures += 1
		printerr("FAIL: ", label)

## A fake LudoPawsPlatform for Play Core calls.
class FakePlay:
	extends Node
	signal playcore(kind: String, json: String)
	var info := {}
	var install := {"versionCode": 17, "versionName": "2.0.0", "firstInstall": 0, "lastUpdate": 100000}
	var started: Array = []
	var completed := 0
	var reviews := 0
	var referrer := {"ok": true, "referrer": "utm_source=x&lp_ref=" + "a".repeat(64), "installAt": 2000, "clickedAt": 1000}
	func appInstallInfo() -> String: return JSON.stringify(install)
	func updateCheck() -> void: _emit.call_deferred("update_info", JSON.stringify(info))
	func updateStart(immediate: bool) -> bool:
		started.append(immediate)
		return true
	func updateComplete() -> void: completed += 1
	func openPlayStore() -> void: pass
	func reviewRequest() -> void:
		reviews += 1
		_emit.call_deferred("review_ready", "{}")
		_emit2.call_deferred()
	func _emit2() -> void: _emit.call_deferred("review", "{\"ok\":true}")
	func installReferrerRead() -> void: _emit.call_deferred("referrer", JSON.stringify(referrer))
	func integrityTokenRequest(project: String, h: String) -> void: _emit.call_deferred("integrity", JSON.stringify({"ok": true, "token": "TOKEN-" + h}))
	func _emit(kind: String, json: String) -> void: playcore.emit(kind, json)

class FakeUi:
	var calls: Array = []
	var answer := "update"
	func prompt(urgent: bool, on_update: Callable) -> void:
		calls.append(["prompt", urgent])
		if answer == "update":
			on_update.call()
	func whats_new(version: String, _notes: Callable) -> void: calls.append(["whats_new", version])
	func install_ready(on_restart: Callable) -> void:
		calls.append(["install_ready"])
		on_restart.call()
	func downloading(p: float) -> void: calls.append(["downloading", p])

func _reset() -> void:
	NativePrefs.reset_cache()
	for f in ["ludopaws_update_experience_v1", "automatic_prompts_v2", "engagement_v1", "friendship_attribution", "referral_popup_v1"]:
		var p = NativePrefs.open(f)
		var e = p.edit()
		for k in p.keys():
			e.remove(k)
		e.commit()
	Gate.reset_session()
	Gate.now_ms = func() -> int: return int(Time.get_unix_time_from_system() * 1000.0)

func _initialize() -> void:
	# ---- In-app update (live PlayUpdateCoordinator / UpdateExperiencePolicy).
	_check(Updates.should_show_whats_new(-1, 17, true) and not Updates.should_show_whats_new(-1, 17, false) and Updates.should_show_whats_new(16, 17, false)
		and not Updates.should_show_whats_new(17, 17, true), "What's New only after a real upgrade")
	_check(Updates.should_prompt(18, -1, 0, 1000, false) and not Updates.should_prompt(18, 18, 1000, 1000 + 3600000, false)
		and Updates.should_prompt(18, 18, 1000, 1000 + 72 * 3600000, false) and Updates.should_prompt(18, 18, 1000, 2000, true),
		"the same update is re-offered after 72 h; a critical one always")
	_reset()
	var play := FakePlay.new()
	root.add_child(play)
	var ui := FakeUi.new()
	var up = Updates.new(play, ui)
	play.info = {"ok": true, "availability": "AVAILABLE", "priority": 0, "availableVersionCode": 18, "flexible": true, "immediate": true}
	await up.on_home()
	_check(ui.calls.size() >= 1 and ui.calls[0] == ["whats_new", "2.0.0"] and NativePrefs.open("ludopaws_update_experience_v1").get_long("seen_version") == 17,
		"after an upgrade: What's New first (%s)" % [ui.calls])
	_check(not ui.calls.has(["prompt", false]), "only one automatic prompt per day (gate)")
	_reset()
	NativePrefs.open("ludopaws_update_experience_v1").edit().put_long("seen_version", 17).commit()
	ui.calls.clear()
	up = Updates.new(play, ui)
	await up.on_home()
	_check(ui.calls == [["prompt", false]] and play.started == [false], "normal update: flexible prompt, background download (%s %s)" % [ui.calls, play.started])
	play.playcore.emit("update_progress", "{\"done\":50,\"total\":100}")
	play.playcore.emit("update_downloaded", "{}")
	_check(ui.calls.has(["downloading", 0.5]) and ui.calls.has(["install_ready"]) and play.completed >= 1, "downloaded: Restart to update completes the install")
	_reset()
	NativePrefs.open("ludopaws_update_experience_v1").edit().put_long("seen_version", 17).commit()
	ui.calls.clear()
	play.started.clear()
	play.info["priority"] = 5
	up = Updates.new(play, ui)
	await up.on_home()
	_check(ui.calls == [["prompt", true]] and play.started == [true], "critical (priority 5) update: immediate flow")
	_reset()
	NativePrefs.open("ludopaws_update_experience_v1").edit().put_long("seen_version", 17).commit()
	ui.calls.clear()
	play.info = {"ok": true, "availability": "NOT_AVAILABLE"}
	up = Updates.new(play, ui)
	await up.on_home()
	_check(ui.calls.is_empty(), "no update: nothing shown")

	# ---- Natural review (live NaturalReviewController + AutomaticPromptGate).
	_reset()
	Engagement.account_id = func() -> String: return ""
	var day := 86400000
	Engagement.completed("m1", 10 * day)
	Engagement.completed("m2", 10 * day)
	_check(not Review.eligible(20 * day), "review needs 3 matches")
	Engagement.completed("m3", 10 * day)
	_check(not Review.eligible(20 * day), "review needs 2 different days")
	Engagement.completed("m4", 11 * day)
	_check(Review.eligible(20 * day), "3 matches on 2 days -> eligible (win or loss)")
	var asked: bool = await Review.request(play, 20 * day)
	_check(asked and play.reviews == 1 and Engagement.last_review_attempt() == 20 * day, "the Play review sheet is requested once")
	Gate.reset_session()
	_check(not Review.eligible(20 * day + 89 * day) and Review.eligible(20 * day + 90 * day), "then not again for 90 days")
	_reset()
	Gate.now_ms = func() -> int: return 5 * day + 1000
	_check(Gate.reserve("review"), "the gate allows one prompt")
	Gate.presented("review")
	Gate.release("review")
	_check(not Gate.reserve("referral"), "one automatic prompt per session / day")

	# ---- Install referrer -> server (live ReferralRepository), with fake account + server.
	_reset()
	var srv := FakeServer.new()
	var account := FakeAccount.new(srv)
	root.add_child(account)
	AgeAdult.set_adult(true)
	srv.replies["/api/referrals/status"] = [{"verificationEnabled": true, "enabled": true, "googleLinked": true, "consented": true, "campaign": "friendship-bundle-v1", "state": "INVITE"}]
	srv.replies["/api/referrals/enroll"] = [{"ok": true}]
	srv.replies["/api/referrals/challenge"] = [{"challenge": "C1", "cloudProjectNumber": "314526321614", "requestHash": "H1", "bound": false}]
	srv.replies["/api/referrals/bind"] = [{"bound": true}]
	var ref = Referrals.new(account, play)
	await ref.enroll()
	_check(ref.has_consent(account.id), "consent stored with the live terms version")
	var result: String = await ref.refresh_attribution(true)
	var challenge = srv.body("/api/referrals/challenge")
	var bind = srv.body("/api/referrals/bind")
	_check(result == "BOUND" and challenge == {"click": "a".repeat(64), "installAt": 2000, "clickedAt": 1000} and bind == {"challenge": "C1", "integrityToken": "TOKEN-H1"},
		"install referrer click + Play times -> challenge -> integrity token -> bind (%s %s %s)" % [result, challenge, bind])
	_check(await ref.refresh_attribution(true) == "INSTALL_ALREADY_USED", "an installation binds once")
	_check(Referrals.click_from_referrer("utm_source=google-play") == "" and Referrals.click_from_referrer("lp_ref=xyz") == "", "no invitation without a valid lp_ref")
	_check(Referrals.can_show_automatically(true, "INVITE", 0, 0, 0, 10 * day, false) and not Referrals.can_show_automatically(true, "INVITE", 3, 0, 0, 10 * day, false)
		and not Referrals.can_show_automatically(true, "INVITE", 1, 10 * day - 1000, 0, 10 * day, false)
		and not Referrals.can_show_automatically(true, "INVITE", 1, 0, 10 * day - 6 * day, 10 * day, false)
		and not Referrals.can_show_automatically(true, "REWARDED", 0, 0, 0, 10 * day, false), "referral popup: 3 times max, 1 day apart, 7 days after Not now, only INVITE")
	AgeAdult.set_adult(false)
	_check(await ref.refresh_attribution(true) == "", "no verification outside the adult age band")

	# ---- Post-win interstitial rules (live AdBreakPolicy + exit gate).
	var ads = Ads.new()
	var fake_ads := FakeAdsPlatform.new()
	root.add_child(fake_ads)
	ads._platform = fake_ads
	ads.interstitial_enabled = true
	ads.store = FakeStore.new()
	_check(not await ads.post_win_exit("m1"), "no break before a completed match")
	ads.completed("m1")
	ads._entitlement = {"accountId": "", "adsRemoved": false, "at": Time.get_ticks_msec()}
	_check(await ads.post_win_exit("m1") and fake_ads.shown == 1, "after a completed match with a fresh entitlement: one post-win ad")
	_check(not await ads.post_win_exit("m1"), "never twice for the same match")
	NativePrefs.open("ludopaws_ad_breaks").edit().put_long("last_shown_at", 0).commit()
	ads.completed("m2")
	ads._entitlement = {"accountId": "", "adsRemoved": true, "at": Time.get_ticks_msec()}
	_check(not await ads.post_win_exit("m2"), "Remove Ads: never an interstitial")
	ads._entitlement = {"accountId": "", "adsRemoved": false, "at": Time.get_ticks_msec() - 61000}
	_check(not await ads.post_win_exit("m2"), "a stale (> 60 s) entitlement skips the ad")
	ads._entitlement = {"accountId": "", "adsRemoved": false, "at": Time.get_ticks_msec()}
	ads._quiet_until = Time.get_ticks_msec() + 90000
	_check(not await ads.post_win_exit("m2"), "90 s quiet after a rewarded ad")
	ads._quiet_until = 0
	fake_ads.is_ready = false
	_check(not await ads.post_win_exit("m2"), "no ad loaded: the game never waits")
	_reset()
	# Static callables must not outlive this test script's lambdas (shutdown crash).
	Gate.now_ms = Callable()
	Engagement.account_id = Callable()
	print("Growth smoke: %d checks, %d failures" % [checks, failures])
	quit(1 if failures > 0 else 0)

class AgeAdult:
	static func set_adult(on: bool) -> void:
		var AgeEntry = load("res://scripts/game/safety/age_entry.gd")
		AgeEntry.default_path = "user://growth_test_age.cfg"
		AgeEntry.new().save_choice(30 if on else 10)

class FakeServer:
	var sent: Array = []
	var replies := {}
	func body(path: String):
		for r in sent:
			if r[0] == path:
				return r[1]
		return null
	func handle(path: String, body) -> Dictionary:
		sent.append([path, body])
		var q: Array = replies.get(path, [])
		return {"ok": true, "data": q.pop_front()} if not q.is_empty() else {"ok": false, "error": "NOT_FOUND"}

class FakeService:
	var server
	var id := "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
	func require_session() -> Dictionary: return {"ok": true, "session": {"accessToken": "t", "accountId": id}}
	func ensure_guest_session() -> Dictionary: return require_session()
	func _request(_m: String, path: String, body, _t) -> Dictionary: return server.handle(path, body)

class FakeAccount:
	extends Node
	var service
	var id := "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
	func _init(srv) -> void:
		service = FakeService.new()
		service.server = srv
	func session() -> Dictionary: return {"accountId": id}

class FakeAdsPlatform:
	extends Node
	signal ads(kind: String, detail: String)
	var is_ready := true
	var shown := 0
	func adsInterstitialReady() -> bool: return is_ready
	func adsShowInterstitial() -> void:
		shown += 1
		(func(): ads.emit("interstitial_finished", "true")).call_deferred()
	func adsLoadInterstitial(_u: String) -> void: pass

class FakeStore:
	var inventory = {"ads_removed": false}
	func refresh_wallet(_ids := []) -> Dictionary: return {"ok": false}
