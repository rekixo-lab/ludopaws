@tool
extends RefCounted
## Elephant reaction profile. Port of ElephantReactionMotion.kt.

const P = preload("res://scripts/game/characters/shared/pose.gd")

const REACTION_DURATION_SCALE := 1.16

static func reaction(cue: String, f: Dictionary) -> Dictionary:
	var p: float = f["p"]
	var arc: float = f["arc"]
	var wave: float = f["wave"]
	var fast: float = f["fast"]
	var bounce: float = f["bounce"]
	match cue:
		"IDLE": return P.reaction({"headTiltDegrees": 3.0 * wave, "secondaryAppendageDegrees": 9.0 * wave})
		"EXCITED", "HAPPY", "HOME": return P.reaction({"liftY": 0.12 * bounce, "primaryAppendageDegrees": 20.0 * arc,
			"secondaryAppendageDegrees": 55.0 * arc, "tertiaryAppendageDegrees": 18.0 * wave, "jawOpen": 0.25 * arc})
		"VICTORY": return P.reaction({"liftY": 0.16 * bounce, "bodyYawDegrees": 360.0 * p, "primaryAppendageDegrees": 28.0 * arc,
			"secondaryAppendageDegrees": 75.0 * arc, "tertiaryAppendageDegrees": 25.0 * wave, "jawOpen": 0.40 * arc})
		"CAPTURE": return P.reaction({"headTiltDegrees": -5.0 * arc, "primaryAppendageDegrees": 16.0 * arc,
			"secondaryAppendageDegrees": 42.0 * arc, "jawOpen": 0.25 * arc})
		"CAPTURED": return P.reaction({"headTiltDegrees": 10.0 * wave, "primaryAppendageDegrees": 25.0 * arc,
			"secondaryAppendageDegrees": -16.0 * arc, "jawOpen": 0.35 * arc})
		"SAD", "DEFEAT": return P.reaction({"headTiltDegrees": 8.0 * arc, "neckPitchDegrees": 10.0 * arc,
			"primaryAppendageDegrees": -12.0 * arc, "secondaryAppendageDegrees": -15.0 * arc})
		"ANGRY": return P.reaction({"bodyYawDegrees": 6.0 * fast * (1.0 - p), "primaryAppendageDegrees": 22.0 * arc,
			"secondaryAppendageDegrees": 28.0 * arc, "jawOpen": 0.25 * arc})
		"NERVOUS": return P.reaction({"headTiltDegrees": 7.0 * wave, "primaryAppendageDegrees": 8.0 * fast * (1.0 - p),
			"secondaryAppendageDegrees": 18.0 * arc})
		"SAFE": return P.reaction({"headTiltDegrees": 4.0 * wave, "secondaryAppendageDegrees": 16.0 * arc})
	return P.reaction()
