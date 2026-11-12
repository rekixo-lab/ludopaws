extends Node
## DEV-ONLY performance probe (autoload). Does nothing in release exports or unless the flag
## file user://perf_probe.cfg exists (deleted on launch: one run per pushed file) (pushed with `adb shell run-as <pkg>`). It logs to logcat:
##   PERF stall   – any frame slower than 50 ms, with the route and the slowest spans that frame
##   PERF second  – fps, p50/p95/max frame ms, static memory, node count, draw calls
##   PERF span    – per-scenario totals of instrumented spans (page rebuild, dice redraw, …)
## Scenario (optional, [probe] scenario=): "robot" plays a 4-player Robot match as a human
## would, then "shop" switches Shop tabs and opens Wallet; "both" runs robot then shop.

const FLAG := "user://perf_probe.cfg"
static var enabled := false
static var _spans := {}      # name -> [count, total_us, max_us]
static var _frame_spans := {} # this frame: name -> us
## A/B switches read from the flag file ([probe] key=true) to isolate a cost on device.
static var flags := {}
## Tap-to-feedback: set when a human taps the dice, consumed by the dice's first rolling frame.
static var tap_us := 0

static func mark_tap() -> void:
	if enabled:
		tap_us = Time.get_ticks_usec()

static func tap_feedback() -> void:
	if enabled and tap_us > 0:
		span("tap_to_dice_motion", Time.get_ticks_usec() - tap_us)
		tap_us = 0

static func flag(name: String) -> bool:
	return enabled and bool(flags.get(name, false))

var _frames: PackedFloat32Array = []
var _last_us := 0
var _second_started := 0
var _scenario := ""
var _page: Control

static func span(name: String, us: int) -> void:
	if not enabled:
		return
	var s: Array = _spans.get_or_add(name, [0, 0, 0])
	s[0] += 1
	s[1] += us
	s[2] = maxi(s[2], us)
	_frame_spans[name] = int(_frame_spans.get(name, 0)) + us

func _ready() -> void:
	if not OS.is_debug_build() or not FileAccess.file_exists(FLAG):
		set_process(false)
		return
	enabled = true
	var cfg := ConfigFile.new()
	cfg.load(FLAG)
	# One-shot: the flag is consumed on launch, so a normal app start never runs the probe
	# (it used to keep auto-playing on every launch while the file stayed on the phone).
	DirAccess.remove_absolute(ProjectSettings.globalize_path(FLAG))
	_scenario = str(cfg.get_value("probe", "scenario", ""))
	for key in cfg.get_section_keys("probe"):
		flags[key] = cfg.get_value("probe", key)
	RenderingServer.viewport_set_measure_render_time(get_viewport().get_viewport_rid(), true)
	print("PERF flags ", flags)
	process_priority = 1000
	_last_us = Time.get_ticks_usec()
	_second_started = Time.get_ticks_msec()
	print("PERF start scenario=%s screen=%s scale=%s" % [_scenario, DisplayServer.screen_get_size(), get_window().get_final_transform().get_scale()])
	if not _scenario.is_empty():
		_run.call_deferred()

func _route() -> String:
	return str(_page.current) if is_instance_valid(_page) else "?"

