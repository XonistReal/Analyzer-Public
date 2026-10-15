package dev.analyzer.gameplay;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ChargedProjectilesComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import static dev.analyzer.gameplay.GameplayEngine.*;

/** Original settings-driven normal combat interactions, with bounded acquisition and actions. */
final class CombatController {
    private static final Map<UUID, Long> firstSeen = new HashMap<>();
    private static final Map<UUID, LivingEntity> seenInstances = new HashMap<>();
    private static final Map<String, LivingEntity> locks = new HashMap<>();
    private static final Map<String, Long> switchAt = new HashMap<>(), nextAction = new HashMap<>(), jitterAt = new HashMap<>();
    private static final Map<String, Observation> observations = new HashMap<>();
    private static final Map<String, Boolean> pairs = new HashMap<>();
    private static final Map<String, CombatPolicy.ClickClock> clickClocks = new HashMap<>();
    private static LivingEntity shieldTarget, criticalTarget;
    private static long shieldUntil, criticalSince, nextHop, sprintReleasedAt = -1;
    private static int shieldSlot = -1;
    private static boolean criticalSprint;
    private static boolean ownedBlock;
    private static long blockUntil, blockReleased = -1000, handUntil;
    private static long lastTotemPop = -1;
    private static int lastWeaponSlot = -1;
    private static long weaponSwitchedAt = -1000;
    private record Observation(UUID target, long since) { }
    private CombatController() { }

