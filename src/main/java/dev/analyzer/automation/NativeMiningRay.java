package dev.analyzer.automation;

import net.minecraft.block.ShapeContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.List;

/** Reachable outline samples for partial blocks and partially covered faces, using ordinary world rays. */
public final class NativeMiningRay {
    public record Aim(Vec3d point, BlockHitResult hit) { }
    private NativeMiningRay() { }
    public static Aim visibleAim(MinecraftClient mc, BlockPos target) { return visibleAim(mc, mc.player.getEyePos(), target); }
    public static Aim visibleAim(MinecraftClient mc, Vec3d eye, BlockPos target) {
        return visibleAim(mc, eye, target, 0);
    }
    static Aim visibleAim(MinecraftClient mc, Vec3d eye, BlockPos target, int sampleOffset) {
        double range = Math.max(.1, mc.player.getBlockInteractionRange() - .05);
        var points = samples(mc, target);
        for (int index = 0; index < points.size(); index++) {
            Vec3d point = points.get(Math.floorMod(index + sampleOffset, points.size()));
            var hit = trace(mc, eye, point);
            if (hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(target) && eye.squaredDistanceTo(hit.getPos()) <= range * range)
                return new Aim(point, hit);
        }
        return null;
    }
    public static BlockHitResult visibleHit(MinecraftClient mc, BlockPos target) {
        var aim = visibleAim(mc, target); return aim == null ? null : aim.hit();
    }
    public static BlockHitResult firstObstruction(MinecraftClient mc, BlockPos target) {
        return obstructions(mc, target).stream().findFirst().orElse(null);
    }
    static List<BlockHitResult> obstructions(MinecraftClient mc, BlockPos target) {
        double range = Math.max(.1, mc.player.getBlockInteractionRange() - .05);
        var hits = new ArrayList<BlockHitResult>();
        for (Vec3d point : samples(mc, target)) {
            var hit = trace(mc, mc.player.getEyePos(), point);
            if (hit.getType() == HitResult.Type.BLOCK && !hit.getBlockPos().equals(target)
                    && mc.player.getEyePos().squaredDistanceTo(hit.getPos()) <= range * range
                    && hits.stream().noneMatch(previous -> previous.getBlockPos().equals(hit.getBlockPos()))) hits.add(hit);
        }
        return List.copyOf(hits);
    }
    public static boolean reaches(MinecraftClient mc, Vec3d eye, Vec3d point, BlockPos target) {
        var hit = trace(mc, eye, point); return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(target);
    }
    private static BlockHitResult trace(MinecraftClient mc, Vec3d eye, Vec3d target) {
        return mc.world.raycast(new RaycastContext(eye, target, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player));
    }
    private static List<Vec3d> samples(MinecraftClient mc, BlockPos target) {
        List<Vec3d> points = new ArrayList<>(32);
        var shape = mc.world.getBlockState(target).getOutlineShape(mc.world, target, ShapeContext.of(mc.player));
        List<Box> boxes = shape.getBoundingBoxes();
        if (boxes.isEmpty()) return List.of(target.toCenterPos());
        for (Box box : boxes) {
            double cx = (box.minX + box.maxX) / 2, cy = (box.minY + box.maxY) / 2, cz = (box.minZ + box.maxZ) / 2;
            points.add(new Vec3d(target.getX() + cx, target.getY() + cy, target.getZ() + cz));
            // Inset face samples avoid voxel-edge ambiguity and can see around grass/vines without aiming through them.
            for (double a : new double[]{.2, .5, .8}) for (double b : new double[]{.2, .5, .8}) {
                double x = box.minX + (box.maxX - box.minX) * a, y = box.minY + (box.maxY - box.minY) * a;
                double z = box.minZ + (box.maxZ - box.minZ) * b;
                points.add(new Vec3d(target.getX() + x, target.getY() + y, target.getZ() + cz));
                points.add(new Vec3d(target.getX() + cx, target.getY() + y, target.getZ() + z));
                points.add(new Vec3d(target.getX() + x, target.getY() + cy, target.getZ() + z));
            }
        }
        return points;
    }
}
