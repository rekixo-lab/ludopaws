@tool
class_name MeadowIcon
extends Control
## A code-drawn glyph (robot, dice, people, bars, pencil, paw, play, back, gear...).

@export var glyph := "paw":
	set(value):
		glyph = value
		queue_redraw()
@export var color := Color("#f6ead0"):
	set(value):
		color = value
		queue_redraw()
@export var medallion := false:
	set(value):
		medallion = value
		queue_redraw()
@export var shadow := true:
	set(value):
		shadow = value
		queue_redraw()

func _init() -> void:
	custom_minimum_size = Vector2(28, 28)
	mouse_filter = MOUSE_FILTER_IGNORE
	size_flags_vertical = SIZE_SHRINK_CENTER

func _draw() -> void:
	var s := minf(size.x, size.y)
	if medallion:
		var center := size * 0.5
		_medallion(center, s * 0.5 - 1.0)
		s *= 0.66
	if shadow:
		MeadowDraw.glyph(self, glyph, size * 0.5 + Vector2(0, s * 0.06), s, Color(0, 0, 0, 0.35))
	MeadowDraw.glyph(self, glyph, size * 0.5, s, color)


func _medallion(center: Vector2, radius: float) -> void:
	draw_circle(center + Vector2(0, 2), radius, Color("#08150d"), true, -1, true)
	var bounds := Rect2(center - Vector2.ONE * radius, Vector2.ONE * radius * 2.0)
	MeadowDraw.gradient_fill(self, MeadowDraw.ellipse_points(center, Vector2.ONE * radius), bounds, Color("#f4d58b"), Color("#83602d"))
	MeadowDraw.gradient_fill(self, MeadowDraw.ellipse_points(center, Vector2.ONE * (radius - 1.8)), bounds, Color("#737b37"), Color("#303819"))
	MeadowDraw.gradient_fill(self, MeadowDraw.ellipse_points(center, Vector2.ONE * (radius - 3.3)), bounds, Color("#4e6027"), Color("#293716"))
	draw_arc(center, radius - 0.65, PI * 1.15, PI * 1.86, 20, Color("#ffe8a9"), 0.7, true)
