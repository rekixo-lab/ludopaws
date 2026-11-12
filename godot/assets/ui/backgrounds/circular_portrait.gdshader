shader_type canvas_item;

uniform vec2 portrait_size = vec2(125.0);
varying vec2 local_position;

void vertex() {
	local_position = VERTEX;
}

void fragment() {
	float radius = min(portrait_size.x, portrait_size.y) * 0.5;
	float distance_to_edge = radius - length(local_position - portrait_size * 0.5);
	COLOR.a *= smoothstep(0.0, 1.0, distance_to_edge);
}
