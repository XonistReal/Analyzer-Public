package dev.analyzer.ui;

import dev.analyzer.core.AnalyzerCore;
import dev.analyzer.core.Category;
import dev.analyzer.core.ModuleDefinition;
import dev.analyzer.gameplay.GameplayEngine;
import dev.analyzer.effects.UiShader;
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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** One layout engine is shared by gameplay rendering, ESP exclusion regions and the editor. */
public final class HudWidgets {
    private static final MinecraftClient CLIENT = MinecraftClient.getInstance();
    private static final String VERSION = FabricLoader.getInstance().getModContainer("analyzer")
            .map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("dev");
    public static final List<String> IDS = List.of("watermark", "active-modules", "fps", "ping", "speed-hud", "armor", "potions",
            "target-hud", "keybinds", "inventory", "compass", "biome", "world-clock", "item-counter", "keystrokes", "reach-display", "coordinates");
    private static final int WHITE = 0xFFF7F5F8, SECONDARY = 0xFFBBB7C0;
    private static final Map<String, Long> SHADER_WIDGET_FRAMES = new HashMap<>();
    private static long shaderSurfaces;
    private static List<String> lastRenderedIds = List.of();
    private static List<WidgetBounds> lastRenderedBounds = List.of();
    private HudWidgets() { }

