extends RefCounted
## Adapter between the Meadow UI and the local rules model.

const UI = preload("res://scripts/ui/preview/meadow_controls.gd")
const Rules = preload("res://scripts/game/local_match.gd")
const Board = preload("res://scripts/game/board3d/classic_board_3d.gd")
const Rail = preload("res://scripts/ui/match/player_rail.gd")
const Layout = preload("res://scripts/game/match/rail_layout.gd")
const Demo = preload("res://scripts/game/tutorial/demo_match.gd")
const Coach = preload("res://scripts/game/tutorial/tutorial_coach.gd")
const Tutorial = preload("res://scripts/ui/preview/tutorial_pages.gd")
const TurnFlow = preload("res://scripts/game/local_turn_flow.gd")
const Pacing = preload("res://scripts/game/gameplay_pacing.gd")
const SavedMatch = preload("res://scripts/game/match/saved_match_store.gd")
const OnlineFlow = preload("res://scripts/game/online/online_turn_flow.gd")
const ResultRecovery = preload("res://scripts/game/match/result_recovery.gd")
const Handoff = preload("res://scripts/game/match/pass_and_play_handoff.gd")
## Side inset of the rails (the board itself is edge to edge).
const RAIL_PAD := 10.0
## Match header (brand + mode plaque) height in logical px, reserved above the rails.
const HEADER_RESERVE := 186.0
const Hints = preload("res://scripts/ui/hints/hints.gd")
const TurnBanner = preload("res://scripts/ui/match/turn_banner.gd")
const Messages = preload("res://scripts/game/match/match_messages.gd")
const MessageBar = preload("res://scripts/ui/match/match_message_bar.gd")
const COLOR_MAP := [0, 3, 2, 1] # Setup palette: Red, Blue, Yellow, Green.
const Characters = preload("res://scripts/game/characters/catalog.gd")
const Starter = preload("res://scripts/game/characters/starter_assignment.gd")
const Setup = preload("res://scripts/game/setup/offline_setup.gd")
const Profile = preload("res://scripts/game/profile/profile_store.gd")
const Weekly = preload("res://scripts/game/profile/weekly_challenge_store.gd")

## Robot-driven local modes: a Robot match or the Demo match.
static func robot_mode(page) -> bool:
	return page.state.match_mode in [&"robot", &"demo"]

static func start(page) -> void:
	if page.state.match_mode == &"demo":
		# NEW V2 FEATURE: the demo only changes the starting position; real rules and dice.
		page.state.local_game = Demo.create(page.state.inventory.selected_character())
		return
	# Live OfflineCharacterSetupStore + OfflinePlayerLayout: the saved setup decides every
	# seat's colour, Paw and human / robot type (rematch reloads the same setup). Locked or
	# legacy Paws are repaired here, so they can never reach a match.
	var robot: bool = page.state.match_mode == &"robot"
	var mode := "COMPUTER" if robot else "PASS_AND_PLAY"
	var inventory = page.state.inventory
	var setup := Setup.load_setup(mode, inventory.unlocked_character_ids(), inventory.selected_character(), page.state.player_count)
	page.state.player_count = setup["player_count"]
	if robot:
		page.state.difficulty = 1  # live has no difficulty: robots play the live policy
	var colors: Array[int] = []
	var players: Array = []
	for seat in Setup.roster(mode, setup):
		colors.append(Setup.COLORS.find(seat["color"]))
		players.append({"displayName": seat["name"], "characterId": seat["characterId"],
			"playerType": "BOT" if seat["bot"] else "HUMAN"})
	page.state.local_game = Rules.new(colors, "ROBOT" if robot else "PASS_AND_PLAY", players)
	# Live: the active setup and a first checkpoint are saved as the match starts.
	SavedMatch.save_active_setup(mode, setup["player_count"], setup["colors"][0], setup["paws"], setup["humans"])
	SavedMatch.save(mode, page.state.local_game.state, _owner())

