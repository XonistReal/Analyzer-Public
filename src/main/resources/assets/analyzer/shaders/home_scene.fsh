#version 150

uniform sampler2D Scene;
uniform vec2 Resolution;
uniform int Blur;
uniform int Kind;
in vec2 uv;
out vec4 fragColor;

void main() {
    vec3 color;
    if (Blur == 1) {
        vec3 sum = vec3(0.0);
        float weightSum = 0.0;
        for (int x = -2; x <= 2; x++) for (int y = -2; y <= 2; y++) {
            float weight = exp(-float(x * x + y * y) * 0.40);
            vec2 offset = vec2(x, y) * 7.5 / Resolution;
            sum += texture(Scene, clamp(uv + offset, vec2(0.0), vec2(1.0))).rgb * weight;
            weightSum += weight;
        }
        color = sum / weightSum;
    } else color = texture(Scene, uv).rgb;

    if (Kind == 0) {
        // Minecraft owns the landscape. A quiet, warm neutral grade keeps its identity.
        color = mix(color, color * vec3(1.025, 1.0, 0.92), 0.40);
        float navigationVeil = 1.0 - smoothstep(0.08, 0.39, uv.x);
        color *= 0.93 - navigationVeil * 0.45;
        color = mix(color, vec3(0.050, 0.045, 0.038), Blur == 1 ? 0.15 : 0.04);
    } else if (Blur == 1) color *= 0.78;
    fragColor = vec4(color, 1.0);
}
