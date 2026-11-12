extends RefCounted

const Hints = preload("res://scripts/ui/hints/hints.gd")
## Shop (V2 Meadow look) with the live store's logic and content: Paws / Boards / Dice
## (live sells no avatars), only live items, live prices and unlock channels, the
## SmartUnlockPolicy decision and the live unlock chooser (Gems / Play / rewarded ads).
## The server wallet is the authority; desktop runs the store / billing / ad stubs
## (UNTESTED ON DEVICE).

const UI = preload("res://scripts/ui/preview/meadow_controls.gd")
const Hub = preload("res://scripts/ui/preview/hub_pages.gd")
const Catalog = preload("res://scripts/game/store/store_catalog.gd")
const Characters = preload("res://scripts/game/characters/catalog.gd")
const Portrait = preload("res://scripts/game/characters/character_portrait.gd")
const BoardArt = preload("res://scripts/game/board3d/board_art_player.gd")
const ClassicArt = preload("res://scripts/game/board3d/classic_board_art.gd")
const Bake = preload("res://scripts/game/board3d/board_bake.gd")

## Shop preview animation: Paws idle-animate and every Dice rolls on its own every few
## seconds (roll -> settle on a random face, presentation only), but only for cards that
## are on screen, and everything pauses while the list is dragged or coasting so scrolling
## stays smooth. Off-screen portraits do no 3D work at all.
class PreviewDriver extends Control:
	## One dice rolls at a time (taking turns among the visible ones), so rolls never stack.
	const ROLL_GAP_MS := 1300
	const ROLL_MS := 650
	var list: Control
	var _items: Array = []   # [holder, kind, node]
	var _turn := 0
	var _rolling = null      # dice currently rolling
	var _settle_at := 0
	var _next_roll := 0

	func _init() -> void:
		visible = false        # a GridContainer skips hidden children (no grid cell)
		mouse_filter = Control.MOUSE_FILTER_IGNORE

	func _ready() -> void:
		_next_roll = Time.get_ticks_msec() + 900

	func track(holder: Control) -> void:
		for child in holder.get_children():
			if child.has_method("set_live"):
				_items.append([holder, "paw", child])
			elif child.has_method("start_rolling"):
				child.warm_up = true
				_items.append([holder, "dice", child])

	func _process(_delta: float) -> void:
		var now := Time.get_ticks_msec()
		var moving: bool = is_instance_valid(list) and list.has_method("is_moving") and list.is_moving()
		var view: Rect2 = list.get_global_rect() if is_instance_valid(list) else Rect2()
		_items = _items.filter(func(it): return is_instance_valid(it[0]) and is_instance_valid(it[2]))
		var visible_dice: Array = []
		for item in _items:
			var holder: Control = item[0]
			var shown := holder.is_visible_in_tree() and view.intersects(holder.get_global_rect())
			if item[1] == "paw":
				item[2].set_live(shown and not moving)
			else:
				item[2].warm_paused = moving or not shown
				if shown:
					visible_dice.append(item[2])
		if _rolling != null:
			if not is_instance_valid(_rolling):
				_rolling = null
			elif now >= _settle_at:
				_rolling.show_outcome(randi_range(1, 6))
				_rolling = null
				_next_roll = now + ROLL_GAP_MS
			return
		if moving or visible_dice.is_empty() or now < _next_roll:
			return
		_turn = (_turn + 1) % visible_dice.size()
		_rolling = visible_dice[_turn]
		_rolling.start_rolling()
		_settle_at = now + ROLL_MS

