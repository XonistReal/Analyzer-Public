package dev.analyzer.mixin;

import dev.analyzer.render.CosmeticRenderer;
import dev.analyzer.render.ItemCosmeticState;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import net.minecraft.client.render.entity.state.ItemEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntityRenderer.class)
public abstract class ItemPhysicsRendererMixin extends EntityRenderer<ItemEntity, ItemEntityRenderState> {
    @Shadow @Final private Random random;
    protected ItemPhysicsRendererMixin(EntityRendererFactory.Context context) { super(context); }
    @Inject(method = "updateRenderState(Lnet/minecraft/entity/ItemEntity;Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;F)V", at = @At("TAIL"))
    private void analyzer$snapshot(ItemEntity entity, ItemEntityRenderState state, float tickDelta, CallbackInfo ci) {
        ((ItemCosmeticState) state).analyzer$itemPhysics(entity.isOnGround(), entity.isTouchingWater(), (entity.getUuid().hashCode() & 65535) * ((float)Math.PI * 2 / 65535));
    }
    @Inject(method = "render(Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V", at = @At("HEAD"), cancellable = true)
    private void analyzer$physical(ItemEntityRenderState state, MatrixStack matrices, VertexConsumerProvider vertices, int light, CallbackInfo ci) {
        if (!CosmeticRenderer.on("item-physics")) return;
        if (!state.itemRenderState.isEmpty()) CosmeticRenderer.dropped(state, matrices, vertices, light, random);
        super.render(state, matrices, vertices, light);
        ci.cancel();
    }
}
