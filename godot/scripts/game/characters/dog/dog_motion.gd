@tool
extends RefCounted
## Dog motion timeline and reaction profile. Port of Dog3DMotion.kt and DogReactionMotion.kt.

const P = preload("res://scripts/game/characters/shared/pose.gd")
const Hop = preload("res://scripts/game/characters/shared/hop_dynamics.gd")

const IDLE_DURATION_MS := 1800
const HOP_DURATION_MS := 680
const HOME_DURATION_MS := 1300
const REACTION_DURATION_SCALE := 1.0

static func step_travel_progress(progress: float) -> float:
	return Hop.travel(progress)

## Dog3DPose as a dict: liftY, bodyYawDegrees, headTiltDegrees, earBounceDegrees, tailWagDegrees,
## legSwingDegrees, legTuckDegrees, bodyPitchDegrees, neckPitchDegrees, bodyCrouch and the four nullable leg channels.
static func sample(motion: String, progress: float) -> Dictionary:
	var p := clampf(progress, 0.0, 1.0)
	var out := {"liftY": 0.0, "bodyYawDegrees": 0.0, "headTiltDegrees": 0.0, "earBounceDegrees": 0.0,
		"tailWagDegrees": 0.0, "legSwingDegrees": 0.0, "legTuckDegrees": 0.0, "bodyPitchDegrees": 0.0,
		"neckPitchDegrees": 0.0, "bodyCrouch": 0.0, "frontLegTuckDegrees": null, "rearLegTuckDegrees": null,
		"frontLegSwingDegrees": null, "rearLegSwingDegrees": null}
	match motion:
		P.IDLE:
			var wave := sin(p * PI * 2.0)
			var tail := sin(p * PI * 4.0)
			out.merge({"headTiltDegrees": 2.5 * wave, "earBounceDegrees": 2.0 * -wave,
				"neckPitchDegrees": 1.5 * -wave, "tailWagDegrees": 24.0 * tail}, true)
		P.HOP:
			var flight := step_travel_progress(p)
			var arc := maxf(sin(flight * PI), 0.0)
			var front := _pulse(p, 0.16, 0.30, 0.55, 0.76)
			var rear := _pulse(p, 0.24, 0.40, 0.70, 0.90)
			var preload_w := _pulse(p, 0.0, 0.08, 0.12, 0.20)
			var land := _pulse(p, 0.80, 0.87, 0.90, 1.0)
			var tail := sin(p * PI * 3.0)
			out.merge({
				"liftY": 0.58 * 4.0 * flight * (1.0 - flight),
				"bodyCrouch": 0.065 * preload_w + 0.05 * land,
				"bodyPitchDegrees": -4.0 * front * (1.0 - _smooth(p, 0.35, 0.55)) + 3.0 * land,
				"neckPitchDegrees": 3.0 * arc - 4.0 * land,
				"frontLegTuckDegrees": 32.0 * front,
				"rearLegTuckDegrees": 29.0 * rear,
				"frontLegSwingDegrees": (16.0 - 30.0 * _smooth(p, 0.40, 0.71)) * front,
				"rearLegSwingDegrees": (-16.0 + 28.0 * _smooth(p, 0.43, 0.70)) * rear,
				"headTiltDegrees": -6.0 * arc,
				"earBounceDegrees": 18.0 * arc,
				"tailWagDegrees": 28.0 * tail,
				"legSwingDegrees": 28.0 * sin(p * PI * 2.0) * arc,
				"legTuckDegrees": 34.0 * arc,
			}, true)
		P.HOME:
			var bounce := absf(sin(p * PI * 3.0)) * (1.0 - p * 0.30)
			out.merge({
				"liftY": 0.30 * bounce,
				"bodyYawDegrees": 360.0 * p,
				"headTiltDegrees": 7.0 * sin(p * PI * 2.0),
				"earBounceDegrees": 12.0 * maxf(sin(p * PI), 0.0),
				"tailWagDegrees": 48.0 * sin(p * PI * 6.0),
				"legSwingDegrees": 12.0 * sin(p * PI * 6.0),
				"legTuckDegrees": 9.0 * maxf(sin(p * PI * 3.0), 0.0),
			}, true)
	return out

static func _smooth(p: float, start: float, end: float) -> float:
	var t := clampf((p - start) / (end - start), 0.0, 1.0)
	return t * t * (3.0 - 2.0 * t)

