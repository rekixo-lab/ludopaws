extends SceneTree
## B1 contract tests (no network): AccountService follows live AccountRepository /
## AccountSessionStore — guest creation with the persistent guest request id, session shape
## validation, refresh 45 s before expiry with a persisted request id, expired-guest recovery
## onto the same guest, Google accounts never fall back to a new guest, logout clears; Google
## save (link) / restore (login) with the server nonce, live error messages, guest deletion
## code, account deletion.
##   godot --headless --path . --script res://tests/account_contract_smoke.gd

const Account = preload("res://scripts/game/account/account_service.gd")
const AccountNode = preload("res://scripts/game/account/account.gd")

var failures := 0
var checks := 0

func _check(ok: bool, label: String) -> void:
	checks += 1
	if not ok:
		failures += 1
		printerr("FAIL: ", label)

class MemStore:
	var session := ""
	var mode := ""
	var guest_id := "11111111-1111-4111-8111-111111111111"
	var refresh_id := ""
	var refresh_ids_issued := 0
	func sessionLoad() -> String: return session
	func sessionSave(json: String) -> bool:
		session = json
		var s = JSON.parse_string(json)
		mode = "GUEST" if s.get("guestRecoveryEligible", false) else "GOOGLE"
		refresh_id = ""
		return true
	func sessionClear() -> bool:
		session = ""
		refresh_id = ""
		return true
	func lastAccountMode() -> String: return mode
	func guestRequestId() -> String: return guest_id
	func pendingRefreshRequestId() -> String:
		if refresh_id.is_empty():
			refresh_ids_issued += 1
			refresh_id = "22222222-2222-4222-8222-22222222222%d" % refresh_ids_issued
		return refresh_id
	func hasPendingIdentityRecovery() -> bool:
		var s = JSON.parse_string(session) if not session.is_empty() else null
		return s is Dictionary and s.get("identityRecoveryPending", false)
	func sessionCompleteIdentityRecovery(id: String) -> bool:
		var s = JSON.parse_string(session)
		s["identityRecoveryPending"] = false
		return sessionSave(JSON.stringify(s))

class FakeServer:
	var sent: Array = []
	var replies := {}   # path -> Array of {status, body}
	func send(method: String, url: String, headers: PackedStringArray, body: String) -> Dictionary:
		var path := url.replace("https://api.example.test", "").split("?")[0]
		sent.append({"method": method, "path": path, "headers": headers, "body": body})
		var q: Array = replies.get(path, [])
		return q.pop_front() if not q.is_empty() else {"status": 404, "body": "{\"error\":\"NOT_FOUND\"}"}

func _session_body(account := "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", access_exp := 2000, rt := "lpr_" + "A".repeat(43)) -> String:
	return JSON.stringify({"accountId": account, "sessionId": "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
		"accessToken": "h.p.s", "refreshToken": rt, "billingAccountId": "c".repeat(64),
		"accessExpiresAt": access_exp, "sessionExpiresAt": 999999, "profile": {"displayName": "Guest User"}})

