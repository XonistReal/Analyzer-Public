package dev.analyzer.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.*;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import java.util.ArrayList;
import java.util.List;

/** One clean set of native shader-expanded debug lines. Inputs use absolute world coordinates. */
public final class FinderOverlay {
    private FinderOverlay() { }
    public record Shape(Box box, int color, boolean tracer) { }
    public record Segment(Vec3d from, Vec3d to, int color) { }
    private static final int[] EDGES = {0,1,1,5,5,4,4,0,2,3,3,7,7,6,6,2,0,2,1,3,5,7,4,6};

    public static void draw(WorldRenderContext context, List<Shape> shapes, boolean throughWalls, float width) {
        if (shapes.isEmpty()) return;
        List<Segment> segments = new ArrayList<>();
        Vec3d start = context.camera().getPos().add(Vec3d.fromPolar(context.camera().getPitch(), context.camera().getYaw()).multiply(.5));
        for (Shape shape : shapes) {
            if (segments.size() >= 8192) break;
            Box b = shape.box();
            if (context.frustum() != null && !context.frustum().isVisible(b)) continue;
            for (int i = 0; i < EDGES.length; i += 2) {
                int a=EDGES[i], c=EDGES[i+1];
                segments.add(new Segment(new Vec3d((a&1)==0?b.minX:b.maxX,(a&2)==0?b.minY:b.maxY,(a&4)==0?b.minZ:b.maxZ),
                        new Vec3d((c&1)==0?b.minX:b.maxX,(c&2)==0?b.minY:b.maxY,(c&4)==0?b.minZ:b.maxZ),shape.color()));
            }
            if (shape.tracer()) segments.add(new Segment(start,b.getCenter(),shape.color()));
        }
        drawLines(context, segments, throughWalls, width);
    }

    public static void drawLines(WorldRenderContext context, List<Segment> segments, boolean throughWalls, float width) {
        if (segments.isEmpty()) return;
        State state = new State();
        var matrices = RenderSystem.getModelViewStack();
        matrices.pushMatrix();
        try {
            matrices.mul(context.positionMatrix());
            RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.disableCull();
            RenderSystem.depthMask(false); RenderSystem.depthFunc(GL11.GL_LEQUAL);
            if (throughWalls) RenderSystem.disableDepthTest(); else RenderSystem.enableDepthTest();
            RenderSystem.setShaderColor(1,1,1,1); RenderSystem.setShaderFog(Fog.DUMMY);
            RenderSystem.lineWidth(Math.clamp(Float.isFinite(width)?width:1, .5f, 3f));
            RenderSystem.setShader(ShaderProgramKeys.RENDERTYPE_LINES);
            var buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.LINES, VertexFormats.LINES);
            Vec3d camera = context.camera().getPos();
            int count=0;
            for (Segment segment : segments) {
                if (++count > 8192) break;
                Vec3d from=segment.from().subtract(camera), to=segment.to().subtract(camera), direction=to.subtract(from);
                if (!Double.isFinite(from.lengthSquared()+to.lengthSquared()) || direction.lengthSquared()<1e-12) continue;
                direction=direction.normalize();
                buffer.vertex((float)from.x,(float)from.y,(float)from.z).color(segment.color()).normal((float)direction.x,(float)direction.y,(float)direction.z);
                buffer.vertex((float)to.x,(float)to.y,(float)to.z).color(segment.color()).normal((float)direction.x,(float)direction.y,(float)direction.z);
            }
            var built=buffer.endNullable(); if (built!=null) BufferRenderer.drawWithGlobalProgram(built);
        } finally { matrices.popMatrix(); state.restore(); }
    }
    private static final class State {
        final boolean depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),blend=GL11.glIsEnabled(GL11.GL_BLEND),cull=GL11.glIsEnabled(GL11.GL_CULL_FACE);
        final boolean mask=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        final int function=GL11.glGetInteger(GL11.GL_DEPTH_FUNC),sr=GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),dr=GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),sa=GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),da=GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        final float width=RenderSystem.getShaderLineWidth(); final float[] color=RenderSystem.getShaderColor().clone();
        final Fog fog=RenderSystem.getShaderFog(); final ShaderProgram shader=RenderSystem.getShader();
        void restore() {
            if(depth)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();
            if(blend)RenderSystem.enableBlend();else RenderSystem.disableBlend();
            if(cull)RenderSystem.enableCull();else RenderSystem.disableCull();
            RenderSystem.depthMask(mask);RenderSystem.depthFunc(function);RenderSystem.blendFuncSeparate(sr,dr,sa,da);
            RenderSystem.lineWidth(width);RenderSystem.setShaderColor(color[0],color[1],color[2],color[3]);RenderSystem.setShaderFog(fog);
            if(shader==null)RenderSystem.clearShader();else RenderSystem.setShader(shader);
        }
    }
}
