extends RefCounted
## Live store content: only items the live app actually sells (owner scope correction
## 2026-11-10). Offers, prices, unlock channels, rewarded-ad counts, Play product ids,
## defaults, and the remote /api/store/catalog merge.
## Ports StoreCosmeticCatalog.kt, CosmeticPricing.kt, StoreCatalog.kt (gem plans) and the
## server's cosmetic-pricing.js / google-play-billing.js product table.

const BOARD := "BOARD"
const DICE := "DICE"
const AVATAR := "AVATAR"
const CHARACTER_PACK := "CHARACTER_PACK"

const FREE := "FREE"
const GEMS := "GEMS"
const PLAY_PRODUCT := "PLAY_PRODUCT"
const REWARDED_ADS := "REWARDED_ADS"
const LEVEL := "LEVEL"
const EVENT := "EVENT"

const DEFAULT_BOARD := "board_classic"
const DEFAULT_DICE := "dice_classic"
const DEFAULT_AVATAR := "avatar_4"
const DEFAULT_CHARACTER_PACK := "character_pack_starter_paws"
const FREE_CHARACTER_IDS := ["dog", "goat", "duck", "cat"]

## Live shop order inside each category.
const BOARD_IDS := ["board_classic", "board_astral", "board_cloud_lagoon", "board_autumn_forest",
	"board_blossom_garden", "board_winter_haven", "board_monsoon_mire", "board_sunstone_palace"]
const DICE_IDS := ["dice_classic", "dice_pearl_paw", "dice_forest_dew", "dice_lagoon_pearl",
	"dice_frost_crystal", "dice_starlight", "dice_ember_forge", "dice_prism_royal"]
## characterUnlocks order: lumi, donkey, giraffe, then CosmeticPricing.paidCharacterIds.
const CHARACTER_OFFER_IDS := ["character_goat_lumi", "character_donkey", "character_giraffe",
	"character_panda", "character_monkey", "character_cock", "character_pig", "character_elephant",
	"character_horse", "character_rabbit"]
const AVATAR_IDS := ["avatar_9", "avatar_4", "avatar_3", "avatar_5", "avatar_6", "avatar_7", "avatar_1",
	"avatar_8", "avatar_2", "avatar_10"]

## Gem plan titles exactly as the live StoreCatalog.products (Remove Ads is the featured plan).
const GEM_PLAN_TITLES := {"remove_ads": "Remove Ads", "gems_300": "300 Gems · Starter", "gems_1500": "1,200 Gems · Value",
	"gems_3500": "3,000 Gems · Popular", "gems_20000": "7,500 Gems", "gems_40000": "16,000 Gems", "gems_200000": "35,000 Gems · Best Value"}
## Shown when Play has not returned a localized price (live PRICE_UNAVAILABLE).
const PRICE_UNAVAILABLE := "Price unavailable"
const REWARDED_AD_GEMS := 2

## Gem plans (StoreCatalog.kt order) -> Gems granted by the server (google-play-billing.js).
const GEM_PLANS := ["remove_ads", "gems_300", "gems_1500", "gems_3500", "gems_20000", "gems_40000", "gems_200000"]
const PLAY_GEM_AMOUNTS := {"remove_ads": 5, "gems_300": 300, "gems_1500": 1200, "gems_3500": 3000,
	"gems_20000": 7500, "gems_40000": 16000, "gems_200000": 35000}

## Built-in offer for a live id, after CosmeticPricing.apply. null = not a live item.
static func built_in(id: String) -> Variant:
	if id in BOARD_IDS:
		return _offer(id, BOARD, _title(id))
	if id in DICE_IDS:
		return _offer(id, DICE, _title(id))
	if id in CHARACTER_OFFER_IDS or id == DEFAULT_CHARACTER_PACK:
		return _offer(id, CHARACTER_PACK, "STARTER PAWS" if id == DEFAULT_CHARACTER_PACK else _title(id))
	if id in AVATAR_IDS:
		return _offer(id, AVATAR, "Avatar " + id.trim_prefix("avatar_"))
	return null

