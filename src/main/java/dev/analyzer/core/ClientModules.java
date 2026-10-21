package dev.analyzer.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Client widgets with data sourced from the running game, never synthetic counters. */
public final class ClientModules {
    private ClientModules() { }

    public static List<Setting<?>> menuSettings() {
        List<Setting<?>> settings = new ArrayList<>();
        settings.add(Setting.choice("layout", "Menu layout", "Studio", "Studio", "Click GUI", "Compact"));
        for (int i = 0; i < Category.values().length; i++) {
            String prefix = "classic-" + Category.values()[i].name().toLowerCase(Locale.ROOT);
            settings.add(Setting.number(prefix + "-x", Category.values()[i].label() + " column X", 2 + i % 5 * 24, 0, 100, 0.1));
            settings.add(Setting.number(prefix + "-y", Category.values()[i].label() + " column Y", 25 + i / 5 * 70, 0, 100, 0.1));
            settings.add(Setting.bool(prefix + "-collapsed", Category.values()[i].label() + " collapsed", false));
        }
        // Floating Click GUI panels: positions are fractions of the free screen area so they survive resizes.
        for (int i = 0; i < Category.values().length; i++) {
            Category category = Category.values()[i];
            String prefix = "click-" + category.name().toLowerCase(Locale.ROOT);
            settings.add(Setting.number(prefix + "-x", category.label() + " panel X", clickPanelX(category), 0, 100, 0.1));
            settings.add(Setting.number(prefix + "-y", category.label() + " panel Y", 7, 0, 100, 0.1));
            settings.add(Setting.bool(prefix + "-collapsed", category.label() + " panel collapsed", false));
        }
        return List.copyOf(settings);
    }

