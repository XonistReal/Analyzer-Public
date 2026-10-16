package dev.analyzer.finder;

import dev.analyzer.core.ModuleDefinition;
import dev.analyzer.core.SurveyFinderModules;
import dev.analyzer.finder.FinderData.ChunkFacts;
import dev.analyzer.finder.FinderData.Options;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Actual count thresholds, honest candidate evidence and bounded world-scoped history. */
public final class ChunkFinderSelfTest {
    private static int assertions;
    private static final String SCOPE = "a".repeat(64);

    public static void main(String[] args) throws Exception {
        definitions();
        stashRules();
        chunkRules();
        evidenceMembership();
        kelpRules();
        journal();
        System.out.println("Analyzer chunk finder checks passed: " + assertions + " assertions");
    }

    private static void definitions() {
        List<ModuleDefinition> definitions = SurveyFinderModules.definitions();
        check(definitions.size() == 4, "Four original survey modules");
        for (ModuleDefinition module : definitions) {
            check(!module.defaultEnabled(), "Survey remains opt-in: " + module.id());
            check(!module.description().contains("chance"), "No invented calibrated probabilities");
            for (var setting : module.settings()) check(setting.normalize(setting.defaultValue()).equals(setting.defaultValue()), "Valid setting default " + setting.id());
        }
        check(ChunkFinderRules.enabledChunkScan("advanced-stash-finder"), "Stashes require loaded chunks");
        check(!ChunkFinderRules.enabledChunkScan("region-map"), "Coordinate grid does not request block scans");
    }

    private static void stashRules() {
        Values o = new Values();
        check(inspect("advanced-stash-finder", facts(0, 0, Map.of("minecraft:chest", 3), 3, 0), o).isEmpty(), "Below stash threshold");
        var hit = inspect("advanced-stash-finder", facts(0, 0, Map.of("minecraft:chest", 4), 4, 0), o);
        check(hit.size() == 1 && hit.getFirst().width() == 16, "Threshold gives one chunk marker");
        check(hit.getFirst().evidence().contains("chests=4"), "Marker reports actual selected counts");
        o.set("advanced-stash-finder", "chests", false);
        check(inspect("advanced-stash-finder", facts(0, 0, Map.of("minecraft:chest", 4), 4, 0), o).isEmpty(), "Storage filter changes qualification");
        check(inspect("advanced-stash-finder", facts(0, 0, Map.of("minecraft:chest", 4), 4, 1), o).size() == 1, "Any spawner independently qualifies");
        o.set("advanced-stash-finder", "critical-spawners", false);
        check(inspect("advanced-stash-finder", facts(0, 0, Map.of("minecraft:stone", 300), 0, 1), o).isEmpty(), "One spawner does not reach count threshold without override");
        o.set("advanced-stash-finder", "min-storage", 1d);
        check(inspect("advanced-stash-finder", facts(0, 0, Map.of(), 0, 1), o).size() == 1, "Spawner included in selected total");
        o.set("advanced-stash-finder", "spawners", false);
        check(inspect("advanced-stash-finder", facts(0, 0, Map.of(), 0, 1), o).isEmpty(), "Spawner total filter respected");
        o = new Values();
        o.set("advanced-stash-finder", "min-origin-distance", 100d);
        check(inspect("advanced-stash-finder", facts(0, 0, Map.of("minecraft:barrel", 4), 4, 0), o).isEmpty(), "Origin exclusion uses world blocks");
        check(inspect("advanced-stash-finder", facts(-10, -10, Map.of("minecraft:barrel", 4), 4, 0), o).size() == 1, "Negative chunks have positive origin distance");
        Map<String, Integer> mixed = Map.of("minecraft:trapped_chest", 1, "minecraft:purple_shulker_box", 2,
                "minecraft:blast_furnace", 3, "minecraft:decorated_pot", 4, "minecraft:stone", 1000);
        check(ChunkFinderRules.selectedStorage(facts(0, 0, mixed, 10, 0), new Values()) == 10, "Exact type grouping excludes unrelated blocks");
        check(ChunkFinderRules.storageGroup("minecraft:chest_boat") == null, "Block counters do not imply an entity container");
        check(ChunkFinderRules.storageGroup("other:chest") == null, "Unrecognized namespace not guessed");
        check(inspect("unknown", facts(0, 0, mixed, 10, 0), o).isEmpty(), "Unknown module produces no marker");
    }

