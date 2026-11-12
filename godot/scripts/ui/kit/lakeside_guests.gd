extends Control
## One movable sprite per animal, positioned in normalized coordinates of the shared lake.

const GUESTS := preload("res://assets/ui/illustrations/lakeside_guests.png")
const PAIR := preload("res://assets/ui/illustrations/lakeside_fox_rabbit.png")
const ALPHA := preload("res://assets/ui/backgrounds/lakeside_sprite_alpha.gdshader")
const ANIMALS := ["Bear", "Fox", "Rabbit", "Raccoon"]
const TEXTURES := [GUESTS, PAIR, PAIR, GUESTS]
const REGIONS := [Rect2(370, 44, 440, 638), Rect2(252, 48, 561, 645),
	Rect2(1429, 11, 389, 684), Rect2(1416, 53, 496, 632)]
const HEIGHTS := [0.275, 0.29, 0.315, 0.28]
const ONLINE_SEATS := [Vector2(.305, .725), Vector2(.431, .718),
	Vector2(.554, .718), Vector2(.678, .725)]
const TEAM_SEATS := [Vector2(.395, .721), Vector2(.296, .724),
	Vector2(.622, .721), Vector2(.716, .727)]

var lake_size := Vector2(1774, 887)
var team_layout := false
var _actors: Array[Node2D] = []
var _arrival: Tween
var _progress := 0.0:
	set(value):
		_progress = value
		_layout()

func _ready() -> void:
	mouse_filter = MOUSE_FILTER_IGNORE
	clip_contents = true
	var edge_cleanup := ShaderMaterial.new()
	edge_cleanup.shader = ALPHA
	for i in ANIMALS.size():
		var actor := Node2D.new()
		actor.name = ANIMALS[i]
		add_child(actor)
		_actors.append(actor)

		var shadow := Polygon2D.new()
		var shadow_width: float = lake_size.y * HEIGHTS[i] * REGIONS[i].size.aspect() * 0.42
		shadow.polygon = MeadowDraw.ellipse_points(Vector2.ZERO, Vector2(shadow_width, lake_size.y * 0.014))
		shadow.color = Color(0.03, 0.035, 0.02, 0.38)
		actor.add_child(shadow)

		var sprite := Sprite2D.new()
		sprite.name = "Artwork"
		sprite.texture = TEXTURES[i]
		sprite.region_enabled = true
		sprite.region_rect = REGIONS[i]
		sprite.region_filter_clip_enabled = true
		sprite.offset.y = -sprite.region_rect.size.y * 0.5
		sprite.scale = Vector2.ONE * lake_size.y * HEIGHTS[i] / sprite.region_rect.size.y
		sprite.material = edge_cleanup
		actor.add_child(sprite)
	resized.connect(_layout)
	_layout()

func show_four(enabled: bool, animate: bool) -> void:
	if _arrival != null:
		_arrival.kill()
	var target := 1.0 if enabled else 0.0
	if not animate or is_equal_approx(_progress, target):
		_progress = target
		return
	_arrival = create_tween()
	var duration := 0.95 if enabled else 0.62
	_arrival.tween_property(self, "_progress", target, duration * absf(target - _progress))

func _layout() -> void:
	if _actors.is_empty():
		return
	# Match the background's centered cover crop; positions are relative to the stone.
	var zoom := maxf(size.x / lake_size.x, size.y / lake_size.y)
	var origin := (size - lake_size * zoom) * 0.5
	var seats := TEAM_SEATS if team_layout else ONLINE_SEATS
	for i in _actors.size():
		var actor := _actors[i]
		var guest := not team_layout and (i == 0 or i == 3)
		var delay := 0.12 if i == 3 else 0.0
		var t := clampf((_progress - delay) / (1.0 - delay), 0.0, 1.0) if guest else 1.0
		actor.visible = t > 0.0
		var seat: Vector2 = seats[i]
		var side := -1.0 if i < 2 else 1.0
		var travel := 1.0 - smoothstep(0.0, 0.9, t)
		var contact := smoothstep(0.73, 1.0, t)
		var step := sin(t * TAU * 1.65) * (1.0 - contact)
		actor.position = origin + (seat + Vector2(side * 0.09, 0.035) * travel) * lake_size * zoom
		actor.scale = Vector2.ONE * zoom
		actor.modulate.a = smoothstep(0.0, 0.26, t)
		var sprite := actor.get_node("Artwork") as Sprite2D
		sprite.position.y = -absf(step) * lake_size.y * 0.019
		sprite.rotation = side * step * 0.055
