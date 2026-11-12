@tool
class_name MeadowOrnament
extends Control
## Inline decoration: leaf sprigs on both sides of the gap where a caption or a
## button sits.

@export var gap := 160.0:
	set(value):
		gap = value
		queue_redraw()
@export var sprig_length := 26.0:
	set(value):
		sprig_length = value
		queue_redraw()

func _init() -> void:
	mouse_filter = MOUSE_FILTER_IGNORE

func _notification(what: int) -> void:
	if what == NOTIFICATION_RESIZED:
		queue_redraw()

func _draw() -> void:
	var center := size * 0.5
	MeadowDraw.sprig(self, center + Vector2(-gap * 0.5 - sprig_length * 0.15, sprig_length * 0.25), sprig_length, -1.0)
	MeadowDraw.sprig(self, center + Vector2(gap * 0.5 + sprig_length * 0.15, sprig_length * 0.25), sprig_length, 1.0)
