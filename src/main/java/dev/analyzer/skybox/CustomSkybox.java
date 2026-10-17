package dev.analyzer.skybox;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.analyzer.AnalyzerClient;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.DimensionEffects;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Local sky assets, decoded off-thread and uploaded only by the render thread. No remote fetches. */
public final class CustomSkybox {
    private static final ThreadPoolExecutor IO = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(1), task -> { Thread thread = new Thread(task, "Analyzer sky images"); thread.setDaemon(true); return thread; },
            new ThreadPoolExecutor.DiscardOldestPolicy());
    private static int program, vao, texture;
    private static boolean cube;
    private static String key = "", status = "Choose Custom, then Browse custom skyboxes.";
    private static Object world;
    private static CompletableFuture<SkyboxImages.Sky> pending;
    private static long frames, uploads;
    private static long normalFrames, endFrames;
    private CustomSkybox() { }
    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(CustomSkybox::tick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> { close(); IO.shutdownNow(); });
    }
    private static void tick(MinecraftClient client) {
        var core = AnalyzerClient.CORE;
        if (core == null) return;
        var skyType = client.world == null ? null : client.world.getDimensionEffects().getSkyType();
        boolean eligible = skyType == DimensionEffects.SkyType.NORMAL || skyType == DimensionEffects.SkyType.END && core.getBoolean("sky", "end-sky");
        boolean active = core.isEnabled("sky") && !core.getChoice("sky", "source").equals("Procedural") && eligible;
        if (world != client.world) { close(); world = client.world; }
        if (!active) { if (!key.isEmpty() || texture != 0 || pending != null) close(); return; }
        String source = core.getChoice("sky", "source"), path = core.getText("sky", "custom-path");
        String requested = source + "\n" + path;
        if (requested.equals(key)) return;
        close(); world = client.world; key = requested;
        if (source.equals("Custom") && path.isBlank()) { status = "Choose an image or cube folder with Browse custom skyboxes."; return; }
        status = "Loading sky images…";
        var resources = client.getResourceManager();
        pending = CompletableFuture.supplyAsync(() -> {
            try {
                if (source.equals("Custom")) return SkyboxImages.load(Path.of(path));
                var images = new ArrayList<SkyboxImages.Image>();
                // OpenGL +X,-X,+Y,-Y,+Z,-Z. Native CubeMapRenderer: 0 south,1 east,2 north,3 west,4 up,5 down.
                int[] sides = {1, 3, 4, 5, 0, 2};
                for (int side : sides) {
                    Identifier resource = source.equals("Minecraft End") ? Identifier.ofVanilla("textures/environment/end_sky.png")
                            : Identifier.ofVanilla("textures/gui/title/background/panorama_" + side + ".png");
                    try (var stream = resources.getResourceOrThrow(resource).getInputStream()) {
                        var image = SkyboxImages.read(stream);
                        if (image.width() != image.height() || image.width() > SkyboxImages.MAX_CUBE_EDGE)
                            throw new java.io.IOException("Native sky faces must be square and at most 2048 pixels.");
                        images.add(image);
                    }
                }
                int edge = images.getFirst().width();
                if (edge > SkyboxImages.MAX_CUBE_EDGE || images.stream().anyMatch(image -> image.width() != edge || image.height() != edge))
                    throw new java.io.IOException("Minecraft sky resources must contain six matching square faces up to 2048 pixels.");
                return new SkyboxImages.Sky(true, images, source + " · native resources");
            } catch (Exception failure) { throw new java.util.concurrent.CompletionException(failure); }
        }, IO);
    }
    public static String status() { return status; }
    public static long frames() { return frames; }
    public static long uploads() { return uploads; }
    public static long nativeEndFrames() { return endFrames; }
    public static void nativePass(boolean end) {
        long before = frames; render();
        if (frames > before) { if (end) endFrames++; else normalFrames++; }
    }
    public static boolean hasTexture() { return texture != 0; }
    public static void refresh() { close(); tick(MinecraftClient.getInstance()); }

    /** Invoked before terrain from native normal/End sky passes; existing depth/viewport state is restored. */
    public static void render() {
        var client = MinecraftClient.getInstance(); var core = AnalyzerClient.CORE;
        if (core == null || client.world == null || !core.isEnabled("sky") || core.getChoice("sky", "source").equals("Procedural")) return;
        var type = client.world.getDimensionEffects().getSkyType();
        if (type != DimensionEffects.SkyType.NORMAL && (type != DimensionEffects.SkyType.END || !core.getBoolean("sky", "end-sky"))
                || client.gameRenderer.getCamera().getSubmersionType() != CameraSubmersionType.NONE) return;
        // Settings/world can change between the tick and render callback. Never show a stale asset.
        if (!key.equals(core.getChoice("sky", "source") + "\n" + core.getText("sky", "custom-path")) || world != client.world) return;
        try (State ignored = new State(); MemoryStack stack = MemoryStack.stackPush()) {
            if (pending != null && pending.isDone()) {
                var load = pending; pending = null;
                SkyboxImages.Sky sky = load.join(); upload(sky); status = sky.description();
            }
            if (texture == 0) return;
            ensureProgram();
            float delta = client.getRenderTickCounter().getTickDelta(false);
            float visible = core.getChoice("sky", "day-night").equals("Night") ? Math.clamp(client.world.getStarBrightness(delta) * 2f, 0, 1) : 1;
            float rain = core.getBoolean("sky", "weather-fade") ? 1 - client.world.getRainGradient(delta) * .65f : 1;
            float opacity = (float) core.getNumber("sky", "intensity") * visible * rain;
            if (opacity < .001f) return;
            GL11.glDisable(GL11.GL_DEPTH_TEST); GL11.glDisable(GL11.GL_CULL_FACE); GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glDepthMask(false); GL11.glColorMask(true, true, true, true);
            GL11.glEnable(GL11.GL_BLEND); GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL20.glBlendEquationSeparate(GL14.GL_FUNC_ADD, GL14.GL_FUNC_ADD);
            GL20.glUseProgram(program); GL30.glBindVertexArray(vao);
            GL13.glActiveTexture(GL13.GL_TEXTURE0); GL11.glBindTexture(cube ? GL13.GL_TEXTURE_CUBE_MAP : GL11.GL_TEXTURE_2D, texture);
            // Different sampler types cannot alias one texture unit, even when their branch is unused.
            GL20.glUniform1i(GL20.glGetUniformLocation(program, "Panorama"), cube ? 1 : 0);
            GL20.glUniform1i(GL20.glGetUniformLocation(program, "Cube"), cube ? 0 : 1);
            GL20.glUniform1i(GL20.glGetUniformLocation(program, "IsCube"), cube ? 1 : 0);
            GL20.glUniform1f(GL20.glGetUniformLocation(program, "Yaw"), (float) Math.toRadians(core.getNumber("sky", "rotation")));
            GL20.glUniform1f(GL20.glGetUniformLocation(program, "Exposure"), (float) core.getNumber("sky", "exposure"));
            GL20.glUniform1f(GL20.glGetUniformLocation(program, "Opacity"), opacity);
            GL20.glUniformMatrix4fv(GL20.glGetUniformLocation(program, "InvProjection"), false, new Matrix4f(RenderSystem.getProjectionMatrix()).invert().get(stack.mallocFloat(16)));
            GL20.glUniformMatrix4fv(GL20.glGetUniformLocation(program, "CameraRotation"), false, new Matrix4f().rotation(client.gameRenderer.getCamera().getRotation()).get(stack.mallocFloat(16)));
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3); frames++;
        } catch (Exception failure) {
            if (texture != 0) { GL11.glDeleteTextures(texture); texture = 0; }
            status = "Sky not loaded: " + message(failure) + " · Refresh to retry.";
            AnalyzerClient.LOGGER.warn("Custom skybox unavailable; ordinary native sky retained: {}", message(failure));
            client.inGameHud.setOverlayMessage(net.minecraft.text.Text.literal(status), false);
        }
    }
    private static String message(Throwable failure) {
        while (failure.getCause() != null) failure = failure.getCause();
        return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
    }
    private static void upload(SkyboxImages.Sky sky) {
        int target = sky.cube() ? GL13.GL_TEXTURE_CUBE_MAP : GL11.GL_TEXTURE_2D;
        int created = GL11.glGenTextures();
        try {
            GL13.glActiveTexture(GL13.GL_TEXTURE0); GL11.glBindTexture(target, created);
            int max = GL11.glGetInteger(sky.cube() ? GL13.GL_MAX_CUBE_MAP_TEXTURE_SIZE : GL11.GL_MAX_TEXTURE_SIZE);
            if (sky.images().stream().anyMatch(image -> image.width() > max || image.height() > max)) throw new IllegalArgumentException("Sky image exceeds this GPU's texture limit.");
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0); GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0); GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
            for (int i = 0; i < sky.images().size(); i++) {
                var image = sky.images().get(i);
                GL11.glTexImage2D(sky.cube() ? GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X + i : target, 0, GL11.GL_RGBA8,
                        image.width(), image.height(), 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, image.rgba());
                int face = sky.cube() ? GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X + i : target;
                if (GL11.glGetTexLevelParameteri(face, 0, GL11.GL_TEXTURE_WIDTH) != image.width()) throw new IllegalStateException("GPU sky texture allocation failed.");
            }
            GL11.glTexParameteri(target, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(target, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(target, GL11.GL_TEXTURE_WRAP_S, sky.cube() ? GL12.GL_CLAMP_TO_EDGE : GL11.GL_REPEAT);
            GL11.glTexParameteri(target, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            if (sky.cube()) GL11.glTexParameteri(target, GL12.GL_TEXTURE_WRAP_R, GL12.GL_CLAMP_TO_EDGE);
            texture = created; cube = sky.cube(); uploads++;
        } catch (RuntimeException failure) { GL11.glDeleteTextures(created); throw failure; }
    }
    private static void ensureProgram() throws Exception {
        if (program != 0) return;
        int vertex = 0, fragment = 0, created = 0;
        try {
            vertex = compile(GL20.GL_VERTEX_SHADER, "fullscreen.vsh"); fragment = compile(GL20.GL_FRAGMENT_SHADER, "custom_sky.fsh");
            created = GL20.glCreateProgram(); GL20.glAttachShader(created, vertex); GL20.glAttachShader(created, fragment); GL20.glLinkProgram(created);
            if (GL20.glGetProgrami(created, GL20.GL_LINK_STATUS) == 0) throw new IllegalStateException(GL20.glGetProgramInfoLog(created));
            vao = GL30.glGenVertexArrays(); program = created;
        } catch (Exception failure) { if (created != 0) GL20.glDeleteProgram(created); throw failure; }
        finally { if (vertex != 0) GL20.glDeleteShader(vertex); if (fragment != 0) GL20.glDeleteShader(fragment); }
    }
    private static int compile(int type, String name) throws Exception {
        String source;
        try (var stream = MinecraftClient.getInstance().getResourceManager().getResourceOrThrow(Identifier.of("analyzer", "shaders/" + name)).getInputStream()) {
            source = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        int shader = GL20.glCreateShader(type); GL20.glShaderSource(shader, source); GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) != 0) return shader;
        String error = GL20.glGetShaderInfoLog(shader); GL20.glDeleteShader(shader); throw new IllegalStateException(error);
    }
    public static void close() {
        if (pending != null) { pending.cancel(true); pending = null; }
        if (texture != 0) GL11.glDeleteTextures(texture);
        if (program != 0) GL20.glDeleteProgram(program);
        if (vao != 0) GL30.glDeleteVertexArrays(vao);
        texture = program = vao = 0; key = ""; world = null;
        status = "Choose Custom, then Browse custom skyboxes.";
    }
    /** Raw GL restoration leaves Minecraft's cached state unchanged; no viewport/framebuffer/matrix writes. */
    private static final class State implements AutoCloseable {
        final int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM), vao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        final int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE), twoD, cube;
        final int unpack = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
        final int unpackBuffer = GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING), rowLength = GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH), skipRows = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_ROWS), skipPixels = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_PIXELS);
        final int src = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), dst = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB), srcA = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), dstA = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        final int equation = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB), equationA = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);
        final boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST), blend = GL11.glIsEnabled(GL11.GL_BLEND), cull = GL11.glIsEnabled(GL11.GL_CULL_FACE), scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST), write = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        final boolean[] color = new boolean[4];
        State() {
            GL13.glActiveTexture(GL13.GL_TEXTURE0); twoD = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D); cube = GL11.glGetInteger(GL13.GL_TEXTURE_BINDING_CUBE_MAP);
            try (var stack = MemoryStack.stackPush()) { ByteBuffer buffer = stack.malloc(4); GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, buffer); for (int i = 0; i < 4; i++) color[i] = buffer.get(i) != 0; }
        }
        @Override public void close() {
            GL20.glUseProgram(program); GL30.glBindVertexArray(vao); GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, twoD); GL11.glBindTexture(GL13.GL_TEXTURE_CUBE_MAP, cube); GL13.glActiveTexture(active);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, unpack); GL14.glBlendFuncSeparate(src, dst, srcA, dstA); GL20.glBlendEquationSeparate(equation, equationA);
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, unpackBuffer); GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, rowLength); GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, skipRows); GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, skipPixels);
            flag(GL11.GL_DEPTH_TEST, depth); flag(GL11.GL_BLEND, blend); flag(GL11.GL_CULL_FACE, cull); flag(GL11.GL_SCISSOR_TEST, scissor);
            GL11.glDepthMask(write); GL11.glColorMask(color[0], color[1], color[2], color[3]);
        }
        private static void flag(int capability, boolean enabled) { if (enabled) GL11.glEnable(capability); else GL11.glDisable(capability); }
    }
}
