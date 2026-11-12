@tool
extends RefCounted
## Pure Ludo Paws rules engine. Line-for-line port of the live authoritative
## backend (server/src/game-v1-core.js + server/src/game.js).
##
## The match state is a plain Dictionary that uses the same keys as the server
## (players, tokens, turnSeat, consecutiveSixes, pendingRoll, ...). That keeps
## online snapshots, saved games and offline play on one model. Nothing here
## draws, animates, waits or generates randomness: callers pass the roll value.
## Every mutating call returns a new state; the input is never modified.

const R = preload("res://scripts/game/rules/ludo_rulesets.gd")

# ---------------------------------------------------------------- creation

## players: Array of {playerId, displayName, characterId, playerType, color}
## options: {matchMode: "PASS_AND_PLAY"|"ROBOT"|"ONLINE"|"TEAM_UP", rulesetKey,
##           turnRotation: "SEAT" (server, default) | "PERIMETER" (live offline engine)}
static func new_match(players: Array, options: Dictionary = {}) -> Dictionary:
	var mode: String = options.get("matchMode", "PASS_AND_PLAY")
	var team := mode == "TEAM_UP"
	var key: String = options.get("rulesetKey", R.DEFAULT_TEAM if team else R.DEFAULT_CLASSIC)
	if not R.is_known(key) or R.is_team_key(key) != team:
		push_error("Unsupported ruleset %s for %s" % [key, mode])
		key = R.DEFAULT_TEAM if team else R.DEFAULT_CLASSIC
	var seats: Array = []
	for i in players.size():
		var source: Dictionary = players[i]
		var seat := {
			"playerId": str(source.get("playerId", "p%d" % i)),
			"displayName": str(source.get("displayName", "Player %d" % (i + 1))),
			"characterId": str(source.get("characterId", "dog")),
			"playerType": "BOT" if source.get("playerType", "HUMAN") == "BOT" else "HUMAN",
			"color": str(source.get("color", R.COLORS[i % 4])),
			"tokens": [R.YARD, R.YARD, R.YARD, R.YARD],
		}
		if team:
			seat["teamId"] = R.TEAM_ASSIGNMENTS[i % 4]
		seats.append(seat)
	var state := {
		"schemaVersion": 1,
		"status": "ACTIVE",
		"revision": 1,
		"matchMode": mode,
		"rulesetKey": key,
		"players": seats,
		"turnSeat": 0,
		"consecutiveSixes": _zeros(seats.size()),
		"pendingRoll": null,
		"winnerPlayerId": null,
		"turnTimeoutCounts": {},
	}
	# The live Android offline engine passes the turn around the board perimeter
	# (RED, GREEN, YELLOW, BLUE) instead of by seat; seat 0 still starts.
	if options.get("turnRotation", "SEAT") == "PERIMETER" and not team:
		state["turnRotation"] = "PERIMETER"
	if team:
		state["teamAssignments"] = R.TEAM_ASSIGNMENTS.duplicate()
		state["winnerTeamId"] = null
	if R.finishes_all_places(key) and not team:
		state["finishOrderPlayerIds"] = []
	if R.has_opening_six(key):
		var consumed: Array = []
		for i in seats.size():
			consumed.append(false)
		state["openingRollConsumed"] = consumed
	if R.has_comeback(key):
		var waits: Array = []
		for i in seats.size():
			waits.append(-1)
		state["comebackWaits"] = waits
	return state

# ---------------------------------------------------------------- queries

static func is_team_up(state: Dictionary) -> bool:
	return state.get("matchMode", "") == "TEAM_UP"

static func ruleset_key(state: Dictionary) -> String:
	return str(state.get("rulesetKey", "TEAM_UP_V1" if is_team_up(state) else "CLASSIC_V1"))

static func team_for_seat(state: Dictionary, seat: int):
	if not is_team_up(state) or seat < 0 or seat > 3:
		return null
	var assignments: Array = state.get("teamAssignments", R.TEAM_ASSIGNMENTS)
	return assignments[seat]

static func player_finished(player: Dictionary) -> bool:
	var tokens: Array = player.get("tokens", [])
	if tokens.size() != 4:
		return false
	for position in tokens:
		if int(position) != R.HOME:
			return false
	return true

static func is_forfeited(player: Dictionary) -> bool:
	return player.get("forfeitedAt", null) != null

