extends SceneTree
## Headless rule tests. Run from the project root:
##   godot --headless --path . --script res://tests/run_tests.gd
## Exit code 0 = all passed.

const Rules = preload("res://scripts/game/rules/ludo_engine.gd")
const R = preload("res://scripts/game/rules/ludo_rulesets.gd")
const Bot = preload("res://scripts/game/rules/ludo_bot.gd")
const LocalDice = preload("res://scripts/game/rules/local_dice.gd")
const LocalMatch = preload("res://scripts/game/local_match.gd")
const GOLDEN := "res://tests/fixtures/server_rules_golden.json"

var failures := 0
var checks := 0

func _initialize() -> void:
	_unit_tests()
	_golden_server_parity()
	_bot_and_dice()
	_local_match_facade()
	_artwork_random_parity()
	_character_parity()
	_reaction_parity()
	_capture_return_parity()
	_capture_return_on_board()
	_road_presentation_parity()
	_render_cadence()
	_motion_policy_parity()
	_fx_painter_parity()
	_fx_overlay_timeline()
	_yard_paw_prints()
	_gameplay_pacing()
	_turn_flow()
	_procedural_audio_parity()
	_game_audio()
	_voice_plan_parity()
	_voice_player()
	_music()
	_result_model()
	_legal_hints()
	_legal_hint_contrast()
	_starter_assignment()
	_store_catalog()
	_boards()
	_dice_parity()
	await _store_flows()
	await _rewards_flow()
	_native_prefs_and_wallet_cache()
	_catalog_parser()
	_saved_match()
	_player_session_store()
	_ads_rules()
	await _online_flow()
	_demo_match()
	_match_rail()
	_tap_hint_policy()
	_offline_setup()
	_profile_and_weekly()
	print("Rule tests: %d checks, %d failures" % [checks, failures])
	quit(1 if failures > 0 else 0)

func _check(condition: bool, label: String) -> void:
	checks += 1
	if not condition:
		failures += 1
		printerr("FAIL: ", label)

func _players(count: int, team := false) -> Array:
	var out: Array = []
	for i in count:
		out.append({"playerId": "p%d" % i, "color": R.COLORS[i]})
	return out

func _unit_tests() -> void:
	_check(Rules.destination_for_roll(-1, 6) == 0, "six leaves yard")
	_check(Rules.destination_for_roll(-1, 5) == null, "five cannot leave yard")
	_check(Rules.destination_for_roll(50, 1) == 52, "track 50 + 1 enters home lane 52")
	_check(Rules.destination_for_roll(51, 1) == 53, "legacy 51 normalizes to 52")
	_check(Rules.destination_for_roll(52, 5) == 57, "exact roll reaches home")
	_check(Rules.destination_for_roll(53, 5) == null, "overshoot is illegal")
	_check(Rules.destination_for_roll(57, 1) == null, "home token cannot move")
	_check(Rules.global_cell("GREEN", 0) == 13, "green start offset 13")
	_check(Rules.global_cell("BLUE", 20) == 7, "global cell wraps")
	_check(Rules.global_cell("RED", 52) == null, "home lane has no global cell")

	# Opening bonus: first roll counts as six (CLASSIC_V8).
	var state := Rules.new_match(_players(2), {"matchMode": "PASS_AND_PLAY"})
	var result := Rules.roll(state, 2)
	_check(result["openingRollApplied"] == true and result["effectiveOutcome"] == 6, "opening bonus applies")
	_check(result["state"]["consecutiveSixes"][0] == 0, "opening six not counted")
	result = Rules.move(result["state"], 0)
	_check(result["state"]["players"][0]["tokens"][0] == 0, "token entered start")
	_check(result["extraTurn"] == true and result["state"]["turnSeat"] == 0, "six grants extra turn")

	# Third consecutive six forfeits.
	state = Rules.new_match(_players(2), {"matchMode": "PASS_AND_PLAY", "rulesetKey": "CLASSIC_V2"})
	state["players"][0]["tokens"] = [10, R.YARD, R.YARD, R.YARD]
	for i in 2:
		result = Rules.roll(state, 6)
		state = Rules.move(result["state"], 0)["state"]
	result = Rules.roll(state, 6)
	_check(result["forfeitedThirdSix"] == true and result["state"]["turnSeat"] == 1, "third six passes turn")

	# Capture on an unsafe cell, protected on a safe cell and on a stack.
	state = Rules.new_match(_players(2), {"matchMode": "PASS_AND_PLAY", "rulesetKey": "CLASSIC_V2"})
	state["players"][0]["tokens"] = [3, R.YARD, R.YARD, R.YARD]
	state["players"][1]["tokens"] = [44, R.YARD, R.YARD, R.YARD] # GREEN 44 -> global 5 == RED 5
	result = Rules.move(Rules.roll(state, 2)["state"], 0)
	_check(result["captures"] == 1 and result["state"]["players"][1]["tokens"][0] == R.YARD, "capture sends to yard")
	_check(result["extraTurn"] == true, "capture grants extra turn")
	state["players"][1]["tokens"] = [47, R.YARD, R.YARD, R.YARD] # global 8 is safe
	result = Rules.move(Rules.roll(state, 5)["state"], 0)
	_check(result["captures"] == 0, "safe cell blocks capture")
	state["players"][1]["tokens"] = [44, 44, R.YARD, R.YARD]
	result = Rules.move(Rules.roll(state, 2)["state"], 0)
	_check(result["captures"] == 0, "opponent stack is protected")

	# No legal move passes the turn (non-six).
	state = Rules.new_match(_players(2), {"matchMode": "PASS_AND_PLAY", "rulesetKey": "CLASSIC_V2"})
	result = Rules.roll(state, 3)
	_check(result["awaitingMove"] == false and result["state"]["turnSeat"] == 1, "no legal move passes turn")

	# Finish order (v6+): first finisher wins, play continues for places.
	state = Rules.new_match(_players(3), {"matchMode": "PASS_AND_PLAY"})
	state["openingRollConsumed"] = [true, true, true]
	state["players"][0]["tokens"] = [57, 57, 57, 56]
	result = Rules.move(Rules.roll(state, 1)["state"], 3)
	_check(result["state"]["status"] == "ACTIVE" and result["state"]["finishOrderPlayerIds"] == ["p0"], "first finisher ranked, game continues")
	_check(result["state"]["turnSeat"] == 1, "finished player is skipped")

	# Offline perimeter rotation: seats RED, YELLOW, GREEN play RED -> GREEN -> YELLOW.
	var custom: Array = [{"playerId": "a", "color": "RED"}, {"playerId": "b", "color": "YELLOW"}, {"playerId": "c", "color": "GREEN"}]
	state = Rules.new_match(custom, {"matchMode": "PASS_AND_PLAY", "rulesetKey": "CLASSIC_V2", "turnRotation": "PERIMETER"})
	state = Rules.roll(state, 3)["state"]
	_check(state["turnSeat"] == 2, "perimeter rotation RED -> GREEN")
	state = Rules.roll(state, 3)["state"]
	_check(state["turnSeat"] == 1, "perimeter rotation GREEN -> YELLOW")
	state = Rules.new_match(custom, {"matchMode": "PASS_AND_PLAY", "rulesetKey": "CLASSIC_V2"})
	_check(Rules.roll(state, 3)["state"]["turnSeat"] == 1, "default seat rotation unchanged")

	# Team Up: partner plays for finished teammate; both must finish.
	state = Rules.new_match(_players(4), {"matchMode": "TEAM_UP"})
	_check(state["rulesetKey"] == R.DEFAULT_TEAM, "team uses team ruleset")
	state["players"][0]["tokens"] = [57, 57, 57, 57]
	_check(Rules.acting_seat(state) == 2, "partner acts for finished player")

func _golden_server_parity() -> void:
	var file := FileAccess.open(GOLDEN, FileAccess.READ)
	if file == null:
		_check(false, "golden fixture missing")
		return
	var data = JSON.parse_string(file.get_as_text())
	var games: Array = data["games"]
	var mismatches := 0
	for g in games.size():
		var game: Dictionary = games[g]
		var players: Array = []
		for i in int(game["players"]):
			players.append({"playerId": "p%d" % i, "color": game["colors"][i]})
		var state := Rules.new_match(players, {"matchMode": game["matchMode"], "rulesetKey": game["rulesetKey"]})
		var steps: Array = game["steps"]
		for s in steps.size():
			var step: Dictionary = steps[s]
			var label := "%s game %d step %d" % [game["rulesetKey"], g, s]
			var rolled := Rules.roll(state, int(step["r"]))
			if not rolled.get("ok", false):
				mismatches += 1
				_check(false, label + " roll rejected: " + str(rolled.get("code")))
				break
			state = rolled["state"]
			var ok := int(rolled["effectiveOutcome"]) == int(step["e"]) and _same_ints(rolled["legalTokenIndexes"], step["l"])
			if int(step["m"]) >= 0:
				var moved := Rules.move(state, int(step["m"]))
				if not moved.get("ok", false):
					ok = false
				else:
					state = moved["state"]
					ok = ok and int(moved["captures"]) == int(step["x"]) and (1 if moved["extraTurn"] else 0) == int(step["et"])
			ok = ok and _same_snapshot(state, step["a"])
			if not ok:
				mismatches += 1
				_check(false, label + " diverged from live server")
				break
	_check(mismatches == 0, "all %d server golden games match" % games.size())

func _same_snapshot(state: Dictionary, a: Dictionary) -> bool:
	if (1 if state["status"] == "FINISHED" else 0) != int(a["s"]):
		return false
	if int(state["turnSeat"]) != int(a["t"]):
		return false
	for i in state["players"].size():
		if not _same_ints(state["players"][i]["tokens"], a["k"][i]):
			return false
	if not _same_ints(state["consecutiveSixes"], a["c"]):
		return false
	if a.has("w") and not _same_ints(state.get("comebackWaits", []), a["w"]):
		return false
	if a.has("f"):
		var seats: Array = []
		for id in state.get("finishOrderPlayerIds", []):
			seats.append(Rules.seat_of(state, id))
		if not _same_ints(seats, a["f"]):
			return false
	var winner := -1 if state.get("winnerPlayerId") == null else Rules.seat_of(state, state["winnerPlayerId"])
	if winner != int(a["ws"]):
		return false
	if a.has("wt") and state.get("winnerTeamId") != a["wt"]:
		return false
	return true

func _same_ints(left: Array, right: Array) -> bool:
	if left.size() != right.size():
		return false
	for i in left.size():
		if int(left[i]) != int(right[i]):
			return false
	return true

func _bot_and_dice() -> void:
	var counts := [0, 0, 0, 0, 0, 0]
	for i in 6000:
		var value := LocalDice.roll()
		if value < 1 or value > 6:
			_check(false, "die out of range: %d" % value)
			continue
		counts[value - 1] += 1
	for c in counts:
		_check(c > 850 and c < 1150, "die roughly uniform (%s)" % str(counts))
	var state := Rules.new_match(_players(2), {"matchMode": "ROBOT", "rulesetKey": "CLASSIC_V2"})
	state["players"][0]["tokens"] = [3, 20, R.YARD, R.YARD]
	state["players"][1]["tokens"] = [44, R.YARD, R.YARD, R.YARD]
	state = Rules.roll(state, 2)["state"]
	_check(Bot.choose(state, Bot.Difficulty.NORMAL) == 0, "bot prefers capture")
	_robot_policy_parity()

## Replays vectors produced by the live Kotlin LudoPawsComputerMovePolicy.
func _robot_policy_parity() -> void:
	var file := FileAccess.open("res://tests/fixtures/robot_policy_golden.json", FileAccess.READ)
	if file == null:
		_check(false, "robot golden fixture missing")
		return
	var cases: Array = JSON.parse_string(file.get_as_text())["cases"]
	var mismatches := 0
	for c in cases.size():
		var vector: Dictionary = cases[c]
		var players: Array = []
		for s in vector["k"].size():
			var tokens: Array = []
			for t in vector["k"][s]:
				tokens.append(int(t))
			players.append({"playerId": "p%d" % s, "color": R.COLORS[s], "tokens": tokens,
				"teamId": R.TEAM_ASSIGNMENTS[s] if vector["team"] else null})
		var state := {"players": players}
		var seat := int(vector["seat"])
		var legal: Array = vector["l"]
		var ok := true
		for i in legal.size():
			var e := Bot.evaluate(state, seat, int(legal[i]), int(vector["o"]), vector["cb"])
			if e.is_empty() or int(e["score"]) != int(vector["sc"][i]):
				ok = false
		ok = ok and Bot.choose_token(state, seat, legal, int(vector["o"]), vector["cb"]) == int(vector["pick"])
		if not ok:
			mismatches += 1
			if mismatches <= 5:
				_check(false, "robot case %d diverged from Kotlin policy" % c)
	_check(mismatches == 0 and cases.size() > 0, "all %d robot policy cases match Kotlin (%d off)" % [cases.size(), mismatches])

func _local_match_facade() -> void:
	var seats: Array[int] = [0, 2]
	var game := LocalMatch.new(seats, "PASS_AND_PLAY")
	_check(game.apply_roll(1), "facade roll")
	_check(game.roll == 6 and game.legal.size() == 4, "facade opening bonus legal set")
	_check(game.move(game.legal[0]), "facade move")
	var saved = JSON.parse_string(JSON.stringify(game.to_saved()))
	var restored = LocalMatch.from_saved(saved)
	_check(restored.pawns[0] == game.pawns[0] and restored.turn == game.turn, "save/restore round-trip")
	# Play a complete robot-vs-robot match to the end.
	var auto := LocalMatch.new(seats, "ROBOT")
	var guard := 0
	while not auto.complete and guard < 5000:
		guard += 1
		if auto.legal.is_empty():
			auto.apply_roll(LocalDice.roll())
		else:
			auto.move(auto.robot_choice(1))
	_check(auto.complete and auto.finished.size() == 2, "full automated match completes")

## Board artwork PRNGs must match the JVM so ferns/specks/grain land where the live app puts them.
## Expected values printed by kotlin.random.Random / java.util.Random on JDK 21.
func _artwork_random_parity() -> void:
	var KR = load("res://scripts/game/board3d/kotlin_random.gd")
	var k = KR.XorWow.new(742)
	var expected := [0.9335368, 0.9025396, 0.089083076, 0.32901096, 0.7120928]
	var ok := true
	for value in expected:
		ok = ok and absf(k.next_float() - value) < 0.0000005
	_check(ok, "kotlin.random.Random(742) parity")
	var k2 = KR.XorWow.new(-5)
	ok = true
	for value in [0.38695252, 0.46636927, 0.9038157]:
		ok = ok and absf(k2.next_float() - value) < 0.0000005
	_check(ok, "kotlin.random.Random(-5) parity")
	var j = KR.JavaRandom.new(0x53544F4E)
	_check(j.next_boolean() == false and j.next_int(15) == 3 and absf(j.next_float() - 0.5857598) < 0.0000005 \
		and j.next_int(7) == 1 and absf(j.next_float() - 0.19553834) < 0.0000005, "java.util.Random parity")

