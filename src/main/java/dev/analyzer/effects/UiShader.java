package dev.analyzer.effects;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.analyzer.AnalyzerClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Original signed-distance UI renderer. One local quad produces a smooth surface,
 * soft shadow and optional glow. It uses Minecraft's GUI matrices and live scissor;
 * it never allocates an offscreen framebuffer or samples the active render target.
 */
public final class UiShader {
    private static int program, vao;
    private static int projection, model, rect, padding, radius, top, bottom, shadow, shadowBlur, glow, glowBlur;
    private static boolean failed;
    private static long surfaces;

    private UiShader() {}

    public static boolean isHealthy() { return !failed; }
    public static long surfaces() { return surfaces; }
    public static String status() {
        return failed ? "UI shader unavailable / readable fallback" : program == 0 ? "UI shader ready" : "SDF UI shader active";
    }

    /** Returns false only if the caller should draw its readable CPU fallback. */
    public static boolean surface(DrawContext context, float x, float y, float width, float height, float cornerRadius,
                                  int topColor, int bottomColor, int shadowColor, float shadowSoftness,
                                  int glowColor, float glowSoftness) {
        if (width <= 0 || height <= 0) return true;
        if (failed) return false;
        if (!Float.isFinite(x + y + width + height + cornerRadius + shadowSoftness + glowSoftness)) return false;
        // Text and vanilla quads queued earlier must appear below this surface. Restoring
        // raw state afterward keeps RenderSystem's state cache correct for the next batch.
        context.draw();
        try (State state = new State(); MemoryStack stack = MemoryStack.stackPush()) {
            ensureProgram();
            GL20.glUseProgram(program);
            GL30.glBindVertexArray(vao);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDepthMask(false);
            GL11.glColorMask(true, true, true, true);
            GL11.glEnable(GL11.GL_BLEND);
            GL14.glBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL20.glBlendEquationSeparate(GL14.GL_FUNC_ADD, GL14.GL_FUNC_ADD);
            // Scissor, framebuffer, texture bindings and viewport are deliberately untouched.
            GL20.glUniformMatrix4fv(projection, false, RenderSystem.getProjectionMatrix().get(stack.mallocFloat(16)));
            Matrix4f guiMatrix = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(context.getMatrices().peek().getPositionMatrix());
            GL20.glUniformMatrix4fv(model, false, guiMatrix.get(stack.mallocFloat(16)));
            GL20.glUniform4f(rect, x, y, width, height);
            float shadowSize = Math.clamp(shadowSoftness, 0f, 64f);
            float glowSize = Math.clamp(glowSoftness, 0f, 64f);
            float spread = 3f * Math.max((shadowColor >>> 24) > 0 ? shadowSize : 0,
                    (glowColor >>> 24) > 0 ? glowSize : 0) + 5f;
            GL20.glUniform1f(padding, spread);
            GL20.glUniform1f(radius, Math.clamp(cornerRadius, 0f, Math.min(width, height) * .5f));
            color(top, topColor); color(bottom, bottomColor); color(shadow, shadowColor); color(glow, glowColor);
            GL20.glUniform1f(shadowBlur, shadowSize);
            GL20.glUniform1f(glowBlur, glowSize);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
            surfaces++;
            return true;
        } catch (Exception ex) {
            failed = true;
            AnalyzerClient.LOGGER.error("Analyzer SDF UI shader unavailable; using readable rounded surfaces until resource reload", ex);
            return false;
        }
    }

    private static void color(int uniform, int argb) {
        GL20.glUniform4f(uniform, (argb >>> 16 & 255) / 255f, (argb >>> 8 & 255) / 255f,
                (argb & 255) / 255f, (argb >>> 24) / 255f);
    }

