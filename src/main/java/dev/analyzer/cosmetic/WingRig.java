package dev.analyzer.cosmetic;

import java.util.List;

/** Paul Fulham's existing avian joint rig, ported to native 1.21.4 model parts.
 * Copyright (c) 2018 Paul Fulham. MIT; see WINGS-MIT-LICENSE.txt. */
public final class WingRig {
    public record Box(float x, float y, float z, int width, int height, int depth, int u, int v) { }
    public record Extrusion(float x, float y, float z, int width, int height, int u, int v) { }
    public record Part(String name, int parent, float x, float y, float z, Box box, Extrusion extrusion) { }
    private static final List<Part> PARTS = List.of(
            new Part("root", -1, 0, 0, 0, null, null),
            new Part("coracoidLeft", 0, 1.5f, 5.5f, 2.5f, new Box(0, -1.5f, -1.5f, 5, 3, 3, 0, 28), null),
            new Part("coracoidRight", 0, -1.5f, 5.5f, 2.5f, new Box(-5, -1.5f, -1.5f, 5, 3, 3, 0, 34), null),
            new Part("humerusLeft", 1, 4.7f, -.6f, .1f, new Box(-.1f, -1.1f, -2, 7, 3, 4, 0, 0), null),
            new Part("humerusRight", 2, -4.7f, -.6f, .1f, new Box(-6.9f, -1.1f, -2, 7, 3, 4, 0, 7), null),
            new Part("ulnaLeft", 3, 6.5f, .2f, .1f, new Box(0, -1.5f, -1.5f, 9, 3, 3, 22, 0), null),
            new Part("ulnaRight", 4, -6.5f, .2f, .1f, new Box(-9, -1.5f, -1.5f, 9, 3, 3, 22, 6), null),
            new Part("carpalsLeft", 5, 8.5f, 0, 0, new Box(0, -1, -1, 5, 2, 2, 46, 0), null),
            new Part("carpalsRight", 6, -8.5f, 0, 0, new Box(-5, -1, -1, 5, 2, 2, 46, 4), null),
            new Part("feathersCoracoidLeft", 1, .4f, 0, 1, null, new Extrusion(0, 0, -1, 6, 8, 6, 40)),
            new Part("feathersCoracoidRight", 2, -.4f, 0, 1, null, new Extrusion(-6, 0, -1, 6, 8, 0, 40)),
            new Part("feathersTertiaryLeft", 3, 0, 1.5f, 1, null, new Extrusion(0, 0, -.5f, 10, 14, 10, 14)),
            new Part("feathersTertiaryRight", 4, 0, 1.5f, 1, null, new Extrusion(-10, 0, -.5f, 10, 14, 0, 14)),
            new Part("feathersSecondaryLeft", 5, 0, 1, 0, null, new Extrusion(-2, 0, -.5f, 11, 12, 31, 14)),
            new Part("feathersSecondaryRight", 6, 0, 1, 0, null, new Extrusion(-9, 0, -.5f, 11, 12, 20, 14)),
            new Part("feathersPrimaryLeft", 7, 0, 0, 0, null, new Extrusion(0, -2.1f, -.5f, 11, 11, 53, 14)),
            new Part("feathersPrimaryRight", 8, 0, 0, 0, null, new Extrusion(-11, -2.1f, -.5f, 11, 11, 42, 14))
    );
    private WingRig() { }
    public static List<Part> parts() { return PARTS; }
}
