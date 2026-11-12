extends RefCounted
## Release-safe facade for the development perf probe (scripts/debug/perf_probe.gd). The probe
## is excluded from release exports; here it is only looked up in debug builds, so release
## code never loads or runs it.

static var _probe = null
static var _checked := false

static func _p():
	if not _checked:
		_checked = true
		if OS.is_debug_build() and ResourceLoader.exists("res://scripts/debug/perf_probe.gd"):
			_probe = load("res://scripts/debug/perf_probe.gd")
	return _probe

static func is_enabled() -> bool:
	var p = _p()
	return p != null and p.enabled

static func get_flags() -> Dictionary:
	var p = _p()
	return p.flags if p != null else {}

static func span(name: String, us: int) -> void:
	var p = _p()
	if p != null and p.enabled:
		p.span(name, us)

static func flag(name: String) -> bool:
	var p = _p()
	return p != null and p.flag(name)

static func mark_tap() -> void:
	var p = _p()
	if p != null:
		p.mark_tap()

static func tap_feedback() -> void:
	var p = _p()
	if p != null:
		p.tap_feedback()