    private static void chunkRules() {
        Values o = new Values();
        check(inspect("chunk-finder", facts(0, 0, Map.of("minecraft:stone", 30000), 0, 0), o).isEmpty(), "Ordinary solid terrain is not a candidate");
        var markers = inspect("chunk-finder", facts(0, 0, Map.of("minecraft:chest", 2), 2, 0), o);
        check(markers.size() == 1 && markers.getFirst().evidence().contains("Candidate pattern"), "Storage evidence is honestly a candidate");
        check(markers.getFirst().evidence().contains("storage=2") && !markers.getFirst().evidence().contains("%"), "Actual reasons without fake probability");
        o.set("chunk-finder", "storage", false);
        check(inspect("chunk-finder", facts(0, 0, Map.of("minecraft:chest", 2), 2, 0), o).isEmpty(), "Disabled evidence does not contribute");
        check(inspect("chunk-finder", facts(0, 0, Map.of("minecraft:observer", 2), 0, 0), o).size() == 1, "Actual redstone component evidence");
        check(inspect("chunk-finder", facts(0, 0, Map.of("minecraft:oak_planks", 8), 0, 0), o).size() == 1, "Crafted block concentration");
        check(inspect("chunk-finder", facts(0, 0, Map.of("analyzer:rotated_deepslate", 4), 0, 0), o).size() == 1, "Received unusual state evidence");
        check(inspect("chunk-finder", facts(0, 0, Map.of("minecraft:observer", 8, "minecraft:tuff", 500,
                "minecraft:copper_ore", 70, "minecraft:deepslate_copper_ore", 80), 0, 0), o).size() == 1,
                "Natural tuff and copper ores never suppress real building evidence");
        check(inspect("chunk-finder", facts(0, 0, Map.of("minecraft:chest", 2, "minecraft:barrel", 1,
                "minecraft:shulker_box", 1, "minecraft:furnace", 1, "minecraft:hopper", 1,
                "minecraft:tuff", 1000, "minecraft:copper_ore", 150), 6, 0), new Values()).size() == 1,
                "Native storage fixture remains a candidate in ordinary ore-bearing terrain");
        Map<String, Integer> chamber = Map.of("minecraft:observer", 8, "minecraft:tuff_bricks", 30,
                "minecraft:waxed_oxidized_copper_grate", 20, "minecraft:trial_spawner", 1);
        check(inspect("chunk-finder", facts(0, 0, chamber, 0, 1), o).isEmpty(), "Observed trial spawner and sufficient crafted chamber material suppress candidate");
        check(inspect("chunk-finder", facts(0, 0, Map.of("minecraft:observer", 8, "minecraft:waxed_exposed_copper", 50,
                "minecraft:trial_spawner", 1), 0, 1), o).isEmpty(), "Actual aged solid copper block IDs count as crafted chamber material");
        check(inspect("chunk-finder", facts(0, 0, Map.of("minecraft:observer", 8, "minecraft:tuff_bricks", 60), 0, 0), o).size() == 1,
                "Crafted chamber-like material without trial spawner does not suppress a player's build");
        check(inspect("chunk-finder", facts(0, 0, Map.of("minecraft:observer", 8, "minecraft:tuff", 500,
                "minecraft:copper_ore", 150, "minecraft:trial_spawner", 1), 0, 1), o).size() == 1,
                "A trial spawner beside natural tuff/ore alone does not satisfy crafted-material condition");
        check(inspect("chunk-finder", facts(0, 0, Map.of("minecraft:observer", 8, "minecraft:tuff_bricks", 49,
                "minecraft:trial_spawner", 1), 0, 1), o).size() == 1, "Below crafted-material threshold remains visible");
        o.set("chunk-finder", "ignore-trial-chambers", false);
        check(inspect("chunk-finder", facts(0, 0, chamber, 0, 1), o).size() == 1, "Chamber suppression can be disabled explicitly");
    }