static func _offer(id: String, category: String, title: String) -> Dictionary:
	var o := {"id": id, "title": title, "category": category, "unlockKind": FREE, "gemPrice": 0, "requiredLevel": 0,
		"requiredAdViews": 0, "channels": [], "contentAvailable": true, "purchaseAvailable": true, "remoteManaged": false}
	match id:
		"character_goat_lumi":
			o.merge({"unlockKind": GEMS, "gemPrice": 50, "requiredAdViews": 10, "channels": [GEMS, REWARDED_ADS]}, true)
		"character_donkey":
			o.merge({"unlockKind": GEMS, "gemPrice": 75, "requiredAdViews": 15, "channels": [GEMS, REWARDED_ADS]}, true)
		"dice_pearl_paw":
			o.merge({"unlockKind": GEMS, "gemPrice": 100, "requiredAdViews": 20, "channels": [GEMS, REWARDED_ADS]}, true)
		"avatar_2", "avatar_10":
			o.merge({"unlockKind": GEMS, "gemPrice": 100}, true)
	if play_product_ids().has(id):
		var astral := id == "board_astral"
		var price := 300
		match id:
			"character_panda", "character_monkey": price = 150
			"dice_forest_dew", "dice_lagoon_pearl", "dice_frost_crystal", "dice_starlight": price = 100
			"dice_ember_forge", "dice_prism_royal": price = 150
		o.merge({"unlockKind": GEMS, "gemPrice": price, "requiredAdViews": 15 if astral else 0,
			"channels": [GEMS, PLAY_PRODUCT] + ([REWARDED_ADS] if astral else [])}, true)
	return o

## Live offer titles (definition titles / characterUnlocks titles).
static func _title(id: String) -> String:
	match id:
		"board_astral": return "ASTRAL GARDEN"
		"character_goat_lumi": return "LUMI GOAT"
	return id.trim_prefix("character_").trim_prefix("board_").trim_prefix("dice_").replace("_", " ").to_upper()

## Non-consumable cosmetic Play products (COSMETIC_PRICING keys).
static func play_product_ids() -> Array:
	return ["character_panda", "character_monkey", "character_cock", "character_pig", "character_elephant",
		"character_horse", "character_rabbit", "board_astral", "board_cloud_lagoon", "board_autumn_forest",
		"board_blossom_garden", "board_winter_haven", "board_monsoon_mire", "character_giraffe",
		"board_sunstone_palace", "dice_forest_dew", "dice_lagoon_pearl", "dice_frost_crystal", "dice_starlight",
		"dice_ember_forge", "dice_prism_royal"]

## Every Play product id the app may query: gem plans + cosmetic entitlements.
static func all_play_product_ids() -> Array:
	return GEM_PLANS + play_product_ids()

static func default_id(category: String) -> String:
	return {BOARD: DEFAULT_BOARD, DICE: DEFAULT_DICE, AVATAR: DEFAULT_AVATAR}.get(category, DEFAULT_CHARACTER_PACK)

## defaultOwnedIds: shipped FREE built-ins (granted at account creation).
static func default_owned_ids() -> Array:
	var out: Array = []
	for id in BOARD_IDS + DICE_IDS + AVATAR_IDS + [DEFAULT_CHARACTER_PACK]:
		var o: Dictionary = built_in(id)
		if o["unlockKind"] == FREE and o["contentAvailable"]:
			out.append(id)
	return out

## The shop entitlement id that protects a character (null = not sold / free).
static func character_entitlement_id(character_id: String) -> Variant:
	var id := "character_" + character_id
	return id if id in CHARACTER_OFFER_IDS else null

# ---------------------------------------------------------------- server catalog (StoreCatalogParser)

const MAX_ITEMS := 500
const MAX_SUPPRESSED_IDS := 1000
const MAX_GEM_PRICE := 2000000000
const MAX_REWARDED_ADS := 100
const ASSET_FORMATS := ["GLB", "GLTF", "PNG_SET", "SPRITE_ATLAS", "BUILT_IN"]
const CHANNEL_PRIORITY := [GEMS, REWARDED_ADS, FREE, LEVEL, EVENT, PLAY_PRODUCT]
const BACKEND_CATEGORIES := {"PAW": CHARACTER_PACK, "BOARD": BOARD, "DICE": DICE, "AVATAR": AVATAR}

