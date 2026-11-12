@tool
extends RefCounted
## Four storybook houses, garden courtyard, doors and finish plaques in the board centre.
## Port of game/ui/components/LudoPawsCenterHouses3D.kt plus the home-entry and finish
## presentation policies. Static architecture is baked once; doors/plaques are per frame.

const G = preload("res://scripts/game/board3d/paw_geometry.gd")
const D = preload("res://scripts/game/board3d/paw_draw.gd")
const Palette = preload("res://scripts/game/board3d/board_palette.gd")

const CENTER_HOUSE_SCALE := 0.50
const CENTER_DOOR_OUTWARD := CENTER_HOUSE_SCALE

# LudoPawsHomeEntryPolicy (milliseconds)
const CELEBRATE_MS := 1800
const OPEN_MS := 550
const WALK_START_MS := CELEBRATE_MS + OPEN_MS
const STAGGER_MS := 500
const WALK_MS := 1050
const CLOSE_START_MS := WALK_START_MS + 3 * STAGGER_MS + WALK_MS
const PLAQUE_START_MS := CLOSE_START_MS + 600
const ENTRY_DURATION_MS := PLAQUE_START_MS + 1400
const FORWARD_LANDING_SETTLE_MS := 90

const HOUSE_COLORS := ["BLUE", "RED", "GREEN", "YELLOW"]
const CORNER_BLOOMS := [
	[0.708, 0.795, 0.060, 0.82, false],
	[0.718, 0.902, 0.052, 0.66, true],
	[0.806, 0.705, 0.072, 0.94, false],
	[0.900, 0.715, 0.056, 0.67, true],
	[0.735, 0.735, 0.048, 0.55, false],
]

static var CREAM := G.rgb(0xFFE5AC)
static var GOLD := G.rgb(0xF5C34C)
static var SILVER := G.rgb(0xDFE9F4)
static var BRONZE := G.rgb(0xDEA16B)
static var PARTY_COLORS := [G.rgb(0xFFE784), G.rgb(0x7BEAF5), G.rgb(0xFF9ECC), G.rgb(0xFFF2C0)]
static var WOOD := G.rgb(0xAE662D)
static var WOOD_GRAIN := G.rgb(0x713C20)
static var INTERIOR := G.rgb(0x342A25)
static var LEAF := G.rgb(0x329D43)
static var STONE := G.rgb(0xE6DDC9)
static var GRASS := G.rgb(0x55B541)
static var GRASS_TIPS := G.rgb(0xADDA63)
static var TURF_EDGE := G.rgb(0x3D8236)
static var EARTH := G.rgb(0x79614A)
static var CONTACT_SHADE := G.rgb(0x3D5844)
static var LEAF_BASE := G.rgb(0x4BA248)
static var LEAF_DARK := G.rgb(0x226B35)
static var LEAF_LIGHT := G.rgb(0x7DC94E)
static var PATH_STONE_LIGHT := G.rgb(0xF1E6CF)
static var TERRACE_STONE := G.rgb(0xE6D5A9)
static var FENCE_WOOD := G.rgb(0xC08D43)
static var FENCE_GRAIN := G.rgb(0x80512A)
static var FENCE_PINS := G.rgb(0xE5BF71)
static var FLOWER_CREAM := G.rgb(0xF7EEDB)
static var FLOWER_PINK := G.rgb(0xEF859A)

var roofs: Array = []
var roof_shades: Array = []
var roof_edges: Array = []
var roof_base: Array = []

var light_pool: G.Geo
var shell: G.Geo
var arch: G.Geo
var entry_jamb: G.Geo
var cube: G.Geo
var terrain: G.Geo
var turf_edge: G.Geo
var stone_block: G.Geo
var house_apron: G.Geo
var grass_blades: G.Geo
var roof_body: G.Geo
var tiled_roof: Array = []
var ridge_cap: G.Geo
var fence_meshes: Array = []
var lettering := {}
var _static_batches: Array = []

const LAMPS_COUNT := 14

func _init() -> void:
	for hex in [Palette.SKY, Palette.CORAL, Palette.JADE, Palette.HONEY]:
		var base := G.rgb(hex)
		roofs.append(base)
		var shades: Array = []
		for shade in 4:
			shades.append(G.tint(base, 0.91 + shade * 0.055))
		roof_shades.append(shades)
		roof_edges.append(G.tint(base, 0.68))
		roof_base.append(G.tint(base, 0.66))
	_build_meshes()

static func v(x: float, y: float, z := 0.0) -> Vector3:
	return Vector3(x, y, z)

static func roof_apex() -> Vector3:
	return v(0, 0, 0.70)

static func roof_eave(side: int) -> Vector3:
	return v(side * 0.90, 0.90, 0.135)

static func roof_front() -> Vector3:
	return v(0, 0.54, 0.549)

