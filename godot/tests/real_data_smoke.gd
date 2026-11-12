extends SceneTree
## Real data / no samples, through the real UI (docs/PLACEHOLDER_AUDIT.md):
## - no screen renders "Sample", "Preview", "placeholder" or the audited fake names;
## - standard-header routes (incl. Collections, Results) have the wood back + gear header;
## - Profile shows the selected Paw and real stats; a locked Paw cannot be selected;
## - Friends / Leaderboard / Invite / Collections show honest states, Weekly is real;
## - match player cards: text block centred, padded, never over the board (2/3/4 players).
##   godot --headless --path . --script res://tests/real_data_smoke.gd

const Demo = preload("res://scripts/game/tutorial/demo_match.gd")
const AgeEntry = preload("res://scripts/game/safety/age_entry.gd")
const Setup = preload("res://scripts/game/setup/offline_setup.gd")
const Profile = preload("res://scripts/game/profile/profile_store.gd")
const Weekly = preload("res://scripts/game/profile/weekly_challenge_store.gd")
const ProfileHeader = preload("res://scripts/ui/kit/profile_header.gd")
const Card = preload("res://scripts/ui/match/player_card.gd")

const BANNED := ["Sample", "sample", "Preview", "preview", "placeholder", "Placeholder", "Coming soon",
	"FoxRider", "BunnyPop", "BearBuddy", "RaccoonPlay", "PineRaccoon", "BunnyAce", "DeerDash", "OtterLuck",
	"MapleBear", "CloverCat", "HillSquirrel", "MeadowOwl", "MeadowFox", "Challenge preview", "Test price", "0 / 100"]

var failures := 0
var checks := 0

func _check(ok: bool, label: String) -> void:
	checks += 1
	if not ok:
		failures += 1
		printerr("FAIL: ", label)

func _frames(n := 3) -> void:
	for i in n:
		await process_frame

func _texts(node: Node) -> Array:
	var out: Array = []
	for n in node.find_children("*", "", true, false):
		if n is CanvasItem and not n.is_visible_in_tree():
			continue
		for prop in ["text", "label", "title", "tooltip_text"]:
			if prop in n and n.get(prop) is String and not str(n.get(prop)).is_empty():
				out.append(str(n.get(prop)))
	return out

## Live preference files (profile, wallet, settings) in a throwaway folder.
func _isolate_prefs() -> void:
	var NativePrefs = load("res://scripts/game/platform/native_prefs.gd")
	NativePrefs.desktop_dir = "user://real_test_prefs"
	NativePrefs.reset_cache()
	for f in DirAccess.get_files_at("user://real_test_prefs"):
		DirAccess.remove_absolute(ProjectSettings.globalize_path("user://real_test_prefs/" + f))

