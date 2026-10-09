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
    return c * smoothstep(0.48, 0.94, l);
}
void main() {
    vec3 original = texture(Scene, uv).rgb;
    float strength = clamp(Strength, 0.0, 1.0);
    vec2 pixel = 1.0 / Resolution;
    vec3 bloom = vec3(0.0);
    for (int i = 0; i < 8; i++) {
        float a = float(i) * 0.78539816;
        vec2 direction = vec2(cos(a), sin(a));
        bloom += bright(uv + direction * pixel * 4.0) * 0.075;
        bloom += bright(uv + direction * pixel * 13.0) * 0.050;
        bloom += bright(uv + direction * pixel * 28.0) * 0.025;
    }
    vec3 shadowTint = Preset == 1 ? vec3(1.23, 0.83, 0.70) : Preset == 2 ? vec3(0.75, 1.13, 1.40) : Preset == 3 ? vec3(1.0) : vec3(0.85, 1.24, 1.16);
    vec3 highlightTint = Preset == 1 ? vec3(1.22, 1.00, 0.78) : Preset == 2 ? vec3(0.91, 1.12, 1.28) : Preset == 3 ? vec3(1.0) : vec3(1.20, 0.94, 1.35);
    float luma = dot(original, vec3(0.2126, 0.7152, 0.0722));
    vec3 graded = original * mix(shadowTint, highlightTint, smoothstep(0.15, 0.85, luma));
    graded = mix(vec3(luma), graded, Preset == 2 ? 0.94 : Preset == 3 ? 1.0 : 1.12);
    graded = max(vec3(0), (graded-0.5)*(Preset == 3 ? 1.0 : 1.07)+0.5);
    graded += bloom * (Preset == 3 ? 0.70 : 0.48);
    vec2 center = uv * 2.0 - 1.0;
    float vignette = 1.0 - smoothstep(0.35, 1.65, dot(center, center)) * (Preset == 3 ? 0.04 : 0.18);
    graded *= vignette;
    fragColor = vec4(mix(original, clamp(graded, 0.0, 1.0), strength), 1.0);
}
