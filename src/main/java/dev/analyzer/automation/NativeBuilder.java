package dev.analyzer.automation;

import dev.analyzer.AnalyzerClient;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.*;

/** Original player builder: loaded-state inspection, support ordering and normal server-backed interactions. */
public final class NativeBuilder {
    public record Result(boolean done, boolean blocked, String detail, double progress, BlockPos routeGoal) { }
    private record Target(BlockPos pos, BlockState desired) { }
    private record Placement(BlockPos stand, BlockHitResult hit, BlockState preview) { }
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final List<Target> targets = new ArrayList<>();
    private final Map<BlockPos, BlockState> ownedPlacements = new HashMap<>();
    private final Map<BlockPos, Integer> failures = new HashMap<>();
    private record ApproachKey(BlockPos target, BlockPos stand) { }
    private final Set<ApproachKey> rejectedApproaches = new HashSet<>();
    private BlockPos arrivedStand;
    private Object world, player;
    private String name = "Build";
    private boolean active, paused;
    private int savedSlot = -1, selectedSlot = -1, ticks, cooldown, scanCursor, scanMatching, scanObserved;
    private int matching, observed, alignedTick = -1;
    private int planningCursor;
    private boolean pendingSorted;
    private String unavailableReason = "";
    private Target approachTarget;
    private Placement checkedApproach;
    private Target standSearchTarget;
    private final List<BlockPos> standCandidates = new ArrayList<>();
    private int standCursor;
    private boolean approachIncomplete;
    private final List<Target> pending = new ArrayList<>();
    private Target awaiting;
    private BlockState predicted, awaitingInitial;
    private int awaitingUntil;
    private BlockPos aiming;
    private int breakDelay = 5, placeDelay = 4;
    private AutomationNavigator.MotionProfile motion;
    public void configureMotion(AutomationNavigator.MotionProfile profile) {
        motion = Objects.requireNonNull(profile); actionDelays(profile.breakDelayTicks(), profile.placeDelayTicks());
    }
    public void actionDelays(int breakTicks, int placeTicks) {
        breakDelay = Math.clamp(breakTicks, 1, 20); placeDelay = Math.clamp(placeTicks, 1, 20);
    }

