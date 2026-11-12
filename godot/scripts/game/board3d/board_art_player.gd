@tool
extends Control
## Replays a live board renderer's recorded Canvas commands (assets/boards/board_art.json,
## recorded from the unchanged live code by tests/fixtures/boards_gen). The live renderers
## draw in a 150-unit space; this Control maps that onto `edge` pixels.
## Supports the Canvas subset those renderers use: save/restore, translate/scale/rotate,
## rect clips, rect / round-rect / oval / circle / arc / line / path, fill + stroke, and
## linear / radial gradient shaders (exact per-vertex UVs into gradient textures).
## Drawing order is kept by splitting the output into ordered leaf nodes, so clip groups
## nest correctly between draws.

const ART_PATH := "res://assets/boards/board_art.json"
const CURVE_STEPS := 10
const ROUND_STEPS := 6

static var _art := {}

var board_id := "":
	set(value):
		board_id = value
		_rebuild()
var edge := 600.0:
	set(value):
		edge = value
		_rebuild()

static func art() -> Dictionary:
	if _art.is_empty():
		_art = JSON.parse_string(FileAccess.get_file_as_string(ART_PATH))
	return _art

static func has_board(id: String) -> bool:
	return art().has(id)

func _rebuild() -> void:
	for child in get_children():
		remove_child(child)
		child.queue_free()
	if not is_inside_tree() and get_parent() == null and board_id.is_empty():
		return
	if not has_board(board_id):
		return
	var data: Dictionary = art()[board_id]
	build(self, data["commands"], edge / 150.0)

func _ready() -> void:
	_rebuild()

# ---------------------------------------------------------------- replay

## Textures for "bitmap" commands, by recorded key (e.g. live drawable name).
static var textures := {}

class Leaf extends Control:
	var ops: Array = []   # [kind, ...] in canvas pixels (already transformed)
	func _draw() -> void:
		var probe_t := Time.get_ticks_usec()
		_draw_ops()
		preload("res://scripts/game/perf.gd").span("leaf_draw", Time.get_ticks_usec() - probe_t)

	func _draw_ops() -> void:
		for op in ops:
			match op[0]:
				"poly":
					if op[3] == null:
						draw_colored_polygon(op[1], op[2])
					else:
						draw_polygon(op[1], PackedColorArray([op[2]]), op[4], op[3])
				"strip":
					draw_polyline(op[1], op[2], op[3], true)

class Mask extends Control:
	var outline := PackedVector2Array()
	func _draw() -> void:
		if outline.size() >= 3:
			draw_colored_polygon(outline, Color.WHITE)

## 3x3 row-major matrix helpers (Android Canvas matrices, incl. perspective from concat).
static func _mul(a: PackedFloat64Array, b: PackedFloat64Array) -> PackedFloat64Array:
	var o := PackedFloat64Array()
	o.resize(9)
	for r in 3:
		for c in 3:
			o[r * 3 + c] = a[r * 3] * b[c] + a[r * 3 + 1] * b[3 + c] + a[r * 3 + 2] * b[6 + c]
	return o

static func _m(a: float, b: float, c: float, d: float, e: float, f: float) -> PackedFloat64Array:
	return PackedFloat64Array([a, b, c, d, e, f, 0.0, 0.0, 1.0])

static func apply(m: PackedFloat64Array, p: Vector2) -> Vector2:
	var w := m[6] * p.x + m[7] * p.y + m[8]
	if absf(w) < 0.000001:
		w = 0.000001
	return Vector2((m[0] * p.x + m[1] * p.y + m[2]) / w, (m[3] * p.x + m[4] * p.y + m[5]) / w)

