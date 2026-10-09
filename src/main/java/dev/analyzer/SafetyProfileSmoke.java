package dev.analyzer;

import baritone.api.BaritoneAPI;
import baritone.api.Settings;
import baritone.api.utils.SettingsUtil;
import baritone.api.utils.input.Input;
import dev.analyzer.automation.AutomationEngine;
import dev.analyzer.automation.BaritoneBridge;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import org.lwjgl.opengl.GL11;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/** Isolated Survival fixture exercising the official API, actual disk serialization and paused-player movement. */
final class SafetyProfileSmoke {
    static final int LAST_TICK = 410;
    private static final BaritoneBridge BRIDGE = new BaritoneBridge();
    private static final Map<Settings.Setting<?>, Object> ORIGINAL = new LinkedHashMap<>(), BASELINE = new LinkedHashMap<>();
    private static Vec3d pausedAt, stoppedAt;
    private static Path settingsFile;
    private static byte[] originalFile, baselineFile, lastTestFile;
    private static int phase, assertions, screenshots, registrySize, finishX, finishZ;
    private static boolean complete, fileExisted;
    private SafetyProfileSmoke() { }
    static boolean done() { return complete; }

    static void tick(MinecraftClient mc, int t) {
        if (complete) return;
        phase = t;
        BRIDGE.tick();
        if (t > 60 && t < 76) {
            mc.player.setYaw(0);
            require(BRIDGE.setSafetyInputs(true, true, false), "Paused escape movement retains the owned lease");
        }
        switch (t) {
            case 0 -> {
                require(FabricLoader.getInstance().isDevelopmentEnvironment(), "Safety profile fixture is development-only");
                AutomationEngine.INSTANCE.stop();
                mc.setScreen(null); AnalyzerClient.CORE.resetAll();
                mc.options.setPerspective(Perspective.FIRST_PERSON);
                require(BRIDGE.available(), "Official Baritone API is available for routing safety");
                registrySize = BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().getRegistry().entries.size();
                captureSettings(mc);
                fixtureSettings();
                command(mc, "kill @e[type=!minecraft:player]");
                command(mc, "gamemode creative AnalyzerDev");
                command(mc, "clear AnalyzerDev");
                command(mc, "effect clear AnalyzerDev");
                command(mc, "fill 88 160 88 132 160 132 minecraft:bedrock");
                command(mc, "fill 88 161 88 132 167 132 minecraft:air");
                command(mc, "tp AnalyzerDev 100.5 161 100.5 -90 0");
                command(mc, "time set 1000"); command(mc, "weather clear");
            }
            case 10 -> {
                command(mc, "gamemode survival AnalyzerDev");
                command(mc, "effect give AnalyzerDev minecraft:instant_health 1 5 true");
                command(mc, "effect give AnalyzerDev minecraft:saturation 1 5 true");
            }
            case 20 -> {
                require(mc.interactionManager.getCurrentGameMode() == GameMode.SURVIVAL, "The routing fixture uses genuine Survival mode");
                require(mc.player.getBlockX() == 100 && mc.player.getBlockZ() == 100, "Observed fixture position is loaded and authoritative");
                SettingsUtil.save(BaritoneAPI.getSettings());
                baselineFile = readFile(); lastTestFile = baselineFile;
                require(BRIDGE.beginSafetyProfile(), "A mission can request its scoped routing profile");
                require(BRIDGE.safetyProfileStatus().enabled() && !BRIDGE.safetyProfileStatus().applied(), "Idle requested profiles do not alter shared settings");
                require(baselineMatches(), "Idle settings still match the captured preferences");
                require(BRIDGE.travelXZ(128, 100), "An owned route starts with the requested profile");
            }
            case 25 -> {
                require(BRIDGE.status().owned() && BRIDGE.safetyProfileStatus().applied(), "Routing safety applies only to the active owned process");
                require(!BRIDGE.manualTaskActive(), "An owned route does not falsely report manual control");
                require(saferFields(), "Actual public API settings contain avoidance, safe falls and liquid guards");
                require(BRIDGE.safetyProfileStatus().changedSettings() == 21, "Every differing fixture setting is recorded by the lease");
                require(BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().getRegistry().entries.size() > registrySize, "Short-lived command guards protect serialization and manual takeover");
            }
            case 30 -> require(BRIDGE.pause(), "The owned task pauses without discarding its route");
            case 40 -> {
                require(BRIDGE.status().paused(), "The real Baritone task retains its paused ownership");
                require(!BRIDGE.manualTaskActive(), "An owned pause does not falsely report a manual task");
                require(BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("set avoidance"), "The real settings query delegates to the official command");
                lastTestFile = readFile();
                require(Arrays.equals(baselineFile, lastTestFile), "Official settings serialization saves the exact original baseline without temporary profile values");
                require(saferFields() && BRIDGE.safetyProfileStatus().applied(), "The profile reapplies after the query while the same route remains owned");
            }
            case 50 -> {
                require(BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("set mobAvoidanceCoefficient 2.75"), "A user's explicit settings edit runs through the official command");
                BASELINE.put(BaritoneAPI.getSettings().mobAvoidanceCoefficient, 2.75D);
                lastTestFile = readFile();
                String disk = new String(lastTestFile, StandardCharsets.UTF_8);
                require(disk.contains("mobAvoidanceCoefficient 2.75") && !disk.contains("avoidance true") && !disk.contains("mobAvoidanceRadius 10"), "The settings file preserves the explicit edit and excludes other temporary values");
                require(saferFields(), "The owned route temporarily stays conservative after a preference edit");
            }
            case 60 -> {
                pausedAt = mc.player.getPos();
                mc.player.setYaw(0);
                require(BRIDGE.setSafetyInputs(true, true, false), "Escape input is accepted for the valid owned paused process");
            }
            case 76 -> {
                require(mc.player.getPos().distanceTo(pausedAt) > 1, "Forced safety inputs move the actual Survival player while its route is paused");
                require(BRIDGE.status().paused() && BRIDGE.status().owned(), "Escape movement preserves the paused task for later continuation");
                capture(mc, "analyzer-safety-paused-escape.png");
                BRIDGE.clearSafetyInputs(); stoppedAt = mc.player.getPos();
                require(inputsReleased(), "Safety movement and click overrides release together");
            }
            case 88 -> {
                require(mc.player.getPos().distanceTo(stoppedAt) < .6, "Releasing safety inputs stops the actual player after ordinary inertia");
                BRIDGE.endSafetyProfile();
                require(BRIDGE.status().paused() && BRIDGE.status().owned(), "Ending a profile leaves the owned paused process intact");
                require(!BRIDGE.safetyProfileStatus().enabled() && !BRIDGE.safetyProfileStatus().applied(), "Explicit profile cleanup works while the task is paused");
                require(baselineMatches() && registryRestored(), "Pause-profile cleanup restores preferences, the explicit edit and the original command registry");
            }
            case 90 -> {
                require(BRIDGE.cancel(), "The test cancels only its own paused route");
                require(!BRIDGE.setSafetyInputs(true, true, true), "Unowned routes refuse safety movement overrides");
            }
            case 100 -> {
                finishX = mc.player.getBlockX() + 3; finishZ = mc.player.getBlockZ();
                require(BRIDGE.beginSafetyProfile() && BRIDGE.travelXZ(finishX, finishZ), "A new process takes a fresh preference snapshot");
                require(saferFields(), "The fresh process applies the safer public API values");
            }
            case 190 -> {
                require(BRIDGE.status().complete() && mc.player.getBlockX() == finishX && mc.player.getBlockZ() == finishZ, "The owned route reaches the actual three-block target");
                require(!BRIDGE.safetyProfileStatus().applied() && BRIDGE.safetyProfileStatus().enabled(), "Process completion restores settings while the mission scope remains requested");
                require(baselineMatches() && registryRestored(), "Completion restores all captured preferences and command entries");
                require(Arrays.equals(lastTestFile, readFile()), "Profile lifecycle never writes the settings file itself");
            }
            case 200 -> require(BRIDGE.travelXZ(128, 108), "A third owned process reapplies the profile for takeover testing");
            case 205 -> {
                require(saferFields(), "Manual takeover starts from an actually applied profile");
                require(BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("goto 116 108"), "An ordinary manual goto executes through the public command registry");
                BRIDGE.tick();
                require(BRIDGE.status().state() == BaritoneBridge.State.INTERRUPTED && !BRIDGE.status().owned(), "Manual control receives ownership before its command runs");
                require(BRIDGE.manualTaskActive(), "The bridge reports the active replacement manual task before safety actions");
                require(baselineMatches() && registryRestored() && !BRIDGE.safetyProfileStatus().enabled(), "Manual takeover restores shared settings and removes all temporary delegates");
                require(!BRIDGE.cancel(), "Analyzer cannot cancel the replacement manual route");
                require(BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().isActive(), "The manual route remains active after that refused cancel");
            }
            case 340 -> {
                require(mc.player.getBlockX() == 116 && mc.player.getBlockZ() == 108, "The preserved manual route reaches its own observed destination");
                BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
                // This cancels only the local test's manual fixture after its observed arrival.
                capture(mc, "analyzer-safety-manual-takeover.png");
            }
            case 350 -> require(BRIDGE.beginSafetyProfile() && BRIDGE.travelXZ(128, 116), "Stop testing starts another owned profile");
            case 355 -> {
                require(saferFields() && BRIDGE.safetyProfileStatus().applied(), "Stop begins with real temporary shared settings");
                BaritoneAPI.getSettings().mobAvoidanceCoefficient.value = 6.25D;
                BASELINE.put(BaritoneAPI.getSettings().mobAvoidanceCoefficient, 6.25D);
                require(BRIDGE.pause() && BRIDGE.cancel(), "Stop releases its paused owned process");
                BRIDGE.endSafetyProfile();
                require(baselineMatches() && registryRestored() && inputsReleased(), "Stop releases settings, command delegates and input overrides");
                require(BaritoneAPI.getSettings().mobAvoidanceCoefficient.value == 6.25D, "An external public API settings edit survives scoped restoration");
            }
            case LAST_TICK -> {
                BRIDGE.endSafetyProfile();
                restoreFixture();
                require(GL11.glGetError() == GL11.GL_NO_ERROR, "Safety profile final GL state");
                complete = true;
                AnalyzerClient.LOGGER.info("ANALYZER_SAFETY_PROFILE_COMPLETE: {} assertions, {} screenshots; actual scoped settings, disk serialization, completion, paused movement, stop and manual takeover verified", assertions, screenshots);
            }
        }
    }

