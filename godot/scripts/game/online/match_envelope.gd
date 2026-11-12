extends RefCounted
## Port of live GameJson.envelope + validateAuthoritativeMatch / validatePendingRoll: every
## server match snapshot is checked strictly before anything is shown or acted on (schema,
## ids, seats, colours, tokens, ruleset per mode, winner / finish order, pending roll and
## history bounds). A snapshot that fails is never rendered.
##   var env := MatchEnvelope.parse(response)   # {ok, playerId, state} | {ok: false, error}

const R = preload("res://scripts/game/rules/ludo_rulesets.gd")

const COLORS := ["RED", "GREEN", "YELLOW", "BLUE"]
const TEAMS := ["A", "B", "A", "B"]
const STATUSES := ["WAITING", "ACTIVE", "FINISHED"]
const MODES := ["ONLINE", "FRIENDS", "TEAM_UP"]
const PENDING := ["CREATING", "COMMITTED", "RESOLVING", "RESOLVED"]
const CLASSIC_IDS := ["ludoproof-standard-v1", "ludoproof-standard-v2", "ludoproof-standard-v3", "ludoproof-standard-v4",
	"ludoproof-standard-v5", "ludoproof-standard-v6", "ludoproof-standard-v7", "ludoproof-standard-v8"]
const TEAM_IDS := ["ludoproof-team-v1", "ludoproof-team-v2", "ludoproof-team-v3", "ludoproof-team-v4",
	"ludoproof-team-v5", "ludoproof-team-v6", "ludoproof-team-v7"]
## LudoComebackPolicy.enabled.
const COMEBACK_IDS := ["ludoproof-standard-v7", "ludoproof-standard-v8", "ludoproof-team-v6", "ludoproof-team-v7", "ludopaws-quick-two-v1"]

## GameJson.envelope: {ok, playerId, state (validated) or null} | {ok: false, error}.
static func parse(response: Dictionary) -> Dictionary:
	var state = response.get("state")
	if not (state is Dictionary):
		state = response if response.has("matchId") and response.has("players") else null
	var player_id = str(response.get("playerId", "")).strip_edges()
	if state != null:
		var error := validate(state)
		if not error.is_empty():
			return {"ok": false, "error": error}
	return {"ok": true, "playerId": player_id if not player_id.is_empty() else null, "state": state}

static func _int(v) -> bool:
	return (v is int) or (v is float and v == floor(v))

static func _str(v) -> bool:
	return v is String

