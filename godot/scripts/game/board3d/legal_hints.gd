extends RefCounted
## Legal-move cues on the board: a sheen over each legal pawn, a small four-point sparkle
## above it, short corner marks on its tile, an intermittent tapping hand, and a count badge
## on crowded tiles (5+ pawns). No destination preview, no follow circle.
## Shapes, sizes and timing are the live LudoPaws3DLegalPulseView + LudoPawsTapHandArt; the
## colours are V2 Meadow colours (owner decision 2026-11-10).

const TapArt = preload("res://scripts/ui/hints/tap_hand_art.gd")
const Geo = preload("res://scripts/game/board3d/board_geometry.gd")
const Style = preload("res://scripts/ui/kit/meadow_style.gd")
const Wood = preload("res://scripts/ui/kit/wood_plaque.gd")

const PULSE_DURATION_MS := 720
const HAND_PERIOD_MS := 3600
const HAND_VISIBLE_MS := 1400

## AccelerateDecelerate 0 -> 1 -> 0 (ValueAnimator REVERSE, infinite) since `start_ms`.
## WCAG relative-luminance contrast ratio between two colours (alpha ignored).
static func contrast(a: Color, b: Color) -> float:
	var la := _luminance(a)
	var lb := _luminance(b)
	return (maxf(la, lb) + 0.05) / (minf(la, lb) + 0.05)

static func _luminance(c: Color) -> float:
	var ch := func(v: float) -> float: return v / 12.92 if v <= 0.03928 else pow((v + 0.055) / 1.055, 2.4)
	return 0.2126 * ch.call(c.r) + 0.7152 * ch.call(c.g) + 0.0722 * ch.call(c.b)

## Mark colour + dark Meadow keyline: every cue reads on light stone AND dark grass/water.
const KEYLINE := Color("#052117") # MeadowStyle.INK_DARK, deepened for 3:1 on mid greens

static func _line(canvas: CanvasItem, a: Vector2, b: Vector2, color: Color, width: float, dp: float) -> void:
	var key := KEYLINE
	key.a = color.a * 0.85
	canvas.draw_line(a, b, key, width + dp * 1.6, true)
	canvas.draw_line(a, b, color, width, true)

static func pulse(now: int, start_ms: int) -> float:
	var t := float(maxi(now - start_ms, 0)) / PULSE_DURATION_MS
	var leg := int(floor(t))
	var f := t - leg
	if leg % 2 == 1:
		f = 1.0 - f
	return (1.0 - cos(f * PI)) * 0.5

static func hand_visible(now: int) -> bool:
	return now % HAND_PERIOD_MS < HAND_VISIBLE_MS

## Arm/road size of the tile around a logical centre (cells inside the cross are longer).
static func _tile(center: Vector2, cell: float) -> Vector2:
	var w := cell * 5.0 / 3.0 if center.x >= cell * 5.0 and center.x <= cell * 10.0 else cell * 5.0 / 6.0
	var h := cell * 5.0 / 3.0 if center.y >= cell * 5.0 and center.y <= cell * 10.0 else cell * 5.0 / 6.0
	return Vector2(w, h)

## legal: [{color, token, position, character_id, offset (logical cells), occupancy}].
## `origin` is the board's top-left on `canvas`; dp = canvas pixels per live dp.
static func draw_legal(canvas: CanvasItem, origin: Vector2, cell: float, turns: int, legal: Array, breath: float, now: int, dp: float) -> void:
	var edge := cell * 15.0
	var screen := func(p: Vector2) -> Vector2: return origin + Geo.rotate_point(p, edge, turns)
	var mark := Style.MINT
	mark.a = clampf((130.0 + breath * 125.0) / 255.0, 0.0, 1.0)
	var sheen := Style.CREAM
	sheen.a = clampf((65.0 + breath * 110.0) / 255.0, 0.0, 1.0)
	var sparkle := Style.MINT_HI
	sparkle.a = mark.a
	var marked := {}
	for pawn in legal:
		var center = Geo.token_center(pawn["color"], pawn["token"], pawn["position"], cell, turns)
		if center == null:
			continue
		var logical: Vector2 = center + pawn["offset"] * cell
		var fit: Dictionary = Geo.road_presentation(center, cell, pawn["position"], pawn["occupancy"], pawn["character_id"], turns)
		var p: Vector2 = screen.call(logical) + Vector2(0, fit["foot_drop"])
		var radius: float = fit["radius"]
		var body_height := radius * Geo.silhouette_height(pawn["character_id"])
		var key := "%d:%d" % [roundi(center.x * 100), roundi(center.y * 100)]
		if pawn["position"] >= 0 and pawn["position"] < Geo.HOME and not marked.has(key):
			marked[key] = true
			var tile := _tile(center, cell)
			for sx in [-1.0, 1.0]:
				for sy in [-1.0, 1.0]:
					var corner: Vector2 = screen.call(center + Vector2(sx * tile.x * 0.41, sy * tile.y * 0.41))
					var along_x: Vector2 = screen.call(center + Vector2(sx * tile.x * 0.24, sy * tile.y * 0.41))
					var along_y: Vector2 = screen.call(center + Vector2(sx * tile.x * 0.41, sy * tile.y * 0.24))
					_line(canvas, corner, along_x, mark, dp * 1.5, dp)
					_line(canvas, corner, along_y, mark, dp * 1.5, dp)
		if hand_visible(now):
			draw_hand(canvas, p + Vector2(radius * 0.72, -body_height * 0.55 + radius * 0.08 * breath), radius, int(130 + breath * 125), dp)
		var rise := radius * 0.08 * breath
		canvas.draw_line(p + Vector2(-radius * 0.24, -radius * 0.42 - rise), p + Vector2(radius * 0.08, -radius * 0.12 - rise),
			sheen, maxf(radius * 0.11, dp), true)
		var sp := p + Vector2(radius * 0.38, -body_height * 0.88 - radius * 0.08 * breath)
		var long_arm := radius * (0.18 + 0.06 * breath)
		var short_arm := radius * (0.11 + 0.035 * breath)
		var width := maxf(radius * 0.075, dp * 0.8)
		_line(canvas, sp - Vector2(0, long_arm), sp + Vector2(0, long_arm), sparkle, width, dp)
		_line(canvas, sp - Vector2(short_arm, 0), sp + Vector2(short_arm, 0), sparkle, width, dp)