## A board preview from the shared bake (placeholder tint until the bake lands).
class BoardThumb extends TextureRect:
	var board_id := ""
	func _ready() -> void:
		mouse_filter = Control.MOUSE_FILTER_IGNORE
		expand_mode = TextureRect.EXPAND_IGNORE_SIZE
		stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
		_request.call_deferred()
	func _request() -> void:
		if not is_inside_tree():
			return
		var edge := minf(size.x, size.y)
		if edge <= 0.0:
			edge = custom_minimum_size.x
		var s := get_global_transform_with_canvas().get_scale().x * get_viewport().get_final_transform().get_scale().x
		var px := mini(maxi(16, roundi(edge * clampf(s, 1.0, 4.0))), 720)
		var me: WeakRef = weakref(self)
		Bake.request(board_id, px, func(texture):
			var node = me.get_ref()
			if node != null:
				node.texture = texture)
const DiceView = preload("res://scripts/game/dice/dice_view.gd")

const TABS := [["Paws", Catalog.CHARACTER_PACK], ["Boards", Catalog.BOARD], ["Dice", Catalog.DICE]]
const DICE_PREVIEW_FACE := {"dice_classic": 6, "dice_pearl_paw": 1, "dice_forest_dew": 5, "dice_lagoon_pearl": 4,
	"dice_frost_crystal": 6, "dice_starlight": 3, "dice_ember_forge": 5, "dice_prism_royal": 6}
const ACTION_LABELS := {"SELECT": "Equip", "GEMS": "Buy", "PLAY_PRODUCT": "Buy on Play", "WATCH_AD": "Watch Ad",
	"CLAIM_ADS": "Claim", "GET_GEMS": "Get Gems", "FREE": "Get", "LEVEL": "Locked", "EVENT": "Event", "UNAVAILABLE": "Unavailable"}

## Live StoreActivity: opening the Shop / Wallet reads the server wallet and catalog
## (guest account created on demand). The page updates in place when they arrive.
const STORE_TTL_MS := 30000

static func _sync_store(page) -> void:
	var state = page.state
	if state.get_meta("store_loading", false) or Time.get_ticks_msec() - int(state.get_meta("store_loaded_at", -STORE_TTL_MS)) < STORE_TTL_MS:
		return
	state.set_meta("store_loading", true)
	state.set_meta("store_loaded_at", Time.get_ticks_msec())
	var wallet: Dictionary = await state.store.refresh_wallet()
	if wallet.get("ok", false):
		await state.store.refresh_catalog()
		await state.store.sync_equipment()
		# Live StoreActivity: server billing status, account binding, Play prices, recovery.
		var account = page.get_node_or_null("/root/Account") if is_instance_valid(page) else null
		if account != null and account.available():
			var ad_status: Dictionary = await account.service.store_status()
			state.ads.server_available = ad_status.get("ok", false) and bool(ad_status.get("data", {}).get("rewardedAdsV2Available", false))
			if state.ads.server_available:
				state.ads.prepare()
		if account != null and account.available() and not state.billing.is_stub:
			var st: Dictionary = await account.service.store_status()
			state.billing.server_available = st.get("ok", false) and bool(st.get("data", {}).get("billingAvailable", false))
			if state.billing.server_available:
				var billing_account := str(account.session().get("billingAccountId", ""))
				state.billing.set_account(billing_account)
				await state.billing.refresh_products()
				await recover_pending(state, billing_account)
				var owned: Array = await state.billing.owned_purchases()
				state.set_meta("play_pending", owned.filter(func(p): return p.get("state") == "PENDING"))
	state.set_meta("store_loading", false)
	state.set_meta("store_error", "" if wallet.get("ok", false) else _error_text(str(wallet.get("error", ""))))
	if is_instance_valid(page) and page.current in [&"shop", &"wallet"]:
		update(page)

