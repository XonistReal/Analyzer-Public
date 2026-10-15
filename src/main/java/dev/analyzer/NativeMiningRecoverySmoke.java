package dev.analyzer;

import dev.analyzer.automation.AutomationEngine;
import dev.analyzer.automation.AutomationNavigator;
import dev.analyzer.automation.InventoryAutomation;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.List;
import java.util.UUID;

/** Real Survival mining through leaves, grass and successive natural excavation steps; no runtime teleport. */
final class NativeMiningRecoverySmoke {
    private enum Stage { SETUP, LOG, LOG_VERIFY, STONE, VERIFY, SAND, SAND_VERIFY, FINISHED }
    private record Observed(int tick, int logs, int cobble, int stone, int dirt, int grass, boolean protectedIntact,
                            boolean fluidIntact, boolean fallingIntact, float health, double y, int sand, int sandBlocks, int fallingEntities) { }
    private static final BlockPos LOG = new BlockPos(110, 164, 111), LEAF = LOG.west();
    private static final Box PROTECTED = new Box(122, 154, 102, 125, 167, 105);
    private static final BlockPos WATER_ORE = new BlockPos(125, 159, 120), FALLING_ORE = new BlockPos(120, 159, 122);
    private static final BlockPos SAND_MIN = new BlockPos(112, 163, 125), SAND_MAX = new BlockPos(131, 164, 139);
    private static Stage stage = Stage.SETUP;
    private static AutomationNavigator nav;
    private static boolean initialized;
    private static volatile boolean ready, pending;
    private static volatile Observed observed;
    private static volatile String failure = "";
    private static int ticks, entered, lastSnapshot = -100, checks, captures, initialStone, initialDirt, initialGrass;
    private static double lowestY = Double.MAX_VALUE;
    private static int lastActualCount, lastActualProgress;
    private NativeMiningRecoverySmoke() { }
    static boolean done() { return stage == Stage.FINISHED; }
    static int assertions() { return checks; }
    static int captures() { return captures; }

