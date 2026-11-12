extends "res://scripts/game/local_turn_flow.gd"
## Online match flow for the V2 match screen. Same presentation timing as the local flow
## (dice result hold, move unlock, automatic single move), but the server is the only
## authority: a roll / move is a request through online_match_controller.gd, and the board
## changes only when a validated server snapshot arrives (live OnlineMatchActions +
## OnlineAutoMoveScheduler + OnlineResponseRenderer). No local dice, no local robots.

const LocalMatch = preload("res://scripts/game/local_match.gd")

var controller
var _in_flight := false
## The roll event whose move was already requested (never requested twice).
var _moved_event := -1

func _init(ctrl = null, game_speed := Pacing.NORMAL) -> void:
	super(null, game_speed, false, 1)
	controller = ctrl
	if controller != null:
		controller.state_changed.connect(_on_server_state)
		if not controller.state.is_empty():
			game = _render_game(controller.state)

## One render object for the whole match, updated in place (the page keeps its board).
static func _render_game(s: Dictionary) -> RefCounted:
	var g = LocalMatch.from_saved(load("res://scripts/game/online/match_envelope.gd").with_ruleset_key(s))
	_sync_roll(g)
	return g

static func _sync_roll(g) -> void:
	var pending = g.state.get("pendingRoll")
	g.roll = int(pending.get("outcome", 0)) if pending is Dictionary and pending.get("outcome") != null else 0
	g.comeback = pending is Dictionary and bool(pending.get("comebackBonusApplied", false))

func my_seat() -> int:
	if game == null or controller == null:
		return -1
	for i in game.state["players"].size():
		if game.state["players"][i]["playerId"] == controller.player_id:
			return i
	return -1

func computer_turn() -> bool:
	return false   # server robots act on the server

func _my_turn() -> bool:
	return controller != null and controller.is_my_turn()

func can_roll(at: int) -> bool:
	return game != null and not game.complete and game.legal.is_empty() and _my_turn() and not blocked(at) \
		and not _in_flight and not controller.busy

func can_move(at: int) -> bool:
	return game != null and not game.complete and not game.legal.is_empty() and _my_turn() and not blocked(at) \
		and not _in_flight and not controller.busy

func roll(at: int) -> bool:
	if not can_roll(at):
		return false
	if audio != null:
		audio.roll()
	status = "Rolling…"
	_in_flight = true
	changed.emit()
	_request(controller.roll())
	return true

func _commit_move(index: int, _at: int) -> bool:
	if _in_flight or not game.legal.has(index) or _event_index() == _moved_event:
		return false
	_moved_event = _event_index()
	_in_flight = true
	status = "Moving…"
	changed.emit()
	_request(controller.move(index))
	return true

func _request(call) -> void:
	var res = await call
	_in_flight = false
	if res is Dictionary and not res.get("ok", false):
		_moved_event = -1   # a refused request may be tried again
	render(now())

func _commit_roll(_at: int) -> void:
	pass   # never a local roll

## A validated server snapshot: present what changed (roll result hold or move unlock).
func _on_server_state(s: Dictionary, _player_id: String) -> void:
	if game == null:
		game = _render_game(s)
		render(now())
		return
	var previous: Dictionary = game.snapshot()
	var before_index := int(game.state.get("randomEventIndex", 0))
	var before_tokens: Array = game.pawns.duplicate()
	var seat: int = game.turn
	game.state = LocalMatch._normalize(load("res://scripts/game/online/match_envelope.gd").with_ruleset_key(s))
	game._ensure_presentation_fields()
	game._sync()
	_sync_roll(game)
	var current: Dictionary = game.snapshot()
	var at := now()
	if before_tokens != game.pawns:
		Feedback.committed(audio, _ledger, previous, current, Feedback.MOVE)
		committed.emit({"kind": "move", "seat": seat, "message": game.message})
		blocked_until = maxi(blocked_until, at + Pacing.interaction_delay_after_move_ms(previous, current, speed))
	elif int(game.state.get("randomEventIndex", 0)) != before_index:
		Feedback.committed(audio, _ledger, previous, current, Feedback.ROLL)
		committed.emit({"kind": "roll", "seat": seat, "message": game.message})
		blocked_until = maxi(blocked_until, at + Pacing.dice_result_hold_ms(speed))
	render(at)

func render(at: int) -> void:
	if game == null:
		return
	super.render(at)
	if game.complete:
		status = "Match finished."
	elif not _my_turn() and blocked_until <= at:
		status = "Waiting for the other players…"

## Live OnlineAutoMoveScheduler: only my own single legal move is made automatically.
func _schedule_single_legal_move(at: int) -> void:
	if _my_turn() and _event_index() != _moved_event:
		super._schedule_single_legal_move(at)

## The shared controller for this app session (created when the online service exists).
static func controller_for(tree: SceneTree):
	var existing = tree.root.get_node_or_null("OnlineMatch")
	if existing != null:
		return existing
	var account = tree.root.get_node_or_null("Account")
	if account == null or not account.available():
		return null
	var Controller = load("res://scripts/game/online/online_match_controller.gd")
	var GameApiScript = load("res://scripts/game/online/game_api.gd")
	var Transport = load("res://scripts/game/online/http_transport.gd")
	var Realtime = load("res://scripts/game/online/match_realtime_client.gd")
	var c = Controller.new()
	c.name = "OnlineMatch"
	tree.root.add_child(c)
	var transport = Transport.new()
	c.add_child(transport)
	var version := str(ProjectSettings.get_setting("application/config/version", "2.0.0"))
	var api = GameApiScript.new(account.service.base_url, transport, version)
	var inventory = load("res://scripts/game/store/cosmetic_inventory.gd").new()
	c.setup(account, api, inventory, Realtime.new(account.service.base_url, version))
	return c
