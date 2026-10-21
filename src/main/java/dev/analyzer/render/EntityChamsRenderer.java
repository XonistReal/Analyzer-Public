package dev.analyzer.render;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.analyzer.AnalyzerClient;
import dev.analyzer.core.AnalyzerCore;
import dev.analyzer.effects.ChamsShader;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Replays only the real, already posed living model into an independent capture consumer.
 * The ordinary renderer, textures, equipment, labels and world depth are left intact.
 * Every captured mesh expires in its owning world-render frame.
 */
public final class EntityChamsRenderer {
    private static final MinecraftClient MC = MinecraftClient.getInstance();
    private static final int MAX_TARGETS = 128;
    private static final int MAX_ENTITY_VERTICES = 32_768;
    private static final int MAX_FRAME_VERTICES = 262_144;
    private static final List<Mesh> MESHES = new ArrayList<>();
    private static final List<CaptureConsumer> POOL = new ArrayList<>();
    private static final Set<LivingEntity> CAPTURED = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Comparator<Mesh> BACK_TO_FRONT = Comparator.comparingDouble(Mesh::distanceSquared).reversed();
    private static AnalyzerCore core;
    private static ChamsPolicy.Snapshot policy;
    private static Object world;
    private static boolean registered, collecting, capturing, captureWarning, shaderUnavailable;
    private static int frameVertices, limited;
    private static long frames;
    private static Counts lastCounts = new Counts(0, 0, 0, 0);

    private EntityChamsRenderer() { }

    /** Counts refer to actual native model vertices submitted in the most recent world frame. */
    public record Counts(int entities, int vertices, int shaderDraws, int limitedTargets) { }
    private record Mesh(CaptureConsumer vertices, ChamsPolicy.Group group, double distanceSquared) { }
    public static Counts counts() { return lastCounts; }
    public static long frames() { return frames; }
    public static boolean capturing() { return capturing; }
    public static boolean accepting() { return collecting; }
    public static String status() {
        if (MC.world == null) return "Join a world to see entity materials";
        if (shaderUnavailable) return "Shader unavailable; native entity models retained";
        return lastCounts.entities + " posed models · " + lastCounts.vertices + " vertices";
    }

