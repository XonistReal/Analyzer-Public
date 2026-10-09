package dev.analyzer.gameplay;

import dev.analyzer.AnalyzerClient;
import dev.analyzer.core.AnalyzerCore;
import dev.analyzer.core.GameplayModules;
import dev.analyzer.mixin.FishingBobberAccessor;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.block.Blocks;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.lwjgl.glfw.GLFW;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** Bounded, normal-interaction automation. Movement is client-side and may be corrected by servers. */
public final class GameplayEngine {
    private static AnalyzerCore core;
    private static MinecraftClient mc;
    private static boolean registered;
    private static World world;
    private static LivingEntity target;
    private static final Map<String, String> statuses = new HashMap<>();
    private static final Map<String, Long> next = new HashMap<>();
    private static final Map<String, Long> actionCounts = new HashMap<>();
    private static final Map<KeyBinding, Boolean> forcedKeys = new IdentityHashMap<>();
    private static final List<String> IDS = GameplayModules.definitions().stream().map(m -> m.id()).toList();
    private static long ticks, attacks, placements, inventoryClicks, movementChanges;
    private static long lastFailure = -1000;
    private static int attackBudget, interactionBudget, inventoryBudget, savedSlot = -1, ownedSlot = -1, deathTicks;
    private static boolean slotRequested, wasGround;
    private static PlayerEntity slotPlayer;
    private static Double stepOriginal, stepWritten;
    private static PlayerEntity stepPlayer;
    private static boolean consuming;
    private static String consumeModule;
    private static long consumeStart;
    private static long fishingAwaitUntil;
    private GameplayEngine() { }

    public static void register(AnalyzerCore analyzer) {
        core = analyzer; mc = MinecraftClient.getInstance();
        if (registered) return;
        registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(GameplayEngine::tick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> { releaseKeys(); restoreSlot(); restoreStep(); });
    }
    public static LivingEntity target() { return target != null && target.isAlive() ? target : null; }
    public static String status(String id) {
        if (core == null || !on(id)) return "Disabled";
        return statuses.getOrDefault(id, "Waiting for a world");
    }
    public record Diagnostics(long ticks, long attacks, long placements, long inventoryClicks, long movementChanges) { }
    public static Diagnostics diagnostics() { return new Diagnostics(ticks, attacks, placements, inventoryClicks, movementChanges); }
    /** Cumulative real accepted interactions/input or motion changes, useful to HUD and smoke checks. */
    public static long actions(String id) { return actionCounts.getOrDefault(id, 0L); }
    private static void acted(String id) { actionCounts.merge(id, 1L, Long::sum); }
    public static boolean safeWalkActive() {
        return core != null && mc != null && mc.player != null && mc.currentScreen == null && mc.world != null && on("safe-walk");
    }

