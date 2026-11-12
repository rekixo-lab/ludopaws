extends RefCounted
## Species footprint pressed into each yard start slot whose pawn has left the yard. Derived
## from the authoritative snapshot (never from an animation), so it is stable after redraws
## and restores; a pawn back in its yard covers its own print.
## Ports LudoPawsYardFootprintPolicy.kt, LudoPaws3DCharacterPolicy.speciesForSeat and
## LudoBoardView.drawYardPawPrints / createPawPrintArtwork / createPawPrintTexturePaint.

const KR = preload("res://scripts/game/board3d/kotlin_random.gd")
const Geo = preload("res://scripts/game/board3d/board_geometry.gd")

const CURVE_STEPS := 12
static var _artwork := {}
static var _texture: ImageTexture

## LudoPaws3DCharacterPolicy.speciesForCharacterId (null = use the colour fallback).
static func species_for_character(character_id) -> Variant:
	match str(character_id) if character_id != null else "":
		"dog": return "DOG"
		"pig", "goat", "goat_lumi", "donkey": return "GOAT"
		"elephant": return "ELEPHANT"
		"horse": return "HORSE"
		"giraffe": return "GIRAFFE"
		"rabbit": return "RABBIT"
		"duck", "cock": return "DUCK"
		"cat": return "CAT"
	return null

static func fallback_species(color: String) -> Variant:
	return {"RED": "DOG", "GREEN": "GOAT", "YELLOW": "DUCK", "BLUE": "CAT"}.get(color)

## LudoPawsYardFootprintPolicy.footprints. players: [{playerId, seat, color, tokens}];
## character_ids_by_seat: canonical character id per seat ("" / missing = fallback).
static func footprints(players: Array, character_ids_by_seat: Array) -> Array:
	var out: Array = []
	for player in players:
		var color := str(player["color"])
		if not Geo.COLORS.has(color):
			continue
		var seat := int(player["seat"])
		var id = character_ids_by_seat[seat] if seat >= 0 and seat < character_ids_by_seat.size() else null
		var species = species_for_character(id)
		if species == null:
			species = fallback_species(color)
		if species == null:
			continue
		var tokens: Array = player["tokens"]
		for i in tokens.size():
			if int(tokens[i]) != -1:
				out.append({"playerId": player["playerId"], "token": i, "color": color, "species": species})
	return out

## Paints every footprint. `base` maps board pixels to canvas space (board rotation included);
## `team_colors` maps colour name -> Color; `dp` = canvas pixels per live dp.
static func draw(canvas: CanvasItem, base: Transform2D, prints: Array, cell: float, turns: int, dp: float, team_colors: Dictionary) -> void:
	var scale := maxf(cell * 0.25, dp * 0.1)
	var fill := Color8(59, 51, 43, 43)
	var detail := Color8(38, 33, 29, 48)
	for p in prints:
		var center = Geo.yard_token_center(p["color"], p["token"], cell, turns)
		if center == null:
			continue
		var art: Dictionary = artwork(p["species"])
		var team: Color = team_colors.get(p["color"], Color8(0x74, 0x81, 0x7F))
		var glow := Color(team.r, team.g, team.b, 42.0 / 255.0)
		var edge := Color(team.r, team.g, team.b, 145.0 / 255.0)
		canvas.draw_set_transform_matrix(base * Transform2D(0.0, Vector2.ONE * scale, 0.0, center))
		for pad in art["pads"]:
			canvas.draw_colored_polygon(pad, fill)
			# Dirt grain clipped to the pad: the 64 px tile spans one local unit and repeats.
			canvas.draw_colored_polygon(pad, Color.WHITE, pad, grain_texture())
			_outline(canvas, pad, glow, dp * 2.5 / scale)
			_outline(canvas, pad, edge, dp * 1.0 / scale)
		for line in art["details"]:
			canvas.draw_polyline(line, detail, dp * 0.8 / scale, true)
	canvas.draw_set_transform_matrix(Transform2D.IDENTITY)

