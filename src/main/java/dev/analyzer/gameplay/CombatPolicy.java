package dev.analyzer.gameplay;

/** Small deterministic policies shared by combat and its dependency-free checks. */
public final class CombatPolicy {
    private CombatPolicy() { }
    /** One camera owner and one action owner, shared by melee and crystal/placement controllers. */
    public static final class Controls {
        private String camera, action;
        public void reset() { camera = action = null; }
        public boolean camera(String owner) {
            if (!canAct(owner) || camera != null) return false;
            camera = owner; return true;
        }
        public boolean canAct(String owner) { return (action == null || action.equals(owner)) && (camera == null || camera.equals(owner)); }
        public boolean action(String owner) {
            if (!canAct(owner)) return false;
            action = owner; return true;
        }
        public boolean occupiedByOther(String owner) { return !canAct(owner); }
    }
    /** Fractional deadlines retain the requested average CPS instead of rounding every interval up. */
    public static final class ClickClock {
        private double due = Double.NEGATIVE_INFINITY;
        public boolean ready(long tick) { return tick + 1.0e-9 >= due; }
        public void schedule(long tick, double interval) {
            double previous = Double.isFinite(due) && tick - due < 1.000001 ? due : tick;
            due = previous + Math.max(1, interval);
        }
    }
    public static double cpsInterval(double minimum, double maximum, double cap, double sample) {
        double low = Math.clamp(Math.min(minimum, maximum), 1, Math.max(1, cap));
        double high = Math.clamp(Math.max(minimum, maximum), low, Math.max(low, cap));
        return 20 / (low + (high - low) * Math.clamp(sample, 0, 1));
    }
    public static boolean switchSettled(long tick, long switched, int delay) { return tick - switched >= Math.max(0, delay); }
    public static boolean shieldFacing(double lookX, double lookZ, double towardX, double towardZ) { return lookX * towardX + lookZ * towardZ > 0; }
    public static boolean defend(double health, double threshold, boolean threat, boolean offhandTotem, boolean onlyWithoutTotem) {
        return threat && health <= threshold && (!onlyWithoutTotem || !offhandTotem);
    }
    public static int delayTicks(double milliseconds) { return (int) Math.ceil(Math.max(0, milliseconds) / 50); }
    public static int cpsDelay(double minimum, double maximum, double cap, double sample) {
        double low = Math.min(minimum, maximum), high = Math.max(minimum, maximum);
        low = Math.clamp(low, 1, Math.max(1, cap)); high = Math.clamp(high, low, Math.max(low, cap));
        return Math.max(1, (int) Math.ceil(20 / (low + (high - low) * Math.clamp(sample, 0, 1))));
    }
    public static double turn(double error, double limit, double smoothing, double strength) {
        double factor = 1 - Math.clamp(smoothing, 0, 100) / 100 * 0.9;
        return Math.clamp(error * factor * Math.clamp(strength, 0, 100) / 100, -limit, limit);
    }
    public static double priority(String mode, double distance, double health, double angle, double armor, double hurtTime) {
        return switch (mode) { case "Health" -> health; case "Angle" -> angle; case "Armor" -> armor; case "Hurt time" -> hurtTime; default -> distance; };
    }
    public static boolean charged(String timing, double charge, double cooldown, double hitCharge, boolean respect) {
        return !respect || charge >= (timing.equals("CPS") ? hitCharge : cooldown);
    }
    public static int targetLimit(String mode, boolean legit, int requested) { return !legit && mode.equals("Multi") ? Math.clamp(requested, 1, 8) : 1; }
    public static boolean falling(boolean grounded, double vertical, double fallDistance) { return !grounded && vertical < 0 && fallDistance > 0; }
    public static double flightTicks(double distance, double speed) { return Math.clamp(distance / Math.max(0.2, speed), 0, 80); }
    public static double drop(double gravity, double flightTicks) { return 0.5 * Math.max(0, gravity) * flightTicks * flightTicks; }
    /** Lower ballistic arc in tick units; NaN explicitly means no finite arc at this launch speed. */
    public static double lowArcRise(double horizontal, double vertical, double speed, double gravity) {
        if (!Double.isFinite(horizontal + vertical + speed + gravity) || horizontal < 0 || speed <= 0) return Double.NaN;
        if (gravity <= 0 || horizontal < 1.0e-5) return vertical;
        double squared = speed * speed, discriminant = squared * squared - gravity * (gravity * horizontal * horizontal + 2 * vertical * squared);
        if (discriminant < 0) return Double.NaN;
        // Rationalized form avoids losing tiny near-level corrections to subtraction.
        double denominator = squared + Math.sqrt(discriminant);
        return (gravity * horizontal * horizontal + 2 * vertical * squared) / denominator;
    }
}
