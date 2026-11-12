extends SceneTree
## Saves board_art_<id>.png for every recorded live board (art layer only).
##   godot --path . --script res://tests/board_art_preview.gd -- res://.godot/preview

const Player = preload("res://scripts/game/board3d/board_art_player.gd")

func _initialize() -> void:
	var args := OS.get_cmdline_user_args()
	var out_dir: String = args[0] if args.size() > 0 else "user://"
	root.size = Vector2i(600, 600)
	for id in Player.art().keys():
		var art := Player.new()
		art.edge = 600.0
		root.add_child(art)
		art.board_id = id
		for i in 4:
			await process_frame
		await RenderingServer.frame_post_draw
		root.get_texture().get_image().get_region(Rect2i(0, 0, 600, 600)).save_png(out_dir.path_join("board_art_%s.png" % id))
		print("saved ", id)
		art.queue_free()
		await process_frame
	quit()
