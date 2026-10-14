package dev.analyzer.automation;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.EndPortalFrameBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.entity.EntityPose;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.List;

/** Loaded-portal discovery and ordinary placement/use. No commands, seed access or server world edits. */
public final class DimensionAutomation {
    private static final MinecraftClient MC = MinecraftClient.getInstance();
    private static final int RADIUS = 48, VERTICAL = 32, SCAN_BUDGET = 8192;
    private record Placement(BlockPos position, Block block, Item item) { }
    private final AutomationNavigator bridge;
    private final InventoryAutomation inventory;
    private final PortalTransition transition = new PortalTransition();
    private String requested = "", transportTarget = "", sourceDimension = "";
    private Object sourceWorld;
    private BlockPos portal, frame, searchOrigin, waitingPlacement, portalExit;
    private Block expectedPlacement;
    private List<Placement> placements = List.of();
    private int scanIndex, scanYMin, scanYMax, cooldown, placementWait, ignitionWait;
    private int oldSelected = -1;
    private BlockPos aimSupport;
    private Direction aimFace;
    private int aimAge = -1, aimMisses;
    private boolean scanned, travelling, accepted, cooldownExiting;
    public DimensionAutomation(AutomationNavigator bridge, InventoryAutomation inventory) { this.bridge = bridge; this.inventory = inventory; }
    public boolean expectsTransition(String oldDimension, String newDimension) { return transition.expects(oldDimension, newDimension); }
    public boolean awaitingTransition() { return transition.awaitingTransition(); }
    public String diagnostics() {
        String next = "none";
        if (MC.world != null) for (Placement placement : placements) if (!MC.world.getBlockState(placement.position()).isOf(placement.block())) {
            next = placement.position().toShortString() + " -> " + placement.block().getName().getString() + " (has " + MC.world.getBlockState(placement.position()) + ")"; break;
        }
        return "target=" + requested + ", source=" + sourceDimension + ", frame=" + frame + ", portal=" + portal + ", next=" + next
                + ", pending=" + waitingPlacement + ", exit=" + portalExit + ", portalCooldown=" + (MC.player == null ? -1 : MC.player.getPortalCooldown()) + ", route=" + bridge.status() + ", path=" + bridge.routeDiagnostics()
                + ", player=" + (MC.player == null ? "none" : MC.player.getPos() + " yaw=" + MC.player.getYaw() + " pitch=" + MC.player.getPitch());
    }
    /** The task owner checks same-server/save identity before calling this and retaining the mission. */
    public void acceptTransition(String newDimension) {
        String next = PortalTransition.normalize(newDimension);
        if (!transition.expects(sourceDimension, next)) throw new IllegalStateException("Unreviewed dimension transition");
        accepted = next.equals(requested);
        transition.clear(); sourceWorld = MC.world; sourceDimension = next;
        portal = null; frame = null; scanned = false; travelling = false; cooldownExiting = false; portalExit = null; placements = List.of(); waitingPlacement = null;
        if (!accepted) startScan();
    }
    public InventoryAutomation.Result tick(String target, int actionDelayTicks) {
        if (MC.player == null || MC.world == null || MC.interactionManager == null) return need("Join a world to use a portal.");
        if (Boolean.getBoolean("analyzer.smoke") && net.fabricmc.loader.api.FabricLoader.getInstance().isDevelopmentEnvironment() && MC.player.age % 100 == 0)
            dev.analyzer.AnalyzerClient.LOGGER.info("NATIVE_PORTAL_TRACE: {}", diagnostics());
        if (!PortalTransition.supported(target)) return need("Choose the Overworld, Nether or End.");
        if (!requested.equals(target)) {
            stop(); requested = target; sourceWorld = MC.world; sourceDimension = dimension(); startScan();
        }
        if (accepted || dimension().equals(requested)) return done("Verified arrival in " + label(requested));
        if (sourceWorld != MC.world) return need("The world changed before a verified portal transition. Review and resume this stage.");
        if (!(MC.player.currentScreenHandler instanceof PlayerScreenHandler)) return need("Close the workstation before travelling through a portal.");
        if (cooldown > 0) { cooldown--; return waitFor("Waiting for the portal action update"); }
        transportTarget = sourceDimension.equals("the_nether") && requested.equals("the_end")
                || sourceDimension.equals("the_end") && requested.equals("the_nether") ? "overworld" : requested;
        Block portalType = sourceDimension.equals("the_end") || transportTarget.equals("the_end") ? Blocks.END_PORTAL : Blocks.NETHER_PORTAL;
        if (waitingPlacement != null) {
            if (MC.world.getBlockState(waitingPlacement).isOf(expectedPlacement)) {
                waitingPlacement = null; placementWait = 0;
            } else if (++placementWait > 100) return need("The server rejected a portal-frame placement. The remaining frame is preserved; resume after checking protection and materials.");
            else return waitFor("Waiting for the server's portal-frame block");
        }
        if (portal != null && !MC.world.getBlockState(portal).isOf(portalType)) { portal = null; travelling = false; transition.clear(); }
        if (portal != null) return enter(portalType);
        if (!scanned) {
            scan(portalType, transportTarget.equals("the_end"));
            if (!scanned) return waitFor("Looking for a loaded portal within " + RADIUS + " blocks; bounded scan " + scanPercent() + "%");
            if (portal != null) return enter(portalType);
        }
        if (transportTarget.equals("the_end")) return activateEnd(actionDelayTicks);
        if (sourceDimension.equals("the_end")) return need("No loaded End exit portal was found nearby. Reach the real central exit portal; this task does not teleport or assume the dragon is defeated.");
        var kit = inventory.tickTransfer(); if (kit == null) kit = inventory.tick("portal-kit", 30);
        if (!kit.done()) return kit;
        if (frame == null) {
            if (InventoryAutomation.count(Items.OBSIDIAN) < 10) return need("No loaded Nether portal was found. A new frame needs 10 obsidian, 13 cobblestone for corners and a placement staircase, and flint and steel.");
            if (InventoryAutomation.count(Items.COBBLESTONE) < 13) return need("Need 13 cobblestone for portal corners and a safe placement staircase/platform.");
            frame = findNetherSite();
            if (frame == null) return need("Need a clear reachable 4 × 5 portal footprint on solid ground, away from fluids, fire and entities. No blocks were removed.");
            placements = netherPlacements(frame);
        }
        return construct(actionDelayTicks);
    }
    private InventoryAutomation.Result enter(Block type) {
        if (type == Blocks.NETHER_PORTAL && (MC.player.hasPortalCooldown() || cooldownExiting)) {
            InventoryAutomation.Result cooling = leaveCoolingPortal();
            if (cooling != null) return cooling;
        }
        double distance = MC.player.getPos().squaredDistanceTo(portal.toCenterPos());
        if (!travelling) {
            bridge.clearStatus(); transition.arm(sourceDimension, transportTarget);
            if (!bridge.travelXYZ(portal.getX(), portal.getY(), portal.getZ())) { transition.clear(); return need(bridge.status().message()); }
            travelling = true;
        }
        transition.observedEntry(distance <= 9);
        var status = bridge.status();
        if (status.failed()) { travelling = false; transition.clear(); return need("Portal route failed: " + status.message()); }
        // A path reaching its goal proves location, never transport. The engine accepts only the actual registry change.
        if (distance <= 2.25) return waitFor("Inside the " + (type == Blocks.NETHER_PORTAL ? "Nether" : "End") + " portal; waiting for the server to transfer to " + label(transportTarget));
        return waitFor("Walking into the verified portal · " + Math.round(Math.sqrt(distance)) + " blocks");
    }
    private InventoryAutomation.Result leaveCoolingPortal() {
        // Vanilla resets its real cooldown on every contact. Leaving the portal is
        // required before a return trip; standing in its target cell cannot reset it.
        if (insideNetherPortal(MC.player.getBoundingBox())) {
            if (bridge.status().failed()) return need("The portal cooldown exit is blocked: " + bridge.status().message());
            if (cooldownExiting && bridge.status().complete()) return need("The checked portal exit is still touching a portal block. Reach a clear supported stand and resume.");
            if (!cooldownExiting || !bridge.status().owned()) {
                transition.clear(); travelling = false;
                if (bridge.status().owned()) bridge.cancel();
                portalExit = exitStand();
                if (portalExit == null) return need("Portal return needs a dry supported standing place outside the portal. No safe exit cell was reachable nearby.");
                bridge.clearStatus();
                if (!bridge.travelForInteraction(portalExit)) return need(bridge.status().message());
                cooldownExiting = true;
            }
            return waitFor("Stepping outside the real portal before waiting for its return cooldown");
        }
        if (cooldownExiting && bridge.status().owned()) return waitFor("Finishing the checked standing place outside the portal");
        if (bridge.status().failed()) return need("The portal cooldown exit is blocked: " + bridge.status().message());
        if (MC.player.hasPortalCooldown()) return waitFor("Outside the portal; waiting " + ((MC.player.getPortalCooldown()+19)/20) + " seconds for the normal return cooldown");
        cooldownExiting = false; portalExit = null; travelling = false; bridge.clearStatus();
        return null;
    }
    private BlockPos exitStand() {
        BlockPos best = null; double distance = Double.MAX_VALUE;
        for (BlockPos position : BlockPos.iterate(portal.add(-4,-2,-4),portal.add(4,2,4))) {
            if (!MC.world.isChunkLoaded(position) || !MC.world.isChunkLoaded(position.up()) || dangerous(position) || dangerous(position.up()) || dangerous(position.down())
                    || !MC.world.getBlockState(position.down()).isSideSolidFullSquare(MC.world,position.down(),Direction.UP)
                    || !MC.world.getBlockState(position).getCollisionShape(MC.world,position).isEmpty()
                    || !MC.world.getBlockState(position.up()).getCollisionShape(MC.world,position.up()).isEmpty()) continue;
            Box body = new Box(position.getX()+.18,position.getY(),position.getZ()+.18,position.getX()+.82,position.getY()+1.8,position.getZ()+.82);
            if (insideNetherPortal(body) || !MC.world.getOtherEntities(MC.player,body).isEmpty()) continue;
            double candidate = MC.player.getPos().squaredDistanceTo(Vec3d.ofBottomCenter(position));
            if (candidate < distance) { distance = candidate; best = position.toImmutable(); }
        }
        return best;
    }
    private boolean insideNetherPortal(Box body) {
        for (BlockPos position : BlockPos.iterate(BlockPos.ofFloored(body.minX,body.minY,body.minZ),BlockPos.ofFloored(body.maxX-.001,body.maxY-.001,body.maxZ-.001)))
            if (MC.world.getBlockState(position).isOf(Blocks.NETHER_PORTAL)) return true;
        return false;
    }
    private void startScan() {
        if (MC.player == null || MC.world == null) return;
        searchOrigin = MC.player.getBlockPos(); scanIndex = 0;
        scanYMin = Math.max(MC.world.getBottomY(), searchOrigin.getY() - VERTICAL);
        scanYMax = Math.min(MC.world.getTopYInclusive(), searchOrigin.getY() + VERTICAL);
    }
    private int scanTotal() { return (RADIUS * 2 + 1) * (RADIUS * 2 + 1) * Math.max(1, scanYMax - scanYMin + 1); }
    private int scanPercent() { return (int) (100L * scanIndex / Math.max(1, scanTotal())); }
    private void scan(Block type, boolean endFrames) {
        int width = RADIUS * 2 + 1, layers = Math.max(1, scanYMax - scanYMin + 1);
        var mutable = new BlockPos.Mutable();
        int limit = Math.min(scanTotal(), scanIndex + SCAN_BUDGET);
        for (; scanIndex < limit; scanIndex++) {
            int index = scanIndex;
            int y = scanYMin + index % layers; index /= layers;
            int x = searchOrigin.getX() - RADIUS + index % width, z = searchOrigin.getZ() - RADIUS + index / width;
            mutable.set(x, y, z);
            if (!MC.world.isChunkLoaded(mutable)) continue;
            var state = MC.world.getBlockState(mutable);
            if (state.isOf(type)) {
                if (portal == null || MC.player.getPos().squaredDistanceTo(mutable.toCenterPos()) < MC.player.getPos().squaredDistanceTo(portal.toCenterPos())) portal = mutable.toImmutable();
            } else if (endFrames && state.isOf(Blocks.END_PORTAL_FRAME)) {
                BlockPos center = endFrameCenter(mutable, state.get(EndPortalFrameBlock.FACING));
                if (completeEndFrame(center) && (frame == null || MC.player.getPos().squaredDistanceTo(center.toCenterPos()) < MC.player.getPos().squaredDistanceTo(frame.toCenterPos()))) frame = center;
            } else if (type == Blocks.NETHER_PORTAL && state.isOf(Blocks.OBSIDIAN)) {
                for (int baseX = 1; baseX <= 2; baseX++) {
                    BlockPos origin = mutable.add(-baseX, 0, 0);
                    if (completeNetherFrame(origin) && (frame == null || MC.player.getPos().squaredDistanceTo(origin.toCenterPos()) < MC.player.getPos().squaredDistanceTo(frame.toCenterPos()))) frame = origin.toImmutable();
                }
            }
        }
        if (scanIndex >= scanTotal()) scanned = true;
    }
    private static BlockPos endFrameCenter(BlockPos frame, Direction facing) { return frame.offset(facing, 2); }
    private boolean completeNetherFrame(BlockPos origin) {
        for (int y = 0; y <= 4; y++) for (int x = 0; x <= 3; x++) {
            if ((y == 0 || y == 4) && (x == 0 || x == 3)) continue;
            BlockPos position = origin.add(x, y, 0);
            if (!MC.world.isChunkLoaded(position)) return false;
            if (y == 0 || y == 4 || x == 0 || x == 3) { if (!MC.world.getBlockState(position).isOf(Blocks.OBSIDIAN)) return false; }
            else if (!MC.world.getBlockState(position).isReplaceable() || !MC.world.getFluidState(position).isEmpty()) return false;
        }
        return true;
    }
    private List<BlockPos> endFrames(BlockPos center) {
        List<BlockPos> result = new ArrayList<>();
        for (int i = -1; i <= 1; i++) {
            result.add(center.add(i, 0, -2)); result.add(center.add(i, 0, 2));
            result.add(center.add(-2, 0, i)); result.add(center.add(2, 0, i));
        }
        return result;
    }
    private boolean completeEndFrame(BlockPos center) {
        for (BlockPos position : endFrames(center)) {
            var state = MC.world.getBlockState(position);
            if (!state.isOf(Blocks.END_PORTAL_FRAME)) return false;
            Direction toward = Math.abs(position.getX() - center.getX()) == 2 ? (position.getX() < center.getX() ? Direction.EAST : Direction.WEST) : position.getZ() < center.getZ() ? Direction.SOUTH : Direction.NORTH;
            if (state.get(EndPortalFrameBlock.FACING) != toward) return false;
        }
        return true;
    }
    private InventoryAutomation.Result activateEnd(int delay) {
        if (frame == null || !completeEndFrame(frame)) return need("No complete loaded stronghold portal was found. Reach a real stronghold portal, then resume; stronghold exploration and Eye-of-Ender following are not automated.");
        if (bridge.status().failed()) return need(bridge.status().message());
        if (bridge.status().owned()) return waitFor("Finishing the checked End-frame approach before using an eye");
        BlockPos missing = null; int required = 0;
        for (BlockPos position : endFrames(frame)) if (!MC.world.getBlockState(position).get(EndPortalFrameBlock.EYE)) { required++; if (missing == null) missing = position; }
        if (missing == null) {
            for (BlockPos position : BlockPos.iterate(frame.add(-1, 0, -1), frame.add(1, 0, 1))) if (MC.world.getBlockState(position).isOf(Blocks.END_PORTAL)) { portal = position.toImmutable(); return enter(Blocks.END_PORTAL); }
            if (++ignitionWait > 100) return need("All frame eyes are present but the server did not activate the End portal. Verify the complete frame orientation.");
            return waitFor("Waiting for the server's activated End portal");
        }
        if (InventoryAutomation.count(Items.ENDER_EYE) < required) return need("Need " + required + " owned Eyes of Ender to fill this complete stronghold portal.");
        BlockHitResult planned = new BlockHitResult(missing.toCenterPos().add(0, 0.31, 0), Direction.UP, missing, false);
        if (MC.player.getEyePos().squaredDistanceTo(planned.getPos()) > 18 || !visibleFrom(MC.player.getEyePos(), planned)) return approachHit(planned, "the empty End portal frame");
        var preparation = inventory.prepareItem(Items.ENDER_EYE, 1); if (!preparation.done()) return preparation;
        if (!readyInteraction(planned)) return waitFor("Settling and turning toward the empty End portal frame");
        var observed = MC.player.raycast(MC.player.getBlockInteractionRange(), 1, false);
        if (!(observed instanceof BlockHitResult hit) || !hit.getBlockPos().equals(missing)) return refineAim("the empty End portal frame");
        interact(Items.ENDER_EYE, hit);
        cooldown = Math.max(delay, 8); return waitFor("Placing an owned Eye of Ender through normal interaction");
    }
    private InventoryAutomation.Result construct(int delay) {
        if (bridge.status().failed()) return need(bridge.status().message());
        if (bridge.status().owned()) return waitFor("Finishing the checked portal approach before placing another block");
        for (Placement placement : placements) {
            if (MC.world.getBlockState(placement.position()).isOf(placement.block())) continue;
            if (!clearTerrain(placement.position()) || !MC.world.getOtherEntities(MC.player, new Box(placement.position())).isEmpty())
                return need("The portal footprint changed or another entity entered it. No occupied blocks were overwritten.");
            if (InventoryAutomation.count(placement.item()) < 1) return need("Need another " + placement.item().getName().getString() + " to finish the portal frame.");
            BlockHitResult hit = placementHit(placement.position());
            if (hit == null) return need("No supported reachable face for the next portal block. Reach the partly built frame and resume.");
            if (MC.player.getBoundingBox().intersects(new Box(placement.position())) || MC.player.getEyePos().squaredDistanceTo(hit.getPos()) > 18 || !visibleFrom(MC.player.getEyePos(), hit))
                return approachHit(hit, "the portal placement face");
            var preparation = inventory.prepareItem(placement.item(), 1); if (!preparation.done()) return preparation;
            if (!readyInteraction(hit)) return waitFor("Settling and turning toward the next portal block");
            var observed = MC.player.raycast(MC.player.getBlockInteractionRange(), 1, false);
            if (!(observed instanceof BlockHitResult actual) || !actual.getBlockPos().equals(hit.getBlockPos()) || actual.getSide() != hit.getSide()) return refineAim("the planned portal face");
            interact(placement.item(), hit); waitingPlacement = placement.position(); expectedPlacement = placement.block(); placementWait = 0;
            cooldown = Math.max(delay, 8); return waitFor("Building the verified portal frame through normal block placement");
        }
        BlockPos interior = frame.add(1, 1, 0), base = frame.add(1, 0, 0);
        if (MC.world.getBlockState(interior).isOf(Blocks.NETHER_PORTAL)) { portal = interior; return enter(Blocks.NETHER_PORTAL); }
        BlockHitResult planned = new BlockHitResult(base.toCenterPos().add(0, 0.5, 0), Direction.UP, base, false);
        if (MC.player.getEyePos().squaredDistanceTo(planned.getPos()) > 18 || !visibleFrom(MC.player.getEyePos(), planned)) return approachHit(planned, "the complete Nether frame");
        var preparation = inventory.prepareItem(Items.FLINT_AND_STEEL, 1); if (!preparation.done()) return preparation;
        if (!readyInteraction(planned)) return waitFor("Settling and turning toward the portal ignition surface");
        var observed = MC.player.raycast(MC.player.getBlockInteractionRange(), 1, false);
        if (!(observed instanceof BlockHitResult hit) || !hit.getBlockPos().equals(base) || hit.getSide() != Direction.UP) return refineAim("the portal ignition face");
        if (++ignitionWait > 15) return need("The frame is complete but the server did not activate it after normal ignition attempts. Check flint-and-steel durability and area protection.");
        interact(Items.FLINT_AND_STEEL, hit);
        cooldown = Math.max(delay, 10); return waitFor("Igniting the completed frame; waiting for real portal blocks");
    }
    private InventoryAutomation.Result approachHit(BlockHitResult hit, String label) {
        resetAim();
        BlockPos best = null; double distance = Double.MAX_VALUE;
        BlockPos support = hit.getBlockPos();
        Box placing = new Box(support.offset(hit.getSide()));
        for (BlockPos position : BlockPos.iterate(support.add(-3, -4, -3), support.add(3, 2, 3))) {
            // The partially built posts are isolated supports, not approach platforms.
            // Use the deliberately built staircase outside the frame plane instead.
            if (!placements.isEmpty() && frame != null && position.getZ() == frame.getZ()
                    && position.getX() >= frame.getX() && position.getX() <= frame.getX()+3
                    && position.getY() >= frame.getY() && position.getY() <= frame.getY()+5) continue;
            var floor = MC.world.getBlockState(position.down());
            if (!MC.world.isChunkLoaded(position) || !MC.world.isChunkLoaded(position.up()) || dangerous(position) || dangerous(position.down()) || dangerous(position.up())
                    || !floor.isSideSolidFullSquare(MC.world, position.down(), Direction.UP)
                    || !MC.world.getBlockState(position).getCollisionShape(MC.world, position).isEmpty()
                    || !MC.world.getBlockState(position.up()).getCollisionShape(MC.world, position.up()).isEmpty()) continue;
            Box occupant = new Box(position.getX() + 0.2, position.getY(), position.getZ() + 0.2, position.getX() + 0.8, position.getY() + 1.8, position.getZ() + 0.8);
            if (occupant.intersects(placing)) continue;
            Vec3d eye = Vec3d.ofBottomCenter(position).add(0, MC.player.getStandingEyeHeight(), 0);
            if (eye.squaredDistanceTo(hit.getPos()) > 18 || !robustVisibleFrom(eye, hit)) continue;
            double separation = MC.player.getPos().squaredDistanceTo(Vec3d.ofBottomCenter(position));
            if (separation < distance) { distance = separation; best = position.toImmutable(); }
        }
        if (best == null) return need("No supported standing position can see the next portal placement face. Keep the partial frame and reach a clear face before resuming.");
        if (bridge.status().failed()) return need(bridge.status().message());
        if (!bridge.status().owned()) {
            bridge.clearStatus();
            if (!bridge.travelForInteraction(best)) return need(bridge.status().message());
        }
        return waitFor("Walking onto a checked support to reach " + label);
    }
    private boolean readyInteraction(BlockHitResult hit) {
        if (!MC.player.isOnGround() || MC.player.getVelocity().horizontalLength() > .035 || MC.player.getPose() != EntityPose.STANDING) { resetAim(); return false; }
        if (!AutomationMotion.align(hit.getPos())) { resetAim(); return false; }
        if (!hit.getBlockPos().equals(aimSupport) || hit.getSide() != aimFace) {
            aimSupport = hit.getBlockPos().toImmutable(); aimFace = hit.getSide(); aimAge = MC.player.age; aimMisses = 0;
            return false;
        }
        return MC.player.age > aimAge;
    }
    private InventoryAutomation.Result refineAim(String label) {
        if (++aimMisses > 20) return need("The settled crosshair could not reach " + label + ". Clear the view and resume; no interaction was forced.");
        return waitFor("Refining the real crosshair toward " + label);
    }
    private void resetAim() { aimSupport = null; aimFace = null; aimAge = -1; aimMisses = 0; }
    private boolean visibleFrom(Vec3d eye, BlockHitResult planned) {
        Vec3d normal = Vec3d.of(planned.getSide().getVector());
        if (eye.subtract(planned.getPos()).dotProduct(normal) <= 0.02) return false;
        var observed = MC.world.raycast(new RaycastContext(eye, planned.getPos().subtract(normal.multiply(0.001)), RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, MC.player));
        return observed.getBlockPos().equals(planned.getBlockPos()) && observed.getSide() == planned.getSide();
    }
    private boolean robustVisibleFrom(Vec3d eye, BlockHitResult planned) {
        if (!visibleFrom(eye, planned)) return false;
        // Interaction routes settle within 0.15 blocks of the checked stand. Require
        // a little extra clearance so a scaffold corner cannot hide this face there.
        for (double dx : new double[]{-.16, .16}) for (double dz : new double[]{-.16, .16})
            if (!visibleFrom(eye.add(dx, 0, dz), planned)) return false;
        return true;
    }
    private BlockPos findNetherSite() {
        BlockPos player = MC.player.getBlockPos();
        for (int distance = 3; distance <= 7; distance++) for (int x = -distance; x <= distance; x++) for (int z = -distance; z <= distance; z++) {
            if (Math.max(Math.abs(x), Math.abs(z)) != distance) continue;
            BlockPos origin = player.add(x, 0, z);
            if (origin.getY() < MC.world.getBottomY() + 1 || origin.getY() + 4 > MC.world.getTopYInclusive()) continue;
            boolean usable = true;
            for (int dx = -1; dx <= 4 && usable; dx++) for (int dz = -4; dz <= 1 && usable; dz++) {
                BlockPos ground = origin.add(dx, -1, dz);
                var state = MC.world.getBlockState(ground);
                if (!MC.world.isChunkLoaded(ground) || !state.isSideSolidFullSquare(MC.world, ground, Direction.UP) || dangerous(ground)) { usable = false; break; }
                for (int dy = 0; dy <= 4; dy++) if (!clear(origin.add(dx, dy, dz))) { usable = false; break; }
            }
            if (usable) return origin;
        }
        return null;
    }
    private boolean clear(BlockPos position) {
        return clearTerrain(position) && MC.world.getOtherEntities(null, new Box(position)).isEmpty();
    }
    private boolean clearTerrain(BlockPos position) {
        return MC.world.isChunkLoaded(position) && MC.world.getBlockState(position).isReplaceable()
                && MC.world.getFluidState(position).isEmpty() && !dangerous(position);
    }
    private boolean dangerous(BlockPos position) {
        var state = MC.world.getBlockState(position);
        return !state.getFluidState().isEmpty() || state.isOf(Blocks.MAGMA_BLOCK) || state.isOf(Blocks.CAMPFIRE) || state.isOf(Blocks.SOUL_CAMPFIRE)
                || state.isOf(Blocks.FIRE) || state.isOf(Blocks.SOUL_FIRE) || state.isOf(Blocks.CACTUS) || state.isOf(Blocks.SWEET_BERRY_BUSH);
    }
    private static List<Placement> netherPlacements(BlockPos origin) {
        List<Placement> result = new ArrayList<>();
        // A real player must stand above a pillar's top face to place the upper frame. These six
        // supports form ordinary one-block stairs, keeping every placement inside a visible face.
        for (int z = -3; z <= -1; z++) result.add(new Placement(origin.add(0, 0, z), Blocks.COBBLESTONE, Items.COBBLESTONE));
        result.add(new Placement(origin.add(0, 1, -2), Blocks.COBBLESTONE, Items.COBBLESTONE));
        result.add(new Placement(origin.add(0, 1, -1), Blocks.COBBLESTONE, Items.COBBLESTONE));
        result.add(new Placement(origin.add(0, 2, -1), Blocks.COBBLESTONE, Items.COBBLESTONE));
        result.add(new Placement(origin.add(1, 0, -1), Blocks.COBBLESTONE, Items.COBBLESTONE));
        result.add(new Placement(origin.add(1, 1, -1), Blocks.COBBLESTONE, Items.COBBLESTONE));
        result.add(new Placement(origin.add(1, 2, -1), Blocks.COBBLESTONE, Items.COBBLESTONE));
        for (int x = 0; x <= 3; x++) result.add(new Placement(origin.add(x, 0, 0), x == 0 || x == 3 ? Blocks.COBBLESTONE : Blocks.OBSIDIAN, x == 0 || x == 3 ? Items.COBBLESTONE : Items.OBSIDIAN));
        for (int y = 1; y <= 3; y++) { result.add(new Placement(origin.add(0, y, 0), Blocks.OBSIDIAN, Items.OBSIDIAN)); result.add(new Placement(origin.add(3, y, 0), Blocks.OBSIDIAN, Items.OBSIDIAN)); }
        result.add(new Placement(origin.add(0, 4, 0), Blocks.COBBLESTONE, Items.COBBLESTONE));
        result.add(new Placement(origin.add(3, 4, 0), Blocks.COBBLESTONE, Items.COBBLESTONE));
        result.add(new Placement(origin.add(2, 4, 0), Blocks.OBSIDIAN, Items.OBSIDIAN));
        result.add(new Placement(origin.add(1, 4, 0), Blocks.OBSIDIAN, Items.OBSIDIAN));
        return List.copyOf(result);
    }
    private BlockHitResult placementHit(BlockPos target) {
        BlockHitResult fallback = null;
        for (Direction direction : Direction.values()) {
            BlockPos support = target.offset(direction);
            var state = MC.world.getBlockState(support);
            Direction face = direction.getOpposite();
            if (!MC.world.isChunkLoaded(support) || !state.isSideSolidFullSquare(MC.world, support, face) || dangerous(support)) continue;
            Vec3d hit = support.toCenterPos().add(Vec3d.of(face.getVector()).multiply(0.5));
            var planned = new BlockHitResult(hit, face, support, false);
            if (visibleFrom(MC.player.getEyePos(), planned)) return planned;
            if (fallback == null) fallback = planned;
        }
        return fallback;
    }
    private void interact(Item item, BlockHitResult hit) {
        int selected = MC.player.getInventory().selectedSlot, slot = -1;
        for (int i = 0; i < 9; i++) if (MC.player.getInventory().getStack(i).isOf(item)) { slot = i; break; }
        if (slot < 0) return;
        oldSelected = selected; MC.player.getInventory().selectedSlot = slot;
        MC.interactionManager.interactBlock(MC.player, Hand.MAIN_HAND, hit);
        MC.player.swingHand(Hand.MAIN_HAND); MC.player.getInventory().selectedSlot = selected; oldSelected = -1;
        resetAim();
    }
    public void stop() {
        if (oldSelected >= 0 && MC.player != null) MC.player.getInventory().selectedSlot = oldSelected;
        oldSelected = -1; transition.clear(); requested = ""; sourceWorld = null; sourceDimension = "";
        portal = null; frame = null; placements = List.of(); waitingPlacement = null; portalExit = null;
        travelling = false; accepted = false; scanned = false; cooldownExiting = false; cooldown = 0; ignitionWait = 0; placementWait = 0;
        resetAim();
    }
    private String dimension() { return PortalTransition.normalize(MC.world.getRegistryKey().getValue().toString()); }
    private static String label(String dimension) { return switch (dimension) { case "the_nether" -> "the Nether"; case "the_end" -> "the End"; default -> "the Overworld"; }; }
    private static InventoryAutomation.Result need(String detail) { return new InventoryAutomation.Result(false, true, detail); }
    private static InventoryAutomation.Result waitFor(String detail) { return new InventoryAutomation.Result(false, false, detail); }
    private static InventoryAutomation.Result done(String detail) { return new InventoryAutomation.Result(true, false, detail); }
}
