#version 150

uniform vec4 Tint;
uniform vec4 Secondary;
uniform int Style;
uniform float Time;
uniform float Strength;
uniform float WireWidth;
uniform float Edges;

in vec3 viewPosition;
in vec3 viewNormal;
in vec2 quadUv;
out vec4 fragColor;

vec3 safeUnit(vec3 value) {
    return value * inversesqrt(max(dot(value, value), 0.000001));
}

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float noise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), u.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x), u.y);
}

void main() {
    // Screen-pixel edge distance avoids implementation-dependent wide lines. Face UVs exclude
    // triangle diagonals and derivatives antialias the edge. Taken before any discard so the
    // derivatives stay defined for every material, including the optional edge outline.
    vec2 edgePixels = min(quadUv, vec2(1.0) - quadUv) / max(fwidth(quadUv), vec2(0.00001));
    float edgeDistance = min(edgePixels.x, edgePixels.y);
    float coverage = 1.0 - smoothstep(max(0.0, WireWidth - 0.5), WireWidth + 0.5, edgeDistance);

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
        alpha *= coverage;
        color = Tint.rgb * 0.96 + vec3(0.06 * Strength);
    } else if (Style == 5) {
        // Shaded: wrapped key light plus sky fill gives the model volume while keeping its color.
        vec3 key = safeUnit(vec3(-0.4, 0.8, 0.45));
        float wrapped = clamp((dot(normal, key) + 0.45) / 1.45, 0.0, 1.0);
        float sky = 0.5 + 0.5 * normal.y;
        color = Tint.rgb * (0.32 + 0.55 * wrapped + 0.16 * sky) + vec3(rim * 0.3 * Strength);
    } else if (Style == 6) {
        // Hologram: scanlines, a sweeping band and iridescent edges between the two colors.
        float scan = 0.5 + 0.5 * sin(gl_FragCoord.y * 1.35 - Time * 7.0);
        float sweep = fract(viewPosition.y * 0.35 - Time * 0.3) - 0.5;
        float band = exp(-sweep * sweep * 220.0);
        vec3 iris = 0.5 + 0.5 * cos(6.28318 * (rim * 0.7 + Time * 0.05 + vec3(0.0, 0.33, 0.67)));
        color = mix(Tint.rgb, Secondary.rgb, rim) * (0.6 + 0.4 * scan)
                + iris * rim * 0.3 * Strength + vec3(band * 0.55 * Strength);
        alpha *= clamp(0.22 + rim * 0.7 + band * 0.4, 0.0, 1.0) * (0.78 + 0.22 * scan);
    } else if (Style == 7) {
        // Gradient: two colors flowing diagonally across the screen, lightly shaded.
        float flow = 0.5 + 0.5 * sin((gl_FragCoord.x + gl_FragCoord.y) * 0.011 + Time * 1.7);
        vec3 light = safeUnit(vec3(-0.35, 0.75, 0.6));
        float diffuse = clamp((dot(normal, light) + 0.5) / 1.5, 0.0, 1.0);
        color = mix(Tint.rgb, Secondary.rgb, flow) * (0.65 + 0.35 * diffuse) + vec3(rim * 0.25 * Strength);
    } else if (Style == 8) {
        // Rim light: clear center, bright silhouette edge.
        float edge = pow(1.0 - clamp(facing, 0.0, 1.0), 1.5);
        color = Tint.rgb + vec3(edge * 0.3 * Strength);
        alpha *= clamp(edge * (1.15 + 0.6 * Strength), 0.0, 1.0);
    } else if (Style == 9) {
        // Galaxy: a slowly drifting screen-space nebula between the two colors with twinkling stars.
        vec2 p = gl_FragCoord.xy / 180.0;
        float t = Time * 0.03;
        float n = noise(p * 2.0 + vec2(t, -t)) * 0.6 + noise(p * 5.0 - vec2(t * 2.0, 0.0)) * 0.4;
        vec3 nebula = mix(Tint.rgb, Secondary.rgb, smoothstep(0.25, 0.85, n));
        vec2 cell = floor(gl_FragCoord.xy / 3.0);
        float star = step(0.986, hash(cell)) * (0.6 + 0.4 * sin(Time * 3.0 + hash(cell + 7.0) * 40.0));
        color = nebula * (0.35 + 0.45 * n) + vec3(star) + Tint.rgb * rim * 0.55 * Strength;
        alpha *= clamp(0.82 + rim * 0.18, 0.0, 1.0);
    }

    if (Edges > 0.5 && Style != 4) {
        // Optional crisp outline of the model's real face edges over any material.
        vec3 edgeColor = clamp(Tint.rgb * 1.2 + vec3(0.18), 0.0, 1.0);
        color = mix(color, edgeColor, coverage);
        alpha = max(alpha, coverage * Tint.a);
    }

    color = clamp(color, 0.0, 1.0);
    alpha = clamp(alpha, 0.0, 1.0);
    if (alpha < 0.001) discard;
    fragColor = vec4(color * alpha, alpha);
}
