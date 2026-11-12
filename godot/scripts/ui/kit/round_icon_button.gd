@tool
class_name RoundIconButton
extends Button
## Round deep-green button with a pale rim and a code-drawn glyph (back arrow or gear).

enum Glyph { BACK, GEAR }

@export var glyph := Glyph.BACK:
	set(value):
		glyph = value
		queue_redraw()

func _init() -> void:
	custom_minimum_size = Vector2(52, 52)
	size_flags_horizontal = SIZE_SHRINK_CENTER
	size_flags_vertical = SIZE_SHRINK_CENTER
	focus_mode = FOCUS_ALL
	var clear := StyleBoxEmpty.new()
	for state in ["normal", "hover", "pressed", "focus", "hover_pressed"]:
		add_theme_stylebox_override(state, clear)

func _draw() -> void:
	var center := size * 0.5
	var radius := minf(size.x, size.y) * 0.5 - 3.0
	var down := is_pressed()
	var lift := Vector2(0, 1.5) if down else Vector2.ZERO
	draw_circle(center + Vector2(0, 3), radius + 1.0, Color(0, 0, 0, 0.45))
	draw_circle(center + lift, radius + 1.5, Color("#0a1c14"))
	draw_circle(center + lift, radius, Color("#c9d9b8") if not is_hovered() else Color("#e6f3d6"))
	MeadowDraw.gradient_fill(self, MeadowDraw.ellipse_points(center + lift, Vector2(radius - 2.2, radius - 2.2)), Rect2(center - Vector2(radius, radius), Vector2(radius, radius) * 2.0), Color("#2c5f45"), Color("#123425"))
	draw_arc(center + lift, radius - 4.0, PI * 1.15, PI * 1.85, 20, Color(1, 1, 1, 0.16), 2.0, true)
	if has_focus():
		draw_arc(center, radius + 3.5, 0, TAU, 40, Color("#e6fff1"), 2.0, true)
	var name := "back" if glyph == Glyph.BACK else "gear"
	MeadowDraw.glyph(self, name, center + lift + Vector2(0, 1.5), radius * 1.15, Color(0, 0, 0, 0.35))
	if glyph == Glyph.GEAR:
		MeadowDraw.gear(self, center + lift, radius * 1.15, MeadowStyle.INK, Color("#1b4532"))
	else:
		MeadowDraw.arrow_left(self, center + lift, radius * 1.15, MeadowStyle.INK)

func _notification(what: int) -> void:
	if what in [NOTIFICATION_MOUSE_ENTER, NOTIFICATION_MOUSE_EXIT, NOTIFICATION_FOCUS_ENTER, NOTIFICATION_FOCUS_EXIT]:
		queue_redraw()

func _gui_input(event: InputEvent) -> void:
	if event is InputEventMouseButton or event is InputEventScreenTouch:
		queue_redraw()
