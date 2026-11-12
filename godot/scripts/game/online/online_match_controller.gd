extends Node
## Live online match flow (MainActivity + OnlineMatchActions + PublicMatchmakingActions +
## OnlineSessionActions), UI-agnostic:
## - public matchmaking: ticket in `ludoproof_public_matchmaking_v1`, search -> status poll
##   every 1.5 s -> MATCHED accepted only with a complete validated ACTIVE room -> seat
##   captured (player_session_store) -> realtime;
## - the server is the only authority: every response passes match_envelope.gd and a
##   revision guard before it is applied; nothing is simulated locally;
## - roll / move: request ids persisted per operation in `ludoproof_pending_operation` so a
##   retry after a lost response replays the same event (never a second turn);
## - state poll every 3 s without a socket, 30 s with one; socket reconnect every 10 s.
## Signals carry the validated server state for the match screen.

signal state_changed(state: Dictionary, player_id: String)
signal status(text: String)
signal matchmaking(searching: bool, player_count: int)
signal unavailable(text: String)
## The server confirmed the match finished (results recorded, gem reward to refresh).
signal finished(state: Dictionary, gem_reward: bool)

const GameApi = preload("res://scripts/game/online/game_api.gd")
const Envelope = preload("res://scripts/game/online/match_envelope.gd")
const Sessions = preload("res://scripts/game/online/player_session_store.gd")
const Identity = preload("res://scripts/game/online/identity_store.gd")
const Realtime = preload("res://scripts/game/online/match_realtime_client.gd")
const NativePrefs = preload("res://scripts/game/platform/native_prefs.gd")
const Characters = preload("res://scripts/game/characters/catalog.gd")
const Catalog = preload("res://scripts/game/store/store_catalog.gd")

const MATCHMAKING_PREFS := "ludoproof_public_matchmaking_v1"
const OPERATION_PREFS := "ludoproof_pending_operation"
const TEAM_PREFS := "ludoproof_team_matchmaking"
const TEAM_POLL_MS := 2000
const MATCHMAKING_POLL_MS := 1500
const STATE_POLL_RECOVERY_MS := 3000
const STATE_POLL_LIVE_MS := 30000
const REALTIME_RECONNECT_MS := 10000

var account            # Account autoload (session + transport)
var api                # GameApi
var realtime           # MatchRealtimeClient (null in tests)
var inventory          # cosmetic_inventory (equipped Paw / dice)
var leaderboard = Identity.leaderboard()

var match_id := ""
var player_id := ""
var player_token := ""
var mode := "ONLINE"
var state := {}
var last_revision := -1
var busy := false
var unavailable_reason := ""
var _next_matchmaking_poll := 0
var _next_state_poll := 0
var _linked_leaderboard := ""
var _next_team_poll := 0

func setup(account_node, game_api, inventory_cache, realtime_client = null) -> void:
	account = account_node
	api = game_api
	inventory = inventory_cache
	realtime = realtime_client
	if realtime != null:
		add_child(realtime)
		realtime.snapshot.connect(func(payload): apply_response(payload, true))
		realtime.revision.connect(func(rev, _st): if rev > last_revision: refresh_state())
		realtime.unavailable.connect(func(reason): _set_unavailable(reason))
		api.command_channel = realtime.execute_command

## Launch: an open seat from the live store resumes (OnlineSessionActions restore).
func restore() -> bool:
	var s: Dictionary = Sessions.load_session()
	if s.is_empty():
		return false
	match_id = s["matchId"]
	player_id = s["playerId"]
	player_token = s["playerToken"]
	mode = s["mode"]
	_connect_realtime()
	await refresh_state()
	return true

# ---------------------------------------------------------------- public matchmaking

