package dev.analyzer.render;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.analyzer.core.AnalyzerCore;
import dev.analyzer.ui.GuiDraw;
import dev.analyzer.ui.Palette;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.*;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.chunk.ChunkStatus;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import java.util.*;

/** Original cosmetic effects. No block/entity flags, movement, packets or world state are changed. */
public final class VisualEffects {
    private static final MinecraftClient CLIENT = MinecraftClient.getInstance();
    private static final int MAX_EFFECTS = 768, MAX_TRACKED = 2048, MAX_PATHS = 32, MAX_POINTS = 96;
    private static final int MINT = 0xFF79F8CC, CYAN = 0xFF66DFF7, PINK = 0xFFFF7188, GOLD = 0xFFFFCF78, VIOLET = 0xFFB594FF;
    private static final int[] EDGES = {0,1,1,5,5,4,4,0,2,3,3,7,7,6,6,2,0,2,1,3,5,7,4,6};
    private static final int[] FACES = {0,1,3,2,5,4,6,7,4,0,2,6,1,5,7,3,4,5,1,0,2,3,7,6};
    private static final Random RANDOM = new Random();
    private static final Deque<Effect> effects = new ArrayDeque<>();
    private static final Map<Integer, LivingSnapshot> living = new HashMap<>();
    private static final Map<Integer, Deque<TrailPoint>> projectilePaths = new LinkedHashMap<>();
    private static final Deque<TrailPoint> playerPath = new ArrayDeque<>();
    private static final Map<String, Integer> emitted = new HashMap<>();
    private static final Map<Integer, Long> deaths = new HashMap<>();
    private static final Map<Integer, Long> hurtEvents = new HashMap<>();
    private static final BufferAllocator TEXT_ALLOCATOR = new BufferAllocator(64 * 1024);
    private static final VertexConsumerProvider.Immediate TEXT_CONSUMERS = VertexConsumerProvider.immediate(TEXT_ALLOCATOR);
    private static AnalyzerCore core;
    private static ClientWorld world;
    private static Vec3d previousPosition;
    private static boolean previousGround;
    private static double previousVelocityY, stepDistance;
    private static long hurtAt, hitAt, attackAt;
    private static int attackId = -1, ticks, placements, breaks, itemCount, tagCount;
    private static Mining mining;
    private static List<BlockPos> holes = List.of();
    private static int previousHoleRadius;
    private static boolean holesEnabled, registered;
    private static volatile FrameDiagnostics frame = new FrameDiagnostics(0, 0, 0, 0);

    private VisualEffects() { }

