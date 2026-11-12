class_name MeadowDraw
extends RefCounted
## Vector shapes drawn in code for the Moonlit Meadow kit. No bitmaps: every
## helper draws on the CanvasItem it is given, inside that item's _draw().

const LEAF := Color("#8cc463")
const LEAF_MID := Color("#5f9e43")
const LEAF_DARK := Color("#2f5f26")
const STEM := Color("#4f3820")
const CREAM := Color("#f6ead0")

# ---------------------------------------------------------------- primitives

static func rounded_points(rect: Rect2, radius: float, steps := 7) -> PackedVector2Array:
	var r := minf(radius, minf(rect.size.x, rect.size.y) * 0.5 - 0.02)
	if r <= 0.0:
		return PackedVector2Array([rect.position, Vector2(rect.end.x, rect.position.y), rect.end, Vector2(rect.position.x, rect.end.y)])
	var points := PackedVector2Array()
	var corners := [
		[rect.position + Vector2(rect.size.x - r, r), -PI * 0.5],
		[rect.end - Vector2(r, r), 0.0],
		[rect.position + Vector2(r, rect.size.y - r), PI * 0.5],
		[rect.position + Vector2(r, r), PI],
	]
	for corner in corners:
		for i in steps + 1:
			var a: float = corner[1] + PI * 0.5 * i / steps
			points.append(corner[0] + Vector2(cos(a), sin(a)) * r)
	return points

static func ellipse_points(center: Vector2, radii: Vector2, angle := 0.0, steps := 32) -> PackedVector2Array:
	var points := PackedVector2Array()
	for i in steps:
		var t := TAU * i / steps
		points.append(center + Vector2(cos(t) * radii.x, sin(t) * radii.y).rotated(angle))
	return points

## Fills `points` with a vertical gradient between `top` and `bottom`, measured over `span`.
static func gradient_fill(canvas: CanvasItem, points: PackedVector2Array, span: Rect2, top: Color, bottom: Color) -> void:
	var colors := PackedColorArray()
	for point in points:
		var t := clampf((point.y - span.position.y) / maxf(span.size.y, 1.0), 0.0, 1.0)
		colors.append(top.lerp(bottom, t))
	canvas.draw_polygon(points, colors)

static func rounded_gradient(canvas: CanvasItem, rect: Rect2, radius: float, top: Color, bottom: Color) -> void:
	gradient_fill(canvas, rounded_points(rect, radius), rect, top, bottom)

static func rounded_outline(canvas: CanvasItem, rect: Rect2, radius: float, color: Color, width := 1.5) -> void:
	var points := rounded_points(rect, radius)
	points.append(points[0])
	canvas.draw_polyline(points, color, width, true)

static func ellipse(canvas: CanvasItem, center: Vector2, radii: Vector2, color: Color, angle := 0.0) -> void:
	canvas.draw_colored_polygon(ellipse_points(center, radii, angle), color)

## Soft drop shadow made of a few stacked translucent rounded rects.
static func soft_shadow(canvas: CanvasItem, rect: Rect2, radius: float, strength := 0.35, spread := 6.0) -> void:
	for i in 4:
		var grow := spread * (i + 1) / 4.0
		var r := rect.grow(grow)
		canvas.draw_colored_polygon(rounded_points(r, radius + grow), Color(0, 0, 0, strength / 5.0))

