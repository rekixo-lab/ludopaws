extends Node
## Autoload "DevHooks": in debug builds that contain the development perf probe, start it
## (it stays inert without its one-shot flag file). Release builds exclude scripts/debug/,
## so nothing debug-only is loaded or run there.

func _ready() -> void:
	if OS.is_debug_build() and ResourceLoader.exists("res://scripts/debug/perf_probe.gd"):
		var probe: Node = load("res://scripts/debug/perf_probe.gd").new()
		probe.name = "PerfProbe"
		get_tree().root.add_child.call_deferred(probe)
