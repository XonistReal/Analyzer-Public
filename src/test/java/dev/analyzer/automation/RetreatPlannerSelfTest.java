package dev.analyzer.automation;

import java.util.*;

public final class RetreatPlannerSelfTest {
    private static int assertions;
    public static void main(String[] args) {
        var start = new RetreatPlanner.Point(0, 0, 0);
        var mob = new RetreatPlanner.Threat(-2, 0, 0, 0, 0, false);
        var wall = new HashSet<String>();
        // A dead end ahead forces the route sideways before escaping beyond the wall.
        for (int z = -2; z <= 2; z++) wall.add("2," + z);
        var world = terrain(wall);
        var result = RetreatPlanner.search(start, List.of(mob), world, 8, 200, true);
        check(!result.path().isEmpty(), "A surveyed detour exists");
        check(result.path().stream().noneMatch(p -> wall.contains((int)p.x() + "," + (int)p.z())), "No blocked cell in the path");
        check(result.path().getLast().x() > 2, "Finds escape beyond a local dead end");
        check(result.path().size() > 3, "Plans farther than the old two-block corridor");
        var blast = new RetreatPlanner.Threat(-2, 0, 0, 0, 0, true);
        check(RetreatPlanner.approachesBlast(start,new RetreatPlanner.Point(1,0,1),new RetreatPlanner.Threat(1,0,0,0,0,true)),"Diagonal midpoint cannot cut closer to a blast despite equal endpoint distances");
        var escape = RetreatPlanner.search(start, List.of(blast), world, 8, 200, true);
        var previous = start;
        for (var point : escape.path()) { check(Math.hypot(point.x()+2,point.z()) >= Math.hypot(previous.x()+2,previous.z())-.02, "Every blast edge increases separation"); previous=point; }
        var sealed = new RetreatPlanner.Terrain() {
            public List<RetreatPlanner.Edge> neighbors(RetreatPlanner.Point point) { return List.of(); }
            public boolean cover(RetreatPlanner.Point point) { return false; }
        };
        check(RetreatPlanner.search(start,List.of(mob),sealed,8,100,true).path().isEmpty(), "Sealed space reports no fabricated exit");
        var bounded = RetreatPlanner.search(start,List.of(mob),world,8,3,false);
        check(bounded.expanded() <= 3, "Expansion budget is enforced");
        check(result.path().stream().allMatch(point -> Math.hypot(point.x(),point.z())<=8), "Radius is enforced");
        try { result.path().add(start); throw new AssertionError("Path mutable"); } catch (UnsupportedOperationException expected) { assertions++; }
        System.out.println("Retreat planner passed " + assertions + " assertions.");
    }
    private static RetreatPlanner.Terrain terrain(Set<String> wall) {
        return new RetreatPlanner.Terrain() {
            public List<RetreatPlanner.Edge> neighbors(RetreatPlanner.Point point) {
                var edges = new ArrayList<RetreatPlanner.Edge>();
                for(int[] offset:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                    int x=(int)point.x()+offset[0], z=(int)point.z()+offset[1];
                    if(!wall.contains(x+","+z)) edges.add(new RetreatPlanner.Edge(new RetreatPlanner.Point(x,0,z),1));
                }
                return edges;
            }
            public boolean cover(RetreatPlanner.Point point) { return point.x()>=3; }
        };
    }
    private static void check(boolean condition,String message) { if(!condition)throw new AssertionError(message); assertions++; }
}
