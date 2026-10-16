package dev.analyzer.mixin;

import dev.analyzer.gameplay.SilentRotation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Server correction and respawn always end rotation ownership without counter-packets. */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class SilentRotationCorrectionMixin {
    @Inject(method = "onPlayerPositionLook", at = @At("HEAD"))
    private void analyzer$correctedLook(PlayerPositionLookS2CPacket packet, CallbackInfo callback) {
        if (MinecraftClient.getInstance().isOnThread()) SilentRotation.correctedOrDisconnected();
    }
    @Inject(method = "onPlayerRespawn", at = @At("HEAD"))
    private void analyzer$respawned(PlayerRespawnS2CPacket packet, CallbackInfo callback) {
        if (MinecraftClient.getInstance().isOnThread()) SilentRotation.correctedOrDisconnected();
    }
}
