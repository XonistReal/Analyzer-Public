package dev.analyzer.effects;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.analyzer.AnalyzerClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Original, scene-backed liquid glass for native GUI and HUD surfaces. A frame begins
 * after its backdrop and before its first panel, widget or text. Every surface then
 * samples that one immutable copy, never a texture attached to the draw framebuffer.
 * The caller owns the frame scope so a later widget cannot accidentally capture UI.
 */
public final class LiquidGlass {
    private static int program, vao, sceneTexture, sceneWidth, sceneHeight;
    private static int projection, model, rect, padding, radius, scene, resolution, origin, accent, opacity, strength, refraction;
    private static int captureX, captureY, lastSourceFramebuffer = -1;
    private static boolean failed, frameActive, sceneReady, refractionEnabled = true;
    private static long copiedFrames, surfaceFrames, refractionFrames, completedFrames;

    private LiquidGlass() {}

    public static boolean isHealthy() { return !failed; }
    public static boolean isFrameActive() { return frameActive; }
    public static boolean isFrameReady() { return frameActive && sceneReady && !failed; }
    public static long sceneCopies() { return copiedFrames; }
    public static long copiedFrames() { return copiedFrames; }
    public static long surfaceFrames() { return surfaceFrames; }
    public static long surfaces() { return surfaceFrames; }
    public static long refractionFrames() { return refractionFrames; }
    public static long completedFrames() { return completedFrames; }
    public static int lastSourceFramebuffer() { return lastSourceFramebuffer; }
    public static int sourceWidth() { return sceneWidth; }
    public static int sourceHeight() { return sceneHeight; }
    public static String status() {
        return failed ? "Liquid glass unavailable / readable fallback"
                : program == 0 ? "Liquid glass ready" : "Scene refraction, frost and curved highlights active";
    }

    /** Native visual diagnostic only; this is deliberately not a saved user setting. */
    public static void setRefractionEnabled(boolean enabled) { refractionEnabled = enabled; }
    public static boolean isRefractionEnabled() { return refractionEnabled; }

