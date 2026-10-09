package dev.analyzer.mixin;

import dev.analyzer.render.CosmeticRenderer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EndCrystalEntityRenderer;
import net.minecraft.client.render.entity.model.EndCrystalEntityModel;
import net.minecraft.client.render.entity.state.EndCrystalEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EndCrystalEntityRenderer.class)
public abstract class CrystalCosmeticsMixin {
    private static final String RENDER = "render(Lnet/minecraft/client/render/entity/state/EndCrystalEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V";
    @Redirect(method = RENDER, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/model/EndCrystalEntityModel;setAngles(Lnet/minecraft/client/render/entity/state/EndCrystalEntityRenderState;)V"))
    private void analyzer$pose(EndCrystalEntityModel model, EndCrystalEntityRenderState state) { model.setAngles(state); CosmeticRenderer.crystalPose(model, state); }
    @Redirect(method = RENDER, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/model/EndCrystalEntityModel;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;II)V"))
    private void analyzer$model(EndCrystalEntityModel model, MatrixStack matrices, VertexConsumer original, int light, int overlay, EndCrystalEntityRenderState state, MatrixStack ignored, VertexConsumerProvider vertices, int ignoredLight) {
        CosmeticRenderer.crystal(model, state, matrices, vertices, original, light, overlay);
    }
}
