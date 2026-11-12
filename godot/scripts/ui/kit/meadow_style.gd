class_name MeadowStyle
extends RefCounted
## Colours and StyleBoxes for the Moonlit Meadow kit. Everything is StyleBoxFlat,
## so buttons and panels scale cleanly and need no bitmap skins.

const INK := Color("#fff1d6")
const INK_DARK := Color("#0b3b26")
const GOLD := Color("#c99b55")
const GOLD_SOFT := Color("#8a6a3c")
const PANEL := Color("#0c2a21ee")
const MINT := Color("#7fe0ae")
const MINT_HI := Color("#c6ffe2")
const MINT_LOW := Color("#2f9467")
const MOSS := Color("#123a2c")
const MOSS_EDGE := Color("#3a7058")
const CREAM := Color("#f2e2bd")
const WOOD_RIM := Color("#3a2010")
const TEAM_COLORS := [Color("#e8443a"), Color("#3c84e6"), Color("#f2c230"), Color("#3fb35a")]
const TEAM_NAMES := ["Red", "Blue", "Yellow", "Green"]

static func box(fill: Color, border: Color, width := 2, radius := 16) -> StyleBoxFlat:
	var style := StyleBoxFlat.new()
	style.bg_color = fill
	style.border_color = border
	style.set_border_width_all(width)
	style.set_corner_radius_all(radius)
	style.set_content_margin_all(10.0)
	style.anti_aliasing_size = 0.8
	return style

static func panel() -> StyleBoxFlat:
	var style := box(PANEL, GOLD, 2, 18)
	style.set_content_margin_all(14.0)
	style.content_margin_top = 10.0
	style.shadow_color = Color(0, 0, 0, 0.45)
	style.shadow_size = 6
	style.shadow_offset = Vector2(0, 3)
	return style

## Raised mint pill used for primary actions and selected segments.
static func mint(state := "normal", radius := 999) -> StyleBoxFlat:
	var fill := MINT
	if state == "hover":
		fill = Color("#93ebbf")
	elif state == "pressed":
		fill = Color("#5fc993")
	var style := box(fill, MINT_LOW, 0, radius)
	style.border_width_bottom = 2 if state == "pressed" else 4
	style.border_width_top = 2
	style.border_color = MINT_LOW
	style.expand_margin_top = 0
	style.shadow_color = Color(0.45, 1.0, 0.75, 0.22 if state != "pressed" else 0.1)
	style.shadow_size = 7
	style.set_content_margin_all(8.0)
	return style

## Dark moss pill used for secondary actions and unselected segments.
static func moss(state := "normal", radius := 999) -> StyleBoxFlat:
	var fill := MOSS
	if state == "hover":
		fill = Color("#18483a")
	elif state == "pressed":
		fill = Color("#0b2a20")
	var style := box(fill, MOSS_EDGE, 2, radius)
	style.border_width_bottom = 3
	style.border_color = MOSS_EDGE if state != "hover" else Color("#5a9a7e")
	style.set_content_margin_all(8.0)
	return style

static func focus_ring(radius := 999) -> StyleBoxFlat:
	var style := box(Color.TRANSPARENT, Color("#d8ffe9"), 2, radius)
	style.expand_margin_left = 3
	style.expand_margin_right = 3
	style.expand_margin_top = 3
	style.expand_margin_bottom = 3
	return style

static func skin_button(item: BaseButton, primary: bool, radius := 999) -> void:
	for state in ["normal", "hover", "pressed", "disabled"]:
		var style := mint(state, radius) if primary else moss(state, radius)
		item.add_theme_stylebox_override(state, style)
	item.add_theme_stylebox_override("hover_pressed", mint("pressed", radius) if primary else moss("pressed", radius))
	item.add_theme_stylebox_override("focus", focus_ring(radius))
	var text := INK_DARK if primary else INK
	for key in ["font_color", "font_hover_color", "font_pressed_color", "font_focus_color", "font_hover_pressed_color"]:
		item.add_theme_color_override(key, text)
	item.add_theme_color_override("icon_normal_color", text)
	item.add_theme_color_override("icon_hover_color", text)
	item.add_theme_color_override("icon_pressed_color", text)
	item.add_theme_color_override("icon_focus_color", text)

static func cream_field() -> StyleBoxFlat:
	var style := box(Color("#efe0bd"), Color("#b59a6a"), 1, 12)
	style.border_width_top = 3
	style.border_color = Color("#a88c5c")
	style.shadow_color = Color(0, 0, 0, 0.35)
	style.shadow_size = 3
	style.shadow_offset = Vector2(0, 2)
	style.content_margin_left = 14.0
	style.content_margin_right = 14.0
	style.content_margin_top = 6.0
	style.content_margin_bottom = 6.0
	return style

static var _text_font: Font

## Softer serif for body text and panel headings (the display face stays for titles).
static func text_font() -> Font:
	if _text_font == null:
		var system := SystemFont.new()
		system.font_names = PackedStringArray(["DejaVu Serif", "Noto Serif", "Georgia", "serif"])
		system.font_weight = 600
		_text_font = system
	return _text_font