    /**
     * Flush and copy the actual current DRAW target once. Idempotent until endFrame().
     * The current viewport is the captured region, including a nonzero viewport origin.
     */
    public static boolean beginFrame(DrawContext context) {
        if (frameActive) return isFrameReady();
        frameActive = true;
        sceneReady = false;
        if (failed) return false;
        context.draw();
        try (State state = new State()) {
            int width = state.viewport[2], height = state.viewport[3];
            if (width < 1 || height < 1 || state.viewport[0] < 0 || state.viewport[1] < 0 || state.drawSelection == GL11.GL_NONE)
                return false;
            ensureProgram();
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            if (sceneTexture == 0) sceneTexture = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, sceneTexture);
            if (sceneWidth != width || sceneHeight != height) {
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0,
                        GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL13.GL_CLAMP_TO_EDGE);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL13.GL_CLAMP_TO_EDGE);
                sceneWidth = width; sceneHeight = height;
            }
            // A separate READ framebuffer may be bound by a previous native renderer.
            // Read the target actually receiving the scene, using its draw color buffer.
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, state.drawFramebuffer);
            int sourceReadSelection = GL11.glGetInteger(GL11.GL_READ_BUFFER);
            try {
                GL11.glReadBuffer(state.drawSelection);
                GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0,
                        state.viewport[0], state.viewport[1], width, height);
            } finally {
                // GL_READ_BUFFER belongs to its framebuffer. Restore the source before
                // restoring the incoming (potentially different) READ framebuffer.
                GL11.glReadBuffer(sourceReadSelection);
            }
            captureX = state.viewport[0]; captureY = state.viewport[1];
            lastSourceFramebuffer = state.drawFramebuffer;
            copiedFrames++;
            sceneReady = true;
            return true;
        } catch (Exception failure) {
            fail(failure);
            return false;
        }
    }

    /** End the caller's UI frame; the next frame must explicitly capture its backdrop. */
    public static void endFrame() {
        if (frameActive && sceneReady) completedFrames++;
        frameActive = false;
        sceneReady = false;
    }

    /**
     * Draw one rounded glass surface in the DrawContext's current GUI coordinates.
     * Opacity is compositing coverage (0..1), not background blur. Strength (0..2)
     * controls the optical bevel/lens; zero keeps the frost and highlights without warp.
     * Accent contributes a faint reflected color rather than coloring the whole panel.
     * Returns false only when the caller should render a readable fallback.
     */
    public static boolean surface(DrawContext context, float x, float y, float width, float height,
                                  float cornerRadius, int accentColor, float surfaceOpacity, float opticalStrength) {
        if (width <= 0f || height <= 0f || surfaceOpacity <= 0f) return true;
        if (!isFrameReady()) return false;
        if (!Float.isFinite(x + y + width + height + cornerRadius + surfaceOpacity + opticalStrength)) return false;
        context.draw();
        try (State ignored = new State(); MemoryStack stack = MemoryStack.stackPush()) {
            GL20.glUseProgram(program);
            GL30.glBindVertexArray(vao);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, sceneTexture);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDepthMask(false);
            GL11.glColorMask(true, true, true, true);
            GL11.glEnable(GL11.GL_BLEND);
            GL14.glBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL20.glBlendEquationSeparate(GL14.GL_FUNC_ADD, GL14.GL_FUNC_ADD);
            // Leave the live framebuffer, viewport and scissor unchanged. This preserves
            // scrolling clips and transformed HUD placement at every native GUI scale.
            GL20.glUniformMatrix4fv(projection, false, RenderSystem.getProjectionMatrix().get(stack.mallocFloat(16)));
            Matrix4f matrix = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(context.getMatrices().peek().getPositionMatrix());
            GL20.glUniformMatrix4fv(model, false, matrix.get(stack.mallocFloat(16)));
            GL20.glUniform4f(rect, x, y, width, height);
            GL20.glUniform1f(padding, 12f);
            GL20.glUniform1f(radius, Math.clamp(cornerRadius, 0f, Math.min(width, height) * .5f));
            GL20.glUniform1i(scene, 0);
            GL20.glUniform2f(resolution, sceneWidth, sceneHeight);
            GL20.glUniform2f(origin, captureX, captureY);
            GL20.glUniform3f(accent, (accentColor >>> 16 & 255) / 255f,
                    (accentColor >>> 8 & 255) / 255f, (accentColor & 255) / 255f);
            float lens = Math.clamp(opticalStrength, 0f, 2f);
            GL20.glUniform1f(opacity, Math.clamp(surfaceOpacity, 0f, 1f));
            GL20.glUniform1f(strength, lens);
            GL20.glUniform1i(refraction, refractionEnabled ? 1 : 0);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
            surfaceFrames++;
            if (refractionEnabled && lens > 0f) refractionFrames++;
            return true;
        } catch (Exception failure) {
            fail(failure);
            return false;
        }
    }

    private static void fail(Exception failure) {
        failed = true; sceneReady = false;
        AnalyzerClient.LOGGER.error("Analyzer original liquid-glass shader unavailable; using readable surfaces until resource reload", failure);
    }

    private static void ensureProgram() throws Exception {
        if (program != 0) return;
        int vertex = 0, fragment = 0, linked = 0, array = 0;
        try {
            vertex = compile(GL20.GL_VERTEX_SHADER, read("ui_surface.vsh"));
            fragment = compile(GL20.GL_FRAGMENT_SHADER, read("liquid_glass.fsh"));
            linked = GL20.glCreateProgram();
            GL20.glAttachShader(linked, vertex); GL20.glAttachShader(linked, fragment);
            GL30.glBindFragDataLocation(linked, 0, "fragColor");
            GL20.glLinkProgram(linked);
            if (GL20.glGetProgrami(linked, GL20.GL_LINK_STATUS) == GL11.GL_FALSE)
                throw new IllegalStateException(GL20.glGetProgramInfoLog(linked));
            projection = uniform(linked, "ProjMat"); model = uniform(linked, "ModelMat");
            rect = uniform(linked, "Rect"); padding = uniform(linked, "Padding"); radius = uniform(linked, "Radius");
            scene = uniform(linked, "Scene"); resolution = uniform(linked, "Resolution"); origin = uniform(linked, "CaptureOrigin");
            accent = uniform(linked, "Accent"); opacity = uniform(linked, "Opacity");
            strength = uniform(linked, "Strength"); refraction = uniform(linked, "RefractionEnabled");
            array = GL30.glGenVertexArrays();
            program = linked; vao = array;
            AnalyzerClient.LOGGER.info("Analyzer original liquid-glass GLSL compiled: immutable scene copy, lens refraction, frost and curved edge highlights");
        } catch (Exception failure) {
            if (linked != 0) GL20.glDeleteProgram(linked);
            if (array != 0) GL30.glDeleteVertexArrays(array);
            throw failure;
        } finally {
            if (vertex != 0) GL20.glDeleteShader(vertex);
            if (fragment != 0) GL20.glDeleteShader(fragment);
        }
    }

    private static int uniform(int linked, String name) {
        int value = GL20.glGetUniformLocation(linked, name);
        if (value < 0) throw new IllegalStateException("Missing liquid-glass shader uniform: " + name);
        return value;
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

    /** Render-thread resource reload / shutdown; a reload permits retry after failure. */
    public static void close() {
        endFrame();
        if (program != 0) GL20.glDeleteProgram(program);
        if (vao != 0) GL30.glDeleteVertexArrays(vao);
        if (sceneTexture != 0) GL11.glDeleteTextures(sceneTexture);
        program = vao = sceneTexture = sceneWidth = sceneHeight = 0;
        captureX = captureY = 0; lastSourceFramebuffer = -1;
        failed = false;
        refractionEnabled = true;
    }

    /** Raw GL is restored exactly, so RenderSystem's cached bindings remain correct. */
    private static final class State implements AutoCloseable {
        private final int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        private final int previousVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        private final int readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        private final int drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        private final int readSelection = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        private final int drawSelection = GL11.glGetInteger(GL11.GL_DRAW_BUFFER);
        private final int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE), textureZero;
        private final int[] viewport = new int[4], scissorBox = new int[4];
        private final boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST), blend = GL11.glIsEnabled(GL11.GL_BLEND);
        private final boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE), scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        private final boolean depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        private final boolean[] colorWrite = new boolean[4];
        private final int sourceRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), destinationRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        private final int sourceAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), destinationAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        private final int equationRgb = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB), equationAlpha = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);

        private State() {
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            textureZero = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            GL13.glActiveTexture(activeTexture);
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer viewportValues = stack.mallocInt(4), scissorValues = stack.mallocInt(4);
                GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewportValues); GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissorValues);
                ByteBuffer masks = stack.malloc(4); GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, masks);
                for (int i = 0; i < 4; i++) {
                    viewport[i] = viewportValues.get(i); scissorBox[i] = scissorValues.get(i); colorWrite[i] = masks.get(i) != 0;
                }
            }
        }

        @Override public void close() {
            GL20.glUseProgram(previousProgram); GL30.glBindVertexArray(previousVao);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
            GL11.glReadBuffer(readSelection);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
            GL13.glActiveTexture(GL13.GL_TEXTURE0); GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureZero); GL13.glActiveTexture(activeTexture);
            GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            GL11.glScissor(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]);
            GL14.glBlendFuncSeparate(sourceRgb, destinationRgb, sourceAlpha, destinationAlpha);
            GL20.glBlendEquationSeparate(equationRgb, equationAlpha);
            set(GL11.GL_DEPTH_TEST, depth); set(GL11.GL_BLEND, blend); set(GL11.GL_CULL_FACE, cull); set(GL11.GL_SCISSOR_TEST, scissor);
            GL11.glDepthMask(depthWrite); GL11.glColorMask(colorWrite[0], colorWrite[1], colorWrite[2], colorWrite[3]);
        }

        private static void set(int capability, boolean enabled) { if (enabled) GL11.glEnable(capability); else GL11.glDisable(capability); }
    }
}
