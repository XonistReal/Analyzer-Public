package dev.analyzer.automation;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.FallingBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.*;

/** Original loaded-terrain miner. Requests movement goals and breaks only through real vanilla interactions. */
public final class NativeMiner {
    public record Result(boolean done, boolean blocked, String detail, double progress) { }
    private static final MinecraftClient MC = MinecraftClient.getInstance();
    private static final int RADIUS = 24, VERTICAL = 20, SCAN_BUDGET = 4096;
    private final Set<Block> targets = new HashSet<>();
    private final Set<Item> drops = new HashSet<>();
    private final Set<BlockPos> rejected = new HashSet<>();
    private final List<BlockPos> candidates = new ArrayList<>();
    private BlockPos scanOrigin, ore, goal, breaking, tunnelStep, pickup, aimedBlock, occluder;
    private Object world;
    private int quantity, scanIndex, minY, maxY, selected = -1, cooldown, breakingTicks, pickupTicks, preferredY, actionDelay = 3;
    private int previousCount, directionOffset;
    private int lastOwnedSlot = -1;
    private int gravelRetries, placeDelay = 4;
    private int maxGravelRetries = 128;
    private int aimReadyAge = -1;
    private Direction heading = Direction.NORTH;
    private boolean scanning, active, branchMining, paused;
    private boolean flintGoal;
    private boolean awaitingGravel;
    private int gravelPlacementWait;
    private AutomationNavigator.MotionProfile motion = AutomationNavigator.MotionProfile.defaults();
    private Box miningExclusion;
    private String error = "";
    /** Protects the selected blueprint volume while its supplies are gathered. */
    public void setMiningExclusion(Box bounds) {
        miningExclusion = bounds;
        if (ore != null && excluded(ore) || tunnelStep != null && excluded(tunnelStep) || goal != null && excluded(goal)) {
            cancelBreaking(); ore = null; tunnelStep = null; goal = null; startScan();
        }
    }
    public static boolean requiresOrdinaryDrops(String[] blockIds) {
        if (blockIds == null) return false;
        for (String name : blockIds) {
            Identifier id = Identifier.tryParse(name);
            if (id != null && Registries.BLOCK.containsId(id)) {
                Block block = Registries.BLOCK.get(id);
                if (dropFor(block) != block.asItem()) return true;
            }
        }
        return false;
    }
    public boolean begin(int quantity, String[] blockIds) {
        stop(); error = "";
        if (MC.player == null || MC.world == null || MC.interactionManager == null) return fail("Join a world to mine.");
        if (quantity < 1 || quantity > 4096 || blockIds == null || blockIds.length < 1 || blockIds.length > 32) return fail("Choose 1 to 4096 items and valid mining blocks.");
        for (String name : blockIds) {
            Identifier id = name == null ? null : Identifier.tryParse(name);
            if (id == null || !id.getNamespace().equals("minecraft") || !Registries.BLOCK.containsId(id)) return fail("Unknown vanilla mining block: " + name);
            Block block = Registries.BLOCK.get(id);
            if (block.getDefaultState().isAir() || block.getDefaultState().hasBlockEntity() || block.getDefaultState().getHardness(MC.world, MC.player.getBlockPos()) < 0) return fail("This block cannot be collected by the native miner.");
            targets.add(block); drops.add(dropFor(block));
        }
        this.quantity = quantity; world = MC.world; selected = MC.player.getInventory().selectedSlot;
        previousCount = inventoryCount(); active = true; heading = MC.player.getHorizontalFacing();
        branchMining = targets.stream().anyMatch(block -> Registries.BLOCK.getId(block).getPath().endsWith("_ore") || block == Blocks.ANCIENT_DEBRIS
                || block == Blocks.STONE || block == Blocks.COBBLESTONE || block == Blocks.DEEPSLATE || block == Blocks.COBBLED_DEEPSLATE);
        preferredY = preferredHeight(); startScan(); return true;
    }
    public void actionDelayTicks(int ticks) { actionDelay = Math.clamp(ticks, 1, 100); }
    public void configureMotion(AutomationNavigator.MotionProfile profile) { motion = Objects.requireNonNull(profile); actionDelayTicks(profile.breakDelayTicks()); placeDelay = profile.placeDelayTicks(); }
    /** Item goals distinguish random gravel -> flint drops from collecting ordinary gravel. */
    public boolean begin(int quantity, String[] blockIds, Item expectedItem) {
        if (!begin(quantity, blockIds)) return false;
        if (expectedItem == null || expectedItem == Items.AIR || targets.stream().anyMatch(block -> dropFor(block) != expectedItem && !(block == Blocks.GRAVEL && expectedItem == Items.FLINT))) {
            stop(); return fail("The requested item is not a supported drop of these mining blocks.");
        }
        drops.clear(); drops.add(expectedItem); flintGoal = expectedItem == Items.FLINT; previousCount = inventoryCount();
        maxGravelRetries = Math.clamp((quantity - previousCount) * 32, 128, 4096);
        return true;
    }
    public BlockPos targetGoal() { return goal; }
    public String error() { return error; }
    public void pause() { paused = true; cancelBreaking(); goal = null; aimedBlock = null; aimReadyAge = -1; }
    public void resume() { paused = false; cooldown = Math.max(cooldown, actionDelay); aimedBlock = null; aimReadyAge = -1; }
    public int inventoryCount() { return InventoryAutomation.count(stack -> drops.contains(stack.getItem())); }
    public Result tick() {
        if (!active) return result(false, true, error.isEmpty() ? "No active mining goal" : error);
        if (paused) return result(false, false, "Mining paused; ownership and resource target are preserved");
        if (MC.player == null || MC.world != world || MC.interactionManager == null || !MC.player.isAlive()) return result(false, true, "The world changed or the player cannot mine.");
        int count = inventoryCount();
        if (count >= quantity) { cancelBreaking(); goal = null; return result(true, false, "Inventory verified: " + count + "/" + quantity + " requested items"); }
        if (!(MC.player.currentScreenHandler instanceof PlayerScreenHandler)) { cancelBreaking(); return result(false, true, "Close the workstation before mining."); }
        if (MC.currentScreen != null) { cancelBreaking(); aimedBlock = null; aimReadyAge = -1; return result(false, false, "Waiting for the menu to close"); }
        boolean receivedItems = count > previousCount;
        if (receivedItems) {
            previousCount = count; rejected.clear();
            if (pickup != null) { pickup = null; pickupTicks = 0; retarget(); }
        }
        if (cooldown > 0) { cooldown--; return result(false, false, "Waiting between mining actions"); }
        if (awaitingGravel) {
            if (ore != null && MC.world.getBlockState(ore).isOf(Blocks.GRAVEL)) { awaitingGravel = false; gravelPlacementWait = 0; }
            else if (++gravelPlacementWait > 40) return result(false, true, "The server did not accept the owned gravel placement. Check area protection before resuming.");
            else return result(false, false, "Waiting for the server's gravel block before retrying the flint drop");
        }
        if (pickup != null) {
            ItemEntity item = nearestDrop();
            if (item != null) {
                BlockPos at = item.getBlockPos();
                if (walkable(at)) goal = at;
                else if (walkable(at.up())) goal = at.up();
            }
            if (++pickupTicks < 30) return result(false, false, "Collecting the actual mined drops · " + count + "/" + quantity);
            pickup = null; pickupTicks = 0; retarget();
        }
        if (freeSlots() < 2) { cancelBreaking(); return result(false, true, "Free two inventory slots before collecting more mined items."); }
        if (ore != null) {
            if (!targets.contains(MC.world.getBlockState(ore).getBlock())) {
                cancelBreaking();
                if (receivedItems) retarget();
                else {
                    pickup = ore; pickupTicks = 0; if (walkable(ore)) goal = ore;
                    return result(false, false, "Waiting for the server's item drops");
                }
            }
            if (ore != null && occluder != null) {
                if (!isTreeObstruction(MC.world.getBlockState(occluder).getBlock())) { occluder = null; cancelBreaking(); }
                else return breakBlock(occluder, false);
            }
            if (ore != null) {
                BlockPos stand = standingPosition(ore);
                if (stand == null) { rejected.add(ore); retarget(); }
                else {
                    goal = stand;
                    if (inReach(ore.toCenterPos()))
                        return breakBlock(ore, true);
                    return result(false, false, "Walking to loaded " + MC.world.getBlockState(ore).getBlock().getName().getString());
                }
            }
        }
        if (tunnelStep != null) return tunnel();
        if (scanning) {
            scan();
            if (scanning) return result(false, false, "Searching loaded terrain for mining targets · " + (100L * scanIndex / scanTotal()) + "%");
        }
        ore = chooseOre();
        if (ore != null) return result(false, false, "Found a freshly checked loaded ore or resource");
        if (branchMining && wrongNaturalDimension()) return result(false, true, "No loaded requested vein was reachable here, and these ores do not naturally generate in this dimension. Use the reviewed portal stage instead of digging indefinitely.");
        if (flintGoal && InventoryAutomation.count(Items.GRAVEL) > 0) return recycleGravel();
        if (!branchMining) return result(false, true, "No reachable requested blocks were found in loaded terrain within " + RADIUS + " blocks. Move to a source area and resume.");
        return beginTunnel();
    }
    private void retarget() {
        cancelBreaking(); ore = null; goal = null; occluder = null; aimedBlock = null; aimReadyAge = -1;
        int before = candidates.size();
        candidates.removeIf(position -> !currentTarget(position));
        // The full survey observes buried blocks too. Empty surveyed terrain can
        // remain cached while a short branch advances; a depleted vein or eight
        // blocks of movement refreshes that bounded observation window.
        if (scanOrigin == null || scanOrigin.getSquaredDistance(MC.player.getBlockPos()) >= 64 || before > 0 && candidates.isEmpty()) startScan();
    }
    private void startScan() {
        if (MC.player == null || MC.world == null) return;
        scanOrigin = MC.player.getBlockPos(); scanIndex = 0; candidates.clear(); scanning = true;
        minY = Math.max(MC.world.getBottomY(), scanOrigin.getY() - VERTICAL);
        maxY = Math.min(MC.world.getTopYInclusive(), scanOrigin.getY() + VERTICAL);
    }
    private int scanTotal() { return (RADIUS * 2 + 1) * (RADIUS * 2 + 1) * Math.max(1, maxY - minY + 1); }
    private void scan() {
        int width = RADIUS * 2 + 1, layers = Math.max(1, maxY - minY + 1);
        int limit = Math.min(scanTotal(), scanIndex + SCAN_BUDGET);
        var position = new BlockPos.Mutable();
        for (; scanIndex < limit; scanIndex++) {
            int index = scanIndex, y = minY + index % layers; index /= layers;
            position.set(scanOrigin.getX() - RADIUS + index % width, y, scanOrigin.getZ() - RADIUS + index / width);
            if (!MC.world.isChunkLoaded(position) || excluded(position) || !targets.contains(MC.world.getBlockState(position).getBlock()) || rejected.contains(position)) continue;
            // Keep only the nearest bounded candidates; underground veins can contain thousands of blocks.
            if (candidates.size() < 64) candidates.add(position.toImmutable());
            else {
                int farthest = 0; double farthestDistance = -1;
                for (int i = 0; i < candidates.size(); i++) { double distance = candidates.get(i).getSquaredDistance(scanOrigin); if (distance > farthestDistance) { farthest = i; farthestDistance = distance; } }
                if (position.getSquaredDistance(scanOrigin) < farthestDistance) candidates.set(farthest, position.toImmutable());
            }
        }
        scanning = scanIndex < scanTotal();
    }
    private BlockPos chooseOre() {
        return candidates.stream().filter(position -> currentTarget(position) && !rejected.contains(position) && safeToBreak(position) && standingPosition(position) != null)
                .min(Comparator.comparingDouble(position -> position.getSquaredDistance(MC.player.getBlockPos()))).orElse(null);
    }
    private boolean currentTarget(BlockPos position) {
        return MC.world.isChunkLoaded(position) && !excluded(position) && targets.contains(MC.world.getBlockState(position).getBlock());
    }
    private BlockPos standingPosition(BlockPos block) {
        if (inReach(block.toCenterPos()) && walkable(MC.player.getBlockPos()) && visibleResource(MC.player.getEyePos(), block)) return MC.player.getBlockPos();
        BlockPos best = null; double distance = Double.MAX_VALUE;
        for (Direction direction : Direction.Type.HORIZONTAL) for (int dy = -1; dy <= 1; dy++) {
            BlockPos position = block.offset(direction).add(0, dy, 0);
            if (!walkable(position) || !visibleResource(Vec3d.ofBottomCenter(position).add(0, MC.player.getStandingEyeHeight(), 0), block)) continue;
            double d = position.getSquaredDistance(MC.player.getBlockPos()); if (d < distance) { best = position; distance = d; }
        }
        return best;
    }
    private boolean visibleResource(Vec3d eye, BlockPos block) {
        var hit = MC.world.raycast(new RaycastContext(eye, block.toCenterPos(), RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, MC.player));
        if (hit.getBlockPos().equals(block)) return true;
        String id = Registries.BLOCK.getId(MC.world.getBlockState(block).getBlock()).getPath();
        return (id.endsWith("_log") || id.endsWith("_stem")) && isTreeObstruction(MC.world.getBlockState(hit.getBlockPos()).getBlock());
    }
    private Result beginTunnel() {
        BlockPos player = MC.player.getBlockPos();
        for (int offset = 0; offset < 4; offset++) {
            Direction direction = rotate(heading, directionOffset + offset);
            int dy = player.getY() > preferredY ? -1 : player.getY() < preferredY ? 1 : 0;
            BlockPos next = player.offset(direction).add(0, dy, 0);
            if (next.getY() <= MC.world.getBottomY() + 1 || next.getY() >= MC.world.getTopYInclusive() - 2) continue;
            if (!solidSafeFloor(next.down()) || !excavatable(next) || !excavatable(next.up())) continue;
            tunnelStep = next; directionOffset += offset; goal = null; return tunnel();
        }
        cancelBreaking(); return result(false, true, "No dry, supported staircase or branch-mining step is safe. Fluids, loose ceilings, protected blocks or a void stopped excavation.");
    }
    private Result tunnel() {
        if (MC.player.getPos().squaredDistanceTo(Vec3d.ofBottomCenter(tunnelStep)) < 0.45) {
            tunnelStep = null; retarget();
            return result(false, false, "Staircase or branch step reached; checking loaded ores again");
        }
        if (!solidSafeFloor(tunnelStep.down())) return result(false, true, "The next mining step lost its solid floor. Movement stopped.");
        if (tunnelStep.getY() > MC.player.getBlockY()) {
            BlockPos headroom = MC.player.getBlockPos().up(2);
            if (!MC.world.getBlockState(headroom).isReplaceable()) {
                if (!excavatable(headroom)) return result(false, true, "The upward staircase needs clear natural headroom above the player; this ceiling was not removed.");
                return breakBlock(headroom, false);
            }
        }
        if (!MC.world.getBlockState(tunnelStep.up()).isReplaceable()) return breakBlock(tunnelStep.up(), false);
        if (!MC.world.getBlockState(tunnelStep).isReplaceable()) return breakBlock(tunnelStep, false);
        if (!walkable(tunnelStep)) return result(false, true, "The excavated route is blocked or hazardous.");
        cancelBreaking(); goal = tunnelStep;
        return result(false, false, "Walking along the checked mining staircase toward Y " + preferredY);
    }
    private Result breakBlock(BlockPos position, boolean requestedBlock) {
        goal = null;
        if (!safeToBreak(position)) { cancelBreaking(); return result(false, true, "Breaking here could release fluid, a falling ceiling or an unsafe floor. Mining stopped."); }
        int tier = minimumTier(MC.world.getBlockState(position).getBlock());
        int tool = findTool(tier, requestedBlock, MC.world.getBlockState(position).getBlock());
        if (tool < 0 && tier > 0) { cancelBreaking(); return result(false, true, "Need a durable " + tierName(tier) + " pickaxe without Silk Touch for the requested drops."); }
        if (tool >= 9) {
            if (!MC.player.currentScreenHandler.getCursorStack().isEmpty()) return result(false, true, "Return the cursor item before selecting a mining tool.");
            MC.interactionManager.clickSlot(MC.player.currentScreenHandler.syncId, tool, 8, SlotActionType.SWAP, MC.player);
            cooldown = 8; return result(false, false, "Moving the verified mining tool onto the hotbar");
        }
        if (tool >= 0) { MC.player.getInventory().selectedSlot = tool; lastOwnedSlot = tool; }
        else for (int slot = 0; slot < 9; slot++) if (MC.player.getInventory().getStack(slot).isEmpty()) { MC.player.getInventory().selectedSlot = slot; lastOwnedSlot = slot; break; }
        if (!AutomationMotion.align(position.toCenterPos(), motion)) { cancelBreaking(); aimedBlock = null; aimReadyAge = -1; return result(false, false, "Turning toward the mining block"); }
        if (!position.equals(aimedBlock)) { aimedBlock = position.toImmutable(); aimReadyAge = MC.player.age; return result(false, false, "Waiting for normal camera rotation to reach the server"); }
        if (MC.player.age <= aimReadyAge) return result(false, false, "Waiting for the next player tick before mining");
        var hit = MC.player.raycast(MC.player.getBlockInteractionRange(), 1, false);
        if (!(hit instanceof BlockHitResult blockHit) || !blockHit.getBlockPos().equals(position)) {
            cancelBreaking();
            if (requestedBlock && hit instanceof BlockHitResult obstacle && isTreeObstruction(MC.world.getBlockState(obstacle.getBlockPos()).getBlock())
                    && Registries.BLOCK.getId(MC.world.getBlockState(position).getBlock()).getPath().matches(".*_(?:log|stem)") && safeToBreak(obstacle.getBlockPos())) {
                occluder = obstacle.getBlockPos().toImmutable(); goal = null;
                return result(false, false, "Clearing a reachable leaf or vine before gathering the trunk");
            }
            if (requestedBlock) { rejected.add(position); retarget(); return result(false, false, "The ore is occluded; selecting a freshly checked vein or tunnel"); }
            return result(false, true, "The next excavation block is outside the real crosshair's reachable line of sight.");
        }
        if (!position.equals(breaking)) {
            cancelBreaking(); breaking = position.toImmutable(); breakingTicks = 0;
            if (!MC.interactionManager.attackBlock(position, blockHit.getSide())) return result(false, true, "The server did not accept starting this block break.");
        } else MC.interactionManager.updateBlockBreakingProgress(position, blockHit.getSide());
        MC.player.swingHand(Hand.MAIN_HAND); breakingTicks++;
        if (MC.world.getBlockState(position).isAir()) {
            cancelBreaking(); cooldown = actionDelay;
            if (requestedBlock) { pickup = position; pickupTicks = 0; if (walkable(position)) goal = position; }
            return result(false, false, "Block break observed; waiting for actual server drops");
        }
        if (breakingTicks > 600) { cancelBreaking(); return result(false, true, "This block did not break within 30 seconds. Check durability, area protection and connection."); }
        return result(false, false, "Breaking " + MC.world.getBlockState(position).getBlock().getName().getString() + " with normal server timing");
    }
    private boolean excavatable(BlockPos position) {
        if (!MC.world.isChunkLoaded(position) || excluded(position) || danger(position)) return false;
        var state = MC.world.getBlockState(position);
        if (state.isReplaceable()) return true;
        Block block = state.getBlock(); String id = Registries.BLOCK.getId(block).getPath();
        return !state.hasBlockEntity() && !(block instanceof FallingBlock) && state.getHardness(MC.world, position) >= 0
                && (id.equals("stone") || id.equals("deepslate") || id.equals("netherrack") || id.equals("dirt") || id.equals("grass_block")
                || id.equals("coarse_dirt") || id.equals("rooted_dirt") || id.equals("podzol") || id.equals("mycelium")
                || id.equals("granite") || id.equals("diorite") || id.equals("andesite") || id.equals("tuff")
                || id.endsWith("_ore") || block == Blocks.ANCIENT_DEBRIS);
    }
    private boolean safeToBreak(BlockPos position) {
        if (!MC.world.isChunkLoaded(position) || excluded(position) || danger(position) || MC.world.getBlockState(position).hasBlockEntity()) return false;
        if (position.equals(MC.player.getBlockPos().down())) return false;
        for (Direction direction : Direction.values()) if (danger(position.offset(direction))) return false;
        return !(MC.world.getBlockState(position.up()).getBlock() instanceof FallingBlock)
                && MC.world.getBlockState(position).getHardness(MC.world, position) >= 0;
    }
    private boolean danger(BlockPos position) {
        if (!MC.world.isChunkLoaded(position)) return true;
        var state = MC.world.getBlockState(position);
        return !state.getFluidState().isEmpty() || state.isOf(Blocks.FIRE) || state.isOf(Blocks.SOUL_FIRE) || state.isOf(Blocks.MAGMA_BLOCK)
                || state.isOf(Blocks.CAMPFIRE) || state.isOf(Blocks.SOUL_CAMPFIRE) || state.isOf(Blocks.CACTUS) || state.isOf(Blocks.SWEET_BERRY_BUSH);
    }
    private boolean solidSafeFloor(BlockPos position) {
        return !danger(position) && MC.world.getBlockState(position).isSideSolidFullSquare(MC.world, position, Direction.UP);
    }
    private boolean walkable(BlockPos position) {
        return !excluded(position) && !excluded(position.up()) && solidSafeFloor(position.down()) && !danger(position) && !danger(position.up())
                && MC.world.getBlockState(position).getCollisionShape(MC.world, position).isEmpty()
                && MC.world.getBlockState(position.up()).getCollisionShape(MC.world, position.up()).isEmpty();
    }
    private boolean excluded(BlockPos position) { return miningExclusion != null && miningExclusion.intersects(new Box(position)); }
    private boolean inReach(Vec3d position) {
        double range = Math.max(0.1, MC.player.getBlockInteractionRange() - 0.15);
        return MC.player.getEyePos().squaredDistanceTo(position) <= range * range;
    }
    private int findTool(int tier, boolean requestedBlock, Block block) {
        int best = -1, bestTier = -1;
        boolean actualDrops = requestedBlock && ore != null && dropFor(MC.world.getBlockState(ore).getBlock()) != MC.world.getBlockState(ore).getBlock().asItem();
        if (tier == 0) {
            String blockId = Registries.BLOCK.getId(block).getPath();
            String suffix = blockId.endsWith("_log") || blockId.endsWith("_stem") ? "_axe" : "_shovel";
            for (int slot = 0; slot < 36; slot++) {
                var stack = MC.player.getInventory().getStack(slot);
                if (Registries.ITEM.getId(stack.getItem()).getPath().endsWith(suffix) && stack.getMaxDamage() - stack.getDamage() >= 16 && (!(flintGoal || actualDrops) || InventoryAutomation.level(stack, "silk_touch") == 0)) return slot;
            }
            // Select an actual bare hand, moving an empty inventory slot to the
            // hotbar when necessary. Keeping the old Silk tool selected would
            // still produce the wrong drop when every hotbar slot is occupied.
            for (int slot = 0; slot < 36; slot++) if (MC.player.getInventory().getStack(slot).isEmpty()) return slot;
            for (int slot = 0; slot < 36; slot++) {
                ItemStack stack = MC.player.getInventory().getStack(slot);
                if (!stack.isDamageable() && InventoryAutomation.level(stack,"silk_touch") == 0) return slot;
            }
            return -1;
        }
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = MC.player.getInventory().getStack(slot);
            int candidateTier = toolTier(stack.getItem());
            if (candidateTier < tier || candidateTier == 0 || stack.getMaxDamage() - stack.getDamage() < 16
                    || actualDrops && InventoryAutomation.level(stack, "silk_touch") > 0) continue;
            if (candidateTier > bestTier || candidateTier == bestTier && slot < 9 && best >= 9) { best = slot; bestTier = candidateTier; }
        }
        return best;
    }
    private static int toolTier(Item item) {
        if (item == Items.NETHERITE_PICKAXE || item == Items.DIAMOND_PICKAXE) return 4;
        if (item == Items.IRON_PICKAXE) return 3;
        if (item == Items.STONE_PICKAXE) return 2;
        if (item == Items.WOODEN_PICKAXE || item == Items.GOLDEN_PICKAXE) return 1;
        return 0;
    }
    public static int minimumTier(Block block) {
        String id = Registries.BLOCK.getId(block).getPath();
        if (block == Blocks.OBSIDIAN || block == Blocks.ANCIENT_DEBRIS) return 4;
        if (id.contains("diamond_ore") || id.contains("emerald_ore") || id.contains("gold_ore") && !id.equals("nether_gold_ore") || id.contains("redstone_ore")) return 3;
        if (id.contains("iron_ore") || id.contains("copper_ore") || id.contains("lapis_ore")) return 2;
        if (id.endsWith("_log") || id.endsWith("_stem") || id.endsWith("_leaves") || id.contains("vine") || id.equals("gravel") || id.equals("dirt") || id.equals("grass_block") || id.equals("sand") || id.equals("red_sand") || id.equals("clay")) return 0;
        return 1;
    }
    private static String tierName(int tier) { return switch (tier) { case 4 -> "diamond-tier"; case 3 -> "iron-tier"; case 2 -> "stone-tier"; default -> "wooden-tier"; }; }
    private int preferredHeight() {
        if (targets.contains(Blocks.ANCIENT_DEBRIS)) return 15;
        if (targets.contains(Blocks.DEEPSLATE) || targets.contains(Blocks.COBBLED_DEEPSLATE)) return Math.max(MC.world.getBottomY() + 4, Math.min(-8, MC.player.getBlockY() - 6));
        if (targets.contains(Blocks.STONE) || targets.contains(Blocks.COBBLESTONE)) return Math.max(MC.world.getBottomY() + 4, MC.player.getBlockY() - 6);
        for (Block block : targets) {
            String id = Registries.BLOCK.getId(block).getPath();
            if (id.contains("diamond_ore") || id.contains("redstone_ore")) return Math.max(MC.world.getBottomY() + 10, -54);
            if (id.contains("gold_ore")) return Math.max(MC.world.getBottomY() + 10, -16);
            if (id.contains("lapis_ore")) return 0;
            if (id.contains("iron_ore")) return 16;
            if (id.contains("copper_ore")) return 48;
            if (id.contains("coal_ore")) return 48;
        }
        return MC.player.getBlockY();
    }
    private boolean wrongNaturalDimension() {
        String current = MC.world.getRegistryKey().getValue().toString();
        return targets.stream().allMatch(block -> {
            String id = Registries.BLOCK.getId(block).getPath();
            String expected = block == Blocks.ANCIENT_DEBRIS || id.startsWith("nether_") ? "minecraft:the_nether"
                    : id.endsWith("_ore") || block == Blocks.STONE || block == Blocks.COBBLESTONE || block == Blocks.DEEPSLATE || block == Blocks.COBBLED_DEEPSLATE ? "minecraft:overworld" : "";
            return !expected.isEmpty() && !expected.equals(current);
        });
    }
    private static Item dropFor(Block block) {
        String id = Registries.BLOCK.getId(block).getPath();
        if (id.equals("stone")) return Items.COBBLESTONE;
        if (id.equals("deepslate")) return Items.COBBLED_DEEPSLATE;
        if (id.equals("grass_block")) return Items.DIRT;
        if (id.equals("clay")) return Items.CLAY_BALL;
        if (id.contains("iron_ore")) return Items.RAW_IRON;
        if (id.contains("copper_ore")) return Items.RAW_COPPER;
        if (id.equals("nether_gold_ore")) return Items.GOLD_NUGGET;
        if (id.contains("gold_ore")) return Items.RAW_GOLD;
        if (id.contains("diamond_ore")) return Items.DIAMOND;
        if (id.contains("emerald_ore")) return Items.EMERALD;
        if (id.contains("lapis_ore")) return Items.LAPIS_LAZULI;
        if (id.contains("redstone_ore")) return Items.REDSTONE;
        if (id.contains("coal_ore")) return Items.COAL;
        if (id.equals("nether_quartz_ore")) return Items.QUARTZ;
        return block.asItem();
    }
    private ItemEntity nearestDrop() {
        return MC.world.getEntitiesByClass(ItemEntity.class, MC.player.getBoundingBox().expand(8), entity -> drops.contains(entity.getStack().getItem()))
                .stream().min(Comparator.comparingDouble(entity -> entity.squaredDistanceTo(MC.player))).orElse(null);
    }
    private Result recycleGravel() {
        if (gravelRetries >= maxGravelRetries) return result(false, true, "Gravel was retried " + maxGravelRetries + " times without reaching the actual flint goal. Check server loot rules or supply the remaining flint.");
        BlockPos origin = MC.player.getBlockPos(), placing = null;
        for (BlockPos position : BlockPos.iterate(origin.add(-3, -1, -3), origin.add(3, 0, 3))) {
            if (position.getSquaredDistance(origin) < 2 || !walkable(position) || !MC.world.getBlockState(position).isAir()
                    || !MC.world.getOtherEntities(null, new Box(position)).isEmpty()) continue;
            Vec3d face = position.down().toCenterPos().add(0, 0.5, 0);
            if (inReach(face)) { placing = position.toImmutable(); break; }
        }
        if (placing == null) return result(false, true, "Need a reachable clear solid patch to place owned gravel and retry its real flint drop.");
        int slot = -1;
        for (int i = 0; i < 36; i++) if (MC.player.getInventory().getStack(i).isOf(Items.GRAVEL)) { slot = i; break; }
        if (slot < 0) return result(false, true, "No gravel remains to retry for flint.");
        if (slot >= 9) {
            if (!MC.player.currentScreenHandler.getCursorStack().isEmpty()) return result(false, true, "Return the cursor item before selecting gravel.");
            MC.interactionManager.clickSlot(MC.player.currentScreenHandler.syncId, slot, 8, SlotActionType.SWAP, MC.player);
            cooldown = 8; return result(false, false, "Moving owned gravel into the hotbar");
        }
        MC.player.getInventory().selectedSlot = slot; lastOwnedSlot = slot;
        BlockPos support = placing.down(); Vec3d face = support.toCenterPos().add(0, 0.5, 0);
        goal = null;
        if (!AutomationMotion.align(face, motion)) { aimedBlock = null; aimReadyAge = -1; return result(false, false, "Turning toward a safe gravel retry surface"); }
        if (!support.equals(aimedBlock)) { aimedBlock = support; aimReadyAge = MC.player.age; return result(false, false, "Waiting for normal rotation before placing gravel"); }
        if (MC.player.age <= aimReadyAge) return result(false, false, "Waiting for the next player tick before placing gravel");
        var observed = MC.player.raycast(MC.player.getBlockInteractionRange(), 1, false);
        if (!(observed instanceof BlockHitResult hit) || !hit.getBlockPos().equals(support) || hit.getSide() != Direction.UP)
            return result(false, true, "The actual crosshair cannot reach a safe gravel placement face.");
        MC.interactionManager.interactBlock(MC.player, Hand.MAIN_HAND, hit); MC.player.swingHand(Hand.MAIN_HAND);
        ore = placing; pickup = null; goal = null; aimedBlock = null; aimReadyAge = -1; gravelRetries++;
        awaitingGravel = true; gravelPlacementWait = 0;
        cooldown = Math.max(8, placeDelay); return result(false, false, "Placed owned gravel; the next break must produce a real flint item");
    }
    private static boolean isTreeObstruction(Block block) {
        String id = Registries.BLOCK.getId(block).getPath();
        return id.endsWith("_leaves") || id.equals("vine") || id.equals("weeping_vines") || id.equals("weeping_vines_plant") || id.equals("twisting_vines") || id.equals("twisting_vines_plant");
    }
    private static Direction rotate(Direction direction, int times) { for (int i = 0; i < Math.floorMod(times, 4); i++) direction = direction.rotateYClockwise(); return direction; }
    private int freeSlots() { int total = 0; for (int i = 0; i < 36; i++) if (MC.player.getInventory().getStack(i).isEmpty()) total++; return total; }
    private boolean fail(String message) { error = message; targets.clear(); drops.clear(); return false; }
    private Result result(boolean done, boolean blocked, String message) { return new Result(done, blocked, message, Math.clamp(inventoryCount() / (double) Math.max(1, quantity), 0, 1)); }
    private void cancelBreaking() { if (breaking != null && MC.interactionManager != null) MC.interactionManager.cancelBlockBreaking(); breaking = null; breakingTicks = 0; }
    public void stop() {
        cancelBreaking();
        if (selected >= 0 && MC.player != null && MC.world == world && MC.player.getInventory().selectedSlot == lastOwnedSlot) MC.player.getInventory().selectedSlot = selected;
        selected = -1; lastOwnedSlot = -1; active = false; flintGoal = false; awaitingGravel = false; gravelPlacementWait = 0; gravelRetries = 0; targets.clear(); drops.clear(); rejected.clear(); candidates.clear();
        ore = null; goal = null; tunnelStep = null; pickup = null; occluder = null; aimedBlock = null; aimReadyAge = -1; scanning = false; paused = false; cooldown = 0; pickupTicks = 0; directionOffset = 0;
    }
}
