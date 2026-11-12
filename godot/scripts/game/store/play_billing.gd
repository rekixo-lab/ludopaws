extends RefCounted
## Google Play Billing (live GooglePlayBillingGateway + PendingStorePurchaseStore) through the
## LudoPawsPlatform plugin (Billing 9.1.0, the live version). Prices are Play's localized
## strings, never from this repo. Purchases are bound to the game account's obfuscated
## billing id; the server verifies every token and itself consumes / acknowledges it (the
## client never does). Tokens wait in the live encrypted pending store until the server
## confirms, so a crash never loses a paid item.
##
## Enabled only on Android with [features] billing_enabled = true in the local config (CI:
## LUDOPROOF_BILLING_ENABLED) and the server's billingAvailable. Desktop / tests: a stand-in
## (`test_products = true` returns fake products and tokens). UNTESTED ON DEVICE.

const Catalog = preload("res://scripts/game/store/store_catalog.gd")
const ACCOUNT_ID := "^[a-f0-9]{64}$"
const PRODUCT_ID := "^[A-Za-z0-9._-]{1,128}$"
const MAX_PENDING := 100

var is_stub := true
## Tests only: report every Play product with a placeholder price.
var test_products := false
var server_available := false
var account_id := ""
var _products := {}
var _platform = null
var _desktop_pending: Array = []

func _init() -> void:
	if Engine.has_singleton("LudoPawsPlatform"):
		var cfg := ConfigFile.new()
		cfg.load("res://config/release.local.cfg")
		if bool(cfg.get_value("features", "billing_enabled", false)):
			_platform = Engine.get_singleton("LudoPawsPlatform")
			is_stub = false

func enabled() -> bool:
	return (not is_stub and server_available) or test_products

## Next billing signal of one of `kinds` -> parsed JSON (with "kind").
func _next(kinds: Array) -> Dictionary:
	while true:
		var args: Array = await _platform.billing
		if kinds.has(args[0]):
			var data = JSON.parse_string(str(args[1]))
			var out: Dictionary = data if data is Dictionary else {}
			out["kind"] = args[0]
			return out
	return {}

func connect_billing() -> bool:
	if is_stub:
		return test_products
	_platform.billingConnect()
	return bool((await _next(["ready"])).get("ok", false))

## The account binding from the authenticated server (live updateAccountId).
func set_account(billing_account_id: String) -> void:
	account_id = billing_account_id if RegEx.create_from_string(ACCOUNT_ID).search(billing_account_id) != null else ""
	if _platform != null:
		_platform.billingSetAccount(account_id)

## Cached Play prices: id -> {price}.
func query(ids: Array) -> Dictionary:
	if _products.is_empty() and test_products:
		for id in Catalog.all_play_product_ids():
			_products[id] = {"price": "₹0.00", "title": id}
	var out := {}
	for id in ids:
		if _products.has(id):
			out[id] = _products[id]
	return out

## Live queryProducts (INAPP, every Play product id).
func refresh_products() -> bool:
	if is_stub or not await connect_billing():
		return false
	_platform.billingQueryProducts(JSON.stringify(Catalog.all_play_product_ids()))
	var res := await _next(["products"])
	_products.clear()
	var prices = res.get("products", {})
	if prices is Dictionary:
		for id in prices:
			_products[id] = {"price": str(prices[id]), "title": id}
	return res.get("ok", false)

## Opens the Play sheet. -> {status: PURCHASED|PENDING|CANCELLED|OWNED|ERROR, productId,
## token, accountId, message}
func purchase(product_id: String) -> Dictionary:
	if not Catalog.all_play_product_ids().has(product_id):
		return {"status": "ERROR", "message": "This product is not sold here."}
	if is_stub:
		if not test_products:
			return {"status": "ERROR", "message": "Purchases are not available in this build."}
		return {"status": "PURCHASED", "productId": product_id, "token": "desktop-test-%s-%d" % [product_id, Time.get_ticks_usec()], "accountId": account_id}
	if account_id.is_empty():
		return {"status": "ERROR", "message": "Reconnect to your game account before purchasing."}
	load("res://scripts/game/growth/referral_service.gd").fullscreen_interaction()
	_platform.billingPurchase(product_id)
	while true:
		var res := await _next(["purchases", "error"])
		if res["kind"] == "error":
			match str(res.get("code", "")):
				"USER_CANCELED": return {"status": "CANCELLED"}
				"ITEM_ALREADY_OWNED": return {"status": "OWNED", "message": "Google Play already owns this item. Restore purchases to recover it."}
			return {"status": "ERROR", "message": str(res.get("message", "Google Play purchase failed."))}
		for p in res.get("purchases", []):
			if Array(p.get("products", [])).has(product_id):
				return {"status": str(p.get("state", "")) if p.get("state") in ["PURCHASED", "PENDING"] else "ERROR",
					"productId": product_id, "products": p.get("products", []), "token": str(p.get("token", "")), "accountId": p.get("accountId")}
	return {"status": "ERROR"}

