@tool
extends RefCounted
## Port of HorseDefinition.kt (presentation metadata).

const Motion = preload("res://scripts/game/characters/horse/horse_motion.gd")
const Renderer = preload("res://scripts/game/characters/horse/horse_renderer.gd")
const Avatar = preload("res://scripts/game/characters/horse/horse_avatar.gd")

const ID := "horse"
const IDLE_DURATION_MS := 2800
const HOME_DURATION_MS := 1450
const GROUND_PIVOT_Y := -0.855
const ROOT_Y_OFFSET_PER_RADIUS := 0.04
const FOOT_BASELINE_PER_RADIUS := 1.04
const SILHOUETTE_HEIGHT := 2.50

static func reaction(cue: String, frame: Dictionary) -> Dictionary:
	return Motion.reaction(cue, frame)

static func reaction_duration_scale() -> float:
	return Motion.REACTION_DURATION_SCALE