## Builds ordered leaf / clip nodes under `root` for `commands` scaled by `k`.
static func build(root: Control, commands: Array, k: float) -> void:
	var state := {"m": _m(k, 0, 0, 0, k, 0), "stack": [], "parent": root, "origin": Vector2.ZERO, "leaf": null}
	for c in commands:
		match c[0]:
			"save":
				state["stack"].append({"m": state["m"], "parent": state["parent"], "origin": state["origin"]})
			"restore":
				var top: Dictionary = state["stack"].pop_back()
				state["m"] = top["m"]
				if top["parent"] != state["parent"]:
					state["parent"] = top["parent"]
					state["origin"] = top["origin"]
					state["leaf"] = null
			"translate":
				state["m"] = _mul(state["m"], _m(1, 0, c[1], 0, 1, c[2]))
			"scale":
				state["m"] = _mul(state["m"], _m(c[1], 0, 0, 0, c[2], 0))
			"rotate":
				var a := deg_to_rad(c[1])
				var px: float = c[2]
				var py: float = c[3]
				var r := _mul(_mul(_m(1, 0, px, 0, 1, py), _m(cos(a), -sin(a), 0, sin(a), cos(a), 0)), _m(1, 0, -px, 0, 1, -py))
				state["m"] = _mul(state["m"], r)
			"concat":
				state["m"] = _mul(state["m"], PackedFloat64Array(c.slice(1, 10)))
			"clip":
				_clip(state, Rect2(Vector2(c[1], c[2]), Vector2(c[3] - c[1], c[4] - c[2])))
			"clipPath":
				_clip_path(state, _path(c[1])[0])
			"color":
				_fill(state, PackedVector2Array([Vector2(0, 0), Vector2(150, 0), Vector2(150, 150), Vector2(0, 150)]), c[1], null)
			"bitmap":
				_bitmap(state, c)
			_:
				_shape(state, c)

## Rect clip: a clip_contents container in drawing order (bounds of the transformed rect).
static func _clip(state: Dictionary, rect: Rect2) -> void:
	var box := Rect2(apply(state["m"], rect.position), Vector2.ZERO)
	for corner in [Vector2(rect.end.x, rect.position.y), rect.end, Vector2(rect.position.x, rect.end.y)]:
		box = box.expand(apply(state["m"], corner))
	var clip := Control.new()
	clip.clip_contents = true
	clip.mouse_filter = Control.MOUSE_FILTER_IGNORE
	clip.position = box.position - state["origin"]
	clip.size = box.size
	state["parent"].add_child(clip)
	state["parent"] = clip
	state["origin"] = box.position
	state["leaf"] = null

## Path clip: children are drawn only inside the mask outline (clip_children).
static func _clip_path(state: Dictionary, outlines: Array) -> void:
	var mask := Mask.new()
	mask.mouse_filter = Control.MOUSE_FILTER_IGNORE
	mask.clip_children = CanvasItem.CLIP_CHILDREN_ONLY
	mask.size = Vector2(4096, 4096)
	if not outlines.is_empty():
		mask.outline = _to_canvas(state, outlines[0])
	state["parent"].add_child(mask)
	state["parent"] = mask
	state["leaf"] = null

static func _leaf(state: Dictionary) -> Leaf:
	if state["leaf"] == null:
		var leaf := Leaf.new()
		leaf.mouse_filter = Control.MOUSE_FILTER_IGNORE
		leaf.size = Vector2(4096, 4096)
		state["parent"].add_child(leaf)
		state["leaf"] = leaf
	return state["leaf"]

static func _to_canvas(state: Dictionary, points: PackedVector2Array) -> PackedVector2Array:
	var out := PackedVector2Array()
	var origin: Vector2 = state["origin"]
	for p in points:
		out.append(apply(state["m"], p) - origin)
	return out

## Length scale of the current matrix (stroke widths).
static func _scale(state: Dictionary) -> float:
	var m: PackedFloat64Array = state["m"]
	return sqrt(absf(m[0] * m[4] - m[1] * m[3]))

static func _bitmap(state: Dictionary, c: Array) -> void:
	# ["bitmap", key, l, t, r, b, alpha]
	var tex = textures.get(c[1])
	if tex == null:
		return
	var local := _rect(c[2], c[3], c[4], c[5])
	var color := Color(1, 1, 1, float(c[6]) / 255.0)
	_leaf(state).ops.append(["poly", _to_canvas(state, local), color, tex,
		PackedVector2Array([Vector2(0, 0), Vector2(1, 0), Vector2(1, 1), Vector2(0, 1)])])

