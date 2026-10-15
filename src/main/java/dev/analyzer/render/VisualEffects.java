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
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.*;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.*;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
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
    private static final Map<Integer, ContrailPath> contrailPaths = new LinkedHashMap<>();
    private static final Map<Integer, Vec3d> playerPositions = new HashMap<>();
    private static final Set<Integer> lightningSeen = new HashSet<>();
    private static final Map<String, Set<String>> settingIds = new HashMap<>();
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
    private static long hurtAt, hitAt, attackAt, stormAt, heartbeatAt, soundAt;
    private static int soundCombo, ambientCount;
    private static boolean vitalsWarning;
    private static Vec3d previewPosition;
    private static long previewAt;
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
        for (var module : state.modules()) {
            Set<String> ids = new HashSet<>(); module.settings().forEach(setting -> ids.add(setting.id())); settingIds.put(module.id(), ids);
        }
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
    public record CosmeticDiagnostics(int ambientPoints, int contrailPlayers, int lightningEvents, int hitSounds, boolean warning) { }
    public static CosmeticDiagnostics cosmeticDiagnostics() {
        return new CosmeticDiagnostics(ambientCount, contrailPaths.size(), emitted("storm-effects"), emitted("hit-sounds"), vitalsWarning);
    }
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
            case "air-effects" -> ambientCount + " bounded ambient points";
            case "contrails" -> contrailPaths.size() + " moving players with paired trails";
            case "storm-effects" -> emitted(moduleId) + " actual lightning strikes";
            case "vitals" -> vitalsWarning ? "Health, fire or low-air warning active" : "Vitals within warning thresholds";
            case "hit-sounds" -> emitted(moduleId) + " confirmed hit/kill cues";
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
    private static boolean has(String id, String setting) { return settingIds.getOrDefault(id, Set.of()).contains(setting); }
    private static double optional(String id, String setting, double fallback) { return has(id, setting) ? value(id, setting) : fallback; }
    private static String choice(String id, String setting, String fallback) { return has(id, setting) ? core.getChoice(id, setting) : fallback; }
    private static void mark(String id) { emitted.merge(id, 1, (a, b) -> a == Integer.MAX_VALUE ? a : a + b); }
    private static double duration(String id) { return value(id, "duration"); }
    private static long now() { return System.nanoTime(); }
    private static boolean nearby(Vec3d position, double range) { return CLIENT.player != null && position.squaredDistanceTo(CLIENT.player.getPos()) <= range * range; }
    private static boolean matchesWho(String module, Entity entity) {
        String who = choice(module, "who", "Everything");
        return switch (who) { case "Self" -> entity == CLIENT.player; case "Players" -> entity instanceof PlayerEntity;
            case "Hostiles" -> entity instanceof Monster; default -> true; };
    }
    private static int tint(String module, int fallback, long time, double phase) {
        if (!has(module, "color-mode")) return fallback;
        int color = switch (core.getChoice(module, "color-mode")) {
            case "Theme" -> switch (core.getChoice("menu", "accent")) { case "Violet" -> Palette.VIOLET; case "Gold" -> Palette.GOLD; default -> Palette.MINT; };
            case "Custom" -> 0xFF000000 | (int) value(module, "red") << 16 | (int) value(module, "green") << 8 | (int) value(module, "blue");
            case "Rainbow" -> rainbow(time / 1_000_000_000d * value(module, "color-speed") * 0.12 + phase);
            default -> fallback;
        };
        double brightness = value(module, "brightness") * (core.getBoolean(module, "color-pulse")
            ? 0.75 + Math.sin(time / 1_000_000_000d * value(module, "color-speed") * 4 + phase * 6) * 0.25 : 1);
        return (fallback & 0xFF000000) | Math.clamp((int) ((color >> 16 & 255) * brightness), 0, 255) << 16
            | Math.clamp((int) ((color >> 8 & 255) * brightness), 0, 255) << 8 | Math.clamp((int) ((color & 255) * brightness), 0, 255);
    }
    private static int rainbow(double hue) {
        double h = (hue - Math.floor(hue)) * 6, f = h - Math.floor(h); int low = 92, high = 255;
        int up = (int) (low + (high - low) * f), down = (int) (high - (high - low) * f);
        return 0xFF000000 | switch ((int) h) { case 0 -> high << 16 | up << 8 | low; case 1 -> down << 16 | high << 8 | low;
            case 2 -> low << 16 | high << 8 | up; case 3 -> low << 16 | down << 8 | high; case 4 -> up << 16 | low << 8 | high;
            default -> high << 16 | low << 8 | down; };
    }

    /** Called only after an accepted block-item use changed the predicted client block state. */
    public static void onPlaced(BlockPos position, BlockState after) {
        if (!inWorld() || after.isAir()) return;
        placements++;
        blockEffect(position, after, true);
        if (!enabled("place-animation") || !nearby(Vec3d.ofCenter(position), value("place-animation", "range"))) return;
        Box box = blockBounds(position, after);
        add(new Effect("place-animation", Kind.PLACE, box.getCenter(), Vec3d.ZERO, null, box, now(), duration("place-animation"), 1, MINT,
                core.getChoice("place-animation", "style")));
        mark("place-animation");
    }

    /** Successful local breaking produces a visual ghost of the old bounds; the real block stays broken. */
    public static void onBroken(BlockPos position, BlockState before) {
        if (!inWorld() || before.isAir()) return;
        breaks++; mining = null;
        blockEffect(position, before, false);
        if (!enabled("break-animation") || !nearby(Vec3d.ofCenter(position), value("break-animation", "range"))) return;
        Box box = blockBounds(position, before);
        int color = before.isOf(Blocks.OBSIDIAN) ? VIOLET : GOLD;
        add(new Effect("break-animation", Kind.BREAK, box.getCenter(), Vec3d.ZERO, null, box, now(), duration("break-animation"), 1, color, choice("break-animation", "style", "Dissolve")));
        burst("break-animation", box.getCenter(), (int) value("break-animation", "debris"), color, 1.15);
        mark("break-animation");
    }

    private static void blockEffect(BlockPos position, BlockState state, boolean placed) {
        String id = "block-effects";
        if (!enabled(id) || !core.getBoolean(id, placed ? "on-place" : "on-break") || !nearby(Vec3d.ofCenter(position), value(id, "range"))) return;
        Box box = blockBounds(position, state);
        int color = core.getBoolean(id, "block-colors") ? 0xFF000000 | state.getMapColor(CLIENT.world, position).color : placed ? MINT : GOLD;
        if (core.getChoice(id, "style").equals("Frame")) add(new Effect(id, placed ? Kind.PLACE : Kind.BREAK,
            box.getCenter(), Vec3d.ZERO, null, box, now(), duration(id), 1, color, "Frame"));
        else burst(id, box.getCenter(), (int) value(id, "count"), color, 1.2);
        mark(id);
    }

    public static void onMining(BlockPos position, float progress, boolean active) {
        if (core == null || !active || CLIENT.world == null) { mining = null; return; }
        mining = new Mining(position.toImmutable(), Math.clamp(progress, 0, 1), now());
    }

    /** Attack intent is retained briefly; hit markers still wait for actual hurt/health transitions. */
    public static void onAttack(Entity target) { if (inWorld() && target != null) { attackId = target.getId(); attackAt = now(); } }

    public static void onPickup(Entity item, Entity collector) {
        if (!inWorld() || !enabled("pickup-streaks") || item == null || collector == null) return;
        String who = core.getChoice("pickup-streaks", "who");
        if (who.equals("Self") && collector != CLIENT.player || who.equals("Players") && (!(collector instanceof PlayerEntity) || collector == CLIENT.player)) return;
        add(new Effect("pickup-streaks", Kind.PICKUP, item.getPos().add(0, 0.15, 0), Vec3d.ZERO,
                collector.getPos().add(0, collector.getHeight() * 0.55, 0), null, now(), duration("pickup-streaks"), 1, GOLD, ""));
        mark("pickup-streaks");
    }

    public static void onExplosion(Vec3d center) {
        if (!inWorld() || !enabled("explosion-effects") || !nearby(center, value("explosion-effects", "range"))) return;
        if (core.getBoolean("explosion-effects", "shockwave")) ring("explosion-effects", center, value("explosion-effects", "size"), GOLD);
        if (!core.getChoice("explosion-effects", "style").equals("Rings")) burst("explosion-effects", center, (int) value("explosion-effects", "count"), GOLD, 2.1);
        mark("explosion-effects");
    }

    public static void onEntityStatus(Entity entity, byte status) {
        if (!inWorld() || entity == null) return;
        if (status == 35 && enabled("totem-pops")) {
            Vec3d position = entity.getPos().add(0, entity.getHeight() * 0.6, 0);
            ring("totem-pops", entity.getPos().add(0, 0.03, 0), value("totem-pops", "size"), GOLD);
            burst("totem-pops", position, (int) value("totem-pops", "count"), GOLD, 1.7);
            label("totem-pops", position.add(0, 0.6, 0), "POP", GOLD);
            mark("totem-pops");
        } else if (status == 3 && entity instanceof LivingEntity) onDeath(entity);
    }

    private static void onDeath(Entity entity) {
        long time = now();
        if (deaths.size() >= MAX_TRACKED && !deaths.containsKey(entity.getId())) deaths.remove(deaths.keySet().iterator().next());
        Long previous = deaths.put(entity.getId(), time);
        if (previous != null && time - previous < 2_000_000_000L) return;
        if (enabled("hit-sounds") && entity.getId() == attackId && time - attackAt < 3_000_000_000L) playHitSound(true, time);
        if (!enabled("death-effects") || !matchesWho("death-effects", entity) || entity == CLIENT.player && !core.getBoolean("death-effects", "self")
            || !nearby(entity.getPos(), value("death-effects", "range"))) return;
        if (!core.getChoice("death-effects", "style").equals("Shockwave")) burst("death-effects", entity.getPos().add(0, entity.getHeight() * 0.5, 0), (int) value("death-effects", "count"), VIOLET, 1.7);
        ring("death-effects", entity.getPos().add(0, 0.03, 0), 1.3, VIOLET);
        mark("death-effects");
    }

    private static void reset(ClientWorld next) {
        world = next; effects.clear(); living.clear(); projectilePaths.clear(); contrailPaths.clear(); playerPositions.clear(); lightningSeen.clear(); playerPath.clear(); emitted.clear(); deaths.clear(); hurtEvents.clear();
        holes = List.of(); mining = null; previousPosition = null; previousGround = false; previousVelocityY = stepDistance = 0;
        hurtAt = hitAt = attackAt = 0; attackId = -1; ticks = placements = breaks = itemCount = tagCount = 0; holesEnabled = false;
        frame = new FrameDiagnostics(0, 0, 0, 0);
        stormAt = heartbeatAt = soundAt = previewAt = 0; soundCombo = ambientCount = 0; vitalsWarning = false; previewPosition = null;
    }

    public static void tick(MinecraftClient client) {
        if (core == null) return;
        if (client.world != world) reset(client.world);
        if (client.world == null || client.player == null) return;
        long time = now(); ticks++;
        effects.removeIf(effect -> effect.age(time) >= 1);
        deaths.entrySet().removeIf(entry -> time - entry.getValue() > 5_000_000_000L);
        Set<Integer> seen = new HashSet<>(), lightningNow = new HashSet<>();
        int tracked = 0;
        for (Entity entity : client.world.getEntities()) {
            if (++tracked > MAX_TRACKED) break;
            if (entity instanceof LightningEntity) {
                lightningNow.add(entity.getId());
                if (lightningSeen.add(entity.getId())) onLightning(entity.getPos(), time);
            }
            if (entity instanceof PlayerEntity player) trackContrail(player, time);
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
            if (entity instanceof ProjectileEntity && enabled("projectile-trails") && !entity.isRemoved() && projectileVisible(entity)) {
                Deque<TrailPoint> path = projectilePaths.get(entity.getId());
                if (path == null && projectilePaths.size() < MAX_PATHS) {
                    path = new ArrayDeque<>(); projectilePaths.put(entity.getId(), path);
                }
                if (path != null) append(path, entity.getPos(), time);
            }
        }
        living.keySet().retainAll(seen);
        playerPositions.keySet().retainAll(seen);
        lightningSeen.retainAll(lightningNow);
        hurtEvents.keySet().retainAll(seen);
        for (Deque<TrailPoint> path : projectilePaths.values()) expire(path, time, enabled("projectile-trails") ? duration("projectile-trails") : 0);
        projectilePaths.values().removeIf(Deque::isEmpty);
        trackContrail(client.player, time);
        for (ContrailPath path : contrailPaths.values()) {
            double lifetime = enabled("contrails") ? duration("contrails") : 0; expire(path.left, time, lifetime); expire(path.right, time, lifetime);
        }
        contrailPaths.values().removeIf(path -> path.left.isEmpty() && path.right.isEmpty());
        Vec3d position = client.player.getPos();
        boolean grounded = client.player.isOnGround();
        if (previousPosition != null) {
            double distance = position.distanceTo(previousPosition);
            if (enabled("teleport-effects") && distance >= value("teleport-effects", "threshold")) {
                ring("teleport-effects", previousPosition.add(0, 0.03, 0), value("teleport-effects", "size"), VIOLET);
                ring("teleport-effects", position.add(0, 0.03, 0), value("teleport-effects", "size"), CYAN);
                if (core.getBoolean("teleport-effects", "link")) add(new Effect("teleport-effects", Kind.LINK, previousPosition.add(0, 1, 0), Vec3d.ZERO,
                    position.add(0, 1, 0), null, time, duration("teleport-effects"), 1, VIOLET, ""));
                burst("teleport-effects", position.add(0, 0.9, 0), 20, CYAN, 1.3); mark("teleport-effects");
            } else if (distance < 4) {
                if (enabled("trails") && distance > 0.025) append(playerPath, position.add(0, value("trails", "height"), 0), time);
                if (grounded && enabled("footsteps")) stepDistance += Math.sqrt(Math.pow(position.x - previousPosition.x, 2) + Math.pow(position.z - previousPosition.z, 2));
                if (enabled("footsteps") && grounded && stepDistance >= value("footsteps", "spacing")) {
                    ring("footsteps", position.add(0, 0.025, 0), value("footsteps", "size"), CYAN); mark("footsteps"); stepDistance = 0;
                }
            }
            String jumpTrigger = choice("jump-circles", "trigger", "Jump");
            if (previousGround && !grounded && client.player.getVelocity().y > 0.05 && enabled("jump-circles") && !jumpTrigger.equals("Land")) {
                ring("jump-circles", previousPosition.add(0, 0.025, 0), value("jump-circles", "size"), MINT); mark("jump-circles");
            }
            if (!previousGround && grounded && enabled("jump-circles") && !jumpTrigger.equals("Jump")) {
                ring("jump-circles", position.add(0, 0.025, 0), value("jump-circles", "size"), MINT); mark("jump-circles");
            }
            if (!previousGround && grounded && previousVelocityY < -0.22 && enabled("landing-impact")) {
                ring("landing-impact", position.add(0, 0.025, 0), value("landing-impact", "size"), GOLD);
                burst("landing-impact", position.add(0, 0.08, 0), (int) value("landing-impact", "count"), GOLD, 0.7); mark("landing-impact");
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
        if (!enabled("air-effects")) ambientCount = 0;
        vitalsWarning = enabled("vitals") && (client.player.getHealth() <= value("vitals", "threshold")
            || core.getBoolean("vitals", "burning") && client.player.isOnFire()
            || core.getBoolean("vitals", "drowning") && client.player.isSubmergedInWater() && client.player.getAir() < 60);
        if (vitalsWarning && core.getBoolean("vitals", "heartbeat") && time - heartbeatAt > 800_000_000L) {
            heartbeatAt = time; client.player.playSoundToPlayer(SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.PLAYERS, 0.3f, 1.1f); mark("vitals");
        }
    }

    private static boolean projectileVisible(Entity entity) {
        if (!nearby(entity.getPos(), value("projectile-trails", "range"))) return false;
        String filter = entity instanceof PersistentProjectileEntity ? "arrows" : entity instanceof EnderPearlEntity ? "pearls"
            : entity instanceof ThrownItemEntity ? "thrown" : "other";
        return core.getBoolean("projectile-trails", filter);
    }
    private static void trackContrail(PlayerEntity player, long time) {
        Vec3d before = playerPositions.put(player.getId(), player.getPos());
        if (!enabled("contrails") || !player.isAlive() || !nearby(player.getPos(), value("contrails", "range"))) return;
        String who = core.getChoice("contrails", "who");
        if (who.equals("Self") && player != CLIENT.player || who.equals("Players") && player == CLIENT.player) return;
        // Remote players receive position updates without reliable local velocity vectors.
        double speed = player == CLIENT.player || before == null ? player.getVelocity().length() : before.distanceTo(player.getPos());
        if (before != null && before.squaredDistanceTo(player.getPos()) > 16) { contrailPaths.remove(player.getId()); return; }
        if (speed < value("contrails", "min-speed") || core.getBoolean("contrails", "elytra-only") && !player.isGliding()) return;
        ContrailPath path = contrailPaths.get(player.getId());
        if (path == null) {
            if (contrailPaths.size() >= 16) return;
            path = new ContrailPath(); contrailPaths.put(player.getId(), path); mark("contrails");
        }
        Vec3d pos = player.getPos().add(0, player.getHeight() * 0.65, 0);
        double yaw = Math.toRadians(player.getYaw()); Vec3d side = new Vec3d(Math.cos(yaw), 0, Math.sin(yaw)).multiply(value("contrails", "wing-spread"));
        append(path.left, pos.add(side), time); append(path.right, pos.subtract(side), time);
        path.color = core.getBoolean("contrails", "speed-color") ? Palette.mix(CYAN, PINK, Math.clamp((speed - 0.25) / 1.5, 0, 1)) : CYAN;
    }
    private static void onLightning(Vec3d position, long time) {
        if (!enabled("storm-effects") || !nearby(position, value("storm-effects", "range"))) return;
        stormAt = time;
        add(new Effect("storm-effects", Kind.BOLT, position, Vec3d.ZERO, null, null, time, duration("storm-effects"), value("storm-effects", "size"), CYAN,
            core.getChoice("storm-effects", "style")));
        if (core.getBoolean("storm-effects", "sparks")) burst("storm-effects", position.add(0, 0.05, 0), 24, CYAN, 1.8);
        if (core.getBoolean("storm-effects", "scorch")) add(new Effect("storm-effects", Kind.RING, position.add(0, 0.03, 0), Vec3d.ZERO,
            null, null, time, value("storm-effects", "scorch-time"), 2, GOLD, "Ripple"));
        mark("storm-effects");
    }

    private static void onHurt(LivingEntity target, float damage, long time) {
        Vec3d position = target.getPos().add(0, target.getHeight() * 0.6, 0);
        Long previousHurt = hurtEvents.get(target.getId());
        boolean firstHurt = previousHurt == null || time - previousHurt > 150_000_000L;
        if (firstHurt) hurtEvents.put(target.getId(), time);
        if (firstHurt && enabled("hit-particles") && matchesWho("hit-particles", target) && nearby(position, value("hit-particles", "range"))) {
            burst("hit-particles", position, (int) value("hit-particles", "count"), PINK, value("hit-particles", "speed")); mark("hit-particles");
        }
        if (firstHurt && enabled("damage-effects") && matchesWho("damage-effects", target) && nearby(position, value("damage-effects", "range"))) {
            int color = core.getBoolean("damage-effects", "damage-red") ? PINK : VIOLET;
            if (core.getChoice("damage-effects", "style").equals("Shockwave")) ring("damage-effects", target.getPos().add(0, 0.03, 0), 1.2, color);
            else burst("damage-effects", position, (int) value("damage-effects", "count"), color, 1.2);
            mark("damage-effects");
        }
        if (enabled("damage-numbers") && damage > 0.01f) {
            label("damage-numbers", target.getPos().add(0, target.getHeight() + 0.25, 0),
                    "-" + String.format(Locale.ROOT, "%.1f", damage) + (core.getBoolean("damage-numbers", "distance") ? " " + Math.round(CLIENT.player.distanceTo(target)) + "m" : ""), PINK); mark("damage-numbers");
        }
        if (firstHurt && target.getId() == attackId && time - attackAt < 1_500_000_000L && enabled("hit-sounds")) playHitSound(false, time);
        if (firstHurt && target.getId() == attackId && time - attackAt < 1_500_000_000L && enabled("hit-markers")) { hitAt = time; mark("hit-markers"); }
        if (target == CLIENT.player && damage > 0.01f && enabled("health-pulse")) { hurtAt = time; mark("health-pulse"); }
    }

    private static void playHitSound(boolean kill, long time) {
        if (CLIENT.player == null || value("hit-sounds", "volume") <= 0) return;
        String sound = core.getChoice("hit-sounds", "sound"), killChoice = core.getChoice("hit-sounds", "kill-sound");
        if (kill && killChoice.equals("Off")) return;
        if (kill && killChoice.equals("Ding")) sound = "Ding";
        if (time - soundAt > 2_000_000_000L) soundCombo = 0;
        soundAt = time;
        if (!kill) soundCombo = Math.min(6, soundCombo + 1);
        double pitch = value("hit-sounds", "pitch") + (RANDOM.nextDouble() * 2 - 1) * value("hit-sounds", "variation")
            + (core.getBoolean("hit-sounds", "combo") ? soundCombo * 0.06 : 0);
        if (kill && killChoice.equals("Lower")) pitch *= 0.75;
        SoundEvent event = switch (sound) { case "Pop" -> SoundEvents.UI_HUD_BUBBLE_POP; case "Punch" -> SoundEvents.ENTITY_PLAYER_ATTACK_STRONG;
            case "Crit" -> SoundEvents.ENTITY_PLAYER_ATTACK_CRIT; case "Tick" -> SoundEvents.BLOCK_NOTE_BLOCK_HAT.value();
            case "Orb" -> SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP; case "Amethyst" -> SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME;
            default -> SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(); };
        CLIENT.player.playSoundToPlayer(event, SoundCategory.PLAYERS, (float) value("hit-sounds", "volume"), (float) Math.clamp(pitch, 0.25, 2)); mark("hit-sounds");
    }

    private static void append(Deque<TrailPoint> path, Vec3d position, long time) {
        if (!path.isEmpty() && path.getLast().position.squaredDistanceTo(position) < 0.0025) return;
        path.addLast(new TrailPoint(position, time)); while (path.size() > MAX_POINTS) path.removeFirst();
    }
    private static void expire(Deque<TrailPoint> path, long time, double seconds) {
        while (!path.isEmpty() && time - path.getFirst().time > seconds * 1_000_000_000d) path.removeFirst();
    }
    private static void add(Effect effect) {
        if (effect.module.equals("footsteps")) {
            int cap = (int) value("footsteps", "max-prints"), count = 0;
            for (Effect entry : effects) if (entry.module.equals("footsteps")) count++;
            if (count >= cap) for (Iterator<Effect> iterator = effects.iterator(); iterator.hasNext();) if (iterator.next().module.equals("footsteps")) { iterator.remove(); break; }
        }
        while (effects.size() >= MAX_EFFECTS) effects.removeFirst(); effects.addLast(effect);
    }
    private static void ring(String module, Vec3d position, double size, int color) {
        add(new Effect(module, Kind.RING, position, Vec3d.ZERO, null, null, now(), duration(module), size, color, choice(module, "style", "Ripple")));
    }
    private static void label(String module, Vec3d position, String text, int color) {
        add(new Effect(module, Kind.LABEL, position, Vec3d.ZERO, null, null, now(), duration(module), 1, color, text));
    }
    private static void burst(String module, Vec3d position, int count, int color, double speed) {
        long time = now();
        double fragmentSize = switch (module) {
            case "hit-particles", "break-animation", "block-effects", "damage-effects" -> value(module, "size");
            default -> 0.06;
        };
        for (int i = 0; i < count; i++) {
            Vec3d velocity = new Vec3d((RANDOM.nextDouble() - 0.5) * speed * 2,
                    (0.25 + RANDOM.nextDouble()) * speed, (RANDOM.nextDouble() - 0.5) * speed * 2);
            velocity = velocity.multiply(optional(module, "spread", 1));
            add(new Effect(module, Kind.SPARK, position, velocity, null, null, time, duration(module),
                    fragmentSize * (0.7 + RANDOM.nextDouble() * 0.6), color, choice(module, "style", "Sparks")));
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
            int base = tint(effect.module, effect.color, time, age), color = Palette.alpha(base, (int) (235 * fade));
            switch (effect.kind) {
                case PLACE -> {
                    double eased = 1 - Math.pow(1 - age, 3), scale = 0.08 + 0.92 * eased;
                    Vec3d center = effect.position;
                    if (effect.text.equals("Rise")) center = center.add(0, -(1 - eased) * 0.55, 0);
                    if (effect.text.equals("Drop")) center = center.add(0, (1 - eased) * 0.9, 0);
                    if (effect.text.equals("Pop")) scale = 1 + Math.pow(1 - age, 3) * Math.sin(age * Math.PI * 5) * 0.5 - Math.pow(1 - age, 4);
                    if (effect.text.equals("Bloom")) scale = 0.15 + eased * 0.85 + Math.sin(age * Math.PI) * 0.12;
                    if (effect.text.equals("Frame")) scale = 1;
                    Box ghost = scaled(effect.box, center, Math.max(0.05, scale));
                    if (effect.text.equals("Spin")) scene.rotatedBox(ghost, (1 - eased) * Math.PI, color, 2);
                    else { scene.boxes.add(new ColoredBox(ghost, Palette.alpha(base, (int) (42 * fade)))); scene.box(ghost, color, 2); }
                    scene.circle(effect.box.getCenter().add(0, -effect.box.getLengthY() / 2 + 0.01, 0), 0.25 + age * 0.65, color, 1.5f);
                }
                case BREAK -> {
                    Box ghost = scaled(effect.box, effect.position.add(0, age * (effect.text.equals("Chip") ? -0.35 : 0.18), 0), effect.text.equals("Frame") ? 1 : 1 - age * age);
                    if (effect.text.equals("Brackets")) scene.corners(ghost, color, 2);
                    else { scene.boxes.add(new ColoredBox(ghost, Palette.alpha(base, (int) (34 * fade)))); scene.box(ghost, color, 1.8f); }
                }
                case SPARK -> {
                    double gravity = optional(effect.module, "gravity", 2.4);
                    Vec3d pos = effect.position.add(effect.velocity.multiply(seconds)).add(0, -gravity * 0.5 * seconds * seconds, 0);
                    double size = effect.size * (1 - age * 0.65);
                    if (effect.text.equals("Gems")) scene.diamond(pos, size * 1.8, color, 1.5f);
                    else if (effect.text.equals("Shards") || effect.text.equals("Shatter")) scene.line(pos.subtract(effect.velocity.normalize().multiply(size * 2)), pos.add(effect.velocity.normalize().multiply(size * 2)), color, 2);
                    else if (effect.text.equals("Arc")) {
                        Vec3d tail = effect.position.add(effect.velocity.multiply(Math.max(0, seconds - 0.1))).add(0, -gravity * 0.5 * Math.pow(Math.max(0, seconds - 0.1), 2), 0);
                        scene.line(tail, pos, color, 2);
                    } else scene.boxes.add(new ColoredBox(new Box(pos.x - size, pos.y - size, pos.z - size, pos.x + size, pos.y + size, pos.z + size), color));
                }
                case RING -> {
                    double radius = effect.size * (0.1 + Math.sqrt(age) * 0.9);
                    if (effect.text.equals("Implosion")) radius = effect.size * (1 - age);
                    if (effect.text.equals("Tile")) scene.box(new Box(effect.position.x - radius, effect.position.y, effect.position.z - radius,
                        effect.position.x + radius, effect.position.y + 0.015, effect.position.z + radius), color, 2);
                    else if (effect.text.equals("Prints")) {
                        double side = Math.sin(effect.start * 0.00000001) > 0 ? 0.13 : -0.13;
                        scene.boxes.add(new ColoredBox(new Box(effect.position.x + side - radius * 0.25, effect.position.y, effect.position.z - radius * 0.65,
                            effect.position.x + side + radius * 0.25, effect.position.y + 0.012, effect.position.z + radius * 0.65), color));
                    } else if (effect.text.equals("Petals")) {
                        for (int i = 0; i < 6; i++) { double a = i * Math.PI / 3; scene.circle(effect.position.add(Math.cos(a) * radius * 0.65, 0, Math.sin(a) * radius * 0.65), radius * 0.32, color, 1.5f); }
                    } else if (effect.text.equals("Portal")) scene.tiltedCircle(effect.position.add(0, radius, 0), radius, 0, Math.PI / 2, color, 2.5f);
                    else {
                        scene.circle(effect.position, radius, color, 2);
                        scene.circle(effect.position.add(0, 0.01, 0), radius * 0.9, Palette.alpha(base, (int) (90 * fade)), 4);
                        if (effect.text.equals("Rings")) scene.circle(effect.position.add(0, 0.02, 0), radius * 0.65, color, 1.5f);
                        if (effect.text.equals("Crown")) for (int i = 0; i < 12; i++) {
                            double a = i * Math.PI / 6, b = a + Math.PI / 12;
                            Vec3d start = effect.position.add(Math.cos(a) * radius, 0, Math.sin(a) * radius), top = effect.position.add(Math.cos(b) * radius, fade * 0.45, Math.sin(b) * radius);
                            scene.line(start, top, color, 1.5f); scene.line(top, effect.position.add(Math.cos(a + Math.PI / 6) * radius, 0, Math.sin(a + Math.PI / 6) * radius), color, 1.5f);
                        }
                        if (effect.text.equals("Vortex") || effect.text.equals("Spiral")) {
                            Vec3d previous = effect.position;
                            for (int i = 0; i <= 32; i++) { double a = i * Math.PI / 8 + seconds * 5, r = radius * i / 32;
                                Vec3d point = effect.position.add(Math.cos(a) * r, i / 32d * fade * 1.5, Math.sin(a) * r); scene.line(previous, point, color, 1.5f); previous = point; }
                        }
                    }
                }
                case LABEL -> {
                    float scale = effect.module.equals("damage-numbers") ? (float) value("damage-numbers", "scale") : 1;
                    scene.labels.add(new Tag(effect.position.add(0, age * optional(effect.module, "rise", 0.8), 0), effect.text, color, scale));
                }
                case PICKUP -> {
                    Vec3d previous = curve(effect.position, effect.end, age);
                    int segments = (int) value("pickup-streaks", "segments");
                    double size = value("pickup-streaks", "size");
                    scene.diamond(previous, size * (1 - age * 0.7), color, 1.5f);
                    for (int i = 1; i <= segments; i++) {
                        double progress = Math.clamp(age - i * 0.025, 0, 1);
                        Vec3d next = curve(effect.position, effect.end, progress);
                        scene.line(previous, next, Palette.alpha(base, (int) (fade * (1 - i / (segments + 2d)) * 240)), (float) value("pickup-streaks", "line-width")); previous = next;
                    }
                }
                case LINK -> scene.line(effect.position, effect.end, color, 1.5f);
                case BOLT -> lightningScene(scene, effect, color, fade, seconds);
            }
        }
        if (!throughPass && enabled("trails")) styledPath(scene, playerPath, "trails", time, MINT, (float) value("trails", "line-width"), core.getChoice("trails", "style"));
        if (!throughPass && enabled("projectile-trails")) for (var entry : projectilePaths.entrySet()) {
            Entity entity = CLIENT.world.getEntityById(entry.getKey()); if (entity != null && !projectileVisible(entity)) continue;
            styledPath(scene, entry.getValue(), "projectile-trails", time, CYAN, (float) value("projectile-trails", "line-width"), core.getChoice("projectile-trails", "style"));
        }
        if (!throughPass && enabled("contrails")) for (var entry : contrailPaths.entrySet()) {
            String who = core.getChoice("contrails", "who"); boolean self = entry.getKey() == CLIENT.player.getId();
            if (who.equals("Self") && !self || who.equals("Players") && self) continue;
            if (entry.getKey() == CLIENT.player.getId() && CLIENT.options.getPerspective().isFirstPerson() && !core.getBoolean("contrails", "first-person")) continue;
            ContrailPath path = entry.getValue(); float width = (float) value("contrails", "line-width"); String style = core.getChoice("contrails", "style");
            styledPath(scene, path.left, "contrails", time, path.color, width, style); styledPath(scene, path.right, "contrails", time, path.color, width, style);
        }
        if (throughPass && enabled("break-animation") && core.getBoolean("break-animation", "progress") && mining != null && time - mining.time < 300_000_000L) {
            BlockState state = CLIENT.world.getBlockState(mining.position);
            if (!state.isAir()) {
                Box full = blockBounds(mining.position, state);
                Box inner = scaled(full, full.getCenter(), 0.15 + mining.progress * 0.85);
                int color = tint("break-animation", GOLD, time, mining.progress);
                scene.box(full, Palette.alpha(color, 173), 1.5f); scene.box(inner, Palette.alpha(color, 235), 2.5f);
                scene.boxes.add(new ColoredBox(inner, Palette.alpha(color, (int) (20 + mining.progress * 50))));
            }
        }
    }

    private static void staticScene(Scene scene, WorldRenderContext context, boolean throughPass, long time) {
        double clock = time / 1_000_000_000.0;
        float delta = context.tickCounter().getTickDelta(false);
        if (enabled("air-effects") && core.getBoolean("air-effects", "through-walls") == throughPass) ambientScene(scene, context.camera().getPos(), time);
        if (!throughPass) {
            if (enabled("place-preview") && !core.getBoolean("place-preview", "through-walls")) preview(scene);
            if (enabled("block-highlight") && CLIENT.crosshairTarget instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
                int alpha = core.getBoolean("block-highlight", "pulse") ? (int) (150 + Math.sin(clock * 4) * 65) : 215;
                scene.box(blockBounds(hit.getBlockPos(), CLIENT.world.getBlockState(hit.getBlockPos())), Palette.alpha(tint("block-highlight", CYAN, time, 0), alpha), (float) value("block-highlight", "line-width"));
            }
            if (enabled("player-aura") && (!CLIENT.options.getPerspective().isFirstPerson() || core.getBoolean("player-aura", "first-person"))) {
                Vec3d pos = CLIENT.player.getLerpedPos(delta); double radius = value("player-aura", "size"), angle = clock * value("player-aura", "speed");
                int color = tint("player-aura", MINT, time, 0), accent = tint("player-aura", VIOLET, time, 0.3);
                scene.circle(pos.add(0, 0.035, 0), radius, Palette.alpha(color, 192), 2);
                String style = core.getChoice("player-aura", "style");
                if (style.equals("Gyroscope")) for (int i = 0; i < 3; i++) scene.tiltedCircle(pos.add(0, 0.9, 0), radius, angle + i * Math.PI / 3, Math.PI / 3, Palette.alpha(accent, 190), 1.5f);
                else if (style.equals("Rune")) for (int i = 0; i < 8; i++) {
                    double a = angle + i * Math.PI / 4; Vec3d start = pos.add(Math.cos(a) * radius * 0.7, 0.04, Math.sin(a) * radius * 0.7);
                    scene.line(start, pos.add(Math.cos(a) * radius * 1.2, 0.04, Math.sin(a) * radius * 1.2), Palette.alpha(accent, 210), 2);
                } else scene.circle(pos.add(0, 0.8 + Math.sin(angle) * 0.35, 0), radius * 0.75, Palette.alpha(accent, 165), 1.5f);
                for (int i = 0; i < 6; i++) {
                    double a = angle + i * Math.PI / 3;
                    Vec3d p = pos.add(Math.cos(a) * radius, 0.6 + Math.sin(a * 2) * 0.4, Math.sin(a) * radius);
                    scene.boxes.add(new ColoredBox(new Box(p.x - 0.035, p.y - 0.035, p.z - 0.035, p.x + 0.035, p.y + 0.035, p.z + 0.035), Palette.alpha(color, 220)));
                }
            }
            if (enabled("trajectories")) trajectory(scene);
        } else if (enabled("place-preview") && core.getBoolean("place-preview", "through-walls")) preview(scene);
        if (enabled("hole-esp") && core.getBoolean("hole-esp", "through-walls") == throughPass) {
            boolean floor = core.getChoice("hole-esp", "style").equals("Floor outline");
            for (BlockPos pos : holes) {
                Box box = new Box(pos.getX() + 0.002, pos.getY() + 0.002, pos.getZ() + 0.002,
                        pos.getX() + 0.998, pos.getY() + (floor ? 0.015 : 1.998), pos.getZ() + 0.998);
                scene.box(box, tint("hole-esp", MINT, time, 0), (float) value("hole-esp", "line-width"));
            }
        }
        if (enabled("target-esp") && core.getBoolean("target-esp", "through-walls") == throughPass) {
            Entity target = CLIENT.crosshairTarget instanceof EntityHitResult hit ? hit.getEntity()
                    : time - attackAt < value("target-esp", "linger") * 1_000_000_000d ? CLIENT.world.getEntityById(attackId) : null;
            if (target != null && target.isAlive()) {
                Vec3d pos = target.getLerpedPos(delta); double r = (target.getWidth() * 0.7 + 0.2) * value("target-esp", "size");
                int color = tint("target-esp", PINK, time, 0); String style = core.getChoice("target-esp", "style");
                if (style.equals("Box") || style.equals("Corners")) {
                    Box box = target.getBoundingBox().offset(pos.subtract(target.getPos())).expand(0.06 * (value("target-esp", "size") - 1));
                    float lineWidth = (float) value("target-esp", "line-width");
                    if (style.equals("Corners")) scene.corners(box, color, lineWidth); else scene.box(box, color, lineWidth);
                } else {
                    float lineWidth = (float) value("target-esp", "line-width");
                    scene.circle(pos.add(0, 0.06, 0), r, Palette.alpha(color, 229), lineWidth);
                    scene.circle(pos.add(0, target.getHeight() * (0.5 + Math.sin(clock * 2.5) * 0.45), 0), r, Palette.alpha(tint("target-esp", VIOLET, time, 0.25), 195), lineWidth);
                }
            }
        }
        if (enabled("item-esp") && core.getBoolean("item-esp", "through-walls") == throughPass) {
            int found = 0; double range = value("item-esp", "range");
            for (Entity entity : CLIENT.world.getEntities()) {
                if (!(entity instanceof ItemEntity item) || item.isRemoved() || item.squaredDistanceTo(CLIENT.player) > range * range) continue;
                Vec3d pos = item.getLerpedPos(delta); int color = tint("item-esp", GOLD, time, 0);
                String style = core.getChoice("item-esp", "style");
                float lineWidth = (float) value("item-esp", "line-width");
                if (style.equals("Rings")) {
                    scene.circle(pos.add(0, 0.04, 0), 0.32, color, lineWidth);
                } else {
                    Box box = item.getBoundingBox().offset(pos.subtract(item.getPos()));
                    if (style.equals("Corners")) scene.corners(box, color, lineWidth); else scene.box(box, color, lineWidth);
                }
                if (core.getBoolean("item-esp", "labels") && found < 48) scene.labels.add(new Tag(pos.add(0, 0.8, 0),
                        shortName(item.getStack().getName().getString()) + (item.getStack().getCount() > 1 ? " x" + item.getStack().getCount() : ""), color, 0.8f));
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
                                + Math.round(Math.sqrt(target.squaredDistanceTo(CLIENT.player))) + "m", tint("nametags", player ? CYAN : MINT, time, 0),
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
        if (!valid && !core.getBoolean("place-preview", "show-blocked")) return;
        long time = now(); Vec3d target = Vec3d.ofCenter(pos);
        if (previewPosition == null || time - previewAt > 250_000_000L || !core.getBoolean("place-preview", "glide")) previewPosition = target;
        else previewPosition = previewPosition.lerp(target, 1 - Math.exp(-Math.min(0.1, (time - previewAt) / 1_000_000_000d) * 18));
        previewAt = time;
        Box box = new Box(previewPosition.x - 0.502, previewPosition.y - 0.502, previewPosition.z - 0.502,
            previewPosition.x + 0.502, previewPosition.y + 0.502, previewPosition.z + 0.502); int color = tint("place-preview", valid ? MINT : PINK, time, 0);
        if (core.getBoolean("place-preview", "fill")) scene.boxes.add(new ColoredBox(box, Palette.alpha(color, (int) (value("place-preview", "opacity") * 150))));
        if (core.getBoolean("place-preview", "edges")) scene.box(box, Palette.alpha(color, 220), 2);
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
        int color = tint("trajectories", CYAN, now(), 0);
        for (int i = 0; i < steps; i++) {
            Vec3d next = position.add(velocity);
            if (CLIENT.world.getChunkManager().getChunk((int) Math.floor(next.x) >> 4, (int) Math.floor(next.z) >> 4, ChunkStatus.FULL, false) == null) break;
            var hit = CLIENT.world.raycast(new RaycastContext(position, next, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, CLIENT.player));
            if (hit.getType() == HitResult.Type.BLOCK) { scene.line(position, hit.getPos(), Palette.alpha(color, 225), width); scene.circle(hit.getPos().add(0, 0.01, 0), 0.2, color, 2); break; }
            scene.line(position, next, Palette.alpha(color, Math.max(70, 230 - i)), width);
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

    /** Deterministic world positions wrap around the camera; no particles are allocated per tick. */
    private static void ambientScene(Scene scene, Vec3d camera, long time) {
        String id = "air-effects", style = core.getChoice(id, "style");
        int count = Math.clamp((int) value(id, "density"), 0, 160);
        double radius = value(id, "radius"), clear = value(id, "clear-radius"), size = value(id, "size");
        double clock = time / 1_000_000_000d, speed = value(id, "speed"), wind = value(id, "wind");
        List<Vec3d> positions = new ArrayList<>(count);
        int natural = switch (style) { case "Snow" -> 0xFFE4F4FF; case "Embers" -> GOLD; case "Stardust" -> VIOLET; default -> MINT; };
        for (int i = 0; i < count * 8 && positions.size() < count; i++) {
            double phase = seeded(i, 3) * Math.PI * 2;
            double fall = style.equals("Snow") ? -0.65 : style.equals("Embers") ? 0.5 : 0.12;
            double x = wrap(seeded(i, 0) * radius * 2 + clock * speed * wind * 0.25 - camera.x, radius);
            double y = wrap(seeded(i, 1) * radius * 2 + clock * speed * fall - camera.y, radius);
            double z = wrap(seeded(i, 2) * radius * 2 + Math.sin(clock * speed * 0.35 + phase) * 0.6 - camera.z, radius);
            double distance = x * x + y * y + z * z;
            if (distance > radius * radius || distance < clear * clear) continue;
            Vec3d position = camera.add(x, y, z); positions.add(position);
            double shimmer = 0.65 + Math.sin(clock * speed * 1.4 + phase) * 0.25;
            int color = Palette.alpha(tint(id, natural, time, seeded(i, 4)), (int) (210 * shimmer));
            double pointSize = size * (0.65 + seeded(i, 5) * 0.7);
            if (style.equals("Cubes")) scene.rotatedBox(pointBox(position, pointSize * 1.8), clock * speed + phase, color, 1.5f);
            else if (style.equals("Rings")) scene.tiltedCircle(position, pointSize * 2.5, phase, Math.PI / 4, color, 1.5f);
            else if (style.equals("Stardust")) scene.diamond(position, pointSize * 1.5, color, 1.5f);
            else {
                scene.fillBox(pointBox(position, pointSize), color);
                if (style.equals("Embers")) scene.line(position.add(-wind * size, -pointSize * 4, 0), position, Palette.alpha(color, 110), 1.5f);
            }
        }
        ambientCount = positions.size();
        if (style.equals("Network")) {
            double link = value(id, "link-range"), linkSquared = link * link; int links = 0;
            for (int i = 0; i < positions.size() && links < 320; i++) {
                int neighbors = 0;
                for (int j = i + 1; j < positions.size() && neighbors < 3 && links < 320; j++) {
                    double distance = positions.get(i).squaredDistanceTo(positions.get(j));
                    if (distance < linkSquared) {
                        int alpha = (int) (150 * (1 - Math.sqrt(distance) / link));
                        scene.line(positions.get(i), positions.get(j), Palette.alpha(tint(id, CYAN, time, i / 160d), alpha), 1);
                        neighbors++; links++;
                    }
                }
            }
        }
    }
    private static double seeded(int index, int channel) {
        long bits = (index + 1L) * 0x9E3779B97F4A7C15L + channel * 0x632BE59BD9B4E019L;
        bits = (bits ^ bits >>> 30) * 0xBF58476D1CE4E5B9L;
        bits = (bits ^ bits >>> 27) * 0x94D049BB133111EBL;
        return ((bits ^ bits >>> 31) >>> 11) * 0x1.0p-53;
    }
    private static double wrap(double position, double radius) {
        double width = radius * 2; return position - Math.floor((position + radius) / width) * width;
    }
    private static Box pointBox(Vec3d point, double radius) {
        return new Box(point.x - radius, point.y - radius, point.z - radius, point.x + radius, point.y + radius, point.z + radius);
    }
    private static void styledPath(Scene scene, Deque<TrailPoint> points, String id, long time, int natural, float width, String style) {
        if (points.isEmpty()) return;
        double lifetime = duration(id), size = optional(id, "size", 0.055), clock = time / 1_000_000_000d;
        TrailPoint previous = null; int index = 0;
        for (TrailPoint point : points) {
            double age = Math.clamp((time - point.time) / (lifetime * 1_000_000_000d), 0, 1), fade = (1 - age) * (1 - age);
            int color = Palette.alpha(tint(id, natural, time, index * 0.03), (int) (225 * fade));
            if (previous != null) {
                Vec3d direction = point.position.subtract(previous.position).normalize();
                Vec3d side = direction.crossProduct(new Vec3d(0, 1, 0));
                if (side.lengthSquared() < 0.001) side = new Vec3d(1, 0, 0); else side = side.normalize();
                if (style.equals("Beads")) scene.fillBox(pointBox(point.position, size * (0.5 + fade * 0.5)), color);
                else if (style.equals("Sparks") || style.equals("Embers")) {
                    Vec3d offset = side.multiply(Math.sin(index * 2.1 + clock * 2) * size * 2).add(0, age * size * 3, 0);
                    scene.diamond(point.position.add(offset), size * (0.4 + fade * 0.6), color, width);
                    scene.line(previous.position.add(offset), point.position.add(offset), Palette.alpha(color, (int) (120 * fade)), width);
                } else if (style.equals("Spiral")) {
                    Vec3d up = direction.crossProduct(side).normalize();
                    double angle = index * 0.9 + clock * 3, before = (index - 1) * 0.9 + clock * 3;
                    double radius = size * 2.5;
                    scene.line(previous.position.add(side.multiply(Math.cos(before) * radius)).add(up.multiply(Math.sin(before) * radius)),
                        point.position.add(side.multiply(Math.cos(angle) * radius)).add(up.multiply(Math.sin(angle) * radius)), color, width);
                } else {
                    scene.line(previous.position, point.position, color, width);
                    if (style.equals("Ribbon") || style.equals("Streamers")) {
                        Vec3d offset = side.multiply(size * 1.5);
                        scene.line(previous.position.add(offset), point.position.add(offset), Palette.alpha(color, (int) (100 * fade)), width);
                        scene.line(previous.position.subtract(offset), point.position.subtract(offset), Palette.alpha(color, (int) (100 * fade)), width);
                    } else if (style.equals("Vapour")) {
                        Vec3d offset = side.multiply(Math.sin(index * 0.7 + clock) * size * 4).add(0, age * 0.15, 0);
                        scene.line(previous.position.add(offset), point.position.add(offset), Palette.alpha(color, (int) (85 * fade)), width + 1);
                    } else if (style.equals("Comet") && index % 3 == 0) scene.diamond(point.position, size * (0.3 + fade), color, width);
                }
            }
            previous = point; index++;
        }
        if (style.equals("Comet")) scene.diamond(points.getLast().position, size * 2, tint(id, natural, time, 0), width + 1);
    }
    private static void lightningScene(Scene scene, Effect effect, int color, double fade, double seconds) {
        Vec3d base = effect.position; double height = effect.size;
        if (effect.text.equals("Column")) {
            Box column = new Box(base.x - 0.18, base.y, base.z - 0.18, base.x + 0.18, base.y + height, base.z + 0.18);
            scene.fillBox(column, Palette.alpha(color, (int) (45 * fade))); scene.box(column, color, 3);
        } else if (effect.text.equals("Spear")) {
            scene.line(base, base.add(0, height, 0), color, 4);
            scene.diamond(base.add(0, height * 0.16, 0), 0.8 * fade + 0.1, color, 2);
            scene.tiltedCircle(base.add(0, height * 0.3, 0), 0.7, seconds * 6, Math.PI / 4, color, 2);
        } else {
            Vec3d previous = base;
            for (int i = 1; i <= 14; i++) {
                Vec3d next = base.add((seeded(i + (int) effect.start, 7) - 0.5) * 2, height * i / 14,
                    (seeded(i + (int) effect.start, 8) - 0.5) * 2);
                scene.line(previous, next, color, 3); previous = next;
            }
        }
        int branches = (int) value("storm-effects", "branches");
        for (int i = 0; i < branches; i++) {
            double angle = seeded(i + (int) effect.start, 9) * Math.PI * 2;
            Vec3d origin = base.add(0, height * (0.2 + seeded(i, 10) * 0.6), 0), previous = origin;
            for (int j = 1; j <= 4; j++) {
                Vec3d next = origin.add(Math.cos(angle) * j * 0.75, -j * height * 0.025 + Math.sin(j * 2 + i) * 0.3, Math.sin(angle) * j * 0.75);
                scene.line(previous, next, Palette.alpha(color, (int) (180 * fade)), 1.5f); previous = next;
            }
        }
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
        if (enabled("storm-effects") && stormAt != 0 && core.getBoolean("storm-effects", "flash")) {
            double age = (time - stormAt) / (Math.min(0.7, duration("storm-effects")) * 1_000_000_000d);
            if (age < 1) {
                int color = tint("storm-effects", 0xFFE7F7FF, time, 0);
                draw.fill(0, 0, width, height, Palette.alpha(color, (int) (value("storm-effects", "flash-strength") * 200 * Math.pow(1 - age, 3))));
            }
        }
        if (enabled("vitals") && vitalsWarning) {
            int natural = CLIENT.player.isOnFire() && core.getBoolean("vitals", "burning") ? GOLD
                : CLIENT.player.isSubmergedInWater() && CLIENT.player.getAir() < 60 && core.getBoolean("vitals", "drowning") ? CYAN : PINK;
            String style = core.getChoice("vitals", "style");
            double beat = style.equals("Steady") ? 0.85 : 0.5 + Math.pow(Math.max(0, Math.sin(time / 1_000_000_000d * 7.5)), 3) * 0.5;
            warningEdges(draw, width, height, tint("vitals", natural, time, 0), value("vitals", "strength") * beat, style);
        }
        if (enabled("damage-effects") && core.getBoolean("damage-effects", "low-health") && CLIENT.player.isAlive()
                && CLIENT.player.getHealth() <= value("damage-effects", "threshold")) {
            double beat = 0.45 + 0.55 * Math.pow(Math.max(0, Math.sin(time / 1_000_000_000d * 6)), 2);
            warningEdges(draw, width, height, tint("damage-effects", PINK, time, 0), beat * 0.65, "Pulse");
        }
        if (enabled("health-pulse") && hurtAt != 0) {
            double age = (time - hurtAt) / (duration("health-pulse") * 1_000_000_000d);
            if (age < 1) warningEdges(draw, width, height, tint("health-pulse", PINK, time, age),
                value("health-pulse", "intensity") * Math.pow(1 - age, 2), core.getChoice("health-pulse", "style"));
        }
        if (enabled("hit-markers") && hitAt != 0) {
            double age = (time - hitAt) / (duration("hit-markers") * 1_000_000_000d);
            if (age < 1) {
                double size = value("hit-markers", "size") * (1 + age * 0.35), x = width / 2d, y = height / 2d;
                int color = Palette.alpha(tint("hit-markers", GOLD, time, age), (int) (255 * (1 - age)));
                double angle = (time - hitAt) / 1_000_000_000d * value("hit-markers", "spin"), cos = Math.cos(angle), sin = Math.sin(angle);
                String style = core.getChoice("hit-markers", "style"); float thickness = (float) value("hit-markers", "thickness");
                for (int sx : new int[]{-1, 1}) for (int sy : new int[]{-1, 1}) {
                    if (style.equals("Dots")) {
                        double dx = sx * size * 0.8, dy = sy * size * 0.8;
                        GuiDraw.glowDot(draw, x + dx * cos - dy * sin, y + dx * sin + dy * cos, thickness, color, color >>> 24);
                    } else if (style.equals("Corners")) {
                        markerSegment(draw, x, y, sx * size * 0.5, sy * size, sx * size, sy * size, angle, thickness, color);
                        markerSegment(draw, x, y, sx * size, sy * size * 0.5, sx * size, sy * size, angle, thickness, color);
                    } else markerSegment(draw, x, y, sx * size * 0.55, sy * size * 0.55, sx * size, sy * size, angle, thickness, color);
                }
            }
        }
    }

    private static void markerSegment(DrawContext draw, double cx, double cy, double x1, double y1, double x2, double y2,
                                      double angle, float thickness, int color) {
        double cos = Math.cos(angle), sin = Math.sin(angle);
        GuiDraw.stroke(draw, cx + x1 * cos - y1 * sin, cy + x1 * sin + y1 * cos,
            cx + x2 * cos - y2 * sin, cy + x2 * sin + y2 * cos, thickness, color);
    }
    private static void warningEdges(DrawContext draw, int width, int height, int color, double intensity, String style) {
        int alpha = (int) (125 * Math.clamp(intensity, 0, 1));
        if (alpha <= 0) return;
        if (style.equals("Screen")) draw.fill(0, 0, width, height, Palette.alpha(color, alpha / 5));
        int depth = Math.min(18, Math.max(1, Math.min(width, height) / 8));
        for (int layer = 0; layer < depth; layer++) {
            int shade = Palette.alpha(color, (int) (alpha * Math.pow(1 - layer / (double) depth, 2)));
            if (style.equals("Corners")) {
                int armX = Math.max(4, width / 5), armY = Math.max(4, height / 5);
                draw.fill(layer, layer, armX, layer + 1, shade); draw.fill(width - armX, layer, width - layer, layer + 1, shade);
                draw.fill(layer, height - layer - 1, armX, height - layer, shade); draw.fill(width - armX, height - layer - 1, width - layer, height - layer, shade);
                draw.fill(layer, layer, layer + 1, armY, shade); draw.fill(width - layer - 1, layer, width - layer, armY, shade);
                draw.fill(layer, height - armY, layer + 1, height - layer, shade); draw.fill(width - layer - 1, height - armY, width - layer, height - layer, shade);
            } else {
                draw.fill(layer, layer, width - layer, layer + 1, shade); draw.fill(layer, height - layer - 1, width - layer, height - layer, shade);
                draw.fill(layer, layer, layer + 1, height - layer, shade); draw.fill(width - layer - 1, layer, width - layer, height - layer, shade);
            }
        }
    }

    private enum Kind { PLACE, BREAK, SPARK, RING, LABEL, PICKUP, LINK, BOLT }
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
    private static final class ContrailPath {
        final Deque<TrailPoint> left = new ArrayDeque<>(), right = new ArrayDeque<>();
        int color = CYAN;
    }
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
        void fillBox(Box box, int color) {
            if (boxes.size() < 2048) boxes.add(new ColoredBox(box, color));
        }
        void rotatedBox(Box box, double angle, int color, float width) {
            Vec3d center = box.getCenter(); Vec3d[] corners = new Vec3d[8];
            for (int i = 0; i < 8; i++) {
                double x = ((i & 1) == 0 ? box.minX : box.maxX) - center.x;
                double z = ((i & 4) == 0 ? box.minZ : box.maxZ) - center.z;
                corners[i] = center.add(x * Math.cos(angle) - z * Math.sin(angle), ((i & 2) == 0 ? box.minY : box.maxY) - center.y,
                    x * Math.sin(angle) + z * Math.cos(angle));
            }
            for (int i = 0; i < EDGES.length; i += 2) line(corners[EDGES[i]], corners[EDGES[i + 1]], color, width);
        }
        void corners(Box box, int color, float width) {
            Vec3d[] points = new Vec3d[8];
            for (int i = 0; i < 8; i++) points[i] = new Vec3d((i & 1) == 0 ? box.minX : box.maxX, (i & 2) == 0 ? box.minY : box.maxY, (i & 4) == 0 ? box.minZ : box.maxZ);
            for (int i = 0; i < EDGES.length; i += 2) {
                Vec3d start = points[EDGES[i]], end = points[EDGES[i + 1]];
                line(start, start.lerp(end, 0.24), color, width); line(end, end.lerp(start, 0.24), color, width);
            }
        }
        void diamond(Vec3d center, double size, int color, float width) {
            Vec3d top = center.add(0, size * 1.5, 0), bottom = center.add(0, -size * 1.5, 0);
            for (int i = 0; i < 4; i++) {
                double a = i * Math.PI / 2, b = (i + 1) * Math.PI / 2;
                Vec3d rim = center.add(Math.cos(a) * size, 0, Math.sin(a) * size), next = center.add(Math.cos(b) * size, 0, Math.sin(b) * size);
                line(top, rim, color, width); line(bottom, rim, color, width); line(rim, next, color, width);
            }
        }
        void tiltedCircle(Vec3d center, double radius, double yaw, double tilt, int color, float width) {
            Vec3d previous = null;
            for (int i = 0; i <= 40; i++) {
                double angle = i * Math.PI / 20, x = Math.cos(angle) * radius, z = Math.sin(angle) * radius * Math.cos(tilt);
                Vec3d point = center.add(x * Math.cos(yaw) - z * Math.sin(yaw), Math.sin(angle) * radius * Math.sin(tilt), x * Math.sin(yaw) + z * Math.cos(yaw));
                if (previous != null) line(previous, point, color, width); previous = point;
            }
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