static func garden_height(x: float, y: float) -> float:
	var ax := absf(x)
	var ay := absf(y)
	if ax < 0.69 or ay < 0.69:
		return -0.025
	var dx := ax - 0.825
	var dy := ay - 0.825
	var edge_fade := clampf((0.96 - maxf(ax, ay)) / 0.06, 0.0, 1.0)
	return -0.025 + exp(-(dx * dx + dy * dy) / 0.012) * 0.085 * edge_fade

static func _arch_outline() -> Array:
	var outline := [v(-1, 0), v(-1, -0.65)]
	for step in 17:
		var angle := PI - step * PI / 16.0
		outline.append(v(cos(angle), -0.65 - sin(angle) * 0.35))
	outline.append(v(1, 0))
	return outline

func _build_meshes() -> void:
	var b := G.Builder.new()
	b.quad(v(-1, -1), v(1, -1), v(1, 1), v(-1, 1))
	light_pool = b.build()

	b = G.Builder.new()
	b.prism([v(-0.90, -0.25), v(0, -1.15), v(0.90, -0.25), v(0.86, 0), v(-0.86, 0)], -0.055, 0.0)
	shell = b.build()

	b = G.Builder.new()
	b.prism(_arch_outline(), -0.5, 0.5)
	arch = b.build()

	b = G.Builder.new()
	var outline := _arch_outline()
	for index in outline.size():
		var next := (index + 1) % outline.size()
		var o0: Vector3 = outline[index]
		var o1: Vector3 = outline[next]
		b.quad(v(o0.x * 0.195, o0.y * 0.76 - 0.025, 0.041), v(o1.x * 0.195, o1.y * 0.76 - 0.025, 0.041),
			v(o1.x * 0.163, o1.y * 0.69 - 0.025, 0.055), v(o0.x * 0.163, o0.y * 0.69 - 0.025, 0.055))
	entry_jamb = b.build()

	b = G.Builder.new()
	b.prism([v(-1, -1), v(1, -1), v(1, 1), v(-1, 1)], -1.0, 1.0)
	cube = b.build()

	b = G.Builder.new()
	var subdivisions := 32
	for row in subdivisions:
		for col in subdivisions:
			b.quad(_terrain_point(row, col, subdivisions), _terrain_point(row, col + 1, subdivisions),
				_terrain_point(row + 1, col + 1, subdivisions), _terrain_point(row + 1, col, subdivisions))
	terrain = b.build()

	b = G.Builder.new()
	var corners := [v(-1, -1), v(1, -1), v(1, 1), v(-1, 1)]
	for index in 4:
		var a: Vector3 = corners[index]
		var c: Vector3 = corners[(index + 1) % 4]
		b.quad(v(a.x * 0.96, a.y * 0.96, -0.025), v(c.x * 0.96, c.y * 0.96, -0.025),
			v(c.x * 0.98, c.y * 0.98, -0.075), v(a.x * 0.98, a.y * 0.98, -0.075))
	turf_edge = b.build()

	b = G.Builder.new()
	var stone_outline := [v(-0.78, -1), v(0.78, -1), v(1, -0.78), v(1, 0.78), v(0.78, 1), v(-0.78, 1), v(-1, 0.78), v(-1, -0.78)]
	for index in stone_outline.size():
		var a: Vector3 = stone_outline[index]
		var c: Vector3 = stone_outline[(index + 1) % stone_outline.size()]
		var top_a := v(a.x * 0.87, a.y * 0.87, 1)
		var top_c := v(c.x * 0.87, c.y * 0.87, 1)
		b.triangle(v(0, 0, 1), top_a, top_c)
		b.quad(top_a, top_c, v(c.x, c.y, 0.62), v(a.x, a.y, 0.62))
		b.quad(v(a.x, a.y, 0.62), v(a.x, a.y, -0.7), v(c.x, c.y, -0.7), v(c.x, c.y, 0.62))
		b.quad(v(a.x, a.y, -0.7), v(c.x, c.y, -0.7), v(c.x * 0.92, c.y * 0.92, -1), v(a.x * 0.92, a.y * 0.92, -1))
	stone_block = b.build()

	b = G.Builder.new()
	var inner := CENTER_HOUSE_SCALE - 0.04
	var outer := CENTER_HOUSE_SCALE + 0.065
	b.quad(v(-inner, inner, -0.021), v(inner, inner, -0.021), v(outer, outer, -0.021), v(-outer, outer, -0.021))
	house_apron = b.build()

	b = G.Builder.new()
	for row in 5:
		for col in 5:
			var x := 0.735 + col * 0.045
			var y := 0.735 + row * 0.045
			var base := v(x, y, garden_height(x, y) + 0.003)
			for blade in 3:
				var angle := deg_to_rad(row * 17 + col * 31 + blade * 113)
				var spread := v(cos(angle) * 0.020, sin(angle) * 0.020)
				var width := v(-sin(angle) * 0.004, cos(angle) * 0.004)
				var height := 0.045 + ((row + col + blade) % 3) * 0.012
				var middle := base + spread * 0.45 + v(0, -0.011, height * 0.65)
				var tip := base + spread + v(0, -0.025, height)
				b.quad(base - width, base + width, middle + width * 0.6, middle - width * 0.6)
				b.triangle(middle - width * 0.6, middle + width * 0.6, tip)
	grass_blades = b.build()

	b = G.Builder.new()
	for side in [-1, 1]:
		var a := roof_apex()
		var e := roof_eave(side)
		var c := roof_front()
		b.triangle(a, e, c)
		var down := v(0, 0, -0.035)
		b.quad(a, a + down, e + down, e)
		b.quad(e, e + down, c + down, c)
		b.quad(c, c + down, a + down, a)
	roof_body = b.build()

	tiled_roof = _roof_tiles()
	ridge_cap = _raised_ridge()
	fence_meshes = _garden_fence()
	for text in ["WINNER", "2ND", "3RD", "4TH", "HOME"]:
		lettering[text] = _lettering(text)

