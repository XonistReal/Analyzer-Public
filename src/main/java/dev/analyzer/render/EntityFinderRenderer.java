package dev.analyzer.render;

import dev.analyzer.AnalyzerClient;
import dev.analyzer.core.AnalyzerCore;
import dev.analyzer.core.EntityFinderModules;
import dev.analyzer.core.EspSettings;
import dev.analyzer.core.ModuleDefinition;
import dev.analyzer.finder.EntityFinderRules;
import dev.analyzer.finder.EntityFinderRules.Facts;
import dev.analyzer.finder.EntityFinderRules.Group;
import dev.analyzer.finder.EntityFinderRules.Kind;
import dev.analyzer.finder.EntityFinderRules.Options;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.GlowItemFrameEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.mob.DrownedEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.entity.mob.ZombieVillagerEntity;
import net.minecraft.entity.passive.LlamaEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

/** Bounded, read-only received-entity surveys and real posed biped bone overlays. */
public final class EntityFinderRenderer {
    private static final MinecraftClient MC = MinecraftClient.getInstance();
    private static final List<String> IDS = EntityFinderModules.definitions().stream().map(ModuleDefinition::id).toList();
    private static final int MAX_RENDER_TARGETS = 512, MAX_SCAN = 16_384, MAX_SEEN = 2_048;
    private static final Map<String, LinkedHashMap<Integer, Entity>> CACHES = new LinkedHashMap<>();
    private static final Map<String, Options> OPTIONS = new HashMap<>();
    private static final Map<String, LinkedHashSet<java.util.UUID>> SEEN = new HashMap<>();
    private static final Map<String, Long> LAST_NOTICE = new HashMap<>();
    private static final Map<String, Iterator<Entity>> SURVEYS = new HashMap<>();
    private static final List<FinderOverlay.Segment> BONES = new ArrayList<>();
    private static final List<Entity> POSED = new ArrayList<>();
    private static final Set<Integer> CAPTURED = new HashSet<>();
    private static AnalyzerCore core;
    private static Object world;
    private static boolean registered, skeletonFrame, poseWarning;
    private static long ticks;
    private static int rendered, lastSkeletons, lastBones, frameSkeletons;

    private EntityFinderRenderer() { }

    public static void register(AnalyzerCore analyzer) {
        core = analyzer;
        if (registered) return;
        registered = true;
        for (String id : IDS) { CACHES.put(id, new LinkedHashMap<>()); SEEN.put(id, new LinkedHashSet<>()); }
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
        WorldRenderEvents.START.register(context -> {
            BONES.clear(); POSED.clear(); CAPTURED.clear(); frameSkeletons = 0; rendered = 0;
            skeletonFrame = MC.world != null && MC.player != null && world == MC.world && core.isEnabled("skeleton-esp");
        });
        WorldRenderEvents.LAST.register(context -> render(context, false));
        WorldRenderEvents.END.register(context -> {
            render(context, true);
            lastSkeletons = frameSkeletons; lastBones = BONES.size();
            BONES.clear(); POSED.clear(); CAPTURED.clear(); skeletonFrame = false;
        });
    }

    private static void clear() {
        CACHES.values().forEach(Map::clear); SEEN.values().forEach(Set::clear);
        OPTIONS.clear(); LAST_NOTICE.clear(); SURVEYS.clear(); BONES.clear(); POSED.clear(); CAPTURED.clear();
        skeletonFrame = false; frameSkeletons = lastSkeletons = lastBones = rendered = 0;
    }

