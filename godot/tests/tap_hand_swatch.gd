extends SceneTree
## Renders the tap hand on V2 backgrounds (meadow, wood, dark, every yard colour) to a PNG.
##   godot --headless --path . --script res://tests/tap_hand_swatch.gd -- <out.png>
func _initialize() -> void:
	var svg := FileAccess.get_file_as_string("res://assets/ui/icons/tap_hand.svg")
	var hand := Image.new()
	hand.load_svg_from_string(svg, 1.25)
	var bgs := [Color("#5f8f3e"), Color("#7a4a26"), Color("#071c18"), Color("#e85468"), Color("#18b568"), Color("#f3c62d"), Color("#168fdb"), Color("#d9d4c7")]
	var tile := 200
	var out := Image.create(tile * bgs.size(), tile, false, Image.FORMAT_RGBA8)
	for i in bgs.size():
		out.fill_rect(Rect2i(i * tile, 0, tile, tile), bgs[i])
		out.blend_rect(hand, Rect2i(Vector2i.ZERO, hand.get_size()), Vector2i(i * tile + 20, 0))
	out.save_png(OS.get_cmdline_user_args()[0])
	quit()
