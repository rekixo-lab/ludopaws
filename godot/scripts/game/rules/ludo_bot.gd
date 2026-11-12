@tool
extends RefCounted
## Robot move choice for local matches.
##
## NORMAL and HARD use a port of the live Android offline Robot policy
## (feature/offline/domain/ai/LudoPawsComputerMovePolicy.kt). It is deterministic,
## only ranks the legal tokens supplied by the engine, and never touches dice.
## The live app has no difficulty levels; EASY is a V2-only wrapper that picks a
## random legal token some of the time.

const R = preload("res://scripts/game/rules/ludo_rulesets.gd")

enum Difficulty { EASY, NORMAL, HARD }

const MAX_TOKEN_INDEX := 3
const SCORE_FINISH := 120000
const SCORE_CAPTURE_EACH := 25000
const SCORE_HOME_LANE := 18000
const SCORE_RESCUE := 11000
const SCORE_YARD_EXIT := 6000
const SCORE_SAFE_LANDING := 3500
const SCORE_PROGRESS_STEP := 30
const SCORE_HOME_LANE_PROGRESS_BASE := 4000
const SCORE_HOME_LANE_STEP := 350
const SCORE_HOME_PROGRESS := 8000
const PENALTY_THREAT_ROUTE := 3200
const PENALTY_LEAVE_SAFETY_FOR_DANGER := 2500

static func choose(state: Dictionary, difficulty: int = Difficulty.NORMAL) -> int:
	var pending = state.get("pendingRoll")
	if not (pending is Dictionary):
		return -1
	var legal: Array = pending.get("legalTokenIndexes", [])
	if legal.is_empty():
		return -1
	if difficulty == Difficulty.EASY and randf() < 0.6:
		return int(legal[randi() % legal.size()])
	var outcome = pending.get("outcome")
	if outcome == null:
		return -1
	return choose_token(state, int(pending["seat"]), legal, int(outcome), bool(pending.get("comebackBonusApplied", false)))

## Highest score wins; ties go to the lower token index (Kotlin: thenBy { -tokenIndex }
## inside maxWith, which keeps the larger of -index).
static func choose_token(state: Dictionary, seat: int, legal: Array, outcome: int, comeback := false) -> int:
	var best := -1
	var best_score := 0
	for value in legal:
		var token := int(value)
		var evaluation := evaluate(state, seat, token, outcome, comeback)
		if evaluation.is_empty():
			continue
		var score := int(evaluation["score"])
		if best == -1 or score > best_score or (score == best_score and token < best):
			best = token
			best_score = score
	return best

static func evaluate(state: Dictionary, seat: int, token: int, outcome: int, comeback := false) -> Dictionary:
	var player: Dictionary = state["players"][seat]
	var tokens: Array = player["tokens"]
	if token < 0 or token >= tokens.size():
		return {}
	var source := int(tokens[token])
	var destination
	if comeback and source == R.YARD:
		destination = 0
	else:
		destination = destination_for_roll(source, outcome)
	if destination == null:
		return {}
	var dest := int(destination)
	var color: String = player["color"]
	var captures := _capture_count(state, seat, dest)
	var source_threats := _threat_routes(state, seat, source)
	var dest_threats := _threat_routes(state, seat, dest)
	var source_safe := _is_safe(color, source)
	var dest_safe := _is_safe(color, dest)
	var rescued := source_threats > 0 and dest_threats == 0
	var entered_lane := _is_track(source) and dest >= R.FIRST_HOME_LANE and dest < R.HOME

	var score := _progress_score(dest)
	if dest == R.HOME:
		score += SCORE_FINISH
	score += captures * SCORE_CAPTURE_EACH
	if entered_lane:
		score += SCORE_HOME_LANE
	if rescued:
		score += SCORE_RESCUE
	if source == R.YARD:
		score += SCORE_YARD_EXIT
	if dest_safe:
		score += SCORE_SAFE_LANDING
	score -= dest_threats * PENALTY_THREAT_ROUTE
	if source_safe and not dest_safe and dest_threats > 0:
		score -= PENALTY_LEAVE_SAFETY_FOR_DANGER
	score += maxi(MAX_TOKEN_INDEX - token, 0)
	return {
		"tokenIndex": token, "destination": dest, "score": score, "captures": captures,
		"destinationSafe": dest_safe, "threatRoutes": dest_threats, "rescuedFromThreat": rescued,
	}

