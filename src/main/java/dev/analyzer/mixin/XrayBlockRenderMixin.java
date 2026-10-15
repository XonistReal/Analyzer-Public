package dev.analyzer.mixin;

import dev.analyzer.render.XrayRenderer;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockRenderManager.class)
public abstract class XrayBlockRenderMixin {
    @Inject(method = "renderBlock", at = @At("HEAD"), cancellable = true)
    private void analyzer$xrayFilter(BlockState state, BlockPos pos, BlockRenderView world, MatrixStack matrices, VertexConsumer vertices, boolean cull, Random random, CallbackInfo ci) {
        if (XrayRenderer.hidden(state)) {XrayRenderer.recordHidden(); ci.cancel();}
    }
    @ModifyVariable(method = "renderBlock", at = @At("HEAD"), argsOnly = true)
    private VertexConsumer analyzer$xrayModelVertices(VertexConsumer vertices, BlockState state, BlockPos pos, BlockRenderView world, MatrixStack matrices, VertexConsumer original, boolean cull, Random random) {
        if (((BlockRenderManager)(Object)this).getModel(state) instanceof dev.analyzer.render.XrayBakedModel) return vertices;
        return XrayRenderer.vertices(state, vertices);
    }
    @Inject(method = "renderFluid", at = @At("HEAD"), cancellable = true)
    private void analyzer$xrayFluidFilter(BlockPos pos, BlockRenderView world, VertexConsumer vertices, BlockState state, FluidState fluid, CallbackInfo ci) {
        if (XrayRenderer.active() && XrayRenderer.policy().style() != dev.analyzer.render.XrayPolicy.Style.FADE) ci.cancel();
    }
    @ModifyVariable(method = "renderFluid", at = @At("HEAD"), argsOnly = true)
    private VertexConsumer analyzer$xrayFluidVertices(VertexConsumer vertices, BlockPos pos, BlockRenderView world, VertexConsumer original, BlockState state, FluidState fluid) {return XrayRenderer.vertices(state, vertices);}
}