static func shop(page) -> void:
	var body: VBoxContainer = page.body
	var state = page.state
	_sync_store(page)
	var balance := UI.row(UI.panel(body), 8)
	balance.alignment = BoxContainer.ALIGNMENT_CENTER
	UI.image(balance, UI.icon("gem"), 30).custom_minimum_size.x = 32
	var balance_label := UI.label(balance, "%d Gems" % state.inventory.gems, 20, true)
	balance_label.autowrap_mode = TextServer.AUTOWRAP_OFF
	var index: int = state.tab_indexes.get("shop", 0)
	Hub._hub_filters(body, page, index, "shop", TABS.map(func(t): return t[0]))
	body = page.body
	var category: String = TABS[index][1]
	var offers: Array = state.store.catalog.for_category(category)
	if category == Catalog.CHARACTER_PACK:
		# The Starter Paws pack is the free-four bundle, not a buyable Paw: show the Paws.
		offers = offers.filter(func(o): return o["id"] != Catalog.DEFAULT_CHARACTER_PACK)
		offers = Catalog.FREE_CHARACTER_IDS.map(func(id): return _free_paw(id)) + offers
	var prices: Dictionary = state.billing.query(offers.map(func(o): return o["id"]))
	var grid := GridContainer.new()
	# Boards in two columns so each board is large enough to read; Paws and Dice in three.
	grid.columns = 2 if category == Catalog.BOARD else 3
	grid.add_theme_constant_override("h_separation", 7)
	grid.add_theme_constant_override("v_separation", 10)
	body.add_child(grid)
	# Animated previews run only while on screen and pause while the list moves.
	var driver := PreviewDriver.new()
	driver.list = page.scroll
	grid.add_child(driver)
	page.state.set_meta("shop_driver", driver)
	var cards: Array = []
	for offer in offers:
		cards.append([_card(page, grid, offer, prices.get(offer["id"])), offer, prices.get(offer["id"])])
	UI.button(body, "Wallet", page.open.bind(&"wallet"), false, "gem")
	page.state.set_meta("shop_ui", {"body": body, "balance": balance_label, "grid": grid, "cards": cards})
	# Arriving from a locked Paw in setup: show that Paw and open its unlock chooser (live
	# EXTRA_FEATURED_CHARACTER_ID + EXTRA_START_SMART_UNLOCK).
	if page.state.has_meta("shop_focus"):
		var focus: String = page.state.get_meta("shop_focus")
		page.state.remove_meta("shop_focus")
		for entry in cards:
			if entry[1]["id"] == focus:
				var target: Control = entry[0]
				var focus_offer: Dictionary = entry[1]
				(func():
					if is_instance_valid(target) and page.scroll is KineticScroll:
						page.scroll.set_position_now(maxf(target.position.y - 20.0, 0.0))
					chooser(page, focus_offer)).call_deferred()

## After a purchase, equip or ad reward: update the balance and the cards in place. The page,
## its header, tabs and scroll position stay (no rebuild); open dialogs stay open.
static func update(page) -> void:
	var state = page.state
	var shop_ui = state.get_meta("shop_ui") if state.has_meta("shop_ui") else null
	var wallet_ui = state.get_meta("wallet_ui") if state.has_meta("wallet_ui") else null
	if page.current == &"shop" and shop_ui != null and is_instance_valid(shop_ui["grid"]) and shop_ui["grid"].is_inside_tree():
		shop_ui["balance"].text = "%d Gems" % state.inventory.gems
		var grid: Control = shop_ui["grid"]
		for entry in shop_ui["cards"]:
			var old: Control = entry[0]
			var index := old.get_index()
			var fresh := _card(page, grid, entry[1], entry[2])
			grid.move_child(fresh, index)
			grid.remove_child(old)
			old.queue_free()
			entry[0] = fresh
	elif page.current == &"wallet" and wallet_ui != null and is_instance_valid(wallet_ui["list"]) and wallet_ui["list"].is_inside_tree():
		wallet_ui["balance"].text = str(state.inventory.gems)
		var list: VBoxContainer = wallet_ui["list"]
		for child in list.get_children():
			list.remove_child(child)
			child.queue_free()
		_wallet_list(page, list)
	else:
		page.refresh()

static func _free_paw(id: String) -> Dictionary:
	return {"id": "character_" + id, "title": Characters.display_name(id).to_upper(), "category": Catalog.CHARACTER_PACK,
		"unlockKind": Catalog.FREE, "gemPrice": 0, "requiredAdViews": 0, "channels": [Catalog.FREE], "contentAvailable": true,
		"purchaseAvailable": true, "free": true}

