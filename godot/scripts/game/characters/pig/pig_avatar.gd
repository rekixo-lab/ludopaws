@tool
extends RefCounted
## 2D fallback avatar. Port of PigAvatarRenderer.kt.

const A = preload("res://scripts/game/characters/shared/avatar_draw.gd")

static func draw(canvas: CanvasItem, origin := Vector2.ZERO, size := 100.0) -> void:
	var c := A.new(canvas, origin, size)
	var ear := Color8(0xFF, 0xA3, 0xB0)
	c.triangle(21, 39, 17, 9, 44, 25, ear)
	c.triangle(56, 25, 83, 9, 79, 39, ear)
	var inner := Color8(0xE9, 0x59, 0x77)
	c.triangle(25, 30, 22, 17, 36, 27, inner)
	c.triangle(64, 27, 78, 17, 75, 30, inner)
	c.oval(16, 23, 84, 86, Color8(0xFF, 0xB4, 0xC0))
	c.eye(35, 47)
	c.eye(65, 47)
	c.oval(31, 57, 69, 78, Color8(0xFF, 0x81, 0x98))
	var nostril := Color8(0x70, 0x3B, 0x4E)
	c.oval(39, 63, 45, 72, nostril)
	c.oval(55, 63, 61, 72, nostril)
	c.ellipse_arc(41, 74, 59, 82, 10, 160, Color8(0xB5, 0x4A, 0x68), 1.5)
