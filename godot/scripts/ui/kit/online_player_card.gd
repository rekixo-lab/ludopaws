extends Button

const SURFACE := preload("res://assets/ui/backgrounds/mode_card_surface.svg")
const TWO := preload("res://assets/ui/icons/team.svg")
const FOUR := preload("res://assets/ui/icons/four_players.svg")

var player_count := 2
var selected := false:
	set(value):
		selected = value
		queue_redraw()

func _ready() -> void:
	custom_minimum_size.y = 116
	size_flags_horizontal = SIZE_EXPAND_FILL
	tooltip_text = "%d Players" % player_count
	var clear := StyleBoxEmpty.new()
	for state in ["normal", "hover", "pressed", "focus", "hover_pressed"]:
		add_theme_stylebox_override(state, clear)
	resized.connect(queue_redraw)
	focus_entered.connect(queue_redraw)
	focus_exited.connect(queue_redraw)

func _draw() -> void:
	var rect := Rect2(Vector2(3, 4), size - Vector2(6, 8))
	var radius := 19.0
	MeadowDraw.soft_shadow(self, rect, radius, 0.50, 4.0)
	if selected:
		MeadowDraw.rounded_gradient(self, rect, radius, Color("#91ffd0"), Color("#65f3a4"))
		for i in range(1, 8):
			var inset := float(i) * 1.55
			var depth := float(i) / 7.0
			MeadowDraw.rounded_gradient(self, rect.grow(-inset), radius - inset,
				Color("#55d6a1").lerp(Color("#215449"), depth),
				Color("#42fa9a").lerp(Color("#174335"), depth))
		MeadowDraw.rounded_outline(self, rect.grow(-0.8), radius - 0.8, Color("#eaffeb"), 2)
	else:
		draw_texture_rect(SURFACE, rect, false)
	var art := TWO if player_count == 2 else FOUR
	var art_width := 65.0 if player_count == 2 else 78.0
	var art_height := art_width / art.get_size().aspect()
	var center := size.x * 0.5
	draw_texture_rect(art, Rect2(center - art_width / 2.0, 18, art_width, art_height), false)
	var font := MeadowStyle.text_font()
	var title := "%d Players" % player_count
	var text_width := font.get_string_size(title, HORIZONTAL_ALIGNMENT_LEFT, -1, 22).x
	var baseline := Vector2(center - text_width * 0.5, 90)
	draw_string_outline(font, baseline, title, HORIZONTAL_ALIGNMENT_LEFT, -1, 22, 4, Color("#0b211a"))
	draw_string(font, baseline, title, HORIZONTAL_ALIGNMENT_LEFT, -1, 22, Color("#fff2d4"))
	if has_focus():
		draw_line(baseline + Vector2(0, 4), baseline + Vector2(text_width, 4), Color("#d7ffeb"), 1.2, true)
