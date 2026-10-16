package dev.analyzer;

import dev.analyzer.gameplay.GameplayEngine;
import dev.analyzer.gameplay.SilentRotation;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.MessageScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.HuskEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
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
import java.util.concurrent.ConcurrentLinkedQueue;

/** Actual integrated-server acceptance; never loaded into a normal player session. */
public final class SilentAimSmoke {
    private static final String WORLD = "Analyzer-Silent-Aim-Smoke";
    private static final String TAG = "analyzer_silent_aim_target";
    private static final List<String> MODES = List.of("Silent movement", "Silent packet", "Silent pulse");
    private static final float CAMERA_YAW = 90, CAMERA_PITCH = 7.5f;
    private static final ConcurrentLinkedQueue<ServerAttack> SERVER_ATTACKS = new ConcurrentLinkedQueue<>();
    private static final List<String> CAPTURES = new ArrayList<>();
    private static volatile boolean serverPrepared, movingTarget;
    private static volatile ServerState serverState = new ServerState(0, 0, 0, 0, -1, Vec3d.ZERO, Vec3d.ZERO, 0, false);
    private static volatile int targetEntityId = -1;
    private static volatile String observedMode = "";
    private static volatile int written;
    private static boolean requested, setup, ready, disconnected, finished;
    private static int ticks, stage, age, modeIndex, assertions, cameraSamples, lastAttackCount, movingBefore, gateStartedAge = -1;
    private static long beforeActions, beforeTransportTick;
    private static float beforeHealth;
    private static List<SilentRotation.Sample> successfulSamples = List.of();

    private record ServerAttack(String mode, long time, int entityId, float yaw, float pitch, double error,
                                float health, double targetX, boolean rayHits, double rayDistance,
                                Vec3d rayStart, Vec3d rayEnd, Box targetBox) { }
    private record ServerState(long time, float yaw, float pitch, float health, int targetId, Vec3d targetPosition,
                               Vec3d playerPosition, int aliveTargets, boolean wall) { }
    private SilentAimSmoke() { }

