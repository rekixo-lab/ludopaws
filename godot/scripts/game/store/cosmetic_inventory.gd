extends RefCounted
## Local CACHE of the server wallet. Port of live GemWalletStore + CosmeticInventoryStore +
## CharacterSelectionStore over the live app's own preference files (NativePrefs):
## `ludoproof_wallet` (gem_balance, wallet_account_id, owned_cosmetics, selected_*,
## ad_progress_*, local_equipment_*, offline_selection_*) and
## `ludo_paws_character_selection_v1` (pack_id, character_id). A player's cache from the
## Kotlin app is therefore read as-is after the update.
##
## Rules (live): the server (PlayerAccount) is the authority; only a server snapshot for the
## signed-in account is cached (account id must match, balance 0..2e9, revision never goes
## back); a different account clears the previous account's cache; nothing here mints Gems,
## ownership or ad progress. Without a snapshot for the current account only shipped free
## items are owned and the balance reads 0. Offline: free items are selectable
## (offline_selection_*), owned Boards / Dice are equipped locally and synced to the server
## later (local_equipment_{account}_{category}, LocalEquipmentSync).

signal changed

const Catalog = preload("res://scripts/game/store/store_catalog.gd")
const Characters = preload("res://scripts/game/characters/catalog.gd")
const NativePrefs = preload("res://scripts/game/platform/native_prefs.gd")

const PREFS := "ludoproof_wallet"
const CHARACTER_PREFS := "ludo_paws_character_selection_v1"
const MAX_BALANCE := 2000000000
const KEY_BALANCE := "gem_balance"
const KEY_ACCOUNT := "wallet_account_id"
const KEY_REVISION := "wallet_revision_v1"
const KEY_ADS_REMOVED := "ads_removed"
const KEY_OWNED := "owned_cosmetics"
const KEY_TOKENS := "verified_purchase_tokens"
const KEY_EVENTS := "verified_event_entitlements"
const KEY_ARCHIVE := "legacy_wallet_unverified_archive_v1"
const KEY_ARCHIVE_NOTICE := "legacy_wallet_unverified_notice_shown_v1"
const KEY_AD_PREFIX := "ad_progress_"
const SELECTED_KEYS := {"BOARD": "selected_board", "DICE": "selected_dice", "AVATAR": "selected_avatar", "CHARACTER_PACK": "selected_character_pack"}

var prefs
var characters
## Callable() -> String: the signed-in account id ("" without a session).
var account_id: Callable

func _init(provider: Callable = Callable()) -> void:
	prefs = NativePrefs.open(PREFS)
	characters = NativePrefs.open(CHARACTER_PREFS)
	account_id = provider if provider.is_valid() else _session_account

static func _session_account() -> String:
	var tree := Engine.get_main_loop() as SceneTree
	var account = tree.root.get_node_or_null("Account") if tree != null else null
	return str(account.session().get("accountId", "")) if account != null else ""

func _owner() -> String:
	return str(account_id.call())

func _has_snapshot() -> bool:
	var owner := _owner()
	return not owner.is_empty() and prefs.get_string(KEY_ACCOUNT, "") == owner

# ---------------------------------------------------------------- reads (V2 interface)

var gems: int:
	get: return clampi(prefs.get_int(KEY_BALANCE, 0), 0, MAX_BALANCE) if _has_snapshot() else 0

var ads_removed: bool:
	get: return _has_snapshot() and prefs.get_bool(KEY_ADS_REMOVED, false)

## id -> true: shipped free items + the cached server inventory of the current account.
var owned: Dictionary:
	get:
		var out := {}
		for id in Catalog.default_owned_ids():
			out[id] = true
		if _has_snapshot():
			for id in prefs.get_string_set(KEY_OWNED, []):
				out[str(id)] = true
		return out

## category -> selected id (see selected_id).
var selected: Dictionary:
	get:
		var out := {}
		for category in [Catalog.BOARD, Catalog.DICE, Catalog.AVATAR, Catalog.CHARACTER_PACK]:
			out[category] = selected_id(category)
		out["CHARACTER"] = selected_character()
		return out

## cosmetic id -> verified rewarded-ad progress (server snapshot only).
var ad_progress: Dictionary:
	get:
		var out := {}
		if _has_snapshot():
			for key in prefs.keys():
				if key.begins_with(KEY_AD_PREFIX):
					out[key.trim_prefix(KEY_AD_PREFIX)] = maxi(prefs.get_int(key, 0), 0)
		return out

func is_owned(id: String) -> bool:
	return owned.has(id)

static func _shipped_free(id: String) -> bool:
	var o = Catalog.built_in(id)
	return o != null and o["unlockKind"] == Catalog.FREE and int(o["requiredLevel"]) == 0 and o["contentAvailable"]

