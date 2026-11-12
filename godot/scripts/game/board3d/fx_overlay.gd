extends RefCounted
## Board FX timeline: paw trails behind moves, the captured pawn's ghost run home, home
## stars, and per-reaction bursts/rings. Presentation-only; never touches game state.
## Port of LudoPawsGameFxOverlayView.kt, driven by the clock instead of ValueAnimators.

const Painter = preload("res://scripts/game/board3d/fx_painter.gd")
const FxPolicy = preload("res://scripts/game/reactions/fx_policy.gd")
const MotionPolicy = preload("res://scripts/game/board3d/pawn_motion_policy.gd")
const Geo = preload("res://scripts/game/board3d/board_geometry.gd")

const MAX_SIMULTANEOUS_REACTIONS := 6
const MAX_TRANSITIONS := 8
const TRANSITION_DURATION_MS := 720
const Pacing = preload("res://scripts/game/gameplay_pacing.gd")

var painter := Painter.new()
var speed := Pacing.NORMAL           # Settings > Game speed
var snapshot = null
var transitions: Array = []          # non-capture token changes
var capture_transitions: Array = []  # track -> yard
var forward = null                   # first FORWARD plan of the last bind
var transition_anim = null           # {start, duration} or null (progress 1)
var capture_anim = null              # {start, duration} or null (not visible)
var capture_contact_delay := 0
var reactions: Array = []
var reaction_anim = null             # {start, duration}

## LudoPawsGameFxOverlayView.bind: called for every new snapshot.
func bind(previous, current, now: int) -> void:
	snapshot = current
	var detected := _detect(previous, current)
	transitions = detected.filter(func(t): return not t["capture"])
	capture_transitions = detected.filter(func(t): return t["capture"])
	forward = _first_forward(previous, current)
	var movement := Pacing.forward_travel_duration_ms(speed, int(forward["steps"])) if forward != null else 0
	capture_contact_delay = movement
	transition_anim = null if transitions.is_empty() else {"start": now, "duration": maxi(movement if forward != null else TRANSITION_DURATION_MS, 1)}
	capture_anim = null if capture_transitions.is_empty() else {"start": now + capture_contact_delay, "duration": Pacing.capture_return_duration_ms(speed)}

## LudoPawsGameFxOverlayView.play: replaces the running reaction FX.
func play(list: Array, now: int) -> void:
	reactions = list.slice(0, MAX_SIMULTANEOUS_REACTIONS)
	if reactions.is_empty():
		reaction_anim = null
		return
	var duration := 0
	for r in reactions:
		duration = maxi(duration, int(FxPolicy.plan(r["animationCue"])["durationMs"]))
	# Move-made reactions wait for the pawn to arrive so FX never preview the destination.
	reaction_anim = {"start": now + (capture_contact_delay if forward != null else 0), "duration": duration}

func stop() -> void:
	snapshot = null
	transitions = []
	capture_transitions = []
	forward = null
	transition_anim = null
	capture_anim = null
	capture_contact_delay = 0
	reactions = []
	reaction_anim = null

## True while any effect still changes over time (for render pacing).
func animating(now: int) -> bool:
	for anim in [transition_anim, capture_anim, reaction_anim]:
		if anim != null and now < int(anim["start"]) + int(anim["duration"]):
			return true
	return false

## Draw commands for this frame in board pixels (cell = board edge / 15), before rotation.
func commands(now: int, cell: float, turns: int, reduced_motion: bool) -> Array:
	painter.commands = []
	if snapshot == null or cell <= 0.0:
		return painter.commands
	var transition_progress := _progress(transition_anim, now, 1.0)
	var capture_progress := _progress(capture_anim, now, -1.0) if capture_anim != null else -1.0
	if capture_anim != null and now >= int(capture_anim["start"]) + int(capture_anim["duration"]):
		capture_progress = 1.0
	for t in transitions:
		_draw_transition(t, cell, turns, transition_progress)
	if capture_progress >= 0.0:
		for t in capture_transitions:
			_draw_transition(t, cell, turns, capture_progress)
	if transitions.any(func(t): return t["home"]):
		painter.draw_home_stars(cell, transition_progress)
	if reaction_anim != null:
		var start := int(reaction_anim["start"])
		if now >= start + int(reaction_anim["duration"]):
			reactions = []
			reaction_anim = null
		elif now >= start:
			var p := float(now - start) / float(reaction_anim["duration"])
			for r in reactions:
				_draw_reaction(r, cell, turns, p, reduced_motion)
	return painter.commands

func _progress(anim, now: int, idle: float) -> float:
	if anim == null:
		return idle
	var start := int(anim["start"])
	if now < start:
		return -1.0
	return clampf(float(now - start) / float(anim["duration"]), 0.0, 1.0)

func _draw_transition(t: Dictionary, cell: float, turns: int, progress: float) -> void:
	var from = Geo.token_center(t["color"], t["token"], t["from"], cell, turns)
	var to = Geo.token_center(t["color"], t["token"], t["to"], cell, turns)
	if from == null or to == null:
		return
	var color := Painter.player_color(t["color"])
	if t["capture"]:
		painter.draw_capture_return(from, to, color, cell, progress)
	else:
		painter.draw_paw_trail(from, to, color, cell, progress, 1.0 if t["home"] else 0.55)

func _draw_reaction(r: Dictionary, cell: float, turns: int, progress: float, reduced_motion: bool) -> void:
	var player = null
	for p in snapshot["players"]:
		if p["playerId"] == r["playerId"]:
			player = p
	if player == null:
		return
	var tokens: Array = player["tokens"]
	var token = r.get("tokenIndex")
	if token == null:
		token = 0
		for i in tokens.size():
			if int(tokens[i]) >= 0:
				token = i
				break
	if int(token) >= tokens.size():
		return
	var center = Geo.token_center(player["color"], int(token), int(tokens[int(token)]), cell, turns)
	if center == null or reduced_motion:
		return
	var plan: Dictionary = FxPolicy.plan(r["animationCue"])
	painter.draw_reaction(center, r["animationCue"], cell, progress, plan["particleCount"], plan["allowConfetti"])

func _detect(previous, current) -> Array:
	var out: Array = []
	if previous == null or current == null or previous["matchId"] != current["matchId"]:
		return out
	var before := {}
	for p in previous["players"]:
		before[p["playerId"]] = p
	for player in current["players"]:
		if not before.has(player["playerId"]):
			continue
		var old_tokens: Array = before[player["playerId"]]["tokens"]
		var tokens: Array = player["tokens"]
		for i in tokens.size():
			if i >= old_tokens.size():
				continue
			var from := int(old_tokens[i])
			var to := int(tokens[i])
			if from != to:
				out.append({"playerId": player["playerId"], "color": player["color"], "token": i, "from": from, "to": to,
					"capture": from >= 0 and to == -1, "home": from != 57 and to == 57})
	return out.slice(0, MAX_TRANSITIONS)

## LudoPawsPawnAnimationPolicy.plans(...).firstOrNull { FORWARD }.
func _first_forward(previous, current):
	if previous == null or current == null or previous["matchId"] != current["matchId"]:
		return null
	for player in current["players"]:
		var old = null
		for p in previous["players"]:
			if p["playerId"] == player["playerId"]:
				old = p
				break
		if old == null:
			continue
		var tokens: Array = player["tokens"]
		for i in tokens.size():
			if i >= old["tokens"].size():
				continue
			var plan = MotionPolicy.transition(int(old["tokens"][i]), int(tokens[i]))
			if plan != null and plan["kind"] == "forward":
				return plan
	return null
