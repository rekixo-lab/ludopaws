extends SceneTree
## Renders the 3D classic board with a mid-game position and saves screenshots.
## Needs a display (not --headless):
##   godot --path . --script res://tests/board_preview.gd -- [out_dir]
## Writes board_turns0.png, board_turns3.png, board_capture_return.png, board_fx.png and board_finished.png to out_dir (default user://).

const Board = preload("res://scripts/game/board3d/classic_board_3d.gd")
const LocalMatch = preload("res://scripts/game/local_match.gd")

func _initialize() -> void:
	var args := OS.get_cmdline_user_args()
	var out_dir: String = args[0] if args.size() > 0 else "user://"
	root.size = Vector2i(720, 820)
	var seats: Array[int] = [0, 1, 2, 3]
	var game := LocalMatch.new(seats, "PASS_AND_PLAY")
	var players: Array = game.state["players"]
	players[0]["tokens"] = [-1, 5, 5, 54]
	players[1]["tokens"] = [-1, -1, 0, 20]
	players[2]["tokens"] = [57, 57, 30, -1]
	players[3]["tokens"] = [12, 47, -1, -1]
	game._sync()
	var bg := ColorRect.new()
	bg.color = Color("#1c382d")
	bg.size = Vector2(720, 820)
	root.add_child(bg)
	var board := Board.new()
	board.match_state = game
	board.position = Vector2(20, 60)
	board.size = Vector2(680, 680)
	root.add_child(board)
	for turns in [0, 3]:
		board.rotation_turns = turns
		for i in 12:
			await process_frame
		await RenderingServer.frame_post_draw
		var image := root.get_texture().get_image()
		var path := out_dir.path_join("board_turns%d.png" % turns)
		image.save_png(path)
		print("saved ", ProjectSettings.globalize_path(path))
	# Legal-move cues (V2 colours, live shapes): Red rolls 6 with paws in the yard and on 5/5;
	# Green's four paws plus one Red paw crowd one tile (count badge). Wait for the tap hand.
	board.rotation_turns = 0
	players[1]["tokens"] = [8, 8, 8, 8]
	players[0]["tokens"] = [-1, 5, 5, 21]
	game._sync()
	game.apply_roll(6)
	board.input_enabled = true
	while Time.get_ticks_msec() % 3600 > 900:
		await process_frame
	for i in 6:
		await process_frame
	await RenderingServer.frame_post_draw
	root.get_texture().get_image().save_png(out_dir.path_join("board_hints.png"))
	print("saved hints legal=", game.legal)
	board.input_enabled = false
	players[1]["tokens"] = [-1, -1, 0, 20]
	players[0]["tokens"] = [-1, 5, 5, 54]
	game.state["pendingRoll"] = null
	game._sync()
	# Blue's pawn on 12 is captured: mid-way along the live curved return to its yard.
	board.rotation_turns = 0
	for i in 4:
		await process_frame
	players[3]["tokens"] = [-1, 47, -1, -1]
	game._sync()
	await create_timer(0.72).timeout
	await RenderingServer.frame_post_draw
	root.get_texture().get_image().save_png(out_dir.path_join("board_capture_return.png"))
	print("saved capture return")
	# FX overlay: Red steps 5 -> 8 (paw trail), then its capture burst and Green's ghost run home.
	var before: Dictionary = game.snapshot()
	players[0]["tokens"] = [-1, 8, 5, 54]
	players[1]["tokens"] = [-1, -1, 0, -1]
	game._sync()
	var after: Dictionary = game.snapshot()
	after["matchId"] = before["matchId"]
	var t0 := Time.get_ticks_msec()
	board._fx.bind(before, after, t0)
	board._fx.play([{"playerId": players[0]["playerId"], "tokenIndex": 1, "animationCue": "CAPTURE"}], t0)
	await create_timer(1.02).timeout
	await RenderingServer.frame_post_draw
	root.get_texture().get_image().save_png(out_dir.path_join("board_fx.png"))
	print("saved fx")
	# Finished two-player match: Red won, Yellow placed second.
	var seats2: Array[int] = [0, 2]
	var done := LocalMatch.new(seats2, "PASS_AND_PLAY")
	done.state["players"][0]["tokens"] = [57, 57, 57, 57]
	done.state["players"][1]["tokens"] = [57, 40, -1, 52]
	done.state["status"] = "FINISHED"
	done.state["finishOrderPlayerIds"] = [done.state["players"][0]["playerId"]]
	done._sync()
	board.match_state = done
	board.rotation_turns = 3
	for i in 20:
		await process_frame
	await RenderingServer.frame_post_draw
	var finished := root.get_texture().get_image()
	finished.save_png(out_dir.path_join("board_finished.png"))
	print("saved finished")
	quit()
