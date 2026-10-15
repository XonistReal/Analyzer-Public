package dev.analyzer.mixin;

import dev.analyzer.render.XrayRenderer;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntityRenderDispatcher.class)
public abstract class XrayBlockEntityMixin {
    @Inject(method = "render(Lnet/minecraft/block/entity/BlockEntity;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;)V", at = @At("HEAD"), cancellable = true)
    private <E extends BlockEntity> void analyzer$xrayHideOpaqueSpecialModels(E entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertices, CallbackInfo ci) {
        if (!XrayRenderer.active()) return;
        if (!XrayRenderer.selected(entity.getCachedState())) ci.cancel();
        else XrayRenderer.recordBlockEntity();
    }
    @ModifyArgs(method = "render(Lnet/minecraft/client/render/block/entity/BlockEntityRenderer;Lnet/minecraft/block/entity/BlockEntity;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/block/entity/BlockEntityRenderer;render(Lnet/minecraft/block/entity/BlockEntity;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;II)V"))
    private static void analyzer$lightSelectedContainer(Args args) {
        BlockEntity entity = args.get(0);
        if (XrayRenderer.active() && XrayRenderer.selected(entity.getCachedState())) args.set(4, 15728880);
    }
}
