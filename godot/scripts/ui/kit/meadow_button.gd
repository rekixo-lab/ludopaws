@tool
class_name MeadowButton
extends Button
## Code-drawn button. Variants follow the reference: glossy mint pill, raised mint
## segment, dark moss segment, cream chip. Text and glyph are drawn here too.

enum Look { MINT, MOSS, SEGMENT_ON, SEGMENT_OFF, CREAM }

@export var look := Look.MINT:
	set(value):
		look = value
		queue_redraw()
@export var label := "":
	set(value):
		label = value
		tooltip_text = value
		queue_redraw()
@export var glyph := "":
	set(value):
		glyph = value
		queue_redraw()
@export var font_size := 20:
	set(value):
		font_size = value
		queue_redraw()
@export var radius := -1.0:
	set(value):
		radius = value
		queue_redraw()
@export var display_font := false
var stacked_icon := false
var moss_tint := Color.TRANSPARENT
var glyph_artwork: Texture2D

func _init() -> void:
	custom_minimum_size = Vector2(60, 46)
	size_flags_horizontal = SIZE_EXPAND_FILL
	focus_mode = FOCUS_ALL
	var clear := StyleBoxEmpty.new()
	for state in ["normal", "hover", "pressed", "focus", "hover_pressed", "disabled"]:
		add_theme_stylebox_override(state, clear)

func _corner() -> float:
	if radius >= 0.0:
		return radius
	return size.y * 0.5 if look in [Look.MINT, Look.MOSS, Look.CREAM] else 11.0

func _draw() -> void:
	var rect := Rect2(Vector2(2, 2), size - Vector2(4, 6))
	var down := is_pressed() or (button_pressed and toggle_mode)
	if down:
		rect.position.y += 2
	var hover := is_hovered() and not disabled
	var r := _corner()
	var ink := MeadowStyle.INK
	match look:
		Look.MINT, Look.SEGMENT_ON:
			var top := Color("#b8ffd9") if not hover else Color("#cbffe4")
			var bottom := Color("#56d692") if not hover else Color("#66e0a0")
			MeadowDraw.glossy(self, rect, r, top, bottom, Color("#1f6b47"), Color(0.55, 1.0, 0.78, 0.6) if look == Look.MINT else Color(0.5, 1.0, 0.75, 0.35))
			ink = Color("#0d3a25")
		Look.MOSS, Look.SEGMENT_OFF:
			var top := Color("#2b5c45") if not hover else Color("#336b51")
			var bottom := Color("#163a2b") if not hover else Color("#1c4535")
			var rim := Color("#5d8a6c")
			if moss_tint.a > 0.0:
				top = moss_tint.lightened(0.22 if hover else 0.12)
				bottom = moss_tint.darkened(0.18)
				rim = moss_tint.lightened(0.43)
			MeadowDraw.soft_shadow(self, Rect2(rect.position + Vector2(0, 2), rect.size), r, 0.35, 2.5)
			draw_colored_polygon(MeadowDraw.rounded_points(rect.grow(1.5), r + 1.5), rim)
			MeadowDraw.rounded_gradient(self, rect, r, top, bottom)
			MeadowDraw.rounded_outline(self, rect.grow(-1.5), maxf(r - 1.5, 0.0), Color(1, 1, 1, 0.08), 1.0)
		Look.CREAM:
			MeadowDraw.soft_shadow(self, Rect2(rect.position + Vector2(0, 2), rect.size), r, 0.35, 2.5)
			draw_colored_polygon(MeadowDraw.rounded_points(rect.grow(1.5), r + 1.5), Color("#a88a5c"))
			MeadowDraw.rounded_gradient(self, rect, r, Color("#fbf1d9"), Color("#e2cfa5"))
			ink = Color("#3a2a18")
	if has_focus():
		MeadowDraw.rounded_outline(self, rect.grow(3.0), r + 3.0, Color("#e6fff1"), 2.0)
	var font: Font = UIFonts.display() if display_font else MeadowStyle.text_font()
	var px := get_theme_font_size("font_size") if has_theme_font_size_override("font_size") else font_size
	var text_size := font.get_string_size(label, HORIZONTAL_ALIGNMENT_LEFT, -1, px)
	var room := rect.size.x - 18.0 - (px * 1.15 + 12.0 if not glyph.is_empty() and not stacked_icon else 0.0)
	while text_size.x > room and px > 10:
		px -= 1
		text_size = font.get_string_size(label, HORIZONTAL_ALIGNMENT_LEFT, -1, px)
	var glyph_size := px * 1.15
	var gap := 12.0 if not label.is_empty() else 0.0
	var total := text_size.x + (glyph_size + gap if not glyph.is_empty() and not stacked_icon else 0.0)
	var x := rect.get_center().x - total * 0.5
	var baseline := rect.get_center().y + font.get_ascent(px) * 0.5 - font.get_descent(px) * 0.35
	if not glyph.is_empty():
		var c := Vector2(x + glyph_size * 0.5, rect.get_center().y)
		if stacked_icon:
			glyph_size = minf(40.0, rect.size.y * 0.43)
			c = rect.get_center() - Vector2(0, glyph_size * 0.36)
			baseline = rect.get_center().y + glyph_size * 0.82
		if glyph_artwork != null:
			var box := Vector2(glyph_size * 1.2, glyph_size * 1.2 / glyph_artwork.get_size().aspect())
			draw_texture_rect(glyph_artwork, Rect2(c - box * 0.5, box), false, ink)
		elif MeadowDraw.has_glyph(glyph):
			MeadowDraw.glyph(self, glyph, c, glyph_size, ink)
		else:
			var svg := load("res://assets/ui/icons/%s.svg" % glyph) as Texture2D
			if svg != null:
				var box := Vector2(glyph_size, glyph_size * svg.get_size().y / maxf(svg.get_size().x, 1.0))
				draw_texture_rect(svg, Rect2(c - box * 0.5, box), false)
		x = rect.get_center().x - text_size.x * 0.5 if stacked_icon else x + glyph_size + gap
	if not label.is_empty():
		draw_string(font, Vector2(x, baseline), label, HORIZONTAL_ALIGNMENT_LEFT, -1, px, ink)
	if disabled:
		draw_colored_polygon(MeadowDraw.rounded_points(rect, r), Color(0.05, 0.1, 0.08, 0.45))

func _notification(what: int) -> void:
	if what in [NOTIFICATION_MOUSE_ENTER, NOTIFICATION_MOUSE_EXIT, NOTIFICATION_FOCUS_ENTER, NOTIFICATION_FOCUS_EXIT, NOTIFICATION_RESIZED]:
		queue_redraw()

func _gui_input(event: InputEvent) -> void:
	if event is InputEventMouseButton or event is InputEventScreenTouch:
		queue_redraw()
