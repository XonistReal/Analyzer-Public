package dev.analyzer.core;

import java.util.ArrayList;
import java.util.List;

/** Functional gameplay modules. All are opt-in; no server bypass is advertised. */
public final class GameplayModules {
    private GameplayModules() { }
    public static List<ModuleDefinition> definitions() {
        List<ModuleDefinition> out = new ArrayList<>();
        combat(out, "kill-aura", "Kill Aura", "Attack the nearest selected visible target within vanilla reach and cooldown.", 3);
        combat(out, "aim-assist", "Aim Assist", "Smoothly turn toward a selected target while holding attack.", 6);
        combat(out, "trigger-bot", "Trigger Bot", "Attack a selected entity under your crosshair at the configured cooldown.", 3);
        combat(out, "auto-clicker", "Auto Clicker", "Repeat entity attacks while you hold attack; respects reach and cooldown.", 3);
        add(out, "auto-weapon", "Auto Weapon", "Select a hotbar melee weapon by its material/damage estimate while attacking.", Category.COMBAT);
        add(out, "shield-breaker", "Shield Breaker", "Select a hotbar axe and attack a visible blocking target at normal reach.", Category.COMBAT);
        combat(out, "bow-aimbot", "Bow Aimbot", "Aim a drawn bow at a visible target with velocity lead and gravity compensation.", 48);
        combat(out, "criticals", "Criticals", "Jump before melee attacks, then attack in the ordinary falling phase. Hold attack or enable an aura.", 3);
        hvh(out, "crystal-aura", "Crystal Aura", "Place crystals on real obsidian/bedrock and break reachable enemy-near crystals. No damage prediction.",
                Setting.bool("place", "Place crystals", true), Setting.bool("break", "Break crystals", true),
                Setting.number("cooldown", "Break cooldown threshold", 0.95, 0.2, 1, 0.05),
                Setting.number("self-distance", "Minimum self distance", 3, 2, 6, 0.25), Setting.number("health", "Minimum health", 12, 1, 20, 1));
        hvh(out, "anchor-aura", "Anchor Aura", "Place, charge and use anchors only in dimensions where they explode. No damage prediction.",
                Setting.number("self-distance", "Minimum self distance", 3.5, 2, 6, 0.25), Setting.number("health", "Minimum health", 12, 1, 20, 1));
        add(out, "surround", "Surround", "Place hotbar obsidian around your feet, one normal interaction at a time.", Category.HVH,
                Setting.number("delay", "Placement interval (ticks)", 3, 1, 20, 1), Setting.bool("support", "Build lower supports", true));
        hvh(out, "auto-trap", "Auto Trap", "Place obsidian around a selected target and above its head with ordinary block use.");
        hvh(out, "auto-web", "Auto Web", "Place hotbar cobweb at a selected target's feet when support and reach permit.");
        add(out, "hole-filler", "Hole Filler", "Fill nearby one-block holes with a solid floor and four solid walls using obsidian.", Category.HVH,
                Setting.number("range", "Search range", 4, 1, 5, 0.5), Setting.number("delay", "Placement interval (ticks)", 4, 1, 20, 1));
        hvh(out, "target-strafe", "Target Strafe", "Move tangentially around the nearest selected visible target; collision remains active.",
                Setting.number("radius", "Orbit radius", 2.5, 1, 5, 0.25), Setting.number("speed", "Horizontal speed", 0.22, 0.05, 0.6, 0.01),
                Setting.bool("clockwise", "Clockwise", true));
        add(out, "keep-sprint", "Keep Sprint", "Hold the normal sprint input while moving forward and able to sprint.", Category.MOVEMENT);
        add(out, "auto-walk", "Auto Walk", "Hold forward while the world is focused; releases the input when disabled.", Category.MOVEMENT);
        move(out, "speed", "Speed", "Set horizontal client velocity while directional input is held; servers may correct it.", "speed", "Speed (blocks/tick)", 0.35, 0.1, 1);
        move(out, "fly", "Fly", "Client velocity flight with jump/sneak vertical control; does not grant server flight permission.", "speed", "Flight speed", 0.35, 0.05, 1);
        move(out, "step", "Step", "Temporarily raise the client step-height attribute; servers may correct movement.", "height", "Step height", 1, 0.6, 2.5);
        add(out, "parkour", "Parkour", "Jump when approaching an unsupported edge while moving on the ground.", Category.MOVEMENT);
        add(out, "safe-walk", "Safe Walk", "Use vanilla ledge clipping without requiring the sneak key.", Category.MOVEMENT);
        move(out, "fast-ladder", "Fast Ladder", "Increase upward velocity while climbing with forward or jump input.", "speed", "Climb speed", 0.28, 0.1, 0.6);
        move(out, "reverse-step", "Reverse Step", "Accelerate downward when falling toward a nearby floor.", "speed", "Downward speed", 0.8, 0.1, 2);
        move(out, "fast-fall", "Fast Fall", "Accelerate an existing fall after the configured distance; no fall-damage spoof.", "speed", "Downward speed", 1, 0.2, 3);
        move(out, "glide", "Glide", "Limit ordinary falling velocity outside fluids; servers may correct it.", "speed", "Fall speed", 0.1, 0.02, 0.5);
        move(out, "high-jump", "High Jump", "Increase upward velocity at the start of a normal jump.", "height", "Upward velocity", 0.7, 0.42, 1.5);
        move(out, "long-jump", "Long Jump", "Add horizontal velocity at the start of a normal directional jump.", "speed", "Jump speed", 0.65, 0.2, 1.5);
        move(out, "elytra-boost", "Elytra Boost", "Add a bounded impulse in the view direction while already gliding.", "thrust", "Impulse per tick", 0.035, 0.005, 0.1);
        add(out, "ground-strafe", "Ground Strafe", "Align existing horizontal momentum with your ground movement input.", Category.MOVEMENT);
        add(out, "auto-eat", "Auto Eat", "Eat hotbar food below a hunger threshold; temporarily owns and restores use input/slot.", Category.PLAYER,
                Setting.number("hunger", "Eat below hunger", 16, 1, 20, 1));
        add(out, "auto-gapple", "Auto Gapple", "Use a hotbar golden apple below a health threshold.", Category.PLAYER,
                Setting.number("health", "Eat below health", 12, 1, 20, 1));
        add(out, "auto-totem", "Auto Totem", "Swap an inventory totem into the offhand through the normal player inventory handler.", Category.PLAYER);
        add(out, "auto-armor", "Auto Armor", "Equip stronger material-tier armor through guarded normal inventory quick moves.", Category.PLAYER);
        add(out, "auto-tool", "Auto Tool", "Choose the fastest hotbar tool for the block you are actively mining.", Category.PLAYER);
        add(out, "refill", "Refill", "Swap larger matching inventory stacks into low hotbar stacks.", Category.PLAYER,
                Setting.number("threshold", "Refill below count", 16, 1, 32, 1));
        add(out, "anti-afk", "Anti AFK", "Periodically turn and jump using ordinary input, while no menu is open.", Category.PLAYER,
                Setting.number("interval", "Interval (seconds)", 30, 5, 120, 1));
        add(out, "auto-respawn", "Auto Respawn", "Request an ordinary respawn after the death screen delay.", Category.PLAYER,
                Setting.number("delay", "Delay (ticks)", 20, 1, 100, 1));
        add(out, "durability-alert", "Durability Alert", "Show a periodic warning for low-durability equipped items.", Category.PLAYER,
                Setting.number("threshold", "Remaining durability (%)", 15, 1, 50, 1));
        add(out, "auto-fish", "Auto Fish", "Reel on the server-synchronized caught-fish flag, then optionally cast again.", Category.PLAYER,
                Setting.bool("cast", "Automatically cast", true), Setting.number("delay", "Cast/reel interval (ticks)", 20, 5, 100, 1));
        return List.copyOf(out);
    }
    private static void combat(List<ModuleDefinition> out, String id, String name, String description, double range) {
        List<Setting<?>> settings = new ArrayList<>(List.of(targets(range, id.equals("bow-aimbot") ? 64 : 8)));
        settings.add(Setting.number("turn-speed", "Turn speed (degrees/tick)", 12, 1, 90, 1));
        if (!id.equals("aim-assist") && !id.equals("bow-aimbot")) settings.add(Setting.number("cooldown", "Attack cooldown threshold", 0.95, 0.2, 1, 0.05));
        if (id.equals("auto-clicker")) settings.add(Setting.number("cps", "Maximum clicks per second", 8, 1, 20, 1));
        add(out, id, name, description, Category.COMBAT, settings.toArray(Setting<?>[]::new));
    }
    private static Setting<?>[] targets(double range, double max) {
        return new Setting<?>[]{Setting.bool("players", "Players", true), Setting.bool("hostiles", "Hostile mobs", false),
                Setting.bool("passives", "Passive mobs", false), Setting.number("range", "Target range", range, 1, max, 0.25),
                Setting.number("fov", "Field of view", 180, 20, 360, 10)};
    }
    private static void hvh(List<ModuleDefinition> out, String id, String name, String description, Setting<?>... extra) {
        List<Setting<?>> settings = new ArrayList<>(List.of(targets(6, 8)));
        if (!id.equals("target-strafe")) settings.add(Setting.number("delay", "Action interval (ticks)", 4, 1, 20, 1));
        settings.addAll(List.of(extra));
        add(out, id, name, description, Category.HVH, settings.toArray(Setting<?>[]::new));
    }
    private static void move(List<ModuleDefinition> out, String id, String name, String description, String setting, String label, double value, double min, double max) {
        add(out, id, name, description, Category.MOVEMENT, Setting.number(setting, label, value, min, max, 0.01));
    }
    private static void add(List<ModuleDefinition> out, String id, String name, String description, Category category, Setting<?>... settings) {
        out.add(new ModuleDefinition(id, name, description, category, false, List.of(settings)));
    }
}
