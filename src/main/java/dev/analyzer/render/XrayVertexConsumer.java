package dev.analyzer.render;

import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumer;

/** Preserves the real model's positions, UVs, tint and normals while changing only opacity/light. */
public final class XrayVertexConsumer implements VertexConsumer {
    private final VertexConsumer delegate;
    private final int opacity;
    private final boolean ore;
    public XrayVertexConsumer(VertexConsumer delegate, int opacity, boolean ore) {this.delegate = delegate; this.opacity = opacity; this.ore = ore;}
    public VertexConsumer vertex(float x, float y, float z) {delegate.vertex(x, y, z); return this;}
    public VertexConsumer color(int red, int green, int blue, int alpha) {delegate.color(red, green, blue, alpha * opacity / 255); XrayRenderer.recordVertex(ore, alpha * opacity / 255); return this;}
    public VertexConsumer texture(float u, float v) {delegate.texture(u, v); return this;}
    public VertexConsumer overlay(int u, int v) {delegate.overlay(u, v); return this;}
    public VertexConsumer light(int u, int v) {if (ore) delegate.light(LightmapTextureManager.MAX_LIGHT_COORDINATE); else delegate.light(u, v); return this;}
    public VertexConsumer normal(float x, float y, float z) {delegate.normal(x, y, z); return this;}
}
