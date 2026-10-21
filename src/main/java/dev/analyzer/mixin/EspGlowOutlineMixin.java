package dev.analyzer.mixin;

import dev.analyzer.render.EspGlow;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Entity ESP Glow: entities it accepts are outlined by the native entity-outline pass. */
@Mixin(MinecraftClient.class)
public abstract class EspGlowOutlineMixin {
    @Inject(method = "hasOutline", at = @At("HEAD"), cancellable = true)
    private void analyzer$espGlow(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (EspGlow.color(entity) != -1) cir.setReturnValue(true);
    }
}
