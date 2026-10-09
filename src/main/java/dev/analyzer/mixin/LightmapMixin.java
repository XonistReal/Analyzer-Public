package dev.analyzer.mixin;

import dev.analyzer.AnalyzerClient;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.client.render.LightmapTextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Changes only the lightmap's local gamma read. The player's options remain untouched. */
@Mixin(LightmapTextureManager.class)
public abstract class LightmapMixin {
    @Redirect(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/SimpleOption;getValue()Ljava/lang/Object;", ordinal = 1))
    private Object analyzer$brightness(SimpleOption<?> option) {
        if (AnalyzerClient.CORE != null && AnalyzerClient.CORE.isEnabled("fullbright")) return AnalyzerClient.CORE.getNumber("fullbright", "gamma");
        return option.getValue();
    }
}
