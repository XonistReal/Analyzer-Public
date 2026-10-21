package dev.analyzer.gameplay;

import dev.analyzer.core.CombatSettings;
import dev.analyzer.core.ModuleDefinition;
import dev.analyzer.core.Setting;
import java.util.List;

/** Geometry, timing and settings checks for the Fight Bot controller. */
public final class FightBotPolicySelfTest {
    private static int assertions;

    public static void main(String[] args) {
        keys();
        orbit();
        timing();
        safety();
        settings();
        System.out.println("Fight Bot policy passed " + assertions + " assertions.");
    }

    private static void keys() {
        check(near(FightBotPolicy.yawOf(0, 1), 0) && near(FightBotPolicy.yawOf(-1, 0), 90) && near(FightBotPolicy.yawOf(1, 0), -90),
                "Yaw follows the native convention: +Z is 0 and -X is +90");
        check(Double.isNaN(FightBotPolicy.yawOf(0, 0)), "A zero vector has no direction");
        for (double facing = -180; facing < 180; facing += 7.5) {
            for (double desired = -180; desired < 180; desired += 3) {
                var keys = FightBotPolicy.keys(desired, facing);
                check(keys.moving() && !(keys.forward() && keys.back()) && !(keys.left() && keys.right()),
                        "Every finite request presses one consistent key combination");
                var actual = MovementPolicy.direction(facing, keys.forwardInput(), keys.sideInput());
                double error = Math.abs(SilentAimPolicy.wrap(FightBotPolicy.yawOf(actual.x(), actual.z()) - desired));
                check(error <= 22.5 + 1.0e-9, "Chosen keys move within half a sector of the request: facing " + facing + " desired " + desired);
            }
        }
        check(FightBotPolicy.keys(0, 0).equals(new FightBotPolicy.Keys(true, false, false, false)), "Straight ahead is forward only");
        check(FightBotPolicy.keys(90, 0).equals(new FightBotPolicy.Keys(false, false, false, true)), "Positive relative yaw is to the right");
        check(FightBotPolicy.keys(-90, 0).equals(new FightBotPolicy.Keys(false, false, true, false)), "Negative relative yaw is to the left");
        check(FightBotPolicy.keys(180, 0).equals(new FightBotPolicy.Keys(false, true, false, false)), "Behind is backward only");
        check(FightBotPolicy.keys(Double.NaN, 0) == FightBotPolicy.NONE, "Invalid requests press nothing");
        check(FightBotPolicy.keys(10, 0).forward() && FightBotPolicy.keys(-40, 0).forward(), "Near-forward requests keep forward held, so sprint stays possible");
    }

    private static void orbit() {
        check(FightBotPolicy.phase(10, 2.6) == FightBotPolicy.Phase.CHASE && FightBotPolicy.phase(4, 2.6) == FightBotPolicy.Phase.FIGHT,
                "Chasing switches to fighting near the circle");
        check(FightBotPolicy.phase(Double.NaN, 2.6) == FightBotPolicy.Phase.FIGHT, "Invalid distance never starts a chase");
        var approach = FightBotPolicy.approach(0, 0, 0, 10, 1);
        check(near(approach.x(), 0) && near(approach.z(), 1) && near(approach.yaw(), 0), "Chasing heads straight for the target");
        check(!FightBotPolicy.approach(3, 3, 3, 3, 1).moving(), "No approach direction exists on top of the target");

        // Target at the origin looking toward +Z (yaw 0); player in front of it, slightly to its -X side.
        var inFront = FightBotPolicy.orbit(-0.5, 2.6, 0, 0, 0, 2.6, "Away from their aim", 50, 1, false);
        check(inFront.moving(), "A player in the target's aim cone moves");
        double before = Math.abs(SilentAimPolicy.wrap(FightBotPolicy.yawOf(-0.5, 2.6)));
        double after = Math.abs(SilentAimPolicy.wrap(FightBotPolicy.yawOf(-0.5 + inFront.x() * 0.3, 2.6 + inFront.z() * 0.3)));
        check(after > before, "Moving along the orbit leaves the target's aim cone");
        var mirrored = FightBotPolicy.orbit(0.5, 2.6, 0, 0, 0, 2.6, "Away from their aim", 50, -1, false);
        check(mirrored.side() == -inFront.side(), "The escape side mirrors with the player's side of the cone");
        var behind = FightBotPolicy.orbit(0, -2.6, 0, 0, 0, 2.6, "Away from their aim", 50, -1, false);
        check(behind.side() == -1, "Outside the cone the previous orbit direction is kept");
        var far = FightBotPolicy.orbit(0, -6, 0, 0, 0, 2.6, "Clockwise", 50, 1, false);
        check(far.z() > 0, "Outside the fighting distance the orbit closes in");
        var close = FightBotPolicy.orbit(0, -1, 0, 0, 0, 2.6, "Clockwise", 50, 1, false);
        check(close.z() < 0.5, "Inside the fighting distance the orbit does not push further in");
        check(FightBotPolicy.orbit(0, -2, 0, 0, 0, 2.6, "Clockwise", 50, -1, false).side() == 1
                && FightBotPolicy.orbit(0, -2, 0, 0, 0, 2.6, "Counter-clockwise", 50, 1, false).side() == -1,
                "Fixed circle choices ignore the previous side");
        var retreat = FightBotPolicy.orbit(0, -2, 0, 0, 0, 2.6, "Clockwise", 50, 1, true);
        check(retreat.z() < 0, "Backing off moves away from the target");
        for (double angle = 0; angle < 360; angle += 15) {
            double px = Math.cos(Math.toRadians(angle)) * 3, pz = Math.sin(Math.toRadians(angle)) * 3;
            var plan = FightBotPolicy.orbit(px, pz, 0, 0, angle * 2, 2.6, "Away from their aim", 50, 1, false);
            check(plan.moving() && near(Math.hypot(plan.x(), plan.z()), 1), "Orbit requests are unit vectors: " + angle);
        }
        check(FightBotPolicy.orbit(0, 0, 0, 0, 0, 2.6, "Away from their aim", 50, 1, false).moving(), "Overlapping the target steps away");
        check(!FightBotPolicy.orbit(Double.NaN, 0, 0, 0, 0, 2.6, "Clockwise", 50, 1, false).moving(), "Invalid positions do not move");
    }

