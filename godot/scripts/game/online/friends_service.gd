extends Node
## Live Friends data flow (FriendsActivity + FriendPresenceController +
## FriendRoomHandoffValidator), UI-agnostic. Friend ID from FriendIdentityStore (registered
## once with POST /api/friends/register, linked to the game account), a snapshot every 10 s
## (friends, requests, invites, blocked, recent players, the open room's state), presence
## heartbeat every 30 s, requests / invites / block / report, and private rooms that hand
## off to online_match_controller.gd as FRIENDS seats. Family mode: public names "Player".
## Calls return (await) {ok, ...} | {ok: false, message}.

signal changed

const Identity = preload("res://scripts/game/online/identity_store.gd")
const Envelope = preload("res://scripts/game/online/match_envelope.gd")
const Sessions = preload("res://scripts/game/online/player_session_store.gd")
const GameApi = preload("res://scripts/game/online/game_api.gd")
const REFRESH_MS := 10000
const HEARTBEAT_MS := 30000
const FRIEND_ID := "^LPF-[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}$"

var account
var api
var controller          # online_match_controller.gd
var identity = Identity.friend()
var credential := {}
var snapshot := {}
var status_text := ""
var busy := false
var _linked := ""
var _next_refresh := 0
var _next_heartbeat := 0
var active := false     # the Friends screen is open (live onStart / onStop)

func setup(account_node, game_api, match_controller) -> void:
	account = account_node
	api = game_api
	controller = match_controller
	credential = identity.load_credential()

func _status(text: String) -> void:
	status_text = text
	changed.emit()

func _display_name() -> String:
	return load("res://scripts/game/profile/profile_store.gd").load_state()["displayName"]

## Live ensureLinkedFriendWallet: once per process per account + token.
func _link_wallet() -> void:
	var key := "%s:%s" % [account.session().get("accountId", ""), credential.get("token", "")]
	if credential.is_empty() or _linked == key:
		return
	var res: Dictionary = await account.service.link_friend_identity(credential["token"])
	if res["ok"]:
		_linked = key

## FriendsActivity.ensureIdentity.
func ensure_identity() -> Dictionary:
	if not credential.is_empty():
		return {"ok": true}
	if busy:
		return {"ok": false}
	busy = true
	_status("Creating your Friend ID…")
	var request_id: String = identity.registration_request_id()
	var res: Dictionary = await api.register_friend(_display_name(), request_id)
	busy = false
	if not res["ok"]:
		_status("Friends are unavailable right now. Check your connection and try again.")
		return res
	var next := {"id": str(res["data"].get("friendId", "")), "token": str(res["data"].get("friendToken", "")), "requestId": request_id}
	if not identity.save(next):
		_status("Friend ID was created but could not be secured on this device.")
		return {"ok": false}
	credential = next
	await _link_wallet()
	_status("Friend ID ready.")
	return await sync()

## FriendsActivity.syncFriends: snapshot + recent + the open room's state.
func sync(announce := false) -> Dictionary:
	if credential.is_empty():
		return await ensure_identity()
	if busy:
		return {"ok": false}
	busy = true
	if announce:
		_status("Refreshing friends…")
	await _link_wallet()
	var res: Dictionary = await api.friend_snapshot(credential["token"])
	if res["ok"]:
		var result: Dictionary = res["data"]
		var recent: Dictionary = await api.friend_recent(credential["token"])
		if recent["ok"]:
			result["_recent"] = recent["data"]
		var seat := _friend_seat()
		if not seat.is_empty():
			var room: Dictionary = await api.state(seat["matchId"], seat["playerToken"])
			if room["ok"] and room["data"].get("state") is Dictionary:
				result["_roomState"] = room["data"]["state"]
			elif str(room.get("error", "")) in ["MATCH_NOT_FOUND", "AUTH_INVALID"]:
				result["_roomUnavailable"] = true
		snapshot = result
		var rs = result.get("_roomState")
		if result.get("_roomUnavailable", false) or (rs is Dictionary and rs.get("status") == "FINISHED"):
			if controller == null or controller.match_id == seat.get("matchId", ""):
				Sessions.clear()
		if announce:
			status_text = "Friends refreshed."
	elif str(res.get("error", "")) in ["INVALID_FRIEND_TOKEN", "AUTH_INVALID"]:
		identity.clear_credential_only()
		credential = {}
	busy = false
	changed.emit()
	return res

