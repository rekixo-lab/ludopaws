@tool
extends RefCounted
## Cock reaction profile. Port of CockReactionMotion.kt.

const P = preload("res://scripts/game/characters/shared/pose.gd")

const REACTION_DURATION_SCALE := 1.02

static func reaction(cue: String, f: Dictionary) -> Dictionary:
	var p: float = f["p"]
	var arc: float = f["arc"]
	var wave: float = f["wave"]
	var fast: float = f["fast"]
	var bounce: float = f["bounce"]
	match cue:
		"IDLE": return P.reaction({"headTiltDegrees": 6.0 * wave, "neckPitchDegrees": -3.0 * arc})
		"EXCITED", "HAPPY", "HOME": return P.reaction({"liftY": 0.18 * bounce, "primaryAppendageDegrees": 36.0 * arc + 7.0 * fast,
			"neckPitchDegrees": -10.0 * arc, "jawOpen": 0.45 * arc})
		"VICTORY": return P.reaction({"liftY": 0.22 * bounce, "bodyYawDegrees": 360.0 * p,
			"primaryAppendageDegrees": 48.0 * arc, "neckPitchDegrees": -14.0 * arc, "jawOpen": 0.65 * arc})
		"CAPTURE": return P.reaction({"liftY": 0.12 * arc, "bodyYawDegrees": 10.0 * wave,
			"primaryAppendageDegrees": 30.0 * arc, "neckPitchDegrees": -8.0 * arc, "jawOpen": 0.32 * arc})
		"CAPTURED": return P.reaction({"liftY": 0.10 * arc, "headTiltDegrees": 14.0 * wave,
			"primaryAppendageDegrees": 25.0 * fast * (1.0 - p), "jawOpen": 0.35 * arc})
		"SAD", "DEFEAT": return P.reaction({"headTiltDegrees": 12.0 * arc, "neckPitchDegrees": 14.0 * arc, "primaryAppendageDegrees": -6.0 * arc})
		"ANGRY": return P.reaction({"bodyYawDegrees": 9.0 * fast * (1.0 - p), "neckPitchDegrees": 7.0 * arc,
			"primaryAppendageDegrees": 22.0 * arc, "jawOpen": 0.25 * arc})
		"NERVOUS": return P.reaction({"headTiltDegrees": 10.0 * wave, "primaryAppendageDegrees": 10.0 * fast * (1.0 - p)})
		"SAFE": return P.reaction({"headTiltDegrees": 5.0 * wave, "neckPitchDegrees": -6.0 * arc})
	return P.reaction()
