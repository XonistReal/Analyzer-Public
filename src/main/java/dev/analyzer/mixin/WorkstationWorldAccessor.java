package dev.analyzer.mixin;

import net.minecraft.client.network.PendingUpdateManager;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only access to ordinary vanilla prediction acknowledgements. */
@Mixin(ClientWorld.class)
public interface WorkstationWorldAccessor {
    @Accessor("pendingUpdateManager") PendingUpdateManager analyzer$pendingUpdates();
}
