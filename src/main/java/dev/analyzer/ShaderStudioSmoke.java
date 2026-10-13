package dev.analyzer;

import dev.analyzer.automation.AutomationEngine;
import dev.analyzer.core.AnalyzerCore;
import dev.analyzer.core.Category;
import dev.analyzer.effects.GpuEffects;
import dev.analyzer.effects.UiShader;
import dev.analyzer.gameplay.GameplayEngine;
import dev.analyzer.ui.AutomationScreen;
import dev.analyzer.ui.CatalogScreen;
import dev.analyzer.ui.HudEditorScreen;
import dev.analyzer.ui.MenuScreens;
import dev.analyzer.ui.StudioScreen;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.ScreenshotRecorder;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Bounded, opt-in acceptance run against the actual GLSL Studio and its input controls. */
final class ShaderStudioSmoke {
    private static final List<Step> steps = new ArrayList<>();
    private static int stage, entered, tick, assertions, captures, originalGuiScale;
    private static boolean initialized, complete;
    private static Screen parent;
    private static long surfacesBefore, sceneFramesBefore;
    private static double[] frozenPoint;
    private static String deepModule;
    private static boolean deepModuleEnabled;
    private static GameplayEngine.Diagnostics actionsBefore;
    private ShaderStudioSmoke() { }

    static void tick(MinecraftClient mc, int now) {
        if (complete) return;
        tick = now;
        if (!initialized) { initialized = true; buildSteps(mc); }
        if (stage >= steps.size()) { finish(mc); return; }
        Step step = steps.get(stage);
        if (now - entered > 130) throw new IllegalStateException("Studio fixture timed out at " + stage + ": " + step.label);
        if (now > 1500) throw new IllegalStateException("Studio fixture exceeded its bounded run");
        if (now - entered < step.waitTicks || !step.action.test(mc)) return;
        AnalyzerClient.LOGGER.info("Shader Studio phase complete: {} / {}", stage, step.label);
        stage++; entered = now;
    }

