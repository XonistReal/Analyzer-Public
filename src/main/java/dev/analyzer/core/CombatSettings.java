package dev.analyzer.core;

import java.util.ArrayList;
import java.util.List;

/** Original combat controls. All actions retain vanilla reach and server authority. */
public final class CombatSettings {
    private CombatSettings() { }
    public static List<ModuleDefinition> definitions() {
        List<ModuleDefinition> modules = new ArrayList<>();
        List<Setting<?>> aura = targets(3, 8);
        aura.addAll(List.of(
            Setting.bool("legit", "Require visible crosshair / 120 degree cone", false),
            Setting.choice("mode", "Target mode", "Single", "Single", "Switch", "Multi"),
            Setting.number("switch-delay", "Switch delay (ms)", 250, 50, 1000, 50),
            Setting.number("max-targets", "Maximum targets per charged action", 3, 1, 8, 1),
            Setting.number("aim-range", "Extra acquisition range", 0, 0, 4, 0.25),
            Setting.bool("only-click", "Only acquire while holding attack", false),
            Setting.bool("prefer-reachable", "Prefer targets inside actual melee reach", true)));
        rotation(aura, 12); timing(aura, false);
        add(modules, "kill-aura", "Kill Aura", "Choose, rotate toward and attack filtered targets with normal reach and bounded charged actions.", aura);

        List<Setting<?>> aim = targets(6, 8); rotation(aim, 12); aim.removeIf(setting -> setting.id().equals("hit-angle"));
        aim.addAll(List.of(Setting.bool("legit", "Visible gradual assistance while clicking", false),
            Setting.bool("hold-attack", "Only while holding attack", true),
            Setting.bool("weapon-only", "Only while holding a melee weapon", false),
            Setting.bool("pause-using", "Pause while using an item", true),
            Setting.bool("pause-mining", "Pause while breaking a block", true),
            Setting.number("strength", "Aim strength (%)", 100, 1, 100, 1)));
        add(modules, "aim-assist", "Aim Assist", "Pull your actual camera toward a filtered target with configurable aim point, strength and smoothing.", aim);

        List<Setting<?>> trigger = crosshairTargets(3, 8); rotation(trigger, 12); timing(trigger, false);
        trigger.addAll(List.of(Setting.bool("legit", "Reaction delay and varied charged attacks", false),
            Setting.number("reaction-delay", "Crosshair reaction delay (ms)", 0, 0, 1000, 25)));
        add(modules, "trigger-bot", "Trigger Bot", "Attack a filtered crosshair target after its reaction delay, normal reach and selected charge timing.", trigger);

        List<Setting<?>> clicker = crosshairTargets(3, 8); rotation(clicker, 12); timing(clicker, true);
        clicker.addAll(List.of(Setting.bool("legit", "Vary clicks, cap at 12 CPS and occasionally skip", false),
            Setting.choice("button", "Repeated button", "Left", "Left", "Right", "Both"),
            Setting.number("cps", "Maximum CPS cap", 8, 1, 20, 1),
            Setting.choice("pattern", "Left-attack click pattern", "Steady", "Steady", "Random", "Butterfly"),
            Setting.bool("respect-cooldown", "Wait for the selected attack charge", true)));
        add(modules, "auto-clicker", "Auto Clicker", "Repeat held entity attacks or ordinary item/block use with bounded steady, random or paired timing.", clicker);

        add(modules, "auto-weapon", "Auto Weapon", "Temporarily choose allowed hotbar melee weapons by material estimate or durability after a reaction delay.", new ArrayList<>(List.of(
            Setting.bool("legit", "Crosshair-only delayed switch; no same-tick attack", false),
            Setting.bool("swords", "Allow swords", true), Setting.bool("axes", "Allow axes", true),
            Setting.bool("maces", "Allow maces", true), Setting.bool("tridents", "Allow tridents", true),
            Setting.choice("preference", "Weapon preference", "Damage estimate", "Damage estimate", "Sword first", "Axe first", "Durability"),
            Setting.number("reaction-delay", "Weapon switch delay (ms)", 0, 0, 1000, 25),
            Setting.number("settle-ticks", "Wait after switching weapon (ticks)", 1, 0, 10, 1))));

        add(modules, "shield-breaker", "Shield Breaker", "Hold a usable hotbar axe for a blocking crosshair target, with reaction and release delays.", new ArrayList<>(List.of(
            Setting.bool("legit", "Delayed axe switch before attacking", false),
            Setting.bool("ignore-friends", "Ignore saved friends", true),
            Setting.number("reaction-delay", "Blocking-target reaction delay (ms)", 0, 0, 1000, 25),
            Setting.number("release-delay", "Keep axe after blocking ends (ms)", 0, 0, 1000, 25),
            Setting.number("settle-ticks", "Wait after switching axe (ticks)", 1, 0, 10, 1),
            Setting.bool("only-click", "Only break shields while holding attack", false),
            Setting.bool("pause-using", "Pause while using an item", true),
            Setting.bool("pause-mining", "Pause while breaking a block", true),
            Setting.number("cooldown", "Required attack charge", 0.95, 0.2, 1, 0.05),
            Setting.choice("swing", "Swing animation", "Client", "Client", "Packet", "None"))));

        List<Setting<?>> bow = targets(48, 120); rotation(bow, 12); bow.removeIf(setting -> setting.id().equals("hit-angle"));
        bow.addAll(List.of(Setting.bool("bows", "Aim drawn bows", true),
            Setting.bool("crossbows", "Aim held charged crossbows before firing", false),
            Setting.bool("tridents", "Aim drawn tridents", false),
            Setting.bool("predict-movement", "Lead target velocity", true),
            Setting.choice("prediction", "Projectile compensation", "Lead and drop", "Direct", "Lead", "Lead and drop"),
            Setting.number("lead-factor", "Target velocity lead multiplier", 1, 0, 2, 0.05),
            Setting.number("min-charge", "Minimum bow / trident draw fraction", 0, 0, 1, 0.05)));
        add(modules, "bow-aimbot", "Bow Aimbot", "Aim the actual camera for selected drawn bows, charged crossbows or tridents using bounded lead/drop estimates.", bow);

        List<Setting<?>> criticals = crosshairTargets(3, 8); rotation(criticals, 12); timing(criticals, false);
        criticals.removeIf(setting -> setting.id().equals("wait-for-crit"));
        criticals.addAll(List.of(Setting.bool("legit", "Wait for your own normal jump", false),
            Setting.choice("mode", "Critical movement", "Jump", "Jump", "Mini jump", "Manual fall"),
            Setting.choice("crit-targets", "Apply critical timing to", "Living", "Living", "Players", "Hostiles"),
            Setting.number("min-charge", "Minimum charge for critical timing", 0.9, 0, 1, 0.05),
            Setting.bool("only-kill-aura", "Only apply to Kill Aura attacks", false),
            Setting.number("hop-height", "Mini jump upward velocity", 0.2, 0.08, 0.42, 0.01),
            Setting.number("min-interval", "Minimum time between automatic hops (ms)", 0, 0, 1000, 50),
            Setting.number("hold-time", "Maximum waiting time for a falling hit (ticks)", 14, 4, 30, 1),
            Setting.bool("hop-while-moving", "Allow automatic hops while moving", true),
            Setting.bool("sprint-reset", "Release sprint normally while waiting for a crit", true),
            Setting.bool("crit-particles", "Show local critical cue on the falling attack", false)));
        add(modules, "criticals", "Criticals", "Use real jumps or your own falling motion to time ordinary charged attacks; no packet critical spoofing.", criticals);

        List<Setting<?>> block = targets(4, 8); rotation(block, 18);
        block.removeIf(setting -> List.of("wall-range", "through-walls", "hit-angle", "aim-point", "other-living", "passives").contains(setting.id()));
        block.addAll(List.of(Setting.number("health", "Raise shield at or below health", 20, 1, 36, 1),
            Setting.number("release-delay", "Keep shield after threat leaves (ticks)", 4, 0, 20, 1),
            Setting.bool("face-threat", "Face the selected visible threat", true),
            Setting.bool("yield-hvh", "Yield while combat placement modules are enabled", true),
            Setting.bool("pause-mining", "Pause while breaking a block", true)));
        add(modules, "auto-block", "Auto Block", "Raise an equipped offhand shield toward a visible threat between attacks; release it before ordinary melee attacks.", block);

        List<Setting<?>> doubleHand = targets(6, 12);
        doubleHand.removeIf(setting -> List.of("wall-range", "through-walls", "other-living", "passives").contains(setting.id()));
        doubleHand.addAll(List.of(Setting.number("health", "Hold main-hand totem at or below health", 10, 1, 36, 1),
            Setting.choice("mode", "Protect when", "Either", "Low health", "After totem pop", "Either"),
            Setting.bool("without-offhand", "Only when offhand has no totem", true),
            Setting.number("hold-ticks", "Keep totem after danger ends (ticks)", 10, 0, 60, 1)));
        add(modules, "double-hand", "Double Hand", "Temporarily hold a hotbar totem after a real server-reported pop or at low health near a visible threat; yields to Auto Totem once the offhand is protected.", doubleHand);
        add(modules, "hover-totem", "Hover Totem", "In your own inventory, hold the selected key over a totem to move it into the offhand through a normal guarded slot swap.", new ArrayList<>(List.of(
            Setting.choice("hold-key", "Hold key while hovering", "Sneak", "Sneak", "Use", "None"),
            Setting.number("hover-ticks", "Hover before swapping (ticks)", 3, 0, 20, 1),
            Setting.number("delay", "Minimum interval between swaps (ticks)", 6, 1, 40, 1))));
        return List.copyOf(modules);
    }
    private static List<Setting<?>> targets(double range, double maximum) {
        return new ArrayList<>(List.of(Setting.bool("players", "Players", true),
            Setting.bool("hostiles", "Hostile mobs", false), Setting.bool("passives", "Passive mobs", false),
            Setting.bool("other-living", "Other living entities", false),
            Setting.bool("ignore-friends", "Ignore saved friends", true),
            Setting.bool("ignore-invisible", "Ignore invisible entities", false),
            Setting.bool("ignore-naked", "Ignore players without armor", false),
            Setting.number("ignore-new", "Ignore newly loaded entities (ticks)", 0, 0, 100, 1),
            Setting.number("range", "Target range from eyes to hitbox", range, 1, maximum, 0.25),
            Setting.number("fov", "Target field of view (degrees)", 180, 10, 360, 10),
            Setting.choice("priority", "Target priority", "Distance", "Distance", "Health", "Angle", "Armor", "Hurt time"),
            Setting.bool("through-walls", "Allow obstructed targets", false),
            Setting.number("wall-range", "Obstructed target range", 0, 0, maximum, 0.25)));
    }
    private static void rotation(List<Setting<?>> settings, double speed) {
        settings.addAll(List.of(Setting.choice("rotation", "Rotation", "Client", "Client", "None"),
            Setting.number("turn-speed", "Maximum turn (degrees/tick)", speed, 1, 180, 1),
            Setting.number("smoothing", "Slow down near aim point (%)", 0, 0, 100, 1),
            Setting.choice("aim-point", "Aim point", "Head", "Nearest", "Chest", "Head"),
            Setting.number("hit-angle", "Maximum attack aim error (degrees)", 90, 1, 180, 1)));
    }
    private static List<Setting<?>> crosshairTargets(double range, double maximum) {
        List<Setting<?>> settings = targets(range, maximum);
        // Crosshair modules cannot acquire an unseen target or choose between several entities.
        settings.removeIf(setting -> List.of("priority", "through-walls", "wall-range").contains(setting.id()));
        return settings;
    }
    private static void timing(List<Setting<?>> settings, boolean clicker) {
        settings.addAll(List.of(Setting.choice("timing", "Attack timing", clicker ? "CPS" : "Cooldown", "Cooldown", "CPS"),
            Setting.number("cooldown", "Required attack charge", 0.95, 0.2, 1, 0.05),
            Setting.number("attack-jitter", "Random delay after charge (ms)", 0, 0, 300, 25),
            Setting.number("min-cps", "Minimum CPS", clicker ? 8 : 9, 1, 20, 1),
            Setting.number("max-cps", "Maximum CPS", clicker ? 8 : 12, 1, 20, 1),
            Setting.number("hit-charge", "Required charge in CPS mode", 0.95, 0, 1, 0.05),
            Setting.bool("wait-for-crit", "Wait for falling motion while airborne", false),
            Setting.bool("weapon-only", "Only attack with a melee weapon", false),
            Setting.bool("pause-using", "Pause attacks while using an item", true),
            Setting.bool("pause-mining", "Pause while breaking a block", true),
            Setting.bool("require-ray", "Require actual camera ray through target hitbox", true),
            Setting.number("hurt-time", "Maximum target hurt time (ticks)", 10, 0, 10, 1),
            Setting.number("settle-ticks", "Wait after changing held weapon (ticks)", 1, 0, 10, 1),
            Setting.choice("swing", "Swing animation", "Client", "Client", "Packet", "None")));
    }
    private static void add(List<ModuleDefinition> modules, String id, String name, String description, List<Setting<?>> settings) {
        modules.add(new ModuleDefinition(id, name, description, Category.COMBAT, false, settings));
    }
}
