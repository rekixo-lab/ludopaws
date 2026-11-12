shader_type canvas_item;
render_mode unshaded;

float disc(float radius, float boundary) {
	return 1.0 - smoothstep(boundary - 0.007, boundary + 0.007, radius);
}

void fragment() {
	vec2 centered = UV - vec2(0.5);
	float radius = length(centered);
	float light = clamp(0.53 - centered.y * 0.72 - centered.x * 0.34, 0.0, 1.0);

	vec3 shadow = vec3(0.12, 0.065, 0.032);
	float alpha = disc(length(centered - vec2(0.005, 0.02)), 0.476) * 0.69;

	vec3 gold_outer = mix(vec3(0.38, 0.19, 0.085), vec3(0.78, 0.52, 0.29), light);
	vec3 gold_ridge = mix(vec3(0.57, 0.27, 0.11), vec3(1.0, 0.82, 0.59), light);
	vec3 gold_inner = mix(vec3(0.41, 0.21, 0.10), vec3(0.65, 0.39, 0.20), light);
	vec3 finish = mix(shadow, gold_outer, disc(radius, 0.474));
	finish = mix(finish, gold_ridge, disc(radius, 0.461));
	finish = mix(finish, gold_inner, disc(radius, 0.431));
	finish = mix(finish, vec3(0.23, 0.125, 0.064), disc(radius, 0.411));

	vec3 grain = texture(TEXTURE, vec2(UV.x * 0.73 + 0.13, UV.y * 0.77 + 0.1)).rgb;
	float grain_light = dot(grain, vec3(0.31, 0.52, 0.17));
	vec3 wood = vec3(0.267, 0.162, 0.101) + grain_light * vec3(0.39, 0.235, 0.15);
	wood *= mix(0.79, 1.24, light);
	float inset_shadow = smoothstep(0.345, 0.402, radius);
	wood *= 1.0 - inset_shadow * 0.22;
	finish = mix(finish, wood, disc(radius, 0.4));
	float upper_ridge = (1.0 - smoothstep(0.0, 0.013, abs(radius - 0.458))) * smoothstep(0.49, 0.87, light);
	finish += vec3(0.16, 0.122, 0.076) * upper_ridge;
	COLOR = vec4(finish, max(alpha, disc(radius, 0.474)));
}
