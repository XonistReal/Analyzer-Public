package dev.analyzer.ui;

import dev.analyzer.core.AnalyzerCore;
import dev.analyzer.core.Category;
import dev.analyzer.core.ClientModules;
import dev.analyzer.core.ModuleDefinition;
import dev.analyzer.effects.GpuEffects;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * True click GUI: free-floating category panels drawn directly over the game, each dragged by its
 * header and saved as a fraction of the free screen area, so placement survives window resizes.
 * Left-click toggles a module; right-click opens its settings in a flyout beside its panel. The same
 * shader surfaces, themes and typed settings as the other layouts; no outer frame or board.
 */
public final class ClickGuiScreen extends CatalogScreen {
    private static final List<Category> CATEGORIES = List.of(Category.COMBAT, Category.HVH, Category.MOVEMENT,
            Category.VISUAL, Category.COSMETICS, Category.WORLD, Category.PLAYER, Category.HUD, Category.CLIENT);
    private static final int PANEL_W = 112, HEADER_H = 20, ROW_H = 15, FLYOUT_W = 228, MARGIN = 4, FOOTER_H = 22;
    private final List<Category> order = new ArrayList<>(CATEGORIES);   // draw order: the last panel is on top
    private final Map<Category, Double> scroll = new EnumMap<>(Category.class);
    private final Map<Category, Area> bounds = new EnumMap<>(Category.class);
    private Category dragging;
    private double grabX, grabY, pressX, pressY, dragX, dragY, flyoutScroll, flyoutMax;
    private boolean moved;
    private String selected, hovered;
    private Area flyout;
    private int toolbarBottom = 30;
    private record Item(String id, String label, Runnable action) { }

    public ClickGuiScreen(AnalyzerCore core) { super(core, "Analyzer / Click GUI"); }
    @Override protected String layout() { return "Click GUI"; }
    @Override protected boolean shaderStyle() { return true; }
    @Override protected float canvasScale() { return 1f; }
    @Override protected int canvasWidth() { return width; }
    @Override protected int canvasHeight() { return height; }
    @Override protected boolean drawShell() { return false; }
    @Override protected void backdrop(DrawContext draw) {
        // The world stays visible behind the panels; a light dim keeps their text readable.
        if (client.world == null) GpuEffects.drawBackdrop(draw);
        else draw.fill(0, 0, width, height, 0x26000000);
    }
    @Override public String selectedModuleId() { return selected; }
    @Override public ClickGuiScreen inspect(String id) {
        core.modules().stream().filter(module -> module.id().equals(id)).findFirst().ifPresent(module -> {
            selected = id; flyoutScroll = 0;
            core.setValue("menu", prefix(module.category()) + "-collapsed", false);
            raise(module.category());
        });
        return this;
    }
    @Override protected void searchChanged() { scroll.clear(); }

