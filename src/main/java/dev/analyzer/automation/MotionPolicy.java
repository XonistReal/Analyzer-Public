package dev.analyzer.automation;

/** Original, deterministic angular motion. It has no networking, random offsets or silent rotations. */
public final class MotionPolicy {
    public record Rotation(double yaw, double pitch, double yawVelocity, double pitchVelocity) {
        public static Rotation at(double yaw, double pitch) { return new Rotation(finite(yaw, 0), clamp(finite(pitch, 0), -90, 90), 0, 0); }
    }
    public record Limits(double speed, double acceleration) {
        public Limits { speed = clamp(finite(speed, 150), 30, 720); acceleration = clamp(finite(acceleration, 600), 60, 3600); }
    }
    private record Axis(double position, double velocity) { }
    private MotionPolicy() { }

    /** A pure preview: repeated calls with one frame snapshot never spend a second angular budget. */
    public static Rotation next(Rotation from, double yaw, double pitch, Limits limits, double seconds) {
        if (from == null || limits == null) throw new IllegalArgumentException("Motion snapshot and limits are required");
        double dt = clamp(finite(seconds, .05), .001, .1);
        Axis horizontal = axis(from.yaw(), from.yawVelocity(), angle(yaw - from.yaw()), limits, dt);
        Axis vertical = axis(from.pitch(), from.pitchVelocity(), clamp(finite(pitch, from.pitch()), -90, 90) - from.pitch(), limits, dt);
        return new Rotation(horizontal.position(), clamp(vertical.position(), -90, 90), horizontal.velocity(), vertical.velocity());
    }
    private static Axis axis(double current, double velocity, double error, Limits limits, double dt) {
        error = finite(error, 0); velocity = clamp(finite(velocity, 0), -limits.speed(), limits.speed());
        if (Math.abs(error) < .00001) return new Axis(current + error, 0);
        // Include this tick's displacement in the braking distance, so the final approach slows down.
        double halfStep = limits.acceleration() * dt / 2;
        double brakingSpeed = Math.sqrt(2 * limits.acceleration() * Math.abs(error) + halfStep * halfStep) - halfStep;
        double desired = Math.copySign(Math.min(limits.speed(), brakingSpeed), error);
        double nextVelocity = velocity + clamp(desired - velocity, -limits.acceleration() * dt, limits.acceleration() * dt);
        double displacement = nextVelocity * dt;
        if (Math.signum(displacement) == Math.signum(error) && Math.abs(displacement) >= Math.abs(error))
            return new Axis(current + error, 0);
        return new Axis(current + displacement, nextVelocity);
    }
    public static double angle(double degrees) {
        if (!Double.isFinite(degrees)) return 0;
        double result = degrees % 360;
        if (result >= 180) result -= 360;
        if (result < -180) result += 360;
        return result;
    }
    public static boolean aligned(Rotation current, double yaw, double pitch, double tolerance) {
        return Math.abs(angle(yaw - current.yaw())) <= tolerance && Math.abs(pitch - current.pitch()) <= tolerance;
    }
    private static double finite(double value, double fallback) { return Double.isFinite(value) ? value : fallback; }
    private static double clamp(double value, double minimum, double maximum) { return Math.max(minimum, Math.min(maximum, value)); }
}
