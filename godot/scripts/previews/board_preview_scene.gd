@tool
extends Control
## Editor/F6 preview of the 3D classic board with a mid-game position.
## Open scenes/previews/board_preview.tscn: the board draws in the 2D editor viewport.

const Board = preload("res://scripts/game/board3d/classic_board_3d.gd")
const LocalMatch = preload("res://scripts/game/local_match.gd")

## Quarter turns of the board perspective (3 = Red at bottom-left, as in a Red match).
@export_range(0, 3) var perspective := 0:
	set(value):
		perspective = value
		if is_instance_valid(_board):
			_board.rotation_turns = value

var _board: Control

func _ready() -> void:
	for child in get_children():
		if child.has_meta("generated"):
			child.queue_free()
	var bg := ColorRect.new()
	bg.color = Color("#1c382d")
	bg.mouse_filter = Control.MOUSE_FILTER_IGNORE
	bg.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	bg.set_meta("generated", true)
	add_child(bg)
	_board = Board.new()
	_board.set_meta("generated", true)
	_board.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	_board.offset_left = 16
	_board.offset_right = -16
	_board.match_state = mid_game()
	_board.rotation_turns = perspective
	_board.input_enabled = false
	add_child(_board)

## Four players mid-race: yard, start cell, a two-pawn stack, home lane, a finished pair.
static func mid_game():
	var seats: Array[int] = [0, 1, 2, 3]
	var game = LocalMatch.new(seats, "PASS_AND_PLAY")
	var players: Array = game.state["players"]
	players[0]["tokens"] = [-1, 5, 5, 54]
	players[1]["tokens"] = [-1, -1, 0, 20]
	players[2]["tokens"] = [57, 57, 30, -1]
	players[3]["tokens"] = [12, 47, -1, -1]
	game._sync()
	return game