static func _outline(canvas: CanvasItem, pad: PackedVector2Array, color: Color, width: float) -> void:
	var loop := pad.duplicate()
	loop.append(pad[0])
	canvas.draw_polyline(loop, color, width, true)

# ---------------------------------------------------------------- artwork (unit space)

## {pads: [PackedVector2Array closed outlines], details: [PackedVector2Array open strokes]}.
static func artwork(species: String) -> Dictionary:
	if _artwork.is_empty():
		_artwork = _build_artwork()
	return _artwork[species]

static func _build_artwork() -> Dictionary:
	var cat_center := _path([[-0.40, 0.00],
		["c", -0.52, -0.17, -0.36, -0.34, -0.18, -0.28], ["c", -0.07, -0.24, -0.07, -0.13, 0.0, -0.13],
		["c", 0.07, -0.13, 0.07, -0.24, 0.18, -0.28], ["c", 0.36, -0.34, 0.52, -0.17, 0.40, 0.00],
		["c", 0.31, 0.25, 0.18, 0.45, 0.0, 0.47], ["c", -0.18, 0.45, -0.31, 0.25, -0.40, 0.00]])
	var dog_center := _path([[-0.43, 0.02],
		["c", -0.55, -0.16, -0.36, -0.35, -0.16, -0.30], ["c", -0.06, -0.28, -0.04, -0.22, 0.0, -0.22],
		["c", 0.04, -0.22, 0.06, -0.28, 0.16, -0.30], ["c", 0.36, -0.35, 0.55, -0.16, 0.43, 0.02],
		["c", 0.36, 0.30, 0.21, 0.53, 0.0, 0.56], ["c", -0.21, 0.53, -0.36, 0.30, -0.43, 0.02]])
	var duck_web := _path([[-0.58, 0.40],
		["c", -0.75, 0.18, -1.01, -0.12, -0.95, -0.42], ["c", -0.91, -0.65, -0.76, -0.70, -0.66, -0.46],
		["c", -0.57, -0.26, -0.47, -0.08, -0.34, 0.04], ["c", -0.39, -0.22, -0.46, -0.52, -0.31, -0.77],
		["c", -0.19, -0.96, -0.04, -0.86, -0.02, -0.61], ["c", 0.00, -0.40, 0.00, -0.15, 0.03, 0.04],
		["c", 0.20, -0.14, 0.37, -0.39, 0.55, -0.57], ["c", 0.72, -0.75, 0.90, -0.57, 0.80, -0.34],
		["c", 0.70, -0.08, 0.51, 0.22, 0.42, 0.40], ["c", 0.20, 0.62, -0.39, 0.62, -0.58, 0.40]])
	var goat_left := _path([[-0.52, 0.39],
		["c", -0.63, 0.20, -0.48, -0.18, -0.35, -0.43], ["c", -0.28, -0.58, -0.09, -0.56, -0.05, -0.37],
		["c", 0.00, -0.10, -0.02, 0.21, -0.12, 0.42], ["c", -0.23, 0.57, -0.44, 0.55, -0.52, 0.39]])
	var goat_right := PackedVector2Array()
	for q in goat_left:
		goat_right.append(Vector2(-q.x, q.y))
	return {
		"RABBIT": {"pads": [_oval(-0.55, -0.87, -0.08, 0.68), _oval(0.08, -0.87, 0.55, 0.68),
			_oval(-0.25, 0.76, -0.03, 1.10), _oval(0.03, 0.76, 0.25, 1.10)],
			"details": [PackedVector2Array([Vector2(-0.32, -0.62), Vector2(-0.32, -0.30)]),
				PackedVector2Array([Vector2(0.32, -0.62), Vector2(0.32, -0.30)])]},
		"HORSE": {"pads": [_path([[-0.48, 0.62], ["c", -0.92, -0.15, -0.63, -0.82, 0.0, -0.84],
			["c", 0.63, -0.82, 0.92, -0.15, 0.48, 0.62]])],
			"details": [PackedVector2Array([Vector2(-0.29, 0.39), Vector2(0.0, -0.24), Vector2(0.29, 0.39)])]},
		"ELEPHANT": {"pads": [_oval(-0.70, -0.76, 0.70, 0.76)],
			"details": [-1, 0, 1].map(func(n): return _arc(n * 0.36 - 0.13, -0.65, n * 0.36 + 0.13, -0.34, 180.0, 180.0))},
		"CAT": {"pads": [_toe(-0.76, -0.80, -0.45, -0.42, -0.035), _toe(-0.39, -0.91, -0.08, -0.53, -0.015),
			_toe(0.08, -0.91, 0.39, -0.53, 0.018), _toe(0.45, -0.80, 0.76, -0.42, 0.035), cat_center],
			"details": [_path([[-0.18, 0.25], ["q", 0.0, 0.35, 0.18, 0.25]], false)]},
		"DOG": {"pads": [_toe(-0.79, -0.72, -0.44, -0.34, -0.04), _toe(-0.41, -0.89, -0.04, -0.49, -0.018),
			_toe(0.04, -0.89, 0.41, -0.49, 0.02), _toe(0.44, -0.72, 0.79, -0.34, 0.04), dog_center],
			"details": [_path([[-0.21, 0.27], ["q", 0.0, 0.42, 0.21, 0.27]], false)]},
		"DUCK": {"pads": [duck_web], "details": [
			_path([[-0.48, -0.07], ["q", -0.34, 0.17, -0.26, 0.34]], false),
			_path([[-0.02, -0.08], ["q", 0.00, 0.18, 0.00, 0.39]], false),
			_path([[0.40, -0.05], ["q", 0.30, 0.19, 0.22, 0.36]], false)]},
		"GIRAFFE": {"pads": [_oval(-0.42, -0.73, -0.055, 0.67), _oval(0.055, -0.73, 0.42, 0.67)], "details": []},
		"GOAT": {"pads": [goat_left, goat_right],
			"details": [_path([[0.0, -0.34], ["c", -0.04, -0.08, -0.02, 0.22, 0.0, 0.38]], false)]},
	}

