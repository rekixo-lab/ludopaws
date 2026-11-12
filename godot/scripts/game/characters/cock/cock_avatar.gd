@tool
extends RefCounted
## 2D fallback avatar. Port of CockAvatarRenderer.kt.

const A = preload("res://scripts/game/characters/shared/avatar_draw.gd")

static func draw(canvas: CanvasItem, origin := Vector2.ZERO, size := 100.0) -> void:
	var c := A.new(canvas, origin, size)
	var red := Color8(0xD4, 0x18, 0x18)
	c.oval(41, 5, 53, 29, red)
	c.oval(49, 2, 61, 28, red)
	c.oval(57, 9, 68, 31, red)
	c.oval(24, 23, 76, 75, Color8(0xFF, 0xBA, 0x36))
	c.oval(40, 65, 49, 85, red)
	c.oval(51, 65, 60, 85, red)
	c.eye(38, 45)
	c.eye(62, 45)
	c.triangle(39, 56, 61, 56, 50, 72, Color8(0xF1, 0x8A, 0x24))
