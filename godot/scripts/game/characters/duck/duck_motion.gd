@tool
extends RefCounted
## Duck motion timeline and reaction profile. Port of Duck3DMotion.kt and DuckReactionMotion.kt.

const P = preload("res://scripts/game/characters/shared/pose.gd")
const Hop = preload("res://scripts/game/characters/shared/hop_dynamics.gd")

const IDLE_DURATION_MS := 2200
const HOP_DURATION_MS := 720
const HOME_DURATION_MS := 1350
const REACTION_DURATION_SCALE := 0.94

static func step_travel_progress(progress: float) -> float:
	return Hop.travel(progress)

static func _smooth(p: float, a: float, b: float) -> float:
	var t := clampf((p - a) / (b - a), 0.0, 1.0)
	return t * t * (3.0 - 2.0 * t)

static func _pulse(p: float, a: float, b: float, c: float, d: float) -> float:
	return _smooth(p, a, b) * (1.0 - _smooth(p, c, d))

## Duck3DPose: wingFlap -> primary, legPaddle, legTuck, bodyCrouch, pitch/roll/neck.
static func sample(motion: String, progress: float) -> Dictionary:
	var p := clampf(progress, 0.0, 1.0)
	var out := {"liftY": 0.0, "bodyYawDegrees": 0.0, "wingFlapDegrees": 0.0, "headTiltDegrees": 0.0,
		"legPaddleDegrees": 0.0, "legTuckDegrees": 0.0, "bodyPitchDegrees": 0.0, "bodyRollDegrees": 0.0,
		"neckPitchDegrees": 0.0, "bodyCrouch": 0.0}
	match motion:
		P.IDLE:
			var wave := sin(p * PI * 2.0)
			out.merge({"wingFlapDegrees": 3.5 * wave, "bodyRollDegrees": 1.2 * wave, "neckPitchDegrees": -1.0 * wave,
				"headTiltDegrees": 2.5 * sin(p * PI * 2.0 + PI / 3.0)}, true)
		P.HOP:
			var flight := step_travel_progress(p)
			var arc := maxf(sin(flight * PI), 0.0)
			var flap := sin(p * PI * 4.0) * arc
			var preload_w := _pulse(p, 0.0, 0.07, 0.10, 0.16)
			var land := _pulse(p, 0.82, 0.88, 0.91, 1.0)
			out.merge({"liftY": 0.62 * 4.0 * flight * (1.0 - flight), "legTuckDegrees": 30.0 * arc,
				"bodyCrouch": 0.06 * preload_w + 0.055 * land, "bodyPitchDegrees": -4.0 * arc + 4.0 * land,
				"bodyRollDegrees": 2.5 * sin(p * PI * 2.0) * arc, "neckPitchDegrees": 3.0 * arc - 4.0 * land,
				"wingFlapDegrees": 34.0 * arc + 7.0 * flap, "headTiltDegrees": -7.0 * arc,
				"legPaddleDegrees": 42.0 * sin(p * PI * 2.0) * arc}, true)
		P.HOME:
			var bounce := absf(sin(p * PI * 3.0)) * (1.0 - p * 0.35)
			out.merge({"liftY": 0.34 * bounce, "legTuckDegrees": 12.0 * bounce,
				"bodyRollDegrees": 3.0 * sin(p * PI * 6.0) * sin(p * PI), "bodyYawDegrees": 360.0 * p,
				"wingFlapDegrees": 52.0 * maxf(sin(p * PI), 0.0), "headTiltDegrees": 8.0 * sin(p * PI * 2.0),
				"legPaddleDegrees": 20.0 * sin(p * PI * 6.0)}, true)
	return out

static func reaction(cue: String, f: Dictionary) -> Dictionary:
	var p: float = f["p"]
	var arc: float = f["arc"]
	var wave: float = f["wave"]
	var fast: float = f["fast"]
	var bounce: float = f["bounce"]
	var r := {}
	match cue:
		"IDLE": r = {"liftY": 0.025 * wave, "headTiltDegrees": 7.0 * wave, "primaryAppendageDegrees": 8.0 * wave}
		"EXCITED", "HAPPY": r = {"liftY": 0.25 * bounce, "bodyYawDegrees": 7.0 * wave, "headTiltDegrees": -7.0 * arc, "primaryAppendageDegrees": 38.0 * arc + 9.0 * fast, "scale": 1.0 + 0.04 * arc}
		"SAFE": r = {"liftY": 0.07 * arc, "headTiltDegrees": 5.0 * wave, "primaryAppendageDegrees": 11.0 * arc}
		"CAPTURE": r = {"liftY": 0.18 * arc, "bodyYawDegrees": 13.0 * wave, "headTiltDegrees": -8.0 * arc, "primaryAppendageDegrees": 42.0 * arc, "scale": 1.0 + 0.045 * arc}
		"CAPTURED": r = {"liftY": 0.12 * arc, "bodyYawDegrees": 15.0 * fast * (1.0 - p), "headTiltDegrees": 12.0 * wave, "primaryAppendageDegrees": 28.0 * fast * (1.0 - p), "scale": 1.0 - 0.045 * arc}
		"SAD", "DEFEAT": r = {"liftY": -0.045 * arc, "headTiltDegrees": 13.0 * arc, "primaryAppendageDegrees": -5.0 * arc, "scale": 1.0 - 0.03 * arc}
		"ANGRY": r = {"bodyYawDegrees": 10.0 * fast * (1.0 - p), "headTiltDegrees": -8.0 * arc, "primaryAppendageDegrees": 26.0 * fast}
		"NERVOUS": r = {"bodyYawDegrees": 6.0 * fast * (1.0 - p), "headTiltDegrees": 10.0 * wave, "primaryAppendageDegrees": 12.0 * fast}
		"HOME": r = {"liftY": 0.18 * bounce, "headTiltDegrees": 8.0 * wave, "primaryAppendageDegrees": 42.0 * arc + 6.0 * fast, "scale": 1.0 + 0.035 * arc}
		"VICTORY": r = {"liftY": 0.23 * bounce, "bodyYawDegrees": 360.0 * p, "headTiltDegrees": 8.0 * wave, "primaryAppendageDegrees": 50.0 * arc + 8.0 * fast, "scale": 1.0 + 0.05 * arc}
	var pose := P.reaction(r)
	pose["scale"] = 1.0 + (pose["scale"] - 1.0) * 0.45
	match cue:
		"EXCITED", "HAPPY", "HOME", "VICTORY": pose["jawOpen"] = 0.35 * arc
		"CAPTURED": pose["jawOpen"] = 0.28 * arc
		"CAPTURE", "ANGRY": pose["jawOpen"] = 0.22 * arc
		_: pose["jawOpen"] = 0.0
	match cue:
		"SAD", "DEFEAT": pose["neckPitchDegrees"] = 9.0 * arc
		"EXCITED", "VICTORY": pose["neckPitchDegrees"] = -5.0 * arc
		"NERVOUS": pose["neckPitchDegrees"] = -3.0 * arc
		_: pose["neckPitchDegrees"] = 0.0
	return pose
