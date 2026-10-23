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
            in vec3 vNormal;
            in vec3 vLocalPosition;
            out vec4 outColor;
            void main() {
                if (uEntryClip.z > uEntryClip.x &&
                    (gl_FragCoord.x < uEntryClip.x || gl_FragCoord.x > uEntryClip.z ||
                     gl_FragCoord.y < uEntryClip.y || gl_FragCoord.y > uEntryClip.w)) discard;
                vec3 normal = normalize(vNormal);
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
                bool softFurLighting = uMaterial.x > 5.5 && uMaterial.x < 6.5;
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
