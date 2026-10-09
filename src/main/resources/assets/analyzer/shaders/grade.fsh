#version 150
uniform sampler2D Scene;
uniform vec2 Resolution;
uniform float Strength;
uniform int Preset;
in vec2 uv;
out vec4 fragColor;
vec3 bright(vec2 p) {
    vec3 c = texture(Scene, clamp(p, vec2(0.0), vec2(1.0))).rgb;
    float l = dot(c, vec3(0.2126, 0.7152, 0.0722));
    return c * smoothstep(0.62, 1.0, l);
}
void main() {
    vec3 original = texture(Scene, uv).rgb;
    float strength = clamp(Strength, 0.0, 1.0);
    vec2 pixel = 1.0 / Resolution;
    vec3 bloom = vec3(0.0);
    for (int i = 0; i < 8; i++) {
        float a = float(i) * 0.78539816;
        vec2 direction = vec2(cos(a), sin(a));
        bloom += bright(uv + direction * pixel * 3.0) * 0.075;
        bloom += bright(uv + direction * pixel * 8.0) * 0.050;
    }
    vec3 shadowTint = Preset == 1 ? vec3(1.10, 0.92, 0.90) : Preset == 2 ? vec3(0.92, 1.02, 1.12) : vec3(0.92, 1.07, 1.05);
    vec3 highlightTint = Preset == 1 ? vec3(1.09, 1.01, 0.90) : Preset == 2 ? vec3(0.99, 1.04, 1.10) : vec3(1.04, 0.98, 1.09);
    float luma = dot(original, vec3(0.2126, 0.7152, 0.0722));
    vec3 graded = original * mix(shadowTint, highlightTint, smoothstep(0.15, 0.85, luma));
    graded = mix(vec3(luma), graded, Preset == 2 ? 0.92 : 1.08);
    graded += bloom * 0.20;
    vec2 center = uv * 2.0 - 1.0;
    float vignette = 1.0 - smoothstep(0.35, 1.65, dot(center, center)) * 0.12;
    graded *= vignette;
    fragColor = vec4(mix(original, clamp(graded, 0.0, 1.0), strength), 1.0);
}