func ticket() -> Dictionary:
	var p = NativePrefs.open(MATCHMAKING_PREFS)
	var id = p.get_string("requestId", null)
	if id == null:
		return {}
	var t := {"requestId": id, "displayName": p.get_string("displayName", ""), "playerCount": p.get_int("playerCount", 0),
		"startedAt": p.get_long("startedAt", 0)}
	if RegEx.create_from_string("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$").search(id) == null \
			or str(t["displayName"]).strip_edges().is_empty() or not [2, 4].has(t["playerCount"]) or t["startedAt"] <= 0:
		_clear_ticket()
		return {}
	return t

func _clear_ticket() -> void:
	var p = NativePrefs.open(MATCHMAKING_PREFS)
	var e = p.edit()
	for k in p.keys():
		e.remove(k)
	e.commit()

func _in_match() -> bool:
	return not match_id.is_empty() and state.get("status") != "FINISHED"

## PublicMatchmakingActions.beginPublicMatchmaking (2 or 4 players).
func begin_public(player_count: int, display_name := "Player") -> Dictionary:
	if _in_match():
		status.emit("Finish the current online match before searching again.")
		return {"ok": false}
	var existing := ticket()
	if not existing.is_empty():
		matchmaking.emit(true, existing["playerCount"])
		return await poll_public()
	var t := {"requestId": GameApi.new_request_id(), "displayName": display_name, "playerCount": 4 if player_count == 4 else 2,
		"startedAt": int(Time.get_unix_time_from_system() * 1000.0)}
	if not NativePrefs.open(MATCHMAKING_PREFS).edit().put_string("requestId", t["requestId"]).put_string("displayName", t["displayName"]) \
			.put_int("playerCount", t["playerCount"]).put_long("startedAt", t["startedAt"]).commit():
		status.emit("Could not save matchmaking search.")
		return {"ok": false}
	matchmaking.emit(true, t["playerCount"])
	status.emit("Searching for a public match…")
	var cred := await _leaderboard_credential()
	if not cred["ok"]:
		return _network_failure(cred)
	var character := await _character_selection(cred["credential"]["token"])
	var res: Dictionary = await api.search_public_match(cred["credential"]["token"], t["displayName"], t["requestId"], t["playerCount"],
		character["characterId"], GameApi.RULESET_V8, character["accountToken"], character["matchStyle"])
	return _apply_matchmaking(res, t["playerCount"])

func poll_public() -> Dictionary:
	var t := ticket()
	if t.is_empty():
		return {"ok": false}
	if _in_match():
		_clear_ticket()
		matchmaking.emit(false, 0)
		return {"ok": false}
	var cred := await _leaderboard_credential()
	if not cred["ok"]:
		return {"ok": false}
	var res: Dictionary = await api.public_match_status(cred["credential"]["token"], t["requestId"], t["playerCount"])
	return _apply_matchmaking(res, t["playerCount"], true)

func cancel_public() -> Dictionary:
	var t := ticket()
	if t.is_empty():
		matchmaking.emit(false, 0)
		return {"ok": true}
	var cred := await _leaderboard_credential()
	if not cred["ok"]:
		return _network_failure(cred)
	var res: Dictionary = await api.cancel_public_match(cred["credential"]["token"], t["requestId"], t["playerCount"])
	if not res["ok"]:
		return _network_failure(res)
	if res["data"].get("status") == "MATCHED":
		status.emit("A match is being finalized and can no longer be cancelled. Reconnecting…" if res["data"].get("pending", false)
			else "A match was found while cancelling. Opening it now…")
		return await poll_public()
	_clear_ticket()
	matchmaking.emit(false, 0)
	status.emit("Matchmaking cancelled.")
	return {"ok": true, "cancelled": true}

