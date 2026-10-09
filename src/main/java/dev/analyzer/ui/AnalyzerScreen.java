package dev.analyzer.ui;

import dev.analyzer.AnalyzerClient;
import dev.analyzer.core.AnalyzerCore;
import dev.analyzer.core.Category;
import dev.analyzer.core.GameplayModules;
import dev.analyzer.core.ModuleDefinition;
import dev.analyzer.core.Setting;
import dev.analyzer.core.UtilityModules;
import dev.analyzer.core.VisualModules;
import dev.analyzer.effects.GpuEffects;
import dev.analyzer.gameplay.GameplayEngine;
import dev.analyzer.gameplay.UtilityEngine;
import dev.analyzer.render.AnalyzerRenderer;
import dev.analyzer.render.VisualEffects;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.DimensionEffects;
import net.minecraft.client.util.InputUtil;
import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.Set;
import java.util.stream.Collectors;

/** Original Analyzer click GUI, with a logical canvas that fits every Minecraft GUI scale. */
public final class AnalyzerScreen extends Screen {
    private static final int CANVAS_W = 900, CANVAS_H = 520;
    private static final Rect MODULE_VIEW = new Rect(180, 269, 409, 203);
    private static final Rect SETTINGS_VIEW = new Rect(627, 218, 251, 204);
    private static final Rect PROFILE_VIEW = new Rect(181, 241, 408, 231);
    private static final Set<String> GAMEPLAY_IDS = moduleIds(GameplayModules.definitions());
    private static final Set<String> UTILITY_IDS = moduleIds(UtilityModules.definitions());
    private static final Set<String> VISUAL_IDS = moduleIds(VisualModules.definitions());
    private static final Set<String> COSMETIC_IDS = moduleIds(dev.analyzer.core.CosmeticModules.definitions());
    private final AnalyzerCore core;
    private final List<Hit> hits = new ArrayList<>();
    private final Map<String, Rect> controlBounds = new HashMap<>();
    private double categoryScroll;
    private static final Rect CATEGORY_VIEW = new Rect(16, 156, 129, 157);
    private final Map<String, Double> animation = new HashMap<>();
    private static final int MAX_PARTICLES = 96;
    private final AmbientParticle[] particles = new AmbientParticle[MAX_PARTICLES];
    private int visibleParticles;
    private double animationTime, motion = 0.8;
    private int accentColor = Palette.MINT;
    private boolean glass = true;
    private ModuleDefinition selected;
    private String category = "ALL", query = "", profileName = "";
    private String selectedProfile = "", captureModule = null;
    private List<String> profiles = List.of();
    private Focus focus = Focus.NONE;
    private boolean selectAll, dirty, error;
    private String status = "Your perspective. Your rules.";
    private long changedAt, statusAt, lastFrame = System.nanoTime();
    private final long openedAt = System.nanoTime();
    private double scroll, settingsScroll, profileScroll, mx, my, frameSeconds;
    private float canvasScale = 1, canvasX, canvasY;
    private SliderDrag dragging;

    public AnalyzerScreen(AnalyzerCore core) {
        super(Text.literal("Analyzer"));
        this.core = core;
        this.selected = core.modules().isEmpty() ? null : core.modules().getFirst();
        SplittableRandom random = new SplittableRandom(0xA11A7EFL);
        for (int i = 0; i < particles.length; i++) {
            particles[i] = new AmbientParticle(random.nextDouble(-20, CANVAS_W + 20), random.nextDouble(-20, CANVAS_H + 20),
                    random.nextDouble(6, 20) * (i % 2 == 0 ? 1 : -1), random.nextDouble(-10, 10), random.nextDouble(0, Math.PI * 2));
        }
        refreshProfiles();
    }

