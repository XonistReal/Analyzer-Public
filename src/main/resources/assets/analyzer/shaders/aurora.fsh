#version 150
uniform vec2 Resolution;
uniform float Time;
in vec2 uv;
out vec4 fragColor;
void main() {
    vec2 p = uv;
    vec3 color = vec3(0.025, 0.038, 0.053);
    float wave = sin(p.x * 4.2 + Time * 0.14) * 0.08 + sin(p.x * 8.0 - Time * 0.10) * 0.03;
    float ribbon = exp(-pow((p.y - 0.63 - wave) * 5.5, 2.0));
    float violet = exp(-length((p - vec2(0.78, 0.38)) * vec2(2.0, 2.8)) * 2.6);
    float mint = exp(-length((p - vec2(0.22, 0.65)) * vec2(2.2, 3.0)) * 2.2);
    color += vec3(0.02, 0.16, 0.13) * ribbon * mint;
    color += vec3(0.09, 0.04, 0.18) * violet;
    vec2 grid = abs(fract(p * Resolution / 44.0) - 0.5);
    float dots = (1.0 - smoothstep(0.00, 0.018, length(grid))) * 0.04;
    color += dots;
    float grain = fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453);
    color += (grain - 0.5) / 255.0;
    fragColor = vec4(color, 1.0);
}
