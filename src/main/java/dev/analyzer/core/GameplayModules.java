package dev.analyzer.core;

import java.util.ArrayList;
import java.util.List;

/** Functional gameplay modules, grouped by controller; all are opt-in. */
public final class GameplayModules {
    private GameplayModules() { }
    public static List<ModuleDefinition> definitions() {
        List<ModuleDefinition> out = new ArrayList<>();
        out.addAll(CombatSettings.definitions());
        out.addAll(HvhSettings.definitions());
        out.addAll(MovementSettings.definitions());
        add(out, "name-changer", "Name Changer", "Show a local alias for your own name in rendered labels and chat. Your account and outgoing text stay unchanged; editable input shows its actual text.", Category.CLIENT,
                Setting.text("alias", "Alias (letters, numbers, _)", "Player", 32));
        add(out, "auto-eat", "Auto Eat", "Begin eating below the hunger threshold and continue until full; temporarily owns and restores use input/slot.", Category.PLAYER,
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
    private static void add(List<ModuleDefinition> out, String id, String name, String description, Category category, Setting<?>... settings) {
        out.add(new ModuleDefinition(id, name, description, category, false, List.of(settings)));
    }
}
