@tool
extends RefCounted
## Chestnut horse: mane and forelock, blaze, stockings and hooves on the reversed-knee rig.
## Port of HorseRenderer.kt (legs use the Donkey leg IK, as live does).

const G = preload("res://scripts/game/board3d/paw_geometry.gd")
const P = preload("res://scripts/game/characters/shared/pose.gd")
const Hop = preload("res://scripts/game/characters/shared/hop_dynamics.gd")
const M = preload("res://scripts/game/characters/horse/horse_geometry.gd")
const Rig = preload("res://scripts/game/characters/donkey/donkey_leg_rig.gd")
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
			pose = P.pose({"headTiltDegrees": 2.0 * wave, "primaryAppendageDegrees": 6.0 * wave,
				"secondaryAppendageDegrees": 9.0 * wave, "neckPitchDegrees": 2.0 * wave})
		P.HOP:
			pose = P.pose({"legSwingDegrees": 31.0 * wave * arc, "frontLegTuckDegrees": 33.0 * arc, "rearLegTuckDegrees": 23.0 * arc,
				"primaryAppendageDegrees": 12.0 * arc, "secondaryAppendageDegrees": 22.0 * arc,
				"bodyPitchDegrees": -6.0 * arc, "neckPitchDegrees": 5.0 * arc})
		_:
			pose = P.pose({"liftY": 0.18 * absf(sin(phase * PI * 3.0)) * (1.0 - phase),
				"bodyYawDegrees": 360.0 * phase, "headTiltDegrees": 6.0 * wave,
				"primaryAppendageDegrees": 12.0 * wave, "secondaryAppendageDegrees": 28.0 * wave})
	return Hop.apply(pose, motion, progress, 0.55, playback)

static func draw(ctx, parent: Transform3D, pose: Dictionary, _team: String) -> void:
	var previous: String = ctx.surface
	var rig := G.rotate(parent, -24.0, 0, 1, 0)
	_legs(ctx, rig, pose)
	var torso := G.translate(rig, 0, -pose["bodyCrouch"], 0)
	torso = G.rotate(torso, pose["bodyPitchDegrees"], 1, 0, 0)
	torso = G.rotate(torso, pose["bodyRollDegrees"], 0, 0, 1)
	var breath: float = 1.0 + (ctx.life.breath if ctx.life != null else 0.0)
	ctx.surface = "HORSE_COAT"
	ctx.draw_geometry(torso, M.mesh("body"), Vector3.ZERO, Vector3(breath, 1, breath), M.CHESTNUT)
	_tail(ctx, torso, pose["secondaryAppendageDegrees"])
	var neck := G.translate(torso, 0, 0.36, 0.16)
	neck = G.rotate(neck, pose["neckPitchDegrees"], 1, 0, 0)
	neck = G.translate(neck, 0, -0.36, -0.16)
	ctx.surface = "HORSE_COAT"
	ctx.draw_geometry(neck, M.mesh("neck"), Vector3.ZERO, Vector3.ONE, M.CHESTNUT)
	ctx.surface = "COAT"
	ctx.draw_geometry(neck, M.mesh("mane"), Vector3.ZERO, Vector3.ONE, M.MANE)
	ctx.draw_geometry(neck, M.mesh("mane"), Vector3(0.038, 0.005, 0.028), Vector3(0.72, 0.96, 0.77), M.MANE_LIGHT)
	ctx.prepare_head(neck, 1.08, pose["headTiltDegrees"])
	_face(ctx, ctx.head, pose)
	ctx.surface = previous

static func _legs(ctx, parent: Transform3D, pose: Dictionary) -> void:
	for leg_index in 4:
		var rear := leg_index < 2
		var side := -1.0 if leg_index % 2 == 0 else 1.0
		var x: float = side * (0.27 if rear else 0.235)
		var scrape: float = pose["gestureWeight"] if (not rear and side < 0.0) else 0.0
		var pair_tuck = pose["rearLegTuckDegrees"] if rear else pose["frontLegTuckDegrees"]
		var s := Rig.solve(rear, side, pose["legSwingDegrees"] + 9.0 * scrape * sin(pose["gesturePhase"] * PI * 2.0),
			float(pair_tuck if pair_tuck != null else pose["legTuckDegrees"]) + 8.0 * scrape, pose["bodyCrouch"])
		ctx.surface = "COAT"
		_bone(ctx, parent, x, s[0], s[1], s[2], s[3], 0.105 if rear else 0.09, M.CHESTNUT)
		ctx.draw_part(parent, Vector3(x, s[2], s[3]), Vector3(0.068, 0.07, 0.07), M.CHESTNUT)
		_bone(ctx, parent, x, s[2], s[3], s[4], s[5], 0.062, M.STOCKING)
		ctx.surface = "HOOF"
		ctx.draw_geometry(parent, M.mesh("hoof"), Vector3(x, s[4] - 0.03, s[5] + 0.025), Vector3.ONE, M.HOOF)

