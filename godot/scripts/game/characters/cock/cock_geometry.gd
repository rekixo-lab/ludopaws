@tool
extends RefCounted
## Cock anatomy and materials. Port of CockGeometry.kt and the colours in CockRenderer.kt.

const G = preload("res://scripts/game/board3d/paw_geometry.gd")

const RED := Color(0.83, 0.09, 0.08, 1)
const GOLD := Color(1, 0.64, 0.12, 1)
const COAT := Color(0.65, 0.22, 0.09, 1)
const TAIL := Color(0.025, 0.28, 0.23, 1)
const TEAL := Color(0.04, 0.46, 0.37, 1)

static var _cache := {}

static func mesh(name: String) -> G.Geo:
	if _cache.is_empty():
		_build()
	return _cache[name]

static func _build() -> void:
	_cache["body"] = G.loft([[-0.43, 0.10, 0.14], [-0.30, 0.34, 0.36, 0, -0.04], [-0.06, 0.46, 0.47, 0, -0.08],
		[0.19, 0.38, 0.36], [0.36, 0.22, 0.20]], 24, 3)
	_cache["neck"] = G.loft([[0.19, 0.30, 0.29], [0.39, 0.26, 0.24], [0.62, 0.18, 0.17, 0, 0.015], [0.79, 0.15, 0.15]], 20, 3)
	_cache["head"] = G.loft([[0.65, 0.10, 0.12], [0.77, 0.25, 0.24, 0, 0.02], [0.98, 0.31, 0.28], [1.15, 0.23, 0.22], [1.21, 0.06, 0.07]], 24, 3)
	_cache["wing"] = G.loft([[-0.45, 0.015, 0.015, 0, -0.15], [-0.30, 0.15, 0.07, 0, -0.08], [-0.10, 0.18, 0.095], [0.04, 0.06, 0.04]], 18, 3)
	_cache["feather"] = G.loft([[0.0, 0.035, 0.025], [0.18, 0.10, 0.04, 0, -0.02], [0.43, 0.085, 0.035, 0, -0.12], [0.64, 0.015, 0.012, 0, -0.25]], 16, 3)
	_cache["beak"] = G.loft([[0.0, 0.12, 0.07], [0.11, 0.09, 0.055], [0.24, 0.005, 0.005]], 16, 2)
