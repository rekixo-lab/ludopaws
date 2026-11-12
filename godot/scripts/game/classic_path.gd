extends RefCounted
## Board coordinates use a fixed 15-unit square. The middle band is five units wide.

const HOME := 57
const YARD := -1
const SAFE := [0, 8, 13, 21, 26, 34, 39, 47]
# Points are column, row; colors go clockwise from the top-left yard.
const TRACK := [
	Vector2i(1,6), Vector2i(2,6), Vector2i(3,6), Vector2i(4,6), Vector2i(5,6),
	Vector2i(6,5), Vector2i(6,4), Vector2i(6,3), Vector2i(6,2), Vector2i(6,1), Vector2i(6,0), Vector2i(7,0), Vector2i(8,0),
	Vector2i(8,1), Vector2i(8,2), Vector2i(8,3), Vector2i(8,4), Vector2i(8,5),
	Vector2i(9,6), Vector2i(10,6), Vector2i(11,6), Vector2i(12,6), Vector2i(13,6), Vector2i(14,6), Vector2i(14,7), Vector2i(14,8),
	Vector2i(13,8), Vector2i(12,8), Vector2i(11,8), Vector2i(10,8), Vector2i(9,8),
	Vector2i(8,9), Vector2i(8,10), Vector2i(8,11), Vector2i(8,12), Vector2i(8,13), Vector2i(8,14), Vector2i(7,14), Vector2i(6,14),
	Vector2i(6,13), Vector2i(6,12), Vector2i(6,11), Vector2i(6,10), Vector2i(6,9),
	Vector2i(5,8), Vector2i(4,8), Vector2i(3,8), Vector2i(2,8), Vector2i(1,8), Vector2i(0,8), Vector2i(0,7), Vector2i(0,6),
]

static func destination(position: int, roll: int) -> int:
	if roll < 1 or roll > 6 or position < YARD or position >= HOME:
		return -2
	if position == YARD:
		return 0 if roll == 6 else -2
	if position == 51:
		position = 52
	var target := position + roll
	if position <= 50 and target > 50:
		target += 1
	return target if target <= HOME else -2

static func global_index(color: int, position: int) -> int:
	return (color * 13 + position) % 52 if position >= 0 and position <= 50 else -1

static func lane_cell(color: int, step: int) -> Vector2i:
	match color:
		0: return Vector2i(1 + step, 7)
		1: return Vector2i(7, 1 + step)
		2: return Vector2i(13 - step, 7)
		_: return Vector2i(7, 13 - step)

static func boundary(index: int) -> float:
	if index <= 6:
		return index * 5.0 / 6.0
	if index <= 9:
		return 5.0 + (index - 6) * 5.0 / 3.0
	return 10.0 + (index - 9) * 5.0 / 6.0

static func cell_rect(cell: Vector2i) -> Rect2:
	var a := Vector2(boundary(cell.x), boundary(cell.y))
	var b := Vector2(boundary(cell.x + 1), boundary(cell.y + 1))
	return Rect2(a, b - a)
