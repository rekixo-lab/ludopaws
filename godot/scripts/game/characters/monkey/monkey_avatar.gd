@tool
extends RefCounted
## 2D fallback avatar. Port of MonkeyAvatarRenderer.kt.

const A = preload("res://scripts/game/characters/shared/avatar_draw.gd")

static func draw(canvas: CanvasItem, origin := Vector2.ZERO, size := 100.0) -> void:
	var c := A.new(canvas, origin, size)
	var fur := Color8(0x61, 0x30, 0x15)
	c.circle(17, 54, 15, fur)
	c.circle(83, 54, 15, fur)
	var ear := Color8(0xAB, 0x64, 0x40)
	c.circle(17, 54, 9, ear)
	c.circle(83, 54, 9, ear)
	c.oval(20, 16, 80, 87, fur)
	c.oval(43, 7, 58, 30, fur)
	var face := Color8(0xE0, 0xA8, 0x66)
	c.oval(25, 34, 52, 75, face)
	c.oval(48, 34, 75, 75, face)
	c.oval(29, 60, 71, 88, face)
	c.eye(37, 53)
	c.eye(63, 53)
	var dark := Color8(0x24, 0x11, 0x09)
	c.oval(45, 66, 55, 72, dark)
	c.ellipse_arc(36, 69, 64, 82, 10, 160, dark, 2)
