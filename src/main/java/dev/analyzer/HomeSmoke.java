package dev.analyzer;

import dev.analyzer.core.AnalyzerCore;
import dev.analyzer.effects.HomeEffects;
import dev.analyzer.effects.UiShader;
import dev.analyzer.ui.HomePages;
import dev.analyzer.ui.HomeScreen;
import dev.analyzer.ui.MenuScreens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.option.ServerList;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.ScreenshotRecorder;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Isolated, opt-in title acceptance: real native screens/controls, no world join or Realms request. */
final class HomeSmoke {
    private static final String[] HOME_CONTROLS = {"home-worlds", "home-servers", "home-realms", "home-analyzer", "home-options", "home-quit",
            "home-language", "home-accessibility", "home-credits", "home-background", "home-vanilla"};
    private static final String[] OPTION_CONTROLS = {"back", "options-fov", "options-video", "options-sounds", "options-controls", "options-skin", "options-chat", "options-language",
            "options-resource-packs", "options-accessibility", "options-telemetry", "options-credits"};
    private static final String[] WORLD_CONTROLS = {"back", "world-search", "world-create", "world-play", "world-edit", "world-delete", "world-recreate", "world-list"};
    private static final String[] SERVER_CONTROLS = {"back", "server-add", "server-direct", "server-refresh", "server-join", "server-edit", "server-delete", "server-list"};
    private static final List<Step> steps = new ArrayList<>();
    private static int ticks, stage, entered, assertions, screenshots, originalGuiScale, originalFov;
    private static boolean initialized, complete, originalMultiplayerWarning;
    private static HomeScreen home;
    private static Screen page;
    private static long framesBefore, backgroundBefore, blurBefore;
    private static float frozenYaw;
    private static PixelStats panoramaPixels, orbitPixels;
    private HomeSmoke() {}

    static void tick(MinecraftClient mc) { tick(mc, ++ticks); }
    static void tick(MinecraftClient mc, int now) {
        if (complete) return;
        ticks = now;
        if (!initialized) {
            if (!mc.isFinishedLoading() || mc.getOverlay() != null || !(mc.currentScreen instanceof HomeScreen)) {
                if (now > 600) throw new IllegalStateException("Home fixture did not reach the custom title after loading");
                return;
            }
            initialized = true; entered = now; buildSteps(mc);
        }
        if (stage >= steps.size()) { finish(mc); return; }
        Step step = steps.get(stage);
        if (now - entered > 240) throw new IllegalStateException("Home fixture timed out at " + stage + ": " + step.label);
        if (now - entered < step.waitTicks || mc.getOverlay() != null || !step.action.test(mc)) return;
        AnalyzerClient.LOGGER.info("ANALYZER_HOME_PHASE: {} / {}", stage, step.label);
        stage++; entered = now;
    }

