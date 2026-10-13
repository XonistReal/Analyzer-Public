package dev.analyzer.gameplay;

/** Pure motion policy shared by the controller and its boundary checks. */
public final class MovementPolicy {
    public record Direction(double x, double z) { public boolean moving() { return x != 0 || z != 0; } }
    private MovementPolicy() { }
    public static Direction direction(double yawDegrees, double forward, double side) {
        double yaw = Math.toRadians(yawDegrees);
        double x = -Math.sin(yaw) * forward + Math.cos(yaw) * side;
        double z = Math.cos(yaw) * forward + Math.sin(yaw) * side;
        double length = Math.hypot(x, z);
        return length == 0 ? new Direction(0, 0) : new Direction(x / length, z / length);
    }
    public static double horizontalSpeed(double base, double multiplier, String profile) {
        double requested = base * multiplier;
        return profile.equals("Conservative") ? Math.min(.45, requested) : requested;
    }
    public static double flightSpeed(double speed, String mode) {
        return mode.equals("Conservative glide") ? Math.min(.2873, speed) : speed;
    }
    public static double flightVertical(String mode, double velocityY, boolean jump, boolean sneak,
                                        double verticalSpeed, boolean pulse, long tick, int interval) {
        if (!mode.equals("Vanilla")) {
            double descent = mode.equals("Conservative glide") ? .08 : .02;
            return sneak ? Math.min(velocityY, -verticalSpeed) : Math.min(velocityY, -descent);
        }
        if (jump != sneak) return jump ? verticalSpeed : -verticalSpeed;
        return pulse && tick % Math.max(1, interval) == 0 ? -.04 : 0;
    }
    public static boolean safeWalk(boolean enabled, boolean legit, boolean onlySneaking, boolean sneakHeld) {
        return enabled && !legit && (!onlySneaking || sneakHeld);
    }
}
