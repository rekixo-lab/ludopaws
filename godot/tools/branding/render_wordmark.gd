extends SceneTree
## Rasterises live's unchanged rekixo-logo-static.svg to a given pixel width (no redesign).
##   godot --headless --path . --script res://tools/branding/render_wordmark.gd -- <width_px> <out.png>
func _initialize() -> void:
	var args := OS.get_cmdline_user_args()
	var width := int(args[0])
	var svg := FileAccess.get_file_as_string("res://assets/branding/source/rekixo-logo-static.svg")
	var probe := Image.new()
	probe.load_svg_from_string(svg, 1.0)
	var img := Image.new()
	img.load_svg_from_string(svg, float(width) / probe.get_used_rect().size.x)
	img = img.get_region(img.get_used_rect())
	img.save_png(args[1])
	quit()
