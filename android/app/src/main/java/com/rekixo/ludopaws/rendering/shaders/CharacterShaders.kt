package com.rekixo.ludopaws.rendering.shaders

internal object CharacterShaders {
        const val VERTEX_SHADER = """
            #version 300 es
            layout(location = 0) in vec3 aPosition;
            layout(location = 1) in vec3 aNormal;
            layout(location = 2) in vec3 aLocalPosition;
            uniform mat4 uMvp;
            uniform mat3 uNormalMatrix;
            uniform vec2 uLimbBend;
            out vec3 vNormal;
            out vec3 vLocalPosition;
            void main() {
                // A continuous skin follows the solved knee without exposed rigid caps.
                // Almost straight bone sections meet in a softly rounded knee.
                // A narrow smooth joint avoids rubber-hose curvature along the whole leg.
                float y = aPosition.y;
                float rounded = sqrt(y * y + 0.0025);
                float weight = max(0.0, (0.5024938 - rounded) / 0.4524938);
                float slope = abs(y) <= 0.5 ? -y / (rounded * 0.4524938) : 0.0;
                vec3 position = aPosition + vec3(0.0, uLimbBend.x, uLimbBend.y) * weight;
                float dy = 1.0 + slope * uLimbBend.x;
                float dz = slope * uLimbBend.y;
                vec3 normal = vec3(aNormal.x, (aNormal.y - dz * aNormal.z) / max(0.1, dy), aNormal.z);
                vLocalPosition = aLocalPosition + vec3(0.0, uLimbBend.x, uLimbBend.y) * weight;
                vNormal = normalize(uNormalMatrix * normal);
                gl_Position = uMvp * vec4(position, 1.0);
            }
        """

