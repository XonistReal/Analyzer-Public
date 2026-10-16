package dev.analyzer.effects;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.analyzer.AnalyzerClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Original model-space material shaders for captured, already posed entity quads.
 * Capture supplies camera/view-space positions and inverse-transpose transformed
 * normals. The shader applies the live projection exactly once. Neither pass writes
 * scene depth; visible and occluded colors remain independently configurable.
 */
public final class ChamsShader {
    private static final int STRIDE = 6;
    private static final int MAX_VERTICES = 262_144;
    private static int program, vao, vbo, ebo, indexQuadCapacity;
    private static int projection, tint, style, time, strength, wireSize;
    private static FloatBuffer upload;
    private static boolean failed;
    private static long draws, passes, vertices;

    private ChamsShader() {}

    public static boolean isHealthy() { return !failed; }
    public static long drawCalls() { return draws; }
    public static long passCount() { return passes; }
    public static long submittedVertices() { return vertices; }
    public static String status() {
        return failed ? "Entity chams unavailable; native entity models retained"
                : program == 0 ? "Entity material shader ready" : "Native entity material shader active";
    }

    public static boolean draw(float[] xyzNormals, int vertexCount, int visibleArgb, int hiddenArgb,
                               String material, boolean throughWalls, float opacity, float seconds) {
        return draw(xyzNormals, vertexCount, visibleArgb, hiddenArgb, material, throughWalls, opacity, seconds, 1f, 1f);
    }

    /**
     * Render an array of native model quads, six floats per vertex (XYZ then normal).
     * ARGB alpha supplies each pass's opacity; opacity multiplies both. Glow is an
     * emissive surface/rim, not a post-process bloom. Glass is a transparent Fresnel
     * material, not a refraction copy of the world. Returns false for caller fallback.
     */
    public static boolean draw(float[] xyzNormals, int vertexCount, int visibleArgb, int hiddenArgb,
                               String material, boolean throughWalls, float opacity, float seconds,
                               float effectStrength, float wireWidth) {
        if (failed) return false;
        if (vertexCount == 0 || opacity <= 0f) return true;
        if (xyzNormals == null || vertexCount < 0 || vertexCount > MAX_VERTICES || (vertexCount & 3) != 0
                || xyzNormals.length < vertexCount * STRIDE
                || !Float.isFinite(opacity) || !Float.isFinite(seconds)
                || !Float.isFinite(effectStrength) || !Float.isFinite(wireWidth)) return false;
        int floatCount = vertexCount * STRIDE;
        for (int i = 0; i < floatCount; i++) if (!Float.isFinite(xyzNormals[i])) return false;
        try (State ignored = new State(); MemoryStack stack = MemoryStack.stackPush()) {
            ensureProgram();
            ensureUpload(floatCount);
            GL20.glUseProgram(program);
            GL30.glBindVertexArray(vao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            upload.clear();
            upload.put(xyzNormals, 0, floatCount).flip();
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, upload, GL15.GL_STREAM_DRAW);
            ensureIndices(vertexCount / 4);

            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            // Normal-facing fragments are selected in the shader. This works with
            // vanilla's mirrored model transforms without changing front-face state.
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
            GL11.glDisable(GL11.GL_POLYGON_OFFSET_LINE);
            GL11.glDisable(GL11.GL_POLYGON_OFFSET_POINT);
            GL11.glColorMask(true, true, true, true);
            GL11.glEnable(GL11.GL_BLEND);
            GL14.glBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL20.glBlendEquationSeparate(GL14.GL_FUNC_ADD, GL14.GL_FUNC_ADD);
            GL20.glUniformMatrix4fv(projection, false, RenderSystem.getProjectionMatrix().get(stack.mallocFloat(16)));
            int shaderStyle = styleCode(material);
            GL20.glUniform1i(style, shaderStyle);
            // Keep trigonometric precision on sessions lasting many hours.
            GL20.glUniform1f(time, seconds % 4096f);
            GL20.glUniform1f(strength, Math.clamp(effectStrength, 0f, 2f));
            // Quad-edge fragments provide pixel-width wireframe even on core-profile
            // drivers that reject wide glLineWidth values despite their range query.
            GL20.glUniform1f(wireSize, Math.clamp(wireWidth, 0.5f, 3f));
            int count = vertexCount / 4 * 6;
            float alpha = Math.clamp(opacity, 0f, 1f);
            if (throughWalls && (hiddenArgb >>> 24) != 0) pass(hiddenArgb, alpha, GL11.GL_GREATER, count);
            if ((visibleArgb >>> 24) != 0) pass(visibleArgb, alpha, GL11.GL_LEQUAL, count);
            draws++;
            vertices += vertexCount;
            return true;
        } catch (Exception failure) {
            failed = true;
            AnalyzerClient.LOGGER.error("Analyzer entity material shader unavailable; retaining native entity models until resource reload", failure);
            return false;
        }
    }

