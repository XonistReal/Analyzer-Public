package dev.analyzer.gameplay;

import dev.analyzer.gameplay.SilentAimPolicy.Angles;
import dev.analyzer.core.AnalyzerCore;
import dev.analyzer.core.CombatSettings;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/** Geometry invariants for the virtual rotation actually consumed by Kill Aura's transport. */
public final class SilentAimPolicySelfTest {
    private static int assertions;
    public static void main(String[] args) throws Exception {
        equal(0, SilentAimPolicy.wrap(720), 0, "Whole turns normalize");
        equal(-180, SilentAimPolicy.wrap(180), 0, "The half-turn has a deterministic sign");
        equal(179, SilentAimPolicy.wrap(-181), 0, "Negative wrapping follows the short path");
        check(Double.isNaN(SilentAimPolicy.wrap(Double.NaN)), "Malformed yaw cannot become a finite rotation");
        check(new Angles(540, 120).equals(new Angles(-180, 90)), "Native angles wrap yaw and clamp pitch");
        rejects(() -> new Angles(Float.NaN, 0), "NaN yaw is rejected");
        rejects(() -> new Angles(0, Float.POSITIVE_INFINITY), "Infinite pitch is rejected");
        var current = new Angles(179, 0);
        var crossing = SilentAimPolicy.step(current, new Angles(-179, 0), 1, 0, 100);
        equal(1, SilentAimPolicy.wrap(crossing.yaw() - current.yaw()), 1.0e-5, "Wrap crossing spends one degree on the short path");
        check(SilentAimPolicy.matches(new Angles(179.999f, 3), new Angles(-179.999f, 3), .003), "Observed packet matching crosses the yaw seam");
        check(!SilentAimPolicy.matches(new Angles(0, 3), new Angles(0, 3.1f), .01), "Observed pitch must match too");
        check(!SilentAimPolicy.matches(null, current, 1) && !SilentAimPolicy.matches(current, current, Double.NaN), "An absent or malformed observation is never accepted");
        check(SilentAimPolicy.step(current, new Angles(0, 20), Double.NaN, 0, 100).equals(current), "Malformed turn speed produces no turn");
        check(SilentAimPolicy.step(current, new Angles(0, 20), 180, Double.POSITIVE_INFINITY, 100).equals(current), "Malformed smoothing produces no turn");
        check(SilentAimPolicy.step(current, new Angles(0, 20), 180, 0, 0).equals(current), "Zero strength produces no turn");
        check(SilentAimPolicy.step(current, new Angles(0, 20), 0, 0, 100).equals(current), "Zero budget produces no turn");
        for (float yaw : List.of(-179f, -90f, 0f, 90f, 179f)) for (float pitch : List.of(-89f, -25f, 0f, 25f, 89f))
            for (double budget : List.of(.1, 1d, 12d, 180d)) for (double smoothing : List.of(0d, 35d, 100d)) {
                Angles start = new Angles(yaw, pitch), target = new Angles(-42, 37);
                Angles result = SilentAimPolicy.step(start, target, budget, smoothing, 100);
                double yawStep = SilentAimPolicy.wrap(result.yaw() - start.yaw());
                double pitchStep = result.pitch() - start.pitch();
                check(Math.hypot(yawStep, pitchStep) <= budget + .00003, "Both axes share the same angular budget");
                check(Math.abs(SilentAimPolicy.wrap(target.yaw() - result.yaw())) <= Math.abs(SilentAimPolicy.wrap(target.yaw() - start.yaw())) + .00003, "Yaw never overshoots its target");
                check(Math.abs(target.pitch() - result.pitch()) <= Math.abs(target.pitch() - start.pitch()) + .00003, "Pitch never overshoots its target");
                var direction = SilentAimPolicy.direction(result);
                equal(1, Math.hypot(Math.hypot(direction.x(), direction.z()), direction.y()), 1.0e-12, "Every virtual ray is unit length");
            }
        Angles goal = new Angles(-179, 67), angle = new Angles(179, -50);
        for (int tick = 0; tick < 200; tick++) angle = SilentAimPolicy.step(angle, goal, 12, 50, 100);
        check(SilentAimPolicy.matches(angle, goal, .0001), "Smoothed virtual aim converges without persistent oscillation");
        equal(0, SilentAimPolicy.aim(0, 0, 4, 79).yaw(), .00001, "Positive Z is yaw zero");
        equal(-90, SilentAimPolicy.aim(4, 0, 0, 79).yaw(), .00001, "Positive X uses native negative yaw");
        equal(90, SilentAimPolicy.aim(-4, 0, 0, 79).yaw(), .00001, "Negative X uses native positive yaw");
        check(SilentAimPolicy.aim(0, 4, 0, 79).equals(new Angles(79, -90)), "A straight upward target preserves yaw");
        check(SilentAimPolicy.aim(0, -4, 0, 79).equals(new Angles(79, 90)), "A straight downward target preserves yaw");
        rejects(() -> SilentAimPolicy.aim(Double.NaN, 0, 0, 0), "Malformed target vectors cannot reach transport");
        for (double x : List.of(-3d, -1d, 0d, 1d, 3d)) for (double y : List.of(-3d, -1d, 0d, 1d, 3d)) for (double z : List.of(-3d, -1d, 0d, 1d, 3d)) {
            if (x == 0 && y == 0 && z == 0) continue;
            var aimed = SilentAimPolicy.aim(x, y, z, 52);
            check(SilentAimPolicy.angleTo(aimed, x, y, z) < .00002, "Computed aim follows its actual target vector");
        }
        equal(0, SilentAimPolicy.angleTo(new Angles(0, 0), 0, 0, 0), 0, "An eye already inside the aim point needs no turn");
        equal(180, SilentAimPolicy.angleTo(new Angles(0, 0), 0, 0, -3), .00001, "Behind-camera vectors remain distinct");
        check(Double.isInfinite(SilentAimPolicy.angleTo(new Angles(0, 0), Double.NaN, 0, 0)), "Invalid target angles fail the facing gate");
        var forward = new Angles(0, 0);
        equal(2, SilentAimPolicy.hitDistance(forward, 0, 1, 0, -.3, 0, 2, .3, 2, 2.6, 3), .00001, "Virtual forward ray intersects the actual target hitbox front");
        check(Double.isNaN(SilentAimPolicy.hitDistance(forward, 0, 1, 0, -.3, 0, 2, .3, 2, 2.6, 1.9)), "Ray cannot extend beyond allowed reach");
        var movingTargetAim = SilentAimPolicy.aim(.05, 0, 2.3, 0);
        check(!SilentAimPolicy.matches(forward, movingTargetAim, .001), "Moving target changes the next intended angle even while the prior ray is useful");
        check(Double.isFinite(SilentAimPolicy.hitDistance(forward, 0, 1, 0, -.25, 0, 2, .35, 2, 2.6, 3)), "Prior emitted movement angle still hits a freshly moved target box");
        check(Double.isNaN(SilentAimPolicy.hitDistance(forward, 0, 1, 0, .5, 0, 2, 1.1, 2, 2.6, 3)), "Prior emitted angle rejects a target that moved outside its actual ray");
        check(Double.isNaN(SilentAimPolicy.hitDistance(forward, 0, 1, 0, .4, 0, 2, 1, 2, 2.6, 3)), "A nearby target outside the virtual ray is rejected");
        check(Double.isNaN(SilentAimPolicy.hitDistance(forward, 0, 1, 0, -.3, 0, -3, .3, 2, -2, 3)), "Targets behind the ray are rejected");
        equal(0, SilentAimPolicy.hitDistance(forward, 0, 1, 0, -.3, 0, -.3, .3, 2, .3, 0), 0, "An eye inside a hitbox is immediately intersecting");
        check(Double.isNaN(SilentAimPolicy.hitDistance(forward, 0, 1, 0, 1, 0, 2, -1, 2, 3, 3)), "Inverted boxes fail closed");
        check(Double.isNaN(SilentAimPolicy.hitDistance(forward, 0, 1, 0, -.3, 0, 2, .3, 2, 3, Double.NaN)), "Invalid reach fails closed");
        equal(2, SilentAimPolicy.hitDistance(new Angles(90, 0), 0, 1, 0, -3, 0, -.3, -2, 2, .3, 3), .00001, "Negative-direction slabs preserve entry distance");
        equal(1, SilentAimPolicy.hitDistance(new Angles(0, 90), 0, 2, 0, -.3, 0, -.3, .3, 1, .3, 3), .00001, "Vertical native pitch uses the downward ray");
        settingsPersistence();
        System.out.println("Analyzer silent aim policy: " + assertions + " assertions passed.");
    }
    private static void settingsPersistence() throws Exception {
        var modules = CombatSettings.definitions();
        var aura = modules.stream().filter(module -> module.id().equals("kill-aura")).findFirst().orElseThrow();
        check(!aura.defaultEnabled(), "Kill Aura remains opt-in");
        check(aura.setting("rotation").defaultValue().equals("Client"), "Missing old rotation fields retain visible Client aiming");
        check(aura.setting("rotation").choices().equals(List.of("Client", "None", "Silent movement", "Silent packet", "Silent pulse")), "Only three explicit new transports extend the old rotation choices");
        var bot = modules.stream().filter(module -> module.id().equals("fight-bot")).findFirst().orElseThrow();
        check(bot.setting("rotation").choices().equals(List.of("Silent movement", "Client")),
                "Fight Bot uses only the movement lease, the one transport its movement correction matches");
        for (var module : modules) if (!module.id().equals("kill-aura") && !module.id().equals("fight-bot"))
            check(module.settings().stream().noneMatch(setting -> setting.choices().stream().anyMatch(value -> value.startsWith("Silent "))), "Silent choices are scoped to implemented Kill Aura and Fight Bot: " + module.id());
        Path directory = Files.createTempDirectory("analyzer-silent-aim-config-");
        try {
            Path file = directory.resolve("config.json");
            for (String old : List.of("Client", "None")) {
                Files.writeString(file, "{\"schemaVersion\":1,\"modules\":{\"kill-aura\":{\"enabled\":true,\"keybind\":72,\"settings\":{\"rotation\":\"" + old
                    + "\",\"range\":3.25,\"turn-speed\":19}},\"aim-assist\":{\"settings\":{\"strength\":73}}}}");
                byte[] before = Files.readAllBytes(file);
                AnalyzerCore loaded = new AnalyzerCore(directory, modules); loaded.load();
                check(loaded.getChoice("kill-aura", "rotation").equals(old), "Explicit old rotation survives: " + old);
                check(loaded.isEnabled("kill-aura") && loaded.keybind("kill-aura") == 72, "Loading new controls preserves existing activation and binding");
                equal(3.25, loaded.getNumber("kill-aura", "range"), 0, "Existing melee reach survives new choices");
                equal(19, loaded.getNumber("kill-aura", "turn-speed"), 0, "Existing turn rate survives new choices");
                equal(73, loaded.getNumber("aim-assist", "strength"), 0, "Unrelated assistance settings survive");
                check(loaded.loadWarnings().isEmpty(), "Old rotation choice loads without warnings");
                check(java.util.Arrays.equals(before, Files.readAllBytes(file)), "Loading never rewrites the old user's file");
            }
            AnalyzerCore core = new AnalyzerCore(directory, modules); core.load();
            for (String mode : List.of("Client", "None", "Silent movement", "Silent packet", "Silent pulse")) {
                core.setValue("kill-aura", "rotation", mode);
                core.setValue("kill-aura", "silent-settle", 3d); core.setValue("kill-aura", "silent-refresh", 4d);
                core.save(); core.saveProfile("rotation-test");
                AnalyzerCore restored = new AnalyzerCore(directory, modules); restored.load();
                check(restored.getChoice("kill-aura", "rotation").equals(mode), "Rotation survives active config round trip: " + mode);
                equal(3, restored.getNumber("kill-aura", "silent-settle"), 0, "Settlement persists");
                equal(4, restored.getNumber("kill-aura", "silent-refresh"), 0, "Packet refresh persists");
                restored.setValue("kill-aura", "rotation", "Client"); restored.loadProfile("rotation-test");
                check(restored.getChoice("kill-aura", "rotation").equals(mode), "Rotation survives named profile round trip: " + mode);
                check(restored.loadWarnings().isEmpty(), "New mode reloads without warnings");
            }
        } finally {
            try (var files = Files.walk(directory)) {
                for (Path path : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }
    private static void rejects(Runnable action, String message) {
        try { action.run(); } catch (IllegalArgumentException expected) { check(true, message); return; }
        throw new AssertionError(message);
    }
    private static void equal(double expected, double actual, double tolerance, String message) {
        check(Double.isFinite(actual) && Math.abs(expected - actual) <= tolerance, message + ": " + actual);
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        assertions++;
    }
}
