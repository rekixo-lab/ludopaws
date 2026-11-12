extends RefCounted
## Deterministic snapshot sequence for the reaction golden: seeded local matches played
## through LocalMatch (the match screen's path), delta-encoded (reaction_delta.gd).
## Both the generator (fixtures/reactions_gen) and tests/reaction_parity.gd rebuild it;
## the golden stores its SHA-256 so a drift in the inputs is reported, not hidden.

const LocalMatch = preload("res://scripts/game/local_match.gd")
const Delta = preload("res://tests/reaction_delta.gd")

const GAMES := [
	[[0, 2], "ROBOT", "CLASSIC_V8"], [[0, 1, 2], "PASS_AND_PLAY", "CLASSIC_V5"],
	[[0, 1, 2, 3], "TEAM_UP", "TEAM_UP_V7"], [[1, 3], "PASS_AND_PLAY", "CLASSIC_V1"],
	[[0, 1, 2, 3], "ROBOT", "CLASSIC_V6"], [[2, 3], "ROBOT", "CLASSIC_V3"],
]

static func build() -> Array:
	var rng := RandomNumberGenerator.new()
	rng.seed = 20261110
	var steps: Array = []
	var t := 1000
	for g in GAMES.size():
		var spec: Array = GAMES[g]
		var seats: Array[int] = []
		seats.assign(spec[0])
		var game = LocalMatch.new(seats, spec[1], [], spec[2])
		game.state["matchId"] = "golden-%d" % g
		var snap: Dictionary = game.snapshot()
		steps.append(Delta.encode(t, snap, []))
		var previous_history: Array = snap["history"]
		for guard in 1200:
			if game.complete:
				break
			if game.legal.is_empty():
				# Bias some rolls so sixes, third sixes and poor streaks all occur.
				var value := rng.randi_range(1, 6)
				if rng.randf() < 0.12:
					value = 6
				elif rng.randf() < 0.08:
					value = 1
				game.apply_roll(value)
			else:
				var choice: int = game.robot_choice(2) if rng.randf() < 0.7 else game.legal[rng.randi_range(0, game.legal.size() - 1)]
				game.move(choice)
			t += rng.randi_range(40, 1400) + (26000 if rng.randf() < 0.03 else 0)
			snap = game.snapshot()
			steps.append(Delta.encode(t, snap, previous_history))
			previous_history = snap["history"]
		t += 5000
	return steps

static func text(steps: Array) -> String:
	return JSON.stringify({"steps": steps})
