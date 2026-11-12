extends MarginContainer

var blue := false

func _ready() -> void:
	size_flags_horizontal = SIZE_EXPAND_FILL
	for side in ["left", "right"]:
		add_theme_constant_override("margin_" + side, 12)
	add_theme_constant_override("margin_top", 20)
	add_theme_constant_override("margin_bottom", 24)
	resized.connect(queue_redraw)

func _draw() -> void:
	var w := size.x
	var h := size.y
	var top := Curve2D.new()
	top.add_point(Vector2(6, 17), Vector2.ZERO, Vector2(w * 0.13, -12))
	top.add_point(Vector2(w * 0.31, 3), Vector2(-w * 0.12, 0), Vector2(w * 0.17, 0))
	top.add_point(Vector2(w - 6, 15), Vector2(-w * 0.16, 3))
	var hem := Curve2D.new()
	hem.add_point(Vector2(w - 6, h - 17), Vector2.ZERO, Vector2(-w * 0.04, 7))
	hem.add_point(Vector2(w * 0.80, h - 4), Vector2(w * 0.08, 0), Vector2(-w * 0.11, 0))
	hem.add_point(Vector2(w * 0.46, h - 14), Vector2(w * 0.10, 0), Vector2(-w * 0.10, 0))
	hem.add_point(Vector2(w * 0.25, h - 9), Vector2(w * 0.08, 0), Vector2(-w * 0.10, 0))
	hem.add_point(Vector2(6, h - 17), Vector2(w * 0.12, 2))
	if blue:
		draw_set_transform(Vector2(w, 0), 0.0, Vector2(-1, 1))
	var crown := top.tessellate()
	var lower := hem.tessellate()
	var points := crown.duplicate()
	points.append_array(lower)
	MeadowDraw.gradient_fill(self, points, Rect2(Vector2.ZERO, size),
		Color("#2768a5") if blue else Color("#a4473b"), Color("#102e4d") if blue else Color("#52241d"))

	draw_polyline(crown, Color("#57331f"), 4.4, true)
	draw_polyline(crown, Color("#e8ae87") if not blue else Color("#aad0e6"), 2.0, true)
	var stitch_color := Color("#eca78c") if not blue else Color("#8eb4d5")
	_draw_stitches(top, 3.2, stitch_color)

	draw_polyline(lower, Color("#56331c"), 7.0, true)
	draw_polyline(lower, Color("#c68742"), 5.0, true)
	draw_polyline(lower, Color("#efb568"), 2.0, true)
	_draw_stitches(hem, -4.1, stitch_color)

	_draw_rail(6, 17, h - 17)
	_draw_rail(w - 6, 15, h - 17)
	draw_set_transform(Vector2.ZERO)

func _draw_stitches(curve: Curve2D, offset_y: float, color: Color) -> void:
	var offset := Vector2(0, offset_y)
	var length := curve.get_baked_length()
	for i in range(2, floori(length) - 2, 3):
		var start := curve.sample_baked(i, true) + offset
		var end := curve.sample_baked(i + 1.5, true) + offset
		draw_line(start, end, color, 0.75, true)

func _draw_rail(x: float, top_y: float, bottom_y: float) -> void:
	var bounds := Rect2(x - 2.8, top_y, 5.6, bottom_y - top_y)
	MeadowDraw.rounded_gradient(self, bounds, 2.6, Color("#93623b"), Color("#ac6f37"))
	draw_line(Vector2(x - 1.1, top_y), Vector2(x - 1.1, bottom_y), Color("#d9a15d"), 1.3, true)
	for y in [top_y, bottom_y]:
		var cap := Rect2(x - 4.1, y - 4.4, 8.2, 8.8)
		MeadowDraw.rounded_gradient(self, cap, 3.3, Color("#e6b67a"), Color("#8b512c"))
		draw_line(Vector2(x - 1.9, y - 2.6), Vector2(x - 1.9, y + 2.3), Color("#f1c58b"), 0.85, true)
