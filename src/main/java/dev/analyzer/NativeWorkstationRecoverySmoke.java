package dev.analyzer;

import dev.analyzer.automation.AutomationEngine;
import dev.analyzer.automation.AutomationNavigator;
import dev.analyzer.automation.InventoryAutomation;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.util.UUID;

/** Real Survival close/raised/grass placement, crafting, exact owned pickup and pre-existing-table preservation. */
final class NativeWorkstationRecoverySmoke {
    private enum Stage { OWNED_SETUP, OWNED_CRAFT, OWNED_HOLD, OWNED_VERIFY, OWNED_CAPTURE, EXISTING_SETUP, EXISTING_CRAFT, EXISTING_VERIFY, EXISTING_CAPTURE, REPLACED_SETUP, REPLACED_CRAFT, REPLACED_HOLD, REPLACED_VERIFY, FINISHED }
    private record Snapshot(int tick, boolean survival, int tables, int grass, int tableItems, int woodenPicks, boolean cursorEmpty) { }
    private static Stage stage = Stage.OWNED_SETUP;
    private static boolean initialized, stopVerified;
    private static int currentTick, entered, checks, captures, lastSnapshot = -100;
    private static volatile boolean setupDone, snapshotPending;
    private static volatile String serverFailure = "";
    private static volatile Snapshot observed;
    private static AutomationNavigator navigator;
    private static InventoryAutomation inventory;
    private static String lastDetail = "";
    private static BlockPos origin;
    private static BlockPos replacement;
    private NativeWorkstationRecoverySmoke() { }
    static boolean done() { return stage == Stage.FINISHED; }
    static int assertions() { return checks; }
    static int captures() { return captures; }
    static void tick(MinecraftClient mc, int tick) {
        if (done()) return;
        if (!FabricLoader.getInstance().isDevelopmentEnvironment() || !Boolean.getBoolean("analyzer.smoke") || !Boolean.getBoolean("analyzer.nativeAutomationSmoke")) throw new IllegalStateException("Workstation fixture is development-only");
        if (mc.player == null || mc.world == null || mc.getServer() == null) return;
        currentTick = tick;
        if (!initialized) { initialized = true; entered = tick; origin = new BlockPos(210, 161, 210); setup(mc, true); }
        if (!serverFailure.isEmpty()) throw new IllegalStateException("Workstation fixture server failure: " + serverFailure);
        if (age() > 2400) throw new IllegalStateException("Workstation fixture timed out at " + stage + ": " + lastDetail + "; route=" + navigator.status().message() + "; player=" + mc.player.getPos());
        if (setupDone && !snapshotPending && tick - lastSnapshot >= 8) snapshot(mc);
        switch (stage) {
            case OWNED_SETUP -> {
                if (!ready(mc)) return;
                require(observed.survival && observed.tables == 0 && observed.tableItems == 0 && observed.woodenPicks == 0, "Raised-surface test starts in real Survival without a table or pickaxe");
                require(observed.grass == 8, "Every adjacent raised placement cell starts with real short grass");
                require(InventoryAutomation.count(Items.OAK_PLANKS) == 8 && InventoryAutomation.count(Items.STICK) == 4, "Only actual ordinary recipe materials are provided");
                enter(Stage.OWNED_CRAFT);
            }
            case OWNED_CRAFT -> craft(Stage.OWNED_VERIFY);
            case OWNED_HOLD -> {
                if (age() < 20 || !fresh()) return;
                require(observed.tables == 1 && observed.woodenPicks == 1, "Explicit Stop cancels recovery actions without breaking or abandoning ownership of the acknowledged table");
                require(inventory.hasOwnedWorkstations(), "Stopped workstation ownership remains available for explicit resumed work");
                stopVerified = true; enter(Stage.OWNED_CRAFT);
            }
            case OWNED_VERIFY -> {
                if (!fresh()) return;
                require(observed.woodenPicks == 1, "Ordinary crafting produces the actual server wooden pickaxe");
                require(observed.tables == 0 && observed.tableItems == 1, "The worker-placed table is actually broken and its drop returns to server inventory");
                require(observed.grass < 8, "Ordinary block breaking clears a harmless grass obstruction on a raised near placement cell");
                require(observed.cursorEmpty && !inventory.hasOwnedWorkstations(), "Crafting cursor and acknowledged workstation ownership finish cleanly");
                require(stopVerified, "The recovery lifecycle is tested across an actual stopped interval");
                inventory.stop(); navigator.cancel(); mc.setScreen(new InventoryScreen(mc.player)); enter(Stage.OWNED_CAPTURE);
            }
            case OWNED_CAPTURE -> {
                if (age() < 6) return; capture(mc, "owned-workstation-recovered"); mc.setScreen(null);
                origin = new BlockPos(230, 161, 210); setup(mc, false); enter(Stage.EXISTING_SETUP);
            }
            case EXISTING_SETUP -> {
                if (!ready(mc)) return;
                require(observed.tables == 1 && observed.tableItems == 0 && observed.woodenPicks == 0, "A separate pre-existing table starts in the world and is not an owned item");
                enter(Stage.EXISTING_CRAFT);
            }
            case EXISTING_CRAFT -> craft(Stage.EXISTING_VERIFY);
            case EXISTING_VERIFY -> {
                if (!fresh()) return;
                require(observed.woodenPicks == 1, "The worker actually reuses a nearby pre-existing table for normal crafting");
                require(observed.tables == 1 && observed.tableItems == 0, "The pre-existing table stays in the world and is never claimed or mined for cleanup");
                require(!inventory.hasOwnedWorkstations() && observed.cursorEmpty, "Using another table does not create ownership or leave cursor items");
                inventory.stop(); navigator.cancel(); mc.setScreen(new InventoryScreen(mc.player)); enter(Stage.EXISTING_CAPTURE);
            }
            case EXISTING_CAPTURE -> {
                if (age() < 6) return; capture(mc, "existing-workstation-preserved"); mc.setScreen(null);
                origin = new BlockPos(250, 161, 210); setup(mc, true); enter(Stage.REPLACED_SETUP);
            }
            case REPLACED_SETUP -> {
                if (!ready(mc)) return; enter(Stage.REPLACED_CRAFT);
            }
            case REPLACED_CRAFT -> craft(Stage.REPLACED_VERIFY);
            case REPLACED_HOLD -> {
                if (age() == 0) {
                    var pos = replacement; var id = mc.player.getUuid();
                    mc.getServer().execute(() -> {
                        var player = mc.getServer().getPlayerManager().getPlayer(id); var world = player.getServerWorld();
                        world.setBlockState(pos, Blocks.AIR.getDefaultState());
                        player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket(pos, Blocks.AIR.getDefaultState()));
                        world.setBlockState(pos, Blocks.CRAFTING_TABLE.getDefaultState());
                        player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket(pos, Blocks.CRAFTING_TABLE.getDefaultState()));
                    });
                }
                if (age() < 20 || !fresh()) return;
                require(observed.tables == 1 && !inventory.hasOwnedWorkstations(), "Observed server removal and replacement while stopped invalidates old table ownership");
                var result = inventory.tick("bootstrap", 30);
                require(result.done(), "Explicit resumed work credits the actual pick without claiming the manual replacement");
                enter(Stage.REPLACED_VERIFY);
            }
            case REPLACED_VERIFY -> {
                if (age() < 20 || !fresh()) return;
                require(observed.tables == 1 && observed.tableItems == 0 && observed.woodenPicks == 1, "The replacement remains a world table and is not harvested as the previous owned block");
                inventory.stop(); navigator.cancel(); capture(mc, "replacement-workstation-preserved"); stage = Stage.FINISHED;
                require(!mc.options.forwardKey.isPressed() && !mc.options.jumpKey.isPressed() && !mc.options.attackKey.isPressed() && !mc.options.useKey.isPressed(), "Workstation completion releases movement and action controls");
                AnalyzerClient.LOGGER.info("ANALYZER_NATIVE_WORKSTATIONS_COMPLETE: {} assertions, {} captures; raised near-surface grass clearing, actual recipe, acknowledged owned table recovery and existing table preserved", checks, captures);
            }
            case FINISHED -> { }
        }
    }
    private static void craft(Stage next) {
        navigator.tick();
        InventoryAutomation.Result result = inventory.tickTransfer(); if (result == null) result = inventory.tick("bootstrap", 30);
        lastDetail = result.detail();
        if (result.blocked()) throw new IllegalStateException("Workstation fixture blocked at " + stage + ": " + lastDetail);
        if (stage == Stage.REPLACED_CRAFT && inventory.workstationRecoveryActive() && InventoryAutomation.count(Items.WOODEN_PICKAXE) > 0) {
            replacement = null; var mc = MinecraftClient.getInstance();
            for (BlockPos pos : BlockPos.iterate(origin.add(-4, -1, -4), origin.add(4, 3, 4))) if (mc.world.getBlockState(pos).isOf(Blocks.CRAFTING_TABLE)) {replacement = pos.toImmutable(); break;}
            require(replacement != null && inventory.hasOwnedWorkstations(), "The replacement regression starts with an actual acknowledged worker-placed table");
            inventory.stop(); navigator.cancel(); enter(Stage.REPLACED_HOLD); observed = null; return;
        }
        if (stage == Stage.OWNED_CRAFT && !stopVerified && inventory.workstationRecoveryActive() && InventoryAutomation.count(Items.WOODEN_PICKAXE) > 0) {
            inventory.stop(); navigator.cancel(); enter(Stage.OWNED_HOLD); observed = null; return;
        }
        if (result.done()) {enter(next); observed = null;}
    }
    private static void setup(MinecraftClient mc, boolean owned) {
        setupDone = false; observed = null; snapshotPending = false; lastSnapshot = -100;
        AutomationEngine.INSTANCE.stop(); mc.setScreen(null);
        if (mc.player.currentScreenHandler != mc.player.playerScreenHandler) mc.player.closeHandledScreen();
        for (var module : AnalyzerClient.CORE.modules()) if (module.category() == dev.analyzer.core.Category.COMBAT || module.category() == dev.analyzer.core.Category.HVH || module.category() == dev.analyzer.core.Category.MOVEMENT || module.category() == dev.analyzer.core.Category.PLAYER) AnalyzerClient.CORE.setEnabled(module.id(), false);
        AnalyzerClient.CORE.setEnabled("expedition", false);
        navigator = new AutomationNavigator(); inventory = new InventoryAutomation(navigator); inventory.actionDelayTicks(2);
        UUID playerId = mc.player.getUuid(); BlockPos location = origin;
        mc.getServer().execute(() -> {
            try {
                ServerPlayerEntity player = mc.getServer().getPlayerManager().getPlayer(playerId);
                if (player == null) throw new IllegalStateException("Isolated workstation player disconnected");
                var world = player.getServerWorld();
                for (int x = location.getX() - 10; x <= location.getX() + 10; x += 5) for (int z = location.getZ() - 10; z <= location.getZ() + 10; z += 5) world.getChunk(new BlockPos(x, location.getY(), z));
                for (BlockPos pos : BlockPos.iterate(location.add(-9, -1, -9), location.add(9, 5, 9))) world.setBlockState(pos, Blocks.AIR.getDefaultState());
                if (owned) {
                    world.setBlockState(location.down(), Blocks.BEDROCK.getDefaultState());
                    for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) if (x != 0 || z != 0) {
                        world.setBlockState(location.add(x, 0, z), Blocks.DIRT.getDefaultState());
                        world.setBlockState(location.add(x, 1, z), Blocks.SHORT_GRASS.getDefaultState());
                    }
                } else {
                    for (BlockPos pos : BlockPos.iterate(location.add(-4, -1, -4), location.add(4, -1, 4))) world.setBlockState(pos, Blocks.BEDROCK.getDefaultState());
                    world.setBlockState(location.east(), Blocks.CRAFTING_TABLE.getDefaultState());
                }
                player.changeGameMode(GameMode.SURVIVAL); player.getInventory().clear();
                player.getInventory().setStack(0, new net.minecraft.item.ItemStack(Items.OAK_PLANKS, owned ? 8 : 3));
                player.getInventory().setStack(1, new net.minecraft.item.ItemStack(Items.STICK, 4));
                player.getInventory().setStack(2, new net.minecraft.item.ItemStack(Items.IRON_AXE));
                player.getHungerManager().setFoodLevel(20); player.setHealth(20);
                player.requestTeleport(location.getX() + .5, location.getY(), location.getZ() + .5);
                player.currentScreenHandler.sendContentUpdates(); setupDone = true;
            } catch (RuntimeException error) {serverFailure = error.toString();}
        });
    }
    private static boolean ready(MinecraftClient mc) {return setupDone && age() >= 24 && observed != null && mc.player.squaredDistanceTo(Vec3d.ofBottomCenter(origin)) < .1 && mc.world.getBlockState(origin.down()).isOf(Blocks.BEDROCK) && mc.interactionManager.getCurrentGameMode() == GameMode.SURVIVAL;}
    private static boolean fresh() {return observed != null && observed.tick > entered + 6;}
    private static void snapshot(MinecraftClient mc) {
        snapshotPending = true; lastSnapshot = currentTick; int requested = currentTick; UUID id = mc.player.getUuid(); BlockPos location = origin;
        mc.getServer().execute(() -> {
            try {
                ServerPlayerEntity player = mc.getServer().getPlayerManager().getPlayer(id); if (player == null) throw new IllegalStateException("Snapshot player disconnected");
                int tables = 0, grass = 0, tableItems = 0, picks = 0;
                for (BlockPos pos : BlockPos.iterate(location.add(-8, -1, -8), location.add(8, 4, 8))) {var state = player.getServerWorld().getBlockState(pos); if (state.isOf(Blocks.CRAFTING_TABLE)) tables++; if (state.isOf(Blocks.SHORT_GRASS)) grass++;}
                for (int i = 0; i < player.getInventory().size(); i++) {var stack = player.getInventory().getStack(i); if (stack.isOf(Items.CRAFTING_TABLE)) tableItems += stack.getCount(); if (stack.isOf(Items.WOODEN_PICKAXE)) picks += stack.getCount();}
                observed = new Snapshot(requested, player.interactionManager.getGameMode() == GameMode.SURVIVAL, tables, grass, tableItems, picks, player.currentScreenHandler.getCursorStack().isEmpty());
            } catch (RuntimeException error) {serverFailure = error.toString();} finally {snapshotPending = false;}
        });
    }
    private static void capture(MinecraftClient mc, String name) {mc.inGameHud.getChatHud().clear(false); mc.getToastManager().clear(); ScreenshotRecorder.saveScreenshot(mc.runDirectory, "analyzer-native-workstations-" + name + ".png", mc.getFramebuffer(), text -> AnalyzerClient.LOGGER.info("Workstation capture: {}", text.getString())); captures++;}
    private static int age() {return currentTick - entered;}
    private static void enter(Stage value) {stage = value; entered = currentTick + 1;}
    private static void require(boolean value, String detail) {if (!value) throw new IllegalStateException("Native workstation assertion: " + detail); checks++;}
}
