@tool
extends RefCounted
## Procedural duck: feet, body, wings with feathers, tail, neck and head. Port of DuckRenderer.kt
## and DuckDrawHelpers.kt.

const G = preload("res://scripts/game/board3d/paw_geometry.gd")
const P = preload("res://scripts/game/characters/shared/pose.gd")
const Hop = preload("res://scripts/game/characters/shared/hop_dynamics.gd")
const M = preload("res://scripts/game/characters/duck/duck_geometry.gd")
const Motion = preload("res://scripts/game/characters/duck/duck_motion.gd")
const Face = preload("res://scripts/game/characters/duck/duck_face.gd")

static func step_travel_progress(progress: float) -> float:
	return Motion.step_travel_progress(progress)

static func sample(motion: String, progress: float, playback := P.ISOLATED) -> Dictionary:
	var anatomy_progress := 1.0 if motion == P.HOP and playback == P.SETTLE else progress
	var d := Motion.sample(motion, anatomy_progress)
	var pose := P.pose({
		"liftY": d["liftY"], "bodyYawDegrees": d["bodyYawDegrees"], "headTiltDegrees": d["headTiltDegrees"],
		"legPaddleDegrees": d["legPaddleDegrees"], "primaryAppendageDegrees": d["wingFlapDegrees"],
		"legTuckDegrees": d["legTuckDegrees"], "bodyCrouch": d["bodyCrouch"],
		"bodyPitchDegrees": d["bodyPitchDegrees"], "bodyRollDegrees": d["bodyRollDegrees"],
		"neckPitchDegrees": d["neckPitchDegrees"],
	})
	return Hop.apply(pose, motion, progress, 0.54, playback)

static func draw(ctx, parent: Transform3D, pose: Dictionary, _team: String) -> void:
	var previous: String = ctx.surface
	var rig := G.rotate(parent, -12.0, 0, 1, 0)
	_feet(ctx, rig, pose)
	var torso := G.translate(rig, 0, -pose["bodyCrouch"], 0)
	torso = G.rotate(torso, pose["bodyPitchDegrees"], 1, 0, 0)
	torso = G.rotate(torso, pose["bodyRollDegrees"], 0, 0, 1)
	var breath: float = 1.0 + (ctx.life.breath if ctx.life != null else 0.0)
	ctx.surface = "BODY_COAT"
	ctx.draw_geometry(torso, M.mesh("body"), Vector3.ZERO, Vector3(breath, 1, breath), M.YELLOW)
	for side in [-1.0, 1.0]:
		_wing(ctx, torso, side, pose)
	ctx.surface = "COAT"
	ctx.draw_geometry(torso, M.mesh("tail"), Vector3(0, -0.02, -0.52), Vector3.ONE, M.BELLY, Vector3(-46, 0, 0))
	var neck := G.translate(torso, 0, 0.43, 0)
	neck = G.rotate(neck, pose["neckPitchDegrees"] + 12.0 * pose["gestureWeight"], 1, 0, 0)
	neck = G.translate(neck, 0, -0.43, 0)
	ctx.draw_geometry(neck, M.mesh("neck"), Vector3.ZERO, Vector3.ONE, M.YELLOW)
	ctx.prepare_head(neck, 0.97, pose["headTiltDegrees"])
	# A small downward glance reveals the bill's top in the frontal game camera.
	var head := G.translate(ctx.head, 0, 0.84, 0.05)
	head = G.rotate(head, 8.0, 1, 0, 0)
	ctx.head = G.translate(head, 0, -0.90, -0.05)
	Face.draw(ctx, ctx.head, pose)
	ctx.surface = previous

static func _wing(ctx, parent: Transform3D, side: float, pose: Dictionary) -> void:
	var wing := G.translate(parent, side * 0.47, 0.23, -0.03)
	var gesture: float = pose["gestureWeight"] * (16.0 if side < 0.0 else 5.0)
	wing = G.rotate(wing, side * (15.0 + clampf(pose["primaryAppendageDegrees"], -12.0, 80.0) + gesture), 0, 0, 1)
	wing = G.rotate(wing, -8.0, 1, 0, 0)
	ctx.surface = "COAT"
	ctx.draw_geometry(wing, M.mesh("wing"), Vector3.ZERO, Vector3(side, 1, 1), M.YELLOW)
	for i in 3:
		ctx.draw_geometry(wing, M.mesh("feather"), Vector3(side * (i - 1) * 0.065, -0.19, 0.065 - i * 0.008),
			Vector3(1, 0.80 + i * 0.08, 1), M.BELLY, Vector3(0, 0, side * (i - 1) * 9.0))

static func _bone(ctx, parent: Transform3D, mesh: G.Geo, x: float, y1: float, z1: float, y2: float, z2: float, radius: float, color: Color) -> void:
	var dy := y2 - y1
	var dz := z2 - z1
	var length := maxf(sqrt(dy * dy + dz * dz), 0.001)
	ctx.draw_geometry(parent, mesh, Vector3(x, (y1 + y2) * 0.5, (z1 + z2) * 0.5),
		Vector3(radius, length, radius), color, Vector3(rad_to_deg(atan2(dz, dy)), 0, 0))

static func _feet(ctx, parent: Transform3D, pose: Dictionary) -> void:
	var tuck := clampf(pose["legTuckDegrees"] / 38.0, 0.0, 1.0)
	for i in 2:
		var side := -1.0 if i == 0 else 1.0
		var paddle: float = side * pose["legPaddleDegrees"]
		var ankle_y := -0.70 + tuck * 0.19
		var ankle_z := 0.20 + sin(deg_to_rad(paddle)) * 0.09
		ctx.surface = "NOSE"
		_bone(ctx, parent, M.mesh("leg"), side * 0.245, -0.40 - pose["bodyCrouch"], 0.08, ankle_y + 0.06, ankle_z, 0.085, M.ORANGE)
		ctx.draw_geometry(parent, M.mesh("foot"), Vector3(side * 0.245, ankle_y + 0.056, ankle_z), Vector3.ONE, M.ORANGE,
			Vector3(16.0 - paddle * 0.30 * tuck, side * 8.0, 0))
