extends RefCounted

const Hints = preload("res://scripts/ui/hints/hints.gd")

const LocalMatch = preload("res://scripts/game/local_match_page.gd")
const Results = preload("res://scripts/game/result_presentation.gd")
const UI = preload("res://scripts/ui/preview/meadow_controls.gd")
const Tutorial = preload("res://scripts/ui/preview/tutorial_pages.gd")
const BOARD = preload("res://scenes/main.tscn")
const NAMES := {
	&"online": "Online Match", &"team_up": "Team Up", &"friends": "Friends Match",
	&"robot": "Robot Match", &"pass_and_play": "Pass & Play", &"demo": "Tutorial",
}
const ICONS := {
	&"online": "globe", &"team_up": "team", &"friends": "friends",
	&"robot": "robot", &"pass_and_play": "pass_play", &"demo": "paw",
}
const DieFace = preload("res://scripts/ui/preview/die_face.gd")
const ANIMALS := [0, 1, 2, 5]

static func mode_title(mode: StringName) -> String:
	return NAMES[mode]

static func mode_icon(mode: StringName) -> String:
	return ICONS[mode]

static func build(page) -> void:
	match page.current:
		&"match": _match(page)
		&"results": _results(page)
		&"details": _details(page)

static func _board(parent: Control, dimmed := false) -> SubViewportContainer:
	var frame := PanelContainer.new()
	frame.add_theme_stylebox_override("panel", UI.surface(Color("#2b1a10"), Color("#e2b36e"), 18))
	parent.add_child(frame)
	var display := SubViewportContainer.new()
	display.custom_minimum_size = Vector2(0, 355)
	display.stretch = true
	display.mouse_filter = Control.MOUSE_FILTER_IGNORE
	frame.add_child(display)
	var viewport := SubViewport.new()
	viewport.own_world_3d = true
	viewport.gui_disable_input = true
	viewport.render_target_update_mode = SubViewport.UPDATE_WHEN_VISIBLE
	display.add_child(viewport)
	var board := BOARD.instantiate()
	viewport.add_child(board)
	board.get_node("PrototypeHUD").hide()
	var camera := viewport.get_camera_3d()
	camera.position = Vector3(0, 25, 0)
	camera.look_at(Vector3.ZERO, Vector3.FORWARD)
	camera.size = 16.4
	var key_light: DirectionalLight3D = board.get_node("SoftKeyLight")
	key_light.shadow_enabled = false
	key_light.light_energy = 0.6
	var fill_light: OmniLight3D = board.get_node("SoftFillLight")
	fill_light.light_energy = 0.28
	var environment := Environment.new()
	environment.background_mode = Environment.BG_COLOR
	environment.background_color = Color("#231c16")
	environment.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
	environment.ambient_light_color = Color("#f4e0c6")
	environment.ambient_light_energy = 0.52
	camera.environment = environment
	if dimmed:
		var shade := ColorRect.new()
		shade.color = Color("#03170ead")
		shade.mouse_filter = Control.MOUSE_FILTER_IGNORE
		display.add_child(shade)
		shade.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	return display

static func _name(page, index: int) -> String:
	if page.state.match_mode in [&"robot", &"demo"] and index > 0:
		return "Robot %d" % index
	var value: String = page.state.player_names[index].strip_edges()
	return value if not value.is_empty() else "Player %d" % (index + 1)

static func _players(page, parent: Control, start: int, total: int) -> void:
	var profiles := UI.row(parent)
	var selected_seat: int = maxi(0, page.state.team_seats.find(true))
	for i in range(start, mini(total, start + 2)):
		var profile := UI.row(UI.panel(profiles))
		var portrait := UI.image(profile, UI.animal(ANIMALS[i]), 37)
		portrait.custom_minimum_size.x = 38
		var text := UI.column(profile, 0)
		text.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		var name_label := UI.label(text, _name(page, i), 14)
		name_label.autowrap_mode = TextServer.AUTOWRAP_OFF
		name_label.text_overrun_behavior = TextServer.OVERRUN_TRIM_ELLIPSIS
		if page.state.match_mode == &"team_up":
			var own_team := (i < 2) == (selected_seat < 2)
			var team := "RED" if i < 2 else "BLUE"
			var role := " · YOU" if i == selected_seat else (" · PARTNER" if own_team else "")
			var role_label := UI.label(text, team + role, 11)
			role_label.autowrap_mode = TextServer.AUTOWRAP_OFF

static func _player_count(page) -> int:
	match page.state.match_mode:
		&"online":
			return page.state.online_count
		&"friends", &"team_up":
			return 4
		_:
			return page.state.player_count

