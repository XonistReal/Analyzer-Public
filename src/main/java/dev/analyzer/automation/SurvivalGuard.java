package dev.analyzer.automation;

import dev.analyzer.core.AnalyzerCore;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.*;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Scoped survival decisions and surveyed retreat routes for an explicitly running Analyzer plan. */
public final class SurvivalGuard {
    public record Result(boolean intervening, boolean fatalBlock, String message, boolean resumeReady) { }
    public record Diagnostics(long attacks, long foodUses, long retreatTicks, int threatCount, boolean intervening, boolean consuming) { }
    private static final Result CLEAR = new Result(false, false, "", false);
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final Map<KeyBinding, Boolean> keys = new LinkedHashMap<>();
    private final EscapeNavigator.Navigator navigator = new EscapeNavigator.Navigator();
    private AutomationNavigator bridge;
    private Object world, player;
    private boolean intervening, consuming;
    private int ticks, quietTicks, recoveryTicks, consumeTicks, lastAttack = -100, threatCount;
    private int savedSlot = -1, ownedSlot = -1, foodSwapSource = -1, foodSwapHotbar = -1;
    private ItemStack swapOriginal = ItemStack.EMPTY;
    private float savedYaw, savedPitch, ownedYaw, ownedPitch;
    private boolean ownsLook;
    private long attacks, foodUses, retreatTicks;
    private Item consumedItem;
    private Hand consumedHand = Hand.MAIN_HAND;

    public Diagnostics diagnostics() { return new Diagnostics(attacks, foodUses, retreatTicks, threatCount, intervening, consuming); }

