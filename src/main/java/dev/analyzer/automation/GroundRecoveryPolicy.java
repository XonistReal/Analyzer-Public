package dev.analyzer.automation;

/** Limits for natural-tree recovery; a caller must freshly observe every cell before each action. */
final class GroundRecoveryPolicy {
    record Support(boolean loaded, boolean dry, boolean hazardous, boolean protectedBlock, boolean blockEntity,
                   boolean naturalTree, boolean solid) { }
    private GroundRecoveryPolicy() { }
    static boolean climbColumn(int depth, boolean continuous, boolean safeLanding) {
        return depth >= 2 && depth <= 24 && continuous && safeLanding;
    }
    static boolean treeColumn(int depth, int logs, int naturalLeaves, boolean rootedSoil, boolean verified) {
        return depth >= 3 && depth <= 24 && logs >= 3 && naturalLeaves >= 3 && rootedSoil && verified;
    }
    static boolean underfootCut(double drop, boolean centered, Support floor, Support landing) {
        return Double.isFinite(drop) && drop > 0 && drop <= 1.01 && centered && safe(floor) && safe(landing)
                && floor.naturalTree() && floor.solid() && landing.solid();
    }
    private static boolean safe(Support support) {
        return support != null && support.loaded() && support.dry() && !support.hazardous() && !support.protectedBlock() && !support.blockEntity();
    }
}