    @Override public boolean shouldPause() { return false; }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        frameSeconds = Math.clamp((now - lastFrame) / 1_000_000_000.0, 0.001, 0.05);
        lastFrame = now;
        updateMaterial();
        animationTime += frameSeconds * motion;
        double entrance = 1 - Math.exp(-(now - openedAt) / 160_000_000.0);
        canvasScale = Math.min(1.25f, Math.min((width - 20f) / CANVAS_W, (height - 20f) / CANVAS_H));
        canvasScale = Math.max(0.15f, canvasScale);
        canvasX = (width - CANVAS_W * canvasScale) / 2;
        canvasY = (height - CANVAS_H * canvasScale) / 2 + (float) ((1 - entrance) * 14 * canvasScale);
        mx = (mouseX - canvasX) / canvasScale;
        my = (mouseY - canvasY) / canvasScale;
        updateParticles();
        hits.clear(); controlBounds.clear();
        GpuEffects.drawBackdrop(context);
        if (!GpuEffects.isHealthy()) context.fillGradient(0, 0, width, height, 0xB3050A10, 0xD9060C10);
        context.getMatrices().push();
        context.getMatrices().translate(canvasX, canvasY, 0);
        context.getMatrices().scale(canvasScale, canvasScale, 1);
        GuiDraw.glow(context, 0, 0, CANVAS_W, CANVAS_H, 18, accentColor);
        GuiDraw.glow(context, 189, 10, 390, 120, 22, Palette.VIOLET);
        GuiDraw.outlined(context, 0, 0, CANVAS_W, CANVAS_H, 18, Palette.BRIGHT_BORDER, glass ? Palette.GLASS : Palette.BACKGROUND);
        GuiDraw.rounded(context, 1, 1, 158, CANVAS_H - 2, 17, Palette.alpha(Palette.SURFACE, glass ? 226 : 255));
        context.fill(144, 1, 159, CANVAS_H - 1, Palette.alpha(Palette.SURFACE, glass ? 226 : 255));
        clip(context, new Rect(3, 3, CANVAS_W - 6, CANVAS_H - 6));
        drawParticleField(context, new Rect(3, 3, CANVAS_W - 6, CANVAS_H - 6), 1.0);
        drawComets(context);
        context.disableScissor();
        context.fill(159, 20, 160, CANVAS_H - 20, Palette.BORDER);
        context.fill(607, 20, 608, 480, Palette.BORDER);
        drawSidebar(context);
        drawHeader(context);
        if (category.equals("PROFILES")) drawProfiles(context);
        else { drawModules(context); drawInspector(context); }
        drawFooter(context);
        context.getMatrices().pop();
    }

    private void updateMaterial() {
        boolean hasMenu = core.modules().stream().anyMatch(module -> module.id().equals("menu"));
        int targetAccent = Palette.MINT;
        glass = true;
        motion = 0.8;
        if (hasMenu) {
            boolean custom = core.isEnabled("menu");
            glass = custom && core.getChoice("menu", "material").equals("Glass");
            motion = custom ? core.getNumber("menu", "motion") : 0;
            if (custom) targetAccent = switch (core.getChoice("menu", "accent")) {
                case "Violet" -> Palette.VIOLET;
                case "Gold" -> Palette.GOLD;
                default -> Palette.MINT;
            };
        }
        accentColor = Palette.mix(accentColor, targetAccent, 1 - Math.exp(-frameSeconds * 12));
    }

    private void updateParticles() {
        visibleParticles = core.isEnabled("particles") ? (int) Math.round(core.getNumber("particles", "density") * MAX_PARTICLES) : 0;
        double dt = frameSeconds * motion;
        for (int i = 0; i < visibleParticles; i++) {
            AmbientParticle p = particles[i];
            double dx = p.x - mx, dy = p.y - my;
            double distance = Math.hypot(dx, dy);
            double push = distance > 1 && distance < 120 ? (1 - distance / 120) * 155 : 0;
            double targetVx = p.baseVx + (distance > 1 ? dx / distance * push : 0);
            double targetVy = p.baseVy + Math.sin(animationTime * 0.6 + p.phase) * 5 + (distance > 1 ? dy / distance * push : 0);
            p.vx += (targetVx - p.vx) * (1 - Math.exp(-dt * 3));
            p.vy += (targetVy - p.vy) * (1 - Math.exp(-dt * 3));
            p.x += p.vx * dt; p.y += p.vy * dt;
            if (p.x < -24) p.x = CANVAS_W + 23;
            else if (p.x > CANVAS_W + 24) p.x = -23;
            if (p.y < -24) p.y = CANVAS_H + 23;
            else if (p.y > CANVAS_H + 24) p.y = -23;
        }
    }

    private int particleColor(int index) {
        return index % 7 == 0 ? Palette.CYAN : index % 3 == 0 ? Palette.VIOLET : accentColor;
    }

    private void drawParticleField(DrawContext c, Rect window, double strength) {
        if (visibleParticles == 0) return;
        int cursorLinks = 0;
        for (int i = 0; i < visibleParticles; i++) {
            AmbientParticle p = particles[i];
            if (!window.contains(p.x, p.y)) continue;
            int color = particleColor(i);
            int links = 0;
            for (int j = i + 1; j < visibleParticles && links < 2; j++) {
                AmbientParticle other = particles[j];
                if (!window.contains(other.x, other.y)) continue;
                double distance = Math.hypot(p.x - other.x, p.y - other.y);
                if (distance < 118 && distance > 8) {
                    int alpha = (int) (strength * (102 - distance * 0.57));
                    GuiDraw.stroke(c, p.x, p.y, other.x, other.y, 0.7f, Palette.alpha(color, alpha));
                    links++;
                }
            }
            double distanceToCursor = Math.hypot(p.x - mx, p.y - my);
            if (window.contains(mx, my) && distanceToCursor < 142 && cursorLinks < 6) {
                GuiDraw.stroke(c, p.x, p.y, mx, my, 0.7f,
                        Palette.alpha(accentColor, (int) (strength * (130 - distanceToCursor * 0.65))));
                cursorLinks++;
            }
            float radius = i % 11 == 0 ? 2.4f : i % 3 == 0 ? 1.65f : 1.1f;
            int brightness = (int) (strength * (195 + Math.sin(animationTime * 1.3 + p.phase) * 45));
            GuiDraw.glowDot(c, p.x, p.y, radius, color, brightness);
            if (i % 11 == 0) {
                GuiDraw.stroke(c, p.x - 5, p.y, p.x + 6, p.y, 0.7f, Palette.alpha(color, (int) (strength * 85)));
                GuiDraw.stroke(c, p.x, p.y - 5, p.x, p.y + 6, 0.7f, Palette.alpha(color, (int) (strength * 85)));
            }
        }
    }

    private void drawComets(DrawContext c) {
        if (visibleParticles == 0 || motion <= 0) return;
        for (int comet = 0; comet < 2; comet++) {
            double x = (animationTime * (72 + comet * 21) + comet * 487) % 1150 - 100;
            double y = comet == 0 ? 77 + Math.sin(animationTime * 0.35) * 12 : 398 + Math.sin(animationTime * 0.4 + 2) * 35;
            int color = comet == 0 ? accentColor : Palette.VIOLET;
            for (int segment = 14; segment > 0; segment--) {
                double sx = x - segment * 6;
                double sy = y + segment * (comet == 0 ? 0.42 : -0.3);
                GuiDraw.stroke(c, sx, sy, sx + 7, sy - (comet == 0 ? 0.42 : -0.3), 1.1f,
                        Palette.alpha(color, 120 - segment * 7));
            }
            GuiDraw.glowDot(c, x, y, 2.1f, color, 242);
        }
    }

    private String liveStatus(ModuleDefinition module) {
        String id = module.id();
        if (!core.isEnabled(id)) return "Disabled · preview shown";
        if (id.equals("particles")) return visibleParticles == 0 ? "Density is zero · increase it below" : visibleParticles + " luminous particles · " + (motion > 0 ? "cursor reactive" : "static motion");
        if (id.equals("menu")) return core.getChoice(id, "material") + " material · " + core.getChoice(id, "accent") + " accent";
        if ((id.equals("shader") || id.equals("sky")) && !GpuEffects.isHealthy()) return "Effect unavailable · " + GpuEffects.status();
        if (client == null || client.world == null || client.player == null) return "Enabled · enter a world to render";
        if (GAMEPLAY_IDS.contains(id)) return GameplayEngine.status(id);
        if (UTILITY_IDS.contains(id)) return UtilityEngine.status(id);
        if (COSMETIC_IDS.contains(id)) return dev.analyzer.render.CosmeticRenderer.status(id);
        if (module.category() == Category.COSMETICS) return VisualEffects.status(id);
        if (VISUAL_IDS.contains(id)) return VisualEffects.status(id);
        return switch (id) {
            case "entity-esp", "storage-esp", "tracers" -> AnalyzerRenderer.status(id);
            case "shader" -> core.getChoice(id, "preset").equals("Off") ? "Preset is Off · choose a look below" : core.getNumber(id, "strength") == 0 ? "Strength is zero · increase it below" : core.getChoice(id, "preset") + " post effect · " + Math.round(core.getNumber(id, "strength") * 100) + "% strength";
            case "sky" -> {
                if (client.world.getDimensionEffects().getSkyType() != DimensionEffects.SkyType.NORMAL) yield "Custom sky needs an Overworld sky";
                if (client.gameRenderer.getCamera().getSubmersionType() != CameraSubmersionType.NONE) yield "Sky hidden while submerged";
                if (core.getNumber(id, "intensity") == 0) yield "Intensity is zero · increase it below";
                if (core.getChoice(id, "day-night").equals("Night") && client.world.getStarBrightness(client.getRenderTickCounter().getTickDelta(false)) < 0.001f) yield "Waiting for night · preview shown";
                yield core.getChoice(id, "preset") + " sky · " + Math.round(core.getNumber(id, "intensity") * 100) + "% intensity";
            }
            case "fullbright" -> "Lighting boost · " + number(core.getNumber(id, "gamma")) + "× brightness";
            case "crosshair" -> "Live HUD · close menu to see crosshair";
            case "coordinates" -> "Live position · " + client.player.getBlockPos().toShortString();
            default -> "Enabled · settings applied";
        };
    }

    private static Set<String> moduleIds(List<ModuleDefinition> modules) {
        return modules.stream().map(ModuleDefinition::id).collect(Collectors.toUnmodifiableSet());
    }

    private int statusColor(ModuleDefinition module) {
        if (!core.isEnabled(module.id())) return Palette.MUTED;
        if ((module.id().equals("shader") || module.id().equals("sky")) && !GpuEffects.isHealthy()) return Palette.ERROR;
        if (!module.id().equals("particles") && !module.id().equals("menu") && (client == null || client.world == null)) return Palette.GOLD;
        return accentColor;
    }

    private String previewKind(ModuleDefinition module) {
        return switch (module.id()) {
            case "sky" -> "CUSTOM SKY / CELESTIAL EFFECT";
            case "shader" -> "POST EFFECT / SCENE PROCESSING";
            case "entity-esp", "storage-esp" -> core.getChoice(module.id(), "style").toUpperCase(Locale.ROOT) + " ESP / " + (core.getBoolean(module.id(), "through-walls") ? "THROUGH WALLS" : "VISIBLE TARGETS");
            case "tracers" -> "WORLD TRACERS / SETTINGS PREVIEW";
            case "particles" -> "MENU PARTICLES / LIVE FIELD";
            case "menu" -> "INTERFACE / LIVE MATERIAL";
            default -> module.category().label().toUpperCase(Locale.ROOT) + " / LIVE CONFIGURATION";
        };
    }

    private void drawPreview(DrawContext c, ModuleDefinition module, Rect area) {
        String id = module.id();
        boolean enabled = core.isEnabled(id);
        int color = enabled ? accentColor : Palette.SUBTLE;
        GuiDraw.outlined(c, area.x, area.y, area.w, area.h, 8, Palette.alpha(accentColor, 70), 0xBC0B1723);
        clip(c, new Rect(area.x + 2, area.y + 2, area.w - 4, area.h - 4));
        for (int x = area.x + 12; x < area.right(); x += 15) GuiDraw.stroke(c, x, area.y + 3, x, area.bottom() - 3, 0.5f, 0x1638AAAE);
        for (int y = area.y + 12; y < area.bottom(); y += 15) GuiDraw.stroke(c, area.x + 3, y, area.right() - 3, y, 0.5f, 0x1638AAAE);
        switch (id) {
            case "entity-esp" -> {
                int x = area.x + 50, y = area.y + 21;
                GuiDraw.rounded(c, x + 10, y + 5, 12, 12, 5, 0xFF3A515E);
                GuiDraw.rounded(c, x + 6, y + 22, 21, 25, 4, 0xFF283F4D);
                String style = core.getChoice(id, "style");
                float thickness = (float) core.getNumber(id, "line-width");
                GuiDraw.wireBox(c, x, y, 32, 53, style.equals("Corners"), thickness, color);
                if (style.equals("Boxes")) {
                    GuiDraw.wireBox(c, x + 6, y - 5, 32, 53, false, 0.6f, Palette.alpha(color, 90));
                    for (int corner = 0; corner < 4; corner++) GuiDraw.stroke(c, x + corner % 2 * 32, y + corner / 2 * 53, x + corner % 2 * 32 + 6, y + corner / 2 * 53 - 5, 0.6f, Palette.alpha(color, 150));
                }
                if (hasSetting(module, "health") && core.getBoolean(id, "health")) {
                    c.fill(x - 5, y, x - 3, y + 53, 0xFF273940);
                    c.fill(x - 5, y + 11, x - 3, y + 53, color);
                }
                if (hasSetting(module, "labels") && core.getBoolean(id, "labels")) label(c, "PLAYER · " + number(Math.min(32, core.getNumber(id, "range") / 2)) + "m", area.x + 30, area.y + 6, 0.56f, color);
            }
            case "storage-esp" -> {
                int x = area.x + 47, y = area.y + 29;
                GuiDraw.cube(c, x, y, 37, Palette.alpha(color, 180));
                GuiDraw.wireBox(c, x - 5, y - 7, 48, 48, core.getChoice(id, "style").equals("Corners"), (float) core.getNumber(id, "line-width"), color);
                if (hasSetting(module, "labels") && core.getBoolean(id, "labels")) label(c, "CHEST · 16m", area.x + 34, area.y + 9, 0.64f, color);
            }
            case "tracers" -> {
                double originX = area.x + area.w / 2.0, originY = area.bottom() - 10;
                for (int i = 0; i < 3; i++) {
                    double x = area.x + 25 + i * 42, y = area.y + 22 + Math.sin(animationTime + i * 1.6) * 8;
                    GuiDraw.stroke(c, originX, originY, x, y, (float) core.getNumber(id, "line-width"), color);
                    GuiDraw.glowDot(c, x, y, 2, color, 240);
                }
                GuiDraw.glowDot(c, originX, originY, 1.8f, Palette.TEXT, 210);
            }
            case "crosshair" -> {
                double x = area.x + area.w / 2.0, y = area.y + area.h / 2.0;
                double gap = core.getNumber(id, "gap"), arm = core.getNumber(id, "size");
                float weight = (float) core.getNumber(id, "line-width");
                GuiDraw.stroke(c, x - gap - arm, y, x - gap, y, weight, color);
                GuiDraw.stroke(c, x + gap, y, x + gap + arm, y, weight, color);
                GuiDraw.stroke(c, x, y - gap - arm, x, y - gap, weight, color);
                GuiDraw.stroke(c, x, y + gap, x, y + gap + arm, weight, color);
                GuiDraw.glowDot(c, x, y, 1, color, 130);
            }
            case "coordinates" -> {
                for (int axis = 0; axis < 3; axis++) {
                    String value = client == null || client.player == null ? "—" : Integer.toString(axis == 0 ? client.player.getBlockX() : axis == 1 ? client.player.getBlockY() : client.player.getBlockZ());
                    label(c, (axis == 0 ? "X " : axis == 1 ? "Y " : "Z ") + value, area.x + 20, area.y + 20 + axis * 18, 0.94f, color);
                }
            }
            case "particles" -> {
                int count = (int) Math.round(core.getNumber(id, "density") * 18);
                if (!enabled || count == 0) label(c, "FIELD OFF", area.x + 36, area.y + 37, 0.8f, Palette.SUBTLE);
                else for (int i = 0; i < count; i++) {
                    double x = area.x + 12 + Math.floorMod(i * 37, 109) + Math.sin(animationTime + i) * 3;
                    double y = area.y + 11 + Math.floorMod(i * 23, 64) + Math.cos(animationTime * 0.7 + i) * 5;
                    GuiDraw.glowDot(c, x, y, i % 3 == 0 ? 2 : 1, particleColor(i), 235);
                    if (i > 0) GuiDraw.stroke(c, area.x + 12 + Math.floorMod((i - 1) * 37, 109), area.y + 11 + Math.floorMod((i - 1) * 23, 64), x, y, 0.5f, Palette.alpha(color, 60));
                }
            }
            case "sky" -> drawCelestialPreview(c, area, core.getChoice(id, "preset"), core.getNumber(id, "intensity") * (enabled ? 1 : 0.25), core.getBoolean(id, "stars"), true);
            case "shader" -> drawCelestialPreview(c, area, core.getChoice(id, "preset"), core.getNumber(id, "strength") * (enabled ? 1 : 0.25), false, false);
            case "fullbright" -> {
                double brightness = enabled ? core.getNumber(id, "gamma") / 16 : 0.05;
                for (int row = 0; row < 3; row++) for (int column = 0; column < 5; column++) {
                    int shade = Palette.mix(0xFF102027, (column + row) % 2 == 0 ? 0xFF89BCA2 : 0xFF94AECA, brightness);
                    GuiDraw.rounded(c, area.x + 16 + column * 20, area.y + 17 + row * 19, 18, 17, 2, shade);
                }
                if (enabled) GuiDraw.glowDot(c, area.x + 110, area.y + 13, 2.2f, Palette.GOLD, 245);
            }
            case "menu" -> {
                GuiDraw.rounded(c, area.x + 18, area.y + 16, 98, 56, 8, Palette.alpha(color, glass ? 42 : 95));
                GuiDraw.rounded(c, area.x + 24, area.y + 22, 21, 44, 5, glass ? 0xA4111C2A : Palette.SURFACE);
                for (int row = 0; row < 3; row++) GuiDraw.rounded(c, area.x + 53, area.y + 24 + row * 12, 54, 8, 3, Palette.alpha(color, 130 - row * 22));
                label(c, glass ? "GLASS" : "SOLID", area.x + 50, area.bottom() - 12, 0.59f, color);
            }
            default -> GuiDraw.logo(c, area.x + 49, area.y + 24, 35, color);
        }
        c.disableScissor();
    }

    private void drawCelestialPreview(DrawContext c, Rect area, String preset, double strength, boolean stars, boolean sky) {
        int tint = preset.equals("Ember") ? Palette.GOLD : preset.equals("Nebula") ? Palette.VIOLET : preset.equals("Frost") ? Palette.CYAN : accentColor;
        double amount = preset.equals("Off") ? 0 : strength;
        double time = sky && selected != null && selected.id().equals("sky") ? animationTime * core.getNumber("sky", "speed") : animationTime;
        if (!sky) {
            for (int column = 0; column < 8; column++) {
                int h = 16 + column * 11 % 26;
                GuiDraw.rounded(c, area.x + 8 + column * 15, area.bottom() - h - 6, 12, h, 2, Palette.mix(0xFF203540, tint, amount * 0.55));
            }
        }
        for (int band = 0; band < (sky ? 3 : 2); band++) {
            int previousX = area.x, previousY = area.y + 30;
            for (int x = 0; x <= area.w; x += 3) {
                int y = area.y + 21 + band * 12 + (int) (Math.sin(x * 0.043 + time * 0.6 + band * 1.3) * 9 + Math.cos(x * 0.019 - time * 0.35) * 7);
                int color = band == 1 ? Palette.VIOLET : tint;
                GuiDraw.stroke(c, previousX, previousY + 4, area.x + x, y + 4, sky ? 8 : 5, Palette.alpha(color, (int) (amount * 23)));
                GuiDraw.stroke(c, previousX, previousY, area.x + x, y, sky ? 2.6f : 1.2f, Palette.alpha(color, (int) (amount * 170)));
                previousX = area.x + x; previousY = y;
            }
        }
        if (stars) for (int i = 0; i < 15; i++) GuiDraw.glowDot(c, area.x + 7 + i * 43 % 119, area.y + 7 + i * 31 % 53, i % 5 == 0 ? 1.5f : 0.8f, Palette.TEXT, (int) (strength * (170 + Math.sin(time + i) * 60)));
        if (preset.equals("Bloom")) for (int i = 0; i < 3; i++) GuiDraw.glowDot(c, area.x + 29 + i * 38, area.y + 27 + i * 13, 3, Palette.GOLD, (int) (amount * 245));
        label(c, (sky ? "SKY / " : "POST FX / ") + preset.toUpperCase(Locale.ROOT), area.x + 10, area.bottom() - 13, 0.58f, Palette.alpha(Palette.TEXT, 210));
    }

    private boolean hasSetting(ModuleDefinition module, String id) { return module.settings().stream().anyMatch(setting -> setting.id().equals(id)); }

    private void drawSidebar(DrawContext c) {
        GuiDraw.glow(c, 23, 22, 34, 34, 10, accentColor);
        GuiDraw.rounded(c, 23, 22, 34, 34, 10, Palette.mix(Palette.SURFACE, accentColor, 0.18));
        GuiDraw.logo(c, 28, 26, 23, accentColor);
        heading(c, "ANALYZER", 67, 25, 1.1f, Palette.TEXT);
        label(c, "CLIENT / 1.21.4", 68, 42, 0.68f, Palette.SUBTLE);
        label(c, "WORKSPACE", 23, 95, 0.74f, Palette.SUBTLE);
        sidebar(c, "ALL", "All modules", "01", 116);
        categoryScroll = Math.clamp(categoryScroll, 0, Math.max(0, Category.values().length * 26 - CATEGORY_VIEW.h));
        clip(c, CATEGORY_VIEW);
        for (int i = 0; i < Category.values().length; i++) {
            Category entry = Category.values()[i];
            int y = 159 + i * 26 - (int) categoryScroll;
            if (y + 24 < CATEGORY_VIEW.y || y >= CATEGORY_VIEW.bottom()) continue;
            Rect box = new Rect(16, y, 128, 24);
            boolean active = category.equals(entry.name());
            GuiDraw.rounded(c, box.x, box.y, box.w, box.h, 5, active ? Palette.alpha(accentColor, 50) : Palette.alpha(Palette.SURFACE, 220));
            if (active) c.fill(16, y + 5, 18, y + 20, accentColor);
            label(c, String.format(Locale.ROOT, "%02d", i + 2), 27, y + 8, 0.64f, active ? accentColor : Palette.SUBTLE);
            heading(c, entry.label(), 49, y + 7, 0.8f, active ? Palette.TEXT : Palette.MUTED);
            diagnostic("category:" + entry.name(), box, CATEGORY_VIEW);
            clippedHit(box, CATEGORY_VIEW, () -> { category = entry.name(); scroll = 0; focus = Focus.NONE; captureModule = null; dragging = null; });
        }
        c.disableScissor();
        if (Category.values().length * 26 > CATEGORY_VIEW.h) scrollBar(c, CATEGORY_VIEW, categoryScroll, Category.values().length * 26 - CATEGORY_VIEW.h);
        c.fill(24, 317, 135, 318, Palette.BORDER);
        label(c, "PERSONAL", 23, 328, 0.74f, Palette.SUBTLE);
        sidebar(c, "PROFILES", "Profiles", "05", 350);
        GuiDraw.outlined(c, 18, 425, 123, 53, 10, Palette.BORDER, 0xFF121F25);
        GuiDraw.rounded(c, 29, 439, 5, 5, 2, GpuEffects.isHealthy() ? accentColor : Palette.ERROR);
        label(c, GpuEffects.isHealthy() ? "RENDER ONLINE" : "RENDER LIMITED", 40, 436, 0.73f, GpuEffects.isHealthy() ? accentColor : Palette.ERROR);
        label(c, enabledCount() + " modules / " + visibleParticles + " particles", 29, 454, 0.67f, Palette.MUTED);
    }

    private void sidebar(DrawContext c, String id, String name, String number, int y) {
        Rect bounds = new Rect(16, y, 128, 35);
        controlBounds.put("category:" + id, bounds);
        double hover = ease("nav-" + id, bounds.contains(mx, my) ? 1 : 0);
        boolean active = category.equals(id);
        GuiDraw.rounded(c, 16, y, 128, 35, 8,
                active ? Palette.mix(Palette.SURFACE, accentColor, 0.14) : Palette.mix(Palette.SURFACE, Palette.CARD_HOVER, hover));
        if (active) GuiDraw.rounded(c, 16, y + 9, 3, 17, 1, accentColor);
        label(c, number, 27, y + 12, 0.72f, active ? accentColor : Palette.SUBTLE);
        heading(c, name, 49, y + 10, 0.9f, active ? Palette.TEXT : Palette.MUTED);
        hits.add(new Hit(bounds, () -> {
            category = id; scroll = 0; profileScroll = 0; focus = Focus.NONE;
            captureModule = null; dragging = null;
            if (id.equals("PROFILES")) refreshProfiles();
        }));
    }

    private void drawHeader(DrawContext c) {
        label(c, "ANALYZER  /  " + (category.equals("PROFILES") ? "PERSONAL" : "WORKSPACE"), 182, 25, 0.75f, Palette.SUBTLE);
        String title = switch (category) {
            case "VISUAL" -> "See beyond the ordinary.";
            case "WORLD" -> "Know your surroundings.";
            case "HUD" -> "Every detail. In view.";
            case "PROFILES" -> "Make it yours.";
            default -> "A new perspective.";
        };
        heading(c, title, 182, 45, 1.48f, Palette.TEXT);
        GuiDraw.outlined(c, 481, 22, 106, 23, 6, Palette.alpha(accentColor, 85), 0xAF101E28);
        GuiDraw.rounded(c, 491, 30, 4, 4, 2, GpuEffects.isHealthy() ? accentColor : Palette.ERROR);
        label(c, "EFFECT ENGINE", 502, 29, 0.66f, GpuEffects.isHealthy() ? accentColor : Palette.ERROR);
        label(c, GuiDraw.truncate(textRenderer, GpuEffects.status(), 107, 0.60f), 481, 51, 0.60f, Palette.MUTED);
        button(c, "close", "×", 850, 24, 29, 29, false, this::close);
        label(c, category.equals("PROFILES") ? "PROFILE MANAGER" : "MODULE INSPECTOR", 628, 64, 0.78f, Palette.SUBTLE);
        button(c, "layout-workspace", "Workspace", 182, 68, 86, 20, true, () -> switchLayout("Workspace"));
        button(c, "layout-classic", "Classic", 274, 68, 64, 20, false, () -> switchLayout("Classic"));
        button(c, "layout-compact", "Compact", 344, 68, 73, 20, false, () -> switchLayout("Compact"));
        button(c, "hud-editor", "Edit HUD", 423, 68, 76, 20, false, () -> client.setScreen(new HudEditorScreen(core, this)));
        button(c, "automation", "Automation", 505, 68, 89, 20, false, () -> client.setScreen(MenuScreens.automation(core, this)));
    }

    private void drawModules(DrawContext c) {
        GuiDraw.outlined(c, 181, 91, 408, 108, 12, Palette.mix(Palette.BORDER, accentColor, 0.34),
                Palette.alpha(Palette.mix(Palette.SURFACE, accentColor, 0.09), glass ? 213 : 255));
        drawParticleField(c, new Rect(185, 95, 400, 100), 0.85);
        label(c, "LIVE SETTINGS PREVIEW", 197, 106, 0.73f, accentColor);
        if (selected != null) {
            heading(c, selected.name(), 197, 126, 1.3f, Palette.TEXT);
            List<String> statusLines = GuiDraw.wrap(textRenderer, liveStatus(selected), 234, 0.79f);
            for (int i = 0; i < Math.min(2, statusLines.size()); i++) label(c, statusLines.get(i), 197, 152 + i * 13, 0.79f, statusColor(selected));
            label(c, previewKind(selected), 197, 181, 0.65f, Palette.SUBTLE);
            drawPreview(c, selected, new Rect(444, 101, 132, 87));
        }
        field(c, "Search modules…", query, Focus.SEARCH, new Rect(181, 214, 408, 35));
        label(c, "/", 570, 226, 0.78f, Palette.SUBTLE);
        List<ModuleDefinition> list = visibleModules();
        label(c, category.equals("ALL") ? "ALL MODULES" : category + " MODULES", 182, 257, 0.7f, Palette.SUBTLE);
        label(c, list.size() + " available", 521, 257, 0.7f, Palette.SUBTLE);
        double maximum = Math.max(0, ((list.size() + 1) / 2) * 98 - MODULE_VIEW.h);
        scroll = Math.clamp(scroll, 0, maximum);
        clip(c, MODULE_VIEW);
        if (list.isEmpty()) {
            heading(c, "No matching modules", 207, 322, 1.07f, Palette.MUTED);
            label(c, "Try a different name or category.", 207, 347, 0.85f, Palette.SUBTLE);
        }
        for (int i = 0; i < list.size(); i++) {
            int x = 181 + i % 2 * 209;
            int y = 274 + (i / 2) * 98 - (int) scroll;
            if (y + 86 < MODULE_VIEW.y || y > MODULE_VIEW.bottom()) continue;
            drawCard(c, list.get(i), x, y);
        }
        c.disableScissor();
        scrollBar(c, MODULE_VIEW, scroll, maximum);
    }

    private void drawCard(DrawContext c, ModuleDefinition module, int x, int y) {
        Rect bounds = new Rect(x, y, 198, 86);
        double hover = ease("card-" + module.id(), bounds.contains(mx, my) && MODULE_VIEW.contains(mx, my) ? 1 : 0);
        boolean active = core.isEnabled(module.id()), isSelected = selected != null && selected.id().equals(module.id());
        GuiDraw.outlined(c, x, y, 198, 86, 10,
                isSelected ? Palette.mix(Palette.BORDER, accentColor, 0.59) : Palette.mix(Palette.BORDER, accentColor, hover * 0.28),
                Palette.mix(glass ? Palette.CARD_GLASS : Palette.CARD, Palette.alpha(Palette.CARD_HOVER, glass ? 237 : 255), hover));
        drawParticleField(c, new Rect(x + 8, y + 8, 181, 70), 0.68);
        GuiDraw.rounded(c, x + 12, y + 13, 25, 25, 7, active ? Palette.mix(Palette.CARD, accentColor, 0.20) : 0xFF223039);
        icon(c, module.id(), x + 18, y + 19, active ? accentColor : Palette.MUTED);
        heading(c, GuiDraw.truncate(textRenderer, module.name(), 105, 0.97f), x + 46, y + 13, 0.97f, Palette.TEXT);
        label(c, (active ? "ACTIVE / " : "OFF / ") + module.category(), x + 46, y + 29, 0.61f, active ? accentColor : Palette.SUBTLE);
        List<String> lines = GuiDraw.wrap(textRenderer, module.description(), 172, 0.76f);
        for (int i = 0; i < Math.min(2, lines.size()); i++) label(c, lines.get(i), x + 12, y + 51 + i * 12, 0.76f, Palette.MUTED);
        clippedHit(bounds, MODULE_VIEW, () -> {
            selected = module; settingsScroll = 0; focus = Focus.NONE; captureModule = null;
        });
        Rect toggle = new Rect(x + 155, y + 13, 31, 17);
        diagnostic("inspect:" + module.id(), bounds, MODULE_VIEW);
        diagnostic("module:" + module.id(), toggle, MODULE_VIEW);
        drawToggle(c, "cardtoggle-" + module.id(), toggle, active);
        clippedHit(toggle, MODULE_VIEW, () -> { core.toggle(module.id()); changed(); });
    }

    private void drawInspector(DrawContext c) {
        if (selected == null) return;
        boolean enabled = core.isEnabled(selected.id());
        GuiDraw.rounded(c, 629, 94, 39, 39, 10, Palette.mix(Palette.CARD, accentColor, 0.15));
        icon(c, selected.id(), 641, 106, accentColor);
        label(c, selected.category().toString(), 679, 96, 0.72f, Palette.SUBTLE);
        heading(c, selected.name(), 679, 114, 1.3f, Palette.TEXT);
        List<String> description = GuiDraw.wrap(textRenderer, selected.description(), 244, 0.9f);
        for (int i = 0; i < Math.min(2, description.size()); i++) label(c, description.get(i), 629, 145 + i * 14, 0.9f, Palette.MUTED);
        label(c, enabled ? "MODULE ACTIVE" : "MODULE DISABLED", 629, 181, 0.75f, statusColor(selected));
        label(c, GuiDraw.truncate(textRenderer, liveStatus(selected), 198, 0.68f), 629, 198, 0.68f, statusColor(selected));
        Rect toggle = new Rect(832, 179, 44, 23);
        controlBounds.put("selected-toggle", toggle);
        drawToggle(c, "inspecttoggle-" + selected.id(), toggle, enabled);
        hits.add(new Hit(toggle, () -> { core.toggle(selected.id()); changed(); }));
        c.fill(628, 211, 877, 212, Palette.BORDER);
        int totalHeight = selected.settings().size() * 63 + 8;
        double maximum = Math.max(0, totalHeight - SETTINGS_VIEW.h);
        settingsScroll = Math.clamp(settingsScroll, 0, maximum);
        clip(c, SETTINGS_VIEW);
        for (int i = 0; i < selected.settings().size(); i++) {
            int y = 228 + i * 63 - (int) settingsScroll;
            if (y + 59 < SETTINGS_VIEW.y || y > SETTINGS_VIEW.bottom()) continue;
            drawSetting(c, selected.settings().get(i), y);
        }
        if (selected.settings().isEmpty()) label(c, "This module needs no extra setup.", 629, 236, 0.9f, Palette.MUTED);
        c.disableScissor();
        scrollBar(c, SETTINGS_VIEW, settingsScroll, maximum);
        c.fill(628, 430, 877, 431, Palette.BORDER);
        label(c, "KEYBIND", 629, 447, 0.75f, Palette.SUBTLE);
        String binding = captureModule != null ? "Press a key…" : keyName(core.keybind(selected.id()));
        button(c, "bind", binding, 729, 438, 148, 30, captureModule != null, () -> {
            captureModule = selected.id(); focus = Focus.NONE;
        });
        label(c, captureModule != null ? "Esc cancels · Backspace clears" : "Click a card to inspect · Space to toggle", 630, 475, 0.68f, Palette.SUBTLE);
    }

    private void drawSetting(DrawContext c, Setting<?> setting, int y) {
        String moduleId = selected.id();
        diagnostic("setting:" + moduleId + ":" + setting.id(), new Rect(629, y + 18, 247, 26), SETTINGS_VIEW);
        label(c, setting.label(), 629, y, 0.94f, Palette.TEXT);
        switch (setting.kind()) {
            case BOOLEAN -> {
                boolean value = core.getBoolean(moduleId, setting.id());
                Rect bounds = new Rect(841, y - 2, 35, 19);
                drawToggle(c, "setting-" + moduleId + setting.id(), bounds, value);
                label(c, value ? "Enabled" : "Disabled", 629, y + 22, 0.78f, value ? accentColor : Palette.SUBTLE);
                clippedHit(new Rect(629, y - 2, 247, 42), SETTINGS_VIEW, () -> {
                    core.setValue(moduleId, setting.id(), !core.getBoolean(moduleId, setting.id())); changed();
                });
            }
            case NUMBER -> {
                double value = core.getNumber(moduleId, setting.id());
                String formatted = number(value);
                label(c, formatted, 875 - GuiDraw.width(textRenderer, formatted, 0.85f), y + 1, 0.85f, accentColor);
                Rect track = new Rect(629, y + 29, 245, 5);
                GuiDraw.rounded(c, track.x, track.y, track.w, track.h, 2, Palette.BORDER);
                double amount = setting.max() == setting.min() ? 0 : Math.clamp((value - setting.min()) / (setting.max() - setting.min()), 0.0, 1.0);
                int px = track.x + (int) (amount * track.w);
                GuiDraw.rounded(c, track.x, track.y, px - track.x, track.h, 2, accentColor);
                GuiDraw.rounded(c, px - 4, track.y - 2, 9, 9, 4, Palette.TEXT);
                clippedHit(new Rect(625, y + 18, 253, 26), SETTINGS_VIEW, () -> {
                    dragging = new SliderDrag(moduleId, setting, track); updateSlider(mx);
                });
            }
            case CHOICE -> {
                String value = core.getChoice(moduleId, setting.id());
                Rect bounds = new Rect(629, y + 18, 247, 26);
                GuiDraw.outlined(c, bounds.x, bounds.y, bounds.w, bounds.h, 6, Palette.BORDER,
                        bounds.contains(mx, my) ? Palette.CARD_HOVER : Palette.CARD);
                label(c, value, 639, y + 26, 0.86f, accentColor);
                label(c, "›", 860, y + 23, 1.07f, Palette.MUTED);
                clippedHit(bounds, SETTINGS_VIEW, () -> {
                    List<String> choices = setting.choices();
                    if (!choices.isEmpty()) {
                        int index = choices.indexOf(core.getChoice(moduleId, setting.id()));
                        core.setValue(moduleId, setting.id(), choices.get((index + 1) % choices.size())); changed();
                    }
                });
            }
        }
    }

    private void drawProfiles(DrawContext c) {
        GuiDraw.outlined(c, 181, 91, 408, 99, 12, 0xFF453858, 0xFF221D30);
        label(c, "YOUR CONFIGURATION, REFINED", 198, 107, 0.74f, Palette.VIOLET);
        heading(c, "One click. A whole new outlook.", 198, 130, 1.09f, Palette.TEXT);
        label(c, "Save your modules, settings and keybinds together.", 198, 161, 0.84f, Palette.MUTED);
        field(c, "Profile name", profileName, Focus.PROFILE, new Rect(181, 202, 284, 31));
        button(c, "save-profile", "Save profile", 474, 202, 115, 31, true, this::saveProfile);
        profileScroll = Math.clamp(profileScroll, 0, Math.max(0, profiles.size() * 57 - PROFILE_VIEW.h));
        clip(c, PROFILE_VIEW);
        if (profiles.isEmpty()) {
            heading(c, "A clean slate.", 203, 303, 1.24f, Palette.MUTED);
            label(c, "Name your current setup and save your first profile.", 203, 330, 0.87f, Palette.SUBTLE);
        }
        for (int i = 0; i < profiles.size(); i++) {
            String name = profiles.get(i);
            int y = 247 + i * 57 - (int) profileScroll;
            if (y + 47 < PROFILE_VIEW.y || y > PROFILE_VIEW.bottom()) continue;
            boolean chosen = selectedProfile.equals(name);
            Rect bounds = new Rect(181, y, 408, 47);
            GuiDraw.outlined(c, 181, y, 408, 47, 8, chosen ? 0xFF6D568B : Palette.BORDER,
                    bounds.contains(mx, my) ? Palette.CARD_HOVER : Palette.CARD);
            GuiDraw.rounded(c, 194, y + 12, 22, 22, 6, 0xFF352B45);
            label(c, "P", 201, y + 16, 0.92f, Palette.VIOLET);
            heading(c, GuiDraw.truncate(textRenderer, name, 205, 0.96f), 228, y + 10, 0.96f, Palette.TEXT);
            label(c, chosen ? "Selected profile" : "Saved configuration", 228, y + 27, 0.69f, chosen ? Palette.VIOLET : Palette.SUBTLE);
            clippedHit(bounds, PROFILE_VIEW, () -> { selectedProfile = name; profileName = name; focus = Focus.NONE; });
            staticButton(c, "Load", 505, y + 10, 67, 27, false, new Rect(505, y + 10, 67, 27).contains(mx, my));
            clippedHit(new Rect(505, y + 10, 67, 27), PROFILE_VIEW, () -> loadProfile(name));
        }
        c.disableScissor();
        scrollBar(c, PROFILE_VIEW, profileScroll, Math.max(0, profiles.size() * 57 - PROFILE_VIEW.h));
        GuiDraw.rounded(c, 629, 95, 43, 43, 11, 0xFF352B45);
        label(c, "P", 644, 106, 1.5f, Palette.VIOLET);
        label(c, "CURRENT WORKSPACE", 686, 99, 0.69f, Palette.SUBTLE);
        heading(c, enabledCount() + " modules enabled", 686, 117, 1.06f, Palette.TEXT);
        c.fill(629, 161, 877, 162, Palette.BORDER);
        label(c, "SELECTED PROFILE", 629, 185, 0.73f, Palette.SUBTLE);
        heading(c, GuiDraw.truncate(textRenderer, selectedProfile.isEmpty() ? "Choose a profile" : selectedProfile, 240, 1.3f), 629, 209, 1.3f, Palette.TEXT);
        label(c, "Profiles include every module setting", 629, 247, 0.86f, Palette.MUTED);
        label(c, "and your keyboard shortcuts.", 629, 264, 0.86f, Palette.MUTED);
        if (!selectedProfile.isEmpty()) {
            button(c, "load-selected", "Load this profile", 629, 301, 248, 35, true, () -> loadProfile(selectedProfile));
            button(c, "delete-selected", "Delete profile", 629, 349, 248, 32, false, this::deleteProfile);
        }
        label(c, "Settings also save automatically.", 629, 438, 0.8f, Palette.MUTED);
        label(c, "Stored locally in config/analyzer/.", 629, 456, 0.74f, Palette.SUBTLE);
    }

    private void drawFooter(DrawContext c) {
        c.fill(180, 488, 878, 489, Palette.BORDER);
        String message = error ? status : dirty ? "Saving changes…" : status;
        label(c, GuiDraw.truncate(textRenderer, message, 439, 0.77f), 182, 500, 0.77f,
                error ? Palette.ERROR : dirty ? Palette.MUTED : accentColor);
        String shortcut = category.equals("PROFILES") ? "TAB NAME · ENTER SAVE" : "/ SEARCH";
        label(c, keyName(AnalyzerClient.getMenuKey()).toUpperCase(Locale.ROOT) + " / ESC CLOSE    ·    " + shortcut, 641, 500, 0.67f, Palette.SUBTLE);
        if (error && new Rect(181, 489, 442, 31).contains(mx, my)) {
            List<String> lines = GuiDraw.wrap(textRenderer, status, 398, 0.85f);
            int h = 23 + lines.size() * 14;
            GuiDraw.outlined(c, 181, 482 - h, 424, h, 8, 0xFF86505B, 0xFF261D25);
            for (int i = 0; i < lines.size(); i++) label(c, lines.get(i), 193, 494 - h + i * 14, 0.85f, Palette.ERROR);
        }
    }

    private void icon(DrawContext c, String module, int x, int y, int color) {
        switch (module) {
            case "entity-esp" -> {
                GuiDraw.rounded(c, x + 4, y, 5, 5, 2, color);
                c.fill(x + 3, y + 7, x + 10, y + 14, color);
                GuiDraw.line(c, x, y - 2, x, y + 15, color);
                GuiDraw.line(c, x + 13, y - 2, x + 13, y + 15, color);
            }
            case "storage-esp" -> GuiDraw.cube(c, x, y, 13, color);
            case "tracers" -> {
                GuiDraw.line(c, x, y + 13, x + 12, y, color);
                GuiDraw.line(c, x + 3, y + 13, x + 13, y + 4, color);
                GuiDraw.rounded(c, x + 10, y - 1, 4, 4, 2, color);
            }
            case "fullbright" -> {
                GuiDraw.rounded(c, x + 3, y + 3, 8, 8, 4, color);
                c.fill(x + 6, y - 1, x + 8, y + 2, color);
                c.fill(x + 6, y + 12, x + 8, y + 15, color);
                c.fill(x - 1, y + 6, x + 2, y + 8, color);
                c.fill(x + 12, y + 6, x + 15, y + 8, color);
            }
            case "coordinates" -> { label(c, "XYZ", x - 2, y + 3, 0.68f, color); }
            case "crosshair" -> {
                c.fill(x + 6, y, x + 8, y + 5, color); c.fill(x + 6, y + 9, x + 8, y + 14, color);
                c.fill(x, y + 6, x + 5, y + 8, color); c.fill(x + 9, y + 6, x + 14, y + 8, color);
            }
            case "particles" -> {
                GuiDraw.rounded(c, x, y + 5, 3, 3, 1, color); GuiDraw.rounded(c, x + 7, y, 4, 4, 2, color);
                GuiDraw.rounded(c, x + 8, y + 10, 3, 3, 1, color);
            }
            case "sky" -> {
                GuiDraw.stroke(c, x, y + 10, x + 4, y + 3, 1.5f, color);
                GuiDraw.stroke(c, x + 4, y + 3, x + 8, y + 9, 1.5f, color);
                GuiDraw.stroke(c, x + 8, y + 9, x + 13, y + 1, 1.5f, color);
                GuiDraw.glowDot(c, x + 10, y + 12, 1, color, 190);
            }
            case "menu" -> {
                GuiDraw.wireBox(c, x, y, 14, 13, false, 1, color);
                GuiDraw.stroke(c, x + 4, y + 1, x + 4, y + 12, 1, color);
                GuiDraw.stroke(c, x + 7, y + 4, x + 12, y + 4, 1, color);
                GuiDraw.stroke(c, x + 7, y + 8, x + 12, y + 8, 1, color);
            }
            default -> {
                GuiDraw.line(c, x, y + 12, x + 6, y, color); GuiDraw.line(c, x + 6, y, x + 14, y + 12, color);
                GuiDraw.line(c, x, y + 12, x + 14, y + 12, color); GuiDraw.line(c, x + 6, y, x + 6, y + 12, color);
            }
        }
    }

    private void drawToggle(DrawContext c, String id, Rect bounds, boolean value) {
        double amount = ease(id, value ? 1 : 0);
        GuiDraw.rounded(c, bounds.x, bounds.y, bounds.w, bounds.h, bounds.h / 2,
                Palette.mix(0xFF34424A, Palette.mix(Palette.SURFACE, accentColor, 0.80), amount));
        int size = bounds.h - 6;
        int x = bounds.x + 3 + (int) ((bounds.w - size - 6) * amount);
        GuiDraw.rounded(c, x, bounds.y + 3, size, size, size / 2,
                Palette.mix(0xFFB6C5CB, Palette.TEXT, amount));
    }

    private void field(DrawContext c, String placeholder, String value, Focus target, Rect bounds) {
        controlBounds.put(target == Focus.SEARCH ? "search" : "profile-name", bounds);
        boolean focused = focus == target;
        GuiDraw.outlined(c, bounds.x, bounds.y, bounds.w, bounds.h, 7,
                focused ? accentColor : Palette.BORDER, Palette.SURFACE);
        if (target == Focus.SEARCH) {
            GuiDraw.rounded(c, bounds.x + 11, bounds.y + 10, 10, 10, 5, Palette.SUBTLE);
            GuiDraw.rounded(c, bounds.x + 13, bounds.y + 12, 6, 6, 3, Palette.SURFACE);
            GuiDraw.line(c, bounds.x + 20, bounds.y + 19, bounds.x + 24, bounds.y + 23, Palette.SUBTLE);
        }
        int x = bounds.x + (target == Focus.SEARCH ? 33 : 12);
        String shown = value.isEmpty() ? placeholder : value;
        float maxWidth = bounds.w - (x - bounds.x) - 24;
        if (!value.isEmpty() && focused) {
            while (GuiDraw.width(textRenderer, shown, 0.91f) > maxWidth && shown.length() > 1) shown = shown.substring(1);
        } else shown = GuiDraw.truncate(textRenderer, shown, maxWidth, 0.91f);
        if (focused && selectAll && !value.isEmpty()) c.fill(x, bounds.y + 8, x + (int) GuiDraw.width(textRenderer, shown, 0.91f), bounds.y + 23, 0x663FBE92);
        label(c, shown, x, bounds.y + 11, 0.91f, value.isEmpty() ? Palette.SUBTLE : Palette.TEXT);
        if (focused && (System.currentTimeMillis() / 450) % 2 == 0) {
            int cursorX = x + (value.isEmpty() ? 0 : (int) GuiDraw.width(textRenderer, shown, 0.91f)) + 1;
            c.fill(cursorX, bounds.y + 9, cursorX + 1, bounds.y + 23, accentColor);
        }
        hits.add(new Hit(bounds, () -> { focus = target; selectAll = false; captureModule = null; }));
    }

    private void button(DrawContext c, String id, String label, int x, int y, int w, int h, boolean primary, Runnable action) {
        controlBounds.put(id, new Rect(x, y, w, h));
        Rect bounds = new Rect(x, y, w, h);
        double hover = ease("button-" + id, bounds.contains(mx, my) ? 1 : 0);
        staticButton(c, label, x, y, w, h, primary, hover > 0.1);
        hits.add(new Hit(bounds, action));
    }

    private void staticButton(DrawContext c, String value, int x, int y, int w, int h, boolean primary, boolean hover) {
        int inside = primary ? hover ? Palette.mix(accentColor, Palette.TEXT, 0.25) : accentColor : hover ? Palette.CARD_HOVER : Palette.CARD;
        GuiDraw.outlined(c, x, y, w, h, 6, primary ? accentColor : Palette.BORDER, inside);
        label(c, value, x + (w - GuiDraw.width(textRenderer, value, 0.85f)) / 2, y + (h - 9) / 2, 0.85f,
                primary ? 0xFF10271E : Palette.MUTED);
    }

    private void scrollBar(DrawContext c, Rect view, double position, double maximum) {
        if (maximum <= 0) return;
        int thumbHeight = Math.max(22, (int) (view.h * view.h / (view.h + maximum)));
        int y = view.y + (int) ((view.h - thumbHeight) * position / maximum);
        GuiDraw.rounded(c, view.right() - 2, y, 2, thumbHeight, 1, 0xFF416357);
    }

    private void clip(DrawContext c, Rect rect) {
        // DrawContext transforms scissor coordinates through the current matrix stack.
        c.enableScissor(rect.x, rect.y, rect.right(), rect.bottom());
    }

    private void clippedHit(Rect bounds, Rect clip, Runnable action) {
        int x = Math.max(bounds.x, clip.x), y = Math.max(bounds.y, clip.y);
        int right = Math.min(bounds.right(), clip.right()), bottom = Math.min(bounds.bottom(), clip.bottom());
        if (right > x && bottom > y) hits.add(new Hit(new Rect(x, y, right - x, bottom - y), action));
    }

    private void label(DrawContext c, String value, float x, float y, float size, int color) { GuiDraw.label(c, textRenderer, value, x, y, size, color); }
    private void heading(DrawContext c, String value, float x, float y, float size, int color) { GuiDraw.heading(c, textRenderer, value, x, y, size, color); }

    private double ease(String id, double goal) {
        double value = animation.getOrDefault(id, goal);
        value += (goal - value) * (1 - Math.exp(-frameSeconds * 15));
        animation.put(id, value);
        return value;
    }

    private List<ModuleDefinition> visibleModules() {
        String filter = query.toLowerCase(Locale.ROOT).strip();
        return core.modules().stream().filter(module -> category.equals("ALL") || module.category().toString().equals(category))
                .filter(module -> (module.id() + " " + module.name() + " " + module.description()).toLowerCase(Locale.ROOT).contains(filter)).toList();
    }

    private long enabledCount() { return core.modules().stream().filter(module -> core.isEnabled(module.id())).count(); }
    private String number(double value) { return value == Math.rint(value) ? Long.toString((long) value) : String.format(Locale.ROOT, "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", ""); }
    private String keyName(int key) { return key < 0 ? "Unbound" : InputUtil.fromKeyCode(key, 0).getLocalizedText().getString(); }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        mx = (mouseX - canvasX) / canvasScale; my = (mouseY - canvasY) / canvasScale;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (hit.bounds.contains(mx, my)) { hit.action.run(); return true; }
        }
        focus = Focus.NONE; selectAll = false; return true;
    }

    @Override public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (dragging != null && button == 0) { updateSlider((mouseX - canvasX) / canvasScale); return true; }
        return false;
    }

    @Override public boolean mouseReleased(double mouseX, double mouseY, int button) { dragging = null; return true; }

    private void updateSlider(double mouseX) {
        if (dragging == null) return;
        Setting<?> setting = dragging.setting;
        double t = Math.clamp((mouseX - dragging.track.x) / dragging.track.w, 0.0, 1.0);
        double value = setting.min() + t * (setting.max() - setting.min());
        if (setting.step() > 0) value = setting.min() + Math.round((value - setting.min()) / setting.step()) * setting.step();
        core.setValue(dragging.module, setting.id(), Math.clamp(value, setting.min(), setting.max()));
        changed();
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        double x = (mouseX - canvasX) / canvasScale, y = (mouseY - canvasY) / canvasScale;
        if (CATEGORY_VIEW.contains(x, y)) categoryScroll = Math.max(0, categoryScroll - verticalAmount * 26);
        else if (category.equals("PROFILES") && PROFILE_VIEW.contains(x, y)) profileScroll = Math.max(0, profileScroll - verticalAmount * 30);
        else if (SETTINGS_VIEW.contains(x, y)) settingsScroll = Math.max(0, settingsScroll - verticalAmount * 28);
        else if (MODULE_VIEW.contains(x, y)) scroll = Math.max(0, scroll - verticalAmount * 30);
        return true;
    }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (captureModule != null) {
            if (keyCode != GLFW.GLFW_KEY_ESCAPE) {
                core.setKeybind(captureModule, keyCode == GLFW.GLFW_KEY_BACKSPACE || keyCode == GLFW.GLFW_KEY_DELETE ? GLFW.GLFW_KEY_UNKNOWN : keyCode);
                changed();
            }
            captureModule = null; return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || (keyCode == AnalyzerClient.getMenuKey() && focus == Focus.NONE)) {
            if (focus != Focus.NONE && keyCode == GLFW.GLFW_KEY_ESCAPE) { focus = Focus.NONE; selectAll = false; }
            else close();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            focus = focus == Focus.NONE ? category.equals("PROFILES") ? Focus.PROFILE : Focus.SEARCH : Focus.NONE;
            selectAll = false; return true;
        }
        if (focus != Focus.NONE) {
            boolean control = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
            if (control && keyCode == GLFW.GLFW_KEY_A) { selectAll = true; return true; }
            if (control && keyCode == GLFW.GLFW_KEY_V && client != null) {
                append(client.keyboard.getClipboard().replaceAll("[\\p{Cntrl}]", "")); return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE || keyCode == GLFW.GLFW_KEY_DELETE) {
                String value = input();
                setInput(selectAll ? "" : value.isEmpty() ? "" : value.substring(0, value.offsetByCodePoints(value.length(), -1)));
                selectAll = false; return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                if (focus == Focus.PROFILE) saveProfile(); else focus = Focus.NONE;
                return true;
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_SLASH && !category.equals("PROFILES")) { focus = Focus.SEARCH; return true; }
        if (keyCode == GLFW.GLFW_KEY_SPACE && selected != null && !category.equals("PROFILES")) { core.toggle(selected.id()); changed(); return true; }
        if ((keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_RIGHT || keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN) && !category.equals("PROFILES")) {
            List<ModuleDefinition> list = visibleModules();
            if (!list.isEmpty()) {
                int index = Math.max(0, list.indexOf(selected));
                int step = keyCode == GLFW.GLFW_KEY_LEFT ? -1 : keyCode == GLFW.GLFW_KEY_RIGHT ? 1 : keyCode == GLFW.GLFW_KEY_UP ? -2 : 2;
                int next = Math.clamp(index + step, 0, list.size() - 1);
                selected = list.get(next); settingsScroll = 0;
                int rowY = next / 2 * 98;
                if (rowY < scroll) scroll = rowY;
                else if (rowY + 86 > scroll + MODULE_VIEW.h) scroll = rowY + 86 - MODULE_VIEW.h;
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override public boolean charTyped(char character, int modifiers) {
        if (focus == Focus.NONE || captureModule != null || Character.isISOControl(character)) return false;
        if (character == '/' && focus == Focus.SEARCH && query.isEmpty()) return true;
        append(String.valueOf(character)); return true;
    }

    private String input() { return focus == Focus.PROFILE ? profileName : query; }
    private void setInput(String value) { if (focus == Focus.PROFILE) profileName = value; else { query = value; scroll = 0; } }
    private void append(String text) {
        String value = (selectAll ? "" : input()) + text;
        setInput(value.substring(0, Math.min(focus == Focus.PROFILE ? 48 : 96, value.length()))); selectAll = false;
    }

    private void changed() { dirty = true; changedAt = System.currentTimeMillis(); error = false; }
    private void report(String message, boolean failed) { status = message; error = failed; statusAt = System.currentTimeMillis(); }

    @Override public void tick() {
        if (dirty && !error && System.currentTimeMillis() - changedAt > 700) persist();
        if (!error && statusAt != 0 && System.currentTimeMillis() - statusAt > 5500) { status = "Your perspective. Your rules."; statusAt = 0; }
    }

    private void persist() {
        try { core.save(); dirty = false; report("All changes saved.", false); }
        catch (IOException exception) { report("Could not save: " + exception.getMessage(), true); }
    }

    private void refreshProfiles() {
        try { profiles = core.profiles(); }
        catch (IOException exception) { profiles = List.of(); report("Could not read profiles: " + exception.getMessage(), true); }
    }

    private void saveProfile() {
        String name = profileName.strip();
        if (name.isEmpty()) { report("Enter a profile name first.", true); focus = Focus.PROFILE; return; }
        try {
            core.saveProfile(name); profiles = core.profiles();
            selectedProfile = name.toLowerCase(Locale.ROOT).replace(' ', '-');
            report("Saved profile “" + selectedProfile + "”.", false);
        }
        catch (IOException | IllegalArgumentException exception) { report("Could not save profile: " + exception.getMessage(), true); }
    }

    private void loadProfile(String name) {
        try {
            core.loadProfile(name); selectedProfile = name; profileName = name;
            changed(); core.save(); dirty = false;
            report("Loaded profile “" + name + "”.", false);
        }
        catch (IOException | IllegalArgumentException exception) { report("Could not load profile: " + exception.getMessage(), true); }
    }

    private void deleteProfile() {
        String name = selectedProfile;
        if (name.isEmpty()) return;
        try { core.deleteProfile(name); selectedProfile = ""; profiles = core.profiles(); report("Deleted profile “" + name + "”.", false); }
        catch (IOException | IllegalArgumentException exception) { report("Could not delete profile: " + exception.getMessage(), true); }
    }

    @Override public void close() {
        if (dirty) persist();
        super.close();
    }

    @Override public void removed() { if (dirty) persist(); }

    public AnalyzerScreen showProfiles() { category = "PROFILES"; refreshProfiles(); return this; }
    public AnalyzerScreen inspect(String id) {
        core.modules().stream().filter(module -> module.id().equals(id)).findFirst().ifPresent(module -> {
            selected = module; category = "ALL"; scroll = Math.max(0, core.modules().indexOf(module) / 2 * 98 - 80); settingsScroll = 0;
        });
        return this;
    }
    private void switchLayout(String layout) { core.setValue("menu", "layout", layout); changed(); persist(); client.setScreen(MenuScreens.create(core)); }
    private void diagnostic(String id, Rect bounds, Rect clip) {
        int x = Math.max(bounds.x, clip.x), y = Math.max(bounds.y, clip.y);
        int right = Math.min(bounds.right(), clip.right()), bottom = Math.min(bounds.bottom(), clip.bottom());
        if (right > x && bottom > y) controlBounds.put(id, new Rect(x, y, right - x, bottom - y));
    }
    public double[] controlPoint(String action) {
        Rect bounds = controlBounds.get(action);
        return bounds == null ? null : new double[]{canvasX + (bounds.x + bounds.w / 2.0) * canvasScale, canvasY + (bounds.y + bounds.h / 2.0) * canvasScale};
    }
    private enum Focus { NONE, SEARCH, PROFILE }
    private static final class AmbientParticle {
        double x, y, vx, vy;
        final double baseVx, baseVy, phase;
        AmbientParticle(double x, double y, double vx, double vy, double phase) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy;
            this.baseVx = vx; this.baseVy = vy; this.phase = phase;
        }
    }
    private record Hit(Rect bounds, Runnable action) {}
    private record SliderDrag(String module, Setting<?> setting, Rect track) {}
    private record Rect(int x, int y, int w, int h) {
        int right() { return x + w; }
        int bottom() { return y + h; }
        boolean contains(double px, double py) { return px >= x && py >= y && px < right() && py < bottom(); }
    }
}
