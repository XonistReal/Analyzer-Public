package dev.analyzer.render;

public final class XrayPolicySelfTest {
    private static int checks;
    public static void main(String[] args) {
        var disabled = new XrayPolicy.Snapshot(false, XrayPolicy.Style.ORES, XrayPolicy.ALL_ORES, true, 24);
        check(!disabled.hidden("minecraft:stone") && disabled.alpha("minecraft:stone") == 255 && disabled.chunkKey() == 0, "Disabled rendering preserves ordinary terrain");
        for (var style : XrayPolicy.Style.values()) {
            var all = new XrayPolicy.Snapshot(true, style, XrayPolicy.ALL_ORES, true, 24);
            for (String ore : new String[]{"diamond_ore", "deepslate_diamond_ore", "iron_ore", "deepslate_iron_ore", "copper_ore", "deepslate_copper_ore", "gold_ore", "deepslate_gold_ore", "nether_gold_ore", "redstone_ore", "deepslate_redstone_ore", "emerald_ore", "deepslate_emerald_ore", "lapis_ore", "deepslate_lapis_ore", "coal_ore", "deepslate_coal_ore", "nether_quartz_ore", "ancient_debris"})
                check(all.selected("minecraft:" + ore) && !all.hidden("minecraft:" + ore) && all.alpha("minecraft:" + ore) == 255, style + " keeps the actual selected ore fully opaque: " + ore);
            check(!all.selected("minecraft:diamond_block") && !all.selected("other:diamond_ore"), "Manufactured/modded lookalikes are not classified as vanilla ores");
            check(all.alpha("minecraft:stone") == (style == XrayPolicy.Style.FADE ? 38 : 0), "Only Fade draws terrain at approximately fifteen percent opacity");
            check(all.hidden("minecraft:stone") == (style != XrayPolicy.Style.FADE), "Outline and ore-only styles suppress ordinary textured terrain");
        }
        var diamond = new XrayPolicy.Snapshot(true, XrayPolicy.Style.FADE, 1, false, 500);
        check(diamond.selected("minecraft:diamond_ore") && !diamond.selected("minecraft:gold_ore"), "Ore toggles select only their actual group");
        check(diamond.alpha("minecraft:gold_ore") == 38, "Deselected ores become ordinary faded terrain");
        check(diamond.range() == 48 && new XrayPolicy.Snapshot(true, null, -1, true, -20).range() == 8, "Overlay work is bounded independently of loaded-world rendering");
        check(XrayPolicy.Style.parse("Fade terrain") == XrayPolicy.Style.FADE && XrayPolicy.Style.parse("Terrain outlines") == XrayPolicy.Style.OUTLINES && XrayPolicy.Style.parse("Ores only") == XrayPolicy.Style.ORES, "All three saved styles resolve exactly");
        check(diamond.chunkKey() != disabled.chunkKey() && diamond.chunkKey() != new XrayPolicy.Snapshot(true, XrayPolicy.Style.ORES, 1, false, 24).chunkKey(), "Enable and style changes invalidate chunk geometry");
        check(diamond.chunkKey() == new XrayPolicy.Snapshot(true, XrayPolicy.Style.FADE, 1, true, 8).chunkKey(), "Overlay-only preferences do not rebuild textured chunks unnecessarily");
        for (var style : XrayPolicy.Style.values()) {
            var targets = new XrayPolicy.Snapshot(true, style, 0, false, 24);
            for (String name : new String[]{"chest", "trapped_chest", "ender_chest", "barrel", "hopper", "dispenser", "dropper", "furnace", "blast_furnace", "smoker", "brewing_stand", "crafter", "decorated_pot", "chiseled_bookshelf", "lectern", "beehive", "bee_nest", "campfire", "soul_campfire", "shulker_box", "crafting_table", "enchanting_table", "anvil", "chipped_anvil", "damaged_anvil", "smithing_table", "cartography_table", "fletching_table", "stonecutter", "grindstone", "loom", "composter"})
                check(targets.selected("minecraft:" + name) && !targets.hidden("minecraft:" + name) && targets.alpha("minecraft:" + name) == 255, "Visible utility model retains full opacity in " + style + ": " + name);
            for (String dye : new String[]{"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"})
                check(targets.selected("minecraft:" + dye + "_shulker_box"), "Every vanilla colored storage box is visible in " + style);
            var none = new XrayPolicy.Snapshot(true, style, 0, false, 24, false, false, false);
            check(targets.selected("minecraft:jukebox") && targets.selected("minecraft:composter") && targets.selected("minecraft:beacon"), "Single-slot disc storage, compost output and beacon workstation remain visible in " + style);
            check(!none.selected("minecraft:crafting_table") && !none.selected("minecraft:chest") && !none.selected("minecraft:furnace"), "Utility visibility filters are independent of ore groups");
            check(none.chunkKey() != targets.chunkKey(), "Utility filter changes rebuild textured geometry");
            check(new XrayPolicy.Snapshot(true, style, 0, false, 24, true, true, false).chunkKey() == targets.chunkKey(), "Entity highlight changes do not rebuild block chunks");
        }
        System.out.println("XrayPolicySelfTest: " + checks + " assertions passed");
    }
    private static void check(boolean condition, String message) {if (!condition) throw new AssertionError(message); checks++;}
}
