@tool
extends RefCounted
## Long independently flicking ears and a broad pale muzzle. Port of DonkeyFaceRenderer.kt.

const G = preload("res://scripts/game/board3d/paw_geometry.gd")
const M = preload("res://scripts/game/characters/donkey/donkey_geometry.gd")
const Mat = preload("res://scripts/game/characters/shared/pet_jump.gd")

static func draw(ctx, parent: Transform3D, pose: Dictionary) -> void:
	var life = ctx.life
	var t: float = life.time_seconds if life != null else 0.0
	var idle: float = life.idle_weight if life != null else 0.0
	ctx.surface = "SOFT_FUR"
	ctx.draw_geometry(parent, M.mesh("head"), Vector3.ZERO, Vector3.ONE, M.COAT)
	ctx.draw_geometry(parent, M.mesh("mane"), Vector3.ZERO, Vector3.ONE, M.DARK)
	ctx.surface = "COAT"
	ctx.draw_geometry(parent, M.mesh("forelock"), Vector3(-0.035, 1.26, 0.106), Vector3.ONE, M.DARK, Vector3(0, 0, -11))
	ctx.draw_geometry(parent, M.mesh("forelock"), Vector3(0.019, 1.245, 0.123), Vector3(0.68, 0.88, 0.68), M.MANE_LIGHT, Vector3(0, 0, 8))
	for i in 2:
		var side := -1.0 if i == 0 else 1.0
		var flick := sin(t * 8.0 + side) * pow(maxf(0.0, sin(t * 0.65 + side)), 12) * 8.0 * idle
		var ear := G.translate(parent, side * 0.20, 1.14, -0.045)
		var gesture: float = (24.0 if side < 0.0 else -8.0) * pose["gestureWeight"]
		ear = G.rotate(ear, -side * (13.0 - pose["primaryAppendageDegrees"] * 0.50 + flick + gesture), 0, 0, 1)
		ear = G.rotate(ear, side * 5.0 + flick * 0.45 - pose["neckPitchDegrees"] * 0.45, 1, 0, 0)
		ear = G.rotate(ear, side * (8.0 + flick * 0.5), 0, 1, 0)
		ctx.surface = "SOFT_FUR"
		ctx.draw_geometry(ear, M.mesh("ear"), Vector3.ZERO, Vector3.ONE, M.EAR_RIM)
		ctx.draw_geometry(ear, M.mesh("ear"), Vector3(0, 0.009, 0.022), Vector3(0.88, 0.97, 0.70), M.COAT)
		ctx.draw_geometry(ear, M.mesh("ear"), Vector3(0, 0.055, 0.052), Vector3(0.57, 0.80, 0.18), M.EAR_INNER)
	ctx.surface = "SOFT_FUR"
	ctx.draw_geometry(parent, M.mesh("muzzle"), Vector3.ZERO, Vector3.ONE, M.CREAM)
	for i in 2:
		var side := -1.0 if i == 0 else 1.0
		var x := side * 0.216
		var open: float = life.blink if life != null else 1.0
		var gx: float = (life.gaze_x if life != null else 0.0) * 0.35
		var gy: float = (life.gaze_y if life != null else 0.0) * 0.35
		ctx.surface = "SOFT_FUR"
		ctx.draw_part(parent, Vector3(x, 1.015, 0.228), Vector3(0.092, 0.104, 0.037), M.CREAM)
		ctx.surface = "MATTE"
		ctx.draw_part(parent, Vector3(x, 1.017, 0.250), Vector3(0.080, 0.093, 0.027), M.EAR_RIM)
		ctx.surface = "EYE"
		ctx.draw_part(parent, Vector3(x, 1.017, 0.269), Vector3(0.074, 0.087 * open, 0.021), M.EYE_AMBER)
		ctx.draw_part(parent, Vector3(x + gx, 1.017 + gy * open, 0.288), Vector3(0.049, 0.066 * open, 0.009), M.PUPIL)
		if open > 0.3:
			ctx.draw_part(parent, Vector3(x + gx - 0.019, 1.048 + gy, 0.298), Vector3(0.018, 0.021 * open, 0.006), Mat.WHITE)
		ctx.surface = "NOSE"
		ctx.draw_part(parent, Vector3(side * 0.142, 0.738, 0.604), Vector3(0.042, 0.029, 0.017), M.NOSTRIL_RIM, Vector3(0, 0, side * 24.0))
		ctx.draw_part(parent, Vector3(side * 0.142, 0.741, 0.618), Vector3(0.026, 0.018, 0.009), M.NOSE, Vector3(0, 0, side * 24.0))
		ctx.surface = "MATTE"
		ctx.draw_part(parent, Vector3(side * 0.083, 0.590, 0.603), Vector3(0.083, 0.004, 0.005), M.NOSE, Vector3(0, 0, side * 9.0))
		ctx.draw_part(parent, Vector3(x, 1.143, 0.208), Vector3(0.053, 0.009, 0.012), M.DARK, Vector3(0, 0, -side * 9.0))
	var chew := pow(maxf(0.0, sin(t * 0.43)), 18) * (0.5 + 0.5 * sin(t * 8.0)) * 0.16 * idle
	var open := clampf((life.mouth_open if life != null else 0.0) + chew + pose["jawOpen"] * 0.65, 0.0, 1.0)
	ctx.surface = "MATTE"
	ctx.draw_part(parent, Vector3(0, 0.575, 0.591), Vector3(0.14, 0.003 + open * 0.040, 0.015), M.PUPIL)
	var jaw := G.translate(parent, 0, 0.62, 0.22)
	jaw = G.rotate(jaw, -open * 24.0, 1, 0, 0)
	jaw = G.translate(jaw, 0, -0.62, -0.22)
	ctx.surface = "SOFT_FUR"
	ctx.draw_geometry(jaw, M.mesh("jaw"), Vector3.ZERO, Vector3.ONE, M.CREAM)
	if open > 0.24:
		ctx.surface = "MATTE"
		ctx.draw_part(parent, Vector3(0, 0.579, 0.606), Vector3(0.060, 0.019, 0.012), Mat.WHITE)
		ctx.surface = "NOSE"
		ctx.draw_part(jaw, Vector3(0, 0.541, 0.591), Vector3(0.065, 0.012, 0.033), M.EAR_INNER)
