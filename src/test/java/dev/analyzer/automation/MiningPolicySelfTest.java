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
        System.out.println("Mining policy passed " + checks + " assertions");
    }
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
}