    /** The caller must invoke this only for active plans and call cleanup on manual pause/stop. */
    public Result tick(AnalyzerCore core, AutomationNavigator route, InventoryAutomation inventory) {
        ticks++;
        if (mc.world == null || mc.player == null || mc.interactionManager == null || !mc.player.isAlive()
                || mc.player.isSpectator() || mc.player.getAbilities().creativeMode) { cleanup(); return CLEAR; }
        if (mc.currentScreen != null && !inventory.ownsScreen()) { cleanup(); return CLEAR; }
        if (intervening && (world != mc.world || player != mc.player)) cleanup();
        boolean defense = bool(core, "defense", true), autoFood = bool(core, "auto-food", true);
        boolean proactive = bool(core, "proactive-avoidance", true);
        double range = proactive ? number(core,"avoid-range",16) : number(core,"threat-range",12), retreatHealth = number(core, "retreat-health", 10);
        double minimumHealth = number(core, "min-health", 6), minimumHunger = number(core, "min-hunger", 6);
        double resumeHealth = Math.max(minimumHealth, retreatHealth);
        int hunger = mc.player.getHungerManager().getFoodLevel();
        List<LivingEntity> threats = defense ? threats(range, proactive) : List.of();
        threatCount = threats.size();
        boolean foodAvailable = hotbarFood() >= 0 || inventoryFood() >= 0 || usableFood(mc.player.getOffHandStack());
        boolean hungry = autoFood && foodAvailable && (hunger < number(core, "eat-below", 14)
                || mc.player.getHealth() < retreatHealth && (hunger < 20 || hasHealingFood()));
        if (!intervening && threats.isEmpty() && !hungry) return CLEAR;
        if (!intervening) {
            intervening = true; world = mc.world; player = mc.player; bridge = route; quietTicks = recoveryTicks = 0;
            if (route.status().owned()) route.pause();
            mc.interactionManager.cancelBlockBreaking();
            inventory.stop();
            if (!inventory.recovery().isBlank()) return new Result(true, true, "Safety needs an empty inventory slot: " + inventory.recovery(), false);
        }
        if (route.status().owned() && !route.status().paused()) route.pause();
        if (!inventory.recovery().isBlank()) return new Result(true, true, "Safety cannot return a held crafting item safely. Clear a slot and stop the task.", false);
        releaseMovement();
        if (route.status().owned() && route.status().paused()) route.setSafetyInputs(false, false, false);
        if (!threats.isEmpty()) {
            quietTicks = recoveryTicks = 0;
            LivingEntity target = threats.getFirst();
            double distance = Math.sqrt(target.squaredDistanceTo(mc.player));
            boolean explosive = threats.stream().anyMatch(this::urgentCreeper);
            int weapon = bestWeapon();
            boolean canEngage = !explosive && threats.size() <= 2 && mc.player.getHealth() > retreatHealth && weapon >= 0
                    && weaponScore(mc.player.getInventory().getStack(weapon)) >= 4;
            // Eating under a melee attacker prevents defending and is too slow to be a retreat.
            if (consuming && (distance < 4.5 || explosive)) stopFood();
            if (canEngage && distance <= 3.1 && mc.player.canInteractWithEntity(target, 0)) {
                stopFood(); select(weapon); if(aim(target.getEyePos()))attack(target);
                return new Result(true, false, "Defending against " + target.getName().getString() + "; original task is paused.", false);
            }
            if (canEngage && distance <= 6 && !consuming && !proactive) {
                Vec3d toward = target.getPos().subtract(mc.player.getPos()).multiply(1, 0, 1).normalize();
                if (safeWalk(toward)) {
                    select(weapon); if(aim(target.getEyePos()))move(route, false);
                    return new Result(true, false, "Closing normal attack distance to " + target.getName().getString() + "; original task is paused.", false);
                }
            }
            EscapeNavigator.Route escape = proactive ? navigator.choose(mc, threats,(int)number(core,"escape-radius",12),bool(core,"prefer-cover",true)) : EscapeNavigator.choose(mc, threats);
            if (escape != null) {
                float awayYaw=(float)(Math.toDegrees(Math.atan2(escape.direction().z,escape.direction().x))-90);
                boolean backwards=explosive && Math.abs(MathHelper.wrapDegrees(awayYaw+180-mc.player.getYaw()))<=number(core,"movement-angle",35);
                boolean aligned=backwards?AutomationMotion.alignYaw(awayYaw+180):aim(mc.player.getEyePos().add(escape.direction().multiply(4)));
                boolean sprint = hunger > 6 && !consuming && !escape.swimming();
                if(aligned){
                    if(backwards){if(!route.setSafetyBackwardInputs(escape.jump())){force(mc.options.backKey);if(escape.jump())force(mc.options.jumpKey);}}
                    else move(route, sprint, escape.jump());
                    retreatTicks++;
                }
                // A ranged opponent or a close pursuer can still be struck at normal reach while retreating.
                // A retreat keeps facing its checked corridor. It never attacks behind the visible camera.
                if (hungry && distance >= 6 && !explosive) food();
                return new Result(true, false, "Retreating from " + target.getName().getString() + " via " + escape.terrain() + "; original task is paused.", false);
            }
            if (!explosive && weapon >= 0 && mc.player.canInteractWithEntity(target, 0)) {
                stopFood(); select(weapon); if(aim(target.getEyePos()))attack(target);
                return new Result(true, false, "No safe ground retreat found; defending in place. Move to cover if needed.", false);
            }
            String terrain = mc.player.isTouchingWater() ? "No checked water or shore escape yet"
                    : !mc.player.isOnGround() ? "Waiting to land after a fall or knockback" : "No checked retreat corridor yet";
            return new Result(true, true, terrain + "; protection keeps checking. Move manually if needed.", false);
        }
        quietTicks++;
        if (hungry || consuming) {
            if (!food() && !consuming) {
                boolean critical = hunger < minimumHunger || mc.player.getHealth() < resumeHealth && hunger < 18;
                if (critical) return new Result(true, true, "Recovery held: no usable food. Health " + Math.round(mc.player.getHealth()) + "/" + (int)Math.ceil(resumeHealth) + " HP; protection keeps watching.", false);
            } else {
                recoveryTicks = 0;
                return new Result(true, false, "Eating available food while the route is paused.", false);
            }
        }
        if (mc.player.getHealth() < resumeHealth || hunger < minimumHunger) {
            if (++recoveryTicks > 400 || hunger < 18 && !foodAvailable)
                return new Result(true, true, "Recovery held: health " + Math.round(mc.player.getHealth()) + "/" + (int)Math.ceil(resumeHealth) + " HP, hunger " + hunger + "/" + (int)Math.ceil(minimumHunger) + ". Protection keeps watching.", false);
            return new Result(true, false, "Threat cleared; recovering health " + Math.round(mc.player.getHealth()) + "/" + (int)Math.ceil(resumeHealth) + " HP and hunger.", false);
        }
        if (quietTicks >= Math.max(10, (int) Math.round(number(core, "resume-delay", 3) * 20))) {
            cleanup();
            return new Result(false, false, "Threat cleared and health/hunger checked. Ready to resume the original task.", true);
        }
        return new Result(true, false, "Threat cleared; watching before resuming the original task.", false);
    }