static func owned(state, offer: Dictionary) -> bool:
	if offer["category"] == Catalog.CHARACTER_PACK:
		return state.inventory.is_character_unlocked(str(offer["id"]).trim_prefix("character_"))
	return state.inventory.is_owned(offer["id"])

static func equipped(state, offer: Dictionary) -> bool:
	if offer["category"] == Catalog.CHARACTER_PACK:
		return state.inventory.selected_character() == str(offer["id"]).trim_prefix("character_")
	return state.inventory.selected_id(offer["category"]) == offer["id"]

static func decision(state, offer: Dictionary) -> Dictionary:
	var play_ok: bool = state.billing.query([offer["id"]]).has(offer["id"])
	return Catalog.smart_unlock(offer, owned(state, offer), state.inventory.gems,
		int(state.inventory.ad_progress.get(offer["id"], 0)), play_ok, state.ads.rewarded_available())

static func _card(page, grid: Control, offer: Dictionary, play) -> Control:
	var state = page.state
	var card := UI.panel(grid)
	var product_panel := card.get_parent() as MeadowPanel
	var padding := StyleBoxEmpty.new()
	padding.set_content_margin_all(6)
	product_panel.add_theme_stylebox_override("panel", padding)
	card.add_theme_constant_override("separation", 4)
	var art := preview(offer, 160 if offer["category"] == Catalog.BOARD else 101)
	card.add_child(art)
	var driver = page.state.get_meta("shop_driver") if page.state.has_meta("shop_driver") else null
	if is_instance_valid(driver) and driver.get_parent() == grid:
		driver.track(art)
	UI.label(card, offer["title"], 14, true)
	var line := UI.row(card, 4)
	line.alignment = BoxContainer.ALIGNMENT_CENTER
	var is_owned := owned(state, offer)
	if equipped(state, offer):
		UI.label(line, "Equipped", 13, true).autowrap_mode = TextServer.AUTOWRAP_OFF
	elif is_owned:
		UI.label(line, "Owned", 13, true).autowrap_mode = TextServer.AUTOWRAP_OFF
	elif int(offer["gemPrice"]) > 0:
		UI.image(line, UI.icon("gem"), 17).custom_minimum_size.x = 18
		UI.label(line, str(offer["gemPrice"]), 14, true).autowrap_mode = TextServer.AUTOWRAP_OFF
	else:
		UI.label(line, "Free", 13, true).autowrap_mode = TextServer.AUTOWRAP_OFF
	if not is_owned and play != null and offer["channels"].has(Catalog.PLAY_PRODUCT):
		UI.label(card, "or %s on Play" % play["price"], 11, true)
	if not is_owned and int(offer["requiredAdViews"]) > 0:
		UI.label(card, "%d/%d ads" % [mini(int(state.inventory.ad_progress.get(offer["id"], 0)), int(offer["requiredAdViews"])), int(offer["requiredAdViews"])], 11, true)
	var d := decision(state, offer)
	var label: String = "Equipped" if equipped(state, offer) else ACTION_LABELS.get(d["primary"], d["primary"])
	# Cards differ in text lines; the button sits at the bottom so every row lines up.
	_spacer(card)
	var button := UI.button(card, label, chooser.bind(page, offer), true, "", 38)
	button.add_theme_font_size_override("font_size", 13)
	button.disabled = equipped(state, offer) or d["primary"] in ["UNAVAILABLE", "LEVEL", "EVENT"]
	return product_panel

