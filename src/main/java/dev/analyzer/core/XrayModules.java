package dev.analyzer.core;

import java.util.ArrayList;
import java.util.List;

/** Loaded-world textured terrain visibility, separate from Ore ESP markers. */
public final class XrayModules {
    private XrayModules() { }
    public static List<ModuleDefinition> definitions() {
        List<Setting<?>> settings = new ArrayList<>(List.of(
                Setting.choice("style", "World style", "Fade terrain", "Fade terrain", "Terrain outlines", "Ores only"),
                Setting.number("range", "Detail range", 24, 8, 48, 4),
                Setting.bool("glow", "Subtle ore glow in Fade terrain", true),
                Setting.bool("workstations", "Keep workstations visible", true),
                Setting.bool("storage", "Keep storage containers visible", true),
                Setting.bool("entities", "Entity highlights", true)));
        for (String[] ore : new String[][]{{"diamond","Diamond"},{"emerald","Emerald"},{"gold","Gold"},{"iron","Iron"},{"copper","Copper"},{"lapis","Lapis"},{"redstone","Redstone"},{"coal","Coal"},{"quartz","Nether quartz"},{"debris","Ancient debris"}})
            settings.add(Setting.bool(ore[0], ore[1] + " ores", true));
        return List.of(new ModuleDefinition("xray", "X-ray", "Real ore textures in loaded terrain: 15% terrain, fine terrain outlines, or ores only. Disabling restores ordinary chunks.", Category.VISUAL, false, settings));
    }
}
