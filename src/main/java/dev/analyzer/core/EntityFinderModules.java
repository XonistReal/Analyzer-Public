package dev.analyzer.core;

import java.util.List;

/** Original, independent read-only overlays for entity families received from the server. */
public final class EntityFinderModules {
    private EntityFinderModules() { }

    public static List<ModuleDefinition> definitions() {
        return List.of(
                FinderSettings.module("collectible-esp", "Collectible ESP",
                        "Mark loaded item frames, armor stands and placed banners; contents stay server-controlled.", "Pink",
                        Setting.bool("item-frames", "Item frames", true),
                        Setting.bool("glow-frames", "Glow item frames", true),
                        Setting.bool("armor-stands", "Armor stands", true),
                        Setting.bool("banners", "Placed banners", true),
                        Setting.bool("filled-frames-only", "Only frames displaying an item", false)),
                FinderSettings.module("drowned-trident-esp", "Drowned Trident ESP",
                        "Mark received drowned actually holding a trident in either hand; drops are not guaranteed.", "Cyan"),
                FinderSettings.module("invisible-esp", "Invisible ESP",
                        "Mark received invisible players and mobs without altering their visibility or inventory.", "Violet",
                        Setting.bool("players", "Players", true),
                        Setting.bool("hostiles", "Hostile mobs", true),
                        Setting.bool("passives", "Non-hostile mobs", true)),
                FinderSettings.module("llama-esp", "Llama ESP", "Mark loaded llamas, optionally including trader llamas.", "Gold",
                        Setting.bool("trader-llamas", "Trader llamas", true)),
                FinderSettings.module("pillager-esp", "Pillager ESP", "Mark loaded pillagers with independent debug outlines.", "Red"),
                FinderSettings.module("skeleton-esp", "Skeleton ESP",
                        "Draw animated bones from the real posed biped model for selected players and mobs.", "White",
                        Setting.bool("players", "Players", true),
                        Setting.bool("hostiles", "Hostile mobs with biped models", true),
                        Setting.bool("passives", "Non-hostile mobs with biped models", false),
                        Setting.bool("invisibles", "Invisible entities", false),
                        Setting.bool("self", "Own player in third person", false),
                        Setting.bool("head", "Head direction bone", true)),
                FinderSettings.module("villager-esp", "Villager ESP", "Mark loaded villagers, optionally including zombie villagers.", "Mint",
                        Setting.bool("zombie-villagers", "Zombie villagers", false),
                        Setting.bool("adults-only", "Adults only", false)),
                FinderSettings.module("wandering-trader-esp", "Wandering Trader ESP",
                        "Mark received wandering traders; no unreceived entities or trade data are inferred.", "Blue"));
    }
}