## The seat that must act now. In Team Up a finished player's partner plays.
static func acting_seat(state: Dictionary) -> int:
	var scheduled := int(state.get("turnSeat", 0))
	if not is_team_up(state):
		return scheduled
	var players: Array = state["players"]
	if scheduled < 0 or scheduled >= players.size() or not player_finished(players[scheduled]):
		return scheduled
	var partner := (scheduled + 2) % 4
	if partner < players.size() and not player_finished(players[partner]):
		return partner
	return scheduled

## Relative path step -> shared 52-cell global index (null for yard/home lane).
static func global_cell(color: String, relative: int):
	if relative < 0 or relative > R.LAST_TRACK_POSITION:
		return null
	return (int(R.START_OFFSETS[color]) + relative) % R.TRACK_CELLS

static func is_safe_global(cell) -> bool:
	return cell != null and int(cell) in R.SAFE_GLOBAL_CELLS

## Position 51 is a legacy gap: tokens leave the shared track after 50 and
## enter encoded home-lane position 52 on the next step.
static func destination_for_roll(position: int, roll: int):
	if roll < 1 or roll > 6:
		return null
	if position == R.YARD:
		return 0 if roll == 6 else null
	if position == R.HOME:
		return null
	var normalized := R.FIRST_HOME_LANE if position == R.RESERVED_HOME_ENTRY else position
	if normalized < 0 or normalized > 56:
		return null
	var destination := normalized + roll
	if normalized <= R.LAST_TRACK_POSITION and destination > R.LAST_TRACK_POSITION:
		destination += 1
	return destination if destination <= R.HOME else null

static func destination_for_token(state: Dictionary, seat: int, token: int, roll: int):
	var tokens: Array = state["players"][seat]["tokens"]
	var position := int(tokens[token])
	var pending = state.get("pendingRoll")
	if position == R.YARD and roll >= 1 and roll < 6 \
			and pending is Dictionary and int(pending.get("seat", -1)) == seat \
			and pending.get("comebackBonusApplied", false) == true \
			and R.has_comeback(ruleset_key(state)) \
			and int(_comeback_state(state)[seat]) >= 2 \
			and token == _first_yard_token(tokens) \
			and _only_yard_or_home(tokens):
		return 0
	return destination_for_roll(position, roll)

static func legal_tokens(state: Dictionary, seat: int, roll: int) -> Array:
	var legal: Array = []
	var tokens: Array = state["players"][seat]["tokens"]
	for index in tokens.size():
		if destination_for_token(state, seat, index, roll) != null:
			legal.append(index)
	return legal

# ---------------------------------------------------------------- roll

## Applies a verified/local die value for the acting seat.
## Returns {ok, code?, state, awaitingMove, forfeitedThirdSix, legalTokenIndexes,
##          randomOutcome, effectiveOutcome, openingRollApplied, comebackBonusApplied}
static func roll(state: Dictionary, outcome: int) -> Dictionary:
	if state.get("status") != "ACTIVE":
		return _fail("INVALID_MATCH_STATUS")
	if state.get("pendingRoll") != null:
		return _fail("ROLL_ALREADY_PENDING")
	if outcome < 1 or outcome > 6:
		return _fail("INVALID_ROLL")
	var next: Dictionary = state.duplicate(true)
	var seat := acting_seat(next)
	next["pendingRoll"] = {"status": "COMMITTED", "seat": seat, "scheduledSeat": int(next["turnSeat"])}
	var key := ruleset_key(next)
	var player: Dictionary = next["players"][seat]

	var opening := false
	var comeback_bonus := false
	if R.has_opening_six(key):
		var consumed: Array = _opening_state(next)
		opening = consumed[seat] == false and _all_in_yard(player["tokens"])
		consumed[seat] = true
		next["openingRollConsumed"] = consumed
	if R.has_comeback(key):
		var waits: Array = _comeback_state(next)
		var stranded := _is_stranded(player)
		comeback_bonus = int(waits[seat]) >= 2 and stranded and not opening and outcome != 6
		var counts_without_capture := R.has_yard_comeback(key)
		if not stranded or opening or (counts_without_capture and outcome == 6):
			waits[seat] = -1
		elif outcome != 6 and not comeback_bonus and (int(waits[seat]) >= 0 or counts_without_capture):
			waits[seat] = maxi(0, int(waits[seat])) + 1
		next["comebackWaits"] = waits
		next["pendingRoll"]["comebackBonusApplied"] = comeback_bonus
	var effective := 6 if opening else outcome

	var sixes: Array = next["consecutiveSixes"]
	sixes[seat] = int(sixes[seat]) + 1 if effective == 6 else 0
	var result := {
		"ok": true, "state": next, "awaitingMove": false, "forfeitedThirdSix": false,
		"legalTokenIndexes": [], "seat": seat, "randomOutcome": outcome,
		"effectiveOutcome": effective, "openingRollApplied": opening,
		"comebackBonusApplied": comeback_bonus,
	}
	if effective == 6 and int(sixes[seat]) >= 3:
		sixes[seat] = 0
		next["pendingRoll"] = null
		_advance_turn(next)
		result["forfeitedThirdSix"] = true
	else:
		var legal := legal_tokens(next, seat, effective)
		if legal.is_empty():
			next["pendingRoll"] = null
			if effective != 6:
				_advance_turn(next)
		else:
			var pending: Dictionary = next["pendingRoll"]
			pending["status"] = "RESOLVED"
			pending["outcome"] = effective
			pending["randomOutcome"] = outcome
			pending["openingRollApplied"] = opening
			pending["legalTokenIndexes"] = legal
			result["awaitingMove"] = true
			result["legalTokenIndexes"] = legal
	# The guaranteed opening six never counts toward the three-six penalty.
	if opening:
		sixes[seat] = 0
	_touch(next)
	return result

