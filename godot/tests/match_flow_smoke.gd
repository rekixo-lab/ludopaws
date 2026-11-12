extends SceneTree
const ResultOverlay = preload("res://scripts/ui/preview/match_result_overlay.gd")
## End-to-end smoke test through the real UI: Home -> match route -> roll / tap pawns on
## the 3D board -> results. Plays one Pass & Play and one Robot match to the end.
##   godot --headless --path . --script res://tests/match_flow_smoke.gd
## With a display (no --headless) it also saves match/results screenshots:
##   godot --path . --script res://tests/match_flow_smoke.gd -- res://.godot/preview
## Exit code 0 = both matches reached the results screen.

const LocalMatchPage = preload("res://scripts/game/local_match_page.gd")
const Board3D = preload("res://scripts/game/board3d/classic_board_3d.gd")

var failures := 0
var out_dir := ""

func _initialize() -> void:
	# Tests never touch the player's saved setup.
	load("res://scripts/game/setup/offline_setup.gd").path = "user://test_smoke_setup.cfg"
	DirAccess.remove_absolute(ProjectSettings.globalize_path("user://test_smoke_setup.cfg"))
	var args := OS.get_cmdline_user_args()
	if args.size() > 0 and DisplayServer.get_name() != "headless":
		out_dir = args[0]
	var home: Control = load("res://scenes/ui/home/home_screen.tscn").instantiate()
	root.add_child(home)
	for i in 3:
		await process_frame
	var page: Control = home.preview
	# Phone aspect (A71 1080x2400) so the edge-to-edge board layout applies.
	root.size = Vector2i(900, 2000)
	for i in 3:
		await process_frame
	# Skip move animations without touching the saved settings file.
	load("res://scripts/game/settings/game_settings.gd").overrides["reduced_motion"] = true
	load("res://scripts/game/settings/game_settings.gd").overrides["confirm_exit"] = false
	await _play(page, &"pass_and_play", 2)
	await _play(page, &"robot", 2)
	await _demo(page)
	print("Match flow smoke: %d failures" % failures)
	quit(1 if failures > 0 else 0)

func _check(ok: bool, label: String) -> void:
	if not ok:
		failures += 1
		printerr("FAIL: ", label)
	else:
		print("ok: ", label)

