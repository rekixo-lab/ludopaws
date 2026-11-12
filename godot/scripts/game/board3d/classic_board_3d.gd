@tool
extends Control
## Live classic board for Godot: baked 2D artwork + transparent orthographic 3D layer
## (road relief, centre houses, doors, finish plaques) + pawn/touch overlay.
## Same public API as scripts/game/classic_board.gd so the match page can swap it in.
##
## Layer order mirrors LudoPawsReactiveBoardView: board chrome (2D) -> 3D scene
## (roads, houses, pawns; depth cleared between them) -> overlays.

signal pawn_selected(index: int)

const Rules = preload("res://scripts/game/local_match.gd")
const G = preload("res://scripts/game/board3d/paw_geometry.gd")
const D = preload("res://scripts/game/board3d/paw_draw.gd")
const Geo = preload("res://scripts/game/board3d/board_geometry.gd")
const Road = preload("res://scripts/game/board3d/classic_road_scene.gd")
const Houses = preload("res://scripts/game/board3d/center_houses.gd")
const Art = preload("res://scripts/game/board3d/classic_board_art.gd")
const Palette = preload("res://scripts/game/board3d/board_palette.gd")

## World units: the board is 15 units wide in the live pixel space (cell = 1).
const BOARD := 15.0
## Extra transparent room above the board so tall animals on the top row are not clipped.
const TOP_OVERFLOW_CELLS := 1.6
## Depth separation per layer; with an orthographic camera this equals a depth clear.
const LAYER_DEPTH := 10.0
## Step / capture timing follow Settings > Game speed (LudoPawsGameplayPacingPolicy).
const Pacing = preload("res://scripts/game/gameplay_pacing.gd")
const LANDING_SETTLE_MS := 90
const PawnLayer = preload("res://scripts/game/characters/pawn_layer.gd")
const Catalog = preload("res://scripts/game/characters/catalog.gd")
const Pose = preload("res://scripts/game/characters/shared/pose.gd")
const ReactionMotion = preload("res://scripts/game/characters/shared/reaction_motion.gd")
const Living = preload("res://scripts/game/characters/shared/living_behavior.gd")
const Reactions = preload("res://scripts/game/reactions/reaction_engine.gd")
const IdlePolicy = preload("res://scripts/game/reactions/idle_reaction_policy.gd")
const Seq = preload("res://scripts/game/reactions/seq.gd")
const CaptureReturn = preload("res://scripts/game/board3d/capture_return_placement.gd")
const Cadence = preload("res://scripts/game/board3d/render_cadence.gd")
const MotionPolicy = preload("res://scripts/game/board3d/pawn_motion_policy.gd")
const FxOverlay = preload("res://scripts/game/board3d/fx_overlay.gd")
const FxPainter = preload("res://scripts/game/board3d/fx_painter.gd")
const PawPrints = preload("res://scripts/game/board3d/yard_paw_prints.gd")
const MoveSounds = preload("res://scripts/game/audio/movement_sound_policy.gd")
const LegalHints = preload("res://scripts/game/board3d/legal_hints.gd")
const BoardArt = preload("res://scripts/game/board3d/board_art_player.gd")
## Live dp-based FX strokes (3.2 dp ring) scaled to the board: a phone board is ~360 dp wide.
const FX_BOARD_DP := 360.0
## The live pawn layer is drawn through a 1.10 RGB scale (PAWN_BRIGHTNESS_SCALE).
const PAWN_BRIGHTNESS := 1.10

var match_state: Rules:
	set(value):
		if value != match_state:
			_shown.clear()
			_motion.clear()
			_active_reactions.clear()
			_fx.stop()
			_last_forward = null
			_snapshot = null
			_snapshot_revision = -1
		match_state = value
## Skips step/capture animations (Settings > Accessibility > Reduced Motion).
var reduced_motion := false
## Equipped live board (board_classic + the 7 recorded live boards). Visual theme only:
## route, safe cells and hit-testing never change.
var board_id := "board_classic":
	set(value):
		var next := value if value == "board_classic" or BoardArt.has_board(value) else "board_classic"
		if next == board_id:
			return
		board_id = next
		if _roads != null:
			_roads.mesh = _road_mesh_for_board()
		_art_key = ""
		_layout_dirty = true
## GameSpeed name (SLOW / NORMAL / FAST) for step travel and capture return durations.
var game_speed := Pacing.NORMAL:
	set(value):
		game_speed = Pacing.speed_or_default(value)
		_fx.speed = game_speed
var animals: Array[int] = []
var input_enabled := true
var rotation_turns := 0:
	set(value):
		rotation_turns = posmod(value, 4)
		_layout_dirty = true
		queue_redraw()

## Baked board artwork by "board_id|px": the 2D art is drawn once per board and physical size
## and then shown as a plain texture, so re-entering the tree never replays it (that replay
## cost ~1 s per turn change on a Samsung A71).
static var _art_cache := {}
## Upper bound for the board's render targets (physical px per side).
const MAX_TARGET_PX := 1440
## Bump when any board artwork changes so on-disk bakes are redrawn.
const ART_BAKE_VERSION := 1
const ART_BAKE_DIR := "user://board_bakes"
static var _houses: Houses
static var _road_mesh: ArrayMesh
static var _stone_road_mesh: ArrayMesh
static var _house_mesh: ArrayMesh
var _shown := {}   # "playerId:token" -> position currently drawn
var _motion := {}  # "playerId:token" -> {kind, path, from, start, delay, duration}
## Pawn-local body language (LudoPaws3DActiveReaction): "playerId:token" ->
## {cue, start, duration, priority, variant, moment, observer}
var _active_reactions := {}
var _snapshot = null           # last bound MatchSnapshot (match_snapshot.gd)
var _snapshot_revision := -1
var _meaningful_key = null     # LudoPawsReactiveBoardView idle clock
var _last_change_ms := 0
var _last_idle_key = null

var _art_viewport: SubViewport
var _art: Control
var _art_view: TextureRect
var _art_key := ""
var _scene_viewport: SubViewport
var _scene_view: TextureRect
var _camera: Camera3D
var _content: Node3D
var _roads: MeshInstance3D
var _houses_static: MeshInstance3D
var _house_pool: D.Pool
var _overlay: Control
var _pawn_layer: PawnLayer
var _pawns: Array = []
var _targets := {}
var _layout_dirty := true
var _last_edge := -1.0
var _pacer := Cadence.Pacer.new()
var _fx := FxOverlay.new()
var _chrome: Control         # LudoPawsBoardChromeView top layer: yard paw prints, below the pawns
var _chrome_key := ""
var _legal_pulse_start := -1
var _audio_motion_key := ""      # LudoPaws3DSceneView movement audio clock
var _audio_next_hop := 1
var _audio_next_step := 1
var _capture_audio_keys := {}
var _last_forward = null  # {to, started_ms, duration_ms} of the latest step move (cadence tail)

