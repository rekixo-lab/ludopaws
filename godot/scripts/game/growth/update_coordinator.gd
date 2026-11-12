extends RefCounted
## Live PlayUpdateCoordinator + UpdateExperiencePolicy (`ludopaws_update_experience_v1`):
## Google Play decides availability (no polling of our own). On Home: What's New once after an
## upgrade, else an update prompt (priority >= 5 + immediate allowed = immediate update, else
## flexible) at most again after 72 h for the same version; a downloaded flexible update asks
## to restart. Re-checked when Home returns after 6 h. Never during a match or the Demo.
## `ui` receives: prompt(urgent, on_update), whats_new(version, on_notes), install_ready(on_restart),
## downloading(progress 0..1).

const NativePrefs = preload("res://scripts/game/platform/native_prefs.gd")
const Gate = preload("res://scripts/game/growth/prompt_gate.gd")
const PREFS := "ludopaws_update_experience_v1"
const REPEAT_PROMPT_AFTER_MS := 72 * 3600 * 1000
const RECHECK_MS := 6 * 3600 * 1000

var platform
var ui
var current_version := 0
var current_version_name := ""
var checking := false
var _last_check := -RECHECK_MS
var _listening := false

static func should_show_whats_new(previous: int, current: int, updated_install: bool) -> bool:
	if current <= 0:
		return false
	return updated_install if previous < 0 else current > previous

static func should_prompt(available: int, prompted: int, last_prompt: int, now: int, critical: bool) -> bool:
	if available <= 0 or now <= 0:
		return false
	if critical or available != prompted:
		return true
	if last_prompt <= 0 or last_prompt > now:
		return true
	return now - last_prompt >= REPEAT_PROMPT_AFTER_MS

func _init(platform_singleton, ui_target) -> void:
	platform = platform_singleton
	ui = ui_target

func _prefs():
	return NativePrefs.open(PREFS)

## Home is shown (live beforePromotions). Returns after the gate is done.
func on_home() -> void:
	if platform == null or checking or Time.get_ticks_msec() - _last_check < RECHECK_MS:
		return
	checking = true
	_listen()
	var info = JSON.parse_string(str(platform.appInstallInfo()))
	if info is Dictionary and int(info.get("versionCode", 0)) > 0:
		current_version = int(info["versionCode"])
		current_version_name = str(info.get("versionName", ""))
		if await _whats_new(info):
			_finish()
			return
	await _check_play()
	_finish()

func _finish() -> void:
	checking = false
	_last_check = Time.get_ticks_msec()

func _listen() -> void:
	if _listening:
		return
	_listening = true
	platform.playcore.connect(func(kind: String, json: String):
		if kind == "update_downloaded":
			ui.install_ready(platform.updateComplete)
		elif kind == "update_progress":
			var d = JSON.parse_string(json)
			if d is Dictionary and int(d.get("total", 0)) > 0:
				ui.downloading(float(d["done"]) / float(d["total"])))

func _whats_new(info: Dictionary) -> bool:
	var prior: int = _prefs().get_long("seen_version", -1)
	if prior == current_version:
		return false
	var updated: bool = int(info.get("lastUpdate", 0)) > int(info.get("firstInstall", 0)) + 30000
	if not should_show_whats_new(prior, current_version, updated):
		_prefs().edit().put_long("seen_version", current_version).commit()
		return false
	if not Gate.reserve("whats_new"):
		return false
	_prefs().edit().put_long("seen_version", current_version).commit()
	Gate.presented("whats_new")
	await ui.whats_new(current_version_name, platform.openPlayStore)
	Gate.release("whats_new")
	await _check_play()
	return true

func _check_play() -> void:
	platform.updateCheck()
	var args: Array = await platform.playcore
	while args[0] != "update_info":
		args = await platform.playcore
	var info = JSON.parse_string(str(args[1]))
	if not (info is Dictionary) or not info.get("ok", false):
		return
	if info.get("downloaded", false):
		ui.install_ready(platform.updateComplete)
		return
	if info.get("availability") == "IN_PROGRESS" and info.get("immediate", false):
		platform.updateStart(true)
		return
	if info.get("availability") != "AVAILABLE":
		return
	var urgent: bool = int(info.get("priority", 0)) >= 5 and info.get("immediate", false)
	if not urgent and not info.get("flexible", false):
		return
	var now := int(Time.get_unix_time_from_system() * 1000.0)
	var p = _prefs()
	if not should_prompt(int(info.get("availableVersionCode", 0)), p.get_int("prompt_version", -1), p.get_long("prompt_at", 0), now, urgent) \
			or not Gate.reserve("play_update"):
		return
	p.edit().put_int("prompt_version", int(info["availableVersionCode"])).put_long("prompt_at", now).commit()
	Gate.presented("play_update")
	await ui.prompt(urgent, func(): platform.updateStart(urgent))
	Gate.release("play_update")
