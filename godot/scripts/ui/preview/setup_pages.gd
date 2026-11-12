extends RefCounted
## Robot / Pass & Play setup (live behaviour, compact V2 Moonlit Meadow look) — one screen:
## players (+ live human / robot mix by re-tapping), a seat chip grid,
## a horizontal Paw carousel (all 14 Paws, locked ones previewable), colour dots and a fixed
## Start Game right below. Selections update in place (no page rebuild) and are saved immediately.
## Logic: offline_setup.gd + starter_assignment.gd. See docs/SETUP_PARITY.md.

const UI = preload("res://scripts/ui/preview/meadow_controls.gd")
const Setup = preload("res://scripts/game/setup/offline_setup.gd")
const Starter = preload("res://scripts/game/characters/starter_assignment.gd")
const Characters = preload("res://scripts/game/characters/catalog.gd")
const Portrait = preload("res://scripts/game/characters/character_portrait.gd")
const Card = preload("res://scripts/ui/match/player_card.gd")
const Catalog = preload("res://scripts/game/store/store_catalog.gd")
const Hints = preload("res://scripts/ui/hints/hints.gd")

const UNLOCK_LABELS := {"GEMS": "%d Gems", "PLAY_PRODUCT": "Buy", "WATCH_AD": "Watch Ad", "CLAIM_ADS": "Unlock",
	"GET_GEMS": "Get Gems"}

var page
var mode := "COMPUTER"
var s: Dictionary              # the saved setup snapshot
var _count_buttons := {}       # count -> MeadowButton
var _mix: Label
var _chips_row: GridContainer
var _chips: Array = []         # [{panel, portrait, dot, label}]
var _carousel: KineticScroll
var _cards := {}               # id -> {button, portrait, badge, name, sub, lock}
var _paw_title: Label
var _color_buttons := {}       # name -> Button
var _color_title: Label

static func build(page, robot: bool) -> void:
	var view := new()
	view.page = page
	view.mode = "COMPUTER" if robot else "PASS_AND_PLAY"
	view._load()
	page.state.set_meta("setup_view", view)
	view._build()

func _unlocked() -> Array:
	return page.state.inventory.unlocked_character_ids()

func _load() -> void:
	s = Setup.load_setup(mode, _unlocked(), page.state.inventory.selected_character())
	_sync_state()

## Saves on every selection (live) and keeps the page state in step for the match start.
func _save() -> void:
	s = Setup.save_setup(mode, s, _unlocked())
	_sync_state()

func _sync_state() -> void:
	page.state.player_count = s["player_count"]

func computer() -> bool:
	return mode == "COMPUTER"

func seat_label(seat: int) -> String:
	if not computer():
		return str(s["names"][seat])
	if seat == 0:
		return "You"
	if seat < int(s["humans"]):
		return "Player %d" % (seat + 1)
	return "Robot %d" % (seat - int(s["humans"]) + 1)

# ---------------------------------------------------------------- build (once)

