package dev.analyzer.gameplay;

import dev.analyzer.automation.AutomationEngine;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static dev.analyzer.gameplay.GameplayEngine.*;

/**
 * Original short-lived wire-rotation ownership. Rendering and movement input always read
 * the real player view. Only native movement packet angle reads may use this lease.
 * An observed send means local transmission, never a server acknowledgement.
 */
public final class SilentRotation {
    private static final double MATCH_EPSILON = .025;
    private static final int MAX_SAMPLES = 1024;
    private static Lease lease;
    private static SilentAimPolicy.Angles emitted;
    private static ClientPlayerEntity emittedPlayer;
    private static ClientWorld emittedWorld;
    private static String sendingKind;
    private static ClientPlayerEntity movementPlayer;
    private static SilentAimPolicy.Angles movementAngles;
    private static boolean armed, capturing;
    private static String reason = "No silent rotation lease";
    private static final ArrayList<Sample> samples = new ArrayList<>();

    private static final class Lease {
        final String owner, mode;
        final LivingEntity target;
        final ClientPlayerEntity player;
        final ClientWorld world;
        final Map<String, Object> settings;
        SilentAimPolicy.Angles angles, lastEmission;
        long refreshed, stepped = Long.MIN_VALUE, firstEmission = Long.MIN_VALUE, lastExplicit = Long.MIN_VALUE;
        Lease(String owner, String mode, LivingEntity target, ClientPlayerEntity player, ClientWorld world) {
            this.owner = owner; this.mode = mode; this.target = target; this.player = player; this.world = world;
            settings = core.values(owner);
            angles = new SilentAimPolicy.Angles(player.getYaw(), player.getPitch());
        }
    }
    private SilentRotation() { }

