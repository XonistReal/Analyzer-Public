package dev.analyzer.automation;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Short, loaded-terrain escape checks. It never requests a route, edits blocks or moves the player. */
public final class EscapeNavigator {
    public record Route(Vec3d direction, boolean jump, boolean swimming, String terrain) { }
    private record Step(double feetY, boolean jump, boolean water, String terrain) { }
    private record Support(double top, boolean water) { }
    private record Corridor(double distance, boolean jump, boolean water, String terrain) { }
    private static final double STEP = .25, LOOK_AHEAD = 2.0, MAX_HEIGHT_CHANGE = 1.03;
    private static final double EPSILON = .001;

    private EscapeNavigator() { }

    /**
     * Select a checked short corridor, prioritizing an imminent blast over ordinary pursuers.
     * Surface water is usable only with a clear path upward and air above it; jumping supplies buoyancy.
     * Every call rechecks the current world, so an explosion crater cannot leave a cached route behind.
     */
    public static Route choose(MinecraftClient mc, List<LivingEntity> threats) {
        if (mc.world == null || mc.player == null || threats.isEmpty() || mc.player.isInLava()
                || mc.player.isClimbing() || mc.player.isGliding()) return null;
        Survey survey = new Survey(mc);
        List<LivingEntity> urgent = threats.stream().filter(e -> e instanceof CreeperEntity c
                && (c.isIgnited() || c.getFuseSpeed() > 0 || c.squaredDistanceTo(mc.player) < (c.isCharged() ? 144 : 64))).toList();
        List<LivingEntity> priority = urgent.isEmpty() ? threats : urgent;
        Vec3d origin = mc.player.getPos(), away = Vec3d.ZERO;
        for (LivingEntity threat : priority) {
            Vec3d delta = origin.subtract(threat.getPos()).multiply(1, 0, 1);
            double length = delta.length();
            if (length > .01) away = away.add(delta.multiply(1 / (length * length)));
        }
        if (away.lengthSquared() < .0001) away = Vec3d.fromPolar(0, mc.player.getYaw() + 180).multiply(1, 0, 1);
        away = away.normalize();
        double yaw = Math.atan2(away.z, away.x), before = nearest(origin, priority), bestScore = -Double.MAX_VALUE;
        Route best = null;
        for (int i = 0; i < 12; i++) {
            double angle = yaw + i * Math.PI / 6;
            Vec3d direction = new Vec3d(Math.cos(angle), 0, Math.sin(angle));
            Corridor corridor = survey.corridor(direction);
            if (corridor == null || corridor.distance < .75) continue;
            if (!urgent.isEmpty()) {
                boolean approachesBlast = false;
                for (double distance = STEP; distance <= corridor.distance + EPSILON; distance += STEP) {
                    if (nearest(origin.add(direction.multiply(distance)), priority) < before - .05) {
                        approachesBlast = true;
                        break;
                    }
                }
                if (approachesBlast) continue;
            }
            Vec3d destination = origin.add(direction.multiply(corridor.distance));
            double after = nearest(destination, priority);
            // A blast escape may approach another mob; that risk is scored rather than rejecting every exit.
            if (after <= before + .15) continue;
            double risk = 0;
            for (LivingEntity threat : threats) {
                double separation = Math.sqrt(destination.squaredDistanceTo(threat.getPos()));
                if (separation < 4) risk += (4 - separation) * (threat instanceof CreeperEntity ? 3 : 1);
            }
            double score = after - before + direction.dotProduct(away) * 2 + corridor.distance
                    - risk - (corridor.water ? .4 : 0) - (corridor.jump ? .15 : 0);
            if (score > bestScore) {
                bestScore = score;
                best = new Route(direction, corridor.jump, corridor.water, corridor.terrain);
            }
        }
        return best;
    }

    private static double nearest(Vec3d point, List<LivingEntity> entities) {
        double distance = Double.MAX_VALUE;
        for (LivingEntity entity : entities) distance = Math.min(distance, point.squaredDistanceTo(entity.getPos()));
        return distance;
    }

    /** Explicit developer-test diagnostics only; never called or printed by the protection loop. */
    public static String diagnostics(MinecraftClient mc, List<LivingEntity> threats) {
        if (mc.world == null || mc.player == null) return "No world/player";
        Survey survey = new Survey(mc);
        StringBuilder result = new StringBuilder("waterSurface=").append(survey.initialWaterSurface)
                .append(", body=").append(mc.player.getBoundingBox()).append(", threats=");
        for (LivingEntity threat : threats) result.append(threat.getName().getString()).append('@').append(threat.getPos()).append(';');
        for (int i = 0; i < 12; i++) {
            double angle = i * Math.PI / 6;
            Corridor corridor = survey.corridor(new Vec3d(Math.cos(angle), 0, Math.sin(angle)));
            result.append(" | ").append(i * 30).append("deg: ");
            if (corridor != null) result.append(corridor.distance).append("m ").append(corridor.terrain).append(" / ");
            result.append(survey.lastRejection);
        }
        return result.toString();
    }

