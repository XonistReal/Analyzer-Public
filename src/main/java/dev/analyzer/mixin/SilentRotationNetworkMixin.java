package dev.analyzer.mixin;

import dev.analyzer.gameplay.SilentRotation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.network.DisconnectionInfo;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observe actual ordinary transmission after delegation; this is not an acknowledgement. */
@Mixin(ClientCommonNetworkHandler.class)
public abstract class SilentRotationNetworkMixin {
    @Inject(method = "sendPacket", at = @At("RETURN"))
    private void analyzer$sentLook(Packet<?> packet, CallbackInfo callback) { SilentRotation.sent(packet); }
    @Inject(method = "onDisconnected", at = @At("HEAD"))
    private void analyzer$lostConnection(DisconnectionInfo info, CallbackInfo callback) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.isOnThread()) SilentRotation.correctedOrDisconnected();
    }
}
