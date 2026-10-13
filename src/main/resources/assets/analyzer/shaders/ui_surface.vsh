#version 150

uniform mat4 ProjMat;
uniform mat4 ModelMat;
uniform vec4 Rect;
uniform float Padding;

out vec2 localPoint;

void main() {
    // Six generated vertices: no persistent vertex buffers or GUI library needed.
    vec2 corner;
    if (gl_VertexID == 0 || gl_VertexID == 3) corner = vec2(0.0, 0.0);
    else if (gl_VertexID == 1) corner = vec2(1.0, 0.0);
    else if (gl_VertexID == 2 || gl_VertexID == 4) corner = vec2(1.0, 1.0);
    else corner = vec2(0.0, 1.0);
    localPoint = corner * (Rect.zw + vec2(Padding * 2.0)) - vec2(Padding);
    gl_Position = ProjMat * ModelMat * vec4(Rect.xy + localPoint, 0.0, 1.0);
}
