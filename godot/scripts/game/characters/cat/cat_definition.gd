@tool
extends RefCounted
## Port of CatDefinition.kt (presentation metadata).

const Motion = preload("res://scripts/game/characters/cat/cat_motion.gd")
const Renderer = preload("res://scripts/game/characters/cat/cat_renderer.gd")
const Avatar = preload("res://scripts/game/characters/cat/cat_avatar.gd")

const ID := "cat"
const IDLE_DURATION_MS := Motion.IDLE_DURATION_MS
const HOME_DURATION_MS := Motion.HOME_DURATION_MS
const MODEL_SIZE_MULTIPLIER := 1.375
## Anchored leg shortening keeps the paws on their board square.
const GROUND_PIVOT_Y := (0.22 + (-0.84 - 0.22) * 0.5) * MODEL_SIZE_MULTIPLIER
const ROOT_Y_OFFSET_PER_RADIUS := 0.02
const FOOT_BASELINE_PER_RADIUS := 1.18
const SILHOUETTE_HEIGHT := 2.10 * MODEL_SIZE_MULTIPLIER

static func reaction(cue: String, frame: Dictionary) -> Dictionary:
	return Motion.reaction(cue, frame)

static func reaction_duration_scale() -> float:
	return Motion.REACTION_DURATION_SCALE
