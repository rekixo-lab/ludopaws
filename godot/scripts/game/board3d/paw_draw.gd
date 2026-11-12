@tool
extends RefCounted
## CharacterDrawContext equivalents for Godot.
##
## Recorder bakes immutable artwork into merged meshes (live StaticHouseRecorder).
## Pool redraws animated parts each frame by reusing MeshInstance3D nodes.
## Both take the same drawPart/drawGeometry arguments as the live renderer.

const G = preload("res://scripts/game/board3d/paw_geometry.gd")
const OPAQUE_SHADER = preload("res://scripts/game/board3d/paw_parts.gdshader")
const ALPHA_SHADER = preload("res://scripts/game/board3d/paw_parts_alpha.gdshader")
const SELF = preload("res://scripts/game/board3d/paw_draw.gd")

## CharacterSurface: [kind, gloss]
const SURFACES := {
	"MATTE": [0.0, 0.055], "SOFT_FUR": [6.0, 0.025], "COAT": [1.0, 0.045], "BODY_COAT": [4.0, 0.045],
	"CAT_COAT": [5.0, 0.035], "HORN": [2.0, 0.11], "HOOF": [3.0, 0.13], "EYE": [0.0, 0.38], "NOSE": [0.0, 0.17],
	"HOUSE_WALL": [7.0, 0.025], "HOUSE_ROOF": [8.0, 0.055],
	"LUMI_FUR": [15.0, 0.035], "LUMI_GLOSS": [16.0, 0.15], "LUMI_EYE": [17.0, 0.45],
	"FINISH_METAL": [12.0, 0.65], "PARTY_BULB": [13.0, 0.85], "PARTY_GLOW": [14.0, 0.0],
	"GARDEN_GROUND": [9.0, 0.015], "GARDEN_STONE": [10.0, 0.045], "GARDEN_LEAF": [11.0, 0.08],
	"BOARD_WATER": [0.0, 0.10],
	"ELEPHANT_HIDE": [18.0, 0.055], "ELEPHANT_TRUNK": [19.0, 0.055], "DONKEY_FUR": [20.0, 0.035],
	"GOAT_FLEECE": [21.0, 0.025], "PIG_SKIN": [22.0, 0.085], "HORSE_COAT": [23.0, 0.14],
	"HORSE_FACE": [24.0, 0.095], "GOAT_HORN": [25.0, 0.12],
}

## Per-layer shader state that the live renderer set with scissor/uniforms.
class Layer:
	var clip_rect := Vector4.ZERO
	var brightness := 1.0

static var _materials := {}
static var _simple: Shader

## DEV probe only: flat colour, to isolate fragment cost on device.
static func _simple_shader() -> Shader:
	if _simple == null:
		_simple = Shader.new()
		_simple.code = "shader_type spatial;\nrender_mode unshaded, cull_disabled;\nuniform vec4 color = vec4(1.0);\nvoid fragment() { ALBEDO = color.rgb; }\n"
	return _simple

## Per-frame fast path: layer instance -> surface -> colour -> material (no string keys; the
## formatted key cost ~40 % of the pawn draw on device).
static var _fast := {}

static func material(color: Color, surface: String, layer: Layer = null) -> ShaderMaterial:
	var by_surface: Dictionary = _fast.get_or_add(layer, {})
	var by_color: Dictionary = by_surface.get_or_add(surface, {})
	var found = by_color.get(color)
	if found != null:
		return found
	var m := _material_slow(color, surface, layer)
	by_color[color] = m
	return m

static func _material_slow(color: Color, surface: String, layer: Layer = null) -> ShaderMaterial:
	var clip := layer.clip_rect if layer != null else Vector4.ZERO
	var bright := layer.brightness if layer != null else 1.0
	var key := "%s|%s|%s|%s" % [color.to_html(true), surface, clip, bright]
	if _materials.has(key):
		return _materials[key]
	var m := ShaderMaterial.new()
	_configure(m, color, surface, clip, bright)
	_materials[key] = m
	return m