## applyPublicMatchmakingResponse: MATCHED is accepted only with a complete, validated,
## ACTIVE room for this ticket and seat credentials; otherwise the ticket is kept so the
## next poll can recover it.
func _apply_matchmaking(res: Dictionary, count: int, quiet := false) -> Dictionary:
	if not res["ok"]:
		return {"ok": false} if quiet else _network_failure(res)
	var data: Dictionary = res["data"]
	match str(data.get("status", "")):
		"MATCHED":
			var target := 4 if int(data.get("targetPlayerCount", count)) == 4 else 2
			var env := Envelope.parse(data)
			var s = env.get("state")
			var assigned_id := str(data.get("matchId", "")).strip_edges().to_upper()
			var valid: bool = env["ok"] and s is Dictionary and s["matchId"] == assigned_id and s["matchMode"] == "ONLINE" \
				and s["status"] == "ACTIVE" and int(s["targetPlayerCount"]) == target and s["players"].size() == target \
				and not str(data.get("playerId", "")).strip_edges().is_empty() and not str(data.get("playerToken", "")).strip_edges().is_empty()
			if not valid:
				status.emit("Match was assigned; waiting for a complete, verified room snapshot…")
				return {"ok": false, "pending": true}
			_clear_ticket()
			matchmaking.emit(false, target)
			capture_session(data, "ONLINE")
			apply_response(data)
			return {"ok": true, "matched": true}
		"SEARCHING":
			matchmaking.emit(true, count)
			return {"ok": true, "searching": true, "queued": int(data.get("queuedPlayers", 1))}
	return {"ok": false}

# ---------------------------------------------------------------- Team Up (TeamUpActivity)

## TeamMatchmakingStore ("team_search_ticket" JSON, bound to the signed-in account).
func team_ticket() -> Dictionary:
	var raw = NativePrefs.open(TEAM_PREFS).get_string("team_search_ticket", null)
	var t = JSON.parse_string(str(raw)) if raw != null else null
	if not (t is Dictionary):
		return {}
	var owner = t.get("owner")
	if owner != null and str(owner) != "" and str(owner) != "null" and str(owner) != str(account.session().get("accountId", "")):
		return {}
	var name := str(t.get("displayName", ""))
	if name.length() < 2 or name.length() > 24 or int(t.get("startedAt", 0)) <= 0:
		return {}
	return t

func _clear_team_ticket() -> void:
	var p = NativePrefs.open(TEAM_PREFS)
	var e = p.edit()
	for k in p.keys():
		e.remove(k)
	e.commit()

func begin_team(display_name := "Player") -> Dictionary:
	if _in_match():
		status.emit("Finish the current online match before searching again.")
		return {"ok": false}
	var t := team_ticket()
	if t.is_empty():
		t = {"requestId": GameApi.new_request_id(), "displayName": display_name, "startedAt": int(Time.get_unix_time_from_system() * 1000.0),
			"owner": account.session().get("accountId")}
		NativePrefs.open(TEAM_PREFS).edit().put_string("team_search_ticket", JSON.stringify(t)).remove("cancel_pending").remove("assignment_pending").commit()
	matchmaking.emit(true, 4)
	var cred := await _leaderboard_credential()
	if not cred["ok"]:
		return _network_failure(cred)
	var character := await _character_selection(cred["credential"]["token"])
	var res: Dictionary = await api.request("POST", "/api/team-matchmaking/search", {"profile": cred["credential"]["token"], "character": character["accountToken"],
		"body": {"displayName": GameApi.public_name(t["displayName"]), "clientRequestId": t["requestId"], "characterId": character["characterId"],
			"matchStyle": character["matchStyle"], "rulesetKey": "TEAM_UP_V7"}})
	return _apply_team(res)

func poll_team() -> Dictionary:
	var t := team_ticket()
	if t.is_empty() or _in_match():
		return {"ok": false}
	var cred := await _leaderboard_credential()
	if not cred["ok"]:
		return {"ok": false}
	return _apply_team(await api.request("POST", "/api/team-matchmaking/status", {"profile": cred["credential"]["token"], "body": {"clientRequestId": t["requestId"]}}), true)

