extends RefCounted
## Live OfflineResultRecovery: a finished local match result is written to
## `ludopaws_pending_local_results_v1` before the profile / weekly stores record it, and
## retried at launch, so a crash or failed write never loses the statistics.

const NativePrefs = preload("res://scripts/game/platform/native_prefs.gd")
const Profile = preload("res://scripts/game/profile/profile_store.gd")
const Weekly = preload("res://scripts/game/profile/weekly_challenge_store.gd")
const PREFS := "ludopaws_pending_local_results_v1"

static func record(match_id: String, owner: String, won: bool, computer: bool, characters: Array) -> void:
	var p = NativePrefs.open(PREFS)
	var raw := str(p.get_string(match_id, ""))
	var existing = JSON.parse_string(raw) if not raw.is_empty() else null
	var completed_at := int(existing.get("completedAt", 0)) if existing is Dictionary else 0
	var result := {"owner": owner if not owner.is_empty() else null, "won": won, "computer": computer, "characters": characters,
		"completedAt": completed_at if completed_at > 0 else int(Time.get_unix_time_from_system() * 1000.0)}
	if p.edit().put_string(match_id, JSON.stringify(result)).commit():
		_apply(match_id, result)

static func retry() -> void:
	var p = NativePrefs.open(PREFS)
	for match_id in p.keys():
		var result = JSON.parse_string(str(p.get_string(match_id, "{}")))
		if result is Dictionary:
			_apply(match_id, result)

static func _apply(match_id: String, result: Dictionary) -> void:
	var owner := str(result.get("owner", "")) if result.get("owner") != null else ""
	var characters: Array = Array(result.get("characters", [])).map(func(v): return str(v))
	# Pinned to the account that played (live pinAccountScope).
	var profile_owner: Callable = Profile.account_id
	var weekly_owner: Callable = Weekly.account_id
	Profile.account_id = func() -> String: return owner
	Weekly.account_id = func() -> String: return owner
	Profile.record_completed_match(match_id, "COMPUTER" if result.get("computer", false) else "LOCAL", bool(result.get("won", false)), characters)
	# Live: the recovery path also records the neutral engagement milestone (review rule).
	var Engagement = load("res://scripts/game/growth/engagement_store.gd")
	var engagement_owner: Callable = Engagement.account_id
	Engagement.account_id = func() -> String: return owner
	Engagement.completed(match_id, int(result.get("completedAt", 0)))
	Engagement.account_id = engagement_owner
	if result.has("completedAt"):
		Weekly.completed(match_id, characters, int(result["completedAt"]))
	Profile.account_id = profile_owner
	Weekly.account_id = weekly_owner
	NativePrefs.open(PREFS).edit().remove(match_id).commit()
