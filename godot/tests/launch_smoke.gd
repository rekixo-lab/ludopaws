extends SceneTree
## Single launch screen (docs: assets/branding, android/build/res/values/launch_screen.xml):
## built once per process (never on resume / re-created Home); logo and "by Rekixo" placed by
## the same dp math as the Android system splash (centre, 288 dp, 200x80 dp at 60 dp from the
## bottom) and the same colour; the bar sits between them; error -> Retry on the same screen;
## minimum ~1 s before it finishes.
##   godot --headless --path . --script res://tests/launch_smoke.gd

const Launch = preload("res://scripts/ui/launch/launch_splash.gd")

var failures := 0
var checks := 0

func _check(ok: bool, label: String) -> void:
	checks += 1
	if not ok:
		failures += 1
		printerr("FAIL: ", label)

func _initialize() -> void:
	# Android resources and the Godot constants agree.
	var xml := FileAccess.get_file_as_string("res://android_overlay/res/values/launch_screen.xml")
	_check(xml.contains("<color name=\"launch_background\">#%s</color>" % Launch.BACKGROUND.to_html(false).to_upper()), "launch colour matches the Android splash colour")
	_check(xml.contains("<dimen name=\"launch_branding_bottom\">%ddp</dimen>" % int(Launch.BRANDING_BOTTOM_DP)), "branding bottom inset matches the Android value")
	var layer := FileAccess.get_file_as_string("res://android_overlay/res/drawable/launch_window_background.xml")
	_check(layer.contains("android:width=\"288dp\"") and layer.contains("android:width=\"200dp\"") and layer.contains("android:height=\"80dp\""),
		"Android 9-11 layer-list uses the same 288 dp logo and 200x80 dp branding")
	var theme_attrs := FileAccess.get_file_as_string("res://export_presets.cfg")
	_check(theme_attrs.contains("@drawable/launch_logo") and theme_attrs.contains("@drawable/launch_branding") and theme_attrs.contains("@color/launch_background"),
		"system splash theme uses the shared logo, branding and colour")
	_check(Launch.LOGO.get_width() == 288 * 4 and Launch.BRANDING.get_width() == 200 * 4 and Launch.BRANDING.get_height() == 80 * 4, "shared bitmaps are 288 dp and 200x80 dp at xxxhdpi")
	# Layout math (A71: 1080x2400 px at 420 dpi = 2.625 px/dp).
	var r: Dictionary = Launch.layout_rects(Rect2(0, 0, 1080, 2400), 2.625)
	_check(r["logo"].get_center().is_equal_approx(Vector2(540, 1200)) and is_equal_approx(r["logo"].size.x, 756.0), "logo centred, 288 dp")
	_check(is_equal_approx(r["branding"].end.y, 2400 - 60 * 2.625) and is_equal_approx(r["branding"].get_center().x, 540.0), "branding centred, 60 dp above the bottom")
	_check(r["bar"].position.y > 1200 + 96 * 2.625 and r["bar"].end.y < r["branding"].position.y, "loading bar between the logo circle and the branding")
	# Measured on the A71: the wordmark pixels span y 2100-2173 in the system splash (centre 2136.5).
	_check(absf(r["branding"].get_center().y - 2136.5) <= 2.0, "matches the measured A71 system splash branding (centre y %.1f)" % r["branding"].get_center().y)
	# Once per process.
	Launch.shown_this_process = false
	var first = Launch.show_once(root)
	var second = Launch.show_once(root)
	_check(first != null and second == null, "the launch screen is built once per process")
	for i in 3:
		await process_frame
	# Resume / re-created Home never brings it back.
	var home: Control = load("res://scenes/ui/home/home_screen.tscn").instantiate()
	root.add_child(home)
	home.notification(Node.NOTIFICATION_APPLICATION_RESUMED)
	await process_frame
	await process_frame
	var screens := root.get_children().filter(func(n): return n.get_script() == Launch)
	_check(screens.size() == 1, "resume / a new Home do not show it again (%d)" % screens.size())
	# Error -> Retry on the same screen.
	var retried := [0]
	first.fail("Something went wrong while loading.", func(): retried[0] += 1)
	await process_frame
	var buttons := first.find_children("*", "MeadowButton", true, false)
	_check(first.failed and buttons.size() == 1 and not first._track.visible, "an error keeps the launch screen and shows Retry")
	buttons[0].pressed.emit()
	await process_frame
	_check(retried[0] == 1 and not first.failed and first._track.visible, "Retry runs the retry and brings the bar back")
	# Minimum ~1 s, then it finishes.
	var started := Time.get_ticks_msec()
	first.complete()
	var done := [false, 0]
	first.finished.connect(func():
		done[0] = true
		done[1] = first.visible_ms())
	while not done[0] and Time.get_ticks_msec() - started < 4000:
		await process_frame
	_check(done[0] and done[1] >= Launch.MIN_VISIBLE_MS, "finishes after the bar is full and >= 1 s on screen (%d ms)" % done[1])
	home.queue_free()
	print("Launch smoke: %d checks, %d failures" % [checks, failures])
	quit(1 if failures > 0 else 0)
