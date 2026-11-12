extends RefCounted

const Hints = preload("res://scripts/ui/hints/hints.gd")

const ProfileStatsFrame := preload("res://scripts/ui/kit/profile_stats_frame.gd")
const ProfileLevel := preload("res://scripts/ui/kit/profile_level.gd")
const WoodIconButton := preload("res://scripts/ui/kit/wood_icon_button.gd")
const UI = preload("res://scripts/ui/preview/meadow_controls.gd")
const ANIMALS := ["Fox", "Bunny", "Hedgehog", "Deer", "Owl", "Raccoon"]
const ShopPages = preload("res://scripts/ui/preview/shop_pages.gd")
const Profile = preload("res://scripts/game/profile/profile_store.gd")
const Weekly = preload("res://scripts/game/profile/weekly_challenge_store.gd")
const Online = preload("res://scripts/game/online/online_status.gd")
const Catalog = preload("res://scripts/game/store/store_catalog.gd")
const Portrait = preload("res://scripts/game/characters/character_portrait.gd")
const Characters = preload("res://scripts/game/characters/catalog.gd")
const Tutorial = preload("res://scripts/ui/preview/tutorial_pages.gd")
const BADGES := [
	"Ludo Rookie", "Steady Roller", "Win Streaker", "Team Player", "Lucky Roll", "Night Owl",
	"Match Master", "Victory Pro", "Dice Collector", "Streaming Champ", "Friend Circle", "Legend of Meadow",
]

static func build(page) -> void:
	match page.current:
		&"settings": _settings(page)
		&"profile": _profile(page)
		&"badges": _badges(page)
		&"leaderboard": _leaderboard(page)
		&"invite": _invite(page)
		&"rewards": _rewards(page)
		&"collections": _collections(page)
		&"shop": ShopPages.shop(page)
		&"wallet": ShopPages.wallet(page)

static func _settings(page) -> void:
	var body: VBoxContainer = page.body
	body.add_theme_constant_override("separation", 8)
	# Live GameSettingsStore switches (stored in ludoproof_game_settings).
	var audio := _settings_section(body, "Audio", "audio")
	_switch(audio, page, "Music", "music")
	_switch(audio, page, "Sound", "effects")
	_switch(audio, page, "Animal voices", "animal_voices")
	_switch(audio, page, "Haptics", "haptics")
	var gameplay := _settings_section(body, "Gameplay", "dice")
	_speed_options(gameplay, page)
	_switch(gameplay, page, "Play hints", "play_hints")
	_switch(gameplay, page, "Quick chat", "quick_chat")
	_switch(gameplay, page, "Confirm Before Exit", "confirm_exit")
	# Pass & Play: OFF = a short turn banner (default); ON = the live PASS THE PHONE / READY prompt.
	_switch(gameplay, page, "Pass the phone prompt · फ़ोन देने का संकेत", "pass_phone_prompt", false)
	_switch(_settings_section(body, "Accessibility", "profile"), page, "Calm motion", "reduced_motion", false)
	_setting_options(_settings_section(body, "Language", "globe"), page, "", "language", ["English", "हिन्दी"], 0)
	var tutorial := _settings_section(body, "Tutorial", "paw")
	# Replays the first-time Demo match; the completion flag and stats stay unchanged.
	UI.button(tutorial, "Play tutorial again", Tutorial.start_demo.bind(page, true), false, "play", 33)
	var privacy := _settings_section(body, "Privacy", "profile")
	UI.button(privacy, "Privacy Policy", OS.shell_open.bind(PRIVACY_URL), false, "", 33)
	UI.button(privacy, "Online account deletion", OS.shell_open.bind(DELETION_URL), false, "", 33)
	UI.button(privacy, "Ad privacy options", page.state.ads.privacy_options, false, "", 33)
	UI.label(body, "Settings are saved on this device.", 13, true)

static func _settings_section(parent: Control, title: String, icon: String) -> VBoxContainer:
	var content := UI.panel(parent, title, Color("#15382a"), icon)
	content.add_theme_constant_override("separation", 3)
	var padding := StyleBoxEmpty.new()
	padding.content_margin_left = 13
	padding.content_margin_right = 13
	padding.content_margin_top = 7
	padding.content_margin_bottom = 8
	content.get_parent().add_theme_stylebox_override("panel", padding)
	var heading := content.get_child(0) as HBoxContainer
	heading.add_theme_constant_override("separation", 8)
	var emblem := heading.get_child(0) as Control
	emblem.custom_minimum_size = Vector2(27, 26)
	var caption := heading.get_child(1) as Label
	caption.add_theme_font_size_override("font_size", 20)
	return content

static func _settings_row(parent: Control) -> HBoxContainer:
	var divider := HSeparator.new()
	var stroke := StyleBoxLine.new()
	stroke.color = Color("#6b79578c")
	divider.add_theme_stylebox_override("separator", stroke)
	divider.add_theme_constant_override("separation", 1)
	parent.add_child(divider)
	return UI.row(parent, 8)

static func _switch(parent: Control, page, title: String, key: String, initial := true) -> void:
	var line := _settings_row(parent)
	var label := UI.label(line, title, 16)
	label.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	var toggle := CheckButton.new()
	toggle.button_pressed = page.state.setting(key, initial)
	toggle.custom_minimum_size = Vector2(51, 28)
	var active := preload("res://assets/ui/icons/switch_on.svg")
	var inactive := preload("res://assets/ui/icons/switch_off.svg")
	toggle.add_theme_icon_override("checked", active)
	toggle.add_theme_icon_override("unchecked", inactive)
	toggle.add_theme_icon_override("checked_mirrored", active)
	toggle.add_theme_icon_override("unchecked_mirrored", inactive)
	toggle.toggled.connect(_save_toggle.bind(page, key))
	line.add_child(toggle)

static func _save_toggle(active: bool, page, key: String) -> void:
	page.state.set_setting(key, active)