    @Override protected void header(DrawContext draw) {
        List<Item> items = List.of(
                new Item("layout-studio", "Studio", () -> switchLayout("Studio")),
                new Item("layout-compact", "Compact", () -> switchLayout("Compact")),
                new Item("profiles", "Profiles", () -> client.setScreen(MenuScreens.profiles(core, this))),
                new Item("hud-editor", "Edit HUD", () -> client.setScreen(new HudEditorScreen(core, this))),
                new Item("automation", "Automation", () -> client.setScreen(MenuScreens.automation(core, this))),
                new Item("findings", "Findings", () -> client.setScreen(new FinderHistoryScreen(core, this))),
                new Item("appearance", "Style", () -> inspect("hud")),
                new Item("reset-panels", "Reset", this::resetPanels),
                new Item("close", "×", this::close));
        int h = 18, gap = 4, searchW = 132, available = Math.max(160, width - 16);
        // Flow the toolbar into as many rows as the screen needs; the search field always leads.
        List<List<Integer>> rows = new ArrayList<>();
        List<Integer> current = new ArrayList<>();
        int used = 16 + gap + searchW;
        for (int i = 0; i < items.size(); i++) {
            int w = itemWidth(items.get(i));
            if (used + gap + w > available && !current.isEmpty()) { rows.add(current); current = new ArrayList<>(); used = 0; }
            current.add(i); used += (used == 0 ? 0 : gap) + w;
        }
        rows.add(current);
        int top = 6, rowPitch = h + 4, total = rows.size() * rowPitch - 4;
        int widest = 0;
        for (int r = 0; r < rows.size(); r++) widest = Math.max(widest, rowWidth(items, rows.get(r), r == 0 ? 16 + gap + searchW : 0, gap));
        int left = (width - widest) / 2;
        GuiDraw.shaderPanel(draw, left - 6, top - 4, widest + 12, total + 8, 9, accent, glass(), theme());
        for (int r = 0; r < rows.size(); r++) {
            int lead = r == 0 ? 16 + gap + searchW : 0, rowW = rowWidth(items, rows.get(r), lead, gap);
            int x = (width - rowW) / 2, y = top + r * rowPitch;
            if (r == 0) {
                BrandAssets.monogram(draw, x, y + 2, 14);
                x += 16 + gap;
                Area field = new Area(x, y, searchW, h);
                GuiDraw.shaderCard(draw, field.x(), field.y(), field.w(), field.h(), 6, accent, glass(), theme(), searchFocused ? 1 : 0, searchFocused);
                label(draw, query.isEmpty() ? "Search modules" : GuiDraw.truncate(textRenderer, query, searchW - 26, .84f),
                        field.x() + 7, field.y() + 5, .84f, query.isEmpty() ? Palette.MUTED : Palette.TEXT);
                control("search", new Area(field.x(), field.y(), searchW - 18, h), 0, () -> { searchFocused = true; selectAll = false; capture = null; });
                if (!query.isEmpty()) button(draw, "search-clear", "×", field.right() - 17, field.y() + 2, 15, 14, false,
                        () -> { query = ""; searchFocused = false; searchChanged(); });
                x += searchW + gap;
            }
            for (int index : rows.get(r)) {
                Item item = items.get(index);
                int w = itemWidth(item);
                button(draw, item.id(), item.label(), x, y, w, h, false, item.action());
                x += w + gap;
            }
        }
        toolbarBottom = top + total + 6;
    }
    private int itemWidth(Item item) { return Math.max(20, Math.round(GuiDraw.width(textRenderer, item.label(), .86f)) + 14); }
    private int rowWidth(List<Item> items, List<Integer> row, int lead, int gap) {
        int width = lead;
        for (int index : row) width += (width == 0 ? 0 : gap) + itemWidth(items.get(index));
        return width;
    }

    @Override protected void body(DrawContext draw) {
        hovered = null; flyout = null; bounds.clear();
        for (Category category : List.copyOf(order)) drawPanel(draw, category);
        if (selected != null) drawFlyout(draw);
    }

