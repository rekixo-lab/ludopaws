@tool
extends Button

const CARD_SURFACE := preload("res://assets/ui/backgrounds/mode_card_surface.svg")
const NAV_ACTIVE_SURFACE := preload("res://assets/ui/backgrounds/nav_active_surface.svg")
const LABEL_FONT := preload("res://assets/ui/brand/ui_sans_bold.tres")

@export var title: String = "":
	set(value):
		title = value
		if is_node_ready():
			$Contents/Title.text = title
			tooltip_text = title

@export var glyph: Texture2D:
	set(value):
		glyph = value
		if is_node_ready():
			$Contents/Glyph.texture = glyph

@export var navigation_item := false
@export var current := false:
	set(value):
		if current == value:
			return
		current = value
		if is_node_ready() and navigation_item:
			_apply_navigation_style()
@export_range(0, 100) var glyph_size := 0
@export_range(0, 100) var glyph_height := 0

func _ready() -> void:
	$Contents/Glyph.texture = glyph
	$Contents/Title.text = title
	tooltip_text = title
	mouse_default_cursor_shape = Control.CURSOR_POINTING_HAND
	var default_glyph_size := 30 if navigation_item else 46
	var width := glyph_size if glyph_size > 0 else default_glyph_size
	var height := glyph_height if glyph_height > 0 else width
	$Contents/Glyph.custom_minimum_size = Vector2(width, height)
	$Contents/Title.add_theme_font_override("font", LABEL_FONT)
	$Contents/Title.add_theme_font_size_override("font_size", 16 if navigation_item else 22)
	if navigation_item:
		_apply_navigation_style()
	else:
		add_theme_stylebox_override("normal", _textured_style(CARD_SURFACE, Color.WHITE, 22))
		add_theme_stylebox_override("hover", _textured_style(CARD_SURFACE, Color(1.13, 1.13, 1.13), 22))
		add_theme_stylebox_override("pressed", _textured_style(CARD_SURFACE, Color(0.82, 0.82, 0.82), 22))
	add_theme_stylebox_override("focus", _style(Color.TRANSPARENT, Color("#fff0c5")))

func _apply_navigation_style() -> void:
	set_pressed_no_signal(current)
	if current:
		$Contents/Glyph.self_modulate = Color(1.17, 1.29, 1.52)
		add_theme_stylebox_override("normal", _textured_style(NAV_ACTIVE_SURFACE, Color.WHITE, 15))
		add_theme_stylebox_override("hover", _textured_style(NAV_ACTIVE_SURFACE, Color(1.06, 1.06, 1.06), 15))
		add_theme_stylebox_override("pressed", _textured_style(NAV_ACTIVE_SURFACE, Color.WHITE, 15))
		add_theme_stylebox_override("hover_pressed", _textured_style(NAV_ACTIVE_SURFACE, Color(1.06, 1.06, 1.06), 15))
	else:
		$Contents/Glyph.self_modulate = Color.WHITE
		add_theme_stylebox_override("normal", StyleBoxEmpty.new())
		add_theme_stylebox_override("hover", _style(Color(0.69, 0.42, 0.22, 0.16), Color.TRANSPARENT))
		add_theme_stylebox_override("pressed", _style(Color(0.12, 0.07, 0.04, 0.22), Color.TRANSPARENT))
		add_theme_stylebox_override("hover_pressed", _style(Color(0.12, 0.07, 0.04, 0.22), Color.TRANSPARENT))

func _textured_style(texture: Texture2D, tint: Color, margin: int) -> StyleBoxTexture:
	var style := StyleBoxTexture.new()
	style.texture = texture
	style.modulate_color = tint
	for side in [SIDE_LEFT, SIDE_TOP, SIDE_RIGHT, SIDE_BOTTOM]:
		style.set_texture_margin(side, margin)
	return style

func _style(fill: Color, border: Color) -> StyleBoxFlat:
	var style := StyleBoxFlat.new()
	style.bg_color = fill
	style.border_color = border
	style.set_border_width_all(2)
	style.set_corner_radius_all(13 if navigation_item else 20)
	return style
