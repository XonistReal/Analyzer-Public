package dev.analyzer;

import dev.analyzer.automation.AutomationEngine;
import dev.analyzer.automation.AutomationInputDiagnostics;
import dev.analyzer.automation.AutomationNavigator;
import dev.analyzer.automation.InventoryAutomation;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.opengl.GL11;

import java.util.Set;

/** Isolated real Survival regression: vines, rooted timber fallback, preserved unsafe canopy and resumed mining. */
final class NativeDescentSmoke {
    private enum Stage { VINE_SETUP, VINE, TIMBER_SETUP, TIMBER, UNSAFE_SETUP, UNSAFE, FINISHED }
    private static final AutomationNavigator NAV = new AutomationNavigator();
    private static Stage stage = Stage.VINE_SETUP;
    private static int tick, entered, assertions, captures;
    private static boolean configured, vineCapture, timberCapture;
    private static float minimumHealth = 20;
    private NativeDescentSmoke() { }
    static boolean done() { return stage == Stage.FINISHED; }
    static void tick(MinecraftClient mc, int t) {
        if (done()) return;
        if (!FabricLoader.getInstance().isDevelopmentEnvironment() || !Boolean.getBoolean("analyzer.smoke") || !Boolean.getBoolean("analyzer.nativeAutomationSmoke"))
            throw new IllegalStateException("Descent fixtures are development-only");
        tick = t;
        if (t > 3600 || age() > 1400) throw new IllegalStateException("Descent fixture timed out at " + stage + ": " + NAV.status() + " / " + NAV.diagnostics() + " / " + NAV.groundDiagnostics());
        if (stage == Stage.VINE || stage == Stage.TIMBER || stage == Stage.UNSAFE) {
            NAV.tick(); minimumHealth = Math.min(minimumHealth, mc.player.getHealth());
            require(mc.player.getHealth() >= 19.5, "Natural descent never trades a long fall for health");
        }
        switch (stage) {
            case VINE_SETUP -> {
                if (!configured) { setup(mc, true, true); configured = true; }
                if (age() < 35) return;
                ready(mc); require(mc.world.getBlockState(new BlockPos(73, 165, 70)).isOf(Blocks.VINE), "The authoritative jungle fixture has a real continuous vine");
                require(NAV.mineByName(1, "minecraft:stone"), "The original intended stone mining task starts above the tree"); enter(Stage.VINE);
            }
            case VINE -> {
                require(!NAV.status().failed(), "Mining continues through vine recovery: " + NAV.status().message());
                var recovery = NAV.groundDiagnostics();
                if (!vineCapture && recovery.climbs() > 0 && mc.player.getY() < 168 && mc.player.getY() > 162) { capture(mc, "vine-descent"); vineCapture = true; }
                if (!NAV.status().complete()) return;
                require(vineCapture && recovery.climbs() >= 1 && recovery.cuts() == 0, "The first recovery actually descends the vine and preserves the trunk");
                completedMining(mc); require(mc.world.getBlockState(new BlockPos(70, 165, 70)).isOf(Blocks.JUNGLE_LOG), "Vine descent leaves the natural trunk intact");
                capture(mc, "vine-resumed-mining"); enter(Stage.TIMBER_SETUP); configured = false;
            }
            case TIMBER_SETUP -> {
                if (!configured) { setup(mc, false, true); configured = true; }
                if (age() < 35) return;
                ready(mc); require(NAV.mineByName(1, "minecraft:stone"), "A second original mining task starts above a tree with no vines"); enter(Stage.TIMBER);
            }
            case TIMBER -> {
                require(!NAV.status().failed(), "Mining continues through checked rooted-trunk recovery: " + NAV.status().message());
                var recovery = NAV.groundDiagnostics();
                if (!timberCapture && recovery.cuts() >= 3 && mc.player.getY() > 162) { capture(mc, "rooted-trunk-descent"); timberCapture = true; }
                if (!NAV.status().complete()) return;
                require(timberCapture && recovery.cuts() >= 8 && recovery.climbs() == 0, "The fallback uses real one-block rooted timber cuts instead of a long drop");
                completedMining(mc); require(InventoryAutomation.count(Items.JUNGLE_LOG) >= 5, "Normal descent cuts produce actual jungle-log drops in the inventory");
                enter(Stage.UNSAFE_SETUP); configured = false;
            }
            case UNSAFE_SETUP -> {
                if (!configured) { setup(mc, false, false); configured = true; }
                if (age() < 35) return;
                ready(mc); require(NAV.recoverWorkGround(), "Unsafe canopy is surveyed without assuming a route"); enter(Stage.UNSAFE);
            }
            case UNSAFE -> {
                if (!NAV.status().failed()) return;
                require(mc.player.getY() > 170.9 && serverPlayer(mc).getY() > 170.9, "No root or continuous vine means no unchecked drop on client or server");
                for (BlockPos pos : BlockPos.iterate(68, 170, 68, 72, 170, 72)) require(mc.world.getBlockState(pos).isOf(Blocks.JUNGLE_LEAVES), "An unsupported canopy is preserved rather than cut over an air gap");
                require(NAV.groundDiagnostics().cuts() == 0 && NAV.groundDiagnostics().climbs() == 0, "Rejected descent performs no fabricated break or climb");
                require(minimumHealth >= 19.5 && serverPlayer(mc).getHealth() >= 19.5, "All descent fixtures preserve actual Survival health");
                NAV.cancel(); AutomationEngine.INSTANCE.stop(); require(!mc.options.forwardKey.isPressed() && !mc.options.backKey.isPressed() && !mc.options.leftKey.isPressed() && !mc.options.rightKey.isPressed() && !mc.options.jumpKey.isPressed() && !mc.options.sneakKey.isPressed() && !mc.options.attackKey.isPressed(), "Stop releases all descent-owned controls");
                capture(mc, "unsafe-descent-preserved");
                command(mc, "gamerule randomTickSpeed 3");
                AnalyzerClient.LOGGER.info("ANALYZER_NATIVE_DESCENT_COMPLETE: {} assertions, {} captures, {} elapsed ticks; actual Survival vine descent, rooted timber fallback, automatic mining continuation and unsafe-gap preservation passed", assertions, captures, tick);
                enter(Stage.FINISHED);
            }
            case FINISHED -> { }
        }
    }
    private static void setup(MinecraftClient mc, boolean vines, boolean rooted) {
        NAV.cancel(); NAV.clearStatus(); AutomationEngine.INSTANCE.stop(); mc.setScreen(null); AutomationInputDiagnostics.developmentOverride(Set.of());
        AnalyzerClient.CORE.setValue("expedition", "max-drop", 1.0);
        NAV.configureMotion(new AutomationNavigator.MotionProfile(true, 180, 720, 25, 3, 4));
        command(mc, "kill @e[type=!minecraft:player]"); command(mc, "difficulty peaceful"); command(mc, "gamemode survival AnalyzerDev");
        command(mc, "gamerule randomTickSpeed 0");
        command(mc, "clear AnalyzerDev"); command(mc, "effect clear AnalyzerDev"); command(mc, "effect give AnalyzerDev minecraft:instant_health 1 4 true"); command(mc, "effect give AnalyzerDev minecraft:saturation 180 4 true");
        command(mc, "fill 60 160 58 84 160 82 minecraft:bedrock"); command(mc, "fill 60 161 58 84 178 82 minecraft:air");
        command(mc, "setblock 70 160 70 minecraft:dirt");
        if (rooted) command(mc, "fill 70 161 70 70 170 70 minecraft:jungle_log");
        command(mc, "fill 68 170 68 72 170 72 minecraft:jungle_leaves[persistent=false,distance=1]");
        if (rooted) command(mc, "setblock 70 170 70 minecraft:jungle_log");
        if (vines) for (int y = 170; y >= 161; y--) command(mc, "setblock 73 " + y + " 70 minecraft:vine[west=true]");
        command(mc, "setblock 77 161 70 minecraft:stone"); command(mc, "give AnalyzerDev minecraft:diamond_pickaxe"); command(mc, "give AnalyzerDev minecraft:iron_axe");
        command(mc, "tp AnalyzerDev 70.5 171 70.5 0 0"); minimumHealth = 20;
    }
    private static void ready(MinecraftClient mc) {
        require(mc.player.isOnGround() && mc.player.getY() > 170.9 && !mc.player.getAbilities().creativeMode, "The fixture begins in actual Survival on top of a ten-block jungle canopy");
        require(NAV.needsWorkGroundRecovery() && InventoryAutomation.count(Items.COBBLESTONE) == 0, "A real ground recovery is needed before the missing stone is collected");
    }
    private static void completedMining(MinecraftClient mc) {
        require(mc.player.getY() < 162 && serverPlayer(mc).getY() < 162, "The player actually reaches lower work ground on client and integrated server");
        require(InventoryAutomation.count(Items.COBBLESTONE) >= 1 && mc.world.getBlockState(new BlockPos(77, 161, 70)).isAir(), "The original native mining goal completes after descent with the real stone drop");
        require(mc.player.getHealth() >= 19.5 && serverPlayer(mc).getHealth() >= 19.5, "Ground recovery causes no meaningful fall damage");
    }
    private static net.minecraft.server.network.ServerPlayerEntity serverPlayer(MinecraftClient mc) { return mc.getServer().getPlayerManager().getPlayer(mc.player.getUuid()); }
    private static int age() { return tick - entered; }
    private static void enter(Stage next) { stage = next; entered = tick; AnalyzerClient.LOGGER.info("Native descent acceptance: {}", next); }
    private static void command(MinecraftClient mc, String command) { mc.getServer().execute(() -> mc.getServer().getCommandManager().executeWithPrefix(mc.getServer().getCommandSource().withSilent(), command)); }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException("Native descent check failed at " + stage + " / " + tick + ": " + message); assertions++; }
    private static void capture(MinecraftClient mc, String name) { require(GL11.glGetError() == GL11.GL_NO_ERROR, "Clean GL state before " + name); ScreenshotRecorder.saveScreenshot(mc.runDirectory, "analyzer-native-recovery-" + name + ".png", mc.getFramebuffer(), text -> AnalyzerClient.LOGGER.info("Native descent capture: {}", text.getString())); captures++; }
}
