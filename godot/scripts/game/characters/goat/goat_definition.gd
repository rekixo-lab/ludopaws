@tool
extends RefCounted
## Port of GoatDefinition.kt (presentation metadata).

const Motion = preload("res://scripts/game/characters/goat/goat_motion.gd")
const Renderer = preload("res://scripts/game/characters/goat/goat_renderer.gd")
const Avatar = preload("res://scripts/game/characters/goat/goat_avatar.gd")

const ID := "goat"
const IDLE_DURATION_MS := Motion.IDLE_DURATION_MS
const HOME_DURATION_MS := Motion.HOME_DURATION_MS
const GROUND_PIVOT_Y := -0.954
const ROOT_Y_OFFSET_PER_RADIUS := 0.02
const FOOT_BASELINE_PER_RADIUS := 1.17
const SILHOUETTE_HEIGHT := 3.15

static func reaction(cue: String, frame: Dictionary) -> Dictionary:
	return Motion.reaction(cue, frame)

static func reaction_duration_scale() -> float:
	return Motion.REACTION_DURATION_SCALE