func _initialize() -> void:
	var store := MemStore.new()
	var server := FakeServer.new()
	var acc := Account.new("https://api.example.test", server, store)
	acc.now_s = func() -> int: return 1000
	# Guest creation.
	server.replies["/api/account/guest"] = [{"status": 200, "body": _session_body()}]
	var res: Dictionary = await acc.ensure_guest_session()
	var body = JSON.parse_string(server.sent[-1]["body"])
	_check(res["ok"] and server.sent[-1]["method"] == "POST" and body == {"clientRequestId": store.guest_id, "displayName": "Guest User"},
		"guest account: POST /api/account/guest with the persistent guest request id")
	_check(store.mode == "GUEST" and acc.current_session()["guestRecoveryEligible"] == true, "guest session saved as GUEST")
	# Valid session: no refresh before the 45 s margin.
	var n := server.sent.size()
	res = await acc.require_session()
	_check(res["ok"] and server.sent.size() == n, "access token valid beyond 45 s: no refresh")
	# Refresh inside the margin, persisted request id.
	acc.now_s = func() -> int: return 1960
	server.replies["/api/account/refresh"] = [{"status": 200, "body": _session_body("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", 5000)}]
	res = await acc.require_session()
	body = JSON.parse_string(server.sent[-1]["body"])
	_check(res["ok"] and server.sent[-1]["path"] == "/api/account/refresh" and body["refreshToken"] == "lpr_" + "A".repeat(43)
		and body["clientRequestId"] == "22222222-2222-4222-8222-222222222221" and int(acc.current_session()["accessExpiresAt"]) == 5000,
		"refresh 45 s before expiry with the persisted refresh request id")
	# Invalid server session shape is rejected.
	acc.now_s = func() -> int: return 4990
	server.replies["/api/account/refresh"] = [{"status": 200, "body": _session_body("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", 9000, "bad")}]
	res = await acc.require_session()
	_check(not res["ok"] and res["error"] == "INVALID_SESSION" and int(acc.current_session()["accessExpiresAt"]) == 5000, "an invalid session from the server is never saved")
	# Expired guest refresh -> reopen the same guest account.
	server.replies["/api/account/refresh"] = [{"status": 401, "body": "{\"error\":\"REFRESH_TOKEN_INVALID\",\"message\":\"x\"}"}]
	server.replies["/api/account/guest"] = [{"status": 200, "body": _session_body("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", 9999)}]
	res = await acc.ensure_guest_session()
	_check(res["ok"] and server.sent[-1]["path"] == "/api/account/guest" and JSON.parse_string(server.sent[-1]["body"])["clientRequestId"] == store.guest_id,
		"expired guest session reopens the same guest account")
	# Google account: never a new guest.
	store.session = ""
	store.mode = "GOOGLE"
	acc._cached = null
	n = server.sent.size()
	res = await acc.ensure_guest_session()
	_check(not res["ok"] and res["error"] == "ACCOUNT_SESSION_REQUIRED" and server.sent.size() == n, "a Google account never falls back to a new guest")
	# Authenticated read uses the bearer token; logout clears.
	store.mode = "GUEST"
	server.replies["/api/account/guest"] = [{"status": 200, "body": _session_body("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", 99999)}]
	await acc.ensure_guest_session()
	server.replies["/api/wallet"] = [{"status": 200, "body": "{\"gems\":5}"}]
	res = await acc.wallet()
	var auth := ""
	for h in server.sent[-1]["headers"]:
		if h.begins_with("Authorization: "):
			auth = h
	_check(res["ok"] and auth == "Authorization: Bearer h.p.s", "wallet read carries the access token")
	server.replies["/api/account/logout"] = [{"status": 200, "body": "{}"}]
	await acc.logout()
	_check(acc.current_session().is_empty(), "logout clears the session")
	_check(Account.valid_shape(JSON.parse_string(_session_body())) and not Account.valid_shape({"accountId": "x"}), "session shape validation (live regexes)")
	await _google_and_deletion()
	await _leaderboard()
	await _online_controller()
	await _friends()
	print("Account contract smoke: %d checks, %d failures" % [checks, failures])
	quit(1 if failures > 0 else 0)

func _body(server: FakeServer):
	return JSON.parse_string(server.sent[-1]["body"])

