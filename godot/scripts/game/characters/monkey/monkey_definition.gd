@tool
extends RefCounted
## Port of MonkeyDefinition.kt (presentation metadata).

const Motion = preload("res://scripts/game/characters/monkey/monkey_motion.gd")
const Renderer = preload("res://scripts/game/characters/monkey/monkey_renderer.gd")
const Avatar = preload("res://scripts/game/characters/monkey/monkey_avatar.gd")

const ID := "monkey"
const IDLE_DURATION_MS := 2800
const HOME_DURATION_MS := 1450
const MODEL_SIZE_MULTIPLIER := 1.26
const GROUND_PIVOT_Y := -0.72 * MODEL_SIZE_MULTIPLIER
const ROOT_Y_OFFSET_PER_RADIUS := 0.06
const FOOT_BASELINE_PER_RADIUS := 1.02
const SILHOUETTE_HEIGHT := 3.0

static func reaction(cue: String, frame: Dictionary) -> Dictionary:
	return Motion.reaction(cue, frame)

static func reaction_duration_scale() -> float:
	return Motion.REACTION_DURATION_SCALE
