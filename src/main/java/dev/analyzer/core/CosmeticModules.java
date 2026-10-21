package dev.analyzer.core;

import java.util.ArrayList;
import java.util.List;

/** Original render-only cosmetics. Every exposed style has an actual implementation. */
public final class CosmeticModules {
    private CosmeticModules() { }
    public static List<ModuleDefinition> definitions() {
        List<ModuleDefinition> out = new ArrayList<>();
        add(out, "item-physics", "Item Physics", "Dropped-item models tumble in the air and rest on the ground; entity physics is unchanged.",
                Setting.number("tumble", "Tumble speed", 1, 0, 3, 0.05), Setting.bool("water-rock", "Rock in water", true),
                Setting.number("water-strength", "Water rocking", 0.2, 0, 0.6, 0.02), Setting.number("ground-lift", "Ground clearance", 0.035, 0.01, 0.15, 0.005));
        List<Setting<?>> hand = new ArrayList<>(List.of(
                Setting.choice("swing", "Swing style", "Low", "Vanilla", "Low", "Smooth", "Stab", "Slide", "Spin", "Tap"),
                Setting.number("swing-strength", "Swing strength", 0.4, 0.05, 1.5, 0.05),
                Setting.bool("cancel-swing", "Cancel swing", false), Setting.bool("hold-pose", "Hold swing pose", false),
                Setting.number("swing-pose", "Held swing progress", 0.35, 0, 1, 0.05),
                Setting.number("equip-dip", "Equip dip amount", 1, 0, 1, 0.05),
                Setting.bool("cancel-eat", "Cancel eating animation", false), Setting.bool("hide-hands", "Hide hands and items", false),
                Setting.bool("hide-offhand", "Hide offhand item", false), Setting.number("hand-scale", "Hands and items scale", 1, 0.3, 1.5, 0.05)));
        for (String side : List.of("main", "off")) {
            String label = side.equals("main") ? "Main" : "Offhand";
            for (String axis : List.of("x", "y", "z")) hand.add(Setting.number(side + "-" + axis, label + " hand " + axis.toUpperCase(), 0, -1, 1, 0.05));
            hand.add(Setting.number(side + "-size", label + " item size", 1, 0.2, 2.5, 0.05));
            for (String rotation : List.of("tilt", "turn", "roll")) hand.add(Setting.number(side + "-" + rotation, label + " item " + rotation, 0, -180, 180, 5));
        }
        hand.add(Setting.number("stretch-x", "Item width", 1, 0.2, 3, 0.05));
        hand.add(Setting.number("stretch-y", "Item height", 1, 0.2, 3, 0.05));
        hand.add(Setting.number("stretch-z", "Item depth", 1, 0.2, 3, 0.05));
        add(out, "hand-tweaks", "Hand Tweaks", "Actual first-person hand/item transforms and swing/equip controls; does not alter attacks.", hand);
        List<Setting<?>> crystal = colors(true);
        crystal.addAll(List.of(Setting.choice("model", "Crystal model", "Vanilla", "Vanilla", "Gem", "Prism"),
                Setting.number("opacity", "Opacity", 0.85, 0.1, 1, 0.05), Setting.bool("glow", "Vanilla model fullbright", true),
                Setting.bool("rings", "Orbit bands", true), Setting.number("size", "Model size", 1, 0.25, 2.5, 0.05),
                Setting.number("spin", "Spin rate", 1, -3, 3, 0.05), Setting.number("bounce", "Bounce height", 1, 0, 2, 0.05),
                Setting.choice("base", "Crystal base", "Default", "Default", "Always", "Never")));
        add(out, "crystal-chams", "Crystal Chams", "Tint the actual crystal model or replace it with an original Gem/Prism mesh. Normal depth remains.", crystal);
        List<Setting<?>> cape = colors(false);
        cape.addAll(List.of(Setting.choice("style", "Cape style", "Trimmed", "Trimmed", "Stripes", "Gradient"),
                Setting.choice("hem", "Cape hem", "Straight", "Straight", "Pointed", "Swallowtail"),
                Setting.choice("who", "Wearers", "Self", "Self", "Players", "Both"),
                Setting.bool("hide-elytra", "Hide with Elytra", true), Setting.bool("hide-vanilla", "Hide Minecraft cape", true),
                Setting.number("length", "Cape length", 1, 0.5, 1.6, 0.05), Setting.number("width", "Cape width", 1, 0.5, 1.6, 0.05),
                Setting.number("flow", "Flow", 1, 0, 2, 0.05), Setting.number("ripple", "Ripple", 0.5, 0, 2, 0.05),
                Setting.number("wind", "Wind", 0.4, 0, 2, 0.05), Setting.number("opacity", "Opacity", 0.9, 0.2, 1, 0.05)));
        add(out, "cape", "Cape", "An original segmented cloth mesh with moving hems and colors, visible in third person. No image/GIF importer.", cape);
        add(out, "wings", "Wings", "Approved Angel, Dragon, Bat and Parrot textures on an existing articulated wing rig. Local third-person cosmetic; no flight or server broadcast.",
                Setting.choice("style", "Wing style", "Angel", "Angel", "Dragon", "Bat", "Parrot"),
                Setting.choice("animation", "Animation", "Auto", "Auto", "Flap", "Idle", "Glide", "Still"),
                Setting.number("speed", "Animation speed", 1, 0, 3, 0.05),
                Setting.number("strength", "Animation strength", 1, 0, 1.5, 0.05),
                Setting.number("scale", "Wing scale", 1, 0.5, 1.5, 0.05),
                Setting.number("height", "Extra height (pixels)", 0, -4, 8, 0.25),
                Setting.number("back-offset", "Extra back offset (pixels)", 0, -2, 6, 0.25),
                Setting.bool("hide-elytra", "Hide with Elytra", true),
                Setting.bool("armor-clearance", "Extra chest armor clearance", true),
                Setting.bool("fullbright", "Fullbright texture (no bloom)", false));
        List<Setting<?>> hat = colors(false);
        hat.addAll(0, List.of(Setting.choice("style", "Hat style", "Straw", "Straw", "Lacquered", "Glass", "Rainbow glass")));
        hat.addAll(List.of(Setting.choice("who", "Wearers", "Self", "Self", "Players", "Both"),
                Setting.number("size", "Brim size", 1, 0.8, 1.6, 0.05),
                Setting.number("height", "Cone height", 1, 0.5, 1.8, 0.05),
                Setting.number("lift", "Extra height (pixels)", 0, -2, 6, 0.25),
                Setting.number("tilt", "Tilt (degrees)", 0, -25, 25, 1),
                Setting.bool("strap", "Chin strap", true),
                Setting.bool("hide-helmet", "Hide while wearing a helmet", false),
                Setting.number("opacity", "Glass opacity", 0.6, 0.15, 1, 0.05)));
        add(out, "straw-hat", "Straw Hat", "An original conical straw hat on the player's head in third person, in woven straw, lacquer or tinted glass. Rendered locally; nobody else sees it.", hat);
        List<Setting<?>> pet = colors(false);
        pet.addAll(List.of(Setting.choice("pet", "Companion", "Rune Orb", "Rune Orb", "Ghost", "Dragon"),
                Setting.choice("position", "Pet position", "Shoulder", "Shoulder", "Orbit", "Behind"),
                Setting.number("size", "Pet size", 1, 0.5, 2.5, 0.05), Setting.number("speed", "Pet animation speed", 1, 0, 2, 0.05),
                Setting.number("opacity", "Pet opacity", 0.85, 0.2, 1, 0.05)));
        add(out, "pets", "Pets", "Original animated Rune Orb, Ghost or Dragon geometry following your rendered body in third person.", pet);
        add(out, "emotes", "Emotes", "Pose your local player model in third person. No camera change or server-side animation.",
                Setting.choice("emote", "Emote", "Wave", "Wave", "Dance", "Bow", "Sit", "Cheer"),
                Setting.bool("loop", "Loop", false), Setting.number("length", "Duration (seconds)", 4, 1, 20, 0.5),
                Setting.bool("stop-moving", "Stop when moving", true), Setting.number("strength", "Pose strength", 1, 0.3, 1.5, 0.05));
        add(out, "walk-style", "Walk Style", "Alternative local third-person arm, leg and body poses using the existing player model.",
                Setting.choice("style", "Walking style", "Swagger", "Swagger", "Robot", "Ninja", "Zombie", "Bouncy"),
                Setting.number("strength", "Pose strength", 1, 0.3, 1.5, 0.05), Setting.bool("only-moving", "Only while walking", true));
        add(out, "spinbot", "Spinbot", "Spin only your rendered player model. Camera, entity rotation and server packets are unchanged.",
                Setting.number("speed", "Yaw speed (degrees/tick)", 15, -180, 180, 5), Setting.number("pitch", "Rendered head pitch", 0, -90, 90, 5));
        return List.copyOf(out);
    }
    private static List<Setting<?>> colors(boolean original) {
        List<Setting<?>> settings = new ArrayList<>();
        settings.add(original ? Setting.choice("color", "Color mode", "Theme", "Theme", "Mint", "Violet", "Gold", "Rainbow", "Custom", "Original")
                : Setting.choice("color", "Color mode", "Theme", "Theme", "Mint", "Violet", "Gold", "Rainbow", "Custom"));
        settings.add(Setting.number("red", "Custom red", 121, 0, 255, 1));
        settings.add(Setting.number("green", "Custom green", 248, 0, 255, 1));
        settings.add(Setting.number("blue", "Custom blue", 204, 0, 255, 1));
        return settings;
    }
    private static void add(List<ModuleDefinition> out, String id, String name, String description, Setting<?>... settings) { add(out, id, name, description, List.of(settings)); }
    private static void add(List<ModuleDefinition> out, String id, String name, String description, List<Setting<?>> settings) { out.add(new ModuleDefinition(id, name, description, Category.COSMETICS, false, settings)); }
}
