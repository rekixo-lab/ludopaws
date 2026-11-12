extends RefCounted
class_name RewardsPage
## Rewards page (V2 Moonlit Meadow look, live RewardsActivity behaviour): a summary strip,
## the Daily Gift chest, the Free Spin wheel and the Daily Ad Bonus. Amounts and rules are the
## server's (engagement-rewards.js: daily gift, spin outcomes / odds, bonus Gems; admob-ssv.js
## credits the ad). The client never picks an amount or a spin result: the wheel only lands
## on a segment showing the amount the server returned. A claim updates only its own card.

const UI = preload("res://scripts/ui/preview/meadow_controls.gd")
const Hints = preload("res://scripts/ui/hints/hints.gd")

## Wheel segments clockwise from the pointer (V2 design); 5 = golden jackpot.
const SEGMENTS := [1, 2, 1, 3, 1, 2, 1, 5]
const LUDO := [Color("#d9534f"), Color("#3fa75b"), Color("#f2c230"), Color("#3a7bd5")]
const GOLD := Color("#ffcf4d")

# ---------------------------------------------------------------- pure helpers (tested)

## "hh:mm" until the server's reset time (ms epoch).
static func countdown(resets_at_ms: int, now_ms := -1) -> String:
	var now := now_ms if now_ms >= 0 else int(Time.get_unix_time_from_system() * 1000.0)
	var left := maxi(0, resets_at_ms - now) / 60000
	return "%02d:%02d" % [left / 60, left % 60]

## Segment indexes that show `amount`.
static func segments_for(amount: int) -> Array:
	var out: Array = []
	for i in SEGMENTS.size():
		if SEGMENTS[i] == amount:
			out.append(i)
	return out

## The wheel rotation (degrees, clockwise) that puts segment `index` under the top pointer,
## at least `min_travel` degrees ahead of `from` (offset keeps it inside the segment).
static func stop_rotation(from: float, index: int, min_travel: float, offset := 0.0) -> float:
	var center := index * 45.0 + 22.5 + clampf(offset, -15.0, 15.0)
	var target := fposmod(-center, 360.0)
	var base := from + min_travel
	return base + fposmod(target - fposmod(base, 360.0), 360.0)

## The segment under the pointer at a rotation.
static func segment_at(rotation_deg: float) -> int:
	return int(floor(fposmod(-rotation_deg, 360.0) / 45.0)) % SEGMENTS.size()

## Odds lines from the server's spinOutcomes ({gems, percent}).
static func odds_lines(outcomes: Array) -> Array:
	return outcomes.filter(func(o): return o is Dictionary).map(func(o):
		var g := int(o.get("gems", 0))
		return "%d %s · %d%%" % [g, "Gem" if g == 1 else "Gems", int(o.get("percent", 0))])

## Daily Ad Bonus text per state.
static func ad_text(state: String, gems: int, left := "") -> String:
	match state:
		"loading": return "Loading the ad… · विज्ञापन लोड हो रहा है…"
		"unavailable": return "No ad is available right now. Try again later. · अभी कोई विज्ञापन नहीं है, बाद में कोशिश करें."
		"verifying": return "Verifying your reward… · इनाम की जाँच हो रही है…"
		"done": return "+%d Gems added ✓ · +%d Gems मिल गए ✓" % [gems, gems]
		"collected": return "Already collected today. Come back in %s. · आज ले लिया, %s बाद फिर आएँ." % [left, left]
	return "Watch one short video, get +%d Gems (once a day). · एक छोटा वीडियो देखें, +%d Gems पाएँ (दिन में एक बार)." % [gems, gems]

# ---------------------------------------------------------------- page

static func build(page, body: VBoxContainer, status: Dictionary) -> void:
	var view := RewardsView.new()
	view.page = page
	view.status = status
	view.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	body.add_child(view)
	view.build()