## Toe pad: soft tapered tip and fuller pressure pad (createPawPrintArtwork.toePad).
static func _toe(left: float, top: float, right: float, bottom: float, lean: float) -> PackedVector2Array:
	var w := right - left
	var h := bottom - top
	var cx := (left + right) / 2.0 + lean
	return _path([[cx, top],
		["c", cx + w * 0.34, top - h * 0.02, right + w * 0.04, top + h * 0.18, right - w * 0.04, top + h * 0.48],
		["c", right - w * 0.10, bottom - h * 0.10, cx + w * 0.23, bottom + h * 0.035, cx - w * 0.02, bottom],
		["c", left + w * 0.19, bottom + h * 0.01, left - w * 0.04, top + h * 0.63, left + w * 0.05, top + h * 0.28],
		["c", left + w * 0.15, top + h * 0.02, cx - w * 0.29, top - h * 0.025, cx, top]])

## Flattens moveTo + cubic ("c") / quadratic ("q") segments into points.
static func _path(ops: Array, closed := true) -> PackedVector2Array:
	var out := PackedVector2Array()
	var cursor := Vector2(ops[0][0], ops[0][1])
	out.append(cursor)
	for k in range(1, ops.size()):
		var op: Array = ops[k]
		if op[0] == "c":
			var c1 := Vector2(op[1], op[2])
			var c2 := Vector2(op[3], op[4])
			var end := Vector2(op[5], op[6])
			for s in range(1, CURVE_STEPS + 1):
				var t := float(s) / CURVE_STEPS
				var u := 1.0 - t
				out.append(cursor * u * u * u + c1 * 3.0 * u * u * t + c2 * 3.0 * u * t * t + end * t * t * t)
			cursor = end
		else:
			var c := Vector2(op[1], op[2])
			var end := Vector2(op[3], op[4])
			for s in range(1, CURVE_STEPS + 1):
				var t := float(s) / CURVE_STEPS
				var u := 1.0 - t
				out.append(cursor * u * u + c * 2.0 * u * t + end * t * t)
			cursor = end
	if closed and out.size() > 1 and out[0].is_equal_approx(out[out.size() - 1]):
		out.remove_at(out.size() - 1)
	return out