func _process(_delta: float) -> void:
	var now_us := Time.get_ticks_usec()
	var ms := (now_us - _last_us) / 1000.0
	_last_us = now_us
	_frames.append(ms)
	if _scroll_recording:
		_scroll_frames.append(ms)
	if ms > 50.0:
		var parts := []
		for k in _frame_spans:
			parts.append("%s=%.1f" % [k, _frame_spans[k] / 1000.0])
		var vp := get_viewport().get_viewport_rid()
		print("PERF stall ms=%.1f route=%s process=%.1f setup=%.1f render_cpu=%.1f render_gpu=%.1f %s" % [ms, _route(),
			Performance.get_monitor(Performance.TIME_PROCESS) * 1000.0, RenderingServer.get_frame_setup_time_cpu(),
			RenderingServer.viewport_get_measured_render_time_cpu(vp), RenderingServer.viewport_get_measured_render_time_gpu(vp), " ".join(parts)])
	_frame_spans.clear()
	if Time.get_ticks_msec() - _second_started >= 1000:
		_second_started = Time.get_ticks_msec()
		var sorted := _frames.duplicate()
		sorted.sort()
		var n := sorted.size()
		if n > 0:
			var vp2 := get_viewport().get_viewport_rid()
			print("PERF gpu route=%s render_cpu=%.1f render_gpu=%.1f vram=%.1fMB" % [_route(), RenderingServer.viewport_get_measured_render_time_cpu(vp2),
				RenderingServer.viewport_get_measured_render_time_gpu(vp2), Performance.get_monitor(Performance.RENDER_VIDEO_MEM_USED) / 1048576.0])
			print("PERF second route=%s frames=%d p50=%.1f p95=%.1f max=%.1f mem=%.1fMB nodes=%d draws=%d" % [_route(), n,
				sorted[n / 2], sorted[mini(n - 1, int(n * 0.95))], sorted[n - 1],
				Performance.get_monitor(Performance.MEMORY_STATIC) / 1048576.0, Performance.get_monitor(Performance.OBJECT_NODE_COUNT),
				Performance.get_monitor(Performance.RENDER_TOTAL_DRAW_CALLS_IN_FRAME)])
		_frames.clear()

func _report(label: String) -> void:
	for k in _spans:
		var s: Array = _spans[k]
		print("PERF span %s %s count=%d total=%.0fms avg=%.2fms max=%.1fms" % [label, k, s[0], s[1] / 1000.0, s[1] / 1000.0 / maxi(s[0], 1), s[2] / 1000.0])
	_spans.clear()

func _wait(ms: int) -> void:
	await get_tree().create_timer(ms / 1000.0).timeout

func _run() -> void:
	var home := get_tree().current_scene
	while home == null or not "preview" in home or home.preview == null:
		await get_tree().process_frame
		home = get_tree().current_scene
	_page = home.preview
	await _wait(2500)
	_report("boot")
	if _scenario in ["robot", "both"]:
		await _robot()
	if _scenario in ["shop", "both"]:
		await _shop()
	if _scenario == "tour":
		await _tour()
	if _scenario == "scroll":
		await _scroll_runs()
	if _scenario == "hands":
		await _hand_tour()
	if _scenario == "setup":
		await _setup_tour()
	if _scenario == "real_data":
		await _real_data_tour()
	if _scenario == "pass_play":
		await _pass_play_tour()
	if _scenario == "net":
		await _net_probe()
	if _scenario == "account":
		await _account_probe()
	if _scenario == "platform":
		await _platform_probe()
	if _scenario == "wallet":
		await _wallet_probe()
	if _scenario == "ads":
		await _ads_probe()
	if _scenario == "rewards_shots":
		await _rewards_shots()
	print("PERF done")

func _robot() -> void:
	var LocalMatchPage = load("res://scripts/game/local_match_page.gd")
	_page.state.player_count = 4
	var t0 := Time.get_ticks_usec()
	_page.start_match(&"robot")
	print("PERF open match ms=%.1f" % ((Time.get_ticks_usec() - t0) / 1000.0))
	var game = _page.state.local_game
	var started := Time.get_ticks_msec()
	while _page.current == &"match" and Time.get_ticks_msec() - started < int(flags.get("robot_ms", 90000)):
		await _wait(700)
		if _page.current != &"match" or game.turn != 0 or is_instance_valid(_page.overlay):
			continue
		var flow = _page.state.get_meta("turn_flow")
		if flow.can_roll(flow.now()):
			LocalMatchPage._roll(_page, game)
		elif flow.can_move(flow.now()) and not game.legal.is_empty():
			_page.state.get_meta("board_view").pawn_selected.emit(game.legal[0])
	_report("robot")
	_page.reset_match()
	_page.go_home()
	await _wait(1500)

