package dev.analyzer.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.fluid.FlowableFluid;
import net.minecraft.fluid.Fluid;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Read-only vanilla flow predicates; no fluid state, world data or simulation is changed. */
@Mixin(FlowableFluid.class)
public interface SchematicFluidAccess {
    @Invoker("receivesFlow")
    static boolean analyzer$receivesFlow(Direction direction, BlockView world, BlockPos from, BlockState fromState, BlockPos to, BlockState toState) { throw new AssertionError(); }
    @Invoker("canFill")
    static boolean analyzer$canFill(BlockView world, BlockPos pos, BlockState state, Fluid fluid) { throw new AssertionError(); }
}
