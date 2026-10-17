package dev.analyzer.ui;

import dev.analyzer.AnalyzerClient;
import dev.analyzer.core.AnalyzerCore;
import dev.analyzer.core.ModuleDefinition;
import dev.analyzer.core.Setting;
import dev.analyzer.effects.GpuEffects;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.screen.narration.NarrationPart;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Shared native shell, material, focus, persistence and typed settings. */
public abstract class CatalogScreen extends Screen {
    protected static final int W=1000, H=600;
    protected final AnalyzerCore core;
    protected final List<Control> controls=new ArrayList<>();
    protected final Map<String,Point> diagnostics=new HashMap<>();
    protected float scale=1,ox,oy;
    protected double mx,my;
    protected int accent;
    protected String query="",capture=null,status="",focusedControl=null;
    protected boolean searchFocused,selectAll,failed,dirty,layoutRefreshRequested;
    private boolean ignoreSearchShortcutChar;
    protected long changedAt;
    protected Slider slider;
    protected Screen returnParent;
    private String hoveredSetting;
    private final long opened=System.nanoTime();
    private long lastFrame=opened;
    private double frameSeconds=.016;
    private final Map<String,Double> animation=new HashMap<>();
    protected CatalogScreen(AnalyzerCore core,String title){super(Text.literal(title));this.core=core;}
    @Override public boolean shouldPause(){return false;}
    protected abstract String layout();
    protected abstract void body(DrawContext draw);
    protected boolean shaderStyle(){return true;}
    protected float canvasScale(){return Math.max(.15f,Math.min(1.2f,Math.min((width-20f)/W,(height-20f)/H)));}
    protected void backdrop(DrawContext draw){GpuEffects.drawBackdrop(draw);}
    public CatalogScreen inspect(String id){return this;}
    public CatalogScreen withParent(Screen parent){returnParent=parent;return this;}
    protected void editText(String module, Setting<?> setting) {
        if (module.equals("sky") && setting.id().equals("custom-path")) {
            client.setScreen(new SkyboxBrowserScreen(core, this, client.runDirectory.toPath(), path -> {
                core.setValue("sky", "custom-path", path.toAbsolutePath().normalize().toString());
                core.setValue("sky", "source", "Custom"); changed(); AnalyzerClient.saveConfig();
                dev.analyzer.skybox.CustomSkybox.refresh();
            }));
        } else client.setScreen(new TextSettingScreen(core, module, setting, this));
    }
    protected String settingText(String module, Setting<?> setting) {
        String value = core.getChoice(module, setting.id());
        if (!module.equals("sky") || !setting.id().equals("custom-path")) return value;
        if (value.isBlank()) return "Browse images / cube folders…";
        try { return java.nio.file.Path.of(value).getFileName().toString(); }
        catch (RuntimeException failure) { return "Choose another skybox…"; }
    }
    public Screen parentScreen(){return returnParent;}
    public String selectedModuleId(){return null;}
    public List<String> visibleModuleIds(){return matching().stream().map(ModuleDefinition::id).toList();}
    public String queryText(){return query;}
    public void restoreQueryText(String value){query=value==null?"":value;searchChanged();}
    @Override public void render(DrawContext draw,int mouseX,int mouseY,float delta){
        long now=System.nanoTime();frameSeconds=Math.clamp((now-lastFrame)/1e9,.001,.05);lastFrame=now;
        scale=canvasScale();
        ox=(width-W*scale)/2;oy=(height-H*scale)/2;
        if(core.getNumber("menu","motion")>0)oy+=(float)(Math.exp(-(now-opened)/170_000_000.0)*9*scale);
        mx=(mouseX-ox)/scale;my=(mouseY-oy)/scale;
        accent=core.isEnabled("menu")?HudWidgets.accent(core):Palette.MINT;
        controls.clear();diagnostics.clear();hoveredSetting=null;
        try(GuiDraw.GlassScope optical=StudioAuxiliary.backdropScope(draw,theme(),()->backdrop(draw))){
        try(GuiDraw.FontScope font=shaderStyle()?GuiDraw.sansFont():null){
        draw.getMatrices().push();draw.getMatrices().translate(ox,oy,0);draw.getMatrices().scale(scale,scale,1);
        GuiDraw.shaderPanel(draw,10,9,W-20,H-22,24,accent,glass(),theme());
        header(draw);body(draw);footer(draw);
        if(focusedControl!=null)controls.stream().filter(c->c.id.equals(focusedControl)).findFirst().ifPresent(c->
            GuiDraw.roundedBorder(draw,c.area.x-2,c.area.y-2,c.area.w+4,c.area.h+4,5,accent));
        draw.getMatrices().pop();
        }
        if(hoveredSetting!=null)draw.drawTooltip(textRenderer,Text.literal(hoveredSetting),mouseX,mouseY);
        }
    }
    protected String theme(){return core.getChoice("menu","theme");}
    protected int radius(){return theme().equals("Minimal")?6:theme().equals("Classic")?9:16;}
    protected boolean glass(){return core.isEnabled("menu")&&core.getChoice("menu","material").equals("Glass")&&GpuEffects.isHealthy();}
    protected boolean profilesOpen(){return false;}
    protected void header(DrawContext draw){
        BrandAssets.monogram(draw,24,17,35);
        BrandAssets.wordmark(draw,62,13,107);
        label(draw,layout()+" / 1.21.4",63,43,.75f,Palette.MUTED);
        GuiDraw.shaderCard(draw,178,20,273,34,9,accent,glass(),theme(),searchFocused?1:0,searchFocused);
        label(draw,query.isEmpty()?"Search all modules…":GuiDraw.truncate(textRenderer,query,231,.95f),190,31,.95f,query.isEmpty()?Palette.MUTED:Palette.TEXT);
        control("search",new Area(178,20,244,34),0,()->{searchFocused=true;selectAll=false;capture=null;});
        button(draw,"search-clear","×",424,24,22,26,false,()->{query="";searchFocused=false;searchChanged();});
        int x=465;
        for(String name:List.of("Studio","Click GUI")){
            int w=name.equals("Studio")?87:106;
            button(draw,"layout-"+name.toLowerCase(Locale.ROOT),name,x,23,w,28,name.equals(layout())&&!profilesOpen(),()->switchLayout(name));x+=w+6;
        }
        button(draw,"profiles","Profiles",709,23,69,28,profilesOpen(),()->client.setScreen(MenuScreens.profiles(core,this)));
        button(draw,"hud-editor","Edit HUD",784,23,77,28,false,()->client.setScreen(new HudEditorScreen(core,this)));
        button(draw,"automation","Automation",867,23,90,28,false,()->client.setScreen(MenuScreens.automation(core,this)));
        button(draw,"close","×",963,23,25,28,false,this::close);
        label(draw,core.modules().size()+" modules · "+theme()+" theme · "+core.getChoice("menu","accent")+" accent",179,62,.74f,Palette.MUTED);
        button(draw,"appearance","HUD & menu settings",825,56,153,21,false,()->inspect("hud"));
        draw.fill(24,83,976,84,Palette.alpha(Palette.TEXT,28));
    }
    protected void footer(DrawContext draw){
        draw.fill(24,563,976,564,Palette.alpha(Palette.TEXT,28));
        String message=failed?status:dirty?"Saving settings…":status.isEmpty()?"Click a name to inspect · use its switch to enable":status;
        label(draw,GuiDraw.truncate(textRenderer,message,608,.82f),25,578,.82f,failed?Palette.ERROR:Palette.MUTED);
        label(draw,"TAB FOCUS · ENTER APPLY · / SEARCH",722,578,.74f,Palette.MUTED);
        if(failed&&new Area(24,564,640,29).contains(mx,my))hoveredSetting=status;
    }
    protected void searchChanged(){}
    protected List<ModuleDefinition> matching(){
        String filter=query.toLowerCase(Locale.ROOT).strip();
        return core.modules().stream().filter(m->(m.id()+" "+m.name()+" "+m.description()+" "+m.category().label()).toLowerCase(Locale.ROOT).contains(filter)).toList();
    }
    protected void panel(DrawContext draw,Area box,boolean selected){
        GuiDraw.shaderPanel(draw,box.x,box.y,box.w,box.h,radius(),accent,glass(),theme());
        if(selected)GuiDraw.roundedBorder(draw,box.x,box.y,box.w,box.h,radius(),Palette.alpha(accent,170));
    }
    protected void label(DrawContext draw,String value,float x,float y,float size,int color){GuiDraw.label(draw,textRenderer,value,x,y,size,color);}
    protected void button(DrawContext draw,String id,String value,int x,int y,int w,int h,boolean selected,Runnable action){
        Area area=new Area(x,y,w,h);double hover=ease("button:"+id,area.contains(mx,my)?1:0);
        GuiDraw.shaderCard(draw,x,y,w,h,Math.min(8,h/3),accent,glass(),theme(),hover,selected);
        label(draw,value,x+(w-GuiDraw.width(textRenderer,value,.86f))/2,y+(h-9)/2,.86f,Palette.TEXT);
        control(id,area,0,action);
    }
    protected void control(String id,Area area,int button,Runnable action){controls.add(new Control(id,area,button,action));diagnostics.put(id,new Point(area.x+area.w/2.0,area.y+area.h/2.0));}
    protected void clippedControl(String id,Area area,Area clip,int button,Runnable action){Area intersection=area.intersect(clip);if(intersection.w>0&&intersection.h>0)control(id,intersection,button,action);}
    protected void toggle(DrawContext draw,int x,int y,boolean enabled){
        double amount=ease("toggle:"+x+":"+y,enabled?1:0);
        GuiDraw.shaderPill(draw,x,y,32,18,accent,amount);
    }
    protected double ease(String id,double goal){
        double value=animation.getOrDefault(id,goal);
        if(core.getNumber("menu","motion")==0)value=goal;else value+=(goal-value)*(1-Math.exp(-frameSeconds*15));
        if(animation.size()>2048)animation.clear();animation.put(id,value);return value;
    }
    private List<String> settingLines(Setting<?> s,int width){return GuiDraw.wrap(textRenderer,s.label(),width-24,.91f);}
    private int rowHeight(Setting<?> s,int width){return settingLines(s,width).size()*12+(s.kind()==Setting.Kind.BOOLEAN?25:42);}
    protected boolean inlineKeybinds(){return true;}
    protected int settingsHeight(ModuleDefinition module){return settingsHeight(module,280);}
    protected int settingsHeight(ModuleDefinition module,int width){
        List<Setting<?>> fields=SettingSections.ordered(core,module);int total=14+(fields.isEmpty()?36:0);String last=null;
        for(Setting<?> s:fields){String group=SettingSections.group(module,s);if(!group.equals(last))total+=30;total+=rowHeight(s,width);last=group;}return total+(inlineKeybinds()?44:0);
    }
    protected void settings(DrawContext draw,ModuleDefinition module,int x,int y,int width,Area viewport){
        String id=module.id(),lastGroup=null;List<Setting<?>> fields=SettingSections.ordered(core,module);
        for(int index=0;index<fields.size();index++){
            Setting<?> setting=fields.get(index);String group=SettingSections.group(module,setting);
            if(!group.equals(lastGroup)){
                int groupHeight=30;
                for(int next=index;next<fields.size()&&SettingSections.group(module,fields.get(next)).equals(group);next++)groupHeight+=rowHeight(fields.get(next),width);
                GuiDraw.shaderCard(draw,x,y,width,groupHeight-5,radius(),accent,glass(),theme(),0,false);
                label(draw,group.isEmpty()?"Settings":group,x+12,y+10,.94f,Palette.TEXT);y+=30;
            }
            lastGroup=group;List<String> lines=settingLines(setting,width);int pitch=rowHeight(setting,width);
            if(y+pitch>=viewport.y&&y<viewport.bottom()){
                if(viewport.contains(mx,my)&&new Area(x,y,width,pitch).contains(mx,my))hoveredSetting=setting.label();
                for(int line=0;line<lines.size();line++)label(draw,lines.get(line),x+12,y+line*12,.91f,Palette.TEXT);
                int controlY=y+lines.size()*12+4;
                switch(setting.kind()){
                    case BOOLEAN->{
                        boolean value=core.getBoolean(id,setting.id());toggle(draw,x+width-45,controlY-2,value);
                        label(draw,value?"Enabled":"Disabled",x+12,controlY+3,.8f,Palette.MUTED);
                        clippedControl("setting:"+id+":"+setting.id(),new Area(x+6,y,width-12,pitch-5),viewport,0,()->{core.setValue(id,setting.id(),!core.getBoolean(id,setting.id()));changed();});
                    }
                    case CHOICE,TEXT->{
                        Area hit=new Area(x+9,controlY,width-18,26);
                        GuiDraw.shaderCard(draw,hit.x,hit.y,hit.w,hit.h,7,accent,glass(),theme(),hit.contains(mx,my)?1:0,false);
                        label(draw,GuiDraw.truncate(textRenderer,settingText(id,setting),hit.w-28,.91f),hit.x+9,hit.y+8,.91f,accent);
                        label(draw,"›",hit.right()-15,hit.y+7,1,Palette.TEXT);
                        clippedControl("setting:"+id+":"+setting.id(),hit,viewport,0,()->{if(setting.kind()==Setting.Kind.TEXT)editText(id,setting);else cycle(id,setting,1);});
                        clippedControl("setting-back:"+id+":"+setting.id(),hit,viewport,1,()->{if(setting.kind()==Setting.Kind.TEXT)editText(id,setting);else cycle(id,setting,-1);});
                    }
                    case NUMBER->{
                        double value=core.getNumber(id,setting.id());
                        Area track=new Area(x+12,controlY+13,Math.max(18,width-106),4),exact=new Area(x+width-83,controlY,70,27);
                        StudioAuxiliary.surface(draw,track.x,track.y,track.w,4,2,0xFF656169,0xFF656169,0,0,0,0);
                        double amount=(value-setting.min())/Math.max(.000001,setting.max()-setting.min());
                        StudioAuxiliary.surface(draw,track.x,track.y,(int)(track.w*amount),4,2,accent,accent,0,0,0,0);
                        StudioAuxiliary.surface(draw,track.x+(int)(track.w*amount)-4,track.y-2,8,8,4,Palette.TEXT,Palette.TEXT,0,0,0,0);
                        GuiDraw.shaderCard(draw,exact.x,exact.y,exact.w,exact.h,7,accent,glass(),theme(),exact.contains(mx,my)?1:0,false);
                        label(draw,number(value),exact.x+9,exact.y+9,.91f,accent);
                        clippedControl("setting:"+id+":"+setting.id(),new Area(track.x-4,track.y-8,track.w+8,21),viewport,0,()->{slider=new Slider(id,setting,track);updateSlider(mx);});
                        clippedControl("number:"+id+":"+setting.id(),exact,viewport,0,()->client.setScreen(new NumericSettingScreen(core,id,setting,this)));
                        clippedControl("number-right:"+id+":"+setting.id(),new Area(x+5,y,width-10,pitch-5),viewport,1,()->client.setScreen(new NumericSettingScreen(core,id,setting,this)));
                    }
                }
            }
            y+=pitch;
        }
        if(fields.isEmpty()){label(draw,"No extra setup is needed.",x+12,y+12,.9f,Palette.MUTED);y+=36;}
        if(inlineKeybinds()&&y+44>=viewport.y&&y<viewport.bottom()){
            label(draw,"Keybind",x+12,y+6,.88f,Palette.MUTED);
            Area hit=new Area(x+9,y+20,width-18,25);
            GuiDraw.shaderCard(draw,hit.x,hit.y,hit.w,hit.h,7,accent,glass(),theme(),hit.contains(mx,my)?1:0,capture!=null&&capture.equals(id));
            String key=capture!=null&&capture.equals(id)?"Press a key…":core.keybind(id)<0?"Unbound":InputUtil.fromKeyCode(core.keybind(id),0).getLocalizedText().getString();
            label(draw,GuiDraw.truncate(textRenderer,key,width-38,.89f),x+18,y+28,.89f,Palette.TEXT);
            clippedControl("keybind:"+id,hit,viewport,0,()->{capture=id;searchFocused=false;});
        }
    }
    protected void cycle(String id,Setting<?> setting,int direction){
        List<String> options=setting.choices();core.setValue(id,setting.id(),options.get(Math.floorMod(options.indexOf(core.getChoice(id,setting.id()))+direction,options.size())));changed();
        if((id.equals("hud")&&setting.id().equals("menu-layout"))||(id.equals("menu")&&setting.id().equals("layout")))layoutRefreshRequested=true;
    }
    protected void changed(){dirty=true;failed=false;changedAt=System.currentTimeMillis();}
    protected void persist(){try{core.save();dirty=false;status="All changes saved.";failed=false;}catch(IOException e){failed=true;status="Save failed: "+e.getMessage();}}
    @Override public void tick(){if(dirty&&!failed&&System.currentTimeMillis()-changedAt>650)persist();if(layoutRefreshRequested){layoutRefreshRequested=false;if(dirty)persist();MenuScreens.refreshLayout(core,this,selectedModuleId());}}
    @Override public void removed(){if(dirty)persist();}
    @Override public void close(){if(dirty)persist();if(returnParent!=null)client.setScreen(returnParent);else super.close();}
    protected void switchLayout(String name){core.setValue("menu","layout",name);changed();persist();Screen next=MenuScreens.create(core,selectedModuleId());if(next instanceof CatalogScreen catalog){catalog.restoreQueryText(query);catalog.withParent(returnParent);}client.setScreen(next);}
    @Override public boolean mouseClicked(double x,double y,int button){
        mx=(x-ox)/scale;my=(y-oy)/scale;
        for(int i=controls.size()-1;i>=0;i--){Control c=controls.get(i);if(c.button==button&&c.area.contains(mx,my)){focusedControl=c.id;c.action.run();applyKeyPressNarratorDelay();return true;}}
        searchFocused=false;focusedControl=null;return true;
    }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){mx=(x-ox)/scale;my=(y-oy)/scale;if(button==0&&slider!=null){updateSlider(mx);return true;}return false;}
    @Override public boolean mouseReleased(double x,double y,int button){slider=null;return true;}
    protected void updateSlider(double x){if(slider!=null){core.setValue(slider.module,slider.setting.id(),slider.setting.min()+Math.clamp((x-slider.track.x)/slider.track.w,0,1)*(slider.setting.max()-slider.setting.min()));changed();}}
    private List<Control> keyboardControls(){return controls.stream().filter(c->c.button==0&&!isNumericSlider(c.id)).toList();}
    private boolean isNumericSlider(String id){Setting<?> s=definitionSetting(id);return id.startsWith("setting:")&&s!=null&&s.kind()==Setting.Kind.NUMBER;}
    private Setting<?> definitionSetting(String action){String[] p=action.split(":");if(p.length!=3)return null;return core.modules().stream().filter(m->m.id().equals(p[1])).flatMap(m->m.settings().stream()).filter(s->s.id().equals(p[2])).findFirst().orElse(null);}
    private String controlLabel(String id){
        Setting<?> s=definitionSetting(id);if(s!=null){String[] p=id.split(":");return s.label()+": "+core.values(p[1]).get(p[2]);}
        if(id.equals("selected-toggle")&&selectedModuleId()!=null){String mid=selectedModuleId();return core.definition(mid).name()+(core.isEnabled(mid)?": enabled":": disabled");}
        if((id.equals("bind")&&selectedModuleId()!=null)||id.startsWith("keybind:")){String mid=id.equals("bind")?selectedModuleId():id.substring("keybind:".length());int key=core.keybind(mid);return core.definition(mid).name()+" keybind: "+(mid.equals(capture)?"Press a key. Escape cancels.":key<0?"Unbound":InputUtil.fromKeyCode(key,0).getLocalizedText().getString());}
        if(id.startsWith("inspect:")||id.startsWith("inspect-left:")||id.startsWith("module:")||id.startsWith("toggle:")){String mid=id.substring(id.indexOf(':')+1);return core.definition(mid).name()+(id.startsWith("inspect")?": inspect settings":core.isEnabled(mid)?": enabled":": disabled");}
        return id.replace(':',' ').replace('-',' ');
    }
    @Override protected void addElementNarrations(NarrationMessageBuilder builder){
        List<Control> entries=keyboardControls();int index=-1;for(int i=0;i<entries.size();i++)if(entries.get(i).id.equals(focusedControl)){index=i;break;}
        if(index<0){super.addElementNarrations(builder);return;}NarrationMessageBuilder child=builder.nextMessage();
        child.put(NarrationPart.TITLE,Text.literal(controlLabel(entries.get(index).id)));
        child.put(NarrationPart.POSITION,Text.translatable("narrator.position.screen",index+1,entries.size()));
        child.put(NarrationPart.USAGE,Text.literal("Tab moves focus. Enter activates. Arrow keys adjust settings."));
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        ignoreSearchShortcutChar=false;
        if(capture!=null){if(key!=GLFW.GLFW_KEY_ESCAPE){core.setKeybind(capture,key==GLFW.GLFW_KEY_DELETE||key==GLFW.GLFW_KEY_BACKSPACE?-1:key);changed();}capture=null;return true;}
        if(key==GLFW.GLFW_KEY_ESCAPE||(key==AnalyzerClient.getMenuKey()&&!searchFocused)){if(searchFocused)searchFocused=false;else close();return true;}
        if(key==GLFW.GLFW_KEY_TAB){
            List<Control> list=keyboardControls();if(list.isEmpty())return true;int index=-1;for(int i=0;i<list.size();i++)if(list.get(i).id.equals(focusedControl)){index=i;break;}
            boolean backward=(modifiers&GLFW.GLFW_MOD_SHIFT)!=0;int next=index<0?(backward?list.size()-1:0):Math.floorMod(index+(backward?-1:1),list.size());
            focusedControl=list.get(next).id;searchFocused=false;applyKeyPressNarratorDelay();return true;
        }
        if(key==GLFW.GLFW_KEY_SLASH&&!searchFocused){searchFocused=true;focusedControl="search";ignoreSearchShortcutChar=true;return true;}
        if(!searchFocused&&(key==GLFW.GLFW_KEY_PAGE_DOWN||key==GLFW.GLFW_KEY_PAGE_UP)){double[] point=controlPoint("settings-scroll");if(point!=null){mouseScrolled(point[0],point[1],0,key==GLFW.GLFW_KEY_PAGE_DOWN?-8:8);applyKeyPressNarratorDelay();return true;}}
        if(!searchFocused&&focusedControl!=null){
            if(key==GLFW.GLFW_KEY_ENTER||key==GLFW.GLFW_KEY_KP_ENTER||key==GLFW.GLFW_KEY_SPACE){controls.stream().filter(c->c.id.equals(focusedControl)).findFirst().ifPresent(c->c.action.run());applyKeyPressNarratorDelay();return true;}
            if(key==GLFW.GLFW_KEY_LEFT||key==GLFW.GLFW_KEY_RIGHT){Setting<?> s=definitionSetting(focusedControl);if(s!=null){String mid=focusedControl.split(":")[1];int direction=key==GLFW.GLFW_KEY_LEFT?-1:1;
                if(s.kind()==Setting.Kind.NUMBER){core.setValue(mid,s.id(),core.getNumber(mid,s.id())+direction*s.step());changed();}else if(s.kind()==Setting.Kind.CHOICE)cycle(mid,s,direction);else if(s.kind()==Setting.Kind.TEXT)editText(mid,s);else{core.setValue(mid,s.id(),!core.getBoolean(mid,s.id()));changed();}applyKeyPressNarratorDelay();return true;}}
        }
        if(!searchFocused)return super.keyPressed(key,scan,modifiers);
        if((modifiers&GLFW.GLFW_MOD_CONTROL)!=0&&key==GLFW.GLFW_KEY_A){selectAll=true;return true;}
        if((modifiers&GLFW.GLFW_MOD_CONTROL)!=0&&key==GLFW.GLFW_KEY_V){append(client.keyboard.getClipboard().replaceAll("[\\p{Cntrl}]",""));return true;}
        if(key==GLFW.GLFW_KEY_BACKSPACE||key==GLFW.GLFW_KEY_DELETE){query=selectAll||query.isEmpty()?"":query.substring(0,query.offsetByCodePoints(query.length(),-1));selectAll=false;searchChanged();return true;}
        if(key==GLFW.GLFW_KEY_ENTER)searchFocused=false;return true;
    }
    @Override public boolean charTyped(char c,int modifiers){if(!searchFocused||Character.isISOControl(c))return false;boolean suppress=c=='/'&&ignoreSearchShortcutChar;ignoreSearchShortcutChar=false;if(suppress)return true;append(String.valueOf(c));return true;}
    private void append(String value){query=(selectAll?"":query)+value;if(query.length()>96)query=query.substring(0,96);selectAll=false;searchChanged();}
    protected String number(double value){return value==Math.rint(value)?Long.toString((long)value):String.format(Locale.ROOT,"%.2f",value).replaceAll("0+$","").replaceAll("\\.$","");}
    public double[] controlPoint(String action){Point p=diagnostics.get(action);return p==null?null:new double[]{ox+p.x*scale,oy+p.y*scale};}
    protected record Point(double x,double y){}
    protected record Area(int x,int y,int w,int h){
        int right(){return x+w;}int bottom(){return y+h;}
        boolean contains(double px,double py){return px>=x&&py>=y&&px<right()&&py<bottom();}
        Area intersect(Area other){int left=Math.max(x,other.x),top=Math.max(y,other.y);return new Area(left,top,Math.max(0,Math.min(right(),other.right())-left),Math.max(0,Math.min(bottom(),other.bottom())-top));}
    }
    protected record Control(String id,Area area,int button,Runnable action){}
    protected record Slider(String module,Setting<?> setting,Area track){}
}
