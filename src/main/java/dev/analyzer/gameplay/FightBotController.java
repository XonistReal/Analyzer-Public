package dev.analyzer.gameplay;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import java.util.List;

import static dev.analyzer.gameplay.GameplayEngine.*;

/**
 * Original Fight Bot: one filtered target, chase and circle movement through ordinary keys, falling
 * critical timing and attacks validated exactly like Kill Aura (reach, charge, fresh hitbox and block
 * rays, action ownership). In Silent movement the camera never turns. Movement correction asks the
 * next player tick's physics to use the transmitted lease yaw, so the held keys, sprint and the look
 * the server receives agree. Nothing here changes velocity directly; servers remain authoritative.
 */
final class FightBotController {
    private static final String ID = "fight-bot";
    private static LivingEntity locked;
    private static int side = 1;
    private static long sideChanged = Long.MIN_VALUE, critJumpTick = Long.MIN_VALUE, movementTick = Long.MIN_VALUE;
    private static boolean critPending, sprintingLastTick;
    private static float steeringYaw;
    private FightBotController() { }

    static void reset() {
        locked = null; side = 1; critPending = false; sprintingLastTick = false;
        sideChanged = critJumpTick = movementTick = Long.MIN_VALUE;
    }

    /** Release ownership for this tick; held keys return to their physical state at the next tick. */
    static void stop(String reason) {
        if (SilentRotation.ownedBy(ID)) SilentRotation.cancel();
        critPending = false; movementTick = Long.MIN_VALUE;
        if (mc != null && mc.player != null) sprintingLastTick = mc.player.isSprinting();
        if (reason != null && on(ID)) note(ID, reason);
    }

    /** Keep Sprint and sprint input must not reassert sprint while a critical attack is lined up. */
    static boolean suppressSprint() { return critPending && on(ID); }

    /** The yaw movement physics will use next tick; other movement helpers steer relative to it. */
    static float movementYaw(float ordinary) { return movementTick == ticks ? steeringYaw : ordinary; }

    /** @return true while the bot owns aim and melee this tick. */
    static boolean tick() {
        if (!on(ID)) { if (locked != null || critPending) stop(null); locked = null; return false; }
        ClientPlayerEntity p = mc.player;
        boolean wasSprinting = sprintingLastTick;
        if (p.getAbilities().flying || p.isGliding() || p.isSpectator() || p.hasVehicle()) { stop("Paused while flying, gliding or riding"); return false; }
        if (combatControls.occupiedByOther(ID)) { stop("Waiting for an explosive or placement action"); return false; }
        if (boolOr(ID, "pause-mining", true) && mc.interactionManager.isBreakingBlock()) { stop("Paused while breaking a block"); return false; }
        if (p.isUsingItem() && boolOr(ID, "pause-using", true)) { stop("Paused while using an item"); return false; }
        if (FightBotPolicy.lowHealth(p.getHealth(), p.getAbsorptionAmount(), num(ID, "stop-health"))) { stop("Paused at the configured low health"); return false; }
        LivingEntity victim = select();
        if (victim == null) { stop("No eligible target within the chase range"); return false; }
        if (!combatControls.camera(ID)) { stop("Waiting for the current aim owner"); return false; }
        GameplayEngine.target = victim;
        if (critPending && (p.isOnGround() && ticks > critJumpTick + 1 || p.isTouchingWater() || p.isClimbing())) critPending = false;

        Vec3d point = CombatController.aimPoint(ID, victim);
        String mode = choice(ID, "rotation");
        boolean silent = SilentRotation.isSilent(mode);
        boolean leased = false;
        if (silent) {
            leased = SilentRotation.update(ID, victim, point, mode, num(ID, "turn-speed"), num(ID, "smoothing"), 100);
            if (!leased) note(ID, SilentRotation.status());
        } else {
            SilentRotation.cancel();
            CombatController.rotate(ID, point);
        }

        double distance = CombatController.distance(victim), reach = num(ID, "range");
        double ticksToCharge = FightBotPolicy.ticksUntilCharged(p.getAttackCooldownProgress(0), num(ID, "cooldown"),
                p.getAttackCooldownProgressPerTick());
        boolean criticals = bool(ID, "criticals") && canCritical(p);
        if (!criticals) critPending = false;
        move(p, victim, distance, reach, ticksToCharge, criticals, leased);
        if (!silent || leased) strike(p, victim, point, distance, reach, criticals, silent, wasSprinting);
        sprintingLastTick = p.isSprinting();
        return true;
    }

    private static LivingEntity select() {
        List<LivingEntity> found = CombatController.targets(ID, num(ID, "chase-range"));
        if (found.isEmpty()) { locked = null; return null; }
        // Keep fighting the same target while it stays eligible; switching mid-combo wastes hits.
        if (locked == null || !found.contains(locked)) { locked = found.getFirst(); critPending = false; }
        return locked;
    }

