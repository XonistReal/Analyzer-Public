package dev.analyzer.mixin;

import dev.analyzer.render.CosmeticRenderer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerCosmeticsMixin extends LivingEntityRenderer<AbstractClientPlayerEntity, PlayerEntityRenderState, PlayerEntityModel> {
    protected PlayerCosmeticsMixin(EntityRendererFactory.Context context, PlayerEntityModel model, float radius) { super(context, model, radius); }
    @Inject(method = "<init>", at = @At("RETURN"))
    private void analyzer$features(EntityRendererFactory.Context context, boolean slim, CallbackInfo ci) { addFeature(new CosmeticRenderer.Accessories(this)); }
    @Inject(method = "updateRenderState(Lnet/minecraft/client/network/AbstractClientPlayerEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V", at = @At("TAIL"))
    private void analyzer$state(AbstractClientPlayerEntity player, PlayerEntityRenderState state, float tick, CallbackInfo ci) { CosmeticRenderer.updatePlayer(state); }
}