func _terrain_point(row: int, col: int, subdivisions: int) -> Vector3:
	var x := -0.96 + col * 1.92 / subdivisions
	var y := -0.96 + row * 1.92 / subdivisions
	return v(x, y, garden_height(x, y))

func _roof_tiles() -> Array:
	var batches: Array = []
	for k in 4:
		batches.append(G.Builder.new())
	for side in [-1, 1]:
		var apex := roof_apex()
		var eave := roof_eave(side)
		var ridge := roof_front()
		for row in 6:
			var t0 := row / 6.0
			var t1 := (row + 1) / 6.0
			var columns := row + 1
			for column in columns:
				var u0 := float(column) / columns
				var u1 := (column + 1.0) / columns
				var points := [_roof_point(apex, eave, ridge, t0, u0), _roof_point(apex, eave, ridge, t1, u0),
					_roof_point(apex, eave, ridge, t1, u1), _roof_point(apex, eave, ridge, t0, u1)]
				var center: Vector3 = (points[0] + points[1] + points[2] + points[3]) * 0.25
				var lower: Array = []
				for p in points:
					lower.append(center + (p - center) * 0.93 + v(0, 0, 0.003))
				var upper: Array = []
				for index in 4:
					upper.append(center + (lower[index] - center) * 0.88 + v(0, 0, 0.024 if (index == 1 or index == 2) else 0.012))
				var shade := (row * 3 + column + (1 if side < 0 else 0)) % 4
				var batch: G.Builder = batches[shade]
				batch.triangle(upper[0], upper[1], upper[2])
				if row > 0:
					batch.triangle(upper[0], upper[2], upper[3])
				for edge in 4:
					var next := (edge + 1) % 4
					batches[0 if edge == 1 else shade].quad(lower[edge], lower[next], upper[next], upper[edge])
	var out: Array = []
	for builder in batches:
		out.append(builder.build())
	return out

static func _roof_point(apex: Vector3, eave: Vector3, ridge: Vector3, t: float, u: float) -> Vector3:
	return apex + ((eave - apex) * (1.0 - u) + (ridge - apex) * u) * t

func _raised_ridge() -> G.Geo:
	var b := G.Builder.new()
	var apex := roof_apex()
	var front := roof_front()
	for row in 8:
		for segment in 8:
			b.quad(_ridge_point(apex, front, row, segment), _ridge_point(apex, front, row + 1, segment),
				_ridge_point(apex, front, row + 1, segment + 1), _ridge_point(apex, front, row, segment + 1))
		for edge in [0, 8]:
			var a := _ridge_point(apex, front, row, edge)
			var c := _ridge_point(apex, front, row + 1, edge)
			var down := v(0, 0, -0.045)
			b.quad(a, c, c + down, a + down)
	for segment in 8:
		b.triangle(front + v(0, 0, -0.025), _ridge_point(apex, front, 8, segment + 1), _ridge_point(apex, front, 8, segment))
	return b.build()

static func _ridge_point(apex: Vector3, front: Vector3, row: int, segment: int) -> Vector3:
	var t := row / 8.0
	var center := apex + (front - apex) * t
	var angle := segment * PI / 8.0
	var radius := 0.0 if row == 0 else 0.025 - t * 0.004
	return center + v(cos(angle) * radius, 0, 0.020 + sin(angle) * radius)

func _garden_fence() -> Array:
	var body := G.Builder.new()
	var grain := G.Builder.new()
	var pins := G.Builder.new()
	for side in [-1, 1]:
		for x in [0.23, 0.57, 0.91]:
			_timber(body, grain, pins, v(side * x, 0.971, -0.030), v(side * x, 0.916, 0.105), 0.016, 0.011, true)
		for height in [0.023, 0.078]:
			var inward_y: float = 0.971 - (height + 0.030) * (0.055 / 0.135)
			_timber(body, grain, pins, v(side * 0.23, inward_y, height), v(side * 0.968, inward_y, height), 0.009, 0.011, false)
	return [body.build(), grain.build(), pins.build()]

