extends RefCounted
## Compact encoding of reaction-golden snapshot steps: each step stores the snapshot without
## its history plus the history events that are new or changed since the previous step;
## decoding upserts them by eventIndex and keeps the last 100 (HISTORY_WINDOW).

const WINDOW := 100

static func encode(t: int, snapshot: Dictionary, previous_history: Array) -> Dictionary:
	var known := {}
	for e in previous_history:
		known[int(e["eventIndex"])] = e
	var changed: Array = []
	for e in snapshot["history"]:
		var old = known.get(int(e["eventIndex"]))
		if old == null or old != e:
			changed.append(e)
	var s := snapshot.duplicate(true)
	s.erase("history")
	s.erase("revision")
	return {"t": t, "s": s, "h": changed}

## Returns the full snapshot; `history` is the running list (mutated in place) and
## `previous_match` the previous step's matchId (a new match starts with an empty history).
static func decode(step: Dictionary, history: Array, previous_match := "") -> Dictionary:
	if previous_match != "" and previous_match != step["s"]["matchId"]:
		history.clear()
	for e in step["h"]:
		var event: Dictionary = _ints(e)
		var replaced := false
		for i in history.size():
			if int(history[i]["eventIndex"]) == int(event["eventIndex"]):
				history[i] = event
				replaced = true
		if not replaced:
			history.append(event)
	while history.size() > WINDOW:
		history.pop_front()
	var s: Dictionary = _ints(step["s"])
	s["history"] = history.duplicate(true)
	return s

static func _ints(value):
	if value is Dictionary:
		var out := {}
		for k in value:
			out[k] = _ints(value[k])
		return out
	if value is Array:
		return value.map(func(v): return _ints(v))
	if value is float and is_equal_approx(value, roundf(value)):
		return int(value)
	return value
