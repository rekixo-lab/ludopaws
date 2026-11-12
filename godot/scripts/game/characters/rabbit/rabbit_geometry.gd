@tool
extends RefCounted
## Rabbit anatomy and materials. Port of RabbitGeometry.kt and the colours in RabbitRenderer.kt.

const G = preload("res://scripts/game/board3d/paw_geometry.gd")

const CREAM := Color(0.91, 0.86, 0.76, 1)
const WHITE := Color(1, 0.97, 0.91, 1)
const PINK := Color(0.86, 0.54, 0.57, 1)
const NOSE := Color(0.74, 0.36, 0.42, 1)
const WHISKER := Color(0.46, 0.41, 0.37, 1)

static var _cache := {}

static func mesh(name: String) -> G.Geo:
	if _cache.is_empty():
		_build()
	return _cache[name]

static func _build() -> void:
	_cache["body"] = G.loft([[-0.37, 0.12, 0.17, 0, -0.08], [-0.23, 0.34, 0.39, 0, -0.09], [0.02, 0.39, 0.45, 0, -0.10],
		[0.28, 0.29, 0.30, 0, -0.035], [0.44, 0.16, 0.16]], 24, 3)
	_cache["neck"] = G.loft([[0.26, 0.23, 0.22], [0.42, 0.21, 0.20], [0.58, 0.18, 0.18, 0, 0.035]], 20, 3)
	_cache["head"] = G.loft([[0.44, 0.12, 0.13, 0, 0.10], [0.58, 0.30, 0.25, 0, 0.08], [0.82, 0.38, 0.30, 0, 0.045],
		[1.04, 0.29, 0.24], [1.15, 0.13, 0.12]], 28, 3)
	_cache["ear"] = G.loft([[0.0, 0.075, 0.065], [0.17, 0.10, 0.075], [0.43, 0.115, 0.065, 0.015], [0.64, 0.075, 0.043, 0.035], [0.75, 0.008, 0.012, 0.045]], 20, 3)
	_cache["arm"] = G.loft([[-0.50, 0.055, 0.06], [-0.32, 0.065, 0.075], [-0.10, 0.085, 0.095], [0.03, 0.055, 0.065]], 16, 2)
	_cache["hindPaw"] = G.loft([[-0.065, 0.085, 0.21, 0, 0.04], [-0.035, 0.13, 0.30, 0, 0.055], [0.055, 0.12, 0.26, 0, 0.025], [0.10, 0.07, 0.15]], 20, 3)
	_cache["forePaw"] = G.loft([[-0.055, 0.05, 0.075], [-0.02, 0.075, 0.12, 0, 0.015], [0.05, 0.065, 0.095], [0.08, 0.035, 0.045]], 18, 2)
