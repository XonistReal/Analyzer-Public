package dev.analyzer;

import dev.analyzer.automation.AutomationEngine;
import dev.analyzer.automation.AutomationNavigator;
import dev.analyzer.automation.InventoryAutomation;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.GameMode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Isolated developer-only Survival acceptance for real template crafting, smithing and armor equip. */
final class NativeProgressionSmoke {
    private enum Stage { SETUP, SMITH, VERIFY_SMITH, EQUIP, VERIFY_EQUIP, CAPTURE, FINISHED }
    private record Armor(Item item, int protection, int unbreaking) { }
    private record ServerSnapshot(int requestedTick, boolean survival, Map<Item, Integer> items, List<Armor> upgrades,
                                  List<Armor> equipped, boolean cursorEmpty, int itemDrops, boolean craftingTable, boolean smithingTable) { }
    private static final Item[] DIAMOND = {Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS};
    private static final Item[] NETHERITE = {Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS};
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final BlockPos CENTER = new BlockPos(88, 161, 88);
    private static Stage stage = Stage.SETUP;
    private static boolean initialized;
    private static int currentTick, entered, checks, captures, lastSnapshot = -100;
    private static long firstClicks;
    private static AutomationNavigator navigator;
    private static InventoryAutomation worker;
    private static volatile boolean setupDone, snapshotPending;
    private static volatile String serverFailure = "";
    private static volatile ServerSnapshot observed;

    private NativeProgressionSmoke() { }
    static boolean done() { return stage == Stage.FINISHED; }
    static int assertions() { return checks; }
    static int captures() { return captures; }

