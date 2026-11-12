extends CanvasLayer
## The single Ludo Paws launch screen (V2). Android's system splash (Android 12+: background,
## animated icon, branding image; Android 9-11: the window-background layer-list) shows the
## SAME background, logo and "by Rekixo" from the same dp math; this screen continues it pixel
## for pixel and only adds the loading bar between them, so the user sees one screen until
## Home. The bar is an empty-to-filled 3D four-colour track in a recessed groove that follows
## real readiness milestones only (splash drawn · Home built · Home's first frame · first-launch
## route decided), at most one full track per second, no percentage. Minimum 1000 ms visible
## (live), then a short fade into Home. Shown once per process: never on resume or Back. An
## error keeps this screen and offers a small Retry.

const LOGO := preload("res://assets/branding/launch/launch_logo.png")
const BRANDING := preload("res://assets/branding/launch/launch_branding.png")
## android_overlay/res/values/launch_screen.xml (keep in sync).
const BACKGROUND := Color("#0B2530")
const LOGO_DP := 288.0              # windowSplashScreenAnimatedIcon canvas (192 dp mask circle)
const BRANDING_DP := Vector2(200, 80)
const BRANDING_BOTTOM_DP := 60.0    # launch_branding_bottom
const MIN_VISIBLE_MS := 1000
const FILL_PER_SECOND := 1.0
const FADE_MS := 220.0
const TRACK_COLORS := [Color("#e8443a"), Color("#f2c230"), Color("#3fb35a"), Color("#3c84e6")]

static var shown_this_process := false

signal finished

var target := 0.0
var fill := 0.0
var failed := false
var _first_draw_ms := -1
var _done_requested := false
var _fade := 1.0
var _root: Control
var _logo: TextureRect
var _branding: TextureRect
var _track: Control
var _error: VBoxContainer
var _retry: Callable

## Starts the launch screen unless it already ran in this process.
static func show_once(parent: Node) -> Node:
	if shown_this_process:
		return null
	shown_this_process = true
	var splash := new()
	parent.add_child.call_deferred(splash)
	return splash

## Physical pixels per dp on this device, expressed in this layer's canvas units. Mobile: the
## screen's dpi / 160 (Android's dp). Desktop: a phone-like dp from the window width.
static func dp_in_canvas(viewport: Viewport) -> float:
	var final_scale := maxf(viewport.get_final_transform().get_scale().x, 0.0001)
	if OS.has_feature("mobile"):
		return (DisplayServer.screen_get_dpi() / 160.0) / final_scale
	return viewport.get_visible_rect().size.x / 392.0

## The shared layout (same math as the system splash): logo centred on the screen, branding
## centred horizontally with its bottom BRANDING_BOTTOM_DP above the screen's bottom edge.
static func layout_rects(view: Rect2, dp: float) -> Dictionary:
	var logo_size := Vector2.ONE * LOGO_DP * dp
	var logo := Rect2(view.get_center() - logo_size * 0.5, logo_size)
	var brand_size := BRANDING_DP * dp
	var brand := Rect2(Vector2(view.get_center().x - brand_size.x * 0.5, view.end.y - BRANDING_BOTTOM_DP * dp - brand_size.y), brand_size)
	# The loading bar sits midway between the logo's visible circle and the branding.
	var circle_bottom := view.get_center().y + 96.0 * dp
	var bar_h := 24.0 * dp
	var bar_w := minf(240.0 * dp, view.size.x * 0.7)
	var bar_y := (circle_bottom + brand.position.y) * 0.5 - bar_h * 0.5
	var bar := Rect2(Vector2(view.get_center().x - bar_w * 0.5, bar_y), Vector2(bar_w, bar_h))
	return {"logo": logo, "branding": brand, "bar": bar}

func _ready() -> void:
	layer = 120
	_root = Control.new()
	_root.mouse_filter = Control.MOUSE_FILTER_STOP
	_root.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	add_child(_root)
	var bg := ColorRect.new()
	bg.color = BACKGROUND
	bg.mouse_filter = Control.MOUSE_FILTER_IGNORE
	bg.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	_root.add_child(bg)
	_logo = _image(LOGO, "Logo")
	_branding = _image(BRANDING, "Branding")
	_track = Control.new()
	_track.name = "Track"
	_track.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_track.draw.connect(_draw_track)
	_root.add_child(_track)
	get_viewport().size_changed.connect(_layout)
	_layout()
	reach(0.15)

func _image(texture: Texture2D, node_name: String) -> TextureRect:
	var r := TextureRect.new()
	r.name = node_name
	r.texture = texture
	r.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	r.stretch_mode = TextureRect.STRETCH_SCALE
	r.texture_filter = CanvasItem.TEXTURE_FILTER_LINEAR_WITH_MIPMAPS
	r.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_root.add_child(r)
	return r

