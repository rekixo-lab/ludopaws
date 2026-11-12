extends RefCounted
## Adaptive frame pacing for the board's 3D pawn layer: ~60 fps while something moves or
## reacts, ~30 fps while players think or wait. Presentation-only; never touches game state,
## input, dice or animation timing (animations sample the clock, so fewer frames just skip).
## Port of LudoPaws3DRenderCadencePolicy.kt and LudoPaws3DDisplayPacer.kt.

const ACTIVE_FRAME_DELAY_MS := 16
const IDLE_FRAME_DELAY_MS := 33
const HOME_CELEBRATION_TAIL_MS := 1400
const FORWARD_LANDING_SETTLE_MS := 90
const SECONDARY_SETTLE_TAIL_MS := 250
const HOME := 57

## LudoPaws3DRenderCadencePolicy.captureContactDelayMillis.
static func capture_contact_delay_ms(forward_duration_ms: int) -> int:
	return maxi(forward_duration_ms - FORWARD_LANDING_SETTLE_MS, 0)

## scene: {home_entries: {id: {started_ms, rank}}, forward: {to, started_ms, duration_ms} or null,
##         captures: [{started_ms, duration_ms}], reactions: [{started_ms, duration_ms}]}.
## home_entry_end(rank) gives the entry's animated length (rank 1 includes the plaque).
static func frame_delay_ms(scene: Dictionary, now_ms: int, plaque_start_ms: int, entry_duration_ms: int) -> int:
	return ACTIVE_FRAME_DELAY_MS if has_active_animation(scene, now_ms, plaque_start_ms, entry_duration_ms) else IDLE_FRAME_DELAY_MS

static func has_active_animation(scene: Dictionary, now_ms: int, plaque_start_ms: int, entry_duration_ms: int) -> bool:
	for entry in scene.get("home_entries", {}).values():
		var end := plaque_start_ms + 3000 if int(entry.get("rank", 0)) == 1 else entry_duration_ms
		var started := int(entry.get("started_ms", 0))
		if started > 0 and now_ms - started < end:
			return true
	var forward = scene.get("forward")
	if forward != null and int(forward["duration_ms"]) > 0:
		var tail := HOME_CELEBRATION_TAIL_MS if int(forward["to"]) == HOME else SECONDARY_SETTLE_TAIL_MS
		if now_ms < int(forward["started_ms"]) + int(forward["duration_ms"]) + tail:
			return true
	for capture in scene.get("captures", []):
		if int(capture["duration_ms"]) > 0 and now_ms < int(capture["started_ms"]) + int(capture["duration_ms"]):
			return true
	for reaction in scene.get("reactions", []):
		if int(reaction["duration_ms"]) > 0 and now_ms < int(reaction["started_ms"]) + int(reaction["duration_ms"]):
			return true
	return false

## LudoPaws3DDisplayPacer: picks whole display refreshes instead of drifting against vsync.
class Pacer:
	const NONE := -9223372036854775807
	var last_frame_ns := NONE

	func reset() -> void:
		last_frame_ns = NONE

	func should_render(frame_time_ns: int, refresh_period_ns: int, frame_delay_ms: int) -> bool:
		var refresh := maxi(refresh_period_ns, 1)
		var refreshes := maxi(roundi(frame_delay_ms * 1000000.0 / refresh), 1)
		var interval := refresh * refreshes
		# Tolerate timestamp rounding without skipping a whole refresh.
		if last_frame_ns != NONE and frame_time_ns - last_frame_ns < interval - refresh / 4:
			return false
		# A late frame replaces missed frames; never build a catch-up queue.
		last_frame_ns = frame_time_ns
		return true
