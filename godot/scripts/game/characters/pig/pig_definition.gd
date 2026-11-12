@tool
extends RefCounted
## Port of PigDefinition.kt (presentation metadata).

const Motion = preload("res://scripts/game/characters/pig/pig_motion.gd")
const Renderer = preload("res://scripts/game/characters/pig/pig_renderer.gd")
const Avatar = preload("res://scripts/game/characters/pig/pig_avatar.gd")

const ID := "pig"
const IDLE_DURATION_MS := 2600
const HOME_DURATION_MS := 1450
const GROUND_PIVOT_Y := -0.76
const ROOT_Y_OFFSET_PER_RADIUS := 0.08
const FOOT_BASELINE_PER_RADIUS := 1.02
const SILHOUETTE_HEIGHT := 2.20

static func reaction(cue: String, frame: Dictionary) -> Dictionary:
	return Motion.reaction(cue, frame)

static func reaction_duration_scale() -> float:
	return Motion.REACTION_DURATION_SCALE
