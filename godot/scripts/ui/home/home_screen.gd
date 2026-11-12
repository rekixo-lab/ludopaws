extends Control

signal route_requested(route: StringName)

const Preview := preload("res://scripts/ui/preview/meadow_preview.gd")
const Hints := preload("res://scripts/ui/hints/hints.gd")
const LaunchSplash := preload("res://scripts/ui/launch/launch_splash.gd")
const Hub := preload("res://scripts/ui/preview/hub_pages.gd")
const DESIGN_SIZE := Vector2(430, 826)
const MIN_LAYOUT_HEIGHT := 760.0
const HERO_DESIGN_HEIGHT := 454.0
const HERO_WIDE := preload("res://assets/ui/brand/home_woodland_hero.webp")
const HERO_PORTRAIT := preload("res://assets/ui/brand/home_woodland_hero_portrait.webp")

var preview: Control

@onready var canvas: Control = $Canvas
@onready var play_content: Control = $Canvas/PlayContent
@onready var hero: TextureRect = $Canvas/PlayContent/WoodlandHero
@onready var brand: Control = $Canvas/PlayContent/BrandTitle
@onready var navigation = $Canvas/BottomNavigation

func _ready() -> void:
	get_window().content_scale_aspect = Window.CONTENT_SCALE_ASPECT_EXPAND
	get_window().content_scale_size = Vector2i(DESIGN_SIZE)
	resized.connect(_fit_canvas)
	_fit_canvas()
	for item: Button in get_tree().get_nodes_in_group("home_routes"):
		if is_ancestor_of(item):
			var route: StringName = item.get_meta("route")
			if item.get_parent() == $Canvas/PlayContent/Currencies/Gems:
				route = &"wallet"
			item.pressed.connect(_request_route.bind(route))
	preview = Preview.new()
	canvas.add_child(preview)
	canvas.move_child(preview, navigation.get_index())
	preview.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	preview.hide()
	preview.top_inset = -hero.offset_top
	preview.route_changed.connect(_update_visible_route)
	route_requested.connect(preview.open)
	_start_launch_splash()
	preview.first_launch.call_deferred()
	_refresh_continue.call_deferred()
	_connect_account.call_deferred()
	# After the launch screen and the first-launch route (age / demo / home) have settled.
	get_tree().create_timer(1.5).timeout.connect(_check_room_link)
	get_tree().create_timer(3.0).timeout.connect(_home_promotions)
	# New players (no game yet) who stay idle on Home: the hand points at the Robot mode.
	var robot_tile := get_node_or_null("Canvas/PlayContent/ModeGrid/Robot") as Control
	if robot_tile != null:
		var home := self
		Hints.offer(robot_tile, "home.robot", func():
			var fresh: bool = int(home.preview.state.setting("total_games", 0)) == 0
			return robot_tile if fresh and not home.preview.visible and robot_tile.is_visible_in_tree() else null,
			Hints.Policy.IDLE_SCREEN_MS, 40)
	for argument in OS.get_cmdline_user_args():
		if argument.begins_with("--preview-route="):
			_request_route(StringName(argument.trim_prefix("--preview-route=")))
		if argument.begins_with("--capture-home="):
			_capture_preview(argument.trim_prefix("--capture-home="))

func _fit_canvas() -> void:
	var area := _safe_content_area()
	if area.size.x <= 0.0 or area.size.y <= 0.0:
		return
	var factor := minf(area.size.x / DESIGN_SIZE.x, area.size.y / MIN_LAYOUT_HEIGHT)
	canvas.size = Vector2(DESIGN_SIZE.x, area.size.y / factor)
	canvas.scale = Vector2.ONE * factor
	canvas.position = area.position + Vector2((area.size.x - DESIGN_SIZE.x * factor) * 0.5, 0.0)
	# Draw the artwork into the cutout inset while keeping controls inside it.
	hero.offset_top = -area.position.y / factor
	if is_instance_valid(preview):
		preview.top_inset = -hero.offset_top
	var available_hero_height := HERO_DESIGN_HEIGHT + canvas.size.y - DESIGN_SIZE.y - hero.offset_top
	_fit_hero(available_hero_height)

