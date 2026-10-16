package dev.analyzer.core;

import java.util.List;
import java.util.ArrayList;

/** The complete supported module catalog. No placeholders are presented as features. */
public final class BuiltinModules {
    private BuiltinModules() { }

    public static List<ModuleDefinition> definitions() {
        List<ModuleDefinition> modules = new ArrayList<>(List.of(
                new ModuleDefinition("entity-esp", "Entity ESP", "Clean debug boxes for players, hostile mobs and non-hostile mobs.",
                        Category.VISUAL, false, entityEspSettings()),
                new ModuleDefinition("storage-esp", "Storage ESP", "Debug boxes around loaded containers, with colors for each storage group.",
                        Category.WORLD, false, storageEspSettings()),
                new ModuleDefinition("tracers", "Tracers", "Single clean lines to selected entities, using the same colors as Entity ESP.",
                        Category.VISUAL, false, tracerSettings()),
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
                        Category.COSMETICS, true, List.of(
                        Setting.number("density", "Density", 0.75, 0, 1, 0.05))),
                new ModuleDefinition("menu", "Interface", "Glass or solid panels, accent colors and motion control.",
                        Category.CLIENT, true, menuSettings()),
                new ModuleDefinition("sky", "Sky Aurora", "Animated sky curtains, stars and cosmic color above your world.",
                        Category.WORLD, true, List.of(
                        Setting.choice("preset", "Sky preset", "Aurora", "Aurora", "Nebula", "Ember"),
                        Setting.number("intensity", "Intensity", 0.8, 0, 1, 0.05),
                        Setting.bool("stars", "Twinkling stars", true),
                        Setting.number("speed", "Animation speed", 0.6, 0, 2, 0.05),
                        Setting.choice("day-night", "Visible when", "Always", "Always", "Night"))),
                new ModuleDefinition("shader", "Atmosphere", "Cinematic bloom and color grading. Sky Aurora controls the sky.",
                        Category.VISUAL, false, List.of(
                        Setting.choice("preset", "Preset", "Aurora", "Off", "Aurora", "Ember", "Frost", "Bloom"),
                        Setting.number("strength", "Strength", 0.75, 0, 1, 0.01)))
        ));
        modules.addAll(ClientModules.definitions());
        modules.add(ChamsSettings.definition());
        modules.addAll(BlockFinderModules.definitions());
        modules.addAll(EntityFinderModules.definitions());
        modules.addAll(SurveyFinderModules.definitions());
        modules.addAll(VisualModules.definitions());
        modules.addAll(XrayModules.definitions());
        modules.addAll(CosmeticModules.definitions());
        modules.addAll(GameplayModules.definitions());
        modules.addAll(UtilityModules.definitions());
        modules.addAll(AutomationModules.definitions());
        return List.copyOf(modules);
    }
    private static List<Setting<?>> entityEspSettings() {
        List<Setting<?>> settings = new ArrayList<>(List.of(
                Setting.bool("players", "Players", true),
                Setting.bool("hostiles", "Hostile mobs", true),
                Setting.bool("passives", "Non-hostile mobs", false),
                Setting.bool("items", "Dropped items", false),
                Setting.bool("labels", "Name and distance", false),
                Setting.bool("health", "Health", false),
                Setting.bool("radar", "Nearby target radar", false)));
        settings.addAll(espGeometrySettings(128, 256));
        settings.addAll(EspSettings.entityColors());
        settings.addAll(espLabelSettings(48, 256, 16));
        return List.copyOf(settings);
    }
    private static List<Setting<?>> storageEspSettings() {
        List<Setting<?>> settings = new ArrayList<>(List.of(
                Setting.bool("labels", "Container labels", false),
                Setting.bool("tracers", "Storage tracers", false),
                Setting.number("tracer-width", "Tracer width", 1, .5, 4, .25)));
        settings.addAll(espGeometrySettings(64, 128));
        for (EspSettings.StorageGroup group : EspSettings.StorageGroup.values()) {
            settings.add(Setting.bool(group.id(), group.label(), true));
            settings.addAll(EspSettings.colors(group.id(), group.label(), group.defaultColor()));
        }
        settings.addAll(espLabelSettings(40, 128, 12));
        return List.copyOf(settings);
    }
    private static List<Setting<?>> tracerSettings() {
        List<Setting<?>> settings = new ArrayList<>(List.of(
                Setting.bool("players", "Players", true),
                Setting.bool("hostiles", "Hostile mobs", false),
                Setting.bool("passives", "Non-hostile mobs", false),
                Setting.bool("items", "Dropped items", false),
                Setting.number("range", "Range (blocks)", 128, 16, 256, 8),
                Setting.bool("through-walls", "Through walls", true),
                Setting.number("line-width", "Line width", 1, .5, 4, .25),
                Setting.number("opacity", "Line opacity (%)", 100, 10, 100, 5),
                Setting.choice("anchor", "Line anchor", "Center", "Center", "Feet", "Head"),
                Setting.bool("match-esp-colors", "Use Entity ESP colors", true)));
        settings.addAll(EspSettings.entityColors());
        return List.copyOf(settings);
    }
    private static List<Setting<?>> espGeometrySettings(int range, int maximum) {
        return List.of(Setting.number("range", "Range (blocks)", range, 16, maximum, 8),
                Setting.bool("through-walls", "Through walls", true),
                Setting.number("line-width", "Line width", 1, .5, 4, .25),
                Setting.number("opacity", "Line opacity (%)", 100, 10, 100, 5),
                Setting.choice("style", "Box style", "Boxes", "Boxes", "Corners", "Screen"),
                Setting.number("min-marker-pixels", "Minimum marker size (pixels)", 0, 0, 12, 1));
    }
    private static List<Setting<?>> espLabelSettings(int range, int maximum, int budget) {
        return List.of(Setting.number("label-range", "Label range (blocks)", range, 8, maximum, 4),
                Setting.number("label-scale", "Label scale", 1, .5, 2, .05),
                Setting.bool("distance-scale", "Shrink distant labels", true),
                Setting.number("label-near", "Full-size distance (blocks)", 8, 2, 64, 2),
                Setting.number("label-far-scale", "Distant label size", .45, .2, 1, .05),
                Setting.bool("fade-labels", "Fade distant labels", true),
                Setting.number("max-labels", "Maximum labels", budget, 0, 128, 1),
                Setting.bool("declutter", "Hide overlapping labels", true));
    }
    private static List<Setting<?>> menuSettings() {
        List<Setting<?>> settings = new ArrayList<>(List.of(
            Setting.choice("material", "Panel material", "Glass", "Glass", "Solid"),
            Setting.choice("theme", "Menu theme", "Glass", "Glass", "Classic", "Minimal", "Neon"),
            Setting.choice("accent", "Accent color", "Rose", "Rose", "Mint", "Violet", "Gold"),
            Setting.number("motion", "Motion amount", 0.8, 0, 1, 0.05),
            Setting.bool("home-enabled", "Custom home screen", true),
            Setting.choice("home-background", "Home background", "Panorama", "Panorama", "Orbit"),
            Setting.number("home-motion", "Home animation", 0.65, 0, 1, 0.05),
            Setting.bool("home-blur", "Blur home submenus", true)));
        settings.addAll(ClientModules.menuSettings());
        return List.copyOf(settings);
    }
}
