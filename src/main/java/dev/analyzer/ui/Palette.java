package dev.analyzer.ui;

/** Analyzer's shared ARGB palette, including its original menu themes. */
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

    /** Neutral surfaces let the scene supply the color of frosted glass. */
    public static int menuSurface(String theme, boolean glass) {
        int color = switch (theme == null ? "Glass" : theme) {
            case "Classic" -> 0xFF101820;
            case "Minimal" -> 0xFF121416;
            case "Neon" -> 0xFF151321;
            default -> 0xFF11171D;
        };
        if (!glass) return color;
        return alpha(color, switch (theme == null ? "Glass" : theme) {
            case "Classic" -> 194;
            case "Minimal" -> 180;
            case "Neon" -> 180;
            default -> 174;
        });
    }

    /** Cards keep more contrast than the outer glass shell, including on a bright world. */
    public static int menuCard(String theme, boolean glass, double hover) {
        int color = switch (theme == null ? "Glass" : theme) {
            case "Classic" -> 0xFF19232D;
            case "Minimal" -> 0xFF191B1D;
            case "Neon" -> 0xFF211C2E;
            default -> 0xFF1A2229;
        };
        double amount = Math.clamp(hover, 0.0, 1.0);
        color = mix(color, TEXT, amount * 0.045);
        if (!glass) return color;
        int opacity = switch (theme == null ? "Glass" : theme) {
            case "Classic" -> 216;
            case "Minimal" -> 182;
            case "Neon" -> 205;
            default -> 198;
        };
        return alpha(color, opacity + (int) Math.round(amount * 10));
    }

    public static int menuBorder(String theme, int accent, boolean emphasized) {
        // A lightened accent keeps Violet selection readable on the brightest glass shell.
        if (emphasized) return alpha(mix(accent, TEXT, "Neon".equals(theme) ? 0.34 : 0.22),
                "Neon".equals(theme) ? 230 : 220);
        return switch (theme == null ? "Glass" : theme) {
            case "Classic" -> 0x9FDAE5EB;
            case "Minimal" -> 0xA9E5E5E5;
            case "Neon" -> alpha(mix(TEXT, accent, 0.17), 170);
            default -> 0xA3DBEAF0;
        };
    }

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
