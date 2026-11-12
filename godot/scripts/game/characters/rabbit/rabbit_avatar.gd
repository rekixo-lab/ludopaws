@tool
extends RefCounted
## 2D fallback avatar. Port of RabbitAvatarRenderer.kt.

const A = preload("res://scripts/game/characters/shared/avatar_draw.gd")

static func draw(canvas: CanvasItem, origin := Vector2.ZERO, size := 100.0) -> void:
	var c := A.new(canvas, origin, size)
	var fur := Color8(0xE8, 0xDB, 0xC2)
	c.oval(23, 2, 42, 54, fur)
	c.oval(58, 2, 77, 54, fur)
	var pink := Color8(0xD9, 0x95, 0x9D)
	c.oval(29, 9, 37, 44, pink)
	c.oval(63, 9, 71, 44, pink)
	c.oval(20, 38, 80, 91, fur)
	c.eye(36, 59)
	c.eye(64, 59)
	var cheek := Color8(0xFF, 0xF7, 0xE8)
	c.oval(32, 69, 50, 83, cheek)
	c.oval(50, 69, 68, 83, cheek)
	c.triangle(44, 70, 56, 70, 50, 77, Color8(0xBC, 0x5C, 0x6B))
	c.polygon([Vector2(45, 81), Vector2(49, 81), Vector2(49, 88), Vector2(45, 88)], Color.WHITE)
	c.polygon([Vector2(51, 81), Vector2(55, 81), Vector2(55, 88), Vector2(51, 88)], Color.WHITE)
	var whisker := Color8(0x75, 0x69, 0x5E)
	for side in [-1, 1]:
		for whisker_index in range(-1, 2):
			c.line(50 + side * 14, 76 + whisker_index * 3, 50 + side * 36, 76 + whisker_index * 7, whisker, 1)
