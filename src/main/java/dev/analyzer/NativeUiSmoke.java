package dev.analyzer;

import dev.analyzer.automation.AutomationEngine;
import dev.analyzer.core.AnalyzerCore;
import dev.analyzer.core.Category;
import dev.analyzer.effects.GpuEffects;
import dev.analyzer.gameplay.GameplayEngine;
import dev.analyzer.ui.AnalyzerScreen;
import dev.analyzer.ui.AutomationScreen;
import dev.analyzer.ui.ClassicScreen;
import dev.analyzer.ui.CatalogScreen;
import dev.analyzer.ui.CompactScreen;
import dev.analyzer.ui.HudEditorScreen;
import dev.analyzer.ui.MenuScreens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.screen.narration.ScreenNarrator;
import net.minecraft.client.util.ScreenshotRecorder;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Opt-in native acceptance checks; all clicks use bounds registered by the actual renderer. */
final class NativeUiSmoke {
    private static final List<Step> steps = new ArrayList<>();
    private static int stage, entered, tick, assertions, captures, originalGuiScale;
    private static int originalHudKey;
    private static boolean initialized, complete;
    private static Screen parent;
    private static double[] frozenPoint;
    private static GameplayEngine.Diagnostics actionsBefore;
    private static long sceneFramesBefore, solidSceneFrames;
    private NativeUiSmoke() { }

    static void tick(MinecraftClient mc, int t) {
        if (complete) return;
        tick = t;
        if (!initialized) { initialized = true; buildSteps(mc); }
        if (stage >= steps.size()) { finish(mc); return; }
        Step step = steps.get(stage);
        if (t - entered > 100) throw new IllegalStateException("Native UI fixture timed out at " + stage + ": " + step.label);
        if (t > 1600) throw new IllegalStateException("Native UI fixture exceeded its bounded run");
        if (t - entered < step.waitTicks || !step.action.test(mc)) return;
        AnalyzerClient.LOGGER.info("Native UI phase complete: {} / {}", stage, step.label);
        stage++; entered = t;
    }

