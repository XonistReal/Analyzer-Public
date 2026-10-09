package dev.analyzer.core;

import java.util.List;

/** Original, opt-in world and player utilities using normal game interactions. */
public final class UtilityModules {
    private UtilityModules() { }
    private static ModuleDefinition world(String id, String name, String description, Setting<?>... settings) {
        return new ModuleDefinition(id, name, description, Category.WORLD, false, List.of(settings));
    }
    private static ModuleDefinition player(String id, String name, String description, Setting<?>... settings) {
        return new ModuleDefinition(id, name, description, Category.PLAYER, false, List.of(settings));
    }
    private static Setting<Double> range() { return Setting.number("range", "Interaction range", 4, 1, 4.5, 0.25); }
    public static List<ModuleDefinition> definitions() {
        return List.of(
            world("scaffold", "Scaffold", "Place hotbar blocks underneath your feet using ordinary block interactions.",
                Setting.number("delay", "Placement delay (ticks)", 3, 2, 20, 1)),
            world("tower", "Tower", "While holding jump, rise onto automatically placed hotbar blocks.",
                Setting.number("height", "Jump velocity", 0.42, 0.42, 0.7, 0.02)),
            world("air-place", "Air Place", "While holding use, attempt to place a block at your crosshair in empty air.",
                Setting.number("distance", "Placement distance", 3, 1, 4, 0.25)),
            world("nuker", "Nuker", "Mine nearby reachable blocks one at a time with normal mining progress.", range()),
            world("auto-farm", "Auto Farm", "Harvest mature crops and replant empty farmland using hotbar seeds.", range()),
            world("auto-tunnel", "Auto Tunnel", "Mine a two-block-tall path in the direction you face.", range()),
            world("bed-breaker", "Bed Breaker", "Find and mine reachable beds using normal mining progress.", range()),
            world("flatten", "Flatten", "Mine reachable blocks above your standing level; preserve the floor.", range()),
            world("speed-mine", "Speed Mine", "Remove the client delay between ordinary block breaks; server mining rules still apply."),
            world("vein-miner", "Vein Miner", "After a manual break, mine connected blocks of the same type within reach.",
                range(), Setting.number("limit", "Connected block limit", 32, 4, 64, 1)),
            world("no-block-interact", "No Block Interact", "Use block placement against containers without opening them."),
            player("chest-stealer", "Chest Stealer", "Quick-move items from an open container at a bounded rate.",
                Setting.number("delay", "Transfer delay (ticks)", 4, 2, 20, 1)),
            player("middle-click-friend", "Middle Click Friend", "Middle-click a player to add or remove a locally saved friend."),
            player("player-detection", "Player Detection", "Notify locally when a non-friend enters your selected radius.",
                Setting.number("range", "Detection range", 48, 8, 128, 4)),
            player("notifier", "Notifier", "Local messages for loaded players arriving or departing."),
            player("coord-snapper", "Coord Snapper", "Copy your coordinates to the clipboard, then turn off automatically.",
                Setting.bool("nether", "Include Nether conversion", true))
        );
    }
}
