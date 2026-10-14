package dev.analyzer.automation;

import java.util.Map;

/** Deliberately explicit legacy conversion. Unknown ID/data pairs never become air or stone. */
final class LegacySchematicBlocks {
    private static final String[] WOOD = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak"};
    private static final String[] COLORS = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"};
    private LegacySchematicBlocks() { }
    static SchematicData.State resolve(int id, int data) {
        if (id == 1 && data <= 6) return block(new String[]{"stone", "granite", "polished_granite", "diorite", "polished_diorite", "andesite", "polished_andesite"}[data]);
        if (id == 3 && data <= 2) return block(new String[]{"dirt", "coarse_dirt", "podzol"}[data]);
        if (id == 5 && data < WOOD.length) return block(WOOD[data] + "_planks");
        if (id == 12 && data <= 1) return block(data == 0 ? "sand" : "red_sand");
        if (id == 19 && data <= 1) return block(data == 0 ? "sponge" : "wet_sponge");
        if ((id == 17 || id == 162) && (data & 3) < (id == 17 ? 4 : 2)) {
            String wood = WOOD[(data & 3) + (id == 162 ? 4 : 0)]; int orientation = data & 12;
            return state(wood + (orientation == 12 ? "_wood" : "_log"), Map.of("axis", orientation == 4 ? "x" : orientation == 8 ? "z" : "y"));
        }
        if (id == 35 || id == 95 || id == 159 || id == 160 || id == 171 || id == 251 || id == 252) {
            String suffix = switch (id) { case 35 -> "_wool"; case 95 -> "_stained_glass"; case 159 -> "_terracotta"; case 160 -> "_stained_glass_pane"; case 171 -> "_carpet"; case 251 -> "_concrete"; default -> "_concrete_powder"; };
            return block(COLORS[data] + suffix);
        }
        if (id == 24 && data <= 2) return block(new String[]{"sandstone", "chiseled_sandstone", "cut_sandstone"}[data]);
        if (id == 179 && data <= 2) return block(new String[]{"red_sandstone", "chiseled_red_sandstone", "cut_red_sandstone"}[data]);
        if (id == 98 && data <= 3) return block(new String[]{"stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks", "chiseled_stone_bricks"}[data]);
        if (id == 155 && data <= 4) return data < 2 ? block(data == 0 ? "quartz_block" : "chiseled_quartz_block") : state("quartz_pillar", Map.of("axis", data == 3 ? "x" : data == 4 ? "z" : "y"));
        String stair = switch (id) {
            case 53 -> "oak"; case 67 -> "cobblestone"; case 108 -> "brick"; case 109 -> "stone_brick"; case 114 -> "nether_brick";
            case 128 -> "sandstone"; case 134 -> "spruce"; case 135 -> "birch"; case 136 -> "jungle"; case 156 -> "quartz";
            case 163 -> "acacia"; case 164 -> "dark_oak"; case 180 -> "red_sandstone"; case 203 -> "purpur"; default -> null;
        };
        if (stair != null && data < 8) return state(stair + "_stairs", Map.of("facing", new String[]{"east", "west", "south", "north"}[data & 3], "half", (data & 4) != 0 ? "top" : "bottom", "shape", "straight", "waterlogged", "false"));
        if (id == 43 || id == 44 || id == 125 || id == 126 || id == 181 || id == 182 || id == 204 || id == 205) {
            int variant = data & 7; String material = null;
            if (id == 43 || id == 44) material = new String[]{"smooth_stone", "sandstone", "petrified_oak", "cobblestone", "brick", "stone_brick", "nether_brick", "quartz"}[variant];
            else if ((id == 125 || id == 126) && variant < WOOD.length) material = WOOD[variant];
            else if ((id == 181 || id == 182) && variant == 0) material = "red_sandstone";
            else if ((id == 204 || id == 205) && variant == 0) material = "purpur";
            if (material != null) {
                boolean doubled = id == 43 || id == 125 || id == 181 || id == 204;
                // Legacy seamless double slabs use different modern blocks; refuse ambiguous data.
                if (doubled && (data & 8) != 0) return unsupported(id, data);
                return state(material + "_slab", Map.of("type", doubled ? "double" : (data & 8) != 0 ? "top" : "bottom", "waterlogged", "false"));
            }
        }
        if ((id == 50 || id == 75 || id == 76) && data >= 1 && data <= 5) {
            String material = id == 50 ? "" : "redstone_";
            Map<String, String> props = id == 50 ? Map.of() : Map.of("lit", id == 76 ? "true" : "false");
            if (data == 5) return state(material + "torch", props);
            var values = new java.util.TreeMap<>(props); values.put("facing", new String[]{"", "east", "west", "south", "north"}[data]);
            return state(material + "wall_torch", values);
        }
        if ((id == 54 || id == 146 || id == 61 || id == 62 || id == 65) && data >= 2 && data <= 5) {
            String name = id == 54 ? "chest" : id == 146 ? "trapped_chest" : id == 65 ? "ladder" : "furnace";
            var props = new java.util.TreeMap<String, String>(); props.put("facing", new String[]{"", "", "north", "south", "west", "east"}[data]);
            if (id == 61 || id == 62) props.put("lit", id == 62 ? "true" : "false");
            else { props.put("waterlogged", "false"); if (id != 65) props.put("type", "single"); }
            return state(name, props);
        }
        if (id == 78 && data < 8) return state("snow", Map.of("layers", Integer.toString(data + 1)));
        if (id == 170 && (data == 0 || data == 4 || data == 8)) return state("hay_block", Map.of("axis", data == 4 ? "x" : data == 8 ? "z" : "y"));
        if (data == 0) {
            String name = switch (id) {
                case 0 -> "air"; case 2 -> "grass_block"; case 4 -> "cobblestone"; case 7 -> "bedrock"; case 13 -> "gravel";
                case 14 -> "gold_ore"; case 15 -> "iron_ore"; case 16 -> "coal_ore"; case 20 -> "glass"; case 21 -> "lapis_ore";
                case 22 -> "lapis_block"; case 30 -> "cobweb"; case 32 -> "dead_bush"; case 37 -> "dandelion"; case 39 -> "brown_mushroom";
                case 40 -> "red_mushroom"; case 41 -> "gold_block"; case 42 -> "iron_block"; case 45 -> "bricks"; case 47 -> "bookshelf";
                case 48 -> "mossy_cobblestone"; case 49 -> "obsidian"; case 56 -> "diamond_ore"; case 57 -> "diamond_block";
                case 58 -> "crafting_table"; case 73, 74 -> "redstone_ore"; case 79 -> "ice"; case 80 -> "snow_block"; case 82 -> "clay";
                case 85 -> "oak_fence"; case 87 -> "netherrack"; case 88 -> "soul_sand"; case 89 -> "glowstone"; case 101 -> "iron_bars";
                case 102 -> "glass_pane"; case 110 -> "mycelium"; case 112 -> "nether_bricks"; case 113 -> "nether_brick_fence";
                case 121 -> "end_stone"; case 122 -> "dragon_egg"; case 129 -> "emerald_ore"; case 133 -> "emerald_block";
                case 152 -> "redstone_block"; case 153 -> "nether_quartz_ore"; case 165 -> "slime_block"; case 172 -> "terracotta";
                case 173 -> "coal_block"; case 174 -> "packed_ice"; case 201 -> "purpur_block"; case 206 -> "end_stone_bricks";
                case 213 -> "magma_block"; case 214 -> "nether_wart_block"; case 215 -> "red_nether_bricks"; case 217 -> "structure_void";
                default -> null;
            };
            if (name != null) return block(name);
        }
        return unsupported(id, data);
    }
    private static SchematicData.State unsupported(int id, int data) { throw new IllegalArgumentException("Unsupported legacy block ID " + id + " with metadata " + data + "; export this schematic as a current .litematic/.schem instead"); }
    /** Legacy metadata omits these modern connection states. Do not start a partially wrong build. */
    static String buildProblem(SchematicData data) {
        if (!data.format().startsWith("Legacy")) return "";
        for (int y = 0; y < data.height(); y++) for (int z = 0; z < data.length(); z++) for (int x = 0; x < data.width(); x++) {
            SchematicData.State state = data.stateAt(x, y, z); if (state == null || state.air()) continue;
            String name = state.name();
            boolean connected = name.endsWith("_fence") || name.endsWith("_glass_pane") || name.equals("minecraft:glass_pane") || name.equals("minecraft:iron_bars");
            if (connected) return omittedState(name);
            for (int[] offset : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                SchematicData.State neighbor = data.stateAt(x + offset[0], y, z + offset[1]);
                if (neighbor == null) continue;
                if (name.endsWith("_stairs") && neighbor.name().endsWith("_stairs")
                        && state.properties().getOrDefault("half", "bottom").equals(neighbor.properties().getOrDefault("half", "bottom"))
                        && horizontalAxis(state.properties().get("facing")) != horizontalAxis(neighbor.properties().get("facing"))) return omittedState(name);
                if ((name.equals("minecraft:chest") || name.equals("minecraft:trapped_chest")) && neighbor.name().equals(name)) return omittedState(name);
            }
            if (name.equals("minecraft:grass_block") || name.equals("minecraft:podzol") || name.equals("minecraft:mycelium")) {
                SchematicData.State above = data.stateAt(x, y + 1, z);
                if (above != null && (above.name().equals("minecraft:snow") || above.name().equals("minecraft:snow_block"))) return omittedState(name);
            }
        }
        return "";
    }
    private static int horizontalAxis(String facing) { return "north".equals(facing) || "south".equals(facing) ? 0 : 1; }
    private static String omittedState(String name) { return "Legacy metadata does not contain the exact modern connection state for " + name + "; export this build as a current .litematic/.schem before starting"; }
    private static SchematicData.State block(String name) { return state(name, Map.of()); }
    private static SchematicData.State state(String name, Map<String, String> props) { return new SchematicData.State("minecraft:" + name, props); }
}
