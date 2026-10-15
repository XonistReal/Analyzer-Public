package dev.analyzer.mixin;

import dev.analyzer.gameplay.GameplayEngine;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observe the real local totem event without changing or inventing network packets. */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class CombatNetworkMixin {
    @Inject(method = "onEntityStatus", at = @At("HEAD"))
    private void analyzer$combatStatus(EntityStatusS2CPacket packet, CallbackInfo callback) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.isOnThread() && client.world != null && packet.getStatus() == 35 && packet.getEntity(client.world) == client.player)
            GameplayEngine.localTotemPopped();
    }
}
