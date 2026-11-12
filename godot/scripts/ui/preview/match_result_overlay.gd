extends RefCounted
## End-of-match result (V2 look, live LudoPawsResultPresentation content). The match screen
## stays exactly where it is (no route change, board / header / player cards untouched);
## once the last move and its celebration have finished, an overlay fades in over a dark
## veil with the result card: trophy, live title / subtitle, placements with portraits,
## Gems only for online / friends matches (server matchGemRewards), Play Again / Home /
## Share / Details. Back = Home. The Demo shows its own text and Continue.

const UI = preload("res://scripts/ui/preview/meadow_controls.gd")
const Results = preload("res://scripts/game/result_presentation.gd")
const Portrait = preload("res://scripts/game/characters/character_portrait.gd")
const Hints = preload("res://scripts/ui/hints/hints.gd")
const DELAY_S := 1.4
const NAME := "MatchResultOverlay"

## Called when the match completes; the overlay appears after DELAY_S on the same screen.
static func schedule(page) -> void:
	var game = page.state.local_game
	if game == null:
		return
	page.state.set_meta("result_pending", game)
	await page.get_tree().create_timer(DELAY_S).timeout
	if not is_instance_valid(page) or page.state.local_game != game or page.current != &"match":
		return
	show(page)

static func current(page) -> Control:
	return page.get_node_or_null(NAME) as Control

static func show(page) -> Control:
	var old := current(page)
	if old != null:
		old.queue_free()
	var reduced: bool = page.state.setting("reduced_motion", false)
	var root := Control.new()
	root.name = NAME
	page.add_child(root)
	root.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	var veil := ColorRect.new()
	veil.color = Color("#05100ec0")
	root.add_child(veil)
	veil.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	var center := CenterContainer.new()
	root.add_child(center)
	center.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	var frame := UI.panel(center)
	var panel := frame.get_parent() as Control
	panel.custom_minimum_size.x = minf(360.0, page.size.x - 32.0)
	frame.add_theme_constant_override("separation", 6)
	_fill(page, frame)
	# Pop-in + paw confetti; Calm motion: fade only.
	root.modulate.a = 0.0
	var tw := root.create_tween()
	tw.tween_property(root, "modulate:a", 1.0, 0.25)
	if not reduced:
		panel.pivot_offset = panel.size / 2.0
		panel.scale = Vector2(0.86, 0.86)
		tw.parallel().tween_property(panel, "scale", Vector2.ONE, 0.35).set_trans(Tween.TRANS_BACK).set_ease(Tween.EASE_OUT)
		_confetti(root, page.size)
	return root

static func _mode(page) -> String:
	match page.state.match_mode:
		&"robot": return "COMPUTER"
		&"pass_and_play": return "PASS_AND_PLAY"
		&"online":
			var s: Dictionary = page.state.local_game.state
			return "TEAM_UP" if s.get("matchMode") == "TEAM_UP" else "ONLINE"
	return "COMPUTER"

static func _viewer(page) -> String:
	if page.state.match_mode == &"online":
		var c = page.get_tree().root.get_node_or_null("OnlineMatch")
		return c.player_id if c != null else ""
	return str(page.state.local_game.state["players"][0]["playerId"])

