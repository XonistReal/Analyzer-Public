package dev.analyzer.automation;

import dev.analyzer.AnalyzerClient;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Objects;

/** Original observed-world task execution. No external bot, chat commands, packet rewriting or teleportation. */
public final class AutomationNavigator {
    public enum State { UNAVAILABLE, IDLE, RUNNING, PAUSED, COMPLETE, FAILED, INTERRUPTED }
    public record Status(State state, String process, String message, double progress, boolean owned) {
        public boolean active() { return state == State.RUNNING || state == State.PAUSED; }
        public boolean complete() { return state == State.COMPLETE; }
        public boolean failed() { return state == State.FAILED || state == State.INTERRUPTED; }
        public boolean paused() { return state == State.PAUSED; }
    }
    public record MotionProfile(boolean smooth, double turnSpeed, double turnAcceleration, double movementAngle, int breakDelayTicks, int placeDelayTicks) {
        public MotionProfile {
            var limits = new MotionPolicy.Limits(turnSpeed, turnAcceleration); turnSpeed = limits.speed(); turnAcceleration = limits.acceleration();
            movementAngle = Double.isFinite(movementAngle) ? Math.max(5, Math.min(60, movementAngle)) : 35;
            breakDelayTicks = Math.max(1, Math.min(20, breakDelayTicks)); placeDelayTicks = Math.max(1, Math.min(20, placeDelayTicks));
        }
        public static MotionProfile defaults() { return new MotionProfile(true, 150, 600, 35, 5, 4); }
    }
    public record MotionStatus(boolean applied, double lastYawStep, double lastPitchStep, boolean headingHold, long rejectedInteractions) { }
    public record SafetyProfileStatus(boolean enabled, boolean applied, int changedSettings) { }
    public record RouteDiagnostics(String state, int expanded, int pathNodes, int nextNode, int replans) { }
    public record InputStatus(boolean active, String description) { }
    public static final int SHELTER_MATERIAL_COUNT = ShelterSchematic.MATERIAL_COUNT;
    private enum Kind { TRAVEL, MINE, BUILD }
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final Object group;
    private final NativeInputLease inputs;
    private final boolean workers;
    private boolean preciseArrival;
    private NativeMiner miner;
    private NativeBuilder builder;
    private AutomationNavigator approach;
    private MotionProfile motion = MotionProfile.defaults();
    private Status status = new Status(State.IDLE, "", "Native automation ready", 0, false);
    private Kind kind;
    private Object world;
    private NativePathfinder.Target goal;
    private NativePathfinder.Search search;
    private List<NativePathfinder.Point> path = List.of();
    private int node, ticks, replans, stuckTicks, bestNodeTicks;
    private double initialDistance, bestDistance = Double.MAX_VALUE, lastYawStep, lastPitchStep;
    private boolean safeRouting, safetyInputs, headingHold, reserved;
    private Vec3d lastPosition;
    private Vec3d movementAnchor;
    private int lastMovementTick;
    private Box miningExclusion;
    private BlockPos approachGoal;
    private String diagnosticState = "";
    private int lastDiagnostic;

