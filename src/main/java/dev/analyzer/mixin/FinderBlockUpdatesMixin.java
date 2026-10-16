package dev.analyzer.mixin;

import dev.analyzer.finder.FinderEngine;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Single-block and chunk-delta packets both reach the native received-state update path. */
@Mixin(ClientWorld.class)
public abstract class FinderBlockUpdatesMixin {
    @Unique private BlockState analyzer$finderPreviousState;

    @Inject(method = "handleBlockUpdate", at = @At("HEAD"))
    private void analyzer$finderBeforeBlockChanged(BlockPos pos, BlockState state, int flags, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        analyzer$finderPreviousState = ((Object) this == client.world && client.isOnThread())
                ? ((ClientWorld) (Object) this).getBlockState(pos) : null;
    }

    @Inject(method = "handleBlockUpdate", at = @At("TAIL"))
    private void analyzer$finderBlockChanged(BlockPos pos, BlockState state, int flags, CallbackInfo ci) {
        BlockState previous = analyzer$finderPreviousState;
        analyzer$finderPreviousState = null;
        if ((Object) this == MinecraftClient.getInstance().world) FinderEngine.blockChanged(pos, previous, state);
    }
}
