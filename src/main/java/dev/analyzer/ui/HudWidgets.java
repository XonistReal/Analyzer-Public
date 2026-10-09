package dev.analyzer.ui;

import dev.analyzer.core.AnalyzerCore;
import dev.analyzer.core.Category;
import dev.analyzer.core.ModuleDefinition;
import dev.analyzer.gameplay.GameplayEngine;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** One layout engine is shared by gameplay rendering, ESP exclusion regions and the editor. */
public final class HudWidgets {
    private static final MinecraftClient CLIENT = MinecraftClient.getInstance();
    private static final String VERSION = FabricLoader.getInstance().getModContainer("analyzer")
            .map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("dev");
    public static final List<String> IDS = List.of("watermark", "active-modules", "fps", "ping", "speed-hud", "armor", "potions",
            "target-hud", "keybinds", "inventory", "compass", "biome", "world-clock", "item-counter", "keystrokes", "reach-display", "coordinates");
    private HudWidgets() { }

    public record WidgetBounds(String id, int x, int y, int width, int height) {
        public boolean contains(double px, double py) { return px >= x && py >= y && px < x + width && py < y + height; }
    }
    private record Widget(String id, int width, int height, List<String> lines, int rowPitch, int iconPitch) { }

    public static float scale(AnalyzerCore core, DrawContext draw) {
        // Minecraft GUI scale changes coordinate units, not the requested HUD size.
        return windowScale(core);
    }
    private static float windowScale(AnalyzerCore core) {
        return (float) (Math.max(0.5, CLIENT.getWindow().getFramebufferWidth() / 720.0)
                * core.getNumber("hud", "scale") / Math.max(1, CLIENT.getWindow().getScaleFactor()));
    }
    /** Keep small captions readable while panels and icons retain the user's size. */
    public static float readableTtfScale(AnalyzerCore core, DrawContext draw, float preferred) {
        return readableScale(core.getChoice("hud", "theme"), scale(core, draw), preferred, false);
    }
    private static float readableScale(String theme, float outerScale, float preferred, boolean themeFont) {
        double physical = Math.max(0.1, outerScale * CLIENT.getWindow().getScaleFactor());
        boolean bitmap = themeFont && theme.equals("Classic");
        float nominalSize = bitmap ? 9 : 12;
        float minimum = preferred >= 0.85f ? 14 : 12;
        float requested = bitmap ? preferred * 0.8f : GuiDraw.effectiveScale(preferred);
        return Math.max(requested, (float) (minimum / (nominalSize * physical)));
    }
    private static float textWidth(String value, String theme, float effectiveScale) {
        return theme.equals("Classic") ? CLIENT.textRenderer.getWidth(value) * effectiveScale
                : GuiDraw.width(CLIENT.textRenderer, value, effectiveScale);
    }
    private static String fitText(String value, String theme, float effectiveScale, int width) {
        if (textWidth(value, theme, effectiveScale) <= width) return value;
        if (textWidth("…", theme, effectiveScale) > width) return "";
        int end = value.length();
        while (end > 0 && textWidth(value.substring(0, end) + "…", theme, effectiveScale) > width)
            end = value.offsetByCodePoints(end, -1);
        return value.substring(0, end) + "…";
    }
    public static int accent(AnalyzerCore core) {
        return switch (core.getChoice("menu", "accent")) { case "Violet" -> Palette.VIOLET; case "Gold" -> Palette.GOLD; default -> Palette.MINT; };
    }
    public static List<WidgetBounds> bounds(AnalyzerCore core, DrawContext draw, boolean editor) {
        float scale = scale(core, draw);
        int width = Math.round(draw.getScaledWindowWidth() / scale), height = Math.round(draw.getScaledWindowHeight() / scale);
        List<WidgetBounds> result = new ArrayList<>();
        if (!core.isEnabled("hud") && !editor) return result;
        for (Widget widget : widgets(core, editor)) {
            int x = (int) Math.round(position(core, widget.id, true) * Math.max(0, width - widget.width) / 100);
            int y = (int) Math.round(position(core, widget.id, false) * Math.max(0, height - widget.height) / 100);
            result.add(new WidgetBounds(widget.id, Math.round(x * scale), Math.round(y * scale), Math.round(widget.width * scale), Math.round(widget.height * scale)));
        }
        return result;
    }
    public static double position(AnalyzerCore core, String id, boolean x) {
        return id.equals("coordinates") || id.equals("radar") ? core.getNumber("hud", id + (x ? "-x" : "-y")) : core.getNumber(id, x ? "x" : "y");
    }
    public static void move(AnalyzerCore core, String id, double x, double y) {
        String owner = id.equals("coordinates") || id.equals("radar") ? "hud" : id;
        core.setValue(owner, id.equals("coordinates") || id.equals("radar") ? id + "-x" : "x", x);
        core.setValue(owner, id.equals("coordinates") || id.equals("radar") ? id + "-y" : "y", y);
    }

