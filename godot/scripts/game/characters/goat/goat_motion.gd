@tool
extends RefCounted
## Goat motion timeline and reaction profile. Port of Goat3DMotion.kt and GoatReactionMotion.kt.

const P = preload("res://scripts/game/characters/shared/pose.gd")
const Hop = preload("res://scripts/game/characters/shared/hop_dynamics.gd")

const IDLE_DURATION_MS := 2000
const HOP_DURATION_MS := 700
const HOME_DURATION_MS := 1350
const REACTION_DURATION_SCALE := 1.04

static func step_travel_progress(progress: float) -> float:
	return Hop.travel(progress)

static func _smooth(p: float, start: float, end: float) -> float:
	var t := clampf((p - start) / (end - start), 0.0, 1.0)
	return t * t * (3.0 - 2.0 * t)

static func _pair(p: float, start: float, peak: float, release: float, contact: float) -> float:
	return _smooth(p, start, peak) * (1.0 - _smooth(p, release, contact))

static func _gauss(p: float, center: float, width: float) -> float:
	var d := (p - center) / width
	return exp(-d * d)

## Goat3DPose: earFlick -> primary, beardSwing -> secondary, tailFlick -> tertiary.
static func sample(motion: String, progress: float) -> Dictionary:
	var p := clampf(progress, 0.0, 1.0)
	var out := {"liftY": 0.0, "bodyYawDegrees": 0.0, "headTiltDegrees": 0.0, "earFlickDegrees": 0.0,
		"beardSwingDegrees": 0.0, "tailFlickDegrees": 0.0, "legSwingDegrees": 0.0, "legTuckDegrees": 0.0,
		"bodyPitchDegrees": 0.0, "bodyRollDegrees": 0.0, "neckPitchDegrees": 0.0, "bodyCrouch": 0.0,
		"frontLegTuckDegrees": null, "rearLegTuckDegrees": null, "frontLegSwingDegrees": null, "rearLegSwingDegrees": null}
	match motion:
		P.IDLE:
			var wave := sin(p * PI * 2.0)
			var ear := 5.0 * _gauss(p, 0.25, 0.055) - 4.0 * _gauss(p, 0.7, 0.075)
			var tail := 10.0 * _gauss(p, 0.25, 0.09) - 8.0 * _gauss(p, 0.73, 0.11)
			var envelope := sin(p * PI) * sin(p * PI)
			var glance := _gauss(p, 0.62, 0.12) * envelope
			var sniff := _gauss(p, 0.40, 0.07) * envelope
			out.merge({"bodyYawDegrees": 2.5 * glance, "headTiltDegrees": 2.2 * wave + 5.0 * glance,
				"earFlickDegrees": ear, "beardSwingDegrees": 3.5 * -wave, "tailFlickDegrees": tail,
				"bodyRollDegrees": 0.65 * wave, "neckPitchDegrees": 0.8 * -wave + 6.0 * sniff - 2.0 * glance,
				"bodyPitchDegrees": 1.2 * sniff}, true)
		P.HOP:
			var front := _pair(p, 0.16, 0.30, 0.51, 0.76)
			var rear := _pair(p, 0.24, 0.44, 0.70, 0.90)
			var flight := step_travel_progress(p)
			var arc := maxf(sin(flight * PI), 0.0)
			var wobble := sin(p * PI * 2.0)
			var preload_w := _pair(p, 0.0, 0.09, 0.12, 0.26)
			var landing := _pair(p, 0.74, 0.83, 0.86, 1.0)
			out.merge({
				"liftY": 0.60 * 4.0 * flight * (1.0 - flight) if p >= Hop.TAKEOFF and p <= Hop.CONTACT else 0.0,
				"headTiltDegrees": -5.5 * arc, "earFlickDegrees": 15.0 * arc - 5.0 * landing,
				"beardSwingDegrees": 18.0 * arc + 4.0 * wobble - 8.0 * landing, "tailFlickDegrees": 20.0 * wobble,
				"legSwingDegrees": 27.0 * wobble * front, "legTuckDegrees": 32.0 * arc,
				"frontLegTuckDegrees": 34.0 * front, "rearLegTuckDegrees": 30.0 * rear,
				"frontLegSwingDegrees": (18.0 - 36.0 * _smooth(p, 0.38, 0.68)) * front,
				"rearLegSwingDegrees": (-20.0 + 34.0 * _smooth(p, 0.40, 0.68)) * rear,
				"bodyPitchDegrees": -5.0 * front * (1.0 - _smooth(p, 0.35, 0.58)) + 4.0 * landing,
				"bodyRollDegrees": 0.0, "neckPitchDegrees": -4.0 * front + 6.0 * landing,
				"bodyCrouch": 0.08 * preload_w + 0.065 * landing,
			}, true)
		P.HOME:
			var bounce := absf(sin(p * PI * 3.0)) * (1.0 - p * 0.30)
			var s1 := sin(p * PI)
			out.merge({"liftY": 0.31 * bounce, "bodyYawDegrees": 360.0 * p,
				"headTiltDegrees": 8.0 * sin(p * PI * 2.0), "earFlickDegrees": 10.0 * sin(p * PI * 4.0),
				"beardSwingDegrees": 22.0 * sin(p * PI * 3.0), "tailFlickDegrees": 28.0 * sin(p * PI * 6.0),
				"legSwingDegrees": 11.0 * sin(p * PI * 6.0), "legTuckDegrees": 8.0 * maxf(sin(p * PI * 3.0), 0.0),
				"bodyPitchDegrees": -4.0 * sin(p * PI * 6.0) * s1, "neckPitchDegrees": 3.0 * sin(p * PI * 6.0) * s1,
				"bodyCrouch": 0.035 * (1.0 - clampf(bounce, 0.0, 1.0)) * s1 * s1}, true)
	return out