    private static void timing() {
        check(near(FightBotPolicy.ticksUntilCharged(0.5, 1, 12.5), 6.25), "Charge time scales with the weapon's cooldown");
        check(FightBotPolicy.ticksUntilCharged(1, 0.95, 12.5) == 0, "A charged weapon needs no wait");
        check(FightBotPolicy.ticksUntilCharged(Double.NaN, 1, 12) == Double.POSITIVE_INFINITY, "Invalid charge never looks ready");
        check(FightBotPolicy.jumpForCritical(true, true, 6, 6, 3, 3), "Jumps six ticks before charge at reach");
        check(!FightBotPolicy.jumpForCritical(true, true, 7, 6, 3, 3), "Does not jump too early");
        check(!FightBotPolicy.jumpForCritical(false, true, 0, 6, 3, 3), "Cannot jump while airborne");
        check(!FightBotPolicy.jumpForCritical(true, false, 0, 6, 3, 3), "Respects a blocked jump");
        check(!FightBotPolicy.jumpForCritical(true, true, 0, 6, 6, 3), "Does not hop for crits far outside reach");
        check(FightBotPolicy.releaseSprintForCritical(false, 0.08) && !FightBotPolicy.releaseSprintForCritical(false, 0.33)
                && !FightBotPolicy.releaseSprintForCritical(true, -0.1), "Sprint is released near the top of the jump");
        check(FightBotPolicy.criticalWindow(false, -0.08, 0.1, false, false), "Falling without sprint is a critical window");
        check(!FightBotPolicy.criticalWindow(false, -0.08, 0.1, false, true), "Sprint must have been off for the previous tick");
        check(!FightBotPolicy.criticalWindow(false, -0.08, 0.1, true, false), "Sprinting never counts");
        check(!FightBotPolicy.criticalWindow(true, -0.08, 0.1, false, false) && !FightBotPolicy.criticalWindow(false, 0.1, 0.1, false, false),
                "Ground and rising phases are not critical windows");
        // A vanilla jump: 0.42 initial velocity, then (v - 0.08) * 0.98 each tick. Falling starts on tick 6.
        double velocity = 0.42; int risingTicks = 0;
        while (velocity >= 0) { velocity = (velocity - 0.08) * 0.98; risingTicks++; }
        check(risingTicks == 6, "Six rising ticks match the default jump lead");
        check(FightBotPolicy.backOff(true, 10, 6, 3, 3) && !FightBotPolicy.backOff(true, 7, 6, 3, 3)
                && !FightBotPolicy.backOff(true, 10, 6, 5, 3) && !FightBotPolicy.backOff(false, 10, 6, 3, 3),
                "Backing off only happens close in, early in the recharge");
        check(FightBotPolicy.chaseHop(true, true, true, 7, 3) && !FightBotPolicy.chaseHop(true, true, true, 5, 3)
                && !FightBotPolicy.chaseHop(true, true, false, 9, 3), "Chase hops need sprint and distance");
    }

    private static void safety() {
        check(FightBotPolicy.safeDrop(true, true, 0, 0) && FightBotPolicy.safeDrop(true, true, 3, 3), "Floors within the allowed drop are safe");
        check(!FightBotPolicy.safeDrop(true, true, 4, 3) && !FightBotPolicy.safeDrop(true, false, 0, 3) && !FightBotPolicy.safeDrop(false, true, 0, 3),
                "Deep drops, missing floors and unloaded ground are unsafe");
        check(FightBotPolicy.lowHealth(5, 0, 6) && !FightBotPolicy.lowHealth(5, 2, 6) && !FightBotPolicy.lowHealth(1, 0, 0),
                "Health pause counts absorption and zero disables it");
    }

    private static void settings() {
        ModuleDefinition bot = CombatSettings.definitions().stream().filter(module -> module.id().equals("fight-bot")).findFirst().orElse(null);
        check(bot != null, "Fight Bot is a registered combat module");
        check(!bot.defaultEnabled(), "Fight Bot is opt-in");
        Setting<?> rotation = bot.setting("rotation");
        check(rotation.defaultValue().equals("Silent movement") && rotation.choices().equals(List.of("Silent movement", "Client")),
                "Fight Bot keeps the camera free by default and offers only implemented rotation modes");
        for (String id : List.of("players", "range", "chase-range", "fov", "turn-speed", "cooldown", "movement", "keep-distance",
                "criticals", "crit-lead", "max-drop", "manual-override", "stop-health", "silent-settle"))
            check(bot.settings().stream().anyMatch(setting -> setting.id().equals(id)), "Fight Bot setting exists: " + id);
        check(((Double) bot.setting("fov").defaultValue()) == 360, "Free look needs a full-circle default target cone");
        check(((Double) bot.setting("chase-range").max()) <= 16, "The chase range stays inside the silent rotation range limit");
        check(bot.settings().stream().map(Setting::id).distinct().count() == bot.settings().size(), "Unique Fight Bot setting IDs");
    }

    private static boolean near(double a, double b) { return Math.abs(a - b) < 1.0e-6; }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        assertions++;
    }
}