# ---------------------------------------------------------------- move

## Moves one legal token with the resolved roll.
## Returns {ok, code?, state, seat, token, from, to, captures, captured:[{seat, token, from}],
##          extraTurn, comebackEntry, winnerPlayerId, winnerTeamId, finished}
static func move(state: Dictionary, token: int) -> Dictionary:
	if state.get("status") != "ACTIVE":
		return _fail("INVALID_MATCH_STATUS")
	var pending = state.get("pendingRoll")
	if not (pending is Dictionary) or pending.get("status") != "RESOLVED":
		return _fail("NO_RESOLVED_ROLL")
	var seat := int(pending["seat"])
	if seat != acting_seat(state):
		return _fail("NOT_YOUR_TURN")
	var legal: Array = pending.get("legalTokenIndexes", [])
	if not _contains_int(legal, token):
		return _fail("ILLEGAL_TOKEN_MOVE")

	var next: Dictionary = state.duplicate(true)
	var player: Dictionary = next["players"][seat]
	var roll_value := int(pending["outcome"])
	var current := int(player["tokens"][token])
	var destination = destination_for_token(next, seat, token, roll_value)
	if destination == null:
		return _fail("ILLEGAL_TOKEN_MOVE")
	var comeback_entry := current == R.YARD and roll_value != 6 and int(destination) == 0
	player["tokens"][token] = int(destination)
	var counts: Dictionary = next.get("playerMoveCounts", {})
	counts[player["playerId"]] = int(counts.get(player["playerId"], 0)) + 1
	next["playerMoveCounts"] = counts

	var captured := _capture_opponents(next, seat, int(destination))
	next["pendingRoll"] = null
	var result := {
		"ok": true, "state": next, "seat": seat, "token": token, "from": current,
		"to": int(destination), "captures": captured.size(), "captured": captured,
		"extraTurn": false, "comebackEntry": comeback_entry,
		"winnerPlayerId": null, "winnerTeamId": null, "finished": false,
	}

	var finished_now := false
	if is_team_up(next):
		var team = team_for_seat(next, seat)
		if _team_finished(next, team):
			next["status"] = "FINISHED"
			next["winnerPlayerId"] = null
			next["winnerTeamId"] = team
			result["winnerTeamId"] = team
			finished_now = true
	elif player_finished(player):
		finished_now = true
		var order = next.get("finishOrderPlayerIds")
		if order is Array and not order.has(player["playerId"]):
			order.append(player["playerId"])
		var remaining := _remaining_players(next)
		if not (order is Array) or remaining.size() <= 1:
			if order is Array and remaining.size() == 1:
				order.append(remaining[0]["playerId"])
			next["status"] = "FINISHED"
			next["winnerPlayerId"] = order[0] if order is Array and not order.is_empty() else player["playerId"]
		else:
			_advance_turn(next)
		result["winnerPlayerId"] = next.get("winnerPlayerId")

	if not finished_now:
		var reached_home := int(destination) == R.HOME
		var extra := not comeback_entry and (reached_home or roll_value == 6 or captured.size() > 0)
		result["extraTurn"] = extra
		if not extra:
			_advance_turn(next)

	if R.has_comeback(ruleset_key(next)):
		var waits: Array = _comeback_state(state)
		var players_before: Array = state["players"]
		var players_after: Array = next["players"]
		for i in players_after.size():
			var after: Array = players_after[i]["tokens"]
			var before: Array = players_before[i]["tokens"]
			var was_captured := false
			for t in after.size():
				var b := int(before[t])
				if int(after[t]) == R.YARD and b >= 0 and b < R.HOME:
					was_captured = true
			if was_captured and _is_stranded(players_after[i]):
				waits[i] = 0
			elif not _is_stranded(players_after[i]):
				waits[i] = -1
		next["comebackWaits"] = waits

	result["finished"] = next["status"] == "FINISHED"
	_touch(next)
	return result

