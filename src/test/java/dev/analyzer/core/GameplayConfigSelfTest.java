package dev.analyzer.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/** Regression coverage for loading real pre-overhaul IDs into expanded module definitions. */
public final class GameplayConfigSelfTest {
    private static int assertions;
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("analyzer-gameplay-config-");
        try {
            Files.writeString(directory.resolve("config.json"), """
                    {"schemaVersion":1,"modules":{
                      "speed":{"enabled":true,"keybind":71,"settings":{"speed":0.55}},
                      "kill-aura":{"enabled":false,"settings":{"players":false,"hostiles":true,"passives":false,"range":3,"fov":180,"turn-speed":12,"cooldown":0.95}},
                      "crystal-aura":{"enabled":false,"settings":{"place":true,"break":false,"self-distance":4,"health":16,"delay":6,"cooldown":0.95}},
                      "target-strafe":{"enabled":false,"settings":{"radius":3,"speed":0.22,"clockwise":false}},
                      "not-an-analyzer-module":{"enabled":true,"settings":{}}
                    }}
                    """);
            AnalyzerCore core = new AnalyzerCore(directory);
            core.load();
            check(core.modules().size() == 156, "Finder extensions retain the existing gameplay definitions");
            check(core.loadWarnings().isEmpty(), "Old setting types and choices remain valid");
            check(core.isEnabled("speed") && core.keybind("speed") == 71, "Explicit old enable and binding survive");
            equal(.55, core.getNumber("speed", "speed"), "Old speed is retained");
            equal(1, core.getNumber("speed", "multiplier"), "Added multiplier does not multiply an old saved speed");
            check(core.getChoice("speed", "profile").equals("Standard"), "Old direct-speed behavior remains selected");
            check(!core.getBoolean("kill-aura", "players") && core.getBoolean("kill-aura", "hostiles"), "Old target categories survive");
            equal(.95, core.getNumber("kill-aura", "cooldown"), "Old cooldown fraction remains a fraction");
            equal(12, core.getNumber("kill-aura", "turn-speed"), "Old camera rate survives");
            check(!core.getBoolean("crystal-aura", "break"), "Old crystal break preference survives");
            equal(4, core.getNumber("crystal-aura", "self-distance"), "Old distance safety remains");
            equal(16, core.getNumber("crystal-aura", "health"), "Old health safety remains");
            equal(6, core.getNumber("crystal-aura", "delay"), "Old action delay remains");
            check(!core.getBoolean("target-strafe", "clockwise"), "Old orbit direction remains");
            for (var module : core.modules()) if (module.category() == Category.COMBAT || module.category() == Category.HVH || module.category() == Category.MOVEMENT) {
                check(!module.defaultEnabled(), "Gameplay remains opt-in: " + module.id());
                for (var setting : module.settings()) check(core.getValue(module.id(), setting.id()) != null, "Added settings are populated: " + module.id() + "/" + setting.id());
            }
            core.setValue("speed", "multiplier", 2.3); core.save();
            AnalyzerCore restored = new AnalyzerCore(directory); restored.load();
            equal(2.3, restored.getNumber("speed", "multiplier"), "Added settings persist");
            equal(.55, restored.getNumber("speed", "speed"), "Old and added settings coexist after save");
            check(restored.loadWarnings().isEmpty(), "Expanded snapshots reload cleanly");
            System.out.println("Gameplay configuration migration: " + assertions + " assertions passed.");
        } finally {
            try (var files = Files.walk(directory)) {
                for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(file);
            }
        }
    }
    private static void equal(double expected, double actual, String label) { check(Math.abs(expected - actual) < .000001, label); }
    private static void check(boolean passed, String label) { if (!passed) throw new AssertionError(label); assertions++; }
}
