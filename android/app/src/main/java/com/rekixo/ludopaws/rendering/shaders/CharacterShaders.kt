package com.rekixo.ludopaws.rendering.shaders

internal object CharacterShaders {
        const val VERTEX_SHADER = """
            #version 300 es
            layout(location = 0) in vec3 aPosition;
            layout(location = 1) in vec3 aNormal;
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
                vLocalPosition = position;
                vNormal = normalize(uNormalMatrix * normal);
                gl_Position = uMvp * vec4(position, 1.0);
            }
        """

        const val FRAGMENT_SHADER = """
            #version 300 es
            precision highp float;
            uniform vec4 uColor;
            uniform vec2 uMaterial;
            uniform vec3 uLightDirection;
            in vec3 vNormal;
            in vec3 vLocalPosition;
            out vec4 outColor;
            void main() {
                vec3 normal = normalize(vNormal);
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
                vec3 rgb = baseColor * coatTone * ((softFurLighting ? 0.48 : 0.38) + diffuse * 0.50 + fill + sky) + vec3((specular + rim) * uColor.a);
                outColor = vec4(rgb, uColor.a);
            }
        """
}