## "" when valid, else the first problem (live GameSchemaException message).
static func validate(v: Dictionary) -> String:
	if not _int(v.get("schemaVersion")) or int(v["schemaVersion"]) != 1:
		return "unsupported match schemaVersion"
	if not _str(v.get("matchId")) or RegEx.create_from_string("^LP[A-Z2-9]{8}$").search(v["matchId"]) == null:
		return "invalid matchId"
	var status = v.get("status")
	if not STATUSES.has(status):
		return "invalid match status"
	if not _str(v.get("hostPlayerId")):
		return "missing hostPlayerId"
	var mode = v.get("matchMode")
	if not MODES.has(mode):
		return "invalid matchMode"
	if not v.has("targetPlayerCount"):
		return "missing targetPlayerCount"
	var target = v["targetPlayerCount"]
	if target != null:
		if not _int(target) or int(target) < 2 or int(target) > 4:
			return "invalid targetPlayerCount"
		target = int(target)
	if mode == "FRIENDS" and target == null:
		return "friend room requires targetPlayerCount"
	if mode == "TEAM_UP" and target != 4:
		return "Team Up requires targetPlayerCount 4"
	var ruleset = v.get("rulesetId")
	if not (TEAM_IDS if mode == "TEAM_UP" else CLASSIC_IDS).has(ruleset):
		return "rulesetId does not match matchMode"
	if not v.has("revision") or not _int(v["revision"]) or int(v["revision"]) < 0:
		return "invalid revision"
	if not _int(v.get("randomEventIndex")) or int(v["randomEventIndex"]) < 0:
		return "invalid randomEventIndex"
	var random_index := int(v["randomEventIndex"])
	if not v.has("turnSeat"):
		return "missing turnSeat"
	var turn = v["turnSeat"]
	if turn == null:
		if status != "WAITING":
			return "turnSeat may be null only while waiting"
	elif not _int(turn):
		return "invalid turnSeat"
	var players = v.get("players")
	if not (players is Array) or players.size() < 1 or players.size() > 4:
		return "players must contain one to four entries"
	var n: int = players.size()
	if target != null and n > target:
		return "player count exceeds targetPlayerCount"
	if status != "WAITING":
		if n < 2:
			return "active/finished match requires at least two players"
		if turn == null or int(turn) < 0 or int(turn) >= n:
			return "turnSeat is outside player seats"
	if status == "ACTIVE" and target != null and n != target:
		return "active match is missing required players"
	if mode == "TEAM_UP" and status != "WAITING" and n != 4:
		return "active/finished Team Up requires four players"
	if COMEBACK_IDS.has(ruleset):
		var waits = v.get("comebackWaits")
		if not (waits is Array) or waits.size() != n:
			return "comeback counter count does not match players"
		for w in waits:
			if not _int(w) or int(w) < -1 or int(w) > 2:
				return "invalid comeback counter"
	var ids := []
	for i in n:
		var p = players[i]
		if not (p is Dictionary) or not _str(p.get("playerId")):
			return "invalid player"
		if ids.has(p["playerId"]):
			return "duplicate playerId"
		ids.append(p["playerId"])
		if not _int(p.get("seat")) or int(p["seat"]) != i:
			return "player seat does not match array position"
		# Public two-player Online matches seat the opponent in the Yellow quadrant.
		var expected: String = "YELLOW" if mode == "ONLINE" and target == 2 and i == 1 else COLORS[i]
		if p.get("color") != expected:
			return "player color does not match seat"
		if p.has("playerType") and not ["HUMAN", "BOT"].has(p["playerType"]):
			return "invalid playerType"
		var tokens = p.get("tokens")
		if not (tokens is Array) or tokens.size() != 4:
			return "player must have exactly four tokens"
		for t in tokens:
			if not _int(t) or int(t) < -1 or int(t) > 57:
				return "token position is outside -1..57"
		if mode == "TEAM_UP" and p.get("teamId") != TEAMS[i]:
			return "player teamId does not match seat assignment"
	if not ids.has(v["hostPlayerId"]):
		return "hostPlayerId is not a seated player"
	if mode == "TEAM_UP":
		if v.get("teamAssignments") != TEAMS:
			return "invalid Team Up assignments"
		if not v.has("actingSeat"):
			return "Team Up snapshot is missing actingSeat"
		var acting = v["actingSeat"]
		if acting == null:
			if status != "WAITING":
				return "actingSeat may be null only while waiting"
		elif not _int(acting) or int(acting) < 0 or int(acting) >= n:
			return "actingSeat is outside player seats"
		if not v.has("winnerTeamId"):
			return "Team Up snapshot is missing winnerTeamId"
	if not v.has("winnerPlayerId"):
		return "missing winnerPlayerId"
	var winner = v["winnerPlayerId"] if _str(v["winnerPlayerId"]) and not str(v["winnerPlayerId"]).is_empty() else null
	var winner_team = v.get("winnerTeamId") if _str(v.get("winnerTeamId")) and not str(v.get("winnerTeamId")).is_empty() else null
	if winner != null and not ids.has(winner):
		return "winnerPlayerId is not a seated player"
	var order = v.get("finishOrderPlayerIds")
	if order != null:
		if not (order is Array):
			return "invalid finish order"
		var seen := []
		for id in order:
			if not _str(id) or seen.has(id) or not ids.has(id):
				return "finish order contains a duplicate or unknown player"
			seen.append(id)
		if winner != null and not seen.is_empty() and seen[0] != winner:
			return "finish order winner does not match winnerPlayerId"
	if winner_team != null and not ["A", "B"].has(winner_team):
		return "invalid winnerTeamId"
	if status == "FINISHED":
		if mode == "TEAM_UP":
			if winner != null:
				return "Team Up must not expose an individual winner"
			if winner_team == null:
				return "finished Team Up match requires winnerTeamId"
		else:
			if winner == null:
				return "finished classic match requires winnerPlayerId"
			if winner_team != null:
				return "classic match must not expose winnerTeamId"
	elif winner != null or winner_team != null:
		return "unfinished match must not expose a winner"
	if not v.has("pendingRoll"):
		return "missing pendingRoll"
	if v["pendingRoll"] != null:
		if status != "ACTIVE":
			return "pendingRoll is allowed only for an active match"
		if not (v["pendingRoll"] is Dictionary):
			return "pendingRoll must be an object or null"
		var error := _validate_pending(v["pendingRoll"], n, mode, random_index)
		if not error.is_empty():
			return error
	var history = v.get("history")
	if not (history is Array):
		return "missing history"
	var previous := -1
	for e in history:
		if not (e is Dictionary) or not _int(e.get("eventIndex")):
			return "invalid history event"
		var index := int(e["eventIndex"])
		if index < 0 or index <= previous or index >= random_index:
			return "history eventIndex out of order"
		previous = index
		if not ids.has(e.get("playerId")):
			return "history playerId is not seated"
		for key in ["outcome", "randomOutcome", "effectiveOutcome"]:
			if e.get(key) != null and (not _int(e[key]) or int(e[key]) < 1 or int(e[key]) > 6):
				return "history %s is outside 1..6" % key
		if e.get("moveTokenIndex") != null and (not _int(e["moveTokenIndex"]) or int(e["moveTokenIndex"]) < 0 or int(e["moveTokenIndex"]) > 3):
			return "history moveTokenIndex is outside 0..3"
		if e.get("captures") != null and (not _int(e["captures"]) or int(e["captures"]) < 0):
			return "history captures must be non-negative"
	return ""

