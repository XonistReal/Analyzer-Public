#version 150
uniform vec2 Resolution;
uniform float Time;
uniform int Accent;
uniform sampler2D Scene;
uniform int UseScene;
in vec2 uv;
out vec4 fragColor;
float hash(vec2 p) { return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453); }
float noise(vec2 p) {
    vec2 i=floor(p),f=fract(p); f=f*f*(3.0-2.0*f);
    return mix(mix(hash(i),hash(i+vec2(1,0)),f.x),mix(hash(i+vec2(0,1)),hash(i+vec2(1)),f.x),f.y);
}
void main() {
    vec2 p=uv;
    vec3 color=vec3(0.017,0.025,0.043);
    vec3 primary=Accent==2?vec3(0.95,0.56,0.12):Accent==1?vec3(0.48,0.19,0.91):vec3(0.03,0.61,0.40);
    vec3 secondary=Accent==2?vec3(0.38,0.16,0.025):vec3(0.38,0.08,0.71);
    for(int i=0;i<3;i++) {
        float fi=float(i);
        float wave=sin(p.x*5.2+Time*0.20+fi*2.0)*0.105+sin(p.x*13.0-Time*0.15+fi)*0.026;
        float center=0.37+fi*0.14+wave;
        float d=p.y-center;
        float curtain=smoothstep(-0.02,0.028,d)*exp(-max(d,0.0)*10.0);
        float streak=0.32+noise(vec2(p.x*93.0+Time*0.15+fi*24.0,p.y*4.0))*0.68;
        float edge=exp(-pow(d*90.0,2.0));
        color+=(curtain*streak*0.27+edge*0.15)*mix(primary,secondary,smoothstep(0.02,0.18,d));
    }
    color+=secondary*exp(-length((p-vec2(0.8,0.4))*vec2(2.0,2.7))*3.0)*0.22;
    vec2 cell=floor(p*Resolution/20.0),local=fract(p*Resolution/20.0)-0.5;
    float star=(1.0-smoothstep(0.025,0.080,length(local)))*step(0.97,hash(cell));
    color+=star*vec3(0.40,0.65,0.73)*(0.6+0.4*sin(Time+hash(cell)*55.0));
    float edge=1.0-smoothstep(0.18,0.95,length(p-0.5));
    color*=0.62+0.38*edge;
    if (UseScene == 1) {
        vec3 blurred=vec3(0.0); float total=0.0;
        for (int x=-2;x<=2;x++) for (int y=-2;y<=2;y++) {
            float weight=exp(-float(x*x+y*y)*0.35);
            blurred+=texture(Scene,clamp(uv+vec2(x,y)*9.0/Resolution,vec2(0),vec2(1))).rgb*weight;
            total+=weight;
        }
        color=color*0.85+blurred/total*0.30;
    }
    fragColor=vec4(color,1.0);
}
