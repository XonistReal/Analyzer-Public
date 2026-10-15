package dev.analyzer.automation;

import java.util.function.BiPredicate;

/** Small checked-position corrections use ordinary movement keys without making the camera face backward. */
final class RouteFollowingPolicy {
    record Steering(boolean forward, boolean backward, boolean left, boolean right) { }
    private static final Steering STILL = new Steering(false, false, false, false);
    private RouteFollowingPolicy() { }
    static boolean reached(double horizontal, double vertical, double tolerance, boolean descending, boolean grounded) {
        return Double.isFinite(horizontal) && horizontal < tolerance && Math.abs(vertical) < .45 && (!descending || grounded);
    }
    static boolean localCorrection(boolean recentering, boolean finalApproach, boolean descending, double horizontal) {
        return Double.isFinite(horizontal) && (recentering && horizontal <= .9 || finalApproach && horizontal <= .8 || descending && horizontal <= 1.2);
    }
    static Steering correct(double dx, double dz, double velocityX, double velocityZ, double yaw) {
        if (!Double.isFinite(dx) || !Double.isFinite(dz) || !Double.isFinite(velocityX) || !Double.isFinite(velocityZ) || !Double.isFinite(yaw)) return STILL;
        double ax = dx * .24 - velocityX * 1.65, az = dz * .24 - velocityZ * 1.65;
        double radians = Math.toRadians(yaw);
        double forward = -Math.sin(radians) * ax + Math.cos(radians) * az;
        double left = Math.cos(radians) * ax + Math.sin(radians) * az;
        return new Steering(forward > .015, forward < -.015, left > .015, left < -.015);
    }
    /** Check commanded travel separately from drift: opposite keys still need their own supported corridor. */
    static boolean safeCorrection(Steering input, double velocityX, double velocityZ, double yaw,
                                  BiPredicate<Double, Double> checkedCorridor) {
        if (!Double.isFinite(velocityX) || !Double.isFinite(velocityZ) || !Double.isFinite(yaw)) return false;
        double radians = Math.toRadians(yaw);
        double forward = (input.forward() ? 1 : 0) - (input.backward() ? 1 : 0);
        double left = (input.left() ? 1 : 0) - (input.right() ? 1 : 0);
        double length = Math.max(1, Math.hypot(forward, left));
        double dx = (-Math.sin(radians) * forward + Math.cos(radians) * left) / length;
        double dz = (Math.cos(radians) * forward + Math.sin(radians) * left) / length;
        return checkedCorridor.test(dx * .35, dz * .35)
                && checkedCorridor.test(velocityX * 3, velocityZ * 3)
                && checkedCorridor.test(velocityX * 2 + dx * .25, velocityZ * 2 + dz * .25);
    }
}
