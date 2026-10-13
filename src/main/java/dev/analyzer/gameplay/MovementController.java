package dev.analyzer.gameplay;

import net.minecraft.block.Blocks;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import static dev.analyzer.gameplay.GameplayEngine.*;

/** Original settings-driven movement. Normal-input profiles never add movement velocity. */
final class MovementController {
    private static int flightTicks;
    private static Float pillarPitch;
    private static net.minecraft.client.network.ClientPlayerEntity pillarPlayer;
    private MovementController() { }

    static void movement() {
        var p = mc.player;
        if (on("auto-walk") && (!bool("auto-walk", "stop-on-collision") || !p.horizontalCollision)) {
            switch (choice("auto-walk", "direction")) {
                case "Backward" -> force(mc.options.backKey);
                case "Left" -> force(mc.options.leftKey);
                case "Right" -> force(mc.options.rightKey);
                default -> force(mc.options.forwardKey);
            }
            if (bool("auto-walk", "jump")) force(mc.options.jumpKey);
            if (bool("auto-walk", "sprint") && choice("auto-walk", "direction").equals("Forward")) sprintInput();
            acted("auto-walk"); note("auto-walk", "Holding " + choice("auto-walk", "direction").toLowerCase());
        }
        double forward = (mc.options.forwardKey.isPressed() ? 1 : 0) - (mc.options.backKey.isPressed() ? 1 : 0);
        double side = (mc.options.leftKey.isPressed() ? 1 : 0) - (mc.options.rightKey.isPressed() ? 1 : 0);
        var input = MovementPolicy.direction(p.getYaw(), forward, side);
        Vec3d direction = new Vec3d(input.x(), 0, input.z());
        boolean moving = input.moving(), sneak = mc.options.sneakKey.isPressed(), jump = mc.options.jumpKey.isPressed();
        if (on("target-strafe") && !bool("target-strafe", "legit")) {
            Vec3d orbit = HvhController.steeringDirection();
            if (orbit != null) { direction = orbit; moving = true; }
        }
        if (on("keep-sprint") && !CombatController.suppressSprint() && forward > 0 && !sneak
                && (!bool("keep-sprint", "pause-on-use") || !p.isUsingItem())
                && (!bool("keep-sprint", "ground-only") || p.isOnGround())
                && (p.getHungerManager().getFoodLevel() > num("keep-sprint", "min-hunger") || p.getAbilities().creativeMode)) {
            if (bool("keep-sprint", "legit")) force(mc.options.sprintKey); else p.setSprinting(true);
            acted("keep-sprint"); note("keep-sprint", bool("keep-sprint", "legit") ? "Normal sprint input" : "Maintaining allowed sprint state");
        }
        step(direction, moving);
        if (!on("high-jump") || !bool("high-jump", "legit") || !jump) restorePillarLook();
        Vec3d v = p.getVelocity();
        if (on("fly")) {
            if (bool("fly", "legit")) {
                flightTicks = 0;
                elytraTakeoff("fly", true);
                if (p.isGliding()) rocket("fly", (int) num("fly", "rocket-delay"), 1);
                return;
            }
            flightTicks++;
            String mode = choice("fly", "mode");
            double speed = MovementPolicy.flightSpeed(num("fly", "speed"), mode);
            double vertical = MovementPolicy.flightVertical(mode, v.y, jump, sneak, num("fly", "vertical-speed"),
                    bool("fly", "anti-hover"), flightTicks, (int) num("fly", "pulse-interval"));
            p.setVelocity(direction.x * speed, vertical, direction.z * speed);
            movementChanges++; acted("fly"); note("fly", mode + " client motion; server may correct it"); return;
        }
        flightTicks = 0;
        if (on("elytra-boost") && bool("elytra-boost", "take-off") && !p.isGliding()) elytraTakeoff("elytra-boost", true);
        if (p.isTouchingWater() || p.isInLava()) return;
        if (on("safe-walk") && bool("safe-walk", "legit") && moving && p.isOnGround()
                && (!bool("safe-walk", "only-sneaking") || sneak) && unsupportedEdge(direction, .35)) {
            force(mc.options.sneakKey); sneak = true; acted("safe-walk"); note("safe-walk", "Normal sneak input near a loaded edge");
        }
        boolean changed = false;
        if (on("speed") && moving && !p.isUsingItem() && !sneak && !p.isGliding()) {
            String profile = bool("speed", "legit") ? "Sprint hop" : choice("speed", "profile");
            if (profile.equals("Sprint hop")) keyHop("speed");
            else if (!profile.equals("Conservative") || p.isOnGround()) {
                double speed = MovementPolicy.horizontalSpeed(num("speed", "speed"), num("speed", "multiplier"), profile);
                v = horizontal(v, direction, speed); changed = true; acted("speed");
                note("speed", profile + " motion · " + String.format(java.util.Locale.ROOT, "%.2f", speed) + " blocks/tick");
            } else note("speed", "Conservative profile leaves airborne velocity unchanged");
        } else if (on("ground-strafe") && moving && p.isOnGround() && !sneak) {
            if (bool("ground-strafe", "legit")) keyHop("ground-strafe");
            else {
                double speed = choice("ground-strafe", "mode").equals("Steady") ? num("ground-strafe", "speed") : Math.min(.8, Math.hypot(v.x, v.z));
                v = horizontal(v, direction, speed); changed = true; acted("ground-strafe");
                note("ground-strafe", choice("ground-strafe", "mode") + " ground steering");
            }
        }
        if (on("parkour") && moving && p.isOnGround() && !p.isClimbing() && !sneak
                && unsupportedEdge(direction, num("parkour", "look-ahead"))) {
            if (bool("parkour", "sprint")) sprintInput();
            if (bool("parkour", "legit")) force(mc.options.jumpKey);
            else { p.jump(); v = new Vec3d(v.x, p.getVelocity().y, v.z); changed = true; }
            acted("parkour"); note("parkour", "Jumping at a checked unsupported edge");
        }
        if (on("long-jump") && bool("long-jump", "legit") && moving && p.isOnGround() && !sneak
                && unsupportedEdge(direction, .35)) keyHop("long-jump");
        if (on("high-jump") && bool("high-jump", "legit") && jump) pillar();
        boolean jumped = wasGround && !p.isOnGround() && v.y > .2;
        if (on("high-jump") && !bool("high-jump", "legit") && jumped) {
            v = new Vec3d(v.x, Math.max(v.y, num("high-jump", "height")), v.z); changed = true; acted("high-jump");
        }
        if (on("long-jump") && !bool("long-jump", "legit") && jumped && moving) {
            v = horizontal(v, direction, num("long-jump", "speed") * num("long-jump", "boost"));
            v = new Vec3d(v.x, Math.max(v.y, num("long-jump", "height")), v.z); changed = true; acted("long-jump");
        }
        if (on("fast-ladder") && p.isClimbing()) {
            if (bool("fast-ladder", "legit")) {
                force(mc.options.forwardKey); force(mc.options.jumpKey); acted("fast-ladder"); note("fast-ladder", "Normal ladder forward/jump input");
            } else if (forward > 0 || jump) {
                double speed = num("fast-ladder", "speed") * (jump ? num("fast-ladder", "jump-multiplier") : 1);
                v = new Vec3d(v.x, Math.max(v.y, speed), v.z); changed = true; acted("fast-ladder");
            }
        }
        if (on("fast-fall") && bool("fast-fall", "legit")) scaffoldDrop("fast-fall");
        if (on("reverse-step") && bool("reverse-step", "legit")) scaffoldDrop("reverse-step");
        if (!p.isOnGround() && !p.isClimbing() && !p.isGliding() && v.y < 0) {
            if (on("fast-fall") && !bool("fast-fall", "legit") && p.fallDistance > num("fast-fall", "trigger-fall")) {
                v = new Vec3d(v.x, -Math.max(-v.y, num("fast-fall", "speed")), v.z); changed = true; acted("fast-fall");
            } else if (on("reverse-step") && !bool("reverse-step", "legit") && nearbyFloor(num("reverse-step", "max-drop"))) {
                v = new Vec3d(v.x, -Math.max(-v.y, num("reverse-step", "speed")), v.z); changed = true; acted("reverse-step");
            }
            if (on("glide")) {
                if (bool("glide", "legit")) elytraTakeoff("glide", false);
                else { v = new Vec3d(v.x, Math.max(v.y, -num("glide", "speed")), v.z); changed = true; acted("glide"); }
            }
        }
        if (on("elytra-boost") && p.isGliding()) {
            if (bool("elytra-boost", "legit") || choice("elytra-boost", "mode").equals("Rockets")) {
                rocket("elytra-boost", (int) num("elytra-boost", "rocket-delay"), num("elytra-boost", "rocket-below"));
            } else {
                v = v.add(p.getRotationVec(1).multiply(num("elytra-boost", "thrust")));
                if (jump && num("elytra-boost", "climb") > 0) v = new Vec3d(v.x, Math.max(v.y, num("elytra-boost", "climb")), v.z);
                double max = num("elytra-boost", "max-speed");
                if (v.length() > max) v = v.normalize().multiply(max);
                changed = true; acted("elytra-boost"); note("elytra-boost", "Bounded view impulse with configured climb");
            }
        }
        if (changed) { p.setVelocity(v); movementChanges++; }
    }