## Live GameSpeed (SLOW / NORMAL / FAST) stored as the live string value.
static func _speed_options(parent: Control, page) -> void:
	var line := _settings_row(parent)
	var label := UI.label(line, "Game Speed", 16)
	label.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	var selector := OptionButton.new()
	var style := UI.surface(Color("#102b20"), Color("#af8c57"), 9)
	style.set_content_margin_all(4)
	style.set_border_width_all(1)
	style.shadow_size = 0
	selector.size_flags_horizontal = Control.SIZE_SHRINK_END
	selector.add_theme_stylebox_override("normal", style)
	selector.add_theme_font_override("font", MeadowStyle.text_font())
	for option in ["Slow", "Normal", "Fast"]:
		selector.add_item(option)
	selector.select(["SLOW", "NORMAL", "FAST"].find(str(page.state.setting("game_speed", "NORMAL"))))
	selector.item_selected.connect(func(i: int): page.state.set_setting("game_speed", ["SLOW", "NORMAL", "FAST"][i]))
	line.add_child(selector)

static func _setting_options(parent: Control, page, title: String, key: String, options: Array, initial: int) -> void:
	var line := _settings_row(parent)
	if not title.is_empty():
		var label := UI.label(line, title, 16)
		label.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	var selector := OptionButton.new()
	var style := UI.surface(Color("#102b20"), Color("#af8c57"), 9)
	style.set_content_margin_all(4)
	style.set_border_width_all(1)
	style.shadow_size = 0
	selector.size_flags_horizontal = Control.SIZE_EXPAND_FILL if title.is_empty() else Control.SIZE_SHRINK_END
	selector.add_theme_stylebox_override("normal", style)
	selector.add_theme_font_override("font", MeadowStyle.text_font())
	for option in options:
		selector.add_item(option)
	selector.select(page.state.setting(key, initial))
	selector.item_selected.connect(_save_option.bind(page, key))
	line.add_child(selector)

static func _save_option(index: int, page, key: String) -> void:
	page.state.set_setting(key, index)

static func _profile(page) -> void:
	var body: VBoxContainer = page.body
	var identity := UI.row(body, 14)
	var portrait_frame := Control.new()
	portrait_frame.custom_minimum_size = Vector2(143, 143)
	portrait_frame.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	identity.add_child(portrait_frame)
	var backing := Panel.new()
	backing.mouse_filter = Control.MOUSE_FILTER_IGNORE
	backing.position = Vector2(12, 12)
	backing.size = Vector2(119, 119)
	backing.add_theme_stylebox_override("panel", UI.surface(Color("#17291e"), Color.TRANSPARENT, 60))
	portrait_frame.add_child(backing)
	# Live ProfileHeaderUi: one portrait only, the player's selected Paw (3D), in the V2 frame.
	var paw: String = page.state.inventory.selected_character()
	var portrait := Portrait.new()
	portrait.character_id = paw
	portrait.zoom = 1.05
	portrait.position = Vector2(12, 12)
	portrait.size = Vector2(119, 119)
	portrait.mouse_filter = Control.MOUSE_FILTER_IGNORE
	portrait_frame.add_child(portrait)
	var rim := UI.image(portrait_frame, preload("res://assets/ui/illustrations/woodland_portrait_frame.svg"), 0)
	rim.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	var about := UI.column(identity, 9)
	about.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	about.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	var name_row := UI.row(about, 4)
	var name_label := UI.label(name_row, Profile.load_state()["displayName"], 22)
	name_label.autowrap_mode = TextServer.AUTOWRAP_OFF
	name_label.text_overrun_behavior = TextServer.OVERRUN_TRIM_ELLIPSIS
	name_label.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	var edit := WoodIconButton.new()
	edit.artwork = preload("res://assets/ui/icons/edit.svg")
	edit.tooltip_text = "Edit name"
	edit.pressed.connect(_edit_name.bind(page))
	name_row.add_child(edit)
	var profile: Dictionary = Profile.load_state()
	var progress := Profile.level_progress(profile["totalXp"])
	var level := ProfileLevel.new()
	level.level = progress["level"]
	level.experience = progress["into"]
	level.next_level_xp = progress["next"]
	about.add_child(level)
	# Live ProfileMasteryUi: the selected Paw's finite mastery tier.
	var played_with := int(profile["characterMatches"].get(paw, 0))
	var goal = Profile.next_goal(played_with)
	UI.label(about, "%s · %s · %d %s%s" % [Characters.display_name(paw), Profile.mastery_tier(played_with), played_with,
		"match" if played_with == 1 else "matches", (" (next %d)" % goal) if goal != null else ""], 13)

	var account := _profile_card(body, 12, 10)
	var account_row := UI.row(account, 12)
	var account_icon := MeadowIcon.new()
	account_icon.glyph = "link"
	account_icon.color = Color("#ffe1a1")
	account_icon.custom_minimum_size = Vector2(42, 42)
	account_icon.medallion = true
	account_icon.shadow = false
	account_row.add_child(account_icon)
	var account_copy := UI.column(account_row, 3)
	account_copy.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	UI.label(account_copy, "Account Link", 19)
	var account_line := UI.label(account_copy, "Guest · This device", 14)
	account_line.autowrap_mode = TextServer.AUTOWRAP_OFF
	account_line.text_overrun_behavior = TextServer.OVERRUN_TRIM_ELLIPSIS
	var status := PanelContainer.new()
	status.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	var status_style := UI.surface(Color("#223a2a"), Color("#647952"), 12)
	status_style.set_content_margin_all(5)
	status.add_theme_stylebox_override("panel", status_style)
	account_row.add_child(status)
	var account_status := UI.label(status, "Guest", 13, true)
	account_status.autowrap_mode = TextServer.AUTOWRAP_OFF
	account_status.add_theme_color_override("font_color", Color("#d8e8bd"))
	# Live refreshAccountStatus: the real session state (guest / Google / offline).
	var service = page.get_node_or_null("/root/Account")
	if service != null:
		var show_status := func() -> void:
			if not is_instance_valid(account_line):
				return
			account_line.text = service.status_text
			account_status.text = "Google" if service.google_linked else ("Guest" if service.signed_in() else "Device")
		service.changed.connect(show_status, CONNECT_REFERENCE_COUNTED)
		account_line.tree_exiting.connect(func(): if service.changed.is_connected(show_status): service.changed.disconnect(show_status))
		show_status.call()
		service.refresh_status()
		service.refresh_play_games()

	var stats_frame := ProfileStatsFrame.new()
	var stat_grid := GridContainer.new()
	stat_grid.columns = 2
	stat_grid.add_theme_constant_override("h_separation", 0)
	stat_grid.add_theme_constant_override("v_separation", 0)
	stats_frame.add_child(stat_grid)
	var icons := [UI.art("match_trophy"), UI.atlas("woodland_badges", 2, UI.icon("coin")), UI.icon("win_rate"), UI.icon("pass_play")]
	var labels := ["Matches Played", "Matches Won", "Win Rate", "Best Streak"]
	var games: int = profile["totalGames"]
	var values := [str(games), str(profile["totalWins"]), ("%d%%" % roundi(100.0 * profile["totalWins"] / games)) if games > 0 else "—", str(profile["bestWinStreak"])]
	for index in labels.size():
		_profile_stat(stat_grid, icons[index], labels[index], values[index])
	body.add_child(stats_frame)
	var manage := UI.button(body, "Manage Account", _account_menu.bind(page), true, "gear", 48)
	manage.custom_minimum_size.x = 248
	manage.size_flags_horizontal = Control.SIZE_SHRINK_CENTER
	var links := UI.row(body)
	UI.button(links, "Badges", page.open.bind(&"badges"), false, "paw")
	UI.button(links, "Leaderboard", page.open.bind(&"leaderboard"), false, "coin")
	UI.button(body, "Invite Friends", page.open.bind(&"invite"), true, "friends")

