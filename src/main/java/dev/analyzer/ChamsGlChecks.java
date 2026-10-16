package dev.analyzer;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.analyzer.effects.ChamsShader;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Live-driver checks used only by the explicitly selected, isolated chams fixture. */
final class ChamsGlChecks {
    private static final List<String> DETAILS = new ArrayList<>();
    private static int assertions;
    private ChamsGlChecks() { }

    static List<String> details() { return List.copyOf(DETAILS); }

    /** Does not touch framebuffer pixels: every test draw has a zero-area scissor. */
    static int run() {
        if (!FabricLoader.getInstance().isDevelopmentEnvironment()
                || !Boolean.getBoolean("analyzer.smoke") || !Boolean.getBoolean("analyzer.chamsSmoke"))
            throw new IllegalStateException("Chams GL checks require the isolated development chams fixture");
        RenderSystem.assertOnRenderThread();
        assertions = 0;
        DETAILS.clear();
        require(GL11.glGetError() == GL11.GL_NO_ERROR, "Fixture enters with no pending GL error");
        Snapshot baseline = new Snapshot();
        int program = 0, vao = 0, vertexBuffer = 0, elementBuffer = 0;
        try {
            program = testProgram();
            vao = GL30.glGenVertexArrays();
            vertexBuffer = GL15.glGenBuffers();
            elementBuffer = GL15.glGenBuffers();
            GL20.glUseProgram(program);
            GL30.glBindVertexArray(vao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vertexBuffer);
            GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, elementBuffer);
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(0, 0, 0, 0);
            float[] quad = {
                    -0.5f, -0.5f, -2f, 0f, 0f, 1f,
                     0.5f, -0.5f, -2f, 0f, 0f, 1f,
                     0.5f,  0.5f, -2f, 0f, 0f, 1f,
                    -0.5f,  0.5f, -2f, 0f, 0f, 1f
            };
            String[] styles = { "Flat", "Metallic", "Glass", "Glow", "Wireframe" };
            for (int pattern = 0; pattern < 2; pattern++) {
                configure(pattern);
                for (String style : styles) {
                    Snapshot expected = new Snapshot();
                    long draws = ChamsShader.drawCalls(), passes = ChamsShader.passCount();
                    require(ChamsShader.draw(quad, 4, 0xdde636ee, 0x772adbd5, style, true, 0.85f, 23f, 1.3f, pattern == 0 ? 0.5f : 3f),
                            style + " compiles and submits both native depth passes");
                    require(ChamsShader.drawCalls() == draws + 1, style + " records one mesh submission");
                    require(ChamsShader.passCount() == passes + 2, style + " records visible and hidden passes");
                    expected.assertSame(new Snapshot(), style + " / state pattern " + pattern);
                    require(GL11.glGetError() == GL11.GL_NO_ERROR, style + " draw produces no GL error");
                }
            }
            DETAILS.add("All five GLSL materials compile/draw with two opposite incoming GL-state patterns; every modified state is restored.");

            Snapshot expected = new Snapshot();
            long draws = ChamsShader.drawCalls(), passes = ChamsShader.passCount();
            require(ChamsShader.draw(quad, 4, 0xff77eeff, 0xffee7722, "Flat", false, 1f, 0f),
                    "Visible-only material submits normally");
            require(ChamsShader.passCount() == passes + 1, "Through-walls off omits the occluded pass");
            require(ChamsShader.drawCalls() == draws + 1, "Visible-only pass keeps normal mesh accounting");
            expected.assertSame(new Snapshot(), "Visible-only pass");
            draws = ChamsShader.drawCalls(); passes = ChamsShader.passCount();
            require(ChamsShader.draw(quad, 4, 0x0077eeff, 0x00ee7722, "Glow", true, 1f, 0f), "Zero-alpha colors are valid");
            require(ChamsShader.passCount() == passes, "Zero-alpha pass colors submit no draw elements");
            require(ChamsShader.drawCalls() == draws + 1, "Zero-alpha mesh remains a handled submission");
            expected.assertSame(new Snapshot(), "Zero-alpha passes");

            invalid(quad, -4, 1f, 0f, 1f, 1f, "Negative vertex count");
            invalid(quad, 3, 1f, 0f, 1f, 1f, "Incomplete native quad");
            invalid(null, 4, 1f, 0f, 1f, 1f, "Missing vertex storage");
            invalid(new float[12], 4, 1f, 0f, 1f, 1f, "Short vertex storage");
            invalid(quad, 262_148, 1f, 0f, 1f, 1f, "Bounded vertex limit");
            float[] nonfinite = quad.clone(); nonfinite[0] = Float.NaN;
            invalid(nonfinite, 4, 1f, 0f, 1f, 1f, "Nonfinite vertex");
            nonfinite[0] = Float.POSITIVE_INFINITY;
            invalid(nonfinite, 4, 1f, 0f, 1f, 1f, "Infinite vertex");
            invalid(quad, 4, Float.NaN, 0f, 1f, 1f, "Nonfinite opacity");
            invalid(quad, 4, 1f, Float.NaN, 1f, 1f, "Nonfinite time");
            invalid(quad, 4, 1f, 0f, Float.NaN, 1f, "Nonfinite strength");
            invalid(quad, 4, 1f, 0f, 1f, Float.NaN, "Nonfinite line width");
            expected = new Snapshot(); draws = ChamsShader.drawCalls(); passes = ChamsShader.passCount();
            require(ChamsShader.draw(null, 0, 0xffffffff, 0xffffffff, "Flat", true, 1f, 0f), "Empty mesh is a handled no-op");
            require(ChamsShader.draw(quad, 4, 0xffffffff, 0xffffffff, "Flat", true, 0f, 0f), "Zero-opacity mesh is a handled no-op");
            require(ChamsShader.drawCalls() == draws && ChamsShader.passCount() == passes, "No-op meshes submit no GL work");
            expected.assertSame(new Snapshot(), "No-op meshes");
            DETAILS.add("Invalid counts/storage/nonfinite attributes and uniforms are rejected before GL mutation; empty/zero-alpha paths remain harmless.");

            expected = new Snapshot();
            ChamsShader.close();
            require(ChamsShader.isHealthy(), "Closing resources clears shader failure state");
            expected.assertSame(new Snapshot(), "Resource close with another program/VAO bound");
            require(ChamsShader.draw(quad, 4, 0xff77eeff, 0xffee7722, "Metallic", true, 1f, 0f),
                    "A fresh native shader program/VBO/EBO compiles after close");
            expected.assertSame(new Snapshot(), "Fresh shader recompile");
            require(GL11.glGetError() == GL11.GL_NO_ERROR, "Reloaded material draws with no GL error");
            DETAILS.add("Render-thread close and lazy recompile succeed while preserving a separately bound program, VAO and buffers.");
        } finally {
            baseline.restore();
            if (elementBuffer != 0) GL15.glDeleteBuffers(elementBuffer);
            if (vertexBuffer != 0) GL15.glDeleteBuffers(vertexBuffer);
            if (vao != 0) GL30.glDeleteVertexArrays(vao);
            if (program != 0) GL20.glDeleteProgram(program);
        }
        baseline.assertSame(new Snapshot(), "Whole development fixture");
        require(GL11.glGetError() == GL11.GL_NO_ERROR, "Whole development fixture restores GL without error");
        AnalyzerClient.LOGGER.info("ANALYZER_CHAMS_GL_CHECKS_COMPLETE assertions={} details={}", assertions, DETAILS);
        return assertions;
    }

    private static void invalid(float[] vertices, int count, float opacity, float time, float strength, float width, String label) {
        Snapshot expected = new Snapshot();
        long draws = ChamsShader.drawCalls(), passes = ChamsShader.passCount();
        require(!ChamsShader.draw(vertices, count, 0xffffffff, 0xffffffff, "Flat", true, opacity, time, strength, width), label + " rejected");
        require(ChamsShader.drawCalls() == draws && ChamsShader.passCount() == passes, label + " creates no submission");
        expected.assertSame(new Snapshot(), label);
    }

    private static void configure(int pattern) {
        boolean first = pattern == 0;
        set(GL11.GL_DEPTH_TEST, !first);
        set(GL11.GL_CULL_FACE, first);
        set(GL11.GL_BLEND, !first);
        set(GL11.GL_POLYGON_OFFSET_FILL, first);
        set(GL11.GL_POLYGON_OFFSET_LINE, !first);
        set(GL11.GL_POLYGON_OFFSET_POINT, first);
        GL11.glDepthMask(first);
        GL11.glDepthFunc(first ? GL11.GL_GEQUAL : GL11.GL_LESS);
        GL11.glColorMask(first, !first, true, false);
        GL14.glBlendFuncSeparate(GL11.GL_DST_COLOR, GL11.GL_ONE_MINUS_DST_COLOR, GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL20.glBlendEquationSeparate(GL14.GL_FUNC_REVERSE_SUBTRACT, GL14.GL_MAX);
        // Core/forward-compatible contexts may permit only width 1 even when the
        // aliased range advertises more. Production wireframe uses face fragments.
        GL11.glLineWidth(1f);
    }

    private static int testProgram() {
        int vertex = 0, fragment = 0, program = 0;
        try {
            vertex = compile(GL20.GL_VERTEX_SHADER, "#version 150\nvoid main(){gl_Position=vec4(2.0,2.0,2.0,1.0);}");
            fragment = compile(GL20.GL_FRAGMENT_SHADER, "#version 150\nout vec4 fragColor;void main(){fragColor=vec4(1.0);}");
            program = GL20.glCreateProgram();
            GL20.glAttachShader(program, vertex); GL20.glAttachShader(program, fragment);
            GL30.glBindFragDataLocation(program, 0, "fragColor");
            GL20.glLinkProgram(program);
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE)
                throw new IllegalStateException("GL fixture program link failed: " + GL20.glGetProgramInfoLog(program));
            return program;
        } catch (RuntimeException failure) {
            if (program != 0) GL20.glDeleteProgram(program);
            throw failure;
        } finally {
            if (vertex != 0) GL20.glDeleteShader(vertex);
            if (fragment != 0) GL20.glDeleteShader(fragment);
        }
    }

    private static int compile(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source); GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(shader); GL20.glDeleteShader(shader);
            throw new IllegalStateException("GL fixture shader compile failed: " + log);
        }
        return shader;
    }

    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException("Chams GL assertion failed: " + message);
        assertions++;
    }

    private static void set(int capability, boolean value) {
        if (value) GL11.glEnable(capability); else GL11.glDisable(capability);
    }

    private static final class Snapshot {
        private final int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        private final int vao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        private final int array = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        private final int elements = GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING);
        private final int depthFunction = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        private final boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST), cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        private final boolean blend = GL11.glIsEnabled(GL11.GL_BLEND), depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        private final boolean fillOffset = GL11.glIsEnabled(GL11.GL_POLYGON_OFFSET_FILL);
        private final boolean lineOffset = GL11.glIsEnabled(GL11.GL_POLYGON_OFFSET_LINE);
        private final boolean pointOffset = GL11.glIsEnabled(GL11.GL_POLYGON_OFFSET_POINT);
        private final float lineWidth = GL11.glGetFloat(GL11.GL_LINE_WIDTH);
        private final float offsetFactor = GL11.glGetFloat(GL11.GL_POLYGON_OFFSET_FACTOR);
        private final float offsetUnits = GL11.glGetFloat(GL11.GL_POLYGON_OFFSET_UNITS);
        private final int sourceRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), destinationRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        private final int sourceAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), destinationAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        private final int equationRgb = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB), equationAlpha = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);
        private final int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE), texture2d = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        private final int readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        private final int drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        private final boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        private final boolean[] colorMask = new boolean[4];
        private final int[] viewport = new int[4], scissorBox = new int[4], polygonMode = new int[2];

        private Snapshot() {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                ByteBuffer mask = stack.malloc(4); GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, mask);
                for (int i = 0; i < 4; i++) colorMask[i] = mask.get(i) != 0;
                IntBuffer ints = stack.mallocInt(4);
                GL11.glGetIntegerv(GL11.GL_VIEWPORT, ints); ints.get(0, viewport);
                GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, ints); ints.get(0, scissorBox);
                GL11.glGetIntegerv(GL11.GL_POLYGON_MODE, ints); ints.get(0, polygonMode);
            }
        }

        private void assertSame(Snapshot actual, String label) {
            require(program == actual.program, label + " preserves program");
            require(vao == actual.vao, label + " preserves VAO");
            require(array == actual.array, label + " preserves array buffer");
            require(elements == actual.elements, label + " preserves VAO element buffer");
            require(depthFunction == actual.depthFunction && depth == actual.depth && depthWrite == actual.depthWrite, label + " preserves depth state");
            require(cull == actual.cull && blend == actual.blend, label + " preserves cull/blend enables");
            require(fillOffset == actual.fillOffset && lineOffset == actual.lineOffset && pointOffset == actual.pointOffset, label + " preserves polygon-offset enables");
            require(Float.floatToRawIntBits(offsetFactor) == Float.floatToRawIntBits(actual.offsetFactor)
                    && Float.floatToRawIntBits(offsetUnits) == Float.floatToRawIntBits(actual.offsetUnits), label + " preserves polygon-offset values");
            require(Float.floatToRawIntBits(lineWidth) == Float.floatToRawIntBits(actual.lineWidth), label + " preserves line width");
            require(sourceRgb == actual.sourceRgb && destinationRgb == actual.destinationRgb
                    && sourceAlpha == actual.sourceAlpha && destinationAlpha == actual.destinationAlpha, label + " preserves separate blend functions");
            require(equationRgb == actual.equationRgb && equationAlpha == actual.equationAlpha, label + " preserves separate blend equations");
            require(Arrays.equals(colorMask, actual.colorMask), label + " preserves four color writes");
            require(Arrays.equals(polygonMode, actual.polygonMode), label + " preserves polygon mode");
            require(activeTexture == actual.activeTexture && texture2d == actual.texture2d, label + " preserves active texture/binding");
            require(readFramebuffer == actual.readFramebuffer && drawFramebuffer == actual.drawFramebuffer, label + " preserves framebuffer bindings");
            require(Arrays.equals(viewport, actual.viewport), label + " preserves viewport");
            require(scissor == actual.scissor && Arrays.equals(scissorBox, actual.scissorBox), label + " preserves scissor");
        }

        private void restore() {
            GL20.glUseProgram(program);
            GL30.glBindVertexArray(vao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, array);
            // The selected VAO already owns its saved element binding. Avoid an
            // invalid element-buffer mutation when the incoming core-profile VAO is 0.
            if (vao != 0) GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, elements);
            GL11.glDepthFunc(depthFunction); GL11.glDepthMask(depthWrite); GL11.glLineWidth(lineWidth);
            set(GL11.GL_DEPTH_TEST, depth); set(GL11.GL_CULL_FACE, cull); set(GL11.GL_BLEND, blend);
            set(GL11.GL_POLYGON_OFFSET_FILL, fillOffset); set(GL11.GL_POLYGON_OFFSET_LINE, lineOffset);
            set(GL11.GL_POLYGON_OFFSET_POINT, pointOffset);
            GL14.glBlendFuncSeparate(sourceRgb, destinationRgb, sourceAlpha, destinationAlpha);
            GL20.glBlendEquationSeparate(equationRgb, equationAlpha);
            GL11.glColorMask(colorMask[0], colorMask[1], colorMask[2], colorMask[3]);
            GL11.glScissor(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]);
            set(GL11.GL_SCISSOR_TEST, scissor);
        }
    }
}
