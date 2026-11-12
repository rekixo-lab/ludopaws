extends SceneTree
## Saves screenshots of the editor preview scenes (needs a display, not --headless):
##   godot --path . --script res://tests/capture_previews.gd -- res://.godot/preview [species...]
## Without species arguments: board, robot match and the characters preview of every ported species.

const Catalog = preload("res://scripts/game/characters/catalog.gd")

func _initialize() -> void:
	var args := OS.get_cmdline_user_args()
	var out_dir: String = args[0] if args.size() > 0 else "user://"
	var species: Array = args.slice(1) if args.size() > 1 else []
	var shots: Array = []
	if species.is_empty():
		shots.append(["board_preview", 0.5, ""])
		shots.append(["robot_match_preview", 8.0, ""])
		species = Catalog.RENDERED.keys()
	for id in species:
		shots.append(["characters_preview", 3.0, id])
	for shot in shots:
		var scene: Control = load("res://scenes/previews/%s.tscn" % shot[0]).instantiate()
		if not shot[2].is_empty():
			scene.character_id = shot[2]
		root.add_child(scene)
		await create_timer(shot[1]).timeout
		await RenderingServer.frame_post_draw
		var name: String = shot[0] if shot[2].is_empty() else "characters_%s" % shot[2]
		var path: String = out_dir.path_join("%s.png" % name)
		root.get_texture().get_image().save_png(path)
		print("saved ", ProjectSettings.globalize_path(path))
		scene.queue_free()
		await process_frame
	quit()
