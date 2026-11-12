@tool
extends RefCounted
## 2D fallback avatar. Port of GiraffeAvatarRenderer.kt.

const A = preload("res://scripts/game/characters/shared/avatar_draw.gd")

static func draw(canvas: CanvasItem, origin := Vector2.ZERO, size := 100.0) -> void:
	var c := A.new(canvas, origin, size)
	var coat := Color8(0xF2, 0xAE, 0x4A)
	_round_rect(c, 39, 47, 61, 96, 10, coat)
	c.triangle(32, 44, 13, 30, 36, 31, coat)
	c.triangle(68, 44, 87, 30, 64, 31, coat)
	_round_rect(c, 38, 12, 45, 36, 4, coat)
	_round_rect(c, 55, 12, 62, 36, 4, coat)
	c.oval(28, 25, 72, 68, coat)
	var patch := Color8(0x96, 0x50, 0x23)
	c.circle(41, 13, 5.5, patch)
	c.circle(59, 13, 5.5, patch)
	c.oval(41, 69, 49, 78, patch)
	c.oval(52, 79, 59, 89, patch)
	c.eye(39, 45)
	c.eye(61, 45)
	c.oval(33, 51, 67, 69, Color8(0xFF, 0xDE, 0x9D))
	var dark := Color8(0x59, 0x37, 0x20)
	c.circle(44, 58, 1.6, dark)
	c.circle(56, 58, 1.6, dark)
	c.ellipse_arc(43, 58, 57, 65, 10, 160, dark, 1.4)

## canvas.drawRoundRect(RectF(l, t, r, b), radius, radius, fill)
static func _round_rect(c, l: float, t: float, r: float, b: float, radius: float, color: Color) -> void:
	var points: Array = []
	var corners := [[r - radius, b - radius, 0.0], [l + radius, b - radius, 90.0], [l + radius, t + radius, 180.0], [r - radius, t + radius, 270.0]]
	for corner in corners:
		for i in 7:
			var a := deg_to_rad(corner[2] + i * 15.0)
			points.append(Vector2(corner[0] + cos(a) * radius, corner[1] + sin(a) * radius))
	c.polygon(points, color)