static func build(page, show_result := false) -> void:
	var body: VBoxContainer = page.body
	var game: Rules = page.state.local_game
	var flow = _flow_for(page, game)
	var now: int = flow.now()
	# Live player rails: a card per player in its yard's corner, the active player's dice beside it.
	var rails := build_rails(page, game, flow, now, show_result)
	body.add_child(rails["top"])
	var board := _board_for(page, game)
	board.rotation_turns = (3 - game.colors[_viewer_seat(page, game)]) % 4
	board.reduced_motion = page.state.setting("reduced_motion", false)
	board.board_id = page.state.inventory.selected_id("BOARD")
	board.game_speed = flow.speed
	# V2 layout: the square board spans the screen edge to edge (rendered at physical pixels,
	# never upscaled); it shrinks only when the screen is too short for header + rails + board.
	_size_board(page, board, rails["top"].custom_minimum_size.y)
	body.add_child(board)
	body.add_child(rails["bottom"])
	board.input_enabled = flow.can_move(now) and not show_result
	if show_result:
		page.state.set_meta("match_ui", null)
		return
	# The old status box is gone; one slim message row keeps lessons, hints and events.
	var bar := MessageBar.new()
	body.add_child(bar)
	_fill_messages(page, game, flow, now, bar)
	page.state.set_meta("match_ui", {"body": body, "top": rails["top"], "bottom": rails["bottom"], "bar": bar, "game": game})

## The equipped dice (a dev-only perf/screenshot probe flag can preview another skin).
static func _dice_style(page) -> String:
	const Probe = preload("res://scripts/game/perf.gd")
	if Probe.is_enabled() and Probe.get_flags().has("dice_style"):
		return str(Probe.get_flags()["dice_style"])
	return page.state.inventory.selected_id("DICE")

static func _size_board(page, board: Control, rail_h: float) -> void:
	var width: float = _content_width(page)
	board.custom_minimum_size.y = clampf(minf(width, page.size.y - HEADER_RESERVE - 2.0 * rail_h - MessageBar.HEIGHT - 24.0), 300.0, width)

## Turn changes update only the rails, the dice and the status panel in place: the page, the
## board and its 3D scene are never rebuilt or re-parented during play (perf: a re-parented
## board re-entered the tree and replayed its whole 2D artwork, ~1 s on a Samsung A71).
static func update(page) -> bool:
	var ui = (page.state.get_meta("match_ui") if page.state.has_meta("match_ui") else null) if page.state.has_meta("match_ui") else null
	var game: Rules = page.state.local_game
	if ui == null or ui["game"] != game or page.current != &"match" or not is_instance_valid(ui["bar"]) \
			or not is_instance_valid(ui["top"]) or not is_instance_valid(ui["bottom"]) or ui["top"].get_parent() != ui["body"]:
		return false
	var flow = _flow_for(page, game)
	var now: int = flow.now()
	var body: VBoxContainer = ui["body"]
	var rails := build_rails(page, game, flow, now)
	for key in ["top", "bottom"]:
		var old: Control = ui[key]
		var index := old.get_index()
		body.remove_child(old)
		old.queue_free()
		body.add_child(rails[key])
		body.move_child(rails[key], index)
		ui[key] = rails[key]
	var board = page.state.get_meta("board_view")
	board.input_enabled = flow.can_move(now)
	_size_board(page, board, rails["top"].custom_minimum_size.y)
	var old_bar: Control = ui["bar"]
	var bar_index := old_bar.get_index()
	body.remove_child(old_bar)
	old_bar.queue_free()
	var bar := MessageBar.new()
	body.add_child(bar)
	body.move_child(bar, bar_index)
	ui["bar"] = bar
	_fill_messages(page, game, flow, now, bar)
	return true

