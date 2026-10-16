package dev.analyzer.finder;

import java.util.List;
import java.util.Map;

/** Repeated animation/growth packets must not restart stable aggregate counts. */
public final class FinderUpdateSelfTest {
    private static int assertions;

    public static void main(String[] args) {
        propertyChanges();
        changedEvidence();
        kelpAndFastPaths();
        axisEvidence();
        blockMarkerChanges();
        continuousPower();
        System.out.println("Analyzer finder update checks passed: " + assertions + " assertions");
    }

    private static void propertyChanges() {
        sameType("observer", "powered", "false", "true");
        sameType("repeater", "powered", "false", "true");
        sameType("comparator", "powered", "false", "true");
        sameType("piston", "extended", "false", "true");
        sameType("sticky_piston", "extended", "false", "true");
        sameType("redstone_wire", "power", "0", "15");
        sameType("beehive", "honey_level", "0", "5");
        sameType("bee_nest", "honey_level", "0", "5");
        sameType("sweet_berry_bush", "age", "0", "3");
        sameType("wheat", "age", "0", "7");
        sameType("melon_stem", "age", "0", "7");
        sameType("farmland", "moisture", "0", "7");
        sameType("kelp", "age", "0", "25");
        sameType("chest", "facing", "north", "east");
        sameType("chest", "waterlogged", "false", "true");
        sameType("hopper", "enabled", "true", "false");
        sameType("trial_spawner", "trial_spawner_state", "inactive", "active");
        sameType("white_banner", "rotation", "0", "8");
        // Source-only Light ESP must still refresh its block marker through ordinary dirty-state
        // handling, but emitted-light properties do not affect chunk/storage/kelp aggregates.
        sameType("redstone_lamp", "lit", "false", "true");
        sameType("waxed_copper_bulb", "lit", "false", "true");
        sameType("redstone_torch", "lit", "false", "true");
        sameType("sea_pickle", "pickles", "1", "4");
    }

    private static void changedEvidence() {
        for (String block : List.of("oak_planks", "cobblestone", "deepslate_bricks", "crafting_table", "enchanting_table",
                "piston", "sticky_piston", "observer", "repeater", "farmland", "beehive", "melon_stem",
                "deepslate", "end_stone", "tuff_bricks", "waxed_exposed_copper")) {
            transition("minecraft:air", "minecraft:" + block, true, false, false, "Placing " + block + " changes real evidence");
            transition("minecraft:" + block, "minecraft:air", true, false, false, "Removing " + block + " changes real evidence");
        }
        for (String block : List.of("stone", "dirt", "tuff", "copper_ore", "deepslate_copper_ore", "wheat", "redstone_wire",
                "piston_head", "moving_piston", "redstone_lamp", "redstone_torch", "glowstone")) {
            transition("minecraft:air", "minecraft:" + block, false, false, false, "Uncounted/transient " + block + " leaves aggregates stable");
            transition("minecraft:" + block, "minecraft:air", false, false, false, "Removing uncounted/transient " + block + " leaves aggregates stable");
        }
        transition("minecraft:observer", "minecraft:repeater", true, false, false, "Changing an evidence ID refreshes its exact counts");
    }

    private static void kelpAndFastPaths() {
        for (String block : List.of("kelp", "kelp_plant")) {
            transition("minecraft:water", "minecraft:" + block, false, true, false, "Growing kelp changes measured columns");
            transition("minecraft:" + block, "minecraft:water", false, true, false, "Removing kelp changes measured columns");
        }
        transition("minecraft:kelp_plant", "minecraft:kelp", false, true, false, "Plant-to-tip change can alter top height");
        for (String block : List.of("chest", "trapped_chest", "barrel", "blue_shulker_box", "hopper", "furnace", "crafter",
                "spawner", "trial_spawner")) {
            transition("minecraft:air", "minecraft:" + block, true, false, true, "Placing received " + block + " changes exact storage count");
            transition("minecraft:" + block, "minecraft:air", true, false, true, "Removing received " + block + " changes exact storage count");
        }
        transition("minecraft:chest", "minecraft:barrel", true, false, true, "Container type replacement refreshes individual type totals");
        transition("minecraft:spawner", "minecraft:trial_spawner", true, false, true, "Spawner kind replacement preserves exact identities");
        for (String banner : List.of("red_banner", "red_wall_banner")) {
            transition("minecraft:air", "minecraft:" + banner, false, false, true, "Placed banner refreshes fast collectible observations");
            transition("minecraft:" + banner, "minecraft:air", false, false, true, "Removed banner refreshes fast collectible observations");
        }
    }

    private static void axisEvidence() {
        for (String horizontal : List.of("x", "z")) {
            var before = new State("minecraft:deepslate", Map.of("axis", "y"));
            var after = new State("minecraft:deepslate", Map.of("axis", horizontal));
            check(classify(before, after).equals(new FinderUpdatePolicy.Change(true, false, false)), "Vertical to horizontal raw deepslate changes rotated evidence");
            check(classify(after, before).equals(new FinderUpdatePolicy.Change(true, false, false)), "Horizontal to vertical raw deepslate changes rotated evidence");
        }
        sameType("deepslate", "axis", "x", "z");
        sameType("deepslate", "axis", "y", "y");
        sameType("oak_log", "axis", "y", "x");
        check(FinderUpdatePolicy.classify(null, null, null, null).equals(new FinderUpdatePolicy.Change(false, false, false)), "No state is not invented evidence");
    }