func _friend_seat() -> Dictionary:
	var s: Dictionary = Sessions.load_session()
	return s if s.get("mode") == "FRIENDS" else {}

func friends() -> Array: return Array(snapshot.get("friends", []))
func incoming_requests() -> Array: return Array(snapshot.get("incomingRequests", []))
func outgoing_requests() -> Array: return Array(snapshot.get("outgoingRequests", []))
func invites() -> Array: return Array(snapshot.get("invites", []))
func blocked() -> Array: return Array(snapshot.get("blockedFriends", []))
func recent() -> Array: return Array(snapshot.get("_recent", {}).get("recent", []))
func room_state() -> Dictionary:
	var rs = snapshot.get("_roomState")
	return rs if rs is Dictionary else {}

func _simple(call, ok_text: String) -> Dictionary:
	if credential.is_empty():
		return {"ok": false}
	var res: Dictionary = await call
	_status(ok_text if res["ok"] else str(res.get("message", "Request failed.")))
	if res["ok"]:
		await sync()
	return res

func send_request(target: String) -> Dictionary:
	var id := target.strip_edges().to_upper()
	if RegEx.create_from_string(FRIEND_ID).search(id) == null:
		_status("Enter a valid Friend ID like LPF-ABCD-EFGH-JKLM.")
		return {"ok": false}
	return await _simple(api.send_friend_request(credential.get("token", ""), id), "Friend request sent.")

func respond_request(request_id: String, accept: bool) -> Dictionary:
	return await _simple(api.respond_friend_request(credential["token"], request_id, accept), "Friend added." if accept else "Request declined.")

func remove(friend_id: String) -> Dictionary:
	return await _simple(api.remove_friend(credential["token"], friend_id), "Friend removed.")

func block(friend_id: String) -> Dictionary:
	return await _simple(api.block_friend(credential["token"], friend_id), "Player blocked. They cannot message or invite you.")

func unblock(friend_id: String) -> Dictionary:
	return await _simple(api.unblock_friend(credential["token"], friend_id), "Player unblocked.")

func report(friend_id: String, reason: String, message_id = null) -> Dictionary:
	return await _simple(api.report_friend(credential["token"], friend_id, reason, GameApi.new_request_id(), message_id), "Report sent. Thank you.")

## FriendRoomHandoffValidator.isValidFriendRoomHandoff.
static func valid_handoff(response: Dictionary, match_id: String, player_id: String, player_count = null) -> bool:
	if match_id.is_empty() or player_id.is_empty() or str(response.get("playerToken", "")).strip_edges().is_empty() \
			or str(response.get("matchId", "")).strip_edges().to_upper() != match_id:
		return false
	var env := Envelope.parse(response)
	var s = env.get("state")
	return env["ok"] and s is Dictionary and s["matchId"] == match_id and s["matchMode"] == "FRIENDS" and s["status"] in ["WAITING", "ACTIVE"] \
		and s["targetPlayerCount"] != null and (player_count == null or int(s["targetPlayerCount"]) == int(player_count)) \
		and s["players"].any(func(p): return p["playerId"] == player_id)

