extends SceneTree
## Robot / Pass & Play setup through the real UI (docs/SETUP_PARITY.md):
## one screen (no scrolling), Start Game on screen; a locked Paw opens the preview and is
## never assigned; Paw and colour swaps change exactly two seats without rebuilding the
## page; the match gets every seat's Paw and colour (cards and board); rematch keeps them;
## the setup is saved.
##   godot --headless --path . --script res://tests/setup_smoke.gd

const Demo = preload("res://scripts/game/tutorial/demo_match.gd")
const AgeEntry = preload("res://scripts/game/safety/age_entry.gd")
const Setup = preload("res://scripts/game/setup/offline_setup.gd")

var failures := 0
var checks := 0

func _check(ok: bool, label: String) -> void:
	checks += 1
	if not ok:
		failures += 1
		printerr("FAIL: ", label)

func _frames(n := 3) -> void:
	for i in n:
		await process_frame

## Live preference files in a throwaway folder.
func _isolate_prefs() -> void:
	var NativePrefs = load("res://scripts/game/platform/native_prefs.gd")
	NativePrefs.desktop_dir = "user://setup_test_prefs"
	NativePrefs.reset_cache()
	for f in DirAccess.get_files_at(NativePrefs.desktop_dir):
		DirAccess.remove_absolute(ProjectSettings.globalize_path(NativePrefs.desktop_dir.path_join(f)))

