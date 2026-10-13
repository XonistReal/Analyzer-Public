package dev.analyzer.core;

import java.util.List;

/** Movement controls inspired by the public Temple catalogue; existing numeric IDs retain their defaults. */
public final class MovementSettings {
    private MovementSettings() { }
    public static List<ModuleDefinition> definitions() {
        return List.of(
                module("keep-sprint", "Keep Sprint", "Keep sprinting while vanilla movement and hunger conditions permit.",
                        Setting.bool("legit", "Use the normal sprint key", true),
                        Setting.number("min-hunger", "Sprint above hunger", 6, 0, 20, 1),
                        Setting.bool("pause-on-use", "Pause while using items", true),
                        Setting.bool("ground-only", "Only sprint on the ground", false)),
                module("auto-walk", "Auto Walk", "Hold a chosen movement key while the world is focused; input is released on pause or disable.",
                        Setting.choice("direction", "Direction", "Forward", "Forward", "Backward", "Left", "Right"),
                        Setting.bool("sprint", "Sprint when walking forward", false),
                        Setting.bool("jump", "Hold jump", false),
                        Setting.bool("stop-on-collision", "Stop at a wall", false)),
                module("speed", "Speed", "Choose direct motion, a grounded conservative profile, or normal sprint jumps.",
                        Setting.bool("legit", "Use normal sprint and jump keys", false),
                        Setting.choice("profile", "Profile", "Standard", "Standard", "Conservative", "Sprint hop"),
                        rate("speed", "Base speed (blocks/tick)", .35, .1, 1),
                        Setting.number("multiplier", "Multiplier (direct profiles)", 1, 1, 4, .1)),
                module("fly", "Fly", "Client flight or descending glide; Legit uses an equipped elytra and hotbar rockets. Servers may correct direct motion.",
                        Setting.bool("legit", "Use elytra and normal rockets", false),
                        Setting.choice("mode", "Mode", "Vanilla", "Vanilla", "Glide", "Conservative glide"),
                        rate("speed", "Horizontal speed (blocks/tick)", .35, .05, 1),
                        rate("vertical-speed", "Vertical speed (blocks/tick)", .35, .05, 1),
                        Setting.bool("anti-hover", "Periodic downward hover pulse", false),
                        Setting.number("pulse-interval", "Hover pulse interval (ticks)", 40, 10, 100, 1),
                        Setting.number("rocket-delay", "Legit rocket interval (ticks)", 30, 10, 100, 1)),
                module("step", "Step", "Raise the client step height, or use a normal jump at a checked one-block ledge.",
                        Setting.bool("legit", "Use the normal jump key", false),
                        Setting.choice("mode", "Mode", "Instant", "Instant", "Jump"),
                        rate("height", "Maximum step height (blocks)", 1, .6, 2.5)),
                module("parkour", "Parkour", "Jump near a loaded unsupported edge while moving on the ground.",
                        Setting.bool("legit", "Use the normal jump key", false),
                        Setting.bool("sprint", "Sprint jumps", true),
                        rate("look-ahead", "Edge look-ahead (blocks)", .65, .2, 1)),
                module("safe-walk", "Safe Walk", "Clip movement at ledges, or hold normal sneak near an edge.",
                        Setting.bool("legit", "Sneak at edges using normal input", false),
                        Setting.bool("only-sneaking", "Only while the sneak key is held", false)),
                module("fast-ladder", "Fast Ladder", "Increase climbing velocity; Legit holds normal forward and jump on climbable blocks.",
                        Setting.bool("legit", "Use normal forward and jump keys", false),
                        rate("speed", "Climb speed (blocks/tick)", .28, .1, .6),
                        Setting.number("jump-multiplier", "Speed while holding jump", 1, 1, 2, .1)),
                module("reverse-step", "Reverse Step", "Accelerate an existing descent only toward a loaded floor within the chosen drop. Legit sneaks through scaffolding.",
                        Setting.bool("legit", "Drop through scaffolding with sneak", false),
                        rate("speed", "Downward speed (blocks/tick)", .8, .1, 2),
                        rate("max-drop", "Maximum nearby drop (blocks)", 1.5, 1, 6)),
                module("fast-fall", "Fast Fall", "Accelerate an existing fall after a distance threshold. Legit only drops through scaffolding.",
                        Setting.bool("legit", "Drop through scaffolding with sneak", false),
                        rate("speed", "Downward speed (blocks/tick)", 1, .2, 3),
                        rate("trigger-fall", "Trigger fall distance (blocks)", 1, .25, 10)),
                module("glide", "Glide", "Limit falling velocity outside fluids; Legit opens an equipped usable elytra.",
                        Setting.bool("legit", "Open an equipped elytra normally", false),
                        rate("speed", "Fall speed (blocks/tick)", .1, .02, .5)),
                module("high-jump", "High Jump", "Increase an ordinary jump's upward velocity; Legit pillars with normal hotbar block use while jump is held.",
                        Setting.bool("legit", "Pillar with normal jumps and blocks", false),
                        rate("height", "Upward velocity (blocks/tick)", .7, .42, 1.5)),
                module("long-jump", "Long Jump", "Boost the start of a directional jump; Legit sprint-jumps near an edge without modifying velocity.",
                        Setting.bool("legit", "Normal sprint jump at a ledge", false),
                        rate("speed", "Base jump speed (blocks/tick)", .65, .2, 1.5),
                        Setting.number("boost", "Horizontal boost multiplier", 1, .5, 3, .1),
                        rate("height", "Minimum jump velocity", .42, .2, 1)),
                module("elytra-boost", "Elytra Boost", "Add a bounded gliding impulse and optional climb, or use real hotbar rockets when flight slows.",
                        Setting.bool("legit", "Use normal hotbar rocket interactions", false),
                        Setting.choice("mode", "Mode", "Impulse", "Impulse", "Rockets"),
                        rate("thrust", "Impulse per tick", .035, .005, .3),
                        rate("climb", "Space climb velocity", 0, 0, 1.5),
                        rate("max-speed", "Maximum impulse speed", 3, .5, 5),
                        Setting.bool("take-off", "Jump and open equipped elytra", false),
                        rate("rocket-below", "Use rocket below speed", 1, .1, 3),
                        Setting.number("rocket-delay", "Rocket interval (ticks)", 30, 10, 100, 1)),
                module("ground-strafe", "Ground Strafe", "Align existing ground momentum, set a steady ground speed, or use ordinary sprint hops.",
                        Setting.bool("legit", "Use normal sprint and jump keys", false),
                        Setting.choice("mode", "Mode", "Momentum", "Momentum", "Steady"),
                        rate("speed", "Steady ground speed (blocks/tick)", .32, .1, .8)));
    }
    private static Setting<Double> rate(String id, String label, double value, double min, double max) {
        return Setting.number(id, label, value, min, max, .01);
    }
    private static ModuleDefinition module(String id, String name, String description, Setting<?>... settings) {
        return new ModuleDefinition(id, name, description, Category.MOVEMENT, false, List.of(settings));
    }
}
