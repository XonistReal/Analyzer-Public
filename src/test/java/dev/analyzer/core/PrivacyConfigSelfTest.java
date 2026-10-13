package dev.analyzer.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** Saved privacy choices remain explicit; missing older fields receive protections. */
public final class PrivacyConfigSelfTest {
    private static int assertions;
    private static final List<String> FIELDS = List.of("vanilla-brand", "block-mod-channels", "vanilla-only",
            "hide-known-packs", "block-login-queries", "block-key-probes", "block-local-pack-urls", "scrub-pack-headers");

    public static void main(String[] args) throws Exception {
        Path temporary = Files.createTempDirectory("analyzer-privacy-config-");
        try {
            AnalyzerCore core = new AnalyzerCore(temporary);
            check(core.definition("server-privacy").category() == Category.CLIENT, "Privacy is discoverable in Client");
            check(core.isEnabled("server-privacy"), "Privacy is enabled for missing configs");
            for (String field : FIELDS) {
                check(core.getBoolean("server-privacy", field), "Protection defaults on: " + field);
                try { core.setValue("server-privacy", field, "false"); throw new AssertionError("Accepted wrong type"); }
                catch (IllegalArgumentException expected) { assertions++; }
                core.setValue("server-privacy", field, false);
            }
            core.setEnabled("server-privacy", false);
            core.setValue("menu", "layout", "Click GUI");
            core.setValue("watermark", "logo", "A logo");
            core.setKeybind("server-privacy", 80);
            core.save();
            core.saveProfile("privacy-off");
            byte[] originalProfile = Files.readAllBytes(temporary.resolve("profiles/privacy-off.json"));
            AnalyzerCore loaded = new AnalyzerCore(temporary);
            loaded.load();
            verifyOff(loaded);
            loaded.reset("server-privacy");
            check(loaded.isEnabled("server-privacy"), "Reset restores the privacy default");
            for (String field : FIELDS) check(loaded.getBoolean("server-privacy", field), "Reset restores " + field);
            check(loaded.getChoice("menu", "layout").equals("Click GUI"), "Privacy reset preserves layout");
            check(loaded.getChoice("watermark", "logo").equals("A logo"), "Privacy reset preserves selected watermark");
            loaded.loadProfile("privacy-off");
            verifyOff(loaded);
            check(Arrays.equals(originalProfile, Files.readAllBytes(temporary.resolve("profiles/privacy-off.json"))), "Loading leaves profile bytes intact");

            String old = "{\"schemaVersion\":1,\"modules\":{\"menu\":{\"settings\":{\"layout\":\"Classic\"}},\"watermark\":{\"settings\":{\"logo\":\"A logo\"}},\"kill-aura\":{\"enabled\":true,\"keybind\":71}}}";
            Files.writeString(temporary.resolve("profiles/older-hvh.json"), old);
            loaded.loadProfile("older-hvh");
            check(loaded.isEnabled("server-privacy"), "An older HVH profile cannot silently disable missing privacy");
            for (String field : FIELDS) check(loaded.getBoolean("server-privacy", field), "Missing old field uses protection: " + field);
            check(loaded.getChoice("menu", "layout").equals("Studio"), "Legacy layout migration remains active");
            check(loaded.getChoice("watermark", "logo").equals("A logo"), "Older profile retains explicit watermark");
            check(loaded.isEnabled("kill-aura") && loaded.keybind("kill-aura") == 71, "Privacy migration preserves unrelated HVH state");
            check(Files.readString(temporary.resolve("profiles/older-hvh.json")).equals(old), "Older file is not rewritten");

            String partial = "{\"schemaVersion\":1,\"modules\":{\"server-privacy\":{\"enabled\":false,\"settings\":{\"vanilla-only\":false,\"vanilla-brand\":\"false\"}}}}";
            Files.writeString(temporary.resolve("profiles/partial.json"), partial);
            loaded.loadProfile("partial");
            check(!loaded.isEnabled("server-privacy"), "Explicit master opt-out is preserved");
            check(!loaded.getBoolean("server-privacy", "vanilla-only"), "Explicit compatibility choice is preserved");
            check(loaded.getBoolean("server-privacy", "vanilla-brand"), "Malformed field retains safe default");
            check(!loaded.loadWarnings().isEmpty(), "Malformed protection field is reported");
            System.out.println("Privacy configuration passed " + assertions + " assertions.");
        } finally {
            try (var paths = Files.walk(temporary)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }

    private static void verifyOff(AnalyzerCore core) {
        check(!core.isEnabled("server-privacy"), "Master opt-out survives persistence");
        check(core.keybind("server-privacy") == 80, "Assigned privacy keybind survives persistence");
        for (String field : FIELDS) check(!core.getBoolean("server-privacy", field), "Opt-out survives persistence: " + field);
        check(core.getChoice("menu", "layout").equals("Click GUI"), "Layout stays independent");
        check(core.getChoice("watermark", "logo").equals("A logo"), "Logo stays independent");
    }

    private static void check(boolean ok, String message) {
        assertions++;
        if (!ok) throw new AssertionError(message);
    }
}
