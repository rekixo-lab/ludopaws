extends CanvasLayer
## Guidance (autoload): the one place that shows the tap hand. Screens *offer* targets; when
## the user has been idle long enough, the hand points at the most important available one.
## Any tap / scroll / drag / key hides it and restarts the idle clock. See
## docs/TAP_HINT_MAP.md and tap_hint_policy.gd. The hand only shows; it never acts.
##
## offer(owner, key, target, idle_ms, priority, essential):
##   target: Callable returning a Control (its rect; hidden / disabled = unavailable), a
##   Dictionary {rect: Rect2 (canvas), within: Control} or null when not applicable now.
##   The offer goes away with its owner node or withdraw(key).

const Policy = preload("res://scripts/ui/hints/tap_hint_policy.gd")
const TapHand = preload("res://scripts/ui/hints/tap_hand.gd")
const EVAL_MS := 100.0

var policy := Policy.new()
var hand: TapHand
## Callable -> {play_hints: bool, reduced_motion: bool} (the page registers it).
var settings: Callable
## Callable -> Control or null: an open dialog; targets outside it are not shown.
var modal: Callable
var clock_ms := 0.0
var last_input_ms := 0.0
var enabled := true
var _offers := {}
var _shown = null      # the offer currently pointed at
var _shown_rect := Rect2()
var _next_eval := 0.0
var _last_event_id := 0

func _ready() -> void:
	layer = 90
	hand = TapHand.new()
	add_child(hand)
	var root := get_tree().root
	if not root.window_input.is_connected(_on_input):
		root.window_input.connect(_on_input.bind(true))

func offer(owner: Node, key: String, target: Callable, idle_ms: int, priority: int, essential := false) -> void:
	_offers[key] = {"key": key, "target": target, "idle_ms": idle_ms, "priority": priority,
		"essential": essential, "owner": weakref(owner)}

func withdraw(key: String) -> void:
	_offers.erase(key)
	if _shown != null and _shown["key"] == key:
		_hide()

func has_offer(key: String) -> bool:
	return _offers.has(key)

func showing_key() -> String:
	return _shown["key"] if _shown != null and hand.showing() else ""

func _input(event: InputEvent) -> void:
	_on_input(event, false)

## Any deliberate input: hide, note whether the user hit the pointed target, restart idle.
func _on_input(event: InputEvent, from_window: bool) -> void:
	if event.get_instance_id() == _last_event_id:
		return
	var press: bool = (event is InputEventScreenTouch and event.pressed) \
		or (event is InputEventMouseButton and event.pressed) \
		or event is InputEventScreenDrag \
		or (event is InputEventMouseMotion and event.button_mask != 0) \
		or (event is InputEventKey and event.pressed)
	if not press:
		return
	_last_event_id = event.get_instance_id()
	if _shown != null and hand.showing() and (event is InputEventScreenTouch or event is InputEventMouseButton):
		var pos: Vector2 = event.position
		if from_window:
			pos = get_tree().root.get_final_transform().affine_inverse() * pos
		if _shown_rect.grow(4.0).has_point(pos):
			policy.acted(_shown["key"])
		else:
			policy.ignored(_shown["key"])
	last_input_ms = clock_ms
	_hide()

func _hide() -> void:
	_shown = null
	hand.hide_hand()

func _setting(name: String, fallback: bool) -> bool:
	if settings.is_valid():
		var s: Dictionary = settings.call()
		return bool(s.get(name, fallback))
	return fallback

func _process(delta: float) -> void:
	clock_ms += delta * 1000.0
	if clock_ms < _next_eval:
		if _shown != null and hand.showing():
			_place(_shown_rect)
		return
	_next_eval = clock_ms + EVAL_MS
	evaluate()

## Re-checks every offer and shows / moves / hides the hand (also called by tests).
func evaluate() -> void:
	hand.reduced_motion = _setting("reduced_motion", false)
	if not enabled:
		_hide()
		return
	var screen := get_viewport().get_visible_rect()
	var open_dialog = modal.call() if modal.is_valid() else null
	var available: Array = []
	var rects := {}
	for key in _offers.keys():
		var o: Dictionary = _offers[key]
		if o["owner"].get_ref() == null:
			_offers.erase(key)
			continue
		var r = _resolve(o, open_dialog, screen)
		if r != null:
			available.append(o)
			rects[key] = r
	var best = policy.pick(available, _setting("play_hints", true), int(clock_ms - last_input_ms))
	if best == null:
		if _shown != null:
			_hide()
		return
	if _shown == null or _shown["key"] != best["key"]:
		_shown = best
		policy.shown(best["key"])
		hand.hide_hand(true)
		_place(rects[best["key"]])
		hand.show_hand()
	_shown_rect = rects[best["key"]]
	_place(_shown_rect)

func _place(rect: Rect2) -> void:
	var screen := get_viewport().get_visible_rect()
	var dp := screen.size.x / 392.0
	var px := get_viewport().get_final_transform().get_scale().x
	hand.point_at(rect, screen, dp, px)

## The target's canvas rect, or null when it is not available right now.
func _resolve(o: Dictionary, open_dialog, screen: Rect2) -> Variant:
	var t = o["target"].call() if o["target"].is_valid() else null
	var rect := Rect2()
	var within: Control = null
	if t is Control:
		if not is_instance_valid(t) or not t.is_visible_in_tree():
			return null
		if t is BaseButton and t.disabled:
			return null
		rect = t.get_global_rect()
		within = t
	elif t is Dictionary:
		rect = t.get("rect", Rect2())
		within = t.get("within")
	else:
		return null
	if rect.size.x <= 0.0 or rect.size.y <= 0.0 or not screen.intersects(rect):
		return null
	# A dialog is open: only its own controls can be hinted.
	if is_instance_valid(open_dialog) and (within == null or not open_dialog.is_ancestor_of(within)):
		return null
	return rect
