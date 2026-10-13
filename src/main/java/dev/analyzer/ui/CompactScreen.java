package dev.analyzer.ui;

import dev.analyzer.core.AnalyzerCore;
import dev.analyzer.core.Category;
import dev.analyzer.core.ModuleDefinition;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** Dense dashboard: three-column catalog, flat category strip and an independent inspector. */
public final class CompactScreen extends CatalogScreen {
    private String category = "ALL";
    private ModuleDefinition selected;
    private double scroll, settingScroll;
    private double maxScroll, maxSettingScroll;
    private static final Area CATALOG = new Area(23, 167, 596, 385);
    private static final Area INSPECTOR = new Area(653, 236, 315, 306);

    public CompactScreen(AnalyzerCore core) { super(core, "Analyzer / Compact"); selected = core.modules().getFirst(); }
    @Override protected String layout() { return "Compact"; }
    @Override public CompactScreen inspect(String id) {
        core.modules().stream().filter(module -> module.id().equals(id)).findFirst().ifPresent(module -> { selected = module; category = "ALL"; query = ""; scroll = Math.max(0, core.modules().indexOf(module) / 3 * 65 - 60); settingScroll = 0; });
        return this;
    }
    @Override public String selectedModuleId() { return selected == null ? null : selected.id(); }
    private List<ModuleDefinition> visible() { return matching().stream().filter(module -> category.equals("ALL") || module.category().name().equals(category)).toList(); }
    @Override protected void body(DrawContext draw) {
        panel(draw, new Area(10, 88, 980, 474), false);
        int x = 24, y = 99;
        button(draw, "category:ALL", "All", x, y, 56, 25, category.equals("ALL"), () -> { category = "ALL"; scroll = 0; }); x += 64;
        for (Category entry : Category.values()) {
            int w = Math.max(63, (int) GuiDraw.width(textRenderer, entry.label(), 0.82f) + 22);
            if (x + w > 970) { x = 24; y += 28; }
            button(draw, "category:" + entry.name(), entry.label(), x, y, w, 25, category.equals(entry.name()), () -> { category = entry.name(); scroll = 0; }); x += w + 6;
        }
        List<ModuleDefinition> modules = visible();
        label(draw, modules.size() + " MATCHING MODULES", 26, 149, 0.73f, accent);
        maxScroll = Math.max(0, ((modules.size() + 2) / 3) * 65 - CATALOG.h());
        scroll = Math.clamp(scroll, 0, maxScroll);
        draw.enableScissor(CATALOG.x(), CATALOG.y(), CATALOG.right(), CATALOG.bottom());
        for (int i = 0; i < modules.size(); i++) {
            ModuleDefinition module = modules.get(i);
            int cx = 25 + i % 3 * 198, cy = 169 + i / 3 * 65 - (int) scroll;
            Area card = new Area(cx, cy, 190, 58);
            if (card.bottom() < CATALOG.y() || card.y() > CATALOG.bottom()) continue;
            boolean enabled = core.isEnabled(module.id()), active = selected == module;
            GuiDraw.card(draw, cx, cy, 190, 58, radius(), accent, glass(), theme(), card.contains(mx, my) ? 1 : 0, active);
            label(draw, GuiDraw.truncate(textRenderer, module.name(), 145, 0.96f), cx + 11, cy + 10, 0.96f, Palette.TEXT);
            label(draw, module.category().label().toUpperCase(java.util.Locale.ROOT), cx + 11, cy + 30, 0.62f, enabled ? accent : Palette.SUBTLE);
            toggle(draw, cx + 155, cy + 29, enabled);
            clippedControl("inspect-left:" + module.id(), card, CATALOG, 0, () -> { selected = module; settingScroll = 0; searchFocused = false; capture = null; });
            clippedControl("inspect:" + module.id(), card, CATALOG, 1, () -> { selected = module; settingScroll = 0; searchFocused = false; capture = null; });
            clippedControl("module:" + module.id(), new Area(cx + 151, cy + 25, 39, 27), CATALOG, 0, () -> { core.toggle(module.id()); changed(); });
        }
        draw.disableScissor();
        draw.fill(635, 140, 636, 544, Palette.BORDER);
        if (selected == null) return;
        label(draw, "INSPECT / " + selected.category().label().toUpperCase(java.util.Locale.ROOT), 653, 151, 0.7f, accent);
        GuiDraw.heading(draw, textRenderer, selected.name(), 653, 172, 1.5f, Palette.TEXT);
        List<String> description = GuiDraw.wrap(textRenderer, selected.description(), 308, 0.81f);
        for (int line = 0; line < Math.min(2, description.size()); line++) label(draw, description.get(line), 653, 200 + line * 13, 0.81f, Palette.MUTED);
        toggle(draw, 934, 175, core.isEnabled(selected.id()));
        control("selected-toggle", new Area(926, 166, 46, 32), 0, () -> { core.toggle(selected.id()); changed(); });
        maxSettingScroll = Math.max(0, settingsHeight(selected, INSPECTOR.w()) - INSPECTOR.h());
        settingScroll = Math.clamp(settingScroll, 0, maxSettingScroll);
        draw.enableScissor(INSPECTOR.x(), INSPECTOR.y(), INSPECTOR.right(), INSPECTOR.bottom());
        diagnostics.put("settings-scroll", new Point(INSPECTOR.x() + INSPECTOR.w() / 2.0, INSPECTOR.y() + INSPECTOR.h() / 2.0));
        settings(draw, selected, INSPECTOR.x(), INSPECTOR.y() - (int) settingScroll, INSPECTOR.w(), INSPECTOR);
        draw.disableScissor();
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        double lx = (x - ox) / scale, ly = (y - oy) / scale;
        if (INSPECTOR.contains(lx, ly)) settingScroll = Math.clamp(settingScroll - vertical * 29, 0, maxSettingScroll);
        else if (CATALOG.contains(lx, ly)) scroll = Math.clamp(scroll - vertical * 37, 0, maxScroll);
        return true;
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (!searchFocused && capture == null && focusedControl == null && selected != null && key == GLFW.GLFW_KEY_SPACE) { core.toggle(selected.id()); changed(); return true; }
        return super.keyPressed(key, scan, modifiers);
    }
}
