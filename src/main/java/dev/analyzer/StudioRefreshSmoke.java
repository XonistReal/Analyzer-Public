package dev.analyzer;

import dev.analyzer.automation.AutomationEngine;
import dev.analyzer.automation.SafetyHud;
import dev.analyzer.core.AnalyzerCore;
import dev.analyzer.effects.GpuEffects;
import dev.analyzer.effects.HomeEffects;
import dev.analyzer.effects.UiShader;
import dev.analyzer.gameplay.GameplayEngine;
import dev.analyzer.render.AnalyzerRenderer;
import dev.analyzer.ui.AutomationScreen;
import dev.analyzer.ui.BrandAssets;
import dev.analyzer.ui.CatalogScreen;
import dev.analyzer.ui.HomeScreen;
import dev.analyzer.ui.HudEditorScreen;
import dev.analyzer.ui.HudWidgets;
import dev.analyzer.ui.MenuScreens;
import dev.analyzer.ui.NumericSettingScreen;
import dev.analyzer.ui.StudioScreen;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.ScreenshotRecorder;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Opt-in native acceptance of refreshed auxiliary screens and the actual gameplay HUD. */
final class StudioRefreshSmoke {
    private static final String PROFILE = "studio-refresh-fixture";
    private static final List<Step> steps = new ArrayList<>();
    private static final Map<String, Long> widgetCounts = new HashMap<>();
    private static int phase, entered, ticks, assertions, screenshots, originalGuiScale;
    private static boolean initialized, complete, safetyFixture;
    private static Screen studio, editor, modal;
    private static long surfacesBefore, hudSurfacesBefore;
    private static long wordmarkBefore, monogramBefore;
    private static long safetyBefore;
    private static long radarBefore;
    private static GameplayEngine.Diagnostics actionsBefore;
    private static double[] frozenPoint;
    private static double originalWidgetX, originalWidgetY;
    private static HudWidgets.WidgetBounds sampledWidget;
    private static int[] baselinePixels;
    private static Map<String, String> existingProfiles;

    private StudioRefreshSmoke() { }

    static void tick(MinecraftClient client, int now) {
        if (complete) return;
        ticks = now;
        if (!initialized) { initialized = true; buildSteps(client); }
        if (phase >= steps.size()) { finish(client); return; }
        Step step = steps.get(phase);
        if (now - entered > 160) throw new IllegalStateException("Studio refresh timeout at " + phase + ": " + step.label);
        if (now > 1700) throw new IllegalStateException("Studio refresh exceeded its bounded run");
        if (now - entered < step.waitTicks || !step.action.test(client)) return;
        AnalyzerClient.LOGGER.info("Studio refresh phase complete: {} / {}", phase, step.label);
        phase++; entered = now;
    }

