package dev.analyzer.ui;

import dev.analyzer.AnalyzerClient;
import dev.analyzer.core.AnalyzerCore;
import dev.analyzer.core.ModuleDefinition;
import dev.analyzer.core.Setting;
import dev.analyzer.effects.GpuEffects;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Original Analyzer click GUI, with a logical canvas that fits every Minecraft GUI scale. */
public final class AnalyzerScreen extends Screen {
    private static final int CANVAS_W = 900, CANVAS_H = 520;
    private static final Rect MODULE_VIEW = new Rect(180, 269, 409, 203);
    private static final Rect SETTINGS_VIEW = new Rect(627, 218, 251, 204);
    private static final Rect PROFILE_VIEW = new Rect(181, 241, 408, 231);
    private final AnalyzerCore core;
    private final List<Hit> hits = new ArrayList<>();
    private final Map<String, Double> animation = new HashMap<>();
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
        refreshProfiles();
    }

    @Override public boolean shouldPause() { return false; }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        frameSeconds = Math.clamp((now - lastFrame) / 1_000_000_000.0, 0.001, 0.05);
        lastFrame = now;
        double entrance = 1 - Math.exp(-(now - openedAt) / 160_000_000.0);
        canvasScale = Math.min(1.25f, Math.min((width - 20f) / CANVAS_W, (height - 20f) / CANVAS_H));
        canvasScale = Math.max(0.15f, canvasScale);
        canvasX = (width - CANVAS_W * canvasScale) / 2;
        canvasY = (height - CANVAS_H * canvasScale) / 2 + (float) ((1 - entrance) * 14 * canvasScale);
        mx = (mouseX - canvasX) / canvasScale;
        my = (mouseY - canvasY) / canvasScale;
        hits.clear();
        context.fillGradient(0, 0, width, height, 0x99050A10, 0xD9060C10);
        GpuEffects.drawBackdrop(context);
        context.getMatrices().push();
        context.getMatrices().translate(canvasX, canvasY, 0);
        context.getMatrices().scale(canvasScale, canvasScale, 1);
        GuiDraw.glow(context, 0, 0, CANVAS_W, CANVAS_H, 18, Palette.MINT);
        GuiDraw.outlined(context, 0, 0, CANVAS_W, CANVAS_H, 18, Palette.BORDER, Palette.BACKGROUND);
        GuiDraw.rounded(context, 1, 1, 158, CANVAS_H - 2, 17, Palette.SURFACE);
        context.fill(144, 1, 159, CANVAS_H - 1, Palette.SURFACE);
        context.fill(159, 20, 160, CANVAS_H - 20, Palette.BORDER);
        context.fill(607, 20, 608, 480, Palette.BORDER);
        drawSidebar(context);
        drawHeader(context);
        if (category.equals("PROFILES")) drawProfiles(context);
        else { drawModules(context); drawInspector(context); }
        drawFooter(context);
        context.getMatrices().pop();
    }

    private void drawSidebar(DrawContext c) {
        GuiDraw.glow(c, 23, 22, 34, 34, 10, Palette.MINT);
        GuiDraw.rounded(c, 23, 22, 34, 34, 10, 0xFF19342F);
        GuiDraw.logo(c, 28, 26, 23, Palette.MINT);
        heading(c, "ANALYZER", 67, 25, 1.1f, Palette.TEXT);
        label(c, "CLIENT / 1.21.4", 68, 42, 0.68f, Palette.SUBTLE);
        label(c, "WORKSPACE", 23, 95, 0.74f, Palette.SUBTLE);
        sidebar(c, "ALL", "All modules", "01", 116);
        sidebar(c, "VISUAL", "Visual", "02", 159);
        sidebar(c, "WORLD", "World", "03", 202);
        sidebar(c, "HUD", "HUD", "04", 245);
        c.fill(24, 307, 135, 308, Palette.BORDER);
        label(c, "PERSONAL", 23, 328, 0.74f, Palette.SUBTLE);
        sidebar(c, "PROFILES", "Profiles", "05", 350);
        GuiDraw.outlined(c, 18, 425, 123, 53, 10, Palette.BORDER, 0xFF121F25);
        GuiDraw.rounded(c, 29, 439, 5, 5, 2, Palette.MINT);
        label(c, "SYSTEM ONLINE", 40, 436, 0.73f, Palette.MINT);
        label(c, enabledCount() + " modules enabled", 29, 454, 0.75f, Palette.MUTED);
    }

    private void sidebar(DrawContext c, String id, String name, String number, int y) {
        Rect bounds = new Rect(16, y, 128, 35);
        double hover = ease("nav-" + id, bounds.contains(mx, my) ? 1 : 0);
        boolean active = category.equals(id);
        GuiDraw.rounded(c, 16, y, 128, 35, 8,
                active ? 0xFF20352F : Palette.mix(Palette.SURFACE, Palette.CARD_HOVER, hover));
        if (active) GuiDraw.rounded(c, 16, y + 9, 3, 17, 1, Palette.MINT);
        label(c, number, 27, y + 12, 0.72f, active ? Palette.MINT : Palette.SUBTLE);
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
        button(c, "close", "×", 850, 24, 29, 29, false, this::close);
        label(c, category.equals("PROFILES") ? "PROFILE MANAGER" : "MODULE INSPECTOR", 628, 64, 0.78f, Palette.SUBTLE);
    }

    private void drawModules(DrawContext c) {
        GuiDraw.outlined(c, 181, 91, 408, 108, 12, 0xFF2D4844, 0xFF142521);
        int particleCount = core.isEnabled("particles") ? (int) (core.getNumber("particles", "density") * 36) : 0;
        for (int i = 0; i < particleCount; i++) {
            double time = System.nanoTime() / 1_000_000_000.0;
            int x = 379 + (i * 37 % 195);
            int y = 101 + (int) ((i * 23 + time * (4 + i % 3)) % 87);
            GuiDraw.rounded(c, x, y, 2, 2, 1, Palette.alpha(Palette.MINT, 45 + i % 4 * 12));
        }
        label(c, "DESIGNED TO DISCOVER", 197, 106, 0.76f, Palette.MINT);
        heading(c, "Clarity changes everything.", 197, 126, 1.13f, Palette.TEXT);
        label(c, "Beautiful visuals. Precise awareness.", 197, 151, 0.84f, Palette.MUTED);
        label(c, "Built for your world.", 197, 171, 0.84f, Palette.MUTED);
        radar(c, 527, 144, 39);
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
                isSelected ? 0xFF4E9C83 : Palette.mix(Palette.BORDER, 0xFF406058, hover),
                Palette.mix(Palette.CARD, Palette.CARD_HOVER, hover));
        GuiDraw.rounded(c, x + 12, y + 13, 25, 25, 7, active ? 0xFF234238 : 0xFF223039);
        icon(c, module.id(), x + 18, y + 19, active ? Palette.MINT : Palette.MUTED);
        heading(c, GuiDraw.truncate(textRenderer, module.name(), 105, 0.97f), x + 46, y + 13, 0.97f, Palette.TEXT);
        label(c, module.category().toString(), x + 46, y + 29, 0.65f, Palette.SUBTLE);
        List<String> lines = GuiDraw.wrap(textRenderer, module.description(), 172, 0.76f);
        for (int i = 0; i < Math.min(2, lines.size()); i++) label(c, lines.get(i), x + 12, y + 51 + i * 12, 0.76f, Palette.MUTED);
        clippedHit(bounds, MODULE_VIEW, () -> {
            selected = module; settingsScroll = 0; focus = Focus.NONE; captureModule = null;
        });
        Rect toggle = new Rect(x + 155, y + 13, 31, 17);
        drawToggle(c, "cardtoggle-" + module.id(), toggle, active);
        clippedHit(toggle, MODULE_VIEW, () -> { core.toggle(module.id()); changed(); });
    }

    private void drawInspector(DrawContext c) {
        if (selected == null) return;
        boolean enabled = core.isEnabled(selected.id());
        GuiDraw.rounded(c, 629, 94, 39, 39, 10, 0xFF20332F);
        icon(c, selected.id(), 641, 106, Palette.MINT);
        label(c, selected.category().toString(), 679, 96, 0.72f, Palette.SUBTLE);
        heading(c, selected.name(), 679, 114, 1.3f, Palette.TEXT);
        List<String> description = GuiDraw.wrap(textRenderer, selected.description(), 244, 0.9f);
        for (int i = 0; i < Math.min(2, description.size()); i++) label(c, description.get(i), 629, 145 + i * 14, 0.9f, Palette.MUTED);
        label(c, enabled ? "MODULE ACTIVE" : "MODULE DISABLED", 629, 185, 0.79f, enabled ? Palette.MINT : Palette.SUBTLE);
        Rect toggle = new Rect(832, 179, 44, 23);
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
        label(c, setting.label(), 629, y, 0.94f, Palette.TEXT);
        switch (setting.kind()) {
            case BOOLEAN -> {
                boolean value = core.getBoolean(moduleId, setting.id());
                Rect bounds = new Rect(841, y - 2, 35, 19);
                drawToggle(c, "setting-" + moduleId + setting.id(), bounds, value);
                label(c, value ? "Enabled" : "Disabled", 629, y + 22, 0.78f, value ? Palette.MINT : Palette.SUBTLE);
                clippedHit(new Rect(629, y - 2, 247, 42), SETTINGS_VIEW, () -> {
                    core.setValue(moduleId, setting.id(), !core.getBoolean(moduleId, setting.id())); changed();
                });
            }
            case NUMBER -> {
                double value = core.getNumber(moduleId, setting.id());
                String formatted = number(value);
                label(c, formatted, 875 - GuiDraw.width(textRenderer, formatted, 0.85f), y + 1, 0.85f, Palette.MINT);
                Rect track = new Rect(629, y + 29, 245, 5);
                GuiDraw.rounded(c, track.x, track.y, track.w, track.h, 2, Palette.BORDER);
                double amount = setting.max() == setting.min() ? 0 : Math.clamp((value - setting.min()) / (setting.max() - setting.min()), 0.0, 1.0);
                int px = track.x + (int) (amount * track.w);
                GuiDraw.rounded(c, track.x, track.y, px - track.x, track.h, 2, Palette.MINT);
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
                label(c, value, 639, y + 26, 0.86f, Palette.MINT);
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
                error ? Palette.ERROR : dirty ? Palette.MUTED : Palette.MINT);
        String shortcut = category.equals("PROFILES") ? "TAB NAME · ENTER SAVE" : "/ SEARCH";
        label(c, keyName(AnalyzerClient.getMenuKey()).toUpperCase(Locale.ROOT) + " / ESC CLOSE    ·    " + shortcut, 641, 500, 0.67f, Palette.SUBTLE);
        if (error && new Rect(181, 489, 442, 31).contains(mx, my)) {
            List<String> lines = GuiDraw.wrap(textRenderer, status, 398, 0.85f);
            int h = 23 + lines.size() * 14;
            GuiDraw.outlined(c, 181, 482 - h, 424, h, 8, 0xFF86505B, 0xFF261D25);
            for (int i = 0; i < lines.size(); i++) label(c, lines.get(i), 193, 494 - h + i * 14, 0.85f, Palette.ERROR);
        }
    }

    private void radar(DrawContext c, int x, int y, int radius) {
        double angle = System.nanoTime() / 1_000_000_000.0 * 0.42;
        for (int ring = 1; ring <= 3; ring++) {
            int r = ring * radius / 3;
            for (int i = 0; i < 48; i++) {
                double a = i * Math.PI / 24;
                GuiDraw.line(c, x + Math.cos(a) * r, y + Math.sin(a) * r,
                        x + Math.cos(a + Math.PI / 24) * r, y + Math.sin(a + Math.PI / 24) * r, 0xFF2A493F);
            }
        }
        GuiDraw.line(c, x - radius, y, x + radius, y, 0xFF2A493F);
        GuiDraw.line(c, x, y - radius, x, y + radius, 0xFF2A493F);
        GuiDraw.cube(c, x - 12, y - 13, 25, Palette.MINT);
        GuiDraw.line(c, x, y, x + Math.cos(angle) * radius, y + Math.sin(angle) * radius, Palette.alpha(Palette.MINT, 100));
        int px = x + (int) (Math.cos(angle) * (radius - 4)), py = y + (int) (Math.sin(angle) * (radius - 4));
        GuiDraw.glow(c, px - 2, py - 2, 4, 4, 2, Palette.MINT);
        GuiDraw.rounded(c, px - 2, py - 2, 4, 4, 2, Palette.MINT);
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
            default -> {
                GuiDraw.line(c, x, y + 12, x + 6, y, color); GuiDraw.line(c, x + 6, y, x + 14, y + 12, color);
                GuiDraw.line(c, x, y + 12, x + 14, y + 12, color); GuiDraw.line(c, x + 6, y, x + 6, y + 12, color);
            }
        }
    }

    private void drawToggle(DrawContext c, String id, Rect bounds, boolean value) {
        double amount = ease(id, value ? 1 : 0);
        GuiDraw.rounded(c, bounds.x, bounds.y, bounds.w, bounds.h, bounds.h / 2,
                Palette.mix(0xFF34424A, 0xFF57C79F, amount));
        int size = bounds.h - 6;
        int x = bounds.x + 3 + (int) ((bounds.w - size - 6) * amount);
        GuiDraw.rounded(c, x, bounds.y + 3, size, size, size / 2,
                Palette.mix(0xFFB6C5CB, Palette.TEXT, amount));
    }

    private void field(DrawContext c, String placeholder, String value, Focus target, Rect bounds) {
        boolean focused = focus == target;
        GuiDraw.outlined(c, bounds.x, bounds.y, bounds.w, bounds.h, 7,
                focused ? Palette.MINT : Palette.BORDER, Palette.SURFACE);
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
            c.fill(cursorX, bounds.y + 9, cursorX + 1, bounds.y + 23, Palette.MINT);
        }
        hits.add(new Hit(bounds, () -> { focus = target; selectAll = false; captureModule = null; }));
    }

    private void button(DrawContext c, String id, String label, int x, int y, int w, int h, boolean primary, Runnable action) {
        Rect bounds = new Rect(x, y, w, h);
        double hover = ease("button-" + id, bounds.contains(mx, my) ? 1 : 0);
        staticButton(c, label, x, y, w, h, primary, hover > 0.1);
        hits.add(new Hit(bounds, action));
    }

    private void staticButton(DrawContext c, String value, int x, int y, int w, int h, boolean primary, boolean hover) {
        int inside = primary ? hover ? 0xFF91FFDA : Palette.MINT : hover ? Palette.CARD_HOVER : Palette.CARD;
        GuiDraw.outlined(c, x, y, w, h, 6, primary ? Palette.MINT : Palette.BORDER, inside);
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
                .filter(module -> (module.name() + " " + module.description()).toLowerCase(Locale.ROOT).contains(filter)).toList();
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
        if (category.equals("PROFILES") && PROFILE_VIEW.contains(x, y)) profileScroll = Math.max(0, profileScroll - verticalAmount * 30);
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

    private enum Focus { NONE, SEARCH, PROFILE }
    private record Hit(Rect bounds, Runnable action) {}
    private record SliderDrag(String module, Setting<?> setting, Rect track) {}
    private record Rect(int x, int y, int w, int h) {
        int right() { return x + w; }
        int bottom() { return y + h; }
        boolean contains(double px, double py) { return px >= x && py >= y && px < right() && py < bottom(); }
    }
}