# ---------------------------------------------------------------- account (live ProfileMenuUi)

const PRIVACY_URL := "https://ludopaws.rekixo.com/privacy-policy.html"
const DELETION_URL := "https://ludopaws.rekixo.com/account-deletion.html"

static func account_menu(page) -> void:
	_account_menu(page)

static func _account_menu(page) -> void:
	var account = page.get_node_or_null("/root/Account")
	if account == null:
		return
	var frame: VBoxContainer = page.dialog("Manage Account", account.status_text, true)
	var session: Dictionary = account.session()
	if session.get("guestRecoveryEligible", false):
		UI.button(frame, "Guest deletion code", _guest_code.bind(page, false), false, "", 44)
		UI.button(frame, "Replace guest deletion code", _confirm.bind(page, "Replace saved guest deletion code?",
			"This invalidates all previously saved guest deletion codes for this account. Save the replacement privately before uninstalling.",
			"Replace code", _guest_code.bind(page, true)), false, "", 44)
	UI.button(frame, "Save / Restore Account", _save_restore.bind(page), true, "link", 46)
	if account.play_games_configured():
		var pgs: Dictionary = account.play_games
		UI.button(frame, ("Play Games · %s" % pgs["name"]) if pgs["connected"] else "Sign in to Google Play Games", _play_games.bind(page), false, "", 44)
	UI.button(frame, "Delete account and data", _confirm.bind(page, "Delete account and data?",
		"This permanently deletes your Ludo Paws account, server wallet, cosmetics, linked Friend ID and leaderboard profile. This cannot be undone. Your local profile and wallet cache for this account will also be cleared.",
		"Delete permanently", _delete_account.bind(page)), false, "", 44)
	var links := UI.row(frame)
	UI.button(links, "Online deletion", OS.shell_open.bind(DELETION_URL), false, "", 40)
	UI.button(links, "Privacy Policy", OS.shell_open.bind(PRIVACY_URL), false, "", 40)
	UI.button(frame, "Close", page.dismiss, false, "", 42)

static func _confirm(page, title: String, message: String, ok_title: String, action: Callable) -> void:
	var frame: VBoxContainer = page.dialog(title, message, true)
	var row := UI.row(frame)
	UI.button(row, "Cancel", page.dismiss, false, "", 44)
	UI.button(row, ok_title, action, true, "", 44)

## Live openAccountActions -> showAccountActions (Google features need the adult choice).
static func _save_restore(page) -> void:
	var account = page.get_node("/root/Account")
	if not account.google_allowed():
		page.notice("Ludo Paws Account", "Google account features are unavailable for this privacy setting. You can continue playing.", true)
		return
	var text := "This account is saved to Google. You can restore it on another device, or choose another Google account only after confirming the switch." if account.google_linked \
		else "Your guest account is currently tied to this installation. Save it to Google so your account and eligible store items can follow you to another device. XP, match statistics and badges stay on this device."
	var frame: VBoxContainer = page.dialog("Ludo Paws Account", text, true)
	UI.button(frame, "Save this account to Google", _google.bind(page, true), true, "", 46)
	UI.button(frame, "Restore an account saved to Google", _confirm.bind(page, "Restore another account?",
		"This will switch the active account on this device. The current guest profile, its Friend ID, leaderboard identity and inventory will not be merged into the restored account. If you have not saved the current guest to Google, cancel and save it first.",
		"Continue", _google.bind(page, false)), false, "", 44)
	UI.button(frame, "Cancel", page.dismiss, false, "", 42)

static func _google(page, link_current: bool) -> void:
	var account = page.get_node("/root/Account")
	page.dismiss()
	var result: Dictionary = await account.google_flow(link_current)
	if is_instance_valid(page) and not str(result.get("message", "")).is_empty():
		page.notice(result["title"], result["message"], true)

