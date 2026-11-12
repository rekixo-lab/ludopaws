@tool
extends RefCounted
## Presentation-only state-diff engine: derives game moments from two immutable
## snapshots (match_snapshot.gd). Never rolls, moves or mutates game state, so offline
## and online share the same reactions. Port of LudoPawsGameMomentDetector.kt.
##
## Moment = {matchId, eventIndex, type, playerId, seat, tokenIndex, relatedPlayerId, value}

const Seq = preload("res://scripts/game/reactions/seq.gd")

const POOR_ROLL_STREAK_LENGTH := 3
const START_OFFSETS := {"RED": 0, "GREEN": 13, "YELLOW": 26, "BLUE": 39}
const SAFE_GLOBAL_CELLS := [0, 8, 13, 21, 26, 34, 39, 47]

static func detect(previous, current) -> Array:
	if previous == null or current == null or previous["matchId"] != current["matchId"]:
		return []
	var moments: Array = []
	_turn_change(previous, current, moments)
	_roll_lifecycle(previous, current, moments)
	_token_changes(previous, current, moments)
	_lead_change(previous, current, moments)
	_finish(previous, current, moments)
	var seen := {}
	var out: Array = []
	for m in moments:
		var key := ":".join([m["matchId"], str(m["eventIndex"]), m["type"], m["playerId"],
			"" if m["tokenIndex"] == null else str(m["tokenIndex"]), "" if m["relatedPlayerId"] == null else m["relatedPlayerId"]])
		if not seen.has(key):
			seen[key] = true
			out.append(m)
	return out

static func idle_moment(current, now_ms: int, last_change_ms: int, threshold_ms := 24000) -> Variant:
	if current == null or current["status"] != "ACTIVE" or threshold_ms <= 0 or now_ms - last_change_ms < threshold_ms:
		return null
	var active = _player_at(current, _active_seat(current))
	if active == null:
		return null
	return _moment(current, "IDLE_WAITING", active, current["randomEventIndex"])

static func _active_seat(s: Dictionary) -> int:
	return int(s["actingSeat"]) if s.get("actingSeat") != null else int(s["turnSeat"])

static func _player_at(s: Dictionary, seat: int) -> Variant:
	var players: Array = s["players"]
	return players[seat] if seat >= 0 and seat < players.size() else null

static func _player_by_id(s: Dictionary, id) -> Variant:
	for p in s["players"]:
		if p["playerId"] == id:
			return p
	return null

static func _turn_change(previous: Dictionary, current: Dictionary, sink: Array) -> void:
	if current["status"] != "ACTIVE" or int(previous["turnSeat"]) == int(current["turnSeat"]):
		return
	var active = _player_at(current, _active_seat(current))
	if active == null:
		return
	sink.append(_moment(current, "TURN_STARTED", active, current["randomEventIndex"]))

static func _roll_lifecycle(previous: Dictionary, current: Dictionary, sink: Array) -> void:
	var pending = current.get("pendingRoll")
	var previous_pending = previous.get("pendingRoll")
	if pending != null and (previous_pending == null or int(previous_pending["eventIndex"]) != int(pending["eventIndex"]) \
			or previous_pending["status"] != pending["status"]):
		var player = _player_at(current, int(pending["seat"]))
		if player != null:
			sink.append(_moment(current, "ROLL_STARTED", player, pending["eventIndex"], null, null, pending.get("outcome")))
	var previous_indexes := {}
	for e in previous["history"]:
		previous_indexes[int(e["eventIndex"])] = true
	for event in current["history"]:
		if previous_indexes.has(int(event["eventIndex"])) or event.get("outcome") == null:
			continue
		var player = _player_by_id(current, event["playerId"])
		if player == null:
			continue
		var outcome := int(event["outcome"])
		var third_six := outcome == 6 and _is_third_consecutive_six(current, event)
		if third_six:
			sink.append(_moment(current, "THIRD_SIX_FORFEIT", player, event["eventIndex"], null, null, outcome))
		elif outcome == 6:
			sink.append(_moment(current, "SIX_ROLLED", player, event["eventIndex"], null, null, outcome))
		elif outcome <= 2:
			sink.append(_moment(current, "LOW_ROLL", player, event["eventIndex"], null, null, outcome))
			if _is_poor_roll_streak(current, event):
				sink.append(_moment(current, "POOR_ROLL_STREAK", player, event["eventIndex"], null, null, POOR_ROLL_STREAK_LENGTH))
		var pending_for_event: bool = pending != null and int(pending["eventIndex"]) == int(event["eventIndex"])
		if not third_six and event.get("moveTokenIndex") == null and not pending_for_event:
			var type := "EXACT_HOME_MISS" if _is_exact_home_miss(player, outcome) else "NO_LEGAL_MOVE"
			sink.append(_moment(current, type, player, event["eventIndex"], null, null, outcome))