## Every ported character must match golden vectors from the unchanged live Kotlin renderer.
func _character_parity() -> void:
	var Parity = load("res://tests/character_parity.gd")
	var Catalog = load("res://scripts/game/characters/catalog.gd")
	# A script that fails to compile must fail the suite, never skip its checks.
	_check(Parity != null and Parity.can_instantiate(), "character parity script compiles")
	_check(Catalog != null and Catalog.can_instantiate(), "character catalog compiles")
	if Parity == null or Catalog == null or not Parity.can_instantiate() or not Catalog.can_instantiate():
		return
	for species in Catalog.RENDERED:
		var problems: Array = Parity.run(species)
		for i in mini(problems.size(), 8):
			printerr("  ", problems[i])
		_check(problems.is_empty(), "%s matches the live renderer (%d differences)" % [species, problems.size()])

## The reaction pipeline must match the unchanged live Kotlin pipeline step for step.
func _reaction_parity() -> void:
	var Parity = load("res://tests/reaction_parity.gd")
	_check(Parity != null and Parity.can_instantiate(), "reaction parity script compiles")
	if Parity == null or not Parity.can_instantiate():
		return
	var problems: Array = Parity.run()
	for i in mini(problems.size(), 12):
		printerr("  ", problems[i])
	_check(problems.is_empty(), "reaction pipeline matches the live pipeline (%d differences)" % problems.size())

## Captured-pawn placement must match the unchanged live policy (capture_return_gen/generate.sh).
func _capture_return_parity() -> void:
	var Placement = load("res://scripts/game/board3d/capture_return_placement.gd")
	var rows = JSON.parse_string(FileAccess.get_file_as_string("res://tests/fixtures/capture_return_golden.json"))
	_check(rows is Array and rows.size() == 707, "capture return golden fixture loads")
	if not rows is Array:
		return
	var problems: Array = []
	for row in rows:
		var s: Dictionary = Placement.sample(Vector2(row[0], row[1]), Vector2(row[2], row[3]), row[4], row[5])
		var tol := 0.0002 * maxf(1.0, float(row[4]))
		if s["phase"] != row[9] or absf(s["point"].x - row[6]) > tol * 4.0 or absf(s["point"].y - row[7]) > tol * 4.0 \
				or absf(s["scale"] - row[8]) > 0.0002:
			problems.append("%s -> got %s %.5f %s" % [row, s["point"], s["scale"], s["phase"]])
	for i in mini(problems.size(), 6):
		printerr("  ", problems[i])
	_check(problems.is_empty(), "capture return placement matches live (%d differences)" % problems.size())

## The board drives a captured pawn along the live route: still on its cell until contact,
## then shake, pop (scaled up), curved trip and settle in its yard slot, facing the viewer.
func _capture_return_on_board() -> void:
	var Board = load("res://scripts/game/board3d/classic_board_3d.gd")
	var Placement = load("res://scripts/game/board3d/capture_return_placement.gd")
	var Geo = load("res://scripts/game/board3d/board_geometry.gd")
	var seats: Array[int] = [0, 2]
	var game := LocalMatch.new(seats, "PASS_AND_PLAY")
	var board = Board.new()
	board.match_state = game
	var player: Dictionary = game.state["players"][1]
	player["tokens"][2] = 20
	board._sync_motion(1000)
	player["tokens"][2] = -1
	board._sync_motion(2000)
	var key := "%s:2" % player["playerId"]
	_check(board._motion.get(key, {}).get("kind") == "return", "capture starts a return motion")
	var shown: Dictionary = board.scene_state()["players"].filter(func(p): return p["playerId"] == player["playerId"])[0]
	var slot: int = 2 + int(shown["seat"])
	var from: Vector2 = Geo.token_center(player["color"], 2, 20, 1.0) + Geo.token_offset_fraction(slot, 20)
	var home: Vector2 = Geo.token_center(player["color"], 2, -1, 1.0)
	var ok := true
	for elapsed in [0, 120, 300, 650, 1000, 1250]:
		var pawn: Dictionary = board._pawn_states(board.scene_state(), 2000 + elapsed).filter(func(p): return p["key"] == key)[0]
		var want: Dictionary = Placement.sample(from, home, 1.0, elapsed / 1300.0)
		ok = ok and pawn["screen"].distance_to(want["point"]) < 0.0001 and absf(pawn["presentation_scale"] - want["scale"]) < 0.0001 \
			and pawn["frame"]["kind"] == "IDLE" and pawn["yaw"] == 0.0 and pawn["hop"] == 0.0
	_check(ok, "captured pawn follows the live capture-return route on the board")
	board.free()

## Road foot drop and radius must match the unchanged live LudoPawsPawnLayout (layout_gen/generate.sh).
func _road_presentation_parity() -> void:
	var Geo = load("res://scripts/game/board3d/board_geometry.gd")
	var rows = JSON.parse_string(FileAccess.get_file_as_string("res://tests/fixtures/layout_golden.json"))
	_check(rows is Array and rows.size() > 1000, "pawn layout golden fixture loads")
	if not rows is Array:
		return
	var problems: Array = []
	for row in rows:
		var center = Geo.token_center(row[0], 1, int(row[1]), 1.0, int(row[2]))
		var fit: Dictionary = Geo.road_presentation(Vector2(row[5], row[6]), 1.0, int(row[1]), int(row[3]), row[4], int(row[2]))
		if center == null or center.distance_to(Vector2(row[5], row[6])) > 0.0001 \
				or absf(fit["radius"] - row[7]) > 0.0001 or absf(fit["foot_drop"] - row[8]) > 0.0001:
			problems.append("%s -> centre %s radius %.5f drop %.5f" % [row, center, fit["radius"], fit["foot_drop"]])
	for i in mini(problems.size(), 6):
		printerr("  ", problems[i])
	_check(problems.is_empty(), "road presentation matches live (%d rows, %d differences)" % [rows.size(), problems.size()])

## Ports LudoPaws3DRenderCadencePolicyTest and LudoPaws3DDisplayPacerTest.
func _render_cadence() -> void:
	var C = load("res://scripts/game/board3d/render_cadence.gd")
	var Houses = load("res://scripts/game/board3d/center_houses.gd")
	var delay := func(scene: Dictionary, now: int) -> int:
		return C.frame_delay_ms(scene, now, Houses.PLAQUE_START_MS, Houses.ENTRY_DURATION_MS)
	_check(delay.call({}, 10000) == 33, "idle board uses the lower-cost cadence")
	var home := {"forward": {"to": 57, "started_ms": 1000, "duration_ms": 600}}
	_check(delay.call(home, 2999) == 16 and delay.call(home, 3000) == 33, "home move stays active through the celebration tail")
	var step := {"forward": {"to": 18, "started_ms": 2000, "duration_ms": 600}}
	_check(delay.call(step, 2599) == 16 and delay.call(step, 2600) == 16 and delay.call(step, 2849) == 16 \
		and delay.call(step, 2850) == 33, "ordinary move stays active through facing/spring recovery")
	var capture := {"captures": [{"started_ms": 5000, "duration_ms": 800}]}
	_check(delay.call(capture, 5799) == 16 and delay.call(capture, 5800) == 33, "capture return active only while visible")
	var reaction := {"reactions": [{"started_ms": 7000, "duration_ms": 680}]}
	_check(delay.call(reaction, 7679) == 16 and delay.call(reaction, 7680) == 33, "reaction active until its exact expiry")
	var entry := {"home_entries": {"a": {"started_ms": 100, "rank": 2}, "b": {"started_ms": 0, "rank": 1}}}
	_check(delay.call(entry, 100 + Houses.ENTRY_DURATION_MS - 1) == 16 and delay.call(entry, 100 + Houses.ENTRY_DURATION_MS) == 33,
		"home entry active for its duration; restored (unstarted) entries are idle")
	_check(C.capture_contact_delay_ms(600) == 510 and C.capture_contact_delay_ms(90) == 0 and C.capture_contact_delay_ms(20) == 0,
		"capture contact starts before the landing settle")
	var strides_ok := true
	for hz in [60, 120]:
		for target in [16, 33]:
			var pacer = C.Pacer.new()
			var refresh: int = 1000000000 / hz
			var selected: Array = []
			for i in hz * 2:
				if pacer.should_render(10000000000 + i * refresh, refresh, target):
					selected.append(i)
			var stride: int = hz / (60 if target == 16 else 30)
			strides_ok = strides_ok and selected.size() == (120 if target == 16 else 60)
			for k in range(1, selected.size()):
				strides_ok = strides_ok and selected[k] - selected[k - 1] == stride
	_check(strides_ok, "pacer uses whole refreshes at 60/120 Hz without double frames")
	var p = C.Pacer.new()
	var period := 16666666
	var start := 10000000000
	_check(p.should_render(start, period, 33) and p.should_render(start + period, period, 16) \
		and p.should_render(start + period * 7, period, 16) and not p.should_render(start + period * 7, period, 16) \
		and p.should_render(start + period * 8, period, 16), "pacer wakes on the next refresh and never replays missed frames")
	p.reset()
	_check(p.should_render(start + period * 8, period, 33), "pacer reset renders immediately")

## Every (from, to) token change animates exactly like the unchanged live policy (motion_gen/generate.sh).
func _motion_policy_parity() -> void:
	var M = load("res://scripts/game/board3d/pawn_motion_policy.gd")
	var rows = JSON.parse_string(FileAccess.get_file_as_string("res://tests/fixtures/motion_golden.json"))
	_check(rows is Array and rows.size() == 59 * 59, "pawn motion golden fixture loads")
	if not rows is Array:
		return
	var problems: Array = []
	for row in rows:
		var plan = M.transition(int(row[0]), int(row[1]))
		var kind: String = "NONE" if plan == null else ("FORWARD" if plan["kind"] == "forward" else "CAPTURE_RETURN")
		var steps: int = 0 if plan == null else int(plan["steps"])
		var path: Array = M.forward_path(int(row[0]), steps) if kind == "FORWARD" else []
		var want: Array = row[4].map(func(v): return int(v))
		if kind != row[2] or steps != int(row[3]) or path != want:
			problems.append("%s -> %s %d %s" % [row, kind, steps, path])
	for i in mini(problems.size(), 6):
		printerr("  ", problems[i])
	_check(problems.is_empty(), "pawn animation policy matches live for every token change (%d differences)" % problems.size())
	# A backwards non-capture change (e.g. a corrected restore) jumps without animating.
	var Board = load("res://scripts/game/board3d/classic_board_3d.gd")
	var seats: Array[int] = [0, 2]
	var game := LocalMatch.new(seats, "PASS_AND_PLAY")
	var board = Board.new()
	board.match_state = game
	var tokens: Array = game.state["players"][0]["tokens"]
	tokens[1] = 30
	tokens[2] = 48
	board._sync_motion(1000)
	tokens[1] = 20
	board._sync_motion(2000)
	var no_motion: bool = board._motion.is_empty()
	tokens[2] = 53
	board._sync_motion(4000)
	var m: Dictionary = board._motion.values()[0] if board._motion.size() == 1 else {}
	_check(no_motion and m.get("path") == [49, 50, 52, 53] and int(m.get("duration", 0)) == 4 * 280 + 90,
		"board animates only live transitions, walking the live visual cells")
	board.free()

## Every FX draw call matches the unchanged live LudoPawsFxPainter (fx_gen/generate.sh), with one
## painter reused in the harness order so carried-over stroke widths are checked too.
func _fx_painter_parity() -> void:
	var Painter = load("res://scripts/game/board3d/fx_painter.gd")
	var blocks = JSON.parse_string(FileAccess.get_file_as_string("res://tests/fixtures/fx_golden.json"))
	_check(blocks is Array and blocks.size() == 280, "FX golden fixture loads")
	if not blocks is Array:
		return
	var painter = Painter.new(2.0)
	var cell := 40.0
	var problems: Array = []
	for block in blocks:
		var parts: PackedStringArray = block["label"].split(" ")
		var p := float(parts[-1])
		painter.commands = []
		match parts[0]:
			"trail": painter.draw_paw_trail(Vector2(100, 220), Vector2(260, 180), Painter.rgb(229, 57, 53), cell, p, 0.55)
			"home-trail": painter.draw_paw_trail(Vector2(300, 300), Vector2(300, 260), Painter.rgb(30, 136, 229), cell, p, 1.0)
			"capture": painter.draw_capture_return(Vector2(420, 140), Vector2(70, 70), Painter.rgb(46, 160, 67), cell, p)
			"stars": painter.draw_home_stars(cell, p)
			_: painter.draw_reaction(Vector2(250, 330), parts[0], cell, p, int(parts[1]), int(parts[1]) == 32)
		var diff := _fx_diff(painter.commands, block["commands"])
		if not diff.is_empty():
			problems.append("%s: %s" % [block["label"], diff])
	for i in mini(problems.size(), 6):
		printerr("  ", problems[i])
	_check(problems.is_empty(), "FX painter matches live draw calls (%d differences)" % problems.size())

func _fx_diff(got: Array, want: Array) -> String:
	if got.size() != want.size():
		return "%d commands, want %d" % [got.size(), want.size()]
	for i in want.size():
		var g: Array = got[i]
		var w: Array = want[i]
		var flat_g: Array = []
		var flat_w: Array = []
		for v in g: flat_g.append_array(v if v is Array else [v])
		for v in w: flat_w.append_array(v if v is Array else [v])
		if flat_g.size() != flat_w.size():
			return "command %d: %s want %s" % [i, g, w]
		for k in flat_w.size():
			var a = flat_g[k]
			var b = flat_w[k]
			if b is float and (a is float or a is int):
				# The colour channel is an exact ARGB integer (JSON stores it as float).
				if absf(float(a) - b) > (0.0 if b > 1.0e6 else 0.002 * maxf(1.0, absf(b))):
					return "command %d value %d: %s want %s" % [i, k, g, w]
			elif a != b:
				return "command %d value %d: %s want %s" % [i, k, g, w]
	return ""