    @SuppressWarnings("deprecation")
    public static void register(AnalyzerCore state) {
        if (registered) return;
        registered = true; core = state;
        ClientTickEvents.END_CLIENT_TICK.register(VisualEffects::tick);
        WorldRenderEvents.LAST.register(context -> render(context, false));
        WorldRenderEvents.END.register(context -> render(context, true));
        HudRenderCallback.EVENT.register(VisualEffects::hud);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> TEXT_ALLOCATOR.close());
    }

    public static int emitted(String moduleId) { return emitted.getOrDefault(moduleId, 0); }
    public record Diagnostics(int placements, int breaks, int activeEffects, int trailPoints, int holes, int items, int nametags) { }
    /** Geometry/text submitted by the last complete world frame, summed across depth passes. */
    public record FrameDiagnostics(int boxes, int segments, int labels, int passes) { }
    public static FrameDiagnostics frameDiagnostics() { return frame; }
    public static Diagnostics diagnostics() {
        return new Diagnostics(placements, breaks, effects.size(), playerPath.size(), holes.size(), itemCount, tagCount);
    }
    public static String status(String moduleId) {
        if (CLIENT.world == null) return "Join a world to see the effect";
        return switch (moduleId) {
            case "hole-esp" -> holes.size() + " loaded resistant holes";
            case "item-esp" -> itemCount + " dropped items in range";
            case "nametags" -> tagCount + " loaded name tags";
            case "place-preview" -> CLIENT.player != null && CLIENT.player.getMainHandStack().getItem() instanceof BlockItem
                    ? "Aim at a block face to preview placement" : "Hold a block in your main hand";
            case "trajectories" -> "Hold a bow, trident, snowball, egg or pearl";
            case "player-aura", "block-highlight", "target-esp" -> "Live world overlay";
            default -> emitted(moduleId) + " events this world";
        };
    }

    private static boolean enabled(String id) { return core != null && core.isEnabled(id); }
    private static boolean inWorld() {
        if (core == null || CLIENT.world == null) return false;
        if (world != CLIENT.world) reset(CLIENT.world);
        return true;
    }
    private static double value(String id, String setting) { return core.getNumber(id, setting); }
    private static void mark(String id) { emitted.merge(id, 1, (a, b) -> a == Integer.MAX_VALUE ? a : a + b); }
    private static double duration(String id) { return value(id, "duration"); }
    private static long now() { return System.nanoTime(); }

    /** Called only after an accepted block-item use changed the predicted client block state. */
    public static void onPlaced(BlockPos position, BlockState after) {
        if (!inWorld() || after.isAir()) return;
        placements++;
        if (!enabled("place-animation")) return;
        Box box = blockBounds(position, after);
        add(new Effect("place-animation", Kind.PLACE, box.getCenter(), Vec3d.ZERO, null, box, now(), duration("place-animation"), 1, MINT,
                core.getChoice("place-animation", "style")));
        mark("place-animation");
    }

    /** Successful local breaking produces a visual ghost of the old bounds; the real block stays broken. */
    public static void onBroken(BlockPos position, BlockState before) {
        if (!inWorld() || before.isAir()) return;
        breaks++; mining = null;
        if (!enabled("break-animation")) return;
        Box box = blockBounds(position, before);
        int color = before.isOf(Blocks.OBSIDIAN) ? VIOLET : GOLD;
        add(new Effect("break-animation", Kind.BREAK, box.getCenter(), Vec3d.ZERO, null, box, now(), duration("break-animation"), 1, color, ""));
        burst("break-animation", box.getCenter(), (int) value("break-animation", "debris"), color, 1.15);
        mark("break-animation");
    }

    public static void onMining(BlockPos position, float progress, boolean active) {
        if (core == null || !active || CLIENT.world == null) { mining = null; return; }
        mining = new Mining(position.toImmutable(), Math.clamp(progress, 0, 1), now());
    }

    /** Attack intent is retained briefly; hit markers still wait for actual hurt/health transitions. */
    public static void onAttack(Entity target) { if (inWorld() && target != null) { attackId = target.getId(); attackAt = now(); } }

    public static void onPickup(Entity item, Entity collector) {
        if (!inWorld() || !enabled("pickup-streaks") || item == null || collector == null) return;
        add(new Effect("pickup-streaks", Kind.PICKUP, item.getPos().add(0, 0.15, 0), Vec3d.ZERO,
                collector.getPos().add(0, collector.getHeight() * 0.55, 0), null, now(), duration("pickup-streaks"), 1, GOLD, ""));
        mark("pickup-streaks");
    }

    public static void onExplosion(Vec3d center) {
        if (!inWorld() || !enabled("explosion-effects")) return;
        ring("explosion-effects", center, value("explosion-effects", "size"), GOLD);
        burst("explosion-effects", center, 32, GOLD, 2.1);
        mark("explosion-effects");
    }

    public static void onEntityStatus(Entity entity, byte status) {
        if (!inWorld() || entity == null) return;
        if (status == 35 && enabled("totem-pops")) {
            Vec3d position = entity.getPos().add(0, entity.getHeight() * 0.6, 0);
            ring("totem-pops", entity.getPos().add(0, 0.03, 0), 1.8, GOLD);
            burst("totem-pops", position, (int) value("totem-pops", "count"), GOLD, 1.7);
            label("totem-pops", position.add(0, 0.6, 0), "POP", GOLD);
            mark("totem-pops");
        } else if (status == 3 && entity instanceof LivingEntity) onDeath(entity);
    }

    private static void onDeath(Entity entity) {
        long time = now();
        if (deaths.size() >= MAX_TRACKED && !deaths.containsKey(entity.getId())) deaths.remove(deaths.keySet().iterator().next());
        Long previous = deaths.put(entity.getId(), time);
        if (previous != null && time - previous < 2_000_000_000L || !enabled("death-effects")) return;
        burst("death-effects", entity.getPos().add(0, entity.getHeight() * 0.5, 0), (int) value("death-effects", "count"), VIOLET, 1.7);
        ring("death-effects", entity.getPos().add(0, 0.03, 0), 1.3, VIOLET);
        mark("death-effects");
    }

    private static void reset(ClientWorld next) {
        world = next; effects.clear(); living.clear(); projectilePaths.clear(); playerPath.clear(); emitted.clear(); deaths.clear(); hurtEvents.clear();
        holes = List.of(); mining = null; previousPosition = null; previousGround = false; previousVelocityY = stepDistance = 0;
        hurtAt = hitAt = attackAt = 0; attackId = -1; ticks = placements = breaks = itemCount = tagCount = 0; holesEnabled = false;
        frame = new FrameDiagnostics(0, 0, 0, 0);
    }

    public static void tick(MinecraftClient client) {
        if (core == null) return;
        if (client.world != world) reset(client.world);
        if (client.world == null || client.player == null) return;
        long time = now(); ticks++;
        effects.removeIf(effect -> effect.age(time) >= 1);
        deaths.entrySet().removeIf(entry -> time - entry.getValue() > 5_000_000_000L);
        Set<Integer> seen = new HashSet<>();
        int tracked = 0;
        for (Entity entity : client.world.getEntities()) {
            if (++tracked > MAX_TRACKED) break;
            if (entity instanceof LivingEntity target) {
                seen.add(entity.getId());
                LivingSnapshot previous = living.put(entity.getId(), new LivingSnapshot(target.getHealth(), target.hurtTime));
                if (previous != null) {
                    float damage = previous.health - target.getHealth();
                    boolean hurt = damage > 0.01f || target.hurtTime > previous.hurtTime && target.hurtTime >= 8;
                    if (hurt) onHurt(target, damage, time);
                    if (previous.health > 0 && target.getHealth() <= 0) onDeath(target);
                }
            }
            if (entity instanceof ProjectileEntity && enabled("projectile-trails") && !entity.isRemoved()) {
                Deque<TrailPoint> path = projectilePaths.get(entity.getId());
                if (path == null && projectilePaths.size() < MAX_PATHS) {
                    path = new ArrayDeque<>(); projectilePaths.put(entity.getId(), path);
                }
                if (path != null) append(path, entity.getPos(), time);
            }
        }
        living.keySet().retainAll(seen);
        hurtEvents.keySet().retainAll(seen);
        for (Deque<TrailPoint> path : projectilePaths.values()) expire(path, time, enabled("projectile-trails") ? duration("projectile-trails") : 0);
        projectilePaths.values().removeIf(Deque::isEmpty);
        Vec3d position = client.player.getPos();
        boolean grounded = client.player.isOnGround();
        if (previousPosition != null) {
            double distance = position.distanceTo(previousPosition);
            if (enabled("teleport-effects") && distance >= value("teleport-effects", "threshold")) {
                ring("teleport-effects", previousPosition.add(0, 0.03, 0), 1.5, VIOLET);
                ring("teleport-effects", position.add(0, 0.03, 0), 1.5, CYAN);
                burst("teleport-effects", position.add(0, 0.9, 0), 20, CYAN, 1.3); mark("teleport-effects");
            } else if (distance < 4) {
                if (enabled("trails") && distance > 0.025) append(playerPath, position.add(0, 0.15, 0), time);
                if (grounded && enabled("footsteps")) stepDistance += Math.sqrt(Math.pow(position.x - previousPosition.x, 2) + Math.pow(position.z - previousPosition.z, 2));
                if (enabled("footsteps") && grounded && stepDistance >= value("footsteps", "spacing")) {
                    ring("footsteps", position.add(0, 0.025, 0), 0.32, CYAN); mark("footsteps"); stepDistance = 0;
                }
            }
            if (previousGround && !grounded && client.player.getVelocity().y > 0.05 && enabled("jump-circles")) {
                ring("jump-circles", previousPosition.add(0, 0.025, 0), value("jump-circles", "size"), MINT); mark("jump-circles");
            }
            if (!previousGround && grounded && previousVelocityY < -0.22 && enabled("landing-impact")) {
                ring("landing-impact", position.add(0, 0.025, 0), value("landing-impact", "size"), GOLD);
                burst("landing-impact", position.add(0, 0.08, 0), 12, GOLD, 0.7); mark("landing-impact");
            }
        }
        previousPosition = position; previousGround = grounded; previousVelocityY = client.player.getVelocity().y;
        if (!enabled("footsteps")) stepDistance = 0;
        expire(playerPath, time, enabled("trails") ? duration("trails") : 0);
        boolean holeEnabled = enabled("hole-esp");
        int radius = holeEnabled ? (int) value("hole-esp", "radius") : 0;
        if (holeEnabled && (!holesEnabled || radius != previousHoleRadius || ticks % 10 == 0)) scanHoles(radius);
        if (!holeEnabled) holes = List.of();
        holesEnabled = holeEnabled; previousHoleRadius = radius;
    }

    private static void onHurt(LivingEntity target, float damage, long time) {
        Vec3d position = target.getPos().add(0, target.getHeight() * 0.6, 0);
        Long previousHurt = hurtEvents.get(target.getId());
        boolean firstHurt = previousHurt == null || time - previousHurt > 150_000_000L;
        if (firstHurt) hurtEvents.put(target.getId(), time);
        if (firstHurt && enabled("hit-particles")) { burst("hit-particles", position, (int) value("hit-particles", "count"), PINK, 1.3); mark("hit-particles"); }
        if (enabled("damage-numbers") && damage > 0.01f) {
            label("damage-numbers", target.getPos().add(0, target.getHeight() + 0.25, 0),
                    "-" + String.format(Locale.ROOT, "%.1f", damage), PINK); mark("damage-numbers");
        }
        if (firstHurt && target.getId() == attackId && time - attackAt < 1_500_000_000L && enabled("hit-markers")) { hitAt = time; mark("hit-markers"); }
        if (target == CLIENT.player && damage > 0.01f && enabled("health-pulse")) { hurtAt = time; mark("health-pulse"); }
    }

    private static void append(Deque<TrailPoint> path, Vec3d position, long time) {
        if (!path.isEmpty() && path.getLast().position.squaredDistanceTo(position) < 0.0025) return;
        path.addLast(new TrailPoint(position, time)); while (path.size() > MAX_POINTS) path.removeFirst();
    }
    private static void expire(Deque<TrailPoint> path, long time, double seconds) {
        while (!path.isEmpty() && time - path.getFirst().time > seconds * 1_000_000_000d) path.removeFirst();
    }
    private static void add(Effect effect) { while (effects.size() >= MAX_EFFECTS) effects.removeFirst(); effects.addLast(effect); }
    private static void ring(String module, Vec3d position, double size, int color) {
        add(new Effect(module, Kind.RING, position, Vec3d.ZERO, null, null, now(), duration(module), size, color, ""));
    }
    private static void label(String module, Vec3d position, String text, int color) {
        add(new Effect(module, Kind.LABEL, position, Vec3d.ZERO, null, null, now(), duration(module), 1, color, text));
    }
    private static void burst(String module, Vec3d position, int count, int color, double speed) {
        long time = now();
        for (int i = 0; i < count; i++) {
            Vec3d velocity = new Vec3d((RANDOM.nextDouble() - 0.5) * speed * 2,
                    (0.25 + RANDOM.nextDouble()) * speed, (RANDOM.nextDouble() - 0.5) * speed * 2);
            add(new Effect(module, Kind.SPARK, position, velocity, null, null, time, duration(module),
                    0.025 + RANDOM.nextDouble() * 0.045, color, ""));
        }
    }

    private static Box blockBounds(BlockPos position, BlockState state) {
        var shape = state.getOutlineShape(CLIENT.world, position);
        return (shape.isEmpty() ? new Box(0, 0, 0, 1, 1, 1) : shape.getBoundingBox()).offset(position).expand(0.004);
    }

    private static void scanHoles(int radius) {
        List<BlockPos> found = new ArrayList<>(); BlockPos center = CLIENT.player.getBlockPos();
        for (int x = -radius; x <= radius && found.size() < 128; x++) for (int z = -radius; z <= radius && found.size() < 128; z++) {
            if (x * x + z * z > radius * radius) continue;
            for (int y = -3; y <= 3 && found.size() < 128; y++) {
                BlockPos pos = center.add(x, y, z);
                if (CLIENT.world.getChunkManager().getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false) == null) continue;
                if (!CLIENT.world.getBlockState(pos).isAir() || !CLIENT.world.getBlockState(pos.up()).isAir()) continue;
                boolean safe = resistant(CLIENT.world.getBlockState(pos.down()));
                for (Direction direction : Direction.Type.HORIZONTAL) safe &= resistant(CLIENT.world.getBlockState(pos.offset(direction)));
                if (safe) found.add(pos.toImmutable());
            }
        }
        holes = List.copyOf(found);
    }
    private static boolean resistant(BlockState state) {
        return state.isOf(Blocks.BEDROCK) || state.isOf(Blocks.OBSIDIAN) || state.isOf(Blocks.CRYING_OBSIDIAN)
                || state.isOf(Blocks.ENDER_CHEST) || state.isOf(Blocks.RESPAWN_ANCHOR);
    }

    private static void render(WorldRenderContext context, boolean throughPass) {
        if (core == null || CLIENT.world == null || CLIENT.player == null) return;
        if (world != CLIENT.world) reset(CLIENT.world);
        if (!throughPass) frame = new FrameDiagnostics(0, 0, 0, 0);
        long time = now(); Scene scene = new Scene();
        transientScene(scene, time, throughPass);
        staticScene(scene, context, throughPass, time);
        if (scene.boxes.isEmpty() && scene.lines.isEmpty() && scene.labels.isEmpty()) return;
        SavedState previous = new SavedState();
        if (throughPass) RenderSystem.getModelViewStack().pushMatrix();
        try {
            if (throughPass) RenderSystem.getModelViewStack().mul(context.positionMatrix());
            RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.disableCull(); RenderSystem.depthMask(false);
            if (throughPass) RenderSystem.disableDepthTest(); else RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL); RenderSystem.setShaderColor(1, 1, 1, 1); RenderSystem.setShaderFog(Fog.DUMMY);
            drawScene(scene, context, throughPass);
        } finally {
            if (throughPass) RenderSystem.getModelViewStack().popMatrix();
            previous.restore();
        }
    }

    private static void transientScene(Scene scene, long time, boolean throughPass) {
        for (Effect effect : effects) {
            if (!enabled(effect.module)) continue;
            // A placed-block ghost must remain visible while easing inside its newly solid block.
            // Other particles/ripples respect normal world depth.
            if ((effect.kind == Kind.PLACE) != throughPass) continue;
            double age = effect.age(time); if (age >= 1) continue;
            double seconds = age * effect.duration, fade = (1 - age) * (1 - age);
            int color = Palette.alpha(effect.color, (int) (235 * fade));
            switch (effect.kind) {
                case PLACE -> {
                    double eased = 1 - Math.pow(1 - age, 3), scale = 0.08 + 0.92 * eased;
                    Vec3d center = effect.position;
                    if (effect.text.equals("Rise")) center = center.add(0, -(1 - eased) * 0.55, 0);
                    if (effect.text.equals("Pop")) scale = 1 + Math.pow(1 - age, 3) * Math.sin(age * Math.PI * 5) * 0.5 - Math.pow(1 - age, 4);
                    if (effect.text.equals("Bloom")) scale = 0.15 + eased * 0.85 + Math.sin(age * Math.PI) * 0.12;
                    Box ghost = scaled(effect.box, center, Math.max(0.05, scale));
                    scene.boxes.add(new ColoredBox(ghost, Palette.alpha(effect.color, (int) (42 * fade))));
                    scene.box(ghost, color, 2); scene.circle(effect.box.getCenter().add(0, -effect.box.getLengthY() / 2 + 0.01, 0), 0.25 + age * 0.65, color, 1.5f);
                }
                case BREAK -> {
                    Box ghost = scaled(effect.box, effect.position.add(0, age * 0.18, 0), 1 - age * age);
                    scene.boxes.add(new ColoredBox(ghost, Palette.alpha(effect.color, (int) (34 * fade)))); scene.box(ghost, color, 1.8f);
                }
                case SPARK -> {
                    Vec3d pos = effect.position.add(effect.velocity.multiply(seconds)).add(0, -1.2 * seconds * seconds, 0);
                    double size = effect.size * (1 - age * 0.65);
                    scene.boxes.add(new ColoredBox(new Box(pos.x - size, pos.y - size, pos.z - size, pos.x + size, pos.y + size, pos.z + size), color));
                }
                case RING -> {
                    double radius = effect.size * (0.1 + Math.sqrt(age) * 0.9);
                    scene.circle(effect.position, radius, color, 2);
                    scene.circle(effect.position.add(0, 0.01, 0), radius * 0.9, Palette.alpha(effect.color, (int) (90 * fade)), 4);
                }
                case LABEL -> {
                    float scale = effect.module.equals("damage-numbers") ? (float) value("damage-numbers", "scale") : 1;
                    scene.labels.add(new Tag(effect.position.add(0, age * 0.8, 0), effect.text, color, scale));
                }
                case PICKUP -> {
                    Vec3d previous = curve(effect.position, effect.end, age);
                    for (int i = 1; i <= 12; i++) {
                        double progress = Math.clamp(age - i * 0.025, 0, 1);
                        Vec3d next = curve(effect.position, effect.end, progress);
                        scene.line(previous, next, Palette.alpha(effect.color, (int) (fade * (1 - i / 14.0) * 240)), (float) value("pickup-streaks", "line-width")); previous = next;
                    }
                }
            }
        }
        if (!throughPass && enabled("trails")) scene.path(playerPath, time, duration("trails"), MINT, (float) value("trails", "line-width"));
        if (!throughPass && enabled("projectile-trails")) for (Deque<TrailPoint> path : projectilePaths.values()) scene.path(path, time,
                duration("projectile-trails"), CYAN, (float) value("projectile-trails", "line-width"));
        if (throughPass && enabled("break-animation") && core.getBoolean("break-animation", "progress") && mining != null && time - mining.time < 300_000_000L) {
            BlockState state = CLIENT.world.getBlockState(mining.position);
            if (!state.isAir()) {
                Box full = blockBounds(mining.position, state);
                Box inner = scaled(full, full.getCenter(), 0.15 + mining.progress * 0.85);
                scene.box(full, 0xADFFCF78, 1.5f); scene.box(inner, 0xEB79F8CC, 2.5f);
                scene.boxes.add(new ColoredBox(inner, Palette.alpha(GOLD, (int) (20 + mining.progress * 50))));
            }
        }
    }

    private static void staticScene(Scene scene, WorldRenderContext context, boolean throughPass, long time) {
        double clock = time / 1_000_000_000.0;
        float delta = context.tickCounter().getTickDelta(false);
        if (!throughPass) {
            if (enabled("place-preview") && !core.getBoolean("place-preview", "through-walls")) preview(scene);
            if (enabled("block-highlight") && CLIENT.crosshairTarget instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
                int alpha = core.getBoolean("block-highlight", "pulse") ? (int) (150 + Math.sin(clock * 4) * 65) : 215;
                scene.box(blockBounds(hit.getBlockPos(), CLIENT.world.getBlockState(hit.getBlockPos())), Palette.alpha(CYAN, alpha), (float) value("block-highlight", "line-width"));
            }
            if (enabled("player-aura")) {
                Vec3d pos = CLIENT.player.getLerpedPos(delta); double radius = value("player-aura", "size"), angle = clock * value("player-aura", "speed");
                scene.circle(pos.add(0, 0.035, 0), radius, 0xC079F8CC, 2);
                scene.circle(pos.add(0, 0.8 + Math.sin(angle) * 0.35, 0), radius * 0.75, 0xA5B594FF, 1.5f);
                for (int i = 0; i < 6; i++) {
                    double a = angle + i * Math.PI / 3;
                    Vec3d p = pos.add(Math.cos(a) * radius, 0.6 + Math.sin(a * 2) * 0.4, Math.sin(a) * radius);
                    scene.boxes.add(new ColoredBox(new Box(p.x - 0.035, p.y - 0.035, p.z - 0.035, p.x + 0.035, p.y + 0.035, p.z + 0.035), 0xDC79F8CC));
                }
            }
            if (enabled("trajectories")) trajectory(scene);
        } else if (enabled("place-preview") && core.getBoolean("place-preview", "through-walls")) preview(scene);
        if (enabled("hole-esp") && core.getBoolean("hole-esp", "through-walls") == throughPass) {
            for (BlockPos pos : holes) { Box box = new Box(pos.getX() + 0.03, pos.getY() + 0.015, pos.getZ() + 0.03,
                    pos.getX() + 0.97, pos.getY() + 0.15, pos.getZ() + 0.97); scene.box(box, 0xCF79F8CC, 2); scene.boxes.add(new ColoredBox(box, 0x2079F8CC)); }
        }
        if (enabled("target-esp") && core.getBoolean("target-esp", "through-walls") == throughPass) {
            Entity target = CLIENT.crosshairTarget instanceof EntityHitResult hit ? hit.getEntity()
                    : time - attackAt < value("target-esp", "linger") * 1_000_000_000d ? CLIENT.world.getEntityById(attackId) : null;
            if (target != null && target.isAlive()) {
                Vec3d pos = target.getLerpedPos(delta); double r = target.getWidth() * 0.7 + 0.2;
                scene.circle(pos.add(0, 0.06, 0), r, 0xE5FF7188, 2);
                scene.circle(pos.add(0, target.getHeight() * (0.5 + Math.sin(clock * 2.5) * 0.45), 0), r, 0xC3B594FF, 2);
            }
        }
        if (enabled("item-esp") && core.getBoolean("item-esp", "through-walls") == throughPass) {
            int found = 0; double range = value("item-esp", "range");
            for (Entity entity : CLIENT.world.getEntities()) {
                if (!(entity instanceof ItemEntity item) || item.isRemoved() || item.squaredDistanceTo(CLIENT.player) > range * range) continue;
                Vec3d pos = item.getLerpedPos(delta); scene.circle(pos.add(0, 0.04, 0), 0.32, 0xE8FFCF78, 2);
                scene.line(pos, pos.add(0, 0.7 + Math.sin(clock * 3) * 0.1, 0), 0x91FFCF78, 1.5f);
                if (core.getBoolean("item-esp", "labels") && found < 48) scene.labels.add(new Tag(pos.add(0, 0.8, 0),
                        shortName(item.getStack().getName().getString()) + (item.getStack().getCount() > 1 ? " x" + item.getStack().getCount() : ""), GOLD, 0.8f));
                if (++found >= 256) break;
            } itemCount = found;
        }
        if (enabled("nametags") && core.getBoolean("nametags", "through-walls") == throughPass) {
            int found = 0; double range = value("nametags", "range");
            for (Entity entity : CLIENT.world.getEntities()) {
                if (!(entity instanceof LivingEntity target) || target == CLIENT.player || !target.isAlive() || target.squaredDistanceTo(CLIENT.player) > range * range) continue;
                boolean player = target instanceof PlayerEntity;
                if (player ? !core.getBoolean("nametags", "players") : !core.getBoolean("nametags", "mobs")) continue;
                if (context.frustum() != null && !context.frustum().isVisible(target.getBoundingBox().expand(0.7))) continue;
                scene.labels.add(new Tag(target.getLerpedPos(delta).add(0, target.getHeight() + 0.35, 0),
                        shortName(target.getName().getString()) + "  " + Math.round(target.getHealth()) + " HP  "
                                + Math.round(Math.sqrt(target.squaredDistanceTo(CLIENT.player))) + "m", player ? CYAN : MINT,
                        (float) value("nametags", "scale")));
                if (++found >= 48) break;
            } tagCount = found;
        }
    }

    private static String shortName(String name) { return name.length() > 32 ? name.substring(0, 29) + "…" : name; }
    private static void preview(Scene scene) {
        if (!(CLIENT.crosshairTarget instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK
                || !(CLIENT.player.getMainHandStack().getItem() instanceof BlockItem)) return;
        ItemPlacementContext placement = new ItemPlacementContext(CLIENT.player, Hand.MAIN_HAND, CLIENT.player.getMainHandStack(), hit);
        BlockPos pos = placement.getBlockPos(); boolean valid = placement.canPlace();
        Box box = new Box(pos).expand(0.002); int color = valid ? MINT : PINK;
        scene.boxes.add(new ColoredBox(box, Palette.alpha(color, (int) (value("place-preview", "opacity") * 150)))); scene.box(box, Palette.alpha(color, 220), 2);
    }
    private static void trajectory(Scene scene) {
        ItemStack stack = CLIENT.player.isUsingItem() ? CLIENT.player.getActiveItem() : CLIENT.player.getMainHandStack();
        Item item = stack.getItem(); double speed, gravity;
        if (item instanceof BowItem) { speed = CLIENT.player.isUsingItem() ? Math.max(0.1, BowItem.getPullProgress(CLIENT.player.getItemUseTime())) * 3 : 3; gravity = 0.05; }
        else if (item instanceof TridentItem) { speed = 2.5; gravity = 0.05; }
        else if (item == Items.SNOWBALL || item == Items.EGG || item == Items.ENDER_PEARL) { speed = 1.5; gravity = 0.03; }
        else return;
        Vec3d position = CLIENT.player.getEyePos().add(0, -0.1, 0);
        Vec3d velocity = CLIENT.player.getRotationVector().multiply(speed).add(CLIENT.player.getVelocity().x,
                CLIENT.player.isOnGround() ? 0 : CLIENT.player.getVelocity().y, CLIENT.player.getVelocity().z);
        int steps = (int) value("trajectories", "steps"); float width = (float) value("trajectories", "line-width");
        for (int i = 0; i < steps; i++) {
            Vec3d next = position.add(velocity);
            if (CLIENT.world.getChunkManager().getChunk((int) Math.floor(next.x) >> 4, (int) Math.floor(next.z) >> 4, ChunkStatus.FULL, false) == null) break;
            var hit = CLIENT.world.raycast(new RaycastContext(position, next, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, CLIENT.player));
            if (hit.getType() == HitResult.Type.BLOCK) { scene.line(position, hit.getPos(), 0xE166DFF7, width); scene.circle(hit.getPos().add(0, 0.01, 0), 0.2, GOLD, 2); break; }
            scene.line(position, next, Palette.alpha(CYAN, Math.max(70, 230 - i)), width);
            position = next; velocity = velocity.multiply(0.99).add(0, -gravity, 0);
        }
    }

    private static Vec3d curve(Vec3d start, Vec3d end, double progress) {
        return start.lerp(end, progress).add(0, Math.sin(progress * Math.PI) * 0.7, 0);
    }
    private static Box scaled(Box box, Vec3d center, double scale) {
        double x = box.getLengthX() * scale / 2, y = box.getLengthY() * scale / 2, z = box.getLengthZ() * scale / 2;
        return new Box(center.x - x, center.y - y, center.z - z, center.x + x, center.y + y, center.z + z);
    }

    private static void drawScene(Scene scene, WorldRenderContext context, boolean throughWalls) {
        Vec3d camera = context.camera().getPos();
        if (!scene.boxes.isEmpty()) {
            RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
            BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            for (ColoredBox colored : scene.boxes) {
                Box box = colored.box.offset(camera.negate());
                for (int corner : FACES) buffer.vertex((float) ((corner & 1) == 0 ? box.minX : box.maxX),
                        (float) ((corner & 2) == 0 ? box.minY : box.maxY), (float) ((corner & 4) == 0 ? box.minZ : box.maxZ)).color(colored.color);
            } BufferRenderer.drawWithGlobalProgram(buffer.end());
        }
        if (!scene.lines.isEmpty()) {
            RenderSystem.setShader(ShaderProgramKeys.RENDERTYPE_LINES);
            Map<Float, List<Segment>> widths = new LinkedHashMap<>();
            for (Segment line : scene.lines) widths.computeIfAbsent(line.width, ignored -> new ArrayList<>()).add(line);
            for (var entry : widths.entrySet()) {
                RenderSystem.lineWidth(entry.getKey());
                BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.LINES, VertexFormats.LINES);
                for (Segment line : entry.getValue()) {
                    Vec3d start = line.start.subtract(camera), end = line.end.subtract(camera), normal = end.subtract(start).normalize();
                    buffer.vertex((float) start.x, (float) start.y, (float) start.z).color(line.color).normal((float) normal.x, (float) normal.y, (float) normal.z);
                    buffer.vertex((float) end.x, (float) end.y, (float) end.z).color(line.color).normal((float) normal.x, (float) normal.y, (float) normal.z);
                } BufferRenderer.drawWithGlobalProgram(buffer.end());
            }
        }
        scene.labels.sort(Comparator.comparingDouble(tag -> tag.position.squaredDistanceTo(camera)));
        int textCount = 0;
        for (Tag tag : scene.labels) {
            if (textCount >= 64 || tag.position.squaredDistanceTo(camera) > 128 * 128) break;
            MatrixStack matrix = new MatrixStack(); Vec3d position = tag.position.subtract(camera);
            matrix.translate(position.x, position.y, position.z); matrix.multiply(context.camera().getRotation());
            float scale = 0.025f * tag.scale * (float) Math.clamp(Math.sqrt(tag.position.squaredDistanceTo(camera)) / 22, 1, 3);
            matrix.scale(scale, -scale, scale);
            int width = CLIENT.textRenderer.getWidth(GuiDraw.text(tag.text));
            CLIENT.textRenderer.draw(GuiDraw.text(tag.text), -width / 2f, -9, tag.color, true, matrix.peek().getPositionMatrix(),
                    TEXT_CONSUMERS, throughWalls ? TextRenderer.TextLayerType.SEE_THROUGH : TextRenderer.TextLayerType.NORMAL,
                    0, LightmapTextureManager.MAX_LIGHT_COORDINATE);
            textCount++;
        }
        if (!scene.labels.isEmpty()) TEXT_CONSUMERS.draw();
        frame = new FrameDiagnostics(frame.boxes + scene.boxes.size(), frame.segments + scene.lines.size(),
                frame.labels + textCount, frame.passes + 1);
    }

    private static void hud(DrawContext draw, RenderTickCounter tickCounter) {
        if (core == null || CLIENT.world == null || CLIENT.player == null || CLIENT.currentScreen != null || CLIENT.options.hudHidden) return;
        long time = now(); int width = draw.getScaledWindowWidth(), height = draw.getScaledWindowHeight();
        if (enabled("health-pulse") && hurtAt != 0) {
            double age = (time - hurtAt) / (duration("health-pulse") * 1_000_000_000d);
            if (age < 1) {
                int alpha = (int) (value("health-pulse", "intensity") * Math.pow(1 - age, 2) * 125);
                for (int layer = 0; layer < 12; layer++) {
                    int color = Palette.alpha(PINK, (int) (alpha * (1 - layer / 12.0)));
                    draw.fill(layer, layer, width - layer, layer + 1, color); draw.fill(layer, height - layer - 1, width - layer, height - layer, color);
                    draw.fill(layer, layer, layer + 1, height - layer, color); draw.fill(width - layer - 1, layer, width - layer, height - layer, color);
                }
            }
        }
        if (enabled("hit-markers") && hitAt != 0) {
            double age = (time - hitAt) / (duration("hit-markers") * 1_000_000_000d);
            if (age < 1) {
                double size = value("hit-markers", "size") * (1 + age * 0.35), x = width / 2d, y = height / 2d;
                int color = Palette.alpha(GOLD, (int) (255 * (1 - age)));
                for (int sx : new int[]{-1, 1}) for (int sy : new int[]{-1, 1})
                    GuiDraw.line(draw, x + sx * size * 0.55, y + sy * size * 0.55, x + sx * size, y + sy * size, color);
            }
        }
    }

    private enum Kind { PLACE, BREAK, SPARK, RING, LABEL, PICKUP }
    private record Effect(String module, Kind kind, Vec3d position, Vec3d velocity, Vec3d end, Box box,
                          long start, double duration, double size, int color, String text) {
        double age(long time) { return Math.clamp((time - start) / (duration * 1_000_000_000d), 0, 1); }
    }
    private record LivingSnapshot(float health, int hurtTime) { }
    private record TrailPoint(Vec3d position, long time) { }
    private record Mining(BlockPos position, float progress, long time) { }
    private record ColoredBox(Box box, int color) { }
    private record Segment(Vec3d start, Vec3d end, int color, float width) { }
    private record Tag(Vec3d position, String text, int color, float scale) { }
    private static final class Scene {
        final List<ColoredBox> boxes = new ArrayList<>(); final List<Segment> lines = new ArrayList<>(); final List<Tag> labels = new ArrayList<>();
        void line(Vec3d start, Vec3d end, int color, float width) {
            if (start.squaredDistanceTo(end) > 0.000001 && lines.size() < 8192) lines.add(new Segment(start, end, color, width));
        }
        void box(Box box, int color, float width) {
            Vec3d[] corners = new Vec3d[8];
            for (int i = 0; i < 8; i++) corners[i] = new Vec3d((i & 1) == 0 ? box.minX : box.maxX, (i & 2) == 0 ? box.minY : box.maxY, (i & 4) == 0 ? box.minZ : box.maxZ);
            for (int i = 0; i < EDGES.length; i += 2) line(corners[EDGES[i]], corners[EDGES[i + 1]], color, width);
        }
        void circle(Vec3d center, double radius, int color, float width) {
            for (int i = 0; i < 40; i++) {
                double a = i * Math.PI / 20, b = (i + 1) * Math.PI / 20;
                line(center.add(Math.cos(a) * radius, 0, Math.sin(a) * radius), center.add(Math.cos(b) * radius, 0, Math.sin(b) * radius), color, width);
            }
        }
        void path(Deque<TrailPoint> points, long time, double lifetime, int color, float width) {
            TrailPoint previous = null;
            for (TrailPoint point : points) {
                if (previous != null) line(previous.position, point.position,
                        Palette.alpha(color, (int) (220 * Math.clamp(1 - (time - point.time) / (lifetime * 1_000_000_000d), 0, 1))), width);
                previous = point;
            }
        }
    }
    private static final class SavedState {
        final boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST), blend = GL11.glIsEnabled(GL11.GL_BLEND), cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        final boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        final int depthFunction = GL11.glGetInteger(GL11.GL_DEPTH_FUNC), sourceRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),
                destinationRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB), sourceAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), destinationAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        final float lineWidth = RenderSystem.getShaderLineWidth(); final float[] color = RenderSystem.getShaderColor().clone();
        final Fog fog = RenderSystem.getShaderFog(); final ShaderProgram shader = RenderSystem.getShader();
        void restore() {
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            RenderSystem.depthMask(depthMask); RenderSystem.depthFunc(depthFunction); RenderSystem.blendFuncSeparate(sourceRgb, destinationRgb, sourceAlpha, destinationAlpha);
            RenderSystem.lineWidth(lineWidth); RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]); RenderSystem.setShaderFog(fog);
            if (shader == null) RenderSystem.clearShader(); else RenderSystem.setShader(shader);
        }
    }
}
