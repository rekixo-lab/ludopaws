extends RefCounted
## Saved offline match (live OfflineGameEngine checkpoint): one game per local mode in the
## live preference files `ludoproof_computer_game` (Robot) / `ludoproof_offline_game`
## (Pass & Play), key "state", in the live checkpoint JSON shape. A match saved by the
## Kotlin app resumes in V2 after the update.
##
## V2 dice are a plain local RNG (owner decision): V2 writes no fairness history / proof
## fields and restores a live save from its board state (tokens, turn, sixes, opening and
## comeback state, pending roll) without claiming any proof verification. Invalid or
## unsupported saves are cleared like live (Quick Match restores with M3).

const R = preload("res://scripts/game/rules/ludo_rulesets.gd")
const NativePrefs = preload("res://scripts/game/platform/native_prefs.gd")
const Characters = preload("res://scripts/game/characters/catalog.gd")

const FILES := {"COMPUTER": "ludoproof_computer_game", "PASS_AND_PLAY": "ludoproof_offline_game"}
const SETUP_PREFS := "ludo_paws_offline_character_setup_v1"

static func _prefs(mode: String):
	return NativePrefs.open(FILES[mode])

static func has_active(mode: String) -> bool:
	return not load_state(mode).is_empty()

static func clear(mode: String) -> void:
	_prefs(mode).edit().remove("state").commit()

## Live writeCheckpoint shape (no fairness history: V2 dice carry no proof).
static func save(mode: String, state: Dictionary, owner := "") -> bool:
	var players: Array = []
	var finished: Array = []
	for p in state.get("players", []):
		players.append({"playerId": p["playerId"], "displayName": p.get("displayName", ""), "color": p["color"],
			"tokens": p["tokens"].duplicate(), "characterId": p.get("characterId", "dog"), "playerType": p.get("playerType", "HUMAN")})
	for id in state.get("finishOrderPlayerIds", []):
		finished.append(id)
	var json := {"profileOwnerKnown": not owner.is_empty(), "profileOwnerAccountId": owner if not owner.is_empty() else null,
		"rulesetId": R.ruleset_id(str(state.get("rulesetKey", R.DEFAULT_CLASSIC))), "matchId": state.get("matchId", ""),
		"status": state.get("status", "ACTIVE"), "turnSeat": int(state.get("turnSeat", 0)), "eventIndex": int(state.get("eventIndex", 0)),
		"revision": int(state.get("revision", 1)), "winnerPlayerId": state.get("winnerPlayerId"), "finishedPlayerIds": finished,
		"players": players, "consecutiveSixes": state.get("consecutiveSixes", []),
		"openingRollConsumed": state.get("openingRollConsumed", players.map(func(_p): return true)),
		"comebackWaits": state.get("comebackWaits", players.map(func(_p): return -1)), "history": [], "lastAudit": null}
	var pending = state.get("pendingRoll")
	if pending is Dictionary and pending.get("status") == "RESOLVED":
		json["pending"] = {"outcome": int(pending["outcome"]), "eventIndex": int(state.get("eventIndex", 0)),
			"legal": Array(pending.get("legalTokenIndexes", [])).duplicate(), "randomOutcome": int(pending.get("randomOutcome", pending["outcome"])),
			"openingRollApplied": bool(pending.get("openingRollApplied", false)), "comebackBonusApplied": bool(pending.get("comebackBonusApplied", false))}
	return _prefs(mode).edit().put_string("state", JSON.stringify(json)).commit()

## The saved ACTIVE match as a V2 engine state, or {} (an invalid save is cleared, like live).
static func load_state(mode: String) -> Dictionary:
	var raw = _prefs(mode).get_string("state", null)
	if raw == null:
		return {}
	var json = JSON.parse_string(str(raw))
	var state := _to_v2(json, mode) if json is Dictionary else {}
	if state.is_empty():
		clear(mode)
		return {}
	return state if state["status"] == "ACTIVE" else {}

static func _key_for(ruleset_id: String) -> String:
	for key in R.IDS:
		if R.IDS[key] == ruleset_id and not R.is_team_key(key):
			return key
	return ""

