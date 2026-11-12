@tool
extends RefCounted
## Equine rig: gait foot paths with torso-following hips, knee-bent leg skins, swishing tail.
## Port of DonkeyRenderer.kt and DonkeyDrawHelpers.kt.

const G = preload("res://scripts/game/board3d/paw_geometry.gd")
const P = preload("res://scripts/game/characters/shared/pose.gd")
const Hop = preload("res://scripts/game/characters/shared/hop_dynamics.gd")
const M = preload("res://scripts/game/characters/donkey/donkey_geometry.gd")
const Motion = preload("res://scripts/game/characters/donkey/donkey_motion.gd")
const Rig = preload("res://scripts/game/characters/donkey/donkey_leg_rig.gd")
const Face = preload("res://scripts/game/characters/donkey/donkey_face.gd")

static func step_travel_progress(progress: float) -> float:
	return Motion.step_travel_progress(progress)

static func sample(motion: String, progress: float, playback := P.ISOLATED) -> Dictionary:
	var anatomy_progress := 1.0 if motion == P.HOP and playback == P.SETTLE else progress
	var d := Motion.sample(motion, anatomy_progress)
	var pose := P.pose({
		"stridePhase": d["stridePhase"], "strideWeight": d["strideWeight"], "bodyRollDegrees": d["bodyRollDegrees"],
		"liftY": d["liftY"], "bodyYawDegrees": d["bodyYawDegrees"], "headTiltDegrees": d["headTiltDegrees"],
		"legSwingDegrees": d["legSwingDegrees"], "legTuckDegrees": d["legTuckDegrees"],
		"primaryAppendageDegrees": d["earTwitchDegrees"], "secondaryAppendageDegrees": d["tailSwayDegrees"],
		"bodyPitchDegrees": d["bodyPitchDegrees"], "neckPitchDegrees": d["neckPitchDegrees"], "bodyCrouch": d["bodyCrouch"],
		"frontLegTuckDegrees": d["frontLegTuckDegrees"], "rearLegTuckDegrees": d["rearLegTuckDegrees"],
		"frontLegSwingDegrees": d["frontLegSwingDegrees"], "rearLegSwingDegrees": d["rearLegSwingDegrees"],
	})
	return Hop.apply(pose, motion, progress, 0.48, playback)

static func draw(ctx, parent: Transform3D, pose: Dictionary, _team: String) -> void:
	var previous: String = ctx.surface
	var rig := G.rotate(parent, -32.0, 0, 1, 0)
	var time: float = ctx.life.time_seconds if ctx.life != null else 0.0
	var idle: float = ctx.life.idle_weight if ctx.life != null else 0.0
	var shift := sin(time * 0.72) * 0.018 * idle
	var roll: float = pose["bodyRollDegrees"] + sin(time * 0.72) * 1.2 * idle
	var heave := 0.0
	_legs(ctx, rig, pose, shift, roll, heave)
	var torso := G.translate(rig, shift, -pose["bodyCrouch"] + heave, 0)
	torso = G.rotate(torso, roll, 0, 0, 1)
	torso = G.rotate(torso, pose["bodyPitchDegrees"], 1, 0, 0)
	var breath: float = 1.0 + (ctx.life.breath if ctx.life != null else 0.0)
	ctx.surface = "DONKEY_FUR"
	ctx.draw_geometry(torso, M.mesh("body"), Vector3.ZERO, Vector3(breath, 1, breath), M.COAT)
	var neck := G.translate(torso, 0, 0.38, 0)
	neck = G.rotate(neck, pose["neckPitchDegrees"], 1, 0, 0)
	neck = G.translate(neck, 0, -0.38, 0)
	ctx.surface = "SOFT_FUR"
	ctx.draw_geometry(neck, M.mesh("neck"), Vector3.ZERO, Vector3.ONE, M.COAT)
	ctx.prepare_head(neck, 0.82, pose["headTiltDegrees"])
	# Keep room above the forehead for the long expressive ears.
	var head := G.translate(ctx.head, 0, 0.73, 0.20)
	head = G.rotate(head, 8.0, 0, 1, 0)
	head = G.scale(head, 1.04, 1.01, 1.09)
	ctx.head = G.translate(head, 0, -0.94, 0)
	Face.draw(ctx, ctx.head, pose)
	_tail(ctx, torso, pose["secondaryAppendageDegrees"])
	ctx.surface = previous
	ctx.limb_bend = Vector2.ZERO

