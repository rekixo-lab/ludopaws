@tool
extends RefCounted
## Lightweight fallback while the 3D portrait renders. Port of LumiGoatAvatarRenderer.

const A = preload("res://scripts/game/characters/shared/avatar_draw.gd")

static func draw(canvas: CanvasItem, origin := Vector2.ZERO, size := 100.0) -> void:
	var c := A.new(canvas, origin, size)
	var horn := Color8(0xA7, 0x9B, 0x85)
	c.oval(37, 12, 44, 31, horn)
	c.oval(56, 12, 63, 31, horn)
	var white := Color8(0xFF, 0xF6, 0xEF)
	c.oval(5, 33, 39, 54, white)
	c.oval(61, 33, 95, 54, white)
	c.oval(29, 22, 71, 85, white)
	var grey := Color8(0x9C, 0x96, 0x8C)
	c.oval(10, 37, 29, 47, grey)
	c.oval(71, 37, 90, 47, grey)
	var dark := Color8(0x3C, 0x39, 0x34)
	c.oval(35, 43, 43, 50, dark)
	c.oval(57, 43, 65, 50, dark)
	c.oval(43, 67, 57, 76, Color8(0x57, 0x56, 0x4E))
