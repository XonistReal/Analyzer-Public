package dev.analyzer.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/** Dependency-free assertions; run with the project's coreTest Gradle task. */
public final class CoreSelfTest {
    private static int assertions;
    public static void main(String[] args) throws Exception {
        Path temporary = Files.createTempDirectory("analyzer-core-test-");
        try {
            AnalyzerCore core = new AnalyzerCore(temporary);
            check(core.modules().size() == 10, "Complete module registry");
            check(core.getChoice("menu", "material").equals("Glass"), "Glass interface default");
            core.setValue("menu", "accent", "Gold");
            check(core.getChoice("menu", "accent").equals("Gold"), "Interface theme choices");
            check(core.isEnabled("sky") && core.getChoice("sky", "preset").equals("Aurora"), "Visible sky defaults");
            core.setValue("sky", "intensity", 8.0);
            equal(1, core.getNumber("sky", "intensity"), "Sky intensity clamps");
            core.setValue("sky", "speed", 0.0);
            equal(0, core.getNumber("sky", "speed"), "Sky animation can be frozen");
            core.setValue("entity-esp", "style", "Screen");
            check(core.getChoice("entity-esp", "style").equals("Screen"), "Projected ESP style");
            core.setValue("shader", "preset", "Bloom");
            check(core.getChoice("shader", "preset").equals("Bloom"), "Neutral bloom preset");
            check(core.isEnabled("coordinates"), "Coordinate defaults");
            check(core.keybind("entity-esp") == -1, "Unbound key default");
            check(core.getBoolean("entity-esp", "labels") && core.getBoolean("entity-esp", "health")
                    && core.getBoolean("storage-esp", "labels"), "ESP information defaults");
            core.setValue("entity-esp", "range", 999);
            equal(256, core.getNumber("entity-esp", "range"), "Upper clamping");
            core.setValue("entity-esp", "range", -100);
            equal(16, core.getNumber("entity-esp", "range"), "Lower clamping");
            core.setValue("entity-esp", "line-width", 1.62);
            equal(1.5, core.getNumber("entity-esp", "line-width"), "Step snapping");
            rejects(() -> core.setValue("entity-esp", "range", Double.NaN), "Nonfinite values");
            rejects(() -> core.setValue("entity-esp", "players", "true"), "Typed booleans");
            rejects(() -> core.setValue("shader", "preset", "Unknown"), "Choice validation");
            rejects(() -> core.setKeybind("entity-esp", 900), "Key validation");
            core.setEnabled("entity-esp", true);
            core.setKeybind("entity-esp", 71);
            core.setValue("entity-esp", "range", 96);
            core.setValue("shader", "preset", "Ember");
            core.save();
            AnalyzerCore restored = new AnalyzerCore(temporary);
            restored.load();
            check(restored.isEnabled("entity-esp"), "Enabled round trip");
            check(restored.keybind("entity-esp") == 71, "Keybind round trip");
            equal(96, restored.getNumber("entity-esp", "range"), "Numeric round trip");
            check(restored.getChoice("shader", "preset").equals("Ember"), "Choice round trip");
            restored.saveProfile("Night Travel");
            check(restored.profiles().equals(java.util.List.of("night-travel")), "Normalized profile list");
            restored.setValue("entity-esp", "range", 200);
            restored.loadProfile("Night Travel");
            equal(96, restored.getNumber("entity-esp", "range"), "Named profile loading");
            rejects(() -> restored.saveProfile("../outside"), "Path traversal prevention");
            rejects(() -> restored.saveProfile("CON"), "Reserved Windows filenames");
            restored.deleteProfile("Night Travel");
            check(restored.profiles().isEmpty(), "Profile deletion");
            Files.writeString(temporary.resolve("config.json"), "{broken");
            ioFailure(restored::load, "Malformed config must fail");
            equal(96, restored.getNumber("entity-esp", "range"), "Malformed load preserves current values");
            restored.save();
            check(recoveryFiles(temporary).size() == 1, "Malformed document preserved before automatic save");
            check(Files.readString(recoveryFiles(temporary).getFirst()).equals("{broken"), "Recovery preserves original bytes");
            Files.writeString(temporary.resolve("config.json"), """
                    {"schemaVersion":1,"modules":{"entity-esp":{"enabled":true,"keybind":71.5,
                    "settings":{"range":1000,"players":"yes","style":"Unknown"}}}}
                    """);
            restored.load();
            equal(256, restored.getNumber("entity-esp", "range"), "Disk values clamp");
            check(restored.getBoolean("entity-esp", "players"), "Invalid setting retains default");
            check(restored.keybind("entity-esp") == -1, "Fractional key rejected");
            check(restored.loadWarnings().size() == 3, "Invalid values diagnosed");
            Files.writeString(temporary.resolve("config.json"), "{\"schemaVersion\":99,\"modules\":{}}");
            ioFailure(restored::load, "Future schema must fail");
            equal(256, restored.getNumber("entity-esp", "range"), "Future schema preserves values");
            restored.save();
            check(recoveryFiles(temporary).size() == 2, "Future schema preserved before automatic save");
            check(recoveryFiles(temporary).stream().anyMatch(path -> {
                try { return Files.readString(path).contains("\"schemaVersion\":99"); }
                catch (IOException exception) { throw new java.io.UncheckedIOException(exception); }
            }), "Recovery retains unsupported schema");
            Files.writeString(temporary.resolve("config.json"), "{\"schemaVersion\":1}");
            ioFailure(restored::load, "Structurally invalid document must fail");
            equal(256, restored.getNumber("entity-esp", "range"), "Rejected document preserves values");
            restored.save();
            check(recoveryFiles(temporary).size() == 3, "Rejected document preserved before automatic save");
            check(recoveryFiles(temporary).stream().anyMatch(path -> {
                try { return Files.readString(path).equals("{\"schemaVersion\":1}"); }
                catch (IOException exception) { throw new java.io.UncheckedIOException(exception); }
            }), "Recovery retains structurally invalid document");
            try (var files = Files.list(temporary)) {
                check(files.noneMatch(path -> path.toString().endsWith(".tmp")), "Atomic write cleans temporary files");
            }
            System.out.println("Analyzer core: " + assertions + " assertions passed.");
        } finally {
            try (var files = Files.walk(temporary)) {
                for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(file);
            }
        }
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        assertions++;
    }

    private static void equal(double expected, double actual, String label) {
        check(Math.abs(expected - actual) < 0.000001, label + ": expected " + expected + ", got " + actual);
    }

    private static void rejects(ThrowingAction action, String label) throws Exception {
        try { action.run(); } catch (IllegalArgumentException expected) { assertions++; return; }
        throw new AssertionError(label);
    }

    private static void ioFailure(ThrowingAction action, String label) throws Exception {
        try { action.run(); } catch (IOException expected) { assertions++; return; }
        throw new AssertionError(label);
    }

    private static java.util.List<Path> recoveryFiles(Path directory) throws IOException {
        try (var files = Files.list(directory)) {
            return files.filter(path -> path.getFileName().toString().startsWith("config-recovery-")).toList();
        }
    }

    @FunctionalInterface private interface ThrowingAction { void run() throws Exception; }
}
