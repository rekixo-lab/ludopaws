extends SceneTree
## B2 contract tests (no network): GameApi builds exactly the requests live GameApi.kt sends;
## responses / errors / size cap follow GameHttpTransport; every path exists on the live
## server (tests/fixtures/server_route_literals.txt); realtime backoff, terminal policy and
## message handling follow MatchRealtimeClient.
##   godot --headless --path . --script res://tests/online_contract_smoke.gd

const GameApi = preload("res://scripts/game/online/game_api.gd")
const Realtime = preload("res://scripts/game/online/match_realtime_client.gd")
const Envelope = preload("res://scripts/game/online/match_envelope.gd")

var failures := 0
var checks := 0

func _check(ok: bool, label: String) -> void:
	checks += 1
	if not ok:
		failures += 1
		printerr("FAIL: ", label)

class FakeTransport:
	var sent: Array = []
	var reply := {"status": 200, "body": "{\"ok\":true}"}
	func send(method: String, url: String, headers: PackedStringArray, body: String) -> Dictionary:
		sent.append({"method": method, "url": url, "headers": headers, "body": body})
		return reply

## JSON numbers parse as floats: normalise integral floats so values compare like JSON.
func _n(v):
	if v is float and v == floor(v):
		return int(v)
	if v is Dictionary:
		var out := {}
		for k in v:
			out[k] = _n(v[k])
		return out
	if v is Array:
		return v.map(func(x): return _n(x))
	return v

func _header(req: Dictionary, name: String) -> String:
	for h in req["headers"]:
		if h.begins_with(name + ": "):
			return h.substr(name.length() + 2)
	return ""

