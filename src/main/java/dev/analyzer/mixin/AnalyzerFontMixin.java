package dev.analyzer.mixin;

import net.minecraft.client.font.FontStorage;
import net.minecraft.client.font.GlyphAtlasTexture;
import net.minecraft.client.font.TextRenderLayerSet;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Smooth only Analyzer's supersampled font atlases; Minecraft bitmap fonts stay native. */
@Mixin(FontStorage.class)
public abstract class AnalyzerFontMixin {
    @Shadow @Final private Identifier id;
    @Redirect(method = "bake(Lnet/minecraft/client/font/RenderableGlyph;)Lnet/minecraft/client/font/BakedGlyph;",
        at = @At(value = "NEW", target = "net/minecraft/client/font/GlyphAtlasTexture"))
    private GlyphAtlasTexture analyzer$atlas(TextRenderLayerSet layers, boolean colored) {
        if (id.getNamespace().equals("analyzer") && (id.getPath().equals("ui") || id.getPath().equals("ui_bold") || id.getPath().equals("studio") || id.getPath().equals("studio_bold")))
            return new dev.analyzer.ui.SmoothFontAtlas(layers, colored);
        return new GlyphAtlasTexture(layers, colored);
    }
}
