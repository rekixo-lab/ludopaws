extends RefCounted
## Top / bottom player rails around the board (live layout, V2 look), one component for every
## mode. Each card sits in its yard's corner (rail_layout.gd); each seat owns a dice slot on the
## card's inner side and ONLY the active seat's dice is shown. The dice itself is the roll
## button, enabled only for the active local human. See docs/MATCH_RAIL_SPEC.md.

const Layout = preload("res://scripts/game/match/rail_layout.gd")
const Card = preload("res://scripts/ui/match/player_card.gd")
const DiceView = preload("res://scripts/game/dice/dice_view.gd")

## Live phone width in dp; V2 sizes scale from it (card 116 dp, dice 58 dp, rail 78 dp).
const PHONE_DP := 392.0

## model: {players: [{seat, color, name, character, bot, computer, local, forfeited, tokens,
##          team_id}], first_color, online, local_color, local_team_id, active_seat,
##          roll_enabled, dice_style, countdown (0..1 or null), width}
## cache: Dictionary kept by the caller across rebuilds ({cards: {}, dice: {}}).
## on_roll: Callable(seat). Returns {top, bottom, slots: {seat: slot}, dice_buttons: {seat: Button}}.
static func build(model: Dictionary, cache: Dictionary, on_roll: Callable) -> Dictionary:
	# Fit the live dp layout to the available width (two halves of card + dice).
	var k: float = clampf(float(model.get("width", 870.0)) / PHONE_DP, 0.6, 2.4)
	var compact: bool = model.get("width", 870.0) < 700.0
	# Live card widths (104 / 116 dp) with compact V2 text.
	var card_w := (104.0 if compact else 116.0) * k
	# V2: the dice is ~15 % above live's 54 / 58 dp so premium skins show their detail.
	var dice_px := (62.0 if compact else 66.0) * k
	# Live rails reserve 78 dp (70 compact) incl. a quick-chat footer V2 does not have yet;
	# V2 reserves the taller of card and dice.
	var rail_h := maxf(56.0 * k, dice_px + 2.0)
	var pad: float = model.get("pad", 0.0)
	# The card may use up to +20 % of its half's free space so names stay on one line.
	var half_w: float = (float(model.get("width", 870.0)) - 6.0) * 0.5
	card_w = clampf(half_w - dice_px - 12.0, card_w, card_w * 1.4)
	var cards: Dictionary = cache.get_or_add("cards", {})
	var dice: Dictionary = cache.get_or_add("dice", {})
	var halves := {}
	var top := _rail(rail_h, halves, [Layout.TOP_LEFT, Layout.TOP_RIGHT], pad)
	var bottom := _rail(rail_h, halves, [Layout.BOTTOM_LEFT, Layout.BOTTOM_RIGHT], pad)
	var slots := {}
	var buttons := {}
	var players: Array = model["players"]
	for p in players:
		var slot: int
		if model.get("online", false):
			slot = Layout.online_slot(p["color"], model["local_color"], p.get("local", false), players.size())
		else:
			slot = Layout.slot_for_color(p["color"], model["first_color"])
		slots[p["seat"]] = slot
		var active: bool = p["seat"] == model["active_seat"]
		var cached_card = cards.get(p["seat"])
		var card: Card
		if is_instance_valid(cached_card):
			card = cached_card
		else:
			card = Card.new(k)
			cards[p["seat"]] = card
		if card.get_parent() != null:
			card.get_parent().remove_child(card)
		card.custom_minimum_size = Vector2(card_w, 0)
		var data: Dictionary = p.duplicate()
		data["local_team_id"] = model.get("local_team_id")
		card.bind(data, active, not Layout.is_left(slot))
		var holder: HBoxContainer = halves[slot]
		var button := _dice_slot(p, model, dice, dice_px, active, on_roll)
		buttons[p["seat"]] = button
		if Layout.dice_after_card(slot):
			holder.add_child(card)
			holder.add_child(button)
		else:
			holder.add_child(button)
			holder.add_child(card)
	return {"top": top, "bottom": bottom, "slots": slots, "dice_buttons": buttons}

