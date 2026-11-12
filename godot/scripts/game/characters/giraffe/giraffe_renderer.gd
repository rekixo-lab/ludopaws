@tool
extends RefCounted
## Patterned giraffe on a stretched reversed-knee rig: long neck with mane, ossicones,
## pink-lined ears and a three-quarter stance. Port of GiraffeRenderer.kt
## (legs use the Donkey leg IK scaled along Y, as live does).

const G = preload("res://scripts/game/board3d/paw_geometry.gd")
const P = preload("res://scripts/game/characters/shared/pose.gd")
const Hop = preload("res://scripts/game/characters/shared/hop_dynamics.gd")
const M = preload("res://scripts/game/characters/giraffe/giraffe_geometry.gd")
const Rig = preload("res://scripts/game/characters/donkey/donkey_leg_rig.gd")
const Def = preload("res://scripts/game/characters/giraffe/giraffe_definition.gd")

static func step_travel_progress(progress: float) -> float:
	return Hop.travel(progress)

static func sample(motion: String, progress: float, playback := P.ISOLATED) -> Dictionary:
	var phase := clampf(progress, 0.0, 1.0)
	var wave := sin(phase * PI * 2.0)
	var anatomy := 1.0 if playback == P.SETTLE else phase
	var arc := maxf(sin(step_travel_progress(anatomy) * PI), 0.0)
	var pose := {}
	match motion:
		P.IDLE:
			pose = P.pose({"headTiltDegrees": 4.0 * wave, "neckPitchDegrees": 3.0 * wave,
				"primaryAppendageDegrees": 10.0 * wave, "secondaryAppendageDegrees": 17.0 * wave})
		P.HOP:
			pose = P.pose({"legSwingDegrees": 27.0 * wave * arc, "frontLegTuckDegrees": 31.0 * arc,
				"rearLegTuckDegrees": 21.0 * arc, "bodyPitchDegrees": -5.0 * arc, "neckPitchDegrees": 8.0 * arc,
				"primaryAppendageDegrees": 12.0 * arc, "secondaryAppendageDegrees": 19.0 * wave})
		_:
			var bow := sin(phase * PI)
			pose = P.pose({"liftY": 0.16 * bow * bow, "bodyYawDegrees": 360.0 * phase, "neckPitchDegrees": 11.0 * bow,
				"headTiltDegrees": 6.0 * wave, "secondaryAppendageDegrees": 25.0 * wave})
	return Hop.apply(pose, motion, progress, 0.47, playback)

static func draw(ctx, parent: Transform3D, pose: Dictionary, _team: String) -> void:
	var previous: String = ctx.surface
	var model := G.scale(parent, Def.MODEL_SCALE, Def.MODEL_SCALE, Def.MODEL_SCALE)
	# A slight three-quarter stance reveals the length of the torso and all four hooves.
	model = G.rotate(model, -25.0, 0, 1, 0)
	_legs(ctx, model, pose)
	var torso := G.translate(model, 0, -pose["bodyCrouch"], 0)
	torso = G.rotate(torso, pose["bodyPitchDegrees"], 1, 0, 0)
	torso = G.rotate(torso, pose["bodyRollDegrees"], 0, 0, 1)
	var breath: float = 1.0 + (ctx.life.breath if ctx.life != null else 0.0)
	_coat(ctx, torso, "body", breath)
	_tail(ctx, torso, pose["secondaryAppendageDegrees"])
	var neck := G.translate(torso, 0, 0.24, 0.25)
	neck = G.rotate(neck, pose["neckPitchDegrees"], 1, 0, 0)
	neck = G.translate(neck, 0, -0.24, -0.25)
	_coat(ctx, neck, "neck")
	ctx.surface = "SOFT_FUR"
	ctx.draw_geometry(neck, M.mesh("mane"), Vector3.ZERO, Vector3.ONE, M.CINNAMON)
	ctx.prepare_head(neck, 1.86, pose["headTiltDegrees"])
	_face(ctx, ctx.head, pose)
	ctx.surface = previous

static func _coat(ctx, parent: Transform3D, part: String, width := 1.0) -> void:
	ctx.surface = "COAT"
	ctx.draw_geometry(parent, M.mesh(part + "_gold"), Vector3.ZERO, Vector3(width, 1, width), M.HONEY)
	ctx.draw_geometry(parent, M.mesh(part + "_patches"), Vector3.ZERO, Vector3(width, 1, width), M.CINNAMON)

static func _leg_y(y: float) -> float:
	return Def.LEG_Y_ANCHOR + (y - Def.LEG_Y_ANCHOR) * Def.LEG_Y_SCALE