## Live game art preview: 3D portrait, board artwork or the dice skin.
static func preview(offer: Dictionary, height: int) -> Control:
	var id: String = offer["id"]
	var holder := Control.new()
	holder.custom_minimum_size = Vector2(height, height)
	holder.mouse_filter = Control.MOUSE_FILTER_IGNORE
	match offer["category"]:
		Catalog.CHARACTER_PACK:
			var portrait := Portrait.new()
			portrait.max_fps = 30
			portrait.character_id = id.trim_prefix("character_")
			portrait.custom_minimum_size = Vector2(height, height)
			portrait.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
			holder.add_child(portrait)
		Catalog.BOARD:
			# Baked once per board and physical size (board_bake.gd), shown as a texture.
			var thumb := BoardThumb.new()
			thumb.board_id = id
			thumb.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
			holder.add_child(thumb)
		Catalog.DICE:
			var dice := DiceView.new()
			dice.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
			dice.style_id = id
			dice.show_outcome(DICE_PREVIEW_FACE.get(id, 6), false)
			holder.add_child(dice)
	return holder

## Live unlock chooser: every way this item can be unlocked right now.
static func chooser(page, offer: Dictionary) -> void:
	var state = page.state
	var d := decision(state, offer)
	if d["primary"] == "SELECT":
		_equip(page, offer)
		return
	var card: VBoxContainer = page.dialog(offer["title"], _offer_text(state, offer), true)
	card.add_child(preview(offer, 150))
	var actions: Array = [d["primary"]]
	if d["alternative"] != null:
		actions.append(d["alternative"])
	for action in actions:
		var choice := UI.button(card, _action_text(state, offer, action), _act.bind(page, offer, action), action == d["primary"])
		if action == d["primary"]:
			Hints.button(choice, "shop.chooser", Hints.Policy.IDLE_SCREEN_MS, 70)
	UI.button(card, "Close", page.dismiss)

static func _offer_text(state, offer: Dictionary) -> String:
	var parts: Array = []
	if int(offer["gemPrice"]) > 0:
		parts.append("%d Gems" % offer["gemPrice"])
	if int(offer["requiredAdViews"]) > 0:
		parts.append("or %d rewarded ads (%d watched)" % [offer["requiredAdViews"], int(state.inventory.ad_progress.get(offer["id"], 0))])
	return " · ".join(parts) if not parts.is_empty() else "Free"

static func _action_text(state, offer: Dictionary, action: String) -> String:
	match action:
		"GEMS": return "Buy for %d Gems" % offer["gemPrice"]
		"PLAY_PRODUCT": return "Buy on Play · %s" % state.billing.query([offer["id"]]).get(offer["id"], {}).get("price", "")
		"WATCH_AD": return "Watch an ad (%d/%d)" % [int(state.inventory.ad_progress.get(offer["id"], 0)), int(offer["requiredAdViews"])]
		"CLAIM_ADS": return "Claim with ads"
		"GET_GEMS": return "Get more Gems"
	return ACTION_LABELS.get(action, action)

static func _act(page, offer: Dictionary, action: String) -> void:
	var state = page.state
	var result := {}
	match action:
		"GEMS":
			result = await state.store.buy_with_gems(offer)
		"CLAIM_ADS":
			result = await state.store.claim_with_ads(offer)
		"PLAY_PRODUCT":
			result = await play_purchase(page, offer["id"])
			if not is_instance_valid(page) or result.get("handled", false):
				return
		"WATCH_AD":
			var shown: Dictionary = await state.ads.show_rewarded(offer["id"])
			if not is_instance_valid(page):
				return
			page.dismiss()
			page.notice("Rewarded ad", "Thanks! Progress updates once the ad is verified." if shown.get("ok", false) else "No ad is available right now.")
			update(page)
			return
		"GET_GEMS":
			page.dismiss()
			page.open(&"wallet")
			return
	if not is_instance_valid(page):
		return
	if result.get("ok", false):
		await _equip(page, offer, true)
	else:
		page.notice("Not unlocked", _error_text(str(result.get("error", ""))))

static func _equip(page, offer: Dictionary, acquired := false) -> void:
	var state = page.state
	if offer["category"] == Catalog.CHARACTER_PACK:
		state.inventory.select_character(str(offer["id"]).trim_prefix("character_"))
	else:
		await state.store.select(offer["id"])
	if not is_instance_valid(page):
		return
	page.dismiss()
	if acquired:
		page.notice("Unlocked", "%s is yours and equipped." % offer["title"])
	update(page)

