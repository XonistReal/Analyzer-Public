package dev.analyzer.render;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.analyzer.core.AnalyzerCore;
import dev.analyzer.core.ModuleDefinition;
import dev.analyzer.ui.GuiDraw;
import dev.analyzer.ui.HudWidgets;
import dev.analyzer.ui.Palette;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
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
import net.minecraft.world.RaycastContext;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
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
    private static final int MAX_SCREEN_LABELS = 40;
    private static final float SCREEN_LABEL_SCALE = 0.62f;
    private static final String CLIENT_VERSION = FabricLoader.getInstance().getModContainer("analyzer")
            .map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("dev");
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
    private static volatile AnalyzerRenderer activeRenderer;
    private List<ScreenTarget> screenEntities = List.of(), screenStorage = List.of();
    private final List<Target> radarTargets = new ArrayList<>();
    private Vec3d frameCamera = Vec3d.ZERO;
    private boolean registered;

    public AnalyzerRenderer(AnalyzerCore core) {
        this.core = core;
        activeRenderer = this;
    }

    /** Last submitted world targets. Read-only diagnostics used by the development smoke run. */
    public static RenderCounts counts() { return lastCounts; }
    public record RenderCounts(int entities, int containers, int tracers) { }
    /** Screen-mode rectangles surviving near-plane / viewport / optional visibility checks. */
    public static RenderCounts screenCounts() {
        AnalyzerRenderer renderer = activeRenderer;
        return renderer == null ? new RenderCounts(0, 0, 0)
                : new RenderCounts(renderer.screenEntities.size(), renderer.screenStorage.size(), 0);
    }

    /** Live, concise inspector feedback. Counts include selected loaded targets in range. */
    public static String status(String moduleId) {
        AnalyzerRenderer renderer = activeRenderer;
        if (renderer == null || renderer.client.world == null) return "Join a world to see live targets";
        int count = switch (moduleId) {
            case "entity-esp" -> lastCounts.entities;
            case "storage-esp" -> lastCounts.containers;
            case "tracers" -> lastCounts.tracers;
            default -> -1;
        };
        if (moduleId.equals("storage-esp")) {
            if (!renderer.storage.ready()) return "Scanning loaded chunks";
            if (count == 0) return "0 containers · " + renderer.storage.loadedChunksScanned() + " loaded chunks scanned";
            return count + (count == 1 ? " container in range" : " containers in range");
        }
        if (count < 0) return "";
        EntityFilter filter = renderer.filter(moduleId);
        if (!filter.players && !filter.hostiles && !filter.passives && !filter.items) return "Select a target category";
        if (count == 0 && filter.players && !filter.hostiles && !filter.passives && !filter.items) {
            return renderer.client.isInSingleplayer() ? "0 other players · enable mobs for this world" : "0 other players in range";
        }
        if (count == 0) return "0 matching entities in range";
        return count + (count == 1 ? " entity in range" : " entities in range");
    }

    @SuppressWarnings("deprecation")
    public void register() {
        if (registered) return;
        registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            storage.tick(mc, core);
            if (mc.world == null) {
                lastCounts = new RenderCounts(0, 0, 0);
                screenEntities = List.of(); screenStorage = List.of(); radarTargets.clear();
            }
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> labelAllocator.close());
        // Fabulous composition clears main depth. Depth-tested overlays must be drawn
        // before it; through-wall overlays belong after all composition has completed.
        WorldRenderEvents.LAST.register(context -> renderWorld(context, false));
        WorldRenderEvents.END.register(context -> renderWorld(context, true));
        HudRenderCallback.EVENT.register(this::renderHud);
    }

    private void renderWorld(WorldRenderContext context, boolean afterWorld) {
        if (!afterWorld) {
            lastCounts = new RenderCounts(0, 0, 0);
            screenEntities = List.of(); screenStorage = List.of(); radarTargets.clear();
            storage.ensureReady(client, core);
        }
        if (client.world == null || client.player == null) return;
        boolean esp = belongsToPass("entity-esp", afterWorld);
        boolean containers = belongsToPass("storage-esp", afterWorld);
        boolean tracers = belongsToPass("tracers", afterWorld);
        if (!esp && !containers && !tracers) return;

        Vec3d camera = context.camera().getPos();
        frameCamera = camera;
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
        radarTargets.addAll(entityTargets);
        boolean entityScreen = esp && core.getChoice("entity-esp", "style").equals("Screen");
        boolean storageScreen = containers && core.getChoice("storage-esp", "style").equals("Screen");
        if (entityScreen) screenEntities = projectTargets("entity-esp", entityTargets, context);
        if (storageScreen) screenStorage = projectTargets("storage-esp", storageTargets, context);

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
            if (!entityScreen && !entityTargets.isEmpty()) drawEsp("entity-esp", entityTargets);
            if (!storageScreen && !storageTargets.isEmpty()) drawEsp("storage-esp", storageTargets);
            if (!tracerTargets.isEmpty()) {
                setDepth(core.getBoolean("tracers", "through-walls"));
                // A point just beyond the near plane anchors the lines at the center of view.
                Vector3f start = new Vector3f(0, -0.025f, -0.25f).rotate(context.camera().getRotation());
                float width = (float) core.getNumber("tracers", "line-width");
                drawTracers(tracerTargets, start, width + 2, 28);
                drawTracers(tracerTargets, start, width, 190);
            }
            if (!entityScreen && !entityTargets.isEmpty()) drawLabels("entity-esp", entityTargets, context,
                    core.getBoolean("entity-esp", "labels"), core.getBoolean("entity-esp", "health"));
            if (!storageScreen && !storageTargets.isEmpty()) drawLabels("storage-esp", storageTargets, context,
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

    private List<ScreenTarget> projectTargets(String module, List<Target> targets, WorldRenderContext context) {
        Matrix4f clipMatrix = new Matrix4f(context.projectionMatrix()).mul(context.positionMatrix());
        boolean throughWalls = core.getBoolean(module, "through-walls");
        boolean labels = core.getBoolean(module, "labels");
        boolean health = module.equals("entity-esp") && core.getBoolean(module, "health");
        List<ScreenTarget> projected = new ArrayList<>();
        // Nearest targets get priority when a dense base contains many loaded entities.
        for (int i = targets.size() - 1; i >= 0 && projected.size() < MAX_LABELS; i--) {
            Target target = targets.get(i);
            if (context.frustum() != null && !context.frustum().isVisible(
                    target.bounds.offset(context.camera().getPos()).expand(0.25))) continue;
            ScreenBounds bounds = projectBox(target.bounds, clipMatrix);
            if (bounds == null || !throughWalls && !hasLineOfSight(target, context.camera().getPos())) continue;
            String text = labels ? name(target) : "";
            if (text.length() > 48) text = text.substring(0, 45) + "…";
            if (labels) text += " · " + Math.round(Math.sqrt(target.distanceSquared)) + "m";
            float fraction = health && target.entity instanceof LivingEntity living
                    ? Math.clamp(living.getHealth() / Math.max(1, living.getMaxHealth()), 0, 1) : -1;
            projected.add(new ScreenTarget(bounds, target.color, text, fraction, target.distanceSquared));
        }
        Collections.reverse(projected);
        return List.copyOf(projected);
    }

    private boolean hasLineOfSight(Target target, Vec3d camera) {
        Vec3d end = target.bounds.getCenter().add(camera);
        var hit = client.world.raycast(new RaycastContext(camera, end,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, client.player));
        return hit.getType() == HitResult.Type.MISS || target.bounds.expand(0.08).contains(hit.getPos().subtract(camera));
    }

    /** Clip 3D edges at the near plane before projecting, so nearby boxes never flip or jump. */
    private static ScreenBounds projectBox(Box box, Matrix4f matrix) {
        if (box.contains(Vec3d.ZERO)) return null;
        Vector4f[] corners = new Vector4f[8];
        List<Vector4f> visible = new ArrayList<>(20);
        for (int i = 0; i < 8; i++) {
            corners[i] = matrix.transform(new Vector4f((float) ((i & 1) == 0 ? box.minX : box.maxX),
                    (float) ((i & 2) == 0 ? box.minY : box.maxY),
                    (float) ((i & 4) == 0 ? box.minZ : box.maxZ), 1));
            if (corners[i].z + corners[i].w >= 0.001f && corners[i].w > 0.001f) visible.add(corners[i]);
        }
        for (int edge = 0; edge < BOX_EDGES.length; edge += 2) {
            Vector4f a = corners[BOX_EDGES[edge]], b = corners[BOX_EDGES[edge + 1]];
            float da = a.z + a.w - 0.001f, db = b.z + b.w - 0.001f;
            if ((da < 0) == (db < 0)) continue;
            Vector4f intersection = new Vector4f(a).lerp(b, da / (da - db));
            if (intersection.w > 0.001f) visible.add(intersection);
        }
        if (visible.isEmpty()) return null;
        float left = Float.POSITIVE_INFINITY, top = Float.POSITIVE_INFINITY;
        float right = Float.NEGATIVE_INFINITY, bottom = Float.NEGATIVE_INFINITY;
        for (Vector4f point : visible) {
            float x = point.x / point.w, y = point.y / point.w;
            if (!Float.isFinite(x) || !Float.isFinite(y)) continue;
            left = Math.min(left, x); right = Math.max(right, x);
            top = Math.min(top, -y); bottom = Math.max(bottom, -y);
        }
        if (right < -1 || left > 1 || bottom < -1 || top > 1 || !Float.isFinite(left)) return null;
        left = Math.clamp((left + 1) / 2, 0, 1); right = Math.clamp((right + 1) / 2, 0, 1);
        top = Math.clamp((top + 1) / 2, 0, 1); bottom = Math.clamp((bottom + 1) / 2, 0, 1);
        return right > left && bottom > top ? new ScreenBounds(left, top, right, bottom) : null;
    }

    private void drawScreenTargets(DrawContext draw, String module, List<ScreenTarget> targets,
                                   List<ScreenLabelRequest> labelRequests, List<HudRect> boxes) {
        if (!core.isEnabled(module) || !core.getChoice(module, "style").equals("Screen")) return;
        int width = draw.getScaledWindowWidth(), height = draw.getScaledWindowHeight();
        int thickness = Math.max(1, Math.round((float) core.getNumber(module, "line-width")));
        for (ScreenTarget target : targets) {
            int left = Math.round(target.bounds.left * width), top = Math.round(target.bounds.top * height);
            int right = Math.max(left + 3, Math.round(target.bounds.right * width));
            int bottom = Math.max(top + 3, Math.round(target.bounds.bottom * height));
            HudRect box = new HudRect(left, top, right - left, bottom - top);
            boxes.add(box);
            draw.fill(left, top, right, bottom, Palette.alpha(target.color, 12));
            int corner = Math.max(3, Math.min(18, Math.min(right - left, bottom - top) / 4));
            for (int layer = 3; layer >= 1; layer--) {
                screenCorners(draw, left - layer, top - layer, right + layer, bottom + layer,
                        corner + layer, thickness + layer * 2, Palette.alpha(target.color, 7));
            }
            screenCorners(draw, left - 1, top - 1, right + 1, bottom + 1, corner + 1, thickness + 2, 0xC4060D13);
            screenCorners(draw, left, top, right, bottom, corner, thickness, Palette.alpha(target.color, 250));
            if (target.health >= 0) {
                draw.fill(left - 5, top - 1, left - 2, bottom + 1, 0xD9060D13);
                int healthColor = target.health > 0.6f ? PASSIVE_COLOR : target.health > 0.3f ? ITEM_COLOR : HOSTILE_COLOR;
                draw.fill(left - 4, bottom - Math.round((bottom - top) * target.health), left - 3, bottom, healthColor);
            }
            if (!target.text.isEmpty()) {
                String text = GuiDraw.truncate(client.textRenderer, target.text, Math.min(120, width - 16), SCREEN_LABEL_SCALE);
                int panelWidth = (int) Math.ceil(GuiDraw.width(client.textRenderer, text, SCREEN_LABEL_SCALE)) + 8;
                labelRequests.add(new ScreenLabelRequest(box, panelWidth, text, target.color, target.distanceSquared));
            }
        }
    }

    /** Shared layout keeps nearby entity/container tags readable without hiding their boxes. */
    private void drawScreenLabels(DrawContext draw, List<ScreenLabelRequest> requests, List<HudRect> boxes,
                                  List<HudRect> reserved) {
        int width = draw.getScaledWindowWidth(), height = draw.getScaledWindowHeight();
        requests.sort(Comparator.comparingDouble(ScreenLabelRequest::distanceSquared));
        List<HudRect> occupied = new ArrayList<>(reserved);
        occupied.addAll(boxes);
        int drawn = 0, panelHeight = 10;
        for (ScreenLabelRequest request : requests) {
            if (drawn >= MAX_SCREEN_LABELS) break;
            HudRect panel = findScreenLabelPosition(request, panelHeight, width, height, occupied);
            if (panel == null) continue;
            occupied.add(panel);
            float ax = Math.clamp(panel.x + panel.width / 2f, request.box.x, request.box.right());
            float ay = Math.clamp(panel.y + panel.height / 2f, request.box.y, request.box.bottom());
            float bx = Math.clamp(ax, panel.x, panel.right()), by = Math.clamp(ay, panel.y, panel.bottom());
            if (Math.abs(ax - bx) + Math.abs(ay - by) > 5) {
                GuiDraw.line(draw, ax, ay, bx, by, Palette.alpha(request.color, 85));
            }
            GuiDraw.outlined(draw, panel.x, panel.y, panel.width, panel.height, 2,
                    Palette.alpha(request.color, 125), 0xE508131A);
            draw.fill(panel.x + 1, panel.y + 2, panel.x + 2, panel.bottom() - 2, Palette.alpha(request.color, 230));
            GuiDraw.label(draw, client.textRenderer, request.text, panel.x + 4, panel.y + 2, SCREEN_LABEL_SCALE, Palette.TEXT);
            drawn++;
        }
    }

    private static HudRect findScreenLabelPosition(ScreenLabelRequest request, int panelHeight,
                                                    int width, int height, List<HudRect> occupied) {
        int centerX = request.box.x + request.box.width / 2;
        int preferredX = centerX - request.panelWidth / 2;
        // A bounded search gives tags nearby staggered rows; dense/offscreen tags may be omitted.
        for (int row = 0; row < 6; row++) {
            int above = request.box.y - panelHeight - 3 - row * (panelHeight + 3);
            int below = request.box.bottom() + 3 + row * (panelHeight + 3);
            int[] positions = {preferredX, above, preferredX, below,
                    request.box.right() + 4, request.box.y + row * (panelHeight + 3),
                    request.box.x - request.panelWidth - 4, request.box.y + row * (panelHeight + 3)};
            for (int position = 0; position < positions.length; position += 2) {
                int x = Math.clamp(positions[position], 2, Math.max(2, width - request.panelWidth - 2));
                int y = positions[position + 1];
                if (y < 2 || y + panelHeight > height - 2) continue;
                HudRect candidate = new HudRect(x, y, request.panelWidth, panelHeight);
                boolean free = true;
                for (HudRect other : occupied) {
                    if (candidate.intersects(other, 2)) { free = false; break; }
                }
                if (free) return candidate;
            }
        }
        return null;
    }

    private List<HudRect> reservedHudAreas(DrawContext draw, float scale, List<ModuleDefinition> enabled) {
        List<HudRect> reserved = new ArrayList<>();
        if (!client.getDebugHud().shouldShowDebugHud()) {
            for (HudWidgets.WidgetBounds widget : HudWidgets.bounds(core, draw, false)) {
                reserved.add(new HudRect(widget.x(), widget.y(), widget.width(), widget.height()));
            }
            if (core.isEnabled("hud") && core.isEnabled("entity-esp") && core.getBoolean("entity-esp", "radar")) {
                int logicalWidth = Math.round(draw.getScaledWindowWidth() / scale);
                int logicalHeight = Math.round(draw.getScaledWindowHeight() / scale);
                int x = (int) Math.round(HudWidgets.position(core, "radar", true) * Math.max(0, logicalWidth - 108) / 100);
                int y = (int) Math.round(HudWidgets.position(core, "radar", false) * Math.max(0, logicalHeight - 133) / 100);
                reserved.add(HudRect.scaled(x, y, 108, 133, scale));
            }
        }
        int width = draw.getScaledWindowWidth(), height = draw.getScaledWindowHeight();
        reserved.add(new HudRect(Math.max(0, width / 2 - 94), Math.max(0, height - 27), 188, 27));
        reserved.add(new HudRect(width / 2 - 12, height / 2 - 12, 24, 24));
        return reserved;
    }

    private static boolean isTargetModule(String id) {
        return id.equals("entity-esp") || id.equals("storage-esp") || id.equals("tracers");
    }

    private static void screenCorners(DrawContext draw, int left, int top, int right, int bottom,
                                      int length, int thickness, int color) {
        draw.fill(left, top, left + length, top + thickness, color);
        draw.fill(left, top, left + thickness, top + length, color);
        draw.fill(right - length, top, right, top + thickness, color);
        draw.fill(right - thickness, top, right, top + length, color);
        draw.fill(left, bottom - thickness, left + length, bottom, color);
        draw.fill(left, bottom - length, left + thickness, bottom, color);
        draw.fill(right - length, bottom - thickness, right, bottom, color);
        draw.fill(right - thickness, bottom - length, right, bottom, color);
    }

    private record ScreenBounds(float left, float top, float right, float bottom) { }
    private record ScreenTarget(ScreenBounds bounds, int color, String text, float health, double distanceSquared) { }
    private record ScreenLabelRequest(HudRect box, int panelWidth, String text, int color, double distanceSquared) { }
    private record HudRect(int x, int y, int width, int height) {
        int right() { return x + width; }
        int bottom() { return y + height; }
        boolean intersects(HudRect other, int gap) {
            return x < other.right() + gap && right() + gap > other.x
                    && y < other.bottom() + gap && bottom() + gap > other.y;
        }
        static HudRect scaled(int x, int y, int width, int height, float scale) {
            int left = (int) Math.floor(x * scale), top = (int) Math.floor(y * scale);
            return new HudRect(left, top, (int) Math.ceil((x + width) * scale) - left,
                    (int) Math.ceil((y + height) * scale) - top);
        }
    }

    private void renderHud(DrawContext draw, RenderTickCounter tickCounter) {
        if (client.world == null || client.player == null || client.options.hudHidden || client.currentScreen != null) return;
        if (core.isEnabled("crosshair") && client.options.getPerspective().isFirstPerson()) drawCrosshair(draw);
        float hudScale = HudWidgets.scale(core, draw);
        List<ScreenLabelRequest> labelRequests = new ArrayList<>();
        List<HudRect> boxes = new ArrayList<>();
        drawScreenTargets(draw, "entity-esp", screenEntities, labelRequests, boxes);
        drawScreenTargets(draw, "storage-esp", screenStorage, labelRequests, boxes);
        drawScreenLabels(draw, labelRequests, boxes, reservedHudAreas(draw, hudScale, List.of()));
        if (client.getDebugHud().shouldShowDebugHud()) return;
        // HUD Studio draws these exact widgets itself, with its editing guides.
        if (client.currentScreen instanceof dev.analyzer.ui.HudEditorScreen) return;
        HudWidgets.render(core, draw, false);
        if (core.isEnabled("hud") && core.isEnabled("entity-esp") && core.getBoolean("entity-esp", "radar")) {
            int width = Math.round(draw.getScaledWindowWidth() / hudScale), height = Math.round(draw.getScaledWindowHeight() / hudScale);
            if (height < 150) return;
            int x = (int) Math.round(HudWidgets.position(core, "radar", true) * Math.max(0, width - 108) / 100);
            int y = (int) Math.round(HudWidgets.position(core, "radar", false) * Math.max(0, height - 133) / 100);
            draw.getMatrices().push();
            draw.getMatrices().scale(hudScale, hudScale, 1);
            draw.getMatrices().translate(x - 10, y - 57, 0);
            drawRadar(draw);
            draw.getMatrices().pop();
        }
    }

    /** The editor uses the actual selected entity radar, and the same persisted placement as gameplay. */
    public static HudWidgets.WidgetBounds renderEditorRadar(DrawContext draw) {
        AnalyzerRenderer renderer = activeRenderer;
        if (renderer == null || renderer.client.world == null || renderer.client.player == null
                || !renderer.core.isEnabled("entity-esp") || !renderer.core.getBoolean("entity-esp", "radar")) return null;
        float scale = HudWidgets.scale(renderer.core, draw);
        int width = Math.round(draw.getScaledWindowWidth() / scale), height = Math.round(draw.getScaledWindowHeight() / scale);
        int x = (int) Math.round(HudWidgets.position(renderer.core, "radar", true) * Math.max(0, width - 108) / 100);
        int y = (int) Math.round(HudWidgets.position(renderer.core, "radar", false) * Math.max(0, height - 133) / 100);
        draw.getMatrices().push(); draw.getMatrices().scale(scale, scale, 1); draw.getMatrices().translate(x - 10, y - 57, 0);
        renderer.drawRadar(draw); draw.getMatrices().pop();
        return new HudWidgets.WidgetBounds("radar", Math.round(x * scale), Math.round(y * scale), Math.round(108 * scale), Math.round(133 * scale));
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

    private void drawRadar(DrawContext draw) {
        int cx = 64, cy = 118, radius = 35;
        GuiDraw.outlined(draw, 10, 57, 108, 133, 7, 0x5A4C7780, 0xD908141D);
        GuiDraw.label(draw, client.textRenderer, "LOCAL RADAR", 20, 65, 0.8f, Palette.TEXT);
        GuiDraw.label(draw, client.textRenderer, "N", cx - 2, cy - radius - 9, 0.65f, Palette.MUTED);
        for (int ring = 1; ring <= 2; ring++) {
            double r = radius * ring / 2.0;
            for (int segment = 0; segment < 48; segment++) {
                double a = segment * Math.PI * 2 / 48, b = (segment + 1) * Math.PI * 2 / 48;
                GuiDraw.line(draw, cx + Math.cos(a) * r, cy + Math.sin(a) * r,
                        cx + Math.cos(b) * r, cy + Math.sin(b) * r, 0x424E7784);
            }
        }
        GuiDraw.line(draw, cx - radius, cy, cx + radius, cy, 0x253F6570);
        GuiDraw.line(draw, cx, cy - radius, cx, cy + radius, 0x253F6570);
        double sweep = System.nanoTime() / 1_000_000_000.0 * 0.75;
        GuiDraw.line(draw, cx, cy, cx + Math.cos(sweep) * radius, cy + Math.sin(sweep) * radius, 0x3864EBD0);
        double scale = radius / Math.max(1, core.getNumber("entity-esp", "range"));
        Vec3d origin = client.player.getPos();
        int count = 0;
        for (int i = radarTargets.size() - 1; i >= 0 && count < MAX_LABELS; i--) {
            Target target = radarTargets.get(i);
            Vec3d relative = target.bounds.getCenter().add(frameCamera).subtract(origin);
            double px = relative.x * scale, py = relative.z * scale;
            if (px * px + py * py > radius * radius) continue;
            int x = cx + (int) Math.round(px), y = cy + (int) Math.round(py);
            draw.fill(x - 2, y - 2, x + 3, y + 3, Palette.alpha(target.color, 45));
            draw.fill(x - 1, y - 1, x + 2, y + 2, target.color);
            count++;
        }
        double yaw = Math.toRadians(client.player.getYaw());
        double dx = -Math.sin(yaw), dz = Math.cos(yaw);
        GuiDraw.line(draw, cx + dx * 5, cy + dz * 5, cx - dx * 3 + dz * 3, cy - dz * 3 - dx * 3, Palette.TEXT);
        GuiDraw.line(draw, cx + dx * 5, cy + dz * 5, cx - dx * 3 - dz * 3, cy - dz * 3 + dx * 3, Palette.TEXT);
        draw.fill(cx - 1, cy - 1, cx + 2, cy + 2, Palette.TEXT);
        int[] colors = {PLAYER_COLOR, HOSTILE_COLOR, PASSIVE_COLOR, ITEM_COLOR};
        String[] legend = {"P", "H", "M", "I"};
        for (int i = 0; i < colors.length; i++) {
            int x = 20 + i * 24;
            draw.fill(x, 164, x + 3, 167, colors[i]);
            GuiDraw.label(draw, client.textRenderer, legend[i], x + 6, 162, 0.65f, Palette.MUTED);
        }
        String summary = radarTargets.isEmpty() ? "0 selected targets" : radarTargets.size() + " targets · "
                + Math.round(core.getNumber("entity-esp", "range")) + "m";
        GuiDraw.label(draw, client.textRenderer, GuiDraw.truncate(client.textRenderer, summary, 92, 0.7f),
                18, 178, 0.7f, radarTargets.isEmpty() ? Palette.MUTED : Palette.MINT);
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