    private List<LivingEntity> threats(double range, boolean proactive) {
        List<LivingEntity> result = new ArrayList<>();
        int seen = 0;
        for (var entity : mc.world.getEntities()) {
            if (++seen > 1024) break;
            if (!(entity instanceof MobEntity mob) || !(mob instanceof Monster) || !mob.isAlive() || mob.isSpectator()
                    || mc.player.isTeammate(mob) || mob.squaredDistanceTo(mc.player) > range * range
                    || !proactive && !mc.player.canSee(mob) && !urgentCreeper(mob)) continue;
            if (mob.isAiDisabled() && !(mob instanceof CreeperEntity c && (c.isIgnited() || c.getFuseSpeed() > 0))) continue;
            boolean attackedUs = mc.player.getAttacker() == mob || mob.isAttacking();
            if (mob instanceof EndermanEntity enderman && !enderman.isAngry() && !attackedUs) continue;
            if ((mob instanceof ZombifiedPiglinEntity || mob instanceof PiglinEntity) && !attackedUs) continue;
            if (mob instanceof SpiderEntity && mc.world.isDay() && mc.world.getLightLevel(mob.getBlockPos()) >= 12 && !attackedUs) continue;
            if (mob instanceof SlimeEntity slime && !(mob instanceof MagmaCubeEntity) && slime.getSize() < 2) continue;
            result.add(mob);
        }
        result.sort(Comparator.<LivingEntity>comparingInt(e -> e instanceof CreeperEntity c && (c.isIgnited() || c.getFuseSpeed()>0) ? 0 : urgentCreeper(e) ? 1 : 2)
                .thenComparingDouble(e -> e.squaredDistanceTo(mc.player)));
        return result;
    }
    private boolean urgentCreeper(LivingEntity entity){return entity instanceof CreeperEntity creeper
            && (creeper.isIgnited() || creeper.getFuseSpeed()>0 || creeper.squaredDistanceTo(mc.player) <= (creeper.isCharged()?144:64));}

