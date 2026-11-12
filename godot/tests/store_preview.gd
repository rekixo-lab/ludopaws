extends SceneTree
## Shop (Paws / Boards / Dice) and Wallet screenshots through the real UI, with an
## in-memory demo wallet (nothing is saved).
##   godot --path . --script res://tests/store_preview.gd -- res://.godot/preview

func _initialize() -> void:
	var out_dir: String = OS.get_cmdline_user_args()[0] if OS.get_cmdline_user_args().size() > 0 else "user://"
	var home: Control = load("res://scenes/ui/home/home_screen.tscn").instantiate()
	root.add_child(home)
	for i in 3:
		await process_frame
	var page: Control = home.preview
	var inv = page.state.inventory
	inv.path = "user://store_preview_demo.cfg" # never touch the real cache
	inv.gems = 420
	inv.owned["character_donkey"] = true
	inv.owned["board_astral"] = true
	inv.ad_progress["character_goat_lumi"] = 4
	inv.ad_progress["dice_pearl_paw"] = 12
	for tab in 3:
		page.state.tab_indexes["shop"] = tab
		if tab == 0:
			page.open_nested(&"shop")
		else:
			page.refresh()
		for i in 20:
			await process_frame
		await RenderingServer.frame_post_draw
		var name: String = ["shop_paws", "shop_boards", "shop_dice"][tab]
		root.get_texture().get_image().save_png(out_dir.path_join(name + ".png"))
		print("saved ", name)
	page.open_nested(&"wallet")
	for i in 10:
		await process_frame
	await RenderingServer.frame_post_draw
	root.get_texture().get_image().save_png(out_dir.path_join("wallet.png"))
	print("saved wallet")
	DirAccess.remove_absolute(ProjectSettings.globalize_path("user://store_preview_demo.cfg"))
	quit()
