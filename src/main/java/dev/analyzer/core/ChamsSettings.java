package dev.analyzer.core;

import java.util.ArrayList;
import java.util.List;

/** Settings for original shaders drawn on the animated entity mesh, independent of box ESP. */
public final class ChamsSettings {
    private ChamsSettings() { }

    public static ModuleDefinition definition() {
        return new ModuleDefinition("chams", "Entity Chams",
                "Shader materials on actual living models, with separate visible and occluded colors.",
                Category.VISUAL, false, settings());
    }

    public static List<Setting<?>> settings() {
        List<Setting<?>> settings = new ArrayList<>(List.of(
                Setting.choice("style", "Material style", "Flat", "Flat", "Metallic", "Glass", "Glow", "Wireframe"),
                Setting.bool("players", "Players", true),
                Setting.bool("hostiles", "Hostile mobs", true),
                Setting.bool("passives", "Non-hostile mobs", false),
                Setting.bool("self", "Own player in third person", false),
                Setting.bool("invisibles", "Invisible entities", false),
                Setting.number("range", "Range (blocks)", 128, 8, 256, 8),
                Setting.bool("through-walls", "Occluded model pass", true),
                Setting.number("visible-opacity", "Visible opacity (%)", 85, 0, 100, 5),
                Setting.number("hidden-opacity", "Occluded opacity (%)", 45, 0, 100, 5),
                Setting.number("effect-strength", "Material strength", 1, 0, 2, .05),
                Setting.number("animation-speed", "Material animation speed", 1, 0, 3, .05),
                Setting.number("wire-width", "Wire width", 1, .5, 3, .25),
                Setting.bool("match-esp-colors", "Use Entity ESP visible colors", true)));
        settings.addAll(EspSettings.colors("players", "Visible players", "Cyan"));
        settings.addAll(EspSettings.colors("hostiles", "Visible hostile mobs", "Red"));
        settings.addAll(EspSettings.colors("passives", "Visible non-hostile mobs", "Mint"));
        settings.addAll(EspSettings.colors("players-hidden", "Occluded players", "Violet"));
        settings.addAll(EspSettings.colors("hostiles-hidden", "Occluded hostile mobs", "Orange"));
        settings.addAll(EspSettings.colors("passives-hidden", "Occluded non-hostile mobs", "Blue"));
        return List.copyOf(settings);
    }
}
