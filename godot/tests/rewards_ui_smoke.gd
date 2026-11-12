extends SceneTree
## Rewards page + Collections Get tests (desktop server stand-in with the live rules).
##   godot --headless --path . --script res://tests/rewards_ui_smoke.gd

const RewardsPage = preload("res://scripts/ui/preview/rewards_page.gd")
const Demo = preload("res://scripts/game/tutorial/demo_match.gd")
const AgeEntry = preload("res://scripts/game/safety/age_entry.gd")

var failures := 0
var checks := 0

func _check(ok: bool, label: String) -> void:
	checks += 1
	if not ok:
		failures += 1
		printerr("FAIL: ", label)

func _frames(n := 3) -> void:
	for i in n:
		await process_frame

func _view(page) -> Node:
	var found: Array = page.find_children("*", "", true, false).filter(func(n): return n.get_class() == "VBoxContainer" and n.has_method("refresh_card"))
	return found[0] if not found.is_empty() else null

func _texts(node: Node) -> String:
	return " | ".join(node.find_children("*", "Label", true, false).map(func(l): return l.text))

func _initialize() -> void:
	# Pure wheel geometry: every server amount, from any start, lands on a matching segment.
	var landed := true
	for amount in [1, 2, 3, 5]:
		for i in 25:
			var segs: Array = RewardsPage.segments_for(amount)
			var seg: int = segs[i % segs.size()]
			var rot := RewardsPage.stop_rotation(randf() * 5000.0, seg, 400.0, randf_range(-15, 15))
			landed = landed and RewardsPage.SEGMENTS[RewardsPage.segment_at(rot)] == amount
	_check(landed and RewardsPage.SEGMENTS == [1, 2, 1, 3, 1, 2, 1, 5], "wheel stop angle always shows the server amount (all amounts, any start)")
	_check(RewardsPage.odds_lines([{"gems": 1, "percent": 40}, {"gems": 5, "percent": 60}]) == ["1 Gem · 40%", "5 Gems · 60%"], "odds come from the server's spinOutcomes")
	_check(RewardsPage.countdown(3 * 3600000 + 25 * 60000 + 5000, 0) == "03:25", "countdown hh:mm from resetsAt")
	var states := ["idle", "loading", "unavailable", "verifying", "done", "collected"].map(func(s): return RewardsPage.ad_text(s, 2, "05:00"))
	_check(states.all(func(t): return not t.is_empty()) and states.duplicate().reduce(func(acc, t): return acc if acc.has(t) else acc + [t], []).size() == 6
		and states[0].contains("+2 Gems") and states[5].contains("05:00"), "every ad bonus state has its own clear message")
	# Animated wheel run per amount (real tweens, sped up).
	Engine.time_scale = 8.0
	for amount in [1, 2, 3, 5]:
		var wheel = RewardsPage.RewardWheel.new()
		wheel.custom_minimum_size = Vector2(200, 200)
		root.add_child(wheel)
		var ticks := [0]
		wheel.tick.connect(func(): ticks[0] += 1)
		wheel.start_spin()
		await _frames(10)
		await wheel.stop_on(amount)
		_check(RewardsPage.SEGMENTS[RewardsPage.segment_at(wheel.rotation_deg)] == amount and ticks[0] > 8 and not wheel.spinning,
			"spinning wheel stops on a %d segment with ticks (%d)" % [amount, ticks[0]])
		wheel.queue_free()
	Engine.time_scale = 1.0

	# Page flow on the desktop stand-in.
	Demo.flag_path = "user://rewards_test_tutorial.cfg"
	AgeEntry.default_path = "user://rewards_test_age.cfg"
	AgeEntry.new().save_choice(30)
	Demo.mark_completed()
	root.size = Vector2i(900, 2000)
	var home: Control = load("res://scenes/ui/home/home_screen.tscn").instantiate()
	root.add_child(home)
	await _frames()
	var page: Control = home.preview
	var store = page.state.store
	store.desktop.spin_roll = func() -> int: return 96   # server picks 5
	page.open(&"rewards")
	for i in 30:
		await process_frame
		if _view(page) != null:
			break
	var view = _view(page)
	_check(view != null and view.cards.has("daily") and view.cards.has("spin") and view.cards.has("bonus"), "three reward cards")
	var heights: Array = view.cards.values().map(func(c): return c.custom_minimum_size.y)
	_check(heights.all(func(h): return h == heights[0]), "the three cards share one size")
	_check(view.summary.text.begins_with("Today's rewards: 0/3 collected · resets in "), "summary strip (%s)" % view.summary.text)
	var gems_before: int = page.state.inventory.gems
	await view._claim_daily()
	await _frames(40)
	_check(_view(page) == view and view.chest.opened and _texts(view.cards["daily"]).contains("Come back tomorrow") and page.state.inventory.gems == gems_before + 3
		and view.summary.text.begins_with("Today's rewards: 1/3"), "daily gift: chest opens, +3 from the server, countdown, no page rebuild")
	Engine.time_scale = 8.0
	await view._claim_spin()
	Engine.time_scale = 1.0
	await _frames(5)
	if is_instance_valid(page.overlay):
		page.dismiss()
	_check(_view(page) == view and _texts(view.cards["spin"]).contains("+5") and page.state.inventory.gems == gems_before + 8
		and RewardsPage.SEGMENTS[RewardsPage.segment_at(view.wheel.rotation_deg)] == 5 and view.wheel.dimmed,
		"free spin lands on the server's 5, wheel dimmed with today's result")
	await view._watch_bonus()
	await _frames(5)
	_check(_texts(view.cards["bonus"]).contains("Gems added ✓") and view.summary.text.begins_with("Today's rewards: 3/3"), "ad bonus: verified and added")
	# A new reward day during a spin: the wheel stops empty with a clear message.
	page.refresh()
	await _frames()
	store.desktop.clock = func() -> int: return int(Time.get_unix_time_from_system() * 1000.0) + 86400000
	view = _view(page)
	view.status["spinClaimed"] = false
	view.refresh_card("spin")
	var before2: int = page.state.inventory.gems
	Engine.time_scale = 8.0
	await view._claim_spin()
	Engine.time_scale = 1.0
	_check(page.state.inventory.gems == before2 and _texts(view.cards["spin"]).contains("A new reward day started") and not view.wheel.spinning,
		"day change: no Gems, the wheel stops and asks for a refresh")
	# Collections: compact Get pills; unavailable collections have none.
	page.open(&"collections")
	await _frames()
	page.select_tab(1, "collections")
	await _frames(5)
	var gets: Array = page.find_children("*", "", true, false).filter(func(n): return n is Button and n.has_meta("collection_get"))
	var sizes_ok: bool = not gets.is_empty() and gets.all(func(b): return b.size.y <= 40.0 and b.size.y < (b.get_parent().get_parent() as Control).size.y)
	_check(sizes_ok, "collection Get buttons stay compact (%s)" % [gets.map(func(b): return b.size)])
	var unavailable := page.find_children("*", "Label", true, false).filter(func(l): return l.text.contains("Not available right now"))
	_check(not unavailable.is_empty(), "collections with retired items show 'Not available right now' instead of Get")
	print("Rewards UI smoke: %d checks, %d failures" % [checks, failures])
	quit(1 if failures > 0 else 0)