## LudoPawsGameFxOverlayView timing: trails run with the move, the capture ghost and move-made
## reaction FX wait for contact, Reduced Motion drops reaction FX but keeps trails.
func _fx_overlay_timeline() -> void:
	var Overlay = load("res://scripts/game/board3d/fx_overlay.gd")
	var fx = Overlay.new()
	var before := {"matchId": "m", "players": [
		{"playerId": "a", "color": "RED", "seat": 0, "tokens": [10, -1, -1, -1]},
		{"playerId": "b", "color": "YELLOW", "seat": 2, "tokens": [14, -1, -1, -1]}]}
	var after := before.duplicate(true)
	after["players"][0]["tokens"][0] = 14
	after["players"][1]["tokens"][0] = -1
	fx.bind(before, after, 1000)
	var contact := 4 * 280
	_check(fx.forward != null and fx.capture_contact_delay == contact and fx.transitions.size() == 1 \
		and fx.capture_transitions.size() == 1, "FX bind finds the move, the capture and the contact delay")
	var kinds := func(list: Array) -> Array:
		return list.map(func(c): return c[0])
	var at_start: Array = fx.commands(1000, 40.0, 0, false)
	_check(at_start.size() == 16 and kinds.call(at_start).count("oval") == 4, "paw trail draws at once; capture ghost waits for contact")
	var at_contact: Array = fx.commands(1000 + contact + 100, 40.0, 0, false)
	_check(at_contact.any(func(c): return c[0] == "line"), "capture ghost runs home after contact")
	fx.play([{"playerId": "a", "tokenIndex": 0, "animationCue": "CAPTURE"}], 1000)
	var burst := func(list: Array) -> int:
		return list.filter(func(c): return c[0] == "circle" and (int(c[5]) & 0xFFFFFF) == 0xFFEB3B).size()
	_check(burst.call(fx.commands(1000 + contact - 10, 40.0, 0, false)) == 0 \
		and burst.call(fx.commands(1000 + contact + 10, 40.0, 0, false)) == 14, "move-made reaction FX start when the pawn arrives")
	_check(burst.call(fx.commands(1000 + contact + 10, 40.0, 0, true)) == 0, "Reduced Motion drops reaction FX")
	var settled: Array = fx.commands(1000 + contact + 1300 + 2000, 40.0, 0, false)
	_check(settled.filter(func(c): return c[0] == "circle" and (int(c[5]) >> 24) == 220).size() == 1,
		"finished capture leaves the live destination ring until the next snapshot")
	fx.bind(after, after, 9000)
	_check(fx.commands(9000, 40.0, 0, false).is_empty(), "next snapshot clears transition FX")

## LudoPawsYardFootprintPolicy + speciesForSeat: prints for every pawn out of its yard, by seat species.
func _yard_paw_prints() -> void:
	var Prints = load("res://scripts/game/board3d/yard_paw_prints.gd")
	var players := [
		{"playerId": "a", "seat": 0, "color": "RED", "tokens": [-1, 5, 51, 57]},
		{"playerId": "b", "seat": 1, "color": "GREEN", "tokens": [-1, -1, -1, -1]},
		{"playerId": "c", "seat": 2, "color": "YELLOW", "tokens": [3, -1, -1, -1]},
		{"playerId": "d", "seat": 3, "color": "BLUE", "tokens": [-1, -1, 9, -1]},
		{"playerId": "e", "seat": 4, "color": "PURPLE", "tokens": [1, 2, 3, 4]}]
	var prints: Array = Prints.footprints(players, ["cock", "dog", "panda", "donkey"])
	var summary := prints.map(func(p): return "%s%d:%s" % [p["color"].substr(0, 1), p["token"], p["species"]])
	_check(summary == ["R1:DUCK", "R2:DUCK", "R3:DUCK", "Y0:DUCK", "B2:GOAT"],
		"yard paw prints: pawns out of the yard, live species mapping and colour fallback (%s)" % [summary])
	var ok := true
	for species in ["DOG", "GOAT", "DUCK", "CAT", "ELEPHANT", "HORSE", "GIRAFFE", "RABBIT"]:
		var art: Dictionary = Prints.artwork(species)
		ok = ok and not art["pads"].is_empty() and art["pads"].all(func(p): return p.size() >= 8)
	var tex: ImageTexture = Prints.grain_texture()
	_check(ok and tex.get_width() == 64, "every species has paw print artwork and the grain tile builds")

## Port of LudoPawsGameplayPacingPolicyTest plus interaction delays after a move.
func _gameplay_pacing() -> void:
	var P = load("res://scripts/game/gameplay_pacing.gd")
	_check(P.dice_result_hold_ms("NORMAL") == 700 and P.post_roll_auto_move_delay_ms("NORMAL") == 100, "normal dice hold and auto-move tail")
	_check(P.capture_return_duration_ms("NORMAL") == 1300, "normal capture return duration")
	_check(P.dice_result_hold_ms("FAST") < P.dice_result_hold_ms("NORMAL") and P.dice_result_hold_ms("NORMAL") < P.dice_result_hold_ms("SLOW")
		and P.capture_return_duration_ms("FAST") < P.capture_return_duration_ms("NORMAL")
		and P.capture_return_duration_ms("NORMAL") < P.capture_return_duration_ms("SLOW"), "speeds keep ordered pacing")
	_check(P.movement_audio_step_duration_ms(3 * 360 + 90, 3) == 360 and P.movement_audio_step_duration_ms(6 * 280 + 90, 6) == 280
		and P.movement_audio_step_duration_ms(6 * 210 + 90, 6) == 210, "one movement tick per visual step")
	_check(P.capture_contact_delay_ms("SLOW", 6) == 2160 and P.capture_contact_delay_ms("NORMAL", 6) == 1680
		and P.capture_contact_delay_ms("FAST", 6) == 1260, "capture contact on the final visible step")
	_check(P.post_move_breath_ms("NORMAL", false) == 450 and P.post_move_breath_ms("NORMAL", true) == 600, "extra roll gets longer breath")
	_check(P.remaining_presentation_delay_ms(1000, 1250) == 0 and P.remaining_presentation_delay_ms(1000, 750) == 250
		and P.remaining_presentation_delay_ms(1000, 0) == 1000 and P.remaining_presentation_delay_ms(1000, -50) == 1000, "remaining presentation delay")
	_check(P.next_speed("SLOW") == "NORMAL" and P.next_speed("FAST") == "SLOW" and P.speed_or_default("bogus") == "NORMAL", "game speed cycle")
	var before := {"matchId": "m", "status": "ACTIVE", "turnSeat": 0, "players": [
		{"playerId": "a", "tokens": [10, -1, -1, -1]}, {"playerId": "b", "tokens": [14, -1, -1, -1]}]}
	var capture := before.duplicate(true)
	capture["players"][0]["tokens"][0] = 14
	capture["players"][1]["tokens"][0] = -1
	var plain := before.duplicate(true)
	plain["players"][0]["tokens"][0] = 13
	plain["turnSeat"] = 1
	var home := {"matchId": "m", "status": "ACTIVE", "turnSeat": 0, "players": [{"playerId": "a", "tokens": [57, 57, 57, 55]}]}
	var done := {"matchId": "m", "status": "FINISHED", "turnSeat": 0, "players": [{"playerId": "a", "tokens": [57, 57, 57, 57]}]}
	_check(P.interaction_delay_after_move_ms(before, capture, "NORMAL") == 4 * 280 + 1300 + 600
		and P.interaction_delay_after_move_ms(before, plain, "NORMAL") == 3 * 280 + 450
		and P.interaction_delay_after_move_ms(home, done, "NORMAL") == 2 * 280 + 90 + 900
		and P.interaction_delay_after_move_ms(null, plain, "NORMAL") == 450, "interaction delay after a move (travel, capture, breath, home entry)")

## Local turn flow timing (OfflineGameActions / renderGame), driven by an explicit clock.
func _turn_flow() -> void:
	var Flow = load("res://scripts/game/local_turn_flow.gd")
	var seats: Array[int] = [0, 2]
	var game := LocalMatch.new(seats, "ROBOT", [{"playerType": "HUMAN"}, {"playerType": "BOT"}])
	var flow = Flow.new(game, "NORMAL", true, 1)
	var rolls := [3, 3, 4, 2, 5, 1]
	var roll_index := [0]
	flow.roll_source = func() -> int:
		roll_index[0] += 1
		return rolls[(roll_index[0] - 1) % rolls.size()]
	flow.render(0)
	_check(flow.can_roll(0) and flow.roll(0) and not flow.can_roll(1), "human roll starts and locks the dice")
	flow.tick(599)
	_check(game.roll == 0, "roll waits for the roll delay")
	flow.tick(600)
	# Opening bonus: the first roll counts as 6, four interchangeable yard paws -> forced single move.
	_check(game.roll == 6 and flow.blocked(600) and flow.blocked(1299) and not flow.can_move(1000), "result is held readable for 700 ms")
	flow.tick(1300)
	_check(flow._auto_move != null and int(flow._auto_move["due"]) == 1400, "forced single move scheduled after the hold")
	flow.tick(1400)
	_check(game.pawns[0][0] == 0 and flow.blocked(1401), "forced single move is made and input locks while it plays")
	var unlock: int = flow.blocked_until
	_check(unlock == 1400 + 280 + 600, "move lock = travel + extra-turn breath")
	flow.tick(unlock)
	_check(flow.can_roll(unlock), "extra turn: dice unlock after the move")
	flow.roll(unlock)
	flow.tick(unlock + 600)
	flow.tick(unlock + 1300)
	flow.tick(unlock + 1400)
	var t: int = flow.blocked_until
	flow.tick(t)
	_check(flow.computer_turn() and flow.cpu_roll_due == t + 720, "robot rolls after its think time")
	flow.tick(t + 720)
	_check(game.roll > 0 and flow.blocked(t + 721), "robot roll result is held too")
	var single = Flow.single_automatic_token_index([5, 5, -1, 20], 3, [0, 1, 3])
	_check(single == null and Flow.single_automatic_token_index([5, 5, -1, -1], 3, [1, 0]) == 0
		and Flow.single_automatic_token_index([5, -1, -1, -1], 2, [0]) == 0, "single automatic token: only one effective move")
	flow.free()

## Procedural fallback sounds equal the unchanged live synth sample for sample (audio_gen/generate.sh).
func _procedural_audio_parity() -> void:
	var Synth = load("res://scripts/game/audio/procedural_audio.gd")
	var rows = JSON.parse_string(FileAccess.get_file_as_string("res://tests/fixtures/audio_golden.json"))
	_check(rows is Array and rows.filter(func(r): return r[0] != "plan").size() == 3 * (18 + 20), "procedural audio golden loads")
	if not rows is Array:
		return
	var problems: Array = []
	for row in rows:
		if row[0] == "plan":
			continue
		var table: Dictionary = Synth.SFX if row[0] == "sfx" else Synth.VOCAL
		if not table.has(row[1]):
			problems.append("%s missing" % row[1])
			continue
		var pcm: PackedInt32Array = Synth.samples(table[row[1]], float(row[2]))
		var sum := 0
		var total := 0
		for v in pcm:
			sum += v
			total += absi(v)
		var picks: Array = []
		for k in 16:
			picks.append(pcm[(k * (pcm.size() - 1)) / 15])
		var want: Array = row[6].map(func(v): return int(v))
		# Allow +-1 LSB per sample from libm sin/cos differences.
		var close := picks.size() == want.size()
		for k in want.size():
			close = close and absi(picks[k] - want[k]) <= 1
		if pcm.size() != int(row[3]) or absi(sum - int(row[4])) > pcm.size() / 50 + 2 or absi(total - int(row[5])) > pcm.size() / 50 + 2 or not close:
			problems.append("%s %s %s: n=%d sum=%d abs=%d picks=%s want %s" % [row[0], row[1], row[2], pcm.size(), sum, total, picks, row])
	for i in mini(problems.size(), 4):
		printerr("  ", problems[i])
	_check(problems.is_empty(), "procedural audio matches the live synth (%d differences)" % problems.size())
	var stream: AudioStreamWAV = Synth.sfx_stream("MOVE_PAW")
	_check(stream.mix_rate == 24000 and stream.data.size() == 1800 * 2, "procedural stream is 24 kHz mono 16-bit")

## GameSoundFeedback resolution, movement sound policy and offline feedback (live semantics).
func _game_audio() -> void:
	var Audio = load("res://scripts/game/audio/game_audio.gd")
	var Moves = load("res://scripts/game/audio/movement_sound_policy.gd")
	var Feedback = load("res://scripts/game/audio/offline_feedback.gd")
	var LedgerScript = load("res://scripts/game/reactions/feedback_ledger.gd")
	var audio = Audio.new()
	var enabled := [true]
	audio.setting_source = func(key: String, fallback): return enabled[0] if key == "effects" else fallback
	audio.capture()
	audio.move_step("horse")
	audio.move_step("duck")
	audio.dice_settle(5)
	audio.jump()
	enabled[0] = false
	audio.six()
	var sources: Array = audio.log.map(func(e): return e[0])
	_check(sources == ["asset:audio/sfx/gameplay/capture/lp_sfx_capture_01.wav", "synth:MOVE_HOOF", "synth:MOVE_WEB",
		"asset:audio/sfx/gameplay/dice/settle/lp_sfx_dice_settle_01.wav", "asset:audio/sfx/gameplay/movement/jump/lp_sfx_jump_01.wav"],
		"sounds resolve authored takes, else the live procedural fallback; Effects off silences them (%s)" % [sources])
	_check(audio.log[0][1] == 0.62 and audio.log[0][2] == 10 and is_equal_approx(audio.log[2][1], 0.28), "live volumes and priorities")
	_check(Moves.cues_for_visual_step("RED", -1, 1) == ["YARD_EXIT"] and Moves.cues_for_visual_step("RED", 5, 3) == ["STEP", "SAFE_RELIEF"]
		and Moves.cues_for_visual_step("RED", 49, 2) == ["STEP", "HOME_LANE"] and Moves.cues_for_visual_step("RED", 49, 3) == ["STEP"] and Moves.cues_for_visual_step("RED", 55, 2) == ["STEP", "HOME"]
		and Moves.cues_for_visual_step("GREEN", 7, 1) == ["STEP", "SAFE_RELIEF"] and Moves.cues_for_visual_step("RED", 3, 0) == [],
		"movement sound cues per visual step (yard exit, safe, home lane, home)")
	var ledger = LedgerScript.new()
	var before := {"matchId": "m", "status": "ACTIVE", "turnSeat": 0, "randomEventIndex": 3, "history": [], "pendingRoll": null,
		"players": [{"playerId": "a", "seat": 0, "color": "RED", "tokens": [5, -1, -1, -1]}]}
	_check(Feedback.committed_action_key(before, "ROLL") == "ROLL:2" and Feedback.committed(null, ledger, null, before, "ROLL") == ""
		and Feedback.committed(null, ledger, null, before, "ROLL") == "" and ledger.retained_key_count() == 1,
		"committed action feedback is keyed and plays once")
	audio.free()

