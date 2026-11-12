extends Node
## Autoload "Account": the one AccountService for the app plus the live ProfileActivity
## account flows (status line, Save to Google = link, Restore = Google login, guest deletion
## code, account deletion, Play Games). Messages and rules follow live ProfileActivity.kt.
## The Google ID token comes from the LudoPawsPlatform plugin (Credential Manager) and goes
## only to the account server; it is never stored or logged.

signal changed
## Live StartupSessionCoordinator state: CHECKING, GUEST_READY, SAVED_READY, OFFLINE,
## RESTORE_REQUIRED, RETRY_REQUIRED.
signal startup_changed(state: String)

const AccountService = preload("res://scripts/game/account/account_service.gd")
const HttpTransport = preload("res://scripts/game/online/http_transport.gd")
const AgeEntry = preload("res://scripts/game/safety/age_entry.gd")
const LOCAL_CONFIG := "res://config/release.local.cfg"

var service
var server_client_id := ""
var busy := false
var status_text := "Guest account"
var google_linked := false
var play_games := {"connected": false, "name": "", "busy": false}
var startup_state := "CHECKING"
var _startup_busy := false
## Callable(nonce) -> (await) {status, id_token}; the plugin on Android, a fake in tests.
var credential_provider: Callable

func _ready() -> void:
	var cfg := ConfigFile.new()
	cfg.load(LOCAL_CONFIG)
	var url := str(cfg.get_value("backend", "base_url", "")).strip_edges()
	server_client_id = str(cfg.get_value("play", "server_client_id", "")).strip_edges()
	# Headless runs (automated tests) never talk to the live server on their own.
	if url.begins_with("https://") and not url.contains("example.invalid") and DisplayServer.get_name() != "headless":
		var transport := HttpTransport.new()
		add_child(transport)
		service = AccountService.new(url, transport, null, ProjectSettings.get_setting("application/config/version", "2.0.0"))
	credential_provider = _plugin_credential
	var platform = _platform()
	if platform != null:
		platform.connect("play_games", _on_play_games)
	if service != null:
		startup.call_deferred()
	else:
		startup_state = "OFFLINE"

## Live StartupSessionCoordinator.refresh: open (or reopen) this device's game account at
## launch; offline play never waits for it.
func startup() -> void:
	if _startup_busy or service == null:
		return
	_startup_busy = true
	_publish_startup("CHECKING")
	var res: Dictionary = await service.ensure_guest_session()
	_startup_busy = false
	if res["ok"]:
		var s: Dictionary = res["session"]
		_publish_startup("RESTORE_REQUIRED" if s.get("identityRecoveryPending", false) else ("GUEST_READY" if s.get("guestRecoveryEligible", false) else "SAVED_READY"))
	elif str(res.get("error", "")) in ["ACCOUNT_SESSION_REQUIRED", "REFRESH_TOKEN_INVALID"]:
		_publish_startup("RESTORE_REQUIRED")
	elif int(res.get("status", 0)) == 0:
		_publish_startup("OFFLINE")
	else:
		_publish_startup("RETRY_REQUIRED")

func _publish_startup(state: String) -> void:
	startup_state = state
	startup_changed.emit(state)

## HomeActivity.updateStartupBanner text ("" = hidden).
static func startup_banner(state: String) -> String:
	match state:
		"RESTORE_REQUIRED": return "Restore your saved account to reconnect · Tap here"
		"CHECKING": return "Connecting — offline play available"
		"OFFLINE": return "Offline play available · Tap to retry"
		"RETRY_REQUIRED": return "Couldn't connect · Tap to retry"
	return ""

static func _platform():
	return Engine.get_singleton("LudoPawsPlatform") if Engine.has_singleton("LudoPawsPlatform") else null

func available() -> bool:
	return service != null

func signed_in() -> bool:
	return service != null and service.signed_in()

func session() -> Dictionary:
	return service.current_session() if service != null else {}

## Live isAccountSignInConfigured.
func sign_in_configured() -> bool:
	return service != null and not server_client_id.is_empty()

func google_allowed() -> bool:
	return AgeEntry.new().optional_google_allowed()

func _set_status(text: String) -> void:
	status_text = text.left(100)
	changed.emit()

