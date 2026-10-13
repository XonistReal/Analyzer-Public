package dev.analyzer.gameplay;

/** Original Minecraft-independent damage and placement policy, shared by the controller and checks. */
public final class HvhPolicy {
    private HvhPolicy() { }
    public static double rawExplosionDamage(double distance, double power, double exposure) {
        if (!Double.isFinite(distance) || !Double.isFinite(power) || !Double.isFinite(exposure)
                || distance < 0 || power <= 0 || exposure < 0 || exposure > 1) return Double.NaN;
        if (distance > power * 2) return 0;
        double impact = (1 - distance / (power * 2)) * exposure;
        return Math.floor((impact * impact + impact) * 7 * power + 1);
    }
    public static boolean damageAllowed(double enemy, double self, double minimum, double maximum,
                                        double health, boolean antiSuicide, boolean facePlace) {
        return Double.isFinite(enemy) && Double.isFinite(self) && Double.isFinite(health)
                && enemy > 0 && self >= 0 && self <= maximum
                && (!antiSuicide || self + 1 < health) && (facePlace || enemy >= minimum);
    }
    public static int placementBudget(int configured, int shared, boolean legit) {
        return Math.max(0, Math.min(shared, legit ? 1 : Math.clamp(configured, 1, 8)));
    }
    public static boolean protectedHole(double distanceToSelf, double distanceToFriend, boolean keepSelf, boolean keepFriend) {
        return keepSelf && distanceToSelf <= 1.5 || keepFriend && distanceToFriend <= 1.5;
    }
}