## Glossy pill / card: shadow, dark rim, gradient body, upper sheen, inner light line.
static func glossy(canvas: CanvasItem, rect: Rect2, radius: float, top: Color, bottom: Color, rim: Color, glow := Color.TRANSPARENT) -> void:
	if glow.a > 0.0:
		for i in 5:
			var grow := 2.0 + i * 2.2
			canvas.draw_colored_polygon(rounded_points(rect.grow(grow), radius + grow), Color(glow, glow.a * (1.0 - i / 5.0) * 0.35))
	soft_shadow(canvas, Rect2(rect.position + Vector2(0, 3), rect.size), radius, 0.4, 3.0)
	canvas.draw_colored_polygon(rounded_points(rect.grow(1.5), radius + 1.5), rim)
	rounded_gradient(canvas, rect, radius, top, bottom)
	var sheen := Rect2(rect.position + Vector2(rect.size.y * 0.18, 2.0), Vector2(rect.size.x - rect.size.y * 0.36, rect.size.y * 0.42))
	rounded_gradient(canvas, sheen, minf(radius, sheen.size.y * 0.5), Color(1, 1, 1, 0.38), Color(1, 1, 1, 0.02))
	var inner := rounded_points(rect.grow(-1.5), maxf(radius - 1.5, 0.0))
	inner.append(inner[0])
	canvas.draw_polyline(inner, Color(1, 1, 1, 0.22), 1.2, true)

# ---------------------------------------------------------------- decoration

static func leaf(canvas: CanvasItem, base: Vector2, length: float, angle: float, color := LEAF) -> void:
	var points := PackedVector2Array()
	for i in 15:
		var t := i / 14.0
		points.append(Vector2(t * length, sin(pow(t, 0.8) * PI) * length * 0.32 * (1.0 - t * 0.2)))
	for i in range(13, 0, -1):
		var t := i / 14.0
		points.append(Vector2(t * length, -sin(pow(t, 0.8) * PI) * length * 0.32 * (1.0 - t * 0.2)))
	var moved := PackedVector2Array()
	var colors := PackedColorArray()
	for point in points:
		moved.append(base + point.rotated(angle))
		colors.append(color.lightened(0.18) if point.y < 0.0 else color.darkened(0.12))
	canvas.draw_polygon(moved, colors)
	canvas.draw_line(base, base + Vector2(length * 0.9, 0.0).rotated(angle), LEAF_DARK, maxf(1.0, length * 0.05), true)

## Curved sprig with leaves. `direction` 1 points right, -1 points left.
static func sprig(canvas: CanvasItem, origin: Vector2, length: float, direction := 1.0) -> void:
	# A short curved twig rising outward with three broad leaves, like the reference.
	var d := direction
	var tip := origin + Vector2(length * 0.55 * d, -length * 0.55)
	var mid := origin + Vector2(length * 0.12 * d, -length * 0.32)
	canvas.draw_polyline(PackedVector2Array([origin, mid, tip]), LEAF_DARK, maxf(1.4, length * 0.06), true)
	var up := -PI * 0.5
	leaf(canvas, tip, length * 0.5, up + 0.55 * d)
	leaf(canvas, mid, length * 0.5, up - 0.15 * d, LEAF_MID)
	leaf(canvas, origin.lerp(mid, 0.4), length * 0.46, (0.15 if d > 0 else PI - 0.15))

## Fan of leaves around a point, used at the ends of wooden signs.
static func leaf_cluster(canvas: CanvasItem, center: Vector2, size: float, direction := 1.0) -> void:
	var d := direction
	var angles := [-1.25, -0.6, 0.0, 0.55, 1.1]
	for i in angles.size():
		var a: float = angles[i] * d + (0.0 if d > 0 else PI)
		leaf(canvas, center, size * (0.85 if i % 2 == 0 else 0.7), a, LEAF if i % 2 == 0 else LEAF_MID)

# ---------------------------------------------------------------- paws

## Flat paw print (icons, colour chips).
static func paw(canvas: CanvasItem, center: Vector2, size: float, color: Color, outline := Color.TRANSPARENT) -> void:
	var pad := center + Vector2(0.0, size * 0.17)
	var toes := _toes(center, size)
	if outline.a > 0.0:
		ellipse(canvas, pad, Vector2(size * 0.32, size * 0.26) + Vector2.ONE * size * 0.06, outline)
		for toe in toes:
			ellipse(canvas, toe[0], toe[1] + Vector2.ONE * size * 0.06, outline, toe[2])
	ellipse(canvas, pad, Vector2(size * 0.32, size * 0.26), color)
	for toe in toes:
		ellipse(canvas, toe[0], toe[1], color, toe[2])