func _fit_hero(available_height: float) -> void:
	var wide_layout := DESIGN_SIZE.x / available_height >= 1.0
	hero.texture = HERO_WIDE if wide_layout else HERO_PORTRAIT
	var brand_scale := 0.77 if wide_layout else 1.0
	brand.scale = Vector2.ONE * brand_scale
	brand.position = Vector2((DESIGN_SIZE.x - brand.size.x * brand_scale) * 0.5 + 2.0, 57.0 if wide_layout else 65.0)
	var source_size := hero.texture.get_size()
	var art_height := DESIGN_SIZE.x * source_size.y / source_size.x if wide_layout else available_height
	hero.offset_bottom = hero.offset_top + art_height - canvas.size.y

	# Sample a proportional crop rather than changing any object's aspect ratio.
	var frame_size := Vector2(DESIGN_SIZE.x, art_height)
	var uniform_scale := maxf(frame_size.x / source_size.x, frame_size.y / source_size.y)
	var visible_fraction := frame_size / (source_size * uniform_scale)
	var hero_material := hero.material as ShaderMaterial
	hero_material.set_shader_parameter("visible_fraction", visible_fraction)
	hero_material.set_shader_parameter("crop_origin", (Vector2.ONE - visible_fraction) * 0.5)
	hero_material.set_shader_parameter("fade_start", 0.91 if wide_layout else 0.85)

func _safe_content_area() -> Rect2:
	var area := Rect2(Vector2.ZERO, size)
	if OS.get_name() != "Android":
		return area
	var window_pixels := get_window().size
	if window_pixels.x <= 0 or window_pixels.y <= 0:
		return area
	var safe_pixels := DisplayServer.get_display_safe_area().intersection(Rect2i(Vector2i.ZERO, window_pixels))
	if not safe_pixels.has_area():
		return area
	var units_per_pixel := size / Vector2(window_pixels)
	return Rect2(Vector2(safe_pixels.position) * units_per_pixel, Vector2(safe_pixels.size) * units_per_pixel)

func _request_route(route: StringName) -> void:
	route_requested.emit(route)

func _update_visible_route(route: StringName) -> void:
	play_content.visible = route == &"play"
	if route == &"play":
		_refresh_continue()
		_refresh_gems()
		_home_promotions.call_deferred()
	navigation.visible = route in [&"play", &"rewards", &"shop", &"profile", &"online", &"team_up", &"friends"]
	navigation.select_route(route if route in [&"rewards", &"shop", &"profile"] else &"play")

# ---------------------------------------------------------------- Home promotions (live order)

var _updates
var _promotions_busy := false
var _download_label: Label

## Live HomeActivity.resumeHomePromotions: the update / What's New gate first, then (adult
## age band only) the natural review and the referral popup. Only on the Home screen, never
## during a match or the Demo.
func _home_promotions() -> void:
	if _promotions_busy or not is_instance_valid(preview) or preview.visible or not Engine.has_singleton("LudoPawsPlatform"):
		return
	if not load("res://scripts/game/safety/age_entry.gd").new().has_choice():
		return
	_promotions_busy = true
	var platform = Engine.get_singleton("LudoPawsPlatform")
	if _updates == null:
		_updates = load("res://scripts/game/growth/update_coordinator.gd").new(platform, self)
	await _updates.on_home()
	if not preview.visible and load("res://scripts/game/growth/referral_service.gd").adult():
		await get_tree().create_timer(2.2).timeout
		if not preview.visible:
			await load("res://scripts/game/growth/natural_review.gd").request(platform)
		var account = get_node_or_null("/root/Account")
		if account != null and account.available() and not preview.visible:
			var referrals = load("res://scripts/game/growth/referral_service.gd").new(account, platform)
			referrals.refresh_attribution()
			var snapshot: Dictionary = await referrals.automatic_popup()
			if not snapshot.is_empty() and not preview.visible:
				var choice: String = await home_sheet("Invite a friend", "Play a real match together and earn the Friendship Bundle. Only you, the inviter, get the reward.",
					[["Share invite", "share", true], ["Not now", "later", false]])
				referrals.popup_dismissed(choice != "share")
				if choice == "share":
					var url: String = await referrals.invite()
					if not url.is_empty():
						load("res://scripts/game/online/room_link.gd").share("Want to play Ludo Paws with me?\n" + url, "Invite a friend")
	_promotions_busy = false

signal _sheet_closed(choice: String)