func _build() -> void:
	var body: VBoxContainer = page.body
	body.add_theme_constant_override("separation", 7)
	# Players (+ the live human / robot mix in Robot mode).
	var players := _section(body, "Players", "friends")
	var counts := UI.row(players, 6)
	for n in [2, 3, 4]:
		var b := MeadowButton.new()
		b.label = str(n)
		b.font_size = 22
		b.custom_minimum_size = Vector2(0, 44)
		b.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		b.pressed.connect(_on_count.bind(n))
		counts.add_child(b)
		_count_buttons[n] = b
	if computer():
		_mix = UI.label(players, "", 12, true)
	# Seat chips.
	_chips_row = GridContainer.new()
	_chips_row.add_theme_constant_override("h_separation", 6)
	_chips_row.add_theme_constant_override("v_separation", 6)
	body.add_child(_chips_row)
	_build_chips()
	# Paw carousel.
	var paws := _section(body, "", "paw")
	_paw_title = paws.get_meta("title")
	_carousel = KineticScroll.new()
	_carousel.horizontal = true
	_carousel.size_flags_vertical = Control.SIZE_SHRINK_BEGIN
	_carousel.custom_minimum_size.y = 122
	paws.add_child(_carousel)
	var strip := HBoxContainer.new()
	strip.add_theme_constant_override("separation", 6)
	_carousel.add_child(strip)
	for id in Starter.starter_ids():
		_cards[id] = _paw_card(strip, id)
	# Colour dots.
	var colors := _section(body, "", "paw")
	_color_title = colors.get_meta("title")
	var dots := UI.row(colors, 10)
	dots.alignment = BoxContainer.ALIGNMENT_CENTER
	for name in Setup.COLORS:
		var b := Button.new()
		b.custom_minimum_size = Vector2(54, 54)
		b.focus_mode = Control.FOCUS_NONE
		b.tooltip_text = name.capitalize()
		b.pressed.connect(_on_color.bind(name))
		dots.add_child(b)
		_color_buttons[name] = b
	# Start Game right after the colours (not pinned to the screen's bottom edge).
	var lobby = load("res://scripts/ui/preview/lobby_pages.gd")
	lobby._start_button(body, page.start_match.bind(page.current))
	refresh()
	_reveal_selected.call_deferred()

func _section(parent: Control, title: String, icon: String) -> VBoxContainer:
	var card := UI.panel(parent, "", Color("#12362a"))
	card.add_theme_constant_override("separation", 5)
	var panel := card.get_parent() as Control
	var pad := StyleBoxEmpty.new()
	pad.content_margin_left = 10
	pad.content_margin_right = 10
	pad.content_margin_top = 6
	pad.content_margin_bottom = 7
	panel.add_theme_stylebox_override("panel", pad)
	var head := UI.row(card, 6)
	var glyph := MeadowIcon.new()
	glyph.glyph = icon
	glyph.custom_minimum_size = Vector2(22, 22)
	head.add_child(glyph)
	var label := UI.label(head, title, 16)
	label.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	card.set_meta("title", label)
	return card

func _build_chips() -> void:
	for c in _chips:
		c["panel"].queue_free()
	_chips.clear()
	# Two chips per row (2x2 at 3-4 seats), each roomy enough for portrait, name and Paw.
	_chips_row.columns = 2
	var tight := false
	for seat in int(s["player_count"]):
		var panel := Button.new()
		panel.focus_mode = Control.FOCUS_NONE
		panel.custom_minimum_size = Vector2(0, 56)
		panel.clip_contents = true
		panel.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		panel.pressed.connect(_on_seat.bind(seat))
		_chips_row.add_child(panel)
		var row := HBoxContainer.new()
		row.mouse_filter = Control.MOUSE_FILTER_IGNORE
		row.add_theme_constant_override("separation", 1 if tight else 3)
		row.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
		row.offset_left = 2 if tight else 4
		row.offset_right = -2 if tight else -4
		panel.add_child(row)
		var portrait := Portrait.new()
		portrait.snapshot = true
		portrait.zoom = 1.15
		portrait.custom_minimum_size = Vector2(46, 46)
		portrait.size_flags_vertical = Control.SIZE_SHRINK_CENTER
		portrait.mouse_filter = Control.MOUSE_FILTER_IGNORE
		row.add_child(portrait)
		var copy := VBoxContainer.new()
		copy.mouse_filter = Control.MOUSE_FILTER_IGNORE
		copy.add_theme_constant_override("separation", 0)
		copy.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		copy.alignment = BoxContainer.ALIGNMENT_CENTER
		row.add_child(copy)
		var label := UI.label(copy, "", 14)
		label.autowrap_mode = TextServer.AUTOWRAP_OFF
		label.clip_text = true
		label.text_overrun_behavior = TextServer.OVERRUN_TRIM_ELLIPSIS
		label.mouse_filter = Control.MOUSE_FILTER_IGNORE
		var paw := UI.label(copy, "", 12)
		paw.autowrap_mode = TextServer.AUTOWRAP_OFF
		paw.add_theme_color_override("font_color", MeadowStyle.MINT)
		paw.clip_text = true
		paw.text_overrun_behavior = TextServer.OVERRUN_TRIM_ELLIPSIS
		paw.mouse_filter = Control.MOUSE_FILTER_IGNORE
		var chip := {"panel": panel, "portrait": portrait, "label": label, "paw": paw}
		if not computer():
			# Pass & Play: a small pencil renames the seat (no always-open text field).
			var pencil := Button.new()
			pencil.flat = true
			pencil.focus_mode = Control.FOCUS_NONE
			pencil.custom_minimum_size = Vector2(22, 22)
			pencil.size_flags_vertical = Control.SIZE_SHRINK_CENTER
			pencil.tooltip_text = "Rename"
			pencil.pressed.connect(_rename.bind(seat))
			var icon := MeadowIcon.new()
			icon.glyph = "pencil"
			icon.mouse_filter = Control.MOUSE_FILTER_IGNORE
			icon.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
			pencil.add_child(icon)
			row.add_child(pencil)
		_chips.append(chip)