    private static void move(ClientPlayerEntity p, LivingEntity victim, double distance, double reach,
                             double ticksToCharge, boolean criticals, boolean leased) {
        boolean grounded = p.isOnGround();
        int lead = (int) num(ID, "crit-lead");
        if (criticals && !critPending && FightBotPolicy.jumpForCritical(grounded, canJump(p), ticksToCharge, lead, distance, reach)) {
            force(mc.options.jumpKey); critPending = true; critJumpTick = ticks;
        }
        boolean releasing = criticals && critPending && FightBotPolicy.releaseSprintForCritical(grounded, p.getVelocity().y);
        if (releasing) releaseSprint(p);
        if (bool(ID, "swim") && (p.isTouchingWater() || p.isInLava())) force(mc.options.jumpKey);

        String movement = choice(ID, "movement");
        boolean manual = bool(ID, "manual-override") && (physical(mc.options.forwardKey) || physical(mc.options.backKey)
                || physical(mc.options.leftKey) || physical(mc.options.rightKey));
        if (manual) { note(ID, "Your movement keys are steering; attacks continue"); return; }
        if (movement.equals("Stand still")) return;
        if (on("target-strafe")) { note(ID, "Target Strafe is steering; attacks continue"); return; }

        double keep = num(ID, "keep-distance");
        boolean chase = movement.equals("Chase only") || FightBotPolicy.phase(distance, keep) == FightBotPolicy.Phase.CHASE;
        if (movement.equals("Chase only") && distance <= keep) return;
        boolean backOff = FightBotPolicy.backOff(bool(ID, "back-off"), ticksToCharge, lead, distance, reach);
        FightBotPolicy.Plan plan = chase && !backOff
                ? FightBotPolicy.approach(p.getX(), p.getZ(), victim.getX(), victim.getZ(), side)
                : FightBotPolicy.orbit(p.getX(), p.getZ(), victim.getX(), victim.getZ(), victim.getHeadYaw(),
                        keep, choice(ID, "circle"), num(ID, "danger-cone"), side, backOff);
        if (plan.moving() && !safe(p, plan)) {
            // Try the other way around before stopping; the side flip is rate-limited to avoid jitter.
            if (!chase && ticks - sideChanged > 10) {
                FightBotPolicy.Plan other = FightBotPolicy.orbit(p.getX(), p.getZ(), victim.getX(), victim.getZ(),
                        victim.getHeadYaw(), keep, "Away from their aim".equals(choice(ID, "circle")) ? "Away from their aim"
                                : plan.side() > 0 ? "Counter-clockwise" : "Clockwise", 0, -plan.side(), backOff);
                if (other.moving() && safe(p, other)) { plan = other; sideChanged = ticks; }
            }
            if (!safe(p, plan)) { side = plan.side(); note(ID, "Holding position at an unsafe drop or hazard"); return; }
        }
        side = plan.side();
        if (!plan.moving()) return;

        // Keys are chosen relative to the yaw physics will use next tick: the lease yaw with
        // movement correction, otherwise the real camera yaw (the bot then moves camera-relative).
        boolean correction = leased && SilentRotation.ownedBy(ID) && SilentRotation.movementCorrectionAvailable();
        float facing = correction ? SilentRotation.angles().yaw() : p.getYaw();
        FightBotPolicy.Keys keys = FightBotPolicy.keys(plan.yaw(), facing);
        // Releasing forward for the stop-sprint tick keeps vanilla from restarting sprint mid-air.
        if (keys.forward() && !releasing) force(mc.options.forwardKey);
        if (keys.back()) force(mc.options.backKey);
        if (keys.left()) force(mc.options.leftKey);
        if (keys.right()) force(mc.options.rightKey);
        if (bool(ID, "sprint") && keys.forward() && !critPending && !p.isSneaking()
                && (p.getHungerManager().getFoodLevel() > 6 || p.getAbilities().creativeMode)) force(mc.options.sprintKey);
        if (correction) { SilentRotation.requestMovementCorrection(); steeringYaw = facing; movementTick = ticks; }
        if (grounded && !critPending && FightBotPolicy.chaseHop(bool(ID, "chase-hops"), true, p.isSprinting(), distance, reach)
                && canJump(p) && safeAhead(p, plan, 2.4)) force(mc.options.jumpKey);
        if (grounded && p.horizontalCollision && bool(ID, "jump-obstacles") && canJump(p)) force(mc.options.jumpKey);
        acted(ID);
        note(ID, (chase ? "Chasing " : backOff ? "Backing off from " : "Circling ") + victim.getName().getString());
    }