static func _token_changes(previous: Dictionary, current: Dictionary, sink: Array) -> void:
	for current_player in current["players"]:
		var previous_player = _player_by_id(previous, current_player["playerId"])
		if previous_player == null:
			continue
		var now_tokens: Array = current_player["tokens"]
		var before_tokens: Array = previous_player["tokens"]
		for token in now_tokens.size():
			if token >= before_tokens.size():
				continue
			var from := int(before_tokens[token])
			var to := int(now_tokens[token])
			if from == to:
				continue
			var move_event = _latest_move_event(current, current_player["playerId"], token)
			var event_index = move_event["eventIndex"] if move_event != null else current["randomEventIndex"]
			if from >= 0 and to == -1:
				sink.append(_moment(current, "TOKEN_CAPTURED", current_player, event_index, token))
				continue
			if to <= from:
				continue
			var movement := "TOKEN_LEFT_YARD" if from == -1 and to == 0 else "TOKEN_MOVED"
			sink.append(_moment(current, movement, current_player, event_index, token, null, to))
			if move_event != null and _was_only_legal_move(previous, move_event, token):
				sink.append(_moment(current, "ONLY_LEGAL_MOVE", current_player, event_index, token, null, to))
			if move_event != null and int(move_event.get("captures", 0)) > 0:
				sink.append(_moment(current, "CAPTURE_MADE", current_player, move_event["eventIndex"], token, null, int(move_event["captures"])))
			if to == 57:
				sink.append(_moment(current, "HOME_REACHED", current_player, event_index, token))
			elif from >= 0 and from <= 51 and to >= 52 and to <= 56:
				sink.append(_moment(current, "HOME_LANE_ENTERED", current_player, event_index, token, null, to))
			elif _is_safe_position(current_player["color"], to):
				sink.append(_moment(current, "SAFE_REACHED", current_player, event_index, token))
			var threat = _threatening_player(current, current_player, to)
			if threat != null:
				sink.append(_moment(current, "TOKEN_THREATENED", current_player, event_index, token, threat["playerId"], to))

static func _lead_change(previous: Dictionary, current: Dictionary, sink: Array) -> void:
	var previous_leader = _unique_leader(previous)
	var current_leader = _unique_leader(current)
	if current_leader == null:
		return
	if (previous_leader != null and previous_leader["playerId"] == current_leader["playerId"]) or _lead_margin(current, current_leader) < 8:
		return
	sink.append(_moment(current, "PLAYER_LEADING", current_leader, current["randomEventIndex"], null, null, _progress_score(current_leader)))

static func _finish(previous: Dictionary, current: Dictionary, sink: Array) -> void:
	if previous["status"] == "FINISHED" or current["status"] != "FINISHED":
		return
	var history: Array = current["history"]
	var event_index = history[-1]["eventIndex"] if not history.is_empty() else current["randomEventIndex"]
	var team = current.get("winnerTeamId")
	if team != null and str(team).strip_edges() != "":
		for player in current["players"]:
			sink.append(_moment(current, "TEAM_WIN" if player.get("teamId") == team else "TEAM_LOSS", player, event_index))
		return
	var winner = current.get("winnerPlayerId")
	if winner == null:
		return
	for player in current["players"]:
		sink.append(_moment(current, "MATCH_WIN" if player["playerId"] == winner else "MATCH_LOSS", player, event_index))

static func _was_only_legal_move(previous: Dictionary, event: Dictionary, token: int) -> bool:
	var pending = previous.get("pendingRoll")
	if pending == null or int(pending["eventIndex"]) != int(event["eventIndex"]) or pending["status"] != "RESOLVED":
		return false
	var legal := {}
	for i in pending["legalTokenIndexes"]:
		legal[int(i)] = true
	return legal.size() == 1 and legal.has(token)