    private void drawPanel(DrawContext draw, Category category) {
        List<ModuleDefinition> modules = matching().stream().filter(module -> module.category() == category).toList();
        boolean searching = !query.isBlank();
        if (searching && modules.isEmpty()) return;
        int x = panelX(category), y = Math.max(panelY(category), searching ? toolbarBottom : 0);
        boolean collapsed = !searching && core.getBoolean("menu", prefix(category) + "-collapsed");
        int content = collapsed ? 0 : modules.size() * ROW_H + 4;
        int view = Math.min(content, Math.max(0, height - FOOTER_H - y - HEADER_H));
        double max = Math.max(0, content - view);
        double offset = Math.clamp(scroll.getOrDefault(category, 0.0), 0, max);
        scroll.put(category, offset);
        Area box = new Area(x, y, PANEL_W, HEADER_H + view);
        bounds.put(category, box);
        GuiDraw.shaderPanel(draw, box.x(), box.y(), box.w(), box.h(), 8, accent, glass(), theme());
        long enabled = modules.stream().filter(module -> core.isEnabled(module.id())).count();
        GuiDraw.heading(draw, textRenderer, category.label(), x + 8, y + 6, .9f, Palette.TEXT);
        String count = enabled + "/" + modules.size();
        label(draw, count, x + PANEL_W - 19 - GuiDraw.width(textRenderer, count, .74f), y + 7, .74f, Palette.MUTED);
        label(draw, collapsed ? "+" : "−", x + PANEL_W - 13, y + 5, .95f, Palette.TEXT);
        if (view > 0) draw.fill(x + 6, y + HEADER_H - 1, x + PANEL_W - 6, y + HEADER_H, Palette.alpha(accent, 120));
        Area header = new Area(x, y, PANEL_W, HEADER_H);
        control("header:" + category.name(), header, 0, () -> beginDrag(category, x, y));
        control("collapse:" + category.name(), header, 1,
                () -> collapse(category, !core.getBoolean("menu", prefix(category) + "-collapsed")));
        if (view <= 0) return;
        Area viewport = new Area(x + 3, y + HEADER_H, PANEL_W - 6, view);
        draw.enableScissor(viewport.x(), viewport.y(), viewport.right(), viewport.bottom());
        int rowY = y + HEADER_H + 2 - (int) Math.round(offset);
        for (ModuleDefinition module : modules) {
            Area row = new Area(x + 4, rowY, PANEL_W - 8, ROW_H - 2);
            if (row.bottom() > viewport.y() && row.y() < viewport.bottom()) {
                boolean on = core.isEnabled(module.id()), open = module.id().equals(selected);
                boolean over = viewport.contains(mx, my) && row.contains(mx, my);
                double lit = ease("click-row:" + module.id(), on ? 1 : 0);
                if (lit > .01) GuiDraw.rounded(draw, row.x(), row.y(), row.w(), row.h(), 4, Palette.alpha(accent, (int) (82 * lit)));
                if (over) GuiDraw.rounded(draw, row.x(), row.y(), row.w(), row.h(), 4, Palette.alpha(Palette.TEXT, 16));
                if (open) GuiDraw.roundedBorder(draw, row.x(), row.y(), row.w(), row.h(), 4, Palette.alpha(accent, 210));
                label(draw, GuiDraw.truncate(textRenderer, module.name(), row.w() - 16, .84f),
                        row.x() + 5, row.y() + 3, .84f, on ? Palette.TEXT : Palette.MUTED);
                if (!module.settings().isEmpty()) label(draw, open ? "‹" : "›", row.right() - 8, row.y() + 2, .9f, open ? accent : Palette.MUTED);
                if (over) hovered = module.id();
                clippedControl("module:" + module.id(), row, viewport, 0, () -> { core.toggle(module.id()); changed(); });
                clippedControl("inspect:" + module.id(), row, viewport, 1, () -> select(module));
            }
            rowY += ROW_H;
        }
        draw.disableScissor();
        if (max > 0) {
            int thumb = Math.max(12, (int) (view * view / (double) content));
            int thumbY = viewport.y() + (int) Math.round((view - thumb) * offset / max);
            GuiDraw.rounded(draw, x + PANEL_W - 4, thumbY, 2, thumb, 1, Palette.alpha(accent, 170));
        }
    }

