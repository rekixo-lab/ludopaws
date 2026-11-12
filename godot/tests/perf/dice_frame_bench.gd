extends SceneTree
## Desktop micro-benchmark: cost of one rolling dice frame per skin (draw commands + nodes).
const Renderers = preload("res://scripts/game/dice/dice_renderers.gd")
const CE = preload("res://scripts/game/render/canvas_emitter.gd")
const Art = preload("res://scripts/game/board3d/board_art_player.gd")

func _initialize() -> void:
	for id in Renderers.PALETTES:
		var r = Renderers.create(id)
		var t := Time.get_ticks_usec()
		var nodes := 0
		var cmds := 0
		for i in 30:
			var c := CE.new()
			r.draw(c, 140.0, Renderers.palette(id, 0xFFE85468), Renderers.frame(3, true, false, false, 0.6, false, 2, i * 20, 0.0))
			var root := Control.new()
			Art.build(root, c.commands, 1.0)
			cmds = c.commands.size()
			nodes = root.find_children("*", "", true, false).size()
			root.free()
		print("BENCH %s spatial=%s cmds=%d nodes=%d ms/frame=%.2f" % [id, r.uses_spatial_roll, cmds, nodes, (Time.get_ticks_usec() - t) / 1000.0 / 30])
	quit()
