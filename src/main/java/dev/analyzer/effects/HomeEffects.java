package dev.analyzer.effects;

import dev.analyzer.AnalyzerClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;

/** Original title-screen art, isolated from the world's post effects and Studio surfaces. */
public final class HomeEffects {
    private static int orbitProgram, sceneProgram, vao, sceneTexture, sceneWidth, sceneHeight;
    private static int orbitResolution, orbitTime, sceneResolution, sceneSampler, sceneBlur, sceneKind;
    private static boolean failed;
    private static long frames, orbitFrames, panoramaFrames, blurFrames, sceneCopies;
    private static float lastTime;
    private static int lastSourceFramebuffer = -1;
    private static final long START = System.nanoTime();

    private HomeEffects() {}

    public static boolean isHealthy() { return !failed; }
    public static String status() {
        return failed ? "Home shader unavailable / readable fallback"
                : orbitProgram == 0 ? "Home shader ready" : "Panorama and Orbit shaders active";
    }
    public static long frames() { return frames; }
    public static long orbitFrames() { return orbitFrames; }
    public static long panoramaFrames() { return panoramaFrames; }
    /** Alias for reporting the landscape presentation without changing the stored background name. */
    public static long landscapeFrames() { return panoramaFrames; }
    public static long blurFrames() { return blurFrames; }
    public static long sceneCopies() { return sceneCopies; }
    public static float lastTime() { return lastTime; }
    public static int lastSourceFramebuffer() { return lastSourceFramebuffer; }

    /**
     * Panorama expects Minecraft's native panorama to have been rendered first. Orbit
     * supplies its own scene. Blur always samples a separate copy of the completed
     * background; a texture is never simultaneously the input and render target.
     */
    public static void draw(DrawContext context, String background, boolean blur, float motion) {
        boolean orbit = "Orbit".equals(background);
        float speed = Float.isFinite(motion) ? Math.clamp(motion, 0f, 4f) : 0f;
        lastTime = speed == 0f ? 0f : (System.nanoTime() - START) / 1_000_000_000f * speed;
        if (failed) { fallback(context, orbit); return; }
        context.draw();
        var client = MinecraftClient.getInstance();
        var framebuffer = client.getFramebuffer();
        int width = framebuffer.textureWidth, height = framebuffer.textureHeight;
        if (width < 1 || height < 1) return;
        try (State state = new State()) {
            // CubeMapRenderer and Minecraft's final compositing can target a different
            // framebuffer than client.getFramebuffer().fbo. Copy the actual destination
            // receiving the background, including the default framebuffer when live.
            int sourceFramebuffer = state.drawBuffer;
            ensurePrograms();
            if (orbit) {
                prepare(width, height, orbitProgram);
                GL20.glUniform2f(orbitResolution, width, height);
                GL20.glUniform1f(orbitTime, lastTime);
                GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
                orbitFrames++;
            }
            if (!orbit || blur) {
                copyScene(width, height, sourceFramebuffer);
                prepare(width, height, sceneProgram);
                GL20.glUniform2f(sceneResolution, width, height);
                GL20.glUniform1i(sceneSampler, 0);
                GL20.glUniform1i(sceneBlur, blur ? 1 : 0);
                GL20.glUniform1i(sceneKind, orbit ? 1 : 0);
                GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
                if (!orbit) panoramaFrames++;
                if (blur) blurFrames++;
            }
            frames++;
        } catch (Exception failure) {
            failed = true;
            AnalyzerClient.LOGGER.error("Analyzer Home shader unavailable; the home navigation remains usable until resource reload", failure);
            fallback(context, orbit);
        }
    }

    private static void fallback(DrawContext context, boolean orbit) {
        var window = MinecraftClient.getInstance().getWindow();
        if (orbit) context.fillGradient(0, 0, window.getScaledWidth(), window.getScaledHeight(), 0xFF07090F, 0xFF15100A);
        else context.fill(0, 0, window.getScaledWidth(), window.getScaledHeight(), 0x600D1115);
    }