## Live StoreCatalogParser.parse: the whole response is rejected when any item is invalid.
## Returns {ok, version, items: [offer], suppressed: [id]} or {ok: false, error}.
static func parse_server_catalog(response: Dictionary, stale := false) -> Dictionary:
	var fail := func(m: String) -> Dictionary: return {"ok": false, "error": m}
	if not response.get("ok", false):
		return fail.call("Catalog response did not confirm success.")
	var version := int(response.get("catalogVersion", -1))
	if version < 0:
		return fail.call("Catalog version is invalid.")
	var entries = response.get("items")
	if not (entries is Array) or entries.size() > MAX_ITEMS:
		return fail.call("Catalog items are missing or too many.")
	var id_rx := RegEx.create_from_string(ID_PATTERN)
	var sha_rx := RegEx.create_from_string("^[a-f0-9]{64}$")
	var items: Array = []
	var seen := {}
	for item in entries:
		if not (item is Dictionary):
			return fail.call("Catalog item is not an object.")
		var id := str(item.get("id", ""))
		if id_rx.search(id) == null or item.get("status") != "PUBLISHED" or not item.get("contentReady", false):
			return fail.call("Catalog item is invalid or unpublished.")
		var category = BACKEND_CATEGORIES.get(str(item.get("category", "")))
		if category == null:
			return fail.call("Catalog category is unsupported by this app.")
		var item_version := int(item.get("catalogVersion", -1))
		var asset = item.get("asset")
		if item_version < 1 or item_version > version or not (asset is Dictionary) or str(asset.get("thumbnail", "")).strip_edges().is_empty() \
				or str(asset.get("manifest", "")).strip_edges().is_empty() or int(asset.get("version", 0)) <= 0 \
				or sha_rx.search(str(asset.get("sha256", ""))) == null or not ASSET_FORMATS.has(str(asset.get("format", ""))):
			return fail.call("Catalog item metadata is invalid.")
		var unlock = item.get("unlock")
		var channels_json = unlock.get("channels") if unlock is Dictionary else null
		if not (channels_json is Array) or channels_json.size() < 1 or channels_json.size() > 6:
			return fail.call("Catalog unlock channels are invalid.")
		var channels: Array = []
		for c in channels_json:
			if not CHANNEL_PRIORITY.has(str(c)) or channels.has(str(c)):
				return fail.call("Catalog has an unknown or duplicated unlock channel.")
			channels.append(str(c))
		var gems = unlock.get("gems") if unlock.get("gems") is Dictionary else {}
		var ads = unlock.get("rewardedAds") if unlock.get("rewardedAds") is Dictionary else {}
		if channels.has(GEMS) and (not gems.get("enabled", false) or int(gems.get("price", 0)) < 1 or int(gems.get("price", 0)) > MAX_GEM_PRICE):
			return fail.call("Catalog Gem price is invalid.")
		if channels.has(REWARDED_ADS) and (not ads.get("enabled", false) or int(ads.get("requiredCompletedAds", 0)) < 1 or int(ads.get("requiredCompletedAds", 0)) > MAX_REWARDED_ADS):
			return fail.call("Catalog rewarded-ad target is invalid.")
		if seen.has(id):
			return fail.call("Catalog contains a duplicate item ID.")
		seen[id] = true
		var built = built_in(id)
		var primary: String = channels[0]
		for c in CHANNEL_PRIORITY:
			if channels.has(c):
				primary = c
				break
		var title := str(item.get("name", "")).strip_edges().left(80)
		var level = unlock.get("level")
		items.append({"id": id, "title": title if not title.is_empty() else id, "category": category, "unlockKind": primary,
			"gemPrice": int(gems.get("price", 0)) if channels.has(GEMS) else 0,
			"requiredLevel": int(level.get("minimumLevel", 0)) if level is Dictionary else 0,
			"requiredAdViews": int(ads.get("requiredCompletedAds", 0)) if channels.has(REWARDED_ADS) else 0,
			"channels": channels,
			# Only an already-shipped renderer is interactive; new remote content stays locked.
			"contentAvailable": built != null and built["category"] == category and built["contentAvailable"],
			"purchaseAvailable": not stale, "remoteManaged": true, "catalogVersion": item_version})
	var suppressed: Array = []
	var sup = response.get("suppressedIds", [])
	if not (sup is Array) or sup.size() > MAX_SUPPRESSED_IDS:
		return fail.call("Catalog suppression list is too large.")
	for id in sup:
		if id_rx.search(str(id)) != null and not seen.has(str(id)) and not suppressed.has(str(id)):
			suppressed.append(str(id))
	return {"ok": true, "version": version, "items": items, "suppressed": suppressed, "stale": stale}

