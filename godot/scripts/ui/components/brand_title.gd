@tool
extends Control

const DISPLAY_FONT := preload("res://assets/ui/brand/display_serif.tres")
const LETTER_EDGE := Color("#3e2211")
# The material turns this face color into the vertical gold gradient.
const GOLD_FACE := Color("#ffd27a")
const LETTER_SHADOW := Color(0.10, 0.045, 0.025, 0.75)

@export var upper_text := "LUDO":
	set(value):
		upper_text = value
		queue_redraw()

@export var lower_text := "PAWS":
	set(value):
		lower_text = value
		queue_redraw()

func _draw() -> void:
	_draw_word(upper_text, 53.0, 44, Color("#fff1d1"), Color("#caa46b"))
	_draw_word(lower_text, 91.0, 44, GOLD_FACE, Color("#a95f25"))

func _draw_word(word: String, baseline: float, font_size: int, face: Color, bevel: Color) -> void:
	if word.is_empty():
		return
	var spacing := -1.65
	var advances: Array[float] = []
	var word_width := spacing * (word.length() - 1)
	for letter in word:
		var advance := DISPLAY_FONT.get_string_size(letter, HORIZONTAL_ALIGNMENT_LEFT, -1, font_size).x
		advances.append(advance)
		word_width += advance

	var cursor := (size.x - word_width) * 0.5
	for index in range(advances.size()):
		var letter := word[index]
		var advance := advances[index]
		var center := cursor + advance * 0.5
		var arc_position := (center - size.x * 0.5) / (word_width * 0.5)
		draw_set_transform(Vector2(center, baseline + 6.0 * arc_position * arc_position), arc_position * 0.16)
		var origin := Vector2(-advance * 0.5, 0.0)
		draw_string_outline(DISPLAY_FONT, origin + Vector2(0.9, 2.8), letter, HORIZONTAL_ALIGNMENT_LEFT, -1, font_size, 4, LETTER_SHADOW)
		draw_string_outline(DISPLAY_FONT, origin, letter, HORIZONTAL_ALIGNMENT_LEFT, -1, font_size, 2, LETTER_EDGE)
		draw_string(DISPLAY_FONT, origin + Vector2(0.0, 1.9), letter, HORIZONTAL_ALIGNMENT_LEFT, -1, font_size, bevel)
		draw_string(DISPLAY_FONT, origin, letter, HORIZONTAL_ALIGNMENT_LEFT, -1, font_size, face)
		cursor += advance + spacing
	draw_set_transform(Vector2.ZERO)