func _timber(body: G.Builder, grain: G.Builder, pins: G.Builder, a: Vector3, b: Vector3, width: float, depth: float, post: bool) -> void:
	var delta := b - a
	var direction := delta / delta.length()
	var across := v(1, 0) if post else v(0, 1)
	var front := direction.cross(across)
	front = front * ((-1.0 if front.z < 0.0 else 1.0) / front.length())
	var profile := [Vector2(-0.72, -1), Vector2(0.72, -1), Vector2(1, -0.72), Vector2(1, 0.72),
		Vector2(0.72, 1), Vector2(-0.72, 1), Vector2(-1, 0.72), Vector2(-1, -0.72)]
	for index in profile.size():
		var p: Vector2 = profile[index]
		var q: Vector2 = profile[(index + 1) % profile.size()]
		var ap := a + across * (p.x * width) + front * (p.y * depth)
		var aq := a + across * (q.x * width) + front * (q.y * depth)
		var bp := b + across * (p.x * width) + front * (p.y * depth)
		var bq := b + across * (q.x * width) + front * (q.y * depth)
		body.quad(ap, bp, bq, aq)
		body.triangle(a, aq, ap)
		body.triangle(b, bp, bq)
	for line in range(-1, 2):
		var offset := across * (line * width * 0.40) + front * (depth + 0.0007)
		var start := a + delta * (0.10 + (line + 1) * 0.035) + offset
		var finish := a + delta * (0.86 - (line + 1) * 0.025) + offset
		var thin := across * 0.0008
		grain.quad(start - thin, start + thin, finish + thin, finish - thin)
	if post:
		for t in [0.38, 0.78]:
			var center: Vector3 = a + delta * t + front * (depth + 0.0015)
			var horizontal := across * 0.0032
			var vertical := direction * 0.0038
			pins.quad(center - horizontal - vertical, center + horizontal - vertical,
				center + horizontal + vertical, center - horizontal + vertical)

const GLYPHS := {
	"W": ["10001", "10001", "10001", "10101", "10101", "11011", "10001"],
	"I": ["11111", "00100", "00100", "00100", "00100", "00100", "11111"],
	"N": ["10001", "11001", "11001", "10101", "10011", "10011", "10001"],
	"E": ["11111", "10000", "10000", "11110", "10000", "10000", "11111"],
	"R": ["11110", "10001", "10001", "11110", "10100", "10010", "10001"],
	"2": ["01110", "10001", "00001", "00010", "00100", "01000", "11111"],
	"3": ["11110", "00001", "00001", "01110", "00001", "00001", "11110"],
	"4": ["10010", "10010", "10010", "11111", "00010", "00010", "00010"],
	"D": ["11110", "10001", "10001", "10001", "10001", "10001", "11110"],
	"T": ["11111", "00100", "00100", "00100", "00100", "00100", "00100"],
	"H": ["10001", "10001", "10001", "11111", "10001", "10001", "10001"],
	"O": ["01110", "10001", "10001", "10001", "10001", "10001", "01110"],
	"M": ["10001", "11011", "10101", "10101", "10001", "10001", "10001"],
}

func _lettering(text: String) -> G.Geo:
	var pixel := 0.033
	var width := (text.length() * 6 - 1) * pixel
	var b := G.Builder.new()
	for letter in text.length():
		var rows: Array = GLYPHS[text[letter]]
		for row in rows.size():
			var line: String = rows[row]
			for column in line.length():
				if line[column] == "1":
					var x := (letter * 6 + column) * pixel - width * 0.5
					var y := (row - 3.5) * pixel
					b.prism([v(x, y), v(x + pixel, y), v(x + pixel, y + pixel), v(x, y + pixel)], 0.0, 0.032)
	return b.build()

# ---------------------------------------------------------------- static artwork

