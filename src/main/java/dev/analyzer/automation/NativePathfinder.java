package dev.analyzer.automation;

import java.util.*;

/** Original incremental A*. The caller supplies only observed, legal movement edges. */
public final class NativePathfinder {
    public record Point(int x, int y16, int z) { public double y() { return y16 / 16.0; } }
    public record Edge(Point destination, double cost) {
        public Edge { if (destination == null || !Double.isFinite(cost) || cost <= 0) throw new IllegalArgumentException("Invalid path edge"); }
    }
    public interface Grid {
        List<Edge> neighbors(Point point);
        default boolean frontier(Point point) { return false; }
        /** Only a window that excludes the actual X/Z goal may deliberately finish at a useful frontier. */
        default boolean goalOutsideWindow(Target target) { return false; }
    }
    public record Target(int x, Integer y, int z) {
        public boolean reached(Point point) { return point.x() == x && point.z() == z && (y == null || Math.floor(point.y() + .001) == y); }
        public double heuristic(Point point) {
            double horizontal = Math.hypot((double) x - point.x(), (double) z - point.z());
            return horizontal + (y == null ? 0 : Math.abs(y - point.y()) * .5);
        }
    }
    public record Result(List<Point> path, boolean reached, String detail, int expanded) { public Result { path = List.copyOf(path); } }
    private record Entry(Point point, double score, double cost, long sequence) { }
    public static final class Search {
        private final Grid grid;
        private final Point start;
        private final Target target;
        private final int maximum;
        private final PriorityQueue<Entry> open = new PriorityQueue<>(Comparator.comparingDouble(Entry::score).thenComparingLong(Entry::sequence));
        private final Map<Point, Double> costs = new HashMap<>();
        private final Map<Point, Point> parents = new HashMap<>();
        private final Set<Point> closed = new HashSet<>();
        private Point nearest;
        private Point frontier;
        private long sequence;
        private int expanded;
        private Result result;
        public Search(Grid grid, Point start, Target target, int maximum) {
            this.grid = Objects.requireNonNull(grid); this.start = Objects.requireNonNull(start); this.target = Objects.requireNonNull(target);
            this.maximum = Math.max(32, Math.min(32_768, maximum)); nearest = start;
            costs.put(start, 0D); open.add(new Entry(start, target.heuristic(start), 0, sequence++));
        }
        /** null means calculating. Every call bounds expanded nodes, rather than freezing a game tick. */
        public Result advance(int budget) {
            if (result != null) return result;
            int allotted = Math.max(1, Math.min(1024, budget)), spent = 0, stale = 0;
            while (!open.isEmpty() && spent < allotted) {
                Entry entry = open.poll();
                if (closed.contains(entry.point()) || entry.cost() > costs.getOrDefault(entry.point(), Double.POSITIVE_INFINITY) + .000001) {
                    if (++stale >= allotted * 8 + 32) break;
                    continue;
                }
                Point point = entry.point(); closed.add(point); spent++; expanded++;
                if (target.heuristic(point) < target.heuristic(nearest) - .000001) nearest = point;
                if (grid.frontier(point) && (frontier == null || target.heuristic(point) + entry.cost() * .05
                        < target.heuristic(frontier) + costs.get(frontier) * .05)) frontier = point;
                if (target.reached(point)) return result = finish(point, true, "Route reaches the target");
                if (grid.goalOutsideWindow(target) && grid.frontier(point) && target.heuristic(point) < target.heuristic(start) - .000001)
                    return result = finish(point, false, "Reached the next checked loaded-terrain frontier");
                if (expanded >= maximum) return result = partial("Loaded-terrain segment reached the search budget");
                for (Edge edge : grid.neighbors(point)) {
                    if (closed.contains(edge.destination())) continue;
                    double candidate = entry.cost() + edge.cost();
                    if (candidate >= costs.getOrDefault(edge.destination(), Double.POSITIVE_INFINITY) - .000001) continue;
                    costs.put(edge.destination(), candidate); parents.put(edge.destination(), point);
                    open.add(new Entry(edge.destination(), candidate + target.heuristic(edge.destination()), candidate, sequence++));
                }
            }
            if (open.isEmpty()) return result = partial("Observed terrain has no further safe route");
            return null;
        }
        private Result partial(String detail) {
            // A frontier extends long travel only when it actually approaches the destination.
            // An isolated nearby interaction stand must not send the player to a distant window edge.
            Point last = frontier != null && target.heuristic(frontier) < target.heuristic(start) - .000001 ? frontier : nearest;
            return finish(last, false, detail);
        }
        private Result finish(Point last, boolean reached, String detail) {
            List<Point> points = new ArrayList<>(); points.add(last);
            while (!last.equals(start)) {
                last = parents.get(last); if (last == null) throw new IllegalStateException("Path parent chain is incomplete"); points.add(last);
            }
            Collections.reverse(points); return new Result(points, reached, detail, expanded);
        }
        public int expanded() { return expanded; }
        public int discovered() { return costs.size(); }
    }
    private NativePathfinder() { }
}