## Purchases Play still reports (restore / recovery): [{productId, token, state, accountId}].
func owned_purchases() -> Array:
	if is_stub:
		return []
	if not await connect_billing():
		return []
	_platform.billingQueryPurchases()
	var res := await _next(["owned"])
	var out: Array = []
	for p in res.get("purchases", []):
		var products: Array = p.get("products", [])
		out.append({"productId": products[0] if products.size() == 1 else "", "products": products, "token": str(p.get("token", "")),
			"state": str(p.get("state", "")), "accountId": p.get("accountId")})
	return out

## Live processPurchases rules: one known product; restorable non-consumables need their own
## binding; a Gem purchase of another account stays with Play; the binding must be valid.
## -> "" when the purchase may be verified now, "OTHER_ACCOUNT", or the reason.
static func verification_block(p: Dictionary, current_account: String) -> String:
	var products: Array = p.get("products", [p.get("productId", "")])
	var product := str(products[0]) if products.size() == 1 else ""
	if products.size() != 1 or not Catalog.all_play_product_ids().has(product):
		return "A Google Play purchase has an unsupported product shape; no entitlement was granted."
	var bound = p.get("accountId")
	var restorable := product == "remove_ads" or Catalog.play_product_ids().has(product)
	if bound == null and restorable:
		return "This purchase has no recoverable Ludo Paws account binding. Restore the account that made the purchase; no entitlement was granted."
	if bound != current_account and not restorable:
		return "OTHER_ACCOUNT"
	if bound == null or RegEx.create_from_string(ACCOUNT_ID).search(str(bound)) == null:
		return "This Play purchase has an invalid Ludo Paws account binding. No content was granted."
	return ""

# ---------------------------------------------------------------- pending purchases

func pending_all() -> Array:
	if _platform == null:
		return _desktop_pending.duplicate(true)
	var res = JSON.parse_string(str(_platform.pendingPurchasesLoad()))
	if not (res is Dictionary) or not res.get("ok", false):
		return []   # unreadable data stays on disk, never dropped
	var list = JSON.parse_string(str(res.get("json", "[]")))
	return list if list is Array else []

static func _valid_pending(p: Dictionary) -> bool:
	return RegEx.create_from_string(PRODUCT_ID).search(str(p.get("productId", ""))) != null \
		and str(p.get("purchaseToken", "")).length() >= 8 and str(p.get("purchaseToken", "")).length() <= 2048 \
		and RegEx.create_from_string(ACCOUNT_ID).search(str(p.get("accountId", ""))) != null

func _persist(list: Array) -> bool:
	if _platform == null:
		_desktop_pending = list.duplicate(true)
		return true
	return bool(_platform.pendingPurchasesSave(JSON.stringify(list)))

func pending_add(product_id: String, token: String, billing_account: String) -> bool:
	var entry := {"productId": product_id, "purchaseToken": token, "accountId": billing_account}
	if not _valid_pending(entry):
		return false
	var list := pending_all()
	for p in list:
		if p.get("purchaseToken") == token:
			return p == entry
	if list.size() >= MAX_PENDING:
		return false
	list.append(entry)
	return _persist(list)

func pending_remove(token: String) -> void:
	_persist(pending_all().filter(func(p): return p.get("purchaseToken") != token))

func pending_remove_account(billing_account: String) -> void:
	_persist(pending_all().filter(func(p): return p.get("accountId") != billing_account))
