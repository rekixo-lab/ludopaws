extends RefCounted
## Sound for an already-committed local roll or move, exactly once per action (ledger).
## Physical arrivals (yard exit, capture, safe, home lane, home) are NOT played here: the
## board's movement clock plays them when the pawn actually arrives.
## Port of OfflineLudoPawsFeedbackPolicy and OfflineLudoPawsFeedbackDispatcher.

const Reactions = preload("res://scripts/game/reactions/reaction_engine.gd")
const Ledger = preload("res://scripts/game/reactions/feedback_ledger.gd")

const ROLL := "ROLL"
const MOVE := "MOVE"

## Dedicated sound for the highest-priority derived reaction ("" = none).
static func decide_sound(reactions: Array) -> String:
	if reactions.is_empty():
		return ""
	var highest: Dictionary = reactions[0]
	for r in reactions:
		if int(r["priority"]) > int(highest["priority"]):
			highest = r
	match highest.get("momentType"):
		"SIX_ROLLED": return "six"
		"POOR_ROLL_STREAK", "NO_LEGAL_MOVE": return "frustrated"
		"EXACT_HOME_MISS": return "exact_home_miss"
		"THIRD_SIX_FORFEIT": return "third_six"
		"MATCH_WIN", "TEAM_WIN": return "victory"
		"MATCH_LOSS", "TEAM_LOSS": return "defeat"
	return ""

## committedActionKey: ROLL:<event> / MOVE:<event> (null = nothing to key on).
static func committed_action_key(current: Dictionary, action: String) -> Variant:
	var history: Array = current.get("history", [])
	var pending = current.get("pendingRoll")
	var event = null
	if action == ROLL:
		if pending != null:
			event = pending["eventIndex"]
		elif not history.is_empty():
			event = history[-1]["eventIndex"]
		elif int(current.get("randomEventIndex", 0)) - 1 >= 0:
			event = int(current["randomEventIndex"]) - 1
	else:
		for i in range(history.size() - 1, -1, -1):
			if history[i].get("moveTokenIndex") != null:
				event = history[i]["eventIndex"]
				break
		if event == null and not history.is_empty():
			event = history[-1]["eventIndex"]
		if event == null and pending != null:
			event = pending["eventIndex"]
	return null if event == null else "%s:%d" % [action, int(event)]

## Plays the committed action's sounds on `audio` (GameAudio) once. Returns the sound name.
static func committed(audio, ledger: Ledger, previous, current: Dictionary, action: String) -> String:
	var probe_t := Time.get_ticks_usec()
	var probe_r = _probed_committed(audio, ledger, previous, current, action)
	preload("res://scripts/game/perf.gd").span("feedback", Time.get_ticks_usec() - probe_t)
	return probe_r

static func _probed_committed(audio, ledger: Ledger, previous, current: Dictionary, action: String) -> String:
	var key = committed_action_key(current, action)
	if key == null or not ledger.once(str(current["matchId"]), "OFFLINE:" + key):
		return ""
	var sound := decide_sound(Reactions.derive(previous, current))
	if audio == null:
		return sound
	# Rattle plays at roll start; the short contact tick lands with the committed face.
	if action == ROLL:
		audio.dice_settle()
	if not sound.is_empty():
		audio.call(sound)
	return sound
