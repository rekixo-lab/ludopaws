shader_type canvas_item;

uniform vec2 visible_fraction = vec2(1.0);
uniform vec2 crop_origin = vec2(0.0);
uniform float fade_start : hint_range(0.0, 0.99) = 0.85;

void fragment() {
	vec4 illustration = texture(TEXTURE, crop_origin + UV * visible_fraction);
	float fade = smoothstep(fade_start, 1.0, UV.y);
	illustration.rgb *= mix(1.0, 0.76, fade);
	illustration.a *= 1.0 - fade;
	COLOR = illustration;
}
