extends Node3D
## Ludo Paws v2: runnable *visual-only* starting scene for Codex.
## It does NOT implement turns, fair dice, Ludo rules or network gameplay.
## The dice button is explicitly labeled as a NON-AUTHORITATIVE preview.

const GRID_SIZE: int = 15
const BOARD_HALF: float = 7.0

var _materials: Dictionary = {}
var _dice_text: Label


func _ready() -> void:
    randomize()
    _build_environment()
    _build_board()
    _build_cat(Vector3(-5.0, 0.0, -5.0))
    _build_panda(Vector3(5.0, 0.0, 5.0))
    _build_hud()


func _build_environment() -> void:
    var camera := Camera3D.new()
    camera.name = "ComparisonCamera"
    camera.projection = Camera3D.PROJECTION_ORTHOGONAL
    camera.size = 31.0
    camera.position = Vector3(0.0, 23.0, 24.0)
    add_child(camera)
    camera.look_at(Vector3.ZERO, Vector3.UP)
    camera.current = true

    var sunlight := DirectionalLight3D.new()
    sunlight.name = "SoftKeyLight"
    sunlight.rotation_degrees = Vector3(-55.0, -25.0, 0.0)
    sunlight.light_energy = 1.6
    sunlight.shadow_enabled = true
    add_child(sunlight)

    var fill_light := OmniLight3D.new()
    fill_light.name = "SoftFillLight"
    fill_light.position = Vector3(-5.0, 9.0, 2.0)
    fill_light.light_energy = 0.5
    fill_light.omni_range = 30.0
    add_child(fill_light)

    var world := WorldEnvironment.new()
    var settings := Environment.new()
    settings.background_mode = Environment.BG_COLOR
    settings.background_color = Color("#17233b")
    settings.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
    settings.ambient_light_color = Color("#bdd4ee")
    settings.ambient_light_energy = 0.7
    world.environment = settings
    add_child(world)


func _build_board() -> void:
    _make_box(self, Vector3(0.0, -0.18, 0.0), Vector3(15.5, 0.32, 15.5), Color("#30394d"))

    for row in range(GRID_SIZE):
        for col in range(GRID_SIZE):
            var center := Vector3(float(col) - BOARD_HALF, 0.04, float(row) - BOARD_HALF)
            _make_box(self, center, Vector3(0.96, 0.12, 0.96), _tile_color(row, col))

    # Simple visual markers are deliberately not authoritative pawns or path data.
    for spot in [
        Vector3(-5.65, 0.15, -5.65),
        Vector3(-4.35, 0.15, -5.65),
        Vector3(-5.65, 0.15, -4.35),
        Vector3(-4.35, 0.15, -4.35),
    ]:
        _make_ball(self, spot, Vector3(0.35, 0.11, 0.35), Color("#ffc9b7"))
    for spot in [
        Vector3(4.35, 0.15, 4.35),
        Vector3(5.65, 0.15, 4.35),
        Vector3(4.35, 0.15, 5.65),
        Vector3(5.65, 0.15, 5.65),
    ]:
        _make_ball(self, spot, Vector3(0.35, 0.11, 0.35), Color("#fff4b0"))


func _tile_color(row: int, col: int) -> Color:
    if row < 6 and col < 6:
        return Color("#ea7e76") if row in [0, 5] or col in [0, 5] else Color("#fbc5bd")
    if row < 6 and col >= 9:
        return Color("#80bfa4") if row in [0, 5] or col in [9, 14] else Color("#cee9d9")
    if row >= 9 and col < 6:
        return Color("#8ba7dc") if row in [9, 14] or col in [0, 5] else Color("#d0dcf3")
    if row >= 9 and col >= 9:
        return Color("#efc463") if row in [9, 14] or col in [9, 14] else Color("#ffedb7")
    if row in range(6, 9) and col in range(6, 9):
        return Color("#e7d9c6")
    if row == 7 and col in range(1, 6):
        return Color("#f19b91")
    if row == 7 and col in range(9, 14):
        return Color("#ffe08c")
    if col == 7 and row in range(1, 6):
        return Color("#a2dcc1")
    if col == 7 and row in range(9, 14):
        return Color("#a8c0ee")
    if (row + col) % 5 == 0:
        return Color("#d8edfb")
    return Color("#f5f5f0")


func _build_cat(location: Vector3) -> void:
    var cat := Node3D.new()
    cat.name = "CatPreview"
    cat.position = location
    add_child(cat)

    _make_ball(cat, Vector3(0.0, 0.58, 0.0), Vector3(0.95, 0.78, 0.78), Color("#db905f"))
    _make_ball(cat, Vector3(0.0, 1.14, 0.09), Vector3(0.75, 0.69, 0.69), Color("#f2b17e"))
    _make_ball(cat, Vector3(-0.25, 1.52, 0.02), Vector3(0.25, 0.35, 0.23), Color("#d88761"))
    _make_ball(cat, Vector3(0.25, 1.52, 0.02), Vector3(0.25, 0.35, 0.23), Color("#d88761"))
    _make_ball(cat, Vector3(-0.16, 1.22, 0.39), Vector3(0.10, 0.13, 0.05), Color("#15263b"))
    _make_ball(cat, Vector3(0.16, 1.22, 0.39), Vector3(0.10, 0.13, 0.05), Color("#15263b"))
    _make_ball(cat, Vector3(0.0, 1.05, 0.44), Vector3(0.12, 0.09, 0.06), Color("#bd5771"))
    for x in [-0.30, 0.30]:
        for z in [-0.25, 0.27]:
            _make_ball(cat, Vector3(x, 0.25, z), Vector3(0.26, 0.35, 0.25), Color("#e4a77c"))
    _make_ball(cat, Vector3(0.45, 0.62, -0.26), Vector3(0.23, 0.23, 0.70), Color("#bc754c"))