## Vocal plans equal the unchanged live LudoPawsReactionAudioProfile for every species x cue.
func _voice_plan_parity() -> void:
	var Profile = load("res://scripts/game/audio/reaction_audio_profile.gd")
	var rows = JSON.parse_string(FileAccess.get_file_as_string("res://tests/fixtures/audio_golden.json"))
	var plans: Array = rows.filter(func(r): return r[0] == "plan")
	_check(plans.size() > 900, "voice plan golden loads")
	var problems: Array = []
	for row in plans:
		var moment = null if row[3] == "null" else row[3]
		var reaction := {"voiceCue": row[2], "momentType": moment}
		var got = Profile.plan("dog", row[1], reaction)
		var want = row[4]
		if want == null or got == null:
			if (want == null) != (got == null):
				problems.append("%s: got %s" % [row, got])
			continue
		var play: Array = []
		var variant: Array = []
		for i in 20:
			play.append(Profile.should_play(got, "key-%s-%s-%d" % [row[1], row[2], i]))
		for i in 5:
			variant.append(Profile.deterministic_variant_index("key-%s-%d" % [row[1], i], 2))
		if got["fallback"] != want[0] or absf(got["volume"] - want[1]) > 1e-5 or absf(got["rate"] - want[2]) > 1e-5 \
				or got["probability"] != int(want[3]) or got["delay"] != int(want[4]) or got["names"] != want[5] \
				or play != want[6] or variant != want[7].map(func(v): return int(v)):
			problems.append("%s: got %s %s %s" % [row, got, play, variant])
	for i in mini(problems.size(), 4):
		printerr("  ", problems[i])
	_check(problems.is_empty(), "voice plans match live (%d rows, %d differences)" % [plans.size(), problems.size()])

## LudoPawsVoicePlayer: the capture pair calls after contact + plan delay; voices setting gates it.
func _voice_player() -> void:
	var Audio = load("res://scripts/game/audio/game_audio.gd")
	var audio = Audio.new()
	var voices := [true]
	audio.setting_source = func(key: String, fallback): return voices[0] if key == "animal_voices" else fallback
	var capture := [
		{"playerId": "a", "seat": 0, "voiceCue": "CAPTURE", "priority": 88, "momentType": "CAPTURE_MADE", "eventIndex": 4, "reactionKey": "k1"},
		{"playerId": "b", "seat": 1, "voiceCue": "CAPTURED", "priority": 86, "momentType": "TOKEN_CAPTURED", "eventIndex": 4, "reactionKey": "k2"},
		{"playerId": "c", "seat": 2, "voiceCue": "SAFE", "priority": 58, "momentType": "SAFE_REACHED", "eventIndex": 4, "reactionKey": "k3"}]
	audio.play_voices(capture, ["horse", "panda", "dog"], 1000, 5000)
	var dues: Array = audio._voice_queue.map(func(v): return v["due"])
	dues.sort()
	_check(dues == [6080, 6155], "capture pair calls at contact + 80 / + 155 ms; the SAFE call is not in the batch (%s)" % [dues])
	for v in audio._voice_queue:
		v["play"].call()
	_check(audio.log.size() == 2 and audio.log[0][0] == "voice:audio/sfx/voices/horse/lp_vocal_horse_capture_01.wav"
		and is_equal_approx(audio.log[0][1], 0.58) and audio.log[0][2] == 9, "the horse's authored capture call plays (%s)" % [audio.log])
	audio._voice_queue.clear()
	voices[0] = false
	audio.play_voices(capture, ["horse", "dog", "dog"], 0, 5000)
	_check(audio._voice_queue.is_empty(), "Animal voices off: no calls")
	audio.free()

## GameMusicController: theme at the bed volume, ducked under animal calls.
func _music() -> void:
	var Audio = load("res://scripts/game/audio/game_audio.gd")
	var audio = Audio.new()
	var now := Time.get_ticks_msec()
	_check(is_equal_approx(audio.music_volume(now), 0.16), "music bed volume")
	audio.duck_for_voice(650)
	_check(is_equal_approx(audio.music_volume(Time.get_ticks_msec()), 0.075) and is_equal_approx(audio.music_volume(now + 5000), 0.16),
		"music ducks under an animal call for 650 ms")
	_check(ResourceLoader.exists("res://assets/" + audio.MUSIC_PATH), "title theme is packaged")
	audio.free()

## LudoPawsResultPresentation title / subtitle / placements.
func _result_model() -> void:
	var R = load("res://scripts/game/result_presentation.gd")
	var state := {"status": "FINISHED", "matchMode": "ROBOT", "winnerPlayerId": "b", "finishOrderPlayerIds": ["b", "a"],
		"players": [{"playerId": "a", "displayName": "You"}, {"playerId": "b", "displayName": "Robot 1"}, {"playerId": "c", "displayName": "Robot 2"}]}
	var m: Dictionary = R.model(state, "COMPUTER", "a")
	_check(m["title"] == "2ND PLACE" and m["subtitle"] == "Great game!" and m["placements"].map(func(p): return p["label"] + " " + p["name"]) == ["1ST Robot 1", "2ND You", "3RD Robot 2"]
		and m["placements"][1]["you"], "computer result: rank title, subtitle, ordered placements (%s)" % [m])
	var pass_card: Dictionary = R.model(state, "PASS_AND_PLAY", "a")
	_check(pass_card["title"] == "MATCH COMPLETE" and not pass_card["placements"].any(func(p): return p["you"]), "pass & play result never highlights a viewer")
	state["players"][1]["forfeited"] = true
	state["players"][1]["forfeitReason"] = "TURN_TIMEOUT"
	_check(R.model(state, "COMPUTER", "a")["subtitle"] == "Match ended after a turn timeout.", "forfeit subtitle")

## Legal pulse timing, tap-hand cadence and the first-match coach policy.
func _legal_hints() -> void:
	var H = load("res://scripts/game/board3d/legal_hints.gd")
	var Page = load("res://scripts/game/local_match_page.gd")
	_check(is_zero_approx(H.pulse(1000, 1000)) and is_equal_approx(H.pulse(1360, 1000), 0.5) and is_equal_approx(H.pulse(1720, 1000), 1.0)
		and is_equal_approx(H.pulse(2080, 1000), 0.5), "legal pulse: 720 ms accelerate-decelerate, reversing")
	_check(H.hand_visible(3600) and H.hand_visible(4999) and not H.hand_visible(5000), "tap hand shows 1.4 s of every 3.6 s")
	_check(H.hand_outline().size() > 30, "tap hand outline builds")
	_check(Page.coach_hint(true, true, true, null) == "Tap your character's dice to begin."
		and Page.coach_hint(true, true, true, 2) == "Choose a glowing paw. Shield cells are safe."
		and Page.coach_hint(false, true, true, 2) == null and Page.coach_hint(true, false, true, null) == null
		and Page.coach_hint(true, true, false, null) == null, "first-match coach hint policy")

## Move-hint contrast on every ported board theme: the dark keyline must stand out from each
## board colour a hint can sit on, and the mint mark from the keyline (WCAG >= 3:1).
## Add each board's road/yard/surface colours here when it is ported (E).
func _legal_hint_contrast() -> void:
	var H = load("res://scripts/game/board3d/legal_hints.gd")
	var Style = load("res://scripts/ui/kit/meadow_style.gd")
	var P = load("res://scripts/game/board3d/board_palette.gd")
	var boards := {"board_classic": [P.STONE, P.STONE_LIGHT, P.STONE_SHADE, P.CLASSIC_NEUTRAL_CELL, P.CORAL, P.JADE, P.HONEY, P.SKY, P.CLASSIC_SURFACE, 0x698349, 0x9AA66E]}
	# Every recorded live board: its surface and neutral road colours (team colours are shared).
	var Art = load("res://scripts/game/board3d/board_art_player.gd")
	for id in Art.art():
		var d: Dictionary = Art.art()[id]
		boards[id] = [int(d["surface"]) & 0xFFFFFF, int(d["neutral"]) & 0xFFFFFF, P.CORAL, P.JADE, P.HONEY, P.SKY]
	_check(boards.size() == 8, "contrast check covers all 8 live boards")
	var weak: Array = []
	for board in boards:
		for hex in boards[board]:
			var bg: Color = P.color(hex)
			# Either the keyline or the mark must separate from the tile (the keyline wraps the mark).
			var best := maxf(H.contrast(H.KEYLINE, bg), H.contrast(Style.MINT, bg))
			if best < 3.0:
				weak.append("%s #%06x %.2f" % [board, hex, best])
	_check(weak.is_empty() and H.contrast(Style.MINT, H.KEYLINE) >= 3.0, "move hints stay readable on every ported board (%s)" % [weak])

## Ports of StarterPawsAssignmentPolicyTest (live) on the V2 policy.
func _starter_assignment() -> void:
	var S = load("res://scripts/game/characters/starter_assignment.gd")
	var all: Array = S.starter_ids()
	var free := ["dog", "goat", "duck", "cat"]
	var ok := true
	ok = ok and S.normalize(4, [], "cat", true, all) == ["cat", "dog", "goat", "duck"]
	ok = ok and S.normalize(4, [], "dog", false, all) == ["dog", "goat", "duck", "cat"]
	ok = ok and S.select(4, ["dog", "goat", "duck", "cat"], 2, "dog", false, all) == ["duck", "goat", "dog", "cat"]
	ok = ok and S.normalize(4, ["squirrel", "hedgehog", "duck", "sheep"], "squirrel", false, all) == ["dog", "goat", "duck", "cat"]
	ok = ok and S.select(3, ["dog", "goat", "duck"], 1, "not_a_character", false, all) == ["dog", "goat", "duck"]
	var two: Array = S.normalize(2, ["cat", "dog", "goat", "duck"], "cat", false, all)
	var four: Array = S.normalize(4, two, "cat", false, all)
	ok = ok and two == ["cat", "dog"] and four[0] == "cat" and _unique(four)
	var swapped: Array = S.select(4, ["dog", "goat", "duck", "cat"], 2, "cat", true, free)
	ok = ok and swapped == ["dog", "goat", "cat", "duck"] and S.select(4, swapped, 2, "horse", true, free) == swapped
	ok = ok and S.select(4, ["dog", "goat", "duck", "cat"], 2, "goat", true, free) == ["dog", "duck", "goat", "cat"]
	ok = ok and S.select(4, ["dog", "goat", "duck", "cat"], 1, "dog", true, free) == ["dog", "goat", "duck", "cat"]
	ok = ok and S.select(4, ["dog", "goat", "duck", "cat"], 0, "horse", true, free) == ["dog", "goat", "duck", "cat"]
	ok = ok and S.normalize(4, ["horse", "dog", "goat", "cat"], "horse", true, free)[0] == "dog"
	ok = ok and S.select(4, ["dog", "goat", "duck", "cat"], 0, "cat", true, free) == ["cat", "goat", "duck", "dog"]
	var mixed: Array = S.normalize(4, ["dog", "horse", "rabbit", "cat"], "dog", true, free, 2)
	ok = ok and mixed == ["dog", "goat", "duck", "cat"] and S.select(4, mixed, 1, "horse", true, free, 2) == mixed \
		and S.select(4, mixed, 2, "goat", true, free, 2) == mixed
	var three: Array = S.normalize(4, ["dog", "goat", "horse", "rabbit"], "dog", true, free, 3)
	ok = ok and three[2] == "duck" and three[3] == "cat"
	for humans in [1, 2, 3]:
		ok = ok and S.normalize(4, ["horse", "panda", "rabbit", "monkey"], "horse", true, free, humans) == ["dog", "goat", "duck", "cat"]
	var local: Array = S.normalize(4, ["goat_lumi", "donkey", "dog", "cat"], "goat_lumi", false, free)
	ok = ok and _same_set(local, free) and S.normalize(4, ["goat_lumi"], "goat_lumi", true, free)[0] == "dog" \
		and S.select(4, local, 0, "donkey", false, free) == local
	var lumi: Array = S.select(4, ["dog", "goat", "duck", "cat"], 0, "goat_lumi", false, free + ["goat_lumi"])
	ok = ok and lumi[0] == "goat_lumi" and _unique(lumi) and not lumi.has("donkey")
	_check(ok, "starter Paws assignment matches the live policy tests")

func _unique(list: Array) -> bool:
	var seen := {}
	for v in list:
		seen[v] = true
	return seen.size() == list.size()

func _same_set(a: Array, b: Array) -> bool:
	return a.size() == b.size() and a.all(func(v): return b.has(v))

