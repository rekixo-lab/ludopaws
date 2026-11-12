@tool
class_name WoodPlaque
extends Control
## Carved wooden screen-title sign, drawn entirely in code: plank with grain and a
## carved inner groove, scroll curls at both ends, leaf clusters, and either a raised
## crest holding the icon (`crest`) or the icon inline before the title.

const OUTLINE := Color("#2a1508")
const WOOD_TOP := Color("#94592f")
const WOOD_BOTTOM := Color("#5c3218")
const GROOVE := Color("#4a2711")
const SHINE := Color("#e2a86a")
const TEXT := Color("#fbeccd")

@export var title := "Title":
	set(value):
		title = value
		queue_redraw()
@export var glyph := "":
	set(value):
		glyph = value
		queue_redraw()
@export var crest := false:
	set(value):
		crest = value
		custom_minimum_size.y = 134.0 if crest else 76.0
		queue_redraw()
@export var paws := false:
	set(value):
		paws = value
		queue_redraw()
@export var font_size := 34:
	set(value):
		font_size = value
		queue_redraw()
@export var board_inset := 30.0:
	set(value):
		board_inset = value
		queue_redraw()

func _init() -> void:
	mouse_filter = MOUSE_FILTER_IGNORE
	custom_minimum_size = Vector2(280, 76)

func _notification(what: int) -> void:
	if what == NOTIFICATION_RESIZED:
		queue_redraw()

func board_rect() -> Rect2:
	var height := 70.0 if crest else 58.0
	var top := size.y - height - 6.0
	return Rect2(Vector2(board_inset, top), Vector2(size.x - board_inset * 2.0, height))

const FRAME_TOP := Color("#d0955a")
const FRAME_BOTTOM := Color("#8a5229")

func _outline_shape(board: Rect2) -> PackedVector2Array:
	var shape := MeadowDraw.rounded_points(board, 16.0)
	if crest:
		var merged := Geometry2D.merge_polygons(shape, _crest_points(board))
		for polygon in merged:
			if not Geometry2D.is_polygon_clockwise(polygon):
				return polygon
		if merged.size() > 0:
			return merged[0]
	return shape

func _offset(shape: PackedVector2Array, delta: float) -> PackedVector2Array:
	var result := Geometry2D.offset_polygon(shape, delta, Geometry2D.JOIN_ROUND)
	return result[0] if result.size() > 0 else shape

func _draw() -> void:
	var board := board_rect()
	var shape := _outline_shape(board)
	var bounds := Rect2(Vector2(0, board.position.y - (46.0 if crest else 0.0)), Vector2(size.x, board.size.y + (46.0 if crest else 0.0)))
	MeadowDraw.soft_shadow(self, Rect2(board.position + Vector2(0, 6), board.size), 16.0, 0.55, 7.0)
	_scroll(board, -1.0)
	_scroll(board, 1.0)
	# Dark outline, carved light-wood frame, darker inset board.
	draw_colored_polygon(_offset(shape, 2.5), OUTLINE)
	MeadowDraw.gradient_fill(self, shape, bounds, FRAME_TOP, FRAME_BOTTOM)
	var inner := _offset(shape, -8.5)
	draw_colored_polygon(_offset(shape, -7.0), GROOVE)
	MeadowDraw.gradient_fill(self, inner, bounds, WOOD_TOP, WOOD_BOTTOM)
	_grain(board.grow(-9.0))
	var rim := inner.duplicate()
	rim.append(rim[0])
	draw_polyline(rim, Color(SHINE, 0.18), 1.0, true)
	var outer := shape.duplicate()
	outer.append(outer[0])
	var upper := PackedVector2Array()
	for point in outer:
		if point.y < board.position.y + 4.0:
			upper.append(point)
	if upper.size() > 1:
		draw_polyline(upper, Color("#f0c08a", 0.6), 1.4, true)
	if crest:
		_crest_face(board)
	_title(board)

func _scroll(board: Rect2, side: float) -> void:
	# A rolled end: a dark curl peeking out beyond the plank, a little lower.
	var r := board.size.y * 0.22
	var center := Vector2(board.position.x if side < 0 else board.end.x, board.get_center().y + board.size.y * 0.18)
	center.x += side * r * 0.7
	draw_circle(center + Vector2(0, 3), r + 3.0, Color(0, 0, 0, 0.35))
	draw_circle(center, r + 2.5, OUTLINE)
	MeadowDraw.gradient_fill(self, MeadowDraw.ellipse_points(center, Vector2(r, r)), Rect2(center - Vector2(r, r), Vector2(r, r) * 2.0), FRAME_TOP, FRAME_BOTTOM)
	var spiral := PackedVector2Array()
	for i in 40:
		var t := i / 39.0
		var a := (PI * 0.5 + t * TAU * 1.35) * side
		spiral.append(center + Vector2(cos(a) * side, sin(a)) * r * (0.82 - t * 0.62))
	draw_polyline(spiral, GROOVE, 2.0, true)
	draw_arc(center, r - 1.0, PI * 1.1, PI * 1.85, 14, Color(SHINE, 0.45), 1.4, true)

