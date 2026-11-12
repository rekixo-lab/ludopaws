extends Control
## The animated tap hand: one TextureRect + a ripple, only transform / opacity change per
## frame (no node or texture is created while it animates). Loop ~1.1 s: the finger comes
## down onto the target, presses (a little lower and smaller), a soft ripple spreads, it
## lifts. Appears / disappears with a fade and a small scale. Reduced Motion: no movement,
## only a gentle opacity pulse.

const Art = preload("res://scripts/ui/hints/tap_hand_art.gd")
const LOOP_MS := 1100.0
const FADE_MS := 180.0

var reduced_motion := false
var _image: TextureRect
var _ripple: Control
var _tip := Vector2.ZERO        # fingertip in this control's space
var _hand_size := Vector2.ZERO
var _want := false
var _fade := 0.0
var _clock := 0.0
var _press := 0.0
var _ripple_p := -1.0

func _init() -> void:
	mouse_filter = Control.MOUSE_FILTER_IGNORE
	visible = false
	_ripple = Control.new()
	_ripple.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_ripple.draw.connect(_draw_ripple)
	add_child(_ripple)
	_image = TextureRect.new()
	_image.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_image.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	_image.stretch_mode = TextureRect.STRETCH_SCALE
	_image.texture_filter = CanvasItem.TEXTURE_FILTER_LINEAR_WITH_MIPMAPS
	add_child(_image)

## Points the fingertip at `target` (canvas rect) inside `screen`, sized from the target
## (0.6-0.9 x its short side, clamped to 44-64 dp). `px_per_unit`: physical px per canvas unit.
func point_at(target: Rect2, screen: Rect2, dp: float, px_per_unit: float) -> void:
	var h := clampf(minf(target.size.x, target.size.y) * 0.75, 44.0 * dp, 64.0 * dp)
	_hand_size = Vector2(h * Art.aspect(), h)
	var tex := Art.texture(h * px_per_unit)
	if _image.texture != tex:
		_image.texture = tex
	# Small targets (dice, paw): fingertip near the centre, nudged down-right. Wide buttons and
	# tiles: on their lower-right part, so the hand hangs off the side, not over the label.
	var wide := target.size.x > _hand_size.x * 1.6
	var tip := target.get_center() + target.size * (Vector2(0.36, 0.22) if wide else Vector2(0.10, 0.10))
	var mirrored := tip.x + _hand_size.x - Art.tip_in(_hand_size).x > screen.end.x
	# Never upside down: near the bottom edge the hand moves up instead (clamped below).
	var flipped := false
	_image.flip_h = mirrored
	_image.flip_v = flipped
	var local_tip := Art.tip_in(_hand_size, mirrored, flipped)
	var pos := tip - local_tip
	# Never cut by the screen edge.
	pos.x = clampf(pos.x, screen.position.x, screen.end.x - _hand_size.x)
	pos.y = clampf(pos.y, screen.position.y, screen.end.y - _hand_size.y)
	position = pos
	size = _hand_size
	_tip = tip - pos
	_image.size = _hand_size
	_image.pivot_offset = local_tip
	_ripple.size = _hand_size

func hand_rect() -> Rect2:
	return Rect2(position, _hand_size)

func fingertip() -> Vector2:
	return position + _tip

func show_hand() -> void:
	if not _want:
		_want = true
		_clock = 0.0
		visible = true

func hide_hand(immediate := false) -> void:
	_want = false
	if immediate:
		_fade = 0.0
		visible = false

func showing() -> bool:
	return _want

func _process(delta: float) -> void:
	if not visible:
		return
	var ms := delta * 1000.0
	_fade = clampf(_fade + (ms / FADE_MS if _want else -ms / FADE_MS), 0.0, 1.0)
	if _fade <= 0.0 and not _want:
		visible = false
		return
	_clock += ms
	var p := fmod(_clock, LOOP_MS) / LOOP_MS
	var appear := 0.85 + 0.15 * _ease(_fade)
	if reduced_motion:
		_press = 0.0
		_ripple_p = -1.0
		_image.position = Vector2.ZERO
		_image.scale = Vector2.ONE * appear
		modulate.a = _fade * (0.80 + 0.20 * (0.5 + 0.5 * sin(_clock / 1600.0 * TAU)))
		return
	# Approach (0-0.30), press (0.30-0.45), release (0.45-0.62), hover.
	if p < 0.30:
		_press = 0.0
	elif p < 0.45:
		_press = _ease((p - 0.30) / 0.15)
	elif p < 0.62:
		_press = 1.0 - _ease((p - 0.45) / 0.17)
	else:
		_press = 0.0
	var hover := 1.0 - _press
	var lift := Vector2(0.10, 0.08) * _hand_size.y * hover * (0.35 if p >= 0.62 else 1.0 - p / 0.30 * 0.65)
	_image.position = lift * (Vector2(-1, 1) if _image.flip_h else Vector2.ONE) * (Vector2(1, -1) if _image.flip_v else Vector2.ONE)
	_image.scale = Vector2.ONE * appear * (1.0 - 0.07 * _press)
	_ripple_p = (p - 0.40) / 0.45 if p >= 0.40 and p < 0.85 else -1.0
	modulate.a = _fade
	_ripple.queue_redraw()

func _draw_ripple() -> void:
	if _ripple_p < 0.0:
		return
	var r := _hand_size.y * (0.10 + 0.32 * _ripple_p)
	var a := 0.55 * (1.0 - _ripple_p)
	_ripple.draw_arc(_tip, r, 0.0, TAU, 40, Color(1.0, 0.97, 0.88, a), maxf(2.0, _hand_size.y * 0.035), true)
	_ripple.draw_circle(_tip, r * 0.55, Color(0.60, 0.95, 0.78, a * 0.35))

static func _ease(x: float) -> float:
	x = clampf(x, 0.0, 1.0)
	return x * x * (3.0 - 2.0 * x)
