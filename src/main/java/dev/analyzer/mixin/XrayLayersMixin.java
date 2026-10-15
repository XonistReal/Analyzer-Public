package dev.analyzer.mixin;

import dev.analyzer.render.XrayPolicy;
import dev.analyzer.render.XrayRenderer;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.fluid.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderLayers.class)
public abstract class XrayLayersMixin {
    @Inject(method = "getBlockLayer", at = @At("RETURN"), cancellable = true)
    private static void analyzer$xrayTerrainLayer(BlockState state, CallbackInfoReturnable<RenderLayer> cir) {if (XrayRenderer.fadeTerrain(state)) cir.setReturnValue(RenderLayer.getTranslucent());}
    @Inject(method = "getFluidLayer", at = @At("RETURN"), cancellable = true)
    private static void analyzer$xrayFluidLayer(FluidState state, CallbackInfoReturnable<RenderLayer> cir) {if (XrayRenderer.active() && XrayRenderer.policy().style() == XrayPolicy.Style.FADE) cir.setReturnValue(RenderLayer.getTranslucent());}
}
