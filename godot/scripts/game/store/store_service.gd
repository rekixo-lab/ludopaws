extends RefCounted
## Store / wallet / rewards against the live server, which owns Gems and entitlements (live
## AccountRepository + StoreActivity + RewardsActivity flows). Every response that carries a
## wallet replaces the local cache (cosmetic_inventory.gd, live GemWalletStore rules); the
## client never mints value.
##
## Endpoints (live): GET /api/wallet[?adReceiptIds], GET /api/store/catalog,
## POST /api/wallet/cosmetics/purchase {cosmeticId, clientRequestId, unlockChannel?,
## expectedGemPrice?}, POST /api/wallet/cosmetics/select {cosmeticId},
## POST /api/store/purchases/verify {productId, purchaseToken},
## GET|POST /api/rewards {action: daily|spin|collection|bonus-ticket, day, clientRequestId,
## collectionId, expectedPrice} -> {rewards, collections, receipt, wallet}.
##
## `backend` is Callable(method, path, body) -> (await) {ok, data, owner} | {ok: false, error,
## message, status}. On device / desktop with the live config it is the account session
## (guest account created on demand, like live ensureGuestSession). Without a backend URL
## (and in headless tests) it is DesktopBackend: the same server rules, local, UNTESTED ON
## DEVICE.

signal wallet_changed

const Catalog = preload("res://scripts/game/store/store_catalog.gd")
const Inventory = preload("res://scripts/game/store/cosmetic_inventory.gd")
const GameApi = preload("res://scripts/game/online/game_api.gd")

const DEV_ACCOUNT := "00000000-0000-4000-8000-000000000000"

var inventory: Inventory
var catalog := Catalog.new()
var backend: Callable
var desktop: DesktopBackend   # set when running on the local stand-in
## Last /api/rewards response: {rewards, collections, receipt?, wallet} (for synchronous UI).
var last_rewards := {}
var last_error := ""
var _wallet_busy := false

func _init(cache: Inventory, server: Callable = Callable()) -> void:
	inventory = cache
	if server.is_valid():
		backend = server
	else:
		var account = _account_node()
		if account != null and account.available():
			backend = _server_call.bind(account)
		else:
			desktop = DesktopBackend.new()
			backend = desktop.handle
			inventory.account_id = func() -> String: return DEV_ACCOUNT
			# A fresh stand-in continues after the cached revision (the live revision guard
			# would otherwise refuse its first snapshot after an app restart).
			if inventory.prefs.get_string(inventory.KEY_ACCOUNT, "") == DEV_ACCOUNT:
				desktop.revision = inventory.prefs.get_long(inventory.KEY_REVISION, 0)

static func _account_node():
	var tree := Engine.get_main_loop() as SceneTree
	return tree.root.get_node_or_null("Account") if tree != null else null

## Live server call through the account session (ensureGuestSession, then the bearer token).
static func _server_call(method: String, path: String, body: Dictionary, account) -> Dictionary:
	var v: Dictionary = await account.service.ensure_guest_session()
	if not v["ok"]:
		return v
	var res: Dictionary = await account.service._request(method, path, body if method != "GET" else null, v["session"]["accessToken"])
	res["owner"] = v["session"]["accountId"]
	return res

## Applies the wallet in a response to the cache (only for the account that made the call).
func _apply(res: Dictionary) -> Dictionary:
	if not res.get("ok", false):
		last_error = str(res.get("error", ""))
		return res
	var data: Dictionary = res.get("data", {})
	var wallet = data.get("wallet")
	if wallet is Dictionary:
		if not inventory.apply_server_snapshot(wallet, str(res.get("owner", ""))):
			return {"ok": false, "error": "WALLET_NOT_VERIFIED", "message": "Account or wallet changed. Refresh and try again."}
		wallet_changed.emit()
	var out := data.duplicate()
	out["ok"] = true
	return out

func refresh_wallet(ad_receipt_ids := []) -> Dictionary:
	if _wallet_busy:
		return {"ok": false, "error": "BUSY"}
	_wallet_busy = true
	var res := _apply(await backend.call("GET", "/api/wallet" + _receipts(ad_receipt_ids), {}))
	_wallet_busy = false
	return res

## Live StoreCatalogRepository: the published server catalog (parsed like live, cached in
## the live ludopaws_store_catalog_cache_v1 prefs) merged into the built-in catalog. When the
## server cannot be read, the cached copy is used as stale (nothing is purchasable from it).
const CATALOG_PREFS := "ludopaws_store_catalog_cache_v1"