func cancel_team() -> Dictionary:
	var t := team_ticket()
	if t.is_empty():
		matchmaking.emit(false, 0)
		return {"ok": true}
	var cred := await _leaderboard_credential()
	if not cred["ok"]:
		return _network_failure(cred)
	var res: Dictionary = await api.request("POST", "/api/team-matchmaking/cancel", {"profile": cred["credential"]["token"], "body": {"clientRequestId": t["requestId"]}})
	if res["ok"] and res["data"].get("status") == "MATCHED":
		return _apply_team(res)
	if not res["ok"]:
		return _network_failure(res)
	_clear_team_ticket()
	matchmaking.emit(false, 0)
	status.emit("Team Up search cancelled.")
	return {"ok": true, "cancelled": true}

## applyMatchmakingResponse / openMatchedGame (Team Up).
func _apply_team(res: Dictionary, quiet := false) -> Dictionary:
	if not res["ok"]:
		return {"ok": false} if quiet else _network_failure(res)
	var data: Dictionary = res["data"]
	match str(data.get("status", "")):
		"MATCHED":
			var env := Envelope.parse(data)
			var s = env.get("state")
			var valid: bool = data.get("matchMode") == "TEAM_UP" and env["ok"] and s is Dictionary \
				and s["matchId"] == str(data.get("matchId", "")).strip_edges().to_upper() and s["matchMode"] == "TEAM_UP" \
				and s["status"] in ["ACTIVE", "FINISHED"] and int(s.get("targetPlayerCount", 0)) == 4 and s["players"].size() == 4 \
				and not str(data.get("playerId", "")).strip_edges().is_empty() and not str(data.get("playerToken", "")).strip_edges().is_empty()
			if not valid:
				status.emit("Team match reserved • waiting for a complete verified room snapshot…")
				return {"ok": false, "pending": true}
			_clear_team_ticket()
			matchmaking.emit(false, 4)
			capture_session(data, "TEAM_UP")
			apply_response(data)
			return {"ok": true, "matched": true}
		"FINALIZING":
			status.emit("Team match reserved • reconnecting to the room…")
			return {"ok": true, "searching": true, "queued": clampi(int(data.get("queuedPlayers", 4)), 1, 4)}
		"SEARCHING":
			matchmaking.emit(true, 4)
			return {"ok": true, "searching": true, "queued": clampi(int(data.get("queuedPlayers", 1)), 1, 4)}
		"IDLE":
			_clear_team_ticket()
			matchmaking.emit(false, 0)
			status.emit("Search expired. Tap QUICK TEAM to search again.")
			return {"ok": false, "expired": true}
	status.emit("Unexpected Team Up matchmaking response.")
	return {"ok": false}

# ---------------------------------------------------------------- seat + state

## captureSession: the seat is stored securely before anything else uses it.
func capture_session(data: Dictionary, seat_mode: String) -> void:
	var code := str(data["matchId"]).strip_edges().to_upper()
	if code != match_id:
		last_revision = -1
	match_id = code
	player_id = str(data["playerId"]).strip_edges()
	player_token = str(data["playerToken"]).strip_edges()
	mode = seat_mode
	unavailable_reason = ""
	Sessions.save({"matchId": match_id, "playerId": player_id, "playerToken": player_token, "mode": mode})
	_connect_realtime()

## applyResponse: validated snapshot for this match, never an older revision.
func apply_response(data: Dictionary, from_socket := false) -> bool:
	var env := Envelope.parse(data)
	if not env["ok"]:
		if not from_socket:
			status.emit("The server sent an invalid match state. Syncing again…")
		return false
	var s = env["state"]
	if not (s is Dictionary) or s["matchId"] != match_id:
		return false
	if int(s["revision"]) < last_revision:
		return false
	var was_finished: bool = state.get("matchId") == s["matchId"] and state.get("status") == "FINISHED"
	last_revision = int(s["revision"])
	state = s
	state_changed.emit(state, player_id)
	if s["status"] == "FINISHED" and not was_finished:
		_on_finished(s)
	return true