func _play(page: Control, mode: StringName, players: int) -> void:
	page.state.player_count = players
	page.start_match(mode)
	await process_frame
	var game = page.state.local_game
	_check(game != null and page.current == &"match", "%s match starts" % mode)
	var board = (page.state.get_meta("board_view") if page.state.has_meta("board_view") else null)
	_check(board is Board3D and board.is_inside_tree(), "%s shows the 3D board" % mode)
	await process_frame
	var rails: Dictionary = page.state.get_meta("rail_cache")
	var board_rect: Rect2 = board.get_global_rect()
	var clear := board_rect.size.x > 0
	for node in rails["cards"].values() + rails["dice"].values():
		if node.is_visible_in_tree():
			clear = clear and not node.get_global_rect().grow(-1.0).intersects(board_rect)
	_check(clear, "%s rail cards and dice never cover the board" % mode)
	_check(absf(board.size.x - page.size.x) < 2.0 and absf(board.size.y - board.size.x) < 2.0,
		"%s board spans the screen width and is square (%s vs page %s)" % [mode, board.size, page.size])
	var ui: Dictionary = page.state.get_meta("match_ui")
	var panels: Array = ui["body"].get_children().filter(func(n): return n is PanelContainer and not n is Board3D)
	_check(panels.is_empty() and is_instance_valid(ui["bar"]), "%s has no status box, only the message row" % mode)
	var hit_checked := false
	Engine.time_scale = 40.0 # robot turns wait on timers
	var actions := 0
	var handoffs := 0
	var banners := {}
	var captured_mid := false
	var moments_seen := {}
	while page.current == &"match" and ResultOverlay.current(page) == null and actions < 6000:
		var human: bool = mode != &"robot" or game.turn == 0
		var banner = page.state.get_meta("turn_banner") if page.state.has_meta("turn_banner") else null
		if is_instance_valid(banner) and not banners.has(banner.get_instance_id()):
			banners[banner.get_instance_id()] = true
		if is_instance_valid(page.overlay):
			# The PASS THE PHONE modal is opt-in now; by default it must never appear.
			handoffs += 1
			page.dismiss()
			page.refresh()
		elif human:
			board = (page.state.get_meta("board_view") if page.state.has_meta("board_view") else null)
			if game.legal.is_empty():
				LocalMatchPage._roll(page, game)
			elif not hit_checked and board._targets.has(game.legal[0]):
				# Hit-testing at the new size: a real click on the pawn's spot selects it.
				hit_checked = true
				var index: int = game.legal[0]
				var color: String = game.state["players"][game.turn]["color"]
				var pos: int = int(game.pawns[game.turn][index])
				var Geo = load("res://scripts/game/board3d/board_geometry.gd")
				var cell: float = board._edge() / 15.0
				var logical = Geo.token_center(color, index, pos, cell) if pos >= 0 else Geo.yard_token_center(color, index, cell)
				var expected: Vector2 = board._to_screen(logical)
				var target: Vector2 = board._targets[index]
				var picked := [-1]
				var probe := func(i: int): picked[0] = i
				board.pawn_selected.connect(probe)
				var click := InputEventMouseButton.new()
				click.button_index = MOUSE_BUTTON_LEFT
				click.pressed = true
				click.position = target
				board._gui_input(click)
				board.pawn_selected.disconnect(probe)
				_check(picked[0] == index and target.distance_to(expected) <= cell * 1.2 and Rect2(Vector2.ZERO, board.size).has_point(target),
					"%s hit-testing at board edge %.0f picks paw %d (target %s, cell centre %s)" % [mode, board._edge(), index, target, expected])
				if picked[0] != index:
					board.pawn_selected.emit(index)
			else:
				# Tap the pawn through the board's signal, exactly like a touch.
				board.pawn_selected.emit(game.legal[0])
			actions += 1
		await process_frame
		board = (page.state.get_meta("board_view") if page.state.has_meta("board_view") else null)
		if is_instance_valid(board):
			for r in board._active_reactions.values():
				moments_seen[r["moment"] if r["moment"] != null else ("OBSERVER" if r["observer"] else "?")] = true
		if not out_dir.is_empty() and not captured_mid and actions > 60:
			captured_mid = true
			await _capture("%s_match.png" % mode)
	Engine.time_scale = 1.0
	# Character reactions are driven by the live moment pipeline during play. Actions here
	# come faster than the director's 900 ms window, so low-priority cues are (correctly)
	# queued and outranked; sixes and home arrivals always get through.
	_check(moments_seen.has("SIX_ROLLED") and moments_seen.has("HOME_REACHED"),
		"%s pawns react to game moments (%s)" % [mode, ", ".join(moments_seen.keys())])
	if mode == &"pass_and_play":
		_check(handoffs == 0 and banners.size() > 0, "pass_and_play: turn banners (%d), no blocking handoff modal (%d)" % [banners.size(), handoffs])
	_check(game.complete, "%s match completes (%d human actions)" % [mode, actions])
	_check(page.current == &"match" and ResultOverlay.current(page) != null, "%s ends with the result overlay on the match screen (no route change)" % mode)
	var after: Rect2 = board.get_global_rect()
	_check(is_instance_valid(board) and board.is_inside_tree() and after == board_rect, "%s board keeps its exact place and size under the result (%s vs %s)" % [mode, after, board_rect])
	var overlay := ResultOverlay.current(page)
	await process_frame
	var card: Control = overlay.find_children("*", "PanelContainer", true, false)[0]
	_check(page.get_global_rect().encloses(card.get_global_rect()) and overlay.find_children("*", "ScrollContainer", true, false).is_empty(),
		"%s result card fits the screen without scrolling (%s in %s)" % [mode, card.get_global_rect(), page.get_global_rect()])
	overlay.free()
	page.finish_match()
	await process_frame
	var none_yet := ResultOverlay.current(page) == null
	await create_timer(ResultOverlay.DELAY_S + 0.1).timeout
	_check(none_yet and ResultOverlay.current(page) != null, "%s result overlay appears only after the celebration delay" % mode)
	if not out_dir.is_empty():
		await _capture("%s_results.png" % mode)