static func _guest_code(page, rotate: bool) -> void:
	var account = page.get_node("/root/Account")
	page.dismiss()
	var res: Dictionary = await account.guest_deletion_code(rotate)
	if not is_instance_valid(page):
		return
	if not res["ok"]:
		page.notice("Could not prepare code", res["message"], true)
		return
	var frame: VBoxContainer = page.dialog("Guest deletion code", ("This new code replaces every previously saved guest deletion code for this account. Save it somewhere private before uninstalling. It can delete this guest account only; it cannot sign in or restore your account. Never send it to support or share it with anyone." if rotate
		else "Save this code somewhere private before uninstalling. On the official Ludo Paws deletion page, it can delete this guest account only; it cannot sign in or restore your account. Never send this code to support or share it with anyone."), true)
	var code := LineEdit.new()
	code.text = res["code"]
	code.editable = false
	code.add_theme_font_size_override("font_size", 13)
	frame.add_child(code)
	var row := UI.row(frame)
	UI.button(row, "Copy", func(): DisplayServer.clipboard_set(res["code"]), false, "", 44)
	UI.button(row, "Close", page.dismiss, true, "", 44)

static func _delete_account(page) -> void:
	var account = page.get_node("/root/Account")
	page.dismiss()
	var res: Dictionary = await account.delete_account()
	if not is_instance_valid(page):
		return
	if res["ok"] and res.get("cleanupErrors", []).is_empty():
		page.notice("Account deleted", "Your Ludo Paws account and its server-owned data have been deleted.", true)
	elif res["ok"]:
		page.notice("Account deleted", "The server account was deleted, but this device could not clear: %s. Clear the app's local storage before sharing or handing over this device." % ", ".join(res["cleanupErrors"]), true)
	else:
		page.notice("Account not deleted", str(res.get("message", "The account service could not complete deletion. Your account session has been kept so you can try again.")), true)

static func _play_games(page) -> void:
	var account = page.get_node("/root/Account")
	page.notice("Google Play Games", account.play_games_sign_in(), true)

static func _profile_card(parent: Control, horizontal: int, vertical: int) -> PanelContainer:
	var card := PanelContainer.new()
	var style := UI.surface(Color("#14271c"), Color("#bc8d4b"), 15)
	for side in [SIDE_LEFT, SIDE_RIGHT]:
		style.set_content_margin(side, horizontal)
	for side in [SIDE_TOP, SIDE_BOTTOM]:
		style.set_content_margin(side, vertical)
	card.add_theme_stylebox_override("panel", style)
	parent.add_child(card)
	return card

static func _profile_stat(parent: Control, artwork: Texture2D, title: String, value: String) -> void:
	var cell := PanelContainer.new()
	cell.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	cell.custom_minimum_size.y = 70
	var margins := StyleBoxEmpty.new()
	margins.content_margin_left = 10
	margins.content_margin_right = 10
	cell.add_theme_stylebox_override("panel", margins)
	parent.add_child(cell)
	var line := UI.row(cell, 8)
	var mark := UI.image(line, artwork, 33)
	mark.custom_minimum_size.x = 35
	var text := UI.column(line, 1)
	text.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	text.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	UI.label(text, title, 13)
	UI.label(text, value, 21)

static func _edit_name(page) -> void:
	var dialog: VBoxContainer = page.dialog("Edit Profile", "Choose a display name for this device.")
	var edit := UI.field(dialog, Profile.load_state()["displayName"])
	UI.button(dialog, "Save Name", _save_name.bind(page, edit), true)
	UI.button(dialog, "Cancel", page.dismiss)

static func _save_name(page, edit: LineEdit) -> void:
	var value := edit.text.strip_edges()
	if not value.is_empty():
		Profile.update_display_name(value)
		page.refresh()

static func _badges(page) -> void:
	# Live ProfileProgression.badges: cosmetic badges from this device's real match history.
	var badges: Array = Profile.badges(Profile.load_state())
	var earned_count := badges.filter(func(b): return b["unlocked"]).size()
	var body: VBoxContainer = page.body
	var index: int = page.state.tab_indexes.get("badges", 0)
	_hub_filters(body, page, index, "badges", ["All (%d)" % badges.size(), "Earned (%d)" % earned_count, "Locked (%d)" % (badges.size() - earned_count)])
	body = page.body
	var shown := badges.filter(func(b): return index == 0 or (index == 1) == b["unlocked"])
	if shown.is_empty():
		var empty := UI.panel(body, "No badges yet" if index == 1 else "All badges earned!", Color("#12362a"), "paw")
		UI.label(empty, "Finish a match to earn your first badge." if index == 1 else "Every badge is yours. Well played!", 15, true)
		if index == 1:
			UI.button(empty, "Play", page.go_home, true, "play")
		return
	var grid := GridContainer.new()
	grid.columns = 3
	grid.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	grid.mouse_filter = Control.MOUSE_FILTER_IGNORE
	grid.add_theme_constant_override("h_separation", 9)
	grid.add_theme_constant_override("v_separation", 10)
	body.add_child(grid)
	for badge in shown:
		_badge_card(grid, badges.find(badge), badge["unlocked"], badge)
	var feedback := UI.button(body, "Feedback", _badge_feedback.bind(page), false, "", 37)
	feedback.custom_minimum_size.x = 142
	feedback.size_flags_horizontal = Control.SIZE_SHRINK_CENTER

static func _badge_feedback(page) -> void:
	var dialog: VBoxContainer = page.dialog("Badge Feedback", "Save a note about Badges on this device.")
	var note := TextEdit.new()
	note.custom_minimum_size.y = 98
	note.placeholder_text = "Write your feedback"
	note.text = page.state.setting("badges_feedback", "")
	dialog.add_child(note)
	UI.button(dialog, "Save Note", _save_badge_feedback.bind(page, note), true)
	UI.button(dialog, "Cancel", page.dismiss)

static func _save_badge_feedback(page, note: TextEdit) -> void:
	page.state.set_setting("badges_feedback", note.text.strip_edges())
	page.dismiss()

