extends RefCounted
## Board FX shapes (paw trails, capture ghost, reaction bursts/rings/bolts/drops, home stars,
## confetti). Port of LudoPawsFxPainter.kt. Emits draw commands in board pixel space so the
## exact live output can be tested; `render()` paints them on a CanvasItem.
## Like the live painter, ONE instance keeps its paints: stroke widths set by one effect
## carry over to the next one that does not set them (e.g. the reaction ring width).
##
## Commands: ["circle", x, y, r, style, argb, width] · ["line", x1, y1, x2, y2, style, argb, width]
## ["oval", l, t, r, b, style, argb, width] · ["rect", l, t, r, b, style, argb, width]
## ["path", [x0, y0, ...], closed, style, argb, width] · ["save"] · ["rotate", deg, px, py] · ["restore"]

const BOARD_SIZE := 15.0
const PAW_TRAIL_COUNT := 4
const WHITE := 0xFFFFFFFF
const CONFETTI_COLORS := [0xFFFFC107, 0xFFF44336, 0xFF2196F3, 0xFF4CAF50, 0xFF9C27B0]

var density := 1.0
var ring_width := 3.2
var line_width := 2.3
var commands: Array = []

func _init(display_density := 1.0) -> void:
	density = display_density
	ring_width = 3.2 * density
	line_width = 2.3 * density

static func rgb(r: int, g: int, b: int) -> int:
	return 0xFF000000 | (r << 16) | (g << 8) | b

static func with_alpha(color: int, alpha: int) -> int:
	return (clampi(alpha, 0, 255) << 24) | (color & 0xFFFFFF)

## Kotlin computes fades in float32; matching it keeps alpha rounding identical at .5 edges.
static func _f32(v: float) -> float:
	var a := PackedFloat32Array([v])
	return a[0]

static func _fade(progress: float) -> float:
	return clampf(_f32(1.0 - _f32(progress)), 0.0, 1.0)

static func _round(v: float) -> int:
	return int(floor(_f32(v) + 0.5))

static func _ease_out_cubic(v: float) -> float:
	var t := 1.0 - clampf(v, 0.0, 1.0)
	return 1.0 - t * t * t

static func _lerp(a: float, b: float, p: float) -> float:
	return a + (b - a) * clampf(p, 0.0, 1.0)

func _circle(x: float, y: float, r: float, style: String, color: int, width := 0.0) -> void:
	commands.append(["circle", x, y, r, style, color, width])

func draw_capture_return(from: Vector2, to: Vector2, color: int, cell: float, progress: float) -> void:
	var eased := _ease_out_cubic(progress)
	var x := _lerp(from.x, to.x, eased)
	var y := _lerp(from.y, to.y, eased)
	var fade := _fade(progress)
	line_width = cell * 0.12
	commands.append(["line", from.x, from.y, x, y, "STROKE", with_alpha(color, _round(120.0 * fade)), line_width])
	_circle(x, y, cell * (0.25 + 0.05 * sin(progress * PI)), "FILL", with_alpha(color, _round(220.0 * fade)))
	_paw_mark(x, y, cell * 0.14, WHITE, _round(210.0 * fade))
	if progress > 0.72:
		draw_destination_flash(to, color, cell)

func draw_paw_trail(from: Vector2, to: Vector2, color: int, cell: float, progress: float, emphasis: float) -> void:
	var fade := _fade(progress)
	if fade <= 0.0:
		return
	for index in PAW_TRAIL_COUNT:
		var fraction := ((index + 1.0) / (PAW_TRAIL_COUNT + 1.0)) * maxf(progress, 0.18)
		_paw_mark(_lerp(from.x, to.x, fraction), _lerp(from.y, to.y, fraction), cell * 0.085 * emphasis,
			color, _round(180.0 * fade * emphasis))

func draw_reaction(center: Vector2, cue: String, cell: float, progress: float, particle_count: int, allow_confetti: bool) -> void:
	var color := reaction_color(cue)
	var fade := _fade(progress)
	var pulse := cell * (0.30 + 0.58 * _ease_out_cubic(progress))
	_circle(center.x, center.y, pulse * 0.86, "FILL", with_alpha(color, _round(75.0 * fade)))
	_circle(center.x, center.y, pulse, "STROKE", with_alpha(color, _round(225.0 * fade)), ring_width)
	match cue:
		"EXCITED", "HAPPY", "CAPTURE":
			_burst(center, color, cell, particle_count, progress)
		"CAPTURED":
			_impact(center, color, cell, progress, particle_count)
		"SAFE":
			_safe_shield(center, color, cell, progress)
		"HOME":
			_burst(center, color, cell, particle_count, progress)
			draw_home_stars(cell, progress)
		"ANGRY":
			_angry_bolts(center, cell, progress)
		"NERVOUS":
			_nervous_orbit(center, color, cell, progress)
		"SAD", "DEFEAT":
			_sad_drops(center, cell, progress, particle_count)
		"VICTORY":
			_burst(center, color, cell, particle_count / 2, progress)
			if allow_confetti:
				_confetti(cell, progress, particle_count)
		"IDLE":
			_idle_breath(center, color, cell, progress)