    private static void tick(MinecraftClient client) {
        ticks++;
        releaseKeys();
        if (world != client.world) {
            restoreSlot(); restoreStep(); consuming = false; fishingAwaitUntil = 0; target = null; next.clear(); deathTicks = 0; world = client.world;
        }
        if (client.player == null || client.world == null || client.interactionManager == null) { target = null; restoreStep(); return; }
        var p = client.player;
        if (p.isDead()) {
            restoreSlot(); restoreStep(); consuming = false; fishingAwaitUntil = 0; target = null;
            if (on("auto-respawn") && client.currentScreen instanceof DeathScreen && ++deathTicks >= num("auto-respawn", "delay")) {
                p.requestRespawn(); client.setScreen(null); deathTicks = 0; acted("auto-respawn"); note("auto-respawn", "Respawn requested");
            }
            return;
        }
        deathTicks = 0;
        if (client.currentScreen != null || p.isSpectator() || p.hasVehicle()
                || p.currentScreenHandler != p.playerScreenHandler || !p.currentScreenHandler.getCursorStack().isEmpty()) {
            if (consuming) client.interactionManager.stopUsingItem(p);
            consuming = false; fishingAwaitUntil = 0; restoreSlot(); restoreStep(); target = null;
            for (String id : IDS) if (on(id)) note(id, "Paused while a menu, vehicle or inventory transaction is active");
            return;
        }
        if (slotPlayer != null && slotPlayer != p) restoreSlot();
        if (ownedSlot >= 0 && p.getInventory().selectedSlot != ownedSlot) { savedSlot = ownedSlot = -1; slotPlayer = null; consuming = false; fishingAwaitUntil = 0; }
        slotRequested = false; target = null; attackBudget = interactionBudget = inventoryBudget = 1;
        for (String id : IDS) if (on(id)) note(id, "Ready");
        try {
            boolean eating = food();
            if (!eating && !p.isUsingItem()) { inventory(); tools(); combat(); hvh(); fishing(); }
            else if (!eating) combat(); // Bow aiming can run during the player's own bow use.
            movement(); playerUtilities();
        } catch (RuntimeException ex) {
            if (ticks - lastFailure >= 100) { lastFailure = ticks; AnalyzerClient.LOGGER.warn("Gameplay automation paused after a guarded tick failure", ex); }
            for (String id : IDS) if (on(id)) note(id, "Paused: " + ex.getClass().getSimpleName());
        } finally {
            if (!slotRequested && !consuming) restoreSlot();
            wasGround = p.isOnGround();
        }
    }
    private static boolean on(String id) { return core != null && core.isEnabled(id); }
    private static double num(String id, String setting) { return core.getNumber(id, setting); }
    private static boolean bool(String id, String setting) { return core.getBoolean(id, setting); }
    private static void note(String id, String value) { statuses.put(id, value); }
    private static boolean ready(String id, int delay) {
        if (ticks < next.getOrDefault(id, 0L)) return false;
        next.put(id, ticks + delay); return true;
    }
    private static void force(KeyBinding key) { forcedKeys.putIfAbsent(key, key.isPressed()); key.setPressed(true); }
    private static void releaseKeys() {
        if (mc == null) return;
        for (var entry : forcedKeys.entrySet()) {
            InputUtil.Key key = KeyBindingHelper.getBoundKeyOf(entry.getKey());
            boolean pressed = key.getCode() >= 0 && (key.getCategory() == InputUtil.Type.MOUSE
                    ? GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), key.getCode()) == GLFW.GLFW_PRESS
                    : InputUtil.isKeyPressed(mc.getWindow().getHandle(), key.getCode()));
            entry.getKey().setPressed(pressed);
        }
        forcedKeys.clear();
    }
    private static void selectSlot(int slot) {
        if (slot < 0 || slot > 8) return;
        if (savedSlot < 0) { savedSlot = mc.player.getInventory().selectedSlot; slotPlayer = mc.player; }
        mc.player.getInventory().selectedSlot = ownedSlot = slot; slotRequested = true;
    }
    private static void restoreSlot() {
        if (slotPlayer != null && savedSlot >= 0 && slotPlayer.getInventory().selectedSlot == ownedSlot)
            slotPlayer.getInventory().selectedSlot = savedSlot;
        savedSlot = ownedSlot = -1; slotPlayer = null;
    }
    private static int hotbar(Predicate<ItemStack> predicate) {
        for (int i = 0; i < 9; i++) if (predicate.test(mc.player.getInventory().getStack(i))) return i;
        return -1;
    }
    private static int inventoryItem(Predicate<ItemStack> predicate) {
        for (int i = 0; i < 36; i++) if (predicate.test(mc.player.getInventory().getStack(i))) return i;
        return -1;
    }
    private static int screenSlot(int inventorySlot) { return inventorySlot < 9 ? 36 + inventorySlot : inventorySlot; }
    private static boolean click(int slot, int button, SlotActionType action, String module) {
        if (inventoryBudget < 1 || mc.player.currentScreenHandler != mc.player.playerScreenHandler
                || !mc.player.currentScreenHandler.getCursorStack().isEmpty()) return false;
        inventoryBudget--; inventoryClicks++; acted(module);
        mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, slot, button, action, mc.player); return true;
    }

    private static boolean food() {
        var p = mc.player;
        if (consuming && (!on(consumeModule) || ticks - consumeStart > 80)) {
            mc.interactionManager.stopUsingItem(p); consuming = false; restoreSlot();
        }
        if (consuming) {
            if (!p.isUsingItem()) { consuming = false; restoreSlot(); return false; }
            slotRequested = true; force(mc.options.useKey); note(consumeModule, "Eating; use input and hotbar slot owned temporarily"); return true;
        }
        if (p.isUsingItem() || interactionBudget == 0) return false;
        String id = on("auto-gapple") && p.getHealth() <= num("auto-gapple", "health") ? "auto-gapple"
                : on("auto-eat") && p.getHungerManager().getFoodLevel() < num("auto-eat", "hunger") ? "auto-eat" : null;
        if (id == null) return false;
        int slot = hotbar(stack -> !stack.isEmpty() && (id.equals("auto-gapple")
                ? stack.isOf(Items.GOLDEN_APPLE) || stack.isOf(Items.ENCHANTED_GOLDEN_APPLE)
                : stack.contains(DataComponentTypes.FOOD)));
        if (slot < 0) { note(id, "Put food in the hotbar"); return false; }
        selectSlot(slot); interactionBudget--;
        var result = mc.interactionManager.interactItem(p, Hand.MAIN_HAND);
        if (result.isAccepted() && p.isUsingItem()) {
            consuming = true; consumeModule = id; consumeStart = ticks; acted(id); force(mc.options.useKey); note(id, "Eating"); return true;
        }
        note(id, "Food use unavailable"); return false;
    }
    private static void inventory() {
        var p = mc.player;
        if (on("auto-totem") && !p.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
            int slot = inventoryItem(s -> s.isOf(Items.TOTEM_OF_UNDYING));
            if (slot < 0) note("auto-totem", "No inventory totem");
            else if (click(screenSlot(slot), 40, SlotActionType.SWAP, "auto-totem")) note("auto-totem", "Totem moved to offhand");
        }
        if (on("auto-armor") && inventoryBudget > 0 && ready("auto-armor", 5)) {
            for (EquipmentSlot armor : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
                ItemStack current = p.getEquippedStack(armor); int best = -1; double score = armorScore(current);
                if (current.isOf(Items.ELYTRA)) continue;
                for (int i = 0; i < 36; i++) {
                    ItemStack candidate = p.getInventory().getStack(i);
                    var equip = candidate.get(DataComponentTypes.EQUIPPABLE);
                    if (equip != null && equip.slot() == armor && armorScore(candidate) > score + 0.01) { best = i; score = armorScore(candidate); }
                }
                if (best < 0) continue;
                if (!current.isEmpty()) {
                    if (inventoryItem(ItemStack::isEmpty) < 0) { note("auto-armor", "Need an empty inventory slot before replacing armor"); break; }
                    var armorSlot = p.playerScreenHandler.slots.get(8 - armor.getEntitySlotId());
                    if (!armorSlot.canTakeItems(p)) { note("auto-armor", "Equipped armor cannot be removed"); continue; }
                    click(8 - armor.getEntitySlotId(), 0, SlotActionType.QUICK_MOVE, "auto-armor");
                    note("auto-armor", "Stored old armor; equip on next inventory interval");
                } else { click(screenSlot(best), 0, SlotActionType.QUICK_MOVE, "auto-armor"); note("auto-armor", "Equipped stronger material-tier armor"); }
                break;
            }
        }
        if (on("refill") && inventoryBudget > 0 && ready("refill", 6)) {
            outer: for (int h = 0; h < 9; h++) {
                ItemStack held = p.getInventory().getStack(h);
                if (held.isEmpty() || held.getMaxCount() <= 1 || held.getCount() >= num("refill", "threshold")) continue;
                for (int i = 9; i < 36; i++) {
                    ItemStack candidate = p.getInventory().getStack(i);
                    if (candidate.getCount() > held.getCount() && ItemStack.areItemsAndComponentsEqual(held, candidate)) {
                        click(i, h, SlotActionType.SWAP, "refill"); note("refill", "Restocked hotbar slot " + (h + 1)); break outer;
                    }
                }
            }
        }
    }
    private static double tier(ItemStack stack) {
        String name = Registries.ITEM.getId(stack.getItem()).getPath();
        return name.contains("netherite") ? 6 : name.contains("diamond") ? 5 : name.contains("iron") ? 4
                : name.contains("chainmail") ? 3 : name.contains("stone") ? 3 : name.contains("gold") ? 2 : 1;
    }
    private static double armorScore(ItemStack stack) {
        if (stack.isEmpty() || stack.isOf(Items.ELYTRA)) return 0;
        String name = Registries.ITEM.getId(stack.getItem()).getPath();
        if (!(name.endsWith("helmet") || name.endsWith("chestplate") || name.endsWith("leggings") || name.endsWith("boots"))) return 0;
        return tier(stack) * 100 + (stack.isDamageable() ? 1.0 - stack.getDamage() / (double) stack.getMaxDamage() : 1);
    }
    private static void tools() {
        if (!on("auto-tool") || !mc.options.attackKey.isPressed() || !(mc.crosshairTarget instanceof BlockHitResult hit)) return;
        var state = mc.world.getBlockState(hit.getBlockPos()); int best = -1; float speed = 1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            float score = stack.getMiningSpeedMultiplier(state);
            if (score > speed && (!stack.isDamageable() || stack.getDamage() < stack.getMaxDamage() - 1)) { best = i; speed = score; }
        }
        if (best >= 0) { if (mc.player.getInventory().selectedSlot != best) acted("auto-tool"); selectSlot(best); note("auto-tool", "Selected hotbar tool · mining speed " + speed); }
        else note("auto-tool", "No faster usable hotbar tool");
    }

    private static LivingEntity findTarget(String id) {
        if (!bool(id, "players") && !bool(id, "hostiles") && !bool(id, "passives")) { note(id, "Select a target category"); return null; }
        var p = mc.player; LivingEntity best = null; double bestDistance = num(id, "range") * num(id, "range"); int examined = 0;
        for (Entity entity : mc.world.getEntities()) {
            if (++examined > 512) break;
            if (!(entity instanceof LivingEntity living) || living == p || !living.isAlive() || living.isSpectator()
                    || p.isTeammate(living) || !p.canSee(living)) continue;
            if (living instanceof PlayerEntity && (!bool(id, "players") || UtilityEngine.isFriend(living.getUuid()))) continue;
            if (living instanceof Monster ? !bool(id, "hostiles") : living instanceof MobEntity && !bool(id, "passives")) continue;
            if (!(living instanceof PlayerEntity) && !(living instanceof MobEntity)) continue;
            if (living instanceof TameableEntity pet && pet.isTamed()) continue;
            double distance = p.squaredDistanceTo(living);
            Vec3d delta = living.getEyePos().subtract(p.getEyePos());
            float yaw = (float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90);
            if (Math.abs(MathHelper.wrapDegrees(yaw - p.getYaw())) > num(id, "fov") * 0.5) continue;
            if (distance < bestDistance) { best = living; bestDistance = distance; }
        }
        if (best == null) note(id, "No visible selected targets in range");
        return best;
    }
    private static void aim(Vec3d point, double degrees) {
        var p = mc.player; Vec3d delta = point.subtract(p.getEyePos());
        float yaw = (float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90);
        float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z)));
        p.setYaw(p.getYaw() + MathHelper.clamp(MathHelper.wrapDegrees(yaw - p.getYaw()), (float) -degrees, (float) degrees));
        p.setPitch(MathHelper.clamp(p.getPitch() + MathHelper.clamp(pitch - p.getPitch(), (float) -degrees, (float) degrees), -90, 90));
    }
    private static boolean validCrosshair(String id, Entity entity) {
        if (!(entity instanceof LivingEntity living) || !living.isAlive() || living == mc.player || living.isSpectator()
                || mc.player.isTeammate(living) || !mc.player.canSee(living) || !mc.player.canInteractWithEntity(living, 0)
                || mc.player.squaredDistanceTo(living) > num(id, "range") * num(id, "range")) return false;
        Vec3d delta = living.getEyePos().subtract(mc.player.getEyePos());
        float yaw = (float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90);
        if (Math.abs(MathHelper.wrapDegrees(yaw - mc.player.getYaw())) > num(id, "fov") * 0.5) return false;
        if (living instanceof PlayerEntity) return bool(id, "players") && !UtilityEngine.isFriend(living.getUuid());
        if (living instanceof TameableEntity pet && pet.isTamed()) return false;
        return living instanceof Monster ? bool(id, "hostiles") : living instanceof MobEntity && bool(id, "passives");
    }
    private static int weapon(boolean axeOnly) {
        int best = -1; double score = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i); if (stack.isEmpty()) continue;
            String name = Registries.ITEM.getId(stack.getItem()).getPath();
            boolean axe = name.endsWith("_axe"), sword = name.endsWith("_sword");
            if (axeOnly && !axe || !axeOnly && !axe && !sword && !stack.isOf(Items.MACE) && !stack.isOf(Items.TRIDENT)) continue;
            if (stack.isDamageable() && stack.getDamage() >= stack.getMaxDamage() - 1) continue;
            double value = stack.isOf(Items.MACE) ? 6 : stack.isOf(Items.TRIDENT) ? 9 : axe ? 6 + tier(stack) : 3 + tier(stack);
            if (value > score) { score = value; best = i; }
        }
        return best;
    }
    private static void combat() {
        var p = mc.player;
        if (on("bow-aimbot") && p.isUsingItem() && p.getActiveItem().isOf(Items.BOW)) {
            LivingEntity bowTarget = findTarget("bow-aimbot");
            if (bowTarget != null) {
                double charge = Math.min(1, p.getItemUseTime() / 20.0), speed = Math.max(0.2, (charge * charge + charge * 2) / 3 * 3);
                double flight = p.distanceTo(bowTarget) / speed;
                Vec3d point = bowTarget.getEyePos().add(bowTarget.getVelocity().multiply(flight)).add(0, 0.025 * flight * flight, 0);
                aim(point, num("bow-aimbot", "turn-speed")); acted("bow-aimbot"); target = bowTarget; note("bow-aimbot", "Leading " + bowTarget.getName().getString());
            }
        }
        if (p.isUsingItem()) return;
        if (on("aim-assist") && mc.options.attackKey.isPressed()) {
            LivingEntity aimed = findTarget("aim-assist");
            if (aimed != null) { aim(aimed.getEyePos(), num("aim-assist", "turn-speed")); acted("aim-assist"); target = aimed; note("aim-assist", "Aiming at " + aimed.getName().getString()); }
        }
        LivingEntity victim = null; String attacker = null;
        if (on("kill-aura")) { victim = findTarget("kill-aura"); attacker = "kill-aura"; }
        for (String id : List.of("trigger-bot", "auto-clicker", "criticals")) {
            if (victim == null && on(id) && (id.equals("trigger-bot") || mc.options.attackKey.isPressed())
                    && mc.crosshairTarget instanceof EntityHitResult hit && validCrosshair(id, hit.getEntity())) {
                victim = (LivingEntity) hit.getEntity(); attacker = id;
            }
        }
        LivingEntity pointed = mc.crosshairTarget instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity living ? living : null;
        if (on("auto-weapon") && (victim != null || mc.options.attackKey.isPressed() && pointed != null)) {
            int slot = weapon(false); if (slot >= 0) { if (p.getInventory().selectedSlot != slot) acted("auto-weapon"); selectSlot(slot); note("auto-weapon", "Hotbar weapon selected"); } else note("auto-weapon", "No hotbar melee weapon");
        }
        if (on("shield-breaker") && pointed != null && pointed.isBlocking() && p.canSee(pointed)
                && p.canInteractWithEntity(pointed, 0) && !p.isTeammate(pointed)
                && !UtilityEngine.isFriend(pointed.getUuid())) {
            int slot = weapon(true);
            if (slot >= 0) { if (p.getInventory().selectedSlot != slot) acted("shield-breaker"); selectSlot(slot); if (victim == null) { victim = pointed; attacker = "shield-breaker"; } note("shield-breaker", "Axe selected for blocking target"); }
            else note("shield-breaker", "Need a hotbar axe");
        }
        if (victim == null) { if (!on("aim-assist") && !on("bow-aimbot")) target = null; return; }
        target = victim;
        if (!p.canInteractWithEntity(victim, 0)) { note(attacker, "Target outside vanilla attack reach"); return; }
        double cooldown = attacker.equals("shield-breaker") ? 0.95 : num(attacker, "cooldown");
        if (p.getAttackCooldownProgress(0) < cooldown) { note(attacker, "Waiting for attack cooldown"); return; }
        if (on("criticals") && !p.isTouchingWater() && !p.isClimbing() && !p.getAbilities().flying) {
            if (p.isOnGround()) { p.jump(); acted("criticals"); note("criticals", "Jumping; waiting for a normal falling attack"); return; }
            if (p.getVelocity().y >= 0 || p.fallDistance <= 0) { note("criticals", "Waiting for falling phase"); return; }
        }
        aim(victim.getEyePos(), attacker.equals("shield-breaker") ? 30 : num(attacker, "turn-speed"));
        if (!attacker.equals("auto-clicker") || ready(attacker, Math.max(1, (int) Math.ceil(20 / num(attacker, "cps"))))) attack(victim, attacker);
    }
    private static boolean attack(Entity entity, String id) {
        if (attackBudget == 0 || !mc.player.canInteractWithEntity(entity, 0) || !mc.player.canSee(entity)) return false;
        attackBudget--; mc.interactionManager.attackEntity(mc.player, entity); mc.player.swingHand(Hand.MAIN_HAND); attacks++; acted(id);
        note(id, "Attacked " + entity.getName().getString()); return true;
    }

    private static boolean interact(BlockHitResult hit, int slot, String id) {
        if (slot < 0) { note(id, "Required item is missing from the hotbar"); return false; }
        if (interactionBudget == 0 || !mc.player.canInteractWithBlockAt(hit.getBlockPos(), 0)) return false;
        var ray = mc.world.raycast(new RaycastContext(mc.player.getEyePos(), hit.getPos(), RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player));
        if (ray.getType() == HitResult.Type.BLOCK && !ray.getBlockPos().equals(hit.getBlockPos())) { note(id, "Support is obstructed"); return false; }
        int original = mc.player.getInventory().selectedSlot;
        try {
            mc.player.getInventory().selectedSlot = slot; interactionBudget--;
            var result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
            if (result.isAccepted()) { mc.player.swingHand(Hand.MAIN_HAND); placements++; acted(id); note(id, "Block interaction accepted"); return true; }
            note(id, "Waiting for a valid block interaction"); return false;
        } finally { mc.player.getInventory().selectedSlot = original; }
    }
    private static boolean place(BlockPos pos, int slot, String id) {
        if (interactionBudget == 0 || !mc.world.getBlockState(pos).isReplaceable()) return false;
        if (slot < 0) { note(id, "Required block is missing from the hotbar"); return false; }
        for (Direction direction : Direction.values()) {
            BlockPos support = pos.offset(direction);
            var state = mc.world.getBlockState(support);
            if (state.isReplaceable() || state.getCollisionShape(mc.world, support).isEmpty()) continue;
            Direction face = direction.getOpposite();
            Vec3d hit = Vec3d.ofCenter(support).add(Vec3d.of(face.getVector()).multiply(0.499));
            if (!mc.player.canInteractWithBlockAt(support, 0)) continue;
            if (interact(new BlockHitResult(hit, face, support, false), slot, id)) return true;
            if (interactionBudget == 0) return false;
        }
        note(id, "No reachable visible support face"); return false;
    }
    private static BlockHitResult upperFace(BlockPos pos) { return new BlockHitResult(Vec3d.ofCenter(pos).add(0, 0.499, 0), Direction.UP, pos, false); }
    private static void hvh() {
        var p = mc.player;
        int obsidian = hotbar(s -> s.isOf(Items.OBSIDIAN));
        if (on("surround") && ready("surround", (int) num("surround", "delay"))) {
            BlockPos feet = p.getBlockPos();
            for (Direction direction : List.of(Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST)) {
                BlockPos pos = feet.offset(direction);
                if (bool("surround", "support") && mc.world.getBlockState(pos.down()).isReplaceable()) {
                    if (place(pos.down(), obsidian, "surround")) break;
                } else if (place(pos, obsidian, "surround")) break;
                if (interactionBudget == 0) break;
            }
        }
        for (String id : List.of("auto-trap", "auto-web", "crystal-aura", "anchor-aura", "target-strafe")) {
            if (!on(id)) continue;
            LivingEntity enemy = findTarget(id); if (enemy == null) continue;
            target = enemy;
            if (id.equals("target-strafe")) { strafe(enemy); continue; }
            if (!ready(id, (int) num(id, "delay"))) continue;
            BlockPos feet = enemy.getBlockPos();
            switch (id) {
                case "auto-trap" -> {
                    for (BlockPos pos : List.of(feet.north(), feet.south(), feet.east(), feet.west(), feet.north().up(), feet.south().up(), feet.east().up(), feet.west().up(), feet.up(2))) {
                        if (place(pos, obsidian, id) || interactionBudget == 0) break;
                    }
                }
                case "auto-web" -> place(feet, hotbar(s -> s.isOf(Items.COBWEB)), id);
                case "crystal-aura" -> crystals(enemy);
                case "anchor-aura" -> anchors(enemy);
                default -> { }
            }
        }
        if (on("hole-filler") && interactionBudget > 0 && ready("hole-filler", (int) num("hole-filler", "delay"))) {
            BlockPos center = p.getBlockPos(); int radius = (int) Math.ceil(num("hole-filler", "range")), examined = 0;
            outer: for (int y = -1; y <= 0; y++) for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
                if (++examined > 256) break outer;
                BlockPos pos = center.add(x, y, z);
                if (pos.equals(center) || !mc.world.getBlockState(pos).isAir() || !mc.world.getBlockState(pos.up()).isAir()
                        || mc.world.getBlockState(pos.down()).getCollisionShape(mc.world, pos.down()).isEmpty()) continue;
                boolean walls = true;
                for (Direction direction : List.of(Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST)) {
                    BlockPos wall = pos.offset(direction); if (mc.world.getBlockState(wall).getCollisionShape(mc.world, wall).isEmpty()) { walls = false; break; }
                }
                if (walls && place(pos, obsidian, "hole-filler")) break outer;
                if (interactionBudget == 0) break outer;
            }
        }
    }
    private static void crystals(LivingEntity enemy) {
        String id = "crystal-aura"; var p = mc.player;
        if (p.getHealth() < num(id, "health")) { note(id, "Below minimum health"); return; }
        double self = num(id, "self-distance");
        if (bool(id, "break")) {
            int examined = 0;
            for (Entity entity : mc.world.getEntities()) {
                if (++examined > 512) break;
                if (entity instanceof EndCrystalEntity && enemy.squaredDistanceTo(entity) < 25 && p.squaredDistanceTo(entity) >= self * self
                        && p.canInteractWithEntity(entity, 0) && p.canSee(entity) && p.getAttackCooldownProgress(0) >= num(id, "cooldown")) {
                    if (attack(entity, id)) return;
                }
            }
        }
        if (!bool(id, "place")) return;
        int slot = hotbar(s -> s.isOf(Items.END_CRYSTAL));
        if (slot < 0) { note(id, "Need hotbar end crystals"); return; }
        BlockPos feet = enemy.getBlockPos();
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            BlockPos base = feet.add(x, -1, z); var block = mc.world.getBlockState(base);
            Vec3d center = Vec3d.ofCenter(base.up());
            if (!(block.isOf(Blocks.OBSIDIAN) || block.isOf(Blocks.BEDROCK)) || !mc.world.getBlockState(base.up()).isAir()
                    || !mc.world.getBlockState(base.up(2)).isAir() || center.squaredDistanceTo(p.getPos()) < self * self
                    || !mc.world.getOtherEntities(null, new Box(base.up()).stretch(0, 1, 0)).isEmpty()) continue;
            if (interact(upperFace(base), slot, id) || interactionBudget == 0) return;
        }
        note(id, "Need clear reachable obsidian/bedrock away from self");
    }
    private static void anchors(LivingEntity enemy) {
        String id = "anchor-aura"; var p = mc.player;
        if (mc.world.getDimension().respawnAnchorWorks()) { note(id, "Paused: anchors are respawn points in this dimension"); return; }
        if (p.getHealth() < num(id, "health")) { note(id, "Below minimum health"); return; }
        int anchor = hotbar(s -> s.isOf(Items.RESPAWN_ANCHOR)), glowstone = hotbar(s -> s.isOf(Items.GLOWSTONE));
        BlockPos feet = enemy.getBlockPos(); double self = num(id, "self-distance");
        for (BlockPos pos : List.of(feet.north(), feet.south(), feet.east(), feet.west(), feet.north().up(), feet.south().up())) {
            if (Vec3d.ofCenter(pos).squaredDistanceTo(p.getPos()) < self * self) continue;
            var state = mc.world.getBlockState(pos);
            if (state.isOf(Blocks.RESPAWN_ANCHOR)) {
                if (state.get(RespawnAnchorBlock.CHARGES) == 0) interact(upperFace(pos), glowstone, id);
                else {
                    int neutral = hotbar(s -> !s.isOf(Items.GLOWSTONE));
                    if (p.getOffHandStack().isOf(Items.GLOWSTONE) && state.get(RespawnAnchorBlock.CHARGES) < 4) { note(id, "Remove offhand glowstone before detonating"); return; }
                    interact(upperFace(pos), neutral, id);
                }
            } else place(pos, anchor, id);
            if (interactionBudget == 0) return;
        }
        note(id, "Need a reachable safe-distance anchor location");
    }
    private static void strafe(LivingEntity enemy) {
        if (on("fly") || !mc.player.isOnGround() || mc.player.isTouchingWater()) return;
        Vec3d delta = enemy.getPos().subtract(mc.player.getPos()); double length = Math.hypot(delta.x, delta.z);
        if (length < 0.1) return;
        double side = bool("target-strafe", "clockwise") ? 1 : -1;
        double radial = Math.clamp((length - num("target-strafe", "radius")) * 0.5, -0.8, 0.8);
        Vec3d direction = new Vec3d(delta.x * radial - delta.z * side, 0, delta.z * radial + delta.x * side).normalize();
        Vec3d v = mc.player.getVelocity(); double speed = num("target-strafe", "speed");
        mc.player.setVelocity(direction.x * speed, v.y, direction.z * speed); movementChanges++; acted("target-strafe");
        note("target-strafe", "Orbiting " + enemy.getName().getString());
    }

    private static void movement() {
        var p = mc.player;
        if (on("auto-walk")) { force(mc.options.forwardKey); acted("auto-walk"); }
        double forward = (mc.options.forwardKey.isPressed() ? 1 : 0) - (mc.options.backKey.isPressed() ? 1 : 0);
        double side = (mc.options.leftKey.isPressed() ? 1 : 0) - (mc.options.rightKey.isPressed() ? 1 : 0);
        boolean moving = forward != 0 || side != 0;
        double yaw = Math.toRadians(p.getYaw());
        Vec3d direction = new Vec3d(-Math.sin(yaw) * forward + Math.cos(yaw) * side, 0, Math.cos(yaw) * forward + Math.sin(yaw) * side);
        if (moving) direction = direction.normalize();
        if (on("keep-sprint") && forward > 0 && !mc.options.sneakKey.isPressed() && !p.isUsingItem()
                && (p.getHungerManager().getFoodLevel() > 6 || p.getAbilities().creativeMode)) { force(mc.options.sprintKey); acted("keep-sprint"); }
        if (on("step")) {
            var attribute = p.getAttributeInstance(EntityAttributes.STEP_HEIGHT);
            if (attribute != null) {
                if (stepOriginal == null || stepPlayer != p) { restoreStep(); stepOriginal = attribute.getBaseValue(); stepPlayer = p; }
                stepWritten = num("step", "height"); attribute.setBaseValue(stepWritten); acted("step"); note("step", "Client step height " + stepWritten);
            }
        } else restoreStep();
        Vec3d v = p.getVelocity();
        if (on("fly")) {
            double speed = num("fly", "speed"); double vertical = (mc.options.jumpKey.isPressed() ? speed : 0) - (mc.options.sneakKey.isPressed() ? speed : 0);
            p.setVelocity(direction.x * speed, vertical, direction.z * speed); movementChanges++; acted("fly"); note("fly", "Client motion; server may correct it"); return;
        }
        if (p.isTouchingWater() || p.isInLava()) return;
        if (on("speed") && moving && !p.isUsingItem() && !mc.options.sneakKey.isPressed()) {
            double speed = num("speed", "speed"); v = new Vec3d(direction.x * speed, v.y, direction.z * speed); movementChanges++; acted("speed");
        } else if (on("ground-strafe") && moving && p.isOnGround()) {
            double speed = Math.min(0.8, Math.hypot(v.x, v.z)); v = new Vec3d(direction.x * speed, v.y, direction.z * speed); movementChanges++; acted("ground-strafe");
        }
        if (on("parkour") && moving && p.isOnGround() && !p.isClimbing()) {
            Box ahead = p.getBoundingBox().offset(direction.multiply(0.65)).offset(0, -0.15, 0);
            if (mc.world.isSpaceEmpty(p, ahead)) { p.jump(); v = p.getVelocity(); movementChanges++; acted("parkour"); note("parkour", "Jumped approaching an unsupported edge"); }
        }
        boolean jumped = wasGround && !p.isOnGround() && v.y > 0.2;
        if (on("high-jump") && jumped) { v = new Vec3d(v.x, Math.max(v.y, num("high-jump", "height")), v.z); movementChanges++; acted("high-jump"); }
        if (on("long-jump") && jumped && moving) { double speed = num("long-jump", "speed"); v = new Vec3d(direction.x * speed, v.y, direction.z * speed); movementChanges++; acted("long-jump"); }
        if (on("fast-ladder") && p.isClimbing() && (forward > 0 || mc.options.jumpKey.isPressed())) { v = new Vec3d(v.x, Math.max(v.y, num("fast-ladder", "speed")), v.z); movementChanges++; acted("fast-ladder"); }
        if (!p.isOnGround() && !p.isClimbing() && !p.isGliding() && v.y < 0) {
            if (on("fast-fall") && p.fallDistance > 1) { v = new Vec3d(v.x, -Math.max(-v.y, num("fast-fall", "speed")), v.z); movementChanges++; acted("fast-fall"); }
            else if (on("reverse-step") && !mc.world.isSpaceEmpty(p, p.getBoundingBox().offset(0, -1.5, 0))) { v = new Vec3d(v.x, -Math.max(-v.y, num("reverse-step", "speed")), v.z); movementChanges++; acted("reverse-step"); }
            if (on("glide")) { v = new Vec3d(v.x, Math.max(v.y, -num("glide", "speed")), v.z); movementChanges++; acted("glide"); }
        }
        if (on("elytra-boost") && p.isGliding()) {
            v = v.add(p.getRotationVec(1).multiply(num("elytra-boost", "thrust")));
            if (v.length() > 3) v = v.normalize().multiply(3);
            movementChanges++; acted("elytra-boost"); note("elytra-boost", "Adding a bounded gliding impulse");
        }
        p.setVelocity(v);
    }
    private static void restoreStep() {
        if (stepPlayer != null && stepOriginal != null) {
            var attribute = stepPlayer.getAttributeInstance(EntityAttributes.STEP_HEIGHT);
            if (attribute != null && stepWritten != null && Math.abs(attribute.getBaseValue() - stepWritten) < 0.000001) attribute.setBaseValue(stepOriginal);
        }
        stepPlayer = null; stepOriginal = stepWritten = null;
    }
    private static void playerUtilities() {
        var p = mc.player;
        if (on("anti-afk") && ready("anti-afk", (int) (num("anti-afk", "interval") * 20))) {
            p.setYaw(p.getYaw() + (ticks % 2 == 0 ? 15 : -15)); if (p.isOnGround()) force(mc.options.jumpKey);
            acted("anti-afk"); note("anti-afk", "Ordinary turn/jump input");
        }
        if (on("durability-alert") && ready("durability-alert", 80)) {
            for (EquipmentSlot slot : List.of(EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
                ItemStack stack = p.getEquippedStack(slot);
                if (stack.isDamageable() && 100.0 * (stack.getMaxDamage() - stack.getDamage()) / stack.getMaxDamage() <= num("durability-alert", "threshold")) {
                    String message = stack.getName().getString() + " · low durability";
                    mc.inGameHud.setOverlayMessage(Text.literal(message), false); acted("durability-alert"); note("durability-alert", message); break;
                }
            }
        }
    }
    private static void fishing() {
        if (!on("auto-fish")) { fishingAwaitUntil = 0; return; }
        boolean hooked = mc.player.fishHook != null;
        if (hooked) fishingAwaitUntil = 0;
        boolean ownsRod = slotPlayer == mc.player && ownedSlot >= 0
                && mc.player.getInventory().selectedSlot == ownedSlot && mc.player.getMainHandStack().isOf(Items.FISHING_ROD);
        // Vanilla discards a bobber if neither hand holds a rod. Retain only our
        // existing slot ownership, including while the cast packet is in flight.
        if (ownsRod && (hooked || ticks <= fishingAwaitUntil)) slotRequested = true;
        if (interactionBudget == 0 || mc.player.isUsingItem()) return;
        if (!hooked && ownsRod && ticks <= fishingAwaitUntil) { note("auto-fish", "Waiting for the cast bobber"); return; }
        int slot = hotbar(s -> s.isOf(Items.FISHING_ROD));
        if (slot < 0) { note("auto-fish", "Need a hotbar fishing rod"); return; }
        if (hooked && !mc.player.getMainHandStack().isOf(Items.FISHING_ROD) && !mc.player.getOffHandStack().isOf(Items.FISHING_ROD)) {
            note("auto-fish", "Fishing interrupted by an item change"); return;
        }
        boolean bite = hooked && ((FishingBobberAccessor) mc.player.fishHook).analyzer$caughtFish();
        if (!bite && (hooked || !bool("auto-fish", "cast"))) { note("auto-fish", hooked ? "Waiting for synchronized fish bite" : "Cast the rod to begin"); return; }
        if (!ready("auto-fish", (int) num("auto-fish", "delay"))) return;
        selectSlot(slot); interactionBudget--;
        var result = mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        if (!result.isAccepted()) { note("auto-fish", "Fishing interaction unavailable"); return; }
        fishingAwaitUntil = hooked ? 0 : ticks + 40;
        mc.player.swingHand(Hand.MAIN_HAND); acted("auto-fish");
        note("auto-fish", bite ? "Reeled fish; waiting to recast" : "Cast rod");
    }
}