static func _hub_filters(parent: Control, page, selected: int, group: String, captions: Array) -> void:
	var rail := PanelContainer.new()
	var track := UI.surface(Color("#1b2b21"), Color("#b18d54"), 19)
	track.set_border_width_all(1)
	track.set_content_margin_all(2)
	track.shadow_size = 0
	rail.add_theme_stylebox_override("panel", track)
	parent.add_child(rail)
	var options := UI.row(rail, 1)
	for i in captions.size():
		var tab := Button.new()
		tab.text = captions[i]
		tab.custom_minimum_size.y = 36
		tab.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		tab.add_theme_font_override("font", MeadowStyle.text_font())
		tab.add_theme_font_size_override("font_size", 14)
		tab.add_theme_color_override("font_color", Color("#092b1c") if selected == i else Color("#fff0cc"))
		tab.add_theme_color_override("font_pressed_color", Color("#092b1c"))
		tab.add_theme_color_override("font_hover_color", Color("#092b1c") if selected == i else Color("#ffffff"))
		var selected_style := StyleBoxTexture.new()
		selected_style.texture = preload("res://assets/ui/backgrounds/mint_pill.svg")
		for side in [SIDE_LEFT, SIDE_RIGHT]:
			selected_style.set_texture_margin(side, 16)
		for side in [SIDE_TOP, SIDE_BOTTOM]:
			selected_style.set_texture_margin(side, 12)
		selected_style.set_content_margin_all(2)
		var idle := StyleBoxEmpty.new()
		for state_name in ["normal", "hover", "pressed"]:
			tab.add_theme_stylebox_override(state_name, selected_style if selected == i else idle)
		tab.pressed.connect(page.select_tab.bind(i, group))
		options.add_child(tab)
	# The tab row stays fixed; the list below it scrolls (meadow_preview.begin_list).
	page.begin_list()


static func _badge_card(parent: Control, index: int, earned: bool, badge := {}) -> void:
	var card := PanelContainer.new()
	card.custom_minimum_size.y = 132
	card.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	card.mouse_filter = Control.MOUSE_FILTER_IGNORE
	var border := Color("#ac8648") if earned else Color("#857658")
	var frame := UI.surface(Color("#16281e") if earned else Color("#1a241e"), border, 11)
	frame.set_border_width_all(1)
	frame.set_content_margin_all(6)
	frame.shadow_size = 0
	card.add_theme_stylebox_override("panel", frame)
	parent.add_child(card)
	var content := UI.column(card, 4)
	content.mouse_filter = Control.MOUSE_FILTER_IGNORE
	var stamp := UI.image(content, UI.atlas("woodland_badges", index % 6, UI.icon("paw")), 64)
	if not earned:
		var gray := ShaderMaterial.new()
		gray.shader = preload("res://assets/ui/backgrounds/locked_badge.gdshader")
		stamp.material = gray
		var lock := MeadowIcon.new()
		lock.glyph = "lock"
		lock.color = Color("#ffe6a4")
		stamp.add_child(lock)
		lock.set_anchors_and_offsets_preset(Control.PRESET_CENTER)
		lock.position += Vector2(15, 5)
	var title := UI.label(content, badge.get("title", BADGES[index % BADGES.size()]), 13, true)
	if not badge.is_empty():
		card.tooltip_text = badge["requirement"]
	title.mouse_filter = Control.MOUSE_FILTER_IGNORE
	title.custom_minimum_size.y = 24
	if earned:
		var pill := PanelContainer.new()
		pill.mouse_filter = Control.MOUSE_FILTER_IGNORE
		pill.size_flags_horizontal = Control.SIZE_SHRINK_CENTER
		var chip := UI.surface(Color("#287347"), Color("#5ca46a"), 10)
		chip.set_border_width_all(1)
		chip.set_content_margin_all(3)
		chip.shadow_size = 0
		pill.add_theme_stylebox_override("panel", chip)
		content.add_child(pill)
		var label := UI.label(pill, "✓ Earned", 12, true)
		label.autowrap_mode = TextServer.AUTOWRAP_OFF
	else:
		# Real progress toward the badge (live ProfileBadge.progressCurrent / progressGoal).
		var current: int = badge.get("current", 0)
		var goal: int = badge.get("goal", 1)
		UI.progress(content, current, goal)
		var meter := content.get_child(-1) as ProgressBar
		meter.mouse_filter = Control.MOUSE_FILTER_IGNORE
		meter.custom_minimum_size.y = 10
		UI.label(content, "%d / %d" % [current, goal], 11, true)

static func _leaderboard(page) -> void:
	var body: VBoxContainer = page.begin_list()
	# Live LeaderboardActivity: one global ranking (top 50 + you), server data only, with the
	# last saved ranking when offline.
	var state = page.state
	var account = page.get_node_or_null("/root/Account")
	if account == null or not account.available():
		var copy := Online.copy("The global leaderboard")
		UI.state_card(body, "offline", copy["title"], copy["message"], "Retry", page.refresh)
		return
	if not state.has_meta("leaderboard"):
		state.set_meta("leaderboard", load("res://scripts/game/online/leaderboard_service.gd").new(account))
	var service = state.get_meta("leaderboard")
	var shown = state.get_meta("leaderboard_snapshot") if state.has_meta("leaderboard_snapshot") else null
	if shown == null:
		var saved: Dictionary = service.cached()
		if not saved.is_empty():
			shown = saved["snapshot"]
			state.set_meta("leaderboard_status", "Showing the last saved ranking.")
	if not state.get_meta("leaderboard_loading", false) and Time.get_ticks_msec() - int(state.get_meta("leaderboard_at", -60000)) >= 60000:
		_load_leaderboard(page, service)
	var status: String = state.get_meta("leaderboard_status", "")
	if shown == null:
		if state.get_meta("leaderboard_loading", false):
			UI.state_card(body, "loading", "Loading rankings…", "Fetching the latest leaderboard.")
		else:
			UI.state_card(body, "offline", "Leaderboard", status if not status.is_empty() else "Leaderboard is unavailable right now.", "Retry", func():
				state.set_meta("leaderboard_at", -60000)
				page.refresh())
		return
	if not status.is_empty():
		UI.label(body, status, 13, true)
	var list := UI.panel(body, "Top Players", Color.TRANSPARENT, "trophy")
	if shown["entries"].is_empty():
		UI.label(list, "No ranked players yet. Finish an online match to appear here.", 14, true)
	for e in shown["entries"]:
		_rank_row(list, e)
	if shown["viewer"] != null and not shown["entries"].any(func(x): return x["isViewer"]):
		_rank_row(UI.panel(body, "You", Color.TRANSPARENT, "profile"), shown["viewer"])
	UI.label(body, "Online matches: +%d for finishing, +%d for a win." % [shown["finishPoints"], shown["winBonus"]], 12, true)

