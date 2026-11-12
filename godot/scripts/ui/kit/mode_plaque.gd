extends Control

const TEAM := preload("res://assets/ui/icons/team.svg")
const GLOBE := preload("res://assets/ui/icons/globe.svg")
const WOOD := preload("res://assets/ui/illustrations/woodland_header.webp")

var title := ""
var glyph := ""
var crest := false
var artwork: Texture2D
var match_title := false

func _ready() -> void:
	mouse_filter = MOUSE_FILTER_IGNORE
	custom_minimum_size.y = 104 if crest else (76 if match_title else 72)
	resized.connect(queue_redraw)

func _draw() -> void:
	var center := size.x * 0.5
	var board_width := minf(size.x, 390.0 if crest else (352.0 if match_title else 300.0))
	var board_y := 31.0 if crest else (2.0 if match_title else 0.0)
	var board := Rect2(center - board_width / 2.0, board_y, board_width, 72)
	if crest:
		MeadowDraw.wood_icon_face(self, Vector2(center, 27), MeadowDraw.rounded_points(Rect2(-32, -25, 64, 56), 24), Rect2(-32, -25, 64, 56))
	draw_texture_rect(WOOD, board, false)
	var font := UIFonts.display()
	var px := 34 if crest else 30
	var width := font.get_string_size(title, HORIZONTAL_ALIGNMENT_LEFT, -1, px).x
	var inline_icon := not crest and not glyph.is_empty()
	var start := center - (width + (39.0 if inline_icon else 0.0)) / 2.0
	if not glyph.is_empty():
		var icon_center := Vector2(center, 25) if crest else Vector2(start + 15, board_y + 34)
		var texture := artwork
		if texture == null and glyph in ["globe", "team"]:
			texture = TEAM if glyph == "team" else GLOBE
		if texture != null:
			var icon_size := Vector2(34, 34 / texture.get_size().aspect())
			draw_texture_rect(texture, Rect2(icon_center - icon_size * 0.5, icon_size), false)
		else:
			MeadowDraw.glyph(self, glyph, icon_center + Vector2(0, 2), 32, Color("#422716"))
			MeadowDraw.glyph(self, glyph, icon_center, 32, Color("#ffe3a7"))
		if inline_icon:
			start += 39
	var baseline := Vector2(start, board_y + 44)
	draw_string_outline(font, baseline, title, HORIZONTAL_ALIGNMENT_LEFT, -1, px, 5, Color("#472910"))
	draw_string(font, baseline, title, HORIZONTAL_ALIGNMENT_LEFT, -1, px, Color("#fff0cb"))
