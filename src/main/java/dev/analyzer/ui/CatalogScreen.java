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

/** Shared controls, keyboard capture and persistence; layouts supply their own composition. */
abstract class CatalogScreen extends Screen {
    protected final AnalyzerCore core;
    protected static final int W = 1000, H = 600;
    protected final List<Control> controls = new ArrayList<>();
    protected final Map<String, Point> diagnostics = new HashMap<>();
    protected float scale = 1, ox, oy;
    protected double mx, my;
    protected int accent;
    protected String query = "", capture = null, status = "";
    protected boolean searchFocused, selectAll, failed, dirty;
    protected long changedAt;
    protected Slider slider;
    private final long opened = System.nanoTime();
    private long lastFrame = opened;
    private double frameSeconds = 0.016;
    private final Map<String, Double> animation = new HashMap<>();

    protected CatalogScreen(AnalyzerCore core, String title) { super(Text.literal(title)); this.core = core; }
    @Override public boolean shouldPause() { return false; }
    protected abstract String layout();
    protected abstract void body(DrawContext draw);
    @Override public void render(DrawContext draw, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        frameSeconds = Math.clamp((now - lastFrame) / 1e9, 0.001, 0.05); lastFrame = now;
        scale = Math.max(0.15f, Math.min(1.2f, Math.min((width - 16f) / W, (height - 16f) / H)));
        ox = (width - W * scale) / 2; oy = (height - H * scale) / 2;
        if (core.getNumber("menu", "motion") > 0) oy += (float) (Math.exp(-(now - opened) / 150_000_000.0) * 12 * scale);
        mx = (mouseX - ox) / scale; my = (mouseY - oy) / scale;
        accent = HudWidgets.accent(core); controls.clear(); diagnostics.clear();
        GpuEffects.drawBackdrop(draw);
        draw.getMatrices().push(); draw.getMatrices().translate(ox, oy, 0); draw.getMatrices().scale(scale, scale, 1);
        MenuScreens.particles(core, draw, W, H, (System.nanoTime() - opened) / 1e9, mx, my);
        header(draw); body(draw);
        label(draw, GuiDraw.truncate(textRenderer, failed ? status : status.isEmpty() ? "LEFT CLICK toggle · RIGHT CLICK settings · scroll each panel" : status, 748, 0.8f), 23, 579, 0.8f, failed ? Palette.ERROR : Palette.MUTED);
        label(draw, core.modules().size() + " modules / " + layout().toUpperCase(Locale.ROOT), 805, 579, 0.78f, accent);
        draw.getMatrices().pop();
    }
    private void header(DrawContext draw) {
        GuiDraw.outlined(draw, 10, 9, W - 20, 66, 12, Palette.alpha(accent, 90), 0xDE0A141E);
        GuiDraw.logo(draw, 25, 23, 28, accent);
        GuiDraw.heading(draw, textRenderer, "ANALYZER", 65, 20, 1.3f, Palette.TEXT);
        label(draw, layout().toUpperCase(Locale.ROOT) + " / YOUR WORKSPACE", 66, 43, 0.63f, Palette.MUTED);
        int x = 247;
        for (String name : List.of("Workspace", "Classic", "Compact")) {
            final String destination = name;
            button(draw, "layout-" + name.toLowerCase(Locale.ROOT), name, x, 20, 88, 25, name.equals(layout()), () -> {
                core.setValue("menu", "layout", destination); changed(); persist(); client.setScreen(MenuScreens.create(core));
            }); x += 94;
        }
        button(draw, "profiles", "Profiles", 540, 20, 72, 25, false, () -> client.setScreen(new AnalyzerScreen(core).showProfiles()));
        button(draw, "hud-editor", "Edit HUD", 620, 20, 79, 25, false, () -> client.setScreen(new HudEditorScreen(core, this)));
        GuiDraw.outlined(draw, 709, 20, 240, 25, 5, searchFocused ? accent : Palette.BORDER, Palette.SURFACE);
        label(draw, query.isEmpty() ? "Search all modules…  /" : GuiDraw.truncate(textRenderer, query, 217, 0.86f), 721, 28, 0.86f, query.isEmpty() ? Palette.MUTED : Palette.TEXT);
        control("search", new Area(709, 20, 240, 25), 0, () -> { searchFocused = true; selectAll = false; capture = null; });
        button(draw, "close", "×", 957, 20, 23, 25, false, this::close);
        button(draw, "automation", "Automation", 826, 49, 123, 20, false, () -> client.setScreen(MenuScreens.automation(core, this)));
        label(draw, "Drag category headers / every control is saved locally", 248, 53, 0.7f, Palette.SUBTLE);
    }
    protected List<ModuleDefinition> matching() {
        String filter = query.toLowerCase(Locale.ROOT).strip();
        return core.modules().stream().filter(module -> (module.id() + " " + module.name() + " " + module.description() + " " + module.category().label()).toLowerCase(Locale.ROOT).contains(filter)).toList();
    }
    protected boolean glass() { return core.getChoice("menu", "material").equals("Glass"); }
    protected void panel(DrawContext draw, Area box, boolean selected) {
        GuiDraw.outlined(draw, box.x, box.y, box.w, box.h, 9, Palette.alpha(selected ? accent : Palette.CYAN, selected ? 160 : 55), glass() ? 0xC90B1722 : Palette.BACKGROUND);
    }
    protected void label(DrawContext draw, String value, float x, float y, float size, int color) { GuiDraw.label(draw, textRenderer, value, x, y, size, color); }
    protected void button(DrawContext draw, String id, String value, int x, int y, int w, int h, boolean selected, Runnable action) {
        Area area = new Area(x, y, w, h);
        double hover = ease("button:" + id, area.contains(mx, my) ? 1 : 0);
        GuiDraw.rounded(draw, x, y, w, h, 5, selected ? Palette.alpha(accent, 75) : Palette.mix(Palette.CARD, Palette.CARD_HOVER, hover));
        label(draw, value, x + 9, y + 8, 0.82f, selected ? accent : Palette.TEXT);
        control(id, area, 0, action);
    }
    protected void control(String id, Area area, int button, Runnable action) {
        controls.add(new Control(id, area, button, action));
        diagnostics.put(id, new Point(area.x + area.w / 2.0, area.y + area.h / 2.0));
    }
    protected void clippedControl(String id, Area area, Area clip, int button, Runnable action) {
        Area intersection = area.intersect(clip);
        if (intersection.w > 0 && intersection.h > 0) control(id, intersection, button, action);
    }
    protected void toggle(DrawContext draw, int x, int y, boolean enabled) {
        double amount = ease("toggle:" + x + ":" + y, enabled ? 1 : 0);
        GuiDraw.rounded(draw, x, y, 26, 13, 6, Palette.mix(0xFF33444C, Palette.alpha(accent, 200), amount));
        GuiDraw.rounded(draw, x + 3 + (int) (12 * amount), y + 3, 7, 7, 3, Palette.TEXT);
    }
    protected double ease(String id, double goal) {
        double value = animation.getOrDefault(id, goal);
        if (core.getNumber("menu", "motion") == 0) value = goal;
        else value += (goal - value) * (1 - Math.exp(-frameSeconds * 15));
        if (animation.size() > 2048) animation.clear();
        animation.put(id, value); return value;
    }
    protected int settingsHeight(ModuleDefinition module) { return 45 + module.settings().stream().mapToInt(s -> s.kind() == Setting.Kind.NUMBER ? 39 : 31).sum(); }
    protected void settings(DrawContext draw, ModuleDefinition module, int x, int y, int width, Area viewport) {
        String id = module.id();
        for (Setting<?> setting : module.settings()) {
            int rowHeight = setting.kind() == Setting.Kind.NUMBER ? 39 : 31;
            if (y + rowHeight >= viewport.y && y < viewport.bottom()) {
                String title = GuiDraw.truncate(textRenderer, setting.label(), width - (setting.kind() == Setting.Kind.CHOICE ? 20 : 58), 0.76f);
                label(draw, title, x + 8, y + 5, 0.76f, Palette.MUTED);
                switch (setting.kind()) {
                    case BOOLEAN -> {
                        toggle(draw, x + width - 35, y + 4, core.getBoolean(id, setting.id()));
                        clippedControl("setting:" + id + ":" + setting.id(), new Area(x + 4, y + 2, width - 8, 23), viewport, 0, () -> { core.setValue(id, setting.id(), !core.getBoolean(id, setting.id())); changed(); });
                    }
                    case CHOICE -> {
                        String value = core.getChoice(id, setting.id());
                        label(draw, "‹ " + GuiDraw.truncate(textRenderer, value, width - 25, 0.8f) + " ›", x + 8, y + 17, 0.8f, accent);
                        Area hit = new Area(x + 4, y + 1, width - 8, 28);
                        clippedControl("setting:" + id + ":" + setting.id(), hit, viewport, 0, () -> cycle(id, setting, 1));
                        clippedControl("setting-back:" + id + ":" + setting.id(), hit, viewport, 1, () -> cycle(id, setting, -1));
                    }
                    case NUMBER -> {
                        double value = core.getNumber(id, setting.id());
                        label(draw, number(value), x + width - 43, y + 5, 0.7f, accent);
                        Area track = new Area(x + 9, y + 24, width - 18, 4);
                        draw.fill(track.x, track.y, track.right(), track.bottom(), Palette.BORDER);
                        double amount = (value - setting.min()) / Math.max(0.000001, setting.max() - setting.min());
                        draw.fill(track.x, track.y, track.x + (int) (track.w * amount), track.bottom(), accent);
                        GuiDraw.rounded(draw, track.x + (int) (track.w * amount) - 3, track.y - 2, 7, 8, 3, Palette.TEXT);
                        clippedControl("setting:" + id + ":" + setting.id(), new Area(track.x - 3, track.y - 8, track.w + 6, 20), viewport, 0, () -> { slider = new Slider(id, setting, track); updateSlider(mx); });
                    }
                }
            }
            y += rowHeight;
        }
        if (y + 31 >= viewport.y && y < viewport.bottom()) {
            String key = capture != null && capture.equals(id) ? "Press a key / Esc cancel" : core.keybind(id) < 0 ? "Unbound / click to bind" : InputUtil.fromKeyCode(core.keybind(id), 0).getLocalizedText().getString();
            label(draw, "KEYBIND", x + 8, y + 3, 0.65f, Palette.SUBTLE);
            label(draw, GuiDraw.truncate(textRenderer, key, width - 18, 0.8f), x + 8, y + 17, 0.8f, accent);
            clippedControl("keybind:" + id, new Area(x + 4, y, width - 8, 32), viewport, 0, () -> { capture = id; searchFocused = false; });
        }
    }
    private void cycle(String id, Setting<?> setting, int direction) {
        List<String> options = setting.choices();
        int next = Math.floorMod(options.indexOf(core.getChoice(id, setting.id())) + direction, options.size());
        core.setValue(id, setting.id(), options.get(next)); changed();
    }
    protected void changed() { dirty = true; failed = false; changedAt = System.currentTimeMillis(); }
    protected void persist() {
        try { core.save(); dirty = false; status = "All changes saved."; failed = false; }
        catch (IOException exception) { failed = true; status = "Save failed: " + exception.getMessage(); }
    }
    @Override public void tick() { if (dirty && !failed && System.currentTimeMillis() - changedAt > 650) persist(); }
    @Override public void removed() { if (dirty) persist(); }
    @Override public void close() { if (dirty) persist(); super.close(); }
    @Override public boolean mouseClicked(double x, double y, int button) {
        mx = (x - ox) / scale; my = (y - oy) / scale;
        for (int i = controls.size() - 1; i >= 0; i--) { Control control = controls.get(i); if (control.button == button && control.area.contains(mx, my)) { control.action.run(); return true; } }
        searchFocused = false; return true;
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        mx = (x - ox) / scale; my = (y - oy) / scale;
        if (button == 0 && slider != null) { updateSlider(mx); return true; } return false;
    }
    @Override public boolean mouseReleased(double x, double y, int button) { slider = null; return true; }
    protected void updateSlider(double x) {
        if (slider == null) return;
        double value = slider.setting.min() + Math.clamp((x - slider.track.x) / slider.track.w, 0, 1) * (slider.setting.max() - slider.setting.min());
        core.setValue(slider.module, slider.setting.id(), value); changed();
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (capture != null) {
            if (key != GLFW.GLFW_KEY_ESCAPE) { core.setKeybind(capture, key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_BACKSPACE ? -1 : key); changed(); }
            capture = null; return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE || (key == AnalyzerClient.getMenuKey() && !searchFocused)) {
            if (searchFocused) searchFocused = false; else close(); return true;
        }
        if (key == GLFW.GLFW_KEY_SLASH && !searchFocused) { searchFocused = true; return true; }
        if (key == GLFW.GLFW_KEY_TAB) { searchFocused = !searchFocused; return true; }
        if (!searchFocused) return super.keyPressed(key, scan, modifiers);
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && key == GLFW.GLFW_KEY_A) { selectAll = true; return true; }
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && key == GLFW.GLFW_KEY_V) { append(client.keyboard.getClipboard().replaceAll("[\\p{Cntrl}]", "")); return true; }
        if (key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) { query = selectAll || query.isEmpty() ? "" : query.substring(0, query.offsetByCodePoints(query.length(), -1)); selectAll = false; return true; }
        if (key == GLFW.GLFW_KEY_ENTER) searchFocused = false;
        return true;
    }
    @Override public boolean charTyped(char c, int modifiers) { if (!searchFocused || Character.isISOControl(c)) return false; if (c == '/' && query.isEmpty()) return true; append(String.valueOf(c)); return true; }
    private void append(String value) { query = (selectAll ? "" : query) + value; if (query.length() > 96) query = query.substring(0, 96); selectAll = false; }
    protected String number(double value) { return value == Math.rint(value) ? Long.toString((long) value) : String.format(Locale.ROOT, "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", ""); }
    public double[] controlPoint(String action) { Point point = diagnostics.get(action); return point == null ? null : new double[]{ox + point.x * scale, oy + point.y * scale}; }
    protected record Point(double x, double y) { }
    protected record Area(int x, int y, int w, int h) {
        int right() { return x + w; } int bottom() { return y + h; }
        boolean contains(double px, double py) { return px >= x && py >= y && px < right() && py < bottom(); }
        Area intersect(Area other) { int left = Math.max(x, other.x), top = Math.max(y, other.y); return new Area(left, top, Math.max(0, Math.min(right(), other.right()) - left), Math.max(0, Math.min(bottom(), other.bottom()) - top)); }
    }
    protected record Control(String id, Area area, int button, Runnable action) { }
    protected record Slider(String module, Setting<?> setting, Area track) { }
}
