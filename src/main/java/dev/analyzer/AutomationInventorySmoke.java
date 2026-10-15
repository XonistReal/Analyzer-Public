package dev.analyzer;

import dev.analyzer.automation.AutomationEngine;
import dev.analyzer.automation.AutomationNavigator;
import dev.analyzer.automation.InventoryAutomation;
import dev.analyzer.automation.TaskPlanner;
import dev.analyzer.ui.AutomationScreen;
import dev.analyzer.ui.MenuScreens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.EnchantmentScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

/** Isolated Survival fixtures exercising normal server-backed crafting and workstation actions. */
final class AutomationInventorySmoke {
    static final int LAST_TICK = 3600;
    private enum Stage { MIXED_SETUP, MIXED, MIXED_VERIFY, SETUP, CURSOR, CURSOR_VERIFY, BOOTSTRAP, BOOTSTRAP_VERIFY, STONE, STONE_VERIFY,
        IRON, IRON_VERIFY, DIAMOND, DIAMOND_VERIFY, EQUIP, EQUIP_VERIFY, KIT_CAPTURE,
        TABLE_SETUP, TABLE, TABLE_VERIFY, QUALITY_SETUP, QUALITY, QUALITY_VERIFY, FINAL_EQUIP,
        FINAL_VERIFY, GEAR_CAPTURE, CONSOLE, CONSOLE_CAPTURE, FINISHED }
    private static final Item[] ARMOR = {Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS};
    private static final EquipmentSlot[] EQUIPMENT = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static InventoryAutomation worker;
    private static AutomationNavigator navigator;
    private static Stage stage = Stage.SETUP;
    private static int entered, tick, assertions, captures;
    private static long clicksBefore;
    private static AutomationScreen console;
    private AutomationInventorySmoke() { }
    static boolean done() { return stage == Stage.FINISHED; }
    static int assertions() { return assertions; }
    static int captures() { return captures; }

