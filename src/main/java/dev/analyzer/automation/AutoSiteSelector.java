package dev.analyzer.automation;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.Heightmap;
import net.minecraft.registry.tag.BlockTags;
import net.fabricmc.loader.api.FabricLoader;
import dev.analyzer.AnalyzerClient;

import java.util.*;

/** Original local build-site survey; unknown chunks never become assumed empty terrain. */
public final class AutoSiteSelector {
    private static final int MAX_CANDIDATES = 64, MAX_SURVEY_BLOCKS = 1_048_576, HEIGHT_RADIUS = 48;
    private AutoSiteSelector() { }
    public record Selection(Optional<BlockPos> origin, double score, List<String> rejections) {
        public Selection { rejections = List.copyOf(rejections); }
        public String message() { return origin.map(pos -> "Selected loaded site at " + pos.toShortString()).orElse("No safe loaded site: " + String.join("; ", rejections)); }
    }
    public static Selection choose(ClientWorld world, BlockPos player, BuildSchematic schematic, int radius, int hostileRadius) {
        if (world == null || player == null || schematic == null) throw new IllegalArgumentException("Join a world and choose a schematic first");
        if (radius < 4 || radius > 128 || hostileRadius < 4 || hostileRadius > 48) throw new IllegalArgumentException("Site radius must be 4–128 and hostile clearance 4–48 blocks");
        int volume = SchematicImporter.volume(schematic.widthX(), schematic.heightY(), schematic.lengthZ());
        int stride = Math.max(4, Math.max(schematic.widthX(), schematic.lengthZ()) / 2);
        List<BlockPos> columns = new ArrayList<>();
        for (int dz = -radius; dz <= radius; dz += stride) for (int dx = -radius; dx <= radius; dx += stride) {
            if ((long)dx * dx + (long)dz * dz > (long)radius * radius) continue;
            int x = player.getX() + dx - schematic.widthX() / 2, z = player.getZ() + dz - schematic.lengthZ() / 2;
            if (!world.isChunkLoaded(new BlockPos(x, player.getY(), z))) continue;
            columns.add(new BlockPos(x, player.getY(), z));
        }
        columns.sort(Comparator.comparingDouble(pos -> horizontalDistance(pos, player, schematic)));
        List<BlockPos> candidates = new ArrayList<>();
        for (BlockPos column : columns.subList(0, Math.min(MAX_CANDIDATES, columns.size()))) {
            for (int y : observedHeights(world, column, schematic.heightY())) candidates.add(new BlockPos(column.getX(), y, column.getZ()));
        }
        candidates.sort(Comparator.comparingDouble(pos -> Math.hypot(horizontalDistance(pos, player, schematic), pos.getY() - player.getY())));
        if (FabricLoader.getInstance().isDevelopmentEnvironment() && Boolean.getBoolean("analyzer.nativeAutomationSmoke"))
            AnalyzerClient.LOGGER.info("Native build-site survey: player={}, reportedMotionHeight={}, observedCandidates={}", player,
                    world.getTopY(Heightmap.Type.MOTION_BLOCKING, player.getX(), player.getZ()), candidates.stream().limit(8).toList());
        LinkedHashSet<String> rejections = new LinkedHashSet<>(); BlockPos best = null; double score = Double.NEGATIVE_INFINITY;
        int budget = Math.max(1, MAX_SURVEY_BLOCKS / Math.max(1, volume + schematic.widthX() * schematic.lengthZ() * 3));
        int checked = 0;
        for (BlockPos origin : candidates) {
            if (checked++ >= Math.min(MAX_CANDIDATES, budget)) break;
            BuildSitePolicy.Result result = survey(world, origin, schematic, hostileRadius,
                    Math.hypot(horizontalDistance(origin, player, schematic), origin.getY() - player.getY()));
            if (result.accepted() && result.score() > score) { best = origin; score = result.score(); }
            else if (!result.accepted() && rejections.size() < 8) rejections.add(result.reason());
        }
        if (candidates.isEmpty()) rejections.add(columns.isEmpty() ? "No nearby candidate chunk is loaded" : "No dry supporting surface was observed in the bounded nearby height survey");
        if (best == null && checked >= budget && candidates.size() > budget) rejections.add("The bounded survey did not cover every candidate; move closer to clear level ground");
        return new Selection(Optional.ofNullable(best), score, List.copyOf(rejections));
    }
    /** Client-only heightmaps can be absent or stale. Actual loaded blocks authorize a support height. */
    private static List<Integer> observedHeights(ClientWorld world, BlockPos column, int height) {
        int lowest = world.getBottomY() + 1, highest = world.getTopYInclusive() - height + 1;
        if (highest < lowest) return List.of();
        var heights = new LinkedHashSet<Integer>();
        int reported = world.getTopY(Heightmap.Type.MOTION_BLOCKING, column.getX(), column.getZ());
        if (observedSupport(world, column.getX(), reported, column.getZ(), lowest, highest)) heights.add(reported);
        int center = Math.clamp(column.getY(), lowest, highest);
        // Inspect the local level first, including cave floors and edited terrain that a heightmap misses.
        for (int offset = 0; offset <= HEIGHT_RADIUS && heights.size() < 4; offset++) {
            int below = center - offset;
            if (observedSupport(world, column.getX(), below, column.getZ(), lowest, highest)) heights.add(below);
            int above = center + offset;
            if (offset > 0 && observedSupport(world, column.getX(), above, column.getZ(), lowest, highest)) heights.add(above);
        }
        return List.copyOf(heights);
    }
    private static boolean observedSupport(ClientWorld world, int x, int y, int z, int lowest, int highest) {
        if (y < lowest || y > highest) return false;
        BlockPos feet = new BlockPos(x, y, z), support = feet.down();
        if (!world.isChunkLoaded(feet) || !world.isChunkLoaded(support) || !world.isInBuildLimit(support)
                || !world.getWorldBorder().contains(feet) || !world.getWorldBorder().contains(support)) return false;
        BlockState floor = world.getBlockState(support);
        if (floor.isIn(BlockTags.LEAVES) || floor.hasBlockEntity() || world.getBlockEntity(support) != null
                || !floor.isFullCube(world, support) || hazard(floor) || !floor.getFluidState().isEmpty()) return false;
        for (int dy = 0; dy < 2; dy++) {
            BlockPos pos = feet.up(dy);
            if (!world.isInBuildLimit(pos) || !world.getWorldBorder().contains(pos) || !world.isChunkLoaded(pos)) return false;
            BlockState state = world.getBlockState(pos);
            if ((!state.isAir() && !state.isReplaceable()) || hazard(state) || !state.getFluidState().isEmpty() || state.hasBlockEntity()) return false;
        }
        return true;
    }
    public static String siteProblem(ClientWorld world, BlockPos origin, BuildSchematic schematic) {
        BuildSitePolicy.Result result = survey(world, origin, schematic, 12, 0);
        return result.accepted() ? "" : result.reason();
    }
    private static double horizontalDistance(BlockPos origin, BlockPos player, BuildSchematic schematic) {
        return Math.hypot(origin.getX() + schematic.widthX() / 2.0 - player.getX(), origin.getZ() + schematic.lengthZ() / 2.0 - player.getZ());
    }
    private static BuildSitePolicy.Result survey(ClientWorld world, BlockPos origin, BuildSchematic schematic, int hostileRadius, double distance) {
        boolean loaded = true, bounds = true; int occupied = 0, fluids = 0, hazards = 0, supported = 0, ground = 0;
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int z = 0; z < schematic.lengthZ(); z++) for (int x = 0; x < schematic.widthX(); x++) {
            pos.set(origin.getX() + x, origin.getY() - 1, origin.getZ() + z);
            ground++;
            if (!world.isInBuildLimit(pos) || !world.getWorldBorder().contains(pos)) { bounds = false; continue; }
            if (!world.isChunkLoaded(pos)) { loaded = false; continue; }
            BlockState floor = world.getBlockState(pos);
            if (hazard(floor)) hazards++;
            if (!floor.getFluidState().isEmpty()) fluids++;
            if (world.getBlockEntity(pos) != null || floor.hasBlockEntity()) occupied++;
            if (floor.isFullCube(world, pos) && !hazard(floor) && floor.getFluidState().isEmpty()) supported++;
            // Uniform foundation support avoids treating already completed walls as a terrain slope.
            for (int y = 0; y < schematic.heightY(); y++) {
                pos.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                if (!world.isInBuildLimit(pos) || !world.getWorldBorder().contains(pos)) { bounds = false; continue; }
                if (!world.isChunkLoaded(pos)) { loaded = false; continue; }
                BlockState state = world.getBlockState(pos);
                if (!schematic.inSchematic(x, y, z, state)) {
                    if (hazard(state)) hazards++;
                    if (!state.getFluidState().isEmpty()) fluids++;
                    continue;
                }
                BlockState desired = schematic.desiredState(x, y, z, state, List.of());
                boolean expectedFluid = NativeBuilder.fluidAllowed(state, desired);
                if (!state.getFluidState().isEmpty() && !expectedFluid) fluids++;
                // A resumed identical ordinary block is harmless; saved block entities are always preserved.
                boolean preserved = NativeBuilder.matches(state, desired) || NativeBuilder.placementCompatible(state, desired);
                if (hazard(state) && !preserved && !expectedFluid) hazards++;
                if (!preserved && (state.hasBlockEntity() || world.getBlockEntity(pos) != null || !state.isAir() && !state.isReplaceable())) occupied++;
            }
        }
        Box footprint = new Box(origin.getX(), origin.getY(), origin.getZ(), origin.getX() + schematic.widthX(), origin.getY() + schematic.heightY(), origin.getZ() + schematic.lengthZ());
        int hostiles = world.getEntitiesByClass(HostileEntity.class, footprint.expand(hostileRadius), entity -> entity.isAlive()).size();
        boolean accessible = false;
        for (int x = -1; x <= schematic.widthX(); x++) {
            accessible |= access(world, origin.add(x, 0, -1)); accessible |= access(world, origin.add(x, 0, schematic.lengthZ()));
        }
        for (int z = 0; z < schematic.lengthZ(); z++) {
            accessible |= access(world, origin.add(-1, 0, z)); accessible |= access(world, origin.add(schematic.widthX(), 0, z));
        }
        int slope = supported == ground ? 0 : 2;
        return BuildSitePolicy.evaluate(new BuildSitePolicy.Survey(loaded, bounds, occupied, fluids, hazards, hostiles, ground, supported, slope, accessible, distance));
    }
    private static boolean access(ClientWorld world, BlockPos pos) {
        for (int y = -1; y <= 1; y++) if (!world.isChunkLoaded(pos.up(y)) || !world.isInBuildLimit(pos.up(y)) || !world.getWorldBorder().contains(pos.up(y))) return false;
        BlockState floor = world.getBlockState(pos.down()), feet = world.getBlockState(pos), head = world.getBlockState(pos.up());
        return floor.isFullCube(world, pos.down()) && !hazard(floor) && floor.getFluidState().isEmpty()
                && (feet.isAir() || feet.isReplaceable()) && (head.isAir() || head.isReplaceable())
                && !hazard(feet) && !hazard(head) && feet.getFluidState().isEmpty() && head.getFluidState().isEmpty();
    }
    private static boolean hazard(BlockState state) {
        return state.isOf(Blocks.LAVA) || state.isOf(Blocks.FIRE) || state.isOf(Blocks.SOUL_FIRE) || state.isOf(Blocks.MAGMA_BLOCK)
                || state.isOf(Blocks.CACTUS) || state.isOf(Blocks.CAMPFIRE) || state.isOf(Blocks.SOUL_CAMPFIRE)
                || state.isOf(Blocks.SWEET_BERRY_BUSH) || state.isOf(Blocks.POWDER_SNOW) || state.isOf(Blocks.WITHER_ROSE);
    }
}
