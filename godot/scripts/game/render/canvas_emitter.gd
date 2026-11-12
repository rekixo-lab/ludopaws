extends RefCounted
## A tiny Android-Canvas look-alike that records draw commands in exactly the format of the
## JVM recorder (tests/fixtures/boards_gen/AndroidGraphicsRecorder.kt). Live 2D renderers are
## ported against it line by line, proven by comparing command lists with frames recorded from
## the unchanged live code, and drawn by board_art_player.gd.

var commands: Array = []

class APaint:
	var style := "FILL"
	var color := 0xFF000000
	var stroke_width := 0.0
	var cap := "BUTT"
	var join := "MITER"
	var shader = null   # ["linear", ...] / ["radial", ...] (recorder layout)
	var shadow = null   # ["shadow", radius, dx, dy, argb]

	func _init(fields := {}) -> void:
		for k in fields:
			set(k, fields[k])

	func set_alpha(a: int) -> void:
		color = (color & 0xFFFFFF) | ((clampi(a, 0, 255) & 255) << 24)

	func tail() -> Array:
		var out := [style, color & 0xFFFFFFFF, stroke_width, cap, join, shader]
		if shadow != null:
			out.append(shadow)
		return out

class APath:
	var verbs: Array = []
	func move_to(x: float, y: float) -> APath:
		verbs.append(["M", x, y])
		return self
	func line_to(x: float, y: float) -> APath:
		verbs.append(["L", x, y])
		return self
	func quad_to(a: float, b: float, c: float, d: float) -> APath:
		verbs.append(["Q", a, b, c, d])
		return self
	func cubic_to(a: float, b: float, c: float, d: float, e: float, f: float) -> APath:
		verbs.append(["C", a, b, c, d, e, f])
		return self
	func close() -> APath:
		verbs.append(["Z"])
		return self
	func rewind() -> void:
		verbs = []
	func add_round_rect(l: float, t: float, r: float, b: float, rx: float, ry: float) -> APath:
		verbs.append(["RRECT", l, t, r, b, rx, ry])
		return self

static func linear(x0: float, y0: float, x1: float, y1: float, colors: Array, stops = null) -> Array:
	return ["linear", x0, y0, x1, y1, colors.map(func(c): return int(c) & 0xFFFFFFFF), stops, "CLAMP"]

static func radial(x: float, y: float, r: float, colors: Array, stops = null) -> Array:
	return ["radial", x, y, r, colors.map(func(c): return int(c) & 0xFFFFFFFF), stops, "CLAMP"]

func save() -> int:
	commands.append(["save"])
	return 0
func restore() -> void:
	commands.append(["restore"])
func translate(x: float, y: float) -> void:
	commands.append(["translate", x, y])
func scale(x: float, y: float) -> void:
	commands.append(["scale", x, y])
func rotate(deg: float, px := 0.0, py := 0.0) -> void:
	commands.append(["rotate", deg, px, py])
func concat(m: PackedFloat64Array) -> void:
	commands.append(["concat"] + Array(m))
func clip_path(p: APath) -> void:
	commands.append(["clipPath", p.verbs.duplicate(true)])
func draw_rect(l: float, t: float, r: float, b: float, p: APaint) -> void:
	commands.append(["rect", l, t, r, b] + p.tail())
func draw_round_rect(l: float, t: float, r: float, b: float, rx: float, ry: float, p: APaint) -> void:
	commands.append(["rrect", l, t, r, b, rx, ry] + p.tail())
func draw_oval(l: float, t: float, r: float, b: float, p: APaint) -> void:
	commands.append(["oval", l, t, r, b] + p.tail())
func draw_circle(x: float, y: float, r: float, p: APaint) -> void:
	commands.append(["circle", x, y, r] + p.tail())
func draw_line(a: float, b: float, c: float, d: float, p: APaint) -> void:
	commands.append(["line", a, b, c, d] + p.tail())
func draw_arc(l: float, t: float, r: float, b: float, start: float, sweep: float, use_center: bool, p: APaint) -> void:
	commands.append(["arc", l, t, r, b, start, sweep, use_center] + p.tail())
func draw_path(path: APath, p: APaint) -> void:
	commands.append(["path", path.verbs.duplicate(true), "WINDING"] + p.tail())
func draw_bitmap(key: String, l: float, t: float, r: float, b: float, alpha := 255) -> void:
	commands.append(["bitmap", key, l, t, r, b, alpha])

## Android Matrix.setPolyToPoly for 4 points: the projective map src quad -> dst quad,
## row-major [sx, kx, tx, ky, sy, ty, p0, p1, p2] (null when degenerate).
static func poly_to_poly(src: Array, dst: Array) -> Variant:
	var a = _square_to_quad(dst)
	var b = _square_to_quad(src)
	if a == null or b == null:
		return null
	var inv = _inverse(b)
	if inv == null:
		return null
	var m := _mul(a, inv)
	if absf(m[8]) < 1e-12:
		return null
	var n := PackedFloat64Array()
	for v in m:
		n.append(v / m[8])
	return n

static func _square_to_quad(q: Array) -> Variant:
	var x0: float = q[0]; var y0: float = q[1]; var x1: float = q[2]; var y1: float = q[3]
	var x2: float = q[4]; var y2: float = q[5]; var x3: float = q[6]; var y3: float = q[7]
	var dx1 := x1 - x2; var dx2 := x3 - x2; var sx := x0 - x1 + x2 - x3
	var dy1 := y1 - y2; var dy2 := y3 - y2; var sy := y0 - y1 + y2 - y3
	var den := dx1 * dy2 - dx2 * dy1
	if absf(den) < 1e-12:
		return null
	var g := (sx * dy2 - dx2 * sy) / den
	var h := (dx1 * sy - sx * dy1) / den
	return PackedFloat64Array([x1 - x0 + g * x1, x3 - x0 + h * x3, x0, y1 - y0 + g * y1, y3 - y0 + h * y3, y0, g, h, 1.0])

static func _mul(a: PackedFloat64Array, b: PackedFloat64Array) -> PackedFloat64Array:
	var o := PackedFloat64Array()
	o.resize(9)
	for r in 3:
		for c in 3:
			o[r * 3 + c] = a[r * 3] * b[c] + a[r * 3 + 1] * b[3 + c] + a[r * 3 + 2] * b[6 + c]
	return o

static func _inverse(m: PackedFloat64Array) -> Variant:
	var det := m[0] * (m[4] * m[8] - m[5] * m[7]) - m[1] * (m[3] * m[8] - m[5] * m[6]) + m[2] * (m[3] * m[7] - m[4] * m[6])
	if absf(det) < 1e-12:
		return null
	var i := 1.0 / det
	return PackedFloat64Array([
		(m[4] * m[8] - m[5] * m[7]) * i, (m[2] * m[7] - m[1] * m[8]) * i, (m[1] * m[5] - m[2] * m[4]) * i,
		(m[5] * m[6] - m[3] * m[8]) * i, (m[0] * m[8] - m[2] * m[6]) * i, (m[2] * m[3] - m[0] * m[5]) * i,
		(m[3] * m[7] - m[4] * m[6]) * i, (m[1] * m[6] - m[0] * m[7]) * i, (m[0] * m[4] - m[1] * m[3]) * i])
