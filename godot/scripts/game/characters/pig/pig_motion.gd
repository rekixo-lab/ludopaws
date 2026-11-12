@tool
extends RefCounted
## Pig reaction profile. Port of PigReactionMotion.kt.

const P = preload("res://scripts/game/characters/shared/pose.gd")

const REACTION_DURATION_SCALE := 1.08

static func reaction(cue: String, f: Dictionary) -> Dictionary:
	var p: float = f["p"]
	var arc: float = f["arc"]
	var wave: float = f["wave"]
	var fast: float = f["fast"]
	var bounce: float = f["bounce"]
	match cue:
		"IDLE": return P.reaction({"headTiltDegrees": 5.0 * wave, "secondaryAppendageDegrees": 10.0 * wave})
		"EXCITED", "HAPPY", "HOME": return P.reaction({"liftY": 0.17 * bounce, "headTiltDegrees": 7.0 * wave,
			"primaryAppendageDegrees": 16.0 * fast * arc, "secondaryAppendageDegrees": 25.0 * wave, "jawOpen": 0.32 * arc})
		"VICTORY": return P.reaction({"liftY": 0.20 * bounce, "bodyYawDegrees": 360.0 * p,
			"primaryAppendageDegrees": 18.0 * wave, "secondaryAppendageDegrees": 30.0 * fast * arc, "jawOpen": 0.45 * arc})
		"CAPTURE": return P.reaction({"liftY": 0.12 * arc, "neckPitchDegrees": -7.0 * arc,
			"secondaryAppendageDegrees": 20.0 * wave, "jawOpen": 0.30 * arc})
		"CAPTURED": return P.reaction({"liftY": 0.09 * arc, "headTiltDegrees": 12.0 * wave,
			"primaryAppendageDegrees": -15.0 * arc, "jawOpen": 0.45 * arc})
		"SAD", "DEFEAT": return P.reaction({"headTiltDegrees": 10.0 * arc, "primaryAppendageDegrees": 18.0 * arc,
			"neckPitchDegrees": 12.0 * arc, "secondaryAppendageDegrees": -12.0 * arc})
		"ANGRY": return P.reaction({"bodyYawDegrees": 8.0 * fast * (1.0 - p), "neckPitchDegrees": 6.0 * arc, "jawOpen": 0.28 * arc})
		"NERVOUS": return P.reaction({"headTiltDegrees": 9.0 * wave, "primaryAppendageDegrees": 10.0 * fast * (1.0 - p)})
		"SAFE": return P.reaction({"headTiltDegrees": 5.0 * wave, "secondaryAppendageDegrees": 12.0 * wave})
	return P.reaction()
