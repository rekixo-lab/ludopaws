@tool
extends RefCounted
## Pure timing/key policy for idle character reactions. The board owns the clock; this only
## says whether visible state meaningfully changed and when one idle reaction is eligible.
## Port of LudoPawsIdleReactionPolicy.kt.

const IDLE_THRESHOLD_MS := 24000

static func meaningful_state_key(state) -> Variant:
	if state == null:
		return null
	var pending = state.get("pendingRoll")
	var parts := [state["matchId"], state["status"], str(state["turnSeat"]),
		str(state["actingSeat"] if state.get("actingSeat") != null else -1),
		str(state["randomEventIndex"]),
		str(pending["eventIndex"] if pending != null else -1),
		pending["status"] if pending != null else "",
		str(pending["outcome"] if pending != null and pending.get("outcome") != null else -1)]
	var players: Array = (state["players"] as Array).duplicate()
	players.sort_custom(func(a, b): return int(a["seat"]) < int(b["seat"]))
	var roster := ""
	for p in players:
		roster += "%s:%s;" % [p["playerId"], ",".join((p["tokens"] as Array).map(func(t): return str(t)))]
	parts.append(roster)
	parts.append(str(state["winnerPlayerId"]) if state.get("winnerPlayerId") != null else "")
	parts.append(str(state["winnerTeamId"]) if state.get("winnerTeamId") != null else "")
	return "|".join(parts)

static func idle_reaction_key(state) -> Variant:
	if state == null or state["status"] != "ACTIVE":
		return null
	var seat := int(state["actingSeat"]) if state.get("actingSeat") != null else int(state["turnSeat"])
	var players: Array = state["players"]
	if seat < 0 or seat >= players.size():
		return null
	return ":".join([state["matchId"], str(state["randomEventIndex"]), str(seat), players[seat]["playerId"]])

static func delay_until_eligible_ms(state, last_change_ms: int, now_ms: int, threshold_ms := IDLE_THRESHOLD_MS) -> Variant:
	if state == null or state["status"] != "ACTIVE" or threshold_ms <= 0:
		return null
	var elapsed := maxi(0, now_ms - last_change_ms)
	return maxi(0, threshold_ms - elapsed)
