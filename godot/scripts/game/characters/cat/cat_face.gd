@tool
extends RefCounted
## Ear roots and jaw are hinged; eyes (feline slit) and fine whiskers follow the skull.
## Port of CatFaceRenderer.kt.

const G = preload("res://scripts/game/board3d/paw_geometry.gd")
const M = preload("res://scripts/game/characters/cat/cat_geometry.gd")
const Mat = preload("res://scripts/game/characters/shared/pet_jump.gd")

static func draw(ctx, parent: Transform3D, pose: Dictionary) -> void:
	var life = ctx.life
	ctx.surface = "CAT_COAT"
	ctx.draw_geometry(parent, M.mesh("head"), Vector3.ZERO, Vector3.ONE, M.TABBY_FUR)
	ctx.surface = "SOFT_FUR"
	ctx.draw_geometry(parent, M.mesh("muzzle"), Vector3.ZERO, Vector3.ONE, M.CREAM)
	for i in 2:
		var side := -1.0 if i == 0 else 1.0
		_eye(ctx, parent, side)
		var t: float = life.time_seconds if life != null else 0.0
		var twitch := sin(t * 11.0 + side) * pow(maxf(0.0, sin(t * 0.61 + side)), 16) * 4.0 if life != null else 0.0
		var ear := G.translate(parent, side * 0.225, 1.15, -0.04)
		var primary: float = pose["primaryAppendageDegrees"]
		var ear_angle := 12.0 - primary * 1.4 if primary < 0.0 else 12.0 - primary * 0.35
		ear = G.rotate(ear, -side * (ear_angle + twitch), 0, 0, 1)
		ear = G.rotate(ear, -6.0, 1, 0, 0)
		ctx.surface = "CAT_COAT"
		ctx.draw_geometry(ear, M.mesh("ear"), Vector3.ZERO, Vector3(side, 1, 1), M.TABBY_FUR)
		ctx.surface = "MATTE"
		ctx.draw_geometry(ear, M.mesh("ear"), Vector3(0, 0.025, 0.060), Vector3(side * 0.66, 0.73, 0.20), M.EAR_PINK)
	ctx.surface = "NOSE"
	ctx.draw_geometry(parent, M.mesh("nose"), Vector3(0, 0.795, 0.350), Vector3.ONE, M.NOSE_PINK)
	ctx.surface = "MATTE"
	ctx.draw_part(parent, Vector3(0, 0.727, 0.361), Vector3(0.003, 0.021, 0.004), M.PUPIL_DARK)
	for side in [-1.0, 1.0]:
		ctx.draw_part(parent, Vector3(side * 0.025, 0.705, 0.353), Vector3(0.029, 0.004, 0.006), M.PUPIL_DARK, Vector3(0, 0, side * 14.0))
		for i in 3:
			ctx.draw_part(parent, Vector3(side * 0.23, 0.745 - i * 0.028, 0.33), Vector3(0.105, 0.0025, 0.0025), M.WHISKER, Vector3(0, 0, side * (12.0 - i * 12.0)))
	var open := clampf((life.mouth_open if life != null else 0.0) + pose["jawOpen"], 0.0, 1.0)
	var jaw := G.translate(parent, 0, 0.73, 0.19)
	jaw = G.rotate(jaw, -open * 25.0, 1, 0, 0)
	jaw = G.translate(jaw, 0, -0.73, -0.19)
	ctx.surface = "MATTE"
	ctx.draw_part(parent, Vector3(0, 0.663, 0.332), Vector3(0.082, 0.004 + open * 0.028, 0.012), M.PUPIL_DARK)
	if open > 0.05:
		ctx.surface = "SOFT_FUR"
		ctx.draw_geometry(jaw, M.mesh("jaw"), Vector3.ZERO, Vector3.ONE, M.CREAM)
	if open > 0.16:
		ctx.surface = "NOSE"
		ctx.draw_part(jaw, Vector3(0, 0.658, 0.335), Vector3(0.037, 0.010, 0.025), M.NOSE_PINK)

static func _eye(ctx, parent: Transform3D, side: float) -> void:
	var life = ctx.life
	var open := clampf(life.blink if life != null else 1.0, 0.0, 1.0)
	var gx := clampf((life.gaze_x if life != null else 0.0) * 0.32, -0.012, 0.012)
	var gy := clampf((life.gaze_y if life != null else 0.0) * 0.32, -0.009, 0.009) * open
	var rig := G.translate(parent, side * 0.16, 1.015, 0.224)
	rig = G.rotate(rig, side * 33.0, 0, 1, 0)
	rig = G.rotate(rig, -side * 9.0, 0, 0, 1)
	ctx.surface = "CAT_COAT"
	ctx.draw_part(rig, Vector3.ZERO, Vector3(0.088, 0.094, 0.024), M.TABBY_FUR)
	if open <= 0.04:
		return
	ctx.surface = "EYE"
	ctx.draw_part(rig, Vector3(0, 0, 0.020), Vector3(0.074, 0.080 * open, 0.018), M.EYE_GREEN)
	ctx.draw_part(rig, Vector3(gx, gy, 0.036), Vector3(0.020, 0.059 * open, 0.008), M.PUPIL_DARK)
	if open > 0.3:
		ctx.draw_part(rig, Vector3(gx - 0.013, gy + 0.026 * open, 0.045), Vector3(0.010, 0.013 * open, 0.004), Mat.WHITE)