static func _configure(m: ShaderMaterial, color: Color, surface: String, clip: Vector4, bright: float, party := 0.0, bend := Vector2.ZERO) -> void:
	var transparent := color.a < 1.0 or surface == "PARTY_GLOW"
	var shader: Shader = ALPHA_SHADER if transparent else OPAQUE_SHADER
	if preload("res://scripts/game/perf.gd").flag("simple_shader"):
		shader = _simple_shader()
	if m.shader != shader:
		m.shader = shader
	var spec: Array = SURFACES.get(surface, SURFACES["MATTE"])
	m.set_shader_parameter("color", Vector4(color.r, color.g, color.b, color.a))
	m.set_shader_parameter("kind", spec[0])
	m.set_shader_parameter("gloss", spec[1])
	m.set_shader_parameter("clip_rect", clip)
	m.set_shader_parameter("brightness", bright)
	m.set_shader_parameter("party_energy", party)
	m.set_shader_parameter("limb_bend", bend)

# ---------------------------------------------------------------- baking

class Batch:
	var geo: G.Geo
	var color: Color
	var surface: String

class Recorder:
	var surface := "MATTE"
	var _opaque := {}      # key -> Batch (insertion ordered)
	var _transparent: Array = []

	func draw_part(parent: Transform3D, t: Vector3, s: Vector3, color: Color, r := Vector3.ZERO) -> void:
		draw_geometry(parent, G.sphere(), t, s, color, r)

	func draw_geometry(parent: Transform3D, geo: G.Geo, t: Vector3, s: Vector3, color: Color, r := Vector3.ZERO) -> void:
		var m := G.part_transform(parent, t, s, r)
		var normal_basis := m.basis.inverse().transposed()
		var batch: Batch
		if color.a < 1.0:
			batch = Batch.new()
			batch.geo = G.Geo.new()
			batch.color = color
			batch.surface = surface
			_transparent.append(batch)
		else:
			var key := "%s|%s" % [color.to_html(true), surface]
			if not _opaque.has(key):
				batch = Batch.new()
				batch.geo = G.Geo.new()
				batch.color = color
				batch.surface = surface
				_opaque[key] = batch
			batch = _opaque[key]
		var out := batch.geo
		var offset := out.v.size()
		var locals := geo.l if geo.l.size() == geo.v.size() else geo.v
		for k in geo.v.size():
			out.v.append(m * geo.v[k])
			out.n.append((normal_basis * geo.n[k]).normalized())
			out.l.append(locals[k])
		for index in geo.i:
			out.i.append(index + offset)

	func batches() -> Array:
		var out: Array = _opaque.values()
		out.append_array(_transparent)
		return out

## Builds one MeshInstance3D-ready ArrayMesh (one surface per batch).
static func bake(batches: Array, layer: Layer = null) -> ArrayMesh:
	var mesh := ArrayMesh.new()
	for b in batches:
		var batch: Batch = b
		if batch.geo.v.is_empty():
			continue
		batch.geo.to_mesh(material(batch.color, batch.surface, layer), mesh)
	return mesh

# ---------------------------------------------------------------- per-frame parts