func _paw_card(strip: Control, id: String) -> Dictionary:
	var button := Button.new()
	button.focus_mode = Control.FOCUS_NONE
	button.custom_minimum_size = Vector2(76, 112)
	button.pressed.connect(_on_paw.bind(id))
	strip.add_child(button)
	var column := VBoxContainer.new()
	column.mouse_filter = Control.MOUSE_FILTER_IGNORE
	column.add_theme_constant_override("separation", 0)
	column.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	column.offset_top = 3
	button.add_child(column)
	var art := Control.new()
	art.custom_minimum_size = Vector2(0, 66)
	art.mouse_filter = Control.MOUSE_FILTER_IGNORE
	column.add_child(art)
	var portrait := Portrait.new()
	portrait.snapshot = true   # cached still: the carousel never re-renders 3D while scrolling
	portrait.character_id = id
	portrait.zoom = 1.1
	portrait.mouse_filter = Control.MOUSE_FILTER_IGNORE
	portrait.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	art.add_child(portrait)
	# Team badge: the seat colour of whoever has this Paw (live showTeamBadge).
	var badge := Panel.new()
	badge.mouse_filter = Control.MOUSE_FILTER_IGNORE
	badge.custom_minimum_size = Vector2(16, 16)
	badge.size = Vector2(16, 16)
	badge.position = Vector2(54, 48)
	art.add_child(badge)
	var lock := MeadowIcon.new()
	lock.glyph = "lock"
	lock.mouse_filter = Control.MOUSE_FILTER_IGNORE
	lock.size = Vector2(20, 20)
	lock.position = Vector2(4, 4)
	art.add_child(lock)
	var name := UI.label(column, Characters.display_name(id), 12, true)
	name.autowrap_mode = TextServer.AUTOWRAP_OFF
	name.clip_text = true
	name.mouse_filter = Control.MOUSE_FILTER_IGNORE
	var sub := UI.label(column, "", 10, true)
	sub.autowrap_mode = TextServer.AUTOWRAP_OFF
	sub.clip_text = true
	sub.mouse_filter = Control.MOUSE_FILTER_IGNORE
	return {"button": button, "portrait": portrait, "badge": badge, "name": name, "sub": sub, "lock": lock}

# ---------------------------------------------------------------- refresh (in place)

