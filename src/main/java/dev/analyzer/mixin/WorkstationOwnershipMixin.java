package dev.analyzer.mixin;

import dev.analyzer.automation.WorkstationController;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientWorld.class)
public abstract class WorkstationOwnershipMixin {
    @Inject(method = "handleBlockUpdate", at = @At("HEAD"))
    private void analyzer$invalidateReplacedTable(BlockPos pos, BlockState state, int flags, CallbackInfo ci) {
        WorkstationController.serverBlockUpdate(this, pos, state);
    }
}
