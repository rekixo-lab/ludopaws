extends SceneTree
## Tap hand guidance through the real UI (docs/TAP_HINT_MAP.md):
## - after the idle time the hand points at the own dice; any input hides it at once;
## - never on a robot turn or while a dialog covers the target;
## - fingertip on the dice and the hand fully on screen for 2/3/4 players and every
##   board rotation (Pass & Play colour choices);
## - Play hints off: no hand (the Demo still shows its lesson hand).
##   godot --headless --path . --script res://tests/tap_hint_smoke.gd

const Demo = preload("res://scripts/game/tutorial/demo_match.gd")
const AgeEntry = preload("res://scripts/game/safety/age_entry.gd")
const Setup = preload("res://scripts/game/setup/offline_setup.gd")

var failures := 0
var checks := 0
var g: Node
var page: Control

func _check(ok: bool, label: String) -> void:
	checks += 1
	if not ok:
		failures += 1
		printerr("FAIL: ", label)

func _frames(n := 3) -> void:
	for i in n:
		await process_frame

## Waits (fast clock) until the hand shows `key`, up to `ms` of guidance time.
func _wait_hand(key: String, ms := 9000.0) -> bool:
	var until: float = g.clock_ms + ms
	while g.clock_ms < until:
		await process_frame
		g.evaluate()
		if g.showing_key() == key:
			return true
	return false

## Live preference files in a throwaway folder.
func _isolate_prefs() -> void:
	var NativePrefs = load("res://scripts/game/platform/native_prefs.gd")
	NativePrefs.desktop_dir = "user://hint_test_prefs"
	NativePrefs.reset_cache()
	for f in DirAccess.get_files_at(NativePrefs.desktop_dir):
		DirAccess.remove_absolute(ProjectSettings.globalize_path(NativePrefs.desktop_dir.path_join(f)))

func _initialize() -> void:
	Demo.flag_path = "user://hint_test_tutorial.cfg"
	AgeEntry.default_path = "user://hint_test_age.cfg"
	Setup.path = "user://hint_test_setup.cfg"
	DirAccess.remove_absolute(ProjectSettings.globalize_path(Setup.path))
	_isolate_prefs()
	AgeEntry.new().save_choice(30)
	Demo.mark_completed()
	root.size = Vector2i(900, 2000)
	var home: Control = load("res://scenes/ui/home/home_screen.tscn").instantiate()
	root.add_child(home)
	await _frames()
	page = home.preview
	g = root.get_node_or_null("Guidance")
	_check(g != null, "Guidance autoload is present")
	if g == null:
		quit(1)
		return
	load("res://scripts/game/settings/game_settings.gd").overrides["confirm_exit"] = false
	load("res://scripts/game/settings/game_settings.gd").overrides["play_hints"] = true
	Engine.time_scale = 8.0
	# 2/3/4 players and every rotation: Pass & Play first-colour choices rotate the board.
	for setup in [[&"robot", 2, 0], [&"robot", 3, 0], [&"robot", 4, 0], [&"pass_and_play", 4, 0], [&"pass_and_play", 4, 1],
			[&"pass_and_play", 4, 2], [&"pass_and_play", 4, 3]]:
		page.go_home()
		page.state.player_count = setup[1]
		if setup[0] == &"pass_and_play":
			# Rotate the board: P1's colour decides the bottom-left yard.
			var C: Array = Setup.COLORS
			Setup.save_setup("PASS_AND_PLAY", {"player_count": 4, "colors": [C[setup[2]], C[(setup[2] + 1) % 4], C[(setup[2] + 2) % 4], C[(setup[2] + 3) % 4]]},
				page.state.inventory.unlocked_character_ids())
		page.start_match(setup[0])
		g.policy = load("res://scripts/ui/hints/tap_hint_policy.gd").new()  # fresh session caps per case
		await _frames(4)
		var label := "%s %dp colour %d" % [setup[0], setup[1], setup[2]]
		var shown := await _wait_hand("match.dice")
		_check(shown, "%s: the hand points at the own dice after idle" % label)
		if shown:
			var dice: Control = page.state.get_meta("rail_cache")["dice"][page.state.local_game.turn].get_parent()
			var tip: Vector2 = g.hand.fingertip()
			var screen: Rect2 = root.get_visible_rect()
			_check(dice.get_global_rect().grow(2.0).has_point(tip), "%s: fingertip on the dice (%s in %s)" % [label, tip, dice.get_global_rect()])
			_check(screen.encloses(g.hand.hand_rect()), "%s: hand fully on screen (%s)" % [label, g.hand.hand_rect()])
			_check(not g.hand._image.flip_v, "%s: the hand is never upside down" % label)
			var tap := InputEventMouseButton.new()
			tap.button_index = MOUSE_BUTTON_LEFT
			tap.pressed = true
			tap.position = Vector2(5, 5)
			root.push_input(tap, true)
			var up := tap.duplicate()
			up.pressed = false
			root.push_input(up, true)
			await process_frame
			_check(g.showing_key() == "", "%s: any input hides the hand at once" % label)
		page.reset_match()
	# Robot turn: never on the robot's dice.
	g.policy = load("res://scripts/ui/hints/tap_hint_policy.gd").new()
	page.go_home()
	page.state.player_count = 2
	page.start_match(&"robot")
	await _frames(3)
	var game = page.state.local_game
	var robot_seen := false
	var steps := 0
	while steps < 4000 and not game.complete:
		steps += 1
		await process_frame
		g.evaluate()
		if game.turn != 0 and g.showing_key() == "match.dice":
			robot_seen = true
			break
		if game.turn == 0:
			var flow = page.state.get_meta("turn_flow")
			if flow.can_roll(flow.now()):
				load("res://scripts/game/local_match_page.gd")._roll(page, game)
			elif flow.can_move(flow.now()):
				page.state.get_meta("board_view").pawn_selected.emit(game.legal[0])
		if steps > 600:
			break
	_check(not robot_seen, "never on a robot turn")
	# Dialog over the match: the dice behind it is not hinted.
	page.notice("Note", "Dialog over the match")
	await _frames(2)
	var until: float = g.clock_ms + 8000.0
	var behind := false
	while g.clock_ms < until:
		await process_frame
		g.evaluate()
		if g.showing_key() == "match.dice":
			behind = true
	_check(not behind and g.showing_key() == "dialog.ok", "a dialog hides targets behind it; its OK gets the hand (%s)" % g.showing_key())
	page.dismiss()
	page.reset_match()
	# Play hints off: nothing in a normal match, the Demo lesson still shows.
	load("res://scripts/game/settings/game_settings.gd").overrides["play_hints"] = false
	page.go_home()
	page.start_match(&"robot")
	await _frames(3)
	_check(not await _wait_hand("match.dice", 8000.0), "Play hints off: no hand in a normal match")
	page.reset_match()
	page.start_match(&"demo")
	await _frames(3)
	_check(await _wait_hand("match.dice", 6000.0), "Play hints off: the Demo still shows its lesson hand")
	page.reset_match()
	load("res://scripts/game/settings/game_settings.gd").overrides["play_hints"] = true
	Engine.time_scale = 1.0
	print("Tap hint smoke: %d checks, %d failures" % [checks, failures])
	quit(1 if failures > 0 else 0)
