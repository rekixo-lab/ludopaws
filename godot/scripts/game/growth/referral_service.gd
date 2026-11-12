extends RefCounted
## Live ReferralRepository + ReferralPopupPolicy / ReferralPopupSession. Server APIs
## /api/referrals/{status,offer,invite,enroll,withdraw,challenge,bind}; optional verification
## (adult band, Google-linked account, explicit consent with the live terms version) reads the
## Play install referrer once, then binds it with a Play Integrity token for the server's
## challenge. Only the inviter is rewarded, by the server. Local state in the live files
## `friendship_attribution` and `referral_popup_v1`.

const NativePrefs = preload("res://scripts/game/platform/native_prefs.gd")
const AgeEntry = preload("res://scripts/game/safety/age_entry.gd")
const TERMS_VERSION := "friendship-2026-10-27"
const CAMPAIGN := "friendship-bundle-v1"
const TIMED_CAMPAIGN := "friendship-bundle-2"
const DAY := 86400000
const PUBLIC_SHARE_HOST := "ludopaws.rekixo.com"

var account      # Account autoload
var platform     # LudoPawsPlatform (null on desktop)
var attributing := false
## ReferralPopupSession (process-local).
static var completed_matches: Array = []
static var pending_home := false
static var automatic_shown := false
static var fullscreen_at := -1

func _init(account_node, platform_singleton = null) -> void:
	account = account_node
	platform = platform_singleton

static func _prefs():
	return NativePrefs.open("friendship_attribution")

static func adult() -> bool:
	return AgeEntry.new().optional_google_allowed()

func _request(operation: String, body = null) -> Dictionary:
	var v: Dictionary = await account.service.require_session()
	if not v.get("ok", false):
		return v
	return await account.service._request("GET" if operation == "status" else "POST", "/api/referrals/" + operation,
		body if operation != "status" else null, v["session"]["accessToken"])

func _owner() -> String:
	return str(account.session().get("accountId", ""))

func has_consent(account_id: String) -> bool:
	var p = _prefs()
	return p.get_string("consented_account", "") == account_id and p.get_string("consent_terms", "") == TERMS_VERSION

## ReferralRepository.status (+ local verification state and consent).
func status(create_account := false) -> Dictionary:
	if create_account:
		await account.service.ensure_guest_session()
	var owner := _owner()
	if owner.is_empty():
		return {"enabled": false}
	var res := await _request("status")
	if not res.get("ok", false):
		return {"enabled": false, "error": res.get("error", ""), "message": res.get("message", "")}
	var s: Dictionary = res["data"]
	var p = _prefs()
	s["verificationState"] = p.get_string("verification_state", "NOT_STARTED") if p.get_string("verification_account", "") == owner else "NOT_STARTED"
	s["localConsent"] = has_consent(owner)
	s["localAccountId"] = owner
	return s

## Invite link from the server (only an https share link on our hosts, public host).
func invite(timed := false) -> String:
	var res := await _request("invite", {"campaign": TIMED_CAMPAIGN} if timed else {})
	var url := str(res.get("data", {}).get("shareUrl", "")) if res.get("ok", false) else ""
	var rx := RegEx.create_from_string("^https://(ludopaws\\\\.rekixo\\\\.com|api\\\\.ludopaws\\\\.rekixo\\\\.com)/r/[a-f0-9]{64}$")
	if rx.search(url) == null:
		return ""
	return url.replace("https://api.ludopaws.rekixo.com/", "https://%s/" % PUBLIC_SHARE_HOST)

func enroll() -> Dictionary:
	if not adult():
		return {"ok": false, "message": "Review Age and privacy in Settings before using optional invitation verification."}
	var owner := _owner()
	var res := await _request("enroll", {"termsVersion": TERMS_VERSION, "consent": true})
	if res.get("ok", false) and _owner() == owner:
		_prefs().edit().put_string("consented_account", owner).put_string("consent_terms", TERMS_VERSION) \
			.put_string("consent_revision", load("res://scripts/game/online/game_api.gd").new_request_id()) \
			.put_int("attempts", 0).put_long("next_attempt_at", 0).commit()
	return res

