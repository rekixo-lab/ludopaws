@tool
extends RefCounted
## 2D fallback avatar until the 3D portrait is ready. Port of CatAvatarRenderer.kt.

const A = preload("res://scripts/game/characters/shared/avatar_draw.gd")

static func draw(canvas: CanvasItem, origin := Vector2.ZERO, size := 100.0) -> void:
	var c := A.new(canvas, origin, size)
	var fur := Color8(0xB5, 0xB0, 0xA3)
	var charcoal := Color8(0x30, 0x2E, 0x29)
	var pink := Color8(0xBC, 0x96, 0x91)
	c.triangle(25, 38, 30, 11, 45, 30, fur)
	c.triangle(55, 30, 70, 11, 75, 38, fur)
	c.triangle(30, 30, 33, 18, 40, 29, pink)
	c.triangle(60, 29, 67, 18, 70, 30, pink)
	c.oval(24, 22, 76, 74, fur)
	c.oval(33, 49, 67, 72, Color8(0xD6, 0xD1, 0xC2))
	c.line(45, 27, 43, 37, charcoal, 1.3)
	c.line(50, 25, 50, 36, charcoal, 1.3)
	c.line(55, 27, 57, 37, charcoal, 1.3)
	c.eye(40, 44)
	c.eye(60, 44)
	c.triangle(46, 55, 54, 55, 50, 61, pink)
	c.line(34, 58, 22, 55, charcoal, 1.7)
	c.line(34, 62, 21, 63, charcoal, 1.7)
	c.line(66, 58, 78, 55, charcoal, 1.7)
	c.line(66, 62, 79, 63, charcoal, 1.7)