    public boolean begin(String name, BuildSchematic schematic, BlockPos origin) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) throw new IllegalArgumentException("Join a world before building");
        SchematicImporter.volume(schematic.widthX(), schematic.heightY(), schematic.lengthZ()); stop();
        this.name = name == null ? "Build" : name; world = mc.world; player = mc.player;
        for (int y = 0; y < schematic.heightY(); y++) for (int z = 0; z < schematic.lengthZ(); z++) for (int x = 0; x < schematic.widthX(); x++) {
            BlockPos pos = origin.add(x, y, z);
            if (!mc.world.isChunkLoaded(pos) || !mc.world.isInBuildLimit(pos) || !mc.world.getWorldBorder().contains(pos)) throw new IllegalArgumentException("Load the complete build inside the world border and build height");
            BlockState current = mc.world.getBlockState(pos);
            if (!schematic.inSchematic(x, y, z, current)) continue;
            BlockState desired = schematic.desiredState(x, y, z, current, List.of());
            if (desired == null) throw new IllegalArgumentException("A selected build cell has no block state");
            if (!current.getFluidState().isEmpty()) throw new IllegalArgumentException("The build footprint contains fluid at " + pos.toShortString());
            if (!matches(current, desired) && !placementCompatible(current, desired) && (!current.isAir() && !current.isReplaceable() || current.hasBlockEntity() || mc.world.getBlockEntity(pos) != null))
                throw new IllegalArgumentException("This build would clear an existing solid block or saved block entity at " + pos.toShortString());
            targets.add(new Target(pos, desired));
        }
        if (targets.isEmpty()) throw new IllegalArgumentException("Schematic contains no selected cells");
        active = true; paused = false; savedSlot = mc.player.getInventory().selectedSlot;
        return true;
    }
    public void pause() { paused = true; if (mc.interactionManager != null) mc.interactionManager.cancelBlockBreaking(); aiming = null; alignedTick = -1; }
    public void resume() { paused = false; aiming = null; alignedTick = -1; rejectedApproaches.clear(); arrivedStand = null; resetScan(); }
    /** The navigator reports a genuinely centered, settled route completion before this stand is exhausted. */
    public void approachArrived(BlockPos stand) { arrivedStand = stand == null ? null : stand.toImmutable(); }
    public void stop() {
        if (mc.interactionManager != null) mc.interactionManager.cancelBlockBreaking();
        if (mc.player == player && savedSlot >= 0 && selectedSlot >= 0 && mc.player.getInventory().selectedSlot == selectedSlot) mc.player.getInventory().selectedSlot = savedSlot;
        targets.clear(); pending.clear(); ownedPlacements.clear(); failures.clear(); rejectedApproaches.clear(); arrivedStand = null;
        active = paused = false; world = player = null; awaiting = null; predicted = awaitingInitial = null; savedSlot = selectedSlot = -1;
        ticks = cooldown = scanCursor = scanMatching = scanObserved = matching = observed = 0; aiming = null; alignedTick = -1;
        resetScan();
    }
    public Result tick() {
        ticks++;
        if (!active) return blocked("No build has been started");
        if (paused) return waiting("Builder is paused", null);
        if (mc.world != world || mc.player != player || mc.player == null || mc.interactionManager == null || !mc.player.isAlive()) return blocked("The builder's original world or player is unavailable");
        if (mc.player.isSpectator()) return blocked("Building cannot run in Spectator mode");
        if (mc.getOverlay() != null) return waiting("Waiting for resource loading to finish", null);
        if (mc.currentScreen != null || !(mc.player.currentScreenHandler instanceof PlayerScreenHandler)) return waiting("Close the current menu to continue building", null);
        if (!mc.player.currentScreenHandler.getCursorStack().isEmpty()) return blocked("Put the held inventory cursor item back into a slot before building");
        if (cooldown > 0) { cooldown--; return waiting("Waiting for the configured block-action interval", null); }
        if (awaiting != null) {
            BlockState actual = mc.world.getBlockState(awaiting.pos);
            if (!actual.equals(awaitingInitial) && (matches(actual, awaiting.desired) || placementCompatible(actual, awaiting.desired))) {
                if (!actual.isAir()) ownedPlacements.put(awaiting.pos, actual);
                rejectedApproaches.clear(); arrivedStand = null;
                awaiting = null; predicted = awaitingInitial = null; resetScan();
            } else if (ticks < awaitingUntil) return waiting("Waiting for the server's placed block state", null);
            else {
                Target failed = awaiting; awaiting = null;
                diagnostic("placement-timeout target=" + failed.pos + " desired=" + failed.desired + " predicted=" + predicted + " before=" + awaitingInitial + " observed=" + actual);
                if (!actual.equals(awaitingInitial) && !actual.isAir() && !placementCompatible(actual, failed.desired)) {
                    predicted = awaitingInitial = null;
                    return blocked("Ordinary placement produced " + actual + " instead of " + failed.desired + " at " + failed.pos.toShortString() + "; construction stopped before spending another material");
                }
                if (!actual.equals(awaitingInitial) && predicted != null && actual.isOf(predicted.getBlock())) ownedPlacements.put(failed.pos, actual);
                predicted = awaitingInitial = null;
                if (failures.merge(failed.pos, 1, Integer::sum) >= 3) return blocked("The server did not produce the requested block state at " + failed.pos.toShortString() + "; check placement rules and orientation");
                resetScan();
            }
        }
        int inspected = 0;
        while (scanCursor < targets.size() && inspected++ < 8192) {
            Target target = targets.get(scanCursor++);
            if (!mc.world.isChunkLoaded(target.pos)) return blocked("Part of the selected build is no longer loaded");
            scanObserved++; BlockState current = mc.world.getBlockState(target.pos);
            if (matches(current, target.desired)) { scanMatching++; continue; }
            if (!current.getFluidState().isEmpty()) return blocked("Fluid has entered the selected build at " + target.pos.toShortString());
            if ((current.hasBlockEntity() || mc.world.getBlockEntity(target.pos) != null) && !matches(current, target.desired) && !placementCompatible(current, target.desired)) return blocked("A saved block entity blocks construction at " + target.pos.toShortString());
            if (!current.isAir() && !current.isReplaceable() && !ownedPlacements.containsKey(target.pos) && !canStack(current, target.desired) && !placementCompatible(current, target.desired)) return blocked("An existing solid block blocks construction at " + target.pos.toShortString());
            if (!ImportedSchematic.secondary(target.desired)) pending.add(target);
        }
        if (scanCursor < targets.size()) return waiting("Inspecting the next loaded portion of " + name, null);
        matching = scanMatching; observed = scanObserved;
        if (observed == targets.size() && matching == targets.size()) return new Result(true, false, "Every selected block matches the actual world", 1, null);
        if (!pendingSorted) {
            pending.sort(Comparator.comparingInt((Target target) -> target.pos.getY()).thenComparingDouble(target -> mc.player.squaredDistanceTo(Vec3d.ofCenter(target.pos))));
            pendingSorted = true;
        }
        long planningDeadline = System.nanoTime() + 6_000_000L;
        while (planningCursor < pending.size()) {
            if (System.nanoTime() > planningDeadline) return waiting("Checking the next loaded placement approach", null);
            Target target = pending.get(planningCursor);
            BlockState current = mc.world.getBlockState(target.pos);
            if (matches(current, target.desired)) { planningCursor++; continue; }
            if (!current.isAir() && (target.desired.isAir() || !canStack(current, target.desired))) {
                if (current.isReplaceable() || ownedPlacements.containsKey(target.pos)) {
                    BlockHitResult hit = visibleHit(target.pos);
                    if (hit != null && reachable(hit.getPos())) return breakBlock(target, current, hit);
                    BlockPos stand = target.equals(approachTarget) && checkedApproach != null && safeStand(checkedApproach.stand) ? checkedApproach.stand : standForBreak(target.pos);
                    if (stand != null) return route(target, new Placement(stand, null, null));
                }
                planningCursor++;
                continue;
            }
            if (target.desired.isAir()) { planningCursor++; continue; }
            Item material = target.desired.getBlock().asItem();
            if (material == Items.AIR) { unavailableReason = "The schematic requires a block without a normal placeable item: " + Registries.BLOCK.getId(target.desired.getBlock()); planningCursor++; continue; }
            int slot = materialSlot(material);
            if (slot < 0) { unavailableReason = "Need " + Registries.ITEM.getId(material) + " in inventory before placing " + target.pos.toShortString(); planningCursor++; continue; }
            if (slot >= 9) return hotbar(material, slot);
            ItemStack stack = mc.player.getInventory().getStack(slot);
            Placement local = placement(target, stack, mc.player.getBlockPos(), mc.player.getEyePos(), true);
            if (local != null && reachable(local.hit.getPos()) && !collisionWithPlayer(target)) {
                select(slot); return place(target, local);
            }
            Placement approach = target.equals(approachTarget) && checkedApproach != null && safeStand(checkedApproach.stand) ? checkedApproach : approach(target, stack);
            if (approach != null) return route(target, approach);
            if (approachIncomplete) return waiting("Surveying the next loaded placement standing cell", null);
            planningCursor++;
        }
        if (!unavailableReason.isBlank()) return blocked(unavailableReason);
        resetScan();
        return blocked("No loaded supported placement approach can reach the remaining requested states; provide normal stairs/scaffolding or check the schematic's orientation and paired blocks");
    }
    private Result route(Target target, Placement approach) {
        if (approach.stand.equals(arrivedStand) && mc.player.getPos().distanceTo(Vec3d.ofBottomCenter(approach.stand)) <= .22) {
            // Real visibility is authoritative. Never request an already completed, unusable approach forever.
            rejectedApproaches.add(new ApproachKey(target.pos, approach.stand));
            diagnostic("reject-arrived-approach target=" + target.pos + " stand=" + approach.stand + " player=" + mc.player.getPos());
            approachTarget = null; checkedApproach = null; arrivedStand = null; aiming = null; alignedTick = -1;
            return waiting("The reached placement approach is occluded; checking another standing cell", null);
        }
        aiming = null; alignedTick = -1; approachTarget = target; checkedApproach = approach;
        return waiting("Walking to a checked placement approach for " + target.pos.toShortString(), approach.stand);
    }
    private Result place(Target target, Placement placement) {
        if (!readyAim(target.pos, placement.hit.getPos())) return waiting("Turning smoothly toward the placement face", null);
        ItemStack stack = mc.player.getMainHandStack();
        BlockState preview = target.desired.getBlock().getPlacementState(new ItemPlacementContext(mc.player, Hand.MAIN_HAND, stack, placement.hit));
        if (preview == null || !placementCompatible(preview, target.desired)) { resetScan(); return waiting("Rechecking the requested placement orientation", null); }
        if (!visible(placement.hit, mc.player.getEyePos())) { resetScan(); return waiting("Rechecking placement line of sight", null); }
        BlockState before = mc.world.getBlockState(target.pos);
        var result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, placement.hit);
        diagnostic("place target=" + target.pos + " desired=" + target.desired + " preview=" + preview + " clicked=" + placement.hit.getBlockPos()
                + " face=" + placement.hit.getSide() + " hit=" + placement.hit.getPos() + " hand=" + stack + " initial=" + before
                + " after=" + mc.world.getBlockState(target.pos) + " accepted=" + result.isAccepted());
        if (result.isAccepted()) { mc.player.swingHand(Hand.MAIN_HAND); awaiting = target; predicted = preview; awaitingInitial = before; awaitingUntil = ticks + 30; }
        else if (failures.merge(target.pos, 1, Integer::sum) >= 3) return blocked("The server refused ordinary block placement at " + target.pos.toShortString());
        cooldown = placeDelay; aiming = null; alignedTick = -1; resetScan();
        return waiting("Placed a normal block; awaiting the observed state", null);
    }
    private Result breakBlock(Target target, BlockState current, BlockHitResult hit) {
        if (current.getHardness(mc.world, target.pos) < 0) return blocked("An unbreakable block blocks construction");
        if (!current.isReplaceable() && (!ownedPlacements.containsKey(target.pos) || !current.isOf(ownedPlacements.get(target.pos).getBlock()))) return blocked("The builder will not clear another existing solid block");
        if (!readyAim(target.pos, hit.getPos())) return waiting("Turning smoothly toward the block to clear", null);
        selectTool(current);
        boolean breaking = mc.interactionManager.isBreakingBlock();
        if (breaking) mc.interactionManager.updateBlockBreakingProgress(target.pos, hit.getSide());
        else mc.interactionManager.attackBlock(target.pos, hit.getSide());
        mc.player.swingHand(Hand.MAIN_HAND);
        if (mc.world.getBlockState(target.pos).isAir()) { cooldown = breakDelay; mc.interactionManager.cancelBlockBreaking(); resetScan(); aiming = null; alignedTick = -1; }
        return waiting("Breaking replaceable foliage or correcting an owned placement normally", null);
    }
    private Result hotbar(Item material, int source) {
        int destination = -1;
        for (int slot = 0; slot < 9; slot++) if (mc.player.getInventory().getStack(slot).isEmpty()) { destination = slot; break; }
        if (destination < 0) return blocked("Free one hotbar slot for " + Registries.ITEM.getId(material));
        mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, source, destination, SlotActionType.SWAP, mc.player);
        cooldown = 5;
        return waiting("Moving an owned material into an empty hotbar slot", null);
    }
    private Placement approach(Target target, ItemStack stack) {
        approachIncomplete = false;
        if (!target.equals(standSearchTarget)) {
            standSearchTarget = target; standCandidates.clear(); standCursor = 0;
            for (int y = -3; y <= 1; y++) for (int z = -3; z <= 3; z++) for (int x = -3; x <= 3; x++) {
                if (x == 0 && z == 0 || Math.abs(x) + Math.abs(z) > 4) continue;
                standCandidates.add(target.pos.add(x, y, z));
            }
            standCandidates.sort(Comparator.comparingDouble(pos -> mc.player.squaredDistanceTo(Vec3d.ofCenter(pos))));
        }
        long deadline = System.nanoTime() + 4_000_000L;
        while (standCursor < standCandidates.size()) {
            if (System.nanoTime() > deadline) { approachIncomplete = true; return null; }
            BlockPos stand = standCandidates.get(standCursor++);
            if (!safeStand(stand) || rejectedApproaches.contains(new ApproachKey(target.pos, stand))) continue;
            Vec3d eye = new Vec3d(stand.getX() + .5, stand.getY() + mc.player.getStandingEyeHeight(), stand.getZ() + .5);
            Placement placement = placement(target, stack, stand, eye, false);
            if (placement == null || eye.squaredDistanceTo(placement.hit.getPos()) > reach() * reach()) continue;
            return placement;
        }
        return null;
    }
    private Placement placement(Target target, ItemStack stack, BlockPos stand, Vec3d eye, boolean actual) {
        if (!(stack.getItem() instanceof BlockItem)) return null;
        BlockState current = mc.world.getBlockState(target.pos);
        for (Direction direction : Direction.values()) {
            boolean stacking = canStack(current, target.desired);
            BlockPos support = stacking ? target.pos : target.pos.offset(direction);
            Direction face = direction.getOpposite();
            if (!mc.world.isChunkLoaded(support) || mc.world.getBlockState(support).isAir() || !support.equals(target.pos) && mc.world.getBlockState(support).isReplaceable() || mc.world.getBlockState(support).hasBlockEntity()) continue;
            var shape = mc.world.getBlockState(support).getOutlineShape(mc.world, support);
            if (shape.isEmpty()) continue;
            Box outline = shape.getBoundingBox();
            double height = ImportedSchematic.value(target.desired, "half").equals("top") || ImportedSchematic.value(target.desired, "type").equals("top") ? .75 : .25;
            double hx = (outline.minX + outline.maxX) / 2, hy = (outline.minY + outline.maxY) / 2, hz = (outline.minZ + outline.maxZ) / 2;
            switch (face) {
                case UP -> hy = outline.maxY; case DOWN -> hy = outline.minY;
                case EAST -> hx = outline.maxX; case WEST -> hx = outline.minX;
                case SOUTH -> hz = outline.maxZ; case NORTH -> hz = outline.minZ;
            }
            if (face.getAxis().isHorizontal()) {
                hy = stacking ? (outline.minY + outline.maxY) / 2 : Math.clamp(height, outline.minY + .001, outline.maxY - .001);
                if (stacking && ImportedSchematic.value(current, "type").equals("top")) hy = outline.minY;
            }
            Vec3d hitPos = new Vec3d(support.getX() + hx, support.getY() + hy, support.getZ() + hz);
            BlockHitResult hit = new BlockHitResult(hitPos, face, support, false);
            if (!visible(hit, eye)) continue;
            if (!actual && !robustApproach(hit, eye)) continue;
            ItemPlacementContext context;
            {
                Vec3d delta = hitPos.subtract(eye); float yaw = (float)(Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90);
                Direction facing = Direction.fromHorizontalDegrees(yaw);
                Direction look = Direction.getFacing(delta);
                context = new ItemPlacementContext(mc.player, Hand.MAIN_HAND, stack, hit) {
                    @Override public Direction getHorizontalPlayerFacing() { return facing; }
                    @Override public Direction getPlayerLookDirection() { return look; }
                    @Override public Direction getVerticalPlayerLookDirection() { return delta.y < 0 ? Direction.DOWN : Direction.UP; }
                    @Override public float getPlayerYaw() { return yaw; }
                    @Override public Direction[] getPlacementDirections() {
                        List<Direction> order = new ArrayList<>(List.of(Direction.values()));
                        order.sort(Comparator.comparingDouble((Direction direction) -> direction.getDoubleVector().dotProduct(delta)).reversed());
                        if (!canReplaceExisting()) { Direction clicked = getSide().getOpposite(); order.remove(clicked); order.addFirst(clicked); }
                        return order.toArray(Direction[]::new);
                    }
                };
            }
            if (!context.getBlockPos().equals(target.pos)) continue;
            BlockState preview = target.desired.getBlock().getPlacementState(context);
            if (preview != null && placementCompatible(preview, target.desired)) return new Placement(stand, hit, preview);
        }
        return null;
    }
    private boolean safeStand(BlockPos pos) {
        if (!mc.world.isChunkLoaded(pos) || !mc.world.isInBuildLimit(pos) || !mc.world.getWorldBorder().contains(pos) || !mc.world.isInBuildLimit(pos.up())) return false;
        BlockState floor = mc.world.getBlockState(pos.down()), feet = mc.world.getBlockState(pos), head = mc.world.getBlockState(pos.up());
        return floor.isFullCube(mc.world, pos.down()) && floor.getFluidState().isEmpty() && !hazard(floor)
                && (feet.isAir() || feet.isReplaceable()) && (head.isAir() || head.isReplaceable())
                && feet.getFluidState().isEmpty() && head.getFluidState().isEmpty() && !hazard(feet) && !hazard(head);
    }
    private BlockPos standForBreak(BlockPos target) {
        BlockPos best = null; double distance = Double.POSITIVE_INFINITY;
        for (int z = -3; z <= 3; z++) for (int x = -3; x <= 3; x++) for (int y = -2; y <= 1; y++) {
            BlockPos stand = target.add(x, y, z);
            if (!safeStand(stand) || rejectedApproaches.contains(new ApproachKey(target, stand))) continue;
            Vec3d eye = new Vec3d(stand.getX() + .5, stand.getY() + mc.player.getStandingEyeHeight(), stand.getZ() + .5);
            if (eye.squaredDistanceTo(Vec3d.ofCenter(target)) > reach() * reach()) continue;
            var hit = mc.world.raycast(new RaycastContext(eye, Vec3d.ofCenter(target), RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player));
            if (hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(target)) continue;
            double candidate = mc.player.squaredDistanceTo(Vec3d.ofCenter(stand)); if (candidate < distance) { distance = candidate; best = stand; }
        }
        return best;
    }
    private BlockHitResult visibleHit(BlockPos target) {
        var hit = mc.world.raycast(new RaycastContext(mc.player.getEyePos(), Vec3d.ofCenter(target), RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player));
        return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(target) ? hit : null;
    }
    private boolean visible(BlockHitResult desired, Vec3d eye) {
        Vec3d into = desired.getPos().subtract(desired.getSide().getDoubleVector().multiply(.001));
        var hit = mc.world.raycast(new RaycastContext(eye, into, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player));
        return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(desired.getBlockPos()) && hit.getSide() == desired.getSide();
    }
    private boolean robustApproach(BlockHitResult hit, Vec3d centerEye) {
        // These four eyes surround the navigator's .15-block arrival circle. A corner-grazing ideal ray is insufficient.
        for (double x : new double[]{-.16, .16}) for (double z : new double[]{-.16, .16}) {
            Vec3d eye = centerEye.add(x, 0, z);
            if (eye.squaredDistanceTo(hit.getPos()) > reach() * reach() || !visible(hit, eye)) return false;
        }
        return true;
    }
    private boolean collisionWithPlayer(Target target) { return !target.desired.getCollisionShape(mc.world, target.pos).isEmpty() && mc.player.getBoundingBox().intersects(new Box(target.pos)); }
    private boolean readyAim(BlockPos target, Vec3d point) {
        if (!target.equals(aiming)) { aiming = target; alignedTick = -1; }
        if (!AutomationMotion.align(point, motion)) { alignedTick = -1; return false; }
        if (alignedTick < 0) { alignedTick = ticks; return false; }
        return ticks > alignedTick;
    }
    private int materialSlot(Item item) { for (int slot = 0; slot < 36; slot++) if (mc.player.getInventory().getStack(slot).isOf(item)) return slot; return -1; }
    private void select(int slot) { mc.player.getInventory().selectedSlot = slot; selectedSlot = slot; }
    private void selectTool(BlockState state) {
        int best = mc.player.getInventory().selectedSlot; float speed = 1;
        for (int slot = 0; slot < 9; slot++) { ItemStack stack = mc.player.getInventory().getStack(slot); if (stack.isDamageable() && stack.getMaxDamage() - stack.getDamage() <= 1) continue; float candidate = stack.getMiningSpeedMultiplier(state); if (candidate > speed) { speed = candidate; best = slot; } }
        select(best);
    }
    private static boolean matches(BlockState actual, BlockState desired) { return actual.equals(desired) || actual.isAir() && desired.isAir(); }
    static boolean canStack(BlockState actual, BlockState desired) {
        if (!actual.isOf(desired.getBlock())) return false;
        if (ImportedSchematic.value(desired, "type").equals("double") && !ImportedSchematic.value(actual, "type").equals("double")) return true;
        for (String field : List.of("layers", "candles", "pickles", "eggs")) {
            String a = ImportedSchematic.value(actual, field), d = ImportedSchematic.value(desired, field);
            if (!a.isEmpty() && !d.isEmpty() && Integer.parseInt(a) < Integer.parseInt(d)) return true;
        }
        return false;
    }
    static boolean placementCompatible(BlockState actual, BlockState desired) {
        if (!actual.isOf(desired.getBlock())) return false;
        for (var property : desired.getProperties()) {
            String name = property.getName();
            if (Set.of("north", "south", "east", "west", "shape", "distance", "snowy").contains(name)) continue;
            if (name.equals("type") && Set.of("left", "right", "single").contains(ImportedSchematic.value(desired, "type"))) continue;
            if (!actual.get(property).equals(desired.get(property))) return canStack(actual, desired);
        }
        return true;
    }
    private static boolean hazard(BlockState state) {
        return state.isOf(net.minecraft.block.Blocks.LAVA) || state.isOf(net.minecraft.block.Blocks.FIRE) || state.isOf(net.minecraft.block.Blocks.SOUL_FIRE)
                || state.isOf(net.minecraft.block.Blocks.MAGMA_BLOCK) || state.isOf(net.minecraft.block.Blocks.CACTUS) || state.isOf(net.minecraft.block.Blocks.POWDER_SNOW)
                || state.isOf(net.minecraft.block.Blocks.SWEET_BERRY_BUSH) || state.isOf(net.minecraft.block.Blocks.CAMPFIRE) || state.isOf(net.minecraft.block.Blocks.SOUL_CAMPFIRE);
    }
    private double reach() { return Math.min(4.5, mc.player.getBlockInteractionRange()); }
    private boolean reachable(Vec3d point) { return mc.player.getEyePos().squaredDistanceTo(point) <= reach() * reach(); }
    private void resetScan() {
        scanCursor = scanMatching = scanObserved = planningCursor = 0; pending.clear(); pendingSorted = false;
        unavailableReason = ""; approachTarget = null; checkedApproach = null;
        standSearchTarget = null; standCandidates.clear(); standCursor = 0; approachIncomplete = false;
    }
    private double progress() { return targets.isEmpty() ? 0 : matching / (double)targets.size(); }
    private Result waiting(String message, BlockPos routeGoal) { return new Result(false, false, message, progress(), routeGoal); }
    private Result blocked(String message) { if (mc.interactionManager != null) mc.interactionManager.cancelBlockBreaking(); return new Result(false, true, message, progress(), null); }
    private static void diagnostic(String detail) {
        if (Boolean.getBoolean("analyzer.nativeAutomationSmoke")) AnalyzerClient.LOGGER.info("ANALYZER_NATIVE_BUILDER: {}", detail);
    }
}
