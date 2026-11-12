@tool
extends RefCounted
## Bounded secondary gestures; never changes feet, route or turn timing.
## Port of feature/characters/shared/CharacterLivingBehavior.kt.

const P = preload("res://scripts/game/characters/shared/pose.gd")

## id: [period, head, neck, primary, secondary, tertiary]
const PROFILES := {
	"dog": [9.5, 7.0, 7.0, 12.0, 24.0, 0.0],
	"goat": [10.8, 8.0, 8.0, 12.0, 5.0, 13.0],
	"goat_lumi": [12.7, 6.0, 5.0, 18.0, 0.0, 10.0],
	"duck": [10.2, 6.0, 11.0, 16.0, 0.0, 0.0],
	"cat": [12.1, 9.0, 6.0, 9.0, 24.0, 0.0],
	"donkey": [13.3, 7.0, 8.0, 14.0, 21.0, 0.0],
	"cock": [8.8, 8.0, 14.0, 20.0, 8.0, 0.0],
	"pig": [11.3, 7.0, 11.0, 10.0, 20.0, 0.0],
	"elephant": [15.1, 4.0, 3.0, 12.0, 27.0, 12.0],
	"horse": [11.8, 6.0, 9.0, 13.0, 25.0, 0.0],
	"rabbit": [9.1, 8.0, 10.0, 19.0, 7.0, 0.0],
	"panda": [14.3, 6.0, 6.0, 22.0, 9.0, 0.0],
	"monkey": [8.3, 10.0, 8.0, 28.0, 16.0, 30.0],
	"giraffe": [14.7, 7.0, 11.0, 18.0, 19.0, 0.0],
}

static func idle_duration_ms(base: int, seed: int) -> int:
	var speed: float = [0.89, 1.03, 1.14, 0.96][posmod(seed, 4)]
	return maxi(int(base * speed), 1)

static func blend_idle(pose: Dictionary, character_id: String, seconds: float, seed: int, weight: float) -> Dictionary:
	if not PROFILES.has(character_id):
		return pose
	var profile: Array = PROFILES[character_id]
	var strength := clampf(weight, 0.0, 1.0)
	if strength <= 0.0:
		return pose
	var period: float = profile[0]
	var t := seconds + posmod(seed, 97) * 0.173
	var cycle := floori(t / period)
	var phase := t / period - floorf(t / period)
	var window := clampf(phase / 0.42, 0.0, 1.0)
	var envelope := pow(sin(window * PI), 2) * strength
	var wave := sin(window * PI * 2.0)
	var kind := posmod(cycle + seed, 3)
	var glance := envelope if kind == 0 else envelope * 0.35
	var listen := envelope if kind == 1 else envelope * 0.25
	var flourish := envelope if kind == 2 else envelope * 0.18
	var pitch := 0.0
	if character_id == "dog":
		pitch = 13.0 * flourish
	elif character_id == "duck":
		pitch = 5.0 * flourish
	return P.copy(pose, {
		"headTiltDegrees": pose["headTiltDegrees"] + profile[1] * wave * glance,
		"neckPitchDegrees": pose["neckPitchDegrees"] + profile[2] * (1.0 if kind == 0 else -0.35) * glance,
		"primaryAppendageDegrees": pose["primaryAppendageDegrees"] + profile[3] * wave * listen,
		"secondaryAppendageDegrees": pose["secondaryAppendageDegrees"] + profile[4] * wave * flourish,
		"tertiaryAppendageDegrees": pose["tertiaryAppendageDegrees"] + profile[5] * wave * flourish,
		"gestureWeight": envelope if kind == 2 else 0.0,
		"gesturePhase": window,
		"bodyPitchDegrees": pose["bodyPitchDegrees"] + pitch,
	})

static func accent_reaction(base: Dictionary, character_id: String, cue: String, progress: float, variant: int) -> Dictionary:
	if not PROFILES.has(character_id):
		return base
	var profile: Array = PROFILES[character_id]
	var p := clampf(progress, 0.0, 1.0)
	var envelope := maxf(sin(p * PI), 0.0)
	var wave := sin(p * PI * (2.0 if variant % 2 == 0 else 4.0)) * envelope
	var positive := cue in ["HAPPY", "EXCITED", "HOME", "VICTORY", "SAFE"]
	var lively := 0.32 if positive else 0.14
	return P.copy(base, {
		"primaryAppendageDegrees": base["primaryAppendageDegrees"] + profile[3] * wave * lively,
		"secondaryAppendageDegrees": base["secondaryAppendageDegrees"] + profile[4] * wave * lively,
		"tertiaryAppendageDegrees": base["tertiaryAppendageDegrees"] + profile[5] * wave * lively,
		"neckPitchDegrees": base["neckPitchDegrees"] + profile[2] * envelope * (-0.25 if positive else 0.25),
	})
