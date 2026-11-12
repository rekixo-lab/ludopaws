@tool
extends RefCounted
## Cat motion timeline and reaction profile. Port of Cat3DMotion.kt and CatReactionMotion.kt.

const P = preload("res://scripts/game/characters/shared/pose.gd")
const Hop = preload("res://scripts/game/characters/shared/hop_dynamics.gd")

const IDLE_DURATION_MS := 2000
const HOP_DURATION_MS := 650
const HOME_DURATION_MS := 1250
const REACTION_DURATION_SCALE := 0.98

static func step_travel_progress(progress: float) -> float:
	return Hop.travel(progress)

static func _smooth(p: float, a: float, b: float) -> float:
	var t := clampf((p - a) / (b - a), 0.0, 1.0)
	return t * t * (3.0 - 2.0 * t)

static func _pulse(p: float, a: float, b: float, c: float, d: float) -> float:
	return _smooth(p, a, b) * (1.0 - _smooth(p, c, d))

## Cat3DPose: earTwitch -> primary, tailSway -> secondary.
static func sample(motion: String, progress: float) -> Dictionary:
	var p := clampf(progress, 0.0, 1.0)
	var out := {"liftY": 0.0, "bodyYawDegrees": 0.0, "headTiltDegrees": 0.0, "earTwitchDegrees": 0.0,
		"tailSwayDegrees": 0.0, "legSwingDegrees": 0.0, "legTuckDegrees": 0.0, "bodyPitchDegrees": 0.0,
		"neckPitchDegrees": 0.0, "bodyCrouch": 0.0, "frontLegTuckDegrees": null, "rearLegTuckDegrees": null,
		"frontLegSwingDegrees": null, "rearLegSwingDegrees": null}
	match motion:
		P.IDLE:
			var wave := sin(p * PI * 2.0)
			var tail := sin(p * PI * 2.0) * 0.75 + sin(p * PI * 4.0) * 0.34
			out.merge({"headTiltDegrees": 2.5 * wave, "earTwitchDegrees": 3.5 * sin(p * PI * 4.0),
				"neckPitchDegrees": 1.5 * -wave, "tailSwayDegrees": 24.0 * tail}, true)
		P.HOP:
			var flight := step_travel_progress(p)
			var arc := maxf(sin(flight * PI), 0.0)
			var front := _pulse(p, 0.16, 0.30, 0.55, 0.76)
			var rear := _pulse(p, 0.24, 0.40, 0.70, 0.90)
			var preload_w := _pulse(p, 0.0, 0.08, 0.12, 0.20)
			var land := _pulse(p, 0.80, 0.87, 0.90, 1.0)
			var tail := sin(p * PI * 3.0)
			out.merge({"liftY": 0.60 * 4.0 * flight * (1.0 - flight), "bodyCrouch": 0.08 * preload_w + 0.06 * land,
				"bodyPitchDegrees": -4.0 * front * (1.0 - _smooth(p, 0.35, 0.55)) + 3.0 * land,
				"neckPitchDegrees": 3.0 * arc - 4.0 * land, "frontLegTuckDegrees": 32.0 * front, "rearLegTuckDegrees": 29.0 * rear,
				"frontLegSwingDegrees": (16.0 - 30.0 * _smooth(p, 0.40, 0.71)) * front,
				"rearLegSwingDegrees": (-16.0 + 28.0 * _smooth(p, 0.43, 0.70)) * rear,
				"headTiltDegrees": -6.0 * arc, "earTwitchDegrees": 12.0 * arc, "tailSwayDegrees": 22.0 * tail,
				"legSwingDegrees": 28.0 * sin(p * PI * 2.0) * arc, "legTuckDegrees": 38.0 * arc}, true)
		P.HOME:
			var bounce := absf(sin(p * PI * 3.0)) * (1.0 - p * 0.30)
			out.merge({"liftY": 0.30 * bounce, "bodyYawDegrees": 360.0 * p, "headTiltDegrees": 7.0 * sin(p * PI * 2.0),
				"earTwitchDegrees": 12.0 * maxf(sin(p * PI), 0.0), "tailSwayDegrees": 50.0 * sin(p * PI * 6.0),
				"legSwingDegrees": 12.0 * sin(p * PI * 6.0), "legTuckDegrees": 9.0 * maxf(sin(p * PI * 3.0), 0.0)}, true)
	return out

