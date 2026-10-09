package dev.analyzer.render;

import dev.analyzer.core.AnalyzerCore;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
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

/** Only indexes block entities already sent by the server. Never loads or searches blocks. */
final class StorageCache {
    private static final int MAX_CONTAINERS = 4096;
    private static final int REFRESH_TICKS = 10;
    private List<Container> containers = List.of();
    private ClientWorld cachedWorld;
    private int ticks;
    private boolean previouslyEnabled;
    private double previousRange;

    List<Container> containers() { return containers; }

    void tick(MinecraftClient client, AnalyzerCore core) {
        boolean enabled = core.isEnabled("storage-esp");
        if (client.world != cachedWorld || client.world == null || client.player == null || !enabled) {
            containers = List.of();
            cachedWorld = client.world;
            ticks = REFRESH_TICKS;
            previouslyEnabled = false;
        }
        if (!enabled || client.world == null || client.player == null) return;
        double range = core.getNumber("storage-esp", "range");
        if (!previouslyEnabled || range != previousRange || ++ticks >= REFRESH_TICKS) {
            ticks = 0;
            previouslyEnabled = true;
            previousRange = range;
            refresh(client, range);
        }
    }

    private void refresh(MinecraftClient client, double range) {
        ClientWorld world = client.world;
        Vec3d origin = client.player.getPos();
        ChunkPos center = client.player.getChunkPos();
        int radius = Math.min(9, (int) Math.ceil(range / 16.0) + 1);
        List<Container> found = new ArrayList<>();
        // Near chunks are visited first so the bounded cache remains useful in dense bases.
        for (int ring = 0; ring <= radius && found.size() < MAX_CONTAINERS; ring++) {
            for (int dx = -ring; dx <= ring && found.size() < MAX_CONTAINERS; dx++) {
                for (int dz = -ring; dz <= ring && found.size() < MAX_CONTAINERS; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                    WorldChunk chunk = world.getChunkManager().getChunk(center.x + dx, center.z + dz,
                            ChunkStatus.FULL, false);
                    if (chunk == null) continue;
                    for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                        int color = color(blockEntity);
                        if (color == 0 || blockEntity.isRemoved()) continue;
                        BlockPos pos = blockEntity.getPos();
                        if (Vec3d.ofCenter(pos).squaredDistanceTo(origin) > range * range) continue;
                        VoxelShape shape = blockEntity.getCachedState().getOutlineShape(world, pos);
                        Box bounds = (shape.isEmpty() ? new Box(0, 0, 0, 1, 1, 1)
                                : shape.getBoundingBox()).offset(pos).expand(0.003);
                        found.add(new Container(blockEntity, pos.toImmutable(), bounds, color, name(blockEntity)));
                        if (found.size() >= MAX_CONTAINERS) break;
                    }
                }
            }
        }
        containers = List.copyOf(found);
    }

    boolean isCurrent(ClientWorld world, Container container) {
        if (world != cachedWorld || container.blockEntity.isRemoved()) return false;
        WorldChunk chunk = world.getChunkManager().getChunk(container.position.getX() >> 4,
                container.position.getZ() >> 4, ChunkStatus.FULL, false);
        return chunk != null && chunk.getBlockEntities().get(container.position) == container.blockEntity;
    }

    private static int color(BlockEntity blockEntity) {
        if (blockEntity instanceof EnderChestBlockEntity) return 0xFFA993FA;
        if (blockEntity instanceof ShulkerBoxBlockEntity) return 0xFFFF92D4;
        if (blockEntity instanceof ChestBlockEntity) return 0xFFFFCF78;
        if (blockEntity instanceof BarrelBlockEntity) return 0xFFEDAE71;
        if (blockEntity instanceof HopperBlockEntity) return 0xFF66DFF7;
        if (blockEntity instanceof AbstractFurnaceBlockEntity) return 0xFFFF8266;
        return 0;
    }

    private static String name(BlockEntity blockEntity) {
        if (blockEntity instanceof EnderChestBlockEntity) return "Ender chest";
        if (blockEntity instanceof ShulkerBoxBlockEntity) return "Shulker box";
        if (blockEntity instanceof ChestBlockEntity) return "Chest";
        if (blockEntity instanceof BarrelBlockEntity) return "Barrel";
        if (blockEntity instanceof HopperBlockEntity) return "Hopper";
        if (blockEntity instanceof AbstractFurnaceBlockEntity) {
            String block = blockEntity.getCachedState().getBlock().getName().getString();
            return block.isEmpty() ? "Furnace" : block;
        }
        return "Container";
    }

    record Container(BlockEntity blockEntity, BlockPos position, Box bounds, int color, String name) { }
}
