package dev.analyzer.ui;

/** Analyzer's shared, opaque ARGB palette. */
public final class Palette {
    public static final int BACKGROUND = 0xFF090F13;
    public static final int SURFACE = 0xFF10191F;
    public static final int CARD = 0xFF141F26;
    public static final int CARD_HOVER = 0xFF1A2931;
    public static final int BORDER = 0xFF25343C;
    public static final int MINT = 0xFF79F8CC;
    public static final int VIOLET = 0xFFA993FA;
    public static final int CYAN = 0xFF6DDEFF;
    public static final int GOLD = 0xFFFFCF84;
    public static final int GLASS = 0xAD09101A;
    public static final int CARD_GLASS = 0xC415222D;
    public static final int BRIGHT_BORDER = 0xFF3A645C;
    public static final int TEXT = 0xFFF4FAFF;
    public static final int MUTED = 0xFFB9CBD4;
    public static final int SUBTLE = 0xFF91A8B5;
    public static final int ERROR = 0xFFFF9299;

    private Palette() {}

    public static int alpha(int color, int alpha) {
        return (Math.clamp(alpha, 0, 255) << 24) | (color & 0xFFFFFF);
    }

    public static int mix(int first, int second, double amount) {
        double t = Math.clamp(amount, 0.0, 1.0);
        int a = (int) (((first >>> 24) & 255) * (1 - t) + ((second >>> 24) & 255) * t);
        int r = (int) (((first >>> 16) & 255) * (1 - t) + ((second >>> 16) & 255) * t);
        int g = (int) (((first >>> 8) & 255) * (1 - t) + ((second >>> 8) & 255) * t);
        int b = (int) ((first & 255) * (1 - t) + (second & 255) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
