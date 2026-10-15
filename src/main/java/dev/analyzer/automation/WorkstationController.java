package dev.analyzer.automation;

import dev.analyzer.mixin.WorkstationPendingAccessor;
import dev.analyzer.mixin.WorkstationWorldAccessor;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Checked ordinary workstation interactions and recovery of this worker's acknowledged table placements. */
public final class WorkstationController {
    private static final Map<WorkstationController, Boolean> OBSERVERS = new WeakHashMap<>();
    private record Candidate(BlockPos target, BlockHitResult face, BlockPos stand) { }
    private record Placing(Candidate candidate, Block block, Item item, int inventoryBefore, int age, Object world) { }
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final AutomationNavigator navigator;
    private final InventoryAutomation inventory;
    private final Map<BlockPos, Object> tables = new LinkedHashMap<>();
    private final Set<String> rejected = new HashSet<>();
    private final Set<BlockPos> rejectedStands = new HashSet<>();
    private Candidate candidate;
    private Placing placing;
    private Object world;
    private BlockPos interactionTarget, routeStand, breaking, collecting;
    private Vec3d aim;
    private int alignedAge = -1, breakAge, pickupAge, pickupBefore, placementFailures, routeFailures, aimMisses;
    private int previousSlot = -1, selectedSlot = -1;
    private boolean breakStarted, ownsRoute;