func refresh() -> void:
	var slot := int(s["slot"])
	var count := int(s["player_count"])
	for n in _count_buttons:
		_count_buttons[n].look = MeadowButton.Look.SEGMENT_ON if n == count else MeadowButton.Look.SEGMENT_OFF
	if computer():
		_mix.text = Setup.mix_label(count, int(s["humans"])) + ("  ·  tap %d again to change" % count if count > 2 else "")
	if _chips.size() != count:
		_build_chips()
	for seat in count:
		var chip: Dictionary = _chips[seat]
		var color: String = s["colors"][seat]
		chip["portrait"].character_id = s["paws"][seat]
		chip["portrait"].team = color
		chip["label"].text = seat_label(seat)
		chip["paw"].text = Characters.display_name(s["paws"][seat])
		chip["panel"].add_theme_stylebox_override("normal", _chip_style(color, seat == slot))
		chip["panel"].add_theme_stylebox_override("hover", _chip_style(color, seat == slot))
		chip["panel"].add_theme_stylebox_override("pressed", _chip_style(color, true))
	_paw_title.text = "Paw for %s" % seat_label(slot)
	var unlocked := _unlocked()
	for id in _cards:
		var card: Dictionary = _cards[id]
		var owner: int = Array(s["paws"]).find(id)
		var locked := not unlocked.has(id)
		var selected: bool = owner == slot
		card["button"].modulate = Color(1, 1, 1, 0.55) if locked else Color.WHITE
		card["lock"].visible = locked
		card["badge"].visible = owner >= 0
		if owner >= 0:
			card["badge"].add_theme_stylebox_override("panel", _dot(s["colors"][owner], 8, 2))
		card["portrait"].team = s["colors"][owner] if owner >= 0 else s["colors"][slot]
		card["sub"].text = _unlock_label(id) if locked else (seat_label(owner) if owner >= 0 else "")
		card["sub"].add_theme_color_override("font_color", MeadowStyle.GOLD if locked else MeadowStyle.MINT)
		card["name"].add_theme_color_override("font_color", Color("#ffd45e") if selected else MeadowStyle.INK)
		var style := _card_style(selected)
		for state_name in ["normal", "hover", "pressed"]:
			card["button"].add_theme_stylebox_override(state_name, style)
	var color_seat := 0 if computer() else slot
	_color_title.text = ("Your colour" if computer() else "%s's colour" % seat_label(slot)) + " · " + str(s["colors"][color_seat]).capitalize()
	for name in _color_buttons:
		var b: Button = _color_buttons[name]
		var mine: bool = s["colors"][color_seat] == name
		for state_name in ["normal", "hover", "pressed"]:
			b.add_theme_stylebox_override(state_name, _dot(name, 27, 5 if mine else 2, mine))
		var owner: int = Array(s["colors"]).find(name)
		b.tooltip_text = name.capitalize() + ("" if owner < 0 or owner == color_seat or computer() else " · %s has it (tap to swap)" % seat_label(owner))

func _chip_style(color: String, selected: bool) -> StyleBoxFlat:
	var box := StyleBoxFlat.new()
	box.bg_color = Color("#1e3d36") if selected else MeadowStyle.MOSS
	box.set_corner_radius_all(12)
	box.border_color = Color("#ffd45e") if selected else Card.TEAM.get(color, Color.WHITE)
	box.set_border_width_all(3 if selected else 2)
	box.shadow_color = Color(1.0, 0.83, 0.37, 0.45) if selected else Color(0, 0, 0, 0.3)
	box.shadow_size = 7 if selected else 2
	return box

func _card_style(selected: bool) -> StyleBoxFlat:
	var box := StyleBoxFlat.new()
	box.bg_color = Color("#1e3d36") if selected else Color("#0f2d23")
	box.set_corner_radius_all(12)
	box.border_color = Color("#ffd45e") if selected else MeadowStyle.MOSS_EDGE
	box.set_border_width_all(3 if selected else 1)
	box.shadow_color = Color(1.0, 0.83, 0.37, 0.4) if selected else Color(0, 0, 0, 0)
	box.shadow_size = 6 if selected else 0
	return box

func _dot(color: String, radius: int, border: int, selected := false) -> StyleBoxFlat:
	var box := StyleBoxFlat.new()
	box.bg_color = Card.TEAM.get(color, Color.WHITE)
	box.set_corner_radius_all(radius)
	box.border_color = Color("#ffd45e") if selected else Color("#0b2a20")
	box.set_border_width_all(border)
	box.shadow_color = Color(1.0, 0.83, 0.37, 0.55) if selected else Color(0, 0, 0, 0)
	box.shadow_size = 6 if selected else 0
	return box

func _offer_for(id: String) -> Variant:
	for offer in page.state.store.catalog.for_category(Catalog.CHARACTER_PACK):
		if offer["id"] == "character_" + id:
			return offer
	return null

