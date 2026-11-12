@tool
extends RefCounted
## CharacterPose and LudoPaws3DReactionPose from the live renderer
## (feature/characters/rendering/CharacterRenderer.kt, LudoPaws3DReactionMotion.kt) as Dictionaries.
## Keys keep the Kotlin names; the four fore/hind leg channels are null unless a species sets them.

## CharacterMotion
const IDLE := "IDLE"
const HOP := "HOP"
const HOME := "HOME"
## CharacterHopPlayback
const ISOLATED := "ISOLATED"
const START := "START"
const CHAIN := "CHAIN"
const SETTLE := "SETTLE"
## AnimationCue
const CUES := ["IDLE", "EXCITED", "HAPPY", "SAD", "ANGRY", "NERVOUS", "SAFE", "CAPTURE", "CAPTURED", "HOME", "VICTORY", "DEFEAT"]

static func pose(values := {}) -> Dictionary:
	var p := {
		"liftY": 0.0, "bodyScaleY": 1.0, "bodyYawDegrees": 0.0, "headTiltDegrees": 0.0,
		"primaryAppendageDegrees": 0.0, "secondaryAppendageDegrees": 0.0, "tertiaryAppendageDegrees": 0.0,
		"legSwingDegrees": 0.0, "legTuckDegrees": 0.0, "legPaddleDegrees": 0.0,
		"bodyPitchDegrees": 0.0, "bodyRollDegrees": 0.0, "neckPitchDegrees": 0.0, "bodyCrouch": 0.0,
		"jawOpen": 0.0, "stridePhase": 0.0, "strideWeight": 0.0,
		"frontLegTuckDegrees": null, "rearLegTuckDegrees": null,
		"frontLegSwingDegrees": null, "rearLegSwingDegrees": null,
		"gestureWeight": 0.0, "gesturePhase": 0.0,
	}
	p.merge(values, true)
	return p

## Kotlin data-class copy(...)
static func copy(base: Dictionary, changes: Dictionary) -> Dictionary:
	var p := base.duplicate()
	p.merge(changes, true)
	return p

static func reaction(values := {}) -> Dictionary:
	var r := {
		"liftY": 0.0, "bodyYawDegrees": 0.0, "headTiltDegrees": 0.0,
		"primaryAppendageDegrees": 0.0, "secondaryAppendageDegrees": 0.0, "tertiaryAppendageDegrees": 0.0,
		"scale": 1.0, "bodyPitchDegrees": 0.0, "neckPitchDegrees": 0.0, "jawOpen": 0.0,
	}
	r.merge(values, true)
	return r

## Nullable float times a factor (Kotlin `x?.times(f)`).
static func times(value, factor: float):
	return null if value == null else float(value) * factor

static func zero_if_set(value):
	return null if value == null else 0.0