func withdraw() -> Dictionary:
	var owner := _owner()
	NativePrefs.open("referral_popup_v1").edit().put_int("%s:%s:count" % [CAMPAIGN, owner], 3).commit()
	_prefs().edit().remove("consented_account").remove("consent_terms") \
		.put_string("consent_revision", load("res://scripts/game/online/game_api.gd").new_request_id()) \
		.remove("click").remove("install_at").remove("clicked_at").remove("referrer_read") \
		.put_string("verification_account", owner).put_string("verification_state", "STOPPED").commit()
	return await _request("withdraw", {})

func _state(owner: String, value: String) -> void:
	_prefs().edit().put_string("verification_account", owner).put_string("verification_state", value).commit()

func _retry_later(owner: String, value: String) -> void:
	var attempts := mini(_prefs().get_int("attempts", 0) + 1, 8)
	_prefs().edit().put_int("attempts", attempts) \
		.put_long("next_attempt_at", int(Time.get_unix_time_from_system() * 1000.0) + mini(900000, 15000 * (1 << attempts))).commit()
	_state(owner, value)

## Live refreshAttribution: adult, consented, linked; install referrer once; challenge; bind.
func refresh_attribution(force := false) -> String:
	var owner := _owner()
	if not adult() or owner.is_empty() or not has_consent(owner) or attributing:
		return ""
	var p = _prefs()
	var delay: int = p.get_long("next_attempt_at", 0) - int(Time.get_unix_time_from_system() * 1000.0)
	if not force and delay > 0 and delay <= 3600000:
		return ""
	if p.get_bool("bound", false):
		_state(owner, "INSTALL_ALREADY_USED")
		return "INSTALL_ALREADY_USED"
	attributing = true
	_state(owner, "VERIFYING")
	var result := await _attribute(owner)
	attributing = false
	return result

func _attribute(owner: String) -> String:
	var s := await status()
	if not (s.get("verificationEnabled", s.get("enabled", false)) and s.get("googleLinked", false) and s.get("consented", false)):
		_state(owner, "UNAVAILABLE")
		return "UNAVAILABLE"
	var p = _prefs()
	if not p.get_bool("referrer_read", false):
		var ref := await read_referrer()
		if not ref.get("ok", false):
			_retry_later(owner, "STORE_UNAVAILABLE")
			return "STORE_UNAVAILABLE"
		var e = p.edit().put_bool("referrer_read", true)
		var click := click_from_referrer(str(ref.get("referrer", "")))
		if not click.is_empty() and int(ref.get("installAt", 0)) > 0 and int(ref.get("clickedAt", 0)) > 0:
			e.put_string("click", click).put_long("install_at", int(ref["installAt"])).put_long("clicked_at", int(ref["clickedAt"]))
		e.commit()
	var click = p.get_string("click", null)
	if click == null:
		_state(owner, "NO_INVITATION")
		return "NO_INVITATION"
	var challenge := await _request("challenge", {"click": click, "installAt": p.get_long("install_at", 0), "clickedAt": p.get_long("clicked_at", 0)})
	if not challenge.get("ok", false):
		return _bind_error(owner, challenge)
	if not challenge["data"].get("bound", false):
		var token := await integrity_token(str(challenge["data"].get("cloudProjectNumber", "")), str(challenge["data"].get("requestHash", "")))
		if token.is_empty():
			_retry_later(owner, "RETRY_LATER")
			return "RETRY_LATER"
		var bound := await _request("bind", {"challenge": challenge["data"].get("challenge", ""), "integrityToken": token})
		if not bound.get("ok", false):
			return _bind_error(owner, bound)
	p.edit().put_bool("bound", true).put_int("attempts", 0).put_long("next_attempt_at", 0).remove("click").remove("install_at").remove("clicked_at").commit()
	_state(owner, "BOUND")
	return "BOUND"

func _bind_error(owner: String, res: Dictionary) -> String:
	var value: String = {"REFERRAL_NOT_ELIGIBLE": "NOT_ELIGIBLE", "SELF_REFERRAL": "NOT_ELIGIBLE", "TERMS_ACCEPTANCE_REQUIRED": "REVIEW_TERMS",
		"INSTALL_NOT_VERIFIED": "PLAY_NOT_VERIFIED"}.get(str(res.get("error", "")), "RETRY_LATER")
	_retry_later(owner, value)
	return value

