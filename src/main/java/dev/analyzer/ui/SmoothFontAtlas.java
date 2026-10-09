package dev.analyzer.ui;

import net.minecraft.client.font.GlyphAtlasTexture;
import net.minecraft.client.font.TextRenderLayerSet;
import net.minecraft.util.TriState;
import org.lwjgl.opengl.GL11;

/** Own-font atlas retains linear sampling across uploads and render-phase filter changes. */
public final class SmoothFontAtlas extends GlyphAtlasTexture {
    private static int created;
    private static boolean healthy = true;
    private static final boolean DIAGNOSTICS = Boolean.getBoolean("analyzer.smoke");
    public SmoothFontAtlas(TextRenderLayerSet layers, boolean colored) { super(layers, colored); created++; }
    @Override public void setFilter(boolean blur, boolean mipmap) {
        super.setFilter(true, false);
        if (DIAGNOSTICS) {
            // setFilter can return without binding when its cached values already match.
            int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            bindTexture();
            healthy &= GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER) == GL11.GL_LINEAR
                    && GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER) == GL11.GL_LINEAR;
            com.mojang.blaze3d.platform.GlStateManager._bindTexture(previous);
        }
    }
    @Override public void setFilter(TriState blur, boolean mipmap) { setFilter(true, false); }
    public static boolean healthy() { return created > 0 && healthy; }
}
