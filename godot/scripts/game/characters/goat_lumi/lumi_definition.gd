@tool
extends RefCounted
## Port of LumiGoatDefinition.kt: a separate starter character built on the Goat definition.

const Motion = preload("res://scripts/game/characters/goat_lumi/lumi_motion.gd")
const Renderer = preload("res://scripts/game/characters/goat_lumi/lumi_renderer.gd")
const Avatar = preload("res://scripts/game/characters/goat_lumi/lumi_avatar.gd")
const Goat = preload("res://scripts/game/characters/goat/goat_definition.gd")

const ID := "goat_lumi"
const IDLE_DURATION_MS := 3150
const HOME_DURATION_MS := Goat.HOME_DURATION_MS
const MODEL_SIZE_MULTIPLIER := 1.38
## Keep foot contact aligned with the shortened, uniformly enlarged model.
const GROUND_PIVOT_Y := -0.27 * MODEL_SIZE_MULTIPLIER
const ROOT_Y_OFFSET_PER_RADIUS := Goat.ROOT_Y_OFFSET_PER_RADIUS
const FOOT_BASELINE_PER_RADIUS := 1.18
const SILHOUETTE_HEIGHT := 2.35 * MODEL_SIZE_MULTIPLIER

static func reaction(cue: String, frame: Dictionary) -> Dictionary:
	return Motion.reaction(cue, frame)

static func reaction_duration_scale() -> float:
	return Motion.REACTION_DURATION_SCALE
