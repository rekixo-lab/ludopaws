extends RefCounted
## Whether the live online service can be used from this build. The backend URL is never in
## the repo: it lives only in the git-ignored config/release.local.cfg ([backend] base_url).
## Server-backed pages show an honest state instead of sample data until a session exists. See
## docs/PLACEHOLDER_AUDIT.md.

const LOCAL_CONFIG := "res://config/release.local.cfg"

enum State { NOT_CONNECTED, SIGN_IN_REQUIRED, READY }

static func backend_url() -> String:
	var cfg := ConfigFile.new()
	if cfg.load(LOCAL_CONFIG) != OK:
		return ""
	var url := str(cfg.get_value("backend", "base_url", "")).strip_edges()
	return "" if url.is_empty() or url.contains("example.invalid") else url

## A saved live account session (guest or Google) exists on this device.
static func signed_in() -> bool:
	var tree := Engine.get_main_loop() as SceneTree
	var account = tree.root.get_node_or_null("Account") if tree != null else null
	return account != null and account.signed_in()

static func state() -> State:
	if backend_url().is_empty():
		return State.NOT_CONNECTED
	return State.READY if signed_in() else State.SIGN_IN_REQUIRED

## {title, message} for the current state of a server-backed feature.
static func copy(feature: String) -> Dictionary:
	match state():
		State.NOT_CONNECTED:
			return {"title": "Online service not connected", "message": "%s needs the Ludo Paws online service, which is not connected in this build. Your local games still work." % feature}
		State.SIGN_IN_REQUIRED:
			return {"title": "Sign in to continue", "message": "%s needs a signed-in account." % feature}
	return {"title": "", "message": ""}
