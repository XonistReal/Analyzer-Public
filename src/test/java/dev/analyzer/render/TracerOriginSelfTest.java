package dev.analyzer.render;

import dev.analyzer.core.BuiltinModules;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.List;

/** A screen origin must survive the same combined transform as an emitted native line vertex. */
public final class TracerOriginSelfTest {
    private static int assertions;
    public static void main(String[] args) {
        settings();
        projections();
        unsafeMatrices();
        submittedEvidence();
        System.out.println("Analyzer tracer origin checks passed: " + assertions + " assertions");
    }

    private static void settings() {
        int sources = 0;
        for (var module : BuiltinModules.definitions()) {
            boolean tracer = module.id().equals("tracers") || module.settings().stream().anyMatch(value -> value.id().equals("tracers"));
            if (!tracer) continue;
            var origin = module.settings().stream().filter(value -> value.id().equals("tracer-origin")).findFirst().orElseThrow();
            check(origin.choices().equals(List.of("Crosshair", "Bottom", "Top")), "Each tracer producer exposes the same source choices: " + module.id());
            check(origin.defaultValue().equals("Crosshair"), "Missing historical source choices default to crosshair: " + module.id());
            sources++;
        }
        check(sources == 29, "Entity tracers, storage tracers and all 27 finder settings have an independent origin");
        var target = BuiltinModules.definitions().stream().filter(module -> module.id().equals("tracers")).findFirst().orElseThrow()
                .settings().stream().filter(value -> value.id().equals("anchor")).findFirst().orElseThrow();
        check(target.choices().equals(List.of("Center", "Feet", "Head")), "Historical target anchor remains independent of the new screen origin");
    }

    private static void projections() {
        for (int height : new int[]{1, 240, 1080, 2160}) {
            for (float aspect : new float[]{.75f, 16f / 9f, 32f / 9f}) {
                for (int fov : new int[]{30, 70, 110}) {
                    for (int phase = 0; phase < 8; phase++) {
                        // Minecraft applies view bob, damage roll and nausea before passing its projection to world rendering.
                        Matrix4f projection = new Matrix4f().perspective((float) Math.toRadians(fov), aspect, .05f, 1024f);
                        float step = phase * .73f;
                        projection.rotateZ((float) Math.sin(step) * .12f)
                                .translate((float) Math.cos(step) * .07f, (float) Math.sin(step) * .11f, 0)
                                .rotateX((float) Math.cos(step) * .04f)
                                .scale(1 + phase * .02f, 1, 1);
                        Matrix4f view = new Matrix4f().rotateX(phase * .17f - .6f).rotateY(phase * 1.1f);
                        // A translated view also covers alternate camera transforms while camera translation remains external.
                        if ((phase & 1) != 0) view.translate(.05f, -.1f, .02f);
                        Matrix4f originalProjection = new Matrix4f(projection), originalView = new Matrix4f(view);
                        for (String origin : List.of("Crosshair", "Bottom", "Top")) {
                            Vector3f point = TracerOrigin.cameraRelative(projection, view, origin, height);
                            check(point != null && point.isFinite(), "Finite source at FOV/aspect/camera phase " + phase);
                            Vector4f projected = TracerOrigin.lineClipMatrix(projection, view).transform(new Vector4f(point, 1));
                            check(projected.w > 0, "Source remains in front of the camera");
                            close(0, projected.x / projected.w, "Source is locked to viewport center horizontally");
                            close(TracerOrigin.screenY(origin, height), projected.y / projected.w, "Source is locked to selected screen height");
                            close(0, projected.z / projected.w, "Source remains beyond the near plane and before far clipping");
                        }
                        check(projection.equals(originalProjection) && view.equals(originalView), "Inversion never mutates renderer-owned matrices");
                    }
                }
            }
        }
        Matrix4f bobbed = new Matrix4f().perspective((float) Math.toRadians(70), 16f/9f, .05f, 256f)
                .translate(.08f, -.12f, 0).rotateZ(.1f);
        Vector4f oldStart = TracerOrigin.lineClipMatrix(bobbed, new Matrix4f()).transform(new Vector4f(0, -.025f, -.25f, 1));
        check(Math.abs(oldStart.x / oldStart.w) > .1 || Math.abs(oldStart.y / oldStart.w) > .1,
                "Regression fixture proves camera-rotation-only starts drift visibly during view bob");
        close(0, TracerOrigin.screenY("unknown historical value", 1080), "Unknown input safely selects center");
        close(1 - 4f/1080, TracerOrigin.screenY("Top",1080), "Top is two framebuffer pixels inside clipping");
        close(-1 + 4f/1080, TracerOrigin.screenY("Bottom",1080), "Bottom is two framebuffer pixels inside clipping");
    }

    private static void unsafeMatrices() {
        check(TracerOrigin.cameraRelative(new Matrix4f().zero(), new Matrix4f(), "Crosshair", 1080) == null,
                "A singular projection skips only the tracer origin");
        check(TracerOrigin.cameraRelative(new Matrix4f().m00(Float.NaN), new Matrix4f(), "Top",1080) == null,
                "Nonfinite projection cannot submit invalid geometry");
        check(TracerOrigin.cameraRelative(new Matrix4f(), new Matrix4f().zero(), "Bottom", 1080) == null,
                "A singular model view cannot submit invalid geometry");
        close(0, TracerOrigin.screenY("Bottom", 0), "Transient zero-height viewport remains bounded");
    }

    private static void submittedEvidence() {
        Matrix4f projection = new Matrix4f().perspective(1.2f, 1.7f, .05f, 512);
        Vector3f vertex = TracerOrigin.cameraRelative(projection, new Matrix4f(), "Top",1080);
        check(TracerOrigin.samples().isEmpty(), "Computing an unused or culled origin does not report a draw");
        TracerOrigin.captureForChecks(true);
        check(TracerOrigin.samples().isEmpty(), "Enabling isolated native evidence clears previous samples");
        for (int i = 0; i < 80; i++) TracerOrigin.submitted("producer-" + i, "Top", projection, new Matrix4f(),
                vertex.x, vertex.y, vertex.z, 1080);
        check(TracerOrigin.samples().size() == 64, "Development samples retain a bounded producer count");
        var sample = TracerOrigin.samples().getLast();
        close(0, sample.screenX(), "Submitted float vertex projects to the expected horizontal origin");
        close(sample.expectedY(), sample.screenY(), "Submitted float vertex projects to the expected vertical origin");
        TracerOrigin.captureForChecks(false);
        TracerOrigin.submitted("disabled", "Top", projection, new Matrix4f(), vertex.x, vertex.y, vertex.z,1080);
        check(TracerOrigin.samples().isEmpty(), "Ordinary rendering retains no tracer evidence");
    }

    private static void close(double expected, double actual, String message) {
        check(Double.isFinite(actual) && Math.abs(expected - actual) < .00002, message + ": " + actual);
    }
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
