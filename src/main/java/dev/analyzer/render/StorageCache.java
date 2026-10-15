package dev.analyzer.render;

import dev.analyzer.core.AnalyzerCore;
import dev.analyzer.core.EspSettings;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.enums.ChestType;
import net.minecraft.registry.Registries;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

/** Only indexes block entities already sent by the server. Never loads or searches blocks. */
final class StorageCache {
    private static final int MAX_CONTAINERS = 4096;
    private static final int REFRESH_TICKS = 10;
    private List<Container> containers = List.of();
    private ClientWorld cachedWorld;
    private int ticks;
    private boolean previouslyEnabled;
    private double previousRange;
    private ChunkPos previousCenter;
    private int loadedChunksScanned;
    private boolean ready;

    List<Container> containers() { return containers; }
    int loadedChunksScanned() { return loadedChunksScanned; }
    boolean ready() { return ready; }

    void tick(MinecraftClient client, AnalyzerCore core) {
        boolean enabled = core.isEnabled("storage-esp");
        if (client.world != cachedWorld || client.world == null || client.player == null || !enabled) {
            containers = List.of();
            cachedWorld = client.world;
            ticks = REFRESH_TICKS;
            previouslyEnabled = false;
            previousCenter = null;
            loadedChunksScanned = 0;
            ready = false;
        }
        if (!enabled || client.world == null || client.player == null) return;
        double range = core.getNumber("storage-esp", "range");
        if (!previouslyEnabled || range != previousRange || !client.player.getChunkPos().equals(previousCenter)
                || ++ticks >= REFRESH_TICKS) {
            ticks = 0;
            previouslyEnabled = true;
            previousRange = range;
            previousCenter = client.player.getChunkPos();
            refresh(client, range);
        }
    }

    /** A menu toggle/profile change gets a fresh cache before its very next world frame. */
    void ensureReady(MinecraftClient client, AnalyzerCore core) {
        if (!core.isEnabled("storage-esp") || client.world == null || client.player == null) return;
        double range = core.getNumber("storage-esp", "range");
        if (cachedWorld != client.world || !previouslyEnabled || previousRange != range) {
            cachedWorld = client.world;
            previouslyEnabled = true;
            previousRange = range;
            previousCenter = client.player.getChunkPos();
            ticks = 0;
            refresh(client, range);
        }
    }

    private void refresh(MinecraftClient client, double range) {
        ClientWorld world = client.world;
        Vec3d origin = client.player.getPos();
        ChunkPos center = client.player.getChunkPos();
        int radius = Math.min(9, (int) Math.ceil(range / 16.0) + 1);
        List<Container> found = new ArrayList<>();
        int scanned = 0;
        Set<BlockPos> visited = new HashSet<>();
        // Near chunks are visited first so the bounded cache remains useful in dense bases.
        for (int ring = 0; ring <= radius && found.size() < MAX_CONTAINERS; ring++) {
            for (int dx = -ring; dx <= ring && found.size() < MAX_CONTAINERS; dx++) {
                for (int dz = -ring; dz <= ring && found.size() < MAX_CONTAINERS; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                    WorldChunk chunk = world.getChunkManager().getChunk(center.x + dx, center.z + dz,
                            ChunkStatus.FULL, false);
                    if (chunk == null) continue;
                    scanned++;
                    for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                        BlockPos pos = blockEntity.getPos();
                        if (blockEntity.isRemoved() || visited.contains(pos)) continue;
                        BlockState state = blockEntity.getCachedState();
                        EspSettings.StorageGroup group = EspSettings.storageGroup(Registries.BLOCK.getId(state.getBlock()).toString());
                        if (group == null || Vec3d.ofCenter(pos).squaredDistanceTo(origin) > range * range) continue;
                        Box bounds = bounds(world, pos, state);
                        BlockEntity partner = null;
                        BlockPos partnerPos = null;
                        BlockState partnerState = null;
                        // One outer box for a double chest avoids the distracting center seam.
                        if (blockEntity instanceof ChestBlockEntity && state.contains(ChestBlock.CHEST_TYPE)
                                && state.get(ChestBlock.CHEST_TYPE) != ChestType.SINGLE) {
                            BlockPos candidate = pos.offset(ChestBlock.getFacing(state));
                            BlockEntity other = loadedBlockEntity(world, candidate);
                            if (other instanceof ChestBlockEntity && !other.isRemoved()) {
                                BlockState otherState = other.getCachedState();
                                if (otherState.getBlock() == state.getBlock() && otherState.contains(ChestBlock.CHEST_TYPE)
                                        && otherState.get(ChestBlock.CHEST_TYPE) != ChestType.SINGLE
                                        && candidate.offset(ChestBlock.getFacing(otherState)).equals(pos)) {
                                    partner = other; partnerPos = candidate.toImmutable(); partnerState = otherState;
                                    bounds = bounds.union(bounds(world, candidate, otherState));
                                    visited.add(candidate.toImmutable());
                                }
                            }
                        }
                        visited.add(pos.toImmutable());
                        String name = state.getBlock().getName().getString();
                        found.add(new Container(blockEntity, pos.toImmutable(), state, partner, partnerPos, partnerState,
                                bounds.expand(.002), group, partner == null ? name : "Double " + name.toLowerCase(java.util.Locale.ROOT)));
                        if (found.size() >= MAX_CONTAINERS) break;
                    }
                }
            }
        }
        containers = List.copyOf(found);
        loadedChunksScanned = scanned;
        ready = true;
    }

    private static Box bounds(ClientWorld world, BlockPos pos, BlockState state) {
        VoxelShape shape = state.getOutlineShape(world, pos);
        return (shape.isEmpty() ? new Box(0, 0, 0, 1, 1, 1) : shape.getBoundingBox()).offset(pos);
    }

    private static BlockEntity loadedBlockEntity(ClientWorld world, BlockPos position) {
        WorldChunk chunk = world.getChunkManager().getChunk(position.getX() >> 4, position.getZ() >> 4, ChunkStatus.FULL, false);
        return chunk == null ? null : chunk.getBlockEntities().get(position);
    }

    boolean isCurrent(ClientWorld world, Container container) {
        if (world != cachedWorld || container.blockEntity.isRemoved()
                || loadedBlockEntity(world, container.position) != container.blockEntity
                || container.blockEntity.getCachedState() != container.state) return false;
        return container.partner == null || !container.partner.isRemoved()
                && loadedBlockEntity(world, container.partnerPosition) == container.partner
                && container.partner.getCachedState() == container.partnerState;
    }

    record Container(BlockEntity blockEntity, BlockPos position, BlockState state, BlockEntity partner,
                     BlockPos partnerPosition, BlockState partnerState, Box bounds, EspSettings.StorageGroup group, String name) { }
}
