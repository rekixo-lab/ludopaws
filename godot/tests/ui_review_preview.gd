extends SceneTree
## Screenshots of the reworked screens into <out>/ui_review/: Settings, Wallet (top / lower),
## Shop, Rewards, Collections (gear), a Robot match (gear), Settings from the match and the
## return to the same match.
##   godot --path . --script res://tests/ui_review_preview.gd -- res://.godot/preview

const Demo = preload("res://scripts/game/tutorial/demo_match.gd")
const AgeEntry = preload("res://scripts/game/safety/age_entry.gd")

var out := ""

func _shot(name: String, frames := 20) -> void:
	DisplayServer.window_set_mode(DisplayServer.WINDOW_MODE_WINDOWED)
	root.size = Vector2i(900, 2000)
	for i in frames:
		await process_frame
	await RenderingServer.frame_post_draw
	root.get_texture().get_image().save_png(out.path_join(name + ".png"))
	print("saved ", name)

func _initialize() -> void:
	var base: String = OS.get_cmdline_user_args()[0] if OS.get_cmdline_user_args().size() > 0 else "user://"
	out = base.path_join("ui_review")
	DirAccess.make_dir_recursive_absolute(ProjectSettings.globalize_path(out))
	Demo.flag_path = "user://preview_ui_tutorial.cfg"
	AgeEntry.default_path = "user://preview_ui_age.cfg"
	AgeEntry.new().save_choice(30)
	Demo.mark_completed()
	var home: Control = load("res://scenes/ui/home/home_screen.tscn").instantiate()
	root.add_child(home)
	await process_frame
	var page: Control = home.preview
	load("res://scripts/game/settings/game_settings.gd").overrides["confirm_exit"] = false
	for route in [&"settings", &"wallet", &"shop", &"rewards", &"collections"]:
		page.go_home()
		page.open(route)
		await _shot(str(route))
		if route == &"shop":
			page.select_tab(1, "shop")
			await _shot("shop_boards", 40)
			page.select_tab(2, "shop")
			await _shot("shop_dice")
			page.select_tab(0, "shop")
		if route == &"wallet":
			page.scroll.set_position_now(100000)
			await _shot("wallet_lower")
		if route == &"shop":
			page.select_tab(0, "shop")
			await _shot("shop_paws_top")
			page.scroll.set_position_now(100000)
			await _shot("shop_paws_scrolled")
	for route in [&"robot", &"pass_and_play"]:
		for n in [2, 3, 4]:
			page.go_home()
			page.open(route)
			var view = page.state.get_meta("setup_view")
			view._on_count(n)
			if route == &"robot" and n == 3:
				view._on_count(3)
			await _shot("setup_%s_%dp" % [route, n])
	page.go_home()
	page.state.player_count = 4
	page.start_match(&"robot")
	await _shot("match", 40)
	page.open_nested(&"settings")
	await _shot("settings_from_match")
	page.back()
	await _shot("match_returned", 40)
	quit()
