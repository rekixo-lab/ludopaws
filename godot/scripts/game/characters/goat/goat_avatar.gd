@tool
extends RefCounted
## 2D fallback avatar until the 3D portrait is ready. Port of GoatAvatarRenderer.kt.

const A = preload("res://scripts/game/characters/shared/avatar_draw.gd")

static func draw(canvas: CanvasItem, origin := Vector2.ZERO, size := 100.0) -> void:
	var c := A.new(canvas, origin, size)
	var horn := Color8(0xC9, 0x99, 0x58)
	c.triangle(31, 31, 35, 9, 43, 31, horn)
	c.triangle(57, 31, 65, 9, 69, 31, horn)
	c.oval(17, 34, 38, 53, Color8(0xD8, 0xD2, 0xC7))
	c.oval(62, 34, 83, 53, Color8(0xD8, 0xD2, 0xC7))
	c.oval(28, 18, 72, 72, Color8(0xF6, 0xE8, 0xCE))
	c.oval(34, 49, 66, 70, Color8(0xEC, 0xC8, 0xA4))
	c.eye(40, 42)
	c.eye(60, 42)
	c.oval(42, 56, 47, 60, Color8(0x6B, 0x55, 0x45))
	c.oval(53, 56, 58, 60, Color8(0x6B, 0x55, 0x45))
	c.triangle(41, 68, 59, 68, 50, 85, Color8(0xA8, 0x95, 0x7F))