## Count badge in the upper corner of every tile holding 5+ pawns (live drawCrowdCounts).
static func draw_crowd_counts(canvas: CanvasItem, origin: Vector2, cell: float, turns: int, players: Array, font: Font) -> void:
	var edge := cell * 15.0
	var groups := {}
	var centers := {}
	for player in players:
		var tokens: Array = player["tokens"]
		for i in tokens.size():
			var position := int(tokens[i])
			if position < 0 or position >= Geo.HOME:
				continue
			var center = Geo.token_center(player["color"], i, position, cell, turns)
			if center == null:
				continue
			var key := "%d:%d" % [roundi(center.x * 100), roundi(center.y * 100)]
			groups[key] = int(groups.get(key, 0)) + 1
			centers[key] = center
	for key in groups:
		if groups[key] < 5:
			continue
		var center: Vector2 = centers[key]
		var tile := _tile(center, cell)
		var screen_size := tile if turns % 2 == 0 else Vector2(tile.y, tile.x)
		var rotated := Geo.rotate_point(center, edge, turns)
		var r := minf(cell * 0.17, screen_size.y * 0.19)
		var at := origin + rotated + Vector2(screen_size.x * 0.5 - r - cell * 0.04, -screen_size.y * 0.5 + r + cell * 0.04)
		var fill := Style.MOSS
		fill.a = 0xF0 / 255.0
		canvas.draw_circle(at, r, fill, true, -1.0, true)
		var size := int(maxf(r * 1.35, 6.0))
		var text := str(groups[key])
		var width := font.get_string_size(text, HORIZONTAL_ALIGNMENT_LEFT, -1, size).x
		canvas.draw_string(font, at + Vector2(-width * 0.5, (font.get_ascent(size) - font.get_descent(size)) * 0.5),
			text, HORIZONTAL_ALIGNMENT_LEFT, -1, size, Style.INK)

# ---------------------------------------------------------------- tap hand

const HAND := [Vector2(0, 0.38), Vector2(-0.16, 0.12), ["q", -0.22, -0.02, -0.08, 0.0], Vector2(0, 0.10), Vector2(0, -0.33),
	["c", 0.01, -0.44, 0.14, -0.44, 0.15, -0.30], Vector2(0.15, -0.02), ["q", 0.21, -0.05, 0.23, 0.015],
	["q", 0.30, -0.015, 0.31, 0.065], ["q", 0.39, 0.04, 0.39, 0.14], Vector2(0.33, 0.38)]
static var _hand_points := PackedVector2Array()

static func hand_outline() -> PackedVector2Array:
	if _hand_points.is_empty():
		var cursor := Vector2.ZERO
		for op in HAND:
			if op is Vector2:
				_hand_points.append(op)
				cursor = op
				continue
			for s in range(1, 9):
				var t := s / 8.0
				var u := 1.0 - t
				var q: Vector2
				if op[0] == "q":
					q = cursor * u * u + Vector2(op[1], op[2]) * 2.0 * u * t + Vector2(op[3], op[4]) * t * t
				else:
					q = cursor * u * u * u + Vector2(op[1], op[2]) * 3.0 * u * u * t + Vector2(op[3], op[4]) * 3.0 * u * t * t + Vector2(op[5], op[6]) * t * t * t
				_hand_points.append(q)
			cursor = _hand_points[_hand_points.size() - 1]
	return _hand_points

## Live LudoPawsTapHandArt placement and timing, drawn with the V2 tap hand art
## (assets/ui/icons/tap_hand.svg): the fingertip sits where the live hand's tip is
## (at + (0.075, -0.44) x radius) and the hand is about live's height (0.82 x radius,
## shown a little larger so the new art reads on every board).
static func draw_hand(canvas: CanvasItem, at: Vector2, radius: float, alpha: int, dp: float) -> void:
	if radius <= 0.0 or alpha <= 0:
		return
	var h := maxf(radius * 0.95, 30.0 * dp)
	var size := Vector2(h * TapArt.aspect(), h)
	var scale := canvas.get_global_transform_with_canvas().get_scale().x * canvas.get_viewport().get_final_transform().get_scale().x
	var tex := TapArt.texture(h * maxf(scale, 1.0))
	if tex == null:
		return
	var tip := at + Vector2(0.075, -0.44) * radius
	var rect := Rect2(tip - TapArt.tip_in(size), size)
	canvas.draw_texture_rect(tex, rect, false, Color(1, 1, 1, clampi(alpha, 0, 255) / 255.0))
