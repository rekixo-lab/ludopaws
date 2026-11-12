@tool
extends RefCounted
## Stateful presentation scheduler: once-only keys, per-player voice cooldowns, batches per
## event, a 900 ms playback window and priority interrupts. Never touches game state.
## Port of LudoPawsReactionDirector.kt.

const Seq = preload("res://scripts/game/reactions/seq.gd")

const PRIORITY_CAPTURE := 86
const PRIORITY_VICTORY := 100
const NONE := -9223372036854775807 - 1 # Long.MIN_VALUE / Int.MIN_VALUE sentinel

var playback_window_ms := 900
var max_consumed_keys := 512
var max_queued_batches := 12

var _active_match_id = null
var _active_until_ms: int = NONE
var _active_priority: int = NONE
var _consumed := {}       # insertion-ordered set (Godot Dictionaries keep insertion order)
var _queued: Array = []   # [{matchId, eventIndex, reactions, priority}]
var _last_cue_at := {}

func _init(window_ms := 900, max_keys := 512, max_batches := 12) -> void:
	playback_window_ms = window_ms
	max_consumed_keys = max_keys
	max_queued_batches = max_batches

## Returns {reactions, interrupted, queuedBatchCount}.
func submit(reactions: Array, now_ms: int) -> Dictionary:
	var incoming = reactions[0]["matchId"] if not reactions.is_empty() and str(reactions[0]["matchId"]).strip_edges() != "" else null
	if incoming != null and incoming != _active_match_id:
		_reset_for_match(incoming)
	if now_ms >= _active_until_ms:
		_active_until_ms = NONE
		_active_priority = NONE
	var ordered := Seq.stable_sort(reactions, func(a, b):
		if a["priority"] != b["priority"]:
			return a["priority"] > b["priority"]
		if a["eventIndex"] != b["eventIndex"]:
			return a["eventIndex"] < b["eventIndex"]
		if a["playerId"] != b["playerId"]:
			return a["playerId"] < b["playerId"]
		return _token(a) < _token(b))
	var accepted: Array = []
	for r in ordered:
		if _remember_once(r) and _cooldown_allows(r, now_ms):
			accepted.append(r)
	_enqueue(_batches_for(accepted))
	var active := now_ms < _active_until_ms and _active_priority != NONE
	var best = _queued[0] if not _queued.is_empty() else null
	if active and best != null and _should_interrupt(best["priority"], _active_priority):
		_queued.remove_at(0)
		_start(best, now_ms)
		return {"reactions": best["reactions"], "interrupted": true, "queuedBatchCount": _queued.size()}
	if active:
		return {"reactions": [], "interrupted": false, "queuedBatchCount": _queued.size()}
	if _queued.is_empty():
		return {"reactions": [], "interrupted": false, "queuedBatchCount": 0}
	var next: Dictionary = _queued.pop_front()
	_start(next, now_ms)
	return {"reactions": next["reactions"], "interrupted": false, "queuedBatchCount": _queued.size()}

func clear() -> void:
	_active_match_id = null
	_active_until_ms = NONE
	_active_priority = NONE
	_consumed.clear()
	_queued.clear()
	_last_cue_at.clear()

func _reset_for_match(match_id) -> void:
	clear()
	_active_match_id = match_id

static func _token(r: Dictionary) -> int:
	return -1 if r["tokenIndex"] == null else int(r["tokenIndex"])

func _remember_once(r: Dictionary) -> bool:
	var key := _stable_key(r)
	if _consumed.has(key):
		return false
	_consumed[key] = true
	while _consumed.size() > max_consumed_keys:
		_consumed.erase(_consumed.keys()[0])
	return true

func _cooldown_allows(r: Dictionary, now_ms: int) -> bool:
	var cooldown := cooldown_ms(r["voiceCue"])
	if cooldown <= 0:
		return true
	var key: String = r["playerId"] + ":" + r["voiceCue"]
	if _last_cue_at.has(key) and now_ms - int(_last_cue_at[key]) < cooldown:
		return false
	_last_cue_at[key] = now_ms
	return true

static func cooldown_ms(cue: String) -> int:
	match cue:
		"IDLE": return 24000
		"NERVOUS": return 8000
		"PROUD": return 12000
		"FRUSTRATED": return 3500
		"SIX": return 750
		"YARD_EXIT": return 1000
		"SAFE": return 1400
		"HOME_LANE": return 1500
	return 0

static func _batches_for(reactions: Array) -> Array:
	var groups := {}
	for r in reactions:
		var key := "%s:%d" % [r["matchId"], r["eventIndex"]]
		if not groups.has(key):
			groups[key] = []
		groups[key].append(r)
	var batches: Array = []
	for key in groups:
		var grouped: Array = Seq.stable_sort(groups[key], func(a, b): return a["priority"] > b["priority"])
		var top: int = NONE
		for r in grouped:
			top = maxi(top, int(r["priority"]))
		batches.append({"matchId": grouped[0]["matchId"], "eventIndex": grouped[0]["eventIndex"], "reactions": grouped, "priority": top})
	return Seq.stable_sort(batches, _batch_less)

static func _batch_less(a: Dictionary, b: Dictionary) -> bool:
	if a["priority"] != b["priority"]:
		return a["priority"] > b["priority"]
	return a["eventIndex"] < b["eventIndex"]

func _enqueue(incoming: Array) -> void:
	if incoming.is_empty():
		return
	_queued.append_array(incoming)
	_queued = Seq.stable_sort(_queued, _batch_less)
	while _queued.size() > max_queued_batches:
		_queued.pop_back()

func _start(batch: Dictionary, now_ms: int) -> void:
	_active_match_id = batch["matchId"]
	_active_priority = batch["priority"]
	_active_until_ms = now_ms + playback_window_ms

static func _should_interrupt(incoming: int, active: int) -> bool:
	return incoming >= PRIORITY_VICTORY or (incoming >= PRIORITY_CAPTURE and incoming > active)

static func _stable_key(r: Dictionary) -> String:
	if str(r.get("reactionKey", "")) != "":
		return r["reactionKey"]
	var id: String = r["matchId"] if str(r["matchId"]).strip_edges() != "" else "legacy"
	var type = r["momentType"] if r.get("momentType") != null else r["voiceCue"]
	return ":".join([id, str(r["eventIndex"]), type, r["playerId"], "" if r["tokenIndex"] == null else str(r["tokenIndex"])])
