package dev.analyzer.ui;

import dev.analyzer.core.AnalyzerCore;
import dev.analyzer.effects.GpuEffects;
import dev.analyzer.effects.UiShader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Studio surfaces shared by the task console, HUD editor and precise-value dialog. */
public final class StudioAuxiliary {
    public static final int TEXT = 0xFFF7F5F8, MUTED = 0xFFBBB7C0, SUBTLE = 0xFF908C96;
    private StudioAuxiliary() { }

    public static int accent(AnalyzerCore core) {
        if (core == null) return 0xFFDB85DA;
        return switch (core.getChoice("menu", "accent")) {
            case "Rose" -> 0xFFDB85DA;
            case "Violet" -> Palette.VIOLET;
            case "Gold" -> Palette.GOLD;
            default -> Palette.MINT;
        };
    }
    public static String theme(AnalyzerCore core) { return core == null ? "Glass" : core.getChoice("menu", "theme"); }
    public static boolean glass(AnalyzerCore core) {
        return core != null && core.isEnabled("menu") && core.getChoice("menu", "material").equals("Glass") && GpuEffects.isHealthy();
    }
    public static void backdrop(DrawContext draw, AnalyzerCore core, int width, int height) {
        if (glass(core)) GpuEffects.drawBackdrop(draw);
        else draw.fill(0, 0, width, height, 0x30000000);
    }

    /** Stable physical size across native GUI scales, while fitting a small game window. */
    public static float canvasScale(MinecraftClient client, int canvasWidth, int canvasHeight, double fractionWidth, double fractionHeight) {
        var window = client.getWindow();
        double physical = Math.min(1.35, Math.min(window.getFramebufferWidth() * fractionWidth / canvasWidth,
                window.getFramebufferHeight() * fractionHeight / canvasHeight));
        return (float) Math.max(.12, physical / window.getScaleFactor());
    }

    public static void surface(DrawContext draw, float x, float y, float w, float h, float radius,
                               int top, int bottom, int shadow, float softness, int glow, float glowSoftness) {
        if (!UiShader.surface(draw, x, y, w, h, radius, top, bottom, shadow, softness, glow, glowSoftness))
            GuiDraw.rounded(draw, Math.round(x), Math.round(y), Math.round(w), Math.round(h), Math.round(radius), top);
    }
    public static void field(DrawContext draw, AnalyzerCore core, int x, int y, int w, int h, boolean focused) {
        surface(draw, x, y, w, h, 10, focused ? 0xFF38333C : 0xFF202023, 0xFF202023,
                0x25000000, 3, focused ? Palette.alpha(accent(core), 36) : 0, 6);
        if (focused) surface(draw, x + 10, y + h - 2, w - 20, 1.5f, .75f,
                Palette.alpha(accent(core), 150), Palette.alpha(accent(core), 150), 0, 0, 0, 0);
    }
    public static void button(DrawContext draw, AnalyzerCore core, TextRenderer renderer, int x, int y, int w, int h,
                              String label, boolean enabled, boolean primary, boolean hover) {
        int accent = accent(core);
        if (primary && enabled) surface(draw, x, y, w, h, Math.min(11, h / 2f),
                Palette.mix(accent, 0xFFFFFFFF, hover ? .12 : .04), accent,
                0x30000000, 3, Palette.alpha(accent, hover ? 48 : 24), 7);
        else GuiDraw.shaderCard(draw, x, y, w, h, Math.min(11, h / 2), accent, glass(core), theme(core), hover ? 1 : 0, false);
        String text = GuiDraw.truncate(renderer, label, w - 14, .96f);
        GuiDraw.label(draw, renderer, text, x + (w - GuiDraw.width(renderer, text, .96f)) / 2,
                y + (h - 11) / 2f, .96f, enabled ? primary ? 0xFF1D1220 : TEXT : SUBTLE);
    }
    public static void button(DrawContext draw, AnalyzerCore core, int x, int y, int w, int h,
                              String label, boolean enabled, boolean primary, boolean hover) {
        button(draw, core, MinecraftClient.getInstance().textRenderer, x, y, w, h, label, enabled, primary, hover);
    }
    public static void pill(DrawContext draw, int x, int y, String label, int color, TextRenderer renderer) {
        String text = GuiDraw.truncate(renderer, label, 164, .82f);
        int w = Math.round(GuiDraw.width(renderer, text, .82f)) + 20;
        surface(draw, x, y, w, 24, 12, Palette.alpha(color, 23), Palette.alpha(color, 16), 0, 0, 0, 0);
        GuiDraw.label(draw, renderer, text, x + 10, y + 7, .82f, color);
    }

    /** Native button focus, input, callbacks and narration with Studio drawing. */
    public static final class Button extends ButtonWidget {
        private final AnalyzerCore core;
        public Button(AnalyzerCore core, int x, int y, int width, int height, Text text, PressAction action) {
            super(x, y, width, height, text, action, DEFAULT_NARRATION_SUPPLIER);
            this.core = core;
        }
        @Override protected void renderWidget(DrawContext draw, int mouseX, int mouseY, float delta) {
            try (var font = GuiDraw.sansFont()) {
                button(draw, core, MinecraftClient.getInstance().textRenderer, getX(), getY(), getWidth(), getHeight(),
                        getMessage().getString(), active, false, isHovered() || isFocused());
                if (isFocused()) surface(draw, getX() + 8, getY() + getHeight() - 2, getWidth() - 16, 1,
                        .5f, accent(core), accent(core), 0, 0, 0, 0);
            }
        }
    }
}
