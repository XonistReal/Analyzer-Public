package dev.analyzer;

import dev.analyzer.cosmetic.WingsRenderer;
import dev.analyzer.cosmetic.WingAnimation;
import dev.analyzer.render.CosmeticRenderer;
import dev.analyzer.ui.MenuScreens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.MessageScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Items;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** Explicitly guarded isolated world; actual native textured feature renders, never user saves. */
public final class WingsSmoke {
    private static final String WORLD="Analyzer-Wings-Smoke";
    private static final String[] STYLES={"Angel","Dragon","Bat","Parrot"};
    private static boolean requested,setup,ready,disconnected,finished;
    private static volatile boolean prepared;
    private static volatile int written;
    private static int ticks,stage,age,checks;
    private static long baseline;
    private static final List<String> photos=new ArrayList<>();
    private WingsSmoke() { }
    public static void tick(MinecraftClient client) {
        if(!FabricLoader.getInstance().isDevelopmentEnvironment()||!Boolean.getBoolean("analyzer.wingsSmoke"))
            throw new IllegalStateException("Wings acceptance requires an isolated development launch");
        if(finished)return;
        if(++ticks>3000)throw new IllegalStateException("Wings native fixture timed out stage "+stage);
        if(disconnected) {
            if(++age<12||written!=photos.size())return;
            check(client.world==null,"Actual disconnect finishes without retained render work");
            check(CosmeticRenderer.frames("wings")==baseline,"No wing feature is submitted after disconnect");
            for(String name:photos)try{check(Files.size(client.runDirectory.toPath().resolve("screenshots").resolve(name))>1024,"Native framebuffer saved "+name);}
            catch(Exception ex){throw new IllegalStateException(ex);}
            check(GL11.glGetError()==GL11.GL_NO_ERROR,"Native GL state remains valid at shutdown");
            finished=true; AnalyzerClient.LOGGER.info("ANALYZER_WINGS_COMPLETE: {} native checks; {} captures; four textures; crouch; Elytra; first person; reload; disable; disconnect",checks,photos.size());
            client.scheduleStop();return;
        }
        if(!requested&&client.getOverlay()==null&&client.currentScreen!=null&&client.currentScreen.getClass().getSimpleName().equals("AccessibilityOnboardingScreen")){client.setScreen(new TitleScreen());return;}
        if(!requested&&client.getOverlay()==null&&client.currentScreen instanceof TitleScreen){
            requested=true;AnalyzerClient.CORE.resetAll();
            for(var module:AnalyzerClient.CORE.modules())AnalyzerClient.CORE.setEnabled(module.id(),false);
            client.options.pauseOnLostFocus=false;client.options.getViewDistance().setValue(4);client.options.getSimulationDistance().setValue(5);
            client.options.getGuiScale().setValue(2);client.options.getFov().setValue(55);client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
            client.options.hudHidden=true;client.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);client.getToastManager().clear();
            GLFW.glfwHideWindow(client.getWindow().getHandle());
            if(Files.exists(client.runDirectory.toPath().resolve("saves/"+WORLD+"/level.dat")))client.createIntegratedServerLoader().start(WORLD,client::scheduleStop);
            else client.createIntegratedServerLoader().createAndStart(WORLD,new LevelInfo(WORLD,GameMode.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(FeatureFlags.VANILLA_FEATURES),DataConfiguration.SAFE_MODE),new GeneratorOptions(94183L,false,false),WorldPresets::createDemoOptions,new TitleScreen());
        }
        if(!requested||client.world==null||client.player==null||client.getServer()==null||client.getOverlay()!=null)return;
        if(!setup){setup=true;client.getServer().execute(()->{
            for(String command:List.of("gamerule doMobSpawning false","gamerule doDaylightCycle false","gamerule doWeatherCycle false","time set noon","weather clear","fill -12 160 -12 12 160 12 minecraft:smooth_stone","tp AnalyzerDev 0.5 161 0.5 0 7","item replace entity AnalyzerDev armor.chest with minecraft:air"))command(client,command);
            prepared=true;
        });return;}
        if(!ready){
            if(!prepared||client.player.getBlockY()!=161||!client.world.getBlockState(new BlockPos(0,160,0)).isOf(net.minecraft.block.Blocks.SMOOTH_STONE))return;
            ready=true;client.setScreen(null);client.inGameHud.getChatHud().clear(false);client.getToastManager().clear();
            check(!AnalyzerClient.CORE.isEnabled("wings"),"Missing/old settings leave the new cosmetic opt-in");
            AnalyzerClient.CORE.setEnabled("wings",true);AnalyzerClient.CORE.setValue("wings","animation","Flap");
            AnalyzerClient.CORE.setValue("wings","style",STYLES[0]);enter(0);
        }
        age++;
        if(stage<4){
            if(age<35||!drawn())return;
            String style=STYLES[stage];check(WingsRenderer.frames(style)>0,"Actual native feature submits "+style+" wing texture");
            var snapshot=WingsRenderer.lastSnapshot();
            check(snapshot!=null&&snapshot.style().equals(style)&&snapshot.motion()==WingAnimation.Motion.FLAP&&snapshot.submittedVertices()==1584,"Actual "+style+" draw uses the selected articulated bounded rig");
            check(Math.abs(snapshot.mountY()+3.0/16)<.00001,"Native "+style+" attachment uses the approved higher upper-back mount");
            check(client.getResourceManager().getResource(Identifier.of("analyzer","textures/cosmetics/wings/"+style.toLowerCase(java.util.Locale.ROOT)+"_wings.png")).isPresent(),"Bundled original "+style+" texture resolves");
            check(GL11.glGetError()==GL11.GL_NO_ERROR,"Native textured "+style+" rendering keeps clean GL state");
            capture(client,style.toLowerCase(java.util.Locale.ROOT));
            if(stage<3)AnalyzerClient.CORE.setValue("wings","style",STYLES[stage+1]);
            else client.options.sneakKey.setPressed(true);
            enter(stage+1);return;
        }
        switch(stage){
            case 4->{if(age>=25&&drawn()&&client.player.isSneaking()){
                var snapshot=WingsRenderer.lastSnapshot();
                check(snapshot!=null&&Math.abs(snapshot.bodyPitch())>.1,"Real native crouching body transform carries the attached rig");capture(client,"crouching");
                client.options.sneakKey.setPressed(false);command(client,"item replace entity AnalyzerDev armor.chest with minecraft:elytra");enter(5);
            }}
            case 5->{if(age>=20&&client.player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)){enter(6);}}
            case 6->{if(age>=15){check(!drawn(),"Real equipped Elytra suppresses wings with the default hide option");AnalyzerClient.CORE.setValue("wings","hide-elytra",false);enter(7);}}
            case 7->{if(age>=25&&drawn()){
                check(true,"Explicit Elytra overlap opt-out permits native wing rendering");
                command(client,"item replace entity AnalyzerDev armor.chest with minecraft:diamond_chestplate");enter(8);
            }}
            case 8->{if(age>=25&&drawn()&&client.player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.DIAMOND_CHESTPLATE)){
                var snapshot=WingsRenderer.lastSnapshot();
                check(snapshot!=null&&Math.abs(snapshot.mountZ()-.8/16)<.00001,"Native chest armor adds the selected upper-back clearance");capture(client,"armored");
                client.options.setPerspective(Perspective.FIRST_PERSON);enter(9);
            }}
            case 9->{if(age>=15){check(!drawn(),"First-person world camera submits no self wing feature");client.options.setPerspective(Perspective.THIRD_PERSON_BACK);client.reloadResources();enter(10);}}
            case 10->{if(age>=30&&drawn()){
                check(GL11.glGetError()==GL11.GL_NO_ERROR,"Resource reload restores native textured wing rendering");
                AnalyzerClient.CORE.setEnabled("wings",false);enter(11);
            }}
            case 11->{if(age>=15){check(!drawn(),"Disabling prevents additional feature submissions");AnalyzerClient.CORE.setEnabled("wings",true);client.options.hudHidden=false;client.setScreen(MenuScreens.create(AnalyzerClient.CORE,"wings"));enter(12);}}
            case 12->{if(age>=15){capture(client,"settings");client.setScreen(null);enter(13);}}
            case 13->{if(age>=15&&drawn()){
                check(true,"Re-enabled native wings remain usable after the settings screen");
                baseline=CosmeticRenderer.frames("wings");disconnected=true;age=0;
                client.world.disconnect();client.disconnect(new MessageScreen(Text.literal("Saving isolated wing acceptance world")));
            }}
            default->throw new IllegalStateException("Unknown wing fixture phase");
        }
    }
    private static void enter(int value){stage=value;age=0;baseline=CosmeticRenderer.frames("wings");}
    private static boolean drawn(){return CosmeticRenderer.frames("wings")>baseline;}
    private static void command(MinecraftClient client,String command){
        var server=client.getServer();
        if(server.isOnThread())server.getCommandManager().executeWithPrefix(server.getCommandSource().withSilent(),command);
        else server.execute(()->server.getCommandManager().executeWithPrefix(server.getCommandSource().withSilent(),command));
    }
    private static void capture(MinecraftClient client,String suffix){String name="analyzer-native-wings-"+suffix+".png";photos.add(name);ScreenshotRecorder.saveScreenshot(client.runDirectory,name,client.getFramebuffer(),text->{written++;AnalyzerClient.LOGGER.info("Wings native framebuffer: {}",name);});}
    private static void check(boolean passed,String description){if(!passed)throw new IllegalStateException("Wings native fixture: "+description);checks++;AnalyzerClient.LOGGER.info("Wings native check: {}",description);}
}
