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
import net.minecraft.util.ActionResult;
import java.util.function.Supplier;
import net.minecraft.util.PlayerInput;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.block.FluidBlock;
import net.minecraft.fluid.Fluids;
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
    private record Placement(BlockPos stand, BlockHitResult hit, BlockState preview, boolean sneak) { }
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
    private BlockPos buildMin, buildMax, refillSource, refillStand;
    private Item refillItem;
    private int refillUntil, fluidWait, configurationWait;
    private final List<Target> fluidTargets = new ArrayList<>();
    private String deferredState = "", deferredPowerState = "";
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
        buildMin = origin.toImmutable(); buildMax = origin.add(schematic.widthX() - 1, schematic.heightY() - 1, schematic.lengthZ() - 1);
        for (int y = 0; y < schematic.heightY(); y++) for (int z = 0; z < schematic.lengthZ(); z++) for (int x = 0; x < schematic.widthX(); x++) {
            BlockPos pos = origin.add(x, y, z);
            if (!mc.world.isChunkLoaded(pos) || !mc.world.isInBuildLimit(pos) || !mc.world.getWorldBorder().contains(pos)) throw new IllegalArgumentException("Load the complete build inside the world border and build height");
            BlockState current = mc.world.getBlockState(pos);
            if (!schematic.inSchematic(x, y, z, current)) continue;
            BlockState desired = schematic.desiredState(x, y, z, current, List.of());
            if (desired == null) throw new IllegalArgumentException("A selected build cell has no block state");
            if (!current.getFluidState().isEmpty() && !fluidAllowed(current, desired)) throw new IllegalArgumentException("Unexpected fluid occupies " + pos.toShortString() + "; the builder only enters the blueprint's actual water/fluid cells.");
            if (!matches(current, desired) && !placementCompatible(current, desired) && (!current.isAir() && !current.isReplaceable() || current.hasBlockEntity() || mc.world.getBlockEntity(pos) != null))
                throw new IllegalArgumentException("This build would clear an existing solid block or saved block entity at " + pos.toShortString());
            Target selected = new Target(pos, desired); targets.add(selected);
            if (!desired.getFluidState().isEmpty()) fluidTargets.add(selected);
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
        resetScan(); buildMin = buildMax = refillSource = null; refillItem = null; refillUntil = fluidWait = configurationWait = 0; refillStand = null; fluidTargets.clear();
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
        if (refillItem != null) {
            if (materialSlot(refillItem) >= 0) { refillItem = null; refillSource = refillStand = null; arrivedStand = null; resetScan(); }
            else if (ticks < refillUntil) return waiting("Waiting for the server's filled reusable bucket", null);
            else { refillItem = null; return blocked("The server did not refill the bucket. Check access to the observed source and resume."); }
        }
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
            if (!current.getFluidState().isEmpty() && !fluidAllowed(current, target.desired)) return blocked("Unexpected fluid entered a dry blueprint cell at " + target.pos.toShortString());
            if ((current.hasBlockEntity() || mc.world.getBlockEntity(target.pos) != null) && !matches(current, target.desired) && !placementCompatible(current, target.desired)) return blocked("A saved block entity blocks construction at " + target.pos.toShortString());
            if (!current.isAir() && !current.isReplaceable() && !ownedPlacements.containsKey(target.pos) && !canStack(current, target.desired) && !placementCompatible(current, target.desired)) return blocked("An existing solid block blocks construction at " + target.pos.toShortString());
            if (!ImportedSchematic.secondary(target.desired)) pending.add(target);
        }
        if (scanCursor < targets.size()) return waiting("Inspecting the next loaded portion of " + name, null);
        matching = scanMatching; observed = scanObserved;
        if (observed == targets.size() && matching == targets.size()) return new Result(true, false, "Every construction block matches its observed persistent state; live power and growth remain server-controlled", 1, null);
        if (!pendingSorted) {
            pending.sort(Comparator.comparingInt((Target target) -> fluidPhase(target, mc.world.getBlockState(target.pos)))
                    .thenComparingInt(target -> target.pos.getY()).thenComparingDouble(target -> mc.player.squaredDistanceTo(Vec3d.ofCenter(target.pos))));
            pendingSorted = true;
        }
        long planningDeadline = System.nanoTime() + 6_000_000L;
        while (planningCursor < pending.size()) {
            if (System.nanoTime() > planningDeadline) return waiting("Checking the next loaded placement approach", null);
            Target target = pending.get(planningCursor);
            BlockState current = mc.world.getBlockState(target.pos);
            if (matches(current, target.desired)) { planningCursor++; continue; }
            if (ImportedSchematic.flowing(target.desired)) { deferredState = "Waiting for the real source fluid to reach " + target.pos.toShortString(); planningCursor++; continue; }
            if (current.isOf(target.desired.getBlock()) && placementCompatible(current, target.desired) && !canStack(current, target.desired)) {
                if (configurationNeeded(current, target.desired)) {
                    String power = configurationPowerRequirement(current,target);
                    if (!power.isBlank()) { deferredPowerState = power; planningCursor++; continue; }
                    BlockHitResult hit = visibleHit(target.pos);
                    if (hit != null && reachable(hit.getPos())) return configure(target, hit);
                    BlockPos stand = standForBreak(target.pos);
                    if (stand != null) return route(target, new Placement(stand, null, null, false));
                }
                deferredState = "Waiting for neighboring blocks to settle at " + target.pos.toShortString() + ": expected " + target.desired + ", observed " + current;
                planningCursor++; continue;
            }
            if (!current.isAir() && !fluidAllowed(current, target.desired) && (target.desired.isAir() || !canStack(current, target.desired))) {
                if (current.isReplaceable() || ownedPlacements.containsKey(target.pos)) {
                    BlockHitResult hit = visibleHit(target.pos);
                    if (hit != null && reachable(hit.getPos())) return breakBlock(target, current, hit);
                    BlockPos stand = target.equals(approachTarget) && checkedApproach != null && safeStand(checkedApproach.stand) ? checkedApproach.stand : standForBreak(target.pos);
                    if (stand != null) return route(target, new Placement(stand, null, null, false));
                }
                planningCursor++;
                continue;
            }
            if (target.desired.isAir()) { planningCursor++; continue; }
            boolean bucketAction = ImportedSchematic.source(target.desired) || needsWaterlogging(current, target.desired);
            Item material = bucketAction ? bucketItem(target.desired) : target.desired.getBlock().asItem();
            if (bucketAction && !contained(target)) { deferredState = "Build fluid containment before filling " + target.pos.toShortString() + "; a real spill could leave the blueprint or enter a dry cell."; planningCursor++; continue; }
            if (material == Items.AIR) { unavailableReason = "The schematic requires a block without a normal placeable item: " + Registries.BLOCK.getId(target.desired.getBlock()); planningCursor++; continue; }
            int slot = materialSlot(material);
            if (slot < 0) {
                if (bucketAction) {
                    // A single bucket may currently hold the other fluid. Spend it on its real
                    // blueprint source first; never require a second bucket or dump it somewhere.
                    if (materialSlot(Items.BUCKET) < 0 && (materialSlot(Items.WATER_BUCKET) >= 0 || materialSlot(Items.LAVA_BUCKET) >= 0)) {
                        deferredState = "The reusable bucket currently holds the other fluid; its checked source must be placed before refilling";
                        planningCursor++; continue;
                    }
                    return refill(material);
                }
                unavailableReason = "Need " + Registries.ITEM.getId(material) + " in inventory before placing " + target.pos.toShortString(); planningCursor++; continue;
            }
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
        if (!deferredPowerState.isBlank()) {
            String power = deferredPowerState; resetScan();
            if (configurationWait++ < 80) return waiting(power + "; waiting for the completed circuit to settle",null);
            return blocked(power + ". Remaining geometry/fluids were attempted first; provide the required real circuit power and resume.");
        }
        if (!deferredState.isBlank()) {
            String reason = deferredState; resetScan();
            int settleTicks = fluidTargets.stream().anyMatch(target -> target.desired.getFluidState().isIn(net.minecraft.registry.tag.FluidTags.LAVA)) ? 240 : 80;
            if (reason.startsWith("Waiting for the real source fluid") && fluidWait++ < settleTicks) return waiting(reason, null);
            return blocked(reason + ". Check containment, supports and the exact requested connections, then resume.");
        }
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
        boolean bucket = stack.isOf(Items.WATER_BUCKET) || stack.isOf(Items.LAVA_BUCKET);
        // Vanilla filled buckets ignore fluid while aiming at their placement support;
        // only an empty bucket's refill uses SOURCE_ONLY (see refill below).
        BlockHitResult actualHit = lookHit(RaycastContext.FluidHandling.NONE);
        if (actualHit == null || !actualHit.getBlockPos().equals(placement.hit.getBlockPos()) || actualHit.getSide() != placement.hit.getSide()) {
            resetScan(); return waiting("Aligning the real crosshair with the checked placement face", null);
        }
        ItemPlacementContext actualContext = new ItemPlacementContext(mc.player, Hand.MAIN_HAND, stack, actualHit) {
            @Override public boolean shouldCancelInteraction() { return placement.sneak || super.shouldCancelInteraction(); }
        };
        if (!bucket && !actualContext.getBlockPos().equals(target.pos)) { resetScan(); return waiting("The actual placement context moved to a different cell; rechecking the requested block",null); }
        BlockState preview = bucket ? target.desired : target.desired.getBlock().getPlacementState(actualContext);
        if (preview == null || !placementCompatible(preview, target.desired)) { resetScan(); return waiting("Rechecking the requested placement orientation", null); }
        if (!visible(placement.hit, mc.player.getEyePos())) { resetScan(); return waiting("Rechecking placement line of sight", null); }
        BlockState before = mc.world.getBlockState(target.pos);
        if (bucket && !contained(target)) return blocked("The previously checked fluid containment changed before use");
        var result = ordinaryUse(placement.sneak, () -> bucket ? mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND)
                : mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, actualHit));
        diagnostic("place target=" + target.pos + " desired=" + target.desired + " preview=" + preview + " clicked=" + placement.hit.getBlockPos()
                + " face=" + placement.hit.getSide() + " hit=" + placement.hit.getPos() + " hand=" + stack + " initial=" + before
                + " after=" + mc.world.getBlockState(target.pos) + " accepted=" + result.isAccepted());
        if (result.isAccepted()) { configurationWait = fluidWait = 0; mc.player.swingHand(Hand.MAIN_HAND); awaiting = target; predicted = preview; awaitingInitial = before; awaitingUntil = ticks + 30; }
        else if (failures.merge(target.pos, 1, Integer::sum) >= 3) return blocked("The server refused ordinary block placement at " + target.pos.toShortString());
        cooldown = placeDelay; aiming = null; alignedTick = -1; resetScan();
        return waiting("Placed a normal block; awaiting the observed state", null);
    }
    private Result breakBlock(Target target, BlockState current, BlockHitResult hit) {
        if (current.getHardness(mc.world, target.pos) < 0) return blocked("An unbreakable block blocks construction");
        if (!current.isReplaceable() && (!ownedPlacements.containsKey(target.pos) || !current.isOf(ownedPlacements.get(target.pos).getBlock()))) return blocked("The builder will not clear another existing solid block");
        if (!readyAim(target.pos, hit.getPos())) return waiting("Turning smoothly toward the block to clear", null);
        BlockHitResult real = lookHit(RaycastContext.FluidHandling.NONE);
        if (real == null || !real.getBlockPos().equals(target.pos)) return waiting("Aligning the real crosshair before clearing the selected block",null);
        selectTool(current);
        boolean breaking = mc.interactionManager.isBreakingBlock();
        if (breaking) mc.interactionManager.updateBlockBreakingProgress(target.pos, real.getSide());
        else mc.interactionManager.attackBlock(target.pos, real.getSide());
        mc.player.swingHand(Hand.MAIN_HAND);
        if (mc.world.getBlockState(target.pos).isAir()) { cooldown = breakDelay; mc.interactionManager.cancelBlockBreaking(); resetScan(); aiming = null; alignedTick = -1; }
        return waiting("Breaking replaceable foliage or correcting an owned placement normally", null);
    }
    private Result hotbar(Item material, int source) {
        int destination = -1;
        for (int slot = 0; slot < 9; slot++) if (mc.player.getInventory().getStack(slot).isEmpty()) { destination = slot; break; }
        // Large real palettes exceed nine materials. Reuse a task-selected slot through a
        // normal SWAP: its previous stack moves into the source slot rather than being lost.
        if (destination < 0 && selectedSlot >= 0 && selectedSlot < 9) destination = selectedSlot;
        if (destination < 0) {
            for (int slot = 8; slot >= 0; slot--) {
                ItemStack held=mc.player.getInventory().getStack(slot);
                if (!held.isDamageable() && !held.contains(net.minecraft.component.DataComponentTypes.FOOD)) { destination=slot; break; }
            }
        }
        if (destination < 0) destination = mc.player.getInventory().selectedSlot;
        mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, source, destination, SlotActionType.SWAP, mc.player);
        cooldown = 5;
        return waiting("Swapping the next owned material into the working hotbar slot", null);
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
        if (stack.isOf(Items.WATER_BUCKET) || stack.isOf(Items.LAVA_BUCKET)) return bucketPlacement(target, stand, eye, actual);
        if (!(stack.getItem() instanceof BlockItem)) return null;
        BlockState current = mc.world.getBlockState(target.pos);
        for (Direction direction : Direction.values()) {
            boolean stacking = canStack(current, target.desired);
            BlockPos support = stacking ? target.pos : target.pos.offset(direction);
            Direction face = direction.getOpposite();
            if (!mc.world.isChunkLoaded(support) || mc.world.getBlockState(support).isAir() || !support.equals(target.pos) && mc.world.getBlockState(support).isReplaceable() ) continue;
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
            boolean sneaking = interactiveSupport(mc.world.getBlockState(support));
            if (!visible(hit, eye)) continue;
            if (!actual && !robustApproach(hit, eye)) continue;
            ItemPlacementContext context;
            {
                Vec3d delta = hitPos.subtract(eye); float yaw = (float)(Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90);
                Direction facing = Direction.fromHorizontalDegrees(yaw);
                Direction look = Direction.getFacing(delta);
                context = new ItemPlacementContext(mc.player, Hand.MAIN_HAND, stack, hit) {
                    @Override public boolean shouldCancelInteraction() { return sneaking; }
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
            if (preview != null && placementCompatible(preview, target.desired)) return new Placement(stand, hit, preview, sneaking);
        }
        return null;
    }
    private static boolean needsWaterlogging(BlockState current, BlockState desired) {
        return current.isOf(desired.getBlock()) && ImportedSchematic.value(desired, "waterlogged").equals("true") && ImportedSchematic.value(current, "waterlogged").equals("false");
    }
    private static int fluidPhase(Target target, BlockState current) {
        if (ImportedSchematic.value(target.desired, "waterlogged").equals("true") && !current.isOf(target.desired.getBlock())) return 0;
        if (ImportedSchematic.source(target.desired) || needsWaterlogging(current, target.desired)) return 2;
        if (ImportedSchematic.flowing(target.desired) || !target.desired.getFluidState().isEmpty() && !(target.desired.getBlock() instanceof FluidBlock)) return 3;
        return 0;
    }
    static boolean fluidAllowed(BlockState current, BlockState desired) {
        if (current.getFluidState().isEmpty()) return false;
        return !desired.getFluidState().isEmpty() && current.getFluidState().isIn(net.minecraft.registry.tag.FluidTags.WATER) == desired.getFluidState().isIn(net.minecraft.registry.tag.FluidTags.WATER)
                && (desired.getBlock() instanceof FluidBlock || current.getBlock() instanceof FluidBlock || current.isOf(desired.getBlock()));
    }
    private static Item bucketItem(BlockState desired) { return desired.getFluidState().isIn(net.minecraft.registry.tag.FluidTags.LAVA) ? Items.LAVA_BUCKET : Items.WATER_BUCKET; }
    private static boolean interactiveSupport(BlockState state) {
        String name = Registries.BLOCK.getId(state.getBlock()).getPath();
        return state.hasBlockEntity() || name.endsWith("_door") || name.endsWith("_trapdoor") || name.endsWith("_fence_gate") || name.endsWith("_button")
                || Set.of("lever", "repeater", "comparator", "note_block", "daylight_detector", "composter").contains(name);
    }
    private ActionResult ordinaryUse(boolean sneak, Supplier<ActionResult> action) {
        if (!sneak || mc.player.input.playerInput.sneak()) return action.get();
        // The ordinary sneak-use sequence avoids opening containers used as placement supports.
        PlayerInput previous = mc.player.input.playerInput;
        mc.player.input.playerInput = new PlayerInput(previous.forward(), previous.backward(), previous.left(), previous.right(), previous.jump(), true, previous.sprint());
        mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.PRESS_SHIFT_KEY));
        try { return action.get(); }
        finally {
            mc.player.input.playerInput = previous;
            mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.RELEASE_SHIFT_KEY));
        }
    }
    private BlockHitResult lookHit(RaycastContext.FluidHandling fluids) {
        Vec3d eye = mc.player.getEyePos(), end = eye.add(mc.player.getRotationVec(1).multiply(reach()));
        var hit = mc.world.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.OUTLINE, fluids, mc.player));
        return hit.getType() == HitResult.Type.BLOCK ? hit : null;
    }
    private static boolean configurationNeeded(BlockState current, BlockState desired) {
        String block = Registries.BLOCK.getId(desired.getBlock()).toString();
        for (var property : desired.getProperties()) if (SchematicStatePolicy.adjustable(block, property.getName()) && !current.get(property).equals(desired.get(property))) return true;
        return false;
    }
    private static String configurationPowerRequirement(BlockState current, Target target) {
        String block = Registries.BLOCK.getId(current.getBlock()).getPath();
        boolean openDiff = !ImportedSchematic.value(current,"open").equals(ImportedSchematic.value(target.desired,"open"));
        if (!openDiff) return "";
        if ((block.endsWith("_door") || block.endsWith("_trapdoor")) && block.startsWith("iron_"))
            return "The iron door/trapdoor at " + target.pos.toShortString() + " needs real redstone power for its requested open state";
        if (ImportedSchematic.value(current,"powered").equals("true"))
            return "Redstone currently controls the requested open state at " + target.pos.toShortString();
        return "";
    }
    private Result configure(Target target, BlockHitResult hit) {
        if (!readyAim(target.pos, hit.getPos())) return waiting("Turning toward the placed block's exact configuration", null);
        BlockHitResult real = lookHit(RaycastContext.FluidHandling.NONE);
        if (real == null || !real.getBlockPos().equals(target.pos)) { resetScan(); return waiting("Rechecking the configuration crosshair", null); }
        BlockState before = mc.world.getBlockState(target.pos);
        if (!placementCompatible(before, target.desired) || !configurationNeeded(before, target.desired)) { resetScan(); return waiting("Rechecking the placed configuration", null); }
        String power = configurationPowerRequirement(before,target);
        if (!power.isBlank()) { resetScan(); return waiting(power,null); }
        var used = ordinaryUse(false, () -> mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, real));
        if (!used.isAccepted()) return blocked("The server refused configuration at " + target.pos.toShortString());
        configurationWait = fluidWait = 0; mc.player.swingHand(Hand.MAIN_HAND); awaiting = target; predicted = target.desired; awaitingInitial = before; awaitingUntil = ticks + 30;
        cooldown = placeDelay; aiming = null; alignedTick = -1; resetScan();
        return waiting("Applying the repeater/comparator/opening/note setting through normal use", null);
    }
    private Placement bucketPlacement(Target target, BlockPos stand, Vec3d eye, boolean actual) {
        boolean logging = needsWaterlogging(mc.world.getBlockState(target.pos), target.desired);
        for (Direction direction : Direction.values()) {
            BlockPos support = logging ? target.pos : target.pos.offset(direction); Direction face = direction.getOpposite();
            if (!mc.world.isChunkLoaded(support)) continue;
            BlockState state = mc.world.getBlockState(support);
            if (state.isAir() || state.isReplaceable() || !state.getFluidState().isEmpty() && !logging) continue;
            var shape = state.getOutlineShape(mc.world, support); if (shape.isEmpty()) continue;
            Box box = shape.getBoundingBox(); double x = (box.minX + box.maxX) / 2, y = (box.minY + box.maxY) / 2, z = (box.minZ + box.maxZ) / 2;
            switch (face) { case UP -> y = box.maxY; case DOWN -> y = box.minY; case EAST -> x = box.maxX; case WEST -> x = box.minX; case SOUTH -> z = box.maxZ; case NORTH -> z = box.minZ; }
            BlockHitResult hit = new BlockHitResult(new Vec3d(support.getX() + x, support.getY() + y, support.getZ() + z), face, support, false);
            if (eye.squaredDistanceTo(hit.getPos()) <= reach() * reach() && visible(hit, eye) && (actual || robustApproach(hit, eye))) return new Placement(stand, hit, target.desired, false);
            if (logging) continue;
        }
        return null;
    }
    private boolean insideBuild(BlockPos pos) { return buildMin != null && pos.getX() >= buildMin.getX() && pos.getX() <= buildMax.getX() && pos.getY() >= buildMin.getY() && pos.getY() <= buildMax.getY() && pos.getZ() >= buildMin.getZ() && pos.getZ() <= buildMax.getZ(); }
    /** Re-read every spill boundary immediately before the normal bucket action. */
    private boolean contained(Target source) {
        boolean lava = source.desired.getFluidState().isIn(net.minecraft.registry.tag.FluidTags.LAVA);
        Set<SchematicFluidPolicy.Cell> intended = new HashSet<>();
        for (Target target : fluidTargets) if (target.desired.getFluidState().isIn(net.minecraft.registry.tag.FluidTags.LAVA) == lava)
            intended.add(new SchematicFluidPolicy.Cell(target.pos.getX(), target.pos.getY(), target.pos.getZ()));
        return SchematicFluidPolicy.contained(new SchematicFluidPolicy.Cell(source.pos.getX(),source.pos.getY(),source.pos.getZ()), new SchematicFluidPolicy.Field() {
            private BlockPos pos(SchematicFluidPolicy.Cell cell) { return new BlockPos(cell.x(),cell.y(),cell.z()); }
            public boolean known(SchematicFluidPolicy.Cell cell) { BlockPos p=pos(cell); return mc.world.isChunkLoaded(p) && mc.world.isInBuildLimit(p) && mc.world.getWorldBorder().contains(p); }
            public boolean closed(SchematicFluidPolicy.Cell cell, SchematicFluidPolicy.Face incoming) {
                BlockPos p=pos(cell); BlockState barrier=mc.world.getBlockState(p);
                Direction direction=Direction.fromVector(incoming.dx,incoming.dy,incoming.dz,Direction.DOWN);
                BlockPos from=p.offset(direction.getOpposite()); BlockState fromState=mc.world.getBlockState(from);
                var fluid=lava?Fluids.LAVA:Fluids.WATER;
                boolean sameFluid=!barrier.getFluidState().isEmpty() && (lava?barrier.getFluidState().isIn(net.minecraft.registry.tag.FluidTags.LAVA):barrier.getFluidState().isIn(net.minecraft.registry.tag.FluidTags.WATER));
                return !dev.analyzer.mixin.SchematicFluidAccess.analyzer$receivesFlow(direction,mc.world,from,fromState,p,barrier)
                        || !sameFluid && !dev.analyzer.mixin.SchematicFluidAccess.analyzer$canFill(mc.world,p,barrier,fluid);
            }
            public boolean intended(SchematicFluidPolicy.Cell cell) { return intended.contains(cell); }
        });
    }
    private Result refill(Item needed) {
        int bucket = materialSlot(Items.BUCKET);
        if (bucket < 0) return blocked("Need one reusable bucket (empty, or already filled with " + (needed == Items.WATER_BUCKET ? "water" : "lava") + ") before filling the blueprint's sources");
        if (bucket >= 9) return hotbar(Items.BUCKET, bucket);
        select(bucket);
        if (refillSource == null || !refillSource(needed, refillSource)) {
            refillSource = refillStand = null; double closest = Double.POSITIVE_INFINITY; BlockPos center = mc.player.getBlockPos();
            for (int y = -6; y <= 6; y++) for (int z = -24; z <= 24; z++) for (int x = -24; x <= 24; x++) {
                BlockPos candidate = center.add(x, y, z);
                if (!refillSource(needed, candidate)) continue;
                double distance = mc.player.squaredDistanceTo(Vec3d.ofCenter(candidate));
                if (distance < closest) { closest = distance; refillSource = candidate; }
            }
        }
        if (refillSource == null) return blocked("Place or approach a real " + (needed == Items.WATER_BUCKET ? "water" : "lava") + " source outside the build (within 24 blocks) to refill the same bucket; no source was observed. Resume afterward.");
        BlockHitResult visible = fluidHit(mc.player.getEyePos(), refillSource);
        if (visible == null || !reachable(visible.getPos())) {
            if (refillStand != null && refillStand.equals(arrivedStand) && mc.player.getPos().distanceTo(Vec3d.ofBottomCenter(refillStand)) <= .22) {
                rejectedApproaches.add(new ApproachKey(refillSource,refillStand)); refillStand = null; arrivedStand = null;
            }
            if (refillStand != null) {
                Vec3d eye=Vec3d.ofBottomCenter(refillStand).add(0,mc.player.getStandingEyeHeight(),0);
                var checked=fluidHit(eye,refillSource);
                if (!safeStand(refillStand) || checked==null || eye.squaredDistanceTo(checked.getPos())>reach()*reach()) refillStand=null;
            }
            if (refillStand == null) refillStand = fluidStand(refillSource);
            if (refillStand == null) return blocked("The observed refill source has no dry, supported reachable standing face. Provide an accessible source outside the blueprint and resume.");
            return waiting("Walking to the committed checked source approach to refill the reusable bucket", refillStand);
        }
        // Releasing the walking child brakes naturally; do not route back to the current cell center.
        if (!mc.player.isOnGround() || mc.player.getVelocity().horizontalLength() > .035) return waiting("Braking on the reached source approach before using the reusable bucket",null);
        if (!readyAim(refillSource, visible.getPos())) return waiting("Turning toward the actual refill source", null);
        BlockHitResult crosshair = lookHit(RaycastContext.FluidHandling.SOURCE_ONLY);
        if (crosshair == null || !crosshair.getBlockPos().equals(refillSource) || !refillSource(needed, refillSource)) return waiting("Aligning the real source-fluid crosshair", null);
        var result = ordinaryUse(false, () -> mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND));
        if (!result.isAccepted()) return blocked("The server refused to fill the bucket from the observed source");
        mc.player.swingHand(Hand.MAIN_HAND); refillItem = needed; refillUntil = ticks + 30; cooldown = placeDelay;
        aiming = null; alignedTick = -1;
        return waiting("Refilling the reusable bucket through normal item use", null);
    }
    private boolean refillSource(Item needed, BlockPos pos) {
        if (insideBuild(pos) || !mc.world.isChunkLoaded(pos) || !mc.world.isInBuildLimit(pos) || !mc.world.getWorldBorder().contains(pos)) return false;
        BlockState state = mc.world.getBlockState(pos);
        return state.getBlock() instanceof FluidBlock && state.getFluidState().isStill()
                && (needed == Items.WATER_BUCKET ? state.getFluidState().isOf(Fluids.WATER) : state.getFluidState().isOf(Fluids.LAVA));
    }
    private BlockHitResult fluidHit(Vec3d eye, BlockPos target) {
        var hit = mc.world.raycast(new RaycastContext(eye, Vec3d.ofCenter(target), RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.SOURCE_ONLY, mc.player));
        return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(target) ? hit : null;
    }
    private BlockPos fluidStand(BlockPos source) {
        BlockPos best = null; double distance = Double.POSITIVE_INFINITY;
        for (int y = -2; y <= 2; y++) for (int z = -3; z <= 3; z++) for (int x = -3; x <= 3; x++) {
            BlockPos stand = source.add(x, y, z); if (!safeStand(stand) || rejectedApproaches.contains(new ApproachKey(source,stand))) continue;
            Vec3d eye = Vec3d.ofBottomCenter(stand).add(0, mc.player.getStandingEyeHeight(), 0); var hit = fluidHit(eye, source);
            if (hit == null || eye.squaredDistanceTo(hit.getPos()) > reach() * reach()) continue;
            double score = mc.player.squaredDistanceTo(Vec3d.ofCenter(stand)); if (score < distance) { best = stand; distance = score; }
        }
        return best;
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
    private boolean collisionWithPlayer(Target target) {
        if (!target.desired.getFluidState().isEmpty()) {
            Box spill = new Box(target.pos).expand(target.desired.getFluidState().isIn(net.minecraft.registry.tag.FluidTags.LAVA) ? .45 : .05);
            return mc.player.getBoundingBox().intersects(spill) || mc.player.getBoundingBox().offset(mc.player.getVelocity().multiply(3)).intersects(spill);
        }
        return !target.desired.getCollisionShape(mc.world, target.pos).isEmpty() && mc.player.getBoundingBox().intersects(new Box(target.pos));
    }
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
    static boolean matches(BlockState actual, BlockState desired) {
        if (actual.equals(desired) || actual.isAir() && desired.isAir()) return true;
        if (!actual.isOf(desired.getBlock())) return false;
        String block = Registries.BLOCK.getId(desired.getBlock()).toString();
        for (var property : desired.getProperties()) {
            if (SchematicStatePolicy.simulation(block, property.getName())) continue;
            if (desired.getBlock() instanceof FluidBlock && property.getName().equals("level")) {
                if (ImportedSchematic.source(desired) != ImportedSchematic.source(actual)) return false;
            } else if (!actual.get(property).equals(desired.get(property))) return false;
        }
        return true;
    }
    static boolean canStack(BlockState actual, BlockState desired) {
        if (!actual.isOf(desired.getBlock())) return false;
        boolean stacking = needsWaterlogging(actual, desired)
                || ImportedSchematic.value(desired, "type").equals("double") && !ImportedSchematic.value(actual, "type").equals("double");
        for (String field : List.of("layers", "candles", "pickles", "eggs")) {
            String a = ImportedSchematic.value(actual, field), d = ImportedSchematic.value(desired, field);
            if (!a.isEmpty() && !d.isEmpty() && Integer.parseInt(a) < Integer.parseInt(d)) stacking = true;
        }
        return stacking && placementCompatible(actual, desired);
    }
    static boolean placementCompatible(BlockState actual, BlockState desired) {
        if (!actual.isOf(desired.getBlock())) return false;
        String block = Registries.BLOCK.getId(desired.getBlock()).toString();
        for (var property : desired.getProperties()) {
            String name = property.getName();
            if (!SchematicStatePolicy.placementPropertyCompatible(block,name,ImportedSchematic.value(actual,name),ImportedSchematic.value(desired,name))) return false;
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
        unavailableReason = ""; deferredState = deferredPowerState = ""; approachTarget = null; checkedApproach = null;
        standSearchTarget = null; standCandidates.clear(); standCursor = 0; approachIncomplete = false;
    }
    private double progress() { return targets.isEmpty() ? 0 : matching / (double)targets.size(); }
    private Result waiting(String message, BlockPos routeGoal) { return new Result(false, false, message, progress(), routeGoal); }
    private Result blocked(String message) { if (mc.interactionManager != null) mc.interactionManager.cancelBlockBreaking(); return new Result(false, true, message, progress(), null); }
    private static void diagnostic(String detail) {
        if (Boolean.getBoolean("analyzer.nativeAutomationSmoke")) AnalyzerClient.LOGGER.info("ANALYZER_NATIVE_BUILDER: {}", detail);
    }
}
