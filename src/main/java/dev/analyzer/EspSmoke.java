package dev.analyzer;

import dev.analyzer.render.AnalyzerRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import org.lwjgl.opengl.GL11;

/** Dense real-entity renderer checks, invoked only by the isolated development smoke harness. */
final class EspSmoke {
    static final int LAST_TICK = 180;
    private static int assertions, captures, phase, unfilteredLabels, fullRangeTargets;
    private static float largest, smallest;
    private EspSmoke() { }

    static void tick(MinecraftClient mc, int tick) {
        var core = AnalyzerClient.CORE; phase = tick;
        switch (tick) {
            case 0 -> {
                core.resetAll(); mc.setScreen(null);
                // Retained visual tests use five chunks; this fixture spans eighty-four blocks north.
                mc.options.getViewDistance().setValue(8); mc.options.getSimulationDistance().setValue(8);
                mc.options.sendClientSettings();
                mc.options.setPerspective(Perspective.FIRST_PERSON); mc.options.hudHidden = false;
                viewport(mc, 1920, 1080, 2);
                core.setEnabled("entity-esp", true); core.setValue("entity-esp", "items", true);
                core.setValue("entity-esp", "labels", true);
                core.setValue("entity-esp", "style", "Corners"); core.setValue("entity-esp", "health", false);
                core.setValue("entity-esp", "max-labels", 128); core.setValue("entity-esp", "declutter", false);
                command(mc, "gamemode creative AnalyzerDev"); command(mc, "kill @e[type=!minecraft:player]");
                command(mc, "time set 18000"); command(mc, "weather clear");
                // Dense display fixture must not kill its own entities through vanilla crowd damage.
                command(mc, "gamerule maxEntityCramming 0"); command(mc, "gamerule doMobSpawning false");
                command(mc, "tp AnalyzerDev 11.5 161 21.5 180 0");
                // Seventy-one actual hostile targets recreate the user's dense scene; no fake overlay data.
                for (int i = 0; i < 60; i++)
                    zombie(mc, 11.5 + (i % 3) * .02, 14.5 + (i % 4) * .02);
                zombie(mc, 7.5, 14.5); zombie(mc, 15.5, 14.5);
                zombie(mc, 2.5, -5.5); zombie(mc, 20.5, -5.5);
                for (int i = 0; i < 7; i++) zombie(mc, -4.5 + i * 5, -62.5);
                command(mc, "summon minecraft:item 10 161 -62 {NoGravity:1b,PickupDelay:32767,Item:{id:\"minecraft:diamond\",count:1}}");
            }
            case 30 -> {
                var labels = AnalyzerRenderer.labelCounts();
                unfilteredLabels = labels.entities(); fullRangeTargets = AnalyzerRenderer.counts().entities();
                AnalyzerClient.LOGGER.info("ESP dense fixture: selected={}, labels={}",AnalyzerRenderer.counts(),labels);
                require(fullRangeTargets >= 71, "Dense fixture contains at least seventy-one selected real targets");
                require(unfilteredLabels >= 50, "Declutter opt-out actually submits the dense label stack");
                require(labels.tinyMarkersSkipped() > 0, "World ESP omits actual subpixel distant item marker");
                capture(mc, "analyzer-esp-dense-unfiltered.png");
                core.setValue("entity-esp", "declutter", true); core.setValue("entity-esp", "max-labels", 16);
            }
            case 40 -> {
                var labels = AnalyzerRenderer.labelCounts();
                require(labels.entities() > 0 && labels.entities() <= 16, "World labels obey configured budget");
                require(labels.entities() < unfilteredLabels, "World projection rejects overlapping tags");
                require(labels.largestTextPixels() > labels.smallestTextPixels() + 1, "Actual near and middle tags use different sizes");
                require(labels.largestTextPixels() <= 14.01f, "ESP glyphs do not inherit HUD font floor");
                capture(mc, "analyzer-esp-distance-world.png");
                core.setValue("entity-esp", "style", "Screen");
            }
            case 50 -> {
                var labels = AnalyzerRenderer.labelCounts();
                AnalyzerClient.LOGGER.info("ESP screen fixture: selected={}, projected={}, labels={}", AnalyzerRenderer.counts(), AnalyzerRenderer.screenCounts(), labels);
                require(AnalyzerRenderer.screenCounts().entities() >= 71, "Screen mode preserves meaningful actual target projections");
                require(labels.entities() > 0 && labels.entities() <= 16, "Screen labels obey same budget");
                require(labels.tinyMarkersSkipped() > 0, "Screen mode omits subpixel item without inflating its box");
                require(labels.largestTextPixels() > labels.smallestTextPixels() + 1, "Screen tags shrink by distance");
                largest = labels.largestTextPixels(); smallest = labels.smallestTextPixels();
                capture(mc, "analyzer-esp-distance-screen-gui2.png"); viewport(mc, 1920, 1080, 4);
            }
            case 60 -> {
                var labels = AnalyzerRenderer.labelCounts();
                require(Math.abs(labels.largestTextPixels() - largest) < .1f, "ESP near text physical size survives GUI scale change");
                require(Math.abs(labels.smallestTextPixels() - smallest) < 1, "ESP far text physical size survives GUI scale change");
                capture(mc, "analyzer-esp-distance-screen-gui4.png");
                core.setValue("hud", "scale", 1.2);
            }
            case 70 -> {
                var labels = AnalyzerRenderer.labelCounts();
                require(Math.abs(labels.largestTextPixels() - largest) < .1f, "Changing HUD to 120 percent leaves ESP text independent");
                core.setValue("hud", "scale", .6); core.setValue("entity-esp", "max-labels", 2);
            }
            case 80 -> {
                require(AnalyzerRenderer.labelCounts().entities() <= 2, "Live maximum-label setting limits submitted panels");
                core.setValue("entity-esp", "max-labels", 0);
            }
            case 90 -> {
                require(AnalyzerRenderer.labelCounts().entities() == 0, "Zero label budget actually hides tags");
                require(AnalyzerRenderer.screenCounts().entities() >= 71, "Label budget leaves boxes available");
                core.setValue("entity-esp", "max-labels", 16); core.setValue("entity-esp", "range", 16);
            }
            case 100 -> {
                require(AnalyzerRenderer.counts().entities() < fullRangeTargets && AnalyzerRenderer.counts().entities() >= 60,
                        "Block range excludes middle and far fixtures while retaining near crowd");
                core.setValue("entity-esp", "range", 128); core.setValue("entity-esp", "label-range", 8);
            }
            case 110 -> {
                require(AnalyzerRenderer.counts().entities() >= 71, "Independent label range preserves geometry block range");
                require(AnalyzerRenderer.labelCounts().largestTextPixels() == AnalyzerRenderer.labelCounts().smallestTextPixels(),
                        "Eight-block label range retains only full-size near tags");
                core.setValue("entity-esp", "label-range", 48); core.setValue("entity-esp", "label-scale", .5);
            }
            case 120 -> {
                require(AnalyzerRenderer.labelCounts().entities() > 0 && AnalyzerRenderer.labelCounts().largestTextPixels() <= 7.01f,
                        "Half label scale is rendered below the HUD minimum");
                core.setValue("entity-esp", "label-scale", 1); core.setEnabled("entity-esp", false);
            }
            case 130 -> {
                require(AnalyzerRenderer.labelCounts().entities() == 0 && AnalyzerRenderer.screenCounts().entities() == 0,
                        "Disabling ESP clears label and projection caches");
                core.setEnabled("storage-esp", true); core.setValue("storage-esp", "style", "Screen");
                core.setValue("storage-esp", "labels", true);
                core.setValue("storage-esp", "label-range", 48); core.setValue("storage-esp", "max-labels", 2);
                command(mc, "setblock 7 161 14 minecraft:chest"); command(mc, "setblock 15 161 14 minecraft:barrel");
                command(mc, "setblock 2 161 -5 minecraft:chest"); command(mc, "setblock 20 161 -5 minecraft:barrel");
            }
            case 155 -> {
                var labels = AnalyzerRenderer.labelCounts();
                require(AnalyzerRenderer.screenCounts().containers() >= 4, "Storage fixture projects actual loaded containers");
                require(labels.containers() > 0 && labels.containers() <= 2, "Storage uses its own label range and budget");
                capture(mc, "analyzer-esp-storage-declutter.png"); core.setValue("storage-esp", "max-labels", 0);
            }
            case 165 -> {
                require(AnalyzerRenderer.labelCounts().containers() == 0 && AnalyzerRenderer.screenCounts().containers() >= 4,
                        "Storage label budget preserves container boxes");
                core.setEnabled("storage-esp", false);
            }
            case LAST_TICK -> {
                require(GL11.glGetError() == GL11.GL_NO_ERROR, "ESP final GL state"); core.resetAll();
                command(mc, "kill @e[type=!minecraft:player]"); command(mc, "gamerule maxEntityCramming 24");
                AnalyzerClient.LOGGER.info("ANALYZER_ESP_COMPLETE: {} assertions, {} screenshots", assertions, captures);
            }
            default -> { }
        }
    }

    private static void zombie(MinecraftClient mc, double x, double z) {
        command(mc, "summon minecraft:zombie " + x + " 161 " + z + " {NoAI:1b,NoGravity:1b,Silent:1b,PersistenceRequired:1b}");
    }
    private static void viewport(MinecraftClient mc, int width, int height, int guiScale) {
        org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getHandle(), width, height);
        mc.options.getGuiScale().setValue(guiScale); mc.onResolutionChanged();
    }
    private static void command(MinecraftClient mc, String text) {
        mc.getServer().execute(() -> mc.getServer().getCommandManager()
                .executeWithPrefix(mc.getServer().getCommandSource().withSilent(), text));
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("ESP check failed at " + phase + ": " + message);
        assertions++; AnalyzerClient.LOGGER.info("ESP check passed: {}", message);
    }
    private static void capture(MinecraftClient mc, String name) {
        require(GL11.glGetError() == GL11.GL_NO_ERROR, "ESP GL state before " + name);
        ScreenshotRecorder.saveScreenshot(mc.runDirectory, name, mc.getFramebuffer(),
                text -> AnalyzerClient.LOGGER.info("ESP screenshot: {}", text.getString())); captures++;
    }
}
