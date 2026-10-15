package dev.analyzer;

import dev.analyzer.automation.AutomationEngine;
import dev.analyzer.automation.AutomationMotion;
import dev.analyzer.automation.AutomationNavigator;
import dev.analyzer.automation.InventoryAutomation;
import dev.analyzer.automation.ToolRecovery;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Developer-only real-world tool recovery, including two actual tool breakages in one recovery controller. */
final class NativeToolRecoverySmoke {
    private enum Stage { SETUP, FIRST_RECOVERY, FIRST_VERIFY, FIRST_BREAK_SETUP, FIRST_BREAK, FIRST_BROKEN_VERIFY,
        SECOND_RECOVERY, SECOND_VERIFY, SECOND_BREAK_SETUP, SECOND_BREAK, SECOND_BROKEN_VERIFY, THIRD_RECOVERY, THIRD_VERIFY, CAPTURE, FINISHED }
    private record Tool(Item item, int slot, int remaining) { }
    private record Snapshot(int requestedTick, boolean survival, List<Tool> tools, boolean table, int tableItems, boolean furnace,
                            int naturalLogs, int sourceStone, boolean markerStone, boolean markerAir, boolean cursorEmpty) { }
    private static final BlockPos MIN = new BlockPos(102, 160, 102), MAX = new BlockPos(126, 168, 126);
    private static final List<BlockPos> LOGS = logs();
    private static Stage stage = Stage.SETUP;
    private static boolean initialized;
    private static int currentTick, entered, checks, captures, lastSnapshot = -100, alignedAge = -1, breakActions;
    private static boolean breakStarted;
    private static long firstClicks, previousClicks;
    private static AutomationNavigator navigator;
    private static InventoryAutomation inventory;
    private static ToolRecovery recovery;
    private static volatile boolean setupDone, snapshotPending, breakReady;
    private static volatile String serverFailure = "";
    private static volatile Snapshot observed;
    private static volatile BlockPos marker, breakStand;

    private NativeToolRecoverySmoke() { }
    static boolean done() { return stage == Stage.FINISHED; }
    static int assertions() { return checks; }
    static int captures() { return captures; }