## Batches baked at size 6 / no rotation, exactly like the live staticArtwork.
## LudoPawsCenterHouses3D.stoneRoadArtwork: bevelled stone slabs (the entrance-step solid)
## on every neutral road cell of non-classic boards, with a readable shield + check on the
## four neutral safe cells. Board units (15 x 15); coloured lanes and start cells stay Canvas.
func stone_road_artwork() -> Array:
	var rec := D.Recorder.new()
	var tones := [Palette.color(Palette.STONE), Palette.color(Palette.STONE_LIGHT), Palette.color(Palette.STONE_SHADE)]
	var joint := Palette.color(Palette.JOINT)
	var shield_color := Palette.color(Palette.SAFE)
	var check := Palette.color(Palette.TEXT)
	var safe := [Vector2i(2, 6), Vector2i(6, 12), Vector2i(12, 8), Vector2i(8, 2)]
	var shield_b := G.Builder.new()
	shield_b.prism([v(0, -0.26), v(0.22, -0.17), v(0.20, 0.03), v(0.12, 0.18), v(0, 0.27), v(-0.12, 0.18),
		v(-0.20, 0.03), v(-0.22, -0.17)], -0.008, 0.008)
	var shield := shield_b.build()
	var boundary := func(i: int) -> float:
		if i <= 6: return i * 5.0 / 6.0
		if i <= 9: return 5.0 + (i - 6) * 5.0 / 3.0
		return 10.0 + (i - 9) * 5.0 / 6.0
	for row in 15:
		for col in 15:
			var vertical := col >= 6 and col <= 8 and (row < 6 or row >= 9)
			var horizontal := row >= 6 and row <= 8 and (col < 6 or col >= 9)
			if not vertical and not horizontal:
				continue
			if (col == 7 and row >= 1 and row <= 5) or (row == 7 and col >= 1 and col <= 5) \
					or (col == 7 and row >= 9 and row <= 13) or (row == 7 and col >= 9 and col <= 13) \
					or (row == 6 and col == 1) or (row == 1 and col == 8) or (row == 8 and col == 13) or (row == 13 and col == 6):
				continue
			var left: float = boundary.call(col)
			var top: float = boundary.call(row)
			var width: float = boundary.call(col + 1) - left
			var height: float = boundary.call(row + 1) - top
			var unit := minf(width, height)
			var cx := left + width * 0.5
			var cy := top + height * 0.5
			var gap := unit * 0.065
			var sx := width * 0.5 - gap
			var sy := height * 0.5 - gap
			var depth := unit * (0.032 + ((row * 7 + col * 3) % 3) * 0.003)
			rec.surface = "MATTE"
			rec.draw_geometry(Transform3D.IDENTITY, stone_block, v(cx + unit * 0.012, cy + unit * 0.018, 0.004),
				v(sx + unit * 0.006, sy + unit * 0.006, 0.003), joint)
			rec.surface = "GARDEN_STONE"
			rec.draw_geometry(Transform3D.IDENTITY, stone_block, v(cx, cy - unit * 0.012, depth + 0.009), v(sx, sy, depth),
				tones[(row * 7 + col * 11) % tones.size()])
			if Vector2i(row, col) in safe:
				var z := depth * 2.0 + 0.023
				rec.surface = "MATTE"
				rec.draw_geometry(Transform3D.IDENTITY, shield, v(cx, cy, z), v(unit, unit, unit), shield_color)
				_beam(rec, Transform3D.IDENTITY, v(cx - unit * 0.10, cy, z + 0.014), v(cx - unit * 0.025, cy + unit * 0.075, z + 0.014), unit * 0.020, check)
				_beam(rec, Transform3D.IDENTITY, v(cx - unit * 0.025, cy + unit * 0.075, z + 0.014), v(cx + unit * 0.12, cy - unit * 0.085, z + 0.014), unit * 0.020, check)
	return rec.batches()

## drawStoneRoads transform: board centre, perspective turn, board units -> size.
static func stone_road_transform(size: float, turns: int) -> Transform3D:
	var m := G.translate(Transform3D.IDENTITY, size * 0.5, size * 0.5, 0)
	m = G.rotate(m, turns * 90.0, 0, 0, 1)
	m = G.scale(m, size / 15.0, size / 15.0, size / 15.0)
	return G.translate(m, -7.5, -7.5, 0)

func static_artwork() -> Array:
	if not _static_batches.is_empty():
		return _static_batches
	var rec := D.Recorder.new()
	var size := 6.0
	var half_span := size / 6.0
	_draw_garden(rec, size, half_span, 0)
	var artwork_half := half_span * CENTER_HOUSE_SCALE
	for index in 4:
		var house := _house_transform(size, 0, index, artwork_half)
		var facade := _facade(house)
		rec.surface = "HOUSE_WALL"
		rec.draw_geometry(facade, shell, Vector3.ZERO, Vector3.ONE, CREAM)
		rec.surface = "HOUSE_ROOF"
		rec.draw_geometry(house, roof_body, Vector3.ZERO, Vector3.ONE, roof_base[index])
		for shade in tiled_roof.size():
			rec.draw_geometry(house, tiled_roof[shade], Vector3.ZERO, Vector3.ONE, roof_shades[index][shade])
		_beam(rec, house, roof_eave(-1), roof_front(), 0.018, roof_edges[index])
		_beam(rec, house, roof_eave(1), roof_front(), 0.018, roof_edges[index])
		rec.draw_geometry(house, ridge_cap, Vector3.ZERO, Vector3.ONE, roof_shades[index][3])
		rec.surface = "HOUSE_WALL"
		rec.draw_geometry(facade, arch, v(0, -0.025, 0.016), v(0.195, 0.76, 0.025), GOLD)
		rec.draw_geometry(facade, arch, v(0, -0.025, 0.033), v(0.163, 0.69, 0.018), INTERIOR)
		rec.draw_geometry(facade, cube, v(0, -0.008, 0.075), v(0.25, 0.025, 0.045), STONE)
	_static_batches = rec.batches()
	return _static_batches