static func _match(page) -> void:
	var body: VBoxContainer = page.body
	var mode: StringName = page.state.match_mode
	var count := _player_count(page)
	_players(page, body, 0, count)
	_board(body)
	if count > 2:
		_players(page, body, 2, count)
	var controls := UI.panel(body)
	var roll: int = page.state.last_roll
	var turn: int = page.state.turn % count
	var status := "%s’s turn" % _name(page, turn)
	if mode == &"pass_and_play" and roll > 0:
		status = "Next Player · %s" % _name(page, turn)
	elif mode == &"team_up":
		status = "Partner handoff · Preview"
	UI.label(controls, status, 20, true)
	if roll > 0:
		_die(controls, roll)
	if mode == &"pass_and_play" and page.state.handoff_pending:
		UI.button(controls, "Pass to %s" % _name(page, turn), _handoff.bind(page), true, "pass_play")
	else:
		UI.button(controls, "Roll Preview", _roll.bind(page, count), true, "", 48)
	UI.label(controls, "Visual preview", 13, true)
	UI.button(body, "Preview Match Results", page.finish_match)
	if mode == &"friends":
		UI.button(body, "Syncing Match · Retry", page.notice.bind("Match Sync", "This sample is running locally. Live sync will be connected in the gameplay phase."))

static func _roll(page, count: int) -> void:
	var value := randi_range(1, 6)
	page.state.last_roll = value
	var index: int = page.state.turn % count
	page.state.events.push_front("%s rolled %d" % [_name(page, index), value])
	if page.state.events.size() > 8:
		page.state.events.pop_back()
	page.state.turn = (index + 1) % count
	page.state.handoff_pending = page.state.match_mode == &"pass_and_play"
	page.refresh()

static func _handoff(page) -> void:
	page.state.handoff_pending = false
	page.refresh()

static func _die(parent: Control, value: int) -> void:
	var face := DieFace.new()
	face.value = value
	face.custom_minimum_size = Vector2(47, 47)
	face.size_flags_horizontal = Control.SIZE_SHRINK_CENTER
	parent.add_child(face)

static func save_dialog(page) -> void:
	var message := "Continue this match later from Home." if page.state.local_game != null else "Keep this preview for the next time you tap Continue Match?"
	var actions: VBoxContainer = page.dialog("Save Match", message, true)
	var lantern := UI.image(actions, UI.icon("lantern"), 76)
	actions.move_child(lantern, 1)
	UI.button(actions, "Save & Exit", _exit.bind(page, true), true)
	UI.button(actions, "Keep Playing", _keep_playing.bind(page), false, "play")
	UI.button(actions, "Discard Match", _exit.bind(page, false), false)

## Live OnlineHeaderUi exit dialog: leaving an unfinished match is a server-side forfeit.
static func leave_online_dialog(page) -> void:
	var c = page.get_tree().root.get_node_or_null("OnlineMatch")
	var active: bool = c != null and c.state.get("status") == "ACTIVE"
	if c == null or c.state.get("status") == "FINISHED":
		_leave_online(page)
		return
	var actions: VBoxContainer = page.dialog("Leave match?", "Leaving attempts a server-side forfeit. Ranked results are saved only after the server confirms the match is finished."
		if active else "This match is not confirmed as finished. Leaving attempts a server-side forfeit if it is active.", true)
	UI.button(actions, "Keep Playing", page.dismiss, true, "play")
	UI.button(actions, "Leave Match", _leave_online.bind(page), false)

static func _leave_online(page) -> void:
	var c = page.get_tree().root.get_node_or_null("OnlineMatch")
	if c != null and not c.leave():
		page.dismiss()
		return
	page.dismiss()
	page.reset_match()
	page.state.saved_preview = false
	page.go_home()

static func _keep_playing(page) -> void:
	page.dismiss()
	if page.state.local_game != null:
		page.refresh()

static func _exit(page, save: bool) -> void:
	page.state.saved_preview = save
	var game = page.state.local_game
	if game != null and page.state.match_mode in [&"robot", &"pass_and_play"]:
		var mode := "COMPUTER" if page.state.match_mode == &"robot" else "PASS_AND_PLAY"
		var SavedMatch = load("res://scripts/game/match/saved_match_store.gd")
		if save:
			SavedMatch.save(mode, game.state)
		else:
			SavedMatch.clear(mode)   # live DISCARD MATCH
	if not save:
		page.reset_match()
	page.go_home()

