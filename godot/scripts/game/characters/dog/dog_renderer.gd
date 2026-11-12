@tool
extends RefCounted
## One continuous torso/skull, four connected IK chains and articulated face/tail.
## Port of DogRenderer.kt and DogDrawHelpers.kt.

const G = preload("res://scripts/game/board3d/paw_geometry.gd")
const P = preload("res://scripts/game/characters/shared/pose.gd")
const Hop = preload("res://scripts/game/characters/shared/hop_dynamics.gd")
const M = preload("res://scripts/game/characters/dog/dog_geometry.gd")
const Motion = preload("res://scripts/game/characters/dog/dog_motion.gd")
const Rig = preload("res://scripts/game/characters/dog/dog_leg_rig.gd")
const Face = preload("res://scripts/game/characters/dog/dog_face.gd")

static func step_travel_progress(progress: float) -> float:
	return Motion.step_travel_progress(progress)

static func sample(motion: String, progress: float, playback := P.ISOLATED) -> Dictionary:
	var anatomy_progress := 1.0 if motion == P.HOP and playback == P.SETTLE else progress
	var d := Motion.sample(motion, anatomy_progress)
	var pose := P.pose({
		"liftY": d["liftY"], "bodyYawDegrees": d["bodyYawDegrees"], "headTiltDegrees": d["headTiltDegrees"],
		"legSwingDegrees": d["legSwingDegrees"], "legTuckDegrees": d["legTuckDegrees"],
		"primaryAppendageDegrees": d["earBounceDegrees"], "secondaryAppendageDegrees": d["tailWagDegrees"],
		"bodyPitchDegrees": d["bodyPitchDegrees"], "neckPitchDegrees": d["neckPitchDegrees"],
		"bodyCrouch": d["bodyCrouch"],
		"frontLegTuckDegrees": d["frontLegTuckDegrees"], "rearLegTuckDegrees": d["rearLegTuckDegrees"],
		"frontLegSwingDegrees": d["frontLegSwingDegrees"], "rearLegSwingDegrees": d["rearLegSwingDegrees"],
	})
	return Hop.apply(pose, motion, progress, 0.56, playback)

static func draw(ctx, parent: Transform3D, pose: Dictionary, _team: String) -> void:
	var previous: String = ctx.surface
	var rig := G.rotate(parent, -15.0, 0, 1, 0)
	_legs(ctx, rig, pose)
	var torso := G.translate(rig, 0, -pose["bodyCrouch"], 0)
	torso = G.rotate(torso, pose["bodyPitchDegrees"], 1, 0, 0)
	var breath: float = 1.0 + (ctx.life.breath if ctx.life != null else 0.0)
	ctx.surface = "BODY_COAT"
	ctx.draw_geometry(torso, M.mesh("body"), Vector3.ZERO, Vector3(breath, 1, breath), M.TAN)
	var neck := G.translate(torso, 0, 0.38, 0)
	neck = G.rotate(neck, pose["neckPitchDegrees"], 1, 0, 0)
	neck = G.translate(neck, 0, -0.38, 0)
	ctx.surface = "COAT"
	ctx.draw_geometry(neck, M.mesh("neck"), Vector3.ZERO, Vector3.ONE, M.TAN)
	ctx.prepare_head(neck, 0.94, pose["headTiltDegrees"])
	Face.draw(ctx, ctx.head, pose)
	_tail(ctx, torso, pose["secondaryAppendageDegrees"])
	ctx.surface = previous

## DogDrawHelpers.dogBone: a limb segment from (y1,z1) to (y2,z2).
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
		var x: float = side * (0.30 if rear else 0.275)
		var tuck = pose["rearLegTuckDegrees"] if rear else pose["frontLegTuckDegrees"]
		var swing = pose["rearLegSwingDegrees"] if rear else pose["frontLegSwingDegrees"]
		var s := Rig.solve(rear, side, float(swing if swing != null else pose["legSwingDegrees"]),
			float(tuck if tuck != null else pose["legTuckDegrees"]), pose["bodyCrouch"], tuck != null)
		ctx.surface = "COAT"
		_bone(ctx, parent, M.mesh("limb"), x, s[0], s[1], s[2], s[3], 0.135 if rear else 0.10, M.TAN)
		ctx.draw_part(parent, Vector3(x, s[2], s[3]), Vector3(0.075, 0.078, 0.078), M.TAN)
		_bone(ctx, parent, M.mesh("limb"), x, s[2], s[3], s[4], s[5], 0.075, M.TAN)
		ctx.draw_geometry(parent, M.mesh("paw"), Vector3(x, s[4] - 0.04, s[5] + 0.035), Vector3.ONE, M.CREAM)
		ctx.surface = "MATTE"
		for toe in [-1.0, 1.0]:
			ctx.draw_part(parent, Vector3(x + toe * 0.043, s[4] - 0.058, s[5] + 0.207), Vector3(0.004, 0.027, 0.006), M.PAW_SEAM)

static func _tail(ctx, parent: Transform3D, wag: float) -> void:
	var chain := G.translate(parent, 0.26, 0.12, -0.43)
	chain = G.rotate(chain, -54.0, 1, 0, 0)
	chain = G.rotate(chain, -22.0, 0, 0, 1)
	ctx.surface = "COAT"
	for i in 4:
		chain = G.rotate(chain, wag * (0.22 + i * 0.055), 0, 0, 1)
		chain = G.rotate(chain, 9.0, 1, 0, 0)
		var radius := 0.085 - i * 0.017
		ctx.draw_geometry(chain, M.mesh("tail"), Vector3(0, 0.09, 0), Vector3(radius, 0.19, radius), M.CREAM if i == 3 else M.TAN)
		chain = G.translate(chain, 0, 0.18, 0)
