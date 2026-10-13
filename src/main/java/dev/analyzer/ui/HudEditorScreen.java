package dev.analyzer.ui;

import dev.analyzer.core.AnalyzerCore;
import dev.analyzer.core.ModuleDefinition;
import dev.analyzer.render.AnalyzerRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Drag the exact gameplay widgets; their normalized positions persist across resolution changes. */
public final class HudEditorScreen extends Screen {
    private static final int TOOLBAR_WIDTH = 1000, TOOLBAR_HEIGHT = 120;
    private final AnalyzerCore core;
    private final Screen back;
    private List<HudWidgets.WidgetBounds> widgets = List.of();
    private final List<Hit> hits = new ArrayList<>();
    private final Map<String, double[]> diagnostics = new HashMap<>();
    private String selected = "watermark", dragging, focusedControl, status = "Drag to move · right click to toggle · arrows move selection";
    private double grabX, grabY;
    private boolean dirty, error;
    private long changedAt;
    private float toolbarScale, toolbarX, toolbarY;
    private double mx, my;
    public HudEditorScreen(AnalyzerCore core, Screen back) { super(Text.literal("Analyzer / HUD Studio")); this.core = core; this.back = back; }
    @Override public boolean shouldPause() { return false; }
    @Override public void render(DrawContext draw, int mouseX, int mouseY, float delta) {
        try (GuiDraw.GlassScope optical = GuiDraw.glassScope(draw,
                core.getChoice("hud", "theme").equals("Glass") ? "Glass" : StudioAuxiliary.theme(core))) {
        draw.fill(0, 0, width, height, 0x24000000);
        int accent = StudioAuxiliary.accent(core);
        List<HudWidgets.WidgetBounds> layout = new ArrayList<>(HudWidgets.render(core, draw, true));
        HudWidgets.WidgetBounds radar = AnalyzerRenderer.renderEditorRadar(draw);
        if (radar != null) layout.add(radar);
        widgets = List.copyOf(layout);
        for (HudWidgets.WidgetBounds widget : widgets) if (widget.id().equals(selected)) {
            StudioAuxiliary.surface(draw, widget.x() - 2, widget.y() - 2, widget.width() + 4, widget.height() + 4, 10,
                    Palette.alpha(accent, 12), Palette.alpha(accent, 5), 0, 0, Palette.alpha(accent, 60), 3);
            if (dragging != null) {
                int centerX = widget.x() + widget.width() / 2, centerY = widget.y() + widget.height() / 2;
                draw.fill(centerX, 0, centerX + 1, height, Palette.alpha(accent, 25));
                draw.fill(0, centerY, width, centerY + 1, Palette.alpha(accent, 25));
            }
        }
        hits.clear(); diagnostics.clear();
        toolbarScale = StudioAuxiliary.canvasScale(client, TOOLBAR_WIDTH, TOOLBAR_HEIGHT, .92, .24);
        toolbarX = (width - TOOLBAR_WIDTH * toolbarScale) / 2;
        toolbarY = height - TOOLBAR_HEIGHT * toolbarScale - 10;
        mx = (mouseX - toolbarX) / toolbarScale; my = (mouseY - toolbarY) / toolbarScale;
        draw.getMatrices().push(); draw.getMatrices().translate(toolbarX, toolbarY, 0); draw.getMatrices().scale(toolbarScale, toolbarScale, 1);
        try (var font = GuiDraw.sansFont()) {
        GuiDraw.shaderPanel(draw, 0, 0, TOOLBAR_WIDTH, TOOLBAR_HEIGHT, 20, accent, StudioAuxiliary.glass(core), StudioAuxiliary.theme(core));
        GuiDraw.heading(draw, textRenderer, "HUD Studio", 22, 15, 1.45f, StudioAuxiliary.TEXT);
        GuiDraw.label(draw, textRenderer, "Arrange your widgets", 22, 40, .94f, StudioAuxiliary.MUTED);
        button(draw, "theme", core.getChoice("hud", "theme"), 204, 13, 95, () -> {
            List<String> themes = List.of("Glass", "Classic", "Minimal", "Neon");
            core.setValue("hud", "theme", themes.get((themes.indexOf(core.getChoice("hud", "theme")) + 1) % themes.size())); changed();
        });
        button(draw, "scale-minus", "−", 310, 13, 30, () -> { core.setValue("hud", "scale", core.getNumber("hud", "scale") - 0.05); changed(); });
        GuiDraw.label(draw, textRenderer, Math.round(core.getNumber("hud", "scale") * 100) + "%", 350, 24, .96f, accent);
        button(draw, "scale-plus", "+", 407, 13, 30, () -> { core.setValue("hud", "scale", core.getNumber("hud", "scale") + 0.05); changed(); });
        toggleButton(draw, "snap", "Snap", 447, 13, 85, core.getBoolean("hud", "snap"), () -> { core.setValue("hud", "snap", !core.getBoolean("hud", "snap")); changed(); });
        button(draw, "disabled", "Show " + (core.getBoolean("hud", "show-disabled") ? "all" : "active"), 544, 13, 99, () -> { core.setValue("hud", "show-disabled", !core.getBoolean("hud", "show-disabled")); changed(); });
        button(draw, "selected-toggle", enabled(selected) ? "Disable" : "Enable", 654, 13, 92, () -> { toggleSelected(); changed(); });
        button(draw, "selected-settings", "Settings", 758, 13, 89, () -> openSettings(selected.equals("radar") ? "entity-esp" : selected));
        button(draw, "done", "Done", 901, 13, 77, this::close);
        button(draw, "menu-settings", "Menu settings", 22, 66, 150, () -> openSettings("hud"));
        button(draw, "reset-layout", "Reset layout", 184, 66, 118, this::resetLayout);
        String name = selected.equals("radar") ? "Radar" : core.modules().stream().filter(module -> module.id().equals(selected)).map(ModuleDefinition::name).findFirst().orElse(selected);
        GuiDraw.heading(draw, textRenderer, name, 324, 64, 1.12f, StudioAuxiliary.TEXT);
        GuiDraw.label(draw, textRenderer, GuiDraw.truncate(textRenderer, status, 650, .89f), 324, 86, .89f, error ? Palette.ERROR : StudioAuxiliary.MUTED);
        }
        draw.getMatrices().pop();
        }
    }
    private void openSettings(String id) {
        Screen settings = MenuScreens.create(core, id);
        if (settings instanceof CatalogScreen catalog) catalog.withParent(this);
        client.setScreen(settings);
    }
    private boolean enabled(String id) { return id.equals("radar") ? core.isEnabled("entity-esp") && core.getBoolean("entity-esp", "radar") : core.isEnabled(id); }
    private void toggleSelected() { if (selected.equals("radar")) core.setValue("entity-esp", "radar", !core.getBoolean("entity-esp", "radar")); else core.toggle(selected); }
    private void button(DrawContext draw, String id, String text, int x, int y, int w, Runnable action) {
        boolean hover = mx >= x && my >= y && mx < x + w && my < y + 32;
        StudioAuxiliary.button(draw, core, textRenderer, x, y, w, 32, text, true, false, hover);
        control(draw, id, x, y, w, action);
    }
    private void toggleButton(DrawContext draw, String id, String text, int x, int y, int w, boolean enabled, Runnable action) {
        boolean hover = mx >= x && my >= y && mx < x + w && my < y + 32;
        GuiDraw.shaderCard(draw, x, y, w, 32, 10, StudioAuxiliary.accent(core), StudioAuxiliary.glass(core), StudioAuxiliary.theme(core), hover ? 1 : 0, false);
        GuiDraw.label(draw, textRenderer, text, x + 10, y + 10, .95f, StudioAuxiliary.TEXT);
        GuiDraw.shaderPill(draw, x + w - 37, y + 8, 29, 16, StudioAuxiliary.accent(core), enabled);
        control(draw, id, x, y, w, action);
    }
    private void control(DrawContext draw, String id, int x, int y, int w, Runnable action) {
        hits.add(new Hit(id, x, y, w, 32, action));
        diagnostics.put(id, new double[]{toolbarX + (x + w / 2.0) * toolbarScale, toolbarY + (y + 16) * toolbarScale});
        if (id.equals(focusedControl)) StudioAuxiliary.surface(draw, x + 8, y + 30, w - 16, 1.5f, .75f,
                StudioAuxiliary.accent(core), StudioAuxiliary.accent(core), 0, 0, 0, 0);
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        double tx = (x - toolbarX) / toolbarScale, ty = (y - toolbarY) / toolbarScale;
        if (button == 0) for (int i = hits.size() - 1; i >= 0; i--) { Hit hit = hits.get(i); if (hit.contains(tx, ty)) { focusedControl = hit.id; hit.action.run(); return true; } }
        for (int i = widgets.size() - 1; i >= 0; i--) {
            HudWidgets.WidgetBounds widget = widgets.get(i);
            if (!widget.contains(x, y)) continue;
            selected = widget.id(); focusedControl = null;
            if (button == 1) { toggleSelected(); changed(); }
            else if (button == 0) { dragging = selected; grabX = x - widget.x(); grabY = y - widget.y(); }
            return true;
        }
        return true;
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (dragging == null || button != 0) return false;
        HudWidgets.WidgetBounds widget = widgets.stream().filter(w -> w.id().equals(dragging)).findFirst().orElse(null);
        if (widget == null) return false;
        double px = x - grabX, py = y - grabY;
        if (core.getBoolean("hud", "snap")) { px = Math.round(px / 8) * 8; py = Math.round(py / 8) * 8; }
        HudWidgets.move(core, dragging, Math.clamp(px * 100 / Math.max(1, width - widget.width()), 0, 100), Math.clamp(py * 100 / Math.max(1, height - widget.height()), 0, 100));
        changed(); return true;
    }
    @Override public boolean mouseReleased(double x, double y, int button) { dragging = null; return true; }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { close(); return true; }
        if (key == GLFW.GLFW_KEY_TAB && !hits.isEmpty()) {
            int current = -1;
            for (int i = 0; i < hits.size(); i++) if (hits.get(i).id.equals(focusedControl)) current = i;
            int direction = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? -1 : 1;
            focusedControl = hits.get(Math.floorMod(current + direction, hits.size())).id; return true;
        }
        if (focusedControl != null && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE)) {
            hits.stream().filter(hit -> hit.id.equals(focusedControl)).findFirst().ifPresent(hit -> hit.action.run()); return true;
        }
        if (key == GLFW.GLFW_KEY_SPACE) { toggleSelected(); changed(); return true; }
        double step = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? 0.1 : 1;
        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
            double x = HudWidgets.position(core, selected, true), y = HudWidgets.position(core, selected, false);
            if (key == GLFW.GLFW_KEY_LEFT) x -= step; else if (key == GLFW.GLFW_KEY_RIGHT) x += step; else if (key == GLFW.GLFW_KEY_UP) y -= step; else y += step;
            HudWidgets.move(core, selected, x, y); changed(); return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }
    private void resetLayout() {
        for (ModuleDefinition module : core.modules()) if (HudWidgets.IDS.contains(module.id()) && !module.id().equals("coordinates"))
            module.settings().stream().filter(s -> s.id().equals("x") || s.id().equals("y")).forEach(setting -> core.setValue(module.id(), setting.id(), setting.defaultValue()));
        core.modules().stream().filter(m -> m.id().equals("hud")).findFirst().ifPresent(module -> module.settings().stream().filter(s -> s.id().endsWith("-x") || s.id().endsWith("-y")).forEach(setting -> core.setValue("hud", setting.id(), setting.defaultValue())));
        changed(); status = "Layout reset to defaults.";
    }
    private void changed() { dirty = true; error = false; changedAt = System.currentTimeMillis(); }
    private void persist() { try { core.save(); dirty = false; status = "All positions saved."; error = false; } catch (IOException exception) { status = "Save failed: " + exception.getMessage(); error = true; } }
    @Override public void tick() { if (dirty && !error && System.currentTimeMillis() - changedAt > 650) persist(); }
    @Override public void removed() { if (dirty) persist(); }
    @Override public void close() { if (dirty) persist(); client.setScreen(back); }
    public double[] controlPoint(String action) {
        if (action.startsWith("widget:")) {
            String id = action.substring(7);
            HudWidgets.WidgetBounds widget = widgets.stream().filter(w -> w.id().equals(id)).findFirst().orElse(null);
            return widget == null ? null : new double[]{widget.x() + widget.width() / 2.0, widget.y() + widget.height() / 2.0};
        }
        return diagnostics.get(action);
    }
    public List<HudWidgets.WidgetBounds> widgetBounds() { return widgets; }
    private record Hit(String id, int x, int y, int w, int h, Runnable action) { boolean contains(double px, double py) { return px >= x && py >= y && px < x + w && py < y + h; } }
}