    private static void buildSteps(MinecraftClient mc) {
        AnalyzerCore core = AnalyzerClient.CORE;
        originalGuiScale = mc.options.getGuiScale().getValue();
        originalHudKey = core.keybind("hud");
        actionsBefore = GameplayEngine.diagnostics();
        sceneFramesBefore = GpuEffects.menuSceneFrames();
        add("Isolated daylight fixture", 0, client -> {
            AutomationEngine.INSTANCE.stop(); core.resetAll(); core.setEnabled("sky", false);
            core.setValue("menu", "motion", 0D); core.setValue("menu", "layout", "Workspace");
            command(client, "time set 1000"); command(client, "weather clear");
            command(client, "kill @e[type=!minecraft:player]");
            client.inGameHud.getChatHud().clear(false); client.getToastManager().clear();
            viewport(client, 1280, 800, 2);
            client.setScreen(new AnalyzerScreen(core).inspect("entity-esp"));
        });
        add("Rendered focused workspace", 8, client -> {
            require(client.currentScreen instanceof AnalyzerScreen, "The default factory resolves the focused workspace");
            require(!core.isEnabled("entity-esp") && !core.isEnabled("kill-aura"), "Inspection begins with gameplay modules disabled");
            require(((AnalyzerScreen) client.currentScreen).selectedModuleId().equals("entity-esp"), "The actual inspector selects Entity ESP");
            inViewport(client, "search", "settings-scroll", "hud-editor", "selected-toggle");
            frozenPoint = point(client.currentScreen, "settings-scroll");
            require(GpuEffects.menuSceneFrames() > sceneFramesBefore, "Glass frost samples the actual native world scene");
            capture(client, "analyzer-ui-overhaul-workspace-glass.png");
        });
        add("Fresh reverse keyboard focus reaches the last control", 3, client -> {
            Screen current = client.currentScreen;
            current.keyPressed(GLFW.GLFW_KEY_TAB, 0, GLFW.GLFW_MOD_SHIFT);
            verifyFocusedNarration(current, "Entity ESP", "keybind");
            current.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
            verifyFocusedNarration(current, "Entity ESP", "Press a key");
            current.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
            require(client.currentScreen == current && core.keybind("entity-esp") < 0, "Initial Shift Tab reaches the last keybind control, and its capture cancels cleanly");
        });
        add("Zero motion remains still", 6, client -> {
            double[] after = point(client.currentScreen, "settings-scroll");
            require(after != null && frozenPoint != null && Math.abs(after[0] - frozenPoint[0]) < .001 && Math.abs(after[1] - frozenPoint[1]) < .001,
                "Zero motion removes panel entrance drift across real frames");
            search(client, "Kill Aura");
        });
        add("Search narrows real cards", 4, client -> {
            AnalyzerScreen screen = (AnalyzerScreen) client.currentScreen;
            require(screen.queryText().equals("Kill Aura"), "The real search field accepts text");
            require(screen.visibleModuleIds().equals(List.of("kill-aura")), "Search narrows catalogue results to the matching module");
            require(point(screen, "inspect:entity-esp") == null, "Filtered-out cards have no active hit region");
            screen.keyPressed(GLFW.GLFW_KEY_SLASH, 0, 0);
            screen.charTyped('/', 0);
            require(screen.queryText().equals("Kill Aura"), "The slash search shortcut preserves an existing query when its character event arrives");
            screen.charTyped('X', 0);
            require(screen.queryText().equals("Kill AuraX"), "Ordinary typing still appends after the slash shortcut");
            screen.keyPressed(GLFW.GLFW_KEY_BACKSPACE, 0, 0);
            screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
            click(client, "inspect:kill-aura", GLFW.GLFW_MOUSE_BUTTON_LEFT);
        });
        add("Keyboard pages the actual long inspector", 3, client -> {
            require(point(client.currentScreen, "setting:kill-aura:players") != null, "Keyboard paging starts at the actual targeting group");
            client.currentScreen.keyPressed(GLFW.GLFW_KEY_PAGE_DOWN, 0, 0);
        });
        add("Page Down reaches later targeting controls", 3, client -> {
            require(point(client.currentScreen, "setting:kill-aura:players") == null && point(client.currentScreen, "setting:kill-aura:range") != null, "Page Down moves to the real later targeting controls without a mouse wheel");
            client.currentScreen.keyPressed(GLFW.GLFW_KEY_PAGE_UP, 0, 0);
        });
        add("Cards inspect independently of activation", 3, client -> {
            require(((AnalyzerScreen) client.currentScreen).selectedModuleId().equals("kill-aura"), "Clicking a card selects its long inspector");
            require(!core.isEnabled("kill-aura"), "Clicking a card does not enable the action module");
            require(point(client.currentScreen, "setting:kill-aura:players") != null, "The targeting group is visible in the real inspector");
            click(client, "selected-toggle", GLFW.GLFW_MOUSE_BUTTON_LEFT);
            verifyFocusedNarration(client.currentScreen, "Kill Aura", "enabled");
            click(client, "selected-toggle", GLFW.GLFW_MOUSE_BUTTON_LEFT);
            verifyFocusedNarration(client.currentScreen, "Kill Aura", "disabled");
            click(client, "module:kill-aura", GLFW.GLFW_MOUSE_BUTTON_LEFT);
            require(core.isEnabled("kill-aura"), "The separate card switch enables its module");
            click(client, "module:kill-aura", GLFW.GLFW_MOUSE_BUTTON_LEFT);
            require(!core.isEnabled("kill-aura"), "The same switch disables the module without changing inspection");
            scroll(client, -100);
        });
        add("Long Combat inspector reaches Safety", 4, client -> {
            require(core.definition("kill-aura").settings().size() >= 30, "Combat fixture exercises a genuinely long settings list");
            require(point(client.currentScreen, "setting:kill-aura:pause-using") != null, "Independent inspector scrolling reaches the last Safety group");
            click(client, "setting:kill-aura:pause-using", GLFW.GLFW_MOUSE_BUTTON_LEFT);
            require(!core.getBoolean("kill-aura", "pause-using"), "The scrolled Safety toggle updates its real saved value");
            capture(client, "analyzer-ui-overhaul-combat-settings.png");
            scroll(client, 100);
        });
        awaitSetting("kill-aura", "range", GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        add("Precise Combat number entry", 2, client -> {
            require(client.currentScreen.getClass().getSimpleName().equals("NumericSettingScreen"), "Right-clicking the rendered number opens precise entry");
            applyNumber(client, "4.25");
            near(core.getNumber("kill-aura", "range"), 4.25, "Keyboard entry updates the real Combat range");
            require(client.currentScreen == parent, "Applying a number returns to the original settings context");
            search(client, "no-such-analyzer-module-6291");
        });
        add("Empty search does not expose stale targets", 4, client -> {
            AnalyzerScreen screen = (AnalyzerScreen) client.currentScreen;
            require(screen.visibleModuleIds().isEmpty(), "Unknown search text renders an empty result state");
            require(point(screen, "inspect:kill-aura") == null && point(screen, "module:kill-aura") == null, "An empty search removes old card hit regions");
            require(!core.isEnabled("kill-aura"), "Search and numeric editing keep Combat disabled");
            search(client, "Entity ESP");
        });
        add("Search returns to ESP", 4, client -> {
            click(client, "inspect:entity-esp", GLFW.GLFW_MOUSE_BUTTON_LEFT);
            require(!core.isEnabled("entity-esp"), "Inspecting ESP through search leaves it disabled");
        });
        awaitSetting("entity-esp", "line-width", GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        add("Precise ESP number entry", 2, client -> {
            applyNumber(client, "1.75");
            near(core.getNumber("entity-esp", "line-width"), 1.75, "Keyboard entry updates the actual ESP line width");
            require(client.currentScreen == parent, "ESP numeric entry retains its catalogue parent");
            scroll(client, -100);
        });
        add("Long ESP inspector reaches its final control", 4, client -> {
            require(point(client.currentScreen, "setting:entity-esp:style") != null, "ESP scrolling reaches its final Box style choice");
            capture(client, "analyzer-ui-overhaul-esp-settings.png");
            search(client, "");
        });
        add("Category navigation scopes the full catalogue", 4, client -> click(client, "category:COMBAT", GLFW.GLFW_MOUSE_BUTTON_LEFT));
        add("Category contents and search cleanup", 3, client -> {
            AnalyzerScreen screen = (AnalyzerScreen) client.currentScreen;
            require(!screen.visibleModuleIds().isEmpty() && screen.visibleModuleIds().stream().allMatch(id -> core.definition(id).category() == Category.COMBAT), "Combat navigation shows only its actual category");
            require(screen.queryText().isEmpty(), "Clearing the search restores category browsing");
            client.setScreen(new AnalyzerScreen(core).inspect("hud"));
        });

        // These are HUD-module controls in the native inspector, not direct setters.
        awaitSetting("hud", "menu-material", GLFW.GLFW_MOUSE_BUTTON_LEFT);
        add("Solid sampling baseline", 0, client -> solidSceneFrames = GpuEffects.menuSceneFrames());
        add("HUD material alias controls the interface", 3, client -> {
            require(core.getChoice("menu", "material").equals("Solid") && core.getChoice("hud", "menu-material").equals("Solid"), "HUD Studio panel material writes the canonical interface preference");
            require(core.getChoice("hud", "theme").equals("Glass"), "Menu material does not replace the independent HUD widget theme");
            require(GpuEffects.menuSceneFrames() == solidSceneFrames, "Solid native panels do not sample the scene frost pass");
            capture(client, "analyzer-ui-overhaul-solid.png");
        });
        awaitSetting("hud", "menu-material", GLFW.GLFW_MOUSE_BUTTON_LEFT);
        add("Glass material is restored", 3, client -> {
            require(core.getChoice("menu", "material").equals("Glass"), "The actual HUD material control switches back to Glass");
            require(GpuEffects.menuSceneFrames() > solidSceneFrames, "Restored Glass resumes actual scene sampling");
        });
        for (String theme : List.of("Classic", "Minimal", "Neon", "Glass")) {
            awaitSetting("hud", "menu-theme", GLFW.GLFW_MOUSE_BUTTON_LEFT);
            add("Existing interface theme " + theme, 4, client -> {
                require(core.getChoice("menu", "theme").equals(theme) && core.getChoice("hud", "menu-theme").equals(theme), "The HUD alias selects existing menu theme " + theme);
                require(core.getChoice("hud", "theme").equals("Glass"), "Changing menu theme preserves independent HUD widgets");
                capture(client, "analyzer-ui-overhaul-theme-" + theme.toLowerCase(java.util.Locale.ROOT) + ".png");
            });
        }
        add("Actual HUD menu settings capture", 3, client -> {
            inViewport(client, "setting:hud:menu-layout", "setting:hud:menu-material", "setting:hud:menu-theme", "setting:hud:menu-accent");
            capture(client, "analyzer-ui-overhaul-menu-settings.png");
        });
        awaitSetting("hud", "menu-accent", GLFW.GLFW_MOUSE_BUTTON_LEFT);
        add("Existing accent choice remains available", 3, client -> {
            require(core.getChoice("menu", "accent").equals("Violet") && core.getChoice("hud", "menu-accent").equals("Violet"), "HUD Studio forwards the existing Violet accent");
            require(!core.isEnabled("kill-aura") && !core.isEnabled("entity-esp"), "Menu personalization changes no action-module enable state");
            client.currentScreen.keyPressed(GLFW.GLFW_KEY_TAB, 0, 0);
            client.currentScreen.keyPressed(GLFW.GLFW_KEY_TAB, 0, GLFW.GLFW_MOD_SHIFT);
            client.currentScreen.keyPressed(GLFW.GLFW_KEY_RIGHT, 0, 0);
            require(core.getChoice("menu", "accent").equals("Gold"), "Tab navigation returns to the focused choice and arrow keys adjust it");
            client.currentScreen.keyPressed(GLFW.GLFW_KEY_LEFT, 0, 0);
            require(core.getChoice("menu", "accent").equals("Violet"), "The opposite arrow reverses the focused choice");
            verifyFocusedNarration(client.currentScreen, "Accent color", "Violet");
            scroll(client, 100);
        });
        awaitSetting("hud", "menu-layout", GLFW.GLFW_MOUSE_BUTTON_LEFT);
        add("HUD layout alias switches the native menu", 4, client -> {
            require(core.getChoice("menu", "layout").equals("Classic") && core.getChoice("hud", "menu-layout").equals("Classic"), "The HUD menu choice writes the existing Classic layout value");
            require(client.currentScreen instanceof ClassicScreen, "Changing HUD menu layout opens the selected native Classic menu");
            require(MenuScreens.matchesLayout(core, client.currentScreen), "The live screen matches its persisted layout");
            require(point(client.currentScreen, "setting:hud:menu-layout") != null, "The menu switch retains the inspected HUD settings context");
            capture(client, "analyzer-ui-overhaul-classic-glass.png");
            saveAndReload(core);
            client.setScreen(null);
            client.setScreen(MenuScreens.create(core, "hud"));
        });
        add("Classic name clicks inspect without activation", 3, client -> search(client, "Kill Aura"));
        add("Classic name and settings are separate from its switch", 3, client -> {
            click(client, "inspect-left:kill-aura", GLFW.GLFW_MOUSE_BUTTON_LEFT);
            require(!core.isEnabled("kill-aura") && ((CatalogScreen) client.currentScreen).selectedModuleId().equals("kill-aura"), "Classic name clicks inspect Combat while its module stays disabled");
            verifyFocusedNarration(client.currentScreen, "Kill Aura", "inspect settings");
            ((CatalogScreen) client.currentScreen).inspect("hud");
            search(client, "HUD Studio");
        });
        addKeybindSteps("Classic");
        addProfileBackSteps("Classic");
        add("The next open uses the saved choice", 3, client -> {
            require(client.currentScreen instanceof ClassicScreen, "Closing and reopening resolves the saved Classic preference");
            click(client, "layout-compact", GLFW.GLFW_MOUSE_BUTTON_LEFT);
        });
        add("Retained Compact alternative", 4, client -> {
            require(client.currentScreen instanceof CompactScreen && core.getChoice("hud", "menu-layout").equals("Compact"), "The existing Compact header control updates the shared HUD preference");
            require(((CatalogScreen) client.currentScreen).queryText().equals("HUD Studio") && ((CatalogScreen) client.currentScreen).selectedModuleId().equals("hud"), "Switching from Classic to Compact retains search and inspected module");
            click(client, "inspect-left:hud", GLFW.GLFW_MOUSE_BUTTON_LEFT);
            require(core.isEnabled("hud"), "Compact name clicks inspect HUD without disabling its module");
            verifyFocusedNarration(client.currentScreen, "HUD Studio", "inspect settings");
            capture(client, "analyzer-ui-overhaul-compact-glass.png");
        });
        addKeybindSteps("Compact");
        addProfileBackSteps("Compact");
        add("Return to the selected Workspace menu", 3, client -> {
            click(client, "layout-workspace", GLFW.GLFW_MOUSE_BUTTON_LEFT);
        });
        add("Workspace selection and Automation context", 4, client -> {
            require(client.currentScreen instanceof AnalyzerScreen, "Workspace remains selectable alongside retained menus");
            require(((CatalogScreen) client.currentScreen).queryText().equals("HUD Studio") && ((CatalogScreen) client.currentScreen).selectedModuleId().equals("hud"), "Switching from Compact to Workspace retains search and inspected module");
            parent = client.currentScreen;
            click(client, "automation", GLFW.GLFW_MOUSE_BUTTON_LEFT);
        });
        add("Automation has its actual contextual parent", 4, client -> {
            require(client.currentScreen instanceof AutomationScreen, "The workspace opens the native Automation screen");
            require(!AutomationEngine.INSTANCE.snapshot().running(), "Opening Automation does not start a task");
            client.currentScreen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
            require(client.currentScreen == parent, "Automation Back returns to the exact workspace parent");
        });
        addProfileBackSteps("Workspace");
        add("Profile Escape retains exact Workspace context", 3, client -> { parent = client.currentScreen; click(client, "profiles", GLFW.GLFW_MOUSE_BUTTON_LEFT); });
        add("Profile Escape returns to the originating query", 3, client -> {
            client.currentScreen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
            require(client.currentScreen == parent && ((CatalogScreen) client.currentScreen).queryText().equals("HUD Studio") && ((CatalogScreen) client.currentScreen).selectedModuleId().equals("hud"), "Escape from Profiles returns to the exact query and selected module");
        });
        add("HUD editing has its actual contextual parent", 3, client -> { parent = client.currentScreen; click(client, "hud-editor", GLFW.GLFW_MOUSE_BUTTON_LEFT); });
        add("HUD editor retained controls", 4, client -> {
            require(client.currentScreen instanceof HudEditorScreen, "Edit HUD opens the real HUD Studio editor");
            inViewport(client, "theme", "done", "selected-settings");
            click(client, "theme", GLFW.GLFW_MOUSE_BUTTON_LEFT);
            require(core.getChoice("hud", "theme").equals("Classic"), "HUD widgets retain their existing independent theme picker");
            require(core.getChoice("menu", "theme").equals("Glass"), "HUD widget themes preserve the selected menu theme");
            capture(client, "analyzer-ui-overhaul-hud-studio.png");
            click(client, "done", GLFW.GLFW_MOUSE_BUTTON_LEFT);
            require(client.currentScreen == parent, "HUD Done returns to its exact menu parent");
            core.setValue("hud", "theme", "Glass"); core.setValue("menu", "accent", "Mint");
        });
        add("Reload native resource textures", 2, client -> client.reloadResources());
        add("Native frost after resource reload", 8, client -> {
            require(GpuEffects.isHealthy() && GpuEffects.menuSceneFrames() > 0, "The actual native Glass pass recovers after resource reload");
            require(GL11.glGetError() == GL11.GL_NO_ERROR, "Resource reload leaves native GL state clean");
        });

        // Vanilla caps GUI scale 4 to 3 at 1280x800 to keep at least 320x240 logical pixels.
        for (int[] size : new int[][]{{1280, 800}, {1920, 1080}}) for (int requested : new int[]{2, 3, 4}) {
            int w = size[0], h = size[1], effective = w == 1280 && requested == 4 ? 3 : requested;
            add("Resize to " + w + " / GUI " + requested, 1, client -> {
                viewport(client, w, h, requested);
                core.setValue("menu", "motion", 0D);
                client.setScreen(new AnalyzerScreen(core).inspect("kill-aura"));
            });
            add("Native bounds at " + w + " / GUI " + requested, 6, client -> {
                require(client.getWindow().getFramebufferWidth() == w && client.getWindow().getFramebufferHeight() == h, "The actual framebuffer resized to " + w + "x" + h);
                near(client.getWindow().getScaleFactor(), effective, "Vanilla effective GUI scale for " + w + " / requested " + requested);
                inViewport(client, "search", "settings-scroll", "selected-toggle", "hud-editor", "setting:kill-aura:players");
                require(((AnalyzerScreen) client.currentScreen).selectedModuleId().equals("kill-aura"), "Resize retains the inspected module");
                require(!core.isEnabled("kill-aura"), "Resize and reopening keep inspected Combat disabled");
                scroll(client, -100);
            });
            add("Scrolled bounds at " + w + " / GUI " + requested, 3, client -> {
                inViewport(client, "setting:kill-aura:pause-using");
                capture(client, "analyzer-ui-overhaul-workspace-" + w + "-gui" + requested + ".png");
            });
        }
        add("Zero-motion final cleanup", 3, client -> {
            require(GpuEffects.isHealthy(), "Native backdrop and material effects remain healthy after themes and resize");
            GameplayEngine.Diagnostics after = GameplayEngine.diagnostics();
            require(after.attacks() == actionsBefore.attacks() && after.placements() == actionsBefore.placements() && after.inventoryClicks() == actionsBefore.inventoryClicks() && after.movementChanges() == actionsBefore.movementChanges(), "UI interactions make no gameplay attacks, placements, inventory clicks or owned movement changes");
            require(!client.options.forwardKey.isPressed() && !client.options.jumpKey.isPressed() && !client.options.sneakKey.isPressed(), "Menus retain clean released movement inputs");
            require(GL11.glGetError() == GL11.GL_NO_ERROR, "Final native GL state is clean");
            client.setScreen(null); core.resetAll();
            client.options.getGuiScale().setValue(originalGuiScale); client.onResolutionChanged();
            try { core.save(); } catch (IOException failure) { throw new IllegalStateException("Cannot restore isolated UI smoke settings", failure); }
        });
    }

    private static void awaitSetting(String module, String setting, int button) {
        steps.add(new Step("Rendered HUD/settings control " + module + "/" + setting, 3, mc -> {
            String id = "setting:" + module + ":" + setting;
            double[] at = point(mc.currentScreen, id);
            if (at == null) { scroll(mc, -2); return false; }
            parent = mc.currentScreen;
            click(mc, id, button); return true;
        }));
    }
    private static void addKeybindSteps(String layout) {
        steps.add(new Step("Rendered inline " + layout + " keybind", 3, mc -> {
            double[] at = point(mc.currentScreen, "keybind:hud");
            if (at == null) { scroll(mc, -2); return false; }
            click(mc, "keybind:hud", GLFW.GLFW_MOUSE_BUTTON_LEFT);
            verifyFocusedNarration(mc.currentScreen, "HUD Studio", "Press a key");
            mc.currentScreen.keyPressed(GLFW.GLFW_KEY_Q, 0, 0);
            require(AnalyzerClient.CORE.keybind("hud") == GLFW.GLFW_KEY_Q, layout + " inline keybind accepts the selected real key");
            return true;
        }));
        add(layout + " keybind capture cancellation and restoration", 3, mc -> {
            Screen current = mc.currentScreen;
            click(mc, "keybind:hud", GLFW.GLFW_MOUSE_BUTTON_LEFT);
            current.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
            require(mc.currentScreen == current && AnalyzerClient.CORE.keybind("hud") == GLFW.GLFW_KEY_Q, layout + " Escape cancels key capture without closing the menu or losing its binding");
            click(mc, "keybind:hud", GLFW.GLFW_MOUSE_BUTTON_LEFT);
            current.keyPressed(originalHudKey < 0 ? GLFW.GLFW_KEY_BACKSPACE : originalHudKey, 0, 0);
            require(AnalyzerClient.CORE.keybind("hud") == originalHudKey, layout + " real key capture restores the original binding");
        });
    }
    private static void addProfileBackSteps(String layout) {
        add(layout + " contextual Profile entry", 3, mc -> {
            require(((CatalogScreen) mc.currentScreen).queryText().equals("HUD Studio") && ((CatalogScreen) mc.currentScreen).selectedModuleId().equals("hud"), layout + " Profile entry starts with actual search and selection");
            parent = mc.currentScreen; click(mc, "profiles", GLFW.GLFW_MOUSE_BUTTON_LEFT);
        });
        add(layout + " contextual Profile Back", 3, mc -> {
            if (layout.equals("Workspace")) capture(mc, "analyzer-ui-overhaul-profiles.png");
            click(mc, "profiles-back", GLFW.GLFW_MOUSE_BUTTON_LEFT);
            require(mc.currentScreen == parent, layout + " Profile Back returns to the exact originating menu");
            require(((CatalogScreen) mc.currentScreen).queryText().equals("HUD Studio") && ((CatalogScreen) mc.currentScreen).selectedModuleId().equals("hud"), layout + " Profile Back retains its query and inspected module");
        });
    }
    private static void add(String label, int wait, Consumer<MinecraftClient> action) { steps.add(new Step(label, wait, mc -> { action.accept(mc); return true; })); }
    private static void viewport(MinecraftClient mc, int width, int height, int gui) {
        GLFW.glfwSetWindowSize(mc.getWindow().getHandle(), width, height);
        mc.options.getGuiScale().setValue(gui); mc.onResolutionChanged();
    }
    private static void search(MinecraftClient mc, String text) {
        click(mc, "search", GLFW.GLFW_MOUSE_BUTTON_LEFT);
        Screen screen = mc.currentScreen;
        screen.keyPressed(GLFW.GLFW_KEY_A, 0, GLFW.GLFW_MOD_CONTROL);
        screen.keyPressed(GLFW.GLFW_KEY_BACKSPACE, 0, 0);
        for (char c : text.toCharArray()) screen.charTyped(c, 0);
        screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
    }
    private static void applyNumber(MinecraftClient mc, String value) {
        Screen screen = mc.currentScreen;
        screen.keyPressed(GLFW.GLFW_KEY_A, 0, GLFW.GLFW_MOD_CONTROL);
        screen.keyPressed(GLFW.GLFW_KEY_BACKSPACE, 0, 0);
        for (char c : value.toCharArray()) screen.charTyped(c, 0);
        screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
    }
    private static double[] point(Screen screen, String id) {
        if (screen instanceof AnalyzerScreen value) return value.controlPoint(id);
        if (screen instanceof ClassicScreen value) return value.controlPoint(id);
        if (screen instanceof CompactScreen value) return value.controlPoint(id);
        if (screen instanceof HudEditorScreen value) return value.controlPoint(id);
        return null;
    }
    private static void click(MinecraftClient mc, String id, int button) {
        Screen screen = mc.currentScreen; double[] at = point(screen, id);
        require(at != null, "Actual rendered control exists: " + id);
        screen.mouseClicked(at[0], at[1], button); screen.mouseReleased(at[0], at[1], button);
    }
    private static void scroll(MinecraftClient mc, double amount) {
        double[] at = point(mc.currentScreen, "settings-scroll");
        require(at != null, "Actual inspector viewport has a scroll anchor");
        mc.currentScreen.mouseScrolled(at[0], at[1], 0, amount);
    }
    private static void inViewport(MinecraftClient mc, String... ids) {
        for (String id : ids) {
            double[] at = point(mc.currentScreen, id);
            require(at != null && Double.isFinite(at[0]) && Double.isFinite(at[1]) && at[0] >= 0 && at[1] >= 0 && at[0] < mc.getWindow().getScaledWidth() && at[1] < mc.getWindow().getScaledHeight(), "Rendered control stays in the logical viewport: " + id);
        }
    }
    private static void saveAndReload(AnalyzerCore core) {
        try {
            core.save();
            AnalyzerCore loaded = new AnalyzerCore(FabricLoader.getInstance().getConfigDir().resolve("analyzer")); loaded.load();
            for (String id : List.of("layout", "material", "theme", "accent")) require(loaded.getChoice("menu", id).equals(core.getChoice("menu", id)) && loaded.getChoice("hud", "menu-" + id).equals(core.getChoice("menu", id)), "Real active configuration reload preserves canonical/aliased menu " + id);
            near(loaded.getNumber("kill-aura", "range"), 4.25, "Real configuration reload preserves precise Combat numeric entry");
            near(loaded.getNumber("entity-esp", "line-width"), 1.75, "Real configuration reload preserves precise ESP numeric entry");
            require(loaded.loadWarnings().isEmpty(), "The saved interface and gameplay settings reload without warnings");
        } catch (IOException failure) { throw new IllegalStateException("Cannot verify native preference persistence", failure); }
    }
    private static void verifyFocusedNarration(Screen screen, String label, String value) {
        try {
            // Exercise the actual custom Screen narration hook without changing the user's
            // narration preferences or relying on speech-device availability.
            var hook = Class.forName("dev.analyzer.ui.CatalogScreen").getDeclaredMethod("addElementNarrations", NarrationMessageBuilder.class);
            hook.setAccessible(true);
            ScreenNarrator narrator = new ScreenNarrator();
            narrator.buildNarrations(builder -> {
                try { hook.invoke(screen, builder); }
                catch (ReflectiveOperationException failure) { throw new IllegalStateException("Cannot execute focused narration", failure); }
            });
            String spoken = narrator.buildNarratorText(true);
            require(spoken.contains(label) && spoken.contains(value) && spoken.contains("Tab"), "Focused native narration includes its setting name, current value and keyboard instructions");
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Cannot verify native narration hook", failure); }
    }
    private static void command(MinecraftClient mc, String text) { mc.getServer().execute(() -> mc.getServer().getCommandManager().executeWithPrefix(mc.getServer().getCommandSource().withSilent(), text)); }
    private static void near(double actual, double expected, String label) { require(Math.abs(actual - expected) < .00001, label + " (actual " + actual + ")"); }
    private static void require(boolean ok, String label) { if (!ok) throw new IllegalStateException("Native UI check failed at phase " + stage + ": " + label); assertions++; AnalyzerClient.LOGGER.info("Native UI check passed: {}", label); }
    private static void capture(MinecraftClient mc, String name) {
        require(GL11.glGetError() == GL11.GL_NO_ERROR, "Clean native GL state before " + name);
        mc.inGameHud.getChatHud().clear(false); mc.getToastManager().clear();
        ScreenshotRecorder.saveScreenshot(mc.runDirectory, name, mc.getFramebuffer(), message -> AnalyzerClient.LOGGER.info("Native UI screenshot: {}", message.getString())); captures++;
    }
    private static void finish(MinecraftClient mc) { complete = true; AnalyzerClient.LOGGER.info("ANALYZER_NATIVE_UI_SMOKE_COMPLETE: {} assertions, {} screenshots, {} ticks; actual catalogue/search/inspection, long Combat/ESP numeric editing, HUD menu aliases/themes, persisted preferences, contextual Back and effective GUI scales", assertions, captures, tick); mc.scheduleStop(); }
    private record Step(String label, int waitTicks, Predicate<MinecraftClient> action) { }
}