    private void drawFlyout(DrawContext draw) {
        ModuleDefinition module = core.modules().stream().filter(item -> item.id().equals(selected)).findFirst().orElse(null);
        if (module == null) { selected = null; return; }
        Area panel = bounds.get(module.category());
        int anchorX = panel != null ? panel.x() : panelX(module.category());
        int anchorY = panel != null ? panel.y() : panelY(module.category());
        int fx = anchorX + PANEL_W + 6;
        if (fx + FLYOUT_W > width - MARGIN) fx = anchorX - FLYOUT_W - 6;
        fx = Math.clamp(fx, MARGIN, Math.max(MARGIN, width - FLYOUT_W - MARGIN));
        List<String> description = GuiDraw.wrap(textRenderer, module.description(), FLYOUT_W - 24, .78f);
        int intro = 28 + description.size() * 10 + 6;
        int contentH = intro + settingsHeight(module, FLYOUT_W - 16) + 8;
        int fy = Math.clamp(anchorY, toolbarBottom, Math.max(toolbarBottom, height - FOOTER_H - 90));
        int h = Math.max(60, Math.min(contentH, height - FOOTER_H - fy));
        flyoutMax = Math.max(0, contentH - h);
        flyoutScroll = Math.clamp(flyoutScroll, 0, flyoutMax);
        flyout = new Area(fx, fy, FLYOUT_W, h);
        GuiDraw.shaderPanel(draw, fx, fy, FLYOUT_W, h, 10, accent, glass(), theme());
        Area viewport = new Area(fx + 2, fy + 2, FLYOUT_W - 4, h - 4);
        diagnostics.put("settings-scroll", new Point(fx + FLYOUT_W / 2.0, fy + h / 2.0));
        draw.enableScissor(viewport.x(), viewport.y(), viewport.right(), viewport.bottom());
        int y = fy + 8 - (int) Math.round(flyoutScroll);
        GuiDraw.heading(draw, textRenderer, GuiDraw.truncate(textRenderer, module.name(), FLYOUT_W - 92, .98f), fx + 10, y + 3, .98f, Palette.TEXT);
        boolean on = core.isEnabled(module.id());
        toggle(draw, fx + FLYOUT_W - 66, y, on);
        clippedControl("selected-toggle", new Area(fx + FLYOUT_W - 70, y - 2, 40, 22), viewport, 0, () -> { core.toggle(module.id()); changed(); });
        Area close = new Area(fx + FLYOUT_W - 24, y, 16, 16);
        label(draw, "×", close.x() + 4, close.y() + 3, 1f, close.contains(mx, my) ? accent : Palette.MUTED);
        clippedControl("flyout-close", close, viewport, 0, () -> selected = null);
        for (int line = 0; line < description.size(); line++)
            label(draw, description.get(line), fx + 10, y + 24 + line * 10, .78f, Palette.MUTED);
        settings(draw, module, fx + 8, y + intro, FLYOUT_W - 16, viewport);
        draw.disableScissor();
        if (flyoutMax > 0) {
            int thumb = Math.max(14, (int) (h * h / (double) contentH));
            int thumbY = fy + (int) Math.round((h - thumb) * flyoutScroll / flyoutMax);
            GuiDraw.rounded(draw, fx + FLYOUT_W - 4, thumbY, 2, thumb, 1, Palette.alpha(accent, 170));
        }
    }

    @Override protected void footer(DrawContext draw) {
        String message = failed ? status : hovered != null ? describe(hovered) : dirty ? "Saving…"
                : "Left-click toggles a module · Right-click opens its settings · Drag a header to move its panel";
        float w = Math.min(width - 16, GuiDraw.width(textRenderer, message, .78f) + 16);
        int x = Math.round((width - w) / 2), y = height - FOOTER_H + 4;
        GuiDraw.shaderCard(draw, x, y, Math.round(w), 14, 6, accent, glass(), theme(), 0, false);
        label(draw, GuiDraw.truncate(textRenderer, message, w - 12, .78f), x + 6, y + 3, .78f, failed ? Palette.ERROR : Palette.MUTED);
    }
    private String describe(String id) {
        return core.modules().stream().filter(module -> module.id().equals(id)).findFirst()
                .map(module -> module.name() + " · " + module.description()).orElse("");
    }

