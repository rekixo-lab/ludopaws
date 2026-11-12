extends RefCounted
## Live EngagementStore (`engagement_v1`, per owner = account id or "installation"): completed
## matches / days for the natural review rule. Local only, never a wallet authority.

const NativePrefs = preload("res://scripts/game/platform/native_prefs.gd")
const DAY := 86400000
static var account_id: Callable = func() -> String:
	var tree := Engine.get_main_loop() as SceneTree
	var account = tree.root.get_node_or_null("Account") if tree != null else null
	return str(account.session().get("accountId", "")) if account != null else ""

static func _owner() -> String:
	var id := str(account_id.call())
	return "installation" if id.is_empty() else id

static func completed(match_id: String, completed_at := -1) -> void:
	var at := completed_at if completed_at > 0 else int(Time.get_unix_time_from_system() * 1000.0)
	if match_id.strip_edges().is_empty():
		return
	var key := _owner()
	if NativePrefs.open("ludoproof_profile").get_string_set("deleted_profile_accounts", []).has(key):
		return
	var p = NativePrefs.open("engagement_v1")
	var ids: Array = p.get_string_set(key + ":matches", [])
	if ids.has(match_id):
		return
	ids.append(match_id)
	while ids.size() > 128:
		ids.pop_front()
	var day := at / DAY
	var days: Array = p.get_string_set(key + ":days", [])
	if not days.has(str(day)):
		days.append(str(day))
	days.sort_custom(func(a, b): return int(a) < int(b))
	var previous_day: int = p.get_long(key + ":today", -1)
	var count: int = p.get_int(key + ":todayCount", 0) + 1 if previous_day == day else 1
	var e = p.edit().put_string_set(key + ":matches", ids).put_string_set(key + ":days", days.slice(-366))
	if day >= previous_day:
		e.put_long(key + ":today", day).put_int(key + ":todayCount", count)
		if count == 1:
			e.put_string(key + ":todayFirstMatch", match_id)
	e.commit()

static func completed_matches() -> int: return NativePrefs.open("engagement_v1").get_string_set(_owner() + ":matches", []).size()
static func completed_days() -> int: return NativePrefs.open("engagement_v1").get_string_set(_owner() + ":days", []).size()
static func last_review_attempt() -> int: return NativePrefs.open("engagement_v1").get_long(_owner() + ":reviewAttempt", 0)
static func review_attempt(at_ms: int) -> void: NativePrefs.open("engagement_v1").edit().put_long(_owner() + ":reviewAttempt", at_ms).commit()
