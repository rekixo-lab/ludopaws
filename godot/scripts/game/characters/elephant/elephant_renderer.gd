@tool
extends RefCounted
## Elephant: columnar legs with nails, fluttering ears, tusks and a shader-curled trunk.
## Port of ElephantRenderer.kt.

const G = preload("res://scripts/game/board3d/paw_geometry.gd")
const P = preload("res://scripts/game/characters/shared/pose.gd")
const Hop = preload("res://scripts/game/characters/shared/hop_dynamics.gd")
const M = preload("res://scripts/game/characters/elephant/elephant_geometry.gd")
const Mat = preload("res://scripts/game/characters/shared/pet_jump.gd")

static func step_travel_progress(progress: float) -> float:
	return Hop.travel(progress)

static func sample(motion: String, progress: float, playback := P.ISOLATED) -> Dictionary:
	var phase := clampf(progress, 0.0, 1.0)
	var wave := sin(phase * PI * 2.0)
	var anatomy_phase := 1.0 if motion == P.HOP and playback == P.SETTLE else phase
	var arc := maxf(sin(step_travel_progress(anatomy_phase) * PI), 0.0)
	var pose := {}
	match motion:
		P.IDLE:
			pose = P.pose({"headTiltDegrees": 2.0 * wave, "primaryAppendageDegrees": 5.0 * wave,
				"secondaryAppendageDegrees": 6.0 * wave, "tertiaryAppendageDegrees": 8.0 * wave})
		P.HOP:
			pose = P.pose({"legSwingDegrees": 18.0 * wave * arc, "legTuckDegrees": 20.0 * arc,
				"primaryAppendageDegrees": 20.0 * arc, "secondaryAppendageDegrees": 32.0 * arc,
				"tertiaryAppendageDegrees": 16.0 * arc, "bodyPitchDegrees": -4.0 * arc})
		_:
			pose = P.pose({"liftY": 0.12 * absf(sin(phase * PI * 3.0)) * (1.0 - phase),
				"bodyYawDegrees": 360.0 * phase, "primaryAppendageDegrees": 24.0 * wave,
				"secondaryAppendageDegrees": 62.0 * sin(phase * PI), "tertiaryAppendageDegrees": 22.0 * wave})
	return Hop.apply(pose, motion, progress, 0.40, playback)

static func draw(ctx, parent: Transform3D, pose: Dictionary, _team: String) -> void:
	var previous: String = ctx.surface
	var rig := G.rotate(parent, -12.0, 0, 1, 0)
	_legs(ctx, rig, pose)
	var torso := G.translate(rig, 0, -pose["bodyCrouch"], 0)
	torso = G.rotate(torso, pose["bodyPitchDegrees"], 1, 0, 0)
	torso = G.rotate(torso, pose["bodyRollDegrees"], 0, 0, 1)
	var breath: float = 1.0 + (ctx.life.breath if ctx.life != null else 0.0)
	ctx.surface = "ELEPHANT_HIDE"
	ctx.draw_geometry(torso, M.mesh("body"), Vector3.ZERO, Vector3(breath, 1, breath), M.SKIN)
	_tail(ctx, torso, pose["tertiaryAppendageDegrees"])
	var neck := G.translate(torso, 0, 0.34, 0.10)
	neck = G.rotate(neck, pose["neckPitchDegrees"], 1, 0, 0)
	neck = G.translate(neck, 0, -0.34, -0.10)
	ctx.draw_geometry(neck, M.mesh("neck"), Vector3.ZERO, Vector3.ONE, M.SKIN)
	ctx.prepare_head(neck, 0.92, pose["headTiltDegrees"])
	_face(ctx, ctx.head, pose)
	ctx.surface = previous

static func _legs(ctx, parent: Transform3D, pose: Dictionary) -> void:
	for leg_index in 4:
		var rear := leg_index < 2
		var side := -1.0 if leg_index % 2 == 0 else 1.0
		var leg := G.translate(parent, side * 0.30, -0.23, -0.43 if rear else 0.28)
		var swing: float = (-side if rear else side) * pose["legSwingDegrees"]
		var tuck := clampf(pose["legTuckDegrees"] / 38.0, 0.0, 1.0)
		leg = G.rotate(leg, swing, 1, 0, 0)
		leg = G.scale(leg, 1, 1.0 - 0.30 * tuck, 1)
		ctx.surface = "ELEPHANT_HIDE"
		ctx.draw_geometry(leg, M.mesh("leg"), Vector3.ZERO, Vector3.ONE, M.SKIN)
		ctx.draw_geometry(leg, M.mesh("foot"), Vector3(0, -0.545, 0.02), Vector3.ONE, M.SKIN)
		for nail_index in range(-1, 2):
			ctx.draw_part(leg, Vector3(nail_index * 0.078, -0.56, 0.161), Vector3(0.034, 0.043, 0.018), M.NAIL)

