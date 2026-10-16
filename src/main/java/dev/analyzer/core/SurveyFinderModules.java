package dev.analyzer.core;

import java.util.List;

/** Original, opt-in loaded-chunk surveys. A candidate pattern is never a confirmed base. */
public final class SurveyFinderModules {
    private SurveyFinderModules() { }

    public static List<ModuleDefinition> definitions() {
        return List.of(
                FinderSettings.module("advanced-stash-finder", "Advanced Stash Finder",
                        "Records received storage clusters by chunk, with actual counts and local history.", "Gold",
                                Setting.number("min-storage", "Minimum storage blocks", 4, 1, 128, 1),
                                Setting.number("min-origin-distance", "Minimum distance from world origin", 0, 0, 30000000, 100),
                                Setting.bool("critical-spawners", "Any spawner qualifies", true),
                                Setting.bool("chests", "Chests and trapped chests", true),
                                Setting.bool("barrels", "Barrels", true),
                                Setting.bool("shulkers", "Shulker boxes", true),
                                Setting.bool("ender-chests", "Ender chests", true),
                                Setting.bool("furnaces", "Furnaces, smokers and blast furnaces", true),
                                Setting.bool("dispensers", "Dispensers and droppers", true),
                                Setting.bool("hoppers", "Hoppers", true),
                                Setting.bool("other-storage", "Other storage containers", true),
                                Setting.bool("spawners", "Include spawners in total", true),
                                Setting.bool("remember-stashes", "Keep local stash history", true)),
                FinderSettings.module("chunk-finder", "Chunk Finder",
                        "Shows explainable building and farm evidence in received chunks; candidates can be natural.", "Orange",
                                Setting.number("min-evidence", "Minimum evidence score", 4, 1, 128, 1),
                                Setting.bool("storage", "Storage clusters", true),
                                Setting.bool("workstations", "Workstations", true),
                                Setting.bool("redstone", "Pistons and redstone components", true),
                                Setting.bool("building-blocks", "Crafted building blocks", true),
                                Setting.bool("unusual-blocks", "Unusual deepslate and end stone", true),
                                Setting.bool("farm-patterns", "Grouped crops and hives", true),
                                Setting.bool("ignore-trial-chambers", "Suppress tuff/copper chamber clusters", true),
                                Setting.number("trial-chamber-threshold", "Chamber block threshold", 50, 8, 256, 1)),
                FinderSettings.module("kelp-esp", "Kelp ESP",
                        "Marks tall kelp distributions near a selected top height; this is a pattern, not proof of a farm.", "Mint",
                                Setting.number("min-columns", "Minimum kelp columns", 10, 1, 256, 1),
                                Setting.number("min-tall-columns", "Minimum tall columns", 10, 1, 256, 1),
                                Setting.number("min-length", "Minimum column length", 8, 1, 32, 1),
                                Setting.number("top-y", "Matching top height", 62, -64, 320, 1),
                                Setting.number("sea-level-ratio", "Matching tall tops (%)", 60, 0, 100, 5)),
                FinderSettings.module("region-map", "Region Map",
                        "Original coordinate grid of your position and locally recorded stashes; no assumed server region IDs.", "Cyan",
                                Setting.number("grid-size", "Blocks per grid cell", 512, 16, 65536, 16),
                                Setting.number("position-x", "Horizontal position (%)", 98, 0, 100, 1),
                                Setting.number("position-y", "Vertical position (%)", 70, 0, 100, 1),
                                Setting.choice("cells", "Visible cells", "5", "3", "5", "7", "9"),
                                Setting.bool("show-coordinates", "Coordinates", true),
                                Setting.bool("show-labels", "Grid coordinates", true),
                                Setting.bool("show-player", "Player indicator", true),
                                Setting.bool("show-stashes", "Local stash markers", true))
        );
    }
}