## Demo lesson + Skip, the first-match coach hint (tap hides play hints) or a short game
## event (third six, no legal move, extra turn, …) in the message row.
static func _fill_messages(page, game: Rules, flow, now: int, bar) -> void:
	var robot: bool = flow.computer_turn()
	var demo: bool = page.state.match_mode == &"demo"
	var lesson := ""
	var coach = null
	if demo:
		var lessons: Coach = _coach_for(page, game)
		lessons.on_turn(game.turn == 0 and not game.complete, flow.can_roll(now), flow.can_move(now))
		lesson = lessons.text()
	else:
		coach = coach_hint(page.state.setting("total_games", 0) == 0, page.state.setting("play_hints", true),
			not game.complete and not flow.blocked(now) and not robot, game.legal.size() if not game.legal.is_empty() else null)
	# A game event shows for TRANSIENT_MS from the moment its message first appeared.
	var seen = (page.state.get_meta("toast_seen") if page.state.has_meta("toast_seen") else null) if page.state.has_meta("toast_seen") else null
	var key := "%s|%s" % [game.state.get("randomEventIndex", 0), game.message]
	if seen == null or seen["key"] != key:
		seen = {"key": key, "at": now}
		page.state.set_meta("toast_seen", seen)
	var shown := Messages.pick(lesson, coach, game.message)
	bar.show_message(shown, Messages.TRANSIENT_MS - (now - int(seen["at"])))
	if shown["kind"] == "coach":
		bar.label.text = "%s · Tap to hide" % shown["text"]
		bar.on_tap(func():
			page.state.set_setting("play_hints", false)
			_update_or_refresh(page))
	if demo:
		bar.add_skip(Tutorial.skip_dialog.bind(page))

static func _update_or_refresh(page) -> void:
	if not update(page):
		page.refresh()

## The page's content width (the body is not laid out yet on the first build).
static func _content_width(page) -> float:
	return maxf(page.size.x, 200.0) if page.size.x > 0 else 900.0

## Rail model for a local match (Robot, Pass & Play, Demo): seat 0 is the local human in Robot
## modes; every seat is local in Pass & Play. Returns player_rail.build's result.
static func build_rails(page, game: Rules, flow, now: int, show_result := false) -> Dictionary:
	var players: Array = []
	var robots := robot_mode(page)
	for i in game.state["players"].size():
		var p: Dictionary = game.state["players"][i]
		var bot: bool = game.is_bot(i)
		players.append({"seat": i, "color": p["color"], "name": player_name(page, i), "character": p.get("characterId", "dog"),
			"bot": false, "computer": bot, "local": not bot, "forfeited": p.get("forfeited", false),
			"tokens": Array(game.pawns[i]), "team_id": null})
	var presenting := int(page.state.get_meta("presenting_seat", -1)) if page.state.has_meta("presenting_seat") else -1
	var active := -1 if show_result or game.complete else Layout.active_seat(game.turn, flow.blocked(now), presenting)
	var model := {"players": players, "first_color": game.state["players"][0]["color"], "online": false,
		"active_seat": active, "roll_enabled": flow.can_roll(now) and active == game.turn,
		"dice_style": _dice_style(page), "countdown": null, "width": _content_width(page) - 2.0 * RAIL_PAD, "pad": RAIL_PAD}
	var cache: Dictionary = page.state.get_meta("rail_cache") if page.state.has_meta("rail_cache") else {}
	if cache.get("game") != game:
		cache = {"game": game}
		page.state.set_meta("rail_cache", cache)
	var result := Rail.build(model, cache, func(seat: int):
		if seat == game.turn and _active(page, game):
			_roll(page, game))
	# Tap hand: own dice after 3 s idle (Demo 1.5 s). The button is disabled for robot /
	# remote turns, while rolling and while a move presents, so the hand never shows then.
	var dice_buttons: Dictionary = result["dice_buttons"]
	var demo: bool = page.state.match_mode == &"demo"
	Hints.offer(result["bottom"], "match.dice", func():
		var b = dice_buttons.get(game.turn)
		return b if is_instance_valid(b) and not game.is_bot(game.turn) and not game.complete else null,
		Hints.Policy.IDLE_DEMO_MS if demo else Hints.Policy.IDLE_MATCH_MS, 80 if not demo else 90, demo)
	# Dice state follows the flow: rolling while a roll is in flight, attention when tappable.
	for seat in cache["dice"]:
		var view = cache["dice"][seat]
		if seat == active and (flow.roll_due >= 0 or flow.cpu_roll_due >= 0):
			view.start_rolling()
		view.set_attention(seat == active and model["roll_enabled"])
	return result

