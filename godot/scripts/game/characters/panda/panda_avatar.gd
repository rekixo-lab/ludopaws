@tool
extends RefCounted
## 2D fallback avatar. Port of PandaAvatarRenderer.kt.

const A = preload("res://scripts/game/characters/shared/avatar_draw.gd")

static func draw(canvas: CanvasItem, origin := Vector2.ZERO, size := 100.0) -> void:
	var c := A.new(canvas, origin, size)
	var dark := Color8(0x15, 0x19, 0x22)
	c.circle(26, 25, 14, dark)
	c.circle(74, 25, 14, dark)
	c.oval(15, 22, 85, 91, Color8(0xF0, 0xEE, 0xE4))
	for side in [-1, 1]:
		var x: float = 50.0 + side * 16.0
		c.rotate(-side * 23.0, x, 55)
		c.oval(x - 12, 41, x + 12, 70, dark)
		c.restore()
	c.eye(34, 54)
	c.eye(66, 54)
	c.oval(43, 66, 57, 75, dark)
	c.line(50, 74, 50, 79, dark, 2)
	c.ellipse_arc(40, 73, 50, 84, 0, 130, dark, 2)
	c.ellipse_arc(50, 73, 60, 84, 50, 130, dark, 2)
