package dev.analyzer.core;

import java.util.List;

/** Execution settings for the explicit-start local task planner. */
public final class AutomationModules {
    private AutomationModules() { }
    public static List<ModuleDefinition> definitions() {
        return List.of(new ModuleDefinition("expedition", "Automation", "Review a local task plan. Safer routing and active Survival protection guard its normal actions.", Category.CLIENT, false, List.of(
                Setting.bool("safe-routing", "Use cautious native routes", true),
                Setting.number("path-budget", "Path search nodes per tick", 256, 64, 512, 64),
                Setting.number("path-radius", "Path segment radius (blocks)", 24, 8, 48, 4),
                Setting.number("max-drop", "Maximum planned drop (blocks)", 1, 1, 3, 1),
                Setting.bool("smooth-turns", "Smooth visible turning", true),
                Setting.number("turn-speed", "Turning speed (degrees / second)", 150, 30, 720, 10),
                Setting.number("turn-acceleration", "Turning acceleration (degrees / second²)", 600, 60, 3600, 60),
                Setting.number("movement-angle", "Move within heading angle (degrees)", 35, 5, 60, 5),
                Setting.number("break-delay", "Delay between broken blocks (ticks)", 5, 1, 20, 1),
                Setting.number("place-delay", "Delay between block placements (ticks)", 4, 1, 20, 1),
                Setting.bool("auto-site", "Find a suitable nearby build site", true),
                Setting.number("site-radius", "Build site search radius (blocks)", 24, 8, 64, 4),
                Setting.bool("schematic-preview", "Show selected schematic ghost", true),
                Setting.number("preview-opacity", "Schematic ghost opacity", .3, .1, .8, .05),
                Setting.choice("preview-style", "Schematic preview style", "Ghost", "Ghost", "Outline"),
                Setting.bool("defense", "Protect against nearby hostile mobs", true),
                Setting.bool("proactive-avoidance", "Avoid hostile patrols before contact", true),
                Setting.number("avoid-range", "Proactive threat range (blocks)", 16, 8, 32, 1),
                Setting.number("escape-radius", "Escape route search radius (blocks)", 12, 4, 24, 1),
                Setting.bool("prefer-cover", "Prefer cover from ranged mobs", true),
                Setting.number("threat-range", "Threat detection range (blocks)", 12, 4, 24, 1),
                Setting.number("retreat-health", "Retreat below health", 10, 6, 20, 1),
                Setting.number("resume-delay", "Safe delay before resuming (seconds)", 3, 0.5, 10, 0.5),
                Setting.bool("auto-food", "Eat available food during tasks", true),
                Setting.number("eat-below", "Eat below hunger", 14, 6, 20, 1),
                Setting.number("min-health", "Pause below health", 6, 1, 20, 1),
                Setting.number("min-hunger", "Pause below hunger", 6, 0, 20, 1),
                Setting.number("stuck-timeout", "No-progress timeout (seconds)", 90, 15, 600, 5),
                Setting.bool("pause-on-menu", "Pause while other menus are open", true),
                Setting.bool("pause-on-input", "Yield to manual movement or action input", true))));
    }
}
