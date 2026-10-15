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
        double currentYaw = finite(from.yaw(), 0), currentPitch = clamp(finite(from.pitch(), 0), -90, 90);
        Axis horizontal = axis(currentYaw, from.yawVelocity(), angle(finite(yaw, currentYaw) - currentYaw), limits, dt);
        Axis vertical = axis(currentPitch, from.pitchVelocity(), clamp(finite(pitch, currentPitch), -90, 90) - currentPitch, limits, dt);
        return new Rotation(horizontal.position(), clamp(vertical.position(), -90, 90), horizontal.velocity(), vertical.velocity());
    }
    private static Axis axis(double current, double velocity, double error, Limits limits, double dt) {
        velocity = clamp(finite(velocity, 0), -limits.speed(), limits.speed());
        double target = current + finite(error, 0), remaining = dt, acceleration = limits.acceleration();
        // Integrate phase boundaries inside the tick. Using the endpoint velocity for the whole
        // tick overshoots the braking point, then needs an abrupt velocity reset at the target.
        for (int phase = 0; phase < 8 && remaining > 1e-12; phase++) {
            error = target - current;
            if (Math.abs(error) < 1e-10 && Math.abs(velocity) < 1e-8) return new Axis(target, 0);
            double direction = error == 0 ? -Math.signum(velocity) : Math.signum(error);
            double distance = Math.abs(error), forwardVelocity = velocity * direction;
            double stoppingDistance = forwardVelocity * forwardVelocity / (2 * acceleration);
            if (forwardVelocity < 0 || stoppingDistance > distance + 1e-10) {
                // A changed target may be behind us or inside the existing stopping distance.
                // Brake first; that physically necessary drift must not become repeated hunting.
                double brakingDirection = -Math.signum(velocity);
                double stopTime = Math.abs(velocity) / acceleration;
                double time = Math.min(remaining, stopTime);
                current += velocity * time + .5 * brakingDirection * acceleration * time * time;
                velocity += brakingDirection * acceleration * time;
                remaining -= time;
                if (time == stopTime) velocity = 0;
                continue;
            }
            // The fastest feasible profile reaches the target with zero velocity. Replanning
            // this profile from its exact intermediate state preserves the same braking curve.
            double peak = Math.min(limits.speed(), Math.sqrt(acceleration * distance + .5 * forwardVelocity * forwardVelocity));
            double accelerateTime = Math.max(0, (peak - forwardVelocity) / acceleration);
            double accelerateDistance = Math.max(0, (peak * peak - forwardVelocity * forwardVelocity) / (2 * acceleration));
            double brakeDistance = peak * peak / (2 * acceleration);
            double cruiseTime = peak > 0 ? Math.max(0, distance - accelerateDistance - brakeDistance) / peak : 0;
            double brakeTime = peak / acceleration;
            if (remaining >= accelerateTime + cruiseTime + brakeTime) return new Axis(target, 0);
            double time = Math.min(remaining, accelerateTime);
            double displacement = forwardVelocity * time + .5 * acceleration * time * time;
            double speed = forwardVelocity + acceleration * time;
            remaining -= time;
            time = Math.min(remaining, cruiseTime);
            displacement += peak * time;
            remaining -= time;
            if (remaining > 0) {
                time = Math.min(remaining, brakeTime);
                displacement += peak * time - .5 * acceleration * time * time;
                speed = Math.max(0, peak - acceleration * time);
            }
            return new Axis(current + direction * displacement, direction * speed);
        }
        return new Axis(current, velocity);
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