## One turn-flow controller per match (live pacing, robot turns, forced single moves). It
## lives at the tree root so its timers keep running while the page rebuilds.
static func _coach_for(page, game: Rules) -> Coach:
	var coach = (page.state.get_meta("tutorial_coach") if page.state.has_meta("tutorial_coach") else null)
	if coach == null or coach.get_meta("game") != game:
		coach = Coach.new(game.state["players"][0]["playerId"])
		coach.set_meta("game", game)
		coach.set_meta("last", game.snapshot())
		page.state.set_meta("tutorial_coach", coach)
	return coach

static func _flow_for(page, game: Rules) -> Node:
	var flow = (page.state.get_meta("turn_flow") if page.state.has_meta("turn_flow") else null)
	if is_instance_valid(flow) and flow.game == game:
		return flow
	# Live: the post-win ad entitlement is verified during play, never at the exit.
	if page.state.match_mode != &"demo":
		page.state.ads.prepare_exit_gate()
	if is_instance_valid(flow):
		flow.queue_free()
	if page.state.match_mode == &"online":
		flow = OnlineFlow.new(OnlineFlow.controller_for(page.get_tree()), page.state.setting("game_speed", Pacing.NORMAL))
		flow.game = game
	else:
		flow = TurnFlow.new(game, page.state.setting("game_speed", Pacing.NORMAL), robot_mode(page),
			Demo.ROBOT_DIFFICULTY if page.state.match_mode == &"demo" else page.state.difficulty)
	flow.name = "TurnFlow"
	flow.allowed = func() -> bool: return _active(page, game)
	flow.committed.connect(func(event: Dictionary):
		_record(page, "%s · %s" % [player_name(page, event["seat"]), event["message"]])
		if page.state.match_mode == &"demo":
			var lessons: Coach = _coach_for(page, game)
			var now_snapshot: Dictionary = game.snapshot()
			lessons.on_commit(lessons.get_meta("last"), now_snapshot)
			lessons.set_meta("last", now_snapshot)
		page.state.set_meta("presenting_seat", event["seat"])
		if event["kind"] == "roll":
			page.state.last_roll = game.roll
			var cache: Dictionary = page.state.get_meta("rail_cache") if page.state.has_meta("rail_cache") else {}
			var view = cache.get("dice", {}).get(event["seat"])
			if is_instance_valid(view):
				view.show_outcome(game.roll)
			var card = cache.get("cards", {}).get(event["seat"])
			if game.roll == 6 and is_instance_valid(card):
				card.excite())
	flow.changed.connect(func(): _on_flow_changed(page, game), CONNECT_DEFERRED)
	page.state.set_meta("turn_flow", flow)
	page.state.set_meta("presenting_seat", -1)
	# First render / resume never prompts a handoff: start from the current snapshot.
	page.state.set_meta("handoff_prev", _handoff_snapshot(game))
	page.state.set_meta("handoff_pending", null)
	# Warm-up at match start: every gameplay sound loads now, not on first use.
	var audio = page.get_tree().root.get_node_or_null("GameAudio")
	if audio != null and audio.has_method("warm_up"):
		audio.warm_up()
	# The page frees its children on every rebuild; the flow must outlive those rebuilds.
	page.get_tree().root.add_child.call_deferred(flow)
	return flow

## FirstMatchCoachPolicy.hint (choices = legal count while a roll is pending, else null).
static func coach_hint(first_match: bool, enabled: bool, local_turn: bool, choices) -> Variant:
	if not first_match or not enabled or not local_turn:
		return null
	if choices != null:
		return "Choose a glowing paw. Shield cells are safe." if int(choices) > 0 else null
	return "Tap your character's dice to begin."