static func _legs(ctx, parent: Transform3D, pose: Dictionary) -> void:
	for leg in 4:
		var rear := leg < 2
		var side := -1.0 if leg % 2 == 0 else 1.0
		var x: float = side * (0.20 if rear else 0.185)
		var tuck = pose["rearLegTuckDegrees"] if rear else pose["frontLegTuckDegrees"]
		var s := Rig.solve(rear, side, pose["legSwingDegrees"], float(tuck if tuck != null else pose["legTuckDegrees"]), pose["bodyCrouch"])
		var hip_y := _leg_y(s[0])
		var knee_y := _leg_y(s[2])
		var ankle_y := _leg_y(s[4])
		ctx.surface = "COAT"
		_bone(ctx, parent, x, hip_y, s[1], knee_y, s[3], 0.092 if rear else 0.079, M.HONEY)
		_bone(ctx, parent, x, knee_y, s[3], ankle_y, s[5], 0.052, M.CREAM)
		ctx.draw_part(parent, Vector3(x, knee_y, s[3]), Vector3(0.057, 0.069, 0.055), M.HONEY)
		ctx.surface = "HOOF"
		ctx.draw_geometry(parent, M.mesh("hoof"), Vector3(x, ankle_y + Def.HOOF_OFFSET, s[5] + 0.015), Vector3.ONE, M.HOOF_BROWN)

static func _bone(ctx, parent: Transform3D, x: float, y1: float, z1: float, y2: float, z2: float, radius: float, color: Color) -> void:
	var dy := y2 - y1
	var dz := z2 - z1
	ctx.draw_part(parent, Vector3(x, (y1 + y2) * 0.5, (z1 + z2) * 0.5),
		Vector3(radius, sqrt(dy * dy + dz * dz) * 0.5 + 0.035, radius), color, Vector3(rad_to_deg(atan2(dz, dy)), 0, 0))

static func _face(ctx, parent: Transform3D, pose: Dictionary) -> void:
	var life = ctx.life
	ctx.surface = "SOFT_FUR"
	ctx.draw_geometry(parent, M.mesh("head"), Vector3.ZERO, Vector3.ONE, M.HONEY)
	for x in [-1.0, 1.0]:
		var ear := G.translate(parent, x * 0.174, 1.74, 0.285)
		ear = G.rotate(ear, -x * (57.0 + pose["primaryAppendageDegrees"]), 0, 0, 1)
		ctx.surface = "SOFT_FUR"
		ctx.draw_geometry(ear, M.mesh("ear"), Vector3.ZERO, Vector3.ONE, M.HONEY)
		ctx.draw_geometry(ear, M.mesh("ear"), Vector3(0, 0.025, 0.031), Vector3(0.62, 0.77, 0.45), M.EAR_PINK)
		ctx.surface = "HORN"
		ctx.draw_part(parent, Vector3(x * 0.10, 1.89, 0.27), Vector3(0.040, 0.12, 0.043), M.HONEY, Vector3(0, 0, -x * 8.0))
		ctx.surface = "SOFT_FUR"
		ctx.draw_part(parent, Vector3(x * 0.115, 2.005, 0.27), Vector3(0.058, 0.063, 0.056), M.CINNAMON)
		ctx.surface = "EYE"
		ctx.draw_living_eye(parent, x * 0.142, 1.648, 0.520, 0.066, 0.083, M.HONEY, M.CREAM, M.DARK, false, false)
		ctx.surface = "SOFT_FUR"
		ctx.draw_part(parent, Vector3(x * 0.103, 1.78, 0.415), Vector3(0.033, 0.048, 0.02), M.CINNAMON, Vector3(0, 0, x * 15.0))
	var open := clampf((life.mouth_open if life != null else 0.0) + pose["jawOpen"], 0.0, 1.0)
	ctx.surface = "SOFT_FUR"
	ctx.draw_part(parent, Vector3(0, 1.467, 0.618), Vector3(0.174, 0.118, 0.143), M.CREAM, Vector3(-10.0, 0, 0))
	ctx.draw_part(parent, Vector3(0, 1.395 - 0.055 * open, 0.600), Vector3(0.126, 0.046, 0.103), M.CREAM)
	ctx.surface = "NOSE"
	ctx.draw_part(parent, Vector3(-0.067, 1.490, 0.751), Vector3(0.024, 0.016, 0.010), M.DARK, Vector3(0, 0, -17.0))
	ctx.draw_part(parent, Vector3(0.067, 1.490, 0.751), Vector3(0.024, 0.016, 0.010), M.DARK, Vector3(0, 0, 17.0))
	ctx.surface = "MATTE"
	ctx.draw_part(parent, Vector3(0, 1.415 - 0.019 * open, 0.742), Vector3(0.087, 0.008 + 0.020 * open, 0.012), M.HOOF_BROWN)

static func _tail(ctx, parent: Transform3D, sway: float) -> void:
	var tail := G.translate(parent, 0, 0.045, -0.685)
	tail = G.rotate(tail, -126.0, 1, 0, 0)
	tail = G.rotate(tail, sway, 0, 0, 1)
	ctx.surface = "COAT"
	ctx.draw_part(tail, Vector3(0, 0.135, 0), Vector3(0.027, 0.155, 0.029), M.HONEY)
	tail = G.translate(tail, 0, 0.27, 0)
	tail = G.rotate(tail, -24.0, 1, 0, 0)
	ctx.draw_part(tail, Vector3(0, 0.065, 0), Vector3(0.023, 0.090, 0.023), M.HONEY)
	ctx.surface = "SOFT_FUR"
	ctx.draw_part(tail, Vector3(0, 0.175, 0), Vector3(0.051, 0.091, 0.039), M.CINNAMON)