    static void tick(MinecraftClient mc, int tick) {
        if (done()) return;
        if (!FabricLoader.getInstance().isDevelopmentEnvironment() || !Boolean.getBoolean("analyzer.smoke")
                || !Boolean.getBoolean("analyzer.nativeAutomationSmoke")) throw new IllegalStateException("Tool-recovery fixture is development-only");
        if (mc.player == null || mc.world == null || mc.getServer() == null) return;
        currentTick = tick;
        if (!initialized) { initialized = true; entered = tick; setup(mc); }
        if (!serverFailure.isEmpty()) throw new IllegalStateException("Native recovery server fixture: " + serverFailure);
        if (age() > 3600) throw new IllegalStateException("Native tool recovery timed out at " + stage + "; navigation=" + navigator.status().message() + "; inventory=" + inventory.currentJob());
        if (setupDone && !snapshotPending && tick - lastSnapshot >= 10) requestSnapshot(mc);
        switch (stage) {
            case SETUP -> {
                if (!setupDone || age() < 30 || observed == null || !mc.world.isChunkLoaded(new BlockPos(108, 161, 111))
                        || mc.player.squaredDistanceTo(new Vec3d(108.5, 161, 111.5)) > .25) return;
                require(observed.survival && observed.tools.isEmpty() && !ToolRecovery.hasTier(2), "Recovery starts in real Survival without a usable mining tool");
                require(observed.naturalLogs == 16 && observed.sourceStone == 32 && !observed.table, "Only nearby world logs and stone are supplied; no tool, table or recipe material is handed to the player");
                require(InventoryAutomation.countResource("logs") == 0 && InventoryAutomation.count(Items.COBBLESTONE) == 0, "Player starts without credited wood or cobblestone");
                firstClicks = inventory.clicks(); enter(Stage.FIRST_RECOVERY);
            }
            case FIRST_RECOVERY -> runRecovery(mc, Stage.FIRST_VERIFY);
            case FIRST_VERIFY -> {
                if (!freshSnapshot(20)) return;
                verifyReplacement("Initial missing-tool recovery");
                require(observed.naturalLogs <= 8, "The production miner actually harvests at least eight nearby logs");
                require(observed.sourceStone <= 16, "The production miner actually gathers the stone prerequisite from the world");
                require(!observed.table && observed.tableItems == 1 && observed.furnace, "Recovery returns its actual crafting table to inventory and crafts a real furnace");
                require(inventory.clicks() > firstClicks + 20, "Wooden prerequisites and the stone pickaxe use normal recipe slot clicks");
                beginBreak(mc, Stage.FIRST_BREAK_SETUP);
            }
            case FIRST_BREAK_SETUP -> waitForBreak(mc, Stage.FIRST_BREAK);
            case FIRST_BREAK -> breakNormally(mc, Stage.FIRST_BROKEN_VERIFY);
            case FIRST_BROKEN_VERIFY -> {
                if (!freshSnapshot(12)) return;
                require(observed.markerAir && observed.tools.stream().noneMatch(tool -> tool.item == Items.STONE_PICKAXE), "The first near-worn stone pickaxe really breaks while mining an actual stone block");
                require(!ToolRecovery.hasTier(2), "The first break removes the usable stone tier instead of retaining a stale tool flag");
                previousClicks = inventory.clicks(); enter(Stage.SECOND_RECOVERY);
            }
            case SECOND_RECOVERY -> runRecovery(mc, Stage.SECOND_VERIFY);
            case SECOND_VERIFY -> {
                if (!freshSnapshot(20)) return;
                verifyReplacement("Recovery after the first actual break");
                require(inventory.clicks() > previousClicks, "The same recovery controller crafts a new replacement after its first completion");
                beginBreak(mc, Stage.SECOND_BREAK_SETUP);
            }
            case SECOND_BREAK_SETUP -> waitForBreak(mc, Stage.SECOND_BREAK);
            case SECOND_BREAK -> breakNormally(mc, Stage.SECOND_BROKEN_VERIFY);
            case SECOND_BROKEN_VERIFY -> {
                if (!freshSnapshot(12)) return;
                require(observed.markerAir && observed.tools.stream().noneMatch(tool -> tool.item == Items.STONE_PICKAXE), "The second replacement really breaks through another normal mining action");
                require(!ToolRecovery.hasTier(2), "The second actual break again invalidates the stone tier");
                previousClicks = inventory.clicks(); enter(Stage.THIRD_RECOVERY);
            }
            case THIRD_RECOVERY -> runRecovery(mc, Stage.THIRD_VERIFY);
            case THIRD_VERIFY -> {
                if (!freshSnapshot(20)) return;
                verifyReplacement("Recovery after a second actual break");
                require(inventory.clicks() > previousClicks, "A second break resets the completed prerequisite queue and performs new server-backed crafting");
                inventory.stop(); navigator.cancel(); mc.setScreen(new InventoryScreen(mc.player)); enter(Stage.CAPTURE);
            }
            case CAPTURE -> {
                if (age() < 6) return;
                mc.inGameHud.getChatHud().clear(false); mc.getToastManager().clear();
                ScreenshotRecorder.saveScreenshot(mc.runDirectory, "analyzer-native-automation-tool-recovery.png", mc.getFramebuffer(), text -> AnalyzerClient.LOGGER.info("Native recovery capture: {}", text.getString()));
                captures++; mc.setScreen(null); stage = Stage.FINISHED;
                AnalyzerClient.LOGGER.info("ANALYZER_NATIVE_TOOL_RECOVERY_COMPLETE: {} assertions, {} capture; world wood/stone harvesting, real wooden and stone tools, real table, two actual pickaxe breakages and two successful repeated recoveries", checks, captures);
            }
            case FINISHED -> { }
        }
    }
    private static void runRecovery(MinecraftClient mc, Stage next) {
        navigator.tick(); InventoryAutomation.Result result = recovery.tick(2);
        if (result.blocked()) throw new IllegalStateException("Native tool recovery blocked at " + stage + " / " + currentTick + ": " + result.detail());
        if (result.done()) { inventory.stop(); navigator.cancel(); enter(next); requestSnapshot(mc); }
    }
    private static void verifyReplacement(String description) {
        require(observed.survival && observed.tools.stream().anyMatch(tool -> tool.item == Items.STONE_PICKAXE && tool.remaining >= 16), description + " produces an actual usable server stone pickaxe");
        require(ToolRecovery.hasTier(2), description + " agrees with the live client inventory tier");
        require(observed.cursorEmpty, description + " returns the server cursor item safely");
    }
    private static void beginBreak(MinecraftClient mc, Stage next) {
        inventory.stop(); navigator.cancel(); alignedAge = -1; breakReady = false; marker = breakStand = null;
        breakStarted = false; breakActions = 0;
        UUID playerId = mc.player.getUuid();
        mc.getServer().execute(() -> {
            try {
                ServerPlayerEntity player = mc.getServer().getPlayerManager().getPlayer(playerId);
                if (player == null) throw new IllegalStateException("The isolated player disconnected before the wear-out precondition");
                var world = player.getServerWorld();
                int slot = -1;
                for (int i = 0; i < 36; i++) if (player.getInventory().getStack(i).isOf(Items.STONE_PICKAXE)) { slot = i; break; }
                if (slot < 0) throw new IllegalStateException("The crafted server replacement is missing before the wear-out fixture");
                BlockPos anchor = player.getBlockPos();
                for (int dz = -3; dz <= 3 && breakStand == null; dz++) for (int dx = -3; dx <= 3; dx++) {
                    BlockPos stand = anchor.add(dx, 0, dz), target = stand.east(2);
                    if (stand.getX() < 103 || stand.getX() > 123 || stand.getZ() < 103 || stand.getZ() > 125 || target.getX() > 125) continue;
                    if (!world.getBlockState(stand).isAir() || !world.getBlockState(stand.up()).isAir()
                            || !world.getBlockState(stand.east()).isAir() || !world.getBlockState(stand.east().up()).isAir() || !world.getBlockState(target).isAir()
                            || !world.getBlockState(stand.down()).isOf(Blocks.BEDROCK) || !world.getBlockState(target.down()).isOf(Blocks.BEDROCK)) continue;
                    breakStand = stand; marker = target; break;
                }
                if (breakStand == null) throw new IllegalStateException("No empty isolated wear-out surface exists near the player carrying the recovered table");
                String itemSlot = slot < 9 ? "hotbar." + slot : "inventory." + (slot - 9);
                // Only the isolated wear-out precondition uses a command; the item must break through a normal action.
                command(mc, "item replace entity AnalyzerDev " + itemSlot + " with minecraft:stone_pickaxe[minecraft:damage=130]");
                command(mc, "setblock " + coordinates(marker) + " minecraft:stone");
                command(mc, "tp AnalyzerDev " + (breakStand.getX() + .5) + " " + breakStand.getY() + " " + (breakStand.getZ() + .5) + " -90 20");
                breakReady = true;
            } catch (RuntimeException error) { serverFailure = error.toString(); }
        });
        enter(next);
    }
    private static void waitForBreak(MinecraftClient mc, Stage next) {
        if (!breakReady || !freshSnapshot(20) || marker == null || breakStand == null || !mc.world.getBlockState(marker).isOf(Blocks.STONE)
                || mc.player.squaredDistanceTo(new Vec3d(breakStand.getX() + .5, breakStand.getY(), breakStand.getZ() + .5)) > .25
                || mc.player.currentScreenHandler != mc.player.playerScreenHandler) return;
        boolean acknowledged = false;
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isOf(Items.STONE_PICKAXE) && stack.getMaxDamage() - stack.getDamage() == 1) acknowledged = true;
        }
        if (!acknowledged) return;
        require(observed.markerStone && observed.tools.stream().anyMatch(tool -> tool.item == Items.STONE_PICKAXE && tool.remaining == 1), "The wear-out precondition arrives in the authoritative server inventory with exactly one durability");
        require(!ToolRecovery.hasTier(2), "Near-broken tools are already rejected by the production usable-tier check");
        enter(next);
    }
    private static void breakNormally(MinecraftClient mc, Stage next) {
        if (mc.world.getBlockState(marker).isAir()) { mc.interactionManager.cancelBlockBreaking(); enter(next); requestSnapshot(mc); return; }
        if (!AutomationMotion.align(Vec3d.ofCenter(marker))) { alignedAge = -1; return; }
        if (alignedAge < 0) { alignedAge = mc.player.age; return; }
        if (mc.player.age == alignedAge) return;
        int hotbar = -1;
        for (int i = 0; i < 9; i++) { ItemStack stack = mc.player.getInventory().getStack(i); if (stack.isOf(Items.STONE_PICKAXE) && stack.getMaxDamage() - stack.getDamage() == 1) { hotbar = i; break; } }
        if (hotbar < 0) {
            InventoryAutomation.Result ready = inventory.prepareItem(Items.STONE_PICKAXE, 1);
            if (ready.blocked()) throw new IllegalStateException("Could not move the worn fixture tool into the hotbar: " + ready.detail());
            return;
        }
        mc.player.getInventory().selectedSlot = hotbar;
        HitResult trace = mc.player.raycast(4.5, 1, false);
        if (!(trace instanceof BlockHitResult hit) || !hit.getBlockPos().equals(marker)) throw new IllegalStateException("The actual crosshair cannot reach the wear-out stone marker");
        // Match the native miner's action lifecycle. Vanilla's idle mouse release can clear
        // isBreakingBlock between END_CLIENT_TICK calls; restarting attackBlock on that flag
        // resets progress every frame. Start once, then let the normal manager update it.
        if (!breakStarted) {
            if (!mc.interactionManager.attackBlock(marker, hit.getSide())) throw new IllegalStateException("The wear-out fixture could not start its normal stone break");
            breakStarted = true;
        } else mc.interactionManager.updateBlockBreakingProgress(marker, hit.getSide());
        mc.player.swingHand(Hand.MAIN_HAND);
        if (++breakActions == 1 || breakActions % 20 == 0) AnalyzerClient.LOGGER.info("Native recovery wear-out: stage={} actions={} target={} clientState={} mainHand={} serverTools={}",
                stage, breakActions, marker, mc.world.getBlockState(marker), mc.player.getMainHandStack(), observed == null ? "pending" : observed.tools);
        if (breakActions > 200) throw new IllegalStateException("The wear-out marker did not break after 200 normal progress updates; selected=" + hotbar + "; hand=" + mc.player.getMainHandStack());
    }
    private static void setup(MinecraftClient mc) {
        AutomationEngine.INSTANCE.stop(); mc.setScreen(null); if (mc.player.currentScreenHandler != mc.player.playerScreenHandler) mc.player.closeHandledScreen();
        AnalyzerClient.CORE.setEnabled("expedition", false);
        for (var module : AnalyzerClient.CORE.modules()) {
            if (module.category() == dev.analyzer.core.Category.COMBAT || module.category() == dev.analyzer.core.Category.HVH
                    || module.category() == dev.analyzer.core.Category.MOVEMENT || module.category() == dev.analyzer.core.Category.PLAYER) AnalyzerClient.CORE.setEnabled(module.id(), false);
        }
        navigator = new AutomationNavigator(); inventory = new InventoryAutomation(navigator); inventory.actionDelayTicks(3);
        recovery = new ToolRecovery(navigator, inventory); UUID playerId = mc.player.getUuid();
        mc.getServer().execute(() -> {
            try {
                ServerPlayerEntity player = mc.getServer().getPlayerManager().getPlayer(playerId);
                if (player == null || !player.getName().getString().equals("AnalyzerDev")) throw new IllegalStateException("Expected the isolated AnalyzerDev player");
                for (int x = 6; x <= 7; x++) for (int z = 6; z <= 7; z++) mc.getServer().getOverworld().getChunk(x, z);
                for (String command : List.of("gamemode survival AnalyzerDev", "clear AnalyzerDev", "effect clear AnalyzerDev", "difficulty peaceful", "gamerule doMobSpawning false",
                        "fill 102 160 102 126 160 126 minecraft:bedrock", "fill 102 161 102 126 168 126 minecraft:air",
                        "fill 118 161 105 119 161 120 minecraft:stone", "kill @e[type=minecraft:item,x=102,y=160,z=102,dx=24,dy=10,dz=24]",
                        "tp AnalyzerDev 108.5 161 111.5 -90 15", "effect give AnalyzerDev minecraft:instant_health 1 5 true", "effect give AnalyzerDev minecraft:saturation 300 5 true")) command(mc, command);
                for (BlockPos pos : LOGS) command(mc, "setblock " + coordinates(pos) + " minecraft:oak_log");
                setupDone = true;
            } catch (RuntimeException error) { serverFailure = error.toString(); }
        });
    }
    private static void requestSnapshot(MinecraftClient mc) {
        if (snapshotPending || mc.getServer() == null || mc.player == null) return;
        snapshotPending = true; lastSnapshot = currentTick; int requested = currentTick; UUID playerId = mc.player.getUuid();
        mc.getServer().execute(() -> {
            try {
                ServerPlayerEntity player = mc.getServer().getPlayerManager().getPlayer(playerId); var world = player.getServerWorld();
                List<Tool> tools = new ArrayList<>(); boolean furnace = false; int tableItems = 0;
                for (int i = 0; i < player.getInventory().size(); i++) {
                    ItemStack stack = player.getInventory().getStack(i); if (stack.isOf(Items.FURNACE)) furnace = true;
                    if (stack.isOf(Items.CRAFTING_TABLE)) tableItems += stack.getCount();
                    if (stack.isOf(Items.STONE_PICKAXE) || stack.isOf(Items.WOODEN_PICKAXE) || stack.isOf(Items.IRON_PICKAXE) || stack.isOf(Items.DIAMOND_PICKAXE) || stack.isOf(Items.NETHERITE_PICKAXE)) tools.add(new Tool(stack.getItem(), i, stack.getMaxDamage() - stack.getDamage()));
                }
                boolean table = false; for (BlockPos pos : BlockPos.iterate(MIN.up(), MAX)) { table |= world.getBlockState(pos).isOf(Blocks.CRAFTING_TABLE); furnace |= world.getBlockState(pos).isOf(Blocks.FURNACE); }
                int natural = 0; for (BlockPos pos : LOGS) if (world.getBlockState(pos).isOf(Blocks.OAK_LOG)) natural++;
                int stone = 0; for (BlockPos pos : BlockPos.iterate(new BlockPos(118, 161, 105), new BlockPos(119, 161, 120))) if (world.getBlockState(pos).isOf(Blocks.STONE)) stone++;
                BlockPos target = marker;
                observed = new Snapshot(requested, player.interactionManager.getGameMode() == GameMode.SURVIVAL, List.copyOf(tools), table, tableItems, furnace, natural, stone,
                        target != null && world.getBlockState(target).isOf(Blocks.STONE), target != null && world.getBlockState(target).isAir(), player.currentScreenHandler.getCursorStack().isEmpty());
            } catch (RuntimeException error) { serverFailure = error.toString(); }
            finally { snapshotPending = false; }
        });
    }
    private static List<BlockPos> logs() { List<BlockPos> positions = new ArrayList<>(); for (int x : new int[]{112, 114}) for (int z : new int[]{109, 112}) for (int y = 161; y <= 164; y++) positions.add(new BlockPos(x, y, z)); return List.copyOf(positions); }
    private static String coordinates(BlockPos pos) { return pos.getX() + " " + pos.getY() + " " + pos.getZ(); }
    private static void command(MinecraftClient mc, String text) { mc.getServer().getCommandManager().executeWithPrefix(mc.getServer().getCommandSource().withSilent(), text); }
    private static int age() { return currentTick - entered; }
    private static boolean freshSnapshot(int delay) { return age() >= delay && observed != null && observed.requestedTick >= entered; }
    private static void enter(Stage next) { stage = next; entered = currentTick + 1; AnalyzerClient.LOGGER.info("Native tool-recovery acceptance: {}", next); }
    private static void require(boolean condition, String detail) { if (!condition) throw new IllegalStateException("Native tool recovery failed at " + stage + ": " + detail); checks++; AnalyzerClient.LOGGER.info("Native tool-recovery check passed: {}", detail); }
}
