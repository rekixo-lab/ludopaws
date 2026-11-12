extends RefCounted
## Port of live AccountRepository (session part): guest account, session refresh, profile,
## wallet / catalog reads, Google challenge / login / link, logout. The session lives in the
## live AccountSessionStore (Android Keystore + AES/GCM) through the LudoPawsPlatform plugin,
## so an existing player's session from the Kotlin app keeps working after the update.
## Desktop development uses a plain local stand-in (never on Android).
##
## Every call returns (await) {ok: true, ...} or {ok: false, status, error, message}.

const GameApi = preload("res://scripts/game/online/game_api.gd")
const REFRESH_MARGIN_S := 45
const MAX_REQUEST_BYTES := 8 * 1024
const ACCOUNT_ID := "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$"
const BILLING_ID := "^[a-f0-9]{64}$"
const REFRESH_TOKEN := "^lpr_[A-Za-z0-9_-]{43}$"
const HEX64 := "^[a-f0-9]{64}$"

var base_url := ""
var transport
var store            # session store: plugin singleton or DevSessionStore
var version_name := "2.0.0"
var _refreshing := false
## Seconds clock (tests inject a fixed clock).
var now_s: Callable = func() -> int: return int(Time.get_unix_time_from_system())

func _init(url: String, http_transport, session_store = null, app_version := "2.0.0") -> void:
	base_url = url.rstrip("/")
	transport = http_transport
	version_name = app_version
	store = session_store if session_store != null else default_store()

static func default_store():
	if Engine.has_singleton("LudoPawsPlatform"):
		return Engine.get_singleton("LudoPawsPlatform")
	return DevSessionStore.new()

static func valid_shape(s: Dictionary) -> bool:
	var rx := func(p: String, v) -> bool:
		var r := RegEx.create_from_string(p)
		return v is String and r.search(v) != null
	return rx.call(ACCOUNT_ID, s.get("accountId")) and rx.call(ACCOUNT_ID, s.get("sessionId")) \
		and rx.call(BILLING_ID, s.get("billingAccountId")) and str(s.get("accessToken", "")).split(".").size() == 3 \
		and rx.call(REFRESH_TOKEN, s.get("refreshToken")) and int(s.get("accessExpiresAt", 0)) > 0 and int(s.get("sessionExpiresAt", 0)) > 0

## The decrypted session is cached; every write goes through _save_raw / _clear_raw.
var _cached = null

func _save_raw(json: String) -> bool:
	_cached = null
	return bool(store.sessionSave(json))

func _clear_raw() -> bool:
	_cached = null
	return bool(store.sessionClear())

func current_session() -> Dictionary:
	if _cached == null:
		_cached = str(store.sessionLoad())
	var text: String = _cached
	if text.is_empty():
		return {}
	var s = JSON.parse_string(text)
	return s if s is Dictionary else {}

func signed_in() -> bool:
	return not current_session().is_empty()

## AccountRepository.ensureGuestSession.
func ensure_guest_session() -> Dictionary:
	var current := current_session()
	if not current.is_empty():
		var res := await valid_session(current)
		if res["ok"]:
			return res
		var recoverable: bool = res.get("status") == 401 and res.get("error") == "REFRESH_TOKEN_INVALID" and current.get("guestRecoveryEligible", false)
		if not recoverable:
			return res
		_clear_raw()
		return await _create_guest()
	if store.lastAccountMode() == "GOOGLE":
		return _err(401, "ACCOUNT_SESSION_REQUIRED", "Your saved account session is unavailable. Restore the Google-linked account instead of creating a new guest account.")
	return await _create_guest()

func _create_guest() -> Dictionary:
	var request_id: String = store.guestRequestId()
	if request_id.is_empty():
		return _err(0, "SECURE_STORAGE_UNAVAILABLE", "Secure storage is unavailable on this device.")
	var res := await _request("POST", "/api/account/guest", {"clientRequestId": request_id, "displayName": "Guest User"})
	if not res["ok"]:
		return res
	return _save_session(res["data"], true)

## AccountRepository.requireSession.
func require_session() -> Dictionary:
	var current := current_session()
	if current.is_empty():
		return _err(401, "ACCOUNT_SESSION_REQUIRED", "Sign in or continue as a guest to use the store.")
	return await valid_session(current)

