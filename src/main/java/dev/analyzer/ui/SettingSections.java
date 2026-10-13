package dev.analyzer.ui;

import dev.analyzer.core.Category;
import dev.analyzer.core.AnalyzerCore;
import dev.analyzer.core.ModuleDefinition;
import dev.analyzer.core.Setting;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Group long gameplay inspectors without changing serialized setting order or IDs. */
final class SettingSections {
    private SettingSections() { }
    static String group(ModuleDefinition module, Setting<?> setting) {
        if (module.id().equals("hud")) return setting.id().startsWith("menu-") ? "Menu appearance" : setting.id().startsWith("home-") ? "Home screen" : "HUD appearance";
        if (module.id().equals("entity-esp")) {
            String id = setting.id();
            if (id.equals("players") || id.equals("hostiles") || id.equals("passives") || id.equals("items")) return "Targets";
            if (id.equals("through-walls") || id.equals("line-width") || id.equals("style") || id.equals("min-marker-pixels") || id.equals("range")) return "Boxes and visibility";
            return "Labels and distance";
        }
        if (module.category() != Category.COMBAT && module.category() != Category.HVH && module.category() != Category.MOVEMENT) return "";
        String id = setting.id();
        if (id.equals("players") || id.equals("hostiles") || id.equals("passives") || id.equals("other-living")
                || id.startsWith("ignore-") || id.equals("fov") || id.equals("range") || id.equals("priority")
                || id.equals("aim-range") || id.equals("wall-range") || id.equals("targets") || id.equals("target-mode")) return "Targeting";
        if (id.contains("damage") || id.contains("suicide") || id.startsWith("pause-") || id.equals("health")
                || id.equals("self-distance") || id.startsWith("face-place")) return "Safety";
        if (id.contains("cps") || id.contains("cooldown") || id.contains("jitter") || id.contains("delay")
                || id.contains("interval") || id.equals("timing") || id.contains("charge") || id.equals("wait-for-crit")) return "Timing";
        if (id.contains("rotation") || id.equals("rotate") || id.equals("turn-speed") || id.equals("smoothing")
                || id.startsWith("aim-") || id.equals("hit-angle") || id.equals("swing")) return "Aim and presentation";
        if (module.category() == Category.HVH && !module.id().equals("target-strafe")) return "Placement and behavior";
        return "General";
    }
    static List<Setting<?>> ordered(AnalyzerCore core, ModuleDefinition module) {
        Map<String, List<Setting<?>>> groups = new LinkedHashMap<>();
        if (module.id().equals("hud")) groups.put("Menu appearance", new ArrayList<>());
        for (Setting<?> setting : module.settings()) if (visible(core, module, setting)) groups.computeIfAbsent(group(module, setting), key -> new ArrayList<>()).add(setting);
        return groups.values().stream().flatMap(List::stream).toList();
    }
    static int headingCount(AnalyzerCore core, ModuleDefinition module) {
        return (int) ordered(core, module).stream().map(setting -> group(module, setting)).filter(value -> !value.isEmpty()).distinct().count();
    }
    private static boolean enabled(AnalyzerCore core, ModuleDefinition module, String id) {
        return module.settings().stream().anyMatch(setting -> setting.id().equals(id)) && core.getBoolean(module.id(), id);
    }
    private static String choice(AnalyzerCore core, ModuleDefinition module, String id) {
        return module.settings().stream().anyMatch(setting -> setting.id().equals(id)) ? core.getChoice(module.id(), id) : "";
    }
    /** Hide values belonging to another mode while keeping their saved values intact. */
    static boolean visible(AnalyzerCore core, ModuleDefinition module, Setting<?> setting) {
        String id = setting.id(), name = module.id();
        boolean legit = enabled(core, module, "legit");
        if (name.equals("kill-aura")) {
            if (id.equals("switch-delay")) return !legit && choice(core, module, "mode").equals("Switch");
            if (id.equals("max-targets")) return !legit && choice(core, module, "mode").equals("Multi");
        }
        if (name.equals("speed") && (id.equals("speed") || id.equals("multiplier"))) return !legit && !choice(core, module, "profile").equals("Sprint hop");
        if (name.equals("fly")) {
            if (id.equals("rocket-delay")) return legit;
            if (id.equals("pulse-interval")) return !legit && enabled(core, module, "anti-hover") && choice(core, module, "mode").equals("Vanilla");
            if (id.equals("mode") || id.equals("speed") || id.equals("vertical-speed") || id.equals("anti-hover")) return !legit;
        }
        if (name.equals("ground-strafe") && id.equals("speed")) return !legit && choice(core, module, "mode").equals("Steady");
        if (legit && (name.equals("high-jump") || name.equals("long-jump") || name.equals("fast-fall") || name.equals("reverse-step") || name.equals("fast-ladder")))
            if (id.equals("height") || id.equals("speed") || id.equals("boost") || id.equals("trigger-fall") || id.equals("max-drop") || id.equals("jump-multiplier")) return false;
        if (name.equals("elytra-boost")) {
            boolean rockets = legit || choice(core, module, "mode").equals("Rockets");
            if (id.equals("rocket-below") || id.equals("rocket-delay")) return rockets;
            if (id.equals("thrust") || id.equals("climb") || id.equals("max-speed")) return !rockets;
        }
        if (name.equals("criticals") && id.equals("hop-height")) return !legit && choice(core, module, "mode").equals("Mini jump");
        if (name.equals("bow-aimbot") && id.equals("lead-factor")) return enabled(core, module, "predict-movement") && !choice(core, module, "prediction").equals("Direct");
        if (name.equals("crystal-aura")) {
            if (id.equals("place-delay")) return enabled(core, module, "place");
            if (id.equals("break-delay") || id.equals("break-range") || id.equals("break-which") || id.equals("cooldown")) return enabled(core, module, "break");
        }
        if (name.equals("auto-web") && id.equals("motion-threshold")) return enabled(core, module, "moving-only");
        if (module.category() == Category.HVH) {
            if (id.equals("red") || id.equals("green") || id.equals("blue")) return enabled(core, module, "render");
            if (id.equals("aim-speed") || id.equals("aim-smoothing")) return legit || enabled(core, module, "rotate");
        }
        if (name.equals("target-strafe")) {
            String style = choice(core, module, "style");
            if (id.equals("hug-distance")) return style.equals("Hug");
            if (id.equals("hop-arc") || id.equals("view-cone")) return style.equals("Blind hop");
            if (id.equals("weave")) return !style.equals("Circle");
            if (id.equals("direction-stability")) return choice(core, module, "direction").equals("Flip");
        }
        return true;
    }
}
