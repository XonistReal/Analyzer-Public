package dev.analyzer.render;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.analyzer.core.AnalyzerCore;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Fog;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.registry.Registries;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/** Native terrain models remain the ore geometry. This bounded pass supplies only wire detail and a soft ore fringe. */
public final class XrayRenderer {
    public record Diagnostics(boolean enabled, String style, int terrainAlpha, long oreModels, long terrainModels,
                              long hiddenModels, long oreVertices, long terrainVertices, long badOreAlpha,
                              long badTerrainAlpha, int wireBlocks, int glowBlocks, int entities, long blockEntities, long reloads) { }
    private record Mark(BlockPos pos, BlockState state, Box box, int color) { }
    private static final MinecraftClient MC = MinecraftClient.getInstance();
    private static final int[] EDGES = {0,1,0,2,0,4,1,3,1,5,2,3,2,6,3,7,4,5,4,6,5,7,6,7};
    private static final AtomicLong ORE_MODELS = new AtomicLong(), TERRAIN_MODELS = new AtomicLong(), HIDDEN_MODELS = new AtomicLong(), ORE_VERTICES = new AtomicLong(), TERRAIN_VERTICES = new AtomicLong(), BAD_ORE_ALPHA = new AtomicLong(), BAD_TERRAIN_ALPHA = new AtomicLong();
    private static final AtomicLong BLOCK_ENTITIES = new AtomicLong();
    private static volatile XrayPolicy.Snapshot policy = new XrayPolicy.Snapshot(false, XrayPolicy.Style.FADE, XrayPolicy.ALL_ORES, true, 24);
    private static AnalyzerCore core;
    private static Object world;
    private static boolean registered, forceReload;
    private static int scanIndex, scanRadius, ticks, lastScan, wiresDrawn, glowDrawn, entitiesDrawn;
    private static long reloads;
    private static BlockPos center;
    private static List<Mark> oreMarks = List.of(), wireMarks = List.of();
    private static final List<Mark> scanningOres = new ArrayList<>(), scanningWires = new ArrayList<>();
    private XrayRenderer() { }
    public static void register(AnalyzerCore analyzer) {
        core = analyzer; if (registered) return; registered = true;
        ModelLoadingPlugin.register(context -> context.modifyBlockModelAfterBake().register((model, modelContext) -> model instanceof XrayBakedModel ? model : new XrayBakedModel(model)));
        ClientTickEvents.END_CLIENT_TICK.register(XrayRenderer::tick);
        WorldRenderEvents.LAST.register(XrayRenderer::render);
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            public Identifier getFabricId() {return Identifier.of("analyzer", "xray-terrain");}
            public void reload(ResourceManager manager) {MC.execute(() -> {XrayBakedModel.clearMaterials(); clearMarks(); forceReload = true;});}
        });
    }
    public static XrayPolicy.Snapshot policy() {return policy;}
    private static String id(BlockState state) {return Registries.BLOCK.getId(state.getBlock()).toString();}
    public static boolean selected(BlockState state) {return policy.enabled() && policy.selected(id(state));}
    public static boolean hidden(BlockState state) {return policy.enabled() && policy.hidden(id(state));}
    public static boolean active() {return policy.enabled();}
    public static boolean fadeTerrain(BlockState state) {return active() && policy.style() == XrayPolicy.Style.FADE && !selected(state);}
    public static VertexConsumer vertices(BlockState state, VertexConsumer original) {
        var current = policy; if (!current.enabled()) return original;
        boolean ore = current.selected(id(state)); if (ore) ORE_MODELS.incrementAndGet(); else TERRAIN_MODELS.incrementAndGet();
        return new XrayVertexConsumer(original, ore ? 255 : current.style() == XrayPolicy.Style.FADE ? XrayPolicy.TERRAIN_ALPHA : 0, ore);
    }
    public static void recordHidden() {HIDDEN_MODELS.incrementAndGet();}
    public static void recordBlockEntity() {BLOCK_ENTITIES.incrementAndGet();}
    static void recordModel(boolean ore) {if (ore) ORE_MODELS.incrementAndGet(); else TERRAIN_MODELS.incrementAndGet();}
    static void recordVertex(boolean ore, int alpha) {
        if (ore) {ORE_VERTICES.incrementAndGet(); if (alpha != 255) BAD_ORE_ALPHA.incrementAndGet();}
        else {TERRAIN_VERTICES.incrementAndGet(); if (alpha != XrayPolicy.TERRAIN_ALPHA) BAD_TERRAIN_ALPHA.incrementAndGet();}
    }
    public static Diagnostics diagnostics() {return new Diagnostics(active(), policy.style().name(), XrayPolicy.TERRAIN_ALPHA, ORE_MODELS.get(), TERRAIN_MODELS.get(), HIDDEN_MODELS.get(), ORE_VERTICES.get(), TERRAIN_VERTICES.get(), BAD_ORE_ALPHA.get(), BAD_TERRAIN_ALPHA.get(), wiresDrawn, glowDrawn, entitiesDrawn, BLOCK_ENTITIES.get(), reloads);}
    public static void resetDiagnostics() {ORE_MODELS.set(0); TERRAIN_MODELS.set(0); HIDDEN_MODELS.set(0); ORE_VERTICES.set(0); TERRAIN_VERTICES.set(0); BAD_ORE_ALPHA.set(0); BAD_TERRAIN_ALPHA.set(0); BLOCK_ENTITIES.set(0); wiresDrawn = glowDrawn = entitiesDrawn = 0;}
    private static void tick(MinecraftClient client) {
        ticks++; if (core == null) return;
        int ores = 0; for (int i = 0; i < XrayPolicy.GROUPS.length; i++) if (core.getBoolean("xray", XrayPolicy.GROUPS[i])) ores |= 1 << i;
        var next = new XrayPolicy.Snapshot(client.world != null && core.isEnabled("xray"), XrayPolicy.Style.parse(core.getChoice("xray", "style")), ores, core.getBoolean("xray", "glow"), (int)core.getNumber("xray", "range"), core.getBoolean("xray", "workstations"), core.getBoolean("xray", "storage"), core.getBoolean("xray", "entities"));
        boolean newWorld = world != client.world, chunksChanged = next.chunkKey() != policy.chunkKey();
        boolean overlayChanged = !next.equals(policy);
        policy = next;
        if (newWorld || overlayChanged) clearMarks();
        world = client.world;
        if ((newWorld || chunksChanged || forceReload) && client.world != null) {client.worldRenderer.reload(); reloads++;}
        forceReload = false;
        if (!next.enabled() || client.player == null) return;
        BlockPos at = client.player.getBlockPos();
        if (center == null || center.getSquaredDistance(at) >= 16 || scanIndex < 0 && ticks - lastScan >= 40) startScan(at);
        if (scanIndex >= 0) scan();
    }
    private static void startScan(BlockPos at) {center = at.toImmutable(); scanRadius = policy.range(); scanIndex = 0; scanningOres.clear(); scanningWires.clear(); lastScan = ticks;}
    private static void clearMarks() {center = null; scanIndex = -1; oreMarks = wireMarks = List.of(); scanningOres.clear(); scanningWires.clear(); wiresDrawn = glowDrawn = 0;}
    private static void scan() {
        int width = scanRadius * 2 + 1, total = width * width * width, end = Math.min(total, scanIndex + 8192);
        for (; scanIndex < end; scanIndex++) {
            int n = scanIndex, x = n % width; n /= width; int y = n % width, z = n / width;
            BlockPos pos = center.add(x - scanRadius, y - scanRadius, z - scanRadius);
            if (pos.getSquaredDistance(center) > scanRadius * scanRadius || !MC.world.isInBuildLimit(pos) || !MC.world.isChunkLoaded(pos)) continue;
            BlockState state = MC.world.getBlockState(pos); if (state.isAir()) continue;
            boolean ore = policy.oreSelected(id(state));
            if (!ore && selected(state)) continue;
            if (!ore && (policy.style() != XrayPolicy.Style.OUTLINES || scanningWires.size() >= 4096 || !surface(pos))) continue;
            if (ore && scanningOres.size() >= 4096) continue;
            var shape = state.getOutlineShape(MC.world, pos); if (shape.isEmpty()) continue;
            Box box = shape.getBoundingBox().offset(pos); int color = ore ? color(state) : 0x4AADB6C0;
            (ore ? scanningOres : scanningWires).add(new Mark(pos, state, box, color));
        }
        if (scanIndex == total) {oreMarks = List.copyOf(scanningOres); wireMarks = List.copyOf(scanningWires); scanIndex = -1;}
    }
    private static boolean surface(BlockPos pos) {for (Direction side : Direction.values()) {BlockPos near = pos.offset(side); if (MC.world.isChunkLoaded(near) && (MC.world.getBlockState(near).isAir() || selected(MC.world.getBlockState(near)))) return true;} return false;}
    private static int color(BlockState state) {return switch (XrayPolicy.oreGroup(id(state))) {case 0 -> 0xFF65F4ED; case 1 -> 0xFF54ED91; case 2 -> 0xFFFFD46B; case 3 -> 0xFFE3BA99; case 4 -> 0xFFDC996B; case 5 -> 0xFF6599FA; case 6 -> 0xFFFA6C75; case 7 -> 0xFFA0A5AF; case 8 -> 0xFFF1DCDA; default -> 0xFFB89081;};}
    private static void render(WorldRenderContext context) {
        wiresDrawn = glowDrawn = entitiesDrawn = 0;
        if (!active() || MC.world == null || MC.player == null) return;
        boolean wires = policy.style() == XrayPolicy.Style.OUTLINES, glow = policy.style() == XrayPolicy.Style.FADE && policy.glow();
        if (!wires && !glow && !policy.entities()) return;
        SavedState saved = new SavedState();
        try {
            RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.disableCull(); RenderSystem.disableDepthTest(); RenderSystem.depthMask(false);
            RenderSystem.setShaderColor(1, 1, 1, 1); RenderSystem.setShaderFog(Fog.DUMMY); RenderSystem.setShader(ShaderProgramKeys.RENDERTYPE_LINES);
            if (wires && !wireMarks.isEmpty()) {RenderSystem.lineWidth(.8f); wiresDrawn = draw(wireMarks, context.camera().getPos(), false, .65f);}
            if (glow && !oreMarks.isEmpty()) {RenderSystem.lineWidth(4); glowDrawn = draw(oreMarks, context.camera().getPos(), true, .07f); RenderSystem.lineWidth(1.2f); draw(oreMarks, context.camera().getPos(), true, .24f);}
            if (policy.entities()) {RenderSystem.lineWidth(1.2f); drawEntities(context.camera().getPos());}
        } finally {saved.restore();}
    }
    private static void drawEntities(Vec3d camera) {
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.LINES, VertexFormats.LINES);
        for (var entity : MC.world.getEntities()) {
            if (entity == MC.player || entity.isRemoved() || entity.isSpectator() || MC.player.squaredDistanceTo(entity) > policy.range() * policy.range()) continue;
            emitBox(buffer, entity.getBoundingBox().expand(.01).offset(camera.negate()), 0xB376E9E2);
            if (++entitiesDrawn >= 256) break;
        }
        var built = buffer.endNullable(); if (built != null) BufferRenderer.drawWithGlobalProgram(built);
    }
    private static void emitBox(BufferBuilder buffer, Box box, int color) {
        for (int e = 0; e < EDGES.length; e += 2) {
            int a = EDGES[e], b = EDGES[e + 1]; float ax = (float)((a & 1) == 0 ? box.minX : box.maxX), ay = (float)((a & 2) == 0 ? box.minY : box.maxY), az = (float)((a & 4) == 0 ? box.minZ : box.maxZ);
            float bx = (float)((b & 1) == 0 ? box.minX : box.maxX), by = (float)((b & 2) == 0 ? box.minY : box.maxY), bz = (float)((b & 4) == 0 ? box.minZ : box.maxZ); Vec3d normal = new Vec3d(bx - ax, by - ay, bz - az).normalize();
            buffer.vertex(ax, ay, az).color(color).normal((float)normal.x, (float)normal.y, (float)normal.z); buffer.vertex(bx, by, bz).color(color).normal((float)normal.x, (float)normal.y, (float)normal.z);
        }
    }
    private static int draw(List<Mark> marks, Vec3d camera, boolean ore, float opacity) {
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.LINES, VertexFormats.LINES); int count = 0;
        for (Mark mark : marks) {
            if (MC.world.getBlockState(mark.pos) != mark.state || selected(mark.state) != ore) continue;
            Box box = mark.box.expand(ore ? .003 : -.001).offset(camera.negate()); int alpha = (int)((mark.color >>> 24) * opacity), color = alpha << 24 | mark.color & 0xFFFFFF;
            for (int e = 0; e < EDGES.length; e += 2) {
                int a = EDGES[e], b = EDGES[e + 1]; float ax = (float)((a & 1) == 0 ? box.minX : box.maxX), ay = (float)((a & 2) == 0 ? box.minY : box.maxY), az = (float)((a & 4) == 0 ? box.minZ : box.maxZ);
                float bx = (float)((b & 1) == 0 ? box.minX : box.maxX), by = (float)((b & 2) == 0 ? box.minY : box.maxY), bz = (float)((b & 4) == 0 ? box.minZ : box.maxZ); Vec3d normal = new Vec3d(bx - ax, by - ay, bz - az).normalize();
                buffer.vertex(ax, ay, az).color(color).normal((float)normal.x, (float)normal.y, (float)normal.z); buffer.vertex(bx, by, bz).color(color).normal((float)normal.x, (float)normal.y, (float)normal.z);
            }
            count++;
        }
        var built = buffer.endNullable(); if (built != null) BufferRenderer.drawWithGlobalProgram(built); return count;
    }
    private static final class SavedState {
        final boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST), blend = GL11.glIsEnabled(GL11.GL_BLEND), cull = GL11.glIsEnabled(GL11.GL_CULL_FACE), mask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        final int depthFunction = GL11.glGetInteger(GL11.GL_DEPTH_FUNC), sourceRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), destinationRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB), sourceAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), destinationAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        final float line = RenderSystem.getShaderLineWidth(); final float[] color = RenderSystem.getShaderColor().clone(); final Fog fog = RenderSystem.getShaderFog(); final ShaderProgram shader = RenderSystem.getShader();
        void restore() {if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest(); if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend(); if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull(); RenderSystem.depthMask(mask); RenderSystem.depthFunc(depthFunction); RenderSystem.blendFuncSeparate(sourceRgb, destinationRgb, sourceAlpha, destinationAlpha); RenderSystem.lineWidth(line); RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]); RenderSystem.setShaderFog(fog); if (shader == null) RenderSystem.clearShader(); else RenderSystem.setShader(shader);}
    }
}
