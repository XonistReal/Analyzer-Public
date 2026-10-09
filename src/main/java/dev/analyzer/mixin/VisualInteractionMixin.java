package dev.analyzer.mixin;

import dev.analyzer.render.VisualEffects;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayDeque;
import java.util.Deque;

/** observes vanilla accepted results; never cancels or rewrites the interaction. */
@Mixin(ClientPlayerInteractionManager.class)
public abstract class VisualInteractionMixin {
    @Shadow private float currentBreakingProgress;
    @Shadow private boolean breakingBlock;
    @Shadow private BlockPos currentBreakingPos;
    // Stacks preserve the outer snapshot if another module performs a nested interaction.
    @Unique private final Deque<Object[]> analyzer$placements = new ArrayDeque<>();
    @Unique private final Deque<Object[]> analyzer$breaks = new ArrayDeque<>();

    @Inject(method = "interactBlock", at = @At("HEAD"))
    private void analyzer$beforePlace(ClientPlayerEntity player, Hand hand, BlockHitResult hit, CallbackInfoReturnable<ActionResult> callback) {
        MinecraftClient client = MinecraftClient.getInstance();
        Object[] snapshot = new Object[]{null, null};
        if (client.world != null && player.getStackInHand(hand).getItem() instanceof BlockItem) {
            BlockPos position = new ItemPlacementContext(player, hand, player.getStackInHand(hand), hit).getBlockPos().toImmutable();
            snapshot[0] = position; snapshot[1] = client.world.getBlockState(position);
        }
        analyzer$placements.addLast(snapshot);
    }

    @Inject(method = "interactBlock", at = @At("RETURN"))
    private void analyzer$afterPlace(ClientPlayerEntity player, Hand hand, BlockHitResult hit, CallbackInfoReturnable<ActionResult> callback) {
        Object[] snapshot = analyzer$placements.pollLast(); MinecraftClient client = MinecraftClient.getInstance();
        if (snapshot == null || snapshot[0] == null || client.world == null || !callback.getReturnValue().isAccepted()) return;
        BlockPos position = (BlockPos) snapshot[0]; BlockState before = (BlockState) snapshot[1], after = client.world.getBlockState(position);
        if (!after.isAir() && !after.equals(before)) VisualEffects.onPlaced(position, after);
    }

    @Inject(method = "breakBlock", at = @At("HEAD"))
    private void analyzer$beforeBreak(BlockPos position, CallbackInfoReturnable<Boolean> callback) {
        MinecraftClient client = MinecraftClient.getInstance();
        analyzer$breaks.addLast(new Object[]{position.toImmutable(), client.world == null ? null : client.world.getBlockState(position)});
    }

    @Inject(method = "breakBlock", at = @At("RETURN"))
    private void analyzer$afterBreak(BlockPos position, CallbackInfoReturnable<Boolean> callback) {
        Object[] snapshot = analyzer$breaks.pollLast(); MinecraftClient client = MinecraftClient.getInstance();
        if (snapshot == null || snapshot[1] == null || client.world == null || !callback.getReturnValue()) return;
        BlockPos captured = (BlockPos) snapshot[0]; BlockState before = (BlockState) snapshot[1];
        if (!before.isAir() && !client.world.getBlockState(captured).equals(before)) VisualEffects.onBroken(captured, before);
    }

    @Inject(method = "updateBlockBreakingProgress", at = @At("RETURN"))
    private void analyzer$mining(BlockPos position, Direction direction, CallbackInfoReturnable<Boolean> callback) {
        VisualEffects.onMining(currentBreakingPos, currentBreakingProgress, breakingBlock && callback.getReturnValue());
    }

    @Inject(method = "cancelBlockBreaking", at = @At("RETURN"))
    private void analyzer$cancelMining(CallbackInfo callback) { VisualEffects.onMining(BlockPos.ORIGIN, 0, false); }

    @Inject(method = "attackEntity", at = @At("TAIL"))
    private void analyzer$attack(PlayerEntity player, Entity target, CallbackInfo callback) { VisualEffects.onAttack(target); }
}
