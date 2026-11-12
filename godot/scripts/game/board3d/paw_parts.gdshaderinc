// Port of the live Android CharacterShaders (rendering/shaders/CharacterShaders.kt).
// All lighting is evaluated in the live "pixel space" (+X right, +Y down, +Z to viewer)
// and on sRGB values, exactly like the GLES program; the result is converted to linear
// only when the renderer expects linear output, so the original pixels are reproduced.

render_mode unshaded, cull_disabled, shadows_disabled, ambient_light_disabled, fog_disabled;

uniform vec4 color = vec4(1.0);
uniform float kind = 0.0;      // CharacterSurface.kind
uniform float gloss = 0.055;   // CharacterSurface.gloss
uniform float entry_fade = 1.0;
uniform vec4 clip_rect = vec4(0.0);   // pixel-space x0, y0, x1, y1; disabled when x1 <= x0
uniform vec2 limb_bend = vec2(0.0);
uniform float party_energy = 0.0;
uniform float brightness = 1.0; // the live pawn layer is drawn through a 1.10 RGB scale
uniform vec3 light_direction = vec3(0.40, -1.0, -0.65);

varying vec3 v_normal;
varying vec3 v_local;
varying vec2 v_pixel;

void vertex() {
	vec3 a_position = VERTEX;
	vec3 a_normal = NORMAL;
	vec3 a_local = vec3(UV, UV2.x);
	vec3 position;
	vec3 normal;
	if (abs(kind - 19.0) < 0.5) {
		float curve = max(limb_bend.x, 0.01);
		float angle = max(-a_position.y, 0.0) * curve;
		float s = sin(angle);
		float c = cos(angle);
		position = vec3(a_position.x, -s / curve + a_position.z * s,
			(1.0 - c) / curve + a_position.z * c);
		float along = a_normal.y / max(0.55, 1.0 - a_position.z * curve);
		normal = vec3(a_normal.x, along * c + a_normal.z * s, a_normal.z * c - along * s);
		v_local = a_local;
	} else {
		float y = a_position.y;
		float rounded = sqrt(y * y + 0.0025);
		float weight = max(0.0, (0.5024938 - rounded) / 0.4524938);
		float slope = abs(y) <= 0.5 ? -y / (rounded * 0.4524938) : 0.0;
		position = a_position + vec3(0.0, limb_bend.x, limb_bend.y) * weight;
		float dy = 1.0 + slope * limb_bend.x;
		float dz = slope * limb_bend.y;
		normal = vec3(a_normal.x, (a_normal.y - dz * a_normal.z) / max(0.1, dy), a_normal.z);
		v_local = a_local + vec3(0.0, limb_bend.x, limb_bend.y) * weight;
	}
	VERTEX = position;
	// Godot world == view space here (axis-aligned orthographic camera). Board3D's root
	// flips Y, so pixel space is world space with Y negated.
	vec3 world_normal = normalize(MODEL_NORMAL_MATRIX * normal);
	v_normal = vec3(world_normal.x, -world_normal.y, world_normal.z);
	vec3 world = (MODEL_MATRIX * vec4(position, 1.0)).xyz;
	v_pixel = vec2(world.x, -world.y);
}

vec3 to_linear(vec3 c) {
	c = clamp(c, 0.0, 1.0);
	return mix(pow((c + vec3(0.055)) * (1.0 / 1.055), vec3(2.4)), c * (1.0 / 12.92), lessThan(c, vec3(0.04045)));
}

