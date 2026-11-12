@tool
extends RefCounted
## Giraffe reaction profile. Port of GiraffeReactionMotion.kt.

const P = preload("res://scripts/game/characters/shared/pose.gd")

const REACTION_DURATION_SCALE := 1.08

static func reaction(cue: String, f: Dictionary) -> Dictionary:
	var p: float = f["p"]
	var arc: float = f["arc"]
	var wave: float = f["wave"]
	var fast: float = f["fast"]
	var bounce: float = f["bounce"]
	match cue:
		"IDLE": return P.reaction({"headTiltDegrees": 4.0 * wave, "primaryAppendageDegrees": 7.0 * wave})
		"EXCITED", "HAPPY", "HOME": return P.reaction({"liftY": 0.16 * bounce, "neckPitchDegrees": -8.0 * arc,
			"primaryAppendageDegrees": 14.0 * wave, "secondaryAppendageDegrees": 17.0 * wave, "jawOpen": 0.25 * arc})
		"VICTORY": return P.reaction({"liftY": 0.17 * bounce, "bodyYawDegrees": 360.0 * p, "neckPitchDegrees": 9.0 * arc,
			"headTiltDegrees": 7.0 * wave, "secondaryAppendageDegrees": 23.0 * wave, "jawOpen": 0.32 * arc})
		"CAPTURE": return P.reaction({"liftY": 0.09 * arc, "neckPitchDegrees": 14.0 * arc,
			"primaryAppendageDegrees": 9.0 * arc, "secondaryAppendageDegrees": 21.0 * wave})
		"CAPTURED": return P.reaction({"neckPitchDegrees": -13.0 * arc, "headTiltDegrees": 8.0 * fast * (1.0 - p),
			"primaryAppendageDegrees": -12.0 * arc, "jawOpen": 0.35 * arc})
		"SAD", "DEFEAT": return P.reaction({"neckPitchDegrees": 20.0 * arc, "headTiltDegrees": 9.0 * arc,
			"primaryAppendageDegrees": 23.0 * arc, "secondaryAppendageDegrees": -9.0 * arc})
		"ANGRY": return P.reaction({"neckPitchDegrees": 10.0 * arc, "bodyYawDegrees": 4.0 * fast * (1.0 - p),
			"primaryAppendageDegrees": -12.0 * arc})
		"NERVOUS": return P.reaction({"headTiltDegrees": 8.0 * wave, "primaryAppendageDegrees": 15.0 * fast * (1.0 - p),
			"secondaryAppendageDegrees": 12.0 * wave})
		"SAFE": return P.reaction({"neckPitchDegrees": 5.0 * arc, "primaryAppendageDegrees": 7.0 * wave})
	return P.reaction()
