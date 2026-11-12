shader_type canvas_item;
render_mode unshaded;

void fragment() {
	vec2 edge = min(UV, 1.0 - UV);
	float alpha = smoothstep(0.0, 0.08, edge.x) * smoothstep(0.0, 0.14, edge.y);
	COLOR.a *= alpha;
}