    private static void buildSteps(MinecraftClient mc) {
        AnalyzerCore core = AnalyzerClient.CORE;
        originalGuiScale = mc.options.getGuiScale().getValue();
        actionsBefore = GameplayEngine.diagnostics();
        add("Isolated daylight fixture with an existing Classic preference", 0, client -> {
            AutomationEngine.INSTANCE.stop(); core.resetAll();
            core.setEnabled("sky", false); core.setValue("menu", "motion", 0D);
            core.setValue("menu", "layout", "Studio");
            core.setValue("menu", "classic-visual-x", 17D);
            core.setValue("menu", "classic-visual-y", 36D);
            core.setValue("menu", "classic-visual-collapsed", true);
            save(core);
            try {
                var path = FabricLoader.getInstance().getConfigDir().resolve("analyzer/config.json");
                var legacy = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(path)).getAsJsonObject();
                legacy.getAsJsonObject("modules").getAsJsonObject("menu").getAsJsonObject("settings").addProperty("layout", "Classic");
                java.nio.file.Files.writeString(path, legacy.toString());core.load();
            } catch (IOException failure) {throw new IllegalStateException("Cannot prepare isolated retired-layout fixture", failure);}
            command(client, "time set 1000"); command(client, "weather clear");
            command(client, "kill @e[type=!minecraft:player]");
            client.inGameHud.getChatHud().clear(false); client.getToastManager().clear();
            viewport(client, 1280, 800, 2);
            client.setScreen(MenuScreens.create(core, "hud"));
        });
        add("Saved retired preference migrates before the native factory opens", 5, client -> {
            require(client.currentScreen instanceof StudioScreen && core.getChoice("hud", "menu-layout").equals("Studio"), "An existing Classic preference now opens Studio");
            AnalyzerCore loaded = loadedCore();
            require(loaded.getChoice("menu", "layout").equals("Studio") && loaded.getNumber("menu", "classic-visual-x") == 17D, "Reload migrates the retired layout while retaining saved window positions");
            surfacesBefore = UiShader.surfaces(); sceneFramesBefore = GpuEffects.menuSceneFrames();
        });
        chooseHud("menu-layout", "Studio");
        add("Explicit HUD selection opens Studio", 4, client -> {
            require(client.currentScreen instanceof StudioScreen && core.getChoice("menu", "layout").equals("Studio"), "The explicit HUD Studio selector opens the new native menu and writes its preference");
            ((CatalogScreen) client.currentScreen).inspect("kill-aura");
        });
        add("Actual GLSL Studio renders the new card layout", 6, client -> {
            require(UiShader.isHealthy() && UiShader.surfaces() > surfacesBefore, "The Studio draws successful GLSL rounded surfaces");
            require(GpuEffects.menuSceneFrames() > sceneFramesBefore, "Glass Studio samples the actual daylight scene");
            require(core.getNumber("menu", "classic-visual-x") == 17D && core.getNumber("menu", "classic-visual-y") == 36D && core.getBoolean("menu", "classic-visual-collapsed"), "Selecting Studio preserves the independent Classic window settings");
            inViewport(client, "search", "appearance", "settings-scroll", "module:kill-aura");
            frozenPoint = point(client.currentScreen, "settings-scroll");
            capture(client, "analyzer-studio-combat.png");
            click(client, "inspect:kill-aura", GLFW.GLFW_MOUSE_BUTTON_LEFT);
            ((CatalogScreen) client.currentScreen).inspect("entity-esp");
            search(client, "Kill Aura");
        });
        add("Search and card activation are separate", 4, client -> {
            CatalogScreen screen = (CatalogScreen) client.currentScreen;
            require(screen.queryText().equals("Kill Aura") && screen.visibleModuleIds().equals(List.of("kill-aura")), "Actual search narrows the Studio cards to Kill Aura");
            require(point(screen, "inspect:entity-esp") == null, "Filtered-out cards lose their live hit regions");
            click(client, "inspect:kill-aura", GLFW.GLFW_MOUSE_BUTTON_LEFT);
            require(screen.selectedModuleId().equals("kill-aura") && !core.isEnabled("kill-aura"), "A module name inspects its settings without activating Combat");
            click(client, "module:kill-aura", GLFW.GLFW_MOUSE_BUTTON_LEFT);
            require(core.isEnabled("kill-aura"), "The separate card switch enables its module");
            click(client, "module:kill-aura", GLFW.GLFW_MOUSE_BUTTON_LEFT);
            require(!core.isEnabled("kill-aura"), "The card switch disables its module while inspection remains selected");
        });
        seek("setting:kill-aura:range", client -> {
            double[] at = point(client.currentScreen, "setting:kill-aura:range");
            double before = core.getNumber("kill-aura", "range");
            click(client, "setting:kill-aura:range", GLFW.GLFW_MOUSE_BUTTON_LEFT);
            client.currentScreen.mouseClicked(at[0], at[1], GLFW.GLFW_MOUSE_BUTTON_LEFT);
            client.currentScreen.mouseDragged(at[0] + 10, at[1], GLFW.GLFW_MOUSE_BUTTON_LEFT, 10, 0);
            client.currentScreen.mouseReleased(at[0] + 10, at[1], GLFW.GLFW_MOUSE_BUTTON_LEFT);
            require(core.getNumber("kill-aura", "range") != before && core.getNumber("kill-aura", "range") >= core.definition("kill-aura").setting("range").min() && core.getNumber("kill-aura", "range") <= core.definition("kill-aura").setting("range").max(), "Dragging the actual numeric track edits its normalized bounded value");
        });
        seek("number:kill-aura:range", client -> {
            parent = client.currentScreen;
            click(client, "number:kill-aura:range", GLFW.GLFW_MOUSE_BUTTON_LEFT);
        });
        add("Precise keyboard numeric editing", 2, client -> {
            require(client.currentScreen.getClass().getSimpleName().equals("NumericSettingScreen"), "The rendered numeric value opens precise native entry");
            applyNumber(client, "4.25");
            require(core.getNumber("kill-aura", "range") == 4.25 && client.currentScreen == parent, "Applying a number writes Combat range and returns to the exact expanded card");
            scroll(client, -100);
        });
        add("Long card settings and keybind are reachable", 4, client -> {
            require(point(client.currentScreen, "setting:kill-aura:pause-using") != null, "Independent card scrolling reaches the final Combat Safety control");
            click(client, "setting:kill-aura:pause-using", GLFW.GLFW_MOUSE_BUTTON_LEFT);
            require(!core.getBoolean("kill-aura", "pause-using"), "The scrolled Safety toggle edits its real saved value");
        });
        seek("keybind:kill-aura", client -> {
            Screen current = client.currentScreen;
            click(client, "keybind:kill-aura", GLFW.GLFW_MOUSE_BUTTON_LEFT);
            current.keyPressed(GLFW.GLFW_KEY_Q, 0, 0);
            require(core.keybind("kill-aura") == GLFW.GLFW_KEY_Q, "The card keybind control captures a real key");
            click(client, "keybind:kill-aura", GLFW.GLFW_MOUSE_BUTTON_LEFT);
            current.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
            require(client.currentScreen == current && core.keybind("kill-aura") == GLFW.GLFW_KEY_Q, "Escape cancels key capture without closing Studio or losing the binding");
            click(client, "keybind:kill-aura", GLFW.GLFW_MOUSE_BUTTON_LEFT);
            current.keyPressed(GLFW.GLFW_KEY_BACKSPACE, 0, 0);
        });
        add("Inspect a deep Cosmetic card", 3, client -> {
            List<String> cosmetics = core.modules().stream().filter(module -> module.category() == Category.COSMETICS).map(module -> module.id()).toList();
            require(cosmetics.size() > 12, "The deep-card fixture exercises a multi-page Cosmetic category");
            deepModule = cosmetics.getLast(); deepModuleEnabled = core.isEnabled(deepModule);
            ((CatalogScreen) client.currentScreen).inspect(deepModule);
        });
        add("Deep card is revealed by the real masonry layout", 4, client -> {
            CatalogScreen screen = (CatalogScreen) client.currentScreen;
            require(screen.selectedModuleId().equals(deepModule) && point(screen, "module:" + deepModule) != null && point(screen, "settings-scroll") != null,
                    "Inspecting the last Cosmetic module reveals its title, switch and expanded settings");
            require(core.isEnabled(deepModule) == deepModuleEnabled, "Revealing a deep card preserves its actual enable state");
            frozenPoint = point(screen, "module:" + deepModule);
            scroll(client, 1);
        });
        add("Boundary wheel bubbles to the surrounding catalogue", 4, client -> {
            double[] after = point(client.currentScreen, "module:" + deepModule);
            require(after != null && frozenPoint != null && after[1] > frozenPoint[1] + .1,
                    "An upward wheel at the card's first setting moves the outer catalogue instead of trapping scroll input");
            require(((CatalogScreen) client.currentScreen).selectedModuleId().equals(deepModule) && core.isEnabled(deepModule) == deepModuleEnabled,
                    "Boundary scrolling preserves the selected card and enable state");
        });
        add("Category browsing and empty search", 3, client -> {
            search(client, "missing-module-studio-937");
        });
        add("No stale controls in an empty result", 3, client -> {
            CatalogScreen screen = (CatalogScreen) client.currentScreen;
            require(screen.visibleModuleIds().isEmpty() && point(screen, "module:kill-aura") == null, "An empty result removes old switches and cards");
            search(client, ""); click(client, "category:MOVEMENT", GLFW.GLFW_MOUSE_BUTTON_LEFT);
        });
        add("Movement category scopes the actual catalogue", 3, client -> {
            List<String> ids = ((CatalogScreen) client.currentScreen).visibleModuleIds();
            require(!ids.isEmpty() && ids.stream().allMatch(id -> core.definition(id).category() == Category.MOVEMENT), "Movement navigation shows only actual Movement modules");
            click(client, "appearance", GLFW.GLFW_MOUSE_BUTTON_LEFT);
        });
        add("Appearance opens the HUD Studio module", 4, client -> {
            require(client.currentScreen instanceof StudioScreen && ((CatalogScreen) client.currentScreen).selectedModuleId().equals("hud"), "Appearance keeps the new Studio menu and expands real HUD Studio settings");
            capture(client, "analyzer-studio-menu-settings.png");
        });

