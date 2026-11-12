shader_type canvas_item;

uniform vec4 gold_face_marker : source_color = vec4(1.0, 0.823529, 0.478431, 1.0);
uniform vec4 gold_light : source_color = vec4(1.0, 0.875, 0.54, 1.0);
uniform vec4 gold_shade : source_color = vec4(0.99, 0.72, 0.34, 1.0);

varying vec3 draw_ink;
varying float glyph_y;

void vertex() {
	draw_ink = COLOR.rgb;
	glyph_y = VERTEX.y;
}

void fragment() {
	if (distance(draw_ink, gold_face_marker.rgb) < 0.025) {
		float depth = smoothstep(-34.0, -1.0, glyph_y);
		COLOR.rgb = mix(gold_light.rgb, gold_shade.rgb, depth);
	}
}
