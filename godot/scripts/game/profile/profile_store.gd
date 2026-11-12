extends RefCounted
## Device-local player profile (port of live ProfileStore + ProfileProgression +
## CharacterMasteryPolicy): completed-match statistics, XP / level, day and win streaks,
## per-Paw mastery and the 9 cosmetic badges. Each match id is counted once. Cosmetic only:
## no currency, rank or gameplay effect. Stored per account in the live `ludoproof_profile`
## file (live JSON shape), so a player's stats survive the update.

const Characters = preload("res://scripts/game/characters/catalog.gd")
const NativePrefs = preload("res://scripts/game/platform/native_prefs.gd")
## Live ProfileStore storage: the live preference file and keys, one JSON per account.
const PREFS := "ludoproof_profile"
const KEY_LEGACY := "profile_json_v1"
const KEY_PREFIX := "profile_json_v2_account_"
const KEY_MIGRATED := "profile_legacy_scope_migrated"
const KEY_QUARANTINE := "profile_json_legacy_quarantine"
const KEY_DELETED := "deleted_profile_accounts"
const MAX_RECORDED := 250
const MAX_NAME_LENGTH := 24
const MODES := ["CLASSIC", "RUSH", "SNAKE_LADDER"]
## Callable() -> String: the signed-in account id ("" = device profile before any account).
static var account_id: Callable = _session_account

static func _session_account() -> String:
	var tree := Engine.get_main_loop() as SceneTree
	var account = tree.root.get_node_or_null("Account") if tree != null else null
	return str(account.session().get("accountId", "")) if account != null else ""

static func _prefs():
	return NativePrefs.open(PREFS)

static func _storage_key() -> String:
	var id := str(account_id.call())
	return KEY_LEGACY if id.is_empty() else KEY_PREFIX + id

## Live ensureCurrentAccountScope: the pre-account profile becomes the first account's one.
static func ensure_current_account_scope() -> bool:
	var id := str(account_id.call())
	if id.is_empty():
		return false
	var p = _prefs()
	var e = p.edit()
	if not p.contains(KEY_PREFIX + id) and not p.get_bool(KEY_MIGRATED, false):
		var legacy = p.get_string(KEY_LEGACY, null)
		if legacy != null:
			e.put_string(KEY_PREFIX + id, legacy)
	return e.put_bool(KEY_MIGRATED, true).commit()

## Live prepareForAccountRestore: pin the legacy profile to the current account before the
## session is replaced, so a restored account never inherits another profile's stats.
static func prepare_for_account_restore() -> bool:
	var p = _prefs()
	var id := str(account_id.call())
	var legacy = p.get_string(KEY_LEGACY, null)
	var e = p.edit()
	if not p.get_bool(KEY_MIGRATED, false) and legacy != null:
		if not id.is_empty():
			if not p.contains(KEY_PREFIX + id):
				e.put_string(KEY_PREFIX + id, legacy)
		elif not p.contains(KEY_QUARANTINE):
			e.put_string(KEY_QUARANTINE, legacy)
	return e.put_bool(KEY_MIGRATED, true).remove(KEY_LEGACY).commit()

## Live clearForAccountDeletion.
static func clear_for_account_deletion(id: String) -> bool:
	var p = _prefs()
	var deleted: Array = p.get_string_set(KEY_DELETED, [])
	if not deleted.has(id):
		deleted.append(id)
	return p.edit().put_string_set(KEY_DELETED, deleted).remove(KEY_PREFIX + id).remove(KEY_LEGACY).remove(KEY_QUARANTINE).commit()

static func _blank() -> Dictionary:
	return {"displayName": "Guest User", "totalGames": 0, "totalWins": 0, "currentDayStreak": 0, "bestDayStreak": 0, "currentWinStreak": 0,
		"bestWinStreak": 0, "totalXp": 0, "onlineGames": 0, "localGames": 0, "lastPlayedDay": -1,
		"recordedMatchIds": [], "characterMatchIds": [], "characterMatches": {}, "modeStats": {}, "purchases": []}