static func reaction(cue: String, f: Dictionary) -> Dictionary:
	var p: float = f["p"]
	var arc: float = f["arc"]
	var wave: float = f["wave"]
	var fast: float = f["fast"]
	var bounce: float = f["bounce"]
	var wind := sin(clampf(p / 0.3, 0.0, 1.0) * PI)
	var r := {}
	match cue:
		"IDLE": r = {"headTiltDegrees": 10.0 * wave, "primaryAppendageDegrees": 7.0 * fast, "secondaryAppendageDegrees": 6.0 * -wave, "tertiaryAppendageDegrees": 9.0 * fast}
		"EXCITED", "HAPPY": r = {"liftY": 0.22 * bounce, "headTiltDegrees": -6.0 * arc + 4.0 * wave, "primaryAppendageDegrees": 14.0 * arc, "secondaryAppendageDegrees": 13.0 * wave, "tertiaryAppendageDegrees": 18.0 * fast}
		"SAFE": r = {"liftY": 0.08 * arc, "headTiltDegrees": 7.0 * wave, "primaryAppendageDegrees": 5.0 * wave, "secondaryAppendageDegrees": 5.0 * -wave}
		"CAPTURE": r = {"liftY": 0.13 * arc, "bodyYawDegrees": 13.0 * wave, "headTiltDegrees": -16.0 * arc, "primaryAppendageDegrees": 11.0 * arc, "secondaryAppendageDegrees": 18.0 * arc, "tertiaryAppendageDegrees": 16.0 * fast, "scale": 1.0 + 0.04 * arc}
		"CAPTURED": r = {"liftY": 0.11 * arc, "bodyYawDegrees": 13.0 * fast * (1.0 - p), "headTiltDegrees": 11.0 * wave, "primaryAppendageDegrees": -7.0 * arc, "secondaryAppendageDegrees": 15.0 * wave, "tertiaryAppendageDegrees": -10.0 * arc, "scale": 1.0 - 0.045 * arc}
		"SAD", "DEFEAT": r = {"liftY": -0.05 * arc, "headTiltDegrees": 12.0 * arc, "primaryAppendageDegrees": -7.0 * arc, "secondaryAppendageDegrees": 10.0 * arc, "tertiaryAppendageDegrees": -7.0 * arc}
		"ANGRY": r = {"liftY": 0.035 * bounce, "bodyYawDegrees": 7.0 * fast * (1.0 - p), "headTiltDegrees": -12.0 * arc, "primaryAppendageDegrees": 8.0 * fast, "secondaryAppendageDegrees": 13.0 * fast, "tertiaryAppendageDegrees": 11.0 * fast}
		"NERVOUS": r = {"bodyYawDegrees": 5.0 * fast * (1.0 - p), "headTiltDegrees": 9.0 * wave, "primaryAppendageDegrees": 8.0 * fast, "secondaryAppendageDegrees": 7.0 * wave}
		"HOME": r = {"liftY": 0.15 * bounce, "headTiltDegrees": 8.0 * wave, "primaryAppendageDegrees": 10.0 * fast, "secondaryAppendageDegrees": 17.0 * wave, "tertiaryAppendageDegrees": 22.0 * fast, "scale": 1.0 + 0.03 * arc}
		"VICTORY": r = {"liftY": 0.20 * bounce, "bodyYawDegrees": 360.0 * p, "headTiltDegrees": 8.0 * wave, "primaryAppendageDegrees": 12.0 * fast, "secondaryAppendageDegrees": 20.0 * wave, "tertiaryAppendageDegrees": 26.0 * fast, "scale": 1.0 + 0.04 * arc}
	var pose := P.reaction(r)
	pose["scale"] = 1.0 + (pose["scale"] - 1.0) * 0.40
	match cue:
		"CAPTURE": pose["neckPitchDegrees"] = 18.0 * arc - 8.0 * wind
		"CAPTURED": pose["neckPitchDegrees"] = -9.0 * arc
		"ANGRY": pose["neckPitchDegrees"] = 16.0 * arc + 2.0 * fast * arc
		"SAD", "DEFEAT": pose["neckPitchDegrees"] = 12.0 * arc
		"NERVOUS": pose["neckPitchDegrees"] = -5.0 * arc
		"HAPPY", "SAFE": pose["neckPitchDegrees"] = -4.0 * arc
		"VICTORY": pose["neckPitchDegrees"] = -8.0 * arc
		_: pose["neckPitchDegrees"] = -2.0 * arc
	match cue:
		"EXCITED", "HOME", "VICTORY": pose["jawOpen"] = 0.30 * arc * (0.75 + 0.25 * fast)
		"CAPTURED": pose["jawOpen"] = 0.34 * arc
		"CAPTURE", "ANGRY": pose["jawOpen"] = 0.20 * arc
		"HAPPY": pose["jawOpen"] = 0.10 * arc
		"SAD", "DEFEAT": pose["jawOpen"] = 0.06 * arc * (0.5 + 0.5 * fast)
		_: pose["jawOpen"] = 0.0
	match cue:
		"CAPTURE": pose["bodyPitchDegrees"] = 8.0 * arc - 6.0 * wind
		"CAPTURED": pose["bodyPitchDegrees"] = -5.0 * arc
		"ANGRY": pose["bodyPitchDegrees"] = 3.0 * arc
		"SAD", "DEFEAT": pose["bodyPitchDegrees"] = 2.0 * arc
		_: pose["bodyPitchDegrees"] = 0.0
	return pose
