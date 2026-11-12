extends HBoxContainer
## Slim message row under the bottom rail (replaces the old status box): a Meadow pill with
## the current lesson / coach hint / game event, plus the Demo's Skip. Fixed height, so the
## board never jumps when a message comes or goes. Events fade out by themselves.

const UI = preload("res://scripts/ui/preview/meadow_controls.gd")
const Style = preload("res://scripts/ui/kit/meadow_style.gd")
const Messages = preload("res://scripts/game/match/match_messages.gd")

const HEIGHT := 40.0
var pill: PanelContainer
var label: Label

func _init() -> void:
	custom_minimum_size.y = HEIGHT
	# Side inset like the rails (the board above runs edge to edge).
	var lead := Control.new()
	lead.custom_minimum_size.x = 4
	add_child(lead)
	alignment = BoxContainer.ALIGNMENT_CENTER
	add_theme_constant_override("separation", 8)
	mouse_filter = Control.MOUSE_FILTER_IGNORE
	pill = PanelContainer.new()
	pill.mouse_filter = Control.MOUSE_FILTER_IGNORE
	pill.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	pill.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	var box := StyleBoxFlat.new()
	box.bg_color = Color(Style.MOSS, 0.92)
	box.border_color = Color(Style.GOLD, 0.75)
	box.set_border_width_all(1)
	box.set_corner_radius_all(15)
	box.content_margin_left = 14
	box.content_margin_right = 14
	box.content_margin_top = 5
	box.content_margin_bottom = 5
	pill.add_theme_stylebox_override("panel", box)
	add_child(pill)
	label = Label.new()
	label.add_theme_font_override("font", Style.text_font())
	label.add_theme_font_size_override("font_size", 13)
	label.add_theme_color_override("font_color", Style.INK)
	label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	label.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	label.max_lines_visible = 2
	label.custom_minimum_size.x = 120
	label.mouse_filter = Control.MOUSE_FILTER_IGNORE
	pill.add_child(label)
	var trail := Control.new()
	trail.custom_minimum_size.x = 4
	add_child(trail)

## shown: Messages.pick result. remaining_ms: how long an event stays (fades at the end).
func show_message(shown: Dictionary, remaining_ms := 0) -> void:
	label.text = shown["text"]
	pill.visible = not shown["text"].is_empty()
	pill.modulate.a = 1.0
	if shown["kind"] == "event" and remaining_ms > 0:
		var tween := pill.create_tween()
		tween.tween_interval(maxf(remaining_ms - 300, 0) / 1000.0)
		tween.tween_property(pill, "modulate:a", 0.0, 0.3)
	elif shown["kind"] == "event":
		pill.visible = false

## The pill accepts taps (first-match coach: tap hides play hints).
func on_tap(callback: Callable) -> void:
	pill.mouse_filter = Control.MOUSE_FILTER_STOP
	pill.gui_input.connect(func(event: InputEvent):
		if (event is InputEventMouseButton or event is InputEventScreenTouch) and event.pressed:
			callback.call())

func add_skip(callback: Callable) -> void:
	var skip := UI.button(self, "Skip", callback, false, "", 34)
	skip.size_flags_horizontal = Control.SIZE_SHRINK_END
	skip.custom_minimum_size.x = 92
	move_child(skip, get_child_count() - 2)