    public static void tick(MinecraftClient mc) {
        if (!FabricLoader.getInstance().isDevelopmentEnvironment() || !Boolean.getBoolean("analyzer.smoke")
                || !Boolean.getBoolean("analyzer.silentAimSmoke")
                || !mc.runDirectory.toPath().toAbsolutePath().normalize().getFileName().toString().equals("run-silent-aim-smoke"))
            throw new IllegalStateException("Silent aim acceptance requires its isolated development launch");
        if (finished) return;
        if (++ticks > 2400) throw new IllegalStateException("Silent aim fixture timed out: stage=" + stage + ", mode=" + observedMode
                + ", status=" + GameplayEngine.status("kill-aura") + ", server=" + serverState);
        if (disconnected) {
            if (++age < 12) return;
            require(!SilentRotation.active() && SilentRotation.angles() == null && SilentRotation.attackAngles() == null,
                    "Disconnect clears the previously active silent rotation lease and all attack angles");
            require(written == 3 && allFilesExist(mc), "Three native framebuffer evidence files completed");
            SilentRotation.captureForChecks(false);
            finished = true;
            AnalyzerClient.LOGGER.info("ANALYZER_SILENT_AIM_COMPLETE: {} checks; {} stable camera tick samples; all three silent transports; actual server attack-time look and server health loss; cooldown/reach/obstruction, mode-change/disable/GUI/target-loss/world cleanup; three native captures", assertions, cameraSamples);
            mc.scheduleStop();
            return;
        }
        if (!requested && mc.getOverlay() == null && mc.currentScreen != null
                && mc.currentScreen.getClass().getSimpleName().equals("AccessibilityOnboardingScreen")) mc.setScreen(new TitleScreen());
        if (!requested && mc.getOverlay() == null && mc.currentScreen instanceof TitleScreen) {
            requested = true;
            AnalyzerClient.CORE.resetAll();
            for (var module : AnalyzerClient.CORE.modules()) AnalyzerClient.CORE.setEnabled(module.id(), false);
            mc.options.pauseOnLostFocus = false;
            mc.options.getViewDistance().setValue(4);
            mc.options.getSimulationDistance().setValue(5);
            mc.options.getGuiScale().setValue(2);
            mc.options.getFov().setValue(70);
            mc.options.setPerspective(Perspective.FIRST_PERSON);
            mc.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);
            mc.getToastManager().clear();
            GLFW.glfwHideWindow(mc.getWindow().getHandle());
            installServerObservers();
            HudRenderCallback.EVENT.register((draw, counter) -> {
                if (!ready || finished || disconnected || mc.currentScreen != null) return;
                draw.fill(12, 12, 388, 103, 0xC51A1820);
                draw.drawText(mc.textRenderer, "Native Kill Aura / " + observedMode, 20, 20, 0xFFF4B6E9, false);
                draw.drawText(mc.textRenderer, "Visible camera: " + mc.player.getYaw() + " / " + mc.player.getPitch(), 20, 38, 0xFFFFFFFF, false);
                draw.drawText(mc.textRenderer, "Actual server look: " + String.format(java.util.Locale.ROOT, "%.2f / %.2f", serverState.yaw(), serverState.pitch()), 20, 54, 0xFFBFEAFF, false);
                draw.drawText(mc.textRenderer, "Actual server target health: " + serverState.health(), 20, 70, 0xFFC1F7CA, false);
                draw.drawText(mc.textRenderer, "Camera stays west; target stands north", 20, 86, 0xFFD6D2DB, false);
            });
            if (Files.exists(mc.runDirectory.toPath().resolve("saves/" + WORLD + "/level.dat")))
                mc.createIntegratedServerLoader().start(WORLD, () -> mc.scheduleStop());
            else mc.createIntegratedServerLoader().createAndStart(WORLD,
                    new LevelInfo(WORLD, GameMode.SURVIVAL, false, Difficulty.NORMAL, true,
                            new GameRules(FeatureFlags.VANILLA_FEATURES), DataConfiguration.SAFE_MODE),
                    new GeneratorOptions(8261320L, false, false), WorldPresets::createDemoOptions, new TitleScreen());
        }
        if (!requested || mc.world == null || mc.player == null || mc.getServer() == null || mc.getOverlay() != null) return;
        if (!setup) { setup = true; setup(mc); return; }
        if (!ready) {
            if (!serverPrepared || mc.player.getBlockY() != 161 || target(mc) == null) return;
            mc.setScreen(null); mc.getToastManager().clear();
            ready = true; configure(); prepareMode(mc); return;
        }
        age++;
        if (stage <= 2 || stage >= 4 && stage <= 13) checkCamera(mc);
        switch (stage) {
            case 0 -> {
                if (age < 10 || Math.abs(serverState.yaw() - CAMERA_YAW) > .01) return;
                require(target(mc) != null && mc.player.canSee(target(mc)) && mc.player.canInteractWithEntity(target(mc), 0),
                        "Actual damageable target is visible and inside vanilla melee reach: " + observedMode);
                beforeHealth = serverState.health(); beforeActions = GameplayEngine.actions("kill-aura");
                beforeTransportTick = GameplayEngine.diagnostics().ticks();
                mc.player.resetLastAttackedTicks();
                SilentRotation.captureForChecks(true);
                AnalyzerClient.CORE.setEnabled("kill-aura", true);
                next(1);
            }
            case 1 -> {
                if (age <= 2) require(GameplayEngine.actions("kill-aura") == beforeActions,
                        "Uncharged actual weapon does not attack during initial acquisition: " + observedMode);
                List<ServerAttack> attacks = attacks(observedMode);
                if (attacks.isEmpty()) {
                    if (age > 180) throw new IllegalStateException("No server attack for " + observedMode + ": " + GameplayEngine.status("kill-aura")
                            + "; transport=" + SilentRotation.samplesForChecks());
                    return;
                }
                ServerAttack attack = attacks.getFirst();
                require(attack.error() <= 2.01, "Integrated server observes converged target aim at the real melee attack: " + observedMode
                        + " / yaw=" + attack.yaw() + ", pitch=" + attack.pitch() + ", error=" + attack.error());
                require(attack.rayHits(), "Actual stationary server attack ray intersects the fresh server target hitbox: " + attack);
                require(Math.abs(MathHelper.wrapDegrees(attack.yaw() - CAMERA_YAW)) > 30,
                        "Server attack-time yaw differs substantially from the unchanged local camera: " + observedMode);
                require(GameplayEngine.actions("kill-aura") == beforeActions + 1, "Exactly one ordinary melee attack was accepted before cooldown: " + observedMode);
                successfulSamples = SilentRotation.samplesForChecks();
                validateTransport(attack);
                lastAttackCount = attacks.size();
                next(2);
            }
            case 2 -> {
                require(attacks(observedMode).size() == lastAttackCount && GameplayEngine.actions("kill-aura") == beforeActions + 1,
                        "Actual sword recharge prevents repeated attacks after the accepted hit: " + observedMode);
                if (age < 4) return;
                require(serverState.health() < beforeHealth && serverState.health() > 0,
                        "Integrated server confirms real melee health loss on a living damageable target: " + observedMode);
                capture(mc, "analyzer-native-silent-aim-" + modeIndex + ".png");
                if (modeIndex == 0) {
                    movingBefore = attacks(observedMode).size();
                    beforeHealth = serverState.health();
                    movingTarget = true;
                    next(12); return;
                }
                AnalyzerClient.CORE.setEnabled("kill-aura", false);
                next(3);
            }
            case 3 -> {
                if (age < 8) return;
                checkCamera(mc);
                require(Math.abs(MathHelper.wrapDegrees(serverState.yaw() - CAMERA_YAW)) < .01
                                && Math.abs(serverState.pitch() - CAMERA_PITCH) < .01,
                        "Disabling returns actual server look to the user's unchanged camera: " + observedMode);
                require(!SilentRotation.active(), "Disabling clears the current silent ownership lease: " + observedMode);
                if (++modeIndex < MODES.size()) { prepareMode(mc); return; }
                observedMode = "Range gate";
                AnalyzerClient.CORE.setValue("kill-aura", "rotation", "Silent movement");
                AnalyzerClient.CORE.setValue("kill-aura", "range", 8D);
                command(mc, "tp @e[tag=" + TAG + ",limit=1] 15.5 161 9.5");
                beforeActions = GameplayEngine.actions("kill-aura");
                next(4);
            }
            case 4 -> {
                if (gateStartedAge < 0) {
                    if (!positionAcknowledged(mc, 9.5) || mc.player.canInteractWithEntity(target(mc), 0)) {
                        waitForFixture(mc, "Distant target teleport must synchronize before enabling range gate"); return;
                    }
                    require(true, "One exact actual server/client target acknowledges the distant teleport before range testing: " + fixtureState(mc));
                    beforeActions = GameplayEngine.actions("kill-aura");
                    AnalyzerClient.CORE.setEnabled("kill-aura", true); gateStartedAge = age;
                }
                if (age - gateStartedAge < 35) return;
                require(positionAcknowledged(mc, 9.5) && !mc.player.canInteractWithEntity(target(mc), 0), "Distant server target is outside actual vanilla attack reach: " + fixtureState(mc));
                require(GameplayEngine.actions("kill-aura") == beforeActions && attacks(observedMode).isEmpty(), "Silent transport never extends normal melee reach");
                AnalyzerClient.CORE.setEnabled("kill-aura", false);
                command(mc, "tp @e[tag=" + TAG + ",limit=1] 15.5 161 13.1");
                command(mc, "fill 14 161 14 16 164 14 minecraft:stone");
                observedMode = "Visibility gate";
                next(5);
            }
            case 5 -> {
                if (gateStartedAge < 0) {
                    if (!positionAcknowledged(mc, 13.1) || !serverState.wall()
                            || !mc.world.getBlockState(new BlockPos(15, 162, 14)).isOf(Blocks.STONE)
                            || mc.player.canSee(target(mc))) {
                        waitForFixture(mc, "Near target and real occluding wall must synchronize before visibility gate"); return;
                    }
                    require(true, "Actual server/client target and received stone wall acknowledge obstruction before visibility testing: " + fixtureState(mc));
                    beforeActions = GameplayEngine.actions("kill-aura");
                    AnalyzerClient.CORE.setEnabled("kill-aura", true); gateStartedAge = age;
                }
                if (age - gateStartedAge < 30) return;
                require(positionAcknowledged(mc, 13.1) && !mc.player.canSee(target(mc)), "Server wall creates a real occluded target inside melee range: " + fixtureState(mc));
                require(GameplayEngine.actions("kill-aura") == beforeActions && attacks(observedMode).isEmpty(), "Disabled through-wall targeting rejects the actual obstruction");
                AnalyzerClient.CORE.setEnabled("kill-aura", false);
                command(mc, "fill 14 161 14 16 164 14 minecraft:air");
                observedMode = "Mode cleanup";
                next(6);
            }
            case 6 -> {
                if (gateStartedAge < 0) {
                    if (!positionAcknowledged(mc, 13.1) || serverState.wall()
                            || !mc.world.getBlockState(new BlockPos(15, 162, 14)).isAir() || !mc.player.canSee(target(mc))) {
                        waitForFixture(mc, "Removed wall must synchronize before mode-change acquisition"); return;
                    }
                    AnalyzerClient.CORE.setValue("kill-aura", "turn-speed", 1D);
                    AnalyzerClient.CORE.setEnabled("kill-aura", true);
                    gateStartedAge = age;
                }
                if (age - gateStartedAge < 4) return;
                AnalyzerClient.CORE.setValue("kill-aura", "rotation", "None");
                beforeActions = GameplayEngine.actions("kill-aura");
                next(7);
            }
            case 7 -> {
                if (age < 10) return;
                require(GameplayEngine.actions("kill-aura") == beforeActions && attacks(observedMode).isEmpty(), "Changing away from silent aim during acquisition prevents a stale silent attack");
                require(Math.abs(MathHelper.wrapDegrees(serverState.yaw() - CAMERA_YAW)) < .01, "Mode change returns actual server yaw to ordinary camera yaw");
                require(!SilentRotation.active(), "Changing to ordinary None rotation clears the silent lease");
                AnalyzerClient.CORE.setValue("kill-aura", "rotation", "Silent movement");
                observedMode = "Menu cleanup";
                next(8);
            }
            case 8 -> {
                if (age < 4) return;
                mc.setScreen(new InventoryScreen(mc.player));
                beforeActions = GameplayEngine.actions("kill-aura");
                next(9);
            }
            case 9 -> {
                if (age < 10) return;
                require(mc.currentScreen instanceof InventoryScreen && GameplayEngine.actions("kill-aura") == beforeActions && attacks(observedMode).isEmpty(),
                        "Opening a real inventory menu stops silent acquisition and all melee interactions");
                require(Math.abs(MathHelper.wrapDegrees(serverState.yaw() - CAMERA_YAW)) < .01, "Opening a menu clears server rotation ownership");
                require(!SilentRotation.active(), "An actual native inventory screen clears the silent lease");
                mc.setScreen(null);
                observedMode = "Target cleanup";
                next(10);
            }
            case 10 -> {
                if (age < 4) return;
                command(mc, "kill @e[tag=" + TAG + "]");
                beforeActions = GameplayEngine.actions("kill-aura");
                next(11);
            }
            case 11 -> {
                if (age < 12) return;
                if (target(mc) != null || serverState.aliveTargets() != 0) { waitForFixture(mc, "Actual target removal must synchronize"); return; }
                require(target(mc) == null && GameplayEngine.target() == null && GameplayEngine.actions("kill-aura") == beforeActions,
                        "Actual server target removal clears acquisition without a stale melee action");
                require(Math.abs(MathHelper.wrapDegrees(serverState.yaw() - CAMERA_YAW)) < .01, "Losing the real target returns server look to ordinary camera yaw");
                require(!SilentRotation.active(), "Actual server target removal clears the silent lease");
                require(GL11.glGetError() == GL11.GL_NO_ERROR, "Silent aim acceptance retains clean native GL state");
                observedMode = "World cleanup";
                mc.getServer().execute(() -> spawnSingleTarget(mc));
                next(13);
            }
            case 12 -> {
                if (age < 100) return;
                movingTarget = false;
                var movingAttacks = attacks(observedMode).stream().skip(movingBefore).toList();
                require(movingAttacks.size() >= 2 && serverState.health() < beforeHealth,
                        "Native movement transport continues landing real server-confirmed melee damage while the server target moves");
                require(movingAttacks.stream().anyMatch(attack -> Math.abs(attack.targetX() - 15.5) > .03),
                        "Additional accepted server attacks observe the target at actually changed server positions");
                // The product's two-degree gate uses freshly received client state. The server's
                // continuously moving center differs during ordinary replication delay; test the
                // actual attack ray against the server's CURRENT full hitbox instead of requiring
                // the delayed client center to equal an unavailable future server center.
                require(movingAttacks.stream().allMatch(ServerAttack::rayHits),
                        "Actual moving-target server attack rays intersect the fresh server target hitbox; observations=" + movingAttacks);
                AnalyzerClient.CORE.setEnabled("kill-aura", false);
                next(3);
            }
            case 13 -> {
                if (age < 18 || target(mc) == null || !SilentRotation.active()) return;
                require(SilentRotation.active() && Math.abs(MathHelper.wrapDegrees(serverState.yaw() - CAMERA_YAW)) > 1,
                        "World cleanup begins with a real active acquisition lease and changed actual server look");
                disconnected = true; age = 0;
                mc.world.disconnect();
                mc.disconnect(new MessageScreen(Text.translatable("menu.savingLevel")));
                mc.setScreen(new TitleScreen());
            }
            default -> throw new IllegalStateException("Unexpected silent aim fixture stage " + stage);
        }
    }

    private static void installServerObservers() {
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (!world.isClient() && entity.getCommandTags().contains(TAG) && entity instanceof LivingEntity living) {
                Vec3d delta = living.getPos().add(0, living.getHeight() * .6, 0).subtract(player.getEyePos());
                float wantedYaw = (float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90);
                float wantedPitch = (float) -Math.toDegrees(Math.atan2(delta.y, delta.horizontalLength()));
                double error = Math.hypot(MathHelper.wrapDegrees(player.getYaw() - wantedYaw), player.getPitch() - wantedPitch);
                Vec3d rayStart = player.getEyePos();
                // Entity's cached rotation vector can still describe the preceding movement
                // look during an attack/restore packet batch. This event owns the actual
                // current server angles; derive its ray directly from those exact values.
                Vec3d rayEnd = rayStart.add(Vec3d.fromPolar(player.getPitch(), player.getYaw()).multiply(3));
                Box targetBox = living.getBoundingBox();
                var intersection = targetBox.raycast(rayStart, rayEnd);
                var attack = new ServerAttack(observedMode, world.getTime(), entity.getId(), player.getYaw(), player.getPitch(), error,
                        living.getHealth(), living.getX(), intersection.isPresent(),
                        intersection.map(point -> point.distanceTo(rayStart)).orElse(Double.NaN), rayStart, rayEnd, targetBox);
                SERVER_ATTACKS.add(attack);
                AnalyzerClient.LOGGER.info("ANALYZER_SILENT_AIM_SERVER_ATTACK: {}", attack);
            }
            return ActionResult.PASS;
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            var player = server.getPlayerManager().getPlayer("AnalyzerDev");
            if (player == null) return;
            float health = 0; Vec3d targetPosition = Vec3d.ZERO; int aliveTargets = 0;
            for (var entity : server.getOverworld().iterateEntities()) if (entity.getCommandTags().contains(TAG) && entity instanceof LivingEntity living) {
                if (living.isAlive() && !living.isRemoved()) aliveTargets++;
                if (entity.getId() != targetEntityId) continue;
                if (movingTarget) living.refreshPositionAndAngles(15.5 + Math.sin(server.getOverworld().getTime() * .06) * .24, 161, 13.1, 0, 0);
                health = living.getHealth(); targetPosition = living.getPos();
            }
            serverState = new ServerState(server.getOverworld().getTime(), player.getYaw(), player.getPitch(), health,
                    targetEntityId, targetPosition, player.getPos(), aliveTargets,
                    server.getOverworld().getBlockState(new BlockPos(15, 162, 14)).isOf(Blocks.STONE));
        });
    }

    private static void setup(MinecraftClient mc) {
        mc.getServer().execute(() -> {
            var server = mc.getServer();
            for (int x = -1; x <= 2; x++) for (int z = -1; z <= 2; z++) server.getOverworld().getChunk(x, z);
            // Killing an old saved husk and then setting its health would revive it. Remove
            // previous development entities completely before spawning exactly one target.
            List<Entity> stale = new ArrayList<>();
            for (var entity : server.getOverworld().iterateEntities()) if (!(entity instanceof PlayerEntity)) stale.add(entity);
            stale.forEach(Entity::discard);
            var source = server.getCommandSource().withSilent();
            for (String command : List.of("gamemode survival AnalyzerDev", "clear AnalyzerDev", "effect clear AnalyzerDev",
                    "gamerule doMobSpawning false", "gamerule doDaylightCycle false", "gamerule doWeatherCycle false", "gamerule randomTickSpeed 0",
                    "time set 1000", "weather clear", "fill 9 160 8 22 160 22 minecraft:polished_deepslate",
                    "fill 9 161 8 22 173 22 minecraft:air", "tp AnalyzerDev 15.5 161 15.5 90 7.5",
                    "item replace entity AnalyzerDev hotbar.0 with minecraft:diamond_sword"))
                server.getCommandManager().executeWithPrefix(source, command);
            spawnSingleTarget(mc);
            serverPrepared = true;
        });
    }

    /** Server-thread-only arena setup; never resurrect an old killed fixture. */
    private static void spawnSingleTarget(MinecraftClient mc) {
        var server = mc.getServer();
        List<Entity> stale = new ArrayList<>();
        for (var entity : server.getOverworld().iterateEntities()) if (entity.getCommandTags().contains(TAG)) stale.add(entity);
        stale.forEach(Entity::discard);
        server.getCommandManager().executeWithPrefix(server.getCommandSource().withSilent(),
                "summon minecraft:husk 15.5 161 13.1 {NoAI:1b,NoGravity:1b,Silent:1b,PersistenceRequired:1b,Tags:[\"" + TAG + "\"]}");
        int count = 0;
        for (var entity : server.getOverworld().iterateEntities()) if (entity.getCommandTags().contains(TAG)
                && entity instanceof HuskEntity living && living.isAlive() && !living.isRemoved()) {
            count++; targetEntityId = living.getId();
            living.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(200);
            living.getAttributeInstance(EntityAttributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
            living.setHealth(200);
        }
        if (count != 1) throw new IllegalStateException("Isolated silent aim arena requires exactly one newly spawned live target, found " + count);
        AnalyzerClient.LOGGER.info("ANALYZER_SILENT_AIM_UNIQUE_TARGET: id={}", targetEntityId);
    }

    private static void configure() {
        var core = AnalyzerClient.CORE;
        core.setValue("kill-aura", "players", false); core.setValue("kill-aura", "hostiles", true);
        core.setValue("kill-aura", "passives", false); core.setValue("kill-aura", "other-living", false);
        core.setValue("kill-aura", "mode", "Single"); core.setValue("kill-aura", "range", 3D);
        core.setValue("kill-aura", "aim-range", 0D); core.setValue("kill-aura", "fov", 360D);
        core.setValue("kill-aura", "legit", false); core.setValue("kill-aura", "hit-angle", 2D);
        core.setValue("kill-aura", "turn-speed", 8D); core.setValue("kill-aura", "smoothing", 25D);
        core.setValue("kill-aura", "aim-point", "Chest"); core.setValue("kill-aura", "through-walls", false);
        core.setValue("kill-aura", "wall-range", 0D); core.setValue("kill-aura", "require-ray", true);
        core.setValue("kill-aura", "timing", "Cooldown"); core.setValue("kill-aura", "cooldown", .95D);
        core.setValue("kill-aura", "attack-jitter", 0D); core.setValue("kill-aura", "weapon-only", true);
        core.setValue("kill-aura", "swing", "None");
    }

    private static void prepareMode(MinecraftClient mc) {
        AnalyzerClient.CORE.setEnabled("kill-aura", false);
        observedMode = MODES.get(modeIndex);
        AnalyzerClient.CORE.setValue("kill-aura", "rotation", observedMode);
        command(mc, "tp AnalyzerDev 15.5 161 15.5 90 7.5");
        command(mc, "tp @e[tag=" + TAG + ",limit=1] 15.5 161 13.1");
        next(0);
    }

    private static void validateTransport(ServerAttack attack) {
        List<SilentRotation.Sample> samples = successfulSamples;
        int attackAt = -1;
        for (int i = 0; i < samples.size(); i++) if (samples.get(i).kind().equals("attack") && samples.get(i).targetId() == attack.entityId()) { attackAt = i; break; }
        require(attackAt >= 0, "Real outbound ordinary attack packet is recorded for the exact server target: " + observedMode);
        String expected = modeIndex == 0 ? "native-look" : "aim-look";
        int lookAt = -1;
        for (int i = attackAt - 1; i >= 0; i--) if (samples.get(i).kind().equals(expected)) { lookAt = i; break; }
        require(lookAt >= 0, "Actual " + expected + " packet precedes the ordinary attack packet: " + observedMode);
        var look = samples.get(lookAt);
        require(Math.abs(MathHelper.wrapDegrees(look.yaw() - attack.yaw())) < .02 && Math.abs(look.pitch() - attack.pitch()) < .02,
                "Received attack-time server look matches the preceding actual outbound look: " + observedMode);
        require(modeIndex != 0 || samples.stream().noneMatch(sample -> sample.kind().equals("aim-look") || sample.kind().equals("restore-look")),
                "Silent movement uses the native movement frame without extra look pulses");
        if (modeIndex == 2) {
            require(samples.subList(attackAt + 1, samples.size()).stream().anyMatch(sample -> sample.kind().equals("restore-look")
                            && Math.abs(MathHelper.wrapDegrees(sample.yaw() - CAMERA_YAW)) < .01 && Math.abs(sample.pitch() - CAMERA_PITCH) < .01),
                    "Silent pulse restores the exact user look after the actual ordinary attack packet");
        } else require(samples.stream().noneMatch(sample -> sample.kind().equals("restore-look")), "Sustained silent mode does not emit attack-only restore pulses: " + observedMode);
        var looks = samples.stream().filter(sample -> sample.kind().equals(expected)).toList();
        float previousYaw = CAMERA_YAW, previousPitch = CAMERA_PITCH; long previousTick = beforeTransportTick;
        for (var sample : looks) {
            if (sample.tick() == previousTick) continue;
            long elapsed = Math.max(1, sample.tick() - previousTick);
            require(Math.abs(MathHelper.wrapDegrees(sample.yaw() - previousYaw)) <= elapsed * 8.02 && Math.abs(sample.pitch() - previousPitch) <= elapsed * 8.02,
                    "Actual server-bound acquisition stays within the elapsed eight-degree-per-tick turn budget: " + observedMode);
            previousYaw = sample.yaw(); previousPitch = sample.pitch(); previousTick = sample.tick();
        }
    }

    private static void checkCamera(MinecraftClient mc) {
        require(Math.abs(MathHelper.wrapDegrees(mc.player.getYaw() - CAMERA_YAW)) < .001
                        && Math.abs(mc.player.getPitch() - CAMERA_PITCH) < .001,
                "Local visible camera remains unchanged while native silent aim executes; stage=" + stage + ", mode=" + observedMode);
        cameraSamples++;
    }
    private static LivingEntity target(MinecraftClient mc) {
        var entity = mc.world.getEntityById(targetEntityId);
        return entity instanceof HuskEntity living && living.isAlive() && !living.isRemoved() ? living : null;
    }
    private static List<ServerAttack> attacks(String mode) { return SERVER_ATTACKS.stream().filter(attack -> attack.mode().equals(mode)).toList(); }
    private static void next(int next) { stage = next; age = 0; gateStartedAge = -1; }
    private static boolean positionAcknowledged(MinecraftClient mc, double z) {
        LivingEntity target = target(mc);
        Vec3d expected = new Vec3d(15.5, 161, z);
        return target != null && target.getPos().distanceTo(expected) < .03
                && serverState.targetId() == targetEntityId && serverState.aliveTargets() == 1
                && serverState.targetPosition().distanceTo(expected) < .03;
    }
    private static void waitForFixture(MinecraftClient mc, String reason) {
        if (age % 20 == 0) AnalyzerClient.LOGGER.info("ANALYZER_SILENT_AIM_FIXTURE_WAIT: {} / {}", reason, fixtureState(mc));
        if (age > 120) throw new IllegalStateException("Silent aim fixture acknowledgement timeout: " + reason + " / " + fixtureState(mc));
    }
    private static String fixtureState(MinecraftClient mc) {
        var target = target(mc);
        return "clientPlayer=" + mc.player.getPos() + ", targetId=" + targetEntityId + ", clientTarget="
                + (target == null ? null : target.getPos()) + ", actualReach=" + (target != null && mc.player.canInteractWithEntity(target, 0))
                + ", visible=" + (target != null && mc.player.canSee(target)) + ", server=" + serverState
                + ", configuredRange=" + AnalyzerClient.CORE.getNumber("kill-aura", "range");
    }
    private static void command(MinecraftClient mc, String command) {
        mc.getServer().execute(() -> mc.getServer().getCommandManager().executeWithPrefix(mc.getServer().getCommandSource().withSilent(), command));
    }
    private static void capture(MinecraftClient mc, String file) {
        require(GL11.glGetError() == GL11.GL_NO_ERROR, "Clean GL state before actual framebuffer capture: " + observedMode);
        CAPTURES.add(file);
        ScreenshotRecorder.saveScreenshot(mc.runDirectory, file, mc.getFramebuffer(), message -> { written++; AnalyzerClient.LOGGER.info("Native silent aim capture: {} / {}", file, message.getString()); });
    }
    private static boolean allFilesExist(MinecraftClient mc) {
        try {
            for (String file : CAPTURES) if (!Files.isRegularFile(mc.runDirectory.toPath().resolve("screenshots").resolve(file))
                    || Files.size(mc.runDirectory.toPath().resolve("screenshots").resolve(file)) < 1024) return false;
            return CAPTURES.size() == 3;
        } catch (java.io.IOException failure) { throw new IllegalStateException("Cannot verify native silent aim screenshots", failure); }
    }
    private static void require(boolean valid, String message) {
        if (!valid) throw new IllegalStateException("Silent aim native acceptance: " + message);
        assertions++; AnalyzerClient.LOGGER.info("ANALYZER_SILENT_AIM_ASSERT: {}", message);
    }
}