    private static void ensureProgram() throws Exception {
        if (program != 0) return;
        int vertex = 0, fragment = 0, linked = 0, array = 0;
        try {
            vertex = compile(GL20.GL_VERTEX_SHADER, read("ui_surface.vsh"));
            fragment = compile(GL20.GL_FRAGMENT_SHADER, read("ui_surface.fsh"));
            linked = GL20.glCreateProgram();
            GL20.glAttachShader(linked, vertex); GL20.glAttachShader(linked, fragment);
            GL30.glBindFragDataLocation(linked, 0, "fragColor");
            GL20.glLinkProgram(linked);
            if (GL20.glGetProgrami(linked, GL20.GL_LINK_STATUS) == GL11.GL_FALSE)
                throw new IllegalStateException(GL20.glGetProgramInfoLog(linked));
            projection = uniform(linked, "ProjMat"); model = uniform(linked, "ModelMat");
            rect = uniform(linked, "Rect"); padding = uniform(linked, "Padding"); radius = uniform(linked, "Radius");
            top = uniform(linked, "TopColor"); bottom = uniform(linked, "BottomColor");
            shadow = uniform(linked, "ShadowColor"); shadowBlur = uniform(linked, "ShadowBlur");
            glow = uniform(linked, "GlowColor"); glowBlur = uniform(linked, "GlowBlur");
            array = GL30.glGenVertexArrays();
            program = linked; vao = array;
            AnalyzerClient.LOGGER.info("Analyzer original SDF UI shader compiled: antialiased surfaces, shadows and pill glow");
        } catch (Exception ex) {
            if (linked != 0) GL20.glDeleteProgram(linked);
            if (array != 0) GL30.glDeleteVertexArrays(array);
            throw ex;
        } finally {
            if (vertex != 0) GL20.glDeleteShader(vertex);
            if (fragment != 0) GL20.glDeleteShader(fragment);
        }
    }

    private static int uniform(int linked, String name) {
        int location = GL20.glGetUniformLocation(linked, name);
        if (location == -1) throw new IllegalStateException("Missing UI shader uniform: " + name);
        return location;
    }

    private static String read(String file) throws Exception {
        var resource = MinecraftClient.getInstance().getResourceManager().getResourceOrThrow(Identifier.of("analyzer", "shaders/" + file));
        try (var stream = resource.getInputStream()) { return new String(stream.readAllBytes(), StandardCharsets.UTF_8); }
    }

    private static int compile(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source); GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(shader); GL20.glDeleteShader(shader);
            throw new IllegalStateException(log);
        }
        return shader;
    }

    /** Render-thread resource reload / shutdown. A reload also retries a failed program. */
    public static void close() {
        if (program != 0) GL20.glDeleteProgram(program);
        if (vao != 0) GL30.glDeleteVertexArrays(vao);
        program = vao = 0;
        failed = false;
    }

    /** Only state modified by this renderer. All other GL state stays live and unchanged. */
    private static final class State implements AutoCloseable {
        private final int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        private final int previousVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        private final boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        private final boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        private final boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        private final boolean depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        private final int sourceRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), destinationRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        private final int sourceAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), destinationAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        private final int equationRgb = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB), equationAlpha = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);
        private final boolean[] colorWrite = new boolean[4];

        private State() {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                ByteBuffer values = stack.malloc(4); GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, values);
                for (int i = 0; i < 4; i++) colorWrite[i] = values.get(i) != 0;
            }
        }

        public void close() {
            GL20.glUseProgram(previousProgram); GL30.glBindVertexArray(previousVao);
            GL14.glBlendFuncSeparate(sourceRgb, destinationRgb, sourceAlpha, destinationAlpha);
            GL20.glBlendEquationSeparate(equationRgb, equationAlpha);
            set(GL11.GL_DEPTH_TEST, depth); set(GL11.GL_BLEND, blend); set(GL11.GL_CULL_FACE, cull);
            GL11.glDepthMask(depthWrite);
            GL11.glColorMask(colorWrite[0], colorWrite[1], colorWrite[2], colorWrite[3]);
        }
        private static void set(int capability, boolean enabled) { if (enabled) GL11.glEnable(capability); else GL11.glDisable(capability); }
    }
}