    private static final class Survey {
        private final MinecraftClient mc;
        private final Vec3d origin;
        private final Box originalBody;
        private final double radius, height, initialWaterSurface;
        private final Map<BlockPos, BlockState> states = new HashMap<>();
        private final Map<BlockPos, List<Box>> shapes = new HashMap<>();
        private String lastRejection = "none";

        Survey(MinecraftClient mc) {
            this.mc = mc;
            origin = mc.player.getPos(); originalBody = mc.player.getBoundingBox();
            // Use the actual footprint: an inflated origin box would reject every escape beside a wall.
            radius = Math.max(.25, (originalBody.maxX - originalBody.minX) / 2);
            // A swimming pose must still have room to rise to an ordinary standing pose at the surface.
            height = Math.max(1.8, originalBody.maxY - originalBody.minY);
            initialWaterSurface = mc.player.isTouchingWater() ? waterSurface(origin.x, origin.z, origin.y) : Double.NaN;
        }

        Corridor corridor(Vec3d direction) {
            lastRejection = "none";
            Vec3d previous = origin;
            double feetY = origin.y, reached = 0;
            boolean jump = false, water = false;
            String terrain = "checked ground";
            for (double distance = STEP; distance <= LOOK_AHEAD + EPSILON; distance += STEP) {
                Vec3d point = origin.add(direction.multiply(distance));
                Step step = step(point.x, point.z, feetY);
                if (step == null) break;
                double lift = Math.max(feetY, step.feetY);
                Box from = body(previous.x, lift, previous.z), to = body(point.x, lift, point.z);
                // Check the complete horizontal sweep and the rise/drop, not only the endpoint.
                if (!clear(from.union(to))) { lastRejection = "horizontal swept collision"; break; }
                if (!clear(body(previous.x, feetY, previous.z).union(from))) { lastRejection = "rise collision"; break; }
                if (!clear(body(point.x, step.feetY, point.z).union(to))) { lastRejection = "drop collision"; break; }
                feetY = step.feetY; previous = new Vec3d(point.x, feetY, point.z); reached = distance;
                jump |= step.jump; water |= step.water;
                if (!step.terrain.equals("checked ground")) terrain = step.terrain;
            }
            return reached == 0 ? null : new Corridor(reached, jump, water, terrain);
        }

        private Step step(double x, double z, double previousY) {
            double surface = waterSurface(x, z, previousY);
            if (!Double.isNaN(surface) && surface <= previousY + 1.4 && surface >= previousY - 1.1) {
                double floatY = Math.max(previousY, surface - .9);
                // Land entry requires shallow water. Existing swimmers may use a clear, loaded surface.
                boolean shallow = supportTop(x, z, surface - 2.03, surface) > surface - 2.03;
                if ((mc.player.isTouchingWater() || shallow) && clear(body(x, floatY, z))
                        && clear(body(x, floatY, z).union(body(x, surface + .03, z)))) {
                    return new Step(floatY, true, true, "surface water");
                }
            }
            double startPlane = Double.isNaN(initialWaterSurface) ? origin.y : Math.max(origin.y, initialWaterSurface);
            // A dry candidate center can still be the first shore landing from submerged feet.
            // Measure that rise from the existing water surface, not from the underwater foot Y.
            double allowedReference = Math.max(previousY, startPlane);
            if (!Double.isNaN(surface)) allowedReference = Math.max(allowedReference, surface);
            double minimum = Math.max(previousY - MAX_HEIGHT_CHANGE, origin.y - MAX_HEIGHT_CHANGE);
            double maximum = Math.min(allowedReference + MAX_HEIGHT_CHANGE, startPlane + MAX_HEIGHT_CHANGE);
            double top = -Double.MAX_VALUE, low = Double.MAX_VALUE;
            boolean water = false, land = false;
            for (double[] offset : footprint()) {
                Support support = support(x + offset[0], z + offset[1], minimum, maximum, previousY);
                if (support == null) { lastRejection = "missing support " + (x + offset[0]) + "," + (z + offset[1]); return null; }
                top = Math.max(top, support.top); low = Math.min(low, support.top);
                water |= support.water; land |= !support.water;
            }
            if (!land) { lastRejection = "only-water support with blocked flotation"; return null; }
            if (top - low > MAX_HEIGHT_CHANGE) { lastRejection = "support spread top=" + top + ", low=" + low; return null; }
            if (!clear(body(x, top + EPSILON, z))) { lastRejection = "landing collision at " + x + "," + top + "," + z; return null; }
            double reference = Double.isNaN(initialWaterSurface) ? previousY : Math.max(previousY, initialWaterSurface);
            if (top - reference > MAX_HEIGHT_CHANGE || previousY - top > MAX_HEIGHT_CHANGE) {
                lastRejection = "height change top=" + top + ", reference=" + reference + ", previous=" + previousY;
                return null;
            }
            boolean hop = top > previousY + .45 || water || mc.player.isTouchingWater();
            String terrain = water || mc.player.isTouchingWater() ? "checked shore step"
                    : hop ? "checked one-block step" : top < previousY - .3 ? "checked short drop" : "checked ground";
            return new Step(top + EPSILON, hop, water, terrain);
        }