static func _rail(height: float, halves: Dictionary, order: Array, pad := 0.0) -> HBoxContainer:
	var rail := HBoxContainer.new()
	rail.custom_minimum_size.y = height
	rail.add_theme_constant_override("separation", 6)
	rail.mouse_filter = Control.MOUSE_FILTER_IGNORE
	if pad > 0.0:
		var lead := Control.new()
		lead.custom_minimum_size.x = pad - 6.0
		lead.mouse_filter = Control.MOUSE_FILTER_IGNORE
		rail.add_child(lead)
	for slot in order:
		var half := HBoxContainer.new()
		half.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		half.alignment = BoxContainer.ALIGNMENT_BEGIN if Layout.is_left(slot) else BoxContainer.ALIGNMENT_END
		half.add_theme_constant_override("separation", 4)
		half.mouse_filter = Control.MOUSE_FILTER_IGNORE
		rail.add_child(half)
		halves[slot] = half
	if pad > 0.0:
		var trail := Control.new()
		trail.custom_minimum_size.x = pad - 6.0
		trail.mouse_filter = Control.MOUSE_FILTER_IGNORE
		rail.add_child(trail)
	return rail

## The seat's dice control: the dice view inside a flat button; only visible when active.
static func _dice_slot(p: Dictionary, model: Dictionary, dice: Dictionary, size: float, active: bool, on_roll: Callable) -> Button:
	var button := Button.new()
	button.flat = true
	button.focus_mode = Control.FOCUS_NONE
	button.custom_minimum_size = Vector2(size, size)
	button.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	var enabled: bool = active and model.get("roll_enabled", false) and p.get("local", false) \
		and not p.get("computer", false) and not p.get("bot", false)
	button.disabled = not enabled
	button.visible = active
	button.tooltip_text = ("%s is thinking" % p["name"]) if p.get("computer", false) or p.get("bot", false) else "Roll dice for %s" % p["name"]
	for state_name in ["normal", "hover", "pressed", "disabled", "focus"]:
		button.add_theme_stylebox_override(state_name, StyleBoxEmpty.new())
	var cached_view = dice.get(p["seat"])
	var view: DiceView
	if is_instance_valid(cached_view):
		view = cached_view
	else:
		view = DiceView.new()
		view.warm_up = true
		dice[p["seat"]] = view
	if view.get_parent() != null:
		view.get_parent().remove_child(view)
	var style: String = model.get("dice_style_by_seat", {}).get(p["seat"], model.get("dice_style", "dice_classic"))
	if view.style_id != style:
		view.style_id = style
	view.team_color = p["color"]
	view.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	button.add_child(view)
	if active and model.get("countdown") != null:
		var ring := Countdown.new()
		ring.fraction = model["countdown"]
		ring.color = Card.TEAM.get(p["color"], Color.WHITE)
		ring.perimeter = view._renderer.uses_perimeter_countdown
		ring.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
		ring.mouse_filter = Control.MOUSE_FILTER_IGNORE
		button.add_child(ring)
	var seat: int = p["seat"]
	button.pressed.connect(func():
		if not button.disabled:
			on_roll.call(seat))
	return button

## DiceView.drawTurnCountdown: team-coloured progress along the dice outline (perimeter skins)
## or a bar under the dice (environment / spatial skins); track alpha 62, progress opaque.
class Countdown extends Control:
	var fraction := 1.0
	var color := Color.WHITE
	var perimeter := true

	func _draw() -> void:
		var e := minf(size.x, size.y)
		var track := Color(color, 62.0 / 255.0)
		if not perimeter:
			var y := e * 0.97
			draw_line(Vector2(e * 0.34, y), Vector2(e * 0.66, y), track, 3.0, true)
			if fraction > 0.0:
				draw_line(Vector2(e * 0.34, y), Vector2(e * (0.34 + 0.32 * fraction), y), color, 3.0, true)
			return
		var r := Rect2(Vector2.ONE * e * 0.07, Vector2.ONE * e * 0.86)
		var pts := _outline(r, e * 0.17 - 4.0)
		var loop := pts.duplicate()
		loop.append(pts[0])
		draw_polyline(loop, track, 3.0, true)
		if fraction > 0.0:
			draw_polyline(loop.slice(0, maxi(2, int(loop.size() * clampf(fraction, 0.0, 1.0)))), color, 3.0, true)

	static func _outline(r: Rect2, radius: float) -> PackedVector2Array:
		var out := PackedVector2Array()
		for c in [[r.end.x - radius, r.position.y + radius, -90.0], [r.end.x - radius, r.end.y - radius, 0.0],
				[r.position.x + radius, r.end.y - radius, 90.0], [r.position.x + radius, r.position.y + radius, 180.0]]:
			for s in 9:
				var a := deg_to_rad(c[2] + 90.0 * s / 8.0)
				out.append(Vector2(c[0], c[1]) + Vector2(cos(a), sin(a)) * radius)
		return out