        for (String theme : List.of("Classic", "Minimal", "Neon", "Glass")) {
            chooseHud("menu-theme", theme);
            add("Native shader theme " + theme, 4, client -> {
                require(core.getChoice("menu", "theme").equals(theme) && core.getChoice("hud", "theme").equals("Glass"), "Menu theme " + theme + " uses the HUD alias and preserves the independent HUD theme");
                require(UiShader.isHealthy(), "The rounded-surface shader remains healthy with theme " + theme);
                capture(client, "analyzer-studio-theme-" + theme.toLowerCase(Locale.ROOT) + ".png");
            });
        }
        for (String accent : List.of("Mint", "Violet", "Gold", "Rose")) {
            chooseHud("menu-accent", accent);
            add("Retained accent " + accent, 2, client -> require(core.getChoice("menu", "accent").equals(accent), "HUD Studio exposes the real " + accent + " accent"));
        }
        add("Saved new preference and Classic positions", 3, client -> {
            save(core); AnalyzerCore loaded = loadedCore();
            require(loaded.getChoice("menu", "layout").equals("Studio") && loaded.getChoice("hud", "menu-layout").equals("Studio"), "An explicit Studio preference reloads through canonical and HUD settings");
            require(loaded.getChoice("menu", "accent").equals("Rose") && loaded.getNumber("kill-aura", "range") == 4.25, "Saved menu personalization and precise numeric edits reload together");
            require(loaded.getNumber("menu", "classic-visual-x") == 17D && loaded.getNumber("menu", "classic-visual-y") == 36D && loaded.getBoolean("menu", "classic-visual-collapsed"), "Saved Studio appearance preserves old Classic positions and collapse state");
            capture(client, "analyzer-studio-rose-settings.png");
        });
        chooseHud("menu-material", "Solid");
        add("Solid Studio beauty context", 3, client -> {
            sceneFramesBefore = GpuEffects.menuSceneFrames();
            ((CatalogScreen) client.currentScreen).inspect("kill-aura");
        });
        add("Solid shader surfaces preserve the crisp world", 5, client -> {
            require(core.getChoice("menu", "material").equals("Solid") && GpuEffects.menuSceneFrames() == sceneFramesBefore,
                    "Solid Studio keeps the world crisp and performs no backdrop scene copies");
            require(UiShader.isHealthy(), "Solid black and gray surfaces use the healthy rounded-surface shader");
            capture(client, "analyzer-studio-solid.png");
            click(client, "appearance", GLFW.GLFW_MOUSE_BUTTON_LEFT);
        });
        chooseHud("menu-material", "Glass");

