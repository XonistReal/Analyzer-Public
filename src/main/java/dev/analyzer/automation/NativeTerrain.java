package dev.analyzer.automation;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.*;

/** Loaded-world collision survey for original walking, one-block jumping, short drops and surface swimming. */
public final class NativeTerrain implements NativePathfinder.Grid {
    private final MinecraftClient mc;
    private final int radius, drop;
    private final NativePathfinder.Point origin;
    private final NativePathfinder.Target terminal;
    private final boolean avoid;
    private final double threatRadius;
    private final double halfWidth, height;
    private final List<MobEntity> threats;
    private final Map<BlockPos, BlockState> states = new HashMap<>();
    private final Map<BlockPos, List<Box>> shapes = new HashMap<>();
    private final Map<NativePathfinder.Point, List<NativePathfinder.Edge>> edges = new HashMap<>();
    private static final double EPS = .001;

    public NativeTerrain(MinecraftClient mc, NativePathfinder.Point origin, int radius, int drop, boolean avoid, double threatRadius) {
        this(mc, origin, radius, drop, avoid, threatRadius, null);
    }
    public NativeTerrain(MinecraftClient mc, NativePathfinder.Point origin, int radius, int drop, boolean avoid, double threatRadius, NativePathfinder.Target terminal) {
        this.mc = mc; this.origin = origin; this.radius = Math.max(8, Math.min(48, radius));
        this.terminal = terminal;
        this.drop = Math.max(1, Math.min(3, drop)); this.avoid = avoid; this.threatRadius = Math.max(4, Math.min(32, threatRadius));
        Box actualBody = mc.player.getBoundingBox();
        halfWidth = Math.max(.01, (actualBody.maxX - actualBody.minX) / 2 - EPS);
        height = Math.max(actualBody.maxY - actualBody.minY, mc.player.getDimensions(EntityPose.STANDING).height());
        threats = avoid ? mc.world.getEntitiesByClass(MobEntity.class, mc.player.getBoundingBox().expand(this.radius + this.threatRadius),
                entity -> entity.isAlive() && (entity instanceof HostileEntity || entity.getTarget() == mc.player)) : List.of();
    }
    public static NativePathfinder.Point playerPoint(MinecraftClient mc) {
        return new NativePathfinder.Point(mc.player.getBlockX(), (int)Math.round(mc.player.getY() * 16), mc.player.getBlockZ());
    }
    @Override public List<NativePathfinder.Edge> neighbors(NativePathfinder.Point point) {
        return edges.computeIfAbsent(point, this::surveyNeighbors);
    }
    @Override public boolean frontier(NativePathfinder.Point point) {
        return Math.abs((long)point.x() - origin.x()) >= radius - 1 || Math.abs((long)point.z() - origin.z()) >= radius - 1;
    }
    private List<NativePathfinder.Edge> surveyNeighbors(NativePathfinder.Point point) {
        List<NativePathfinder.Edge> result = new ArrayList<>(8);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (dx == 0 && dz == 0) continue;
            int x = point.x() + dx, z = point.z() + dz;
            if (Math.abs((long)x - origin.x()) > radius || Math.abs((long)z - origin.z()) > radius) continue;
            NativePathfinder.Point next = landing(x, z, point.y());
            if (next == null || Math.abs(next.y() - origin.y()) > radius || !sweep(center(point), center(next))) continue;
            if (dx != 0 && dz != 0) {
                // Corner cutting requires both side cells to be supported; two diagonal islands are not a bridge.
                NativePathfinder.Point sideX = landing(point.x() + dx, point.z(), point.y());
                NativePathfinder.Point sideZ = landing(point.x(), point.z() + dz, point.y());
                if (sideX == null || sideZ == null || !sweep(center(point), center(sideX)) || !sweep(center(point), center(sideZ))) continue;
            }
            double height = next.y() - point.y();
            double cost = Math.hypot(dx, dz) + Math.max(0, height) * 2 + Math.max(0, -height) * .35;
            BlockState feet = state(BlockPos.ofFloored(x + .5, next.y() + .1, z + .5));
            if (feet != null && feet.getFluidState().isIn(FluidTags.WATER)) cost += 3;
            if (avoid) for (MobEntity threat : threats) {
                double distance = Math.sqrt(threat.squaredDistanceTo(center(next)));
                if (distance < threatRadius) cost += Math.pow(1 - distance / threatRadius, 2) * (threat instanceof CreeperEntity ? 20 : 8);
            }
            result.add(new NativePathfinder.Edge(next, cost));
        }
        return List.copyOf(result);
    }
    /** Chooses a real support height, including slabs, snow and stairs. Unknown chunks have no landing. */
    public NativePathfinder.Point landing(int x, int z, double fromY) {
        List<Double> surfaces = new ArrayList<>();
        for (int y = MathHelper.floor(fromY + 1.03); y >= MathHelper.floor(fromY - drop) - 1; y--) {
            BlockPos pos = new BlockPos(x, y, z); BlockState state = state(pos);
            if (state == null) return null;
            if (hazard(state)) continue;
            // An active End portal is a terminal interaction surface, not permission to route over lava elsewhere.
            if (state.isOf(Blocks.END_PORTAL) && terminal != null && terminal.x() == x && terminal.z() == z
                    && terminal.y() != null && terminal.y() == y) surfaces.add(y + .75);
            if (state.getFluidState().isIn(FluidTags.WATER)) {
                BlockState above = state(pos.up());
                if (above != null && above.getFluidState().isEmpty()) surfaces.add(y + state.getFluidState().getHeight(mc.world, pos) - .8);
            }
            for (Box shape : shapes(pos, state)) {
                if (shape.maxX > .5 - EPS && shape.minX < .5 + EPS && shape.maxZ > .5 - EPS && shape.minZ < .5 + EPS) surfaces.add(y + shape.maxY);
            }
        }
        surfaces.sort(Comparator.reverseOrder());
        for (double y : surfaces) {
            if (y > fromY + 1.03 || y < fromY - drop - EPS) continue;
            NativePathfinder.Point point = new NativePathfinder.Point(x, (int)Math.round(y * 16), z);
            if (clear(body(center(point)))) return point;
        }
        return null;
    }
    /** The executor calls a fresh survey each tick; cached planning observations never authorize a stale step. */
    public boolean sweep(Vec3d from, Vec3d to) {
        if (to.y - from.y > 1.03 || from.y - to.y > drop + EPS) return false;
        double lift = Math.max(from.y, to.y);
        Box rise = body(from).union(body(new Vec3d(from.x, lift, from.z)));
        Box travel = body(new Vec3d(from.x, lift, from.z)).union(body(new Vec3d(to.x, lift, to.z)));
        Box land = body(to).union(body(new Vec3d(to.x, lift, to.z)));
        return clear(rise) && clear(travel) && clear(land);
    }
    private Box body(Vec3d point) { return new Box(point.x - halfWidth, point.y + EPS, point.z - halfWidth, point.x + halfWidth, point.y + height, point.z + halfWidth); }
    private boolean clear(Box box) {
        for (int x = MathHelper.floor(box.minX); x <= MathHelper.floor(box.maxX - EPS); x++)
            for (int z = MathHelper.floor(box.minZ); z <= MathHelper.floor(box.maxZ - EPS); z++)
                for (int y = MathHelper.floor(box.minY); y <= MathHelper.floor(box.maxY - EPS); y++) {
                    BlockPos pos = new BlockPos(x, y, z); BlockState state = state(pos);
                    if (state == null || hazard(state)) return false;
                    for (Box shape : shapes(pos, state)) if (shape.offset(x, y, z).intersects(box)) return false;
                }
        return true;
    }
    private BlockState state(BlockPos pos) {
        if (!mc.world.isInBuildLimit(pos) || !mc.world.getWorldBorder().contains(pos) || !mc.world.isChunkLoaded(pos)) return null;
        return states.computeIfAbsent(pos.toImmutable(), mc.world::getBlockState);
    }
    private List<Box> shapes(BlockPos pos, BlockState state) {
        return shapes.computeIfAbsent(pos.toImmutable(), key -> state.getCollisionShape(mc.world, key, ShapeContext.of(mc.player)).getBoundingBoxes());
    }
    public static Vec3d center(NativePathfinder.Point point) { return new Vec3d(point.x() + .5, point.y(), point.z() + .5); }
    public static boolean hazard(BlockState state) {
        return state.getFluidState().isIn(FluidTags.LAVA) || state.isOf(Blocks.FIRE) || state.isOf(Blocks.SOUL_FIRE) || state.isOf(Blocks.MAGMA_BLOCK)
                || state.isOf(Blocks.CACTUS) || state.isOf(Blocks.CAMPFIRE) || state.isOf(Blocks.SOUL_CAMPFIRE) || state.isOf(Blocks.SWEET_BERRY_BUSH)
                || state.isOf(Blocks.POWDER_SNOW) || state.isOf(Blocks.WITHER_ROSE);
    }
}