## Live OfflineGameEngine.reconcileFinishedResult: won = seat 0 (the device player) won;
## Robot credits only the player's Paw (COMPUTER source), Pass & Play every seat's (LOCAL).
## Feeds the device-local profile and weekly challenge; each match id counts once.
static func record_result(page, game: Rules) -> void:
	var players: Array = game.state["players"]
	if players.is_empty():
		return
	var computer: bool = page.state.match_mode == &"robot"
	var won: bool = game.state.get("winnerPlayerId") == players[0].get("playerId")
	var characters: Array = [players[0].get("characterId", "")] if computer else players.map(func(p): return p.get("characterId", ""))
	var match_id := str(game.state.get("matchId", ""))
	# Live OfflineResultRecovery: written first, then applied (retried at launch on failure).
	ResultRecovery.record(match_id, _owner(), won, computer, characters)
	load("res://scripts/game/growth/referral_service.gd").completed_match(match_id)   # live OfflineGameEngine

static func _owner() -> String:
	var tree := Engine.get_main_loop() as SceneTree
	var account = tree.root.get_node_or_null("Account") if tree != null else null
	return str(account.session().get("accountId", "")) if account != null else ""

## Live checkpoint after every change; a finished match leaves no active save.
static func _checkpoint(page, game: Rules) -> void:
	if page.state.match_mode not in [&"robot", &"pass_and_play"] or page.state.local_game != game:
		return
	var mode := "COMPUTER" if page.state.match_mode == &"robot" else "PASS_AND_PLAY"
	var mark := "%s:%s:%s" % [game.state.get("matchId", ""), game.state.get("revision", 0), game.complete]
	if page.state.get_meta("checkpoint_mark", "") == mark:
		return
	page.state.set_meta("checkpoint_mark", mark)
	if game.complete:
		SavedMatch.clear(mode)
	else:
		SavedMatch.save(mode, game.state, _owner())

static func _on_flow_changed(page, game: Rules) -> void:
	if is_instance_valid(page):
		_checkpoint(page, game)
	if not _active(page, game):
		return
	var flow = page.state.get_meta("turn_flow")
	if page.state.match_mode == &"pass_and_play":
		var current := _handoff_snapshot(game)
		var next = Handoff.resolve("PASS_AND_PLAY", (page.state.get_meta("handoff_prev") if page.state.has_meta("handoff_prev") else null), current)
		page.state.set_meta("handoff_prev", current)
		if next != null:
			page.state.set_meta("handoff_pending", next)
		# V2 shows the prompt once the previous move has finished presenting.
		var pending = (page.state.get_meta("handoff_pending") if page.state.has_meta("handoff_pending") else null)
		if pending != null and not flow.blocked(flow.now()) and not game.complete:
			page.state.set_meta("handoff_pending", null)
			if bool(page.state.setting("pass_phone_prompt", false)):
				# Opt-in: the live PASS THE PHONE / READY modal (pauses the flow until READY).
				_update_or_refresh(page)
				show_handoff(page, pending)
				return
			# Default (V2): a non-blocking turn banner; the game never waits for it.
			show_turn_banner(page, pending)
	if game.complete and not flow.blocked(flow.now()):
		# Completed local games (first-match coach); the profile store replaces this in G.
		# The Demo never counts toward stats, coach counters, rewards or ads.
		if page.state.match_mode != &"demo":
			page.state.set_setting("total_games", int(page.state.setting("total_games", 0)) + 1)
			if page.state.match_mode != &"online":   # online results: online_match_controller
				record_result(page, game)
		page.finish_match()
	else:
		_update_or_refresh(page)

static func _handoff_snapshot(game: Rules) -> Dictionary:
	return {"matchId": game.state.get("matchId"), "status": game.state.get("status"),
		"turnSeat": game.state.get("turnSeat"), "pendingRoll": game.state.get("pendingRoll"),
		"players": game.state.get("players", [])}