## Paint tail: [style, argb, strokeWidth, cap, join, shader(, shadow)].
static func _paint(c: Array) -> Array:
	# Shadow layers are recorded as a 7th paint field when present.
	if c.size() >= 7 and c[c.size() - 1] is Array and c[c.size() - 1].size() == 5 and c[c.size() - 1][0] == "shadow":
		return c.slice(c.size() - 7)
	return c.slice(c.size() - 6) + [null]

static func _shape(state: Dictionary, c: Array) -> void:
	var paint := _paint(c)
	var style: String = paint[0]
	var outlines: Array = []   # Array[PackedVector2Array]
	var closed := true
	match c[0]:
		"rect":
			outlines = [_rect(c[1], c[2], c[3], c[4])]
		"rrect":
			outlines = [_rrect(c[1], c[2], c[3], c[4], c[5], c[6])]
		"oval":
			outlines = [_ellipse(Vector2((c[1] + c[3]) * 0.5, (c[2] + c[4]) * 0.5), Vector2((c[3] - c[1]) * 0.5, (c[4] - c[2]) * 0.5), 0.0, 360.0, 40)]
		"circle":
			outlines = [_ellipse(Vector2(c[1], c[2]), Vector2(c[3], c[3]), 0.0, 360.0, 40)]
		"line":
			outlines = [PackedVector2Array([Vector2(c[1], c[2]), Vector2(c[3], c[4])])]
			closed = false
			style = "STROKE"
		"arc":
			# ["arc", l, t, r, b, start, sweep, useCenter, paint...]
			var center := Vector2((c[1] + c[3]) * 0.5, (c[2] + c[4]) * 0.5)
			var arc := _ellipse(center, Vector2((c[3] - c[1]) * 0.5, (c[4] - c[2]) * 0.5), c[5], c[6], 32)
			if c[7]:
				arc.insert(0, center)
			else:
				closed = false
			outlines = [arc]
		"path":
			var parsed := _path(c[1])
			outlines = parsed[0]
			closed = parsed[1]
		_:
			return
	var shadow = paint[6]
	if shadow != null:
		# setShadowLayer(radius, dx, dy, color): offset copy behind the shape (blur approximated
		# by a slightly grown, softened second pass).
		var dx: float = shadow[2]
		var dy: float = shadow[3]
		var sc := _color(int(shadow[4]))
		for o in outlines:
			if o.size() >= 3 and style != "STROKE":
				var moved := PackedVector2Array()
				for p in o:
					moved.append(p + Vector2(dx, dy))
				var soft := sc
				soft.a *= 0.55
				_fill_color(state, moved, soft)
	if style == "FILL" or style == "FILL_AND_STROKE":
		for o in outlines:
			if o.size() >= 3:
				_fill(state, o, paint[1], paint[5])
	if style == "STROKE" or style == "FILL_AND_STROKE":
		for o in outlines:
			_stroke(state, o, closed and c[0] != "line", paint)

static func _fill_color(state: Dictionary, points: PackedVector2Array, color: Color) -> void:
	_leaf(state).ops.append(["poly", _to_canvas(state, points), color, null, PackedVector2Array()])

static func _fill(state: Dictionary, points: PackedVector2Array, argb, shader) -> void:
	var color := _color(int(argb))
	var tex = null
	var uvs := PackedVector2Array()
	if shader != null:
		var g := _gradient(shader, color.a)
		tex = g[0]
		for p in points:
			uvs.append(g[1].call(p))
		color = Color.WHITE
	_leaf(state).ops.append(["poly", _to_canvas(state, points), color, tex, uvs])