    private void select(ModuleDefinition module) {
        if (module.id().equals(selected)) { selected = null; return; }
        selected = module.id(); flyoutScroll = 0; capture = null; searchFocused = false;
        raise(module.category());
    }
    private void raise(Category category) { order.remove(category); order.add(category); }
    private void collapse(Category category, boolean collapsed) {
        core.setValue("menu", prefix(category) + "-collapsed", collapsed);
        raise(category);
        changed();
    }
    private void beginDrag(Category category, int x, int y) {
        dragging = category; moved = false;
        grabX = mx - x; grabY = my - y; pressX = mx; pressY = my;
        dragX = core.getNumber("menu", prefix(category) + "-x");
        dragY = core.getNumber("menu", prefix(category) + "-y");
        raise(category); searchFocused = false;
    }
    private void resetPanels() {
        for (Category category : CATEGORIES) {
            core.setValue("menu", prefix(category) + "-x", ClientModules.clickPanelX(category));
            core.setValue("menu", prefix(category) + "-y", 7.0);
            core.setValue("menu", prefix(category) + "-collapsed", false);
        }
        scroll.clear();
        changed();
    }
    private static String prefix(Category category) { return "click-" + category.name().toLowerCase(Locale.ROOT); }
    private int panelX(Category category) {
        double fraction = dragging == category ? dragX : core.getNumber("menu", prefix(category) + "-x");
        return (int) Math.round(MARGIN + Math.clamp(fraction, 0, 100) / 100 * Math.max(0, width - PANEL_W - 2 * MARGIN));
    }
    private int panelY(Category category) {
        double fraction = dragging == category ? dragY : core.getNumber("menu", prefix(category) + "-y");
        return (int) Math.round(MARGIN + Math.clamp(fraction, 0, 100) / 100 * Math.max(0, height - HEADER_H - FOOTER_H - MARGIN));
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        double lx = (x - ox) / scale, ly = (y - oy) / scale;
        boolean hit = controls.stream().anyMatch(control -> control.button() == button && control.area().contains(lx, ly));
        // Clicking empty space closes the settings flyout; panels and the flyout keep it open.
        if (!hit && selected != null && (flyout == null || !flyout.contains(lx, ly))) selected = null;
        return super.mouseClicked(x, y, button);
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (dragging != null && button == 0) {
            mx = (x - ox) / scale; my = (y - oy) / scale;
            if (Math.hypot(mx - pressX, my - pressY) > 2) moved = true;
            double freeWidth = Math.max(1, width - PANEL_W - 2 * MARGIN), freeHeight = Math.max(1, height - HEADER_H - FOOTER_H - MARGIN);
            dragX = Math.clamp((mx - grabX - MARGIN) / freeWidth * 100, 0, 100);
            dragY = Math.clamp((my - grabY - MARGIN) / freeHeight * 100, 0, 100);
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (dragging != null && button == 0) {
            Category category = dragging;
            dragging = null;
            if (moved) {
                core.setValue("menu", prefix(category) + "-x", Math.round(dragX * 10) / 10.0);
                core.setValue("menu", prefix(category) + "-y", Math.round(dragY * 10) / 10.0);
                changed();
            } else collapse(category, !core.getBoolean("menu", prefix(category) + "-collapsed"));
        }
        return super.mouseReleased(x, y, button);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        double lx = (x - ox) / scale, ly = (y - oy) / scale;
        if (flyout != null && flyout.contains(lx, ly)) {
            flyoutScroll = Math.clamp(flyoutScroll - vertical * 24, 0, flyoutMax);
            return true;
        }
        for (int i = order.size() - 1; i >= 0; i--) {
            Category category = order.get(i);
            Area box = bounds.get(category);
            if (box != null && box.contains(lx, ly)) {
                scroll.put(category, scroll.getOrDefault(category, 0.0) - vertical * ROW_H * 2);
                return true;
            }
        }
        return true;
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (capture == null && !searchFocused) {
            if (key == GLFW.GLFW_KEY_ESCAPE && selected != null) { selected = null; return true; }
            if (key == GLFW.GLFW_KEY_SPACE && selected != null && focusedControl == null) { core.toggle(selected); changed(); return true; }
        }
        return super.keyPressed(key, scan, modifiers);
    }
}