func draw_home_stars(cell: float, progress: float) -> void:
	for index in 10:
		var angle := index * 2.399963 + progress
		var radius := cell * (1.0 + (index % 3) * 0.7)
		var cx := cell * 7.5 + cos(angle) * radius
		var cy := cell * 7.5 + sin(angle) * radius - cell * progress * 0.7
		_star(cx, cy, cell * 0.10, rgb(255, 214, 64), _round(_f32(1.0 - _f32(progress)) * 230.0))

func draw_destination_flash(center: Vector2, color: int, cell: float) -> void:
	_circle(center.x, center.y, cell * 0.40, "FILL", with_alpha(color, 90))
	ring_width = cell * 0.07
	_circle(center.x, center.y, cell * 0.34, "STROKE", with_alpha(color, 220), ring_width)

func _burst(center: Vector2, color: int, cell: float, particle_count: int, progress: float) -> void:
	var count := clampi(particle_count, 2, 18)
	var fade := _fade(progress)
	var paint := with_alpha(color, _round(230.0 * fade))
	for index in count:
		var angle := TAU * index / count + progress * 0.55
		var radius := cell * (0.36 + progress * 0.72)
		_circle(center.x + cos(angle) * radius, center.y + sin(angle) * radius, cell * 0.055, "FILL", paint)

func _impact(center: Vector2, color: int, cell: float, progress: float, particle_count: int) -> void:
	var count := clampi(particle_count, 4, 12)
	line_width = cell * 0.08
	var paint := with_alpha(color, _round(_f32(1.0 - _f32(progress)) * 230.0))
	for index in count:
		var angle := TAU * index / count
		var inner := cell * (0.24 + 0.16 * progress)
		var outer := cell * (0.52 + 0.28 * progress)
		var d := Vector2(cos(angle), sin(angle))
		commands.append(["line", center.x + d.x * inner, center.y + d.y * inner, center.x + d.x * outer,
			center.y + d.y * outer, "STROKE", paint, line_width])

func _safe_shield(center: Vector2, color: int, cell: float, progress: float) -> void:
	var fade := _fade(progress)
	for index in 3:
		ring_width = cell * 0.07
		_circle(center.x, center.y, cell * (0.34 + index * 0.14 + progress * 0.15), "STROKE",
			with_alpha(color, _round(_f32(200.0 * fade) / (index + 1))), ring_width)

func _angry_bolts(center: Vector2, cell: float, progress: float) -> void:
	var paint := with_alpha(rgb(255, 92, 62), _round(_f32(1.0 - _f32(progress)) * 245.0))
	line_width = cell * 0.08
	for index in 4:
		var side := -1.0 if index % 2 == 0 else 1.0
		var y_offset := (index / 2) * cell * 0.20
		commands.append(["path", [
			center.x + side * cell * 0.38, center.y - cell * 0.42 + y_offset,
			center.x + side * cell * 0.58, center.y - cell * 0.14 + y_offset,
			center.x + side * cell * 0.43, center.y + cell * 0.02 + y_offset,
			center.x + side * cell * 0.68, center.y + cell * 0.30 + y_offset], false, "STROKE", paint, line_width])

func _nervous_orbit(center: Vector2, color: int, cell: float, progress: float) -> void:
	for index in 5:
		var angle := TAU * index / 5 + progress * 8.0
		_circle(center.x + cos(angle) * cell * 0.48, center.y + sin(angle) * cell * 0.48, cell * 0.045, "FILL",
			with_alpha(color, _round(_f32(1.0 - _f32(progress)) * 220.0)))

func _sad_drops(center: Vector2, cell: float, progress: float, particle_count: int) -> void:
	var count := clampi(particle_count, 2, 8)
	var paint := with_alpha(rgb(70, 150, 255), _round(_f32(1.0 - _f32(progress)) * 210.0))
	for index in count:
		var offset := (index - count / 2.0) * cell * 0.12
		var y := center.y + cell * (0.30 + progress * 0.55)
		commands.append(["oval", center.x + offset - cell * 0.035, y - cell * 0.07, center.x + offset + cell * 0.035,
			y + cell * 0.07, "FILL", paint, 0.0])

func _confetti(cell: float, progress: float, particle_count: int) -> void:
	var board_px := cell * BOARD_SIZE
	for index in clampi(particle_count, 8, 40):
		var x_seed := ((index * 37) % 97) / 97.0
		var y_seed := ((index * 53) % 89) / 89.0
		var x := board_px * x_seed + sin(progress * 8.0 + index) * cell * 0.25
		var y := board_px * fmod(y_seed * 0.35 + progress * 1.05, 1.0)
		var paint := with_alpha(CONFETTI_COLORS[index % CONFETTI_COLORS.size()], _round(_f32(1.0 - _f32(_f32(progress) * 0.55)) * 235.0))
		commands.append(["save"])
		commands.append(["rotate", progress * 360.0 + index * 19.0, x, y])
		commands.append(["rect", x - cell * 0.055, y - cell * 0.10, x + cell * 0.055, y + cell * 0.10, "FILL", paint, 0.0])
		commands.append(["restore"])