    WorkstationController(AutomationNavigator navigator, InventoryAutomation inventory) { this.navigator = navigator; this.inventory = inventory; OBSERVERS.put(this, true); }
    /** Observe authoritative updates even while a task is stopped; a replacement never inherits old ownership. */
    public static void serverBlockUpdate(Object world, BlockPos pos, BlockState state) {
        for (var worker : List.copyOf(OBSERVERS.keySet())) {
            if (worker.tables.get(pos) != world) continue;
            if (!state.isOf(Blocks.CRAFTING_TABLE)) {
                if (pos.equals(worker.collecting) || worker.breakStarted && pos.equals(worker.breaking)) worker.removedTables.add(pos.toImmutable());
                else worker.tables.remove(pos);
            } else if (worker.removedTables.remove(pos)) {
                worker.tables.remove(pos);
                if (pos.equals(worker.collecting)) worker.collecting = null;
            }
        }
    }
    private final Set<BlockPos> removedTables = new HashSet<>();
    boolean hasOwnedTables() { clearChangedWorld(); return !tables.isEmpty() || placing != null && placing.block == Blocks.CRAFTING_TABLE; }
    boolean recoveryActive() { return collecting != null || breaking != null && tables.containsKey(breaking); }
    private void clearChangedWorld() {
        if (world != mc.world) { stop(); tables.clear(); removedTables.clear(); placing = null; collecting = null; world = mc.world; rejected.clear(); rejectedStands.clear(); placementFailures = routeFailures = 0; }
    }
    private boolean pending(BlockPos pos) {
        var manager = ((WorkstationWorldAccessor)mc.world).analyzer$pendingUpdates();
        return ((WorkstationPendingAccessor)manager).analyzer$pendingBlocks().containsKey(pos.asLong());
    }
    InventoryAutomation.Result observePlacement() {
        clearChangedWorld();
        if (placing == null) return null;
        if (placing.world != mc.world) { placing = null; return null; }
        BlockPos pos = placing.candidate.target;
        boolean acknowledged = mc.player.age - placing.age >= 4 && !pending(pos);
        if (acknowledged && mc.world.getBlockState(pos).isOf(placing.block)
                && (mc.player.getAbilities().creativeMode || InventoryAutomation.count(placing.item) < placing.inventoryBefore)) {
            if (placing.block == Blocks.CRAFTING_TABLE) tables.put(pos, mc.world);
            placing = null; candidate = null; rejected.clear(); placementFailures = 0; resetAim(); return null;
        }
        if (mc.player.age - placing.age > 100) {
            rejected.add(key(placing.candidate)); placing = null; candidate = null; resetAim();
            if (++placementFailures >= 4) return need("The server did not confirm workstation placement after four checked faces. Check region permissions, then Resume.");
            return waitFor("The workstation placement was not accepted; checking another face");
        }
        return waitFor("Waiting for the server to confirm the exact workstation block and consumed item");
    }
    InventoryAutomation.Result prepareGround() {
        clearChangedWorld();
        if (ownsRoute && !navigator.status().owned()) {
            ownsRoute = false;
            if (navigator.status().failed() && routeStand == null) return need(navigator.status().message());
            if (navigator.status().failed()) { rejectedStands.add(routeStand); routeFailures++; }
            navigator.clearStatus(); routeStand = null;
        }
        if (navigator.needsWorkGroundRecovery()) {
            if (!navigator.status().owned() && !navigator.recoverWorkGround()) return need(navigator.status().message());
            ownsRoute = true; return waitFor("Finding stable ground before using a workstation");
        }
        if (ownsRoute && navigator.status().owned()) return waitFor("Finishing the checked workstation approach");
        if (ownsRoute) {
            ownsRoute = false;
            if (navigator.status().failed()) { rejectedStands.add(routeStand); routeFailures++; }
            navigator.clearStatus(); routeStand = null;
        }
        return !mc.player.isOnGround() || mc.player.getVelocity().horizontalLength() > .035 ? waitFor("Settling on stable ground before a workstation interaction") : null;
    }
    InventoryAutomation.Result approach(BlockPos target) {
        InventoryAutomation.Result ground = prepareGround(); if (ground != null) return ground;
        if (!target.equals(interactionTarget)) { interactionTarget = target; rejectedStands.clear(); routeFailures = 0; resetAim(); }
        NativeMiningRay.Aim visible = NativeMiningRay.visibleAim(mc, target);
        if (visible != null && reachable(visible.hit().getPos())) return null;
        return routeTo(target);
    }
    BlockHitResult interactionHit(BlockPos target) {
        var visible = NativeMiningRay.visibleAim(mc, target);
        if (visible == null || !reachable(visible.hit().getPos())) return null;
        if (aim == null || !NativeMiningRay.reaches(mc, mc.player.getEyePos(), aim, target)) aim = visible.point();
        if (!readyAim(aim)) return null;
        var actual = mc.player.raycast(reach(), 1, false);
        return actual instanceof BlockHitResult hit && hit.getBlockPos().equals(target) ? hit : null;
    }
    private InventoryAutomation.Result routeTo(BlockPos target) {
        if (navigator.status().owned()) return waitFor("Walking to a visible workstation approach");
        if (routeFailures >= 8) return need("No checked workstation approach could be reached. Clear a walking route to " + target.toShortString() + ", then Resume.");
        BlockPos stand = null; double best = Double.MAX_VALUE;
        for (int y = -2; y <= 2; y++) for (int z = -3; z <= 3; z++) for (int x = -3; x <= 3; x++) {
            BlockPos test = target.add(x, y, z);
            if (rejectedStands.contains(test) || test.equals(target) && !mc.world.getBlockState(target).isAir() || !safeStand(test)) continue;
            Vec3d eye = Vec3d.ofBottomCenter(test).add(0, mc.player.getStandingEyeHeight(), 0);
            var visible = NativeMiningRay.visibleAim(mc, eye, target);
            if (visible == null || eye.squaredDistanceTo(visible.hit().getPos()) > reachSquared()) continue;
            double cost = mc.player.squaredDistanceTo(Vec3d.ofBottomCenter(test));
            if (cost < best) { best = cost; stand = test; }
        }
        if (stand == null) return need("No safe loaded standing cell has a clear view of the workstation. Clear a walking route, then Resume.");
        routeStand = stand; ownsRoute = navigator.travelForInteraction(stand);
        if (!ownsRoute) { rejectedStands.add(stand); routeFailures++; return waitFor("Checking a different workstation approach"); }
        return waitFor("Walking to a checked workstation interaction point");
    }
    InventoryAutomation.Result place(Block block, Item item, Box exclusion) {
        InventoryAutomation.Result observation = observePlacement(); if (observation != null) return observation;
        InventoryAutomation.Result ground = prepareGround(); if (ground != null) return ground;
        if (!(item instanceof BlockItem)) return need("The workstation is not an ordinary placeable block item.");
        InventoryAutomation.Result ready = inventory.prepareItem(item, 1); if (!ready.done()) return ready;
        if (candidate == null || !validTarget(candidate.target, exclusion) || !visible(candidate.face, mc.player.getEyePos())) candidate = chooseCandidate(exclusion);
        if (candidate == null) return need("No visible reachable workstation face was found nearby. Stand beside a clear solid block with room for a table, then Resume.");
        if (candidate.stand != null) {
            BlockPos stand = candidate.stand;
            if (!navigator.status().owned()) {
                routeStand = stand; ownsRoute = navigator.travelForInteraction(stand);
                if (!ownsRoute) { rejected.add(key(candidate)); candidate = null; return waitFor("Checking another workstation placement approach"); }
            }
            candidate = null; return waitFor("Walking to a checked workstation placement face");
        }
        BlockPos target = candidate.target;
        BlockState occupying = mc.world.getBlockState(target);
        if (!occupying.isAir() && soft(occupying)) return clearSoft(target);
        var trace = trace(candidate.face, mc.player.getEyePos());
        if (!visible(candidate.face, mc.player.getEyePos())) {
            if (trace.getType() == HitResult.Type.BLOCK && soft(mc.world.getBlockState(trace.getBlockPos())) && (exclusion == null || !exclusion.intersects(new Box(trace.getBlockPos())))) return clearSoft(trace.getBlockPos());
            rejected.add(key(candidate)); candidate = null; return waitFor("Checking an alternate visible workstation face");
        }
        if (!readyAim(candidate.face.getPos())) return waitFor("Turning smoothly toward the checked workstation face");
        var actual = mc.player.raycast(reach(), 1, false);
        if (!(actual instanceof BlockHitResult hit) || !hit.getBlockPos().equals(candidate.face.getBlockPos()) || hit.getSide() != candidate.face.getSide()) {
            if (++aimMisses > 25) {rejected.add(key(candidate)); candidate = null; resetAim();}
            return waitFor("Aligning the actual crosshair with the workstation face");
        }
        int slot = hotbar(item); if (slot < 0) return waitFor("Waiting for the workstation hotbar item");
        int original = mc.player.getInventory().selectedSlot;
        mc.player.getInventory().selectedSlot = slot;
        ItemStack stack = mc.player.getMainHandStack(); ItemPlacementContext context = new ItemPlacementContext(mc.player, Hand.MAIN_HAND, stack, hit);
        if (!context.getBlockPos().equals(target) || block.getPlacementState(context) == null) {
            mc.player.getInventory().selectedSlot = original; rejected.add(key(candidate)); candidate = null; resetAim(); return waitFor("Checking a different valid workstation placement face");
        }
        int before = InventoryAutomation.count(item);
        var result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        mc.player.getInventory().selectedSlot = original;
        if (!result.isAccepted()) {
            rejected.add(key(candidate)); candidate = null; resetAim();
            return ++placementFailures >= 4 ? need("The server refused four workstation placement faces. Check build permissions, then Resume.") : waitFor("The server refused this face; trying another checked surface");
        }
        mc.player.swingHand(Hand.MAIN_HAND); placing = new Placing(candidate, block, item, before, mc.player.age, mc.world); resetAim();
        return waitFor("Placed the workstation normally; waiting for server acknowledgement");
    }
    private Candidate chooseCandidate(Box exclusion) {
        BlockPos origin = mc.player.getBlockPos(); List<BlockPos> targets = new ArrayList<>();
        for (BlockPos pos : BlockPos.iterate(origin.add(-4, -3, -4), origin.add(4, 2, 4))) if (validTarget(pos, exclusion)) targets.add(pos.toImmutable());
        targets.sort(Comparator.comparingDouble(pos -> mc.player.squaredDistanceTo(Vec3d.ofCenter(pos)) + Math.abs(pos.getY() - origin.getY()) * 2));
        Candidate softCandidate = null;
        for (BlockPos target : targets) for (Direction direction : Direction.values()) {
            BlockPos support = target.offset(direction); Direction face = direction.getOpposite();
            BlockState state = mc.world.getBlockState(support);
            if (!mc.world.isChunkLoaded(support) || state.hasBlockEntity() || !state.getFluidState().isEmpty() || NativeTerrain.hazard(state)
                    || !state.isSideSolidFullSquare(mc.world, support, face)) continue;
            Vec3d point = Vec3d.ofCenter(support).add(face.getDoubleVector().multiply(.5));
            BlockHitResult hit = new BlockHitResult(point, face, support, false); Candidate local = new Candidate(target, hit, null);
            if (rejected.contains(key(local)) || !reachable(point)) continue;
            if (visible(hit, mc.player.getEyePos())) return local;
            var obstruction = trace(hit, mc.player.getEyePos());
            if (softCandidate == null && obstruction.getType() == HitResult.Type.BLOCK && soft(mc.world.getBlockState(obstruction.getBlockPos()))
                    && (exclusion == null || !exclusion.intersects(new Box(obstruction.getBlockPos())))) softCandidate = local;
        }
        if (softCandidate != null) return softCandidate;
        // The nearby surface can be valid while the current tree-side view is not. Try a checked stand, not a guessed +1 offset.
        for (BlockPos target : targets) for (int z = -2; z <= 2; z++) for (int x = -2; x <= 2; x++) {
            BlockPos stand = target.add(x, 0, z); if (!safeStand(stand) || rejectedStands.contains(stand)) continue;
            Vec3d eye = Vec3d.ofBottomCenter(stand).add(0, mc.player.getStandingEyeHeight(), 0);
            BlockPos floor = target.down(); BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(floor).add(0, .5, 0), Direction.UP, floor, false);
            Candidate route = new Candidate(target, hit, stand);
            if (!rejected.contains(key(route)) && eye.squaredDistanceTo(hit.getPos()) <= reachSquared() && visible(hit, eye)) return route;
        }
        return null;
    }
    private boolean validTarget(BlockPos pos, Box exclusion) {
        if (!mc.world.isChunkLoaded(pos) || !mc.world.isInBuildLimit(pos) || !mc.world.getWorldBorder().contains(pos)
                || exclusion != null && exclusion.intersects(new Box(pos)) || mc.player.getBoundingBox().intersects(new Box(pos))) return false;
        BlockState state = mc.world.getBlockState(pos), floor = mc.world.getBlockState(pos.down());
        return (state.isAir() || soft(state)) && state.getFluidState().isEmpty() && !NativeTerrain.hazard(state)
                && floor.isSideSolidFullSquare(mc.world, pos.down(), Direction.UP) && floor.getFluidState().isEmpty() && !NativeTerrain.hazard(floor)
                && !floor.isIn(BlockTags.LEAVES) && !floor.isIn(BlockTags.LOGS);
    }
    private boolean safeStand(BlockPos pos) {
        if (!mc.world.isChunkLoaded(pos) || !mc.world.isInBuildLimit(pos.up()) || !mc.world.getWorldBorder().contains(pos)) return false;
        BlockState floor = mc.world.getBlockState(pos.down()), feet = mc.world.getBlockState(pos), head = mc.world.getBlockState(pos.up());
        return floor.isSideSolidFullSquare(mc.world, pos.down(), Direction.UP) && floor.getFluidState().isEmpty() && !NativeTerrain.hazard(floor)
                && !floor.isIn(BlockTags.LEAVES) && !floor.isIn(BlockTags.LOGS)
                && feet.getCollisionShape(mc.world, pos).isEmpty() && head.getCollisionShape(mc.world, pos.up()).isEmpty()
                && feet.getFluidState().isEmpty() && head.getFluidState().isEmpty() && !NativeTerrain.hazard(feet) && !NativeTerrain.hazard(head);
    }
    private InventoryAutomation.Result clearSoft(BlockPos pos) {
        if (!soft(mc.world.getBlockState(pos))) return waitFor("Rechecking the workstation view");
        var visible = NativeMiningRay.visibleAim(mc, pos);
        if (visible == null || !reachable(visible.hit().getPos())) { candidate = null; return waitFor("Checking a clearer workstation face"); }
        return breakNormally(pos, visible, false);
    }
    InventoryAutomation.Result finish() {
        clearChangedWorld();
        InventoryAutomation.Result observation = observePlacement(); if (observation != null) return observation;
        if (tables.isEmpty()) return done("Task-owned crafting tables are recovered");
        if (collecting != null) return collect();
        BlockPos target = tables.keySet().iterator().next();
        if (tables.get(target) != mc.world) { tables.remove(target); return waitFor("Discarding an old-world workstation reference"); }
        if (!mc.world.isChunkLoaded(target)) return need("The task-owned crafting table is unloaded. Return near " + target.toShortString() + " and Resume to recover it.");
        if (!mc.world.getBlockState(target).isOf(Blocks.CRAFTING_TABLE)) { tables.remove(target); return waitFor("The previous task-owned table is no longer at its recorded location"); }
        InventoryAutomation.Result approach = approach(target); if (approach != null) return approach;
        var visible = NativeMiningRay.visibleAim(mc, target);
        if (visible == null) return waitFor("Checking the task-owned crafting table view");
        return breakNormally(target, visible, true);
    }
    private InventoryAutomation.Result breakNormally(BlockPos pos, NativeMiningRay.Aim visible, boolean recover) {
        if (!pos.equals(breaking)) { cancelBreaking(); breaking = pos; breakAge = mc.player.age; pickupBefore = InventoryAutomation.count(Items.CRAFTING_TABLE); aim = visible.point(); }
        if (mc.player.age - breakAge > 300) return need("The server did not allow the workstation obstacle to break. Check region permissions, then Resume.");
        if (aim == null) aim = visible.point();
        if (!readyAim(aim)) return waitFor(recover ? "Turning toward the task-owned crafting table" : "Turning toward harmless foliage blocking the workstation");
        var trace = mc.player.raycast(reach(), 1, false);
        if (!(trace instanceof BlockHitResult hit) || !hit.getBlockPos().equals(pos)) { cancelBreaking(); candidate = null; return waitFor("Rechecking the workstation break line of sight"); }
        selectBreakingTool(mc.world.getBlockState(pos));
        if (!breakStarted) { breakStarted = mc.interactionManager.attackBlock(pos, hit.getSide()); }
        else mc.interactionManager.updateBlockBreakingProgress(pos, hit.getSide());
        mc.player.swingHand(Hand.MAIN_HAND);
        if (mc.world.getBlockState(pos).isAir()) {
            mc.interactionManager.cancelBlockBreaking(); breakStarted = false;
            if (recover) { collecting = pos; pickupAge = mc.player.age; }
            breaking = null; candidate = null; restoreSlot(); resetAim();
        }
        return waitFor(recover ? "Recovering the task-owned crafting table through normal block breaking" : "Clearing harmless foliage through normal block breaking");
    }
    private InventoryAutomation.Result collect() {
        if (pending(collecting)) return waitFor("Waiting for the server to confirm the recovered table was broken");
        if (mc.world.getBlockState(collecting).isOf(Blocks.CRAFTING_TABLE)) { collecting = null; return waitFor("The server kept the table in place; rechecking recovery"); }
        if (InventoryAutomation.count(Items.CRAFTING_TABLE) > pickupBefore || mc.player.getAbilities().creativeMode) {
            tables.remove(collecting); removedTables.remove(collecting); collecting = null; rejectedStands.clear(); routeFailures = 0;
            if (ownsRoute) navigator.cancel(); ownsRoute = false; return tables.isEmpty() ? done("The task-owned crafting table is back in inventory") : waitFor("Recovered one task-owned crafting table");
        }
        if (mc.player.age - pickupAge > 200) return need("The crafting table broke but its item has not reached inventory. Make inventory space or collect the drop near " + collecting.toShortString() + ", then Resume.");
        if (navigator.status().owned()) return waitFor("Walking to collect the task-owned crafting table drop");
        if (safeStand(collecting) && mc.player.squaredDistanceTo(Vec3d.ofBottomCenter(collecting)) > .16) {
            routeStand = collecting; ownsRoute = navigator.travelForInteraction(collecting);
        }
        return waitFor("Waiting for the actual crafting table drop to reach inventory");
    }
    private void selectBreakingTool(BlockState state) {
        int best = mc.player.getInventory().selectedSlot; float speed = 1;
        for (int i = 0; i < 9; i++) { ItemStack stack = mc.player.getInventory().getStack(i); if (stack.isDamageable() && stack.getMaxDamage() - stack.getDamage() <= 1) continue; float value = stack.getMiningSpeedMultiplier(state); if (value > speed) {speed = value; best = i;} }
        if (previousSlot < 0) previousSlot = mc.player.getInventory().selectedSlot;
        mc.player.getInventory().selectedSlot = best; selectedSlot = best;
    }
    private boolean readyAim(Vec3d point) {
        if (aim == null || aim.squaredDistanceTo(point) > .00001) {aim = point; alignedAge = -1;}
        if (!AutomationMotion.align(aim)) {alignedAge = -1; return false;}
        if (alignedAge < 0) {alignedAge = mc.player.age; return false;}
        return mc.player.age > alignedAge;
    }
    private BlockHitResult trace(BlockHitResult hit, Vec3d eye) { return mc.world.raycast(new RaycastContext(eye, hit.getPos().subtract(hit.getSide().getDoubleVector().multiply(.002)), RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player)); }
    private boolean visible(BlockHitResult hit, Vec3d eye) {var actual = trace(hit, eye); return actual.getType() == HitResult.Type.BLOCK && actual.getBlockPos().equals(hit.getBlockPos()) && actual.getSide() == hit.getSide();}
    private boolean soft(BlockState state) {return state.getFluidState().isEmpty() && !state.hasBlockEntity() && MiningPolicy.softObstruction(Registries.BLOCK.getId(state.getBlock()).getPath());}
    private double reach() {return Math.min(4.5, mc.player.getBlockInteractionRange());}
    private double reachSquared() {return reach() * reach();}
    private boolean reachable(Vec3d point) {return mc.player.getEyePos().squaredDistanceTo(point) <= reachSquared();}
    private int hotbar(Item item) {for (int i = 0; i < 9; i++) if (mc.player.getInventory().getStack(i).isOf(item)) return i; return -1;}
    private String key(Candidate value) {return value.target.asLong() + ":" + value.face.getSide() + ":" + (value.stand == null ? "local" : value.stand.asLong());}
    private void resetAim() {aim = null; alignedAge = -1; aimMisses = 0;}
    private void restoreSlot() {if (mc.player != null && previousSlot >= 0 && mc.player.getInventory().selectedSlot == selectedSlot) mc.player.getInventory().selectedSlot = previousSlot; previousSlot = selectedSlot = -1;}
    private void cancelBreaking() {if (breakStarted && mc.interactionManager != null) mc.interactionManager.cancelBlockBreaking(); breakStarted = false; breaking = null; restoreSlot(); resetAim();}
    void stop() {
        cancelBreaking(); candidate = null; interactionTarget = null; rejected.clear(); rejectedStands.clear(); placementFailures = routeFailures = 0;
        // Pause/Stop must never begin cleanup. Retain acknowledged ownership so an explicit resumed task can recover it.
        if (ownsRoute) navigator.cancel(); ownsRoute = false; routeStand = null;
    }
    void pauseActions() { cancelBreaking(); }
    private static InventoryAutomation.Result waitFor(String value) {return new InventoryAutomation.Result(false, false, value);}
    private static InventoryAutomation.Result need(String value) {return new InventoryAutomation.Result(false, true, value);}
    private static InventoryAutomation.Result done(String value) {return new InventoryAutomation.Result(true, false, value);}
}
