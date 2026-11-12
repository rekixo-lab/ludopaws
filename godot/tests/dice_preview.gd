extends SceneTree
## Every live dice skin: resting on its shop preview face, with the attention pulse, and
## mid-roll. Writes dice_all.png.   godot --path . --script res://tests/dice_preview.gd -- res://.godot/preview

const DiceView = preload("res://scripts/game/dice/dice_view.gd")
const Catalog = preload("res://scripts/game/store/store_catalog.gd")
const PREVIEW_FACE := {"dice_classic": 6, "dice_pearl_paw": 1, "dice_forest_dew": 5, "dice_lagoon_pearl": 4,
	"dice_frost_crystal": 6, "dice_starlight": 3, "dice_ember_forge": 5, "dice_prism_royal": 6}

func _initialize() -> void:
	var out_dir: String = OS.get_cmdline_user_args()[0] if OS.get_cmdline_user_args().size() > 0 else "user://"
	root.size = Vector2i(960, 380)
	var bg := ColorRect.new()
	bg.color = Color("#1c382d")
	bg.size = Vector2(960, 380)
	root.add_child(bg)
	var views: Array = []
	for i in Catalog.DICE_IDS.size():
		var id: String = Catalog.DICE_IDS[i]
		for row in 3:
			var dice := DiceView.new()
			dice.position = Vector2(10 + i * 118, 10 + row * 120)
			dice.size = Vector2(110, 110)
			root.add_child(dice)
			dice.style_id = id
			dice.team_color = ["RED", "GREEN", "BLUE"][row]
			dice.show_outcome(PREVIEW_FACE[id], false)
			views.append([dice, row])
	for i in 3:
		await process_frame
	for v in views:
		if v[1] == 1:
			v[0].set_attention(true)
		elif v[1] == 2:
			v[0].start_rolling()
	await create_timer(0.31).timeout
	await RenderingServer.frame_post_draw
	root.get_texture().get_image().save_png(out_dir.path_join("dice_all.png"))
	print("saved dice")
	quit()