func _shop() -> void:
	for round in 3:
		var t0 := Time.get_ticks_usec()
		_page.open(&"shop")
		print("PERF open shop round=%d ms=%.1f" % [round, (Time.get_ticks_usec() - t0) / 1000.0])
		await _wait(1500)
		for tab in [1, 2, 0]:
			t0 = Time.get_ticks_usec()
			_page.select_tab(tab, "shop")
			print("PERF shop tab=%d ms=%.1f" % [tab, (Time.get_ticks_usec() - t0) / 1000.0])
			await _wait(2000)
		t0 = Time.get_ticks_usec()
		_page.open(&"wallet")
		print("PERF open wallet ms=%.1f" % ((Time.get_ticks_usec() - t0) / 1000.0))
		await _wait(1500)
		_page.go_home()
		await _wait(1000)
	_report("shop")


## Screenshot tour: prints "PERF tour <name>" and holds each screen for the host's screencap.
func _hold(name: String, ms := 3500) -> void:
	await _wait(1500)
	print("PERF tour ", name)
	await _wait(ms)

func _tour() -> void:
	for route in [&"settings", &"wallet", &"shop", &"rewards", &"collections"]:
		_page.go_home()
		_page.open(route)
		await _hold(str(route))
		if route == &"wallet":
			_page.scroll.set_position_now(100000)
			await _hold("wallet_lower")
		if route == &"shop":
			_page.select_tab(1, "shop")
			await _hold("shop_boards", 5000)
			_page.select_tab(2, "shop")
			await _hold("shop_dice")
	for count in [2, 3, 4]:
		_page.go_home()
		_page.state.player_count = count
		_page.start_match(&"robot")
		await _hold("match_%dp" % count, 6000)
		if count == 4:
			_page.open_nested(&"settings")
			await _hold("settings_from_match")
			_page.back()
			await _hold("match_returned")
		_page.reset_match()
	_page.go_home()
	_page.start_match(&"demo")
	await _hold("demo", 5000)
	_page.reset_match()
	_page.go_home()


## Scroll test: real touch flings on each list; frame stats only while the list moves.
var _scroll_frames: PackedFloat32Array = []
var _scroll_recording := false

func _fling(from_y: float, to_y: float, ms: int) -> void:
	var win := Vector2(get_window().size)
	var x := win.x * 0.5
	var press := InputEventScreenTouch.new()
	press.index = 0
	press.pressed = true
	press.position = Vector2(x, from_y)
	Input.parse_input_event(press)
	await get_tree().process_frame
	var steps := maxi(ms / 16, 4)
	for k in range(1, steps + 1):
		var drag := InputEventScreenDrag.new()
		drag.index = 0
		drag.position = Vector2(x, lerpf(from_y, to_y, float(k) / steps))
		drag.relative = Vector2(0, (to_y - from_y) / steps)
		Input.parse_input_event(drag)
		await get_tree().process_frame
	var release := InputEventScreenTouch.new()
	release.index = 0
	release.pressed = false
	release.position = Vector2(x, to_y)
	Input.parse_input_event(release)

func _scroll_target(name: String) -> void:
	await _wait(2000)
	var win := Vector2(get_window().size)
	_scroll_frames.clear()
	_scroll_recording = true
	var start_scroll: int = _page.scroll.scroll_vertical if is_instance_valid(_page.scroll) else -1
	for k in 2:
		await _fling(win.y * 0.80, win.y * 0.45, 220)
		print("PERF tour scroll_%s_%d" % [name, k])
		await _wait(1400)
	var mid: int = _page.scroll.scroll_vertical if is_instance_valid(_page.scroll) else -1
	await _fling(win.y * 0.40, win.y * 0.85, 220)
	await _wait(1400)
	_scroll_recording = false
	var sorted := _scroll_frames.duplicate()
	sorted.sort()
	var n := sorted.size()
	var slow := 0
	for f in sorted:
		if f > 50.0:
			slow += 1
	if n > 0:
		var total := 0.0
		for f in sorted:
			total += f
		print("PERF scroll %s frames=%d fps=%.1f p50=%.1f p95=%.1f max=%.1f over50=%d moved=%d->%d" % [name, n,
			1000.0 * n / total, sorted[n / 2], sorted[mini(n - 1, int(n * 0.95))], sorted[n - 1], slow, start_scroll, mid])

