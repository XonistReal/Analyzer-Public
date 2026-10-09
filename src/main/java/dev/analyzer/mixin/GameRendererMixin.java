package dev.analyzer.mixin;
import dev.analyzer.effects.GpuEffects;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "renderWorld", at = @At("RETURN"))
    private void analyzer$worldEffects(RenderTickCounter tickCounter, CallbackInfo ci) { GpuEffects.renderWorld(); }
}
