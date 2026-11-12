@tool
extends RefCounted
## Per-pawn presentation life: breathing, blinks, gaze, idle gestures and reaction springs.
## Port of feature/characters/shared/LudoPaws3DLifeMotion.kt. Never changes gameplay.

const P = preload("res://scripts/game/characters/shared/pose.gd")
const Living = preload("res://scripts/game/characters/shared/living_behavior.gd")

class Spring:
	var value := 0.0
	var velocity := 0.0

	func step(target: float, dt: float, stiffness := 110.0) -> float:
		# Substeps keep secondary motion stable at both 30 and 60 fps.
		var count := maxi(ceili(dt / 0.008), 1)
		var h := dt / count
		for i in count:
			velocity += ((target - value) * stiffness - velocity * sqrt(stiffness) * 1.65) * h
			value += velocity * h
		return value

var _channels: Array[Spring] = []
var _primary := Spring.new()
var _secondary := Spring.new()
var _tertiary := Spring.new()
var _stance := Spring.new()
var _gaze := Spring.new()
var _gaze_vertical := Spring.new()
var _frame_dt := 1.0 / 60.0
var _last_ms := 0
var _seed := 0

var time_seconds := 0.0
var is_idle := false
var idle_weight := 0.0
var blink := 1.0
var breath := 0.0
var head_tilt := 0.0
var head_pitch := 0.0
var mouth_open := 0.0
var shake := 0.0
var gaze_x := 0.0
var gaze_y := 0.0
var reduced_motion := false

func _init() -> void:
	for i in 10:
		_channels.append(Spring.new())

func follow_primary(target: float) -> float:
	return _primary.step(target, _frame_dt, 120.0)

func follow_secondary(target: float) -> float:
	return _secondary.step(target, _frame_dt, 72.0)

func follow_tertiary(target: float) -> float:
	return _tertiary.step(target, _frame_dt, 90.0)

func follow_stance(target: float) -> float:
	return clampf(_stance.step(target, _frame_dt, 24.0), 0.0, 1.0)

func idle_pose(pose: Dictionary, character_id := "") -> Dictionary:
	var faded := P.copy(pose, {
		"bodyYawDegrees": pose["bodyYawDegrees"] * idle_weight,
		"headTiltDegrees": pose["headTiltDegrees"] * idle_weight,
		"bodyPitchDegrees": pose["bodyPitchDegrees"] * idle_weight,
		"bodyRollDegrees": pose["bodyRollDegrees"] * idle_weight,
		"neckPitchDegrees": pose["neckPitchDegrees"] * idle_weight,
		"primaryAppendageDegrees": pose["primaryAppendageDegrees"] * idle_weight,
		"secondaryAppendageDegrees": pose["secondaryAppendageDegrees"] * idle_weight,
		"tertiaryAppendageDegrees": pose["tertiaryAppendageDegrees"] * idle_weight,
	})
	if character_id.is_empty():
		return faded
	return Living.blend_idle(faded, character_id, time_seconds, _seed, idle_weight)

static func _closure(p: float) -> float:
	return pow(sin(PI * p), 2) if p >= 0.0 and p <= 1.0 else 0.0

func update(now_ms: int, seed: int, idle: bool, target_x: float, target_y: float, target: Dictionary) -> Dictionary:
	var dt := 1.0 / 60.0 if _last_ms == 0 else clampf((now_ms - _last_ms) / 1000.0, 0.0, 0.064)
	_last_ms = now_ms
	_frame_dt = dt
	_seed = seed
	var t := 0.0 if reduced_motion else (now_ms % 3600000) / 1000.0 + (seed & 65535) * 0.0137
	time_seconds = t
	is_idle = idle
	var idle_target := 1.0 if idle and not reduced_motion else 0.0
	var idle_tau := 0.25 if idle else 0.08
	idle_weight += (idle_target - idle_weight) * (1.0 - exp(-dt / idle_tau))
	breath = sin(t * 2.1) * 0.010 + sin(t * 4.2 + 0.7) * 0.002
	var blink_time := fmod(t, 3.7 + (seed & 7) * 0.17)
	blink = 1.0 - maxf(_closure(blink_time / 0.19), _closure((blink_time - 0.31) / 0.16) if (seed & 3) == 0 else 0.0) * 0.97
	var cycle := floori(t / 8.7)
	var p := fmod(t, 8.7) / 8.7
	var gesture := pow(sin(p * PI), 12) * idle_weight
	var kind := posmod(cycle + seed, 4)
	head_tilt = sin(t * 0.67) * 1.8 * idle_weight
	head_pitch = gesture * (9.0 + sin(t * 16.0) * 3.0) if kind == 0 else (-gesture * 12.0 if kind == 1 else 0.0)
	mouth_open = gesture if kind == 1 else 0.0
	shake = sin(t * 26.0) * gesture * 5.0 if kind == 2 else 0.0
	head_tilt += gesture * 9.0 if kind == 3 else 0.0
	gaze_x = _gaze.step(clampf(target_x, -1.0, 1.0) * 0.038, dt, 75.0)
	gaze_y = _gaze_vertical.step(clampf(target_y, -1.0, 1.0) * 0.026, dt, 75.0)
	# Normalize full-turn reactions before blending so finishing a spin cannot rewind it.
	var yaw: float = target["bodyYawDegrees"]
	var yaw_target := _channels[1].value + (fposmod(yaw - _channels[1].value + 540.0, 360.0) - 180.0)
	return P.reaction({
		"liftY": _channels[0].step(target["liftY"], dt, 155.0),
		"bodyYawDegrees": _channels[1].step(yaw_target, dt, 145.0),
		"headTiltDegrees": _channels[2].step(target["headTiltDegrees"], dt),
		"primaryAppendageDegrees": _channels[3].step(target["primaryAppendageDegrees"], dt, 80.0),
		"secondaryAppendageDegrees": _channels[4].step(target["secondaryAppendageDegrees"], dt, 65.0),
		"tertiaryAppendageDegrees": _channels[5].step(target["tertiaryAppendageDegrees"], dt, 70.0),
		"scale": 1.0 + _channels[6].step(target["scale"] - 1.0, dt, 130.0),
		"bodyPitchDegrees": _channels[7].step(target["bodyPitchDegrees"], dt, 120.0),
		"neckPitchDegrees": _channels[8].step(target["neckPitchDegrees"], dt, 90.0),
		"jawOpen": _channels[9].step(target["jawOpen"], dt, 100.0),
	})