static func load_state() -> Dictionary:
	var out := _blank()
	var p = _prefs()
	var key := _storage_key()
	var raw = p.get_string(key, null)
	if raw == null and key != KEY_LEGACY and not p.get_bool(KEY_MIGRATED, false):
		ensure_current_account_scope()
		raw = p.get_string(key, null)
	var json = JSON.parse_string(raw) if raw != null else null
	if not (json is Dictionary):
		return out
	out["displayName"] = str(json.get("displayName", "Guest User"))
	for k in ["totalGames", "totalWins", "currentDayStreak", "bestDayStreak", "currentWinStreak", "bestWinStreak", "totalXp", "onlineGames", "localGames"]:
		out[k] = int(json.get(k, 0))
	out["lastPlayedDay"] = int(json["lastPlayedDay"]) if json.get("lastPlayedDay") != null else -1
	var counts = json.get("characterMatches", {})
	if counts is Dictionary:
		for id in counts:
			if Characters.CHARACTERS.has(str(id)):
				out["characterMatches"][str(id)] = clampi(int(counts[id]), 0, 1000000)
	for k in ["characterMatchIds", "recordedMatchIds", "purchases"]:
		if json.get(k) is Array:
			out[k] = json[k].map(func(v): return str(v))
	var modes = json.get("modes", {})
	if modes is Dictionary:
		for m in MODES:
			if modes.get(m) is Dictionary:
				out["modeStats"][m] = {"games": int(modes[m].get("games", 0)), "wins": int(modes[m].get("wins", 0))}
	return out

## Saves in the live JSON shape (ProfileStore.save).
static func _save(state: Dictionary) -> void:
	var modes := {}
	for m in MODES:
		var st: Dictionary = state["modeStats"].get(m, {"games": 0, "wins": 0})
		modes[m] = {"games": int(st["games"]), "wins": int(st["wins"])}
	var json := {"characterMatches": state["characterMatches"], "characterMatchIds": state["characterMatchIds"], "displayName": state["displayName"],
		"totalGames": state["totalGames"], "totalWins": state["totalWins"], "currentDayStreak": state["currentDayStreak"],
		"bestDayStreak": state["bestDayStreak"], "currentWinStreak": state["currentWinStreak"], "bestWinStreak": state["bestWinStreak"],
		"totalXp": state["totalXp"], "onlineGames": state["onlineGames"], "localGames": state["localGames"],
		"lastPlayedDay": state["lastPlayedDay"] if int(state["lastPlayedDay"]) >= 0 else null, "modes": modes,
		"purchases": state["purchases"], "recordedMatchIds": state["recordedMatchIds"]}
	var key := _storage_key()
	var e = _prefs().edit().put_string(key, JSON.stringify(json))
	if key != KEY_LEGACY:
		e.put_bool(KEY_MIGRATED, true)
	e.commit()

static func _deleted() -> bool:
	var id := str(account_id.call())
	return not id.is_empty() and _prefs().get_string_set(KEY_DELETED, []).has(id)

## Live ProfileStore.updateDisplayName: trimmed, single spaces, 24 chars, "Guest User" if blank.
static func update_display_name(value: String) -> String:
	var normalized := RegEx.create_from_string("\\s+").sub(value.strip_edges(), " ", true).left(MAX_NAME_LENGTH)
	if normalized.is_empty():
		normalized = "Guest User"
	var s := load_state()
	s["displayName"] = normalized
	_save(s)
	return normalized

## LocalDate.now().toEpochDay(): the player's local calendar day.
static func local_day() -> int:
	var bias := int(Time.get_time_zone_from_system().get("bias", 0))
	return floori((Time.get_unix_time_from_system() + bias * 60.0) / 86400.0)

## ProfileProgression.xpAward: 20 per completed match, +30 for a win.
static func xp_award(won: bool) -> int:
	return 20 + (30 if won else 0)

static func _xp_requirement(level: int) -> int:
	return 100 + (level - 1) * 25

## {level, into, next, fraction} (max level 100).
static func level_progress(total_xp: int) -> Dictionary:
	var remaining := maxi(total_xp, 0)
	var level := 1
	var need := _xp_requirement(level)
	while remaining >= need and level < 100:
		remaining -= need
		level += 1
		need = _xp_requirement(level)
	return {"level": level, "into": remaining, "next": need, "fraction": clampf(float(remaining) / need, 0.0, 1.0) if need > 0 else 1.0}

