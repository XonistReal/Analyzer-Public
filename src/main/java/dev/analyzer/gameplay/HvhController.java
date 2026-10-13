package dev.analyzer.gameplay;

import dev.analyzer.AnalyzerClient;
import dev.analyzer.render.HvhPreview;
import net.minecraft.block.Blocks;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static dev.analyzer.gameplay.GameplayEngine.*;

/** Bounded vanilla-interaction HVH controllers; all state is scoped to the current world. */
final class HvhController {
    private static final List<Direction> SIDES = List.of(Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST);
    private static final Map<BlockPos, Long> ownCrystals = new HashMap<>(), ownAnchors = new HashMap<>();
    private static final Set<UUID> ownCrystalEntities = new HashSet<>();
    private static final Map<String, Long> deadlines = new HashMap<>();
    private static final Map<Integer, Vec3d> previousPositions = new HashMap<>();
    private static List<BlockPos> holeCandidates = List.of();
    private static World controllerWorld;
    private static BlockPos surroundStart;
    private static long nextHoleScan, steeringTick = -1, lastDirectionChange;
    private static Vec3d steering;
    private static boolean strafeGround;
    private static int orbitSign = 1;
    private HvhController() { }

    static void reset() {
        ownCrystals.clear(); ownCrystalEntities.clear(); ownAnchors.clear(); deadlines.clear(); previousPositions.clear();
        holeCandidates = List.of(); controllerWorld = null; surroundStart = null;
        nextHoleScan = 0; steeringTick = -1; steering = null; lastDirectionChange = 0;
        strafeGround = false; orbitSign = 1; HvhPreview.clear();
    }
    /** Movement modules can retain the accepted orbit direction instead of overwriting it. */
    static Vec3d steeringDirection() { return steeringTick == ticks ? steering : null; }

