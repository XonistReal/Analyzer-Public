package dev.analyzer.mixin;

import dev.analyzer.render.EntityChamsRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.ref.WeakReference;

/** Captures the exact native model and pose without replacing any normal living-entity rendering. */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityChamsMixin {
    @Shadow protected EntityModel<?> model;
    @Unique private WeakReference<LivingEntity> analyzer$chamsEntity;

    @Inject(method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V", at = @At("TAIL"))
    private void analyzer$rememberEntity(LivingEntity entity, LivingEntityRenderState state, float tickDelta, CallbackInfo ci) {
        analyzer$chamsEntity = EntityChamsRenderer.accepting() ? new WeakReference<>(entity) : null;
    }

    @Inject(method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/model/EntityModel;setAngles(Lnet/minecraft/client/render/entity/state/EntityRenderState;)V", shift = At.Shift.AFTER))
    private void analyzer$capturePosedModel(LivingEntityRenderState state, MatrixStack matrices, VertexConsumerProvider consumers, int light, CallbackInfo ci) {
        EntityChamsRenderer.capture(analyzer$chamsEntity == null ? null : analyzer$chamsEntity.get(), state, model, matrices);
    }
}
