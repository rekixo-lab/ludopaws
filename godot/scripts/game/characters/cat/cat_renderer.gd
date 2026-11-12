@tool
extends RefCounted
## Continuous torso/skull, four knee-bent leg skins and an articulated six-segment tail.
## Port of CatRenderer.kt and CatDrawHelpers.kt.

const G = preload("res://scripts/game/board3d/paw_geometry.gd")
const P = preload("res://scripts/game/characters/shared/pose.gd")
const Hop = preload("res://scripts/game/characters/shared/hop_dynamics.gd")
const M = preload("res://scripts/game/characters/cat/cat_geometry.gd")
const Motion = preload("res://scripts/game/characters/cat/cat_motion.gd")
const Rig = preload("res://scripts/game/characters/cat/cat_leg_rig.gd")
const Face = preload("res://scripts/game/characters/cat/cat_face.gd")

const MODEL_SIZE_MULTIPLIER := 1.375
const LEG_LENGTH_SCALE := 0.5
const LEG_LENGTH_ANCHOR_Y := 0.22

static func step_travel_progress(progress: float) -> float:
	return Motion.step_travel_progress(progress)

static func sample(motion: String, progress: float, playback := P.ISOLATED) -> Dictionary:
	var anatomy_progress := 1.0 if motion == P.HOP and playback == P.SETTLE else progress
	var d := Motion.sample(motion, anatomy_progress)
	var pose := P.pose({
		"liftY": d["liftY"], "bodyYawDegrees": d["bodyYawDegrees"], "headTiltDegrees": d["headTiltDegrees"],
		"legSwingDegrees": d["legSwingDegrees"], "legTuckDegrees": d["legTuckDegrees"],
		"primaryAppendageDegrees": d["earTwitchDegrees"], "secondaryAppendageDegrees": d["tailSwayDegrees"],
		"bodyPitchDegrees": d["bodyPitchDegrees"], "neckPitchDegrees": d["neckPitchDegrees"], "bodyCrouch": d["bodyCrouch"],
		"frontLegTuckDegrees": d["frontLegTuckDegrees"], "rearLegTuckDegrees": d["rearLegTuckDegrees"],
		"frontLegSwingDegrees": d["frontLegSwingDegrees"], "rearLegSwingDegrees": d["rearLegSwingDegrees"],
	})
	return Hop.apply(pose, motion, progress, 0.58, playback)

static func draw(ctx, parent: Transform3D, pose: Dictionary, _team: String) -> void:
	var previous: String = ctx.surface
	var rig := G.scale(parent, MODEL_SIZE_MULTIPLIER, MODEL_SIZE_MULTIPLIER, MODEL_SIZE_MULTIPLIER)
	rig = G.rotate(rig, -15.0, 0, 1, 0)
	_legs(ctx, rig, pose)
	var torso := G.translate(rig, 0, -pose["bodyCrouch"], 0)
	torso = G.rotate(torso, pose["bodyPitchDegrees"], 1, 0, 0)
	var breath: float = 1.0 + (ctx.life.breath if ctx.life != null else 0.0)
	ctx.surface = "CAT_COAT"
	ctx.draw_geometry(torso, M.mesh("body"), Vector3(0, 0.08, -0.12), Vector3(breath, 1, breath), M.TABBY_FUR, Vector3(90, 0, 0))
	var neck := G.translate(torso, 0, 0.38, 0)
	neck = G.rotate(neck, pose["neckPitchDegrees"], 1, 0, 0)
	neck = G.translate(neck, 0, -0.38, 0)
	ctx.surface = "CAT_COAT"
	ctx.draw_geometry(neck, M.mesh("neck"), Vector3.ZERO, Vector3.ONE, M.TABBY_FUR)
	# Lower the entire facial rig together; eyes, ears and jaw keep their own pivots.
	neck = G.translate(neck, 0, -0.18, 0)
	ctx.prepare_head(neck, 0.94, pose["headTiltDegrees"])
	Face.draw(ctx, ctx.head, pose)
	_tail(ctx, torso, pose["secondaryAppendageDegrees"])
	ctx.surface = previous
	ctx.limb_bend = Vector2.ZERO

## CatDrawHelpers.catBone: one hip-to-ankle skin bent at the solved knee by the shader.
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

static func _legs(ctx, parent: Transform3D, pose: Dictionary) -> void:
	# The upper roots move into the torso as the length is halved; fur, paws and creases follow.
	var root := G.translate(parent, 0, LEG_LENGTH_ANCHOR_Y * (1.0 - LEG_LENGTH_SCALE), 0)
	root = G.scale(root, 1, LEG_LENGTH_SCALE, 1)
	for leg in 4:
		var rear := leg < 2
		var side := -1.0 if leg % 2 == 0 else 1.0
		var x: float = side * (0.19 if rear else 0.135)
		var tuck = pose["rearLegTuckDegrees"] if rear else pose["frontLegTuckDegrees"]
		var swing = pose["rearLegSwingDegrees"] if rear else pose["frontLegSwingDegrees"]
		var s := Rig.solve(rear, side, float(swing if swing != null else pose["legSwingDegrees"]),
			float(tuck if tuck != null else pose["legTuckDegrees"]), pose["bodyCrouch"], tuck != null)
		var wash: float = pose["gestureWeight"] if (not rear and side < 0.0) else 0.0
		var ay: float = s[4] + wash * 1.12
		var az: float = s[5] + wash * 0.14
		ctx.surface = "CAT_COAT"
		_bone(ctx, root, M.mesh("limb"), x, s[0], s[1], ay, az, s[2], s[3], 0.10 if rear else 0.078, M.TABBY_FUR)
		ctx.surface = "SOFT_FUR"
		ctx.draw_geometry(root, M.mesh("paw"), Vector3(x, ay - 0.03, az + 0.035), Vector3.ONE, M.CREAM)
		ctx.surface = "MATTE"
		for toe in [-1.0, 1.0]:
			ctx.draw_part(root, Vector3(x + toe * 0.028, ay - 0.058, az + 0.14), Vector3(0.0025, 0.013, 0.003), M.STRIPE_DARK)

static func _tail(ctx, parent: Transform3D, sway: float) -> void:
	var chain := G.translate(parent, 0, 0.24, -0.57)
	chain = G.rotate(chain, -43.0, 1, 0, 0)
	chain = G.rotate(chain, -24.0, 0, 0, 1)
	var time: float = ctx.life.time_seconds if ctx.life != null else 0.0
	var idle: float = ctx.life.idle_weight if ctx.life != null else 0.0
	chain = G.rotate(chain, sway * 0.12 + sin(time * 1.05) * 2.0 * idle, 0, 1, 0)
	ctx.surface = "CAT_COAT"
	var radius := 0.075
	for i in 6:
		var wave := sin(time * 1.5 - i * 0.55) * (1.2 + i * 0.4) * idle
		var right_curve := -6.0 if i >= 2 else 0.0
		chain = G.rotate(chain, right_curve + sway * (0.10 + i * 0.025) + wave, 0, 0, 1)
		chain = G.rotate(chain, 14.0 + i * 2.0, 1, 0, 0)
		ctx.draw_geometry(chain, M.mesh("tailTip" if i == 5 else "tail"), Vector3(0, 0.085, 0), Vector3(radius, 0.18, radius), M.TABBY_FUR)
		chain = G.translate(chain, 0, 0.17, 0)
		radius *= 0.80