func _capture(name: String) -> void:
	await RenderingServer.frame_post_draw
	await RenderingServer.frame_post_draw
	var path := out_dir.path_join(name)
	root.get_texture().get_image().save_png(path)
	print("saved ", ProjectSettings.globalize_path(path))

## First-time Demo (NEW V2 FEATURE) through the real UI, with temporary flag files.
func _demo(page: Control) -> void:
	var Demo = load("res://scripts/game/tutorial/demo_match.gd")
	var AgeEntry = load("res://scripts/game/safety/age_entry.gd")
	var Tutorial = load("res://scripts/ui/preview/tutorial_pages.gd")
	var real_flag: String = Demo.flag_path
	var real_age: String = AgeEntry.default_path
	Demo.flag_path = "user://smoke_tutorial.cfg"
	AgeEntry.default_path = "user://smoke_age.cfg"
	Demo.reset()
	DirAccess.remove_absolute(ProjectSettings.globalize_path(AgeEntry.default_path))
	var gems_before: int = page.state.inventory.gems
	var games_before := int(page.state.setting("total_games", 0))
	page.first_launch()
	await process_frame
	_check(page.current == &"age", "first launch opens the age entry")
	Tutorial._save_age(page, 25)
	await process_frame
	_check(page.current == &"match" and page.state.match_mode == &"demo", "after age the demo match starts")
	await process_frame
	var demo_bar = page.state.get_meta("match_ui")["bar"]
	var has_skip: bool = demo_bar.get_children().any(func(n): return n is Button or n.get_class() == "Button" or (n.get_script() != null and "label" in n and str(n.label) == "Skip"))
	_check(demo_bar.pill.visible and not demo_bar.label.text.is_empty() and has_skip,
		"demo lesson + Skip show in the message row (%s)" % demo_bar.label.text)
	var game = page.state.local_game
	Engine.time_scale = 40.0
	var actions := 0
	while page.current == &"match" and ResultOverlay.current(page) == null and actions < 4000:
		if game.turn == 0:
			if game.legal.is_empty():
				LocalMatchPage._roll(page, game)
			else:
				var board = page.state.get_meta("board_view")
				board.pawn_selected.emit(game.legal[0])
			actions += 1
		await process_frame
	Engine.time_scale = 1.0
	_check(game.complete and page.current == &"match" and ResultOverlay.current(page) != null, "the demo ends with the result overlay (%d actions)" % actions)
	_check(not Demo.completed(), "quitting before Continue leaves the demo to run again")
	Tutorial.finish(page)
	await process_frame
	_check(Demo.completed() and page.current == &"play", "Continue marks the demo done and goes Home")
	_check(page.state.inventory.gems == gems_before and int(page.state.setting("total_games", 0)) == games_before,
		"the demo changes no Gems and no stats")
	page.first_launch()
	await process_frame
	_check(page.current == &"play", "the demo does not start again once completed")
	Demo.reset()
	page.first_launch()
	await process_frame
	Tutorial.finish(page) # Skip -> confirm
	_check(Demo.completed(), "skipping marks the demo done")
	Demo.reset()
	Tutorial.start_demo(page, true)
	Tutorial.finish(page)
	_check(not Demo.completed(), "Play tutorial again never changes the flag")
	Demo.reset()
	DirAccess.remove_absolute(ProjectSettings.globalize_path(AgeEntry.default_path))
	Demo.flag_path = real_flag
	AgeEntry.default_path = real_age
