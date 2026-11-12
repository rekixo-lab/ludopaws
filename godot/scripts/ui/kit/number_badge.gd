@tool
class_name NumberBadge
extends Control
## Beige seat-number disc used in player lists.

@export var number := 1:
	set(value):
		number = value
		queue_redraw()

func _init() -> void:
	custom_minimum_size = Vector2(40, 40)
	size_flags_vertical = SIZE_SHRINK_CENTER
	mouse_filter = MOUSE_FILTER_IGNORE

func _draw() -> void:
	var center := size * 0.5
	var radius := minf(size.x, size.y) * 0.5 - 1.5
	draw_circle(center + Vector2(0, 2.5), radius, Color(0, 0, 0, 0.4))
	draw_circle(center, radius + 1.0, Color("#5c4428"))
	MeadowDraw.gradient_fill(self, MeadowDraw.ellipse_points(center, Vector2(radius - 1.0, radius - 1.0)), Rect2(center - Vector2(radius, radius), Vector2(radius, radius) * 2.0), Color("#efdcb2"), Color("#c9ad7b"))
	draw_arc(center, radius - 3.0, PI * 1.1, PI * 1.9, 18, Color(1, 1, 1, 0.5), 1.5, true)
	var font := UIFonts.display()
	var text := str(number)
	var font_size := 21
	var text_size := font.get_string_size(text, HORIZONTAL_ALIGNMENT_LEFT, -1, font_size)
	draw_string(font, center + Vector2(-text_size.x * 0.5, font.get_ascent(font_size) * 0.36), text, HORIZONTAL_ALIGNMENT_LEFT, -1, font_size, Color("#3a2614"))