static func _to_v2(json: Dictionary, mode: String) -> Dictionary:
	var key := _key_for(str(json.get("rulesetId", "")))
	var players_json = json.get("players")
	if key.is_empty() or not (players_json is Array) or players_json.size() < 2 or players_json.size() > 4:
		return {}
	var count: int = players_json.size()
	var characters := _active_characters(mode, count)
	var players: Array = []
	var colors_seen := {}
	var id_map := {}
	for i in count:
		var p = players_json[i]
		if not (p is Dictionary) or not R.COLORS.has(str(p.get("color", ""))) or colors_seen.has(p["color"]):
			return {}
		colors_seen[p["color"]] = true
		var tokens = p.get("tokens")
		if not (tokens is Array) or tokens.size() != 4:
			return {}
		var t: Array = []
		for v in tokens:
			var n := int(v)
			if n < R.YARD or n > R.HOME:
				return {}
			t.append(n)
		var live_id := str(p.get("playerId", ""))
		var bot: bool = str(p.get("playerType", "")) == "BOT" or (mode == "COMPUTER" and live_id.begins_with("cpu-player-"))
		var name := str(p.get("displayName", "Player %d" % (i + 1)))
		var cpu := RegEx.create_from_string("^CPU\\s+(\\d+)$").search(name.strip_edges())
		if bot and cpu != null:
			name = "Robot " + cpu.get_string(1)
		var character := str(p.get("characterId", characters[i] if i < characters.size() else "dog"))
		id_map[live_id] = "local-%d" % i
		players.append({"playerId": "local-%d" % i, "displayName": name, "color": p["color"], "tokens": t,
			"characterId": Characters.canonical_id(character) if Characters.is_known(character) else "dog",
			"playerType": "BOT" if bot else "HUMAN"})
	var seat := int(json.get("turnSeat", 0))
	var sixes = json.get("consecutiveSixes")
	if seat < 0 or seat >= count or not (sixes is Array) or sixes.size() != count:
		return {}
	var status := str(json.get("status", "ACTIVE"))
	var state := {"schemaVersion": 1, "status": status, "revision": int(json.get("revision", 1)),
		"matchMode": "ROBOT" if mode == "COMPUTER" else "PASS_AND_PLAY", "rulesetKey": key, "players": players,
		"turnSeat": seat, "consecutiveSixes": sixes.map(func(v): return int(v)), "pendingRoll": null,
		"winnerPlayerId": id_map.get(json.get("winnerPlayerId"), null), "turnTimeoutCounts": {}, "turnRotation": "PERIMETER",
		"matchId": str(json.get("matchId", "")) if not str(json.get("matchId", "")).is_empty() else "offline-legacy-" + load("res://scripts/game/online/game_api.gd").new_request_id(),
		"eventIndex": int(json.get("eventIndex", 0))}
	if R.finishes_all_places(key):
		state["finishOrderPlayerIds"] = Array(json.get("finishedPlayerIds", [])).map(func(id): return id_map.get(id, "")).filter(func(id): return not id.is_empty())
	if R.has_opening_six(key):
		var opening = json.get("openingRollConsumed")
		# Legacy saves without the field: every seat already consumed (live rule).
		state["openingRollConsumed"] = opening.map(func(v): return bool(v)) if opening is Array and opening.size() == count else players.map(func(_p): return true)
	if R.has_comeback(key):
		var waits = json.get("comebackWaits")
		if waits != null and (not (waits is Array) or waits.size() != count):
			return {}
		state["comebackWaits"] = waits.map(func(v): return int(v)) if waits is Array else players.map(func(_p): return -1)
	var pending = json.get("pending")
	if pending is Dictionary and pending.has("outcome") and pending.get("legal") is Array and not pending["legal"].is_empty():
		var legal: Array = pending["legal"].map(func(v): return int(v))
		if legal.all(func(i): return i >= 0 and i < 4):
			state["pendingRoll"] = {"status": "RESOLVED", "seat": seat, "scheduledSeat": seat, "outcome": int(pending["outcome"]),
				"randomOutcome": int(pending.get("randomOutcome", pending["outcome"])), "legalTokenIndexes": legal,
				"openingRollApplied": bool(pending.get("openingRollApplied", false)), "comebackBonusApplied": bool(pending.get("comebackBonusApplied", false))}
	return state

## Live ActiveOfflineCharacterSetup: the Paws chosen when this match started.
static func _active_characters(mode: String, count: int) -> Array:
	var p = NativePrefs.open(SETUP_PREFS)
	if p.get_string("active_mode", "") != mode or p.get_int("active_player_count", 0) != count:
		return []
	return str(p.get_string("active_character_ids", "")).split(",", false)

## Live OfflineCharacterSetupStore.saveActive.
static func save_active_setup(mode: String, count: int, color: String, characters: Array, humans: int) -> void:
	NativePrefs.open(SETUP_PREFS).edit().put_int("schema_version", 1).put_string("active_mode", mode) \
		.put_int("active_player_count", count).put_int("active_human_count", humans).put_string("active_color", color) \
		.put_string("active_character_ids", ",".join(characters)).commit()