    static void tick(MinecraftClient mc, int t) {
        if (done()) return;
        tick = t;
        if (t > LAST_TICK) throw new IllegalStateException("Inventory automation smoke exceeded " + LAST_TICK + " ticks at " + stage);
        if (t - entered > 1800) throw new IllegalStateException("Inventory automation stage timed out: " + stage);
        if (t == 0) setupMixed(mc);
        switch (stage) {
            case MIXED_SETUP -> {
                if (age() < 30) return;
                require(countServer(mc, Items.OAK_PLANKS) == 2 && countServer(mc, Items.BIRCH_PLANKS) == 2
                        && countServer(mc, Items.OAK_LOG) == 0, "Mixed-plank fixture has exactly two oak and two birch planks without logs");
                enter(Stage.MIXED);
            }
            case MIXED -> runUntilDone("bootstrap", Stage.MIXED_VERIFY);
            case MIXED_VERIFY -> {
                if (age() < 20) return;
                require(countServer(mc, Items.CRAFTING_TABLE) == 1 && countServer(mc, Items.OAK_PLANKS) == 0
                        && countServer(mc, Items.BIRCH_PLANKS) == 0, "A real crafting-table recipe consumes the four mixed-species planks");
                require(countServer(mc, Items.WOODEN_PICKAXE) == 1 && countServer(mc, Items.STICK) == 4,
                        "Mixed-plank bootstrap preserves the supplied usable pickaxe and sticks");
                require(mc.player.currentScreenHandler.getCursorStack().isEmpty() && itemEntities(mc) == 0,
                        "Mixed-plank crafting leaves no cursor item or dropped ingredients");
                worker.stop(); setup(mc); enter(Stage.SETUP);
            }
            case SETUP -> {
                if (age() < 30) return;
                require(mc.getServer() != null && serverPlayer(mc) != null, "Inventory fixture uses an integrated server");
                require(!mc.player.getAbilities().creativeMode && !serverPlayer(mc).getAbilities().creativeMode, "Inventory fixture runs in genuine Survival");
                require(countServer(mc, Items.OAK_LOG) == 16 && countServer(mc, Items.DIAMOND) == 29, "Resource fixture arrived in authoritative server inventory");
                clicksBefore = worker.clicks(); enter(Stage.CURSOR);
            }
            case CURSOR -> {
                InventoryAutomation.Result result = run("bootstrap");
                requireNotBlocked(result);
                if (!mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
                    require(worker.clicks() > clicksBefore, "Bootstrap picks up a real ingredient using a normal slot click");
                    worker.stop(); enter(Stage.CURSOR_VERIFY);
                }
            }
            case CURSOR_VERIFY -> {
                if (age() < 25) return;
                require(mc.player.currentScreenHandler.getCursorStack().isEmpty() && serverPlayer(mc).currentScreenHandler.getCursorStack().isEmpty(), "Stopping mid-craft returns the task-owned cursor on client and server");
                require(countServer(mc, Items.OAK_LOG) == 16, "Stopping mid-craft preserves all logs");
                require(itemEntities(mc) == 0, "Stopping mid-craft drops no item entities");
                enter(Stage.BOOTSTRAP);
            }
            case BOOTSTRAP -> runUntilDone("bootstrap", Stage.BOOTSTRAP_VERIFY);
            case BOOTSTRAP_VERIFY -> {
                if (age() < 20) return;
                require(countServer(mc, Items.WOODEN_PICKAXE) == 1, "Bootstrap crafts an authoritative wooden pickaxe");
                require(!nearServerBlock(mc, Blocks.CRAFTING_TABLE) && countServer(mc, Items.CRAFTING_TABLE) == 1 && !worker.hasOwnedWorkstations() && worker.clicks() > clicksBefore + 10, "Bootstrap uses a real table for its recipe and recovers the acknowledged owned table into server inventory");
                require(countServer(mc, Items.STICK) >= 2, "Bootstrap leaves real crafting sticks");
                enter(Stage.STONE);
            }
            case STONE -> runUntilDone("stone-tools", Stage.STONE_VERIFY);
            case STONE_VERIFY -> {
                if (age() < 20) return;
                require(countServer(mc, Items.STONE_PICKAXE) == 1, "Stone stage crafts the actual stone pickaxe");
                require(countServer(mc, Items.FURNACE) == 1, "Stone stage crafts an authoritative furnace item");
                require(countServer(mc, Items.COBBLESTONE) == 21, "Furnace and stone-pick recipes consume exactly eleven cobblestone");
                enter(Stage.IRON);
            }
            case IRON -> runUntilDone("iron-tools", Stage.IRON_VERIFY);
            case IRON_VERIFY -> {
                if (age() < 20) return;
                require(countServer(mc, Items.IRON_PICKAXE) == 1, "Furnace smelting and crafting yield a real iron pickaxe");
                require(nearServerBlock(mc, Blocks.FURNACE), "Iron stage places a real server furnace");
                require(countServer(mc, Items.RAW_IRON) < 8, "Furnace received the actual raw-iron input");
                enter(Stage.DIAMOND);
            }
            case DIAMOND -> runUntilDone("diamond-kit", Stage.DIAMOND_VERIFY);
            case DIAMOND_VERIFY -> {
                if (age() < 20) return;
                for (Item armor : ARMOR) require(countServer(mc, armor) == 1, "Diamond crafting produces " + armor.getName().getString());
                require(countServer(mc, Items.DIAMOND_PICKAXE) == 1 && countServer(mc, Items.DIAMOND_SWORD) == 1, "Diamond kit includes actual pickaxe and sword");
                require(countServer(mc, Items.DIAMOND) == 0, "Armor and tools consume exactly twenty-nine diamonds");
                enter(Stage.EQUIP);
            }
            case EQUIP -> runUntilDone("equip", Stage.EQUIP_VERIFY);
            case EQUIP_VERIFY -> {
                if (age() < 20) return;
                for (int i = 0; i < ARMOR.length; i++) require(serverPlayer(mc).getEquippedStack(EQUIPMENT[i]).isOf(ARMOR[i]), "Normal equip transaction equips server armor slot " + EQUIPMENT[i]);
                require(itemEntities(mc) == 0, "Crafting and equip fixture drops no items");
                mc.setScreen(new InventoryScreen(mc.player)); enter(Stage.KIT_CAPTURE);
            }
            case KIT_CAPTURE -> {
                if (age() < 15) return;
                capture(mc, "analyzer-automation-crafted-kit.png"); mc.setScreen(null); setupTable(mc); enter(Stage.TABLE_SETUP);
            }
            case TABLE_SETUP -> {
                if (age() < 30) return;
                require(serverPlayer(mc).experienceLevel == 100 && countServer(mc, Items.LAPIS_LAZULI) == 13, "Table fixture has real XP levels and lapis");
                worker = new InventoryAutomation(new AutomationNavigator()); enter(Stage.TABLE);
            }
            case TABLE -> {
                ItemStack helmet = findServer(mc, Items.DIAMOND_HELMET);
                if (!helmet.isEmpty() && helmet.hasEnchantments() && serverPlayer(mc).experienceLevel < 100) {
                    worker.stop(); enter(Stage.TABLE_VERIFY); return;
                }
                InventoryAutomation.Result result = run("diamond-armor");
                requireNotBlocked(result);
            }
            case TABLE_VERIFY -> {
                if (age() < 25) return;
                ItemStack helmet = findServer(mc, Items.DIAMOND_HELMET);
                require(!helmet.isEmpty() && helmet.hasEnchantments(), "Normal table button yields a server-enchanted helmet");
                require(serverPlayer(mc).experienceLevel == 97, "Level-thirty enchantment spends exactly three XP levels");
                require(countServer(mc, Items.LAPIS_LAZULI) == 10, "Level-thirty enchantment spends exactly three lapis");
                require(mc.player.currentScreenHandler.getCursorStack().isEmpty(), "Table shutdown leaves an empty cursor");
                setupQuality(mc); enter(Stage.QUALITY_SETUP);
            }
            case QUALITY_SETUP -> {
                if (age() < 30) return;
                require(countServer(mc, Items.ENCHANTED_BOOK) == 2, "Anvil fixture contains exactly one book for each requested upgrade");
                worker = new InventoryAutomation(new AutomationNavigator()); enter(Stage.QUALITY);
            }
            case QUALITY -> runUntilDone("diamond-armor", Stage.QUALITY_VERIFY);
            case QUALITY_VERIFY -> {
                if (age() < 25) return;
                require(countServer(mc, Items.ENCHANTED_BOOK) == 0, "Anvil consumed the last matching upgrade books without deadlocking");
                require(serverPlayer(mc).experienceLevel < 100, "Actual anvil upgrades spend server XP");
                for (Item armor : ARMOR) {
                    ItemStack stack = findServer(mc, armor);
                    int protection = InventoryAutomation.level(stack, "protection"), unbreaking = InventoryAutomation.level(stack, "unbreaking");
                    require(protection == 4 && unbreaking == 3, "Server item components verify Protection IV and Unbreaking III: " + armor.getName().getString() + " (actual " + protection + "/" + unbreaking + ")");
                }
                require(InventoryAutomation.goodArmor(), "Quality completion agrees with real inventory components");
                enter(Stage.FINAL_EQUIP);
            }
            case FINAL_EQUIP -> runUntilDone("equip", Stage.FINAL_VERIFY);
            case FINAL_VERIFY -> {
                if (age() < 25) return;
                for (int i = 0; i < ARMOR.length; i++) {
                    ItemStack stack = serverPlayer(mc).getEquippedStack(EQUIPMENT[i]);
                    require(stack.isOf(ARMOR[i]) && InventoryAutomation.level(stack, "protection") == 4 && InventoryAutomation.level(stack, "unbreaking") == 3, "Upgraded quality armor is equipped on server: " + EQUIPMENT[i]);
                }
                require(itemEntities(mc) == 0 && mc.player.currentScreenHandler.getCursorStack().isEmpty(), "Anvil and final equip leave no drops or cursor item");
                mc.setScreen(new InventoryScreen(mc.player)); enter(Stage.GEAR_CAPTURE);
            }
            case GEAR_CAPTURE -> {
                if (age() < 15) return;
                capture(mc, "analyzer-automation-enchanted-kit.png"); mc.setScreen(null);
                console = new AutomationScreen(MenuScreens.create(AnalyzerClient.CORE), AutomationEngine.INSTANCE, AnalyzerClient.CORE);
                mc.setScreen(console); enter(Stage.CONSOLE);
            }
            case CONSOLE -> {
                if (age() == 10) {
                    // Use the current rendered control positions, including the actual canvas transform.
                    clickConsole("preset:3");
                }
                if (age() == 15) clickConsole("review");
                if (age() < 25) return;
                TaskPlanner.Plan plan = console.reviewedPlan();
                require(mc.currentScreen == console && plan != null && !console.isReviewError(),
                        "Automation console reviews the Enchanted kit preset through real UI input: " + console.errorText());
                require(plan.title().equals("Enchanted diamond kit") && plan.steps().stream().anyMatch(s -> s.action() == TaskPlanner.Action.ENCHANT),
                        "Actual reviewed console plan contains the selected enchanted kit and enchanting stages");
                enter(Stage.CONSOLE_CAPTURE);
            }
            case CONSOLE_CAPTURE -> {
                if (age() < 5) return;
                capture(mc, "analyzer-automation-expedition-plan.png"); mc.setScreen(null);
                require(!AutomationEngine.INSTANCE.isRunning(), "Reviewing a plan never starts pathing or inventory automation");
                require(worker.clicks() > 0 && GL11.glGetError() == GL11.GL_NO_ERROR, "Inventory automation completes normal transactions with clean final GL state");
                AnalyzerClient.LOGGER.info("ANALYZER_AUTOMATION_INVENTORY_COMPLETE: {} assertions, {} screenshots, {} elapsed ticks; quality armor verified on server", assertions, captures, t);
                enter(Stage.FINISHED);
            }
            case FINISHED -> { }
        }
    }

