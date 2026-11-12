extends Control

const Hints = preload("res://scripts/ui/hints/hints.gd")
const Online = preload("res://scripts/game/online/online_status.gd")
const ResultOverlay = preload("res://scripts/ui/preview/match_result_overlay.gd")
## Connects the finalized Home entry points to the Moonlit Meadow UI previews.

signal route_changed(route: StringName)

const UI = preload("res://scripts/ui/preview/meadow_controls.gd")
const State = preload("res://scripts/ui/preview/preview_state.gd")
const Lobby = preload("res://scripts/ui/preview/lobby_pages.gd")
const LocalMatch = preload("res://scripts/game/local_match_page.gd")
const Match = preload("res://scripts/ui/preview/match_pages.gd")
const Hub = preload("res://scripts/ui/preview/hub_pages.gd")
const Tutorial = preload("res://scripts/ui/preview/tutorial_pages.gd")

const TITLES := {
	&"online": ["Online", "globe"], &"team_up": ["Team Up", "team"],
	&"friends": ["Friends", "friends"], &"robot": ["Play Robot", "robot"],
	&"pass_and_play": ["Pass & Play", "pass_play"], &"room": ["Room Invite", "friends"],
	&"match": ["", ""], &"results": ["Match Results", "coin"],
	&"details": ["Match Details", "ludo_board"], &"settings": ["Settings", "paw"],
	&"profile": ["Profile", ""], &"badges": ["Badges", ""],
	&"leaderboard": ["Leaderboard", ""], &"invite": ["Invite Friends", ""],
	&"rewards": ["Rewards", "gift"], &"collections": ["Collections", "gift"],
	&"shop": ["Shop", "shop"], &"wallet": ["Wallet", "gem"], &"age": ["Welcome", "paw"],
}
const WoodIconButton := preload("res://scripts/ui/kit/wood_icon_button.gd")
const ModePlaque := preload("res://scripts/ui/kit/mode_plaque.gd")
const VinePageFrame := preload("res://scripts/ui/kit/vine_page_frame.gd")
const VineTitle := preload("res://scripts/ui/kit/vine_title.gd")
const ProfileHeader = preload("res://scripts/ui/kit/profile_header.gd")
const FRAMED_ROUTES := [&"profile", &"badges", &"leaderboard", &"invite", &"settings", &"room", &"details"]
const TABS := [&"play", &"rewards", &"shop", &"profile"]

var state := State.new()
var history: Array[StringName] = []
var current: StringName = &"play"
var scroll: ScrollContainer
var body: VBoxContainer
var overlay: Control
var _parked_match: Control
var scenery: TextureRect
var top_inset := 0.0:
	set(value):
		top_inset = value
		if is_instance_valid(scenery):
			scenery.offset_top = -top_inset

func open(route: StringName) -> void:
	if route == &"continue_match":
		start_match(state.match_mode, state.saved_preview)
		return
	if route in TABS:
		history.clear()
		_show(route)
	elif route in TITLES and route != current:
		history.append(current)
		_show(route)

func open_nested(route: StringName) -> void:
	if route in TITLES and route != current:
		history.append(current)
		_show(route)

func back() -> void:
	if current == &"age":
		return # the age entry is required on first launch (live FamilyAgeScreen)
	if is_instance_valid(overlay):
		dismiss()
	elif current == &"match" and ResultOverlay.current(self) != null:
		Match._after_result(self, ResultOverlay.go_home.bind(self))   # Back = Home
	elif current == &"match" and state.match_mode == &"online":
		Match.leave_online_dialog(self)
	elif current == &"match" and state.setting("confirm_exit", true):
		Match.save_dialog(self)
	else:
		_pop()

func _pop() -> void:
	if history.is_empty():
		_show(&"play")
	else:
		_show(history.pop_back())

func start_match(mode: StringName, resume := false) -> void:
	# Only local matches exist in this build; online / team / friends matches come from the
	# server (never a sample match).
	if mode not in [&"robot", &"pass_and_play", &"demo"] and not (resume and state.local_game != null):
		var copy := Online.copy("Online matches")
		var card: VBoxContainer = dialog(copy["title"], copy["message"], true)
		UI.button(card, "OK", dismiss, true)
		return
	if not resume:
		reset_match()
	state.saved_preview = false
	state.match_mode = mode
	if mode in [&"robot", &"pass_and_play", &"demo"] and state.local_game == null:
		LocalMatch.start(self)
	if current not in [&"match", &"results"]:
		history.append(current)
	_show(&"match")