    private static void kelpRules() {
        Values o = new Values();
        check(inspect("kelp-esp", kelp(10, 10, 6), o).size() == 1, "Exact sixty percent boundary qualifies");
        check(inspect("kelp-esp", kelp(10, 10, 5), o).isEmpty(), "Lower top ratio rejected");
        check(inspect("kelp-esp", kelp(9, 9, 9), o).isEmpty(), "Small sample rejected");
        check(inspect("kelp-esp", kelp(10, 10, 11), o).isEmpty(), "Impossible received count rejected");
        check(inspect("kelp-esp", kelp(10, 11, 6), o).isEmpty(), "Tall count cannot exceed total columns");
        o.set("kelp-esp", "top-y", 80d);
        var mark = inspect("kelp-esp", kelp(10, 10, 6), o).getFirst();
        check(mark.y() == 80 && mark.evidence().contains("natural growth"), "Configured target and heuristic warning preserved");
    }

    private static void evidenceMembership() {
        for (String id : List.of("chest", "trapped_chest", "barrel", "blue_shulker_box", "ender_chest", "furnace",
                "hopper", "crafter", "spawner", "trial_spawner", "crafting_table", "enchanting_table", "anvil",
                "piston", "sticky_piston", "repeater", "comparator", "observer", "oak_planks", "cobblestone",
                "oak_stairs", "red_concrete", "glass", "deepslate", "end_stone", "farmland", "beehive", "melon_stem",
                "pumpkin_stem", "tuff_bricks", "chiseled_tuff", "waxed_exposed_copper", "oxidized_copper_grate"))
            check(ChunkFinderRules.relevantEvidence("minecraft:" + id), "Evidence membership must invalidate changed " + id);
        for (String id : List.of("air", "water", "stone", "dirt", "tuff", "copper_ore", "deepslate_copper_ore",
                "wheat", "redstone_wire", "piston_head", "moving_piston", "kelp", "kelp_plant"))
            check(!ChunkFinderRules.relevantEvidence("minecraft:" + id), "Unrelated/transient blocks do not restart chunk evidence: " + id);
        check(!ChunkFinderRules.relevantEvidence(null), "Missing state is not invented evidence");
    }

