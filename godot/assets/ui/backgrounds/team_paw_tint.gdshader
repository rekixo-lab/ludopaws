shader_type canvas_item;
render_mode unshaded;

uniform vec4 shadow_tint : source_color;
uniform vec4 light_tint : source_color;

void fragment() {
	float value = dot(COLOR.rgb, vec3(0.2126, 0.7152, 0.0722));
	COLOR.rgb = mix(shadow_tint.rgb, light_tint.rgb, smoothstep(0.15, 0.95, value));
}
