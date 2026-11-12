extends RefCounted
## Shared controls for the Moonlit Meadow screens.

const SERIF = preload("res://assets/ui/brand/display_serif.tres")
const INK := Color("#fff0cc")
const GOLD := Color("#d5a65d")

static func texture(path: String) -> Texture2D:
	return load(path) as Texture2D if ResourceLoader.exists(path) else null

static func icon(name: String) -> Texture2D:
	return texture("res://assets/ui/icons/%s.svg" % name)

static func art(name: String) -> Texture2D:
	return texture("res://assets/ui/illustrations/%s.webp" % name)

static func image(parent: Control, source: Texture2D, height: int, cover := false) -> TextureRect:
	var view := TextureRect.new()
	view.texture = source
	view.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	view.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_COVERED if cover else TextureRect.STRETCH_KEEP_ASPECT_CENTERED
	view.custom_minimum_size.y = height
	view.mouse_filter = Control.MOUSE_FILTER_IGNORE
	parent.add_child(view)
	return view

static func illustration(parent: Control, name: String, height: int) -> TextureRect:
	var view := image(parent, art(name), height, true)
	var blend := ShaderMaterial.new()
	blend.shader = preload("res://assets/ui/backgrounds/meadow_illustration.gdshader")
	view.material = blend
	return view

static func animal(index: int) -> Texture2D:
	return atlas("woodland_animals", index, icon("paw"))

static func atlas(name: String, index: int, fallback: Texture2D) -> Texture2D:
	var sheet := art(name)
	if sheet == null:
		return fallback
	var region := AtlasTexture.new()
	var cell := sheet.get_size() / Vector2(3, 2)
	region.atlas = sheet
	region.region = Rect2(Vector2(index % 3, floori(index / 3.0)) * cell, cell)
	region.filter_clip = true
	return region

static func label(parent: Control, value: String, font_size := 18, centered := false) -> Label:
	var item := Label.new()
	item.text = value
	item.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER if centered else HORIZONTAL_ALIGNMENT_LEFT
	item.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	item.add_theme_font_override("font", MeadowStyle.text_font())
	item.add_theme_font_size_override("font_size", font_size)
	item.add_theme_color_override("font_shadow_color", Color(0, 0, 0, 0.45))
	item.add_theme_constant_override("shadow_offset_y", 2)
	item.add_theme_color_override("font_color", INK)
	parent.add_child(item)
	return item

static func column(parent: Control, separation := 10) -> VBoxContainer:
	var box := VBoxContainer.new()
	box.add_theme_constant_override("separation", separation)
	parent.add_child(box)
	return box

static func row(parent: Control, separation := 8) -> HBoxContainer:
	var box := HBoxContainer.new()
	box.add_theme_constant_override("separation", separation)
	parent.add_child(box)
	return box

static func surface(fill: Color, border := GOLD, radius := 16) -> StyleBoxFlat:
	var style := StyleBoxFlat.new()
	style.bg_color = fill
	style.border_color = border
	style.set_border_width_all(2)
	style.set_corner_radius_all(radius)
	style.set_content_margin_all(10.0)
	style.shadow_color = Color(0, 0, 0, 0.48)
	style.shadow_size = 4
	style.shadow_offset = Vector2(0, 3)
	return style

static func panel(parent: Control, heading := "", fill := Color.TRANSPARENT, heading_icon := "") -> VBoxContainer:
	var frame := MeadowPanel.new()
	if fill.a > 0.0:
		frame.tint = fill
	parent.add_child(frame)
	var body := column(frame, 12)
	if not heading.is_empty():
		var title_row := row(body, 12)
		if not heading_icon.is_empty():
			if MeadowDraw.has_glyph(heading_icon):
				var glyph := MeadowIcon.new()
				glyph.glyph = heading_icon
				glyph.custom_minimum_size = Vector2(34, 34)
				title_row.add_child(glyph)
			else:
				var glyph_image := image(title_row, icon(heading_icon), 26)
				glyph_image.custom_minimum_size.x = 30
				glyph_image.size_flags_vertical = Control.SIZE_SHRINK_CENTER
		var title := label(title_row, heading, 22)
		title.autowrap_mode = TextServer.AUTOWRAP_OFF
		title.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	return body

