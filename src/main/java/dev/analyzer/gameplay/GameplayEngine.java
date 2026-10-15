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
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
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
    static AnalyzerCore core;
    static MinecraftClient mc;
    static boolean registered;
    static World world;
    static LivingEntity target;
    static final Map<String, String> statuses = new HashMap<>();
    static final Map<String, Long> next = new HashMap<>();
    static final Map<String, Long> actionCounts = new HashMap<>();
    static final Map<String, Vec3d> interactionAims = new HashMap<>();
    static final Map<KeyBinding, Boolean> forcedKeys = new IdentityHashMap<>();
    static final List<String> IDS = GameplayModules.definitions().stream().map(m -> m.id()).toList();
    static long ticks, attacks, placements, inventoryClicks, movementChanges;
    static long lastFailure = -1000;
    static int attackBudget, interactionBudget, inventoryBudget, savedSlot = -1, ownedSlot = -1, deathTicks;
    static boolean slotRequested, wasGround;
    static PlayerEntity slotPlayer;
    static Double stepOriginal, stepWritten;
    static PlayerEntity stepPlayer;
    static boolean consuming;
    static boolean autoEatRefilling;
    static String consumeModule;
    static long consumeStart;
    static long fishingAwaitUntil;
    private GameplayEngine() { }

    public static void register(AnalyzerCore analyzer) {
        core = analyzer; mc = MinecraftClient.getInstance();
        if (registered) return;
        registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(GameplayEngine::tick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> { releaseKeys(); restoreSlot(); resetControllers(); });
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
    static void acted(String id) { actionCounts.merge(id, 1L, Long::sum); }
    public static boolean safeWalkActive() {
        return MovementController.safeWalkActive();
    }

    static void tick(MinecraftClient client) {
        ticks++;
        releaseKeys();
        if (world != client.world) {
            restoreSlot(); resetControllers(); consuming = false; fishingAwaitUntil = 0; target = null; next.clear(); deathTicks = 0; world = client.world;
        }
        if (client.player == null || client.world == null || client.interactionManager == null) { target = null; resetControllers(); return; }
        var p = client.player;
        if (p.isDead()) {
            restoreSlot(); resetControllers(); consuming = false; fishingAwaitUntil = 0; target = null;
            if (on("auto-respawn") && client.currentScreen instanceof DeathScreen && ++deathTicks >= num("auto-respawn", "delay")) {
                p.requestRespawn(); client.setScreen(null); deathTicks = 0; acted("auto-respawn"); note("auto-respawn", "Respawn requested");
            }
            return;
        }
        deathTicks = 0;
        if (client.currentScreen != null || p.isSpectator() || p.hasVehicle()
                || p.currentScreenHandler != p.playerScreenHandler || !p.currentScreenHandler.getCursorStack().isEmpty()) {
            if (consuming) client.interactionManager.stopUsingItem(p);
            consuming = false; fishingAwaitUntil = 0; restoreSlot(); resetControllers(); target = null;
            for (String id : IDS) if (on(id)) note(id, "Paused while a menu, vehicle or inventory transaction is active");
            return;
        }
        if (slotPlayer != null && slotPlayer != p) restoreSlot();
        if (ownedSlot >= 0 && p.getInventory().selectedSlot != ownedSlot) { savedSlot = ownedSlot = -1; slotPlayer = null; consuming = false; autoEatRefilling = false; fishingAwaitUntil = 0; }
        slotRequested = false; target = null; interactionAims.clear(); attackBudget = interactionBudget = 8; inventoryBudget = 1;
        for (String id : IDS) if (on(id)) note(id, "Ready");
        try {
            boolean eating = food();
            if (!eating && !p.isUsingItem()) { inventory(); tools(); }
            // Each controller applies its own pause-while-using setting. Manual food
            // and Analyzer-owned food both use the actual active-item state.
            if (!eating) { combat(); hvh(); }
            if (!eating && !p.isUsingItem()) fishing();
            movement(); playerUtilities();
        } catch (RuntimeException ex) {
            if (ticks - lastFailure >= 100) { lastFailure = ticks; AnalyzerClient.LOGGER.warn("Gameplay automation paused after a guarded tick failure", ex); }
            for (String id : IDS) if (on(id)) note(id, "Paused: " + ex.getClass().getSimpleName());
        } finally {
            if (!slotRequested && !consuming) restoreSlot();
            wasGround = p.isOnGround();
        }
    }
    static boolean on(String id) { return core != null && core.isEnabled(id); }
    static double num(String id, String setting) { return core.getNumber(id, setting); }
    static boolean bool(String id, String setting) { return core.getBoolean(id, setting); }
    static boolean hasSetting(String id, String setting) {
        return core.definition(id).settings().stream().anyMatch(value -> value.id().equals(setting));
    }
    static boolean boolOr(String id, String setting, boolean fallback) { return hasSetting(id, setting) ? bool(id, setting) : fallback; }
    static double numOr(String id, String setting, double fallback) { return hasSetting(id, setting) ? num(id, setting) : fallback; }
    static String choiceOr(String id, String setting, String fallback) { return hasSetting(id, setting) ? choice(id, setting) : fallback; }
    static void note(String id, String value) { statuses.put(id, value); }
    static boolean ready(String id, int delay) {
        if (ticks < next.getOrDefault(id, 0L)) return false;
        next.put(id, ticks + delay); return true;
    }
    static void force(KeyBinding key) { forcedKeys.putIfAbsent(key, key.isPressed()); key.setPressed(true); }
    static void releaseKeys() {
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
    static void selectSlot(int slot) {
        if (slot < 0 || slot > 8) return;
        if (savedSlot < 0) { savedSlot = mc.player.getInventory().selectedSlot; slotPlayer = mc.player; }
        mc.player.getInventory().selectedSlot = ownedSlot = slot; slotRequested = true;
    }
    static void restoreSlot() {
        if (slotPlayer != null && savedSlot >= 0 && slotPlayer.getInventory().selectedSlot == ownedSlot)
            slotPlayer.getInventory().selectedSlot = savedSlot;
        savedSlot = ownedSlot = -1; slotPlayer = null;
    }
    static int hotbar(Predicate<ItemStack> predicate) {
        for (int i = 0; i < 9; i++) if (predicate.test(mc.player.getInventory().getStack(i))) return i;
        return -1;
    }
    static int inventoryItem(Predicate<ItemStack> predicate) {
        for (int i = 0; i < 36; i++) if (predicate.test(mc.player.getInventory().getStack(i))) return i;
        return -1;
    }
    static int screenSlot(int inventorySlot) { return inventorySlot < 9 ? 36 + inventorySlot : inventorySlot; }
    static boolean click(int slot, int button, SlotActionType action, String module) {
        if (inventoryBudget < 1 || mc.player.currentScreenHandler != mc.player.playerScreenHandler
                || !mc.player.currentScreenHandler.getCursorStack().isEmpty()) return false;
        inventoryBudget--; inventoryClicks++; acted(module);
        mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, slot, button, action, mc.player); return true;
    }

    static boolean food() {
        var p = mc.player;
        if (!on("auto-eat") || p.getHungerManager().getFoodLevel() >= 20) autoEatRefilling = false;
        else if (p.getHungerManager().getFoodLevel() < num("auto-eat", "hunger")) autoEatRefilling = true;
        if (consuming && (!on(consumeModule) || ticks - consumeStart > 80)) {
            mc.interactionManager.stopUsingItem(p); consuming = false; restoreSlot();
        }
        if (consuming) {
            if (!p.isUsingItem()) { consuming = false; restoreSlot(); }
            else { slotRequested = true; force(mc.options.useKey); note(consumeModule, "Eating; use input and hotbar slot owned temporarily"); return true; }
        }
        if (p.isUsingItem() || interactionBudget == 0) return false;
        String id = on("auto-gapple") && p.getHealth() <= num("auto-gapple", "health") ? "auto-gapple"
                : autoEatRefilling ? "auto-eat" : null;
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
    static void inventory() {
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
    static double tier(ItemStack stack) {
        String name = Registries.ITEM.getId(stack.getItem()).getPath();
        return name.contains("netherite") ? 6 : name.contains("diamond") ? 5 : name.contains("iron") ? 4
                : name.contains("chainmail") ? 3 : name.contains("stone") ? 3 : name.contains("gold") ? 2 : 1;
    }
    static double armorScore(ItemStack stack) {
        if (stack.isEmpty() || stack.isOf(Items.ELYTRA)) return 0;
        String name = Registries.ITEM.getId(stack.getItem()).getPath();
        if (!(name.endsWith("helmet") || name.endsWith("chestplate") || name.endsWith("leggings") || name.endsWith("boots"))) return 0;
        return tier(stack) * 100 + (stack.isDamageable() ? 1.0 - stack.getDamage() / (double) stack.getMaxDamage() : 1);
    }
    static void tools() {
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

    static LivingEntity findTarget(String id) { return CombatController.findTarget(id); }
    static void aim(Vec3d point, double degrees) { CombatController.aim(point, degrees); }
    static int weapon(boolean axeOnly) { return CombatController.weapon(axeOnly); }
    static void combat() { CombatController.combat(); }
    static void hvh() { HvhController.hvh(); }
    static void movement() { MovementController.movement(); }
    static void restoreStep() { MovementController.restoreStep(); }
    static String choice(String id, String setting) { return core.getChoice(id, setting); }
    static void resetControllers() {
        autoEatRefilling = false;
        interactionAims.clear();
        CombatController.reset(); HvhController.reset(); MovementController.reset();
    }

    static void swing(String id) {
        switch (choiceOr(id, "swing", "Client")) {
            case "Packet" -> { if (mc.getNetworkHandler() != null) mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND)); }
            case "None" -> { }
            default -> mc.player.swingHand(Hand.MAIN_HAND);
        }
    }
    static boolean allowsObstructed(String id) {
        return !boolOr(id, "legit", false) && (boolOr(id, "through-walls", false) || numOr(id, "wall-range", 0) > 0);
    }
    /** Several candidates may be checked, but a module turns toward one stable point per tick. */
    static boolean aimInteraction(String id, Vec3d point) {
        Vec3d first = interactionAims.putIfAbsent(id, point);
        if (first != null && first.squaredDistanceTo(point) > .0001) return false;
        if (first == null) aim(point, numOr(id, "aim-speed", 35) * (1 - numOr(id, "aim-smoothing", 0) / 200));
        return mc.player.getRotationVec(1).dotProduct(point.subtract(mc.player.getEyePos()).normalize()) >= Math.cos(Math.toRadians(5));
    }

    static boolean attack(Entity entity, String id) {
        if (attackBudget <= 0 || !mc.player.canInteractWithEntity(entity, 0)
                || (!mc.player.canSee(entity) && !allowsObstructed(id))) return false;
        if (!mc.player.canSee(entity) && numOr(id, "wall-range", 0) > 0
                && entity.getBoundingBox().squaredMagnitude(mc.player.getEyePos()) > Math.pow(num(id, "wall-range"), 2)) return false;
        if (hasSetting(id, "aim-speed") && (boolOr(id, "rotate", false) || boolOr(id, "legit", false))) {
            Vec3d point = entity.getBoundingBox().getCenter();
            if (!aimInteraction(id, point)) {
                note(id, "Turning toward crystal"); return false;
            }
            if (boolOr(id, "legit", false) && (!(mc.crosshairTarget instanceof EntityHitResult pointed) || pointed.getEntity() != entity)) {
                note(id, "Waiting for crosshair on crystal"); return false;
            }
        }
        attackBudget--; mc.interactionManager.attackEntity(mc.player, entity); swing(id); attacks++; acted(id);
        note(id, "Attacked " + entity.getName().getString()); return true;
    }

    static boolean interact(BlockHitResult hit, int slot, String id) {
        if (slot < 0) { note(id, "Required item is missing from the hotbar"); return false; }
        if (interactionBudget <= 0 || !mc.player.canInteractWithBlockAt(hit.getBlockPos(), 0)
                || mc.player.getEyePos().squaredDistanceTo(hit.getPos()) > Math.pow(numOr(id, "place-range", 6), 2)) return false;
        var ray = mc.world.raycast(new RaycastContext(mc.player.getEyePos(), hit.getPos(), RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player));
        if (ray.getType() == HitResult.Type.BLOCK && !ray.getBlockPos().equals(hit.getBlockPos()) && !allowsObstructed(id)) { note(id, "Support is obstructed"); return false; }
        if (boolOr(id, "rotate", false) || boolOr(id, "legit", false)) {
            if (!aimInteraction(id, hit.getPos())) { note(id, "Turning toward placement"); return false; }
            if (boolOr(id, "legit", false) && (!(mc.crosshairTarget instanceof BlockHitResult pointed)
                    || !pointed.getBlockPos().equals(hit.getBlockPos()))) { note(id, "Waiting for crosshair on support"); return false; }
        }
        String switching = boolOr(id, "legit", false) ? "Normal" : choiceOr(id, "switch", "Silent");
        if (switching.equals("None") && mc.player.getInventory().selectedSlot != slot) { note(id, "Hold the required hotbar item"); return false; }
        int original = mc.player.getInventory().selectedSlot;
        try {
            if (switching.equals("Normal")) restoreSlot();
            mc.player.getInventory().selectedSlot = slot;
            interactionBudget--;
            var result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
            if (result.isAccepted()) { swing(id); placements++; acted(id); note(id, "Block interaction accepted"); return true; }
            note(id, "Waiting for a valid block interaction"); return false;
        } finally { if (!switching.equals("Normal")) mc.player.getInventory().selectedSlot = original; }
    }
    static boolean place(BlockPos pos, int slot, String id) {
        if (interactionBudget <= 0 || !mc.world.getBlockState(pos).isReplaceable()) return false;
        if (slot < 0) { note(id, "Required block is missing from the hotbar"); return false; }
        ItemStack stack = mc.player.getInventory().getStack(slot);
        if (stack.getItem() instanceof net.minecraft.item.BlockItem item
                && !mc.world.canPlace(item.getBlock().getDefaultState(), pos, net.minecraft.block.ShapeContext.absent())) return false;
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
    static BlockHitResult upperFace(BlockPos pos) { return new BlockHitResult(Vec3d.ofCenter(pos).add(0, 0.499, 0), Direction.UP, pos, false); }
    static void playerUtilities() {
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
    static void fishing() {
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
