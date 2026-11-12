extends Control
## Draws the playable board and resolves taps in the same board coordinates.

signal pawn_selected(index: int)
const Path = preload("res://scripts/game/classic_path.gd")
const Rules = preload("res://scripts/game/local_match.gd")
const COLORS := [Color("#ef5264"), Color("#04b879"), Color("#ffd53a"), Color("#159ddb")]
const YARDS := [Vector2(0, 0), Vector2(10, 0), Vector2(10, 10), Vector2(0, 10)]
const FUR := [Color("#aa663a"), Color("#ebb044"), Color("#a96236"), Color("#e9e6d7")]
const INK := Color("#251d1a")
var match_state: Rules
var animals: Array[int] = []
var input_enabled := true
var rotation_turns := 0
var targets: Dictionary = {}

func _ready() -> void:
	resized.connect(queue_redraw)

func _edge() -> float:
	return minf(size.x, size.y)

func _display(point: Vector2) -> Vector2:
	var rotated := Vector2(7.5, 7.5) + (point - Vector2(7.5, 7.5)).rotated(rotation_turns * PI / 2.0)
	return (size - Vector2.ONE * _edge()) * 0.5 + rotated * (_edge() / 15.0)

func _tile(rect: Rect2, color: Color, radius: float) -> void:
	var a := _display(rect.position)
	var b := _display(rect.end)
	var box := Rect2(a, b - a).abs().grow(-0.55)
	var style := StyleBoxFlat.new()
	style.bg_color = color
	style.set_corner_radius_all(roundi(radius))
	draw_style_box(style, box)

func _draw() -> void:
	targets.clear()
	if match_state == null or _edge() <= 0:
		return
	_tile(Rect2(0, 0, 15, 15), Color("#33442c"), 12)
	for color in 4:
		var origin: Vector2 = YARDS[color]
		var tint: Color = COLORS[color]
		_tile(Rect2(origin, Vector2(5, 5)), tint.darkened(0.61), 14)
		_tile(Rect2(origin + Vector2(0.5, 0.5), Vector2(4, 4)), tint.lightened(0.24).darkened(0.26), 14)
		for index in 4:
			draw_circle(_display(_yard(color, index)), _edge() * 0.019, tint.darkened(0.36), true, -1, true)
		for step in 5:
			_tile(Path.cell_rect(Path.lane_cell(color, step)), tint, 5)
	for index in Path.TRACK.size():
		var cell: Vector2i = Path.TRACK[index]
		var rect := Path.cell_rect(cell)
		_tile(rect, COLORS[index / 13] if index % 13 == 0 else Color("#e8e4c4"), 5)
		if index in Path.SAFE:
			var center := _display(rect.get_center())
			draw_circle(center, _edge() * 0.008, Color("#246a69"), true, -1, true)
	var corners := [Vector2(5, 10), Vector2(5, 5), Vector2(10, 5), Vector2(10, 10)]
	for color in 4:
		draw_colored_polygon(PackedVector2Array([_display(Vector2(7.5, 7.5)), _display(corners[color]), _display(corners[(color + 1) % 4])]), COLORS[color])
	_draw_pawns()

func _yard(color: int, index: int) -> Vector2:
	var down := Vector2(0, 0.4).rotated(-rotation_turns * PI / 2.0)
	return YARDS[color] + Vector2(1.5 + 2 * (index % 2), 1.5 + 2 * floori(index / 2.0)) + down

func _pawn_position(color: int, index: int, position: int) -> Vector2:
	if position == Path.YARD:
		return _yard(color, index)
	if position <= 50:
		return Path.cell_rect(Path.TRACK[Path.global_index(color, position)]).get_center()
	if position < Path.HOME:
		return Path.cell_rect(Path.lane_cell(color, maxi(0, position - 52))).get_center()
	var normal := Vector2.LEFT.rotated(color * PI / 2.0)
	var facing := normal.rotated(rotation_turns * PI / 2.0).round()
	var court := Vector2(facing.x * (0.69 if index % 2 == 0 else 0.89), -0.15 if index < 2 else 0.62)
	if facing.x == 0:
		court = Vector2([-0.47, -0.25, 0.25, 0.47][index], -0.52 if facing.y < 0 else 0.96)
	return Vector2(7.5, 7.5) + court.rotated(-rotation_turns * PI / 2.0) * 2.5

func _draw_pawns() -> void:
	var piles := {}
	for seat in match_state.colors.size():
		var color: int = match_state.colors[seat]
		for index in 4:
			var pos := match_state.pawns[seat][index]
			var point := _display(_pawn_position(color, index, pos))
			var group := Vector2i(point.round())
			if not piles.has(group):
				piles[group] = []
			var can_move := input_enabled and seat == match_state.turn and index in match_state.legal
			piles[group].append([point, index, animals[seat], COLORS[color], can_move])
	for group in piles.values():
		for offset in group.size():
			var entry: Array = group[offset]
			var point: Vector2 = entry[0] + Vector2((offset - (group.size() - 1) / 2.0) * _edge() * 0.025, 0)
			var radius := _edge() * (0.025 if group.size() == 1 else 0.019)
			_animal(point, radius, entry[2], entry[3], entry[4])
			if entry[4]:
				targets[entry[1]] = point - Vector2(0, radius * 0.7)

func _animal(p: Vector2, r: float, species: int, team: Color, active: bool) -> void:
	var fur: Color = FUR[species]
	var ear := INK if species == 3 else fur.darkened(0.25)
	draw_circle(p + Vector2(0, r * 0.28), r * 0.88, Color("#1a211880"), true, -1, true)
	draw_circle(p, r * 0.84, Color("#ffe797") if active else team, false, 2.2, true)
	if species in [0, 1]:
		var neck := 1.45 if species == 1 else 0.92
		draw_line(p, p - Vector2(0, neck * r), fur.darkened(0.18), r * 0.74, true)
		p.y -= (neck - 0.5) * r
	draw_circle(p + Vector2(-0.54, -1.07) * r, r * 0.3, ear, true, -1, true)
	draw_circle(p + Vector2(0.54, -1.07) * r, r * 0.3, ear, true, -1, true)
	var head := p - Vector2(0, r * 0.66)
	draw_circle(head, r * 0.73, fur.darkened(0.22), true, -1, true)
	draw_circle(head - Vector2(0, r * 0.10), r * 0.66, fur, true, -1, true)
	if species == 1:
		draw_circle(head + Vector2(0.36, 0.25) * r, r * 0.15, Color("#99532c"), true, -1, true)
	var muzzle := Color("#d3a372") if species != 3 else Color("#f9f2df")
	draw_circle(head + Vector2(0, 0.28) * r, r * 0.36, muzzle, true, -1, true)
	for direction in [-1, 1]:
		var eye := head + Vector2(direction * 0.30, -0.10) * r
		draw_circle(eye, r * (0.23 if species == 3 else 0.115), INK, true, -1, true)
		draw_circle(eye - Vector2(0, 0.03) * r, r * 0.047, Color.WHITE, true, -1, true)
	draw_circle(head + Vector2(0, 0.21) * r, r * 0.095, INK, true, -1, true)

func _gui_input(event: InputEvent) -> void:
	if not event is InputEventMouseButton or event.button_index != MOUSE_BUTTON_LEFT or not event.pressed:
		return
	var nearest := -1
	var distance := maxf(13.0, _edge() * 0.055)
	for index in targets:
		var candidate: float = event.position.distance_to(targets[index])
		if candidate < distance:
			distance = candidate
			nearest = index
	if nearest >= 0:
		accept_event()
		pawn_selected.emit(nearest)
