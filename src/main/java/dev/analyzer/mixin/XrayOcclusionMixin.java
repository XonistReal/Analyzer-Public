package dev.analyzer.mixin;

import dev.analyzer.render.XrayRenderer;
import net.minecraft.client.render.chunk.ChunkOcclusionDataBuilder;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Rendering-only visibility graph; collision, lighting and server block state remain ordinary Minecraft data. */
@Mixin(ChunkOcclusionDataBuilder.class)
public abstract class XrayOcclusionMixin {
    @Inject(method = "markClosed", at = @At("HEAD"), cancellable = true)
    private void analyzer$xrayOpenChunkGraph(BlockPos pos, CallbackInfo ci) {if (XrayRenderer.active()) ci.cancel();}
}
