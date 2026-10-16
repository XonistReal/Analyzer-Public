#version 150

uniform mat4 ProjMat;

in vec3 Position;
in vec3 Normal;

out vec3 viewPosition;
out vec3 viewNormal;
out vec2 quadUv;

void main() {
    // Both attributes already include the native, animated model/view transform.
    // The capture consumer transforms normals through the normal matrix, not the
    // position matrix, so nonuniform model scaling keeps correct material lighting.
    viewPosition = Position;
    viewNormal = Normal;
    // Indexed quads retain vertices 0,1,2,3. Interpolating their face UVs draws
    // only the four original face edges, never either triangulation diagonal.
    int corner = gl_VertexID & 3;
    quadUv = vec2(corner == 1 || corner == 2 ? 1.0 : 0.0, corner >= 2 ? 1.0 : 0.0);
    gl_Position = ProjMat * vec4(Position, 1.0);
    // CPU-captured view coordinates and the native GPU transform can round the
    // same coplanar face differently. A tiny common eye-facing tolerance keeps
    // those faces visible instead of alternating visible/occluded color stripes.
    gl_Position.z -= 0.000002 * gl_Position.w;
}