static func _bone(ctx, parent: Transform3D, x: float, y1: float, z1: float, y2: float, z2: float, radius: float, color: Color) -> void:
	var dy := y2 - y1
	var dz := z2 - z1
	var length := maxf(sqrt(dy * dy + dz * dz), 0.001)
	ctx.draw_geometry(parent, M.mesh("limb"), Vector3(x, (y1 + y2) * 0.5, (z1 + z2) * 0.5),
		Vector3(radius, length, radius), color, Vector3(rad_to_deg(atan2(dz, dy)), 0, 0))

static func _face(ctx, parent: Transform3D, pose: Dictionary) -> void:
	var life = ctx.life
	ctx.surface = "HORSE_FACE"
	ctx.draw_geometry(parent, M.mesh("head"), Vector3.ZERO, Vector3.ONE, M.CHESTNUT)
	for side in [-1.0, 1.0]:
		var ear := G.translate(parent, side * 0.17, 1.27, -0.04)
		var swivel: float = sin((life.time_seconds if life != null else 0.0) * 1.5 + side) * 7.0 * (life.idle_weight if life != null else 0.0)
		ear = G.rotate(ear, -side * (10.0 + pose["primaryAppendageDegrees"]), 0, 0, 1)
		ear = G.rotate(ear, side * swivel, 0, 1, 0)
		ctx.surface = "COAT"
		ctx.draw_geometry(ear, M.mesh("ear"), Vector3.ZERO, Vector3.ONE, M.CHESTNUT)
		ctx.draw_geometry(ear, M.mesh("ear"), Vector3(0, 0.025, 0.035), Vector3(0.55, 0.78, 0.30), M.MUZZLE)
		ctx.surface = "EYE"
		ctx.draw_living_eye(parent, side * 0.19, 1.10, 0.29, 0.075, 0.09, M.CHESTNUT, M.BLAZE, Mat.EYE_DARK, false, false)
		ctx.surface = "COAT"
	ctx.surface = "SOFT_FUR"
	ctx.draw_geometry(parent, M.mesh("muzzle"), Vector3.ZERO, Vector3.ONE, M.MUZZLE)
	ctx.draw_part(parent, Vector3(0, 0.86, 0.673), Vector3(0.075, 0.07, 0.02), M.BLAZE)
	ctx.surface = "NOSE"
	for side in [-1.0, 1.0]:
		ctx.draw_part(parent, Vector3(side * 0.12, 0.82, 0.677), Vector3(0.026, 0.020, 0.009), Mat.EYE_DARK, Vector3(0, 0, side * 18.0))
	var opening := clampf((life.mouth_open if life != null else 0.0) + pose["jawOpen"], 0.0, 1.0)
	ctx.surface = "MATTE"
	ctx.draw_part(parent, Vector3(0, 0.665, 0.64), Vector3(0.135, 0.007 + opening * 0.035, 0.02), Mat.EYE_DARK)
	var jaw := G.translate(parent, 0, 0.73, 0.30)
	jaw = G.rotate(jaw, -opening * 22.0, 1, 0, 0)
	jaw = G.translate(jaw, 0, -0.73, -0.30)
	ctx.surface = "SOFT_FUR"
	ctx.draw_geometry(jaw, M.mesh("jaw"), Vector3.ZERO, Vector3.ONE, M.MUZZLE)
	ctx.surface = "COAT"
	ctx.draw_geometry(parent, M.mesh("forelock"), Vector3(-0.017, 1.36, 0.16), Vector3.ONE, M.MANE,
		Vector3(0, 0, 10.0 + pose["primaryAppendageDegrees"] * 0.14))
	ctx.draw_geometry(parent, M.mesh("forelock"), Vector3(0.033, 1.345, 0.177), Vector3(0.62, 0.83, 0.70), M.MANE_LIGHT,
		Vector3(0, 0, 19.0 + pose["primaryAppendageDegrees"] * 0.14))

static func _tail(ctx, parent: Transform3D, sway: float) -> void:
	var tail := G.translate(parent, 0.065, 0.12, -0.72)
	tail = G.rotate(tail, -8.0 + sway * 0.25, 1, 0, 0)
	tail = G.rotate(tail, sway * 0.85, 0, 0, 1)
	ctx.surface = "COAT"
	ctx.draw_geometry(tail, M.mesh("tail"), Vector3.ZERO, Vector3.ONE, M.MANE)
	ctx.draw_geometry(tail, M.mesh("tail"), Vector3(0.032, -0.01, 0.025), Vector3(0.67, 0.95, 0.82), M.MANE_LIGHT, Vector3(0, 0, sway * 0.12))
