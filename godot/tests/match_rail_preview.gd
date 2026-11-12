extends SceneTree
## Match rail screenshots into <out>/match_rail/: Robot 2p/3p/4p, Pass & Play 4p and the Demo
## through the real UI; Team Up and Online through the same rail component with mock models
## (online play is not built yet). Portrait 900x1600 and landscape 1600x900; the active dice
## sits on a different player in each shot.
##   godot --path . --script res://tests/match_rail_preview.gd -- res://.godot/preview

const LocalMatchPage = preload("res://scripts/game/local_match_page.gd")
const Rail = preload("res://scripts/ui/match/player_rail.gd")
const Board = preload("res://scripts/game/board3d/classic_board_3d.gd")
const LocalMatch = preload("res://scripts/game/local_match.gd")
const Demo = preload("res://scripts/game/tutorial/demo_match.gd")
const AgeEntry = preload("res://scripts/game/safety/age_entry.gd")

var out := ""

func _shot(name: String, landscape := false, frames := 20) -> void:
	DisplayServer.window_set_mode(DisplayServer.WINDOW_MODE_WINDOWED)
	root.size = Vector2i(1600, 900) if landscape else Vector2i(900, 2000)
	for i in frames:
		await process_frame
	await RenderingServer.frame_post_draw
	root.get_texture().get_image().save_png(out.path_join(name + ".png"))
	print("saved ", name)

func _initialize() -> void:
	var base: String = OS.get_cmdline_user_args()[0] if OS.get_cmdline_user_args().size() > 0 else "user://"
	out = base.path_join("match_rail")
	DirAccess.make_dir_recursive_absolute(ProjectSettings.globalize_path(out))
	Demo.flag_path = "user://preview_rail_tutorial.cfg"
	AgeEntry.default_path = "user://preview_rail_age.cfg"
	var home: Control = load("res://scenes/ui/home/home_screen.tscn").instantiate()
	root.add_child(home)
	await process_frame
	var page: Control = home.preview
	load("res://scripts/game/settings/game_settings.gd").overrides["confirm_exit"] = false
	for setup in [[&"robot", 2, 0, "robot_2p"], [&"robot", 3, 1, "robot_3p"], [&"robot", 4, 2, "robot_4p"], [&"pass_and_play", 4, 3, "pass_and_play_4p"]]:
		page.state.player_count = setup[1]
		page.start_match(setup[0])
		var game = page.state.local_game
		# Hand the turn to a different seat in each shot (presentation only).
		game.state["turnSeat"] = setup[2] % setup[1]
		game._sync()
		page.refresh()
		await _shot(setup[3])
		if setup[3] == "robot_4p":
			await _shot("robot_4p_landscape", true)
		page.reset_match()
	page.start_match(&"demo")
	page.refresh()
	await _shot("demo")
	page.reset_match()
	page.go_home()
	await process_frame
	# Mock Team Up / Online models through the same component.
	await _mock("team_up", true, false)
	await _mock("online_2p", false, true)
	await _mock("online_2p_landscape", false, true, true)
	quit()

func _mock(name: String, team: bool, online: bool, landscape := false) -> void:
	var bg := ColorRect.new()
	bg.color = Color("#0c2a21")
	bg.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	root.add_child(bg)
	var column := VBoxContainer.new()
	column.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	column.offset_left = 15
	column.offset_right = -15
	column.offset_top = 40
	root.add_child(column)
	var count := 4 if team else 2
	var seats: Array[int] = []
	seats.assign([0, 1, 2, 3] if team else [0, 2])
	var game := LocalMatch.new(seats, "TEAM_UP" if team else "PASS_AND_PLAY")
	var players: Array = []
	var names := ["You", "Mira", "Kabir", "Diya"]
	for i in count:
		var p: Dictionary = game.state["players"][i]
		players.append({"seat": i, "color": p["color"], "name": names[i], "character": ["dog", "goat", "duck", "cat"][i],
			"bot": online and i == 1 and count == 2, "computer": false, "local": i == 0, "forfeited": false,
			"tokens": [57, 20, -1, 5] if i == 0 else [-1, -1, 12, 40], "team_id": (["A", "B", "A", "B"][i] if team else null)})
	var model := {"players": players, "first_color": players[0]["color"], "online": online, "local_color": players[0]["color"],
		"local_team_id": "A" if team else null, "active_seat": 2 if team else 1, "roll_enabled": false,
		"dice_style": "dice_pearl_paw", "countdown": 0.6 if online else null, "width": root.get_visible_rect().size.x - 30.0}
	var rails := Rail.build(model, {}, func(_s): pass)
	column.add_child(rails["top"])
	var board := Board.new()
	board.match_state = game
	board.rotation_turns = 3
	board.custom_minimum_size.y = minf(root.get_visible_rect().size.x - 30.0, root.get_visible_rect().size.y * (0.62 if not landscape else 0.55))
	board.size_flags_vertical = Control.SIZE_EXPAND_FILL
	column.add_child(board)
	column.add_child(rails["bottom"])
	await _shot(name, landscape)
	column.queue_free()
	bg.queue_free()
	await process_frame
