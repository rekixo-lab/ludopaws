extends Node
## Port of live MatchRealtimeClient + RealtimeReconnectBackoff + MatchCommandChannel:
## WSS to /api/matches/{ID}/events with the player's bearer token; ROLL / MOVE commands over
## the socket ({type: command, requestId, action, body} -> command_result), 1.5 s ack timeout
## after which the caller retries the SAME command id over HTTP; SYNC / STATE_CHANGED deliver
## verified snapshots (matchId + revision must agree) or revision hints; QUICK_CHAT only for
## catalogued emoji; reconnect with exponential backoff 1 s x 2^min(attempt,4) with 0.8-1.2
## jitter; close code 1008 is terminal (no retry with rejected credentials).
## Limitation vs live: Godot's WebSocketPeer does not expose the handshake HTTP status, so
## 4xx handshake rejections are retried with backoff instead of being terminal.
## Voice chat (VOICE_*) is not part of V2: those messages are ignored.

signal connection_changed(connected: bool)
signal snapshot(payload: Dictionary)
signal revision(revision: int, status)
signal quick_chat(player_id: String, display_name: String, emoji: String)
signal unavailable(reason: String)

const COMMAND_TIMEOUT_MS := 1500
const MAX_PENDING_COMMANDS := 16
const MAX_MESSAGE_CHARS := 512 * 1024
const BASE_DELAY_MS := 1000
const MAX_EXPONENT := 4

var ws_base := ""
var version_name := "2.0.0"
## Allowed quick-chat emoji (QuickChatEmojiCatalog.EMOJIS).
var emoji_catalog: Array = []

var match_id := ""
var player_token := ""
var connected := false
var _socket: WebSocketPeer
var _attempt := 0
var _retry_at_ms := -1
var _pending := {}     # requestId -> {done: bool, response}
var _terminal := false

func _init(base_url := "", app_version := "2.0.0") -> void:
	ws_base = base_url.rstrip("/").replace("https://", "wss://") if base_url.begins_with("https://") else ""
	version_name = app_version

## RealtimeReconnectBackoff.delayMillis.
static func backoff_ms(attempt: int, jitter_unit: float) -> int:
	var exponent := clampi(attempt, 0, MAX_EXPONENT)
	var base := BASE_DELAY_MS << exponent
	return int(base * (0.80 + 0.40 * clampf(jitter_unit, 0.0, 1.0)))

## RealtimeConnectionFailurePolicy (used when the platform reports an HTTP status).
static func is_terminal_http(status: int) -> bool:
	return status >= 400 and status <= 499 and status not in [408, 425, 429]

static func terminal_message(status: int) -> String:
	match status:
		401, 403: return "Online session is unavailable. Please check access or sign in again."
		404: return "This match is no longer available."
		410, 426: return "Please update Ludo Paws to keep playing online."
	return "This online connection was rejected. Return Home and try again."

func connect_match(id: String, token: String) -> void:
	assert(ws_base.begins_with("wss://"), "LudoProof realtime must use WSS")
	if id.to_upper() == match_id and token == player_token and (connected or _socket != null):
		return
	disconnect_match()
	match_id = id.to_upper()
	player_token = token
	_terminal = false
	_attempt = 0
	_open()

func disconnect_match() -> void:
	if _socket != null:
		_socket.close(1000, "client")
	_socket = null
	_release_pending()
	_retry_at_ms = -1
	if connected:
		connected = false
		connection_changed.emit(false)
	match_id = ""
	player_token = ""

func _open() -> void:
	_socket = WebSocketPeer.new()
	_socket.handshake_headers = PackedStringArray(["Authorization: Bearer " + player_token, "User-Agent: LudoProof-Android/" + version_name])
	_socket.inbound_buffer_size = MAX_MESSAGE_CHARS + 1024
	var err := _socket.connect_to_url("%s/api/matches/%s/events" % [ws_base, match_id])
	if err != OK:
		_socket = null
		_schedule_retry()

func _schedule_retry() -> void:
	if _terminal or match_id.is_empty() or _retry_at_ms >= 0:
		return
	_retry_at_ms = Time.get_ticks_msec() + backoff_ms(_attempt, randf())
	_attempt = mini(_attempt + 1, MAX_EXPONENT)

