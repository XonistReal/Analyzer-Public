package dev.analyzer.cosmetic;

import java.util.ArrayList;
import java.util.List;

/**
 * Original conical straw hat geometry in player head model space: pixels, Y down, head top at y = -8.
 * The cone rests on the head where its outer surface clears the head (or helmet) edge, so the brim
 * sits low around the forehead like a real conical hat. Dependency-free so its checks run headless.
 */
public final class StrawHatMesh {
    private StrawHatMesh() { }

    public enum Part { OUTER, INNER, RIM, STRAP }
    /** u/v are normalized texture coordinates for a 64x32 texture: outer straw on top, inner weave below. */
    public record Vertex(float x, float y, float z, float u, float v, float nx, float ny, float nz) { }
    public record Quad(Part part, Vertex a, Vertex b, Vertex c, Vertex d) {
        public List<Vertex> vertices() { return List.of(a, b, c, d); }
    }
    public record Shape(float radius, float height, float rimY, float apexY, float restRadius) { }

    public static final int SEGMENTS = 32;
    private static final float HEAD_TOP = -8, HEAD_HALF = 4, BASE_RADIUS = 10, BASE_HEIGHT = 5, THICKNESS = 0.5f;

    /**
     * @param size   overall scale (1 = 20 pixel brim diameter)
     * @param height cone steepness multiplier
     * @param lift   extra pixels above the resting position
     * @param helmet true when a helmet is worn, so the hat rests on the helmet's larger outline
     */
    public static Shape shape(double size, double height, double lift, boolean helmet) {
        double s = finite(size, 1, 0.4, 2), h = finite(height, 1, 0.3, 2.5), up = finite(lift, 0, -4, 8);
        double radius = BASE_RADIUS * s, coneHeight = BASE_HEIGHT * h * s;
        // Rest on the CORNERS of the outermost head layer: the skin's hat layer is inflated half a
        // pixel, a helmet one pixel. Resting on the edge midpoints would let the corners poke through.
        double half = HEAD_HALF + (helmet ? 1.0 : 0.5);
        // A brim smaller than the outline rests on the corners themselves and sits higher.
        double rest = Math.min(half * Math.sqrt(2) + 0.15, radius);
        double top = HEAD_TOP - (helmet ? 1.0 : 0.5) - 0.1;
        // The cone surface at the resting radius touches the top of that outline.
        double rimY = top + coneHeight * (1 - rest / radius) - up;
        return new Shape((float) radius, (float) coneHeight, (float) rimY, (float) (rimY - coneHeight), (float) rest);
    }

    /** Height of the cone's outer surface at a horizontal radius, for clearance checks. */
    public static double surfaceY(Shape shape, double radius) {
        return shape.rimY() - shape.height() * (1 - Math.clamp(radius / shape.radius(), 0, 1));
    }

    public static List<Quad> build(Shape shape, boolean strap) {
        List<Quad> quads = new ArrayList<>(SEGMENTS * 3 + 8);
        float r = shape.radius(), h = shape.height(), rimY = shape.rimY(), apexY = shape.apexY();
        // Outward-up normal of the cone surface, before normalization: (h/R cos, -1, h/R sin).
        float slope = h / r;
        for (int i = 0; i < SEGMENTS; i++) {
            double a0 = 2 * Math.PI * i / SEGMENTS, a1 = 2 * Math.PI * (i + 1) / SEGMENTS, mid = (a0 + a1) / 2;
            float u0 = (float) i / SEGMENTS, u1 = (float) (i + 1) / SEGMENTS, um = (u0 + u1) / 2;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            float cm = (float) Math.cos(mid), sm = (float) Math.sin(mid);
            float[] n0 = unit(slope * c0, -1, slope * s0), n1 = unit(slope * c1, -1, slope * s1), nm = unit(slope * cm, -1, slope * sm);
            // Outer straw: apex to rim. The apex is a degenerate edge so quads keep the native vertex format.
            quads.add(new Quad(Part.OUTER,
                    new Vertex(0, apexY, 0, um, 0, nm[0], nm[1], nm[2]),
                    new Vertex(0, apexY, 0, um, 0, nm[0], nm[1], nm[2]),
                    new Vertex(r * c1, rimY, r * s1, u1, 0.5f, n1[0], n1[1], n1[2]),
                    new Vertex(r * c0, rimY, r * s0, u0, 0.5f, n0[0], n0[1], n0[2])));
            // Inner weave: the same cone half a pixel lower, facing down and in, wound the other way.
            float innerApex = apexY + THICKNESS, innerRim = rimY + THICKNESS, ir = r - THICKNESS * 0.6f;
            quads.add(new Quad(Part.INNER,
                    new Vertex(0, innerApex, 0, um, 0.5f, -nm[0], -nm[1], -nm[2]),
                    new Vertex(ir * c0, innerRim, ir * s0, u0, 1, -n0[0], -n0[1], -n0[2]),
                    new Vertex(ir * c1, innerRim, ir * s1, u1, 1, -n1[0], -n1[1], -n1[2]),
                    new Vertex(0, innerApex, 0, um, 0.5f, -nm[0], -nm[1], -nm[2])));
            // Rim binding: a thin band joining the two surfaces, facing outward.
            float bu0 = 0.0f, bu1 = 4f / 64, bv0 = 28f / 32, bv1 = 31f / 32;
            quads.add(new Quad(Part.RIM,
                    new Vertex(r * c0, rimY, r * s0, bu0, bv0, c0, 0, s0),
                    new Vertex(r * c1, rimY, r * s1, bu1, bv0, c1, 0, s1),
                    new Vertex(ir * c1, innerRim, ir * s1, bu1, bv1, c1, 0, s1),
                    new Vertex(ir * c0, innerRim, ir * s0, bu0, bv1, c0, 0, s0)));
        }
        if (strap) strap(quads, shape);
        return List.copyOf(quads);
    }

    /** A thin chin strap: down both sides of the head from inside the brim, then under the chin. */
    private static void strap(List<Quad> quads, Shape shape) {
        float x = HEAD_HALF + 0.15f, top = shape.rimY() + THICKNESS, chin = 0.35f, width = 0.6f;
        float bu0 = 0.0f, bu1 = 4f / 64, bv0 = 28f / 32, bv1 = 31f / 32;
        for (int side : new int[]{-1, 1}) {
            float sx = side * x;
            quads.add(new Quad(Part.STRAP,
                    new Vertex(sx, top, -width, bu0, bv0, side, 0, 0),
                    new Vertex(sx, top, width, bu1, bv0, side, 0, 0),
                    new Vertex(sx, chin, width, bu1, bv1, side, 0, 0),
                    new Vertex(sx, chin, -width, bu0, bv1, side, 0, 0)));
        }
        quads.add(new Quad(Part.STRAP,
                new Vertex(-x, chin, -width, bu0, bv0, 0, 1, 0),
                new Vertex(x, chin, -width, bu1, bv0, 0, 1, 0),
                new Vertex(x, chin, width, bu1, bv1, 0, 1, 0),
                new Vertex(-x, chin, width, bu0, bv1, 0, 1, 0)));
    }

    private static float[] unit(float x, float y, float z) {
        float length = (float) Math.sqrt(x * x + y * y + z * z);
        return new float[]{x / length, y / length, z / length};
    }
    private static double finite(double value, double fallback, double min, double max) {
        return Math.clamp(Double.isFinite(value) ? value : fallback, min, max);
    }
}
