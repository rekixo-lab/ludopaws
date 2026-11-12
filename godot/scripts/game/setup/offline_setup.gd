extends RefCounted
## Robot / Pass & Play setup (pure + persistence). Ports of OfflinePlayerLayout (colorsFor,
## oppositeColor, normalizeColorsForSeats, selectColorForSeat), RobotTeamComposition and
## OfflineCharacterSetupStore; Paws go through starter_assignment.gd
## (StarterPawsAssignmentPolicy). See docs/SETUP_PARITY.md. Presentation only: Paws and
## colours never touch dice, moves or winners.

const Starter = preload("res://scripts/game/characters/starter_assignment.gd")
const Characters = preload("res://scripts/game/characters/catalog.gd")

const COLORS := ["RED", "GREEN", "YELLOW", "BLUE"]
const DEFAULT_COLOR := "BLUE"
## V2-only extras (seat names); the live setup lives in PREFS below.
const PATH := "user://ludo_paws_offline_setup_names.cfg"
const SCHEMA := 1
const DEFAULT_NAMES := ["Aarav", "Diya", "Kabir", "Myra"]

static var path := PATH

# ---------------------------------------------------------------- OfflinePlayerLayout

static func opposite_color(color: String) -> String:
	return {"RED": "YELLOW", "YELLOW": "RED", "GREEN": "BLUE", "BLUE": "GREEN"}[color]

## Robot colours by seat from the human's colour: 2 players = opposite corner, 3-4 walk
## round the board from the local colour.
static func colors_for(player_count: int, preferred: String) -> Array:
	assert(player_count >= 2 and player_count <= 4 and COLORS.has(preferred))
	if player_count == 2:
		return [preferred, opposite_color(preferred)]
	var start := COLORS.find(preferred)
	var out: Array = []
	for offset in player_count:
		out.append(COLORS[(start + offset) % COLORS.size()])
	return out

## Every seat keeps a valid saved colour; only missing / duplicate / invalid seats get
## the first free default.
static func normalize_colors(player_count: int, requested: Array, preferred := DEFAULT_COLOR) -> Array:
	var safe: String = preferred if COLORS.has(preferred) else DEFAULT_COLOR
	var defaults := colors_for(player_count, safe)
	if requested.is_empty():
		return defaults
	var assigned: Array = []
	assigned.resize(player_count)
	var used: Array = []
	for seat in player_count:
		var want = requested[seat] if seat < requested.size() else null
		if want is String and COLORS.has(want) and not used.has(want):
			assigned[seat] = want
			used.append(want)
	for seat in player_count:
		if assigned[seat] == null:
			for c in defaults + COLORS:
				if not used.has(c):
					assigned[seat] = c
					used.append(c)
					break
	return assigned

## Pass & Play colour tap: a free colour changes only this seat; a taken colour swaps the
## two seats; nothing else moves; the current colour is a no-op.
static func select_color_for_seat(player_count: int, current: Array, seat: int, requested: String) -> Array:
	assert(seat >= 0 and seat < player_count and COLORS.has(requested))
	var colors := normalize_colors(player_count, current, current[0] if not current.is_empty() else DEFAULT_COLOR)
	var owner := colors.find(requested)
	if owner == seat:
		return colors
	var previous: String = colors[seat]
	colors[seat] = requested
	if owner >= 0:
		colors[owner] = previous
	return colors

# ---------------------------------------------------------------- RobotTeamComposition

static func humans(player_count: int, requested: int) -> int:
	return clampi(requested, 1, player_count - 1)

static func next_humans(player_count: int, current: int) -> int:
	var value := humans(player_count, current)
	return 1 if value == player_count - 1 else value + 1

## Re-tapping the selected size cycles the mix; a different size starts at one human.
static func humans_after_tap(player_count: int, previous_count: int, previous_humans: int) -> int:
	return next_humans(player_count, previous_humans) if player_count == previous_count else humans(player_count, 1)

static func mix_label(player_count: int, requested_humans: int) -> String:
	var h := humans(player_count, requested_humans)
	var r := player_count - h
	return "%d %s · %d %s" % [h, "human" if h == 1 else "humans", r, "robot" if r == 1 else "robots"]

# ---------------------------------------------------------------- setup state

