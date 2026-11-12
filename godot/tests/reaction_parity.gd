extends RefCounted
## Compares the Godot reaction pipeline with tests/fixtures/reactions_golden.json, recorded
## from the unchanged live Kotlin pipeline (fixtures/reactions_gen/generate.sh) over the
## snapshot sequence that tests/reaction_inputs.gd rebuilds. Per step: moments, derived
## reactions, the director's decision, the feedback ledger, the idle clock/key/delay and
## idle reactions; plus every FX plan.

const Inputs = preload("res://tests/reaction_inputs.gd")
const Delta = preload("res://tests/reaction_delta.gd")
const Detector = preload("res://scripts/game/reactions/moment_detector.gd")
const Reactions = preload("res://scripts/game/reactions/reaction_engine.gd")
const Director = preload("res://scripts/game/reactions/reaction_director.gd")
const Idle = preload("res://scripts/game/reactions/idle_reaction_policy.gd")
const Ledger = preload("res://scripts/game/reactions/feedback_ledger.gd")
const Fx = preload("res://scripts/game/reactions/fx_policy.gd")

static func run() -> Array:
	var failures: Array = []
	var file := FileAccess.open("res://tests/fixtures/reactions_golden.json", FileAccess.READ)
	if file == null:
		return ["reactions golden fixture missing"]
	var golden: Dictionary = JSON.parse_string(file.get_as_text())
	var steps := Inputs.build()
	if Inputs.text(steps).sha256_text() != golden["inputHash"]:
		return ["reaction inputs changed since the golden was recorded; rerun tests/fixtures/reactions_gen/generate.sh"]
	var rows: Array = golden["steps"]
	if rows.size() != steps.size():
		return ["reaction golden has %d steps, inputs %d" % [rows.size(), steps.size()]]
	var director := Director.new()
	var ledger := Ledger.new()
	var history: Array = []
	var previous = null
	var meaningful = null
	var last_change := 0
	var last_idle = null
	for i in steps.size():
		var step: Dictionary = steps[i]
		var t := int(step["t"])
		var current := Delta.decode(step, history, previous["matchId"] if previous != null else "")
		var next_key = Idle.meaningful_state_key(current)
		if current["status"] != "ACTIVE" or next_key != meaningful:
			meaningful = next_key
			last_change = t
			last_idle = null
		var moments: Array = Detector.detect(previous, current).map(func(m): return "|".join([m["type"], m["playerId"],
			str(m["seat"]), str(m["eventIndex"]), _s(m["tokenIndex"]), _s(m["relatedPlayerId"]), _s(m["value"])]))
		var derived := Reactions.derive(previous, current)
		var decision := director.submit(derived, t)
		var kept: Array = ledger.filter_reactions(derived).map(func(r): return r["reactionKey"])
		var probe := int(steps[i + 1]["t"]) - 1 if i + 1 < steps.size() else t + 30000
		var delay = Idle.delay_until_eligible_ms(current, last_change, probe)
		var idle_key = Idle.idle_reaction_key(current)
		var idle: Array = []
		if delay != null and delay == 0 and idle_key != null and idle_key != last_idle:
			idle = Reactions.derive_idle(current, probe, last_change, Idle.IDLE_THRESHOLD_MS)
			if not idle.is_empty():
				last_idle = idle_key
		var row: Dictionary = rows[i]
		var got := {
			"m": moments, "d": derived.map(_r), "p": decision["reactions"].map(func(r): return r["reactionKey"]),
			"i": decision["interrupted"], "q": decision["queuedBatchCount"],
			"l": kept if kept.size() != derived.size() else [], "k": meaningful, "ik": idle_key,
			"w": delay, "idle": idle.map(_r),
		}
		var want := {"m": row.get("m", []), "d": row.get("d", []), "p": row.get("p", []), "i": row.get("i", false),
			"q": int(row.get("q", 0)), "l": row.get("l", []), "k": row.get("k"), "ik": row.get("ik"),
			"w": null if row.get("w") == null else int(row["w"]), "idle": row.get("idle", [])}
		for key in want:
			if str(got[key]) != str(want[key]):
				failures.append("step %d %s: got %s want %s" % [i, key, got[key], want[key]])
		if failures.size() > 12:
			return failures
		previous = current
	for cue in golden["fx"]:
		var p := Fx.plan(cue)
		var mine := [p["durationMs"], p["particleCount"], p["allowTranslation"], p["allowRotation"], p["allowShake"], p["allowConfetti"]]
		var want: Array = golden["fx"][cue]
		want = [int(want[0]), int(want[1]), want[2], want[3], want[4], want[5]]
		if str(mine) != str(want):
			failures.append("fx %s: got %s want %s" % [cue, mine, want])
	return failures

static func _s(value) -> String:
	return "" if value == null else str(value)

static func _r(r: Dictionary) -> String:
	return "|".join([r["reactionKey"], str(r["priority"]), r["animationCue"], r["voiceCue"], str(r["seat"])])