## lp_ref from the Play install referrer query (64 hex), else "".
static func click_from_referrer(referrer: String) -> String:
	if referrer.length() > 4096:
		return ""
	for pair in referrer.split("&", false):
		var kv := pair.split("=")
		if kv.size() == 2 and kv[0] == "lp_ref" and RegEx.create_from_string("^[a-f0-9]{64}$").search(kv[1].uri_decode()) != null:
			return kv[1].uri_decode()
	return ""

func read_referrer() -> Dictionary:
	if platform == null:
		return {"ok": false}
	platform.installReferrerRead()
	while true:
		var args: Array = await platform.playcore
		if args[0] == "referrer":
			var d = JSON.parse_string(str(args[1]))
			return d if d is Dictionary else {"ok": false}
	return {}

func integrity_token(project: String, request_hash: String) -> String:
	if platform == null or not project.is_valid_int() or request_hash.is_empty():
		return ""
	platform.integrityTokenRequest(project, request_hash)
	while true:
		var args: Array = await platform.playcore
		if args[0] == "integrity":
			var d = JSON.parse_string(str(args[1]))
			return str(d.get("token", "")) if d is Dictionary and d.get("ok", false) else ""
	return ""

# ---------------------------------------------------------------- popup policy (live)

## ReferralPopupPolicy.canShowAutomatically.
static func can_show_automatically(eligible: bool, state: String, impressions: int, shown_at: int, dismissed_at: int, now: int, shown_this_session: bool) -> bool:
	return eligible and state == "INVITE" and not shown_this_session and now > 0 and impressions >= 0 and impressions <= 2 \
		and shown_at >= 0 and shown_at <= now and dismissed_at >= 0 and dismissed_at <= now \
		and (shown_at == 0 or now - shown_at >= DAY) and (dismissed_at == 0 or now - dismissed_at >= 7 * DAY)

static func completed_match(match_id: String) -> void:
	if match_id.is_empty() or completed_matches.has(match_id):
		return
	completed_matches.append(match_id)
	while completed_matches.size() > 64:
		completed_matches.pop_front()
	pending_home = true

static func fullscreen_interaction() -> void:
	fullscreen_at = Time.get_ticks_msec()
	load("res://scripts/game/growth/prompt_gate.gd").quiet_for()

static func fullscreen_recently() -> bool:
	return fullscreen_at >= 0 and Time.get_ticks_msec() - fullscreen_at < 60000

## Home: the automatic invite popup decision (after the update gate). -> snapshot or {}.
func automatic_popup() -> Dictionary:
	if not adult() or not pending_home or automatic_shown or fullscreen_recently():
		return {}
	var s := await status()
	var owner := _owner()
	if not s.get("enabled", false) or s.get("campaign") != CAMPAIGN or s.get("localAccountId") != owner or owner.is_empty():
		return {}
	var p = NativePrefs.open("referral_popup_v1")
	var key := "%s:%s:" % [CAMPAIGN, owner]
	var now := int(Time.get_unix_time_from_system() * 1000.0)
	if not can_show_automatically(true, str(s.get("state", "")), p.get_int(key + "count", 0), p.get_long(key + "shown", 0), p.get_long(key + "dismissed", 0), now, automatic_shown):
		return {}
	if int(load("res://scripts/game/profile/profile_store.gd").load_state()["totalGames"]) < 2:
		return {}
	var Gate = load("res://scripts/game/growth/prompt_gate.gd")
	if not Gate.reserve("referral"):
		return {}
	Gate.presented("referral")
	pending_home = false
	automatic_shown = true
	p.edit().put_int(key + "count", p.get_int(key + "count", 0) + 1).put_long(key + "shown", now).commit()
	return s

func popup_dismissed(deferred: bool) -> void:
	if deferred:
		NativePrefs.open("referral_popup_v1").edit().put_long("%s:%s:dismissed" % [CAMPAIGN, _owner()], int(Time.get_unix_time_from_system() * 1000.0)).commit()
	load("res://scripts/game/growth/prompt_gate.gd").release("referral")
