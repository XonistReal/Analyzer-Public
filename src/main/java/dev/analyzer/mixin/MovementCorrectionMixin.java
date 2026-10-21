package dev.analyzer.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.analyzer.gameplay.SilentRotation;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Fight Bot movement correction: velocity from movement input reads the transmitted lease yaw for
 * one requested player tick. Only the local player is affected; camera and rendering are untouched.
 * Optional (require = 0): if a future mapping moves this call, the bot falls back to camera-relative keys.
 */
@Mixin(Entity.class)
public abstract class MovementCorrectionMixin {
    @ModifyExpressionValue(method = "updateVelocity", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getYaw()F"))
    private float analyzer$correctedVelocityYaw(float original) {
        return SilentRotation.physicsYaw(this, original);
    }
}
