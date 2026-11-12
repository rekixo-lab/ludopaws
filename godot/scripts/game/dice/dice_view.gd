extends Control
## The match dice: the equipped live skin with live roll / settle / attention animation.
## Port of DiceView.kt (rolling, beginSettle, attention, applyFrame) and
## DiceRollAnimationPolicy / DiceAttentionAnimationPolicy. Shows the outcome it is given;
## it never decides one.

const Renderers = preload("res://scripts/game/dice/dice_renderers.gd")
const CE = preload("res://scripts/game/render/canvas_emitter.gd")
const Art = preload("res://scripts/game/board3d/board_art_player.gd")

const SETTLE_MS := 450
const ROLLING_FACES := [2, 5, 3, 6, 4, 1, 5, 2, 6, 3, 1, 4]
const TEAM := {"RED": 0xFFE85468, "GREEN": 0xFF18B568, "YELLOW": 0xFFF3C62D, "BLUE": 0xFF168FDB}

var style_id := "dice_classic":
	set(value):
		style_id = value if Renderers.PALETTES.has(value) else "dice_classic"
		_renderer = Renderers.create(style_id)
		_clear_variants()
		_dirty = true
var team_color = null:   # board colour name of the player who rolls
	set(value):
		if value != team_color:
			team_color = value
			_clear_variants()
			_dirty = true
var face := 1
var rolling := false
var settling := false
var attention := false
var _renderer
var _roll_started := 0
var _roll_start_face := 1
var _roll_ms_at_settle := 0
var _settle_started := 0
var _settle_start_rotation := 18.0
var _target := 1
var _attention_started := 0
var _rotation := 0.0
var _scale := 1.0
var _translate_y := 0.0
var _pulse := 0.0
var _dirty := true
var _content: Control
static var _textures_ready := false

func _init() -> void:
	mouse_filter = Control.MOUSE_FILTER_IGNORE
	_renderer = Renderers.create(style_id)

func _ready() -> void:
	_content = Control.new()
	_content.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(_content)
	_ensure_textures(self)
	resized.connect(func():
		_clear_variants()
		_dirty = true)

## Environment artwork + spatial surface textures (rendered once from the recorded commands).
static func _ensure_textures(host: Node) -> void:
	if _textures_ready:
		return
	_textures_ready = true
	for key in ["dice_forest_dew_3d", "dice_lagoon_pearl_3d", "dice_frost_crystal_3d", "dice_starlight_3d"]:
		Art.textures[key] = load("res://assets/dice/%s.png" % key)
	var golden = JSON.parse_string(FileAccess.get_file_as_string("res://assets/dice/surfaces.json"))
	for key in golden:
		var vp := SubViewport.new()
		vp.size = Vector2i(128, 128)
		vp.transparent_bg = true
		vp.disable_3d = true
		vp.render_target_update_mode = SubViewport.UPDATE_ONCE
		var root := Control.new()
		root.size = Vector2(128, 128)
		vp.add_child(root)
		Art.build(root, golden[key], 1.0)
		host.get_tree().root.add_child.call_deferred(vp)
		Art.textures[key] = vp.get_texture()

func now() -> int:
	return Time.get_ticks_msec()

# ---------------------------------------------------------------- DiceView API

func start_rolling() -> void:
	if rolling:
		return
	attention = false
	rolling = true
	settling = false
	_roll_start_face = face
	_roll_started = now()

## Offline transitions: settle straight onto the committed outcome.
func show_outcome(outcome: int, animate := true) -> void:
	if outcome < 1 or outcome > 6:
		return
	attention = false
	if not animate:
		rolling = false
		settling = false
		_target = outcome
		face = outcome
		_reset_transform()
		_dirty = true
		return
	_begin_settle(outcome)

func set_attention(enabled: bool) -> void:
	var should := enabled and not rolling and not settling
	if should == attention:
		return
	attention = should
	if should:
		_attention_started = now()
	else:
		_reset_transform()
	_dirty = true

func stop_rolling() -> void:
	rolling = false
	settling = false
	attention = false
	_reset_transform()
	_dirty = true

func _begin_settle(outcome: int) -> void:
	var was_rolling := rolling
	var t := now()
	_roll_ms_at_settle = maxi(t - _roll_started, 0) if was_rolling else 0
	if not was_rolling:
		_roll_start_face = face
	rolling = false
	_target = outcome
	face = outcome
	_settle_start_rotation = clampf(_rotation, -20.0, 20.0) if was_rolling and absf(_rotation) > 1.0 else 18.0
	settling = true
	_settle_started = t

