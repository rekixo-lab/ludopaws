extends Button
## Compact reusable wood button. Supply a separate texture for its icon.

enum Glyph { NONE, BACK, SETTINGS }
const NAV_SIZE := Vector2(52, 52)

var artwork: Texture2D
var glyph := Glyph.NONE

func configure_navigation(kind: Glyph) -> void:
	glyph = kind
	custom_minimum_size = NAV_SIZE
	size = NAV_SIZE
	tooltip_text = "Back" if kind == Glyph.BACK else "Settings"
	queue_redraw()

func _init() -> void:
	custom_minimum_size = Vector2(37, 36)
	size_flags_horizontal = SIZE_SHRINK_CENTER
	size_flags_vertical = SIZE_SHRINK_CENTER
	var clear := StyleBoxEmpty.new()
	for state in ["normal", "hover", "pressed", "hover_pressed", "focus"]:
		add_theme_stylebox_override(state, clear)

func _ready() -> void:
	resized.connect(queue_redraw)
	button_down.connect(queue_redraw)
	button_up.connect(queue_redraw)
	focus_entered.connect(queue_redraw)
	focus_exited.connect(queue_redraw)

func _draw() -> void:
	var center := size * 0.5 + Vector2(0, 1.5 if is_pressed() else 0)
	var navigation := glyph != Glyph.NONE
	var half := 22.0 if navigation else 16.0
	var bounds := Rect2(Vector2.ONE * -half, Vector2.ONE * half * 2.0)
	var corner := 12.0 if navigation else 9.0
	var shape := MeadowDraw.rounded_points(bounds, corner, 10)
	MeadowDraw.wood_icon_face(self, center, shape, bounds, 1.0, navigation)
	if artwork != null:
		draw_texture_rect(artwork, Rect2(center - Vector2(14, 14), Vector2(28, 28)), false)
	elif glyph == Glyph.BACK:
		MeadowDraw.arrow_left(self, center + Vector2(0, 1.5), 30, Color("#442819"))
		MeadowDraw.arrow_left(self, center, 30, Color("#fff0c6"))
	elif glyph == Glyph.SETTINGS:
		var hole := Color("#4b2e1b")
		MeadowDraw.gear(self, center + Vector2(0, 1.5), 30, Color("#402416"), hole)
		MeadowDraw.gear(self, center, 29, Color("#ffdf99"), hole)
		draw_arc(center, 4.8, PI * 1.10, PI * 1.90, 10, Color("#fff4ca"), 0.9, true)
	if has_focus():
		MeadowDraw.rounded_outline(self, Rect2(center - Vector2.ONE * (half + 1), Vector2.ONE * (half + 1) * 2), corner + 1, Color("#fff1c5"), 1.0)