func _scroll_runs() -> void:
	for tab in [0, 1, 2]:
		_page.go_home()
		_page.open(&"shop")
		_page.select_tab(tab, "shop")
		await _scroll_target(["shop_paws", "shop_boards", "shop_dice"][tab])
	for route in [&"wallet", &"rewards", &"collections", &"leaderboard"]:
		_page.go_home()
		_page.open(route)
		await _scroll_target(str(route))
	_page.go_home()


## Tap hand screenshots: waits for the hand on each target, then holds for the screencap.
func _wait_key(key: String, ms := 12000) -> void:
	var g := get_tree().root.get_node_or_null("Guidance")
	var until := Time.get_ticks_msec() + ms
	while Time.get_ticks_msec() < until:
		await get_tree().process_frame
		if g != null and g.showing_key() == key:
			await _wait(700)
			return

func _wait_board_hand(ms := 15000) -> void:
	var H = load("res://scripts/game/board3d/legal_hints.gd")
	var until := Time.get_ticks_msec() + ms
	while Time.get_ticks_msec() < until:
		await get_tree().process_frame
		var game = _page.state.local_game
		if game != null and not game.legal.is_empty() and game.turn == 0 and H.hand_visible(Time.get_ticks_msec()) \
				and Time.get_ticks_msec() % H.HAND_PERIOD_MS > 500:
			return

func _snap(name: String) -> void:
	print("PERF tour ", name)
	await _wait(1600)

func _hand_tour() -> void:
	var Match = load("res://scripts/game/local_match_page.gd")
	var games := int(_page.state.setting("total_games", 0))
	# Home (new player view), then restore the real count.
	_page.state.set_setting("total_games", 0)
	_page.go_home()
	await _wait_key("home.robot")
	await _snap("hand_home")
	_page.state.set_setting("total_games", games)
	# Age entry.
	_page._show(&"age")
	await _wait_key("age.slider")
	await _snap("hand_age")
	# Robot match: dice, then a legal paw.
	_page.go_home()
	_page.state.player_count = 4
	_page.start_match(&"robot")
	await _wait_key("match.dice")
	await _snap("hand_match_dice")
	var game = _page.state.local_game
	# Screenshot setup only: two of the player's paws on the road, so a roll gives a real
	# choice (no single automatic move).
	game.state["players"][0]["tokens"] = [5, 14, -1, -1]
	game._sync()
	_page.refresh()
	for attempt in 12:
		if game.turn == 0 and not game.legal.is_empty():
			break
		var flow = _page.state.get_meta("turn_flow")
		if game.turn == 0 and flow.can_roll(flow.now()):
			Match._roll(_page, game)
		await _wait(1500)
	if not game.legal.is_empty():
		await _wait_board_hand()
		await _snap("hand_match_paw")
	_page.reset_match()
	# Demo: dice, then a paw.
	_page.go_home()
	_page.start_match(&"demo")
	await _wait_key("match.dice")
	await _snap("hand_demo_dice")
	game = _page.state.local_game
	game.state["players"][0]["tokens"] = [57, 57, 20, 30]
	game._sync()
	_page.refresh()
	await _wait(600)
	Match._roll(_page, game)
	await _wait(1500)
	if not game.legal.is_empty():
		await _wait_board_hand()
		await _snap("hand_demo_paw")
	_page.reset_match()
	# Pass & Play READY.
	_page.go_home()
	_page.state.player_count = 2
	_page.start_match(&"pass_and_play")
	await _wait(800)
	Match.show_handoff(_page, {"seat": 1, "name": "Player 2", "color": _page.state.local_game.state["players"][1]["color"]})
	await _wait_key("match.ready")
	await _snap("hand_ready")
	_page.dismiss()
	_page.reset_match()
	# Rewards: daily gift.
	_page.go_home()
	_page.open(&"rewards")
	await _wait_key("rewards.daily_gift")
	await _snap("hand_daily_gift")
	_page.go_home()


