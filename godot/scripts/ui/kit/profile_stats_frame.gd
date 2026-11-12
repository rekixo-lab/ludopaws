extends PanelContainer
## A gold frame that wraps around a centered Match Stats heading.

const CAPTION := "Match Stats"
const FRAME_TOP := 17.0
const CORNER := 17.0
const FONT_SIZE := 22
const CELL_CORNER := 10.0
const CELL_LINE := Color("#a7844c")

var heading_font: Font = UIFonts.display()

func _init() -> void:
	var margins := StyleBoxEmpty.new()
	margins.content_margin_left = 6
	margins.content_margin_right = 6
	margins.content_margin_top = 37
	margins.content_margin_bottom = 7
	add_theme_stylebox_override("panel", margins)

func _ready() -> void:
	resized.connect(queue_redraw)
	var grid := get_child(0) as GridContainer
	if grid != null:
		grid.sort_children.connect(queue_redraw)
	if OS.get_name() == "Android":
		var serif := FontFile.new()
		if serif.load_dynamic_font("/system/fonts/NotoSerif-Bold.ttf") == OK:
			heading_font = serif

func _draw() -> void:
	var frame := Rect2(1.5, FRAME_TOP, size.x - 3, size.y - FRAME_TOP - 1.5)
	if frame.size.y <= CORNER * 2:
		return
	var text_width := heading_font.get_string_size(CAPTION, HORIZONTAL_ALIGNMENT_LEFT, -1, FONT_SIZE).x
	var center := frame.get_center().x
	var left := center - (text_width + 37) * 0.5
	var right := left + text_width + 37
	var stroke := _outline(frame, left - 16, right + 16)
	MeadowDraw.rounded_gradient(self, frame, CORNER, Color("#14271c"), Color("#101f16"))
	draw_polyline(stroke, Color("#5b3d24"), 3.1, true)
	draw_polyline(stroke, Color("#bd8f4d"), 1.8, true)
	var highlight := stroke.duplicate()
	for i in highlight.size():
		highlight[i].y -= 0.35
	draw_polyline(highlight, Color("#f6cd87"), 0.85, true)
	_draw_grid()
	draw_set_transform(Vector2(left - 14, FRAME_TOP + 2), -0.48)
	MeadowDraw.sprig(self, Vector2.ZERO, 30, -1)
	draw_set_transform(Vector2(right + 14, FRAME_TOP + 2), 0.48)
	MeadowDraw.sprig(self, Vector2.ZERO, 30)
	draw_set_transform(Vector2.ZERO)
	var mark := Vector2(left + 11, FRAME_TOP - 1)
	MeadowDraw.bars(self, mark + Vector2(0, 1.5), 25, Color("#55351a"))
	MeadowDraw.bars(self, mark, 25, Color("#f4c472"))
	var baseline := Vector2(left + 37, FRAME_TOP + 5)
	draw_string_outline(heading_font, baseline + Vector2(0, 1.4), CAPTION, HORIZONTAL_ALIGNMENT_LEFT, -1, FONT_SIZE, 4, Color("#281a10"))
	draw_string(heading_font, baseline, CAPTION, HORIZONTAL_ALIGNMENT_LEFT, -1, FONT_SIZE, Color("#fff0cc"))

func _outline(frame: Rect2, left_end: float, right_end: float) -> PackedVector2Array:
	var points := PackedVector2Array([Vector2(left_end, frame.position.y)])
	_corner(points, frame.position + Vector2(CORNER, CORNER), -PI / 2.0, -PI)
	_corner(points, Vector2(frame.position.x + CORNER, frame.end.y - CORNER), PI, PI / 2.0)
	_corner(points, frame.end - Vector2(CORNER, CORNER), PI / 2.0, 0)
	_corner(points, Vector2(frame.end.x - CORNER, frame.position.y + CORNER), 0, -PI / 2.0)
	points.append(Vector2(right_end, frame.position.y))
	return points

func _corner(points: PackedVector2Array, center: Vector2, start: float, finish: float, radius := CORNER) -> void:
	for i in 9:
		points.append(center + Vector2.from_angle(lerpf(start, finish, i / 8.0)) * radius)


func _draw_grid() -> void:
	var grid := get_child(0) as GridContainer
	if grid == null or grid.get_child_count() != 4 or grid.size.y <= 0:
		return
	var first := grid.get_child(0) as Control
	var split := grid.position + first.position + first.size
	var rect := Rect2(grid.position, grid.size)
	MeadowDraw.rounded_gradient(self, rect, CELL_CORNER, Color("#17271b"), Color("#142419"))
	# The four outer curves meet one horizontal and one vertical shared stroke.
	# Skip the corner of each cell where the two shared strokes intersect.
	for index in 4:
		var cell := grid.get_child(index) as Control
		var width := cell.size.x
		var height := cell.size.y
		var radius := CELL_CORNER
		var path := PackedVector2Array()
		_corner(path, Vector2(width - radius, radius), 0, -PI / 2.0, radius)
		_corner(path, Vector2(radius, radius), -PI / 2.0, -PI, radius)
		_corner(path, Vector2(radius, height - radius), PI, PI / 2.0, radius)
		for i in path.size():
			var point := path[i]
			if index % 2 == 1:
				point.x = width - point.x
			if index >= 2:
				point.y = height - point.y
			path[i] = grid.position + cell.position + point
		draw_polyline(path, CELL_LINE, 1.05, true)
	draw_line(Vector2(split.x, rect.position.y + CELL_CORNER), Vector2(split.x, rect.end.y - CELL_CORNER), CELL_LINE, 1.05, true)
	draw_line(Vector2(rect.position.x + CELL_CORNER, split.y), Vector2(rect.end.x - CELL_CORNER, split.y), CELL_LINE, 1.05, true)
