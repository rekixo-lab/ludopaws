@tool
extends RefCounted
## Horse reaction profile. Port of HorseReactionMotion.kt.

const P = preload("res://scripts/game/characters/shared/pose.gd")

const REACTION_DURATION_SCALE := 1.02

static func reaction(cue: String, f: Dictionary) -> Dictionary:
	var p: float = f["p"]
	var arc: float = f["arc"]
	var wave: float = f["wave"]
	var fast: float = f["fast"]
	var bounce: float = f["bounce"]
	match cue:
		"IDLE": return P.reaction({"headTiltDegrees": 4.0 * wave, "primaryAppendageDegrees": 8.0 * wave})
		"EXCITED", "HAPPY", "HOME": return P.reaction({"liftY": 0.18 * bounce, "neckPitchDegrees": -7.0 * arc,
			"primaryAppendageDegrees": 12.0 * arc, "secondaryAppendageDegrees": 25.0 * wave, "jawOpen": 0.35 * arc})
		"VICTORY": return P.reaction({"liftY": 0.22 * bounce, "bodyYawDegrees": 360.0 * p,
			"neckPitchDegrees": -10.0 * arc, "secondaryAppendageDegrees": 30.0 * wave, "jawOpen": 0.50 * arc})
		"CAPTURE": return P.reaction({"liftY": 0.13 * arc, "headTiltDegrees": -5.0 * arc,
			"primaryAppendageDegrees": 15.0 * arc, "secondaryAppendageDegrees": 20.0 * wave, "jawOpen": 0.28 * arc})
		"CAPTURED": return P.reaction({"headTiltDegrees": 12.0 * wave, "primaryAppendageDegrees": -18.0 * arc,
			"neckPitchDegrees": -8.0 * arc, "jawOpen": 0.40 * arc})
		"SAD", "DEFEAT": return P.reaction({"neckPitchDegrees": 13.0 * arc, "headTiltDegrees": 8.0 * arc,
			"primaryAppendageDegrees": -16.0 * arc, "secondaryAppendageDegrees": -10.0 * arc})
		"ANGRY": return P.reaction({"bodyYawDegrees": 7.0 * fast * (1.0 - p), "primaryAppendageDegrees": -20.0 * arc, "jawOpen": 0.25 * arc})
		"NERVOUS": return P.reaction({"headTiltDegrees": 8.0 * wave, "primaryAppendageDegrees": 12.0 * fast * (1.0 - p),
			"secondaryAppendageDegrees": 15.0 * wave})
		"SAFE": return P.reaction({"neckPitchDegrees": -4.0 * arc, "secondaryAppendageDegrees": 12.0 * wave})
	return P.reaction()
