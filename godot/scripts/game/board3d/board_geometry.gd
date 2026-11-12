@tool
extends RefCounted
## Presentation geometry of the stretched classic board, shared by the 2D art, the 3D
## scene, pawns and touch. Port of LudoPawsFxBoardGeometry and LudoPawsPawnLayout.
## All results are in board pixels for a board `edge` px square (cell = edge / 15).
## Gameplay never reads these values; they only place already-authoritative tokens.

const GiraffeDef = preload("res://scripts/game/characters/giraffe/giraffe_definition.gd")
const HOME := 57
const YARD := -1
const COLORS := ["RED", "GREEN", "YELLOW", "BLUE"]
const START_OFFSETS := {"RED": 0, "GREEN": 13, "YELLOW": 26, "BLUE": 39}
const CENTER_HOUSE_SCALE := 0.50
const ANIMAL_SIZE_MULTIPLIER := 1.32

## [row, col]
const TRACK := [
	[6, 1], [6, 2], [6, 3], [6, 4], [6, 5], [5, 6], [4, 6], [3, 6], [2, 6], [1, 6],
	[0, 6], [0, 7], [0, 8], [1, 8], [2, 8], [3, 8], [4, 8], [5, 8], [6, 9], [6, 10],
	[6, 11], [6, 12], [6, 13], [6, 14], [7, 14], [8, 14], [8, 13], [8, 12], [8, 11], [8, 10],
	[8, 9], [9, 8], [10, 8], [11, 8], [12, 8], [13, 8], [14, 8], [14, 7], [14, 6], [13, 6],
	[12, 6], [11, 6], [10, 6], [9, 6], [8, 5], [8, 4], [8, 3], [8, 2], [8, 1], [8, 0],
	[7, 0], [6, 0],
]
const HOME_LANES := {
	"RED": [[7, 1], [7, 2], [7, 3], [7, 4], [7, 5]],
	"GREEN": [[1, 7], [2, 7], [3, 7], [4, 7], [5, 7]],
	"YELLOW": [[7, 13], [7, 12], [7, 11], [7, 10], [7, 9]],
	"BLUE": [[13, 7], [12, 7], [11, 7], [10, 7], [9, 7]],
}
const YARD_ORIGINS := {"RED": [0, 0], "GREEN": [0, 9], "YELLOW": [9, 9], "BLUE": [9, 0]}

static func axis_boundary(index: int, cell: float) -> float:
	var yard_step := cell * 5.0 / 6.0
	var road_step := cell * 5.0 / 3.0
	if index <= 6:
		return index * yard_step
	if index <= 9:
		return cell * 5.0 + (index - 6) * road_step
	return cell * 10.0 + (index - 9) * yard_step

static func center_for_cell(row: int, col: int, cell: float) -> Vector2:
	return Vector2((axis_boundary(col, cell) + axis_boundary(col + 1, cell)) * 0.5,
		(axis_boundary(row, cell) + axis_boundary(row + 1, cell)) * 0.5)

static func _rot(p: Vector2, turns: int) -> Vector2:
	match posmod(turns, 4):
		1: return Vector2(-p.y, p.x)
		2: return Vector2(-p.x, -p.y)
		3: return Vector2(p.y, -p.x)
	return p

## Unrotated (logical) board point for a token; null when it has no visual cell.
static func token_center(color: String, token_index: int, position: int, cell: float, turns := 0):
	var render_position := 52 if position == 51 else position
	if render_position == YARD:
		return yard_token_center(color, token_index, cell, turns)
	if render_position >= 0 and render_position <= 50:
		if not START_OFFSETS.has(color):
			return null
		var coord: Array = TRACK[(START_OFFSETS[color] + render_position) % TRACK.size()]
		return center_for_cell(coord[0], coord[1], cell)
	if render_position >= 52 and render_position <= 56:
		var lane: Array = HOME_LANES.get(color, [])
		if lane.is_empty():
			return null
		var coord: Array = lane[render_position - 52]
		return center_for_cell(coord[0], coord[1], cell)
	if render_position == HOME:
		return finish_garden_center(color, token_index, cell, turns)
	return null

static func finish_garden_center(color: String, token_index: int, cell: float, turns := 0) -> Vector2:
	var left := axis_boundary(6, cell)
	var right := axis_boundary(9, cell)
	var center := (left + right) * 0.5
	var half := (right - left) * 0.5
	var normal := Vector2.ZERO
	match color:
		"RED": normal = Vector2(-1, 0)
		"GREEN": normal = Vector2(0, -1)
		"YELLOW": normal = Vector2(1, 0)
		"BLUE": normal = Vector2(0, 1)
		_: return Vector2(center, center)
	var facing := _rot(normal, turns)
	var index := clampi(token_index, 0, 3)
	var screen: Vector2
	if facing.x != 0.0:
		screen = Vector2(facing.x * (0.69 if index % 2 == 0 else 0.89), -0.15 if index < 2 else 0.62)
	else:
		screen = Vector2([-0.47, -0.25, 0.25, 0.47][index], -0.52 if facing.y < 0.0 else 0.96)
	var logical := _rot(screen, posmod(-turns, 4))
	return Vector2(center, center) + logical * half

