package dev.analyzer.core;

import java.util.List;

/** Original loaded-block finders. Pattern matches are evidence, never proof of a player or stash. */
public final class BlockFinderModules {
    private BlockFinderModules() { }

    public static List<ModuleDefinition> definitions() {
        return List.of(
                FinderSettings.module("amethyst-finder", "Amethyst Finder",
                        "Marks received budding amethyst or geode blocks; chunk evidence is local to loaded data.", "Violet",
                        Setting.choice("target", "Amethyst blocks", "Budding amethyst", "Budding amethyst", "All amethyst"),
                        yMin(-64), yMax(64)),
                FinderSettings.module("bedrock-void-esp", "Bedrock Void ESP",
                        "Finds known air openings in the world's lowest layer beside received bedrock.", "Red",
                        Setting.number("minimum-size", "Minimum connected opening cells", 2, 1, 16, 1)),
                FinderSettings.module("beehive-esp", "Beehive ESP",
                        "Shows received beehives and bee nests at the selected honey level.", "Gold",
                        Setting.number("minimum-honey", "Minimum honey level", 5, 0, 5, 1),
                        Setting.bool("beehives", "Crafted beehives", true), Setting.bool("bee-nests", "Natural bee nests", true)),
                FinderSettings.module("block-notifier", "Block Notifier",
                        "Highlights selected valuable blocks, workstations or storage in received chunks.", "Cyan",
                        Setting.choice("target", "Block group", "Valuable blocks", "Valuable blocks", "Spawners", "Workstations", "Storage", "Diamond ore", "Ancient debris", "Beacons", "Nether portals"),
                        Setting.number("minimum-neighbors", "Minimum matching cells including target", 1, 1, 7, 1)),
                FinderSettings.module("covered-hole-esp", "Covered Hole ESP",
                        "Finds a solid cover above a bounded, enclosed vertical shaft; natural formations can match.", "Orange",
                        Setting.number("minimum-depth", "Minimum air depth", 3, 2, 16, 1),
                        Setting.number("maximum-depth", "Maximum checked depth", 16, 2, 32, 1),
                        Setting.bool("manufactured-cover-only", "Only building-block covers", true)),
                FinderSettings.module("deepslate-esp", "Deepslate ESP",
                        "Highlights deepslate by type and height; high blocks are a clue rather than placement proof.", "Blue",
                        Setting.choice("target", "Deepslate blocks", "Raw deepslate", "Raw deepslate", "Building variants", "Both"),
                        yMin(8), yMax(320)),
                FinderSettings.module("dripstone-esp", "Dripstone ESP",
                        "Marks supported pointed-dripstone columns up to 32 blocks long.", "Orange",
                        Setting.bool("stalactites", "Hanging stalactites", true), Setting.bool("stalagmites", "Rising stalagmites", true),
                        Setting.number("minimum-length", "Minimum column length", 4, 1, 32, 1)),
                FinderSettings.module("hole-tunnel-stairs-esp", "Hole / Tunnel / Stairs ESP",
                        "Recognizes bounded shafts, enclosed corridors and rising passage patterns in loaded terrain.", "Violet",
                        Setting.bool("holes", "Vertical shafts", true), Setting.bool("tunnels", "Enclosed tunnels", true),
                        Setting.bool("stairs", "Rising passages", true),
                        Setting.number("minimum-depth", "Minimum shaft depth", 3, 2, 16, 1),
                        Setting.number("maximum-depth", "Maximum checked shaft depth", 16, 2, 32, 1),
                        Setting.number("minimum-length", "Minimum tunnel / stair length", 4, 3, 16, 1)),
                FinderSettings.module("light-esp", "Light ESP",
                        "Highlights received emitted block light; sunlight is excluded and source mode avoids propagated-light clutter.", "Gold",
                        Setting.number("minimum-light", "Minimum block light", 8, 1, 15, 1),
                        Setting.bool("source-only", "Only emitting blocks", true),
                        Setting.bool("include-natural", "Include natural light-source types", false),
                        Setting.bool("torches", "Torches and lanterns", true), Setting.bool("glowstone", "Glowstone and sea lanterns", true),
                        Setting.bool("redstone", "Redstone light sources", true), Setting.bool("beacons", "Beacons and conduits", true),
                        Setting.bool("thermal-colors", "Color by light strength", false)),
                FinderSettings.module("one-by-one-holes", "1 x 1 Holes",
                        "Finds one-cell pockets with a solid floor and four walls; matching terrain may be natural.", "Cyan",
                        Setting.bool("covered-only", "Require a solid ceiling", true)),
                FinderSettings.module("piston-esp", "Piston ESP",
                        "Highlights received pistons and piston heads, optionally only extended mechanisms.", "Mint",
                        Setting.bool("regular", "Regular pistons", true), Setting.bool("sticky", "Sticky pistons", true),
                        Setting.bool("heads", "Extended piston heads", true), Setting.bool("extended-only", "Only extended pistons", false)),
                FinderSettings.module("rotated-deepslate-esp", "Rotated Deepslate ESP",
                        "Shows raw deepslate with an actual horizontal axis; axisless decorative blocks are excluded.", "Violet",
                        Setting.choice("orientation", "Block axis", "Horizontal", "Horizontal", "X axis", "Z axis")),
                FinderSettings.module("spawner-notifier", "Spawner Notifier",
                        "Marks received spawner blocks without claiming hidden contents, activation or server data access.", "Red",
                        Setting.bool("trial-spawners", "Include trial spawners", true)),
                FinderSettings.module("sweet-berry-esp", "Sweet Berry ESP",
                        "Shows berry bushes from their actual received growth age.", "Pink",
                        Setting.number("minimum-age", "Minimum bush age", 2, 0, 3, 1)),
                FinderSettings.module("vine-esp", "Vine ESP",
                        "Shows bounded vine columns, optionally requiring an observed solid block below their base.", "Mint",
                        Setting.number("minimum-length", "Minimum column length", 3, 1, 32, 1),
                        Setting.bool("grounded-only", "Require ground contact", true)));
    }

    private static Setting<Double> yMin(int initial) { return Setting.number("min-y", "Minimum Y", initial, -64, 320, 1); }
    private static Setting<Double> yMax(int initial) { return Setting.number("max-y", "Maximum Y", initial, -64, 320, 1); }
}
