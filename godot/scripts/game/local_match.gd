@tool
extends RefCounted
## Offline match model used by the Meadow UI (Robot / Pass & Play / local Team Up).
## Rules come from the live-parity engine in scripts/game/rules/; this class only
## mirrors engine state into the simple fields the existing UI already reads.

const Rules = preload("res://scripts/game/rules/ludo_engine.gd")
const R = preload("res://scripts/game/rules/ludo_rulesets.gd")
const Bot = preload("res://scripts/game/rules/ludo_bot.gd")
const Snapshot = preload("res://scripts/game/reactions/match_snapshot.gd")

signal rolled(result: Dictionary)
signal moved(result: Dictionary)

## Authoritative engine state (server-shaped Dictionary; safe to save as JSON).
var state: Dictionary = {}
## Last engine results, for animation / FX / sound / character reactions.
var last_roll_result: Dictionary = {}
var last_move_result: Dictionary = {}

# UI mirror fields (read-only for callers).
var colors: Array[int] = []
var pawns: Array[PackedInt32Array] = []
var finished: Array[int] = []
var legal: Array[int] = []
var turn := 0
var roll := 0
var random_roll := 0
var opening_bonus := false
var comeback := false
var complete := false
var message := ""

## seats: board color index per seat (0 Red, 1 Green, 2 Yellow, 3 Blue).
## players: optional [{displayName, characterId, playerType}] per seat.
func _init(seats: Array[int], mode := "PASS_AND_PLAY", players: Array = [], ruleset_key := "") -> void:
	var setup: Array = []
	for i in seats.size():
		var entry: Dictionary = players[i] if i < players.size() else {}
		setup.append({
			"playerId": "local-%d" % i,
			"displayName": entry.get("displayName", "Player %d" % (i + 1)),
			"characterId": entry.get("characterId", "dog"),
			"playerType": entry.get("playerType", "HUMAN"),
			"color": R.COLORS[seats[i]],
		})
	var options := {"matchMode": mode, "turnRotation": "PERIMETER"}
	if not ruleset_key.is_empty():
		options["rulesetKey"] = ruleset_key
	state = Rules.new_match(setup, options)
	_ensure_presentation_fields()
	_sync()

static func from_saved(saved: Dictionary) -> RefCounted:
	var seats: Array[int] = []
	for player in saved.get("players", []):
		seats.append(maxi(0, R.COLORS.find(player.get("color", "RED"))))
	var game = load("res://scripts/game/local_match.gd").new(seats)
	game.state = _normalize(saved)
	game._ensure_presentation_fields()
	game._sync()
	return game

func to_saved() -> Dictionary:
	return state.duplicate(true)

## Live MatchSnapshot view of this match for the reaction pipeline (presentation only).
func snapshot() -> Dictionary:
	var probe_t := Time.get_ticks_usec()
	var probe_r = _probed_snapshot()
	preload("res://scripts/game/perf.gd").span("snapshot", Time.get_ticks_usec() - probe_t)
	return probe_r

func _probed_snapshot() -> Dictionary:
	return Snapshot.from_state(state)

## Offline matches carry a local match id, a roll counter and a bounded roll history
## (OfflineGameEngine LocalState), which the reaction pipeline reads.
func _ensure_presentation_fields() -> void:
	if str(state.get("matchId", "")).is_empty():
		state["matchId"] = "offline-%d-%d" % [Time.get_unix_time_from_system() * 1000, randi()]
	if not state.has("randomEventIndex"):
		state["randomEventIndex"] = 0
	if not state.has("history"):
		state["history"] = []

func acting_seat() -> int:
	return Rules.acting_seat(state)

func apply_roll(value: int) -> bool:
	var probe_t := Time.get_ticks_usec()
	var probe_r = _probed_apply_roll(value)
	preload("res://scripts/game/perf.gd").span("rules_roll", Time.get_ticks_usec() - probe_t)
	return probe_r