    public static List<WidgetBounds> render(AnalyzerCore core, DrawContext draw, boolean editor) {
        List<WidgetBounds> result = bounds(core, draw, editor);
        if (result.isEmpty()) return result;
        float scale = scale(core, draw);
        int width = Math.round(draw.getScaledWindowWidth() / scale), height = Math.round(draw.getScaledWindowHeight() / scale);
        String theme = core.getChoice("hud", "theme");
        int accent = accent(core);
        draw.getMatrices().push();
        draw.getMatrices().scale(scale, scale, 1);
        for (Widget widget : widgets(core, editor)) {
            int x = (int) Math.round(position(core, widget.id, true) * Math.max(0, width - widget.width) / 100);
            int y = (int) Math.round(position(core, widget.id, false) * Math.max(0, height - widget.height) / 100);
            boolean enabled = core.isEnabled(widget.id);
            double opacity = widget.id.equals("coordinates") ? 0.8 : core.getNumber(widget.id, "opacity");
            int color = enabled ? accent : Palette.SUBTLE;
            panel(draw, x, y, widget.width, widget.height, theme, color, opacity * (enabled ? 1 : 0.45));
            if (widget.id.equals("watermark")) {
                GuiDraw.logo(draw, x + 8, y + 9, 21, color);
                text(draw, "ANALYZER", x + 37, y + 7, 1.2f, Palette.TEXT, theme);
                text(draw, VERSION + " / " + theme.toUpperCase(Locale.ROOT), x + 37, y + 24, 0.7f, color, theme);
            } else if (widget.id.equals("inventory")) drawInventory(draw, core, widget, x, y, theme);
            else if (widget.id.equals("armor")) drawArmor(draw, core, widget, x, y, theme);
            else if (widget.id.equals("keystrokes")) drawKeys(draw, core, widget, x, y, theme, color);
            else {
                for (int i = 0; i < widget.lines.size(); i++) {
                    float fontScale = readableScale(theme, scale, 0.92f, true);
                    String line = fitText(widget.lines.get(i), theme, fontScale, widget.width - 17);
                    text(draw, line, x + 8, y + 7 + i * widget.rowPitch, 0.92f, i == 0 ? color : Palette.TEXT, theme);
                }
                if (widget.id.equals("target-hud") && target() != null) {
                    LivingEntity target = target();
                    int bar = Math.round((widget.width - 16) * Math.clamp(target.getHealth() / Math.max(1, target.getMaxHealth()), 0, 1));
                    GuiDraw.rounded(draw, x + 8, y + widget.height - 11, widget.width - 16, 4, 2, Palette.BORDER);
                    GuiDraw.rounded(draw, x + 8, y + widget.height - 11, bar, 4, 2, color);
                }
            }
            if (editor) {
                GuiDraw.wireBox(draw, x, y, widget.width, widget.height, false, 0.7f, Palette.alpha(color, 170));
                text(draw, (enabled ? "" : "OFF / ") + widget.id, x + 3, Math.max(0, y - 11), 0.66f, color, theme);
            }
        }
        draw.getMatrices().pop();
        return result;
    }