static func _fill(page, frame: VBoxContainer) -> void:
	var state: Dictionary = page.state.local_game.state
	UI.image(frame, UI.art("match_trophy"), 78)
	if page.state.match_mode == &"demo":
		var won: bool = not page.state.local_game.finished.is_empty() and page.state.local_game.finished[0] == 0
		UI.label(frame, "Demo complete!" if won else "Demo finished", 24, true)
		UI.label(frame, ("तुम जीत गए! अब असली मैच खेलो · You won! Now try a real match." if won
			else "इस बार Robot जीता. अब तुम तैयार हो · The robot won this time. You're ready to play!"), 14, true)
		var cont := UI.button(frame, "Continue", load("res://scripts/ui/preview/tutorial_pages.gd").finish.bind(page), true, "play", 44)
		Hints.button(cont, "demo.continue", Hints.Policy.IDLE_DEMO_MS, 80, true)
		return
	var viewer := _viewer(page)
	var card: Dictionary = Results.model(state, _mode(page), viewer)
	UI.label(frame, card["title"], 24, true)
	UI.label(frame, card["subtitle"], 14, true)
	for place in card["placements"]:
		var row := UI.row(frame, 8)
		var portrait := Portrait.new()
		portrait.character_id = str(place.get("characterId", "dog"))
		portrait.custom_minimum_size = Vector2(38, 38)
		portrait.mouse_filter = Control.MOUSE_FILTER_IGNORE
		row.add_child(portrait)
		var shown := str(place["name"])
		if place["you"] and shown != "You":
			shown += " (You)"
		var name := UI.label(row, shown, 15)
		name.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		name.autowrap_mode = TextServer.AUTOWRAP_OFF
		name.text_overrun_behavior = TextServer.OVERRUN_TRIM_ELLIPSIS
		var rank := UI.label(row, str(place["label"]), 15)
		rank.autowrap_mode = TextServer.AUTOWRAP_OFF
		if place["you"]:
			name.add_theme_color_override("font_color", Color("#ffe1a1"))
	# Gems only for server matches (live: matchGemRewards from the server).
	if page.state.match_mode == &"online":
		var amount := 0
		for r in state.get("matchGemRewards", []):
			if r is Dictionary and r.get("playerId") == viewer:
				amount = int(r.get("amount", 0))
		if amount > 0:
			var gems := UI.row(frame, 6)
			gems.alignment = BoxContainer.ALIGNMENT_CENTER
			UI.image(gems, UI.icon("gem"), 22).custom_minimum_size.x = 24
			UI.label(gems, "+%d Gems" % amount, 16).autowrap_mode = TextServer.AUTOWRAP_OFF
	var Match = load("res://scripts/ui/preview/match_pages.gd")
	var actions := UI.row(frame, 6)
	var again := UI.button(actions, "Play Again", Match._after_result.bind(page, play_again.bind(page)), true, "play", 42)
	Hints.button(again, "results.play_again", Hints.Policy.IDLE_SCREEN_MS, 60)
	UI.button(actions, "Home", Match._after_result.bind(page, go_home.bind(page)), true, "", 42)
	var more := UI.row(frame, 6)
	UI.button(more, "Share", load("res://scripts/game/online/room_link.gd").share.bind(
		load("res://scripts/game/online/room_link.gd").game_share_text(), "Share Ludo Paws"), false, "friends", 38)
	UI.button(more, "Details", details.bind(page), false, "bars", 38)

static func go_home(page) -> void:
	var c = page.get_tree().root.get_node_or_null("OnlineMatch")
	if page.state.match_mode == &"online" and c != null:
		c.leave()   # a finished seat is simply forgotten
	var overlay := current(page)
	if overlay != null:
		overlay.queue_free()
	page.go_home()

## Local: the same setup again. Online: back to that mode's lobby for a new match.
static func play_again(page) -> void:
	var overlay := current(page)
	if overlay != null:
		overlay.queue_free()
	if page.state.match_mode == &"online":
		var mode: String = str(page.state.local_game.state.get("matchMode", "ONLINE"))
		go_home(page)
		page.open({"TEAM_UP": &"team_up", "FRIENDS": &"friends"}.get(mode, &"online"))
		return
	page.replay()

## Match details in a sheet (last roll + match history).
static func details(page) -> void:
	var frame: VBoxContainer = page.dialog("Match Details", "", true)
	var value: int = page.state.last_roll
	UI.label(frame, ("Last roll: %d" % value) if value > 0 else "No rolls yet", 16, true)
	var list := UI.column(frame, 4)
	var events: Array = page.state.events
	for event in events.slice(-12):
		UI.label(list, event, 13)
	if events.is_empty():
		UI.label(list, "No events recorded.", 13, true)
	UI.button(frame, "Close", page.dismiss, true, "", 40)

static func _confetti(root: Control, area: Vector2) -> void:
	var paw: Texture2D = UI.icon("paw") if ResourceLoader.exists("res://assets/ui/icons/paw.svg") else UI.icon("gem")
	for i in 14:
		var bit := TextureRect.new()
		bit.texture = paw
		bit.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
		bit.size = Vector2(18, 18)
		bit.modulate = [Color("#ffcf4d"), Color("#f08a7a"), Color("#8fd19e"), Color("#9cc4ff")][i % 4]
		bit.mouse_filter = Control.MOUSE_FILTER_IGNORE
		root.add_child(bit)
		bit.position = Vector2(randf() * area.x, -20.0)
		var tw := bit.create_tween()
		tw.tween_property(bit, "position", Vector2(bit.position.x + randf_range(-40, 40), area.y * randf_range(0.35, 0.7)), randf_range(1.1, 1.7)).set_ease(Tween.EASE_OUT)
		tw.parallel().tween_property(bit, "modulate:a", 0.0, 1.6).set_delay(0.6)
		tw.tween_callback(bit.queue_free)
