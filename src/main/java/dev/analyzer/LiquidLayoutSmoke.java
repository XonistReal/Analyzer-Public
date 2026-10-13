package dev.analyzer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.analyzer.automation.AutomationEngine;
import dev.analyzer.core.AnalyzerCore;
import dev.analyzer.core.Category;
import dev.analyzer.effects.GpuEffects;
import dev.analyzer.effects.LiquidGlass;
import dev.analyzer.effects.UiShader;
import dev.analyzer.ui.BrandAssets;
import dev.analyzer.ui.CatalogScreen;
import dev.analyzer.ui.HudEditorScreen;
import dev.analyzer.ui.HudWidgets;
import dev.analyzer.ui.MenuScreens;
import dev.analyzer.ui.NumericSettingScreen;
import dev.analyzer.ui.ProfilesScreen;
import dev.analyzer.ui.StudioScreen;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Opt-in native acceptance for retired-layout migration and the scene-backed glass. */
final class LiquidLayoutSmoke {
    private static final String PROFILE = "liquid-legacy-hvh-" + ProcessHandle.current().pid();
    private static final List<Step> STEPS = new ArrayList<>();
    private static final List<String> CAPTURES = new ArrayList<>();
    private static int phase, entered, ticks, assertions, originalGui;
    private static boolean started, finished;
    private static Screen studio, clickGui, editor, modal;
    private static Map<String, String> oldProfiles;
    private static byte[] fixtureBytes;
    private static long surfacesBefore, copiesBefore, completedBefore, wordmarkBefore, monogramBefore;
    private static double[] frozenPoint, panelBefore;
    private static OpticalFixture optics;
    private static int[] nakedPixels, plainPixels, refractedPixels, repeatedPixels;
    private static double warpDifference, outsideDifference, edgeDisplacement;

    private LiquidLayoutSmoke() { }

    static void tick(MinecraftClient client, int now) {
        if (finished) return;
        ticks = now;
        if (!started) { started = true; build(client); }
        if (phase >= STEPS.size()) { finish(client); return; }
        Step step = STEPS.get(phase);
        if (now - entered > 200) throw new IllegalStateException("Liquid/layout timeout at " + phase + ": " + step.label);
        if (now > 2400) throw new IllegalStateException("Liquid/layout exceeded its bounded native run");
        if (now - entered < step.waitTicks || !step.action.test(client)) return;
        AnalyzerClient.LOGGER.info("Liquid/layout native phase complete: {} / {}", phase, step.label);
        phase++; entered = now;
    }

