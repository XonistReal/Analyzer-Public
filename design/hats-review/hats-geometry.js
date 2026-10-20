/* Original Analyzer concept geometry. Units are Minecraft skin pixels.
 * This review source has no dependency on any Temple/Lunar cosmetic assets.
 * createDesigns receives the pinned Three.js API; no renderer or network is owned here.
 */
function createAnalyzerHatDesigns(THREE) {
  'use strict';
  const TAU = Math.PI * 2;
  const MAX_VERTICES = 180000, MAX_MESHES = 1200;
  const V = (x,y,z) => new THREE.Vector3(x,y,z);
  const metal = (color, roughness=.28) => new THREE.MeshStandardMaterial({color, metalness:.82, roughness});
  const gold = metal('#dcb65b'), paleGold = metal('#f2dda1',.2), oldGold = metal('#77501e',.4);
  const silver = metal('#cbd9e1',.2), darkSilver = metal('#465061',.3), roseGold = metal('#cb9f92');
  const ruby = new THREE.MeshPhysicalMaterial({color:'#9b1634',metalness:.12,roughness:.17,clearcoat:1,clearcoatRoughness:.1});
  const sapphire = ruby.clone(); sapphire.color.set('#2867ad');
  const emerald = ruby.clone(); emerald.color.set('#1d826d');
  const crystal = new THREE.MeshPhysicalMaterial({color:'#c4c2f4',metalness:.13,roughness:.12,clearcoat:1,transparent:true,opacity:.9});
  const pearl = new THREE.MeshPhysicalMaterial({color:'#eee8dd',metalness:.06,roughness:.23,clearcoat:.8});
  const lightGold = new THREE.MeshStandardMaterial({color:'#fff0a6',emissive:'#e5ae2f',emissiveIntensity:1.2,roughness:.3,metalness:.35});
  const lightViolet = new THREE.MeshStandardMaterial({color:'#ded4fa',emissive:'#8472b7',emissiveIntensity:.7,roughness:.2,metalness:.2});
  const velvet = new THREE.MeshStandardMaterial({color:'#741a35',roughness:.94,metalness:0});
  let randomSeed = 982451653;
  const random = () => { randomSeed = (Math.imul(randomSeed,1664525)+1013904223)>>>0; return randomSeed/4294967296; };
  // A deterministic, locally authored weave; no downloaded textures.
  if (typeof document !== 'undefined') {
    const cloth = document.createElement('canvas'); cloth.width=cloth.height=128;
    const ctx=cloth.getContext('2d'), pixels=ctx.createImageData(128,128);
    for(let i=0;i<pixels.data.length;i+=4) {
      const cell=i/4, x=cell%128,y=Math.floor(cell/128), n=112+Math.floor(random()*36)+((x+y)%3===0?12:0);
      pixels.data[i]=pixels.data[i+1]=pixels.data[i+2]=n; pixels.data[i+3]=255;
    }
    ctx.putImageData(pixels,0,0);
    velvet.bumpMap=new THREE.CanvasTexture(cloth); velvet.bumpMap.wrapS=velvet.bumpMap.wrapT=THREE.RepeatWrapping;
    velvet.bumpMap.repeat.set(5,3); velvet.bumpScale=.035;
  }
  const materials=[gold,paleGold,oldGold,silver,darkSilver,roseGold,ruby,sapphire,emerald,crystal,pearl,lightGold,lightViolet,velvet];
  const root = name => { const group=new THREE.Group(); group.name=name; group.userData.animators=[]; return group; };
  const mesh=(g,geometry,material,pos,rot) => {
    const m=new THREE.Mesh(geometry,material); if(pos)m.position.set(...pos);if(rot)m.rotation.set(...rot);
    m.castShadow=true; m.receiveShadow=true;g.add(m);return m;
  };
  const sphere=(g,r,p,material=pearl,segments=10) => mesh(g,new THREE.SphereGeometry(r,segments,8),material,p);
  const box=(g,s,p,mat=gold,rot) => mesh(g,new THREE.BoxGeometry(...s),mat,p,rot);
  const tube=(g,points,r,mat=gold,segments=32,closed=false,sides=6) => {
    const curve=new THREE.CatmullRomCurve3(points.map(p=>V(...p)),closed,'centripetal');
    return mesh(g,new THREE.TubeGeometry(curve,segments,r,sides,closed),mat);
  };
  const circlePoints=(r,y,n=80,start=0,length=TAU) => Array.from({length:n+1},(_,i)=>{const t=start+i/n*length;return [r*Math.cos(t),y,r*Math.sin(t)];});
  const torus=(g,r,tubeRadius,y,mat=gold) => mesh(g,new THREE.TorusGeometry(r,tubeRadius,8,96),mat,[0,y,0],[Math.PI/2,0,0]);
  function annular(g,outer,inner,height,y,mat=gold,bevel=.06,start=0,length=TAU) {
    const shape=new THREE.Shape(), n=Math.max(4,Math.ceil(length*14));
    for(let i=0;i<=n;i++){const a=start+i/n*length; const x=outer*Math.cos(a),z=outer*Math.sin(a);i?shape.lineTo(x,z):shape.moveTo(x,z);}
    for(let i=n;i>=0;i--){const a=start+i/n*length;shape.lineTo(inner*Math.cos(a),inner*Math.sin(a));}shape.closePath();
    const geo=new THREE.ExtrudeGeometry(shape,{depth:height,bevelEnabled:true,bevelSize:bevel,bevelThickness:bevel,bevelSegments:2,steps:1,curveSegments:2});
    return mesh(g,geo,mat,[0,y+height,0],[Math.PI/2,0,0]);
  }
  const pointOnRing=(r,y,a)=>[r*Math.cos(a),y,r*Math.sin(a)];
  function jewel(g,angle,y,radius,size,material) {
    const p=pointOnRing(radius,y,angle), mount=new THREE.Group(); mount.position.set(...p);mount.rotation.y=-angle+Math.PI/2;g.add(mount);
    const bezel=mesh(mount,new THREE.TorusGeometry(size*.75,.075,6,12),paleGold,[0,0,0]); bezel.scale.y=1.25;
    const gem=mesh(mount,new THREE.IcosahedronGeometry(size,0),material,[0,0,.08]);gem.scale.set(.74,1,.43);
    for(let i=0;i<4;i++){const a=TAU*i/4; sphere(mount,.065,[Math.cos(a)*size*.67,Math.sin(a)*size*.86,.14],paleGold,6);}
    return mount;
  }
  function rune(g,angle,y,radius,index) {
    const branch=new THREE.Group();branch.position.set(...pointOnRing(radius,y,angle));branch.rotation.y=-angle+Math.PI/2;g.add(branch);
    const patterns=[ [[-.22,0,-.26],[.22,0,.26]],[[-.22,0,.24],[0,0,-.24],[.22,0,.24]],[[0,0,-.3],[0,0,.3]], [[-.2,0,-.22],[.2,0,-.22],[0,0,.25],[-.2,0,-.22]] ];
    const path=patterns[index%patterns.length];tube(branch,path,.035,oldGold,8,false,4);
    if(index%2===0) tube(branch,[[-.2,0,.03],[.2,0,.03]],.03,oldGold,4,false,4);
  }
  function sunforgedHalo() {
    const g=root('Sunforged halo');
    // The complete tilted ring remains above head-top Y=8, even at its low edge.
    const ring=new THREE.Group(); ring.position.y=10.9;ring.rotation.z=.16;ring.rotation.x=.065;g.add(ring);
    const n=24;
    for(let i=0;i<n;i++) {
      const start=i/n*TAU+.016, width=TAU/n-.032;
      annular(ring,6.15,4.92,.43,0,i%3===0?paleGold:gold,.055,start,width);
      const a=start+width/2;rune(ring,a,.5,5.55,i);
      const stud=sphere(ring,.15,pointOnRing(6.12,.23,a),i%3===0?oldGold:paleGold,6);stud.scale.y=.62;
    }
    torus(ring,4.94,.065,.25,lightGold);torus(ring,6.16,.045,.12,paleGold);torus(ring,5.07,.04,.5,paleGold);
    torus(ring,5.9,.035,.51,oldGold);
    for(let i=0;i<72;i++)sphere(ring,.045,pointOnRing(5.17,.49,i/72*TAU),paleGold,5);
    for(let i=0;i<12;i++) {
      const a=i/12*TAU, decoration=new THREE.Group(); decoration.position.set(...pointOnRing(6.15,.24,a)); decoration.rotation.y=-a;ring.add(decoration);
      mesh(decoration,new THREE.OctahedronGeometry(.21,0),oldGold,[0,0,0],[Math.PI/4,0,Math.PI/4]);
      sphere(decoration,.065,[.14,0,0],lightGold,6);
    }
    const particles=new THREE.Group();g.add(particles);
    for(let i=0;i<16;i++){
      const a=random()*TAU,r=5.4+random()*2.2,y=9.6+random()*3.2;
      const spark=mesh(particles,new THREE.OctahedronGeometry(.045+random()*.045,0),lightGold,pointOnRing(r,y,a));
      spark.userData={a,r,y,speed:.17+random()*.2,phase:random()*TAU};
    }
    g.userData.animators.push(time=>{ ring.position.y=10.9+Math.sin(time*.7)*.1;ring.rotation.y=time*.035;for(const p of particles.children){const d=p.userData,a=d.a+time*d.speed;p.position.set(Math.cos(a)*d.r,d.y+Math.sin(time*.85+d.phase)*.3,Math.sin(a)*d.r);p.scale.setScalar(.55+.45*Math.sin(time*1.3+d.phase)**2);}});
    return g;
  }
  function sovereignCrown() {
    const g=root('Sovereign crown');
    g.position.y=.04;
    annular(g,5.78,5.06,.76,8.13,gold,.08);
    torus(g,5.48,.14,8.13,silver);torus(g,5.6,.11,8.27,paleGold);torus(g,5.67,.11,9.02,paleGold);
    const cap=mesh(g,new THREE.SphereGeometry(4.91,48,24,0,TAU,0,Math.PI/2),velvet,[0,9.02,0]);cap.scale.y=.86;
    // Eight fabric seams with a restrained metal thread.
    for(let j=0;j<8;j++){
      const a=j/8*TAU, points=[];
      for(let k=0;k<=18;k++){const t=k/18*Math.PI/2;points.push([4.94*Math.cos(t)*Math.cos(a),9.02+4.25*Math.sin(t),4.94*Math.cos(t)*Math.sin(a)]);}
      tube(g,points,.027,oldGold,24,false,4);
    }
    // A paired gold lattice, rather than a solid yellow belt.
    for(let i=0;i<24;i++){
      const a=i/24*TAU,b=(i+1)/24*TAU,m=(a+b)/2;
      tube(g,[pointOnRing(5.8,8.35,a),pointOnRing(5.81,8.89,m),pointOnRing(5.8,8.35,b)],.034,paleGold,12,false,4);
      tube(g,[pointOnRing(5.8,8.91,a),pointOnRing(5.81,8.37,m),pointOnRing(5.8,8.91,b)],.025,oldGold,12,false,4);
    }
    for(let i=0;i<80;i++){
      const a=i/80*TAU;sphere(g,.071,pointOnRing(5.57,8.1,a),pearl,6);sphere(g,.052,pointOnRing(5.69,9.07,a),paleGold,6);
    }
    for(let i=0;i<8;i++){
      const a=i/8*TAU; jewel(g,a,8.66,5.85,.4,[ruby,sapphire,emerald,sapphire][i%4]);
      const front=new THREE.Group();front.position.set(...pointOnRing(5.5,9.06,a));front.rotation.y=-a+Math.PI/2;g.add(front);
      tube(front,[[-.65,0,0],[-.65,.45,0],[-.32,.75,0],[0,1.32,0],[.32,.75,0],[.65,.45,0],[.65,0,0]],.095,gold,24,false,6);
      tube(front,[[-.38,.18,.015],[0,.75,.06],[.38,.18,.015]],.035,paleGold,12,false,4);
      sphere(front,.14,[0,1.34,0],pearl,8);
    }
    // Two crossing arches form four arms. Their double rails carry individual pearls.
    for(let j=0;j<2;j++) {
      const a=j*Math.PI/2;
      for(const side of [-1,1]){
        const points=[];for(let k=0;k<=40;k++){const t=k/40*Math.PI,r=5.17*Math.cos(t),offset=side*.15;points.push([r*Math.cos(a)+offset*Math.sin(a),9.74+4.4*Math.sin(t),r*Math.sin(a)-offset*Math.cos(a)]);}
        tube(g,points,.095,paleGold,56,false,6);
      }
      for(let i=0;i<15;i++) {
        const t=(i+.5)/15*Math.PI,r=5.17*Math.cos(t),y=9.74+4.4*Math.sin(t);
        sphere(g,.104,[r*Math.cos(a),y+.12,r*Math.sin(a)],pearl,8);
        if(i%2===0){const knot=mesh(g,new THREE.OctahedronGeometry(.19,0),gold,[r*Math.cos(a),y-.02,r*Math.sin(a)]);knot.scale.y=.42;}
      }
    }
    // A small original orb and four-point finial at the cross-arch summit.
    sphere(g,.4,[0,14.23,0],gold,14);torus(g,.37,.045,14.24,paleGold);
    box(g,[.15,1.05,.15],[0,15.1,0],paleGold);box(g,[.72,.15,.15],[0,15.17,0],paleGold);
    sphere(g,.1,[0,15.67,0],ruby,8);sphere(g,.075,[-.39,15.17,0],pearl,8);sphere(g,.075,[.39,15.17,0],pearl,8);
    const glints=[];
    for(let i=0;i<6;i++){const a=i/6*TAU;const p=sphere(g,.052,pointOnRing(5.87,8.69,a),lightGold,6);glints.push(p);}
    g.userData.animators.push(time=>glints.forEach((p,i)=>p.scale.setScalar(.4+.6*Math.sin(time*.8+i*1.9)**12)));
    return g;
  }
  function astralDiadem() {
    const g=root('Astral diadem');
    annular(g,5.6,5.08,.28,8.13,darkSilver,.05);
    torus(g,5.52,.055,8.15,silver);torus(g,5.5,.045,8.45,roseGold);
    for(let i=0;i<48;i++){const a=i/48*TAU;sphere(g,.045,pointOnRing(5.6,8.31,a),silver,5);}
    // Seven open-work pedestals lift individual double-ended, faceted crystals.
    const prisms=[];
    for(let i=0;i<7;i++){
      const a=Math.PI*.14+i/6*Math.PI*.72, h=1.4+2.25*Math.sin(i/6*Math.PI), r=5.35;
      const mount=new THREE.Group();mount.position.set(...pointOnRing(r,8.42,a));mount.rotation.y=-a+Math.PI/2;g.add(mount);
      tube(mount,[[-.46,0,0],[-.34,h*.43,0],[0,h*.63,0],[.34,h*.43,0],[.46,0,0]],.07,roseGold,20,false,5);
      tube(mount,[[0,0,.04],[0,h*.44,.04]],.045,silver,10,false,4);
      const gem=mesh(mount,new THREE.OctahedronGeometry(.64,0),crystal,[0,h*.77,0]);gem.scale.set(.52,h*.55,.52);gem.rotation.z=(i-3)*-.07;
      const heart=mesh(mount,new THREE.OctahedronGeometry(.18,0),lightViolet,[0,h*.77,.04]);heart.scale.y=h*.7;
      sphere(mount,.085,[0,h*.2,.1],pearl,8);prisms.push({gem,heart,h,phase:i*.72});
    }
    // Fine interlaced filigree around the back, with a deliberate open front.
    for(let j=0;j<12;j++){
      const a=TAU*j/12,b=a+TAU/12;
      tube(g,[pointOnRing(5.52,8.45,a),pointOnRing(5.46,9.03,(2*a+b)/3),pointOnRing(5.46,9.03,(a+2*b)/3),pointOnRing(5.52,8.45,b)],.034,roseGold,18,false,4);
    }
    const orbit=new THREE.Group();orbit.position.y=10.6;orbit.rotation.x=.22;g.add(orbit);
    tube(orbit,circlePoints(6.2,0,80,-Math.PI*.05,Math.PI*1.2),.025,silver,96,false,4);
    const stars=[];
    for(let i=0;i<7;i++){const a=-Math.PI*.05+i/6*Math.PI*1.2;const star=mesh(orbit,new THREE.OctahedronGeometry(i%2?.09:.14,0),lightViolet,pointOnRing(6.2,0,a));star.scale.y=1.5;stars.push(star);}
    g.userData.animators.push(time=>{orbit.rotation.y=Math.sin(time*.16)*.08;stars.forEach((p,i)=>p.scale.setScalar(.7+.3*Math.sin(time*.9+i)**2));prisms.forEach(p=>{p.heart.scale.x=p.heart.scale.z=.85+.15*Math.sin(time*.65+p.phase);});});
    return g;
  }
  const designs=[
    {id:'sunforged-halo',name:'Sunforged halo',summary:'Beveled gold segments · inset runes · warm inner light · drifting sparks',group:sunforgedHalo()},
    {id:'sovereign-crown',name:'Sovereign crown',summary:'Woven velvet · crossed pearl arches · faceted jewels · braided goldwork',group:sovereignCrown()},
    {id:'astral-diadem',name:'Astral diadem',summary:'Open rose-gold filigree · seven crystal spires · a fine orbiting star arc',group:astralDiadem()}
  ];
  for(const design of designs){
    let meshes=0,vertices=0,triangles=0;
    design.group.traverse(obj=>{if(!obj.isMesh)return;meshes++;const geometry=obj.geometry;vertices+=geometry.attributes.position.count;triangles+=(geometry.index?geometry.index.count:geometry.attributes.position.count)/3;
      const p=geometry.attributes.position.array;for(let i=0;i<p.length;i++)if(!Number.isFinite(p[i]))throw new Error('Non-finite concept vertex');});
    if(meshes>MAX_MESHES||vertices>MAX_VERTICES)throw new Error('Concept geometry cap exceeded');
    design.bounds=new THREE.Box3().setFromObject(design.group); design.counts={meshes,vertices,triangles};
    design.group.userData.id=design.id;
  }
  return {designs,materials,limits:{vertices:MAX_VERTICES,meshes:MAX_MESHES},animate(time){if(!Number.isFinite(time))return;for(const d of designs)for(const animate of d.group.userData.animators)animate(time);},dispose(){const gs=new Set(),ms=new Set(materials);for(const d of designs)d.group.traverse(o=>{if(o.isMesh){gs.add(o.geometry);for(const m of (Array.isArray(o.material)?o.material:[o.material]))ms.add(m);}});gs.forEach(g=>g.dispose());ms.forEach(m=>{if(m.bumpMap)m.bumpMap.dispose();m.dispose();});}};
}
