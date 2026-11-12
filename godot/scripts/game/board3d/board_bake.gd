extends Node
## Board artwork bakes shared by the match board and the Shop previews: each board is drawn
## once per physical size into a SubViewport, read back to an ImageTexture, cached in memory
## and on disk (user://board_bakes). Jobs run one per frame so opening a tab never draws
## every board at once (the Shop Boards tab took ~1.2 s on a Samsung A71 before this).

const Art = preload("res://scripts/game/board3d/classic_board_art.gd")
const BoardArt = preload("res://scripts/game/board3d/board_art_player.gd")
const VERSION := 1
const DIR := "user://board_bakes"

static var textures := {}   # "board|px" -> Texture2D
static var _queue: Array = []  # [key, board_id, px, [callbacks]]
static var _runner: Node

static func key_for(board_id: String, px: int) -> String:
	return "%s|%d" % [board_id, px]

static func path_for(key: String) -> String:
	return "%s/v%d_%s_%s.png" % [DIR, VERSION, ProjectSettings.get_setting("application/config/version", "0"), key.replace("|", "_")]

## The cached texture (memory, then disk) or null.
static func cached(board_id: String, px: int) -> Texture2D:
	var key := key_for(board_id, px)
	if not textures.has(key) and FileAccess.file_exists(path_for(key)):
		var image := Image.load_from_file(path_for(key))
		if image != null and not image.is_empty() and image.get_width() == px:
			textures[key] = ImageTexture.create_from_image(image)
	return textures.get(key)

static func store(board_id: String, px: int, image: Image) -> Texture2D:
	var key := key_for(board_id, px)
	var texture := ImageTexture.create_from_image(image)
	textures[key] = texture
	if not OS.has_feature("editor"):
		DirAccess.make_dir_recursive_absolute(DIR)
		image.save_png(path_for(key))
	return texture

## Calls done(texture) now if cached, else after the queued bake (one board per frame).
static func request(board_id: String, px: int, done: Callable) -> void:
	var hit := cached(board_id, px)
	if hit != null:
		done.call(hit)
		return
	var key := key_for(board_id, px)
	for job in _queue:
		if job[0] == key:
			job[3].append(done)
			return
	_queue.append([key, board_id, px, [done]])
	if not is_instance_valid(_runner):
		var tree := Engine.get_main_loop() as SceneTree
		if tree == null:
			return
		_runner = load("res://scripts/game/board3d/board_bake.gd").new()
		_runner.name = "BoardBake"
		tree.root.add_child.call_deferred(_runner)

static func new_art(board_id: String, px: int) -> Control:
	var art: Control
	if board_id == "board_classic":
		art = Art.new()
	else:
		art = BoardArt.new()
		art.board_id = board_id
		art.edge = px
	art.size = Vector2(px, px)
	return art

var _busy := false

func _process(_delta: float) -> void:
	if _busy or _queue.is_empty():
		return
	_bake(_queue.pop_front())

func _bake(job: Array) -> void:
	_busy = true
	var px: int = job[2]
	var vp := SubViewport.new()
	vp.disable_3d = true
	vp.transparent_bg = false
	vp.size = Vector2i(px, px)
	vp.render_target_update_mode = SubViewport.UPDATE_ONCE
	vp.add_child(new_art(job[1], px))
	add_child(vp)
	await RenderingServer.frame_post_draw
	var image := vp.get_texture().get_image()
	vp.queue_free()
	_busy = false
	if image == null or image.is_empty():
		return
	var texture := store(job[1], px, image)
	for done in job[3]:
		if done.is_valid():
			done.call(texture)
