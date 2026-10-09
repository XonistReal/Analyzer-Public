package dev.analyzer.render;

import java.util.List;

/** Distance and collision rules shared by world billboards and Screen ESP. No HUD scale enters these rules. */
public final class EspLayout {
    private EspLayout() { }

    public record Appearance(double size, double opacity) {
        public boolean visible() { return size > 0 && opacity > 0; }
    }

    public static Appearance appearance(double distance, double near, double range, double farSize,
                                        boolean shrink, boolean fade) {
        if (!Double.isFinite(distance) || distance < 0 || !Double.isFinite(range) || range <= 0 || distance > range)
            return new Appearance(0, 0);
        near = Math.clamp(Double.isFinite(near) ? near : 0, 0, range);
        farSize = Math.clamp(Double.isFinite(farSize) ? farSize : 0.45, 0.05, 1);
        double progress = range <= near ? 0 : Math.clamp((distance - near) / (range - near), 0, 1);
        // Smoothstep avoids a visible size jump at the full-size distance.
        double eased = progress * progress * (3 - 2 * progress);
        return new Appearance(shrink ? 1 - (1 - farSize) * eased : 1, fade ? 1 - 0.65 * eased : 1);
    }

    /** A subpixel box is omitted, never inflated into a fixed-size distant marker. */
    public static boolean markerVisible(double widthPixels, double heightPixels, double minimumPixels) {
        return Double.isFinite(widthPixels) && Double.isFinite(heightPixels) && widthPixels > 0 && heightPixels > 0
                && Math.max(widthPixels, heightPixels) >= Math.max(0, minimumPixels);
    }

    public record Rect(double x, double y, double width, double height) {
        public double right() { return x + width; }
        public double bottom() { return y + height; }
        public boolean overlaps(Rect other, double gap) {
            return x < other.right() + gap && right() + gap > other.x
                    && y < other.bottom() + gap && bottom() + gap > other.y;
        }
        public boolean inside(double widthPixels, double heightPixels) {
            return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(width) && Double.isFinite(height)
                    && width > 0 && height > 0 && x >= 0 && y >= 0 && right() <= widthPixels && bottom() <= heightPixels;
        }
    }

    public static boolean free(Rect candidate, List<Rect> occupied, double gap) {
        for (Rect other : occupied) if (candidate.overlaps(other, gap)) return false;
        return true;
    }
}
