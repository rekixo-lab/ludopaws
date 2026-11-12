@tool
extends Control
## Editor/F6 preview of the 3D characters: every character idling, then the selected
## character in each motion (IDLE, HOP, HOME) and reaction cue. Unported species say "pending".

const Portrait = preload("res://scripts/game/characters/character_portrait.gd")
const Catalog = preload("res://scripts/game/characters/catalog.gd")
const MOTIONS := ["IDLE", "HOP", "HOME", "EXCITED", "HAPPY", "SAFE", "CAPTURE", "CAPTURED", "NERVOUS", "ANGRY", "SAD", "VICTORY", "DEFEAT"]

@export var character_id := "dog":
	set(value):
		character_id = value
		if is_inside_tree():
			_build()
@export_enum("RED", "GREEN", "YELLOW", "BLUE") var team := "BLUE":
	set(value):
		team = value
		if is_inside_tree():
			_build()

func _ready() -> void:
	_build()

func _build() -> void:
	for child in get_children():
		child.queue_free()
	var bg := ColorRect.new()
	bg.color = Color("#1c382d")
	bg.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	add_child(bg)
	var column := VBoxContainer.new()
	column.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	column.add_theme_constant_override("separation", 10)
	for side in ["left", "top"]:
		column.set("offset_" + side, 12)
	for side in ["right", "bottom"]:
		column.set("offset_" + side, -12)
	add_child(column)
	_title(column, "All characters (idle) · %d/14 ported" % Catalog.RENDERED.size())
	var roster := GridContainer.new()
	roster.columns = 7
	column.add_child(roster)
	for id in Catalog.REGISTRY_ORDER:
		_cell(roster, id, "IDLE", Catalog.display_name(id), 116)
	_title(column, "%s · motions and reactions" % Catalog.display_name(character_id))
	var grid := GridContainer.new()
	grid.columns = 5
	column.add_child(grid)
	for motion in MOTIONS:
		_cell(grid, character_id, motion, motion, 160)

func _title(parent: Control, text: String) -> void:
	var label := Label.new()
	label.text = text
	label.add_theme_font_size_override("font_size", 20)
	parent.add_child(label)

func _cell(parent: Control, id: String, motion: String, caption: String, edge: int) -> void:
	var box := VBoxContainer.new()
	parent.add_child(box)
	if Catalog.definition(id) != null:
		var portrait := Portrait.new()
		portrait.character_id = id
		portrait.motion = motion
		portrait.team = team
		portrait.custom_minimum_size = Vector2(edge, edge)
		box.add_child(portrait)
	else:
		var pending := Label.new()
		pending.text = "pending"
		pending.custom_minimum_size = Vector2(edge, edge)
		pending.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
		pending.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
		pending.modulate = Color(1, 1, 1, 0.5)
		box.add_child(pending)
	var label := Label.new()
	label.text = caption
	label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	label.add_theme_font_size_override("font_size", 13)
	box.add_child(label)
