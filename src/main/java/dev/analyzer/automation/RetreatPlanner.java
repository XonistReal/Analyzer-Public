package dev.analyzer.automation;

import java.util.*;

/** Original bounded best-first search for an escape through a surveyed local graph. */
public final class RetreatPlanner {
    public record Point(double x, double y, double z) { }
    public record Threat(double x, double y, double z, double velocityX, double velocityZ, boolean explosive) { }
    public record Edge(Point destination, double cost) { }
    public interface Terrain {
        List<Edge> neighbors(Point point);
        boolean cover(Point point);
    }
    public record Result(List<Point> path, int expanded, boolean exhausted) {
        public Result { path = List.copyOf(path); }
    }
    private record Visit(Point point, double cost, double priority) { }
    private RetreatPlanner() { }

    public static Result search(Point start, List<Threat> threats, Terrain terrain, int radius, int budget, boolean preferCover) {
        Objects.requireNonNull(start); Objects.requireNonNull(terrain);
        if (radius < 2 || radius > 32 || budget < 1 || budget > 4096 || threats.isEmpty()) return new Result(List.of(), 0, false);
        List<Threat> snapshot = List.copyOf(threats);
        PriorityQueue<Visit> open = new PriorityQueue<>(Comparator.comparingDouble(Visit::priority));
        Map<Point, Double> costs = new HashMap<>();
        Map<Point, Point> parents = new HashMap<>();
        Set<Point> closed = new HashSet<>();
        double initial = quality(start, start, snapshot, false), best = initial;
        Point destination = start;
        costs.put(start, 0.0); open.add(new Visit(start, 0, -initial));
        int expanded = 0;
        while (!open.isEmpty() && expanded < budget) {
            Visit next = open.poll();
            if (!closed.add(next.point)) continue;
            expanded++;
            boolean cover = preferCover && terrain.cover(next.point);
            double quality = quality(next.point, start, snapshot, cover) - next.cost * .12;
            if (quality > best + .2 && horizontal(next.point, start) >= 2) { best = quality; destination = next.point; }
            for (Edge edge : terrain.neighbors(next.point)) {
                Point point = edge.destination;
                if (point == null || !finite(point) || !Double.isFinite(edge.cost) || edge.cost <= 0 || closed.contains(point)
                        || horizontal(point, start) > radius || Math.abs(point.y - start.y) > 4) continue;
                // An imminent blast cannot be approached even while seeking a better distant endpoint.
                if (approachesExplosion(next.point, point, snapshot)) continue;
                double cost = next.cost + edge.cost + danger(point, snapshot) * .12;
                if (cost >= costs.getOrDefault(point, Double.MAX_VALUE)) continue;
                costs.put(point, cost); parents.put(point, next.point);
                open.add(new Visit(point, cost, cost * .18 - quality(point, start, snapshot, false)));
            }
        }
        if (destination.equals(start)) return new Result(List.of(), expanded, expanded >= budget && !open.isEmpty());
        List<Point> path = new ArrayList<>();
        for (Point point = destination; !point.equals(start); point = parents.get(point)) {
            if (point == null || path.size() > budget) return new Result(List.of(), expanded, true);
            path.add(point);
        }
        Collections.reverse(path);
        return new Result(path, expanded, expanded >= budget && !open.isEmpty());
    }
    private static boolean finite(Point point) { return Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z); }
    private static double horizontal(Point a, Point b) { return Math.hypot(a.x - b.x, a.z - b.z); }
    private static double separation(Point point, Threat threat) {
        // Half a second of observed pursuit raises the cost of a corridor the mob is entering.
        return Math.hypot(point.x - (threat.x + Math.clamp(threat.velocityX, -1, 1) * 10),
                point.z - (threat.z + Math.clamp(threat.velocityZ, -1, 1) * 10));
    }
    private static double danger(Point point, List<Threat> threats) {
        double value = 0;
        for (Threat threat : threats) { double distance = separation(point, threat); value += Math.max(0, (threat.explosive ? 10 : 6) - distance) * (threat.explosive ? 5 : 1.5); }
        return value;
    }
    private static double quality(Point point, Point start, List<Threat> threats, boolean cover) {
        double nearest = threats.stream().mapToDouble(threat -> separation(point, threat)).min().orElse(0);
        return nearest * 2.0 - danger(point, threats) + horizontal(point, start) * .08 + (cover ? 3 : 0);
    }
    private static boolean approachesExplosion(Point from, Point to, List<Threat> threats) {
        for (Threat threat : threats) if (threat.explosive && approachesBlast(from,to,threat)) return true;
        return false;
    }
    public static boolean approachesBlast(Point from,Point to,Threat threat) {
        double dx=to.x-from.x,dz=to.z-from.z,length=dx*dx+dz*dz;
        if(length<1e-12)return false;
        double t=Math.clamp(((threat.x-from.x)*dx+(threat.z-from.z)*dz)/length,0,1);
        double before=(from.x-threat.x)*(from.x-threat.x)+(from.z-threat.z)*(from.z-threat.z);
        double x=from.x+t*dx-threat.x,z=from.z+t*dz-threat.z;
        return x*x+z*z<before-.001;
    }
}
