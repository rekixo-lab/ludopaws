shader_type canvas_item;
render_mode unshaded;

void fragment() {
	vec4 stamp = COLOR;
	float gray = dot(stamp.rgb, vec3(0.299, 0.587, 0.114));
	COLOR = vec4(mix(vec3(gray), vec3(gray * 0.78, gray * 0.83, gray * 0.76), 0.6), stamp.a);
}
