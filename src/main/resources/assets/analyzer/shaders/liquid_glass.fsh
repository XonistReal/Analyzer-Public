#version 150

// Original optical UI material. Scene is a separate, immutable copy of the backdrop.
// Nothing in this shader samples the framebuffer currently receiving the glass.
uniform vec4 Rect;
uniform float Radius;
uniform sampler2D Scene;
uniform vec2 Resolution;
uniform vec2 CaptureOrigin;
uniform vec3 Accent;
uniform float Opacity;
uniform float Strength;
uniform int RefractionEnabled;

in vec2 localPoint;
out vec4 fragColor;

float roundedDistance(vec2 point, vec2 halfSize, float r) {
    vec2 q = abs(point) - halfSize + vec2(r);
    return length(max(q, vec2(0.0))) + min(max(q.x, q.y), 0.0) - r;
}

vec2 safeNormal(vec2 value) {
    return value / max(length(value), 0.00001);
}

vec3 frostedScene(vec2 uv, float frost) {
    vec2 step = vec2(frost) / Resolution;
    // Symmetric fixed taps keep glass stationary when menu/HUD motion is disabled.
    vec3 sampleColor = texture(Scene, uv).rgb * 0.28;
    sampleColor += texture(Scene, uv + vec2(step.x, 0.0)).rgb * 0.12;
    sampleColor += texture(Scene, uv - vec2(step.x, 0.0)).rgb * 0.12;
    sampleColor += texture(Scene, uv + vec2(0.0, step.y)).rgb * 0.12;
    sampleColor += texture(Scene, uv - vec2(0.0, step.y)).rgb * 0.12;
    sampleColor += texture(Scene, uv + step).rgb * 0.06;
    sampleColor += texture(Scene, uv - step).rgb * 0.06;
    sampleColor += texture(Scene, uv + vec2(step.x, -step.y)).rgb * 0.06;
    sampleColor += texture(Scene, uv + vec2(-step.x, step.y)).rgb * 0.06;
    return sampleColor;
}

void main() {
    vec2 halfSize = Rect.zw * 0.5;
    vec2 point = localPoint - halfSize;
    float distance = roundedDistance(point, halfSize, Radius);
    float pixel = max(fwidth(distance), 0.0001);
    float coverage = 1.0 - smoothstep(-pixel * 0.65, pixel * 0.65, distance);

    // Resolve the curved surface normal in framebuffer pixels, including DrawContext
    // transforms and GUI scaling. Screen Y points upward while local UI Y points down.
    float normalStep = max(pixel * 0.75, 0.0001);
    vec2 normalLocal = safeNormal(vec2(
            roundedDistance(point + vec2(normalStep, 0.0), halfSize, Radius)
                    - roundedDistance(point - vec2(normalStep, 0.0), halfSize, Radius),
            roundedDistance(point + vec2(0.0, normalStep), halfSize, Radius)
                    - roundedDistance(point - vec2(0.0, normalStep), halfSize, Radius)));
    vec2 normal = safeNormal(vec2(dot(normalLocal, dFdx(localPoint)), dot(normalLocal, dFdy(localPoint))));

    float bevel = min(max(Radius * 0.72, pixel * 3.5), min(Rect.z, Rect.w) * 0.23 + pixel * 5.0);
    float inward = max(-distance, 0.0);
    float edgeBand = 1.0 - smoothstep(0.0, max(bevel, pixel), inward);
    float lens = pow(edgeBand, 1.5) * (5.0 + min(bevel / pixel, 16.0) * 0.9);
    float dome = (1.0 - edgeBand) * exp(-inward / max(bevel * 2.8, pixel)) * 1.6;
    float optical = RefractionEnabled == 1 ? Strength : 0.0;
    vec2 uv = (gl_FragCoord.xy - CaptureOrigin) / Resolution;
    vec2 refractedUv = uv - normal * (lens + dome) * optical / Resolution;

    float frost = mix(2.0, 0.7, edgeBand);
    vec3 transmitted = frostedScene(refractedUv, frost);
    // A restrained dispersion at the strongly curved edge produces a real optical
    // difference while keeping the interior and all subsequent native text crisp.
    vec2 dispersion = normal * edgeBand * optical * 0.8 / Resolution;
    transmitted.r = mix(transmitted.r, texture(Scene, refractedUv + dispersion).r, edgeBand * 0.6);
    transmitted.b = mix(transmitted.b, texture(Scene, refractedUv - dispersion).b, edgeBand * 0.6);

    float vertical = clamp(localPoint.y / max(Rect.w, pixel), 0.0, 1.0);
    float luminance = dot(transmitted, vec3(0.2126, 0.7152, 0.0722));
    transmitted = mix(vec3(luminance), transmitted, 0.87);
    // Smoked body for label contrast; clearer bevels show the captured scene bending.
    float transmission = mix(0.50, 0.82, edgeBand);
    vec3 body = transmitted * transmission + vec3(0.025, 0.028, 0.036);
    body += Accent * (0.010 + edgeBand * 0.018);

    vec2 light = normalize(vec2(-0.46, 0.89));
    float facingLight = pow(max(dot(normal, light), 0.0), 3.0);
    float rim = exp(-abs(distance + pixel * 0.75) / (pixel * 1.05));
    float innerRim = exp(-abs(distance + bevel * 0.66) / max(pixel * 1.25, 0.0001));
    float fresnel = pow(edgeBand, 2.3);
    float highlight = rim * (0.14 + facingLight * 0.61)
                    + fresnel * facingLight * 0.12 + innerRim * facingLight * 0.085;
    float diagonal = exp(-pow((localPoint.x / max(Rect.z, pixel) + vertical * 0.45 - 0.29) / 0.19, 2.0));
    body += vec3(0.90, 0.95, 1.0) * (highlight + diagonal * pow(1.0 - vertical, 3.0) * 0.027);
    body = clamp(body, vec3(0.0), vec3(1.0));

    float surfaceAlpha = coverage * clamp(Opacity + rim * 0.10, 0.0, 1.0);
    vec4 surface = vec4(body * surfaceAlpha, surfaceAlpha);
    float shiftedDistance = roundedDistance(point - vec2(0.0, pixel * 2.4), halfSize, Radius);
    float shadowAlpha = exp(-pow(max(shiftedDistance, 0.0) / max(pixel * 5.5, 0.0001), 2.0)) * 0.22 * Opacity;
    vec4 shadow = vec4(vec3(0.0), shadowAlpha);
    fragColor = surface + shadow * (1.0 - surface.a);
    if (fragColor.a < 0.001) discard;
}