    public record WidgetBounds(String id, int x, int y, int width, int height) {
        public boolean contains(double px, double py) { return px >= x && py >= y && px < x + width && py < y + height; }
    }
    private record Widget(String id, int width, int height, String title, List<String> lines, int rowPitch, int iconPitch) { }
    /** Native-render evidence, separate from menu shader draws. */
    public static long shaderSurfaces() { return shaderSurfaces; }
    public static long shaderWidgetFrames(String id) { return SHADER_WIDGET_FRAMES.getOrDefault(id, 0L); }
    public static List<String> lastRenderedWidgetIds() { return lastRenderedIds; }
    public static List<WidgetBounds> lastRenderedBounds() { return lastRenderedBounds; }

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
        float nominalSize = 12;
        float minimum = preferred >= 0.85f ? 14 : 12;
        float requested = GuiDraw.effectiveScale(preferred);
        return Math.max(requested, (float) (minimum / (nominalSize * physical)));
    }
    private static float textWidth(String value, String theme, float effectiveScale) {
        return GuiDraw.width(CLIENT.textRenderer, value, effectiveScale);
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
        return switch (core.getChoice("menu", "accent")) {
            case "Rose" -> 0xFFDB85DA;
            case "Violet" -> Palette.VIOLET;
            case "Gold" -> Palette.GOLD;
            default -> Palette.MINT;
        };
    }
    public static List<WidgetBounds> bounds(AnalyzerCore core, DrawContext draw, boolean editor) {
        try (GuiDraw.FontScope ignored = GuiDraw.sansFont()) {
            return scopedBounds(core, draw, editor);
        }
    }
    private static List<WidgetBounds> scopedBounds(AnalyzerCore core, DrawContext draw, boolean editor) {
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
        try (GuiDraw.FontScope ignored = GuiDraw.sansFont()) {
            return scopedRender(core, draw, editor);
        }
    }
    private static List<WidgetBounds> scopedRender(AnalyzerCore core, DrawContext draw, boolean editor) {
        List<WidgetBounds> result = scopedBounds(core, draw, editor);
        if (result.isEmpty()) { lastRenderedIds = List.of(); lastRenderedBounds = List.of(); return result; }
        float scale = scale(core, draw);
        int width = Math.round(draw.getScaledWindowWidth() / scale), height = Math.round(draw.getScaledWindowHeight() / scale);
        String theme = core.getChoice("hud", "theme");
        int accent = accent(core);
        List<String> renderedIds = new ArrayList<>();
        draw.getMatrices().push();
        draw.getMatrices().scale(scale, scale, 1);
        try {
        for (Widget widget : widgets(core, editor)) {
            int x = (int) Math.round(position(core, widget.id, true) * Math.max(0, width - widget.width) / 100);
            int y = (int) Math.round(position(core, widget.id, false) * Math.max(0, height - widget.height) / 100);
            boolean enabled = core.isEnabled(widget.id);
            double opacity = widget.id.equals("coordinates") ? 0.8 : core.getNumber(widget.id, "opacity");
            int color = enabled ? accent : Palette.SUBTLE;
            if (panel(draw, x, y, widget.width, widget.height, theme, color, opacity * (enabled ? 1 : 0.45)))
                SHADER_WIDGET_FRAMES.merge(widget.id, 1L, Long::sum);
            renderedIds.add(widget.id);
            if (widget.id.equals("watermark")) {
                BrandAssets.monogram(draw, x + 11, y + 8, 24);
                BrandAssets.wordmark(draw, x + 46, y + 12, Math.min(95, widget.width - 57));
                int versionY = y + widget.height - (int) Math.ceil(12 * readableScale(theme, scale, .7f, true)) - 7;
                text(draw, VERSION + "  ·  " + theme, x + 11, versionY, 0.7f, SECONDARY, theme);
            } else if (widget.id.equals("inventory")) drawInventory(draw, core, widget, x, y, theme);
            else if (widget.id.equals("armor")) drawArmor(draw, core, widget, x, y, theme);
            else if (widget.id.equals("keystrokes")) drawKeys(draw, core, widget, x, y, theme, color);
            else {
                int contentY = y + 10;
                if (!widget.title.isEmpty()) {
                    heading(draw, widget.title, x + 11, y + 9, .84f, WHITE, theme);
                    contentY = y + headingPitch(theme, scale);
                }
                for (int i = 0; i < widget.lines.size(); i++) {
                    float fontScale = readableScale(theme, scale, 0.92f, true);
                    String line = fitText(widget.lines.get(i), theme, fontScale, widget.width - 22);
                    boolean secondary = i > 0 && !widget.title.isEmpty() && line.startsWith("  ") || i > 0 && widget.id.equals("target-hud")
                            || widget.id.equals("coordinates") && i > 0 || widget.id.equals("world-clock") && i > 0;
                    int lineColor = line.startsWith("+ ") ? color : secondary ? SECONDARY : WHITE;
                    if (i == 0 && widget.title.isEmpty() && !widget.id.equals("coordinates") && !widget.id.equals("target-hud") && line.contains("  "))
                        metric(draw, line, x + 11, contentY, color, theme);
                    else if (widget.id.equals("target-hud") && i == 0) heading(draw, line, x + 11, contentY, .92f, WHITE, theme);
                    else text(draw, line, x + 11, contentY + i * widget.rowPitch, .92f, lineColor, theme);
                }
                if (widget.id.equals("target-hud") && target() != null) {
                    LivingEntity target = target();
                    int bar = Math.round((widget.width - 22) * Math.clamp(target.getHealth() / Math.max(1, target.getMaxHealth()), 0, 1));
                    surface(draw, x + 11, y + widget.height - 12, widget.width - 22, 4, 2, 0xFF505054, 0xFF505054, 0, 0, 0, 0);
                    surface(draw, x + 11, y + widget.height - 12, bar, 4, 2, color, color, 0, 0, Palette.alpha(color, 35), 3);
                }
            }
            if (editor) {
                text(draw, (enabled ? "" : "OFF / ") + widget.id, x + 3, Math.max(0, y - 11), 0.66f, color, theme);
            }
        }
        } finally { draw.getMatrices().pop(); }
        lastRenderedIds = List.copyOf(renderedIds);
        lastRenderedBounds = List.copyOf(result);
        return result;
    }

    private static List<Widget> widgets(AnalyzerCore core, boolean editor) {
        List<Widget> widgets = new ArrayList<>();
        String theme = core.getChoice("hud", "theme");
        float body = readableScale(theme, windowScale(core), 0.92f, true);
        float secondary = readableScale(theme, windowScale(core), 0.7f, true);
        int rowPitch = Math.max(17, (int) Math.ceil(12 * body) + 5);
        int iconPitch = Math.max(22, (int) Math.ceil(textWidth("100%", theme, secondary)) + 5);
        boolean world = CLIENT.player != null && CLIENT.world != null;
        for (String id : IDS) {
            if (!core.isEnabled(id) && !(editor && core.getBoolean("hud", "show-disabled"))) continue;
            List<String> lines = new ArrayList<>();
            String title = "";
            int width = 72, height = 32;
            switch (id) {
                case "watermark" -> {
                    float titleScale = readableScale(theme, windowScale(core), 1.16f, true);
                    width = Math.max(128, (int) Math.ceil(textWidth("Analyzer", theme, titleScale)) + 22);
                    height = Math.max(51, 27 + (int) Math.ceil(12 * titleScale) + (int) Math.ceil(12 * secondary));
                }
                case "active-modules" -> {
                    title = "Active modules";
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
                    width = 182; height = 40 + lines.size() * rowPitch;
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
                case "armor" -> {
                    width = Math.max(108, 19 + iconPitch * 4);
                    height = headingPitch(theme, windowScale(core)) + (core.getBoolean(id, "durability") ? 30 + (int) Math.ceil(12 * secondary) : 25);
                }
                case "potions" -> {
                    title = "Effects";
                    if (world) for (StatusEffectInstance effect : CLIENT.player.getStatusEffects()) {
                        String name = Text.translatable(effect.getEffectType().value().getTranslationKey()).getString();
                        int seconds = Math.max(0, effect.getDuration() / 20);
                        lines.add(name + " " + (effect.getAmplifier() + 1) + " · " + (effect.isInfinite() ? "∞" : seconds / 60 + ":" + String.format(Locale.ROOT, "%02d", seconds % 60)));
                        if (lines.size() == 6) break;
                    }
                    if (lines.isEmpty()) lines.add(world ? "No active effects" : "Join a world");
                    width = 184; height = 40 + lines.size() * rowPitch;
                }
                case "target-hud" -> {
                    LivingEntity target = target();
                    lines.add(target == null ? "No target" : target.getDisplayName().getString());
                    lines.add(target == null ? "Aim at a living entity" : String.format(Locale.ROOT, "%.1f / %.1f HP · %.1fm", target.getHealth(), target.getMaxHealth(), CLIENT.player.distanceTo(target)));
                    if (target != null) lines.add("Armor " + target.getArmor() + " · " + (target.isAlive() ? "Alive" : "Defeated"));
                    width = 194; height = 78;
                }
                case "keybinds" -> {
                    title = "Keybinds";
                    for (ModuleDefinition module : core.modules()) {
                        int key = core.keybind(module.id());
                        if (key < 0 || (core.getBoolean(id, "active-only") && !core.isEnabled(module.id()))) continue;
                        lines.add((core.isEnabled(module.id()) ? "● " : "○ ") + module.name() + " · " + InputUtil.fromKeyCode(key, 0).getLocalizedText().getString());
                        if (lines.size() == 7) break;
                    }
                    if (lines.isEmpty()) lines.add("No assigned binds");
                    width = 190; height = 40 + lines.size() * rowPitch;
                }
                case "inventory" -> { width = 191; height = headingPitch(theme, windowScale(core)) + 64; }
                case "compass" -> {
                    double yaw = world ? Math.floorMod((int) CLIENT.player.getYaw(), 360) : 0;
                    String[] directions = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
                    lines.add("COMPASS  " + (world ? directions[(int) Math.round(yaw / 45) % 8] + " · " + Math.round(yaw) + "°" : "—"));
                }
                case "biome" -> {
                    String biome = world ? CLIENT.world.getBiome(CLIENT.player.getBlockPos()).getKey().map(key -> key.getValue().getPath().replace('_', ' ')).orElse("unknown") : "join a world";
                    lines.add("BIOME  " + biome); width = 142;
                }
                case "world-clock" -> {
                    long ticks = world ? CLIENT.world.getTimeOfDay() : 0;
                    int totalMinutes = (int) ((Math.floorMod(ticks, 24000) / 1000.0 + 6) * 60) % 1440;
                    int hour = totalMinutes / 60;
                    boolean twelve = core.getChoice(id, "format").equals("12 hour");
                    lines.add("TIME  " + (world ? String.format(Locale.ROOT, "%02d:%02d", twelve ? (hour + 11) % 12 + 1 : hour, totalMinutes % 60) + (twelve ? hour < 12 ? " AM" : " PM" : "") : "—"));
                    lines.add("Day " + (world ? ticks / 24000 : "—")); height = 50;
                }
                case "item-counter" -> {
                    String choice = core.getChoice(id, "item");
                    Item item = switch (choice) { case "Pearls" -> Items.ENDER_PEARL; case "Crystals" -> Items.END_CRYSTAL; case "Gapples" -> Items.ENCHANTED_GOLDEN_APPLE; case "Arrows" -> Items.ARROW; default -> Items.TOTEM_OF_UNDYING; };
                    int count = 0;
                    if (world) for (int i = 0; i < CLIENT.player.getInventory().size(); i++) { ItemStack stack = CLIENT.player.getInventory().getStack(i); if (stack.isOf(item) || choice.equals("Gapples") && stack.isOf(Items.GOLDEN_APPLE)) count += stack.getCount(); }
                    lines.add(choice.toUpperCase(Locale.ROOT) + "  " + (world ? count : "—"));
                }
                case "keystrokes" -> { width = 110; height = core.getBoolean(id, "mouse") ? 130 : 103; }
                case "reach-display" -> lines.add("TARGET  " + (world && CLIENT.crosshairTarget != null && CLIENT.crosshairTarget.getType() != HitResult.Type.MISS
                        ? String.format(Locale.ROOT, "%.2f m", CLIENT.gameRenderer.getCamera().getPos().distanceTo(CLIENT.crosshairTarget.getPos())) : "—"));
                case "coordinates" -> {
                    lines.add(world ? "X " + CLIENT.player.getBlockX() + "   Y " + CLIENT.player.getBlockY() + "   Z " + CLIENT.player.getBlockZ() : "X —   Y —   Z —");
                    if (core.getBoolean(id, "show-dimension")) lines.add(world ? CLIENT.world.getRegistryKey().getValue().getPath().replace('_', ' ') : "Join a world");
                    width = 178; height = 22 + lines.size() * rowPitch;
                }
                default -> { continue; }
            }
            if (!lines.isEmpty()) {
                int longest = (int) Math.ceil(lines.stream().mapToDouble(line -> textWidth(line, theme, body)).max().orElse(0)) + 22;
                int limit = id.equals("coordinates") ? 360 : 240;
                limit = Math.min(limit, Math.max(48, (int) (CLIENT.getWindow().getScaledWidth() / windowScale(core)) - 16));
                width = Math.min(limit, Math.max(width, longest));
                height = Math.max(height, (title.isEmpty() ? 21 : headingPitch(theme, windowScale(core)) + 10)
                        + lines.size() * rowPitch + (id.equals("target-hud") ? 15 : 0));
            }
            widgets.add(new Widget(id, width, height, title, List.copyOf(lines), rowPitch, iconPitch));
        }
        return widgets;
    }

    private static float scaleForWindow(AnalyzerCore core) { return windowScale(core); }
    private static int headingPitch(String theme, float outer) {
        return Math.max(31, 20 + (int) Math.ceil(12 * readableScale(theme, outer, .84f, true)));
    }
    private static LivingEntity target() {
        if (CLIENT.player == null) return null;
        LivingEntity combat = GameplayEngine.target();
        return combat != null ? combat : CLIENT.crosshairTarget instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity living ? living : null;
    }
    /** The same SDF surface for gameplay, the editor, Radar and safety notices. */
    public static boolean panel(DrawContext draw, int x, int y, int width, int height, String theme, int color, double opacity) {
        double alpha = Math.clamp(opacity, 0, 1);
        int fill = switch (theme) {
            case "Classic" -> 0xFF19191C;
            case "Minimal" -> 0xFF151519;
            case "Neon" -> 0xFF211B27;
            default -> 0xFF232326;
        };
        int top = Palette.alpha(fill, (int) Math.round(alpha * (theme.equals("Minimal") ? 190 : theme.equals("Glass") ? 232 : 255)));
        int bottom = Palette.alpha(Palette.mix(fill, 0xFF070709, .30), top >>> 24);
        int shadow = Palette.alpha(0xFF000000, (int) (alpha * (theme.equals("Minimal") ? 26 : 72)));
        int glow = theme.equals("Neon") ? Palette.alpha(color, (int) (alpha * 26)) : 0;
        return surface(draw, x, y, width, height, 11, top, bottom, shadow, 5, glow, 6);
    }
    /** Every visible rounded HUD primitive takes this native shader path first. */
    private static boolean surface(DrawContext draw, float x, float y, float width, float height, float radius,
                                   int top, int bottom, int shadow, float shadowBlur, int glow, float glowBlur) {
        boolean rendered = UiShader.surface(draw, x, y, width, height, radius, top, bottom, shadow, shadowBlur, glow, glowBlur);
        if (rendered && width > 0 && height > 0) shaderSurfaces++;
        else if (!rendered) GuiDraw.rounded(draw, Math.round(x), Math.round(y), Math.round(width), Math.round(height), Math.round(radius), top);
        return rendered;
    }
    private static void inset(DrawContext draw, int x, int y, int width, int height, int color, boolean active) {
        int top = active ? Palette.alpha(Palette.mix(0xFF373339, color, .53), 238) : 0xB836363A;
        int bottom = active ? Palette.alpha(Palette.mix(0xFF242127, color, .39), 238) : 0xC026262A;
        surface(draw, x, y, width, height, Math.min(7, height / 3f), top, bottom, 0x16000000, 2,
                active ? Palette.alpha(color, 50) : 0, active ? 4 : 0);
    }
    private static void metric(DrawContext draw, String value, int x, int y, int color, String theme) {
        int split = value.indexOf("  ");
        String label = value.substring(0, split), content = value.substring(split).stripLeading();
        text(draw, label, x, y + 1, .72f, SECONDARY, theme);
        float outer = (float) Math.hypot(draw.getMatrices().peek().getPositionMatrix().m00(), draw.getMatrices().peek().getPositionMatrix().m01());
        int offset = (int) Math.ceil(textWidth(label, theme, readableScale(theme, outer, .72f, true))) + 7;
        text(draw, content, x + offset, y, .92f, color, theme);
    }
    private static void text(DrawContext draw, String text, int x, int y, float scale, int color, String theme) {
        float outer = (float) Math.hypot(draw.getMatrices().peek().getPositionMatrix().m00(), draw.getMatrices().peek().getPositionMatrix().m01());
        float effective = readableScale(theme, outer, scale, true);
        GuiDraw.label(draw, CLIENT.textRenderer, text, x, y, effective, color, false);
    }
    private static void heading(DrawContext draw, String text, int x, int y, float scale, int color, String theme) {
        float outer = (float) Math.hypot(draw.getMatrices().peek().getPositionMatrix().m00(), draw.getMatrices().peek().getPositionMatrix().m01());
        GuiDraw.heading(draw, CLIENT.textRenderer, text, x, y, readableScale(theme, outer, scale, true), color);
    }
    private static void drawInventory(DrawContext draw, AnalyzerCore core, Widget widget, int x, int y, String theme) {
        heading(draw, "Inventory", x + 11, y + 9, .84f, WHITE, theme);
        int contentY = y + headingPitch(theme, scaleForWindow(core));
        for (int slot = 0; slot < 27; slot++) {
            int sx = x + 10 + slot % 9 * 19, sy = contentY + slot / 9 * 19;
            inset(draw, sx, sy, 18, 18, accent(core), false);
            if (CLIENT.player == null) continue;
            ItemStack stack = CLIENT.player.getInventory().getStack(slot + 9);
            draw.drawItem(stack, sx, sy);
            if (core.getBoolean(widget.id, "counts")) {
                draw.drawStackOverlay(CLIENT.textRenderer, stack, sx, sy, "");
                if (!stack.isEmpty() && stack.getCount() > 1) {
                    String count = Integer.toString(stack.getCount());
                    float effective = readableScale(theme, scaleForWindow(core), 0.7f, true);
                    text(draw, count, sx + 17 - (int) Math.ceil(textWidth(count, theme, effective)),
                            sy + 17 - (int) Math.ceil(12 * effective), 0.7f, WHITE, theme);
                }
            }
        }
    }
    private static void drawArmor(DrawContext draw, AnalyzerCore core, Widget widget, int x, int y, String theme) {
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        boolean equipped = CLIENT.player != null && java.util.Arrays.stream(slots).anyMatch(slot -> !CLIENT.player.getEquippedStack(slot).isEmpty());
        heading(draw, equipped ? "Armor" : "Armor · empty", x + 11, y + 9, .75f, WHITE, theme);
        int contentY = y + headingPitch(theme, scaleForWindow(core));
        for (int i = 0; i < slots.length; i++) {
            int sx = x + 10 + i * widget.iconPitch;
            inset(draw, sx, contentY, 18, 18, accent(core), false);
            ItemStack stack = CLIENT.player == null ? ItemStack.EMPTY : CLIENT.player.getEquippedStack(slots[i]);
            draw.drawItem(stack, sx + 1, contentY + 1);
            if (core.getBoolean(widget.id, "durability")) {
                String percent = stack.isDamageable() ? Math.round(100.0 * (stack.getMaxDamage() - stack.getDamage()) / stack.getMaxDamage()) + "%" : "—";
                text(draw, percent, sx, contentY + 22, 0.58f, SECONDARY, theme);
            }
        }
    }
    private static void drawKeys(DrawContext draw, AnalyzerCore core, Widget widget, int x, int y, String theme, int color) {
        boolean[] pressed = {CLIENT.options.forwardKey.isPressed(), CLIENT.options.leftKey.isPressed(), CLIENT.options.backKey.isPressed(), CLIENT.options.rightKey.isPressed(), CLIENT.options.jumpKey.isPressed()};
        String[] keys = {"W", "A", "S", "D", "SPACE"};
        int[] xs = {40, 10, 40, 70, 10}, ys = {10, 40, 40, 40, 70};
        for (int i = 0; i < keys.length; i++) {
            int w = i == 4 ? 90 : 30 - 3;
            inset(draw, x + xs[i], y + ys[i], w, 26, color, pressed[i]);
            centeredText(draw, keys[i], x + xs[i], y + ys[i], w, 26, .8f, WHITE, theme);
        }
        if (core.getBoolean(widget.id, "mouse")) for (int i = 0; i < 2; i++) {
            boolean down = GLFW.glfwGetMouseButton(CLIENT.getWindow().getHandle(), i) == GLFW.GLFW_PRESS;
            inset(draw, x + 10 + i * 47, y + 100, 43, 20, color, down);
            centeredText(draw, i == 0 ? "LMB" : "RMB", x + 10 + i * 47, y + 100, 43, 20, .7f, WHITE, theme);
        }
    }
    private static void centeredText(DrawContext draw, String value, int x, int y, int width, int height, float preferred, int color, String theme) {
        float outer = (float) Math.hypot(draw.getMatrices().peek().getPositionMatrix().m00(), draw.getMatrices().peek().getPositionMatrix().m01());
        float effective = readableScale(theme, outer, preferred, true);
        int tx = x + Math.round((width - textWidth(value, theme, effective)) / 2);
        int ty = y + Math.round((height - 12 * effective) / 2);
        text(draw, value, tx, ty, preferred, color, theme);
    }
}