static func _error_text(code: String) -> String:
	match code:
		"INSUFFICIENT_GEMS": return "Not enough Gems."
		"COSMETIC_PRICE_CHANGED": return "The price changed. Please check the Shop again."
		"AD_PROGRESS_INCOMPLETE": return "Watch the remaining ads first."
		"COSMETIC_UNAVAILABLE": return "This item is not available right now."
		"NOT_ENOUGH_GEMS": return "Not enough Gems."
		"WALLET_NOT_VERIFIED": return "Account or wallet changed. Refresh and try again."
		"ACCOUNT_SESSION_REQUIRED": return "Restore your saved account to use the store."
		"NETWORK_ERROR", "TIMEOUT": return "Connect to the internet and try again."
	return "The purchase could not be completed."

# ---------------------------------------------------------------- wallet (live GEMS tab)

## Gem balance, the live Gem plans in live order with Play's localized prices, the rewarded
## ad (+2 Gems, server-verified), pending Play purchases and Restore. Live keeps no
## transaction history on the client, so none is shown.
static func wallet(page) -> void:
	var body: VBoxContainer = page.body
	var state = page.state
	_sync_store(page)
	var balance := UI.panel(body)
	var balance_row := UI.row(balance, 13)
	balance_row.alignment = BoxContainer.ALIGNMENT_CENTER
	UI.image(balance_row, UI.icon("gem"), 58).custom_minimum_size.x = 64
	var gems := UI.column(balance_row, 0)
	UI.label(gems, "My Gems", 17, true).autowrap_mode = TextServer.AUTOWRAP_OFF
	var balance_label := UI.label(gems, str(state.inventory.gems), 30, true)
	# Title + balance stay fixed; Remove Ads / Free Gems, the plans and orders scroll.
	body = page.begin_list()
	page.state.set_meta("wallet_ui", {"balance": balance_label, "list": body})
	_wallet_list(page, body)

static func _wallet_list(page, body: VBoxContainer) -> void:
	var state = page.state
	var prices: Dictionary = state.billing.query(Catalog.GEM_PLANS)
	_wallet_feature(body, page, prices)
	var plans := UI.label(body, "Gem Plans", 24, true)
	plans.autowrap_mode = TextServer.AUTOWRAP_OFF
	var grid := GridContainer.new()
	grid.columns = 2
	grid.add_theme_constant_override("h_separation", 10)
	grid.add_theme_constant_override("v_separation", 10)
	body.add_child(grid)
	for tier in range(1, Catalog.GEM_PLANS.size()):
		_wallet_pack(grid, page, Catalog.GEM_PLANS[tier], tier, prices)
	var pending: Array = state.get_meta("play_pending", [])
	var orders := UI.panel(body, "Pending Orders")
	if pending.is_empty():
		UI.label(orders, "No pending purchases.", 16, true)
	for p in pending:
		UI.label(orders, "%s · waiting for payment" % Catalog.GEM_PLAN_TITLES.get(p["productId"], p["productId"]), 15)
	var actions := UI.row(body)
	UI.button(actions, "Restore Purchases", _restore.bind(page), true)

