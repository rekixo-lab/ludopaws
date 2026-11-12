@tool
class_name BrandSign
extends Control
## "LUDO PAWS / Moonlit Meadow" sign for inner screens, drawn in code: arched wooden
## board, paw crest with leaves on top, green ribbon underneath.

@export var title := "LUDO PAWS":
	set(value):
		title = value
		queue_redraw()
@export var subtitle := "Moonlit Meadow":
	set(value):
		subtitle = value
		queue_redraw()

func _init() -> void:
	mouse_filter = MOUSE_FILTER_IGNORE
	custom_minimum_size = Vector2(240, 138)

func _notification(what: int) -> void:
	if what == NOTIFICATION_RESIZED:
		queue_redraw()

func _arched(x0: float, x1: float, top: float, bottom: float, lift: float, sag: float, steps := 24) -> PackedVector2Array:
	var points := PackedVector2Array()
	var corner := 10.0
	for i in steps + 1:
		var t := float(i) / steps
		var x := lerpf(x0 + corner, x1 - corner, t)
		points.append(Vector2(x, top - sin(t * PI) * lift))
	points.append(Vector2(x1, top + corner))
	for i in steps + 1:
		var t := float(i) / steps
		var x := lerpf(x1 - corner, x0 + corner, t)
		points.append(Vector2(x, bottom + sin(t * PI) * sag))
	points.append(Vector2(x0, top + corner))
	return points

func _draw() -> void:
	var cx := size.x * 0.5
	var w := minf(size.x, 236.0)
	var x0 := cx - w * 0.5
	var x1 := cx + w * 0.5
	var top := 44.0
	var bottom := 106.0
	var span := Rect2(Vector2(x0, top - 10.0), Vector2(w, bottom - top + 16.0))
	# Leaves and crest behind the board.
	MeadowDraw.leaf_cluster(self, Vector2(cx - 30.0, top - 4.0), 28.0, -1.0)
	MeadowDraw.leaf_cluster(self, Vector2(cx + 30.0, top - 4.0), 28.0, 1.0)
	var crest := Vector2(cx, 26.0)
	draw_circle(crest + Vector2(0, 2.5), 25.0, Color(0, 0, 0, 0.4))
	draw_circle(crest, 25.0, WoodPlaque.OUTLINE)
	MeadowDraw.gradient_fill(self, MeadowDraw.ellipse_points(crest, Vector2(22.5, 22.5)), Rect2(crest - Vector2(23, 23), Vector2(46, 46)), Color("#b0743f"), Color("#5f3418"))
	draw_arc(crest, 19.0, 0, TAU, 36, WoodPlaque.GROOVE, 1.6, true)
	draw_arc(crest, 22.0, PI * 1.1, PI * 1.9, 18, Color(1, 0.85, 0.6, 0.4), 1.3, true)
	MeadowDraw.paw(self, crest + Vector2(0, 3.0), 27.0, Color("#3b1d0b"))
	MeadowDraw.paw(self, crest + Vector2(0, 1.0), 25.0, Color("#f6c77d"))
	# Board.
	MeadowDraw.soft_shadow(self, Rect2(Vector2(x0, top + 4.0), Vector2(w, bottom - top)), 12.0, 0.5, 6.0)
	draw_colored_polygon(_arched(x0 - 2.5, x1 + 2.5, top - 2.5, bottom + 2.5, 9.0, 4.0), WoodPlaque.OUTLINE)
	MeadowDraw.gradient_fill(self, _arched(x0, x1, top, bottom, 9.0, 4.0), span, WoodPlaque.WOOD_TOP, WoodPlaque.WOOD_BOTTOM)
	var groove := _arched(x0 + 6.0, x1 - 6.0, top + 6.0, bottom - 6.0, 8.0, 3.0)
	groove.append(groove[0])
	draw_polyline(groove, WoodPlaque.GROOVE, 1.8, true)
	# Title.
	var font: Font = UIFonts.display()
	var font_size := 37
	var text_w := font.get_string_size(title, HORIZONTAL_ALIGNMENT_LEFT, -1, font_size).x
	while text_w > w - 30.0 and font_size > 18:
		font_size -= 1
		text_w = font.get_string_size(title, HORIZONTAL_ALIGNMENT_LEFT, -1, font_size).x
	var baseline := top + 41.0
	var x := cx - text_w * 0.5
	draw_string_outline(font, Vector2(x, baseline + 3.0), title, HORIZONTAL_ALIGNMENT_LEFT, -1, font_size, 5, Color(0, 0, 0, 0.35))
	draw_string_outline(font, Vector2(x, baseline), title, HORIZONTAL_ALIGNMENT_LEFT, -1, font_size, 4, Color("#3b1d0b"))
	draw_string(font, Vector2(x, baseline + 1.5), title, HORIZONTAL_ALIGNMENT_LEFT, -1, font_size, Color("#d99a4e"))
	draw_string(font, Vector2(x, baseline), title, HORIZONTAL_ALIGNMENT_LEFT, -1, font_size, Color("#fff0cf"))
	# Ribbon.
	if subtitle.is_empty():
		return
	var text_font := MeadowStyle.text_font()
	var sub_size := 17
	var sub_w := text_font.get_string_size(subtitle, HORIZONTAL_ALIGNMENT_LEFT, -1, sub_size).x
	var ribbon := Rect2(Vector2(cx - sub_w * 0.5 - 16.0, bottom - 16.0), Vector2(sub_w + 32.0, 30.0))
	MeadowDraw.soft_shadow(self, Rect2(ribbon.position + Vector2(0, 3), ribbon.size), 9.0, 0.4, 3.0)
	draw_colored_polygon(MeadowDraw.rounded_points(ribbon.grow(2.0), 11.0), Color("#3b1d0b"))
	draw_colored_polygon(MeadowDraw.rounded_points(ribbon.grow(0.8), 9.8), Color("#c9a35c"))
	MeadowDraw.rounded_gradient(self, ribbon.grow(-0.8), 8.0, Color("#2f6243"), Color("#163a27"))
	draw_string(text_font, Vector2(cx - sub_w * 0.5, ribbon.get_center().y + 6.0), subtitle, HORIZONTAL_ALIGNMENT_LEFT, -1, sub_size, Color("#fbeccd"))
