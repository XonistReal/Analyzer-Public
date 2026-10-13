#version 150

uniform vec2 Resolution;
uniform float Time;
in vec2 uv;
out vec4 fragColor;

// Original stylized orbital artwork: analytical layers, not a relativistic simulation.
float hash(vec2 point) {
    return fract(sin(dot(point, vec2(127.1, 311.7))) * 43758.5453);
}

float noise(vec2 point) {
    vec2 cell = floor(point), local = fract(point);
    local = local * local * (3.0 - 2.0 * local);
    return mix(mix(hash(cell), hash(cell + vec2(1.0, 0.0)), local.x),
               mix(hash(cell + vec2(0.0, 1.0)), hash(cell + vec2(1.0)), local.x), local.y);
}

float bell(float value, float width) {
    return exp(-value * value / max(width * width, 0.0000001));
}

vec3 starfield(vec2 point) {
    vec3 color = vec3(0.0);
    for (int i = 0; i < 2; i++) {
        float layer = float(i);
        vec2 field = point * Resolution / (28.0 + 23.0 * layer);
        vec2 cell = floor(field);
        vec2 center = vec2(hash(cell + 12.0), hash(cell + 53.0));
        float distance = length(fract(field) - center);
        float star = (1.0 - smoothstep(0.012, 0.041 + layer * 0.018, distance))
                   * step(0.90 + layer * 0.035, hash(cell));
        float twinkle = 0.92 + 0.08 * sin(Time * 0.34 + hash(cell + 8.0) * 30.0);
        color += star * vec3(0.45, 0.53, 0.68) * twinkle * (0.34 - layer * 0.10);
    }
    return color;
}

void main() {
    // Aspect-aware coordinates keep the hole round at every tested window size.
    vec2 point = (uv - vec2(0.635, 0.51)) * vec2(Resolution.x / Resolution.y, 1.0) * 2.0;
    float radius = length(point);
    float aa = max(2.0 / Resolution.y, 0.0005);
    float tilt = 0.245;
    vec2 orbit = mat2(cos(tilt), -sin(tilt), sin(tilt), cos(tilt)) * point;
    vec3 amber = vec3(1.00, 0.43, 0.08);
    vec3 ivory = vec3(1.00, 0.87, 0.55);

    // A slight radial deflection bends the sparse stars near the lens.
    vec2 stars = uv + point * (0.003 / max(radius * radius, 0.045));
    vec3 color = vec3(0.003, 0.004, 0.008) + starfield(stars);
    float haze = exp(-dot(orbit * vec2(0.85, 3.8), orbit * vec2(0.85, 3.8)) * 3.5);
    color += amber * haze * 0.017;

    // The distant disk is a flattened annulus. Fine circular bands drift slowly.
    vec2 diskPoint = orbit * vec2(1.0, 4.5);
    float diskRadius = length(diskPoint);
    float angle = atan(diskPoint.y, diskPoint.x);
    float bands = 0.58 + 0.21 * sin(diskRadius * 126.0 + sin(angle * 9.0 - Time * 0.05) * 3.0)
                       + 0.16 * noise(vec2(diskRadius * 78.0, sin(angle) * 8.0 + Time * 0.014));
    float diskBounds = smoothstep(0.19, 0.26, diskRadius) * (1.0 - smoothstep(0.72, 0.95, diskRadius));
    float diskBrightness = exp(-max(diskRadius - 0.22, 0.0) * 4.0) * diskBounds;
    float sideLight = 0.70 + 0.47 * clamp(orbit.x / max(diskRadius, 0.001), -1.0, 1.0);
    vec3 disk = mix(amber, ivory, exp(-max(diskRadius - 0.20, 0.0) * 10.0));
    color += disk * diskBrightness * bands * sideLight * 1.55;

    // An upper and lower curved arc give the disk its lensed silhouette.
    float photonRadius = 0.244;
    float lens = bell(radius - photonRadius, 0.016);
    float outerGlow = bell(radius - 0.270, 0.060);
    float upper = smoothstep(-0.04, 0.18, orbit.y);
    color += amber * outerGlow * (0.16 + upper * 0.23);
    color += ivory * lens * (0.58 + upper * 1.35);
    color += ivory * bell(radius - 0.235, aa * 1.8) * (0.85 + upper * 1.60);

    // The dark core occludes all distant layers. A narrow near-side disk passes in front.
    float core = 1.0 - smoothstep(0.222 - aa, 0.222 + aa, radius);
    color = mix(color, vec3(0.001, 0.001, 0.002), core);
    float frontCurve = orbit.y + 0.055 + 0.085 * exp(-orbit.x * orbit.x * 10.0);
    float front = bell(frontCurve, 0.018 + 0.016 * abs(orbit.x))
                * (1.0 - smoothstep(0.68, 0.94, abs(orbit.x)));
    float frontBands = 0.76 + 0.18 * sin(orbit.x * 135.0 + Time * 0.06)
                           + 0.06 * noise(vec2(orbit.x * 80.0, Time * 0.012));
    color += mix(amber, ivory, exp(-orbit.x * orbit.x * 7.0)) * front * frontBands * 1.9;
    color += ivory * bell(frontCurve, 0.0045 + aa) * exp(-orbit.x * orbit.x * 2.8) * 1.1;

    // Restrained tone mapping preserves a black background and warm white highlight.
    color = 1.0 - exp(-color * 1.28);
    float navigationVeil = 1.0 - smoothstep(0.08, 0.37, uv.x);
    color *= 1.0 - navigationVeil * 0.50;
    float vignette = 1.0 - smoothstep(0.35, 0.96, length((uv - 0.5) * vec2(1.1, 1.0)));
    color *= 0.64 + 0.36 * vignette;
    fragColor = vec4(color, 1.0);
}
