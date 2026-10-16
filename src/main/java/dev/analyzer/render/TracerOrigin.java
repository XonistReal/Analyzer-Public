package dev.analyzer.render;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Inverts the exact native line transform, including projection-space view bob and hurt tilt. */
public final class TracerOrigin {
    // Minecraft's rendertype_lines.vsh applies this after ModelViewMat, before ProjMat.
    public static final float LINE_VIEW_SHRINK = 1f - 1f / 256f;
    private static final float CLIP_DEPTH = 0f;
    private static boolean captureChecks;
    private static final Map<String, Sample> SAMPLES = new LinkedHashMap<>();
    private TracerOrigin() { }

    /** NDC origin sits two framebuffer pixels inside the top/bottom clip edge. */
    public static float screenY(String choice, int framebufferHeight) {
        float edge = 1f - Math.min(1f, 4f / Math.max(1, framebufferHeight));
        return "Top".equals(choice) ? edge : "Bottom".equals(choice) ? -edge : 0f;
    }

    /** Returns camera-relative world coordinates, or null when a renderer supplied unusable matrices. */
    public static Vector3f cameraRelative(Matrix4fc projection, Matrix4fc drawModelView,
                                          String choice, int framebufferHeight) {
        Matrix4f clip = lineClipMatrix(projection, drawModelView);
        float determinant = clip.determinant();
        if (!clip.isFinite() || !Float.isFinite(determinant) || Math.abs(determinant) < 1e-12f) return null;
        float y = screenY(choice, framebufferHeight);
        Vector4f point = new Matrix4f(clip).invert().transform(new Vector4f(0, y, CLIP_DEPTH, 1));
        if (!point.isFinite() || Math.abs(point.w) < 1e-8f) return null;
        Vector3f result = new Vector3f(point.x / point.w, point.y / point.w, point.z / point.w);
        if (!result.isFinite()) return null;
        return result;
    }

    /** Records the actual float vertex supplied to the native line buffer, never a proposed/cull-only origin. */
    public static void submitted(String module, String choice, Matrix4fc projection, Matrix4fc drawModelView,
                                 float x, float y, float z, int framebufferHeight) {
        if (captureChecks) {
            Matrix4f clip = lineClipMatrix(projection, drawModelView);
            Vector4f projected = clip.transform(new Vector4f(x, y, z, 1));
            if (projected.isFinite() && projected.w > 1e-8f) {
                SAMPLES.put(module, new Sample(module, choice, x, y, z,
                        projected.x / projected.w, projected.y / projected.w, projected.z / projected.w,
                        screenY(choice, framebufferHeight), framebufferHeight,
                        projection.m20(), projection.m21(), projection.m30(), projection.m31()));
                while (SAMPLES.size() > 64) SAMPLES.remove(SAMPLES.keySet().iterator().next());
            }
        }
    }

    public static Matrix4f lineClipMatrix(Matrix4fc projection, Matrix4fc drawModelView) {
        return new Matrix4f(projection).scale(LINE_VIEW_SHRINK).mul(drawModelView);
    }

    /** Opt-in read-only evidence for isolated development fixtures; ordinary rendering retains no samples. */
    public static void captureForChecks(boolean enabled) { captureChecks = enabled; SAMPLES.clear(); }
    public static List<Sample> samples() { return List.copyOf(SAMPLES.values()); }
    public record Sample(String module, String choice, float relativeX, float relativeY, float relativeZ,
                         float screenX, float screenY, float clipZ, float expectedY, int framebufferHeight,
                         float projection20, float projection21, float projection30, float projection31) { }
}
