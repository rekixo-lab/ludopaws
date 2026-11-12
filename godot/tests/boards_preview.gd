extends SceneTree
## Every live board in a match position with legal-move hints showing (owner request:
## check the hints read clearly on every board). Writes board_hints_<id>.png.
##   godot --path . --script res://tests/boards_preview.gd -- res://.godot/preview

const Board = preload("res://scripts/game/board3d/classic_board_3d.gd")
const LocalMatch = preload("res://scripts/game/local_match.gd")
const Catalog = preload("res://scripts/game/store/store_catalog.gd")

func _initialize() -> void:
	var args := OS.get_cmdline_user_args()
	var out_dir: String = args[0] if args.size() > 0 else "user://"
	root.size = Vector2i(720, 760)
	var bg := ColorRect.new()
	bg.color = Color("#1c382d")
	bg.size = Vector2(720, 760)
	root.add_child(bg)
	for id in Catalog.BOARD_IDS:
		var seats: Array[int] = [0, 1, 2, 3]
		var game := LocalMatch.new(seats, "PASS_AND_PLAY")
		var players: Array = game.state["players"]
		players[0]["tokens"] = [-1, 5, 5, 21]
		players[1]["tokens"] = [8, 8, 8, 8]
		players[2]["tokens"] = [30, -1, 57, -1]
		players[3]["tokens"] = [12, 47, -1, -1]
		game._sync()
		game.apply_roll(6)
		var board := Board.new()
		board.board_id = id
		board.match_state = game
		board.input_enabled = true
		board.position = Vector2(20, 40)
		board.size = Vector2(680, 680)
		root.add_child(board)
		while Time.get_ticks_msec() % 3600 > 900:
			await process_frame
		for i in 8:
			await process_frame
		await RenderingServer.frame_post_draw
		root.get_texture().get_image().save_png(out_dir.path_join("board_hints_%s.png" % id))
		print("saved ", id)
		board.queue_free()
		await process_frame
	quit()