## Where the baked artwork goes for a board of `size` and perspective `turns`.
static func static_transform(size: float, turns: int) -> Transform3D:
	var half_span := size / 6.0
	var m := G.translate(Transform3D.IDENTITY, size * 0.5, size * 0.5, 0)
	m = G.rotate(m, turns * 90.0, 0, 0, 1)
	m = G.scale(m, half_span, half_span, half_span)
	return G.translate(m, -3, -3, 0)

static func _house_transform(size: float, turns: int, index: int, artwork_half: float) -> Transform3D:
	var m := G.translate(Transform3D.IDENTITY, size * 0.5, size * 0.5, 0)
	m = G.rotate(m, turns * 90.0 + index * 90.0, 0, 0, 1)
	return G.scale(m, artwork_half, artwork_half, artwork_half)

static func _facade(house: Transform3D) -> Transform3D:
	var m := G.translate(house, 0, 1, 0.02)
	m = G.rotate(m, -48.990913, 1, 0, 0)
	return G.scale(m, 1, 0.60959, 1)

func _draw_garden(c, size: float, half_span: float, turns: int) -> void:
	for side in 4:
		var garden := G.translate(Transform3D.IDENTITY, size * 0.5, size * 0.5, 0)
		garden = G.rotate(garden, turns * 90.0 + side * 90.0, 0, 0, 1)
		garden = G.scale(garden, half_span, half_span, half_span)
		if side == 0:
			c.surface = "GARDEN_GROUND"
			c.draw_geometry(garden, cube, v(0, 0, -0.13), v(0.995, 0.995, 0.04), EARTH)
			c.draw_geometry(garden, turf_edge, Vector3.ZERO, Vector3.ONE, TURF_EDGE)
			c.draw_geometry(garden, terrain, Vector3.ZERO, Vector3.ONE, GRASS)
		c.surface = "GARDEN_STONE"
		c.draw_geometry(garden, house_apron, Vector3.ZERO, Vector3.ONE, TERRACE_STONE)
		for edge in [-1, 1]:
			for block in 3:
				c.draw_geometry(garden, stone_block, v(edge * (0.20 + block * 0.10), CENTER_HOUSE_SCALE + 0.065, -0.006),
					v(0.067, 0.022, 0.012), STONE if block % 2 == 0 else TERRACE_STONE)
		for step in 3:
			var y := CENTER_DOOR_OUTWARD + 0.042 + step * 0.078
			var step_width := 0.105 + step * 0.008
			c.surface = "GARDEN_GROUND"
			c.draw_part(garden, v(0.009, y + 0.014, -0.020), v(step_width + 0.009, 0.041, 0.003), CONTACT_SHADE)
			c.surface = "GARDEN_STONE"
			c.draw_geometry(garden, stone_block, v(0, y, 0.010 - step * 0.007), v(step_width, 0.031, 0.018),
				PATH_STONE_LIGHT if step == 1 else STONE)
		c.surface = "GARDEN_GROUND"
		c.draw_geometry(garden, grass_blades, Vector3.ZERO, Vector3.ONE, GRASS_TIPS)
		c.surface = "GARDEN_LEAF"
		for index in CORNER_BLOOMS.size():
			var plant: Array = CORNER_BLOOMS[index]
			var ground := garden_height(plant[0], plant[1])
			c.draw_part(garden, v(plant[0], plant[1], ground - 0.006), v(0.065, 0.058, 0.027), LEAF_BASE)
			for leaf in 4:
				var angle := deg_to_rad(index * 37 + leaf * 93)
				var x: float = plant[0] + cos(angle) * 0.034
				var y: float = plant[1] + sin(angle) * 0.028
				c.draw_part(garden, v(x, y, garden_height(x, y) + 0.010), v(0.039, 0.020, 0.013),
					LEAF_BASE if leaf % 2 == 0 else LEAF, v(0, 0, rad_to_deg(angle)))
		for index in CORNER_BLOOMS.size():
			var plant: Array = CORNER_BLOOMS[index]
			var ground := garden_height(plant[0], plant[1])
			var flower_y: float = plant[1] - 0.012 - (index % 2) * 0.009
			var bloom_z: float = ground + plant[2]
			var size_k: float = plant[3]
			_beam(c, garden, v(plant[0], plant[1], ground - 0.004), v(plant[0], flower_y, bloom_z - 0.006), 0.0035, LEAF_DARK)
			c.draw_part(garden, v(plant[0] - 0.014, plant[1] - 0.003, ground + plant[2] * 0.45),
				v(0.026, 0.012, 0.009), LEAF_LIGHT, v(0, 0, -32.0 + index * 11.0))
			c.draw_part(garden, v(plant[0], flower_y, bloom_z - 0.010), v(0.020 * size_k, 0.018 * size_k, 0.012), LEAF)
			for petal in 5:
				var angle := deg_to_rad(petal * 72 + index * 29)
				c.draw_part(garden, v(plant[0] + cos(angle) * 0.015 * size_k, flower_y + sin(angle) * 0.013 * size_k, bloom_z),
					v(0.015 * size_k, 0.013 * size_k, 0.008 * size_k), FLOWER_PINK if plant[4] else FLOWER_CREAM,
					v(sin(angle) * 18.0, cos(angle) * 18.0, rad_to_deg(angle)))
			c.draw_part(garden, v(plant[0], flower_y, bloom_z + 0.007), v(0.008 * size_k, 0.008 * size_k, 0.006 * size_k), GOLD)
		c.surface = "GARDEN_STONE"
		c.draw_geometry(garden, fence_meshes[0], Vector3.ZERO, Vector3.ONE, FENCE_WOOD)
		c.draw_geometry(garden, fence_meshes[1], Vector3.ZERO, Vector3.ONE, FENCE_GRAIN)
		c.draw_geometry(garden, fence_meshes[2], Vector3.ZERO, Vector3.ONE, FENCE_PINS)

