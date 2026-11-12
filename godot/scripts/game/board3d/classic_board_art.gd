@tool
extends Control
## Static 2D artwork of the classic board, baked once per size like the live
## LudoPawsBoardChromeView bitmap. Port of ClassicJungleHabitat.draw and
## ClassicBoardRenderer.draw (cross tile depth, safe shields, entry stars, centre depth).
## Board pixel space: (0,0) top-left, `edge` px square.

const KR = preload("res://scripts/game/board3d/kotlin_random.gd")
const Palette = preload("res://scripts/game/board3d/board_palette.gd")
const Road = preload("res://scripts/game/board3d/classic_road_scene.gd")

const MOSS := 0x698349
const LEAF := 0x3E713E
const DEEP_LEAF := 0x284F35
const COLORED_ENTRIES := [Vector2i(6, 1), Vector2i(1, 8), Vector2i(8, 13), Vector2i(13, 6)]
const NEUTRAL_SAFE_CELLS := [Vector2i(2, 6), Vector2i(6, 12), Vector2i(12, 8), Vector2i(8, 2)]

static var _grain_textures: Array = []
static var _gradients := {}

var edge := 0.0
var density := 1.0
var _stack: Array[Transform2D] = []
var _xf := Transform2D.IDENTITY
var _grain_layer: Node2D
var _grain_jobs: Array = []

func _ready() -> void:
	texture_filter = CanvasItem.TEXTURE_FILTER_LINEAR
	_grain_layer = Node2D.new()
	_grain_layer.texture_repeat = CanvasItem.TEXTURE_REPEAT_ENABLED
	_grain_layer.draw.connect(_draw_grain_layer)
	add_child(_grain_layer)
	resized.connect(queue_redraw)

static func c(hex: int, alpha := 255) -> Color:
	return Palette.color(hex, alpha / 255.0)

## 0xAARRGGBB literal as used in the Kotlin source.
static func argb(value: int) -> Color:
	return Color8((value >> 16) & 255, (value >> 8) & 255, value & 255, (value >> 24) & 255)

func _draw() -> void:
	var probe_t := Time.get_ticks_usec()
	_probed_draw()
	preload("res://scripts/game/perf.gd").span("draw_board_art", Time.get_ticks_usec() - probe_t)

func _probed_draw() -> void:
	edge = minf(size.x, size.y)
	_grain_jobs.clear()
	if edge <= 0.0:
		return
	density = edge / 380.0
	_xf = Transform2D.IDENTITY
	var cell := edge / 15.0
	# Fallback behind the transparent centre (covered by the 3D garden).
	draw_rect(Rect2(Vector2.ZERO, Vector2.ONE * edge), c(Palette.CLASSIC_SURFACE))
	_jungle(edge)
	_cross_tile_depth(cell)
	_center_depth(cell)
	_grain_layer.queue_redraw()

# ---------------------------------------------------------------- transform stack

func _push(t: Transform2D) -> void:
	_stack.append(_xf)
	_xf = _xf * t
	draw_set_transform_matrix(_xf)

func _pop() -> void:
	_xf = _stack.pop_back()
	draw_set_transform_matrix(_xf)

# ---------------------------------------------------------------- primitives

static func _gradient_texture(colors: Array, stops: Array, radial: bool) -> GradientTexture2D:
	var key := "%s|%s|%s" % [colors, stops, radial]
	if _gradients.has(key):
		return _gradients[key]
	var g := Gradient.new()
	var offsets := PackedFloat32Array()
	var cols := PackedColorArray()
	for i in colors.size():
		offsets.append(stops[i] if not stops.is_empty() else float(i) / (colors.size() - 1))
		cols.append(colors[i])
	g.offsets = offsets
	g.colors = cols
	var t := GradientTexture2D.new()
	t.gradient = g
	if radial:
		t.fill = GradientTexture2D.FILL_RADIAL
		t.fill_from = Vector2(0.5, 0.5)
		t.fill_to = Vector2(1.0, 0.5)
		t.width = 256
		t.height = 256
	else:
		t.fill_from = Vector2(0, 0)
		t.fill_to = Vector2(1, 0)
		t.width = 256
		t.height = 1
	_gradients[key] = t
	return t

