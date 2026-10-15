package dev.analyzer.render;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.analyzer.automation.ImportedSchematic;
import dev.analyzer.automation.SchematicData;
import dev.analyzer.automation.SchematicStatePolicy;
import dev.analyzer.core.AnalyzerCore;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.*;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.TriState;
import net.minecraft.util.math.*;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.EmptyBlockView;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import java.util.*;
import static net.minecraft.client.render.RenderPhase.*;

/** A bounded read-only blueprint ghost. No real blocks, block entities, packets or world maps are replaced. */
public final class SchematicPreviewRenderer {
    private static final int MAX_DRAWN = 4_096;
    private static final double RANGE_SQUARED = 96 * 96;
    private static final MinecraftClient MC = MinecraftClient.getInstance();
    private static final BufferAllocator ALLOCATOR = new BufferAllocator(256 * 1024);
    private static final VertexConsumerProvider.Immediate CONSUMERS = VertexConsumerProvider.immediate(ALLOCATOR);
    // Native entity/debug layers write depth even when the caller previously disabled
    // depthMask. The ghost must test the real world's depth without ever changing it.
    private static final RenderLayer GHOST = RenderLayer.of("analyzer_schematic_model",
            VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, VertexFormat.DrawMode.QUADS, 65_536, false, true,
            RenderLayer.MultiPhaseParameters.builder().program(ENTITY_TRANSLUCENT_PROGRAM)
                    .texture(new Texture(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE, TriState.FALSE, false))
                    .transparency(TRANSLUCENT_TRANSPARENCY).cull(DISABLE_CULLING).lightmap(ENABLE_LIGHTMAP)
                    .overlay(ENABLE_OVERLAY_COLOR).depthTest(LEQUAL_DEPTH_TEST).writeMaskState(COLOR_MASK)
                    .target(MAIN_TARGET).build(false));
    private static final RenderLayer GHOST_FILL = RenderLayer.of("analyzer_schematic_fill",
            VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS, 16_384, false, true,
            RenderLayer.MultiPhaseParameters.builder().program(POSITION_COLOR_PROGRAM)
                    .transparency(TRANSLUCENT_TRANSPARENCY).cull(DISABLE_CULLING)
                    .depthTest(LEQUAL_DEPTH_TEST).writeMaskState(COLOR_MASK).target(MAIN_TARGET).build(false));
    private static final RenderLayer GHOST_LINES = RenderLayer.of("analyzer_schematic_lines",
            VertexFormats.LINES, VertexFormat.DrawMode.LINES, 16_384,
            RenderLayer.MultiPhaseParameters.builder().program(LINES_PROGRAM).lineWidth(new LineWidth(OptionalDouble.empty()))
                    .layering(VIEW_OFFSET_Z_LAYERING).transparency(TRANSLUCENT_TRANSPARENCY).cull(DISABLE_CULLING)
                    .depthTest(LEQUAL_DEPTH_TEST).writeMaskState(COLOR_MASK).target(MAIN_TARGET).build(false));
    private static AnalyzerCore core;
    private static boolean registered;
    private static Selection selected;
    private static List<Cell> nearby = List.of();
    private static Vec3d sortedFrom = Vec3d.ZERO;
    private static int tick;
    private static int drawn;
    private record Cell(BlockPos relative, BlockState snapshot, BlockState construction) { }
    private record Selection(Object world, ImportedSchematic snapshot, ImportedSchematic construction, BlockPos origin,
                             List<Cell> cells, String detail, boolean reviewed) { }
    private SchematicPreviewRenderer() { }

