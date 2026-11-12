extends PanelContainer
## Compact player card for the match rail (live LudoPawsPlayerCardView data, V2 Meadow look):
## equipped-animal portrait ringed in the player's colour, name (• BOT), Team line, state line
## (<Animal> • YOUR TURN / CPU TURN / TURN / WAITING / OUT) and progress (h/4 HOME • r MOVING).
## Active: thicker team-coloured border + glow; never replaces the team colour.

const Portrait = preload("res://scripts/game/characters/character_portrait.gd")
const Characters = preload("res://scripts/game/characters/catalog.gd")
const Style = preload("res://scripts/ui/kit/meadow_style.gd")
const TEAM := {"RED": Color("#e85468"), "GREEN": Color("#18b568"), "YELLOW": Color("#f3c62d"), "BLUE": Color("#168fdb")}

var portrait: Control
var name_label: Label
var team_label: Label
## Line 2: the most important fact right now (turn state when active, else progress).
var status_label: Label
## Kept for callers / tests: line 2 is shown through status_label.
var state_label: Label
var progress_label: Label
var _row: HBoxContainer
var _copy: VBoxContainer
var _name_row: HBoxContainer
var _key := ""
var _pulse_started := -100000

func _init(scale := 1.0) -> void:
	mouse_filter = Control.MOUSE_FILTER_IGNORE
	_row = HBoxContainer.new()
	_row.add_theme_constant_override("separation", int(4 * scale))
	_row.alignment = BoxContainer.ALIGNMENT_CENTER
	add_child(_row)
	portrait = Portrait.new()
	portrait.custom_minimum_size = Vector2(40, 40) * scale
	portrait.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	portrait.zoom = 1.15
	# Waiting players show a cached still (live portrait bitmap cache); the active player's
	# portrait is alive and reacts (six -> EXCITED).
	portrait.snapshot = true
	_row.add_child(portrait)
	# Two lines, centred vertically next to the portrait.
	_copy = VBoxContainer.new()
	_copy.add_theme_constant_override("separation", int(1 * scale))
	_copy.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_copy.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	_copy.alignment = BoxContainer.ALIGNMENT_CENTER
	_row.add_child(_copy)
	_name_row = HBoxContainer.new()
	_name_row.add_theme_constant_override("separation", int(4 * scale))
	_name_row.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_copy.add_child(_name_row)
	name_label = _text(12.0 * scale, Style.INK)
	name_label.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	team_label = _text(10.0 * scale, Style.GOLD)
	team_label.clip_text = false
	_name_row.add_child(name_label)
	_name_row.add_child(team_label)
	status_label = _text(9.5 * scale, Style.MINT)
	_copy.add_child(status_label)
	state_label = status_label
	progress_label = status_label

func _text(size: float, color: Color) -> Label:
	var l := Label.new()
	l.add_theme_font_override("font", Style.text_font())
	l.add_theme_font_size_override("font_size", int(round(size)))
	l.add_theme_color_override("font_color", color)
	l.clip_text = true
	l.text_overrun_behavior = TextServer.OVERRUN_TRIM_ELLIPSIS
	l.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
	l.mouse_filter = Control.MOUSE_FILTER_IGNORE
	return l

## Line 2 (V2 compact card): YOUR TURN / CPU TURN / BOT TURN / TURN when active, OUT when
## forfeited, else progress ("ALL HOME", "2/4 HOME", "1 MOVING", "0/4 HOME").
static func status_text(d: Dictionary, active: bool) -> String:
	if d.get("forfeited", false):
		return "OUT"
	if active:
		if d.get("bot", false):
			return "BOT TURN"
		if d.get("computer", false):
			return "CPU TURN"
		return "YOUR TURN" if d.get("local", false) else "TURN"
	var tokens: Array = d["tokens"]
	var home := tokens.filter(func(t): return int(t) == 57).size()
	var yard := tokens.filter(func(t): return int(t) == -1).size()
	var racing := tokens.size() - home - yard
	if home >= tokens.size():
		return "ALL HOME"
	if home > 0:
		return "%d/4 HOME" % home
	if racing > 0:
		return "%d MOVING" % racing
	return "0/4 HOME"

## data: {name, color, character, bot, computer, local, forfeited, tokens, team_id, local_team_id}
func bind(d: Dictionary, active: bool, portrait_on_end: bool) -> void:
	var key := str([d, active, portrait_on_end])
	if key == _key:
		return
	_key = key
	_row.move_child(portrait, 1 if portrait_on_end else 0)
	portrait.snapshot = not active
	if not active:
		portrait.motion = "IDLE"
	var character := Characters.resolve_id(d.get("character", "dog"))
	portrait.character_id = character
	portrait.team = d["color"]
	var animal := Characters.display_name(character)
	# Text faces the board: left-aligned beside a left portrait, right-aligned beside a right one.
	var align := HORIZONTAL_ALIGNMENT_RIGHT if portrait_on_end else HORIZONTAL_ALIGNMENT_LEFT
	name_label.horizontal_alignment = align
	status_label.horizontal_alignment = align
	_name_row.move_child(team_label, 0 if portrait_on_end else 1)
	name_label.text = ("%s • BOT" % d["name"]) if d.get("bot", false) else str(d["name"])
	var team = d.get("team_id")
	team_label.visible = team != null
	if team != null:
		var role := "YOU" if d.get("local", false) else ("PARTNER" if team == d.get("local_team_id") else "")
		team_label.text = ("%s·%s" % [team, role]) if not role.is_empty() else "TEAM %s" % team
		team_label.add_theme_color_override("font_color", Style.GOLD if team == "A" else Color("#6de7ff"))
	var line := status_text(d, active)
	status_label.text = line
	status_label.add_theme_color_override("font_color", Style.GOLD if active else Style.MINT)
	var tokens: Array = d["tokens"]
	var home := tokens.filter(func(t): return int(t) == 57).size()
	var yard := tokens.filter(func(t): return int(t) == -1).size()
	var racing := tokens.size() - home - yard
	var box := StyleBoxFlat.new()
	box.bg_color = Color("#1e3d36") if active else Style.MOSS
	box.set_corner_radius_all(15)
	var team_color: Color = TEAM.get(d["color"], Color("#74817f"))
	box.border_color = team_color
	box.set_border_width_all(3 if active else 2)
	box.shadow_color = Color(team_color, 0.55) if active else Color(0, 0, 0, 0.35)
	box.shadow_size = 9 if active else 3
	# Even inner padding: nothing touches the rounded corners.
	box.set_content_margin_all(6)
	add_theme_stylebox_override("panel", box)
	tooltip_text = "%s, %s, %s, %d home, %d moving, %d in yard%s" % [animal, d["name"], line, home, racing, yard, ", active turn" if active else ""]

## Six: a short excited pulse on the active card (spec §4.5).
func excite() -> void:
	_pulse_started = Time.get_ticks_msec()
	if not portrait.snapshot:
		portrait.motion = "EXCITED"
		portrait.restart()
		_reaction_until = Time.get_ticks_msec() + REACTION_MS

const REACTION_MS := 1400
var _reaction_until := 0

func _process(_delta: float) -> void:
	if _reaction_until > 0 and Time.get_ticks_msec() >= _reaction_until:
		_reaction_until = 0
		portrait.motion = "IDLE"
		portrait.restart()
	var e := Time.get_ticks_msec() - _pulse_started
	pivot_offset = size * 0.5
	scale = Vector2.ONE * (1.0 + 0.06 * sin(PI * e / 420.0)) if e < 420 else Vector2.ONE