## android LinearGradient(from, to, colors, stops, CLAMP) over a polygon.
func _linear(points: PackedVector2Array, from: Vector2, to: Vector2, colors: Array, stops: Array = []) -> void:
	var axis := to - from
	var length2 := maxf(axis.length_squared(), 0.000001)
	var uvs := PackedVector2Array()
	for p in points:
		uvs.append(Vector2(clampf((p - from).dot(axis) / length2, 0.0, 1.0), 0.5))
	draw_colored_polygon(points, Color.WHITE, uvs, _gradient_texture(colors, stops, false))

## android RadialGradient(center, radius, colors, stops, CLAMP) over a polygon.
func _radial(points: PackedVector2Array, center: Vector2, radius: float, colors: Array, stops: Array = []) -> void:
	# Outside the radius the texture's padded edge texels hold the last stop (CLAMP).
	var uvs := PackedVector2Array()
	for p in points:
		uvs.append(Vector2(0.5, 0.5) + (p - center) / radius * 0.5)
	draw_colored_polygon(points, Color.WHITE, uvs, _gradient_texture(colors, stops, true))

static func _round_rect(r: Rect2, radius: float, steps := 6) -> PackedVector2Array:
	var out := PackedVector2Array()
	var rad := minf(radius, minf(r.size.x, r.size.y) * 0.5)
	if rad <= 0.0:
		return PackedVector2Array([r.position, Vector2(r.end.x, r.position.y), r.end, Vector2(r.position.x, r.end.y)])
	var corners := [Vector2(r.end.x - rad, r.position.y + rad), Vector2(r.end.x - rad, r.end.y - rad),
		Vector2(r.position.x + rad, r.end.y - rad), Vector2(r.position.x + rad, r.position.y + rad)]
	var starts := [-PI / 2.0, 0.0, PI / 2.0, PI]
	for k in 4:
		for s in steps + 1:
			var a: float = starts[k] + s * (PI / 2.0) / steps
			out.append(corners[k] + Vector2(cos(a), sin(a)) * rad)
	return out

static func _oval(center: Vector2, rx: float, ry: float, steps := 14) -> PackedVector2Array:
	var out := PackedVector2Array()
	for s in steps:
		var a := s * TAU / steps
		out.append(center + Vector2(cos(a) * rx, sin(a) * ry))
	return out

func _fill(points: PackedVector2Array, color: Color) -> void:
	if points.size() >= 3:
		draw_colored_polygon(points, color)

func _stroke_line(a: Vector2, b: Vector2, color: Color, width: float, round_cap := true) -> void:
	draw_line(a, b, color, width, true)
	if round_cap:
		draw_circle(a, width * 0.5, color, true, -1, true)
		draw_circle(b, width * 0.5, color, true, -1, true)

static func _quad_to(out: PackedVector2Array, control: Vector2, end: Vector2, steps := 8) -> void:
	var start := out[out.size() - 1]
	for s in range(1, steps + 1):
		var t := float(s) / steps
		out.append(start.lerp(control, t).lerp(control.lerp(end, t), t))

static func _cubic_to(out: PackedVector2Array, c1: Vector2, c2: Vector2, end: Vector2, steps := 14) -> void:
	var start := out[out.size() - 1]
	for s in range(1, steps + 1):
		var t := float(s) / steps
		var u := 1.0 - t
		out.append(start * u * u * u + c1 * 3.0 * u * u * t + c2 * 3.0 * u * t * t + end * t * t * t)

# ---------------------------------------------------------------- jungle habitat

func _jungle(board: float) -> void:
	_push(Transform2D.IDENTITY.scaled(Vector2.ONE * board / 150.0))
	_clearing(0, 0, Palette.CORAL, 742)
	_clearing(100, 0, Palette.JADE, 1321)
	_clearing(100, 100, Palette.HONEY, 2089)
	_clearing(0, 100, Palette.SKY, 3011)
	_pop()