func refresh_catalog() -> Dictionary:
	var NativePrefs = load("res://scripts/game/platform/native_prefs.gd")
	var cache = NativePrefs.open(CATALOG_PREFS)
	var res: Dictionary = await backend.call("GET", "/api/store/catalog", {})
	var parsed := {}
	if res.get("ok", false):
		var raw: Dictionary = res["data"].duplicate()
		raw["ok"] = true
		parsed = Catalog.parse_server_catalog(raw)
		if parsed["ok"]:
			cache.edit().put_string("published_catalog_json", JSON.stringify(raw)) \
				.put_long("published_catalog_fetched_at", int(Time.get_unix_time_from_system() * 1000.0)).commit()
	if not parsed.get("ok", false):
		var text = cache.get_string("published_catalog_json", null)
		var cached = JSON.parse_string(text) if text != null else null
		parsed = Catalog.parse_server_catalog(cached, true) if cached is Dictionary else {"ok": true, "items": [], "suppressed": [], "stale": true}
		if not parsed["ok"]:
			parsed = {"ok": true, "items": [], "suppressed": [], "stale": true}
	catalog.replace_remote(parsed["items"], parsed["suppressed"])
	return {"ok": res.get("ok", false) and not parsed.get("stale", false), "stale": parsed.get("stale", false), "items": parsed["items"].size(),
		"error": res.get("error", "")}

static func _receipts(ids: Array) -> String:
	var valid: Array = []
	var rx := RegEx.create_from_string("^[a-f0-9-]{36}$")
	for id in ids:
		if rx.search(str(id)) != null and not valid.has(str(id)) and valid.size() < 64:
			valid.append(str(id))
	return "" if valid.is_empty() else "?adReceiptIds=" + ",".join(valid)

## Spend Gems (expectedGemPrice guards against a price change between display and checkout).
func buy_with_gems(offer: Dictionary) -> Dictionary:
	return _apply(await backend.call("POST", "/api/wallet/cosmetics/purchase", {"cosmeticId": offer["id"],
		"clientRequestId": GameApi.new_request_id(), "unlockChannel": "GEMS", "expectedGemPrice": int(offer["gemPrice"])}))

## Claim an item whose verified rewarded-ad progress is complete.
func claim_with_ads(offer: Dictionary) -> Dictionary:
	return _apply(await backend.call("POST", "/api/wallet/cosmetics/purchase", {"cosmeticId": offer["id"],
		"clientRequestId": GameApi.new_request_id(), "unlockChannel": "REWARDED_ADS"}))

## Equip an owned item. Live: the selection is saved locally first (selectOwnedLocalCosmetic)
## and confirmed by the server (LocalEquipmentSync); offline it stays pending.
func select(offer_id: String) -> Dictionary:
	var offer = Catalog.built_in(offer_id)
	if offer == null or not inventory.select(offer["category"], offer_id):
		return {"ok": false, "error": "COSMETIC_NOT_OWNED"}
	await sync_equipment()
	return {"ok": true}

## LocalEquipmentSync.request: confirm pending Boards / Dice with the server.
func sync_equipment() -> void:
	var owner: String = inventory._owner()
	var pending: Dictionary = inventory.pending_equipment()
	for category in pending:
		var id: String = pending[category]
		if inventory._owner() != owner:
			return
		var res := _apply(await backend.call("POST", "/api/wallet/cosmetics/select", {"cosmeticId": id}))
		if res.get("ok", false) and res.get("wallet", {}).get("selectedCosmetics", {}).get(category) == id:
			inventory.confirm_equipment(owner, category, id)

## Rewards status (GET /api/rewards): flat {ok, day, dailyClaimed, ...} for the UI, plus
## collections; the full response stays in last_rewards.
func rewards_status(ad_receipt_ids := []) -> Dictionary:
	var res := _apply(await backend.call("GET", "/api/rewards" + _receipts(ad_receipt_ids), {}))
	return _rewards_view(res)

func _rewards_view(res: Dictionary) -> Dictionary:
	if not res.get("ok", false):
		return res
	var rewards = res.get("rewards")
	if not (rewards is Dictionary) or int(rewards.get("day", -1)) < 0 or int(rewards.get("resetsAt", 0)) <= 0:
		return {"ok": false, "error": "INVALID_RESPONSE", "message": "Reward status could not be read. Retry."}
	last_rewards = res
	var flat: Dictionary = rewards.duplicate()
	flat["ok"] = true
	flat["collections"] = res.get("collections", [])
	if res.get("receipt") is Dictionary:
		flat["receipt"] = res["receipt"]
		flat["amount"] = int(res["receipt"].get("amount", 0))
		flat["replay"] = bool(res["receipt"].get("replay", false))
	return flat

