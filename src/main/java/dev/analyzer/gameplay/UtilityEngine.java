package dev.analyzer.gameplay;

import dev.analyzer.AnalyzerClient;
import dev.analyzer.core.AnalyzerCore;
import dev.analyzer.mixin.InteractionCooldownAccessor;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.BedBlock;
import net.minecraft.block.CropBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Bounded, normal-interaction utilities. No direct client-world block changes. */
public final class UtilityEngine {
    private static AnalyzerCore core;
    private static final MinecraftClient MC = MinecraftClient.getInstance();
    private static final Set<UUID> FRIENDS = new HashSet<>();
    private static final Map<String, Long> OPERATIONS = new HashMap<>();
    private static final Map<UUID, String> knownPlayers = new HashMap<>();
    private static final Set<UUID> nearbyPlayers = new HashSet<>();
    private static final ArrayDeque<BlockPos> vein = new ArrayDeque<>();
    private static Object world;
    private static BlockPos mining;
    private static String miningModule;
    private static Block veinBlock;
    private static int ticks, lastPlace, lastTransfer;
    private static boolean middleHeld;
    private static Path friendsFile;
    private UtilityEngine() { }
    public static void register(AnalyzerCore state) {
        core = state;
        friendsFile = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("analyzer/friends.txt");
        try {
            if (Files.exists(friendsFile)) for (String value : Files.readAllLines(friendsFile)) {
                try { FRIENDS.add(UUID.fromString(value)); } catch (IllegalArgumentException ignored) { }
            }
        } catch (Exception ex) { AnalyzerClient.LOGGER.warn("Could not read local friends", ex); }
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
    }
    public static boolean isFriend(UUID uuid) { return FRIENDS.contains(uuid); }
    public static long operations(String id) { return OPERATIONS.getOrDefault(id, 0L); }
    public static String status(String id) {
        if (MC.world == null) return "Join a world to use this module";
        return switch (id) {
            case "vein-miner" -> vein.size() + " connected blocks queued";
            case "middle-click-friend" -> FRIENDS.size() + " saved friends";
            case "player-detection" -> nearbyPlayers.size() + " non-friends nearby";
            case "chest-stealer" -> MC.currentScreen instanceof HandledScreen<?> ? "Transferring from open container" : "Open a container to transfer";
            case "speed-mine" -> "Removes client break delay; server mining time still applies";
            case "no-block-interact" -> "Sneaks during block use to bypass container activation";
            case "notifier" -> "Local chat alerts for player arrivals and departures";
            default -> operations(id) + " interactions this session";
        };
    }
    private static void acted(String id) { OPERATIONS.merge(id, 1L, Long::sum); }
    private static void message(String value) { MC.inGameHud.getChatHud().addMessage(Text.literal("[Analyzer] " + value)); }