    private static void strike(ClientPlayerEntity p, LivingEntity victim, Vec3d point, double distance, double reach,
                               boolean criticals, boolean silent, boolean wasSprinting) {
        String name = victim.getName().getString();
        if (distance > reach || !p.canInteractWithEntity(victim, 0)) return;
        if (victim.hurtTime > num(ID, "hurt-time")) { note(ID, "Waiting for " + name + "'s hurt time"); return; }
        boolean settled = bool(ID, "auto-weapon") ? CombatController.holdBestWeapon(ID) : CombatController.weaponSettled(ID);
        if (!settled) { note(ID, "Waiting for the selected weapon"); return; }
        if (bool(ID, "weapon-only") && !CombatController.melee(p.getMainHandStack())) { note(ID, "Hold a melee weapon"); return; }
        if (p.getAttackCooldownProgress(0) < num(ID, "cooldown")) { note(ID, "Waiting for attack charge"); return; }
        if (criticals && !FightBotPolicy.criticalWindow(p.isOnGround(), p.getVelocity().y, p.fallDistance, p.isSprinting(), wasSprinting)) {
            if (!p.isOnGround() || critPending) {
                if (FightBotPolicy.releaseSprintForCritical(p.isOnGround(), p.getVelocity().y)) releaseSprint(p);
                note(ID, "Lining up a falling critical hit on " + name); return;
            }
            if (canJump(p)) {
                force(mc.options.jumpKey); critPending = true; critJumpTick = ticks;
                note(ID, "Jumping for a critical hit on " + name); return;
            }
            if (bool(ID, "crit-only")) { note(ID, "Waiting for room to jump for a critical hit"); return; }
        }
        boolean landed = false;
        if (silent) {
            try {
                if (!SilentRotation.prepareAttack(ID, victim, point)) { note(ID, SilentRotation.status()); return; }
                // Movement mode must use its actual transmitted direction, checked against the fresh hitbox.
                SilentAimPolicy.Angles angles = SilentRotation.attackAngles();
                if (angles == null) { note(ID, "Waiting for a transmitted silent aim direction"); return; }
                if (CombatController.aimError(point, angles) > num(ID, "hit-angle") || !CombatController.cameraHits(ID, victim, angles)) {
                    note(ID, "Turning the server aim onto " + name); return;
                }
                landed = attack(victim, ID);
            } finally { SilentRotation.finishAttack(); }
        } else {
            if (CombatController.aimError(point, null) > num(ID, "hit-angle") || !CombatController.cameraHits(ID, victim, null)) {
                note(ID, "Turning toward " + name); return;
            }
            landed = attack(victim, ID);
        }
        if (landed) {
            critPending = false;
            note(ID, (criticals && !p.isOnGround() ? "Critical hit on " : "Hit ") + name);
        }
    }

    private static void releaseSprint(ClientPlayerEntity p) {
        if (p.isSprinting()) p.setSprinting(false);
        forcedKeys.putIfAbsent(mc.options.sprintKey, mc.options.sprintKey.isPressed());
        mc.options.sprintKey.setPressed(false);
    }

    /** Vanilla critical conditions that jumping cannot create. */
    private static boolean canCritical(ClientPlayerEntity p) {
        return !p.isTouchingWater() && !p.isInLava() && !p.isClimbing()
                && !p.hasStatusEffect(StatusEffects.BLINDNESS) && !p.hasStatusEffect(StatusEffects.LEVITATION);
    }

    /** A jump only helps when there is head room for it to fall afterwards. */
    private static boolean canJump(ClientPlayerEntity p) {
        return p.isOnGround() && !p.isSneaking() && mc.world.isSpaceEmpty(p, p.getBoundingBox().offset(0, 1.0, 0));
    }

    private static boolean safe(ClientPlayerEntity p, FightBotPolicy.Plan plan) {
        return safeAhead(p, plan, 0.6) && safeAhead(p, plan, 1.2);
    }

    /** Loaded floor within the allowed drop and no configured hazard where the next steps land. */
    private static boolean safeAhead(ClientPlayerEntity p, FightBotPolicy.Plan plan, double ahead) {
        BlockPos feet = BlockPos.ofFloored(p.getX() + plan.x() * ahead, p.getY() + 0.01, p.getZ() + plan.z() * ahead);
        boolean hazards = bool(ID, "avoid-hazards");
        if (!mc.world.isChunkLoaded(feet)) return false;
        if (hazards && (hazard(feet) || hazard(feet.up()))) return false;
        if (!p.isOnGround()) return true;   // airborne steps are judged again on landing
        int maxDrop = (int) num(ID, "max-drop");
        for (int down = 1; down <= maxDrop + 1; down++) {
            BlockPos below = feet.down(down);
            if (!mc.world.isChunkLoaded(below)) return false;
            if (hazards && hazard(below)) return false;
            if (!mc.world.getBlockState(below).getCollisionShape(mc.world, below).isEmpty())
                return FightBotPolicy.safeDrop(true, true, down - 1, maxDrop);
        }
        return false;
    }

    private static boolean hazard(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        return state.isOf(Blocks.LAVA) || state.isOf(Blocks.FIRE) || state.isOf(Blocks.SOUL_FIRE) || state.isOf(Blocks.MAGMA_BLOCK)
                || state.isOf(Blocks.CACTUS) || state.isOf(Blocks.SWEET_BERRY_BUSH) || state.isOf(Blocks.CAMPFIRE)
                || state.isOf(Blocks.SOUL_CAMPFIRE) || state.isOf(Blocks.POWDER_SNOW) || state.isOf(Blocks.WITHER_ROSE);
    }
}