    static void reset() {
        releaseBlock();
        restoreCriticalSprint();
        firstSeen.clear(); seenInstances.clear(); locks.clear(); switchAt.clear(); nextAction.clear(); jitterAt.clear(); observations.clear(); pairs.clear(); clickClocks.clear();
        shieldTarget = criticalTarget = null; shieldSlot = -1; shieldUntil = criticalSince = nextHop = 0; sprintReleasedAt = -1;
        blockUntil = handUntil = 0; lastWeaponSlot = -1; weaponSwitchedAt = -1000;
        lastTotemPop = -1;
    }
    static void totemPopped() { lastTotemPop = ticks; }
    static void suspend() { releaseBlock(); criticalTarget = null; restoreCriticalSprint(); observations.clear(); locks.clear(); }
    static void yieldForFood() {
        if (ownedBlock && (on("auto-eat") && (autoEatRefilling || mc.player.getHungerManager().getFoodLevel() < num("auto-eat", "hunger"))
                || on("auto-gapple") && mc.player.getHealth() <= num("auto-gapple", "health"))) releaseBlock();
    }
    private static boolean legit(String id) { return boolOr(id, "legit", false); }
    private static Vec3d nearest(Vec3d eye, Box box) {
        return new Vec3d(MathHelper.clamp(eye.x, box.minX, box.maxX), MathHelper.clamp(eye.y, box.minY, box.maxY), MathHelper.clamp(eye.z, box.minZ, box.maxZ));
    }
    private static double distance(LivingEntity entity) { return nearest(mc.player.getEyePos(), entity.getBoundingBox()).distanceTo(mc.player.getEyePos()); }
    private static double angle(Vec3d point) {
        Vec3d delta = point.subtract(mc.player.getEyePos());
        if (delta.lengthSquared() < 1.0e-8) return 0;
        return Math.toDegrees(Math.acos(Math.clamp(mc.player.getRotationVec(1).dotProduct(delta.normalize()), -1, 1)));
    }
    private static Vec3d aimPoint(String id, LivingEntity entity) {
        return switch (choiceOr(id, "aim-point", "Head")) {
            case "Nearest" -> nearest(mc.player.getEyePos(), entity.getBoundingBox());
            case "Chest" -> entity.getPos().add(0, entity.getHeight() * 0.6, 0);
            default -> entity.getEyePos();
        };
    }
    private static boolean eligible(String id, LivingEntity entity, double range) {
        var p = mc.player;
        if (entity == p || !entity.isAlive() || entity.isSpectator() || p.isTeammate(entity)) return false;
        if (entity instanceof PlayerEntity) {
            if (((PlayerEntity) entity).isCreative()) return false;
            if (!boolOr(id, "players", true) || boolOr(id, "ignore-friends", true) && UtilityEngine.isFriend(entity.getUuid())) return false;
            if (boolOr(id, "ignore-naked", false) && entity.getArmor() == 0) return false;
        } else if (entity instanceof Monster) {
            if (!boolOr(id, "hostiles", false)) return false;
        } else if (entity instanceof MobEntity) {
            if (!boolOr(id, "passives", false)) return false;
        } else if (!boolOr(id, "other-living", entity instanceof MobEntity && boolOr(id, "passives", false))) return false;
        if (entity instanceof TameableEntity pet && pet.isTamed()) return false;
        if (boolOr(id, "ignore-invisible", false) && entity.isInvisible()) return false;
        if (seenInstances.put(entity.getUuid(), entity) != entity) firstSeen.put(entity.getUuid(), ticks);
        long appeared = firstSeen.getOrDefault(entity.getUuid(), ticks);
        if (ticks - appeared < numOr(id, "ignore-new", 0)) return false;
        double actualDistance = distance(entity);
        if (actualDistance > range) return false;
        if (!p.canSee(entity)) {
            if (legit(id)) return false;
            double wallRange = numOr(id, "wall-range", 0);
            if (wallRange > 0 ? actualDistance > wallRange : !boolOr(id, "through-walls", false)) return false;
        }
        Vec3d delta = aimPoint(id, entity).subtract(p.getEyePos());
        double yaw = Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90;
        double fov = numOr(id, "fov", 180);
        if (id.equals("kill-aura") && legit(id)) fov = Math.min(fov, 120);
        return Math.abs(MathHelper.wrapDegrees(yaw - p.getYaw())) <= fov * 0.5;
    }
    static List<LivingEntity> targets(String id) { return targets(id, numOr(id, "range", 3)); }
    /** Shared HvH acquisition; no Kill Aura lock/switch side effects. */
    static List<LivingEntity> targets(String id, double rangeOverride) {
        List<LivingEntity> found = new ArrayList<>(); int examined = 0;
        if (firstSeen.size() > 4096) { firstSeen.clear(); seenInstances.clear(); }
        for (LivingEntity entity : mc.world.getEntitiesByClass(LivingEntity.class, mc.player.getBoundingBox().expand(rangeOverride + 2), living -> living != mc.player)) {
            if (++examined > 1024) break;
            if (eligible(id, entity, rangeOverride)) found.add(entity);
        }
        String priority = choiceOr(id, "priority", "Distance");
        found.sort(Comparator.comparingDouble((LivingEntity living) -> CombatPolicy.priority(priority, distance(living), living.getHealth() + living.getAbsorptionAmount(),
            angle(aimPoint(id, living)), living.getArmor(), living.hurtTime)).thenComparingDouble(CombatController::distance).thenComparingInt(Entity::getId));
        return found;
    }
    static LivingEntity findTarget(String id) {
        List<LivingEntity> found = targets(id, numOr(id, "range", 3) + (id.equals("kill-aura") ? numOr(id, "aim-range", 0) : 0));
        if (found.isEmpty()) { locks.remove(id); note(id, "No eligible loaded targets in range"); return null; }
        if (!id.equals("kill-aura")) return found.getFirst();
        if (boolOr(id, "prefer-reachable", true)) {
            List<LivingEntity> reachable = found.stream().filter(entity -> distance(entity) <= numOr(id, "range", 3) && mc.player.canInteractWithEntity(entity, 0)).toList();
            if (!reachable.isEmpty()) found = new ArrayList<>(reachable);
        }
        LivingEntity current = locks.get(id);
        String mode = legit(id) ? "Single" : choiceOr(id, "mode", "Single");
        if (!found.contains(current) || mode.equals("Multi")) current = found.getFirst();
        else if (mode.equals("Switch") && ticks >= switchAt.getOrDefault(id, 0L)) current = found.get((found.indexOf(current) + 1) % found.size());
        if (current != locks.get(id) || !switchAt.containsKey(id)) switchAt.put(id, ticks + CombatPolicy.delayTicks(numOr(id, "switch-delay", 250)));
        locks.put(id, current); return current;
    }
    static boolean validCrosshair(String id, Entity entity) {
        return entity instanceof LivingEntity living && eligible(id, living, numOr(id, "range", 3)) && mc.player.canInteractWithEntity(entity, 0);
    }
    static void aim(Vec3d point, double degrees) { aim("combat-movement", point, degrees); }
    static void aim(String id, Vec3d point, double degrees) { aim(id, point, degrees, 0, 100); }
    private static void aim(String id, Vec3d point, double degrees, double smoothing, double strength) {
        if (!combatControls.camera(id)) return;
        var p = mc.player; Vec3d delta = point.subtract(p.getEyePos());
        if (delta.lengthSquared() < 1.0e-10) return;
        float yaw = delta.horizontalLengthSquared() < 1.0e-10 ? p.getYaw() : (float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90);
        float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z)));
        p.setYaw(p.getYaw() + (float) CombatPolicy.turn(MathHelper.wrapDegrees(yaw - p.getYaw()), degrees, smoothing, strength));
        p.setPitch(MathHelper.clamp(p.getPitch() + (float) CombatPolicy.turn(pitch - p.getPitch(), degrees, smoothing, strength), -90, 90));
    }
    private static void rotate(String id, Vec3d point) {
        if (choiceOr(id, "rotation", "Client").equals("None") && !legit(id)) return;
        aim(id, point, numOr(id, "turn-speed", id.equals("shield-breaker") ? 30 : 12),
            legit(id) ? Math.max(50, numOr(id, "smoothing", 0)) : numOr(id, "smoothing", 0), numOr(id, "strength", 100));
    }
    private static boolean melee(ItemStack stack) {
        String name = Registries.ITEM.getId(stack.getItem()).getPath();
        return name.endsWith("_sword") || name.endsWith("_axe") || stack.isOf(Items.MACE) || stack.isOf(Items.TRIDENT);
    }
    static int weapon(boolean axeOnly) { return selectWeapon(axeOnly, false); }
    private static int selectWeapon(boolean axeOnly, boolean configured) {
        int best = -1; double score = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i); if (stack.isEmpty()) continue;
            String name = Registries.ITEM.getId(stack.getItem()).getPath();
            boolean axe = name.endsWith("_axe"), sword = name.endsWith("_sword"), mace = stack.isOf(Items.MACE), trident = stack.isOf(Items.TRIDENT);
            if (axeOnly ? !axe : !axe && !sword && !mace && !trident) continue;
            if (configured && !(axe && bool("auto-weapon", "axes") || sword && bool("auto-weapon", "swords") || mace && bool("auto-weapon", "maces") || trident && bool("auto-weapon", "tridents"))) continue;
            if (stack.isDamageable() && stack.getDamage() >= stack.getMaxDamage() - 1) continue;
            double value = mace ? 6 : trident ? 9 : axe ? 6 + tier(stack) : 3 + tier(stack);
            String preference = configured ? choice("auto-weapon", "preference") : "Damage estimate";
            if (preference.equals("Sword first") && sword || preference.equals("Axe first") && axe) value += 100;
            if (preference.equals("Durability")) value = stack.isDamageable() ? 100.0 * (stack.getMaxDamage() - stack.getDamage()) / stack.getMaxDamage() : 100;
            if (value > score) { score = value; best = i; }
        }
        return best;
    }
    private static boolean observed(String id, LivingEntity entity, double delay) {
        Observation before = observations.get(id);
        if (before == null || !before.target().equals(entity.getUuid())) { before = new Observation(entity.getUuid(), ticks); observations.put(id, before); }
        return ticks - before.since() >= CombatPolicy.delayTicks(delay);
    }
    private static double reaction(String id) { return Math.max(numOr(id, "reaction-delay", 0), legit(id) ? 150 : 0); }
    private static void observeWeapon() {
        int slot = mc.player.getInventory().selectedSlot;
        if (lastWeaponSlot >= 0 && lastWeaponSlot != slot) weaponSwitchedAt = ticks;
        lastWeaponSlot = slot;
    }
    private static void chooseWeapon(int slot) {
        if (mc.player.getInventory().selectedSlot != slot) weaponSwitchedAt = ticks;
        selectSlot(slot); lastWeaponSlot = slot;
    }
    /** Defenses and an actively aimed projectile reserve control before explosive or melee actions. */
    static boolean prepare() {
        observeWeapon();
        if (!on("auto-block") || yieldBlockToHvh()) releaseBlock();
        else if (ownedBlock && (!mc.player.isUsingItem() || !mc.player.getActiveItem().isOf(Items.SHIELD))) ownedBlock = false;
        if (doubleHand()) { releaseBlock(); criticalTarget = null; restoreCriticalSprint(); return true; }
        if (bow()) { releaseBlock(); criticalTarget = null; restoreCriticalSprint(); return true; }
        return false;
    }
    private static boolean doubleHand() {
        String id = "double-hand"; var p = mc.player;
        if (!on(id)) { handUntil = 0; return false; }
        boolean offhand = p.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING);
        if (bool(id, "without-offhand") && offhand) { handUntil = 0; return false; }
        LivingEntity threat = findTarget(id);
        String mode = choice(id, "mode");
        if (!mode.equals("After totem pop") && CombatPolicy.defend(p.getHealth() + p.getAbsorptionAmount(), num(id, "health"), threat != null && p.canSee(threat), offhand, bool(id, "without-offhand")))
            handUntil = ticks + (long) num(id, "hold-ticks");
        if (!mode.equals("Low health") && lastTotemPop >= 0 && ticks - lastTotemPop <= num(id, "hold-ticks"))
            handUntil = Math.max(handUntil, lastTotemPop + (long) num(id, "hold-ticks"));
        if (handUntil < ticks) return false;
        int slot = hotbar(stack -> stack.isOf(Items.TOTEM_OF_UNDYING));
        if (slot < 0) { note(id, "Need a totem in the hotbar"); handUntil = 0; return false; }
        if (p.isUsingItem() && !ownedBlock) { note(id, "Waiting for the active item use to finish"); return false; }
        if (!combatControls.action(id)) return false;
        chooseWeapon(slot); note(id, "Holding a main-hand totem for the selected protection window"); acted(id); return true;
    }
    private static void releaseBlock() {
        if (!ownedBlock) return;
        if (mc != null && mc.player != null && mc.interactionManager != null && mc.player.isUsingItem()
                && mc.player.getActiveHand() == Hand.OFF_HAND && mc.player.getActiveItem().isOf(Items.SHIELD))
            mc.interactionManager.stopUsingItem(mc.player);
        ownedBlock = false; blockReleased = ticks;
    }
    private static void autoBlock(boolean attackWindow) {
        String id = "auto-block"; var p = mc.player;
        if (!on(id) || yieldBlockToHvh() || attackWindow || ticks <= blockReleased + 1 || !p.getOffHandStack().isOf(Items.SHIELD)
                || boolOr(id, "pause-mining", true) && mc.interactionManager.isBreakingBlock()) { releaseBlock(); return; }
        LivingEntity threat = findTarget(id);
        boolean danger = threat != null && p.canSee(threat) && p.getHealth() + p.getAbsorptionAmount() <= num(id, "health");
        if (danger) blockUntil = ticks + (long) num(id, "release-delay");
        if (!danger && ticks > blockUntil) { releaseBlock(); return; }
        if (p.isUsingItem() && !ownedBlock || !combatControls.canAct(id)) return;
        if (bool(id, "face-threat") && threat != null) rotate(id, aimPoint(id, threat));
        if (!combatControls.action(id)) return;
        if (ownedBlock && p.isUsingItem()) { force(mc.options.useKey); note(id, "Holding the offhand shield between attacks"); return; }
        if (interactionBudget < 1 || p.getItemCooldownManager().isCoolingDown(p.getOffHandStack())) return;
        interactionBudget--;
        var result = mc.interactionManager.interactItem(p, Hand.OFF_HAND);
        if (result.isAccepted() && p.isUsingItem() && p.getActiveHand() == Hand.OFF_HAND) {
            ownedBlock = true; force(mc.options.useKey); acted(id); note(id, "Raised the equipped offhand shield");
        }
    }
    private static boolean yieldBlockToHvh() {
        return on("auto-block") && bool("auto-block", "yield-hvh")
            && (on("crystal-aura") || on("anchor-aura") || on("surround") || on("auto-trap") || on("auto-web") || on("hole-filler"));
    }
    private static boolean autoWeapon(LivingEntity victim, LivingEntity pointed) {
        if (!on("auto-weapon")) { observations.remove("auto-weapon"); return false; }
        LivingEntity wanted = legit("auto-weapon") ? pointed : victim != null ? victim : mc.options.attackKey.isPressed() ? pointed : null;
        if (wanted == null) { observations.remove("auto-weapon"); return false; }
        if (UtilityEngine.isFriend(wanted.getUuid()) || mc.player.isTeammate(wanted) || wanted instanceof PlayerEntity player && player.isCreative() || !wanted.isAlive() || !mc.player.canSee(wanted)) return false;
        if (!observed("auto-weapon", wanted, reaction("auto-weapon"))) { note("auto-weapon", "Waiting for weapon reaction delay"); return true; }
        int slot = selectWeapon(false, true);
        if (slot < 0) { note("auto-weapon", "No allowed usable hotbar weapon"); return false; }
        boolean changed = mc.player.getInventory().selectedSlot != slot;
        chooseWeapon(slot); if (changed) acted("auto-weapon"); note("auto-weapon", "Allowed hotbar weapon selected");
        return !CombatPolicy.switchSettled(ticks, weaponSwitchedAt, (int) numOr("auto-weapon", "settle-ticks", 1)) || changed && legit("auto-weapon");
    }
    private static boolean shield(LivingEntity pointed) {
        String id = "shield-breaker";
        if (!on(id)) { shieldTarget = null; shieldSlot = -1; observations.remove(id); return false; }
        Vec3d facing = pointed == null ? Vec3d.ZERO : pointed.getRotationVec(1), toward = pointed == null ? Vec3d.ZERO : mc.player.getPos().subtract(pointed.getPos());
        boolean blocking = pointed != null && pointed.isAlive() && pointed.isBlocking() && !mc.player.isTeammate(pointed)
            && !(pointed instanceof PlayerEntity player && player.isCreative())
            && mc.player.canSee(pointed) && mc.player.canInteractWithEntity(pointed, 0)
            && (!boolOr(id, "only-click", false) || mc.options.attackKey.isPressed())
            && CombatPolicy.shieldFacing(facing.x, facing.z, toward.x, toward.z)
            && !(bool(id, "ignore-friends") && UtilityEngine.isFriend(pointed.getUuid()));
        if (!blocking) {
            observations.remove(id);
            if (shieldTarget != null && ticks <= shieldUntil && shieldSlot >= 0) chooseWeapon(shieldSlot);
            else { shieldTarget = null; shieldSlot = -1; }
            return false;
        }
        if (!observed(id, pointed, reaction(id))) { note(id, "Waiting for blocking-target reaction delay"); return false; }
        int slot = weapon(true);
        if (slot < 0) { note(id, "Need a usable hotbar axe"); return false; }
        boolean changed = mc.player.getInventory().selectedSlot != slot;
        chooseWeapon(slot); if (changed) acted(id);
        shieldTarget = pointed; shieldSlot = slot; shieldUntil = ticks + CombatPolicy.delayTicks(Math.max(num(id, "release-delay"), legit(id) ? 150 : 0));
        note(id, "Axe selected for blocking target");
        return CombatPolicy.switchSettled(ticks, weaponSwitchedAt, (int) numOr(id, "settle-ticks", 1)) && (!changed || !legit(id));
    }
    private static double clickInterval(String id, String schedule) {
        double cap = numOr(id, "cps", 20); if (legit(id)) cap = Math.min(cap, 12);
        String pattern = choiceOr(id, "pattern", "Random");
        double sample = pattern.equals("Steady") && !legit(id) ? 0.5 : ThreadLocalRandom.current().nextDouble();
        double base = CombatPolicy.cpsInterval(numOr(id, "min-cps", 9), numOr(id, "max-cps", 12), cap, sample);
        if (pattern.equals("Butterfly")) {
            boolean second = pairs.getOrDefault(schedule, false); pairs.put(schedule, !second);
            return second ? Math.max(1, base * 2 - 1) : 1;
        }
        return base;
    }
    private static CombatPolicy.ClickClock clock(String id) { return clickClocks.computeIfAbsent(id, ignored -> new CombatPolicy.ClickClock()); }
    private static boolean timingReady(String id) {
        var p = mc.player;
        if (boolOr(id, "weapon-only", false) && !melee(p.getMainHandStack())) { note(id, "Hold a melee weapon"); return false; }
        if (p.isUsingItem() && !ownedBlock && boolOr(id, "pause-using", true)) { note(id, "Paused while using an item"); return false; }
        if (boolOr(id, "pause-mining", true) && mc.interactionManager.isBreakingBlock()) { note(id, "Paused while breaking a block"); return false; }
        if (!CombatPolicy.switchSettled(ticks, weaponSwitchedAt, (int) numOr(id, "settle-ticks", 1))) { note(id, "Waiting for the held weapon switch to settle"); return false; }
        if (boolOr(id, "wait-for-crit", false) && !p.isOnGround() && !p.isTouchingWater() && !p.isClimbing() && p.getVelocity().y >= 0) return false;
        String timing = choiceOr(id, "timing", "Cooldown");
        boolean respect = !id.equals("auto-clicker") || bool(id, "respect-cooldown");
        double threshold = numOr(id, "cooldown", 0.95);
        if (id.equals("trigger-bot") && legit(id)) threshold = Math.max(threshold, 0.9 + (Math.sin(ticks * 0.73) + 1) * 0.05);
        double hitCharge = numOr(id, "hit-charge", threshold);
        if (id.equals("trigger-bot") && legit(id)) hitCharge = Math.max(hitCharge, threshold);
        if (!CombatPolicy.charged(timing, p.getAttackCooldownProgress(0), threshold, hitCharge, respect)) {
            jitterAt.remove(id); note(id, "Waiting for attack charge"); return false;
        }
        if (ticks < nextAction.getOrDefault(id, 0L)) return false;
        if (timing.equals("CPS") && !clock(id).ready(ticks)) return false;
        int jitter = CombatPolicy.delayTicks(numOr(id, "attack-jitter", 0));
        long due = jitterAt.computeIfAbsent(id, ignored -> ticks + (jitter == 0 ? 0 : ThreadLocalRandom.current().nextInt(jitter + 1)));
        if (ticks < due) { note(id, "Waiting for attack jitter"); return false; }
        return true;
    }
    private static void scheduled(String id) {
        jitterAt.remove(id); nextAction.put(id, ticks + 1);
        if (choiceOr(id, "timing", "Cooldown").equals("CPS")) clock(id).schedule(ticks, clickInterval(id, id));
    }
    private static void rightClicker() {
        String id = "auto-clicker";
        if (!on(id) || choice(id, "button").equals("Left") || !physical(mc.options.useKey) || mc.player.isUsingItem() || interactionBudget < 1 || !combatControls.canAct(id)) return;
        String schedule = id + ":right";
        if (!clock(schedule).ready(ticks)) return;
        if (legit(id) && ThreadLocalRandom.current().nextInt(20) == 0) return;
        if (!combatControls.action(id)) return;
        clock(schedule).schedule(ticks, clickInterval(id, schedule)); interactionBudget--;
        var result = mc.crosshairTarget instanceof BlockHitResult hit
            ? mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit)
            : mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        if (result.isAccepted()) { swing(id); acted(id); note(id, "Ordinary held-use interaction accepted"); }
    }
    private static boolean bow() {
        String id = "bow-aimbot"; var p = mc.player;
        if (!on(id)) return false;
        ItemStack active = p.isUsingItem() ? p.getActiveItem() : p.getMainHandStack();
        double speed, gravity = 0.05, charge = 1;
        if (active.isOf(Items.BOW) && p.isUsingItem() && bool(id, "bows")) {
            charge = BowItem.getPullProgress(p.getItemUseTime()); speed = Math.max(0.2, charge * 3);
        } else if (active.isOf(Items.TRIDENT) && p.isUsingItem() && bool(id, "tridents")) {
            charge = Math.min(1, p.getItemUseTime() / 10.0); speed = 2.5;
        } else if (active.isOf(Items.CROSSBOW) && bool(id, "crossbows")
                && !active.getOrDefault(DataComponentTypes.CHARGED_PROJECTILES, ChargedProjectilesComponent.DEFAULT).getProjectiles().isEmpty()) {
            speed = 3.15;
            if (active.get(DataComponentTypes.CHARGED_PROJECTILES).getProjectiles().stream().anyMatch(stack -> stack.isOf(Items.FIREWORK_ROCKET))) { speed = 1.6; gravity = 0; }
        } else return false;
        if (charge < num(id, "min-charge")) { note(id, "Waiting for configured projectile draw"); return p.isUsingItem(); }
        LivingEntity victim = findTarget(id); if (victim == null) return p.isUsingItem();
        Vec3d point = aimPoint(id, victim); String prediction = choice(id, "prediction");
        double flight = CombatPolicy.flightTicks(point.distanceTo(p.getEyePos()), speed);
        if (!prediction.equals("Direct") && bool(id, "predict-movement")) {
            point = point.add(victim.getVelocity().multiply(flight * num(id, "lead-factor")));
            flight = CombatPolicy.flightTicks(point.distanceTo(p.getEyePos()), speed);
        }
        if (prediction.equals("Lead and drop")) {
            Vec3d delta = point.subtract(p.getEyePos());
            double rise = CombatPolicy.lowArcRise(delta.horizontalLength(), delta.y, speed, gravity);
            if (!Double.isFinite(rise)) { note(id, "Target is beyond the estimated projectile arc at this draw strength"); return p.isUsingItem(); }
            point = new Vec3d(point.x, p.getEyeY() + rise, point.z);
        }
        rotate(id, point); acted(id); target = victim; note(id, "Camera projectile estimate for " + victim.getName().getString());
        return true;
    }
    private static void restoreCriticalSprint() {
        if (criticalSprint && mc != null && mc.player != null && mc.currentScreen == null && mc.options.sprintKey.isPressed()) mc.player.setSprinting(true);
        criticalSprint = false; sprintReleasedAt = -1;
    }
    /** Movement controllers must not immediately reassert sprint during the falling-hit wait. */
    static boolean suppressSprint() { return criticalTarget != null && on("criticals") && bool("criticals", "sprint-reset"); }
    private static boolean criticals(LivingEntity victim, String attacker) {
        String id = "criticals"; var p = mc.player;
        String kinds = choiceOr(id, "crit-targets", "Living");
        if (!on(id) || boolOr(id, "only-kill-aura", false) && !attacker.equals("kill-aura")
            || kinds.equals("Players") && !(victim instanceof PlayerEntity) || kinds.equals("Hostiles") && !(victim instanceof Monster)
            || p.isTouchingWater() || p.isInLava() || p.isClimbing() || p.getAbilities().flying || p.isGliding() || p.hasStatusEffect(StatusEffects.BLINDNESS)) {
            criticalTarget = null; restoreCriticalSprint(); return true;
        }
        if (p.getAttackCooldownProgress(0) < num(id, "min-charge")) return false;
        if (criticalTarget != victim) { criticalTarget = victim; criticalSince = ticks; sprintReleasedAt = -1; }
        if (ticks - criticalSince > num(id, "hold-time")) {
            criticalTarget = null; restoreCriticalSprint(); note(id, "Falling-hit wait expired; this attack was cancelled"); return false;
        }
        if (bool(id, "sprint-reset")) {
            boolean wasSprinting = p.isSprinting(); criticalSprint |= wasSprinting; p.setSprinting(false);
            forcedKeys.putIfAbsent(mc.options.sprintKey, mc.options.sprintKey.isPressed()); mc.options.sprintKey.setPressed(false);
            if (sprintReleasedAt < 0) {
                sprintReleasedAt = ticks;
                if (wasSprinting) { note(id, "Releasing normal sprint before the falling attack"); return false; }
            }
        }
        if (CombatPolicy.falling(p.isOnGround(), p.getVelocity().y, p.fallDistance)) return true;
        String mode = legit(id) ? "Manual fall" : choice(id, "mode");
        boolean moving = Math.hypot(p.getVelocity().x, p.getVelocity().z) > 0.05;
        if (p.isOnGround() && !mode.equals("Manual fall") && ticks >= nextHop && (bool(id, "hop-while-moving") || !moving)) {
            p.jump(); if (mode.equals("Mini jump")) p.setVelocity(p.getVelocity().x, num(id, "hop-height"), p.getVelocity().z);
            nextHop = ticks + CombatPolicy.delayTicks(num(id, "min-interval")); acted(id); note(id, "Real hop; waiting for a falling charged attack");
        } else note(id, mode.equals("Manual fall") ? "Waiting for your own falling motion" : "Waiting for a supported falling attack");
        return false;
    }
    private static boolean acquisitionAllowed(String id) {
        if (boolOr(id, "pause-mining", true) && mc.interactionManager.isBreakingBlock()) { note(id, "Paused while breaking a block"); return false; }
        if (mc.player.isUsingItem() && !ownedBlock && boolOr(id, "pause-using", true)) { note(id, "Paused while using an item"); return false; }
        if (boolOr(id, "only-click", false) && !physical(mc.options.attackKey)) { note(id, "Hold attack to acquire a target"); return false; }
        return true;
    }
    private static boolean cameraHits(String id, LivingEntity victim) {
        if (!boolOr(id, "require-ray", true)) return true;
        Vec3d start = mc.player.getEyePos();
        Vec3d end = start.add(mc.player.getRotationVec(1).multiply(Math.min(numOr(id, "range", 3), mc.player.getEntityInteractionRange())));
        var point = victim.getBoundingBox().raycast(start, end);
        if (point.isEmpty() && !victim.getBoundingBox().contains(start)) return false;
        if (allowsObstructed(id)) return true;
        Vec3d impact = point.orElse(start);
        var block = mc.world.raycast(new RaycastContext(start, impact, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
        return block.getType() == HitResult.Type.MISS || block.getPos().squaredDistanceTo(start) + .0001 >= impact.squaredDistanceTo(start);
    }
    private static void assist() {
        String id = "aim-assist"; var p = mc.player;
        if (!on(id) || !combatControls.canAct(id) || !acquisitionAllowed(id)
                || (bool(id, "hold-attack") || legit(id)) && !physical(mc.options.attackKey)
                || bool(id, "weapon-only") && !melee(p.getMainHandStack())) return;
        LivingEntity assisted = findTarget(id);
        if (assisted != null) {
            rotate(id, legit(id) ? nearest(p.getEyePos(), assisted.getBoundingBox()) : aimPoint(id, assisted));
            acted(id); target = assisted; note(id, "Assisting the camera while no attack controller owns it");
        }
    }
    static void combat() {
        var p = mc.player;
        if (criticalTarget != null && (!on("criticals") || !criticalTarget.isAlive()
                || !p.canInteractWithEntity(criticalTarget, 0)
                || ticks - criticalSince > num("criticals", "hold-time"))) {
            criticalTarget = null; restoreCriticalSprint();
        }
        if (!on("kill-aura")) { locks.remove("kill-aura"); switchAt.remove("kill-aura"); }
        if (!on("trigger-bot")) observations.remove("trigger-bot");
        // A different controller already aimed or performed an action this tick.
        // Do not change its hotbar selection, camera, sprint state or item use.
        if (combatControls.occupiedByOther("melee")) {
            releaseBlock(); criticalTarget = null; restoreCriticalSprint();
            for (String id : List.of("kill-aura", "aim-assist", "trigger-bot", "auto-clicker", "criticals", "shield-breaker", "auto-weapon"))
                if (on(id)) note(id, "Waiting for the current explosive / placement action");
            return;
        }
        LivingEntity victim = null; String attacker = null;
        if (on("kill-aura") && acquisitionAllowed("kill-aura")) { victim = findTarget("kill-aura"); attacker = "kill-aura"; }
        for (String id : List.of("trigger-bot", "auto-clicker", "criticals")) {
            if (!on(id) || !acquisitionAllowed(id)
                    || id.equals("auto-clicker") && choice(id, "button").equals("Right")
                    || id.equals("criticals") && boolOr(id, "only-kill-aura", false)) continue;
            if (victim == null && (id.equals("trigger-bot") || physical(mc.options.attackKey))
                    && mc.crosshairTarget instanceof EntityHitResult hit && validCrosshair(id, hit.getEntity())) {
                victim = (LivingEntity) hit.getEntity(); attacker = id;
            }
        }
        LivingEntity pointed = mc.crosshairTarget instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity living ? living : null;
        LivingEntity shieldVictim = victim != null ? victim : pointed;
        boolean shieldReady = on("shield-breaker") && acquisitionAllowed("shield-breaker") && shield(shieldVictim);
        boolean shieldHolding = shieldTarget != null && shieldSlot >= 0 && ticks <= shieldUntil;
        boolean shieldPending = on("shield-breaker") && shieldVictim != null && shieldVictim.isBlocking()
            && observations.containsKey("shield-breaker") && weapon(true) >= 0;
        boolean delayedSwitch = shieldPending && !shieldHolding;
        if (shieldHolding) {
            delayedSwitch = !CombatPolicy.switchSettled(ticks, weaponSwitchedAt, (int) numOr("shield-breaker", "settle-ticks", 1));
        } else if (!shieldPending && (victim != null || physical(mc.options.attackKey) && pointed != null)) delayedSwitch = autoWeapon(victim, pointed);
        if (shieldReady && victim == null) { victim = pointed; attacker = "shield-breaker"; }
        if (victim == null) {
            observations.remove("trigger-bot"); observations.remove("auto-weapon"); criticalTarget = null; restoreCriticalSprint();
            autoBlock(false); assist(); rightClicker(); return;
        }
        target = victim;
        boolean reacted = !attacker.equals("trigger-bot") || observed(attacker, victim, reaction(attacker));
        if (!attacker.equals("trigger-bot")) observations.remove("trigger-bot");
        if (criticalTarget != null && criticalTarget != victim) { criticalTarget = null; restoreCriticalSprint(); }
        if (delayedSwitch) { note(attacker, "Waiting for the selected combat weapon"); autoBlock(false); return; }
        Vec3d point = aimPoint(attacker, victim);
        if (!p.canInteractWithEntity(victim, 0) || distance(victim) > numOr(attacker, "range", 3)) {
            autoBlock(false); rotate(attacker, point); note(attacker, "Target outside normal attack reach; acquisition only"); return;
        }
        if (victim.hurtTime > numOr(attacker, "hurt-time", 10) || !timingReady(attacker)) {
            autoBlock(false); rotate(attacker, point); return;
        }
        if (ownedBlock) { releaseBlock(); note(attacker, "Released the offhand shield before attacking"); return; }
        if (ticks <= blockReleased) return;
        rotate(attacker, point);
        if (angle(point) > numOr(attacker, "hit-angle", 180) || !cameraHits(attacker, victim)) {
            note(attacker, "Turning until the actual camera ray reaches the target hitbox"); return;
        }
        if (attacker.equals("kill-aura") && legit(attacker) && (!(mc.crosshairTarget instanceof EntityHitResult hit) || hit.getEntity() != victim)) return;
        if (!reacted) { note(attacker, "Waiting for crosshair reaction delay"); return; }
        if (!criticals(victim, attacker)) return;
        if (attacker.equals("auto-clicker") && legit(attacker) && ThreadLocalRandom.current().nextInt(20) == 0) { scheduled(attacker); return; }
        List<LivingEntity> victims = attacker.equals("kill-aura") && !legit(attacker) && choice(attacker, "mode").equals("Multi") ? targets(attacker) : List.of(victim);
        int limit = CombatPolicy.targetLimit(choiceOr(attacker, "mode", "Single"), legit(attacker), (int) numOr(attacker, "max-targets", 1)), sent = 0;
        for (LivingEntity current : victims) {
            if (sent >= limit || attackBudget < 1) break;
            if (current.hurtTime > numOr(attacker, "hurt-time", 10) || !cameraHits(attacker, current)
                    || angle(aimPoint(attacker, current)) > numOr(attacker, "hit-angle", 180)) continue;
            if (attack(current, attacker)) {
                sent++;
                if (on("criticals") && bool("criticals", "crit-particles") && CombatPolicy.falling(p.isOnGround(), p.getVelocity().y, p.fallDistance)) p.addCritParticles(current);
            }
        }
        if (sent > 0) scheduled(attacker);
        criticalTarget = null; restoreCriticalSprint();
    }
}
