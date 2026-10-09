package dev.analyzer.ui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import java.util.ArrayList;
import java.util.List;

/** Small, dependency-free canvas primitives. All coordinates use the screen's logical canvas. */
public final class GuiDraw {
    private static final Identifier FONT = Identifier.of("analyzer", "ui");
    private static final Identifier BOLD_FONT = Identifier.of("analyzer", "ui_bold");

    private GuiDraw() {}

    public static Text text(String value) {
        return Text.literal(value).styled(style -> style.withFont(FONT));
    }

    public static void rounded(DrawContext context, int x, int y, int width, int height, int radius, int color) {
        if (width <= 0 || height <= 0) return;
        int r = Math.clamp(radius, 0, Math.min(width, height) / 2);
        if (r == 0) {
            context.fill(x, y, x + width, y + height, color);
            return;
        }
        context.fill(x, y + r, x + width, y + height - r, color);
        for (int row = 0; row < r; row++) {
            double dy = r - row - 0.5;
            int inset = (int) Math.ceil(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
            context.fill(x + inset, y + row, x + width - inset, y + row + 1, color);
            context.fill(x + inset, y + height - row - 1, x + width - inset, y + height - row, color);
        }
    }

    public static void outlined(DrawContext context, int x, int y, int width, int height, int radius,
                                int border, int inside) {
        rounded(context, x, y, width, height, radius, border);
        rounded(context, x + 1, y + 1, width - 2, height - 2, radius - 1, inside);
    }

    public static void glow(DrawContext context, int x, int y, int width, int height, int radius, int color) {
        for (int layer = 6; layer >= 1; layer--) {
            rounded(context, x - layer * 2, y - layer * 2, width + layer * 4, height + layer * 4,
                    radius + layer * 2, Palette.alpha(color, 2 + (6 - layer) * 2));
        }
    }

    public static void line(DrawContext context, double x1, double y1, double x2, double y2, int color) {
        double distance = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        int steps = Math.max(1, (int) Math.ceil(distance));
        for (int i = 0; i <= steps; i++) {
            int x = (int) Math.round(x1 + (x2 - x1) * i / steps);
            int y = (int) Math.round(y1 + (y2 - y1) * i / steps);
            context.fill(x, y, x + 1, y + 1, color);
        }
    }

    public static void label(DrawContext context, TextRenderer renderer, String value,
                             float x, float y, float scale, int color) {
        context.getMatrices().push();
        context.getMatrices().translate(x, y, 0);
        context.getMatrices().scale(scale, scale, 1);
        context.drawText(renderer, text(value), 0, 0, color, false);
        context.getMatrices().pop();
    }

    public static void heading(DrawContext context, TextRenderer renderer, String value,
                               float x, float y, float scale, int color) {
        context.getMatrices().push();
        context.getMatrices().translate(x, y, 0);
        context.getMatrices().scale(scale, scale, 1);
        context.drawText(renderer, Text.literal(value).styled(style -> style.withFont(BOLD_FONT)), 0, 0, color, false);
        context.getMatrices().pop();
    }

    public static float width(TextRenderer renderer, String value, float scale) {
        return renderer.getWidth(text(value)) * scale;
    }

    public static String truncate(TextRenderer renderer, String value, float maxWidth, float scale) {
        if (width(renderer, value, scale) <= maxWidth) return value;
        String ending = "…";
        int end = value.length();
        while (end > 0 && width(renderer, value.substring(0, end) + ending, scale) > maxWidth) end--;
        return value.substring(0, end) + ending;
    }

    public static List<String> wrap(TextRenderer renderer, String value, float maxWidth, float scale) {
        List<String> lines = new ArrayList<>();
        String line = "";
        for (String word : value.split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && width(renderer, candidate, scale) > maxWidth) {
                lines.add(line);
                line = word;
            } else line = candidate;
        }
        if (!line.isEmpty()) lines.add(line);
        return lines;
    }

    public static void logo(DrawContext context, int x, int y, int size, int color) {
        // An open geometric A: two ascending rails and an offset crossbar.
        double unit = size / 28.0;
        for (int stroke = 0; stroke < Math.max(2, size / 10); stroke++) {
            line(context, x + 3 * unit + stroke, y + 25 * unit, x + 14 * unit + stroke, y + 3 * unit, color);
            line(context, x + 14 * unit + stroke, y + 3 * unit, x + 25 * unit + stroke, y + 25 * unit, color);
        }
        rounded(context, x + (int) (10 * unit), y + (int) (17 * unit), (int) (12 * unit),
                Math.max(2, (int) (2 * unit)), 1, color);
    }

    public static void cube(DrawContext context, int x, int y, int size, int color) {
        int h = size / 2, q = size / 4;
        line(context, x + h, y, x + size, y + q, color);
        line(context, x + size, y + q, x + h, y + h, color);
        line(context, x + h, y + h, x, y + q, color);
        line(context, x, y + q, x + h, y, color);
        line(context, x, y + q, x, y + h + q, color);
        line(context, x + size, y + q, x + size, y + h + q, color);
        line(context, x + h, y + h, x + h, y + size, color);
        line(context, x, y + h + q, x + h, y + size, color);
        line(context, x + size, y + h + q, x + h, y + size, color);
    }
}