func _setup_tour() -> void:
	for route in [&"robot", &"pass_and_play"]:
		for n in [2, 3, 4]:
			_page.go_home()
			_page.open(route)
			var view = _page.state.get_meta("setup_view")
			view._on_count(n)
			if route == &"robot" and n == 4:
				view._on_count(4)
			await _wait(900)
			await _snap("setup_%s_%dp" % [route, n])
	# Pass & Play 4p colour swap: seat 3 takes seat 1's colour.
	var v = _page.state.get_meta("setup_view")
	v._on_seat(2)
	await _wait(500)
	await _snap("setup_swap_before")
	v._on_color(v.s["colors"][0])
	await _wait(500)
	await _snap("setup_swap_after")
	# Carousel scroll fps (horizontal flings over the Paw row).
	var row: Control = v._carousel
	var y: float = (get_window().get_final_transform() * row.get_global_rect().get_center()).y
	var win := Vector2(get_window().size)
	_scroll_frames.clear()
	_scroll_recording = true
	for k in 3:
		await _fling_x(y, win.x * 0.85, win.x * 0.20, 220)
		await _wait(900)
		if k == 0:
			await _snap("setup_carousel_scrolled")
		await _fling_x(y, win.x * 0.20, win.x * 0.85, 220)
		await _wait(900)
	_scroll_recording = false
	var sorted := _scroll_frames.duplicate()
	sorted.sort()
	var n := sorted.size()
	var total := 0.0
	var slow := 0
	for f in sorted:
		total += f
		if f > 50.0:
			slow += 1
	if n > 0:
		print("PERF scroll setup_carousel frames=%d fps=%.1f p50=%.1f p95=%.1f max=%.1f over50=%d" % [n, 1000.0 * n / total,
			sorted[n / 2], sorted[mini(n - 1, int(n * 0.95))], sorted[n - 1], slow])
	# Locked preview.
	for id in v._cards:
		if not _page.state.inventory.unlocked_character_ids().has(id):
			v._on_paw(id)
			break
	await _wait(1200)
	await _snap("setup_locked_preview")
	_page.dismiss()
	# The match carries the setup.
	_page.start_match(&"pass_and_play")
	await _wait(2500)
	await _snap("setup_match_pass_and_play")
	_page.reset_match()
	_page.go_home()

func _fling_x(y: float, from_x: float, to_x: float, ms: int) -> void:
	var press := InputEventScreenTouch.new()
	press.index = 0
	press.pressed = true
	press.position = Vector2(from_x, y)
	Input.parse_input_event(press)
	await get_tree().process_frame
	var steps := maxi(ms / 16, 4)
	for k in range(1, steps + 1):
		var drag := InputEventScreenDrag.new()
		drag.index = 0
		drag.position = Vector2(lerpf(from_x, to_x, float(k) / steps), y)
		Input.parse_input_event(drag)
		await get_tree().process_frame
	var release := InputEventScreenTouch.new()
	release.index = 0
	release.pressed = false
	release.position = Vector2(to_x, y)
	Input.parse_input_event(release)


## Screens of the real-data pass (before / after screenshots).
func _real_data_tour() -> void:
	for item in [[&"collections", 0, "weekly"], [&"collections", 1, "collections"], [&"profile", -1, "profile"],
			[&"friends", -1, "friends"], [&"leaderboard", -1, "leaderboard"], [&"badges", -1, "badges"], [&"invite", -1, "invite"]]:
		_page.go_home()
		_page.open(item[0])
		if item[1] >= 0:
			_page.select_tab(item[1], str(item[0]))
		await _wait(1800)
		await _snap(item[2])
	for n in [2, 3, 4]:
		_page.go_home()
		_page.state.player_count = n
		_page.start_match(&"robot")
		await _wait(2500)
		await _snap("match_%dp" % n)
		_page.reset_match()
	_page.go_home()


