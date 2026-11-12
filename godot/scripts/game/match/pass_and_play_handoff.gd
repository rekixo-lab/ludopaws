extends RefCounted
## OfflinePassAndPlayHandoffPolicy.resolve (live, presentation only): a handoff only for a fresh
## ACTIVE -> ACTIVE change of turn seat in Pass & Play with no pending roll. First render,
## resume, extra turns, Computer mode and finished matches never prompt.

const COLOUR_NAMES := {"RED": "Red", "GREEN": "Green", "YELLOW": "Yellow", "BLUE": "Blue"}

## previous / current: match snapshots ({matchId, status, turnSeat, pendingRoll, players}).
## Returns {seat, name, color} or null.
static func resolve(mode: String, previous, current: Dictionary) -> Variant:
	if mode != "PASS_AND_PLAY" or previous == null:
		return null
	if previous.get("matchId") != current.get("matchId"):
		return null
	if previous.get("status") != "ACTIVE" or current.get("status") != "ACTIVE":
		return null
	if previous.get("turnSeat") == current.get("turnSeat"):
		return null
	if current.get("pendingRoll") != null:
		return null
	var players: Array = current.get("players", [])
	var seat := int(current.get("turnSeat", -1))
	if seat < 0 or seat >= players.size():
		return null
	var p: Dictionary = players[seat]
	return {"seat": seat, "name": str(p.get("displayName", "")), "color": str(p.get("color", ""))}

## "<Colour> paws • your turn"
static func subtitle(color: String) -> String:
	return "%s paws • your turn" % COLOUR_NAMES.get(color, color.capitalize())
