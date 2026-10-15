package dev.analyzer.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Small shared ESP palette and storage groups. Saved colors are independent of the menu theme. */
public final class EspSettings {
    private EspSettings() { }

    private static final String[] COLORS = {"White", "Red", "Orange", "Gold", "Lime", "Mint", "Cyan", "Blue", "Violet", "Pink", "Gray", "Custom"};
    private static final Set<String> SHULKERS = Set.of("shulker_box", "white_shulker_box", "orange_shulker_box",
            "magenta_shulker_box", "light_blue_shulker_box", "yellow_shulker_box", "lime_shulker_box", "pink_shulker_box",
            "gray_shulker_box", "light_gray_shulker_box", "cyan_shulker_box", "purple_shulker_box", "blue_shulker_box",
            "brown_shulker_box", "green_shulker_box", "red_shulker_box", "black_shulker_box");

    public enum StorageGroup {
        CHESTS("chests", "Chests", "Gold"),
        TRAPPED_CHESTS("trapped-chests", "Trapped chests", "Red"),
        ENDER_CHESTS("ender-chests", "Ender chests", "Violet"),
        BARRELS("barrels", "Barrels", "Orange"),
        SHULKERS("shulkers", "Shulker boxes", "Pink"),
        HOPPERS("hoppers", "Hoppers", "Cyan"),
        FURNACES("furnaces", "Furnaces and smokers", "Orange"),
        DISPENSERS("dispensers", "Dispensers", "Gray"),
        DROPPERS("droppers", "Droppers", "White"),
        BREWING("brewing", "Brewing stands", "Blue"),
        CRAFTERS("crafters", "Crafters", "Mint"),
        BOOKSHELVES("bookshelves", "Chiseled bookshelves", "Gold"),
        POTS("pots", "Decorated pots", "Orange"),
        LECTERNS("lecterns", "Lecterns", "Gold"),
        HIVES("hives", "Beehives and nests", "Lime"),
        CAMPFIRES("campfires", "Campfires", "Orange"),
        JUKEBOXES("jukeboxes", "Jukeboxes", "Pink"),
        VEHICLES("vehicles", "Storage minecarts and boats", "Gold");

        private final String id, label, color;
        StorageGroup(String id, String label, String color) { this.id = id; this.label = label; this.color = color; }
        public String id() { return id; }
        public String label() { return label; }
        public String defaultColor() { return color; }
    }

    /** Exact vanilla IDs only; arbitrary similarly named mod blocks are not silently classified. */
    public static StorageGroup storageGroup(String blockId) {
        if (blockId == null || !blockId.startsWith("minecraft:")) return null;
        String path = blockId.substring(10);
        if (SHULKERS.contains(path)) return StorageGroup.SHULKERS;
        return switch (path) {
            case "chest" -> StorageGroup.CHESTS;
            case "trapped_chest" -> StorageGroup.TRAPPED_CHESTS;
            case "ender_chest" -> StorageGroup.ENDER_CHESTS;
            case "barrel" -> StorageGroup.BARRELS;
            case "hopper" -> StorageGroup.HOPPERS;
            case "furnace", "blast_furnace", "smoker" -> StorageGroup.FURNACES;
            case "dispenser" -> StorageGroup.DISPENSERS;
            case "dropper" -> StorageGroup.DROPPERS;
            case "brewing_stand" -> StorageGroup.BREWING;
            case "crafter" -> StorageGroup.CRAFTERS;
            case "chiseled_bookshelf" -> StorageGroup.BOOKSHELVES;
            case "decorated_pot" -> StorageGroup.POTS;
            case "lectern" -> StorageGroup.LECTERNS;
            case "beehive", "bee_nest" -> StorageGroup.HIVES;
            case "campfire", "soul_campfire" -> StorageGroup.CAMPFIRES;
            case "jukebox" -> StorageGroup.JUKEBOXES;
            default -> null;
        };
    }

    public static int palette(String name) {
        return switch (name == null ? "" : name) {
            case "Red" -> 0xFFFF7188; case "Orange" -> 0xFFFFA563; case "Gold" -> 0xFFFFCF78;
            case "Lime" -> 0xFFB9EB72; case "Mint" -> 0xFF79F8CC; case "Cyan" -> 0xFF66DFF7;
            case "Blue" -> 0xFF83B4FF; case "Violet" -> 0xFFB59BFA; case "Pink" -> 0xFFF48CDC;
            case "Gray" -> 0xFFBEC5CE; default -> 0xFFF1F3F6;
        };
    }

    public static int color(String name, double red, double green, double blue) {
        if (!"Custom".equals(name)) return palette(name);
        return 0xFF000000 | channel(red) << 16 | channel(green) << 8 | channel(blue);
    }

    private static int channel(double value) { return (int) Math.round(Math.clamp(Double.isFinite(value) ? value : 0, 0, 255)); }

    public static List<Setting<?>> colors(String id, String label, String defaultColor) {
        int rgb = palette(defaultColor);
        return List.of(Setting.choice(id + "-color", label + " color", defaultColor, COLORS),
                Setting.number(id + "-red", label + " red", rgb >> 16 & 255, 0, 255, 1),
                Setting.number(id + "-green", label + " green", rgb >> 8 & 255, 0, 255, 1),
                Setting.number(id + "-blue", label + " blue", rgb & 255, 0, 255, 1));
    }

    public static List<Setting<?>> entityColors() {
        List<Setting<?>> settings = new ArrayList<>();
        settings.addAll(colors("players", "Players", "Cyan"));
        settings.addAll(colors("hostiles", "Hostile mobs", "Red"));
        settings.addAll(colors("passives", "Non-hostile mobs", "Mint"));
        settings.addAll(colors("items", "Dropped items", "Gold"));
        return settings;
    }
}
