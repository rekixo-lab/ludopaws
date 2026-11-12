shader_type canvas_item;
// Procedural wood plank: grain, bevel and rim are computed, so no bitmap is needed.

uniform vec2 rect_size = vec2(320.0, 72.0);
uniform float radius = 18.0;
uniform vec4 light_wood : source_color = vec4(0.66, 0.42, 0.22, 1.0);
uniform vec4 dark_wood : source_color = vec4(0.40, 0.23, 0.12, 1.0);
uniform vec4 rim_color : source_color = vec4(0.19, 0.10, 0.05, 1.0);
uniform float seed = 3.0;

float rounded_box(vec2 point, vec2 half_size, float r) {
	vec2 q = abs(point) - half_size + r;
	return length(max(q, vec2(0.0))) + min(max(q.x, q.y), 0.0) - r;
}

void fragment() {
	vec2 p = UV * rect_size;
	float d = rounded_box(p - rect_size * 0.5, rect_size * 0.5, radius);

	// Long horizontal grain with slow waves and a few darker streaks.
	float y = p.y + sin(p.x * 0.031 + seed) * 2.6 + sin(p.x * 0.011 + seed * 1.7) * 4.5;
	float streak = pow(sin(y * 0.62) * 0.5 + 0.5, 6.0);
	float band = sin(y * 0.14 + sin(p.x * 0.017 + seed) * 1.4) * 0.5 + 0.5;
	float knot = exp(-length((p - vec2(rect_size.x * 0.72, rect_size.y * 0.42)) * vec2(0.05, 0.12)) * 1.6);
	vec3 wood = mix(dark_wood.rgb, light_wood.rgb, 0.45 + 0.4 * band);
	wood *= 1.0 - 0.16 * streak - 0.18 * knot;
	wood *= 0.92 + 0.16 * (1.0 - UV.y);

	// Dark rim, then a soft light bevel just inside it.
	float rim = smoothstep(-4.5, -1.2, d);
	wood = mix(wood, rim_color.rgb, rim * 0.9);
	float bevel = (1.0 - smoothstep(0.0, 1.6, abs(d + 6.0))) * (1.0 - smoothstep(0.1, 0.65, UV.y));
	wood += vec3(0.16, 0.10, 0.05) * bevel;
	float under = (1.0 - smoothstep(0.0, 1.8, abs(d + 6.0))) * smoothstep(0.55, 0.95, UV.y);
	wood *= 1.0 - 0.25 * under;

	COLOR = vec4(wood, 1.0 - smoothstep(-0.8, 0.6, d));
}