    static void tick(MinecraftClient mc, int tick) {
        if (done()) return;
        if (!FabricLoader.getInstance().isDevelopmentEnvironment() || !Boolean.getBoolean("analyzer.smoke") || !Boolean.getBoolean("analyzer.nativeAutomationSmoke"))
            throw new IllegalStateException("Mining recovery fixture is development-only");
        if (mc.world == null || mc.player == null || mc.getServer() == null) return;
        ticks = tick;
        if (!initialized) { initialized = true; entered = tick; setup(mc); }
        if (!failure.isBlank()) throw new IllegalStateException("Mining recovery server fixture: " + failure);
        if (ready && tick % 80 == 0) AnalyzerClient.LOGGER.info("ANALYZER_NATIVE_MINING_OBSERVED: stage={}, clientCobble={}, serverCobble={}, clientSand={}, serverSand={}, player={}, status={}, route={}",
                stage, InventoryAutomation.count(Items.COBBLESTONE), observed == null ? -1 : observed.cobble,
                InventoryAutomation.count(Items.SAND), observed == null ? -1 : observed.sand, mc.player.getPos(), nav.status(), nav.diagnostics());
        int stageBudget = stage == Stage.SAND ? 18000 : 5600;
        if (ticks - entered > stageBudget) throw new IllegalStateException("Mining recovery timeout at " + stage + ": " + nav.diagnostics() + " / " + nav.status());
        if (ready && !pending && ticks - lastSnapshot >= 10) snapshot(mc);
        if (stage == Stage.STONE || stage == Stage.SAND) {
            int count = InventoryAutomation.count(stage == Stage.SAND ? Items.SAND : Items.COBBLESTONE);
            if (count != lastActualCount) {lastActualCount = count; lastActualProgress = ticks;}
            if (ticks - lastActualProgress > 1200) throw new IllegalStateException("No actual item progress for sixty seconds at " + stage + ": " + nav.diagnostics() + " / " + nav.status());
        }
        switch (stage) {
            case SETUP -> {
                if (!ready || observed == null || ticks - entered < 30 || mc.player.squaredDistanceTo(106.5, 163, 111.5) > .25
                        || !mc.world.getBlockState(LOG).isOf(Blocks.OAK_LOG) || !mc.world.getBlockState(LEAF).isOf(Blocks.OAK_LEAVES)) return;
                require(!mc.player.getAbilities().creativeMode && observed.logs == 0 && observed.cobble == 0, "Survival starts without credited wood or stone");
                require(observed.protectedIntact && observed.fluidIntact && observed.fallingIntact, "Protected schematic, fluid and falling-ceiling preconditions are real server blocks");
                initialStone = observed.stone; initialDirt = observed.dirt; initialGrass = observed.grass;
                require(initialStone > 96 && initialDirt > 96 && initialGrass > 96, "The stone source is buried beneath actual dirt and grass layers");
                require(observed.sand == 0 && observed.sandBlocks == 600 && observed.fallingEntities == 0, "The large sand goal starts with 600 actual two-layer sand blocks and no credited inventory");
                if (sandOnly()) { beginSand(); return; }
                require(nav.mineByName(1, "minecraft:oak_log"), "Production miner accepts the leaf-covered wood goal"); enter(Stage.LOG);
            }
            case LOG -> {
                nav.tick(); require(!nav.status().failed(), "Covered wood continues: " + nav.status().message());
                if (nav.status().complete()) { nav.clearStatus(); enter(Stage.LOG_VERIFY); snapshot(mc); }
            }
            case LOG_VERIFY -> {
                if (!fresh(10)) return;
                require(observed.logs >= 1 && InventoryAutomation.count(Items.OAK_LOG) >= 1, "Normal wood breaking and pickup put the actual log in both server and client inventories");
                require(mc.world.getBlockState(LOG).isAir() && mc.world.getBlockState(LEAF).isAir(), "The miner clears its leaf occluder and requested log through real breaks");
                capture(mc, "covered-wood"); nav.clearStatus();
                require(nav.mineByName(96, "minecraft:stone"), "Production miner accepts the user's substantial 96-cobblestone goal"); enter(Stage.STONE);
            }
            case STONE -> {
                nav.tick(); lowestY = Math.min(lowestY, mc.player.getY());
                require(!nav.status().failed(), "Grass-covered excavation continues beyond the first few blocks: " + nav.status().message() + " / " + nav.diagnostics());
                if (nav.status().complete()) { nav.cancel(); enter(Stage.VERIFY); snapshot(mc); }
            }
            case VERIFY -> {
                if (!fresh(10)) return;
                require(observed.cobble >= 96 && InventoryAutomation.count(Items.COBBLESTONE) >= 96, "The full 96 cobblestone goal is proven by actual server-backed inventory");
                require(initialStone - observed.stone >= 96, "At least 96 actual world stone blocks were mined instead of crediting actions or a preview");
                require(observed.grass < initialGrass && observed.dirt < initialDirt && lowestY <= 160, "Mining clears real grass/dirt cover and physically follows successive lower supported work cells");
                require(observed.protectedIntact && observed.fluidIntact && observed.fallingIntact, "Mining leaves protected schematic bounds, fluid-adjacent ore and unsupported falling ceilings intact");
                require(observed.health == 20 && !mc.options.forwardKey.isPressed() && !mc.options.backKey.isPressed() && !mc.options.jumpKey.isPressed(), "The sustained mining run ends healthy with movement released");
                capture(mc, "grass-covered-96-stone");
                if (System.getProperty("analyzer.nativeScenario", "").equals("mining-terrain")) {
                    stage = Stage.FINISHED;
                    AnalyzerClient.LOGGER.info("ANALYZER_NATIVE_TERRAIN_RECOVERY_COMPLETE: {} assertions, {} captures; leaf occlusion, grass/dirt excavation, successive supported steps, 96 actual cobblestone and protected/fluid/falling invariants", checks, captures);
                } else beginSand();
            }
            case SAND -> {
                nav.tick();
                require(!nav.status().failed(), "The 600-sand task keeps executing: " + nav.status().message());
                require(observed == null || observed.fallingEntities == 0, "Requested sand is mined top down without creating a falling-block ceiling");
                if (nav.status().complete()) { nav.cancel(); enter(Stage.SAND_VERIFY); snapshot(mc); }
            }
            case SAND_VERIFY -> {
                if (!fresh(10)) return;
                require(observed.sand >= 600 && InventoryAutomation.count(Items.SAND) >= 600, "The full 600-sand goal reaches the real server and client inventories");
                require(observed.sandBlocks <= 1 && observed.fallingEntities == 0, "At least 599 of the 600 supplied sand blocks are removed normally; the only other allowed source is the single isolated ceiling marker");
                require(observed.health == 20 && observed.y >= 162.9, "After underground mining the player returns through supported access to the sand surface without fall damage");
                require(!mc.options.forwardKey.isPressed() && !mc.options.backKey.isPressed() && !mc.options.jumpKey.isPressed() && !mc.options.sneakKey.isPressed(), "Completing the large sand task releases task-owned movement");
                capture(mc, "600-sand"); stage = Stage.FINISHED;
                AnalyzerClient.LOGGER.info("{}: {} assertions, {} captures; {}", sandOnly() ? "ANALYZER_NATIVE_SAND_RECOVERY_COMPLETE" : "ANALYZER_NATIVE_MINING_RECOVERY_COMPLETE", checks, captures,
                        sandOnly() ? "600 actual sand, ordinary top-down mining, no falling entities, real pickups" : "leaf occlusion, grass/dirt excavation, successive supported steps, 96 actual cobblestone, return through preserved access, 600 actual sand and protected/fluid/falling invariants");
            }
            case FINISHED -> { }
        }
    }
    private static void setup(MinecraftClient mc) {
        AutomationEngine.INSTANCE.stop(); mc.setScreen(null);
        if (mc.player.currentScreenHandler != mc.player.playerScreenHandler) mc.player.closeHandledScreen();
        for (var module : AnalyzerClient.CORE.modules()) {
            if (module.category() == dev.analyzer.core.Category.COMBAT || module.category() == dev.analyzer.core.Category.HVH
                    || module.category() == dev.analyzer.core.Category.MOVEMENT || module.category() == dev.analyzer.core.Category.PLAYER) AnalyzerClient.CORE.setEnabled(module.id(), false);
        }
        nav = new AutomationNavigator(); nav.setMiningExclusion(PROTECTED);
        nav.configureMotion(new AutomationNavigator.MotionProfile(true, 180, 900, 35, 2, 3));
        UUID id = mc.player.getUuid();
        mc.getServer().execute(() -> {
            try {
                ServerPlayerEntity player = mc.getServer().getPlayerManager().getPlayer(id);
                if (player == null || !player.getName().getString().equals("AnalyzerDev")) throw new IllegalStateException("Expected isolated AnalyzerDev player");
                for (int x = 6; x <= 8; x++) for (int z = 6; z <= 8; z++) player.getServerWorld().getChunk(x, z);
                for (String cmd : List.of("gamemode survival AnalyzerDev", "clear AnalyzerDev", "effect clear AnalyzerDev", "difficulty peaceful", "gamerule doMobSpawning false",
                        "fill 102 154 102 134 154 140 minecraft:bedrock", "fill 102 155 102 134 159 140 minecraft:stone",
                        "fill 102 160 102 134 161 140 minecraft:dirt", "fill 102 162 102 134 162 140 minecraft:grass_block",
                        "fill 102 163 102 134 174 140 minecraft:air", "fill 102 163 102 134 163 140 minecraft:short_grass",
                        "setblock 110 164 111 minecraft:oak_log", "setblock 109 164 111 minecraft:oak_leaves[persistent=false,distance=1]",
                        "setblock 125 159 121 minecraft:water", "setblock 120 160 122 minecraft:sand",
                        "fill 112 163 125 131 164 139 minecraft:sand",
                        "kill @e[type=minecraft:item,x=102,y=154,z=102,dx=32,dy=20,dz=38]", "tp AnalyzerDev 106.5 163 111.5 -90 0",
                        "item replace entity AnalyzerDev hotbar.0 with minecraft:iron_pickaxe", "item replace entity AnalyzerDev hotbar.1 with minecraft:iron_axe",
                        "item replace entity AnalyzerDev hotbar.2 with minecraft:diamond_shovel[minecraft:enchantments={levels:{\"minecraft:efficiency\":5}}]", "effect give AnalyzerDev minecraft:instant_health 1 5 true", "effect give AnalyzerDev minecraft:saturation 1200 5 true")) command(mc, cmd);
                ready = true;
            } catch (RuntimeException e) { failure = e.toString(); }
        });
    }
    private static void snapshot(MinecraftClient mc) {
        if (pending || !ready) return; pending = true; lastSnapshot = ticks; int at = ticks; UUID id = mc.player.getUuid();
        mc.getServer().execute(() -> {
            try {
                ServerPlayerEntity player = mc.getServer().getPlayerManager().getPlayer(id); var world = player.getServerWorld();
                int logs = 0, cobble = 0, stone = 0, dirt = 0, grass = 0, sand = 0, sandBlocks = 0; boolean protectedIntact = true;
                for (int slot = 0; slot < player.getInventory().size(); slot++) { var stack = player.getInventory().getStack(slot); if (stack.isOf(Items.OAK_LOG)) logs += stack.getCount(); if (stack.isOf(Items.COBBLESTONE)) cobble += stack.getCount(); if (stack.isOf(Items.SAND)) sand += stack.getCount(); }
                for (BlockPos pos : BlockPos.iterate(new BlockPos(102, 155, 102), new BlockPos(130, 162, 124))) {
                    var state = world.getBlockState(pos); if (state.isOf(Blocks.STONE)) stone++; if (state.isOf(Blocks.DIRT)) dirt++; if (state.isOf(Blocks.GRASS_BLOCK)) grass++;
                    if (PROTECTED.contains(pos.toCenterPos()) && pos.getY() <= 159) protectedIntact &= state.isOf(Blocks.STONE);
                }
                for (BlockPos pos : BlockPos.iterate(SAND_MIN, SAND_MAX)) if (world.getBlockState(pos).isOf(Blocks.SAND)) sandBlocks++;
                observed = new Observed(at, logs, cobble, stone, dirt, grass, protectedIntact,
                        world.getBlockState(WATER_ORE).isOf(Blocks.STONE) && world.getBlockState(WATER_ORE.south()).isOf(Blocks.WATER),
                        world.getBlockState(FALLING_ORE).isOf(Blocks.STONE) && world.getBlockState(FALLING_ORE.up()).isOf(Blocks.SAND), player.getHealth(), player.getY(), sand, sandBlocks,
                        world.getEntitiesByClass(FallingBlockEntity.class, new Box(102, 154, 102, 135, 175, 141), entity -> true).size());
            } catch (RuntimeException e) { failure = e.toString(); } finally { pending = false; }
        });
    }
    private static boolean fresh(int age) { return observed != null && observed.tick >= entered && ticks - observed.tick <= age; }
    private static boolean sandOnly() { return System.getProperty("analyzer.nativeScenario", "").equals("sand-recovery"); }
    private static void beginSand() {
        nav.configureMotion(new AutomationNavigator.MotionProfile(true, 360, 1800, 35, 1, 3));
        nav.clearStatus(); require(nav.mineForItem(600, "minecraft:sand", "minecraft:sand"), "Production miner accepts a 600-sand inventory target with ordinary top-down breaks"); enter(Stage.SAND);
    }
    private static void enter(Stage next) { stage = next; entered = ticks + 1; lastActualCount = 0; lastActualProgress = ticks; }
    private static void require(boolean value, String message) { checks++; if (!value) throw new IllegalStateException(message); }
    private static void capture(MinecraftClient mc, String suffix) {
        mc.inGameHud.getChatHud().clear(false); mc.getToastManager().clear();
        ScreenshotRecorder.saveScreenshot(mc.runDirectory, "analyzer-native-automation-" + suffix + ".png", mc.getFramebuffer(), text -> AnalyzerClient.LOGGER.info("Native mining capture: {}", text.getString())); captures++;
    }
    private static void command(MinecraftClient mc, String text) { mc.getServer().getCommandManager().executeWithPrefix(mc.getServer().getCommandSource().withLevel(4).withSilent(), text); }
}
