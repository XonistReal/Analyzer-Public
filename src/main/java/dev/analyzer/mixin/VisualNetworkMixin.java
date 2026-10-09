package dev.analyzer.mixin;

import dev.analyzer.render.VisualEffects;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.network.packet.s2c.play.ItemPickupAnimationS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Captures actual packets before vanilla removes pickup entities, on the client thread only. */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class VisualNetworkMixin {
    @Inject(method = "onItemPickupAnimation", at = @At("HEAD"))
    private void analyzer$pickup(ItemPickupAnimationS2CPacket packet, CallbackInfo callback) {
        MinecraftClient client = MinecraftClient.getInstance();
        // NetworkThreadUtils re-enters this handler on the client thread. Skip its initial Netty invocation.
        if (client.isOnThread() && client.world != null) VisualEffects.onPickup(client.world.getEntityById(packet.getEntityId()),
                client.world.getEntityById(packet.getCollectorEntityId()));
    }
    @Inject(method = "onEntityStatus", at = @At("HEAD"))
    private void analyzer$status(EntityStatusS2CPacket packet, CallbackInfo callback) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.isOnThread() && client.world != null) VisualEffects.onEntityStatus(packet.getEntity(client.world), packet.getStatus());
    }
    @Inject(method = "onExplosion", at = @At("HEAD"))
    private void analyzer$explosion(ExplosionS2CPacket packet, CallbackInfo callback) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.isOnThread() && client.world != null) VisualEffects.onExplosion(packet.center());
    }
}
