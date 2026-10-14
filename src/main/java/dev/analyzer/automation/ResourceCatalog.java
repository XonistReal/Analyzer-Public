package dev.analyzer.automation;

import java.util.List;
import java.util.Locale;

/** Stable local resource intents; quantities are item goals, never inferred from path completion. */
public final class ResourceCatalog {
    public record Resource(String key, List<String> blocks, int pickaxeTier, String dimension) {
        public Resource { blocks = List.copyOf(blocks); }
        public String[] blockIds() { return blocks.toArray(String[]::new); }
    }
    private static final List<Resource> RESOURCES = List.of(
            resource("logs", 0, "", "oak_log", "spruce_log", "birch_log", "jungle_log", "acacia_log", "dark_oak_log", "mangrove_log", "cherry_log", "pale_oak_log", "crimson_stem", "warped_stem"),
            resource("cobblestone", 1, "", "stone", "cobblestone"),
            resource("raw_iron", 2, "overworld", "iron_ore", "deepslate_iron_ore"),
            resource("raw_copper", 2, "overworld", "copper_ore", "deepslate_copper_ore"),
            resource("raw_gold", 3, "overworld", "gold_ore", "deepslate_gold_ore"),
            resource("coal", 1, "overworld", "coal_ore", "deepslate_coal_ore"),
            resource("diamond", 3, "overworld", "diamond_ore", "deepslate_diamond_ore"),
            resource("emerald", 3, "overworld", "emerald_ore", "deepslate_emerald_ore"),
            resource("redstone", 3, "overworld", "redstone_ore", "deepslate_redstone_ore"),
            resource("lapis_lazuli", 2, "overworld", "lapis_ore", "deepslate_lapis_ore"),
            resource("quartz", 1, "the_nether", "nether_quartz_ore"),
            resource("ancient_debris", 4, "the_nether", "ancient_debris"),
            resource("obsidian", 4, "", "obsidian"),
            resource("gravel", 0, "", "gravel"),
            resource("flint", 0, "", "gravel"),
            resource("netherrack", 1, "the_nether", "netherrack"));
    private ResourceCatalog() { }
    private static Resource resource(String key, int tier, String dimension, String... blocks) {
        return new Resource(key, List.of(blocks), tier, dimension);
    }
    public static List<Resource> all() { return RESOURCES; }
    public static Resource get(String key) {
        return RESOURCES.stream().filter(r -> r.key().equals(key)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported resource: " + key));
    }
    public static String canonical(String name) {
        String value = name.toLowerCase(Locale.ROOT).replaceAll("[ _-]", "");
        if (value.endsWith("ores")) value = value.substring(0, value.length() - 4);
        else if (value.endsWith("ore")) value = value.substring(0, value.length() - 3);
        return switch (value) {
            case "log", "logs", "wood" -> "logs";
            case "stone", "cobblestone" -> "cobblestone";
            case "iron", "rawiron" -> "raw_iron";
            case "copper", "rawcopper" -> "raw_copper";
            case "gold", "rawgold" -> "raw_gold";
            case "diamond", "diamonds" -> "diamond";
            case "emerald", "emeralds" -> "emerald";
            case "lapis", "lapislazuli" -> "lapis_lazuli";
            case "ancientdebris", "debris" -> "ancient_debris";
            case "coal", "redstone", "quartz", "obsidian", "gravel", "flint", "netherrack" -> value;
            default -> throw new IllegalArgumentException("Unsupported resource: " + name);
        };
    }
}
