package dev.analyzer.automation;

import java.util.*;

/** Independent route fixtures: budgeted search, unreachable targets, weighted threats and segment frontiers. */
public final class NativePathfinderSelfTest {
    private static int assertions;
    public static void main(String[] args) {
        var start = point(-4, 0);
        var target = new NativePathfinder.Target(4, 0, 0);
        Set<NativePathfinder.Point> wall = new HashSet<>();
        for (int z = -2; z <= 2; z++) wall.add(point(0, z));
        NativePathfinder.Grid grid = rectangle(-6, 6, -5, 5, wall, Map.of());
        var search = new NativePathfinder.Search(grid, start, target, 4096);
        NativePathfinder.Result route = null; int calls = 0, previousExpanded = 0;
        while (route == null && calls++ < 300) {
            route = search.advance(3);
            check(search.expanded() - previousExpanded <= 3, "a tick obeys its node budget");
            previousExpanded = search.expanded();
        }
        check(route != null && route.reached(), "route finds the opening around a wall");
        check(calls > 1, "search really continues over multiple ticks");
        check(route.path().getFirst().equals(start) && route.path().getLast().equals(point(4, 0)), "route has correct endpoints including negative coordinates");
        for (int i = 0; i < route.path().size(); i++) {
            check(!wall.contains(route.path().get(i)), "route never enters an unavailable cell");
            if (i > 0) check(adjacent(route.path().get(i - 1), route.path().get(i)), "every route step has an observed graph edge");
        }
        check(search.advance(3).equals(route), "completed search has a stable result");

        Map<NativePathfinder.Point, Double> threats = new HashMap<>();
        for (int x = 1; x < 5; x++) threats.put(point(x, 0), 20D);
        var safe = complete(new NativePathfinder.Search(rectangle(0, 5, -2, 2, Set.of(), threats), point(0, 0), new NativePathfinder.Target(5, 0, 0), 4096));
        check(safe.reached(), "weighted route reaches the target");
        check(safe.path().stream().noneMatch(threats::containsKey), "route trades a shorter line for lower threat cost");

        Set<NativePathfinder.Point> sealed = Set.of(point(1, 0), point(-1, 0), point(0, 1), point(0, -1));
        var blocked = complete(new NativePathfinder.Search(rectangle(-2, 2, -2, 2, sealed, Map.of()), point(0, 0), new NativePathfinder.Target(2, 0, 0), 4096));
        check(!blocked.reached() && blocked.path().size() == 1, "unreachable goal cannot invent a movement");

        NativePathfinder.Grid frontier = new NativePathfinder.Grid() {
            final NativePathfinder.Grid open = rectangle(-4, 4, -4, 4, Set.of(), Map.of());
            public List<NativePathfinder.Edge> neighbors(NativePathfinder.Point point) { return open.neighbors(point); }
            public boolean frontier(NativePathfinder.Point point) { return Math.abs(point.x()) == 4 || Math.abs(point.z()) == 4; }
            public boolean goalOutsideWindow(NativePathfinder.Target target) { return Math.abs(target.x()) > 4 || Math.abs(target.z()) > 4; }
        };
        var segment = complete(new NativePathfinder.Search(frontier, point(0, 0), new NativePathfinder.Target(200, null, 0), 4096));
        check(!segment.reached() && segment.path().getLast().equals(point(4, 0)), "long travel ends at a legal segment frontier");
        check(segment.path().size() == 5, "segment has actual intermediate cells");
        NativePathfinder.Grid exhaustiveWindow = new NativePathfinder.Grid() {
            public List<NativePathfinder.Edge> neighbors(NativePathfinder.Point point) { return frontier.neighbors(point); }
            public boolean frontier(NativePathfinder.Point point) { return frontier.frontier(point); }
        };
        var exhaustive = complete(new NativePathfinder.Search(exhaustiveWindow, point(0, 0), new NativePathfinder.Target(200, null, 0), 4096));
        check(segment.path().equals(exhaustive.path()), "early segmentation keeps the same checked route to its frontier");
        check(segment.expanded() == 5 && exhaustive.expanded() == 81, "a useful long-distance frontier needs five expansions instead of exhausting all 81 cells");
        check(segment.expanded() * 8 < exhaustive.expanded(), "segment latency improves without increasing the per-tick node budget");
        NativePathfinder.Grid threatenedFrontier = new NativePathfinder.Grid() {
            final NativePathfinder.Grid observed = rectangle(-4, 4, -4, 4, Set.of(), Map.of(point(1, 0), 30D, point(2, 0), 30D));
            public List<NativePathfinder.Edge> neighbors(NativePathfinder.Point point) { return observed.neighbors(point); }
            public boolean frontier(NativePathfinder.Point point) { return frontier.frontier(point); }
            public boolean goalOutsideWindow(NativePathfinder.Target target) { return true; }
        };
        var safeSegment = complete(new NativePathfinder.Search(threatenedFrontier, point(0, 0), new NativePathfinder.Target(200, null, 0), 4096));
        check(safeSegment.path().stream().noneMatch(p -> p.equals(point(1, 0)) || p.equals(point(2, 0))), "early segment completion retains weighted threat avoidance");

        var isolatedPost = complete(new NativePathfinder.Search(frontier, point(0, 0), new NativePathfinder.Target(0, 2, 0), 4096));
        check(!isolatedPost.reached() && isolatedPost.path().equals(List.of(point(0, 0))),
                "an unreachable nearby raised stand never walks away to a distant frontier");
        var budgetedPost = complete(new NativePathfinder.Search(frontier, point(0, 0), new NativePathfinder.Target(0, 2, 0), 64));
        check(!budgetedPost.reached() && budgetedPost.path().equals(List.of(point(0, 0))),
                "a budget-limited local stand search also refuses a regressive frontier");

        var truncated = complete(new NativePathfinder.Search(rectangle(-40, 40, -40, 40, Set.of(), Map.of()), point(0, 0), new NativePathfinder.Target(100, 0, 100), 32));
        check(truncated.expanded() <= 32 && !truncated.reached(), "total exploration is bounded as well as per-tick work");
        check(!new NativePathfinder.Target(0, 1, 0).reached(new NativePathfinder.Point(0, 8, 0)), "XYZ goal requires the requested standing cell height");
        check(new NativePathfinder.Target(0, null, 0).reached(new NativePathfinder.Point(0, -512, 0)), "XZ goal supports any legal dimension height");
        boolean rejected = false;
        try { new NativePathfinder.Edge(start, Double.NaN); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "nonfinite edge costs cannot corrupt the search queue");
        System.out.println("Checked frontier comparison: " + exhaustive.expanded() + " → " + segment.expanded() + " expanded cells at the same node budget");
        System.out.println("Native pathfinder passed " + assertions + " assertions");
    }
    private static NativePathfinder.Result complete(NativePathfinder.Search search) {
        NativePathfinder.Result result = null; int iterations = 0;
        while (result == null && iterations++ < 10_000) result = search.advance(64);
        if (result == null) throw new AssertionError("fixture failed to finish"); return result;
    }
    private static NativePathfinder.Grid rectangle(int minX, int maxX, int minZ, int maxZ, Set<NativePathfinder.Point> unavailable, Map<NativePathfinder.Point, Double> extraCost) {
        return point -> {
            List<NativePathfinder.Edge> edges = new ArrayList<>();
            for (int[] step : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                NativePathfinder.Point next = point(point.x() + step[0], point.z() + step[1]);
                if (next.x() >= minX && next.x() <= maxX && next.z() >= minZ && next.z() <= maxZ && !unavailable.contains(next))
                    edges.add(new NativePathfinder.Edge(next, 1 + extraCost.getOrDefault(next, 0D)));
            }
            return edges;
        };
    }
    private static NativePathfinder.Point point(int x, int z) { return new NativePathfinder.Point(x, 0, z); }
    private static boolean adjacent(NativePathfinder.Point a, NativePathfinder.Point b) { return Math.abs(a.x() - b.x()) + Math.abs(a.z() - b.z()) == 1; }
    private static void check(boolean condition, String label) { assertions++; if (!condition) throw new AssertionError(label); }
}