## First launch: age entry, then the Demo match (see tutorial_pages.gd).
func _ready() -> void:
	# Tap hand guidance reads Play hints / Reduced Motion and knows which dialog is open.
	var g := Hints.guidance()
	if g != null:
		g.settings = func() -> Dictionary:
			return {"play_hints": state.setting("play_hints", true), "reduced_motion": state.setting("reduced_motion", false)}
		g.modal = func(): return overlay if is_instance_valid(overlay) else null

func first_launch() -> void:
	Tutorial.first_launch(self)

func reset_match() -> void:
	state.local_game = null
	state.handoff_pending = false
	state.last_roll = 0
	state.turn = 0
	state.events.clear()

func replay() -> void:
	start_match(state.match_mode)

func finish_match() -> void:
	if state.local_game != null and state.match_mode != &"demo":
		state.ads.completed(str(state.local_game.state.get("matchId", "")))
	if state.local_game == null:
		_show(&"results")   # preview / sample only
		return
	# The match screen stays as it is; the result overlay appears after the last move's
	# celebration (match_result_overlay.gd).
	ResultOverlay.schedule(self)

## Scroll positions by route + tab ("shop:1"), kept across refreshes and tab switches.
var scroll_positions := {}

func _scroll_key() -> String:
	var tab_group: String = {&"shop": "shop", &"badges": "badges", &"leaderboard": "leaderboard",
		&"collections": "collections", &"profile": "profile"}.get(current, "")
	return "%s:%s" % [current, state.tab_indexes.get(tab_group, 0) if not tab_group.is_empty() else ""]

func _remember_scroll() -> void:
	if is_instance_valid(scroll):
		scroll_positions[_scroll_key()] = scroll.scroll_vertical

func _restore_scroll() -> void:
	if not is_instance_valid(scroll):
		return
	var target: int = scroll_positions.get(_scroll_key(), 0)
	if target <= 0:
		return
	var list := scroll
	# The list's size is known after layout; restore then.
	(func():
		if is_instance_valid(list):
			if list is KineticScroll:
				list.set_position_now(target)
			else:
				list.scroll_vertical = target).call_deferred()

func refresh() -> void:
	_remember_scroll()
	_show(current)
	_restore_scroll()

func select_tab(index: int, group: String) -> void:
	_remember_scroll()
	state.tab_indexes[group] = index
	_show(current)
	_restore_scroll()

## Starts the scrolling list: everything added to `body` before this call stays fixed
## (header, title, back / gear, balance, tabs); everything after it scrolls. Only the
## list scrolls, with mobile-style momentum (KineticScroll).
func begin_list() -> VBoxContainer:
	if _list_started:
		return body
	_list_started = true
	_fixed_body = body
	var list := KineticScroll.new()
	list.name = "List"
	body.add_child(list)
	scroll = list
	var column := UI.column(list, _list_separation)
	column.size_flags_horizontal = SIZE_EXPAND_FILL
	body = column
	return column

## Back to the fixed column below the list (a fixed footer such as Start Game).
func end_list() -> VBoxContainer:
	if _fixed_body != null:
		body = _fixed_body
	return body

var _fixed_body: VBoxContainer
var _list_started := false
var _list_separation := 12

func _show(route: StringName) -> void:
	var probe_t := Time.get_ticks_usec()
	_show_route(route)
	preload("res://scripts/game/perf.gd").span("page_build", Time.get_ticks_usec() - probe_t)