    public static List<ModuleDefinition> definitions() {
        List<ModuleDefinition> result = new ArrayList<>();
        result.add(new ModuleDefinition("server-privacy", "Server Privacy",
                "Reduce mod-identifying metadata and text probes. Vanilla-only channels can disable server-connected mods; gameplay remains observable.",
                Category.CLIENT, true, List.of(
                Setting.bool("vanilla-brand", "Report vanilla client brand", true),
                Setting.bool("block-mod-channels", "Block mod channel announcements", true),
                Setting.bool("vanilla-only", "Vanilla-only channels (strict)", true),
                Setting.bool("hide-known-packs", "Hide non-vanilla known packs", true),
                Setting.bool("block-login-queries", "Decline mod login queries", true),
                Setting.bool("block-key-probes", "Protect remote translation/key probes", true),
                Setting.bool("block-local-pack-urls", "Block local/private pack URLs", true),
                Setting.bool("scrub-pack-headers", "Hide identifying pack download headers", true))));
        result.add(new ModuleDefinition("hud", "HUD Studio", "Choose your menu, appearance and HUD widget layout.", Category.HUD, true, List.of(
                Setting.choice("menu-layout", "Menu layout", "Studio", "Studio", "Click GUI", "Compact"),
                Setting.choice("menu-material", "Menu material", "Glass", "Glass", "Solid"),
                Setting.choice("menu-theme", "Menu theme", "Glass", "Glass", "Classic", "Minimal", "Neon"),
                Setting.choice("menu-accent", "Accent color", "Rose", "Rose", "Mint", "Violet", "Gold"),
                Setting.bool("home-enabled", "Custom home screen", true),
                Setting.choice("home-background", "Home background", "Panorama", "Panorama", "Orbit"),
                Setting.number("home-motion", "Home animation", 0.65, 0, 1, 0.05),
                Setting.bool("home-blur", "Blur home submenus", true),
                Setting.choice("theme", "HUD theme", "Glass", "Glass", "Classic", "Minimal", "Neon"),
                Setting.number("scale", "HUD scale", 0.6, 0.5, 1.75, 0.05),
                Setting.number("max-modules", "Maximum list rows", 10, 3, 16, 1),
                Setting.bool("snap", "Editor grid snapping", true),
                Setting.bool("show-disabled", "Editor show disabled widgets", false),
                Setting.number("coordinates-x", "Coordinates X", 2, 0, 100, 0.1),
                Setting.number("coordinates-y", "Coordinates Y", 94, 0, 100, 0.1),
                Setting.number("radar-x", "Radar X", 2, 0, 100, 0.1),
                Setting.number("radar-y", "Radar Y", 26, 0, 100, 0.1))));
        result.add(widget("watermark", "Watermark", "Your selected logo and version badge.", true, 2, 2,
                Setting.choice("logo", "Logo", "Analyzer logo", "Analyzer logo", "A logo")));
        result.add(widget("active-modules", "Active Modules", "A bounded list of enabled gameplay modules.", true, 98, 2,
                Setting.bool("details", "ESP target details", true), Setting.bool("hud-modules", "Include HUD modules", false)));
        result.add(widget("fps", "FPS", "Current client frames per second.", true, 2, 12));
        result.add(widget("ping", "Ping", "Actual player latency reported by the server.", false, 17, 12));
        result.add(widget("speed-hud", "Speed HUD", "Horizontal movement speed in blocks per second.", false, 32, 12,
                Setting.choice("units", "Speed units", "Blocks/s", "Blocks/s", "km/h")));
        result.add(widget("armor", "Armor", "Equipped armor icons with remaining durability.", false, 50, 86,
                Setting.bool("durability", "Durability percentages", true)));
        result.add(widget("potions", "Potion Effects", "Active effects, levels and remaining durations.", false, 98, 55));
        result.add(widget("target-hud", "Target HUD", "Health and armor of your acquired combat or crosshair target.", false, 68, 74));
        result.add(widget("keybinds", "Keybinds", "Assigned module binds with their enabled state.", false, 2, 48,
                Setting.bool("active-only", "Only active binds", false)));
        result.add(widget("inventory", "Inventory Preview", "Your actual inventory in a compact three-row panel.", false, 50, 68,
                Setting.bool("counts", "Stack counts", true)));
        result.add(widget("compass", "Compass", "Yaw-based cardinal direction and heading.", false, 50, 2));
        result.add(widget("biome", "Biome", "Registry name of the biome at your current position.", false, 2, 84));
        result.add(widget("world-clock", "World Clock", "World time of day and current day number.", false, 2, 76,
                Setting.choice("format", "Clock format", "24 hour", "24 hour", "12 hour")));
        result.add(widget("item-counter", "Item Counter", "Count a selected resource in your inventory and offhand.", false, 81, 86,
                Setting.choice("item", "Count item", "Totems", "Totems", "Pearls", "Crystals", "Gapples", "Arrows")));
        result.add(widget("keystrokes", "Keystrokes", "Live movement, jump and mouse-button states.", false, 2, 61,
                Setting.bool("mouse", "Show mouse buttons", true)));
        result.add(widget("reach-display", "Target Distance", "Measured camera distance to the current raycast hit.", false, 68, 12));
        return List.copyOf(result);
    }

    /** Default floating panel order follows the menu's category order, spread across the screen. */
    public static double clickPanelX(Category category) {
        List<Category> order = List.of(Category.COMBAT, Category.HVH, Category.MOVEMENT, Category.VISUAL, Category.COSMETICS,
                Category.WORLD, Category.PLAYER, Category.HUD, Category.CLIENT);
        int index = Math.max(0, order.indexOf(category));
        return Math.round(index * 100.0 / (order.size() - 1) * 10) / 10.0;
    }

    private static ModuleDefinition widget(String id, String name, String description, boolean enabled,
                                           double x, double y, Setting<?>... extra) {
        List<Setting<?>> settings = new ArrayList<>();
        settings.add(Setting.number("x", "HUD position X", x, 0, 100, 0.1));
        settings.add(Setting.number("y", "HUD position Y", y, 0, 100, 0.1));
        settings.add(Setting.number("opacity", "Panel opacity", 0.8, 0.1, 1, 0.05));
        settings.addAll(List.of(extra));
        return new ModuleDefinition(id, name, description, Category.HUD, enabled, settings);
    }
}