## A V2 sheet over Home: [[label, choice, primary], ...] -> (await) the chosen id.
func home_sheet(title: String, message: String, buttons: Array) -> String:
	var UI = load("res://scripts/ui/preview/meadow_controls.gd")
	var veil := ColorRect.new()
	veil.color = Color("#05100ec0")
	add_child(veil)
	veil.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	var center := CenterContainer.new()
	veil.add_child(center)
	center.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	var frame: VBoxContainer = UI.panel(center, title)
	frame.get_parent().custom_minimum_size.x = minf(360.0, size.x - 32.0)
	UI.label(frame, message, 15, true)
	for b in buttons:
		UI.button(frame, b[0], func(): _sheet_closed.emit(b[1]), b[2], "", 44)
	var choice: String = await _sheet_closed
	veil.queue_free()
	return choice

# update_coordinator.gd UI (live PlayUpdateCoordinator dialogs, V2 style).
func prompt(urgent: bool, on_update: Callable) -> void:
	var choice := await home_sheet("Ludo Paws Update Available", ("An important update is available on Google Play." if urgent
		else "New update available. Download it while you keep playing.") + "\nनया अपडेट उपलब्ध है.", [["Update", "update", true], ["Later", "later", false]])
	if choice == "update":
		on_update.call()

func whats_new(version: String, on_notes: Callable) -> void:
	var choice := await home_sheet("What's New in Ludo Paws", "Ludo Paws was updated to version %s. See the exact changes for this release on Google Play." % version,
		[["Let's Play", "play", true], ["Release Notes", "notes", false]])
	if choice == "notes":
		on_notes.call()

func install_ready(on_restart: Callable) -> void:
	if is_instance_valid(_download_label):
		_download_label.queue_free()
	var choice := await home_sheet("Update Ready to Install", "The update is downloaded. Restart Ludo Paws to install it. · अपडेट तैयार है.",
		[["Restart to update", "restart", true], ["Later", "later", false]])
	if choice == "restart":
		on_restart.call()

func downloading(progress: float) -> void:
	if not is_instance_valid(_download_label):
		_download_label = Label.new()
		_download_label.add_theme_font_size_override("font_size", 13)
		_download_label.add_theme_color_override("font_color", Color("#e8dcc0"))
		_download_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
		play_content.add_child(_download_label)
		_download_label.set_anchors_preset(Control.PRESET_TOP_WIDE)
		_download_label.offset_top = 74
		_download_label.offset_bottom = 92
	_download_label.text = "Downloading update… %d%%" % int(progress * 100.0)

## Live RoomLinkActivity: a private-room link opens a "Play together" card.
func _check_room_link() -> void:
	var link: Dictionary = load("res://scripts/game/online/room_link.gd").take_launch_link()
	if link.is_empty() or not is_instance_valid(preview) or preview.current in [&"age", &"match"]:
		return
	var UI = load("res://scripts/ui/preview/meadow_controls.gd")
	# Like live RoomLinkActivity (its own screen): the card opens over the Friends page.
	_request_route(&"friends")
	await get_tree().process_frame
	var frame: VBoxContainer = preview.dialog("Play together", "Your next game starts with a friend. A few friendly paws. One happy game together.", true)
	UI.label(frame, "FRIENDS · PRIVATE ROOM", 13, true)
	UI.label(frame, link["room"], 26, true)
	UI.button(frame, "Open my friend's room", func():
		preview.dismiss()
		var saved: Dictionary = load("res://scripts/game/online/player_session_store.gd").load_session()
		if not saved.is_empty() and saved["matchId"] != link["room"]:
			preview.notice("Play together", "Finish or exit your saved match before joining another room.", true)
			return
		preview.refresh(), true, "play", 46)
	UI.label(frame, "Your invitation will appear in Friends. New friends need the host's approval first.", 13, true)
	UI.label(frame, "Rooms can fill up, start or expire. Ask for a fresh invitation if this one is no longer available.", 12, true)
	UI.button(frame, "Maybe later", preview.dismiss, false, "", 40)

func _notification(what: int) -> void:
	if what == NOTIFICATION_APPLICATION_RESUMED:
		_check_room_link.call_deferred()
	if what == NOTIFICATION_WM_GO_BACK_REQUEST and is_instance_valid(preview) and preview.visible:
		preview.back()

func _unhandled_key_input(event: InputEvent) -> void:
	if event.is_action_pressed("ui_cancel") and is_instance_valid(preview) and preview.visible:
		preview.back()
		get_viewport().set_input_as_handled()

func _capture_preview(path: String) -> void:
	await RenderingServer.frame_post_draw
	await RenderingServer.frame_post_draw
	var error := get_viewport().get_texture().get_image().save_png(path)
	get_tree().quit(error)


