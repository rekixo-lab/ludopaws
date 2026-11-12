@tool
extends RefCounted
## Lightweight donkey fallback while the 3D portrait is prepared. Port of DonkeyAvatarRenderer.kt.

const A = preload("res://scripts/game/characters/shared/avatar_draw.gd")

static func draw(canvas: CanvasItem, origin := Vector2.ZERO, size := 100.0) -> void:
	var c := A.new(canvas, origin, size)
	var ear := Color8(0x85, 0x8D, 0x98)
	var inner := Color8(0xB7, 0x84, 0x84)
	c.rotate(-13, 35, 36)
	c.oval(27, 2, 42, 48, ear)
	c.oval(31, 7, 38, 38, inner)
	c.restore()
	c.rotate(13, 65, 36)
	c.oval(58, 2, 73, 48, ear)
	c.oval(62, 7, 69, 38, inner)
	c.restore()
	c.oval(24, 31, 76, 82, Color8(0x91, 0x98, 0xA1))
	c.oval(44, 28, 56, 42, Color8(0x35, 0x37, 0x3D))
	c.eye(36, 52)
	c.eye(64, 52)
	c.oval(26, 61, 74, 87, Color8(0xE8, 0xE3, 0xD5))
	var dark := Color8(0x42, 0x45, 0x4B)
	c.oval(33, 67, 40, 71, dark)
	c.oval(60, 67, 67, 71, dark)
	c.ellipse_arc(37, 72, 63, 81, 0, 180, dark, 1.5)
