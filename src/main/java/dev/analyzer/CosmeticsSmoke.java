package dev.analyzer;

import dev.analyzer.core.Category;
import dev.analyzer.render.CosmeticRenderer;
import dev.analyzer.render.VisualEffects;
import dev.analyzer.ui.HudWidgets;
import dev.analyzer.ui.MenuScreens;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import java.util.HashMap;
import java.util.Map;

/** Real renderer, packet and interaction checks in the isolated development world. */
final class CosmeticsSmoke {
    private static int assertions, captures, phase;
    private static boolean audit, auditComplete;
    private static int expectedWidth, expectedHeight;
    private static final Map<String, Double> physicalHeight = new HashMap<>();
    private static long before, blockBefore, soundBefore, hurtBefore;
    private static float yawBefore, pitchBefore;
    private static LivingEntity target;
    private static final BlockPos BLOCK = new BlockPos(11,161,18);
    private CosmeticsSmoke() { }

    @SuppressWarnings("deprecation")
    static void tick(MinecraftClient mc, int t) {
        var core = AnalyzerClient.CORE; phase = t;
        if(t>430 && Boolean.getBoolean("analyzer.automationSmoke")) {AutomationReleaseSmoke.tick(mc,t-431);return;}
        switch (t) {
            case 0 -> {
                require(dev.analyzer.ui.SmoothFontAtlas.healthy(),"Analyzer font atlases retain actual linear GPU sampling after resource reload");
                mc.setScreen(null); core.resetAll();
                for (var module : core.modules()) if (module.category() == Category.VISUAL || module.category() == Category.COSMETICS) core.setEnabled(module.id(), false);
                core.setValue("hud", "scale", 0.6); core.setValue("hud", "show-disabled", false);
                core.setValue("hud", "max-modules", 12);
                for (String id : new String[]{"armor","inventory","ping","biome","compass","speed-hud"}) core.setEnabled(id,true);
                command(mc,"kill @e[type=!minecraft:player]"); command(mc,"gamemode creative AnalyzerDev");
                command(mc,"time set 1000"); command(mc,"weather clear");
                command(mc,"fill 0 160 0 24 160 24 minecraft:grass_block");
                command(mc,"fill 0 161 0 24 169 24 minecraft:air");
                command(mc,"tp AnalyzerDev 11.5 161 21.5 180 12");
                command(mc,"item replace entity AnalyzerDev hotbar.0 with minecraft:stone 64");
                command(mc,"item replace entity AnalyzerDev inventory.0 with minecraft:diamond 64");
                command(mc,"item replace entity AnalyzerDev armor.head with minecraft:diamond_helmet");
                command(mc,"item replace entity AnalyzerDev armor.feet with minecraft:diamond_boots[minecraft:damage=425]");
                viewport(mc,1920,1080,2,"Glass");
                HudRenderCallback.EVENT.register((draw, tick) -> {
                    if (!audit || mc.world == null || mc.currentScreen != null) return;
                    require(mc.getWindow().getFramebufferWidth()==expectedWidth && mc.getWindow().getFramebufferHeight()==expectedHeight,"Actual readability framebuffer dimensions");
                    double factor=mc.getWindow().getScaleFactor();
                    var bounds=HudWidgets.bounds(core,draw,false);
                    require(!bounds.isEmpty(),"Readability fixture contains actual HUD widgets");
                    for (var b : bounds) require(b.x()>=0 && b.y()>=0 && b.x()+b.width()<=draw.getScaledWindowWidth()+1 && b.y()+b.height()<=draw.getScaledWindowHeight()+1,"HUD remains in viewport: "+b.id());
                    var mark=bounds.stream().filter(b->b.id().equals("watermark")).findFirst().orElseThrow();
                    String key=expectedWidth+"/"+core.getChoice("hud","theme");
                    double height=mark.height()*factor;
                    Double previous=physicalHeight.putIfAbsent(key,height);
                    if(previous!=null) require(Math.abs(height-previous)<=6,"HUD physical size survives Minecraft GUI scale change");
                    float body=HudWidgets.readableTtfScale(core,draw,0.92f),secondary=HudWidgets.readableTtfScale(core,draw,0.7f);
                    require(body*HudWidgets.scale(core,draw)*factor*12>=13.99,"TTF HUD body minimum fourteen nominal pixels");
                    require(secondary*HudWidgets.scale(core,draw)*factor*12>=11.99,"TTF HUD caption minimum twelve nominal pixels");
                    audit=false; auditComplete=true;
                });
            }
            case 10 -> { mc.inGameHud.getChatHud().clear(false); mc.getToastManager().clear(); }
            case 20 -> capture(mc,"analyzer-60-glass-1920-gui2.png",true);
            case 25 -> viewport(mc,1920,1080,3,"Glass");
            case 30 -> capture(mc,"analyzer-60-glass-1920-gui3.png",true);
            case 35 -> viewport(mc,1920,1080,4,"Glass");
            case 40 -> capture(mc,"analyzer-60-glass-1920-gui4.png",true);
            case 45 -> theme("Classic");
            case 50 -> capture(mc,"analyzer-60-classic-1920.png",true);
            case 55 -> theme("Minimal");
            case 60 -> capture(mc,"analyzer-60-minimal-1920.png",true);
            case 65 -> theme("Neon");
            case 70 -> capture(mc,"analyzer-60-neon-1920.png",true);
            case 75 -> { mc.inGameHud.getChatHud().clear(false); mc.getToastManager().clear(); viewport(mc,1280,800,2,"Glass"); }
            case 80 -> capture(mc,"analyzer-60-glass-1280-gui2.png",true);
            case 85 -> viewport(mc,1280,800,3,"Glass");
            case 90 -> capture(mc,"analyzer-60-glass-1280-gui3.png",true);
            case 95 -> theme("Classic");
            case 100 -> capture(mc,"analyzer-60-classic-1280.png",true);
            case 105 -> theme("Minimal");
            case 110 -> capture(mc,"analyzer-60-minimal-1280.png",true);
            case 115 -> theme("Neon");
            case 120 -> capture(mc,"analyzer-60-neon-1280.png",true);
            case 125 -> { core.setValue("menu","layout","Click GUI"); mc.setScreen(MenuScreens.create(core)); }
            case 135 -> capture(mc,"analyzer-readable-click-gui-1280.png",false);
            case 145 -> { mc.setScreen(null); viewport(mc,1920,1080,3,"Glass"); core.setValue("menu","layout","Click GUI"); mc.setScreen(MenuScreens.create(core)); }
            case 155 -> capture(mc,"analyzer-readable-click-gui-1920.png",false);
            case 160 -> { core.setValue("menu","layout","Click GUI"); mc.setScreen(MenuScreens.create(core)); }
            case 165 -> capture(mc,"analyzer-readable-click-gui.png",false);
            case 170 -> { core.setValue("menu","layout","Studio"); mc.setScreen(MenuScreens.create(core)); }
            case 175 -> capture(mc,"analyzer-readable-studio.png",false);
            case 180 -> { mc.setScreen(null); audit=false; viewport(mc,1280,800,3,"Glass"); audit=false; core.setEnabled("air-effects",true); core.setValue("air-effects","style","Network"); core.setValue("air-effects","density",48); }
            case 190 -> { require(VisualEffects.cosmeticDiagnostics().ambientPoints()==48,"Ambient effect obeys configured bounded density"); require(VisualEffects.frameDiagnostics().segments()>0,"Ambient network submits real world geometry"); capture(mc,"analyzer-air-network.png",false); }
            case 200 -> core.setValue("air-effects","density",12);
            case 210 -> { require(VisualEffects.cosmeticDiagnostics().ambientPoints()==12,"Changing ambient density changes rendered field"); core.setEnabled("air-effects",false); }
            case 220 -> { require(VisualEffects.cosmeticDiagnostics().ambientPoints()==0,"Disabling ambient field clears it"); core.setEnabled("block-effects",true); core.setValue("block-effects","style","Shatter"); blockBefore=VisualEffects.emitted("block-effects"); command(mc,"setblock 11 161 18 minecraft:air"); }
            case 230 -> { mc.player.getInventory().selectedSlot=0; require(mc.interactionManager.interactBlock(mc.player,Hand.MAIN_HAND,new BlockHitResult(new Vec3d(11.5,161,18.5),Direction.UP,BLOCK.down(),false)).isAccepted(),"Cosmetic fixture uses actual accepted block placement"); }
            case 234 -> { require(VisualEffects.emitted("block-effects")>blockBefore,"Independent block effects observe placement"); capture(mc,"analyzer-block-shatter.png",false); }
            case 240 -> mc.interactionManager.attackBlock(BLOCK,Direction.SOUTH);
            case 244 -> { require(mc.world.getBlockState(BLOCK).isAir() && VisualEffects.emitted("block-effects")>=blockBefore+2,"Independent block effects observe actual break"); core.setEnabled("block-effects",false); core.setEnabled("damage-effects",true); core.setEnabled("hit-sounds",true); core.setEnabled("hit-markers",true); core.setValue("hit-markers","style","Corners"); core.setValue("hit-markers","thickness",3); command(mc,"summon minecraft:zombie 11.5 161 19 {NoAI:1b,Silent:1b,PersistenceRequired:1b}"); command(mc,"item replace entity AnalyzerDev hotbar.0 with minecraft:diamond_sword"); hurtBefore=VisualEffects.emitted("damage-effects"); soundBefore=VisualEffects.emitted("hit-sounds"); }
            case 255 -> { target=hostile(mc); require(target!=null,"Hurt/sound fixture is a real loaded entity"); mc.interactionManager.attackEntity(mc.player,target); mc.player.swingHand(Hand.MAIN_HAND); }
            case 260 -> { require(VisualEffects.emitted("damage-effects")>hurtBefore,"Confirmed hurt emits damage effect"); require(VisualEffects.emitted("hit-sounds")>soundBefore,"Confirmed attacked-target hurt submits actual sound cue"); capture(mc,"analyzer-damage-hit-effects.png",false); core.setEnabled("storm-effects",true); before=VisualEffects.emitted("storm-effects"); }
            case 270 -> command(mc,"summon minecraft:lightning_bolt 14 161 17");
            case 275 -> { require(VisualEffects.emitted("storm-effects")>before,"Storm effect observes actual loaded lightning"); capture(mc,"analyzer-storm-effects.png",false); core.setEnabled("storm-effects",false); core.setEnabled("damage-effects",false); core.setEnabled("hit-sounds",false); core.setEnabled("hit-markers",false); command(mc,"kill @e[type=!minecraft:player]"); command(mc,"gamemode survival AnalyzerDev"); core.setEnabled("vitals",true); }
            case 290 -> command(mc,"damage AnalyzerDev 15 minecraft:generic");
            case 300 -> { require(mc.player.getHealth()<=8 && VisualEffects.cosmeticDiagnostics().warning(),"Vitals warning uses actual Survival health loss"); capture(mc,"analyzer-vitals-warning.png",false); core.setEnabled("vitals",false); command(mc,"effect give AnalyzerDev minecraft:instant_health 1 4 true"); command(mc,"gamemode creative AnalyzerDev"); }
            case 310 -> { require(!VisualEffects.cosmeticDiagnostics().warning(),"Disabling vitals stops warning"); core.setEnabled("item-physics",true); core.setEnabled("crystal-chams",true); core.setValue("crystal-chams","model","Gem"); core.setValue("crystal-chams","color","Violet"); command(mc,"summon minecraft:item 10.5 163 18 {Item:{id:\"minecraft:diamond\",count:1},PickupDelay:32767}"); command(mc,"summon minecraft:end_crystal 13.5 161 17.5 {ShowBottom:1b}"); }
            case 330 -> { require(CosmeticRenderer.frames("item-physics")>0,"Actual dropped item renderer uses physics mesh transforms"); require(CosmeticRenderer.frames("crystal-chams")>0,"Actual crystal renderer submits custom Gem model"); capture(mc,"analyzer-item-crystal-cosmetics.png",false); core.setValue("crystal-chams","model","Prism"); }
            case 340 -> { capture(mc,"analyzer-crystal-prism.png",false); core.setEnabled("crystal-chams",false); core.setEnabled("item-physics",false); core.setEnabled("hand-tweaks",true); core.setValue("hand-tweaks","main-x",0.2); core.setValue("hand-tweaks","main-tilt",30); core.setValue("hand-tweaks","main-size",0.75); before=CosmeticRenderer.frames("hand-tweaks"); command(mc,"item replace entity AnalyzerDev weapon.offhand with minecraft:golden_apple"); }
            case 350 -> { require(CosmeticRenderer.frames("hand-tweaks")>before,"First person renderer applies actual hand transforms"); capture(mc,"analyzer-hand-tweaks.png",false); core.setEnabled("hand-tweaks",false); mc.options.setPerspective(Perspective.THIRD_PERSON_BACK); core.setEnabled("cape",true); core.setEnabled("pets",true); core.setValue("cape","hem","Pointed"); core.setValue("cape","style","Gradient"); core.setValue("pets","pet","Dragon"); }
            case 365 -> { require(CosmeticRenderer.frames("cape")>0 && CosmeticRenderer.frames("pets")>0,"Third-person feature renders actual cape and companion meshes"); capture(mc,"analyzer-cape-dragon.png",false); core.setEnabled("emotes",true); core.setValue("emotes","stop-moving",false); core.setValue("emotes","emote","Dance"); core.setValue("emotes","loop",true); }
            case 375 -> { require(CosmeticRenderer.frames("emotes")>0,"Player model receives actual emote pose"); capture(mc,"analyzer-emote-dance.png",false); core.setEnabled("emotes",false); core.setEnabled("walk-style",true); core.setValue("walk-style","only-moving",false); core.setValue("walk-style","style","Zombie"); }
            case 385 -> { require(CosmeticRenderer.frames("walk-style")>0,"Player model receives alternate limb pose"); capture(mc,"analyzer-walk-zombie.png",false); core.setEnabled("walk-style",false); yawBefore=mc.player.getYaw(); pitchBefore=mc.player.getPitch(); core.setEnabled("spinbot",true); }
            case 400 -> { require(CosmeticRenderer.frames("spinbot")>0,"Player render state receives cosmetic spin"); require(mc.player.getYaw()==yawBefore && mc.player.getPitch()==pitchBefore,"Cosmetic spin preserves camera/entity rotations"); var serverPlayer=mc.getServer().getPlayerManager().getPlayer(mc.player.getUuid()); require(serverPlayer!=null && Math.abs(serverPlayer.getYaw()-yawBefore)<0.01,"Cosmetic spin preserves server rotation"); core.setEnabled("spinbot",false); core.setEnabled("cape",false); core.setEnabled("pets",false); before=CosmeticRenderer.frames("cape"); mc.options.setPerspective(Perspective.FIRST_PERSON); core.setEnabled("contrails",true); core.setValue("contrails","elytra-only",false); core.setValue("contrails","min-speed",0.05); core.setValue("contrails","first-person",true); core.setEnabled("auto-walk",true); }
            case 415 -> { require(VisualEffects.cosmeticDiagnostics().contrailPlayers()>0,"Paired contrails follow actual ordinary walking when glide restriction is off"); capture(mc,"analyzer-contrails-walking.png",false); require(CosmeticRenderer.frames("cape")==before,"Disabled cape stops mesh submission"); core.setEnabled("auto-walk",false); core.setEnabled("contrails",false); }
            case 425 -> { require(VisualEffects.cosmeticDiagnostics().contrailPlayers()==0,"Disabled contrails release tracked paths"); core.resetAll(); }
            case 430 -> { require(GL11.glGetError()==GL11.GL_NO_ERROR,"Cosmetics final GL state"); AnalyzerClient.LOGGER.info("ANALYZER_COSMETICS_COMPLETE: {} assertions, {} screenshots; cosmetics={}",assertions,captures,VisualEffects.cosmeticDiagnostics()); if(!Boolean.getBoolean("analyzer.automationSmoke")) mc.scheduleStop(); }
        }
    }
    private static void theme(String name) { AnalyzerClient.CORE.setValue("hud","theme",name); audit=true; auditComplete=false; }
    private static void viewport(MinecraftClient mc,int width,int height,int gui,String name) { expectedWidth=width; expectedHeight=height; GLFW.glfwSetWindowSize(mc.getWindow().getHandle(),width,height); mc.options.getGuiScale().setValue(gui); mc.onResolutionChanged(); theme(name); }
    private static LivingEntity hostile(MinecraftClient mc) { for(var entity:mc.world.getEntities()) if(entity instanceof LivingEntity living && entity instanceof Monster && living.isAlive() && living.squaredDistanceTo(mc.player)<16) return living; return null; }
    private static void command(MinecraftClient mc,String text) { mc.getServer().execute(()->mc.getServer().getCommandManager().executeWithPrefix(mc.getServer().getCommandSource().withSilent(),text)); }
    private static void require(boolean result,String message) { if(!result) throw new IllegalStateException("Cosmetic check failed at "+phase+": "+message); assertions++; AnalyzerClient.LOGGER.info("Cosmetic check passed: {}",message); }
    private static void capture(MinecraftClient mc,String name,boolean requireAudit) { if(requireAudit) require(auditComplete,"HUD bounds audited by actual render callback"); require(GL11.glGetError()==GL11.GL_NO_ERROR,"GL state before "+name); ScreenshotRecorder.saveScreenshot(mc.runDirectory,name,mc.getFramebuffer(),text->AnalyzerClient.LOGGER.info("Cosmetic screenshot: {}",text.getString())); captures++; }
}
