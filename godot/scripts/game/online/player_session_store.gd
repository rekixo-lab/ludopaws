extends RefCounted
## Live SecureSessionStore: the online match seat (matchId, playerId, player token, mode) in
## `ludoproof_secure_session`, the token encrypted with the live Keystore alias
## `ludoproof_player_session_v1` and AAD "ludoproof:player-session:v2:{matchId}:{playerId}";
## pending remote forfeits in `ludoproof_pending_forfeits` (AAD
## "ludoproof:pending-forfeit:v1:{matchId}:{playerId}"). A player's open online match
## therefore survives the update.

const NativePrefs = preload("res://scripts/game/platform/native_prefs.gd")
const Keystore = preload("res://scripts/game/platform/keystore.gd")
const PREFS := "ludoproof_secure_session"
const FORFEITS := "ludoproof_pending_forfeits"
const ALIAS := "ludoproof_player_session_v1"
const FORMAT_VERSION := 2
const MATCH_ID := "^LP[A-Z2-9]{8}$"
const PLAYER_TOKEN := "^lp_[A-Za-z0-9_-]{32,}$"
const REMOTE_MODES := ["ONLINE", "TEAM_UP", "FRIENDS"]

static func _ok(pattern: String, value: String) -> bool:
	return RegEx.create_from_string(pattern).search(value) != null

static func session_aad(match_id: String, player_id: String) -> String:
	return "ludoproof:player-session:v2:%s:%s" % [match_id, player_id]

static func forfeit_aad(match_id: String, player_id: String) -> String:
	return "ludoproof:pending-forfeit:v1:%s:%s" % [match_id, player_id]

## {matchId, playerId, playerToken, mode} -> bool.
static func save(s: Dictionary) -> bool:
	var match_id := str(s.get("matchId", ""))
	var player_id := str(s.get("playerId", ""))
	var token := str(s.get("playerToken", ""))
	var mode := str(s.get("mode", "ONLINE"))
	if not _ok(MATCH_ID, match_id) or player_id.strip_edges().is_empty() or not _ok(PLAYER_TOKEN, token) or not REMOTE_MODES.has(mode):
		return false
	var sealed := Keystore.encrypt(ALIAS, session_aad(match_id, player_id), token)
	if sealed.is_empty():
		return false
	return NativePrefs.open(PREFS).edit().put_int("formatVersion", FORMAT_VERSION).put_string("matchId", match_id) \
		.put_string("playerId", player_id).put_string("gameMode", mode).put_string("iv", sealed["iv"]) \
		.put_string("playerTokenCiphertext", sealed["ct"]).commit()

## The saved seat or {} (an unreadable record is cleared, like live).
static func load_session() -> Dictionary:
	var p = NativePrefs.open(PREFS)
	var match_id = p.get_string("matchId", null)
	var player_id = p.get_string("playerId", null)
	var iv = p.get_string("iv", null)
	var ct = p.get_string("playerTokenCiphertext", null)
	if match_id == null or player_id == null or iv == null or ct == null:
		return {}
	if not _ok(MATCH_ID, match_id) or str(player_id).strip_edges().is_empty():
		clear()
		return {}
	var mode := str(p.get_string("gameMode", "ONLINE"))
	if not REMOTE_MODES.has(mode):
		mode = "ONLINE"
	var aad := session_aad(match_id, player_id) if p.get_int("formatVersion", 1) >= FORMAT_VERSION else ""
	var token = Keystore.decrypt(ALIAS, aad, iv, ct)
	if token == null or not _ok(PLAYER_TOKEN, token):
		clear()
		return {}
	var s := {"matchId": match_id, "playerId": player_id, "playerToken": token, "mode": mode}
	if p.get_int("formatVersion", 1) < FORMAT_VERSION:
		save(s)
	return s

static func clear() -> void:
	var p = NativePrefs.open(PREFS)
	var e = p.edit()
	for k in p.keys():
		e.remove(k)
	e.commit()

static func save_pending_forfeit(f: Dictionary) -> bool:
	var match_id := str(f.get("matchId", ""))
	var player_id := str(f.get("playerId", ""))
	var token := str(f.get("playerToken", ""))
	if not _ok(MATCH_ID, match_id) or player_id.is_empty() or not _ok(PLAYER_TOKEN, token):
		return false
	var sealed := Keystore.encrypt(ALIAS, forfeit_aad(match_id, player_id), JSON.stringify({"token": token, "owner": f.get("ownerAccountId")}))
	if sealed.is_empty():
		return false
	return NativePrefs.open(FORFEITS).edit().put_string(match_id, JSON.stringify({"playerId": player_id, "ranked": bool(f.get("ranked", false)),
		"iv": sealed["iv"], "playerTokenCiphertext": sealed["ct"]})).commit()

static func pending_forfeits() -> Array:
	var p = NativePrefs.open(FORFEITS)
	var out: Array = []
	var keys: Array = p.keys()
	keys.sort()
	for match_id in keys:
		var entry = JSON.parse_string(str(p.get_string(match_id, "{}")))
		if not _ok(MATCH_ID, match_id) or not (entry is Dictionary):
			continue
		var player_id := str(entry.get("playerId", ""))
		var plain = Keystore.decrypt(ALIAS, forfeit_aad(match_id, player_id), str(entry.get("iv", "")), str(entry.get("playerTokenCiphertext", "")))
		if plain == null:
			continue
		var bound = JSON.parse_string(plain) if str(plain).begins_with("{") else null
		var token := str(bound.get("token", "")) if bound is Dictionary else str(plain)
		var owner = bound.get("owner") if bound is Dictionary else null
		if _ok(PLAYER_TOKEN, token):
			out.append({"matchId": match_id, "playerId": player_id, "playerToken": token, "ranked": bool(entry.get("ranked", false)),
				"ownerAccountId": owner if owner != null and str(owner) != "" and str(owner) != "null" else null})
	return out

static func clear_pending_forfeit(match_id: String) -> void:
	NativePrefs.open(FORFEITS).edit().remove(match_id).commit()