func _google_and_deletion() -> void:
	var store := MemStore.new()
	var server := FakeServer.new()
	var svc := Account.new("https://api.example.test", server, store)
	svc.now_s = func() -> int: return 1000
	var node := AccountNode.new()
	root.add_child(node)
	node.service = svc
	node.server_client_id = "client.apps.googleusercontent.com"
	var asked := []
	node.credential_provider = func(nonce: String) -> Dictionary:
		asked.append(nonce)
		return {"status": "OK", "id_token": "google.id.token"}
	var nonce := "d".repeat(64)
	# Save this guest to Google (link): guest first, challenge, Credential Manager, link.
	server.replies["/api/account/guest"] = [{"status": 200, "body": _session_body()}]
	server.replies["/api/account/google/challenge"] = [{"status": 200, "body": JSON.stringify({"nonce": nonce})}]
	server.replies["/api/account/google/link"] = [{"status": 200, "body": JSON.stringify({"profile": {"displayName": "Asha"}})}]
	server.replies["/api/account/links"] = [{"status": 200, "body": "{\"googleLinked\":true}"}]
	var res: Dictionary = await node.google_flow(true)
	var linked: Dictionary = svc.current_session()
	_check(res["ok"] and asked == [nonce] and res["title"] == "Account saved", "save to Google: server nonce passed to the credential picker")
	_check(_body(server) == {"idToken": "google.id.token", "nonce": nonce} or server.sent.any(func(r): return r["path"] == "/api/account/google/link" and JSON.parse_string(r["body"]) == {"idToken": "google.id.token", "nonce": nonce}),
		"link body = {idToken, nonce}")
	_check(linked["guestRecoveryEligible"] == false and linked["displayName"] == "Asha" and store.mode == "GOOGLE" and linked["accountId"] == "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
		"after link the same account is kept in GOOGLE mode with the linked name")
	# Invalid challenge is rejected before any Google UI.
	asked.clear()
	server.replies["/api/account/google/challenge"] = [{"status": 200, "body": "{\"nonce\":\"short\"}"}]
	res = await node.google_flow(false)
	_check(not res["ok"] and asked.is_empty(), "an invalid challenge never opens the credential picker")
	# Cancel / already-linked messages (live text).
	server.replies["/api/account/google/challenge"] = [{"status": 200, "body": JSON.stringify({"nonce": nonce})}]
	node.credential_provider = func(_n: String) -> Dictionary: return {"status": "CANCELLED"}
	res = await node.google_flow(false)
	_check(not res["ok"] and res["message"] == "Google sign-in was cancelled. No account data was changed.", "cancelled sign-in changes nothing")
	node.credential_provider = func(_n: String) -> Dictionary: return {"status": "OK", "id_token": "t"}
	server.replies["/api/account/google/challenge"] = [{"status": 200, "body": JSON.stringify({"nonce": nonce})}]
	server.replies["/api/account/google/link"] = [{"status": 409, "body": "{\"error\":\"GOOGLE_IDENTITY_ALREADY_LINKED\",\"message\":\"x\"}"}]
	res = await node.google_flow(true)
	_check(not res["ok"] and res["message"].begins_with("This Google account is already linked to a different Ludo Paws account."), "already-linked Google identity: live message, nothing merged")
	# Restore (Google login): new session saved, recovery pending until identities are restored.
	server.replies["/api/account/google/challenge"] = [{"status": 200, "body": JSON.stringify({"nonce": nonce})}]
	server.replies["/api/account/google/login"] = [{"status": 200, "body": _session_body("eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee", 5000)}]
	server.replies["/api/account/identities/recover"] = [{"status": 200, "body": "{\"friend\":null,\"leaderboard\":null}"}]
	server.replies["/api/account/links"] = [{"status": 200, "body": "{\"googleLinked\":true}"}]
	res = await node.google_flow(false)
	_check(res["ok"] and svc.current_session()["accountId"] == "eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee" and store.mode == "GOOGLE"
		and not svc.requires_identity_recovery(), "restore: Google account active, no identities to recover -> recovery complete")
	server.replies["/api/account/google/challenge"] = [{"status": 200, "body": JSON.stringify({"nonce": nonce})}]
	server.replies["/api/account/google/login"] = [{"status": 200, "body": _session_body("eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee", 5000)}]
	server.replies["/api/account/identities/recover"] = [{"status": 200, "body": "{\"friend\":{\"friendId\":\"F\"},\"leaderboard\":null}"}]
	res = await node.google_flow(false)
	_check(res["ok"] and svc.requires_identity_recovery(), "restore with a Friend identity to recover stays pending (never a fresh identity)")
	# Guest deletion code only for guests; account deletion clears the session.
	res = await svc.guest_deletion_code()
	_check(not res["ok"] and res["error"] == "GUEST_DELETION_CODE_NOT_AVAILABLE", "deletion code only while the account is a guest")
	store.session = ""
	store.mode = "GUEST"
	svc._cached = null
	server.replies["/api/account/guest"] = [{"status": 200, "body": _session_body()}]
	await svc.ensure_guest_session()
	server.replies["/api/account/guest/deletion-code"] = [{"status": 200, "body": JSON.stringify({"credential": "f".repeat(64)})}]
	res = await svc.guest_deletion_code()
	var n := server.sent.size()
	var again: Dictionary = await svc.guest_deletion_code()
	_check(res["ok"] and res["code"] == "f".repeat(64) and again["code"] == res["code"] and server.sent.size() == n, "guest deletion code saved and reused unless replaced")
	server.replies["/api/account/delete"] = [{"status": 200, "body": "{\"deleted\":false}"}]
	res = await svc.delete_current_account()
	_check(not res["ok"] and res["error"] == "ACCOUNT_DELETE_INCOMPLETE" and not svc.current_session().is_empty(), "unconfirmed deletion keeps the session")
	server.replies["/api/account/delete"] = [{"status": 200, "body": "{\"deleted\":true}"}]
	res = await svc.delete_current_account()
	_check(res["ok"] and _body(server) == {"confirmation": "DELETE_MY_ACCOUNT"} and svc.current_session().is_empty(), "confirmed deletion clears the session")
	node.queue_free()

