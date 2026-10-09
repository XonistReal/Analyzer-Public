package dev.analyzer.core;

import java.util.List;

/** Execution settings for the explicit-start local task planner. */
public final class AutomationModules {
    private AutomationModules() { }
    public static List<ModuleDefinition> definitions() {
        return List.of(new ModuleDefinition("expedition", "Automation", "Review a local task plan. Safer routing and active Survival protection guard its normal actions.", Category.CLIENT, false, List.of(
                Setting.bool("safe-routing", "Use safer Baritone routes", true),
                Setting.bool("defense", "Protect against nearby hostile mobs", true),
                Setting.number("threat-range", "Threat detection range (blocks)", 12, 4, 24, 1),
                Setting.number("retreat-health", "Retreat below health", 10, 6, 20, 1),
                Setting.number("resume-delay", "Safe delay before resuming (seconds)", 3, 0.5, 10, 0.5),
                Setting.bool("auto-food", "Eat available food during tasks", true),
                Setting.number("eat-below", "Eat below hunger", 14, 6, 20, 1),
                Setting.number("min-health", "Pause below health", 6, 1, 20, 1),
                Setting.number("min-hunger", "Pause below hunger", 6, 0, 20, 1),
                Setting.number("stuck-timeout", "No-progress timeout (seconds)", 90, 15, 600, 5),
                Setting.bool("pause-on-menu", "Pause while other menus are open", true))));
    }
}
