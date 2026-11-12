extends Control
## Profile wordmark and navigation above the reusable vine title.

signal back_pressed
signal settings_pressed

var title := "Profile"
var title_icon := ""
var wordmark_only := false

const WoodIconButton := preload("res://scripts/ui/kit/wood_icon_button.gd")
const VineTitle := preload("res://scripts/ui/kit/vine_title.gd")
const MEADOW_RIBBON := preload("res://assets/ui/brand/moonlit_meadow_ribbon.png")
const WORDMARK_LIFT := {"W": 1.5, "S": 2.5}
const BACK_SCALE := 1.10
const CREAM := Color("#fff0cc")
const SHADOW := Color("#26180f")

func _ready() -> void:
	resized.connect(queue_redraw)
	if wordmark_only:
		mouse_filter = MOUSE_FILTER_IGNORE
		return
	custom_minimum_size.y = 151
	var plaque := VineTitle.new()
	plaque.title = title
	plaque.icon = title_icon
	add_child(plaque)
	plaque.set_anchors_and_offsets_preset(PRESET_TOP_WIDE)
	plaque.offset_top = 83
	plaque.offset_bottom = 153
	var back := Button.new()
	var clear := StyleBoxEmpty.new()
	for state in ["normal", "hover", "pressed", "hover_pressed"]:
		back.add_theme_stylebox_override(state, clear)
	var focus := StyleBoxFlat.new()
	focus.bg_color = Color.TRANSPARENT
	focus.border_color = CREAM
	focus.set_border_width_all(2)
	focus.set_corner_radius_all(12)
	back.add_theme_stylebox_override("focus", focus)
	back.tooltip_text = "Back"
	back.position = Vector2(0, 23)
	back.size = Vector2(54, 54)
	back.pressed.connect(func(): back_pressed.emit())
	add_child(back)
	if not title_icon.is_empty():
		var settings := WoodIconButton.new()
		settings.configure_navigation(WoodIconButton.Glyph.SETTINGS)
		settings.pressed.connect(func(): settings_pressed.emit())
		add_child(settings)
		settings.set_anchors_and_offsets_preset(PRESET_TOP_RIGHT)
		settings.position += Vector2(-WoodIconButton.NAV_SIZE.x, 23)

func _draw() -> void:
	var center := size.x * 0.5
	_wordmark(center, 15.0 if wordmark_only else 0.0)
	if not wordmark_only:
		_back_button()

func _wordmark(center: float, offset_y: float) -> void:
	var ribbon_width := 164.0
	var ribbon_height := ribbon_width / MEADOW_RIBBON.get_size().aspect()
	draw_texture_rect(MEADOW_RIBBON, Rect2(center - ribbon_width * 0.5, 36 + offset_y, ribbon_width, ribbon_height), false)
	var font: Font = UIFonts.display()
	var wordmark := "LUDO PAWS"
	var letter_size := 25
	var width := font.get_string_size(wordmark, HORIZONTAL_ALIGNMENT_LEFT, -1, letter_size).x
	var layers := [
		[10, 3.4, Color("#26180f")],
		[8, 0.0, Color("#795133")],
		[6, 1.1, Color("#503019")],
		[2, 0.6, Color("#ae8250")],
		[0, 0.0, CREAM],
	]
	# Paint each bevel across the whole word before drawing the next one.
	# This joins adjacent letters into the same carved outline.
	for layer in layers:
		var x := center - width * 0.5
		for character in wordmark:
			var advance := font.get_string_size(character, HORIZONTAL_ALIGNMENT_LEFT, -1, letter_size).x
			var arc := (x + advance * 0.5 - center) / (width * 0.5)
			var lift: float = WORDMARK_LIFT.get(character, 0.0)
			draw_set_transform(Vector2(x, 32 + offset_y + 6 * arc * arc - lift), 0.12 * arc)
			var offset := Vector2(0, layer[1])
			if layer[0] > 0:
				draw_string_outline(font, offset, character, HORIZONTAL_ALIGNMENT_LEFT, -1, letter_size, layer[0], layer[2])
			else:
				draw_string(font, offset, character, HORIZONTAL_ALIGNMENT_LEFT, -1, letter_size, layer[2])
				if character == "O":
					MeadowDraw.paw(self, Vector2(advance * 0.51, -letter_size * 0.37), 9, CREAM)
			x += advance
	draw_set_transform(Vector2.ZERO)

func _back_button() -> void:
	var center := Vector2(27, 50)
	var shield := Curve2D.new()
	shield.add_point(Vector2(0, -21), Vector2(-4, 1), Vector2(4, 1))
	shield.add_point(Vector2(18, -11), Vector2(-3, -5), Vector2(3, 5))
	shield.add_point(Vector2(19, 9), Vector2(1, -7), Vector2(-1, 6))
	shield.add_point(Vector2(0, 21), Vector2(7, -2), Vector2(-7, -2))
	shield.add_point(Vector2(-19, 9), Vector2(1, 6), Vector2(-1, -7))
	shield.add_point(Vector2(-18, -11), Vector2(-3, 5), Vector2(3, -5))
	shield.add_point(Vector2(0, -21), Vector2(-4, 1))
	var points := shield.tessellate(5, 3)
	points.resize(points.size() - 1)
	MeadowDraw.wood_icon_face(self, center, points, Rect2(-22, -23, 44, 46), BACK_SCALE)
	var arrow := PackedVector2Array([
		Vector2(-10, 0), Vector2(-1, -8), Vector2(1, -8), Vector2(2, -6),
		Vector2(-1, -2.8), Vector2(9, -2.8), Vector2(10, -1.8), Vector2(10, 2.4),
		Vector2(9, 3.2), Vector2(-1, 3.2), Vector2(2, 6.4), Vector2(1, 8), Vector2(-1, 8),
	])
	draw_set_transform(center + Vector2(0, 1.3), 0, Vector2.ONE * (1.13 * BACK_SCALE))
	draw_colored_polygon(arrow, Color("#392414"))
	draw_set_transform(center, 0, Vector2.ONE * BACK_SCALE)
	MeadowDraw.gradient_fill(self, arrow, Rect2(-10, -8, 20, 16), Color("#fff1ce"), Color("#efcc89"))
	draw_set_transform(Vector2.ZERO)

