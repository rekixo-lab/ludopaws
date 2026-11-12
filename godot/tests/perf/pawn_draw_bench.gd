extends SceneTree
## Desktop micro-benchmark of the per-frame pawn draw (16 animals). Relative costs only;
## device numbers come from the perf probe.
##   godot --headless --path . --script res://tests/perf/pawn_draw_bench.gd

const Board = preload("res://scripts/game/board3d/classic_board_3d.gd")
const LocalMatch = preload("res://scripts/game/local_match.gd")
const D = preload("res://scripts/game/board3d/paw_draw.gd")

func _initialize() -> void:
	var seats: Array[int] = []
	seats.assign([0, 1, 2, 3])
	var players: Array = []
	for id in ["dog", "goat", "duck", "cat"]:
		players.append({"displayName": id, "characterId": id, "playerType": "HUMAN"})
	var game := LocalMatch.new(seats, "PASS_AND_PLAY", players)
	var board := Board.new()
	board.match_state = game
	board.size = Vector2(400, 400)
	root.add_child(board)
	for i in 5:
		await process_frame
	var scene: Dictionary = board.scene_state()
	var now := Time.get_ticks_msec()
	var pawns: Array = board._pawn_states(scene, now)
	var animals: Array = pawns.filter(func(p): return p["rendered"])
	for mode in [0, 1, 2, 3]:
		D.Pool.bench_mode = mode
		var t := Time.get_ticks_usec()
		var n := 60
		for i in n:
			board._pawn_layer.draw(animals, now + i * 16, 1.0, Vector2(Board.BOARD, Board.BOARD))
		var per := (Time.get_ticks_usec() - t) / 1000.0 / n
		print("BENCH mode=%d animals=%d parts=%d ms/frame=%.2f" % [mode, animals.size(), board._pawn_layer.pool._used, per])
	D.Pool.bench_mode = 0
	quit()
