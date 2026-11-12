class_name KineticScroll
extends ScrollContainer
## Mobile-style vertical scrolling for list areas (V2 kit). The list follows the finger 1:1,
## keeps its momentum after release and eases to a stop; edges clamp without a jolt. A drag
## past DEADZONE cancels the press on whatever card or button it started on, so a light
## drag never opens a card while a clean tap always does. Mouse wheel scrolls smoothly.
## The scroll bar stays hidden (V2 style).

const DEADZONE := 10.0          # px of movement before a press becomes a drag
const FRICTION := 4.2           # 1/s: velocity decay while coasting
const MIN_SPEED := 18.0         # px/s: below this the list stops
const MAX_SPEED := 6000.0
const WHEEL_STEP := 110.0       # px per wheel notch
const WHEEL_EASE := 14.0        # 1/s: wheel target easing

var _pressed := false
var _dragging := false
var _press_pos := Vector2.ZERO
var _press_scroll := 0.0
var _position := 0.0            # float scroll offset (scroll_vertical is int)
var _velocity := 0.0
var _samples: Array = []        # [[time_s, y], ...] for the release velocity
var _wheel_target := -1.0
var _touch_index := -1
var _eat_emulated := false
## Horizontal carousels (Paw picker) scroll along x with the same feel.
var horizontal := false:
	set(value):
		horizontal = value
		horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_SHOW_NEVER if value else ScrollContainer.SCROLL_MODE_DISABLED
		vertical_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED if value else ScrollContainer.SCROLL_MODE_SHOW_NEVER

func _axis(v: Vector2) -> float:
	return v.x if horizontal else v.y

func _get_offset() -> int:
	return scroll_horizontal if horizontal else scroll_vertical

func _set_offset(value: int) -> void:
	if horizontal:
		scroll_horizontal = value
	else:
		scroll_vertical = value

func _init() -> void:
	horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	vertical_scroll_mode = ScrollContainer.SCROLL_MODE_SHOW_NEVER
	follow_focus = false
	# Built-in drag scrolling is replaced by this script (it fought with child buttons).
	scroll_deadzone = 100000
	size_flags_vertical = Control.SIZE_EXPAND_FILL
	size_flags_horizontal = Control.SIZE_EXPAND_FILL
	set_process(false)

func max_scroll() -> float:
	var content := get_child(0) as Control if get_child_count() > 0 else null
	if content == null:
		return 0.0
	return maxf(_axis(content.get_combined_minimum_size()) - _axis(size), 0.0)

## True while the finger drags or the list coasts / eases (animated previews pause).
func is_moving() -> bool:
	return _dragging or is_processing()

## Jump without animation (restoring a remembered position).
func set_position_now(value: float) -> void:
	_velocity = 0.0
	_wheel_target = -1.0
	_apply(value)

func _apply(value: float) -> void:
	_position = clampf(value, 0.0, max_scroll())
	_set_offset(roundi(_position))

## Input positions arrive in root canvas units; the list scrolls in its own (scaled) units.
func _hit(pos: Vector2) -> bool:
	return get_global_rect().has_point(pos)

func _unit() -> float:
	return maxf(absf(_axis(get_global_transform().get_scale())), 0.0001)

