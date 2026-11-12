extends RefCounted
## Port of live GameApi.kt (+ GameHttpTransport.executeJson): the same paths, headers and
## JSON bodies against the live server. HTTPS only, 512 KB response cap, server errors
## surface as {error, message}. Family mode: public names are always "Player"
## (FamilyOnlineSafety.publicName). Match commands keep their clientRequestId so the
## authoritative server can replay a result instead of taking a second turn.
##
## Every call returns (await) a Dictionary: {ok: true, data: {...}} or
## {ok: false, error: "CODE", message: "..."}.
## `transport` is an object with `send(method, url, headers: PackedStringArray, body: String)`
## returning (await) {status: int, body: String} or {status: 0, error: "..."} on a network
## failure (online/http_transport.gd on device; a fake in tests).

const Characters = preload("res://scripts/game/characters/catalog.gd")
const MAX_RESPONSE_BYTES := 512 * 1024
const RULESET_V8 := "CLASSIC_V8"
const FAMILY_MODE := true

var base_url := ""
var transport
var version_name := "2.0.0"
## Optional realtime command channel: Callable(match_id, player_token, action, body) -> Dictionary
## (await) or null when the socket is not usable (then the HTTP command is used).
var command_channel: Callable

func _init(url: String, http_transport, app_version := "2.0.0") -> void:
	base_url = url.rstrip("/")
	transport = http_transport
	version_name = app_version
	assert(base_url.begins_with("https://"), "LudoProof API must use HTTPS")

static func public_name(local_name: String) -> String:
	return "Player" if FAMILY_MODE else local_name

## LudoPawsCharacterIdentityContract.resolveForRequest.
static func character_for_request(character_id) -> String:
	var id := Characters.canonical_id(str(character_id).strip_edges().to_lower()) if character_id != null else ""
	return id if Characters.is_known(id) else "dog"

static func new_request_id() -> String:
	var b := Crypto.new().generate_random_bytes(16)
	b[6] = (b[6] & 0x0f) | 0x40
	b[8] = (b[8] & 0x3f) | 0x80
	var h := b.hex_encode()
	return "%s-%s-%s-%s-%s" % [h.substr(0, 8), h.substr(8, 4), h.substr(12, 4), h.substr(16, 4), h.substr(20, 12)]

# ---------------------------------------------------------------- friends

func register_friend(display_name: String, client_request_id: String):
	return await request("POST", "/api/friends/register", {"body": {"displayName": public_name(display_name), "clientRequestId": client_request_id}})

func friend_snapshot(friend_token: String):
	return await request("GET", "/api/friends/snapshot", {"friend": friend_token})

func friend_heartbeat(friend_token: String, display_name: String):
	return await request("POST", "/api/friends/heartbeat", {"friend": friend_token, "body": {"displayName": public_name(display_name)}})

func send_friend_request(friend_token: String, friend_id: String):
	return await request("POST", "/api/friends/request", {"friend": friend_token, "body": {"friendId": friend_id}})

func respond_friend_request(friend_token: String, request_id: String, accept: bool):
	return await request("POST", "/api/friends/request/respond", {"friend": friend_token, "body": {"requestId": request_id, "accept": accept}})

func remove_friend(friend_token: String, friend_id: String):
	return await request("POST", "/api/friends/remove", {"friend": friend_token, "body": {"friendId": friend_id}})

func block_friend(friend_token: String, friend_id: String):
	return await request("POST", "/api/friends/block", {"friend": friend_token, "body": {"friendId": friend_id}})

func unblock_friend(friend_token: String, friend_id: String):
	return await request("POST", "/api/friends/unblock", {"friend": friend_token, "body": {"friendId": friend_id}})

func report_friend(friend_token: String, friend_id: String, reason: String, client_request_id: String, message_id = null):
	var body := {"friendId": friend_id, "reason": reason, "clientRequestId": client_request_id}
	if message_id != null:
		body["messageId"] = message_id
	return await request("POST", "/api/friends/report", {"friend": friend_token, "body": body})

