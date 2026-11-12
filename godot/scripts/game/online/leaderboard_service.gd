extends RefCounted
## Live LeaderboardActivity data flow: the device's leaderboard identity
## (LeaderboardIdentityStore; registered once with POST /api/leaderboard/profile/register),
## GET /api/leaderboard?limit=50 with the profile token, and LeaderboardCacheStore
## (`ludoproof_leaderboard_cache`, per signed-in account, max 96 000 chars).
## Every call returns (await) {ok, snapshot?, cached?, savedAt?, message?}.

const Identity = preload("res://scripts/game/online/identity_store.gd")
const GameApi = preload("res://scripts/game/online/game_api.gd")
const NativePrefs = preload("res://scripts/game/platform/native_prefs.gd")
const CACHE := "ludoproof_leaderboard_cache"
const MAX_CACHE_CHARS := 96000

var account   # Account autoload
var identity = Identity.leaderboard()

func _init(account_node) -> void:
	account = account_node

## LeaderboardSnapshot.fromJson.
static func parse(payload: Dictionary) -> Dictionary:
	var entry := func(item: Dictionary, viewer = null) -> Dictionary:
		var name := str(item.get("displayName", "Player")).strip_edges()
		return {"rank": maxi(int(item.get("rank", 0)), 0), "displayName": name if not name.is_empty() else "Player",
			"games": maxi(int(item.get("games", 0)), 0), "wins": maxi(int(item.get("wins", 0)), 0),
			"points": maxi(int(item.get("points", 0)), 0), "isViewer": viewer if viewer != null else bool(item.get("isViewer", false))}
	var entries: Array = []
	for item in payload.get("entries", []):
		if item is Dictionary:
			entries.append(entry.call(item))
	var scoring = payload.get("scoring") if payload.get("scoring") is Dictionary else {}
	return {"entries": entries, "viewer": entry.call(payload["viewer"], true) if payload.get("viewer") is Dictionary else null,
		"finishPoints": int(scoring.get("finish", 20)), "winBonus": int(scoring.get("winBonus", 30))}

func _request(method: String, path: String, body = null, token = null) -> Dictionary:
	return await account.service._request(method, path, body, token)

## LeaderboardActivity.ensureCredential: stored credential, else register this device once.
func ensure_credential() -> Dictionary:
	var c: Dictionary = identity.load_credential()
	if not c.is_empty():
		return {"ok": true, "credential": c}
	var request_id: String = identity.registration_request_id()
	var res := await _request("POST", "/api/leaderboard/profile/register", {"clientRequestId": request_id})
	if not res["ok"]:
		return res
	var cred := {"id": str(res["data"].get("profileId", "")), "token": str(res["data"].get("profileToken", "")), "requestId": request_id}
	if not identity.save(cred):
		return {"ok": false, "error": "INVALID_RESPONSE", "message": "Leaderboard registration could not be saved."}
	return {"ok": true, "credential": cred}

func cached() -> Dictionary:
	var owner: String = account.session().get("accountId", "")
	var p = NativePrefs.open(CACHE)
	if owner.is_empty() or p.get_string("viewer_account_id", "") != owner:
		return {}
	var payload = JSON.parse_string(str(p.get_string("payload_v1", "{}")))
	return {"snapshot": parse(payload), "savedAt": p.get_long("saved_at_v1", 0)} if payload is Dictionary and not payload.is_empty() else {}

func clear_cache() -> void:
	var p = NativePrefs.open(CACHE)
	var e = p.edit()
	for k in p.keys():
		e.remove(k)
	e.commit()

func refresh() -> Dictionary:
	var owner: String = account.session().get("accountId", "")
	if owner.is_empty():
		return {"ok": false, "message": "Sign in to view the leaderboard."}
	var c := await ensure_credential()
	if not c["ok"]:
		return _failure()
	var res := await _request("GET", "/api/leaderboard?limit=50", null, c["credential"]["token"])
	if not res["ok"]:
		return _failure()
	if account.session().get("accountId", "") != owner:
		return {"ok": false, "message": ""}
	var raw := JSON.stringify(res["data"])
	if raw.length() <= MAX_CACHE_CHARS:
		NativePrefs.open(CACHE).edit().put_string("viewer_account_id", owner).put_string("payload_v1", raw) \
			.put_long("saved_at_v1", int(Time.get_unix_time_from_system() * 1000.0)).commit()
	return {"ok": true, "snapshot": parse(res["data"])}

func _failure() -> Dictionary:
	return {"ok": false, "message": "Could not refresh. Showing the last saved ranking." if not cached().is_empty()
		else "Leaderboard is unavailable right now. Check your connection and try again."}