    private static List<Widget> widgets(AnalyzerCore core, boolean editor) {
        List<Widget> widgets = new ArrayList<>();
        String theme = core.getChoice("hud", "theme");
        float body = readableScale(theme, windowScale(core), 0.92f, true);
        float secondary = readableScale(theme, windowScale(core), 0.7f, true);
        int rowPitch = Math.max(15, (int) Math.ceil(CLIENT.textRenderer.fontHeight * body) + 4);
        int iconPitch = Math.max(22, (int) Math.ceil(textWidth("100%", theme, secondary)) + 5);
        boolean world = CLIENT.player != null && CLIENT.world != null;
        for (String id : IDS) {
            if (!core.isEnabled(id) && !(editor && core.getBoolean("hud", "show-disabled"))) continue;
            List<String> lines = new ArrayList<>();
            int width = 142, height = 28;
            switch (id) {
                case "watermark" -> { width = 162; height = 41; }
                case "active-modules" -> {
                    List<ModuleDefinition> enabled = core.modules().stream().filter(module -> core.isEnabled(module.id()))
                            .filter(module -> core.getBoolean(id, "hud-modules") || module.category() != Category.HUD)
                            .sorted(Comparator.comparing(ModuleDefinition::name)).toList();
                    int maximum = (int) core.getNumber("hud", "max-modules");
                    maximum = Math.min(maximum, Math.max(1, (int) (CLIENT.getWindow().getScaledHeight() / scaleForWindow(core) / rowPitch) - 5));
                    int shownModules = 0;
                    for (int i = 0; i < enabled.size() && lines.size() < maximum; i++) {
                        if (lines.size() == maximum - 1 && i < enabled.size() - 1) break;
                        ModuleDefinition module = enabled.get(i);
                        lines.add(module.name());
                        shownModules++;
                        if (core.getBoolean(id, "details") && (module.id().equals("entity-esp") || module.id().equals("storage-esp") || module.id().equals("tracers")) && lines.size() < maximum - 1)
                            lines.add("  " + dev.analyzer.render.AnalyzerRenderer.status(module.id()));
                    }
                    if (enabled.size() > shownModules) lines.add("+ " + (enabled.size() - shownModules) + " more enabled");
                    if (lines.isEmpty()) lines.add("No active modules");
                    width = 196; height = 14 + lines.size() * 15;
                }
                case "fps" -> lines.add("FPS  " + CLIENT.getCurrentFps());
                case "ping" -> {
                    var entry = world && CLIENT.getNetworkHandler() != null ? CLIENT.getNetworkHandler().getPlayerListEntry(CLIENT.player.getUuid()) : null;
                    lines.add("PING  " + (entry == null ? "—" : entry.getLatency() + " ms"));
                }
                case "speed-hud" -> {
                    double speed = world ? CLIENT.player.getVelocity().horizontalLength() * 20 : 0;
                    boolean km = core.getChoice(id, "units").equals("km/h");
                    lines.add("SPEED  " + (world ? String.format(Locale.ROOT, "%.2f", speed * (km ? 3.6 : 1)) : "—") + (km ? " km/h" : " b/s"));
                }
                case "armor" -> { width = Math.max(98, 10 + iconPitch * 4); height = core.getBoolean(id, "durability") ? 59 : 46; }
                case "potions" -> {
                    lines.add("POTION EFFECTS");
                    if (world) for (StatusEffectInstance effect : CLIENT.player.getStatusEffects()) {
                        String name = Text.translatable(effect.getEffectType().value().getTranslationKey()).getString();
                        int seconds = Math.max(0, effect.getDuration() / 20);
                        lines.add(name + " " + (effect.getAmplifier() + 1) + " · " + (effect.isInfinite() ? "∞" : seconds / 60 + ":" + String.format(Locale.ROOT, "%02d", seconds % 60)));
                        if (lines.size() == 7) break;
                    }
                    if (lines.size() == 1) lines.add(world ? "No active effects" : "Join a world");
                    width = 190; height = 14 + lines.size() * 15;
                }
                case "target-hud" -> {
                    LivingEntity target = target();
                    lines.add(target == null ? "NO LIVING TARGET" : target.getDisplayName().getString());
                    lines.add(target == null ? "Aim at a living entity" : String.format(Locale.ROOT, "%.1f / %.1f HP · %.1fm", target.getHealth(), target.getMaxHealth(), CLIENT.player.distanceTo(target)));
                    if (target != null) lines.add("Armor " + target.getArmor() + " · " + (target.isAlive() ? "Alive" : "Defeated"));
                    width = 194; height = 70;
                }
                case "keybinds" -> {
                    lines.add("MODULE KEYBINDS");
                    for (ModuleDefinition module : core.modules()) {
                        int key = core.keybind(module.id());
                        if (key < 0 || (core.getBoolean(id, "active-only") && !core.isEnabled(module.id()))) continue;
                        lines.add((core.isEnabled(module.id()) ? "● " : "○ ") + module.name() + " · " + InputUtil.fromKeyCode(key, 0).getLocalizedText().getString());
                        if (lines.size() == 8) break;
                    }
                    if (lines.size() == 1) lines.add("No assigned binds");
                    width = 190; height = 14 + lines.size() * 15;
                }
                case "inventory" -> { width = 176; height = 77; }
                case "compass" -> {
                    double yaw = world ? Math.floorMod((int) CLIENT.player.getYaw(), 360) : 0;
                    String[] directions = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
                    lines.add("COMPASS  " + (world ? directions[(int) Math.round(yaw / 45) % 8] + " · " + Math.round(yaw) + "°" : "—"));
                }
                case "biome" -> {
                    String biome = world ? CLIENT.world.getBiome(CLIENT.player.getBlockPos()).getKey().map(key -> key.getValue().getPath().replace('_', ' ')).orElse("unknown") : "join a world";
                    lines.add("BIOME  " + biome); width = 192;
                }
                case "world-clock" -> {
                    long ticks = world ? CLIENT.world.getTimeOfDay() : 0;
                    int totalMinutes = (int) ((Math.floorMod(ticks, 24000) / 1000.0 + 6) * 60) % 1440;
                    int hour = totalMinutes / 60;
                    boolean twelve = core.getChoice(id, "format").equals("12 hour");
                    lines.add("TIME  " + (world ? String.format(Locale.ROOT, "%02d:%02d", twelve ? (hour + 11) % 12 + 1 : hour, totalMinutes % 60) + (twelve ? hour < 12 ? " AM" : " PM" : "") : "—"));
                    lines.add("Day " + (world ? ticks / 24000 : "—")); height = 43;
                }
                case "item-counter" -> {
                    String choice = core.getChoice(id, "item");
                    Item item = switch (choice) { case "Pearls" -> Items.ENDER_PEARL; case "Crystals" -> Items.END_CRYSTAL; case "Gapples" -> Items.ENCHANTED_GOLDEN_APPLE; case "Arrows" -> Items.ARROW; default -> Items.TOTEM_OF_UNDYING; };
                    int count = 0;
                    if (world) for (int i = 0; i < CLIENT.player.getInventory().size(); i++) { ItemStack stack = CLIENT.player.getInventory().getStack(i); if (stack.isOf(item) || choice.equals("Gapples") && stack.isOf(Items.GOLDEN_APPLE)) count += stack.getCount(); }
                    lines.add(choice.toUpperCase(Locale.ROOT) + "  " + (world ? count : "—"));
                }
                case "keystrokes" -> { width = 102; height = core.getBoolean(id, "mouse") ? 116 : 94; }
                case "reach-display" -> lines.add("TARGET  " + (world && CLIENT.crosshairTarget != null && CLIENT.crosshairTarget.getType() != HitResult.Type.MISS
                        ? String.format(Locale.ROOT, "%.2f m", CLIENT.gameRenderer.getCamera().getPos().distanceTo(CLIENT.crosshairTarget.getPos())) : "—"));
                case "coordinates" -> {
                    lines.add(world ? "X " + CLIENT.player.getBlockX() + "   Y " + CLIENT.player.getBlockY() + "   Z " + CLIENT.player.getBlockZ() : "X —   Y —   Z —");
                    if (core.getBoolean(id, "show-dimension")) lines.add(world ? CLIENT.world.getRegistryKey().getValue().getPath().replace('_', ' ').toUpperCase(Locale.ROOT) : "JOIN A WORLD");
                    width = 205; height = 14 + lines.size() * 15;
                }
                default -> { continue; }
            }
            if (!lines.isEmpty()) {
                int longest = (int) Math.ceil(lines.stream().mapToDouble(line -> textWidth(line, theme, body)).max().orElse(0)) + 17;
                int limit = id.equals("coordinates") ? 360 : 240;
                limit = Math.min(limit, Math.max(48, (int) (CLIENT.getWindow().getScaledWidth() / windowScale(core)) - 16));
                width = Math.min(limit, Math.max(width, longest));
                height = Math.max(height, 14 + lines.size() * rowPitch + (id.equals("target-hud") ? 15 : 0));
            }
            widgets.add(new Widget(id, width, height, List.copyOf(lines), rowPitch, iconPitch));
        }
        return widgets;
    }