func send_friend_invite(friend_token: String, friend_id: String, match_id: String, room_player_token: String, client_request_id: String):
	return await request("POST", "/api/friends/invite", {"friend": friend_token, "room": room_player_token,
		"body": {"friendId": friend_id, "matchId": match_id, "clientRequestId": client_request_id}})

func respond_friend_invite(friend_token: String, invite_id: String, accept: bool, client_request_id = null):
	var body := {"inviteId": invite_id, "accept": accept}
	if accept:
		assert(client_request_id != null and not str(client_request_id).is_empty(), "clientRequestId is required when accepting a friend invite")
		body["clientRequestId"] = client_request_id
	return await request("POST", "/api/friends/invite/respond", {"friend": friend_token, "body": body})

func send_friend_message(friend_token: String, friend_id: String, text: String, client_request_id: String):
	return await request("POST", "/api/friends/messages/send", {"friend": friend_token,
		"body": {"friendId": friend_id, "text": text, "clientRequestId": client_request_id}})

func friend_messages(friend_token: String, friend_id: String):
	return await request("POST", "/api/friends/messages/list", {"friend": friend_token, "body": {"friendId": friend_id}})

func friend_recent(friend_token: String):
	return await request("POST", "/api/friends/recent", {"friend": friend_token, "body": {}})

# ---------------------------------------------------------------- matches / matchmaking

func create_friend_room(friend_token: String, display_name: String, client_request_id: String, player_count: int,
		character_id: String, character_account_token = null, match_style = null):
	return await request("POST", "/api/matches", {"friend": friend_token, "character": character_account_token,
		"body": {"displayName": public_name(display_name), "clientRequestId": client_request_id,
			"characterId": character_for_request(character_id), "matchStyle": match_style,
			"targetPlayerCount": player_count, "matchMode": "FRIENDS", "rulesetKey": RULESET_V8}})

func search_public_match(profile_token: String, display_name: String, client_request_id: String, player_count: int,
		character_id: String, ruleset_key := RULESET_V8, character_account_token = null, match_style = null):
	return await request("POST", "/api/matchmaking/search", {"profile": profile_token, "character": character_account_token,
		"body": {"displayName": public_name(display_name), "clientRequestId": client_request_id,
			"characterId": character_for_request(character_id), "matchStyle": match_style,
			"playerCount": player_count, "rulesetKey": ruleset_key}})

func public_match_status(profile_token: String, client_request_id: String, player_count: int):
	return await request("POST", "/api/matchmaking/status", {"profile": profile_token,
		"body": {"clientRequestId": client_request_id, "playerCount": player_count}})

func cancel_public_match(profile_token: String, client_request_id: String, player_count: int):
	return await request("POST", "/api/matchmaking/cancel", {"profile": profile_token,
		"body": {"clientRequestId": client_request_id, "playerCount": player_count}})

func create_match(profile_token: String, display_name: String, client_request_id: String, character_id: String,
		ruleset_key := RULESET_V8, character_account_token = null, match_style = null):
	return await request("POST", "/api/matches", {"profile": profile_token, "character": character_account_token,
		"body": {"displayName": public_name(display_name), "clientRequestId": client_request_id,
			"characterId": character_for_request(character_id), "matchStyle": match_style, "rulesetKey": ruleset_key}})

func join_match(match_id: String, display_name: String, client_request_id: String, profile_token = null,
		friend_join_token = null, character_id := "dog", character_account_token = null, match_style = null):
	return await request("POST", "/api/matches/%s/join" % match_id.to_upper(), {"profile": profile_token,
		"friend_join": friend_join_token, "character": character_account_token,
		"body": {"supportedRulesetKey": RULESET_V8, "displayName": public_name(display_name), "clientRequestId": client_request_id,
			"characterId": character_for_request(character_id), "matchStyle": match_style}})

func state(match_id: String, player_token: String):
	return await request("GET", "/api/matches/%s/state" % match_id.to_upper(), {"player": player_token})

func start(match_id: String, player_token: String):
	return await request("POST", "/api/matches/%s/start" % match_id.to_upper(), {"player": player_token, "body": {}})