        chooseHud("menu-layout", "Click GUI");
        add("HUD settings switch to the new shader Click GUI", 4, client -> {
            require(client.currentScreen.getClass().getSimpleName().equals("ClickGuiScreen") && MenuScreens.matchesLayout(core, client.currentScreen), "HUD Studio selects the new shader Click GUI");
            require(core.getChoice("hud", "menu-layout").equals("Click GUI"), "Only the current Click GUI alternative is persisted");
            capture(client, "analyzer-studio-click-gui.png");
        });
        chooseHud("menu-layout", "Studio");
        add("Profiles preserve their Studio parent", 3, client -> {
            require(client.currentScreen instanceof StudioScreen, "The new Studio remains selectable after alternate menus");
            parent = client.currentScreen; click(client, "profiles", GLFW.GLFW_MOUSE_BUTTON_LEFT);
        });
        add("Profile Back", 3, client -> {
            click(client, "profiles-back", GLFW.GLFW_MOUSE_BUTTON_LEFT);
            require(client.currentScreen == parent, "Profiles returns to the exact Studio card context");
            click(client, "hud-editor", GLFW.GLFW_MOUSE_BUTTON_LEFT);
        });
        add("HUD editor returns to its native parent", 4, client -> {
            require(client.currentScreen instanceof HudEditorScreen, "Studio opens the actual HUD editor");
            client.currentScreen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
            require(client.currentScreen == parent, "HUD editing returns to its exact Studio parent");
            click(client, "automation", GLFW.GLFW_MOUSE_BUTTON_LEFT);
        });
        add("Automation keeps its contextual Studio parent", 4, client -> {
            require(client.currentScreen instanceof AutomationScreen && !AutomationEngine.INSTANCE.snapshot().running(), "Opening Automation displays review without starting a task");
            client.currentScreen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
            require(client.currentScreen == parent, "Automation Back retains the Studio card context");
            surfacesBefore = UiShader.surfaces(); client.reloadResources();
        });
        add("Resource reload recovers native shaders", 8, client -> {
            require(UiShader.isHealthy() && UiShader.surfaces() > surfacesBefore && GpuEffects.isHealthy(), "Resource reload recompiles the actual menu surface and backdrop shaders");
            require(GL11.glGetError() == GL11.GL_NO_ERROR, "Resource reload restores clean GL state");
        });

