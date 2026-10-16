package dev.analyzer.finder;

import dev.analyzer.finder.FinderData.ChunkFacts;
import dev.analyzer.finder.FinderData.Mark;
import dev.analyzer.finder.FinderData.Options;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Pure aggregation over received observations. Scores describe evidence, never probability. */
public final class ChunkFinderRules {
    private ChunkFinderRules() { }

    public static boolean enabledChunkScan(String module) {
        return "advanced-stash-finder".equals(module) || "chunk-finder".equals(module) || "kelp-esp".equals(module);
    }

    /** Conservative membership for received block-ID changes that can alter this survey's evidence.
     * Same-ID property changes are classified separately; transient piston heads are not evidence. */
    public static boolean relevantEvidence(String registryId) {
        if (registryId == null || registryId.equals("minecraft:piston_head") || registryId.equals("minecraft:moving_piston")) return false;
        return storageGroup(registryId) != null || registryId.equals("minecraft:spawner") || registryId.equals("minecraft:trial_spawner")
                || workstation(registryId) || redstone(registryId) || crafted(registryId) || farm(registryId)
                || registryId.equals("minecraft:deepslate") || registryId.equals("minecraft:end_stone") || chamberMaterial(registryId);
    }

    public static List<Mark> inspect(String module, ChunkFacts facts, Options options) {
        if (facts == null || options == null || facts.counts() == null || facts.topY() <= facts.bottomY()) return List.of();
        return switch (module) {
            case "advanced-stash-finder" -> stash(facts, options);
            case "chunk-finder" -> chunk(facts, options);
            case "kelp-esp" -> kelp(facts, options);
            default -> List.of();
        };
    }

    public static int selectedStorage(ChunkFacts facts, Options options) {
        int total = 0;
        for (Map.Entry<String, Integer> entry : facts.counts().entrySet()) {
            String group = storageGroup(entry.getKey());
            if (group != null && options.bool("advanced-stash-finder", group)) total = add(total, positive(entry.getValue()));
        }
        if (options.bool("advanced-stash-finder", "spawners")) total = add(total, positive(facts.spawners()));
        return total;
    }

    public static String storageGroup(String id) {
        if (id == null || !id.startsWith("minecraft:")) return null;
        String path = id.substring(10);
        if (path.equals("chest") || path.equals("trapped_chest")) return "chests";
        if (path.equals("barrel")) return "barrels";
        if (path.equals("shulker_box") || path.endsWith("_shulker_box")) return "shulkers";
        if (path.equals("ender_chest")) return "ender-chests";
        if (path.equals("furnace") || path.equals("smoker") || path.equals("blast_furnace")) return "furnaces";
        if (path.equals("dispenser") || path.equals("dropper")) return "dispensers";
        if (path.equals("hopper")) return "hoppers";
        if (path.equals("crafter") || path.equals("brewing_stand") || path.equals("decorated_pot")
                || path.equals("chiseled_bookshelf")) return "other-storage";
        return null;
    }

    private static List<Mark> stash(ChunkFacts f, Options o) {
        String module = "advanced-stash-finder";
        double distance = Math.hypot(f.x() * 16.0 + 8, f.z() * 16.0 + 8);
        if (distance < finite(o.number(module, "min-origin-distance"), 0)) return List.of();
        int selected = selectedStorage(f, o);
        boolean critical = o.bool(module, "critical-spawners") && f.spawners() > 0;
        if (!critical && selected < threshold(o, module, "min-storage", 4)) return List.of();
        StringBuilder detail = new StringBuilder("Observed ").append(selected).append(" selected storage/spawner blocks");
        List<String> groups = new ArrayList<>();
        for (String group : List.of("chests", "barrels", "shulkers", "ender-chests", "furnaces", "dispensers", "hoppers", "other-storage")) {
            if (!o.bool(module, group)) continue;
            int count = 0;
            for (var entry : f.counts().entrySet()) if (group.equals(storageGroup(entry.getKey()))) count = add(count, positive(entry.getValue()));
            if (count > 0) groups.add(group + "=" + count);
        }
        if (f.spawners() > 0) groups.add("spawners=" + f.spawners());
        if (!groups.isEmpty()) detail.append(" (").append(String.join(", ", groups)).append(')');
        if (critical) detail.append("; spawner override");
        return List.of(chunkMark(module, f, detail.toString()));
    }

    private static List<Mark> chunk(ChunkFacts f, Options o) {
        String module = "chunk-finder";
        int chamber = sum(f, ChunkFinderRules::chamberMaterial);
        // Tuff and copper ore are ordinary geology. Suppression requires both crafted chamber
        // material and an actually received trial spawner, rather than a terrain-name substring.
        if (o.bool(module, "ignore-trial-chambers") && count(f, "minecraft:trial_spawner") > 0
                && chamber >= threshold(o, module, "trial-chamber-threshold", 50)) return List.of();
        int score = 0;
        List<String> evidence = new ArrayList<>();
        if (o.bool(module, "storage") && f.storage() >= 2) {
            score += Math.min(16, f.storage() * 2);
            evidence.add("storage=" + f.storage());
        }
        if (o.bool(module, "workstations")) {
            int work = sum(f, ChunkFinderRules::workstation);
            if (work > 0) { score += Math.min(12, work * 2); evidence.add("workstations=" + work); }
        }
        if (o.bool(module, "redstone")) {
            int redstone = sum(f, ChunkFinderRules::redstone);
            if (redstone > 0) { score += Math.min(16, redstone * 2); evidence.add("redstone=" + redstone); }
        }
        if (o.bool(module, "building-blocks")) {
            int crafted = sum(f, ChunkFinderRules::crafted);
            if (crafted >= 4) { score += Math.min(12, crafted / 2); evidence.add("crafted blocks=" + crafted); }
        }
        if (o.bool(module, "unusual-blocks")) {
            int unusual = count(f, "analyzer:unusual_deepslate") + count(f, "analyzer:rotated_deepslate") + count(f, "analyzer:end_stone_outside_end");
            if (unusual > 0) { score += Math.min(12, unusual); evidence.add("unusual block states=" + unusual); }
        }
        if (o.bool(module, "farm-patterns")) {
            int farm = sum(f, ChunkFinderRules::farm);
            if (farm >= 4) { score += Math.min(12, farm / 2); evidence.add("crop/hive blocks=" + farm); }
        }
        if (score < threshold(o, module, "min-evidence", 4)) return List.of();
        return List.of(chunkMark(module, f, "Candidate pattern; evidence score " + score + ": " + String.join(", ", evidence)));
    }

