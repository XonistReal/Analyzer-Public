package dev.analyzer.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.analyzer.gameplay.SilentRotation;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The sprint-jump boost follows the same corrected yaw as movement input. Optional, like the velocity hook. */
@Mixin(LivingEntity.class)
public abstract class MovementCorrectionJumpMixin {
    @ModifyExpressionValue(method = "jump", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getYaw()F"))
    private float analyzer$correctedJumpYaw(float original) {
        return SilentRotation.physicsYaw(this, original);
    }
}