func _show_route(route: StringName) -> void:
	if current == &"match" and route == &"settings" and state.local_game != null:
		_park_match_visuals()
	var keep_match := route == &"settings" or (current == &"settings" and route == &"match")
	current = route
	for child in get_children():
		if child == _parked_match:
			if keep_match:
				continue
			_parked_match = null
		remove_child(child)
		child.queue_free()
	overlay = null
	visible = route != &"play"
	route_changed.emit(route)
	if not visible:
		return

	var base := ColorRect.new()
	base.color = Color("#071c18")
	add_child(base)
	base.set_anchors_and_offsets_preset(PRESET_FULL_RECT)
	var backdrop := UI.art("moonlit_screen")
	if backdrop == null:
		backdrop = UI.texture("res://assets/ui/backgrounds/moonlit_meadow.webp")
	scenery = UI.image(self, backdrop, 0, true)
	scenery.set_anchors_and_offsets_preset(PRESET_FULL_RECT)
	scenery.offset_top = -top_inset
	var uses_vine_frame := route in FRAMED_ROUTES
	# The standard moonlit header (title plaque, wood back left, wood gear right).
	var uses_market_header := route in STANDARD_HEADER_ROUTES
	var has_tabs := route in [&"online", &"team_up", &"friends", &"rewards", &"shop", &"profile"]
	var badges := route == &"badges"
	# Fixed page column: header, tabs and summary cards stay put; begin_list() adds the one
	# scrolling area below them (the match never scrolls).
	var content_host := VBoxContainer.new()
	content_host.name = "Page"
	scroll = null
	_list_started = false
	_list_separation = 8 if route in [&"match", &"results"] else 12
	add_child(content_host)
	content_host.set_anchors_and_offsets_preset(PRESET_FULL_RECT)
	# The match board runs edge to edge; every other page keeps its 15 px side margin.
	content_host.offset_left = 0 if route == &"match" and state.local_game != null else 15
	content_host.offset_right = -content_host.offset_left
	content_host.offset_top = 8
	content_host.offset_bottom = -92 if has_tabs else -12
	if uses_vine_frame:
		var frame := VinePageFrame.new()
		frame.fade_bottom = route != &"profile"
		frame.add_theme_constant_override("separation", 12)
		frame.size_flags_vertical = SIZE_EXPAND_FILL
		content_host.add_child(frame)
		body = frame
	else:
		body = UI.column(content_host, 8 if route in [&"match", &"results"] else 12)
	body.size_flags_horizontal = SIZE_EXPAND_FILL
	body.size_flags_vertical = SIZE_EXPAND_FILL
	if not uses_vine_frame and not uses_market_header and route not in MODE_LOBBIES and route != &"match":
		var brand_area := Control.new()
		brand_area.custom_minimum_size.y = 112
		body.add_child(brand_area)
		var brand := BrandSign.new()
		brand_area.add_child(brand)
		brand.set_anchors_and_offsets_preset(PRESET_FULL_RECT)
	var header: Array = TITLES[route]
	if route == &"match":
		header = [Match.mode_title(state.match_mode), Match.mode_icon(state.match_mode)]
	if uses_vine_frame or uses_market_header:
		var profile_header := ProfileHeader.new()
		profile_header.title = header[0]
		profile_header.back_pressed.connect(back)
		if uses_market_header:
			profile_header.title_icon = {&"rewards": "gift", &"shop": "shop", &"wallet": "wallet", &"collections": "gift", &"results": "trophy"}.get(route, "gift")
			profile_header.settings_pressed.connect(open.bind(&"settings"))
		body.add_child(profile_header)
	if uses_vine_frame:
		var padding := MarginContainer.new()
		for side in ["left", "right", "bottom"]:
			padding.add_theme_constant_override("margin_" + side, 13)
		padding.size_flags_vertical = SIZE_EXPAND_FILL
		body.add_child(padding)
		body = UI.column(padding, 12)
		body.size_flags_vertical = SIZE_EXPAND_FILL
	elif route == &"match":
		_match_header(header[0], header[1])
	elif not uses_market_header:
		_header(header[0], header[1])
	# Pages whose builder fixes more than the header (tabs, balance) start the list
	# themselves; every other page scrolls everything below its header. Never the match.
	if route not in SELF_LISTED_ROUTES and route != &"match":
		begin_list()
	if route in [&"online", &"team_up", &"friends", &"robot", &"pass_and_play", &"room"]:
		Lobby.build(self)
	elif route == &"match" and state.local_game != null:
		LocalMatch.build(self)
	elif route in [&"match", &"results", &"details"]:
		Match.build(self)
	elif route == &"age":
		Tutorial.age(self)
	else:
		Hub.build(self)
	if route == &"match" and is_instance_valid(_parked_match):
		_parked_match.queue_free()
		_parked_match = null

func _park_match_visuals() -> void:
	# Settings pauses the turn flow. Keep the board and rail views alive until Back.
	_parked_match = Control.new()
	_parked_match.hide()
	_parked_match.process_mode = Node.PROCESS_MODE_DISABLED
	add_child(_parked_match)
	var visuals: Array = []
	if state.has_meta("board_view"):
		visuals.append(state.get_meta("board_view"))
	var cache: Dictionary = state.get_meta("rail_cache", {})
	for group in ["cards", "dice"]:
		visuals.append_array(cache.get(group, {}).values())
	for view in visuals:
		if is_instance_valid(view) and view.get_parent() != null:
			view.reparent(_parked_match, false)

const MODE_LOBBIES := [&"online", &"team_up", &"friends", &"robot", &"pass_and_play"]
const MEDALLION_ROUTES := [&"robot", &"pass_and_play"]
## Builders that call begin_list() themselves (after tabs / balance).
const SELF_LISTED_ROUTES := [&"shop", &"wallet", &"badges", &"leaderboard", &"collections", &"robot", &"pass_and_play"]
const STANDARD_HEADER_ROUTES := [&"rewards", &"shop", &"wallet", &"collections", &"results"]
const GEAR_ROUTES := [&"rewards", &"collections", &"shop", &"wallet"]

