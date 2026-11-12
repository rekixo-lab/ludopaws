@tool
extends RefCounted
## 2D fallback avatar until the 3D portrait is ready. Port of DuckAvatarRenderer.kt.

const A = preload("res://scripts/game/characters/shared/avatar_draw.gd")

static func draw(canvas: CanvasItem, origin := Vector2.ZERO, size := 100.0) -> void:
	var c := A.new(canvas, origin, size)
	var yellow_dark := Color8(0xF3, 0xBB, 0x2D)
	c.triangle(43, 23, 49, 8, 54, 24, yellow_dark)
	c.triangle(49, 23, 58, 11, 59, 28, yellow_dark)
	c.oval(23, 18, 77, 72, Color8(0xFF, 0xD9, 0x4D))
	c.eye(39, 42)
	c.eye(61, 42)
	var bill := StyleBoxFlat.new()
	bill.bg_color = Color8(0xF1, 0x8A, 0x24)
	bill.set_corner_radius_all(roundi(8 * c.k))
	canvas.draw_style_box(bill, Rect2(c.p(31, 53), Vector2(38, 16) * c.k))
	c.line(35, 61, 65, 61, Color8(0xD9, 0x6D, 0x16), 2)
