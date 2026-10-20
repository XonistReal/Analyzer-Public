package dev.analyzer.cosmetic;

import dev.analyzer.core.AnalyzerCore;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EntityPose;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Approved local-player wearables: native posed attachment and ordinary textured cutout depth.
 * Model geometry/poses/textures derived from Wings, copyright (c) 2018 Paul Fulham, MIT.
 * No dedicated GPU objects, player-state mutation or external runtime mod. */
public final class WingsRenderer {
    private static final List<String> STYLES = List.of("Angel", "Dragon", "Bat", "Parrot");
    private static final List<Identifier> TEXTURES = STYLES.stream()
            .map(style -> Identifier.of("analyzer","textures/cosmetics/wings/"+style.toLowerCase(Locale.ROOT)+"_wings.png")).toList();
    private static final long[] FRAMES = new long[4];
    private static final boolean DIAGNOSTICS = FabricLoader.getInstance().isDevelopmentEnvironment() && Boolean.getBoolean("analyzer.wingsSmoke");
    private static Snapshot lastSnapshot;
    public record Snapshot(String style, String animation, WingAnimation.Motion motion, int submittedVertices,
                           double mountY, double mountZ, float bodyPitch, float bodyYaw, float bodyRoll,
                           float bodyPivotY, boolean fullbright) { }
    private final ModelPart root;
    private final List<ModelPart> parts;
    private final List<List<WingMesh.Quad>> feathers;

    public WingsRenderer() {
        ModelData model = new ModelData();
        List<ModelPartData> data = new ArrayList<>(17);
        for (WingRig.Part p : WingRig.parts()) {
            ModelPartBuilder builder = ModelPartBuilder.create();
            if (p.box() != null) {
                WingRig.Box b = p.box();
                builder.uv(b.u(),b.v()).cuboid(b.x(),b.y(),b.z(),b.width(),b.height(),b.depth());
            }
            ModelPartData parent = p.parent() < 0 ? model.getRoot() : data.get(p.parent());
            data.add(parent.addChild(p.name(),builder,ModelTransform.pivot(p.x(),p.y(),p.z())));
        }
        root = TexturedModelData.of(model,64,64).createModel().getChild("root");
        List<ModelPart> nativeParts = new ArrayList<>(17); List<List<WingMesh.Quad>> meshes = new ArrayList<>(17);
        for (WingRig.Part p : WingRig.parts()) {
            nativeParts.add(p.parent() < 0 ? root : nativeParts.get(p.parent()).getChild(p.name()));
            meshes.add(p.extrusion() == null ? List.of() : WingMesh.extrusion(p.extrusion()));
        }
        parts = List.copyOf(nativeParts); feathers = List.copyOf(meshes);
    }

    public static long frames(String style) { int index = STYLES.indexOf(style); return index < 0 ? 0 : FRAMES[index]; }
    public static Snapshot lastSnapshot() { return DIAGNOSTICS ? lastSnapshot : null; }
    public static void resetDiagnostics() { if (DIAGNOSTICS) { java.util.Arrays.fill(FRAMES,0); lastSnapshot = null; } }
    public static boolean eligible(AnalyzerCore core, PlayerEntityRenderState state) {
        var client = MinecraftClient.getInstance(); var player = client.player;
        return core != null && core.isEnabled("wings") && client.world != null && player != null && state.id == player.getId()
                && !state.invisible && !state.spectator && state.pose != EntityPose.SLEEPING
                && (!core.getBoolean("wings","hide-elytra") || !state.equippedChestStack.isOf(Items.ELYTRA));
    }

    /** Caller owns a player feature pass; body transforms are read, never changed. */
    public boolean render(AnalyzerCore core, PlayerEntityModel playerModel, MatrixStack matrices,
                          VertexConsumerProvider vertices, int light, PlayerEntityRenderState state) {
        if (!eligible(core,state)) return false;
        int styleIndex = STYLES.indexOf(core.getChoice("wings","style"));
        if (styleIndex < 0) styleIndex = 0;
        String animation = core.getChoice("wings","animation");
        WingAnimation.Mode mode;
        try { mode = WingAnimation.Mode.valueOf(animation.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) { mode = WingAnimation.Mode.AUTO; }
        var player = MinecraftClient.getInstance().player;
        var pose = WingAnimation.pose(mode,state.age,core.getNumber("wings","speed"),core.getNumber("wings","strength"),
                state.isGliding,state.isSwimming,player != null && !player.isOnGround());
        double mountY = WingAnimation.mountY(core.getNumber("wings","height"));
        double mountZ = WingAnimation.mountZ(core.getNumber("wings","back-offset"),core.getBoolean("wings","armor-clearance"),!state.equippedChestStack.isEmpty());
        boolean fullbright = core.getBoolean("wings","fullbright");
        int actualLight = fullbright ? LightmapTextureManager.MAX_LIGHT_COORDINATE : light;
        for (ModelPart p : parts) p.resetTransform();
        for (int i=0;i<4;i++) {
            apply(parts.get(1+i*2),WingAnimation.mirror(pose.bones().get(i),false));
            apply(parts.get(2+i*2),WingAnimation.mirror(pose.bones().get(i),true));
            apply(parts.get(9+i*2),WingAnimation.mirror(pose.feathers().get(i),false));
            apply(parts.get(10+i*2),WingAnimation.mirror(pose.feathers().get(i),true));
        }
        matrices.push();
        try {
            playerModel.body.rotate(matrices);
            // Match the approved Fit-2 preview: raise root three original model pixels before scaling.
            matrices.translate(0,mountY,mountZ);
            float scale = (float)core.getNumber("wings","scale"); matrices.scale(scale,scale,scale);
            VertexConsumer out = vertices.getBuffer(RenderLayer.getEntityCutout(TEXTURES.get(styleIndex)));
            root.render(matrices,out,actualLight,OverlayTexture.DEFAULT_UV);
            renderFeathers(0,matrices,out,actualLight);
        } finally {
            matrices.pop();
            // Clear pose state after submission as well as before use, including exceptional buffer exits.
            for (ModelPart p : parts) p.resetTransform();
        }
        FRAMES[styleIndex]++;
        if (DIAGNOSTICS) lastSnapshot = new Snapshot(STYLES.get(styleIndex),animation,pose.motion(),1584,mountY,mountZ,
                playerModel.body.pitch,playerModel.body.yaw,playerModel.body.roll,playerModel.body.pivotY,fullbright);
        return true;
    }
    private static void apply(ModelPart part, WingAnimation.Rotation rotation) {
        part.pitch = (float)Math.toRadians(rotation.x()); part.yaw = (float)Math.toRadians(rotation.y()); part.roll = (float)Math.toRadians(rotation.z());
    }
    private void renderFeathers(int index, MatrixStack matrices, VertexConsumer out, int light) {
        matrices.push();
        try {
            parts.get(index).rotate(matrices);
            MatrixStack.Entry entry = matrices.peek();
            for (WingMesh.Quad q : feathers.get(index)) for (WingMesh.Vertex v : q.vertices())
                out.vertex(entry,v.x()/16,v.y()/16,v.z()/16).color(-1).texture(v.u()/64,v.v()/64)
                        .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry,q.nx(),q.ny(),q.nz());
            for (int child=index+1;child<parts.size();child++) if (WingRig.parts().get(child).parent()==index)
                renderFeathers(child,matrices,out,light);
        } finally { matrices.pop(); }
    }
}
