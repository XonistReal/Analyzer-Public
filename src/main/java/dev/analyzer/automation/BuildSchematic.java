package dev.analyzer.automation;

import net.minecraft.block.BlockState;
import java.util.List;

/** Analyzer's own read-only build target contract. Coordinates are relative to its minimum corner. */
public interface BuildSchematic {
    int widthX();
    int heightY();
    int lengthZ();
    default boolean inSchematic(int x, int y, int z, BlockState current) {
        return x >= 0 && x < widthX() && y >= 0 && y < heightY() && z >= 0 && z < lengthZ();
    }
    BlockState desiredState(int x, int y, int z, BlockState current, List<BlockState> placeable);
}