        for (int[] size : new int[][]{{1280, 800}, {1920, 1080}}) for (int requested : new int[]{2, 3, 4}) {
            int width = size[0], height = size[1], effective = width == 1280 && requested == 4 ? 3 : requested;
            add("Studio resize " + width + " / GUI " + requested, 1, client -> {
                viewport(client, width, height, requested);
                client.setScreen(MenuScreens.create(core, "kill-aura"));
            });
            add("Actual Studio bounds " + width + " / GUI " + requested, 6, client -> {
                require(client.getWindow().getFramebufferWidth() == width && client.getWindow().getFramebufferHeight() == height && client.getWindow().getScaleFactor() == effective,
                    "Actual framebuffer and vanilla effective GUI scale match " + width + "x" + height + " / requested " + requested);
                inViewport(client, "search", "settings-scroll", "appearance", "module:kill-aura", "setting:kill-aura:players");
                require(client.currentScreen instanceof StudioScreen && ((CatalogScreen) client.currentScreen).selectedModuleId().equals("kill-aura") && !core.isEnabled("kill-aura"), "Resize retains Studio inspection without activating Combat");
                capture(client, "analyzer-studio-" + width + "-gui" + requested + ".png");
            });
        }
        add("Final native cleanup", 4, client -> {
            double[] at = point(client.currentScreen, "settings-scroll");
            frozenPoint = at;
        });
        add("Zero motion and clean gameplay ownership", 4, client -> {
            double[] at = point(client.currentScreen, "settings-scroll");
            require(at != null && frozenPoint != null && Math.abs(at[0] - frozenPoint[0]) < .001 && Math.abs(at[1] - frozenPoint[1]) < .001, "Zero menu motion leaves actual card coordinates still across frames");
            GameplayEngine.Diagnostics after = GameplayEngine.diagnostics();
            require(after.attacks() == actionsBefore.attacks() && after.placements() == actionsBefore.placements() && after.inventoryClicks() == actionsBefore.inventoryClicks() && after.movementChanges() == actionsBefore.movementChanges(), "Studio interactions create no gameplay attacks, placements, inventory clicks or owned movement changes");
            require(UiShader.isHealthy() && GpuEffects.isHealthy() && GL11.glGetError() == GL11.GL_NO_ERROR, "Final surface shaders, backdrop and GL state remain healthy");
            client.setScreen(null); core.resetAll(); save(core);
            client.options.getGuiScale().setValue(originalGuiScale); client.onResolutionChanged();
        });
    }

    private static void add(String label, int wait, Consumer<MinecraftClient> action) {
        steps.add(new Step(label, wait, mc -> { action.accept(mc); return true; }));
    }
    private static void seek(String id, Consumer<MinecraftClient> action) {
        steps.add(new Step("Rendered card control " + id, 3, mc -> {
            if (point(mc.currentScreen, id) == null) { scroll(mc, -2); return false; }
            action.accept(mc); return true;
        }));
    }
    private static void chooseHud(String setting, String value) {
        boolean[] prepared = {false};
        steps.add(new Step("HUD choice " + setting + " = " + value, 3, mc -> {
            if (!prepared[0]) { ((CatalogScreen) mc.currentScreen).inspect("hud"); prepared[0] = true; return false; }
            if (AnalyzerClient.CORE.getChoice("hud", setting).equals(value)) return true;
            String id = "setting:hud:" + setting;
            if (point(mc.currentScreen, id) == null) { scroll(mc, -2); return false; }
            click(mc, id, GLFW.GLFW_MOUSE_BUTTON_LEFT); return false;
        }));
    }
    private static void viewport(MinecraftClient mc, int width, int height, int gui) {
        GLFW.glfwSetWindowSize(mc.getWindow().getHandle(), width, height);
        mc.options.getGuiScale().setValue(gui); mc.onResolutionChanged();
    }
    private static void search(MinecraftClient mc, String value) {
        click(mc, "search", GLFW.GLFW_MOUSE_BUTTON_LEFT);
        Screen screen = mc.currentScreen;
        screen.keyPressed(GLFW.GLFW_KEY_A, 0, GLFW.GLFW_MOD_CONTROL);
        screen.keyPressed(GLFW.GLFW_KEY_BACKSPACE, 0, 0);
        for (char c : value.toCharArray()) screen.charTyped(c, 0);
        screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
    }
    private static void applyNumber(MinecraftClient mc, String value) {
        Screen screen = mc.currentScreen;
        screen.keyPressed(GLFW.GLFW_KEY_A, 0, GLFW.GLFW_MOD_CONTROL);
        screen.keyPressed(GLFW.GLFW_KEY_BACKSPACE, 0, 0);
        for (char c : value.toCharArray()) screen.charTyped(c, 0);
        screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
    }
    private static double[] point(Screen screen, String id) { return screen instanceof CatalogScreen catalog ? catalog.controlPoint(id) : null; }
    private static void click(MinecraftClient mc, String id, int button) {
        double[] at = point(mc.currentScreen, id);
        if (at == null) throw new IllegalStateException("Studio rendered control is missing: " + id);
        Screen current = mc.currentScreen; current.mouseClicked(at[0], at[1], button); current.mouseReleased(at[0], at[1], button);
    }
    private static void scroll(MinecraftClient mc, double amount) {
        double[] at = point(mc.currentScreen, "settings-scroll");
        if (at == null) return; // A layout choice can create a new screen between rendered frames.
        mc.currentScreen.mouseScrolled(at[0], at[1], 0, amount);
    }
    private static void inViewport(MinecraftClient mc, String... ids) {
        boolean valid = true;
        for (String id : ids) {
            double[] at = point(mc.currentScreen, id);
            valid &= at != null && Double.isFinite(at[0]) && Double.isFinite(at[1]) && at[0] >= 0 && at[1] >= 0 && at[0] < mc.getWindow().getScaledWidth() && at[1] < mc.getWindow().getScaledHeight();
        }
        require(valid, "Rendered controls stay in the native logical viewport: " + String.join(", ", ids));
    }
    private static void save(AnalyzerCore core) { try { core.save(); } catch (IOException failure) { throw new IllegalStateException("Cannot save isolated Studio settings", failure); } }
    private static AnalyzerCore loadedCore() {
        try { AnalyzerCore loaded = new AnalyzerCore(FabricLoader.getInstance().getConfigDir().resolve("analyzer")); loaded.load(); if (!loaded.loadWarnings().isEmpty()) throw new IOException("Unexpected config warnings " + loaded.loadWarnings()); return loaded; }
        catch (IOException failure) { throw new IllegalStateException("Cannot reload isolated Studio settings", failure); }
    }
    private static void command(MinecraftClient mc, String text) { mc.getServer().execute(() -> mc.getServer().getCommandManager().executeWithPrefix(mc.getServer().getCommandSource().withSilent(), text)); }
    private static void require(boolean ok, String label) { if (!ok) throw new IllegalStateException("Studio check failed at phase " + stage + ": " + label); assertions++; AnalyzerClient.LOGGER.info("Shader Studio check passed: {}", label); }
    private static void capture(MinecraftClient mc, String name) {
        require(GL11.glGetError() == GL11.GL_NO_ERROR, "Clean native GL state before " + name);
        mc.inGameHud.getChatHud().clear(false); mc.getToastManager().clear();
        ScreenshotRecorder.saveScreenshot(mc.runDirectory, name, mc.getFramebuffer(), message -> AnalyzerClient.LOGGER.info("Shader Studio screenshot: {}", message.getString())); captures++;
    }
    private static void finish(MinecraftClient mc) { complete = true; AnalyzerClient.LOGGER.info("ANALYZER_SHADER_STUDIO_SMOKE_COMPLETE: {} assertions, {} screenshots, {} ticks; actual GLSL card surfaces, retired-layout migration, separate inspection/switches, precise numbers, HUD aliases/themes/accents, persistence, contextual Back, resource reload and effective GUI scales", assertions, captures, tick); mc.scheduleStop(); }
    private record Step(String label, int waitTicks, Predicate<MinecraftClient> action) { }
}
