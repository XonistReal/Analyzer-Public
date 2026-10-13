package dev.analyzer.gameplay;

import dev.analyzer.core.MovementSettings;
import java.util.Map;
import java.util.Set;

/** Bounds, direction geometry, profile guarantees, and compatibility with existing movement profiles. */
public final class MovementPolicySelfTest {
    private static int assertions;
    public static void main(String[] args) {
        var forward = MovementPolicy.direction(0, 1, 0);
        near(forward.x(), 0, "forward X at yaw zero"); near(forward.z(), 1, "forward Z at yaw zero");
        var east = MovementPolicy.direction(-90, 1, 0);
        near(east.x(), 1, "yaw rotates input into world direction");
        var diagonal = MovementPolicy.direction(23, 1, 1);
        near(Math.hypot(diagonal.x(), diagonal.z()), 1, "diagonal input cannot multiply speed");
        check(!MovementPolicy.direction(90, 0, 0).moving(), "no input has no motion direction");
        near(MovementPolicy.horizontalSpeed(.35, 2, "Standard"), .7, "standard multiplier controls direct speed");
        near(MovementPolicy.horizontalSpeed(1, 4, "Conservative"), .45, "conservative profile bounds excessive requested speed");
        near(MovementPolicy.flightSpeed(1, "Conservative glide"), .2873, "conservative glide horizontal cap");
        check(MovementPolicy.flightVertical("Glide", .4, true, false, .35, true, 40, 40) < 0, "glide cannot sustain lift even holding jump");
        check(MovementPolicy.flightVertical("Conservative glide", .4, true, false, .35, false, 1, 40) <= -.08, "conservative glide always descends");
        near(MovementPolicy.flightVertical("Vanilla", 0, true, false, .6, true, 40, 40), .6, "held ascent overrides hover pulse");
        near(MovementPolicy.flightVertical("Vanilla", 0, false, true, .6, true, 40, 40), -.6, "held descent uses independent vertical speed");
        near(MovementPolicy.flightVertical("Vanilla", 0, false, false, .6, true, 39, 40), 0, "hover pulse is periodic");
        near(MovementPolicy.flightVertical("Vanilla", 0, false, false, .6, true, 40, 40), -.04, "hover pulse descends at interval boundary");
        check(!MovementPolicy.safeWalk(true, true, false, false), "normal-input safe walk disables direct ledge clipping");
        check(!MovementPolicy.safeWalk(true, false, true, false), "sneak-only clipping honors unheld key");
        check(MovementPolicy.safeWalk(true, false, true, true), "sneak-only clipping honors held key");
        check(!MovementPolicy.safeWalk(false, false, false, true), "disabled module never clips");
        var definitions = MovementSettings.definitions();
        check(definitions.size() == 15 && definitions.stream().map(m -> m.id()).distinct().count() == 15, "all fifteen existing movement modules remain unique");
        Set<String> expected = Set.of("keep-sprint", "auto-walk", "speed", "fly", "step", "parkour", "safe-walk", "fast-ladder", "reverse-step", "fast-fall", "glide", "high-jump", "long-jump", "elytra-boost", "ground-strafe");
        check(expected.equals(definitions.stream().map(m -> m.id()).collect(java.util.stream.Collectors.toSet())), "no movement module types are added or removed");
        var legacy = Map.of("speed/speed", .35, "fly/speed", .35, "step/height", 1D, "fast-ladder/speed", .28,
                "reverse-step/speed", .8, "fast-fall/speed", 1D, "glide/speed", .1, "high-jump/height", .7,
                "long-jump/speed", .65, "elytra-boost/thrust", .035);
        for (var entry : legacy.entrySet()) {
            String[] path = entry.getKey().split("/");
            var module = definitions.stream().filter(m -> m.id().equals(path[0])).findFirst().orElseThrow();
            near(((Number) module.setting(path[1]).defaultValue()).doubleValue(), entry.getValue(), "legacy default preserved: " + entry.getKey());
        }
        System.out.println("Movement policy passed " + assertions + " assertions.");
    }
    private static void near(double actual, double expected, String label) { check(Math.abs(actual - expected) < .000001, label + " (actual " + actual + ")"); }
    private static void check(boolean result, String label) { if (!result) throw new AssertionError(label); assertions++; }
}
