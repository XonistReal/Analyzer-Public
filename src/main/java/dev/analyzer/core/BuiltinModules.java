package dev.analyzer.core;

import java.util.List;

/** The complete supported module catalog. No placeholders are presented as features. */
public final class BuiltinModules {
    private BuiltinModules() { }

    public static List<ModuleDefinition> definitions() {
        return List.of(
                new ModuleDefinition("entity-esp", "Entity ESP", "Highlight nearby players, creatures and items.",
                        Category.VISUAL, false, List.of(
                        Setting.bool("players", "Players", true),
                        Setting.bool("hostiles", "Hostile mobs", true),
                        Setting.bool("passives", "Passive mobs", false),
                        Setting.bool("items", "Dropped items", false),
                        Setting.bool("labels", "Name and distance", true),
                        Setting.bool("health", "Health", true),
                        Setting.number("range", "Range", 128, 16, 256, 8),
                        Setting.bool("through-walls", "Through walls", true),
                        Setting.number("line-width", "Line width", 1.5, 0.5, 4, 0.25),
                        Setting.choice("style", "Box style", "Corners", "Boxes", "Corners"))),
                new ModuleDefinition("storage-esp", "Storage ESP", "Highlight nearby containers.",
                        Category.WORLD, false, List.of(
                        Setting.bool("labels", "Container labels", true),
                        Setting.number("range", "Range", 64, 16, 128, 8),
                        Setting.bool("through-walls", "Through walls", true),
                        Setting.number("line-width", "Line width", 1.5, 0.5, 4, 0.25),
                        Setting.choice("style", "Box style", "Boxes", "Boxes", "Corners"))),
                new ModuleDefinition("tracers", "Tracers", "Draw lines from your view to nearby entities.",
                        Category.VISUAL, false, List.of(
                        Setting.bool("players", "Players", true),
                        Setting.bool("hostiles", "Hostile mobs", false),
                        Setting.bool("passives", "Passive mobs", false),
                        Setting.bool("items", "Dropped items", false),
                        Setting.number("range", "Range", 128, 16, 256, 8),
                        Setting.bool("through-walls", "Through walls", true),
                        Setting.number("line-width", "Line width", 1, 0.5, 4, 0.25))),
                new ModuleDefinition("fullbright", "Fullbright", "Lift world lighting for clearer exploration.",
                        Category.WORLD, false, List.of(
                        Setting.number("gamma", "Brightness", 16, 1, 16, 0.5))),
                new ModuleDefinition("coordinates", "Coordinates", "Show position and dimension in your HUD.",
                        Category.HUD, true, List.of(
                        Setting.bool("show-dimension", "Show dimension", true))),
                new ModuleDefinition("crosshair", "Crosshair", "A crisp customizable crosshair.",
                        Category.HUD, false, List.of(
                        Setting.number("size", "Arm length", 6, 2, 20, 0.5),
                        Setting.number("gap", "Center gap", 3, 0, 12, 0.5),
                        Setting.number("line-width", "Line width", 1.5, 0.5, 4, 0.25))),
                new ModuleDefinition("particles", "Particles", "Animated ambient particles in the Analyzer menu.",
                        Category.VISUAL, true, List.of(
                        Setting.number("density", "Density", 0.5, 0, 1, 0.05))),
                new ModuleDefinition("shader", "Atmosphere", "Built-in Aurora, Ember and Frost screen effects.",
                        Category.VISUAL, false, List.of(
                        Setting.choice("preset", "Preset", "Aurora", "Off", "Aurora", "Ember", "Frost"),
                        Setting.number("strength", "Strength", 0.6, 0, 1, 0.01)))
        );
    }
}