static func _toes(center: Vector2, size: float) -> Array:
	return [
		[center + Vector2(-0.37, -0.1) * size, Vector2(0.115, 0.14) * size, -0.4],
		[center + Vector2(-0.135, -0.31) * size, Vector2(0.12, 0.15) * size, -0.12],
		[center + Vector2(0.135, -0.31) * size, Vector2(0.12, 0.15) * size, 0.12],
		[center + Vector2(0.37, -0.1) * size, Vector2(0.115, 0.14) * size, 0.4],
	]

## Shaded "jelly bean" paw with highlights, for team colour chips.
static func glossy_paw(canvas: CanvasItem, center: Vector2, size: float, color: Color) -> void:
	paw(canvas, center + Vector2(0, size * 0.06), size * 1.04, color.darkened(0.6))
	paw(canvas, center, size, color.darkened(0.2))
	paw(canvas, center - Vector2(0, size * 0.035), size * 0.88, color)
	var pad := center + Vector2(-size * 0.08, size * 0.08)
	ellipse(canvas, pad, Vector2(size * 0.09, size * 0.04), Color(1, 1, 1, 0.45), -0.3)
	for toe in _toes(center, size):
		ellipse(canvas, toe[0] + Vector2(-size * 0.025, -size * 0.045), toe[1] * 0.3, Color(1, 1, 1, 0.5), toe[2])

## Fluffy fur paw standing on a round wooden tray (animal picker).
static func fur_paw_token(canvas: CanvasItem, center: Vector2, radius: float, fur: Color, pads: Color, tuft := Color.TRANSPARENT) -> void:
	var r := radius
	# Wooden tray seen at an angle.
	var tray := center + Vector2(0, r * 0.52)
	ellipse(canvas, tray + Vector2(0, r * 0.08), Vector2(r * 1.0, r * 0.46), Color(0, 0, 0, 0.45))
	gradient_fill(canvas, ellipse_points(tray, Vector2(r * 0.98, r * 0.44)), Rect2(tray - Vector2(r, r * 0.45), Vector2(r * 2.0, r * 0.9)), Color("#a8703d"), Color("#4a2a14"))
	ellipse(canvas, tray + Vector2(0, -r * 0.03), Vector2(r * 0.84, r * 0.34), Color("#3a2010"))
	canvas.draw_arc(tray, r * 0.9, PI * 1.05, PI * 1.95, 24, Color(1, 0.85, 0.6, 0.0), 1.0, true)
	# Fur dome: fluffy rim of circles, then body with light from the top-left.
	var dome := center + Vector2(0, -r * 0.05)
	var radii := Vector2(r * 0.74, r * 0.82)
	ellipse(canvas, dome + Vector2(0, r * 0.06), radii + Vector2.ONE * r * 0.06, fur.darkened(0.45))
	for i in 22:
		var a := TAU * i / 22.0
		var wobble := 0.94 + 0.08 * sin(i * 2.7)
		var p := dome + Vector2(cos(a) * radii.x, sin(a) * radii.y) * wobble
		canvas.draw_circle(p, r * 0.16, fur.lightened(0.1) if sin(a) < -0.2 else fur.darkened(0.08))
	gradient_fill(canvas, ellipse_points(dome, radii), Rect2(dome - radii, radii * 2.0), fur.lightened(0.16), fur.darkened(0.22))
	ellipse(canvas, dome + Vector2(-r * 0.22, -r * 0.42), Vector2(r * 0.3, r * 0.16), Color(1, 1, 1, 0.16), -0.4)
	if tuft.a > 0.0:
		for i in 7:
			var t := (i - 3) / 3.0
			canvas.draw_circle(dome + Vector2(t * r * 0.42, r * 0.62 - abs(t) * r * 0.1), r * 0.15, tuft)
	# Pads: large bean toes and main pad, each with a dark rim and a shine.
	var toes := [
		[Vector2(-0.44, -0.12), Vector2(0.15, 0.2), -0.45], [Vector2(-0.16, -0.42), Vector2(0.16, 0.21), -0.15],
		[Vector2(0.16, -0.42), Vector2(0.16, 0.21), 0.15], [Vector2(0.44, -0.12), Vector2(0.15, 0.2), 0.45],
	]
	for toe in toes:
		var c: Vector2 = dome + toe[0] * r
		var size: Vector2 = toe[1] * r
		ellipse(canvas, c + Vector2(0, r * 0.03), size + Vector2.ONE * r * 0.025, pads.darkened(0.5), toe[2])
		gradient_fill(canvas, ellipse_points(c, size, toe[2], 20), Rect2(c - size, size * 2.0), pads.lightened(0.18), pads.darkened(0.12))
		ellipse(canvas, c + Vector2(-size.x * 0.25, -size.y * 0.35), size * 0.32, Color(1, 1, 1, 0.42), toe[2])
	var main := dome + Vector2(0, r * 0.22)
	var main_size := Vector2(r * 0.36, r * 0.28)
	var heart := PackedVector2Array()
	for i in 36:
		var a := TAU * i / 36.0
		var squash := 1.0 + 0.18 * sin(a)
		heart.append(main + Vector2(cos(a) * main_size.x * squash, sin(a) * main_size.y))
	var rim := PackedVector2Array()
	for point in heart:
		rim.append(main + (point - main) * 1.1 + Vector2(0, r * 0.03))
	canvas.draw_colored_polygon(rim, pads.darkened(0.5))
	gradient_fill(canvas, heart, Rect2(main - main_size, main_size * 2.0), pads.lightened(0.18), pads.darkened(0.15))
	ellipse(canvas, main + Vector2(-main_size.x * 0.35, -main_size.y * 0.4), main_size * Vector2(0.32, 0.22), Color(1, 1, 1, 0.38), -0.2)