func roll(match_id: String, player_token: String, client_request_id: String, expected_event_index: int):
	return await command(match_id, player_token, "ROLL", "roll", {"clientRequestId": client_request_id, "expectedEventIndex": expected_event_index})

func move(match_id: String, player_token: String, token_index: int, event_index: int, client_request_id := ""):
	var id := client_request_id if not client_request_id.is_empty() else new_request_id()
	return await command(match_id, player_token, "MOVE", "move", {"tokenIndex": token_index, "eventIndex": event_index, "clientRequestId": id})

## Socket first; on loss the same command id goes over HTTP (the server replays, never
## a second turn).
func command(match_id: String, player_token: String, action: String, path: String, body: Dictionary):
	assert(not str(body.get("clientRequestId", "")).is_empty(), "clientRequestId is required")
	if command_channel.is_valid():
		var response = await command_channel.call(match_id, player_token, action, body)
		if response is Dictionary:
			if response.has("error"):
				return {"ok": false, "error": str(response.get("error", "COMMAND_FAILED")), "message": str(response.get("message", "Match command failed"))}
			return {"ok": true, "data": response}
	return await request("POST", "/api/matches/%s/%s" % [match_id.to_upper(), path], {"player": player_token, "body": body})

# ---------------------------------------------------------------- transport

## opts: {player, friend, profile (bearer, first non-null wins), room, friend_join, character, body}
func build(method: String, path: String, opts := {}) -> Dictionary:
	var headers := PackedStringArray(["Accept: application/json", "User-Agent: LudoProof-Android/" + version_name])
	var bearer = opts.get("player")
	if bearer == null: bearer = opts.get("friend")
	if bearer == null: bearer = opts.get("profile")
	if bearer != null:
		headers.append("Authorization: Bearer " + str(bearer))
	if opts.get("room") != null:
		headers.append("X-LudoProof-Room-Token: " + str(opts["room"]))
	if opts.get("friend_join") != null:
		headers.append("X-LudoProof-Friend-Join-Token: " + str(opts["friend_join"]))
	if opts.get("character") != null:
		headers.append("X-LudoProof-Character-Account-Token: " + str(opts["character"]))
	var body := ""
	if opts.has("body"):
		headers.append("Content-Type: application/json; charset=utf-8")
		body = JSON.stringify(_strip_nulls(opts["body"]))
	return {"method": method, "url": base_url + path, "headers": headers, "body": body}

## org.json drops null values from objects; match that on the wire.
static func _strip_nulls(value):
	if value is Dictionary:
		var out := {}
		for k in value:
			if value[k] != null:
				out[k] = _strip_nulls(value[k])
		return out
	return value

func request(method: String, path: String, opts := {}):
	var req := build(method, path, opts)
	var res: Dictionary = await transport.send(req["method"], req["url"], req["headers"], req["body"])
	return parse_response(res)

## GameHttpTransport.executeJson.
static func parse_response(res: Dictionary) -> Dictionary:
	var status := int(res.get("status", 0))
	if status == -1:
		return {"ok": false, "error": "RESPONSE_TOO_LARGE", "message": "Server response exceeded the safety limit."}
	if status == 0:
		return {"ok": false, "error": str(res.get("error", "NETWORK_ERROR")), "message": "Network request failed."}
	var text := str(res.get("body", ""))
	if text.to_utf8_buffer().size() > MAX_RESPONSE_BYTES:
		return {"ok": false, "error": "RESPONSE_TOO_LARGE", "message": "Server response exceeded the safety limit."}
	var json = {} if text.strip_edges().is_empty() else JSON.parse_string(text)
	var success := status >= 200 and status < 300
	if not (json is Dictionary):
		if success:
			return {"ok": false, "error": "INVALID_RESPONSE", "message": "Server response was not valid JSON."}
		json = {}
	if not success:
		return {"ok": false, "error": str(json.get("error", "HTTP_%d" % status)), "message": str(json.get("message", "Request failed with HTTP %d" % status))}
	return {"ok": true, "data": json}