# ---------------------------------------------------------------- turn timeout

## Missed roll deadline (v4+). After four misses the player forfeits.
static func forfeit_timed_out_turn(state: Dictionary) -> Dictionary:
	if state.get("status") != "ACTIVE" or state.get("pendingRoll") != null:
		return _fail("INVALID_MATCH_STATUS")
	if not R.has_turn_deadline(ruleset_key(state)):
		return _fail("TURN_TIMEOUT_DISABLED")
	var next: Dictionary = state.duplicate(true)
	var seat := acting_seat(next)
	var player: Dictionary = next["players"][seat]
	var timeouts: Dictionary = next.get("turnTimeoutCounts", {})
	var count := int(timeouts.get(player["playerId"], 0)) + 1
	timeouts[player["playerId"]] = count
	next["turnTimeoutCounts"] = timeouts
	var forfeited := count >= R.MAX_MISSED_ROLLS_BEFORE_FORFEIT
	if forfeited:
		if is_team_up(next):
			var losing = team_for_seat(next, seat)
			for i in next["players"].size():
				if team_for_seat(next, i) == losing:
					next["players"][i]["forfeitedAt"] = 1
					next["players"][i]["tokens"] = [R.YARD, R.YARD, R.YARD, R.YARD]
					next["consecutiveSixes"][i] = 0
			next["status"] = "FINISHED"
			next["winnerTeamId"] = "B" if losing == "A" else "A"
		else:
			player["forfeitedAt"] = 1
			player["tokens"] = [R.YARD, R.YARD, R.YARD, R.YARD]
			next["consecutiveSixes"][seat] = 0
			var remaining := _remaining_players(next)
			if remaining.size() <= 1:
				next["status"] = "FINISHED"
				var order = next.get("finishOrderPlayerIds")
				if not remaining.is_empty() and order is Array and not order.has(remaining[0]["playerId"]):
					order.append(remaining[0]["playerId"])
				if order is Array and not order.is_empty():
					next["winnerPlayerId"] = order[0]
				elif not remaining.is_empty():
					next["winnerPlayerId"] = remaining[0]["playerId"]
			else:
				_advance_turn(next)
	else:
		_advance_turn(next)
	_touch(next)
	return {"ok": true, "state": next, "seat": seat, "count": count, "forfeited": forfeited}

# ---------------------------------------------------------------- helpers

static func seat_of(state: Dictionary, player_id) -> int:
	var players: Array = state["players"]
	for i in players.size():
		if players[i]["playerId"] == player_id:
			return i
	return -1

## Final standings as seat indexes (winner first).
static func standings(state: Dictionary) -> Array:
	var seats: Array = []
	var order = state.get("finishOrderPlayerIds")
	if order is Array:
		for id in order:
			seats.append(seat_of(state, id))
	elif state.get("winnerTeamId") != null:
		for i in state["players"].size():
			if team_for_seat(state, i) == state["winnerTeamId"]:
				seats.append(i)
	elif state.get("winnerPlayerId") != null:
		seats.append(seat_of(state, state["winnerPlayerId"]))
	for i in state["players"].size():
		if not seats.has(i):
			seats.append(i)
	return seats

