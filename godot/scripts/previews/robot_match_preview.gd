@tool
extends Control
## Robot vs Robot on the 3D classic board: real rules engine, live Robot policy,
## local dice and the board's step/capture animations. Restarts after each match.
## Runs in the editor viewport too (toggle run_in_editor) and with F6.

const Board = preload("res://scripts/game/board3d/classic_board_3d.gd")
const LocalMatch = preload("res://scripts/game/local_match.gd")
const LocalDice = preload("res://scripts/game/rules/local_dice.gd")
const Bot = preload("res://scripts/game/rules/ludo_bot.gd")
const NAMES := ["Red", "Green", "Yellow", "Blue"]

@export_range(2, 4) var player_count := 4
@export_range(0.1, 2.0, 0.05) var think_seconds := 0.35
@export var run_in_editor := true

var _board: Control
var _status: Label
var _log: Label
var _game
var _wait := 0.0
var _history: Array[String] = []

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
	var column := VBoxContainer.new()
	column.set_meta("generated", true)
	column.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	column.add_theme_constant_override("separation", 8)
	for side in ["left", "right", "top", "bottom"]:
		column.set("offset_" + side, 16 if side in ["left", "top"] else -16)
	add_child(column)
	_status = Label.new()
	_status.add_theme_font_size_override("font_size", 22)
	_status.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	column.add_child(_status)
	_board = Board.new()
	_board.size_flags_vertical = Control.SIZE_EXPAND_FILL
	_board.input_enabled = false
	column.add_child(_board)
	_log = Label.new()
	_log.add_theme_font_size_override("font_size", 16)
	_log.custom_minimum_size.y = 150
	_log.modulate = Color(1, 1, 1, 0.85)
	column.add_child(_log)
	_new_match()

func _new_match() -> void:
	var seats: Array[int] = []
	for color in ([0, 2] if player_count == 2 else range(player_count)):
		seats.append(color)
	var players: Array = []
	for i in seats.size():
		players.append({"displayName": "Robot %s" % NAMES[seats[i]], "playerType": "BOT"})
	_game = LocalMatch.new(seats, "ROBOT", players)
	_board.match_state = _game
	_history.clear()
	_wait = 0.8
	_show_status("New match · %d robots" % seats.size())

func _process(delta: float) -> void:
	if Engine.is_editor_hint() and not run_in_editor:
		return
	if _game == null:
		return
	_wait -= delta
	if _wait > 0.0 or _board.is_animating():
		return
	if _game.complete:
		_new_match()
		return
	_wait = think_seconds
	var seat: int = _game.turn
	var who: String = NAMES[_game.colors[seat]]
	if _game.legal.is_empty():
		_game.apply_roll(LocalDice.roll())
		_record("%s · %s" % [who, _game.message])
	else:
		var token: int = _game.robot_choice(Bot.Difficulty.NORMAL)
		_game.move(token)
		_record("%s · %s" % [who, _game.message])
		if _game.complete:
			var order: Array = []
			for i in _game.finished.size():
				order.append("%d. %s" % [i + 1, NAMES[_game.colors[_game.finished[i]]]])
			_show_status("Match over · " + "  ".join(order))
			_wait = 4.0
			return
	_show_status("Turn: %s · roll %d" % [NAMES[_game.colors[_game.turn]], _game.roll])

func _record(entry: String) -> void:
	_history.push_front(entry)
	if _history.size() > 6:
		_history.pop_back()
	_log.text = "\n".join(_history)

func _show_status(text: String) -> void:
	_status.text = text