    public AutomationNavigator() { this(new Object(), true); }
    private AutomationNavigator(Object group, boolean workers) {
        this.group = group; this.workers = workers; preciseArrival = !workers; inputs = new NativeInputLease(group);
    }
    public boolean available() { return mc.world != null && mc.player != null && mc.interactionManager != null; }
    public Status status() { return status; }
    public void configureMotion(MotionProfile profile) {
        motion = Objects.requireNonNull(profile); if (approach != null) approach.configureMotion(profile);
        if (miner != null) miner.configureMotion(profile);
        if (builder != null) builder.configureMotion(profile);
    }
    /** Protect the selected build volume from material gathering; null clears this mission constraint. */
    public void setMiningExclusion(Box bounds) {
        miningExclusion = bounds;
        if (miner != null) miner.setMiningExclusion(bounds);
    }
    public MotionStatus motionStatus() {
        if (approach != null && approach.status.active()) return approach.motionStatus();
        return new MotionStatus(inputs.active(), lastYawStep, lastPitchStep, headingHold, 0);
    }
    public SafetyProfileStatus safetyProfileStatus() { return new SafetyProfileStatus(safeRouting, safeRouting && status.owned(), 0); }
    public boolean beginSafetyProfile() { if (manualTaskActive()) return fail("Another native task currently owns movement"); safeRouting = true; return true; }
    public void endSafetyProfile() { safeRouting = false; clearSafetyInputs(); }
    /** Compatibility name: another Analyzer navigation controller is treated as external ownership. */
    public boolean manualTaskActive() {
        return anotherTaskOwnsControls() || status.state() == State.RUNNING && mc.currentScreen == null
                && bool("pause-on-input", true) && NativeInputLease.hasPhysicalInput(mc);
    }
    public boolean anotherTaskOwnsControls() { return NativeInputLease.anotherController(inputs); }
    public boolean travelXZ(int x, int z) { return travel(x, null, z); }
    public boolean travelXYZ(int x, int y, int z) { return travel(x, y, z); }
    /** Reach a checked interaction stand, centered and settled before reporting completion. */
    public boolean travelForInteraction(BlockPos stand) {
        if (stand == null) return fail("Choose a checked interaction approach");
        return travel(stand.getX(), stand.getY(), stand.getZ(), true);
    }
    private boolean travel(int x, Integer y, int z) {
        return travel(x, y, z, !workers);
    }
    private boolean travel(int x, Integer y, int z, boolean precision) {
        if (!canStart()) return false;
        if (Math.abs((long)x) > 29_999_000 || Math.abs((long)z) > 29_999_000 || y != null && (y < mc.world.getBottomY() || y > mc.world.getTopYInclusive()))
            return fail("Travel coordinates are outside this dimension's limits");
        preciseArrival = precision;
        kind = Kind.TRAVEL; goal = new NativePathfinder.Target(x, y, z); initialDistance = distance();
        begin(y == null ? "Travel X/Z" : "Travel X/Y/Z"); planRoute(); return true;
    }
    public boolean mineByName(int quantity, String... names) {
        return mine(quantity, names, null);
    }
    /** A probabilistic/alternative drop is verified as the requested item, rather than crediting the broken block. */
    public boolean mineForItem(int quantity, String itemId, String... names) {
        Identifier id = itemId == null ? null : Identifier.tryParse(itemId);
        if (id == null || !id.getNamespace().equals("minecraft") || !Registries.ITEM.containsId(id) || Registries.ITEM.get(id) == Items.AIR)
            return fail("Choose a known vanilla item to collect");
        return mine(quantity, names, Registries.ITEM.get(id));
    }
    private boolean mine(int quantity, String[] names, Item expectedDrop) {
        if (!workers || !canStart()) return false;
        miner = new NativeMiner();
        boolean accepted=expectedDrop==null?miner.begin(quantity,names):miner.begin(quantity,names,expectedDrop);
        if (!accepted) { String error = miner.error(); miner = null; return fail(error); }
        miner.setMiningExclusion(miningExclusion);
        miner.configureMotion(motion);
        kind = Kind.MINE; begin("Mine " + (names == null || names.length == 0 ? "resource" : names[0])); return true;
    }
    public boolean buildShelter(BlockPos origin, String materialId) {
        Identifier id = materialId == null ? null : Identifier.tryParse(materialId);
        if (!available() || id == null || !Registries.BLOCK.containsId(id)) return fail("Choose a valid shelter block");
        return buildSchematic("starter shelter", new ShelterSchematic(Registries.BLOCK.get(id).getDefaultState()), origin);
    }
    public boolean buildSchematic(String name, BuildSchematic schematic, BlockPos origin) {
        if (!workers || !canStart()) return false;
        builder = new NativeBuilder();
        try { if (!builder.begin(name, schematic, origin)) return fail("The native builder rejected this blueprint or site"); }
        catch (IllegalArgumentException problem) { builder = null; return fail(problem.getMessage()); }
        builder.configureMotion(motion);
        kind = Kind.BUILD; begin("Build " + name); return true;
    }
    private boolean canStart() {
        if (!available() || !mc.player.isAlive()) return fail("Join a world with a living player first");
        if (status.owned()) return fail("Pause or cancel the current native task before starting another");
        if (manualTaskActive()) return fail("Another task owns movement; it was not replaced");
        var input = inputStatus();
        if (bool("pause-on-input", true) && input.active())
            return fail("Release " + input.description() + ", then press Resume in Automation.");
        return true;
    }
    private void begin(String title) {
        if (!inputs.reserve()) throw new IllegalStateException("Another native task owns control");
        reserved = true;
        world = mc.world; ticks = replans = stuckTicks = bestNodeTicks = 0; bestDistance = Double.MAX_VALUE;
        lastPosition = mc.player.getPos(); path = List.of(); node = 0; search = null;
        movementAnchor = lastPosition; lastMovementTick = 0;
        status = new Status(State.RUNNING, title, "Reviewing observed terrain", 0, true);
    }
    private boolean fail(String detail) {
        if (!status.owned()) status = new Status(State.FAILED, "", detail, 0, false);
        return false;
    }
    public boolean pause() {
        if (!status.owned()) return false;
        inputs.close(); if (approach != null) approach.pause(); if (builder != null) builder.pause();
        if (miner != null) miner.pause();
        AutomationMotion.reset();
        status = new Status(State.PAUSED, status.process(), "Paused; Resume rechecks actual terrain", status.progress(), true); return true;
    }
    public boolean resume() {
        if (!status.owned() || !available() || mc.world != world || !mc.player.isAlive()) return false;
        clearSafetyInputs();
        if (kind == Kind.TRAVEL) planRoute();
        if (approach != null && approach.status.paused()) approach.resume();
        if (builder != null) builder.resume(); if (miner != null) miner.resume();
        status = new Status(State.RUNNING, status.process(), "Resuming native task", status.progress(), true); return true;
    }
    public boolean cancel() {
        if (!status.owned() && !safetyInputs) return false;
        release(); status = new Status(State.IDLE, "", "Native task canceled", 0, false); return true;
    }
    private void release() {
        clearSafetyInputs(); inputs.close(); search = null; path = List.of();
        if (approach != null) approach.cancel(); if (miner != null) miner.stop(); if (builder != null) builder.stop();
        approachGoal = null; miner = null; builder = null; kind = null; AutomationMotion.reset();
        if (reserved) inputs.releaseReservation(); reserved = false;
    }
    public void clearStatus() { if (!status.owned()) status = new Status(State.IDLE, "", "Native automation ready", 0, false); }
    private void finish(State state, String detail, double progress) {
        String title = status.process(); release(); status = new Status(state, title, detail, progress, false);
    }
    public void tick() {
        if (!status.owned()) return;
        if (!available() || mc.world != world || !mc.player.isAlive()) {
            finish(State.INTERRUPTED, "World changed or player died; review and start explicitly", status.progress()); return;
        }
        if (status.paused()) return;
        if (manualTaskActive()) { finish(State.INTERRUPTED, "Manual input or another native task took control", status.progress()); return; }
        ticks++;
        try {
          if (kind == Kind.TRAVEL) tickTravel();
          else if (kind == Kind.MINE) {
            var result = miner.tick();
            if (result.done()) { finish(State.COMPLETE, result.detail(), 1); return; }
            if (result.blocked()) { finish(State.FAILED, result.detail(), result.progress()); return; }
            status = new Status(State.RUNNING, status.process(), result.detail(), result.progress(), true);
            driveApproach(miner.targetGoal());
          } else if (kind == Kind.BUILD) {
            var result = builder.tick();
            if (result.done()) { finish(State.COMPLETE, result.detail(), 1); return; }
            if (result.blocked()) { finish(State.FAILED, result.detail(), result.progress()); return; }
            status = new Status(State.RUNNING, status.process(), result.detail(), result.progress(), true);
            driveApproach(result.routeGoal());
          }
        } catch (RuntimeException error) {
            double progress = status.progress();
            finish(State.FAILED, "Native task stopped after " + error.getClass().getSimpleName() + ". Review the game log before retrying.", progress);
            AnalyzerClient.LOGGER.warn("Native automation released its controls after a task error", error);
        }
    }
    private void driveApproach(BlockPos destination) {
        if (destination == null) { if (approach != null) approach.cancel(); approachGoal = null; return; }
        if (approach == null) { approach = new AutomationNavigator(group, false); approach.configureMotion(motion); }
        boolean drifted = approach.status.complete() && mc.player.getPos().distanceTo(Vec3d.ofBottomCenter(destination)) > .15;
        if (!destination.equals(approachGoal) || drifted) {
            approach.cancel(); approach.clearStatus(); approachGoal = destination.toImmutable();
            approach.safeRouting = safeRouting;
            if (!approach.travelXYZ(destination.getX(), destination.getY(), destination.getZ())) {
                finish(State.FAILED, approach.status.message(), status.progress()); return;
            }
        }
        approach.tick();
        if (kind == Kind.BUILD && builder != null && approach.status.complete()) builder.approachArrived(approachGoal);
        diagnosticApproach();
        if (approach.status.failed()) finish(State.FAILED, "Approach blocked: " + approach.status.message(), status.progress());
    }
    private void diagnosticApproach() {
        if (!FabricLoader.getInstance().isDevelopmentEnvironment() || !Boolean.getBoolean("analyzer.nativeAutomationSmoke")) return;
        String state = approachGoal + ":" + approach.status.state();
        if (state.equals(diagnosticState) && ticks - lastDiagnostic < 40) return;
        diagnosticState = state; lastDiagnostic = ticks;
        AnalyzerClient.LOGGER.info("ANALYZER_NATIVE_APPROACH: {}", diagnostics());
    }
    /** Explicit diagnostics contain positions and input states only; never account data or server addresses. */
    public String diagnostics() {
        String player = mc.player == null ? "unavailable" : "position=" + mc.player.getPos() + ", velocity=" + mc.player.getVelocity()
                + ", onGround=" + mc.player.isOnGround() + ", pose=" + mc.player.getPose() + ", input=" + mc.player.input.playerInput;
        return "mode=" + kind + ", state=" + status.state() + ", target=" + goal + ", route=" + routeDiagnostics()
                + ", approachGoal=" + approachGoal + ", child=" + (approach == null ? "none" : approach.status)
                + ", " + player;
    }
    private void planRoute() {
        inputs.close(); path = List.of(); node = 0;
        var start = NativeTerrain.playerPoint(mc);
        if (preciseArrival && goal.reached(start)) {
            // A block-level graph cannot center the player within a cell; steer this actual last half-step directly.
            path = List.of(new NativePathfinder.Point(goal.x(), (int)Math.round(mc.player.getY() * 16), goal.z()));
            search = null; replans++; stuckTicks = 0; bestDistance = Double.MAX_VALUE; return;
        }
        var terrain = new NativeTerrain(mc, start, integer("path-radius", 24), integer("max-drop", 1), safeRouting || bool("proactive-avoidance", true), number("avoid-range", 16), goal);
        search = new NativePathfinder.Search(terrain, start, goal, 8192); replans++; stuckTicks = 0; bestDistance = Double.MAX_VALUE;
    }
    private void tickTravel() {
        if (goal.reached(NativeTerrain.playerPoint(mc))) {
            double centerDistance = Math.hypot(mc.player.getX() - (goal.x() + .5), mc.player.getZ() - (goal.z() + .5));
            if (!preciseArrival || centerDistance <= .15) {
                inputs.close();
                if (preciseArrival && (!mc.player.isOnGround() || mc.player.getVelocity().horizontalLength() > .035)) {
                    status = new Status(State.RUNNING, status.process(), "Settling at the checked interaction approach", progress(), true); return;
                }
                finish(State.COMPLETE, "Actual player position reaches the target", 1); return;
            }
        }
        if (mc.currentScreen != null) { inputs.close(); lastMovementTick = ticks; return; }
        if (mc.player.isInLava() || mc.player.isSpectator() || mc.player.isGliding() || mc.player.hasVehicle()) {
            finish(State.FAILED, "Native ground navigation cannot act safely in this movement state", status.progress()); return;
        }
        if (mc.player.getPos().squaredDistanceTo(movementAnchor) > .0225) {
            movementAnchor = mc.player.getPos(); lastMovementTick = ticks;
        } else if (ticks - lastMovementTick > 240 + Math.ceil(3600 / motion.turnSpeed()) + Math.ceil(8192.0 / integer("path-budget", 256))) {
            finish(State.FAILED, "Native route made no physical movement within its planning and turning budget. Check the approach and resume explicitly.", progress()); return;
        }
        if (search != null) {
            var result = search.advance(integer("path-budget", 256));
            status = new Status(State.RUNNING, status.process(), "Planning loaded terrain: " + search.expanded() + " nodes", progress(), true);
            inputs.close();
            if (result == null) return;
            if (FabricLoader.getInstance().isDevelopmentEnvironment() && Boolean.getBoolean("analyzer.nativeAutomationSmoke"))
                AnalyzerClient.LOGGER.info("ANALYZER_NATIVE_ROUTE: target={}, from={}, reached={}, detail={}, path={}",
                        goal, NativeTerrain.playerPoint(mc), result.reached(), result.detail(), result.path());
            search = null; path = result.path(); node = 1;
            if (path.size() < 2) { finish(State.FAILED, "No loaded, supported walking route. Clear access or load nearby chunks and retry.", progress()); return; }
        }
        if (node >= path.size()) { planRoute(); return; }
        Vec3d target = NativeTerrain.center(path.get(node));
        double separation = mc.player.getPos().distanceTo(target);
        boolean finalApproach = preciseArrival && node == path.size() - 1;
        double tolerance = finalApproach ? .15 : .28;
        if (Math.hypot(mc.player.getX() - target.x, mc.player.getZ() - target.z) < tolerance && Math.abs(mc.player.getY() - target.y) < .45) {
            node++; bestDistance = Double.MAX_VALUE; bestNodeTicks = ticks; inputs.close(); return;
        }
        if (separation < bestDistance - .04) { bestDistance = separation; stuckTicks = 0; } else if (!headingHold) stuckTicks++;
        if (stuckTicks > 60) {
            if (replans >= 6 && mc.player.getPos().squaredDistanceTo(lastPosition) < 1) { finish(State.FAILED, "Repeated native routes made no movement progress", progress()); return; }
            lastPosition = mc.player.getPos(); planRoute(); return;
        }
        // Recheck terrain through a new survey. Servers, falling blocks and explosions can invalidate the planned path.
        var observed = new NativeTerrain(mc, NativeTerrain.playerPoint(mc), 8, integer("max-drop", 1), false, 4, goal);
        boolean grounded = mc.player.isOnGround() || mc.player.isTouchingWater();
        boolean recentering = false;
        if (grounded && !observed.sweep(mc.player.getPos(), target)) {
            Vec3d cellCenter = NativeTerrain.center(NativeTerrain.playerPoint(mc));
            double centerOffset = Math.hypot(mc.player.getX() - cellCenter.x, mc.player.getZ() - cellCenter.z);
            if (centerOffset > .06 && observed.sweep(mc.player.getPos(), cellCenter)) {
                // Planning uses cell centers. A real corner offset can hide a valid stair jump behind
                // the next step; return to the surveyed center before trying that same edge.
                target = cellCenter; separation = mc.player.getPos().distanceTo(target); recentering = true;
            } else {
                if (ticks - bestNodeTicks < 10) { inputs.close(); return; }
                planRoute(); bestNodeTicks = ticks; return;
            }
        }
        Vec3d delta = target.subtract(mc.player.getPos());
        float yaw = (float)(Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90);
        double beforeYaw = mc.player.getYaw(), beforePitch = mc.player.getPitch();
        boolean aligned = AutomationMotion.alignYaw(yaw, motion);
        lastYawStep = MotionPolicy.angle(mc.player.getYaw() - beforeYaw); lastPitchStep = mc.player.getPitch() - beforePitch; headingHold = !aligned;
        boolean jump = target.y > mc.player.getY() + .4 || mc.player.isTouchingWater();
        boolean sprint = aligned && !jump && grounded && !safeRouting && separation > 1.5;
        boolean careful = (recentering || finalApproach && separation < 1.25) && mc.player.isOnGround() && Math.abs(delta.y) < .2;
        boolean applied = careful ? inputs.applySneaking(aligned, aligned && jump) : inputs.apply(aligned, sprint, aligned && jump);
        if (!applied) { finish(State.INTERRUPTED, "Another task owns keyboard movement", progress()); return; }
        status = new Status(State.RUNNING, status.process(), recentering ? "Centering on the checked route before the next step"
                : (aligned ? "Following" : "Turning toward") + " native route · " + Math.round(distance()) + " blocks remaining", progress(), true);
    }
    private double distance() {
        if (goal == null || mc.player == null) return 0;
        double dx = mc.player.getX() - (goal.x() + .5), dz = mc.player.getZ() - (goal.z() + .5);
        return Math.hypot(Math.hypot(dx, dz), goal.y() == null ? 0 : mc.player.getY() - goal.y());
    }
    private double progress() { return Math.max(0, Math.min(1, 1 - distance() / Math.max(1, initialDistance))); }
    public RouteDiagnostics routeDiagnostics() {
        if (approach != null && approach.status.active()) return approach.routeDiagnostics();
        return new RouteDiagnostics(search != null ? "CALCULATING" : "FOLLOWING", search == null ? 0 : search.expanded(), path.size(), node, replans);
    }
    public boolean setSafetyInputs(boolean forward, boolean sprint, boolean jump) {
        if (!available() || !mc.player.isAlive() || manualTaskActive() || status.owned() && !status.paused()) return false;
        safetyInputs = inputs.apply(forward, sprint, jump); return safetyInputs;
    }
    /** Backpedal along an independently checked escape corridor while the actual camera faces the threat. */
    public boolean setSafetyBackwardInputs(boolean jump) {
        if (!available() || !mc.player.isAlive() || manualTaskActive() || status.owned() && !status.paused()) return false;
        safetyInputs = inputs.backward(jump); return safetyInputs;
    }
    public boolean manualInputActive() { return inputStatus().active(); }
    public InputStatus inputStatus() {
        var held = available() && mc.currentScreen == null ? NativeInputLease.heldControls(mc) : List.<String>of();
        String description = held.isEmpty() ? "movement or action buttons"
                : String.join(", ", held.subList(0, Math.min(3, held.size()))) + (held.size() > 3 ? " and other controls" : "");
        return new InputStatus(!held.isEmpty(), description);
    }
    boolean inputWindowFocused() { return AutomationInputDiagnostics.heldOverride() != null || mc.isWindowFocused(); }
    public void clearSafetyInputs() { if (safetyInputs) inputs.close(); safetyInputs = false; }
    public boolean lookToward(Vec3d point) { return available() && (status.paused() || safetyInputs || safeRouting && !status.owned()) && !manualTaskActive() && AutomationMotion.align(point, motion); }
    public boolean lookYaw(float yaw) { return available() && (status.paused() || safetyInputs || safeRouting && !status.owned()) && !manualTaskActive() && AutomationMotion.alignYaw(yaw, motion); }
    private boolean bool(String field, boolean fallback) { return AnalyzerClient.CORE == null ? fallback : AnalyzerClient.CORE.getBoolean("expedition", field); }
    private double number(String field, double fallback) { return AnalyzerClient.CORE == null ? fallback : AnalyzerClient.CORE.getNumber("expedition", field); }
    private int integer(String field, int fallback) { return (int)Math.round(number(field, fallback)); }
}
