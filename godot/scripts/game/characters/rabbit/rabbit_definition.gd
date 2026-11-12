@tool
extends RefCounted
## Port of RabbitDefinition.kt (presentation metadata).

const Motion = preload("res://scripts/game/characters/rabbit/rabbit_motion.gd")
const Renderer = preload("res://scripts/game/characters/rabbit/rabbit_renderer.gd")
const Avatar = preload("res://scripts/game/characters/rabbit/rabbit_avatar.gd")

const ID := "rabbit"
const IDLE_DURATION_MS := 2400
const HOME_DURATION_MS := 1350
const GROUND_PIVOT_Y := -0.73
const ROOT_Y_OFFSET_PER_RADIUS := 0.06
const FOOT_BASELINE_PER_RADIUS := 1.02
const SILHOUETTE_HEIGHT := 2.60

static func reaction(cue: String, frame: Dictionary) -> Dictionary:
	return Motion.reaction(cue, frame)

static func reaction_duration_scale() -> float:
	return Motion.REACTION_DURATION_SCALE
