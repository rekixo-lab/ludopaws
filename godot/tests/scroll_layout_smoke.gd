extends SceneTree
## Fixed header + scrolling list on every page (V2 layout), through the real UI:
## - after scrolling to the end, everything outside the list (background, title, back / gear,
##   balance, tabs) is exactly where it was; only the list moved;
## - each page has exactly one scrolling area (the match has none);
## - each Shop tab remembers its own scroll position;
## - Shop / Wallet updates (purchase, equip, reward) keep the page and its scroll position;
## - a drag on a card scrolls without opening it, a tap opens it.
##   godot --headless --path . --script res://tests/scroll_layout_smoke.gd

const Demo = preload("res://scripts/game/tutorial/demo_match.gd")
const AgeEntry = preload("res://scripts/game/safety/age_entry.gd")
const ShopPages = preload("res://scripts/ui/preview/shop_pages.gd")

var failures := 0
var checks := 0

func _check(ok: bool, label: String) -> void:
	checks += 1
	if not ok:
		failures += 1
		printerr("FAIL: ", label)

func _frames(n := 4) -> void:
	for i in n:
		await process_frame

func _initialize() -> void:
	# Tests never touch the player's saved setup.
	load("res://scripts/game/setup/offline_setup.gd").path = "user://test_smoke_setup.cfg"
	DirAccess.remove_absolute(ProjectSettings.globalize_path("user://test_smoke_setup.cfg"))
	Demo.flag_path = "user://scroll_test_tutorial.cfg"
	AgeEntry.default_path = "user://scroll_test_age.cfg"
	AgeEntry.new().save_choice(30)
	Demo.mark_completed()
	root.size = Vector2i(900, 2000)
	var home: Control = load("res://scenes/ui/home/home_screen.tscn").instantiate()
	root.add_child(home)
	await _frames()
	var page: Control = home.preview
	load("res://scripts/game/settings/game_settings.gd").overrides["confirm_exit"] = false
	for route in [&"settings", &"wallet", &"shop", &"rewards", &"collections", &"profile", &"badges",
			&"leaderboard", &"invite", &"online", &"team_up", &"friends"]:
		page.go_home()
		page.open(route)
		await _frames()
		await _fixed_header(page, str(route))
	for tab in [0, 1, 2]:
		page.go_home()
		page.open(&"shop")
		page.select_tab(tab, "shop")
		await _frames()
		await _fixed_header(page, "shop tab %d" % tab)
	# Robot / Pass & Play setup: one fixed screen; the Paw carousel is the only (horizontal)
	# scroller (setup_smoke.gd covers the layout).
	for route in [&"robot", &"pass_and_play"]:
		page.go_home()
		page.open(route)
		await _frames()
		var lists := _scrolls(page)
		_check(lists.size() == 1 and lists[0] is KineticScroll and lists[0].horizontal, "%s: no page scroll, only the Paw carousel" % route)
	await _tab_memory(page)
	await _no_rebuild(page)
	await _drag_vs_tap(page)
	await _touch_drag_vs_tap(page)
	page.go_home()
	page.state.player_count = 2
	page.start_match(&"robot")
	await _frames()
	_check(_scrolls(page).is_empty(), "the match screen never scrolls")
	print("Scroll layout smoke: %d checks, %d failures" % [checks, failures])
	quit(1 if failures > 0 else 0)

func _scrolls(page: Control) -> Array:
	return page.find_children("*", "ScrollContainer", true, false).filter(func(n): return n.is_visible_in_tree())

## Rects of every visible control outside the list (the fixed part of the page).
func _fixed_rects(page: Control, list: Control) -> Dictionary:
	var out := {}
	for node in page.find_children("*", "Control", true, false):
		if node == list or list.is_ancestor_of(node) or not node.is_visible_in_tree():
			continue
		out[node.get_instance_id()] = node.get_global_rect()
	return out

func _fixed_header(page: Control, label: String) -> void:
	var lists := _scrolls(page)
	_check(lists.size() == 1, "%s has exactly one scrolling area (%d)" % [label, lists.size()])
	if lists.size() != 1:
		return
	var list = lists[0]
	_check(list is KineticScroll and list == page.scroll, "%s scrolls through the kinetic list" % label)
	var before := _fixed_rects(page, list)
	_check(before.size() > 3, "%s keeps a fixed header outside the list" % label)
	list.set_position_now(100000)
	await _frames(2)
	var after := _fixed_rects(page, list)
	var moved := 0
	for id in before:
		if after.has(id) and not after[id].is_equal_approx(before[id]):
			moved += 1
	_check(moved == 0, "%s: header / title / tabs / balance stay put while the list scrolls (%d moved)" % [label, moved])
	if list.max_scroll() > 0.0:
		_check(list.scroll_vertical > 0, "%s: the list itself scrolls" % label)
	list.set_position_now(0)