static func yard_token_center(color: String, token_index: int, cell: float, turns := 0):
	if not YARD_ORIGINS.has(color):
		return null
	var origin: Array = YARD_ORIGINS[color]
	var white_left := axis_boundary(origin[1], cell) + cell * 0.5
	var white_top := axis_boundary(origin[0], cell) + cell * 0.5
	var white_size := cell * 4.0
	var row_fraction := 0.25 if token_index < 2 else 0.75
	var col_fraction := 0.25 if token_index % 2 == 0 else 0.75
	var distance := cell * 0.40
	var offset := Vector2(0, distance)
	match posmod(turns, 4):
		1: offset = Vector2(distance, 0)
		2: offset = Vector2(0, -distance)
		3: offset = Vector2(-distance, 0)
	return Vector2(white_left + white_size * col_fraction, white_top + white_size * row_fraction) + offset

## Board rotation for the perspective (canvas.rotate(turns * 90) about the centre).
static func rotate_point(p: Vector2, edge: float, turns: int) -> Vector2:
	var c := Vector2(edge, edge) * 0.5
	return c + (p - c).rotated(posmod(turns, 4) * PI / 2.0)

static func unrotate_point(p: Vector2, edge: float, turns: int) -> Vector2:
	var c := Vector2(edge, edge) * 0.5
	return c + (p - c).rotated(-posmod(turns, 4) * PI / 2.0)

# ---------------------------------------------------------------- pawn layout

static func radius_scale(position: int, occupancy: int) -> float:
	var base := 0.40 if position == YARD else (0.28 if position == HOME else 0.37)
	var crowd := 1.0
	if occupancy == 2: crowd = 0.92
	elif occupancy == 3: crowd = 0.86
	elif occupancy == 4: crowd = 0.80
	elif occupancy > 4 and occupancy <= 6: crowd = 0.72
	elif occupancy > 6 and occupancy <= 9: crowd = 0.64
	elif occupancy > 9: crowd = 0.56
	return base * crowd * ANIMAL_SIZE_MULTIPLIER

## LudoPawsPawnLayout.silhouetteHeight: model height in model units used for the road foot drop.
static func silhouette_height(character_id: String) -> float:
	match character_id:
		"donkey": return 3.35
		"goat": return 3.15
		"cat": return 2.10 * 1.375 # CatDefinition.MODEL_SIZE_MULTIPLIER
		"goat_lumi": return 2.35 * 1.38 # LumiGoatDefinition.MODEL_SIZE_MULTIPLIER
		"duck": return 2.85
		"cock": return 2.22
		"pig": return 2.20
		"elephant": return 2.10
		"horse": return 2.50
		"giraffe": return (2.07 - GiraffeDef.GROUND_Y) * GiraffeDef.MODEL_SCALE
		"rabbit": return 2.60
	return 3.0

## LudoPawsPawnLayout.roadPresentation: on the four road arms (not yards or the centre), feet
## drop toward the lower edge of their stack slot without shrinking the animal. `p` is the
## unrotated, un-offset centre; returns {radius, foot_drop} (foot_drop in screen y, cell units).
static func road_presentation(p: Vector2, cell: float, position: int, occupancy: int, character_id: String, turns: int) -> Dictionary:
	var original := cell * radius_scale(position, occupancy)
	var in_x := p.x >= cell * 5.0 and p.x <= cell * 10.0
	var in_y := p.y >= cell * 5.0 and p.y <= cell * 10.0
	if position < 0 or position >= HOME or (not in_x and not in_y):
		return {"radius": original, "foot_drop": 0.0}
	var logical_width := cell * 5.0 / 3.0 if in_x else cell * 5.0 / 6.0
	var logical_height := cell * 5.0 / 3.0 if in_y else cell * 5.0 / 6.0
	var height := logical_height if turns % 2 == 0 else logical_width
	var width := logical_width if turns % 2 == 0 else logical_height
	var screen_rows := _road_rows(occupancy, width >= height).size()
	var foot_drop := minf(original * silhouette_height(character_id) * 0.44, height / screen_rows * 0.38)
	return {"radius": original, "foot_drop": foot_drop}

