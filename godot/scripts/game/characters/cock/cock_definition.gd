@tool
extends RefCounted
## Port of CockDefinition.kt (presentation metadata).

const Motion = preload("res://scripts/game/characters/cock/cock_motion.gd")
const Renderer = preload("res://scripts/game/characters/cock/cock_renderer.gd")
const Avatar = preload("res://scripts/game/characters/cock/cock_avatar.gd")

const ID := "cock"
const IDLE_DURATION_MS := 2400
const HOME_DURATION_MS := 1350
const GROUND_PIVOT_Y := -0.78
const ROOT_Y_OFFSET_PER_RADIUS := 0.14
const FOOT_BASELINE_PER_RADIUS := 0.99
const SILHOUETTE_HEIGHT := 2.22

static func reaction(cue: String, frame: Dictionary) -> Dictionary:
	return Motion.reaction(cue, frame)

static func reaction_duration_scale() -> float:
	return Motion.REACTION_DURATION_SCALE
