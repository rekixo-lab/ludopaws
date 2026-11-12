extends SceneTree
## Manual, read-only check against the configured live backend (never in run_all): GET
## /api/capabilities and /api/store/catalog through the real transport. No data changes.
##   godot --headless --path . --script res://tests/live_readonly_probe.gd
const GameApi = preload("res://scripts/game/online/game_api.gd")
const Transport = preload("res://scripts/game/online/http_transport.gd")
const Online = preload("res://scripts/game/online/online_status.gd")
func _initialize() -> void:
	var url := Online.backend_url()
	if url.is_empty():
		print("LIVE no backend configured")
		quit(0)
		return
	var t := Transport.new()
	root.add_child(t)
	await process_frame
	var api := GameApi.new(url, t)
	print("LIVE start ", url)
	for path in ["/api/capabilities", "/api/store/catalog"]:
		var started := Time.get_ticks_msec()
		var res: Dictionary = await api.request("GET", path)
		var keys := (res.get("data", {}) as Dictionary).keys() if res["ok"] else []
		print("LIVE GET %s -> ok=%s %s keys=%s (%d ms)" % [path, res["ok"], res.get("error", ""), keys.slice(0, 8), Time.get_ticks_msec() - started])
	quit(0)
