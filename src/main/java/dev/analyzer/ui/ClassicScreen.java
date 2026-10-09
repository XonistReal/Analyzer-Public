package dev.analyzer.ui;

import dev.analyzer.core.AnalyzerCore;
import dev.analyzer.core.Category;
import dev.analyzer.core.ModuleDefinition;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Floating, draggable category windows with inline right-click settings. */
public final class ClassicScreen extends CatalogScreen {
    private static final int COLUMN_W = 184, COLUMN_H = 218;
    private static final int AVAILABLE_Y = H - COLUMN_H - 36;
    private final List<Window> windows = new ArrayList<>();
    private Window dragged;
    private double grabX, grabY;

    public ClassicScreen(AnalyzerCore core) {
        super(core, "Analyzer / Classic");
        for (Category category : Category.values()) windows.add(new Window(category));
    }
    @Override protected String layout() { return "Classic"; }
    @Override protected void body(DrawContext draw) {
        for (Window window : windows) drawWindow(draw, window);
    }
    private void drawWindow(DrawContext draw, Window window) {
        String prefix = prefix(window.category);
        int x = (int) Math.round(core.getNumber("menu", prefix + "-x") / 100 * (W - COLUMN_W));
        int y = (int) Math.round(core.getNumber("menu", prefix + "-y") / 100 * AVAILABLE_Y);
        boolean collapsed = core.getBoolean("menu", prefix + "-collapsed");
        List<ModuleDefinition> modules = matching().stream().filter(module -> module.category() == window.category).toList();
        Area box = new Area(x, y, COLUMN_W, collapsed ? 33 : COLUMN_H);
        window.bounds = box;
        GuiDraw.glow(draw, x, y, COLUMN_W, box.h(), 9, Palette.alpha(accent, 120));
        panel(draw, box, dragged == window);
        GuiDraw.rounded(draw, x + 1, y + 1, COLUMN_W - 2, 31, 8, Palette.alpha(accent, 65));
        draw.fill(x + 1, y + 24, x + COLUMN_W - 1, y + 32, Palette.alpha(accent, 65));
        GuiDraw.heading(draw, textRenderer, window.category.label().toUpperCase(Locale.ROOT), x + 11, y + 11, 0.92f, Palette.TEXT);
        label(draw, Integer.toString(modules.size()), x + 133, y + 12, 0.8f, accent);
        control("header:" + window.category.name(), new Area(x, y, COLUMN_W, 32), 0, () -> {
            dragged = window; grabX = mx - x; grabY = my - y;
            windows.remove(window); windows.add(window); searchFocused = false;
        });
        control("collapse:" + window.category.name(), new Area(x, y, COLUMN_W, 32), 1, () -> {
            core.setValue("menu", prefix + "-collapsed", !collapsed); changed();
        });
        button(draw, "collapse:" + window.category.name(), collapsed ? "+" : "−", x + COLUMN_W - 30, y + 5, 23, 22, false, () -> {
            core.setValue("menu", prefix + "-collapsed", !collapsed); changed();
        });
        if (collapsed) return;
        Area viewport = new Area(x + 4, y + 36, COLUMN_W - 8, COLUMN_H - 42);
        int content = modules.size() * 28;
        ModuleDefinition expanded = modules.stream().filter(module -> module.id().equals(window.expanded)).findFirst().orElse(null);
        if (expanded != null) content += settingsHeight(expanded);
        window.maxScroll = Math.max(0, content - viewport.h());
        window.scroll = Math.clamp(window.scroll, 0, window.maxScroll);
        draw.enableScissor(viewport.x(), viewport.y(), viewport.right(), viewport.bottom());
        int rowY = viewport.y() - (int) window.scroll;
        if (modules.isEmpty()) label(draw, "No matching modules", x + 10, rowY + 12, 0.77f, Palette.MUTED);
        for (ModuleDefinition module : modules) {
            boolean enabled = core.isEnabled(module.id()), selected = module.id().equals(window.expanded);
            Area row = new Area(x + 6, rowY, COLUMN_W - 12, 26);
            if (row.bottom() >= viewport.y() && row.y() < viewport.bottom()) {
                GuiDraw.rounded(draw, row.x(), row.y(), row.w(), row.h(), 4,
                        Palette.alpha(enabled ? accent : selected ? Palette.CARD_HOVER : Palette.CARD, enabled ? 60 : 200));
                if (enabled) draw.fill(row.x(), row.y() + 5, row.x() + 2, row.bottom() - 5, accent);
                label(draw, GuiDraw.truncate(textRenderer, module.name(), 121, 0.87f), row.x() + 8, row.y() + 9, 0.87f, enabled ? Palette.TEXT : Palette.MUTED);
                toggle(draw, row.right() - 32, row.y() + 7, enabled);
                clippedControl("module:" + module.id(), row, viewport, 0, () -> { core.toggle(module.id()); changed(); });
                clippedControl("inspect:" + module.id(), row, viewport, 1, () -> { window.expanded = selected ? "" : module.id(); capture = null; searchFocused = false; });
            }
            rowY += 28;
            if (selected) {
                settings(draw, module, x + 6, rowY + 3, COLUMN_W - 12, viewport);
                rowY += settingsHeight(module);
            }
        }
        draw.disableScissor();
        if (window.maxScroll > 0) {
            int thumb = Math.max(18, (int) (viewport.h() * viewport.h() / (double) Math.max(1, content)));
            int ty = viewport.y() + (int) ((viewport.h() - thumb) * window.scroll / window.maxScroll);
            GuiDraw.rounded(draw, x + COLUMN_W - 3, ty, 2, thumb, 1, Palette.alpha(accent, 180));
        }
    }
    private String prefix(Category category) { return "classic-" + category.name().toLowerCase(Locale.ROOT); }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (dragged != null && button == 0) {
            mx = (x - ox) / scale; my = (y - oy) / scale;
            double px = Math.clamp(mx - grabX, 0, W - COLUMN_W);
            double py = Math.clamp(my - grabY, 79, AVAILABLE_Y);
            core.setValue("menu", prefix(dragged.category) + "-x", px * 100 / (W - COLUMN_W));
            core.setValue("menu", prefix(dragged.category) + "-y", py * 100 / AVAILABLE_Y);
            changed(); return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseReleased(double x, double y, int button) { dragged = null; return super.mouseReleased(x, y, button); }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        double lx = (x - ox) / scale, ly = (y - oy) / scale;
        for (int i = windows.size() - 1; i >= 0; i--) {
            Window window = windows.get(i);
            if (window.bounds != null && window.bounds.contains(lx, ly)) { window.scroll = Math.clamp(window.scroll - vertical * 27, 0, window.maxScroll); return true; }
        }
        return true;
    }
    private static final class Window {
        final Category category;
        String expanded = "";
        double scroll, maxScroll;
        Area bounds;
        Window(Category category) { this.category = category; }
    }
}
