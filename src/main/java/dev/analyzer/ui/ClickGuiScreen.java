package dev.analyzer.ui;

import dev.analyzer.core.AnalyzerCore;
import dev.analyzer.core.Category;
import dev.analyzer.core.ModuleDefinition;
import dev.analyzer.effects.GpuEffects;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Shader category columns with independent settings scroll and a reachable wide board. */
public final class ClickGuiScreen extends CatalogScreen {
    private static final Area BOARD = new Area(38, 128, 924, 406);
    private static final int COLUMN_W = 220, PITCH = 236, ROW = 32;
    private static final int HORIZONTAL_TRAVEL = 64, VERTICAL_TRAVEL = 64;
    private static final List<Category> CATEGORIES = List.of(Category.COMBAT, Category.HVH, Category.MOVEMENT,
            Category.VISUAL, Category.COSMETICS, Category.WORLD, Category.PLAYER, Category.HUD, Category.CLIENT);
    private static final int CONTENT_W = CATEGORIES.size() * PITCH - (PITCH - COLUMN_W);
    private static final double MAX_PAN = CONTENT_W - BOARD.w() + HORIZONTAL_TRAVEL;
    private final List<Column> columns = new ArrayList<>();
    private Column dragged;
    private String selected;
    private Category active = Category.COMBAT;
    private double pan, grabX, grabY;