    private static void setupMixed(MinecraftClient mc) {
        setup(mc);
        command(mc, "clear AnalyzerDev");
        command(mc, "give AnalyzerDev minecraft:oak_planks 2"); command(mc, "give AnalyzerDev minecraft:birch_planks 2");
        command(mc, "give AnalyzerDev minecraft:stick 4"); command(mc, "give AnalyzerDev minecraft:wooden_pickaxe");
        stage = Stage.MIXED_SETUP;
    }
    private static void setup(MinecraftClient mc) {
        mc.setScreen(null); AnalyzerClient.CORE.setEnabled("expedition", false);
        for (var module : AnalyzerClient.CORE.modules()) if (module.category() == dev.analyzer.core.Category.COMBAT || module.category() == dev.analyzer.core.Category.HVH || module.category() == dev.analyzer.core.Category.MOVEMENT || module.category() == dev.analyzer.core.Category.PLAYER) AnalyzerClient.CORE.setEnabled(module.id(), false);
        command(mc, "kill @e[type=!minecraft:player]"); command(mc, "gamerule doMobSpawning false"); command(mc, "difficulty peaceful");
        command(mc, "gamemode survival AnalyzerDev"); command(mc, "clear AnalyzerDev"); command(mc, "effect clear AnalyzerDev");
        command(mc, "effect give AnalyzerDev minecraft:instant_health 1 4 true"); command(mc, "effect give AnalyzerDev minecraft:saturation 180 4 true");
        command(mc, "fill 32 160 32 64 160 64 minecraft:grass_block"); command(mc, "fill 32 161 32 64 180 64 minecraft:air");
        command(mc, "time set 1000"); command(mc, "weather clear"); command(mc, "tp AnalyzerDev 40.5 161 40.5 180 15");
        command(mc, "give AnalyzerDev minecraft:oak_log 16"); command(mc, "give AnalyzerDev minecraft:raw_iron 8");
        command(mc, "give AnalyzerDev minecraft:coal 16"); command(mc, "give AnalyzerDev minecraft:cobblestone 32"); command(mc, "give AnalyzerDev minecraft:diamond 29");
        navigator = new AutomationNavigator(); worker = new InventoryAutomation(navigator); entered = tick;
    }
    private static void setupTable(MinecraftClient mc) {
        worker.stop(); command(mc, "clear AnalyzerDev"); command(mc, "fill 38 161 36 46 166 46 minecraft:air");
        command(mc, "setblock 42 161 40 minecraft:enchanting_table");
        int placed = 0;
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) if ((Math.abs(x) == 2 || Math.abs(z) == 2) && placed++ < 15)
            command(mc, "setblock " + (42 + x) + " 161 " + (40 + z) + " minecraft:bookshelf");
        command(mc, "tp AnalyzerDev 42.5 161 43.5 180 15"); command(mc, "experience set AnalyzerDev 100 levels");
        command(mc, "give AnalyzerDev minecraft:lapis_lazuli 13"); command(mc, "give AnalyzerDev minecraft:diamond_helmet");
        for (String id : new String[]{"diamond_chestplate", "diamond_leggings", "diamond_boots"}) command(mc, "give AnalyzerDev minecraft:" + id + enchantments(4, 3));
    }
    private static void setupQuality(MinecraftClient mc) {
        worker.stop(); command(mc, "clear AnalyzerDev"); command(mc, "fill 38 161 36 46 166 46 minecraft:air");
        command(mc, "setblock 43 161 41 minecraft:anvil"); command(mc, "tp AnalyzerDev 41.5 161 41.5 -90 15");
        command(mc, "experience set AnalyzerDev 100 levels");
        command(mc, "give AnalyzerDev minecraft:diamond_helmet" + enchantments(3, 2));
        for (String id : new String[]{"diamond_chestplate", "diamond_leggings", "diamond_boots"}) command(mc, "give AnalyzerDev minecraft:" + id + enchantments(4, 3));
        command(mc, "give AnalyzerDev minecraft:enchanted_book[minecraft:stored_enchantments={levels:{\"minecraft:protection\":4}}]");
        command(mc, "give AnalyzerDev minecraft:enchanted_book[minecraft:stored_enchantments={levels:{\"minecraft:unbreaking\":3}}]");
    }
    private static String enchantments(int protection, int unbreaking) { return "[minecraft:enchantments={levels:{\"minecraft:protection\":" + protection + ",\"minecraft:unbreaking\":" + unbreaking + "}}]"; }
    private static InventoryAutomation.Result run(String job) { navigator.tick(); InventoryAutomation.Result result = worker.tickTransfer(); return result == null ? worker.tick(job, 30) : result; }
    private static void runUntilDone(String job, Stage next) {
        InventoryAutomation.Result result = run(job); requireNotBlocked(result);
        if (result.done()) { AnalyzerClient.LOGGER.info("Inventory smoke stage completed: {} / {} / {} clicks", job, result.detail(), worker.clicks()); enter(next); }
    }
    private static void requireNotBlocked(InventoryAutomation.Result result) { if (result.blocked()) throw new IllegalStateException("Inventory smoke blocked at " + stage + " / tick " + tick + ": " + result.detail()); }
    private static void enter(Stage next) { stage = next; entered = tick; }
    private static int age() { return tick - entered; }
    private static ServerPlayerEntity serverPlayer(MinecraftClient mc) { return mc.getServer().getPlayerManager().getPlayer(mc.player.getUuid()); }
    private static int countServer(MinecraftClient mc, Item item) { int count = 0; var inv = serverPlayer(mc).getInventory(); for (int i = 0; i < inv.size(); i++) if (inv.getStack(i).isOf(item)) count += inv.getStack(i).getCount(); return count; }
    private static ItemStack findServer(MinecraftClient mc, Item item) {
        var player = serverPlayer(mc); var inv = player.getInventory();
        for (int i = 0; i < inv.size(); i++) if (inv.getStack(i).isOf(item)) return inv.getStack(i);
        if (player.currentScreenHandler instanceof EnchantmentScreenHandler && player.currentScreenHandler.getSlot(0).getStack().isOf(item)) return player.currentScreenHandler.getSlot(0).getStack();
        return ItemStack.EMPTY;
    }
    private static int itemEntities(MinecraftClient mc) { int count = 0; for (var entity : mc.world.getEntities()) if (entity instanceof ItemEntity) count++; return count; }
    private static boolean nearServerBlock(MinecraftClient mc, Block block) {
        var world = mc.getServer().getWorld(mc.world.getRegistryKey());
        for (BlockPos pos : BlockPos.iterate(mc.player.getBlockPos().add(-8, -2, -8), mc.player.getBlockPos().add(8, 2, 8))) if (world.getBlockState(pos).isOf(block)) return true;
        return false;
    }
    private static void clickConsole(String control) {
        double[] point = console.controlPoint(control);
        require(point != null, "The automation console renders the " + control + " control");
        require(console.mouseClicked(point[0], point[1], GLFW.GLFW_MOUSE_BUTTON_LEFT),
                "The rendered " + control + " control accepts normal mouse input");
    }
    private static void command(MinecraftClient mc, String text) { mc.getServer().execute(() -> mc.getServer().getCommandManager().executeWithPrefix(mc.getServer().getCommandSource().withSilent(), text)); }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException("Inventory check failed at " + stage + " / " + tick + ": " + message); assertions++; AnalyzerClient.LOGGER.info("Inventory check passed: {}", message); }
    private static void capture(MinecraftClient mc, String name) {
        mc.inGameHud.getChatHud().clear(false); mc.getToastManager().clear();
        require(GL11.glGetError() == GL11.GL_NO_ERROR, "Inventory GL state before " + name);
        ScreenshotRecorder.saveScreenshot(mc.runDirectory, name, mc.getFramebuffer(), text -> AnalyzerClient.LOGGER.info("Inventory screenshot: {}", text.getString())); captures++;
    }
}