## LudoPathEncoding.destinationForRoll.
static func destination_for_roll(position: int, roll: int):
	if roll < 1 or roll > 6:
		return null
	if position == R.YARD:
		return 0 if roll == 6 else null
	if position == R.HOME:
		return null
	var normalized := _normalize(position)
	if normalized < 0 or normalized > R.HOME - 1:
		return null
	var destination := normalized + roll
	if normalized <= R.LAST_TRACK_POSITION and destination > R.LAST_TRACK_POSITION:
		destination += 1
	return destination if destination <= R.HOME else null

static func _normalize(position: int) -> int:
	return R.FIRST_HOME_LANE if position == R.RESERVED_HOME_ENTRY else position

static func _is_track(position: int) -> bool:
	return position >= 0 and position <= R.LAST_TRACK_POSITION

static func _global(color: String, position: int):
	var normalized := _normalize(position)
	if not _is_track(normalized) or not R.START_OFFSETS.has(color):
		return null
	return (int(R.START_OFFSETS[color]) + normalized) % R.TRACK_CELLS

static func _is_safe(color: String, position: int) -> bool:
	if position >= R.FIRST_HOME_LANE and position <= R.HOME:
		return true
	var cell = _global(color, position)
	return cell != null and int(cell) in R.SAFE_GLOBAL_CELLS

static func _same_team(state: Dictionary, a: int, b: int) -> bool:
	var first = state["players"][a].get("teamId")
	return first != null and str(first) != "" and first == state["players"][b].get("teamId")

static func _opponents(state: Dictionary, seat: int) -> Array:
	var out: Array = []
	var mover_id = state["players"][seat]["playerId"]
	for other in state["players"].size():
		if state["players"][other]["playerId"] != mover_id and not _same_team(state, seat, other):
			out.append(other)
	return out

static func _capture_count(state: Dictionary, seat: int, destination: int) -> int:
	var color: String = state["players"][seat]["color"]
	if not _is_track(destination) or _is_safe(color, destination):
		return 0
	var target = _global(color, destination)
	if target == null:
		return 0
	var count := 0
	for other in _opponents(state, seat):
		var opponent: Dictionary = state["players"][other]
		for position in opponent["tokens"]:
			if _global(opponent["color"], int(position)) == target:
				count += 1
	return count

static func _threat_routes(state: Dictionary, seat: int, position: int) -> int:
	var color: String = state["players"][seat]["color"]
	if not _is_track(position) or _is_safe(color, position):
		return 0
	var target = _global(color, position)
	if target == null:
		return 0
	var count := 0
	for other in _opponents(state, seat):
		var opponent: Dictionary = state["players"][other]
		for raw in opponent["tokens"]:
			var from := _normalize(int(raw))
			if not _is_track(from):
				continue
			for roll in range(1, 7):
				var destination = destination_for_roll(from, roll)
				if destination != null and _is_track(int(destination)) and _global(opponent["color"], int(destination)) == target:
					count += 1
	return count

static func _progress_score(destination: int) -> int:
	if destination >= 0 and destination <= 51:
		return (destination + 1) * SCORE_PROGRESS_STEP
	if destination >= R.FIRST_HOME_LANE and destination < R.HOME:
		return SCORE_HOME_LANE_PROGRESS_BASE + (destination - R.FIRST_HOME_LANE + 1) * SCORE_HOME_LANE_STEP
	if destination == R.HOME:
		return SCORE_HOME_PROGRESS
	return 0
