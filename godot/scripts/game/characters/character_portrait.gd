@tool
extends Control
## Live 3D portrait of one character, using the same geometry/materials as the board
## (port of LudoPaws3DSceneRenderer.drawPortrait). Can loop a motion or a reaction cue.

const G = preload("res://scripts/game/board3d/paw_geometry.gd")
const D = preload("res://scripts/game/board3d/paw_draw.gd")
const P = preload("res://scripts/game/characters/shared/pose.gd")
const Life = preload("res://scripts/game/characters/shared/life_motion.gd")
const Catalog = preload("res://scripts/game/characters/catalog.gd")
const Reaction = preload("res://scripts/game/characters/shared/reaction_motion.gd")
const Living = preload("res://scripts/game/characters/shared/living_behavior.gd")

@export var character_id := "dog":
	set(value):
		if value != character_id:
			character_id = value
			_resnapshot()
		queue_redraw()
@export_enum("RED", "GREEN", "YELLOW", "BLUE") var team := "BLUE":
	set(value):
		if value != team:
			team = value
			_resnapshot()
## Static portrait (live LudoPawsCharacterPortraitBitmapCache, used by player cards): drawn
## once per character / team / size, shared, then shown as a plain texture with no per-frame
## 3D work.
@export var snapshot := false:
	set(value):
		if value == snapshot:
			return
		snapshot = value
		if _viewport == null:
			return
		if snapshot:
			_resnapshot()
		else:
			# Back to a live, animated portrait.
			_snapshot_key = ""
			_view.texture = _viewport.get_texture()
			_viewport.render_target_update_mode = SubViewport.UPDATE_WHEN_VISIBLE if _live else SubViewport.UPDATE_DISABLED
			set_process(_live)
## Live (animated) portraits can be paused off screen or while a list scrolls: the last frame
## stays visible and no 3D work runs.
var _live := true
## 0 = every frame; otherwise the most frames per second this portrait renders (Shop grids
## of many portraits use 30 so idle animation stays smooth and cheap).
var max_fps := 0
var _last_render_ms := -100000

func set_live(on: bool) -> void:
	if on == _live:
		return
	_live = on
	if snapshot or _viewport == null:
		return
	_viewport.render_target_update_mode = (SubViewport.UPDATE_ONCE if max_fps > 0 else SubViewport.UPDATE_WHEN_VISIBLE) if on else SubViewport.UPDATE_DISABLED
	set_process(on)
## IDLE, HOP, HOME or a reaction cue (EXCITED, CAPTURE, VICTORY, ...).
@export var motion := "IDLE"
@export var animate := true
@export_range(0.35, 1.5, 0.01) var zoom := 1.0
@export_range(0.5, 0.75, 0.01) var vertical_anchor := 0.62
@export var yaw_degrees := 0.0

var _viewport: SubViewport
var _view: TextureRect
var _pool: D.Pool
var _life := Life.new()
var _started := 0

func _ready() -> void:
	for child in get_children():
		if child.has_meta("generated"):
			child.queue_free()
	_viewport = SubViewport.new()
	_viewport.own_world_3d = true
	_viewport.transparent_bg = true
	_viewport.msaa_3d = Viewport.MSAA_4X
	if snapshot:
		_viewport.render_target_update_mode = SubViewport.UPDATE_DISABLED
	_viewport.set_meta("generated", true)
	add_child(_viewport)
	var camera := Camera3D.new()
	camera.projection = Camera3D.PROJECTION_ORTHOGONAL
	camera.keep_aspect = Camera3D.KEEP_WIDTH
	camera.size = 1.0
	camera.position = Vector3(0.5, -0.5, 50)
	camera.far = 100.0
	_viewport.add_child(camera)
	var content := Node3D.new()
	content.transform = Transform3D(Basis.from_scale(Vector3(1, -1, 1)), Vector3.ZERO)
	_viewport.add_child(content)
	var bright := D.Layer.new()
	bright.brightness = 1.10
	_pool = D.Pool.new(content, bright)
	_view = TextureRect.new()
	_view.texture = _viewport.get_texture()
	_view.stretch_mode = TextureRect.STRETCH_SCALE
	_view.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	_view.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_view.set_meta("generated", true)
	add_child(_view)
	_started = Time.get_ticks_msec()
	resized.connect(_fit)
	_fit()
	# The screen scale is known only once the control sits in the laid-out tree.
	_fit.call_deferred()

func _fit() -> void:
	var logical := minf(size.x, size.y)
	var edge := maxi(1, roundi(logical))
	# Render at the device's physical pixels, not the logical size stretched by the screen scale.
	var scale := 1.0
	if is_inside_tree():
		scale = clampf(get_global_transform_with_canvas().get_scale().x * get_viewport().get_final_transform().get_scale().x, 1.0, 4.0)
	_viewport.size = Vector2i.ONE * mini(maxi(1, roundi(logical * scale)), 512)
	_view.size = Vector2(edge, edge)
	_view.position = (size - Vector2(edge, edge)) * 0.5
	_resnapshot()

func restart() -> void:
	_started = Time.get_ticks_msec()

func _process(_delta: float) -> void:
	if _pool == null:
		return
	const Probe = preload("res://scripts/game/perf.gd")
	if Probe.flag("no_portraits"):
		_viewport.render_target_update_mode = SubViewport.UPDATE_DISABLED
		return
	var probe_t := Time.get_ticks_usec()
	_portrait_frame()
	Probe.span("portrait", Time.get_ticks_usec() - probe_t)

