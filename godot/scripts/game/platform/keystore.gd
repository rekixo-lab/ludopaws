extends RefCounted
## AES/GCM with the live app's Android Keystore aliases (LudoPawsPlatform plugin), so secrets
## written by the Kotlin app (player sessions, friend / leaderboard identities, pending Play
## purchases) decrypt after the update. Desktop / tests: a NON-SECRET stand-in (AES-CBC with
## a fixed development key + an AAD check) for development only; never on a device.

const DEV_KEY := "ludopaws-dev-only-not-a-secret!!"

static func _platform():
	return Engine.get_singleton("LudoPawsPlatform") if Engine.has_singleton("LudoPawsPlatform") else null

## {iv, ct} (base64) or {} on failure.
static func encrypt(alias: String, aad: String, plaintext: String) -> Dictionary:
	var platform = _platform()
	if platform != null:
		var out = JSON.parse_string(str(platform.keystoreEncrypt(alias, aad, plaintext)))
		return out if out is Dictionary else {}
	var iv := Crypto.new().generate_random_bytes(16)
	var body := JSON.stringify({"a": alias + "|" + aad, "v": plaintext}).to_utf8_buffer()
	body.resize(body.size() + (16 - body.size() % 16))   # zero padding (dev only)
	var aes := AESContext.new()
	aes.start(AESContext.MODE_CBC_ENCRYPT, DEV_KEY.to_utf8_buffer(), iv)
	var ct := aes.update(body)
	aes.finish()
	return {"iv": Marshalls.raw_to_base64(iv), "ct": Marshalls.raw_to_base64(ct)}

## The plaintext, or null when it does not decrypt (wrong key / AAD / tampered).
static func decrypt(alias: String, aad: String, iv: String, ct: String):
	var platform = _platform()
	if platform != null:
		var out = JSON.parse_string(str(platform.keystoreDecrypt(alias, aad, iv, ct)))
		return out["v"] if out is Dictionary and out.get("ok", false) else null
	var raw := Marshalls.base64_to_raw(ct)
	var vec := Marshalls.base64_to_raw(iv)
	if raw.is_empty() or raw.size() % 16 != 0 or vec.size() != 16:
		return null
	var aes := AESContext.new()
	aes.start(AESContext.MODE_CBC_DECRYPT, DEV_KEY.to_utf8_buffer(), vec)
	var plain := aes.update(raw)
	aes.finish()
	var text := plain.get_string_from_utf8()
	var parsed = JSON.parse_string(text.rstrip(char(0)))
	if not (parsed is Dictionary) or parsed.get("a") != alias + "|" + aad:
		return null
	return str(parsed.get("v", ""))