static func _load_leaderboard(page, service) -> void:
	var state = page.state
	state.set_meta("leaderboard_loading", true)
	state.set_meta("leaderboard_at", Time.get_ticks_msec())
	var res: Dictionary = await service.refresh()
	state.set_meta("leaderboard_loading", false)
	if res.get("ok", false):
		state.set_meta("leaderboard_snapshot", res["snapshot"])
		state.set_meta("leaderboard_status", "")
	else:
		state.set_meta("leaderboard_status", str(res.get("message", "")))
	if is_instance_valid(page) and page.current == &"leaderboard":
		page.refresh()

static func _rank_row(parent: Control, e: Dictionary) -> void:
	var line := UI.row(parent, 10)
	var rank := UI.label(line, "#%d" % e["rank"] if e["rank"] > 0 else "—", 16)
	rank.custom_minimum_size.x = 46
	var who := UI.column(line, 1)
	who.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	var name := UI.label(who, e["displayName"] + ("  (You)" if e["isViewer"] else ""), 16)
	name.autowrap_mode = TextServer.AUTOWRAP_OFF
	name.text_overrun_behavior = TextServer.OVERRUN_TRIM_ELLIPSIS
	if e["isViewer"]:
		name.add_theme_color_override("font_color", Color("#ffe1a1"))
	UI.label(who, "%d games · %d wins" % [e["games"], e["wins"]], 12)
	_rank_score(line, str(e["points"]))

static func _rank_score(parent: Control, value: String) -> void:
	var cup := UI.image(parent, UI.icon("trophy"), 18)
	cup.custom_minimum_size.x = 19
	var score := UI.label(parent, value, 15)
	score.autowrap_mode = TextServer.AUTOWRAP_OFF

static func _invite(page) -> void:
	var body: VBoxContainer = page.begin_list()
	UI.label(body, "Invite friends to LUDO PAWS\nand play together in Moonlit Meadow!", 17, true)
	# Live GameShareLink: the public game link through the Android share sheet.
	var RoomLink = load("res://scripts/game/online/room_link.gd")
	UI.button(body, "Share Ludo Paws", RoomLink.share.bind(RoomLink.game_share_text(), "Share Ludo Paws"), true, "friends", 52)
	UI.label(body, RoomLink.GAME_SHARE_URL, 14, true)
	_referral_card(page, body)
	UI.button(body, "Play with Friends", page.open.bind(&"friends"), false, "play", 44)

## Live ReferralActivity: invitation rewards (server state) and optional verification.
static func _referral_card(page, body: VBoxContainer) -> void:
	var account = page.get_node_or_null("/root/Account")
	var Referrals = load("res://scripts/game/growth/referral_service.gd")
	var box := UI.panel(body, "Invitation rewards", Color.TRANSPARENT, "gift")
	if not Referrals.adult():
		UI.label(box, "Optional invitation rewards are unavailable for this privacy setting. You can share the game without rewards; gameplay is unchanged.", 13, true)
		return
	if account == null or not account.available():
		UI.label(box, "Offline · you can share the game now. Connect to check invitation rewards.", 13, true)
		return
	var state = page.state
	var s = state.get_meta("referral_status") if state.has_meta("referral_status") else null
	if s == null:
		UI.label(box, "Checking invitation rewards…", 13, true)
		if not state.get_meta("referral_loading", false):
			state.set_meta("referral_loading", true)
			var service = Referrals.new(account, Engine.get_singleton("LudoPawsPlatform") if Engine.has_singleton("LudoPawsPlatform") else null)
			var loaded: Dictionary = await service.status(true)
			state.set_meta("referral_loading", false)
			state.set_meta("referral_status", loaded)
			if is_instance_valid(page) and page.current == &"invite":
				page.refresh()
		return
	UI.label(box, referral_progress(s), 14, true)
	var linked: bool = s.get("googleLinked", false)
	var enabled: bool = s.get("enabled", false)
	var service = Referrals.new(account, Engine.get_singleton("LudoPawsPlatform") if Engine.has_singleton("LudoPawsPlatform") else null)
	var RoomLink = load("res://scripts/game/online/room_link.gd")
	if enabled and linked and not str(s.get("state", "")) in ["REWARDED", "VERIFYING"]:
		UI.button(box, "Invite a friend ↗", func():
			var url: String = await service.invite()
			if url.is_empty():
				page.notice("Invite a friend", "Invitation rewards could not load. You can still share the game; tap Refresh to retry.", true)
			else:
				RoomLink.share("Want to play Ludo Paws with me?\n" + url, "Invite a friend"), true, "friends", 44)
	elif enabled and not linked:
		UI.button(box, "Set up invitation rewards (optional)", page.open.bind(&"profile"), false, "", 40)
	UI.label(box, "Were you invited? Accept the disclosure and verify before playing. Only your inviter receives the reward.", 12, true)
	UI.label(box, referral_verification(str(s.get("verificationState", "NOT_STARTED"))), 12, true)
	if s.get("localConsent", false) or s.get("consented", false):
		UI.button(box, "Verify my invitation", func():
			await service.refresh_attribution(true)
			state.remove_meta("referral_status")
			page.refresh(), false, "", 38).disabled = not (s.get("verificationEnabled", enabled) and linked)
		UI.button(box, "Stop verification", func():
			await service.withdraw()
			state.remove_meta("referral_status")
			page.refresh(), false, "", 36)
	elif linked and enabled:
		UI.button(box, "Agree & verify (optional)", func():
			var frame: VBoxContainer = page.dialog("Were you invited?", REFERRAL_DISCLOSURE, true)
			UI.button(frame, "Read rules & privacy", OS.shell_open.bind("https://ludopaws.rekixo.com/referral-terms.html"), false, "", 38)
			UI.button(frame, "Agree & continue", func():
				page.dismiss()
				await service.enroll()
				await service.refresh_attribution(true)
				state.remove_meta("referral_status")
				page.refresh(), true, "", 42)
			UI.button(frame, "Not now", page.dismiss, false, "", 38), false, "", 38)
	var links := UI.row(box)
	UI.button(links, "Rules & privacy", OS.shell_open.bind("https://ludopaws.rekixo.com/referral-terms.html"), false, "", 34)
	UI.button(links, "Refresh", func():
		state.remove_meta("referral_status")
		page.refresh(), false, "", 34)