    /** One bounded angular step per gameplay tick. Caller has already reserved the camera channel. */
    public static boolean update(String owner, LivingEntity target, Vec3d point, String mode,
                                 double turnSpeed, double smoothing, double strength) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!isSilent(mode) || !"kill-aura".equals(owner) || !context(client, owner, target, mode)
                || point == null || !Double.isFinite(point.x + point.y + point.z)
                || !target.getBoundingBox().expand(.1).contains(point)) {
            cancel(); reason = "Silent rotation is waiting for valid ownership and a loaded target"; return false;
        }
        if (lease == null || lease.player != client.player || lease.world != client.world
                || lease.target != target || !lease.mode.equals(mode) || !lease.owner.equals(owner)
                || !lease.settings.equals(core.values(owner))) {
            cancel(); lease = new Lease(owner, mode, target, client.player, client.world);
        }
        Lease current = lease;
        current.refreshed = ticks;
        if (current.stepped != ticks) {
            Vec3d delta = point.subtract(client.player.getEyePos());
            current.angles = SilentAimPolicy.step(current.angles,
                SilentAimPolicy.aim(delta.x, delta.y, delta.z, current.angles.yaw()), turnSpeed, smoothing, strength);
            current.stepped = ticks;
        }
        if (mode.equals("Silent packet") && (current.lastExplicit == Long.MIN_VALUE
                || ticks - current.lastExplicit >= refreshTicks())) {
            send(current.angles, "aim-look"); current.lastExplicit = ticks;
        }
        reason = "Silent rotation is tracking the selected loaded target";
        return true;
    }

    /**
     * Called after normal reach, angle, ray, item, charge and action-owner validation.
     * Movement waits for a real prior native look; Packet/Pulse send only a look packet.
     */
    public static boolean prepareAttack(String owner, LivingEntity target, Vec3d point) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!valid(client) || !lease.owner.equals(owner) || lease.target != target || point == null
                || !Double.isFinite(point.x + point.y + point.z) || !target.getBoundingBox().expand(.1).contains(point)) {
            cancel(); reason = "Silent attack lost its current target or control lease"; return false;
        }
        Lease current = lease;
        if (!current.mode.equals("Silent movement")) {
            send(current.angles, "aim-look"); current.lastExplicit = ticks;
        }
        if (!hasEmittedLease()) { reason = "Waiting for native movement to transmit this target's silent look"; return false; }
        if (!current.mode.equals("Silent pulse") && (current.firstEmission == Long.MIN_VALUE
                || ticks - current.firstEmission < settleTicks())) {
            reason = "Waiting for the configured silent target warmup"; return false;
        }
        armed = true; reason = "The planned silent look was locally transmitted before this attack"; return true;
    }

    /** Always pair with prepareAttack in a finally block, even if an ordinary attack throws. */
    public static void finishAttack() {
        boolean pulse = armed && lease != null && lease.mode.equals("Silent pulse");
        armed = false;
        if (pulse) restoreLook();
    }

    /** Cancel stale ownership at the end of the owning gameplay tick. */
    public static void endTick() {
        if (lease != null && (lease.refreshed != ticks || !valid(MinecraftClient.getInstance()))) cancel();
    }

    /** Before native movement runs: menus/food/world changes must not reuse last tick's lease. */
    public static void beforeMovement(ClientPlayerEntity player) {
        movementPlayer = null; movementAngles = null;
        if (lease != null && (lease.player != player || !valid(MinecraftClient.getInstance()))) cancel();
        if (lease != null && !lease.mode.equals("Silent pulse")) {
            movementPlayer = player; movementAngles = lease.angles;
        }
    }
    public static void afterMovement() { movementPlayer = null; movementAngles = null; }

    public static float movementYaw(ClientPlayerEntity player, float ordinary) {
        return movementLease(player) ? movementAngles.yaw() : ordinary;
    }
    public static float movementPitch(ClientPlayerEntity player, float ordinary) {
        return movementLease(player) ? movementAngles.pitch() : ordinary;
    }
    private static boolean movementLease(ClientPlayerEntity player) {
        return lease != null && movementPlayer == player && movementAngles != null;
    }

    public static SilentAimPolicy.Angles angles() { return lease == null ? null : lease.angles; }
    /**
     * Fresh attack gates must raycast this direction against the CURRENT target box.
     * Movement's observed previous look may differ from this tick's next tracking step;
     * demanding exact equality would prevent attacks on continuously moving targets.
     */
    public static SilentAimPolicy.Angles attackAngles() {
        if (lease == null) return null;
        return lease.mode.equals("Silent movement") ? hasEmittedLease() ? lease.lastEmission : null : lease.angles;
    }
    public static SilentAimPolicy.Direction direction() { return lease == null ? null : SilentAimPolicy.direction(lease.angles); }
    public static String status() { return reason; }
    public static boolean active() { return lease != null; }
    public static boolean isSilent(String mode) {
        return "Silent movement".equals(mode) || "Silent packet".equals(mode) || "Silent pulse".equals(mode);
    }
    public static void cancel() { restoreLook(); clear(); }
    /** A server correction or disconnect owns the new view; never counter it with a restoration. */
    public static void correctedOrDisconnected() {
        clear(); emitted = null; emittedPlayer = null; emittedWorld = null;
    }
    private static void clear() { lease = null; armed = false; afterMovement(); reason = "No silent rotation lease"; }

    private static boolean valid(MinecraftClient client) {
        return lease != null && core != null && ticks >= lease.refreshed && ticks - lease.refreshed <= 1
            && lease.player == client.player && lease.world == client.world
            && lease.settings.equals(core.values(lease.owner))
            && context(client, lease.owner, lease.target, lease.mode);
    }
    private static boolean context(MinecraftClient client, String owner, LivingEntity target, String mode) {
        if (client == null || !client.isOnThread() || core == null || client.player == null || client.world == null
                || client.getNetworkHandler() == null || client.interactionManager == null || client.currentScreen != null
                || !Float.isFinite(client.player.getYaw()) || !Float.isFinite(client.player.getPitch())
                || client.getCameraEntity() != client.player || !on(owner) || !choiceOr(owner, "rotation", "Client").equals(mode)
                || boolOr(owner, "legit", false) || !combatControls.canAct(owner) || AutomationEngine.INSTANCE.isRunning()
                || consuming || client.player.isDead() || client.player.isSpectator() || client.player.hasVehicle()
                || client.player.currentScreenHandler != client.player.playerScreenHandler
                || !client.player.currentScreenHandler.getCursorStack().isEmpty()
                || client.player.isUsingItem() && boolOr(owner, "pause-using", true) && !CombatController.silentUsingAllowed()
                || boolOr(owner, "pause-mining", true) && client.interactionManager.isBreakingBlock()
                || boolOr(owner, "only-click", false) && !physical(client.options.attackKey)) return false;
        if (target == null || !target.isAlive() || target.isRemoved() || target.getWorld() != client.world
                || client.world.getEntityById(target.getId()) != target) return false;
        double range = numOr(owner, "range", 3) + numOr(owner, "aim-range", 0);
        return Double.isFinite(range) && range >= 0 && range <= 16
            && target.getBoundingBox().squaredMagnitude(client.player.getEyePos()) <= (range + .05) * (range + .05)
            && CombatController.silentEligible(target);
    }
    private static int settleTicks() { return (int) Math.clamp(numOr("kill-aura", "silent-settle", 1), 0, 10); }
    private static int refreshTicks() { return (int) Math.clamp(numOr("kill-aura", "silent-refresh", 1), 1, 20); }
    private static boolean hasEmittedLease() {
        MinecraftClient client = MinecraftClient.getInstance();
        return emitted != null && emittedPlayer == client.player && emittedWorld == client.world
            && lease != null && lease.lastEmission != null && SilentAimPolicy.matches(emitted, lease.lastEmission, MATCH_EPSILON);
    }
    private static void send(SilentAimPolicy.Angles angles, String kind) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.getNetworkHandler() == null || client.world == null || !client.isOnThread()) return;
        String previous = sendingKind;
        try {
            sendingKind = kind;
            client.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
                angles.yaw(), angles.pitch(), client.player.isOnGround(), client.player.horizontalCollision));
        } finally { sendingKind = previous; }
    }
    private static void restoreLook() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (lease == null || emitted == null || lease.world != client.world || lease.player != client.player
                || emittedWorld != client.world || emittedPlayer != client.player || client.player == null
                || client.getNetworkHandler() == null || !client.isOnThread()) return;
        if (!Float.isFinite(client.player.getYaw()) || !Float.isFinite(client.player.getPitch())) return;
        var camera = new SilentAimPolicy.Angles(client.player.getYaw(), client.player.getPitch());
        if (!SilentAimPolicy.matches(emitted, camera, MATCH_EPSILON)) send(camera, "restore-look");
    }

    /** The network mixin invokes this only after the native send call returned normally. */
    public static void sent(Packet<?> packet) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!client.isOnThread() || client.player == null || client.world == null) return;
        if (packet instanceof PlayerMoveC2SPacket move && move.changesLook()) {
            float yaw = move.getYaw(0), pitch = move.getPitch(0);
            if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) { correctedOrDisconnected(); return; }
            emitted = new SilentAimPolicy.Angles(yaw, pitch);
            emittedPlayer = client.player; emittedWorld = client.world;
            // Explicit looks must participate in native change detection, including the
            // return to the real view when a stationary player's lease ends.
            if ((lease != null || sendingKind != null) && client.player instanceof SilentRotationState state)
                state.analyzer$recordSentLook(emitted.yaw(), emitted.pitch());
            if (lease != null && SilentAimPolicy.matches(emitted, lease.angles, MATCH_EPSILON)) {
                lease.lastEmission = emitted;
                if (lease.firstEmission == Long.MIN_VALUE) lease.firstEmission = ticks;
            }
            record(sendingKind == null ? "native-look" : sendingKind, emitted);
        } else if (armed && packet instanceof PlayerInteractEntityC2SPacket interact) {
            interact.handle(new PlayerInteractEntityC2SPacket.Handler() {
                @Override public void interact(Hand hand) { }
                @Override public void interactAt(Hand hand, Vec3d point) { }
                @Override public void attack() { if (emitted != null) record("attack", emitted); }
            });
        }
    }

    public record Sample(long tick, String kind, String mode, int targetId, float yaw, float pitch) { }
    /** Strictly development-only bounded observation; no packets or behavior are fabricated. */
    public static void captureForChecks(boolean enabled) {
        if (!FabricLoader.getInstance().isDevelopmentEnvironment() || !Boolean.getBoolean("analyzer.silentAimSmoke"))
            throw new IllegalStateException("Silent rotation packet capture is development-only");
        capturing = enabled; if (enabled) samples.clear();
    }
    public static List<Sample> samplesForChecks() { return List.copyOf(samples); }
    private static void record(String kind, SilentAimPolicy.Angles value) {
        if (!capturing || samples.size() >= MAX_SAMPLES) return;
        samples.add(new Sample(ticks, kind, lease == null ? "" : lease.mode, lease == null ? -1 : lease.target.getId(), value.yaw(), value.pitch()));
    }
}
