package dev.analyzer;

import dev.analyzer.automation.AutomationEngine;
import dev.analyzer.core.Category;
import dev.analyzer.gameplay.GameplayEngine;
import dev.analyzer.gameplay.GameplayCombatSmoke;
import dev.analyzer.ui.AnalyzerScreen;
import dev.analyzer.ui.ClassicScreen;
import dev.analyzer.ui.CompactScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

/** Bounded integration checks inside DevSmoke's isolated Analyzer-Smoke world, after the gameplay tick. */
final class GameplaySettingsSmoke {
    private static int stage, entered, tick, assertions, screenshots, attempts;
    private static long motionBefore, actionsBefore;
    private static double stepOriginal;
    private static Vec3d start;
    private static Screen editorParent;
    private static double[] scrollAnchor;
    private static boolean complete;
    private GameplaySettingsSmoke() { }

    static void tick(MinecraftClient mc, int t) {
        if (complete) return;
        tick = t;
        if (t > 480) throw new IllegalStateException("Gameplay settings fixture timed out at stage " + stage);
        int age = t - entered;
        var core = AnalyzerClient.CORE;
        switch (stage) {
            case 0 -> {
                AutomationEngine.INSTANCE.stop(); core.resetAll(); mc.setScreen(null);
                core.setValue("menu", "motion", 0D);
                command(mc, "kill @e[type=!minecraft:player]");
                command(mc, "fill 10 160 10 22 160 22 minecraft:polished_deepslate");
                command(mc, "fill 10 161 10 22 169 22 minecraft:air");
                command(mc, "gamemode survival AnalyzerDev"); command(mc, "tp AnalyzerDev 15.5 161 15.5 0 0");
                command(mc, "effect give AnalyzerDev minecraft:saturation 30 5 true");
                next();
            }
            case 1 -> { if (age >= 8) {
                if (!mc.player.isOnGround() || mc.player.getAbilities().creativeMode || mc.player.getPos().squaredDistanceTo(15.5, 161, 15.5) > 4) {
                    if (age > 80) throw new IllegalStateException("Survival fixture did not synchronize: " + mc.player.getPos());
                    return;
                }
                require(mc.player.isOnGround() && !mc.player.getAbilities().creativeMode, "Motion checks start on loaded ground in actual Survival");
                stepOriginal = mc.player.getAttributeInstance(EntityAttributes.STEP_HEIGHT).getBaseValue();
                motionBefore = GameplayEngine.diagnostics().movementChanges(); actionsBefore = GameplayEngine.actions("speed");
                core.setValue("speed", "profile", "Sprint hop"); core.setValue("speed", "speed", .8); core.setValue("speed", "multiplier", 3D);
                core.setEnabled("auto-walk", true); core.setEnabled("speed", true); start = mc.player.getPos(); next();
            }}
            case 2 -> { if (age >= 7) {
                require(GameplayEngine.actions("speed") > actionsBefore && GameplayEngine.status("speed").contains("Normal sprint/jump"), "Sprint hop dispatches normal sprint/jump inputs");
                require(GameplayEngine.diagnostics().movementChanges() == motionBefore, "Sprint hop makes no direct velocity changes despite a large configured multiplier");
                require(mc.player.getY() > start.y + .2 && mc.player.getPos().distanceTo(start) > .3, "Normal hop inputs move and jump the actual player");
                core.setEnabled("speed", false); core.setEnabled("auto-walk", false); command(mc, "tp AnalyzerDev 15.5 161 15.5 0 0");
                core.setValue("speed", "profile", "Standard"); core.setValue("speed", "speed", .5); core.setValue("speed", "multiplier", 1.2);
                core.setEnabled("auto-walk", true); core.setEnabled("speed", true); next();
            }}
            case 3 -> { if (age >= 7) {
                near(horizontal(mc), .6, "Standard profile applies the actual base-speed multiplier");
                require(mc.player.getZ() > 17, "Direct Standard motion moves the actual player along the fixture floor");
                core.setValue("speed", "profile", "Conservative"); core.setValue("speed", "speed", .8); core.setValue("speed", "multiplier", 4D); next();
            }}
            case 4 -> { if (age >= 4) {
                require(mc.player.isOnGround(), "Conservative ground fixture remains supported");
                near(horizontal(mc), .45, "Conservative grounded motion caps an excessive speed request");
                command(mc, "tp AnalyzerDev 15.5 164 15.5 0 0"); next();
            }}
            case 5 -> {
                if (age == 3) { require(!mc.player.isOnGround(), "Conservative airborne fixture is actually falling"); motionBefore = GameplayEngine.diagnostics().movementChanges(); }
                if (age >= 6) {
                    require(!mc.player.isOnGround() && GameplayEngine.diagnostics().movementChanges() == motionBefore, "Conservative profile leaves airborne velocity to vanilla for consecutive actual ticks");
                    require(GameplayEngine.status("speed").contains("airborne"), "Conservative waiting state is observable");
                    core.setEnabled("speed", false); core.setEnabled("auto-walk", false); command(mc, "tp AnalyzerDev 15.5 161 15.5 0 0");
                    core.setValue("step", "height", 1.5); core.setEnabled("step", true); next();
                }
            }
            case 6 -> { if (age >= 5) {
                near(mc.player.getAttributeInstance(EntityAttributes.STEP_HEIGHT).getBaseValue(), 1.5, "Step owns the actual client step-height attribute");
                mc.setScreen(new InventoryScreen(mc.player)); next();
            }}
            case 7 -> { if (age >= 3) {
                near(mc.player.getAttributeInstance(EntityAttributes.STEP_HEIGHT).getBaseValue(), stepOriginal, "Opening a manual menu restores owned Step height");
                require(!mc.options.forwardKey.isPressed() && !mc.options.jumpKey.isPressed(), "Menu pause releases previous movement inputs");
                core.setEnabled("step", false); mc.setScreen(null); command(mc, "tp AnalyzerDev 15.5 164 15.5 0 0");
                core.setValue("fly", "mode", "Glide"); core.setValue("fly", "vertical-speed", .6); core.setEnabled("fly", true);
                mc.options.jumpKey.setPressed(true); mc.player.setVelocity(0, .4, 0); next();
            }}
            case 8 -> { if (age >= 5) {
                require(!mc.player.isOnGround() && mc.player.getVelocity().y < 0, "Fly Glide never sustains upward motion while jump is held");
                core.setEnabled("fly", false); mc.options.jumpKey.setPressed(false);
                core.setEnabled("safe-walk", true); core.setValue("safe-walk", "only-sneaking", true); mc.options.sneakKey.setPressed(false);
                require(!GameplayEngine.safeWalkActive(), "Safe Walk sneak-only condition excludes unheld sneak");
                mc.options.sneakKey.setPressed(true); require(GameplayEngine.safeWalkActive(), "Safe Walk actual mixin gate accepts held sneak");
                core.setValue("safe-walk", "legit", true); require(!GameplayEngine.safeWalkActive(), "Legit Safe Walk uses normal input instead of forcing the mixin gate");
                core.setEnabled("safe-walk", false); mc.options.sneakKey.setPressed(false); command(mc, "tp AnalyzerDev 15.5 161 15.5 180 12");
                core.setValue("fly", "mode", "Vanilla"); core.setValue("fly", "anti-hover", true);
                editorParent = new AnalyzerScreen(core).inspect("fly"); mc.setScreen(editorParent); scrollAnchor = null; attempts = 0; next();
            }}
            case 9 -> { if (age >= 2 && openNumber(mc, "fly", "vertical-speed")) next(); }
            case 10 -> { if (age >= 1) { applyNumber(mc, "0.47"); near(core.getNumber("fly", "vertical-speed"), .47, "Workspace right-click and Enter update the real numeric value"); next(); }}
            case 11 -> {
                if (age == 2) scroll(mc, -25);
                if (age == 4) {
                require(GL11.glGetError() == GL11.GL_NO_ERROR, "Workspace grouped inspector scroll has clean GL state");
                require(point(editorParent, "setting:fly:pulse-interval") != null, "Workspace grouped scrolling renders its active Timing control");
                editorParent = new AnalyzerScreen(core).inspect("kill-aura"); mc.setScreen(editorParent); scrollAnchor = null;
                }
                if (age == 6) {
                    scrollAnchor = point(editorParent, "setting:kill-aura:players");
                    require(scrollAnchor != null && core.definition("kill-aura").settings().size() >= 30, "Long Combat inspector renders the actual targeting group");
                    scroll(mc, -100);
                }
                if (age >= 8) {
                require(point(editorParent, "setting:kill-aura:pause-using") != null, "Long Combat inspector scrolling renders the last Safety group");
                capture(mc, "analyzer-gameplay-settings-workspace.png");
                editorParent = new CompactScreen(core); mc.setScreen(editorParent); search(editorParent, "Client flight or descending glide"); next();
            }}
            case 12 -> { if (age >= 2) { click(mc, "inspect:fly", 1); scrollAnchor = null; attempts = 0; next(); }}
            case 13 -> { if (age >= 2 && openNumber(mc, "fly", "vertical-speed")) next(); }
            case 14 -> { if (age >= 1) { applyNumber(mc, "0.48"); near(core.getNumber("fly", "vertical-speed"), .48, "Compact right-click and Enter update the real numeric value"); next(); }}
            case 15 -> { if (age == 2) scroll(mc, -25); if (age >= 4) {
                require(GL11.glGetError() == GL11.GL_NO_ERROR, "Compact grouped inspector scroll has clean GL state");
                require(point(editorParent, "number:fly:pulse-interval") != null, "Compact grouped scrolling renders its active Timing control");
                capture(mc, "analyzer-gameplay-settings-compact.png");
                for (Category category : Category.values()) {
                    String prefix = "classic-" + category.name().toLowerCase(java.util.Locale.ROOT);
                    core.setValue("menu", prefix + "-collapsed", category != Category.MOVEMENT);
                    core.setValue("menu", prefix + "-x", category == Category.MOVEMENT ? 50D : 0D);
                    core.setValue("menu", prefix + "-y", category == Category.MOVEMENT ? 65D : 0D);
                }
                editorParent = new ClassicScreen(core); mc.setScreen(editorParent); search(editorParent, "Client flight or descending glide"); next();
            }}
            case 16 -> { if (age >= 2) { click(mc, "inspect:fly", 1); scrollAnchor = null; attempts = 0; next(); }}
            case 17 -> { if (age >= 2 && openNumber(mc, "fly", "vertical-speed")) next(); }
            case 18 -> { if (age >= 1) { applyNumber(mc, "0.49"); near(core.getNumber("fly", "vertical-speed"), .49, "Classic right-click and Enter update the real numeric value"); next(); }}
            case 19 -> { if (age >= 2) { scroll(mc, -25); next(); }}
            case 20 -> { if (age >= 2) {
                capture(mc, "analyzer-gameplay-settings-classic.png");
                require(point(editorParent, "number:fly:pulse-interval") != null, "Classic scrolling exposes its active Timing group's real numeric control");
                mc.setScreen(null); mc.options.forwardKey.setPressed(false); mc.options.jumpKey.setPressed(false); mc.options.sneakKey.setPressed(false); mc.options.sprintKey.setPressed(false);
                core.resetAll();
                try { core.save(); } catch (java.io.IOException failure) { throw new IllegalStateException("Cannot restore isolated smoke config", failure); }
                next();
            }}
            case 21 -> { if (age >= 2) {
                if (!GameplayCombatSmoke.tick(mc, age - 2)) return;
                require(GL11.glGetError() == GL11.GL_NO_ERROR, "Gameplay settings final GL state");
                core.resetAll();
                try { core.save(); } catch (java.io.IOException failure) { throw new IllegalStateException("Cannot restore isolated smoke config", failure); }
                require(!core.isEnabled("speed") && !core.isEnabled("fly") && !core.isEnabled("step"), "Test action modules are reset after the fixture");
                complete = true;
                AnalyzerClient.LOGGER.info("ANALYZER_GAMEPLAY_SETTINGS_SMOKE_COMPLETE: {} assertions, {} screenshots, {} ticks; actual movement profiles, Step cleanup, descending flight, Safe Walk gates, and precise numeric entry in all three layouts", assertions, screenshots, t);
                mc.scheduleStop();
            }}
        }
    }
    private static boolean openNumber(MinecraftClient mc, String module, String setting) {
        double[] value = point(editorParent, editorParent instanceof AnalyzerScreen ? "setting:" + module + ":" + setting : "number:" + module + ":" + setting);
        if (scrollAnchor == null) scrollAnchor = point(editorParent, "setting:" + module + ":legit");
        if (value == null) {
            if (++attempts > 8) throw new IllegalStateException("Numeric control never became visible in " + editorParent.getClass().getSimpleName());
            scroll(mc, -2); return false;
        }
        scrollAnchor = value;
        editorParent.mouseClicked(value[0], value[1], GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        require(mc.currentScreen != editorParent && mc.currentScreen.getClass().getSimpleName().equals("NumericSettingScreen"), "Actual rendered numeric control opens precise entry in " + editorParent.getClass().getSimpleName());
        return true;
    }
    private static void applyNumber(MinecraftClient mc, String value) {
        Screen numeric = mc.currentScreen;
        numeric.keyPressed(GLFW.GLFW_KEY_BACKSPACE, 0, 0);
        for (char c : value.toCharArray()) numeric.charTyped(c, 0);
        numeric.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
        require(mc.currentScreen == editorParent, "Enter commits the numeric editor and returns to its actual parent");
    }
    private static void search(Screen screen, String text) {
        screen.keyPressed(GLFW.GLFW_KEY_SLASH, 0, 0);
        for (char c : text.toCharArray()) screen.charTyped(c, 0);
        screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
    }
    private static double[] point(Screen screen, String id) {
        if (screen instanceof AnalyzerScreen workspace) return workspace.controlPoint(id);
        if (screen instanceof CompactScreen compact) return compact.controlPoint(id);
        if (screen instanceof ClassicScreen classic) return classic.controlPoint(id);
        return null;
    }
    private static void click(MinecraftClient mc, String id, int button) {
        double[] value = point(mc.currentScreen, id); require(value != null, "Rendered control exists: " + id);
        mc.currentScreen.mouseClicked(value[0], value[1], button); mc.currentScreen.mouseReleased(value[0], value[1], button);
    }
    private static void scroll(MinecraftClient mc, double amount) {
        if (scrollAnchor == null) throw new IllegalStateException("No real inspector scroll anchor");
        editorParent.mouseScrolled(scrollAnchor[0], scrollAnchor[1], 0, amount);
    }
    private static double horizontal(MinecraftClient mc) { return Math.hypot(mc.player.getVelocity().x, mc.player.getVelocity().z); }
    private static void command(MinecraftClient mc, String text) { mc.getServer().execute(() -> mc.getServer().getCommandManager().executeWithPrefix(mc.getServer().getCommandSource().withSilent(), text)); }
    private static void next() { stage++; entered = tick; }
    private static void near(double actual, double expected, String label) { require(Math.abs(actual - expected) < .00001, label + " (actual " + actual + ")"); }
    private static void require(boolean condition, String label) { if (!condition) throw new IllegalStateException("Gameplay settings smoke stage " + stage + ": " + label); assertions++; AnalyzerClient.LOGGER.info("Gameplay settings check passed: {}", label); }
    private static void capture(MinecraftClient mc, String name) {
        require(GL11.glGetError() == GL11.GL_NO_ERROR, "GL state before " + name);
        mc.inGameHud.getChatHud().clear(false); mc.getToastManager().clear();
        ScreenshotRecorder.saveScreenshot(mc.runDirectory, name, mc.getFramebuffer(), message -> AnalyzerClient.LOGGER.info("Gameplay settings screenshot: {}", message.getString())); screenshots++;
    }
}
