package dev.analyzer.finder;

/** Pure coordinate-grid math, independent of rendering and server-specific region labels. */
public final class RegionMapPolicy {
    private RegionMapPolicy() { }

    public static long coordinateCell(double coordinate, int cellSize) {
        if (!Double.isFinite(coordinate) || cellSize < 1) throw new IllegalArgumentException("Invalid grid coordinate");
        return (long) Math.floor(coordinate / cellSize);
    }

    /** Projection deliberately remains outside [0,1] for off-grid observations, so they can be clipped. */
    public static double projection(double coordinate, long firstCell, int cellSize, int cells) {
        if (!Double.isFinite(coordinate) || cellSize < 1 || cells < 1) throw new IllegalArgumentException("Invalid grid projection");
        return (coordinate - (double) firstCell * cellSize) / ((double) cells * cellSize);
    }

    public static boolean visible(double projected) {
        return Double.isFinite(projected) && projected >= 0 && projected < 1;
    }
}