# ---------------------------------------------------------------- glyphs

static func arrow_left(canvas: CanvasItem, center: Vector2, size: float, color: Color) -> void:
	var w := size * 0.12
	canvas.draw_line(center + Vector2(-size * 0.3, 0), center + Vector2(size * 0.32, 0), color, w, true)
	canvas.draw_polyline(PackedVector2Array([
		center + Vector2(-size * 0.02, -size * 0.29), center + Vector2(-size * 0.31, 0), center + Vector2(-size * 0.02, size * 0.29),
	]), color, w, true)
	canvas.draw_circle(center + Vector2(size * 0.32, 0), w * 0.5, color)
	canvas.draw_circle(center + Vector2(-size * 0.02, -size * 0.29), w * 0.5, color)
	canvas.draw_circle(center + Vector2(-size * 0.02, size * 0.29), w * 0.5, color)

static func chevron_down(canvas: CanvasItem, center: Vector2, size: float, color: Color) -> void:
	var w := size * 0.2
	var points := PackedVector2Array([center + Vector2(-size * 0.42, -size * 0.18), center + Vector2(0, size * 0.22), center + Vector2(size * 0.42, -size * 0.18)])
	canvas.draw_polyline(points, color, w, true)
	for point in points:
		canvas.draw_circle(point, w * 0.5, color)

static func check(canvas: CanvasItem, center: Vector2, size: float, color: Color) -> void:
	var w := size * 0.17
	var points := PackedVector2Array([center + Vector2(-size * 0.33, 0.0), center + Vector2(-size * 0.08, size * 0.26), center + Vector2(size * 0.36, -size * 0.26)])
	canvas.draw_polyline(points, color, w, true)
	for point in points:
		canvas.draw_circle(point, w * 0.5, color)

