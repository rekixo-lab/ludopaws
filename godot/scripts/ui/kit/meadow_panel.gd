@tool
class_name MeadowPanel
extends PanelContainer
## Reference-style card: deep green gradient, thin olive-gold rim, inner light edge.

@export var tint := Color("#1a4331"):
	set(value):
		tint = value
		queue_redraw()

var raised_rim_top := 0.0

func _init() -> void:
	size_flags_horizontal = SIZE_EXPAND_FILL
	var margins := StyleBoxEmpty.new()
	margins.content_margin_left = 18
	margins.content_margin_right = 18
	margins.content_margin_top = 12
	margins.content_margin_bottom = 16
	add_theme_stylebox_override("panel", margins)

func _draw() -> void:
	var rect := Rect2(Vector2(1.5, 1.5), size - Vector2(3, 3))
	if raised_rim_top > 0.0:
		rect.position.y += raised_rim_top
		rect.size.y -= raised_rim_top
		_draw_raised_rim(rect)
		return
	var r := 16.0
	MeadowDraw.soft_shadow(self, Rect2(rect.position + Vector2(0, 4), rect.size), r, 0.5, 6.0)
	draw_colored_polygon(MeadowDraw.rounded_points(rect.grow(1.5), r + 1.5), Color("#0a1c14"))
	draw_colored_polygon(MeadowDraw.rounded_points(rect, r), Color("#9c8a58"))
	var inner := rect.grow(-1.6)
	MeadowDraw.rounded_gradient(self, inner, r - 1.6, tint.lightened(0.04), tint.darkened(0.32))
	MeadowDraw.rounded_outline(self, inner.grow(-1.2), r - 2.8, Color(1, 1, 1, 0.07), 1.0)
	# Faint top glow, as in the reference cards.
	var glow := Rect2(inner.position + Vector2(10, 1), Vector2(inner.size.x - 20, minf(40.0, inner.size.y * 0.4)))
	MeadowDraw.rounded_gradient(self, glow, 12.0, Color(1, 1, 1, 0.05), Color(1, 1, 1, 0.0))

func _draw_raised_rim(rect: Rect2) -> void:
	MeadowDraw.soft_shadow(self, rect, 24, 0.55, 8)
	MeadowDraw.rounded_gradient(self, rect, 24, Color("#51321e"), Color("#27180e"))
	MeadowDraw.rounded_gradient(self, rect.grow(-1), 23, Color("#fff0b4"), Color("#ad6c32"))
	MeadowDraw.rounded_gradient(self, rect.grow(-3), 21, Color("#ae7138"), Color("#f2c47c"))
	MeadowDraw.rounded_gradient(self, rect.grow(-5), 19, Color("#123b2b"), Color("#081f18"))
	MeadowDraw.rounded_outline(self, rect.grow(-6), 18, Color("#517254"), 1)

func _notification(what: int) -> void:
	if what == NOTIFICATION_RESIZED:
		queue_redraw()
