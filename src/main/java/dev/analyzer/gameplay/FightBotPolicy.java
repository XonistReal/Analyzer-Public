package dev.analyzer.gameplay;

/**
 * Original, dependency-free Fight Bot geometry and timing shared by the controller and its checks.
 * Native yaw convention: yaw zero faces positive Z and positive yaw turns toward negative X.
 * Behavior references (LiquidBounce FightBot, Wurst FightBot) informed the goals only; no code is copied.
 */
public final class FightBotPolicy {
    private FightBotPolicy() { }

    /** The ordinary keyboard directions the controller may hold for one input tick. */
    public record Keys(boolean forward, boolean back, boolean left, boolean right) {
        public boolean moving() { return forward != back || left != right; }
        /** Vanilla input convention: forward is +1 and left is +1. */
        public double forwardInput() { return (forward ? 1 : 0) - (back ? 1 : 0); }
        public double sideInput() { return (left ? 1 : 0) - (right ? 1 : 0); }
    }
    public static final Keys NONE = new Keys(false, false, false, false);

    public enum Phase { CHASE, FIGHT }

    /** Horizontal movement request. A zero vector means the controller should not move this tick. */
    public record Plan(double x, double z, int side) {
        public boolean moving() { return Double.isFinite(x) && Double.isFinite(z) && Math.hypot(x, z) > 1.0e-6; }
        public double yaw() { return yawOf(x, z); }
    }
    private static final Plan STILL = new Plan(0, 0, 1);