## OnlineResponseRenderer on an authoritative FINISHED state (live order).
func _on_finished(s: Dictionary) -> void:
	var Profile = load("res://scripts/game/profile/profile_store.gd")
	var Weekly = load("res://scripts/game/profile/weekly_challenge_store.gd")
	var me: Dictionary = {}
	for p in s["players"]:
		if p["playerId"] == player_id:
			me = p
	var mine: Array = [str(me["characterId"])] if me.has("characterId") else []
	var winners: Array = s["players"].filter(func(p): return (p.get("teamId") == s.get("winnerTeamId")) if s.get("winnerTeamId") != null else p["playerId"] == s.get("winnerPlayerId"))
	if not winners.is_empty() and winners.all(func(p): return Array(p["tokens"]).all(func(t): return int(t) == 57)):
		if not me.is_empty():
			load("res://scripts/game/growth/engagement_store.gd").completed(s["matchId"])
		if int(s.get("finishedAt", 0)) > 0:
			Weekly.completed(s["matchId"], mine, int(s["finishedAt"]))
		load("res://scripts/game/growth/referral_service.gd").completed_match(s["matchId"])
	Profile.record_character_match(s["matchId"], mine)
	if s["matchMode"] == "ONLINE":   # ranked: GameMode.ONLINE only
		Profile.record_completed_match(s["matchId"], "ONLINE", s.get("winnerPlayerId") == player_id, mine)
	if realtime != null:
		realtime.disconnect_match()
	var rewards = s.get("matchGemRewards")
	var reward: bool = rewards is Array and rewards.any(func(r): return r is Dictionary and r.get("playerId") == player_id and int(r.get("amount", 0)) in [1, 2, 3])
	finished.emit(s, reward)

func refresh_state() -> Dictionary:
	if match_id.is_empty() or player_token.is_empty():
		return {"ok": false}
	var res: Dictionary = await api.state(match_id, player_token)
	if res["ok"]:
		apply_response(res["data"])
	else:
		_session_error(res)
	return res

func is_my_turn() -> bool:
	if state.get("status") != "ACTIVE":
		return false
	var seat := int(state.get("actingSeat", state.get("turnSeat", -1))) if state.get("actingSeat") != null else int(state.get("turnSeat", -1))
	var players: Array = state.get("players", [])
	return seat >= 0 and seat < players.size() and players[seat]["playerId"] == player_id

## OnlineMatchActions.rollVerifiedDice: the server decides the outcome.
func roll() -> Dictionary:
	if busy or state.get("status") != "ACTIVE":
		return {"ok": false}
	if state.get("pendingRoll") is Dictionary and state["pendingRoll"].get("status") == "RESOLVED":
		status.emit("Move a highlighted token before rolling again.")
		return {"ok": false}
	var key := "%s:%d" % [match_id, int(state["randomEventIndex"])]
	var request_id := _operation("roll", key)
	busy = true
	var res: Dictionary = await api.roll(match_id, player_token, request_id, int(state["randomEventIndex"]))
	busy = false
	if res["ok"]:
		_clear_operation("roll", key)
		apply_response(res["data"])
	else:
		_session_error(res)
		await refresh_state()   # reconcileOnError
	return res

## OnlineMatchActions.moveToken: only a legal token of the verified pending roll.
func move(token_index: int) -> Dictionary:
	var pending = state.get("pendingRoll")
	if busy or not (pending is Dictionary) or int(pending.get("eventIndex", -1)) < 0:
		status.emit("No verified roll is available for this move.")
		return {"ok": false}
	var event_index := int(pending["eventIndex"])
	var key := "%s:%d:%d" % [match_id, event_index, token_index]
	var request_id := _operation("move", key)
	busy = true
	var res: Dictionary = await api.move(match_id, player_token, token_index, event_index, request_id)
	busy = false
	if res["ok"]:
		_clear_operation("move", key)
		apply_response(res["data"])
	else:
		_session_error(res)
		await refresh_state()
	return res

