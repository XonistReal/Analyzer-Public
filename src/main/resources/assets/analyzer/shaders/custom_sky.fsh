#version 150
uniform mat4 InvProjection;
uniform mat4 CameraRotation;
uniform sampler2D Panorama;
uniform samplerCube Cube;
uniform int IsCube;
uniform float Yaw;
uniform float Exposure;
uniform float Opacity;
in vec2 uv;
out vec4 fragColor;
void main() {
    vec4 projected = InvProjection * vec4(uv * 2.0 - 1.0, 1.0, 1.0);
    vec3 ray = normalize(mat3(CameraRotation) * projected.xyz);
    float c = cos(Yaw), s = sin(Yaw);
    ray.xz = mat2(c, -s, s, c) * ray.xz;
    // Cube images use OpenGL face orientation with top-row first; panorama V follows latitude.
    vec3 color;
    if (IsCube == 1) color = texture(Cube, ray).rgb;
    else color = texture(Panorama, vec2(atan(ray.z, ray.x) / 6.28318530718 + .5,
                                      .5 - asin(clamp(ray.y, -1.0, 1.0)) / 3.14159265359)).rgb;
    fragColor = vec4(clamp(color * Exposure, 0.0, 1.0), Opacity);
}