static func reaction(cue: String, f: Dictionary) -> Dictionary:
	var p: float = f["p"]
	var arc: float = f["arc"]
	var wave: float = f["wave"]
	var fast: float = f["fast"]
	var bounce: float = f["bounce"]
	var r := {}
	match cue:
		"IDLE": r = {"headTiltDegrees": 5.0 * wave, "primaryAppendageDegrees": 4.0 * fast, "secondaryAppendageDegrees": 28.0 * wave}
		"EXCITED": r = {"liftY": 0.16 * arc, "headTiltDegrees": -5.0 * arc, "primaryAppendageDegrees": 9.0 * arc, "secondaryAppendageDegrees": 26.0 * fast}
		"HAPPY", "SAFE": r = {"liftY": 0.07 * arc, "bodyYawDegrees": 5.0 * wave, "headTiltDegrees": 6.0 * wave, "primaryAppendageDegrees": 5.0 * fast, "secondaryAppendageDegrees": 32.0 * wave}
		"CAPTURE": r = {"liftY": 0.12 * arc, "bodyYawDegrees": 14.0 * wave, "headTiltDegrees": -7.0 * arc, "primaryAppendageDegrees": 8.0 * arc, "secondaryAppendageDegrees": 39.0 * fast, "scale": 1.0 + 0.035 * arc}
		"CAPTURED": r = {"liftY": 0.10 * arc, "bodyYawDegrees": 12.0 * fast * (1.0 - p), "headTiltDegrees": 11.0 * wave, "primaryAppendageDegrees": -9.0 * arc, "secondaryAppendageDegrees": 14.0 * fast, "scale": 1.0 - 0.04 * arc}
		"SAD", "DEFEAT": r = {"liftY": -0.04 * arc, "headTiltDegrees": 9.0 * arc, "primaryAppendageDegrees": -7.0 * arc, "secondaryAppendageDegrees": 13.0 * wave, "scale": 1.0 - 0.025 * arc}
		"ANGRY": r = {"bodyYawDegrees": 5.0 * fast * (1.0 - p), "headTiltDegrees": -6.0 * arc, "primaryAppendageDegrees": -8.0 * arc, "secondaryAppendageDegrees": 26.0 * fast}
		"NERVOUS": r = {"bodyYawDegrees": 4.0 * fast * (1.0 - p), "headTiltDegrees": 7.0 * wave, "primaryAppendageDegrees": 7.0 * fast, "secondaryAppendageDegrees": 18.0 * fast}
		"HOME": r = {"liftY": 0.12 * bounce, "headTiltDegrees": 7.0 * wave, "primaryAppendageDegrees": 8.0 * fast, "secondaryAppendageDegrees": 38.0 * fast, "scale": 1.0 + 0.025 * arc}
		"VICTORY": r = {"liftY": 0.17 * bounce, "bodyYawDegrees": 360.0 * p, "headTiltDegrees": 7.0 * wave, "primaryAppendageDegrees": 10.0 * fast, "secondaryAppendageDegrees": 48.0 * fast, "scale": 1.0 + 0.035 * arc}
	var pose := P.reaction(r)
	pose["scale"] = 1.0 + (pose["scale"] - 1.0) * 0.45
	match cue:
		"SAD", "DEFEAT": pose["neckPitchDegrees"] = 8.0 * arc
		"CAPTURE", "ANGRY": pose["neckPitchDegrees"] = 6.0 * arc
		"NERVOUS": pose["neckPitchDegrees"] = -4.0 * arc
		_: pose["neckPitchDegrees"] = 0.0
	match cue:
		"CAPTURED": pose["jawOpen"] = 0.30 * arc
		"ANGRY", "CAPTURE": pose["jawOpen"] = 0.22 * arc
		"EXCITED", "HOME", "VICTORY": pose["jawOpen"] = 0.25 * arc
		"HAPPY": pose["jawOpen"] = 0.12 * arc
		_: pose["jawOpen"] = 0.0
	return pose