func _idle_breath(center: Vector2, color: int, cell: float, progress: float) -> void:
	var wave := sin(progress * PI)
	ring_width = cell * 0.045
	_circle(center.x, center.y, cell * (0.34 + wave * 0.06), "STROKE", with_alpha(color, _round(_f32(1.0 - _f32(progress)) * 120.0)), ring_width)

func _paw_mark(x: float, y: float, radius: float, color: int, alpha: int) -> void:
	var paint := with_alpha(color, alpha)
	commands.append(["oval", x - radius * 0.70, y - radius * 0.25, x + radius * 0.70, y + radius * 0.80, "FILL", paint, 0.0])
	for index in 3:
		_circle(x + (index - 1) * radius * 0.58, y - radius * 0.62, radius * 0.34, "FILL", paint)

func _star(cx: float, cy: float, radius: float, color: int, alpha: int) -> void:
	var points: Array = []
	for index in 10:
		var angle := -PI / 2.0 + index * PI / 5.0
		var r := radius if index % 2 == 0 else radius * 0.45
		points.append(cx + cos(angle) * r)
		points.append(cy + sin(angle) * r)
	commands.append(["path", points, true, "FILL", with_alpha(color, alpha), 0.0])

static func reaction_color(cue: String) -> int:
	match cue:
		"CAPTURED", "SAD", "DEFEAT": return rgb(232, 72, 85)
		"ANGRY": return rgb(255, 112, 67)
		"SAFE": return rgb(72, 202, 228)
		"HOME", "VICTORY": return rgb(255, 193, 7)
		"NERVOUS": return rgb(171, 71, 188)
	return rgb(255, 235, 59)

## LudoPawsGameFxOverlayView.playerColor.
static func player_color(color: String) -> int:
	match color:
		"RED": return rgb(229, 57, 53)
		"GREEN": return rgb(46, 160, 67)
		"YELLOW": return rgb(255, 193, 7)
		"BLUE": return rgb(30, 136, 229)
	return WHITE

static func to_color(argb: int) -> Color:
	return Color8((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, (argb >> 24) & 255)

## Paints `list` on `canvas` with `base` mapping board pixels to canvas space (rotation included).
static func render(canvas: CanvasItem, list: Array, base: Transform2D) -> void:
	var stack: Array = []
	var xf := base
	canvas.draw_set_transform_matrix(xf)
	for c in list:
		match c[0]:
			"save":
				stack.append(xf)
			"restore":
				xf = stack.pop_back()
				canvas.draw_set_transform_matrix(xf)
			"rotate":
				var pivot := Vector2(c[2], c[3])
				xf = xf * Transform2D(0.0, pivot) * Transform2D(deg_to_rad(c[1]), Vector2.ZERO) * Transform2D(0.0, -pivot)
				canvas.draw_set_transform_matrix(xf)
			"circle":
				if c[4] == "FILL":
					canvas.draw_circle(Vector2(c[1], c[2]), c[3], to_color(c[5]), true, -1.0, true)
				else:
					canvas.draw_arc(Vector2(c[1], c[2]), c[3], 0.0, TAU, 40, to_color(c[5]), c[6], true)
			"line":
				canvas.draw_line(Vector2(c[1], c[2]), Vector2(c[3], c[4]), to_color(c[6]), c[7], true)
				# Round caps (linePaint.strokeCap = ROUND).
				canvas.draw_circle(Vector2(c[1], c[2]), c[7] * 0.5, to_color(c[6]), true, -1.0, true)
				canvas.draw_circle(Vector2(c[3], c[4]), c[7] * 0.5, to_color(c[6]), true, -1.0, true)
			"oval":
				var points := PackedVector2Array()
				var center := Vector2((c[1] + c[3]) * 0.5, (c[2] + c[4]) * 0.5)
				var half := Vector2((c[3] - c[1]) * 0.5, (c[4] - c[2]) * 0.5)
				for k in 20:
					var a := k * TAU / 20.0
					points.append(center + Vector2(cos(a) * half.x, sin(a) * half.y))
				canvas.draw_colored_polygon(points, to_color(c[6]))
			"rect":
				canvas.draw_rect(Rect2(c[1], c[2], c[3] - c[1], c[4] - c[2]), to_color(c[6]))
			"path":
				var pts := PackedVector2Array()
				var flat: Array = c[1]
				for k in range(0, flat.size(), 2):
					pts.append(Vector2(flat[k], flat[k + 1]))
				if c[3] == "FILL":
					canvas.draw_colored_polygon(pts, to_color(c[4]))
				else:
					canvas.draw_polyline(pts, to_color(c[4]), c[5], true)
	canvas.draw_set_transform_matrix(Transform2D.IDENTITY)