    private static float scaleForWindow(AnalyzerCore core) { return windowScale(core); }
    private static LivingEntity target() {
        if (CLIENT.player == null) return null;
        LivingEntity combat = GameplayEngine.target();
        return combat != null ? combat : CLIENT.crosshairTarget instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity living ? living : null;
    }
    private static void panel(DrawContext draw, int x, int y, int width, int height, String theme, int color, double opacity) {
        int background = Palette.alpha(0xFF07121B, (int) (opacity * 240));
        switch (theme) {
            case "Classic" -> { draw.fill(x, y, x + width, y + height, background); draw.fill(x, y, x + width, y + 2, color); }
            case "Minimal" -> { draw.fill(x, y + 4, x + 2, y + height - 4, color); }
            case "Neon" -> { GuiDraw.glow(draw, x, y, width, height, 7, color); GuiDraw.outlined(draw, x, y, width, height, 7, Palette.alpha(color, 180), background); }
            default -> GuiDraw.outlined(draw, x, y, width, height, 7, Palette.alpha(color, 65), background);
        }
    }
    private static void text(DrawContext draw, String text, int x, int y, float scale, int color, String theme) {
        float outer = (float) Math.hypot(draw.getMatrices().peek().getPositionMatrix().m00(), draw.getMatrices().peek().getPositionMatrix().m01());
        float effective = readableScale(theme, outer, scale, true);
        if (!theme.equals("Classic")) { GuiDraw.label(draw, CLIENT.textRenderer, text, x, y, effective, color, theme.equals("Minimal")); return; }
        GuiDraw.bitmapLabel(draw, CLIENT.textRenderer, text, x, y, effective, color);
    }
    private static void drawInventory(DrawContext draw, AnalyzerCore core, Widget widget, int x, int y, String theme) {
        text(draw, "INVENTORY", x + 8, y + 5, 0.72f, Palette.TEXT, theme);
        for (int slot = 0; slot < 27; slot++) {
            int sx = x + 7 + slot % 9 * 18, sy = y + 18 + slot / 9 * 18;
            GuiDraw.rounded(draw, sx, sy, 17, 17, 2, 0x302D5D67);
            if (CLIENT.player == null) continue;
            ItemStack stack = CLIENT.player.getInventory().getStack(slot + 9);
            draw.drawItem(stack, sx, sy);
            if (core.getBoolean(widget.id, "counts")) {
                draw.drawStackOverlay(CLIENT.textRenderer, stack, sx, sy, "");
                if (!stack.isEmpty() && stack.getCount() > 1) {
                    String count = Integer.toString(stack.getCount());
                    float effective = readableScale(theme, scaleForWindow(core), 0.7f, true);
                    text(draw, count, sx + 17 - (int) Math.ceil(textWidth(count, theme, effective)),
                            sy + 17 - (int) Math.ceil(CLIENT.textRenderer.fontHeight * effective), 0.7f, Palette.TEXT, theme);
                }
            }
        }
    }
    private static void drawArmor(DrawContext draw, AnalyzerCore core, Widget widget, int x, int y, String theme) {
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        boolean equipped = CLIENT.player != null && java.util.Arrays.stream(slots).anyMatch(slot -> !CLIENT.player.getEquippedStack(slot).isEmpty());
        text(draw, equipped ? "ARMOR" : "ARMOR / EMPTY", x + 8, y + 5, 0.62f, Palette.TEXT, theme);
        for (int i = 0; i < slots.length; i++) {
            int sx = x + 8 + i * widget.iconPitch;
            GuiDraw.rounded(draw, sx, y + 19, 18, 18, 3, 0x302D5D67);
            ItemStack stack = CLIENT.player == null ? ItemStack.EMPTY : CLIENT.player.getEquippedStack(slots[i]);
            draw.drawItem(stack, sx + 1, y + 20);
            if (core.getBoolean(widget.id, "durability")) {
                String percent = stack.isDamageable() ? Math.round(100.0 * (stack.getMaxDamage() - stack.getDamage()) / stack.getMaxDamage()) + "%" : "—";
                text(draw, percent, sx, y + 43, 0.58f, Palette.TEXT, theme);
            }
        }
    }
    private static void drawKeys(DrawContext draw, AnalyzerCore core, Widget widget, int x, int y, String theme, int color) {
        boolean[] pressed = {CLIENT.options.forwardKey.isPressed(), CLIENT.options.leftKey.isPressed(), CLIENT.options.backKey.isPressed(), CLIENT.options.rightKey.isPressed(), CLIENT.options.jumpKey.isPressed()};
        String[] keys = {"W", "A", "S", "D", "SPACE"};
        int[] xs = {37, 8, 37, 66, 8}, ys = {7, 35, 35, 35, 64};
        for (int i = 0; i < keys.length; i++) {
            int w = i == 4 ? 86 : 27;
            GuiDraw.rounded(draw, x + xs[i], y + ys[i], w, 25, 4, pressed[i] ? Palette.alpha(color, 155) : 0x55233441);
            text(draw, keys[i], x + xs[i] + (i == 4 ? 27 : 9), y + ys[i] + 8, 0.8f, Palette.TEXT, theme);
        }
        if (core.getBoolean(widget.id, "mouse")) for (int i = 0; i < 2; i++) {
            boolean down = GLFW.glfwGetMouseButton(CLIENT.getWindow().getHandle(), i) == GLFW.GLFW_PRESS;
            GuiDraw.rounded(draw, x + 8 + i * 44, y + 93, 42, 18, 3, down ? Palette.alpha(color, 155) : 0x55233441);
            text(draw, i == 0 ? "LMB" : "RMB", x + 17 + i * 44, y + 99, 0.7f, Palette.TEXT, theme);
        }
    }
}