func _ready() -> void:
	clip_contents = false
	mouse_filter = Control.MOUSE_FILTER_STOP
	if _houses == null:
		_houses = Houses.new()
	_build_art()
	_build_scene()
	_overlay = Control.new()
	_overlay.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_overlay.draw.connect(_draw_overlay)
	add_child(_overlay)
	resized.connect(func(): _layout_dirty = true)
	set_process(true)

func _edge() -> float:
	return minf(size.x, size.y)

func _board_origin() -> Vector2:
	return (size - Vector2.ONE * _edge()) * 0.5

# ---------------------------------------------------------------- construction

func _build_art() -> void:
	_art_viewport = SubViewport.new()
	_art_viewport.disable_3d = true
	_art_viewport.transparent_bg = false
	_art_viewport.render_target_update_mode = SubViewport.UPDATE_DISABLED
	add_child(_art_viewport)
	_art_view = TextureRect.new()
	_art_view.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_art_view.texture = _art_viewport.get_texture()
	_art_view.stretch_mode = TextureRect.STRETCH_SCALE
	_art_view.expand_mode = TextureRect.EXPAND_IGNORE_SIZE # texture is physical px, the rect is logical
	add_child(_art_view)
	_chrome = Control.new()
	_chrome.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_chrome.texture_repeat = CanvasItem.TEXTURE_REPEAT_ENABLED # paw-print grain tile
	_chrome.draw.connect(_draw_chrome)
	add_child(_chrome)

## Classic: the ported ClassicBoardRenderer + jungle habitat. Others: the live renderer's
## recorded drawing (BoardArtPlayer). Road relief: classic only; stone road slabs for boards
## that keep the overlay (stoneRoadOverlayEnabled).
func _install_art() -> void:
	if _art != null:
		_art.queue_free()
	if board_id == "board_classic":
		_art = Art.new()
	else:
		var player := BoardArt.new()
		player.board_id = board_id
		_art = player
	_art_viewport.add_child(_art)

func _road_mesh_for_board() -> ArrayMesh:
	if board_id == "board_classic":
		if _road_mesh == null:
			_road_mesh = D.bake(Road.artwork())
		return _road_mesh
	if BoardArt.art()[board_id].get("stoneRoads", false):
		if _stone_road_mesh == null:
			_stone_road_mesh = D.bake(_houses.stone_road_artwork())
		return _stone_road_mesh
	return null

func _build_scene() -> void:
	_scene_viewport = SubViewport.new()
	_scene_viewport.own_world_3d = true
	_scene_viewport.transparent_bg = true
	_scene_viewport.msaa_3d = Viewport.MSAA_4X
	# Rendered only on the frames the display pacer selects (see _process).
	_scene_viewport.render_target_update_mode = SubViewport.UPDATE_DISABLED
	add_child(_scene_viewport)
	_camera = Camera3D.new()
	_camera.projection = Camera3D.PROJECTION_ORTHOGONAL
	_camera.keep_aspect = Camera3D.KEEP_WIDTH
	_camera.size = BOARD
	_camera.near = 0.05
	_camera.far = 200.0
	_scene_viewport.add_child(_camera)
	# Live pixel space is +Y down; flip once here so every ported number stays as-is.
	_content = Node3D.new()
	_content.transform = Transform3D(Basis.from_scale(Vector3(1, -1, 1)), Vector3.ZERO)
	_scene_viewport.add_child(_content)
	var road_layer := _layer(0)
	_roads = MeshInstance3D.new()
	_roads.mesh = _road_mesh_for_board()
	road_layer.add_child(_roads)
	var house_layer := _layer(1)
	var clip := D.Layer.new()
	# glScissor of the live renderer: decorative volume stays inside the centre square.
	clip.clip_rect = Vector4(BOARD / 3.0, BOARD / 3.0, BOARD * 2.0 / 3.0, BOARD * 2.0 / 3.0)
	_houses_static = MeshInstance3D.new()
	if _house_mesh == null:
		_house_mesh = D.bake(_houses.static_artwork(), clip)
	_houses_static.mesh = _house_mesh
	house_layer.add_child(_houses_static)
	_house_pool = D.Pool.new(house_layer, clip)
	var pawn_layer := _layer(2)
	var bright := D.Layer.new()
	bright.brightness = PAWN_BRIGHTNESS
	_pawn_layer = PawnLayer.new(D.Pool.new(pawn_layer, bright))
	_scene_view = TextureRect.new()
	_scene_view.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_scene_view.texture = _scene_viewport.get_texture()
	_scene_view.stretch_mode = TextureRect.STRETCH_SCALE
	_scene_view.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	add_child(_scene_view)

## Physical pixels per logical pixel for this control (canvas scale x window stretch).
func physical_scale() -> float:
	if not is_inside_tree():
		return 1.0
	var s := get_global_transform_with_canvas().get_scale().x * get_viewport().get_final_transform().get_scale().x
	return clampf(s, 1.0, 4.0)

## Show the cached bake for `key`, or draw the art once into the viewport and bake it.
func _show_art(key: String, px: int) -> void:
	if not _art_cache.has(key) and FileAccess.file_exists(_bake_path(key)):
		var stored := Image.load_from_file(_bake_path(key))
		if stored != null and not stored.is_empty() and stored.get_width() == px:
			_art_cache[key] = ImageTexture.create_from_image(stored)
	if _art_cache.has(key):
		_art_view.texture = _art_cache[key]
		_drop_art()
		return
	if _art == null:
		_install_art()
	_art.size = Vector2(px, px)
	if _art is BoardArt:
		_art.edge = px
	_art_viewport.size = Vector2i(px, px)
	_art_viewport.render_target_update_mode = SubViewport.UPDATE_ONCE
	_art.queue_redraw()
	_art_view.texture = _art_viewport.get_texture()
	_bake_art(key)

func _bake_art(key: String) -> void:
	await RenderingServer.frame_post_draw
	if not is_instance_valid(self) or _art_key != key or not is_inside_tree():
		return
	var image := _art_viewport.get_texture().get_image()
	if image == null or image.is_empty():
		return # headless / dummy renderer: keep the live viewport texture
	var texture := ImageTexture.create_from_image(image)
	_art_cache[key] = texture
	if not OS.has_feature("editor"):
		DirAccess.make_dir_recursive_absolute(ART_BAKE_DIR)
		image.save_png(_bake_path(key))
	_art_view.texture = texture
	_drop_art()

static func _bake_path(key: String) -> String:
	return "%s/v%d_%s_%s.png" % [ART_BAKE_DIR, ART_BAKE_VERSION, ProjectSettings.get_setting("application/config/version", "0"), key.replace("|", "_")]

func _drop_art() -> void:
	_art_viewport.render_target_update_mode = SubViewport.UPDATE_DISABLED
	if _art != null:
		_art_viewport.remove_child(_art)
		_art.queue_free()
		_art = null