## Pass & Play 4p: a few turns with the banner (default), then the READY modal (setting ON).
func _pass_play_tour() -> void:
	var Match = load("res://scripts/game/local_match_page.gd")
	var original: bool = _page.state.setting("pass_phone_prompt", false)
	for prompt in [false, true]:
		_page.state.set_setting("pass_phone_prompt", prompt)
		_page.go_home()
		_page.state.player_count = 4
		_page.start_match(&"pass_and_play")
		await _wait(1500)
		var game = _page.state.local_game
		var shots := 0
		var start := Time.get_ticks_msec()
		while shots < (3 if not prompt else 1) and Time.get_ticks_msec() - start < 60000:
			await get_tree().process_frame
			if is_instance_valid(_page.overlay):
				await _wait(600)
				await _snap("pass_play_ready_modal")
				shots += 1
				_page.dismiss()
				continue
			var banner = _page.state.get_meta("turn_banner") if _page.state.has_meta("turn_banner") else null
			if not prompt and is_instance_valid(banner) and banner.modulate.a > 0.9 and not banner.has_meta("shot"):
				banner.set_meta("shot", true)
				await _snap("pass_play_banner_%d" % shots)
				shots += 1
				continue
			var flow = _page.state.get_meta("turn_flow")
			if flow.can_roll(flow.now()):
				await _wait(300)
				Match._roll(_page, game)
			elif flow.can_move(flow.now()) and not game.legal.is_empty():
				await _wait(300)
				if not game.legal.is_empty() and flow.can_move(flow.now()):
					_page.state.get_meta("board_view").pawn_selected.emit(game.legal[0])
		_page.reset_match()
	_page.state.set_setting("pass_phone_prompt", original)
	_page.go_home()


## Read-only network check against the configured backend (B2 on device).
func _net_probe() -> void:
	var GameApi = load("res://scripts/game/online/game_api.gd")
	var Transport = load("res://scripts/game/online/http_transport.gd")
	var url: String = load("res://scripts/game/online/online_status.gd").backend_url()
	print("PERF net url=", url)
	if url.is_empty():
		return
	var t = Transport.new()
	add_child(t)
	var api = GameApi.new(url, t)
	for path in ["/api/capabilities", "/api/store/catalog"]:
		var started := Time.get_ticks_msec()
		var res: Dictionary = await api.request("GET", path)
		print("PERF net GET %s ok=%s error=%s ms=%d" % [path, res["ok"], res.get("error", ""), Time.get_ticks_msec() - started])


## B1 on device: plugin present, guest session (test package), read-only wallet / catalog.
## Prints facts only (never tokens).
func _account_probe() -> void:
	var Account = load("res://scripts/game/account/account_service.gd")
	var Transport = load("res://scripts/game/online/http_transport.gd")
	var url: String = load("res://scripts/game/online/online_status.gd").backend_url()
	print("PERF account plugin=%s url=%s" % [Engine.has_singleton("LudoPawsPlatform"), url])
	var t = Transport.new()
	add_child(t)
	var acc = Account.new(url, t)
	var before: Dictionary = acc.current_session()
	print("PERF account saved_session=%s mode=%s" % [not before.is_empty(), acc.store.lastAccountMode()])
	var res: Dictionary = await acc.ensure_guest_session()
	var s: Dictionary = res.get("session", {})
	print("PERF account guest ok=%s error=%s account=%s.. same_as_before=%s" % [res["ok"], res.get("error", ""),
		str(s.get("accountId", "")).left(8), not before.is_empty() and before.get("accountId") == s.get("accountId")])
	var again: Dictionary = acc.current_session()
	print("PERF account reload_from_keystore=%s" % [again.get("accountId") == s.get("accountId") and not again.is_empty()])
	for call in ["wallet", "store_catalog", "profile"]:
		var r: Dictionary = await acc.call(call)
		var keys := (r.get("data", {}) as Dictionary).keys().slice(0, 6) if r["ok"] else []
		print("PERF account %s ok=%s error=%s keys=%s" % [call, r["ok"], r.get("error", ""), keys])

