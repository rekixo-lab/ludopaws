@tool
extends RefCounted
## Rabbit: big hind paws, fore-arms, long double-layer ears, sniffing nose and buck teeth.
## Port of RabbitRenderer.kt.

const G = preload("res://scripts/game/board3d/paw_geometry.gd")
const P = preload("res://scripts/game/characters/shared/pose.gd")
const Hop = preload("res://scripts/game/characters/shared/hop_dynamics.gd")
const M = preload("res://scripts/game/characters/rabbit/rabbit_geometry.gd")
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
			pose = P.pose({"headTiltDegrees": 3.0 * wave, "primaryAppendageDegrees": 5.0 * wave,
				"secondaryAppendageDegrees": 3.0 * wave, "neckPitchDegrees": 2.0 * wave})
		P.HOP:
			pose = P.pose({"frontLegTuckDegrees": 30.0 * arc, "rearLegTuckDegrees": 36.0 * arc,
				"frontLegSwingDegrees": -18.0 * arc, "rearLegSwingDegrees": 24.0 * arc,
				"primaryAppendageDegrees": -24.0 * arc, "secondaryAppendageDegrees": 10.0 * arc,
				"bodyPitchDegrees": -7.0 * arc, "neckPitchDegrees": 5.0 * arc})
		_:
			pose = P.pose({"liftY": 0.22 * absf(sin(phase * PI * 3.0)) * (1.0 - phase),
				"bodyYawDegrees": 360.0 * phase, "primaryAppendageDegrees": 16.0 * wave,
				"headTiltDegrees": 7.0 * wave, "secondaryAppendageDegrees": 12.0 * wave})
	return Hop.apply(pose, motion, progress, 0.63, playback)

static func draw(ctx, parent: Transform3D, pose: Dictionary, _team: String) -> void:
	var previous: String = ctx.surface
	var rig := G.rotate(parent, -16.0, 0, 1, 0)
	_legs(ctx, rig, pose)
	var torso := G.translate(rig, 0, -pose["bodyCrouch"], 0)
	torso = G.rotate(torso, pose["bodyPitchDegrees"], 1, 0, 0)
	torso = G.rotate(torso, pose["bodyRollDegrees"], 0, 0, 1)
	var breath: float = 1.0 + (ctx.life.breath if ctx.life != null else 0.0)
	ctx.surface = "SOFT_FUR"
	ctx.draw_geometry(torso, M.mesh("body"), Vector3.ZERO, Vector3(breath, 1, breath), M.CREAM)
	ctx.draw_part(torso, Vector3(0, -0.01, 0.28), Vector3(0.25, 0.30, 0.11), M.WHITE)
	ctx.draw_part(torso, Vector3(0.25, -0.10 + pose["secondaryAppendageDegrees"] * 0.0015, -0.48), Vector3(0.17, 0.16, 0.17), M.WHITE,
		Vector3(0, 0, pose["secondaryAppendageDegrees"]))
	var neck := G.translate(torso, 0, 0.34, 0)
	neck = G.rotate(neck, pose["neckPitchDegrees"], 1, 0, 0)
	neck = G.translate(neck, 0, -0.34, 0)
	ctx.draw_geometry(neck, M.mesh("neck"), Vector3.ZERO, Vector3.ONE, M.CREAM)
	ctx.prepare_head(neck, 0.86, pose["headTiltDegrees"])
	_face(ctx, ctx.head, pose)
	ctx.surface = previous