func _layer(index: int) -> Node3D:
	var layer := Node3D.new()
	layer.position = Vector3(0, 0, index * LAYER_DEPTH)
	_content.add_child(layer)
	return layer

func _apply_layout() -> void:
	var edge := _edge()
	if edge <= 0.0:
		return
	var origin := _board_origin()
	# Render targets at the device's physical pixels (not the ~400 px logical size stretched
	# ~2.5x on a phone), capped by MAX_TARGET_PX.
	var px := mini(maxi(1, roundi(edge * physical_scale())), MAX_TARGET_PX)
	var key := "%s|%d" % [board_id, px]
	if key != _art_key:
		_art_key = key
		_show_art(key, px)
	_art_view.position = origin
	_art_view.size = Vector2(edge, edge)
	_art_view.pivot_offset = Vector2(edge, edge) * 0.5
	_art_view.rotation = rotation_turns * PI / 2.0
	var overflow := edge / BOARD * TOP_OVERFLOW_CELLS
	var scene_px := px
	const Probe = preload("res://scripts/game/perf.gd")
	if Probe.flag("scene_px"):
		scene_px = mini(px, int(Probe.get_flags()["scene_px"]))
	_scene_viewport.size = Vector2i(scene_px, roundi((edge + overflow) * scene_px / edge))
	_scene_view.position = origin - Vector2(0, overflow)
	_scene_view.size = Vector2(edge, edge + overflow)
	var overflow_units := TOP_OVERFLOW_CELLS
	# Godot Y = -pixel Y. Visible pixel rows: -overflow .. BOARD.
	_camera.position = Vector3(BOARD * 0.5, -(BOARD - overflow_units) * 0.5, 100.0)
	_roads.transform = Road.transform(BOARD, rotation_turns) if board_id == "board_classic" \
		else Houses.stone_road_transform(BOARD, rotation_turns)
	_houses_static.transform = Houses.static_transform(BOARD, rotation_turns)
	_overlay.position = Vector2.ZERO
	_overlay.size = size
	_layout_dirty = false

# ---------------------------------------------------------------- per frame

func _process(_delta: float) -> void:
	if preload("res://scripts/game/perf.gd").flag("board_msaa_off") and _scene_viewport.msaa_3d != Viewport.MSAA_DISABLED:
		_scene_viewport.msaa_3d = Viewport.MSAA_DISABLED
	if preload("res://scripts/game/perf.gd").flag("no_board"):
		_scene_viewport.render_target_update_mode = SubViewport.UPDATE_DISABLED
		return
	var probe_t := Time.get_ticks_usec()
	_board_frame()
	preload("res://scripts/game/perf.gd").span("board_frame", Time.get_ticks_usec() - probe_t)

func _board_frame() -> void:
	const Probe = preload("res://scripts/game/perf.gd")
	var pt := Time.get_ticks_usec()
	var forced := _layout_dirty
	if _layout_dirty:
		_apply_layout()
	var now := Time.get_ticks_msec()
	_sync_motion(now)
	_bind_reactions(now)
	_emit_movement_audio(now)
	if not is_visible_in_tree():
		_pacer.reset()
		return
	if forced:
		_pacer.reset()
	var scene := scene_state()
	Probe.span("bf_sync_scene", Time.get_ticks_usec() - pt)
	pt = Time.get_ticks_usec()
	# LudoPaws3DSceneView.onDisplayFrame: ~60 fps while animating, ~30 fps while waiting.
	var hz := DisplayServer.screen_get_refresh_rate()
	var refresh_ns := int(1000000000.0 / (hz if hz > 0.0 else 60.0))
	var delay := Cadence.frame_delay_ms(_cadence_scene(scene), now, Houses.PLAQUE_START_MS, Houses.ENTRY_DURATION_MS)
	Probe.span("bf_cadence", Time.get_ticks_usec() - pt)
	if not _pacer.should_render(Time.get_ticks_usec() * 1000, refresh_ns, delay):
		# The FX overlay animates at display rate like the live overlay view.
		if _fx.animating(now):
			_overlay.queue_redraw()
		return
	_scene_viewport.render_target_update_mode = SubViewport.UPDATE_ONCE
	pt = Time.get_ticks_usec()
	_refresh_chrome(scene)
	_house_pool.begin()
	_houses.draw_dynamic(_house_pool, BOARD, rotation_turns, scene, now)
	_house_pool.end()
	Probe.span("bf_houses", Time.get_ticks_usec() - pt)
	pt = Time.get_ticks_usec()
	_pawns = _pawn_states(scene, now)
	var animals3d: Array = _pawns.filter(func(p): return p["rendered"])
	Probe.span("bf_pawn_states", Time.get_ticks_usec() - pt)
	pt = Time.get_ticks_usec()
	_pawn_layer.reduced_motion = reduced_motion
	if not (Probe.flag("no_pawn_draw") and _pawn_layer.pool._used > 0):
		_pawn_layer.draw(animals3d, now, 1.0, Vector2(BOARD, BOARD))
	Probe.span("bf_pawn_draw", Time.get_ticks_usec() - pt)
	_overlay.queue_redraw()

## Animation timeline view for the render cadence policy (LudoPaws3DSceneState fields it reads).
func _cadence_scene(scene: Dictionary) -> Dictionary:
	var captures: Array = []
	for m in _motion.values():
		if m["kind"] == "return":
			captures.append({"started_ms": int(m["start"]) + int(m["delay"]), "duration_ms": int(m["return_ms"])})
	var reactions: Array = []
	for r in _active_reactions.values():
		reactions.append({"started_ms": int(r["start"]), "duration_ms": int(r["duration"])})
	return {"home_entries": scene.get("home_entries", {}), "forward": _last_forward, "captures": captures, "reactions": reactions}

## Presentation snapshot for the 3D scene; built from the authoritative engine state.
func scene_state() -> Dictionary:
	if match_state == null:
		return {}
	var state: Dictionary = match_state.state
	var players: Array = []
	for seat in state.get("players", []).size():
		var p: Dictionary = state["players"][seat]
		players.append({"playerId": p["playerId"], "seat": seat, "color": p["color"], "tokens": p["tokens"], "teamId": p.get("teamId")})
	var ranks := finish_ranks(state)
	_update_home_entries(state, players, ranks)
	return {"players": players, "status": state.get("status"), "ranks": ranks,
		"home_entries": _home_entries, "forward": _forward_home()}

## The door of a house opens for a pawn that is walking into it (LudoPawsCenterHouses3D).
func _forward_home():
	for key in _motion:
		var m: Dictionary = _motion[key]
		if m["kind"] == "forward" and int(m["path"][-1]) == Geo.HOME:
			return {"playerId": str(key).get_slice(":", 0), "to": Geo.HOME, "started_ms": m["start"], "duration_ms": m["duration"]}
	return null