## PendingOperationStore.getOrCreate / clear.
static func _operation(type: String, key: String) -> String:
	var p = NativePrefs.open(OPERATION_PREFS)
	var existing = p.get_string("requestId", null)
	if p.get_string("type", "") == type and p.get_string("operationKey", "") == key and existing != null:
		return existing
	var id := GameApi.new_request_id()
	p.edit().put_string("type", type).put_string("operationKey", key).put_string("requestId", id).commit()
	return id

static func _clear_operation(type: String, key: String) -> void:
	var p = NativePrefs.open(OPERATION_PREFS)
	if p.get_string("type", "") == type and p.get_string("operationKey", "") == key:
		p.edit().remove("type").remove("operationKey").remove("requestId").commit()

## Seat no longer valid (live resetInvalidSessionIfNeeded).
func _session_error(res: Dictionary) -> void:
	var code := str(res.get("error", ""))
	if code in ["MATCH_NOT_FOUND", "INVALID_PLAYER_TOKEN", "PLAYER_NOT_IN_MATCH", "HTTP_404", "HTTP_401", "HTTP_403"]:
		_set_unavailable("This online match is no longer available.")
		forget_seat()

func _set_unavailable(reason: String) -> void:
	unavailable_reason = reason
	unavailable.emit(reason)

# ---------------------------------------------------------------- leaving (RemoteExitActions)

const TERMINAL_FORFEIT_CODES := ["INVALID_MATCH_STATUS", "MATCH_NOT_FOUND", "INVALID_PLAYER_TOKEN", "AUTH_REQUIRED", "AUTH_INVALID"]
const FORFEIT_RECONCILE_DELAY_MS := 900
const FORFEIT_STATE_CHECKS := 3
var _forfeits_in_flight := {}

## abandonRemoteSessionState: an unfinished match is forfeited on the server (queued
## securely first, retried until confirmed); the seat and search are cleared.
func leave() -> bool:
	var me_forfeited: bool = state.get("players", []).any(func(p): return p["playerId"] == player_id and p.get("forfeited", false))
	if not match_id.is_empty() and state.get("status") != "FINISHED" and not me_forfeited:
		if player_token.is_empty() or player_id.is_empty():
			status.emit("Could not securely prepare the match exit. Stay in the match and sync before trying again.")
			return false
		if not Sessions.save_pending_forfeit({"matchId": match_id, "playerId": player_id, "playerToken": player_token,
				"ranked": mode == "ONLINE", "ownerAccountId": account.session().get("accountId") if account != null else null}):
			status.emit("Could not securely save the forfeit. Stay in the match and try again.")
			return false
	_clear_ticket()
	_clear_team_ticket()
	forget_seat()
	retry_pending_forfeits()
	return true

func retry_pending_forfeits() -> void:
	for op in Sessions.pending_forfeits():
		var key := str(op["matchId"]).to_upper()
		if _forfeits_in_flight.has(key):
			continue
		_forfeits_in_flight[key] = true
		await _process_forfeit(op)
		_forfeits_in_flight.erase(key)

func _process_forfeit(op: Dictionary) -> void:
	var res: Dictionary = await api.request("POST", "/api/matches/%s/start" % op["matchId"], {"player": op["playerToken"], "body": {"forfeit": true}})
	if not res["ok"]:
		var code := str(res.get("error", ""))
		if code in ["INVALID_MATCH_STATUS", "PLAYER_FORFEITED"]:
			res = await api.state(op["matchId"], op["playerToken"])
			if not res["ok"]:
				if TERMINAL_FORFEIT_CODES.has(str(res.get("error", ""))):
					Sessions.clear_pending_forfeit(op["matchId"])
				return
		elif TERMINAL_FORFEIT_CODES.has(code):
			Sessions.clear_pending_forfeit(op["matchId"])
			return
		else:
			return   # stays queued for a later retry
	for i in FORFEIT_STATE_CHECKS + 1:
		var snap = res["data"].get("state", res["data"])
		var st := str(snap.get("status", ""))
		if st == "FINISHED":
			_record_forfeit_result(op, snap.get("winnerPlayerId"))
			return
		if st != "ACTIVE":
			Sessions.clear_pending_forfeit(op["matchId"])
			return
		if i == FORFEIT_STATE_CHECKS:
			return
		await get_tree().create_timer(FORFEIT_RECONCILE_DELAY_MS / 1000.0).timeout
		res = await api.state(op["matchId"], op["playerToken"])
		if not res["ok"]:
			return

