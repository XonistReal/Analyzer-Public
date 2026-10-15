package dev.analyzer.automation;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeavesBlock;
import dev.analyzer.AnalyzerClient;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Bounded recovery from natural tree canopies, using observed routes, continuous vines or rooted timber. */
final class NativeGroundRecovery {
    record Result(boolean done, boolean blocked, String detail, double progress) { }
    record Diagnostics(String mode, int climbs, int cuts, double startY, double lowestY) { }
    private record Vine(BlockPos column, BlockPos approach, int groundY, int topY) { }
    private record Timber(BlockPos top, int groundY) { }
    private enum Mode { WALK, FIND_VINE, APPROACH_VINE, ENTER_VINE, DESCEND_VINE, FIND_TIMBER, APPROACH_TIMBER, CUT }
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final NativeInputLease inputs;
    private final AutomationNavigator route;
    private final Box protectedBounds;
    private AutomationNavigator.MotionProfile motion;
    private Mode mode = Mode.WALK;
    private final List<BlockPos> walking = new ArrayList<>();
    private final List<Vine> vines = new ArrayList<>();
    private final List<Timber> timber = new ArrayList<>();
    private int nextWalk, nextVine, nextTimber, ticks, stalled, cooldown, cuts, climbs, aimAge = -1, breakTicks;
    private int originalSlot = -1, ownedSlot = -1;
    private BlockPos breaking;
    private Vine vine;
    private Timber trunk;
    private final double startY;
    private double lowestY;
    private Vec3d lastPosition;
    private String failure = "";
    private boolean paused;