static func _face(ctx, parent: Transform3D, pose: Dictionary) -> void:
	var life = ctx.life
	for side in [-1.0, 1.0]:
		var ear := G.translate(parent, side * 0.34, 0.83, -0.035)
		var idle: float = life.idle_weight if life != null else 0.0
		var flutter: float = sin((life.time_seconds if life != null else 0.0) * 2.2 + side * 0.9) * 5.0 * idle
		ear = G.rotate(ear, side * (12.0 + pose["primaryAppendageDegrees"] + flutter), 0, 1, 0)
		ear = G.rotate(ear, side * (4.0 + flutter * 0.65), 0, 0, 1)
		ctx.surface = "ELEPHANT_HIDE"
		ctx.draw_geometry(ear, M.mesh("ear"), Vector3.ZERO, Vector3(side, 1, 1), M.SKIN)
		ctx.draw_geometry(ear, M.mesh("ear"), Vector3(side * 0.025, 0.005, 0.058), Vector3(side * 0.78, 0.80, 0.35), M.INNER_EAR)
	ctx.surface = "ELEPHANT_HIDE"
	ctx.draw_geometry(parent, M.mesh("head"), Vector3.ZERO, Vector3.ONE, M.SKIN)
	for side in [-1.0, 1.0]:
		ctx.surface = "EYE"
		ctx.draw_living_eye(parent, side * 0.235, 0.88, 0.325, 0.085, 0.10, M.SKIN, Mat.WHITE, Mat.EYE_DARK, false, false)
		ctx.surface = "HORN"
		ctx.draw_geometry(parent, M.mesh("tusk"), Vector3(side * 0.195, 0.58, 0.31), Vector3.ONE, M.IVORY, Vector3(90, side * 10.0, 0))
	ctx.surface = "MATTE"
	var opening := clampf((life.mouth_open if life != null else 0.0) + pose["jawOpen"], 0.0, 1.0)
	ctx.draw_part(parent, Vector3(0, 0.49, 0.34), Vector3(0.13, 0.012 + opening * 0.03, 0.018), M.SHADOW)
	_trunk(ctx, parent, pose["secondaryAppendageDegrees"] + 32.0 * pose["gestureWeight"])

static func _trunk(ctx, parent: Transform3D, curl: float) -> void:
	var trunk := G.translate(parent, 0, 0.79, 0.345)
	trunk = G.rotate(trunk, -curl * 0.16, 1, 0, 0)
	var curve := clampf(0.91 + curl * 0.021, 0.52, 2.65)
	ctx.surface = "ELEPHANT_TRUNK"
	ctx.limb_bend = Vector2(curve, 0)
	ctx.draw_geometry(trunk, M.mesh("trunk"), Vector3.ZERO, Vector3.ONE, M.SKIN)
	ctx.limb_bend = Vector2.ZERO
	# The nose openings share the exact end frame used by the vertex shader.
	var angle := M.TRUNK_LENGTH * curve
	var tip := G.translate(trunk, 0, -sin(angle) / curve, (1.0 - cos(angle)) / curve)
	tip = G.rotate(tip, -rad_to_deg(angle), 1, 0, 0)
	ctx.surface = "NOSE"
	for side in [-1.0, 1.0]:
		ctx.draw_part(tip, Vector3(side * 0.022, -0.002, 0.008), Vector3(0.012, 0.006, 0.015), M.SHADOW)

static func _tail(ctx, parent: Transform3D, sway: float) -> void:
	var tail := G.translate(parent, 0.29, 0.10, -0.64)
	tail = G.rotate(tail, -25.0 + sway, 0, 0, 1)
	ctx.surface = "ELEPHANT_HIDE"
	ctx.draw_part(tail, Vector3(0, -0.15, 0), Vector3(0.028, 0.18, 0.028), M.SKIN)
	ctx.draw_part(tail, Vector3(0, -0.32, 0), Vector3(0.055, 0.08, 0.04), M.SHADOW)
