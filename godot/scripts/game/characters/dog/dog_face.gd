@tool
extends RefCounted
## Hinged floppy ears and jaw stay with the head; gaze and blink use shared life state.
## Port of DogFaceRenderer.kt.

const G = preload("res://scripts/game/board3d/paw_geometry.gd")
const M = preload("res://scripts/game/characters/dog/dog_geometry.gd")
const Mat = preload("res://scripts/game/characters/shared/pet_jump.gd")

static func draw(ctx, parent: Transform3D, pose: Dictionary) -> void:
	var life = ctx.life
	ctx.surface = "COAT"
	ctx.draw_geometry(parent, M.mesh("head"), Vector3.ZERO, Vector3.ONE, M.TAN)
	ctx.draw_geometry(parent, M.mesh("muzzle"), Vector3.ZERO, Vector3.ONE, M.CREAM)
	var t: float = life.time_seconds if life != null else 0.0
	for side in [-1.0, 1.0]:
		_eye(ctx, parent, side)
		var listen := sin(t * 0.8 + side * 1.7) * 2.5 if life != null else 0.0
		var ear := G.translate(parent, side * 0.365, 1.12, -0.035)
		ear = G.rotate(ear, side * (12.0 + pose["primaryAppendageDegrees"] * 0.7 + listen), 0, 0, 1)
		ear = G.rotate(ear, -8.0 + pose["primaryAppendageDegrees"] * 0.28, 1, 0, 0)
		ctx.surface = "COAT"
		ctx.draw_geometry(ear, M.mesh("ear"), Vector3.ZERO, Vector3(side, 1, 1), M.EAR_BROWN)
	ctx.surface = "NOSE"
	ctx.draw_part(parent, Vector3(0, 0.83, 0.595), Vector3(0.125, 0.080, 0.065), Mat.NOSE_DARK)
	ctx.draw_part(parent, Vector3(-0.031, 0.859, 0.651), Vector3(0.027, 0.013, 0.008), Mat.NOSE_HIGHLIGHT)
	ctx.surface = "MATTE"
	ctx.draw_part(parent, Vector3(0, 0.737, 0.588), Vector3(0.006, 0.031, 0.009), M.LIP)
	var pant := 0.0
	if life != null and life.is_idle:
		pant = pow(maxf(0.0, sin(t * 0.63)), 6) * (0.12 + 0.05 * sin(t * 5.0))
	var open := clampf((life.mouth_open if life != null else 0.0) + pose["jawOpen"] + pant, 0.0, 1.0)
	var jaw := G.translate(parent, 0, 0.74, 0.20)
	jaw = G.rotate(jaw, -open * 27.0, 1, 0, 0)
	jaw = G.translate(jaw, 0, -0.74, -0.20)
	ctx.draw_part(parent, Vector3(0, 0.685, 0.539), Vector3(0.205, 0.010 + open * 0.06, 0.036), M.LIP)
	ctx.surface = "COAT"
	ctx.draw_geometry(jaw, M.mesh("jaw"), Vector3.ZERO, Vector3.ONE, M.CREAM)
	if open > 0.09:
		ctx.surface = "NOSE"
		ctx.draw_part(jaw, Vector3(0, 0.675, 0.535), Vector3(0.080, 0.016, 0.073), M.TONGUE_PINK)

static func _eye(ctx, parent: Transform3D, side: float) -> void:
	var life = ctx.life
	var x: float = side * 0.215
	var open: float = life.blink if life != null else 1.0
	var gx: float = (life.gaze_x if life != null else 0.0) * 0.5
	var gy: float = (life.gaze_y if life != null else 0.0) * 0.5
	ctx.surface = "COAT"
	ctx.draw_part(parent, Vector3(x, 1.015, 0.285), Vector3(0.115, 0.132, 0.052), M.EAR_BROWN)
	ctx.surface = "EYE"
	ctx.draw_part(parent, Vector3(x, 1.015, 0.313), Vector3(0.101, 0.118 * open, 0.038), M.IRIS)
	ctx.draw_part(parent, Vector3(x + gx, 1.015 + gy * open, 0.346), Vector3(0.067, 0.087 * open, 0.015), Mat.EYE_DARK)
	if open > 0.3:
		ctx.draw_part(parent, Vector3(x + gx - 0.026, 1.055 + gy, 0.360), Vector3(0.021, 0.025 * open, 0.008), Mat.WHITE)
	ctx.surface = "COAT"
	ctx.draw_part(parent, Vector3(x, 1.17, 0.249), Vector3(0.100, 0.025, 0.036), M.CREAM, Vector3(0, 0, side * 8.0))