    static void hvh() {
        if (controllerWorld != mc.world) { reset(); controllerWorld = mc.world; }
        steeringTick = -1; steering = null;
        synchronizeOwnership();
        ownAnchors.entrySet().removeIf(e -> ticks - e.getValue() > 1200 || ticks - e.getValue() > 20 && !mc.world.getBlockState(e.getKey()).isOf(Blocks.RESPAWN_ANCHOR));
        if (!on("crystal-aura")) { ownCrystals.clear(); ownCrystalEntities.clear(); }
        if (!on("anchor-aura")) ownAnchors.clear();
        if (on("surround") && !paused("surround")) surround(); else surroundStart = null;
        for (String id : List.of("auto-trap", "auto-web", "crystal-aura", "anchor-aura", "target-strafe")) {
            if (!on(id) || paused(id)) continue;
            LivingEntity enemy = findTarget(id);
            if (enemy == null) continue;
            target = enemy;
            if (id.equals("target-strafe")) { strafe(enemy); continue; }
            if (id.equals("crystal-aura")) { crystals(enemy); continue; }
            if (!due(id, (int) num(id, "delay"))) continue;
            switch (id) {
                case "auto-trap" -> trap(enemy);
                case "auto-web" -> web(enemy);
                case "anchor-aura" -> anchors(enemy);
                default -> { }
            }
        }
        if (on("hole-filler") && !paused("hole-filler")) holes();
        else { holeCandidates = List.of(); nextHoleScan = 0; }
        Set<Integer> seen = new HashSet<>(); int examined = 0;
        for (Entity entity : mc.world.getEntities()) {
            if (++examined > 512) break;
            if (entity instanceof LivingEntity) { seen.add(entity.getId()); previousPositions.put(entity.getId(), entity.getPos()); }
        }
        previousPositions.keySet().retainAll(seen);
    }
    private static void synchronizeOwnership() {
        // A crystal has no server-provided owner. Bind only an accepted placement's exact spawn
        // position during the following second, then retain its UUID only while that entity exists.
        ownCrystals.entrySet().removeIf(e -> ticks - e.getValue() > 20);
        if (ownCrystals.isEmpty() && ownCrystalEntities.isEmpty()) return;
        Set<UUID> seen = new HashSet<>(); int examined = 0;
        for (Entity entity : mc.world.getEntities()) {
            if (++examined > 512) break;
            if (!(entity instanceof EndCrystalEntity) || !entity.isAlive()) continue;
            seen.add(entity.getUuid());
            var iterator = ownCrystals.entrySet().iterator();
            while (iterator.hasNext()) {
                var placement = iterator.next();
                if (entity.getPos().squaredDistanceTo(crystalOrigin(placement.getKey())) < .0001) {
                    ownCrystalEntities.add(entity.getUuid()); iterator.remove(); break;
                }
            }
        }
        ownCrystalEntities.retainAll(seen);
    }
    private static boolean paused(String id) {
        if (boolOr(id, "pause-eating", false) && mc.player.isUsingItem()) { note(id, "Paused while using an item"); return true; }
        double health = numOr(id, "pause-health", 0);
        if (health > 0 && mc.player.getHealth() <= health) { note(id, "Paused below health threshold"); return true; }
        return false;
    }
    private static boolean due(String key, int delay) {
        if (!available(key)) return false;
        deadlines.put(key, ticks + Math.max(1, delay)); return true;
    }
    private static boolean available(String key) { return ticks >= deadlines.getOrDefault(key, 0L); }
    private static void completed(String key, int delay) { deadlines.put(key, ticks + Math.max(1, delay)); }
    private static void disable(String id, String reason) { core.setEnabled(id, false); AnalyzerClient.saveConfig(); note(id, reason); }
    private static int blockSlot(String id) {
        return hotbar(stack -> switch (choiceOr(id, "block", "Obsidian")) {
            case "Ender chest" -> stack.isOf(Items.ENDER_CHEST);
            case "Any hard" -> stack.isOf(Items.OBSIDIAN) || stack.isOf(Items.CRYING_OBSIDIAN) || stack.isOf(Items.ENDER_CHEST) || stack.isOf(Items.NETHERITE_BLOCK);
            case "Any" -> stack.getItem() instanceof BlockItem item && item.getBlock().getDefaultState().isSolidBlock(mc.world, mc.player.getBlockPos());
            default -> stack.isOf(Items.OBSIDIAN);
        });
    }
    private static int budget(String id) { return HvhPolicy.placementBudget((int) numOr(id, "blocks-per-tick", 1), interactionBudget, boolOr(id, "legit", false)); }
    private static void placeMany(String id, List<BlockPos> positions, int slot, boolean support) {
        int limit = budget(id), count = 0;
        if (slot < 0) { note(id, "Need selected building material in hotbar"); return; }
        for (BlockPos pos : positions) {
            if (count >= limit || interactionBudget <= 0) return;
            if (!mc.world.getBlockState(pos).isReplaceable()) continue;
            if (support && mc.world.getBlockState(pos.down()).isReplaceable() && place(pos.down(), slot, id)) {
                count++; HvhPreview.accepted(id, pos.down());
            }
            if (count < limit && place(pos, slot, id)) { count++; HvhPreview.accepted(id, pos); }
        }
    }