## mode: "COMPUTER" | "PASS_AND_PLAY". Returns a repaired snapshot:
## {player_count, slot, paws, colors, humans, names, difficulty}.
static func repair(mode: String, s: Dictionary, unlocked: Array, preferred: String) -> Dictionary:
	var computer := mode == "COMPUTER"
	var count := clampi(int(s.get("player_count", 2)), 2, 4)
	var h := humans(count, int(s.get("humans", 1))) if computer else count
	var paws: Array = Starter.normalize(count, Array(s.get("paws", [])), preferred, computer, unlocked, h)
	var stored: Array = Array(s.get("colors", []))
	var colors: Array
	if computer:
		var first = stored[0] if not stored.is_empty() else DEFAULT_COLOR
		colors = colors_for(count, first if COLORS.has(first) else DEFAULT_COLOR)
	else:
		colors = normalize_colors(count, stored, stored[0] if not stored.is_empty() and COLORS.has(stored[0]) else DEFAULT_COLOR)
	var names: Array = Array(s.get("names", DEFAULT_NAMES))
	var clean_names: Array = []
	for i in 4:
		var n := str(names[i]).strip_edges().left(14) if i < names.size() else ""
		clean_names.append(n if not n.is_empty() else DEFAULT_NAMES[i])
	return {"player_count": count, "slot": clampi(int(s.get("slot", 0)), 0, count - 1), "paws": paws,
		"colors": colors, "humans": h, "names": clean_names, "difficulty": clampi(int(s.get("difficulty", 1)), 0, 2)}

## Live OfflineCharacterSetupStore keys in `ludo_paws_offline_character_setup_v1`
## ({mode}_player_count, _selected_slot, _human_count, _character_ids, _colors_by_seat;
## mode = computer | pass_and_play). V2-only extras (seat names) stay in `path`.
const PREFS := "ludo_paws_offline_character_setup_v1"

static func _prefs():
	return load("res://scripts/game/platform/native_prefs.gd").open(PREFS)

static func _csv(v) -> Array:
	var out: Array = []
	if v == null:
		return out
	for part in str(v).split(","):
		if not part.strip_edges().is_empty():
			out.append(part.strip_edges())
	return out

static func load_setup(mode: String, unlocked: Array, preferred: String, count_override := -1) -> Dictionary:
	var p = _prefs()
	var raw := {}
	var m := mode.to_lower()
	if p.get_int("schema_version", SCHEMA) == SCHEMA:
		if p.contains(m + "_player_count"):
			raw["player_count"] = p.get_int(m + "_player_count", 2)
		if p.contains(m + "_selected_slot"):
			raw["slot"] = p.get_int(m + "_selected_slot", 0)
		if p.contains(m + "_human_count"):
			raw["humans"] = p.get_int(m + "_human_count", 1)
		if p.contains(m + "_character_ids"):
			raw["paws"] = _csv(p.get_string(m + "_character_ids", ""))
		if p.contains(m + "_colors_by_seat"):
			raw["colors"] = _csv(p.get_string(m + "_colors_by_seat", ""))
	var cfg := ConfigFile.new()
	if cfg.load(path) == OK and cfg.has_section_key(mode, "names"):
		raw["names"] = cfg.get_value(mode, "names")
	if count_override >= 2:
		raw["player_count"] = count_override
	return repair(mode, raw, unlocked, preferred)

## Saves (after repair) and returns what was stored.
static func save_setup(mode: String, s: Dictionary, unlocked: Array) -> Dictionary:
	var preferred: String = s["paws"][0] if not Array(s.get("paws", [])).is_empty() else "dog"
	var repaired := repair(mode, s, unlocked, preferred)
	var m := mode.to_lower()
	_prefs().edit().put_int("schema_version", SCHEMA).put_int(m + "_player_count", repaired["player_count"]) \
		.put_int(m + "_selected_slot", repaired["slot"]).put_int(m + "_human_count", repaired["humans"]) \
		.put_string(m + "_character_ids", ",".join(repaired["paws"])).put_string(m + "_colors_by_seat", ",".join(repaired["colors"])).commit()
	var cfg := ConfigFile.new()
	cfg.load(path)
	cfg.set_value(mode, "names", repaired["names"])
	cfg.save(path)
	return repaired

## Seat roster for the match: [{color, characterId, bot, name}] in seat order.
static func roster(mode: String, s: Dictionary) -> Array:
	var computer := mode == "COMPUTER"
	var out: Array = []
	for seat in int(s["player_count"]):
		var bot: bool = computer and seat >= int(s["humans"])
		var name: String
		if computer:
			name = "You" if seat == 0 else ("Player %d" % (seat + 1) if not bot else "Robot %d" % (seat - int(s["humans"]) + 1))
		else:
			name = s["names"][seat]
		out.append({"color": s["colors"][seat], "characterId": s["paws"][seat], "bot": bot, "name": name})
	return out
