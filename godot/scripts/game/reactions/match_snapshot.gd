@tool
extends RefCounted
## Builds the live MatchSnapshot shape (GameModels.kt) that the reaction pipeline reads,
## from a local match. Mirrors OfflineGameEngine.LocalState.toSnapshot: one history event
## per roll (eventIndex = roll counter, outcome = effective roll), filled with
## moveTokenIndex/captures when that roll is moved; a pending roll is always RESOLVED and
## carries the roll's eventIndex (randomEventIndex - 1).
##
## Snapshot = {matchId, status, players: [{playerId, color, seat, tokens, teamId}], turnSeat,
##   actingSeat, randomEventIndex, pendingRoll: null | {status, seat, eventIndex, outcome,
##   legalTokenIndexes}, winnerPlayerId, winnerTeamId, history: [{eventIndex, playerId,
##   outcome, moveTokenIndex, captures}], revision}

const Rules = preload("res://scripts/game/rules/ludo_engine.gd")

## OfflineLudoV6Binding.HISTORY_WINDOW
const HISTORY_WINDOW := 100

static func from_state(state: Dictionary) -> Dictionary:
	var players: Array = []
	var source: Array = state.get("players", [])
	for seat in source.size():
		var p: Dictionary = source[seat]
		var tokens: Array = []
		for t in p["tokens"]:
			tokens.append(int(t))
		players.append({"playerId": str(p["playerId"]), "color": str(p["color"]), "seat": seat,
			"tokens": tokens, "teamId": p.get("teamId")})
	var event_index := int(state.get("randomEventIndex", 0))
	var pending = state.get("pendingRoll")
	var pending_snapshot = null
	if pending is Dictionary and pending.get("status") == "RESOLVED":
		var legal: Array = []
		for index in pending.get("legalTokenIndexes", []):
			legal.append(int(index))
		pending_snapshot = {"status": "RESOLVED", "seat": int(pending["seat"]), "eventIndex": event_index - 1,
			"outcome": int(pending["outcome"]), "legalTokenIndexes": legal}
	var turn := int(state.get("turnSeat", 0))
	var acting := Rules.acting_seat(state) if state.get("status") == "ACTIVE" else turn
	return {
		"matchId": str(state.get("matchId", "")),
		"status": str(state.get("status", "ACTIVE")),
		"players": players,
		"turnSeat": turn,
		# Offline snapshots carry no actingSeat; a Team Up teammate acting for a finished
		# partner is reported like the live server does.
		"actingSeat": acting if acting != turn else null,
		"randomEventIndex": event_index,
		"pendingRoll": pending_snapshot,
		"winnerPlayerId": state.get("winnerPlayerId"),
		"winnerTeamId": state.get("winnerTeamId"),
		"history": (state.get("history", []) as Array).duplicate(true),
		"revision": int(state.get("revision", 0)),
	}

## Record a committed roll on `next` (the engine's new state). `seat` rolled `outcome`
## (effective value, as offline history stores it).
static func record_roll(next: Dictionary, seat: int, outcome: int) -> void:
	var event := int(next.get("randomEventIndex", 0))
	var history: Array = next.get("history", [])
	history.append({"eventIndex": event, "playerId": str(next["players"][seat]["playerId"]),
		"outcome": outcome, "moveTokenIndex": null, "captures": 0})
	if history.size() > HISTORY_WINDOW:
		history = history.slice(history.size() - HISTORY_WINDOW)
	next["history"] = history
	next["randomEventIndex"] = event + 1

## Record the move that used the pending roll.
static func record_move(next: Dictionary, token: int, captures: int) -> void:
	var event := int(next.get("randomEventIndex", 0)) - 1
	var history: Array = next.get("history", [])
	for i in range(history.size() - 1, -1, -1):
		if int(history[i]["eventIndex"]) == event:
			history[i]["moveTokenIndex"] = token
			history[i]["captures"] = captures
			break
	next["history"] = history