## AccountRepository.validSession: refresh 45 s before expiry with a persisted request id.
func valid_session(current: Dictionary) -> Dictionary:
	while _refreshing:
		await Engine.get_main_loop().process_frame
	var latest := current_session()
	if latest.is_empty():
		return _err(401, "ACCOUNT_SESSION_REQUIRED", "Reconnect to your game account.")
	if latest.get("accountId") != current.get("accountId") or latest.get("sessionId") != current.get("sessionId"):
		return _err(409, "ACCOUNT_SESSION_CHANGED", "The active account changed. Please try again.")
	if int(latest.get("accessExpiresAt", 0)) > now_s.call() + REFRESH_MARGIN_S:
		return {"ok": true, "session": latest}
	_refreshing = true
	var request_id: String = store.pendingRefreshRequestId()
	var res := await _request("POST", "/api/account/refresh", {"accountId": latest["accountId"], "sessionId": latest["sessionId"],
		"refreshToken": latest["refreshToken"], "clientRequestId": request_id})
	_refreshing = false
	if not res["ok"]:
		return res
	return _save_session(res["data"], latest.get("guestRecoveryEligible", false), latest.get("identityRecoveryPending", false),
		latest.get("guestDeletionCredential", ""))

func _save_session(r: Dictionary, guest: bool, identity_pending := false, deletion := "") -> Dictionary:
	var profile: Dictionary = r.get("profile", {}) if r.get("profile") is Dictionary else {}
	var s := {"accountId": str(r.get("accountId", "")), "sessionId": str(r.get("sessionId", "")),
		"accessToken": str(r.get("accessToken", "")), "refreshToken": str(r.get("refreshToken", "")),
		"billingAccountId": str(r.get("billingAccountId", "")), "accessExpiresAt": int(r.get("accessExpiresAt", 0)),
		"sessionExpiresAt": int(r.get("sessionExpiresAt", 0)), "displayName": str(profile.get("displayName", "Guest User")).left(24),
		"guestRecoveryEligible": guest, "identityRecoveryPending": identity_pending}
	if guest and not deletion.is_empty():
		s["guestDeletionCredential"] = deletion
	if not valid_shape(s):
		return _err(502, "INVALID_SESSION", "The account service returned an invalid session.")
	if not _save_raw(JSON.stringify(s)):
		return _err(0, "SECURE_STORAGE_UNAVAILABLE", "The account session could not be saved securely.")
	return {"ok": true, "session": s}

# ---------------------------------------------------------------- authenticated reads / actions

func _authed(method: String, path: String, body = null) -> Dictionary:
	var v := await require_session()
	if not v["ok"]:
		return v
	return await _request(method, path, body, v["session"]["accessToken"])

func profile() -> Dictionary: return await _authed("GET", "/api/account/me")
func wallet(ad_receipt_ids := []) -> Dictionary: return await _authed("GET", "/api/wallet" + _receipts(ad_receipt_ids))
func store_catalog() -> Dictionary: return await _authed("GET", "/api/store/catalog")
func store_status() -> Dictionary: return await _request("GET", "/api/store/status")
func linked_identities() -> Dictionary: return await _authed("GET", "/api/account/links")
func update_display_name(name: String) -> Dictionary:
	var v := await require_session()
	if not v["ok"]:
		return v
	var res := await _request("POST", "/api/account/profile", {"displayName": GameApi.public_name(name)}, v["session"]["accessToken"])
	if res["ok"] and res["data"].get("profile") is Dictionary:
		var latest := current_session()
		var shown := str(res["data"]["profile"].get("displayName", "")).strip_edges().left(24)
		if latest.get("accountId") == v["session"]["accountId"] and latest.get("sessionId") == v["session"]["sessionId"] \
				and not shown.is_empty() and latest.get("displayName") != shown:
			latest["displayName"] = shown
			_save_raw(JSON.stringify(latest))
	return res

func _receipts(ids: Array) -> String:
	if ids.is_empty():
		return ""
	return "?adReceiptIds=" + ",".join(ids.map(func(i): return str(i).uri_encode()))