func _initialize() -> void:
	var t := FakeTransport.new()
	var api := GameApi.new("https://api.example.test/", t, "2.0.0")
	_check(api.base_url == "https://api.example.test", "base URL trimmed")
	# Requests (method, path, headers, body) as live GameApi.kt.
	await api.register_friend("Asha", "req-1")
	var r: Dictionary = t.sent[-1]
	_check(r["method"] == "POST" and r["url"] == "https://api.example.test/api/friends/register"
		and _n(JSON.parse_string(r["body"])) == {"displayName": "Player", "clientRequestId": "req-1"}
		and _header(r, "Accept") == "application/json" and _header(r, "User-Agent") == "LudoProof-Android/2.0.0"
		and _header(r, "Authorization") == "", "register friend: family-mode public name 'Player', no bearer")
	await api.friend_snapshot("FT")
	r = t.sent[-1]
	_check(r["method"] == "GET" and r["url"].ends_with("/api/friends/snapshot") and _header(r, "Authorization") == "Bearer FT" and r["body"] == "", "friend snapshot: GET with friend bearer")
	await api.send_friend_invite("FT", "F2", "LPABCDEFGH", "ROOM", "req-2")
	r = t.sent[-1]
	_check(_header(r, "Authorization") == "Bearer FT" and _header(r, "X-LudoProof-Room-Token") == "ROOM"
		and _n(JSON.parse_string(r["body"])) == {"friendId": "F2", "matchId": "LPABCDEFGH", "clientRequestId": "req-2"}, "friend invite: bearer + room token header")
	await api.search_public_match("PT", "Asha", "req-3", 4, "Dog", "CLASSIC_V8", "CHAR", null)
	r = t.sent[-1]
	var body = _n(JSON.parse_string(r["body"]))
	_check(r["url"].ends_with("/api/matchmaking/search") and _header(r, "Authorization") == "Bearer PT" and _header(r, "X-LudoProof-Character-Account-Token") == "CHAR"
		and body == {"displayName": "Player", "clientRequestId": "req-3", "characterId": "dog", "playerCount": 4, "rulesetKey": "CLASSIC_V8"},
		"matchmaking search: profile bearer, character header, canonical paw, null matchStyle omitted (%s)" % [body])
	await api.create_friend_room("FT", "Asha", "req-4", 3, "nope")
	body = _n(JSON.parse_string(t.sent[-1]["body"]))
	_check(body["targetPlayerCount"] == 3 and body["matchMode"] == "FRIENDS" and body["rulesetKey"] == "CLASSIC_V8" and body["characterId"] == "dog",
		"friend room: FRIENDS mode, V8 ruleset, unknown paw -> default")
	await api.join_match("lpabcdefgh", "Asha", "req-5", null, "FJ", "cat")
	r = t.sent[-1]
	body = _n(JSON.parse_string(r["body"]))
	_check(r["url"].ends_with("/api/matches/LPABCDEFGH/join") and _header(r, "X-LudoProof-Friend-Join-Token") == "FJ" and _header(r, "Authorization") == ""
		and body["supportedRulesetKey"] == "CLASSIC_V8" and body["characterId"] == "cat", "join: upper-case id, friend join token, supported ruleset")
	await api.state("lpx", "PLAYER")
	r = t.sent[-1]
	_check(r["method"] == "GET" and r["url"].ends_with("/api/matches/LPX/state") and _header(r, "Authorization") == "Bearer PLAYER", "state: GET with player bearer")
	await api.roll("LPX", "PLAYER", "cmd-1", 7)
	r = t.sent[-1]
	_check(r["url"].ends_with("/api/matches/LPX/roll") and _n(JSON.parse_string(r["body"])) == {"clientRequestId": "cmd-1", "expectedEventIndex": 7}, "roll over HTTP without a socket")
	await api.move("LPX", "PLAYER", 2, 8, "cmd-2")
	_check(_n(JSON.parse_string(t.sent[-1]["body"])) == {"tokenIndex": 2, "eventIndex": 8, "clientRequestId": "cmd-2"}, "move body")
	# Socket first, same command id over HTTP when the socket returns null.
	var socket_calls := []
	api.command_channel = func(m, p, a, b):
		socket_calls.append(b["clientRequestId"])
		return null
	var before := t.sent.size()
	await api.roll("LPX", "PLAYER", "cmd-3", 9)
	_check(socket_calls == ["cmd-3"] and t.sent.size() == before + 1 and JSON.parse_string(t.sent[-1]["body"])["clientRequestId"] == "cmd-3",
		"no socket ack -> the same command id goes over HTTP")
	api.command_channel = func(m, p, a, b): return {"error": "NOT_YOUR_TURN", "message": "Wait"}
	var res: Dictionary = await api.roll("LPX", "PLAYER", "cmd-4", 9)
	_check(not res["ok"] and res["error"] == "NOT_YOUR_TURN", "socket error response surfaces as the server error")
	api.command_channel = Callable()
	# Responses (GameHttpTransport.executeJson).
	_check(_n(GameApi.parse_response({"status": 200, "body": "{\"a\":1}"})) == {"ok": true, "data": {"a": 1}}, "2xx JSON -> ok")
	_check(GameApi.parse_response({"status": 200, "body": ""}) == {"ok": true, "data": {}}, "empty 2xx body -> {}")
	var e: Dictionary = GameApi.parse_response({"status": 409, "body": "{\"error\":\"NOT_ENOUGH_GEMS\",\"message\":\"m\"}"})
	_check(not e["ok"] and e["error"] == "NOT_ENOUGH_GEMS" and e["message"] == "m", "server error code + message")
	e = GameApi.parse_response({"status": 502, "body": "<html>"})
	_check(e["error"] == "HTTP_502", "non-JSON error -> HTTP_<code>")
	_check(GameApi.parse_response({"status": 200, "body": "x".repeat(GameApi.MAX_RESPONSE_BYTES + 1)})["error"] == "RESPONSE_TOO_LARGE"
		and GameApi.parse_response({"status": -1})["error"] == "RESPONSE_TOO_LARGE", "512 KB response cap")
	_check(GameApi.parse_response({"status": 0, "error": "TIMEOUT"})["error"] == "TIMEOUT", "network failure surfaces")
	var id := GameApi.new_request_id()
	_check(id.length() == 36 and id[14] == "4" and id != GameApi.new_request_id(), "client request ids are random UUID v4")
	# Paths exist on the live server.
	var literals := FileAccess.get_file_as_string("res://tests/fixtures/server_route_literals.txt").split("\n")
	var missing: Array = []
	for req in t.sent:
		var path: String = req["url"].replace("https://api.example.test", "")
		var parts := path.split("/")
		# Routers mounted under a prefix (friends, matchmaking, match rooms) match on the
		# last path segment in the server source.
		var known := literals.has(path) or literals.has("/" + parts[parts.size() - 1])
		if not known:
			missing.append(path)
	_check(missing.is_empty(), "every client path exists on the live server %s" % [missing])
	# Realtime.
	_check(Realtime.backoff_ms(0, 0.0) == 800 and Realtime.backoff_ms(0, 1.0) == 1200 and Realtime.backoff_ms(4, 0.5) == 16000
		and Realtime.backoff_ms(9, 0.5) == 16000 and Realtime.backoff_ms(-3, 0.5) == 1000, "reconnect backoff 1 s x 2^min(n,4), jitter 0.8-1.2")
	_check(Realtime.is_terminal_http(401) and Realtime.is_terminal_http(404) and not Realtime.is_terminal_http(429) and not Realtime.is_terminal_http(503)
		and Realtime.terminal_message(410) == "Please update Ludo Paws to keep playing online.", "terminal failure policy")
	var rt := Realtime.new("https://api.example.test", "2.0.0")
	_check(rt.ws_base == "wss://api.example.test", "realtime uses WSS on the API host")
	rt.match_id = "LPX"
	rt.emoji_catalog = ["👍"]
	var got := {"snap": 0, "rev": [], "chat": 0}
	rt.snapshot.connect(func(p): got["snap"] += 1)
	rt.revision.connect(func(rv, st): got["rev"].append([rv, st]))
	rt.quick_chat.connect(func(a, b, c): got["chat"] += 1)
	rt.handle_text(JSON.stringify({"type": "SYNC", "matchId": "LPX", "revision": 5, "state": {"matchId": "LPX", "revision": 5}}))
	rt.handle_text(JSON.stringify({"type": "STATE_CHANGED", "matchId": "LPX", "revision": 6, "status": "ACTIVE"}))
	rt.handle_text(JSON.stringify({"type": "STATE_CHANGED", "matchId": "OTHER", "revision": 7}))
	rt.handle_text(JSON.stringify({"type": "SYNC", "matchId": "LPX", "revision": 8, "state": {"matchId": "LPX", "revision": 3}}))
	rt.handle_text(JSON.stringify({"type": "QUICK_CHAT", "playerId": "p", "displayName": "Player", "emoji": "👍"}))
	rt.handle_text(JSON.stringify({"type": "QUICK_CHAT", "playerId": "p", "displayName": "Player", "emoji": "💣"}))
	rt.handle_text("not json")
	_check(got["snap"] == 1 and got["rev"] == [[6, "ACTIVE"], [8, null]] and got["chat"] == 1,
		"realtime: verified snapshot, revision hints, other match ignored, quick chat only from the catalog (%s)" % [got])
	var pending := {"done": false, "response": null}
	rt._pending["r1"] = pending
	rt.handle_text(JSON.stringify({"type": "command_result", "requestId": "r1", "statusCode": 409, "response": {}}))
	_check(pending["done"] and pending["response"]["error"] == "HTTP_409", "command_result with a non-2xx status carries an error")
	rt.free()
	_envelope()
	var RL = load("res://scripts/game/online/room_link.gd")
	_check(RL.parse("https://ludopaws.rekixo.com/play/friends/LPABCDEFGH?host=LPF-ABCD-EFGH-JKLM") == {"room": "LPABCDEFGH", "mode": "FRIENDS", "host": "LPF-ABCD-EFGH-JKLM"}
		and RL.parse("ludopaws://api.ludopaws.rekixo.com/play/friends/LPABCDEFGH")["host"] == null
		and RL.parse("https://evil.example/play/friends/LPABCDEFGH").is_empty() and RL.parse("ludopaws://ludopaws.rekixo.com/play/friends/LPABCDEFGH").is_empty()
		and RL.parse("https://ludopaws.rekixo.com/play/friends/LPABCDEFGH#x").is_empty() and RL.parse("https://ludopaws.rekixo.com/play/friends/LPABCDEFGH?host=bad").is_empty()
		and RL.url("LPABCDEFGH") == "https://ludopaws.rekixo.com/play/friends/LPABCDEFGH", "room links: live origins, paths and host rules")
	print("Online contract smoke: %d checks, %d failures" % [checks, failures])
	quit(1 if failures > 0 else 0)