    private static void buildSteps(MinecraftClient mc) {
        AnalyzerCore core = AnalyzerClient.CORE;
        originalGuiScale = mc.options.getGuiScale().getValue();
        actionsBefore = GameplayEngine.diagnostics();
        existingProfiles = profileHashes();
        HudRenderCallback.EVENT.register((draw, counter) -> {
            if (safetyFixture) SafetyHud.render(draw, core, "Native shader banner fixture: status text remains readable and task controls stay separate.", true);
        });
        add("Isolated quiet creative fixture", 0, client -> {
            require(!profiles(core).contains(PROFILE), "The temporary native profile does not collide with an existing profile");
            AutomationEngine.INSTANCE.stop(); core.resetAll();
            for (var module : core.modules()) core.setEnabled(module.id(), false);
            core.setEnabled("menu", true); core.setEnabled("hud", true);
            core.setValue("menu", "layout", "Studio"); core.setValue("menu", "accent", "Rose");
            core.setValue("menu", "theme", "Glass"); core.setValue("menu", "material", "Solid");
            core.setValue("menu", "motion", 0D); core.setValue("hud", "theme", "Glass");
            command(client, "gamerule doDaylightCycle false"); command(client, "time set 1000"); command(client, "weather clear");
            client.inGameHud.getChatHud().clear(false); client.getToastManager().clear();
            viewport(client, 1280, 800, 2); surfacesBefore = UiShader.surfaces();
            studio = MenuScreens.create(core, "kill-aura"); client.setScreen(studio);
        });
        add("Current Studio native surfaces", 5, client -> {
            require(client.currentScreen instanceof StudioScreen && UiShader.surfaces() > surfacesBefore, "The saved Studio route draws real successful SDF surfaces");
            bounds(client, "profiles", "hud-editor", "automation", "appearance", "settings-scroll");
            frozenPoint = point(studio, "settings-scroll"); capture(client, "analyzer-refresh-studio.png");
        });
        add("Zero menu motion preserves actual controls", 5, client -> {
            double[] after = point(studio, "settings-scroll");
            require(after != null && frozenPoint != null && Math.abs(after[0] - frozenPoint[0]) < .001 && Math.abs(after[1] - frozenPoint[1]) < .001,
                    "Zero motion leaves the real Studio control coordinates fixed across frames");
            ((CatalogScreen) studio).inspect("hand-tweaks");
        });
        add("Swing animation settings retain Studio cards", 4, client -> {
            require(client.currentScreen == studio && ((CatalogScreen) studio).selectedModuleId().equals("hand-tweaks"), "Hand/swing animation settings expand inside the current Studio cards");
            bounds(client, "module:hand-tweaks", "settings-scroll"); capture(client, "analyzer-refresh-animation-settings.png");
            ((CatalogScreen) studio).inspect("kill-aura");
        });
        seek("number:kill-aura:range", client -> { click(client, "number:kill-aura:range"); modal = client.currentScreen; surfacesBefore = UiShader.surfaces(); });
        add("Numeric modal uses refreshed surfaces", 4, client -> {
            require(modal instanceof NumericSettingScreen && client.currentScreen == modal && UiShader.surfaces() > surfacesBefore, "The real numeric value opens a shader modal");
            require(numericTitleEdgeFraction(client) > .012, "The numeric title remains crisp after native child widgets render");
            bounds(client, "value", "apply", "cancel"); type(client.currentScreen, "NaN"); click(client, "apply");
        });
        add("Invalid numeric input stays in its modal", 3, client -> {
            require(client.currentScreen == modal && !((NumericSettingScreen) modal).errorText().isEmpty(), "A non-finite number reports an error without closing the modal");
            capture(client, "analyzer-refresh-number-error.png"); click(client, "value"); type(client.currentScreen, "4.25"); click(client, "apply");
            require(client.currentScreen == studio && core.getNumber("kill-aura", "range") == 4.25, "Applying a precise finite value updates the setting and returns its exact Studio parent");
        });
        seek("number:kill-aura:range", client -> { click(client, "number:kill-aura:range"); });
        add("Numeric cancellation preserves its saved value", 4, client -> {
            type(client.currentScreen, "5.75"); capture(client, "analyzer-refresh-number.png"); click(client, "cancel");
            require(client.currentScreen == studio && core.getNumber("kill-aura", "range") == 4.25, "Cancel returns its exact parent without applying typed input");
            surfacesBefore = UiShader.surfaces(); click(client, "automation");
        });
        add("Automation opens the new shader console", 5, client -> {
            require(client.currentScreen instanceof AutomationScreen && UiShader.surfaces() > surfacesBefore, "Automation draws refreshed shader surfaces through its actual Studio entry");
            require(!AutomationEngine.INSTANCE.snapshot().running(), "Opening Automation does not execute a task");
            bounds(client, "field:0", "field:1", "field:2", "field:3", "field:4", "preset:5", "review", "back");
            click(client, "preset:5");
        });
        add("Native preset review", 3, client -> { click(client, "review"); });
        add("Review shows a real plan without execution", 4, client -> {
            AutomationScreen automation = (AutomationScreen) client.currentScreen;
            require(automation.reviewedPlan() != null && !automation.reviewedPlan().steps().isEmpty(), "The actual review action produces supported task stages");
            require(!AutomationEngine.INSTANCE.snapshot().running(), "Review remains separate from task execution");
            capture(client, "analyzer-refresh-automation-review.png");
            click(client, "field:0"); type(client.currentScreen, "unsupported studio fixture 927");
            client.currentScreen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
        });
        add("Automation rejects unsupported input", 4, client -> {
            require(((AutomationScreen) client.currentScreen).reviewedPlan() == null && !AutomationEngine.INSTANCE.snapshot().running(), "Unsupported input removes the reviewed plan and cannot start automation");
            click(client, "back"); require(client.currentScreen == studio, "Automation Back returns the exact Studio card context");
            surfacesBefore = UiShader.surfaces(); click(client, "profiles");
        });
        add("Profiles is a shader screen instead of Workspace", 5, client -> {
            require(!(client.currentScreen instanceof dev.analyzer.ui.AnalyzerScreen) && UiShader.surfaces() > surfacesBefore, "Profiles no longer opens the old Workspace renderer");
            bounds(client, "profile-name", "save-profile", "profiles-back");
            capture(client, "analyzer-refresh-profiles.png"); click(client, "profile-name"); type(client.currentScreen, PROFILE); click(client, "save-profile");
        });
        add("Native profile save persists the actual snapshot", 5, client -> {
            require(profiles(core).contains(PROFILE), "The actual profile Save action writes its temporary profile");
            core.setValue("kill-aura", "range", 3D); core.setValue("watermark", "logo", "A logo"); click(client, "load-selected");
        });
        add("Native profile load and delete retain existing profiles", 5, client -> {
            require(core.getNumber("kill-aura", "range") == 4.25, "The native Load action restores the saved precise setting");
            require(core.getChoice("watermark", "logo").equals("Analyzer logo"), "The native profile Load restores its saved full-watermark logo choice");
            click(client, "delete-selected");
        });
        add("Profile return preserves its exact parent", 4, client -> {
            require(!profiles(core).contains(PROFILE) && existingProfiles.equals(profileHashes()), "Deleting only the fixture leaves all existing profile bytes unchanged");
            click(client, "profiles-back"); require(client.currentScreen == studio, "Profiles Back returns its exact Studio parent");
            surfacesBefore = UiShader.surfaces(); click(client, "hud-editor"); editor = client.currentScreen;
        });
        add("HUD editor uses shader controls and live widget geometry", 5, client -> {
            require(editor instanceof HudEditorScreen && UiShader.surfaces() > surfacesBefore, "Studio opens the refreshed shader HUD editor");
            core.setEnabled("watermark", true); core.setEnabled("fps", true); core.setValue("hud", "show-disabled", true);
            rememberBrandCounts();
        });
        add("HUD editor actually drags the shader widget", 4, client -> {
            requireLogoFrames(core, "The default full-logo HUD editor preview");
            bounds(client, "widget:watermark", "theme", "selected-settings", "menu-settings", "done");
            originalWidgetX = HudWidgets.position(core, "watermark", true); originalWidgetY = HudWidgets.position(core, "watermark", false);
            double[] at = point(editor, "widget:watermark");
            editor.mouseClicked(at[0], at[1], GLFW.GLFW_MOUSE_BUTTON_LEFT);
            editor.mouseDragged(at[0] + 30, at[1] + 20, GLFW.GLFW_MOUSE_BUTTON_LEFT, 30, 20);
            editor.mouseReleased(at[0] + 30, at[1] + 20, GLFW.GLFW_MOUSE_BUTTON_LEFT);
            require(HudWidgets.position(core, "watermark", true) != originalWidgetX || HudWidgets.position(core, "watermark", false) != originalWidgetY,
                    "Dragging the rendered shader widget changes its actual normalized position");
            capture(client, "analyzer-refresh-hud-editor.png"); click(client, "selected-settings");
        });
        add("Selected HUD settings keep the current menu style", 5, client -> {
            require(client.currentScreen instanceof StudioScreen && ((CatalogScreen) client.currentScreen).selectedModuleId().equals("watermark"), "Selected widget Settings uses Studio rather than the old Workspace");
            client.currentScreen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
            require(client.currentScreen == editor, "Widget Settings Escape returns the exact editor");
        });
        add("Menu settings route keeps Studio", 4, client -> { click(client, "menu-settings"); });
        add("HUD Studio settings return to the editor", 5, client -> {
            require(client.currentScreen instanceof StudioScreen && ((CatalogScreen) client.currentScreen).selectedModuleId().equals("hud"), "Menu settings expands HUD Studio in the current Studio renderer");
            client.currentScreen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
            require(client.currentScreen == editor, "Menu settings Escape returns the exact editor"); click(client, "done");
            require(client.currentScreen == studio, "HUD editor Done returns the exact Studio parent");
            AnalyzerCore loaded = loadedCore();
            require(loaded.getNumber("watermark", "x") == core.getNumber("watermark", "x") && loaded.getNumber("watermark", "y") == core.getNumber("watermark", "y"), "The moved widget position survives a real config reload");
            core.setValue("menu", "home-enabled", true); core.setValue("menu", "home-background", "Panorama"); core.setValue("menu", "home-motion", 0D);
            wordmarkBefore = BrandAssets.wordmarkFrames(); monogramBefore = BrandAssets.monogramFrames();
            client.setScreen(new HomeScreen(core));
        });
        add("Supplied-brand Panorama Home remains usable", 5, client -> {
            require(client.currentScreen instanceof HomeScreen && HomeEffects.isHealthy(), "The Panorama Home still draws healthy native effects");
            require(BrandAssets.wordmarkFrames() > wordmarkBefore && BrandAssets.monogramFrames() > monogramBefore, "Home draws both supplied transparent logo assets in its actual native frame");
            require(client.currentScreen.children().stream().filter(element -> element instanceof ButtonWidget).map(element -> ((ButtonWidget) element).getMessage().getString()).noneMatch(text -> text.toLowerCase(Locale.ROOT).contains("analyzer")), "Actual Home navigation uses generic labels alongside the supplied logo assets");
            bounds(client, "home-worlds", "home-servers", "home-analyzer", "home-options", "home-background", "home-vanilla");
            capture(client, "analyzer-refresh-home-logo.png");
            core.setValue("menu", "home-background", "Orbit");
        });
        add("Supplied-brand Orbit Home", 5, client -> {
            require(HomeEffects.lastTime() == 0F && HomeEffects.isHealthy(), "Zero-motion Orbit uses a fixed actual shader time");
            capture(client, "analyzer-refresh-home-orbit-logo.png");
            core.setValue("menu", "home-background", "Panorama");
            for (String id : HudWidgets.IDS) core.setEnabled(id, false);
            core.setEnabled("hud", true); core.setEnabled("fps", true); HudWidgets.move(core, "fps", 4, 15);
            hudSurfacesBefore = HudWidgets.shaderSurfaces(); client.setScreen(null);
        });
        add("Real gameplay FPS shader widget renders", 5, client -> {
            require(HudWidgets.shaderSurfaces() > hudSurfacesBefore && HudWidgets.shaderWidgetFrames("fps") > 0, "With all menus closed, the gameplay FPS panel submits successful HUD shader surfaces");
            sampledWidget = HudWidgets.lastRenderedBounds().stream().filter(widget -> widget.id().equals("fps")).findFirst().orElseThrow();
            core.setEnabled("hud", false);
        });
        add("Read actual scene without HUD", 4, client -> { baselinePixels = samplePixels(client, sampledWidget); core.setEnabled("hud", true); });
        add("Shader HUD changes actual completed framebuffer pixels", 5, client -> {
            int[] visible = samplePixels(client, sampledWidget);
            double difference = 0;
            for (int i = 0; i < visible.length; i++) difference += Math.abs(visible[i] - baselinePixels[i]);
            difference /= Math.max(1, visible.length) * 255D;
            require(difference > .025, "The real HUD panel changes the completed scene pixels by " + difference);
            AnalyzerClient.LOGGER.info("ANALYZER_STUDIO_REFRESH_FRAMEBUFFER: fpsPanelMeanChannelDifference={}", difference);
            core.setValue("hud", "scale", .6D); viewport(client, 1920, 1080, 2);
            for (int i = 0; i < HudWidgets.IDS.size(); i++) { String id = HudWidgets.IDS.get(i); core.setEnabled(id, true); HudWidgets.move(core, id, i % 4 * 31D, i / 4 * 23D); }
            rememberWidgetCounts();
        });
        add("Open the real Watermark setting card", 2, client -> {
            studio = MenuScreens.create(core, "watermark"); client.setScreen(studio);
        });
        seek("setting:watermark:logo", client -> {
            require(core.getChoice("watermark", "logo").equals("Analyzer logo"), "The Watermark logo choice defaults to the full wordmark");
            click(client, "setting:watermark:logo");
        });
        add("Actual Watermark selector saves A logo", 4, client -> {
            require(core.getChoice("watermark", "logo").equals("A logo"), "The real Watermark settings control selects the A monogram");
            capture(client, "analyzer-watermark-settings.png");
            save(core); require(loadedCore().getChoice("watermark", "logo").equals("A logo"), "The selected A logo survives a real active-config reload");
            saveLogoProfile(core); client.setScreen(null); rememberBrandCounts();
        });
        add("Gameplay watermark renders only the selected A logo", 5, client -> {
            requireLogoFrames(core, "The gameplay A watermark");
            capture(client, "analyzer-watermark-a.png");
            editor = new HudEditorScreen(core, studio); client.setScreen(editor); rememberBrandCounts();
        });
        add("A editor preview keeps asset exclusivity", 5, client -> {
            requireLogoFrames(core, "The A-logo HUD editor preview");
            capture(client, "analyzer-watermark-editor-a.png");
            click(client, "done"); require(client.currentScreen == studio, "A-logo editing returns its exact Watermark settings card");
        });
        seek("setting:watermark:logo", client -> { click(client, "setting:watermark:logo"); });
        add("Actual Watermark selector restores the full logo", 4, client -> {
            require(core.getChoice("watermark", "logo").equals("Analyzer logo"), "The same native selector cycles back to the full wordmark");
            save(core); require(loadedCore().getChoice("watermark", "logo").equals("Analyzer logo"), "The full logo survives a real active-config reload");
            client.setScreen(null); rememberBrandCounts();
        });
        add("Gameplay watermark renders only the full wordmark", 5, client -> {
            requireLogoFrames(core, "The gameplay full watermark");
            capture(client, "analyzer-watermark-analyzer.png");
            editor = new HudEditorScreen(core, studio); client.setScreen(editor); rememberBrandCounts();
        });
        add("Full editor preview keeps asset exclusivity", 5, client -> {
            requireLogoFrames(core, "The full-logo HUD editor preview");
            capture(client, "analyzer-watermark-editor-analyzer.png");
            click(client, "done"); client.setScreen(null); core.setEnabled("watermark", false); rememberBrandCounts();
        });
        add("A disabled gameplay watermark draws no logo", 5, client -> {
            require(client.currentScreen == null && BrandAssets.wordmarkFrames() == wordmarkBefore && BrandAssets.monogramFrames() == monogramBefore
                    && !HudWidgets.lastRenderedWidgetIds().contains("watermark"), "Disabling the gameplay Watermark suppresses both logo assets and its panel");
            core.setEnabled("watermark", true);
        });
        for (String theme : List.of("Glass", "Classic", "Minimal", "Neon")) {
            add("Select independent HUD theme " + theme, 2, client -> {
                core.setValue("hud", "theme", theme); rememberWidgetCounts(); hudSurfacesBefore = HudWidgets.shaderSurfaces();
                wordmarkBefore = BrandAssets.wordmarkFrames(); monogramBefore = BrandAssets.monogramFrames();
            });
            add("Every actual HUD widget uses shaders for " + theme, 5, client -> {
                require(core.getChoice("menu", "theme").equals("Glass") && core.getChoice("hud", "theme").equals(theme), "HUD " + theme + " preserves its independent menu theme");
                require(HudWidgets.shaderSurfaces() > hudSurfacesBefore, "HUD " + theme + " submits new shader surfaces with menus closed");
                requireLogoFrames(core, "The gameplay watermark for " + theme);
                for (String id : HudWidgets.IDS) require(HudWidgets.shaderWidgetFrames(id) > widgetCounts.getOrDefault(id, 0L), "Actual shader widget rendered for " + theme + ": " + id);
                widgetBounds(client); capture(client, "analyzer-refresh-hud-" + theme.toLowerCase(Locale.ROOT) + ".png");
            });
        }
        add("Draw the real Entity ESP radar", 2, client -> {
            for (String id : HudWidgets.IDS) core.setEnabled(id, List.of("watermark", "fps", "coordinates").contains(id));
            HudWidgets.move(core, "watermark", 4, 4); HudWidgets.move(core, "fps", 4, 15); HudWidgets.move(core, "coordinates", 4, 94);
            core.setEnabled("entity-esp", true); core.setValue("entity-esp", "radar", true); core.setValue("entity-esp", "passives", true);
            HudWidgets.move(core, "radar", 78, 28); radarBefore = AnalyzerRenderer.radarShaderFrames();
        });
        add("Gameplay radar uses successful native shader surfaces", 5, client -> {
            require(AnalyzerRenderer.radarShaderFrames() > radarBefore, "The real selected Entity ESP radar submits successful shader panel frames");
            capture(client, "analyzer-refresh-radar.png"); core.setEnabled("entity-esp", false);
            for (int i = 0; i < HudWidgets.IDS.size(); i++) { String id = HudWidgets.IDS.get(i); core.setEnabled(id, true); HudWidgets.move(core, id, i % 4 * 31D, i / 4 * 23D); }
        });
        add("Draw safety banner through the actual HUD callback", 2, client -> {
            safetyBefore = SafetyHud.shaderFrames(); safetyFixture = true;
        });
        add("Safety banner uses real shader surfaces and physical bounds", 5, client -> {
            var banner = SafetyHud.lastBounds();
            require(SafetyHud.shaderFrames() > safetyBefore && banner != null && banner.x() >= 0 && banner.y() >= 0
                    && banner.x() + banner.width() <= client.getWindow().getFramebufferWidth()
                    && banner.y() + banner.height() <= client.getWindow().getFramebufferHeight(), "The native callback draws the shader safety banner inside its physical framebuffer");
            capture(client, "analyzer-refresh-safety-banner.png"); safetyFixture = false;
        });
        for (int[] size : new int[][]{{1280, 800}, {1920, 1080}}) for (int requestedGui : new int[]{2, 3, 4}) {
            String label = size[0] + "x" + size[1] + "-gui" + requestedGui;
            add("Resize real HUD " + label, 1, client -> { viewport(client, size[0], size[1], requestedGui); core.setValue("hud", "theme", "Glass"); client.setScreen(null); hudSurfacesBefore = HudWidgets.shaderSurfaces(); });
            add("Actual HUD bounds " + label, 5, client -> {
                require(client.getWindow().getFramebufferWidth() == size[0] && client.getWindow().getFramebufferHeight() == size[1] && HudWidgets.shaderSurfaces() > hudSurfacesBefore, "Real framebuffer/HUD submissions match " + label);
                widgetBounds(client); studio = MenuScreens.create(core, "hud"); client.setScreen(studio);
            });
            add("Auxiliary native controls fit " + label, 4, client -> { bounds(client, "profiles", "hud-editor", "automation", "appearance"); click(client, "automation"); });
            add("Automation shader controls fit " + label, 4, client -> { bounds(client, "field:0", "preset:5", "review", "back"); click(client, "back"); require(client.currentScreen == studio, "Resized Automation returns its exact parent at " + label); click(client, "hud-editor"); });
            add("Editor shader controls fit " + label, 4, client -> { bounds(client, "selected-settings", "menu-settings", "theme", "done"); if (size[0] == 1920 && requestedGui == 4) capture(client, "analyzer-refresh-editor-1920-gui4.png"); click(client, "done"); require(client.currentScreen == studio, "Resized editor returns its exact parent at " + label); click(client, "profiles"); });
            add("Profile shader controls fit " + label, 4, client -> { bounds(client, "profile-name", "save-profile", "profiles-back"); click(client, "profiles-back"); require(client.currentScreen == studio, "Resized Profiles returns its exact parent at " + label); });
        }
        add("Reload actual shaders with HUD active", 2, client -> { client.setScreen(null); hudSurfacesBefore = HudWidgets.shaderSurfaces(); client.reloadResources(); });
        await("Refreshed shaders survive resource reload", 8, client -> {
            if (client.getOverlay() != null || HudWidgets.shaderSurfaces() <= hudSurfacesBefore) return false;
            require(UiShader.isHealthy() && GpuEffects.isHealthy() && HomeEffects.isHealthy() && GL11.glGetError() == GL11.GL_NO_ERROR, "Resource reload restores healthy UI/HUD/Home shader state and clean GL");
            widgetBounds(client); capture(client, "analyzer-refresh-hud-reloaded.png"); return true;
        });
        add("Final input and data ownership", 3, client -> {
            GameplayEngine.Diagnostics after = GameplayEngine.diagnostics();
            require(after.attacks() == actionsBefore.attacks() && after.placements() == actionsBefore.placements() && after.inventoryClicks() == actionsBefore.inventoryClicks() && after.movementChanges() == actionsBefore.movementChanges(), "UI/HUD changes create no gameplay attacks, placements, inventory clicks or owned movement");
            require(!AutomationEngine.INSTANCE.snapshot().running() && existingProfiles.equals(profileHashes()), "The native acceptance starts no automation and preserves existing local profiles");
            safetyFixture = false;
            core.resetAll(); save(core); client.options.getGuiScale().setValue(originalGuiScale); client.onResolutionChanged();
        });
    }