## Ports of StoreCosmeticCatalogTest + server prices: only live items, live prices/channels.
func _store_catalog() -> void:
	var C = load("res://scripts/game/store/store_catalog.gd")
	var cat = C.new()
	var boards: Array = cat.for_category("BOARD")
	var dice: Array = cat.for_category("DICE")
	var paws: Array = cat.for_category("CHARACTER_PACK")
	_check(boards.size() == 8 and dice.size() == 8 and cat.for_category("AVATAR").is_empty()
		and C.built_in("board_classic")["title"] == "CLASSIC", "the shop lists exactly the 8 live boards and 8 live dice, no avatars")
	_check(C.built_in("board_dawn_meadow") == null and C.built_in("dice_pumpkin") == null, "draft / retired items do not exist in V2")
	var prices := {}
	for o in boards + dice + paws:
		prices[o["id"]] = [o["gemPrice"], o["requiredAdViews"], o["channels"]]
	_check(prices["board_astral"] == [300, 15, ["GEMS", "PLAY_PRODUCT", "REWARDED_ADS"]] and prices["board_winter_haven"] == [300, 0, ["GEMS", "PLAY_PRODUCT"]]
		and prices["dice_pearl_paw"] == [100, 20, ["GEMS", "REWARDED_ADS"]] and prices["dice_ember_forge"] == [150, 0, ["GEMS", "PLAY_PRODUCT"]]
		and prices["dice_starlight"] == [100, 0, ["GEMS", "PLAY_PRODUCT"]] and prices["character_goat_lumi"] == [50, 10, ["GEMS", "REWARDED_ADS"]]
		and prices["character_donkey"] == [75, 15, ["GEMS", "REWARDED_ADS"]] and prices["character_panda"] == [150, 0, ["GEMS", "PLAY_PRODUCT"]]
		and prices["character_giraffe"] == [300, 0, ["GEMS", "PLAY_PRODUCT"]] and prices["character_rabbit"] == [300, 0, ["GEMS", "PLAY_PRODUCT"]],
		"live prices, ad counts and unlock channels")
	var defaults: Array = C.default_owned_ids()
	_check(defaults.has("board_classic") and defaults.has("dice_classic") and defaults.has("avatar_4") and not defaults.has("avatar_2")
		and defaults.filter(func(id): return str(id).begins_with("dice_")) == ["dice_classic"] and not defaults.has("character_goat_lumi"),
		"only free built-ins are owned by default")
	_check(C.all_play_product_ids().size() == 7 + 21 and not C.play_product_ids().has("dice_pearl_paw")
		and C.PLAY_GEM_AMOUNTS["gems_20000"] == 7500 and C.PLAY_GEM_AMOUNTS["remove_ads"] == 5, "Play product ids and Gem amounts")
	cat.replace_remote([{"id": "board_astral", "category": "BOARD", "remoteManaged": true, "gemPrice": 250, "contentAvailable": true},
		{"id": "board_unknown_new", "category": "BOARD", "remoteManaged": true}], ["board_winter_haven"])
	var merged: Array = cat.for_category("BOARD").map(func(o): return o["id"])
	_check(cat.find_shop_item("board_astral")["gemPrice"] == 250 and not merged.has("board_winter_haven") and not merged.has("board_unknown_new"),
		"remote catalog overrides price, suppresses items and never shows unknown content")
	var offer: Dictionary = C.built_in("board_astral")
	_check(C.smart_unlock(offer, true, 0, 0, true, true)["primary"] == "SELECT"
		and C.smart_unlock(offer, false, 400, 0, true, true) == {"primary": "GEMS", "alternative": "PLAY_PRODUCT"}
		and C.smart_unlock(offer, false, 10, 15, true, true) == {"primary": "CLAIM_ADS", "alternative": "PLAY_PRODUCT"}
		and C.smart_unlock(offer, false, 10, 3, true, true) == {"primary": "PLAY_PRODUCT", "alternative": "WATCH_AD"}
		and C.smart_unlock(C.built_in("dice_pearl_paw"), false, 10, 3, false, true) == {"primary": "WATCH_AD", "alternative": "GET_GEMS"}
		and C.smart_unlock(C.built_in("avatar_2"), false, 10, 0, false, false) == {"primary": "GET_GEMS", "alternative": null},
		"SmartUnlockPolicy decisions")
	var Inv = load("res://scripts/game/store/cosmetic_inventory.gd")
	_test_prefs()
	var inv = Inv.new(func() -> String: return ACC_A)
	inv.apply_server_snapshot(_wallet(ACC_A, 1, 40, ["character_donkey", "board_astral"], {"BOARD": "board_astral", "DICE": "dice_starlight"}))
	_check(inv.unlocked_character_ids() == ["dog", "goat", "duck", "cat", "donkey"] and inv.selected_id("BOARD") == "board_astral"
		and inv.selected_id("DICE") == "dice_classic" and not inv.select_character("horse"), "ownership cache: paid Paws need an entitlement, unowned selections repair")
	inv.apply_server_snapshot(_wallet(ACC_A, 2, 40, [], {"BOARD": "board_astral"}))
	_check(inv.selected_id("BOARD") == "board_classic", "a refunded item returns its category to the default")

## Board art replay builds for every recorded live board; changing board never changes the
## route, pawn positions or hit-testing (E16).
func _boards() -> void:
	var Art = load("res://scripts/game/board3d/board_art_player.gd")
	var Board = load("res://scripts/game/board3d/classic_board_3d.gd")
	var ok: bool = Art.art().size() == 7
	for id in Art.art():
		var host := Control.new()
		Art.build(host, Art.art()[id]["commands"], 4.0)
		ok = ok and host.get_child_count() > 0
		host.free()
	_check(ok, "the 7 recorded live boards replay")
	var seats: Array[int] = [0, 2]
	var game := LocalMatch.new(seats, "PASS_AND_PLAY")
	game.state["players"][0]["tokens"] = [5, 5, -1, 54]
	game._sync()
	var points: Array = []
	for id in ["board_classic", "board_astral", "board_monsoon_mire", "board_not_live"]:
		var board = Board.new()
		board.board_id = id
		board.match_state = game
		points.append(board._pawn_states(board.scene_state(), 1000).map(func(p): return p["screen"]))
		if id == "board_not_live":
			ok = ok and board.board_id == "board_classic"
		board.free()
	_check(points[0] == points[1] and points[1] == points[2] and ok, "board themes never move pawns; unknown boards fall back to classic")

## Every live dice skin draws exactly what the unchanged live renderer draws (dice_golden.json).
func _dice_parity() -> void:
	var R = load("res://scripts/game/dice/dice_renderers.gd")
	var CE = load("res://scripts/game/render/canvas_emitter.gd")
	var data = JSON.parse_string(FileAccess.get_file_as_string("res://tests/fixtures/dice_golden.json"))
	_check(data is Dictionary and data.size() == 9, "dice golden loads (8 dice + surfaces)")
	if not data is Dictionary:
		return
	for id in data:
		if id == "surfaces":
			continue
		var renderer = R.create(id)
		var palette: Array = R.PALETTES.get(id, [[0xFF000000], 0xFF000000, 0xFF000000]).duplicate(true)
		palette[1] = 0xFFE85468 # team border used by the recorder
		var problems: Array = []
		_check(renderer != null and renderer.uses_perimeter_countdown == data[id]["usesPerimeterCountdown"]
			and renderer.uses_spatial_roll == data[id]["usesSpatialRoll"], "%s renderer flags match live" % id)
		for fr in data[id]["frames"]:
			var parts: PackedStringArray = fr["label"].split(" ")
			var face := int(parts[1])
			var f: Dictionary
			match parts[0]:
				"rest": f = R.frame(face)
				"attention": f = R.frame(face, false, false, true, 0.5)
				"attention-low": f = R.frame(face, false, false, true, 0.0)
				"attention-high": f = R.frame(face, false, false, true, 1.0)
				"deadline": f = R.frame(face, false, false, true, 0.7, true)
				"rolling": f = R.frame(face, true, false, false, 0.4, false, 5, int(parts[2]))
				"settling": f = R.frame(face, false, true, false, 0.6, false, 1, 520, float(parts[2]))
			var c = CE.new()
			renderer.draw(c, 100.0, palette, f)
			var diff := _cmd_diff(c.commands, fr["commands"])
			if not diff.is_empty():
				problems.append("%s: %s" % [fr["label"], diff])
		for i in mini(problems.size(), 3):
			printerr("  ", id, " ", problems[i])
		_check(problems.is_empty(), "%s draws exactly like live (%d frames, %d differences)" % [id, data[id]["frames"].size(), problems.size()])

func _cmd_diff(got: Array, want: Array) -> String:
	if got.size() != want.size():
		return "%d commands, want %d" % [got.size(), want.size()]
	for i in want.size():
		if not _same_value(got[i], want[i]):
			return "command %d: %s want %s" % [i, str(got[i]).left(300), str(want[i]).left(300)]
	return ""

func _same_value(a, b) -> bool:
	if b is Array:
		if not (a is Array or a is PackedFloat64Array) or a.size() != b.size():
			return false
		for k in b.size():
			if not _same_value(a[k], b[k]):
				return false
		return true
	if (b is float or b is int) and (a is float or a is int):
		var bf := float(b)
		# ARGB colours must match exactly; geometry within float32 rounding.
		if absf(bf) > 1.0e6:
			return absi(int(a) - int(bf)) <= 0
		return absf(float(a) - bf) <= 0.002 * maxf(1.0, absf(bf))
	return a == b

const ACC_A := "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
const ACC_B := "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"

func _test_prefs() -> void:
	var NativePrefs = load("res://scripts/game/platform/native_prefs.gd")
	NativePrefs.desktop_dir = "user://test_native_prefs"
	NativePrefs.reset_cache()
	for f in DirAccess.get_files_at("user://test_native_prefs"):
		DirAccess.remove_absolute(ProjectSettings.globalize_path("user://test_native_prefs/" + f))

static func _wallet(account: String, revision: int, balance: int, owned: Array, selected := {}, progress := {}) -> Dictionary:
	return {"accountId": account, "revision": revision, "balance": balance, "adsRemoved": false, "ownedCosmetics": owned,
		"selectedCosmetics": selected, "rewardedAdProgress": progress}

## Store flows through the service (desktop backend applying the live server rules).
func _store_flows() -> void:
	_test_prefs()
	var Inv = load("res://scripts/game/store/cosmetic_inventory.gd")
	var Service = load("res://scripts/game/store/store_service.gd")
	var Billing = load("res://scripts/game/store/play_billing.gd")
	var Ads = load("res://scripts/game/ads/ads_service.gd")
	var inv = Inv.new()
	var store = Service.new(inv)
	store.desktop.balance = 120
	await store.refresh_wallet()
	var astral: Dictionary = store.catalog.find_shop_item("board_astral")
	var forest: Dictionary = store.catalog.find_shop_item("dice_forest_dew")
	_check((await store.buy_with_gems(astral))["error"] == "INSUFFICIENT_GEMS" and inv.gems == 120, "Gem purchase needs enough Gems")
	var stale := forest.duplicate()
	stale["gemPrice"] = 90
	_check((await store.buy_with_gems(stale))["error"] == "COSMETIC_PRICE_CHANGED", "a changed price is rejected (expectedGemPrice)")
	_check((await store.buy_with_gems(forest))["ok"] and inv.gems == 20 and inv.is_owned("dice_forest_dew"), "Gem purchase debits and grants")
	_check((await store.select("dice_forest_dew"))["ok"] and inv.selected_id("DICE") == "dice_forest_dew" and inv.pending_equipment().is_empty()
		and store.desktop.selected.get("DICE") == "dice_forest_dew" and not (await store.select("dice_starlight"))["ok"],
		"only owned items can be equipped; the server confirms the local choice")
	var ads = Ads.new(store)
	var lumi: Dictionary = store.catalog.find_shop_item("character_goat_lumi")
	_check((await store.claim_with_ads(lumi))["error"] == "AD_PROGRESS_INCOMPLETE", "ad claims need complete verified progress")
	for i in 10:
		await ads.show_rewarded("character_goat_lumi")
	_check((await store.claim_with_ads(lumi))["ok"] and inv.is_character_unlocked("goat_lumi"), "10 verified ads unlock Lumi")
	await ads.show_rewarded()
	_check(inv.gems == 22, "a verified rewarded ad credits 2 Gems")
	var billing = Billing.new()
	billing.test_products = true
	var sheet: Dictionary = await billing.purchase("gems_1500")
	var first: Dictionary = await store.verify_play_purchase("gems_1500", sheet["token"])
	var again: Dictionary = await store.verify_play_purchase("gems_1500", sheet["token"])
	_check(first["ok"] and again.get("replay", false) and inv.gems == 22 + 1200, "Play Gem packs grant their server amount once per token")
	var ads_off: Dictionary = await billing.purchase("remove_ads")
	await store.verify_play_purchase("remove_ads", ads_off["token"])
	_check(inv.ads_removed and inv.gems == 22 + 1200 + 5, "remove_ads turns ads off and adds 5 Gems")
	var giraffe: Dictionary = await billing.purchase("character_giraffe")
	_check((await store.verify_play_purchase("character_giraffe", giraffe["token"]))["ok"] and inv.is_character_unlocked("giraffe"),
		"a Play cosmetic product grants its entitlement")
	# Live GooglePlayBillingGateway.processPurchases rules + PendingStorePurchaseStore.
	var me := "a".repeat(64)
	var other := "b".repeat(64)
	_check(Billing.verification_block({"products": ["gems_300"], "accountId": me}, me) == ""
		and Billing.verification_block({"products": ["gems_300"], "accountId": other}, me) == "OTHER_ACCOUNT"
		and Billing.verification_block({"products": ["remove_ads"], "accountId": null}, me).begins_with("This purchase has no recoverable")
		and Billing.verification_block({"products": ["remove_ads"], "accountId": other}, me) == ""
		and Billing.verification_block({"products": ["gems_300", "gems_1500"], "accountId": me}, me).begins_with("A Google Play purchase has an unsupported")
		and Billing.verification_block({"products": ["gems_300"], "accountId": "bad"}, "bad").begins_with("This Play purchase has an invalid"),
		"Play purchases: one known product, account binding, other account's Gems stay with Play")
	_check(billing.pending_add("gems_300", "token-0001", me) and billing.pending_add("gems_300", "token-0001", me)
		and not billing.pending_add("gems_1500", "token-0001", me) and not billing.pending_add("gems_300", "short", me)
		and billing.pending_all().size() == 1, "pending tokens: saved once, conflicting or invalid entries refused")
	billing.pending_add("gems_300", "token-0002", other)
	billing.pending_remove_account(me)
	_check(billing.pending_all().map(func(x): return x["purchaseToken"]) == ["token-0002"], "account deletion removes only that account's pending tokens")
	# Offline equip: owned Boards / Dice stay pending until the server confirms them.
	var offline = Service.new(inv, func(_m, _p, _b): return {"ok": false, "error": "NETWORK_ERROR", "status": 0})
	_check((await offline.select("dice_classic"))["ok"] and inv.selected_id("DICE") == "dice_classic", "free items are selectable offline")
	_check((await offline.select("dice_forest_dew"))["ok"] and inv.pending_equipment() == {"DICE": "dice_forest_dew"}
		and inv.selected_id("DICE") == "dice_forest_dew", "an owned Dice equipped offline stays pending (LocalEquipmentSync)")
	await store.sync_equipment()
	_check(inv.pending_equipment().is_empty() and inv.selected_id("DICE") == "dice_forest_dew", "pending equipment is confirmed when the server is reachable")

## Engagement rewards (engagement-rewards.js rules on the desktop server stub).
func _rewards_flow() -> void:
	_test_prefs()
	var Inv = load("res://scripts/game/store/cosmetic_inventory.gd")
	var Service = load("res://scripts/game/store/store_service.gd")
	var inv = Inv.new()
	var store = Service.new(inv)
	var now := [86400000 * 100 + 5000]
	store.desktop.clock = func() -> int: return now[0]
	store.desktop.spin_roll = func() -> int: return 96
	var status: Dictionary = await store.rewards_status()
	var day: int = status["day"]
	var gift: Dictionary = await store.claim_reward("daily", day)
	var again: Dictionary = await store.claim_reward("daily", day)
	var spin: Dictionary = await store.claim_reward("spin", day)
	_check(gift["amount"] == 3 and again.get("replay", false) and spin["amount"] == 5 and inv.gems == 8, "daily gift 3 once a day; spin roll 95+ gives 5")
	_check((await store.claim_reward("daily", day - 1))["error"] == "REWARD_DAY_CHANGED", "stale reward day is rejected")
	_check(store.desktop.credit_daily_bonus() and not store.desktop.credit_daily_bonus(), "daily ad bonus 2 once a day")
	await store.refresh_wallet()
	_check(inv.gems == 10 and store.cached_rewards()["dailyClaimed"], "the bonus reaches the wallet; the last status is cached for the page")
	now[0] += 86400000
	var next: Dictionary = await store.rewards_status()
	_check(not next["dailyClaimed"] and next["dailyClaims"] == 1, "rewards reset on the next day")

