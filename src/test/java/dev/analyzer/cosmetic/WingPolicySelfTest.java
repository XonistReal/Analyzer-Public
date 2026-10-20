package dev.analyzer.cosmetic;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.analyzer.core.AnalyzerCore;
import dev.analyzer.core.CosmeticModules;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/** The selected downloaded rig, actual extrusion topology, animation invariants and saved settings. */
public final class WingPolicySelfTest {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        sourceGeometry();
        selectedAssets();
        extrusionGeometry();
        animation();
        settingsPersistence();
        System.out.println("WingPolicySelfTest: " + assertions + " assertions passed");
    }

    private static void selectedAssets() throws Exception {
        JsonObject source = JsonParser.parseString(Files.readString(Path.of("design/cosmetics-review/models/wings/avian-review.json"))).getAsJsonObject();
        for (String style : List.of("angel", "dragon", "bat", "parrot")) {
            String filename = style + "_wings.png";
            byte[] data;
            try (var stream = WingPolicySelfTest.class.getResourceAsStream("/assets/analyzer/textures/cosmetics/wings/" + filename)) {
                if (stream == null) throw new AssertionError("Missing selected runtime texture " + filename);
                data = stream.readAllBytes();
            }
            String expected = source.getAsJsonObject("downloadedFiles").getAsJsonObject(filename).get("sha256").getAsString();
            check(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)).equals(expected), "Selected downloaded PNG bytes are unchanged: " + style);
            var image = ImageIO.read(new ByteArrayInputStream(data));
            check(image != null && image.getWidth() == 64 && image.getHeight() == 64 && image.getColorModel().hasAlpha(), "Selected texture retains native atlas dimensions and cutout alpha: " + style);
            image.flush();
        }
        check(Arrays.equals(Files.readAllBytes(Path.of("WINGS-MIT-LICENSE.txt")),
            Files.readAllBytes(Path.of("design/cosmetics-review/models/wings/LICENSE-pau101-Wings.md"))), "Selected rig and texture copies retain the complete unmodified MIT notice");
    }

    private static void sourceGeometry() throws Exception {
        Path source = Path.of("design/cosmetics-review/models/wings/avian-review.json");
        JsonArray original = JsonParser.parseString(Files.readString(source)).getAsJsonObject().getAsJsonArray("parts");
        var parts = WingRig.parts();
        check(parts.size() == 17 && original.size() == parts.size(), "The selected existing 17-part rig is retained");
        check(parts.getFirst().name().equals("root") && parts.getFirst().parent() == -1, "Exactly one named root starts the hierarchy");
        Set<String> names = new HashSet<>();
        boolean exactPivots = true, exactBodies = true, exactFeathers = true;
        int bodies = 0, feathers = 0;
        for (int i = 0; i < parts.size(); i++) {
            WingRig.Part part = parts.get(i);
            JsonObject originalPart = original.get(i).getAsJsonObject();
            check(names.add(part.name()) && part.name().equals(originalPart.get("name").getAsString()), "Source part name/order is unique: " + part.name());
            String expectedParent = originalPart.get("parent").isJsonNull() ? null : originalPart.get("parent").getAsString();
            check(i == 0 ? expectedParent == null : part.parent() >= 0 && part.parent() < i && parts.get(part.parent()).name().equals(expectedParent), "Source parent precedes and owns its actual child: " + part.name());
            exactPivots &= vector(originalPart.getAsJsonArray("pivot"), part.x(), part.y(), part.z());
            if (originalPart.has("box")) {
                bodies++;
                var box = part.box();
                JsonObject expected = originalPart.getAsJsonObject("box");
                exactBodies &= box != null && part.extrusion() == null
                    && vector(expected.getAsJsonArray("origin"), box.x(), box.y(), box.z())
                    && vector(expected.getAsJsonArray("size"), box.width(), box.height(), box.depth())
                    && vector(expected.getAsJsonArray("uv"), box.u(), box.v());
            } else if (originalPart.has("extrudedTexture")) {
                feathers++;
                var extrusion = part.extrusion();
                JsonObject expected = originalPart.getAsJsonObject("extrudedTexture");
                exactFeathers &= extrusion != null && part.box() == null
                    && vector(expected.getAsJsonArray("origin"), extrusion.x(), extrusion.y(), extrusion.z())
                    && vector(expected.getAsJsonArray("uv"), extrusion.u(), extrusion.v())
                    && close(expected.get("width").getAsDouble(), extrusion.width())
                    && close(expected.get("height").getAsDouble(), extrusion.height());
            } else check(part.box() == null && part.extrusion() == null, "Root contains no invented geometry");
        }
        check(exactPivots, "Every downloaded pivot is preserved without fitting edits to the mesh");
        check(exactBodies && bodies == 8, "All eight original cube origins, dimensions and atlas rectangles are retained");
        check(exactFeathers && feathers == 8, "All eight one-pixel feather extrusions preserve original atlas and geometry");
        rejectsMutation(() -> parts.clear(), "Shared rig definitions cannot be changed between players or reloads");
    }

    private static void extrusionGeometry() {
        boolean finite = true, uvBounded = true, atlasLocal = true, outward = true, bounded = true, normalsUnit = true, fourVertices = true;
        int totalQuads = 48;
        for (var part : WingRig.parts()) {
            var extrusion = part.extrusion();
            if (extrusion == null) continue;
            var quads = WingMesh.extrusion(extrusion);
            check(quads.size() == 2 + 2 * extrusion.width() + 2 * extrusion.height(), "Extruded cutout retains its per-pixel sides: " + part.name());
            totalQuads += quads.size();
            Set<String> directions = new HashSet<>();
            for (var quad : quads) {
                fourVertices &= quad.vertices().size() == 4;
                double nx = quad.nx(), ny = quad.ny(), nz = quad.nz();
                normalsUnit &= close(nx * nx + ny * ny + nz * nz, 1);
                directions.add(nx + ":" + ny + ":" + nz);
                var a = quad.vertices().get(0); var b = quad.vertices().get(1); var c = quad.vertices().get(2);
                double ax = b.x() - a.x(), ay = b.y() - a.y(), az = b.z() - a.z();
                double bx = c.x() - a.x(), by = c.y() - a.y(), bz = c.z() - a.z();
                outward &= (ay * bz - az * by) * nx + (az * bx - ax * bz) * ny + (ax * by - ay * bx) * nz > 0;
                for (var vertex : quad.vertices()) {
                    finite &= Double.isFinite(vertex.x()) && Double.isFinite(vertex.y()) && Double.isFinite(vertex.z()) && Double.isFinite(vertex.u()) && Double.isFinite(vertex.v());
                    uvBounded &= inside(vertex.u(), 0, 64) && inside(vertex.v(), 0, 64);
                    atlasLocal &= inside(vertex.u(), extrusion.u(), extrusion.u() + extrusion.width())
                        && inside(vertex.v(), extrusion.v(), extrusion.v() + extrusion.height());
                    bounded &= inside(vertex.x(), extrusion.x(), extrusion.x() + extrusion.width())
                        && inside(vertex.y(), extrusion.y(), extrusion.y() + extrusion.height())
                        && inside(vertex.z(), extrusion.z(), extrusion.z() + 1);
                }
            }
            check(directions.size() == 6, "Every feather has actual front/back and four side directions: " + part.name());
        }
        check(totalQuads == 396, "Selected native model remains bounded to 396 source quads before cutout discard");
        check(fourVertices, "Every submitted feather face has exactly four vertices");
        check(finite && bounded, "All feather vertices are finite and inside their actual one-pixel solid");
        check(uvBounded && atlasLocal, "No feather samples outside its downloaded atlas rectangle or stretches the entire texture");
        check(normalsUnit && outward, "Each nondegenerate quad winds outward consistently with its supplied unit normal");
    }

    private static void animation() {
        var still = WingAnimation.pose(WingAnimation.Mode.STILL, 0, 1, 1, false, false, false);
        double[][] rest = {{0, -23.5, -16}, {0, 13, 29}, {0, 12, -28}, {0, 4, 18.3}};
        boolean sourceRest = still.bones().size() == 4 && still.feathers().size() == 4;
        for (int i = 0; i < 4; i++) {
            var angle = still.bones().get(i);
            sourceRest &= close(angle.x(), rest[i][0]) && close(angle.y(), rest[i][1]) && close(angle.z(), rest[i][2]);
        }
        check(sourceRest, "Still uses the original articulated rest pose");
        check(close(still.feathers().getFirst().z(), 23.48), "Rest feather angle preserves the source pivot pose");
        boolean finite = true, mirror = true, bounded = true, frozenSpeed = true, zeroStrength = true, originalJointCount = true;
        for (var mode : WingAnimation.Mode.values()) {
            var stopped = WingAnimation.pose(mode, 0, 0, 1, false, false, false);
            var noMotion = WingAnimation.pose(mode, 0, 1, 0, false, false, false);
            for (double age : new double[]{0, 1, 35.5, 100, 10_000, 1_000_000}) {
                var pose = WingAnimation.pose(mode, age, 3, 1.5, false, false, false);
                originalJointCount &= pose.bones().size() == 4 && pose.feathers().size() == 4;
                frozenSpeed &= stopped.equals(WingAnimation.pose(mode, age, 0, 1, false, false, false));
                zeroStrength &= noMotion.equals(WingAnimation.pose(mode, age, 1, 0, false, false, false));
                for (var rotations : List.of(pose.bones(), pose.feathers())) for (var angle : rotations) {
                    finite &= Double.isFinite(angle.x()) && Double.isFinite(angle.y()) && Double.isFinite(angle.z());
                    bounded &= Math.abs(angle.x()) <= 180 && Math.abs(angle.y()) <= 180 && Math.abs(angle.z()) <= 180;
                    var left = WingAnimation.mirror(angle, false); var right = WingAnimation.mirror(angle, true);
                    mirror &= left.equals(angle) && close(left.x(), right.x()) && close(left.y(), -right.y()) && close(left.z(), -right.z())
                        && WingAnimation.mirror(right, true).equals(angle);
                }
            }
        }
        check(finite && bounded, "Animation at every offered mode stays finite and bounded even at maximum controls and long sessions");
        check(originalJointCount, "Every mode drives the four existing bone/feather pairs");
        check(mirror, "Actual renderer mirror helper preserves pitch and mirrors yaw/roll exactly once");
        check(frozenSpeed && zeroStrength, "Zero speed freezes time and zero strength removes time-varying motion");
        check(!WingAnimation.pose(WingAnimation.Mode.FLAP, 0, 1, 1, false, false, false)
            .equals(WingAnimation.pose(WingAnimation.Mode.FLAP, 17, 1, 1, false, false, false)), "Nonzero flap settings visibly articulate the existing rig");
        check(!WingAnimation.pose(WingAnimation.Mode.IDLE, 0, 1, 1, false, false, false)
            .equals(WingAnimation.pose(WingAnimation.Mode.IDLE, 37, 1, 1, false, false, false)), "Idle retains its original subtle articulated motion");
        check(WingAnimation.pose(WingAnimation.Mode.AUTO, 12, 1, 1, true, false, true).equals(WingAnimation.pose(WingAnimation.Mode.GLIDE, 12, 1, 1, false, false, false)), "Native gliding selects the existing glide pose");
        check(close(WingAnimation.mountY(0), -3.0 / 16), "Default mount implements the approved three-pixel upward fit");
        check(WingAnimation.mountY(2) < WingAnimation.mountY(0), "Positive height raises the model in native downward-Y coordinates");
        check(close(WingAnimation.mountZ(2, false, true), 2.0 / 16)
            && close(WingAnimation.mountZ(2, true, false), 2.0 / 16), "Armor spacing has no effect when disabled or no armor is worn");
        check(WingAnimation.mountZ(2, true, true) > WingAnimation.mountZ(2, false, true), "Actual armor receives additional back clearance");
    }

    private static void settingsPersistence() throws Exception {
        var definition = CosmeticModules.definitions().stream().filter(module -> module.id().equals("wings")).findFirst().orElseThrow();
        check(!definition.defaultEnabled(), "Wings are opt-in in fresh and older configurations");
        check(definition.setting("style").choices().equals(List.of("Angel", "Dragon", "Bat", "Parrot")), "Only the four approved downloaded looks are offered");
        check(CosmeticModules.definitions().stream().noneMatch(module -> module.id().contains("hat") || module.id().equals("crown")), "Unapproved accessory shortlist is not installed as hats");
        Path temporary = Files.createTempDirectory("analyzer-wings-check-");
        try {
            Path file = temporary.resolve("config.json");
            Files.writeString(file, "{\"schemaVersion\":1,\"modules\":{\"cape\":{\"enabled\":true,\"settings\":{\"style\":\"Stripes\",\"opacity\":0.6}}}}");
            byte[] original = Files.readAllBytes(file);
            AnalyzerCore core = new AnalyzerCore(temporary); core.load();
            check(!core.isEnabled("wings") && core.getChoice("wings", "style").equals("Angel"), "Older config gains disabled wings with an explicit default look");
            check(core.isEnabled("cape") && core.getChoice("cape", "style").equals("Stripes") && close(core.getNumber("cape", "opacity"), .6), "Older independent cape choices are preserved");
            check(Arrays.equals(original, Files.readAllBytes(file)), "Loading new defaults never rewrites the user's old config");
            check(core.getBoolean("wings", "hide-elytra") && core.getBoolean("wings", "armor-clearance")
                && !core.getBoolean("wings", "fullbright"), "Fresh defaults respect Elytra, armor clearance and native lighting");
            for (String style : List.of("Angel", "Dragon", "Bat", "Parrot")) {
                core.setEnabled("wings", true); core.setValue("wings", "style", style);
                core.setValue("wings", "animation", "Flap"); core.setValue("wings", "speed", 1.75);
                core.setValue("wings", "strength", .65); core.setValue("wings", "scale", 1.2);
                core.setValue("wings", "height", 2.25); core.setValue("wings", "back-offset", 1.5);
                core.setValue("wings", "hide-elytra", false); core.setValue("wings", "armor-clearance", false); core.setValue("wings", "fullbright", true);
                core.save(); core.saveProfile("selected-wings");
                var expected = core.values("wings");
                AnalyzerCore restored = new AnalyzerCore(temporary); restored.load();
                check(restored.isEnabled("wings") && restored.values("wings").equals(expected), "Every saved control and selected texture round trips: " + style);
                restored.reset("wings"); restored.loadProfile("selected-wings");
                check(restored.isEnabled("wings") && restored.values("wings").equals(expected), "Named profiles restore all selected wing controls: " + style);
                check(restored.loadWarnings().isEmpty(), "Approved wing profile loads without discarded settings: " + style);
            }
        } finally {
            try (var paths = Files.walk(temporary)) { for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path); }
        }
    }

    private static boolean vector(JsonArray original, double... actual) {
        if (original.size() != actual.length) return false;
        for (int i = 0; i < actual.length; i++) if (!close(original.get(i).getAsDouble(), actual[i])) return false;
        return true;
    }
    private static boolean close(double a, double b) { return Double.isFinite(a) && Double.isFinite(b) && Math.abs(a - b) < .00001; }
    private static boolean inside(double n, double low, double high) { return n >= low - .00001 && n <= high + .00001; }
    private static void rejectsMutation(Runnable action, String message) {
        try { action.run(); } catch (UnsupportedOperationException expected) { check(true, message); return; }
        throw new AssertionError(message);
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); assertions++; }
}