func _probed_apply_roll(value: int) -> bool:
	if complete or not legal.is_empty():
		return false
	var seat := acting_seat()
	var result := Rules.roll(state, value)
	if not result.get("ok", false):
		return false
	state = result["state"]
	Snapshot.record_roll(state, seat, int(result["effectiveOutcome"]))
	last_roll_result = result
	roll = int(result["effectiveOutcome"])
	random_roll = int(result["randomOutcome"])
	opening_bonus = result["openingRollApplied"]
	comeback = result["comebackBonusApplied"]
	message = "Rolled %d" % random_roll
	if opening_bonus:
		message = "Opening bonus · Counts as 6"
	elif comeback:
		message = "Comeback · Bring a paw out"
	if result["forfeitedThirdSix"]:
		message = "Third six · Turn passed"
	elif result["legalTokenIndexes"].is_empty():
		message = "Rolled %d · No legal move" % random_roll
		if roll == 6:
			message += " · Roll again"
	_sync()
	last_roll_result["seat"] = seat
	rolled.emit(last_roll_result)
	return true

func move(index: int) -> bool:
	var probe_t := Time.get_ticks_usec()
	var probe_r = _probed_move(index)
	preload("res://scripts/game/perf.gd").span("rules_move", Time.get_ticks_usec() - probe_t)
	return probe_r

func _probed_move(index: int) -> bool:
	if complete or not legal.has(index):
		return false
	var result := Rules.move(state, index)
	if not result.get("ok", false):
		return false
	state = result["state"]
	Snapshot.record_move(state, index, int(result["captures"]))
	last_move_result = result
	message = "Paw %d moved" % (index + 1)
	if result["captures"] > 0:
		message = "Captured %d · Extra turn" % result["captures"]
	elif result["to"] == R.HOME:
		message = "Paw reached home · Extra turn"
	elif result["extraTurn"]:
		message = "Rolled 6 · Extra turn"
	_sync()
	moved.emit(result)
	return true

## Missed the 15 s roll deadline (only when the ruleset has deadlines).
func time_out_turn() -> bool:
	var result := Rules.forfeit_timed_out_turn(state)
	if not result.get("ok", false):
		return false
	state = result["state"]
	message = "Time up · Turn passed" if not result["forfeited"] else "Player left the match"
	_sync()
	return true

func robot_choice(difficulty: int) -> int:
	var probe_t := Time.get_ticks_usec()
	var probe_r = _probed_robot_choice(difficulty)
	preload("res://scripts/game/perf.gd").span("robot_choice", Time.get_ticks_usec() - probe_t)
	return probe_r

func _probed_robot_choice(difficulty: int) -> int:
	return Bot.choose(state, clampi(difficulty, 0, 2))

func is_bot(seat: int) -> bool:
	return state["players"][seat].get("playerType", "HUMAN") == "BOT"

func _sync() -> void:
	colors.clear()
	pawns.clear()
	for player in state["players"]:
		colors.append(maxi(0, R.COLORS.find(player["color"])))
		var tokens := PackedInt32Array()
		for position in player["tokens"]:
			tokens.append(int(position))
		pawns.append(tokens)
	turn = Rules.acting_seat(state)
	legal.clear()
	var pending = state.get("pendingRoll")
	if pending is Dictionary and pending.get("status") == "RESOLVED":
		for index in pending.get("legalTokenIndexes", []):
			legal.append(int(index))
	complete = state["status"] == "FINISHED"
	finished.clear()
	if complete:
		for seat in Rules.standings(state):
			finished.append(seat)
	else:
		var order = state.get("finishOrderPlayerIds")
		if order is Array:
			for id in order:
				finished.append(Rules.seat_of(state, id))

## JSON round-trips turn ints into floats; restore integer fields.
static func _normalize(value):
	if value is Dictionary:
		var out := {}
		for key in value:
			out[key] = _normalize(value[key])
		return out
	if value is Array:
		var list: Array = []
		for item in value:
			list.append(_normalize(item))
		return list
	if value is float and is_equal_approx(value, roundf(value)):
		return int(value)
	return value