    private static void surround() {
        String id = "surround"; var p = mc.player; BlockPos feet = p.getBlockPos();
        if (surroundStart == null) surroundStart = feet.toImmutable();
        if (bool(id, "off-jump") && !p.isOnGround() && p.getVelocity().y > .05) { disable(id, "Disabled after jump"); return; }
        if (bool(id, "off-moved") && !feet.equals(surroundStart)) { disable(id, "Disabled after leaving starting block"); return; }
        if (bool(id, "center") && p.isOnGround() && !anyMovementKey()) {
            Vec3d delta = Vec3d.ofBottomCenter(feet).subtract(p.getPos()).multiply(1, 0, 1);
            if (delta.lengthSquared() > .02) {
                Vec3d step = delta.normalize().multiply(.07);
                if (safeStep(step)) { p.setVelocity(step.x, p.getVelocity().y, step.z); movementChanges++; acted(id); }
                note(id, "Centering before placement"); return;
            }
        }
        Set<BlockPos> occupied = new LinkedHashSet<>(); occupied.add(feet);
        if (bool(id, "extend")) {
            Box box = p.getBoundingBox();
            for (int x = (int) Math.floor(box.minX + .001); x <= (int) Math.floor(box.maxX - .001); x++)
                for (int z = (int) Math.floor(box.minZ + .001); z <= (int) Math.floor(box.maxZ - .001); z++) occupied.add(new BlockPos(x, feet.getY(), z));
        }
        Set<BlockPos> shell = new LinkedHashSet<>(); String shape = choice(id, "shape");
        for (BlockPos cell : occupied) {
            if (bool(id, "floor")) shell.add(cell.down());
            for (Direction side : SIDES) {
                BlockPos wall = cell.offset(side); if (occupied.contains(wall)) continue;
                shell.add(wall);
                if (shape.equals("Double")) shell.add(wall.up());
                if (shape.equals("Anti-city")) shell.add(wall.offset(side));
            }
            if (bool(id, "self-trap")) shell.add(cell.up(2));
        }
        List<BlockPos> positions = List.copyOf(shell); HvhPreview.pending(id, positions);
        if (positions.stream().noneMatch(pos -> mc.world.getBlockState(pos).isReplaceable())) {
            if (bool(id, "off-done")) disable(id, "Surround complete"); return;
        }
        if (due(id, (int) num(id, "delay"))) placeMany(id, positions, blockSlot(id), bool(id, "support"));
    }
    private static void trap(LivingEntity enemy) {
        String id = "auto-trap"; BlockPos feet = enemy.getBlockPos();
        if (bool(id, "only-hole") && !inHole(feet)) { note(id, "Waiting for target in a supported hole"); return; }
        String shape = choice(id, "shape"); List<BlockPos> positions = new ArrayList<>();
        for (Direction side : SIDES) {
            if (shape.startsWith("Full")) positions.add(feet.offset(side));
            if (!shape.equals("Top")) positions.add(feet.offset(side).up());
        }
        if (shape.equals("Top") || shape.equals("Full + roof")) positions.add(feet.up(2));
        HvhPreview.pending(id, positions);
        if (positions.stream().noneMatch(pos -> mc.world.getBlockState(pos).isReplaceable())) {
            if (bool(id, "off-done")) disable(id, "Trap complete"); return;
        }
        placeMany(id, positions, blockSlot(id), true);
    }
    private static void web(LivingEntity primary) {
        String id = "auto-web"; List<BlockPos> positions = new ArrayList<>();
        List<LivingEntity> enemies = bool(id, "every-enemy") ? CombatController.targets(id, num(id, "range")) : List.of(primary);
        for (LivingEntity enemy : enemies) {
            Vec3d previous = previousPositions.get(enemy.getId());
            double motion = previous == null ? enemy.getVelocity().horizontalLength() : enemy.getPos().subtract(previous).horizontalLength();
            if (bool(id, "moving-only") && motion < num(id, "motion-threshold")) continue;
            if (bool(id, "feet")) positions.add(enemy.getBlockPos());
            if (bool(id, "head")) positions.add(enemy.getBlockPos().up());
            if (positions.size() >= 32) break;
        }
        HvhPreview.pending(id, positions);
        placeMany(id, positions, hotbar(stack -> stack.isOf(Items.COBWEB)), false);
    }

