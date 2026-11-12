@tool
extends RefCounted
## Presentation adapter from snapshot moments to character intent (voice cue, animation
## cue, priority). Port of LudoPawsReactionEngine.kt.
##
## Reaction = {playerId, seat, tokenIndex, voiceCue, animationCue, priority, matchId,
##   eventIndex, momentType, reactionKey}

const Detector = preload("res://scripts/game/reactions/moment_detector.gd")
const Director = preload("res://scripts/game/reactions/reaction_director.gd")
const Seq = preload("res://scripts/game/reactions/seq.gd")

## moment type -> [voice cue, animation cue, priority]; types without an entry produce no reaction.
const MAPPING := {
	"TURN_STARTED": ["SILENT", "IDLE", 12],
	"SIX_ROLLED": ["SIX", "EXCITED", 66],
	"TOKEN_LEFT_YARD": ["YARD_EXIT", "HAPPY", 44],
	"ONLY_LEGAL_MOVE": ["SILENT", "HAPPY", 14],
	"THIRD_SIX_FORFEIT": ["THIRD_SIX", "ANGRY", 92],
	"CAPTURE_MADE": ["CAPTURE", "CAPTURE", 88],
	"TOKEN_CAPTURED": ["CAPTURED", "CAPTURED", 86],
	"SAFE_REACHED": ["SAFE", "SAFE", 58],
	"HOME_LANE_ENTERED": ["HOME_LANE", "EXCITED", 62],
	"HOME_REACHED": ["HOME", "HOME", 82],
	"POOR_ROLL_STREAK": ["FRUSTRATED", "SAD", 52],
	"EXACT_HOME_MISS": ["FRUSTRATED", "SAD", 50],
	"NO_LEGAL_MOVE": ["FRUSTRATED", "SAD", 46],
	"TOKEN_THREATENED": ["NERVOUS", "NERVOUS", 40],
	"PLAYER_LEADING": ["PROUD", "HAPPY", 26],
	"MATCH_WIN": ["VICTORY", "VICTORY", 100],
	"TEAM_WIN": ["VICTORY", "VICTORY", 100],
	"MATCH_LOSS": ["DEFEAT", "DEFEAT", 30],
	"TEAM_LOSS": ["DEFEAT", "DEFEAT", 30],
	"IDLE_WAITING": ["IDLE", "IDLE", 20],
}

## The live engine keeps one process-wide playback director.
static var _director: Director = Director.new()

## Raw reactions, then the shared director's decision (what plays now).
static func detect(previous, current, now_ms: int) -> Array:
	return _director.submit(derive(previous, current), now_ms)["reactions"]

static func derive(previous, current) -> Array:
	var mapped: Array = []
	for moment in Detector.detect(previous, current):
		var r = _to_reaction(moment)
		if r != null:
			mapped.append(r)
	var aligned := _align_capture_pair(mapped)
	var seen := {}
	var unique: Array = []
	for r in aligned:
		if not seen.has(r["reactionKey"]):
			seen[r["reactionKey"]] = true
			unique.append(r)
	return Seq.stable_sort(unique, func(a, b): return a["priority"] > b["priority"])

static func derive_idle(current, now_ms: int, last_change_ms: int, threshold_ms := 24000) -> Array:
	var moment = Detector.idle_moment(current, now_ms, last_change_ms, threshold_ms)
	if moment == null:
		return []
	var r = _to_reaction(moment)
	return [] if r == null else [r]

static func reset_playback_state() -> void:
	_director.clear()

static func _align_capture_pair(reactions: Array) -> Array:
	var captures := reactions.filter(func(r): return r["momentType"] == "CAPTURE_MADE")
	if captures.size() != 1:
		return reactions
	var attacker: Dictionary = captures[0]
	var out: Array = []
	for r in reactions:
		if r["momentType"] == "TOKEN_CAPTURED" and r["matchId"] == attacker["matchId"]:
			var copy: Dictionary = r.duplicate()
			copy["eventIndex"] = attacker["eventIndex"]
			copy["reactionKey"] = stable_key(r["matchId"], attacker["eventIndex"], r["momentType"], r["playerId"], r["tokenIndex"])
			out.append(copy)
		else:
			out.append(r)
	return out

static func _to_reaction(moment: Dictionary) -> Variant:
	if not MAPPING.has(moment["type"]):
		return null
	var m: Array = MAPPING[moment["type"]]
	return {"playerId": moment["playerId"], "seat": moment["seat"], "tokenIndex": moment["tokenIndex"],
		"voiceCue": m[0], "animationCue": m[1], "priority": m[2], "matchId": moment["matchId"],
		"eventIndex": moment["eventIndex"], "momentType": moment["type"],
		"reactionKey": stable_key(moment["matchId"], moment["eventIndex"], moment["type"], moment["playerId"], moment["tokenIndex"])}

static func stable_key(match_id: String, event_index: int, type: String, player_id: String, token) -> String:
	return ":".join([match_id, str(event_index), type, player_id, "" if token == null else str(token)])
