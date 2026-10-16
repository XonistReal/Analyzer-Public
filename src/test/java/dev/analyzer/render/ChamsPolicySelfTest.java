package dev.analyzer.render;

import dev.analyzer.core.AnalyzerCore;
import dev.analyzer.core.Category;
import dev.analyzer.core.ChamsSettings;
import dev.analyzer.core.EspSettings;
import dev.analyzer.core.ModuleDefinition;
import dev.analyzer.core.Setting;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

/** Selection, disjoint depth passes, palette persistence and frame-snapshot checks for model chams. */
public final class ChamsPolicySelfTest {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("analyzer-chams-policy-");
        try {
            var esp = new ModuleDefinition("entity-esp", "Entity ESP", "Test palette", Category.VISUAL, false,
                    EspSettings.entityColors());
            var definitions = List.of(esp, ChamsSettings.definition());
            AnalyzerCore core = new AnalyzerCore(directory, definitions);
            check(!ChamsPolicy.read(core).enabled(), "Fresh chams remain opt-in");
            core.setEnabled("chams", true);
            var initial = ChamsPolicy.read(core);
            check(initial.style() == ChamsPolicy.Style.FLAT, "Plain material is the default");
            check(initial.hasVisiblePass() && initial.hasHiddenPass(), "Both independently colored model passes are available");
            check(initial.passes().equals(List.of(ChamsPolicy.Depth.HIDDEN_GREATER, ChamsPolicy.Depth.VISIBLE_LEQUAL)),
                    "Occluded pixels render before visible pixels");
            selection(core);
            palettesAndPersistence(core, definitions, directory);
            depthAndBounds();
            settingsValidation(core);
            System.out.println("Analyzer chams policy checks passed: " + assertions + " assertions");
        } finally {
            try (var files = Files.walk(directory)) {
                for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(file);
            }
        }
    }

    private static void selection(AnalyzerCore core) {
        var snapshot = ChamsPolicy.read(core);
        check(accept(snapshot, ChamsPolicy.Group.PLAYER, 128 * 128), "Player exactly at the range endpoint remains selected");
        check(!accept(snapshot, ChamsPolicy.Group.PLAYER, 128 * 128 + .001), "Player beyond range is excluded");
        check(accept(snapshot, ChamsPolicy.Group.HOSTILE, 1), "Hostile mobs are selected by default");
        check(!accept(snapshot, ChamsPolicy.Group.PASSIVE, 1), "Non-hostile mobs stay opt-in");
        check(!accept(snapshot, null, 1), "Non-living and unclassified entities are not selected");
        for (double invalid : new double[]{-1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
            check(!accept(snapshot, ChamsPolicy.Group.PLAYER, invalid), "Invalid distance cannot bypass range " + invalid);
        check(!snapshot.accepts(ChamsPolicy.Group.PLAYER, 1, true, true, false, false, false, false), "Removed entities excluded");
        check(!snapshot.accepts(ChamsPolicy.Group.PLAYER, 1, false, false, false, false, false, false), "Dead entities excluded");
        check(!snapshot.accepts(ChamsPolicy.Group.PLAYER, 1, false, true, false, false, false, true), "Spectators excluded");
        check(!snapshot.accepts(ChamsPolicy.Group.PLAYER, 1, false, true, false, false, true, false), "Invisible entities require opt-in");
        check(!snapshot.accepts(ChamsPolicy.Group.PLAYER, 1, false, true, true, false, false, false), "Own player is opt-in");
        core.setValue("chams", "self", true);
        core.setValue("chams", "invisibles", true);
        core.setValue("chams", "passives", true);
        var selected = ChamsPolicy.read(core);
        check(selected.accepts(ChamsPolicy.Group.PLAYER, 0, false, true, true, false, false, false), "Own model is available in third person");
        check(!selected.accepts(ChamsPolicy.Group.PLAYER, 0, false, true, true, true, false, false), "First person never draws own model around camera");
        check(selected.accepts(ChamsPolicy.Group.PLAYER, 1, false, true, false, false, true, false), "Explicit invisible opt-in works");
        check(accept(selected, ChamsPolicy.Group.PASSIVE, 1), "Explicit non-hostile selection works");
        check(!selected.accepts(ChamsPolicy.Group.PLAYER, 1, false, true, false, false, true, true), "Invisible opt-in does not enable spectators");
        core.setEnabled("chams", false);
        check(!accept(ChamsPolicy.read(core), ChamsPolicy.Group.PLAYER, 1), "Disable clears every category");
        check(ChamsPolicy.read(core).passes().isEmpty(), "Disable produces no depth passes");
        core.setEnabled("chams", true);
        core.setValue("chams", "visible-opacity", 0);
        core.setValue("chams", "hidden-opacity", 0);
        check(!accept(ChamsPolicy.read(core), ChamsPolicy.Group.PLAYER, 1), "Fully transparent settings omit model work");
        core.reset("chams");
        core.setEnabled("chams", true);
    }

    private static void palettesAndPersistence(AnalyzerCore core, List<ModuleDefinition> definitions, Path directory) throws Exception {
        core.setValue("entity-esp", "players-color", "Custom");
        core.setValue("entity-esp", "players-red", 17);
        core.setValue("entity-esp", "players-green", 34);
        core.setValue("entity-esp", "players-blue", 51);
        core.setValue("chams", "players-color", "Gold");
        core.setValue("chams", "players-hidden-color", "Pink");
        var matched = ChamsPolicy.read(core);
        check((matched.color(ChamsPolicy.Group.PLAYER, false) & 0xFFFFFF) == 0x112233, "Visible material uses custom Entity ESP palette without enabling ESP");
        check((matched.color(ChamsPolicy.Group.PLAYER, true) & 0xFFFFFF) == (EspSettings.palette("Pink") & 0xFFFFFF), "Hidden palette is independent of the matched visible palette");
        check((matched.color(ChamsPolicy.Group.PLAYER, false) >>> 24) == 217, "Visible opacity is encoded independently");
        check((matched.color(ChamsPolicy.Group.PLAYER, true) >>> 24) == 115, "Hidden opacity is encoded independently");
        core.setValue("entity-esp", "players-red", 99);
        check((matched.color(ChamsPolicy.Group.PLAYER, false) & 0xFFFFFF) == 0x112233, "Captured frame palette does not change during a profile edit");
        core.setValue("chams", "match-esp-colors", false);
        var independent = ChamsPolicy.read(core);
        check((independent.color(ChamsPolicy.Group.PLAYER, false) & 0xFFFFFF) == (EspSettings.palette("Gold") & 0xFFFFFF), "Visible palette opt-out retains its independently saved choice");
        core.setValue("chams", "hostiles-hidden-color", "Custom");
        core.setValue("chams", "hostiles-hidden-red", 9);
        core.setValue("chams", "hostiles-hidden-green", 8);
        core.setValue("chams", "hostiles-hidden-blue", 7);
        core.setValue("chams", "style", "Wireframe");
        core.setValue("chams", "wire-width", 2.75);
        core.saveProfile("Materials");
        core.save();
        AnalyzerCore loaded = new AnalyzerCore(directory, definitions);
        loaded.load();
        check(ChamsPolicy.read(loaded).equals(ChamsPolicy.read(core)), "All material/filter/palette settings survive configuration load");
        loaded.reset("chams");
        loaded.loadProfile("Materials");
        check(ChamsPolicy.read(loaded).equals(ChamsPolicy.read(core)), "Profiles restore the exact material and independent palettes");
        check(ChamsPolicy.read(loaded).style() == ChamsPolicy.Style.WIREFRAME && ChamsPolicy.read(loaded).wireWidth() == 2.75,
                "Wireframe material and selected width survive profile restoration");
        check((ChamsPolicy.read(loaded).color(ChamsPolicy.Group.HOSTILE, true) & 0xFFFFFF) == 0x090807, "Occluded custom RGB survives serialization");
        core.setValue("chams", "through-walls", false);
        check(ChamsPolicy.read(core).passes().equals(List.of(ChamsPolicy.Depth.VISIBLE_LEQUAL)), "Disabling through-wall rendering removes only the hidden pass");
        core.setValue("chams", "visible-opacity", 0);
        check(ChamsPolicy.read(core).passes().isEmpty(), "Visible opacity zero does not implicitly enable an occluded pass");
        core.setValue("chams", "through-walls", true);
        check(ChamsPolicy.read(core).passes().equals(List.of(ChamsPolicy.Depth.HIDDEN_GREATER)), "Occluded-only style still uses a strict depth test");
    }

    private static void depthAndBounds() {
        // For every valid pixel depth pair there is exactly one classification; equality belongs only to visible.
        for (int world = 0; world <= 20; world++) for (int fragment = 0; fragment <= 20; fragment++) {
            double worldDepth = world / 20d, fragmentDepth = fragment / 20d;
            boolean hidden = ChamsPolicy.Depth.HIDDEN_GREATER.accepts(fragmentDepth, worldDepth);
            boolean visible = ChamsPolicy.Depth.VISIBLE_LEQUAL.accepts(fragmentDepth, worldDepth);
            check(hidden != visible, "Visible and hidden passes partition pixel depth " + fragment + "/" + world);
            check(hidden == (fragment > world), "Only geometry behind recorded world depth is occluded " + fragment + "/" + world);
        }
        for (double invalid : new double[]{-1, 1.01, Double.NaN, Double.POSITIVE_INFINITY}) {
            for (var depth : ChamsPolicy.Depth.values()) {
                check(!depth.accepts(invalid, .5), "Bad fragment depth is discarded");
                check(!depth.accepts(.5, invalid), "Bad world depth is discarded");
            }
        }
        var colors = new ChamsPolicy.Colors(0xFF010203, 0xFF040506, 0xFF070809);
        var invalid = new ChamsPolicy.Snapshot(true, null, true, true, true, true, true, Double.NaN,
                true, Double.NaN, Double.POSITIVE_INFINITY, Float.NaN, Float.NaN, Float.NaN, colors, colors);
        check(invalid.style() == ChamsPolicy.Style.FLAT, "Missing material has a readable fallback");
        check(!accept(invalid, ChamsPolicy.Group.PLAYER, 0), "Invalid range and opacity never select unlimited entities");
        check(invalid.passes().isEmpty(), "Invalid opacity cannot submit visible work");
        check(invalid.effectStrength() == 1 && invalid.animationSpeed() == 1 && invalid.wireWidth() == 1,
                "Nonfinite shader controls have finite safe fallbacks");
        var clamped = new ChamsPolicy.Snapshot(true, ChamsPolicy.Style.WIREFRAME, true, true, true, false, false, 9999,
                true, 120, -10, 3, -3, 99, colors, colors);
        check(clamped.range() == 256 && clamped.visibleOpacity() == 100 && clamped.hiddenOpacity() == 0, "Bounds limit range and independent opacities");
        check(clamped.effectStrength() == 2 && clamped.animationSpeed() == 0 && clamped.wireWidth() == 3, "Material uniforms obey their supported intervals");
        check(clamped.color(ChamsPolicy.Group.PLAYER, false) == 0xFF010203, "Opacity clamping preserves the exact selected RGB");
        check(clamped.color(ChamsPolicy.Group.HOSTILE, true) == 0x00040506, "Transparent hidden palette preserves RGB for future edits");
        check(ChamsPolicy.Style.from("Old box") == ChamsPolicy.Style.FLAT && ChamsPolicy.Style.from(null) == ChamsPolicy.Style.FLAT,
                "Historical/unrecognized renderer labels fall back safely without inventing a shader style");
    }

    private static void settingsValidation(AnalyzerCore core) {
        HashSet<Integer> codes = new HashSet<>();
        HashSet<String> settingIds = new HashSet<>();
        for (var style : ChamsPolicy.Style.values()) {
            check(codes.add(style.shaderCode()), "Each shader material has its own uniform code");
            core.setValue("chams", "style", style.label());
            check(ChamsPolicy.read(core).style() == style, "Each exposed material selects the intended renderer " + style);
        }
        for (Setting<?> setting : ChamsSettings.settings()) {
            check(settingIds.add(setting.id()), "No ambiguous saved material setting " + setting.id());
            if (setting.kind() == Setting.Kind.NUMBER) {
                core.setValue("chams", setting.id(), setting.min());
                check(Double.isFinite(core.getNumber("chams", setting.id())), "Lower numeric endpoint is usable " + setting.id());
                core.setValue("chams", setting.id(), setting.max());
                check(Double.isFinite(core.getNumber("chams", setting.id())), "Upper numeric endpoint is usable " + setting.id());
            }
        }
        boolean rejected = false;
        try { core.setValue("chams", "style", "Old box"); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "Live edits cannot silently select unknown material names");
    }

    private static boolean accept(ChamsPolicy.Snapshot snapshot, ChamsPolicy.Group group, double distanceSquared) {
        return snapshot.accepts(group, distanceSquared, false, true, false, false, false, false);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
