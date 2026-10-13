package dev.analyzer;

import dev.analyzer.core.Category;
import dev.analyzer.render.VisualEffects;
import dev.analyzer.gameplay.GameplayEngine;
import dev.analyzer.gameplay.UtilityEngine;
import dev.analyzer.ui.MenuScreens;
import dev.analyzer.ui.CatalogScreen;
import dev.analyzer.ui.StudioScreen;
import dev.analyzer.ui.HudEditorScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.Monster;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.opengl.GL11;

/** Extra integration scenarios in the existing isolated development world. */
final class ExpansionSmoke {
    private static long placeBefore, breakBefore, attacksBefore, inventoryBefore;
    private static float healthBefore;
    private static LivingEntity duelTarget;
    private static double stepBefore;
    private static int screenshots;
    private static String clipboardBefore;
    private static double watermarkBefore;
    private static long hvhBefore, explosionBefore, utilityBefore;
    private static final BlockPos BLOCK = new BlockPos(11, 161, 18);
    private ExpansionSmoke() { }
    static void tick(MinecraftClient mc, int t) {
        var core = AnalyzerClient.CORE;
        if (t >= 16 && t <= 48 && t % 2 == 0) screenshot(mc, "analyzer-block-demo-" + t + ".png");
        switch (t) {
            case 0 -> {
                clipboardBefore = mc.keyboard.getClipboard();
                mc.setScreen(null);
                for (var module : core.modules()) if (module.category() == Category.COMBAT || module.category() == Category.MOVEMENT || module.category() == Category.HVH || module.category() == Category.PLAYER) core.setEnabled(module.id(), false);
                core.setEnabled("place-animation", true); core.setValue("place-animation", "duration", 1.5);
                core.setEnabled("break-animation", true); core.setValue("break-animation", "duration", 1.5);
                command(mc, "gamemode creative AnalyzerDev"); command(mc, "tp AnalyzerDev 11 161 21 180 12");
                command(mc, "item replace entity AnalyzerDev hotbar.0 with minecraft:stone 64");
                command(mc, "setblock 11 161 18 minecraft:air");
                placeBefore = VisualEffects.emitted("place-animation"); breakBefore = VisualEffects.emitted("break-animation");
            }
            case 15 -> place(mc);
            case 21 -> {
                require(mc.world.getBlockState(BLOCK).isOf(net.minecraft.block.Blocks.STONE), "Actual normal stone placement");
                require(VisualEffects.emitted("place-animation") > placeBefore, "Placement emits enabled animation");
                require(VisualEffects.frameDiagnostics().boxes() > 0, "Placement ghost submits actual geometry");
                screenshot(mc, "analyzer-place-animation.png");
            }
            case 30 -> mc.interactionManager.attackBlock(BLOCK, Direction.SOUTH);
            case 36 -> {
                require(mc.world.getBlockState(BLOCK).isAir(), "Actual normal block break");
                require(VisualEffects.emitted("break-animation") > breakBefore, "Break emits enabled animation");
                require(VisualEffects.frameDiagnostics().segments() > 0, "Break debris submits actual geometry");
                screenshot(mc, "analyzer-break-animation.png");
                core.setEnabled("place-animation", false); core.setEnabled("break-animation", false);
                placeBefore = VisualEffects.emitted("place-animation"); breakBefore = VisualEffects.emitted("break-animation");
            }
            case 50 -> place(mc);
            case 55 -> mc.interactionManager.attackBlock(BLOCK, Direction.SOUTH);
            case 62 -> {
                require(VisualEffects.emitted("place-animation") == placeBefore, "Placement animation toggle stops events");
                require(VisualEffects.emitted("break-animation") == breakBefore, "Break animation toggle stops events");
                core.setEnabled("place-animation", true); core.setEnabled("break-animation", true);
                command(mc, "kill @e[type=minecraft:zombie]");
                command(mc, "summon minecraft:zombie 11 161 19 {NoAI:1b,Silent:1b,PersistenceRequired:1b}");
                command(mc, "item replace entity AnalyzerDev hotbar.0 with minecraft:diamond_sword");
                core.setEnabled("hit-particles", true); core.setEnabled("damage-numbers", true);
                core.setEnabled("hit-markers", true); core.setEnabled("target-esp", true); core.setEnabled("target-hud", true);
            }
            case 85 -> {
                duelTarget = nearestHostile(mc); require(duelTarget != null, "Nearby combat fixture loaded");
                healthBefore = duelTarget.getHealth(); attacksBefore = GameplayEngine.diagnostics().attacks();
                core.setValue("kill-aura", "players", false); core.setValue("kill-aura", "hostiles", true);
                core.setEnabled("kill-aura", true);
            }
            case 105 -> {
                require(GameplayEngine.diagnostics().attacks() > attacksBefore, "Kill Aura submits ordinary attacks");
                require(duelTarget.getHealth() < healthBefore, "Kill Aura changes actual target health");
                screenshot(mc, "analyzer-combat-effects.png"); core.setEnabled("kill-aura", false);
                attacksBefore = GameplayEngine.diagnostics().attacks();
            }
            case 125 -> {
                require(GameplayEngine.diagnostics().attacks() == attacksBefore, "Disabled aura stops attacking");
                stepBefore = mc.player.getAttributeInstance(EntityAttributes.STEP_HEIGHT).getBaseValue();
                core.setEnabled("step", true); core.setValue("step", "height", 1.25);
            }
            case 132 -> {
                require(Math.abs(mc.player.getAttributeInstance(EntityAttributes.STEP_HEIGHT).getBaseValue() - 1.25) < 0.01, "Step height applied"); core.setEnabled("step", false);
            }
            case 138 -> {
                require(Math.abs(mc.player.getAttributeInstance(EntityAttributes.STEP_HEIGHT).getBaseValue() - stepBefore) < 0.01, "Step height restored");
                core.setEnabled("auto-walk", true);
            }
            case 142 -> { require(mc.options.forwardKey.isPressed(), "Auto Walk owns forward input"); core.setEnabled("auto-walk", false); }
            case 146 -> {
                require(!mc.options.forwardKey.isPressed(), "Auto Walk releases forward input");
                command(mc, "tp AnalyzerDev 11 161 21 180 12");
                command(mc, "item replace entity AnalyzerDev weapon.offhand with minecraft:air");
                command(mc, "item replace entity AnalyzerDev hotbar.2 with minecraft:totem_of_undying");
                inventoryBefore = GameplayEngine.diagnostics().inventoryClicks();
            }
            case 160 -> core.setEnabled("auto-totem", true);
            case 176 -> {
                require(mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING), "Auto Totem performs real offhand inventory swap");
                require(GameplayEngine.diagnostics().inventoryClicks() > inventoryBefore, "Inventory swap dispatched");
                core.setEnabled("auto-totem", false); core.setEnabled("coord-snapper", true);
            }
            case 181 -> {
                require(!core.isEnabled("coord-snapper") && UtilityEngine.operations("coord-snapper") > 0, "Coordinates copied and one-shot module turned off");
                mc.keyboard.setClipboard(clipboardBefore);
                core.setEnabled("armor", true); core.setEnabled("potions", true); core.setEnabled("speed-hud", true);
                core.setEnabled("compass", true); core.setEnabled("ping", true); core.setEnabled("keystrokes", true);
                core.setEnabled("world-clock", true); core.setEnabled("biome", true); core.setEnabled("item-counter", true);
                core.setValue("hud", "theme", "Glass");
            }
            case 196 -> screenshot(mc, "analyzer-hud-glass.png");
            case 200 -> core.setValue("hud", "theme", "Classic");
            case 214 -> screenshot(mc, "analyzer-hud-classic.png");
            case 218 -> core.setValue("hud", "theme", "Minimal");
            case 232 -> screenshot(mc, "analyzer-hud-minimal.png");
            case 236 -> core.setValue("hud", "theme", "Neon");
            case 250 -> screenshot(mc, "analyzer-hud-neon.png");
            case 255 -> { core.setValue("menu", "layout", "Studio"); mc.setScreen(MenuScreens.create(core, "kill-aura")); }
            case 260 -> require(mc.currentScreen instanceof StudioScreen, "Studio shader GUI factory");
            case 266 -> control(mc, "module:kill-aura", 0);
            case 268 -> { require(core.isEnabled("kill-aura"), "Studio module toggle"); ((CatalogScreen) mc.currentScreen).inspect("kill-aura"); }
            case 271 -> control(mc, "setting:kill-aura:hostiles", 0);
            case 273 -> { require(!core.getBoolean("kill-aura", "hostiles"), "Studio inline boolean setting"); control(mc, "module:kill-aura", 0); }
            case 275 -> { require(mc.currentScreen instanceof StudioScreen, "Studio GUI remains selected"); screenshot(mc, "analyzer-gui-studio.png"); }
            case 280 -> { core.setValue("menu", "layout", "Click GUI"); mc.setScreen(MenuScreens.create(core)); }
            case 286 -> { control(mc, "search", 0); type(mc, "fly"); }
            case 290 -> control(mc, "module:fly", 0);
            case 292 -> { require(core.isEnabled("fly"), "Click GUI filtered module toggle"); control(mc, "module:fly", 0); }
            case 294 -> { mc.currentScreen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_A, 0, org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL); mc.currentScreen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE, 0, 0); }
            case 300 -> { require(mc.currentScreen.getClass().getSimpleName().equals("ClickGuiScreen"), "New Click GUI factory"); screenshot(mc, "analyzer-gui-click-gui.png"); }
            case 305 -> { core.setValue("menu", "layout", "Studio"); mc.setScreen(MenuScreens.create(core)); }
            case 325 -> { require(mc.currentScreen instanceof StudioScreen, "Studio remains the module default"); screenshot(mc, "analyzer-gui-studio-return.png"); }
            case 330 -> control(mc, "hud-editor", 0);
            case 336 -> {
                var editor = (HudEditorScreen) mc.currentScreen;
                watermarkBefore = core.getNumber("watermark", "x");
                double[] point = editor.controlPoint("widget:watermark"); require(point != null, "Editor exposes actual watermark bounds");
                editor.mouseClicked(point[0], point[1], 0); editor.mouseDragged(point[0] + 35, point[1] + 25, 0, 35, 25); editor.mouseReleased(point[0] + 35, point[1] + 25, 0);
            }
            case 339 -> { require(core.getNumber("watermark", "x") > watermarkBefore, "HUD drag changes normalized position"); control(mc, "theme", 0); control(mc, "scale-plus", 0); }
            case 343 -> { require(core.getChoice("hud", "theme").equals("Glass"), "HUD editor theme control"); require(core.getNumber("hud", "scale") == 0.65, "HUD editor increases default 60% scale to 65%"); }
            case 345 -> core.setValue("hud", "show-disabled", false);
            case 350 -> screenshot(mc, "analyzer-hud-editor.png");
            case 355 -> {
                require(VisualEffects.diagnostics() != null, "Visual diagnostics available");
                control(mc, "done", 0); mc.setScreen(null);
                core.setEnabled("explosion-effects", true); core.setEnabled("death-effects", true);
                command(mc, "kill @e[type=!minecraft:player]");
                command(mc, "fill 8 161 15 15 166 23 minecraft:air");
                command(mc, "fill 8 160 15 15 160 23 minecraft:polished_deepslate");
                command(mc, "setblock 12 160 18 minecraft:obsidian");
                command(mc, "tp AnalyzerDev 11.5 161 21.5 180 12");
                command(mc, "summon minecraft:zombie 11.5 161 17.5 {NoAI:1b,Silent:1b,PersistenceRequired:1b}");
                command(mc, "item replace entity AnalyzerDev hotbar.1 with minecraft:end_crystal 64");
                targetModule("crystal-aura"); core.setValue("crystal-aura", "self-distance", 2); core.setValue("crystal-aura", "health", 1);
                core.setValue("crystal-aura", "cooldown", 0.2); core.setValue("crystal-aura", "delay", 2);
                hvhBefore = GameplayEngine.actions("crystal-aura"); explosionBefore = VisualEffects.emitted("explosion-effects");
            }
            case 368 -> { command(mc, "kill @e[type=minecraft:item]"); command(mc, "kill @e[type=minecraft:experience_orb]"); }
            case 375 -> {
                AnalyzerClient.LOGGER.info("Crystal fixture: base={}, target={}, player={}, usingItem={}", mc.world.getBlockState(new BlockPos(12,160,18)), nearestHostile(mc), mc.player.getPos(), mc.player.isUsingItem());
                require(mc.world.getBlockState(new BlockPos(12,160,18)).isOf(net.minecraft.block.Blocks.OBSIDIAN) && nearestHostile(mc)!=null, "Crystal fixture has loaded obsidian and live reachable target");
                core.setEnabled("crystal-aura", true);
            }
            case 410 -> {
                AnalyzerClient.LOGGER.info("Crystal smoke: actions={}, player={}, health={}, status={}", GameplayEngine.actions("crystal-aura")-hvhBefore, mc.player.getPos(), mc.player.getHealth(), GameplayEngine.status("crystal-aura"));
                require(GameplayEngine.actions("crystal-aura") > hvhBefore, "Crystal Aura performs real crystal interaction");
                require(VisualEffects.emitted("explosion-effects") > explosionBefore, "Crystal Aura creates actual explosion packet");
                screenshot(mc, "analyzer-crystal-aura.png"); core.setEnabled("crystal-aura", false);
                command(mc, "kill @e[type=!minecraft:player]"); command(mc, "fill 8 161 15 15 166 23 minecraft:air");
                command(mc, "fill 8 160 15 15 160 23 minecraft:polished_deepslate");
                // The real crystal explosion can knock the creative player beyond vanilla anchor reach.
                command(mc, "tp AnalyzerDev 11.5 161 21.5 180 12");
                command(mc, "summon minecraft:zombie 11.5 161 17.5 {NoAI:1b,Silent:1b,PersistenceRequired:1b}");
                command(mc, "item replace entity AnalyzerDev hotbar.2 with minecraft:respawn_anchor 64");
                command(mc, "item replace entity AnalyzerDev hotbar.3 with minecraft:glowstone 64");
                targetModule("anchor-aura"); core.setValue("anchor-aura", "self-distance", 2); core.setValue("anchor-aura", "health", 1); core.setValue("anchor-aura", "delay", 2);
                hvhBefore = GameplayEngine.actions("anchor-aura"); explosionBefore = VisualEffects.emitted("explosion-effects");
            }
            case 430 -> core.setEnabled("anchor-aura", true);
            case 465 -> {
                AnalyzerClient.LOGGER.info("Anchor smoke: actions={}, explosions={}, player={}, status={}",
                    GameplayEngine.actions("anchor-aura") - hvhBefore, VisualEffects.emitted("explosion-effects") - explosionBefore,
                    mc.player.getPos(), GameplayEngine.status("anchor-aura"));
                require(GameplayEngine.actions("anchor-aura") >= hvhBefore + 3, "Anchor Aura places, charges and detonates");
                require(VisualEffects.emitted("explosion-effects") > explosionBefore, "Anchor Aura creates actual explosion packet");
                screenshot(mc, "analyzer-anchor-aura.png"); core.setEnabled("anchor-aura", false);
                command(mc, "kill @e[type=!minecraft:player]"); command(mc, "fill 9 161 19 14 165 24 minecraft:air");
                command(mc, "fill 9 160 19 14 160 24 minecraft:polished_deepslate"); command(mc, "tp AnalyzerDev 11.5 161 21.5 180 12");
                command(mc, "item replace entity AnalyzerDev hotbar.4 with minecraft:obsidian 64");
                hvhBefore = GameplayEngine.actions("surround"); core.setValue("surround", "delay", 2);
            }
            case 485 -> core.setEnabled("surround", true);
            case 515 -> {
                int walls = 0; BlockPos feet = new BlockPos(11, 161, 21);
                for (Direction d : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) if (mc.world.getBlockState(feet.offset(d)).isOf(net.minecraft.block.Blocks.OBSIDIAN)) walls++;
                require(walls == 4 && GameplayEngine.actions("surround") >= hvhBefore + 4, "Surround builds four actual obsidian sides");
                screenshot(mc, "analyzer-surround.png"); core.setEnabled("surround", false);
                command(mc, "fill 9 161 19 14 165 24 minecraft:air");
                command(mc, "item replace entity AnalyzerDev hotbar.0 with minecraft:diamond_pickaxe");
                mc.player.getInventory().selectedSlot = 0;
                utilityBefore = UtilityEngine.operations("nuker");
            }
            case 535 -> core.setEnabled("nuker", true);
            case 539 -> {
                core.setEnabled("nuker", false);
                require(UtilityEngine.operations("nuker") > utilityBefore && mc.world.getBlockState(new BlockPos(11, 160, 21)).isAir(), "Nuker performs real ordinary block mining");
                command(mc, "fill 9 160 19 14 160 24 minecraft:bedrock");
                command(mc, "setblock 11 161 19 minecraft:stone");
                command(mc, "tp AnalyzerDev 11.5 161 21.5 180 12");
                command(mc, "gamemode survival AnalyzerDev");
                core.setValue("nuker", "range", 2.5);
                utilityBefore = UtilityEngine.operations("nuker");
            }
            case 560 -> core.setEnabled("nuker", true);
            case 590 -> {
                core.setEnabled("nuker", false);
                require(mc.interactionManager.getCurrentGameMode() == net.minecraft.world.GameMode.SURVIVAL, "Mining fixture is Survival mode");
                require(UtilityEngine.operations("nuker") > utilityBefore && mc.world.getBlockState(new BlockPos(11, 161, 19)).isAir(), "Nuker completes real Survival mining progress without held attack");
                screenshot(mc, "analyzer-survival-mining.png");
                command(mc, "setblock 11 161 19 minecraft:obsidian");
            }
            case 610 -> core.setEnabled("nuker", true);
            case 618 -> {
                require(mc.interactionManager.isBreakingBlock(), "Long Survival mining retains ordinary progress");
                core.setEnabled("nuker", false);
            }
            case 620 -> {
                require(!mc.interactionManager.isBreakingBlock(), "Disabling mining cancels owned progress");
                core.setEnabled("nuker", true);
            }
            case 628 -> {
                require(mc.interactionManager.isBreakingBlock(), "Mining resumes after enable");
                mc.setScreen(MenuScreens.create(core));
            }
            case 630 -> {
                require(!mc.interactionManager.isBreakingBlock(), "Opening a menu cancels owned mining progress");
                core.setEnabled("nuker", false); mc.setScreen(null);
                command(mc, "item replace entity AnalyzerDev hotbar.5 with minecraft:fishing_rod");
                command(mc, "tp AnalyzerDev 11.5 161 21.5 180 -10");
                mc.player.getInventory().selectedSlot = 0;
                hvhBefore = GameplayEngine.actions("auto-fish");
            }
            case 650 -> core.setEnabled("auto-fish", true);
            case 665 -> {
                require(mc.player.fishHook != null && mc.player.getMainHandStack().isOf(Items.FISHING_ROD), "Auto Fish casts and retains rod while waiting for bite");
                require(GameplayEngine.actions("auto-fish") > hvhBefore, "Auto Fish uses actual rod interaction");
            }
            case 675 -> {
                require(mc.player.fishHook != null && mc.player.getMainHandStack().isOf(Items.FISHING_ROD), "Fishing bobber survives subsequent waiting ticks");
                core.setEnabled("auto-fish", false);
            }
            case 677 -> {
                require(mc.player.getInventory().selectedSlot == 0, "Auto Fish restores previous hotbar slot on disable");
                require(GL11.glGetError() == GL11.GL_NO_ERROR, "Expansion final GL state");
                AnalyzerClient.LOGGER.info("ANALYZER_EXPANSION_COMPLETE: {} modules, {} new screenshots; gameplay={}; visuals={}", core.modules().size(), screenshots, GameplayEngine.diagnostics(), VisualEffects.diagnostics());
            }
        }
        if (t >= 680) CosmeticsSmoke.tick(mc, t - 680);
    }
    private static void place(MinecraftClient mc) {
        mc.player.getInventory().selectedSlot = 0;
        var hit = new BlockHitResult(new Vec3d(11.5, 161, 18.5), Direction.UP, BLOCK.down(), false);
        require(mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit).isAccepted(), "Normal placement interaction accepted");
        mc.player.swingHand(Hand.MAIN_HAND);
    }
    private static LivingEntity nearestHostile(MinecraftClient mc) {
        LivingEntity best = null; double distance = 64;
        for (var e : mc.world.getEntities()) if (e instanceof LivingEntity living && e instanceof Monster && e.isAlive() && mc.player.squaredDistanceTo(e) < distance) { distance = mc.player.squaredDistanceTo(e); best = living; }
        return best;
    }
    private static void command(MinecraftClient mc, String text) { mc.getServer().execute(() -> mc.getServer().getCommandManager().executeWithPrefix(mc.getServer().getCommandSource().withSilent(), text)); }
    private static void type(MinecraftClient mc, String value) { for (char c : value.toCharArray()) mc.currentScreen.charTyped(c, 0); }
    private static void targetModule(String id) { var core = AnalyzerClient.CORE; core.setValue(id, "players", false); core.setValue(id, "hostiles", true); core.setValue(id, "range", 8); core.setValue(id, "fov", 360); }
    private static void control(MinecraftClient mc, String key, int button) {
        double[] point = mc.currentScreen instanceof CatalogScreen screen ? screen.controlPoint(key)
            : mc.currentScreen instanceof HudEditorScreen screen ? screen.controlPoint(key) : null;
        require(point != null, "Control is visible: " + key);
        mc.currentScreen.mouseClicked(point[0], point[1], button); mc.currentScreen.mouseReleased(point[0], point[1], button);
    }
    private static void require(boolean okay, String message) { if (!okay) throw new IllegalStateException("Expansion check failed: " + message); AnalyzerClient.LOGGER.info("Expansion check passed: {}", message); }
    private static void screenshot(MinecraftClient mc, String name) {
        require(GL11.glGetError() == GL11.GL_NO_ERROR, "GL state before " + name);
        ScreenshotRecorder.saveScreenshot(mc.runDirectory, name, mc.getFramebuffer(), text -> AnalyzerClient.LOGGER.info("Expansion screenshot: {}", text.getString())); screenshots++;
    }
}
