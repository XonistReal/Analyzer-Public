package dev.analyzer.automation;

import java.util.*;

/** Conservative bounded proof that a source cannot spread down/sideways into unselected dry cells. */
public final class SchematicFluidPolicy {
    private SchematicFluidPolicy() { }
    public record Cell(int x, int y, int z) { }
    public enum Face {
        DOWN(0,-1,0), NORTH(0,0,-1), SOUTH(0,0,1), WEST(-1,0,0), EAST(1,0,0);
        public final int dx, dy, dz;
        Face(int dx, int dy, int dz) { this.dx=dx; this.dy=dy; this.dz=dz; }
    }
    public interface Field {
        boolean known(Cell cell);
        boolean closed(Cell cell, Face incoming);
        boolean intended(Cell cell);
    }
    public static boolean contained(Cell source, Field field) {
        if (source == null || field == null || !field.known(source) || !field.intended(source)) return false;
        var queue = new ArrayDeque<Cell>(); var seen = new HashSet<Cell>(); queue.add(source);
        while (!queue.isEmpty()) {
            Cell cell = queue.removeFirst(); if (!seen.add(cell)) continue;
            if (seen.size() > SchematicImporter.MAX_VOLUME) return false;
            for (Face face : Face.values()) {
                Cell next = new Cell(cell.x + face.dx, cell.y + face.dy, cell.z + face.dz);
                if (!field.known(next)) return false;
                if (field.closed(next, face)) continue;
                if (!field.intended(next)) return false;
                if (!seen.contains(next)) queue.add(next);
            }
        }
        return true;
    }
}
