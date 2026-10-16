package dev.analyzer.core;

import java.util.ArrayList;
import java.util.List;

/** Every finder is opt-in, local and bounded. Shared IDs keep scan/render controls consistent. */
public final class FinderSettings {
    private FinderSettings() { }
    public static ModuleDefinition module(String id, String name, String description, String color, Setting<?>... specific) {
        List<Setting<?>> settings = new ArrayList<>(List.of(specific));
        settings.add(Setting.number("range", "Range (blocks)", 96, 16, 256, 8));
        settings.add(Setting.number("scan-budget", "Scan work per tick", 4096, 512, 16384, 512));
        settings.add(Setting.number("max-markers", "Maximum markers", 128, 8, 512, 8));
        settings.add(Setting.bool("through-walls", "Through walls", true));
        settings.add(Setting.bool("tracers", "Tracers", false));
        settings.add(Setting.choice("tracer-origin", "Tracer origin", "Crosshair", "Crosshair", "Bottom", "Top"));
        settings.add(Setting.number("line-width", "Line width", 1, .5, 3, .25));
        settings.add(Setting.number("opacity", "Opacity (%)", 100, 10, 100, 5));
        settings.add(Setting.bool("notify", "Local discovery messages", false));
        settings.addAll(EspSettings.colors("marker", "Marker", color));
        return new ModuleDefinition(id, name, description, Category.VISUAL, false, List.copyOf(settings));
    }
}
