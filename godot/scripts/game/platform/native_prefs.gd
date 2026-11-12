extends RefCounted
## The live app's SharedPreferences files, read and written with the live keys and types
## (LudoPawsPlatform plugin prefsRead / prefsWrite on Android). On desktop and in tests a
## JSON file per name under user://native_prefs/ stands in with the same typed values.
## Types: s String, i Int, l Long, b Boolean, f Float, ss StringSet (live getStringSet).
##
##   var p := NativePrefs.open("ludoproof_wallet")
##   p.get_int("gem_balance", 0)
##   p.edit().put_int("gem_balance", 5).remove("x").commit()

static var _cache := {}        # name -> {key: {t, v}}
## Desktop stand-in folder. Empty = "user://native_prefs" in a normal run and memory only in
## headless runs (automated tests never leave data behind unless they set a folder).
static var desktop_dir := ""

static func _dir() -> String:
	if not desktop_dir.is_empty():
		return desktop_dir
	return "" if DisplayServer.get_name() == "headless" else "user://native_prefs"

var name := ""

static func open(file_name: String):
	var p = load("res://scripts/game/platform/native_prefs.gd").new()
	p.name = file_name
	return p

static func _platform():
	return Engine.get_singleton("LudoPawsPlatform") if Engine.has_singleton("LudoPawsPlatform") else null

static func reset_cache() -> void:
	_cache.clear()

func _data() -> Dictionary:
	if _cache.has(name):
		return _cache[name]
	var text := ""
	var platform = _platform()
	if platform != null:
		text = str(platform.prefsRead(name))
	elif not _dir().is_empty():
		var path := _dir().path_join(name + ".json")
		if FileAccess.file_exists(path):
			text = FileAccess.get_file_as_string(path)
	var parsed = JSON.parse_string(text) if not text.is_empty() else {}
	_cache[name] = parsed if parsed is Dictionary else {}
	return _cache[name]

func contains(key: String) -> bool: return _data().has(key)
func keys() -> Array: return _data().keys()

func _typed(key: String, type: String, fallback):
	var item = _data().get(key)
	if not (item is Dictionary) or item.get("t") != type:
		return fallback
	return item.get("v", fallback)

func get_string(key: String, fallback = null): return _typed(key, "s", fallback)
func get_int(key: String, fallback := 0) -> int: return int(_typed(key, "i", fallback))
func get_long(key: String, fallback := 0) -> int: return int(str(_typed(key, "l", str(fallback))))
func get_bool(key: String, fallback := false) -> bool: return bool(_typed(key, "b", fallback))
func get_float(key: String, fallback := 0.0) -> float: return float(_typed(key, "f", fallback))
func get_string_set(key: String, fallback := []) -> Array: return Array(_typed(key, "ss", fallback))

func edit() -> Editor:
	return Editor.new(self)

## Writes in one commit; the in-memory view changes only when the commit succeeds.
func _commit(ops: Array) -> bool:
	var platform = _platform()
	var ok := false
	if platform != null:
		ok = bool(platform.prefsWrite(name, JSON.stringify(ops)))
	elif _dir().is_empty():
		ok = true
	else:
		var next := _data().duplicate(true)
		_apply(next, ops)
		DirAccess.make_dir_recursive_absolute(_dir())
		var f := FileAccess.open(_dir().path_join(name + ".json"), FileAccess.WRITE)
		ok = f != null and f.store_string(JSON.stringify(next))
		if f != null:
			f.close()
	if ok:
		_apply(_data(), ops)
	else:
		_cache.erase(name)
	return ok

static func _apply(data: Dictionary, ops: Array) -> void:
	for op in ops:
		if op["op"] == "remove":
			data.erase(op["k"])
		else:
			data[op["k"]] = {"t": op["t"], "v": op["v"]}

class Editor:
	var prefs
	var ops: Array = []
	func _init(p) -> void: prefs = p
	func _put(k: String, t: String, v) -> Editor:
		ops.append({"op": "put", "k": k, "t": t, "v": v})
		return self
	func put_string(k: String, v: String) -> Editor: return _put(k, "s", v)
	func put_int(k: String, v: int) -> Editor: return _put(k, "i", v)
	func put_long(k: String, v: int) -> Editor: return _put(k, "l", str(v))
	func put_bool(k: String, v: bool) -> Editor: return _put(k, "b", v)
	func put_float(k: String, v: float) -> Editor: return _put(k, "f", v)
	func put_string_set(k: String, v: Array) -> Editor: return _put(k, "ss", v.map(func(x): return str(x)))
	func remove(k: String) -> Editor:
		ops.append({"op": "remove", "k": k})
		return self
	func commit() -> bool:
		return prefs._commit(ops)
