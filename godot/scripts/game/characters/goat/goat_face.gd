@tool
extends RefCounted
## Skull, jaw, ears and beard with proper pivots; amber irises with horizontal pupils.
## Port of GoatFaceRenderer.kt.

const G = preload("res://scripts/game/board3d/paw_geometry.gd")
const M = preload("res://scripts/game/characters/goat/goat_geometry.gd")
const Mat = preload("res://scripts/game/characters/shared/pet_jump.gd")

static func draw(ctx, parent: Transform3D, pose: Dictionary) -> void:
	var life = ctx.life
	ctx.surface = "COAT"
	var flare := 1.0 + clampf((life.breath if life != null else 0.0) * 4.0, -0.1, 0.15)
	ctx.draw_geometry(parent, M.mesh("head"), Vector3.ZERO, Vector3.ONE, M.CREAM)
	ctx.draw_part(parent, Vector3(0, 0.91, 0.22), Vector3(0.105, 0.23, 0.11), M.IVORY, Vector3(-15, 0, 0))
	ctx.draw_geometry(parent, M.mesh("muzzle"), Vector3(0, 0.755, 0.355), Vector3.ONE, M.MUZZLE)
	for side in [-1.0, 1.0]:
		_eye(ctx, parent, side)
		_ear(ctx, parent, side, pose)
		ctx.surface = "GOAT_HORN"
		ctx.draw_geometry(parent, M.mesh("horn"), Vector3(side * 0.18, 1.245, -0.10), Vector3(side, 1, 1), M.HORN, Vector3(-9, 0, -side * 10.0))
	ctx.surface = "NOSE"
	ctx.draw_part(parent, Vector3(0, 0.735, 0.505), Vector3(0.13, 0.055, 0.055), M.NOSE_TAUPE)
	for side in [-1.0, 1.0]:
		ctx.draw_part(parent, Vector3(side * 0.071, 0.735, 0.552), Vector3(0.028 * flare, 0.013 * flare, 0.014), Mat.NOSE_DARK, Vector3(0, 0, -side * 18.0))
	ctx.surface = "MATTE"
	ctx.draw_part(parent, Vector3(0, 0.691, 0.533), Vector3(0.006, 0.025, 0.008), M.LIP)
	_jaw(ctx, parent, pose)
	ctx.surface = "COAT"
	ctx.draw_geometry(parent, M.mesh("tuft"), Vector3(-0.075, 1.275, 0.13), Vector3(1, 0.7, 1), M.CREAM, Vector3(0, 0, -17))
	ctx.draw_geometry(parent, M.mesh("tuft"), Vector3(0.035, 1.29, 0.14), Vector3(1, 0.8, 1), M.CREAM, Vector3(0, 0, 10))

static func _eye(ctx, parent: Transform3D, side: float) -> void:
	var life = ctx.life
	var x: float = side * 0.248
	var open: float = life.blink if life != null else 1.0
	var gx: float = (life.gaze_x if life != null else 0.0) * 0.38
	var gy: float = (life.gaze_y if life != null else 0.0) * 0.35
	ctx.surface = "MATTE"
	ctx.draw_part(parent, Vector3(x, 1.035, 0.209), Vector3(0.092, 0.067, 0.045), M.EYE_RIM, Vector3(0, side * 13.0, 0))
	ctx.surface = "EYE"
	ctx.draw_part(parent, Vector3(x, 1.035, 0.239), Vector3(0.079, 0.056 * open, 0.034), M.EYE_GOLD, Vector3(0, side * 13.0, 0))
	ctx.draw_part(parent, Vector3(x + gx, 1.035 + gy * open, 0.271), Vector3(0.051, 0.011 * open, 0.009), Mat.EYE_DARK)
	if open > 0.3:
		ctx.draw_part(parent, Vector3(x + gx - 0.018, 1.049 + gy, 0.281), Vector3(0.011, 0.010 * open, 0.005), Mat.WHITE)
	ctx.surface = "COAT"
	ctx.draw_part(parent, Vector3(x, 1.113, 0.222), Vector3(0.099, 0.024, 0.047), M.CREAM, Vector3(0, 0, -side * 8.0))
	ctx.draw_part(parent, Vector3(x, 0.976, 0.235), Vector3(0.081, 0.013, 0.025), M.IVORY)
	ctx.draw_part(parent, Vector3(side * 0.19, 1.182, 0.185), Vector3(0.034, 0.064, 0.020), M.FUR_SHADE, Vector3(0, 0, -side * 24.0))