static func _results(page) -> void:
	if page.state.local_game != null:
		_local_results(page)
		return
	var body: VBoxContainer = page.body
	var display := _board(body, true)
	display.custom_minimum_size.y = 300
	var layout := CenterContainer.new()
	display.add_child(layout)
	layout.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	var badge := UI.panel(layout)
	badge.get_parent().custom_minimum_size.x = 265
	UI.image(badge, UI.art("match_trophy"), 105)
	var teams: bool = page.state.match_mode == &"team_up"
	var banner := UI.label(badge, "YOUR TEAM WON" if teams else "MATCH COMPLETE", 23, true)
	banner.add_theme_color_override("font_color", Color("#12382b"))
	var parchment := MeadowStyle.cream_field()
	parchment.set_content_margin_all(8)
	banner.add_theme_stylebox_override("normal", parchment)
	_result_place(body, "1st", "Your Team" if teams else _name(page, 0), true)
	_result_place(body, "2nd", "Opposing Team" if teams else _name(page, 1), false)
	var reward := UI.row(UI.panel(body), 7)
	reward.alignment = BoxContainer.ALIGNMENT_CENTER
	UI.image(reward, UI.icon("gem"), 24).custom_minimum_size.x = 25
	UI.label(reward, "+3 Gems · Preview", 16, true).autowrap_mode = TextServer.AUTOWRAP_OFF
	_result_actions(page)

static func _result_place(body: Control, place: String, player: String, winner: bool) -> void:
	var card := UI.panel(body, "", Color("#245526") if winner else Color("#1d3b3e"))
	var row := UI.row(card, 8)
	UI.image(row, UI.icon("trophy"), 20).custom_minimum_size.x = 21
	UI.label(row, place, 16).autowrap_mode = TextServer.AUTOWRAP_OFF
	UI.label(row, player, 17).size_flags_horizontal = Control.SIZE_EXPAND_FILL

static func _result_actions(page) -> void:
	var body: VBoxContainer = page.body
	UI.button(body, "Match Details   ›", page.open.bind(&"details"))
	var actions := UI.row(body, 5)
	UI.button(actions, "Share", page.copy_text.bind("Ludo Paws · " + mode_title(page.state.match_mode)), true)
	Hints.button(UI.button(actions, "Play Again", _after_result.bind(page, page.replay), true, "play"), "results.play_again", Hints.Policy.IDLE_SCREEN_MS, 60)
	UI.button(actions, "Home", _after_result.bind(page, page.go_home), true)

## Live PostWinInterstitialPolicy + exit gate: leaving a finished match (local with a
## winner, online classic with a winner, Team Up with a winning team) may show one
## post-win ad; the Demo never does. Navigation never waits for the network.
static func _after_result(page, next: Callable) -> void:
	var game = page.state.local_game
	var s: Dictionary = game.state if game != null else {}
	var eligible: bool = page.state.match_mode != &"demo" and s.get("status") == "FINISHED" \
		and (s.get("winnerTeamId") in ["A", "B"] if s.get("matchMode") == "TEAM_UP" else str(s.get("winnerPlayerId", "")) != "")
	if eligible:
		await page.state.ads.post_win_exit(str(s.get("matchId", "")))
	if is_instance_valid(page):
		next.call()

static func _details(page) -> void:
	var body: VBoxContainer = page.body
	var last := UI.panel(body, "Last Roll", Color.TRANSPARENT, "dice")
	var value: int = page.state.last_roll
	if value > 0:
		var roll := UI.row(last)
		roll.alignment = BoxContainer.ALIGNMENT_CENTER
		_die(roll, value)
		var reading := UI.label(roll, "Roll %d" % value, 19, true)
		reading.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	else:
		UI.label(last, "No rolls yet", 20, true)
	var events := UI.panel(body, "Event History", Color.TRANSPARENT, "bars")
	if page.state.events.is_empty():
		UI.label(events, "Roll the preview die to see events here.", 16)
	for event in page.state.events:
		events.add_child(HSeparator.new())
		UI.label(events, event, 16)
	UI.button(body, "Back to Results", page.back, true)


static func _local_results(page) -> void:
	LocalMatch.build(page, true)
	if page.state.match_mode == &"demo":
		var game = page.state.local_game
		Tutorial.results(page, not game.finished.is_empty() and game.finished[0] == 0)
		return
	var body: VBoxContainer = page.body
	var result := UI.panel(body)
	UI.image(result, UI.art("match_trophy"), 105)
	# LudoPawsResultPresentation: live title / subtitle / placements; the viewer is the first player.
	var state: Dictionary = page.state.local_game.state
	var mode := "COMPUTER" if page.state.match_mode == &"robot" else "PASS_AND_PLAY"
	var card: Dictionary = Results.model(state, mode, state["players"][0]["playerId"])
	UI.label(result, card["title"], 25, true)
	UI.label(result, card["subtitle"], 16, true)
	UI.label(body, "Final Standings", 21, true)
	for place in card["placements"]:
		_result_place(body, place["label"], place["name"], place["you"])
	_result_actions(page)
