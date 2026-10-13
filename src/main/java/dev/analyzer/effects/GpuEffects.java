package dev.analyzer.effects;

import dev.analyzer.AnalyzerClient;
import dev.analyzer.ui.Palette;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.DimensionEffects;
import net.minecraft.block.enums.CameraSubmersionType;
import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Matrix4f;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;

/** Original, resource-reloadable GLSL effects. Never samples a texture being written. */
public final class GpuEffects {
    private static int grading, backdrop, sky, vao, copyTexture, copyWidth, copyHeight;
    private static boolean failed;
    private static long skyFrames, worldFrames, menuFrames, menuSceneFrames;
    private static final long START = System.nanoTime();
    private GpuEffects() {}

    public static boolean isHealthy() { return !failed; }
    public static String status() { return failed ? "GPU effect failed / reload resources to retry" : grading == 0 ? "Ready / initializes on first frame" : "GPU ready / sky + bloom + menu"; }
    public static long skyFrames() { return skyFrames; }
    public static long worldFrames() { return worldFrames; }
    public static long menuFrames() { return menuFrames; }
    public static long menuSceneFrames() { return menuSceneFrames; }

    /** Called in the actual Overworld sky pass, before terrain and celestial objects. */
    public static void renderSky() {
        var core = AnalyzerClient.CORE;
        var client = MinecraftClient.getInstance();
        if (core == null || !core.isEnabled("sky") || failed || client.world == null
                || client.world.getDimensionEffects().getSkyType() != DimensionEffects.SkyType.NORMAL
                || client.gameRenderer.getCamera().getSubmersionType() != CameraSubmersionType.NONE) return;
        float tick = client.getRenderTickCounter().getTickDelta(false);
        float night = Math.clamp(client.world.getStarBrightness(tick) * 2f, 0f, 1f);
        float visibility = core.getChoice("sky", "day-night").equals("Night") ? night : 1f;
        float intensity = (float) core.getNumber("sky", "intensity") * visibility * (1f - client.world.getRainGradient(tick) * 0.65f);
        if (intensity < 0.001f) return;
        var window = client.getWindow();
        int w = window.getFramebufferWidth(), h = window.getFramebufferHeight();
        if (w < 1 || h < 1) return;
        try (State state = new State(); MemoryStack stack = MemoryStack.stackPush()) {
            ensurePrograms();
            prepare(w, h, sky);
            GL11.glEnable(GL11.GL_BLEND);
            org.lwjgl.opengl.GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL20.glBlendEquationSeparate(GL14_ADD, GL14_ADD);
            var inverseProjection = new Matrix4f(RenderSystem.getProjectionMatrix()).invert();
            var cameraRotation = new Matrix4f().rotation(client.gameRenderer.getCamera().getRotation());
            GL20.glUniformMatrix4fv(GL20.glGetUniformLocation(sky, "InvProjection"), false, inverseProjection.get(stack.mallocFloat(16)));
            GL20.glUniformMatrix4fv(GL20.glGetUniformLocation(sky, "CameraRotation"), false, cameraRotation.get(stack.mallocFloat(16)));
            GL20.glUniform1f(GL20.glGetUniformLocation(sky, "Time"), (System.nanoTime() - START) / 1_000_000_000f * (float) core.getNumber("sky", "speed"));
            GL20.glUniform1f(GL20.glGetUniformLocation(sky, "Intensity"), intensity);
            GL20.glUniform1i(GL20.glGetUniformLocation(sky, "Stars"), core.getBoolean("sky", "stars") ? 1 : 0);
            GL20.glUniform1i(GL20.glGetUniformLocation(sky, "Preset"), switch (core.getChoice("sky", "preset")) { case "Nebula" -> 1; case "Ember" -> 2; default -> 0; });
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
            skyFrames++;
        } catch (Exception ex) { reportFailure(ex); }
    }

    private static final int GL14_ADD = 0x8006;

    public static void renderWorld() {
        var core = AnalyzerClient.CORE;
        if (core == null || !core.isEnabled("shader") || failed) return;
        String preset = core.getChoice("shader", "preset");
        if (preset.equals("Off")) return;
        var client = MinecraftClient.getInstance();
        if (client.world == null) return;
        var framebuffer = client.getFramebuffer();
        int w = framebuffer.textureWidth, h = framebuffer.textureHeight;
        if (w < 1 || h < 1) return;
        try (State state = new State()) {
            ensurePrograms();
            copyScene(framebuffer);
            prepare(w, h, grading);
            GL20.glUniform1i(GL20.glGetUniformLocation(grading, "Scene"), 0);
            GL20.glUniform2f(GL20.glGetUniformLocation(grading, "Resolution"), w, h);
            GL20.glUniform1f(GL20.glGetUniformLocation(grading, "Strength"), (float) core.getNumber("shader", "strength"));
            GL20.glUniform1i(GL20.glGetUniformLocation(grading, "Preset"), switch (preset) { case "Ember" -> 1; case "Frost" -> 2; case "Bloom" -> 3; default -> 0; });
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
            worldFrames++;
        } catch (Exception ex) { reportFailure(ex); }
    }