static func gear(canvas: CanvasItem, center: Vector2, size: float, color: Color, hole := Color("#0d2c22")) -> void:
	var points := PackedVector2Array()
	for i in 64:
		var t := TAU * i / 64.0
		var phase := fposmod(t / (TAU / 8.0), 1.0)
		var tooth := 1.0 if phase > 0.18 and phase < 0.62 else 0.78
		points.append(center + Vector2(cos(t), sin(t)) * size * 0.5 * tooth)
	canvas.draw_colored_polygon(points, color)
	canvas.draw_circle(center, size * 0.17, hole)

static func play(canvas: CanvasItem, center: Vector2, size: float, color: Color) -> void:
	var points := PackedVector2Array([center + Vector2(-size * 0.34, -size * 0.44), center + Vector2(size * 0.46, 0), center + Vector2(-size * 0.34, size * 0.44)])
	canvas.draw_colored_polygon(points, color)
	points.append(points[0])
	canvas.draw_polyline(points, color, size * 0.1, true)

static func people(canvas: CanvasItem, center: Vector2, size: float, color: Color, count := 3) -> void:
	var offsets := [Vector2.ZERO] if count == 1 else ([Vector2(-0.2, 0), Vector2(0.2, 0)] if count == 2 else [Vector2(-0.3, 0.06), Vector2(0.3, 0.06), Vector2(0, 0)])
	for i in offsets.size():
		var main := i == offsets.size() - 1 and count == 3
		var s := size * (0.62 if not main else 0.72) if count == 3 else size * 0.7
		var p: Vector2 = center + offsets[i] * size
		var body := PackedVector2Array()
		for k in 17:
			var a := PI + PI * k / 16.0
			body.append(p + Vector2(cos(a) * s * 0.42, s * 0.42 + sin(a) * s * 0.36))
		canvas.draw_colored_polygon(body, color if main or count != 3 else color.darkened(0.12))
		canvas.draw_circle(p + Vector2(0, -s * 0.16), s * 0.22, color if main or count != 3 else color.darkened(0.12))

static func bars(canvas: CanvasItem, center: Vector2, size: float, color: Color) -> void:
	var w := size * 0.2
	var heights := [0.38, 0.62, 0.86]
	for i in 3:
		var h: float = heights[i] * size
		var x := center.x + (i - 1) * size * 0.3 - w * 0.5
		canvas.draw_colored_polygon(rounded_points(Rect2(Vector2(x, center.y + size * 0.43 - h), Vector2(w, h)), w * 0.3, 3), color)

static func pencil(canvas: CanvasItem, center: Vector2, size: float, color: Color) -> void:
	var dir := Vector2(1, -1).normalized()
	var normal := Vector2(dir.y, -dir.x)
	var tail := center + dir * size * 0.42
	var neck := center - dir * size * 0.22
	var tip := center - dir * size * 0.46
	var w := size * 0.13
	canvas.draw_colored_polygon(PackedVector2Array([tail + normal * w, tail - normal * w, neck - normal * w, neck + normal * w]), color)
	canvas.draw_colored_polygon(PackedVector2Array([neck + normal * w, neck - normal * w, tip]), color.darkened(0.15))
	canvas.draw_line(tail - dir * size * 0.12 + normal * w, tail - dir * size * 0.12 - normal * w, Color(0, 0, 0, 0.35), size * 0.04, true)

static func robot(canvas: CanvasItem, center: Vector2, size: float, color: Color, face := Color("#2a1a0e")) -> void:
	var head := Rect2(center + Vector2(-size * 0.4, -size * 0.2), Vector2(size * 0.8, size * 0.6))
	canvas.draw_line(center + Vector2(0, -size * 0.2), center + Vector2(0, -size * 0.38), color, size * 0.07, true)
	canvas.draw_circle(center + Vector2(0, -size * 0.42), size * 0.075, color)
	canvas.draw_colored_polygon(rounded_points(Rect2(head.position + Vector2(-size * 0.13, size * 0.14), Vector2(size * 1.06, size * 0.3)), size * 0.07, 3), color.darkened(0.08))
	canvas.draw_colored_polygon(rounded_points(head, size * 0.17), color)
	var screen := head.grow(-size * 0.09)
	canvas.draw_colored_polygon(rounded_points(screen, size * 0.11), face)
	canvas.draw_circle(screen.get_center() + Vector2(-size * 0.15, -size * 0.02), size * 0.075, color)
	canvas.draw_circle(screen.get_center() + Vector2(size * 0.15, -size * 0.02), size * 0.075, color)
	canvas.draw_line(screen.get_center() + Vector2(-size * 0.09, size * 0.11), screen.get_center() + Vector2(size * 0.09, size * 0.11), color, size * 0.05, true)

