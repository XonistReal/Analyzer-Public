package dev.analyzer.render;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.analyzer.core.AnalyzerCore;
import dev.analyzer.core.ModuleDefinition;
import dev.analyzer.ui.GuiDraw;
import dev.analyzer.ui.Palette;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Fog;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.List;

/** Native world overlays and HUD. No entity flags, game packets, or world state are changed. */
public final class AnalyzerRenderer {
    private static final int MAX_ENTITY_TARGETS = 4096;
    private static final int MAX_LABELS = 128;
    private static final int PLAYER_COLOR = 0xFF66DFF7;
    private static final int HOSTILE_COLOR = 0xFFFF7188;
    private static final int PASSIVE_COLOR = 0xFF79F8CC;
    private static final int ITEM_COLOR = 0xFFFFCF78;
    private static final Comparator<Target> BACK_TO_FRONT = Comparator.comparingDouble(Target::distanceSquared).reversed();
    private static final int[] BOX_EDGES = {0, 1, 1, 5, 5, 4, 4, 0, 2, 3, 3, 7, 7, 6, 6, 2, 0, 2, 1, 3, 5, 7, 4, 6};
    private static final int[] BOX_FACES = {0, 1, 3, 2, 5, 4, 6, 7, 4, 0, 2, 6, 1, 5, 7, 3, 4, 5, 1, 0, 2, 3, 7, 6};

    private final AnalyzerCore core;
    private final MinecraftClient client = MinecraftClient.getInstance();
    private final StorageCache storage = new StorageCache();
    private final BufferAllocator labelAllocator = new BufferAllocator(64 * 1024);
    private final VertexConsumerProvider.Immediate labelConsumers = VertexConsumerProvider.immediate(labelAllocator);
    private static volatile RenderCounts lastCounts = new RenderCounts(0, 0, 0);
    private boolean registered;

    public AnalyzerRenderer(AnalyzerCore core) {
        this.core = core;
    }

    /** Last submitted world targets. Read-only diagnostics used by the development smoke run. */
    public static RenderCounts counts() { return lastCounts; }
    public record RenderCounts(int entities, int containers, int tracers) { }

