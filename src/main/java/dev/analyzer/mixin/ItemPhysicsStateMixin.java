package dev.analyzer.mixin;

import dev.analyzer.render.ItemCosmeticState;
import net.minecraft.client.render.entity.state.ItemEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ItemEntityRenderState.class)
public abstract class ItemPhysicsStateMixin implements ItemCosmeticState {
    @Unique private boolean analyzer$grounded, analyzer$water;
    @Unique private float analyzer$angle;
    @Override public boolean analyzer$grounded() { return analyzer$grounded; }
    @Override public boolean analyzer$water() { return analyzer$water; }
    @Override public float analyzer$angle() { return analyzer$angle; }
    @Override public void analyzer$itemPhysics(boolean grounded, boolean water, float angle) { analyzer$grounded = grounded; analyzer$water = water; analyzer$angle = angle; }
}
