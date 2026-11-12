extends Node
## Local match turn flow with live pacing: a human roll resolves after the roll delay, every
## result is held readable, input stays locked while a committed move plays out, a forced
## single move is made automatically, and robots roll/choose after their think time.
## Port of OfflineGameActions (rollOffline, scheduleComputerTurnIfNeeded, renderRolledState)
## and OfflineGameplayControlsUi (renderGame, renderCommittedMove, schedulePresentationUnlock,
## scheduleSingleLegalHumanMove) plus LudoTurnAutomationPolicy.singleAutomaticTokenIndex.
## Rules stay in the engine; this only decides WHEN actions happen.
##
## The clock is the node's own, scaled by Engine.time_scale (tests speed it up); `tick(now)`
## can also be driven directly.

const Pacing = preload("res://scripts/game/gameplay_pacing.gd")
const Bot = preload("res://scripts/game/rules/ludo_bot.gd")
const LocalDice = preload("res://scripts/game/rules/local_dice.gd")
const Feedback = preload("res://scripts/game/audio/offline_feedback.gd")
const Ledger = preload("res://scripts/game/reactions/feedback_ledger.gd")

## The UI should rebuild (state, status or interactivity changed).
signal changed
## A roll or move was committed: {kind: "roll"|"move", seat, message}.
signal committed(event: Dictionary)

var game                      # LocalMatch
var speed := Pacing.NORMAL
var difficulty := 1
var robot_mode := false
var roll_source: Callable = func() -> int: return LocalDice.roll()
## Actions are allowed (live offlineActionsAllowed); the clock and timers pause otherwise.
var allowed: Callable = func() -> bool: return true

var clock_ms := 0.0
var blocked_until := 0
var roll_due := -1            # human roll in flight (rollDelayMs)
var cpu_roll_due := -1
var status := ""
var _cpu_key = null           # computerActionRevision
var _cpu_move = null          # {due, key, token, event}
var _auto_move = null         # {due, key, token, event}
var _unlock_due := -1
var _ledger := Ledger.new()
## Sound facade (GameAudio autoload); null = silent (tests).
var audio = null

func _init(local_game = null, game_speed := Pacing.NORMAL, robots := false, robot_difficulty := 1) -> void:
	game = local_game
	speed = Pacing.speed_or_default(game_speed)
	robot_mode = robots
	difficulty = robot_difficulty

func _ready() -> void:
	if audio == null:
		audio = get_node_or_null(^"/root/GameAudio")
	render(now())

func _exit_tree() -> void:
	set_process(false)

func _process(delta: float) -> void:
	if not allowed.call():
		return
	clock_ms += delta * 1000.0 # delta is already scaled by Engine.time_scale
	tick(now())

func now() -> int:
	return int(clock_ms)

func blocked(at: int) -> bool:
	return at < blocked_until

func computer_turn() -> bool:
	return robot_mode and game != null and not game.complete and game.is_bot(game.turn)

## The dice can be rolled by the local player right now (live canRoll).
func can_roll(at: int) -> bool:
	return game != null and not game.complete and game.legal.is_empty() and not computer_turn() \
		and not blocked(at) and roll_due < 0

## Pawns may be tapped right now (live interactionPlayerId != null).
func can_move(at: int) -> bool:
	return game != null and not game.complete and not game.legal.is_empty() and not computer_turn() and not blocked(at)

func roll(at: int) -> bool:
	if not can_roll(at):
		return false
	roll_due = at + Pacing.roll_delay_ms(speed)
	if audio != null:
		audio.roll()
	status = "Rolling locally…"
	changed.emit()
	return true

func move(index: int, at: int) -> bool:
	if not can_move(at) or not game.legal.has(index):
		return false
	return _commit_move(index, at)

func tick(at: int) -> void:
	var probe_t := Time.get_ticks_usec()
	_probed_tick(at)
	preload("res://scripts/game/perf.gd").span("flow_tick", Time.get_ticks_usec() - probe_t)

func _probed_tick(at: int) -> void:
	if game == null:
		return
	if roll_due >= 0 and at >= roll_due:
		roll_due = -1
		_commit_roll(at)
	if cpu_roll_due >= 0 and at >= cpu_roll_due:
		cpu_roll_due = -1
		if computer_turn() and game.legal.is_empty() and _action_key() == _cpu_key:
			_commit_roll(at)
		else:
			_cpu_key = null
	if _cpu_move != null and at >= int(_cpu_move["due"]):
		var plan: Dictionary = _cpu_move
		_cpu_move = null
		# Never replay an old CPU decision against a newer dice event.
		if computer_turn() and _event_index() == plan["event"] and game.legal.has(plan["token"]):
			_commit_move(plan["token"], at)
		else:
			_cpu_key = null
			render(at)
	if _auto_move != null and at >= int(_auto_move["due"]):
		var plan: Dictionary = _auto_move
		_auto_move = null
		if not computer_turn() and not game.complete and _event_index() == plan["event"] and _automatic_token() == plan["token"]:
			_commit_move(plan["token"], at)
	if _unlock_due >= 0 and at >= _unlock_due:
		_unlock_due = -1
		render(at)

