package dev.analyzer.cosmetic;

import dev.analyzer.core.AnalyzerCore;
import dev.analyzer.ui.Palette;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Original local straw hat on the posed head. Straw and Lacquered use the original woven texture
 * with native entity lighting; the glass styles are unlit translucent color like the other glass
 * cosmetics. Only render state is read; no player or entity state changes.
 */
public final class StrawHatRenderer {
    private static final Identifier TEXTURE = Identifier.of("analyzer", "textures/cosmetics/hats/straw_hat.png");
    private static final int LACQUER = 0xFF8E4B2D;
    private record Key(double size, double height, double lift, boolean helmet, boolean strap) { }
    private Key cachedKey;
    private List<StrawHatMesh.Quad> cached = List.of();

    /** @param tint the cosmetic color for the glass styles (alpha ignored) */
    public boolean render(AnalyzerCore core, PlayerEntityModel model, MatrixStack matrices, VertexConsumerProvider vertices,
                          int light, PlayerEntityRenderState state, int tint) {
        boolean helmet = !state.equippedHeadStack.isEmpty();
        if (helmet && core.getBoolean("straw-hat", "hide-helmet")) return false;
        String style = core.getChoice("straw-hat", "style");
        boolean glass = style.equals("Glass") || style.equals("Rainbow glass");
        Key key = new Key(core.getNumber("straw-hat", "size"), core.getNumber("straw-hat", "height"),
                core.getNumber("straw-hat", "lift"), helmet, core.getBoolean("straw-hat", "strap") && !glass);
        if (!key.equals(cachedKey)) {
            cached = StrawHatMesh.build(StrawHatMesh.shape(key.size(), key.height(), key.lift(), key.helmet()), key.strap());
            cachedKey = key;
        }
        matrices.push();
        try {
            model.head.rotate(matrices);
            // Tilt about the top of the head so the brim dips forward or back without lifting off.
            matrices.translate(0, -0.5, 0);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float) core.getNumber("straw-hat", "tilt")));
            matrices.translate(0, 0.5, 0);
            MatrixStack.Entry entry = matrices.peek();
            if (!glass) {
                int color = style.equals("Lacquered") ? LACQUER : 0xFFFFFFFF;
                VertexConsumer out = vertices.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE));
                for (StrawHatMesh.Quad quad : cached) for (StrawHatMesh.Vertex v : quad.vertices())
                    out.vertex(entry, v.x() / 16f, v.y() / 16f, v.z() / 16f).color(color).texture(v.u(), v.v())
                            .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, v.nx(), v.ny(), v.nz());
            } else {
                int alpha = (int) Math.round(Math.clamp(core.getNumber("straw-hat", "opacity"), 0.15, 1) * 255);
                VertexConsumer out = vertices.getBuffer(RenderLayer.getDebugQuads());
                Matrix4f matrix = entry.getPositionMatrix();
                for (StrawHatMesh.Quad quad : cached) {
                    for (StrawHatMesh.Vertex v : quad.vertices()) {
                        // Brighter near the apex, clearer toward the brim, with a solid edge at the rim.
                        double t = switch (quad.part()) { case OUTER -> v.v() * 2; case INNER -> (v.v() - 0.5) * 2; default -> 1; };
                        int face = Palette.mix(Palette.mix(tint | 0xFF000000, 0xFFFFFFFF, 0.35 * (1 - t)), 0xFF000000,
                                quad.part() == StrawHatMesh.Part.INNER ? 0.35 : 0);
                        int a = quad.part() == StrawHatMesh.Part.RIM ? Math.min(255, alpha + 60) : (int) (alpha * (1 - 0.35 * t));
                        out.vertex(matrix, v.x() / 16f, v.y() / 16f, v.z() / 16f).color(Palette.alpha(face, a));
                    }
                }
            }
        } finally { matrices.pop(); }
        return true;
    }
}
