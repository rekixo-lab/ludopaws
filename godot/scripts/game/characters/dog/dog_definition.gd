@tool
extends RefCounted
## Port of DogDefinition.kt (presentation metadata; pricing/ownership live elsewhere).

const Motion = preload("res://scripts/game/characters/dog/dog_motion.gd")
const Renderer = preload("res://scripts/game/characters/dog/dog_renderer.gd")
const Avatar = preload("res://scripts/game/characters/dog/dog_avatar.gd")

const ID := "dog"
const IDLE_DURATION_MS := Motion.IDLE_DURATION_MS
const HOME_DURATION_MS := Motion.HOME_DURATION_MS
const GROUND_PIVOT_Y := -0.84
const ROOT_Y_OFFSET_PER_RADIUS := 0.01
const FOOT_BASELINE_PER_RADIUS := 1.19
const SILHOUETTE_HEIGHT := 3.0

static func reaction(cue: String, frame: Dictionary) -> Dictionary:
	return Motion.reaction(cue, frame)

static func reaction_duration_scale() -> float:
	return Motion.REACTION_DURATION_SCALE
