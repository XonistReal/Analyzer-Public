package dev.analyzer.automation;

import dev.analyzer.AnalyzerClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

/** Explicit local-stage camera actions. Calling stages own their lifecycle; this class never ticks itself. */
public final class AutomationMotion {
    private static Object world, player;
    private static int frame = Integer.MIN_VALUE;
    private static MotionPolicy.Rotation base, current;
    private AutomationMotion() { }

    /** Turn toward a real world point; true only once both angles are within three degrees. */
    public static boolean align(Vec3d point) {
        return align(point, null);
    }
    public static boolean align(Vec3d point, AutomationNavigator.MotionProfile profile) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (point == null || mc.player == null || mc.world == null || !mc.player.isAlive()) return false;
        Vec3d delta = point.subtract(mc.player.getEyePos());
        if (delta.lengthSquared() < .000001) return true;
        double horizontal = Math.hypot(delta.x, delta.z);
        double yaw = horizontal < .000001 ? mc.player.getYaw() : Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90;
        double pitch = -Math.toDegrees(Math.atan2(delta.y, horizontal));
        MotionPolicy.Rotation rotation = turn(yaw, pitch, profile, false);
        return rotation != null && MotionPolicy.aligned(rotation, yaw, pitch, 3);
    }
    /** Turn toward a checked movement corridor without changing pitch. */
    public static boolean alignYaw(float yaw) {
        return alignYaw(yaw, null);
    }
    public static boolean alignYaw(float yaw, AutomationNavigator.MotionProfile profile) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return false;
        MotionPolicy.Rotation rotation = turn(yaw, mc.player.getPitch(), profile, true);
        double tolerance = profile != null ? profile.movementAngle() : AnalyzerClient.CORE == null ? 35 : AnalyzerClient.CORE.getNumber("expedition", "movement-angle");
        return rotation != null && Math.abs(MotionPolicy.angle(yaw - rotation.yaw())) <= tolerance;
    }
    private static MotionPolicy.Rotation turn(double yaw, double pitch, AutomationNavigator.MotionProfile profile, boolean yawOnly) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || !mc.player.isAlive()) return null;
        if (world != mc.world || player != mc.player || current == null) {
            world = mc.world; player = mc.player; frame = Integer.MIN_VALUE;
            current = MotionPolicy.Rotation.at(mc.player.getYaw(), mc.player.getPitch());
        }
        if (frame != mc.player.age) {
            frame = mc.player.age;
            // Carry momentum only while the actual camera still matches our preceding output.
            // A manual camera change or another owner must not inherit stale angular velocity.
            double yawVelocity = sameAngle(mc.player.getYaw(), current.yaw(), true) ? current.yawVelocity() : 0;
            double pitchVelocity = sameAngle(mc.player.getPitch(), current.pitch(), false) ? current.pitchVelocity() : 0;
            base = new MotionPolicy.Rotation(mc.player.getYaw(), mc.player.getPitch(), yawVelocity, pitchVelocity);
        }
        var core = AnalyzerClient.CORE;
        boolean smooth = profile != null ? profile.smooth() : core == null || core.getBoolean("expedition", "smooth-turns");
        MotionPolicy.Limits limits = new MotionPolicy.Limits(profile != null ? profile.turnSpeed() : core == null ? 150 : core.getNumber("expedition", "turn-speed"),
                profile != null ? profile.turnAcceleration() : core == null ? 600 : core.getNumber("expedition", "turn-acceleration"));
        // A yaw-only corridor update holds pitch even when a previous block aim had momentum.
        // Every call still previews yaw from the same tick snapshot, including owner handoffs.
        MotionPolicy.Rotation snapshot = yawOnly ? new MotionPolicy.Rotation(base.yaw(), mc.player.getPitch(), base.yawVelocity(), 0) : base;
        current = smooth ? MotionPolicy.next(snapshot, yaw, pitch, limits, .05) : MotionPolicy.Rotation.at(snapshot.yaw() + MotionPolicy.angle(yaw - snapshot.yaw()), pitch);
        mc.player.setYaw((float) current.yaw()); mc.player.setPitch((float) current.pitch());
        return current;
    }
    private static boolean sameAngle(double actual, double expected, boolean wrap) {
        double difference = wrap ? MotionPolicy.angle(actual - expected) : actual - expected;
        return Math.abs(difference) <= Math.max(.001, 2 * Math.ulp((float) expected));
    }
    public static void reset() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && player == mc.player && world == mc.world && frame == mc.player.age && base != null) {
            // A worker-to-defense handoff within one tick must not spend a second angular budget.
            current = MotionPolicy.Rotation.at(mc.player.getYaw(), mc.player.getPitch());
            base = MotionPolicy.Rotation.at(base.yaw(), base.pitch());
            return;
        }
        world = player = null; base = current = null; frame = Integer.MIN_VALUE;
    }
}