class FakeController:
	extends Node
	signal state_changed(state: Dictionary, player_id: String)
	var state := {}
	var player_id := "p0"
	var busy := false
	var rolls := 0
	var moves: Array = []
	func is_my_turn() -> bool:
		var seat := int(state.get("turnSeat", -1))
		return state.get("status") == "ACTIVE" and seat >= 0 and state["players"][seat]["playerId"] == player_id
	func roll():
		rolls += 1
		return {"ok": true}
	func move(i: int):
		moves.append(i)
		return {"ok": true}

static func _online_room(turn: int, pending = null, tokens0 := [-1, -1, -1, -1], index := 0) -> Dictionary:
	return {"schemaVersion": 1, "matchId": "LPABCDEFGH", "status": "ACTIVE", "hostPlayerId": "p0", "matchMode": "ONLINE", "targetPlayerCount": 2,
		"rulesetId": "ludoproof-standard-v8", "rulesetKey": "CLASSIC_V8", "revision": index, "randomEventIndex": index, "turnSeat": turn, "comebackWaits": [-1, -1],
		"winnerPlayerId": null, "players": [{"playerId": "p0", "seat": 0, "color": "RED", "tokens": tokens0},
		{"playerId": "p1", "seat": 1, "color": "YELLOW", "tokens": [-1, -1, -1, -1]}], "pendingRoll": pending, "history": []}

## Online flow: the server is the authority; only my turn is actionable; only my single legal
## move is made automatically; the board changes only from server snapshots.
func _online_flow() -> void:
	var Flow = load("res://scripts/game/online/online_turn_flow.gd")
	var c := FakeController.new()
	c.state = _online_room(1)
	var flow = Flow.new(c)
	root.add_child(flow)
	flow.set_process(false)
	_check(not flow.can_roll(0) and flow.my_seat() == 0 and flow.computer_turn() == false, "the opponent's turn is not actionable")
	c.state_changed.emit(_online_room(1, {"status": "RESOLVED", "seat": 1, "eventIndex": 0, "eventId": "roll:0", "legalTokenIndexes": [0], "outcome": 6}, [-1, -1, -1, -1], 1), "p0")
	flow.tick(100000)
	_check(c.moves.is_empty() and not flow.can_move(100000), "no automatic move for another player's roll")
	c.state = _online_room(0, null, [-1, -1, -1, -1], 2)
	c.state_changed.emit(c.state, "p0")
	_check(flow.can_roll(100000) and flow.roll(100000) and c.rolls == 1, "my turn: the roll is a server request")
	await process_frame
	c.state = _online_room(0, {"status": "RESOLVED", "seat": 0, "eventIndex": 2, "eventId": "roll:2", "legalTokenIndexes": [0], "outcome": 6}, [-1, -1, -1, -1], 3)
	c.state_changed.emit(c.state, "p0")
	_check(flow.game.roll == 6 and flow.game.legal == [0], "the server's roll result is shown")
	for t in [101000, 103000, 106000, 110000]:
		flow.tick(t)
		await process_frame
	_check(c.moves == [0], "my single legal move is made automatically through the server (%s)" % [c.moves])
	_check(flow.game.pawns[0][0] == -1, "the pawn moves only when the server says so")
	flow.queue_free()
	c.free()

## Live AdBreakPolicy + PendingAdRewardStore rules.
func _ads_rules() -> void:
	_test_prefs()
	var Ads = load("res://scripts/game/ads/ads_service.gd")
	var ads = Ads.new()
	var now := 10_000_000_000
	_check(not ads.can_show_break(now), "no ad break before a completed match")
	ads.completed("m1")
	_check(ads.can_show_break(now), "one completed match allows the first break")
	load("res://scripts/game/platform/native_prefs.gd").open("ludopaws_ad_breaks").edit().put_long("last_shown_at", now - 299000).commit()
	_check(not ads.can_show_break(now) and ads.can_show_break(now + 2000), "5 minutes between ad breaks")
	ads._quiet_until = Time.get_ticks_msec() + 60000
	_check(not ads.can_show_break(now + 2000), "no ad break right after a rewarded ad")
	var owner := ACC_A
	var id1 := "11111111-1111-4111-8111-111111111111"
	var id2 := "22222222-2222-4222-8222-222222222222"
	var t := int(Time.get_unix_time_from_system() * 1000.0)
	Ads._put_record({"accountId": owner, "requestId": id1, "cosmeticId": "", "createdAt": t, "earned": true, "abandoned": false})
	Ads._put_record({"accountId": owner, "requestId": id2, "cosmeticId": "", "createdAt": t - 400000, "earned": false, "abandoned": true})
	_check(Ads.records(owner).map(func(r): return r["requestId"]) == [id1], "reward hints: abandoned hints expire after 5 minutes")
	Ads.confirm(owner, [id1])
	_check(Ads.records(owner).is_empty(), "a server-credited receipt removes its hint")
	_test_prefs()
	load("res://scripts/game/platform/native_prefs.gd").desktop_dir = ""
	load("res://scripts/game/platform/native_prefs.gd").reset_cache()

## Live SecureSessionStore (online seat) + pending forfeits; Keystore stand-in with AAD.
func _player_session_store() -> void:
	_test_prefs()
	var Store = load("res://scripts/game/online/player_session_store.gd")
	var Keystore = load("res://scripts/game/platform/keystore.gd")
	var token := "lp_" + "a".repeat(40)
	_check(Store.save({"matchId": "LPABCDEFGH", "playerId": "p1", "playerToken": token, "mode": "FRIENDS"}), "online seat saved")
	_check(Store.load_session() == {"matchId": "LPABCDEFGH", "playerId": "p1", "playerToken": token, "mode": "FRIENDS"}, "online seat loads with its token")
	_check(not Store.save({"matchId": "bad", "playerId": "p1", "playerToken": token}) and not Store.save({"matchId": "LPABCDEFGH", "playerId": "p1", "playerToken": "x"}),
		"invalid match id / token are refused")
	var sealed: Dictionary = Keystore.encrypt("ludoproof_player_session_v1", "aad-1", "secret")
	_check(Keystore.decrypt("ludoproof_player_session_v1", "aad-1", sealed["iv"], sealed["ct"]) == "secret"
		and Keystore.decrypt("ludoproof_player_session_v1", "aad-2", sealed["iv"], sealed["ct"]) == null, "a ciphertext only opens with its AAD")
	var p = load("res://scripts/game/platform/native_prefs.gd").open("ludoproof_secure_session")
	p.edit().put_string("playerId", "p2").commit()
	_check(Store.load_session().is_empty() and not p.contains("matchId"), "a record bound to another seat is cleared (AAD mismatch)")
	_check(Store.save_pending_forfeit({"matchId": "LPABCDEFGJ", "playerId": "p9", "playerToken": token, "ranked": true, "ownerAccountId": ACC_A})
		and Store.pending_forfeits() == [{"matchId": "LPABCDEFGJ", "playerId": "p9", "playerToken": token, "ranked": true, "ownerAccountId": ACC_A}],
		"pending forfeits keep token and owner")
	Store.clear_pending_forfeit("LPABCDEFGJ")
	_check(Store.pending_forfeits().is_empty(), "pending forfeit cleared")
	_test_prefs()
	load("res://scripts/game/platform/native_prefs.gd").desktop_dir = ""
	load("res://scripts/game/platform/native_prefs.gd").reset_cache()

## Live OfflineGameEngine checkpoints (ludoproof_computer_game / ludoproof_offline_game) and
## OfflineResultRecovery.
func _saved_match() -> void:
	_test_prefs()
	var Saved = load("res://scripts/game/match/saved_match_store.gd")
	var Recovery = load("res://scripts/game/match/result_recovery.gd")
	var Profile = load("res://scripts/game/profile/profile_store.gd")
	var NativePrefs = load("res://scripts/game/platform/native_prefs.gd")
	var seats: Array[int] = [0, 2]
	var game := LocalMatch.new(seats, "ROBOT", [{"displayName": "Asha", "characterId": "cat"}, {"displayName": "Robot 1", "characterId": "goat", "playerType": "BOT"}])
	game.state["players"][0]["tokens"] = [5, -1, -1, 57]
	game._sync()
	_check(Saved.save("COMPUTER", game.state), "a V2 match saves")
	var back: Dictionary = Saved.load_state("COMPUTER")
	_check(back["players"][0]["tokens"] == [5, -1, -1, 57] and back["players"][1]["playerType"] == "BOT" and back["players"][0]["characterId"] == "cat"
		and back["rulesetKey"] == game.state["rulesetKey"] and back["turnRotation"] == "PERIMETER", "saved match round-trips (tokens, robots, Paws, ruleset)")
	var raw = JSON.parse_string(str(NativePrefs.open("ludoproof_computer_game").get_string("state", "")))
	_check(raw["rulesetId"] == "ludoproof-standard-v8" and raw["players"][0].has("tokens") and raw.has("consecutiveSixes") and raw["history"] == [],
		"stored in the live checkpoint shape (no fairness history from V2)")
	# A checkpoint written by the live Kotlin app (with EntroNex history) resumes in V2.
	var live := {"profileOwnerKnown": true, "profileOwnerAccountId": ACC_A, "rulesetId": "ludoproof-standard-v8", "matchId": "offline-123",
		"status": "ACTIVE", "turnSeat": 1, "eventIndex": 14, "revision": 15, "winnerPlayerId": null, "finishedPlayerIds": [],
		"players": [{"playerId": "local-player", "displayName": "You", "color": "BLUE", "tokens": [0, 12, -1, -1]},
			{"playerId": "cpu-player-1", "displayName": "CPU 1", "color": "GREEN", "tokens": [-1, -1, -1, 30]}],
		"consecutiveSixes": [0, 1], "openingRollConsumed": [true, true], "comebackWaits": [-1, 0],
		"history": [{"eventIndex": 13, "playerId": "cpu-player-1", "roundId": "r", "proofDigest": "d", "fairnessDigest": "f", "outcome": 6, "randomOutcome": 6}],
		"lastAudit": {"x": 1}, "pending": {"outcome": 6, "eventIndex": 14, "roundId": "r2", "clientCommitment": "c", "proofDigest": "p", "legal": [0, 3]}}
	NativePrefs.open("ludo_paws_offline_character_setup_v1").edit().put_string("active_mode", "COMPUTER").put_int("active_player_count", 2) \
		.put_string("active_character_ids", "panda,dog").commit()
	NativePrefs.open("ludoproof_computer_game").edit().put_string("state", JSON.stringify(live)).commit()
	var restored: Dictionary = Saved.load_state("COMPUTER")
	_check(restored.get("turnSeat") == 1 and restored["players"][1]["playerType"] == "BOT" and restored["players"][1]["displayName"] == "Robot 1"
		and restored["players"][0]["tokens"] == [0, 12, -1, -1] and restored["players"][0]["characterId"] == "panda"
		and restored["pendingRoll"]["outcome"] == 6 and restored["pendingRoll"]["legalTokenIndexes"] == [0, 3] and restored["comebackWaits"] == [-1, 0],
		"a live Kotlin checkpoint resumes: tokens, turn, robots (CPU n -> Robot n), Paws, pending roll and comeback state")
	var resumed = LocalMatch.from_saved(restored)
	_check(resumed.turn == 1 and resumed.legal == [0, 3] and not resumed.complete, "the resumed match continues with the pending move")
	NativePrefs.open("ludoproof_offline_game").edit().put_string("state", "{\"rulesetId\":\"ludopaws-unknown\",\"players\":[]}").commit()
	_check(Saved.load_state("PASS_AND_PLAY").is_empty() and not NativePrefs.open("ludoproof_offline_game").contains("state"), "an unsupported or broken save is cleared, like live")
	Saved.clear("COMPUTER")
	_check(not Saved.has_active("COMPUTER"), "discard clears the saved match")
	# Result recovery: a pending result recorded at launch, once.
	Profile.account_id = func() -> String: return ""
	NativePrefs.open("ludopaws_pending_local_results_v1").edit().put_string("m-rec", JSON.stringify({"owner": null, "won": true, "computer": true,
		"characters": ["dog"], "completedAt": int(Time.get_unix_time_from_system() * 1000.0)})).commit()
	Recovery.retry()
	Recovery.retry()
	_check(Profile.load_state()["totalWins"] == 1 and not NativePrefs.open("ludopaws_pending_local_results_v1").contains("m-rec"), "a pending match result is recorded once at launch")
	Profile.account_id = Profile._session_account
	_test_prefs()
	NativePrefs.desktop_dir = ""
	NativePrefs.reset_cache()

## Live StoreCatalogParser rules on the server catalog JSON.
func _catalog_parser() -> void:
	var C = load("res://scripts/game/store/store_catalog.gd")
	var item := func(id: String, category: String, channels: Array, extra := {}) -> Dictionary:
		var it := {"id": id, "status": "PUBLISHED", "contentReady": true, "category": category, "catalogVersion": 3, "name": " Astral Garden ",
			"asset": {"thumbnail": "t.png", "manifest": "m.json", "version": 1, "sha256": "a".repeat(64), "format": "BUILT_IN"},
			"unlock": {"channels": channels, "gems": {"enabled": true, "price": 250}, "rewardedAds": {"enabled": true, "requiredCompletedAds": 15}}}
		it.merge(extra, true)
		return it
	var ok: Dictionary = C.parse_server_catalog({"ok": true, "catalogVersion": 3, "items": [item.call("board_astral", "BOARD", ["PLAY_PRODUCT", "GEMS", "REWARDED_ADS"]),
		item.call("board_new_thing", "BOARD", ["GEMS"])], "suppressedIds": ["board_winter_haven", "board_astral", "BAD ID"]})
	var astral: Dictionary = ok["items"][0]
	_check(ok["ok"] and astral["unlockKind"] == "GEMS" and astral["gemPrice"] == 250 and astral["requiredAdViews"] == 15 and astral["title"] == "Astral Garden"
		and astral["contentAvailable"] and not ok["items"][1]["contentAvailable"] and ok["suppressed"] == ["board_winter_haven"],
		"server catalog: channel priority, prices, unknown content locked, suppression minus published ids")
	_check(not C.parse_server_catalog({"ok": true, "catalogVersion": 3, "items": [item.call("board_astral", "BOARD", ["GEMS"], {"status": "DRAFT"})]})["ok"]
		and not C.parse_server_catalog({"ok": true, "catalogVersion": 3, "items": [item.call("x_board", "HAT", ["GEMS"])]})["ok"]
		and not C.parse_server_catalog({"ok": true, "catalogVersion": 2, "items": [item.call("board_astral", "BOARD", ["GEMS"])]})["ok"]
		and not C.parse_server_catalog({"ok": true, "catalogVersion": 3, "items": [item.call("board_astral", "BOARD", ["GEMS", "GEMS"])]})["ok"],
		"an unpublished, unknown-category, future-version or duplicated-channel item rejects the whole catalog")
	var stale: Dictionary = C.parse_server_catalog({"ok": true, "catalogVersion": 3, "items": [item.call("board_astral", "BOARD", ["GEMS"])]}, true)
	_check(stale["ok"] and not stale["items"][0]["purchaseAvailable"], "a cached (stale) catalog is never purchasable")

