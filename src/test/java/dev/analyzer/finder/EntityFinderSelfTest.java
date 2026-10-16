package dev.analyzer.finder;

import dev.analyzer.core.AnalyzerCore;
import dev.analyzer.core.EntityFinderModules;
import dev.analyzer.core.ModuleDefinition;
import dev.analyzer.finder.EntityFinderRules.Facts;
import dev.analyzer.finder.EntityFinderRules.Group;
import dev.analyzer.finder.EntityFinderRules.Kind;
import dev.analyzer.finder.EntityFinderRules.Options;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/** Real selection boundaries and saved independently configured entity finders. No game bootstrap required. */
public final class EntityFinderSelfTest {
    private static int assertions;
    private static final Options ALL = new Options(true, 96, true, true, true, true, true,
            true, true, true, false, true, true, false);
    private static final Options DEFAULT = new Options(true, 96, true, true, true, false, false,
            true, true, true, false, true, false, false);

    public static void main(String[] args) throws Exception {
        specializedFamilies();
        broadGroupsAndValidity();
        surveyBudgets();
        configuration();
        System.out.println("Analyzer entity finder checks passed: " + assertions + " assertions");
    }

    private static void surveyBudgets() {
        check(EntityFinderRules.scanAllowance(0, 16384) == 0, "No enabled finder schedules entity reads");
        check(EntityFinderRules.scanAllowance(8, 0) == 0, "A zero requested budget performs no survey reads");
        check(EntityFinderRules.scanAllowance(1, 16384) == 16384, "One enabled finder can use its complete configured budget");
        check(EntityFinderRules.scanAllowance(8, 512) == 512, "Small pacing budget is preserved beside larger finders");
        for (int active = 1; active <= 8; active++) {
            int allowance = EntityFinderRules.scanAllowance(active, 16384);
            check(allowance > 0 && allowance * active <= 16384, "Each enabled cursor progresses inside the shared cap: " + active);
            check(EntityFinderRules.scanAllowance(active, 512) <= 512, "Other enabled modules cannot increase requested pacing: " + active);
        }
    }

    private static Facts facts(Kind kind, Group group) {
        return new Facts(kind, group, 25, false, true, false, false, true, false, false, false, false);
    }

    private static void specializedFamilies() {
        for (Kind kind : Kind.values()) {
            Facts candidate = facts(kind, Group.PASSIVE);
            check(accept("llama-esp", candidate, ALL) == (kind == Kind.LLAMA || kind == Kind.TRADER_LLAMA), "Only llamas match: " + kind);
            check(accept("pillager-esp", candidate, ALL) == (kind == Kind.PILLAGER), "Only actual pillagers match: " + kind);
            check(accept("wandering-trader-esp", candidate, ALL) == (kind == Kind.WANDERING_TRADER), "Only wandering traders match: " + kind);
            check(accept("villager-esp", candidate, ALL) == (kind == Kind.VILLAGER || kind == Kind.ZOMBIE_VILLAGER), "Villager families: " + kind);
            check(accept("collectible-esp", candidate, ALL) == (kind == Kind.ITEM_FRAME || kind == Kind.GLOW_ITEM_FRAME || kind == Kind.ARMOR_STAND), "Collectibles use actual supported families: " + kind);
            check(!accept("drowned-trident-esp", candidate, ALL), "No trident false positives from entity family alone: " + kind);
            Facts trident = new Facts(kind, candidate.group(), 25, false, true, false, false, true, false, true, false, false);
            check(accept("drowned-trident-esp", trident, ALL) == (kind == Kind.DROWNED), "Actual held trident and drowned both required: " + kind);
        }
        Options noExtras = new Options(true, 96, true, true, true, false, false,
                true, true, true, false, false, false, false);
        check(!accept("llama-esp", facts(Kind.TRADER_LLAMA, Group.PASSIVE), noExtras), "Trader llama switch excludes only trader llamas");
        check(accept("llama-esp", facts(Kind.LLAMA, Group.PASSIVE), noExtras), "Ordinary llamas stay selected");
        check(!accept("villager-esp", facts(Kind.ZOMBIE_VILLAGER, Group.HOSTILE), DEFAULT), "Zombie villagers opt in");
        Options adult = new Options(true, 96, true, true, true, false, false,
                true, true, true, false, true, true, true);
        Facts baby = new Facts(Kind.VILLAGER, Group.PASSIVE, 25, false, true, false, false, true, false, false, false, true);
        check(!accept("villager-esp", baby, adult), "Adult-only excludes babies");
        check(accept("villager-esp", baby, DEFAULT), "Babies retained without adult-only");
        for (Kind kind : List.of(Kind.ITEM_FRAME, Kind.GLOW_ITEM_FRAME)) {
            Options filled = new Options(true, 96, true, true, true, false, false,
                    true, true, true, true, true, false, false);
            Facts empty = new Facts(kind, Group.OTHER, 25, false, true, false, false, true, false, false, true, false);
            check(!accept("collectible-esp", empty, filled), "Filled-only excludes empty frame: " + kind);
            check(accept("collectible-esp", empty, DEFAULT), "Empty frames can be selected: " + kind);
            check(accept("collectible-esp", facts(kind, Group.OTHER), filled), "Displayed items qualify: " + kind);
        }
        for (Kind kind : List.of(Kind.ITEM_FRAME, Kind.GLOW_ITEM_FRAME, Kind.ARMOR_STAND)) {
            Options off = new Options(true, 96, false, false, false, false, false,
                    false, false, false, false, false, false, false);
            check(!accept("collectible-esp", facts(kind, Group.OTHER), off), "Each collectible family remains configurable: " + kind);
        }
    }

