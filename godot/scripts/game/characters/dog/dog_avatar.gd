@tool
extends RefCounted
## Synchronous 2D fallback avatar (100 x 100 design box) shown until a 3D portrait is ready.
## Port of DogAvatarRenderer.kt (CharacterAvatarContext.drawEye included).

static func draw(canvas: CanvasItem, origin := Vector2.ZERO, size := 100.0) -> void:
	var k := size / 100.0
	var tan := Color8(0xD8, 0x95, 0x58)
	var dark := Color8(0x8B, 0x55, 0x35)
	var cream := Color8(0xFF, 0xE4, 0xBB)
	_oval(canvas, origin, k, Rect2(18, 29, 18, 37), dark)
	_oval(canvas, origin, k, Rect2(64, 29, 18, 37), dark)
	_oval(canvas, origin, k, Rect2(25, 20, 50, 53), tan)
	_oval(canvas, origin, k, Rect2(33, 48, 34, 24), cream)
	_eye(canvas, origin, k, Vector2(40, 43))
	_eye(canvas, origin, k, Vector2(60, 43))
	_oval(canvas, origin, k, Rect2(45, 54, 10, 8), Color8(0x31, 0x25, 0x1F))
	var stroke := Color8(0x5B, 0x3A, 0x2A)
	canvas.draw_line(origin + Vector2(50, 62) * k, origin + Vector2(50, 66) * k, stroke, 2.4 * k, true)
	canvas.draw_arc(origin + Vector2(46, 65) * k, 4.0 * k, deg_to_rad(15), deg_to_rad(85), 12, stroke, 2.4 * k, true)
	canvas.draw_arc(origin + Vector2(54, 65) * k, 4.0 * k, deg_to_rad(95), deg_to_rad(165), 12, stroke, 2.4 * k, true)

static func _oval(canvas: CanvasItem, origin: Vector2, k: float, r: Rect2, color: Color) -> void:
	var points := PackedVector2Array()
	var c := r.get_center()
	for i in 24:
		var a := i * TAU / 24.0
		points.append(origin + (c + Vector2(cos(a) * r.size.x * 0.5, sin(a) * r.size.y * 0.5)) * k)
	canvas.draw_colored_polygon(points, color)

## CharacterAvatarContext.drawEye
static func _eye(canvas: CanvasItem, origin: Vector2, k: float, p: Vector2) -> void:
	canvas.draw_circle(origin + p * k, 5.5 * k, Color8(0x22, 0x25, 0x2A), true, -1, true)
	canvas.draw_circle(origin + (p - Vector2(1.8, 1.8)) * k, 1.7 * k, Color.WHITE, true, -1, true)