func _beam(c, parent: Transform3D, a: Vector3, b: Vector3, radius: float, color: Color) -> void:
	var d := b - a
	var length := d.length()
	var m := G.translate(parent, (a.x + b.x) * 0.5, (a.y + b.y) * 0.5, (a.z + b.z) * 0.5)
	var angle := rad_to_deg(acos(clampf(d.y / length, -1.0, 1.0)))
	if angle > 0.001:
		if d.x == 0.0 and d.z == 0.0:
			m = G.rotate(m, angle, 1, 0, 0)
		else:
			m = G.rotate(m, angle, d.z, 0, -d.x)
	c.draw_geometry(m, cube, Vector3.ZERO, v(radius, length * 0.5, radius), color)

# ---------------------------------------------------------------- per frame

## scene: {players: [{playerId, color, tokens}], status, ranks: {playerId: rank},
##         home_entries: {playerId: {started_ms, rank}}, forward: {playerId, to, started_ms, duration_ms}}
func draw_dynamic(pool: D.Pool, size: float, turns: int, scene: Dictionary, now_ms: int) -> void:
	var artwork_half := size / 6.0 * CENTER_HOUSE_SCALE
	for index in 4:
		var house := _house_transform(size, turns, index, artwork_half)
		var facade := _facade(house)
		pool.surface = "HOUSE_WALL"
		var hinge := G.translate(facade, -0.151, -0.025, 0.057)
		hinge = G.rotate(hinge, -82.0 * _door_openness(index, scene, now_ms), 0, 1, 0)
		pool.draw_geometry(hinge, arch, v(0.151, 0, 0), v(0.151, 0.675, 0.024), WOOD)
		for plank in range(1, 5):
			pool.draw_geometry(hinge, cube, v(plank * 0.0604, -0.23, 0.027), v(0.0035, 0.22, 0.002), WOOD_GRAIN)
		pool.draw_part(hinge, v(0.244, -0.30, 0.041), v(0.022, 0.028, 0.025), GOLD)

		var player := _player_for(scene, HOUSE_COLORS[index])
		if player.is_empty():
			continue
		var entries: Dictionary = scene.get("home_entries", {})
		if entries.has(player["playerId"]):
			var entry: Dictionary = entries[player["playerId"]]
			var elapsed := _elapsed(entry, now_ms)
			var reveal := _smooth((elapsed - PLAQUE_START_MS) / 500.0)
			var age := maxi(elapsed - PLAQUE_START_MS, 0) if int(entry.get("started_ms", 0)) > 0 else 3000
			if reveal > 0.0:
				_draw_finish_plaque(pool, house, index, int(entry["rank"]), reveal, now_ms, age)
		elif scene.get("status") == "FINISHED":
			var rank := int(scene.get("ranks", {}).get(player["playerId"], 0))
			if rank == 4 or (rank == 2 and scene.get("players", []).size() == 2):
				_draw_finish_plaque(pool, house, index, rank, 1.0, now_ms, 3000)
	pool.party_energy = 0.0

static func _player_for(scene: Dictionary, color: String) -> Dictionary:
	for p in scene.get("players", []):
		if p.get("color") == color:
			return p
	return {}

static func _elapsed(entry: Dictionary, now_ms: int) -> int:
	var started := int(entry.get("started_ms", 0))
	return ENTRY_DURATION_MS if started == 0 else now_ms - started

static func entry_walk(elapsed: int, index: int) -> float:
	return clampf(float(elapsed - WALK_START_MS - index * STAGGER_MS) / WALK_MS, 0.0, 1.0)

static func _door_for_entry(elapsed: int) -> float:
	return _smooth(float(elapsed - CELEBRATE_MS) / OPEN_MS) * (1.0 - _smooth(float(elapsed - CLOSE_START_MS) / 600.0))

