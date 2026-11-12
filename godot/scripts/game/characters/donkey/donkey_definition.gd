@tool
extends RefCounted
## Port of DonkeyDefinition.kt (presentation metadata).

const Motion = preload("res://scripts/game/characters/donkey/donkey_motion.gd")
const Renderer = preload("res://scripts/game/characters/donkey/donkey_renderer.gd")
const Avatar = preload("res://scripts/game/characters/donkey/donkey_avatar.gd")

const ID := "donkey"
const IDLE_DURATION_MS := Motion.IDLE_DURATION_MS
const HOME_DURATION_MS := Motion.HOME_DURATION_MS
const GROUND_PIVOT_Y := -0.84
const ROOT_Y_OFFSET_PER_RADIUS := 0.02
const FOOT_BASELINE_PER_RADIUS := 1.028
const SILHOUETTE_HEIGHT := 3.35

static func reaction(cue: String, frame: Dictionary) -> Dictionary:
	return Motion.reaction(cue, frame)

static func reaction_duration_scale() -> float:
	return Motion.REACTION_DURATION_SCALE
