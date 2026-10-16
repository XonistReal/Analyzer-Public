package dev.analyzer.finder;

/** Boundary checks use the same pure policy called by the production HUD. */
public final class RegionMapSelfTest {
    private static int assertions;

    public static void main(String[] args) {
        for (int size : new int[]{16, 512, 65536}) {
            for (int cell : new int[]{-3, -1, 0, 1, 3}) {
                double start = (double) cell * size;
                check(RegionMapPolicy.coordinateCell(start, size) == cell, "Exact cell boundary");
                check(RegionMapPolicy.coordinateCell(start - .01, size) == cell - 1, "Previous cell below exact boundary");
                check(RegionMapPolicy.coordinateCell(start + size - .01, size) == cell, "Cell includes last fractional block");
                check(RegionMapPolicy.projection(start, cell, size, 5) == 0, "Projection origin");
                check(RegionMapPolicy.projection(start + 5d * size, cell, size, 5) == 1, "Projection excludes end boundary");
                check(Math.abs(RegionMapPolicy.projection(start + 2.5 * size, cell, size, 5) - .5) < 1e-12, "Grid center");
            }
        }
        check(RegionMapPolicy.coordinateCell(-.001, 512) == -1, "Negative fraction is not truncated to zero");
        check(RegionMapPolicy.coordinateCell(-512, 512) == -1, "Exact negative multiple");
        check(RegionMapPolicy.coordinateCell(-512.001, 512) == -2, "Below negative multiple");
        for (int cells : new int[]{3, 5, 7, 9}) {
            for (double player : new double[]{-30000000, -512.1, -.1, 0, 512.1, 30000000}) {
                long first = RegionMapPolicy.coordinateCell(player, 512) - cells / 2;
                double projected = RegionMapPolicy.projection(player, first, 512, cells);
                check(RegionMapPolicy.visible(projected), "Centered player remains inside bounded grid");
                check(projected >= .5 - .5 / cells - 1e-12 && projected <= .5 + .5 / cells + 1e-12,
                        "Player remains in center cell");
            }
        }
        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            invalid(() -> RegionMapPolicy.coordinateCell(invalid, 512));
            invalid(() -> RegionMapPolicy.projection(invalid, 0, 512, 5));
            check(!RegionMapPolicy.visible(invalid), "Nonfinite markers never draw");
        }
        invalid(() -> RegionMapPolicy.coordinateCell(0, 0));
        invalid(() -> RegionMapPolicy.coordinateCell(0, -1));
        invalid(() -> RegionMapPolicy.projection(0, 0, 0, 5));
        invalid(() -> RegionMapPolicy.projection(0, 0, 512, 0));
        check(RegionMapPolicy.visible(0), "First grid edge visible");
        check(!RegionMapPolicy.visible(1), "Last grid edge clipped");
        check(!RegionMapPolicy.visible(-.0001), "Outside first edge clipped");
        check(!RegionMapPolicy.visible(1.0001), "Outside last edge clipped");
        check(RegionMapPolicy.projection(-1, 0, 512, 5) < 0, "Outside markers are not clamped to grid boundary");
        System.out.println("Analyzer region map checks passed: " + assertions + " assertions");
    }

    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); assertions++; }
    private static void invalid(Runnable action) {
        try { action.run(); throw new AssertionError("Invalid grid input accepted"); }
        catch (IllegalArgumentException expected) { assertions++; }
    }
}
