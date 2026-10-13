package dev.analyzer.core;

import java.util.ArrayList;
import java.util.List;

/** Supported HVH controls. Existing IDs/defaults remain stable across profile upgrades. */
public final class HvhSettings {
    private HvhSettings() { }
    public static List<ModuleDefinition> definitions() {
        List<ModuleDefinition> result = new ArrayList<>();
        List<Setting<?>> crystal = targets();
        crystal.add(n("delay", "Minimum action interval (ticks)", 4, 1, 20, 1));
        crystal.addAll(List.of(b("place", "Place crystals", true), b("break", "Break crystals", true),
                n("cooldown", "Break cooldown threshold", .95, .2, 1, .05),
                n("self-distance", "Minimum self distance (blocks)", 3, 2, 6, .25),
                n("health", "Minimum health (HP)", 12, 1, 20, 1),
                n("place-delay", "Placement interval floor (ticks)", 1, 0, 20, 1),
                n("break-delay", "Break interval floor (ticks)", 1, 0, 20, 1),
                n("break-range", "Crystal break range (blocks)", 4.5, 1, 6, .25),
                c("break-which", "Break which crystals", "Smart", "Smart", "Own", "All"),
                n("face-place-health", "Face place below enemy health (HP; 0 off)", 8, 0, 36, 1),
                b("face-place-hole", "Face place enemies in holes", false)));
        interactions(crystal, 30); damage(crystal, 6, 8);
        add(result, "crystal-aura", "Crystal Aura", "Score reachable crystals with loaded-world explosion exposure and conservative self/friend damage guards.", crystal);

        List<Setting<?>> anchor = targets();
        anchor.addAll(List.of(n("delay", "Action interval (ticks)", 4, 1, 20, 1),
                n("self-distance", "Minimum self distance (blocks)", 3.5, 2, 6, .25),
                n("health", "Minimum health (HP)", 12, 1, 20, 1),
                b("use-existing", "Use existing anchors", true)));
        interactions(anchor, 30); damage(anchor, 7, 7);
        add(result, "anchor-aura", "Anchor Aura", "Place, charge and detonate reachable anchors outside anchor-safe dimensions, with exposure damage guards.", anchor);

        List<Setting<?>> surround = new ArrayList<>(List.of(n("delay", "Placement interval (ticks)", 3, 1, 20, 1),
                b("support", "Build lower supports", true), c("shape", "Surround shape", "Normal", "Normal", "Double", "Anti-city"),
                b("center", "Ease toward block center", true), b("floor", "Fill missing floor", true),
                b("extend", "Surround every occupied block", true), b("self-trap", "Cover above your head", false),
                b("off-jump", "Disable on upward jump", true), b("off-moved", "Disable after leaving start block", true),
                b("off-done", "Disable when complete", false)));
        placement(surround, true);
        add(result, "surround", "Surround", "Build a supported configurable shell around your occupied blocks using normal placement.", surround);

        List<Setting<?>> trap = targets(); trap.add(n("delay", "Action interval (ticks)", 4, 1, 20, 1));
        trap.addAll(List.of(c("shape", "Trap shape", "Full + roof", "Top", "Head", "Full", "Full + roof"),
                b("only-hole", "Only targets in holes", false), b("off-done", "Disable when cage complete", false)));
        placement(trap, true);
        add(result, "auto-trap", "Auto Trap", "Build a supported roof, head shell or full cage around a selected target.", trap);

        List<Setting<?>> web = targets(); web.add(n("delay", "Action interval (ticks)", 4, 1, 20, 1));
        web.addAll(List.of(b("feet", "Web target feet", true), b("head", "Web target head", false),
                b("every-enemy", "Web every selected enemy", false), b("moving-only", "Only moving targets", false),
                n("motion-threshold", "Moving threshold (blocks/tick)", .03, .01, .3, .01)));
        placement(web, false);
        add(result, "auto-web", "Auto Web", "Place supported feet/head webs on selected enemies within reach and a per-tick budget.", web);

        List<Setting<?>> holes = new ArrayList<>(List.of(n("range", "Search range (blocks)", 4, 1, 5, .5),
                n("delay", "Placement interval (ticks)", 4, 1, 20, 1),
                b("near-enemies", "Only holes near enemies", true), n("enemy-distance", "Enemy distance to hole (blocks)", 3, 1, 8, .25),
                b("keep-yours", "Keep holes beside you", true), b("double-holes", "Include two-block holes", true),
                n("scan-delay", "Scan interval (ticks)", 4, 1, 20, 1), b("protect-friends", "Keep holes beside friends", true)));
        placement(holes, true);
        add(result, "hole-filler", "Hole Filler", "Scan loaded nearby supported single/double holes and fill relevant unoccupied holes.", holes);

        List<Setting<?>> strafe = targets();
        strafe.addAll(List.of(n("radius", "Orbit radius (blocks)", 2.5, 1, 5, .25),
                n("speed", "Horizontal speed cap (blocks/tick)", .22, .05, .6, .01), b("clockwise", "Clockwise", true),
                c("style", "Strafe style", "Circle", "Circle", "Vanilla hop", "Blind hop", "Hug"),
                c("direction", "Direction override", "Saved clockwise", "Saved clockwise", "Right", "Left", "Flip"),
                b("respect-wasd", "A/D direction and S pause", true), b("ground-only", "Steer only on ground", true),
                b("auto-jump", "Jump while orbiting", false), b("natural-pace", "Ease at normal movement pace", true),
                b("sprint", "Use sprint pace", false), n("hug-distance", "Hug distance (blocks)", .4, .2, 1.5, .05),
                b("weave", "Reverse direction after landing", true), n("hop-arc", "Blind hop arc (degrees)", 55, 20, 120, 5),
                n("view-cone", "Avoid target front cone (degrees)", 120, 30, 180, 5),
                n("direction-stability", "Direction stability (%)", 20, 0, 100, 5),
                b("legit", "Camera + ordinary forward input", false), n("aim-speed", "Camera turn speed (degrees/tick)", 30, 5, 90, 1)));
        add(result, "target-strafe", "Target Strafe", "Orbit a selected target with checked support, collision, ordinary hops and optional camera/input steering.", strafe);
        return List.copyOf(result);
    }
    private static List<Setting<?>> targets() {
        return new ArrayList<>(List.of(b("players", "Players", true), b("hostiles", "Hostile mobs", false),
                b("passives", "Passive mobs", false), n("range", "Target range (blocks)", 6, 1, 8, .25),
                n("fov", "Field of view (degrees)", 180, 20, 360, 10), b("ignore-naked", "Ignore unarmored players", false),
                b("ignore-friends", "Ignore friends", true), b("ignore-invisible", "Ignore invisible targets", false),
                c("priority", "Target priority", "Distance", "Distance", "Health", "Armor"), b("through-walls", "Target through walls", false)));
    }
    private static void interactions(List<Setting<?>> settings, double speed) {
        settings.addAll(List.of(n("place-range", "Placement reach cap (blocks)", 4.5, 1, 6, .25),
                b("legit", "Require real crosshair + vanilla reach", false), b("rotate", "Turn real camera to interaction", false),
                n("aim-speed", "Camera turn speed (degrees/tick)", speed, 5, 90, 1), n("aim-smoothing", "Aim smoothing (%)", 40, 0, 100, 5),
                c("switch", "Hotbar switch (Silent = visible switch/back)", "Silent", "None", "Normal", "Silent"),
                c("swing", "Swing animation", "Client", "Client", "Packet", "None"),
                b("pause-eating", "Pause while using an item", true), n("pause-health", "Pause at health (HP; 0 off)", 0, 0, 20, 1),
                b("render", "Show pending / accepted placement preview", true), n("red", "Preview red", 232, 0, 255, 1),
                n("green", "Preview green", 200, 0, 255, 1), n("blue", "Preview blue", 114, 0, 255, 1)));
    }
    private static void placement(List<Setting<?>> settings, boolean block) {
        interactions(settings, 35);
        settings.add(n("blocks-per-tick", "Placements per tick (shared cap 8)", 1, 1, 8, 1));
        if (block) settings.add(c("block", "Building material", "Obsidian", "Obsidian", "Ender chest", "Any hard", "Any"));
    }
    private static void damage(List<Setting<?>> settings, double enemy, double self) {
        settings.addAll(List.of(n("min-damage", "Minimum predicted enemy damage (HP)", enemy, 0, 36, .5),
                n("max-self-damage", "Maximum conservative self damage (HP)", self, 0, 36, .5),
                b("anti-suicide", "Require health above self damage", true), b("protect-friends", "Reject blasts near friends", true)));
    }
    private static Setting<Boolean> b(String id, String label, boolean value) { return Setting.bool(id, label, value); }
    private static Setting<Double> n(String id, String label, double value, double min, double max, double step) { return Setting.number(id, label, value, min, max, step); }
    private static Setting<String> c(String id, String label, String value, String... choices) { return Setting.choice(id, label, value, choices); }
    private static void add(List<ModuleDefinition> out, String id, String name, String description, List<Setting<?>> settings) {
        out.add(new ModuleDefinition(id, name, description, Category.HVH, false, settings));
    }
}
