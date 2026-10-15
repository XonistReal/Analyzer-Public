package dev.analyzer.automation;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.ArrayList;

/** Stable local resource intents; quantities are item goals, never inferred from path completion. */
public final class ResourceCatalog {
    public record Resource(String key, List<String> blocks, int pickaxeTier, String dimension) {
        public Resource { blocks = List.copyOf(blocks); }
        public String[] blockIds() { return blocks.toArray(String[]::new); }
    }
    private static final List<Resource> RESOURCES = resources();
    private static List<Resource> resources() {
        var resources = new ArrayList<>(List.of(
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
            resource("netherrack", 1, "the_nether", "netherrack"),
            resource("sand", 0, "", "sand"),
            resource("red_sand", 0, "", "red_sand"),
            resource("dirt", 0, "", "dirt", "grass_block", "podzol", "mycelium"),
            resource("coarse_dirt", 0, "", "coarse_dirt"),
            resource("rooted_dirt", 0, "", "rooted_dirt"),
            resource("clay_ball", 0, "", "clay"),
            resource("cobbled_deepslate", 1, "overworld", "deepslate", "cobbled_deepslate"),
            resource("granite", 1, "overworld", "granite"),
            resource("diorite", 1, "overworld", "diorite"),
            resource("andesite", 1, "overworld", "andesite"),
            resource("tuff", 1, "overworld", "tuff"),
            resource("soul_sand", 0, "the_nether", "soul_sand"),
            resource("soul_soil", 0, "the_nether", "soul_soil")));
        for (String wood : List.of("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "pale_oak"))
            resources.add(resource(wood + "_log", 0, "", wood + "_log"));
        for (String wood : List.of("crimson", "warped")) resources.add(resource(wood + "_stem", 0, "the_nether", wood + "_stem"));
        return List.copyOf(resources);
    }
    private static final Map<String, String> ALIASES = aliasesByName();
    private static Map<String, String> aliasesByName() {
        var names = new LinkedHashMap<String, String>();
        for (Resource resource : RESOURCES) {
            String name = resource.key().replace('_', ' '); names.put(name, resource.key());
            if (name.endsWith(" log") || name.endsWith(" stem") || name.endsWith("ball")) names.put(name + "s", resource.key());
        }
        aliases(names, "logs", "log", "wood", "timber");
        aliases(names, "cobblestone", "stone", "cobble", "cobblestones");
        aliases(names, "raw_iron", "iron", "iron ore", "iron ores", "raw iron ore");
        aliases(names, "raw_copper", "copper", "copper ore", "copper ores", "raw copper ore");
        aliases(names, "raw_gold", "gold", "gold ore", "gold ores", "raw gold ore");
        aliases(names, "diamond", "diamonds", "diamond ore", "diamond ores");
        aliases(names, "emerald", "emeralds", "emerald ore", "emerald ores");
        aliases(names, "lapis_lazuli", "lapis", "lapis ore", "lapis lazuli ore");
        aliases(names, "redstone", "redstone dust", "redstone ore", "redstone ores");
        aliases(names, "coal", "coal ore", "coal ores");
        aliases(names, "quartz", "nether quartz", "quartz ore", "nether quartz ore");
        aliases(names, "ancient_debris", "debris");
        aliases(names, "dirt", "soil", "earth");
        aliases(names, "clay_ball", "clay", "clay balls");
        aliases(names, "cobbled_deepslate", "deepslate");
        return Collections.unmodifiableMap(names);
    }
    private static void aliases(Map<String, String> names, String key, String... aliases) { for (String alias : aliases) names.put(alias, key); }
    private ResourceCatalog() { }
    private static Resource resource(String key, int tier, String dimension, String... blocks) {
        return new Resource(key, List.of(blocks), tier, dimension);
    }
    public static List<Resource> all() { return RESOURCES; }
    public static Map<String, String> aliases() { return ALIASES; }
    public static Resource get(String key) {
        return RESOURCES.stream().filter(r -> r.key().equals(key)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported resource: " + key));
    }
    public static String canonical(String name) {
        if (name == null) throw new IllegalArgumentException("Choose a resource name.");
        String value = name.toLowerCase(Locale.ROOT).replaceAll("[ _-]", "");
        return ALIASES.entrySet().stream().filter(entry -> entry.getKey().replace(" ", "").equals(value)).map(Map.Entry::getValue).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported resource: " + name + ". Examples: sand, dirt, gravel, diamonds or oak logs."));
    }
}
