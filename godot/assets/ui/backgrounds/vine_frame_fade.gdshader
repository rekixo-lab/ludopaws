shader_type canvas_item;

uniform float frame_height;
varying float frame_y;

void vertex() {
	frame_y = VERTEX.y;
}

void fragment() {
	COLOR.a *= smoothstep(18.0, 122.0, frame_height - frame_y);
}