    private static List<Mark> kelp(ChunkFacts f, Options o) {
        String module = "kelp-esp";
        if (f.kelpColumns() > 256 || f.tallKelpColumns() > f.kelpColumns()
                || f.kelpColumns() < threshold(o, module, "min-columns", 10)
                || f.tallKelpColumns() < threshold(o, module, "min-tall-columns", 10)
                || f.tallKelpColumns() <= 0 || f.seaLevelKelpColumns() < 0
                || f.seaLevelKelpColumns() > f.tallKelpColumns()) return List.of();
        double ratio = f.seaLevelKelpColumns() * 100.0 / f.tallKelpColumns();
        double required = Math.clamp(finite(o.number(module, "sea-level-ratio"), 60), 0, 100);
        if (ratio < required) return List.of();
        int top = (int) Math.clamp(finite(o.number(module, "top-y"), 62), f.bottomY(), f.topY() - 1);
        return List.of(new Mark(module, f.x() * 16, top, f.z() * 16, 16, .05, 16,
                "Kelp pattern: " + f.seaLevelKelpColumns() + "/" + f.tallKelpColumns()
                        + " tall tops match Y=" + top + "; natural growth can match", 0));
    }

    private static Mark chunkMark(String module, ChunkFacts f, String evidence) {
        return new Mark(module, f.x() * 16, f.bottomY(), f.z() * 16, 16,
                f.topY() - f.bottomY(), 16, evidence, 0);
    }

    private static int sum(ChunkFacts f, java.util.function.Predicate<String> filter) {
        int sum = 0;
        for (var entry : f.counts().entrySet()) if (entry.getKey() != null && filter.test(entry.getKey())) sum = add(sum, positive(entry.getValue()));
        return sum;
    }

    private static boolean workstation(String id) {
        return id.equals("minecraft:crafting_table") || id.equals("minecraft:enchanting_table")
                || id.equals("minecraft:anvil") || id.equals("minecraft:chipped_anvil") || id.equals("minecraft:damaged_anvil")
                || id.equals("minecraft:smithing_table") || id.equals("minecraft:cartography_table")
                || id.equals("minecraft:fletching_table") || id.equals("minecraft:loom") || id.equals("minecraft:stonecutter")
                || id.equals("minecraft:grindstone") || id.equals("minecraft:brewing_stand");
    }

    private static boolean redstone(String id) {
        return id.equals("minecraft:piston") || id.equals("minecraft:sticky_piston")
                || id.equals("minecraft:repeater") || id.equals("minecraft:comparator") || id.equals("minecraft:observer");
    }

    private static boolean farm(String id) {
        return id.equals("minecraft:farmland") || id.equals("minecraft:beehive")
                || id.equals("minecraft:melon_stem") || id.equals("minecraft:pumpkin_stem");
    }

    private static boolean crafted(String id) {
        return id.equals("minecraft:cobblestone") || id.equals("minecraft:cobbled_deepslate")
                || id.equals("minecraft:polished_deepslate") || id.endsWith("_planks")
                || id.endsWith("_stairs") || id.endsWith("_concrete") || id.endsWith("_glass")
                || id.equals("minecraft:glass") || id.equals("minecraft:deepslate_bricks")
                || id.equals("minecraft:deepslate_tiles");
    }

    private static boolean chamberMaterial(String id) {
        if (id == null || !id.startsWith("minecraft:")) return false;
        String name = id.substring(10);
        if (switch (name) {
            case "tuff_bricks", "chiseled_tuff_bricks", "chiseled_tuff", "polished_tuff",
                    "tuff_brick_stairs", "tuff_brick_slab", "tuff_brick_wall",
                    "polished_tuff_stairs", "polished_tuff_slab", "polished_tuff_wall",
                    "tuff_stairs", "tuff_slab", "tuff_wall" -> true;
            default -> false;
        }) return true;
        if (name.startsWith("waxed_")) name = name.substring(6);
        boolean aged = false;
        for (String age : List.of("exposed_", "weathered_", "oxidized_"))
            if (name.startsWith(age)) { name = name.substring(age.length()); aged = true; break; }
        if (name.equals("copper")) return aged;
        return switch (name) {
            case "cut_copper", "cut_copper_stairs", "cut_copper_slab", "chiseled_copper",
                    "copper_grate", "copper_bulb", "copper_door", "copper_trapdoor", "copper_block" -> true;
            default -> false;
        };
    }

    private static int count(ChunkFacts f, String key) { return positive(f.counts().get(key)); }
    private static int positive(Integer value) { return value == null ? 0 : Math.clamp(value, 0, 1000000); }
    private static int add(int a, int b) { return (int) Math.min(1000000L, (long) a + b); }
    private static double finite(double value, double fallback) { return Double.isFinite(value) ? value : fallback; }
    private static int threshold(Options o, String module, String key, int fallback) {
        return (int) Math.clamp(finite(o.number(module, key), fallback), 1, 1000000);
    }
}
