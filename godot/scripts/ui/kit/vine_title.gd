extends Control
## Rustic title plaque shared by pages that use the vine frame.

const BACKGROUND := preload("res://assets/ui/illustrations/vine_title_background.png")

const PAW := preload("res://assets/ui/icons/profile_paw_crest.svg")
const ANDROID_SERIF := "/system/fonts/NotoSerif-Bold.ttf"

var title := "Profile"
var icon := ""
var _icon_texture: Texture2D
var title_font: Font = UIFonts.display()

func _ready() -> void:
	mouse_filter = MOUSE_FILTER_IGNORE
	resized.connect(queue_redraw)
	if not icon.is_empty():
		_icon_texture = load("res://assets/ui/icons/%s.svg" % icon) as Texture2D
	if OS.get_name() == "Android":
		var serif := FontFile.new()
		if serif.load_dynamic_font(ANDROID_SERIF) == OK:
			title_font = serif

func _draw() -> void:
	var center := size.x * 0.5
	var width := minf(size.x - 12.0, 358.0)
	var height := width / BACKGROUND.get_size().aspect()
	draw_texture_rect(BACKGROUND, Rect2(center - width * 0.5, 34 - height * 0.5, width, height), false)
	if icon.is_empty():
		for side in [-1, 1]:
			draw_set_transform(Vector2(center + side * width * 0.285, 34), side * 0.12)
			draw_texture_rect(PAW, Rect2(-16, -16, 32, 32), false)
		draw_set_transform(Vector2.ZERO)
	var font := title_font
	var font_size := 28
	var limit := width * 0.59 - 34.0
	while font_size > 14 and font.get_string_size(title, HORIZONTAL_ALIGNMENT_LEFT, -1, font_size).x > limit:
		font_size -= 1
	var text_width := font.get_string_size(title, HORIZONTAL_ALIGNMENT_LEFT, -1, font_size).x
	var text_origin := Vector2(center - text_width * 0.5 + (16 if _icon_texture != null else 0), 43)
	if _icon_texture != null:
		draw_texture_rect(_icon_texture, Rect2(text_origin.x - 35, 20, 28, 28), false)
	draw_string_outline(font, text_origin + Vector2(0, 2), title, HORIZONTAL_ALIGNMENT_LEFT, -1, font_size, 5, Color("#26180f"))
	draw_string_outline(font, text_origin, title, HORIZONTAL_ALIGNMENT_LEFT, -1, font_size, 3, Color("#382315"))
	draw_string(font, text_origin, title, HORIZONTAL_ALIGNMENT_LEFT, -1, font_size, Color("#fff1d3"))