const REFERRAL_DISCLOSURE := "Optional Google Play verification uses your linked game account, Google-identity anti-replay hash, click/install times and app/device integrity signals. Google verifies temporary tokens; we do not store those tokens. Our backend stores consent and eligible match/reward records.\n\nNo contacts or advertising ID are requested. Decline and keep playing, or stop verification later. Minimal security hashes remain until 14 days after the campaign ends. Only the inviter receives the reward."

## Live ReferralActivity progress text.
static func referral_progress(s: Dictionary) -> String:
	var state := str(s.get("state", ""))
	if state == "REWARDED":
		return "Bundle received!\nYour items and 100 Gems are in your account." if s.get("walletSynced", false) else "Reward granted on the server. Refresh to sync your items and Gems."
	if not s.get("enabled", false):
		return "Rewards are currently unavailable."
	if not s.get("googleLinked", false):
		return "Share the game now, or optionally connect Google below to set up invitation rewards."
	match state:
		"VERIFYING": return "Match verified · delivering your reward…"
		"FRIEND_JOINED": return "Friend joined! Next: a completed real match."
	return "Ready for your first invitation."

## Live ReferralActivity verification text.
static func referral_verification(v: String) -> String:
	match v:
		"VERIFYING": return "Checking your Google Play installation… You can keep playing; no local reward will be granted."
		"NO_INVITATION": return "No valid invitation was found for this installation. An existing install cannot be converted into a new referral."
		"INSTALL_ALREADY_USED": return "This installation was already used or its pending attribution was cancelled. It cannot be bound again to a different account."
		"NOT_ELIGIBLE": return "This installation is not eligible for this invitation. See the rules or contact AI@rekixo.com."
		"PLAY_NOT_VERIFIED": return "Google Play could not verify this installation. Use the genuine licensed Play release on a supported device, then retry."
		"REVIEW_TERMS": return "Review and accept the updated invitation terms before verifying."
		"STORE_UNAVAILABLE", "RETRY_LATER", "UNAVAILABLE": return "Verification is temporarily unavailable. Your pending invitation is saved; use Verify to retry."
		"STOPPED": return "Optional verification stopped. Existing earned rewards remain unchanged."
		"BOUND": return "Invitation verified. Finish a qualifying Online or Friends match. Your inviter gets the reward."
	return ""

## Server rewards are loaded in the background; the page draws the last response and
## redraws when a fresh one arrives (no rebuild loop: one load per 30 s or per claim).
const REWARDS_TTL_MS := 30000

static func _load_rewards(page) -> void:
	var state = page.state
	state.set_meta("rewards_loaded_at", Time.get_ticks_msec())
	state.set_meta("rewards_loading", true)
	var res: Dictionary = await state.store.rewards_status()
	state.set_meta("rewards_loading", false)
	state.set_meta("rewards_error", "" if res.get("ok", false) else str(res.get("message", "Rewards are not available right now.")))
	if is_instance_valid(page) and page.current == &"rewards":
		page.refresh()

static func _rewards(page) -> void:
	var body: VBoxContainer = page.body
	# Server-authoritative engagement rewards (live RewardsActivity / engagement-rewards.js).
	var state = page.state
	var status: Dictionary = state.store.cached_rewards()
	if not state.get_meta("rewards_loading", false) and Time.get_ticks_msec() - int(state.get_meta("rewards_loaded_at", -REWARDS_TTL_MS)) >= REWARDS_TTL_MS:
		_load_rewards(page)
	if status.is_empty():
		var error: String = state.get_meta("rewards_error", "")
		var box := UI.panel(body, "Rewards", Color.TRANSPARENT, "gift")
		UI.label(box, "Loading today's rewards…" if error.is_empty() or state.get_meta("rewards_loading", false) else error, 15, true)
		if not error.is_empty():
			UI.button(box, "Retry", func():
				state.set_meta("rewards_loaded_at", -REWARDS_TTL_MS)
				page.refresh(), true, "", 40)
		return
	# V2 Rewards page (rewards_page.gd): chest, wheel, ad bonus; each card updates alone.
	RewardsPage.build(page, body, status)
	UI.button(body, "Weekly Challenge & Collections", page.open.bind(&"collections"), true, "gift")

static func _collections(page) -> void:
	var body: VBoxContainer = page.body
	var index: int = page.state.tab_indexes.get("collections", 0)
	_hub_filters(body, page, index, "collections", ["Weekly Challenge", "Collections"])
	body = page.body
	if index == 0:
		_weekly(page, body)
	else:
		_collection_list(page, body)