    private static void broadGroupsAndValidity() {
        for (Group group : Group.values()) {
            Facts invisible = new Facts(Kind.OTHER, group, 25, false, true, false, false, true, true, false, false, false);
            check(accept("invisible-esp", invisible, ALL) == (group != Group.OTHER), "Invisible groups are broad and explicit: " + group);
            check(!accept("invisible-esp", facts(Kind.OTHER, group), ALL), "Visible entities never match Invisible ESP: " + group);
            check(accept("skeleton-esp", invisible, ALL) == (group != Group.OTHER), "Skeleton group filtering: " + group);
            check(!accept("skeleton-esp", invisible, DEFAULT), "Invisible bones need explicit opt-in: " + group);
        }
        for (int selected = 0; selected < 3; selected++) {
            Options only = new Options(true, 96, selected == 0, selected == 1, selected == 2, true, true,
                    true, true, true, false, true, true, false);
            for (Group group : Group.values()) {
                Facts invisible = new Facts(Kind.OTHER, group, 25, false, true, false, false, true, true, false, false, false);
                check(accept("invisible-esp", invisible, only) == (group.ordinal() == selected), "Invisible independent group switch " + selected + "/" + group);
                check(accept("skeleton-esp", invisible, only) == (group.ordinal() == selected), "Skeleton independent group switch " + selected + "/" + group);
            }
        }
        for (ModuleDefinition module : EntityFinderModules.definitions()) {
            String id = module.id();
            Kind kind = switch (id) {
                case "collectible-esp" -> Kind.ITEM_FRAME;
                case "drowned-trident-esp" -> Kind.DROWNED;
                case "llama-esp" -> Kind.LLAMA;
                case "pillager-esp" -> Kind.PILLAGER;
                case "villager-esp" -> Kind.VILLAGER;
                case "wandering-trader-esp" -> Kind.WANDERING_TRADER;
                default -> Kind.OTHER;
            };
            Facts valid = new Facts(kind, Group.PLAYER, 96 * 96, false, true, false, false, true, true, true, false, false);
            check(accept(id, valid, ALL), "Range endpoint included: " + id);
            for (double distance : new double[]{96 * 96 + .01, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
                Facts invalid = new Facts(kind, Group.PLAYER, distance, false, true, false, false, true, true, true, false, false);
                check(!accept(id, invalid, ALL), "Invalid or out-of-range entity excluded: " + id + "/" + distance);
            }
            for (int flag = 0; flag < 4; flag++) {
                Facts invalid = new Facts(kind, Group.PLAYER, 1, flag == 0, flag != 1, flag == 2, flag == 3, true, true, true, false, false);
                check(!accept(id, invalid, ALL), "Removed/dead/spectator/first-person-self excluded " + id + "/" + flag);
            }
            Facts thirdSelf = new Facts(kind, Group.PLAYER, 1, false, true, false, true, false, true, true, false, false);
            check(accept(id, thirdSelf, ALL), "Explicit third person self passes common validity: " + id);
            check(!accept(id, thirdSelf, DEFAULT), "Own player requires opt-in: " + id);
            Options disabled = new Options(false, 96, true, true, true, true, true, true, true, true, false, true, true, false);
            check(!accept(id, valid, disabled), "Disabled module performs no selection: " + id);
        }
        check(!accept("unknown", facts(Kind.LLAMA, Group.PASSIVE), ALL), "Unknown module never becomes a catch-all");
        check(!accept(null, facts(Kind.LLAMA, Group.PASSIVE), ALL), "Null module excluded");
        check(!accept("llama-esp", null, ALL), "Missing entity facts excluded");
        check(!accept("llama-esp", facts(Kind.LLAMA, Group.PASSIVE), null), "Missing policy excluded");
        Options nonfinite = new Options(true, Double.NaN, true, true, true, true, true, true, true, true, false, true, true, false);
        check(!accept("llama-esp", facts(Kind.LLAMA, Group.PASSIVE), nonfinite), "Nonfinite range never bypasses limits");
    }

    private static void configuration() throws Exception {
        Path directory = Files.createTempDirectory("analyzer-entity-finders-");
        try {
            List<ModuleDefinition> modules = EntityFinderModules.definitions();
            check(modules.size() == 8, "Eight independently configured entity ESP modules");
            AnalyzerCore core = new AnalyzerCore(directory, modules);
            for (ModuleDefinition module : modules) {
                String id = module.id();
                check(!core.isEnabled(id), "New module stays opt-in: " + id);
                check(core.getNumber(id, "max-markers") <= 512, "Bounded default marker count: " + id);
                core.setValue(id, "max-markers", 100_000);
                check(core.getNumber(id, "max-markers") == 512, "Marker count hard cap: " + id);
                core.setValue(id, "scan-budget", 1_000_000);
                check(core.getNumber(id, "scan-budget") == 16_384, "Entity survey hard cap: " + id);
                check(!core.getBoolean(id, "notify"), "Discovery messages opt in: " + id);
                core.setValue(id, "marker-color", "Custom");
                core.setValue(id, "marker-red", 21);
                core.setValue(id, "marker-green", 34);
                core.setValue(id, "marker-blue", 55);
            }
            core.setEnabled("villager-esp", true);
            core.setValue("villager-esp", "zombie-villagers", true);
            core.setValue("skeleton-esp", "head", false);
            core.setValue("collectible-esp", "banners", false);
            core.save();
            AnalyzerCore reloaded = new AnalyzerCore(directory, modules);
            reloaded.load();
            check(reloaded.isEnabled("villager-esp"), "Finder enabled state persists");
            check(reloaded.getBoolean("villager-esp", "zombie-villagers"), "Family selection persists");
            check(!reloaded.getBoolean("skeleton-esp", "head"), "Pose options persist");
            check(!reloaded.getBoolean("collectible-esp", "banners"), "Placed banner option persists independently");
            for (ModuleDefinition module : modules) {
                check(reloaded.getChoice(module.id(), "marker-color").equals("Custom"), "Independent marker palette persists " + module.id());
                check(reloaded.getNumber(module.id(), "marker-blue") == 55, "Custom channels persist " + module.id());
            }
        } finally {
            try (var files = Files.walk(directory)) {
                for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(file);
            }
        }
    }

    private static boolean accept(String id, Facts candidate, Options options) { return EntityFinderRules.accepts(id, candidate, options); }
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