func _clearing(left: float, top: float, team: int, seed: int) -> void:
	_push(Transform2D(0, Vector2(left, top)))
	var square := PackedVector2Array([Vector2(0, 0), Vector2(50, 0), Vector2(50, 50), Vector2(0, 50)])
	_linear(square, Vector2.ZERO, Vector2(50, 50), [c(0x506B3D), c(0x314F35)])
	var random := KR.XorWow.new(seed)
	for k in 180:
		var x := random.next_float() * 50.0
		var y := random.next_float() * 50.0
		_fill(_clip_square(_oval(Vector2(x, y), 1.4, 0.7, 10)), argb(0x305D8B43 if k % 3 == 0 else 0x2831412A))
	var path := PackedVector2Array([Vector2(12, 6.7)])
	_cubic_to(path, Vector2(23, 3), Vector2(43, 5), Vector2(44, 15))
	_cubic_to(path, Vector2(47, 23), Vector2(44, 40), Vector2(37, 44))
	_cubic_to(path, Vector2(26, 47), Vector2(9, 44), Vector2(6, 35))
	_cubic_to(path, Vector2(3, 24), Vector2(5, 11), Vector2(12, 6.7))
	path.remove_at(path.size() - 1)
	_fill(path, c(0x354C30))
	_radial(path, Vector2(22, 20), 32.0, [c(0xADB17B), c(0x8D9861), c(0x607E49)], [0.0, 0.65, 1.0])
	for k in 260:
		var x := 5.0 + random.next_float() * 40.0
		var y := 5.0 + random.next_float() * 40.0
		if Geometry2D.is_point_in_polygon(Vector2(x, y), path):
			_fill(_oval(Vector2(x, y), 0.6, 0.28, 8), argb(0x2072623D if k % 2 == 0 else 0x2090AC67))
	var closed := path.duplicate()
	closed.append(path[0])
	draw_polyline(closed, c(0x526A3E), 0.55, true)
	_stroke_line(Vector2(18, 47), Vector2(32, 47), c(team), 0.9)
	_stroke_line(Vector2(47, 19), Vector2(47, 31), c(team), 0.6)
	for index in 16:
		var along := 4.0 + index * 2.7
		_fern(along, 3.5, 12.0 + index * 19.0, 0.7)
		_fern(50.0 - along, 46.7, 164.0 + index * 17.0, 0.72)
	_bamboo(3.5, 22)
	_bamboo(46.5, 28)
	_palm(5.8, 6, 1.0)
	_palm(44.5, 44, 0.9)
	_fern(43.5, 5.5, 125, 1.25)
	_fern(6.3, 44, -45, 1.25)
	_rock(38, 4, 2)
	_rock(12, 46, 1.6)
	_pop()

## canvas.clipRect(0, 0, 50, 50) for the scattered specks.
static func _clip_square(points: PackedVector2Array) -> PackedVector2Array:
	var clipped := Geometry2D.intersect_polygons(points, PackedVector2Array([Vector2(0, 0), Vector2(50, 0), Vector2(50, 50), Vector2(0, 50)]))
	return clipped[0] if not clipped.is_empty() else PackedVector2Array()

func _fern(x: float, y: float, rotation: float, scale: float) -> void:
	_push(Transform2D(deg_to_rad(rotation), Vector2.ONE * scale, 0.0, Vector2(x, y)))
	_stroke_line(Vector2.ZERO, Vector2(0, -3.2), c(MOSS), 0.24, false)
	for index in 4:
		var along := -0.45 - index * 0.65
		var width := 1.6 - index * 0.29
		var leaf := PackedVector2Array([Vector2(0, along)])
		_quad_to(leaf, Vector2(-width, along + 0.15), Vector2(-width, along - 0.7))
		_quad_to(leaf, Vector2(-0.4, along - 0.65), Vector2(0, along))
		_quad_to(leaf, Vector2(width, along + 0.15), Vector2(width, along - 0.7))
		_quad_to(leaf, Vector2(0.4, along - 0.65), Vector2(0, along))
		_fill_halves(leaf, c(LEAF if index % 2 == 0 else MOSS))
	_pop()

## Self-touching paths (two lobes meeting at a point) are filled lobe by lobe.
func _fill_halves(points: PackedVector2Array, color: Color) -> void:
	var half := points.size() / 2
	_fill(points.slice(0, half + 1), color)
	_fill(points.slice(half), color)

