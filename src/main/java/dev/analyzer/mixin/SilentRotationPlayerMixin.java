package dev.analyzer.mixin;

import dev.analyzer.gameplay.SilentRotation;
import dev.analyzer.gameplay.SilentRotationState;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Packet angle reads only: camera, movement input, model and interpolation remain untouched. */
@Mixin(ClientPlayerEntity.class)
public abstract class SilentRotationPlayerMixin implements SilentRotationState {
    @Shadow private float lastYaw;
    @Shadow private float lastPitch;
    @Override public void analyzer$recordSentLook(float yaw, float pitch) { lastYaw = yaw; lastPitch = pitch; }
    @Inject(method = "sendMovementPackets", at = @At("HEAD"))
    private void analyzer$validateRotation(CallbackInfo callback) {
        SilentRotation.beforeMovement((ClientPlayerEntity) (Object) this);
    }
    @Inject(method = "sendMovementPackets", at = @At("RETURN"))
    private void analyzer$finishedRotation(CallbackInfo callback) { SilentRotation.afterMovement(); }
    @Redirect(method = "sendMovementPackets", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getYaw()F"))
    private float analyzer$movementYaw(ClientPlayerEntity player) {
        return SilentRotation.movementYaw(player, player.getYaw());
    }
    @Redirect(method = "sendMovementPackets", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getPitch()F"))
    private float analyzer$movementPitch(ClientPlayerEntity player) {
        return SilentRotation.movementPitch(player, player.getPitch());
    }
}