# ---------------------------------------------------------------- animation policies

static func rolling_frame(elapsed_ms: int) -> Dictionary:
	var e := maxi(elapsed_ms, 0)
	var cycle := float(e % 420) / 420.0
	var hop := absf(sin(cycle * PI * 2.0))
	var tumble := sin(e / 145.0 * PI * 2.0) * 15.0 + sin(e / 81.0 * PI * 2.0) * 4.0
	var press := clampf(e / 75.0, 0.0, 1.0)
	return {"face": ROLLING_FACES[(e / 52) % ROLLING_FACES.size()], "rotation": tumble,
		"scale": (1.0 - (1.0 - press) * 0.10) * (0.95 + hop * 0.045), "ty": -hop * 0.065, "pulse": 0.45 + hop * 0.55}

static func settle_frame(elapsed_ms: int, outcome: int, start_rotation := 18.0) -> Dictionary:
	var p := clampf(float(elapsed_ms) / SETTLE_MS, 0.0, 1.0)
	var remaining := 1.0 - p
	var bounce := sin(p * PI) * remaining
	var wobble := sin(p * PI * 3.0) * remaining
	return {"face": outcome, "rotation": start_rotation * remaining + wobble * 7.0, "scale": 1.0 + bounce * 0.075,
		"ty": -bounce * 0.055, "pulse": 0.35 + remaining * 0.65, "finished": p >= 1.0}

static func attention_frame(elapsed_ms: int) -> Dictionary:
	var cycle := float(maxi(elapsed_ms, 0) % 900) / 900.0
	var breath := (sin(cycle * PI * 2.0 - PI * 0.5) + 1.0) * 0.5
	return {"face": 1, "rotation": 0.0, "scale": 1.0 + breath * 0.07, "ty": -breath * 0.002, "pulse": 0.20 + breath * 0.675}

func _apply(f: Dictionary, keep_face := false) -> void:
	if not keep_face:
		face = f["face"]
	_rotation = f["rotation"]
	_scale = f["scale"]
	_translate_y = f["ty"]
	_pulse = f["pulse"]

func _reset_transform() -> void:
	_rotation = 0.0
	_scale = 1.0
	_translate_y = 0.0
	_pulse = 0.0

func _process(_delta: float) -> void:
	var t := now()
	var spatial: bool = _renderer.uses_spatial_roll
	if rolling:
		if not spatial:
			_apply(rolling_frame(t - _roll_started))
		_dirty = true
	elif settling:
		var e := t - _settle_started
		if not spatial:
			_apply(settle_frame(e, _target, _settle_start_rotation))
		if e >= SETTLE_MS:
			settling = false
			_reset_transform()
		_dirty = true
	elif attention:
		# The live attention frame carries face 1 but DiceView keeps showing the real face.
		_apply(attention_frame(t - _attention_started), true)
		_dirty = true
	if not _warm.is_empty() and not rolling and not settling:
		_warm_step()
	if _dirty:
		_dirty = false
		var probe_t := Time.get_ticks_usec()
		_redraw(t)
		if rolling:
			preload("res://scripts/game/perf.gd").tap_feedback()
		preload("res://scripts/game/perf.gd").span("dice_redraw", Time.get_ticks_usec() - probe_t)

func _redraw(t: int) -> void:
	if _content == null:
		return
	var edge := minf(size.x, size.y)
	if edge <= 0.0 or _renderer == null:
		_show_variant(null)
		return
	var spatial: bool = _renderer.uses_spatial_roll
	var center := edge * 0.5
	_content.position = (size - Vector2(edge, edge)) * 0.5
	_content.size = Vector2(edge, edge)
	_content.pivot_offset = Vector2(center, center)
	var palette := Renderers.palette(style_id, TEAM.get(team_color))
	if spatial and (rolling or settling):
		# Spatial skins render their own 3D roll: a fresh drawing, at most every
		# SPATIAL_FRAME_MS (30 fps), the board's waiting cadence; the last frame of the settle
		# is always drawn so the outcome face is exact.
		if t - _spatial_drawn_ms < SPATIAL_FRAME_MS and _shown_variant != null:
			_dirty = true
			return
		_spatial_drawn_ms = t
		_content.rotation_degrees = 0.0
		_content.scale = Vector2.ONE
		var live := _variant_root("live", edge, true)
		var c := CE.new()
		var f := Renderers.frame(_roll_start_face if rolling else face, rolling, settling, attention, _pulse,
			false, _roll_start_face, (maxi(t - _roll_started, 0) if rolling else _roll_ms_at_settle),
			float(t - _settle_started) / SETTLE_MS)
		_renderer.draw(c, edge, palette, f)
		Art.build(live, c.commands, 1.0)
		_show_variant(live)
		return
	# Every other look is built once and cached; motion (tumble, press scale, hop) is the
	# content's transform — the same translate/scale/rotate about the centre the live canvas
	# applies — so a rolling dice never rebuilds nodes.
	_content.position += Vector2(0, edge * _translate_y)
	_content.rotation_degrees = _rotation
	_content.scale = Vector2(_scale, _scale)
	var busy := rolling or settling
	var pulse_q := roundi(clampf(_pulse, 0.0, 1.0) * PULSE_STEPS)
	var key := "%d|%s|%s|%d|%d" % [face, busy, attention, pulse_q, roundi(edge)]
	if not _variants.has(key):
		var root := _variant_root(key, edge, false)
		var c := CE.new()
		_renderer.draw(c, edge, palette, Renderers.frame(face, rolling, settling, attention,
			float(pulse_q) / PULSE_STEPS, false, _roll_start_face, 0, 0.0))
		Art.build(root, c.commands, 1.0)
	_show_variant(_variants[key])

