package dev.analyzer.automation;

import java.util.List;

public final class MiningPolicySelfTest {
    private static int checks;
    public static void main(String[] args) {
        for (String id : List.of("stone", "dirt", "grass_block", "deepslate", "netherrack", "diamond_ore", "deepslate_iron_ore", "tuff", "ancient_debris"))
            check(MiningPolicy.naturalExcavation(id), "natural mining approach permits " + id);
        for (String id : List.of("short_grass", "tall_grass", "fern", "large_fern", "vine", "twisting_vines_plant", "snow"))
            check(MiningPolicy.softObstruction(id), "harmless outline obstruction permits " + id);
        for (String id : List.of("cobblestone", "stone_bricks", "oak_planks", "glass", "chest", "crafting_table", "furnace", "obsidian", "sand", "gravel", "water", "lava", "bedrock")) {
            check(!MiningPolicy.naturalExcavation(id), "resource approach never authorizes " + id);
            check(!MiningPolicy.softObstruction(id), "obstruction clearing never authorizes " + id);
        }
        check(MiningPolicy.treeObstruction("oak_leaves") && !MiningPolicy.softObstruction("oak_leaves"), "leaf clearing remains a tree-specific permission");
        check(!MiningPolicy.naturalExcavation(null) && !MiningPolicy.treeObstruction(null), "missing state grants no excavation permission");
        var survey = new MiningSurveyPolicy(24, 20);
        check(survey.size() == 98441 && survey.offset(0).distanceSquared() == 0, "The bounded survey starts at the player's actual cell");
        java.util.Set<String> seen = new java.util.HashSet<>();
        boolean ordered = true, bounded = true; int previous = -1, immediate = 0;
        for (int i = 0; i < survey.size(); i++) {
            var cell = survey.offset(i);
            ordered &= cell.distanceSquared() >= previous; previous = cell.distanceSquared();
            bounded &= Math.abs(cell.x()) <= 24 && Math.abs(cell.z()) <= 24 && Math.abs(cell.y()) <= 20;
            seen.add(cell.x() + "," + cell.y() + "," + cell.z());
            if (i < 7 && cell.distanceSquared() <= 1) immediate++;
        }
        check(ordered && bounded && seen.size() == survey.size(), "Nearest-first observations cover the bounded world window exactly once");
        check(immediate == 7, "All six adjacent block faces are observed before a distant corner");
        check(survey.offset(63).distanceSquared() <= 8, "A dense nearby resource cache is available within 64 observations rather than 98,441");
        check(survey.offset(survey.size()-1).distanceSquared() == 1552, "Distant boundary observations remain available when the local source is absent");
        boolean rejected = false;
        try {new MiningSurveyPolicy(1000, 20);} catch (IllegalArgumentException expected) {rejected = true;}
        check(rejected, "Survey construction cannot allocate an unbounded task window");
        System.out.println("Mining policy passed " + checks + " assertions");
    }
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
}