    private static void build(MinecraftClient mc) {
        AnalyzerCore core = AnalyzerClient.CORE;
        originalGui = mc.options.getGuiScale().getValue(); oldProfiles = profileHashes();
        add("Quiet isolated creative world", 0, client -> {
            AutomationEngine.INSTANCE.stop(); core.resetAll(); quiet(core);
            require(!Files.exists(profileFile()), "The new isolated profile name cannot replace an existing profile");
            core.setValue("menu", "layout", "Studio"); core.setValue("menu", "theme", "Glass");
            core.setValue("menu", "material", "Glass"); core.setValue("menu", "motion", 0D);
            core.setValue("menu", "accent", "Gold"); core.setValue("hud", "theme", "Minimal");
            command(client, "gamerule doDaylightCycle false"); command(client, "time set 1000"); command(client, "weather clear");
            client.inGameHud.getChatHud().clear(false); client.getToastManager().clear();
            viewport(client, 1280, 800, 2);
            makeLegacyFixture(client);
            studio = MenuScreens.create(core, "kill-aura"); client.setScreen(studio); surfacesBefore = LiquidGlass.surfaceFrames();
        });
        add("Studio scene-backed glass", 5, client -> {
            require(client.currentScreen instanceof StudioScreen && LiquidGlass.surfaceFrames() > surfacesBefore,
                    "Studio draws successful liquid glass instead of alpha-only surfaces");
            require(core.getChoice("hud", "theme").equals("Minimal"), "The Glass menu does not replace the independent HUD theme");
            bounds(client, "profiles", "hud-editor", "automation", "appearance", "settings-scroll");
            frozenPoint = point(client.currentScreen, "settings-scroll");
            capture(client, "analyzer-liquid-studio-glass.png"); click(client, "profiles");
        });
        seekProfile(PROFILE, client -> {
            click(client, "load-profile:" + PROFILE);
            JsonObject modules = JsonParser.parseString(new String(fixtureBytes, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("modules");
            require(core.isEnabled("kill-aura") == modules.getAsJsonObject("kill-aura").get("enabled").getAsBoolean(), "Old HVH module flags survive layout migration");
            require(core.getNumber("kill-aura", "range") == modules.getAsJsonObject("kill-aura").getAsJsonObject("settings").get("range").getAsDouble(), "Old HVH gameplay settings survive layout migration");
            // Inspect flags synchronously after native Load, then suppress arena combat
            // before another gameplay tick. This fixture validates UI ownership only.
            quiet(core);
        });
        add("Load the old HVH premade through its actual Profiles button", 4, client -> {
            require(client.currentScreen instanceof ProfilesScreen, "A premade profile loads in the actual Profiles screen");
            require(core.getChoice("menu", "layout").equals("Studio"), "The old HVH Classic layout migrates to Studio on native Load");
            require(Arrays.equals(fixtureBytes, read(profileFile())), "Loading the old profile preserves its original source bytes");
            quiet(core); core.setValue("menu", "theme", "Glass"); core.setValue("menu", "material", "Glass"); core.setValue("menu", "motion", 0D);
            click(client, "profiles-back");
            require(client.currentScreen == studio && client.currentScreen instanceof StudioScreen,
                    "Back to modules after old HVH Load returns the exact Studio parent");
        });
        add("Every fresh bundled profile defaults to Studio", 3, client -> {
            for (String name : List.of("cinematic", "classic", "neon", "studio", "hvh")) {
                JsonObject document = bundled(client, name);
                require(document.getAsJsonObject("modules").getAsJsonObject("menu").getAsJsonObject("settings").get("layout").getAsString().equals("Studio"),
                        "Current bundled " + name + " profile cannot restore a removed layout");
            }
        });
        add("Studio renders after the legacy HVH parent return", 4, client -> {
            require(client.currentScreen instanceof StudioScreen, "The next rendered frame after old HVH Back remains Studio");
            capture(client, "analyzer-liquid-legacy-hvh-return.png");
            ((CatalogScreen) client.currentScreen).inspect("hud");
        });
        seek("setting:hud:menu-layout", client -> { click(client, "setting:hud:menu-layout"); });
        add("Actual HUD Studio selector opens the new shader Click GUI", 5, client -> {
            clickGui = client.currentScreen;
            require(core.getChoice("menu", "layout").equals("Click GUI") && clickGui.getClass().getSimpleName().equals("ClickGuiScreen"),
                    "The native HUD Studio menu selector offers only the new Click GUI alternative");
            require(MenuScreens.matchesLayout(core, clickGui), "The saved Click GUI choice resolves to its actual shader screen");
            bounds(client, "profiles", "hud-editor", "automation", "appearance");
            capture(client, "analyzer-liquid-click-gui.png");
        });
        add("Reach the wide board's real pan controls", 2, client -> {
            bounds(client, "board-scroll", "board-previous", "board-next", "reset-columns");
            click(client, "reset-columns"); click(client, "category:COMBAT");
        });
        add("Pan the board with its native next control", 4, client -> {
            bounds(client, "header:COMBAT"); click(client, "board-next");
        });
        add("The wide board moves its category hit targets", 4, client -> {
            require(point(clickGui, "header:COMBAT") == null, "Panning the real wide board removes the left Combat column from the clipped viewport");
            click(client, "board-previous");
        });
        add("Previous restores the actual left category", 4, client -> {
            bounds(client, "header:COMBAT"); panelBefore = point(clickGui, "header:COMBAT");
            double[] at = point(clickGui, "board-scroll"); clickGui.mouseScrolled(at[0], at[1], -.35, 0);
        });
        add("Native horizontal scrolling pans the same board", 4, client -> {
            double[] after = point(clickGui, "header:COMBAT");
            require(after != null && Math.abs(after[0] - panelBefore[0]) > 4,
                    "A genuine horizontal scroll event moves the actual category columns");
        });
        for (Category category : Category.values()) {
            add("Jump to native Click GUI category " + category.name(), 2, client -> { click(client, "category:" + category.name()); });
            add("Reach actual category panel " + category.name(), 4, client -> {
                bounds(client, "header:" + category.name(), "collapse:" + category.name());
                require(((CatalogScreen) clickGui).visibleModuleIds().stream().anyMatch(id -> core.definition(id).category() == category), "Category navigation exposes real " + category.label() + " modules");
            });
        }
        add("Collapse and drag a real category panel", 2, client -> {
            click(client, "category:COMBAT");
        });
        add("Collapsed panel suppresses its module rows", 4, client -> {
            click(client, "collapse:COMBAT");
        });
        add("Restore the category and begin its actual drag", 4, client -> {
            require(point(clickGui, "module:kill-aura") == null, "A collapsed Combat panel has no live Kill Aura row hit target");
            click(client, "collapse:COMBAT"); panelBefore = point(clickGui, "header:COMBAT");
        });
        add("Move the live panel by its header", 4, client -> {
            double[] at = point(clickGui, "header:COMBAT"); require(at != null, "The category header exists before dragging");
            clickGui.mouseClicked(at[0], at[1], GLFW.GLFW_MOUSE_BUTTON_LEFT);
            clickGui.mouseDragged(at[0] + 35, at[1] + 18, GLFW.GLFW_MOUSE_BUTTON_LEFT, 35, 18);
            clickGui.mouseReleased(at[0] + 35, at[1] + 18, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        });
        add("A dragged category keeps genuine settings controls", 4, client -> {
            double[] moved = point(clickGui, "header:COMBAT");
            require(moved != null && Math.hypot(moved[0] - panelBefore[0], moved[1] - panelBefore[1]) > 8,
                    "Dragging the native category header moves its rendered panel and hit targets");
            require(core.getNumber("menu", "classic-combat-x") != 74D || core.getNumber("menu", "classic-combat-y") != 25D,
                    "The new shader column drag writes real compatible saved placement fields");
            ((CatalogScreen) clickGui).inspect("kill-aura");
        });
        seek("number:kill-aura:range", client -> { click(client, "number:kill-aura:range"); modal = client.currentScreen; });
        add("Type a precise setting from the new Click GUI", 4, client -> {
            require(modal instanceof NumericSettingScreen, "A Click GUI numeric setting opens the real native value modal");
            bounds(client, "value", "apply", "cancel"); click(client, "value"); type(client.currentScreen, "4.25"); click(client, "apply");
            require(client.currentScreen == clickGui && core.getNumber("kill-aura", "range") == 4.25, "Precise input saves genuine module data and returns its exact Click GUI parent");
            save(core); AnalyzerCore reloaded = loadedCore();
            require(reloaded.getChoice("menu", "layout").equals("Click GUI") && reloaded.getNumber("kill-aura", "range") == 4.25
                    && reloaded.getNumber("menu", "classic-combat-x") == core.getNumber("menu", "classic-combat-x")
                    && reloaded.getNumber("menu", "classic-combat-y") == core.getNumber("menu", "classic-combat-y"),
                    "The new layout, typed setting and dragged category placement survive a real configuration reload");
        });
        add("Click GUI Profiles ownership", 4, client -> { click(client, "profiles"); });
        add("Profiles returns the exact new Click GUI", 4, client -> {
            require(client.currentScreen instanceof ProfilesScreen, "The new Click GUI opens the real Profiles surface");
            click(client, "profiles-back"); require(client.currentScreen == clickGui, "Profiles Back preserves the exact Click GUI instance");
            click(client, "hud-editor"); editor = client.currentScreen;
        });
        add("HUD editor ownership", 4, client -> {
            require(editor instanceof HudEditorScreen, "Click GUI opens the real HUD editor"); bounds(client, "selected-settings", "menu-settings", "done"); click(client, "selected-settings");
        });
        add("Editor settings preserve Click GUI", 4, client -> {
            require(client.currentScreen.getClass().getSimpleName().equals("ClickGuiScreen"), "HUD Settings retains the selected new shader layout");
            client.currentScreen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0); require(client.currentScreen == editor, "Settings Escape returns the exact HUD editor");
            click(client, "done"); require(client.currentScreen == clickGui, "HUD Done returns the exact Click GUI parent");
            core.setValue("menu", "material", "Solid"); core.setValue("menu", "theme", "Glass"); surfacesBefore = LiquidGlass.surfaceFrames();
        });
        add("Liquid Glass theme is independent of Solid backdrop material", 5, client -> {
            require(core.getChoice("menu", "material").equals("Solid") && LiquidGlass.surfaceFrames() > surfacesBefore,
                    "Glass theme still draws real liquid glass when backdrop material is Solid");
            capture(client, "analyzer-liquid-click-gui-solid-backdrop.png");
            core.setValue("menu", "theme", "Classic"); core.setValue("menu", "material", "Glass"); surfacesBefore = LiquidGlass.surfaceFrames();
        });
        add("Backdrop Glass does not force the Classic theme into liquid glass", 5, client -> {
            require(core.getChoice("menu", "theme").equals("Classic") && LiquidGlass.surfaceFrames() == surfacesBefore,
                    "Glass backdrop material retains the independently selected opaque Classic theme");
            core.setValue("menu", "theme", "Glass"); core.setValue("menu", "material", "Glass");
        });
        for (int[] size : new int[][]{{1280, 800}, {1920, 1080}}) for (int gui : new int[]{2, 3, 4}) {
            String suffix = size[0] + "x" + size[1] + "-gui" + gui;
            add("Resize native Click GUI " + suffix, 1, client -> { viewport(client, size[0], size[1], gui); ((CatalogScreen) clickGui).inspect("hud"); });
            add("Resized category navigation fits " + suffix, 5, client -> {
                require(client.getWindow().getFramebufferWidth() == size[0] && client.getWindow().getFramebufferHeight() == size[1], "Native framebuffer matches " + suffix);
                bounds(client, "profiles", "hud-editor", "automation", "appearance", "category:CLIENT", "category:HVH");
                click(client, "category:CLIENT");
            });
            add("Resized panel remains accessible " + suffix, 4, client -> { bounds(client, "header:CLIENT", "collapse:CLIENT"); click(client, "category:HVH"); });
            add("Resized HVH controls remain accessible " + suffix, 4, client -> {
                bounds(client, "header:HVH", "collapse:HVH");
                if (gui == 4) capture(client, "analyzer-liquid-click-" + suffix + ".png");
                core.setValue("menu", "layout", "Studio"); studio = MenuScreens.create(core, "kill-aura"); client.setScreen(studio);
            });
            add("Resized Studio keeps liquid surfaces " + suffix, 4, client -> {
                bounds(client, "profiles", "hud-editor", "automation", "appearance", "settings-scroll");
                core.setValue("menu", "layout", "Click GUI"); clickGui = MenuScreens.create(core, "hud"); client.setScreen(clickGui);
            });
        }
        add("Set up actual native optical calibration scene", 2, client -> {
            viewport(client, 1280, 800, 2); optics = new OpticalFixture(); client.setScreen(optics); optics.glass = false;
        });
        add("Read unchanged high-contrast native source", 5, client -> {
            nakedPixels = readPixels(client, 0, 0, client.getFramebuffer().textureWidth, client.getFramebuffer().textureHeight);
            optics.glass = true; LiquidGlass.setRefractionEnabled(false);
        });
        add("Read actual glass without scene displacement", 5, client -> {
            require(optics.frames > 1 && optics.successfulFrames > 0, "The calibration is a genuinely rendered native shader frame");
            plainPixels = readPixels(client, 0, 0, client.getFramebuffer().textureWidth, client.getFramebuffer().textureHeight);
            capture(client, "analyzer-liquid-optics-no-refraction.png"); LiquidGlass.setRefractionEnabled(true);
        });
        add("Compare actual immutable-scene lens refraction", 5, client -> {
            refractedPixels = readPixels(client, 0, 0, client.getFramebuffer().textureWidth, client.getFramebuffer().textureHeight);
            int w = client.getFramebuffer().textureWidth, h = client.getFramebuffer().textureHeight;
            warpDifference = meanDifference(plainPixels, refractedPixels, w, h, .13, .20, .87, .76);
            outsideDifference = meanDifference(plainPixels, refractedPixels, w, h, .02, .02, .10, .90);
            edgeDisplacement = edgeShift(plainPixels, refractedPixels, w, h);
            require(warpDifference > .002, "Refraction toggle changes real captured-scene pixels with identical source and compositing opacity: " + warpDifference);
            require(edgeDisplacement >= 1.5, "A genuine high-contrast scene edge moves through the native curved lens by " + edgeDisplacement + " framebuffer pixels");
            require(outsideDifference < .00001, "Pixels outside the glass lens remain exactly stationary: " + outsideDifference);
            double corner = meanDifference(nakedPixels, refractedPixels, w, h, .131, .201, .136, .208);
            require(corner < .025, "Rounded glass corners clip the scene-backed body instead of drawing a rectangular alpha sheet: " + corner);
            AnalyzerClient.LOGGER.info("ANALYZER_LIQUID_FRAMEBUFFER: interiorMeanDifference={} outsideMeanDifference={} edgeMovementPixels={} clippedCornerDifference={}", warpDifference, outsideDifference, edgeDisplacement, corner);
            capture(client, "analyzer-liquid-optics-refraction.png");
        });
        add("Native optical material remains stationary across frames", 5, client -> {
            repeatedPixels = readPixels(client, 0, 0, client.getFramebuffer().textureWidth, client.getFramebuffer().textureHeight);
            require(Arrays.equals(refractedPixels, repeatedPixels), "The shader has no hidden time drift when the native source and motion are unchanged");
            require(!LiquidGlass.isFrameActive() && GL11.glGetError() == GL11.GL_NO_ERROR, "The completed native lens frame closes ownership and leaves clean GL");
            for (String id : HudWidgets.IDS) core.setEnabled(id, false);
            core.setEnabled("watermark", true); core.setEnabled("fps", true); core.setEnabled("coordinates", true); core.setEnabled("hud", true);
            HudWidgets.move(core, "watermark", 4, 4); HudWidgets.move(core, "fps", 4, 18); HudWidgets.move(core, "coordinates", 4, 94);
            core.setValue("menu", "theme", "Glass"); client.setScreen(null);
        });
        for (String theme : List.of("Glass", "Classic", "Minimal", "Neon")) for (String logo : List.of("Analyzer logo", "A logo")) {
            add("Select HUD " + theme + " / " + logo, 2, client -> {
                core.setValue("hud", "theme", theme); core.setValue("watermark", "logo", logo);
                wordmarkBefore = BrandAssets.wordmarkFrames(); monogramBefore = BrandAssets.monogramFrames(); surfacesBefore = LiquidGlass.surfaceFrames();
            });
            add("Real HUD retains selected logo and independent theme", 5, client -> {
                boolean a = logo.equals("A logo");
                require(client.currentScreen == null && (a ? BrandAssets.monogramFrames() > monogramBefore && BrandAssets.wordmarkFrames() == wordmarkBefore
                        : BrandAssets.wordmarkFrames() > wordmarkBefore && BrandAssets.monogramFrames() == monogramBefore), "The actual " + theme + " gameplay watermark renders only " + logo);
                require(core.getChoice("menu", "theme").equals("Glass"), "Changing the gameplay HUD theme preserves menu Glass");
                require(theme.equals("Glass") ? LiquidGlass.surfaceFrames() > surfacesBefore : LiquidGlass.surfaceFrames() == surfacesBefore,
                        "Only the independent Glass HUD theme uses liquid refraction surfaces");
                if (theme.equals("Glass")) capture(client, "analyzer-liquid-hud-" + (a ? "a" : "analyzer") + ".png");
            });
        }
        add("Reload actual glass resources", 2, client -> {
            core.setValue("menu", "layout", "Studio"); core.setValue("menu", "theme", "Glass"); core.setValue("hud", "theme", "Glass");
            studio = MenuScreens.create(core, "kill-aura"); client.setScreen(studio); copiesBefore = LiquidGlass.sceneCopies(); client.reloadResources();
        });
        await("Original optical shader recompiles after resource reload", 8, client -> {
            if (client.getOverlay() != null || LiquidGlass.sceneCopies() <= copiesBefore) return false;
            require(LiquidGlass.isHealthy() && UiShader.isHealthy() && GpuEffects.isHealthy() && GL11.glGetError() == GL11.GL_NO_ERROR,
                    "Native UI and scene-backed liquid shaders survive resource reload with clean GL");
            bounds(client, "settings-scroll", "profiles", "hud-editor"); frozenPoint = point(studio, "settings-scroll");
            copiesBefore = LiquidGlass.sceneCopies(); completedBefore = LiquidGlass.completedFrames(); surfacesBefore = LiquidGlass.surfaceFrames(); return true;
        });
        add("Zero-motion Studio preserves coordinates and per-frame capture ownership", 5, client -> {
            double[] next = point(studio, "settings-scroll");
            require(next != null && Math.abs(next[0] - frozenPoint[0]) < .00001 && Math.abs(next[1] - frozenPoint[1]) < .00001,
                    "Zero menu motion preserves exact native setting control geometry after reload");
            require(LiquidGlass.sceneCopies() - copiesBefore == LiquidGlass.completedFrames() - completedBefore && !LiquidGlass.isFrameActive(),
                    "Real UI captures precisely one immutable scene per completed liquid frame");
            require(LiquidGlass.sceneCopies() > copiesBefore && LiquidGlass.surfaceFrames() - surfacesBefore > (LiquidGlass.sceneCopies() - copiesBefore) * 3,
                    "The actual Studio frames reuse each captured scene across multiple rendered surfaces");
            capture(client, "analyzer-liquid-studio-reloaded.png");
            require(Arrays.equals(fixtureBytes, read(profileFile())), "The native old-profile regression still leaves its legacy JSON unchanged");
            deleteFixture(); require(oldProfiles.equals(profileHashes()), "The entire isolated pre-existing profile set retains its original bytes");
            require(!AutomationEngine.INSTANCE.snapshot().running(), "No navigation or shader operation starts automation");
            core.resetAll(); save(core); client.options.getGuiScale().setValue(originalGui); client.onResolutionChanged();
        });
    }

    private static void quiet(AnalyzerCore core) { for (var definition : core.modules()) core.setEnabled(definition.id(), false); core.setEnabled("menu", true); core.setEnabled("hud", true); }
    private static Path profileFile() { return FabricLoader.getInstance().getConfigDir().resolve("analyzer/profiles/" + PROFILE + ".json"); }
    private static void makeLegacyFixture(MinecraftClient client) {
        JsonObject document = bundled(client, "hvh");
        document.getAsJsonObject("modules").getAsJsonObject("menu").getAsJsonObject("settings").addProperty("layout", "Classic");
        fixtureBytes = document.toString().getBytes(StandardCharsets.UTF_8);
        try { Files.createDirectories(profileFile().getParent()); Files.write(profileFile(), fixtureBytes, java.nio.file.StandardOpenOption.CREATE_NEW); }
        catch (IOException failure) { throw new IllegalStateException("Cannot create isolated historical HVH profile", failure); }
    }
    private static JsonObject bundled(MinecraftClient client, String name) {
        try (var stream = client.getResourceManager().getResourceOrThrow(Identifier.of("analyzer", "profiles/" + name + ".json")).getInputStream()) {
            return JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException failure) { throw new IllegalStateException("Cannot read actual bundled premade " + name, failure); }
    }
    private static byte[] read(Path path) { try { return Files.readAllBytes(path); } catch (IOException failure) { throw new IllegalStateException(failure); } }
    private static void deleteFixture() { try { Files.delete(profileFile()); } catch (IOException failure) { throw new IllegalStateException(failure); } }

    /** Actual native diagnostic screen; no generated images or simulated shader result. */
    private static final class OpticalFixture extends Screen {
        private boolean glass;
        private int frames, successfulFrames;
        private int foreignReadFramebuffer, foreignTexture;
        OpticalFixture() { super(Text.literal("Native liquid optical calibration")); }
        @Override public boolean shouldPause() { return false; }
        @Override public void render(DrawContext draw, int mouseX, int mouseY, float delta) {
            frames++;
            for (int x = 0; x < width; x++) {
                int stripe = Math.floorDiv(x * 1000 / Math.max(1, width), 26) % 4;
                draw.fill(x, 0, x + 1, height, switch (stripe) { case 0 -> 0xFFEFEAE2; case 1 -> 0xFF102647; case 2 -> 0xFFED9441; default -> 0xFF296F86; });
            }
            if (!glass) return;
            draw.draw();
            int originalRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING), originalActive = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            GL13.glActiveTexture(GL13.GL_TEXTURE3); int originalTexture3 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            ensureForeignReadTarget();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, foreignTexture); GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, foreignReadFramebuffer);
            GlState incoming = GlState.capture(); long copied = LiquidGlass.sceneCopies();
            try {
                require(incoming.readFbo != incoming.drawFbo && incoming.active == GL13.GL_TEXTURE3,
                        "The optical fixture actually supplies a distinct READ target and nonzero active texture unit");
                require(LiquidGlass.beginFrame(draw), "Native calibration copies the actual scene successfully");
                require(LiquidGlass.beginFrame(draw) && LiquidGlass.sceneCopies() == copied + 1, "A repeated begin cannot capture its own UI or allocate a second source copy");
                require(LiquidGlass.lastSourceFramebuffer() == incoming.drawFbo,
                        "The captured glass source is the real native DRAW scene even when another READ framebuffer is bound");
                require(incoming.equals(GlState.capture()), "Capturing liquid source preserves native framebuffer, program, texture, scissor and viewport state");
                require(LiquidGlass.surface(draw, width * .13F, height * .20F, width * .74F, height * .56F, width * .064F, 0xFFF4D99A, .95F, 1.4F), "The real calibration lens submits successfully");
                require(LiquidGlass.surface(draw, width * .34F, height * .85F, width * .32F, height * .07F, height * .035F, 0xFFF4D99A, .95F, 1.4F), "A second glass surface samples the same immutable scene");
                require(LiquidGlass.sceneCopies() == copied + 1 && incoming.equals(GlState.capture()), "Multiple surfaces keep one scene copy and restore complete incoming GL state");
                successfulFrames++;
            } finally {
                LiquidGlass.endFrame(); GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, originalRead);
                GL13.glActiveTexture(GL13.GL_TEXTURE3); GL11.glBindTexture(GL11.GL_TEXTURE_2D, originalTexture3); GL13.glActiveTexture(originalActive);
            }
        }
        private void ensureForeignReadTarget() {
            if (foreignReadFramebuffer != 0) return;
            foreignReadFramebuffer = GL30.glGenFramebuffers(); foreignTexture = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, foreignTexture);
            try (MemoryStack stack = MemoryStack.stackPush()) {
                ByteBuffer pixels = stack.malloc(4 * 4 * 4);
                for (int i = 0; i < 16; i++) pixels.put((byte) 255).put((byte) 0).put((byte) 255).put((byte) 255);
                pixels.flip(); GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, 4, 4, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
            }
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, foreignReadFramebuffer);
            GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, foreignTexture, 0);
            require(GL30.glCheckFramebufferStatus(GL30.GL_READ_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE, "The foreign READ target is a real complete framebuffer with different scene data");
        }
        @Override public void removed() {
            if (foreignReadFramebuffer != 0) GL30.glDeleteFramebuffers(foreignReadFramebuffer);
            if (foreignTexture != 0) GL11.glDeleteTextures(foreignTexture);
            foreignReadFramebuffer = foreignTexture = 0;
        }
    }

    private record GlState(int program, int vao, int readFbo, int drawFbo, int active, int texture, int texture0,
                           boolean scissor, String scissorBox, String viewport, boolean blend, boolean depth, boolean cull) {
        static GlState capture() {
            int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE), texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            GL13.glActiveTexture(GL13.GL_TEXTURE0); int texture0 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D); GL13.glActiveTexture(active);
            try (MemoryStack stack = MemoryStack.stackPush()) {
                var scissor = stack.mallocInt(4); var viewport = stack.mallocInt(4);
                GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissor); GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
                return new GlState(GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM), GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING),
                        GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING), GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING), active, texture, texture0,
                        GL11.glIsEnabled(GL11.GL_SCISSOR_TEST), scissor.get(0) + ":" + scissor.get(1) + ":" + scissor.get(2) + ":" + scissor.get(3),
                        viewport.get(0) + ":" + viewport.get(1) + ":" + viewport.get(2) + ":" + viewport.get(3),
                        GL11.glIsEnabled(GL11.GL_BLEND), GL11.glIsEnabled(GL11.GL_DEPTH_TEST), GL11.glIsEnabled(GL11.GL_CULL_FACE));
            }
        }
    }
    private static double meanDifference(int[] a, int[] b, int w, int h, double x0, double y0, double x1, double y1) {
        long difference = 0; int count = 0;
        for (int y = (int) (h * y0); y < h * y1; y++) for (int x = (int) (w * x0); x < w * x1; x++) for (int channel = 0; channel < 3; channel++) {
            int offset = ((h - y - 1) * w + x) * 3 + channel; difference += Math.abs(a[offset] - b[offset]); count++;
        }
        return difference / Math.max(1D, count * 255D);
    }
    private static double edgeShift(int[] a, int[] b, int width, int height) {
        double maximum = 0; int row = (int) (height * .48);
        for (double[] window : new double[][]{{.132, .184, -1}, {.816, .868, 1}}) {
            List<Integer> before = edges(a, width, height, row, (int) (width * window[0]), (int) (width * window[1]));
            List<Integer> after = edges(b, width, height, row, (int) (width * window[0]), (int) (width * window[1]));
            // Match in the normal's expected direction and within the shader's actual
            // 19.4 * 1.4 pixel lens bound (+ dispersion/filter allowance). Otherwise a
            // transition softened below the threshold could pair with another stripe.
            for (int position : before) {
                int moved = after.stream().mapToInt(edge -> (int) ((edge - position) * window[2]))
                        .filter(delta -> delta > 0 && delta <= 30).min().orElse(0);
                maximum = Math.max(maximum, moved);
            }
        }
        return maximum;
    }
    private static List<Integer> edges(int[] pixels, int width, int height, int row, int start, int end) {
        List<Integer> positions = new ArrayList<>(); double[] differences = new double[Math.max(1, end - start)];
        for (int x = start; x < end; x++) {
            int offset = ((height - row - 1) * width + x) * 3;
            for (int channel = 0; channel < 3; channel++) differences[x - start] += Math.abs(pixels[offset + channel] - pixels[offset + channel + 3]);
        }
        for (int i = 1; i < differences.length - 1; i++) if (differences[i] > 45 && differences[i] > differences[i - 1] && differences[i] >= differences[i + 1]) positions.add(start + i);
        return positions;
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
            require(GL11.glGetError() == GL11.GL_NO_ERROR, "Native optical framebuffer read leaves clean GL"); return result;
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read); GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, pack);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, alignment); GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, rows); GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, skipRows); GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, skipPixels); MemoryUtil.memFree(pixels);
        }
    }
    private static double[] point(Screen screen, String id) {
        if (screen == null) return null;
        try { return (double[]) screen.getClass().getMethod("controlPoint", String.class).invoke(screen, id); }
        catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException failure) { throw new IllegalStateException("Cannot inspect rendered native control " + id, failure); }
    }
    private static void bounds(MinecraftClient client, String... ids) { for (String id : ids) { double[] at = point(client.currentScreen, id); require(at != null && Double.isFinite(at[0]) && Double.isFinite(at[1]) && at[0] >= 0 && at[1] >= 0 && at[0] < client.getWindow().getScaledWidth() && at[1] < client.getWindow().getScaledHeight(), "The actual hit target fits its live viewport: " + id); } }
    private static void click(MinecraftClient client, String id) { Screen screen = client.currentScreen; double[] at = point(screen, id); require(at != null, "Actual native rendered control exists: " + id); screen.mouseClicked(at[0], at[1], GLFW.GLFW_MOUSE_BUTTON_LEFT); screen.mouseReleased(at[0], at[1], GLFW.GLFW_MOUSE_BUTTON_LEFT); }
    private static void type(Screen screen, String value) { screen.keyPressed(GLFW.GLFW_KEY_A, 0, GLFW.GLFW_MOD_CONTROL); for (int i = 0; i < 520; i++) screen.keyPressed(GLFW.GLFW_KEY_BACKSPACE, 0, 0); for (char letter : value.toCharArray()) screen.charTyped(letter, 0); }
    private static void seek(String id, Consumer<MinecraftClient> action) { await("Reach typed control " + id, 3, client -> { if (point(client.currentScreen, id) == null) { double[] at = point(client.currentScreen, "settings-scroll"); require(at != null, "A real selected setting card owns its scroll viewport"); client.currentScreen.mouseScrolled(at[0], at[1], 0, -2); return false; } action.accept(client); return true; }); }
    private static void seekProfile(String name, Consumer<MinecraftClient> action) { await("Reach isolated old premade in the actual profile list", 3, client -> { if (point(client.currentScreen, "load-profile:" + name) == null) { double[] at = point(client.currentScreen, "profile-name"); require(at != null, "The real Profiles screen supplies its native list context"); client.currentScreen.mouseScrolled(at[0], at[1] + 45, 0, -3); return false; } action.accept(client); return true; }); }
    private static Map<String, String> profileHashes() { Path directory = FabricLoader.getInstance().getConfigDir().resolve("analyzer/profiles"); Map<String, String> hashes = new HashMap<>(); if (!Files.isDirectory(directory)) return hashes; try (var files = Files.walk(directory)) { for (Path file : files.filter(Files::isRegularFile).toList()) hashes.put(directory.relativize(file).toString(), HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)))); return Map.copyOf(hashes); } catch (Exception failure) { throw new IllegalStateException(failure); } }
    private static void save(AnalyzerCore core) { try { core.save(); } catch (IOException failure) { throw new IllegalStateException(failure); } }
    private static AnalyzerCore loadedCore() { AnalyzerCore core = new AnalyzerCore(FabricLoader.getInstance().getConfigDir().resolve("analyzer")); try { core.load(); return core; } catch (IOException failure) { throw new IllegalStateException(failure); } }
    private static void command(MinecraftClient client, String value) { client.getServer().execute(() -> client.getServer().getCommandManager().executeWithPrefix(client.getServer().getCommandSource().withSilent(), value)); }
    private static void viewport(MinecraftClient client, int width, int height, int gui) { GLFW.glfwSetWindowSize(client.getWindow().getHandle(), width, height); client.options.getGuiScale().setValue(gui); client.onResolutionChanged(); }
    private static void add(String label, int waitTicks, Consumer<MinecraftClient> action) { STEPS.add(new Step(label, waitTicks, client -> { action.accept(client); return true; })); }
    private static void await(String label, int waitTicks, Predicate<MinecraftClient> action) { STEPS.add(new Step(label, waitTicks, action)); }
    private static void require(boolean condition, String label) { if (!condition) throw new IllegalStateException("Liquid/layout check " + phase + ": " + label); assertions++; AnalyzerClient.LOGGER.info("ANALYZER_LIQUID_ASSERT: {}", label); }
    private static void capture(MinecraftClient client, String name) { require(LiquidGlass.isHealthy() && UiShader.isHealthy() && GL11.glGetError() == GL11.GL_NO_ERROR, "Healthy actual shaders/GL before " + name); ScreenshotRecorder.saveScreenshot(client.runDirectory, name, client.getFramebuffer(), message -> AnalyzerClient.LOGGER.info("Liquid/layout screenshot: {}", message.getString())); CAPTURES.add(name); }
    private static void finish(MinecraftClient client) { finished = true; LiquidGlass.setRefractionEnabled(true); AnalyzerClient.LOGGER.info("ANALYZER_LIQUID_LAYOUT_SMOKE_COMPLETE: {} assertions, {} screenshots, {} phases, {} ticks; old premade native Load/Back migration, five new starter defaults, HUD layout selector, nine category jumps, panel collapse/drag, typed value/parent routes, independent theme/material, six window/GUI pairs, real immutable-scene pixels and edge displacement, GL restoration, exclusive HUD logos, zero motion and reload. interiorDelta={} outsideDelta={} edgeMovementPixels={}", assertions, CAPTURES.size(), STEPS.size(), ticks, warpDifference, outsideDifference, edgeDisplacement); client.scheduleStop(); }
    private record Step(String label, int waitTicks, Predicate<MinecraftClient> action) { }
}