## createFriendRoomInternal: the open room is reused; a new one keeps its request id.
func create_room(player_count: int) -> Dictionary:
	if credential.is_empty():
		_status("Friend identity is not ready yet.")
		return {"ok": false}
	var seat := _friend_seat()
	if not seat.is_empty():
		return {"ok": true, "seat": seat}
	var name := _display_name()
	var key := "friends:%d:%s" % [player_count, name]
	var request_id: String = controller._operation("friend_create", key)
	_status("Preparing a private room…")
	var character: Dictionary = await controller._character_selection(credential["token"])
	var res: Dictionary = await api.create_friend_room(credential["token"], name, request_id, player_count, character["characterId"],
		character["accountToken"], character["matchStyle"])
	if not res["ok"]:
		_status(str(res.get("message", "Could not create the private room.")))
		return res
	var data: Dictionary = res["data"]
	var room := str(data.get("matchId", "")).strip_edges().to_upper()
	var pid := str(data.get("playerId", "")).strip_edges()
	if not valid_handoff(data, room, pid, player_count):
		_status("Private room response is incomplete • retry the room request.")
		return {"ok": false}
	controller._clear_operation("friend_create", key)
	controller.capture_session(data, "FRIENDS")
	controller.apply_response(data)
	_status("Private room ready.")
	return {"ok": true, "seat": {"matchId": room, "playerId": pid, "playerToken": controller.player_token}}

func invite(friend_id: String, player_count := 2) -> Dictionary:
	var room := await create_room(player_count)
	if not room["ok"]:
		return room
	return await _simple(api.send_friend_invite(credential["token"], friend_id, room["seat"]["matchId"], room["seat"]["playerToken"], GameApi.new_request_id()),
		"Private-room invite sent.")

## acceptInvite: accept -> join with the friend join token -> validated handoff -> seat.
func accept_invite(invite_id: String, match_id: String) -> Dictionary:
	var name := _display_name()
	var key := "friends_join:%s:%s" % [match_id, name]
	var request_id: String = controller._operation("friend_join", key)
	_status("Joining private room…")
	var accepted: Dictionary = await api.respond_friend_invite(credential["token"], invite_id, true, request_id)
	if not accepted["ok"]:
		_status(str(accepted.get("message", "Could not join the room.")))
		return accepted
	if str(accepted["data"].get("matchId", "")) != match_id:
		_status("Accepted invite does not match the selected room.")
		return {"ok": false}
	var character: Dictionary = await controller._character_selection(credential["token"])
	var joined: Dictionary = await api.join_match(match_id, name, request_id, null, str(accepted["data"].get("friendJoinToken", "")),
		character["characterId"], character["accountToken"], character["matchStyle"])
	if not joined["ok"]:
		_status(str(joined.get("message", "Could not join the room.")))
		return joined
	var data: Dictionary = joined["data"]
	if not valid_handoff(data, match_id.to_upper(), str(data.get("playerId", "")).strip_edges()):
		_status("Private room response is incomplete • retry the room request.")
		return {"ok": false}
	controller._clear_operation("friend_join", key)
	controller.capture_session(data, "FRIENDS")
	controller.apply_response(data)
	_status("Joined the private room.")
	await sync()
	return {"ok": true}

func decline_invite(invite_id: String) -> Dictionary:
	return await _simple(api.respond_friend_invite(credential["token"], invite_id, false), "Room invite declined.")

## The host starts a full room (POST /api/matches/{id}/start).
func start_room() -> Dictionary:
	if controller.match_id.is_empty():
		return {"ok": false}
	var res: Dictionary = await api.start(controller.match_id, controller.player_token)
	if res["ok"]:
		controller.apply_response(res["data"])
	else:
		_status(str(res.get("message", "The room could not start yet.")))
	return res

func _process(_delta: float) -> void:
	var now := Time.get_ticks_msec()
	if active and now >= _next_refresh:
		_next_refresh = now + REFRESH_MS
		sync()
	# FriendPresenceController: best-effort presence every 30 s while the app runs.
	if not credential.is_empty() and now >= _next_heartbeat:
		_next_heartbeat = now + HEARTBEAT_MS
		api.friend_heartbeat(credential["token"], _display_name())