static func dice_pair(canvas: CanvasItem, center: Vector2, size: float, color: Color, pip := Color("#2a1a0e")) -> void:
	_die(canvas, center + Vector2(-size * 0.2, size * 0.02), size * 0.5, -0.25, color, pip, [Vector2(-1, -1), Vector2(1, 1), Vector2(0, 0)])
	_die(canvas, center + Vector2(size * 0.22, -size * 0.06), size * 0.46, 0.3, color.darkened(0.08), pip, [Vector2(-1, -1), Vector2(1, -1), Vector2(-1, 1), Vector2(1, 1)])

static func _die(canvas: CanvasItem, center: Vector2, size: float, angle: float, color: Color, pip: Color, pips: Array) -> void:
	var points := rounded_points(Rect2(-Vector2.ONE * size * 0.5, Vector2.ONE * size), size * 0.2, 4)
	var moved := PackedVector2Array()
	for point in points:
		moved.append(center + point.rotated(angle))
	canvas.draw_colored_polygon(moved, color)
	moved.append(moved[0])
	canvas.draw_polyline(moved, pip, maxf(1.0, size * 0.06), true)
	for p in pips:
		canvas.draw_circle(center + (p * size * 0.24).rotated(angle), size * 0.09, pip)

## Shared gold bevel and wooden face for small icon buttons.
static func wood_icon_face(canvas: CanvasItem, center: Vector2, points: PackedVector2Array, bounds: Rect2, scale := 1.0, cast_shadow := true) -> void:
	if cast_shadow:
		canvas.draw_set_transform(center + Vector2(0, 2), 0, Vector2.ONE * (1.07 * scale))
		canvas.draw_colored_polygon(points, Color("#241a10"))
	# Five narrow bevel faces give the rim a soft round cross-section.
	var bevel := [
		[1.00, Color("#a97437"), Color("#7c451d")],
		[0.98, Color("#f2c876"), Color("#ae6a24")],
		[0.95, Color("#ffe7a6"), Color("#f7be5d")],
		[0.91, Color("#d7a253"), Color("#ffdb87")],
		[0.86, Color("#855121"), Color("#bb792f")],
		[0.82, Color("#452910"), Color("#815124")],
	]
	for face in bevel:
		canvas.draw_set_transform(center, 0, Vector2.ONE * (float(face[0]) * scale))
		gradient_fill(canvas, points, bounds, face[1], face[2])
	canvas.draw_set_transform(center + Vector2(0, 0.25), 0, Vector2.ONE * (0.78 * scale))
	gradient_fill(canvas, points, bounds, Color("#725031"), Color("#432a17"))
	canvas.draw_set_transform(Vector2.ZERO)

