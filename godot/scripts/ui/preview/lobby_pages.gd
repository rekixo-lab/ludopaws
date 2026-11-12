extends RefCounted

const Hints = preload("res://scripts/ui/hints/hints.gd")
const Online = preload("res://scripts/game/online/online_status.gd")

const UI = preload("res://scripts/ui/preview/meadow_controls.gd")
const ModePlaque := preload("res://scripts/ui/kit/mode_plaque.gd")
const LAKE_BACKGROUND := preload("res://assets/ui/illustrations/lakeside_empty.png")
const LakesideGuests := preload("res://scripts/ui/kit/lakeside_guests.gd")
const OnlinePlayerCard := preload("res://scripts/ui/kit/online_player_card.gd")
const TeamPennant := preload("res://scripts/ui/kit/team_pennant.gd")

static func build(page) -> void:
	match page.current:
		&"online": _online(page)
		&"team_up": _team(page)
		&"friends": _friends(page)
		&"room": _room(page)
		&"robot": _local_setup(page, true)
		&"pass_and_play": _local_setup(page, false)

static func _online(page) -> void:
	var body: VBoxContainer = page.body
	UI.label(body, "Play online with real players.", 19, true)
	var options := UI.row(body)
	var cards: Array[OnlinePlayerCard] = []
	for count in [2, 4]:
		var option := OnlinePlayerCard.new()
		option.player_count = count
		option.selected = page.state.online_count == count
		cards.append(option)
		options.add_child(option)
	var status := UI.panel(body)
	status.get_parent().size_flags_horizontal = Control.SIZE_SHRINK_CENTER
	var caption := UI.label(status, "●  " + ("Online service connected" if Online.state() == Online.State.READY else "Online service not connected"), 14, true)
	caption.autowrap_mode = TextServer.AUTOWRAP_OFF
	var guests := _lakeside(body, 304, false)
	guests.show_four(page.state.online_count == 4, false)
	for card in cards:
		card.pressed.connect(_online_size.bind(page, card.player_count, cards, guests))
	var find := UI.button(body, "Find Match", _queue.bind(page, &"online"), true, "search", 64) as MeadowButton
	find.font_size = 26
	find.custom_minimum_size.x = 360
	find.size_flags_horizontal = Control.SIZE_SHRINK_CENTER

static func _online_size(page, count: int, cards: Array[OnlinePlayerCard], guests: LakesideGuests) -> void:
	if page.state.online_count == count:
		return
	page.state.online_count = count
	for card in cards:
		card.selected = card.player_count == count
	guests.show_four(count == 4, not page.state.setting("reduced_motion", false))

static func _queue(page, mode: StringName) -> void:
	var OnlineFlow = load("res://scripts/game/online/online_turn_flow.gd")
	var c = OnlineFlow.controller_for(page.get_tree())
	if c == null:
		_needs_service(page, "Online matchmaking" if mode == &"online" else "Team Up matchmaking")
		return
	var team := mode != &"online"
	# Live PublicMatchmakingActions: search, poll every 1.5 s, open the verified match.
	var frame: VBoxContainer = page.dialog("Finding teammates" if team else "Finding a match",
		"Searching for a 2 vs 2 Team Up match…" if team else "Searching for %d players…" % page.state.online_count, true)
	var line: Label = frame.get_child(frame.get_child_count() - 1)
	UI.button(frame, "Cancel", func():
		await (c.cancel_team() if team else c.cancel_public())
		if is_instance_valid(page):
			page.dismiss(), false, "", 44)
	var on_status := func(text: String):
		if is_instance_valid(line):
			line.text = text
	var on_state := func(s: Dictionary, _pid: String):
		if is_instance_valid(page) and s.get("status") == "ACTIVE" and page.state.match_mode != &"online":
			_open_online(page, c)
	c.status.connect(on_status)
	c.state_changed.connect(on_state)
	frame.tree_exiting.connect(func():
		if c.status.is_connected(on_status):
			c.status.disconnect(on_status)
		if c.state_changed.is_connected(on_state):
			c.state_changed.disconnect(on_state))
	var res: Dictionary = await (c.begin_team() if team else c.begin_public(page.state.online_count))
	if is_instance_valid(page) and res.get("matched", false):
		_open_online(page, c)