static func _oval(l: float, t: float, r: float, b: float) -> PackedVector2Array:
	var out := PackedVector2Array()
	var c := Vector2((l + r) * 0.5, (t + b) * 0.5)
	for k in 32:
		var a := k * TAU / 32.0
		out.append(c + Vector2(cos(a) * (r - l) * 0.5, sin(a) * (b - t) * 0.5))
	return out

static func _arc(l: float, t: float, r: float, b: float, start_deg: float, sweep_deg: float) -> PackedVector2Array:
	var out := PackedVector2Array()
	var c := Vector2((l + r) * 0.5, (t + b) * 0.5)
	for k in 17:
		var a := deg_to_rad(start_deg + sweep_deg * k / 16.0)
		out.append(c + Vector2(cos(a) * (r - l) * 0.5, sin(a) * (b - t) * 0.5))
	return out

# ---------------------------------------------------------------- grain texture

## createPawPrintTexturePaint: 64 px transparent tile of organic flecks and short scratches,
## java.util.Random(0x5052494E54) so the grain matches the live tile.
static func grain_texture() -> ImageTexture:
	if _texture != null:
		return _texture
	var size := 64
	var image := Image.create(size, size, false, Image.FORMAT_RGBA8)
	image.fill(Color(0, 0, 0, 0))
	var random := KR.JavaRandom.new(0x5052494E54)
	for i in 100:
		var light := random.next_int(4) == 0
		var color: Color
		if light:
			color = Color8(235, 221, 199, 17 + random.next_int(13))
		else:
			color = Color8(35, 29, 24, 15 + random.next_int(19))
		var x := random.next_float() * size
		var y := random.next_float() * size
		var radius: float
		if random.next_int(5) == 0:
			radius = 1.2 + random.next_float() * 1.4
		else:
			radius = 0.45 + random.next_float() * 0.9
		_stamp(image, Rect2(x - radius - 1.0, y - radius - 1.0, radius * 2.0 + 2.0, radius * 2.0 + 2.0),
			func(px: float, py: float) -> float: return radius + 0.5 - Vector2(px, py).distance_to(Vector2(x, y)), color)
	for i in 18:
		var color := Color8(35, 29, 24, 12 + random.next_int(13))
		var width := 0.4 + random.next_float() * 0.7
		var x := random.next_float() * size
		var y := random.next_float() * size
		var x2 := minf(x + 1.5 + random.next_float() * 4.0, size - 1.0)
		var y2 := clampf(y + (random.next_float() - 0.5) * 2.5, 0.0, size - 1.0)
		var a := Vector2(x, y)
		var b := Vector2(x2, y2)
		_stamp(image, Rect2(a, Vector2.ZERO).expand(b).grow(width + 1.0), func(px: float, py: float) -> float:
			return width * 0.5 + 0.5 - Geometry2D.get_closest_point_to_segment(Vector2(px, py), a, b).distance_to(Vector2(px, py)), color)
	_texture = ImageTexture.create_from_image(image)
	return _texture

## Source-over blend of an anti-aliased shape; `coverage(px, py)` returns 0..1 at pixel centres.
static func _stamp(image: Image, box: Rect2, coverage: Callable, color: Color) -> void:
	var size := image.get_width()
	for py in range(maxi(int(box.position.y), 0), mini(int(ceil(box.end.y)), size)):
		for px in range(maxi(int(box.position.x), 0), mini(int(ceil(box.end.x)), size)):
			var c := clampf(coverage.call(px + 0.5, py + 0.5), 0.0, 1.0)
			if c <= 0.0:
				continue
			var src := Color(color.r, color.g, color.b, color.a * c)
			var dst := image.get_pixel(px, py)
			var out_a := src.a + dst.a * (1.0 - src.a)
			if out_a <= 0.0:
				continue
			var rgb := (Vector3(src.r, src.g, src.b) * src.a + Vector3(dst.r, dst.g, dst.b) * dst.a * (1.0 - src.a)) / out_a
			image.set_pixel(px, py, Color(rgb.x, rgb.y, rgb.z, out_a))
