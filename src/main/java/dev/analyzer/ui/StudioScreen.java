package dev.analyzer.ui;

import dev.analyzer.core.AnalyzerCore;
import dev.analyzer.core.Category;
import dev.analyzer.core.ModuleDefinition;
import dev.analyzer.core.Setting;
import dev.analyzer.effects.GpuEffects;
import dev.analyzer.effects.UiShader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A centered shader-drawn menu with category tabs and settings inside each card. */
public final class StudioScreen extends CatalogScreen {
    private static final Area CATALOG = new Area(38, 151, 924, 400);
    private static final int CARD_WIDTH = 296;
    private static final List<Category> CATEGORIES = List.of(Category.COMBAT, Category.HVH, Category.MOVEMENT,
            Category.VISUAL, Category.COSMETICS, Category.WORLD, Category.PLAYER, Category.HUD, Category.CLIENT);
    private static final int WHITE = 0xFFF7F5F8, SECONDARY = 0xFFBBB7C0;
    private final Set<String> expanded = new HashSet<>();
    private final Map<String, Double> innerScroll = new HashMap<>();
    private final List<CardViewport> cardViewports = new ArrayList<>();
    private String category = Category.COMBAT.name();
    private ModuleDefinition selected;
    private String revealModule;
    private double scroll, maxScroll;

