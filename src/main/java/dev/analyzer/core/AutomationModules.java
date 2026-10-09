package dev.analyzer.core;

import java.util.List;

/** Execution settings for the explicit-start local task planner. */
public final class AutomationModules {
    private AutomationModules() { }
    public static List<ModuleDefinition> definitions() {
        return List.of(new ModuleDefinition("expedition", "Automation", "Review a local task plan, then let Baritone and normal inventory actions carry it out.", Category.CLIENT, false, List.of(
                Setting.number("min-health", "Pause below health", 6, 1, 20, 1),
                Setting.number("min-hunger", "Pause below hunger", 6, 0, 20, 1),
                Setting.number("stuck-timeout", "No-progress timeout (seconds)", 90, 15, 600, 5),
                Setting.bool("pause-on-menu", "Pause while other menus are open", true))));
    }
}