## CosmeticInventoryStore.selectedId.
func selected_id(category: String) -> String:
	var pending := pending_equipment()
	if pending.has(category):
		return pending[category]
	var offline = prefs.get_string("offline_selection_" + category, null)
	if offline != null:
		var o = Catalog.built_in(offline)
		if o != null and o["category"] == category and _shipped_free(offline):
			return offline
	if not _has_snapshot():
		return Catalog.default_id(category)
	var id = prefs.get_string(SELECTED_KEYS.get(category, ""), null)
	if id != null:
		var o = Catalog.built_in(id)
		if o != null and o["category"] == category and o["contentAvailable"] and is_owned(id):
			return id
	return Catalog.default_id(category)

# ---------------------------------------------------------------- server snapshot

## GemWalletStore.applyServerSnapshot (+ CosmeticInventoryStore character-pack sync).
## `wallet` is the server's wallet JSON; returns false when it is not cached.
func apply_server_snapshot(wallet: Dictionary, expected_account := "") -> bool:
	var owner := expected_account if not expected_account.is_empty() else _owner()
	if owner.is_empty() or str(wallet.get("accountId", "")) != owner or _owner() != owner:
		return false
	var balance := int(wallet.get("balance", -1))
	if balance < 0 or balance > MAX_BALANCE:
		return false
	var prior = prefs.get_string(KEY_ACCOUNT, null)
	var revision := int(wallet.get("revision", 0))
	if revision < 0 or (prior == owner and revision < prefs.get_long(KEY_REVISION, 0)):
		return false
	var e = prefs.edit()
	var legacy_progress: bool = prefs.keys().any(func(k): return k.begins_with(KEY_AD_PREFIX))
	var legacy: bool = legacy_progress or [KEY_BALANCE, KEY_ADS_REMOVED, KEY_OWNED, KEY_EVENTS, KEY_TOKENS].any(func(k): return prefs.contains(k)) \
		or SELECTED_KEYS.values().any(func(k): return prefs.contains(k))
	if prior == null and legacy and not prefs.contains(KEY_ARCHIVE):
		# Pre-account device data kept for a future verified support flow; never read to grant.
		e.put_string(KEY_ARCHIVE, JSON.stringify(_legacy_archive()))
	if prior != owner:
		for k in [KEY_TOKENS, KEY_OWNED, KEY_EVENTS, KEY_ADS_REMOVED]:
			e.remove(k)
	for k in SELECTED_KEYS.values():
		e.remove(k)
	for k in prefs.keys():
		if k.begins_with(KEY_AD_PREFIX):
			e.remove(k)
	var owned_ids: Array = []
	for id in wallet.get("ownedCosmetics", []):
		if not str(id).strip_edges().is_empty() and not owned_ids.has(str(id)):
			owned_ids.append(str(id))
	e.put_string(KEY_ACCOUNT, owner).put_long(KEY_REVISION, revision).put_int(KEY_BALANCE, balance) \
		.put_bool(KEY_ADS_REMOVED, bool(wallet.get("adsRemoved", false))).put_string_set(KEY_OWNED, owned_ids)
	var sel = wallet.get("selectedCosmetics", {})
	if sel is Dictionary:
		for category in SELECTED_KEYS:
			var v := str(sel.get(category, "")).strip_edges()
			if not v.is_empty():
				e.put_string(SELECTED_KEYS[category], v)
	var progress = wallet.get("rewardedAdProgress", {})
	if progress is Dictionary:
		for id in progress:
			e.put_int(KEY_AD_PREFIX + str(id), maxi(int(progress[id]), 0))
	if not e.commit():
		return false
	if sel is Dictionary:
		_sync_character_pack(str(sel.get("CHARACTER_PACK", "")))
	changed.emit()
	return true

func _legacy_archive() -> Dictionary:
	var progress := {}
	for k in prefs.keys():
		if k.begins_with(KEY_AD_PREFIX):
			progress[k.trim_prefix(KEY_AD_PREFIX)] = maxi(prefs.get_int(k, 0), 0)
	return {"formatVersion": 1, "verified": false, "capturedAt": int(Time.get_unix_time_from_system() * 1000.0),
		"balance": maxi(prefs.get_int(KEY_BALANCE, 0), 0), "adsRemoved": prefs.get_bool(KEY_ADS_REMOVED, false),
		"ownedCosmetics": prefs.get_string_set(KEY_OWNED, []), "selectedBoard": prefs.get_string("selected_board"),
		"selectedDice": prefs.get_string("selected_dice"), "selectedAvatar": prefs.get_string("selected_avatar"),
		"selectedCharacterPack": prefs.get_string("selected_character_pack"), "eventEntitlements": prefs.get_string_set(KEY_EVENTS, []),
		"hadAppliedPurchaseTokenCache": prefs.contains(KEY_TOKENS), "appliedPurchaseTokenCount": prefs.get_string_set(KEY_TOKENS, []).size(),
		"rewardedAdProgress": progress}

