@tool
extends RefCounted
## Raised stone road relief and yard palms for the classic board.
## Port of feature/boards/content/classic/ClassicRoadScene.kt (ClassicRoadProfile + artwork).
## Works in the live 150-unit board space; Board3D scales it to the board.

const G = preload("res://scripts/game/board3d/paw_geometry.gd")
const D = preload("res://scripts/game/board3d/paw_draw.gd")
const Palette = preload("res://scripts/game/board3d/board_palette.gd")

const GAP := 0.026
const DEPTH := 0.045
const CORNER := 0.28
const BEVEL := 0.028

static func boundary(index: int) -> float:
	if index <= 6:
		return index * 50.0 / 6.0
	if index <= 9:
		return 50.0 + (index - 6) * 50.0 / 3.0
	return 100.0 + (index - 9) * 50.0 / 6.0

## RectF-style [left, top, right, bottom]
static func raw(row: int, column: int) -> Rect2:
	var l := boundary(column)
	var t := boundary(row)
	return Rect2(l, t, boundary(column + 1) - l, boundary(row + 1) - t)

static func face(r: Rect2) -> Rect2:
	var unit := minf(r.size.x, r.size.y)
	var gap := unit * GAP
	return Rect2(r.position.x + gap, r.position.y + gap,
		r.size.x - gap * 2.0, r.size.y - gap * 2.0 - unit * DEPTH)

static func is_road(row: int, column: int) -> bool:
	return (column >= 6 and column <= 8 and (row < 6 or row > 8)) or \
		(row >= 6 and row <= 8 and (column < 6 or column > 8))

## Team colour for coloured lane/start cells, or -1.
static func team(row: int, column: int) -> int:
	if (row == 7 and column >= 1 and column <= 5) or (row == 6 and column == 1):
		return Palette.CORAL
	if (column == 7 and row >= 1 and row <= 5) or (row == 1 and column == 8):
		return Palette.JADE
	if (row == 7 and column >= 9 and column <= 13) or (row == 8 and column == 13):
		return Palette.HONEY
	if (column == 7 and row >= 9 and row <= 13) or (row == 13 and column == 6):
		return Palette.SKY
	return -1

static func _outline(bounds: Rect2, radius: float, height: float) -> Array:
	var points: Array = []
	var corner := minf(radius, minf(bounds.size.x, bounds.size.y) * 0.5)
	for quadrant in 4:
		var cx := bounds.end.x - corner if (quadrant == 0 or quadrant == 3) else bounds.position.x + corner
		var cy := bounds.end.y - corner if quadrant < 2 else bounds.position.y + corner
		for step in 5:
			var angle := deg_to_rad(quadrant * 90.0 + step * 22.5)
			points.append(Vector3(cx + cos(angle) * corner, cy + sin(angle) * corner, height))
	return points

static var _artwork: Array = []