func _process(_delta: float) -> void:
	if _retry_at_ms >= 0 and Time.get_ticks_msec() >= _retry_at_ms:
		_retry_at_ms = -1
		if not match_id.is_empty() and _socket == null:
			_open()
	if _socket == null:
		return
	_socket.poll()
	var state := _socket.get_ready_state()
	if state == WebSocketPeer.STATE_OPEN:
		if not connected:
			connected = true
			_attempt = 0
			connection_changed.emit(true)
		while _socket.get_available_packet_count() > 0:
			handle_text(_socket.get_packet().get_string_from_utf8())
	elif state == WebSocketPeer.STATE_CLOSED:
		var code := _socket.get_close_code()
		_socket = null
		_release_pending()
		if connected:
			connected = false
			connection_changed.emit(false)
		if code == 1008:
			_terminal = true
			unavailable.emit("Match connection is no longer authorized.")
		else:
			_schedule_retry()

## One realtime message (exposed for tests).
func handle_text(text: String) -> void:
	if text.length() > MAX_MESSAGE_CHARS:
		return
	var payload = JSON.parse_string(text)
	if not (payload is Dictionary):
		return
	var type := str(payload.get("type", ""))
	match type:
		"command_result":
			var pending = _pending.get(str(payload.get("requestId", "")))
			if pending != null:
				var response = payload.get("response")
				if not (response is Dictionary):
					response = {}
				var status := int(payload.get("statusCode", 200))
				if (status < 200 or status > 299) and not response.has("error"):
					response["error"] = "HTTP_%d" % status
				pending["response"] = response
				pending["done"] = true
			return
		"VOICE_PEERS", "VOICE_SIGNAL", "VOICE_ENDED":
			return
		"QUICK_CHAT":
			var sender := str(payload.get("playerId", "")).strip_edges()
			var name := str(payload.get("displayName", "")).strip_edges()
			var emoji := str(payload.get("emoji", ""))
			if not sender.is_empty() and not name.is_empty() and emoji_catalog.has(emoji):
				quick_chat.emit(sender, name, emoji)
			return
		"SYNC", "STATE_CHANGED":
			pass
		_:
			return
	var incoming := str(payload.get("matchId", ""))
	if not incoming.is_empty() and incoming != match_id:
		return
	var state = payload.get("state")
	if state is Dictionary and str(state.get("matchId", "")) == match_id and int(state.get("revision", -1)) >= 0 \
			and int(state.get("revision", -2)) == int(payload.get("revision", -1)):
		snapshot.emit(payload)
		return
	var rev := int(payload.get("revision", -1))
	if rev >= 0:
		var status = payload.get("status")
		revision.emit(rev, status if status is String and not status.is_empty() else null)

## MatchCommandChannel: returns (await) the server response, or null when REST should retry
## the exact same command (not connected, other match / token, queue full, send failed, or
## no acknowledgement within COMMAND_TIMEOUT_MS).
func execute_command(id: String, token: String, action: String, body: Dictionary):
	assert(action == "ROLL" or action == "MOVE", "Unsupported match command")
	assert(not str(body.get("clientRequestId", "")).is_empty(), "clientRequestId is required")
	if not connected or _socket == null or id.to_upper() != match_id or token != player_token or _pending.size() >= MAX_PENDING_COMMANDS:
		return null
	var request_id: String = load("res://scripts/game/online/game_api.gd").new_request_id()
	var pending := {"done": false, "response": null}
	_pending[request_id] = pending
	var err := _socket.send_text(JSON.stringify({"type": "command", "requestId": request_id, "action": action, "body": body}))
	if err != OK:
		_pending.erase(request_id)
		return null
	var deadline := Time.get_ticks_msec() + COMMAND_TIMEOUT_MS
	while not pending["done"] and Time.get_ticks_msec() < deadline and is_inside_tree():
		await get_tree().process_frame
	_pending.erase(request_id)
	return pending["response"] if pending["done"] else null

func send_quick_chat(emoji: String) -> bool:
	if not connected or _socket == null:
		return false
	return _socket.send_text(JSON.stringify({"type": "QUICK_CHAT_SEND", "emoji": emoji})) == OK

func _release_pending() -> void:
	for id in _pending:
		_pending[id]["done"] = false
	_pending.clear()