    private static void tick() {
        ticks++;
        if (world != MC.world) { clear(); world = MC.world; }
        if (MC.world == null || MC.player == null) { clear(); return; }
        int active = 0;
        for (String id : IDS) {
            Options options = readOptions(id);
            OPTIONS.put(id, options);
            var cache = CACHES.get(id);
            if (!options.enabled()) { cache.clear(); SEEN.get(id).clear(); SURVEYS.remove(id); continue; }
            active++;
            cache.values().removeIf(entity -> !EntityFinderRules.accepts(id, facts(entity), options));
            int max = limit(id);
            while (cache.size() > max) cache.remove(cache.keySet().iterator().next());
        }
        if (active == 0) { SURVEYS.clear(); return; }
        for (String id : IDS) {
            Options options = OPTIONS.get(id);
            if (!options.enabled()) continue;
            // A shared iterator would let a small-budget finder see only each large batch's
            // prefix forever. Independent cursors retain fair progress across loaded entities.
            Iterator<Entity> survey = SURVEYS.computeIfAbsent(id, ignored -> MC.world.getEntities().iterator());
            int budget = EntityFinderRules.scanAllowance(active, (int) core.getNumber(id, "scan-budget"));
            var cache = CACHES.get(id);
            int maximum = limit(id);
            try {
                for (int read = 0; read < Math.min(MAX_SCAN, budget); read++) {
                    if (!survey.hasNext()) { SURVEYS.remove(id); break; }
                    Entity entity = survey.next();
                    if (!EntityFinderRules.accepts(id, facts(entity), options)
                            || cache.containsKey(entity.getId()) || cache.size() >= maximum) continue;
                    cache.put(entity.getId(), entity);
                    if (!id.equals("skeleton-esp")) notice(id, entity);
                }
            } catch (ConcurrentModificationException | NoSuchElementException ignored) {
                // The native lookup changed between bounded batches. Restart this cursor next tick.
                SURVEYS.remove(id);
            }
        }
    }

    private static int limit(String id) { return Math.clamp((int) core.getNumber(id, "max-markers"), 1, MAX_RENDER_TARGETS); }

    private static Options readOptions(String id) {
        return new Options(core.isEnabled(id), core.getNumber(id, "range"), bool(id, "players", false),
                bool(id, "hostiles", false), bool(id, "passives", false), bool(id, "invisibles", false),
                bool(id, "self", false), bool(id, "item-frames", false), bool(id, "glow-frames", false),
                bool(id, "armor-stands", false), bool(id, "filled-frames-only", false), bool(id, "trader-llamas", false),
                bool(id, "zombie-villagers", false), bool(id, "adults-only", false));
    }

    private static boolean bool(String id, String setting, boolean fallback) {
        return core.definition(id).settings().stream().anyMatch(value -> value.id().equals(setting))
                ? core.getBoolean(id, setting) : fallback;
    }

    private static Facts facts(Entity entity) {
        Kind kind = entity instanceof GlowItemFrameEntity ? Kind.GLOW_ITEM_FRAME
                : entity instanceof ItemFrameEntity ? Kind.ITEM_FRAME
                : entity instanceof ArmorStandEntity ? Kind.ARMOR_STAND
                : entity instanceof DrownedEntity ? Kind.DROWNED
                : entity instanceof LlamaEntity llama ? (llama.isTrader() ? Kind.TRADER_LLAMA : Kind.LLAMA)
                : entity instanceof PillagerEntity ? Kind.PILLAGER
                : entity instanceof ZombieVillagerEntity ? Kind.ZOMBIE_VILLAGER
                : entity instanceof VillagerEntity ? Kind.VILLAGER
                : entity instanceof WanderingTraderEntity ? Kind.WANDERING_TRADER : Kind.OTHER;
        Group group = entity instanceof PlayerEntity ? Group.PLAYER : entity instanceof Monster ? Group.HOSTILE
                : entity instanceof MobEntity ? Group.PASSIVE : Group.OTHER;
        Entity camera = MC.getCameraEntity();
        boolean self = entity == MC.player || entity == camera && MC.options.getPerspective().isFirstPerson();
        return new Facts(kind, group, camera == null ? Double.POSITIVE_INFINITY : entity.squaredDistanceTo(camera),
                entity.isRemoved() || entity.getWorld() != MC.world, entity.isAlive(), entity.isSpectator(), self,
                MC.options.getPerspective().isFirstPerson(), entity.isInvisible(),
                entity instanceof DrownedEntity drowned && (drowned.getMainHandStack().isOf(Items.TRIDENT)
                        || drowned.getOffHandStack().isOf(Items.TRIDENT)),
                entity instanceof ItemFrameEntity frame && frame.getHeldItemStack().isEmpty(),
                entity instanceof LivingEntity living && living.isBaby());
    }