## Synchronous view of the last rewards response (empty until the first refresh).
func cached_rewards() -> Dictionary:
	return _rewards_view(last_rewards.merged({"ok": true})) if not last_rewards.is_empty() else {}

## Daily gift / free spin (the server picks the spin result; a new reward day is refused).
func claim_reward(action: String, day: int) -> Dictionary:
	var res := _rewards_view(_apply(await backend.call("POST", "/api/rewards", {"action": action, "day": day,
		"clientRequestId": GameApi.new_request_id()})))
	if res.get("ok", false) and action == "spin" and not [1, 2, 3, 5].has(int(res.get("amount", 0))):
		return {"ok": false, "error": "INVALID_RESPONSE", "message": "Spin receipt could not be read. Retry."}
	return res

## Collection bundle at the server's quote (collectionQuote); the price must still match.
func buy_collection(collection_id: String, expected_price: int) -> Dictionary:
	return _rewards_view(_apply(await backend.call("POST", "/api/rewards", {"action": "collection", "collectionId": collection_id,
		"expectedPrice": expected_price, "clientRequestId": GameApi.new_request_id()})))

## Daily ad bonus ticket (the rewarded ad then carries it; SSV credits the Gems).
func bonus_ticket() -> Dictionary:
	return _rewards_view(_apply(await backend.call("POST", "/api/rewards", {"action": "bonus-ticket"})))

## After Play Billing reports a purchase: the server verifies the token, then grants.
func verify_play_purchase(product_id: String, token: String) -> Dictionary:
	return _apply(await backend.call("POST", "/api/store/purchases/verify", {"productId": product_id, "purchaseToken": token}))

# ---------------------------------------------------------------- desktop stand-in server

