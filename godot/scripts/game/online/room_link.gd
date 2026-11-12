extends RefCounted
## Live RoomLink / GameShareLink: private-room links are navigation hints only (the server's
## invite authorization stays mandatory). https://ludopaws.rekixo.com/play/friends/{room}
## [?host=LPF-…]; accepted origins: https on ludopaws.rekixo.com / api.ludopaws.rekixo.com,
## or ludopaws://api.ludopaws.rekixo.com.

const PUBLIC_SHARE_HOST := "ludopaws.rekixo.com"
const LEGACY_SHARE_HOST := "api.ludopaws.rekixo.com"
const GAME_SHARE_URL := "https://ludopaws.rekixo.com/share"
const ROOM := "^LP[A-Z2-9]{8}$"
const FRIEND := "^LPF-[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}$"

static func _ok(pattern: String, v: String) -> bool:
	return RegEx.create_from_string(pattern).search(v) != null

## {room, mode, host} or {}.
static func parse(uri: String) -> Dictionary:
	if uri.is_empty() or uri.length() > 512 or uri.contains("#"):
		return {}
	var rx := RegEx.create_from_string("^([a-z]+)://([^/?#@:]+)(/[^?#]*)?(\\\\?[^#]*)?$")
	var m := rx.search(uri)
	if m == null:
		return {}
	var scheme := m.get_string(1)
	var host := m.get_string(2)
	var valid := (scheme == "https" and host in [PUBLIC_SHARE_HOST, LEGACY_SHARE_HOST]) or (scheme == "ludopaws" and host == LEGACY_SHARE_HOST)
	if not valid:
		return {}
	var parts := m.get_string(3).split("/", false)
	if parts.size() != 3 or parts[0] != "play" or parts[1] != "friends" or not _ok(ROOM, parts[2]):
		return {}
	var friend_host = null
	for pair in m.get_string(4).trim_prefix("?").split("&", false):
		var kv := pair.split("=")
		if kv[0] == "host" and kv.size() == 2:
			friend_host = kv[1].uri_decode()
	if friend_host != null and not _ok(FRIEND, friend_host):
		return {}
	return {"room": parts[2], "mode": "FRIENDS", "host": friend_host}

static func url(room: String, host = null) -> String:
	return "https://%s/play/friends/%s%s" % [PUBLIC_SHARE_HOST, room, ("?host=" + str(host)) if host != null else ""]

static func share_message(room: String, host = null) -> String:
	var t := "Join my private Ludo Paws room: %s\n" % room
	if host != null:
		t += "Host Friend ID: %s\n" % host
	t += "After opening the game, go to Friends and ask the host to send you the room invite."
	if host != null:
		t += " New friends can add the host using the Friend ID above."
	return t + "\n\nGet Ludo Paws on Google Play:\n" + url(room, host)

static func game_share_text() -> String:
	return "Want to play Ludo Paws with me?\n" + GAME_SHARE_URL

## Share sheet on Android; the clipboard elsewhere.
static func share(text: String, title := "Share Ludo Paws") -> void:
	if Engine.has_singleton("LudoPawsPlatform"):
		Engine.get_singleton("LudoPawsPlatform").shareText("Ludo Paws", text, title)
	else:
		DisplayServer.clipboard_set(text)

static func take_launch_link() -> Dictionary:
	if not Engine.has_singleton("LudoPawsPlatform"):
		return {}
	return parse(str(Engine.get_singleton("LudoPawsPlatform").takeLaunchUri()))
