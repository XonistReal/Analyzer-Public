package dev.analyzer.automation;

import dev.analyzer.core.AnalyzerCore;
import dev.analyzer.effects.UiShader;
import dev.analyzer.ui.GuiDraw;
import dev.analyzer.ui.HudWidgets;
import dev.analyzer.ui.Palette;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

/** A small real-status banner, drawn by the engine's HUD callback only while protection intervenes. */
public final class SafetyHud {
    private static final String TITLE = "Task paused for safety";
    private static final float TEXT_SCALE = 14f / 12f;
    private static final int PADDING = 14, LINE_HEIGHT = 16;
    private static long renderedFrames;
    private static long shaderFrames;
    private static Bounds lastBounds;

    private SafetyHud() { }

    /** Physical framebuffer bounds of the most recent draw; null when this call was intentionally hidden. */
    public static Bounds lastBounds() { return lastBounds; }
    public static long renderedFrames() { return renderedFrames; }
    public static long shaderFrames() { return shaderFrames; }
    public record Bounds(int x, int y, int width, int height, float nominalTextPixels) { }

    /** Invoke from HudRenderCallback while the task is PROTECTING; this class registers no callbacks itself. */
    public static void render(DrawContext context, AnalyzerCore core, String message, boolean critical) {
        render(context, core, TITLE, message, critical);
    }
    public static void render(DrawContext context, AnalyzerCore core, String title, String message, boolean critical) {
        try (GuiDraw.GlassScope optical = GuiDraw.glassScope(context, core == null ? "Classic" : core.getChoice("hud", "theme"));
             GuiDraw.FontScope ignored = GuiDraw.sansFont()) {
            scopedRender(context, core, title, message, critical);
        }
    }
    private static void scopedRender(DrawContext context, AnalyzerCore core, String heading, String message, boolean critical) {
        lastBounds = null;
        MinecraftClient client = MinecraftClient.getInstance();
        if (context == null || core == null || client.player == null || client.world == null
                || client.options.hudHidden || client.currentScreen != null || client.getOverlay() != null) return;
        int screenWidth = client.getWindow().getFramebufferWidth();
        int screenHeight = client.getWindow().getFramebufferHeight();
        float pixelsPerPoint = (float) client.getWindow().getScaleFactor();
        if (screenWidth < 120 || screenHeight < 64 || pixelsPerPoint <= 0) return;

        // Physical-pixel coordinates keep the banner readable at HUD 60% and Minecraft GUI 2/3/4.
        // The title/body never inherit the menu canvas transform or its minimum logical font size.
        int maxWidth = Math.min(660, screenWidth - PADDING * 2);
        int contentWidth = maxWidth - PADDING * 2 - 12;
        String title = GuiDraw.truncate(client.textRenderer, heading == null ? TITLE : heading, contentWidth, TEXT_SCALE);
        String text = bounded(message);
        List<String> wrapped = GuiDraw.wrap(client.textRenderer, text, contentWidth, TEXT_SCALE);
        int maxLines = Math.clamp((screenHeight - 44 - PADDING * 2) / LINE_HEIGHT, 1, 2);
        List<String> lines = new ArrayList<>(wrapped.subList(0, Math.min(maxLines, wrapped.size())));
        if (wrapped.size() > maxLines && !lines.isEmpty()) {
            int last = lines.size() - 1;
            lines.set(last, GuiDraw.truncate(client.textRenderer, lines.get(last) + " …", contentWidth, TEXT_SCALE));
        }
        float longest = GuiDraw.width(client.textRenderer, title, TEXT_SCALE);
        for (String line : lines) longest = Math.max(longest, GuiDraw.width(client.textRenderer, line, TEXT_SCALE));
        int width = Math.min(maxWidth, Math.max(Math.min(340, maxWidth), (int) Math.ceil(longest) + PADDING * 2 + 12));
        int height = 44 + lines.size() * LINE_HEIGHT;
        int x = Math.max(0, (screenWidth - width) / 2);
        int y = Math.min(12, Math.max(2, (screenHeight - height) / 2));
        int color = critical ? Palette.ERROR : Palette.GOLD;

        context.getMatrices().push();
        try {
            context.getMatrices().scale(1 / pixelsPerPoint, 1 / pixelsPerPoint, 1);
            if (HudWidgets.panel(context, x, y, width, height, core.getChoice("hud", "theme"), color, .98)) shaderFrames++;
            if (!UiShader.surface(context, x + PADDING, y + 14, 7, 7, 3.5f, color, color, 0, 0, Palette.alpha(color, 48), 3))
                GuiDraw.rounded(context, x + PADDING, y + 14, 7, 7, 3, color);
            GuiDraw.heading(context, client.textRenderer, title, x + PADDING + 17, y + 10,
                    TEXT_SCALE, color);
            for (int i = 0; i < lines.size(); i++) {
                GuiDraw.label(context, client.textRenderer, lines.get(i), x + PADDING,
                        y + 31 + i * LINE_HEIGHT, TEXT_SCALE, Palette.TEXT);
            }
        } finally {
            context.getMatrices().pop();
        }
        lastBounds = new Bounds(x, y, width, height, TEXT_SCALE * 12);
        renderedFrames++;
    }

    private static String bounded(String message) {
        String result = message == null ? "" : message.replace('\0', ' ').replaceAll("\\s+", " ").trim();
        if (result.isEmpty()) return "Protection is active. Open Automation for details.";
        if (result.codePointCount(0, result.length()) > 512)
            result = result.substring(0, result.offsetByCodePoints(0, 509)) + "…";
        return result;
    }
}