func _palm(x: float, y: float, scale: float) -> void:
	_fill(_oval(Vector2(x + 0.25, y + 1.5), 4.75, 3.5), argb(0x35304528))
	_push(Transform2D(0, Vector2.ONE * scale, 0, Vector2(x, y)))
	_stroke_line(Vector2(0, 2.8), Vector2(0.5, -0.6), c(0x7D7950), 0.8, false)
	for frond in 8:
		_push(Transform2D(deg_to_rad(frond * 45.0), Vector2.ZERO))
		var leaf := PackedVector2Array([Vector2.ZERO])
		_quad_to(leaf, Vector2(-2.2, -3), Vector2(0.8, -5.2))
		_quad_to(leaf, Vector2(2, -1.6), Vector2.ZERO)
		leaf.remove_at(leaf.size() - 1)
		_fill(leaf, c(LEAF if frond % 2 == 0 else DEEP_LEAF))
		_stroke_line(Vector2.ZERO, Vector2(0.8, -4.7), c(MOSS), 0.20, true)
		_pop()
	_pop()

func _bamboo(x: float, y: float) -> void:
	for stem in 3:
		var offset := (stem - 1) * 0.9
		_stroke_line(Vector2(x + offset, y + 5), Vector2(x + offset + 0.5, y - 5), c(MOSS if stem == 1 else DEEP_LEAF), 0.65, true)
		for joint in 4:
			var height := y - 3.5 + joint * 2.4
			_stroke_line(Vector2(x + offset - 0.2, height), Vector2(x + offset + 0.6, height), c(0x96AA68), 0.22, true)
	_fern(x, y - 4, -70, 0.85)
	_fern(x, y + 3, 70, 0.85)

func _rock(x: float, y: float, radius: float) -> void:
	_fill(_oval(Vector2(x, y + radius * 0.1), radius, radius * 0.6), c(0x43543D))
	var top := _oval(Vector2(x, y - radius * 0.225), radius, radius * 0.575)
	_linear(top, Vector2(x - radius, y - radius), Vector2(x + radius, y + radius), [c(0x8C9974), c(0x5D7051)])

# ---------------------------------------------------------------- road cells

static func _axis(index: int, cell: float) -> float:
	var yard_step := cell * 5.0 / 6.0
	var road_step := cell * 5.0 / 3.0
	if index <= 6:
		return index * yard_step
	if index <= 9:
		return cell * 5.0 + (index - 6) * road_step
	return cell * 10.0 + (index - 9) * yard_step

func _cross_tile_depth(cell: float) -> void:
	var surface := c(Palette.CLASSIC_SURFACE)
	draw_rect(Rect2(cell * 5, 0, cell * 5, cell * 5), surface)
	draw_rect(Rect2(cell * 5, cell * 10, cell * 5, cell * 5), surface)
	draw_rect(Rect2(0, cell * 5, cell * 5, cell * 5), surface)
	draw_rect(Rect2(cell * 10, cell * 5, cell * 5, cell * 5), surface)
	for row in 15:
		for col in 15:
			if Road.is_road(row, col):
				_raised_cell(cell, row, col)

static func _mix(a: Color, b: Color, amount: float) -> Color:
	return Color8(int(a.r8 * (1.0 - amount) + b.r8 * amount), int(a.g8 * (1.0 - amount) + b.g8 * amount), int(a.b8 * (1.0 - amount) + b.b8 * amount))

static func _darken(color: Color, factor: float) -> Color:
	return Color8(clampi(int(color.r8 * factor), 0, 255), clampi(int(color.g8 * factor), 0, 255), clampi(int(color.b8 * factor), 0, 255))

