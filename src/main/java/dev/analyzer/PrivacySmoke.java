package dev.analyzer;

import dev.analyzer.privacy.PrivacyNetworkSmoke;
import dev.analyzer.privacy.PrivacyTextSmoke;
import dev.analyzer.privacy.PackPrivacySmoke;
import dev.analyzer.privacy.ServerPrivacy;
import dev.analyzer.ui.CatalogScreen;
import dev.analyzer.ui.MenuScreens;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.ScreenshotRecorder;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** Opt-in, isolated native codecs/pipeline/UI acceptance. Never joins a server. */
final class PrivacySmoke {
    private static final List<String> FIELDS = List.of("vanilla-brand", "block-mod-channels", "vanilla-only",
            "hide-known-packs", "block-login-queries", "block-key-probes", "block-local-pack-urls", "scrub-pack-headers");
    private static final List<Step> steps = new ArrayList<>();
    private static final Map<Path, byte[]> profiles = new LinkedHashMap<>();
    private static int ticks, stage, entered, assertions, screenshots;
    private static boolean initialized, complete;
    private static boolean poisonMenuFrame;
    private static int poisonedFrames;
    private static int[] menuBaseline;
    private static double maximumTitleDifference;

    private PrivacySmoke() { }

    static void tick(MinecraftClient client) {
        if (complete) return;
        ticks++;
        if (!initialized) {
            if (!client.isFinishedLoading() || client.getOverlay() != null) {
                if (ticks > 600) throw new IllegalStateException("Privacy fixture did not finish loading");
                return;
            }
            require(FabricLoader.getInstance().isDevelopmentEnvironment(), "Native fixture is development-only");
            initialized = true; entered = ticks;
            GLFW.glfwHideWindow(client.getWindow().getHandle());
            try {
                Path directory = FabricLoader.getInstance().getConfigDir().resolve("analyzer/profiles");
                if (Files.exists(directory)) try (var files = Files.list(directory)) {
                    for (Path path : files.filter(Files::isRegularFile).toList()) profiles.put(path, Files.readAllBytes(path));
                }
            } catch (Exception ex) { throw new IllegalStateException(ex); }
            AnalyzerClient.CORE.resetAll();
            build();
        }
        if (stage == steps.size()) { finish(client); return; }
        Step step = steps.get(stage);
        if (ticks - entered < step.delay) return;
        if (step.action.test(client)) {
            AnalyzerClient.LOGGER.info("ANALYZER_PRIVACY_PHASE {} / {}", stage, step.label);
            stage++; entered = ticks;
        } else if (ticks - entered > 200) throw new AssertionError("Unreachable privacy fixture control: " + step.label);
    }

    private static void build() {
        add("Actual native network packet codecs and transformed Netty pipeline", 1, client -> { assertions += PrivacyNetworkSmoke.run(); return true; });
        add("Actual native remote text codecs, fallback and local UI independence", 1, client -> { assertions += PrivacyTextSmoke.run(client); return true; });
        add("Actual scoped resource-pack URL/header protections without external requests", 1, client -> {
            try { assertions += PackPrivacySmoke.run(client); }
            catch (Exception ex) { throw new IllegalStateException("Native pack privacy fixture failed", ex); }
            return true;
        });
        menuOptics("Studio", "analyzer-privacy-studio");
        for (String field : FIELDS) {
            add("Reach and edit native privacy control " + field, 3, client -> {
                String id = "setting:server-privacy:" + field;
                if (point(client.currentScreen, id) == null) {
                    double[] scroll = point(client.currentScreen, "settings-scroll");
                    require(scroll != null, "Selected module supplies native settings scroll");
                    client.currentScreen.mouseScrolled(scroll[0], scroll[1], 0, -2); return false;
                }
                require(AnalyzerClient.CORE.getBoolean("server-privacy", field), "Protection initially enabled: " + field);
                click(client, id);
                require(!AnalyzerClient.CORE.getBoolean("server-privacy", field) && !ServerPrivacy.option(field), "Native setting immediately reaches privacy gate: " + field);
                click(client, id);
                require(ServerPrivacy.option(field), "Native control restores protection: " + field); return true;
            });
        }
        add("Privacy choices save and reload with independent UI choices", 1, client -> {
            try {
                AnalyzerClient.CORE.setValue("server-privacy", "vanilla-only", false);
                AnalyzerClient.CORE.setValue("watermark", "logo", "A logo");
                AnalyzerClient.CORE.save();
                AnalyzerClient.CORE.setValue("server-privacy", "vanilla-only", true);
                AnalyzerClient.CORE.load();
                require(!ServerPrivacy.options().vanillaOnly(), "Config reload restores explicit networking compatibility");
                require(AnalyzerClient.CORE.getChoice("watermark", "logo").equals("A logo"), "Reload preserves independent exclusive watermark selection");
                AnalyzerClient.CORE.reset("server-privacy");
            } catch (Exception ex) { throw new IllegalStateException(ex); }
            return true;
        });
        menuOptics("Click GUI", "analyzer-privacy-click-gui");
        add("Click GUI privacy control and capture", 5, client -> {
            String id = "setting:server-privacy:vanilla-brand";
            require(point(client.currentScreen, id) != null, "New Click GUI exposes actual privacy settings");
            click(client, id);
            require(!ServerPrivacy.options().vanillaBrand(), "Click GUI reaches network brand policy");
            click(client, id);
            return true;
        });
    }

