@tool
extends RefCounted
## Shared cell-hop weight. Port of feature/characters/shared/CharacterHopDynamics.kt.

const P = preload("res://scripts/game/characters/shared/pose.gd")

const TAKEOFF := 0.18
const CONTACT := 0.78
const CONTACT_SQUASH := 0.17
const CONTACT_CROUCH := 0.085

static func _phase(progress: float) -> float:
	return clampf((progress - TAKEOFF) / (CONTACT - TAKEOFF), 0.0, 1.0)

## Zero velocity AND acceleration at the planted endpoints.
static func travel(progress: float) -> float:
	return smoother(_phase(progress))

static func smoother(value: float) -> float:
	var t := clampf(value, 0.0, 1.0)
	var u := minf(t, 1.0 - t)
	var shaped := u * u * u * (10.0 + u * (-15.0 + 6.0 * u))
	return shaped if t <= 0.5 else 1.0 - shaped

static func _ease(progress: float, start: float, end: float) -> float:
	return smoother((progress - start) / (end - start))

static func apply(base: Dictionary, motion: String, progress: float, height: float, playback := P.ISOLATED) -> Dictionary:
	if motion != P.HOP:
		return base
	var p := clampf(progress, 0.0, 1.0)
	if playback == P.SETTLE:
		var contact_s := 1.0 - smoother(p)
		return P.copy(base, {
			"liftY": 0.0, "bodyScaleY": 1.0 - CONTACT_SQUASH * contact_s, "bodyCrouch": CONTACT_CROUCH * contact_s,
			"bodyPitchDegrees": 3.0 * contact_s, "neckPitchDegrees": -3.0 * contact_s,
			"headTiltDegrees": 0.0, "bodyRollDegrees": 0.0,
			"primaryAppendageDegrees": 0.0, "secondaryAppendageDegrees": 0.0, "tertiaryAppendageDegrees": 0.0,
			"legSwingDegrees": 0.0, "legTuckDegrees": 0.0, "legPaddleDegrees": 0.0,
			"frontLegTuckDegrees": P.zero_if_set(base["frontLegTuckDegrees"]),
			"rearLegTuckDegrees": P.zero_if_set(base["rearLegTuckDegrees"]),
			"frontLegSwingDegrees": P.zero_if_set(base["frontLegSwingDegrees"]),
			"rearLegSwingDegrees": P.zero_if_set(base["rearLegSwingDegrees"]),
			"strideWeight": 0.0,
		})
	var chaining := playback == P.CHAIN
	var preload_w := 0.0 if chaining else _ease(p, 0.0, 0.14) * (1.0 - _ease(p, 0.14, 0.36))
	var carried := 1.0 - _ease(p, 0.0, 0.34) if chaining else 0.0
	var impact := _ease(p, 0.62, 0.80) * (1.0 - _ease(p, 0.80, 1.0)) if playback == P.ISOLATED else _ease(p, 0.62, 1.0)
	var contact := carried + impact
	var extension := _ease(p, 0.18, 0.38) * (1.0 - _ease(p, 0.48, 0.68))
	var flight := _phase(p)
	var arc := maxf(sin(flight * PI), 0.0)
	var front_release := _ease(p, 0.12, 0.28) * (1.0 - _ease(p, 0.58, CONTACT))
	var rear_release := _ease(p, 0.20, 0.38) * (1.0 - _ease(p, 0.68, 0.88))
	var anatomy := _ease(p, 0.10, 0.30) * (1.0 - _ease(p, 0.54, CONTACT))
	var appendages := _ease(p, 0.0, 0.14) * (1.0 - _ease(p, 0.80, 1.0))
	return P.copy(base, {
		"liftY": height * arc * arc,
		"bodyScaleY": 1.0 - 0.16 * preload_w - CONTACT_SQUASH * contact + 0.035 * extension,
		"bodyCrouch": 0.085 * preload_w + CONTACT_CROUCH * contact,
		"bodyPitchDegrees": base["bodyPitchDegrees"] * anatomy + 4.0 * preload_w + 3.0 * contact - 2.0 * extension,
		"neckPitchDegrees": base["neckPitchDegrees"] * anatomy + 3.0 * preload_w - 3.0 * contact,
		"headTiltDegrees": base["headTiltDegrees"] * anatomy,
		"bodyRollDegrees": base["bodyRollDegrees"] * anatomy,
		"primaryAppendageDegrees": base["primaryAppendageDegrees"] * appendages,
		"secondaryAppendageDegrees": base["secondaryAppendageDegrees"] * appendages,
		"tertiaryAppendageDegrees": base["tertiaryAppendageDegrees"] * appendages,
		"legSwingDegrees": base["legSwingDegrees"] * anatomy,
		"legPaddleDegrees": base["legPaddleDegrees"] * anatomy,
		"legTuckDegrees": base["legTuckDegrees"] * front_release,
		"frontLegTuckDegrees": P.times(base["frontLegTuckDegrees"], front_release),
		"rearLegTuckDegrees": P.times(base["rearLegTuckDegrees"], rear_release),
		"frontLegSwingDegrees": P.times(base["frontLegSwingDegrees"], front_release),
		"rearLegSwingDegrees": P.times(base["rearLegSwingDegrees"], rear_release),
		"strideWeight": base["strideWeight"] * _ease(p, 0.16, 0.32) * (1.0 - _ease(p, 0.62, 0.88)),
	})
