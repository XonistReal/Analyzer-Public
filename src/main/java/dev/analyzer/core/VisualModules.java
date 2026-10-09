package dev.analyzer.core;

import java.util.List;

/** Original cosmetic modules, backed by client interaction/state events and bounded rendering. */
public final class VisualModules {
    private VisualModules() { }
    private static Setting<Double> duration(double value) {
        return Setting.number("duration", "Duration (seconds)", value, 0.2, 3, 0.1);
    }
    private static Setting<Double> range(double value) {
        return Setting.number("range", "Range", value, 8, 128, 8);
    }
    private static ModuleDefinition visual(String id, String name, String description, boolean enabled, Setting<?>... settings) {
        Category category = switch (id) {
            case "block-highlight", "hole-esp", "trajectories", "item-esp", "nametags", "target-esp" -> Category.VISUAL;
            default -> Category.COSMETICS;
        };
        return new ModuleDefinition(id, name, description, category, enabled, List.of(settings));
    }
    public static List<ModuleDefinition> definitions() {
        return List.of(
            visual("place-animation", "Place Animation", "Ease a colored block ghost into each successful placement.", true,
                duration(0.7), Setting.choice("style", "Animation", "Bloom", "Bloom", "Rise", "Pop")),
            visual("break-animation", "Break Animation", "Mining progress, dissolving block ghosts and debris on successful breaks.", true,
                duration(0.8), Setting.number("debris", "Debris count", 20, 4, 48, 4), Setting.bool("progress", "Mining progress", true)),
            visual("place-preview", "Place Preview", "Show the actual adjacent or replaceable block position for your held block.", false,
                Setting.bool("through-walls", "Through walls", false), Setting.number("opacity", "Opacity", 0.35, 0.05, 0.7, 0.05)),
            visual("block-highlight", "Block Highlight", "A pulsing outline of the block under your crosshair.", false,
                Setting.number("line-width", "Line width", 2, 0.5, 4, 0.5), Setting.bool("pulse", "Pulse", true)),
            visual("hit-particles", "Hit Particles", "Colored sparks when loaded living entities are actually hurt.", true,
                duration(0.7), Setting.number("count", "Spark count", 18, 4, 48, 2)),
            visual("jump-circles", "Jump Circles", "Expanding floor ripples when you leave the ground with upward velocity.", false,
                duration(1.0), Setting.number("size", "Radius", 1.3, 0.4, 3, 0.1)),
            visual("trails", "Trails", "A fading ribbon following your actual movement.", false,
                duration(1.2), Setting.number("line-width", "Line width", 2, 0.5, 5, 0.5)),
            visual("damage-numbers", "Damage Numbers", "Floating numbers from actual health decreases of loaded living entities.", false,
                duration(1.4), Setting.number("scale", "Text size", 1, 0.5, 2, 0.1)),
            visual("hit-markers", "Hit Markers", "A brief crosshair flash when your recently attacked target is hurt.", false,
                duration(0.4), Setting.number("size", "Marker size", 7, 3, 16, 1)),
            visual("footsteps", "Footsteps", "Small fading floor rings spaced along grounded movement.", false,
                duration(1.3), Setting.number("spacing", "Step spacing", 0.7, 0.3, 1.5, 0.1)),
            visual("death-effects", "Death Effects", "A rising particle burst when a loaded living entity dies.", false,
                duration(1.6), Setting.number("count", "Particle count", 28, 8, 48, 4)),
            visual("projectile-trails", "Projectile Trails", "Fading paths behind actual loaded projectiles.", false,
                duration(1.5), Setting.number("line-width", "Line width", 1.5, 0.5, 4, 0.5)),
            visual("pickup-streaks", "Pickup Streaks", "A curved streak from an item to the collector on real pickup packets.", false,
                duration(0.6), Setting.number("line-width", "Line width", 2, 0.5, 4, 0.5)),
            visual("teleport-effects", "Teleport Effects", "Paired energy rings on large local position changes.", false,
                duration(1.2), Setting.number("threshold", "Minimum distance", 8, 4, 32, 1)),
            visual("player-aura", "Player Aura", "Rotating rings around your actual player position.", false,
                Setting.number("size", "Radius", 0.9, 0.4, 2, 0.1), Setting.number("speed", "Orbit speed", 1, 0.2, 3, 0.1)),
            visual("target-esp", "Target ESP", "Orbiting target rings on your crosshair target or recently attacked entity.", false,
                Setting.number("linger", "Attack highlight (seconds)", 2, 0, 5, 0.5), Setting.bool("through-walls", "Through walls", false)),
            visual("hole-esp", "Hole ESP", "Outline loaded single-block holes with resistant floors and walls.", false,
                Setting.number("radius", "Scan radius", 8, 3, 12, 1), Setting.bool("through-walls", "Through walls", true)),
            visual("trajectories", "Trajectories", "Predict a held bow, trident, snowball, egg or pearl path against loaded blocks.", false,
                Setting.number("steps", "Prediction steps", 80, 20, 160, 10), Setting.number("line-width", "Line width", 1.5, 0.5, 4, 0.5)),
            visual("item-esp", "Item ESP", "Independent glowing rings and beams on loaded dropped items.", false,
                range(64), Setting.bool("through-walls", "Through walls", true), Setting.bool("labels", "Item labels", true)),
            visual("nametags", "Nametags", "Independent readable names, health and distances for loaded players and mobs.", false,
                range(64), Setting.bool("players", "Players", true), Setting.bool("mobs", "Mobs", true),
                Setting.bool("through-walls", "Through walls", true), Setting.number("scale", "Text size", 1, 0.5, 1.5, 0.1)),
            visual("explosion-effects", "Explosion Effects", "Shockwave rings and sparks from actual nearby explosion packets.", false,
                duration(1.2), Setting.number("size", "Shockwave radius", 4, 1, 8, 0.5)),
            visual("totem-pops", "Totem Pops", "Golden rings, sparks and a floating pop label on real totem-use statuses.", false,
                duration(1.5), Setting.number("count", "Spark count", 30, 8, 48, 2)),
            visual("landing-impact", "Landing Impact", "A floor shockwave when a falling player lands.", false,
                duration(0.8), Setting.number("size", "Radius", 1.5, 0.5, 3, 0.1)),
            visual("health-pulse", "Health Pulse", "A fading edge vignette when your local health decreases.", false,
                duration(0.7), Setting.number("intensity", "Intensity", 0.6, 0.1, 1, 0.1))
        );
    }
}
