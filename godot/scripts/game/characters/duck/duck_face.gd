@tool
extends RefCounted
## Rounded skull, gaze-following eyes and a hinged lower bill. Port of DuckFaceRenderer.kt.

const G = preload("res://scripts/game/board3d/paw_geometry.gd")
const M = preload("res://scripts/game/characters/duck/duck_geometry.gd")
const Mat = preload("res://scripts/game/characters/shared/pet_jump.gd")

static func draw(ctx, parent: Transform3D, pose: Dictionary) -> void:
	var life = ctx.life
	ctx.surface = "COAT"
	ctx.draw_geometry(parent, M.mesh("head"), Vector3.ZERO, Vector3.ONE, M.YELLOW)
	for side in [-1.0, 1.0]:
		var x: float = side * 0.205
		var open: float = life.blink if life != null else 1.0
		var gx: float = (life.gaze_x if life != null else 0.0) * 0.45
		var gy: float = (life.gaze_y if life != null else 0.0) * 0.45
		ctx.surface = "COAT"
		ctx.draw_part(parent, Vector3(x, 1.015, 0.311), Vector3(0.110, 0.135, 0.049), M.BELLY)
		ctx.surface = "EYE"
		ctx.draw_part(parent, Vector3(x, 1.015, 0.337), Vector3(0.095, 0.120 * open, 0.033), M.EYE_GOLD)
		ctx.draw_part(parent, Vector3(x + gx, 1.015 + gy * open, 0.366), Vector3(0.071, 0.098 * open, 0.015), Mat.EYE_DARK)
		if open > 0.3:
			ctx.draw_part(parent, Vector3(x + gx - 0.025, 1.055 + gy, 0.379), Vector3(0.022, 0.027 * open, 0.008), Mat.WHITE)
	ctx.surface = "NOSE"
	ctx.draw_geometry(parent, M.mesh("bill"), Vector3(0, 0.815, 0.405), Vector3.ONE, M.ORANGE)
	for side in [-1.0, 1.0]:
		ctx.draw_part(parent, Vector3(side * 0.10, 0.861, 0.535), Vector3(0.020, 0.009, 0.012), M.BILL_SHADOW)
	var opening := clampf((life.mouth_open if life != null else 0.0) + pose["jawOpen"], 0.0, 1.0)
	ctx.surface = "MATTE"
	ctx.draw_part(parent, Vector3(0, 0.762, 0.578), Vector3(0.26, 0.006 + opening * 0.04, 0.025), M.BILL_SHADOW)
	var jaw := G.translate(parent, 0, 0.775, 0.235)
	jaw = G.rotate(jaw, -opening * 25.0, 1, 0, 0)
	jaw = G.translate(jaw, 0, -0.775, -0.235)
	ctx.surface = "NOSE"
	ctx.draw_geometry(jaw, M.mesh("bill"), Vector3(0, 0.749, 0.403), Vector3(0.94, 0.52, 0.94), M.BILL_LOWER)
	ctx.surface = "COAT"
	ctx.draw_geometry(parent, M.mesh("tuft"), Vector3(-0.045, 1.27, -0.025), Vector3.ONE, M.BELLY, Vector3(0, 0, -16))
	ctx.draw_geometry(parent, M.mesh("tuft"), Vector3(0.045, 1.27, -0.045), Vector3(1, 0.75, 1), M.YELLOW, Vector3(0, 0, 12))