    private static void pass(int argb, float alpha, int comparison, int count) {
        GL11.glDepthFunc(comparison);
        GL20.glUniform4f(tint, (argb >>> 16 & 255) / 255f, (argb >>> 8 & 255) / 255f,
                (argb & 255) / 255f, (argb >>> 24) / 255f * alpha);
        GL11.glDrawElements(GL11.GL_TRIANGLES, count, GL11.GL_UNSIGNED_INT, 0L);
        passes++;
    }

    /** Stable saved labels; unknown older labels use the simple flat material. */
    private static int styleCode(String material) {
        return switch (material == null ? "" : material.toLowerCase(Locale.ROOT)) {
            case "metallic" -> 1;
            case "glass" -> 2;
            case "glow" -> 3;
            case "wireframe" -> 4;
            default -> 0;
        };
    }

    private static void ensureUpload(int floats) {
        if (upload != null && upload.capacity() >= floats) return;
        int capacity = Math.max(4096, Integer.highestOneBit(floats - 1) << 1);
        FloatBuffer next = MemoryUtil.memAllocFloat(capacity);
        if (upload != null) MemoryUtil.memFree(upload);
        upload = next;
    }

    private static void ensureIndices(int quads) {
        if (quads <= indexQuadCapacity) return;
        int capacity = Math.max(128, Integer.highestOneBit(quads - 1) << 1);
        IntBuffer data = MemoryUtil.memAllocInt(capacity * 6);
        try {
            for (int quad = 0; quad < capacity; quad++) {
                int base = quad * 4;
                data.put(base).put(base + 1).put(base + 2).put(base + 2).put(base + 3).put(base);
            }
            data.flip();
            GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ebo);
            GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, data, GL15.GL_STATIC_DRAW);
            indexQuadCapacity = capacity;
        } finally {
            MemoryUtil.memFree(data);
        }
    }

    private static void ensureProgram() throws Exception {
        if (program != 0) return;
        int vertex = 0, fragment = 0, linked = 0, array = 0, vertexBuffer = 0, elementBuffer = 0;
        try {
            vertex = compile(GL20.GL_VERTEX_SHADER, read("chams.vsh"));
            fragment = compile(GL20.GL_FRAGMENT_SHADER, read("chams.fsh"));
            linked = GL20.glCreateProgram();
            GL20.glAttachShader(linked, vertex);
            GL20.glAttachShader(linked, fragment);
            GL20.glBindAttribLocation(linked, 0, "Position");
            GL20.glBindAttribLocation(linked, 1, "Normal");
            GL30.glBindFragDataLocation(linked, 0, "fragColor");
            GL20.glLinkProgram(linked);
            if (GL20.glGetProgrami(linked, GL20.GL_LINK_STATUS) == GL11.GL_FALSE)
                throw new IllegalStateException(GL20.glGetProgramInfoLog(linked));
            projection = uniform(linked, "ProjMat");
            tint = uniform(linked, "Tint");
            style = uniform(linked, "Style");
            time = uniform(linked, "Time");
            strength = uniform(linked, "Strength");
            wireSize = uniform(linked, "WireWidth");
            array = GL30.glGenVertexArrays();
            vertexBuffer = GL15.glGenBuffers();
            elementBuffer = GL15.glGenBuffers();
            GL30.glBindVertexArray(array);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vertexBuffer);
            GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, elementBuffer);
            GL20.glEnableVertexAttribArray(0);
            GL20.glEnableVertexAttribArray(1);
            GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, STRIDE * Float.BYTES, 0L);
            GL20.glVertexAttribPointer(1, 3, GL11.GL_FLOAT, false, STRIDE * Float.BYTES, 3L * Float.BYTES);
            program = linked;
            vao = array;
            vbo = vertexBuffer;
            ebo = elementBuffer;
            AnalyzerClient.LOGGER.info("Analyzer native entity materials compiled: Flat / Metallic / Glass / Glow / Wireframe");
        } catch (Exception failure) {
            if (linked != 0) GL20.glDeleteProgram(linked);
            if (array != 0) GL30.glDeleteVertexArrays(array);
            if (vertexBuffer != 0) GL15.glDeleteBuffers(vertexBuffer);
            if (elementBuffer != 0) GL15.glDeleteBuffers(elementBuffer);
            throw failure;
        } finally {
            if (vertex != 0) GL20.glDeleteShader(vertex);
            if (fragment != 0) GL20.glDeleteShader(fragment);
        }
    }

    private static String read(String file) throws Exception {
        var resource = MinecraftClient.getInstance().getResourceManager()
                .getResourceOrThrow(Identifier.of("analyzer", "shaders/" + file));
        try (var stream = resource.getInputStream()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static int uniform(int linked, String name) {
        int location = GL20.glGetUniformLocation(linked, name);
        if (location == -1) throw new IllegalStateException("Missing chams shader uniform: " + name);
        return location;
    }

    private static int compile(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(shader);
            GL20.glDeleteShader(shader);
            throw new IllegalStateException(log);
        }
        return shader;
    }

    /** Render-thread reload/shutdown; a reload permits one fresh compile attempt. */
    public static void close() {
        if (program != 0) GL20.glDeleteProgram(program);
        if (vao != 0) GL30.glDeleteVertexArrays(vao);
        if (vbo != 0) GL15.glDeleteBuffers(vbo);
        if (ebo != 0) GL15.glDeleteBuffers(ebo);
        if (upload != null) MemoryUtil.memFree(upload);
        program = vao = vbo = ebo = indexQuadCapacity = 0;
        upload = null;
        failed = false;
    }

    /** Raw calls restore Minecraft's cached state to the values it already knows. */
    private static final class State implements AutoCloseable {
        private final int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        private final int previousVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        private final int previousArrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        private final int depthFunction = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        private final boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        private final boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        private final boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        private final boolean fillOffset = GL11.glIsEnabled(GL11.GL_POLYGON_OFFSET_FILL);
        private final boolean lineOffset = GL11.glIsEnabled(GL11.GL_POLYGON_OFFSET_LINE);
        private final boolean pointOffset = GL11.glIsEnabled(GL11.GL_POLYGON_OFFSET_POINT);
        private final boolean depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        private final int sourceRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        private final int destinationRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        private final int sourceAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        private final int destinationAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        private final int equationRgb = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB);
        private final int equationAlpha = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);
        private final boolean[] colorWrite = new boolean[4];

        private State() {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                ByteBuffer values = stack.malloc(4);
                GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, values);
                for (int i = 0; i < 4; i++) colorWrite[i] = values.get(i) != 0;
            }
        }

        @Override public void close() {
            GL20.glUseProgram(previousProgram);
            GL30.glBindVertexArray(previousVao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, previousArrayBuffer);
            GL14.glBlendFuncSeparate(sourceRgb, destinationRgb, sourceAlpha, destinationAlpha);
            GL20.glBlendEquationSeparate(equationRgb, equationAlpha);
            GL11.glDepthFunc(depthFunction);
            GL11.glDepthMask(depthWrite);
            set(GL11.GL_DEPTH_TEST, depth);
            set(GL11.GL_BLEND, blend);
            set(GL11.GL_CULL_FACE, cull);
            set(GL11.GL_POLYGON_OFFSET_FILL, fillOffset);
            set(GL11.GL_POLYGON_OFFSET_LINE, lineOffset);
            set(GL11.GL_POLYGON_OFFSET_POINT, pointOffset);
            GL11.glColorMask(colorWrite[0], colorWrite[1], colorWrite[2], colorWrite[3]);
        }

        private static void set(int capability, boolean enabled) {
            if (enabled) GL11.glEnable(capability); else GL11.glDisable(capability);
        }
    }
}
