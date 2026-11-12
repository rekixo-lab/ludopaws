shader_type canvas_item;
render_mode unshaded;

uniform vec2 panel_size = vec2(430.0, 90.0);
uniform float corner_radius = 17.0;

float edge_distance(vec2 point) {
	vec2 half_size = panel_size * 0.5;
	vec2 edge = abs(point - half_size) - half_size + corner_radius + 0.7;
	return length(max(edge, vec2(0.0))) + min(max(edge.x, edge.y), 0.0) - corner_radius;
}

float engraved_line(float distance_to_line) {
	return 1.0 - smoothstep(0.18, 0.92, abs(distance_to_line));
}

void fragment() {
	vec2 point = UV * panel_size;
	float edge = edge_distance(point);
	vec3 grain = texture(TEXTURE, vec2(UV.x, mix(0.07, 0.94, UV.y))).rgb;
	float warmth = dot(grain, vec3(0.42, 0.43, 0.15));
	vec3 wood = mix(grain, vec3(warmth * 1.21, warmth, warmth * 0.79), 0.56);
	float upper_light = 0.13 * (1.0 - smoothstep(0.0, 0.82, UV.y));
	wood = wood * (1.03 + upper_light) + vec3(0.022, 0.016, 0.013);

	float groove_span = smoothstep(11.0, 14.0, point.y) * (1.0 - smoothstep(63.0, 66.0, point.y));
	float vertical_grooves = max(engraved_line(point.x - 222.0), engraved_line(point.x - 323.0));
	float groove_light = max(engraved_line(point.x - 223.1), engraved_line(point.x - 324.1));
	float ledge_span = smoothstep(19.0, 24.0, point.x) * (1.0 - smoothstep(406.0, 413.0, point.x));
	wood *= 1.0 - 0.50 * groove_span * vertical_grooves;
	wood += vec3(0.09, 0.055, 0.028) * groove_span * groove_light;
	wood *= 1.0 - 0.35 * ledge_span * engraved_line(point.y - 73.0);
	wood += vec3(0.11, 0.067, 0.036) * ledge_span * engraved_line(point.y - 74.25);

	float bevel = smoothstep(-3.2, -0.4, edge);
	vec3 rim = mix(vec3(0.54, 0.35, 0.19), vec3(0.19, 0.105, 0.065), smoothstep(0.0, 0.94, UV.y));
	wood = mix(wood, rim, bevel * 0.68);
	float edge_highlight = (1.0 - smoothstep(0.4, 1.7, abs(edge + 1.6))) * (1.0 - smoothstep(0.04, 0.42, UV.y));
	wood += vec3(0.14, 0.091, 0.048) * edge_highlight;
	COLOR = vec4(wood, 1.0 - smoothstep(-0.65, 0.55, edge));
}