    public StudioScreen(AnalyzerCore core) {
        super(core, "Analyzer / Studio");
        selected = core.modules().stream().filter(m -> m.category() == Category.COMBAT).findFirst().orElse(core.modules().getFirst());
        expanded.add(selected.id());
    }
    @Override protected String layout() { return "Studio"; }
    @Override protected float canvasScale() {
        double gui = client.getWindow().getScaleFactor();
        double physical = Math.min(1.10, Math.min(client.getWindow().getFramebufferWidth() * .72 / W,
                client.getWindow().getFramebufferHeight() * .84 / H));
        return (float) Math.max(.12, physical / gui);
    }
    @Override protected void backdrop(DrawContext draw) {
        if (client.world == null) {
            GpuEffects.drawBackdrop(draw);
            if (!core.getChoice("menu", "material").equals("Glass")) draw.fill(0, 0, width, height, 0x18000000);
        } else if (core.getChoice("menu", "material").equals("Glass")) GpuEffects.drawBackdrop(draw);
        else draw.fill(0, 0, width, height, 0x18000000);
    }
    @Override public String selectedModuleId() { return selected == null ? null : selected.id(); }
    @Override public List<String> visibleModuleIds() { return visible().stream().map(ModuleDefinition::id).toList(); }
    @Override protected void searchChanged() { scroll = 0; }
    private List<ModuleDefinition> visible() {
        return matching().stream().filter(m -> !query.isBlank() || category.equals("ALL") || m.category().name().equals(category)).toList();
    }
    @Override public StudioScreen inspect(String id) {
        core.modules().stream().filter(m -> m.id().equals(id)).findFirst().ifPresent(m -> {
            selected = m; category = m.category().name(); query = ""; scroll = 0;
            expanded.add(id); innerScroll.put(id, 0D);
            revealModule = id;
        });
        return this;
    }
    @Override protected void header(DrawContext draw) {
        BrandAssets.wordmark(draw, 398, 4, 204);
        label(draw, net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("analyzer").map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("dev"), 43, 35, .96f, SECONDARY);
        button(draw, "close", "×", 927, 27, 30, 29, false, this::close);
        List<String> labels = new ArrayList<>(); labels.add("All");
        for (Category item : CATEGORIES) labels.add(item.label());
        float total = 0;
        for (String value : labels) total += GuiDraw.width(textRenderer, value, 1.10f) + 25;
        int x = Math.round((W - total) / 2);
        for (int i = 0; i < labels.size(); i++) {
            String id = i == 0 ? "ALL" : CATEGORIES.get(i - 1).name();
            String value = labels.get(i); int w = Math.round(GuiDraw.width(textRenderer, value, 1.10f)) + 25;
            Area hit = new Area(x, 76, w, 28); boolean active = category.equals(id) && query.isBlank();
            label(draw, value, x + 12, 84, 1.10f, active ? accent : WHITE);
            if (active) surface(draw, x + 12, 106, w - 25, 2, 1, accent, accent, 0, 0, Palette.alpha(accent, 45), 3);
            control("category:" + id, hit, 0, () -> { category = id; query = ""; scroll = 0; searchFocused = false; capture = null; });
            x += w;
        }
        GuiDraw.shaderCard(draw, 42, 115, 327, 28, 9, accent, false, theme(), searchFocused ? 1 : 0, false);
        label(draw, query.isEmpty() ? "Search modules" : GuiDraw.truncate(textRenderer, query, 279, .94f), 53, 123, .94f,
                query.isEmpty() ? SECONDARY : WHITE);
        control("search", new Area(42, 115, 292, 28), 0, () -> {searchFocused = true; selectAll = false; capture = null;});
        button(draw, "search-clear", "×", 337, 117, 26, 24, false, () -> { query = ""; searchFocused = false; searchChanged(); });
        button(draw, "profiles", "Profiles", 396, 116, 91, 27, false, () -> client.setScreen(MenuScreens.profiles(core, this)));
        button(draw, "hud-editor", "Edit HUD", 494, 116, 105, 27, false, () -> client.setScreen(new HudEditorScreen(core, this)));
        button(draw, "automation", "Automation", 606, 116, 129, 27, false, () -> client.setScreen(MenuScreens.automation(core, this)));
        button(draw, "appearance", "Appearance", 742, 116, 130, 27, false, () -> inspect("hud"));
        label(draw, visible().size() + " modules", 888, 124, .8f, SECONDARY);
    }
    @Override protected void button(DrawContext draw, String id, String value, int x, int y, int w, int h, boolean selected, Runnable action) {
        Area hit = new Area(x, y, w, h); boolean hovered = hit.contains(mx, my);
        if (hovered || selected) GuiDraw.shaderCard(draw, x, y, w, h, 8, accent, false, theme(), hovered ? 1 : 0, false);
        label(draw, value, x + (w - GuiDraw.width(textRenderer, value, .96f)) / 2, y + (h - 11) / 2f, .96f, selected ? accent : SECONDARY);
        control(id, hit, 0, action);
    }
    @Override protected void footer(DrawContext draw) {
        String text = failed ? status : dirty ? "Saving…" : "Click a card title for settings";
        label(draw, GuiDraw.truncate(textRenderer, text, 620, .84f), 43, 570, .84f, failed ? Palette.ERROR : SECONDARY);
        label(draw, "Esc to close", 869, 570, .84f, SECONDARY);
    }
    @Override protected void body(DrawContext draw) {
        List<ModuleDefinition> modules = visible();
        int[] heights = new int[]{0, 0, 0};
        List<Card> cards = new ArrayList<>();
        for (ModuleDefinition module : modules) {
            int column = 0; for (int i = 1; i < 3; i++) if (heights[i] < heights[column]) column = i;
            int body = expanded.contains(module.id()) ? Math.min(270, studioSettingsHeight(module)) : 0;
            int height = 88 + body;
            cards.add(new Card(module, 40 + column * 312, 154 + heights[column], height, body));
            heights[column] += height + 12;
        }
        maxScroll = Math.max(0, Math.max(heights[0], Math.max(heights[1], heights[2])) - CATALOG.h());
        if (revealModule != null) {
            for (Card card : cards) if (card.module.id().equals(revealModule)) {
                scroll = Math.clamp(card.y - CATALOG.y() - 4D, 0, maxScroll);
                break;
            }
            revealModule = null;
        }
        scroll = Math.clamp(scroll, 0, maxScroll); cardViewports.clear();
        draw.enableScissor(CATALOG.x(), CATALOG.y(), CATALOG.right(), CATALOG.bottom());
        if (cards.isEmpty()) { label(draw, "No modules found", 391, 278, 1.4f, WHITE); label(draw, "Try another search.", 418, 309, 1.0f, SECONDARY); }
        for (Card card : cards) {
            int y = card.y - (int) scroll;
            if (y + card.height < CATALOG.y() || y >= CATALOG.bottom()) continue;
            ModuleDefinition module = card.module; String id = module.id(); boolean open = expanded.contains(id), on = core.isEnabled(id);
            Area area = new Area(card.x, y, CARD_WIDTH, card.height);
            GuiDraw.shaderCard(draw, card.x, y, CARD_WIDTH, card.height, 19, accent, glass(), theme(), area.contains(mx, my) ? 1 : 0, false);
            GuiDraw.heading(draw, textRenderer, GuiDraw.truncate(textRenderer, module.name(), 211, 1.48f), card.x + 17, y + 17, 1.48f, WHITE);
            GuiDraw.shaderPill(draw, card.x + CARD_WIDTH - 62, y + 17, 45, 21, accent, ease("module:" + id, on ? 1 : 0));
            List<String> desc = GuiDraw.wrap(textRenderer, module.description(), CARD_WIDTH - 34, .95f);
            for (int line = 0; line < Math.min(2, desc.size()); line++) label(draw, desc.get(line), card.x + 17, y + 47 + line * 13, .95f, SECONDARY);
            clippedControl("inspect:" + id, new Area(card.x + 3, y + 3, CARD_WIDTH - 6, 80), CATALOG, 0, () -> {
                selected = module; if (open) expanded.remove(id); else expanded.add(id); searchFocused = false; capture = null;
            });
            clippedControl("module:" + id, new Area(card.x + CARD_WIDTH - 67, y + 10, 57, 33), CATALOG, 0, () -> {core.toggle(id); changed();});
            if (module == selected) {
                Point point = diagnostics.get("module:" + id); if (point != null) diagnostics.put("selected-toggle", point);
            }
            if (open) {
                draw.fill(card.x + 16, y + 84, card.x + CARD_WIDTH - 16, y + 85, 0x1CFFFFFF);
                Area viewport = new Area(card.x + 8, y + 90, CARD_WIDTH - 16, Math.max(0, card.body - 8)).intersect(CATALOG);
                int fullHeight = studioSettingsHeight(module), maximum = Math.max(0, fullHeight - Math.max(1, card.body - 8));
                double offset = Math.clamp(innerScroll.getOrDefault(id, 0D), 0, maximum); innerScroll.put(id, offset);
                cardViewports.add(new CardViewport(module, viewport, maximum));
                if (module == selected && viewport.h() > 0) diagnostics.put("settings-scroll", new Point(viewport.x() + viewport.w() / 2D, viewport.y() + viewport.h() / 2D));
                if (viewport.h() > 0) {
                    draw.enableScissor(viewport.x(), viewport.y(), viewport.right(), viewport.bottom());
                    studioSettings(draw, module, card.x + 8, y + 92 - (int) offset, CARD_WIDTH - 16, viewport);
                    draw.disableScissor();
                    if (maximum > 0) {
                        int thumb = Math.max(20, (int) (viewport.h() * (double) viewport.h() / fullHeight));
                        int ty = viewport.y() + (int) ((viewport.h() - thumb) * offset / maximum);
                        surface(draw, card.x + CARD_WIDTH - 6, ty, 2, thumb, 1, 0x805E5963, 0x805E5963, 0, 0, 0, 0);
                    }
                }
            }
        }
        draw.disableScissor();
        if (maxScroll > 0) {
            int thumb = Math.max(28, (int) (CATALOG.h() * CATALOG.h() / (CATALOG.h() + maxScroll)));
            surface(draw, 969, CATALOG.y() + (int) ((CATALOG.h() - thumb) * scroll / maxScroll), 3, thumb, 1.5f, 0xFF57525C, 0xFF57525C, 0, 0, 0, 0);
        }
    }
    private int studioRowHeight(Setting<?> setting) {
        int lines = GuiDraw.wrap(textRenderer, setting.label(), 238, .94f).size();
        return Math.max(1, lines) * 13 + (setting.kind() == Setting.Kind.BOOLEAN ? 27 : 36);
    }
    private int studioSettingsHeight(ModuleDefinition module) {
        int height = 56; String last = null;
        for (Setting<?> setting : SettingSections.ordered(core, module)) {
            String group = SettingSections.group(module, setting);
            if (!group.equals(last) && !group.isEmpty()) height += 26;
            height += studioRowHeight(setting); last = group;
        }
        return height;
    }
    private void studioSettings(DrawContext draw, ModuleDefinition module, int x, int y, int width, Area clip) {
        String mid = module.id(), last = null;
        for (Setting<?> setting : SettingSections.ordered(core, module)) {
            String group = SettingSections.group(module, setting);
            if (!group.equals(last) && !group.isEmpty()) { label(draw, group, x + 9, y + 5, .86f, SECONDARY); y += 26; }
            last = group;
            List<String> lines = GuiDraw.wrap(textRenderer, setting.label(), width - 42, .94f);
            int height = studioRowHeight(setting), controlY = y + lines.size() * 13 + 2;
            if (y + height >= clip.y() && y < clip.bottom()) {
                for (int i = 0; i < lines.size(); i++) label(draw, lines.get(i), x + 9, y + i * 13 + 3, .94f, WHITE);
                switch (setting.kind()) {
                    case BOOLEAN -> {
                        boolean value = core.getBoolean(mid, setting.id());
                        GuiDraw.shaderPill(draw, x + width - 50, controlY, 36, 17, accent, value);
                        label(draw, value ? "On" : "Off", x + 9, controlY + 4, .84f, SECONDARY);
                        clippedControl("setting:" + mid + ":" + setting.id(), new Area(x + 6, y, width - 12, height - 3), clip, 0,
                                () -> {core.setValue(mid, setting.id(), !core.getBoolean(mid, setting.id())); changed();});
                    }
                    case CHOICE -> {
                        Area hit = new Area(x + 8, controlY, width - 18, 25);
                        GuiDraw.shaderCard(draw, hit.x(), hit.y(), hit.w(), hit.h(), 8, accent, false, "Classic", hit.contains(mx, my) ? 1 : 0, false);
                        label(draw, GuiDraw.truncate(textRenderer, core.getChoice(mid, setting.id()), hit.w() - 36, .94f), hit.x() + 9, hit.y() + 7, .94f, WHITE);
                        label(draw, "›", hit.right() - 15, hit.y() + 6, 1.1f, SECONDARY);
                        clippedControl("setting:" + mid + ":" + setting.id(), hit, clip, 0, () -> cycle(mid, setting, 1));
                        clippedControl("setting-back:" + mid + ":" + setting.id(), hit, clip, 1, () -> cycle(mid, setting, -1));
                    }
                    case NUMBER -> {
                        double value = core.getNumber(mid, setting.id());
                        Area track = new Area(x + 11, controlY + 11, width - 91, 4), exact = new Area(x + width - 69, controlY, 58, 25);
                        double amount = (value - setting.min()) / Math.max(.00001, setting.max() - setting.min());
                        surface(draw, track.x(), track.y(), track.w(), 4, 2, 0xFF505054, 0xFF505054, 0, 0, 0, 0);
                        surface(draw, track.x(), track.y(), (float) (track.w() * amount), 4, 2, accent, accent, 0, 0, Palette.alpha(accent, 40), 3);
                        surface(draw, track.x() + (float) (track.w() * amount) - 4, track.y() - 2, 8, 8, 4, WHITE, WHITE, 0, 0, 0, 0);
                        GuiDraw.shaderCard(draw, exact.x(), exact.y(), exact.w(), exact.h(), 7, accent, false, "Classic", 0, false);
                        label(draw, number(value), exact.x() + 8, exact.y() + 7, .9f, WHITE);
                        clippedControl("setting:" + mid + ":" + setting.id(), new Area(track.x() - 3, controlY, track.w() + 6, 25), clip, 0,
                                () -> {slider = new Slider(mid, setting, track); updateSlider(mx);});
                        clippedControl("number:" + mid + ":" + setting.id(), exact, clip, 0, () -> client.setScreen(new NumericSettingScreen(core, mid, setting, this)));
                        clippedControl("number-right:" + mid + ":" + setting.id(), new Area(x + 6, y, width - 12, height - 3), clip, 1,
                                () -> client.setScreen(new NumericSettingScreen(core, mid, setting, this)));
                    }
                }
            }
            y += height;
        }
        label(draw, "Keybind", x + 9, y + 4, .88f, SECONDARY);
        Area hit = new Area(x + 8, y + 21, width - 18, 25);
        GuiDraw.shaderCard(draw, hit.x(), hit.y(), hit.w(), hit.h(), 8, accent, false, "Classic", 0, false);
        String binding = mid.equals(capture) ? "Press a key…" : core.keybind(mid) < 0 ? "Unbound" : InputUtil.fromKeyCode(core.keybind(mid), 0).getLocalizedText().getString();
        label(draw, binding, hit.x() + 9, hit.y() + 7, .92f, WHITE);
        clippedControl("keybind:" + mid, hit, clip, 0, () -> {capture = mid; searchFocused = false;});
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        double lx = (x - ox) / scale, ly = (y - oy) / scale;
        for (CardViewport viewport : cardViewports) if (viewport.area.contains(lx, ly)) {
            double previous = innerScroll.getOrDefault(viewport.module.id(), 0D);
            double next = Math.clamp(previous - vertical * 32, 0, viewport.maximum);
            if (next != previous) {
                selected = viewport.module; innerScroll.put(selected.id(), next); return true;
            }
        }
        if (CATALOG.contains(lx, ly)) scroll = Math.clamp(scroll - vertical * 34, 0, maxScroll);
        return true;
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (capture == null && !searchFocused && focusedControl == null && key == GLFW.GLFW_KEY_SPACE && selected != null) {
            core.toggle(selected.id()); changed(); return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }
    private static void surface(DrawContext draw, float x, float y, float w, float h, float radius,
                                int top, int bottom, int shadow, float shadowBlur, int glow, float glowBlur) {
        if (!UiShader.surface(draw, x, y, w, h, radius, top, bottom, shadow, shadowBlur, glow, glowBlur))
            GuiDraw.rounded(draw, Math.round(x), Math.round(y), Math.round(w), Math.round(h), Math.round(radius), top);
    }
    private record Card(ModuleDefinition module, int x, int y, int height, int body) { }
    private record CardViewport(ModuleDefinition module, Area area, int maximum) { }
}
