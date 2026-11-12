@tool
class_name AnimalChoice
extends Button
## Selectable animal tile: a fluffy paw on a wooden token, name underneath, and a mint
## frame with a check badge when selected. The paw is a stand-in for the code-built
## character portrait that will come from the live game.

@export var animal_name := "Animal":
	set(value):
		animal_name = value
		queue_redraw()
@export var fur := Color("#e0853d"):
	set(value):
		fur = value
		queue_redraw()
@export var pads := Color("#3a2416"):
	set(value):
		pads = value
		queue_redraw()
@export var tuft := Color.TRANSPARENT:
	set(value):
		tuft = value
		queue_redraw()
@export var selected := false:
	set(value):
		selected = value
		queue_redraw()

func _init() -> void:
	custom_minimum_size = Vector2(84, 118)
	size_flags_horizontal = SIZE_EXPAND_FILL
	focus_mode = FOCUS_ALL
	var clear := StyleBoxEmpty.new()
	for state in ["normal", "hover", "pressed", "focus", "hover_pressed"]:
		add_theme_stylebox_override(state, clear)

func _draw() -> void:
	var tile := minf(size.x - 6.0, 84.0)
	var frame := Rect2(Vector2((size.x - tile) * 0.5, 4.0), Vector2(tile, tile))
	var center := frame.get_center()
	if selected:
		for i in 4:
			var g := 2.0 + i * 2.5
			draw_colored_polygon(MeadowDraw.rounded_points(frame.grow(g), 18.0 + g), Color(0.55, 1.0, 0.75, 0.12 - i * 0.025))
		draw_colored_polygon(MeadowDraw.rounded_points(frame, 18.0), Color("#9af0c4"))
		MeadowDraw.rounded_gradient(self, frame.grow(-3.0), 15.0, Color("#1f5a40"), Color("#123828"))
	elif is_hovered():
		draw_colored_polygon(MeadowDraw.rounded_points(frame, 18.0), Color(1, 1, 1, 0.06))
	MeadowDraw.fur_paw_token(self, center + Vector2(0, 0.0), tile * 0.44, fur, pads, tuft)
	if selected:
		var badge := frame.position + Vector2(tile - 6.0, 6.0)
		draw_circle(badge + Vector2(0, 1.5), 13.0, Color(0, 0, 0, 0.35))
		draw_circle(badge, 13.0, Color("#0f3a27"))
		draw_circle(badge, 11.0, Color("#7fe3ad"))
		MeadowDraw.check(self, badge, 14.0, Color("#0d3a25"))
	if has_focus():
		MeadowDraw.rounded_outline(self, frame.grow(4.0), 22.0, Color("#e6fff1"), 2.0)
	var font := MeadowStyle.text_font()
	var font_size := 18
	var width := font.get_string_size(animal_name, HORIZONTAL_ALIGNMENT_LEFT, -1, font_size).x
	var baseline := frame.end.y + 24.0
	draw_string(font, Vector2((size.x - width) * 0.5, baseline + 1.5), animal_name, HORIZONTAL_ALIGNMENT_LEFT, -1, font_size, Color(0, 0, 0, 0.45))
	draw_string(font, Vector2((size.x - width) * 0.5, baseline), animal_name, HORIZONTAL_ALIGNMENT_LEFT, -1, font_size, MeadowStyle.INK)

func _notification(what: int) -> void:
	if what in [NOTIFICATION_MOUSE_ENTER, NOTIFICATION_MOUSE_EXIT, NOTIFICATION_FOCUS_ENTER, NOTIFICATION_FOCUS_EXIT]:
		queue_redraw()