## Array of paw_draw Batch.
static func artwork() -> Array:
	if not _artwork.is_empty():
		return _artwork
	var builders := {}
	var safe_faces := G.Builder.new()
	var symbols := G.Builder.new()
	var dark_foliage := G.Builder.new()
	var light_foliage := G.Builder.new()
	var trunks := G.Builder.new()
	for row in 15:
		for column in 15:
			if not is_road(row, column):
				continue
			var r := raw(row, column)
			var unit := minf(r.size.x, r.size.y)
			var f := face(r)
			var bevel := unit * BEVEL
			var depth := unit * DEPTH
			var color := team(row, column)
			if color < 0:
				color = Palette.CLASSIC_NEUTRAL_CELL
			if not builders.has(color):
				builders[color] = G.Builder.new()
			var builder: G.Builder = builders[color]
			var bottom := Rect2(f.position + Vector2(0, depth), f.size)
			var top := f.grow(-bevel)
			var lower_ring := _outline(bottom, unit * CORNER, 0.025)
			var bevel_ring := _outline(f, unit * CORNER, depth * 0.65)
			var upper_ring := _outline(top, unit * CORNER - bevel, depth)
			var top_center := Vector3(top.get_center().x, top.get_center().y, depth)
			for index in upper_ring.size():
				var next := (index + 1) % upper_ring.size()
				builder.triangle(top_center, upper_ring[index], upper_ring[next])
				builder.quad(upper_ring[index], upper_ring[next], bevel_ring[next], bevel_ring[index])
				builder.quad(bevel_ring[index], bevel_ring[next], lower_ring[next], lower_ring[index])
			var safe := (row == 2 and column == 6) or (row == 6 and column == 12) or \
				(row == 12 and column == 8) or (row == 8 and column == 2)
			var entry := (row == 6 and column == 1) or (row == 1 and column == 8) or \
				(row == 8 and column == 13) or (row == 13 and column == 6)
			var cx := r.get_center().x
			var cy := r.get_center().y
			var height := depth + 0.035
			if safe:
				var shield := [Vector3(cx, cy - 2.6, height), Vector3(cx + 2.0, cy - 1.7, height),
					Vector3(cx + 1.7, cy + 0.8, height), Vector3(cx, cy + 2.5, height),
					Vector3(cx - 1.7, cy + 0.8, height), Vector3(cx - 2.0, cy - 1.7, height)]
				for index in shield.size():
					safe_faces.triangle(Vector3(cx, cy, height), shield[index], shield[(index + 1) % shield.size()])
				_segment(symbols, Vector2(cx - 0.9, cy), Vector2(cx - 0.2, cy + 0.7), height + 0.03)
				_segment(symbols, Vector2(cx - 0.2, cy + 0.7), Vector2(cx + 1.1, cy - 0.8), height + 0.03)
			elif entry:
				var points: Array = []
				for index in 10:
					var angle := deg_to_rad(index * 36.0 - 90.0)
					var radius := 2.4 if index % 2 == 0 else 1.05
					points.append(Vector3(cx + cos(angle) * radius, cy + sin(angle) * radius, height))
				for index in points.size():
					symbols.triangle(Vector3(cx, cy, height), points[index], points[(index + 1) % points.size()])
	for origin_x in [0.0, 100.0]:
		for origin_y in [0.0, 100.0]:
			for tree in 2:
				var cx: float = origin_x + (5.8 if tree == 0 else 44.5)
				var cy: float = origin_y + (6.0 if tree == 0 else 44.0)
				var span := 4.8 if tree == 0 else 4.2
				for frond in 8:
					var angle := frond * PI / 4.0 + tree * 0.25
					var mx := cx + cos(angle) * span * 0.55
					var my := cy + sin(angle) * span * 0.55
					var tip := Vector3(cx + cos(angle) * span, cy + sin(angle) * span, 1.3)
					var ridge := Vector3(mx, my, 3.4)
					var root := Vector3(cx, cy, 2.8)
					var edge_left := Vector3(mx - sin(angle) * 0.85, my + cos(angle) * 0.85, 2.1)
					var edge_right := Vector3(mx + sin(angle) * 0.85, my - cos(angle) * 0.85, 2.1)
					light_foliage.triangle(root, ridge, edge_left)
					light_foliage.triangle(ridge, tip, edge_left)
					dark_foliage.triangle(root, edge_right, ridge)
					dark_foliage.triangle(ridge, edge_right, tip)
				for side in 6:
					var a := side * PI / 3.0
					var b := (side + 1) * PI / 3.0
					trunks.quad(Vector3(cx + cos(a) * 0.4, cy + 2.5 + sin(a) * 0.4, 0.0),
						Vector3(cx + cos(b) * 0.4, cy + 2.5 + sin(b) * 0.4, 0.0),
						Vector3(cx + cos(b) * 0.25, cy + sin(b) * 0.25, 2.8),
						Vector3(cx + cos(a) * 0.25, cy + sin(a) * 0.25, 2.8))
	var out: Array = []
	for color in builders:
		out.append(_batch(builders[color].build(), G.rgb(color), "GARDEN_STONE"))
	out.append(_batch(safe_faces.build(), G.rgb(Palette.SAFE), "MATTE"))
	out.append(_batch(symbols.build(), G.rgb(Palette.STONE_LIGHT), "MATTE"))
	out.append(_batch(trunks.build(), G.rgb(0x766B43), "MATTE"))
	out.append(_batch(dark_foliage.build(), G.rgb(0x315E35), "GARDEN_LEAF"))
	out.append(_batch(light_foliage.build(), G.rgb(0x588446), "GARDEN_LEAF"))
	_artwork = out
	return out

static func _segment(b: G.Builder, start: Vector2, end: Vector2, z: float) -> void:
	var delta := end - start
	var offset := Vector2(-delta.y, delta.x) / delta.length() * 0.22
	b.quad(Vector3(start.x + offset.x, start.y + offset.y, z), Vector3(end.x + offset.x, end.y + offset.y, z),
		Vector3(end.x - offset.x, end.y - offset.y, z), Vector3(start.x - offset.x, start.y - offset.y, z))

static func _batch(geo: G.Geo, color: Color, surface: String) -> D.Batch:
	var b := D.Batch.new()
	b.geo = geo
	b.color = color
	b.surface = surface
	return b

## Transform used by ClassicRoadScene.draw (size = board edge in pixel units).
static func transform(size: float, turns: int) -> Transform3D:
	var m := G.translate(Transform3D.IDENTITY, size * 0.5, size * 0.5, 0)
	m = G.rotate(m, turns * 90.0, 0, 0, 1)
	m = G.scale(m, size / 150.0, size / 150.0, size / 150.0)
	return G.translate(m, -75, -75, 0)