static func _stroke(state: Dictionary, points: PackedVector2Array, closed: bool, paint: Array) -> void:
	if points.size() < 2:
		return
	var width: float = maxf(float(paint[2]), 0.0)
	var hairline := width == 0.0
	var line := points.duplicate()
	if closed:
		line.append(points[0])
	var color := _color(int(paint[1]))
	if paint[5] == null:
		var px := 1.0 if hairline else width * _scale(state)
		_leaf(state).ops.append(["strip", _to_canvas(state, line), color, px])
		if paint[3] == "ROUND" and not hairline:
			for p in [line[0], line[line.size() - 1]]:
				_fill_color(state, _ellipse(p, Vector2.ONE * width * 0.5, 0.0, 360.0, 12), color)
		return
	if hairline:
		width = 1.0 / maxf(_scale(state), 0.0001)
	# Gradient strokes: one quad per segment with exact gradient UVs.
	for i in line.size() - 1:
		var a: Vector2 = line[i]
		var b: Vector2 = line[i + 1]
		var d := (b - a)
		if d.length() < 0.0001:
			continue
		var nrm := d.orthogonal().normalized() * width * 0.5
		var ext := d.normalized() * (width * 0.5 if paint[3] != "BUTT" else 0.0)
		_fill(state, PackedVector2Array([a - ext + nrm, b + ext + nrm, b + ext - nrm, a - ext - nrm]), paint[1], paint[5])

## [texture, uv(point) callable] for a recorded gradient.
## Gradient textures by colours + stops + alpha (the UV mapping is per shape; the texture is
## not), so a re-drawn dice or board reuses them instead of baking new ones.
static var _gradient_textures := {}

static func _gradient(shader: Array, alpha: float) -> Array:
	var colors: Array = shader[5 if shader[0] == "linear" else 4]
	var stops = shader[6 if shader[0] == "linear" else 5]
	var tex_key := [shader[0], colors, stops, alpha]
	var cached = _gradient_textures.get(tex_key)
	if shader[0] == "linear":
		var p0 := Vector2(shader[1], shader[2])
		var d := Vector2(shader[3], shader[4]) - p0
		var len2 := maxf(d.length_squared(), 0.000001)
		var uv := func(p: Vector2) -> Vector2: return Vector2(clampf((p - p0).dot(d) / len2, 0.0, 1.0), 0.5)
		if cached != null:
			return [cached, uv]
	else:
		var center := Vector2(shader[1], shader[2])
		var radius: float = maxf(shader[3], 0.0001)
		if cached != null:
			return [cached, func(p: Vector2) -> Vector2: return (p - center) / (2.0 * radius) + Vector2(0.5, 0.5)]
	var made := _gradient_build(shader, colors, stops, alpha)
	_gradient_textures[tex_key] = made[0]
	return made

static func _gradient_build(shader: Array, colors: Array, stops, alpha: float) -> Array:
	var g := Gradient.new()
	var offsets := PackedFloat32Array()
	var cols := PackedColorArray()
	for i in colors.size():
		offsets.append(float(stops[i]) if stops != null else float(i) / maxf(colors.size() - 1, 1))
		var col := _color(int(colors[i]))
		col.a *= alpha
		cols.append(col)
	g.offsets = offsets
	g.colors = cols
	if shader[0] == "linear":
		var p0 := Vector2(shader[1], shader[2])
		var d := Vector2(shader[3], shader[4]) - p0
		var len2 := maxf(d.length_squared(), 0.000001)
		var tex := GradientTexture1D.new()
		tex.gradient = g
		tex.width = 256
		return [tex, func(p: Vector2) -> Vector2: return Vector2(clampf((p - p0).dot(d) / len2, 0.0, 1.0), 0.5)]
	var center := Vector2(shader[1], shader[2])
	var radius: float = maxf(shader[3], 0.0001)
	var tex2 := GradientTexture2D.new()
	tex2.gradient = g
	tex2.fill = GradientTexture2D.FILL_RADIAL
	tex2.fill_from = Vector2(0.5, 0.5)
	tex2.fill_to = Vector2(1.0, 0.5)
	tex2.width = 128
	tex2.height = 128
	return [tex2, func(p: Vector2) -> Vector2: return (p - center) / (2.0 * radius) + Vector2(0.5, 0.5)]

