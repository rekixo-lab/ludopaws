@tool
extends Control

const FONT := preload("res://assets/ui/brand/ui_sans_bold.tres")
const LABEL_SIZE := 16
const MIN_LABEL_SIZE := 10
const MAX_WIDTH := 152.0
const RESERVED_WIDTH := 73.0

@export var icon: Texture2D:
	set(value):
		icon = value
		if is_node_ready():
			$Icon.texture = icon

@export_range(90, 152) var base_width := 103:
	set(value):
		base_width = value
		if is_node_ready():
			_refresh_balance()

@export var amount: int = 0:
	set(value):
		amount = maxi(value, 0)
		if is_node_ready():
			_refresh_balance()

func _ready() -> void:
	$Icon.texture = icon
	_refresh_balance()

func _refresh_balance() -> void:
	var label: Label = $Balance
	var display := _format_balance(amount)
	var text_width := FONT.get_string_size(display, HORIZONTAL_ALIGNMENT_LEFT, -1, LABEL_SIZE).x
	var bar_width := clampf(ceilf(text_width) + RESERVED_WIDTH, base_width, MAX_WIDTH)
	var font_size := LABEL_SIZE
	while font_size > MIN_LABEL_SIZE and text_width > bar_width - RESERVED_WIDTH:
		font_size -= 1
		text_width = FONT.get_string_size(display, HORIZONTAL_ALIGNMENT_LEFT, -1, font_size).x
	label.text = display
	label.tooltip_text = display
	label.add_theme_font_size_override("font_size", font_size)
	custom_minimum_size = Vector2(bar_width, 31.0)

func _format_balance(value: int) -> String:
	var digits := str(value)
	var split_at := digits.length() - 3
	if split_at <= 0:
		return digits
	var grouped := digits.substr(split_at)
	while split_at > 0:
		var start := maxi(split_at - 2, 0)
		grouped = digits.substr(start, split_at - start) + "," + grouped
		split_at = start
	return grouped