        const val FRAGMENT_SHADER = """
            #version 300 es
            precision highp float;
            uniform vec4 uColor;
            uniform vec4 uEntryClip;
            uniform float uEntryFade;
            uniform vec2 uMaterial;
            uniform vec3 uLightDirection;
            uniform float uPartyClock;
            uniform float uPartyEnergy;
            in vec3 vNormal;
            in vec3 vLocalPosition;
            out vec4 outColor;
            void main() {
                if (uEntryClip.z > uEntryClip.x &&
                    (gl_FragCoord.x < uEntryClip.x || gl_FragCoord.x > uEntryClip.z ||
                     gl_FragCoord.y < uEntryClip.y || gl_FragCoord.y > uEntryClip.w)) discard;
                vec3 normal = normalize(vNormal);
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
                    outColor = vec4(base * (0.82 + key * 0.18) + vec3(specular * uMaterial.y), uColor.a * uEntryFade);
                    return;
                }
                if (uMaterial.x > 14.5) {
                    // Fine white kid fur, filtered as the pawn gets smaller on the board.
                    vec3 p = vLocalPosition;
                    vec3 light = normalize(vec3(-0.45, -0.70, 1.0));
                    float key = max(dot(normal, light), 0.0);
                    float fill = max(dot(normal, normalize(vec3(0.7, 0.1, 0.8))), 0.0);
                    float footprint = max(length(dFdx(p)), length(dFdy(p)));
                    float detail = 1.0 / (1.0 + footprint * footprint * 85000.0);
                    float flow = p.x + sin(p.y * 8.0 + p.z * 6.0) * 0.025;
                    // Jitter individual fibres rather than intersecting periodic stripes.
                    vec3 fibreCell = floor(vec3(flow * 410.0, p.y * 83.0, p.z * 137.0));
                    float seed = fract(sin(dot(fibreCell, vec3(127.1, 311.7, 74.7))) * 43758.5453);
                    float hairs = sin(flow * 1350.0 + seed * 6.283 + p.y * 19.0);
                    float tufts = sin(p.x * 31.0 + sin(p.y * 17.0 + p.z * 23.0) * 2.0);
                    vec3 diffuse = uColor.rgb * (0.70 + key * 0.28 + fill * 0.09);
                    diffuse *= 1.0 + hairs * detail * 0.035 + tufts * 0.016;
                    // Anatomical pigment stays in the skin coordinates, including on fur tips.
                    float crownEdge = p.y + sin(p.x * 29.0 + p.z * 13.0) * 0.022;
                    float crown = smoothstep(1.12, 1.30, crownEdge) *
                        (1.0 - smoothstep(0.09, 0.21, abs(p.x)));
                    float blaze = exp(-pow((p.x + sin(p.y * 21.0) * 0.012) / 0.028, 2.0));
                    crown *= 1.0 - blaze * 0.38;
                    float back = smoothstep(0.40, 0.62, p.y) * (1.0 - smoothstep(-0.1, 0.17, p.z)) *
                        (1.0 - smoothstep(0.68, 0.83, p.y));
                    // Local coat markings, not horizontal bands that read as separate limb joints.
                    float legCenterZ = p.z > -0.1 ? 0.18 : -0.56;
                    float kneeHeight = p.y + 0.49 + sin(p.x * 31.0 + p.z * 19.0) * 0.014;
                    float knee = exp(-pow(kneeHeight / 0.085, 2.0) -
                        pow((abs(p.x) - 0.205) / 0.052, 2.0)) *
                        smoothstep(0.012, 0.045, p.z - legCenterZ);
                    float ear = smoothstep(0.26, 0.42, abs(p.x)) * smoothstep(1.17, 1.28, p.y);
                    float flecks = mix(0.55, seed, detail);
                    float pigment = max(max(crown * (0.58 + flecks * 0.34), back * 0.23),
                        max(knee * 0.76, ear * 0.38));
                    diffuse *= mix(vec3(1.0), vec3(0.20, 0.21, 0.20), pigment);
                    // Pigment volumes follow the skin/fur coordinates, with no overlay mesh.
                    // Uneven edges feather into the white coat instead of forming round decals.
                    vec3 coat = p + vec3(
                        sin(p.y * 23.0 + p.z * 19.0) * 0.018,
                        sin(p.z * 27.0 + p.x * 21.0) * 0.014,
                        sin(p.x * 25.0 + p.y * 17.0) * 0.012);
                    vec3 backSpot = (coat - vec3(-0.15, 0.68, -0.54)) / vec3(0.21, 0.23, 0.24);
                    vec3 shoulderSpot = (coat - vec3(0.10, 0.73, -0.04)) / vec3(0.17, 0.18, 0.21);
                    vec3 chestSpot = (coat - vec3(-0.075, 0.55, 0.335)) / vec3(0.115, 0.15, 0.11);
                    vec3 lowerSpot = (coat - vec3(0.10, 0.24, 0.30)) / vec3(0.09, 0.105, 0.09);
                    float spotDistance = min(min(dot(backSpot, backSpot), dot(shoulderSpot, shoulderSpot)),
                        min(dot(chestSpot, chestSpot), dot(lowerSpot, lowerSpot)));
                    float coatSpots = 1.0 - smoothstep(0.38, 1.45, spotDistance);
                    diffuse *= mix(vec3(1.0), vec3(0.07, 0.075, 0.08), coatSpots * 0.94);
                    float velvet = pow(1.0 - max(normal.z, 0.0), 2.0) * 0.055;
                    outColor = vec4(diffuse + vec3(velvet), uColor.a * uEntryFade);
                    return;
                }
                if (uMaterial.x > 11.5) {
                    if (uMaterial.x > 13.5) {
                        // Soft reflected pools; transparent edges never write depth.
                        float falloff = pow(max(0.0, 1.0 - dot(vLocalPosition.xy, vLocalPosition.xy)), 2.0);
                        outColor = vec4(uColor.rgb, uColor.a * falloff * uEntryFade);
                    } else if (uMaterial.x > 12.5) {
                        float core = pow(max(normal.z, 0.0), 3.0);
                        outColor = vec4(uColor.rgb * (0.78 + uPartyEnergy * 0.32) +
                            vec3(core * 0.5 * uPartyEnergy), uColor.a * uEntryFade);
                    } else {
                        vec3 light = normalize(vec3(-0.45, -0.65, 1.05));
                        float key = max(dot(normal, light), 0.0);
                        float bandCenter = -0.95 + fract(uPartyClock * 0.28) * 1.90;
                        float band = 1.0 - smoothstep(0.025, 0.16,
                            abs(vLocalPosition.x + vLocalPosition.y * 0.24 - bandCenter));
                        float specular = pow(max(dot(normal, normalize(light + vec3(0.0, 0.0, 1.0))), 0.0), 48.0);
                        outColor = vec4(uColor.rgb * (0.70 + 0.30 * key) +
                            vec3(1.0, 0.96, 0.80) * (band * 0.65 + specular * 0.28) * uPartyEnergy,
                            uColor.a * uEntryFade);
                    }
                    return;
                }
                // Garden relief needs stronger directional modelling than the cream walls.
                // Rough mineral/soil grain is filtered at small on-board pixel sizes.
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
                    float broadTone = ground ? sin(p.x * 24.0) * sin(p.y * 19.0) * 0.035 : 0.0;
                    float diffuse = 0.39 + key * 0.57 + fill * 0.10;
                    float sheen = pow(max(dot(normal, normalize(light + vec3(0.0, 0.0, 1.0))), 0.0),
                        foliage ? 22.0 : 36.0) * uMaterial.y;
                    outColor = vec4(uColor.rgb * (textureTone + broadTone) * diffuse + vec3(sheen), uColor.a * uEntryFade);
                    return;
                }
                // Architecture uses a soft screen-space key from above-left.
                // Keep this separate from the established animal material lighting.
                if (uMaterial.x > 6.5 && uMaterial.x < 8.5) {
                    vec3 houseLight = normalize(vec3(-0.45, -0.65, 1.05));
                    float key = max(dot(normal, houseLight), 0.0);
                    float bounce = max(dot(normal, normalize(vec3(0.5, 0.3, 1.0))), 0.0);
                    bool wall = uMaterial.x < 7.5;
                    // Walls stay cream even when their facade faces away from the key.
                    // Roofs retain enough side-to-side contrast to read as convex slopes.
                    float illumination = wall ? 0.78 + key * 0.20 + bounce * 0.04
                                              : 0.55 + key * 0.43 + bounce * 0.05;
                    float crest = wall ? 0.0 : smoothstep(0.10, 0.74, vLocalPosition.z) * 0.035;
                    vec3 halfLight = normalize(houseLight + vec3(0.0, 0.0, 1.0));
                    float sheen = pow(max(dot(normal, halfLight), 0.0), 28.0) * uMaterial.y;
                    outColor = vec4(uColor.rgb * (illumination + crest) + vec3(sheen), uColor.a * uEntryFade);
                    return;
                }
                vec3 light = normalize(-uLightDirection);
                bool softFurLighting = uMaterial.x > 4.5 && uMaterial.x < 6.5;
                // Model Y is flipped into screen space. Soft fur key light comes from above.
                if (softFurLighting) light.y = -abs(light.y);
                vec3 viewDir = vec3(0.0, 0.0, 1.0);
                vec3 halfDir = normalize(light + viewDir);
                float diffuse = max(dot(normal, light), 0.0);
                float specular = pow(max(dot(normal, halfDir), 0.0), mix(18.0, 62.0, step(0.25, uMaterial.y))) * uMaterial.y;
                // Derivative filtering suppresses shimmer when a pawn is only a few board pixels tall.
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
                // Cat-only coat pigment: broad black/ivory bands remain visible at pawn size.
                // Local-space coordinates keep the stripes attached to each articulated mesh.
                if (uMaterial.x > 4.5 && uMaterial.x < 5.5 && uColor.r > 0.5) {
                    float phase = p.y * 21.0 + sin(p.x * 8.0 + p.z * 6.0) * 0.55 + p.z * 2.5;
                    float edge = max(fwidth(phase) * 0.45, 0.10);
                    float stripe = smoothstep(-edge, edge, sin(phase));
                    baseColor = mix(uColor.rgb, vec3(0.035, 0.040, 0.045), stripe);
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
                outColor = vec4(rgb, uColor.a * uEntryFade);
            }
        """
}
