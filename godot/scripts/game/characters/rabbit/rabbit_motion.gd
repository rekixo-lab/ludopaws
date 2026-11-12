@tool
extends RefCounted
## Rabbit reaction profile. Port of RabbitReactionMotion.kt.

const P = preload("res://scripts/game/characters/shared/pose.gd")

const REACTION_DURATION_SCALE := 0.93

static func reaction(cue: String, f: Dictionary) -> Dictionary:
	var p: float = f["p"]
	var arc: float = f["arc"]
	var wave: float = f["wave"]
	var fast: float = f["fast"]
	var bounce: float = f["bounce"]
	match cue:
		"IDLE": return P.reaction({"headTiltDegrees": 6.0 * wave, "primaryAppendageDegrees": 7.0 * wave})
		"EXCITED", "HAPPY", "HOME": return P.reaction({"liftY": 0.23 * bounce, "primaryAppendageDegrees": 18.0 * wave,
			"secondaryAppendageDegrees": 12.0 * fast * arc, "headTiltDegrees": 7.0 * wave, "jawOpen": 0.20 * arc})
		"VICTORY": return P.reaction({"liftY": 0.27 * bounce, "bodyYawDegrees": 360.0 * p,
			"primaryAppendageDegrees": 22.0 * wave, "secondaryAppendageDegrees": 15.0 * wave, "jawOpen": 0.25 * arc})
		"CAPTURE": return P.reaction({"liftY": 0.17 * arc, "neckPitchDegrees": -6.0 * arc, "primaryAppendageDegrees": -12.0 * arc})
		"CAPTURED": return P.reaction({"liftY": 0.10 * arc, "headTiltDegrees": 12.0 * wave,
			"primaryAppendageDegrees": 30.0 * arc, "jawOpen": 0.30 * arc})
		"SAD", "DEFEAT": return P.reaction({"headTiltDegrees": 9.0 * arc, "neckPitchDegrees": 11.0 * arc, "primaryAppendageDegrees": 38.0 * arc})
		"ANGRY": return P.reaction({"bodyYawDegrees": 8.0 * fast * (1.0 - p), "primaryAppendageDegrees": 25.0 * arc, "jawOpen": 0.15 * arc})
		"NERVOUS": return P.reaction({"headTiltDegrees": 9.0 * wave, "primaryAppendageDegrees": 15.0 * fast * (1.0 - p)})
		"SAFE": return P.reaction({"headTiltDegrees": 5.0 * wave, "primaryAppendageDegrees": -7.0 * arc})
	return P.reaction()