        private Support support(double x, double z, double minimum, double maximum, double previousY) {
            double water = waterSurface(x, z, previousY);
            double solid = supportTop(x, z, minimum, maximum);
            // A corner is a support point, not the center of a second player-sized box. Its narrow
            // airway was checked by waterSurface(); the complete real body is checked after choosing
            // the highest support and again along the movement sweep. Duplicating that box here
            // wrongly intersects a raised snow bank beside a water corner.
            if (!Double.isNaN(water) && water >= minimum && water <= maximum && (solid < water - .05))
                return new Support(water, true);
            return solid == -Double.MAX_VALUE ? null : new Support(solid, false);
        }

        /** Shape tops under the complete footprint accept slabs/snow/stairs and respect open trapdoors. */
        private double supportTop(double x, double z, double minimum, double maximum) {
            double top = -Double.MAX_VALUE;
            for (int y = MathHelper.floor(maximum); y >= MathHelper.floor(minimum) - 1; y--) {
                BlockPos pos = BlockPos.ofFloored(x, y, z);
                BlockState state = state(pos);
                if (state == null || danger(state) || !state.getFluidState().isEmpty() && !state.getFluidState().isIn(FluidTags.WATER)) continue;
                for (Box shape : shapes.computeIfAbsent(pos, p -> state.getCollisionShape(mc.world, p, ShapeContext.of(mc.player)).getBoundingBoxes())) {
                    double candidate = pos.getY() + shape.maxY;
                    if (candidate < minimum - EPSILON || candidate > maximum + EPSILON) continue;
                    if (x >= pos.getX() + shape.minX - EPSILON && x <= pos.getX() + shape.maxX + EPSILON
                            && z >= pos.getZ() + shape.minZ - EPSILON && z <= pos.getZ() + shape.maxZ + EPSILON)
                        top = Math.max(top, candidate);
                }
            }
            return top;
        }

        /** Only water connected to a nearby open surface; underwater ceilings are never accepted. */
        private double waterSurface(double x, double z, double feetY) {
            for (int y = MathHelper.floor(feetY + 1.25); y >= MathHelper.floor(feetY - .8); y--) {
                BlockPos pos = BlockPos.ofFloored(x, y, z);
                BlockState state = state(pos);
                if (state == null) return Double.NaN;
                FluidState fluid = state.getFluidState();
                if (!fluid.isIn(FluidTags.WATER)) continue;
                BlockState above = state(pos.up());
                if (above == null || !above.getFluidState().isEmpty() || danger(above)) continue;
                double surface = y + fluid.getHeight(mc.world, pos);
                // Checking a tiny column here does not replace the full player sweep in corridor().
                Box airway = new Box(x - .02, surface + .02, z - .02, x + .02, surface + height + .03, z + .02);
                if (clear(airway)) return surface;
            }
            return Double.NaN;
        }

        private double[][] footprint() {
            double inset = radius - EPSILON;
            return new double[][]{{0, 0}, {-inset, -inset}, {-inset, inset}, {inset, -inset}, {inset, inset}};
        }

        private Box body(double x, double feetY, double z) {
            return new Box(x - radius, feetY, z - radius, x + radius, feetY + height, z + radius);
        }

        private boolean clear(Box body) {
            for (int x = MathHelper.floor(body.minX); x <= MathHelper.floor(body.maxX - EPSILON); x++)
                for (int z = MathHelper.floor(body.minZ); z <= MathHelper.floor(body.maxZ - EPSILON); z++)
                    for (int y = MathHelper.floor(body.minY); y <= MathHelper.floor(body.maxY - EPSILON); y++) {
                        BlockState state = state(new BlockPos(x, y, z));
                        if (state == null || danger(state) || state.getFluidState().isIn(FluidTags.LAVA)) return false;
                    }
            return mc.world.isSpaceEmpty(mc.player, body);
        }

        private BlockState state(BlockPos pos) {
            if (!mc.world.isChunkLoaded(pos)) return null;
            return states.computeIfAbsent(pos.toImmutable(), mc.world::getBlockState);
        }

        private static boolean danger(BlockState state) {
            return state.isOf(Blocks.LAVA) || state.isOf(Blocks.FIRE) || state.isOf(Blocks.SOUL_FIRE) || state.isOf(Blocks.MAGMA_BLOCK)
                    || state.isOf(Blocks.CACTUS) || state.isOf(Blocks.CAMPFIRE) || state.isOf(Blocks.SOUL_CAMPFIRE)
                    || state.isOf(Blocks.SWEET_BERRY_BUSH) || state.isOf(Blocks.POWDER_SNOW) || state.isOf(Blocks.WITHER_ROSE);
        }
    }
}
