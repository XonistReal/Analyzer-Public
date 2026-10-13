package dev.analyzer.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;

/** User-supplied RGBA artwork is rendered unchanged, including its translucent glow. */
public final class BrandAssets {
    public static final Identifier WORDMARK = Identifier.of("analyzer", "textures/brand/wordmark.png");
    public static final Identifier MONOGRAM = Identifier.of("analyzer", "textures/brand/monogram.png");
    public static final float WORDMARK_ASPECT = 1139f / 392f, MONOGRAM_ASPECT = 341f / 392f;
    private static long wordmarkFrames, monogramFrames;
    private BrandAssets() { }
    public static long wordmarkFrames() { return wordmarkFrames; }
    public static long monogramFrames() { return monogramFrames; }
    public static void wordmark(DrawContext draw, float x, float y, float width) {
        draw.drawTexture(RenderLayer::getGuiTextured, WORDMARK, Math.round(x), Math.round(y), 0, 0,
                Math.round(width), Math.round(width / WORDMARK_ASPECT), 1139, 392, 1139, 392);
        wordmarkFrames++;
    }
    public static void monogram(DrawContext draw, float x, float y, float height) {
        draw.drawTexture(RenderLayer::getGuiTextured, MONOGRAM, Math.round(x), Math.round(y), 0, 0,
                Math.round(height * MONOGRAM_ASPECT), Math.round(height), 341, 392, 341, 392);
        monogramFrames++;
    }
}