func _input(event: InputEvent) -> void:
	if not is_visible_in_tree():
		return
	# On touch screens Godot also emulates mouse events; use only one source.
	var touch := event is InputEventScreenTouch or event is InputEventScreenDrag
	var mouse := event is InputEventMouseButton or event is InputEventMouseMotion
	if mouse and event.device == InputEvent.DEVICE_ID_EMULATION:
		# Touch also produces emulated mouse events, which buttons act on. Once a touch has
		# become a drag, swallow them until the emulated release, so the card under the
		# finger at the end of a drag is never clicked.
		if _eat_emulated:
			get_viewport().set_input_as_handled()
			if event is InputEventMouseButton and not event.pressed:
				_eat_emulated = false
				# Release the pressed control off-screen: it lets go without clicking.
				var away: InputEventMouseButton = event.duplicate()
				away.position = Vector2(-10000, -10000)
				away.global_position = away.position
				get_viewport().push_input.call_deferred(away, true)
		return
	if event is InputEventMouseButton and event.pressed and event.button_index in [MOUSE_BUTTON_WHEEL_UP, MOUSE_BUTTON_WHEEL_DOWN]:
		if _hit(event.position):
			var base := _wheel_target if _wheel_target >= 0.0 else _position
			var step := WHEEL_STEP * maxf(event.factor, 1.0)
			_wheel_target = clampf(base + (step if event.button_index == MOUSE_BUTTON_WHEEL_DOWN else -step), 0.0, max_scroll())
			_velocity = 0.0
			set_process(true)
			get_viewport().set_input_as_handled()
		return
	var is_press: bool = (event is InputEventScreenTouch) or (event is InputEventMouseButton and event.button_index == MOUSE_BUTTON_LEFT)
	if is_press:
		if event.pressed:
			if _pressed or not _hit(event.position):
				return
			_pressed = true
			_dragging = false
			_eat_emulated = false
			_touch_index = event.index if touch else -1
			_press_pos = event.position
			_position = float(_get_offset())
			_press_scroll = _position
			_velocity = 0.0
			_wheel_target = -1.0
			_samples = [[Time.get_ticks_usec() / 1e6, _axis(event.position)]]
			set_process(false)
		elif _pressed and (not touch or event.index == _touch_index):
			_pressed = false
			if _dragging:
				_dragging = false
				_velocity = _release_velocity()
				set_process(absf(_velocity) > MIN_SPEED)
				# The press became a drag: the release must not click anything.
				get_viewport().set_input_as_handled()
		return
	var is_move: bool = (event is InputEventScreenDrag and event.index == _touch_index) or (event is InputEventMouseMotion and _touch_index == -1)
	if not (_pressed and is_move):
		return
	var dy: float = _axis(event.position - _press_pos)
	if not _dragging:
		if absf(dy) < DEADZONE:
			return
		_dragging = true
		_eat_emulated = touch
		_cancel_press()
	_samples.append([Time.get_ticks_usec() / 1e6, _axis(event.position)])
	if _samples.size() > 6:
		_samples.pop_front()
	_apply(_press_scroll - dy / _unit())
	get_viewport().set_input_as_handled()

## Finger velocity over the last ~100 ms, in scroll px/s (positive = content moves up).
func _release_velocity() -> float:
	if _samples.size() < 2:
		return 0.0
	var last: Array = _samples[_samples.size() - 1]
	var first: Array = _samples[0]
	for s in _samples:
		if last[0] - s[0] <= 0.1:
			first = s
			break
	var dt: float = last[0] - first[0]
	if dt <= 0.0 or Time.get_ticks_usec() / 1e6 - last[0] > 0.12:
		return 0.0
	return clampf(-(last[1] - first[1]) / dt / _unit(), -MAX_SPEED, MAX_SPEED)

## Moves the pointer focus off the pressed control so it never fires on release.
func _cancel_press() -> void:
	var focus := get_viewport().gui_get_focus_owner()
	if focus != null and is_ancestor_of(focus):
		focus.release_focus()
	var away := InputEventMouseMotion.new()
	away.position = Vector2(-10000, -10000)
	away.global_position = away.position
	away.button_mask = MOUSE_BUTTON_MASK_LEFT
	get_viewport().push_input(away, true)

func _process(delta: float) -> void:
	if _pressed:
		set_process(false)
		return
	if _wheel_target >= 0.0:
		var next := lerpf(_position, _wheel_target, 1.0 - exp(-WHEEL_EASE * delta))
		if absf(next - _wheel_target) < 0.5:
			next = _wheel_target
			_wheel_target = -1.0
			set_process(false)
		_apply(next)
		return
	_apply(_position + _velocity * delta)
	_velocity *= exp(-FRICTION * delta)
	if absf(_velocity) < MIN_SPEED or _position <= 0.0 or _position >= max_scroll():
		_velocity = 0.0
		set_process(false)
