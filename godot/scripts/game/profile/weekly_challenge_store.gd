extends RefCounted
## Device-local weekly collection challenge (port of live WeeklyChallengeStore): weeks start
## Monday 00:00 UTC; three rotating challenges; a completed week adds a collection badge.
## Never a wallet, ranking or competitive reward authority. Stored like live in the
## `weekly_challenges_v1` preference file per owner (account id, else "installation"):
## "{owner}:week:{w}" JSON, "{owner}:badges" string set, "{owner}:seen" {"ids": [...]}.

const Characters = preload("res://scripts/game/characters/catalog.gd")
const NativePrefs = preload("res://scripts/game/platform/native_prefs.gd")
const PREFS := "weekly_challenges_v1"
const DAY_MS := 86400000
const TITLES := ["Paws Party", "Two-Day Adventure", "Meet the Pals"]
const GOALS := ["Finish 3 matches", "Finish a match on 2 different days", "Finish matches using 3 different pals"]
## Callable() -> String: the signed-in account id ("" = this installation).
static var account_id: Callable = _session_account

static func _session_account() -> String:
	var tree := Engine.get_main_loop() as SceneTree
	var account = tree.root.get_node_or_null("Account") if tree != null else null
	return str(account.session().get("accountId", "")) if account != null else ""

static func _owner() -> String:
	var id := str(account_id.call())
	return "installation" if id.is_empty() else id

static func week(time_ms: int) -> int:
	return floori((floori(time_ms / float(DAY_MS)) + 3) / 7.0)

static func _read(p, key: String) -> Dictionary:
	var raw = p.get_string(key, "{}")
	var parsed = JSON.parse_string(str(raw))
	return parsed if parsed is Dictionary else {}

static func now_ms() -> int:
	return int(Time.get_unix_time_from_system() * 1000.0)

## {title, goal, progress, target, ends_at_ms, badges, complete}
static func status(at_ms := -1) -> Dictionary:
	var t := at_ms if at_ms >= 0 else now_ms()
	var w := week(t)
	var p = NativePrefs.open(PREFS)
	var owner := _owner()
	var data := _read(p, "%s:week:%d" % [owner, w])
	var kind := posmod(w, 3)
	var count: int = [Array(data.get("matches", [])).size(), Array(data.get("days", [])).size(), Array(data.get("characters", [])).size()][kind]
	var target := 2 if kind == 1 else 3
	return {"title": TITLES[kind], "goal": GOALS[kind], "progress": mini(count, target), "target": target,
		"ends_at_ms": ((w + 1) * 7 - 3) * DAY_MS, "badges": p.get_string_set(owner + ":badges", []).size(), "complete": count >= target}

static func completed(match_id: String, characters: Array, completed_at_ms := -1) -> void:
	var t := completed_at_ms if completed_at_ms >= 0 else now_ms()
	if match_id.strip_edges().is_empty() or t <= 0:
		return
	var owner := _owner()
	if NativePrefs.open("ludoproof_profile").get_string_set("deleted_profile_accounts", []).has(owner):
		return
	var p = NativePrefs.open(PREFS)
	var seen: Array = Array(_read(p, owner + ":seen").get("ids", [])).map(func(v): return str(v))
	if seen.has(match_id):
		return
	var w := week(t)
	# Delayed recovery is never silently counted in a new week's challenge.
	if w != week(now_ms()):
		seen.append(match_id)
		p.edit().put_string(owner + ":seen", JSON.stringify({"ids": seen.slice(-2048)})).commit()
		return
	var key := "%s:week:%d" % [owner, w]
	var data := _read(p, key)
	var ids: Array = Array(data.get("matches", [])).map(func(v): return str(v))
	if ids.has(match_id):
		return
	ids.append(match_id)
	while ids.size() > 512:
		ids.pop_front()
	var days: Array = Array(data.get("days", [])).map(func(v): return str(v))
	var day := str(floori(t / float(DAY_MS)))
	if not days.has(day):
		days.append(day)
	var pals: Array = Array(data.get("characters", [])).map(func(v): return str(v))
	for c in characters:
		if Characters.is_known(c) and not pals.has(Characters.canonical_id(c)):
			pals.append(Characters.canonical_id(c))
	var kind := posmod(w, 3)
	var complete: bool = [ids.size() >= 3, days.size() >= 2, pals.size() >= 3][kind]
	var badges: Array = p.get_string_set(owner + ":badges", [])
	if complete and not badges.has(str(w)):
		badges.append(str(w))
	seen.append(match_id)
	while seen.size() > 2048:
		seen.pop_front()
	var e = p.edit().put_string(key, JSON.stringify({"matches": ids, "days": days, "characters": pals})) \
		.put_string_set(owner + ":badges", badges).put_string(owner + ":seen", JSON.stringify({"ids": seen}))
	for k in p.keys():
		if k.begins_with(owner + ":week:") and k != key:
			e.remove(k)
	e.commit()