## Opens the verified online match on the V2 match screen.
static func _open_online(page, c) -> void:
	var OnlineFlow = load("res://scripts/game/online/online_turn_flow.gd")
	page.dismiss()
	page.reset_match()
	page.state.local_game = OnlineFlow._render_game(c.state)
	page.start_match(&"online", true)

static func _team(page) -> void:
	var body: VBoxContainer = page.body
	UI.label(body, "Play together with a friend\nagainst another team.", 19, true)
	var versus := ModePlaque.new()
	versus.title = "2 vs 2"
	body.add_child(versus)
	var banners := UI.row(body)
	for team in 2:
		var pennant := TeamPennant.new()
		pennant.blue = team == 1
		banners.add_child(pennant)
		var card := UI.column(pennant, 6)
		var heading := UI.row(card, 7)
		heading.alignment = BoxContainer.ALIGNMENT_CENTER
		var paw_mount := Control.new()
		paw_mount.custom_minimum_size = Vector2(34, 32)
		heading.add_child(paw_mount)
		var paw := UI.image(paw_mount, UI.icon("profile_paw_crest"), 0)
		paw.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
		paw.pivot_offset = Vector2(17, 16)
		paw.rotation_degrees = -14
		var shading := ShaderMaterial.new()
		shading.shader = preload("res://assets/ui/backgrounds/team_paw_tint.gdshader")
		shading.set_shader_parameter("shadow_tint", Color("#123451") if pennant.blue else Color("#531b17"))
		shading.set_shader_parameter("light_tint", Color("#a4e6ff") if pennant.blue else Color("#ffb3a5"))
		paw.material = shading
		var title := UI.label(heading, "Team Red" if team == 0 else "Team Blue", 18, true)
		title.autowrap_mode = TextServer.AUTOWRAP_OFF
		title.size_flags_vertical = Control.SIZE_SHRINK_CENTER
		for seat in 2:
			_team_seat(card, page, team, seat)

	_lakeside(body, 186, true)
	var action_area := MarginContainer.new()
	action_area.resized.connect(func():
		var inset := roundi(action_area.size.x * 0.10)
		action_area.add_theme_constant_override("margin_left", inset)
		action_area.add_theme_constant_override("margin_right", inset)
	)
	body.add_child(action_area)
	var find := UI.button(action_area, "Find Team", _queue.bind(page, &"team_up"), true, "friends", 62) as MeadowButton
	find.glyph_artwork = preload("res://assets/ui/icons/friends.svg")
	find.font_size = 26

static func _team_seat(card: Control, page, team: int, seat: int) -> void:
	var index := team * 2 + seat
	var occupied: bool = page.state.team_seats[index]
	var caption := "You" if occupied else "Seat %d" % (seat + 1)
	var slot := UI.button(card, "", _pick_seat.bind(page, index), occupied, "", 43) as MeadowButton
	slot.tooltip_text = caption
	slot.radius = 12
	slot.moss_tint = Color("#153a58") if team == 1 else Color("#632d25")
	var content := UI.row(slot, 6)
	content.mouse_filter = Control.MOUSE_FILTER_IGNORE
	content.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	content.offset_left = 12
	content.offset_right = -12
	content.offset_bottom = -3
	var avatar := _seat_medallion(content, 29, Color("#685841"), Color("#c0a377"))
	var portrait := UI.image(avatar, UI.icon("profile"), 0)
	portrait.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	portrait.offset_left = 4
	portrait.offset_top = 4
	portrait.offset_right = -4
	portrait.offset_bottom = -4
	var label := UI.label(content, caption, 16, true)
	label.mouse_filter = Control.MOUSE_FILTER_IGNORE
	label.autowrap_mode = TextServer.AUTOWRAP_OFF
	label.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	label.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	if occupied:
		label.add_theme_color_override("font_color", Color("#113b26"))
	var action := _seat_medallion(content, 23, Color("#173c32"), Color("#76cf9e"))
	var mark := UI.label(action, "✓" if occupied else "+", 16, true)
	mark.mouse_filter = Control.MOUSE_FILTER_IGNORE
	mark.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
	mark.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	mark.add_theme_color_override("font_color", Color("#9df4bf"))