func _build_panda(location: Vector3) -> void:
    var panda := Node3D.new()
    panda.name = "PandaPreview"
    panda.position = location
    add_child(panda)

    _make_ball(panda, Vector3(0.0, 0.62, 0.0), Vector3(1.06, 0.95, 0.92), Color("#f5f5ef"))
    _make_ball(panda, Vector3(0.0, 1.31, 0.07), Vector3(0.88, 0.79, 0.77), Color("#f8f8f2"))
    for x in [-0.34, 0.34]:
        _make_ball(panda, Vector3(x, 1.69, 0.03), Vector3(0.33, 0.35, 0.29), Color("#252c39"))
        _make_ball(panda, Vector3(x * 0.60, 1.37, 0.48), Vector3(0.28, 0.32, 0.11), Color("#2a2d3a"))
        _make_ball(panda, Vector3(x * 0.62, 1.39, 0.57), Vector3(0.11, 0.11, 0.06), Color("#f7f8f7"))
        _make_ball(panda, Vector3(x * 0.64, 1.39, 0.61), Vector3(0.06, 0.07, 0.03), Color("#1a2431"))
    _make_ball(panda, Vector3(0.0, 1.14, 0.53), Vector3(0.14, 0.11, 0.10), Color("#2c2d3d"))
    for x in [-0.46, 0.46]:
        _make_ball(panda, Vector3(x, 0.67, 0.01), Vector3(0.29, 0.58, 0.39), Color("#282c39"))
        _make_ball(panda, Vector3(x * 0.7, 0.26, 0.30), Vector3(0.31, 0.27, 0.39), Color("#292c38"))


func _make_box(parent: Node3D, location: Vector3, size: Vector3, tint: Color) -> void:
    var mesh := BoxMesh.new()
    mesh.size = size
    var shape := MeshInstance3D.new()
    shape.mesh = mesh
    shape.material_override = _material(tint)
    shape.position = location
    parent.add_child(shape)


func _make_ball(parent: Node3D, location: Vector3, size: Vector3, tint: Color) -> void:
    var mesh := SphereMesh.new()
    mesh.radius = 0.5
    mesh.height = 1.0
    mesh.radial_segments = 12
    mesh.rings = 6
    var shape := MeshInstance3D.new()
    shape.mesh = mesh
    shape.material_override = _material(tint)
    shape.position = location
    shape.scale = size
    parent.add_child(shape)


func _material(tint: Color) -> StandardMaterial3D:
    var key := tint.to_html()
    if _materials.has(key):
        return _materials[key] as StandardMaterial3D
    var mat := StandardMaterial3D.new()
    mat.albedo_color = tint
    mat.roughness = 0.62
    _materials[key] = mat
    return mat


func _build_hud() -> void:
    var overlay := CanvasLayer.new()
    overlay.name = "PrototypeHUD"
    add_child(overlay)

    var hud := Control.new()
    overlay.add_child(hud)
    hud.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)

    var headline := Label.new()
    headline.text = "LUDO PAWS  •  GODOT V2"
    headline.anchor_left = 0.03
    headline.anchor_right = 0.97
    headline.anchor_top = 0.02
    headline.anchor_bottom = 0.08
    headline.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
    headline.add_theme_font_size_override("font_size", 29)
    headline.add_theme_color_override("font_color", Color("#ffffff"))
    hud.add_child(headline)

    _dice_text = Label.new()
    _dice_text.text = "Visual starter only • Cat / Panda • Gameplay not built yet"
    _dice_text.anchor_left = 0.04
    _dice_text.anchor_right = 0.96
    _dice_text.anchor_top = 0.78
    _dice_text.anchor_bottom = 0.86
    _dice_text.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
    _dice_text.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
    _dice_text.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
    _dice_text.add_theme_font_size_override("font_size", 22)
    _dice_text.add_theme_color_override("font_color", Color("#ffffff"))
    hud.add_child(_dice_text)

    var dice_button := Button.new()
    dice_button.name = "PreviewOnlyDice"
    dice_button.text = "ROLL PREVIEW • NOT GAMEPLAY"
    dice_button.anchor_left = 0.23
    dice_button.anchor_right = 0.77
    dice_button.anchor_top = 0.88
    dice_button.anchor_bottom = 0.95
    dice_button.add_theme_font_size_override("font_size", 22)
    dice_button.pressed.connect(_on_preview_roll)
    hud.add_child(dice_button)


func _on_preview_roll() -> void:
    var number := randi_range(1, 6)
    _dice_text.text = "Visual-only roll: %d • Local preview, not a match result" % number