static func _pulse(p: float, a: float, b: float, c: float, d: float) -> float:
	return _smooth(p, a, b) * (1.0 - _smooth(p, c, d))

## DogReactionMotion.sample; frame = {p, arc, wave, fast, bounce}.
static func reaction(cue: String, f: Dictionary) -> Dictionary:
	var arc: float = f["arc"]
	var wave: float = f["wave"]
	var fast: float = f["fast"]
	var bounce: float = f["bounce"]
	var p: float = f["p"]
	var r := {}
	match cue:
		"IDLE": r = {"headTiltDegrees": 7.0 * wave, "primaryAppendageDegrees": 5.0 * -wave, "secondaryAppendageDegrees": 30.0 * fast}
		"EXCITED": r = {"liftY": 0.24 * bounce, "headTiltDegrees": -7.0 * arc, "primaryAppendageDegrees": 15.0 * arc, "secondaryAppendageDegrees": 42.0 * fast, "scale": 1.0 + 0.045 * arc}
		"HAPPY", "SAFE": r = {"liftY": 0.13 * arc, "bodyYawDegrees": 8.0 * wave, "headTiltDegrees": 6.0 * wave, "primaryAppendageDegrees": 7.0 * arc, "secondaryAppendageDegrees": 34.0 * fast}
		"CAPTURE": r = {"liftY": 0.19 * arc, "bodyYawDegrees": 18.0 * wave, "headTiltDegrees": -10.0 * arc, "primaryAppendageDegrees": 12.0 * arc, "secondaryAppendageDegrees": 46.0 * fast, "scale": 1.0 + 0.055 * arc}
		"CAPTURED": r = {"liftY": 0.12 * arc, "bodyYawDegrees": 16.0 * fast * (1.0 - p), "headTiltDegrees": 13.0 * wave, "primaryAppendageDegrees": -9.0 * arc, "secondaryAppendageDegrees": 8.0 * wave, "scale": 1.0 - 0.055 * arc}
		"SAD", "DEFEAT": r = {"liftY": -0.055 * arc, "headTiltDegrees": 13.0 * arc, "primaryAppendageDegrees": -8.0 * arc, "secondaryAppendageDegrees": 7.0 * wave, "scale": 1.0 - 0.035 * arc}
		"ANGRY": r = {"liftY": 0.045 * bounce, "bodyYawDegrees": 11.0 * fast * (1.0 - p), "headTiltDegrees": -8.0 * arc, "primaryAppendageDegrees": 7.0 * fast, "secondaryAppendageDegrees": 16.0 * fast}
		"NERVOUS": r = {"bodyYawDegrees": 6.0 * fast * (1.0 - p), "headTiltDegrees": 8.0 * wave, "primaryAppendageDegrees": 5.0 * fast, "secondaryAppendageDegrees": 10.0 * wave}
		"HOME": r = {"liftY": 0.16 * bounce, "headTiltDegrees": 7.0 * wave, "primaryAppendageDegrees": 11.0 * arc, "secondaryAppendageDegrees": 44.0 * fast, "scale": 1.0 + 0.035 * arc}
		"VICTORY": r = {"liftY": 0.22 * bounce, "bodyYawDegrees": 360.0 * p, "headTiltDegrees": 7.0 * wave, "primaryAppendageDegrees": 13.0 * arc, "secondaryAppendageDegrees": 52.0 * fast, "scale": 1.0 + 0.05 * arc}
	var pose := P.reaction(r)
	pose["scale"] = 1.0 + (pose["scale"] - 1.0) * 0.45
	match cue:
		"SAD", "DEFEAT": pose["neckPitchDegrees"] = 10.0 * arc
		"CAPTURE", "ANGRY": pose["neckPitchDegrees"] = 7.0 * arc
		"EXCITED", "HAPPY", "VICTORY": pose["neckPitchDegrees"] = -4.0 * arc
		_: pose["neckPitchDegrees"] = 0.0
	match cue:
		"EXCITED", "HOME", "VICTORY": pose["jawOpen"] = 0.35 * arc
		"HAPPY", "SAFE": pose["jawOpen"] = 0.18 * arc
		"CAPTURED": pose["jawOpen"] = 0.26 * arc
		"CAPTURE", "ANGRY": pose["jawOpen"] = 0.15 * arc
		_: pose["jawOpen"] = 0.0
	return pose