static var _snapshots := {}  # "character|team|px|zoom" -> Texture2D
var _snapshot_key := ""

func _snapshot_id() -> String:
	return "%s|%s|%d|%.2f|%.2f" % [character_id, team, _viewport.size.x if _viewport else 0, zoom, vertical_anchor]

func _resnapshot() -> void:
	_snapshot_key = ""
	if snapshot and is_inside_tree():
		set_process(true)

## Snapshot mode: show the shared bake, or render one still frame and bake it.
func _snapshot_frame() -> void:
	var key := _snapshot_id()
	if key == _snapshot_key:
		set_process(false)
		return
	if _snapshots.has(key):
		_snapshot_key = key
		_view.texture = _snapshots[key]
		_viewport.render_target_update_mode = SubViewport.UPDATE_DISABLED
		set_process(false)
		return
	var def = Catalog.definition(character_id)
	_pool.begin()
	if def != null:
		_render(def, _started)
	_pool.end()
	_view.texture = _viewport.get_texture()
	_viewport.render_target_update_mode = SubViewport.UPDATE_ONCE
	_snapshot_key = key
	set_process(false)
	await RenderingServer.frame_post_draw
	if not is_instance_valid(self) or _snapshot_key != key:
		return
	var image := _viewport.get_texture().get_image()
	if image != null and not image.is_empty():
		_snapshots[key] = ImageTexture.create_from_image(image)
		_view.texture = _snapshots[key]

func _portrait_frame() -> void:
	if snapshot:
		_snapshot_frame()
		return
	if max_fps > 0:
		var now := Time.get_ticks_msec()
		if now - _last_render_ms < 1000 / max_fps:
			_viewport.render_target_update_mode = SubViewport.UPDATE_DISABLED
			return
		_last_render_ms = now
		_viewport.render_target_update_mode = SubViewport.UPDATE_ONCE
	var def = Catalog.definition(character_id)
	_pool.begin()
	if def != null:
		_render(def, Time.get_ticks_msec())
	_pool.end()

func _render(def, now: int) -> void:
	var elapsed := (now - _started) if animate else 0
	var kind := P.IDLE
	var progress := 0.0
	var reaction_target := P.reaction()
	if motion == P.HOP:
		kind = P.HOP
		progress = float(elapsed % 680) / 680.0
	elif motion == P.HOME:
		kind = P.HOME
		progress = float(elapsed % def.HOME_DURATION_MS) / def.HOME_DURATION_MS
	elif motion != P.IDLE:
		var duration := Reaction.duration_ms(def.ID, motion)
		var cycle := duration + 500
		var t := elapsed % cycle
		if t < duration:
			reaction_target = Reaction.sample(def.ID, motion, float(t) / duration)
			reaction_target = Living.accent_reaction(reaction_target, def.ID, motion, float(t) / duration, 0)
	if kind == P.IDLE:
		var duration := Living.idle_duration_ms(def.IDLE_DURATION_MS, 0)
		progress = float(elapsed % duration) / duration
	var reaction := _life.update(now, 7, kind == P.IDLE and motion == P.IDLE, 0.0, 0.0, reaction_target)
	var sampled: Dictionary = def.Renderer.sample(kind, progress, P.ISOLATED)
	var base: Dictionary = _life.idle_pose(sampled, def.ID) if kind == P.IDLE else sampled
	var pose := P.copy(base, {
		"liftY": base["liftY"] + reaction["liftY"],
		"bodyYawDegrees": yaw_degrees + base["bodyYawDegrees"] + reaction["bodyYawDegrees"],
		"headTiltDegrees": base["headTiltDegrees"] + reaction["headTiltDegrees"],
		"primaryAppendageDegrees": _life.follow_primary(base["primaryAppendageDegrees"] + reaction["primaryAppendageDegrees"]),
		"secondaryAppendageDegrees": _life.follow_secondary(base["secondaryAppendageDegrees"] + reaction["secondaryAppendageDegrees"]),
		"tertiaryAppendageDegrees": _life.follow_tertiary(base["tertiaryAppendageDegrees"] + reaction["tertiaryAppendageDegrees"]),
		"bodyPitchDegrees": base["bodyPitchDegrees"] + reaction["bodyPitchDegrees"],
		"neckPitchDegrees": base["neckPitchDegrees"] + reaction["neckPitchDegrees"],
		"jawOpen": clampf(base["jawOpen"] + reaction["jawOpen"], 0.0, 1.0),
	})
	_pool.life = _life
	_pool.surface = "MATTE"
	var scale: float = 0.30 * clampf(zoom, 0.6, 1.5) * reaction["scale"]
	var m := G.translate(Transform3D.IDENTITY, 0.5, vertical_anchor, 0)
	m = G.scale(m, scale, -scale, scale)
	m = G.translate(m, 0, pose["liftY"], 0)
	m = G.rotate(m, pose["bodyYawDegrees"], 0, 1, 0)
	var stretch: float = base["bodyScaleY"]
	if stretch != 1.0:
		var width := 1.0 / sqrt(stretch)
		var pivot: float = def.GROUND_PIVOT_Y
		m = G.translate(m, 0, pivot, 0)
		m = G.scale(m, width, stretch, width)
		m = G.translate(m, 0, -pivot, 0)
	def.Renderer.draw(_pool, m, pose, team)
