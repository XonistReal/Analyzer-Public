package dev.analyzer.cosmetic;

import dev.analyzer.core.CosmeticModules;
import dev.analyzer.core.ModuleDefinition;
import dev.analyzer.core.Setting;
import javax.imageio.ImageIO;
import java.util.List;

/** Geometry, texture and settings checks for the Straw Hat cosmetic. */
public final class StrawHatSelfTest {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        for (boolean helmet : new boolean[]{false, true})
            for (double size = 0.8; size <= 1.6001; size += 0.05)
                for (double height = 0.5; height <= 1.8001; height += 0.1) geometry(size, height, helmet);
        texture();
        settings();
        System.out.println("Straw Hat checks passed " + assertions + " assertions.");
    }

    private static void geometry(double size, double height, boolean helmet) {
        var shape = StrawHatMesh.shape(size, height, 0, helmet);
        String where = " (size " + size + ", height " + height + ", helmet " + helmet + ")";
        check(shape.apexY() < shape.rimY(), "The apex is above the brim" + where);
        // The outermost head layer: skin hat layer (+0.5 px) or helmet (+1 px).
        double half = 4 + (helmet ? 1 : 0.5), top = -8 - (helmet ? 1 : 0.5);
        check(StrawHatMesh.surfaceY(shape, half * Math.sqrt(2)) <= top + 1.0e-4, "The cone clears the head corners" + where);
        check(StrawHatMesh.surfaceY(shape, half) < top, "The cone clears the head edges" + where);
        check(shape.radius() > half * Math.sqrt(2), "The brim extends past the head" + where);
        List<StrawHatMesh.Quad> quads = StrawHatMesh.build(shape, true);
        check(quads.size() == StrawHatMesh.SEGMENTS * 3 + 3, "Outer, inner and rim quads per segment, plus a three-piece strap" + where);
        check(StrawHatMesh.build(shape, false).size() == StrawHatMesh.SEGMENTS * 3, "The strap is optional" + where);
        for (var quad : quads) for (var vertex : quad.vertices()) {
            check(Float.isFinite(vertex.x() + vertex.y() + vertex.z()), "Finite vertices");
            check(vertex.u() >= 0 && vertex.u() <= 1 && vertex.v() >= 0 && vertex.v() <= 1, "Texture coordinates stay inside the texture");
            double length = Math.sqrt(vertex.nx() * vertex.nx() + vertex.ny() * vertex.ny() + vertex.nz() * vertex.nz());
            check(Math.abs(length - 1) < 1.0e-4, "Unit normals for native lighting");
            if (quad.part() == StrawHatMesh.Part.OUTER && Math.hypot(vertex.x(), vertex.z()) > 0.1) {
                check(vertex.ny() < 0, "Outer straw faces up (model Y points down)");
                check(vertex.nx() * vertex.x() + vertex.nz() * vertex.z() > 0, "Outer straw faces outward");
            }
            if (quad.part() == StrawHatMesh.Part.INNER) check(vertex.ny() > 0, "The inner weave faces down");
        }
    }

    private static void texture() throws Exception {
        try (var stream = StrawHatSelfTest.class.getResourceAsStream("/assets/analyzer/textures/cosmetics/hats/straw_hat.png")) {
            check(stream != null, "The original straw texture is a runtime resource");
            var image = ImageIO.read(stream);
            check(image.getWidth() == 64 && image.getHeight() == 32, "The straw texture is 64 x 32");
            boolean opaque = true;
            for (int y = 0; y < 32; y++) for (int x = 0; x < 64; x++) opaque &= (image.getRGB(x, y) >>> 24) == 255;
            check(opaque, "The cutout straw texture has no holes");
        }
    }

    private static void settings() {
        ModuleDefinition hat = CosmeticModules.definitions().stream().filter(module -> module.id().equals("straw-hat")).findFirst().orElse(null);
        check(hat != null && !hat.defaultEnabled(), "Straw Hat is an opt-in cosmetic");
        Setting<?> style = hat.setting("style");
        check(style.choices().equals(List.of("Straw", "Lacquered", "Glass", "Rainbow glass")) && style.defaultValue().equals("Straw"),
                "Every exposed hat style is implemented");
        check(hat.setting("who").defaultValue().equals("Self"), "By default only your own hat is shown");
        check(hat.settings().stream().map(Setting::id).distinct().count() == hat.settings().size(), "Unique hat setting IDs");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        assertions++;
    }
}