func _crest_points(board: Rect2, grow := 0.0) -> PackedVector2Array:
	var cx := board.get_center().x
	var half := 74.0 + grow
	var rise := 48.0 + grow
	var base_y := board.position.y + 14.0
	var points := PackedVector2Array()
	points.append(Vector2(cx - half - 16.0, base_y))
	for i in 25:
		var t := PI + PI * i / 24.0
		points.append(Vector2(cx + cos(t) * half, base_y + sin(t) * rise))
	points.append(Vector2(cx + half + 16.0, base_y))
	return points


func _crest_face(board: Rect2) -> void:
	var cx := board.get_center().x
	var base_y := board.position.y + 14.0
	if not glyph.is_empty():
		_icon(Vector2(cx, base_y - 26.0), 52.0)

func _grain(area: Rect2) -> void:
	var seed := float(title.length()) * 1.7
	var rows := int(area.size.y / 7.0)
	for row in rows:
		var y0 := area.position.y + 4.0 + row * 7.0
		var line := PackedVector2Array()
		var x := area.position.x + 6.0
		while x <= area.end.x - 6.0:
			var y := y0 + sin(x * 0.035 + row * 1.3 + seed) * 1.8 + sin(x * 0.011 + seed + row) * 2.2
			line.append(Vector2(x, clampf(y, area.position.y + 2.0, area.end.y - 2.0)))
			x += 6.0
		draw_polyline(line, Color(0.22, 0.1, 0.03, 0.16 + 0.08 * sin(row * 2.1 + seed)), 1.0, true)
	# A couple of knots.
	for k in 2:
		var c := Vector2(area.position.x + area.size.x * (0.22 + 0.58 * k), area.position.y + area.size.y * (0.35 + 0.3 * k))
		MeadowDraw.ellipse(self, c, Vector2(7.0, 2.6), Color(0.2, 0.09, 0.03, 0.22))

func _title(board: Rect2) -> void:
	var font: Font = UIFonts.display()
	var size_px := font_size
	var width := font.get_string_size(title, HORIZONTAL_ALIGNMENT_LEFT, -1, size_px).x
	var limit := board.size.x - 120.0
	while width > limit and size_px > 16:
		size_px -= 1
		width = font.get_string_size(title, HORIZONTAL_ALIGNMENT_LEFT, -1, size_px).x
	var inline_icon := not crest and not glyph.is_empty()
	var icon_size := size_px * 1.05
	var total := width + (icon_size + 10.0 if inline_icon else 0.0)
	var x := board.get_center().x - total * 0.5
	var baseline := board.get_center().y + font.get_ascent(size_px) * 0.36 + (2.0 if crest else 0.0)
	if inline_icon:
		_icon(Vector2(x + icon_size * 0.5, board.get_center().y), icon_size)
		x += icon_size + 10.0
	draw_string(font, Vector2(x, baseline + 3.0), title, HORIZONTAL_ALIGNMENT_LEFT, -1, size_px, Color(0.12, 0.05, 0.0, 0.7))
	draw_string_outline(font, Vector2(x, baseline), title, HORIZONTAL_ALIGNMENT_LEFT, -1, size_px, 2, Color("#4a2410"))
	draw_string(font, Vector2(x, baseline), title, HORIZONTAL_ALIGNMENT_LEFT, -1, size_px, TEXT)
	if not paws:
		var sprig_len := 40.0 if crest else 30.0
		var y := board.get_center().y + sprig_len * 0.45
		MeadowDraw.sprig(self, Vector2(board.get_center().x - total * 0.5 - 12.0, y), sprig_len, -1.0)
		MeadowDraw.sprig(self, Vector2(board.get_center().x + total * 0.5 + 12.0, y), sprig_len, 1.0)
	if paws:
		var gap := total * 0.5 + 24.0
		for side in [-1.0, 1.0]:
			MeadowDraw.paw(self, Vector2(board.get_center().x + gap * side, board.get_center().y + 1.0), 22.0, Color("#3b1d0b"))
			MeadowDraw.paw(self, Vector2(board.get_center().x + gap * side, board.get_center().y - 0.5), 20.0, Color("#f4c47e"))

func _icon(center: Vector2, icon_size: float) -> void:
	if MeadowDraw.has_glyph(glyph):
		MeadowDraw.glyph(self, glyph, center + Vector2(0, 2.5), icon_size, Color(0, 0, 0, 0.4))
		MeadowDraw.glyph(self, glyph, center, icon_size, TEXT)
		return
	var svg := load("res://assets/ui/icons/%s.svg" % glyph) as Texture2D
	if svg != null:
		var box := Vector2(icon_size, icon_size * svg.get_size().y / maxf(svg.get_size().x, 1.0))
		draw_texture_rect(svg, Rect2(center - box * 0.5, box), false)

func _arc_ellipse(center: Vector2, radii: Vector2, color: Color, width: float) -> void:
	var points := PackedVector2Array()
	for i in 25:
		var t := PI * 1.06 + PI * 0.88 * i / 24.0
		points.append(center + Vector2(cos(t) * radii.x, sin(t) * radii.y))
	draw_polyline(points, color, width, true)