func _initialize() -> void:
	Demo.flag_path = "user://setup_test_tutorial.cfg"
	AgeEntry.default_path = "user://setup_test_age.cfg"
	AgeEntry.new().save_choice(30)
	Demo.mark_completed()
	Setup.path = "user://setup_test_offline.cfg"
	DirAccess.remove_absolute(ProjectSettings.globalize_path(Setup.path))
	_isolate_prefs()
	root.size = Vector2i(900, 2000)
	var home: Control = load("res://scenes/ui/home/home_screen.tscn").instantiate()
	root.add_child(home)
	await _frames()
	var page: Control = home.preview
	load("res://scripts/game/settings/game_settings.gd").overrides["confirm_exit"] = false
	var screen: Rect2 = root.get_visible_rect()
	for route in [&"robot", &"pass_and_play"]:
		for n in [2, 3, 4]:
			page.go_home()
			page.open(route)
			var view = page.state.get_meta("setup_view")
			view._on_count(n)
			await _frames()
			var scrolls := page.find_children("*", "ScrollContainer", true, false).filter(func(sc): return sc.is_visible_in_tree() and not sc.horizontal)
			var starts: Array = page.find_children("*", "MeadowButton", true, false).filter(func(b): return b.label == "Start Game" and b.is_visible_in_tree())
			var start: Control = starts[0]
			# Headless windows are square; the A71 page is 955 logical units tall (20:9). The
			# fixed column's minimum height must fit, so nothing needs scrolling there.
			var column: Control = page.get_node("Page")
			var needed: float = column.get_combined_minimum_size().y + 20.0
			_check(scrolls.is_empty() and needed <= 955.0 and start.is_visible_in_tree(),
				"%s %dp: fits one A71 screen (needs %.0f of 955), Start Game fixed" % [route, n, needed])
	# Pass & Play, 4 players: swaps and no rebuild.
	page.go_home()
	page.open(&"pass_and_play")
	var v = page.state.get_meta("setup_view")
	v._on_count(4)
	var body: Control = page.body
	var chips_row: Control = v._chips_row
	var before_colors: Array = v.s["colors"].duplicate()
	v._on_seat(2)
	v._on_color(before_colors[0])
	var after_colors: Array = v.s["colors"]
	var changed := range(4).filter(func(i): return after_colors[i] != before_colors[i])
	_check(changed == [0, 2] and after_colors[2] == before_colors[0] and after_colors[0] == before_colors[2],
		"colour swap changes exactly the two seats (%s -> %s)" % [before_colors, after_colors])
	var before_paws: Array = v.s["paws"].duplicate()
	v._on_paw(before_paws[0])
	var after_paws: Array = v.s["paws"]
	_check(after_paws[2] == before_paws[0] and after_paws[0] == before_paws[2] and after_paws[1] == before_paws[1] and after_paws[3] == before_paws[3],
		"picking another seat's Paw swaps the two seats (%s -> %s)" % [before_paws, after_paws])
	_check(page.body == body and v._chips_row == chips_row and is_instance_valid(chips_row), "selections update in place (no page rebuild)")
	# Locked Paw: preview, never assigned.
	var locked := ""
	for id in v._cards:
		if not page.state.inventory.unlocked_character_ids().has(id):
			locked = id
			break
	_check(not locked.is_empty(), "there is a locked Paw to try")
	var paws_before_locked: Array = v.s["paws"].duplicate()
	v._on_paw(locked)
	await _frames()
	_check(is_instance_valid(page.overlay) and v.s["paws"] == paws_before_locked and not v.s["paws"].has(locked),
		"a locked Paw opens the preview and is never assigned")
	var unlock_btn: Array = page.overlay.find_children("*", "MeadowButton", true, false).filter(func(b): return b.label == "Unlock in Shop")
	_check(unlock_btn.size() == 1, "the preview offers Unlock in Shop")
	if unlock_btn.size() == 1:
		unlock_btn[0].pressed.emit()
		await _frames(4)
		_check(page.current == &"shop" and is_instance_valid(page.overlay), "Unlock in Shop opens the Shop with that Paw's unlock chooser")
		page.dismiss()
	# The match gets the setup; rematch keeps it.
	page.go_home()
	page.open(&"pass_and_play")
	v = page.state.get_meta("setup_view")
	var setup: Dictionary = v.s.duplicate(true)
	page.start_match(&"pass_and_play")
	await _frames(4)
	var game = page.state.local_game
	var ok: bool = game != null and game.state["players"].size() == setup["player_count"]
	if ok:
		for seat in setup["player_count"]:
			var p: Dictionary = game.state["players"][seat]
			ok = ok and p["color"] == setup["colors"][seat] and p["characterId"] == setup["paws"][seat] and p["displayName"] == setup["names"][seat]
	_check(ok, "Pass & Play match gets every seat's Paw, colour and name")
	var cards: Dictionary = page.state.get_meta("rail_cache")["cards"]
	var cards_ok := true
	for seat in setup["player_count"]:
		cards_ok = cards_ok and cards[seat].portrait.character_id == setup["paws"][seat] and cards[seat].portrait.team == setup["colors"][seat]
	_check(cards_ok, "player cards show each seat's Paw and colour")
	var board = page.state.get_meta("board_view")
	var pawns: Array = board._pawn_states(board.scene_state(), Time.get_ticks_msec())
	var board_ok := not pawns.is_empty()
	for p in pawns:
		board_ok = board_ok and p["character_id"] == setup["paws"][int(p["seat"])]
	_check(board_ok, "board pawns use each seat's Paw")
	page.replay()
	await _frames(3)
	var again = page.state.local_game
	_check(again != game and again.state["players"].map(func(p): return p["characterId"]) == setup["paws"]
		and again.state["players"].map(func(p): return p["color"]) == setup["colors"], "rematch keeps the same Paws and colours")
	page.reset_match()
	# Robot: mixed humans, colours from the human's choice, persistence across a reload.
	page.go_home()
	page.open(&"robot")
	v = page.state.get_meta("setup_view")
	v._on_count(2)
	v._on_count(4)
	v._on_count(4)
	v._on_color("RED")
	var robot_setup: Dictionary = v.s.duplicate(true)
	_check(robot_setup["humans"] == 2 and robot_setup["colors"] == Setup.colors_for(4, "RED"), "robot: re-tap gives 2 humans; CPU colours follow yours")
	page.start_match(&"robot")
	await _frames(3)
	game = page.state.local_game
	_check(game.state["players"].map(func(p): return p["playerType"]) == ["HUMAN", "HUMAN", "BOT", "BOT"]
		and game.state["players"].map(func(p): return p["color"]) == Setup.colors_for(4, "RED"), "robot match: 2 humans + 2 robots in live seat order")
	page.reset_match()
	var reloaded: Dictionary = Setup.load_setup("COMPUTER", page.state.inventory.unlocked_character_ids(), "dog")
	_check(reloaded["humans"] == 2 and reloaded["colors"][0] == "RED" and reloaded["player_count"] == 4, "robot setup is saved")
	DirAccess.remove_absolute(ProjectSettings.globalize_path(Setup.path))
	print("Setup smoke: %d checks, %d failures" % [checks, failures])
	quit(1 if failures > 0 else 0)