    private static void menuOptics(String layout, String filename) {
        for (String material : List.of("Glass", "Solid")) {
            add("Fresh title background for " + layout + " / " + material, 1, client -> {
                var core = AnalyzerClient.CORE;
                core.setValue("menu", "layout", layout);
                core.setValue("menu", "material", material);
                core.setValue("menu", "motion", 0.0);
                poisonMenuFrame = false; poisonedFrames = 0;
                var menu = MenuScreens.create(core, "server-privacy");
                client.setScreen(menu);
                ScreenEvents.beforeRender(menu).register((screen, context, x, y, delta) -> {
                    if (!poisonMenuFrame) return;
                    // Deliberately contaminate the incoming framebuffer with previous UI-like content.
                    // A fresh title backdrop must erase it before the optical scene is copied.
                    context.fill(0, 0, screen.width, screen.height, 0xFF00FF00);
                    context.draw(); poisonedFrames++;
                });
                return true;
            });
            add("Read actual title surface baseline " + layout + " / " + material, 5, client -> {
                menuBaseline = menuPixels(client);
                poisonMenuFrame = true;
                return true;
            });
            add("Previous-frame contamination cannot enter title glass " + layout + " / " + material, 5, client -> {
                require(poisonedFrames >= 2, "Real before-render callback contaminated multiple incoming frames");
                int[] result = menuPixels(client);
                long total = 0;
                for (int i = 0; i < result.length; i++) total += Math.abs(result[i] - menuBaseline[i]);
                double difference = total / (result.length * 255.0);
                maximumTitleDifference = Math.max(maximumTitleDifference, difference);
                require(difference < .005, "Actual optical surface matches its clean scene after incoming green poison: " + difference);
                poisonMenuFrame = false;
                capture(client, filename + (material.equals("Solid") ? "-solid" : "") + ".png");
                return true;
            });
        }
    }

