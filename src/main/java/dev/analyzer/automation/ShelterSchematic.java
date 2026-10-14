package dev.analyzer.automation;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import java.util.List;

/** Original, bounded starter shelter: a floor, three walls high, roof and open entrance. */
public final class ShelterSchematic implements BuildSchematic {
    public static final int WIDTH = 7, HEIGHT = 5, LENGTH = 7;
    public static final int MATERIAL_COUNT = 168;
    public static final int VOLUME = WIDTH * HEIGHT * LENGTH;
    private final BlockState material;

    public ShelterSchematic(BlockState material) {
        if (material == null || material.isAir()) throw new IllegalArgumentException("Shelter material must be a solid block");
        this.material = material;
    }

    @Override public int widthX() { return WIDTH; }
    @Override public int heightY() { return HEIGHT; }
    @Override public int lengthZ() { return LENGTH; }
    @Override public boolean inSchematic(int x, int y, int z, BlockState current) {
        return x >= 0 && x < WIDTH && y >= 0 && y < HEIGHT && z >= 0 && z < LENGTH;
    }

    /** No arbitrary terrain below or outside this explicitly selected volume is part of the schematic. */
    @Override public BlockState desiredState(int x, int y, int z, BlockState current, List<BlockState> placeable) {
        if (!inSchematic(x, y, z, current)) return current == null ? Blocks.AIR.getDefaultState() : current;
        return materialAt(x, y, z) ? material : Blocks.AIR.getDefaultState();
    }

    public BlockState material() { return material; }
    public boolean materialAt(int x, int y, int z) {
        if (!inSchematic(x, y, z, null)) return false;
        boolean entrance = z == 0 && x == 3 && (y == 1 || y == 2);
        return y == 0 || y == HEIGHT - 1 || (!entrance && (x == 0 || x == WIDTH - 1 || z == 0 || z == LENGTH - 1));
    }

    public Progress progress(ClientWorld world, BlockPos origin) {
        int placed = 0, matched = 0, observed = 0;
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int x = 0; x < WIDTH; x++) for (int y = 0; y < HEIGHT; y++) for (int z = 0; z < LENGTH; z++) {
            pos.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
            if (!world.isChunkLoaded(pos)) continue;
            observed++;
            BlockState current = world.getBlockState(pos);
            boolean solid = materialAt(x, y, z);
            if (solid ? current.equals(material) : current.isAir()) {
                matched++;
                if (solid) placed++;
            }
        }
        return new Progress(placed, MATERIAL_COUNT, matched, observed, VOLUME);
    }

    /** Survey the same nondestructive loaded foundation policy used for imported structures. */
    public String siteProblem(ClientWorld world, BlockPos origin) {
        return AutoSiteSelector.siteProblem(world, origin, this);
    }

    public record Progress(int placed, int required, int matching, int observed, int volume) {
        public boolean complete() { return observed == volume && matching == volume; }
        public double fraction() { return required == 0 ? 1 : placed / (double) required; }
        public int missing() { return Math.max(0, required - placed); }
    }
}