    private static void tick() {
        ticks++;
        if (world != MC.world) {
            world = MC.world; vein.clear(); knownPlayers.clear(); nearbyPlayers.clear(); mining = null; miningModule = null;
        }
        if (MC.player == null || MC.world == null || MC.interactionManager == null || !MC.player.isAlive()) { cancelMining(); return; }
        detectPlayers();
        if (core.isEnabled("chest-stealer") && MC.currentScreen instanceof HandledScreen<?> screen
            && MC.player.currentScreenHandler != MC.player.playerScreenHandler && ticks - lastTransfer >= core.getNumber("chest-stealer", "delay")) {
            var handler = screen.getScreenHandler();
            for (var slot : handler.slots) {
                if (slot.inventory == MC.player.getInventory() || !slot.hasStack() || !slot.canTakeItems(MC.player)) continue;
                lastTransfer = ticks;
                MC.interactionManager.clickSlot(handler.syncId, slot.id, 0, SlotActionType.QUICK_MOVE, MC.player);
                acted("chest-stealer"); break;
            }
        }
        if (MC.currentScreen != null || MC.getOverlay() != null || MC.player.isSpectator()) { cancelMining(); return; }
        if (core.isEnabled("coord-snapper")) {
            BlockPos p = MC.player.getBlockPos();
            String text = "X " + p.getX() + " Y " + p.getY() + " Z " + p.getZ();
            if (core.getBoolean("coord-snapper", "nether")) {
                double multiplier = MC.world.getRegistryKey() == net.minecraft.world.World.NETHER ? 8 : 0.125;
                text += " | converted X " + Math.round(p.getX() * multiplier) + " Z " + Math.round(p.getZ() * multiplier);
            }
            MC.keyboard.setClipboard(text); message("Coordinates copied."); acted("coord-snapper");
            core.setEnabled("coord-snapper", false); AnalyzerClient.saveConfig();
        }
        boolean middle = GLFW.glfwGetMouseButton(MC.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == GLFW.GLFW_PRESS;
        if (core.isEnabled("middle-click-friend") && middle && !middleHeld
            && MC.crosshairTarget instanceof EntityHitResult hit && hit.getEntity() instanceof PlayerEntity player && player != MC.player) {
            if (!FRIENDS.add(player.getUuid())) FRIENDS.remove(player.getUuid());
            try {
                Files.createDirectories(friendsFile.getParent());
                Path pending = friendsFile.resolveSibling("friends.txt.pending");
                Files.write(pending, FRIENDS.stream().map(UUID::toString).sorted().toList());
                Files.move(pending, friendsFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception ex) { AnalyzerClient.LOGGER.warn("Could not save local friends", ex); }
            message(player.getName().getString() + (isFriend(player.getUuid()) ? " added as a friend." : " removed from friends.")); acted("middle-click-friend");
        }
        middleHeld = middle;
        if (core.isEnabled("speed-mine")) ((InteractionCooldownAccessor) MC.interactionManager).analyzer$setBreakCooldown(0);
        if (core.isEnabled("tower") && MC.options.jumpKey.isPressed()) {
            if (MC.player.isOnGround()) MC.player.setVelocity(MC.player.getVelocity().x, core.getNumber("tower", "height"), MC.player.getVelocity().z);
            if (ticks - lastPlace >= 3 && place(MC.player.getBlockPos().down(), "tower", false)) lastPlace = ticks;
        } else if (core.isEnabled("scaffold") && ticks - lastPlace >= core.getNumber("scaffold", "delay")) {
            BlockPos below = BlockPos.ofFloored(MC.player.getX(), MC.player.getY() - 0.6, MC.player.getZ());
            if (place(below, "scaffold", false)) lastPlace = ticks;
        }
        if (core.isEnabled("air-place") && MC.options.useKey.isPressed() && MC.crosshairTarget != null
            && MC.crosshairTarget.getType() == HitResult.Type.MISS && ticks - lastPlace >= 4) {
            BlockPos target = BlockPos.ofFloored(MC.player.getEyePos().add(MC.player.getRotationVec(1).multiply(core.getNumber("air-place", "distance"))));
            if (place(target, "air-place", true)) lastPlace = ticks;
        }
        tickMining();
    }

    private static void detectPlayers() {
        Map<UUID, String> current = new HashMap<>();
        Set<UUID> nearby = new HashSet<>();
        double range = core.getNumber("player-detection", "range");
        for (PlayerEntity player : MC.world.getPlayers()) {
            if (player == MC.player) continue;
            current.put(player.getUuid(), player.getName().getString());
            if (core.isEnabled("notifier") && !knownPlayers.containsKey(player.getUuid())) { message(player.getName().getString() + " entered the loaded world."); acted("notifier"); }
            if (!isFriend(player.getUuid()) && MC.player.squaredDistanceTo(player) <= range * range) {
                nearby.add(player.getUuid());
                if (core.isEnabled("player-detection") && !nearbyPlayers.contains(player.getUuid())) {
                    message("Nearby player: " + player.getName().getString() + " · " + Math.round(MC.player.distanceTo(player)) + "m"); acted("player-detection");
                }
            }
        }
        if (core.isEnabled("notifier")) knownPlayers.forEach((id, name) -> { if (!current.containsKey(id)) { message(name + " left the loaded world."); acted("notifier"); } });
        knownPlayers.clear(); knownPlayers.putAll(current); nearbyPlayers.clear(); nearbyPlayers.addAll(nearby);
    }

    /** Called only after an actual successful break, from a narrow interaction mixin. */
    public static void onBroken(BlockPos origin, BlockState before) {
        if (core == null || !core.isEnabled("vein-miner") || MC.world == null || miningModule != null) return;
        vein.clear(); veinBlock = before.getBlock();
        int limit = (int) core.getNumber("vein-miner", "limit");
        Set<BlockPos> seen = new HashSet<>(); ArrayDeque<BlockPos> pending = new ArrayDeque<>();
        pending.add(origin); seen.add(origin);
        while (!pending.isEmpty() && vein.size() < limit) {
            BlockPos p = pending.removeFirst();
            for (Direction direction : Direction.values()) {
                BlockPos next = p.offset(direction).toImmutable();
                if (!seen.add(next) || !reachable(next, core.getNumber("vein-miner", "range"))) continue;
                if (MC.world.getBlockState(next).isOf(veinBlock)) { vein.add(next); pending.add(next); }
                if (vein.size() >= limit) break;
            }
        }
    }

    private static void tickMining() {
        // Manual mining owns the interaction manager now; vanilla switches/aborts its previous target.
        if (MC.options.attackKey.isPressed()) { mining = null; miningModule = null; return; }
        if (MC.player.isUsingItem() || MC.options.useKey.isPressed()) { cancelMining(); return; }
        if (mining != null) {
            if (!ownsValidMining(mining)) cancelMining();
            else { MC.interactionManager.updateBlockBreakingProgress(mining, Direction.UP); MC.player.swingHand(Hand.MAIN_HAND); return; }
        }
        if (core.isEnabled("vein-miner")) while (!vein.isEmpty()) {
            BlockPos p = vein.removeFirst();
            if (MC.world.getBlockState(p).isOf(veinBlock) && reachable(p, core.getNumber("vein-miner", "range"))) { mine(p, "vein-miner"); return; }
        }
        if (core.isEnabled("auto-tunnel")) {
            Vec3d facing = MC.player.getRotationVec(1); BlockPos forward = MC.player.getBlockPos().add((int) Math.round(facing.x * 1.8), 0, (int) Math.round(facing.z * 1.8));
            for (int y = 1; y >= 0; y--) { BlockPos p = forward.up(y); if (mineable(p) && reachable(p, core.getNumber("auto-tunnel", "range"))) { mine(p, "auto-tunnel"); return; } }
        }
        for (String id : new String[]{"auto-farm", "bed-breaker", "flatten", "nuker"}) {
            if (!core.isEnabled(id)) continue;
            double range = core.getNumber(id, "range"); BlockPos center = MC.player.getBlockPos();
            BlockPos best = null; double closest = Double.MAX_VALUE;
            for (BlockPos p : BlockPos.iterate(center.add(-4, -3, -4), center.add(4, 3, 4))) {
                if (!reachable(p, range)) continue;
                BlockState state = MC.world.getBlockState(p);
                if (id.equals("auto-farm") && state.isOf(Blocks.FARMLAND) && MC.world.getBlockState(p.up()).isAir() && ticks - lastPlace >= 4) {
                    int seed = hotbarSeeds();
                    if (seed >= 0 && placeWithSlot(p.up(), "auto-farm", seed, false)) { lastPlace = ticks; return; }
                }
                if (!mineable(p)) continue;
                boolean qualifies = switch (id) {
                    case "auto-farm" -> state.getBlock() instanceof CropBlock crop && crop.isMature(state);
                    case "bed-breaker" -> state.getBlock() instanceof BedBlock;
                    case "flatten" -> p.getY() > center.getY();
                    default -> true;
                };
                double distance = p.getSquaredDistance(MC.player.getPos());
                if (qualifies && distance < closest) { best = p.toImmutable(); closest = distance; }
            }
            if (best != null) { mine(best, id); return; }
        }
    }
    private static void mine(BlockPos p, String id) {
        BlockState state = MC.world.getBlockState(p);
        if (!MC.player.getMainHandStack().getItem().canMine(state, MC.world, p, MC.player)) return;
        mining = p.toImmutable(); miningModule = id;
        if (MC.interactionManager.attackBlock(p, Direction.UP)) { acted(id); MC.player.swingHand(Hand.MAIN_HAND); }
        else cancelMining();
    }
    private static void cancelMining() {
        boolean owned = mining != null;
        mining = null; miningModule = null;
        if (owned && MC.interactionManager != null && MC.player != null && MC.world != null)
            MC.interactionManager.cancelBlockBreaking();
    }
    /** Preserve only this utility's exact active target against vanilla's idle mouse-release cancellation. */
    public static boolean preservesIdleMining(BlockPos activeTarget) {
        return core != null && MC.player != null && MC.world != null && world == MC.world && MC.player.isAlive()
            && MC.currentScreen == null && MC.getOverlay() == null && !MC.player.isSpectator()
            && !MC.options.attackKey.isPressed() && !MC.options.useKey.isPressed() && !MC.player.isUsingItem()
            && ownsValidMining(activeTarget);
    }
    private static boolean ownsValidMining(BlockPos activeTarget) {
        return mining != null && mining.equals(activeTarget) && miningModule != null && core.isEnabled(miningModule)
            && reachable(mining, core.getNumber(miningModule, "range")) && mineable(mining);
    }
    private static boolean mineable(BlockPos p) {
        BlockState state = MC.world.getBlockState(p);
        return !state.isAir() && state.getFluidState().isEmpty() && state.getHardness(MC.world, p) >= 0
            && MC.player.getMainHandStack().getItem().canMine(state, MC.world, p, MC.player);
    }
    private static boolean reachable(BlockPos p, double range) { return Vec3d.ofCenter(p).squaredDistanceTo(MC.player.getEyePos()) <= range * range; }
    private static int hotbarSeeds() {
        for (int i = 0; i < 9; i++) { var item = MC.player.getInventory().getStack(i).getItem(); if (item == Items.WHEAT_SEEDS || item == Items.CARROT || item == Items.POTATO || item == Items.BEETROOT_SEEDS) return i; }
        return -1;
    }
    private static boolean place(BlockPos p, String id, boolean air) {
        int slot = -1;
        for (int i = 0; i < 9; i++) { ItemStack stack = MC.player.getInventory().getStack(i); if (!stack.isEmpty() && stack.getItem() instanceof BlockItem && ((BlockItem) stack.getItem()).getBlock().getDefaultState().isOpaque()) { slot = i; break; } }
        return slot >= 0 && placeWithSlot(p, id, slot, air);
    }
    private static boolean placeWithSlot(BlockPos p, String id, int slot, boolean air) {
        if (!MC.world.getBlockState(p).isReplaceable() || !reachable(p, 4.5)) return false;
        if (new net.minecraft.util.math.Box(p).intersects(MC.player.getBoundingBox())) return false;
        BlockPos neighbor = null; Direction face = null;
        for (Direction d : Direction.values()) { BlockPos candidate = p.offset(d); if (!MC.world.getBlockState(candidate).isAir() && !MC.world.getBlockState(candidate).isReplaceable()) { neighbor = candidate; face = d.getOpposite(); break; } }
        if (neighbor == null && !air) return false;
        if (neighbor == null) { neighbor = p; face = Direction.UP; }
        Vec3d hit = Vec3d.ofCenter(neighbor).add(Vec3d.of(face.getVector()).multiply(0.5));
        int saved = MC.player.getInventory().selectedSlot;
        MC.player.getInventory().selectedSlot = slot;
        try {
            var result = MC.interactionManager.interactBlock(MC.player, Hand.MAIN_HAND, new BlockHitResult(hit, face, neighbor, false));
            if (result.isAccepted()) { MC.player.swingHand(Hand.MAIN_HAND); acted(id); return true; }
            return false;
        } finally { MC.player.getInventory().selectedSlot = saved; }
    }
}