var _home_entries := {}
var _entries_key := ""
var _was_complete := {}

## LudoPaws3DSceneView home-entry bookkeeping: a player completing during this view
## gets a live door/walk/plaque sequence; a restored finished player starts settled.
func _update_home_entries(state: Dictionary, players: Array, ranks: Dictionary) -> void:
	# The engine returns a fresh Dictionary per move; the seat roster identifies the match.
	var key := str(state.get("matchMode")) + str(players.map(func(p): return p["playerId"]))
	if key != _entries_key:
		_home_entries.clear()
		_was_complete.clear()
		_entries_key = key
	var now := Time.get_ticks_msec()
	for p in players:
		var complete := true
		for t in p["tokens"]:
			complete = complete and int(t) == Geo.HOME
		var id: String = p["playerId"]
		var known_rank := 0
		if state.get("matchMode") == "TEAM_UP":
			known_rank = 1 if state.get("winnerTeamId") != null and state.get("winnerTeamId") == p.get("teamId") else 0
		else:
			known_rank = int(ranks.get(id, 0))
		if complete:
			if _home_entries.has(id):
				if known_rank > 0:
					_home_entries[id]["rank"] = known_rank
			else:
				var live_arrival: bool = _was_complete.has(id) and not _was_complete[id]
				var rank := 0 if state.get("matchMode") == "TEAM_UP" and state.get("winnerTeamId") == null else known_rank
				_home_entries[id] = {"started_ms": maxi(now, _motion_end(id)) if live_arrival else 0, "rank": rank}
		_was_complete[id] = complete

## Walk-in progress (0..1) of a finished pawn, or 0 when its player has no entry.
func _entry_progress(player_id: String, token: int, now: int) -> float:
	if not _home_entries.has(player_id):
		return 0.0
	var started := int(_home_entries[player_id].get("started_ms", 0))
	var elapsed := Houses.ENTRY_DURATION_MS if started == 0 else now - started
	return Houses.entry_walk(elapsed, token)

## LudoPawsFinishPresentation.ranks
static func finish_ranks(state: Dictionary) -> Dictionary:
	if state.get("matchMode") == "TEAM_UP" or state.get("status") == "WAITING":
		return {}
	var ids: Array = []
	for p in state.get("players", []):
		ids.append(p["playerId"])
	var order: Array = []
	for id in state.get("finishOrderPlayerIds", []):
		if id in ids and not id in order:
			order.append(id)
	if order.is_empty() and state.get("winnerPlayerId") in ids:
		order.append(state["winnerPlayerId"])
	if state.get("status") == "FINISHED" and order.size() == ids.size() - 1:
		for id in ids:
			if not id in order:
				order.append(id)
				break
	var ranks := {}
	for i in order.size():
		ranks[order[i]] = i + 1
	return ranks

# ---------------------------------------------------------------- movement

## True while a step or capture animation is still playing (callers may wait on it).
func is_animating() -> bool:
	return not _motion.is_empty()

func _sync_motion(now: int) -> void:
	if match_state == null:
		return
	var contact_end := 0
	var captured: Array = []
	for p in match_state.state.get("players", []):
		var tokens: Array = p["tokens"]
		for token in tokens.size():
			var key := "%s:%d" % [p["playerId"], token]
			var target := int(tokens[token])
			if not _shown.has(key) or reduced_motion:
				_shown[key] = target
				continue
			var from := int(_shown[key])
			if from == target or _motion.has(key):
				continue
			_shown[key] = target
			var plan = MotionPolicy.transition(from, target)
			if plan == null:
				continue # not an animated transition: the pawn simply appears at its new cell
			if plan["kind"] == "return":
				captured.append([key, from])
				continue
			var path := MotionPolicy.forward_path(from, plan["steps"])
			var duration := Pacing.forward_animation_duration_ms(game_speed, path.size())
			_last_forward = {"to": target, "started_ms": now, "duration_ms": duration, "from": from, "steps": path.size(),
				"key": "%s:%d:%d:%d" % [key, from, target, now], "color": p["color"], "character_id": p.get("characterId")}
			_motion[key] = {"kind": "forward", "path": path, "from": from, "start": now, "delay": 0, "duration": duration,
				"hold": maxi(duration, duration - LANDING_SETTLE_MS + PawnLayer.RETURN_TO_PLAYER_MS)}
			# LudoPaws3DRenderCadencePolicy.captureContactDelayMillis: the landing settle follows contact.
			contact_end = maxi(contact_end, Cadence.capture_contact_delay_ms(duration))
	for entry in captured:
		# The captured pawn is hit on the mover's contact frame, then returns home.
		_motion[entry[0]] = {"kind": "return", "path": [Geo.YARD], "from": entry[1], "start": now,
			"delay": contact_end, "duration": contact_end + Pacing.capture_return_duration_ms(game_speed),
			"return_ms": Pacing.capture_return_duration_ms(game_speed)}
	for key in _motion.keys():
		if now - int(_motion[key]["start"]) >= int(_motion[key].get("hold", _motion[key]["duration"])):
			_motion.erase(key)

func _motion_end(player_id: String) -> int:
	var end := 0
	for key in _motion:
		if str(key).begins_with(player_id + ":"):
			end = maxi(end, int(_motion[key]["start"]) + int(_motion[key]["duration"]))
	return end

# ---------------------------------------------------------------- overlay

func _to_screen(p: Vector2) -> Vector2:
	return _board_origin() + Geo.rotate_point(p, _edge(), rotation_turns)

func _draw_overlay() -> void:
	var probe_t := Time.get_ticks_usec()
	_probed_draw_overlay()
	preload("res://scripts/game/perf.gd").span("draw_overlay", Time.get_ticks_usec() - probe_t)

func _probed_draw_overlay() -> void:
	_targets.clear()
	if match_state == null:
		return
	var cell := _edge() / BOARD
	_draw_finish_ranks(scene_state(), cell)
	var origin := _board_origin()
	for pawn in _pawns:
		var point: Vector2 = origin + pawn["screen"] * cell
		if not pawn["rendered"]:
			_fallback_pawn(point - Vector2(0, pawn["hop"] * cell), pawn["radius"] * cell * 0.62,
				Palette.color(Palette.TEAM[Geo.COLORS.find(pawn["color"])]), pawn["active"])
		if pawn["active"]:
			_targets[pawn["token"]] = point - Vector2(0, pawn["radius"] * cell * (0.9 if pawn["rendered"] else 0.0))
	_draw_legal_hints(origin, cell)
	_draw_fx(origin, cell)

