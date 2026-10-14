package dev.analyzer;

import dev.analyzer.automation.*;
import dev.analyzer.ui.AutomationScreen;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import java.nio.file.*;
import java.util.List;
import java.util.Set;

/** Real integrated-server Survival actions in an isolated development run without an external bot. */
final class NativeAutomationSmoke {
    private enum Phase { SETUP, TRAVEL, PAUSE, RESUME, MINE_SETUP, MINE, BUILD_SETUP, BUILD, IMPORT, IMPORTED_BUILD, PORTAL_SETUP, PORTAL, RETURN, PROGRESSION, INVENTORY, TOOL_RECOVERY, GUARD, TASK_UI, FINISH }
    private static final AutomationNavigator NAV = new AutomationNavigator();
    private static final BlockPos ORE = new BlockPos(66,161,51), BUILD = new BlockPos(70,161,60);
    private static Phase phase=Phase.SETUP;
    private static int entered, checks, captures, inventoryStart, guardStart, progressionStart, recoveryStart, taskUiStart;
    private static Vec3d paused;
    private static float lastYaw;
    private static long supplyBefore;
    private static Path importedFile;
    private static TaskPlanner.Plan importPlan;
    private static boolean importedSuppliesVerified;
    private static final BuildSchematic BLUEPRINT=new BuildSchematic() {
        public int widthX(){return 3;} public int heightY(){return 2;} public int lengthZ(){return 3;}
        public boolean inSchematic(int x,int y,int z,BlockState current){return x>=0&&x<3&&y>=0&&y<2&&z>=0&&z<3;}
        public BlockState desiredState(int x,int y,int z,BlockState current,List<BlockState> ignored){
            if(y==0)return Blocks.COBBLESTONE.getDefaultState();
            if(x==0&&z==1)return Blocks.COBBLESTONE.getDefaultState();
            if(x==1&&z==1)return Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE,SlabType.TOP);
            return Blocks.AIR.getDefaultState();
        }
    };
    static void tick(MinecraftClient mc,int tick) {
        require(FabricLoader.getInstance().isDevelopmentEnvironment()&&Boolean.getBoolean("analyzer.smoke"),"Fixture is development-only");
        if(mc.world==null||mc.player==null||mc.getServer()==null)return;
        int age=tick-entered;
        if(phase==Phase.TRAVEL || phase==Phase.RESUME || phase==Phase.MINE || phase==Phase.BUILD) {
            NAV.tick();
            if(NAV.status().failed())throw new IllegalStateException("Native acceptance "+phase+": "+NAV.status().message());
        }
        switch(phase) {
            case SETUP -> {
                if(age==0) {
                    require(!FabricLoader.getInstance().isModLoaded("baritone"),"No Baritone mod loads in the actual acceptance client");
                    AnalyzerClient.CORE.resetAll();AutomationEngine.INSTANCE.stop();mc.setScreen(null);
                    AutomationInputDiagnostics.developmentOverride(Set.of());
                    AnalyzerClient.CORE.setValue("expedition","turn-speed",120.0);AnalyzerClient.CORE.setValue("expedition","turn-acceleration",360.0);
                    AnalyzerClient.CORE.setValue("expedition","auto-site",false);
                    mc.getServer().execute(()->{
                        for(int x=2;x<=6;x++)for(int z=2;z<=5;z++)mc.getServer().getOverworld().getChunk(x,z);
                    });
                    command(mc,"kill @e[type=!minecraft:player]");command(mc,"gamemode creative AnalyzerDev");command(mc,"clear AnalyzerDev");
                    command(mc,"fill 40 160 40 100 160 90 minecraft:bedrock");command(mc,"fill 40 161 40 100 168 90 minecraft:air");
                    command(mc,"fill 58 161 40 58 164 56 minecraft:stone");command(mc,"tp AnalyzerDev 50.5 161 50.5 90 0");
                    command(mc,"weather clear");command(mc,"time set 1000");command(mc,"gamerule doMobSpawning false");
                    command(mc,"gamerule doTileDrops true");command(mc,"gamemode survival AnalyzerDev");
                    command(mc,"effect give AnalyzerDev minecraft:saturation 60 5 true");
                    command(mc,"item replace entity AnalyzerDev hotbar.0 with minecraft:diamond_pickaxe");
                }
                if(age>=20 && mc.player.getBlockY()==161 && mc.world.getBlockState(new BlockPos(50,160,50)).isOf(Blocks.BEDROCK)) {
                    require(mc.interactionManager.getCurrentGameMode()==GameMode.SURVIVAL,"Actual actions run in Survival");
                    String scenario=System.getProperty("analyzer.nativeScenario","all");
                    if(scenario.equals("portal")){next(Phase.PORTAL_SETUP,tick);return;}
                    if(scenario.equals("build")){next(Phase.BUILD_SETUP,tick);return;}
                    if(scenario.equals("inventory")){inventoryStart=tick+1;next(Phase.INVENTORY,tick);return;}
                    if(scenario.equals("progression")){progressionStart=tick+1;next(Phase.PROGRESSION,tick);return;}
                    if(scenario.equals("tool-recovery")){recoveryStart=tick+1;next(Phase.TOOL_RECOVERY,tick);return;}
                    if(scenario.equals("guard")){guardStart=tick+1;next(Phase.GUARD,tick);return;}
                    if(scenario.equals("task-ui")){taskUiStart=tick+1;next(Phase.TASK_UI,tick);return;}
                    NAV.configureMotion(new AutomationNavigator.MotionProfile(true,120,360,25,5,4));
                    NAV.beginSafetyProfile();require(NAV.travelXYZ(65,161,50),"Original pathfinder accepts a goal beyond a wall");lastYaw=mc.player.getYaw();next(Phase.TRAVEL,tick);
                }
            }
            case TRAVEL -> {
                float step=Math.abs(MathHelper.wrapDegrees(mc.player.getYaw()-lastYaw));lastYaw=mc.player.getYaw();
                require(step<=6.05,"Camera turning stays within the selected degrees-per-second limit");
                if(age>=30){require(NAV.pause(),"Pause accepts the owned original route");next(Phase.PAUSE,tick);}
            }
            case PAUSE -> {
                NAV.tick();
                if(age==6)paused=mc.player.getPos();
                if(age>=18){require(paused.distanceTo(mc.player.getPos())<.05,"Paused route releases movement after ordinary momentum settles");require(NAV.resume(),"Resume rechecks the native route");lastYaw=mc.player.getYaw();next(Phase.RESUME,tick);}
            }
            case RESUME -> {
                float step=Math.abs(MathHelper.wrapDegrees(mc.player.getYaw()-lastYaw));lastYaw=mc.player.getYaw();require(step<=6.05,"Resumed camera remains smooth");
                if(NAV.status().complete()) {
                    require(mc.player.getBlockX()==65&&mc.player.getBlockZ()==50,"Player actually reaches the goal around the wall");
                    require(mc.world.getBlockState(new BlockPos(58,161,50)).isOf(Blocks.STONE),"Walking detour leaves the blocking wall intact");
                    capture(mc,"route");next(Phase.MINE_SETUP,tick);
                } else timeout(age,1200,"Walking did not complete",NAV.status().message());
            }
            case MINE_SETUP -> {
                if(age==0){command(mc,"setblock "+ORE.toShortString().replace(",","")+" minecraft:stone");command(mc,"clear AnalyzerDev minecraft:cobblestone");}
                if(age>=10){require(mc.world.getBlockState(ORE).isOf(Blocks.STONE),"Mining target exists before the action");require(InventoryAutomation.count(Items.COBBLESTONE)==0,"Mining starts without credited drops");NAV.clearStatus();require(NAV.mineByName(1,"minecraft:stone"),"Original miner starts an item goal: "+NAV.status().message());next(Phase.MINE,tick);}
            }
            case MINE -> {
                if(NAV.status().complete()) {
                    require(mc.world.getBlockState(ORE).isAir(),"Normal breaking removes the actual target stone");require(InventoryAutomation.count(Items.COBBLESTONE)>=1,"Actual stone drop reaches the player inventory");capture(mc,"mining");next(Phase.BUILD_SETUP,tick);
                } else timeout(age,1200,"Mining did not complete",NAV.status().message());
            }
            case BUILD_SETUP -> {
                if(age==0){command(mc,"item replace entity AnalyzerDev hotbar.1 with minecraft:cobblestone 16");command(mc,"item replace entity AnalyzerDev hotbar.2 with minecraft:oak_slab 4");}
                if(age>=10){require(InventoryAutomation.count(Items.COBBLESTONE)>=16&&InventoryAutomation.count(Items.OAK_SLAB)>=4,"Real build supplies arrive before construction");supplyBefore=InventoryAutomation.count(Items.COBBLESTONE);NAV.clearStatus();require(NAV.buildSchematic("Native floor and upper slab",BLUEPRINT,BUILD),"Own builder accepts a blueprint");next(Phase.BUILD,tick);}
            }
            case BUILD -> {
                if(NAV.status().complete()) {
                    for(int x=0;x<3;x++)for(int y=0;y<2;y++)for(int z=0;z<3;z++)require(mc.world.getBlockState(BUILD.add(x,y,z)).equals(BLUEPRINT.desiredState(x,y,z,null,List.of())),"Each actual built block state matches the blueprint");
                    require(InventoryAutomation.count(Items.COBBLESTONE)==supplyBefore-10,"Construction consumes real materials");capture(mc,"building");next(Phase.IMPORT,tick);
                } else timeout(age,1600,"Building did not complete",NAV.status().message());
            }
            case IMPORT -> {
                try {
                    if(age==0) {
                        NbtCompound root=new NbtCompound();root.putInt("Version",6);root.putInt("MinecraftDataVersion",4189);
                        NbtCompound region=new NbtCompound();NbtCompound size=new NbtCompound();size.putInt("x",2);size.putInt("y",1);size.putInt("z",1);region.put("Size",size);
                        NbtCompound position=new NbtCompound();position.putInt("x",0);position.putInt("y",0);position.putInt("z",0);region.put("Position",position);
                        NbtList palette=new NbtList();NbtCompound state=new NbtCompound();state.putString("Name","minecraft:cobblestone");palette.add(state);NbtCompound planks=new NbtCompound();planks.putString("Name","minecraft:oak_planks");palette.add(planks);region.put("BlockStatePalette",palette);region.putLongArray("BlockStates",new long[]{4});
                        NbtCompound regions=new NbtCompound();regions.put("floor",region);root.put("Regions",regions);
                        importedFile=mc.runDirectory.toPath().resolve("native-test.litematic");NbtIo.writeCompressed(root,importedFile);
                        var data=SchematicImporter.load(importedFile);require(data.width()==2&&data.materialCounts().get("minecraft:cobblestone")==1&&data.materialCounts().get("minecraft:oak_planks")==1,"Real local Litematica file decodes both palette states");
                        importPlan=AutomationEngine.INSTANCE.schematicPlan(importedFile,new TaskPlanner.Options(65,161,50,75,161,65,10000,TaskPlanner.Direction.NORTH),false);
                        require(importPlan.steps().size()==3&&importPlan.steps().getFirst().action()==TaskPlanner.Action.SUPPLY_MATERIAL&&importPlan.steps().getLast().action()==TaskPlanner.Action.BUILD_SCHEMATIC,"Review places all-material acquisition before construction");
                        command(mc,"clear AnalyzerDev minecraft:cobblestone");command(mc,"clear AnalyzerDev minecraft:oak_planks");command(mc,"item replace entity AnalyzerDev hotbar.4 with minecraft:oak_log 2");
                        command(mc,"setblock 74 161 62 minecraft:stone");
                        var screen=new AutomationScreen(null,AutomationEngine.INSTANCE,AnalyzerClient.CORE);mc.setScreen(screen);screen.onFilesDropped(List.of(importedFile));
                        require(screen.reviewedPlan()!=null&&screen.reviewedPlan().steps().size()==3,"Dropping a real schematic creates a visible three-stage review before starting");
                    }
                    if(age>=10){require(InventoryAutomation.count(Items.COBBLESTONE)==0&&InventoryAutomation.count(Items.OAK_PLANKS)==0&&InventoryAutomation.count(Items.OAK_LOG)>=2,"Material-first fixture starts missing both palette items");capture(mc,"console");mc.setScreen(null);AutomationEngine.INSTANCE.submit(importPlan);next(Phase.IMPORTED_BUILD,tick);}
                } catch(Exception error){throw new IllegalStateException("Native schematic fixture failed",error);}
            }
            case IMPORTED_BUILD -> {
                if(AutomationEngine.INSTANCE.currentStep()<2){require(mc.world.getBlockState(new BlockPos(75,161,65)).isAir()&&mc.world.getBlockState(new BlockPos(76,161,65)).isAir(),"No schematic block is placed during material acquisition");}
                if(AutomationEngine.INSTANCE.currentStep()==2&&!importedSuppliesVerified){require(InventoryAutomation.count(Items.COBBLESTONE)>=1&&InventoryAutomation.count(Items.OAK_PLANKS)>=1,"Every material is owned before construction starts");importedSuppliesVerified=true;}
                if(AutomationEngine.INSTANCE.state().equals("COMPLETED")) {require(importedSuppliesVerified&&mc.world.getBlockState(new BlockPos(75,161,65)).isOf(Blocks.COBBLESTONE)&&mc.world.getBlockState(new BlockPos(76,161,65)).isOf(Blocks.OAK_PLANKS),"Imported file drives actual native construction after mining and crafting all supplies");next(focused("build")?Phase.FINISH:Phase.PORTAL_SETUP,tick);}
                else {require(!AutomationEngine.INSTANCE.state().equals("BLOCKED"),"Imported task remains executable: "+AutomationEngine.INSTANCE.detail());timeout(age,1200,"Imported construction did not finish",AutomationEngine.INSTANCE.detail());}
            }
            case PORTAL_SETUP -> {
                if(age==0){NAV.cancel();AutomationEngine.INSTANCE.stop();command(mc,"tp AnalyzerDev 84.5 161 75.5 0 0");command(mc,"item replace entity AnalyzerDev hotbar.1 with minecraft:obsidian 10");command(mc,"item replace entity AnalyzerDev hotbar.2 with minecraft:cobblestone 32");command(mc,"item replace entity AnalyzerDev hotbar.3 with minecraft:flint_and_steel");}
                if(age>=12) {
                    AutomationEngine.INSTANCE.submit(TaskPlanner.plan("go to the nether",new TaskPlanner.Options(84,161,75,84,161,75,10000,TaskPlanner.Direction.NORTH)));
                    next(Phase.PORTAL,tick);
                }
            }
            case PORTAL -> {
                if(age%100==0)AnalyzerClient.LOGGER.info("Native portal fixture: step={} state={} pos={} obsidian={} cobble={} detail={}",AutomationEngine.INSTANCE.currentStep(),AutomationEngine.INSTANCE.state(),mc.player.getPos(),InventoryAutomation.count(Items.OBSIDIAN),InventoryAutomation.count(Items.COBBLESTONE),AutomationEngine.INSTANCE.detail());
                if(AutomationEngine.INSTANCE.state().equals("COMPLETED")) {
                    require(mc.world.getRegistryKey().getValue().getPath().equals("the_nether"),"Portal construction and actual Nether transport complete");capture(mc,"nether");
                    AutomationEngine.INSTANCE.submit(TaskPlanner.plan("return to the overworld",new TaskPlanner.Options(mc.player.getBlockX(),mc.player.getBlockY(),mc.player.getBlockZ(),mc.player.getBlockX(),mc.player.getBlockY(),mc.player.getBlockZ(),10000,TaskPlanner.Direction.NORTH)));next(Phase.RETURN,tick);
                } else {require(!AutomationEngine.INSTANCE.state().equals("BLOCKED"),"Nether task remains executable: "+AutomationEngine.INSTANCE.detail());timeout(age,3600,"Nether construction/transport did not complete",AutomationEngine.INSTANCE.detail());}
            }
            case RETURN -> {
                if(AutomationEngine.INSTANCE.state().equals("COMPLETED")){require(mc.world.getRegistryKey().getValue().getPath().equals("overworld"),"Actual return transport reaches Overworld");progressionStart=tick+1;next(focused("portal")?Phase.FINISH:Phase.PROGRESSION,tick);}
                else {require(!AutomationEngine.INSTANCE.state().equals("BLOCKED"),"Return task remains executable: "+AutomationEngine.INSTANCE.detail());timeout(age,1600,"Return transport did not complete",AutomationEngine.INSTANCE.detail());}
            }
            case PROGRESSION -> {
                NativeProgressionSmoke.tick(mc,tick-progressionStart);
                if(NativeProgressionSmoke.done()){inventoryStart=tick+1;next(focused("progression")?Phase.FINISH:Phase.INVENTORY,tick);}
            }
            case INVENTORY -> {
                AutomationInventorySmoke.tick(mc,tick-inventoryStart);
                if(AutomationInventorySmoke.done()){recoveryStart=tick+1;next(focused("inventory")?Phase.FINISH:Phase.TOOL_RECOVERY,tick);}
            }
            case TOOL_RECOVERY -> {
                NativeToolRecoverySmoke.tick(mc,tick-recoveryStart);
                if(NativeToolRecoverySmoke.done()){guardStart=tick+1;next(focused("tool-recovery")?Phase.FINISH:Phase.GUARD,tick);}
            }
            case GUARD -> {
                SurvivalGuardSmoke.tick(mc,tick-guardStart);
                if(SurvivalGuardSmoke.done()){taskUiStart=tick+1;next(focused("guard")?Phase.FINISH:Phase.TASK_UI,tick);}
            }
            case TASK_UI -> {
                NativeTaskUiSmoke.tick(mc,tick-taskUiStart);
                if(NativeTaskUiSmoke.done())next(Phase.FINISH,tick);
            }
            case FINISH -> {
                AutomationEngine.INSTANCE.stop();NAV.cancel();require(!mc.options.forwardKey.isPressed()&&!mc.options.backKey.isPressed()&&!mc.options.sprintKey.isPressed()&&!mc.options.jumpKey.isPressed()&&!mc.options.sneakKey.isPressed()&&!mc.options.useKey.isPressed(),"Stop releases every owned movement and item-use key");
                String scenario=System.getProperty("analyzer.nativeScenario","all");
                AnalyzerClient.LOGGER.info("{}: scenario={}, {} checks, {} captures; no Baritone loaded; all selected Survival stages passed",scenario.equals("all")?"ANALYZER_NATIVE_AUTOMATION_COMPLETE":"ANALYZER_NATIVE_FOCUSED_COMPLETE",scenario,checks,captures);mc.scheduleStop();
                AutomationInputDiagnostics.developmentOverride(null);
            }
        }
    }
    private static boolean focused(String scenario){return scenario.equals(System.getProperty("analyzer.nativeScenario","all"));}
    private static void next(Phase next,int tick){phase=next;entered=tick+1;AnalyzerClient.LOGGER.info("Native automation acceptance: {}",next);}
    private static void require(boolean condition,String detail){if(!condition)throw new IllegalStateException("Native automation acceptance failed: "+detail);checks++;}
    private static void timeout(int age,int limit,String detail,String status){if(age>limit)throw new IllegalStateException(detail+": "+status);}
    private static void command(MinecraftClient mc,String command){mc.getServer().execute(()->mc.getServer().getCommandManager().executeWithPrefix(mc.getServer().getCommandSource().withSilent(),command));}
    private static void capture(MinecraftClient mc,String label){ScreenshotRecorder.saveScreenshot(mc.runDirectory,"analyzer-native-automation-"+label+".png",mc.getFramebuffer(),text->AnalyzerClient.LOGGER.info("Native automation capture: {}",text.getString()));captures++;}
}