    /** Melee approach remains conservative; escape uses the height-aware terrain navigator. */
    private boolean safeWalk(Vec3d direction) {
        for (double distance = .2; distance <= 1.61; distance += .2) {
            Vec3d offset = direction.multiply(distance);
            Box body = mc.player.getBoundingBox().offset(offset).expand(.035, 0, .035);
            if (!mc.world.isSpaceEmpty(mc.player, body)) return false;
            for (double dx : new double[]{-.28, .28}) for (double dz : new double[]{-.28, .28}) {
                BlockPos feet = BlockPos.ofFloored(mc.player.getX() + offset.x + dx, mc.player.getY(), mc.player.getZ() + offset.z + dz);
                BlockPos floor = feet.down();
                if (!mc.world.isChunkLoaded(floor)) return false;
                BlockState support = mc.world.getBlockState(floor);
                if (!support.isSideSolidFullSquare(mc.world, floor, Direction.UP) || danger(support) || !mc.world.getFluidState(floor).isEmpty()
                        || danger(mc.world.getBlockState(feet)) || !mc.world.getFluidState(feet).isEmpty()
                        || danger(mc.world.getBlockState(feet.up()))) return false;
            }
        }
        return true;
    }
    private boolean danger(BlockState state) {
        return state.isOf(Blocks.LAVA) || state.isOf(Blocks.FIRE) || state.isOf(Blocks.SOUL_FIRE) || state.isOf(Blocks.MAGMA_BLOCK)
                || state.isOf(Blocks.CACTUS) || state.isOf(Blocks.CAMPFIRE) || state.isOf(Blocks.SOUL_CAMPFIRE)
                || state.isOf(Blocks.SWEET_BERRY_BUSH) || state.isOf(Blocks.POWDER_SNOW) || state.isOf(Blocks.WITHER_ROSE);
    }
    private void move(AutomationNavigator route, boolean sprint) {
        move(route,sprint,false);
    }
    private void move(AutomationNavigator route, boolean sprint, boolean jump) {
        if (!route.setSafetyInputs(true, sprint, jump)) { force(mc.options.forwardKey); if (sprint) force(mc.options.sprintKey); if(jump)force(mc.options.jumpKey); }
    }
    private void releaseMovement() {
        if (bridge != null) bridge.clearSafetyInputs();
        release(mc.options.forwardKey); release(mc.options.backKey); release(mc.options.sprintKey); release(mc.options.jumpKey);
    }
    private void attack(LivingEntity target) {
        if (mc.player.isUsingItem() || ticks - lastAttack < 10 || mc.player.getAttackCooldownProgress(0) < .93f
                || !mc.player.canSee(target) || !mc.player.canInteractWithEntity(target, 0)) return;
        mc.interactionManager.attackEntity(mc.player, target); mc.player.swingHand(Hand.MAIN_HAND); lastAttack = ticks; attacks++;
    }
    private int bestWeapon() {
        int best = -1; double score = 0;
        for (int i = 0; i < 9; i++) { double value = weaponScore(mc.player.getInventory().getStack(i)); if (value > score) { score = value; best = i; } }
        return best;
    }
    private double weaponScore(ItemStack stack) {
        if (stack.isEmpty() || stack.isDamageable() && stack.getDamage() >= stack.getMaxDamage() - 1) return 0;
        if (stack.isOf(Items.TRIDENT)) return 9;
        if (stack.isOf(Items.MACE)) return 6;
        String id = Registries.ITEM.getId(stack.getItem()).getPath();
        int tier = id.startsWith("netherite_") ? 4 : id.startsWith("diamond_") ? 3 : id.startsWith("iron_") ? 2 : id.startsWith("stone_") ? 1 : 0;
        if (id.endsWith("_sword")) return 4 + tier;
        if (id.endsWith("_axe")) return tier == 0 ? 7 : tier == 4 ? 10 : 9;
        if (id.endsWith("_pickaxe")) return 2 + tier;
        return 0;
    }

