extends RefCounted
## Live AutomaticPromptGate: one unsolicited promotion (update, What's New, review, referral)
## per installation per day and per process session, with a 90 s quiet time after any
## prompt or full-screen interaction. Last time in `automatic_prompts_v2` (live key).

const NativePrefs = preload("res://scripts/game/platform/native_prefs.gd")
const DAY := 86400000
static var active := ""
static var shown_this_session := false
static var quiet_until := 0
## Tests inject a clock (ms epoch).
static var now_ms: Callable = func() -> int: return int(Time.get_unix_time_from_system() * 1000.0)

static func quiet_for(ms := 90000) -> void:
	quiet_until = maxi(quiet_until, Time.get_ticks_msec() + ms)

static func reserve(kind: String) -> bool:
	var now: int = now_ms.call()
	var previous: int = NativePrefs.open("automatic_prompts_v2").get_long("last_at", 0)
	if not active.is_empty() or shown_this_session or Time.get_ticks_msec() < quiet_until \
			or previous > now or (previous > 0 and now / DAY == previous / DAY):
		return false
	active = kind
	return true

static func presented(kind: String) -> void:
	if active != kind:
		return
	shown_this_session = true
	NativePrefs.open("automatic_prompts_v2").edit().put_long("last_at", now_ms.call()).commit()

static func release(kind: String) -> void:
	if active != kind:
		return
	active = ""
	quiet_for()

static func reset_session() -> void:
	active = ""
	shown_this_session = false
	quiet_until = 0
