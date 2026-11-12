extends VBoxContainer
## The plaque sits above the frame's top edge.

var shoulder_y := 128.0
var fade_bottom := false

const BOTTOM_FADE := preload("res://assets/ui/backgrounds/vine_frame_fade.gdshader")

func _ready() -> void:
	resized.connect(queue_redraw)
	if fade_bottom:
		var fade := ShaderMaterial.new()
		fade.shader = BOTTOM_FADE
		material = fade

func _draw() -> void:
	if size.y <= shoulder_y + 30:
		return
	if fade_bottom:
		(material as ShaderMaterial).set_shader_parameter("frame_height", size.y)
	var frame := Rect2(2, shoulder_y, size.x - 4, size.y - shoulder_y - 4)
	if fade_bottom:
		MeadowDraw.rounded_gradient(self, frame, 25, Color("#0b2019ed"), Color("#0b241af5"))
		MeadowDraw.rounded_outline(self, frame, 25, Color("#795131"), 3.4)
		MeadowDraw.rounded_outline(self, frame.grow(-1.0), 24, Color("#e5b674"), 1.5)
		return
	MeadowDraw.soft_shadow(self, frame, 25, 0.40, 4)
	MeadowDraw.rounded_gradient(self, frame, 25, Color("#c29454"), Color("#6d472b"))
	MeadowDraw.rounded_gradient(self, frame.grow(-1.1), 24, Color("#f4cc87"), Color("#ac7644"))
	MeadowDraw.rounded_gradient(self, frame.grow(-2.4), 23, Color("#774d2d"), Color("#543522"))
	MeadowDraw.rounded_gradient(self, frame.grow(-3.5), 22, Color("#0b2019ed"), Color("#0b241af5"))
