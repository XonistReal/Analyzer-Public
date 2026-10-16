package dev.analyzer;

import com.mojang.authlib.GameProfile;
import dev.analyzer.render.EntityChamsRenderer;
import dev.analyzer.effects.ChamsShader;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.util.math.BlockPos;
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
import java.util.UUID;

/** Isolated development-only posed-model shader acceptance and native framebuffer captures. */
public final class ChamsSmoke {
    private static final String WORLD = "Analyzer-Chams-Smoke";
    private static final int SCOUT_ID = 991382;
    private static boolean requested, setupRequested, ready, finished;
    private static volatile boolean serverPrepared;
    private static volatile int written;
    private static int ticks, sceneTicks, stopWait;
    private static String lastStartupScreen = "";
    private static final List<String> CAPTURES = new ArrayList<>();
    private ChamsSmoke() { }

    public static void tick(MinecraftClient mc) {
        if (!FabricLoader.getInstance().isDevelopmentEnvironment() || !Boolean.getBoolean("analyzer.chamsSmoke"))
            throw new IllegalStateException("Chams native fixture requires an isolated development launch");
        if (finished) return;
        if (++ticks > 4800) throw new IllegalStateException("Chams native fixture timed out");
        if (!requested && mc.world == null) {
            String startupScreen = mc.currentScreen == null ? "null" : mc.currentScreen.getClass().getSimpleName();
            if (!startupScreen.equals(lastStartupScreen) || ticks % 200 == 0) {
                lastStartupScreen = startupScreen;
                AnalyzerClient.LOGGER.info("Chams native startup: screen={}, overlay={}, finishedLoading={}",
                        startupScreen, mc.getOverlay() == null ? "null" : mc.getOverlay().getClass().getSimpleName(), mc.isFinishedLoading());
            }
            // A new isolated run has never acknowledged the vanilla accessibility onboarding.
            // Bypass only that welcome screen, after its resource loading overlay has finished.
            if (mc.getOverlay() == null && startupScreen.equals("AccessibilityOnboardingScreen")) {
                AnalyzerClient.LOGGER.info("Chams native fixture bypasses fresh-run accessibility onboarding");
                mc.setScreen(new TitleScreen());
                return;
            }
        }
        if (!requested && mc.getOverlay() == null && mc.currentScreen instanceof TitleScreen) {
            requested = true;
            var core = AnalyzerClient.CORE;
            core.resetAll();
            for (var module : core.modules()) core.setEnabled(module.id(), false);
            mc.options.pauseOnLostFocus = false;
            mc.options.getViewDistance().setValue(5);
            mc.options.getSimulationDistance().setValue(5);
            mc.options.getGuiScale().setValue(2);
            mc.options.getFov().setValue(70);
            mc.options.setPerspective(Perspective.FIRST_PERSON);
            mc.options.hudHidden = false;
            mc.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);
            mc.getToastManager().clear();
            GLFW.glfwHideWindow(mc.getWindow().getHandle());
            if (Files.exists(mc.runDirectory.toPath().resolve("saves/" + WORLD + "/level.dat")))
                mc.createIntegratedServerLoader().start(WORLD, () -> mc.scheduleStop());
            else mc.createIntegratedServerLoader().createAndStart(WORLD,
                    new LevelInfo(WORLD, GameMode.CREATIVE, false, Difficulty.NORMAL, true,
                            new GameRules(FeatureFlags.VANILLA_FEATURES), DataConfiguration.SAFE_MODE),
                    new GeneratorOptions(762831L, false, false), WorldPresets::createDemoOptions, new TitleScreen());
        }
        if (!requested || mc.world == null || mc.player == null || mc.getServer() == null || mc.getOverlay() != null) return;
        if (!setupRequested) { setupRequested = true; setup(mc); return; }
        if (!ready) {
            if (!serverPrepared || mc.player.getBlockY() != 161
                    || !mc.world.getBlockState(new BlockPos(11, 160, 23)).isOf(net.minecraft.block.Blocks.POLISHED_DEEPSLATE)
                    || !mc.world.getBlockState(new BlockPos(2, 161, 8)).isOf(net.minecraft.block.Blocks.CHEST)) return;
            ready = true; mc.setScreen(null);
            mc.inGameHud.getChatHud().clear(false);
            mc.getToastManager().clear();
            var scout = new OtherClientPlayerEntity(mc.world, new GameProfile(
                    UUID.fromString("aba4f8ec-6206-45d5-a578-0fa2c7a923bd"), "Scout"));
            scout.setId(SCOUT_ID); scout.refreshPositionAndAngles(11.5, 161, 14, 180, 0);
            scout.setHealth(20); mc.world.addEntity(scout);
            configure(mc);
        }
        int t = sceneTicks++;
        switch (t) {
            case 90 -> { verifyModels(); capture(mc, "analyzer-native-chams-flat.png"); style("Metallic"); }
            case 130 -> { verifyModels(); capture(mc, "analyzer-native-chams-metallic.png"); style("Glass"); }
            case 170 -> { verifyModels(); capture(mc, "analyzer-native-chams-glass.png"); style("Glow"); }
            case 210 -> { verifyModels(); capture(mc, "analyzer-native-chams-glow.png"); style("Wireframe"); }
            case 250 -> {
                verifyModels(); capture(mc, "analyzer-native-chams-wireframe.png");
                style("Flat"); AnalyzerClient.CORE.setValue("chams", "hidden-opacity", 85);
                command(mc, "fill 1 161 17 22 164 17 minecraft:stone_bricks");
            }
            case 295 -> {
                require(mc.world.getBlockState(new BlockPos(11,162,17)).isOf(net.minecraft.block.Blocks.STONE_BRICKS), "Real received wall occludes the same posed entities");
                verifyModels(); capture(mc,"analyzer-native-chams-occluded.png");
                AnalyzerClient.CORE.setValue("chams", "through-walls", false);
            }
            case 335 -> {
                verifyModels(); capture(mc,"analyzer-native-chams-depth-only.png");
                AnalyzerClient.CORE.setEnabled("chams",false);
            }
            case 355 -> {
                require(EntityChamsRenderer.counts().entities()==0, "Disabling clears every pending native model");
                require(GL11.glGetError()==GL11.GL_NO_ERROR, "Clean GL state after disabling");
                int checks = ChamsGlChecks.run();
                require(checks>20, "Live GL shader state restoration checks: " + checks);
                AnalyzerClient.CORE.setEnabled("chams",true);
                AnalyzerClient.CORE.setValue("chams", "through-walls", true);
                style("Metallic");
                mc.reloadResources();
            }
            case 400 -> {
                verifyModels(); require(ChamsShader.isHealthy(), "Native reload recompiles the material shader");
                capture(mc,"analyzer-native-chams-reloaded.png");
                AnalyzerClient.CORE.setEnabled("entity-esp",true);
                AnalyzerClient.CORE.setValue("entity-esp","passives",true);
                AnalyzerClient.CORE.setValue("entity-esp","labels",false);
                command(mc, "fill 1 161 17 22 164 17 minecraft:air");
            }
            case 440 -> { verifyModels(); capture(mc,"analyzer-native-chams-with-boxes.png"); }
            default -> { }
        }
        if (t >= 441) {
            if (written != 9 || !allFilesExist(mc)) {
                if (++stopWait > 200) throw new IllegalStateException("Native screenshot callback/write did not finish");
                return;
            }
            finished = true;
            AnalyzerClient.LOGGER.info("ANALYZER_CHAMS_COMPLETE: 9 native framebuffer photos; five posed-model materials; actual occluder; opt-out depth pass; enable/disable; GPU state restoration; resource reload; independent debug boxes; local Scout player fixture");
            mc.scheduleStop();
        }
    }
    private static void style(String value) { AnalyzerClient.CORE.setValue("chams", "style", value); }
    private static void verifyModels() {
        var counts = EntityChamsRenderer.counts();
        require(counts.entities() >= 5 && counts.vertices() >= 400 && counts.shaderDraws() == counts.entities(),
                "Native posed meshes draw successfully: " + counts);
        require(ChamsShader.isHealthy() && ChamsShader.passCount()>0, "Original GLSL shader is compiled and rendering");
    }
    private static void configure(MinecraftClient mc) {
        var core = AnalyzerClient.CORE;
        core.setEnabled("chams",true);
        core.setValue("chams","players",true); core.setValue("chams","hostiles",true);
        core.setValue("chams","passives",true); core.setValue("chams","through-walls",true);
        core.setValue("chams","visible-opacity",100); core.setValue("chams","hidden-opacity",65);
        core.setValue("chams","wire-width",2);
        core.setValue("chams","animation-speed",0);
        style("Flat"); mc.options.sendClientSettings();
    }

    private static void setup(MinecraftClient mc) {
        mc.getServer().execute(() -> {
            var server = mc.getServer();
            for (int x = -1; x <= 2; x++) for (int z = -1; z <= 2; z++) server.getOverworld().getChunk(x, z);
            String[] commands = {
                    "difficulty normal", "gamemode creative AnalyzerDev", "clear AnalyzerDev", "effect clear AnalyzerDev",
                    "gamerule doMobSpawning false", "gamerule doDaylightCycle false", "gamerule doWeatherCycle false",
                    "gamerule doMobLoot false", "gamerule randomTickSpeed 0", "time set 1000", "weather clear",
                    "kill @e[type=!minecraft:player]", "fill 0 160 0 24 160 25 minecraft:polished_deepslate",
                    "fill 0 161 0 24 171 25 minecraft:air",
                    "fill 0 160 0 24 160 0 minecraft:sea_lantern", "fill 0 160 25 24 160 25 minecraft:sea_lantern",
                    "fill 0 160 0 0 160 25 minecraft:sea_lantern", "fill 24 160 0 24 160 25 minecraft:sea_lantern",
                    "setblock 2 161 8 minecraft:chest[facing=south,type=left]",
                    "setblock 3 161 8 minecraft:chest[facing=south,type=right]",
                    "setblock 6 161 8 minecraft:trapped_chest[facing=south]",
                    "setblock 9 161 8 minecraft:barrel[facing=south]",
                    "setblock 12 161 8 minecraft:purple_shulker_box",
                    "setblock 15 161 8 minecraft:ender_chest[facing=south]",
                    "setblock 18 161 8 minecraft:furnace[facing=south]", "setblock 21 161 8 minecraft:hopper",
                    "setblock 2 161 11 minecraft:dispenser[facing=south]", "setblock 21 161 11 minecraft:crafter",
                    "setblock 2 161 14 minecraft:brewing_stand", "setblock 21 161 14 minecraft:decorated_pot",
                    "summon minecraft:chest_minecart 6.5 161 11.5 {NoGravity:1b,Invulnerable:1b}",
                    "summon minecraft:husk 5.5 161 14.5 {NoAI:1b,NoGravity:1b,Silent:1b,PersistenceRequired:1b,Invulnerable:1b}",
                    "summon minecraft:creeper 8.5 161 12.5 {NoAI:1b,NoGravity:1b,Silent:1b,PersistenceRequired:1b,Invulnerable:1b}",
                    "summon minecraft:cow 15 161 13.5 {NoAI:1b,NoGravity:1b,Silent:1b,PersistenceRequired:1b,Invulnerable:1b}",
                    "summon minecraft:pig 18 161 15 {NoAI:1b,NoGravity:1b,Silent:1b,PersistenceRequired:1b,Invulnerable:1b}",
                    "tp AnalyzerDev 11.5 161 23.5 180 13"
            };
            var source = server.getCommandSource().withSilent();
            for (String text : commands) server.getCommandManager().executeWithPrefix(source, text);
            serverPrepared = true;
            AnalyzerClient.LOGGER.info("ANALYZER_CHAMS_ARENA_READY");
        });
    }

    private static void command(MinecraftClient mc, String text) {
        mc.getServer().execute(() -> mc.getServer().getCommandManager().executeWithPrefix(mc.getServer().getCommandSource().withSilent(), text));
    }
    private static void capture(MinecraftClient mc, String name) {
        require(GL11.glGetError() == GL11.GL_NO_ERROR, "Clean GL state before native framebuffer " + name);
        CAPTURES.add(name);
        ScreenshotRecorder.saveScreenshot(mc.runDirectory, name, mc.getFramebuffer(), text -> {
            written++;
            AnalyzerClient.LOGGER.info("Chams native written: {} / {}", name, text.getString());
        });
    }
    private static boolean allFilesExist(MinecraftClient mc) {
        try {
            for (String name : CAPTURES) {
                var path = mc.runDirectory.toPath().resolve("screenshots").resolve(name);
                if (!Files.isRegularFile(path) || Files.size(path) < 1024) return false;
            }
            return CAPTURES.size() == 9;
        } catch (java.io.IOException failure) { throw new IllegalStateException("Cannot verify native photo files", failure); }
    }
    private static void require(boolean condition, String text) {
        if (!condition) throw new IllegalStateException("Chams native fixture: " + text);
        AnalyzerClient.LOGGER.info("Chams native check: {}", text);
    }
}
