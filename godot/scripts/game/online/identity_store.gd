extends RefCounted
## Live LeaderboardIdentityStore / FriendIdentityStore (same design): a per-device
## registration request id (Keystore-encrypted, fixed AAD) and the server credential
## (id + Keystore-encrypted token bound to "{prefix}{id}:{requestId}") in the live
## preference file. The Kotlin app's credentials decrypt after the update.
##   var lb := IdentityStore.leaderboard()   -> {id, token, requestId} via load()
##   var fr := IdentityStore.friend()

const NativePrefs = preload("res://scripts/game/platform/native_prefs.gd")
const Keystore = preload("res://scripts/game/platform/keystore.gd")
const UUID_V4 := "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$"

var prefs_name := ""
var alias := ""
var k_id := ""
var k_reg_iv := ""
var k_reg_ct := ""
var k_tok_iv := ""
var k_tok_ct := ""
var legacy_id_key := ""
var registration_aad := ""
var credential_prefix := ""
var id_pattern := ""
var token_pattern := ""

static func leaderboard():
	var s = load("res://scripts/game/online/identity_store.gd").new()
	s.prefs_name = "ludoproof_leaderboard_identity"
	s.alias = "ludoproof_leaderboard_identity_key_v2"
	s.k_id = "profile_id_v2"
	s.legacy_id_key = "profile_id_v1"
	s.k_reg_iv = "registration_request_iv_v2"
	s.k_reg_ct = "registration_request_ciphertext_v2"
	s.k_tok_iv = "profile_token_iv_v2"
	s.k_tok_ct = "profile_token_ciphertext_v2"
	s.registration_aad = "ludoproof:leaderboard-registration:v2"
	s.credential_prefix = "ludoproof:leaderboard-credential:v2:"
	s.id_pattern = UUID_V4
	s.token_pattern = "^lpp_[A-Za-z0-9_-]{32,}$"
	return s

static func friend():
	var s = load("res://scripts/game/online/identity_store.gd").new()
	s.prefs_name = "ludoproof_friend_identity_v1"
	s.alias = "ludoproof_friend_identity_key_v1"
	s.k_id = "friend_id"
	s.k_reg_iv = "registration_request_iv"
	s.k_reg_ct = "registration_request_ciphertext"
	s.k_tok_iv = "friend_token_iv"
	s.k_tok_ct = "friend_token_ciphertext"
	s.registration_aad = "ludoproof:friend-registration:v1"
	s.credential_prefix = "ludoproof:friend-credential:v1:"
	s.id_pattern = "^LPF-[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}$"
	s.token_pattern = "^lf_[A-Za-z0-9_-]{32,}$"
	return s

func _p():
	return NativePrefs.open(prefs_name)

static func _ok(pattern: String, v) -> bool:
	return v != null and RegEx.create_from_string(pattern).search(str(v)) != null

func _load_request_id():
	var p = _p()
	var iv = p.get_string(k_reg_iv, null)
	var ct = p.get_string(k_reg_ct, null)
	if iv == null or ct == null:
		return null
	var id = Keystore.decrypt(alias, registration_aad, iv, ct)
	if id == null or not _ok(UUID_V4, str(id).strip_edges().to_lower()):
		p.edit().remove(k_reg_iv).remove(k_reg_ct).commit()
		return null
	return str(id).strip_edges().to_lower()

## The device's persistent registration request id (created once).
func registration_request_id() -> String:
	var existing = _load_request_id()
	if existing != null:
		return existing
	var generated: String = load("res://scripts/game/online/game_api.gd").new_request_id()
	_persist_request_id(generated)
	if not legacy_id_key.is_empty():
		_p().edit().remove(legacy_id_key).commit()
	return generated

func _persist_request_id(id: String) -> bool:
	var sealed := Keystore.encrypt(alias, registration_aad, id)
	return not sealed.is_empty() and _p().edit().put_string(k_reg_iv, sealed["iv"]).put_string(k_reg_ct, sealed["ct"]).commit()

## {id, token, requestId} -> bool. The request id must be this device's.
func save(c: Dictionary) -> bool:
	if not _ok(id_pattern, c.get("id")) or not _ok(token_pattern, c.get("token")) or not _ok(UUID_V4, c.get("requestId")):
		return false
	if registration_request_id() != c["requestId"]:
		return false
	var sealed := Keystore.encrypt(alias, credential_prefix + "%s:%s" % [c["id"], c["requestId"]], c["token"])
	return not sealed.is_empty() and _p().edit().put_string(k_id, c["id"]).put_string(k_tok_iv, sealed["iv"]) \
		.put_string(k_tok_ct, sealed["ct"]).commit()

## Live restoreFromAccount: a credential recovered by the account server.
func restore_from_account(c: Dictionary) -> bool:
	if not _ok(id_pattern, c.get("id")) or not _ok(token_pattern, c.get("token")) or not _ok(UUID_V4, c.get("requestId")):
		return false
	return _persist_request_id(c["requestId"]) and save(c)

func load_credential() -> Dictionary:
	var p = _p()
	var id = p.get_string(k_id, null)
	if id == null:
		return {}
	var request_id = _load_request_id()
	if request_id == null:
		clear_credential_only()
		return {}
	var iv = p.get_string(k_tok_iv, null)
	var ct = p.get_string(k_tok_ct, null)
	if iv == null or ct == null:
		return {}
	if not _ok(id_pattern, id):
		clear_credential_only()
		return {}
	var token = Keystore.decrypt(alias, credential_prefix + "%s:%s" % [id, request_id], iv, ct)
	if token == null or not _ok(token_pattern, token):
		clear_credential_only()
		return {}
	return {"id": id, "token": token, "requestId": request_id}

func clear_credential_only() -> void:
	_p().edit().remove(k_id).remove(k_tok_iv).remove(k_tok_ct).commit()

## Account switch / deletion: everything, including the registration id.
func clear_all() -> bool:
	var p = _p()
	var e = p.edit()
	for k in p.keys():
		e.remove(k)
	return e.commit()
