package dev.analyzer;

import dev.analyzer.automation.AutomationEngine;
import dev.analyzer.automation.InventoryAutomation;
import dev.analyzer.core.Category;
import dev.analyzer.gameplay.GameplayEngine;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;

/** Server-backed repeated ordinary food use across the trigger threshold, plus disable cleanup. */
final class NativeAutoEatSmoke {
    private enum Stage { SETUP, ABOVE, FILL, FULL, DISABLE_START, DISABLED, FINISHED }
    private static Stage stage = Stage.SETUP;
    private static int entered, tick, checks, captures;
    private static volatile int food = -1, apples = -1;
    private static volatile boolean ready;
    private static boolean initialized;
    private static long actions;
    static boolean done() {return stage == Stage.FINISHED;}
    static void tick(MinecraftClient mc, int now) {
        if (done()) return;
        if (!FabricLoader.getInstance().isDevelopmentEnvironment() || !Boolean.getBoolean("analyzer.nativeAutomationSmoke")) throw new IllegalStateException("Auto Eat fixture is development-only");
        tick = now;
        if (mc.player == null || mc.world == null || mc.getServer() == null) return;
        if (!initialized) {initialized = true; entered = now; setup(mc);}
        if (age() > 500) throw new IllegalStateException("Auto Eat acceptance timed out at " + stage + ": food=" + food + ", apples=" + apples + ", status=" + GameplayEngine.status("auto-eat"));
        if (ready && now % 5 == 0) mc.getServer().execute(() -> {
            var p = mc.getServer().getPlayerManager().getPlayer(mc.player.getUuid());
            food = p.getHungerManager().getFoodLevel(); apples = p.getInventory().count(Items.APPLE);
        });
        switch (stage) {
            case SETUP -> {
                if (!ready || age() < 25 || food != 16 || apples != 5) return;
                require(mc.interactionManager.getCurrentGameMode() == GameMode.SURVIVAL, "Auto Eat runs in actual Survival");
                AnalyzerClient.CORE.setValue("auto-eat", "hunger", 14d); actions = GameplayEngine.actions("auto-eat");
                AnalyzerClient.CORE.setEnabled("auto-eat", true); enter(Stage.ABOVE);
            }
            case ABOVE -> {
                if (age() < 35) return;
                require(apples == 5 && food == 16 && GameplayEngine.actions("auto-eat") == actions, "Food above the configured threshold does not start eating");
                hunger(mc, 10); enter(Stage.FILL);
            }
            case FILL -> {
                if (food < 20 || mc.player.getHungerManager().getFoodLevel() < 20) return;
                require(apples == 2 && InventoryAutomation.count(Items.APPLE) == 2, "Three actual apples refill hunger from ten to twenty, continuing after crossing fourteen");
                require(GameplayEngine.actions("auto-eat") >= actions + 3, "Every bite is a normal accepted item interaction");
                enter(Stage.FULL);
            }
            case FULL -> {
                if (age() < 30) return;
                require(apples == 2 && food == 20 && !mc.player.isUsingItem() && !mc.options.useKey.isPressed(), "Full hunger stops eating, preserves remaining food and releases use input");
                require(mc.player.getInventory().selectedSlot == 4, "The original selected hotbar slot is restored after the refill cycle");
                ScreenshotRecorder.saveScreenshot(mc.runDirectory, "analyzer-native-autoeat-full-cycle.png", mc.getFramebuffer(), t -> {}); captures++;
                hunger(mc, 10); enter(Stage.DISABLE_START);
            }
            case DISABLE_START -> {
                if (!mc.player.isUsingItem()) return;
                AnalyzerClient.CORE.setEnabled("auto-eat", false); enter(Stage.DISABLED);
            }
            case DISABLED -> {
                if (age() < 45) return;
                require(apples == 2 && food == 10, "Disabling mid-bite cancels the refill cycle without consuming another item");
                require(!mc.player.isUsingItem() && !mc.options.useKey.isPressed() && mc.player.getInventory().selectedSlot == 4, "Disable restores the slot and releases all owned food use");
                stage = Stage.FINISHED;
                AnalyzerClient.LOGGER.info("ANALYZER_NATIVE_AUTOEAT_COMPLETE: {} assertions, {} captures; real three-apple refill to full hunger, threshold gating, item counts, disable and slot/use cleanup", checks, captures);
            }
            case FINISHED -> { }
        }
    }
    private static void setup(MinecraftClient mc) {
        AutomationEngine.INSTANCE.stop(); mc.setScreen(null);
        for (var module : AnalyzerClient.CORE.modules()) if (module.category() == Category.PLAYER || module.category() == Category.COMBAT || module.category() == Category.HVH || module.category() == Category.MOVEMENT) AnalyzerClient.CORE.setEnabled(module.id(), false);
        AnalyzerClient.CORE.setEnabled("expedition", false);
        var id = mc.player.getUuid();
        mc.getServer().execute(() -> {
            var server = mc.getServer(); var p = server.getPlayerManager().getPlayer(id); var world = p.getServerWorld();
            for (int x = 355; x <= 365; x++) for (int z = 355; z <= 365; z++) {world.getChunk(new BlockPos(x, 160, z)); world.setBlockState(new BlockPos(x, 160, z), Blocks.BEDROCK.getDefaultState()); for (int y = 161; y <= 164; y++) world.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState());}
            var source = server.getCommandSource().withLevel(4).withSilent();
            for (String command : new String[]{"difficulty normal", "gamerule doMobSpawning false", "gamerule naturalRegeneration false", "effect clear AnalyzerDev", "gamemode survival AnalyzerDev", "clear AnalyzerDev", "tp AnalyzerDev 360.5 161 360.5"}) server.getCommandManager().executeWithPrefix(source, command);
            p.getInventory().setStack(0, new ItemStack(Items.APPLE, 5)); p.getInventory().selectedSlot = 4;
            p.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.UpdateSelectedSlotS2CPacket(4));
            p.getHungerManager().setFoodLevel(16); p.getHungerManager().setSaturationLevel(0); p.setHealth(20); p.currentScreenHandler.sendContentUpdates(); ready = true;
        });
    }
    private static void hunger(MinecraftClient mc, int level) {var id = mc.player.getUuid(); mc.getServer().execute(() -> {var p = mc.getServer().getPlayerManager().getPlayer(id); p.getHungerManager().setFoodLevel(level); p.getHungerManager().setSaturationLevel(0);});}
    private static int age() {return tick - entered;}
    private static void enter(Stage value) {stage = value; entered = tick + 1;}
    private static void require(boolean value, String message) {if (!value) throw new IllegalStateException("Native Auto Eat assertion: " + message); checks++;}
}
