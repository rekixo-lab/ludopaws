shader_type canvas_item;
render_mode unshaded;

uniform vec2 art_size = vec2(400.0, 304.0);
uniform float top_fade = 0.28;
varying vec2 art_position;

void vertex() {
	art_position = VERTEX;
}

void fragment() {
	vec2 uv = art_position / max(art_size, vec2(1.0));
	float sides = smoothstep(0.0, 0.13, min(uv.x, 1.0 - uv.x));
	float top = smoothstep(0.0, top_fade, uv.y);
	float bottom = smoothstep(0.0, 0.20, 1.0 - uv.y);
	COLOR.a *= sides * top * bottom;
}
