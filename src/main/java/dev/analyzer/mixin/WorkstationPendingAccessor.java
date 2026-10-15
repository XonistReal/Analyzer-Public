package dev.analyzer.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.network.PendingUpdateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PendingUpdateManager.class)
public interface WorkstationPendingAccessor {
    @Accessor("blockPosToPendingUpdate") Long2ObjectOpenHashMap<?> analyzer$pendingBlocks();
}