static func _wallet_feature(body: Control, page, prices: Dictionary) -> void:
	var feature := UI.panel(body, "", Color("#214b36"))
	feature.add_theme_constant_override("separation", 8)
	var header := UI.row(feature, 9)
	var offer := UI.column(header, 4)
	offer.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_wallet_plank(offer, "Remove Ads")
	var bonus := UI.row(offer, 4)
	bonus.alignment = BoxContainer.ALIGNMENT_CENTER
	UI.image(bonus, UI.icon("gem"), 23).custom_minimum_size.x = 24
	var bonus_copy := UI.label(bonus, "+%d Gems" % Catalog.PLAY_GEM_AMOUNTS["remove_ads"], 17, true)
	bonus_copy.autowrap_mode = TextServer.AUTOWRAP_OFF
	UI.label(offer, "One-time bonus", 13, true)

	var free := UI.panel(header, "", Color("#0f362b"))
	free.add_theme_constant_override("separation", 3)
	var free_card := free.get_parent()
	free_card.custom_minimum_size.x = 144
	free_card.size_flags_horizontal = Control.SIZE_SHRINK_END
	var inset := StyleBoxEmpty.new()
	inset.set_content_margin_all(7)
	free_card.add_theme_stylebox_override("panel", inset)
	UI.label(free, "Free Gems", 16, true)
	var watch := UI.button(free, "+%d · Watch Ad" % Catalog.REWARDED_AD_GEMS,
		_watch_gem_ad.bind(page), true, "play", 31)
	watch.disabled = not page.state.ads.rewarded_available()

	_wallet_price(feature, page, "remove_ads", prices, true)

static func _wallet_pack(grid: Control, page, id: String, tier: int, prices: Dictionary) -> void:
	var card := UI.panel(grid, "", Color("#12362a"))
	card.add_theme_constant_override("separation", 4)
	var panel := card.get_parent()
	var inset := StyleBoxEmpty.new()
	inset.set_content_margin_all(8)
	panel.add_theme_stylebox_override("panel", inset)
	var plan: String = Catalog.GEM_PLAN_TITLES[id]
	var parts := plan.split(" · ", true, 1)
	var badge := parts[1] if parts.size() > 1 else ("Mega" if tier == 4 else "Ultra")
	_wallet_plank(card, badge)
	_wallet_gem_art(card, tier)
	var amount := UI.label(card, parts[0], 19, true)
	amount.autowrap_mode = TextServer.AUTOWRAP_OFF
	_spacer(card)
	_wallet_price(card, page, id, prices, false)

## Flexible gap that pushes a card's button to its bottom edge.
static func _spacer(card: VBoxContainer) -> void:
	var gap := Control.new()
	gap.size_flags_vertical = Control.SIZE_EXPAND_FILL
	gap.mouse_filter = Control.MOUSE_FILTER_IGNORE
	card.add_child(gap)

static func _wallet_plank(parent: Control, title: String) -> void:
	var holder := Control.new()
	holder.custom_minimum_size.y = 36
	parent.add_child(holder)
	var wood := UI.image(holder, UI.art("woodland_header"), 0)
	wood.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	wood.stretch_mode = TextureRect.STRETCH_SCALE
	var label := UI.label(holder, title, 17, true)
	label.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	label.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
	label.autowrap_mode = TextServer.AUTOWRAP_OFF
	label.add_theme_color_override("font_outline_color", Color("#3d2517"))
	label.add_theme_constant_override("outline_size", 4)

static func _wallet_gem_art(parent: Control, tier: int) -> void:
	var holder := Control.new()
	holder.custom_minimum_size.y = 63
	parent.add_child(holder)
	var gems := mini(tier + 1, 5)
	for i in range(gems - 1, -1, -1):
		var primary := i == 0
		var span := 52.0 if primary else (33.0 if i < 3 else 24.0)
		var side := 1.0 if i % 2 == 1 else -1.0
		var x := 0.0 if primary else side * (23.0 if i < 3 else 39.0)
		var y := 2.0 if primary else (12.0 if i < 3 else 20.0)
		var gem := UI.image(holder, UI.icon("gem"), 0)
		gem.set_anchors_and_offsets_preset(Control.PRESET_CENTER)
		gem.offset_left = x - span * 0.5
		gem.offset_right = x + span * 0.5
		gem.offset_top = y - span * 0.5
		gem.offset_bottom = y + span * 0.5

static func _wallet_price(parent: Control, page, id: String, prices: Dictionary, featured: bool) -> void:
	var owned: bool = featured and page.state.inventory.ads_removed
	var price: String = prices.get(id, {}).get("price", Catalog.PRICE_UNAVAILABLE)
	var action := UI.button(parent, "Ads Removed ✓" if owned else price,
		_buy_plan.bind(page, id), featured, "", 37)
	action.disabled = owned or not prices.has(id)