class RewardsView:
	extends VBoxContainer
	var page
	var status := {}
	var summary: Label
	var balance: Label
	var balance_value := 0
	var cards := {}
	var chest: GiftChest
	var wheel: RewardWheel
	var _tick := 0.0

	func build() -> void:
		add_theme_constant_override("separation", 10)
		balance_value = page.state.inventory.gems
		var strip := PanelContainer.new()
		strip.add_theme_stylebox_override("panel", UI.surface(Color("#14271c"), Color("#bc8d4b"), 14))
		add_child(strip)
		var line := UI.row(strip, 8)
		summary = UI.label(line, "", 14)
		summary.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		UI.image(line, UI.icon("gem"), 20).custom_minimum_size.x = 22
		balance = UI.label(line, str(balance_value), 17)
		balance.autowrap_mode = TextServer.AUTOWRAP_OFF
		for key in ["daily", "spin", "bonus"]:
			if key == "bonus" and not ads_offered():
				continue   # live: no ad card when the build has no rewarded ads
			var card := PanelContainer.new()
			card.custom_minimum_size.y = 350
			card.add_theme_stylebox_override("panel", UI.surface(Color("#15382a"), Color("#bc8d4b"), 16))
			add_child(card)
			cards[key] = card
			refresh_card(key)
		_refresh_summary()

	func ads_offered() -> bool:
		var ads = page.state.ads
		return ads._platform != null or (page.state.store.desktop != null)

	func _refresh_summary() -> void:
		var got := 0
		for key in cards:
			if status.get({"daily": "dailyClaimed", "spin": "spinClaimed", "bonus": "bonusClaimed"}[key], false):
				got += 1
		summary.text = "Today's rewards: %d/%d collected · resets in %s" % [got, cards.size(), RewardsPage.countdown(int(status.get("resetsAt", 0)))]

	func _process(delta: float) -> void:
		_tick += delta
		if _tick >= 1.0:
			_tick = 0.0
			_refresh_summary()
			for key in cards:
				var left = cards[key].get_meta("countdown") if cards[key].has_meta("countdown") else null
				if left is Label and is_instance_valid(left):
					left.text = left.get_meta("template") % RewardsPage.countdown(int(status.get("resetsAt", 0)))

	func _content(key: String) -> VBoxContainer:
		var card: PanelContainer = cards[key]
		for child in card.get_children():
			card.remove_child(child)
			child.queue_free()
		card.remove_meta("countdown")
		var box := UI.column(card, 6)
		box.alignment = BoxContainer.ALIGNMENT_CENTER
		return box

	func _countdown_label(card_key: String, parent: Control, template: String) -> Label:
		var label := UI.label(parent, template % RewardsPage.countdown(int(status.get("resetsAt", 0))), 14, true)
		label.set_meta("template", template)
		cards[card_key].set_meta("countdown", label)
		return label

	func refresh_card(key: String) -> void:
		match key:
			"daily": _daily()
			"spin": _spin()
			"bonus": _bonus()
		_refresh_summary()

	func _reduced() -> bool:
		return bool(page.state.setting("reduced_motion", false))

	# ------------------------------------------------------------ Daily Gift

	func _daily() -> void:
		var box := _content("daily")
		UI.label(box, "Daily Gift · रोज़ का तोहफ़ा", 22, true)
		var claimed: bool = status.get("dailyClaimed", false)
		chest = GiftChest.new()
		chest.custom_minimum_size = Vector2(0, 130)
		chest.opened = claimed
		chest.reduced_motion = _reduced()
		box.add_child(chest)
		var gems := int(status.get("dailyGems", 3))
		if claimed:
			UI.label(box, "+%d Gems collected" % gems, 16, true)
			_countdown_label("daily", box, "Come back tomorrow · %s left · कल फिर आएँ")
		else:
			UI.label(box, "Tap to open: +%d Gems · खोलने के लिए टैप करें" % gems, 15, true)
			var open := UI.button(box, "Open Gift", _claim_daily, true, "gift", 40)
			open.custom_minimum_size.x = 190
			open.size_flags_horizontal = Control.SIZE_SHRINK_CENTER
			chest.tapped.connect(_claim_daily)
			Hints.button(open, "rewards.daily_gift", Hints.Policy.IDLE_SCREEN_MS, 55)
		# 7-day progress toward the server's badge tier (no Gem milestone: the server gives none).
		var total := int(status.get("dailyClaims", 0))
		var trail := UI.row(box, 4)
		trail.alignment = BoxContainer.ALIGNMENT_CENTER
		var step := 7 if total > 0 and total % 7 == 0 else total % 7
		for i in 7:
			var paw := UI.label(trail, "●", 16)
			paw.add_theme_color_override("font_color", GOLD if i < step else Color("#4f6a55"))
		UI.label(box, "%d of 7 toward the next Garden Friend tier" % step, 12, true)

	func _claim_daily() -> void:
		if page.state.get_meta("reward_claiming", false) or status.get("dailyClaimed", false):
			return
		page.state.set_meta("reward_claiming", true)
		var before: int = page.state.inventory.gems
		var res: Dictionary = await page.state.store.claim_reward("daily", int(status.get("day", 0)))
		page.state.set_meta("reward_claiming", false)
		if not is_instance_valid(self):
			return
		if res.get("ok", false) and int(res.get("receipt", {}).get("day", -1)) == int(res.get("day", -2)):
			status = res
			if is_instance_valid(chest):
				await chest.open_animated()
			if not is_instance_valid(self):
				return
			_fly_gems(chest, int(res.get("amount", 0)), before)
			refresh_card("daily")
		else:
			_error("daily", res)

	# ------------------------------------------------------------ Free Spin

	func _spin() -> void:
		var box := _content("spin")
		var head := UI.row(box, 6)
		var title := UI.label(head, "Free Spin · मुफ़्त स्पिन", 21)
		title.autowrap_mode = TextServer.AUTOWRAP_OFF
		title.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		title.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
		var odds := UI.button(head, "ⓘ Odds", _show_odds, false, "", 30)
		odds.custom_minimum_size = Vector2(78, 30)
		odds.size_flags_horizontal = Control.SIZE_SHRINK_END
		odds.size_flags_vertical = Control.SIZE_SHRINK_CENTER
		wheel = RewardWheel.new()
		wheel.custom_minimum_size = Vector2(0, 220)
		wheel.reduced_motion = _reduced()
		wheel.tick.connect(_on_tick)
		box.add_child(wheel)
		var spun: bool = status.get("spinClaimed", false)
		if spun:
			var amount := int(status.get("spinGems", 0))
			var at: Array = RewardsPage.segments_for(amount)
			if not at.is_empty():
				wheel.rotation_deg = RewardsPage.stop_rotation(0.0, at[0], 0.0)
			wheel.dimmed = true
			wheel.center_text = "+%d" % amount
			UI.label(box, "Today's spin: +%d Gems" % amount, 16, true)
			_countdown_label("spin", box, "Spin again tomorrow · %s · कल फिर स्पिन करें")
		else:
			wheel.center_text = "SPIN"
			wheel.tapped.connect(_claim_spin)
			UI.label(box, "Tap SPIN in the middle · बीच में SPIN दबाएँ", 14, true)

	func _on_tick() -> void:
		var audio = page.get_node_or_null("/root/GameAudio")
		if audio != null:
			audio.click()
		if bool(page.state.setting("haptics", true)) and OS.has_feature("mobile"):
			Input.vibrate_handheld(8)

	func _show_odds() -> void:
		var lines: Array = RewardsPage.odds_lines(Array(status.get("spinOutcomes", [])))
		page.notice("Free Spin odds", "\n".join(lines) + "\n\nEvery free spin wins Gems.", true)

	func _claim_spin() -> void:
		if wheel.spinning or status.get("spinClaimed", false) or page.state.get_meta("reward_claiming", false):
			return
		page.state.set_meta("reward_claiming", true)
		var before: int = page.state.inventory.gems
		wheel.center_text = ""
		wheel.start_spin()   # spins at once; the server's answer decides where it stops
		var res: Dictionary = await page.state.store.claim_reward("spin", int(status.get("day", 0)))
		page.state.set_meta("reward_claiming", false)
		if not is_instance_valid(self) or not is_instance_valid(wheel):
			return
		var amount := int(res.get("amount", 0))
		if res.get("ok", false) and not RewardsPage.segments_for(amount).is_empty():
			await wheel.stop_on(amount)
			if not is_instance_valid(self):
				return
			status = res
			_fly_gems(wheel, amount, before)
			page.notice("Free Spin", "+%d Gems!" % amount, true)
			refresh_card("spin")
		else:
			await wheel.stop_empty()
			if is_instance_valid(self):
				_error("spin", res)

	# ------------------------------------------------------------ Daily Ad Bonus

	func _bonus(state := "") -> void:
		var box := _content("bonus")
		UI.label(box, "Daily Ad Bonus · रोज़ का वीडियो बोनस", 21, true)
		var tv := AdScreen.new()
		tv.custom_minimum_size = Vector2(0, 120)
		box.add_child(tv)
		var gems := int(status.get("bonusGems", 2))
		if state.is_empty():
			state = "collected" if status.get("bonusClaimed", false) else "idle"
		var left := RewardsPage.countdown(int(status.get("resetsAt", 0)))
		var text := UI.label(box, RewardsPage.ad_text(state, gems, left), 14, true)
		if state == "collected":
			text.set_meta("template", "Already collected today. Come back in %s. · आज ले लिया, फिर आएँ.")
			cards["bonus"].set_meta("countdown", text)
		if state in ["idle", "unavailable"]:
			var watch := UI.button(box, "▶ Watch Ad · +%d" % gems, _watch_bonus, true, "", 40)
			watch.custom_minimum_size.x = 190
			watch.size_flags_horizontal = Control.SIZE_SHRINK_CENTER
			watch.disabled = not page.state.ads.rewarded_available()

	func _watch_bonus() -> void:
		var before: int = page.state.inventory.gems
		_bonus("loading")
		var res: Dictionary = await page.state.ads.show_daily_bonus()
		if not is_instance_valid(self):
			return
		if not res.get("ok", false):
			_bonus("unavailable")
			return
		_bonus("verifying")
		# The server credits only the signed ad callback; check like live (up to 5 x 2 s).
		for i in 5:
			var fresh: Dictionary = await page.state.store.rewards_status()
			if not is_instance_valid(self):
				return
			if fresh.get("ok", false) and fresh.get("bonusClaimed", false):
				status = fresh
				_fly_gems(cards["bonus"], int(fresh.get("bonusGems", 2)), before)
				_bonus("done")
				_refresh_summary()
				return
			await get_tree().create_timer(2.0).timeout
			if not is_instance_valid(self):
				return
		_bonus("verifying")

	# ------------------------------------------------------------ shared

	func _error(key: String, res: Dictionary) -> void:
		var code := str(res.get("error", ""))
		var message := "A new reward day started. Refresh Rewards." if code == "REWARD_DAY_CHANGED" else (
			"You're offline. Connect and try again." if code in ["NETWORK_ERROR", "TIMEOUT"] or int(res.get("status", -1)) == 0
			else str(res.get("message", "Something went wrong. Try again.")))
		refresh_card(key)
		var box: VBoxContainer = cards[key].get_child(0)
		UI.label(box, message, 13, true).add_theme_color_override("font_color", Color("#ffb4a2"))
		UI.button(box, "Refresh" if code == "REWARD_DAY_CHANGED" else "Retry", func():
			page.state.set_meta("rewards_loaded_at", -60000)
			page.refresh(), false, "", 34)

	## Gems fly from the card to the balance, which counts up (to the server's wallet value).
	func _fly_gems(from: Control, amount: int, before: int) -> void:
		var target: int = page.state.inventory.gems
		if target <= before:
			target = before + amount
		var start := from.get_global_rect().get_center()
		var goal := balance.get_global_rect().get_center()
		if not _reduced():
			for i in mini(amount, 6) + 2:
				var gem := TextureRect.new()
				gem.texture = UI.icon("gem")
				gem.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
				gem.size = Vector2(26, 26)
				gem.mouse_filter = Control.MOUSE_FILTER_IGNORE
				gem.top_level = true
				add_child(gem)
				gem.global_position = start + Vector2(randf_range(-40, 40), randf_range(-20, 20))
				var tw := gem.create_tween()
				tw.tween_property(gem, "global_position", gem.global_position + Vector2(0, -50), 0.25).set_ease(Tween.EASE_OUT)
				tw.tween_property(gem, "global_position", goal - Vector2(13, 13), 0.55).set_trans(Tween.TRANS_QUAD).set_ease(Tween.EASE_IN).set_delay(i * 0.05)
				tw.tween_callback(gem.queue_free)
		var count := create_tween()
		count.tween_method(func(v: int):
			if is_instance_valid(balance):
				balance.text = str(v), before, target, 0.1 if _reduced() else 0.9).set_delay(0.0 if _reduced() else 0.6)
		balance_value = target

