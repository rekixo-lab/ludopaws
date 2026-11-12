@tool
extends RefCounted
## 2D fallback avatar. Port of HorseAvatarRenderer.kt.

const A = preload("res://scripts/game/characters/shared/avatar_draw.gd")

static func draw(canvas: CanvasItem, origin := Vector2.ZERO, size := 100.0) -> void:
	var c := A.new(canvas, origin, size)
	var coat := Color8(0xA6, 0x4A, 0x21)
	c.triangle(27, 35, 25, 6, 42, 28, coat)
	c.triangle(58, 28, 75, 6, 73, 35, coat)
	c.oval(25, 23, 75, 81, coat)
	c.oval(42, 16, 60, 40, Color8(0x38, 0x20, 0x17))
	var blaze := Color8(0xFA, 0xF0, 0xD4)
	c.oval(46, 34, 54, 66, blaze)
	c.eye(37, 46)
	c.eye(63, 46)
	c.oval(26, 61, 74, 87, Color8(0x65, 0x43, 0x35))
	c.oval(45, 63, 55, 75, blaze)
	var dark := Color8(0x24, 0x1C, 0x18)
	c.oval(33, 70, 41, 75, dark)
	c.oval(59, 70, 67, 75, dark)
	c.ellipse_arc(38, 76, 62, 83, 10, 160, dark, 1.5)