vec4 shade(vec3 normal_in, vec3 p_in, float clock) {
	vec4 uColor = color;
	vec2 uMaterial = vec2(kind, gloss);
	float uPartyClock = clock;
	float uPartyEnergy = party_energy;
	vec3 normal = normalize(normal_in);
	vec3 vLocalPosition = p_in;
	if (uMaterial.x > 17.5) {
		vec3 p = vLocalPosition;
		vec3 keyDirection = normalize(vec3(-0.48, -0.67, 0.92));
		vec3 fillDirection = normalize(vec3(0.62, -0.22, 0.75));
		float key = max(dot(normal, keyDirection), 0.0);
		float fill = max(dot(normal, fillDirection), 0.0);
		float footprint = max(length(dFdx(p)), length(dFdy(p)));
		float detail = 1.0 / (1.0 + footprint * footprint * 22000.0);
		float grain = sin(p.x * 133.0 + sin(p.y * 83.0)) * sin(p.y * 143.0 + p.z * 121.0);
		float sheenPower = 34.0;
		vec3 pigment = uColor.rgb;
		if (uMaterial.x < 19.5) {
			float fold = sin(p.y * 65.0 + sin(p.x * 18.0) * 1.4 + p.z * 14.0);
			float wrinkles = smoothstep(0.48, 0.96, fold) * detail;
			pigment *= 1.0 - wrinkles * (uMaterial.x > 18.5 ? 0.11 : 0.045);
			float dust = sin(p.x * 17.0 + p.y * 21.0) * sin(p.z * 23.0 - p.y * 16.0);
			pigment *= 1.0 + dust * 0.045 + grain * detail * 0.038;
		} else if (uMaterial.x < 20.5) {
			float dorsal = smoothstep(0.14, 0.31, p.y) * (1.0 - smoothstep(0.035, 0.125, abs(p.x)));
			float shoulder = smoothstep(-0.12, 0.25, p.y) * (1.0 - smoothstep(0.035, 0.14, abs(p.z - 0.14)));
			float belly = 1.0 - smoothstep(-0.32, -0.03, p.y);
			pigment *= 1.0 - max(dorsal * 0.60, shoulder * 0.28);
			pigment = mix(pigment, vec3(0.82, 0.77, 0.69), belly * 0.48);
			pigment *= 1.0 + grain * detail * 0.055;
		} else if (uMaterial.x < 21.5) {
			float coatLine = p.y + p.z * -0.18 + sin(p.z * 23.0 + p.x * 17.0) * 0.026;
			float mantle = smoothstep(0.23, 0.42, coatLine) * (1.0 - smoothstep(-0.10, 0.24, p.z));
			pigment = mix(pigment, vec3(0.47, 0.32, 0.19), mantle * 0.66);
			pigment *= 1.0 + grain * detail * 0.085;
		} else if (uMaterial.x < 22.5) {
			float warm = smoothstep(0.04, 0.40, p.y);
			pigment *= mix(vec3(0.89, 0.82, 0.84), vec3(1.0, 1.02, 1.0), warm);
			pigment *= 1.0 + grain * detail * 0.026;
			sheenPower = 42.0;
		} else if (uMaterial.x < 24.5) {
			if (uMaterial.x > 23.5) {
				float axis = 0.014 * sin(p.y * 18.0) - 0.008;
				float width = mix(0.025, 0.058, smoothstep(0.88, 1.23, p.y));
				float edge = max(0.006, min(0.022, fwidth(p.x)));
				float blaze = (1.0 - smoothstep(width - edge, width + edge, abs(p.x - axis))) *
					smoothstep(0.13, 0.23, p.z) * smoothstep(0.83, 0.93, p.y);
				pigment = mix(pigment, vec3(0.94, 0.87, 0.72), blaze);
			} else {
				float ridge = smoothstep(0.12, 0.39, p.y);
				pigment *= mix(vec3(0.72, 0.64, 0.58), vec3(1.12, 1.05, 0.95), ridge);
			}
			float fibre = sin(p.z * 105.0 + p.y * 41.0 + sin(p.x * 27.0));
			pigment *= 1.0 + fibre * detail * 0.045;
			sheenPower = 53.0;
		} else {
			float tip = smoothstep(0.19, 0.45, p.y);
			float ribs = sin(p.y * 145.0) * detail;
			pigment = mix(pigment, vec3(0.26, 0.18, 0.115), tip * 0.87);
			pigment *= 1.0 + ribs * 0.10;
			sheenPower = 48.0;
		}
		float sheen = pow(max(dot(normal, normalize(keyDirection + vec3(0.0, 0.0, 1.0))), 0.0), sheenPower) * uMaterial.y;
		float rim = pow(1.0 - max(normal.z, 0.0), 2.4) * 0.06;
		return vec4(pigment * (0.47 + key * 0.52 + fill * 0.13) + vec3(sheen + rim), uColor.a * entry_fade);
	}
	if (uMaterial.x > 15.5) {
		vec3 light = normalize(vec3(-0.45, -0.70, 1.0));
		float key = max(dot(normal, light), 0.0);
		float specular = pow(max(dot(normal, normalize(light + vec3(0.0, 0.0, 1.0))), 0.0), 46.0);
		vec3 base = uColor.rgb;
		if (uMaterial.x > 16.5 && base.r > 0.3 && base.b < 0.2) {
			vec2 iris = vec2(abs(vLocalPosition.x) - 0.188, vLocalPosition.y - 1.066);
			float fibers = sin(atan(iris.y, iris.x) * 63.0 + length(iris) * 180.0);
			float detail = 1.0 / (1.0 + dot(fwidth(iris), fwidth(iris)) * 90000.0);
			base *= 1.0 + fibers * detail * 0.10;
		}
		return vec4(base * (0.82 + key * 0.18) + vec3(specular * uMaterial.y), uColor.a * entry_fade);
	}
	if (uMaterial.x > 14.5) {
		vec3 p = vLocalPosition;
		vec3 light = normalize(vec3(-0.45, -0.70, 1.0));
		float key = max(dot(normal, light), 0.0);
		float fill = max(dot(normal, normalize(vec3(0.7, 0.1, 0.8))), 0.0);
		float footprint = max(length(dFdx(p)), length(dFdy(p)));
		float detail = 1.0 / (1.0 + footprint * footprint * 85000.0);
		float flow = p.x + sin(p.y * 8.0 + p.z * 6.0) * 0.025;
		vec3 fibreCell = floor(vec3(flow * 410.0, p.y * 83.0, p.z * 137.0));
		float seed = fract(sin(dot(fibreCell, vec3(127.1, 311.7, 74.7))) * 43758.5453);
		float hairs = sin(flow * 1350.0 + seed * 6.283 + p.y * 19.0);
		float tufts = sin(p.x * 31.0 + sin(p.y * 17.0 + p.z * 23.0) * 2.0);
		vec3 diffuse = uColor.rgb * (0.60 + key * 0.32 + fill * 0.08);
		diffuse *= 1.0 + hairs * detail * 0.035 + tufts * 0.016;
		float crownEdge = p.y + sin(p.x * 29.0 + p.z * 13.0) * 0.022;
		float crown = smoothstep(1.12, 1.30, crownEdge) * (1.0 - smoothstep(0.09, 0.21, abs(p.x)));
		float blaze = exp(-pow((p.x + sin(p.y * 21.0) * 0.012) / 0.028, 2.0));
		crown *= 1.0 - blaze * 0.38;
		float back = smoothstep(0.40, 0.62, p.y) * (1.0 - smoothstep(-0.1, 0.17, p.z)) *
			(1.0 - smoothstep(0.68, 0.83, p.y));
		float legCenterZ = p.z > -0.1 ? 0.18 : -0.56;
		float kneeHeight = p.y + 0.49 + sin(p.x * 31.0 + p.z * 19.0) * 0.014;
		float knee = exp(-pow(kneeHeight / 0.085, 2.0) - pow((abs(p.x) - 0.205) / 0.052, 2.0)) *
			smoothstep(0.012, 0.045, p.z - legCenterZ);
		float ear = smoothstep(0.26, 0.42, abs(p.x)) * smoothstep(1.17, 1.28, p.y);
		float flecks = mix(0.55, seed, detail);
		float pigment = max(max(crown * (0.58 + flecks * 0.34), back * 0.23), max(knee * 0.76, ear * 0.38));
		diffuse *= mix(vec3(1.0), vec3(0.20, 0.21, 0.20), pigment);
		vec3 coat = p + vec3(
			sin(p.y * 23.0 + p.z * 19.0) * 0.018,
			sin(p.z * 27.0 + p.x * 21.0) * 0.014,
			sin(p.x * 25.0 + p.y * 17.0) * 0.012);
		vec3 backSpot = (coat - vec3(-0.15, 0.68, -0.54)) / vec3(0.21, 0.23, 0.24);
		vec3 shoulderSpot = (coat - vec3(0.10, 0.73, -0.04)) / vec3(0.17, 0.18, 0.21);
		vec3 chestSpot = (coat - vec3(-0.075, 0.55, 0.335)) / vec3(0.115, 0.15, 0.11);
		vec3 lowerSpot = (coat - vec3(0.10, 0.24, 0.30)) / vec3(0.09, 0.105, 0.09);
		float backDistance = min(dot(backSpot, backSpot), dot(shoulderSpot, shoulderSpot));
		float frontDistance = min(dot(chestSpot, chestSpot), dot(lowerSpot, lowerSpot));
		float backMarking = (1.0 - smoothstep(0.38, 1.45, backDistance)) * 0.94;
		float frontMarking = (1.0 - smoothstep(0.25, 1.65, frontDistance)) * 0.58;
		float coatSpots = max(backMarking, frontMarking);
		diffuse *= mix(vec3(1.0), vec3(0.07, 0.075, 0.08), coatSpots);
		float velvet = pow(1.0 - max(normal.z, 0.0), 2.0) * 0.035;
		return vec4(diffuse + vec3(velvet), uColor.a * entry_fade);
	}
	if (uMaterial.x > 11.5) {
		if (uMaterial.x > 13.5) {
			float falloff = pow(max(0.0, 1.0 - dot(vLocalPosition.xy, vLocalPosition.xy)), 2.0);
			return vec4(uColor.rgb, uColor.a * falloff * entry_fade);
		} else if (uMaterial.x > 12.5) {
			float core = pow(max(normal.z, 0.0), 3.0);
			return vec4(uColor.rgb * (0.78 + uPartyEnergy * 0.32) + vec3(core * 0.5 * uPartyEnergy), uColor.a * entry_fade);
		}
		vec3 light = normalize(vec3(-0.45, -0.65, 1.05));
		float key = max(dot(normal, light), 0.0);
		float bandCenter = -0.95 + fract(uPartyClock * 0.28) * 1.90;
		float band = 1.0 - smoothstep(0.025, 0.16, abs(vLocalPosition.x + vLocalPosition.y * 0.24 - bandCenter));
		float specular = pow(max(dot(normal, normalize(light + vec3(0.0, 0.0, 1.0))), 0.0), 48.0);
		return vec4(uColor.rgb * (0.70 + 0.30 * key) +
			vec3(1.0, 0.96, 0.80) * (band * 0.65 + specular * 0.28) * uPartyEnergy, uColor.a * entry_fade);
	}
	if (uMaterial.x > 8.5 && uMaterial.x < 11.5) {
		vec3 light = normalize(vec3(-0.45, -0.65, 1.05));
		float key = max(dot(normal, light), 0.0);
		float fill = max(dot(normal, normalize(vec3(0.6, 0.2, 0.8))), 0.0);
		vec3 p = vLocalPosition;
		float footprint = max(length(dFdx(p)), length(dFdy(p)));
		float detail = 1.0 / (1.0 + footprint * footprint * 16000.0);
		bool ground = uMaterial.x < 9.5;
		bool foliage = uMaterial.x > 10.5;
		float grain = sin(p.x * 137.0 + sin(p.y * 79.0)) * sin(p.y * 163.0 + p.z * 31.0);
		float textureTone = 1.0 + grain * detail * (ground ? 0.075 : 0.035);
		float mineral = sin(p.x * 7.0 + sin(p.y * 5.0) * 1.7) * sin(p.y * 9.0 - p.x * 3.0 + p.z * 2.0);
		float broadTone = ground ? sin(p.x * 24.0) * sin(p.y * 19.0) * 0.035 : (foliage ? mineral * 0.035 : 0.0);
		float diffuse = 0.39 + key * 0.57 + fill * 0.10;
		float sheen = pow(max(dot(normal, normalize(light + vec3(0.0, 0.0, 1.0))), 0.0), foliage ? 22.0 : 36.0) * uMaterial.y;
		return vec4(uColor.rgb * (textureTone + broadTone) * diffuse + vec3(sheen), uColor.a * entry_fade);
	}
	if (uMaterial.x > 6.5 && uMaterial.x < 8.5) {
		vec3 houseLight = normalize(vec3(-0.45, -0.65, 1.05));
		float key = max(dot(normal, houseLight), 0.0);
		float bounce = max(dot(normal, normalize(vec3(0.5, 0.3, 1.0))), 0.0);
		bool wall = uMaterial.x < 7.5;
		float illumination = wall ? 0.78 + key * 0.20 + bounce * 0.04 : 0.55 + key * 0.43 + bounce * 0.05;
		float crest = wall ? 0.0 : smoothstep(0.10, 0.74, vLocalPosition.z) * 0.035;
		vec3 halfLight = normalize(houseLight + vec3(0.0, 0.0, 1.0));
		float sheen = pow(max(dot(normal, halfLight), 0.0), 28.0) * uMaterial.y;
		return vec4(uColor.rgb * (illumination + crest) + vec3(sheen), uColor.a * entry_fade);
	}
	vec3 light = normalize(-light_direction);
	bool softFurLighting = uMaterial.x > 4.5 && uMaterial.x < 6.5;
	if (softFurLighting) light.y = -abs(light.y);
	vec3 viewDir = vec3(0.0, 0.0, 1.0);
	vec3 halfDir = normalize(light + viewDir);
	float diffuse = max(dot(normal, light), 0.0);
	float specular = pow(max(dot(normal, halfDir), 0.0), mix(18.0, 62.0, step(0.25, uMaterial.y))) * uMaterial.y;
	vec3 p = vLocalPosition;
	float footprint = max(length(dFdx(p)), length(dFdy(p)));
	float fineDetail = 1.0 / (1.0 + footprint * footprint * 90000.0);
	float coatTone = 1.0;
	if ((uMaterial.x > 0.5 && uMaterial.x < 1.5) || uMaterial.x > 3.5) {
		float hair = sin(p.x * 330.0 + p.z * 215.0 + sin(p.y * 37.0) * 0.65);
		float coat = sin(p.x * 47.0 + p.z * 29.0) * sin(p.y * 39.0 - p.z * 17.0);
		coatTone += hair * fineDetail * 0.045 + coat * 0.018;
	} else if (uMaterial.x > 1.5 && uMaterial.x < 2.5) {
		coatTone += sin(p.y * 145.0) * fineDetail * 0.055;
	}
	vec3 baseColor = uColor.rgb;
	if (uMaterial.x > 4.5 && uMaterial.x < 5.5 && uColor.r > 0.5) {
		vec3 furCell = floor(p * vec3(480.0, 750.0, 480.0));
		float fibre = fract(sin(dot(furCell, vec3(127.1, 311.7, 74.7))) * 43758.5453);
		float flow = sin(p.x * 13.0 + p.z * 8.0) * 1.5 + sin(p.y * 17.0 - p.x * 7.0 + p.z * 11.0) * 0.65;
		float phase = p.y * 48.0 + abs(p.x) * 12.0 + p.z * 20.0 + flow;
		float width = 0.52 + sin(p.x * 19.0 - p.y * 13.0 + p.z * 9.0) * 0.20;
		float edge = clamp(fwidth(phase) * 0.28, 0.09, 0.28);
		float stripe = smoothstep(width - edge, width + 0.22 + edge, sin(phase));
		float broken = 0.20 + 0.80 * smoothstep(-0.55, 0.45,
			sin(p.x * 24.0 + p.y * 14.0 - p.z * 17.0) + sin(p.z * 31.0 + p.x * 11.0) * 0.22);
		float bib = smoothstep(0.15, 0.42, p.z) * (1.0 - smoothstep(0.06, 0.28, abs(p.x)));
		stripe *= broken * (1.0 - bib * 0.42);
		vec3 coatBase = mix(uColor.rgb * vec3(0.92, 0.94, 0.95), uColor.rgb * 1.08, smoothstep(-0.12, 0.35, p.z));
		baseColor = mix(coatBase, vec3(0.19, 0.18, 0.16), stripe * 0.94);
		coatTone = 1.0 + (fibre - 0.5) * fineDetail * 0.09;
	}
	if (uMaterial.x > 3.5) {
		float chest = smoothstep(0.12, 0.35, p.z) * (1.0 - smoothstep(0.12, 0.36, abs(p.x)));
		baseColor = mix(baseColor, baseColor * 1.10, chest);
	}
	if (uMaterial.x > 5.5 && uMaterial.x < 6.5) coatTone = 1.0;
	float sky = (softFurLighting ? -normal.y : normal.y) * 0.055;
	float fill = max(dot(normal, normalize(vec3(-0.7, 0.2, 0.6))), 0.0) * 0.12;
	float rim = pow(1.0 - max(dot(normal, viewDir), 0.0), 2.2) * 0.08;
	vec3 rgb = baseColor * coatTone * ((softFurLighting ? 0.49 : 0.44) + diffuse * 0.55 + fill + sky) + vec3((specular + rim) * uColor.a);
	return vec4(rgb, uColor.a * entry_fade);
}

void fragment() {
	if (clip_rect.z > clip_rect.x && (v_pixel.x < clip_rect.x || v_pixel.x > clip_rect.z ||
			v_pixel.y < clip_rect.y || v_pixel.y > clip_rect.w)) {
		discard;
	}
	vec4 lit = shade(v_normal, v_local, mod(TIME, 100.0));
	// Compatibility writes sRGB directly; Forward+/Mobile expect linear albedo.
	ALBEDO = OUTPUT_IS_SRGB ? clamp(lit.rgb * brightness, 0.0, 1.0) : to_linear(lit.rgb * brightness);
#ifdef PAW_TRANSPARENT
	ALPHA = clamp(lit.a, 0.0, 1.0);
#endif
}