static func _seat_medallion(parent: Control, side: int, fill: Color, rim: Color) -> Panel:
	var medallion := Panel.new()
	medallion.custom_minimum_size = Vector2(side, side)
	medallion.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	medallion.mouse_filter = Control.MOUSE_FILTER_IGNORE
	var style := UI.surface(fill, rim, side)
	style.set_border_width_all(1)
	style.shadow_size = 0
	medallion.add_theme_stylebox_override("panel", style)
	parent.add_child(medallion)
	return medallion


static func _lakeside(body: Control, height: int, team: bool) -> LakesideGuests:
	var scene := UI.image(body, LAKE_BACKGROUND, height, true)
	var blend := ShaderMaterial.new()
	blend.shader = preload("res://assets/ui/backgrounds/lobby_art_blend.gdshader")
	blend.set_shader_parameter("top_fade", 0.17 if team else 0.28)
	scene.material = blend
	scene.resized.connect(func(): blend.set_shader_parameter("art_size", scene.size))
	var guests := LakesideGuests.new()
	guests.team_layout = team
	guests.lake_size = LAKE_BACKGROUND.get_size()
	scene.add_child(guests)
	guests.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	return guests


static func _pick_seat(page, index: int) -> void:
	var occupied: bool = page.state.team_seats[index]
	page.state.team_seats.fill(false)
	page.state.team_seats[index] = not occupied
	page.refresh()

## The shared Friends service (created with the online service).
static func friends_service(page):
	var tree: SceneTree = page.get_tree()
	var existing = tree.root.get_node_or_null("Friends")
	if existing != null:
		return existing
	var c = load("res://scripts/game/online/online_turn_flow.gd").controller_for(tree)
	if c == null:
		return null
	var f = load("res://scripts/game/online/friends_service.gd").new()
	f.name = "Friends"
	tree.root.add_child(f)
	f.setup(tree.root.get_node("Account"), c.api, c)
	return f

static func _friends(page) -> void:
	var body: VBoxContainer = page.body
	var f = friends_service(page)
	if f == null:
		UI.label(body, "Play with your friends.", 19, true)
		var copy := Online.copy("Your friends list")
		UI.state_card(body, "offline", copy["title"], copy["message"], "Retry", page.refresh)
		return
	f.active = true
	if not page.has_meta("friends_hooked"):
		page.set_meta("friends_hooked", true)
		f.changed.connect(func(): if is_instance_valid(page) and page.current == &"friends" and not is_instance_valid(page.overlay): page.refresh())
	if f.credential.is_empty():
		if not f.busy:
			f.ensure_identity()
		UI.state_card(body, "loading", "Preparing your Friend ID…", f.status_text if not f.status_text.is_empty() else "Connecting to Friends.")
		return
	if f.snapshot.is_empty() and not f.busy:
		f.sync()
	# Friend ID (copy / share), add a friend.
	var id_card := UI.panel(body, "Your Friend ID", Color.TRANSPARENT, "friends")
	var id_row := UI.row(id_card, 8)
	var id_label := UI.label(id_row, f.credential["id"], 18)
	id_label.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	UI.button(id_row, "Copy", page.copy_text.bind(f.credential["id"]), false, "", 36)
	var add_row := UI.row(body, 8)
	var input := UI.field(add_row, "")
	input.placeholder_text = "LPF-ABCD-EFGH-JKLM"
	input.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	UI.button(add_row, "Add", func(): f.send_request(input.text), true, "add", 40)
	if not f.status_text.is_empty():
		UI.label(body, f.status_text, 13, true)
	var actions := UI.row(body)
	UI.button(actions, "Create Room", _room_size.bind(page, f), true, "add", 54)
	UI.button(actions, "Join Room", _join_room.bind(page, f), false, "join", 54)
	page.begin_list()
	body = page.body
	if not f.room_state().is_empty() and not f._friend_seat().is_empty():
		UI.button(body, "Open your private room", page.open.bind(&"room"), true, "play", 46)
	if not f.invites().is_empty():
		var box := UI.panel(body, "Room invites", Color.TRANSPARENT, "join")
		for inv in f.invites():
			var line := UI.row(box, 6)
			var who = inv.get("from") if inv.get("from") is Dictionary else {}
			var name := UI.label(line, str(who.get("displayName", "Player")) + " invited you", 14)
			name.size_flags_horizontal = Control.SIZE_EXPAND_FILL
			UI.button(line, "Join", _accept_invite.bind(page, f, str(inv.get("inviteId", "")), str(inv.get("matchId", ""))), true, "", 34)
			UI.button(line, "Decline", func(): f.decline_invite(str(inv.get("inviteId", ""))), false, "", 34)
	if not f.incoming_requests().is_empty():
		var box := UI.panel(body, "Friend requests", Color.TRANSPARENT, "friends")
		for req in f.incoming_requests():
			var line := UI.row(box, 6)
			var who = req.get("from") if req.get("from") is Dictionary else req
			var name := UI.label(line, "%s · %s" % [who.get("displayName", "Player"), who.get("friendId", "")], 13)
			name.size_flags_horizontal = Control.SIZE_EXPAND_FILL
			UI.button(line, "Accept", func(): f.respond_request(str(req.get("requestId", "")), true), true, "", 34)
			UI.button(line, "Decline", func(): f.respond_request(str(req.get("requestId", "")), false), false, "", 34)
	UI.label(body, "Your Friends", 24)
	if f.friends().is_empty():
		UI.state_card(body, "empty", "No friends yet", "Share your Friend ID, or add a friend's ID above.", "Invite Friends", page.open.bind(&"invite"))
	for fr in f.friends():
		var line := UI.row(UI.panel(body), 8)
		var who := UI.column(line, 1)
		who.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		UI.label(who, str(fr.get("displayName", "Player")), 16)
		UI.label(who, "%s · %s" % [fr.get("friendId", ""), "Online" if fr.get("online", false) else "Offline"], 12)
		UI.button(line, "Play", _play_friend.bind(page, f, str(fr.get("friendId", ""))), true, "", 34)
		UI.button(line, "⋯", _friend_menu.bind(page, f, fr), false, "", 34)
	if not f.blocked().is_empty():
		var box := UI.panel(body, "Blocked players", Color.TRANSPARENT, "lock")
		for b in f.blocked():
			var line := UI.row(box, 6)
			var name := UI.label(line, str(b.get("friendId", "")), 13)
			name.size_flags_horizontal = Control.SIZE_EXPAND_FILL
			UI.button(line, "Unblock", func(): f.unblock(str(b.get("friendId", ""))), false, "", 32)