static func _snapshot() -> Dictionary:
	return {"schemaVersion": 1, "matchId": "LPABCDEFGH", "status": "ACTIVE", "hostPlayerId": "p0", "matchMode": "ONLINE",
		"targetPlayerCount": 2, "rulesetId": "ludoproof-standard-v8", "revision": 12, "randomEventIndex": 5, "turnSeat": 1,
		"comebackWaits": [-1, 0], "winnerPlayerId": null, "finishOrderPlayerIds": [],
		"players": [{"playerId": "p0", "seat": 0, "color": "RED", "playerType": "HUMAN", "tokens": [0, -1, -1, -1]},
			{"playerId": "p1", "seat": 1, "color": "YELLOW", "playerType": "BOT", "tokens": [-1, -1, 12, 57]}],
		"pendingRoll": {"status": "RESOLVED", "seat": 1, "eventIndex": 4, "eventId": "roll:4", "legalTokenIndexes": [2], "outcome": 3},
		"history": [{"eventIndex": 3, "playerId": "p0", "outcome": 6, "moveTokenIndex": 0, "captures": 0}]}

## Live GameJson validation: a good snapshot passes; each single corruption is rejected.
func _envelope() -> void:
	var good := _snapshot()
	var env: Dictionary = Envelope.parse({"playerId": "p0", "state": good})
	_check(env["ok"] and env["playerId"] == "p0" and env["state"]["matchId"] == "LPABCDEFGH", "a valid server snapshot is accepted (%s)" % [env.get("error", "")])
	var cases := {
		"bad match id": func(v): v["matchId"] = "XX1",
		"2-player online opponent must be YELLOW": func(v): v["players"][1]["color"] = "GREEN",
		"token out of range": func(v): v["players"][0]["tokens"][0] = 58,
		"winner while active": func(v): v["winnerPlayerId"] = "p0",
		"pending roll id drift": func(v): v["pendingRoll"]["eventIndex"] = 2,
		"history at or after the random index": func(v): v["history"][0]["eventIndex"] = 5,
		"ruleset of another mode": func(v): v["rulesetId"] = "ludoproof-team-v7",
		"missing comeback counters": func(v): v.erase("comebackWaits"),
		"host not seated": func(v): v["hostPlayerId"] = "zz",
		"finished without winner": func(v):
			v["status"] = "FINISHED"
			v["pendingRoll"] = null,
	}
	var accepted: Array = []
	for name in cases:
		var bad := _snapshot()
		cases[name].call(bad)
		if Envelope.parse({"state": bad})["ok"]:
			accepted.append(name)
	_check(accepted.is_empty(), "corrupted snapshots are rejected %s" % [accepted])
