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
        var controls = new CombatPolicy.Controls();
        check(controls.camera("bow"), "The first camera controller owns this tick");
        check(!controls.camera("bow") && !controls.camera("aura"), "Neither a repeated call nor another module gets a second angular budget");
        check(controls.action("bow") && !controls.action("aura"), "The camera owner may act while competing actions wait");
        controls.reset();
        check(controls.action("crystal") && controls.action("crystal"), "One explosive controller can retain its own bounded same-tick action batch");
        check(!controls.camera("melee") && !controls.action("melee"), "Melee cannot turn or swap over an actual crystal action");
        controls.reset();
        check(controls.canAct("melee") && controls.camera("melee"), "An idle enabled module reserves no control next tick");
        for (double cps : List.of(1d, 3d, 7d, 8d, 12d, 17d, 20d)) {
            var clock = new CombatPolicy.ClickClock(); int count = 0;
            for (int tick = 0; tick < 2000; tick++) if (clock.ready(tick)) { count++; clock.schedule(tick, CombatPolicy.cpsInterval(cps, cps, 20, 0.5)); }
            check(Math.abs(count - cps * 100) <= 1, "Fractional scheduling preserves the actual average CPS: " + cps);
            check(!clock.ready(1999), "The same final tick cannot repeat a scheduled attack");
        }
        var resumed = new CombatPolicy.ClickClock(); resumed.schedule(0, 2.5);
        check(resumed.ready(1000), "A long idle interval does not strand the next click");
        resumed.schedule(1000, 2.5);
        check(!resumed.ready(1000) && !resumed.ready(1002) && resumed.ready(1003), "Resuming never spends accumulated idle time in a burst");
        check(!CombatPolicy.switchSettled(10, 10, 1) && CombatPolicy.switchSettled(11, 10, 1), "A weapon switch waits its actual configured tick");
        check(CombatPolicy.switchSettled(10, 10, 0), "Explicit zero switch delay stays available");
        check(CombatPolicy.shieldFacing(0, -1, 0, -2) && !CombatPolicy.shieldFacing(0, -1, 0, 2), "Shield assistance distinguishes the protected front from the unprotected rear");
        check(CombatPolicy.defend(10, 10, true, false, true) && !CombatPolicy.defend(11, 10, true, false, true), "Double Hand uses actual combined health threshold");
        check(!CombatPolicy.defend(2, 10, false, false, true) && !CombatPolicy.defend(2, 10, true, true, true), "Double Hand needs a visible threat and yields to offhand protection");
        check(CombatPolicy.defend(2, 10, true, true, false), "Explicit two-totem preference allows both protected hands");
        for (double horizontal : List.of(.1, 3d, 10d, 30d, 60d)) for (double height : List.of(-10d, 0d, 5d)) {
            double rise = CombatPolicy.lowArcRise(horizontal, height, 3, .05);
            double flight = Math.hypot(horizontal, rise) / 3;
            check(Math.abs(rise - .025 * flight * flight - height) < 1.0e-8, "The estimated low arc reaches the requested height in gravity-only physics");
        }
        check(Double.isNaN(CombatPolicy.lowArcRise(200, 0, 1, .05)), "Impossible low-charge arcs are reported instead of aiming at an arbitrary drop correction");
        check(CombatPolicy.lowArcRise(30, 5, 3, 0) == 5 && CombatPolicy.lowArcRise(0, -4, 3, .05) == -4, "Gravity-free and vertical aim remain finite");
        List<ModuleDefinition> modules = CombatSettings.definitions();
        List<String> legacy = List.of("kill-aura", "aim-assist", "trigger-bot", "auto-clicker", "auto-weapon", "shield-breaker", "bow-aimbot", "criticals");
        check(modules.stream().map(ModuleDefinition::id).toList().subList(0, 8).equals(legacy), "Existing eight IDs and ordering are preserved");
        check(modules.stream().map(ModuleDefinition::id).toList().containsAll(List.of("auto-block", "double-hand", "hover-totem")), "New defensive and inventory helpers are real opt-in modules");
        for (ModuleDefinition module : modules) {
            check(!module.defaultEnabled(), "Combat is opt-in: " + module.id());
            check(module.settings().stream().map(setting -> setting.id()).distinct().count() == module.settings().size(), "Unique setting IDs: " + module.id());
            if (legacy.contains(module.id()) && !module.id().equals("auto-weapon") && !module.id().equals("shield-breaker")) {
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
