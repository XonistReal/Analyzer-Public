package dev.analyzer;

import dev.analyzer.automation.AutomationEngine;
import dev.analyzer.automation.BaritoneBridge;
import dev.analyzer.automation.InventoryAutomation;
import dev.analyzer.automation.ShelterSchematic;
import dev.analyzer.automation.TaskPlanner;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import org.lwjgl.opengl.GL11;
import java.util.List;

/** Isolated integrated-server fixtures; task actions use the real Baritone and Survival interaction path. */
final class AutomationBridgeSmoke {
    static final int LAST_TICK = 870;
    private static final BaritoneBridge BRIDGE = new BaritoneBridge();
    private static final BlockPos STONE = new BlockPos(56,161,52), IRON = new BlockPos(57,161,52);
    private static final BlockPos SHELTER = new BlockPos(62,160,48);
    private static final ShelterSchematic SCHEMATIC = new ShelterSchematic(Blocks.COBBLESTONE.getDefaultState());
    private static Vec3d pausedAt;
    private static int phase, assertions, screenshots, materialsBefore;
    private AutomationBridgeSmoke() { }

    static void tick(MinecraftClient mc, int t) {
        phase = t;
        BRIDGE.tick();
        switch (t) {
            case 0 -> {
                mc.setScreen(null); AnalyzerClient.CORE.resetAll();
                mc.options.setPerspective(Perspective.FIRST_PERSON);
                AutomationEngine.INSTANCE.stop();
                require(FabricLoader.getInstance().isDevelopmentEnvironment(), "Automation smoke is development-only");
                require(FabricLoader.getInstance().isModLoaded("baritone") && BRIDGE.available(), "Official Baritone Fabric API mod actually loads");
                command(mc,"kill @e[type=!minecraft:player]");
                command(mc,"gamemode creative AnalyzerDev");
                command(mc,"clear AnalyzerDev");
                command(mc,"effect clear AnalyzerDev");
                command(mc,"fill 40 160 40 85 160 75 minecraft:bedrock");
                command(mc,"fill 40 161 40 85 169 75 minecraft:air");
                command(mc,"tp AnalyzerDev 50.5 161 50.5 -90 12");
                command(mc,"time set 1000"); command(mc,"weather clear");
                command(mc,"gamerule doTileDrops true");
                command(mc,"gamerule naturalRegeneration false");
            }
            case 10 -> {
                command(mc,"gamemode survival AnalyzerDev");
                command(mc,"item replace entity AnalyzerDev hotbar.0 with minecraft:diamond_pickaxe");
                command(mc,"effect give AnalyzerDev minecraft:instant_health 1 5 true");
                command(mc,"effect give AnalyzerDev minecraft:saturation 1 5 true");
                mc.player.getInventory().selectedSlot=0;
            }
            case 20 -> {
                require(mc.interactionManager.getCurrentGameMode()==GameMode.SURVIVAL, "Baritone action fixture is genuine Survival mode");
                require(mc.player.getBlockPos().equals(new BlockPos(50,161,50)), "Travel fixture starts at observed coordinates");
                require(BRIDGE.travelXZ(56,50), "Bridge dispatches real six-block GoalXZ travel");
            }
            case 25 -> {
                require(BRIDGE.status().owned(), "Analyzer owns its dispatched travel process");
                require(BRIDGE.pause(), "Owned travel accepts temporary-process pause");
            }
            case 30 -> { require(BRIDGE.status().paused(), "Bridge reports actual paused lease"); pausedAt=mc.player.getPos(); }
            case 38 -> require(mc.player.getPos().distanceTo(pausedAt)<0.35, "Safe pause stops player movement on the flat route");
            case 40 -> require(BRIDGE.resume(), "Owned route resumes without replacing its goal");
            case 110 -> {
                require(BRIDGE.status().complete() && mc.player.getBlockX()==56 && mc.player.getBlockZ()==50, "Baritone moves the actual player six blocks to the goal");
                capture(mc,"analyzer-bot-travel.png");
                command(mc,"setblock 56 161 52 minecraft:stone");
                command(mc,"kill @e[type=minecraft:item]");
            }
            case 130 -> {
                require(mc.world.getBlockState(STONE).isOf(Blocks.STONE), "Stone mining fixture is a real loaded block");
                require(InventoryAutomation.count(Items.COBBLESTONE)==0, "Cobblestone starts absent from inventory");
                require(BRIDGE.mineByName(1,"minecraft:stone"), "Public Baritone mineByName starts actual mining");
            }
            case 300 -> {
                require(BRIDGE.status().complete() && InventoryAutomation.count(Items.COBBLESTONE)>=1, "Survival stone mining yields a verified picked-up cobblestone");
                require(mc.world.getBlockState(STONE).isAir(), "Baritone's ordinary breaking removes the actual stone block");
                capture(mc,"analyzer-bot-stone-mining.png");
                command(mc,"setblock 57 161 52 minecraft:iron_ore");
            }
            case 320 -> {
                require(mc.world.getBlockState(IRON).isOf(Blocks.IRON_ORE), "Iron mining fixture is a real loaded ore");
                require(InventoryAutomation.count(Items.RAW_IRON)==0, "Raw iron starts absent from inventory");
                require(BRIDGE.mineByName(1,"minecraft:iron_ore","minecraft:deepslate_iron_ore"), "Mine bridge accepts alternate ore IDs");
            }
            case 490 -> {
                require(BRIDGE.status().complete() && InventoryAutomation.count(Items.RAW_IRON)>=1, "Iron ore mining yields an actual raw-iron drop in inventory");
                require(mc.world.getBlockState(IRON).isAir(), "The real ore block is gone after normal Survival mining");
                capture(mc,"analyzer-bot-iron-mining.png");
            }
            case 500 -> {
                // An almost-complete shelter is a bounded fixture. Baritone must supply the final two blocks.
                command(mc,"fill 62 160 48 68 160 54 minecraft:cobblestone");
                command(mc,"fill 62 164 48 68 164 54 minecraft:cobblestone");
                command(mc,"fill 62 161 48 62 163 54 minecraft:cobblestone");
                command(mc,"fill 68 161 48 68 163 54 minecraft:cobblestone");
                command(mc,"fill 62 161 48 68 163 48 minecraft:cobblestone");
                command(mc,"fill 62 161 54 68 163 54 minecraft:cobblestone");
                command(mc,"fill 63 161 49 67 163 53 minecraft:air");
                command(mc,"fill 65 161 48 65 162 48 minecraft:air");
                command(mc,"fill 64 161 48 64 162 48 minecraft:air");
                command(mc,"tp AnalyzerDev 64.5 161 45.5 0 15");
                command(mc,"item replace entity AnalyzerDev hotbar.1 with minecraft:cobblestone 64");
            }
            case 520 -> {
                var observed=SCHEMATIC.progress(mc.world,SHELTER);
                require(observed.required()==168 && observed.placed()==166 && observed.missing()==2 && !observed.complete(), "Original shelter fixture has exactly two observed missing material blocks");
                require(SCHEMATIC.siteProblem(mc.world,SHELTER).isEmpty(), "Shelter preflight accepts this dry loaded site");
                materialsBefore=InventoryAutomation.count(Items.COBBLESTONE);
                require(BRIDGE.buildShelter(SHELTER,"minecraft:cobblestone"), "Public IBuilderProcess receives the original bounded shelter schematic");
            }
            case 680 -> {
                require(BRIDGE.status().complete() && SCHEMATIC.progress(mc.world,SHELTER).complete(), "Baritone finishes the two missing shelter blocks and all 245 actual cells match");
                require(InventoryAutomation.count(Items.COBBLESTONE)<=materialsBefore-2, "Survival building consumes actual inventory material");
                require(mc.world.getBlockState(new BlockPos(61,160,48)).isOf(Blocks.BEDROCK), "The neighboring floor remains outside the shelter schematic");
                command(mc,"tp AnalyzerDev 64.5 161 45.5 0 10");
            }
            case 690 -> capture(mc,"analyzer-bot-shelter-patch.png");
            case 700 -> require(BRIDGE.travelXZ(80,50), "Second owned route starts for the manual-takeover check");
            case 710 -> {
                var actual=baritone.api.BaritoneAPI.getProvider().getPrimaryBaritone();
                require(actual.getCommandManager().execute("goto 72 50"), "Real public Baritone command manager starts the replacement manual route");
            }
            case 712 -> {
                BRIDGE.tick();
                require(BRIDGE.status().state()==BaritoneBridge.State.INTERRUPTED && !BRIDGE.status().owned(), "Changed manual goal relinquishes Analyzer ownership");
                require(!BRIDGE.cancel(), "Analyzer cancel refuses to cancel an unowned manual route");
                require(baritone.api.BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().isActive(), "Manual route stays active after Analyzer refuses cancel");
            }
            case 800 -> {
                require(mc.player.getBlockX()==72 && mc.player.getBlockZ()==50, "Replacement manual Baritone route reaches its own actual destination");
                baritone.api.BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
                // This cancellation releases the test's own local manual fixture, never a real user's task.
                BRIDGE.clearStatus();
                command(mc,"effect clear AnalyzerDev");
                command(mc,"effect give AnalyzerDev minecraft:instant_health 1 5 true");
                command(mc,"effect give AnalyzerDev minecraft:saturation 1 5 true");
            }
            case 815 -> {
                var plan=new TaskPlanner.Plan("Survival safety check","Short local route",List.of(
                        new TaskPlanner.Step("travel",TaskPlanner.Action.TRAVEL,"Walk to the safety goal","",0,82,161,50)),List.of());
                AnalyzerClient.CORE.setEnabled("auto-weapon",true);
                AutomationEngine.INSTANCE.submit(plan);
                require(AutomationEngine.INSTANCE.isRunning(), "Real mission controller starts the reviewed route");
                require(!AnalyzerClient.CORE.isEnabled("auto-weapon"), "Mission start temporarily suspends an enabled conflicting action module");
            }
            case 820 -> command(mc,"damage AnalyzerDev 15 minecraft:generic");
            case 830 -> {
                require(mc.player.getHealth()<6, "Safety check receives actual server-authoritative health loss");
                require(AutomationEngine.INSTANCE.state().equals("BLOCKED") && AutomationEngine.INSTANCE.detail().contains("Low health"), "Mission controller blocks when actual health falls below its configured limit");
                require(AutomationEngine.INSTANCE.pathStatus().paused(), "Low-health controller block pauses the owned Baritone route");
                capture(mc,"analyzer-bot-low-health.png");
                AnalyzerClient.CORE.setEnabled("expedition",false);
            }
            case 840 -> {
                require(AutomationEngine.INSTANCE.state().equals("STOPPED"), "Disabling the Automation module stops an already blocked task");
                require(!AutomationEngine.INSTANCE.isRunning() && !AutomationEngine.INSTANCE.pathStatus().owned(), "Stop releases the safety controller's owned process");
                require(AnalyzerClient.CORE.isEnabled("auto-weapon"), "Module-off cleanup restores the previously enabled conflicting action module");
                command(mc,"effect give AnalyzerDev minecraft:instant_health 1 5 true");
                command(mc,"gamemode creative AnalyzerDev");
                command(mc,"gamerule naturalRegeneration true");
            }
            case LAST_TICK -> {
                require(GL11.glGetError()==GL11.GL_NO_ERROR,"Automation final GL state");
                AnalyzerClient.LOGGER.info("ANALYZER_AUTOMATION_COMPLETE: {} assertions, {} screenshots; official Baritone travel, mining, shelter patch, pause/resume, takeover and health safety verified",assertions,screenshots);
            }
        }
    }
    private static void command(MinecraftClient mc,String text) {
        mc.getServer().execute(()->mc.getServer().getCommandManager().executeWithPrefix(mc.getServer().getCommandSource().withSilent(),text));
    }
    private static void require(boolean result,String message) {
        if(!result) throw new IllegalStateException("Automation check failed at "+phase+": "+message+" / "+BRIDGE.status());
        assertions++; AnalyzerClient.LOGGER.info("Automation check passed: {}",message);
    }
    private static void capture(MinecraftClient mc,String name) {
        require(GL11.glGetError()==GL11.GL_NO_ERROR,"GL state before "+name);
        ScreenshotRecorder.saveScreenshot(mc.runDirectory,name,mc.getFramebuffer(),text->AnalyzerClient.LOGGER.info("Automation screenshot: {}",text.getString()));
        screenshots++;
    }
}