func _raised_cell(cell: float, row: int, col: int) -> void:
	var team := Road.team(row, col)
	var pigment := c(team if team >= 0 else Palette.CLASSIC_NEUTRAL_CELL)
	var raw := Rect2(_axis(col, cell), _axis(row, cell), 0, 0)
	raw.end = Vector2(_axis(col + 1, cell), _axis(row + 1, cell))
	var unit := minf(raw.size.x, raw.size.y)
	var depth := unit * Road.DEPTH
	var normalized := Road.face(Road.raw(row, col))
	var k := cell / 10.0
	var face := Rect2(normalized.position * k, normalized.size * k)
	var body := Rect2(face.position + Vector2(0, depth), face.size)
	var corner := unit * Road.CORNER
	var body_path := _round_rect(body, corner)
	_linear(body_path, body.position, body.end, [_mix(pigment, Color.WHITE, 0.10), _darken(pigment, 0.70)])
	_linear(_round_rect(face, corner), face.position, face.end,
		[_mix(pigment, Color.WHITE, 0.23), pigment, _darken(pigment, 0.78)], [0.0, 0.43, 1.0])
	var bevel := unit * Road.BEVEL
	var top := face.grow(-bevel)
	var top_path := _round_rect(top, corner - bevel)
	_linear(top_path, top.position, top.end, [_mix(pigment, Color.WHITE, 0.11), pigment, _darken(pigment, 0.94)], [0.0, 0.62, 1.0])
	_grain_jobs.append([top_path, 1])
	if Vector2i(row, col) in COLORED_ENTRIES:
		var star := PackedVector2Array()
		for index in 10:
			var angle := deg_to_rad(index * 36.0 - 90.0)
			var radius := cell * 0.27 * (1.0 if index % 2 == 0 else 0.43)
			star.append(raw.get_center() + Vector2(cos(angle), sin(angle)) * radius)
		_fill(star, c(Palette.STONE_LIGHT))
	if Vector2i(row, col) in NEUTRAL_SAFE_CELLS:
		_safe_shield(raw, cell)

func _safe_shield(raw: Rect2, cell: float) -> void:
	var center := raw.get_center()
	var r := cell * 0.28
	var shield := PackedVector2Array([center + Vector2(0, -r), center + Vector2(r * 0.83, -r * 0.65), center + Vector2(r * 0.75, r * 0.12)])
	_quad_to(shield, center + Vector2(r * 0.58, r * 0.70), center + Vector2(0, r))
	_quad_to(shield, center + Vector2(-r * 0.58, r * 0.70), center + Vector2(-r * 0.75, r * 0.12))
	shield.append(center + Vector2(-r * 0.83, -r * 0.65))
	var shadow := shield.duplicate()
	for i in shadow.size():
		shadow[i] += Vector2(0, cell * 0.025)
	_fill(shadow, argb(0x50314952))
	_linear(shield, center - Vector2(r, r), center + Vector2(r, r), [c(0x47979D), c(Palette.SAFE)])
	var outline := shield.duplicate()
	outline.append(shield[0])
	draw_polyline(outline, c(Palette.STONE_LIGHT), cell * 0.025, true)
	var check := PackedVector2Array([center + Vector2(-r * 0.36, 0), center + Vector2(-r * 0.06, r * 0.30), center + Vector2(r * 0.40, -r * 0.28)])
	draw_polyline(check, Color.WHITE, cell * 0.045, true)
	for p in [check[0], check[2]]:
		draw_circle(p, cell * 0.0225, Color.WHITE, true, -1, true)

func _center_depth(cell: float) -> void:
	var a := _axis(6, cell)
	var b := _axis(9, cell)
	var rect := PackedVector2Array([Vector2(a, a), Vector2(b, a), Vector2(b, b), Vector2(a, b)])
	_linear(rect, Vector2(a, a), Vector2(b, b), [argb(0x20FFFFFF), argb(0x05FFFFFF), argb(0x14000000)], [0.0, 0.48, 1.0])
	_grain_jobs.append([rect, 0])
	var w := 1.3 * density
	var inset := 1.0 * density
	draw_line(Vector2(a + inset, a + inset), Vector2(b - inset, a + inset), argb(0x8EFFFFFF), w, true)
	draw_line(Vector2(a + inset, a + inset), Vector2(a + inset, b - inset), argb(0x8EFFFFFF), w, true)

# ---------------------------------------------------------------- grain

func _draw_grain_layer() -> void:
	var textures := grain_textures()
	for job in _grain_jobs:
		var points: PackedVector2Array = job[0]
		var uvs := PackedVector2Array()
		for p in points:
			uvs.append(p / 128.0)
		_grain_layer.draw_colored_polygon(points, Color.WHITE, uvs, textures[job[1]])