    private static void buildSteps(MinecraftClient mc) {
        var core = AnalyzerClient.CORE;
        originalGuiScale = mc.options.getGuiScale().getValue();
        originalFov = mc.options.getFov().getValue();
        originalMultiplayerWarning = mc.options.skipMultiplayerWarning;
        add("Actual custom title startup", 3, client -> {
            require(client.world == null && client.player == null, "Home acceptance starts outside any game world");
            require(client.runDirectory.toPath().toAbsolutePath().normalize().getFileName().toString().equals("run"), "Home test uses the repository dev run directory");
            ServerList servers = new ServerList(client); servers.loadFile();
            require(servers.size() == 0, "The isolated saved-server list is empty, so the fixture cannot ping public servers");
            client.options.skipMultiplayerWarning = true;
            require(core.getBoolean("menu", "home-enabled") && client.currentScreen instanceof HomeScreen, "The default title routes into the real custom Home");
            GLFW.glfwHideWindow(client.getWindow().getHandle()); client.options.pauseOnLostFocus = false;
            core.setValue("menu", "home-background", "Panorama"); core.setValue("menu", "home-motion", 0D); core.setValue("menu", "home-blur", true);
            viewport(client, 1280, 800, 2); openHome(client); framesBefore = HomeEffects.frames(); backgroundBefore = HomeEffects.landscapeFrames();
        });
        add("Real native panorama and Home controls", 6, client -> {
            require(HomeEffects.isHealthy() && HomeEffects.frames() > framesBefore && HomeEffects.landscapeFrames() > backgroundBefore, "Minecraft panorama is processed by the actual Home shader");
            require(home.backgroundMode().equals("Panorama"), "The visible panorama matches the stored background");
            inViewport(client, HOME_CONTROLS); capture(client, "analyzer-home-panorama.png");
            panoramaPixels = pixels(client);
            require(panoramaPixels.backgroundMean > .01, "Native panorama contains real scene pixels before blur");
            frozenYaw = home.panoramaYaw(); framesBefore = HomeEffects.frames();
        });
        add("Zero motion freezes both title shader and native panorama", 6, client -> {
            require(HomeEffects.frames() > framesBefore && HomeEffects.lastTime() == 0F, "Zero motion submits a zero time uniform over multiple actual frames");
            require(Math.abs(home.panoramaYaw() - frozenYaw) < .000001, "Zero motion keeps the native panorama yaw unchanged");
            backgroundBefore = HomeEffects.orbitFrames(); click(client, "home-background");
        });
        add("Background switch persists Orbit", 6, client -> {
            require(core.getChoice("menu", "home-background").equals("Orbit") && home.backgroundMode().equals("Orbit"), "The actual background control selects procedural Orbit");
            require(HomeEffects.orbitFrames() > backgroundBefore && HomeEffects.isHealthy(), "The original Orbit GLSL draws actual frames");
            save(core); require(loadedCore().getChoice("menu", "home-background").equals("Orbit"), "Orbit survives an active-config reload");
            capture(client, "analyzer-home-orbit.png");
            orbitPixels = pixels(client);
            require(orbitPixels.goldenFraction > .002 && orbitPixels.backgroundMean > .0005, "Orbit has real disk and background content before blur");
            core.setValue("menu", "home-motion", .6D); framesBefore = HomeEffects.frames();
        });
        add("Enabled motion reaches the real shader", 5, client -> {
            require(HomeEffects.frames() > framesBefore && HomeEffects.lastTime() > 0F, "Nonzero Home motion updates its actual shader uniform");
            core.setValue("menu", "home-motion", 0D); core.setValue("menu", "home-background", "Panorama"); click(client, "home-worlds"); page = client.currentScreen;
        });
        add("Native worlds page and its real controls", 5, client -> {
            require(page != home && client.currentScreen == page, "Singleplayer opens a separate real world-selection page");
            pageInViewport(client, WORLD_CONTROLS); panoramaPresent(client); capture(client, "analyzer-home-worlds.png");
            back(client);
        });
        add("Worlds Back returns its exact Home parent", 3, client -> {
            require(client.currentScreen == home, "World selection Back returns the actual Home instance"); click(client, "home-servers"); page = client.currentScreen;
        });
        add("Native saved-server page without joining", 5, client -> {
            require(page != home && client.currentScreen == page, "Multiplayer opens a real saved-server page");
            pageInViewport(client, SERVER_CONTROLS); panoramaPresent(client); capture(client, "analyzer-home-servers.png"); back(client);
        });
        add("Servers Back returns its exact Home parent", 3, client -> {
            require(client.currentScreen == home, "Server selection Back returns the actual Home instance"); click(client, "home-options"); page = client.currentScreen;
        });
        add("Custom options page keeps native navigation", 5, client -> {
            require(page != home && client.currentScreen == page, "Options opens its real settings page");
            pageInViewport(client, OPTION_CONTROLS); panoramaPresent(client); capture(client, "analyzer-home-options.png");
            double[] at = HomePages.controlPoint(client.currentScreen, "options-fov");
            SliderWidget fov = client.currentScreen.children().stream().filter(element -> element instanceof SliderWidget)
                    .map(element -> (SliderWidget) element).filter(widget -> widget.isMouseOver(at[0], at[1])).findFirst().orElseThrow();
            double clickX = fov.getX() + fov.getWidth() * (originalFov < 85 ? .85 : .25);
            client.currentScreen.mouseClicked(clickX, at[1], GLFW.GLFW_MOUSE_BUTTON_LEFT); client.currentScreen.mouseReleased(clickX, at[1], GLFW.GLFW_MOUSE_BUTTON_LEFT);
        });
        add("Original native FOV controller applies its live value", 16, client -> {
            int actual = client.options.getFov().getValue();
            require(actual != originalFov && actual >= 30 && actual <= 110, "The actual native FOV slider changes Minecraft's option within its bounds");
            client.options.getFov().setValue(originalFov); back(client);
        });
        add("Options Back returns its exact Home parent", 3, client -> { require(client.currentScreen == home, "Options Back returns the actual Home instance"); });
        nativeFooter("home-language", "Language");
        nativeFooter("home-accessibility", "Accessibility");
        nativeFooter("home-credits", "Credits");
        for (String layout : List.of("Studio", "Click GUI")) {
            add("Open " + layout + " from Home", 3, client -> { core.setValue("menu", "layout", layout); click(client, "home-analyzer"); });
            add("Native " + layout + " returns to Home", 4, client -> {
                require(MenuScreens.isAnalyzerScreen(client.currentScreen) && MenuScreens.matchesLayout(core, client.currentScreen), "Home opens the saved " + layout + " module menu");
                client.currentScreen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
                require(client.currentScreen == home, layout + " Escape returns its exact Home parent");
            });
        }
        add("Open native module menu for registered shortcut close", 3, client -> { core.setValue("menu", "layout", "Studio"); click(client, "home-analyzer"); });
        add("Dispatch the actual registered menu key", 4, client -> {
            require(MenuScreens.isAnalyzerScreen(client.currentScreen), "The registered shortcut is exercised on an actual module menu");
            KeyBinding.onKeyPressed(InputUtil.fromKeyCode(AnalyzerClient.getMenuKey(), 0));
        });
        add("Registered menu shortcut preserves Home parent", 3, client -> { require(client.currentScreen == home, "The actual menu-key tick returns its exact Home parent"); });
        add("Vanilla fallback is an explicit saved preference", 3, client -> { click(client, "home-vanilla"); });
        add("Native vanilla title remains available", 4, client -> {
            require(!core.getBoolean("menu", "home-enabled") && client.currentScreen instanceof TitleScreen, "The vanilla control opens the actual Minecraft title");
            save(core); require(!loadedCore().getBoolean("menu", "home-enabled"), "Vanilla fallback survives an active-config reload");
            client.setScreen(new TitleScreen());
            require(client.currentScreen instanceof TitleScreen, "A newly initialized title respects the saved vanilla preference");
            core.setValue("menu", "home-enabled", true); save(core); client.setScreen(new TitleScreen());
        });
        add("Re-enabling Home restores the title route", 4, client -> {
            require(client.currentScreen instanceof HomeScreen && loadedCore().getBoolean("menu", "home-enabled"), "Re-enabling Home redirects newly initialized titles");
            home = (HomeScreen) client.currentScreen; core.setValue("menu", "home-background", "Panorama");
        });
        for (int[] size : new int[][]{{1280, 800}, {1920, 1080}}) for (int gui : new int[]{2, 3, 4}) {
            String label = size[0] + "x" + size[1] + "-gui" + gui;
            add("Resize " + label, 3, client -> { viewport(client, size[0], size[1], gui); openHome(client); framesBefore = HomeEffects.frames(); });
            add("Actual native bounds " + label, 5, client -> {
                require(client.getWindow().getFramebufferWidth() == size[0] && client.getWindow().getFramebufferHeight() == size[1], "The native framebuffer matches " + label);
                require(HomeEffects.frames() > framesBefore && HomeEffects.isHealthy(), "The actual Home shader survives " + label);
                inViewport(client, HOME_CONTROLS); capture(client, "analyzer-home-" + label + ".png");
                click(client, "home-options"); page = client.currentScreen;
            });
            add("Actual options bounds " + label, 4, client -> {
                pageInViewport(client, OPTION_CONTROLS); capture(client, "analyzer-home-options-" + label + ".png"); back(client);
                require(client.currentScreen == home, "Resized options Back returns Home at " + label); click(client, "home-worlds");
            });
            add("Actual world-list bounds " + label, 4, client -> {
                pageInViewport(client, WORLD_CONTROLS); capture(client, "analyzer-home-worlds-" + label + ".png"); back(client);
                require(client.currentScreen == home, "Resized worlds Back returns Home at " + label); click(client, "home-servers");
            });
            add("Actual server-list bounds " + label, 4, client -> {
                pageInViewport(client, SERVER_CONTROLS); capture(client, "analyzer-home-servers-" + label + ".png"); back(client);
                require(client.currentScreen == home, "Resized servers Back returns Home at " + label);
            });
        }
        add("Orbit baseline before native-page blur", 3, client -> {
            core.setValue("menu", "home-background", "Orbit"); core.setValue("menu", "home-blur", true); openHome(client);
        });
        add("Blur samples a separate scene", 5, client -> {
            orbitPixels = pixels(client);
            require(orbitPixels.goldenFraction > .002 && orbitPixels.backgroundMean > .0005, "Resized Orbit has real disk and background pixels before blur");
            click(client, "home-options"); blurBefore = HomeEffects.blurFrames();
        });
        add("Actual Orbit blur is submitted", 5, client -> {
            require(HomeEffects.blurFrames() > blurBefore && HomeEffects.sceneCopies() > 0 && HomeEffects.isHealthy(), "Orbit blur copies the completed background and draws actual blur frames");
            PixelStats blurred = pixels(client);
            require(blurred.backgroundMean > Math.max(.0002, orbitPixels.backgroundMean * .10), "Blurred Orbit preserves background pixels outside the native panel");
            require(blurred.goldenFraction > Math.max(.0001, orbitPixels.goldenFraction * .01), "The actual Orbit disk remains visible through the native page");
            capture(client, "analyzer-home-orbit-blur.png"); core.setValue("menu", "home-blur", false); blurBefore = HomeEffects.blurFrames();
        });
        add("Disabling blur removes its pass", 5, client -> {
            require(HomeEffects.blurFrames() == blurBefore, "Disabled blur submits no new blur frames");
            back(client); require(client.currentScreen == home, "Blurred native options still return their actual Home parent");
            framesBefore = HomeEffects.frames(); client.reloadResources();
        });
        add("Native Home survives resource reload", 8, client -> {
            if (HomeEffects.frames() <= framesBefore) return false;
            require(HomeEffects.isHealthy() && UiShader.isHealthy() && GL11.glGetError() == GL11.GL_NO_ERROR, "Resource reload recompiles healthy Home/UI shaders with clean GL state");
            inViewport(client, HOME_CONTROLS); capture(client, "analyzer-home-reloaded.png"); return true;
        });
    }