    @SuppressWarnings("deprecation")
    public void register() {
        if (registered) return;
        registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            storage.tick(mc, core);
            if (mc.world == null) lastCounts = new RenderCounts(0, 0, 0);
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> labelAllocator.close());
        // Fabulous composition clears main depth. Depth-tested overlays must be drawn
        // before it; through-wall overlays belong after all composition has completed.
        WorldRenderEvents.LAST.register(context -> renderWorld(context, false));
        WorldRenderEvents.END.register(context -> renderWorld(context, true));
        HudRenderCallback.EVENT.register(this::renderHud);
    }

    private void renderWorld(WorldRenderContext context, boolean afterWorld) {
        if (!afterWorld) lastCounts = new RenderCounts(0, 0, 0);
        if (client.world == null || client.player == null) return;
        boolean esp = belongsToPass("entity-esp", afterWorld);
        boolean containers = belongsToPass("storage-esp", afterWorld);
        boolean tracers = belongsToPass("tracers", afterWorld);
        if (!esp && !containers && !tracers) return;

        Vec3d camera = context.camera().getPos();
        float tickDelta = context.tickCounter().getTickDelta(false);
        List<Target> entityTargets = new ArrayList<>();
        List<Target> tracerTargets = new ArrayList<>();
        if (esp || tracers) {
            EntityFilter espFilter = esp ? filter("entity-esp") : null;
            EntityFilter tracerFilter = tracers ? filter("tracers") : null;
            for (Entity entity : context.world().getEntities()) {
                if (entity == client.player || entity == context.camera().getFocusedEntity()
                        || entity.isRemoved() || !entity.isAlive()) continue;
                EntityKind kind = kind(entity);
                if (kind == null) continue;
                Vec3d interpolated = entity.getLerpedPos(tickDelta);
                double distanceSquared = interpolated.squaredDistanceTo(camera);
                boolean drawEsp = espFilter != null && espFilter.accepts(kind, distanceSquared)
                        && entityTargets.size() < MAX_ENTITY_TARGETS;
                boolean drawTracer = tracerFilter != null && tracerFilter.accepts(kind, distanceSquared)
                        && tracerTargets.size() < MAX_ENTITY_TARGETS;
                if (!drawEsp && !drawTracer) continue;
                // Interpolate the bounding box's position, retaining its current pose and dimensions.
                Box bounds = entity.getBoundingBox().offset(interpolated.subtract(entity.getPos())).expand(0.025);
                if (drawEsp) entityTargets.add(new Target(bounds.offset(camera.negate()), kind.color, distanceSquared, entity, null));
                if (drawTracer) tracerTargets.add(new Target(bounds.offset(camera.negate()), kind.color, distanceSquared, entity, null));
            }
        }

        List<Target> storageTargets = new ArrayList<>();
        if (containers) {
            double range = core.getNumber("storage-esp", "range");
            for (StorageCache.Container container : storage.containers()) {
                if (!storage.isCurrent(client.world, container)) continue;
                double distanceSquared = container.bounds().getCenter().squaredDistanceTo(camera);
                if (distanceSquared <= range * range) {
                    storageTargets.add(new Target(container.bounds().offset(camera.negate()),
                            container.color(), distanceSquared, null, container.name()));
                }
            }
        }
        if (entityTargets.isEmpty() && storageTargets.isEmpty() && tracerTargets.isEmpty()) return;
        entityTargets.sort(BACK_TO_FRONT);
        storageTargets.sort(BACK_TO_FRONT);
        tracerTargets.sort(BACK_TO_FRONT);
        lastCounts = new RenderCounts(lastCounts.entities + entityTargets.size(),
                lastCounts.containers + storageTargets.size(), lastCounts.tracers + tracerTargets.size());

        // LAST retains the camera matrix and world depth. END has rebound main and popped
        // the camera matrix, so reinstall the view only for that later scope.
        RenderState previous = new RenderState();
        if (afterWorld) RenderSystem.getModelViewStack().pushMatrix();
        try {
            if (afterWorld) RenderSystem.getModelViewStack().mul(context.positionMatrix());
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableCull();
            RenderSystem.depthMask(false);
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.setShaderFog(Fog.DUMMY);
            if (!entityTargets.isEmpty()) drawEsp("entity-esp", entityTargets);
            if (!storageTargets.isEmpty()) drawEsp("storage-esp", storageTargets);
            if (!tracerTargets.isEmpty()) {
                setDepth(core.getBoolean("tracers", "through-walls"));
                // A point just beyond the near plane anchors the lines at the center of view.
                Vector3f start = new Vector3f(0, -0.025f, -0.25f).rotate(context.camera().getRotation());
                float width = (float) core.getNumber("tracers", "line-width");
                drawTracers(tracerTargets, start, width + 2, 28);
                drawTracers(tracerTargets, start, width, 190);
            }
            if (!entityTargets.isEmpty()) drawLabels("entity-esp", entityTargets, context,
                    core.getBoolean("entity-esp", "labels"), core.getBoolean("entity-esp", "health"));
            if (!storageTargets.isEmpty()) drawLabels("storage-esp", storageTargets, context,
                    core.getBoolean("storage-esp", "labels"), false);
        } finally {
            if (afterWorld) RenderSystem.getModelViewStack().popMatrix();
            previous.restore();
        }
    }

    private boolean belongsToPass(String module, boolean afterWorld) {
        return core.isEnabled(module) && core.getBoolean(module, "through-walls") == afterWorld;
    }

    private EntityFilter filter(String module) {
        double range = core.getNumber(module, "range");
        return new EntityFilter(core.getBoolean(module, "players"), core.getBoolean(module, "hostiles"),
                core.getBoolean(module, "passives"), core.getBoolean(module, "items"), range * range);
    }

    private static EntityKind kind(Entity entity) {
        if (entity instanceof PlayerEntity player) return player.isSpectator() ? null : EntityKind.PLAYER;
        if (entity instanceof ItemEntity) return EntityKind.ITEM;
        if (entity instanceof Monster) return EntityKind.HOSTILE;
        if (entity instanceof MobEntity) return EntityKind.PASSIVE;
        return null;
    }

    private void drawEsp(String module, List<Target> targets) {
        setDepth(core.getBoolean(module, "through-walls"));
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        BufferBuilder fill = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        for (Target target : targets) fillBox(fill, target.bounds, Palette.alpha(target.color, 22));
        BufferRenderer.drawWithGlobalProgram(fill.end());

        float width = (float) core.getNumber(module, "line-width");
        boolean corners = core.getChoice(module, "style").equals("Corners");
        drawOutlines(targets, corners, width + 3, 34);
        drawOutlines(targets, corners, width, 235);
    }

    private static void setDepth(boolean throughWalls) {
        if (throughWalls) RenderSystem.disableDepthTest();
        else RenderSystem.enableDepthTest();
    }

    private static void drawOutlines(List<Target> targets, boolean corners, float width, int alpha) {
        RenderSystem.setShader(ShaderProgramKeys.RENDERTYPE_LINES);
        RenderSystem.lineWidth(width);
        BufferBuilder lines = Tessellator.getInstance().begin(VertexFormat.DrawMode.LINES, VertexFormats.LINES);
        for (Target target : targets) outlineBox(lines, target.bounds, Palette.alpha(target.color, alpha), corners);
        BufferRenderer.drawWithGlobalProgram(lines.end());
    }

    private static void drawTracers(List<Target> targets, Vector3f start, float width, int alpha) {
        RenderSystem.setShader(ShaderProgramKeys.RENDERTYPE_LINES);
        RenderSystem.lineWidth(width);
        BufferBuilder lines = Tessellator.getInstance().begin(VertexFormat.DrawMode.LINES, VertexFormats.LINES);
        for (Target target : targets) {
            Vec3d center = target.bounds.getCenter();
            line(lines, start.x, start.y, start.z, center.x, center.y, center.z,
                    Palette.alpha(target.color, alpha));
        }
        BufferRenderer.drawWithGlobalProgram(lines.end());
    }

    private static void outlineBox(BufferBuilder buffer, Box box, int color, boolean corners) {
        for (int i = 0; i < BOX_EDGES.length; i += 2) {
            int a = BOX_EDGES[i], b = BOX_EDGES[i + 1];
            double ax = (a & 1) == 0 ? box.minX : box.maxX, bx = (b & 1) == 0 ? box.minX : box.maxX;
            double ay = (a & 2) == 0 ? box.minY : box.maxY, by = (b & 2) == 0 ? box.minY : box.maxY;
            double az = (a & 4) == 0 ? box.minZ : box.maxZ, bz = (b & 4) == 0 ? box.minZ : box.maxZ;
            if (corners) {
                double x = (bx - ax) * 0.24, y = (by - ay) * 0.24, z = (bz - az) * 0.24;
                line(buffer, ax, ay, az, ax + x, ay + y, az + z, color);
                line(buffer, bx - x, by - y, bz - z, bx, by, bz, color);
            } else line(buffer, ax, ay, az, bx, by, bz, color);
        }
    }

    private static void line(BufferBuilder buffer, double x1, double y1, double z1,
                             double x2, double y2, double z2, int color) {
        double dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0e-7) return;
        float nx = (float) (dx / length), ny = (float) (dy / length), nz = (float) (dz / length);
        // Minecraft's line shader expands paired vertices into screen-space quads. This
        // avoids the one-pixel limit of native OpenGL lines on several Windows drivers.
        buffer.vertex((float) x1, (float) y1, (float) z1).color(color).normal(nx, ny, nz);
        buffer.vertex((float) x2, (float) y2, (float) z2).color(color).normal(nx, ny, nz);
    }

    private static void fillBox(BufferBuilder b, Box box, int color) {
        for (int corner : BOX_FACES) {
            b.vertex((float) ((corner & 1) == 0 ? box.minX : box.maxX),
                    (float) ((corner & 2) == 0 ? box.minY : box.maxY),
                    (float) ((corner & 4) == 0 ? box.minZ : box.maxZ)).color(color);
        }
    }

    private void drawLabels(String module, List<Target> targets, WorldRenderContext context,
                            boolean names, boolean health) {
        if (!names && !health) return;
        List<Label> labels = new ArrayList<>();
        // Bound text work to the nearest visible targets. Geometry still covers the full configured range.
        for (int i = targets.size() - 1; i >= 0 && labels.size() < MAX_LABELS; i--) {
            Target target = targets.get(i);
            boolean healthBar = health && target.entity instanceof LivingEntity;
            if (!names && !healthBar) continue;
            if (context.frustum() != null && !context.frustum().isVisible(
                    target.bounds.offset(context.camera().getPos()).expand(0.7))) continue;
            // Bound remote names for readable tags while preserving their distance suffix.
            String name = names ? name(target) : "";
            if (name.length() > 48) name = name.substring(0, 45) + "…";
            name = GuiDraw.truncate(client.textRenderer, name, 110, 1);
            String label = names ? name + "  ·  " + Math.round(Math.sqrt(target.distanceSquared)) + "m" : "";
            int textWidth = names ? client.textRenderer.getWidth(GuiDraw.text(label)) : 0;
            float width = Math.max(40, textWidth + 12), height = names ? (healthBar ? 23 : 17) : 9;
            MatrixStack matrices = new MatrixStack();
            matrices.translate((target.bounds.minX + target.bounds.maxX) / 2,
                    target.bounds.maxY + 0.18, (target.bounds.minZ + target.bounds.maxZ) / 2);
            // Model-view already holds the world camera matrix in either pass. Vanilla label rotation cancels
            // its orientation; copying context.positionMatrix here would apply that camera twice.
            matrices.multiply(context.camera().getRotation());
            float scale = 0.025f * (float) Math.clamp(Math.sqrt(target.distanceSquared) / 20, 1, 8);
            matrices.scale(scale, -scale, scale);
            float healthFraction = healthBar ? Math.clamp(((LivingEntity) target.entity).getHealth()
                    / Math.max(1, ((LivingEntity) target.entity).getMaxHealth()), 0, 1) : -1;
            labels.add(new Label(label, textWidth, width, height, target.color, healthFraction,
                    new Matrix4f(matrices.peek().getPositionMatrix())));
        }
        if (labels.isEmpty()) return;
        Collections.reverse(labels);
        boolean throughWalls = core.getBoolean(module, "through-walls");
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        setDepth(throughWalls);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        BufferBuilder panels = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        for (Label label : labels) {
            float left = -label.width / 2, right = label.width / 2;
            panel(panels, label.matrix, left, -label.height, right, 0, 0xC20A141B);
            panel(panels, label.matrix, left, -label.height, left + 1.3f, 0,
                    Palette.alpha(label.color, 235));
            if (label.healthFraction >= 0) {
                panel(panels, label.matrix, left + 5, -6, right - 5, -3, 0xFF243A42);
                int healthColor = label.healthFraction > 0.6f ? Palette.MINT
                        : label.healthFraction > 0.3f ? ITEM_COLOR : HOSTILE_COLOR;
                panel(panels, label.matrix, left + 5, -6,
                        left + 5 + (label.width - 10) * label.healthFraction, -3, healthColor);
            }
        }
        BufferRenderer.drawWithGlobalProgram(panels.end());
        if (names) {
            TextRenderer.TextLayerType layer = throughWalls ? TextRenderer.TextLayerType.SEE_THROUGH
                    : TextRenderer.TextLayerType.NORMAL;
            for (Label label : labels) {
                client.textRenderer.draw(GuiDraw.text(label.text), -label.textWidth / 2f,
                        -label.height + 4, Palette.TEXT, false, label.matrix, labelConsumers,
                        layer, 0, LightmapTextureManager.MAX_LIGHT_COORDINATE);
            }
            labelConsumers.draw();
        }
    }

    private static String name(Target target) {
        if (target.containerName != null) return target.containerName;
        if (target.entity instanceof ItemEntity item) {
            return item.getStack().getName().getString() + (item.getStack().getCount() > 1
                    ? " ×" + item.getStack().getCount() : "");
        }
        return target.entity.getName().getString();
    }

    private static void panel(BufferBuilder buffer, Matrix4f matrix, float left, float top,
                              float right, float bottom, int color) {
        if (right <= left || bottom <= top) return;
        buffer.vertex(matrix, left, top, 0).color(color);
        buffer.vertex(matrix, left, bottom, 0).color(color);
        buffer.vertex(matrix, right, bottom, 0).color(color);
        buffer.vertex(matrix, right, top, 0).color(color);
    }

    private record Label(String text, int textWidth, float width, float height, int color,
                         float healthFraction, Matrix4f matrix) { }

    private void renderHud(DrawContext draw, RenderTickCounter tickCounter) {
        if (client.world == null || client.player == null || client.options.hudHidden
                || client.currentScreen != null) return;
        if (core.isEnabled("crosshair") && client.options.getPerspective().isFirstPerson()) drawCrosshair(draw);
        if (client.getDebugHud().shouldShowDebugHud()) return;
        float hudScale = Math.min(1, draw.getScaledWindowWidth() / 720f);
        int width = Math.round(draw.getScaledWindowWidth() / hudScale);
        int height = Math.round(draw.getScaledWindowHeight() / hudScale);
        draw.getMatrices().push();
        draw.getMatrices().scale(hudScale, hudScale, 1);
        try {
        int fps = client.getCurrentFps();
        GuiDraw.outlined(draw, 10, 10, 146, 37, 7, 0x483C6668, 0xD20B141A);
        GuiDraw.rounded(draw, 18, 18, 20, 21, 5, 0x2279F8CC);
        GuiDraw.logo(draw, 19, 18, 18, Palette.MINT);
        GuiDraw.label(draw, client.textRenderer, "ANALYZER", 45, 17, 1.24f, Palette.TEXT);
        GuiDraw.label(draw, client.textRenderer, "0.1  /  " + fps + " FPS", 46, 33, 0.8f, Palette.MUTED);
        draw.fill(145, 20, 147, 22, Palette.MINT);

        List<ModuleDefinition> enabled = core.modules().stream().filter(module -> core.isEnabled(module.id()))
                .sorted(Comparator.comparingDouble((ModuleDefinition module) ->
                        GuiDraw.width(client.textRenderer, module.name(), 1)).reversed()).toList();
        int y = 12;
        for (int i = 0; i < enabled.size(); i++) {
            ModuleDefinition module = enabled.get(i);
            int rowWidth = Math.round(GuiDraw.width(client.textRenderer, module.name(), 1)) + 18;
            int x = width - rowWidth - 10;
            int accent = Palette.mix(Palette.MINT, Palette.VIOLET, i / (double) Math.max(1, enabled.size() - 1));
            GuiDraw.rounded(draw, x, y, rowWidth, 18, 4, 0xBD0B141A);
            draw.fill(width - 12, y + 4, width - 10, y + 14, accent);
            GuiDraw.label(draw, client.textRenderer, module.name(), x + 7, y + 4, 1, accent);
            y += 20;
        }

        if (core.isEnabled("coordinates")) {
            String coordinates = "X " + client.player.getBlockX() + "   Y " + client.player.getBlockY()
                    + "   Z " + client.player.getBlockZ();
            String dimension = dimensionName();
            boolean showDimension = core.getBoolean("coordinates", "show-dimension");
            int cardHeight = showDimension ? 34 : 23;
            int cardWidth = (int) Math.ceil(Math.max(GuiDraw.width(client.textRenderer, coordinates, 1),
                    showDimension ? GuiDraw.width(client.textRenderer, dimension, 0.8f) : 0)) + 20;
            int cy = height - cardHeight - 10;
            GuiDraw.outlined(draw, 10, cy, cardWidth, cardHeight, 6, 0x483C6668, 0xD20B141A);
            GuiDraw.label(draw, client.textRenderer, coordinates, 20, cy + 7, 1, Palette.TEXT);
            if (showDimension) GuiDraw.label(draw, client.textRenderer, dimension, 20, cy + 21, 0.8f, Palette.MUTED);
        }
        } finally {
            draw.getMatrices().pop();
        }
    }

    private String dimensionName() {
        String id = client.world.getRegistryKey().getValue().toString();
        return switch (id) {
            case "minecraft:overworld" -> "OVERWORLD";
            case "minecraft:the_nether" -> "THE NETHER";
            case "minecraft:the_end" -> "THE END";
            default -> id;
        };
    }

    private void drawCrosshair(DrawContext draw) {
        float cx = draw.getScaledWindowWidth() / 2f, cy = draw.getScaledWindowHeight() / 2f;
        float length = (float) core.getNumber("crosshair", "size");
        float gap = (float) core.getNumber("crosshair", "gap");
        float half = (float) core.getNumber("crosshair", "line-width") / 2;
        int color = client.crosshairTarget != null && client.crosshairTarget.getType() == HitResult.Type.ENTITY
                ? PLAYER_COLOR : Palette.MINT;
        draw.getMatrices().push();
        draw.getMatrices().scale(0.5f, 0.5f, 1);
        crosshairArm(draw, cx - gap - length, cy - half, cx - gap, cy + half, color);
        crosshairArm(draw, cx + gap, cy - half, cx + gap + length, cy + half, color);
        crosshairArm(draw, cx - half, cy - gap - length, cx + half, cy - gap, color);
        crosshairArm(draw, cx - half, cy + gap, cx + half, cy + gap + length, color);
        if (gap == 0) draw.fill(Math.round((cx - half) * 2), Math.round((cy - half) * 2),
                Math.round((cx + half) * 2), Math.round((cy + half) * 2), color);
        draw.getMatrices().pop();
    }

    private static void crosshairArm(DrawContext draw, float x0, float y0, float x1, float y1, int color) {
        int left = Math.round(x0 * 2), top = Math.round(y0 * 2);
        int right = Math.max(left + 1, Math.round(x1 * 2)), bottom = Math.max(top + 1, Math.round(y1 * 2));
        draw.fill(left - 2, top - 2, right + 2, bottom + 2, 0xAE071014);
        draw.fill(left, top, right, bottom, color);
    }

    private record Target(Box bounds, int color, double distanceSquared, Entity entity, String containerName) { }

    private enum EntityKind {
        PLAYER(PLAYER_COLOR), HOSTILE(HOSTILE_COLOR), PASSIVE(PASSIVE_COLOR), ITEM(ITEM_COLOR);
        private final int color;
        EntityKind(int color) { this.color = color; }
    }

    private record EntityFilter(boolean players, boolean hostiles, boolean passives, boolean items,
                                double squaredRange) {
        boolean accepts(EntityKind kind, double distanceSquared) {
            return distanceSquared <= squaredRange && switch (kind) {
                case PLAYER -> players;
                case HOSTILE -> hostiles;
                case PASSIVE -> passives;
                case ITEM -> items;
            };
        }
    }

    /** Restore actual incoming state instead of assuming vanilla defaults or another mod's state. */
    private static final class RenderState {
        private final boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        private final boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        private final boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        private final boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        private final int depthFunction = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        private final int sourceRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        private final int destinationRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        private final int sourceAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        private final int destinationAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        private final float lineWidth = RenderSystem.getShaderLineWidth();
        private final float[] color = RenderSystem.getShaderColor().clone();
        private final Fog fog = RenderSystem.getShaderFog();
        private final ShaderProgram shader = RenderSystem.getShader();

        void restore() {
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            RenderSystem.depthMask(depthMask);
            RenderSystem.depthFunc(depthFunction);
            RenderSystem.blendFuncSeparate(sourceRgb, destinationRgb, sourceAlpha, destinationAlpha);
            RenderSystem.lineWidth(lineWidth);
            RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
            RenderSystem.setShaderFog(fog);
            if (shader == null) RenderSystem.clearShader(); else RenderSystem.setShader(shader);
        }
    }
}
