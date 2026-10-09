package dev.analyzer;

import dev.analyzer.automation.AutomationEngine;
import dev.analyzer.automation.EscapeNavigator;
import dev.analyzer.automation.InventoryAutomation;
import dev.analyzer.automation.TaskPlanner;
import net.minecraft.block.Blocks;
import net.minecraft.block.SnowBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import org.lwjgl.opengl.GL11;
import java.util.List;
import java.util.LinkedHashMap;

/** Local, real-physics regression fixtures for the lake/creeper safety-hold report. */
final class TerrainSafetySmoke {
    private static final AutomationEngine ENGINE = AutomationEngine.INSTANCE;
    private static int stage, since, assertions, screenshots, holdAt, blastAt;
    private static int manualGoalX, manualGoalZ;
    private static long retreat, foodUses, mixedAttacks;
    private static boolean done, damaged, airborne, landed, held, resumed, leftWater, setupRequested, blastPondCreated;
    private static Vec3d start, pausedAt;
    private static float blastHealth;
    private static String originalBlockedReason;
    static boolean done() { return done; }

    static void tick(MinecraftClient mc, int t) {
        if (done) return;
        var core = AnalyzerClient.CORE;
        int age = t - since;
        switch (stage) {
            case 0 -> {
                if (!setupRequested) {
                ENGINE.stop(); core.resetAll(); mc.setScreen(null);
                mc.options.setPerspective(Perspective.FIRST_PERSON);
                core.setValue("expedition", "resume-delay", .5);
                core.setValue("expedition", "auto-food", true);
                command(mc, "kill @e[type=!minecraft:player]");
                command(mc, "gamemode creative AnalyzerDev");
                command(mc, "clear AnalyzerDev"); command(mc, "effect clear AnalyzerDev");
                command(mc, "difficulty normal");
                command(mc, "forceload add 200 200 250 245");
                setupRequested = true; since = t;
                } else if (age >= 45) {
                command(mc, "fill 200 160 200 250 160 245 minecraft:bedrock");
                command(mc, "fill 200 161 200 250 170 245 minecraft:air");
                command(mc, "fill 200 161 200 250 161 245 minecraft:snow[layers=3]");
                command(mc, "fill 208 159 205 219 159 218 minecraft:bedrock");
                command(mc, "fill 208 160 205 219 160 218 minecraft:water");
                command(mc, "fill 208 161 205 219 161 218 minecraft:air");
                command(mc, "fill 222 160 205 224 160 218 minecraft:ice");
                command(mc, "fill 228 161 205 230 161 218 minecraft:stone");
                command(mc, "fill 228 162 205 230 162 218 minecraft:snow[layers=3]");
                command(mc, "gamerule doMobSpawning false");
                command(mc, "gamerule doDaylightCycle false");
                command(mc, "gamerule naturalRegeneration false");
                command(mc, "time set 18000"); command(mc, "weather clear");
                command(mc, "tp AnalyzerDev 217.5 160 212.5 -90 12");
                command(mc, "gamemode survival AnalyzerDev");
                command(mc, "item replace entity AnalyzerDev hotbar.0 with minecraft:spruce_log 15");
                command(mc, "effect give AnalyzerDev minecraft:instant_health 1 5 true");
                command(mc, "effect give AnalyzerDev minecraft:saturation 1 5 true");
                next(t);
                }
            }
            case 1 -> { if (age >= 25) {
                require(mc.interactionManager.getCurrentGameMode() == GameMode.SURVIVAL,
                        "Lake regression runs in real Survival mode");
                require(mc.world.isNight() && mc.player.isTouchingWater(), "Player starts at night in actual shallow water");
                require(mc.player.getArmor() == 0 && !mc.player.getMainHandStack().isOf(Items.DIAMOND_SWORD),
                        "Lake fixture has no armor or combat weapon");
                ENGINE.submit(travel("Lake creeper safety", 244, 161, 212));
                retreat = ENGINE.safetyDiagnostics().retreatTicks(); next(t);
            }}
            case 2 -> {
                if (ENGINE.pathStatus().owned() && age >= 2) {
                    start = mc.player.getPos();
                    command(mc, "summon minecraft:creeper " + (start.x - 3.6) + " " + start.y + " " + start.z
                            + " {CustomName:'\"AnalyzerLakeCreeper\"',PersistenceRequired:1b}");
                    next(t);
                } else timeout(age, 120, "Lake route did not acquire an owned Baritone process");
            }
            case 3 -> {
                var creeper = mob(mc, "AnalyzerLakeCreeper");
                if (!damaged && ENGINE.safetyDiagnostics().intervening()) {
                    command(mc, "damage AnalyzerDev 14 minecraft:generic"); damaged = true;
                }
                failIfDead(mc, "shallow-water creeper retreat");
                requireNotBlocked("Water or partial snow must not permanently block the live safety controller");
                if (creeper != null && mc.player.getHealth() <= 6 && ENGINE.safetyDiagnostics().retreatTicks() - retreat >= 12
                        && mc.player.distanceTo(creeper) > 4.6 && mc.player.getPos().distanceTo(start) >= 1) {
                    require(ENGINE.currentStep() == 0 && ENGINE.isRunning(), "Critical-health swimming retains the original live task stage");
                    require(ENGINE.pathStatus().paused(), "Swimming escape keeps the owned Baritone route paused");
                    require(mc.player.isAlive() && !mc.player.isInLava(), "Unarmored six-HP player escapes on real checked terrain");
                    capture(mc, "analyzer-safety-water-retreat.png"); next(t);
                } else timeout(age, 260, "Actual swimming did not gain separation from the normal-AI creeper");
            }
            case 4 -> {
                failIfDead(mc, "water-to-snow shore escape");
                requireNotBlocked("An active water-to-shore escape must keep monitoring");
                if (standingOnLayeredSnow(mc)) leftWater = true;
                if (leftWater && age >= 5) {
                    require(standingOnLayeredSnow(mc),
                            "Actual retreat stands on the partial collision surface of layered shoreline snow");
                    require(ENGINE.currentStep() == 0, "Shore transition preserves the task cursor");
                    capture(mc, "analyzer-safety-snow-shore.png");
                    mc.getServer().execute(() -> {
                        var p = mc.getServer().getPlayerManager().getPlayer("AnalyzerDev");
                        p.addVelocity(0, .38, 0); p.velocityModified = true;
                    });
                    next(t);
                } else timeout(age, 220, "Water escape did not reach the snow-covered shoreline");
            }
            case 5 -> {
                failIfDead(mc, "transient knockback"); requireNotBlocked("Temporary airborne knockback must not permanently block protection");
                if (!mc.player.isOnGround()) airborne = true;
                if (airborne && mc.player.isOnGround() && age > 8) landed = true;
                if (landed) {
                    require(airborne, "Authoritative server impulse produces actual temporary airborne physics");
                    require(ENGINE.isRunning() && ENGINE.currentStep() == 0, "Protection remains live after landing without explicit Resume");
                    ENGINE.pause(); command(mc, "kill @e[name=AnalyzerLakeCreeper]"); next(t);
                } else timeout(age, 100, "Knockback fixture did not leave the ground and land");
            }
            case 6 -> {
                if (age == 10) pausedAt = mc.player.getPos();
                if (age >= 25) {
                    require(ENGINE.state().equals("PAUSED"), "Manual Pause still takes precedence over terrain recovery");
                    require(mc.player.getPos().distanceTo(pausedAt) < .3, "Manual Pause releases owned swim/jump movement");
                    require(!mc.options.forwardKey.isPressed() && !mc.options.jumpKey.isPressed(), "Paused terrain guard releases its held movement keys");
                    ENGINE.stop();
                    command(mc, "clear AnalyzerDev");
                    command(mc, "item replace entity AnalyzerDev hotbar.0 with minecraft:spruce_log 15");
                    command(mc, "effect give AnalyzerDev minecraft:instant_health 1 5 true");
                    command(mc, "effect give AnalyzerDev minecraft:saturation 1 5 true");
                    command(mc, "tp AnalyzerDev 238.5 161.6 232.5 -90 0");
                    next(t);
                }
            }
            case 7 -> { if (age >= 20) { ENGINE.submit(travel("Recover held safety", 247, 161, 232)); next(t); }}
            case 8 -> {
                if (ENGINE.pathStatus().owned() && age >= 2) {
                    // A one-cell chamber with a one-cell-high eye window: visible threat, no player-sized exit.
                    command(mc, "tp AnalyzerDev 238.5 161.6 232.5 -90 0");
                    command(mc, "fill 237 161 231 239 164 233 minecraft:bedrock hollow");
                    command(mc, "setblock 238 161 232 minecraft:air"); command(mc, "setblock 238 162 232 minecraft:air");
                    command(mc, "setblock 237 162 232 minecraft:air");
                    command(mc, "summon minecraft:creeper 232.5 161.6 232.5 {CustomName:'\"AnalyzerHoldCreeper\"',PersistenceRequired:1b}");
                    next(t);
                } else timeout(age, 120, "Hold fixture route did not become owned");
            }
            case 9 -> {
                failIfDead(mc, "enclosed safety hold");
                timeout(age, 140, "Enclosed unarmed player did not enter and retain live SAFETY_HOLD");
                if (ENGINE.state().equals("SAFETY_HOLD")) {
                    if (!held) {
                        require(ENGINE.isRunning() && ENGINE.currentStep() == 0, "No escape enters a live SAFETY_HOLD instead of stopping guard updates");
                        require(ENGINE.pathStatus().paused(), "No escape keeps original Baritone route safely paused");
                        holdAt = t; held = true; command(mc, "damage AnalyzerDev 14 minecraft:generic");
                        mc.getServer().execute(() -> {
                            var p = mc.getServer().getPlayerManager().getPlayer("AnalyzerDev");
                            p.getHungerManager().setFoodLevel(8); p.getHungerManager().setSaturationLevel(0);
                        });
                    }
                    if (held && t - holdAt >= 20 && mc.player.getHealth() <= 6) {
                        capture(mc, "analyzer-safety-live-hold.png");
                        command(mc, "kill @e[name=AnalyzerHoldCreeper]");
                        command(mc, "setblock 239 161 232 minecraft:air"); command(mc, "setblock 239 162 232 minecraft:air");
                        next(t);
                    }
                }
            }
            case 10 -> { if (age >= 25) {
                require(ENGINE.isRunning() && ENGINE.currentStep() == 0 && !ENGINE.state().equals("RUNNING"),
                        "Clearing threat and opening terrain does not restart work with six HP and no food");
                require(mc.player.getHealth() <= 6, "Low health remains genuine while no recovery food exists");
                require(!ENGINE.state().equals("BLOCKED"), "Live hold continues to re-evaluate recovery after the creeper disappears");
                foodUses = ENGINE.safetyDiagnostics().foodUses();
                command(mc, "item replace entity AnalyzerDev hotbar.1 with minecraft:cooked_beef 6");
                command(mc, "gamerule naturalRegeneration true"); next(t);
            }}
            case 11 -> {
                failIfDead(mc, "held-task food recovery"); requireNotBlocked("Food recovery must continue from safety hold");
                if (ENGINE.state().equals("RUNNING")) resumed = true;
                if (ENGINE.state().equals("COMPLETED")) {
                    require(ENGINE.safetyDiagnostics().foodUses() > foodUses && InventoryAutomation.count(Items.COOKED_BEEF) < 6,
                            "Live hold consumes actual server food added after the threat clears");
                    require(mc.player.getHealth() >= core.getNumber("expedition", "retreat-health"),
                            "Normal regeneration restores required health before route resumes");
                    require(resumed && ENGINE.currentStep() == 1, "Original held task resumes and completes without pressing Resume");
                    require(mc.player.getX() > 245, "Resumed path exits the newly opened player-sized passage");
                    capture(mc, "analyzer-safety-hold-recovered.png");
                    prepareBlast(mc); next(t);
                } else timeout(age, 700, "Live safety hold did not recover from actual food and finish its original route");
            }
            case 12 -> { if (age >= 25) {
                require(mc.player.getHealth() >= 19.9 && !mc.player.isTouchingWater() && mc.player.isOnGround(),
                        "Original blast fixture route starts healthy on actual supported dry land");
                require(mc.player.getArmor() == 0, "Actual explosion fixture has no defensive armor");
                ENGINE.submit(travel("Blast survival recovery", 234, 161, 237)); next(t);
            }}
            case 13 -> {
                if (!blastPondCreated && ENGINE.pathStatus().owned() && age >= 2) {
                    command(mc, "fill 219 159 235 225 159 239 minecraft:bedrock");
                    command(mc, "fill 219 160 235 225 160 239 minecraft:water");
                    command(mc, "fill 219 161 235 225 161 239 minecraft:air"); blastPondCreated = true;
                }
                if (blastPondCreated && mc.player.isTouchingWater()) {
                    require(mc.player.getHealth() >= 19.9 && mc.player.getArmor() == 0,
                            "Player actually enters recessed water healthy and unarmored before the blast mob is spawned");
                    require(ENGINE.pathStatus().owned(), "Original valid dry-land task remains owned after the real water entry");
                    var p = mc.player.getPos();
                    command(mc, "summon minecraft:creeper " + (p.x - 2.5) + " 160 " + p.z
                            + " {CustomName:'\"AnalyzerBlastCreeper\"',PersistenceRequired:1b,ignited:1b,Fuse:30s}");
                    blastAt = t; next(t);
                } else timeout(age, 120, "Valid blast route did not become owned and enter actual water");
            }
            case 14 -> {
                failIfDead(mc, "real creeper explosion"); requireNotBlocked("Actual blast and knockback must not terminate the live guard");
                var creeper = mob(mc, "AnalyzerBlastCreeper");
                if (creeper == null && t - blastAt >= 32 && mc.player.getHealth() < 20) {
                    blastHealth = mc.player.getHealth();
                    require(blastHealth > 0 && blastHealth < 20, "Real 30-tick ignited creeper explosion damages but does not kill this fixture player");
                    require(ENGINE.currentStep() == 0 && ENGINE.isRunning(), "Original task remains live immediately after actual explosion");
                    capture(mc, "analyzer-safety-after-real-blast.png");
                    foodUses = ENGINE.safetyDiagnostics().foodUses();
                    mc.getServer().execute(() -> {
                        var p = mc.getServer().getPlayerManager().getPlayer("AnalyzerDev");
                        p.getHungerManager().setFoodLevel(8); p.getHungerManager().setSaturationLevel(0);
                    });
                    command(mc, "item replace entity AnalyzerDev hotbar.1 with minecraft:cooked_beef 5");
                    command(mc, "gamerule naturalRegeneration true"); next(t);
                } else timeout(age, 100, "Ignited creeper did not produce actual nonlethal explosion damage");
            }
            case 15 -> {
                failIfDead(mc, "post-blast food recovery"); requireNotBlocked("Post-blast controller must remain active during food recovery");
                if (ENGINE.state().equals("COMPLETED")) {
                    require(ENGINE.safetyDiagnostics().foodUses() > foodUses && InventoryAutomation.count(Items.COOKED_BEEF) < 5,
                            "Guard eats actual food after the real creeper blast");
                    require(mc.player.getHealth() > blastHealth, "Post-blast recovery uses normal regeneration, without a healing fixture command");
                    require(ENGINE.currentStep() == 1 && !ENGINE.pathStatus().owned(), "Original post-blast task completes and releases its route");
                    require(!ENGINE.routingSafety().enabled() && !ENGINE.safetyDiagnostics().intervening(), "Completed recovered task releases scoped routing and guard control");
                    capture(mc, "analyzer-safety-blast-recovered.png");
                    core.setValue("expedition", "retreat-health", 10);
                    ENGINE.stop(); command(mc, "kill @e[type=!minecraft:player]");
                    command(mc, "clear AnalyzerDev");
                    command(mc, "effect give AnalyzerDev minecraft:instant_health 1 5 true");
                    command(mc, "effect give AnalyzerDev minecraft:saturation 1 5 true");
                    command(mc, "item replace entity AnalyzerDev hotbar.0 with minecraft:diamond_sword");
                    command(mc, "fill 200 161 221 212 163 221 minecraft:bedrock");
                    command(mc, "fill 200 161 224 212 163 224 minecraft:bedrock");
                    command(mc, "tp AnalyzerDev 205.5 161.6 223 -90 0");
                    next(t);
                } else timeout(age, 650, "Original route failed to recover after a real explosion and actual food");
            }
            case 16 -> { if (age >= 20) {
                ENGINE.submit(travel("Prioritize imminent blast", 211, 161, 223)); next(t);
            }}
            case 17 -> {
                if (ENGINE.pathStatus().owned() && age >= 2) {
                    start = mc.player.getPos(); retreat = ENGINE.safetyDiagnostics().retreatTicks();
                    mixedAttacks = ENGINE.safetyDiagnostics().attacks();
                    command(mc, "summon minecraft:creeper " + (start.x - 4) + " 161.6 223 {CustomName:'\"AnalyzerPriorityCreeper\"',PersistenceRequired:1b}");
                    command(mc, "summon minecraft:zombie " + (start.x + 4) + " 161.6 223 {CustomName:'\"AnalyzerPriorityZombie\"',PersistenceRequired:1b}");
                    next(t);
                } else timeout(age, 120, "Mixed-threat fixture did not acquire an owned route");
            }
            case 18 -> {
                failIfDead(mc, "mixed-creeper priority"); requireNotBlocked("A lesser mob must not veto the only checked escape from a nearby creeper");
                var creeper = mob(mc, "AnalyzerPriorityCreeper"); var zombie = mob(mc, "AnalyzerPriorityZombie");
                if (creeper != null && zombie != null && ENGINE.safetyDiagnostics().retreatTicks() - retreat >= 3) {
                    var route = EscapeNavigator.choose(mc, List.of(creeper, zombie));
                    require(route != null && route.direction().x > .75,
                            "Actual narrow-corridor survey prioritizes increasing creeper distance even toward a lesser zombie threat");
                    require(ENGINE.state().equals("PROTECTING") && ENGINE.pathStatus().paused(),
                            "Live mixed-threat guard pauses original work and retreats from the creeper");
                    require(ENGINE.safetyDiagnostics().attacks() == mixedAttacks,
                            "Blast emergency is handled before ordinary zombie combat");
                    require(mc.player.getX() > start.x, "Real mixed-threat movement follows the checked blast escape corridor");
                    capture(mc, "analyzer-safety-creeper-priority.png");
                    ENGINE.stop(); command(mc, "kill @e[type=!minecraft:player]");
                    command(mc, "tp AnalyzerDev 207.5 161.6 223 -90 0");
                    command(mc, "fill 209 161 222 211 161 223 minecraft:stone");
                    command(mc, "summon minecraft:creeper 203.5 161.6 223 {CustomName:'\"AnalyzerStepSurveyCreeper\"',NoAI:1b,PersistenceRequired:1b}");
                    next(t);
                } else timeout(age, 100, "Actual mixed-threat guard did not select the blast escape corridor");
            }
            case 19 -> { if (age >= 20) {
                    var creeper = mob(mc, "AnalyzerStepSurveyCreeper");
                    require(creeper != null && mc.player.isOnGround(), "One-block step survey uses a loaded actual world and entity fixture");
                    var route = EscapeNavigator.choose(mc, List.of(creeper));
                    require(route != null && route.direction().x > .75 && route.jump() && !route.swimming(),
                            "Checked land corridor exposes a required jump over a real one-block stone rise");
                    require(route.terrain().contains("step"), "Height-aware route identifies the checked supported one-block step");
                    capture(mc, "analyzer-safety-one-block-survey.png");
                    ENGINE.stop(); command(mc, "kill @e[type=!minecraft:player]");
                    command(mc, "clear AnalyzerDev");
                    command(mc, "item replace entity AnalyzerDev hotbar.0 with minecraft:spruce_log 15");
                    command(mc, "effect give AnalyzerDev minecraft:instant_health 1 5 true");
                    command(mc, "effect give AnalyzerDev minecraft:saturation 1 5 true"); next(t);
            }}
            case 20 -> { if (age >= 20) {
                ENGINE.submit(new TaskPlanner.Plan("Preserve blocked mining stage", "Actual missing-tool safety regression",
                        List.of(new TaskPlanner.Step("iron", TaskPlanner.Action.MINE, "Mine iron without a valid pickaxe", "raw_iron", 1, 0, 0, 0)), List.of()));
                next(t);
            }}
            case 21 -> {
                if (ENGINE.state().equals("BLOCKED")) {
                    originalBlockedReason = ENGINE.detail();
                    require(originalBlockedReason.contains("Iron ore needs a stone-tier pickaxe"),
                            "Missing actual mining tool genuinely blocks the original stage");
                    require(!ENGINE.pathStatus().owned() && ENGINE.currentStep() == 0,
                            "Blocked prerequisite has no owned Baritone process and keeps its stage cursor");
                    retreat = ENGINE.safetyDiagnostics().retreatTicks();
                    var p = mc.player.getPos();
                    command(mc, "summon minecraft:creeper " + (p.x - 6) + " " + p.y + " " + p.z
                            + " {CustomName:'\"AnalyzerBlockedCreeper\"',PersistenceRequired:1b}");
                    next(t);
                } else timeout(age, 80, "Missing pickaxe did not genuinely block the mining stage");
            }
            case 22 -> {
                failIfDead(mc, "defense of an already blocked task");
                if (ENGINE.safetyDiagnostics().intervening() && ENGINE.safetyDiagnostics().retreatTicks() - retreat >= 3) {
                    require(ENGINE.currentStep() == 0 && !ENGINE.pathStatus().owned(),
                            "Blocked-task defense protects the player without dispatching the unsatisfied mining stage");
                    require(ENGINE.state().equals("PROTECTING") || ENGINE.state().equals("SAFETY_HOLD"),
                            "An already blocked live task can still run its survival guard");
                    command(mc, "kill @e[name=AnalyzerBlockedCreeper]"); next(t);
                } else timeout(age, 120, "Already blocked task did not continue protecting against a normal-AI creeper");
            }
            case 23 -> {
                failIfDead(mc, "preserving original blocked task after defense");
                if (ENGINE.state().equals("BLOCKED") && !ENGINE.safetyDiagnostics().intervening() && age >= 15) {
                    require(ENGINE.detail().equals(originalBlockedReason),
                            "Threat clearance restores the exact original missing-tool block reason");
                    require(ENGINE.currentStep() == 0 && !ENGINE.pathStatus().owned() && !ENGINE.isRunning(),
                            "Threat clearance never resumes or completes an originally blocked mining stage");
                    require(InventoryAutomation.count(Items.RAW_IRON) == 0,
                            "Blocked-stage preservation does not claim or create mined resources");
                    ENGINE.stop(); command(mc, "kill @e[type=!minecraft:player]");
                    command(mc, "fill 220 160 220 250 160 244 minecraft:bedrock");
                    command(mc, "fill 220 161 220 250 170 244 minecraft:air");
                    command(mc, "tp AnalyzerDev 228.5 161.1 237.5 -90 0");
                    command(mc, "effect give AnalyzerDev minecraft:instant_health 1 5 true");
                    command(mc, "effect give AnalyzerDev minecraft:saturation 1 5 true"); next(t);
                } else timeout(age, 120, "Threat clearance did not preserve the original missing-tool block");
            }
            case 24 -> { if (age >= 20) {
                require(mc.player.isOnGround() && !mc.player.isTouchingWater(), "Manual takeover fixture starts on actual plain dry land");
                ENGINE.submit(travel("Resume original task after manual route", 234, 161, 237)); next(t);
            }}
            case 25 -> {
                if (ENGINE.pathStatus().owned() && age >= 2) {
                    manualGoalX = mc.player.getBlockX(); manualGoalZ = mc.player.getBlockZ() + 2;
                    baritone.api.BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(
                            new baritone.api.pathing.goals.GoalBlock(manualGoalX, 161, manualGoalZ)); next(t);
                } else timeout(age, 100, "Original task did not become owned for actual manual takeover");
            }
            case 26 -> {
                if (ENGINE.state().equals("BLOCKED") && ENGINE.pathStatus().state() == dev.analyzer.automation.BaritoneBridge.State.INTERRUPTED) {
                    require(!ENGINE.pathStatus().owned() && ENGINE.currentStep() == 0,
                            "Actual public Baritone manual goal interrupts ownership while preserving the Analyzer stage");
                    require(baritone.api.BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().isActive(),
                            "Analyzer leaves the replacement manual route active"); next(t);
                } else timeout(age, 60, "Actual manual goal replacement did not interrupt the Analyzer engine");
            }
            case 27 -> {
                if (!baritone.api.BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().isActive()
                        && mc.player.getBlockX() == manualGoalX && mc.player.getBlockZ() == manualGoalZ) {
                    require(ENGINE.state().equals("BLOCKED") && ENGINE.currentStep() == 0,
                            "Finishing the manual route does not silently restart the interrupted Analyzer plan");
                    require(ENGINE.pathStatus().state() == dev.analyzer.automation.BaritoneBridge.State.INTERRUPTED,
                            "Completed manual route leaves an observable unowned interruption until explicit Resume");
                    ENGINE.resume(); next(t);
                } else timeout(age, 160, "Replacement manual route did not reach its real destination");
            }
            case 28 -> {
                if (ENGINE.state().equals("COMPLETED")) {
                    require(ENGINE.currentStep() == 1 && mc.player.getBlockX() == 234 && mc.player.getBlockZ() == 237,
                            "Explicit Resume clears the stale unowned interruption and completes the original goal");
                    require(!ENGINE.pathStatus().owned(), "Resumed original task releases its completed route");
                    core.setValue("expedition", "pause-on-menu", false);
                    ENGINE.submit(travel("Menu clears active survival control", 228, 161, 237)); next(t);
                } else timeout(age, 200, "Explicit Resume did not continue the original interrupted stage");
            }
            case 29 -> {
                if (ENGINE.pathStatus().owned() && age >= 2) {
                    var p = mc.player.getPos();
                    command(mc, "summon minecraft:creeper " + (p.x + 6) + " 161 " + p.z
                            + " {CustomName:'\"AnalyzerMenuCreeper\"',PersistenceRequired:1b}"); next(t);
                } else timeout(age, 100, "Menu lifecycle fixture did not become owned");
            }
            case 30 -> {
                if (ENGINE.state().equals("PROTECTING")) {
                    require(ENGINE.safetyDiagnostics().intervening(), "Actual nearby creeper activates the guard before menu opens");
                    mc.setScreen(new net.minecraft.client.gui.screen.ingame.InventoryScreen(mc.player)); next(t);
                } else timeout(age, 100, "Actual creeper did not activate protection for the menu regression");
            }
            case 31 -> { if (age >= 6) {
                require(ENGINE.state().equals("PAUSED") && !ENGINE.safetyDiagnostics().intervening(),
                        "Opening a manual inventory clears active protection into deliberate Pause even with pause-on-menu disabled");
                require(!mc.options.forwardKey.isPressed() && !mc.options.jumpKey.isPressed() && !mc.options.useKey.isPressed(),
                        "Manual inventory pause releases guard movement and held use inputs");
                require(ENGINE.pathStatus().paused(), "Manual inventory lifecycle keeps the original route paused");
                mc.setScreen(null); command(mc, "kill @e[name=AnalyzerMenuCreeper]"); next(t);
            }}
            case 32 -> { if (age >= 15) {
                    require(ENGINE.state().equals("PAUSED") && ENGINE.currentStep() == 0,
                            "Closing the manual inventory never automatically restarts the deliberately paused task");
                    core.setValue("expedition", "pause-on-menu", true);
                    ENGINE.stop(); command(mc, "kill @e[type=!minecraft:player]");
                    command(mc, "gamerule doDaylightCycle true"); command(mc, "gamerule doMobSpawning true");
                    command(mc, "gamerule naturalRegeneration true"); command(mc, "gamemode creative AnalyzerDev");
                    command(mc, "forceload remove 200 200 250 245");
                    require(GL11.glGetError() == GL11.GL_NO_ERROR, "Terrain safety final GL state");
                    done = true;
                    AnalyzerClient.LOGGER.info("ANALYZER_TERRAIN_SAFETY_COMPLETE: {} assertions, {} screenshots; actual shallow-water/snow escape, airborne knockback, live safety-hold recovery, real creeper explosion, mixed-threat priority, one-block land-step survey, original blocked-stage preservation, explicit Resume after manual takeover and manual-menu lifecycle", assertions, screenshots);
            }}
        }
    }