    private static void captureSettings(MinecraftClient mc) {
        settingsFile = mc.runDirectory.toPath().resolve("baritone/settings.txt");
        try { fileExisted = Files.exists(settingsFile); originalFile = fileExisted ? Files.readAllBytes(settingsFile) : new byte[0]; }
        catch (IOException error) { throw new IllegalStateException("Cannot snapshot isolated settings fixture", error); }
    }
    private static <T> void fixture(Settings.Setting<T> setting, T value) {
        ORIGINAL.put(setting, setting.value); setting.value = value; BASELINE.put(setting, value);
    }
    private static void fixtureSettings() {
        var s = BaritoneAPI.getSettings();
        fixture(s.avoidance, false); fixture(s.mobAvoidanceCoefficient, 1.25D); fixture(s.mobAvoidanceRadius, 7);
        fixture(s.mobSpawnerAvoidanceCoefficient, 1.25D); fixture(s.mobSpawnerAvoidanceRadius, 12);
        fixture(s.allowParkour, true); fixture(s.allowParkourPlace, true); fixture(s.allowParkourAscend, true);
        fixture(s.allowDiagonalDescend, true); fixture(s.allowOvershootDiagonalDescend, true); fixture(s.allowWaterBucketFall, true);
        fixture(s.maxFallHeightNoWater, 8); fixture(s.assumeWalkOnWater, true); fixture(s.assumeWalkOnLava, true);
        fixture(s.strictLiquidCheck, false); fixture(s.avoidUpdatingFallingBlocks, false); fixture(s.pauseMiningForFallingBlocks, false);
        fixture(s.walkWhileBreaking, true); fixture(s.sprintAscends, true); fixture(s.allowJumpAtBuildLimit, true); fixture(s.costVerificationLookahead, 2);
    }
    private static boolean saferFields() {
        var s = BaritoneAPI.getSettings();
        return s.avoidance.value && s.mobAvoidanceCoefficient.value == 4D && s.mobAvoidanceRadius.value == 10
                && s.mobSpawnerAvoidanceCoefficient.value == 4D && s.mobSpawnerAvoidanceRadius.value == 16
                && !s.allowParkour.value && !s.allowParkourPlace.value && !s.allowParkourAscend.value && !s.allowDiagonalDescend.value
                && !s.allowOvershootDiagonalDescend.value && !s.allowWaterBucketFall.value && s.maxFallHeightNoWater.value == 3
                && !s.assumeWalkOnWater.value && !s.assumeWalkOnLava.value && s.strictLiquidCheck.value
                && s.avoidUpdatingFallingBlocks.value && s.pauseMiningForFallingBlocks.value && !s.walkWhileBreaking.value
                && !s.sprintAscends.value && !s.allowJumpAtBuildLimit.value && s.costVerificationLookahead.value == 5;
    }
    private static boolean baselineMatches() {
        return BASELINE.entrySet().stream().allMatch(entry -> java.util.Objects.equals(entry.getKey().value, entry.getValue()));
    }
    private static boolean registryRestored() {
        return BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().getRegistry().entries.size() == registrySize;
    }
    private static boolean inputsReleased() {
        var handler = BaritoneAPI.getProvider().getPrimaryBaritone().getInputOverrideHandler();
        return Arrays.stream(new Input[]{Input.MOVE_FORWARD, Input.MOVE_BACK, Input.MOVE_LEFT, Input.MOVE_RIGHT,
                Input.SPRINT, Input.JUMP, Input.CLICK_LEFT, Input.CLICK_RIGHT}).noneMatch(handler::isInputForcedDown);
    }
    @SuppressWarnings({"rawtypes", "unchecked"}) private static void restoreFixture() {
        for (var entry : ORIGINAL.entrySet()) {
            // Restore the test's fields only if they have not been changed by an outside writer during the test.
            if (java.util.Objects.equals(entry.getKey().value, BASELINE.get(entry.getKey()))) ((Settings.Setting) entry.getKey()).value = entry.getValue();
        }
        try {
            if (Arrays.equals(lastTestFile, readFile())) {
                if (fileExisted) Files.write(settingsFile, originalFile);
                else Files.deleteIfExists(settingsFile);
            }
        } catch (IOException error) { throw new IllegalStateException("Cannot restore isolated settings fixture", error); }
    }
    private static byte[] readFile() {
        try { return Files.readAllBytes(settingsFile); }
        catch (IOException error) { throw new IllegalStateException("Cannot read actual Baritone settings", error); }
    }
    private static void command(MinecraftClient mc, String text) {
        mc.getServer().execute(() -> mc.getServer().getCommandManager().executeWithPrefix(mc.getServer().getCommandSource().withSilent(), text));
    }
    private static void require(boolean result, String message) {
        if (!result) throw new IllegalStateException("Safety profile check failed at " + phase + ": " + message + " / " + BRIDGE.status());
        assertions++; AnalyzerClient.LOGGER.info("Safety profile check passed: {}", message);
    }
    private static void capture(MinecraftClient mc, String name) {
        require(GL11.glGetError() == GL11.GL_NO_ERROR, "GL state before " + name);
        ScreenshotRecorder.saveScreenshot(mc.runDirectory, name, mc.getFramebuffer(), text -> AnalyzerClient.LOGGER.info("Safety profile screenshot: {}", text.getString()));
        screenshots++;
    }
}