## Live refreshAccountStatus.
func refresh_status() -> void:
	if busy:
		return
	if service == null:
		_set_status("Offline · progress saved on this device")
		return
	if not signed_in():
		# Family mode: no session until online play creates a private game account.
		_set_status("Offline profile · online play creates a private game account")
		return
	busy = true
	var owner: String = session().get("accountId", "")
	var res: Dictionary = await service.ensure_guest_session()
	var links: Dictionary = await service.linked_identities() if res["ok"] else res
	busy = false
	if owner != session().get("accountId", ""):
		return
	if not res["ok"] or not links["ok"]:
		_set_status("Account needs a connection")
		return
	google_linked = bool(links["data"].get("googleLinked", false))
	if service.requires_identity_recovery():
		_set_status("Account restore needs retry")
	elif google_linked:
		_set_status("Saved to Google · %s" % res["session"].get("displayName", "Player"))
	else:
		_set_status("Guest · save to keep progress")

## Live startGoogleAccountFlow + launchGoogleCredentialPicker. Returns {ok, title, message}.
func google_flow(link_current: bool) -> Dictionary:
	if busy:
		return {"ok": false, "title": "", "message": ""}
	if not sign_in_configured():
		return {"ok": false, "title": "Google account setup required", "message": "Google account sign-in has not been configured for this build. The game can still be played as a guest, but account linking is not available yet."}
	busy = true
	_set_status("Preparing secure sign-in…")
	var result := await _google_flow(link_current)
	busy = false
	_set_status(result["status"] if result["ok"] else result["message"])
	if result["ok"]:
		refresh_status.call_deferred()
		startup.call_deferred()
	return result

func _google_flow(link_current: bool) -> Dictionary:
	if link_current:
		var g: Dictionary = await service.ensure_guest_session()
		if not g["ok"]:
			return _fail(g)
	var challenge: Dictionary = await service.google_challenge()
	if not challenge["ok"]:
		return _fail(challenge)
	var nonce := str(challenge["data"]["nonce"])
	var credential: Dictionary = await credential_provider.call(nonce)
	match str(credential.get("status", "FAILED")):
		"OK":
			pass
		"CANCELLED":
			return _failed("Google sign-in was cancelled. No account data was changed.")
		"NO_CREDENTIAL":
			return _failed("Google could not provide an account. Check your phone's Google account and Google Play services, then try again.")
		"UNSUPPORTED":
			return _failed("Google did not return a supported sign-in credential.")
		"INVALID":
			return _failed("Google sign-in returned an invalid credential. Please try again.")
		_:
			return _failed("Google sign-in could not be completed. Please try again.")
	var token := str(credential.get("id_token", ""))
	if link_current:
		var linked: Dictionary = await service.link_google_identity(token, nonce)
		if not linked["ok"]:
			return _fail(linked)
		# Live also links the legacy Friend / leaderboard identities here (B6 stores).
		return {"ok": true, "title": "Account saved", "status": "Saved to Google · %s" % session().get("displayName", "Player"),
			"message": "Your current guest account is now linked to Google. Its identity was kept; another existing account was not merged."}
	# Live: scope the current local profile to the current account before the session is
	# replaced; another account must not inherit its stats.
	load("res://scripts/game/profile/profile_store.gd").prepare_for_account_restore()
	var restored: Dictionary = await service.restore_google_account(token, nonce)
	if not restored["ok"]:
		return _fail(restored)
	var recovered: Dictionary = await service.recover_linked_identities()
	if recovered["ok"] and recovered["data"].get("friend") == null and recovered["data"].get("leaderboard") == null:
		# Nothing to restore locally: live clears both stores and completes the recovery.
		service.complete_identity_recovery()
	# A Friend / leaderboard credential to restore keeps the recovery pending until the
	# Friend and leaderboard identity stores exist (B6): live's "needs retry" state.
	return {"ok": true, "title": "Account restored", "status": "Account restored · %s" % session().get("displayName", "Player"),
		"message": "Your saved account is active on this device. If this device had a different guest account, its data has not been merged automatically."}

func _failed(message: String) -> Dictionary:
	return {"ok": false, "title": "Ludo Paws Account", "message": message}