    private boolean food() {
        if (consuming) {
            consumeTicks++;
            if (!mc.player.isUsingItem() || consumedItem == null || !mc.player.getActiveItem().isOf(consumedItem) || mc.player.getActiveHand() != consumedHand || consumeTicks > 100) { stopFood(); return mc.player.getHungerManager().getFoodLevel() >= 18; }
            force(mc.options.useKey); return true;
        }
        if (mc.player.isUsingItem() || !(mc.player.currentScreenHandler instanceof PlayerScreenHandler)
                || !mc.player.currentScreenHandler.getCursorStack().isEmpty()) return false;
        int slot = hotbarFood(); Hand hand = Hand.MAIN_HAND;
        if (slot < 0 && usableFood(mc.player.getOffHandStack())) hand = Hand.OFF_HAND;
        if (slot < 0 && hand == Hand.MAIN_HAND) {
            int source = inventoryFood();
            if (source < 0) return false;
            int target = mc.player.getInventory().selectedSlot;
            for (int i = 0; i < 9; i++) if (mc.player.getInventory().getStack(i).isEmpty()) { target = i; break; }
            if (foodSwapSource < 0) {
                foodSwapSource = source; foodSwapHotbar = target; swapOriginal = mc.player.getInventory().getStack(target).copy();
                mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, source, target, SlotActionType.SWAP, mc.player);
            }
            slot = target;
        }
        if (hand == Hand.MAIN_HAND) select(slot);
        consumedItem = mc.player.getStackInHand(hand).getItem(); consumedHand = hand;
        var result = mc.interactionManager.interactItem(mc.player, hand);
        if (!result.isAccepted() || !mc.player.isUsingItem()) return false;
        consuming = true; consumeTicks = 0; foodUses++; force(mc.options.useKey); return true;
    }
    private int hotbarFood() { for (int i = 0; i < 9; i++) if (usableFood(mc.player.getInventory().getStack(i))) return i; return -1; }
    private int inventoryFood() { for (int i = 9; i < 36; i++) if (usableFood(mc.player.getInventory().getStack(i))) return i; return -1; }
    private boolean usableFood(ItemStack stack) {
        if (stack.isEmpty() || !stack.contains(DataComponentTypes.FOOD) || !stack.contains(DataComponentTypes.CONSUMABLE)) return false;
        if (stack.isOf(Items.ROTTEN_FLESH) || stack.isOf(Items.SPIDER_EYE) || stack.isOf(Items.POISONOUS_POTATO) || stack.isOf(Items.PUFFERFISH)
                || stack.isOf(Items.CHORUS_FRUIT) || stack.isOf(Items.SUSPICIOUS_STEW)) return false;
        return mc.player.getHungerManager().getFoodLevel() < 20 || stack.isOf(Items.GOLDEN_APPLE) || stack.isOf(Items.ENCHANTED_GOLDEN_APPLE);
    }
    private boolean hasHealingFood() {
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isOf(Items.GOLDEN_APPLE) || stack.isOf(Items.ENCHANTED_GOLDEN_APPLE)) return true;
        }
        return false;
    }
    private void stopFood() {
        if (consuming && mc.player != null && mc.interactionManager != null && mc.player.isUsingItem() && consumedItem != null
                && mc.player.getActiveItem().isOf(consumedItem) && mc.player.getActiveHand() == consumedHand) mc.interactionManager.stopUsingItem(mc.player);
        consuming = false; consumedItem = null; consumeTicks = 0; release(mc.options.useKey); restoreFoodSwap();
    }
    private void select(int slot) {
        if (slot < 0 || slot > 8) return;
        if (savedSlot < 0) savedSlot = mc.player.getInventory().selectedSlot;
        mc.player.getInventory().selectedSlot = ownedSlot = slot;
    }
    private boolean aim(Vec3d point) {
        return AutomationMotion.align(point);
    }
    private void force(KeyBinding key) { keys.putIfAbsent(key, key.isPressed()); key.setPressed(true); }
    private void release(KeyBinding key) {
        if (!keys.containsKey(key)) return;
        InputUtil.Key bound = KeyBindingHelper.getBoundKeyOf(key);
        boolean physical = bound.getCode() >= 0 && (bound.getCategory() == InputUtil.Type.MOUSE
                ? GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), bound.getCode()) == GLFW.GLFW_PRESS
                : InputUtil.isKeyPressed(mc.getWindow().getHandle(), bound.getCode()));
        key.setPressed(physical); keys.remove(key);
    }

    /** Release only this guard's temporary input, held-slot, inventory-swap and camera ownership. */
    public void cleanup() {
        releaseMovement(); stopFood();
        if (mc.player != null && player == mc.player && world == mc.world) {
            restoreFoodSwap();
            if (savedSlot >= 0 && mc.player.getInventory().selectedSlot == ownedSlot) mc.player.getInventory().selectedSlot = savedSlot;
        }
        savedSlot = ownedSlot = foodSwapSource = foodSwapHotbar = -1; swapOriginal = ItemStack.EMPTY; ownsLook = false;
        navigator.reset();
        bridge = null; world = player = null; intervening = false; consuming = false; quietTicks = recoveryTicks = threatCount = 0;
    }
    private void restoreFoodSwap() {
        if (foodSwapSource < 0 || mc.player == null || mc.interactionManager == null || player != mc.player || world != mc.world
                || !(mc.player.currentScreenHandler instanceof PlayerScreenHandler) || !mc.player.currentScreenHandler.getCursorStack().isEmpty()) return;
        ItemStack source = mc.player.getInventory().getStack(foodSwapSource);
        if (source.isEmpty() && swapOriginal.isEmpty() || ItemStack.areItemsAndComponentsEqual(source, swapOriginal)) {
            mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, foodSwapSource, foodSwapHotbar, SlotActionType.SWAP, mc.player);
            foodSwapSource = foodSwapHotbar = -1; swapOriginal = ItemStack.EMPTY;
        }
    }
    private double number(AnalyzerCore core, String id, double fallback) { return core.definition("expedition").settings().stream().anyMatch(s -> s.id().equals(id)) ? core.getNumber("expedition", id) : fallback; }
    private boolean bool(AnalyzerCore core, String id, boolean fallback) { return core.definition("expedition").settings().stream().anyMatch(s -> s.id().equals(id)) ? core.getBoolean("expedition", id) : fallback; }
}