static func button(parent: Control, title: String, action: Callable, primary := false, glyph := "", height := 47) -> Button:
	var item := MeadowButton.new()
	item.label = title
	item.glyph = glyph
	item.look = MeadowButton.Look.MINT if primary else MeadowButton.Look.MOSS
	item.custom_minimum_size.y = height + 4
	item.font_size = 19 if height >= 50 else 17
	parent.add_child(item)
	item.pressed.connect(action)
	return item

static func choices(parent: Control, options: Array, current: int, action: Callable) -> void:
	var line := row(parent, 6)
	for i in options.size():
		var item := MeadowButton.new()
		item.label = str(options[i])
		item.look = MeadowButton.Look.SEGMENT_ON if i == current else MeadowButton.Look.SEGMENT_OFF
		item.custom_minimum_size.y = 52
		item.font_size = 23 if str(options[i]).length() <= 2 else 20
		line.add_child(item)
		item.pressed.connect(action.bind(i))

static func field(parent: Control, value: String, hint := "", max_chars := 24) -> LineEdit:
	var edit := LineEdit.new()
	edit.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	edit.custom_minimum_size.y = 40
	edit.text = value
	edit.placeholder_text = hint
	edit.max_length = max_chars
	edit.add_theme_stylebox_override("normal", MeadowStyle.cream_field())
	var focus := MeadowStyle.cream_field()
	focus.border_color = Color("#3f9a6b")
	focus.set_border_width_all(2)
	focus.border_width_top = 3
	edit.add_theme_stylebox_override("focus", focus)
	edit.add_theme_font_override("font", MeadowStyle.text_font())
	edit.add_theme_font_size_override("font_size", 19)
	edit.add_theme_color_override("font_color", Color("#261c12"))
	edit.add_theme_color_override("font_placeholder_color", Color("#65533b"))
	parent.add_child(edit)
	return edit

static func progress(parent: Control, value: int, total: int) -> void:
	var meter := ProgressBar.new()
	meter.custom_minimum_size.y = 20
	meter.max_value = total
	meter.value = value
	meter.show_percentage = false
	meter.add_theme_stylebox_override("background", surface(Color("#0d3327"), GOLD, 10))
	meter.add_theme_stylebox_override("fill", surface(Color("#72dbab"), Color("#b4f6d0"), 10))
	parent.add_child(meter)


## Page state card (V2): kind "loading" | "empty" | "offline" | "signin" | "error" — an icon,
## a title, a short message and the next action. Used wherever real data is not available
## yet, so screens never fall back to sample content.
static func state_card(parent: Control, kind: String, title: String, message: String, action_label := "", action := Callable()) -> VBoxContainer:
	var card := panel(parent, "", Color("#12362a"))
	card.add_theme_constant_override("separation", 8)
	card.set_meta("state_kind", kind)
	var glyph := MeadowIcon.new()
	glyph.glyph = {"loading": "paw", "empty": "paw", "offline": "link", "signin": "link", "error": "link"}.get(kind, "paw")
	glyph.custom_minimum_size = Vector2(44, 44)
	glyph.size_flags_horizontal = Control.SIZE_SHRINK_CENTER
	card.add_child(glyph)
	if kind == "loading":
		var spin := glyph.create_tween().set_loops()
		glyph.pivot_offset = Vector2(22, 22)
		spin.tween_property(glyph, "rotation", TAU, 1.2).from(0.0)
	label(card, title, 19, true)
	label(card, message, 14, true)
	if not action_label.is_empty() and action.is_valid():
		var b := button(card, action_label, action, kind != "loading", "", 40)
		b.custom_minimum_size.x = 180
		b.size_flags_horizontal = Control.SIZE_SHRINK_CENTER
	return card