## B1 platform bridge on device: age store (live family_privacy_v1), Play Games configuration,
## Account autoload status, and the Credential Manager round trip with a throwaway nonce (no
## server call; the test package has no OAuth client, so an error status is expected).
func _platform_probe() -> void:
	var p = Engine.get_singleton("LudoPawsPlatform") if Engine.has_singleton("LudoPawsPlatform") else null
	print("PERF platform plugin=%s" % [p != null])
	if p == null:
		return
	print("PERF platform age_band=%d pgs_configured=%s pgs_initialized=%s" % [p.ageBand(), p.playGamesConfigured(), p.playGamesInitialized()])
	var account = get_node_or_null("/root/Account")
	account.refresh_status()
	while account.busy:
		await get_tree().process_frame
	await get_tree().create_timer(3.0).timeout
	print("PERF platform account signed_in=%s google_linked=%s status=%s configured=%s" % [account.signed_in(), account.google_linked, account.status_text, account.sign_in_configured()])
	var t0 := Time.get_ticks_msec()
	p.googleSignIn(account.server_client_id, "0".repeat(64))
	var timer := get_tree().create_timer(20.0)
	var got := ["TIMEOUT", ""]
	var on := func(status, token):
		got[0] = status
		got[1] = token
	p.connect("google_credential", on)
	while got[0] == "TIMEOUT" and timer.time_left > 0.0:
		await get_tree().process_frame
	print("PERF platform credential status=%s token_present=%s ms=%d" % [got[0], not str(got[1]).is_empty(), Time.get_ticks_msec() - t0])

## B3 on device (read-only): server wallet GET for this device's guest account, cached in the
## live ludoproof_wallet prefs through the plugin, read back after a cache reset.
func _wallet_probe() -> void:
	var Inv = load("res://scripts/game/store/cosmetic_inventory.gd")
	var Service = load("res://scripts/game/store/store_service.gd")
	var NativePrefs = load("res://scripts/game/platform/native_prefs.gd")
	var inv = Inv.new()
	var store = Service.new(inv)
	print("PERF wallet backend=%s" % ["desktop" if store.desktop != null else "server"])
	var t0 := Time.get_ticks_msec()
	var res: Dictionary = await store.refresh_wallet()
	print("PERF wallet refresh ok=%s error=%s ms=%d" % [res.get("ok"), res.get("error", ""), Time.get_ticks_msec() - t0])
	var cat: Dictionary = await store.refresh_catalog()
	print("PERF wallet catalog ok=%s stale=%s items=%s error=%s remote=%d" % [cat.get("ok"), cat.get("stale"), cat.get("items"), cat.get("error"), store.catalog.remote.size()])
	NativePrefs.reset_cache()
	var raw = NativePrefs.open("ludoproof_wallet")
	print("PERF wallet prefs account_match=%s balance=%d owned=%d revision=%d gems_view=%d" % [raw.get_string("wallet_account_id", "") == inv._owner(),
		raw.get_int("gem_balance", -1), raw.get_string_set("owned_cosmetics", []).size(), raw.get_long("wallet_revision_v1", -1), inv.gems])
	var r: Dictionary = await store.rewards_status()
	print("PERF wallet rewards ok=%s day=%s dailyClaimed=%s collections=%d" % [r.get("ok"), r.get("day", ""), r.get("dailyClaimed", ""), r.get("collections", []).size()])

