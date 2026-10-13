package dev.analyzer.render;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.analyzer.core.AnalyzerCore;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Fog;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Bounded normal-depth plans and accepted local interactions; previews are not server confirmations. */
public final class HvhPreview {
    private static final Map<String, Plan> pending = new HashMap<>();
    private static final List<Accepted> accepted = new ArrayList<>();
    private static final int[] EDGES = {0,1, 0,2, 0,4, 1,3, 1,5, 2,3, 2,6, 3,7, 4,5, 4,6, 5,7, 6,7};
    private static AnalyzerCore core;
    private static Object world;
    private static boolean registered;
    private static long frames;
    private HvhPreview() { }
    public static void register(AnalyzerCore analyzer) {
        core = analyzer;
        if (registered) return;
        registered = true; WorldRenderEvents.LAST.register(HvhPreview::render);
    }
    public static void clear() { pending.clear(); accepted.clear(); world = null; }
    public static long frames() { return frames; }
    private static void ensureWorld() {
        Object current = MinecraftClient.getInstance().world;
        if (world != current) { clear(); world = current; }
    }
    public static void pending(String id, List<BlockPos> positions) {
        ensureWorld();
        pending.put(id, new Plan(positions.stream().limit(32).map(BlockPos::toImmutable).toList(), System.nanoTime()));
    }
    public static void accepted(String id, BlockPos pos) {
        ensureWorld();
        accepted.add(new Accepted(id, pos.toImmutable(), System.nanoTime()));
        if (accepted.size() > 64) accepted.removeFirst();
    }
    private static boolean visible(String id) { return core != null && core.isEnabled(id) && core.getBoolean(id, "render"); }
    private static int color(String id, int alpha) {
        return alpha << 24 | (int) core.getNumber(id, "red") << 16 | (int) core.getNumber(id, "green") << 8 | (int) core.getNumber(id, "blue");
    }
    private static void render(WorldRenderContext context) {
        ensureWorld();
        if (core == null || context.world() == null || MinecraftClient.getInstance().player == null) return;
        long now = System.nanoTime();
        pending.entrySet().removeIf(e -> now - e.getValue().when > 400_000_000L || !visible(e.getKey()));
        accepted.removeIf(e -> now - e.when > 750_000_000L || !visible(e.id));
        List<Mark> marks = new ArrayList<>(); Vec3d camera = context.camera().getPos();
        pending.forEach((id, plan) -> plan.positions.forEach(pos -> { if (Vec3d.ofCenter(pos).squaredDistanceTo(camera) < 256) marks.add(new Mark(new Box(pos).expand(.003).offset(camera.negate()), color(id, 90))); }));
        for (Accepted action : accepted) if (Vec3d.ofCenter(action.pos).squaredDistanceTo(camera) < 256)
            marks.add(new Mark(new Box(action.pos).expand(.01).offset(camera.negate()), color(action.id, 235)));
        if (marks.isEmpty()) return;
        RenderState saved = new RenderState();
        try {
            RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.disableCull();
            RenderSystem.enableDepthTest(); RenderSystem.depthMask(false); RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.setShaderColor(1, 1, 1, 1); RenderSystem.setShaderFog(Fog.DUMMY);
            RenderSystem.setShader(ShaderProgramKeys.RENDERTYPE_LINES); RenderSystem.lineWidth(1.5f);
            BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.LINES, VertexFormats.LINES);
            for (Mark mark : marks) outline(buffer, mark.box, mark.color);
            BufferRenderer.drawWithGlobalProgram(buffer.end()); frames++;
        } finally { saved.restore(); }
    }
    private static void outline(BufferBuilder buffer, Box box, int color) {
        for (int i = 0; i < EDGES.length; i += 2) {
            int a = EDGES[i], b = EDGES[i + 1];
            float ax = (float) ((a & 1) == 0 ? box.minX : box.maxX), bx = (float) ((b & 1) == 0 ? box.minX : box.maxX);
            float ay = (float) ((a & 2) == 0 ? box.minY : box.maxY), by = (float) ((b & 2) == 0 ? box.minY : box.maxY);
            float az = (float) ((a & 4) == 0 ? box.minZ : box.maxZ), bz = (float) ((b & 4) == 0 ? box.minZ : box.maxZ);
            Vec3d normal = new Vec3d(bx - ax, by - ay, bz - az).normalize();
            buffer.vertex(ax, ay, az).color(color).normal((float) normal.x, (float) normal.y, (float) normal.z);
            buffer.vertex(bx, by, bz).color(color).normal((float) normal.x, (float) normal.y, (float) normal.z);
        }
    }
    private record Plan(List<BlockPos> positions, long when) { }
    private record Accepted(String id, BlockPos pos, long when) { }
    private record Mark(Box box, int color) { }
    /** Restore the incoming state so the preview composes with other renderers. */
    private static final class RenderState {
        private final boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST), blend = GL11.glIsEnabled(GL11.GL_BLEND), cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        private final boolean mask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        private final int depthFunction = GL11.glGetInteger(GL11.GL_DEPTH_FUNC), sourceRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), destinationRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        private final int sourceAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), destinationAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        private final float lineWidth = RenderSystem.getShaderLineWidth();
        private final float[] shaderColor = RenderSystem.getShaderColor().clone();
        private final Fog fog = RenderSystem.getShaderFog();
        private final ShaderProgram shader = RenderSystem.getShader();
        void restore() {
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            RenderSystem.depthMask(mask); RenderSystem.depthFunc(depthFunction); RenderSystem.blendFuncSeparate(sourceRgb, destinationRgb, sourceAlpha, destinationAlpha);
            RenderSystem.lineWidth(lineWidth); RenderSystem.setShaderColor(shaderColor[0], shaderColor[1], shaderColor[2], shaderColor[3]);
            RenderSystem.setShaderFog(fog); if (shader == null) RenderSystem.clearShader(); else RenderSystem.setShader(shader);
        }
    }
}
