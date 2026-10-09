package dev.analyzer.mixin;
import dev.analyzer.AnalyzerClient;
import dev.analyzer.gameplay.UtilityEngine;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.item.BlockItem;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class UtilityInteractionMixin {
    @Shadow private BlockPos currentBreakingPos;
    @Shadow private boolean breakingBlock;
    @Unique private BlockState analyzer$brokenState;
    @Unique private boolean analyzer$temporarySneak;
    @Inject(method="cancelBlockBreaking", at=@At("HEAD"), cancellable=true)
    private void analyzer$keepOwnedMining(CallbackInfo ci) {
        if (breakingBlock && UtilityEngine.preservesIdleMining(currentBreakingPos)) ci.cancel();
    }
    @Inject(method="breakBlock", at=@At("HEAD"))
    private void analyzer$beforeBreak(BlockPos pos, CallbackInfoReturnable<Boolean> ci) {
        var world = MinecraftClient.getInstance().world;
        analyzer$brokenState = world == null ? null : world.getBlockState(pos);
    }
    @Inject(method="breakBlock", at=@At("RETURN"))
    private void analyzer$afterBreak(BlockPos pos, CallbackInfoReturnable<Boolean> ci) {
        if (ci.getReturnValue() && analyzer$brokenState != null) UtilityEngine.onBroken(pos, analyzer$brokenState);
    }
    @Inject(method="interactBlock", at=@At("HEAD"))
    private void analyzer$beforeUse(ClientPlayerEntity player, Hand hand, BlockHitResult hit, CallbackInfoReturnable<ActionResult> ci) {
        analyzer$temporarySneak = AnalyzerClient.CORE != null && AnalyzerClient.CORE.isEnabled("no-block-interact")
            && !player.isSneaking() && player.getStackInHand(hand).getItem() instanceof BlockItem;
        if (analyzer$temporarySneak) {
            player.setSneaking(true);
            player.networkHandler.sendPacket(new ClientCommandC2SPacket(player, ClientCommandC2SPacket.Mode.PRESS_SHIFT_KEY));
        }
    }
    @Inject(method="interactBlock", at=@At("RETURN"))
    private void analyzer$afterUse(ClientPlayerEntity player, Hand hand, BlockHitResult hit, CallbackInfoReturnable<ActionResult> ci) {
        if (analyzer$temporarySneak) {
            player.setSneaking(false);
            player.networkHandler.sendPacket(new ClientCommandC2SPacket(player, ClientCommandC2SPacket.Mode.RELEASE_SHIFT_KEY));
            analyzer$temporarySneak = false;
        }
    }
}
