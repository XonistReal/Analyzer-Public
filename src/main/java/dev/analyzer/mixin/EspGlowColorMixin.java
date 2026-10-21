package dev.analyzer.mixin;

import dev.analyzer.render.EspGlow;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Entity ESP Glow: the native outline uses the ESP color instead of the scoreboard team color. */
@Mixin(Entity.class)
public abstract class EspGlowColorMixin {
    @Inject(method = "getTeamColorValue", at = @At("HEAD"), cancellable = true)
    private void analyzer$espGlowColor(CallbackInfoReturnable<Integer> cir) {
        int color = EspGlow.color((Entity) (Object) this);
        if (color != -1) cir.setReturnValue(color);
    }
}