## Live error mapping for account operations.
func _fail(res: Dictionary) -> Dictionary:
	var message := str(res.get("message", ""))
	match str(res.get("error", "")):
		"GOOGLE_IDENTITY_ALREADY_LINKED":
			message = "This Google account is already linked to a different Ludo Paws account. Choose Restore instead; the accounts will not be merged automatically."
		"GOOGLE_IDENTITY_NOT_LINKED":
			message = "No saved Ludo Paws account is linked to that Google account yet. Choose Save this account to Google to secure the current guest."
		"ACCOUNT_IDENTITY_CONFLICT":
			message = "This account has a different Friend or leaderboard identity. To protect both profiles, they were not merged."
	if message.is_empty():
		message = "The account service is not available right now."
	return _failed(message)

func _plugin_credential(nonce: String) -> Dictionary:
	var platform = _platform()
	if platform == null:
		return {"status": "UNSUPPORTED"}
	platform.googleSignIn(server_client_id, nonce)
	var args: Array = await platform.google_credential
	return {"status": str(args[0]), "id_token": str(args[1])}

## Live showGuestDeletionCode. Returns {ok, code | message}.
func guest_deletion_code(rotate := false) -> Dictionary:
	if busy or service == null:
		return {"ok": false, "message": "Connect to the internet and try again. The guest account has not been deleted."}
	busy = true
	_set_status("Preparing guest deletion code…")
	var res: Dictionary = await service.guest_deletion_code(rotate)
	busy = false
	_set_status("Guest deletion code ready" if res["ok"] else "Guest deletion code unavailable")
	if not res["ok"]:
		res["message"] = str(res.get("message", "Connect to the internet and try again. The guest account has not been deleted."))
	return res

## Live performAccountDeletion (server, then local per-account cleanup).
func delete_account() -> Dictionary:
	if busy or service == null:
		return {"ok": false, "message": "The account service could not complete deletion. Your account session has been kept so you can try again."}
	busy = true
	_set_status("Deleting account…")
	var res: Dictionary = await service.delete_current_account()
	busy = false
	if not res["ok"]:
		_set_status("Account deletion not completed")
		return res
	google_linked = false
	# Live performAccountDeletion local cleanup. Remaining stores (profile, pending Play
	# purchases, Friend / leaderboard identities) are added as they are ported (B4, B6, B7).
	var owner := str(res.get("accountId", ""))
	var cleanup: Array = []
	if not load("res://scripts/game/profile/profile_store.gd").clear_for_account_deletion(owner):
		cleanup.append("profile")
	var Inventory = load("res://scripts/game/store/cosmetic_inventory.gd")
	if not Inventory.new(func() -> String: return owner).clear_account_data(owner):
		cleanup.append("wallet cache")
	load("res://scripts/game/store/play_billing.gd").new().pending_remove_account(str(res.get("billingAccountId", "")))
	load("res://scripts/game/ads/ads_service.gd").remove_account(owner)
	res["cleanupErrors"] = cleanup
	_set_status("Account deleted" if cleanup.is_empty() else "Account deleted; local cleanup needs attention")
	return res

# ---------------------------------------------------------------- Play Games (StartupPlayGames)

func play_games_configured() -> bool:
	var platform = _platform()
	return platform != null and platform.playGamesConfigured()

func refresh_play_games() -> void:
	var platform = _platform()
	if platform == null or not google_allowed() or not platform.playGamesInitialized():
		play_games["connected"] = false
		changed.emit()
		return
	platform.playGamesStatus()

## Explicit Profile sign-in (live startPlayGamesSignIn). Returns a status message.
func play_games_sign_in() -> String:
	var platform = _platform()
	if platform == null or not platform.playGamesConfigured():
		return "Google Play Games is not set up for this build."
	if not google_allowed():
		return "Google account features are unavailable for this privacy setting. You can continue playing."
	if play_games["connected"]:
		return "Signed in as %s" % play_games["name"] if not str(play_games["name"]).is_empty() else "Google Play Games connected"
	if not platform.playGamesInitialize():
		return "Google Play Games setup is required on this device."
	play_games["busy"] = true
	changed.emit()
	platform.playGamesSignIn()
	return "Opening Google Play Games…"

func _on_play_games(action: String, ok: bool, display_name: String) -> void:
	play_games["busy"] = false
	play_games["connected"] = ok and google_allowed()
	play_games["name"] = display_name if ok else ""
	changed.emit()