func _header(title: String, glyph: String) -> void:
	if current in MODE_LOBBIES:
		_mode_header(title, glyph)
		return
	var plaque := WoodPlaque.new()
	plaque.title = title
	plaque.glyph = glyph
	plaque.font_size = 30
	plaque.board_inset = 14.0
	plaque.size_flags_horizontal = SIZE_EXPAND_FILL
	var go_back := RoundIconButton.new()
	go_back.tooltip_text = "Back"
	go_back.pressed.connect(back)
	var bar := UI.row(body, 4)
	bar.alignment = BoxContainer.ALIGNMENT_CENTER
	bar.add_child(go_back)
	bar.add_child(plaque)
	plaque.custom_minimum_size.x = 0
	if current in GEAR_ROUTES:
		var settings := WoodIconButton.new()
		settings.configure_navigation(WoodIconButton.Glyph.SETTINGS)
		settings.pressed.connect(open.bind(&"settings"))
		bar.add_child(settings)
	else:
		var balance := Control.new()
		balance.custom_minimum_size = Vector2(50, 50)
		bar.add_child(balance)

func _match_header(title: String, glyph: String) -> void:
	var theme_header := _add_theme_header(112)
	var settings := WoodIconButton.new()
	settings.configure_navigation(WoodIconButton.Glyph.SETTINGS)
	settings.pressed.connect(open_nested.bind(&"settings"))
	theme_header.add_child(settings)
	settings.set_anchors_and_offsets_preset(PRESET_TOP_RIGHT)
	settings.position += Vector2(-WoodIconButton.NAV_SIZE.x, 20)
	var plaque := ModePlaque.new()
	plaque.title = title
	plaque.glyph = glyph
	plaque.artwork = UI.icon(glyph)
	plaque.match_title = true
	body.add_child(plaque)

func _mode_header(title: String, glyph: String) -> void:
	var plaque := ModePlaque.new()
	plaque.title = title
	plaque.glyph = glyph
	plaque.crest = current in MEDALLION_ROUTES
	if plaque.crest:
		_add_theme_header(106)
	body.add_child(plaque)
	if not plaque.crest:
		_add_wood_back(plaque, 10)

func _add_theme_header(height: float) -> Control:
	var theme_header := ProfileHeader.new()
	theme_header.wordmark_only = true
	theme_header.custom_minimum_size.y = height
	body.add_child(theme_header)
	_add_wood_back(theme_header, 20)
	return theme_header


func _add_wood_back(parent: Control, top: float) -> void:
	var back_button := WoodIconButton.new()
	back_button.configure_navigation(WoodIconButton.Glyph.BACK)
	back_button.pressed.connect(back)
	parent.add_child(back_button)
	back_button.position = Vector2(0, top)



func dialog(title: String, message: String, woodland := false) -> VBoxContainer:
	dismiss()
	var veil := ColorRect.new()
	veil.color = Color("#05100edc")
	add_child(veil)
	veil.set_anchors_and_offsets_preset(PRESET_FULL_RECT)
	overlay = veil
	var center := CenterContainer.new()
	veil.add_child(center)
	center.set_anchors_and_offsets_preset(PRESET_FULL_RECT)
	var frame := UI.panel(center)
	frame.get_parent().custom_minimum_size.x = 330
	if woodland:
		var panel := frame.get_parent() as MeadowPanel
		var plaque := VineTitle.new()
		plaque.title = title
		plaque.custom_minimum_size.y = 68
		panel.raised_rim_top = panel.get_theme_stylebox("panel").get_margin(SIDE_TOP) + plaque.custom_minimum_size.y / 2.0 - 1.5
		frame.add_child(plaque)
	else:
		var sign := WoodPlaque.new()
		sign.title = title
		sign.font_size = 24
		frame.add_child(sign)
	UI.label(frame, message, 16, true)
	return frame

func dismiss() -> void:
	if is_instance_valid(overlay):
		remove_child(overlay)
		overlay.queue_free()
		overlay = null

func notice(title: String, message: String, woodland := false) -> void:
	Hints.button(UI.button(dialog(title, message, woodland), "OK", dismiss, true), "dialog.ok", Hints.Policy.IDLE_SCREEN_MS, 30)

func copy_text(value: String) -> void:
	DisplayServer.clipboard_set(value)
	notice("Copied", value)

func go_home() -> void:
	history.clear()
	_show(&"play")
