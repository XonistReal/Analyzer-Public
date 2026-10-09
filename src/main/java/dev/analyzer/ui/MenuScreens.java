package dev.analyzer.ui;

import dev.analyzer.core.AnalyzerCore;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

/** Persisted layout preference is resolved whenever the click GUI is opened. */
public final class MenuScreens {
    private MenuScreens() { }
    public static boolean isAnalyzerScreen(Screen screen) {
        return screen instanceof AnalyzerScreen || screen instanceof ClassicScreen || screen instanceof CompactScreen || screen instanceof HudEditorScreen;
    }
    public static Screen create(AnalyzerCore core) {
        return switch (core.getChoice("menu", "layout")) {
            case "Classic" -> new ClassicScreen(core);
            case "Compact" -> new CompactScreen(core);
            default -> new AnalyzerScreen(core);
        };
    }
    public static Screen select(AnalyzerCore core, String layout) {
        core.setValue("menu", "layout", layout);
        return create(core);
    }

    /** Small deterministic field shared by the alternative layouts; no allocations per particle. */
    public static void particles(AnalyzerCore core, DrawContext draw, int width, int height, double seconds, double mx, double my) {
        if (!core.isEnabled("particles")) return;
        int count = (int) Math.round(core.getNumber("particles", "density") * 80);
        double motion = core.getNumber("menu", "motion");
        int accent = HudWidgets.accent(core);
        double t = seconds * motion;
        for (int i = 0; i < count; i++) {
            double x = (i * 131.13 + Math.sin(t * 0.31 + i) * 38 + width * 2) % width;
            double y = (i * 73.77 + Math.cos(t * 0.27 + i * 0.47) * 32 + height * 2) % height;
            double distance = Math.hypot(mx - x, my - y);
            int color = i % 3 == 0 ? Palette.VIOLET : accent;
            if (distance < 140) GuiDraw.stroke(draw, x, y, mx, my, 0.6f, Palette.alpha(color, (int) (110 * (1 - distance / 140))));
            if (i > 0) {
                double px = ((i - 1) * 131.13 + Math.sin(t * 0.31 + i - 1) * 38 + width * 2) % width;
                double py = ((i - 1) * 73.77 + Math.cos(t * 0.27 + (i - 1) * 0.47) * 32 + height * 2) % height;
                if (Math.hypot(x - px, y - py) < 160) GuiDraw.stroke(draw, x, y, px, py, 0.7f, Palette.alpha(color, 65));
            }
            GuiDraw.glowDot(draw, x, y, i % 9 == 0 ? 2.1f : 1, color, 190);
        }
    }
}
