package dev.analyzer.ui;

import dev.analyzer.AnalyzerClient;
import dev.analyzer.core.AnalyzerCore;
import dev.analyzer.core.Category;
import dev.analyzer.core.GameplayModules;
import dev.analyzer.core.ModuleDefinition;
import dev.analyzer.core.UtilityModules;
import dev.analyzer.effects.GpuEffects;
import dev.analyzer.gameplay.GameplayEngine;
import dev.analyzer.gameplay.UtilityEngine;
import dev.analyzer.render.AnalyzerRenderer;
import dev.analyzer.render.VisualEffects;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.DimensionEffects;
import net.minecraft.client.util.InputUtil;
import net.minecraft.block.enums.CameraSubmersionType;
import org.lwjgl.glfw.GLFW;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Focused native workspace: rounded module cards, readable inspector and scene glass. */
public final class AnalyzerScreen extends CatalogScreen {
    private static final Area CATALOG=new Area(175,135,464,418);
    private static final Area INSPECTOR=new Area(661,231,315,296);
    private static final Area PROFILES=new Area(175,188,464,363);
    private static final List<Category> ORDER=List.of(Category.COMBAT,Category.HVH,Category.MOVEMENT,Category.VISUAL,Category.COSMETICS,Category.WORLD,Category.PLAYER,Category.HUD,Category.CLIENT);
    private static final Set<String> GAMEPLAY=GameplayModules.definitions().stream().map(ModuleDefinition::id).collect(Collectors.toUnmodifiableSet());
    private static final Set<String> UTILITY=UtilityModules.definitions().stream().map(ModuleDefinition::id).collect(Collectors.toUnmodifiableSet());
    private static final Set<String> COSMETICS=dev.analyzer.core.CosmeticModules.definitions().stream().map(ModuleDefinition::id).collect(Collectors.toUnmodifiableSet());
    private final Screen profileParent;
    private ModuleDefinition selected;
    private String category="ALL",profileName="",selectedProfile="";
    private List<String> profiles=List.of();
    private boolean profileMode,profileFocused,profileSelectAll;
    private double scroll,settingScroll,profileScroll,maxScroll,maxSettingScroll;
    public AnalyzerScreen(AnalyzerCore core){this(core,null);}
    public AnalyzerScreen(AnalyzerCore core,Screen parent){super(core,"Analyzer / Workspace");profileParent=parent;selected=core.modules().getFirst();refreshProfiles();}
    @Override protected String layout(){return "Workspace";}
    @Override protected boolean profilesOpen(){return profileMode;}
    @Override protected boolean inlineKeybinds(){return false;}
    @Override public String selectedModuleId(){return selected==null?null:selected.id();}
    @Override public List<String> visibleModuleIds(){return visible().stream().map(ModuleDefinition::id).toList();}
    @Override protected void searchChanged(){scroll=0;}
    private List<ModuleDefinition> visible(){return matching().stream().filter(m->category.equals("ALL")||m.category().name().equals(category)).toList();}
    @Override protected void body(DrawContext draw){
        sidebar(draw);
        if(profileMode){drawProfiles(draw);return;}
        drawCatalogue(draw);drawInspector(draw);
    }
    private void sidebar(DrawContext draw){
        panel(draw,new Area(19,95,138,457),false);
        button(draw,"category:ALL","All modules",28,107,120,34,category.equals("ALL")&&!profileMode,()->{category="ALL";profileMode=false;scroll=0;searchFocused=false;});
        int y=150;
        for(Category entry:ORDER){
            String count=Long.toString(core.modules().stream().filter(m->m.category()==entry).count());
            Area area=new Area(28,y,120,32);
            GuiDraw.card(draw,area.x(),area.y(),area.w(),area.h(),8,accent,glass(),theme(),area.contains(mx,my)?1:0,category.equals(entry.name())&&!profileMode);
            label(draw,entry.label(),area.x()+10,area.y()+11,.94f,Palette.TEXT);
            label(draw,count,area.right()-10-GuiDraw.width(textRenderer,count,.79f),area.y()+12,.79f,Palette.MUTED);
            control("category:"+entry.name(),area,0,()->{category=entry.name();profileMode=false;scroll=0;searchFocused=false;capture=null;});y+=36;
        }
        label(draw,"APPEARANCE",30,484,.75f,Palette.MUTED);
        button(draw,"hud-settings","HUD Studio",28,505,120,32,false,()->{profileMode=false;inspect("hud");});
    }
    private void drawCatalogue(DrawContext draw){
        label(draw,category.equals("ALL")?"All modules":Category.valueOf(category).label(),178,103,1.2f,Palette.TEXT);
        List<ModuleDefinition> list=visible();
        label(draw,list.size()+" matching · "+core.modules().stream().filter(m->core.isEnabled(m.id())).count()+" enabled",178,121,.78f,Palette.MUTED);
        maxScroll=Math.max(0,((list.size()+1)/2)*112-CATALOG.h());scroll=Math.clamp(scroll,0,maxScroll);
        draw.enableScissor(CATALOG.x(),CATALOG.y(),CATALOG.right(),CATALOG.bottom());
        if(list.isEmpty()){label(draw,"No matching modules",191,191,1.13f,Palette.TEXT);label(draw,"Clear search or choose another category.",191,217,.92f,Palette.MUTED);}
        for(int i=0;i<list.size();i++){
            int x=177+i%2*236,y=140+i/2*112-(int)scroll;
            if(y+104<CATALOG.y()||y>=CATALOG.bottom())continue;
            ModuleDefinition m=list.get(i);Area card=new Area(x,y,226,104);boolean chosen=m==selected,on=core.isEnabled(m.id());
            GuiDraw.card(draw,x,y,226,104,radius(),accent,glass(),theme(),card.contains(mx,my)?1:0,chosen);
            List<String> title=GuiDraw.wrap(textRenderer,m.name(),166,1.05f);
            for(int line=0;line<Math.min(2,title.size());line++)label(draw,title.get(line),x+13,y+15+line*13,1.05f,Palette.TEXT);
            List<String> description=GuiDraw.wrap(textRenderer,m.description(),200,.86f);
            for(int line=0;line<Math.min(3,description.size());line++)label(draw,description.get(line),x+13,y+44+line*12,.86f,Palette.MUTED);
            label(draw,on?"Enabled":"Disabled",x+13,y+88,.77f,Palette.MUTED);
            String right=chosen?"Inspecting":keyName(core.keybind(m.id()));
            label(draw,GuiDraw.truncate(textRenderer,right,93,.77f),x+226-13-GuiDraw.width(textRenderer,GuiDraw.truncate(textRenderer,right,93,.77f),.77f),y+88,.77f,chosen?accent:Palette.MUTED);
            clippedControl("inspect:"+m.id(),card,CATALOG,0,()->{selected=m;settingScroll=0;searchFocused=false;capture=null;profileFocused=false;});
            Area toggleArea=new Area(x+180,y+10,35,25);toggle(draw,x+181,y+13,on);
            clippedControl("module:"+m.id(),toggleArea,CATALOG,0,()->{core.toggle(m.id());changed();});
            Point p=diagnostics.get("module:"+m.id());if(p!=null)diagnostics.put("toggle:"+m.id(),p);
        }
        draw.disableScissor();scrollbar(draw,CATALOG,scroll,maxScroll);
    }
    private void drawInspector(DrawContext draw){
        if(selected==null)return;
        panel(draw,new Area(650,95,334,457),false);
        List<String> title=GuiDraw.wrap(textRenderer,selected.name(),260,1.26f);
        for(int line=0;line<Math.min(2,title.size());line++)label(draw,title.get(line),665,108+line*15,1.26f,Palette.TEXT);
        toggle(draw,939,108,core.isEnabled(selected.id()));
        control("selected-toggle",new Area(935,101,40,32),0,()->{core.toggle(selected.id());changed();});
        List<String> description=GuiDraw.wrap(textRenderer,selected.description(),300,.91f);
        for(int line=0;line<Math.min(3,description.size());line++)label(draw,description.get(line),665,143+line*13,.91f,Palette.MUTED);
        List<String> live=GuiDraw.wrap(textRenderer,liveStatus(selected),300,.83f);
        for(int line=0;line<Math.min(2,live.size());line++)label(draw,live.get(line),665,189+line*12,.83f,core.isEnabled(selected.id())?accent:Palette.MUTED);
        label(draw,selected.settings().size()+" settings · full labels and exact values",665,219,.73f,Palette.MUTED);
        maxSettingScroll=Math.max(0,settingsHeight(selected,INSPECTOR.w())-INSPECTOR.h());settingScroll=Math.clamp(settingScroll,0,maxSettingScroll);
        diagnostics.put("settings-scroll",new Point(INSPECTOR.x()+INSPECTOR.w()/2.0,INSPECTOR.y()+INSPECTOR.h()/2.0));
        draw.enableScissor(INSPECTOR.x(),INSPECTOR.y(),INSPECTOR.right(),INSPECTOR.bottom());
        settings(draw,selected,INSPECTOR.x(),INSPECTOR.y()-(int)settingScroll,INSPECTOR.w(),INSPECTOR);
        draw.disableScissor();scrollbar(draw,INSPECTOR,settingScroll,maxSettingScroll);
        label(draw,"Keybind",664,540,.87f,Palette.MUTED);
        button(draw,"bind",capture==null?keyName(core.keybind(selected.id())):"Press a key…",799,532,177,23,capture!=null,()->{capture=selected.id();searchFocused=false;});
        diagnostics.put("keybind:"+selected.id(),diagnostics.get("bind"));
    }
    private String liveStatus(ModuleDefinition m){
        String id=m.id();if(!core.isEnabled(id))return "Disabled";
        if(id.equals("menu"))return core.getChoice(id,"material")+" · "+core.getChoice(id,"theme")+" · "+core.getChoice(id,"accent");
        if(id.equals("particles"))return Math.round(core.getNumber(id,"density")*80)+" menu particles";
        if(client==null||client.world==null||client.player==null)return "Enabled · enter a world";
        if(GAMEPLAY.contains(id))return GameplayEngine.status(id);
        if(UTILITY.contains(id))return UtilityEngine.status(id);
        if(List.of("entity-esp","storage-esp","tracers").contains(id))return AnalyzerRenderer.status(id);
        if(id.equals("shader")){
            if(core.getChoice(id,"preset").equals("Off"))return "Preset is Off · choose a look below";
            if(core.getNumber(id,"strength")==0)return "Strength is zero · increase it below";
            return GpuEffects.isHealthy()?core.getChoice(id,"preset")+" post effect · "+Math.round(core.getNumber(id,"strength")*100)+"% strength":GpuEffects.status();
        }
        if(id.equals("sky")){
            if(!GpuEffects.isHealthy())return GpuEffects.status();
            if(client.world.getDimensionEffects().getSkyType()!=DimensionEffects.SkyType.NORMAL)return "Custom sky needs an Overworld sky";
            if(client.gameRenderer.getCamera().getSubmersionType()!=CameraSubmersionType.NONE)return "Sky hidden while submerged";
            if(core.getNumber(id,"intensity")==0)return "Intensity is zero · increase it below";
            if(core.getChoice(id,"day-night").equals("Night")&&client.world.getStarBrightness(client.getRenderTickCounter().getTickDelta(false))<.001f)return "Waiting for night";
            return core.getChoice(id,"preset")+" sky · "+Math.round(core.getNumber(id,"intensity")*100)+"% intensity";
        }
        if(id.equals("fullbright"))return "Lighting boost · "+number(core.getNumber(id,"gamma"))+"× brightness";
        if(id.equals("crosshair"))return "Live HUD · close menu to see crosshair";
        if(id.equals("coordinates"))return "Live position · "+client.player.getBlockPos().toShortString();
        if(COSMETICS.contains(id))return dev.analyzer.render.CosmeticRenderer.status(id);
        if(m.category()==Category.HUD)return "Enabled · live HUD";
        return VisualEffects.status(id);
    }
    private void drawProfiles(DrawContext draw){
        label(draw,"Profiles",178,103,1.2f,Palette.TEXT);
        label(draw,"Loading replaces module settings and keybinds.",178,124,.89f,Palette.MUTED);
        Area name=new Area(178,144,310,33);
        GuiDraw.card(draw,name.x(),name.y(),name.w(),name.h(),9,accent,glass(),theme(),profileFocused?1:0,profileFocused);
        label(draw,GuiDraw.truncate(textRenderer,profileName.isEmpty()?"Profile name":profileName,284,.94f),190,155,.94f,profileName.isEmpty()?Palette.MUTED:Palette.TEXT);
        control("profile-name",name,0,()->{profileFocused=true;profileSelectAll=false;searchFocused=false;capture=null;});
        button(draw,"save-profile","Save profile",496,144,141,33,false,this::saveProfile);
        diagnostics.put("profile-save",diagnostics.get("save-profile"));
        profileScroll=Math.clamp(profileScroll,0,Math.max(0,profiles.size()*66-PROFILES.h()));
        draw.enableScissor(PROFILES.x(),PROFILES.y(),PROFILES.right(),PROFILES.bottom());
        for(int i=0;i<profiles.size();i++){
            String p=profiles.get(i);int y=193+i*66-(int)profileScroll;
            if(y+57<PROFILES.y()||y>=PROFILES.bottom())continue;
            Area card=new Area(178,y,459,57);boolean chosen=p.equals(selectedProfile);
            GuiDraw.card(draw,card.x(),card.y(),card.w(),card.h(),radius(),accent,glass(),theme(),card.contains(mx,my)?1:0,chosen);
            label(draw,GuiDraw.truncate(textRenderer,p,290,1.03f),193,y+13,1.03f,Palette.TEXT);
            label(draw,chosen?"Selected profile":"Saved modules, settings and keybinds",193,y+34,.8f,Palette.MUTED);
            clippedControl("profile:"+p,card,PROFILES,0,()->{selectedProfile=p;profileName=p;profileFocused=false;});
            Area load=new Area(548,y+14,75,28);
            GuiDraw.card(draw,load.x(),load.y(),load.w(),load.h(),8,accent,glass(),theme(),load.contains(mx,my)?1:0,false);
            label(draw,"Load",567,y+23,.9f,Palette.TEXT);clippedControl("load-profile:"+p,load,PROFILES,0,()->loadProfile(p));
        }
        draw.disableScissor();scrollbar(draw,PROFILES,profileScroll,Math.max(0,profiles.size()*66-PROFILES.h()));
        panel(draw,new Area(650,95,334,457),false);
        button(draw,"profiles-back","Back to modules",818,101,148,29,false,this::returnModules);
        label(draw,"Your saved setup",669,116,1.2f,Palette.TEXT);
        label(draw,core.modules().stream().filter(m->core.isEnabled(m.id())).count()+" modules enabled",669,147,.95f,Palette.MUTED);
        label(draw,selectedProfile.isEmpty()?"Select a profile":selectedProfile,669,195,1.2f,Palette.TEXT);
        List<String> detail=GuiDraw.wrap(textRenderer,"Loading replaces settings and keybinds. Your worlds and friend list are preserved.",294,.94f);
        for(int line=0;line<detail.size();line++)label(draw,detail.get(line),669,232+line*14,.94f,Palette.MUTED);
        if(!selectedProfile.isEmpty()){
            button(draw,"load-selected","Load this profile",669,327,296,37,false,()->loadProfile(selectedProfile));
            button(draw,"delete-selected","Delete selected profile",669,377,296,32,false,this::deleteProfile);
        }
        label(draw,"Settings save automatically.",669,491,.9f,Palette.MUTED);
        label(draw,"Profiles are stored locally.",669,513,.85f,Palette.MUTED);
    }
    private void scrollbar(DrawContext draw,Area view,double offset,double max){
        if(max<=0)return;int h=Math.max(24,(int)(view.h()*view.h()/(view.h()+max))),y=view.y()+(int)((view.h()-h)*offset/max);
        GuiDraw.rounded(draw,view.right()-2,y,2,h,1,Palette.alpha(accent,170));
    }
    private String keyName(int key){return key<0?"Unbound":InputUtil.fromKeyCode(key,0).getLocalizedText().getString();}
    private void refreshProfiles(){try{profiles=core.profiles();}catch(IOException e){profiles=List.of();report("Could not read profiles: "+e.getMessage(),true);}}
    private void report(String text,boolean error){status=text;failed=error;}
    private void saveProfile(){
        String name=profileName.strip();if(name.isEmpty()){report("Enter a profile name first.",true);profileFocused=true;return;}
        try{core.saveProfile(name);profiles=core.profiles();selectedProfile=name.toLowerCase(Locale.ROOT).replace(' ','-');report("Saved profile "+selectedProfile+".",false);}
        catch(IOException|IllegalArgumentException e){report("Could not save profile: "+e.getMessage(),true);}
    }
    private void loadProfile(String name){
        try{core.loadProfile(name);selectedProfile=name;profileName=name;core.save();dirty=false;report("Loaded "+name+". Settings and keybinds replaced.",false);}
        catch(IOException|IllegalArgumentException e){report("Could not load profile: "+e.getMessage(),true);}
    }
    private void deleteProfile(){
        if(selectedProfile.isEmpty())return;
        try{core.deleteProfile(selectedProfile);selectedProfile="";profiles=core.profiles();report("Profile deleted.",false);}
        catch(IOException|IllegalArgumentException e){report("Could not delete profile: "+e.getMessage(),true);}
    }
    public AnalyzerScreen showProfiles(){profileMode=true;refreshProfiles();return this;}
    private void returnModules(){if(profileParent!=null&&MenuScreens.matchesLayout(core,profileParent))client.setScreen(profileParent);else{profileMode=false;client.setScreen(MenuScreens.create(core,selectedModuleId()));}}
    @Override public AnalyzerScreen inspect(String id){
        core.modules().stream().filter(m->m.id().equals(id)).findFirst().ifPresent(m->{selected=m;category="ALL";profileMode=false;query="";scroll=Math.max(0,core.modules().indexOf(m)/2*112-80);settingScroll=0;});return this;
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){
        double lx=(x-ox)/scale,ly=(y-oy)/scale;
        if(profileMode&&PROFILES.contains(lx,ly))profileScroll=Math.max(0,profileScroll-vertical*35);
        else if(!profileMode&&INSPECTOR.contains(lx,ly))settingScroll=Math.clamp(settingScroll-vertical*35,0,maxSettingScroll);
        else if(CATALOG.contains(lx,ly))scroll=Math.clamp(scroll-vertical*35,0,maxScroll);return true;
    }
    @Override public boolean mouseClicked(double x,double y,int button){profileFocused=false;return super.mouseClicked(x,y,button);}
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(profileMode&&!profileFocused&&!searchFocused&&capture==null&&key==GLFW.GLFW_KEY_ESCAPE){returnModules();return true;}
        if(profileFocused){
            if(key==GLFW.GLFW_KEY_ESCAPE){profileFocused=false;return true;}
            if(key==GLFW.GLFW_KEY_TAB){profileFocused=false;return super.keyPressed(key,scan,modifiers);}
            if((modifiers&GLFW.GLFW_MOD_CONTROL)!=0&&key==GLFW.GLFW_KEY_A){profileSelectAll=true;return true;}
            if(key==GLFW.GLFW_KEY_BACKSPACE||key==GLFW.GLFW_KEY_DELETE){profileName=profileSelectAll||profileName.isEmpty()?"":profileName.substring(0,profileName.offsetByCodePoints(profileName.length(),-1));profileSelectAll=false;return true;}
            if(key==GLFW.GLFW_KEY_ENTER||key==GLFW.GLFW_KEY_KP_ENTER){saveProfile();profileFocused=false;return true;}
            if((modifiers&GLFW.GLFW_MOD_CONTROL)!=0&&key==GLFW.GLFW_KEY_V){appendProfile(client.keyboard.getClipboard().replaceAll("[\\p{Cntrl}]",""));return true;}
            return true;
        }
        if(key==GLFW.GLFW_KEY_SPACE&&!searchFocused&&capture==null&&focusedControl==null&&selected!=null&&!profileMode){core.toggle(selected.id());changed();return true;}
        return super.keyPressed(key,scan,modifiers);
    }
    @Override public boolean charTyped(char c,int modifiers){if(profileFocused&&!Character.isISOControl(c)){appendProfile(String.valueOf(c));return true;}return super.charTyped(c,modifiers);}
    private void appendProfile(String text){profileName=(profileSelectAll?"":profileName)+text;if(profileName.length()>48)profileName=profileName.substring(0,48);profileSelectAll=false;}
}