static func _is_exact_home_miss(player: Dictionary, outcome: int) -> bool:
	for position in player["tokens"]:
		if int(position) >= 52 and int(position) <= 56 and int(position) + outcome > 57:
			return true
	return false

static func _threatening_player(current: Dictionary, player: Dictionary, position: int) -> Variant:
	if position < 0 or position > 51 or _is_safe_position(player["color"], position):
		return null
	var target = _global_track_cell(player["color"], position)
	if target == null:
		return null
	for opponent in current["players"]:
		if opponent["playerId"] == player["playerId"] or _same_team(player, opponent):
			continue
		for opponent_position in opponent["tokens"]:
			var op := int(opponent_position)
			if op < 0 or op > 51:
				continue
			var opponent_global = _global_track_cell(opponent["color"], op)
			if opponent_global == null:
				continue
			var distance := posmod(int(target) - int(opponent_global) + 52, 52)
			if distance >= 1 and distance <= 6 and op + distance <= 51:
				return opponent
	return null

static func _same_team(first: Dictionary, second: Dictionary) -> bool:
	var team = first.get("teamId")
	return team != null and str(team).strip_edges() != "" and team == second.get("teamId")

static func _global_track_cell(color: String, position: int) -> Variant:
	if position < 0 or position > 51 or not START_OFFSETS.has(color):
		return null
	return (int(START_OFFSETS[color]) + position) % 52

static func _is_third_consecutive_six(current: Dictionary, event: Dictionary) -> bool:
	var upto: Array = (current["history"] as Array).filter(func(e): return int(e["eventIndex"]) <= int(event["eventIndex"]))
	var recent := Seq.take_last(upto, 3)
	if recent.size() != 3:
		return false
	for e in recent:
		if e["playerId"] != event["playerId"] or e.get("outcome") == null or int(e["outcome"]) != 6:
			return false
	return true

static func _is_poor_roll_streak(current: Dictionary, event: Dictionary) -> bool:
	var mine: Array = (current["history"] as Array).filter(func(e): return int(e["eventIndex"]) <= int(event["eventIndex"]) \
		and e["playerId"] == event["playerId"] and e.get("outcome") != null)
	var recent := Seq.take_last(mine, POOR_ROLL_STREAK_LENGTH)
	if recent.size() != POOR_ROLL_STREAK_LENGTH:
		return false
	for e in recent:
		if int(e["outcome"]) > 2:
			return false
	return true

static func _is_safe_position(color: String, position: int) -> bool:
	var g = _global_track_cell(color, position)
	return g != null and int(g) in SAFE_GLOBAL_CELLS

static func _unique_leader(state: Dictionary) -> Variant:
	var ranked := Seq.stable_sort(state["players"], func(a, b): return _progress_score(a) > _progress_score(b))
	if ranked.is_empty():
		return null
	if ranked.size() > 1 and _progress_score(ranked[0]) == _progress_score(ranked[1]):
		return null
	return ranked[0]

static func _lead_margin(state: Dictionary, leader: Dictionary) -> int:
	var next = null
	for p in state["players"]:
		if p["playerId"] != leader["playerId"]:
			next = _progress_score(p) if next == null else maxi(next, _progress_score(p))
	return _progress_score(leader) - (0 if next == null else int(next))

static func _progress_score(player: Dictionary) -> int:
	var score := 0
	for position in player["tokens"]:
		var p := int(position)
		if p >= 0 and p <= 51:
			score += p + 1
		elif p >= 52 and p <= 56:
			score += 60 + (p - 52) * 8
		elif p == 57:
			score += 100
	return score

static func _latest_move_event(current: Dictionary, player_id, token: int) -> Variant:
	var history: Array = current["history"]
	for i in range(history.size() - 1, -1, -1):
		var e: Dictionary = history[i]
		if e["playerId"] == player_id and e.get("moveTokenIndex") != null and int(e["moveTokenIndex"]) == token:
			return e
	return null

static func _moment(current: Dictionary, type: String, player: Dictionary, event_index, token = null, related = null, value = null) -> Dictionary:
	return {"matchId": current["matchId"], "eventIndex": int(event_index), "type": type, "playerId": player["playerId"],
		"seat": int(player["seat"]), "tokenIndex": token, "relatedPlayerId": related, "value": value}
