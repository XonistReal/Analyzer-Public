package dev.analyzer;

import dev.analyzer.core.AnalyzerCore;
import dev.analyzer.core.Category;
import dev.analyzer.core.ModuleDefinition;
import dev.analyzer.effects.ChamsShader;
import dev.analyzer.render.CosmeticRenderer;
import dev.analyzer.render.EntityChamsRenderer;
import dev.analyzer.render.EspGlow;
import dev.analyzer.ui.CatalogScreen;
import dev.analyzer.ui.MenuScreens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.HuskEntity;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Development-only acceptance for the 0.16.0 beta additions in an isolated world: Fight Bot chasing
 * and critically hitting a husk behind a camera that faces away, Straw Hat draws, Filled/Glow ESP
 * (including both native-outline hooks), the new chams options and the three menu layouts. Each step
 * waits several ticks so slow software rendering has drawn the state before it is checked.
 */
public final class BetaSmoke {
    private static final String WORLD = "Analyzer-Beta-Smoke";
    private static final float CAMERA_YAW = 180;
    private static final double HUSK_HEALTH = 300;
    private static boolean requested, setupRequested, ready, finished;
    private static volatile boolean serverPrepared, observed;
    private static volatile double huskHealth = HUSK_HEALTH, largestHit;
    private static volatile int hits, written;
    private static int ticks, t, stopWait, checks;
    private static long hatFrames;
    private static double panelX;
    private static String movementModule;
    private static final List<String> CAPTURES = new ArrayList<>();
    private BetaSmoke() { }