static func _room_size(page, f) -> void:
	var frame: VBoxContainer = page.dialog("Private room", "How many players?", true)
	var row := UI.row(frame)
	for n in [2, 3, 4]:
		UI.button(row, str(n), func():
			page.dismiss()
			var r: Dictionary = await f.create_room(n)
			if is_instance_valid(page) and r.get("ok", false):
				page.open(&"room"), n == 2, "", 44)
	UI.button(frame, "Cancel", page.dismiss, false, "", 40)

static func _join_room(page, f) -> void:
	if f.invites().is_empty():
		page.notice("Join Room", "Ask your friend to invite you from their Friends list, or open the room link they shared.", true)
	else:
		page.notice("Join Room", "Your room invites are listed on this page. Tap Join on the one you want.", true)

static func _accept_invite(page, f, invite_id: String, match_id: String) -> void:
	var r: Dictionary = await f.accept_invite(invite_id, match_id)
	if is_instance_valid(page) and r.get("ok", false):
		page.open(&"room")

static func _play_friend(page, f, friend_id: String) -> void:
	var r: Dictionary = await f.invite(friend_id, 2)
	if is_instance_valid(page) and r.get("ok", false):
		page.open(&"room")

static func _friend_menu(page, f, fr: Dictionary) -> void:
	var id := str(fr.get("friendId", ""))
	var frame: VBoxContainer = page.dialog(str(fr.get("displayName", "Player")), id, true)
	UI.button(frame, "Invite to my room", func():
		page.dismiss()
		f.invite(id, 2), true, "", 42)
	UI.button(frame, "Remove friend", func():
		page.dismiss()
		f.remove(id), false, "", 42)
	UI.button(frame, "Block", func():
		page.dismiss()
		f.block(id), false, "", 42)
	UI.button(frame, "Report", func():
		page.dismiss()
		f.report(id, "OTHER"), false, "", 42)
	UI.button(frame, "Cancel", page.dismiss, false, "", 40)

