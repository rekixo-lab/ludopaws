@tool
extends RefCounted
## Procedural kid-goat rig: continuous anatomy, articulated neck and four IK leg chains.
## Port of GoatRenderer.kt and GoatDrawHelpers.kt.

const G = preload("res://scripts/game/board3d/paw_geometry.gd")
const P = preload("res://scripts/game/characters/shared/pose.gd")
const Hop = preload("res://scripts/game/characters/shared/hop_dynamics.gd")
const M = preload("res://scripts/game/characters/goat/goat_geometry.gd")
const Motion = preload("res://scripts/game/characters/goat/goat_motion.gd")
const Rig = preload("res://scripts/game/characters/goat/goat_leg_rig.gd")
const Face = preload("res://scripts/game/characters/goat/goat_face.gd")

static func step_travel_progress(progress: float) -> float:
	return Motion.step_travel_progress(progress)

static func sample(motion: String, progress: float, playback := P.ISOLATED) -> Dictionary:
	var anatomy_progress := 1.0 if motion == P.HOP and playback == P.SETTLE else progress
	var d := Motion.sample(motion, anatomy_progress)
	var pose := P.pose({
		"liftY": d["liftY"], "bodyYawDegrees": d["bodyYawDegrees"], "headTiltDegrees": d["headTiltDegrees"],
		"legSwingDegrees": d["legSwingDegrees"], "legTuckDegrees": d["legTuckDegrees"],
		"primaryAppendageDegrees": d["earFlickDegrees"], "secondaryAppendageDegrees": d["beardSwingDegrees"],
		"tertiaryAppendageDegrees": d["tailFlickDegrees"], "bodyPitchDegrees": d["bodyPitchDegrees"],
		"bodyRollDegrees": d["bodyRollDegrees"], "neckPitchDegrees": d["neckPitchDegrees"], "bodyCrouch": d["bodyCrouch"],
		"frontLegTuckDegrees": d["frontLegTuckDegrees"], "rearLegTuckDegrees": d["rearLegTuckDegrees"],
		"frontLegSwingDegrees": d["frontLegSwingDegrees"], "rearLegSwingDegrees": d["rearLegSwingDegrees"],
	})
	return Hop.apply(pose, motion, progress, 0.60, playback)

static func draw(ctx, parent: Transform3D, pose: Dictionary, _team: String) -> void:
	var previous: String = ctx.surface
	ctx.surface = "COAT"
	var rig := G.rotate(parent, -16.0, 0, 1, 0)
	_legs(ctx, rig, pose)
	var torso := G.translate(rig, 0, -pose["bodyCrouch"], 0)
	torso = G.rotate(torso, pose["bodyPitchDegrees"], 1, 0, 0)
	torso = G.rotate(torso, pose["bodyRollDegrees"], 0, 0, 1)
	var breathing: float = 1.0 + (ctx.life.breath if ctx.life != null else 0.0)
	ctx.surface = "GOAT_FLEECE"
	ctx.draw_geometry(torso, M.mesh("body"), Vector3.ZERO, Vector3(breathing, 1, breathing), M.CREAM)
	ctx.surface = "COAT"
	var neck := G.translate(torso, 0, 0.43, 0.025)
	neck = G.rotate(neck, pose["neckPitchDegrees"], 1, 0, 0)
	neck = G.translate(neck, 0, -0.43, -0.025)
	ctx.draw_geometry(neck, M.mesh("neck"), Vector3.ZERO, Vector3.ONE, M.CREAM)
	ctx.surface = "COAT"
	ctx.prepare_head(neck, 1.03, pose["headTiltDegrees"])
	Face.draw(ctx, ctx.head, pose)
	var tail := G.translate(torso, 0.035, 0.28, -0.51)
	tail = G.rotate(tail, -35.0, 1, 0, 0)
	tail = G.rotate(tail, pose["tertiaryAppendageDegrees"], 0, 0, 1)
	ctx.surface = "COAT"
	ctx.draw_geometry(tail, M.mesh("tail"), Vector3.ZERO, Vector3.ONE, M.IVORY)
	ctx.surface = previous

static func _bone(ctx, parent: Transform3D, mesh: G.Geo, x: float, y1: float, z1: float, y2: float, z2: float, radius: float, color: Color) -> void:
	var dy := y2 - y1
	var dz := z2 - z1
	var length := maxf(sqrt(dy * dy + dz * dz), 0.001)
	ctx.draw_geometry(parent, mesh, Vector3(x, (y1 + y2) * 0.5, (z1 + z2) * 0.5),
		Vector3(radius, length, radius), color, Vector3(rad_to_deg(atan2(dz, dy)), 0, 0))

static func _legs(ctx, parent: Transform3D, pose: Dictionary) -> void:
	for leg in 4:
		var rear := leg < 2
		var side := -1.0 if leg % 2 == 0 else 1.0
		var x: float = side * (0.285 if rear else 0.235)
		var tuck = pose["rearLegTuckDegrees"] if rear else pose["frontLegTuckDegrees"]
		var swing = pose["rearLegSwingDegrees"] if rear else pose["frontLegSwingDegrees"]
		var scrape: float = pose["gestureWeight"] if (not rear and side < 0.0) else 0.0
		var stride: float = float(swing if swing != null else pose["legSwingDegrees"]) + 8.0 * scrape * sin(pose["gesturePhase"] * PI * 2.0)
		var s := Rig.solve(rear, side, stride, float(tuck if tuck != null else pose["legTuckDegrees"]), pose["bodyCrouch"], tuck != null)
		ctx.surface = "COAT"
		_bone(ctx, parent, M.mesh("thigh"), x, s[0], s[1], s[2], s[3], 0.12 if rear else 0.083, M.CREAM)
		ctx.draw_part(parent, Vector3(x, s[2], s[3]), Vector3(0.071, 0.075, 0.075), M.IVORY)
		_bone(ctx, parent, M.mesh("shin"), x, s[2], s[3], s[4], s[5], 0.065, M.IVORY)
		ctx.draw_part(parent, Vector3(x, s[4] + 0.015, s[5]), Vector3(0.067, 0.07, 0.07), M.CREAM)
		ctx.surface = "HOOF"
		ctx.draw_geometry(parent, M.mesh("hoof"), Vector3(x - 0.044, s[4] - 0.064, s[5] + 0.045), Vector3(0.82, 1, 1), M.HOOF_DARK)
		ctx.draw_geometry(parent, M.mesh("hoof"), Vector3(x + 0.044, s[4] - 0.064, s[5] + 0.045), Vector3(0.82, 1, 1), M.HOOF_WARM)
	ctx.surface = "COAT"
