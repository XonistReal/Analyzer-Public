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
    private final AnalyzerCore core;
    private final Screen back;
    private List<HudWidgets.WidgetBounds> widgets = List.of();
    private final List<Hit> hits = new ArrayList<>();
    private final Map<String, double[]> diagnostics = new HashMap<>();
    private String selected = "watermark", dragging, status = "Drag a widget / right click to toggle / arrows move selection";
    private double grabX, grabY;
    private boolean dirty, error;
    private long changedAt;
    private float toolbarScale, toolbarX, toolbarY;
    public HudEditorScreen(AnalyzerCore core, Screen back) { super(Text.literal("Analyzer / HUD Studio")); this.core = core; this.back = back; }
    @Override public boolean shouldPause() { return false; }
    @Override public void render(DrawContext draw, int mouseX, int mouseY, float delta) {
        draw.fill(0, 0, width, height, 0x4507121B);
        int accent = HudWidgets.accent(core);
        for (int x = 0; x < width; x += 16) draw.fill(x, 0, x + 1, height, 0x123AA2A4);
        for (int y = 0; y < height; y += 16) draw.fill(0, y, width, y + 1, 0x123AA2A4);
        List<HudWidgets.WidgetBounds> layout = new ArrayList<>(HudWidgets.render(core, draw, true));
        HudWidgets.WidgetBounds radar = AnalyzerRenderer.renderEditorRadar(draw);
        if (radar != null) layout.add(radar);
        widgets = List.copyOf(layout);
        for (HudWidgets.WidgetBounds widget : widgets) if (widget.id().equals(selected)) {
            GuiDraw.wireBox(draw, widget.x() - 2, widget.y() - 2, widget.width() + 4, widget.height() + 4, true, 1.2f, accent);
            draw.fill(widget.x() + widget.width() / 2, 0, widget.x() + widget.width() / 2 + 1, height, Palette.alpha(accent, 25));
        }
        hits.clear(); diagnostics.clear();
        toolbarScale = Math.max(0.2f, Math.min(1, (width - 20f) / 810));
        toolbarX = (width - 810 * toolbarScale) / 2;
        toolbarY = height - 64 * toolbarScale - 10;
        draw.getMatrices().push(); draw.getMatrices().translate(toolbarX, toolbarY, 0); draw.getMatrices().scale(toolbarScale, toolbarScale, 1);
        GuiDraw.glow(draw, 0, 0, 810, 64, 9, accent);
        GuiDraw.outlined(draw, 0, 0, 810, 64, 9, Palette.alpha(accent, 140), 0xF5091520);
        GuiDraw.heading(draw, textRenderer, "HUD STUDIO", 12, 10, 1.04f, Palette.TEXT);
        button(draw, "theme", core.getChoice("hud", "theme"), 125, 8, 77, () -> {
            List<String> themes = List.of("Glass", "Classic", "Minimal", "Neon");
            core.setValue("hud", "theme", themes.get((themes.indexOf(core.getChoice("hud", "theme")) + 1) % themes.size())); changed();
        });
        button(draw, "scale-minus", "−", 208, 8, 24, () -> { core.setValue("hud", "scale", core.getNumber("hud", "scale") - 0.05); changed(); });
        GuiDraw.label(draw, textRenderer, Math.round(core.getNumber("hud", "scale") * 100) + "%", 238, 17, 0.81f, accent);
        button(draw, "scale-plus", "+", 277, 8, 24, () -> { core.setValue("hud", "scale", core.getNumber("hud", "scale") + 0.05); changed(); });
        button(draw, "snap", "Snap " + (core.getBoolean("hud", "snap") ? "on" : "off"), 310, 8, 73, () -> { core.setValue("hud", "snap", !core.getBoolean("hud", "snap")); changed(); });
        button(draw, "disabled", "Show " + (core.getBoolean("hud", "show-disabled") ? "all" : "active"), 389, 8, 85, () -> { core.setValue("hud", "show-disabled", !core.getBoolean("hud", "show-disabled")); changed(); });
        button(draw, "selected-toggle", enabled(selected) ? "Disable" : "Enable", 482, 8, 70, () -> { toggleSelected(); changed(); });
        button(draw, "selected-settings", "Settings", 558, 8, 73, () -> client.setScreen(new AnalyzerScreen(core).inspect(selected.equals("radar") ? "entity-esp" : selected)));
        button(draw, "reset-layout", "Reset layout", 637, 8, 91, this::resetLayout);
        button(draw, "done", "Done", 735, 8, 62, this::close);
        GuiDraw.label(draw, textRenderer, GuiDraw.truncate(textRenderer, error ? status : selected + " / " + status, 786, 0.79f), 12, 44, 0.79f, error ? Palette.ERROR : Palette.MUTED);
        draw.getMatrices().pop();
    }
    private boolean enabled(String id) { return id.equals("radar") ? core.isEnabled("entity-esp") && core.getBoolean("entity-esp", "radar") : core.isEnabled(id); }
    private void toggleSelected() { if (selected.equals("radar")) core.setValue("entity-esp", "radar", !core.getBoolean("entity-esp", "radar")); else core.toggle(selected); }
    private void button(DrawContext draw, String id, String text, int x, int y, int w, Runnable action) {
        GuiDraw.rounded(draw, x, y, w, 26, 5, 0xFF19303B);
        GuiDraw.label(draw, textRenderer, text, x + 7, y + 8, 0.8f, Palette.TEXT);
        hits.add(new Hit(x, y, w, 26, action));
        diagnostics.put(id, new double[]{toolbarX + (x + w / 2.0) * toolbarScale, toolbarY + (y + 13) * toolbarScale});
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        double tx = (x - toolbarX) / toolbarScale, ty = (y - toolbarY) / toolbarScale;
        if (button == 0) for (int i = hits.size() - 1; i >= 0; i--) { Hit hit = hits.get(i); if (hit.contains(tx, ty)) { hit.action.run(); return true; } }
        for (int i = widgets.size() - 1; i >= 0; i--) {
            HudWidgets.WidgetBounds widget = widgets.get(i);
            if (!widget.contains(x, y)) continue;
            selected = widget.id();
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
    private record Hit(int x, int y, int w, int h, Runnable action) { boolean contains(double px, double py) { return px >= x && py >= y && px < x + w && py < y + h; } }
}
