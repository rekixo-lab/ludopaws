extends Control
## Displays level and XP from its caller, without changing progression state.

var level := 1
var experience := 0
var next_level_xp := 100

func _init() -> void:
	custom_minimum_size = Vector2(140, 58)
	mouse_filter = MOUSE_FILTER_IGNORE

func _ready() -> void:
	resized.connect(queue_redraw)

func _draw() -> void:
	var font: Font = UIFonts.display()
	var caption := "Level %d" % level
	var caption_width := font.get_string_size(caption, HORIZONTAL_ALIGNMENT_LEFT, -1, 15).x
	var badge := Rect2(10, 4, caption_width + 36, 25)
	_gold_rail(badge, Color("#48331e"), Color("#271e13"))
	_star_shield(Vector2(14, 16))
	var baseline := 22.0
	draw_string_outline(font, Vector2(32, baseline + 1), caption, HORIZONTAL_ALIGNMENT_LEFT, -1, 15, 2, Color("#2a1c10"))
	draw_string(font, Vector2(32, baseline), caption, HORIZONTAL_ALIGNMENT_LEFT, -1, 15, Color("#fff0cc"))

	var track := Rect2(1, 36, size.x - 2, 22)
	_gold_rail(track, Color("#19291f"), Color("#0a1913"))
	var inner := track.grow(-3.0)
	var progress := clampf(float(experience) / maxi(1, next_level_xp), 0.0, 1.0)
	if inner.size.x * progress >= 2.0:
		var fill := Rect2(inner.position, Vector2(inner.size.x * progress, inner.size.y))
		MeadowDraw.rounded_gradient(self, fill, 8, Color("#9ce9c3"), Color("#3aaf85"))
		MeadowDraw.rounded_outline(self, fill.grow(-0.6), 7, Color("#c0edb8"), 0.8)
	var xp_font: Font = MeadowStyle.text_font()
	var xp_text := "%d / %d XP" % [experience, next_level_xp]
	var xp_width := xp_font.get_string_size(xp_text, HORIZONTAL_ALIGNMENT_LEFT, -1, 12).x
	var xp_x := (size.x - xp_width) * 0.5
	var on_fill := inner.position.x + inner.size.x * progress >= xp_x + xp_width
	draw_string(xp_font, Vector2(xp_x, 51), xp_text, HORIZONTAL_ALIGNMENT_LEFT, -1, 12, Color("#0c2c20") if on_fill else Color("#f5e3bd"))

func _gold_rail(rect: Rect2, top: Color, bottom: Color) -> void:
	var radius := rect.size.y * 0.5
	MeadowDraw.soft_shadow(self, rect, radius, 0.40, 2)
	MeadowDraw.rounded_gradient(self, rect, radius, Color("#ffe4a0"), Color("#aa7134"))
	MeadowDraw.rounded_gradient(self, rect.grow(-1.6), radius - 1.6, Color("#815b2f"), Color("#56391e"))
	MeadowDraw.rounded_gradient(self, rect.grow(-2.4), radius - 2.4, top, bottom)

func _star_shield(center: Vector2) -> void:
	var outline := PackedVector2Array([
		Vector2(0,-15), Vector2(5,-12), Vector2(13,-9), Vector2(13,1),
		Vector2(11,8), Vector2(6,12), Vector2(0,15), Vector2(-6,12),
		Vector2(-11,8), Vector2(-13,1), Vector2(-13,-9), Vector2(-5,-12),
	])
	draw_set_transform(center + Vector2(0, 2))
	draw_colored_polygon(outline, Color("#25190e"))
	draw_set_transform(center)
	var bounds := Rect2(-13,-15,26,30)
	MeadowDraw.gradient_fill(self, outline, bounds, Color("#ffe2a1"), Color("#c48230"))
	draw_set_transform(center, 0, Vector2.ONE * 0.79)
	MeadowDraw.gradient_fill(self, outline, bounds, Color("#745029"), Color("#302013"))
	draw_set_transform(center + Vector2(0, 0.5))
	var star := PackedVector2Array()
	for i in 10:
		var angle := -PI * 0.5 + i * PI / 5.0
		star.append(Vector2.from_angle(angle) * (8.3 if i % 2 == 0 else 4.2))
	draw_colored_polygon(star, Color("#8c541e"))
	draw_set_transform(center - Vector2(0, 0.9))
	MeadowDraw.gradient_fill(self, star, Rect2(-9,-9,18,18), Color("#fff0ab"), Color("#eca93d"))
	draw_set_transform(Vector2.ZERO)