    public static void register(AnalyzerCore analyzer) {
        core = analyzer;
        if (registered) return;
        registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            tick++;
            if (selected != null && selected.world != client.world) clear();
        });
        WorldRenderEvents.LAST.register(SchematicPreviewRenderer::render);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> { clear(); ALLOCATOR.close(); });
    }
    public static void show(ImportedSchematic snapshot, ImportedSchematic construction, BlockPos origin, String detail, boolean reviewed) {
        List<Cell> cells = new ArrayList<>();
        for (int y = 0; y < snapshot.heightY(); y++) for (int z = 0; z < snapshot.lengthZ(); z++) for (int x = 0; x < snapshot.widthX(); x++) {
            if (!snapshot.inSchematic(x, y, z, null)) continue;
            BlockState state = snapshot.desiredState(x, y, z, null, List.of()); if (state.isAir()) continue;
            BlockState target = construction == null || !construction.inSchematic(x, y, z, null) ? state : construction.desiredState(x, y, z, null, List.of());
            cells.add(new Cell(new BlockPos(x, y, z), state, target));
        }
        selected = new Selection(MC.world, snapshot, construction, origin.toImmutable(), List.copyOf(cells), detail, reviewed);
        nearby = List.of(); sortedFrom = new Vec3d(Double.MAX_VALUE, 0, 0);
    }
    public static void clear() { selected = null; nearby = List.of(); drawn = 0; }
    public static void describe(String detail, boolean reviewed) {
        if (selected != null) selected = new Selection(selected.world, selected.snapshot, selected.construction, selected.origin, selected.cells, detail, reviewed);
    }
    public static boolean available() { return selected != null && selected.world == MC.world; }
    public static String status() {
        Selection view = selected;
        if (view == null || view.world != MC.world) return "No selected schematic ghost";
        return (view.reviewed ? "Reviewed site " : "Unapproved preview anchor ") + view.origin.toShortString() + " · " + view.snapshot.data().name()
                + " · " + view.detail + (view.cells.size() > MAX_DRAWN ? " · nearest " + MAX_DRAWN + " blocks shown" : "");
    }
    public static int drawnBlocks() { return drawn; }
    public static BlockPos origin() { return available() ? selected.origin : null; }

    private static void render(WorldRenderContext context) {
        drawn = 0;
        Selection view = selected;
        if (core == null || view == null || view.world != MC.world || MC.player == null || !core.getBoolean("expedition", "schematic-preview")) return;
        Vec3d camera = context.camera().getPos();
        if (nearby.isEmpty() || tick % 5 == 0 && camera.squaredDistanceTo(sortedFrom) > .25) {
            nearby = view.cells.stream().filter(cell -> Vec3d.ofCenter(view.origin.add(cell.relative)).squaredDistanceTo(camera) <= RANGE_SQUARED)
                    .sorted(Comparator.comparingDouble(cell -> Vec3d.ofCenter(view.origin.add(cell.relative)).squaredDistanceTo(camera)))
                    .limit(MAX_DRAWN).toList();
            sortedFrom = camera;
        }
        if (nearby.isEmpty()) return;
        float alpha = (float)Math.clamp(core.getNumber("expedition", "preview-opacity"), .1, .8);
        boolean models = !core.getChoice("expedition", "preview-style").equals("Outline");
        MatrixStack matrices = new MatrixStack(); Random random = Random.create(42);
        SavedState before = new SavedState();
        try {
            // Draw far to near for translucent native model faces. Models are resolved afresh after resource reloads.
            if (models) for (int i = nearby.size() - 1; i >= 0; i--) {
                Cell cell = nearby.get(i); BlockPos pos = view.origin.add(cell.relative);
                if (!MC.world.isChunkLoaded(pos) || matches(MC.world.getBlockState(pos), cell.construction)) continue;
                matrices.push(); matrices.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
                try {
                    BakedModel model = MC.getBlockRenderManager().getModel(cell.snapshot);
                    VertexConsumer vertices = CONSUMERS.getBuffer(GHOST);
                    boolean quads = false;
                    for (int face = 0; face <= 6; face++) {
                        random.setSeed(cell.snapshot.getRenderingSeed(pos));
                        List<BakedQuad> geometry = model.getQuads(cell.snapshot, face == 6 ? null : Direction.values()[face], random);
                        for (BakedQuad quad : geometry) {
                            int tint = quad.hasTint() ? MC.getBlockColors().getColor(cell.snapshot, MC.world, pos, quad.getTintIndex()) : 0xFFFFFF;
                            if (tint == -1) tint = 0xFFFFFF;
                            vertices.quad(matrices.peek(), quad, ((tint >>> 16) & 255) / 255f, ((tint >>> 8) & 255) / 255f, (tint & 255) / 255f,
                                    alpha, LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV);
                            quads = true;
                        }
                    }
                    // Chests, fluids and other special models retain their actual outline geometry as a tinted ghost.
                    if (!quads) for (Box box : outline(cell.snapshot, pos)) fill(matrices, box, cell.snapshot.getFluidState().isEmpty() ? 0xC99EE8 : 0x62B5F4, alpha * .65f);
                    drawn++;
                } finally { matrices.pop(); }
            }
            CONSUMERS.draw();
            for (Cell cell : nearby) {
                BlockPos pos = view.origin.add(cell.relative); if (!MC.world.isChunkLoaded(pos)) continue;
                BlockState actual = MC.world.getBlockState(pos);
                boolean matching = matches(actual, cell.construction);
                int color = matching ? 0x7FE3B0 : !actual.isAir() && !actual.isReplaceable() && !actual.isOf(cell.construction.getBlock()) ? 0xF27889 : 0xD0A5EC;
                matrices.push(); matrices.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
                try {
                    for (Box box : outline(cell.snapshot, pos)) VertexRendering.drawBox(matrices, CONSUMERS.getBuffer(GHOST_LINES), box.expand(.001),
                            ((color >>> 16) & 255) / 255f, ((color >>> 8) & 255) / 255f, (color & 255) / 255f, matching ? .26f : .75f);
                } finally { matrices.pop(); }
                if (!models) drawn++;
            }
            CONSUMERS.draw();
        } finally { before.restore(); }
    }
    private static boolean matches(BlockState actual, BlockState desired) {
        if (!actual.isOf(desired.getBlock())) return false;
        String block = Registries.BLOCK.getId(desired.getBlock()).toString();
        for (var property : desired.getProperties()) {
            if (SchematicStatePolicy.simulation(block, property.getName())) continue;
            if (!actual.get(property).equals(desired.get(property))) return false;
        }
        return true;
    }
    private static List<Box> outline(BlockState state, BlockPos pos) {
        var shape = state.getOutlineShape(EmptyBlockView.INSTANCE, pos);
        if (!shape.isEmpty()) return shape.getBoundingBoxes();
        float height = state.getFluidState().isEmpty() ? 1 : state.getFluidState().getHeight(EmptyBlockView.INSTANCE, pos);
        return List.of(new Box(0, 0, 0, 1, Math.max(.1f, height), 1));
    }
    private static void fill(MatrixStack matrices, Box b, int color, float alpha) {
        VertexConsumer out = CONSUMERS.getBuffer(GHOST_FILL);
        float[][] points = {{(float)b.minX,(float)b.minY,(float)b.minZ},{(float)b.maxX,(float)b.minY,(float)b.minZ},{(float)b.maxX,(float)b.maxY,(float)b.minZ},{(float)b.minX,(float)b.maxY,(float)b.minZ},
                {(float)b.minX,(float)b.minY,(float)b.maxZ},{(float)b.maxX,(float)b.minY,(float)b.maxZ},{(float)b.maxX,(float)b.maxY,(float)b.maxZ},{(float)b.minX,(float)b.maxY,(float)b.maxZ}};
        for (int index : new int[]{0,3,2,1,4,5,6,7,0,4,7,3,1,2,6,5,3,7,6,2,0,1,5,4}) {
            float[] p = points[index]; out.vertex(matrices.peek(), p[0], p[1], p[2]).color((int)(alpha * 255) << 24 | color);
        }
    }
    private static final class SavedState {
        final boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST), blend = GL11.glIsEnabled(GL11.GL_BLEND), cull = GL11.glIsEnabled(GL11.GL_CULL_FACE), mask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        final int function = GL11.glGetInteger(GL11.GL_DEPTH_FUNC), sr = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), dr = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB), sa = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), da = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        final float[] color = RenderSystem.getShaderColor().clone(); final float width = RenderSystem.getShaderLineWidth();
        final Fog fog = RenderSystem.getShaderFog(); final ShaderProgram shader = RenderSystem.getShader();
        final int[] textures = {RenderSystem.getShaderTexture(0), RenderSystem.getShaderTexture(1), RenderSystem.getShaderTexture(2)};
        void restore() {
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            RenderSystem.depthMask(mask); RenderSystem.depthFunc(function); RenderSystem.blendFuncSeparate(sr, dr, sa, da);
            RenderSystem.lineWidth(width); RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]); RenderSystem.setShaderFog(fog);
            for (int i = 0; i < textures.length; i++) RenderSystem.setShaderTexture(i, textures[i]);
            if (shader == null) RenderSystem.clearShader(); else RenderSystem.setShader(shader);
        }
    }
}