    public ClickGuiScreen(AnalyzerCore core) {
        super(core, "Analyzer / Click GUI");
        for (int i = 0; i < CATEGORIES.size(); i++) columns.add(new Column(CATEGORIES.get(i), i));
    }
    @Override protected String layout() { return "Click GUI"; }
    @Override protected boolean shaderStyle() { return true; }
    @Override protected float canvasScale() {
        double gui = client.getWindow().getScaleFactor();
        double physical = Math.min(1.5, Math.min(client.getWindow().getFramebufferWidth() * .95 / W,
                client.getWindow().getFramebufferHeight() * .94 / H));
        return (float) Math.max(.12, physical / gui);
    }
    @Override protected void backdrop(DrawContext draw) {
        if (core.getChoice("menu", "material").equals("Glass")) GpuEffects.drawBackdrop(draw);
        else draw.fill(0, 0, width, height, 0x18000000);
    }
    @Override public String selectedModuleId() { return selected; }
    @Override protected void searchChanged() {
        for (Column column : columns) column.scroll = 0;
        if (!query.isBlank()) matching().stream().findFirst().ifPresent(module -> reveal(module.category()));
    }
    @Override public ClickGuiScreen inspect(String id) {
        core.modules().stream().filter(module -> module.id().equals(id)).findFirst().ifPresent(module -> {
            selected = id;
            query = "";
            searchFocused = false;
            for (Column column : columns) if (column.category == module.category()) {
                column.expanded = id;
                column.scroll = core.modules().stream().filter(item -> item.category() == column.category)
                        .toList().indexOf(module) * ROW;
                core.setValue("menu", prefix(column.category) + "-collapsed", false);
            }
            reveal(module.category());
        });
        return this;
    }
    private void reveal(Category category) {
        active = category;
        int slot = CATEGORIES.indexOf(category);
        double x = slot * PITCH + xOffset(category);
        pan = Math.clamp(x + COLUMN_W / 2.0 - BOARD.w() / 2.0, 0, MAX_PAN);
    }
    @Override protected void header(DrawContext draw) {
        BrandAssets.wordmark(draw, 37, 9, 146);
        label(draw, "Click GUI", 188, 26, 1.04f, Palette.TEXT);
        label(draw, "Category columns", 188, 45, .83f, Palette.MUTED);
        GuiDraw.shaderCard(draw, 312, 24, 258, 33, 12, accent, glass(), theme(), searchFocused ? 1 : 0, searchFocused);
        label(draw, query.isEmpty() ? "Search modules" : GuiDraw.truncate(textRenderer, query, 211, .98f),
                324, 35, .98f, query.isEmpty() ? Palette.MUTED : Palette.TEXT);
        control("search", new Area(312, 24, 227, 33), 0, () -> { searchFocused = true; selectAll = false; capture = null; });
        button(draw, "search-clear", "×", 541, 28, 25, 25, false, () -> { query = ""; searchFocused = false; searchChanged(); });
        button(draw, "profiles", "Profiles", 584, 27, 73, 29, false, () -> client.setScreen(MenuScreens.profiles(core, this)));
        button(draw, "hud-editor", "Edit HUD", 664, 27, 84, 29, false, () -> client.setScreen(new HudEditorScreen(core, this)));
        button(draw, "automation", "Automation", 755, 27, 106, 29, false, () -> client.setScreen(MenuScreens.automation(core, this)));
        button(draw, "appearance", "Style", 868, 27, 53, 29, false, () -> inspect("hud"));
        button(draw, "close", "×", 928, 27, 31, 29, false, this::close);
        int x = 38;
        for (Category category : CATEGORIES) {
            int w = Math.round(GuiDraw.width(textRenderer, category.label(), .94f)) + 28;
            button(draw, "category:" + category.name(), category.label(), x, 83, w, 29, active == category,
                    () -> { reveal(category); searchFocused = false; capture = null; });
            x += w + 5;
        }
        label(draw, "Drag headers · right-click to collapse", 39, 64, .81f, Palette.MUTED);
    }
    @Override protected void button(DrawContext draw, String id, String value, int x, int y, int w, int h,
                                    boolean selected, Runnable action) {
        Area hit = new Area(x, y, w, h);
        GuiDraw.shaderCard(draw, x, y, w, h, Math.min(10, h / 2), accent, glass(), theme(), hit.contains(mx, my) ? 1 : 0, selected);
        label(draw, value, x + (w - GuiDraw.width(textRenderer, value, .94f)) / 2,
                y + (h - 11) / 2f, .94f, selected ? accent : Palette.TEXT);
        control(id, hit, 0, action);
    }
    @Override protected void panel(DrawContext draw, Area box, boolean selected) {
        GuiDraw.shaderPanel(draw, box.x(), box.y(), box.w(), box.h(), 15, accent, glass(), theme());
    }
    @Override protected void toggle(DrawContext draw, int x, int y, boolean enabled) {
        GuiDraw.shaderPill(draw, x, y, 30, 16, accent, ease("toggle:" + x + ":" + y, enabled ? 1 : 0));
    }
    @Override protected void body(DrawContext draw) {
        draw.enableScissor(BOARD.x(), BOARD.y(), BOARD.right(), BOARD.bottom());
        for (Column column : columns) drawColumn(draw, column);
        draw.disableScissor();
        Area rail = new Area(43, 545, 552, 5);
        GuiDraw.shaderCard(draw, rail.x(), rail.y(), rail.w(), rail.h(), 2, accent, false, "Minimal", 0, false);
        int thumb = Math.round(rail.w() * BOARD.w() / (float) (CONTENT_W + HORIZONTAL_TRAVEL));
        int thumbX = rail.x() + (int) Math.round((rail.w() - thumb) * pan / MAX_PAN);
        GuiDraw.shaderCard(draw, thumbX, rail.y() - 1, thumb, 7, 3, accent, glass(), theme(), 1, true);
        control("board-scroll", new Area(rail.x(), rail.y() - 4, rail.w(), 14), 0,
                () -> pan = Math.clamp((mx - rail.x() - thumb / 2.0) / (rail.w() - thumb) * MAX_PAN, 0, MAX_PAN));
    }
    private void drawColumn(DrawContext draw, Column column) {
        int x = BOARD.x() + column.slot * PITCH + (int) Math.round(xOffset(column.category)) - (int) Math.round(pan);
        int y = BOARD.y() + (int) Math.round(yOffset(column.category));
        boolean collapsed = core.getBoolean("menu", prefix(column.category) + "-collapsed");
        int height = collapsed ? 39 : BOARD.bottom() - y;
        Area box = new Area(x, y, COLUMN_W, height);
        column.bounds = box.intersect(BOARD);
        if (column.bounds.w() <= 0) return;
        panel(draw, box, dragged == column);
        List<ModuleDefinition> modules = matching().stream().filter(module -> module.category() == column.category).toList();
        long enabled = modules.stream().filter(module -> core.isEnabled(module.id())).count();
        GuiDraw.heading(draw, textRenderer, column.category.label(), x + 12, y + 13, 1.02f, Palette.TEXT);
        label(draw, enabled + "/" + modules.size(), x + COLUMN_W - 66, y + 14, .82f, Palette.MUTED);
        Area heading = new Area(x, y, COLUMN_W, 39);
        clippedControl("header:" + column.category.name(), heading, BOARD, 0, () -> {
            dragged = column;
            active = column.category;
            grabX = mx - x;
            grabY = my - y;
            columns.remove(column);
            columns.add(column);
            searchFocused = false;
        });
        clippedControl("collapse:" + column.category.name(), heading, BOARD, 1, () -> collapse(column, !collapsed));
        Area collapse = new Area(x + COLUMN_W - 28, y + 9, 21, 21);
        label(draw, collapsed ? "+" : "−", collapse.x() + 5, collapse.y() + 4, 1.0f, Palette.TEXT);
        clippedControl("collapse:" + column.category.name(), collapse, BOARD, 0, () -> collapse(column, !collapsed));
        if (collapsed) { column.maxScroll = 0; return; }
        Area viewport = new Area(x + 5, y + 43, COLUMN_W - 10, Math.max(0, height - 49)).intersect(BOARD);
        ModuleDefinition expanded = modules.stream().filter(module -> module.id().equals(column.expanded)).findFirst().orElse(null);
        int extra = expanded == null ? 0 : settingsHeight(expanded, COLUMN_W - 16) + 8;
        int content = modules.size() * ROW + extra;
        column.maxScroll = Math.max(0, content - viewport.h());
        column.scroll = Math.clamp(column.scroll, 0, column.maxScroll);
        if (selected != null && selected.equals(column.expanded))
            diagnostics.put("settings-scroll", new Point(viewport.x() + viewport.w() / 2.0, viewport.y() + viewport.h() / 2.0));
        draw.enableScissor(viewport.x(), viewport.y(), viewport.right(), viewport.bottom());
        int rowY = y + 43 - (int) Math.round(column.scroll);
        if (modules.isEmpty()) label(draw, "No matching modules", x + 12, rowY + 10, .90f, Palette.MUTED);
        for (ModuleDefinition module : modules) {
            boolean on = core.isEnabled(module.id()), open = module.id().equals(column.expanded);
            Area row = new Area(x + 8, rowY, COLUMN_W - 16, ROW - 4);
            if (row.bottom() >= viewport.y() && row.y() < viewport.bottom()) {
                GuiDraw.shaderCard(draw, row.x(), row.y(), row.w(), row.h(), 11, accent, glass(), theme(), row.contains(mx, my) ? 1 : 0, open || on);
                label(draw, GuiDraw.truncate(textRenderer, module.name(), row.w() - 49, .96f),
                        row.x() + 9, row.y() + 9, .96f, on ? Palette.TEXT : Palette.MUTED);
                toggle(draw, row.right() - 37, row.y() + 6, on);
                Runnable inspect = () -> {
                    column.expanded = open ? "" : module.id();
                    selected = module.id();
                    active = module.category();
                    capture = null;
                    searchFocused = false;
                };
                clippedControl("inspect-left:" + module.id(), row, viewport, 0, inspect);
                clippedControl("inspect:" + module.id(), row, viewport, 1, inspect);
                Area switchArea = new Area(row.right() - 40, row.y(), 40, row.h());
                clippedControl("module:" + module.id(), switchArea, viewport, 0, () -> { core.toggle(module.id()); changed(); });
                if (module.id().equals(selected)) {
                    Area hit = switchArea.intersect(viewport);
                    if (hit.w() > 0 && hit.h() > 0) diagnostics.put("selected-toggle", new Point(hit.x() + hit.w() / 2.0, hit.y() + hit.h() / 2.0));
                }
            }
            rowY += ROW;
            if (open) {
                settings(draw, module, x + 8, rowY + 4, COLUMN_W - 16, viewport);
                rowY += extra;
            }
        }
        draw.disableScissor();
        if (column.maxScroll > 0 && viewport.h() > 0) {
            int thumb = Math.max(18, (int) (viewport.h() * viewport.h() / (double) Math.max(1, content)));
            int thumbY = viewport.y() + (int) Math.round((viewport.h() - thumb) * column.scroll / column.maxScroll);
            GuiDraw.shaderCard(draw, x + COLUMN_W - 4, thumbY, 2, thumb, 1, accent, glass(), theme(), 1, true);
        }
    }
    private void collapse(Column column, boolean collapsed) {
        core.setValue("menu", prefix(column.category) + "-collapsed", collapsed);
        active = column.category;
        changed();
    }
    @Override protected void footer(DrawContext draw) {
        String message = failed ? status : dirty ? "Saving…" : "Scroll a column for settings · Shift-scroll to move the board";
        label(draw, GuiDraw.truncate(textRenderer, message, 624, .81f), 43, 570, .81f, failed ? Palette.ERROR : Palette.MUTED);
        button(draw, "board-previous", "‹", 650, 557, 31, 29, false, () -> pan = Math.max(0, pan - PITCH * 3));
        button(draw, "board-next", "›", 686, 557, 31, 29, false, () -> pan = Math.min(MAX_PAN, pan + PITCH * 3));
        button(draw, "reset-columns", "Reset columns", 724, 557, 127, 29, false, this::resetColumns);
        button(draw, "layout-studio", "Studio", 858, 557, 104, 29, false, () -> switchLayout("Studio"));
    }
    private void resetColumns() {
        for (Column column : columns) {
            core.setValue("menu", prefix(column.category) + "-x", defaultX(column.category));
            core.setValue("menu", prefix(column.category) + "-y", defaultY(column.category));
            core.setValue("menu", prefix(column.category) + "-collapsed", false);
            column.scroll = 0;
        }
        pan = 0;
        changed();
    }
    /** Retain existing placement fields while aligning their old defaults to the new wide board. */
    private static String prefix(Category category) { return "classic-" + category.name().toLowerCase(Locale.ROOT); }
    private static double defaultX(Category category) { return 2 + category.ordinal() % 5 * 24; }
    private static double defaultY(Category category) { return 25 + category.ordinal() / 5 * 70; }
    private double xOffset(Category category) {
        double value = core.getNumber("menu", prefix(category) + "-x"), origin = defaultX(category);
        double offset = (value < origin ? (value / origin - 1) : (value - origin) / (100 - origin)) * HORIZONTAL_TRAVEL;
        return Math.max(-CATEGORIES.indexOf(category) * PITCH, offset);
    }
    private double yOffset(Category category) {
        double origin = defaultY(category);
        return Math.clamp((core.getNumber("menu", prefix(category) + "-y") - origin) / (100 - origin), 0, 1) * VERTICAL_TRAVEL;
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (dragged != null && button == 0) {
            mx = (x - ox) / scale;
            my = (y - oy) / scale;
            double offset = Math.clamp(mx - grabX - BOARD.x() - dragged.slot * PITCH + pan,
                    Math.max(-HORIZONTAL_TRAVEL, -dragged.slot * PITCH), HORIZONTAL_TRAVEL);
            double originX = defaultX(dragged.category);
            double savedX = offset < 0 ? originX * (1 + offset / HORIZONTAL_TRAVEL)
                    : originX + (100 - originX) * offset / HORIZONTAL_TRAVEL;
            double vertical = Math.clamp(my - grabY - BOARD.y(), 0, VERTICAL_TRAVEL);
            double originY = defaultY(dragged.category);
            core.setValue("menu", prefix(dragged.category) + "-x", savedX);
            core.setValue("menu", prefix(dragged.category) + "-y", originY + (100 - originY) * vertical / VERTICAL_TRAVEL);
            changed();
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        dragged = null;
        return super.mouseReleased(x, y, button);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        double lx = (x - ox) / scale, ly = (y - oy) / scale;
        if (horizontal != 0 || hasShiftDown()) {
            pan = Math.clamp(pan - (horizontal != 0 ? horizontal : vertical) * PITCH * .40, 0, MAX_PAN);
            return true;
        }
        for (int i = columns.size() - 1; i >= 0; i--) {
            Column column = columns.get(i);
            if (column.bounds != null && column.bounds.contains(lx, ly)) {
                column.scroll = Math.clamp(column.scroll - vertical * 32, 0, column.maxScroll);
                active = column.category;
                return true;
            }
        }
        if (BOARD.contains(lx, ly) || new Area(38, 540, 564, 16).contains(lx, ly))
            pan = Math.clamp(pan - vertical * PITCH * .40, 0, MAX_PAN);
        return true;
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (capture == null && !searchFocused && focusedControl == null) {
            if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT) {
                pan = Math.clamp(pan + (key == GLFW.GLFW_KEY_LEFT ? -PITCH : PITCH), 0, MAX_PAN);
                return true;
            }
            if (key == GLFW.GLFW_KEY_SPACE && selected != null) {
                core.toggle(selected);
                changed();
                return true;
            }
        }
        return super.keyPressed(key, scan, modifiers);
    }
    private static final class Column {
        final Category category;
        final int slot;
        String expanded = "";
        double scroll, maxScroll;
        Area bounds;
        Column(Category category, int slot) { this.category = category; this.slot = slot; }
    }
}