## Home Gems bar = the cached server wallet; live Home refreshes it once the startup account
## is ready (and after every wallet change).
func _refresh_gems() -> void:
	var bar := get_node_or_null("Canvas/PlayContent/Currencies/Gems")
	if bar != null and is_instance_valid(preview):
		bar.amount = preview.state.inventory.gems

var _banner: Button

func _connect_account() -> void:
	if not is_instance_valid(preview):
		return
	preview.state.store.wallet_changed.connect(_refresh_gems)
	preview.state.inventory.changed.connect(_refresh_gems)
	_refresh_gems()
	var account = get_node_or_null("/root/Account")
	if account == null or not account.available():
		return
	_resume_online.call_deferred()
	_banner = Button.new()
	_banner.flat = true
	_banner.focus_mode = Control.FOCUS_NONE
	_banner.add_theme_font_size_override("font_size", 12)
	_banner.add_theme_color_override("font_color", Color("#e8dcc0"))
	_banner.add_theme_color_override("font_hover_color", Color("#fff2cf"))
	_banner.add_theme_color_override("font_pressed_color", Color("#fff2cf"))
	_banner.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	play_content.add_child(_banner)
	_banner.set_anchors_preset(Control.PRESET_TOP_WIDE)
	_banner.offset_left = 24
	_banner.offset_right = -24
	_banner.offset_top = 52
	_banner.offset_bottom = 72
	_banner.pressed.connect(_on_banner.bind(account))
	account.startup_changed.connect(_on_startup.bind(account))
	_on_startup(account.startup_state, account)

## Live: an open online seat resumes (Continue Match) and pending forfeits are retried.
func _resume_online() -> void:
	var OnlineFlow = load("res://scripts/game/online/online_turn_flow.gd")
	var c = OnlineFlow.controller_for(get_tree())
	if c == null:
		return
	c.retry_pending_forfeits()
	if not c.finished.is_connected(_on_online_finished):
		c.finished.connect(_on_online_finished)
	if preview.state.local_game != null or not await c.restore():
		return
	if c.state.get("status") == "ACTIVE" and preview.state.local_game == null:
		preview.state.local_game = OnlineFlow._render_game(c.state)
		preview.state.match_mode = &"online"
		preview.state.saved_preview = true
		_refresh_continue()

## Live refreshMatchGemWalletAfterFinish.
func _on_online_finished(_state: Dictionary, gem_reward: bool) -> void:
	if gem_reward and is_instance_valid(preview):
		await preview.state.store.refresh_wallet()
		_refresh_gems()

func _on_startup(state: String, account) -> void:
	if not is_instance_valid(_banner):
		return
	_banner.text = account.startup_banner(state)
	_banner.visible = not _banner.text.is_empty()
	if state in ["GUEST_READY", "SAVED_READY"]:
		await preview.state.store.refresh_wallet()
		await preview.state.store.sync_equipment()
		_refresh_gems()

func _on_banner(account) -> void:
	if account.startup_state == "RESTORE_REQUIRED":
		_request_route(&"profile")
		Hub.account_menu.call_deferred(preview)
	else:
		account.startup()

## Continue Match only for a real, unfinished local game (live resume); never a sample card.
func _refresh_continue() -> void:
	var card := get_node_or_null("Canvas/PlayContent/ContinueMatch") as Control
	if card == null or not is_instance_valid(preview):
		return
	var state = preview.state
	var game = state.local_game
	var resumable: bool = game != null and not game.complete and state.match_mode in [&"robot", &"pass_and_play", &"online"]
	card.visible = resumable
	if resumable:
		var summary := card.get_node_or_null("Summary") as Label
		if summary != null:
			summary.text = "%s  •  %d Players" % [{&"robot": "Robot", &"online": "Online"}.get(state.match_mode, "Pass & Play"), game.state["players"].size()]


## The game splash on a real app start (Home is the main scene), once per process.
func _start_launch_splash() -> void:
	# Only on a real app start: Godot sets current_scene to the main scene before its _ready.
	# (Tests and tools that add Home themselves never get a launch screen.)
	if get_tree().current_scene != self:
		return
	var splash = LaunchSplash.show_once(get_tree().root)
	if splash == null:
		return
	splash.reach(0.25)                 # Home built
	await get_tree().process_frame
	await RenderingServer.frame_post_draw
	splash.reach(0.7)                  # Home's first frame drawn
	await get_tree().process_frame     # first-launch route (age / demo / home) decided
	await get_tree().process_frame
	if is_instance_valid(splash):
		splash.complete()