static func _watch_gem_ad(page) -> void:
	var shown: Dictionary = await page.state.ads.show_rewarded()
	if not is_instance_valid(page):
		return
	page.notice("Rewarded ad", "+%d Gems were credited after verification." % Catalog.REWARDED_AD_GEMS if shown.get("ok", false) else "No ad is available right now.")
	update(page)

static func _buy_plan(page, id: String) -> void:
	var result: Dictionary = await play_purchase(page, id)
	if not is_instance_valid(page):
		return
	if result.get("ok", false):
		page.notice("Thank you", "%s added to your wallet." % Catalog.GEM_PLAN_TITLES[id])
	update(page)

## Live purchase -> verification flow: bind the game account, Play sheet, live purchase
## rules, pending token saved securely, the server verifies (and consumes / acknowledges),
## the pending token is removed only after the server's answer.
## -> the verify result, or {handled: true} when a message was already shown.
static func play_purchase(page, product_id: String) -> Dictionary:
	var state = page.state
	var billing = state.billing
	var account = page.get_node_or_null("/root/Account")
	var billing_account := ""
	if account != null and account.available():
		var v: Dictionary = await account.service.ensure_guest_session()
		billing_account = str(v.get("session", {}).get("billingAccountId", "")) if v.get("ok", false) else ""
	elif billing.test_products:
		billing_account = "d".repeat(64)
	billing.set_account(billing_account)
	var sheet: Dictionary = await billing.purchase(product_id)
	if not is_instance_valid(page):
		return {"handled": true}
	match sheet.get("status"):
		"PURCHASED":
			var block: String = billing.verification_block(sheet, billing_account)
			if block == "OTHER_ACCOUNT":
				return {"handled": true}
			if not block.is_empty():
				page.notice("Purchase", block)
				return {"handled": true}
			if not billing.pending_add(product_id, sheet["token"], billing_account):
				page.notice("Purchase", "Could not securely save the purchase for verification. It stays with Google Play and will be recovered.")
				return {"handled": true}
			var result: Dictionary = await state.store.verify_play_purchase(product_id, sheet["token"])
			if result.get("ok", false):
				billing.pending_remove(sheet["token"])
			elif is_instance_valid(page):
				page.notice("Purchase", "The purchase could not be verified yet. It will be retried.")
				result["handled"] = true
			return result
		"PENDING":
			page.notice("Purchase pending", "Your payment is pending. Items arrive once Play confirms it.")
		"CANCELLED":
			pass
		_:
			page.notice("Purchase", str(sheet.get("message", "Purchase failed.")))
	return {"handled": true}

## Live store-open recovery: only this account's saved tokens are verified again.
static func recover_pending(state, billing_account: String) -> int:
	var recovered := 0
	for p in state.billing.pending_all():
		if p.get("accountId") != billing_account:
			continue   # PendingPurchaseAccountPolicy.mayVerify
		var res: Dictionary = await state.store.verify_play_purchase(p["productId"], p["purchaseToken"])
		if res.get("ok", false):
			state.billing.pending_remove(p["purchaseToken"])
			recovered += 1
	return recovered

## Re-verifies every purchase Play still reports for this account (live restore).
static func _restore(page) -> void:
	var state = page.state
	var restored := 0
	var billing_account: String = state.billing.account_id
	for p in await state.billing.owned_purchases():
		if p.get("state") == "PURCHASED" and state.billing.verification_block(p, billing_account).is_empty():
			var verified: Dictionary = await state.store.verify_play_purchase(p["productId"], p["token"])
			if verified.get("ok", false):
				restored += 1
	await state.store.refresh_wallet()
	if not is_instance_valid(page):
		return
	page.notice("Restore Purchases", "%d purchase(s) checked with the server." % restored)
	update(page)
