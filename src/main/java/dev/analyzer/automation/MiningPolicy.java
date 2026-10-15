package dev.analyzer.automation;

import java.util.Set;

/** Conservative materials which a resource approach may remove; manufactured structures are excluded. */
public final class MiningPolicy {
    private static final Set<String> ROCK_AND_EARTH = Set.of("stone", "deepslate", "netherrack", "dirt", "grass_block",
            "coarse_dirt", "rooted_dirt", "podzol", "mycelium", "granite", "diorite", "andesite", "tuff", "ancient_debris");
    private static final Set<String> SOFT = Set.of("short_grass", "tall_grass", "fern", "large_fern", "dead_bush", "vine",
            "weeping_vines", "weeping_vines_plant", "twisting_vines", "twisting_vines_plant", "hanging_roots", "snow");
    private MiningPolicy() { }
    public static boolean naturalExcavation(String id) { return id != null && (ROCK_AND_EARTH.contains(id) || id.endsWith("_ore")); }
    public static boolean softObstruction(String id) { return id != null && SOFT.contains(id); }
    public static boolean treeObstruction(String id) { return id != null && (id.endsWith("_leaves") || softObstruction(id)); }
}
