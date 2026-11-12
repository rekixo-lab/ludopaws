extends RefCounted
## Settings storage. Live GameSettingsStore values live in the live app's own preference
## file `ludoproof_game_settings` with the live keys (so a player's choices survive the
## update); V2-only UI settings (Pass & Play prompt, confirm exit, language, tab memory ...)
## stay in user://meadow_ui.cfg.

const NativePrefs = preload("res://scripts/game/platform/native_prefs.gd")
const PREFS := "ludoproof_game_settings"
const V2_PATH := "user://meadow_ui.cfg"
## V2 key -> [live key, default]
const LIVE := {
	"music": ["music_enabled", true],
	"effects": ["sound_enabled", true],
	"animal_voices": ["animal_voices_enabled", true],
	"haptics": ["haptics_enabled", true],
	"quick_chat": ["quick_chat_enabled", true],
	"reduced_motion": ["reduced_motion", false],
	"play_hints": ["play_hints", true],
}
const SPEEDS := ["SLOW", "NORMAL", "FAST"]

static var _v2: ConfigFile
## Tests: values that win over storage and are never saved.
static var overrides := {}

static func _cfg() -> ConfigFile:
	if _v2 == null:
		_v2 = ConfigFile.new()
		_v2.load(V2_PATH)
	return _v2

static func get_value(key: String, fallback: Variant) -> Variant:
	if overrides.has(key):
		return overrides[key]
	var prefs = NativePrefs.open(PREFS)
	if LIVE.has(key):
		return prefs.get_bool(LIVE[key][0], bool(LIVE[key][1]))
	if key == "game_speed":
		var speed = prefs.get_string("game_speed", "NORMAL")
		return speed if SPEEDS.has(speed) else "NORMAL"
	return _cfg().get_value("settings", key, fallback)

static func set_value(key: String, value: Variant) -> void:
	if overrides.has(key):
		overrides[key] = value
		return
	var prefs = NativePrefs.open(PREFS)
	if LIVE.has(key):
		prefs.edit().put_bool(LIVE[key][0], bool(value)).commit()
	elif key == "game_speed":
		prefs.edit().put_string("game_speed", str(value) if SPEEDS.has(str(value)) else "NORMAL").commit()
	else:
		_cfg().set_value("settings", key, value)
		_cfg().save(V2_PATH)

## Tests / tools: forget the cached V2 file.
static func reload() -> void:
	_v2 = null
