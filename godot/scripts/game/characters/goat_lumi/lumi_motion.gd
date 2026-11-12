@tool
extends RefCounted
## A cautious white kid: small nods and ear turns lead its reactions. Port of LumiGoatReactionMotion.kt.

const P = preload("res://scripts/game/characters/shared/pose.gd")

const REACTION_DURATION_SCALE := 1.12

static func reaction(cue: String, f: Dictionary) -> Dictionary:
	var p: float = f["p"]
	var arc: float = f["arc"]
	var wave: float = f["wave"]
	var fast: float = f["fast"]
	var bounce: float = f["bounce"]
	match cue:
		"IDLE": return P.reaction({"headTiltDegrees": 5.0 * wave, "primaryAppendageDegrees": 9.0 * wave})
		"EXCITED", "HAPPY", "HOME": return P.reaction({"liftY": 0.16 * bounce, "neckPitchDegrees": -6.0 * arc, "headTiltDegrees": 7.0 * wave,
			"primaryAppendageDegrees": 21.0 * arc, "tertiaryAppendageDegrees": 23.0 * wave, "jawOpen": 0.25 * arc})
		"VICTORY": return P.reaction({"liftY": 0.21 * bounce, "bodyYawDegrees": 360.0 * p, "neckPitchDegrees": -10.0 * arc,
			"primaryAppendageDegrees": 15.0 * wave, "tertiaryAppendageDegrees": 27.0 * wave, "jawOpen": 0.36 * arc})
		"CAPTURE": return P.reaction({"liftY": 0.09 * arc, "neckPitchDegrees": 9.0 * arc, "headTiltDegrees": -6.0 * arc,
			"primaryAppendageDegrees": 14.0 * arc, "tertiaryAppendageDegrees": 17.0 * wave})
		"CAPTURED": return P.reaction({"neckPitchDegrees": -12.0 * arc, "primaryAppendageDegrees": -18.0 * arc,
			"headTiltDegrees": 8.0 * wave, "jawOpen": 0.32 * arc})
		"SAD", "DEFEAT": return P.reaction({"neckPitchDegrees": 15.0 * arc, "headTiltDegrees": 9.0 * arc,
			"primaryAppendageDegrees": -19.0 * arc, "tertiaryAppendageDegrees": -8.0 * arc})
		"ANGRY": return P.reaction({"neckPitchDegrees": 10.0 * arc, "primaryAppendageDegrees": -13.0 * arc,
			"bodyYawDegrees": 5.0 * fast * (1.0 - p), "jawOpen": 0.23 * arc})
		"NERVOUS": return P.reaction({"headTiltDegrees": 8.0 * wave, "primaryAppendageDegrees": 17.0 * fast * (1.0 - p),
			"tertiaryAppendageDegrees": 9.0 * wave})
		"SAFE": return P.reaction({"neckPitchDegrees": 4.0 * arc, "primaryAppendageDegrees": 11.0 * wave})
	return P.reaction()
