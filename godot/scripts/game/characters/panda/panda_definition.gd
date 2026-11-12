@tool
extends RefCounted
## Port of PandaDefinition.kt (presentation metadata).

const Motion = preload("res://scripts/game/characters/panda/panda_motion.gd")
const Renderer = preload("res://scripts/game/characters/panda/panda_renderer.gd")
const Avatar = preload("res://scripts/game/characters/panda/panda_avatar.gd")

const ID := "panda"
const IDLE_DURATION_MS := 3200
const HOME_DURATION_MS := 1650
const MODEL_SIZE_MULTIPLIER := 1.20
const GROUND_PIVOT_Y := -0.72 * MODEL_SIZE_MULTIPLIER
const ROOT_Y_OFFSET_PER_RADIUS := 0.06
const FOOT_BASELINE_PER_RADIUS := 1.02
const SILHOUETTE_HEIGHT := 3.0

static func reaction(cue: String, frame: Dictionary) -> Dictionary:
	return Motion.reaction(cue, frame)

static func reaction_duration_scale() -> float:
	return Motion.REACTION_DURATION_SCALE
