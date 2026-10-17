package dev.analyzer;

import dev.analyzer.skybox.CustomSkybox;
import dev.analyzer.skybox.SkyboxLibrary;
import dev.analyzer.ui.SkyboxBrowserScreen;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.MessageScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Development-only real sky pass, shelf, native assets, fallback and cleanup acceptance. */
public final class SkyboxSmoke {
    private static final String WORLD = "Analyzer-Skybox-Smoke";
    private static boolean requested, setup, ready, disconnected, finished, endRepositioned;
    private static volatile boolean serverReady;
    private static volatile int written;
    private static int ticks, age, stage, assertions;
    private static long frames, uploads;
    private static long endFrames;
    private static Path panorama, cube;
    private static SkyboxBrowserScreen browser;
    private static final List<String> photos = new ArrayList<>();
    private SkyboxSmoke() { }
    public static void tick(MinecraftClient mc) {
        if (!FabricLoader.getInstance().isDevelopmentEnvironment() || !Boolean.getBoolean("analyzer.skyboxSmoke")) throw new IllegalStateException("Skybox fixture requires isolated development launch");
        if (finished) return;
        if (++ticks > 3600) throw new IllegalStateException("Skybox native fixture timed out: " + stage + " / " + CustomSkybox.status());
        if (disconnected) {
            if (++age < 10 || written != photos.size()) return;
            check(mc.world == null && !CustomSkybox.hasTexture(), "Actual world disconnect releases sky texture and pending load");
            for (String name : photos) try { check(Files.size(mc.runDirectory.toPath().resolve("screenshots").resolve(name)) > 1024, "Framebuffer file written " + name); }
            catch (Exception failure) { throw new IllegalStateException(failure); }
            verifyPanoramaPixels(mc, "panorama");
            verifyPanoramaPixels(mc, "end-dimension");
            finished = true; AnalyzerClient.LOGGER.info("ANALYZER_SKYBOX_COMPLETE: {} native checks; {} captures; panoramic/cube/native panorama/native End sources; shelf selection; bad path fallback; disable; resource reload; GL restoration; world cleanup", assertions, photos.size());
            mc.scheduleStop(); return;
        }
        if (!requested && mc.getOverlay() == null && mc.currentScreen != null && mc.currentScreen.getClass().getSimpleName().equals("AccessibilityOnboardingScreen")) { mc.setScreen(new TitleScreen()); return; }
        if (!requested && mc.getOverlay() == null && mc.currentScreen instanceof TitleScreen) {
            requested = true; AnalyzerClient.CORE.resetAll();
            for (var module : AnalyzerClient.CORE.modules()) AnalyzerClient.CORE.setEnabled(module.id(), false);
            mc.options.pauseOnLostFocus = false; mc.options.getViewDistance().setValue(4); mc.options.getSimulationDistance().setValue(5);
            mc.options.getGuiScale().setValue(2); mc.options.getFov().setValue(70);
            mc.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE); mc.getToastManager().clear();
            GLFW.glfwHideWindow(mc.getWindow().getHandle());
            try { fixtures(mc); } catch (Exception failure) { throw new IllegalStateException(failure); }
            if (Files.exists(mc.runDirectory.toPath().resolve("saves/" + WORLD + "/level.dat"))) mc.createIntegratedServerLoader().start(WORLD, mc::scheduleStop);
            else mc.createIntegratedServerLoader().createAndStart(WORLD,
                    new LevelInfo(WORLD, GameMode.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(FeatureFlags.VANILLA_FEATURES), DataConfiguration.SAFE_MODE),
                    new GeneratorOptions(783291L, false, false), WorldPresets::createDemoOptions, new TitleScreen());
        }
        if (!requested || mc.world == null || mc.player == null || mc.getServer() == null || mc.getOverlay() != null) return;
        if (!setup) {
            setup = true;
            mc.getServer().execute(() -> {
                var server = mc.getServer(); var source = server.getCommandSource().withSilent();
                for (String command : List.of("gamerule doMobSpawning false", "gamerule doDaylightCycle false", "gamerule doWeatherCycle false", "time set noon", "weather clear", "fill -8 160 -8 8 160 8 minecraft:stone", "tp AnalyzerDev 0.5 161 0.5 90 -20"))
                    server.getCommandManager().executeWithPrefix(source, command);
                serverReady = true;
            }); return;
        }
        if (!ready) {
            if (!serverReady || mc.player.getBlockY() != 161 || !mc.world.getBlockState(new BlockPos(0, 160, 0)).isOf(net.minecraft.block.Blocks.STONE)) return;
            ready = true; mc.setScreen(null); mc.inGameHud.getChatHud().clear(false); mc.getToastManager().clear();
            AnalyzerClient.CORE.setEnabled("sky", true); AnalyzerClient.CORE.setValue("sky", "intensity", 1.0);
            AnalyzerClient.CORE.setValue("sky", "source", "Custom");
            browser = new SkyboxBrowserScreen(AnalyzerClient.CORE, null, mc.runDirectory.toPath(), path -> {
                AnalyzerClient.CORE.setValue("sky", "custom-path", path.toString()); CustomSkybox.refresh();
            }); mc.setScreen(browser); enter(0);
        }
        age++;
        switch (stage) {
            case 0 -> {
                if (age < 12) return;
                check(browser.currentFolder().equals(SkyboxLibrary.defaultRoot(mc.runDirectory.toPath())), "Shader shelf opens actual game skyboxes folder");
                check(browser.entryNames().contains("gradient.png") && browser.entryNames().contains("cube"), "Shelf lists real panorama and cube folder");
                click("entry:gradient.png"); enter(1);
            }
            case 1 -> { if (age >= 8) { check(panorama.equals(browser.selectedFile()), "Shelf selection records actual absolute path"); capture(mc, "browser"); click("use-selected"); enter(2); } }
            case 2 -> { if (drawn()) { check(AnalyzerClient.CORE.getText("sky", "custom-path").equals(panorama.toString()), "Use selected installs selected image path"); verify("Panorama"); capture(mc, "panorama"); path(cube); enter(3); } }
            case 3 -> { if (drawn()) { verify("Six faces"); capture(mc, "cube"); source("Minecraft panorama"); enter(4); } }
            case 4 -> { if (drawn()) { verify("Minecraft panorama"); capture(mc, "minecraft-panorama"); source("Minecraft End"); enter(5); } }
            case 5 -> { if (drawn()) { verify("Minecraft End"); capture(mc, "minecraft-end"); mc.reloadResources(); enter(6); } }
            case 6 -> { if (drawn()) { verify("Minecraft End"); check(CustomSkybox.uploads() > uploads, "Resource reload decodes and reuploads actual native sky"); source("Custom"); path(panorama.getParent().resolve("missing.png")); enter(7); } }
            case 7 -> {
                if (age < 25 || !CustomSkybox.status().startsWith("Sky not loaded:")) return;
                check(!CustomSkybox.hasTexture() && CustomSkybox.frames() == frames, "Bad path retains ordinary native sky without custom draws");
                check(GL11.glGetError() == GL11.GL_NO_ERROR, "Bad source fallback retains clean GL state");
                path(panorama); enter(8);
            }
            case 8 -> { if (drawn()) { verify("Panorama"); AnalyzerClient.CORE.setEnabled("sky", false); enter(9); } }
            case 9 -> {
                if (age < 10) return;
                check(!CustomSkybox.hasTexture() && CustomSkybox.frames() == frames, "Disable deletes sky texture and restores ordinary native sky");
                AnalyzerClient.CORE.setEnabled("sky", true); enter(10);
            }
            case 10 -> {
                if (drawn()) {
                    verify("Panorama");
                    command(mc, "execute in minecraft:the_end run tp AnalyzerDev 0.5 161 0.5 90 -20"); enter(11);
                }
            }
            case 11 -> {
                if (age < 15 || !mc.world.getRegistryKey().equals(net.minecraft.world.World.END)) return;
                check(!CustomSkybox.hasTexture(), "Actual End dimension keeps its native sky when End opt-in is off");
                AnalyzerClient.CORE.setValue("sky", "end-sky", true); endFrames = CustomSkybox.nativeEndFrames(); enter(12);
            }
            case 12 -> {
                if (!endRepositioned && age >= 20) {
                    // Minecraft deliberately omits its whole sky pass near the dragon's thick-fog bossbar.
                    // Preserve that native condition, then move beyond the fight area to test a real End sky pass.
                    if (mc.inGameHud.getBossBarHud().shouldThickenFog())
                        check(CustomSkybox.nativeEndFrames() == endFrames, "Native dragon thick fog suppresses the whole custom End sky pass");
                    command(mc, "execute in minecraft:the_end run tp AnalyzerDev 512.5 161 512.5 90 -20");
                    endRepositioned = true; age = 0; return;
                }
                if (!endRepositioned || mc.inGameHud.getBossBarHud().shouldThickenFog() || mc.player.getX() < 500) return;
                if (drawn() && age >= 60 && CustomSkybox.nativeEndFrames() > endFrames + 5) {
                    check(CustomSkybox.nativeEndFrames() > endFrames + 5, "Actual End native sky callback renders repeated frames outside dragon thick fog");
                    verify("Panorama"); capture(mc, "end-dimension");
                    command(mc, "execute in minecraft:the_nether run tp AnalyzerDev 0.5 161 0.5 90 -20"); enter(13);
                }
            }
            case 13 -> {
                if (age < 15 || !mc.world.getRegistryKey().equals(net.minecraft.world.World.NETHER)) return;
                check(!CustomSkybox.hasTexture(), "Actual Nether transition releases custom sky resources"); enter(14);
            }
            case 14 -> {
                if (age < 10) return;
                check(CustomSkybox.frames() == frames && !CustomSkybox.hasTexture(), "Unsupported native sky type performs no texture work or custom draws");
                // Disconnect while a supported, loaded custom source is active to cover the stronger cleanup case.
                command(mc, "execute in minecraft:overworld run tp AnalyzerDev 0.5 161 0.5 90 -20"); enter(15);
            }
            case 15 -> {
                if (!mc.world.getRegistryKey().equals(net.minecraft.world.World.OVERWORLD) || !drawn()) return;
                verify("Panorama"); disconnected = true; age = 0; mc.world.disconnect();
                mc.disconnect(new MessageScreen(Text.literal("Saving isolated skybox acceptance world")));
            }
        }
    }
    private static void enter(int value) { stage = value; age = 0; frames = CustomSkybox.frames(); uploads = CustomSkybox.uploads(); }
    private static boolean drawn() { return age >= 30 && CustomSkybox.hasTexture() && CustomSkybox.frames() > frames; }
    private static void source(String source) { AnalyzerClient.CORE.setValue("sky", "source", source); CustomSkybox.refresh(); }
    private static void path(Path path) { AnalyzerClient.CORE.setValue("sky", "custom-path", path.toString()); CustomSkybox.refresh(); }
    private static void command(MinecraftClient mc, String command) {
        mc.getServer().execute(() -> mc.getServer().getCommandManager().executeWithPrefix(mc.getServer().getCommandSource().withSilent(), command));
    }
    private static void click(String id) { double[] point = browser.controlPoint(id); check(point != null, "Shelf renders " + id); check(browser.mouseClicked(point[0], point[1], 0), "Shelf accepts real click " + id); }
    private static void verify(String expected) {
        check(CustomSkybox.status().startsWith(expected) && CustomSkybox.hasTexture(), "Actual native sky texture loads " + expected);
        check(GL11.glGetError() == GL11.GL_NO_ERROR, "Native sky shader draws without GL error " + expected);
        int[] before = snapshot(); CustomSkybox.render(); int[] after = snapshot();
        check(java.util.Arrays.equals(before, after), "Native sky draw restores program, VAO, active unit, texture, blend and depth " + expected);
    }
    private static int[] snapshot() { return new int[]{GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM), GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING), GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE), GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D), GL11.glGetInteger(GL13.GL_TEXTURE_BINDING_CUBE_MAP), GL11.glIsEnabled(GL11.GL_BLEND) ? 1 : 0, GL11.glIsEnabled(GL11.GL_DEPTH_TEST) ? 1 : 0, GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK) ? 1 : 0}; }
    private static void fixtures(MinecraftClient mc) throws Exception {
        Path root = SkyboxLibrary.defaultRoot(mc.runDirectory.toPath()); Files.createDirectories(root);
        panorama = root.resolve("gradient.png"); cube = root.resolve("cube"); Files.createDirectories(cube);
        image(panorama, 512, 256, 0x24467C);
        int index = 0; for (String face : dev.analyzer.skybox.SkyboxImages.FACES) image(cube.resolve(face + ".png"), 128, 128, new int[]{0xCC5577, 0x4477BB, 0x5544AA, 0x223344, 0x669955, 0xBB8844}[index++]);
    }
    private static void image(Path path, int width, int height, int tint) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < width; x++) for (int y = 0; y < height; y++) {
            float gradient = .4f + .6f * y / height;
            int color = (int) ((tint >> 16 & 255) * gradient) << 16 | (int) ((tint >> 8 & 255) * gradient) << 8 | (int) ((tint & 255) * gradient);
            if (x % 64 < 2 || y % 64 < 2) color = 0xDDDDDD;
            image.setRGB(x, y, color);
        }
        ImageIO.write(image, "png", path.toFile()); image.flush();
    }
    private static void capture(MinecraftClient mc, String suffix) {
        String name = "analyzer-native-skybox-" + suffix + ".png"; photos.add(name);
        ScreenshotRecorder.saveScreenshot(mc.runDirectory, name, mc.getFramebuffer(), text -> { written++; AnalyzerClient.LOGGER.info("Skybox capture written: {}", name); });
    }
    private static void verifyPanoramaPixels(MinecraftClient mc, String suffix) {
        try {
            BufferedImage frame = ImageIO.read(mc.runDirectory.toPath().resolve("screenshots/analyzer-native-skybox-" + suffix + ".png").toFile());
            check(frame != null && frame.getWidth() == 1920 && frame.getHeight() == 1080, "Actual " + suffix + " framebuffer has requested native dimensions");
            int blue = 0, samples = 0;
            // Upper scene excludes HUD/hand/terrain. The original test panorama is blue with white grid lines.
            for (int x = frame.getWidth() / 10; x < frame.getWidth() * 9 / 10; x += 23)
                for (int y = frame.getHeight() / 10; y < frame.getHeight() * 45 / 100; y += 19) {
                    int pixel = frame.getRGB(x, y), red = pixel >> 16 & 255, green = pixel >> 8 & 255, b = pixel & 255;
                    if (b >= 30 && b > red * 1.5 && b > green * 1.2) blue++;
                    samples++;
                }
            frame.flush();
            check(blue > samples * .5, "Completed " + suffix + " framebuffer contains the custom blue panorama across most sky pixels (" + blue + "/" + samples + ")");
        } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
    }
    private static void check(boolean value, String message) { if (!value) throw new IllegalStateException("Skybox native fixture: " + message); assertions++; AnalyzerClient.LOGGER.info("Skybox native check: {}", message); }
}
