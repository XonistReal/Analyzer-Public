#version 150

uniform vec4 Tint;
uniform int Style;
uniform float Time;
uniform float Strength;
uniform float WireWidth;

in vec3 viewPosition;
in vec3 viewNormal;
in vec2 quadUv;
out vec4 fragColor;

vec3 safeUnit(vec3 value) {
    return value * inversesqrt(max(dot(value, value), 0.000001));
}

void main() {
    vec3 normal = safeUnit(viewNormal);
    vec3 eye = safeUnit(-viewPosition);
    float facing = dot(normal, eye);
    // Match front surfaces even when native model matrices mirror vertex winding.
    // An occluded back face must not coat the visible front of its own cuboid.
    if (facing <= 0.0) discard;

    float rim = pow(1.0 - clamp(facing, 0.0, 1.0), 2.2);
    vec3 color = Tint.rgb;
    float alpha = Tint.a;

    if (Style == 1) {
        // Metallic: directional diffuse, a narrow view-dependent specular lobe and
        // a broad reflected studio-light band. No external cubemap dependency.
        vec3 light = safeUnit(vec3(-0.35, 0.75, 0.6));
        vec3 halfway = safeUnit(light + eye);
        float diffuse = max(dot(normal, light), 0.0);
        float specular = pow(max(dot(normal, halfway), 0.0), 52.0);
        vec3 reflected = reflect(-eye, normal);
        float band = pow(clamp(1.0 - abs(reflected.y - 0.3), 0.0, 1.0), 7.0);
        color = Tint.rgb * (0.3 + diffuse * 0.6 + band * 0.36 * Strength)
                + vec3(0.86, 0.91, 1.0) * (specular * 0.9 + rim * 0.22) * Strength;
    } else if (Style == 2) {
        // Glass: clear centers and bright Fresnel edges with a reflected highlight.
        // Alpha composes with the real world/skin underneath; no scene refraction.
        vec3 reflected = reflect(-eye, normal);
        float reflection = pow(max(0.0, 1.0 - abs(reflected.y - 0.45) * 2.4), 9.0);
        color = mix(Tint.rgb * 0.8, vec3(0.93, 0.97, 1.0), clamp(rim * 0.8 * Strength, 0.0, 0.9));
        color += vec3(reflection * 0.48 * Strength);
        alpha *= clamp(0.16 + rim * 0.65 * Strength + reflection * 0.2, 0.0, 1.0);
    } else if (Style == 3) {
        // Glow: emissive fill plus a brighter animated edge. This intentionally
        // stays on the model; it is not a bloom or screen-space halo pass.
        float pulse = 0.96 + 0.04 * sin(Time * 2.0);
        color = Tint.rgb * (0.8 + rim * 0.7 * Strength) * pulse
                + vec3(rim * 0.25 * Strength);
        alpha *= clamp(0.54 + rim * 0.46 * Strength, 0.0, 1.0);
    } else if (Style == 4) {
        // Screen-pixel edge distance avoids implementation-dependent wide lines.
        // Face UVs exclude triangle diagonals and derivatives antialias the edge.
        vec2 edgePixels = min(quadUv, vec2(1.0) - quadUv) / max(fwidth(quadUv), vec2(0.00001));
        float edgeDistance = min(edgePixels.x, edgePixels.y);
        float coverage = 1.0 - smoothstep(max(0.0, WireWidth - 0.5), WireWidth + 0.5, edgeDistance);
        alpha *= coverage;
        color = Tint.rgb * 0.96 + vec3(0.06 * Strength);
    }

    color = clamp(color, 0.0, 1.0);
    alpha = clamp(alpha, 0.0, 1.0);
    if (alpha < 0.001) discard;
    fragColor = vec4(color * alpha, alpha);
}
