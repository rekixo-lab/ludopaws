shader_type canvas_item;
render_mode unshaded;

void fragment() {
	float alpha = texture(TEXTURE, UV).a;
	COLOR.a *= smoothstep(0.08, 0.55, alpha);
}