    private static void journal() throws Exception {
        Path directory = Files.createTempDirectory("analyzer-stash-history-");
        try {
            Path file = directory.resolve("stashes.json");
            StashJournal journal = new StashJournal(file, SCOPE);
            check(journal.load().warning().isEmpty() && journal.entries().isEmpty(), "Missing history is a fresh history");
            ChunkFacts first = facts(-2, 5, Map.of("minecraft:barrel", 4, "minecraft:stone", 120), 4, 0);
            check(journal.observe("minecraft:overworld", first, 100), "First observation is new");
            check(!journal.observe("minecraft:overworld", first, 200), "Unchanged counts do not notify again");
            check(journal.entries().getFirst().firstSeen() == 100 && journal.entries().getFirst().lastSeen() == 200, "First/last seen preserved separately");
            check(journal.entries().getFirst().counts().equals(Map.of("minecraft:barrel", 4)), "Journal excludes terrain and all private contents");
            check(journal.dirty(), "Repeated observation updates timestamp dirty state");
            journal.save();
            check(!journal.dirty(), "Atomic save clears dirty flag");
            StashJournal loaded = new StashJournal(file, SCOPE);
            check(loaded.load().warning().isEmpty() && loaded.entries().equals(journal.entries()), "Exact round trip");
            check(loaded.observe("minecraft:overworld", facts(-2, 5, Map.of("minecraft:barrel", 5), 5, 0), 300), "Changed counts produce a new observation event");
            check(loaded.entries().getFirst().firstSeen() == 100, "Updating counts retains first seen");
            check(loaded.observe("minecraft:the_nether", first, 400), "Different dimensions keep distinct keys");
            check(loaded.entries().size() == 2, "No cross-dimension replacement");
            loaded.observe("minecraft:overworld", facts(8, 9, Map.of("minecraft:trial_spawner", 2), 0, 2), 450);
            check(loaded.entries().getFirst().counts().equals(Map.of("minecraft:trial_spawner", 2)), "Trial spawner identity retained without a second invented normal spawner");
            loaded.observe("minecraft:overworld", facts(8, 10, Map.of("minecraft:spawner", 1, "minecraft:trial_spawner", 2), 0, 3), 460);
            check(loaded.entries().getFirst().total() == 3 && loaded.entries().getFirst().counts().size() == 2, "Normal and trial spawner counts retain exact individual IDs");
            byte[] original = Files.readAllBytes(file);
            StashJournal other = new StashJournal(file, "b".repeat(64));
            check(!other.load().warning().isEmpty() && other.entries().isEmpty(), "Wrong scope cannot leak another world's records");
            throwsIo(other::save, "Wrong scope file cannot be overwritten implicitly");
            check(java.util.Arrays.equals(original, Files.readAllBytes(file)), "Failed load/save preserve file bytes");
            Files.writeString(file, "{broken", StandardCharsets.UTF_8);
            check(!loaded.load().warning().isEmpty(), "Corrupt history has readable warning");
            throwsIo(loaded::save, "Corrupt file is preserved until explicit clear");
            check(Files.readString(file).equals("{broken"), "Malformed bytes preserved");
            loaded.clear();
            loaded.save();
            check(new StashJournal(file, SCOPE).load().entries().isEmpty(), "Explicit clear replaces invalid history with valid empty history");
            for (int i = 0; i < StashJournal.MAX_ENTRIES + 1; i++) loaded.observe("minecraft:overworld", facts(i, 0, Map.of("minecraft:chest", 4), 4, 0), i);
            check(loaded.entries().size() == StashJournal.MAX_ENTRIES, "History cap enforced");
            check(loaded.entries().stream().noneMatch(e -> e.chunkX() == 0), "Oldest record evicted deterministically");
            loaded.save();
            check(new StashJournal(file, SCOPE).load().entries().size() == StashJournal.MAX_ENTRIES, "Capped history round trips");
            try { new StashJournal(file, "server.example"); throw new AssertionError("Raw address accepted"); }
            catch (IllegalArgumentException expected) { assertions++; }
            try { loaded.observe("minecraft:overworld", first, -1); throw new AssertionError("Negative timestamp accepted"); }
            catch (IllegalArgumentException expected) { assertions++; }
            try (var files = Files.list(directory)) { check(files.noneMatch(p -> p.getFileName().toString().endsWith(".tmp")), "No temporary file remains after saves"); }
        } finally {
            try (var files = Files.walk(directory)) {
                for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(file);
            }
        }
    }

    private static List<FinderData.Mark> inspect(String module, ChunkFacts facts, Options options) { return ChunkFinderRules.inspect(module, facts, options); }
    private static ChunkFacts facts(int x, int z, Map<String, Integer> counts, int storage, int spawners) {
        return new ChunkFacts(x, z, -64, 320, counts, storage, spawners, 0, 0, 0, 0, 0, 0, List.of());
    }
    private static ChunkFacts kelp(int all, int tall, int matching) {
        return new ChunkFacts(0, 0, -64, 320, Map.of(), 0, 0, 0, 0, 0, all, tall, matching, List.of());
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); assertions++; }
    private static void throwsIo(Io action, String message) throws Exception {
        try { action.run(); throw new AssertionError(message); } catch (IOException expected) { assertions++; }
    }
    @FunctionalInterface private interface Io { void run() throws Exception; }

    private static final class Values implements Options {
        private final Map<String, Object> settings = new HashMap<>();
        Values() { for (var module : SurveyFinderModules.definitions()) for (var setting : module.settings()) settings.put(module.id() + "/" + setting.id(), setting.defaultValue()); }
        void set(String module, String setting, Object value) { settings.put(module + "/" + setting, value); }
        public boolean bool(String module, String setting) { return Boolean.TRUE.equals(settings.get(module + "/" + setting)); }
        public double number(String module, String setting) { Object value = settings.get(module + "/" + setting); return value instanceof Number n ? n.doubleValue() : Double.NaN; }
        public String choice(String module, String setting) { return String.valueOf(settings.get(module + "/" + setting)); }
    }
}