## recordConfirmedResult: ranked results only after the server confirms FINISHED.
func _record_forfeit_result(op: Dictionary, winner) -> void:
	if op.get("ranked", false) and op.get("ownerAccountId") != null:
		var Profile = load("res://scripts/game/profile/profile_store.gd")
		var owner: String = str(op["ownerAccountId"])
		var saved: Callable = Profile.account_id
		Profile.account_id = func() -> String: return owner
		Profile.record_completed_match(op["matchId"], "ONLINE", winner == op["playerId"], [])
		Profile.account_id = saved
	Sessions.clear_pending_forfeit(op["matchId"])

func forget_seat() -> void:
	Sessions.clear()
	if realtime != null:
		realtime.disconnect_match()
	match_id = ""
	player_id = ""
	player_token = ""
	state = {}
	last_revision = -1

func _connect_realtime() -> void:
	if realtime != null and not match_id.is_empty() and not player_token.is_empty():
		realtime.connect_match(match_id, player_token)

func _network_failure(res: Dictionary) -> Dictionary:
	status.emit(str(res.get("message", "Online service is not available right now.")))
	return {"ok": false, "error": res.get("error", "")}

# ---------------------------------------------------------------- identities

## ensureLeaderboardCredential (+ ensureLinkedLeaderboardWallet once per process).
func _leaderboard_credential() -> Dictionary:
	var service = load("res://scripts/game/online/leaderboard_service.gd").new(account)
	service.identity = leaderboard
	var c: Dictionary = await service.ensure_credential()
	if c["ok"] and _linked_leaderboard != c["credential"]["token"]:
		var linked: Dictionary = await account.service.link_leaderboard_identity(c["credential"]["token"])
		if linked["ok"]:
			_linked_leaderboard = c["credential"]["token"]
	return c

## RemoteCharacterAccountSelection.current: equipped Paw + dice; the account token is sent
## only when a premium Paw (required) or dice (best effort) needs an ownership check.
func _character_selection(profile_token: String) -> Dictionary:
	var character: String = inventory.selected_character() if inventory != null else Characters.DEFAULT_CHARACTER_ID
	var dice_id: String = inventory.selected_id(Catalog.DICE) if inventory != null else Catalog.DEFAULT_DICE
	var dice = Catalog.built_in(dice_id)
	var needs_paw := Catalog.character_entitlement_id(character) != null
	var needs_dice: bool = dice == null or dice["unlockKind"] != Catalog.FREE or int(dice["requiredLevel"]) > 0
	var token = null
	if needs_paw or needs_dice:
		var v: Dictionary = await account.service.require_session()
		token = v["session"]["accessToken"] if v["ok"] else null
	return {"characterId": character, "accountToken": token, "matchStyle": {"diceId": dice_id}}

# ---------------------------------------------------------------- timers

func _process(_delta: float) -> void:
	var now := Time.get_ticks_msec()
	if not busy and now >= _next_matchmaking_poll:
		_next_matchmaking_poll = now + MATCHMAKING_POLL_MS
		if not ticket().is_empty() and not _in_match():
			poll_public()
	if not busy and now >= _next_team_poll:
		_next_team_poll = now + TEAM_POLL_MS
		if account != null and not team_ticket().is_empty() and not _in_match():
			poll_team()
	if not busy and now >= _next_state_poll and not match_id.is_empty() and unavailable_reason.is_empty() and state.get("status") != "FINISHED":
		var live: bool = realtime != null and realtime.connected
		_next_state_poll = now + (STATE_POLL_LIVE_MS if live else STATE_POLL_RECOVERY_MS)
		refresh_state()