## The live PlayerAccount / engagement rules on a local wallet, returning server-shaped
## responses, so Shop / Rewards run on desktop and in tests. Never used on a device build
## with the live config.
class DesktopBackend:
	var catalog := Catalog.new()
	var balance := 0
	var ads_removed := false
	var owned: Array = []
	var selected := {}
	var ad_progress := {}
	var revision := 0
	var tokens := {}
	const DAY_MS := 86400000
	var claims := {}          # "day:action" -> amount
	var daily_claims := 0
	var clock: Callable = func() -> int: return int(Time.get_unix_time_from_system() * 1000.0)
	var spin_roll: Callable = func() -> int: return randi() % 100

	func _init() -> void:
		owned = Catalog.default_owned_ids().duplicate()

	func wallet() -> Dictionary:
		return {"accountId": DEV_ACCOUNT, "revision": revision, "balance": balance, "adsRemoved": ads_removed,
			"ownedCosmetics": owned.duplicate(), "selectedCosmetics": selected.duplicate(), "rewardedAdProgress": ad_progress.duplicate(),
			"creditedAdRequestIds": []}

	func _ok(data := {}) -> Dictionary:
		revision += 1
		data["wallet"] = wallet()
		return {"ok": true, "data": data, "owner": DEV_ACCOUNT}

	static func _fail(code: String, status := 409) -> Dictionary:
		return {"ok": false, "error": code, "message": code, "status": status}

	func handle(method: String, path: String, body: Dictionary) -> Dictionary:
		match path.split("?")[0]:
			"/api/wallet":
				return {"ok": true, "data": {"wallet": wallet()}, "owner": DEV_ACCOUNT}
			"/api/store/catalog":
				return {"ok": true, "data": {"items": [], "suppressedIds": []}, "owner": DEV_ACCOUNT}
			"/api/wallet/cosmetics/select":
				var id := str(body.get("cosmeticId", ""))
				var offer = Catalog.built_in(id)
				if offer == null or not owned.has(id):
					return _fail("COSMETIC_NOT_OWNED")
				selected[offer["category"]] = id
				return _ok()
			"/api/wallet/cosmetics/purchase":
				return _purchase(body)
			"/api/store/purchases/verify":
				return _verify(body)
			"/api/rewards":
				return _rewards(method, body)
		return _fail("NOT_FOUND", 404)

	func _purchase(body: Dictionary) -> Dictionary:
		var id := str(body.get("cosmeticId", ""))
		var offer = catalog.find_shop_item(id)
		if offer == null:
			return _fail("COSMETIC_UNAVAILABLE", 404)
		if owned.has(id):
			return _ok({"replay": true})
		match body.get("unlockChannel"):
			"GEMS":
				if not offer["channels"].has("GEMS") and offer["unlockKind"] != "GEMS":
					return _fail("COSMETIC_CHANNEL_INVALID")
				if int(body.get("expectedGemPrice", -1)) != int(offer["gemPrice"]):
					return _fail("COSMETIC_PRICE_CHANGED")
				if balance < int(offer["gemPrice"]):
					return _fail("INSUFFICIENT_GEMS")
				balance -= int(offer["gemPrice"])
			"REWARDED_ADS":
				if not offer["channels"].has("REWARDED_ADS") or int(ad_progress.get(id, 0)) < int(offer["requiredAdViews"]):
					return _fail("AD_PROGRESS_INCOMPLETE")
			_:
				return _fail("COSMETIC_CHANNEL_INVALID", 400)
		owned.append(id)
		return _ok()

	func _verify(body: Dictionary) -> Dictionary:
		var product := str(body.get("productId", ""))
		var token := str(body.get("purchaseToken", ""))
		if token.length() < 8 or not Catalog.all_play_product_ids().has(product):
			return _fail("INVALID_PURCHASE", 400)
		if tokens.has(token):
			return _ok({"replay": true})
		tokens[token] = product
		if Catalog.PLAY_GEM_AMOUNTS.has(product):
			balance += int(Catalog.PLAY_GEM_AMOUNTS[product])
			if product == "remove_ads":
				ads_removed = true
		elif not owned.has(product):
			owned.append(product)
		return _ok()

	func _day() -> int:
		return int(clock.call()) / DAY_MS

	# engagement-rewards.js: DAILY_GEMS 3, spin 1/2/3/5 at 50/30/15/5 %, bonus 2 once a day,
	# milestone every 7 daily claims.
	func status() -> Dictionary:
		var day := _day()
		return {"day": day, "resetsAt": (day + 1) * DAY_MS, "dailyGems": 3,
			"dailyClaimed": claims.has("%d:daily" % day), "spinClaimed": claims.has("%d:spin" % day),
			"spinGems": int(claims.get("%d:spin" % day, 0)), "bonusClaimed": claims.has("%d:bonus" % day), "bonusGems": 2,
			"dailyClaims": daily_claims, "nextMilestone": (daily_claims / 7 + 1) * 7, "milestoneTier": daily_claims / 7,
			"spinOutcomes": [{"gems": 1, "percent": 50}, {"gems": 2, "percent": 30}, {"gems": 3, "percent": 15}, {"gems": 5, "percent": 5}]}

	func _rewards(method: String, body: Dictionary) -> Dictionary:
		var receipt = null
		if method == "POST":
			var action := str(body.get("action", ""))
			var day := _day()
			match action:
				"daily", "spin":
					if int(body.get("day", -1)) != day:
						return _fail("REWARD_DAY_CHANGED")
					var key := "%d:%s" % [day, action]
					if claims.has(key):
						receipt = {"day": day, "action": action, "amount": claims[key], "replay": true}
					else:
						var amount := 3
						if action == "spin":
							var roll: int = spin_roll.call()
							amount = 1 if roll < 50 else (2 if roll < 80 else (3 if roll < 95 else 5))
						claims[key] = amount
						if action == "daily":
							daily_claims += 1
						balance += amount
						receipt = {"day": day, "action": action, "amount": amount, "replay": false}
				"bonus-ticket":
					if claims.has("%d:bonus" % day):
						return _fail("DAILY_BONUS_CLAIMED")
					receipt = {"ticket": "dev-ticket-%d" % day, "day": day}
				"collection":
					return _fail("COLLECTION_NOT_FOUND", 404)
				_:
					return _fail("REWARD_ACTION_INVALID", 400)
		return _ok({"rewards": status(), "collections": [], "receipt": receipt})

	## Desktop stand-in for a verified AdMob SSV callback (UNTESTED ON DEVICE).
	func credit_rewarded_ad(cosmetic_id := "") -> void:
		if cosmetic_id.is_empty():
			balance += 2
		else:
			ad_progress[cosmetic_id] = int(ad_progress.get(cosmetic_id, 0)) + 1
		revision += 1

	## Daily ad bonus: one verified rewarded ad per reward day (server ticket + SSV).
	func credit_daily_bonus() -> bool:
		var key := "%d:bonus" % _day()
		if claims.has(key):
			return false
		claims[key] = 2
		balance += 2
		revision += 1
		return true
