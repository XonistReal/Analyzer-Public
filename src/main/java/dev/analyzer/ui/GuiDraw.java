package dev.analyzer.ui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import java.util.ArrayList;
import java.util.List;

/** Small, dependency-free canvas primitives. All coordinates use the screen's logical canvas. */
public final class GuiDraw {
    private static final Identifier FONT = Identifier.of("analyzer", "ui");
    private static final Identifier BOLD_FONT = Identifier.of("analyzer", "ui_bold");
    private static final float MIN_TEXT_SCALE = 0.8f;

    private GuiDraw() {}

    public static Text text(String value) {
        return Text.literal(value).styled(style -> style.withFont(FONT));
    }

    /** Keep rendering and all measuring helpers on the same minimum logical size. */
    public static float effectiveScale(float scale) {
        return Float.isFinite(scale) ? Math.max(MIN_TEXT_SCALE, scale) : MIN_TEXT_SCALE;
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

    /** One quad per segment rather than one fill per pixel, for animated networks. */
    public static void stroke(DrawContext context, double x1, double y1, double x2, double y2,
                              float thickness, int color) {
        double length = Math.hypot(x2 - x1, y2 - y1);
        if (length < 0.01) return;
        context.getMatrices().push();
        context.getMatrices().translate((float) x1, (float) y1, 0);
        context.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotation((float) Math.atan2(y2 - y1, x2 - x1)));
        context.getMatrices().scale((float) length, thickness, 1);
        context.fill(0, 0, 1, 1, color);
        context.getMatrices().pop();
    }

    public static void glowDot(DrawContext context, double x, double y, float size, int color, int brightness) {
        int cx = (int) Math.round(x), cy = (int) Math.round(y);
        int r = Math.max(1, Math.round(size));
        rounded(context, cx - r * 4, cy - r * 4, r * 8, r * 8, r * 4, Palette.alpha(color, brightness / 18));
        rounded(context, cx - r * 2, cy - r * 2, r * 4, r * 4, r * 2, Palette.alpha(color, brightness / 8));
        rounded(context, cx - r, cy - r, r * 2, r * 2, r, Palette.alpha(color, brightness));
        context.fill(cx, cy, cx + 1, cy + 1, Palette.alpha(Palette.TEXT, brightness));
    }

    public static void wireBox(DrawContext context, int x, int y, int w, int h, boolean corners,
                               float thickness, int color) {
        int arm = Math.max(5, Math.min(w, h) / 4);
        if (!corners) {
            stroke(context, x, y, x + w, y, thickness, color);
            stroke(context, x + w, y, x + w, y + h, thickness, color);
            stroke(context, x + w, y + h, x, y + h, thickness, color);
            stroke(context, x, y + h, x, y, thickness, color);
            return;
        }
        for (int corner = 0; corner < 4; corner++) {
            int cx = x + (corner % 2) * w, cy = y + (corner / 2) * h;
            int dx = corner % 2 == 0 ? arm : -arm, dy = corner / 2 == 0 ? arm : -arm;
            stroke(context, cx, cy, cx + dx, cy, thickness, color);
            stroke(context, cx, cy, cx, cy + dy, thickness, color);
        }
    }

    public static void label(DrawContext context, TextRenderer renderer, String value,
                             float x, float y, float scale, int color) {
        label(context, renderer, value, x, y, scale, color, false);
    }
    public static void label(DrawContext context, TextRenderer renderer, String value,
                             float x, float y, float scale, int color, boolean shadow) {
        scale = effectiveScale(scale);
        context.getMatrices().push();
        translateTextOrigin(context, x, y);
        context.getMatrices().scale(scale, scale, 1);
        context.drawText(renderer, text(value), 0, 0, color, shadow);
        context.getMatrices().pop();
    }
    public static void bitmapLabel(DrawContext context, TextRenderer renderer, String value,
                                   float x, float y, float scale, int color) {
        context.getMatrices().push();
        translateTextOrigin(context, x, y);
        context.getMatrices().scale(scale, scale, 1);
        context.drawText(renderer, value, 0, 0, color, true);
        context.getMatrices().pop();
    }

    public static void heading(DrawContext context, TextRenderer renderer, String value,
                               float x, float y, float scale, int color) {
        scale = effectiveScale(scale);
        context.getMatrices().push();
        translateTextOrigin(context, x, y);
        context.getMatrices().scale(scale, scale, 1);
        context.drawText(renderer, Text.literal(value).styled(style -> style.withFont(BOLD_FONT)), 0, 0, color, false);
        context.getMatrices().pop();
    }

    public static float width(TextRenderer renderer, String value, float scale) {
        return renderer.getWidth(text(value)) * effectiveScale(scale);
    }

    /** Snap through the outer canvas/HUD transform rather than rounding local GUI points. */
    private static void translateTextOrigin(DrawContext context, float x, float y) {
        var matrix = context.getMatrices().peek().getPositionMatrix();
        float a = matrix.m00(), b = matrix.m10(), c = matrix.m01(), d = matrix.m11();
        float determinant = a * d - b * c;
        double pixelsPerPoint = MinecraftClient.getInstance().getWindow().getScaleFactor();
        if (Math.abs(determinant) > 0.000001f && pixelsPerPoint > 0) {
            double screenX = a * x + b * y + matrix.m30();
            double screenY = c * x + d * y + matrix.m31();
            double dx = Math.round(screenX * pixelsPerPoint) / pixelsPerPoint - screenX;
            double dy = Math.round(screenY * pixelsPerPoint) / pixelsPerPoint - screenY;
            x += (float) ((d * dx - b * dy) / determinant);
            y += (float) ((a * dy - c * dx) / determinant);
        }
        context.getMatrices().translate(x, y, 0);
    }

    public static String truncate(TextRenderer renderer, String value, float maxWidth, float scale) {
        if (width(renderer, value, scale) <= maxWidth) return value;
        String ending = "…";
        if (width(renderer, ending, scale) > maxWidth) return "";
        int end = value.length();
        while (end > 0 && width(renderer, value.substring(0, end) + ending, scale) > maxWidth) end = value.offsetByCodePoints(end, -1);
        return value.substring(0, end) + ending;
    }

    public static List<String> wrap(TextRenderer renderer, String value, float maxWidth, float scale) {
        List<String> lines = new ArrayList<>();
        String line = "";
        if (maxWidth <= 0) return lines;
        for (String word : value.split("\\s+")) {
            if (word.isEmpty()) continue;
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && width(renderer, candidate, scale) > maxWidth) {
                lines.add(line);
                line = "";
            }
            // Long paths, player names and translated words must fit the same measured limit.
            if (width(renderer, word, scale) > maxWidth) {
                if (!line.isEmpty()) { lines.add(line); line = ""; }
                String remaining = word;
                while (width(renderer, remaining, scale) > maxWidth) {
                    int end = remaining.length();
                    while (end > 0 && width(renderer, remaining.substring(0, end), scale) > maxWidth) end = remaining.offsetByCodePoints(end, -1);
                    if (end == 0) return lines;
                    lines.add(remaining.substring(0, end));
                    remaining = remaining.substring(end);
                }
                line = remaining;
            } else line = line.isEmpty() ? word : line + " " + word;
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
