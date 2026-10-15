package dev.analyzer.mixin;

import dev.analyzer.render.XrayRenderer;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Block.class)
public abstract class XrayBlockFacesMixin {
    @Inject(method = "shouldDrawSide", at = @At("HEAD"), cancellable = true)
    private static void analyzer$xrayOreFaces(BlockState state, BlockState neighbor, Direction face, CallbackInfoReturnable<Boolean> cir) {if (XrayRenderer.selected(state)) cir.setReturnValue(true);}
}