## Server-backed actions without the service: an honest dialog, never a sample room or match.
static func _needs_service(page, feature: String) -> void:
	var copy := Online.copy(feature)
	var card: VBoxContainer = page.dialog(copy["title"], copy["message"], true)
	UI.button(card, "OK", page.dismiss, true)

## The private room (live FriendsActivity.renderRoom): code, seats filled, host Start.
static func _room(page) -> void:
	var body: VBoxContainer = page.body
	var f = friends_service(page)
	var c = page.get_tree().root.get_node_or_null("OnlineMatch")
	if f == null or c == null or c.match_id.is_empty() or c.mode != "FRIENDS":
		var copy := Online.copy("Friend rooms")
		UI.state_card(body, "offline", "No private room", "Create a room or accept an invite from the Friends page.", "Back", page.back)
		return
	if not page.has_meta("room_hooked"):
		page.set_meta("room_hooked", true)
		c.state_changed.connect(func(s: Dictionary, _p: String):
			if not is_instance_valid(page):
				return
			if s.get("status") == "ACTIVE" and page.current == &"room":
				_open_online(page, c)
			elif page.current == &"room" and not is_instance_valid(page.overlay):
				page.refresh())
	var s: Dictionary = c.state
	var room := UI.panel(body)
	UI.label(room, "Room Code", 21, true)
	var copy_button := UI.button(room, c.match_id + "   ▣", page.copy_text.bind(c.match_id), false, "", 43)
	copy_button.custom_minimum_size.x = 186
	copy_button.size_flags_horizontal = Control.SIZE_SHRINK_CENTER
	var seated: int = Array(s.get("players", [])).size()
	var target := int(s.get("targetPlayerCount", 2)) if s.get("targetPlayerCount") != null else 2
	UI.label(room, "%d / %d players joined" % [seated, target], 16, true)
	var host: bool = s.get("hostPlayerId") == c.player_id
	if s.get("status") == "ACTIVE":
		UI.button(body, "Open Match", _open_online.bind(page, c), true, "play", 48)
	elif host:
		var start := UI.button(body, "Start Match", func(): f.start_room(), true, "play", 48)
		start.disabled = seated < target
	else:
		UI.label(body, "Waiting for the host to start…", 15, true)
	var host_id = f.credential.get("id") if host else null
	UI.button(body, "Share Room Link", func():
		var RoomLink = load("res://scripts/game/online/room_link.gd")
		RoomLink.share(RoomLink.share_message(c.match_id, host_id), "Share room"), false, "friends", 42)
	UI.button(body, "Leave Room", func():
		c.leave()
		page.back(), false, "back", 38)

## Local animal roster for the picker. Names/coat colours only; the code-built
## characters from the live game replace the paw tokens once they are ported.
## [name, fur, pads, lighter tuft]
const Characters = preload("res://scripts/game/characters/catalog.gd")

static func _local_setup(page, robot: bool) -> void:
	# Live setup behaviour in the compact V2 page (setup_pages.gd, docs/SETUP_PARITY.md).
	load("res://scripts/ui/preview/setup_pages.gd").build(page, robot)

static func _caption(body: VBoxContainer, text: String) -> void:
	var holder := Control.new()
	holder.custom_minimum_size.y = 30
	body.add_child(holder)
	var caption := UI.label(holder, text, 18, true)
	caption.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	caption.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
	var ornament := MeadowOrnament.new()
	ornament.gap = UI.SERIF.get_string_size(text, HORIZONTAL_ALIGNMENT_LEFT, -1, 18).x + 22.0
	ornament.sprig_length = 24.0
	holder.add_child(ornament)
	ornament.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)

static func _start_button(body: VBoxContainer, action: Callable) -> void:
	var holder := Control.new()
	holder.custom_minimum_size.y = 70
	body.add_child(holder)
	var ornament := MeadowOrnament.new()
	ornament.gap = 350.0
	ornament.sprig_length = 30.0
	holder.add_child(ornament)
	ornament.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	var center := CenterContainer.new()
	holder.add_child(center)
	center.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	var start := UI.button(center, "Start Game", action, true, "play", 58) as MeadowButton
	Hints.button(start, "setup.start", Hints.Policy.IDLE_SCREEN_MS, 50)
	start.custom_minimum_size.x = 276
	start.font_size = 28
	start.display_font = true

