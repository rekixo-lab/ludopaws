@tool
extends RefCounted
## 2D fallback avatar. Port of ElephantAvatarRenderer.kt.

const A = preload("res://scripts/game/characters/shared/avatar_draw.gd")

static func draw(canvas: CanvasItem, origin := Vector2.ZERO, size := 100.0) -> void:
	var c := A.new(canvas, origin, size)
	var ear := Color8(0x8F, 0x9F, 0xAB)
	c.oval(5, 22, 40, 75, ear)
	c.oval(60, 22, 95, 75, ear)
	var inner := Color8(0xB3, 0x92, 0x98)
	c.oval(11, 29, 34, 68, inner)
	c.oval(66, 29, 89, 68, inner)
	c.oval(24, 15, 76, 73, Color8(0xA0, 0xB0, 0xBA))
	c.eye(36, 44)
	c.eye(64, 44)
	var ivory := Color8(0xFA, 0xF0, 0xCF)
	c.triangle(32, 61, 40, 62, 32, 81, ivory)
	c.triangle(60, 62, 68, 61, 68, 81, ivory)
	# Round-capped trunk stroke.
	c.line(50, 61, 50, 82, ear, 15)
	c.circle(50, 61, 7.5, ear)
	c.circle(50, 82, 7.5, ear)
	c.ellipse_arc(50, 74, 70, 92, 0, 180, ear, 15)
	c.circle(70, 83, 7.5, ear)
