package dev.analyzer.mixin;

import dev.analyzer.render.CosmeticRenderer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ModelTransformationMode;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public abstract class HandTweaksMixin {
    private static final String FIRST = "renderFirstPersonItem(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/util/Hand;FLnet/minecraft/item/ItemStack;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V";
    @Inject(method = FIRST, at = @At("HEAD"), cancellable = true)
    private void analyzer$hide(AbstractClientPlayerEntity player, float tick, float pitch, Hand hand, float swing, ItemStack item, float equip, MatrixStack matrices, VertexConsumerProvider vertices, int light, CallbackInfo ci) { if (CosmeticRenderer.hideHand(hand)) ci.cancel(); }
    @Inject(method = FIRST, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;push()V", shift = At.Shift.AFTER))
    private void analyzer$transform(AbstractClientPlayerEntity player, float tick, float pitch, Hand hand, float swing, ItemStack item, float equip, MatrixStack matrices, VertexConsumerProvider vertices, int light, CallbackInfo ci) { CosmeticRenderer.hand(matrices, hand); }
    @ModifyVariable(method = FIRST, at = @At("HEAD"), argsOnly = true, index = 5)
    private float analyzer$swingProgress(float original) { return CosmeticRenderer.swing(original); }
    @ModifyVariable(method = FIRST, at = @At("HEAD"), argsOnly = true, index = 7)
    private float analyzer$equipProgress(float original) { return CosmeticRenderer.equip(original); }
    @Inject(method = "applySwingOffset", at = @At("HEAD"), cancellable = true)
    private void analyzer$swing(MatrixStack matrices, Arm arm, float swing, CallbackInfo ci) { if (CosmeticRenderer.customSwing(matrices, arm, swing)) ci.cancel(); }
    @Inject(method = "applyEatOrDrinkTransformation", at = @At("HEAD"), cancellable = true)
    private void analyzer$eat(MatrixStack matrices, float tick, Arm arm, ItemStack item, PlayerEntity player, CallbackInfo ci) { if (CosmeticRenderer.cancelEat()) ci.cancel(); }
    @Redirect(method = "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/item/ItemRenderer;renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/world/World;III)V"))
    private void analyzer$item(ItemRenderer renderer, LivingEntity entity, ItemStack item, ModelTransformationMode mode, boolean left, MatrixStack matrices, VertexConsumerProvider vertices, World world, int light, int overlay, int seed) {
        if (!CosmeticRenderer.on("hand-tweaks") || mode != ModelTransformationMode.FIRST_PERSON_LEFT_HAND && mode != ModelTransformationMode.FIRST_PERSON_RIGHT_HAND) { renderer.renderItem(entity, item, mode, left, matrices, vertices, world, light, overlay, seed); return; }
        boolean main = entity.getMainArm() == (left ? Arm.LEFT : Arm.RIGHT);
        matrices.push();
        try { CosmeticRenderer.heldItem(matrices, main); renderer.renderItem(entity, item, mode, left, matrices, vertices, world, light, overlay, seed); }
        finally { matrices.pop(); }
    }
}