# ---------------------------------------------------------------- remote catalog merge

var remote := {}        # id -> offer from /api/store/catalog (remoteManaged)
var suppressed := {}    # legacy ids hidden by the server

const ID_PATTERN := "^[a-z0-9][a-z0-9_-]{2,63}$"

## replaceRemoteCatalog: published items win over suppression metadata for the same id.
func replace_remote(items: Array, suppressed_ids: Array) -> void:
	var regex := RegEx.create_from_string(ID_PATTERN)
	remote.clear()
	for item in items:
		if item.get("remoteManaged", false) and regex.search(str(item.get("id", ""))) != null and not remote.has(item["id"]):
			remote[item["id"]] = item
	suppressed.clear()
	for id in suppressed_ids:
		if regex.search(str(id)) != null and not remote.has(id):
			suppressed[id] = true

func clear_remote() -> void:
	remote.clear()
	suppressed.clear()

## mergedForCategory: live built-ins (minus suppressed / category-mismatched), server values
## win for released content but local content readiness is still required; unknown remote
## items stay hidden. Avatars are not sold in the live Shop.
func for_category(category: String) -> Array:
	if category == AVATAR:
		return []
	var ids: Array = {BOARD: BOARD_IDS, DICE: DICE_IDS}.get(category, [DEFAULT_CHARACTER_PACK] + CHARACTER_OFFER_IDS)
	var out: Array = []
	for id in ids:
		var built: Dictionary = built_in(id)
		var server = remote.get(id)
		if suppressed.has(id) or (server != null and server.get("category") != category):
			continue
		var offer := built
		if server != null:
			offer = server.duplicate(true)
			offer["title"] = server.get("title", built["title"])
			offer["contentAvailable"] = built["contentAvailable"] and server.get("contentAvailable", true)
			for key in ["unlockKind", "gemPrice", "requiredLevel", "requiredAdViews", "channels", "purchaseAvailable"]:
				if not offer.has(key):
					offer[key] = built[key]
		if offer["contentAvailable"]:
			out.append(offer)
	return out

## Shop entry points only use visible, released items.
func find_shop_item(id: String) -> Variant:
	for category in [BOARD, DICE, CHARACTER_PACK]:
		for offer in for_category(category):
			if offer["id"] == id:
				return offer
	return null

## SmartUnlockPolicy.decide -> {primary, alternative}. Never debits, plays or grants anything.
static func smart_unlock(offer: Dictionary, owned: bool, gems: int, ad_progress: int, play_available: bool, ads_available: bool) -> Dictionary:
	if owned:
		return {"primary": "SELECT", "alternative": null}
	if not offer.get("contentAvailable", true) or not offer.get("purchaseAvailable", true):
		return {"primary": "UNAVAILABLE", "alternative": null}
	if int(offer.get("requiredLevel", 0)) > 0:
		return {"primary": "LEVEL", "alternative": null}
	var channels: Array = offer.get("channels", [])
	if channels.is_empty():
		channels = [offer.get("unlockKind", FREE)]
	var price := int(offer.get("gemPrice", 0))
	var ads := int(offer.get("requiredAdViews", 0))
	var gems_possible := channels.has(GEMS) and price > 0
	var can_spend := gems_possible and gems >= price
	var ads_possible := channels.has(REWARDED_ADS) and ads > 0
	var play_possible := play_available and channels.has(PLAY_PRODUCT)
	var ad_action = null
	if ads_possible:
		if ad_progress >= ads:
			ad_action = "CLAIM_ADS"
		elif ads_available:
			ad_action = "WATCH_AD"
	if can_spend:
		return {"primary": "GEMS", "alternative": "PLAY_PRODUCT" if play_possible else ad_action}
	if ad_action == "CLAIM_ADS":
		return {"primary": "CLAIM_ADS", "alternative": "PLAY_PRODUCT" if play_possible else null}
	if play_possible:
		return {"primary": "PLAY_PRODUCT", "alternative": ad_action}
	if ad_action != null:
		return {"primary": ad_action, "alternative": "GET_GEMS" if gems_possible else null}
	if gems_possible:
		return {"primary": "GET_GEMS", "alternative": null}
	for kind in [FREE, LEVEL, EVENT]:
		if channels.has(kind):
			return {"primary": kind, "alternative": null}
	return {"primary": "UNAVAILABLE", "alternative": null}