    private static void notice(String id, Entity entity) {
        if (MC.player == null) return;
        var seen = SEEN.get(id);
        if (!seen.add(entity.getUuid())) return;
        while (seen.size() > MAX_SEEN) seen.remove(seen.iterator().next());
        if (!core.getBoolean(id, "notify") || ticks - LAST_NOTICE.getOrDefault(id, -100L) < 100) return;
        LAST_NOTICE.put(id, ticks);
        MC.player.sendMessage(Text.literal(core.definition(id).name() + ": " + entity.getName().getString()
                + " at " + entity.getBlockX() + ", " + entity.getBlockY() + ", " + entity.getBlockZ()), false);
    }

    private static int color(String id) {
        int rgb = EspSettings.color(core.getChoice(id, "marker-color"), core.getNumber(id, "marker-red"),
                core.getNumber(id, "marker-green"), core.getNumber(id, "marker-blue"));
        return ((int) Math.round(core.getNumber(id, "opacity") * 2.55) << 24) | (rgb & 0xFFFFFF);
    }

    private static void render(WorldRenderContext context, boolean throughWalls) {
        if (MC.world == null || MC.player == null || world != MC.world) return;
        // The complete overlay family shares a 512-target frame cap, including posed bipeds.
        if (!throughWalls) rendered = frameSkeletons;
        float delta = context.tickCounter().getTickDelta(false);
        for (String id : IDS) {
            if (!core.isEnabled(id) || core.getBoolean(id, "through-walls") != throughWalls) continue;
            if (id.equals("skeleton-esp")) {
                FinderOverlay.drawLines(context, BONES, throughWalls, (float) core.getNumber(id, "line-width"));
                if (core.getBoolean(id, "tracers")) {
                    List<FinderOverlay.Shape> tracers = shapes(id, delta, true);
                    // Skeleton tracers accompany its actual bones; no entity boxes substitute for bones.
                    List<FinderOverlay.Segment> lines = new ArrayList<>();
                    Vec3d start = tracers.isEmpty() ? null : FinderOverlay.tracerStart(context, throughWalls,
                            core.getChoice(id, "tracer-origin"), id);
                    if (start != null) for (var shape : tracers) lines.add(new FinderOverlay.Segment(start, shape.box().getCenter(),
                            shape.color(), id, core.getChoice(id, "tracer-origin")));
                    FinderOverlay.drawLines(context, lines, throughWalls, (float) core.getNumber(id, "line-width"));
                }
            } else {
                FinderOverlay.draw(context, shapes(id, delta, core.getBoolean(id, "tracers")), throughWalls,
                        (float) core.getNumber(id, "line-width"), core.getChoice(id, "tracer-origin"), id);
            }
        }
    }

    private static List<FinderOverlay.Shape> shapes(String id, float delta, boolean tracers) {
        List<FinderOverlay.Shape> shapes = new ArrayList<>();
        boolean skeleton = id.equals("skeleton-esp");
        Options options = readOptions(id);
        int color = color(id);
        Iterable<Entity> targets = skeleton ? POSED : CACHES.get(id).values();
        for (Entity entity : targets) {
            if ((!skeleton && rendered >= MAX_RENDER_TARGETS) || shapes.size() >= limit(id)) break;
            if (!EntityFinderRules.accepts(id, facts(entity), options)) continue;
            if (skeleton && !CAPTURED.contains(entity.getId())) continue;
            Vec3d position = entity.getLerpedPos(delta);
            Box box = entity.getBoundingBox().offset(position.subtract(entity.getPos())).expand(.015);
            shapes.add(new FinderOverlay.Shape(box, color, tracers));
            if (!skeleton) rendered++;
        }
        return shapes;
    }

    public static boolean acceptingSkeletons() { return skeletonFrame; }

