#version 150

uniform vec4 Rect;
uniform float Radius;
uniform vec4 TopColor;
uniform vec4 BottomColor;
uniform vec4 ShadowColor;
uniform float ShadowBlur;
uniform vec4 GlowColor;
uniform float GlowBlur;

in vec2 localPoint;
out vec4 fragColor;

float roundedDistance(vec2 point, vec2 halfSize, float r) {
    vec2 outside = abs(point) - halfSize + vec2(r);
    return length(max(outside, vec2(0.0))) + min(max(outside.x, outside.y), 0.0) - r;
}

vec4 premultiply(vec4 color, float coverage) {
    float alpha = color.a * coverage;
    return vec4(color.rgb * alpha, alpha);
}

vec4 over(vec4 foreground, vec4 background) {
    return foreground + background * (1.0 - foreground.a);
}

void main() {
    vec2 point = localPoint - Rect.zw * 0.5;
    float distance = roundedDistance(point, Rect.zw * 0.5, Radius);
    // Derivatives preserve a one-pixel edge at each Minecraft GUI scale.
    float edge = max(fwidth(distance) * 0.65, 0.0001);
    float coverage = 1.0 - smoothstep(-edge, edge, distance);
    float vertical = clamp(localPoint.y / Rect.w, 0.0, 1.0);
    vec4 surface = premultiply(mix(TopColor, BottomColor, vertical), coverage);

    float shadowDistance = roundedDistance(point - vec2(0.0, 2.5), Rect.zw * 0.5, Radius);
    float shadowFalloff = ShadowBlur > 0.0 ? exp(-pow(max(shadowDistance, 0.0) / max(ShadowBlur, 0.001), 2.0)) : 0.0;
    float glowFalloff = GlowBlur > 0.0 ? exp(-pow(max(distance, 0.0) / max(GlowBlur, 0.001), 2.0)) : 0.0;
    vec4 shadow = premultiply(ShadowColor, shadowFalloff);
    vec4 glow = premultiply(GlowColor, glowFalloff);
    fragColor = over(surface, over(glow, shadow));
    if (fragColor.a < 0.001) discard;
}