func _initialize() -> void:
	Demo.flag_path = "user://real_test_tutorial.cfg"
	AgeEntry.default_path = "user://real_test_age.cfg"
	AgeEntry.new().save_choice(30)
	Demo.mark_completed()
	Setup.path = "user://real_test_setup.cfg"
	_isolate_prefs()
	DirAccess.remove_absolute(ProjectSettings.globalize_path(Setup.path))
	var home: Control = load("res://scenes/ui/home/home_screen.tscn").instantiate()
	root.add_child(home)
	await _frames()
	var page: Control = home.preview
	page.state.set_setting("confirm_exit", false)
	# Home: no sample Continue card without a saved game.
	page.go_home()
	await _frames()
	var cont := home.get_node("Canvas/PlayContent/ContinueMatch") as Control
	_check(not cont.visible, "Home shows no Continue Match card without a saved local game")
	# Every route: no sample text.
	var routes := [&"settings", &"profile", &"badges", &"leaderboard", &"invite", &"rewards", &"collections", &"shop", &"wallet",
		&"online", &"team_up", &"friends", &"robot", &"pass_and_play", &"room"]
	for route in routes:
		var tabs: int = {&"badges": 3, &"leaderboard": 2, &"collections": 2, &"shop": 3}.get(route, 1)
		for tab in tabs:
			page.go_home()
			page.open(route)
			if tabs > 1:
				page.select_tab(tab, str(route))
			await _frames()
			var bad := _texts(page).filter(func(t): return BANNED.any(func(w): return t.contains(w)))
			_check(bad.is_empty(), "%s tab %d renders no sample / fake text %s" % [route, tab, bad])
	# Online modes never open a sample match.
	page.go_home()
	page.start_match(&"online")
	await _frames()
	_check(page.current != &"match" and is_instance_valid(page.overlay), "an online match without the service shows the service dialog, not a sample match")
	page.dismiss()
	# Standard header on Collections / Rewards / Shop / Wallet / Results.
	for route in [&"rewards", &"shop", &"wallet", &"collections"]:
		page.go_home()
		page.open(route)
		await _frames()
		var headers := page.find_children("*", "", true, false).filter(func(n): return n.get_script() == ProfileHeader)
		_check(headers.size() == 1, "%s uses the standard moonlit header with back + gear" % route)
	# Honest states for server data; real weekly challenge.
	# Invite has real content without the server (live GameShareLink), so no state card.
	page.go_home()
	page.open(&"invite")
	await _frames()
	_check(page.find_children("*", "Button", true, false).any(func(b): return b.text == "Share Ludo Paws" or str(b.get("label")) == "Share Ludo Paws")
		or page.find_children("*", "", true, false).any(func(n): return str(n.get("label")) == "Share Ludo Paws"), "invite offers the live game share link")
	for route in [&"friends", &"leaderboard"]:
		page.go_home()
		page.open(route)
		await _frames()
		var states := page.find_children("*", "", true, false).filter(func(n): return n.has_meta("state_kind"))
		_check(states.size() == 1 and states[0].get_meta("state_kind") in ["offline", "signin", "empty", "loading"], "%s shows a clear state card" % route)
	Weekly.completed("rd-1", ["dog"])
	page.go_home()
	page.open(&"collections")
	page.select_tab(0, "collections")
	await _frames()
	var weekly_text := " ".join(_texts(page))
	_check(Weekly.TITLES.any(func(t): return weekly_text.contains(t)) and weekly_text.contains("/ %d" % Weekly.status()["target"]), "Weekly Challenge shows the real challenge and progress")
	# Profile: selected Paw portrait, real stats; locked Paws cannot be selected.
	Profile.record_completed_match("rd-m1", "COMPUTER", true, ["dog"])
	var locked := ""
	for id in ["panda", "giraffe", "monkey", "horse"]:
		if not page.state.inventory.is_character_unlocked(id):
			locked = id
			break
	var before: String = page.state.inventory.selected_character()
	_check(locked.is_empty() or not page.state.inventory.select_character(locked), "a locked Paw cannot become the profile portrait")
	page.go_home()
	page.open(&"profile")
	await _frames()
	var portraits := page.find_children("*", "", true, false).filter(func(n): return "character_id" in n and n.is_visible_in_tree())
	_check(portraits.size() >= 1 and portraits[0].character_id == page.state.inventory.selected_character() and before == page.state.inventory.selected_character(),
		"Profile portrait = the selected Paw")
	var profile_text := " ".join(_texts(page))
	_check(profile_text.contains("100%") or profile_text.contains("1"), "Profile stats come from recorded matches")
	# Match cards: centred text block with padding, off the board.
	for n in [2, 3, 4]:
		page.go_home()
		page.state.player_count = n
		page.start_match(&"robot")
		await _frames(4)
		var cards: Dictionary = page.state.get_meta("rail_cache")["cards"]
		var board: Control = page.state.get_meta("board_view")
		var ok := true
		for seat in cards:
			var card: Control = cards[seat]
			if not card.is_visible_in_tree():
				continue
			var r: Rect2 = card.get_global_rect()
			var copy: Rect2 = card._copy.get_global_rect()
			ok = ok and absf(copy.get_center().y - r.get_center().y) <= 3.0 and r.grow(-4.0).encloses(copy) \
				and not r.grow(-1.0).intersects(board.get_global_rect())
		_check(ok, "%dp: card text centred, padded, off the board" % n)
		page.reset_match()
	_check(Card.status_text({"tokens": [57, 57, -1, -1]}, false) == "2/4 HOME" and Card.status_text({"tokens": [5, -1, -1, -1]}, false) == "1 MOVING"
		and Card.status_text({"tokens": [-1, -1, -1, -1], "computer": true}, true) == "CPU TURN", "card line 2 shows the most important fact")
	DirAccess.remove_absolute(ProjectSettings.globalize_path(Setup.path))
	_isolate_prefs()
	print("Real data smoke: %d checks, %d failures" % [checks, failures])
	quit(1 if failures > 0 else 0)