## Live WeeklyChallengeStore (device-local): this week's challenge, progress and reset.
static func _weekly(page, body: VBoxContainer) -> void:
	var w := Weekly.status()
	var weekly := UI.panel(body, w["title"], Color.TRANSPARENT, "trophy")
	weekly.add_theme_constant_override("separation", 7)
	var feature := UI.row(weekly, 8)
	var description := UI.label(feature, w["goal"], 16)
	description.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	var gift := UI.image(feature, UI.icon("gift"), 57)
	gift.custom_minimum_size.x = 62
	UI.progress(weekly, w["progress"], w["target"])
	var left_ms: int = w["ends_at_ms"] - Weekly.now_ms()
	var days := int(left_ms / 86400000.0)
	var hours := int((left_ms % 86400000) / 3600000.0)
	UI.label(weekly, "%d / %d · %s" % [w["progress"], w["target"], "Completed! Badge earned" if w["complete"] else "Resets in %dd %dh" % [days, hours]], 14, true)
	UI.label(body, "Collection badges earned: %d" % w["badges"], 15, true)

## Live COLLECTIONS (server engagement-rewards.js): bundles of a Paw, a board and a dice;
## progress = owned items; price = collectionQuote (missing value at the bundle discount).
const COLLECTIONS := [
	["dawn-adventures", "Dawn Adventures", ["character_horse", "board_dawn_meadow", "dice_pearl_paw"], 550, 650],
	["moonlit-friends", "Moonlit Friends", ["character_cock", "board_moonlit_grove", "dice_pearl_paw"], 550, 650],
	["garden-friends", "Garden Friends", ["character_rabbit", "board_blossom_garden", "dice_pearl_paw"], 600, 700],
	["starlight-pals", "Starlight Pals", ["character_elephant", "board_astral", "dice_pearl_paw"], 600, 700],
	["rainy-day", "Rainy Day Pals", ["character_pig", "board_monsoon_mire", "dice_pearl_paw"], 600, 700],
]

static func _offer(page, id: String) -> Variant:
	for category in [Catalog.CHARACTER_PACK, Catalog.BOARD, Catalog.DICE]:
		for o in page.state.store.catalog.for_category(category):
			if o["id"] == id:
				return o
	return null

## The server's quoted collection from the last Rewards response (id, missing, price).
static func _server_collection(page, id: String) -> Variant:
	for c in page.state.store.cached_rewards().get("collections", []):
		if c is Dictionary and c.get("id") == id:
			return c
	return null

static func _confirm_collection(page, c: Dictionary) -> void:
	var frame: VBoxContainer = page.dialog(str(c.get("title", "Collection")), "Get the missing items for %d Gems?" % int(c.get("price", 0)), true)
	var row := UI.row(frame)
	UI.button(row, "Cancel", page.dismiss, false, "", 44)
	UI.button(row, "Get", _buy_collection.bind(page, c), true, "", 44)

static func _buy_collection(page, c: Dictionary) -> void:
	page.dismiss()
	var result: Dictionary = await page.state.store.buy_collection(str(c["id"]), int(c.get("price", 0)))
	if not is_instance_valid(page):
		return
	if result.get("ok", false):
		page.notice("Collection", "Collection saved.", true)
	else:
		var code := str(result.get("error", ""))
		page.notice("Collection", "Not enough Gems for these missing items." if code == "NOT_ENOUGH_GEMS" else (
			"Ownership or price changed. Refresh the collection before purchasing." if code == "COLLECTION_PRICE_CHANGED" else str(result.get("message", "Try again later."))), true)
	page.refresh()

static func collection_quote(items: Array, owned: Array, prices: Dictionary, price: int, full_price: int) -> Dictionary:
	var missing := items.filter(func(id): return not owned.has(id))
	var value := 0
	for id in missing:
		value += int(prices.get(id, 0))
	return {"missing": missing, "price": ceili(value * price / float(full_price))}

static func _collection_list(page, body: VBoxContainer) -> void:
	for c in COLLECTIONS:
		var items: Array = c[2]
		var offers := items.map(func(id): return _offer(page, id))
		var available := offers.all(func(o): return o != null and o.get("contentAvailable", true) and o.get("purchaseAvailable", true))
		var owned := items.filter(func(id): return ShopPages.owned(page.state, _offer(page, id)) if _offer(page, id) != null else false)
		var prices := {}
		for o in offers:
			if o != null:
				prices[o["id"]] = int(o.get("gemPrice", 0))
		var card := UI.panel(body)
		var line := UI.row(card, 8)
		var details := UI.column(line, 4)
		details.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		UI.label(details, c[1], 17)
		UI.label(details, " · ".join(items.map(func(id): return _item_name(page, id))), 12)
		UI.progress(details, owned.size(), items.size())
		var status := "Complete" if owned.size() == items.size() else (
			"%d Gems for the rest" % collection_quote(items, owned, prices, c[3], c[4])["price"] if available else "Not available right now")
		UI.label(details, "%d / %d owned · %s" % [owned.size(), items.size(), status], 12)
		# One compact pill on the card's right, centred; never for retired / unavailable items.
		var server = _server_collection(page, c[0])
		if available and owned.size() < items.size():
			var price: int = int(server.get("price", 0)) if server != null else int(collection_quote(items, owned, prices, c[3], c[4])["price"])
			var action: Callable = _confirm_collection.bind(page, server) if server != null \
				else page.notice.bind(c[1], "Open Rewards while connected to load today's collection prices.", true)
			collection_get_button(line, price, action)

## The collection "Get" pill: ~72 x 36, centred vertically and horizontally in its slot.
static func collection_get_button(parent: Control, price: int, action: Callable) -> Button:
	var get := UI.button(parent, "Get · %d" % price, action, true, "gem", 36)
	get.custom_minimum_size = Vector2(72, 36)
	get.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	get.size_flags_horizontal = Control.SIZE_SHRINK_CENTER
	get.set_meta("collection_get", true)
	return get

static func _item_name(page, id: String) -> String:
	var o = _offer(page, id)
	return str(o["title"]).capitalize() if o != null else id.get_slice("_", 1).capitalize()

static func _open_shop(page, tab: int) -> void:
	page.state.tab_indexes["shop"] = tab
	page.open_nested(&"shop")

static func _product(page, name: String, art: Texture2D) -> void:
	var card: VBoxContainer = page.dialog(name, "Collection preview", true)
	UI.image(card, art, 175)
	UI.button(card, "Close", page.dismiss, true)