    static void tick(MinecraftClient mc, int tick) {
        if (done()) return;
        if (!FabricLoader.getInstance().isDevelopmentEnvironment() || !Boolean.getBoolean("analyzer.smoke")
                || !Boolean.getBoolean("analyzer.nativeAutomationSmoke")) throw new IllegalStateException("Progression fixture is development-only");
        if (mc.player == null || mc.world == null || mc.getServer() == null) return;
        currentTick = tick;
        if (!initialized) { initialized = true; entered = tick; setup(mc); }
        if (!serverFailure.isEmpty()) throw new IllegalStateException("Native progression server fixture: " + serverFailure);
        if (currentTick - entered > 2400) throw new IllegalStateException("Native progression timed out at " + stage + ": " + (worker == null ? "setup" : worker.currentJob()));
        if (setupDone && !snapshotPending && tick - lastSnapshot >= 10) requestSnapshot(mc);
        switch (stage) {
            case SETUP -> {
                if (!setupDone || age() < 30 || observed == null || !mc.world.isChunkLoaded(CENTER) || mc.player.squaredDistanceTo(CENTER.toCenterPos()) > 4) return;
                ServerSnapshot snapshot = observed;
                require(snapshot.survival, "Smithing fixture is running in authoritative Survival mode");
                require(count(snapshot, Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE) == 1, "Exactly one initial Netherite Upgrade template is supplied");
                require(count(snapshot, Items.DIAMOND) == 21 && count(snapshot, Items.NETHERRACK) == 3, "Three template duplications have their real 21 diamonds and three netherrack");
                require(count(snapshot, Items.NETHERITE_INGOT) == 4, "Exactly four real netherite upgrade ingots are supplied");
                require(count(snapshot, Items.IRON_INGOT) == 2 && count(snapshot, Items.OAK_PLANKS) == 8, "Unused iron and planks are present as inventory preservation markers");
                require(count(snapshot, Items.CRAFTING_TABLE) == 1 && count(snapshot, Items.SMITHING_TABLE) == 1, "Normal workstation items are supplied for placement");
                for (Item item : DIAMOND) require(count(snapshot, item) == 1, "The initial diamond armor piece exists on the server: " + item.getName().getString());
                require(snapshot.itemDrops == 0 && snapshot.cursorEmpty, "Fixture starts without dropped or cursor-held items");
                firstClicks = worker.clicks(); enter(Stage.SMITH);
            }
            case SMITH -> {
                navigator.tick();
                InventoryAutomation.Result result = run("netherite-armor");
                requireNotBlocked(result);
                if (result.done()) { require(worker.clicks() > firstClicks + 40, "Template duplication and smithing use normal inventory slot clicks"); enter(Stage.VERIFY_SMITH); requestSnapshot(mc); }
            }
            case VERIFY_SMITH -> {
                if (age() < 20 || observed == null || observed.requestedTick < entered) return;
                ServerSnapshot snapshot = observed;
                verifySmithing(snapshot);
                require(mc.player.currentScreenHandler.getCursorStack().isEmpty() && snapshot.cursorEmpty, "Smithing returns both client and server inventory cursors empty");
                require(snapshot.craftingTable && snapshot.smithingTable, "Both workstations were actually placed in the server world");
                require(count(snapshot, Items.CRAFTING_TABLE) == 0 && count(snapshot, Items.SMITHING_TABLE) == 0, "Workstation placement consumes the supplied item rather than creating a fake block");
                require(snapshot.itemDrops == 0, "Template crafting and smithing drop no ingredients or armor");
                enter(Stage.EQUIP);
            }
            case EQUIP -> {
                InventoryAutomation.Result result = run("netherite-equip"); requireNotBlocked(result);
                if (result.done()) { enter(Stage.VERIFY_EQUIP); requestSnapshot(mc); }
            }
            case VERIFY_EQUIP -> {
                if (age() < 20 || observed == null || observed.requestedTick < entered) return;
                ServerSnapshot snapshot = observed; verifySmithing(snapshot);
                for (int i = 0; i < NETHERITE.length; i++) {
                    Armor armor = snapshot.equipped.get(i);
                    require(armor.item == NETHERITE[i], "Normal equip clicks put actual netherite armor in server slot " + SLOTS[i]);
                    require(armor.protection == 4 && armor.unbreaking == 3, "Equipped server armor retains Protection IV and Unbreaking III in " + SLOTS[i]);
                }
                require(snapshot.cursorEmpty && mc.player.currentScreenHandler.getCursorStack().isEmpty(), "Equip leaves no held cursor item");
                worker.stop(); navigator.cancel(); mc.setScreen(new InventoryScreen(mc.player)); enter(Stage.CAPTURE);
            }
            case CAPTURE -> {
                if (age() < 6) return;
                mc.inGameHud.getChatHud().clear(false); mc.getToastManager().clear();
                ScreenshotRecorder.saveScreenshot(mc.runDirectory, "analyzer-native-automation-smithing.png", mc.getFramebuffer(), text -> AnalyzerClient.LOGGER.info("Native progression capture: {}", text.getString()));
                captures++; mc.setScreen(null); stage = Stage.FINISHED;
                AnalyzerClient.LOGGER.info("ANALYZER_NATIVE_PROGRESSION_COMPLETE: {} assertions, {} capture; three normal template duplications, four real smithing upgrades, preserved Protection IV/Unbreaking III, and equipped server armor", checks, captures);
            }
            case FINISHED -> { }
        }
    }
    private static void setup(MinecraftClient mc) {
        AutomationEngine.INSTANCE.stop(); mc.setScreen(null);
        if (mc.player.currentScreenHandler != mc.player.playerScreenHandler) mc.player.closeHandledScreen();
        AnalyzerClient.CORE.setEnabled("expedition", false);
        for (var module : AnalyzerClient.CORE.modules()) {
            if (module.category() == dev.analyzer.core.Category.COMBAT || module.category() == dev.analyzer.core.Category.HVH
                    || module.category() == dev.analyzer.core.Category.MOVEMENT || module.category() == dev.analyzer.core.Category.PLAYER) AnalyzerClient.CORE.setEnabled(module.id(), false);
        }
        navigator = new AutomationNavigator(); worker = new InventoryAutomation(navigator); worker.actionDelayTicks(3);
        UUID playerId = mc.player.getUuid();
        mc.getServer().execute(() -> {
            try {
                ServerPlayerEntity player = mc.getServer().getPlayerManager().getPlayer(playerId);
                if (player == null || !player.getName().getString().equals("AnalyzerDev")) throw new IllegalStateException("Expected the isolated AnalyzerDev player");
                mc.getServer().getOverworld().getChunk(5, 5);
                for (String command : List.of("gamemode survival AnalyzerDev", "clear AnalyzerDev", "effect clear AnalyzerDev",
                        "gamerule doMobSpawning false", "difficulty peaceful", "fill 82 160 82 95 160 95 minecraft:bedrock",
                        "fill 82 161 82 95 168 95 minecraft:air", "tp AnalyzerDev 88.5 161 88.5 0 15",
                        "effect give AnalyzerDev minecraft:instant_health 1 5 true", "effect give AnalyzerDev minecraft:saturation 180 5 true",
                        "kill @e[type=minecraft:item,x=82,y=160,z=82,dx=13,dy=10,dz=13]", "give AnalyzerDev minecraft:diamond_pickaxe")) command(mc, command);
                for (String armor : List.of("diamond_helmet", "diamond_chestplate", "diamond_leggings", "diamond_boots"))
                    command(mc, "give AnalyzerDev minecraft:" + armor + "[minecraft:enchantments={levels:{\"minecraft:protection\":4,\"minecraft:unbreaking\":3}}]");
                for (String command : List.of("give AnalyzerDev minecraft:netherite_upgrade_smithing_template", "give AnalyzerDev minecraft:diamond 21",
                        "give AnalyzerDev minecraft:netherrack 3", "give AnalyzerDev minecraft:netherite_ingot 4", "give AnalyzerDev minecraft:iron_ingot 2",
                        "give AnalyzerDev minecraft:oak_planks 8", "give AnalyzerDev minecraft:crafting_table", "give AnalyzerDev minecraft:smithing_table")) command(mc, command);
                setupDone = true;
            } catch (RuntimeException error) { serverFailure = error.toString(); }
        });
    }
    private static void command(MinecraftClient mc, String text) {
        mc.getServer().getCommandManager().executeWithPrefix(mc.getServer().getCommandSource().withSilent(), text);
    }
    private static InventoryAutomation.Result run(String job) {
        InventoryAutomation.Result transfer = worker.tickTransfer();
        return transfer == null ? worker.tick(job, 30) : transfer;
    }
    private static void requireNotBlocked(InventoryAutomation.Result result) {
        if (result.blocked()) throw new IllegalStateException("Native progression blocked at " + stage + " / tick " + currentTick + ": " + result.detail());
    }
    private static void verifySmithing(ServerSnapshot snapshot) {
        require(snapshot.survival, "Upgrades remain in genuine server Survival state");
        for (int i = 0; i < NETHERITE.length; i++) {
            require(count(snapshot, NETHERITE[i]) == 1 && count(snapshot, DIAMOND[i]) == 0, "One diamond armor piece became exactly one real netherite piece: " + NETHERITE[i].getName().getString());
            Armor armor = snapshot.upgrades.get(i);
            require(armor.item == NETHERITE[i] && armor.protection == 4 && armor.unbreaking == 3, "The upgraded server item preserves both original enchantments: " + NETHERITE[i].getName().getString());
        }
        require(count(snapshot, Items.DIAMOND) == 0 && count(snapshot, Items.NETHERRACK) == 0, "Template duplication consumes exactly 21 diamonds and three netherrack");
        require(count(snapshot, Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE) == 0, "Four real upgrades consume the initial and three duplicated templates");
        require(count(snapshot, Items.NETHERITE_INGOT) == 0, "Four real upgrades consume exactly four netherite ingots");
        require(count(snapshot, Items.IRON_INGOT) == 2 && count(snapshot, Items.OAK_PLANKS) == 8, "Unrelated supplied iron and planks are preserved");
    }
    private static void requestSnapshot(MinecraftClient mc) {
        if (snapshotPending || mc.getServer() == null || mc.player == null) return;
        snapshotPending = true; lastSnapshot = currentTick; int requested = currentTick; UUID playerId = mc.player.getUuid();
        mc.getServer().execute(() -> {
            try {
                ServerPlayerEntity player = mc.getServer().getPlayerManager().getPlayer(playerId);
                if (player == null) throw new IllegalStateException("Progression player left the server");
                Map<Item, Integer> items = new HashMap<>();
                for (int i = 0; i < player.getInventory().size(); i++) { ItemStack stack = player.getInventory().getStack(i); if (!stack.isEmpty()) items.merge(stack.getItem(), stack.getCount(), Integer::sum); }
                List<Armor> upgrades = new ArrayList<>(), equipped = new ArrayList<>();
                for (int i = 0; i < NETHERITE.length; i++) {
                    ItemStack found = ItemStack.EMPTY;
                    for (int slot = 0; slot < player.getInventory().size(); slot++) if (player.getInventory().getStack(slot).isOf(NETHERITE[i])) { found = player.getInventory().getStack(slot); break; }
                    upgrades.add(armor(found)); equipped.add(armor(player.getEquippedStack(SLOTS[i])));
                }
                boolean crafting = false, smithing = false; var world = player.getServerWorld();
                for (BlockPos pos : BlockPos.iterate(CENTER.add(-7, -1, -7), CENTER.add(7, 3, 7))) {
                    crafting |= world.getBlockState(pos).isOf(Blocks.CRAFTING_TABLE); smithing |= world.getBlockState(pos).isOf(Blocks.SMITHING_TABLE);
                }
                int drops = world.getEntitiesByClass(ItemEntity.class, new Box(82, 159, 82, 96, 171, 96), entity -> entity.isAlive()).size();
                observed = new ServerSnapshot(requested, player.interactionManager.getGameMode() == GameMode.SURVIVAL, Map.copyOf(items), List.copyOf(upgrades), List.copyOf(equipped), player.currentScreenHandler.getCursorStack().isEmpty(), drops, crafting, smithing);
            } catch (RuntimeException error) { serverFailure = error.toString(); }
            finally { snapshotPending = false; }
        });
    }
    private static Armor armor(ItemStack stack) { return new Armor(stack.getItem(), InventoryAutomation.level(stack, "protection"), InventoryAutomation.level(stack, "unbreaking")); }
    private static int count(ServerSnapshot snapshot, Item item) { return snapshot.items.getOrDefault(item, 0); }
    private static int age() { return currentTick - entered; }
    private static void enter(Stage next) { stage = next; entered = currentTick + 1; AnalyzerClient.LOGGER.info("Native progression acceptance: {}", next); }
    private static void require(boolean condition, String detail) { if (!condition) throw new IllegalStateException("Native progression failed at " + stage + ": " + detail); checks++; AnalyzerClient.LOGGER.info("Native progression check passed: {}", detail); }
}