# ---------------------------------------------------------------- art (procedural, V2 style)

## A wooden gift chest: wobbles while closed, the lid swings open with a glow.
class GiftChest:
	extends Control
	signal tapped
	var opened := false
	var reduced_motion := false
	var lid := 0.0       # 0 closed .. 1 open
	var _t := 0.0

	func _ready() -> void:
		lid = 1.0 if opened else 0.0
		mouse_filter = Control.MOUSE_FILTER_STOP

	func _gui_input(event: InputEvent) -> void:
		if not opened and ((event is InputEventMouseButton and event.pressed and event.button_index == MOUSE_BUTTON_LEFT) or (event is InputEventScreenTouch and event.pressed)):
			tapped.emit()
			accept_event()

	func _process(delta: float) -> void:
		if not opened and not reduced_motion:
			_t += delta
			queue_redraw()

	func open_animated() -> void:
		opened = true
		if reduced_motion:
			lid = 1.0
			queue_redraw()
			return
		var tw := create_tween()
		tw.tween_method(func(v: float):
			lid = v
			queue_redraw(), 0.0, 1.0, 0.55).set_trans(Tween.TRANS_BACK).set_ease(Tween.EASE_OUT)
		await tw.finished

	func _draw() -> void:
		var c := size * Vector2(0.5, 0.62)
		var w := minf(size.x * 0.5, 170.0)
		var h := w * 0.48
		var wobble := sin(_t * 6.0) * 0.05 * (1.0 if sin(_t * 0.9) > 0.55 else 0.0)
		draw_set_transform(c, wobble, Vector2.ONE)
		if lid > 0.05:
			draw_circle(Vector2(0, -h * 0.2), w * 0.55 * lid, Color(1.0, 0.85, 0.4, 0.22 * lid))
		draw_rect(Rect2(-w / 2, -h / 2, w, h), Color("#7a4a27"))
		for i in 3:
			draw_line(Vector2(-w / 2, -h / 2 + h * (i + 1) / 4.0), Vector2(w / 2, -h / 2 + h * (i + 1) / 4.0), Color("#5c341b"), 2.0)
		draw_rect(Rect2(-w / 2, -h / 2, w, h), Color("#e0b155"), false, 3.0)
		draw_rect(Rect2(-w * 0.08, -h / 2, w * 0.16, h), Color("#e0b155"))
		# Lid: closed on top; opening, it tips back so only its gold-trimmed inside shows,
		# staying inside the chest's own space.
		var lid_h := h * 0.42 * (1.0 - 0.5 * lid)
		var top := -h / 2
		var back := w * 0.06 * lid
		var lid_poly := PackedVector2Array([Vector2(-w / 2 - 4, top), Vector2(w / 2 + 4, top),
			Vector2(w / 2 + 4 - back, top - lid_h), Vector2(-w / 2 - 4 + back, top - lid_h)])
		draw_colored_polygon(lid_poly, Color("#8f5a2f").lerp(Color("#4a2a16"), lid))
		draw_polyline(lid_poly + PackedVector2Array([lid_poly[0]]), Color("#e0b155"), 3.0)
		draw_rect(Rect2(-w * 0.08, top - lid_h, w * 0.16, lid_h), Color("#e0b155"))
		if lid > 0.3:
			for i in 5:
				var p := Vector2(lerpf(-w * 0.3, w * 0.3, i / 4.0), top - 4 - (i % 2) * 6)
				draw_circle(p, 6, Color("#ff6b8b") if i % 2 == 0 else Color("#7fd3ff"))
		if lid < 0.5:
			draw_circle(Vector2(0, -h * 0.05), w * 0.06, Color("#ffe08a"))
		draw_set_transform(Vector2.ZERO, 0.0, Vector2.ONE)