static func _capture_opponents(state: Dictionary, moving_seat: int, destination: int) -> Array:
	var captured: Array = []
	if destination < 0 or destination > R.LAST_TRACK_POSITION:
		return captured
	var players: Array = state["players"]
	var mover: Dictionary = players[moving_seat]
	var cell = global_cell(mover["color"], destination)
	if is_safe_global(cell):
		return captured
	var moving_team = team_for_seat(state, moving_seat)
	if is_team_up(state) and R.has_team_stack_protection(ruleset_key(state)):
		var opposing := 0
		for seat in players.size():
			if team_for_seat(state, seat) == moving_team:
				continue
			for position in players[seat]["tokens"]:
				if global_cell(players[seat]["color"], int(position)) == cell:
					opposing += 1
		if opposing >= 2:
			return captured
	for seat in players.size():
		if seat == moving_seat:
			continue
		if is_team_up(state) and team_for_seat(state, seat) == moving_team:
			continue
		var tokens: Array = players[seat]["tokens"]
		var matching: Array = []
		for index in tokens.size():
			if global_cell(players[seat]["color"], int(tokens[index])) == cell:
				matching.append(index)
		# A stack of two or more own tokens is protected from capture.
		if matching.size() != 1:
			continue
		captured.append({"seat": seat, "token": matching[0], "from": int(tokens[matching[0]])})
		tokens[matching[0]] = R.YARD
	return captured

static func _team_finished(state: Dictionary, team) -> bool:
	if team == null:
		return false
	var members := 0
	for seat in state["players"].size():
		if team_for_seat(state, seat) == team:
			if not player_finished(state["players"][seat]):
				return false
			members += 1
	return members == 2

static func _remaining_players(state: Dictionary) -> Array:
	var order = state.get("finishOrderPlayerIds")
	var remaining: Array = []
	for player in state["players"]:
		if is_forfeited(player):
			continue
		if order is Array and order.has(player["playerId"]):
			continue
		remaining.append(player)
	return remaining

static func _advance_turn(state: Dictionary) -> void:
	var players: Array = state["players"]
	var count := players.size()
	if count < 1:
		return
	var start := int(state.get("turnSeat", -1))
	var order = state.get("finishOrderPlayerIds")
	for candidate in _rotation_after(state, start):
		var player: Dictionary = players[candidate]
		if is_forfeited(player):
			continue
		if order is Array and order.has(player["playerId"]):
			continue
		state["turnSeat"] = candidate
		return
	state["turnSeat"] = start if start >= 0 and start < count else 0

## Seats to try, in order, after `start`.
static func _rotation_after(state: Dictionary, start: int) -> Array:
	var players: Array = state["players"]
	var count := players.size()
	var out: Array = []
	if state.get("turnRotation") == "PERIMETER" and start >= 0 and start < count:
		var first := R.COLORS.find(players[start]["color"])
		for offset in range(1, R.COLORS.size() + 1):
			var color: String = R.COLORS[(first + offset) % R.COLORS.size()]
			for seat in count:
				if players[seat]["color"] == color:
					out.append(seat)
		return out
	for offset in range(1, count + 1):
		out.append((((start + offset) % count) + count) % count)
	return out

static func _touch(state: Dictionary) -> void:
	state["revision"] = int(state.get("revision", 0)) + 1

static func _opening_state(state: Dictionary) -> Array:
	var stored: Array = state.get("openingRollConsumed", [])
	var out: Array = []
	for seat in state["players"].size():
		out.append(seat < stored.size() and stored[seat] == true)
	return out

static func _comeback_state(state: Dictionary) -> Array:
	var stored: Array = state.get("comebackWaits", [])
	var out: Array = []
	for seat in state["players"].size():
		var value := -1
		if seat < stored.size() and stored[seat] != null:
			value = int(stored[seat])
		out.append(value if value >= 0 and value <= 2 else -1)
	return out

static func _is_stranded(player: Dictionary) -> bool:
	var tokens: Array = player["tokens"]
	var any_yard := false
	for position in tokens:
		var p := int(position)
		if p == R.YARD:
			any_yard = true
		elif p != R.HOME:
			return false
	return any_yard

static func _all_in_yard(tokens: Array) -> bool:
	if tokens.size() != 4:
		return false
	for position in tokens:
		if int(position) != R.YARD:
			return false
	return true

static func _only_yard_or_home(tokens: Array) -> bool:
	for position in tokens:
		var p := int(position)
		if p != R.YARD and p != R.HOME:
			return false
	return true

static func _first_yard_token(tokens: Array) -> int:
	for index in tokens.size():
		if int(tokens[index]) == R.YARD:
			return index
	return -1

static func _contains_int(values: Array, wanted: int) -> bool:
	for value in values:
		if int(value) == wanted:
			return true
	return false

static func _zeros(count: int) -> Array:
	var out: Array = []
	for i in count:
		out.append(0)
	return out

static func _fail(code: String) -> Dictionary:
	return {"ok": false, "code": code}