    public static void drawBackdrop(DrawContext context) {
        var client = MinecraftClient.getInstance();
        var core = AnalyzerClient.CORE;
        boolean glass = client.world != null && core != null && core.isEnabled("menu")
                && core.getChoice("menu", "material").equals("Glass");
        if (failed) { drawFallback(context, glass); return; }
        context.draw();
        var window = client.getWindow();
        int width = window.getFramebufferWidth(), height = window.getFramebufferHeight();
        if (width < 1 || height < 1) return;
        try (State state = new State()) {
            ensurePrograms();
            if (glass) copyScene(client.getFramebuffer());
            prepare(width, height, backdrop);
            float motion = core != null && core.isEnabled("menu") ? (float) core.getNumber("menu", "motion") : 0f;
            GL20.glUniform1i(GL20.glGetUniformLocation(backdrop, "Scene"), 0);
            GL20.glUniform1i(GL20.glGetUniformLocation(backdrop, "UseScene"), glass ? 1 : 0);
            GL20.glUniform2f(GL20.glGetUniformLocation(backdrop, "Resolution"), width, height);
            GL20.glUniform1f(GL20.glGetUniformLocation(backdrop, "Time"), (System.nanoTime() - START) / 1_000_000_000f * motion);
            GL20.glUniform1i(GL20.glGetUniformLocation(backdrop, "Accent"), core != null && core.isEnabled("menu") ? switch (core.getChoice("menu", "accent")) { case "Violet" -> 1; case "Gold" -> 2; default -> 0; } : 0);
            GL20.glUniform1i(GL20.glGetUniformLocation(backdrop, "Theme"), core != null ? switch (core.getChoice("menu", "theme")) { case "Classic" -> 1; case "Minimal" -> 2; case "Neon" -> 3; default -> 0; } : 0);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
            menuFrames++;
            if (glass) menuSceneFrames++;
        } catch (Exception ex) { reportFailure(ex); drawFallback(context, glass); }
    }

    /** Remains readable on unsupported GPUs; no sampling, animation or framebuffer allocation. */
    private static void drawFallback(DrawContext context, boolean glass) {
        var window = MinecraftClient.getInstance().getWindow();
        context.fill(0, 0, window.getScaledWidth(), window.getScaledHeight(),
                glass ? 0x7910171D : Palette.BACKGROUND);
    }