    public static void tick(MinecraftClient mc) {
        if (!FabricLoader.getInstance().isDevelopmentEnvironment() || !Boolean.getBoolean("analyzer.betaSmoke"))
            throw new IllegalStateException("Beta native fixture requires an isolated development launch");
        if (finished) return;
        if (++ticks > 6000) throw new IllegalStateException("Beta native fixture timed out at step tick " + t);
        if (!requested && mc.world == null && mc.getOverlay() == null && mc.currentScreen != null
                && mc.currentScreen.getClass().getSimpleName().equals("AccessibilityOnboardingScreen")) {
            mc.setScreen(new TitleScreen());
            return;
        }
        if (!requested && mc.getOverlay() == null && mc.currentScreen instanceof TitleScreen) {
            requested = true;
            AnalyzerCore core = AnalyzerClient.CORE;
            core.resetAll();
            for (var module : core.modules()) core.setEnabled(module.id(), false);
            mc.options.pauseOnLostFocus = false;
            mc.options.getViewDistance().setValue(5);
            mc.options.getSimulationDistance().setValue(5);
            mc.options.getGuiScale().setValue(2);
            mc.options.setPerspective(Perspective.FIRST_PERSON);
            mc.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);
            mc.getToastManager().clear();
            GLFW.glfwHideWindow(mc.getWindow().getHandle());
            if (Files.exists(mc.runDirectory.toPath().resolve("saves/" + WORLD + "/level.dat")))
                mc.createIntegratedServerLoader().start(WORLD, () -> mc.scheduleStop());
            else mc.createIntegratedServerLoader().createAndStart(WORLD,
                    new LevelInfo(WORLD, GameMode.SURVIVAL, false, Difficulty.NORMAL, true,
                            new GameRules(FeatureFlags.VANILLA_FEATURES), DataConfiguration.SAFE_MODE),
                    new GeneratorOptions(762831L, false, false), WorldPresets::createDemoOptions, new TitleScreen());
        }
        if (!requested || mc.world == null || mc.player == null || mc.getServer() == null || mc.getOverlay() != null) return;
        if (!setupRequested) { setupRequested = true; setup(mc); return; }
        if (!ready) {
            if (!serverPrepared || mc.player.getBlockY() != 161 || husk(mc) == null) return;
            ready = true; mc.setScreen(null);
            mc.inGameHud.getChatHud().clear(false);
            mc.player.getInventory().selectedSlot = 0;
            mc.player.setYaw(CAMERA_YAW); mc.player.setPitch(10);
        }
        track(mc);
        AnalyzerCore core = AnalyzerClient.CORE;
        if (t >= 20 && t <= 330 && (t % 10 == 0 || t >= 40 && t <= 110)) trace(mc);
        switch (t++) {
            case 20 -> {
                // The target is ten blocks behind the camera; Fight Bot must walk there itself.
                core.setValue("fight-bot", "players", false);
                core.setValue("fight-bot", "hostiles", true);
                core.setEnabled("fight-bot", true);
                require(core.getChoice("fight-bot", "rotation").equals("Silent movement"), "Fight Bot defaults to silent movement");
            }
            case 330 -> {
                Entity target = husk(mc);
                require(target != null && mc.player.distanceTo(target) < 5, "Fight Bot chased the husk behind the camera: distance "
                        + (target == null ? "none" : String.format("%.2f", mc.player.distanceTo(target))));
                require(hits >= 2 && huskHealth <= HUSK_HEALTH - 12, "Fight Bot landed server-side hits: " + hits + " hits, health " + huskHealth);
                require(largestHit > 8.5, "At least one hit was a falling critical hit (largest " + String.format("%.2f", largestHit) + " vs 6.9 plain)");
                require(Math.abs(MathHelper.wrapDegrees(mc.player.getYaw() - CAMERA_YAW)) < 0.01f,
                        "The real camera yaw never moved while fighting: " + mc.player.getYaw());
                capture(mc, "analyzer-beta-fight-bot.png");
                core.setEnabled("fight-bot", false);
            }
            case 350 -> {
                Entity target = husk(mc);
                require(target != null, "The husk survived for the visual checks");
                lookAt(mc, target);
                mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
                core.setEnabled("straw-hat", true);
                hatFrames = CosmeticRenderer.frames("straw-hat");
            }
            case 390 -> {
                require(CosmeticRenderer.frames("straw-hat") > hatFrames + 10, "Straw Hat draws on the posed local player");
                capture(mc, "analyzer-beta-straw-hat.png");
                core.setValue("straw-hat", "style", "Glass");
                hatFrames = CosmeticRenderer.frames("straw-hat");
            }
            case 420 -> {
                require(CosmeticRenderer.frames("straw-hat") > hatFrames + 10, "Glass Straw Hat draws");
                capture(mc, "analyzer-beta-straw-hat-glass.png");
                core.setEnabled("straw-hat", false);
                mc.options.setPerspective(Perspective.FIRST_PERSON);
                core.setEnabled("entity-esp", true);
                core.setValue("entity-esp", "hostiles", true);
                core.setValue("entity-esp", "style", "Filled");
            }
            case 450 -> {
                require(GL11.glGetError() == GL11.GL_NO_ERROR, "Filled ESP leaves clean GL state");
                capture(mc, "analyzer-beta-esp-filled.png");
                core.setValue("entity-esp", "style", "Glow");
            }
            case 480 -> {
                Entity target = husk(mc);
                int color = EspGlow.color(target);
                require(color != -1, "Glow ESP accepts the husk");
                require(mc.hasOutline(target), "Glow ESP hooks Minecraft's outline decision");
                require((target.getTeamColorValue() & 0xFFFFFF) == color, "Glow ESP supplies the ESP color to the native outline");
                capture(mc, "analyzer-beta-esp-glow.png");
                core.setEnabled("entity-esp", false);
                require(!mc.hasOutline(target), "Disabling Glow ESP restores the native outline decision");
                core.setEnabled("chams", true);
                core.setValue("chams", "hostiles", true);
                core.setValue("chams", "style", "Hologram");
                core.setValue("chams", "hidden-style", "Rim light");
                core.setValue("chams", "edges", true);
            }
            case 510 -> {
                var counts = EntityChamsRenderer.counts();
                require(counts.shaderDraws() >= 1 && ChamsShader.isHealthy(), "Hologram chams with a Rim light occluded material and edges draw: " + counts);
                capture(mc, "analyzer-beta-chams-hologram.png");
                core.setValue("chams", "style", "Galaxy");
            }
            case 540 -> {
                require(EntityChamsRenderer.counts().shaderDraws() >= 1 && ChamsShader.isHealthy(), "Galaxy chams draw");
                capture(mc, "analyzer-beta-chams-galaxy.png");
                core.setEnabled("chams", false);
                core.setValue("menu", "layout", "Click GUI");
                mc.setScreen(MenuScreens.create(core));
            }
            case 570 -> {
                require(screen(mc).equals("ClickGuiScreen"), "Click GUI layout opens the floating panels: " + screen(mc));
                movementModule = core.modules().stream().filter(m -> m.category() == Category.MOVEMENT)
                        .map(ModuleDefinition::id).filter(id -> point(mc, "module:" + id) != null).findFirst().orElse(null);
                require(movementModule != null, "The Movement panel renders module rows");
                require(!core.isEnabled(movementModule), movementModule + " starts disabled");
                click(mc, "module:" + movementModule, 0);
            }
            case 590 -> {
                require(core.isEnabled(movementModule), "Left-click toggles " + movementModule + " on");
                click(mc, "module:" + movementModule, 0);
            }
            case 610 -> {
                require(!core.isEnabled(movementModule), "A second left-click toggles it off");
                click(mc, "inspect:" + movementModule, 1);
            }
            case 630 -> {
                require(point(mc, "flyout-close") != null && point(mc, "selected-toggle") != null, "Right-click opens the settings flyout");
                capture(mc, "analyzer-beta-click-gui.png");
                mc.currentScreen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
            }
            case 650 -> {
                require(screen(mc).equals("ClickGuiScreen") && point(mc, "flyout-close") == null, "Escape closes only the flyout");
                double[] header = point(mc, "header:MOVEMENT");
                require(header != null, "The Movement panel header renders");
                panelX = core.getNumber("menu", "click-movement-x");
                var screen = mc.currentScreen;
                screen.mouseClicked(header[0], header[1], 0);
                screen.mouseDragged(header[0] + 60, header[1] + 30, 0, 60, 30);
                screen.mouseDragged(header[0] + 120, header[1] + 60, 0, 60, 30);
                screen.mouseReleased(header[0] + 120, header[1] + 60, 0);
            }
            case 670 -> {
                require(core.getNumber("menu", "click-movement-x") != panelX, "Dragging a header saves the panel position: "
                        + panelX + " -> " + core.getNumber("menu", "click-movement-x"));
                require(GL11.glGetError() == GL11.GL_NO_ERROR, "Click GUI leaves clean GL state");
                capture(mc, "analyzer-beta-click-gui-dragged.png");
                core.setValue("menu", "layout", "Compact");
                mc.setScreen(MenuScreens.create(core));
            }
            case 700 -> {
                require(screen(mc).equals("CompactScreen") && point(mc, "search") != null, "Compact layout opens the shader board");
                capture(mc, "analyzer-beta-compact.png");
                core.setValue("menu", "layout", "Studio");
                mc.setScreen(MenuScreens.create(core));
            }
            case 730 -> {
                require(screen(mc).equals("StudioScreen") && point(mc, "search") != null, "Studio layout still opens");
                mc.setScreen(null);
            }
            default -> { }
        }
        if (t > 740) {
            if (written != CAPTURES.size() || !allFilesExist(mc)) {
                if (++stopWait > 200) throw new IllegalStateException("Native screenshot callback/write did not finish");
                return;
            }
            finished = true;
            AnalyzerClient.LOGGER.info("ANALYZER_BETA_COMPLETE: {} checks; {} captures; Fight Bot {} hits, largest {}, husk health {}; free camera, chase, criticals; Straw Hat straw/glass; Filled and Glow ESP with native outline hooks; Hologram/Galaxy chams with occluded material and edges; Click GUI toggle/flyout/drag; Compact; Studio",
                    checks, CAPTURES.size(), hits, String.format("%.2f", largestHit), huskHealth);
            mc.scheduleStop();
        }
    }

    /** Server-side health of the target, so hits and critical damage are measured where they count. */
    private static void track(MinecraftClient mc) {
        mc.getServer().execute(() -> {
            var husks = mc.getServer().getOverworld().getEntitiesByType(EntityType.HUSK, entity -> entity.getCommandTags().contains("beta_target"));
            if (husks.isEmpty()) return;
            double health = husks.getFirst().getHealth();
            if (!observed) { observed = true; huskHealth = health; return; }
            if (health < huskHealth - 0.01) { largestHit = Math.max(largestHit, huskHealth - health); hits++; }
            huskHealth = health;
        });
    }
    private static void trace(MinecraftClient mc) {
        HuskEntity target = husk(mc);
        var p = mc.player;
        AnalyzerClient.LOGGER.info("Beta fight trace t={} status=[{}] eyeToHitbox={} charge={} ground={} vy={} fall={} sprint={} hurt={} hits={} health={}",
                t, dev.analyzer.gameplay.GameplayEngine.status("fight-bot"),
                target == null ? "none" : String.format("%.2f", Math.sqrt(target.getBoundingBox().squaredMagnitude(p.getEyePos()))),
                String.format("%.2f", p.getAttackCooldownProgress(0)), p.isOnGround(), String.format("%.3f", p.getVelocity().y), String.format("%.3f", p.fallDistance),
                p.isSprinting(), target == null ? -1 : target.hurtTime, hits, huskHealth);
    }
    private static HuskEntity husk(MinecraftClient mc) {
        for (Entity entity : mc.world.getEntities()) if (entity instanceof HuskEntity husk && husk.isAlive()) return husk;
        return null;
    }
    private static void lookAt(MinecraftClient mc, Entity target) {
        double dx = target.getX() - mc.player.getX(), dz = target.getZ() - mc.player.getZ();
        mc.player.setYaw((float) Math.toDegrees(Math.atan2(-dx, dz)));
        mc.player.setPitch(15);
    }
    private static String screen(MinecraftClient mc) { return mc.currentScreen == null ? "none" : mc.currentScreen.getClass().getSimpleName(); }
    private static double[] point(MinecraftClient mc, String id) {
        return mc.currentScreen instanceof CatalogScreen screen ? screen.controlPoint(id) : null;
    }
    private static void click(MinecraftClient mc, String id, int button) {
        double[] p = point(mc, id);
        require(p != null, "Rendered control " + id);
        mc.currentScreen.mouseClicked(p[0], p[1], button);
        mc.currentScreen.mouseReleased(p[0], p[1], button);
    }
    private static void setup(MinecraftClient mc) {
        mc.getServer().execute(() -> {
            var server = mc.getServer();
            for (int x = -1; x <= 2; x++) for (int z = -1; z <= 2; z++) server.getOverworld().getChunk(x, z);
            String[] commands = {
                    "difficulty normal", "gamemode survival AnalyzerDev", "clear AnalyzerDev", "effect clear AnalyzerDev",
                    "effect give AnalyzerDev minecraft:resistance infinite 4 true",
                    "effect give AnalyzerDev minecraft:saturation infinite 0 true",
                    "gamerule doMobSpawning false", "gamerule doDaylightCycle false", "gamerule doWeatherCycle false",
                    "gamerule doMobLoot false", "gamerule randomTickSpeed 0", "time set 1000", "weather clear",
                    "kill @e[type=!minecraft:player]",
                    "fill 0 160 0 24 160 25 minecraft:polished_deepslate", "fill 0 161 0 24 171 25 minecraft:air",
                    "fill 0 160 0 24 160 0 minecraft:sea_lantern", "fill 0 160 25 24 160 25 minecraft:sea_lantern",
                    "item replace entity AnalyzerDev hotbar.0 with minecraft:diamond_sword",
                    "summon minecraft:husk 12.5 161 14.5 {NoAI:1b,PersistenceRequired:1b,Silent:1b,Tags:[\"beta_target\"],"
                            + "attributes:[{id:\"minecraft:max_health\",base:300d}],Health:300f}",
                    "data merge entity @e[type=minecraft:husk,tag=beta_target,limit=1] {Health:300f}",
                    "tp AnalyzerDev 12.5 161 4.5 180 10"
            };
            var source = server.getCommandSource().withSilent();
            for (String text : commands) server.getCommandManager().executeWithPrefix(source, text);
            serverPrepared = true;
            AnalyzerClient.LOGGER.info("ANALYZER_BETA_ARENA_READY");
        });
    }
    private static void capture(MinecraftClient mc, String name) {
        require(GL11.glGetError() == GL11.GL_NO_ERROR, "Clean GL state before " + name);
        CAPTURES.add(name);
        ScreenshotRecorder.saveScreenshot(mc.runDirectory, name, mc.getFramebuffer(), text -> written++);
    }
    private static boolean allFilesExist(MinecraftClient mc) {
        try {
            for (String name : CAPTURES) {
                var path = mc.runDirectory.toPath().resolve("screenshots").resolve(name);
                if (!Files.isRegularFile(path) || Files.size(path) < 1024) return false;
            }
            return true;
        } catch (java.io.IOException failure) { throw new IllegalStateException("Cannot verify native photo files", failure); }
    }
    private static void require(boolean condition, String text) {
        if (!condition) throw new IllegalStateException("Beta native fixture: " + text);
        checks++;
        AnalyzerClient.LOGGER.info("Beta native check: {}", text);
    }
}