## 8-segment Ludo-colour wheel: only `rotation_deg` changes per frame (one draw call set).
class RewardWheel:
	extends Control
	signal tick
	signal tapped
	var rotation_deg := 0.0:
		set(v):
			var before := RewardsPage.segment_at(rotation_deg)
			rotation_deg = v
			if RewardsPage.segment_at(v) != before:
				tick.emit()
			queue_redraw()
	var reduced_motion := false
	var dimmed := false
	var spinning := false
	var center_text := "SPIN":
		set(v):
			center_text = v
			queue_redraw()
	var _free_spin := false
	const SPEED := 540.0

	func _ready() -> void:
		mouse_filter = Control.MOUSE_FILTER_STOP

	func _gui_input(event: InputEvent) -> void:
		if not spinning and not dimmed and ((event is InputEventMouseButton and event.pressed and event.button_index == MOUSE_BUTTON_LEFT) or (event is InputEventScreenTouch and event.pressed)):
			tapped.emit()
			accept_event()

	func start_spin() -> void:
		spinning = true
		_free_spin = not reduced_motion

	func _process(delta: float) -> void:
		if _free_spin:
			rotation_deg += SPEED * delta

	## Server amount arrived: ease out (3.5 s) onto a segment with that amount, slight bounce.
	func stop_on(amount: int) -> void:
		var choices: Array = RewardsPage.segments_for(amount)
		var index: int = choices[randi() % choices.size()]
		var offset := randf_range(-12.0, 12.0)
		_free_spin = false
		if reduced_motion:
			rotation_deg = RewardsPage.stop_rotation(rotation_deg, index, 0.0, offset)
			spinning = false
			return
		var target := RewardsPage.stop_rotation(rotation_deg, index, SPEED * 3.5 / 3.0, offset)
		var tw := create_tween()
		tw.tween_property(self, "rotation_deg", target + 6.0, 3.5).set_trans(Tween.TRANS_CUBIC).set_ease(Tween.EASE_OUT)
		tw.tween_property(self, "rotation_deg", target, 0.3).set_trans(Tween.TRANS_SINE).set_ease(Tween.EASE_IN_OUT)
		await tw.finished
		spinning = false

	## Error / offline: slow down without landing on any prize claim.
	func stop_empty() -> void:
		_free_spin = false
		if not reduced_motion:
			var tw := create_tween()
			tw.tween_property(self, "rotation_deg", rotation_deg + 120.0, 1.0).set_ease(Tween.EASE_OUT).set_trans(Tween.TRANS_CUBIC)
			await tw.finished
		spinning = false
		center_text = "SPIN"

	func _draw() -> void:
		# Room above the disc for the paw pointer, so it never reaches the card title.
		var c := Vector2(size.x / 2.0, size.y / 2.0 + 22.0)
		var r := minf(size.x, size.y - 50.0) * 0.46
		var fade := 0.45 if dimmed else 1.0
		draw_circle(c, r + 8, Color("#8f5a2f") * Color(1, 1, 1, fade))
		draw_circle(c, r + 4, Color("#e0b155") * Color(1, 1, 1, fade))
		for i in SEGMENTS.size():
			var a0 := deg_to_rad(rotation_deg + i * 45.0 - 90.0)
			var a1 := a0 + deg_to_rad(45.0)
			var pts := PackedVector2Array([c])
			for k in 9:
				pts.append(c + Vector2.from_angle(lerpf(a0, a1, k / 8.0)) * r)
			var col: Color = GOLD if SEGMENTS[i] == 5 else LUDO[i % 4]
			draw_colored_polygon(pts, col * Color(1, 1, 1, fade))
			draw_line(c, c + Vector2.from_angle(a0) * r, Color(1, 1, 1, 0.35 * fade), 2.0)
			var mid := c + Vector2.from_angle((a0 + a1) / 2.0) * r * 0.68
			var font := get_theme_default_font()
			var label := "★5" if SEGMENTS[i] == 5 else str(SEGMENTS[i])
			draw_string(font, mid + Vector2(-10, 8), label, HORIZONTAL_ALIGNMENT_CENTER, 20, 22, Color("#2b1a0e") if SEGMENTS[i] == 5 else Color.WHITE)
		# Center SPIN button and the paw pointer at the top (not rotating).
		draw_circle(c, r * 0.3, Color("#1f5d3d") if not dimmed else Color("#33463b"))
		draw_arc(c, r * 0.3, 0, TAU, 40, Color("#e0b155"), 3.0)
		var font := get_theme_default_font()
		draw_string(font, c + Vector2(-r * 0.3, 7), center_text, HORIZONTAL_ALIGNMENT_CENTER, r * 0.6, 19, Color("#fff2cf"))
		var tip := c + Vector2(0, -r + 6)
		draw_colored_polygon(PackedVector2Array([tip, tip + Vector2(-13, -22), tip + Vector2(13, -22)]), Color("#fff2cf"))
		var pad := tip + Vector2(0, -30)
		draw_circle(pad, 11, Color("#fff2cf"))
		for d in [Vector2(-12, -10), Vector2(-4, -15), Vector2(4, -15), Vector2(12, -10)]:
			draw_circle(pad + d, 4.5, Color("#fff2cf"))