## [board grain, stone grain]: 128px tiles from the live createGrainBitmap /
## createStoneGrainBitmap, using the same java.util.Random seeds.
static func grain_textures() -> Array:
	if not _grain_textures.is_empty():
		return _grain_textures
	var board := _Raster.new(128)
	var random := KR.JavaRandom.new(0x4C55444F)
	for k in 620:
		var tone := 0 if random.next_boolean() else 255
		var alpha := 7 + random.next_int(14)
		var x := random.next_float() * 128.0
		var y := random.next_float() * 128.0
		var radius := 0.85 if random.next_int(7) == 0 else 0.38
		board.oval(x, y, radius, radius, Color8(tone, tone, tone, alpha))
	for k in 48:
		var tone := 0 if random.next_boolean() else 255
		var alpha := 6 + random.next_int(10)
		var width := 0.45 + random.next_float() * 0.45
		var x := random.next_float() * 128.0
		var y := random.next_float() * 128.0
		var length := 2.0 + random.next_float() * 6.0
		var drift := (random.next_float() - 0.5) * 1.4
		board.segment(Vector2(x, y), Vector2(x + length, y + drift), width, Color8(tone, tone, tone, alpha))
	var stone := _Raster.new(128)
	random = KR.JavaRandom.new(0x53544F4E)
	for k in 1100:
		var tone := 255 if random.next_boolean() else 38
		var color := Color8(tone, tone, tone, 6 + random.next_int(15))
		var x := random.next_float() * 128.0
		var y := random.next_float() * 128.0
		var radius := 0.16 + random.next_float() * 0.58
		stone.oval(x, y, radius, radius * 0.6, color)
	for index in 16:
		var color := argb(0x0DFFFFFF if index % 2 == 0 else 0x0A3D3830)
		var x := random.next_float() * 128.0
		var y := random.next_float() * 128.0
		var vein := PackedVector2Array([Vector2(x, y)])
		_quad_to(vein, Vector2(x + 3, y - 1.8), Vector2(x + 7, y - 0.8), 6)
		_quad_to(vein, Vector2(x + 9, y + 0.3), Vector2(x + 12, y - 1.7), 6)
		for s in vein.size() - 1:
			stone.segment(vein[s], vein[s + 1], 0.45, color)
	_grain_textures = [board.texture(), stone.texture()]
	return _grain_textures

## Tiny anti-aliased software rasterizer (4x4 supersampling) for the grain tiles.
class _Raster:
	var size: int
	var image: Image

	func _init(pixels: int) -> void:
		size = pixels
		image = Image.create(pixels, pixels, false, Image.FORMAT_RGBA8)
		image.fill(Color(0, 0, 0, 0))

	func _blend(px: int, py: int, color: Color, coverage: float) -> void:
		if coverage <= 0.0:
			return
		px = posmod(px, size)
		py = posmod(py, size)
		var src := Color(color.r, color.g, color.b, color.a * coverage)
		var dst := image.get_pixel(px, py)
		var out_a := src.a + dst.a * (1.0 - src.a)
		if out_a <= 0.0:
			return
		var rgb := (Vector3(src.r, src.g, src.b) * src.a + Vector3(dst.r, dst.g, dst.b) * dst.a * (1.0 - src.a)) / out_a
		image.set_pixel(px, py, Color(rgb.x, rgb.y, rgb.z, out_a))

	func oval(cx: float, cy: float, rx: float, ry: float, color: Color) -> void:
		for py in range(floori(cy - ry), ceili(cy + ry) + 1):
			for px in range(floori(cx - rx), ceili(cx + rx) + 1):
				var hits := 0
				for sy in 4:
					for sx in 4:
						var dx := (px + (sx + 0.5) / 4.0 - cx) / rx
						var dy := (py + (sy + 0.5) / 4.0 - cy) / ry
						if dx * dx + dy * dy <= 1.0:
							hits += 1
				_blend(px, py, color, hits / 16.0)

	func segment(a: Vector2, b: Vector2, width: float, color: Color) -> void:
		var half := width * 0.5
		var lo := Vector2(minf(a.x, b.x), minf(a.y, b.y)) - Vector2.ONE * half
		var hi := Vector2(maxf(a.x, b.x), maxf(a.y, b.y)) + Vector2.ONE * half
		for py in range(floori(lo.y), ceili(hi.y) + 1):
			for px in range(floori(lo.x), ceili(hi.x) + 1):
				var hits := 0
				for sy in 4:
					for sx in 4:
						var p := Vector2(px + (sx + 0.5) / 4.0, py + (sy + 0.5) / 4.0)
						if Geometry2D.get_closest_point_to_segment(p, a, b).distance_to(p) <= half:
							hits += 1
				_blend(px, py, color, hits / 16.0)

	func texture() -> ImageTexture:
		return ImageTexture.create_from_image(image)