    private static void copyScene(int width, int height, int sourceFramebuffer) {
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        if (sceneTexture == 0) sceneTexture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, sceneTexture);
        if (sceneWidth != width || sceneHeight != height) {
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL13.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL13.GL_CLAMP_TO_EDGE);
            sceneWidth = width; sceneHeight = height;
        }
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, sourceFramebuffer);
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height);
        lastSourceFramebuffer = sourceFramebuffer;
        sceneCopies++;
    }

    private static void prepare(int width, int height, int program) {
        GL11.glViewport(0, 0, width, height);
        GL11.glDisable(GL11.GL_DEPTH_TEST); GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_CULL_FACE); GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDepthMask(false); GL11.glColorMask(true, true, true, true);
        GL20.glUseProgram(program); GL30.glBindVertexArray(vao);
    }

    private static void ensurePrograms() throws Exception {
        if (orbitProgram != 0) return;
        int orbit = 0, scene = 0, array = 0;
        try {
            orbit = program("home_orbit.fsh");
            scene = program("home_scene.fsh");
            orbitResolution = uniform(orbit, "Resolution"); orbitTime = uniform(orbit, "Time");
            sceneResolution = uniform(scene, "Resolution"); sceneSampler = uniform(scene, "Scene");
            sceneBlur = uniform(scene, "Blur"); sceneKind = uniform(scene, "Kind");
            array = GL30.glGenVertexArrays();
            orbitProgram = orbit; sceneProgram = scene; vao = array;
            AnalyzerClient.LOGGER.info("Analyzer original Home GLSL compiled: procedural Orbit and separate-copy panorama blur");
        } catch (Exception failure) {
            if (orbit != 0) GL20.glDeleteProgram(orbit);
            if (scene != 0) GL20.glDeleteProgram(scene);
            if (array != 0) GL30.glDeleteVertexArrays(array);
            throw failure;
        }
    }

    private static int uniform(int program, String name) {
        int location = GL20.glGetUniformLocation(program, name);
        if (location == -1) throw new IllegalStateException("Missing Home shader uniform: " + name);
        return location;
    }

    private static int program(String fragmentFile) throws Exception {
        int vertex = 0, fragment = 0, linked = 0;
        try {
            vertex = compile(GL20.GL_VERTEX_SHADER, read("fullscreen.vsh"));
            fragment = compile(GL20.GL_FRAGMENT_SHADER, read(fragmentFile));
            linked = GL20.glCreateProgram();
            GL20.glAttachShader(linked, vertex); GL20.glAttachShader(linked, fragment);
            GL30.glBindFragDataLocation(linked, 0, "fragColor");
            GL20.glLinkProgram(linked);
            if (GL20.glGetProgrami(linked, GL20.GL_LINK_STATUS) == GL11.GL_FALSE)
                throw new IllegalStateException(GL20.glGetProgramInfoLog(linked));
            return linked;
        } catch (Exception failure) {
            if (linked != 0) GL20.glDeleteProgram(linked);
            throw failure;
        } finally {
            if (vertex != 0) GL20.glDeleteShader(vertex);
            if (fragment != 0) GL20.glDeleteShader(fragment);
        }
    }

    private static String read(String file) throws Exception {
        var resource = MinecraftClient.getInstance().getResourceManager().getResourceOrThrow(Identifier.of("analyzer", "shaders/" + file));
        try (var stream = resource.getInputStream()) { return new String(stream.readAllBytes(), StandardCharsets.UTF_8); }
    }

    private static int compile(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source); GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String message = GL20.glGetShaderInfoLog(shader); GL20.glDeleteShader(shader);
            throw new IllegalStateException(message);
        }
        return shader;
    }

    /** Render-thread reload / shutdown. Also permits retry after shader failure. */
    public static void close() {
        if (orbitProgram != 0) GL20.glDeleteProgram(orbitProgram);
        if (sceneProgram != 0) GL20.glDeleteProgram(sceneProgram);
        if (vao != 0) GL30.glDeleteVertexArrays(vao);
        if (sceneTexture != 0) GL11.glDeleteTextures(sceneTexture);
        orbitProgram = sceneProgram = vao = sceneTexture = sceneWidth = sceneHeight = 0;
        failed = false;
    }

    /** Restore actual incoming GL state, keeping Minecraft's cached state consistent. */
    private static final class State implements AutoCloseable {
        private final int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        private final int array = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        private final int readBuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        private final int drawBuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        private final int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        private final int textureZero;
        private final int[] viewport = new int[4];
        private final boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST), blend = GL11.glIsEnabled(GL11.GL_BLEND);
        private final boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE), scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        private final boolean depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        private final boolean[] color = new boolean[4];
        private final int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        private final int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        private final int rgbEquation = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB), alphaEquation = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);

        private State() {
            GL13.glActiveTexture(GL13.GL_TEXTURE0); textureZero = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer values = stack.mallocInt(4); GL11.glGetIntegerv(GL11.GL_VIEWPORT, values);
                ByteBuffer masks = stack.malloc(4); GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, masks);
                for (int i = 0; i < 4; i++) { viewport[i] = values.get(i); color[i] = masks.get(i) != 0; }
            }
        }

        @Override public void close() {
            GL20.glUseProgram(program); GL30.glBindVertexArray(array);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readBuffer); GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawBuffer);
            GL13.glActiveTexture(GL13.GL_TEXTURE0); GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureZero); GL13.glActiveTexture(activeTexture);
            GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            GL14.glBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha); GL20.glBlendEquationSeparate(rgbEquation, alphaEquation);
            set(GL11.GL_DEPTH_TEST, depth); set(GL11.GL_BLEND, blend); set(GL11.GL_CULL_FACE, cull); set(GL11.GL_SCISSOR_TEST, scissor);
            GL11.glDepthMask(depthWrite); GL11.glColorMask(color[0], color[1], color[2], color[3]);
        }
        private static void set(int capability, boolean enabled) { if (enabled) GL11.glEnable(capability); else GL11.glDisable(capability); }
    }
}