## A small TV with a play triangle and Gems (the Daily Ad Bonus picture).
class AdScreen:
	extends Control
	func _draw() -> void:
		var c := size / 2.0
		var w := minf(size.x * 0.42, 150.0)
		var h := w * 0.62
		draw_line(c + Vector2(-w * 0.15, -h / 2), c + Vector2(-w * 0.3, -h / 2 - 18), Color("#e0b155"), 3.0)
		draw_line(c + Vector2(w * 0.15, -h / 2), c + Vector2(w * 0.3, -h / 2 - 18), Color("#e0b155"), 3.0)
		draw_rect(Rect2(c - Vector2(w, h) / 2 - Vector2(6, 6), Vector2(w, h) + Vector2(12, 12)), Color("#7a4a27"))
		draw_rect(Rect2(c - Vector2(w, h) / 2, Vector2(w, h)), Color("#123a4a"))
		draw_rect(Rect2(c - Vector2(w, h) / 2 - Vector2(6, 6), Vector2(w, h) + Vector2(12, 12)), Color("#e0b155"), false, 3.0)
		draw_colored_polygon(PackedVector2Array([c + Vector2(-12, -18), c + Vector2(-12, 18), c + Vector2(20, 0)]), Color("#fff2cf"))
		var gem: Texture2D = load("res://assets/ui/icons/gem.svg")
		for p in [Vector2(w * 0.32, h * 0.22), Vector2(-w * 0.34, h * 0.2)]:
			draw_texture_rect(gem, Rect2(c + p - Vector2(12, 12), Vector2(24, 24)), false)
