@tool
extends RefCounted
## Panda reaction profile. Port of PandaReactionMotion.kt.

const P = preload("res://scripts/game/characters/shared/pose.gd")

const REACTION_DURATION_SCALE := 1.12

static func reaction(cue: String, f: Dictionary) -> Dictionary:
	var p: float = f["p"]
	var arc: float = f["arc"]
	var wave: float = f["wave"]
	var fast: float = f["fast"]
	var bounce: float = f["bounce"]
	match cue:
		"IDLE": return P.reaction({"headTiltDegrees": 4.0 * wave, "primaryAppendageDegrees": 5.0 * wave})
		"EXCITED", "HAPPY", "HOME": return P.reaction({"liftY": 0.20 * bounce, "primaryAppendageDegrees": 55.0 * arc + 12.0 * wave,
			"secondaryAppendageDegrees": 16.0 * wave, "headTiltDegrees": 10.0 * wave, "jawOpen": 0.24 * arc})
		"VICTORY": return P.reaction({"liftY": 0.20 * bounce, "bodyYawDegrees": 360.0 * p, "primaryAppendageDegrees": 75.0 * arc,
			"secondaryAppendageDegrees": 22.0 * fast * arc, "headTiltDegrees": 10.0 * wave, "jawOpen": 0.24 * arc})
		"CAPTURE": return P.reaction({"liftY": 0.12 * arc, "neckPitchDegrees": -7.0 * arc})
		"CAPTURED": return P.reaction({"headTiltDegrees": 13.0 * wave, "primaryAppendageDegrees": 22.0 * arc, "jawOpen": 0.25 * arc})
		"SAD", "DEFEAT": return P.reaction({"neckPitchDegrees": 18.0 * arc, "headTiltDegrees": 12.0 * arc, "primaryAppendageDegrees": -15.0 * arc})
		"ANGRY": return P.reaction({"bodyYawDegrees": 6.0 * fast * (1.0 - p), "primaryAppendageDegrees": 14.0 * arc})
		"NERVOUS": return P.reaction({"headTiltDegrees": 7.0 * wave, "primaryAppendageDegrees": 9.0 * fast * (1.0 - p)})
		"SAFE": return P.reaction({"headTiltDegrees": 5.0 * wave, "primaryAppendageDegrees": 8.0 * arc})
	return P.reaction()