    private static void rememberBrandCounts() { wordmarkBefore = BrandAssets.wordmarkFrames(); monogramBefore = BrandAssets.monogramFrames(); }
    private static void requireLogoFrames(AnalyzerCore core, String context) {
        MinecraftClient client = MinecraftClient.getInstance();
        require(client.currentScreen == null || client.currentScreen instanceof HudEditorScreen, context + " is checked without Home or Studio logo rendering");
        boolean monogram = core.getChoice("watermark", "logo").equals("A logo");
        require(monogram ? BrandAssets.monogramFrames() > monogramBefore && BrandAssets.wordmarkFrames() == wordmarkBefore
                : BrandAssets.wordmarkFrames() > wordmarkBefore && BrandAssets.monogramFrames() == monogramBefore,
                context + " advances only its selected asset's actual native frame counter");
    }
    private static void saveLogoProfile(AnalyzerCore core) {
        try {
            core.saveProfile(PROFILE); core.setValue("watermark", "logo", "Analyzer logo"); core.loadProfile(PROFILE); core.deleteProfile(PROFILE);
            require(core.getChoice("watermark", "logo").equals("A logo") && existingProfiles.equals(profileHashes()), "The A-logo profile roundtrip restores its choice and preserves every existing profile");
        } catch (IOException failure) { throw new IllegalStateException("Cannot roundtrip the isolated watermark profile", failure); }
    }
    private static void rememberWidgetCounts() { for (String id : HudWidgets.IDS) widgetCounts.put(id, HudWidgets.shaderWidgetFrames(id)); }
    private static void widgetBounds(MinecraftClient client) {
        List<HudWidgets.WidgetBounds> rendered = HudWidgets.lastRenderedBounds();
        require(rendered.size() == HudWidgets.IDS.size(), "All seventeen actual HUD widget bounds were produced");
        for (var widget : rendered) require(widget.width() > 0 && widget.height() > 0 && widget.x() >= 0 && widget.y() >= 0
                && widget.x() + widget.width() <= client.getWindow().getScaledWidth() + 1 && widget.y() + widget.height() <= client.getWindow().getScaledHeight() + 1,
                "Rendered HUD widget stays inside the native viewport: " + widget.id());
    }
    private static double[] point(Screen screen, String id) {
        if (screen == null) return null;
        try { return (double[]) screen.getClass().getMethod("controlPoint", String.class).invoke(screen, id); }
        catch (NoSuchMethodException exception) { throw new IllegalStateException("Refreshed screen lacks rendered-control diagnostics: " + screen.getClass().getSimpleName(), exception); }
        catch (IllegalAccessException | InvocationTargetException exception) { throw new IllegalStateException("Cannot read native control " + id, exception); }
    }
    private static void click(MinecraftClient client, String id) {
        Screen screen = client.currentScreen; double[] at = point(screen, id); require(at != null, "Actual rendered control exists: " + id);
        screen.mouseClicked(at[0], at[1], GLFW.GLFW_MOUSE_BUTTON_LEFT); screen.mouseReleased(at[0], at[1], GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }
    private static void bounds(MinecraftClient client, String... ids) {
        for (String id : ids) { double[] at = point(client.currentScreen, id); require(at != null && Double.isFinite(at[0]) && Double.isFinite(at[1])
                && at[0] >= 0 && at[1] >= 0 && at[0] < client.getWindow().getScaledWidth() && at[1] < client.getWindow().getScaledHeight(), "Actual refreshed hit center fits the native viewport: " + id); }
    }
    private static void type(Screen screen, String value) {
        screen.keyPressed(GLFW.GLFW_KEY_A, 0, GLFW.GLFW_MOD_CONTROL); screen.keyPressed(GLFW.GLFW_KEY_BACKSPACE, 0, 0);
        // Native text fields read real GLFW modifier state; clear bounded input without
        // synthesizing operating-system key state or touching the user's clipboard.
        for (int i = 0; i < 520; i++) screen.keyPressed(GLFW.GLFW_KEY_BACKSPACE, 0, 0);
        for (char character : value.toCharArray()) screen.charTyped(character, 0);
    }
    private static void seek(String id, Consumer<MinecraftClient> action) {
        await("Reach actual control " + id, 3, client -> {
            if (point(client.currentScreen, id) == null) { double[] at = point(client.currentScreen, "settings-scroll"); require(at != null, "The real selected card has a scroll viewport"); client.currentScreen.mouseScrolled(at[0], at[1], 0, -2); return false; }
            action.accept(client); return true;
        });
    }
    /** Samples the real panel interior using physical coordinates derived from its live HUD bounds. */
    private static int[] samplePixels(MinecraftClient client, HudWidgets.WidgetBounds bounds) {
        double scale = client.getWindow().getScaleFactor(); int width = client.getFramebuffer().textureWidth, height = client.getFramebuffer().textureHeight;
        int x0 = Math.clamp((int) Math.round((bounds.x() + bounds.width() * .15) * scale), 0, width - 1);
        int x1 = Math.clamp((int) Math.round((bounds.x() + bounds.width() * .85) * scale), x0 + 1, width);
        int y0 = Math.clamp((int) Math.round((bounds.y() + bounds.height() * .72) * scale), 0, height - 1);
        int y1 = Math.clamp((int) Math.round((bounds.y() + bounds.height() * .86) * scale), y0 + 1, height);
        return readPixels(client, x0, y0, x1, y1);
    }
    /** A native-background regression used to blur the modal's already-rendered title. */
    private static double numericTitleEdgeFraction(MinecraftClient client) {
        double[] apply = point(client.currentScreen, "apply"), cancel = point(client.currentScreen, "cancel");
        double canvas = (cancel[0] - apply[0]) / 206D;
        double left = apply[0] - 117 * canvas, top = apply[1] - 169 * canvas, nativeScale = client.getWindow().getScaleFactor();
        int x0 = (int) Math.round((left + 20 * canvas) * nativeScale), x1 = (int) Math.round((left + 420 * canvas) * nativeScale);
        int y0 = (int) Math.round((top + 20 * canvas) * nativeScale), y1 = (int) Math.round((top + 43 * canvas) * nativeScale);
        int[] pixels = readPixels(client, x0, y0, x1, y1); int width = x1 - x0, height = y1 - y0, edges = 0, samples = 0;
        for (int y = 0; y < height - 1; y++) for (int x = 0; x < width - 1; x++) {
            int offset = (y * width + x) * 3, difference = 0;
            for (int channel = 0; channel < 3; channel++) difference = Math.max(difference, Math.max(Math.abs(pixels[offset + channel] - pixels[offset + channel + 3]), Math.abs(pixels[offset + channel] - pixels[offset + channel + width * 3])));
            if (difference > 72) edges++; samples++;
        }
        double fraction = edges / (double) Math.max(1, samples);
        AnalyzerClient.LOGGER.info("ANALYZER_STUDIO_REFRESH_FRAMEBUFFER: numericTitleSharpEdgeFraction={}", fraction); return fraction;
    }
    private static int[] readPixels(MinecraftClient client, int x0, int y0, int x1, int y1) {
        int height = client.getFramebuffer().textureHeight;
        int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING), pack = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        int alignment = GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT), rows = GL11.glGetInteger(GL11.GL_PACK_ROW_LENGTH), skipRows = GL11.glGetInteger(GL11.GL_PACK_SKIP_ROWS), skipPixels = GL11.glGetInteger(GL11.GL_PACK_SKIP_PIXELS);
        int areaWidth = x1 - x0, areaHeight = y1 - y0; ByteBuffer pixels = MemoryUtil.memAlloc(areaWidth * areaHeight * 4);
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, client.getFramebuffer().fbo); GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1); GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, 0); GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, 0); GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, 0);
            GL11.glReadPixels(x0, height - y1, areaWidth, areaHeight, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
            int[] result = new int[areaWidth * areaHeight * 3];
            for (int i = 0; i < areaWidth * areaHeight; i++) for (int channel = 0; channel < 3; channel++) result[i * 3 + channel] = pixels.get(i * 4 + channel) & 255;
            require(GL11.glGetError() == GL11.GL_NO_ERROR, "Native HUD framebuffer read leaves clean GL state"); return result;
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read); GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, pack);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, alignment); GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, rows); GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, skipRows); GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, skipPixels); MemoryUtil.memFree(pixels);
        }
    }
    private static Map<String, String> profileHashes() {
        Path directory = FabricLoader.getInstance().getConfigDir().resolve("analyzer/profiles"); Map<String, String> hashes = new HashMap<>();
        if (!Files.isDirectory(directory)) return hashes;
        try (var files = Files.walk(directory)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) hashes.put(directory.relativize(file).toString(), HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file))));
            return Map.copyOf(hashes);
        } catch (Exception failure) { throw new IllegalStateException("Cannot inspect isolated existing profile hashes", failure); }
    }
    private static List<String> profiles(AnalyzerCore core) { try { return core.profiles(); } catch (IOException failure) { throw new IllegalStateException(failure); } }
    private static AnalyzerCore loadedCore() { var core = new AnalyzerCore(FabricLoader.getInstance().getConfigDir().resolve("analyzer")); try { core.load(); } catch (IOException failure) { throw new IllegalStateException(failure); } return core; }
    private static void save(AnalyzerCore core) { try { core.save(); } catch (IOException failure) { throw new IllegalStateException(failure); } }
    private static void command(MinecraftClient client, String value) { client.getServer().execute(() -> client.getServer().getCommandManager().executeWithPrefix(client.getServer().getCommandSource().withSilent(), value)); }
    private static void viewport(MinecraftClient client, int width, int height, int gui) { GLFW.glfwSetWindowSize(client.getWindow().getHandle(), width, height); client.options.getGuiScale().setValue(gui); client.onResolutionChanged(); }
    private static void add(String label, int waitTicks, Consumer<MinecraftClient> action) { steps.add(new Step(label, waitTicks, client -> { action.accept(client); return true; })); }
    private static void await(String label, int waitTicks, Predicate<MinecraftClient> action) { steps.add(new Step(label, waitTicks, action)); }
    private static void require(boolean condition, String label) { if (!condition) throw new IllegalStateException("Studio refresh check " + phase + ": " + label); assertions++; AnalyzerClient.LOGGER.info("ANALYZER_STUDIO_REFRESH_ASSERT: {}", label); }
    private static void capture(MinecraftClient client, String name) { require(UiShader.isHealthy() && GL11.glGetError() == GL11.GL_NO_ERROR, "Actual shader/GL state before " + name); ScreenshotRecorder.saveScreenshot(client.runDirectory, name, client.getFramebuffer(), message -> AnalyzerClient.LOGGER.info("Studio refresh screenshot: {}", message.getString())); screenshots++; }
    private static void finish(MinecraftClient client) { complete = true; AnalyzerClient.LOGGER.info("ANALYZER_STUDIO_REFRESH_SMOKE_COMPLETE: {} assertions, {} screenshots, {} phases, {} ticks; native auxiliary routes, precise numeric errors/apply/cancel, profile CRUD with preservation, HUD drag/return/persistence, exclusive Watermark selector/assets/disabled/config/profile roundtrips, four real HUD shader themes, framebuffer delta, six window/GUI cases, supplied-brand Home, zero motion and reload", assertions, screenshots, steps.size(), ticks); client.scheduleStop(); }
    private record Step(String label, int waitTicks, Predicate<MinecraftClient> action) { }
}