## Live LeaderboardActivity flow: register once (persistent request id), load with the
## profile token, cache per account.
func _leaderboard() -> void:
	var NativePrefs = load("res://scripts/game/platform/native_prefs.gd")
	NativePrefs.desktop_dir = "user://account_test_prefs"
	NativePrefs.reset_cache()
	for f in DirAccess.get_files_at("user://account_test_prefs"):
		DirAccess.remove_absolute(ProjectSettings.globalize_path("user://account_test_prefs/" + f))
	var store := MemStore.new()
	var server := FakeServer.new()
	var svc := Account.new("https://api.example.test", server, store)
	svc.now_s = func() -> int: return 1000
	var node := AccountNode.new()
	root.add_child(node)
	node.service = svc
	server.replies["/api/account/guest"] = [{"status": 200, "body": _session_body()}]
	await svc.ensure_guest_session()
	var Board = load("res://scripts/game/online/leaderboard_service.gd")
	var lb = Board.new(node)
	var token := "lpp_" + "b".repeat(40)
	server.replies["/api/leaderboard/profile/register"] = [{"status": 200, "body": JSON.stringify({"profileId": "cccccccc-cccc-4ccc-8ccc-cccccccccccc", "profileToken": token})}]
	server.replies["/api/leaderboard"] = [{"status": 200, "body": JSON.stringify({"entries": [{"rank": 1, "displayName": "Player", "games": 4, "wins": 3, "points": 170},
		{"rank": 2, "displayName": "", "games": 1, "wins": 0, "points": 20, "isViewer": true}], "scoring": {"finish": 20, "winBonus": 30}})}]
	var res: Dictionary = await lb.refresh()
	var reg_body = JSON.parse_string(server.sent.filter(func(r): return r["path"] == "/api/leaderboard/profile/register")[0]["body"])
	var load_req: Dictionary = server.sent.filter(func(r): return r["path"] == "/api/leaderboard")[0]
	_check(res["ok"] and res["snapshot"]["entries"].size() == 2 and res["snapshot"]["entries"][1]["displayName"] == "Player" and res["snapshot"]["entries"][1]["isViewer"]
		and reg_body["clientRequestId"] == lb.identity.registration_request_id() and Array(load_req["headers"]).has("Authorization: Bearer " + token),
		"leaderboard: register once with the device request id, load with the profile token")
	server.replies["/api/leaderboard"] = [{"status": 503, "body": "{}"}]
	var n := server.sent.size()
	res = await lb.refresh()
	_check(not res["ok"] and res["message"].begins_with("Could not refresh") and not server.sent.slice(n).any(func(r): return r["path"] == "/api/leaderboard/profile/register")
		and lb.cached()["snapshot"]["entries"].size() == 2, "the stored credential is reused; offline shows the saved ranking")
	store.session = JSON.stringify(JSON.parse_string(_session_body("dddddddd-dddd-4ddd-8ddd-dddddddddddd")))
	svc._cached = null
	_check(lb.cached().is_empty(), "another account never sees the cached ranking")
	node.queue_free()
	for f in DirAccess.get_files_at("user://account_test_prefs"):
		DirAccess.remove_absolute(ProjectSettings.globalize_path("user://account_test_prefs/" + f))
	NativePrefs.desktop_dir = ""
	NativePrefs.reset_cache()