## Google: server nonce, then the Credential Manager ID token (plugin) to log in or link.
func google_challenge() -> Dictionary:
	var res := await _request("POST", "/api/account/google/challenge", {})
	if res["ok"] and RegEx.create_from_string(HEX64).search(str(res["data"].get("nonce", ""))) == null:
		return _err(502, "INVALID_RESPONSE", "Server returned an invalid Google sign-in challenge.")
	return res

func restore_google_account(id_token: String, nonce: String) -> Dictionary:
	var res := await _request("POST", "/api/account/google/login", {"idToken": id_token, "nonce": nonce})
	if not res["ok"]:
		return res
	return _save_session(res["data"], false, true)

## Live linkGoogleIdentity: the server-confirmed link replaces guest-only recovery: the
## session keeps its tokens, takes the linked profile name and is saved in GOOGLE mode.
func link_google_identity(id_token: String, nonce: String) -> Dictionary:
	var v := await require_session()
	if not v["ok"]:
		return v
	var session: Dictionary = v["session"]
	var res := await _request("POST", "/api/account/google/link", {"idToken": id_token, "nonce": nonce}, session["accessToken"])
	if not res["ok"]:
		return res
	var latest := current_session()
	if latest.get("accountId") == session.get("accountId"):
		var linked = res["data"].get("profile")
		var name := str(linked.get("displayName", "")).strip_edges().left(24) if linked is Dictionary else ""
		latest["displayName"] = name if not name.is_empty() else latest.get("displayName", "Guest User")
		latest["guestRecoveryEligible"] = false
		latest.erase("guestDeletionCredential")
		_save_raw(JSON.stringify(latest))
	return res

func recover_linked_identities() -> Dictionary: return await _authed("GET", "/api/account/identities/recover")
func link_friend_identity(friend_token: String) -> Dictionary: return await _authed("POST", "/api/account/link/friend", {"friendToken": friend_token})
func link_leaderboard_identity(profile_token: String) -> Dictionary: return await _authed("POST", "/api/account/link/leaderboard", {"profileToken": profile_token})

func requires_identity_recovery() -> bool:
	return bool(store.hasPendingIdentityRecovery())

func complete_identity_recovery() -> Dictionary:
	var current := current_session()
	if current.is_empty():
		return _err(401, "ACCOUNT_SESSION_REQUIRED", "Restore your saved account to recover linked identities.")
	_cached = null
	if not store.sessionCompleteIdentityRecovery(str(current["accountId"])):
		return _err(0, "SECURE_STORAGE_UNAVAILABLE", "The account session could not be saved securely.")
	return {"ok": true}

## Live ensureGuestDeletionCredential: a delete-only code for guest accounts (never a sign-in).
func guest_deletion_code(rotate_existing := false) -> Dictionary:
	var v := await require_session()
	if not v["ok"]:
		return v
	var session: Dictionary = v["session"]
	if not session.get("guestRecoveryEligible", false):
		return _err(409, "GUEST_DELETION_CODE_NOT_AVAILABLE", "A delete-only code is available only while this account is a guest. Use Google sign-in for a linked account.")
	var existing := str(session.get("guestDeletionCredential", ""))
	if not existing.is_empty() and not rotate_existing:
		return {"ok": true, "code": existing}
	var res := await _request("POST", "/api/account/guest/deletion-code", {"rotate": rotate_existing}, session["accessToken"])
	if not res["ok"]:
		return res
	var code := str(res["data"].get("credential", ""))
	if RegEx.create_from_string(HEX64).search(code) == null:
		return _err(503, "GUEST_DELETION_CODE_RESPONSE_INVALID", "The account service did not return a valid guest deletion code.")
	var latest := current_session()
	if latest.get("accountId") != session.get("accountId") or not latest.get("guestRecoveryEligible", false):
		return _err(409, "GUEST_DELETION_CODE_NOT_AVAILABLE", "This account is no longer an eligible guest account.")
	latest["guestDeletionCredential"] = code
	if not _save_raw(JSON.stringify(latest)):
		return _err(0, "SECURE_STORAGE_UNAVAILABLE", "The account session could not be saved securely.")
	return {"ok": true, "code": code}