static func chain_link(canvas: CanvasItem, center: Vector2, size: float, color: Color) -> void:
	var loop := Curve2D.new()
	loop.add_point(Vector2(-0.015, -0.085), Vector2.ZERO, Vector2(-0.025, -0.045))
	loop.add_point(Vector2(-0.125, -0.165), Vector2(0.052, 0), Vector2(-0.055, 0))
	loop.add_point(Vector2(-0.31, -0.165), Vector2(0.04, 0), Vector2(-0.095, 0))
	loop.add_point(Vector2(-0.465, 0), Vector2(0, -0.09), Vector2(0, 0.09))
	loop.add_point(Vector2(-0.31, 0.165), Vector2(-0.095, 0), Vector2(0.046, 0))
	loop.add_point(Vector2(-0.115, 0.165))
	var path := loop.tessellate(5, 3)
	for side in [-1, 1]:
		var points := PackedVector2Array()
		for point in path:
			points.append(center + (point * side * size).rotated(-PI / 4.0))
		_link_stroke(canvas, points, Vector2(0, size * 0.022), size * 0.16, Color("#4a3216"))
		_link_stroke(canvas, points, Vector2.ZERO, size * 0.132, color.darkened(0.23))
		_link_stroke(canvas, points, Vector2(0, -size * 0.014), size * 0.102, color)
		_link_stroke(canvas, points, Vector2(0, -size * 0.038), size * 0.037, color.lightened(0.4))

static func _link_stroke(canvas: CanvasItem, path: PackedVector2Array, offset: Vector2, width: float, color: Color) -> void:
	var points := path.duplicate()
	for i in points.size():
		points[i] += offset
	canvas.draw_polyline(points, color, width, true)
	canvas.draw_circle(points[0], width * 0.5, color, true, -1, true)
	canvas.draw_circle(points[-1], width * 0.5, color, true, -1, true)

static func padlock(canvas: CanvasItem, center: Vector2, size: float, color: Color) -> void:
	canvas.draw_arc(center - Vector2(0, size * 0.08), size * 0.23, PI, TAU, 16, color, size * 0.11, true)
	var body := Rect2(center + Vector2(-0.36, -0.11) * size, Vector2(0.72, 0.51) * size)
	canvas.draw_colored_polygon(rounded_points(body, size * 0.11), color)
	canvas.draw_circle(center + Vector2(0, size * 0.11), size * 0.07, Color("#372617"), true, -1, true)

const GLYPHS := ["lock", "link", "robot", "dice", "pass_play", "people", "friends", "team", "bars", "difficulty", "pencil", "edit", "paw", "play", "back", "gear", "check", "chevron", "search", "join"]

static func has_glyph(name: String) -> bool:
	return name in GLYPHS

## Draws a named glyph centred in `rect` (used by MeadowIcon and buttons).
static func glyph(canvas: CanvasItem, name: String, center: Vector2, size: float, color: Color) -> void:
	match name:
		"search":
			var lens := center - Vector2.ONE * size * 0.12
			canvas.draw_arc(lens, size * 0.25, 0, TAU, 24, color, size * 0.1, true)
			canvas.draw_line(center + Vector2.ONE * size * 0.08, center + Vector2.ONE * size * 0.34, color, size * 0.1, true)
		"join":
			var door := Rect2(center + Vector2(-0.05, -0.38) * size, Vector2(0.41, 0.76) * size)
			canvas.draw_rect(door, color, false, size * 0.075, true)
			canvas.draw_line(center - Vector2(size * 0.42, 0), center + Vector2(size * 0.15, 0), color, size * 0.08, true)
			canvas.draw_polyline(PackedVector2Array([
				center + Vector2(0.01, -0.14) * size, center + Vector2(0.16, 0) * size,
				center + Vector2(0.01, 0.14) * size]), color, size * 0.08, true)
		"lock": padlock(canvas, center, size, color)
		"link": chain_link(canvas, center, size, color)
		"robot": robot(canvas, center, size, color)
		"dice", "pass_play": dice_pair(canvas, center, size, color)
		"people", "friends", "team": people(canvas, center, size, color, 3)
		"bars", "difficulty": bars(canvas, center, size, color)
		"pencil", "edit": pencil(canvas, center, size, color)
		"paw": paw(canvas, center + Vector2(0, size * 0.04), size * 0.9, color)
		"play": play(canvas, center, size * 0.8, color)
		"back": arrow_left(canvas, center, size, color)
		"gear": gear(canvas, center, size, color)
		"check": check(canvas, center, size, color)
		"chevron": chevron_down(canvas, center, size, color)
