@tool
extends RefCounted
## CharacterAvatarContext helpers for the 2D fallback avatars (100 x 100 design box).
## Port of feature/characters/rendering/CharacterAvatarRenderer.kt.

var canvas: CanvasItem
var origin := Vector2.ZERO
var k := 1.0
## canvas.save(); canvas.rotate(deg, px, py) ... canvas.restore()
var _rotation := 0.0
var _pivot := Vector2.ZERO

func _init(target: CanvasItem, at := Vector2.ZERO, size := 100.0) -> void:
	canvas = target
	origin = at
	k = size / 100.0

func p(x: float, y: float) -> Vector2:
	var q := Vector2(x, y)
	if _rotation != 0.0:
		q = _pivot + (q - _pivot).rotated(deg_to_rad(_rotation))
	return origin + q * k

func rotate(degrees: float, px: float, py: float) -> void:
	_rotation = degrees
	_pivot = Vector2(px, py)

func restore() -> void:
	_rotation = 0.0

## canvas.drawOval(RectF(l, t, r, b))
func oval(l: float, t: float, r: float, b: float, color: Color) -> void:
	var points := PackedVector2Array()
	var c := Vector2((l + r) * 0.5, (t + b) * 0.5)
	for i in 28:
		var a := i * TAU / 28.0
		points.append(p(c.x + cos(a) * (r - l) * 0.5, c.y + sin(a) * (b - t) * 0.5))
	canvas.draw_colored_polygon(points, color)

func circle(x: float, y: float, radius: float, color: Color) -> void:
	canvas.draw_circle(p(x, y), radius * k, color, true, -1, true)

func polygon(points: Array, color: Color) -> void:
	var out := PackedVector2Array()
	for q in points:
		out.append(p(q.x, q.y))
	canvas.draw_colored_polygon(out, color)

func triangle(x1: float, y1: float, x2: float, y2: float, x3: float, y3: float, color: Color) -> void:
	polygon([Vector2(x1, y1), Vector2(x2, y2), Vector2(x3, y3)], color)

func line(x1: float, y1: float, x2: float, y2: float, color: Color, width: float) -> void:
	canvas.draw_line(p(x1, y1), p(x2, y2), color, width * k, true)

## canvas.drawArc(RectF(l, t, r, b), start, sweep, false, stroke) for a circular rect.
func arc(l: float, t: float, r: float, b: float, start_deg: float, sweep_deg: float, color: Color, width: float) -> void:
	canvas.draw_arc(p((l + r) * 0.5, (t + b) * 0.5), (r - l) * 0.5 * k, deg_to_rad(start_deg), deg_to_rad(start_deg + sweep_deg), 16, color, width * k, true)

## canvas.drawArc(RectF(l, t, r, b), start, sweep, false, stroke) for any ellipse.
func ellipse_arc(l: float, t: float, r: float, b: float, start_deg: float, sweep_deg: float, color: Color, width: float) -> void:
	var points := PackedVector2Array()
	var c := Vector2((l + r) * 0.5, (t + b) * 0.5)
	for i in 21:
		var a := deg_to_rad(start_deg + sweep_deg * i / 20.0)
		points.append(p(c.x + cos(a) * (r - l) * 0.5, c.y + sin(a) * (b - t) * 0.5))
	canvas.draw_polyline(points, color, width * k, true)

## CharacterAvatarContext.drawEye
func eye(x: float, y: float) -> void:
	circle(x, y, 5.5, Color8(0x22, 0x25, 0x2A))
	circle(x - 1.8, y - 1.8, 1.7, Color.WHITE)

static func argb(value: int) -> Color:
	return Color8((value >> 16) & 255, (value >> 8) & 255, value & 255, (value >> 24) & 255 if value > 0xFFFFFF else 255)