static func _color(argb: int) -> Color:
	return Color8((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, (argb >> 24) & 255)

static func _rect(l: float, t: float, r: float, b: float) -> PackedVector2Array:
	return PackedVector2Array([Vector2(l, t), Vector2(r, t), Vector2(r, b), Vector2(l, b)])

static func _rrect(l: float, t: float, r: float, b: float, rx: float, ry: float) -> PackedVector2Array:
	rx = minf(rx, (r - l) * 0.5)
	ry = minf(ry, (b - t) * 0.5)
	if rx <= 0.0 or ry <= 0.0:
		return _rect(l, t, r, b)
	var out := PackedVector2Array()
	for corner in [[r - rx, t + ry, -90.0], [r - rx, b - ry, 0.0], [l + rx, b - ry, 90.0], [l + rx, t + ry, 180.0]]:
		for s in ROUND_STEPS + 1:
			var a := deg_to_rad(corner[2] + 90.0 * s / ROUND_STEPS)
			out.append(Vector2(corner[0] + cos(a) * rx, corner[1] + sin(a) * ry))
	return out

static func _ellipse(c: Vector2, r: Vector2, start_deg: float, sweep_deg: float, steps: int) -> PackedVector2Array:
	var out := PackedVector2Array()
	var full := absf(sweep_deg) >= 360.0
	var count := steps if full else maxi(int(steps * absf(sweep_deg) / 360.0), 2)
	for i in (count if full else count + 1):
		var a := deg_to_rad(start_deg + sweep_deg * i / count)
		out.append(c + Vector2(cos(a) * r.x, sin(a) * r.y))
	return out

## Flattens path verbs into subpath outlines; returns [outlines, closed].
static func _path(verbs: Array) -> Array:
	var outlines: Array = []
	var current := PackedVector2Array()
	var cursor := Vector2.ZERO
	var start := Vector2.ZERO
	var closed := false
	for v in verbs:
		match v[0]:
			"M":
				if current.size() > 0:
					outlines.append(current)
				current = PackedVector2Array([Vector2(v[1], v[2])])
				cursor = Vector2(v[1], v[2])
				start = cursor
			"L":
				cursor = Vector2(v[1], v[2])
				current.append(cursor)
			"Q", "q":
				var rel: bool = v[0] == "q"
				var c1 := Vector2(v[1], v[2]) + (cursor if rel else Vector2.ZERO)
				var end := Vector2(v[3], v[4]) + (cursor if rel else Vector2.ZERO)
				for s in range(1, CURVE_STEPS + 1):
					var t := float(s) / CURVE_STEPS
					current.append(cursor.lerp(c1, t).lerp(c1.lerp(end, t), t))
				cursor = end
			"C":
				var c1 := Vector2(v[1], v[2])
				var c2 := Vector2(v[3], v[4])
				var end := Vector2(v[5], v[6])
				for s in range(1, CURVE_STEPS + 1):
					var t := float(s) / CURVE_STEPS
					var u := 1.0 - t
					current.append(cursor * u * u * u + c1 * 3.0 * u * u * t + c2 * 3.0 * u * t * t + end * t * t * t)
				cursor = end
			"Z":
				closed = true
				if current.size() > 0:
					outlines.append(current)
				current = PackedVector2Array()
				cursor = start
			"CIRCLE":
				outlines.append(_ellipse(Vector2(v[1], v[2]), Vector2(v[3], v[3]), 0.0, 360.0, 40))
				closed = true
			"OVAL":
				outlines.append(_ellipse(Vector2((v[1] + v[3]) * 0.5, (v[2] + v[4]) * 0.5), Vector2((v[3] - v[1]) * 0.5, (v[4] - v[2]) * 0.5), 0.0, 360.0, 40))
				closed = true
			"RECT":
				outlines.append(_rect(v[1], v[2], v[3], v[4]))
				closed = true
			"RRECT":
				outlines.append(_rrect(v[1], v[2], v[3], v[4], v[5], v[6]))
				closed = true
			"ARC", "ARCTO":
				var arc := _ellipse(Vector2((v[1] + v[3]) * 0.5, (v[2] + v[4]) * 0.5), Vector2((v[3] - v[1]) * 0.5, (v[4] - v[2]) * 0.5), v[5], v[6], 24)
				current.append_array(arc)
				if arc.size() > 0:
					cursor = arc[arc.size() - 1]
	if current.size() > 0:
		outlines.append(current)
	return [outlines, closed]