## V2 Pass & Play turn banner (default): non-blocking, fades by itself, one light cue.
static func show_turn_banner(page, next: Dictionary) -> void:
	var old = page.state.get_meta("turn_banner") if page.state.has_meta("turn_banner") else null
	if is_instance_valid(old):
		old.queue_free()
	var banner := TurnBanner.new()
	banner.setup(next, player_name(page, int(next["seat"])), Handoff.subtitle(next["color"]), page.state.setting("reduced_motion", false))
	page.add_child(banner)
	page.state.set_meta("turn_banner", banner)
	var board = page.state.get_meta("board_view") if page.state.has_meta("board_view") else null
	var place := func():
		if not is_instance_valid(banner):
			return
		var top := 120.0
		var center_x: float = page.size.x * 0.5
		if is_instance_valid(board) and board.is_inside_tree():
			var r: Rect2 = board.get_global_rect()
			var inv: Transform2D = page.get_global_transform().affine_inverse()
			top = (inv * r.position).y + 10.0
			center_x = (inv * r.get_center()).x
		banner.reset_size()
		banner.place(Vector2(center_x - banner.size.x * 0.5, top))
	place.call_deferred()
	# One light cue per real turn change (sound setting / haptics setting respected).
	var audio = page.get_tree().root.get_node_or_null("GameAudio")
	if audio != null and audio.has_method("click"):
		audio.click()
	if bool(page.state.setting("haptics", true)) and OS.has_feature("mobile"):
		Input.vibrate_handheld(18)

## Pass & Play handoff (live OfflinePassAndPlayHandoffUi): PASS THE PHONE, the next player's
## name, "<Colour> paws • your turn" and READY; not cancellable. The modal blocks the flow.
static func show_handoff(page, next: Dictionary) -> void:
	var name := player_name(page, int(next["seat"]))
	var card: VBoxContainer = page.dialog("PASS THE PHONE", "%s\n%s" % [name, Handoff.subtitle(next["color"])], true)
	var ready := UI.button(card, "READY", func():
		page.dismiss()
		_update_or_refresh(page), true)
	Hints.button(ready, "match.ready", Hints.Policy.IDLE_SCREEN_MS, 95)

## One board per match survives the page's rebuild on every refresh, so baked art,
## the 3D scene and in-flight move animations are kept instead of recreated.
static func _board_for(page, game: Rules) -> Control:
	var board = (page.state.get_meta("board_view") if page.state.has_meta("board_view") else null)
	if not is_instance_valid(board) or board.match_state != game:
		if is_instance_valid(board):
			board.queue_free()
		board = Board.new()
		board.match_state = game
		board.pawn_selected.connect(func(index: int): _move(index, page, game))
		page.state.set_meta("board_view", board)
	elif board.get_parent() != null:
		board.get_parent().remove_child(board)
	return board

## The seat shown at the bottom: seat 0 locally, my server seat online.
static func _viewer_seat(page, game) -> int:
	if page.state.match_mode == &"online":
		var flow = page.state.get_meta("turn_flow") if page.state.has_meta("turn_flow") else null
		if flow != null and flow.has_method("my_seat") and flow.my_seat() >= 0:
			return flow.my_seat()
	return 0

static func player_name(page, seat: int) -> String:
	var game = page.state.local_game
	if page.state.match_mode == &"online" and game != null:
		# Family mode: every public name is "Player"; my own seat reads "You".
		return "You" if seat == _viewer_seat(page, game) else str(game.state["players"][seat].get("displayName", "Player"))
	# The roster's names (setup / demo) when the match carries them.
	if game != null and seat < game.state["players"].size():
		var given := str(game.state["players"][seat].get("displayName", "")).strip_edges()
		if not given.is_empty():
			return given
	if robot_mode(page):
		return "You" if seat == 0 else "Robot %d" % seat
	var name: String = page.state.player_names[seat].strip_edges()
	return name if not name.is_empty() else "Player %d" % (seat + 1)

static func _active(page, game: Rules) -> bool:
	return is_instance_valid(page) and page.current == &"match" and page.state.local_game == game and not is_instance_valid(page.overlay)

static func _roll(page, game: Rules) -> void:
	if not _active(page, game):
		return
	var flow = _flow_for(page, game)
	preload("res://scripts/game/perf.gd").mark_tap()
	flow.roll(flow.now())

static func _move(index: int, page, game: Rules) -> void:
	if not _active(page, game):
		return
	var flow = _flow_for(page, game)
	flow.move(index, flow.now())

static func _record(page, entry: String) -> void:
	page.state.events.push_front(entry)
	if page.state.events.size() > 100:
		page.state.events.pop_back()