## Live deleteCurrentAccount (server part + session). Local per-account caches are cleared
## by the caller (profile, wallet cache, pending purchases ...), as in live ProfileActivity.
func delete_current_account() -> Dictionary:
	var current := current_session()
	if current.is_empty():
		return _err(401, "ACCOUNT_SESSION_REQUIRED", "Sign in again before deleting your account.")
	var v := await valid_session(current)
	if not v["ok"]:
		return v
	var res := await _request("POST", "/api/account/delete", {"confirmation": "DELETE_MY_ACCOUNT"}, v["session"]["accessToken"])
	if not res["ok"]:
		return res
	if not res["data"].get("deleted", false):
		return _err(502, "ACCOUNT_DELETE_INCOMPLETE", "The account service did not confirm deletion.")
	_clear_raw()
	res["accountId"] = v["session"]["accountId"]
	res["billingAccountId"] = v["session"]["billingAccountId"]
	return res

## Live logout: server logout with a valid session, then the local session is cleared.
func logout() -> Dictionary:
	var v := await require_session()
	if not v["ok"]:
		return v
	var res := await _request("POST", "/api/account/logout", {}, v["session"]["accessToken"])
	if not res["ok"]:
		return res
	_clear_raw()
	return res

# ---------------------------------------------------------------- transport

func _request(method: String, path: String, body = null, token = null) -> Dictionary:
	assert(base_url.begins_with("https://"), "LudoProof API must use HTTPS")
	var headers := PackedStringArray(["Accept: application/json", "User-Agent: LudoProof-Android/" + version_name])
	if token != null:
		headers.append("Authorization: Bearer " + str(token))
	var text := ""
	if body != null:
		text = JSON.stringify(body)
		if text.to_utf8_buffer().size() > MAX_REQUEST_BYTES:
			return _err(0, "REQUEST_TOO_LARGE", "Account request exceeded the safety limit.")
		headers.append("Content-Type: application/json")
	var res: Dictionary = await transport.send(method, base_url + path, headers, text)
	var parsed := GameApi.parse_response(res)
	if not parsed["ok"]:
		parsed["status"] = int(res.get("status", 0))
	return parsed

static func _err(status: int, code: String, message: String) -> Dictionary:
	return {"ok": false, "status": status, "error": code, "message": message}

## Desktop-only stand-in with the plugin's interface (plain file, development only).
class DevSessionStore:
	var path := "user://dev_account_session.cfg"
	func _cfg() -> ConfigFile:
		var c := ConfigFile.new()
		c.load(path)
		return c
	func sessionLoad() -> String: return str(_cfg().get_value("s", "session", ""))
	func sessionSave(json: String) -> bool:
		var c := _cfg()
		var s = JSON.parse_string(json)
		c.set_value("s", "session", json)
		c.set_value("s", "mode", "GUEST" if s is Dictionary and s.get("guestRecoveryEligible", false) else "GOOGLE")
		c.set_value("s", "refresh_id", "")
		return c.save(path) == OK
	func sessionClear() -> bool:
		var c := _cfg()
		c.set_value("s", "session", "")
		c.set_value("s", "refresh_id", "")
		return c.save(path) == OK
	func lastAccountMode() -> String: return str(_cfg().get_value("s", "mode", ""))
	func guestRequestId() -> String:
		var c := _cfg()
		var id := str(c.get_value("s", "guest_id", ""))
		if id.is_empty():
			id = GameApi.new_request_id()
			c.set_value("s", "guest_id", id)
			c.save(path)
		return id
	func pendingRefreshRequestId() -> String:
		var c := _cfg()
		var id := str(c.get_value("s", "refresh_id", ""))
		if id.is_empty():
			id = GameApi.new_request_id()
			c.set_value("s", "refresh_id", id)
			c.save(path)
		return id
	func hasPendingIdentityRecovery() -> bool:
		var s = JSON.parse_string(sessionLoad()) if not sessionLoad().is_empty() else null
		return s is Dictionary and s.get("identityRecoveryPending", false)
	func sessionCompleteIdentityRecovery(account_id: String) -> bool:
		var s = JSON.parse_string(sessionLoad()) if not sessionLoad().is_empty() else null
		if s is Dictionary and s.get("accountId") == account_id:
			s["identityRecoveryPending"] = false
			return sessionSave(JSON.stringify(s))
		return true