static func _bone(ctx, parent: Transform3D, mesh: G.Geo, x: float, y1: float, z1: float, y2: float, z2: float,
		knee_y: float, knee_z: float, radius: float, color: Color) -> void:
	var dy := y2 - y1
	var dz := z2 - z1
	var length := maxf(sqrt(dy * dy + dz * dz), 0.001)
	var ky := knee_y - (y1 + y2) * 0.5
	var kz := knee_z - (z1 + z2) * 0.5
	ctx.limb_bend = Vector2(clampf((ky * dy + kz * dz) / (length * length), -0.10, 0.10), (kz * dy - ky * dz) / (length * radius))
	ctx.draw_geometry(parent, mesh, Vector3(x, (y1 + y2) * 0.5, (z1 + z2) * 0.5),
		Vector3(radius, length, radius), color, Vector3(rad_to_deg(atan2(dz, dy)), 0, 0))
	ctx.limb_bend = Vector2.ZERO

static func _legs(ctx, parent: Transform3D, pose: Dictionary, shift: float, roll: float, heave: float) -> void:
	var pitch := deg_to_rad(pose["bodyPitchDegrees"])
	var roll_rad := deg_to_rad(roll)
	var time: float = ctx.life.time_seconds if ctx.life != null else 0.0
	for leg in 4:
		var rear := leg < 2
		var side := -1.0 if leg % 2 == 0 else 1.0
		var x: float = side * (0.275 if rear else 0.24)
		var hoof_pitch := 0.0
		var foot_y := Rig.GROUND_ANKLE_Y
		var foot_z := -0.475 if rear else 0.335
		if pose["strideWeight"] > 0.0:
			var f := Motion.foot(rear, side, pose["stridePhase"], pose["strideWeight"])
			foot_y = f[0]
			foot_z = f[1]
			hoof_pitch = f[2]
		else:
			var tuck_value = pose["rearLegTuckDegrees"] if rear else pose["frontLegTuckDegrees"]
			var tuck: float = float(tuck_value if tuck_value != null else pose["legTuckDegrees"])
			foot_y += clampf(tuck / 38.0, 0.0, 1.0) * 0.22
			# Occasionally rest one hind hoof while the other three carry the weight.
			var rest := 0.0
			if rear and side > 0.0:
				rest = pow(maxf(0.0, sin(time * 0.57)), 12) * (ctx.life.idle_weight if ctx.life != null else 0.0)
			foot_y += rest * 0.055
			foot_z -= rest * 0.025
			hoof_pitch = rest * 16.0
		var hy := -0.26 if rear else -0.24
		var hz := -0.43 if rear else 0.30
		# The hip follows the torso; stance hooves remain on the ground.
		var hip_y: float = (hy * cos(pitch) - hz * sin(pitch)) * cos(roll_rad) + x * sin(roll_rad) - pose["bodyCrouch"] + heave
		var hip_z := hy * sin(pitch) + hz * cos(pitch)
		var s := Rig.solve_foot(rear, hip_y, hip_z, foot_y, foot_z)
		var frame := G.translate(parent, shift * 0.35, 0, 0)
		ctx.surface = "SOFT_FUR"
		_bone(ctx, frame, M.mesh("leg"), x, hip_y, hip_z, s[4], s[5], s[2], s[3], 0.084 if rear else 0.077, M.COAT)
		ctx.surface = "HOOF"
		ctx.draw_geometry(frame, M.mesh("hoof"), Vector3(x, s[4] - 0.03, s[5] + 0.025), Vector3(0.90, 1, 0.94), M.HOOF, Vector3(hoof_pitch, 0, 0))

static func _tail(ctx, parent: Transform3D, sway: float) -> void:
	var chain := G.translate(parent, 0.09, -0.02, -0.72)
	chain = G.rotate(chain, 162.0, 1, 0, 0)
	var time: float = ctx.life.time_seconds if ctx.life != null else 0.0
	var idle: float = ctx.life.idle_weight if ctx.life != null else 0.0
	var swish := sin(time * 6.0) * pow(maxf(0.0, sin(time * 0.48)), 12) * 22.0 * idle
	chain = G.rotate(chain, sway * 0.45 + swish, 0, 0, 1)
	ctx.surface = "SOFT_FUR"
	var radius := 0.045
	for i in 5:
		chain = G.rotate(chain, sway * (0.10 + i * 0.025), 0, 0, 1)
		chain = G.rotate(chain, -5.0, 1, 0, 0)
		var tuft := i == 4
		var thickness := 0.075 if tuft else radius
		ctx.draw_geometry(chain, M.mesh("tailTuft" if tuft else "tail"), Vector3(0, 0.075, 0),
			Vector3(thickness, 0.16, thickness), M.DARK if tuft else M.COAT)
		chain = G.translate(chain, 0, 0.145, 0)
		radius *= 0.88
