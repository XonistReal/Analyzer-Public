package dev.analyzer.gameplay;

import dev.analyzer.core.CombatSettings;
import dev.analyzer.core.ModuleDefinition;
import java.util.List;

/** Regression and boundary checks for decisions used by actual combat controllers. */
public final class CombatPolicySelfTest {
    private static int assertions;
    public static void main(String[] args) {
        check(CombatPolicy.delayTicks(0) == 0 && CombatPolicy.delayTicks(1) == 1 && CombatPolicy.delayTicks(51) == 2, "Reaction delays never run early at tick boundaries");
        check(CombatPolicy.cpsDelay(20, 1, 8, 0) == CombatPolicy.cpsDelay(1, 20, 8, 0), "Reversed CPS bounds have the same lower bound");
        check(CombatPolicy.cpsDelay(9, 20, 8, 1) == 3, "Legacy CPS cap dominates faster configured bounds");
        check(CombatPolicy.cpsDelay(20, 20, 20, 1) == 1, "Maximum rate stays at one action per tick");
        check(CombatPolicy.cpsDelay(1, 20, 20, 0) == 20 && CombatPolicy.cpsDelay(1, 20, 20, 1) == 1, "Random timing samples span requested speeds");
        for (double sample : List.of(-1d, 0d, 0.5d, 1d, 2d)) for (double cap : List.of(1d, 8d, 12d, 20d)) {
            int delay = CombatPolicy.cpsDelay(20, 1, cap, sample);
            check(delay >= 1 && 20.0 / delay <= cap + 1.0e-9, "Every sample respects the configured maximum CPS");
        }
        check(!CombatPolicy.charged("Cooldown", 0.89, 0.95, 0.8, true), "Cooldown mode requires its own threshold");
        check(CombatPolicy.charged("CPS", 0.89, 0.95, 0.8, true), "CPS mode uses the hit-charge gate");
        check(CombatPolicy.charged("Cooldown", 0, 1, 1, false), "Explicit cooldown bypass allows ordinary low-charge attacks");
        check(CombatPolicy.targetLimit("Multi", false, 99) == 8, "Multi cannot exceed the global action limit");
        check(CombatPolicy.targetLimit("Multi", true, 8) == 1 && CombatPolicy.targetLimit("Single", false, 8) == 1, "Legit and Single remain one target");
        check(CombatPolicy.priority("Armor", 5, 2, 90, 0, 3) < CombatPolicy.priority("Armor", 1, 1, 0, 10, 0), "Armor priority chooses weak armor over distance");
        check(CombatPolicy.priority("Hurt time", 5, 2, 90, 0, 0) < CombatPolicy.priority("Hurt time", 1, 1, 0, 0, 10), "Hurt-time priority chooses damage-ready target");
        check(CombatPolicy.priority("Health", 5, 2, 90, 20, 10) == 2 && CombatPolicy.priority("Angle", 5, 2, 30, 20, 10) == 30, "Health and Angle use their actual metrics");
        check(CombatPolicy.turn(180, 12, 0, 100) == 12 && CombatPolicy.turn(-180, 12, 0, 100) == -12, "Rotations cannot exceed turn speed");
        check(CombatPolicy.turn(2, 12, 100, 100) < CombatPolicy.turn(2, 12, 0, 100), "Smoothing slows approach near the aim point");
        check(CombatPolicy.turn(2, 12, 0, 25) == 0.5, "Assistance strength applies before clamping");
        check(!CombatPolicy.falling(true, -0.1, 1) && !CombatPolicy.falling(false, 0.1, 1) && !CombatPolicy.falling(false, -0.1, 0), "Critical timing rejects ground, rising and unsupported zero-fall phases");
        check(CombatPolicy.falling(false, -0.1, 1), "Critical timing allows real downward motion with fall distance");
        check(CombatPolicy.flightTicks(1000, 0) == 80, "Slow long-distance projectile lead is bounded");
        check(CombatPolicy.flightTicks(30, 3) == 10 && CombatPolicy.drop(0.05, 10) == 2.5, "Basic projectile estimate keeps velocity and gravity in tick units");
        check(CombatPolicy.drop(0, 80) == 0, "Firework prediction has no arrow gravity correction");
        List<ModuleDefinition> modules = CombatSettings.definitions();
        check(modules.stream().map(ModuleDefinition::id).toList().equals(List.of("kill-aura", "aim-assist", "trigger-bot", "auto-clicker", "auto-weapon", "shield-breaker", "bow-aimbot", "criticals")), "Existing eight IDs are preserved");
        for (ModuleDefinition module : modules) {
            check(!module.defaultEnabled(), "Combat is opt-in: " + module.id());
            check(module.settings().stream().map(setting -> setting.id()).distinct().count() == module.settings().size(), "Unique setting IDs: " + module.id());
            if (!module.id().equals("auto-weapon") && !module.id().equals("shield-breaker")) {
                for (String id : List.of("players", "hostiles", "passives", "range", "fov", "turn-speed")) check(module.settings().stream().anyMatch(setting -> setting.id().equals(id)), "Legacy setting remains loadable: " + module.id() + "/" + id);
            }
            check(module.settings().stream().filter(setting -> setting.kind() == dev.analyzer.core.Setting.Kind.CHOICE).noneMatch(setting -> setting.choices().contains("Silent") || setting.choices().contains("Packet") && !setting.id().equals("swing")), "Unsupported silent rotation / packet crit modes are not exposed: " + module.id());
        }
        System.out.println("Analyzer combat policy: " + assertions + " assertions passed.");
        System.out.println("Combat controls: " + modules.stream().map(module -> module.id() + "=" + module.settings().size()).collect(java.util.stream.Collectors.joining(", ")));
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        assertions++;
    }
}
