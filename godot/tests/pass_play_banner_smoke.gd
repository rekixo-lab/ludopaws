extends SceneTree
## Pass & Play turn change (V2): by default a non-blocking turn banner (right seat / colour,
## fades by itself, input never blocked); no banner on extra turns, Robot, Demo, first render
## or resume; with "Pass the phone prompt" ON the live READY modal returns and pauses the flow.
##   godot --headless --path . --script res://tests/pass_play_banner_smoke.gd

const LocalMatchPage = preload("res://scripts/game/local_match_page.gd")
const Setup = preload("res://scripts/game/setup/offline_setup.gd")
const Demo = preload("res://scripts/game/tutorial/demo_match.gd")
const AgeEntry = preload("res://scripts/game/safety/age_entry.gd")
const Profile = preload("res://scripts/game/profile/profile_store.gd")
const Weekly = preload("res://scripts/game/profile/weekly_challenge_store.gd")

var failures := 0
var checks := 0
var page: Control

func _check(ok: bool, label: String) -> void:
	checks += 1
	if not ok:
		failures += 1
		printerr("FAIL: ", label)

func _banner():
	return page.state.get_meta("turn_banner") if page.state.has_meta("turn_banner") else null

## Plays human actions until the turn seat changes (or `limit` frames). Returns the new seat.
func _until_turn_change(game, limit := 3000) -> int:
	var start: int = game.turn
	for i in limit:
		await process_frame
		if game.turn != start or game.complete:
			return game.turn
		if is_instance_valid(page.overlay):
			continue
		var flow = page.state.get_meta("turn_flow")
		if flow.can_roll(flow.now()):
			LocalMatchPage._roll(page, game)
		elif flow.can_move(flow.now()) and not game.legal.is_empty():
			page.state.get_meta("board_view").pawn_selected.emit(game.legal[0])
	return game.turn

## Live preference files (profile, weekly, wallet, settings) in a throwaway folder.
func _isolate_prefs() -> void:
	var NativePrefs = load("res://scripts/game/platform/native_prefs.gd")
	NativePrefs.desktop_dir = "user://banner_test_prefs"
	NativePrefs.reset_cache()
	for f in DirAccess.get_files_at(NativePrefs.desktop_dir):
		DirAccess.remove_absolute(ProjectSettings.globalize_path(NativePrefs.desktop_dir.path_join(f)))

func _initialize() -> void:
	Demo.flag_path = "user://banner_test_tutorial.cfg"
	AgeEntry.default_path = "user://banner_test_age.cfg"
	AgeEntry.new().save_choice(30)
	Demo.mark_completed()
	Setup.path = "user://banner_test_setup.cfg"
	_isolate_prefs()
	DirAccess.remove_absolute(ProjectSettings.globalize_path(Setup.path))
	root.size = Vector2i(900, 2000)
	var home: Control = load("res://scenes/ui/home/home_screen.tscn").instantiate()
	root.add_child(home)
	for i in 3:
		await process_frame
	page = home.preview
	load("res://scripts/game/settings/game_settings.gd").overrides["reduced_motion"] = true
	load("res://scripts/game/settings/game_settings.gd").overrides["confirm_exit"] = false
	load("res://scripts/game/settings/game_settings.gd").overrides["pass_phone_prompt"] = false
	Engine.time_scale = 20.0
	# Default: banner, no modal, flow never paused.
	page.state.player_count = 4
	page.start_match(&"pass_and_play")
	await process_frame
	var game = page.state.local_game
	_check(not is_instance_valid(_banner()), "no banner on the first render")
	var seen_banner := false
	var seat_ok := true
	var modal := false
	var changes := 0
	for turn in 4:
		var was: int = game.turn
		var next: int = await _until_turn_change(game)
		if next != was:
			changes += 1
		for i in 30:
			await process_frame
			modal = modal or is_instance_valid(page.overlay)
			var b = _banner()
			if is_instance_valid(b):
				seen_banner = true
				seat_ok = seat_ok and b.seat == game.turn and b.color == game.state["players"][game.turn]["color"] and b.mouse_filter == Control.MOUSE_FILTER_IGNORE
				break
	_check(seen_banner and seat_ok, "a turn banner names the next seat in its colour and never takes input")
	_check(not modal, "default: no PASS THE PHONE modal")
	var b2 = _banner()
	var gone := false
	for i in 400:
		await process_frame
		if not is_instance_valid(b2):
			gone = true
			break
	_check(gone, "the banner fades out by itself")
	_check(changes == 4 and not modal, "the flow never waits for a handoff: 4 turn changes with no modal (%d)" % changes)
	# Resume (a new turn flow / page rebuild) never shows a banner by itself.
	var Page = load("res://scripts/game/local_match_page.gd")
	page.refresh()
	for i in 20:
		await process_frame
	_check(not is_instance_valid(_banner()), "resume / refresh: no banner")
	# Extra turn: same seat again -> no banner (policy: the turn seat did not change).
	var Handoff = load("res://scripts/game/match/pass_and_play_handoff.gd")
	var snap := {"matchId": "m", "status": "ACTIVE", "turnSeat": 1, "pendingRoll": null, "players": [{"displayName": "A", "color": "RED"}, {"displayName": "B", "color": "BLUE"}]}
	_check(Handoff.resolve("PASS_AND_PLAY", snap, snap.duplicate()) == null, "extra turn (same seat) -> no banner")
	_check(Handoff.resolve("ROBOT", snap, snap.merged({"turnSeat": 0}, true)) == null, "Robot mode -> no banner")
	page.reset_match()
	# Robot and Demo never show it.
	for mode in [&"robot", &"demo"]:
		page.go_home()
		page.state.player_count = 2
		page.start_match(mode)
		await process_frame
		var g = page.state.local_game
		await _until_turn_change(g)
		for i in 20:
			await process_frame
		_check(not is_instance_valid(_banner()) or _banner().get_parent() == null, "%s: no turn banner" % mode)
		page.reset_match()
	# Setting ON: the live READY modal returns and pauses the flow until READY.
	page.state.set_setting("pass_phone_prompt", true)
	_check(page.state.setting("pass_phone_prompt", false) == true, "the setting is saved")
	page.go_home()
	page.state.player_count = 2
	page.start_match(&"pass_and_play")
	await process_frame
	game = page.state.local_game
	var before: int = await _until_turn_change(game)
	var opened := false
	for i in 60:
		await process_frame
		if is_instance_valid(page.overlay):
			opened = true
			break
	_check(opened, "setting ON: PASS THE PHONE / READY modal appears")
	if opened:
		var flow2 = page.state.get_meta("turn_flow")
		var rolled := LocalMatchPage._active(page, game)
		_check(not rolled, "setting ON: the flow waits while the modal is open")
		page.dismiss()
	page.state.set_setting("pass_phone_prompt", false)
	page.reset_match()
	Engine.time_scale = 1.0
	_isolate_prefs()
	DirAccess.remove_absolute(ProjectSettings.globalize_path(Setup.path))
	print("Pass & Play banner smoke: %d checks, %d failures" % [checks, failures])
	quit(1 if failures > 0 else 0)