    private static void prepareBlast(MinecraftClient mc) {
        ENGINE.stop();
        // This test deliberately requests 18 HP before recovery resumes; normal default remains 10.
        AnalyzerClient.CORE.setValue("expedition", "retreat-health", 18);
        command(mc, "clear AnalyzerDev"); command(mc, "effect clear AnalyzerDev");
        command(mc, "gamerule naturalRegeneration false");
        command(mc, "fill 218 161 234 235 161 240 minecraft:air");
        command(mc, "tp AnalyzerDev 221.5 161.6 237.5 -90 0");
        command(mc, "item replace entity AnalyzerDev hotbar.0 with minecraft:spruce_log 15");
        command(mc, "effect give AnalyzerDev minecraft:instant_health 1 5 true");
        command(mc, "effect give AnalyzerDev minecraft:saturation 1 5 true");
    }
    private static void next(int t) { stage++; since = t; }
    private static TaskPlanner.Plan travel(String title, int x, int y, int z) {
        return new TaskPlanner.Plan(title, "Local terrain survival regression", List.of(new TaskPlanner.Step("travel", TaskPlanner.Action.TRAVEL, "Walk to the original goal", "", 0, x, y, z)), List.of());
    }
    private static LivingEntity mob(MinecraftClient mc, String name) {
        for (var e : mc.world.getEntities()) if (e instanceof LivingEntity living && e.getName().getString().equals(name) && living.isAlive()) return living;
        return null;
    }
    private static void failIfDead(MinecraftClient mc, String context) {
        if (!mc.player.isAlive()) throw new IllegalStateException("Terrain safety stage " + stage + ": player died during " + context + " / " + playerState() + " / " + terrainState());
    }
    private static void requireNotBlocked(String message) {
        if (ENGINE.state().equals("BLOCKED") || ENGINE.state().equals("STOPPED")) throw new IllegalStateException("Terrain safety stage " + stage + ": " + message + " / " + ENGINE.detail() + " / " + playerState() + " / " + terrainState());
    }
    private static void timeout(int age, int limit, String message) {
        if (age > limit) throw new IllegalStateException("Terrain safety stage " + stage + ": " + message + " / " + ENGINE.state() + " / " + ENGINE.detail() + " / " + ENGINE.pathStatus() + " / " + ENGINE.safetyDiagnostics() + " / " + playerState() + " / " + terrainState());
    }
    private static void command(MinecraftClient mc, String command) {
        mc.getServer().execute(() -> mc.getServer().getCommandManager().executeWithPrefix(mc.getServer().getCommandSource().withSilent(), command));
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("Terrain safety stage " + stage + ": " + message + " / " + ENGINE.state() + " / " + ENGINE.detail() + " / " + playerState() + " / " + terrainState());
        assertions++; AnalyzerClient.LOGGER.info("Terrain safety check passed: {}", message);
    }
    private static void capture(MinecraftClient mc, String name) {
        require(GL11.glGetError() == GL11.GL_NO_ERROR, "GL state before " + name);
        ScreenshotRecorder.saveScreenshot(mc.runDirectory, name, mc.getFramebuffer(), text -> AnalyzerClient.LOGGER.info("Terrain safety screenshot: {}", text.getString())); screenshots++;
    }
    private static String playerState() {
        var p = MinecraftClient.getInstance().player;
        return p == null ? "No local player" : "position=" + p.getPos() + ", HP=" + p.getHealth()
                + ", food=" + p.getHungerManager().getFoodLevel() + ", water=" + p.isTouchingWater() + ", grounded=" + p.isOnGround();
    }
    private static boolean standingOnLayeredSnow(MinecraftClient mc) {
        var p = mc.player;
        if (p.isTouchingWater() || !p.isOnGround() || p.getX() < 200 || p.getX() >= 251 || p.getZ() < 200 || p.getZ() >= 246) return false;
        var floor = BlockPos.ofFloored(p.getX(), p.getBoundingBox().minY - .02, p.getZ());
        var state = mc.world.getBlockState(floor);
        if (!state.isOf(Blocks.SNOW) || state.get(SnowBlock.LAYERS) <= 1 || state.get(SnowBlock.LAYERS) >= 8) return false;
        var shape = state.getCollisionShape(mc.world, floor);
        return !shape.isEmpty() && shape.getBoundingBox().maxY < 1
                && Math.abs(p.getBoundingBox().minY - (floor.getY() + shape.getBoundingBox().maxY)) < .03;
    }
    private static String terrainState() {
        var mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return "No terrain available";
        var p = mc.player;
        var cells = new LinkedHashMap<BlockPos, String>();
        for (double dx : new double[]{-.31, 0, .31}) for (double dz : new double[]{-.31, 0, .31})
            for (int dy = -1; dy <= 2; dy++) {
                var pos = BlockPos.ofFloored(p.getX() + dx, p.getY() + dy, p.getZ() + dz);
                var state = mc.world.getBlockState(pos);
                cells.put(pos, state + ": collision=" + state.getCollisionShape(mc.world, pos).getBoundingBoxes()
                        + ", fluid=" + state.getFluidState());
            }
        var threats = new java.util.ArrayList<LivingEntity>();
        for (var entity : mc.world.getEntities()) if (entity instanceof net.minecraft.entity.mob.MobEntity living
                && living instanceof net.minecraft.entity.mob.Monster && living.isAlive()
                && living.getName().getString().startsWith("Analyzer")) threats.add(living);
        var mobs = threats.stream().map(e -> e.getName().getString() + "@" + e.getPos() + ", distance=" + p.distanceTo(e)).toList();
        return "body=" + p.getBoundingBox() + ", pose=" + p.getPose() + ", bodyClear=" + mc.world.isSpaceEmpty(p, p.getBoundingBox())
                + ", escape=" + EscapeNavigator.choose(mc, threats) + ", hostiles=" + mobs + ", cells=" + cells
                + ", navigator=" + EscapeNavigator.diagnostics(mc, threats);
    }
}