## LudoPaws3DLegalPulseView: cues on the local player's legal pawns + crowd counts.
func _draw_legal_hints(origin: Vector2, cell: float) -> void:
	var scene := scene_state()
	if scene.is_empty():
		return
	var now := Time.get_ticks_msec()
	var dp := cell * BOARD / FX_BOARD_DP
	LegalHints.draw_crowd_counts(_overlay, origin, cell, rotation_turns, scene["players"], get_theme_default_font())
	var legal: Array = []
	var placements := Geo.stack_placements(scene["players"], 1.0, rotation_turns)
	for pawn in _pawns:
		if not pawn["active"]:
			continue
		var player: Dictionary = scene["players"].filter(func(p): return p["seat"] == pawn["seat"])[0]
		var placement: Dictionary = placements.get(pawn["key"], {"offset": Vector2.ZERO, "occupancy": 1})
		legal.append({"color": pawn["color"], "token": pawn["token"], "position": int(player["tokens"][pawn["token"]]),
			"character_id": pawn["character_id"], "offset": placement["offset"], "occupancy": placement["occupancy"]})
	if legal.is_empty():
		_legal_pulse_start = -1
		return
	if _legal_pulse_start < 0:
		_legal_pulse_start = now
	LegalHints.draw_legal(_overlay, origin, cell, rotation_turns, legal, LegalHints.pulse(now, _legal_pulse_start), now, dp)

## LudoPaws3DSceneView.emitMovementStepSounds: a jump at each hop take-off, a footstep on each
## visual cell, landmark sounds (yard exit, safe, home lane, home) on arrival, and the
## capture impact on the contact frame. Missed frames coalesce instead of bursting.
func _emit_movement_audio(now: int) -> void:
	var audio = get_node_or_null(^"/root/GameAudio")
	if audio == null or match_state == null:
		return
	var forward = _last_forward
	if forward != null:
		if forward["key"] != _audio_motion_key:
			_audio_motion_key = forward["key"]
			_audio_next_hop = 1
			_audio_next_step = 1
		var steps: int = forward["steps"]
		var step_ms := Pacing.movement_audio_step_duration_ms(int(forward["duration_ms"]), steps)
		if step_ms > 0:
			var elapsed := maxi(now - int(forward["started_ms"]), 0)
			var travel := int(forward["duration_ms"]) - LANDING_SETTLE_MS
			var due_hop := clampi(elapsed / step_ms + 1, 1, steps)
			if _audio_next_hop <= due_hop:
				if elapsed < travel and not (int(forward["from"]) == -1 and due_hop == 1):
					audio.jump()
				_audio_next_hop = due_hop + 1
			var due_step := clampi(elapsed / step_ms, 0, steps)
			if due_step >= _audio_next_step:
				var landmark = null
				for step in range(_audio_next_step, due_step + 1):
					for cue in MoveSounds.cues_for_visual_step(forward["color"], int(forward["from"]), step):
						if cue == MoveSounds.STEP:
							if step == due_step:
								audio.move_step(forward["character_id"])
						else:
							landmark = cue
				match landmark:
					MoveSounds.YARD_EXIT: audio.yard_exit()
					MoveSounds.SAFE_RELIEF: audio.safe()
					MoveSounds.HOME_LANE: audio.home_lane()
					MoveSounds.HOME: audio.home()
				_audio_next_step = due_step + 1
	var match_id := str(match_state.state.get("matchId", ""))
	for key in _motion:
		var m: Dictionary = _motion[key]
		if m["kind"] != "return" or now < int(m["start"]) + int(m["delay"]):
			continue
		var once := "%s:%s:%d:%d" % [match_id, key, int(m["from"]), int(m["start"])]
		if not _capture_audio_keys.has(once):
			_capture_audio_keys[once] = true
			audio.capture()
	if _capture_audio_keys.size() > 64:
		_capture_audio_keys.clear()

## LudoPawsReactiveBoardView.playReactions -> voicePlayer: animal calls, capture calls on contact.
func _play_voices(reactions: Array, now: int) -> void:
	var audio = get_node_or_null(^"/root/GameAudio")
	if audio == null:
		return
	var ids: Array = []
	for p in match_state.state.get("players", []):
		ids.append(Catalog.resolve_id(p.get("characterId", "dog")))
	var contact := 0
	for m in _motion.values():
		if m["kind"] == "return":
			contact = maxi(contact, int(m["start"]) + int(m["delay"]) - now)
	audio.play_voices(reactions, ids, contact, now)

## Footprints only change with the authoritative state, board size or perspective.
func _refresh_chrome(scene: Dictionary) -> void:
	var key := "%s|%d|%.2f" % [str(_footprints(scene)), rotation_turns, _edge()]
	if key != _chrome_key:
		_chrome_key = key
		_chrome.size = size
		_chrome.queue_redraw()

func _footprints(scene: Dictionary) -> Array:
	if scene.is_empty():
		return []
	var ids: Array = []
	ids.resize(4)
	var state: Dictionary = match_state.state
	for player in scene["players"]:
		ids[int(player["seat"])] = Catalog.resolve_id(state["players"][int(player["seat"])].get("characterId", "dog"))
	return PawPrints.footprints(scene["players"], ids)

func _draw_chrome() -> void:
	var probe_t := Time.get_ticks_usec()
	_probed_draw_chrome()
	preload("res://scripts/game/perf.gd").span("draw_chrome", Time.get_ticks_usec() - probe_t)

func _probed_draw_chrome() -> void:
	if match_state == null:
		return
	var edge := _edge()
	var cell := edge / BOARD
	var pivot := Vector2(edge, edge) * 0.5
	var base := Transform2D(0.0, _board_origin() + pivot) * Transform2D(rotation_turns * PI / 2.0, Vector2.ZERO) * Transform2D(0.0, -pivot)
	var colors := {}
	for i in Geo.COLORS.size():
		colors[Geo.COLORS[i]] = Palette.color(Palette.TEAM[i])
	PawPrints.draw(_chrome, base, _footprints(scene_state()), cell, rotation_turns, edge / FX_BOARD_DP, colors)

## LudoPawsGameFxOverlayView.onDraw: board-pixel FX rotated with the board perspective.
func _draw_fx(origin: Vector2, cell: float) -> void:
	var edge := cell * BOARD
	if absf(_fx.painter.density - edge / FX_BOARD_DP) > 0.0001:
		_fx.painter = FxPainter.new(edge / FX_BOARD_DP) # paints are set up once per board size, as in live
	var list := _fx.commands(Time.get_ticks_msec(), cell, rotation_turns, reduced_motion)
	if list.is_empty():
		return
	var pivot := Vector2(edge, edge) * 0.5
	var base := Transform2D(0.0, origin + pivot) * Transform2D(rotation_turns * PI / 2.0, Vector2.ZERO) * Transform2D(0.0, -pivot)
	FxPainter.render(_overlay, list, base)

