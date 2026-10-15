package dev.analyzer.core;

import java.util.ArrayList;
import java.util.Arrays;
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
        List<Setting<?>> values = new ArrayList<>(Arrays.asList(settings));
        switch (id) {
            case "place-animation" -> add(values, range(48));
            case "break-animation" -> add(values, Setting.choice("style", "Ghost style", "Dissolve", "Dissolve", "Chip", "Brackets"),
                range(48), Setting.number("size", "Debris size", 0.06, 0.02, 0.3, 0.01),
                Setting.number("spread", "Debris spread", 1, 0.2, 3, 0.1), Setting.number("gravity", "Debris gravity", 2.4, 0, 6, 0.2));
            case "place-preview" -> add(values, Setting.bool("edges", "Draw edges", true), Setting.bool("fill", "Draw fill", true),
                Setting.bool("show-blocked", "Show blocked placement", true), Setting.bool("glide", "Ease position changes", true));
            case "hit-particles" -> add(values, Setting.choice("style", "Particle shape", "Sparks", "Sparks", "Shards", "Gems", "Arc"),
                Setting.number("size", "Particle size", 0.06, 0.02, 0.3, 0.01), Setting.number("spread", "Spread", 1, 0.2, 3, 0.1),
                Setting.number("speed", "Particle speed", 1.3, 0.2, 3, 0.1), Setting.number("gravity", "Gravity", 2.4, 0, 6, 0.2),
                Setting.choice("who", "Hurt entities", "Everything", "Everything", "Players", "Self"), range(64));
            case "jump-circles" -> add(values, Setting.choice("style", "Ripple shape", "Ripple", "Ripple", "Rings", "Crown", "Tile"),
                Setting.choice("trigger", "Trigger", "Jump", "Jump", "Land", "Both"));
            case "trails" -> add(values, Setting.choice("style", "Path shape", "Ribbon", "Ribbon", "Beads", "Sparks"),
                Setting.number("height", "Trail height", 0.15, 0, 1.8, 0.05), Setting.number("size", "Bead size", 0.06, 0.02, 0.3, 0.01));
            case "damage-numbers" -> add(values, Setting.number("rise", "Rise distance", 0.8, 0.1, 2.5, 0.1),
                Setting.bool("distance", "Include distance", false));
            case "hit-markers" -> add(values, Setting.choice("style", "Marker shape", "Cross", "Cross", "Corners", "Dots"),
                Setting.number("thickness", "Thickness", 1, 1, 3, 1), Setting.number("spin", "Rotation speed", 0, 0, 5, 0.25));
            case "footsteps" -> add(values, Setting.choice("style", "Footprint shape", "Rings", "Rings", "Prints", "Petals"),
                Setting.number("size", "Print radius", 0.32, 0.1, 1, 0.02), Setting.number("max-prints", "Maximum prints", 24, 4, 64, 2));
            case "death-effects" -> add(values, Setting.choice("style", "Death burst", "Soul", "Soul", "Shatter", "Shockwave"),
                Setting.choice("who", "Entities", "Everything", "Everything", "Players", "Hostiles"), Setting.bool("self", "Own death", true), range(64));
            case "projectile-trails" -> add(values, Setting.choice("style", "Trail shape", "Line", "Line", "Comet", "Spiral"),
                Setting.bool("arrows", "Arrows and tridents", true), Setting.bool("pearls", "Ender pearls", true),
                Setting.bool("thrown", "Other thrown items", true), Setting.bool("other", "Other projectiles", true), range(64));
            case "pickup-streaks" -> add(values, Setting.number("size", "Pickup bead size", 0.06, 0.02, 0.3, 0.01),
                Setting.number("segments", "Streak length", 12, 4, 24, 2), Setting.choice("who", "Collectors", "Both", "Self", "Players", "Both"));
            case "teleport-effects" -> add(values, Setting.choice("style", "Arrival shape", "Rings", "Rings", "Vortex", "Portal"),
                Setting.number("size", "Radius", 1.5, 0.5, 4, 0.1), Setting.bool("link", "Connect departure and arrival", false));
            case "player-aura" -> add(values, Setting.choice("style", "Ring arrangement", "Orbit", "Orbit", "Gyroscope", "Rune"),
                Setting.bool("first-person", "Show own aura in first person", true));
            case "target-esp" -> add(values, Setting.choice("style", "Target shape", "Box", "Orbit", "Box", "Corners"),
                Setting.number("size", "Size multiplier", 1, 0.5, 2, 0.1),
                Setting.number("line-width", "Line width", 1, 0.5, 4, 0.25));
            case "hole-esp" -> add(values, Setting.choice("style", "Hole shape", "Box", "Box", "Floor outline"),
                Setting.number("line-width", "Line width", 1, 0.5, 4, 0.25));
            case "item-esp" -> add(values, Setting.choice("style", "Item shape", "Box", "Box", "Corners", "Rings"),
                Setting.number("line-width", "Line width", 1, 0.5, 4, 0.25));
            case "explosion-effects" -> add(values, Setting.choice("style", "Blast shape", "Nova", "Nova", "Implosion", "Rings"),
                Setting.number("count", "Spark count", 32, 0, 64, 4), Setting.bool("shockwave", "Shockwave", true), range(96));
            case "totem-pops" -> add(values, Setting.choice("style", "Pop shape", "Rings", "Rings", "Spiral", "Crown"),
                Setting.number("size", "Radius", 1.8, 0.5, 4, 0.1));
            case "landing-impact" -> add(values, Setting.choice("style", "Impact shape", "Ripple", "Ripple", "Crown"),
                Setting.number("count", "Debris count", 12, 0, 40, 2));
            case "health-pulse" -> add(values, Setting.choice("style", "Edge shape", "Edges", "Edges", "Corners", "Screen"));
            default -> { }
        }
        if (!id.equals("hit-sounds")) add(values,
            Setting.choice("color-mode", "Color mode", "Natural", "Natural", "Theme", "Custom", "Rainbow"),
            Setting.number("red", "Custom red", 121, 0, 255, 1), Setting.number("green", "Custom green", 248, 0, 255, 1),
            Setting.number("blue", "Custom blue", 204, 0, 255, 1), Setting.number("brightness", "Color brightness", 1, 0.25, 1.75, 0.05),
            Setting.number("color-speed", "Color cycle speed", 1, 0.1, 4, 0.1), Setting.bool("color-pulse", "Pulse brightness", false));
        return new ModuleDefinition(id, name, description, category, enabled, values);
    }
    private static void add(List<Setting<?>> values, Setting<?>... additions) { values.addAll(Arrays.asList(additions)); }
    public static List<ModuleDefinition> definitions() {
        return List.of(
            visual("place-animation", "Place Animation", "Ease a colored block ghost into each successful placement.", true,
                duration(0.7), Setting.choice("style", "Animation", "Bloom", "Bloom", "Rise", "Pop", "Drop", "Spin")),
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
            visual("target-esp", "Target ESP", "A clean debug box on your crosshair target or recently attacked entity, with optional orbit rings.", false,
                Setting.number("linger", "Attack highlight (seconds)", 2, 0, 5, 0.5), Setting.bool("through-walls", "Through walls", false)),
            visual("hole-esp", "Hole ESP", "Outline loaded single-block holes with resistant floors and walls.", false,
                Setting.number("radius", "Scan radius", 8, 3, 12, 1), Setting.bool("through-walls", "Through walls", true)),
            visual("trajectories", "Trajectories", "Predict a held bow, trident, snowball, egg or pearl path against loaded blocks.", false,
                Setting.number("steps", "Prediction steps", 80, 20, 160, 10), Setting.number("line-width", "Line width", 1.5, 0.5, 4, 0.5)),
            visual("item-esp", "Item ESP", "Clean debug boxes around loaded dropped items, with optional labels and ring markers.", false,
                range(64), Setting.bool("through-walls", "Through walls", true), Setting.bool("labels", "Item labels", false)),
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
                duration(0.7), Setting.number("intensity", "Intensity", 0.6, 0.1, 1, 0.1)),
            visual("air-effects", "Air Effects", "A bounded animated particle field around the camera, drawn in world space.", false,
                Setting.choice("style", "Ambient shape", "Fireflies", "Fireflies", "Embers", "Snow", "Stardust", "Cubes", "Network", "Rings"),
                Setting.number("density", "Particle count", 48, 12, 160, 4), Setting.number("radius", "Field radius", 12, 4, 24, 1),
                Setting.number("clear-radius", "Clear camera radius", 1.5, 0.5, 6, 0.25), Setting.number("size", "Particle size", 0.06, 0.02, 0.4, 0.01),
                Setting.number("speed", "Drift speed", 0.6, 0.1, 3, 0.1), Setting.number("wind", "Horizontal wind", 0.3, -2, 2, 0.1),
                Setting.number("link-range", "Network link distance", 3, 1, 6, 0.25), Setting.bool("through-walls", "Through walls", false)),
            visual("block-effects", "Block Effects", "Independent colored fragments or frames from actual placements and breaks.", false,
                Setting.bool("on-place", "On placement", true), Setting.bool("on-break", "On break", true),
                Setting.choice("style", "Fragment shape", "Crumble", "Crumble", "Shatter", "Frame"), Setting.bool("block-colors", "Use block map colors", true),
                Setting.number("count", "Fragment count", 24, 4, 64, 4), Setting.number("size", "Fragment size", 0.07, 0.02, 0.3, 0.01),
                duration(0.9), Setting.number("spread", "Spread", 1, 0.2, 3, 0.1), Setting.number("gravity", "Gravity", 2.4, 0, 6, 0.2), range(48)),
            visual("damage-effects", "Damage Effects", "Confirmed hurt bursts and an optional persistent low-health warning.", false,
                Setting.choice("who", "Hurt entities", "Everything", "Everything", "Players", "Self"),
                Setting.choice("style", "Hurt burst", "Shatter", "Bleed", "Shatter", "Shockwave"), Setting.number("count", "Particle count", 20, 4, 48, 2),
                Setting.number("size", "Particle size", 0.07, 0.02, 0.3, 0.01), duration(0.8),
                Setting.number("spread", "Spread", 1, 0.2, 3, 0.1), Setting.number("gravity", "Gravity", 2.4, 0, 6, 0.2),
                Setting.bool("damage-red", "Natural damage red", true), Setting.bool("low-health", "Low health edge pulse", true),
                Setting.number("threshold", "Low health (HP)", 6, 2, 20, 1), range(64)),
            visual("contrails", "Contrails", "Paired fading paths from real player motion, optionally restricted to elytra flight.", false,
                Setting.choice("style", "Trail shape", "Streamers", "Vapour", "Streamers", "Embers"),
                Setting.choice("who", "Players", "Self", "Self", "Players", "Both"), Setting.bool("elytra-only", "Only while gliding", true),
                Setting.number("min-speed", "Minimum speed (blocks/tick)", 0.25, 0.05, 2, 0.05), duration(2),
                Setting.number("line-width", "Trail width", 2, 0.5, 5, 0.5), Setting.number("wing-spread", "Wing offset", 0.7, 0.2, 2, 0.1),
                Setting.bool("speed-color", "Natural speed color", true), Setting.bool("first-person", "Show own in first person", false), range(96)),
            visual("storm-effects", "Storm Effects", "Branching bolts, ground ripples and a flash on actual loaded lightning strikes.", false,
                Setting.choice("style", "Strike shape", "Bolt", "Bolt", "Column", "Spear"), Setting.number("size", "Strike height", 32, 8, 64, 4),
                Setting.number("branches", "Branches", 4, 0, 8, 1), duration(0.8), Setting.bool("sparks", "Ground sparks", true),
                Setting.bool("scorch", "Ground ripple", true), Setting.number("scorch-time", "Ripple duration", 2, 0.5, 5, 0.25),
                Setting.bool("flash", "Screen flash", true), Setting.number("flash-strength", "Flash strength", 0.25, 0.05, 0.6, 0.05), range(128)),
            visual("vitals", "Vitals", "A local warning driven by real health, fire and remaining underwater air.", false,
                Setting.number("threshold", "Low health (HP)", 8, 2, 20, 1), Setting.choice("style", "Warning style", "Pulse", "Pulse", "Steady", "Corners"),
                Setting.number("strength", "Edge intensity", 0.6, 0.1, 1, 0.1), Setting.bool("heartbeat", "Heartbeat sound", false),
                Setting.bool("burning", "Warn while burning", true), Setting.bool("drowning", "Warn on low underwater air", true)),
            visual("hit-sounds", "Hit Sounds", "Local vanilla sound cues on confirmed hurts of a recently attacked target.", false,
                Setting.choice("sound", "Hit sound", "Ding", "Pop", "Punch", "Crit", "Tick", "Ding", "Orb", "Amethyst"),
                Setting.number("volume", "Volume", 0.65, 0, 1, 0.05), Setting.number("pitch", "Pitch", 1, 0.4, 2, 0.05),
                Setting.number("variation", "Pitch variation", 0.05, 0, 0.4, 0.01), Setting.bool("combo", "Raise pitch on a hit combo", true),
                Setting.choice("kill-sound", "Kill cue", "Lower", "Off", "Same", "Lower", "Ding"))
        );
    }
}
