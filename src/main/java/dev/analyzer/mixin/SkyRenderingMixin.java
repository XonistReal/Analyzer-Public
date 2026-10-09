package dev.analyzer.mixin;

import dev.analyzer.effects.GpuEffects;
import net.minecraft.client.render.SkyRendering;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyRendering.class)
public abstract class SkyRenderingMixin {
    @Inject(method = "renderSky", at = @At("RETURN"))
    private void analyzer$sky(float red, float green, float blue, CallbackInfo ci) {
        GpuEffects.renderSky();
    }
}