    public static void register(AnalyzerCore analyzer) {
        core = analyzer;
        if (registered) return;
        registered = true;
        WorldRenderEvents.START.register(EntityChamsRenderer::beginFrame);
        // LAST follows native entity/equipment buffer flushes and retains the world's depth.
        WorldRenderEvents.LAST.register(EntityChamsRenderer::drawFrame);
        WorldRenderEvents.END.register(context -> endFrame());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (world != client.world || !core.isEnabled("chams")) clear();
        });
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            public Identifier getFabricId() { return Identifier.of("analyzer", "entity_chams"); }
            public void reload(ResourceManager manager) {
                MC.execute(() -> { clear(); ChamsShader.close(); shaderUnavailable = false; captureWarning = false; });
            }
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> { clear(); POOL.clear(); ChamsShader.close(); });
    }

    private static void beginFrame(WorldRenderContext context) {
        endFrame();
        world = MC.world;
        policy = core == null ? null : ChamsPolicy.read(core);
        frameVertices = limited = 0;
        collecting = world != null && policy != null && policy.enabled();
        if (!collecting) lastCounts = new Counts(0, 0, 0, 0);
    }

    /** Called immediately after the native model's setAngles, before its normal material render. */
    public static void capture(LivingEntity entity, LivingEntityRenderState state, EntityModel<?> model, MatrixStack matrices) {
        if (!collecting || capturing || entity == null || policy == null || world != MC.world || entity.getWorld() != world) return;
        boolean firstPerson = MC.options.getPerspective().isFirstPerson();
        // Spectator cameras may focus an entity other than the local player.
        if (firstPerson && entity == MC.getCameraEntity()) return;
        ChamsPolicy.Group group = entity instanceof PlayerEntity ? ChamsPolicy.Group.PLAYER
                : entity instanceof Monster ? ChamsPolicy.Group.HOSTILE
                : entity instanceof MobEntity ? ChamsPolicy.Group.PASSIVE : null;
        double distanceSquared = state.squaredDistanceToCamera;
        if (!policy.accepts(group, distanceSquared, entity.isRemoved(), entity.isAlive(), entity == MC.player,
                firstPerson, state.invisible, entity.isSpectator()) || CAPTURED.contains(entity)) return;
        if (MESHES.size() >= MAX_TARGETS || frameVertices >= MAX_FRAME_VERTICES) { limited++; return; }

        int slot = MESHES.size();
        if (POOL.size() == slot) POOL.add(new CaptureConsumer());
        CaptureConsumer consumer = POOL.get(slot);
        consumer.reset(Math.min(MAX_ENTITY_VERTICES, MAX_FRAME_VERTICES - frameVertices));
        capturing = true;
        matrices.push();
        try {
            // This is Model.render, never EntityRenderer.render: no features, packets, labels,
            // dispatcher calls, recursive capture, live world writes or normal buffer changes.
            model.render(matrices, consumer, LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV, -1);
            CAPTURED.add(entity);
            if (consumer.valid() && consumer.count > 0) {
                MESHES.add(new Mesh(consumer, group, distanceSquared));
                frameVertices += consumer.count;
            } else if (consumer.overflow) limited++;
        } catch (RuntimeException ex) {
            // A failing third-party model must not prevent its ordinary Minecraft render.
            if (!captureWarning) AnalyzerClient.LOGGER.warn("Could not capture an entity's posed model for Analyzer Chams; its normal render is retained", ex);
            captureWarning = true;
        } finally {
            matrices.pop();
            capturing = false;
        }
    }

    private static void drawFrame(WorldRenderContext context) {
        collecting = false;
        if (policy == null || !policy.enabled() || world != MC.world || core == null || !core.isEnabled("chams")) {
            clear(); return;
        }
        int draws = 0;
        MESHES.sort(BACK_TO_FRONT);
        float time = MC.world == null ? 0 : (MC.world.getTime() % 72_000 + context.tickCounter().getTickDelta(false)) / 20f;
        for (Mesh mesh : MESHES) {
            boolean drawn = ChamsShader.draw(mesh.vertices.data, mesh.vertices.count,
                    policy.color(mesh.group, false), policy.color(mesh.group, true), policy.style().label(),
                    policy.hiddenStyle().label(), policy.secondary(), policy.edges(),
                    policy.throughWalls(), 1f, time * policy.animationSpeed(), policy.effectStrength(), policy.wireWidth());
            if (!drawn) { shaderUnavailable = true; break; }
            draws++;
        }
        lastCounts = new Counts(MESHES.size(), frameVertices, draws, limited);
        frames++;
        endFrame();
    }

    private static void endFrame() {
        collecting = capturing = false;
        MESHES.clear(); CAPTURED.clear();
    }

    public static void clear() {
        endFrame(); policy = null; world = null; frameVertices = limited = 0;
        POOL.clear();
        lastCounts = new Counts(0, 0, 0, 0);
    }

    /** Model positions are camera-relative; the native ModelView holds view rotation separately. */
    private static final class CaptureConsumer implements VertexConsumer {
        private float[] data = new float[6 * 1_024];
        private final Matrix4f view = new Matrix4f();
        private final Matrix3f normalView = new Matrix3f();
        private final Vector3f transformed = new Vector3f();
        private int count, limit;
        private boolean overflow, invalid;

        void reset(int maxVertices) {
            count = 0; limit = maxVertices; overflow = invalid = false;
            view.set(RenderSystem.getModelViewMatrix());
            normalView.set(view).normal();
        }
        boolean valid() { return !overflow && !invalid && count % 4 == 0; }

        @Override public VertexConsumer vertex(float x, float y, float z) {
            if (count >= limit) { overflow = true; return this; }
            view.transformPosition(x, y, z, transformed);
            if (!Float.isFinite(transformed.x + transformed.y + transformed.z)) invalid = true;
            int offset = count++ * 6;
            if (offset + 6 > data.length) data = Arrays.copyOf(data, Math.min(limit * 6, data.length * 2));
            data[offset] = transformed.x; data[offset + 1] = transformed.y; data[offset + 2] = transformed.z;
            data[offset + 3] = 0; data[offset + 4] = 1; data[offset + 5] = 0;
            return this;
        }
        @Override public VertexConsumer normal(float x, float y, float z) {
            if (count == 0 || overflow) return this;
            normalView.transform(x, y, z, transformed);
            if (!Float.isFinite(transformed.x + transformed.y + transformed.z)) invalid = true;
            if (transformed.lengthSquared() > 1.0e-10f) transformed.normalize();
            int offset = (count - 1) * 6;
            data[offset + 3] = transformed.x; data[offset + 4] = transformed.y; data[offset + 5] = transformed.z;
            return this;
        }
        @Override public VertexConsumer color(int red, int green, int blue, int alpha) { return this; }
        @Override public VertexConsumer texture(float u, float v) { return this; }
        @Override public VertexConsumer overlay(int u, int v) { return this; }
        @Override public VertexConsumer light(int u, int v) { return this; }
    }
}