## Live characterUnlockLabel via the smart-unlock decision (no lock emoji in V2 fonts).
func _unlock_label(id: String) -> String:
	var offer = _offer_for(id)
	if offer == null:
		return "Locked"
	var shop = load("res://scripts/ui/preview/shop_pages.gd")
	var primary: String = shop.decision(page.state, offer)["primary"]
	var label: String = UNLOCK_LABELS.get(primary, "Locked")
	return label % int(offer["gemPrice"]) if primary == "GEMS" else label

## The chosen seat's Paw scrolled into view when the page opens.
func _reveal_selected() -> void:
	if not is_instance_valid(_carousel):
		return
	var id: String = s["paws"][int(s["slot"])]
	var card: Control = _cards[id]["button"]
	_carousel.set_position_now(maxf(card.position.x - (_carousel.size.x - card.size.x) * 0.5, 0.0))

# ---------------------------------------------------------------- actions (live)

func _on_count(n: int) -> void:
	if computer():
		s["humans"] = Setup.humans_after_tap(n, int(s["player_count"]), int(s["humans"]))
	elif n == int(s["player_count"]):
		return
	s["player_count"] = n
	s["slot"] = clampi(int(s["slot"]), 0, n - 1)
	if computer():
		s["colors"] = Setup.colors_for(n, s["colors"][0])
	_save()
	refresh()

func _on_seat(seat: int) -> void:
	s["slot"] = seat
	_save()
	refresh()
	_reveal_selected()

func _on_color(name: String) -> void:
	if computer():
		s["colors"] = Setup.colors_for(int(s["player_count"]), name)
	else:
		s["colors"] = Setup.select_color_for_seat(int(s["player_count"]), s["colors"], int(s["slot"]), name)
	_save()
	refresh()

func _on_paw(id: String) -> void:
	if not _unlocked().has(id):
		_preview_locked(id)
		return
	var slot := int(s["slot"])
	var humans := int(s["humans"]) if computer() else int(s["player_count"])
	var owner: int = Array(s["paws"]).find(id)
	if computer() and slot >= humans and owner >= 0 and owner < humans:
		page.notice("Paw taken", "This paw belongs to a human player. Choose another robot paw.")
		return
	s["paws"] = Starter.select(int(s["player_count"]), s["paws"], slot, id, computer(), _unlocked(), humans)
	if not computer() or slot == 0:
		page.state.inventory.select_character(s["paws"][0])
	_save()
	refresh()

func _rename(seat: int) -> void:
	var card: VBoxContainer = page.dialog("Player %d name" % (seat + 1), "")
	var edit := UI.field(card, s["names"][seat], "Player name", 14)
	UI.button(card, "Save", func():
		var value := edit.text.strip_edges()
		if not value.is_empty():
			s["names"][seat] = value
			_save()
		page.dismiss()
		refresh(), true)
	UI.button(card, "Cancel", page.dismiss)

## Locked Paw (V2): a preview, never assigned — a turning 3D animal, its price and
## "Unlock in Shop" (the Shop's Paws tab on this Paw with its unlock chooser).
func _preview_locked(id: String) -> void:
	var card: VBoxContainer = page.dialog(Characters.display_name(id), _unlock_label(id), true)
	var holder := Control.new()
	holder.custom_minimum_size = Vector2(0, 170)
	card.add_child(holder)
	var portrait := Portrait.new()
	portrait.character_id = id
	portrait.team = s["colors"][int(s["slot"])]
	portrait.zoom = 1.0
	portrait.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	holder.add_child(portrait)
	var spin := portrait.create_tween().set_loops()
	spin.tween_property(portrait, "yaw_degrees", 360.0, 6.0).from(0.0)
	var unlock := UI.button(card, "Unlock in Shop", func():
		page.dismiss()
		page.state.tab_indexes["shop"] = 0
		page.state.set_meta("shop_focus", "character_" + id)
		page.open(&"shop"), true, "shop")
	Hints.button(unlock, "setup.locked_unlock", Hints.Policy.IDLE_SCREEN_MS, 60)
	UI.button(card, "Close", page.dismiss)
