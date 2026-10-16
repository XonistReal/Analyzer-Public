package dev.analyzer.privacy;

import dev.analyzer.core.AnalyzerCore;
import dev.analyzer.core.Category;
import dev.analyzer.core.Setting;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;

/** Deterministic username/token policy and normal config/profile alias persistence. */
public final class NameChangerSelfTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        same("<Player> Hello Player!", NameMaskPolicy.replace("<AnalyzerDev> Hello AnalyzerDev!", "AnalyzerDev", "Player"), "All exact own tokens");
        same("Player Player Player", NameMaskPolicy.replace("AnalyzerDev analyzerdev ANALYZERDEV", "AnalyzerDev", "Player"), "Case insensitive actual username");
        String collisions = "xAnalyzerDev AnalyzerDev2 AnalyzerDev_alt _AnalyzerDev AnalyzerDev_ éAnalyzerDev AnalyzerDev猫";
        same(collisions, NameMaskPolicy.replace(collisions, "AnalyzerDev", "Player"), "Username boundaries include Unicode letters/digits and underscores");
        same("Player's [Player] /Player", NameMaskPolicy.replace("AnalyzerDev's [AnalyzerDev] /AnalyzerDev", "AnalyzerDev", "Player"), "Punctuation boundaries");
        same("A[]Player.$", NameMaskPolicy.replace("A[]name.$", "name", "Player"), "Literal regex punctuation remains literal");
        same("AnalyzerDev_Alt", NameMaskPolicy.replace("AnalyzerDev", "AnalyzerDev", "AnalyzerDev_Alt"), "Alias may contain real name");
        same("AnalyzerDev_Alt", NameMaskPolicy.replace("AnalyzerDev_Alt", "AnalyzerDev", "AnalyzerDev_Alt"), "Alias is idempotent");
        same("AnalyzerDev", NameMaskPolicy.replace("AnalyzerDev", "AnalyzerDev", "AnalyzerDev"), "Identical alias does not recurse");
        same("§aPlayer§b after", NameMaskPolicy.replace("§aAnalyzer§bDev after", "AnalyzerDev", "Player"), "Username may cross native legacy formatting codes");
        same("§aPlayer§r / Player", NameMaskPolicy.replace("§aAnalyzerDev§r / AnalyzerDev", "AnalyzerDev", "Player"), "External legacy styles stay present");
        same("😀Player😀", NameMaskPolicy.replace("😀AnalyzerDev😀", "AnalyzerDev", "Player"), "Supplementary code points preserve indices");
        same("unchanged", NameMaskPolicy.replace("unchanged", "", "Player"), "Missing own name is inert");
        check(NameMaskPolicy.replace(null, "AnalyzerDev", "Player") == null, "Null remains null");
        same(NameMaskPolicy.OVERSIZE, NameMaskPolicy.replace("x".repeat(NameMaskPolicy.MAX_INPUT) + " AnalyzerDev", "AnalyzerDev", "Player"), "Oversize visual text fails closed");
        check(NameMaskPolicy.matches("AnalyzerDev / AnalyzerDev", "AnalyzerDev", "Player").size() == 2, "Spans expose exact original positions");
        var alias = Setting.text("alias", "Alias", "Player", 32);
        same("Stream_23", alias.normalize("  Stream_23  "), "Safe surrounding whitespace normalized");
        same("x".repeat(32), alias.normalize("x".repeat(32)), "Exact maximum allowed");
        for (Object invalid : new Object[]{"", " ", "bad name", "bad\nname", "bad\tname", "§aHidden", "bad/name", "bad\\name", "é", "😀", "a".repeat(33), 12, true}) {
            try { alias.normalize(invalid); throw new AssertionError("Accepted unsafe alias " + invalid); }
            catch (IllegalArgumentException expected) { checks++; }
        }
        Path temporary = Files.createTempDirectory("analyzer-name-changer-");
        try {
            AnalyzerCore core = new AnalyzerCore(temporary);
            check(core.definition("name-changer").category() == Category.CLIENT, "Client category");
            check(!core.isEnabled("name-changer"), "Opt in for existing/missing configs");
            same("Player", core.getText("name-changer", "alias"), "Readable default alias");
            core.setEnabled("name-changer", true); core.setValue("name-changer", "alias", " Stream_23 ");
            core.setValue("menu", "layout", "Click GUI"); core.save(); core.saveProfile("streaming");
            byte[] original = Files.readAllBytes(temporary.resolve("profiles/streaming.json"));
            AnalyzerCore loaded = new AnalyzerCore(temporary); loaded.load();
            check(loaded.isEnabled("name-changer"), "Master survives active save");
            same("Stream_23", loaded.getText("name-changer", "alias"), "Normalized alias survives active save");
            loaded.reset("name-changer"); check(!loaded.isEnabled("name-changer"), "Reset returns opt in");
            same("Player", loaded.getText("name-changer", "alias"), "Reset default alias");
            same("Click GUI", loaded.getChoice("menu", "layout"), "Unrelated appearance retained");
            loaded.loadProfile("streaming");
            check(loaded.isEnabled("name-changer"), "Master survives profile");
            same("Stream_23", loaded.getText("name-changer", "alias"), "Alias survives profile");
            check(Arrays.equals(original, Files.readAllBytes(temporary.resolve("profiles/streaming.json"))), "Loading does not rewrite profile");
            Files.writeString(temporary.resolve("profiles/old.json"), "{\"schemaVersion\":1,\"modules\":{\"hud\":{\"enabled\":true}}}");
            loaded.loadProfile("old");
            check(!loaded.isEnabled("name-changer"), "Old profile preserves opt-in default");
            same("Player", loaded.getText("name-changer", "alias"), "Old profile fills alias default");
            Files.writeString(temporary.resolve("profiles/invalid.json"), "{\"schemaVersion\":1,\"modules\":{\"name-changer\":{\"enabled\":true,\"settings\":{\"alias\":\"bad\\nname\"}}}}");
            loaded.loadProfile("invalid");
            check(loaded.isEnabled("name-changer"), "Invalid alias does not silently disable masking");
            same("Player", loaded.getText("name-changer", "alias"), "Unsafe saved alias falls back safely");
            check(!loaded.loadWarnings().isEmpty(), "Unsafe saved alias reports load warning");
        } finally {
            try (var paths = Files.walk(temporary)) { for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path); }
        }
        System.out.println("Name Changer passed " + checks + " assertions.");
    }
    private static void same(String expected, String actual, String message) { check(java.util.Objects.equals(expected, actual), message + " (" + actual + ")"); }
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