static func _room(revision: int, random_index: int, pending = null, status := "ACTIVE") -> Dictionary:
	return {"schemaVersion": 1, "matchId": "LPABCDEFGH", "status": status, "hostPlayerId": "p0", "matchMode": "ONLINE",
		"targetPlayerCount": 2, "rulesetId": "ludoproof-standard-v8", "revision": revision, "randomEventIndex": random_index, "turnSeat": 0,
		"comebackWaits": [-1, -1], "winnerPlayerId": null,
		"players": [{"playerId": "p0", "seat": 0, "color": "RED", "playerType": "HUMAN", "tokens": [-1, -1, -1, -1]},
			{"playerId": "p1", "seat": 1, "color": "YELLOW", "playerType": "HUMAN", "tokens": [-1, -1, -1, -1]}],
		"pendingRoll": pending, "history": []}

## Live online flow on a fake server: matchmaking, validated MATCHED, seat capture,
## replay-safe roll / move ids, revision guard.
func _online_controller() -> void:
	var NativePrefs = load("res://scripts/game/platform/native_prefs.gd")
	NativePrefs.desktop_dir = "user://account_test_prefs"
	NativePrefs.reset_cache()
	for f in DirAccess.get_files_at("user://account_test_prefs"):
		DirAccess.remove_absolute(ProjectSettings.globalize_path("user://account_test_prefs/" + f))
	var store := MemStore.new()
	var server := FakeServer.new()
	var svc := Account.new("https://api.example.test", server, store)
	svc.now_s = func() -> int: return 1000
	var node := AccountNode.new()
	root.add_child(node)
	node.service = svc
	server.replies["/api/account/guest"] = [{"status": 200, "body": _session_body()}]
	await svc.ensure_guest_session()
	var GameApi = load("res://scripts/game/online/game_api.gd")
	var api = GameApi.new("https://api.example.test", server)
	var Controller = load("res://scripts/game/online/online_match_controller.gd")
	var c = Controller.new()
	root.add_child(c)
	c.set_process(false)
	c.setup(node, api, null)
	var token := "lpp_" + "b".repeat(40)
	var seat := "lp_" + "s".repeat(40)
	server.replies["/api/leaderboard/profile/register"] = [{"status": 200, "body": JSON.stringify({"profileId": "cccccccc-cccc-4ccc-8ccc-cccccccccccc", "profileToken": token})}]
	server.replies["/api/account/link/leaderboard"] = [{"status": 200, "body": "{}"}, {"status": 200, "body": "{}"}, {"status": 200, "body": "{}"}]
	server.replies["/api/matchmaking/search"] = [{"status": 200, "body": JSON.stringify({"status": "SEARCHING", "queuedPlayers": 1})}]
	var r: Dictionary = await c.begin_public(2)
	var search = JSON.parse_string(server.sent.filter(func(x): return x["path"] == "/api/matchmaking/search")[0]["body"])
	_check(r.get("searching", false) and not c.ticket().is_empty() and search["playerCount"] == 2 and search["clientRequestId"] == c.ticket()["requestId"]
		and search["rulesetKey"] == "CLASSIC_V8" and search["displayName"] == "Player", "public search keeps a persistent ticket (live request shape)")
	var broken := _room(1, 0)
	broken["players"][1]["color"] = "GREEN"
	server.replies["/api/matchmaking/status"] = [{"status": 200, "body": JSON.stringify({"status": "MATCHED", "targetPlayerCount": 2, "matchId": "LPABCDEFGH", "playerId": "p0", "playerToken": seat, "state": broken})},
		{"status": 200, "body": JSON.stringify({"status": "MATCHED", "targetPlayerCount": 2, "matchId": "lpabcdefgh", "playerId": "p0", "playerToken": seat, "state": _room(1, 0)})}]
	r = await c.poll_public()
	_check(r.get("pending", false) and not c.ticket().is_empty() and c.match_id.is_empty(), "an invalid MATCHED room is not accepted; the ticket stays for the next poll")
	r = await c.poll_public()
	var Sessions = load("res://scripts/game/online/player_session_store.gd")
	_check(r.get("matched", false) and c.ticket().is_empty() and c.match_id == "LPABCDEFGH" and Sessions.load_session()["playerToken"] == seat
		and c.is_my_turn(), "a validated MATCHED room captures the seat securely")
	# Roll: the request id survives a lost response and is reused on retry.
	server.replies["/api/matches/LPABCDEFGH/roll"] = [{"status": 0, "error": "TIMEOUT"}, {"status": 200, "body": JSON.stringify({"state": _room(2, 1, {"status": "RESOLVED", "seat": 0, "eventIndex": 0, "eventId": "roll:0", "legalTokenIndexes": [0, 1], "outcome": 6})})}]
	server.replies["/api/matches/LPABCDEFGH/state"] = [{"status": 200, "body": JSON.stringify({"state": _room(1, 0)})}]
	await c.roll()
	await c.roll()
	var rolls: Array = server.sent.filter(func(x): return x["path"] == "/api/matches/LPABCDEFGH/roll").map(func(x): return JSON.parse_string(x["body"]))
	_check(rolls.size() == 2 and rolls[0]["clientRequestId"] == rolls[1]["clientRequestId"] and int(rolls[0]["expectedEventIndex"]) == 0
		and c.state["pendingRoll"]["outcome"] == 6, "a retried roll reuses the same request id (server replays, never a second turn)")
	_check(not c.apply_response({"state": _room(1, 0)}) and c.state["revision"] == 2, "an older revision never replaces the current state")
	server.replies["/api/matches/LPABCDEFGH/move"] = [{"status": 200, "body": JSON.stringify({"state": _room(3, 1)})}]
	await c.move(1)
	var mv = JSON.parse_string(server.sent.filter(func(x): return x["path"] == "/api/matches/LPABCDEFGH/move")[0]["body"])
	_check(int(mv["tokenIndex"]) == 1 and int(mv["eventIndex"]) == 0 and c.state["revision"] == 3, "move sends the token and the verified roll's event index")
	# Team Up: ticket, search, validated 4-player TEAM_UP room -> TEAM_UP seat.
	var saved_id: String = c.match_id
	var saved_token: String = c.player_token
	var saved_state: Dictionary = c.state
	c.match_id = ""
	c.state = {}
	server.replies["/api/team-matchmaking/search"] = [{"status": 200, "body": JSON.stringify({"status": "SEARCHING", "queuedPlayers": 2})}]
	var tr: Dictionary = await c.begin_team()
	var tsearch = JSON.parse_string(server.sent.filter(func(x): return x["path"] == "/api/team-matchmaking/search")[0]["body"])
	_check(tr.get("searching", false) and tsearch["rulesetKey"] == "TEAM_UP_V7" and tsearch["displayName"] == "Player" and not c.team_ticket().is_empty(),
		"Team Up search keeps an account-bound ticket (live request shape)")
	var team := _room(1, 0)
	team["matchMode"] = "TEAM_UP"
	team["targetPlayerCount"] = 4
	team["rulesetId"] = "ludoproof-team-v7"
	team["teamAssignments"] = ["A", "B", "A", "B"]
	team["actingSeat"] = 0
	team["winnerTeamId"] = null
	team["comebackWaits"] = [-1, -1, -1, -1]
	team["players"] = []
	for i in 4:
		team["players"].append({"playerId": "p%d" % i, "seat": i, "color": ["RED", "GREEN", "YELLOW", "BLUE"][i], "teamId": ["A", "B", "A", "B"][i], "tokens": [-1, -1, -1, -1]})
	server.replies["/api/team-matchmaking/status"] = [{"status": 200, "body": JSON.stringify({"status": "MATCHED", "matchMode": "TEAM_UP", "matchId": "LPTEAMAAAA", "playerId": "p2", "playerToken": seat, "state": team})}]
	team["matchId"] = "LPTEAMAAAA"
	server.replies["/api/team-matchmaking/status"] = [{"status": 200, "body": JSON.stringify({"status": "MATCHED", "matchMode": "TEAM_UP", "matchId": "LPTEAMAAAA", "playerId": "p2", "playerToken": seat, "state": team})}]
	tr = await c.poll_team()
	_check(tr.get("matched", false) and c.mode == "TEAM_UP" and c.match_id == "LPTEAMAAAA" and c.team_ticket().is_empty(), "a validated Team Up room opens as a TEAM_UP seat (%s)" % [tr])
	var Env = load("res://scripts/game/online/match_envelope.gd")
	_check(Env.with_ruleset_key(team)["rulesetKey"] == "TEAM_UP_V7", "server snapshots render with the engine key for their rulesetId")
	c.match_id = saved_id
	c.player_token = saved_token
	c.state = saved_state
	c.mode = "ONLINE"
	Sessions.save({"matchId": saved_id, "playerId": "p0", "playerToken": seat, "mode": "ONLINE"})
	# Leave an active match: forfeit queued securely, sent, result recorded after FINISHED.
	var Profile = load("res://scripts/game/profile/profile_store.gd")
	Profile.account_id = func() -> String: return "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
	var games_before: int = Profile.load_state()["totalGames"]
	var done := _room(5, 1, null, "FINISHED")
	done["winnerPlayerId"] = "p1"
	done["finishOrderPlayerIds"] = ["p1"]
	server.replies["/api/matches/LPABCDEFGH/start"] = [{"status": 200, "body": JSON.stringify({"state": done})}]
	_check(c.leave() and c.match_id.is_empty() and Sessions.load_session().is_empty(), "leaving clears the seat")
	for i in 3:
		await process_frame
	var forfeit = JSON.parse_string(server.sent.filter(func(x): return x["path"] == "/api/matches/LPABCDEFGH/start")[0]["body"])
	_check(forfeit == {"forfeit": true} and Sessions.pending_forfeits().is_empty() and Profile.load_state()["totalGames"] == games_before + 1
		and Profile.load_state()["totalWins"] == 0, "the forfeit is sent and the confirmed loss is recorded once for the owner")
	# A terminal error clears a queued forfeit; a network error keeps it for later.
	Sessions.save_pending_forfeit({"matchId": "LPABCDEFGJ", "playerId": "p0", "playerToken": seat, "ranked": true})
	server.replies["/api/matches/LPABCDEFGJ/start"] = [{"status": 0, "error": "TIMEOUT"}, {"status": 404, "body": "{\"error\":\"MATCH_NOT_FOUND\"}"}]
	await c.retry_pending_forfeits()
	var kept: bool = Sessions.pending_forfeits().size() == 1
	await c.retry_pending_forfeits()
	_check(kept and Sessions.pending_forfeits().is_empty(), "a lost forfeit retries later; a match the server no longer has is dropped")
	Profile.account_id = Profile._session_account
	# Seat that the server no longer knows.
	c.capture_session({"matchId": "LPABCDEFGH", "playerId": "p0", "playerToken": seat}, "ONLINE")
	server.replies["/api/matches/LPABCDEFGH/state"] = [{"status": 404, "body": "{\"error\":\"MATCH_NOT_FOUND\"}"}]
	await c.refresh_state()
	_check(c.match_id.is_empty() and Sessions.load_session().is_empty() and not c.unavailable_reason.is_empty(), "a match the server no longer has is forgotten")
	c.queue_free()
	node.queue_free()
	for f in DirAccess.get_files_at("user://account_test_prefs"):
		DirAccess.remove_absolute(ProjectSettings.globalize_path("user://account_test_prefs/" + f))
	NativePrefs.desktop_dir = ""
	NativePrefs.reset_cache()

