extends SceneTree
## Demo (NEW V2 FEATURE) screenshots into <out>/tutorial/: age entry, demo start, first roll
## hint, capture hint, home-lane hint, win screen, skip dialog. ONLY this screenshot script
## fixes dice faces (flow.roll_source) to reach those moments; the demo itself uses real dice.
##   godot --path . --script res://tests/tutorial_preview.gd -- res://.godot/preview

const LocalMatchPage = preload("res://scripts/game/local_match_page.gd")
const Demo = preload("res://scripts/game/tutorial/demo_match.gd")
const AgeEntry = preload("res://scripts/game/safety/age_entry.gd")
const Tutorial = preload("res://scripts/ui/preview/tutorial_pages.gd")

var out := ""

func _shot(name: String, frames := 12) -> void:
	DisplayServer.window_set_mode(DisplayServer.WINDOW_MODE_WINDOWED)
	root.size = Vector2i(900, 1600)
	for i in frames:
		await process_frame
	await RenderingServer.frame_post_draw
	root.get_texture().get_image().save_png(out.path_join(name + ".png"))
	print("saved ", name)

func _initialize() -> void:
	var base: String = OS.get_cmdline_user_args()[0] if OS.get_cmdline_user_args().size() > 0 else "user://"
	out = base.path_join("tutorial")
	DirAccess.make_dir_recursive_absolute(ProjectSettings.globalize_path(out))
	Demo.flag_path = "user://preview_tutorial.cfg"
	AgeEntry.default_path = "user://preview_age.cfg"
	Demo.reset()
	DirAccess.remove_absolute(ProjectSettings.globalize_path(AgeEntry.default_path))
	var home: Control = load("res://scenes/ui/home/home_screen.tscn").instantiate()
	root.add_child(home)
	await process_frame
	var page: Control = home.preview
	page.first_launch()
	await _shot("1_age")
	Tutorial._save_age(page, 25)
	await _shot("2_demo_start_roll_hint", 30)
	var game = page.state.local_game
	var flow = page.state.get_meta("turn_flow")
	var faces := [3]
	flow.roll_source = func() -> int: return faces[0]
	# Put the robot's paw 3 cells ahead of the user's (same global track) to show a capture.
	var user_cell: int = int(game.state["players"][0]["tokens"][3])
	game.state["players"][1]["tokens"][3] = (user_cell + 3 + 52 - 26) % 52
	game._sync()
	LocalMatchPage._roll(page, game)
	await create_timer(1.6).timeout
	await _shot("3_tap_hint")
	if not game.legal.is_empty(): # a single legal paw is moved automatically (live)
		page.state.get_meta("board_view").pawn_selected.emit(game.legal[0])
	await create_timer(1.0).timeout
	await _shot("4_capture_hint")
	# Home lane: the user's paw on 48, roll 5 -> into the coloured lane.
	await create_timer(3.0).timeout
	game.state["players"][0]["tokens"][3] = 48
	game._sync()
	faces[0] = 5
	if game.turn == 0 and game.legal.is_empty():
		LocalMatchPage._roll(page, game)
		await create_timer(1.6).timeout
		if not game.legal.is_empty():
			page.state.get_meta("board_view").pawn_selected.emit(game.legal[0])
	await create_timer(1.0).timeout
	await _shot("5_home_lane_hint")
	page.notice("", "")
	page.dismiss()
	Tutorial.skip_dialog(page)
	await _shot("7_skip_dialog")
	page.dismiss()
	# Win: the user's last paw on 56 rolls a 1.
	await create_timer(3.0).timeout
	game.state["players"][0]["tokens"][3] = 56
	game._sync()
	faces[0] = 1
	for attempt in 40:
		if page.current != &"match":
			break
		if game.turn == 0 and game.legal.is_empty():
			LocalMatchPage._roll(page, game)
		elif game.turn == 0:
			page.state.get_meta("board_view").pawn_selected.emit(game.legal[0])
		await create_timer(0.5).timeout
	await create_timer(1.5).timeout
	await _shot("6_win_screen")
	Demo.reset()
	DirAccess.remove_absolute(ProjectSettings.globalize_path(AgeEntry.default_path))
	quit()