    static boolean safeWalkActive() {
        return core != null && mc != null && mc.player != null && mc.world != null && mc.currentScreen == null
                && mc.player.isAlive() && !mc.player.isSpectator() && !mc.player.hasVehicle()
                && MovementPolicy.safeWalk(on("safe-walk"), bool("safe-walk", "legit"), bool("safe-walk", "only-sneaking"), mc.options.sneakKey.isPressed());
    }
    private static Vec3d horizontal(Vec3d v, Vec3d direction, double speed) { return new Vec3d(direction.x * speed, v.y, direction.z * speed); }
    private static void sprintInput() {
        if (!CombatController.suppressSprint() && mc.options.forwardKey.isPressed() && !mc.options.backKey.isPressed() && !mc.options.sneakKey.isPressed()
                && !mc.player.isUsingItem() && (mc.player.getHungerManager().getFoodLevel() > 6 || mc.player.getAbilities().creativeMode)) force(mc.options.sprintKey);
    }
    private static void keyHop(String id) {
        sprintInput();
        if (mc.player.isOnGround() && !mc.player.isClimbing()) { force(mc.options.jumpKey); acted(id); }
        note(id, "Normal sprint/jump inputs; no velocity boost");
    }
    private static boolean unsupportedEdge(Vec3d direction, double distance) {
        Box ahead = mc.player.getBoundingBox().offset(direction.multiply(distance)).offset(0, -.15, 0);
        return loaded(ahead) && mc.world.isSpaceEmpty(mc.player, ahead);
    }
    private static boolean loaded(Box box) {
        for (BlockPos pos : BlockPos.iterate(BlockPos.ofFloored(box.minX, box.minY, box.minZ),
                BlockPos.ofFloored(box.maxX - .001, box.maxY - .001, box.maxZ - .001))) if (!mc.world.isChunkLoaded(pos)) return false;
        return true;
    }
    private static boolean nearbyFloor(double maxDrop) {
        Box feet = mc.player.getBoundingBox();
        for (double down = .1; down <= maxDrop + .001; down += .1) {
            Box probe = new Box(feet.minX + .01, feet.minY - down, feet.minZ + .01, feet.maxX - .01, feet.minY - down + .08, feet.maxZ - .01);
            if (!loaded(probe)) return false;
            if (!mc.world.isSpaceEmpty(mc.player, probe)) return true;
        }
        return false;
    }
    private static void step(Vec3d direction, boolean moving) {
        if (!on("step")) { restoreStep(); return; }
        var p = mc.player;
        if (bool("step", "legit") || choice("step", "mode").equals("Jump")) {
            restoreStep();
            if (!moving || !p.isOnGround() || p.isClimbing() || p.isTouchingWater() || mc.options.sneakKey.isPressed()) return;
            double rise = Math.min(1, num("step", "height"));
            Box ahead = p.getBoundingBox().offset(direction.multiply(.4));
            Box raised = ahead.offset(0, rise + .02, 0);
            if (loaded(ahead.union(raised)) && !mc.world.isSpaceEmpty(p, ahead) && mc.world.isSpaceEmpty(p, raised)) {
                force(mc.options.jumpKey); acted("step"); note("step", "Normal jump at a checked ledge up to " + rise + " blocks");
            }
            return;
        }
        var attribute = p.getAttributeInstance(EntityAttributes.STEP_HEIGHT);
        if (attribute == null) return;
        if (stepOriginal == null || stepPlayer != p) { restoreStep(); stepOriginal = attribute.getBaseValue(); stepPlayer = p; }
        stepWritten = num("step", "height"); attribute.setBaseValue(stepWritten); acted("step"); note("step", "Client step height " + stepWritten);
    }
    private static void scaffoldDrop(String id) {
        BlockPos feet = mc.player.getBlockPos();
        if (mc.world.getBlockState(feet).isOf(Blocks.SCAFFOLDING) || mc.world.getBlockState(feet.down()).isOf(Blocks.SCAFFOLDING)) {
            force(mc.options.sneakKey); acted(id); note(id, "Normal sneak through scaffolding");
        } else note(id, "Legit drop needs scaffolding; vanilla gravity is unchanged");
    }
    private static boolean usableElytra() {
        var stack = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        return stack.isOf(Items.ELYTRA) && (!stack.isDamageable() || stack.getDamage() < stack.getMaxDamage() - 1);
    }
    private static void elytraTakeoff(String id, boolean jumpFromGround) {
        var p = mc.player;
        if (!usableElytra()) { note(id, "Equip a usable elytra for normal flight"); return; }
        if (p.isGliding() || p.isClimbing() || p.isTouchingWater() || p.isInLava()) return;
        if (p.isOnGround()) { if (jumpFromGround) { force(mc.options.jumpKey); acted(id); note(id, "Normal takeoff jump"); } return; }
        if (p.getVelocity().y < 0 && ready(id + "-open", 10)) {
            p.networkHandler.sendPacket(new ClientCommandC2SPacket(p, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            acted(id); note(id, "Requested ordinary elytra opening; awaiting server");
        }
    }
    private static void rocket(String id, int delay, double belowSpeed) {
        if (!mc.player.isGliding() || mc.player.isUsingItem() || interactionBudget == 0) return;
        if (mc.player.getVelocity().length() >= belowSpeed) { note(id, "Gliding above the rocket threshold"); return; }
        Hand hand = mc.player.getOffHandStack().isOf(Items.FIREWORK_ROCKET) ? Hand.OFF_HAND : Hand.MAIN_HAND;
        int slot = hand == Hand.OFF_HAND ? -1 : hotbar(s -> s.isOf(Items.FIREWORK_ROCKET));
        if (hand == Hand.MAIN_HAND && slot < 0) { note(id, "Need a hotbar or offhand firework rocket"); return; }
        if (!ready(id + "-rocket", delay)) return;
        if (hand == Hand.MAIN_HAND) selectSlot(slot);
        interactionBudget--;
        var result = mc.interactionManager.interactItem(mc.player, hand);
        if (result.isAccepted()) { mc.player.swingHand(hand); acted(id); note(id, "Used a real firework rocket"); }
        else note(id, "Rocket interaction unavailable");
    }
    private static void pillar() {
        var p = mc.player;
        if (p.isUsingItem() || p.isClimbing() || p.isGliding()) return;
        int slot = hotbar(s -> s.getItem() instanceof BlockItem item && item.getBlock().getDefaultState().isFullCube(mc.world, p.getBlockPos())
                && !(item.getBlock() instanceof net.minecraft.block.FallingBlock));
        if (slot < 0) { note("high-jump", "Legit pillar needs stable solid hotbar blocks"); return; }
        if (pillarPlayer != p || pillarPitch == null) { restorePillarLook(); pillarPitch = p.getPitch(); pillarPlayer = p; }
        p.setPitch(90);
        force(mc.options.jumpKey);
        BlockPos target = BlockPos.ofFloored(p.getX(), p.getY() - 1, p.getZ());
        if (p.isOnGround() || new Box(target).intersects(p.getBoundingBox()) || !mc.world.isChunkLoaded(target)) return;
        if (place(target, slot, "high-jump")) note("high-jump", "Pillaring with an ordinary jump and placed block");
    }
    private static void restorePillarLook() {
        if (pillarPlayer != null && pillarPitch != null && Math.abs(pillarPlayer.getPitch() - 90) < .1) pillarPlayer.setPitch(pillarPitch);
        pillarPlayer = null; pillarPitch = null;
    }
    static void restoreStep() {
        if (stepPlayer != null && stepOriginal != null) {
            var attribute = stepPlayer.getAttributeInstance(EntityAttributes.STEP_HEIGHT);
            if (attribute != null && stepWritten != null && Math.abs(attribute.getBaseValue() - stepWritten) < .000001) attribute.setBaseValue(stepOriginal);
        }
        stepPlayer = null; stepOriginal = stepWritten = null;
    }
    static void reset() { flightTicks = 0; restorePillarLook(); restoreStep(); }
}