## LudoPawsPawnLayout.tokenOffsetFraction: fixed per-slot nudge used by the capture-return route.
static func token_offset_fraction(slot: int, position: int) -> Vector2:
	if position < 0 or position > HOME:
		return Vector2.ZERO
	return [Vector2(-0.13, -0.13), Vector2(0.13, -0.13), Vector2(-0.13, 0.13), Vector2(0.13, 0.13)][posmod(slot, 4)]

static func _center_key(p: Vector2) -> String:
	return "%d:%d" % [roundi(p.x * 1000.0), roundi(p.y * 1000.0)]

static func _road_rows(occupancy: int, wide: bool) -> Array:
	match occupancy:
		1: return [1]
		2: return [2] if wide else [1, 1]
		3: return [3] if wide else [1, 2]
		4: return [2, 2]
		5: return [3, 2] if wide else [2, 1, 2]
	var columns := ceili(sqrt(occupancy * 2.0)) if wide else ceili(sqrt(occupancy / 2.0))
	var rows: Array = []
	for k in occupancy / columns:
		rows.append(columns)
	if occupancy % columns > 0:
		rows.append(occupancy % columns)
	return rows

static func _road_stack_offset(slot: int, occupancy: int, width: float, height: float) -> Vector2:
	var rows := _road_rows(occupancy, width >= height)
	var remaining := slot
	var row := 0
	while row < rows.size() - 1 and remaining >= rows[row]:
		remaining -= rows[row]
		row += 1
	var count: int = rows[row]
	var stagger := ((-0.035 if row % 2 == 0 else 0.035) * width) if (count > 1 and rows.size() > 1) else 0.0
	var x := ((remaining + 0.5) / count - 0.5) * width * 0.94 + stagger
	var y := ((row + 0.5) / rows.size() - 0.5) * height * 0.84 + \
		(((-0.025 if remaining % 2 == 0 else 0.025) * height) if (count > 1 and width < height) else 0.0)
	return Vector2(x, y)

static func stack_offset_fraction(slot: int, occupancy: int) -> Vector2:
	if occupancy <= 1:
		return Vector2.ZERO
	var s := posmod(slot, occupancy)
	match occupancy:
		2: return Vector2(-0.18 if s == 0 else 0.18, 0)
		3: return [Vector2(0, -0.18), Vector2(-0.17, 0.14), Vector2(0.17, 0.14)][s]
		4: return [Vector2(-0.16, -0.16), Vector2(0.16, -0.16), Vector2(-0.16, 0.16), Vector2(0.16, 0.16)][s]
	var columns := maxi(ceili(sqrt(occupancy)), 2)
	var rows := ceili(float(occupancy) / columns)
	var spacing := 0.16 if occupancy <= 6 else (0.14 if occupancy <= 9 else 0.12)
	return Vector2((s % columns - (columns - 1) / 2.0) * spacing, (s / columns - (rows - 1) / 2.0) * spacing)

## players: [{playerId, seat, color, tokens}]. Returns {"playerId:token": {slot, occupancy, offset}}.
static func stack_placements(players: Array, cell: float, turns := 0) -> Dictionary:
	turns = posmod(turns, 4)
	var sorted := players.duplicate()
	sorted.sort_custom(func(a, b): return a["seat"] < b["seat"] or (a["seat"] == b["seat"] and str(a["playerId"]) < str(b["playerId"])))
	var groups := {}
	var centers := {}
	for player in sorted:
		var tokens: Array = player["tokens"]
		for token in tokens.size():
			var center = token_center(player["color"], token, int(tokens[token]), cell)
			if center == null:
				continue
			var key := _center_key(center)
			centers[key] = center
			if not groups.has(key):
				groups[key] = []
			groups[key].append("%s:%d" % [player["playerId"], token])
	var out := {}
	for key in groups:
		var occupants: Array = groups[key]
		var center: Vector2 = centers[key]
		var in_x := center.x >= cell * 5.0 and center.x <= cell * 10.0
		var in_y := center.y >= cell * 5.0 and center.y <= cell * 10.0
		var on_road := in_x or in_y
		var width := 5.0 / 3.0 if in_x else 5.0 / 6.0
		var height := 5.0 / 3.0 if in_y else 5.0 / 6.0
		for slot in occupants.size():
			var offset: Vector2
			if on_road and occupants.size() > 1:
				var screen := _road_stack_offset(slot, occupants.size(), width if turns % 2 == 0 else height, height if turns % 2 == 0 else width)
				offset = _rot(screen, posmod(-turns, 4))
			else:
				offset = stack_offset_fraction(slot, occupants.size())
			out[occupants[slot]] = {"slot": slot, "occupancy": occupants.size(), "offset": offset}
	return out