static func _validate_pending(p: Dictionary, n: int, mode: String, random_index: int) -> String:
	var status = p.get("status")
	if not PENDING.has(status):
		return "invalid pending roll status"
	if not _int(p.get("seat")) or int(p["seat"]) < 0 or int(p["seat"]) >= n:
		return "pending roll seat is outside player seats"
	if not _int(p.get("eventIndex")) or int(p["eventIndex"]) < 0:
		return "pending roll eventIndex must be non-negative"
	var index := int(p["eventIndex"])
	if (status == "CREATING" and random_index != index) or (status != "CREATING" and random_index != index + 1):
		return "pending roll eventIndex drift"
	if p.get("eventId") != "roll:%d" % index:
		return "pending roll eventId mismatch"
	if mode == "TEAM_UP" and (not _int(p.get("scheduledSeat")) or int(p["scheduledSeat"]) < 0 or int(p["scheduledSeat"]) >= n):
		return "scheduledSeat is outside player seats"
	if not p.has("legalTokenIndexes"):
		return "pending roll is missing legalTokenIndexes"
	if p["legalTokenIndexes"] != null:
		if not (p["legalTokenIndexes"] is Array):
			return "invalid legalTokenIndexes"
		var seen := []
		for t in p["legalTokenIndexes"]:
			if not _int(t) or int(t) < 0 or int(t) > 3 or seen.has(int(t)):
				return "legal token indexes invalid"
			seen.append(int(t))
		if status == "RESOLVED" and seen.is_empty():
			return "resolved pending roll must contain a legal move"
	elif status == "RESOLVED":
		return "resolved pending roll requires legalTokenIndexes"
	if not p.has("outcome"):
		return "pending roll is missing outcome"
	if p["outcome"] != null:
		if not _int(p["outcome"]) or int(p["outcome"]) < 1 or int(p["outcome"]) > 6:
			return "pending roll outcome is outside 1..6"
	elif status == "RESOLVED":
		return "resolved pending roll requires outcome"
	if p.get("randomOutcome") != null and (not _int(p["randomOutcome"]) or int(p["randomOutcome"]) < 1 or int(p["randomOutcome"]) > 6):
		return "pending roll randomOutcome is outside 1..6"
	if status != "RESOLVED" or p.get("revealDeadlineAt") != null:
		if not _int(p.get("revealDeadlineAt")) or int(p["revealDeadlineAt"]) <= 0:
			return "invalid revealDeadlineAt"
	return ""

## The engine ruleset key for a server rulesetId (server snapshots carry only the id).
static func with_ruleset_key(state: Dictionary) -> Dictionary:
	var out := state.duplicate(true)
	if not out.has("rulesetKey"):
		for key in R.IDS:
			if R.IDS[key] == out.get("rulesetId"):
				out["rulesetKey"] = key
	return out
