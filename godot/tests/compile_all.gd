extends SceneTree
## Loads every script under scripts/ and tests/ so a parse error in a file nothing has
## loaded yet still fails the suite (the import check only compiles what is referenced).
##   godot --headless --path . --script res://tests/compile_all.gd
var failures := 0
func _scan(dir: String) -> void:
	for f in DirAccess.get_files_at(dir):
		if f.ends_with(".gd"):
			var s = load(dir.path_join(f))
			if s == null or not (s is GDScript) or not s.can_instantiate():
				failures += 1
				printerr("FAIL: ", dir.path_join(f))
	for d in DirAccess.get_directories_at(dir):
		_scan(dir.path_join(d))
func _initialize() -> void:
	_scan("res://scripts")
	_scan("res://tests")
	print("Compile all: %d failures" % failures)
	quit(1 if failures > 0 else 0)
