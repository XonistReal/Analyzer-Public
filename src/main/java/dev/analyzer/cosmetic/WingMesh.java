package dev.analyzer.cosmetic;

import java.util.ArrayList;
import java.util.List;

/** Native-vertex port of Wings' thick, alpha-cutout texture extrusion.
 * Copyright (c) 2018 Paul Fulham. MIT; see WINGS-MIT-LICENSE.txt. */
public final class WingMesh {
    public record Vertex(float x, float y, float z, float u, float v) { }
    public record Quad(List<Vertex> vertices, float nx, float ny, float nz) {
        public Quad { vertices = List.copyOf(vertices); }
    }
    private WingMesh() { }
    public static List<Quad> extrusion(WingRig.Extrusion p) {
        if (p.width() < 1 || p.width() > 64 || p.height() < 1 || p.height() > 64)
            throw new IllegalArgumentException("Wing extrusion exceeds its fixed atlas");
        var out = new ArrayList<Quad>(2 + 2 * p.width() + 2 * p.height());
        float x = p.x(), y = p.y(), z = p.z(), x1 = x + p.width(), y1 = y + p.height(), z1 = z + 1;
        float u = p.u(), v = p.v(), u1 = u + p.width(), v1 = v + p.height();
        face(out, x,y,z, x1,y1,z, u,v,u1,v1, false, 0,0,-1);
        face(out, x,y1,z1, x1,y,z1, u,v1,u1,v, false, 0,0,1);
        for (int k = 0; k < p.width(); k++) {
            float sample = u + k + .5f;
            face(out, x+k,y,z, x+k,y1,z1, sample,v,sample,v1, false, -1,0,0);
            face(out, x+k+1,y1,z, x+k+1,y,z1, sample,v1,sample,v, false, 1,0,0);
        }
        for (int k = 0; k < p.height(); k++) {
            float sample = v + k + .5f;
            face(out, x,y+k+1,z, x1,y+k+1,z1, u,sample,u1,sample, true, 0,1,0);
            face(out, x1,y+k,z, x,y+k,z1, u1,sample,u,sample, true, 0,-1,0);
        }
        return List.copyOf(out);
    }
    private static void face(List<Quad> out, float x,float y,float z, float x1,float y1,float z1,
                             float u,float v,float u1,float v1, boolean vertical, float nx,float ny,float nz) {
        // Same per-corner atlas orientation as Minecraft ModelPart. UVs remain in atlas pixels.
        out.add(new Quad(List.of(new Vertex(x1,y,z,u1,v), new Vertex(x,y,vertical ? z : z1,u,v),
                new Vertex(x,y1,z1,u,v1), new Vertex(x1,y1,vertical ? z1 : z,u1,v1)), nx,ny,nz));
    }
}
