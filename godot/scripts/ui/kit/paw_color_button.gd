@tool
class_name PawColorButton
extends MeadowButton
## Colour picker chip from the reference: cream pill, glossy team-coloured paw, chevron.

@export var team := 0:
	set(value):
		team = clampi(value, 0, MeadowStyle.TEAM_COLORS.size() - 1)
		tooltip_text = MeadowStyle.TEAM_NAMES[team]
		queue_redraw()
@export var show_chevron := true:
	set(value):
		show_chevron = value
		queue_redraw()

func _init() -> void:
	super()
	look = Look.CREAM
	custom_minimum_size = Vector2(106, 48)
	size_flags_horizontal = SIZE_SHRINK_END

func _draw() -> void:
	super()
	var rect := Rect2(Vector2(2, 2), size - Vector2(4, 6))
	var paw_x := rect.position.x + rect.size.x * (0.38 if show_chevron else 0.5)
	MeadowDraw.glossy_paw(self, Vector2(paw_x, rect.get_center().y + 1.0), rect.size.y * 0.8, MeadowStyle.TEAM_COLORS[team])
	if show_chevron:
		MeadowDraw.chevron_down(self, Vector2(rect.end.x - 24.0, rect.get_center().y + 1.0), 13.0, Color("#2a1c10"))
