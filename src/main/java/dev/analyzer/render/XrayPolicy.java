package dev.analyzer.render;

import java.util.Locale;
import java.util.Set;

/** Immutable worker-thread render choices; no Minecraft world or settings objects are shared with chunk builders. */
public final class XrayPolicy {
    public enum Style { FADE, OUTLINES, ORES;
        public static Style parse(String label) { return "Terrain outlines".equals(label) ? OUTLINES : "Ores only".equals(label) ? ORES : FADE; }
    }
    public static final String[] GROUPS = {"diamond", "emerald", "gold", "iron", "copper", "lapis", "redstone", "coal", "quartz", "debris"};
    public static final int ALL_ORES = (1 << GROUPS.length) - 1;
    public static final int TERRAIN_ALPHA = 38;
    private static final Set<String> STORAGE = Set.of("chest", "trapped_chest", "ender_chest", "barrel", "hopper", "dispenser", "dropper", "furnace", "blast_furnace", "smoker", "brewing_stand", "crafter", "decorated_pot", "chiseled_bookshelf", "lectern", "beehive", "bee_nest", "campfire", "soul_campfire", "jukebox", "composter");
    private static final Set<String> WORKSTATIONS = Set.of("crafting_table", "furnace", "blast_furnace", "smoker", "brewing_stand", "enchanting_table", "anvil", "chipped_anvil", "damaged_anvil", "smithing_table", "cartography_table", "fletching_table", "stonecutter", "grindstone", "loom", "composter", "lectern", "crafter", "beacon");
    public record Snapshot(boolean enabled, Style style, int ores, boolean glow, int range, boolean workstations, boolean storage, boolean entities) {
        public Snapshot(boolean enabled, Style style, int ores, boolean glow, int range) {this(enabled, style, ores, glow, range, true, true, true);}
        public Snapshot { style = style == null ? Style.FADE : style; ores &= ALL_ORES; range = Math.clamp(range, 8, 48); }
        public boolean oreSelected(String blockId) { int group = oreGroup(blockId); return enabled && group >= 0 && (ores & 1 << group) != 0; }
        public boolean selected(String blockId) { return oreSelected(blockId) || enabled && (workstations && workstation(blockId) || storage && XrayPolicy.storage(blockId)); }
        public boolean hidden(String blockId) { return enabled && style != Style.FADE && !selected(blockId); }
        public int alpha(String blockId) { return !enabled || selected(blockId) ? 255 : style == Style.FADE ? TERRAIN_ALPHA : 0; }
        public int chunkKey() { return !enabled ? 0 : 1 | (style.ordinal() << 1) | (ores << 3) | (workstations ? 1 << 13 : 0) | (storage ? 1 << 14 : 0); }
    }
    private static String vanillaName(String id) {return id != null && id.startsWith("minecraft:") ? id.substring(10) : "";}
    public static boolean workstation(String id) {return WORKSTATIONS.contains(vanillaName(id));}
    public static boolean storage(String id) {String name = vanillaName(id); return STORAGE.contains(name) || name.equals("shulker_box") || name.endsWith("_shulker_box");}
    private XrayPolicy() { }
    public static int oreGroup(String id) {
        if (id == null || !id.startsWith("minecraft:")) return -1;
        String name = id.substring(10).toLowerCase(Locale.ROOT);
        if (name.startsWith("deepslate_")) name = name.substring(10);
        return switch (name) {
            case "diamond_ore" -> 0; case "emerald_ore" -> 1; case "gold_ore", "nether_gold_ore" -> 2;
            case "iron_ore" -> 3; case "copper_ore" -> 4; case "lapis_ore" -> 5; case "redstone_ore" -> 6;
            case "coal_ore" -> 7; case "nether_quartz_ore" -> 8; case "ancient_debris" -> 9; default -> -1;
        };
    }
}
