package dev.analyzer.gameplay;

import java.util.Objects;

/** Finite, dependency-free geometry for a server-facing rotation that never changes the local camera. */
public final class SilentAimPolicy {
    private SilentAimPolicy() { }

    /** Native degree conventions: yaw zero faces positive Z and positive pitch looks down. */
    public record Angles(float yaw, float pitch) {
        public Angles {
            if (!Float.isFinite(yaw) || !Float.isFinite(pitch))
                throw new IllegalArgumentException("Rotation must be finite");
            yaw = (float) wrap(yaw);
            pitch = Math.clamp(pitch, -90, 90);
        }
    }
    public record Direction(double x, double y, double z) { }

    public static double wrap(double degrees) {
        if (!Double.isFinite(degrees)) return Double.NaN;
        double value = degrees % 360;
        if (value >= 180) value -= 360;
        if (value < -180) value += 360;
        return value == 0 ? 0 : value;
    }

    /** The whole yaw/pitch change shares one per-tick budget, including diagonal turns. */
    public static Angles step(Angles current, Angles desired, double turnSpeed, double smoothing, double strength) {
        Objects.requireNonNull(current); Objects.requireNonNull(desired);
        if (!Double.isFinite(turnSpeed) || !Double.isFinite(smoothing) || !Double.isFinite(strength)) return current;
        double budget = Math.clamp(turnSpeed, 0, 180);
        double factor = (1 - Math.clamp(smoothing, 0, 100) * .009) * Math.clamp(strength, 0, 100) / 100;
        double yaw = wrap(desired.yaw() - current.yaw()) * factor;
        double pitch = (desired.pitch() - current.pitch()) * factor;
        double length = Math.hypot(yaw, pitch);
        if (budget == 0 || length == 0) return current;
        double scale = Math.min(1, budget / length);
        return new Angles((float) (current.yaw() + yaw * scale), (float) (current.pitch() + pitch * scale));
    }

    /** Aim from an eye position using target minus eye; vertical targets retain the caller's yaw. */
    public static Angles aim(double dx, double dy, double dz, float fallbackYaw) {
        if (!Double.isFinite(dx) || !Double.isFinite(dy) || !Double.isFinite(dz) || !Float.isFinite(fallbackYaw))
            throw new IllegalArgumentException("Aim vector must be finite");
        double horizontal = Math.hypot(dx, dz);
        float yaw = horizontal <= 1.0e-12 ? fallbackYaw : (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
        return new Angles(yaw, pitch);
    }

    public static Direction direction(Angles angles) {
        Objects.requireNonNull(angles);
        double yaw = Math.toRadians(angles.yaw()), pitch = Math.toRadians(angles.pitch());
        double horizontal = Math.cos(pitch);
        return new Direction(-Math.sin(yaw) * horizontal, -Math.sin(pitch), Math.cos(yaw) * horizontal);
    }

    /** Actual three-dimensional angle from a virtual ray, independent of rendered camera angles. */
    public static double angleTo(Angles angles, double dx, double dy, double dz) {
        if (!Double.isFinite(dx) || !Double.isFinite(dy) || !Double.isFinite(dz)) return Double.POSITIVE_INFINITY;
        double length = Math.hypot(Math.hypot(dx, dz), dy);
        if (length == 0) return 0;
        if (!Double.isFinite(length)) return Double.POSITIVE_INFINITY;
        Direction look = direction(angles);
        double dot = look.x() * (dx / length) + look.y() * (dy / length) + look.z() * (dz / length);
        return Math.toDegrees(Math.acos(Math.clamp(dot, -1, 1)));
    }

    /** Wrapped comparison for an observed ordinary movement packet and a current aim lease. */
    public static boolean matches(Angles first, Angles second, double tolerance) {
        return first != null && second != null && Double.isFinite(tolerance) && tolerance >= 0
            && Math.abs(wrap(first.yaw() - second.yaw())) <= tolerance
            && Math.abs(first.pitch() - second.pitch()) <= tolerance;
    }

    /**
     * Distance to the first AABB hit on the finite virtual ray, or NaN for a miss/invalid box.
     * This pure helper does not test world blocks; live attacks separately raycast actual collision shapes.
     */
    public static double hitDistance(Angles angles, double eyeX, double eyeY, double eyeZ,
                                     double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
                                     double range) {
        if (angles == null || !Double.isFinite(range) || range < 0
            || !Double.isFinite(eyeX) || !Double.isFinite(eyeY) || !Double.isFinite(eyeZ)
            || !Double.isFinite(minX) || !Double.isFinite(minY) || !Double.isFinite(minZ)
            || !Double.isFinite(maxX) || !Double.isFinite(maxY) || !Double.isFinite(maxZ)
            || minX > maxX || minY > maxY || minZ > maxZ) return Double.NaN;
        Direction ray = direction(angles);
        double[] origin = {eyeX, eyeY, eyeZ}, direction = {ray.x(), ray.y(), ray.z()};
        double[] low = {minX, minY, minZ}, high = {maxX, maxY, maxZ};
        double entry = 0, exit = range;
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(direction[axis]) < 1.0e-12) {
                if (origin[axis] < low[axis] || origin[axis] > high[axis]) return Double.NaN;
            } else {
                double first = (low[axis] - origin[axis]) / direction[axis];
                double last = (high[axis] - origin[axis]) / direction[axis];
                if (first > last) { double swap = first; first = last; last = swap; }
                entry = Math.max(entry, first); exit = Math.min(exit, last);
                if (entry > exit) return Double.NaN;
            }
        }
        return entry;
    }
}