## One presentation record per visible pawn, in board units (cell = 1) after board rotation.
## Feeds both the 3D animal layer and the 2D fallback/touch overlay.
func _pawn_states(scene: Dictionary, now: int) -> Array:
	var out: Array = []
	if scene.is_empty():
		return out
	var placements := Geo.stack_placements(scene["players"], 1.0, rotation_turns)
	var occupancy := {}
	for key in placements:
		occupancy[key] = placements[key]["occupancy"]
	var state: Dictionary = match_state.state
	var mover_focus = null
	for player in scene["players"]:
		var seat: int = player["seat"]
		var character_id := Catalog.resolve_id(state["players"][seat].get("characterId", "dog"))
		var rendered := Catalog.definition(character_id) != null
		var tokens: Array = player["tokens"]
		for token in tokens.size():
			var key := "%s:%d" % [player["playerId"], token]
			var position := int(tokens[token])
			var center = Geo.token_center(player["color"], token, position, 1.0, rotation_turns)
			if center == null:
				continue
			var frame := PawnLayer.idle_frame(character_id, seat, token, now)
			var yaw := PawnLayer.FRONT_YAW
			var hop := 0.0
			var presentation := 1.0
			if position == Geo.HOME:
				var progress := _entry_progress(player["playerId"], token, now)
				if progress >= 1.0:
					continue
				if progress > 0.0:
					center = _entry_walk_center(player["color"], center * (_edge() / BOARD), progress, _edge()) / (_edge() / BOARD)
					frame = {"kind": Pose.HOP, "progress": fmod(progress * 5.0, 1.0)} if progress < 0.6 else frame
					presentation = 1.0 - 0.72 * Houses._smooth(progress)
			var placement: Dictionary = placements.get(key, {"offset": Vector2.ZERO, "occupancy": 1})
			var board_point: Vector2 = center + placement["offset"]
			var raw_center: Vector2 = center
			if _motion.has(key):
				var m: Dictionary = _motion[key]
				var sample := _motion_sample(m, player["color"], token, seat, character_id, rendered, now)
				board_point = sample["point"]
				raw_center = board_point
				hop = sample["hop"]
				frame = sample.get("frame", frame)
				yaw = sample.get("yaw", yaw)
				position = int(m["path"][-1]) if m["kind"] == "forward" else int(m["from"])
				if m["kind"] == "forward":
					mover_focus = board_point
				else:
					# The capture route owns translation and scale; the pawn is drawn alone at its old cell size.
					presentation = sample["scale"]
					occupancy[key] = 1
			var reaction := _reaction_pose(key, character_id, now)
			var reacting := _reaction_running(key, now)
			var entry := _entry_elapsed(player["playerId"], now)
			if reduced_motion or frame["kind"] == Pose.HOP or (entry >= 0 and entry >= Houses.CELEBRATE_MS):
				# Locomotion owns weight/contact: keep facial reactions, drop competing bounce/scale.
				reaction = Pose.copy(reaction, {"liftY": 0.0, "scale": 1.0, "bodyYawDegrees": 0.0, "bodyPitchDegrees": 0.0})
			var screen := Geo.rotate_point(board_point, BOARD, rotation_turns)
			var active: bool = input_enabled and seat == match_state.turn and token in match_state.legal
			var fit := Geo.road_presentation(raw_center, 1.0, position, int(occupancy.get(key, 1)), character_id, rotation_turns)
			var radius: float = fit["radius"]
			out.append({"key": key, "seat": seat, "token": token, "color": player["color"], "character_id": character_id,
				"rendered": rendered, "x": screen.x, "y": screen.y + fit["foot_drop"], "screen": screen, "radius": radius, "hop": hop,
				"frame": frame, "yaw": yaw, "active": active, "presentation_scale": presentation,
				"attention_scale": _attention(active, now), "team": player["color"],
				"reaction": reaction, "idle_allowed": frame["kind"] == Pose.IDLE and not reacting})
	var focus: Vector2 = mover_focus if mover_focus != null else Vector2(BOARD, BOARD) * 0.5
	for pawn in out:
		pawn["focus"] = focus
	out.sort_custom(func(a, b): return a["y"] < b["y"])
	return out

## Legal pawns of the acting local player breathe (legalAttentionScale, 900 ms cycle).
func _attention(active: bool, now: int) -> float:
	if not active or not _motion.is_empty():
		return 1.0
	var phase := float(now % 900) / 900.0
	return 1.0 + (sin(phase * TAU) + 1.0) * 0.5 * 0.14

## Board-unit point, screen hop (fallback only), motion frame and facing for a moving pawn.
func _motion_sample(m: Dictionary, color: String, token: int, seat: int, character_id: String, rendered: bool, now: int) -> Dictionary:
	var elapsed := float(now - int(m["start"]) - int(m["delay"]))
	var start_center = Geo.token_center(color, token, int(m["from"]), 1.0, rotation_turns)
	if m["kind"] == "return":
		# LudoPaws3DSceneRenderer.captureReturnVisual: waits on its cell until contact, then
		# shake -> pop -> curved trip to the yard slot; body stays in a still IDLE frame.
		var slot := token + seat
		var from: Vector2 = start_center + Geo.token_offset_fraction(slot, int(m["from"]))
		var home: Vector2 = Geo.token_center(color, token, Geo.YARD, 1.0, rotation_turns) + Geo.token_offset_fraction(slot, Geo.YARD)
		var placement := CaptureReturn.sample(from, home, 1.0, maxf(elapsed, 0.0) / float(m["return_ms"]))
		return {"point": placement["point"], "hop": 0.0, "scale": placement["scale"],
			"frame": {"kind": Pose.IDLE, "progress": 0.0}, "yaw": PawnLayer.FRONT_YAW}
	var path: Array = m["path"]
	var travel_ms := float(int(m["duration"]) - LANDING_SETTLE_MS)
	var steps := path.size()
	var def = Catalog.definition(character_id)
	var final_point = Geo.token_center(color, token, int(path[-1]), 1.0, rotation_turns)
	if elapsed >= travel_ms:
		var after := elapsed - travel_ms
		var last_yaw := _segment_yaw(m, color, token, steps - 1)
		var settle := {"point": final_point, "hop": 0.0, "yaw": PawnLayer.settling_yaw(last_yaw, int(after))}
		if after < LANDING_SETTLE_MS:
			settle["frame"] = {"kind": Pose.HOP, "progress": after / LANDING_SETTLE_MS, "playback": Pose.SETTLE}
		return settle
	var visual := elapsed / travel_ms * steps
	var whole := clampi(int(visual), 0, steps - 1)
	var fraction := clampf(visual - whole, 0.0, 1.0)
	var a = start_center if whole == 0 else Geo.token_center(color, token, int(path[whole - 1]), 1.0, rotation_turns)
	var b = Geo.token_center(color, token, int(path[whole]), 1.0, rotation_turns)
	if a == null or b == null:
		return {"point": b if b != null else start_center, "hop": 0.0}
	var travel: float = def.Renderer.step_travel_progress(fraction) if rendered else Houses._smooth(fraction)
	var target_yaw := _segment_yaw(m, color, token, whole)
	var previous_yaw := _segment_yaw(m, color, token, whole - 1) if whole > 0 else PawnLayer.FRONT_YAW
	return {"point": a.lerp(b, travel), "hop": 0.0 if rendered else sin(PI * fraction) * 0.32,
		"frame": {"kind": Pose.HOP, "progress": fraction, "playback": Pose.START if visual < 1.0 else Pose.CHAIN},
		"yaw": PawnLayer.moving_yaw(previous_yaw, target_yaw, fraction)}