## B5 on device with Google's TEST ad units: consent, SDK init and a rewarded test ad load
## (nothing is shown or credited).
func _ads_probe() -> void:
	var Ads = load("res://scripts/game/ads/ads_service.gd")
	var ads = Ads.new()
	print("PERF ads platform=%s unit=%s debug=%s" % [ads._platform != null, ads.rewarded_unit, OS.is_debug_build()])
	if ads._platform == null:
		return
	var t0 := Time.get_ticks_msec()
	ads._platform.adsPrepare(false)
	var consent: Array = await ads._next(["consent"])
	print("PERF ads consent=%s can_request=%s ms=%d" % [consent[1], ads._platform.adsCanRequest(), Time.get_ticks_msec() - t0])
	if consent[1] == "ready":
		ads._platform.adsLoadRewarded(ads.rewarded_unit)
		var loaded: Array = await ads._next(["rewarded_loaded", "rewarded_failed"])
		print("PERF ads rewarded=%s detail=%s ready=%s ms=%d" % [loaded[0], loaded[1], ads._platform.adsRewardedReady(), Time.get_ticks_msec() - t0])

## Rewards / Collections / match-end screenshots (desktop stand-in build: no live claims).
func _shot(name: String, hold := 1600) -> void:
	print("PERF tour ", name)
	await _wait(hold)

func _rewards_view() -> Node:
	var found: Array = _page.find_children("*", "", true, false).filter(func(n): return n.has_method("refresh_card"))
	return found[0] if not found.is_empty() else null

func _rewards_shots() -> void:
	var store = _page.state.store
	if store.desktop != null:
		store.desktop.spin_roll = func() -> int: return 82   # the stand-in "server" picks 3
	_page.open(&"rewards")
	await _wait(2500)
	await _shot("daily_closed")
	var view = _rewards_view()
	view._claim_daily()
	await _wait(150)
	await _shot("daily_opening", 2200)
	await _shot("daily_claimed")
	if is_instance_valid(_page.scroll):
		_page.scroll.set_position_now(view.cards["spin"].position.y)
	await _wait(600)
	await _shot("wheel_ready")
	view._claim_spin()
	await _wait(700)
	await _shot("wheel_spinning", 1000)
	await _wait(2600)
	await _shot("wheel_stopped")
	if is_instance_valid(_page.overlay):
		_page.dismiss()
	await _wait(400)
	await _shot("wheel_spun")
	if is_instance_valid(_page.scroll):
		_page.scroll.set_position_now(view.cards["bonus"].position.y)
	for st in ["idle", "loading", "unavailable", "verifying"]:
		view._bonus(st)
		await _wait(300)
		await _shot("ad_" + st, 1300)
	view._watch_bonus()
	await _wait(1500)
	await _shot("ad_done")
	view.refresh_card("bonus")
	await _wait(300)
	await _shot("ad_collected")
	_page.open(&"collections")
	await _wait(800)
	_page.select_tab(1, "collections")
	await _wait(1200)
	await _shot("collections")
	# Robot match end: a nearly finished game plays out; before / after the result overlay.
	_page.go_home()
	_page.state.player_count = 2
	_page.start_match(&"robot")
	await _wait(1500)
	var game = _page.state.local_game
	game.state["players"][0]["tokens"] = [57, 57, 57, 52]
	game.state["players"][1]["tokens"] = [57, 57, 57, 50]
	game.state["finishOrderPlayerIds"] = []
	game._sync()
	_page.refresh()
	Engine.time_scale = 2.0
	var started := Time.get_ticks_msec()
	while not game.complete and Time.get_ticks_msec() - started < 60000:
		await _wait(300)
		var flow = _page.state.get_meta("turn_flow")
		if game.turn == 0 and flow.can_roll(flow.now()):
			load("res://scripts/game/local_match_page.gd")._roll(_page, game)
		elif game.turn == 0 and flow.can_move(flow.now()):
			_page.state.get_meta("board_view").pawn_selected.emit(game.legal[0])
	Engine.time_scale = 1.0
	await _wait(200)
	await _shot("match_end_before", 900)
	var Overlay = load("res://scripts/ui/preview/match_result_overlay.gd")
	var waited := 0
	while Overlay.current(_page) == null and waited < 15000:
		await _wait(250)
		waited += 250
	print("PERF result overlay after_complete_ms~%d" % (waited + 1100))
	await _wait(700)
	await _shot("match_end_after", 2500)