    private static void continuousPower() {
        int fullRestarts = 0, kelpRestarts = 0, fastRestarts = 0;
        int blockInvalidations = 0;
        State before = new State("minecraft:observer", Map.of("powered", "false"));
        for (int tick = 0; tick < 200; tick++) {
            State after = new State("minecraft:observer", Map.of("powered", tick % 2 == 0 ? "true" : "false"));
            var change = classify(before, after);
            if (change.chunkEvidence()) fullRestarts++;
            if (change.kelpColumns()) kelpRestarts++;
            if (change.fastSurvey()) fastRestarts++;
            if (FinderUpdatePolicy.blockMarkersChanged(cell(before), cell(after))) blockInvalidations++;
            before = after;
        }
        check(fullRestarts == 0 && kelpRestarts == 0 && fastRestarts == 0,
                "Two hundred observer power changes must leave in-progress counts able to complete");
        check(blockInvalidations == 0, "Two hundred actual observer power flips do not hide nearby block markers");
        var removal = FinderUpdatePolicy.classify(before.id(), "minecraft:air", "", "");
        check(removal.chunkEvidence() && !removal.kelpColumns() && !removal.fastSurvey(),
                "Removing that observer still invalidates its actual block evidence");
    }

    private static void blockMarkerChanges() {
        for (String block : List.of("observer", "repeater", "comparator", "redstone_wire"))
            markerProperty(block, "powered", "false", "true", false);
        markerProperty("wheat", "age", "0", "7", false);
        markerProperty("melon_stem", "age", "0", "7", false);
        markerProperty("kelp", "age", "0", "25", false);
        markerProperty("beehive", "honey_level", "0", "5", true);
        markerProperty("bee_nest", "honey_level", "0", "5", true);
        markerProperty("sweet_berry_bush", "age", "0", "3", true);
        markerProperty("deepslate", "axis", "x", "z", true);
        markerProperty("deepslate", "axis", "y", "x", true);
        markerProperty("pointed_dripstone", "vertical_direction", "up", "down", true);
        markerProperty("piston", "extended", "false", "true", true);
        markerProperty("sticky_piston", "extended", "true", "false", true);
        markerProperty("piston_head", "facing", "north", "east", true);
        FinderData.Cell before = cell(new State("minecraft:redstone_lamp", Map.of("lit", "false")));
        FinderData.Cell lit = new FinderData.Cell(before.id(), true, false, true, false, 0, 15, Map.of("lit", "true"));
        check(FinderUpdatePolicy.blockMarkersChanged(before, lit), "Actual emitted-light change refreshes Light ESP even with an unchanged block ID");
        check(!FinderUpdatePolicy.classify(before.id(), lit.id(), "", "").chunkEvidence(), "Emitting-light update still preserves unrelated chunk count progress");
        check(FinderUpdatePolicy.blockMarkersChanged(before, new FinderData.Cell(before.id(), true, false, false, false, 0, 0, before.properties())), "Full-cube support geometry change invalidates neighboring passage markers");
        check(FinderUpdatePolicy.blockMarkersChanged(before, new FinderData.Cell(before.id(), true, false, true, true, 0, 0, before.properties())), "Fluid geometry change invalidates dry passage markers");
        check(FinderUpdatePolicy.blockMarkersChanged(before, FinderData.Cell.UNKNOWN), "Losing received state invalidates known-data marker");
        check(FinderUpdatePolicy.blockMarkersChanged(null, before), "Missing previous state requires conservative invalidation");
        check(FinderUpdatePolicy.blockMarkersChanged(before, null), "Missing replacement state requires conservative invalidation");
        check(!FinderUpdatePolicy.blockMarkersChanged(before, before), "Pure policy treats equal observations as unchanged; runtime handles prediction acknowledgements explicitly");
    }

    private static void markerProperty(String block, String property, String oldValue, String newValue, boolean expected) {
        FinderData.Cell before = cell(new State("minecraft:" + block, Map.of(property, oldValue)));
        FinderData.Cell after = cell(new State("minecraft:" + block, Map.of(property, newValue)));
        check(FinderUpdatePolicy.blockMarkersChanged(before, after) == expected,
                "Block marker classification for " + block + " " + property);
        check(FinderUpdatePolicy.blockMarkersChanged(after, before) == expected,
                "Reverse block marker classification for " + block + " " + property);
    }

    private static FinderData.Cell cell(State state) { return new FinderData.Cell(state.id(), true, false, true, false, 0, 0, state.properties()); }

    private static void sameType(String block, String property, String oldValue, String newValue) {
        State before = new State("minecraft:" + block, Map.of(property, oldValue));
        State after = new State("minecraft:" + block, Map.of(property, newValue));
        check(classify(before, after).equals(new FinderUpdatePolicy.Change(false, false, false)),
                "Same-ID " + block + " " + property + " change preserves aggregate counts");
    }

    private static void transition(String before, String after, boolean chunk, boolean kelp, boolean fast, String message) {
        check(FinderUpdatePolicy.classify(before, after, "", "").equals(new FinderUpdatePolicy.Change(chunk, kelp, fast)), message);
    }

    private static FinderUpdatePolicy.Change classify(State before, State after) {
        return FinderUpdatePolicy.classify(before.id(), after.id(), before.properties().get("axis"), after.properties().get("axis"));
    }
    private record State(String id, Map<String, String> properties) { }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); assertions++; }
}