    private static void crystals(LivingEntity enemy) {
        String id = "crystal-aura"; var p = mc.player;
        if (p.getHealth() < num(id, "health")) { note(id, "Below minimum health"); return; }
        double selfDistance = num(id, "self-distance");
        if (bool(id, "break") && available(id + ":break") && p.getAttackCooldownProgress(0) >= num(id, "cooldown")) {
            EndCrystalEntity best = null; double bestScore = -1; int examined = 0;
            for (Entity entity : mc.world.getEntities()) {
                if (++examined > 512) break;
                if (!(entity instanceof EndCrystalEntity crystal) || !entity.isAlive() || !p.canInteractWithEntity(entity, 0)
                        || entity.getBoundingBox().squaredMagnitude(p.getEyePos()) > Math.pow(num(id, "break-range"), 2)
                        || p.squaredDistanceTo(entity) < selfDistance * selfDistance
                        || ((!bool(id, "through-walls") || bool(id, "legit")) && !p.canSee(entity))) continue;
                String which = choice(id, "break-which");
                if (which.equals("Own") && !ownCrystalEntities.contains(crystal.getUuid())) continue;
                double score = damageScore(id, enemy, entity.getPos(), 6, null, which.equals("All"));
                if (score > bestScore) { bestScore = score; best = crystal; }
            }
            if (best != null && attack(best, id)) {
                completed(id + ":break", Math.max((int) num(id, "delay"), (int) num(id, "break-delay")));
                HvhPreview.accepted(id, best.getBlockPos());
            }
        }
        if (!bool(id, "place") || !available(id + ":place") || interactionBudget <= 0) return;
        int slot = hotbar(stack -> stack.isOf(Items.END_CRYSTAL));
        if (slot < 0) { note(id, "Need hotbar end crystals"); return; }
        BlockPos best = null; double bestScore = -1; BlockPos feet = enemy.getBlockPos();
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
            BlockPos base = feet.add(x, -1, z); var state = mc.world.getBlockState(base); Vec3d origin = crystalOrigin(base);
            if (!(state.isOf(Blocks.OBSIDIAN) || state.isOf(Blocks.BEDROCK)) || !mc.world.getBlockState(base.up()).isAir()
                    || !mc.world.getBlockState(base.up(2)).isAir() || !p.canInteractWithBlockAt(base, 0)
                    || upperFace(base).getPos().squaredDistanceTo(p.getEyePos()) > Math.pow(num(id, "place-range"), 2)
                    || origin.squaredDistanceTo(p.getPos()) < selfDistance * selfDistance
                    || !mc.world.getOtherEntities(null, new Box(base.up()).stretch(0, 1, 0)).isEmpty()) continue;
            double score = damageScore(id, enemy, origin, 6, null, false);
            if (score > bestScore) { best = base; bestScore = score; }
        }
        HvhPreview.pending(id, best == null ? List.of() : List.of(best.up()));
        if (best != null && interact(upperFace(best), slot, id)) {
            ownCrystals.put(best.toImmutable(), ticks); HvhPreview.accepted(id, best.up());
            completed(id + ":place", Math.max((int) num(id, "delay"), (int) num(id, "place-delay")));
        } else if (best == null) note(id, "No reachable crystal meets damage and safety thresholds");
    }
    private static Vec3d crystalOrigin(BlockPos base) { return new Vec3d(base.getX() + .5, base.getY() + 1, base.getZ() + .5); }
    private static void anchors(LivingEntity enemy) {
        String id = "anchor-aura"; var p = mc.player;
        if (mc.world.getDimension().respawnAnchorWorks()) { note(id, "Anchors are respawn points in this dimension"); return; }
        if (p.getHealth() < num(id, "health")) { note(id, "Below minimum health"); return; }
        BlockPos best = null; double bestScore = -1; BlockPos feet = enemy.getBlockPos();
        for (int y = 0; y <= 1; y++) for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            BlockPos pos = feet.add(x, y, z); var state = mc.world.getBlockState(pos); boolean existing = state.isOf(Blocks.RESPAWN_ANCHOR);
            if (!(existing || state.isReplaceable()) || !p.canInteractWithBlockAt(pos, 0)
                    || Vec3d.ofCenter(pos).squaredDistanceTo(p.getPos()) < Math.pow(num(id, "self-distance"), 2)
                    || upperFace(pos).getPos().squaredDistanceTo(p.getEyePos()) > Math.pow(num(id, "place-range"), 2)
                    || (existing && !bool(id, "use-existing") && !ownAnchors.containsKey(pos))) continue;
            if (!existing && !mc.world.getOtherEntities(null, new Box(pos)).isEmpty()) continue;
            double score = damageScore(id, enemy, Vec3d.ofCenter(pos), 5, existing ? pos : null, false);
            // Finish a safe existing anchor before starting another placement cycle.
            if (score >= 0 && existing) score += 100;
            if (score > bestScore) { best = pos; bestScore = score; }
        }
        HvhPreview.pending(id, best == null ? List.of() : List.of(best));
        if (best == null) { note(id, "No reachable anchor meets damage and safety thresholds"); return; }
        var state = mc.world.getBlockState(best); boolean accepted;
        if (state.isOf(Blocks.RESPAWN_ANCHOR)) {
            if (state.get(RespawnAnchorBlock.CHARGES) == 0) accepted = interact(upperFace(best), hotbar(stack -> stack.isOf(Items.GLOWSTONE)), id);
            else {
                if (p.getOffHandStack().isOf(Items.GLOWSTONE) && state.get(RespawnAnchorBlock.CHARGES) < 4) { note(id, "Remove offhand glowstone before detonating"); return; }
                accepted = interact(upperFace(best), hotbar(stack -> !stack.isOf(Items.GLOWSTONE) && !stack.isOf(Items.RESPAWN_ANCHOR)), id);
                if (accepted) ownAnchors.remove(best);
            }
        } else {
            accepted = place(best, hotbar(stack -> stack.isOf(Items.RESPAWN_ANCHOR)), id);
            if (accepted) ownAnchors.put(best.toImmutable(), ticks);
        }
        if (accepted) HvhPreview.accepted(id, best);
    }
    private static double damageScore(String id, LivingEntity enemy, Vec3d origin, float power, BlockPos removedAnchor, boolean allCrystals) {
        double outgoing = HvhDamage.estimate(mc.world, enemy, origin, power, removedAnchor, false);
        double incoming = HvhDamage.estimate(mc.world, mc.player, origin, power, removedAnchor, true);
        boolean face = allCrystals || (numOr(id, "face-place-health", 0) > 0 && enemy.getHealth() <= numOr(id, "face-place-health", 0))
                || (boolOr(id, "face-place-hole", false) && inHole(enemy.getBlockPos()));
        if (!HvhPolicy.damageAllowed(outgoing, incoming, num(id, "min-damage"), num(id, "max-self-damage"),
                mc.player.getHealth() + mc.player.getAbsorptionAmount(), bool(id, "anti-suicide"), face)) return -1;
        if (bool(id, "protect-friends")) {
            for (PlayerEntity friend : mc.world.getPlayers()) {
                if (friend == mc.player || !UtilityEngine.isFriend(friend.getUuid())) continue;
                double damage = HvhDamage.estimate(mc.world, friend, origin, power, removedAnchor, true);
                if (!Double.isFinite(damage) || damage > 0) return -1;
            }
        }
        return Math.max(0, outgoing - incoming * .25);
    }

    private static boolean solid(BlockPos pos) { return !mc.world.getBlockState(pos).getCollisionShape(mc.world, pos).isEmpty(); }
    private static boolean inHole(BlockPos pos) {
        if (!mc.world.getBlockState(pos).isAir() || !mc.world.getBlockState(pos.up()).isAir() || !solid(pos.down())) return false;
        for (Direction side : SIDES) if (!solid(pos.offset(side))) return false;
        return true;
    }
    private static boolean doubleHole(BlockPos a, Direction shared) {
        BlockPos b = a.offset(shared);
        if (!mc.world.getBlockState(a).isAir() || !mc.world.getBlockState(a.up()).isAir() || !mc.world.getBlockState(b).isAir()
                || !mc.world.getBlockState(b.up()).isAir() || !solid(a.down()) || !solid(b.down())) return false;
        for (Direction side : SIDES) {
            if (side != shared && !solid(a.offset(side))) return false;
            if (side != shared.getOpposite() && !solid(b.offset(side))) return false;
        }
        return true;
    }
    private static void holes() {
        String id = "hole-filler"; var p = mc.player; double range = num(id, "range");
        if (ticks >= nextHoleScan) {
            nextHoleScan = ticks + (int) num(id, "scan-delay"); Set<BlockPos> candidates = new LinkedHashSet<>();
            BlockPos center = p.getBlockPos(); int radius = (int) Math.ceil(range), examined = 0;
            outer: for (int y = -1; y <= 1; y++) for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
                if (++examined > 512) break outer;
                BlockPos pos = center.add(x, y, z);
                if (Vec3d.ofCenter(pos).squaredDistanceTo(p.getPos()) > range * range || !loaded(pos)) continue;
                if (inHole(pos)) candidates.add(pos);
                if (bool(id, "double-holes")) for (Direction side : List.of(Direction.EAST, Direction.SOUTH)) {
                    if (loaded(pos.offset(side)) && doubleHole(pos, side)) { candidates.add(pos); candidates.add(pos.offset(side)); }
                }
            }
            holeCandidates = candidates.stream().sorted(Comparator.comparingDouble(pos -> Vec3d.ofCenter(pos).squaredDistanceTo(p.getPos()))).toList();
        }
        List<LivingEntity> enemies = nearbyEnemies(range + num(id, "enemy-distance")); List<BlockPos> allowed = new ArrayList<>();
        for (BlockPos pos : holeCandidates) {
            if (!loaded(pos) || Vec3d.ofCenter(pos).squaredDistanceTo(p.getPos()) > range * range) continue;
            boolean hole = inHole(pos);
            if (!hole && bool(id, "double-holes")) for (Direction side : SIDES) if (loaded(pos.offset(side)) && doubleHole(pos, side)) { hole = true; break; }
            if (!hole || !mc.world.getOtherEntities(null, new Box(pos).stretch(0, 1, 0)).isEmpty()) continue;
            double self = Vec3d.ofCenter(pos).distanceTo(p.getPos()), friend = Double.POSITIVE_INFINITY;
            for (PlayerEntity player : mc.world.getPlayers()) if (player != p && UtilityEngine.isFriend(player.getUuid())) friend = Math.min(friend, Vec3d.ofCenter(pos).distanceTo(player.getPos()));
            if (HvhPolicy.protectedHole(self, friend, bool(id, "keep-yours"), bool(id, "protect-friends"))) continue;
            if (bool(id, "near-enemies") && enemies.stream().noneMatch(enemy -> Vec3d.ofCenter(pos).squaredDistanceTo(enemy.getPos()) <= Math.pow(num(id, "enemy-distance"), 2))) continue;
            allowed.add(pos); if (allowed.size() >= 32) break;
        }
        HvhPreview.pending(id, allowed);
        if (due(id, (int) num(id, "delay"))) placeMany(id, allowed, blockSlot(id), false);
    }
    private static boolean loaded(BlockPos pos) { return mc.world.getChunkManager().getChunk(pos.getX() >> 4, pos.getZ() >> 4, net.minecraft.world.chunk.ChunkStatus.FULL, false) != null; }
    private static List<LivingEntity> nearbyEnemies(double range) {
        List<LivingEntity> result = new ArrayList<>(); int examined = 0;
        for (Entity entity : mc.world.getEntities()) {
            if (++examined > 512) break;
            if (!(entity instanceof LivingEntity living) || living == mc.player || !living.isAlive() || living.isSpectator()
                    || (!(living instanceof PlayerEntity) && !(living instanceof Monster)) || living.isTeammate(mc.player)
                    || UtilityEngine.isFriend(living.getUuid()) || living.squaredDistanceTo(mc.player) > range * range) continue;
            if (living instanceof TameableEntity tame && tame.isTamed()) continue;
            result.add(living);
        }
        return result;
    }

    private static boolean anyMovementKey() {
        return mc.options.forwardKey.isPressed() || mc.options.backKey.isPressed() || mc.options.leftKey.isPressed() || mc.options.rightKey.isPressed();
    }
    private static void strafe(LivingEntity enemy) {
        String id = "target-strafe"; var p = mc.player; String style = choice(id, "style"); boolean ground = p.isOnGround();
        if (ground && !strafeGround && bool(id, "weave") && !style.equals("Circle")) orbitSign *= -1;
        strafeGround = ground;
        if (on("fly") || p.getAbilities().flying || p.isTouchingWater() || p.isClimbing() || (bool(id, "ground-only") && !ground)) return;
        if (bool(id, "respect-wasd") && mc.options.backKey.isPressed()) { note(id, "Paused by backward input"); return; }
        double side = switch (choice(id, "direction")) {
            case "Left" -> -1; case "Right" -> 1; case "Flip" -> orbitSign;
            default -> bool(id, "clockwise") ? 1 : -1;
        };
        if (!style.equals("Circle") && bool(id, "weave") && !choice(id, "direction").equals("Flip")) side *= orbitSign;
        if (bool(id, "respect-wasd")) {
            if (mc.options.leftKey.isPressed()) side = -1;
            if (mc.options.rightKey.isPressed()) side = 1;
        }
        Vec3d delta = enemy.getPos().subtract(p.getPos()).multiply(1, 0, 1); double distance = delta.length();
        if (distance < .1) return;
        double radius = style.equals("Hug") ? num(id, "hug-distance") : num(id, "radius");
        double radial = Math.clamp((distance - radius) * .5, -.8, .8);
        Vec3d direction = new Vec3d(delta.x * radial - delta.z * side, 0, delta.z * radial + delta.x * side).normalize();
        if (style.equals("Blind hop")) {
            double polar = Math.atan2(p.getZ() - enemy.getZ(), p.getX() - enemy.getX()) + Math.toRadians(num(id, "hop-arc")) * side;
            double front = Math.toRadians(enemy.getYaw() + 90), cone = Math.toRadians(num(id, "view-cone") / 2);
            if (Math.abs(Math.atan2(Math.sin(polar - front), Math.cos(polar - front))) < cone) polar = front + cone * side;
            Vec3d destination = enemy.getPos().add(Math.cos(polar) * radius, 0, Math.sin(polar) * radius);
            direction = destination.subtract(p.getPos()).multiply(1, 0, 1).normalize();
        }
        double cap = num(id, "speed");
        double normal = p.getAttributeValue(EntityAttributes.MOVEMENT_SPEED) * 2.15;
        if (p.isSprinting()) normal /= 1.3; // avoid applying vanilla's existing sprint modifier twice.
        boolean sprint = bool(id, "sprint") && !CombatController.suppressSprint();
        if (sprint) normal *= 1.3;
        double speed = bool(id, "natural-pace") ? Math.min(cap, normal) : cap;
        Vec3d velocity = direction.multiply(speed);
        if (bool(id, "natural-pace")) velocity = p.getVelocity().multiply(1, 0, 1).lerp(velocity, .35);
        if (velocity.length() > speed) velocity = velocity.normalize().multiply(speed);
        Vec3d acceptedDirection = bool(id, "legit") || velocity.lengthSquared() < 1.0e-6 ? direction : velocity.normalize();
        if (!safeStep(acceptedDirection.multiply(Math.max(.6, speed * 3)))) {
            if (choice(id, "direction").equals("Flip") && ticks - lastDirectionChange >= 2 + (long) (num(id, "direction-stability") / 5)) {
                orbitSign *= -1; lastDirectionChange = ticks;
            }
            note(id, "Waiting at collision, unsupported edge or fluid"); return;
        }
        if (bool(id, "legit")) {
            aim(p.getEyePos().add(direction.multiply(3)), num(id, "aim-speed"));
            if (p.getRotationVec(1).multiply(1, 0, 1).normalize().dotProduct(direction) < Math.cos(Math.toRadians(12))) return;
            force(mc.options.forwardKey); if (sprint) force(mc.options.sprintKey);
            Vec3d horizontal = p.getVelocity().multiply(1, 0, 1);
            if (horizontal.length() > speed) {
                horizontal = horizontal.normalize().multiply(speed);
                p.setVelocity(horizontal.x, p.getVelocity().y, horizontal.z); movementChanges++;
            }
        } else {
            p.setVelocity(velocity.x, p.getVelocity().y, velocity.z); movementChanges++;
            if (sprint) force(mc.options.sprintKey);
        }
        if (ground && (bool(id, "auto-jump") || !style.equals("Circle")) && safeStep(acceptedDirection.multiply(Math.max(1, speed * 5)))) p.jump();
        steering = acceptedDirection; steeringTick = ticks; acted(id); note(id, "Orbiting " + enemy.getName().getString());
    }
    private static boolean safeStep(Vec3d delta) {
        var p = mc.player;
        for (int i = 1; i <= 3; i++) {
            Vec3d part = delta.multiply(i / 3.0); BlockPos feet = BlockPos.ofFloored(p.getPos().add(part));
            if (!loaded(feet) || !mc.world.isSpaceEmpty(p, p.getBoundingBox().offset(part))) return false;
            var floor = mc.world.getBlockState(feet.down());
            if (!solid(feet.down()) || !mc.world.getFluidState(feet).isEmpty() || !mc.world.getFluidState(feet.down()).isEmpty()
                    || floor.isOf(Blocks.MAGMA_BLOCK) || floor.isOf(Blocks.CAMPFIRE) || floor.isOf(Blocks.SOUL_CAMPFIRE)) return false;
        }
        return true;
    }
}