## NativePrefs (live SharedPreferences types) and the live GemWalletStore cache rules.
func _native_prefs_and_wallet_cache() -> void:
	_test_prefs()
	var NativePrefs = load("res://scripts/game/platform/native_prefs.gd")
	var p = NativePrefs.open("ludoproof_wallet")
	var wrote: bool = p.edit().put_int("i", 7).put_long("l", 9000000000).put_bool("b", true).put_string("s", "x").put_string_set("ss", ["a", "b"]).commit()
	NativePrefs.reset_cache()
	p = NativePrefs.open("ludoproof_wallet")
	_check(wrote and p.get_int("i") == 7 and p.get_long("l") == 9000000000 and p.get_bool("b") and p.get_string("s") == "x"
		and p.get_string_set("ss") == ["a", "b"] and p.get_int("s", -1) == -1, "native prefs keep live types (int, long, bool, string, string set)")
	p.edit().remove("i").commit()
	_check(not p.contains("i"), "native prefs remove")
	_test_prefs()
	var who := [ACC_A]
	var Inv = load("res://scripts/game/store/cosmetic_inventory.gd")
	var inv = Inv.new(func() -> String: return who[0])
	_check(inv.gems == 0 and inv.is_owned("board_classic") and not inv.is_owned("board_astral"), "no snapshot: only shipped free items, 0 Gems")
	_check(not inv.apply_server_snapshot(_wallet(ACC_B, 1, 50, [])), "a snapshot for another account is never cached")
	_check(not inv.apply_server_snapshot(_wallet(ACC_A, 1, -1, [])) and not inv.apply_server_snapshot(_wallet(ACC_A, 1, 2000000001, [])), "balance must be 0..2e9")
	_check(inv.apply_server_snapshot(_wallet(ACC_A, 5, 50, ["board_astral"], {}, {"dice_pearl_paw": 3})) and inv.gems == 50
		and inv.is_owned("board_astral") and inv.ad_progress == {"dice_pearl_paw": 3}, "server snapshot cached with live keys")
	_check(not inv.apply_server_snapshot(_wallet(ACC_A, 4, 999, [])) and inv.gems == 50, "an older wallet revision never replaces a newer one")
	var raw = NativePrefs.open("ludoproof_wallet")
	_check(raw.get_int("gem_balance") == 50 and raw.get_string("wallet_account_id") == ACC_A and raw.get_long("wallet_revision_v1") == 5
		and raw.get_string_set("owned_cosmetics") == ["board_astral"] and raw.get_int("ad_progress_dice_pearl_paw") == 3, "stored exactly like live GemWalletStore")
	who[0] = ACC_B
	_check(inv.gems == 0 and not inv.is_owned("board_astral") and inv.ad_progress.is_empty(), "another signed-in account never sees the cached wallet")
	_check(inv.apply_server_snapshot(_wallet(ACC_B, 1, 7, [])) and inv.gems == 7 and not raw.get_string_set("owned_cosmetics").has("board_astral"),
		"switching accounts clears the previous account's inventory")
	_check(inv.clear_account_data(ACC_B) and inv.gems == 0 and not raw.contains("wallet_account_id"), "account deletion clears the wallet cache")
	# A pre-account (very old) local wallet is archived, never granted.
	_test_prefs()
	raw = NativePrefs.open("ludoproof_wallet")
	raw.edit().put_int("gem_balance", 999).put_string_set("owned_cosmetics", ["board_astral"]).commit()
	who[0] = ACC_A
	inv.apply_server_snapshot(_wallet(ACC_A, 1, 3, []))
	var archive = JSON.parse_string(str(raw.get_string("legacy_wallet_unverified_archive_v1", "")))
	_check(archive is Dictionary and archive["balance"] == 999 and archive["verified"] == false and inv.gems == 3 and not inv.is_owned("board_astral"),
		"legacy local wallet is archived as unverified and never granted")
	# Character selection keeps the live file and keys.
	inv.select_character("cat")
	var chars = NativePrefs.open("ludo_paws_character_selection_v1")
	_check(chars.get_string("character_id") == "cat" and chars.get_string("pack_id") == "starter_paws" and chars.get_int("schema_version") == 1
		and inv.selected_character() == "cat", "character selection in the live ludo_paws_character_selection_v1 store")
	_test_prefs()
	NativePrefs.desktop_dir = ""
	NativePrefs.reset_cache()

## First-time Demo match (NEW V2 FEATURE): start state, completion on 200 seeds, flags, age bands.
func _demo_match() -> void:
	var Demo = load("res://scripts/game/tutorial/demo_match.gd")
	var Tutorial = load("res://scripts/ui/preview/tutorial_pages.gd")
	var AgeEntry = load("res://scripts/game/safety/age_entry.gd")
	var Coach = load("res://scripts/game/tutorial/tutorial_coach.gd")
	var game = Demo.create("dog")
	var players: Array = game.state["players"]
	var tokens_ok := true
	for p in players:
		var t: Array = p["tokens"].map(func(v): return int(v))
		tokens_ok = tokens_ok and t.count(57) == 3 and t.count(Demo.DEMO_START_POSITION) == 1
	_check(tokens_ok and game.turn == 0 and game.state["rulesetKey"] == "CLASSIC_V8" and game.state["openingRollConsumed"] == [true, true]
		and game.state["comebackWaits"] == [-1, -1] and Demo.start_is_valid(game) and players[0]["color"] == "RED" and players[1]["color"] == "YELLOW"
		and players[0]["characterId"] == "dog" and players[1]["characterId"] in ["goat", "duck", "cat"],
		"demo start: 3 paws home + 1 on the start cell each, no opening bonus, user Red starts vs Robot Yellow")
	var probe = Demo.create("dog")
	probe.apply_roll(3)
	_check(probe.roll == 3, "the demo applies no opening six bonus")
	var finished := 0
	var turns: Array = []
	for s in 200:
		var rng := RandomNumberGenerator.new()
		rng.seed = 1000 + s
		var g = Demo.create(["dog", "goat", "duck", "cat"][s % 4])
		var actions := 0
		while not g.complete and actions < 2000:
			actions += 1
			if g.legal.is_empty():
				g.apply_roll(rng.randi_range(1, 6))
			else:
				g.move(g.robot_choice(Demo.ROBOT_DIFFICULTY))
		if g.complete:
			finished += 1
			turns.append(actions)
	turns.sort()
	_check(finished == 200, "the demo always finishes, robot vs robot on 200 seeds (median %d actions)" % [turns[turns.size() / 2] if not turns.is_empty() else -1])
	_check(Tutorial.first_launch_route(false, false) == &"age" and Tutorial.first_launch_route(true, false) == &"demo"
		and Tutorial.first_launch_route(true, true) == &"home", "first launch: age, then demo, then home")
	var flag := "user://test_tutorial_flag.cfg"
	Demo.reset(flag)
	var first: bool = not Demo.completed(flag)
	Demo.mark_completed(flag)
	_check(first and Demo.completed(flag), "the demo flag is set only when the demo is finished or skipped")
	Demo.reset(flag)
	var age = AgeEntry.new("user://test_age.cfg")
	DirAccess.remove_absolute(ProjectSettings.globalize_path("user://test_age.cfg"))
	_check(not age.has_choice() and not age.save_choice(0) and age.save_choice(14) and age.has_choice() and age.band() == 1
		and AgeEntry.band_for(12) == 0 and AgeEntry.band_for(17) == 2 and AgeEntry.band_for(18) == 3 and not age.optional_google_allowed(),
		"age entry stores only the live band (1-12, 13-15, 16-17, 18+)")
	DirAccess.remove_absolute(ProjectSettings.globalize_path("user://test_age.cfg"))
	var coach = Coach.new("local-0")
	coach.on_turn(true, true, false)
	var roll_hint: String = coach.text()
	coach.on_turn(true, true, false)
	_check(roll_hint == Coach.HINTS["roll"] and coach.shown.size() == 1, "each coach hint shows once")

# ---------------------------------------------------------------- match rail + per-player dice

func _match_rail() -> void:
	var Layout = load("res://scripts/game/match/rail_layout.gd")
	var Geo = load("res://scripts/game/board3d/board_geometry.gd")
	var Rail = load("res://scripts/ui/match/player_rail.gd")
	var Handoff = load("res://scripts/game/match/pass_and_play_handoff.gd")
	var DiceView = load("res://scripts/game/dice/dice_view.gd")
	var colors := ["RED", "GREEN", "YELLOW", "BLUE"]
	# Every first colour (= every board rotation) and 2/3/4 players: each card sits in the screen
	# corner of its own yard after the same rotation the board uses; first player bottom-left.
	var corners_ok := true
	for first in colors:
		var turns: int = Layout.rotation_quarter_turns(first)
		for count in [2, 3, 4]:
			var seats: Array = [first] + colors.filter(func(c): return c != first).slice(0, count - 1)
			for c in seats:
				var slot: int = Layout.slot_for_color(c, first)
				var yard: Vector2 = Geo.rotate_point(Geo.yard_token_center(c, 0, 1.0, 0), 15.0, turns)
				var left := yard.x < 7.5
				var top := yard.y < 7.5
				corners_ok = corners_ok and Layout.is_left(slot) == left and Layout.is_top(slot) == top
			corners_ok = corners_ok and Layout.slot_for_color(first, first) == Layout.BOTTOM_LEFT
	_check(corners_ok, "rail: every card in its yard's corner for 2/3/4 players and every rotation")
	_check(Layout.online_slot("YELLOW", "RED", false, 2) == Layout.TOP_RIGHT and Layout.online_slot("RED", "RED", true, 2) == Layout.BOTTOM_LEFT
		and Layout.online_slot("GREEN", "BLUE", false, 4) == Layout.slot_for_color("GREEN", "BLUE"), "rail: online 2p diagonal, 3-4p by yard")
	_check(Layout.active_seat(2, true, 1) == 1 and Layout.active_seat(2, false, 1) == 2 and Layout.active_seat(2, true, -1) == 2,
		"rail: previous roller stays active while its move presents")
	# Component: only the active dice is shown, inner side, tappable only for the active local human.
	var players: Array = []
	for i in 4:
		players.append({"seat": i, "color": colors[(3 + i) % 4], "name": "P%d" % i, "character": "dog", "bot": false,
			"computer": i == 2, "local": i != 2, "forfeited": false, "tokens": [-1, -1, -1, -1], "team_id": null})
	var rolls: Array = []
	var model := {"players": players, "first_color": "BLUE", "online": false, "active_seat": 0, "roll_enabled": true,
		"dice_style": "dice_classic", "countdown": null, "width": 400.0}
	var cache := {}
	var built: Dictionary = Rail.build(model, cache, func(seat): rolls.append(seat))
	var buttons: Dictionary = built["dice_buttons"]
	var vis := buttons.keys().filter(func(s): return buttons[s].visible)
	_check(vis == [0] and not buttons[0].disabled, "rail: only the active seat's dice is visible and enabled")
	var holder: Node = buttons[0].get_parent()
	_check(holder.get_child(0) is PanelContainer and holder.get_child(1) == buttons[0], "rail: bottom-left dice on the card's inner (right) side")
	buttons[0].pressed.emit()
	_check(rolls == [0], "rail: tapping the active local dice rolls for that seat")
	model["active_seat"] = 2
	built = Rail.build(model, cache, func(seat): rolls.append(seat))
	buttons = built["dice_buttons"]
	buttons[2].pressed.emit()
	_check(buttons[2].visible and buttons[2].disabled and rolls == [0], "rail: robot dice is shown but not tappable")
	model["active_seat"] = 1
	model["online"] = true
	model["local_color"] = "BLUE"
	players[1]["local"] = false
	built = Rail.build(model, cache, func(seat): rolls.append(seat))
	buttons = built["dice_buttons"]
	buttons[1].pressed.emit()
	_check(buttons[1].disabled and rolls == [0], "rail: remote dice is not tappable")
	model["active_seat"] = 0
	model["roll_enabled"] = false
	built = Rail.build(model, cache, func(seat): rolls.append(seat))
	built["dice_buttons"][0].pressed.emit()
	_check(rolls == [0], "rail: no roll while the flow forbids it (double tap / presentation lock)")
	for n in [built["top"], built["bottom"]]:
		n.free()
	for v in cache["cards"].values():
		if is_instance_valid(v): v.free()
	for v in cache["dice"].values():
		if is_instance_valid(v): v.free()
	# Double tap: the turn flow accepts one roll; a second tap while it is in flight is ignored.
	var TurnFlow = load("res://scripts/game/local_turn_flow.gd")
	var Pacing = load("res://scripts/game/gameplay_pacing.gd")
	var g = LocalMatch.new([0, 2], "PASS_AND_PLAY")
	var flow = TurnFlow.new(g, Pacing.NORMAL, false, 1)
	var t0: int = 1000000
	flow.roll(t0)
	var due: int = flow.roll_due
	flow.roll(t0 + 10)
	_check(due >= 0 and flow.roll_due == due and not flow.can_roll(t0 + 10), "rail: a second tap during a roll is blocked")
	flow.free()
	# The dice animation never changes the outcome: the view shows exactly the committed face.
	var view = DiceView.new()
	var faces_ok := true
	for outcome in range(1, 7):
		view.start_rolling()
		view.show_outcome(outcome)
		faces_ok = faces_ok and view.face == outcome and view._target == outcome
		view.show_outcome(outcome, false)
		faces_ok = faces_ok and view.face == outcome
	view.free()
	_check(faces_ok, "rail: the dice animation shows the committed outcome, never its own")
	# Pass & Play handoff policy (OfflinePassAndPlayHandoffPolicy).
	var prev := {"matchId": "m", "status": "ACTIVE", "turnSeat": 0, "pendingRoll": null,
		"players": [{"displayName": "Asha", "color": "RED"}, {"displayName": "Ravi", "color": "YELLOW"}]}
	var cur := prev.duplicate()
	cur["turnSeat"] = 1
	var h = Handoff.resolve("PASS_AND_PLAY", prev, cur)
	_check(h != null and h["name"] == "Ravi" and Handoff.subtitle(h["color"]) == "Yellow paws • your turn", "handoff: fresh seat change prompts the next player")
	var pending := cur.duplicate()
	pending["pendingRoll"] = {"value": 3}
	var finished := cur.duplicate()
	finished["status"] = "FINISHED"
	_check(Handoff.resolve("PASS_AND_PLAY", null, cur) == null and Handoff.resolve("ROBOT", prev, cur) == null
		and Handoff.resolve("PASS_AND_PLAY", prev, prev) == null and Handoff.resolve("PASS_AND_PLAY", prev, pending) == null
		and Handoff.resolve("PASS_AND_PLAY", prev, finished) == null, "handoff: never on first render, CPU mode, extra turn, pending roll or finish")