    private static void nativeFooter(String id, String label) {
        add("Open native " + label + " from Home", 3, client -> { click(client, id); page = client.currentScreen; });
        add("Return from native " + label, 4, client -> {
            require(page != home && client.currentScreen == page, label + " opens its native screen");
            client.currentScreen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
        });
        add("Native " + label + " preserves its parent", 3, client -> { require(client.currentScreen == home, label + " Escape returns its exact Home parent"); });
    }
    private static void panoramaPresent(MinecraftClient mc) {
        PixelStats pagePixels = pixels(mc);
        require(pagePixels.backgroundMean > Math.max(.003, panoramaPixels.backgroundMean * .10), "Blurred native page retains real panorama content outside its panel");
    }
    /** Read the completed main framebuffer, excluding title text/buttons from background samples. */
    private static PixelStats pixels(MinecraftClient mc) {
        int width = mc.getFramebuffer().textureWidth, height = mc.getFramebuffer().textureHeight;
        int readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int packBuffer = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        int packAlignment = GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);
        int packRows = GL11.glGetInteger(GL11.GL_PACK_ROW_LENGTH), skipRows = GL11.glGetInteger(GL11.GL_PACK_SKIP_ROWS), skipPixels = GL11.glGetInteger(GL11.GL_PACK_SKIP_PIXELS);
        ByteBuffer pixels = MemoryUtil.memAlloc(width * height * 4);
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, mc.getFramebuffer().fbo);
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0); GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
            GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, 0); GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, 0); GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, 0);
            GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
            double luminance = 0; int backgroundCount = 0, golden = 0, diskCount = 0;
            for (int y = (int) (height * .025); y < height * .055; y++) for (int x = (int) (width * .32); x < width * .78; x++) {
                int offset = ((height - 1 - y) * width + x) * 4;
                double r = (pixels.get(offset) & 255) / 255D, g = (pixels.get(offset + 1) & 255) / 255D, b = (pixels.get(offset + 2) & 255) / 255D;
                luminance += .2126 * r + .7152 * g + .0722 * b; backgroundCount++;
            }
            for (int y = (int) (height * .20); y < height * .70; y += 2) for (int x = (int) (width * .42); x < width * .94; x += 2) {
                int offset = ((height - 1 - y) * width + x) * 4;
                double r = (pixels.get(offset) & 255) / 255D, g = (pixels.get(offset + 1) & 255) / 255D, b = (pixels.get(offset + 2) & 255) / 255D;
                if (r > .08 && r - g > .02 && r - b > .06) golden++;
                diskCount++;
            }
            require(GL11.glGetError() == GL11.GL_NO_ERROR, "Native framebuffer content read has clean GL state");
            PixelStats result = new PixelStats(luminance / Math.max(1, backgroundCount), golden / (double) Math.max(1, diskCount));
            AnalyzerClient.LOGGER.info("ANALYZER_HOME_PIXEL_CONTENT: backgroundMean={} goldenFraction={}", result.backgroundMean, result.goldenFraction);
            return result;
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer); GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, packBuffer);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, packAlignment); GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, packRows);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, skipRows); GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, skipPixels); MemoryUtil.memFree(pixels);
        }
    }
    private record PixelStats(double backgroundMean, double goldenFraction) {}
    private static void openHome(MinecraftClient mc) { home = new HomeScreen(AnalyzerClient.CORE); mc.setScreen(home); }
    private static void back(MinecraftClient mc) {
        double[] at = HomePages.controlPoint(mc.currentScreen, "back");
        require(at != null, "Native child page exposes its actual Back control");
        mc.currentScreen.mouseClicked(at[0], at[1], GLFW.GLFW_MOUSE_BUTTON_LEFT); mc.currentScreen.mouseReleased(at[0], at[1], GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }
    private static void click(MinecraftClient mc, String id) {
        require(mc.currentScreen instanceof HomeScreen, "Home control is invoked on its actual screen: " + id);
        double[] at = ((HomeScreen) mc.currentScreen).controlPoint(id); require(at != null, "Actual rendered Home control exists: " + id);
        mc.currentScreen.mouseClicked(at[0], at[1], GLFW.GLFW_MOUSE_BUTTON_LEFT);
        if (mc.currentScreen == home) mc.currentScreen.mouseReleased(at[0], at[1], GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }
    private static void inViewport(MinecraftClient mc, String... ids) {
        require(mc.currentScreen instanceof HomeScreen, "Actual Home is visible for native-bound checks");
        for (String id : ids) bounds(mc, id, ((HomeScreen) mc.currentScreen).controlPoint(id));
    }
    private static void pageInViewport(MinecraftClient mc, String... ids) { for (String id : ids) bounds(mc, id, HomePages.controlPoint(mc.currentScreen, id)); }
    private static void bounds(MinecraftClient mc, String id, double[] at) {
        require(at != null && Double.isFinite(at[0]) && Double.isFinite(at[1]) && at[0] >= 0 && at[1] >= 0
                && at[0] < mc.currentScreen.width && at[1] < mc.currentScreen.height, "Actual native hit center is within the screen: " + id);
    }
    private static void viewport(MinecraftClient mc, int width, int height, int gui) { GLFW.glfwSetWindowSize(mc.getWindow().getHandle(), width, height); mc.options.getGuiScale().setValue(gui); mc.onResolutionChanged(); }
    private static AnalyzerCore loadedCore() { var core = new AnalyzerCore(FabricLoader.getInstance().getConfigDir().resolve("analyzer")); try { core.load(); } catch (IOException failure) { throw new IllegalStateException("Cannot reload isolated Home config", failure); } return core; }
    private static void save(AnalyzerCore core) { try { core.save(); } catch (IOException failure) { throw new IllegalStateException("Cannot save isolated Home config", failure); } }
    private static void add(String label, int waitTicks, Consumer<MinecraftClient> action) { steps.add(new Step(label, waitTicks, client -> { action.accept(client); return true; })); }
    private static void add(String label, int waitTicks, Predicate<MinecraftClient> action) { steps.add(new Step(label, waitTicks, action)); }
    private record Step(String label, int waitTicks, Predicate<MinecraftClient> action) {}
    private static void require(boolean condition, String label) { if (!condition) throw new IllegalStateException("Home smoke " + stage + ": " + label); assertions++; AnalyzerClient.LOGGER.info("ANALYZER_HOME_ASSERT: {}", label); }
    private static void capture(MinecraftClient mc, String name) {
        require(GL11.glGetError() == GL11.GL_NO_ERROR && HomeEffects.isHealthy(), "Actual native GL/shader state before " + name);
        String prefix = System.getProperty("analyzer.homeCapturePrefix", "analyzer-home-");
        if (!prefix.matches("[a-z0-9-]+")) throw new IllegalArgumentException("Invalid Home capture prefix");
        name = name.replace("analyzer-home-", prefix);
        ScreenshotRecorder.saveScreenshot(mc.runDirectory, name, mc.getFramebuffer(), message -> AnalyzerClient.LOGGER.info("Home screenshot: {}", message.getString())); screenshots++;
    }
    private static void finish(MinecraftClient mc) {
        var core = AnalyzerClient.CORE;
        core.setValue("menu", "home-enabled", true); core.setValue("menu", "home-background", "Panorama"); core.setValue("menu", "home-motion", .6D); core.setValue("menu", "home-blur", true);
        core.setValue("menu", "layout", "Studio"); save(core); mc.options.getGuiScale().setValue(originalGuiScale);
        mc.options.skipMultiplayerWarning = originalMultiplayerWarning; mc.options.getFov().setValue(originalFov); mc.options.write();
        require(mc.world == null && mc.player == null, "Home acceptance never joins a world or server");
        require(HomeEffects.isHealthy() && UiShader.isHealthy() && GL11.glGetError() == GL11.GL_NO_ERROR, "Final Home/UI shader and GL health");
        complete = true;
        AnalyzerClient.LOGGER.info("ANALYZER_HOME_SMOKE_COMPLETE: {} assertions, {} screenshots, {} phases, {} ticks; native pages, all menu parent returns, persistence, six resize/GUI cases, Panorama/Orbit/blur, zero motion and reload; Realms and Quit controls checked without invoking services or exiting early", assertions, screenshots, steps.size(), ticks);
        mc.scheduleStop();
    }
}