    private static void copyScene(net.minecraft.client.gl.Framebuffer framebuffer) {
        int w = framebuffer.textureWidth, h = framebuffer.textureHeight;
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        if (copyTexture == 0) copyTexture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, copyTexture);
        if (copyWidth != w || copyHeight != h) {
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL13.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL13.GL_CLAMP_TO_EDGE);
            copyWidth = w; copyHeight = h;
        }
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, framebuffer.fbo);
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, w, h);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffer.fbo);
    }

    private static void prepare(int w, int h, int program) {
        GL11.glViewport(0, 0, w, h);
        GL11.glDisable(GL11.GL_DEPTH_TEST); GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_CULL_FACE); GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDepthMask(false); GL11.glColorMask(true, true, true, true);
        GL20.glUseProgram(program); GL30.glBindVertexArray(vao);
    }

    private static void ensurePrograms() throws Exception {
        if (grading != 0) return;
        int newGrading = 0, newBackdrop = 0, newSky = 0;
        try {
            newGrading = program("fullscreen.vsh", "grade.fsh");
            newBackdrop = program("fullscreen.vsh", "aurora.fsh");
            newSky = program("fullscreen.vsh", "sky.fsh");
            vao = GL30.glGenVertexArrays(); grading = newGrading; backdrop = newBackdrop; sky = newSky;
            AnalyzerClient.LOGGER.info("Analyzer GLSL programs compiled and linked: menu, sky and post effects");
        } catch (Exception ex) {
            if (newGrading != 0) GL20.glDeleteProgram(newGrading);
            if (newBackdrop != 0) GL20.glDeleteProgram(newBackdrop);
            if (newSky != 0) GL20.glDeleteProgram(newSky);
            throw ex;
        }
    }

    private static int program(String vertexFile, String fragmentFile) throws Exception {
        int vertex = 0, fragment = 0, program = 0;
        try {
            vertex = compile(GL20.GL_VERTEX_SHADER, read(vertexFile));
            fragment = compile(GL20.GL_FRAGMENT_SHADER, read(fragmentFile));
            program = GL20.glCreateProgram();
            GL20.glAttachShader(program, vertex); GL20.glAttachShader(program, fragment);
            GL20.glLinkProgram(program);
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) throw new IllegalStateException(GL20.glGetProgramInfoLog(program));
            return program;
        } catch (Exception ex) { if (program != 0) GL20.glDeleteProgram(program); throw ex; }
        finally { if (vertex != 0) GL20.glDeleteShader(vertex); if (fragment != 0) GL20.glDeleteShader(fragment); }
    }

    private static String read(String file) throws Exception {
        var resource = MinecraftClient.getInstance().getResourceManager().getResourceOrThrow(Identifier.of("analyzer", "shaders/" + file));
        try (var stream = resource.getInputStream()) { return new String(stream.readAllBytes(), StandardCharsets.UTF_8); }
    }

    private static int compile(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source); GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(shader); GL20.glDeleteShader(shader); throw new IllegalStateException(log);
        }
        return shader;
    }

    private static void reportFailure(Exception ex) {
        failed = true;
        AnalyzerClient.LOGGER.error("Analyzer shader effect disabled after GPU failure; the rest of the client remains available", ex);
    }

    public static void close() {
        if (grading != 0) GL20.glDeleteProgram(grading);
        if (backdrop != 0) GL20.glDeleteProgram(backdrop);
        if (sky != 0) GL20.glDeleteProgram(sky);
        if (vao != 0) GL30.glDeleteVertexArrays(vao);
        if (copyTexture != 0) GL11.glDeleteTextures(copyTexture);
        grading = backdrop = sky = vao = copyTexture = copyWidth = copyHeight = 0;
        failed = false;
    }

    /** Restore raw GL state exactly, keeping Minecraft's cached state valid. */
    private static final class State implements AutoCloseable {
        final int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        final int vertexArray = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        final int readBuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        final int drawBuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        final int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        final int unitZeroTexture;
        final int[] viewport = new int[4];
        final int blendSourceRgb = GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_SRC_RGB), blendDestRgb = GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_DST_RGB);
        final int blendSourceAlpha = GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_SRC_ALPHA), blendDestAlpha = GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_DST_ALPHA);
        final int blendEquationRgb = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB), blendEquationAlpha = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);
        final boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST), blend = GL11.glIsEnabled(GL11.GL_BLEND), cull = GL11.glIsEnabled(GL11.GL_CULL_FACE), scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        final boolean depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        final boolean[] color = new boolean[4];
        State() {
            GL13.glActiveTexture(GL13.GL_TEXTURE0); unitZeroTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer v = stack.mallocInt(4); GL11.glGetIntegerv(GL11.GL_VIEWPORT, v);
                for (int i = 0; i < 4; i++) viewport[i] = v.get(i);
                ByteBuffer c = stack.malloc(4); GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, c);
                for (int i = 0; i < 4; i++) color[i] = c.get(i) != 0;
            }
        }
        public void close() {
            GL20.glUseProgram(program); GL30.glBindVertexArray(vertexArray);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readBuffer); GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawBuffer);
            GL13.glActiveTexture(GL13.GL_TEXTURE0); GL11.glBindTexture(GL11.GL_TEXTURE_2D, unitZeroTexture); GL13.glActiveTexture(activeTexture);
            GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            org.lwjgl.opengl.GL14.glBlendFuncSeparate(blendSourceRgb, blendDestRgb, blendSourceAlpha, blendDestAlpha);
            GL20.glBlendEquationSeparate(blendEquationRgb, blendEquationAlpha);
            set(GL11.GL_DEPTH_TEST, depth); set(GL11.GL_BLEND, blend); set(GL11.GL_CULL_FACE, cull); set(GL11.GL_SCISSOR_TEST, scissor);
            GL11.glDepthMask(depthWrite); GL11.glColorMask(color[0], color[1], color[2], color[3]);
        }
        private static void set(int capability, boolean on) { if (on) GL11.glEnable(capability); else GL11.glDisable(capability); }
    }
}