static func _ear(ctx, parent: Transform3D, side: float, pose: Dictionary) -> void:
	var life = ctx.life
	var t: float = life.time_seconds if life != null else 0.0
	var flick := sin(t * 9.0 + side) * pow(maxf(0.0, sin(t * 0.71 + side * 2.0)), 18) * 5.0 if life != null else 0.0
	var listen := sin(t * 0.37 + side * 1.9) * 4.0 + sin(t * 0.91 - side) * 1.5 if life != null else 0.0
	var ear := G.translate(parent, side * 0.29, 1.12, -0.06)
	ear = G.rotate(ear, -side * (68.0 + pose["primaryAppendageDegrees"] * 0.55 + flick + listen), 0, 0, 1)
	ear = G.rotate(ear, -10.0 + pose["primaryAppendageDegrees"] * 0.3, 1, 0, 0)
	ctx.surface = "COAT"
	ctx.draw_geometry(ear, M.mesh("ear"), Vector3.ZERO, Vector3(0.9, 0.84, 1), M.EAR_TAN)
	ctx.surface = "MATTE"
	ctx.draw_geometry(ear, M.mesh("ear"), Vector3(0, 0.04, 0.034), Vector3(0.62, 0.70, 0.24), M.EAR_INNER)

static func _jaw(ctx, parent: Transform3D, pose: Dictionary) -> void:
	var life = ctx.life
	var t: float = life.time_seconds if life != null else 0.0
	var chew := 0.0
	if life != null and life.is_idle:
		chew = pow(maxf(0.0, sin(t * 0.43)), 12) * (0.5 + 0.5 * sin(t * 7.0)) * 0.08
	var opening := clampf((life.mouth_open if life != null else 0.0) + pose["jawOpen"] + chew, 0.0, 1.0)
	var jaw := G.translate(parent, 0, 0.80, 0.12)
	jaw = G.rotate(jaw, -opening * 24.0, 1, 0, 0)
	# Goats chew cud sideways, not up and down.
	jaw = G.rotate(jaw, chew * sin(t * 7.0 + 1.2) * 90.0, 0, 1, 0)
	jaw = G.translate(jaw, 0, -0.80, -0.12)
	ctx.surface = "MATTE"
	ctx.draw_part(parent, Vector3(0, 0.653, 0.475), Vector3(0.12, 0.012 + opening * 0.042, 0.035), M.LIP)
	ctx.surface = "COAT"
	ctx.draw_geometry(jaw, M.mesh("jaw"), Vector3(0, 0.636, 0.355), Vector3.ONE, M.IVORY)
	if opening > 0.15:
		ctx.surface = "NOSE"
		ctx.draw_part(jaw, Vector3(0, 0.665, 0.465), Vector3(0.052, 0.016, 0.052), M.TONGUE)
	var beard := G.translate(jaw, 0, 0.57, 0.35)
	beard = G.rotate(beard, pose["secondaryAppendageDegrees"] * 0.45, 0, 0, 1)
	beard = G.rotate(beard, pose["secondaryAppendageDegrees"] * 0.23, 1, 0, 0)
	ctx.surface = "COAT"
	ctx.draw_geometry(beard, M.mesh("beard"), Vector3.ZERO, Vector3.ONE, M.BEARD)
	ctx.draw_geometry(beard, M.mesh("beard"), Vector3(-0.045, 0.025, 0.015), Vector3(0.6, 0.8, 0.7), M.IVORY, Vector3(0, 0, -12))
	ctx.draw_geometry(beard, M.mesh("beard"), Vector3(0.048, 0.02, 0.01), Vector3(0.55, 0.74, 0.65), M.FUR_LIGHT, Vector3(0, 0, 13))