func _door_openness(index: int, scene: Dictionary, now_ms: int) -> float:
	var player := _player_for(scene, HOUSE_COLORS[index])
	var entries: Dictionary = scene.get("home_entries", {})
	if not player.is_empty() and entries.has(player["playerId"]):
		return _door_for_entry(_elapsed(entries[player["playerId"]], now_ms))
	var phase := float((now_ms + index * 3100) % 14000)
	var idle := _envelope(phase, 850.0, 1650.0, 2550.0)
	var motion = scene.get("forward")
	if not (motion is Dictionary) or int(motion.get("to", -1)) != 57:
		return idle
	var mover := {}
	for p in scene.get("players", []):
		if p.get("playerId") == motion.get("playerId"):
			mover = p
	if mover.get("color") != HOUSE_COLORS[index]:
		return idle
	var arrival := int(motion["started_ms"]) + int(motion["duration_ms"]) - FORWARD_LANDING_SETTLE_MS
	var since_opening := float(now_ms - (arrival - 650))
	return maxf(idle, _envelope(since_opening, 500.0, 1650.0, 2350.0))

static func _envelope(time: float, open_at: float, close_at: float, end: float) -> float:
	if time < 0.0 or time >= end:
		return 0.0
	if time < open_at:
		return _smooth(time / open_at)
	if time < close_at:
		return 1.0
	return 1.0 - _smooth((time - close_at) / (end - close_at))

static func _smooth(value: float) -> float:
	var t := clampf(value, 0.0, 1.0)
	return t * t * (3.0 - 2.0 * t)

static func rank_label(rank: int) -> String:
	match rank:
		1: return "WINNER"
		2: return "2ND"
		3: return "3RD"
		4: return "4TH"
	return "HOME"

func _draw_finish_plaque(c: D.Pool, parent: Transform3D, color: int, rank: int, reveal: float, now_ms: int, age_ms: int) -> void:
	var metal: Color = GOLD if rank == 1 else (SILVER if rank == 2 else (BRONZE if rank == 3 else roofs[color]))
	var burst := clampf(1.0 - age_ms / 3000.0, 0.0, 1.0)
	c.party_energy = 0.65 + burst * 0.35 if rank == 1 else (0.22 if rank in [2, 3] else 0.08)
	var clock := (now_ms % 100000) / 1000.0
	if rank == 1:
		c.surface = "PARTY_GLOW"
		for side in 2:
			var phase := clock * 0.4 + side * 2
			var segment := int(phase)
			var blend := _smooth(phase - segment)
			var from: Color = PARTY_COLORS[segment % PARTY_COLORS.size()]
			var to: Color = PARTY_COLORS[(segment + 1) % PARTY_COLORS.size()]
			var tint := from.lerp(to, blend)
			tint.a = (0.15 + 0.08 * sin(clock * PI + side)) * reveal
			c.draw_geometry(parent, light_pool, v(-0.40 if side == 0 else 0.40, 1.13, 0.008), v(0.35, 0.16, 1), tint)
	var plaque := G.translate(parent, 0, 1.13, 0.02 + reveal * 0.04)
	var pop := 0.82 + 0.18 * reveal
	plaque = G.scale(plaque, pop, pop, pop)
	c.surface = "GARDEN_STONE"
	c.draw_geometry(plaque, stone_block, v(0, 0.014, -0.018), v(0.74, 0.19, 0.035), CONTACT_SHADE)
	c.surface = "FINISH_METAL"
	c.draw_geometry(plaque, stone_block, v(0, 0, 0.015), v(0.72, 0.18, 0.034), metal)
	c.surface = "HOUSE_WALL"
	c.draw_geometry(plaque, stone_block, v(0, 0, 0.052), v(0.67, 0.137, 0.012), roof_edges[color])
	c.surface = "FINISH_METAL"
	c.draw_geometry(plaque, lettering[rank_label(rank)], v(0, 0, 0.068), Vector3.ONE, metal)
	if rank == 1:
		var lamps: Array = []
		for column in 6:
			lamps.append(Vector2(-0.56 + column * 0.224, -0.153))
		lamps.append(Vector2(0.685, 0))
		for column in 6:
			lamps.append(Vector2(0.56 - column * 0.224, 0.153))
		lamps.append(Vector2(-0.685, 0))
		for index in lamps.size():
			var base: Color = PARTY_COLORS[index % PARTY_COLORS.size()]
			var chase := 0.5 + 0.5 * sin(clock * TAU - index * 0.90)
			var lamp := Color(base.r * (0.48 + chase * 0.52), base.g * (0.48 + chase * 0.52), base.b * (0.48 + chase * 0.52),
				(0.17 + chase * 0.20) * reveal)
			c.surface = "PARTY_GLOW"
			c.draw_geometry(plaque, light_pool, v(lamps[index].x, lamps[index].y, 0.106), v(0.052, 0.052, 1), lamp)
			lamp.a = 1.0
			c.surface = "PARTY_BULB"
			c.draw_part(plaque, v(lamps[index].x, lamps[index].y, 0.094), v(0.022, 0.022, 0.019), lamp)
	else:
		c.surface = "FINISH_METAL"
		for side in [-1, 1]:
			c.draw_part(plaque, v(side * 0.62, 0.095, 0.072), v(0.014, 0.014, 0.012), metal)
	c.party_energy = 0.0