func rects() -> Dictionary:
	return layout_rects(get_viewport().get_visible_rect(), dp_in_canvas(get_viewport()))

func _layout() -> void:
	var r := rects()
	_logo.position = r["logo"].position
	_logo.size = r["logo"].size
	_branding.position = r["branding"].position
	_branding.size = r["branding"].size
	_track.position = r["bar"].position
	_track.size = r["bar"].size
	if is_instance_valid(_error):
		_error.position = Vector2(r["bar"].position.x, r["bar"].position.y - 8.0)
		_error.size.x = r["bar"].size.x
	_track.queue_redraw()

func reach(value: float) -> void:
	target = maxf(target, clampf(value, 0.0, 1.0))

func complete() -> void:
	reach(1.0)
	_done_requested = true

## Loading failed: stay on this screen with a short message and a small Retry.
func fail(message: String, retry: Callable) -> void:
	failed = true
	_retry = retry
	_track.visible = false
	if is_instance_valid(_error):
		_error.queue_free()
	_error = VBoxContainer.new()
	_error.alignment = BoxContainer.ALIGNMENT_CENTER
	_root.add_child(_error)
	var label := Label.new()
	label.text = message
	label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	label.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	label.add_theme_color_override("font_color", Color("#e6eef2"))
	_error.add_child(label)
	var b := MeadowButton.new()
	b.label = "Retry"
	b.font_size = 16
	b.custom_minimum_size = Vector2(120, 38)
	b.size_flags_horizontal = Control.SIZE_SHRINK_CENTER
	b.pressed.connect(_on_retry)
	_error.add_child(b)
	_layout()

func _on_retry() -> void:
	failed = false
	if is_instance_valid(_error):
		_error.queue_free()
	_track.visible = true
	if _retry.is_valid():
		_retry.call()

func visible_ms() -> int:
	return 0 if _first_draw_ms < 0 else Time.get_ticks_msec() - _first_draw_ms

func _process(delta: float) -> void:
	if _first_draw_ms < 0:
		_first_draw_ms = Time.get_ticks_msec()
	fill = minf(target, fill + FILL_PER_SECOND * delta)
	_track.queue_redraw()
	if _done_requested and not failed and fill >= 1.0 and visible_ms() >= MIN_VISIBLE_MS:
		_fade -= delta * 1000.0 / FADE_MS
		_root.modulate.a = clampf(_fade, 0.0, 1.0)
		if _fade <= 0.0:
			finished.emit()
			queue_free()

func _draw_track() -> void:
	var r := Rect2(Vector2.ZERO, _track.size)
	if r.size.x <= 0.0:
		return
	var groove := StyleBoxFlat.new()
	groove.bg_color = Color(0.03, 0.08, 0.10, 0.9)
	groove.set_corner_radius_all(int(r.size.y * 0.5))
	groove.border_color = Color(0.55, 0.38, 0.18, 0.9)      # thin wood rim
	groove.set_border_width_all(maxi(2, int(r.size.y * 0.08)))
	_track.draw_style_box(groove, r)
	var inner := r.grow(-r.size.y * 0.2)
	if fill <= 0.0:
		return
	var w := maxf(inner.size.x * fill, inner.size.y)
	var h := inner.size.y
	var steps := maxi(2, int(w / 3.0))
	for i in steps:
		var x0 := inner.position.x + w * i / steps
		var x1 := inner.position.x + w * (i + 1) / steps
		var t := ((x0 + x1) * 0.5 - inner.position.x) / inner.size.x * (TRACK_COLORS.size() - 1)
		var k := clampi(int(t), 0, TRACK_COLORS.size() - 2)
		var c: Color = TRACK_COLORS[k].lerp(TRACK_COLORS[k + 1], clampf(t - k, 0.0, 1.0))
		# Round the ends: inside the end caps only the circle part is drawn.
		var cx := (x0 + x1) * 0.5
		var rad := h * 0.5
		var dy := h * 0.5
		if cx < inner.position.x + rad:
			dy = sqrt(maxf(rad * rad - pow(inner.position.x + rad - cx, 2), 0.0))
		elif cx > inner.position.x + w - rad:
			dy = sqrt(maxf(rad * rad - pow(cx - (inner.position.x + w - rad), 2), 0.0))
		var mid := inner.get_center().y
		_track.draw_rect(Rect2(x0, mid - dy, x1 - x0 + 0.5, dy * 2.0), c)
		_track.draw_rect(Rect2(x0, mid - dy, x1 - x0 + 0.5, dy * 0.75), Color(1, 1, 1, 0.26))
		_track.draw_rect(Rect2(x0, mid + dy * 0.45, x1 - x0 + 0.5, dy * 0.55), Color(0, 0, 0, 0.2))
