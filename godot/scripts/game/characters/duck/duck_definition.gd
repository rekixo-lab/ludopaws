@tool
extends RefCounted
## Port of DuckDefinition.kt (presentation metadata).

const Motion = preload("res://scripts/game/characters/duck/duck_motion.gd")
const Renderer = preload("res://scripts/game/characters/duck/duck_renderer.gd")
const Avatar = preload("res://scripts/game/characters/duck/duck_avatar.gd")

const ID := "duck"
const IDLE_DURATION_MS := Motion.IDLE_DURATION_MS
const HOME_DURATION_MS := Motion.HOME_DURATION_MS
const GROUND_PIVOT_Y := -0.755
const ROOT_Y_OFFSET_PER_RADIUS := 0.14
const FOOT_BASELINE_PER_RADIUS := 0.99
const SILHOUETTE_HEIGHT := 2.85

static func reaction(cue: String, frame: Dictionary) -> Dictionary:
	return Motion.reaction(cue, frame)

static func reaction_duration_scale() -> float:
	return Motion.REACTION_DURATION_SCALE