## Yaw of the visible road segment for path step `step` (LudoPaws3DSceneRenderer.segmentFacingYaw).
func _segment_yaw(m: Dictionary, color: String, token: int, step: int) -> float:
	var path: Array = m["path"]
	var from_pos := int(m["from"]) if step <= 0 else int(path[step - 1])
	var to_pos := int(path[clampi(step, 0, path.size() - 1)])
	var a = Geo.token_center(color, token, from_pos, 1.0, rotation_turns)
	var b = Geo.token_center(color, token, to_pos, 1.0, rotation_turns)
	if a == null or b == null:
		return PawnLayer.FRONT_YAW
	var d := Geo.rotate_point(b, BOARD, rotation_turns) - Geo.rotate_point(a, BOARD, rotation_turns)
	return PawnLayer.yaw_for_visible_delta(d.x, d.y)

## Renderer home-entry path: approach the doorway gate, then step 23% of a half span inside.
func _entry_walk_center(color: String, raw: Vector2, progress: float, edge: float) -> Vector2:
	var normal: Vector2 = {"RED": Vector2(-1, 0), "GREEN": Vector2(0, -1), "YELLOW": Vector2(1, 0)}.get(color, Vector2(0, 1))
	var half := edge / 6.0
	var approach := Houses._smooth(progress / 0.60)
	var inside := Houses._smooth((progress - 0.60) / 0.40)
	var gate := Vector2.ONE * edge * 0.5 + normal * half * Houses.CENTER_DOOR_OUTWARD
	return raw + (gate - raw) * approach - normal * half * 0.23 * inside

## Live LudoBoardView.drawPawn:the classic pawn shown whenever 3D animals are unavailable.
func _fallback_pawn(p: Vector2, r: float, color: Color, active: bool) -> void:
	_overlay.draw_set_transform(Vector2.ZERO)
	var shadow := PackedVector2Array()
	for k in 16:
		var a := k * TAU / 16.0
		shadow.append(p + Vector2(cos(a) * r * 0.8, r * 0.82 + sin(a) * r * 0.2))
	_overlay.draw_colored_polygon(shadow, Color8(0, 0, 0, 80))
	var body := Rect2(p.x - r * 0.58, p.y + r * 0.16, r * 1.16, r * 0.56)
	var box := StyleBoxFlat.new()
	box.bg_color = color
	box.set_corner_radius_all(roundi(r * 0.28))
	box.anti_aliasing = true
	_overlay.draw_style_box(box, body)
	var head := p - Vector2(0, r * 0.18)
	var dark := Color8(int(color.r8 * 0.56), int(color.g8 * 0.56), int(color.b8 * 0.56))
	var ring := PackedVector2Array()
	var colors := PackedColorArray()
	var light := p + Vector2(-r * 0.28, -r * 0.35)
	for k in 28:
		var a := k * TAU / 28.0
		var q := head + Vector2(cos(a), sin(a)) * r * 0.72
		ring.append(q)
		var t := clampf(q.distance_to(light) / (r * 1.25), 0.0, 1.0)
		colors.append(Color.WHITE.lerp(color, t / 0.30) if t < 0.30 else color.lerp(dark, (t - 0.30) / 0.70))
	_overlay.draw_polygon(ring, colors)
	var outline := ring.duplicate()
	outline.append(ring[0])
	_overlay.draw_polyline(outline, Color("#ffe797") if active else Color(1, 1, 1, 0.85), maxf(1.5, r * (0.16 if active else 0.08)), true)

func _draw_finish_ranks(scene: Dictionary, cell: float) -> void:
	var ranks: Dictionary = scene.get("ranks", {})
	if ranks.is_empty() or scene["players"].size() < 2:
		return
	var font := get_theme_default_font()
	var font_size := int(clampf(cell * 0.40, 8.0, 14.0 * cell / 25.0 + 6.0))
	for player in scene["players"]:
		if not ranks.has(player["playerId"]):
			continue
		var rank: int = ranks[player["playerId"]]
		var origin: Array = Geo.YARD_ORIGINS[player["color"]]
		var center := Vector2(Geo.axis_boundary(origin[1], cell) + cell * 2.5, Geo.axis_boundary(origin[0], cell) + cell * 2.5)
		center = _to_screen(center)
		var half := Vector2(cell * (1.47 if rank == 1 else 1.10), cell * 0.40)
		var radius := roundi(cell * 0.16)
		var metal := Palette.color([0xFFD568, 0xE2EAF2, 0xE8AA70][rank - 1] if rank <= 3 else Palette.TEAM[Geo.COLORS.find(player["color"])])
		_badge(Rect2(center - half + Vector2(cell * 0.03, cell * 0.08), half * 2.0), Color8(0, 0, 0, 0x65), radius)
		_badge(Rect2(center - half, half * 2.0), metal, radius)
		var inset := cell * 0.055
		_badge(Rect2(center - half + Vector2.ONE * inset, half * 2.0 - Vector2.ONE * inset * 2.0), Color("#253849"), roundi(radius * 0.7))
		var label := Houses.rank_label(rank)
		var text_size := font.get_string_size(label, HORIZONTAL_ALIGNMENT_CENTER, -1, font_size)
		_overlay.draw_string(font, center + Vector2(-text_size.x * 0.5, font.get_ascent(font_size) * 0.5 - 1),
			label, HORIZONTAL_ALIGNMENT_LEFT, -1, font_size, metal)

func _badge(rect: Rect2, color: Color, radius: int) -> void:
	var box := StyleBoxFlat.new()
	box.bg_color = color
	box.set_corner_radius_all(radius)
	box.anti_aliasing = true
	_overlay.draw_style_box(box, rect)

# ---------------------------------------------------------------- input

func _gui_input(event: InputEvent) -> void:
	var pressed := false
	var position := Vector2.ZERO
	if event is InputEventMouseButton and event.button_index == MOUSE_BUTTON_LEFT and event.pressed:
		pressed = true
		position = event.position
	elif event is InputEventScreenTouch and event.pressed:
		pressed = true
		position = event.position
	if not pressed:
		return
	var nearest := -1
	var distance := maxf(13.0, _edge() * 0.055)
	for index in _targets:
		var candidate: float = position.distance_to(_targets[index])
		if candidate < distance:
			distance = candidate
			nearest = index
	if nearest >= 0:
		accept_event()
		pawn_selected.emit(nearest)