    private static int[] menuPixels(MinecraftClient client) {
        double[] at = point(client.currentScreen, "settings-scroll");
        require(at != null, "Actual expanded privacy settings expose a sampled optical surface");
        double scale = client.getWindow().getScaleFactor();
        int x = (int) (at[0] * scale) - 16, y = client.getFramebuffer().textureHeight - (int) (at[1] * scale) - 16;
        int read = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER_BINDING);
        int pack = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        int alignment = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_PACK_ALIGNMENT);
        int rows = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_PACK_ROW_LENGTH);
        int skipRows = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_PACK_SKIP_ROWS);
        int skipPixels = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_PACK_SKIP_PIXELS);
        var pixels = org.lwjgl.system.MemoryUtil.memAlloc(32 * 32 * 4);
        try {
            org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER, client.getFramebuffer().fbo);
            org.lwjgl.opengl.GL15.glBindBuffer(org.lwjgl.opengl.GL21.GL_PIXEL_PACK_BUFFER, 0);
            org.lwjgl.opengl.GL11.glPixelStorei(org.lwjgl.opengl.GL11.GL_PACK_ALIGNMENT, 1);
            org.lwjgl.opengl.GL11.glPixelStorei(org.lwjgl.opengl.GL11.GL_PACK_ROW_LENGTH, 0);
            org.lwjgl.opengl.GL11.glPixelStorei(org.lwjgl.opengl.GL11.GL_PACK_SKIP_ROWS, 0);
            org.lwjgl.opengl.GL11.glPixelStorei(org.lwjgl.opengl.GL11.GL_PACK_SKIP_PIXELS, 0);
            org.lwjgl.opengl.GL11.glReadPixels(x, y, 32, 32, org.lwjgl.opengl.GL11.GL_RGBA, org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE, pixels);
            int[] result = new int[32 * 32 * 3];
            for (int pixel = 0; pixel < 32 * 32; pixel++) for (int channel = 0; channel < 3; channel++) result[pixel * 3 + channel] = pixels.get(pixel * 4 + channel) & 255;
            return result;
        } finally {
            org.lwjgl.system.MemoryUtil.memFree(pixels);
            org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER, read);
            org.lwjgl.opengl.GL15.glBindBuffer(org.lwjgl.opengl.GL21.GL_PIXEL_PACK_BUFFER, pack);
            org.lwjgl.opengl.GL11.glPixelStorei(org.lwjgl.opengl.GL11.GL_PACK_ALIGNMENT, alignment);
            org.lwjgl.opengl.GL11.glPixelStorei(org.lwjgl.opengl.GL11.GL_PACK_ROW_LENGTH, rows);
            org.lwjgl.opengl.GL11.glPixelStorei(org.lwjgl.opengl.GL11.GL_PACK_SKIP_ROWS, skipRows);
            org.lwjgl.opengl.GL11.glPixelStorei(org.lwjgl.opengl.GL11.GL_PACK_SKIP_PIXELS, skipPixels);
        }
    }

    private static void add(String label, int delay, Predicate<MinecraftClient> action) { steps.add(new Step(label, delay, action)); }
    private static double[] point(Screen screen, String id) { return screen instanceof CatalogScreen catalog ? catalog.controlPoint(id) : null; }
    private static void click(MinecraftClient client, String id) {
        double[] at = point(client.currentScreen, id);
        require(at != null && at[0] >= 0 && at[1] >= 0 && at[0] < client.getWindow().getScaledWidth()
                && at[1] < client.getWindow().getScaledHeight(), "Actual privacy hit target fits: " + id);
        client.currentScreen.mouseClicked(at[0], at[1], 0);
        client.currentScreen.mouseReleased(at[0], at[1], 0);
    }
    private static void capture(MinecraftClient client, String name) {
        require(org.lwjgl.opengl.GL11.glGetError() == org.lwjgl.opengl.GL11.GL_NO_ERROR, "Privacy menu renders with clean GL");
        ScreenshotRecorder.saveScreenshot(client.runDirectory, name, client.getFramebuffer(), text -> AnalyzerClient.LOGGER.info("Privacy screenshot: {}", name));
        screenshots++;
    }
    private static void finish(MinecraftClient client) {
        try {
            for (var entry : profiles.entrySet()) require(Arrays.equals(entry.getValue(), Files.readAllBytes(entry.getKey())), "Development profile bytes preserved: " + entry.getKey().getFileName());
        } catch (Exception ex) { throw new IllegalStateException(ex); }
        require(client.world == null && client.getNetworkHandler() == null, "Acceptance never joins a world or remote server");
        require(!dev.analyzer.automation.AutomationEngine.INSTANCE.isRunning(), "Privacy menus never start automation");
        AnalyzerClient.CORE.reset("server-privacy");
        AnalyzerClient.CORE.setValue("menu", "layout", "Studio");
        AnalyzerClient.CORE.setValue("watermark", "logo", "Analyzer logo");
        AnalyzerClient.saveConfig();
        complete = true;
        AnalyzerClient.LOGGER.info("ANALYZER_PRIVACY_SMOKE_COMPLETE {} assertions, {} captures, {} phases, {} ticks; actual native metadata codecs/pipeline, remote key probes, scoped resource pack protection, both module menus/materials and persistence; no remote server joined; maximumTitleSceneDifference={}", assertions, screenshots, steps.size(), ticks, maximumTitleDifference);
        client.scheduleStop();
    }
    private static void require(boolean ok, String message) { assertions++; if (!ok) throw new AssertionError(message); }
    private record Step(String label, int delay, Predicate<MinecraftClient> action) { }
}
