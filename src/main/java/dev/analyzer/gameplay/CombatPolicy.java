package dev.analyzer.gameplay;

/** Small deterministic policies shared by combat and its dependency-free checks. */
public final class CombatPolicy {
    private CombatPolicy() { }
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
}