# ---------------------------------------------------------------- reactions

## LudoPawsReactiveBoardView.bind for reactions: on every new authoritative state, update
## the idle clock, derive moments -> reactions through the shared director and play them;
## between states, one idle reaction becomes eligible after 24 s without a meaningful change.
func _bind_reactions(now: int) -> void:
	if match_state == null:
		return
	var revision := int(match_state.state.get("revision", 0))
	if revision != _snapshot_revision:
		_snapshot_revision = revision
		var current: Dictionary = match_state.snapshot()
		_update_idle_clock(current, now)
		var previous = _snapshot
		_snapshot = current
		if previous != null and previous["matchId"] != current["matchId"]:
			_active_reactions.clear()
		_fx.bind(previous, current, now)
		play_reactions(Reactions.detect(previous, current, now), now)
	if _snapshot == null:
		return
	var delay = IdlePolicy.delay_until_eligible_ms(_snapshot, _last_change_ms, now)
	if delay == null or delay > 0:
		return
	var idle_key = IdlePolicy.idle_reaction_key(_snapshot)
	if idle_key == null or idle_key == _last_idle_key:
		return
	var idle := Reactions.derive_idle(_snapshot, now, _last_change_ms, IdlePolicy.IDLE_THRESHOLD_MS)
	if idle.is_empty():
		return
	_last_idle_key = idle_key
	play_reactions(idle, now)

func _update_idle_clock(state: Dictionary, now: int) -> void:
	var key = IdlePolicy.meaningful_state_key(state)
	if state["status"] != "ACTIVE" or key != _meaningful_key:
		_meaningful_key = key
		_last_change_ms = now
		_last_idle_key = null

## LudoPaws3DSceneView.playReactions: higher-priority reactions replace lower ones on the
## same pawn; captures line up with the attacker's contact frame; one quiet bystander.
func play_reactions(reactions: Array, now: int) -> void:
	if reactions.is_empty() or _snapshot == null:
		return
	_fx.play(reactions, now)
	_play_voices(reactions, now)
	var next := {}
	for key in _active_reactions:
		var r: Dictionary = _active_reactions[key]
		if now < int(r["start"]) + int(r["duration"]):
			next[key] = r
	var players: Array = match_state.state.get("players", [])
	for reaction in Seq.stable_sort(reactions, func(a, b): return a["priority"] > b["priority"]):
		var player = null
		for p in _snapshot["players"]:
			if p["playerId"] == reaction["playerId"]:
				player = p
		if player == null:
			continue
		var character_id := Catalog.resolve_id(players[player["seat"]].get("characterId", "dog"))
		var targets := _target_tokens(player, reaction)
		var seed: int = Seq.java_hash(reaction["reactionKey"]) ^ int(reaction["eventIndex"])
		for token in targets:
			var variation := ReactionMotion.choose(reaction["animationCue"], token, seed, targets.size() > 1)
			var duration := maxi(1, int(ReactionMotion.duration_ms(character_id, variation[0]) * variation[3]))
			var key := "%s:%d" % [player["playerId"], token]
			var previous = next.get(key)
			if previous != null and int(reaction["priority"]) < int(previous["priority"]):
				continue
			var start := now + int(variation[1])
			var m = _motion.get(key)
			if reaction["animationCue"] == "CAPTURED" and m != null and m["kind"] == "return":
				start = int(m["start"]) + int(m["delay"])
			elif reaction["animationCue"] == "CAPTURE" and m != null and m["kind"] == "forward":
				start = int(m["start"]) + int(m["duration"]) - LANDING_SETTLE_MS
			next[key] = {"cue": variation[0], "start": start, "duration": duration, "priority": reaction["priority"],
				"variant": variation[2], "moment": reaction["momentType"], "observer": false}
	# One quiet bystander, never the actor/victim and never an extra voice.
	var principal = null
	for r in reactions:
		if r["momentType"] in ["CAPTURE_MADE", "HOME_REACHED", "SAFE_REACHED"]:
			principal = r
			break
	if principal != null and not reduced_motion:
		var actors := {}
		for r in reactions:
			actors[r["playerId"]] = true
		for p in _snapshot["players"]:
			if actors.has(p["playerId"]) or not (p["tokens"] as Array).any(func(t): return int(t) != Geo.HOME):
				continue
			var token: int = (p["tokens"] as Array).find((p["tokens"] as Array).filter(func(t): return int(t) != Geo.HOME)[0])
			var key := "%s:%d" % [p["playerId"], token]
			var moving: bool = _motion.has(key) and _motion[key]["kind"] == "forward"
			if not next.has(key) and not moving:
				var contact := now
				for k in next:
					if str(k).get_slice(":", 0) == principal["playerId"]:
						contact = int(next[k]["start"])
						break
				next[key] = {"cue": "IDLE", "start": maxi(now, contact) + 140, "duration": 750, "priority": 1,
					"variant": 0, "moment": null, "observer": true}
			break
	_active_reactions = next

static func _target_tokens(player: Dictionary, reaction: Dictionary) -> Array:
	var tokens: Array = player["tokens"]
	var token = reaction["tokenIndex"]
	if token != null and int(token) >= 0 and int(token) < tokens.size():
		return [int(token)]
	if reaction["animationCue"] in ["VICTORY", "DEFEAT"]:
		return range(tokens.size())
	for i in tokens.size():
		if int(tokens[i]) >= 0 and int(tokens[i]) <= 56:
			return [i]
	for i in tokens.size():
		if int(tokens[i]) == Geo.YARD:
			return [i]
	return [0]

func _reaction_running(key: String, now: int) -> bool:
	var r = _active_reactions.get(key)
	return r != null and now >= int(r["start"]) and now < int(r["start"]) + int(r["duration"])

## LudoPaws3DSceneRenderer.reactionPose
func _reaction_pose(key: String, character_id: String, now: int) -> Dictionary:
	var r = _active_reactions.get(key)
	if r == null or int(r["duration"]) <= 0 or now < int(r["start"]):
		return Pose.reaction()
	var elapsed := now - int(r["start"])
	if elapsed >= int(r["duration"]):
		return Pose.reaction()
	var progress := float(elapsed) / float(r["duration"])
	if r["observer"]:
		return ReactionMotion.observer(progress)
	var varied := ReactionMotion.vary(ReactionMotion.sample(character_id, r["cue"], progress), progress, r["variant"])
	var accented := Living.accent_reaction(varied, character_id, r["cue"], progress, r["variant"])
	return ReactionMotion.situational(accented, r["moment"], progress)

## Home-entry elapsed time for a finished player, or -1 without an entry.
func _entry_elapsed(player_id: String, now: int) -> int:
	if not _home_entries.has(player_id):
		return -1
	var started := int(_home_entries[player_id].get("started_ms", 0))
	return Houses.ENTRY_DURATION_MS if started == 0 else now - started
