@tool
extends RefCounted
## Port of ElephantDefinition.kt (presentation metadata).

const Motion = preload("res://scripts/game/characters/elephant/elephant_motion.gd")
const Renderer = preload("res://scripts/game/characters/elephant/elephant_renderer.gd")
const Avatar = preload("res://scripts/game/characters/elephant/elephant_avatar.gd")

const ID := "elephant"
const IDLE_DURATION_MS := 3000
const HOME_DURATION_MS := 1600
const GROUND_PIVOT_Y := -0.84
const ROOT_Y_OFFSET_PER_RADIUS := 0.06
const FOOT_BASELINE_PER_RADIUS := 1.04
const SILHOUETTE_HEIGHT := 2.10

static func reaction(cue: String, frame: Dictionary) -> Dictionary:
	return Motion.reaction(cue, frame)

static func reaction_duration_scale() -> float:
	return Motion.REACTION_DURATION_SCALE