## source: "COMPUTER" | "LOCAL" | "ONLINE". Returns false when the match was already counted.
static func record_completed_match(match_id: String, source: String, won: bool, character_ids: Array, today := -1) -> bool:
	if match_id.strip_edges().is_empty() or _deleted():
		return false
	var s := load_state()
	if Array(s["recordedMatchIds"]).has(match_id):
		return false
	s["recordedMatchIds"].append(match_id)
	while s["recordedMatchIds"].size() > MAX_RECORDED:
		s["recordedMatchIds"].pop_front()
	s["totalGames"] += 1
	_award_characters(s, match_id, character_ids)
	if won:
		s["totalWins"] += 1
		s["currentWinStreak"] += 1
		s["bestWinStreak"] = maxi(s["bestWinStreak"], s["currentWinStreak"])
	else:
		s["currentWinStreak"] = 0
	if source == "ONLINE":
		s["onlineGames"] += 1
	elif source == "LOCAL":
		s["localGames"] += 1
	var mode: Dictionary = s["modeStats"].get_or_add("CLASSIC", {"games": 0, "wins": 0})
	mode["games"] = int(mode["games"]) + 1
	mode["wins"] = int(mode["wins"]) + (1 if won else 0)
	s["totalXp"] += xp_award(won)
	var day := today if today >= 0 else local_day()
	if int(s["lastPlayedDay"]) != day:
		s["currentDayStreak"] = s["currentDayStreak"] + 1 if int(s["lastPlayedDay"]) == day - 1 else 1
		s["bestDayStreak"] = maxi(s["bestDayStreak"], s["currentDayStreak"])
		s["lastPlayedDay"] = day
	_save(s)
	return true

## Live ProfileStore.recordCharacterMatch (online: Paw mastery even when unranked).
static func record_character_match(match_id: String, character_ids: Array) -> bool:
	if match_id.strip_edges().is_empty() or _deleted():
		return false
	var s := load_state()
	var before := Array(s["characterMatchIds"]).size()
	_award_characters(s, match_id, character_ids)
	if Array(s["characterMatchIds"]).size() == before:
		return false
	_save(s)
	return true

static func _award_characters(s: Dictionary, match_id: String, ids: Array) -> void:
	var canonical: Array = []
	for id in ids:
		if Characters.is_known(id):
			var c := Characters.canonical_id(id)
			if not canonical.has(c):
				canonical.append(c)
	if canonical.is_empty() or Array(s["characterMatchIds"]).has(match_id):
		return
	s["characterMatchIds"].append(match_id)
	while s["characterMatchIds"].size() > MAX_RECORDED:
		s["characterMatchIds"].pop_front()
	for id in canonical:
		s["characterMatches"][id] = mini(int(s["characterMatches"].get(id, 0)) + 1, 1000000)

## ProfileProgression.badges: cosmetic achievements from persisted statistics.
static func badges(s: Dictionary) -> Array:
	return [
		_badge("first-win", "First Win", "Win your first game", s["totalWins"], 1),
		_badge("strategist", "Strategist", "Win 5 games", s["totalWins"], 5),
		_badge("unstoppable", "Unstoppable", "Win 3 games in a row", s["bestWinStreak"], 3),
		_badge("friendship", "Friendship", "Complete 3 online games", s["onlineGames"], 3),
		_badge("social-gamer", "Social Gamer", "Complete 10 online games", s["onlineGames"], 10),
		_badge("ludo-novice", "Ludo Novice", "Complete a Ludo game", s["totalGames"], 1),
		_badge("couch-champion", "Couch Champion", "Complete 3 local Pass & Play games", s["localGames"], 3),
		_badge("returning-paws", "Returning Paws", "Play on 3 consecutive days", s["bestDayStreak"], 3),
		_badge("game-night", "Game Night", "Complete 20 games", s["totalGames"], 20),
	]

static func _badge(id: String, title: String, requirement: String, current: int, goal: int) -> Dictionary:
	var progress := clampi(current, 0, goal)
	return {"id": id, "title": title, "requirement": requirement, "unlocked": progress >= goal, "current": progress, "goal": goal}

## CharacterMasteryPolicy.
const MILESTONES := [1, 3, 5, 10, 20]
static func mastery_tier(matches: int) -> String:
	if matches >= 20: return "Legendary pal"
	if matches >= 10: return "Best buddy"
	if matches >= 5: return "Good friend"
	if matches >= 3: return "Playful pal"
	if matches >= 1: return "New friend"
	return "Ready to play"

static func next_goal(matches: int) -> Variant:
	for m in MILESTONES:
		if m > maxi(matches, 0):
			return m
	return null
