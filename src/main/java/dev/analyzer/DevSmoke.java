package dev.analyzer;

import dev.analyzer.ui.AnalyzerScreen;
import dev.analyzer.render.AnalyzerRenderer;
import dev.analyzer.effects.GpuEffects;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.option.GraphicsMode;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import java.util.ArrayList;
import java.util.List;

/** Opt-in development smoke run in run/, never runs in an installed release. */
final class DevSmoke {
    private static int ticks, worldTicks;
    private static boolean started, worldRequested, arenaReady;
    private static long skyFramesAtDay;
    private static final List<String> captures = new ArrayList<>();
    static void tick(MinecraftClient client) {
        if (!started && client.isFinishedLoading() && client.getOverlay() == null && client.currentScreen instanceof TitleScreen) {
            started = true; AnalyzerClient.CORE.resetAll();
            client.options.pauseOnLostFocus = false;
            client.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);
            client.getToastManager().clear();
            GLFW.glfwHideWindow(client.getWindow().getHandle());
            client.setScreen(new AnalyzerScreen(AnalyzerClient.CORE));
        }
        if (!started) return;
        ticks++;
        var core = AnalyzerClient.CORE;
        if (!worldRequested) {
            switch (ticks) {
                case 36 -> click(client, 80, 134);
                case 40 -> screenshot(client, "analyzer-ui.png");
                case 44 -> click(client, 352, 295);
                case 46 -> require(core.isEnabled("entity-esp"), "Module toggle click");
                case 47 -> click(client, 800, 451);
                case 48 -> client.currentScreen.keyPressed(GLFW.GLFW_KEY_G, 0, 0);
                case 49 -> require(core.keybind("entity-esp") == GLFW.GLFW_KEY_G, "Module keybind capture");
                case 50 -> { click(client, 700, 245); require(!core.getBoolean("entity-esp", "players"), "Boolean setting click"); }
                case 54 -> { client.currentScreen.keyPressed(GLFW.GLFW_KEY_SLASH, 0, 0); type(client, "Atmosphere"); }
                case 57 -> click(client, 247, 320);
                case 60 -> click(client, 851, 190);
                case 63 -> { require(core.isEnabled("shader"), "Search and inspector toggle"); click(client, 700, 264); }
                case 66 -> require(core.getChoice("shader", "preset").equals("Ember"), "Choice setting click");
                case 67 -> click(client, 874, 320);
                case 68 -> require(core.getNumber("shader", "strength") == 1.0, "Slider maximum");
                case 69 -> click(client, 776, 320);
                case 70 -> require(core.getNumber("shader", "strength") == 0.6, "Slider precision");
                case 72 -> screenshot(client, "analyzer-atmosphere.png");
                case 76 -> click(client, 80, 367);
                case 80 -> { click(client, 315, 217); type(client, "smoke-profile"); }
                case 84 -> click(client, 531, 217);
                case 86 -> {
                    try { require(core.profiles().contains("smoke-profile"), "Profile save through UI"); }
                    catch (Exception ex) { throw new IllegalStateException(ex); }
                }
                case 92 -> screenshot(client, "analyzer-profiles.png");
                case 96 -> {
                    AnalyzerClient.LOGGER.info("ANALYZER_SMOKE_UI_INTERACTIONS_COMPLETE");
                    if (!Boolean.getBoolean("analyzer.worldsmoke")) client.scheduleStop();
                    else {
                        worldRequested = true;
                        client.options.getViewDistance().setValue(5);
                        client.options.getSimulationDistance().setValue(5);
                        var menu = new TitleScreen();
                        String name = "Analyzer-Smoke";
                        if (java.nio.file.Files.exists(client.runDirectory.toPath().resolve("saves/" + name + "/level.dat")))
                            client.createIntegratedServerLoader().start(name, () -> client.scheduleStop());
                        else client.createIntegratedServerLoader().createAndStart(name,
                            new LevelInfo("Analyzer Smoke", GameMode.CREATIVE, false, Difficulty.NORMAL, true, new GameRules(FeatureFlags.VANILLA_FEATURES), DataConfiguration.SAFE_MODE),
                            new GeneratorOptions(762831L, false, false), WorldPresets::createDemoOptions, menu);
                    }
                }
            }
        } else if (client.world != null && client.player != null && client.getServer() != null && client.getOverlay() == null) {
            if (!arenaReady) { arenaReady = true; setupArena(client); }
            worldTicks++;
            switch (worldTicks) {
                case 40 -> {
                    client.setScreen(null);
                    client.inGameHud.getChatHud().clear(false);
                    core.setValue("entity-esp", "players", true); core.setValue("entity-esp", "items", true);
                    core.setEnabled("entity-esp", true); core.setValue("entity-esp", "passives", true); core.setValue("entity-esp", "hostiles", true);
                    core.setEnabled("storage-esp", true); core.setEnabled("tracers", true); core.setValue("tracers", "hostiles", true); core.setValue("tracers", "passives", true);
                    core.setValue("tracers", "items", true);
                    core.setEnabled("shader", true); core.setValue("shader", "preset", "Aurora"); core.setEnabled("crosshair", true);
                    core.setEnabled("fullbright", true);
                }
                case 45 -> {
                    var scout = new OtherClientPlayerEntity(client.world, new GameProfile(java.util.UUID.fromString("aba4f8ec-6206-45d5-a578-0fa2c7a923bd"), "Scout"));
                    scout.setId(991382);
                    scout.refreshPositionAndAngles(7, 161, 16, 0, 0);
                    scout.setHealth(14);
                    client.world.addEntity(scout);
                }
                case 90 -> {
                    var counts = AnalyzerRenderer.counts();
                    require(counts.entities() >= 6, "Entity ESP rendered targets: " + counts.entities());
                    require(counts.containers() >= 6, "Storage ESP rendered targets: " + counts.containers());
                    require(counts.tracers() >= 6, "Tracer rendered targets: " + counts.tracers());
                    require(client.world.getEntityById(991382) instanceof OtherClientPlayerEntity, "Local remote-player fixture present");
                    require(GpuEffects.skyFrames() > 0, "Real sky pass rendered");
                    require(GpuEffects.worldFrames() > 0 && GpuEffects.isHealthy(), "World post effects rendered");
                    screenshot(client, "analyzer-world-aurora.png");
                }
                case 95 -> { core.setValue("shader", "preset", "Ember"); core.setValue("sky", "preset", "Ember"); core.setValue("entity-esp", "style", "Boxes"); }
                case 115 -> screenshot(client, "analyzer-world-ember.png");
                case 120 -> { core.setValue("shader", "preset", "Frost"); core.setValue("entity-esp", "through-walls", false); core.setValue("storage-esp", "through-walls", false); client.options.getGraphicsMode().setValue(GraphicsMode.FABULOUS); client.worldRenderer.reload(); }
                case 140 -> screenshot(client, "analyzer-world-frost.png");
                case 145 -> { core.setEnabled("fullbright", false); core.setEnabled("shader", false); }
                case 160 -> screenshot(client, "analyzer-world-vanilla.png");
                case 165 -> { core.setEnabled("shader", true); client.setScreen(new AnalyzerScreen(core)); client.reloadResources(); }
                case 175 -> GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 1280, 800);
                case 225 -> { if (client.getOverlay() == null) screenshot(client, "analyzer-world-menu.png"); }
                case 240 -> client.setScreen(null);
                case 245 -> screenshot(client, "analyzer-world-resized.png");
                case 250 -> {
                    loadProfile("showcase");
                    client.options.getGraphicsMode().setValue(GraphicsMode.FAST); client.worldRenderer.reload();
                }
                case 270 -> {
                    var counts = AnalyzerRenderer.screenCounts();
                    require(counts.entities() >= 6, "Screen ESP projected entities under Fast: " + counts.entities());
                    require(counts.containers() >= 6, "Screen ESP projected containers under Fast: " + counts.containers());
                    screenshot(client, "analyzer-screen-fast.png");
                }
                case 275 -> { client.options.getGraphicsMode().setValue(GraphicsMode.FANCY); client.worldRenderer.reload(); }
                case 295 -> {
                    require(AnalyzerRenderer.screenCounts().entities() >= 6, "Screen ESP under Fancy");
                    screenshot(client, "analyzer-screen-fancy.png");
                }
                case 300 -> { client.options.getGraphicsMode().setValue(GraphicsMode.FABULOUS); client.worldRenderer.reload(); }
                case 320 -> {
                    require(AnalyzerRenderer.screenCounts().entities() >= 6, "Screen ESP under Fabulous");
                    screenshot(client, "analyzer-screen-fabulous.png");
                }
                case 325 -> {
                    core.setEnabled("entity-esp", false); core.setEnabled("storage-esp", false); core.setEnabled("tracers", true);
                    core.setValue("tracers", "players", true); core.setValue("tracers", "hostiles", false); core.setValue("tracers", "passives", false); core.setValue("tracers", "items", false);
                    client.world.removeEntity(991382, net.minecraft.entity.Entity.RemovalReason.DISCARDED);
                }
                case 345 -> {
                    require(AnalyzerRenderer.status("tracers").contains("0 other players"), "Singleplayer players-only filter is explained");
                    core.setEnabled("tracers", false); core.setEnabled("shader", false); core.setValue("sky", "preset", "Aurora");
                    command(client, "tp AnalyzerDev 11 161 21 180 -24");
                }
                case 370 -> screenshot(client, "analyzer-sky-aurora.png");
                case 375 -> core.setValue("sky", "preset", "Nebula");
                case 395 -> screenshot(client, "analyzer-sky-nebula.png");
                case 400 -> core.setValue("sky", "preset", "Ember");
                case 420 -> screenshot(client, "analyzer-sky-ember.png");
                case 425 -> core.setEnabled("sky", false);
                case 445 -> screenshot(client, "analyzer-sky-off.png");
                case 450 -> {
                    core.setEnabled("sky", true); core.setValue("sky", "preset", "Aurora"); core.setValue("sky", "day-night", "Night");
                    command(client, "time set 6000");
                }
                case 470 -> skyFramesAtDay = GpuEffects.skyFrames();
                case 490 -> {
                    require(GpuEffects.skyFrames() == skyFramesAtDay, "Night-only sky is inactive at noon");
                    screenshot(client, "analyzer-sky-night-at-noon.png");
                    core.setValue("sky", "day-night", "Always");
                }
                case 510 -> {
                    require(GpuEffects.skyFrames() > skyFramesAtDay, "Always sky is visible at noon");
                    screenshot(client, "analyzer-sky-day.png");
                    core.setValue("sky", "day-night", "Night"); command(client, "time set 13000");
                }
                case 535 -> {
                    screenshot(client, "analyzer-sky-night.png"); loadProfile("cinder");
                    core.setValue("menu", "material", "Solid"); core.setEnabled("particles", false);
                    client.setScreen(new AnalyzerScreen(core));
                }
                case 555 -> screenshot(client, "analyzer-menu-gold-solid.png");
                case 560 -> {
                    core.setValue("menu", "material", "Glass"); core.setValue("menu", "accent", "Mint"); core.setEnabled("particles", true);
                }
                case 585 -> screenshot(client, "analyzer-menu-glass-particles.png");
                case 590 -> core.setValue("menu", "accent", "Violet");
                case 615 -> screenshot(client, "analyzer-menu-violet.png");
                case 620 -> {
                    require(GpuEffects.isHealthy(), "GPU effects survived presets, resize and resource reload");
                    AnalyzerClient.LOGGER.info("ANALYZER_SMOKE_WORLD_COMPLETE: {} screenshots; skyFrames={} gradeFrames={}", captures.size(), GpuEffects.skyFrames(), GpuEffects.worldFrames());
                }
            }
            if (worldTicks >= 625) ExpansionSmoke.tick(client, worldTicks - 625);
        }
        if (ticks > 4000) throw new IllegalStateException("Smoke test timed out");
    }

    private static void loadProfile(String name) {
        try { AnalyzerClient.CORE.loadProfile(name); }
        catch (Exception ex) { throw new IllegalStateException("Could not load smoke profile " + name, ex); }
    }
    private static void command(MinecraftClient client, String command) {
        client.getServer().execute(() -> client.getServer().getCommandManager().executeWithPrefix(client.getServer().getCommandSource().withSilent(), command));
    }

    private static void setupArena(MinecraftClient client) {
        var server = client.getServer();
        server.execute(() -> {
            for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++) server.getOverworld().getChunk(x, z);
            var source = server.getCommandSource().withSilent();
            String[] commands = {
                "gamerule doMobSpawning false", "gamerule doMobLoot false", "gamerule doDaylightCycle false", "time set 13000", "weather clear",
                "kill @e[type=!minecraft:player]", "kill @e[type=minecraft:item]", "fill 0 160 0 24 160 24 minecraft:polished_deepslate",
                "fill 0 161 0 24 169 24 minecraft:air", "fill 0 160 0 24 160 0 minecraft:sea_lantern", "fill 0 160 24 24 160 24 minecraft:sea_lantern",
                "fill 0 160 0 0 160 24 minecraft:sea_lantern", "fill 24 160 0 24 160 24 minecraft:sea_lantern",
                "setblock 3 161 8 minecraft:chest[facing=south]", "setblock 6 161 8 minecraft:barrel", "setblock 10 161 7 minecraft:purple_shulker_box",
                "setblock 13 161 8 minecraft:ender_chest", "setblock 17 161 8 minecraft:furnace", "setblock 20 161 8 minecraft:hopper",
                "fill 2 161 11 7 164 11 minecraft:deepslate_bricks",
                "summon minecraft:zombie 4 161 6 {NoAI:1b,Silent:1b,PersistenceRequired:1b}",
                "summon minecraft:cow 9 161 11 {NoAI:1b,Silent:1b,PersistenceRequired:1b}",
                "summon minecraft:pig 15 161 10 {NoAI:1b,Silent:1b,PersistenceRequired:1b}",
                "summon minecraft:skeleton 18 161 6 {NoAI:1b,Silent:1b,PersistenceRequired:1b}",
                "summon minecraft:item 13 161 16 {Item:{id:\"minecraft:diamond\",count:1},PickupDelay:32767,Age:-32768}",
                "tp AnalyzerDev 11 161 21 180 12"
            };
            for (String command : commands) server.getCommandManager().executeWithPrefix(source, command);
            AnalyzerClient.LOGGER.info("ANALYZER_SMOKE_ARENA_READY");
        });
    }

    private static void click(MinecraftClient client, double x, double y) {
        float w = client.getWindow().getScaledWidth(), h = client.getWindow().getScaledHeight();
        double scale = Math.max(0.15, Math.min(1.25, Math.min((w - 20) / 900, (h - 20) / 520)));
        client.currentScreen.mouseClicked((w - 900 * scale) / 2 + x * scale, (h - 520 * scale) / 2 + y * scale, 0);
        client.currentScreen.mouseReleased(0, 0, 0);
    }
    private static void type(MinecraftClient client, String value) { for (char c : value.toCharArray()) client.currentScreen.charTyped(c, 0); }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException("Smoke check failed: " + message); AnalyzerClient.LOGGER.info("Smoke check passed: {}", message); }
    private static void screenshot(MinecraftClient client, String name) {
        int error = GL11.glGetError(); require(error == GL11.GL_NO_ERROR, "GL state before " + name + " (" + error + ")");
        ScreenshotRecorder.saveScreenshot(client.runDirectory, name, client.getFramebuffer(), message -> AnalyzerClient.LOGGER.info("Smoke screenshot: {}", message.getString()));
        captures.add(name);
    }
}