func _commit_roll(at: int) -> void:
	var seat: int = game.turn
	var previous: Dictionary = game.snapshot()
	if not game.apply_roll(roll_source.call()):
		status = "Roll failed"
		changed.emit()
		return
	Feedback.committed(audio, _ledger, previous, game.snapshot(), Feedback.ROLL)
	committed.emit({"kind": "roll", "seat": seat, "message": game.message})
	# renderRolledState: the result stays readable before anything else happens.
	blocked_until = maxi(blocked_until, at + Pacing.dice_result_hold_ms(speed))
	render(at)

func _commit_move(index: int, at: int) -> bool:
	var seat: int = game.turn
	var previous: Dictionary = game.snapshot()
	if not game.move(index):
		return false
	var current: Dictionary = game.snapshot()
	Feedback.committed(audio, _ledger, previous, current, Feedback.MOVE)
	committed.emit({"kind": "move", "seat": seat, "message": game.message})
	blocked_until = maxi(blocked_until, at + Pacing.interaction_delay_after_move_ms(previous, current, speed))
	render(at)
	return true

## OfflineGameplayControlsUi.renderGame: status, then either wait for the unlock or schedule
## the forced single move / the robot's next action.
func render(at: int) -> void:
	if game == null:
		return
	var remaining := maxi(blocked_until - at, 0)
	var computer := computer_turn()
	if remaining > 0:
		status = "Finishing the current paw action…"
	elif game.complete:
		status = "Rematch or change setup."
	elif computer and not game.legal.is_empty():
		status = "Computer is choosing a paw…"
	elif computer:
		status = "Computer is rolling…"
	elif not game.legal.is_empty():
		status = "Choose a glowing paw."
	else:
		status = "Tap the dice beside the active character."
	if remaining > 0:
		_unlock_due = at + remaining
		changed.emit()
		return
	blocked_until = 0
	_schedule_single_legal_move(at)
	_schedule_computer_turn(at)
	changed.emit()

func _schedule_single_legal_move(at: int) -> void:
	if game.complete or computer_turn() or game.legal.is_empty():
		return
	var token = _automatic_token()
	if token == null:
		return
	var event := _event_index()
	if _auto_move != null and _auto_move["event"] == event and _auto_move["token"] == token:
		return
	status = "Comeback bonus • one paw returns • no extra turn" if game.comeback \
		else "Only one move available • moving your paw automatically…"
	_auto_move = {"due": at + Pacing.post_roll_auto_move_delay_ms(speed), "token": token, "event": event}

func _schedule_computer_turn(at: int) -> void:
	if not computer_turn():
		_cpu_key = null
		return
	var key := _action_key()
	if _cpu_key == key:
		return
	_cpu_key = key
	if game.legal.is_empty():
		status = "Computer is rolling…"
		if audio != null:
			audio.roll()
		cpu_roll_due = at + Pacing.cpu_think_ms(speed)
		return
	var token: int = game.robot_choice(difficulty)
	status = "Computer • only one move available…" if game.legal.size() == 1 else "Computer is choosing a move…"
	_cpu_move = {"due": at + maxi(maxi(Pacing.cpu_think_ms(speed) / 2, 180), Pacing.post_roll_auto_move_delay_ms(speed)),
		"token": token, "event": _event_index()}

## computerActionRevision: randomEventIndex * 100 + turnSeat * 10 + (pending ? 1 : 0).
func _action_key() -> int:
	return int(game.state.get("randomEventIndex", 0)) * 100 + int(game.turn) * 10 + (0 if game.legal.is_empty() else 1)

func _event_index() -> int:
	return int(game.state.get("randomEventIndex", 0))

## The comeback bonus moves its single paw; otherwise LudoTurnAutomationPolicy.
func _automatic_token() -> Variant:
	if game.comeback:
		return game.legal[0] if game.legal.size() == 1 else null
	return single_automatic_token_index(game.pawns[game.turn], game.roll, game.legal)

## LudoTurnAutomationPolicy.singleAutomaticTokenIndex: one effective move (one legal token,
## or all legal tokens land on the same cell) -> lowest token index; else null.
static func single_automatic_token_index(tokens, roll_value: int, legal: Array) -> Variant:
	if roll_value < 1 or roll_value > 6 or legal.is_empty():
		return null
	var destinations := {}
	for index in legal:
		if int(index) >= tokens.size():
			return null
		var destination = Bot.destination_for_roll(int(tokens[int(index)]), roll_value)
		if destination == null:
			return null
		destinations[int(destination)] = true
	if destinations.size() != 1:
		return null
	var lowest: int = legal[0]
	for index in legal:
		lowest = mini(lowest, int(index))
	return lowest
