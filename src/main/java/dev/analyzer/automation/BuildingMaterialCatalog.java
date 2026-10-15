package dev.analyzer.automation;

import java.util.*;

/** Curated vanilla material supply recipes. Unsupported palettes require real owned blocks. */
public final class BuildingMaterialCatalog {
    public enum Job { DIRECT, CRAFT, SMELT, SUPPLIED }
    public record Ingredient(String itemId, int count) { }
    public record Material(String itemId, Job job, int outputPerRecipe, int width, List<String> cells, List<String> miningBlocks) {
        public Material { cells = List.copyOf(cells); miningBlocks = List.copyOf(miningBlocks); }
        public List<Ingredient> ingredients() {
            Map<String, Integer> needed = new LinkedHashMap<>();
            for (String cell : cells) if (!cell.isEmpty()) needed.merge(cell, 1, Integer::sum);
            return needed.entrySet().stream().map(e -> new Ingredient(e.getKey(), e.getValue())).toList();
        }
        public String[] blockIds() { return miningBlocks.toArray(String[]::new); }
    }
    private static final Map<String, Material> MATERIALS = new LinkedHashMap<>();
    static {
        for (String item : List.of("slime_ball", "string", "blaze_rod", "kelp", "honeycomb"))
            MATERIALS.put(item, new Material(item, Job.SUPPLIED, 1, 0, List.of(), List.of()));
        direct("cobblestone", "stone", "cobblestone"); direct("cobbled_deepslate", "deepslate", "cobbled_deepslate");
        direct("dirt", "dirt", "grass_block"); direct("sand", "sand"); direct("red_sand", "red_sand"); direct("gravel", "gravel");
        for (String block : List.of("netherrack", "blackstone", "basalt", "end_stone", "andesite", "diorite", "granite", "obsidian")) direct(block, block);
        direct("coal", "coal_ore", "deepslate_coal_ore"); direct("raw_iron", "iron_ore", "deepslate_iron_ore");
        direct("raw_copper", "copper_ore", "deepslate_copper_ore");
        direct("redstone", "redstone_ore", "deepslate_redstone_ore"); direct("diamond", "diamond_ore", "deepslate_diamond_ore");
        direct("raw_gold", "gold_ore", "deepslate_gold_ore"); direct("quartz", "nether_quartz_ore"); direct("clay_ball", "clay");
        smelt("stone", "cobblestone"); smelt("smooth_stone", "stone"); smelt("glass", "sand"); smelt("brick", "clay_ball");
        smelt("nether_brick", "netherrack"); smelt("iron_ingot", "raw_iron"); smelt("gold_ingot", "raw_gold");
        smelt("copper_ingot", "raw_copper");
        craft("stone_bricks", 4, 2, "stone", "stone", "stone", "stone");
        craft("bricks", 1, 2, "brick", "brick", "brick", "brick");
        craft("nether_bricks", 1, 2, "nether_brick", "nether_brick", "nether_brick", "nether_brick");
        craft("quartz_block", 1, 2, "quartz", "quartz", "quartz", "quartz");
        craft("quartz_slab", 6, 3, "quartz_block", "quartz_block", "quartz_block", "", "", "", "", "", "");
        craft("quartz_stairs", 4, 3, "quartz_block", "", "", "quartz_block", "quartz_block", "", "quartz_block", "quartz_block", "quartz_block");
        craft("iron_bars", 16, 3, "iron_ingot", "iron_ingot", "iron_ingot", "iron_ingot", "iron_ingot", "iron_ingot", "", "", "");
        craft("iron_trapdoor", 1, 2, "iron_ingot", "iron_ingot", "iron_ingot", "iron_ingot");
        craft("bucket", 1, 3, "iron_ingot", "", "iron_ingot", "", "iron_ingot", "", "", "", "");
        craft("chest", 1, 3, "oak_planks", "oak_planks", "oak_planks", "oak_planks", "", "oak_planks", "oak_planks", "oak_planks", "oak_planks");
        craft("hopper", 1, 3, "iron_ingot", "", "iron_ingot", "iron_ingot", "chest", "iron_ingot", "", "iron_ingot", "");
        craft("redstone_torch", 1, 2, "redstone", "", "stick", "");
        craft("lever", 1, 2, "stick", "", "cobblestone", "");
        craft("piston", 1, 3, "oak_planks", "oak_planks", "oak_planks", "cobblestone", "iron_ingot", "cobblestone", "cobblestone", "redstone", "cobblestone");
        craft("sticky_piston", 1, 2, "slime_ball", "", "piston", "");
        craft("observer", 1, 3, "cobblestone", "cobblestone", "cobblestone", "redstone", "redstone", "quartz", "cobblestone", "cobblestone", "cobblestone");
        craft("repeater", 1, 3, "", "", "", "redstone_torch", "redstone", "redstone_torch", "stone", "stone", "stone");
        craft("comparator", 1, 3, "", "redstone_torch", "", "redstone_torch", "quartz", "redstone_torch", "stone", "stone", "stone");
        craft("note_block", 1, 3, "oak_planks", "oak_planks", "oak_planks", "oak_planks", "redstone", "oak_planks", "oak_planks", "oak_planks", "oak_planks");
        craft("bow", 1, 3, "", "stick", "string", "stick", "", "string", "", "stick", "string");
        craft("dispenser", 1, 3, "cobblestone", "cobblestone", "cobblestone", "cobblestone", "bow", "cobblestone", "cobblestone", "redstone", "cobblestone");
        craft("dropper", 1, 3, "cobblestone", "cobblestone", "cobblestone", "cobblestone", "", "cobblestone", "cobblestone", "redstone", "cobblestone");
        craft("composter", 1, 3, "oak_slab", "", "oak_slab", "oak_slab", "", "oak_slab", "oak_slab", "oak_slab", "oak_slab");
        craft("copper_block", 1, 3, "copper_ingot", "copper_ingot", "copper_ingot", "copper_ingot", "copper_ingot", "copper_ingot", "copper_ingot", "copper_ingot", "copper_ingot");
        craft("copper_bulb", 4, 3, "", "copper_block", "", "copper_block", "blaze_rod", "copper_block", "", "redstone", "");
        craft("glass_pane", 16, 3, "glass", "glass", "glass", "glass", "glass", "glass", "", "", "");
        craft("furnace", 1, 3, "cobblestone", "cobblestone", "cobblestone", "cobblestone", "", "cobblestone", "cobblestone", "cobblestone", "cobblestone");
        for (String wood : List.of("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "pale_oak", "crimson", "warped")) {
            String log = wood + (wood.equals("crimson") || wood.equals("warped") ? "_stem" : "_log");
            String plank = wood + "_planks";
            direct(log, log); craft(plank, 4, 2, log, "", "", "");
            craft(wood + "_slab", 6, 3, plank, plank, plank, "", "", "", "", "", "");
            craft(wood + "_stairs", 4, 3, plank, "", "", plank, plank, "", plank, plank, plank);
            craft(wood + "_fence", 3, 3, plank, "stick", plank, plank, "stick", plank, "", "", "");
            craft(wood + "_fence_gate", 1, 3, "stick", plank, "stick", "stick", plank, "stick", "", "", "");
            craft(wood + "_door", 3, 3, plank, plank, "", plank, plank, "", plank, plank, "");
            craft(wood + "_trapdoor", 2, 3, plank, plank, plank, plank, plank, plank, "", "", "");
            craft(wood + "_sign", 3, 3, plank, plank, plank, plank, plank, plank, "", "stick", "");
        }
        craft("stick", 4, 2, "oak_planks", "", "oak_planks", "");
        craft("torch", 4, 2, "coal", "", "stick", "");
        craft("crafting_table", 1, 2, "oak_planks", "oak_planks", "oak_planks", "oak_planks");
        for (String block : List.of("cobblestone", "cobbled_deepslate", "stone", "stone_bricks", "bricks", "nether_bricks", "blackstone", "end_stone_bricks", "andesite", "diorite", "granite")) {
            String prefix = switch (block) { case "stone_bricks" -> "stone_brick"; case "bricks" -> "brick"; case "nether_bricks" -> "nether_brick"; case "end_stone_bricks" -> "end_stone_brick"; default -> block; };
            craft(prefix + "_slab", 6, 3, block, block, block, "", "", "", "", "", "");
            craft(prefix + "_stairs", 4, 3, block, "", "", block, block, "", block, block, block);
        }
        craft("end_stone_bricks", 4, 2, "end_stone", "end_stone", "end_stone", "end_stone");
    }
    private BuildingMaterialCatalog() { }
    private static void direct(String item, String... blocks) { MATERIALS.put(item, new Material(item, Job.DIRECT, 1, 0, List.of(), List.of(blocks))); }
    private static void craft(String item, int output, int width, String... cells) { MATERIALS.put(item, new Material(item, Job.CRAFT, output, width, List.of(cells), List.of())); }
    private static void smelt(String item, String source) { MATERIALS.put(item, new Material(item, Job.SMELT, 1, 0, List.of(source), List.of())); }
    public static Optional<Material> find(String itemId) { return Optional.ofNullable(MATERIALS.get(itemId.startsWith("minecraft:") ? itemId.substring(10) : itemId)); }
    public static List<Material> all() { return List.copyOf(MATERIALS.values()); }
    /** Names of real inputs whose acquisition controller is not implemented. No invented craft/mining job. */
    public static Set<String> manualInputs(String itemId) {
        var missing = new TreeSet<String>(); manualInputs(itemId, missing, new HashSet<>(), 0); return Collections.unmodifiableSet(missing);
    }
    private static void manualInputs(String itemId, Set<String> missing, Set<String> visited, int depth) {
        String key = itemId.startsWith("minecraft:") ? itemId.substring(10) : itemId;
        if (!visited.add(key)) return;
        var material = find(key).orElse(null);
        if (depth > 12 || material == null || material.job() == Job.SUPPLIED) { missing.add(key); return; }
        for (var ingredient : material.ingredients()) manualInputs(ingredient.itemId(), missing, visited, depth + 1);
    }
}
