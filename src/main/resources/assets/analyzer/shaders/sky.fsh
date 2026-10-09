#version 150
uniform mat4 InvProjection;
uniform mat4 CameraRotation;
uniform float Time;
uniform float Intensity;
uniform int Stars;
uniform int Preset;
in vec2 uv;
out vec4 fragColor;

float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float noise(vec2 p) {
    vec2 i = floor(p), f = fract(p); f = f*f*(3.0-2.0*f);
    return mix(mix(hash(i), hash(i+vec2(1,0)), f.x), mix(hash(i+vec2(0,1)), hash(i+vec2(1)), f.x), f.y);
}
float cloud(vec2 p) { return noise(p)*0.55 + noise(p*2.07)*0.28 + noise(p*4.17)*0.14; }
void main() {
    vec4 projected = InvProjection * vec4(uv*2.0-1.0, 1.0, 1.0);
    vec3 ray = normalize(mat3(CameraRotation) * projected.xyz);
    if (ray.y < -0.035) discard;
    float longitude = atan(ray.z, ray.x), latitude = asin(clamp(ray.y, -1.0, 1.0));
    vec2 sphere = vec2(longitude, latitude);
    vec3 low = Preset == 2 ? vec3(0.27,0.055,0.035) : vec3(0.025,0.10,0.18);
    vec3 sky = mix(low, vec3(0.006,0.009,0.030), smoothstep(0.0,0.85,latitude));
    float mist = cloud(vec2(cos(longitude)*3.0+Time*0.013, sin(longitude)*3.0+latitude*2.7));
    sky += vec3(0.025,0.022,0.065)*mist;
    vec3 glow = vec3(0.0);
    for (int i=0; i<3; i++) {
        float layer = float(i);
        float fold = 0.19 + layer*0.16 + sin(longitude*3.0 + Time*0.16 + layer*2.1)*0.09
                     + sin(longitude*7.0 - Time*0.12 + layer)*0.035;
        float offset = latitude-fold;
        float streak = 0.34 + 0.66*cloud(vec2(cos(longitude)*33.0+Time*0.1+layer*17.0, sin(longitude)*33.0+latitude*2.0));
        float curtain = smoothstep(-0.012,0.035,offset) * exp(-max(offset,0.0)*7.0) * streak;
        float base = exp(-pow(offset*78.0,2.0))*0.65;
        vec3 green = Preset == 2 ? vec3(1.0,0.26,0.065) : Preset == 1 ? vec3(0.15,0.38,1.0) : vec3(0.045,0.95,0.56);
        vec3 violet = Preset == 2 ? vec3(0.96,0.045,0.31) : vec3(0.58,0.11,0.91);
        vec3 tint = mix(green,violet,smoothstep(0.03,0.37,max(offset,0.0)));
        glow += (curtain*0.6 + base)*tint * (1.0-layer*0.2);
    }
    if (Preset == 1) {
        float gas = pow(cloud(vec2(cos(longitude)*5.0+Time*0.015, sin(longitude)*5.0+latitude*3.0+Time*0.004)), 3.0);
        glow += gas * vec3(0.75,0.12,0.65) * 0.9;
    }
    if (Stars == 1) {
        vec2 starGrid = vec2((longitude/6.2831853+0.5)*720.0,latitude*90.0), cell = floor(starGrid);
        cell.x = mod(cell.x,720.0);
        float seed = hash(cell);
        vec2 center = vec2(hash(cell+3.4),hash(cell+8.1))*0.66+0.17;
        float point = 1.0-smoothstep(0.018,0.074,length(fract(starGrid)-center));
        float twinkle = 0.60 + 0.40*sin(Time*1.5+seed*53.0);
        sky += point*step(0.94,seed)*twinkle*vec3(0.75,0.83,1.0);
    }
    float horizon = smoothstep(-0.02,0.075,ray.y);
    fragColor = vec4(clamp(sky+glow,0.0,1.0), clamp(Intensity,0.0,1.0)*horizon);
}
