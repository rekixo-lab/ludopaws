extends PanelContainer
## Pass & Play turn banner (V2, replaces the default READY modal): a small Meadow pill over
## the top of the board — "<Name>'s turn · <Colour> paws • your turn" — in the next player's
## team colour. Never blocks input (dice and paws stay tappable), fades out by itself after
## ~1.2 s and on any tap. Reduced Motion: fade only (no slide). Presentation only.

const Style = preload("res://scripts/ui/kit/meadow_style.gd")
const Card = preload("res://scripts/ui/match/player_card.gd")
const HOLD_MS := 1200
const FADE_MS := 250.0

var seat := -1
var color := ""
var label: Label
var _age := 0.0
var _closing := false
var _reduced := false

## next: {seat, name, color}; at_top: canvas y of the board's top edge; width: board width.
func setup(next: Dictionary, display_name: String, colour_line: String, reduced_motion: bool) -> void:
	seat = int(next["seat"])
	color = str(next["color"])
	_reduced = reduced_motion
	mouse_filter = Control.MOUSE_FILTER_IGNORE
	var team: Color = Card.TEAM.get(color, Style.GOLD)
	var box := StyleBoxFlat.new()
	box.bg_color = Color(Style.MOSS, 0.94)
	box.border_color = team
	box.set_border_width_all(3)
	box.set_corner_radius_all(18)
	box.shadow_color = Color(team, 0.45)
	box.shadow_size = 8
	box.content_margin_left = 16
	box.content_margin_right = 16
	box.content_margin_top = 7
	box.content_margin_bottom = 7
	add_theme_stylebox_override("panel", box)
	label = Label.new()
	label.text = "%s's turn  ·  %s" % [display_name, colour_line]
	label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	label.add_theme_font_override("font", Style.text_font())
	label.add_theme_font_size_override("font_size", 16)
	label.add_theme_color_override("font_color", Style.INK)
	label.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(label)
	modulate.a = 0.0

func _process(delta: float) -> void:
	_age += delta * 1000.0
	if not _closing and _age >= HOLD_MS:
		_closing = true
		_age = HOLD_MS
	var appear := clampf(_age / 160.0, 0.0, 1.0)
	var out := clampf((_age - HOLD_MS) / FADE_MS, 0.0, 1.0) if _closing else 0.0
	modulate.a = appear * (1.0 - out)
	if not _reduced:
		position.y = _rest_y - 10.0 * (1.0 - appear)
	if _closing and out >= 1.0:
		queue_free()

var _rest_y := 0.0
func place(at: Vector2) -> void:
	_rest_y = at.y
	position = at

## Any tap dismisses it (the tap itself still reaches the game underneath).
func _input(event: InputEvent) -> void:
	if not _closing and ((event is InputEventScreenTouch or event is InputEventMouseButton) and event.pressed):
		_closing = true
		_age = maxf(_age, HOLD_MS)

func showing() -> bool:
	return is_inside_tree() and not is_queued_for_deletion()
