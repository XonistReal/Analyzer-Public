package dev.analyzer.render;

import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.fabric.api.renderer.v1.material.BlendMode;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.client.texture.Sprite;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Delegates the original baked geometry/UVs through Fabric's actual chunk quad emitter. */
public final class XrayBakedModel implements BakedModel {
    private static final Map<RenderMaterial, RenderMaterial> FADED = new ConcurrentHashMap<>(), ORES = new ConcurrentHashMap<>();
    private final BakedModel original;
    public XrayBakedModel(BakedModel original) {this.original = original;}
    public static void clearMaterials() {FADED.clear(); ORES.clear();}
    @Override public boolean isVanillaAdapter() {return false;}
    @Override public void emitBlockQuads(QuadEmitter emitter, BlockRenderView world, BlockState state, BlockPos pos, Supplier<Random> random, Predicate<Direction> cullTest) {
        var policy = XrayRenderer.policy();
        if (!policy.enabled()) {original.emitBlockQuads(emitter, world, state, pos, random, cullTest); return;}
        String id = Registries.BLOCK.getId(state.getBlock()).toString(); boolean ore = policy.selected(id);
        if (policy.hidden(id)) {XrayRenderer.recordHidden(); return;}
        XrayRenderer.recordModel(ore);
        emitter.pushTransform(quad -> {
            RenderMaterial material = quad.material();
            quad.material((ore ? ORES : FADED).computeIfAbsent(material, key -> {
                var finder = Renderer.get().materialFinder().copyFrom(key).blendMode(ore ? BlendMode.SOLID : BlendMode.TRANSLUCENT);
                if (ore) finder.emissive(true).ambientOcclusion(TriState.FALSE);
                return finder.find();
            }));
            if (ore) quad.cullFace(null);
            for (int vertex = 0; vertex < 4; vertex++) {
                int color = quad.color(vertex), alpha = ore ? 255 : (color >>> 24) * XrayPolicy.TERRAIN_ALPHA / 255;
                quad.color(vertex, color & 0xFFFFFF | alpha << 24);
                XrayRenderer.recordVertex(ore, alpha);
            }
            return true;
        });
        try {original.emitBlockQuads(emitter, world, state, pos, random, ore ? direction -> false : cullTest);}
        finally {emitter.popTransform();}
    }
    @Override public void emitItemQuads(QuadEmitter emitter, Supplier<Random> random) {original.emitItemQuads(emitter, random);}
    @Override public List<BakedQuad> getQuads(BlockState state, Direction face, Random random) {return original.getQuads(state, face, random);}
    @Override public boolean useAmbientOcclusion() {return original.useAmbientOcclusion();}
    @Override public boolean hasDepth() {return original.hasDepth();}
    @Override public boolean isSideLit() {return original.isSideLit();}
    @Override public Sprite getParticleSprite() {return original.getParticleSprite();}
    @Override public ModelTransformation getTransformation() {return original.getTransformation();}
}