    /** Observe joints only after native animation has set the actual body/limb transforms. */
    public static void captureSkeleton(LivingEntity entity, EntityModel<?> model, MatrixStack matrices) {
        if (!skeletonFrame || entity == null || !(model instanceof BipedEntityModel<?> biped)
                || world != MC.world || core == null || !core.isEnabled("skeleton-esp")
                || frameSkeletons >= limit("skeleton-esp") || frameSkeletons >= MAX_RENDER_TARGETS
                || CAPTURED.contains(entity.getId())
                || !EntityFinderRules.accepts("skeleton-esp", facts(entity), readOptions("skeleton-esp"))) return;
        Vec3d camera = MC.gameRenderer.getCamera().getPos();
        List<FinderOverlay.Segment> captured = new ArrayList<>(10);
        int color = color("skeleton-esp");
        matrices.push();
        try {
            model.getRootPart().rotate(matrices);
            bone(captured, biped.body, matrices, camera, 0, 0, 0, 0, 12, 0, color);
            bone(captured, biped.leftArm, matrices, camera, 0, 0, 0, 0, 12, 0, color);
            bone(captured, biped.rightArm, matrices, camera, 0, 0, 0, 0, 12, 0, color);
            bone(captured, biped.leftLeg, matrices, camera, 0, 0, 0, 0, 12, 0, color);
            bone(captured, biped.rightLeg, matrices, camera, 0, 0, 0, 0, 12, 0, color);
            connect(captured, anchor(biped.leftArm, matrices, camera, 0, 0, 0), anchor(biped.rightArm, matrices, camera, 0, 0, 0), color);
            connect(captured, anchor(biped.leftLeg, matrices, camera, 0, 0, 0), anchor(biped.rightLeg, matrices, camera, 0, 0, 0), color);
            if (core.getBoolean("skeleton-esp", "head")) bone(captured, biped.head, matrices, camera, 0, 0, 0, 0, -4, -2, color);
            if (!captured.isEmpty()) {
                BONES.addAll(captured); CAPTURED.add(entity.getId()); POSED.add(entity); frameSkeletons++;
                notice("skeleton-esp", entity);
            }
        } catch (RuntimeException exception) {
            if (!poseWarning) AnalyzerClient.LOGGER.warn("Could not observe an entity's skeleton pose; ordinary rendering is retained", exception);
            poseWarning = true;
        } finally { matrices.pop(); }
    }

    private static void bone(List<FinderOverlay.Segment> output, ModelPart part, MatrixStack matrices, Vec3d camera,
                             float x1, float y1, float z1, float x2, float y2, float z2, int color) {
        if (!part.visible || part.hidden) return;
        connect(output, anchor(part, matrices, camera, x1, y1, z1), anchor(part, matrices, camera, x2, y2, z2), color);
    }

    private static Vec3d anchor(ModelPart part, MatrixStack matrices, Vec3d camera, float x, float y, float z) {
        matrices.push();
        try {
            part.rotate(matrices);
            Vector3f point = matrices.peek().getPositionMatrix().transformPosition(x / 16, y / 16, z / 16, new Vector3f());
            return new Vec3d(point.x, point.y, point.z).add(camera);
        } finally { matrices.pop(); }
    }

    private static void connect(List<FinderOverlay.Segment> output, Vec3d from, Vec3d to, int color) {
        if (Double.isFinite(from.x + from.y + from.z + to.x + to.y + to.z) && from.squaredDistanceTo(to) < 64)
            output.add(new FinderOverlay.Segment(from, to, color));
    }

    public static int count(String id) { return id.equals("skeleton-esp") ? lastSkeletons : CACHES.getOrDefault(id, new LinkedHashMap<>()).size(); }
    public static int boneCount() { return lastBones; }
    public static String status(String id) {
        if (MC.world == null) return "Join a world to survey received entities";
        if (id.equals("skeleton-esp")) return lastSkeletons + " posed bipeds · " + lastBones + " bone segments";
        return count(id) + " received matches · bounded local survey";
    }
}