## Border-glow levels kept per face; finer steps are not visible on the outline.
const PULSE_STEPS := 10
const SPATIAL_FRAME_MS := 33
var _spatial_drawn_ms := -100000
const MAX_VARIANTS := 96
var _variants := {}       # key -> Control (insertion order = age)
var _shown_variant: Control

func _variant_root(key: String, edge: float, fresh: bool) -> Control:
	if fresh and _variants.has(key):
		var old: Control = _variants[key]
		_variants.erase(key)
		if old == _shown_variant:
			_shown_variant = null
		old.queue_free()
	if _variants.has(key):
		return _variants[key]
	var root := Control.new()
	root.mouse_filter = Control.MOUSE_FILTER_IGNORE
	root.size = Vector2(edge, edge)
	root.visible = false
	_content.add_child(root)
	_variants[key] = root
	while _variants.size() > MAX_VARIANTS:
		var oldest: String = _variants.keys()[0]
		var node: Control = _variants[oldest]
		_variants.erase(oldest)
		if node == _shown_variant:
			_shown_variant = null
		node.queue_free()
	return root

func _show_variant(root) -> void:
	if root == _shown_variant:
		return
	if is_instance_valid(_shown_variant):
		_shown_variant.visible = false
	_shown_variant = root
	if root != null:
		root.visible = true

## Warm-up: build the rolling / resting looks of every face a couple per frame while idle, so
## the first tap never builds nodes (target: press feedback < 100 ms).
var _warm: Array = []

## Match dice warm their rolling looks; static previews (Shop) do not.
var warm_up := false:
	set(value):
		warm_up = value
		_queue_warm()

func _queue_warm() -> void:
	_warm.clear()
	if not warm_up or _renderer == null or _renderer.uses_spatial_roll:
		return
	# rolling (0.45..1.0) and settle (0.35..1.0) pulses; the resting look has pulse 0.
	for f in range(1, 7):
		_warm.append([f, false, false, 0])
	for q in range(roundi(0.35 * PULSE_STEPS), PULSE_STEPS + 1):
		for f in range(1, 7):
			_warm.append([f, true, false, q])

## One look built per frame across every dice (warm-up never stacks into a long frame), and
## none while warm_paused (a list is scrolling).
static var _warm_frame := -1
var warm_paused := false

func _warm_step() -> void:
	var edge := minf(size.x, size.y)
	if edge <= 0.0 or _content == null or warm_paused:
		return
	var frame := Engine.get_process_frames()
	if _warm_frame == frame:
		return
	_warm_frame = frame
	for i in 1:
		if _warm.is_empty():
			return
		var w: Array = _warm.pop_front()
		var key := "%d|%s|%s|%d|%d" % [w[0], w[1], w[2], w[3], roundi(edge)]
		if _variants.has(key):
			continue
		var root := _variant_root(key, edge, false)
		var c := CE.new()
		_renderer.draw(c, edge, Renderers.palette(style_id, TEAM.get(team_color)), Renderers.frame(w[0], w[1], false, w[2],
			float(w[3]) / PULSE_STEPS, false, w[0], 0, 0.0))
		Art.build(root, c.commands, 1.0)
		if root == _shown_variant:
			root.visible = true

## Style, team colour or size changed: every cached look is stale.
func _clear_variants() -> void:
	for node in _variants.values():
		node.queue_free()
	_variants.clear()
	_shown_variant = null
	_queue_warm()