    NativeGroundRecovery(Object group, AutomationNavigator.MotionProfile motion, Box protectedBounds) {
        this.inputs = new NativeInputLease(group); this.route = new AutomationNavigator(group, false);
        this.motion = motion; this.protectedBounds = protectedBounds; route.configureMotion(motion);
        startY = lowestY = mc.player.getY(); lastPosition = mc.player.getPos(); originalSlot = mc.player.getInventory().selectedSlot;
        survey();
    }
    void configureMotion(AutomationNavigator.MotionProfile profile) { motion = profile; route.configureMotion(profile); }
    Diagnostics diagnostics() { return new Diagnostics(mode.name(), climbs, cuts, startY, lowestY); }
    void pause() { paused = true; inputs.close(); route.pause(); cancelBreaking(); }
    void resume() { paused = false; if (route.status().paused()) route.resume(); aimAge = -1; }
    void stop() { inputs.close(); route.cancel(); cancelBreaking(); if (ownedSlot >= 0 && mc.player != null && mc.player.getInventory().selectedSlot == ownedSlot) mc.player.getInventory().selectedSlot = originalSlot; }
    Result tick() {
        if (paused) return result(false, false, "Ground recovery paused");
        if (!failure.isEmpty()) return result(false, true, failure);
        if (++ticks > 2400) return result(false, true, "Ground recovery exhausted its bounded route and interaction time. No unchecked drop was attempted.");
        lowestY = Math.min(lowestY, mc.player.getY());
        if (stable(mc, mc.player.getBlockPos()) && mc.player.isOnGround()) {
            if (breaking != null && mc.world.getBlockState(breaking).isAir()) cuts++;
            stop(); return result(true, false, "Reached dry, stable work ground; continuing the original task");
        }
        if (mc.currentScreen != null) { inputs.close(); cancelBreaking(); return result(false, false, "Close the menu to continue ground recovery"); }
        if (lastPosition.squaredDistanceTo(mc.player.getPos()) > .015) { stalled = 0; lastPosition = mc.player.getPos(); } else stalled++;
        switch (mode) {
            case WALK -> {
                if (!route.status().owned()) {
                    if (nextWalk >= walking.size()) { mode = Mode.FIND_VINE; break; }
                    route.clearStatus(); BlockPos target = walking.get(nextWalk++);
                    if (!route.travelForInteraction(target)) break;
                }
                route.tick();
                if (route.status().failed() || route.status().complete()) { route.cancel(); route.clearStatus(); }
                return result(false, false, "Checking a supported walking descent from the tree canopy");
            }
            case FIND_VINE -> {
                route.cancel(); inputs.close();
                if (nextVine >= vines.size()) { mode = Mode.FIND_TIMBER; break; }
                vine = vines.get(nextVine++);
                if (!safeVine(vine)) break;
                route.clearStatus();
                if (route.travelForInteraction(vine.approach())) { mode = Mode.APPROACH_VINE; stalled = 0; }
            }
            case APPROACH_VINE -> {
                route.tick();
                if (route.status().failed()) { route.cancel(); mode = Mode.FIND_VINE; }
                else if (route.status().complete()) { route.cancel(); inputs.close(); mode = Mode.ENTER_VINE; stalled = 0; }
                return result(false, false, "Approaching a freshly verified continuous vine or ladder to the ground");
            }
            case ENTER_VINE, DESCEND_VINE -> {
                if (!safeVine(vine)) return result(false, true, "The observed climbable descent changed. Movement stopped instead of stepping into an unchecked fall.");
                Vec3d destination = new Vec3d(vine.column().getX() + .5, mc.player.getY(), vine.column().getZ() + .5);
                if (mode == Mode.ENTER_VINE) {
                    AutomationMotion.align(destination.add(0, -.6, 0), motion);
                    if (mc.player.isClimbing()) { mode = Mode.DESCEND_VINE; climbs++; stalled = 0; }
                }
                // Ordinary inputs hold the player inside the actual climbable column; gravity performs the descent.
                steerColumn(destination);
                if (stalled > 100) return result(false, true, "The server did not allow the checked vine descent. No jump or long fall was attempted.");
                if (mc.player.getY() < vine.groundY() - .1 || Math.hypot(mc.player.getX() - destination.x, mc.player.getZ() - destination.z) > 1.25)
                    return result(false, true, "The player left the checked climbable column; recovery released its controls.");
                return result(false, false, "Descending the continuous vine or ladder with normal movement");
            }
            case FIND_TIMBER -> {
                route.cancel(); inputs.close();
                if (nextTimber >= timber.size()) return result(false, true, "No loaded walking route, continuous climbable column or verified rooted tree descent was safe. Move to a supported access point and Resume.");
                trunk = timber.get(nextTimber++);
                if (!safeTimber(trunk)) break;
                route.clearStatus();
                if (route.travelForInteraction(trunk.top().up())) { mode = Mode.APPROACH_TIMBER; stalled = 0; }
            }
            case APPROACH_TIMBER -> {
                route.tick();
                if (route.status().failed()) { route.cancel(); mode = Mode.FIND_TIMBER; }
                else if (route.status().complete()) { route.cancel(); inputs.close(); mode = Mode.CUT; stalled = 0; }
                return result(false, false, "Approaching the center of a verified natural rooted trunk");
            }
            case CUT -> { return cut(); }
        }
        return result(false, false, "Surveying bounded natural descent alternatives");
    }
    private void steerColumn(Vec3d target) {
        Vec3d delta = target.subtract(mc.player.getPos()), velocity = mc.player.getVelocity();
        double ax = Math.clamp(delta.x * .4 - velocity.x * 1.7, -.12, .12);
        double az = Math.clamp(delta.z * .4 - velocity.z * 1.7, -.12, .12);
        double yaw = Math.toRadians(mc.player.getYaw());
        double forward = -Math.sin(yaw) * ax + Math.cos(yaw) * az, left = Math.cos(yaw) * ax + Math.sin(yaw) * az;
        inputs.steer(forward > .008, forward < -.008, left > .008, left < -.008, false);
    }
    private Result cut() {
        inputs.close();
        if (cooldown > 0) { cooldown--; return result(false, false, "Settling after a checked one-block tree descent"); }
        if (!mc.player.isOnGround()) return result(false, false, "Waiting for the actual lower support before the next tree cut");
        BlockPos floor = mc.player.getBlockPos().down();
        if (breaking != null && !mc.world.getBlockState(breaking).isIn(BlockTags.LOGS) && !naturalLeaves(mc.world.getBlockState(breaking))) {
            cuts++; cancelBreaking(); cooldown = motion.breakDelayTicks(); return result(false, false, "Natural tree block removed; verifying the actual lower landing");
        }
        if (floor.getX() != trunk.top().getX() || floor.getZ() != trunk.top().getZ() || !safeTimber(trunk)
                || !naturalTreeBlock(mc.world.getBlockState(floor)) || !solid(mc, floor.down()) || excluded(floor))
            return result(false, true, "The rooted tree column lost its checked one-block support. No unsafe underfoot cut was attempted.");
        if (Math.hypot(mc.player.getX() - floor.getX() - .5, mc.player.getZ() - floor.getZ() - .5) > .16)
            return result(false, true, "The player moved off the verified trunk center before a cut. Resume from a supported position.");
        if (!GroundRecoveryPolicy.underfootCut(1, true, support(floor), support(floor.down())))
            return result(false, true, "The next tree cut does not have a freshly verified one-block landing");
        selectAxe();
        if (!AutomationMotion.align(floor.toCenterPos(), motion)) { cancelBreaking(); return result(false, false, "Turning toward the verified underfoot tree block"); }
        if (aimAge < 0) { aimAge = mc.player.age; return result(false, false, "Waiting for normal camera movement to reach the server"); }
        if (mc.player.age <= aimAge) return result(false, false, "Waiting for the next normal interaction tick");
        var hit = mc.player.raycast(mc.player.getBlockInteractionRange(), 1, false);
        if (!(hit instanceof BlockHitResult block) || !block.getBlockPos().equals(floor)) return result(false, true, "The checked tree block is not under the actual crosshair");
        if (!floor.equals(breaking)) {
            cancelBreaking(); breaking = floor.toImmutable(); aimAge = mc.player.age; breakTicks = 0;
            if (!mc.interactionManager.attackBlock(floor, block.getSide())) return result(false, true, "The server rejected the tree descent cut");
        } else mc.interactionManager.updateBlockBreakingProgress(floor, block.getSide());
        mc.player.swingHand(Hand.MAIN_HAND);
        if (++breakTicks > 600) return result(false, true, "The natural tree block did not break with normal timing. Check area protection and tools.");
        return result(false, false, "Cutting one verified rooted tree block above a solid landing");
    }
    private void selectAxe() {
        int choice = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (Registries.ITEM.getId(stack.getItem()).getPath().endsWith("_axe") && stack.getMaxDamage() - stack.getDamage() > 1) { choice = i; break; }
            if (stack.isEmpty() && choice < 0) choice = i;
        }
        if (choice >= 0) { ownedSlot = choice; mc.player.getInventory().selectedSlot = choice; }
    }
    private void cancelBreaking() { if (breaking != null && mc.interactionManager != null) mc.interactionManager.cancelBlockBreaking(); breaking = null; aimAge = -1; breakTicks = 0; }
    private Result result(boolean done, boolean blocked, String detail) { return new Result(done, blocked, detail, Math.clamp((startY - mc.player.getY()) / Math.max(1, startY - lowestGround()), 0, 1)); }
    private double lowestGround() { return vine != null ? vine.groundY() : trunk != null ? trunk.groundY() : startY - 16; }
    private boolean excluded(BlockPos pos) { return protectedBounds != null && protectedBounds.intersects(new Box(pos)); }
    private GroundRecoveryPolicy.Support support(BlockPos pos) {
        var state = mc.world.getBlockState(pos);
        return new GroundRecoveryPolicy.Support(loaded(pos), state.getFluidState().isEmpty(), NativeTerrain.hazard(state), excluded(pos), state.hasBlockEntity(), naturalTreeBlock(state), solid(mc, pos));
    }
    private boolean loaded(BlockPos pos) { return mc.world.isInBuildLimit(pos) && mc.world.getWorldBorder().contains(pos) && mc.world.isChunkLoaded(pos); }
    private boolean safeVine(Vine candidate) {
        int top = candidate.topY();
        if (!GroundRecoveryPolicy.climbColumn(top - candidate.groundY() + 1, true,
                stable(mc, new BlockPos(candidate.column().getX(), candidate.groundY(), candidate.column().getZ())))) return false;
        for (int y = candidate.groundY(); y <= top; y++) {
            BlockPos pos = new BlockPos(candidate.column().getX(), y, candidate.column().getZ());
            if (!loaded(pos) || excluded(pos) || !mc.world.getBlockState(pos).isIn(BlockTags.CLIMBABLE) || !clear(mc, pos)) return false;
        }
        return clear(mc, candidate.column().up()) && !excluded(candidate.approach());
    }
    private boolean safeTimber(Timber candidate) {
        if (!naturalSoil(mc.world.getBlockState(new BlockPos(candidate.top().getX(), candidate.groundY() - 1, candidate.top().getZ())))) return false;
        int ceiling = Math.min(candidate.top().getY(), mc.player.getBlockY() - 1);
        for (int y = candidate.groundY(); y <= ceiling; y++) {
            BlockPos pos = new BlockPos(candidate.top().getX(), y, candidate.top().getZ());
            if (!loaded(pos) || excluded(pos) || !naturalTreeBlock(mc.world.getBlockState(pos)) || !mc.world.getBlockState(pos).getFluidState().isEmpty() || mc.world.getBlockState(pos).hasBlockEntity()) return false;
            for (Direction direction : Direction.values()) {
                BlockPos side = pos.offset(direction);
                if (!loaded(side) || !mc.world.getBlockState(side).getFluidState().isEmpty() || NativeTerrain.hazard(mc.world.getBlockState(side)) || mc.world.getBlockState(side).hasBlockEntity()) return false;
            }
        }
        return true;
    }
    private void survey() {
        BlockPos at = mc.player.getBlockPos(); int top = at.getY() - 1;
        for (int x = at.getX() - 8; x <= at.getX() + 8; x++) for (int z = at.getZ() - 8; z <= at.getZ() + 8; z++) {
            for (int y = top; y >= top - 24; y--) {
                BlockPos pos = new BlockPos(x, y, z);
                if (!loaded(pos)) break;
                if (stable(mc, pos.up())) { if (top - y >= 1) walking.add(pos.up()); break; }
            }
            BlockPos column = new BlockPos(x, top, z);
            if (loaded(column) && mc.world.getBlockState(column).isIn(BlockTags.CLIMBABLE)) {
                int bottom = top;
                while (bottom > top - 24 && loaded(new BlockPos(x, bottom - 1, z)) && mc.world.getBlockState(new BlockPos(x, bottom - 1, z)).isIn(BlockTags.CLIMBABLE)) bottom--;
                for (Direction side : Direction.Type.HORIZONTAL) {
                    BlockPos approach = column.offset(side).up();
                    Vine candidate = new Vine(column, approach, bottom, top);
                    if (solid(mc, approach.down()) && clear(mc, approach) && safeVine(candidate)) vines.add(candidate);
                }
            }
            if (loaded(column) && naturalTreeBlock(mc.world.getBlockState(column))) {
                int bottom = top, logs = 0;
                while (bottom >= top - 24 && loaded(new BlockPos(x, bottom, z)) && naturalTreeBlock(mc.world.getBlockState(new BlockPos(x, bottom, z)))) {
                    if (mc.world.getBlockState(new BlockPos(x, bottom, z)).isIn(BlockTags.LOGS)) logs++;
                    bottom--;
                }
                int leaves = 0;
                for (BlockPos near : BlockPos.iterate(column.add(-2, -2, -2), column.add(2, 1, 2))) if (loaded(near) && naturalLeaves(mc.world.getBlockState(near))) leaves++;
                Timber candidate = new Timber(column, bottom + 1);
                if (GroundRecoveryPolicy.treeColumn(top - bottom, logs, leaves, loaded(new BlockPos(x, bottom, z)) && naturalSoil(mc.world.getBlockState(new BlockPos(x, bottom, z))), safeTimber(candidate))) timber.add(candidate);
            }
        }
        walking.sort(Comparator.comparingDouble(pos -> pos.getSquaredDistance(at))); if (walking.size() > 3) walking.subList(3, walking.size()).clear();
        vines.sort(Comparator.comparingDouble(value -> value.approach().getSquaredDistance(at)));
        timber.sort(Comparator.comparingDouble(value -> value.top().getSquaredDistance(at)));
        if (FabricLoader.getInstance().isDevelopmentEnvironment() && Boolean.getBoolean("analyzer.nativeAutomationSmoke"))
            AnalyzerClient.LOGGER.info("Native ground survey: {} walking exits, {} continuous vines, {} rooted trunks", walking.size(), vines.size(), timber.size());
    }
    static boolean needsRecovery(MinecraftClient mc) {
        if (mc.world == null || mc.player == null || !mc.player.isOnGround()) return false;
        BlockPos floor = mc.player.getBlockPos().down(); BlockState support = mc.world.getBlockState(floor);
        if (!naturalTreeBlock(support)) return false;
        for (int d = 1; d <= 24; d++) {
            BlockPos pos = floor.down(d);
            if (!mc.world.isChunkLoaded(pos)) return false;
            if (solid(mc, pos) && !naturalTreeBlock(mc.world.getBlockState(pos))) return d >= 3;
        }
        return true;
    }
    static boolean stable(MinecraftClient mc, BlockPos feet) { return solid(mc, feet.down()) && !naturalTreeBlock(mc.world.getBlockState(feet.down())) && clear(mc, feet); }
    private static boolean solid(MinecraftClient mc, BlockPos pos) {
        if (!mc.world.isInBuildLimit(pos) || !mc.world.getWorldBorder().contains(pos) || !mc.world.isChunkLoaded(pos)) return false;
        BlockState state = mc.world.getBlockState(pos);
        if (NativeTerrain.hazard(state) || !state.getFluidState().isEmpty()) return false;
        if (state.isSideSolidFullSquare(mc.world, pos, Direction.UP)) return true;
        // Leaves support walking even though vanilla deliberately marks their sides non-solid.
        return naturalLeaves(state) && state.getCollisionShape(mc.world, pos).getBoundingBoxes().stream().anyMatch(box ->
                box.maxY >= .999 && box.minX <= .201 && box.maxX >= .799 && box.minZ <= .201 && box.maxZ >= .799);
    }
    private static boolean clear(MinecraftClient mc, BlockPos feet) {
        for (int y = 0; y <= 1; y++) { BlockPos pos = feet.up(y); if (!mc.world.isInBuildLimit(pos) || !mc.world.getWorldBorder().contains(pos) || !mc.world.isChunkLoaded(pos) || NativeTerrain.hazard(mc.world.getBlockState(pos)) || !mc.world.getBlockState(pos).getFluidState().isEmpty()) return false; }
        return mc.world.isSpaceEmpty(mc.player, new Box(feet.getX() + .201, feet.getY() + .001, feet.getZ() + .201, feet.getX() + .799, feet.getY() + 1.799, feet.getZ() + .799));
    }
    private static boolean naturalTreeBlock(BlockState state) { return state.isIn(BlockTags.LOGS) || naturalLeaves(state); }
    private static boolean naturalLeaves(BlockState state) { return state.isIn(BlockTags.LEAVES) && state.contains(LeavesBlock.PERSISTENT) && !state.get(LeavesBlock.PERSISTENT); }
    private static boolean naturalSoil(BlockState state) { return state.isOf(Blocks.DIRT) || state.isOf(Blocks.GRASS_BLOCK) || state.isOf(Blocks.PODZOL) || state.isOf(Blocks.ROOTED_DIRT) || state.isOf(Blocks.COARSE_DIRT) || state.isOf(Blocks.MYCELIUM); }
}