## Selected character pack: keep the Paw when it belongs to the pack, else its first Paw.
## (All live Paws are in the starter pack.)
func _sync_character_pack(pack_id: String) -> void:
	if pack_id != Catalog.DEFAULT_CHARACTER_PACK:
		return
	if Characters.CHARACTERS.has(selected_character()):
		return
	select_character(Characters.DEFAULT_CHARACTER_ID)

## GemWalletStore.clearAccountData (account deletion).
func clear_account_data(owner: String) -> bool:
	if prefs.get_string(KEY_ACCOUNT, "") != owner:
		return true
	var e = prefs.edit()
	for k in [KEY_ACCOUNT, KEY_REVISION, KEY_BALANCE, KEY_ADS_REMOVED, KEY_TOKENS, KEY_OWNED, KEY_EVENTS, KEY_ARCHIVE, KEY_ARCHIVE_NOTICE] + SELECTED_KEYS.values():
		e.remove(k)
	for k in prefs.keys():
		if k.begins_with(KEY_AD_PREFIX) or k.begins_with("local_equipment_%s_" % owner):
			e.remove(k)
	var ok: bool = e.commit()
	changed.emit()
	return ok

# ---------------------------------------------------------------- local selection

## CosmeticInventoryStore.selectOwnedLocalCosmetic: selection only, never ownership.
## Free shipped items -> offline_selection_*; owned Boards / Dice -> pending equipment that
## LocalEquipmentSync confirms with the server.
func select(category: String, id: String) -> bool:
	var o = Catalog.built_in(id)
	if o == null or o["category"] != category or not o["contentAvailable"]:
		return false
	var owner := _owner()
	if _shipped_free(id):
		var e = prefs.edit().put_string("offline_selection_" + category, id)
		if not owner.is_empty():
			e.remove(_equipment_key(owner, category))
		var ok: bool = e.commit()
		changed.emit()
		return ok
	if not category in [Catalog.BOARD, Catalog.DICE] or not is_owned(id) or owner.is_empty():
		return false
	var saved: bool = prefs.edit().put_string(_equipment_key(owner, category), id).remove("offline_selection_" + category).commit()
	changed.emit()
	return saved

static func _equipment_key(owner: String, category: String) -> String:
	return "local_equipment_%s_%s" % [owner, category]

## Owned Boards / Dice equipped locally and not yet confirmed by the server.
func pending_equipment() -> Dictionary:
	var owner := _owner()
	var out := {}
	if owner.is_empty() or prefs.get_string(KEY_ACCOUNT, "") != owner:
		return out
	var have: Array = prefs.get_string_set(KEY_OWNED, [])
	for category in [Catalog.BOARD, Catalog.DICE]:
		var id = prefs.get_string(_equipment_key(owner, category), null)
		if id != null and (have.has(id) or Catalog.default_owned_ids().has(id)):
			var o = Catalog.built_in(id)
			if o != null and o["category"] == category and o["contentAvailable"]:
				out[category] = id
	return out

func confirm_equipment(owner: String, category: String, id: String) -> void:
	if _owner() == owner and prefs.get_string(_equipment_key(owner, category), null) == id:
		prefs.edit().remove(_equipment_key(owner, category)).commit()

func clear_offline_selection(category: String) -> void:
	prefs.edit().remove("offline_selection_" + category).commit()

# ---------------------------------------------------------------- characters

## CharacterSelectionStore.isUnlocked: free four, else the shop entitlement must be owned.
## Newly registered characters stay locked until an entitlement exists.
func is_character_unlocked(character_id: String) -> bool:
	var id := Characters.canonical_id(character_id)
	if not Characters.CHARACTERS.has(id):
		return false
	if id in Catalog.FREE_CHARACTER_IDS:
		return true
	var protected = Catalog.character_entitlement_id(id)
	return protected != null and is_owned(protected)

func unlocked_character_ids() -> Array:
	var have := owned
	return Characters.REGISTRY_ORDER.filter(func(id):
		var c := Characters.canonical_id(id)
		if not Characters.CHARACTERS.has(c):
			return false
		if c in Catalog.FREE_CHARACTER_IDS:
			return true
		var p = Catalog.character_entitlement_id(c)
		return p != null and have.has(p))

## CharacterSelectionStore.load: schema 1, starter pack; a locked Paw falls back to dog.
func selected_character() -> String:
	if characters.get_int("schema_version", 1) != 1:
		return Characters.DEFAULT_CHARACTER_ID
	var id := Characters.canonical_id(str(characters.get_string("character_id", Characters.DEFAULT_CHARACTER_ID)))
	return id if is_character_unlocked(id) else Characters.DEFAULT_CHARACTER_ID

func select_character(character_id: String) -> bool:
	var id := Characters.canonical_id(character_id)
	if not is_character_unlocked(id):
		return false
	var ok: bool = characters.edit().put_int("schema_version", 1).put_string("pack_id", Characters.STARTER_PACK_ID) \
		.put_string("character_id", id).commit()
	changed.emit()
	return ok
