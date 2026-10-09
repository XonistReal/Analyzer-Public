package dev.analyzer.mixin;
import dev.analyzer.gameplay.GameplayEngine;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(PlayerEntity.class)
public abstract class SafeWalkMixin {
    @Inject(method = "clipAtLedge", at = @At("RETURN"), cancellable = true)
    private void analyzer$safeWalk(CallbackInfoReturnable<Boolean> callback) {
        if ((Object) this == MinecraftClient.getInstance().player && GameplayEngine.safeWalkActive()) callback.setReturnValue(true);
    }
}
