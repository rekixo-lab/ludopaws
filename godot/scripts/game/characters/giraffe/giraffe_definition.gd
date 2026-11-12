@tool
extends RefCounted
## Port of GiraffeDefinition.kt (presentation metadata).

const Motion = preload("res://scripts/game/characters/giraffe/giraffe_motion.gd")
const Renderer = preload("res://scripts/game/characters/giraffe/giraffe_renderer.gd")
const Avatar = preload("res://scripts/game/characters/giraffe/giraffe_avatar.gd")

const ID := "giraffe"
const MODEL_SCALE := 0.84
const LEG_Y_SCALE := 1.24
const LEG_Y_ANCHOR := -0.22
const HOOF_BOTTOM := -0.09
const HOOF_OFFSET := -0.02
## LEG_Y_ANCHOR + (DonkeyLegRig.GROUND_ANKLE_Y - LEG_Y_ANCHOR) * LEG_Y_SCALE + HOOF_OFFSET + HOOF_BOTTOM
const GROUND_Y := LEG_Y_ANCHOR + (-0.74 - LEG_Y_ANCHOR) * LEG_Y_SCALE + HOOF_OFFSET + HOOF_BOTTOM
const IDLE_DURATION_MS := 3400
const HOME_DURATION_MS := 1500
const GROUND_PIVOT_Y := GROUND_Y * MODEL_SCALE
const ROOT_Y_OFFSET_PER_RADIUS := 0.04
const FOOT_BASELINE_PER_RADIUS := 1.06
## Measured from grounded hooves to the tips of the ossicones in the neutral rig.
const SILHOUETTE_HEIGHT := (2.07 - GROUND_Y) * MODEL_SCALE

static func reaction(cue: String, frame: Dictionary) -> Dictionary:
	return Motion.reaction(cue, frame)

static func reaction_duration_scale() -> float:
	return Motion.REACTION_DURATION_SCALE