class Pool:
	var surface := "MATTE"
	var party_energy := 0.0
	var limb_bend := Vector2.ZERO
	var layer: Layer
	## Extra depth (pixel-space +Z) for the parts drawn next. With the orthographic camera this
	## is the live renderer's per-animal depth clear: a later pawn is never cut by an earlier one.
	var depth_offset := 0.0
	## CharacterDrawContext state used by the animal renderers.
	var life = null
	var head := Transform3D.IDENTITY
	var _root: Node3D
	var _nodes: Array[MeshInstance3D] = []
	var _node_mesh: Array = []
	var _node_mat: Array = []
	var _node_visible: Array[bool] = []
	var _node_own := {}
	var _used := 0
	static var _meshes := {}  # G.Geo -> ArrayMesh

	func _init(root: Node3D, pool_layer: Layer = null) -> void:
		_root = root
		layer = pool_layer if pool_layer != null else Layer.new()

	func begin() -> void:
		_used = 0
		depth_offset = 0.0

	func end() -> void:
		for k in range(_used, _nodes.size()):
			if _node_visible[k]:
				_nodes[k].visible = false
				_node_visible[k] = false

	func draw_part(parent: Transform3D, t: Vector3, s: Vector3, color: Color, r := Vector3.ZERO) -> void:
		draw_geometry(parent, G.sphere(), t, s, color, r)

	static var bench_mode := 0  # DEV bench: 1 = logic only, 2 = + transform math
	func draw_geometry(parent: Transform3D, geo: G.Geo, t: Vector3, s: Vector3, color: Color, r := Vector3.ZERO) -> void:
		if bench_mode == 1:
			_used += 1
			return
		if bench_mode == 2:
			_used += 1
			var mm := G.part_transform(parent, t, s, r)
			return
		if bench_mode == 3:
			_used += 1
			var mm := G.part_transform(parent, t, s, r)
			var shared := SELF.material(color, surface, layer)
			return
		var node: MeshInstance3D
		var index := _used
		if index < _nodes.size():
			node = _nodes[index]
		else:
			node = MeshInstance3D.new()
			node.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
			_root.add_child(node)
			_nodes.append(node)
			_node_mesh.append(null)
			_node_mat.append(null)
			_node_visible.append(true)
		_used += 1
		# Engine property writes only when something changed (script-side mirrors are cheaper
		# than reading node properties back every frame).
		var mesh: ArrayMesh = _meshes.get(geo)
		if mesh == null:
			mesh = geo.to_mesh()
			_meshes[geo] = mesh
		if _node_mesh[index] != mesh:
			node.mesh = mesh
			_node_mesh[index] = mesh
		var m := G.part_transform(parent, t, s, r)
		if depth_offset != 0.0:
			m.origin.z += depth_offset
		node.transform = m
		if not _node_visible[index]:
			node.visible = true
			_node_visible[index] = true
		if party_energy == 0.0 and limb_bend == Vector2.ZERO:
			# Most parts: one shared material per colour/surface/layer, no per-frame uniform writes.
			var shared := SELF.material(color, surface, layer)
			if _node_mat[index] != shared:
				node.material_override = shared
				_node_mat[index] = shared
		else:
			# Animated uniforms: the node keeps one own material, reused every frame.
			var own = _node_own.get(index)
			if own == null:
				own = ShaderMaterial.new()
				_node_own[index] = own
			if _node_mat[index] != own:
				node.material_override = own
				_node_mat[index] = own
			SELF._configure(own, color, surface, layer.clip_rect, layer.brightness, party_energy, limb_bend)

	# ------------------------------------------------------------ CharacterDrawContext helpers

	## CharacterDrawContext.prepareHead: tilt/pitch/gaze about a neck pivot.
	func prepare_head(parent: Transform3D, center_y: float, tilt: float) -> void:
		var pivot_y := center_y - 0.38
		var m := G.translate(parent, 0, pivot_y, 0)
		var lt: float = life.head_tilt if life != null else 0.0
		var sh: float = life.shake if life != null else 0.0
		m = G.rotate(m, tilt + lt + sh, 0, 0, 1)
		m = G.rotate(m, life.head_pitch if life != null else 0.0, 1, 0, 0)
		m = G.rotate(m, (life.gaze_x if life != null else 0.0) * 180.0, 0, 1, 0)
		head = G.translate(m, 0, -pivot_y, 0)

	func draw_living_eye(parent: Transform3D, x: float, y: float, z: float, sx: float, sy: float,
			skin: Color, iris: Color, pupil: Color, horizontal: bool, slit: bool) -> void:
		var openness: float = life.blink if life != null else 1.0
		var gx: float = life.gaze_x if life != null else 0.0
		var gy: float = (life.gaze_y if life != null else 0.0) * openness
		draw_part(parent, Vector3(x, y, z - 0.009), Vector3(sx * 1.14, sy * 1.09, 0.075), skin)
		draw_part(parent, Vector3(x, y, z), Vector3(sx, sy * openness, 0.075), iris)
		var px := sx * 0.65 if horizontal else (sx * 0.27 if slit else sx * 0.64)
		draw_part(parent, Vector3(x + gx, y + gy, z + 0.067), Vector3(px, sy * openness * (0.28 if horizontal else 0.77), 0.025), pupil)
		if openness > 0.25:
			draw_part(parent, Vector3(x + gx - 0.025, y + gy + 0.04 * openness, z + 0.091),
				Vector3(0.021, 0.026 * openness, 0.012), Color(1, 1, 1, 1))

	func draw_tail(parent: Transform3D, wag: float, coat: Color, tip: Color, length: float) -> void:
		var x := 0.49
		var y := -0.23
		for segment in 5:
			var degrees := -48.0 + wag * (1.0 + segment * 0.10) + segment * 16.0
			var angle := deg_to_rad(degrees)
			var dx := -sin(angle) * length / 5.0
			var dy := cos(angle) * length / 5.0
			var radius := 0.115 - segment * 0.015
			draw_part(parent, Vector3(x + dx * 0.5, y + dy * 0.5, -0.33), Vector3(radius, length / 8.0, radius),
				tip if segment >= 4 else coat, Vector3(0, 0, degrees))
			x += dx
			y += dy