# ---------------------------------------------------------------- tap hand guidance policy

func _tap_hint_policy() -> void:
	var Policy = load("res://scripts/ui/hints/tap_hint_policy.gd")
	var Art = load("res://scripts/ui/hints/tap_hand_art.gd")
	var p = Policy.new()
	var dice := {"key": "match.dice", "idle_ms": 3000, "priority": 80, "essential": false}
	var ready := {"key": "match.ready", "idle_ms": 5000, "priority": 95, "essential": false}
	var lesson := {"key": "demo.dice", "idle_ms": 1500, "priority": 90, "essential": true}
	_check(p.pick([dice], true, 2999) == null and p.pick([dice], true, 3000) == dice, "tap hand waits for the idle time")
	_check(p.pick([dice, ready], true, 6000) == ready and p.pick([dice, ready], true, 4000) == null,
		"one hand: the highest-priority target wins (and waits for its own idle time)")
	_check(p.pick([dice], false, 9000) == null and p.pick([lesson], false, 1500) == lesson,
		"Play hints off silences hints except essential (Demo / first launch)")
	p.ignored("match.dice")
	_check(p.idle_needed(dice) == 4500 and p.pick([dice], true, 4000) == null, "an ignored hint waits 50 % longer next time")
	p.acted("match.dice")
	_check(p.idle_needed(dice) == 3000, "acting on the target resets the back-off")
	for i in Policy.MAX_SHOWS:
		p.shown("match.dice")
	_check(p.pick([dice], true, 99999) == null, "a non-essential hint stops after %d shows" % Policy.MAX_SHOWS)
	var size := Vector2(48, 60)
	_check(Art.tip_in(size).is_equal_approx(Vector2(45, 8) / Vector2(128, 160) * size)
		and Art.tip_in(size, true, true).is_equal_approx(size - Vector2(45, 8) / Vector2(128, 160) * size), "fingertip anchor mirrors / flips with the art")
	_check(Art.texture(120) != null and Art.texture(120).get_height() >= 120, "tap hand art rasterises crisply at the asked size")

# ---------------------------------------------------------------- Robot / Pass & Play setup (live tests)

func _offline_setup() -> void:
	var Setup = load("res://scripts/game/setup/offline_setup.gd")
	var Starter = load("res://scripts/game/characters/starter_assignment.gd")
	var free := ["dog", "goat", "duck", "cat"]
	# StarterPawsAssignmentPolicyTest
	var cpu: Array = Starter.normalize(4, [], "cat", true, free)
	_check(cpu[0] == "cat" and cpu.duplicate().size() == 4 and _unique(cpu), "computer mode keeps human preference first and assigns unique cpus")
	var pnp: Array = Starter.normalize(4, [], "dog", false, free)
	_check(pnp == ["dog", "goat", "duck", "cat"], "pass and play defaults to unique production animals (%s)" % [pnp])
	var swapped: Array = Starter.select(3, ["dog", "goat", "duck"], 0, "duck", false, free)
	_check(swapped == ["duck", "goat", "dog"], "selecting an animal owned by another local player swaps slots")
	_check(Starter.normalize(2, ["Dog", "bogus"], "dog", false, free) == ["dog", "goat"], "legacy / invalid ids are repaired to a safe assignment")
	_check(Starter.select(2, ["dog", "goat"], 1, "panda", true, free) == ["dog", "goat"], "robot cpu cannot select a locked premium paw")
	_check(Starter.select(3, ["dog", "goat", "duck"], 2, "dog", true, free, 1) == ["dog", "goat", "duck"], "robot cpu cannot take the human paw")
	_check(Starter.select(3, ["dog", "goat", "duck"], 0, "duck", true, free, 1) == ["duck", "goat", "dog"], "human may swap with a cpu using an owned paw")
	_check(Starter.normalize(2, ["panda", "giraffe"], "panda", true, free) == ["dog", "goat"], "saved locked animals cannot bypass entitlement")
	# OfflinePlayerLayoutTest
	_check(Setup.colors_for(2, "BLUE") == ["BLUE", "GREEN"] and Setup.colors_for(2, "RED") == ["RED", "YELLOW"], "two-player uses opposite board corners")
	_check(Setup.colors_for(3, "YELLOW") == ["YELLOW", "BLUE", "RED"] and Setup.colors_for(4, "GREEN") == ["GREEN", "YELLOW", "BLUE", "RED"],
		"three / four players keep deterministic colour order")
	_check(Setup.select_color_for_seat(3, ["RED", "GREEN", "YELLOW"], 1, "BLUE") == ["RED", "BLUE", "YELLOW"], "a free colour changes only the chooser")
	_check(Setup.select_color_for_seat(4, ["RED", "GREEN", "YELLOW", "BLUE"], 2, "RED") == ["YELLOW", "GREEN", "RED", "BLUE"],
		"four-player yellow and red swap without changing other players")
	_check(Setup.select_color_for_seat(2, ["RED", "BLUE"], 0, "RED") == ["RED", "BLUE"], "picking the current colour is a no-op")
	# Every permutation: a swap changes exactly the chooser and the previous owner.
	var all_ok := true
	for count in [2, 3, 4]:
		for perm in _perms(Setup.COLORS, count):
			for seat in count:
				for want in Setup.COLORS:
					var out: Array = Setup.select_color_for_seat(count, perm, seat, want)
					var owner: int = perm.find(want)
					for other in count:
						if other == seat or other == owner:
							continue
						all_ok = all_ok and out[other] == perm[other]
					all_ok = all_ok and out[seat] == want and _unique(out)
	_check(all_ok, "every colour permutation (2/3/4) preserves unrelated seats")
	_check(Setup.normalize_colors(2, ["RED", "BLUE"]) == ["RED", "BLUE"] and Setup.normalize_colors(3, ["RED", "RED", "PINK"]) == ["RED", "BLUE", "GREEN"],
		"saved colours are kept; duplicates / invalid seats repaired (%s)" % [Setup.normalize_colors(3, ["RED", "RED", "PINK"])])
	# Robot colour picker vs the actual CPU seat order (RobotColorPickerMatchesActualCpuSeatOrder).
	var robot_ok := true
	for count in [2, 3, 4]:
		for c in Setup.COLORS:
			var roster: Array = Setup.roster("COMPUTER", Setup.repair("COMPUTER", {"player_count": count, "colors": [c]}, free, "dog"))
			robot_ok = robot_ok and roster.map(func(r): return r["color"]) == Setup.colors_for(count, c)
	_check(robot_ok, "robot colour picker matches the CPU seat order for 2/3/4 players")
	# RobotTeamComposition
	_check(Setup.humans(4, 9) == 3 and Setup.humans(2, 0) == 1 and Setup.humans_after_tap(3, 3, 1) == 2 and Setup.humans_after_tap(3, 3, 2) == 1
		and Setup.humans_after_tap(4, 3, 2) == 1, "human / robot mix: re-tap cycles, a new size starts at one human, always one CPU")
	var mixed: Array = Setup.roster("COMPUTER", Setup.repair("COMPUTER", {"player_count": 4, "humans": 2}, free, "dog"))
	_check(mixed.map(func(r): return r["bot"]) == [false, false, true, true] and mixed[1]["name"] == "Player 2" and mixed[2]["name"] == "Robot 1",
		"mixed robot match: human seats first, CPUs numbered from 1")
	# Persistence + repair.
	var real_path: String = Setup.path
	Setup.path = "user://test_offline_setup.cfg"
	DirAccess.remove_absolute(ProjectSettings.globalize_path(Setup.path))
	_test_prefs()
	var saved: Dictionary = Setup.save_setup("PASS_AND_PLAY", {"player_count": 3, "slot": 2, "paws": ["cat", "dog", "goat"],
		"colors": ["GREEN", "RED", "BLUE"], "names": ["A", "B", "C", "D"]}, free)
	var loaded: Dictionary = Setup.load_setup("PASS_AND_PLAY", free, "dog")
	_check(loaded["player_count"] == 3 and loaded["slot"] == 2 and loaded["paws"] == ["cat", "dog", "goat"] and loaded["colors"] == ["GREEN", "RED", "BLUE"]
		and loaded["names"].slice(0, 3) == ["A", "B", "C"], "Pass & Play setup persists (count, seat, Paws, colours, names)")
	var live = load("res://scripts/game/platform/native_prefs.gd").open("ludo_paws_offline_character_setup_v1")
	_check(live.get_int("pass_and_play_player_count") == 3 and live.get_string("pass_and_play_character_ids") == "cat,dog,goat"
		and live.get_string("pass_and_play_colors_by_seat") == "GREEN,RED,BLUE", "setup stored with the live OfflineCharacterSetupStore keys")
	live.edit().put_string("pass_and_play_character_ids", "panda,panda,Dog").put_string("pass_and_play_colors_by_seat", "RED,RED,TEAL").commit()
	var repaired: Dictionary = Setup.load_setup("PASS_AND_PLAY", free, "dog")
	_check(_unique(repaired["paws"]) and not repaired["paws"].has("panda") and _unique(repaired["colors"]) and repaired["colors"][0] == "RED",
		"a corrupted / locked save is repaired on load (%s %s)" % [repaired["paws"], repaired["colors"]])
	DirAccess.remove_absolute(ProjectSettings.globalize_path(Setup.path))
	Setup.path = real_path
	_test_prefs()
	load("res://scripts/game/platform/native_prefs.gd").desktop_dir = ""
	load("res://scripts/game/platform/native_prefs.gd").reset_cache()

func _perms(items: Array, k: int) -> Array:
	if k == 0:
		return [[]]
	var out: Array = []
	for i in items.size():
		var rest := items.duplicate()
		rest.remove_at(i)
		for p in _perms(rest, k - 1):
			out.append([items[i]] + p)
	return out

# ---------------------------------------------------------------- profile + weekly (live ports)

func _profile_and_weekly() -> void:
	var Profile = load("res://scripts/game/profile/profile_store.gd")
	var Weekly = load("res://scripts/game/profile/weekly_challenge_store.gd")
	_test_prefs()
	Profile.account_id = func() -> String: return ""
	Weekly.account_id = func() -> String: return ""
	_check(Profile.xp_award(true) == 50 and Profile.xp_award(false) == 20, "XP: 20 per match, +30 for a win")
	var lp: Dictionary = Profile.level_progress(230)
	_check(lp["level"] == 3 and lp["into"] == 5 and lp["next"] == 150 and Profile.level_progress(0)["level"] == 1, "levels need 100 + 25 x (level - 1) XP")
	_check(Profile.record_completed_match("m1", "COMPUTER", true, ["dog"], 100) and not Profile.record_completed_match("m1", "COMPUTER", true, ["dog"], 100),
		"a match id is counted once")
	Profile.record_completed_match("m2", "LOCAL", true, ["dog", "cat"], 101)
	Profile.record_completed_match("m3", "LOCAL", false, ["dog", "goat"], 103)
	var st: Dictionary = Profile.load_state()
	_check(st["totalGames"] == 3 and st["totalWins"] == 2 and st["bestWinStreak"] == 2 and st["currentWinStreak"] == 0
		and st["localGames"] == 2 and st["bestDayStreak"] == 2 and st["currentDayStreak"] == 1 and st["totalXp"] == 120,
		"stats, streaks and XP follow ProfileStore.recordCompletedMatch (%s)" % [st])
	_check(st["characterMatches"]["dog"] == 3 and st["characterMatches"]["cat"] == 1, "per-Paw mastery counts each match once per Paw")
	var b: Array = Profile.badges(st)
	var unlocked := b.filter(func(x): return x["unlocked"]).map(func(x): return x["id"])
	_check(b.size() == 9 and unlocked == ["first-win", "ludo-novice"], "badges come from real progress (%s)" % [unlocked])
	_check(Profile.mastery_tier(3) == "Playful pal" and Profile.next_goal(3) == 5 and Profile.next_goal(25) == null, "Paw mastery tiers and goals")
	var now: int = Weekly.now_ms()
	var w0: Dictionary = Weekly.status(now)
	Weekly.completed("w1", ["dog"], now)
	Weekly.completed("w1", ["dog"], now)
	Weekly.completed("w2", ["cat"], now)
	var w1: Dictionary = Weekly.status(now)
	_check(w0["progress"] == 0 and w1["progress"] >= 1 and w1["progress"] <= w1["target"] and Weekly.TITLES.has(w1["title"]),
		"weekly challenge progresses from real matches, a match counts once (%s)" % [w1])
	_check(Weekly.week(0) == 0 and Weekly.week(4 * 86400000) == 1, "weeks start Monday 00:00 UTC")
	# Live account scoping: the device profile becomes the first account's; another account
	# starts fresh; a deleted account never records again.
	var NativePrefs = load("res://scripts/game/platform/native_prefs.gd")
	var raw = NativePrefs.open("ludoproof_profile")
	var stored = JSON.parse_string(str(raw.get_string("profile_json_v1", "")))
	_check(stored is Dictionary and stored["modes"]["CLASSIC"]["games"] == 3 and stored["modes"].has("RUSH") and stored.has("purchases")
		and stored["lastPlayedDay"] == 103, "profile saved in the live ludoproof_profile JSON shape")
	Profile.account_id = func() -> String: return ACC_A
	_check(Profile.load_state()["totalGames"] == 3 and raw.contains("profile_json_v2_account_" + ACC_A) and raw.get_bool("profile_legacy_scope_migrated"),
		"the pre-account profile moves to the first signed-in account")
	Profile.account_id = func() -> String: return ACC_B
	_check(Profile.load_state()["totalGames"] == 0, "another account never inherits those stats")
	_check(Profile.update_display_name("  Asha   Rao  ") == "Asha Rao" and Profile.update_display_name("   ") == "Guest User", "display name rules (ProfileStore.updateDisplayName)")
	Profile.clear_for_account_deletion(ACC_B)
	_check(not Profile.record_completed_match("m9", "COMPUTER", true, ["dog"], 104) and Profile.load_state()["totalGames"] == 0, "a deleted account records nothing")
	Weekly.account_id = func() -> String: return ACC_A
	_check(Weekly.status(now)["badges"] == 0 and NativePrefs.open("weekly_challenges_v1").contains("installation:seen"), "weekly progress is per owner in weekly_challenges_v1")
	Profile.account_id = Profile._session_account
	Weekly.account_id = Weekly._session_account
	_test_prefs()
	NativePrefs.desktop_dir = ""
	NativePrefs.reset_cache()