    /** Native yaw of a horizontal direction vector. */
    public static double yawOf(double x, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(z) || Math.hypot(x, z) < 1.0e-12) return Double.NaN;
        return SilentAimPolicy.wrap(Math.toDegrees(Math.atan2(-x, z)));
    }

    /**
     * Nearest of the eight ordinary key combinations, relative to the yaw that movement physics
     * will use next tick (the server-facing yaw when movement correction is active).
     */
    public static Keys keys(double desiredYaw, double facingYaw) {
        double relative = SilentAimPolicy.wrap(desiredYaw - facingYaw);
        if (!Double.isFinite(relative)) return NONE;
        // Positive relative yaw is to the player's right; each sector is 45 degrees wide.
        int sector = Math.floorMod((int) Math.round(relative / 45.0), 8);
        return switch (sector) {
            case 0 -> new Keys(true, false, false, false);
            case 1 -> new Keys(true, false, false, true);
            case 2 -> new Keys(false, false, false, true);
            case 3 -> new Keys(false, true, false, true);
            case 4 -> new Keys(false, true, false, false);
            case 5 -> new Keys(false, true, true, false);
            case 6 -> new Keys(false, false, true, false);
            default -> new Keys(true, false, true, false);
        };
    }

    /** Chase until within reach of the fighting circle; the margin prevents phase flicker at its edge. */
    public static Phase phase(double distance, double keepDistance) {
        return Double.isFinite(distance) && distance > Math.max(0, keepDistance) + 1.75 ? Phase.CHASE : Phase.FIGHT;
    }

    /**
     * Movement around a target. Inside the target's aim cone the orbit moves away from where it looks;
     * outside it, the previous direction is kept with a gentler tangential share so the bot stays
     * hard to track without oscillating. Fixed circle choices always orbit their own way.
     *
     * @param circle "Away from their aim", "Clockwise" or "Counter-clockwise"
     */
    public static Plan orbit(double playerX, double playerZ, double targetX, double targetZ, double targetLookYaw,
                             double keepDistance, String circle, double dangerCone, int previousSide, boolean backOff) {
        if (!Double.isFinite(playerX + playerZ + targetX + targetZ) || !Double.isFinite(keepDistance)) return STILL;
        double dx = playerX - targetX, dz = playerZ - targetZ, distance = Math.hypot(dx, dz);
        int side = previousSide < 0 ? -1 : 1;
        if (distance < 1.0e-3) {
            // Standing inside the target: step straight out of its look direction.
            double look = Double.isFinite(targetLookYaw) ? targetLookYaw : 0;
            return new Plan(Math.sin(Math.toRadians(look)), -Math.cos(Math.toRadians(look)), side);
        }
        double outX = dx / distance, outZ = dz / distance;
        double tangentX = -outZ, tangentZ = outX;   // the direction that increases the polar yaw around the target
        double tangential;
        if ("Clockwise".equals(circle)) { side = 1; tangential = 1; }
        else if ("Counter-clockwise".equals(circle)) { side = -1; tangential = 1; }
        else {
            double polar = yawOf(outX, outZ);
            double offset = Double.isFinite(targetLookYaw) ? SilentAimPolicy.wrap(polar - targetLookYaw) : 180;
            double cone = Math.clamp(Double.isFinite(dangerCone) ? dangerCone : 0, 0, 180);
            if (Math.abs(offset) < cone) {
                if (Math.abs(offset) > 1.0e-6) side = offset > 0 ? 1 : -1;
                tangential = 1;
            } else tangential = 0.45;
        }
        double radial = backOff ? 1 : Math.clamp((distance - keepDistance) * 0.6, -1, 1);   // positive closes in
        double x = -outX * radial + tangentX * side * tangential;
        double z = -outZ * radial + tangentZ * side * tangential;
        if (backOff) { x = outX + tangentX * side * 0.35; z = outZ + tangentZ * side * 0.35; }
        double length = Math.hypot(x, z);
        return length < 1.0e-6 ? new Plan(0, 0, side) : new Plan(x / length, z / length, side);
    }

    /** Straight approach used while chasing. */
    public static Plan approach(double playerX, double playerZ, double targetX, double targetZ, int side) {
        double x = targetX - playerX, z = targetZ - playerZ, length = Math.hypot(x, z);
        if (!Double.isFinite(length) || length < 1.0e-6) return new Plan(0, 0, side);
        return new Plan(x / length, z / length, side);
    }

    /** Remaining ticks until the held weapon reaches the configured attack charge. */
    public static double ticksUntilCharged(double progress, double threshold, double ticksPerFullCharge) {
        if (!Double.isFinite(progress) || !Double.isFinite(threshold) || !Double.isFinite(ticksPerFullCharge)) return Double.POSITIVE_INFINITY;
        return Math.max(0, (Math.clamp(threshold, 0, 1) - Math.clamp(progress, 0, 1)) * Math.max(0, ticksPerFullCharge));
    }

    /**
     * A vanilla jump rises for six ticks before it falls. Jumping this many ticks before full charge
     * lines the falling half of the jump up with the charged attack.
     */
    public static boolean jumpForCritical(boolean grounded, boolean canJump, double ticksToCharge, int lead,
                                          double distance, double reach) {
        return grounded && canJump && Double.isFinite(ticksToCharge) && ticksToCharge <= Math.clamp(lead, 0, 20)
                && Double.isFinite(distance) && distance <= reach + 1.25;
    }

    /** Sprint must already be off on the server, so it is released near the top of the jump. */
    public static boolean releaseSprintForCritical(boolean grounded, double verticalVelocity) {
        return !grounded && Double.isFinite(verticalVelocity) && verticalVelocity < 0.15;
    }

    /**
     * Vanilla only grants a critical while falling and not sprinting. Requiring sprint to have been
     * off for the previous tick too means the stop-sprint command was sent before the attack.
     */
    public static boolean criticalWindow(boolean grounded, double verticalVelocity, double fallDistance,
                                         boolean sprintingNow, boolean sprintingLastTick) {
        return CombatPolicy.falling(grounded, verticalVelocity, fallDistance) && !sprintingNow && !sprintingLastTick;
    }

    /** Retreat out of the target's reach while the attack recharges, then re-engage. */
    public static boolean backOff(boolean enabled, double ticksToCharge, int lead, double distance, double reach) {
        return enabled && Double.isFinite(ticksToCharge) && ticksToCharge > Math.clamp(lead, 0, 20) + 2
                && Double.isFinite(distance) && distance < reach + 0.75;
    }

    /** Sprint-jumps only far from the fight, so they never collide with critical timing. */
    public static boolean chaseHop(boolean enabled, boolean grounded, boolean sprinting, double distance, double reach) {
        return enabled && grounded && sprinting && Double.isFinite(distance) && distance > reach + 3;
    }

    /** A step is safe when a floor exists within the allowed drop. Unknown (unloaded) ground is never safe. */
    public static boolean safeDrop(boolean loaded, boolean floorFound, int emptyBlocksBelow, double maxDrop) {
        return loaded && floorFound && emptyBlocksBelow >= 0 && emptyBlocksBelow <= Math.max(0, maxDrop);
    }

    /** Health pause: zero disables it; absorption counts as health. */
    public static boolean lowHealth(double health, double absorption, double stopAt) {
        return stopAt > 0 && Double.isFinite(health) && health + Math.max(0, absorption) <= stopAt;
    }
}