static func _legs(ctx, parent: Transform3D, pose: Dictionary) -> void:
	ctx.surface = "SOFT_FUR"
	for side in [-1.0, 1.0]:
		var rear_tuck_value = pose["rearLegTuckDegrees"]
		var rear_tuck := clampf(float(rear_tuck_value if rear_tuck_value != null else pose["legTuckDegrees"]) / 38.0, 0.0, 1.0)
		var rear_swing = pose["rearLegSwingDegrees"]
		var hind := G.translate(parent, side * 0.28, -0.28, -0.23)
		hind = G.rotate(hind, float(rear_swing if rear_swing != null else pose["legSwingDegrees"]), 1, 0, 0)
		ctx.draw_part(hind, Vector3(0, -0.03 - pose["bodyCrouch"] * 0.35, 0), Vector3(0.19, 0.25, 0.24), M.CREAM)
		ctx.draw_geometry(hind, M.mesh("hindPaw"), Vector3(0, -0.385 + rear_tuck * 0.21, 0.15), Vector3.ONE, M.WHITE)
		var front_tuck_value = pose["frontLegTuckDegrees"]
		var front_tuck := clampf(float(front_tuck_value if front_tuck_value != null else pose["legTuckDegrees"]) / 38.0, 0.0, 1.0)
		var front_swing = pose["frontLegSwingDegrees"]
		var front := G.translate(parent, side * 0.17, -0.09, 0.30)
		front = G.rotate(front, float(front_swing if front_swing != null else pose["legSwingDegrees"]), 1, 0, 0)
		front = G.scale(front, 1, 1.0 - 0.35 * front_tuck, 1)
		ctx.draw_geometry(front, M.mesh("arm"), Vector3.ZERO, Vector3.ONE, M.CREAM)
		ctx.draw_geometry(front, M.mesh("forePaw"), Vector3(0, -0.585, 0.025), Vector3.ONE, M.WHITE)

static func _face(ctx, parent: Transform3D, pose: Dictionary) -> void:
	var life = ctx.life
	ctx.surface = "SOFT_FUR"
	ctx.draw_geometry(parent, M.mesh("head"), Vector3.ZERO, Vector3.ONE, M.CREAM)
	for side in [-1.0, 1.0]:
		var ear := G.translate(parent, side * 0.17, 1.06, -0.015)
		var gesture: float = (16.0 if side < 0.0 else -4.0) * pose["gestureWeight"]
		ear = G.rotate(ear, -side * (9.0 + pose["primaryAppendageDegrees"] * 0.45 + gesture), 0, 0, 1)
		ear = G.rotate(ear, 7.0 + pose["primaryAppendageDegrees"], 1, 0, 0)
		ctx.draw_geometry(ear, M.mesh("ear"), Vector3.ZERO, Vector3(side, 1, 1), M.CREAM)
		ctx.draw_geometry(ear, M.mesh("ear"), Vector3(side * 0.006, 0.07, 0.052), Vector3(side * 0.57, 0.77, 0.33), M.PINK)
		ctx.surface = "EYE"
		ctx.draw_living_eye(parent, side * 0.19, 0.85, 0.292, 0.095, 0.12, M.CREAM, M.WHITE, Mat.EYE_DARK, false, false)
		ctx.surface = "SOFT_FUR"
		ctx.draw_part(parent, Vector3(side * 0.09, 0.61, 0.32), Vector3(0.125, 0.09, 0.07), M.WHITE)
		for whisker_index in range(-1, 2):
			ctx.draw_part(parent, Vector3(side * 0.24, 0.625 + whisker_index * 0.025, 0.337), Vector3(0.12, 0.003, 0.004),
				M.WHISKER, Vector3(0, 0, side * whisker_index * 10.0))
	var sniff: float = sin((life.time_seconds if life != null else 0.0) * 9.0) * (0.006 + 0.009 * pose["gestureWeight"]) * (life.idle_weight if life != null else 0.0)
	ctx.surface = "NOSE"
	ctx.draw_part(parent, Vector3(0, 0.675 + sniff, 0.389), Vector3(0.044, 0.030, 0.023), M.NOSE)
	var opening := clampf((life.mouth_open if life != null else 0.0) + pose["jawOpen"], 0.0, 1.0)
	ctx.surface = "MATTE"
	ctx.draw_part(parent, Vector3(0, 0.545, 0.347), Vector3(0.060, 0.007 + opening * 0.025, 0.012), M.WHISKER)
	for side in [-1.0, 1.0]:
		ctx.draw_part(parent, Vector3(side * 0.017, 0.532 - opening * 0.012, 0.363), Vector3(0.014, 0.027, 0.010), Mat.WHITE)