func _tab_memory(page: Control) -> void:
	page.go_home()
	page.open(&"shop")
	page.select_tab(1, "shop")
	await _frames()
	page.scroll.set_position_now(240)
	await _frames(1)
	var boards_pos: int = page.scroll.scroll_vertical
	page.select_tab(2, "shop")
	await _frames()
	page.scroll.set_position_now(90)
	await _frames(1)
	page.select_tab(1, "shop")
	await _frames()
	_check(boards_pos > 0 and page.scroll.scroll_vertical == boards_pos,
		"Boards tab remembers its scroll position (%d, now %d)" % [boards_pos, page.scroll.scroll_vertical])
	page.select_tab(2, "shop")
	await _frames()
	_check(page.scroll.scroll_vertical == 90, "Dice tab remembers its own position (%d)" % page.scroll.scroll_vertical)
	page.select_tab(0, "shop")

func _no_rebuild(page: Control) -> void:
	for route in [&"shop", &"wallet"]:
		page.go_home()
		page.open(route)
		await _frames()
		var list = page.scroll
		list.set_position_now(150)
		await _frames(1)
		var pos: int = list.scroll_vertical
		ShopPages.update(page)
		await _frames()
		_check(is_instance_valid(list) and page.scroll == list and list.scroll_vertical == pos,
			"%s: a state update keeps the page and its scroll position (%d)" % [route, pos])

func _drag_vs_tap(page: Control) -> void:
	page.go_home()
	page.open(&"shop")
	page.select_tab(0, "shop")
	await _frames()
	var list: KineticScroll = page.scroll
	list.set_position_now(0)
	var buttons := list.find_children("*", "BaseButton", true, false).filter(func(b): return b.is_visible_in_tree() and not b.disabled)
	_check(not buttons.is_empty(), "shop has tappable cards")
	if buttons.is_empty():
		return
	var button: BaseButton = buttons[0]
	var fired := [0]
	var count := func(): fired[0] += 1
	button.pressed.connect(count)
	var at: Vector2 = button.get_global_rect().get_center()
	# Drag: press on the card, move 80 px up, release.
	_touch(at, true)
	await _frames(1)
	for k in range(1, 9):
		_drag(at - Vector2(0, 10 * k))
		await _frames(1)
	_touch(at - Vector2(0, 80), false)
	await _frames(2)
	_check(fired[0] == 0 and list.scroll_vertical > 0, "a drag on a card scrolls (%d) and does not open it" % list.scroll_vertical)
	page.dismiss()
	list.set_position_now(0)
	await _frames(2)
	at = button.get_global_rect().get_center()
	_touch(at, true)
	await _frames(1)
	_touch(at, false)
	await _frames(2)
	_check(fired[0] == 1, "a clean tap opens the card")
	if is_instance_valid(button):
		button.pressed.disconnect(count)
	page.dismiss()

func _touch(at: Vector2, pressed: bool) -> void:
	var e := InputEventMouseButton.new()
	e.button_index = MOUSE_BUTTON_LEFT
	e.pressed = pressed
	e.position = at
	e.global_position = at
	e.button_mask = MOUSE_BUTTON_MASK_LEFT if pressed else 0
	root.push_input(e, true)

func _drag(at: Vector2) -> void:
	var e := InputEventMouseMotion.new()
	e.position = at
	e.global_position = at
	e.button_mask = MOUSE_BUTTON_MASK_LEFT
	root.push_input(e, true)


## The same through touch events: a drag ending over a card must not click it; a touch tap
## must. NOTE: headless Godot does not emulate mouse events from touch the way a phone does,
## so the emulated-release bug this guards against is verified on the device (phone setup
## tour, carousel fling), not reproducible here.
func _touch_drag_vs_tap(page: Control) -> void:
	page.go_home()
	page.open(&"shop")
	page.select_tab(0, "shop")
	await _frames()
	var list: KineticScroll = page.scroll
	list.set_position_now(0)
	await _frames(2)
	var buttons := list.find_children("*", "BaseButton", true, false).filter(func(b): return b.is_visible_in_tree() and not b.disabled)
	if buttons.is_empty():
		_check(false, "touch: shop has tappable cards")
		return
	var fired := [0]
	var count := func(): fired[0] += 1
	for b in buttons:
		b.pressed.connect(count)
	var to_window: Transform2D = root.get_final_transform()
	var at: Vector2 = buttons[0].get_global_rect().get_center()
	_screen_touch(to_window * at, true)
	await _frames(1)
	for k in range(1, 13):
		_screen_drag(to_window * (at - Vector2(0, 12 * k)))
		await _frames(1)
	# End the drag back over a card.
	_screen_drag(to_window * at)
	await _frames(1)
	_screen_touch(to_window * at, false)
	await _frames(3)
	_check(fired[0] == 0, "touch: a drag that ends over a card does not open it")
	page.dismiss()
	await _frames(2)
	at = buttons[0].get_global_rect().get_center()
	_screen_touch(to_window * at, true)
	await _frames(1)
	_screen_touch(to_window * at, false)
	await _frames(3)
	_check(fired[0] == 1, "touch: a clean tap opens the card (%d)" % fired[0])
	for b in buttons:
		if is_instance_valid(b):
			b.pressed.disconnect(count)
	page.dismiss()

func _screen_touch(at: Vector2, pressed: bool) -> void:
	var e := InputEventScreenTouch.new()
	e.index = 0
	e.pressed = pressed
	e.position = at
	Input.parse_input_event(e)

func _screen_drag(at: Vector2) -> void:
	var e := InputEventScreenDrag.new()
	e.index = 0
	e.position = at
	Input.parse_input_event(e)