static func _friend_room(status := "WAITING", players := 1) -> Dictionary:
	var room := _room(1, 0, null, status)
	room["matchMode"] = "FRIENDS"
	room["targetPlayerCount"] = 2
	room["players"] = room["players"].slice(0, players)
	room["comebackWaits"] = room["comebackWaits"].slice(0, players)
	if players > 1:
		room["players"][1]["color"] = "GREEN"   # only public 2-player Online seats Yellow
	if status == "WAITING":
		room["turnSeat"] = null
	return room

func _friends() -> void:
	var NativePrefs = load("res://scripts/game/platform/native_prefs.gd")
	NativePrefs.desktop_dir = "user://account_test_prefs"
	NativePrefs.reset_cache()
	for f in DirAccess.get_files_at("user://account_test_prefs"):
		DirAccess.remove_absolute(ProjectSettings.globalize_path("user://account_test_prefs/" + f))
	var store := MemStore.new()
	var server := FakeServer.new()
	var svc := Account.new("https://api.example.test", server, store)
	svc.now_s = func() -> int: return 1000
	var node := AccountNode.new()
	root.add_child(node)
	node.service = svc
	server.replies["/api/account/guest"] = [{"status": 200, "body": _session_body()}]
	await svc.ensure_guest_session()
	var api = load("res://scripts/game/online/game_api.gd").new("https://api.example.test", server)
	var c = load("res://scripts/game/online/online_match_controller.gd").new()
	root.add_child(c)
	c.set_process(false)
	c.setup(node, api, null)
	var f = load("res://scripts/game/online/friends_service.gd").new()
	root.add_child(f)
	f.set_process(false)
	f.setup(node, api, c)
	var ftoken := "lf_" + "f".repeat(40)
	var seat := "lp_" + "r".repeat(40)
	server.replies["/api/friends/register"] = [{"status": 200, "body": JSON.stringify({"friendId": "LPF-ABCD-EFGH-JKLM", "friendToken": ftoken})}]
	server.replies["/api/account/link/friend"] = [{"status": 200, "body": "{}"}]
	server.replies["/api/friends/snapshot"] = [{"status": 200, "body": JSON.stringify({"friends": [{"friendId": "LPF-QQQQ-WWWW-EEEE", "displayName": "Player", "online": true}], "invites": []})}]
	server.replies["/api/friends/recent"] = [{"status": 200, "body": "{\"recent\":[]}"}]
	await f.ensure_identity()
	var reg = JSON.parse_string(server.sent.filter(func(x): return x["path"] == "/api/friends/register")[0]["body"])
	_check(f.credential["id"] == "LPF-ABCD-EFGH-JKLM" and reg["displayName"] == "Player" and f.friends().size() == 1
		and server.sent.any(func(x): return x["path"] == "/api/account/link/friend"), "Friend ID registered once (public name Player), linked, snapshot loaded")
	_check(not (await f.send_request("bad-id"))["ok"], "an invalid Friend ID is refused before any request")
	var created := _friend_room()
	server.replies["/api/matches"] = [{"status": 200, "body": JSON.stringify({"matchId": "LPABCDEFGH", "playerId": "p0", "playerToken": seat, "state": created})}]
	server.replies["/api/friends/invite"] = [{"status": 200, "body": "{}"}]
	server.replies["/api/friends/snapshot"] = [{"status": 200, "body": "{}"}]
	server.replies["/api/friends/recent"] = [{"status": 200, "body": "{}"}]
	server.replies["/api/matches/LPABCDEFGH/state"] = [{"status": 200, "body": JSON.stringify({"state": created})}]
	var r: Dictionary = await f.invite("LPF-QQQQ-WWWW-EEEE", 2)
	var create = JSON.parse_string(server.sent.filter(func(x): return x["path"] == "/api/matches")[0]["body"])
	var inv: Dictionary = server.sent.filter(func(x): return x["path"] == "/api/friends/invite")[0]
	_check(r["ok"] and create["matchMode"] == "FRIENDS" and int(create["targetPlayerCount"]) == 2 and c.mode == "FRIENDS" and c.match_id == "LPABCDEFGH"
		and Array(inv["headers"]).has("X-LudoProof-Room-Token: " + seat), "a private room is created, handed off as a FRIENDS seat and the invite carries the room token")
	c.forget_seat()
	server.replies["/api/friends/invite/respond"] = [{"status": 200, "body": JSON.stringify({"matchId": "LPABCDEFGH", "friendJoinToken": "FJT"})}]
	var joined := _friend_room("ACTIVE", 2)
	joined["turnSeat"] = 0
	server.replies["/api/matches/LPABCDEFGH/join"] = [{"status": 200, "body": JSON.stringify({"matchId": "LPABCDEFGH", "playerId": "p1", "playerToken": seat, "state": joined})}]
	server.replies["/api/friends/snapshot"] = [{"status": 200, "body": "{}"}]
	server.replies["/api/friends/recent"] = [{"status": 200, "body": "{}"}]
	server.replies["/api/matches/LPABCDEFGH/state"] = [{"status": 200, "body": JSON.stringify({"state": joined})}]
	r = await f.accept_invite("inv-1", "LPABCDEFGH")
	var join: Dictionary = server.sent.filter(func(x): return x["path"] == "/api/matches/LPABCDEFGH/join")[0]
	_check(r["ok"] and c.player_id == "p1" and Array(join["headers"]).has("X-LudoProof-Friend-Join-Token: FJT") and c.state.get("status") == "ACTIVE",
		"accepting an invite joins with the friend join token and opens the room")
	f.queue_free()
	c.queue_free()
	node.queue_free()
	for file in DirAccess.get_files_at("user://account_test_prefs"):
		DirAccess.remove_absolute(ProjectSettings.globalize_path("user://account_test_prefs/" + file))
	NativePrefs.desktop_dir = ""
	NativePrefs.reset_cache()
