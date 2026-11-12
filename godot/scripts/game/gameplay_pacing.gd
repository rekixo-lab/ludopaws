extends RefCounted
## Presentation pacing for readable play: how long the UI waits before exposing the next
## action while dice, movement, capture and settle animations stay visible. Rules and the
## committed match state never depend on these values.
## Port of LudoPawsGameplayPacingPolicy.kt and GameSpeed (GameSettingsStore.kt).

const MotionPolicy = preload("res://scripts/game/board3d/pawn_motion_policy.gd")

const SLOW := "SLOW"
const NORMAL := "NORMAL"
const FAST := "FAST"
const SPEEDS := [SLOW, NORMAL, FAST]

const DICE_SETTLE_DURATION_MS := 450
const FORWARD_LANDING_SETTLE_MS := 90
## LudoPawsHomeEntryPolicy.INTERACTION_HOLD_MS
const HOME_ENTRY_INTERACTION_HOLD_MS := 900

## GameSpeed: moveStepMs, rollDelayMs, cpuThinkMs.
const _SPEED := {
	SLOW: {"move_step": 360, "roll_delay": 760, "cpu_think": 1000},
	NORMAL: {"move_step": 280, "roll_delay": 600, "cpu_think": 720},
	FAST: {"move_step": 210, "roll_delay": 420, "cpu_think": 500},
}

static func speed_or_default(speed) -> String:
	return str(speed) if SPEEDS.has(str(speed)) else NORMAL

## GameSpeed.next(): SLOW -> NORMAL -> FAST -> SLOW.
static func next_speed(speed: String) -> String:
	return SPEEDS[(SPEEDS.find(speed_or_default(speed)) + 1) % SPEEDS.size()]

static func move_step_ms(speed: String) -> int:
	return _SPEED[speed_or_default(speed)]["move_step"]

static func roll_delay_ms(speed: String) -> int:
	return _SPEED[speed_or_default(speed)]["roll_delay"]

static func cpu_think_ms(speed: String) -> int:
	return _SPEED[speed_or_default(speed)]["cpu_think"]

static func dice_result_hold_ms(speed: String) -> int:
	return {SLOW: 850, NORMAL: 700, FAST: 520}[speed_or_default(speed)]

## Small tail after the common result hold before a forced single move.
static func post_roll_auto_move_delay_ms(speed: String) -> int:
	return {SLOW: 150, NORMAL: 100, FAST: 80}[speed_or_default(speed)]

static func capture_return_duration_ms(speed: String) -> int:
	return {SLOW: 1500, NORMAL: 1300, FAST: 1050}[speed_or_default(speed)]

## Travel time to the final cell, excluding the landing settle.
static func forward_travel_duration_ms(speed: String, visual_steps: int) -> int:
	return maxi(move_step_ms(speed), maxi(visual_steps, 1) * move_step_ms(speed))

static func forward_animation_duration_ms(speed: String, visual_steps: int) -> int:
	return forward_travel_duration_ms(speed, visual_steps) + FORWARD_LANDING_SETTLE_MS

static func capture_contact_delay_ms(speed: String, visual_steps: int) -> int:
	return maxi(forward_animation_duration_ms(speed, visual_steps) - FORWARD_LANDING_SETTLE_MS, 0)

## One movement audio tick per visual board step, locked to the forward animation duration.
static func movement_audio_step_duration_ms(forward_duration_ms: int, visual_steps: int) -> int:
	if visual_steps <= 0:
		return 0
	return maxi(forward_duration_ms - FORWARD_LANDING_SETTLE_MS, 0) / visual_steps

static func remaining_presentation_delay_ms(total_ms: int, already_presented_ms: int) -> int:
	return maxi(total_ms - maxi(already_presented_ms, 0), 0)

static func post_move_breath_ms(speed: String, extra_turn: bool) -> int:
	var base: int = {SLOW: 560, NORMAL: 450, FAST: 330}[speed_or_default(speed)]
	var extra_roll_hold: int = {SLOW: 720, NORMAL: 600, FAST: 450}[speed_or_default(speed)]
	return maxi(base, extra_roll_hold) if extra_turn else base

## How long input stays locked after a committed move (snapshots in live MatchSnapshot shape:
## matchId, status, turnSeat, players[{playerId, tokens}]).
static func interaction_delay_after_move_ms(previous, current: Dictionary, speed: String, include_home_entry := true) -> int:
	if previous == null or previous["matchId"] != current["matchId"]:
		return post_move_breath_ms(speed, false)
	var forward = null
	var has_capture := false
	for plan in plans(previous, current):
		if plan["kind"] == "forward" and forward == null:
			forward = plan
		elif plan["kind"] == "return":
			has_capture = true
	var forward_duration := forward_travel_duration_ms(speed, int(forward["steps"])) if forward != null else 0
	var extra_turn: bool = previous["status"] == "ACTIVE" and current["status"] == "ACTIVE" \
		and int(previous["turnSeat"]) == int(current["turnSeat"])
	var capture_duration := capture_return_duration_ms(speed) if has_capture else 0
	if include_home_entry and newly_complete(previous, current):
		return forward_animation_duration_ms(speed, int(forward["steps"]) if forward != null else 1) + HOME_ENTRY_INTERACTION_HOLD_MS
	return forward_duration + capture_duration + post_move_breath_ms(speed, extra_turn)

## LudoPawsPawnAnimationPolicy.plans: every animated token change, players then tokens in order.
static func plans(previous, current) -> Array:
	var out: Array = []
	if previous == null or current == null or previous["matchId"] != current["matchId"]:
		return out
	for player in current["players"]:
		var before = null
		for p in previous["players"]:
			if p["playerId"] == player["playerId"]:
				before = p
				break
		if before == null:
			continue
		var tokens: Array = player["tokens"]
		for i in tokens.size():
			if i >= before["tokens"].size():
				continue
			var plan = MotionPolicy.transition(int(before["tokens"][i]), int(tokens[i]))
			if plan != null:
				out.append(plan)
	return out

## LudoPawsHomeEntryPolicy.newlyComplete.
static func newly_complete(previous, current: Dictionary) -> bool:
	if previous == null or previous["matchId"] != current["matchId"]:
		return false
	for player in current["players"]:
		if not _complete(player):
			continue
		for p in previous["players"]:
			if p["playerId"] == player["playerId"] and not _complete(p):
				return true
	return false

static func _complete(player: Dictionary) -> bool:
	var tokens: Array = player["tokens"]
	return tokens.size() == 4 and tokens.all(func(t): return int(t) == 57)
