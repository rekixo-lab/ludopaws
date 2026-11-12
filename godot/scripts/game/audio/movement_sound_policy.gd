extends RefCounted
## Which physical sound belongs to a completed visual step of a forward move (the movement
## animation owns the timing). Port of LudoPawsMovementSoundPolicy.kt.

const MotionPolicy = preload("res://scripts/game/board3d/pawn_motion_policy.gd")

const STEP := "STEP"
const YARD_EXIT := "YARD_EXIT"
const SAFE_RELIEF := "SAFE_RELIEF"
const HOME_LANE := "HOME_LANE"
const HOME := "HOME"

const SAFE_GLOBAL_CELLS := [0, 8, 13, 21, 26, 34, 39, 47]
const START_OFFSETS := {"RED": 0, "GREEN": 13, "YELLOW": 26, "BLUE": 39}

static func cues_for_visual_step(color, from: int, step: int) -> Array:
	if step <= 0:
		return []
	var destination = MotionPolicy.position_at_visual_step(from, step)
	if destination == null:
		return []
	# Leaving the yard is its own physical action, not stacked with a step tick.
	if from == -1 and step == 1 and destination == 0:
		return [YARD_EXIT]
	var cues := [STEP]
	if destination >= 52 and destination <= 56:
		var previous = MotionPolicy.position_at_visual_step(from, step - 1)
		if previous == null or previous < 52 or previous > 56:
			cues.append(HOME_LANE)
	if destination == 57:
		cues.append(HOME)
	if _safe(color, destination):
		cues.append(SAFE_RELIEF)
	return cues

static func _safe(color, position: int) -> bool:
	if color == null or position < 0 or position > 50 or not START_OFFSETS.has(color):
		return false
	return (START_OFFSETS[color] + position) % 52 in SAFE_GLOBAL_CELLS
