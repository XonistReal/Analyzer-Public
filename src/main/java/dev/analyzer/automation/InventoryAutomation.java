package dev.analyzer.automation;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.*;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import java.util.*;
import java.util.function.Predicate;

/** Normal server-backed slot clicks, recipes and workstation interactions. Never edits inventory/world state. */
public final class InventoryAutomation {
    public record Result(boolean done, boolean blocked, String detail) {
        static Result waitFor(String detail) { return new Result(false, false, detail); }
        static Result need(String detail) { return new Result(false, true, detail); }
        static Result finished(String detail) { return new Result(true, false, detail); }
    }
    private record Recipe(Item output, int width, Item[] cells) { }
    private static final MinecraftClient MC = MinecraftClient.getInstance();
    private static final Item[] ARMOR = {Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS};
    private static final Item[] NETHERITE_ARMOR = {Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS};
    private static final EquipmentSlot[] EQUIPMENT = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private final AutomationNavigator bridge;
    private final WorkstationController workstations;
    private String job = "";
    private Recipe recipe;
    private int cell, source = -1, phase, wait, ownedSync = -1, transactionSync = -1;
    private int swapSource = -1, swapHotbar = -1;
    private long clickCount;
    private boolean ownedScreen;
    private Class<? extends ScreenHandler> expectedHandler;
    private BlockPos station;
    private int observationWait;
    private boolean cursorOwned;
    private int cursorSource=-1,cursorSync=-1;
    private Object cursorWorld;
    private String recovery="";
    private String finishingText="";
    private int buildAmount=ShelterSchematic.MATERIAL_COUNT;
    private int actionDelayTicks = 5;
    private Item materialItem = Items.AIR;
    private int materialTarget;
    private boolean ordinaryMiningDrops;
    private Box placementExclusion;
    public InventoryAutomation(AutomationNavigator bridge) { this.bridge = bridge; this.workstations = new WorkstationController(bridge, this); }
    public long clicks() { return clickCount; }
    public boolean ownsScreen() { return ownedScreen && MC.player != null && MC.player.currentScreenHandler.syncId == ownedSync || ownedScreen && MC.player != null && ownedSync < 0 && expectedHandler != null && expectedHandler.isInstance(MC.player.currentScreenHandler); }
    public String currentJob() { return job; }
    public String recovery() { return recovery; }
    public boolean hasOwnedWorkstations() { return workstations.hasOwnedTables(); }
    public boolean workstationRecoveryActive() { return workstations.recoveryActive(); }
    /** Only explicit running-stage completion invokes recovery; Pause/Stop never start block actions. */
    public Result finishWorkstations() {
        if (MC.player == null || MC.world == null || MC.interactionManager == null) return Result.need("Join the task's world to recover its crafting table.");
        if (wait > 0) { wait--; return Result.waitFor("Waiting for the workstation inventory update"); }
        if (!hasOwnedWorkstations()) return Result.finished("No task-owned crafting table remains");
        if (!MC.player.currentScreenHandler.getCursorStack().isEmpty()) return returnCursor();
        if (ownedScreen || MC.player.currentScreenHandler != MC.player.playerScreenHandler) {
            if (!ownsScreen()) return Result.need("Close your inventory screen before recovering the task-owned crafting table.");
            closeOwned(); resetTransaction(); return Result.waitFor("Closing the crafting grid before recovering the task-owned table");
        }
        return workstations.finish();
    }
    public void pauseActions() { workstations.pauseActions(); }
    /** Recovery alone can require a replacement that yields ordinary ore/stone drops. */
    public void requireOrdinaryMiningDrops(boolean required) { ordinaryMiningDrops = required; }
    /** Keep prerequisite workstations out of the reviewed construction footprint. */
    public void setPlacementExclusion(Box bounds) { placementExclusion = bounds; }
    /** Whether a workstation can be used without first acquiring or crafting its block item. */
    public boolean workstationAvailable(Block block, Item item) {
        if (MC.player == null || MC.world == null || block == null || item == null) return false;
        if (count(item) > 0 || hasNearby(block)) return true;
        if (!ownsScreen()) return false;
        ScreenHandler handler = MC.player.currentScreenHandler;
        return block == Blocks.CRAFTING_TABLE && handler instanceof CraftingScreenHandler
                || block == Blocks.FURNACE && handler instanceof AbstractFurnaceScreenHandler
                || block == Blocks.SMITHING_TABLE && handler instanceof SmithingScreenHandler
                || block == Blocks.ENCHANTING_TABLE && handler instanceof EnchantmentScreenHandler
                || block == Blocks.ANVIL && handler instanceof AnvilScreenHandler;
    }
    public Result prepareBuild(int amount) {buildAmount=Math.clamp(amount,0,ShelterSchematic.MATERIAL_COUNT);return tick("build-hotbar",30); }
    public void actionDelayTicks(int ticks) { actionDelayTicks = Math.clamp(ticks, 1, 100); }
    /** Makes an owned material available without issuing a click before its slot update arrives. */
    public Result prepareItem(Item item, int required) {
        if (MC.player == null || MC.world == null || MC.interactionManager == null) return Result.need("Join a world to continue.");
        if (wait > 0) { wait--; return Result.waitFor("Waiting for the hotbar update"); }
        return prepareHotbar(item, required);
    }
    public Result tickMaterial(Item item, int targetCount) {
        Objects.requireNonNull(item);
        if (targetCount < 1 || targetCount > 4096 || item == Items.AIR) return Result.need("Choose a real building material and a target from 1 to 4096.");
        materialItem = item; materialTarget = targetCount;
        return tick("material:" + Registries.ITEM.getId(item), 30);
    }

    public static int count(Item item) { return count(s -> s.isOf(item)); }
    public static int count(Predicate<ItemStack> test) {
        if (MC.player == null) return 0;
        int total = 0;
        for (int i = 0; i < MC.player.getInventory().size(); i++) {
            ItemStack stack = MC.player.getInventory().getStack(i);
            if (test.test(stack)) total += stack.getCount();
        }
        return total;
    }
    public static int countResource(String name) {
        return switch (name) {
            case "logs" -> count(s -> Registries.ITEM.getId(s.getItem()).getPath().endsWith("_log") || Registries.ITEM.getId(s.getItem()).getPath().endsWith("_stem"));
            case "cobblestone" -> count(Items.COBBLESTONE);
            default -> count(Registries.ITEM.get(Identifier.of("minecraft", name)));
        };
    }
    public static boolean diamondKit() { for (Item item : ARMOR) if (count(item) < 1) return false; return durableTool(Items.DIAMOND_PICKAXE) && count(Items.DIAMOND_SWORD) > 0; }
    public static boolean durableTool(Item item) { return count(stack -> stack.isOf(item) && (!stack.isDamageable() || stack.getMaxDamage() - stack.getDamage() >= 16)) > 0; }
    public static boolean netheriteArmor() { for (Item item : NETHERITE_ARMOR) if (count(item) < 1) return false; return true; }
    public static boolean goodArmor() { for (Item item : ARMOR) { ItemStack s = findItem(item); if (level(s, "protection") < 4 || level(s, "unbreaking") < 3) return false; } return true; }
    private static ItemStack findItem(Item item) { if (MC.player != null) for (int i = 0; i < MC.player.getInventory().size(); i++) if (MC.player.getInventory().getStack(i).isOf(item)) return MC.player.getInventory().getStack(i); if (MC.player != null && (MC.player.currentScreenHandler instanceof EnchantmentScreenHandler || MC.player.currentScreenHandler instanceof AnvilScreenHandler) && MC.player.currentScreenHandler.getSlot(0).getStack().isOf(item)) return MC.player.currentScreenHandler.getSlot(0).getStack(); return ItemStack.EMPTY; }
    public static int level(ItemStack stack, String id) {
        if (stack.isEmpty()) return 0;
        Identifier wanted = Identifier.of("minecraft", id);
        // Registry references belong to their world. A server stack must not be
        // queried with a client registry object, even when both have the same ID.
        for (var entry : EnchantmentHelper.getEnchantments(stack).getEnchantmentEntries())
            if (entry.getKey().matchesId(wanted)) return entry.getIntValue();
        return 0;
    }

    public Result tick(String requested, int enchantLevel) {
        if (MC.player == null || MC.world == null || MC.interactionManager == null) return Result.need("Join a world to continue.");
        if (!job.equals(requested)) { resetTransaction(); job = requested; }
        if (wait > 0) { wait--; return Result.waitFor("Waiting for the server inventory update"); }
        Result placement = workstations.observePlacement(); if (placement != null) return placement;
        if (!finishingText.isEmpty()) return finish(finishingText);
        if (workstations.recoveryActive()) {
            Result cleanup = finishWorkstations(); if (!cleanup.done()) return cleanup;
        }
        if (transactionSync >= 0 && MC.player.currentScreenHandler.syncId != transactionSync) { resetTransaction(); return Result.need("The inventory window changed; resume after closing it."); }
        if (swapSource >= 0) return restoreSwap();
        if (recipe != null) return craftTick();
        return switch (requested) {
            case "bootstrap" -> bootstrap();
            case "stone-tools" -> stoneTools();
            case "iron-tools" -> ironTools();
            case "diamond-kit" -> diamondCraft();
            case "diamond-pickaxe" -> diamondPickaxe();
            case "portal-kit" -> portalKit();
            case "netherite-template" -> count(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE) > 0 || netheriteArmor() ? finish("Owned Netherite Upgrade template verified") : Result.need("Supply one Netherite Upgrade smithing template from a bastion before starting this expedition. Discovery and looting are not automated.");
            case "smelt-gold" -> smeltGoal(Items.RAW_GOLD, Items.GOLD_INGOT, 16);
            case "smelt-extra-iron" -> smeltGoal(Items.RAW_IRON, Items.IRON_INGOT, 1);
            case "smelt-debris" -> smeltGoal(Items.ANCIENT_DEBRIS, Items.NETHERITE_SCRAP, 16);
            case "netherite-ingots" -> netheriteIngots();
            case "netherite-armor" -> smithArmor();
            case "shelter-materials" -> shelterMaterials();
            case "build-hotbar" -> prepareHotbar(Items.COBBLESTONE, buildAmount);
            case "mining-hotbar" -> preparePickaxe();
            case "equip", "diamond-armor-equip" -> equip();
            case "netherite-equip" -> equipArmor(NETHERITE_ARMOR, "netherite");
            case "diamond-armor", "enchanted-armor", "good-armor" -> enchant(enchantLevel);
            default -> requested.startsWith("material:") ? buildingMaterial() : Result.need("Unsupported inventory task: " + requested);
        };
    }
    private Result buildingMaterial() {
        if (count(materialItem) >= materialTarget) {
            Result clearing = clearOwnedFurnace(); if (clearing != null) return clearing;
            return finish("Verified " + materialTarget + " owned " + materialItem.getName().getString());
        }
        return materialCraft(materialItem, materialTarget, new HashSet<>());
    }
    private Result materialCraft(Item item, int target, Set<Item> chain) {
        if (!chain.add(item) || chain.size() > 12) return Result.need("This material dependency could not be resolved safely.");
        String id = Registries.ITEM.getId(item).toString();
        var found = BuildingMaterialCatalog.find(id);
        if (found.isEmpty()) return Result.need("Supply owned " + item.getName().getString() + ". This palette material does not have an automated vanilla recipe.");
        var specification = found.get();
        if (specification.job() == BuildingMaterialCatalog.Job.SUPPLIED)
            return Result.need("Supply the actual " + item.getName().getString() + "; its acquisition is not implemented. Owned items are credited automatically.");
        if (specification.job() == BuildingMaterialCatalog.Job.DIRECT)
            return Result.need("Gather " + Math.max(1, target - count(item)) + " more " + item.getName().getString() + " before resuming material crafting.");
        if (specification.job() == BuildingMaterialCatalog.Job.SMELT) {
            Item source = Registries.ITEM.get(Identifier.of("minecraft", specification.cells().getFirst()));
            if (count(source) == 0 && !(MC.player.currentScreenHandler instanceof AbstractFurnaceScreenHandler && ownsScreen() && MC.player.currentScreenHandler.getSlot(0).getStack().isOf(source)))
                return materialCraft(source, Math.max(1, target - count(item)), chain);
            if (count(Items.FURNACE) == 0 && !hasNearby(Blocks.FURNACE) && !(MC.player.currentScreenHandler instanceof AbstractFurnaceScreenHandler))
                return materialCraft(Items.FURNACE, 1, chain);
            return smelt(source, item, target);
        }
        Result clearing = clearOwnedFurnace(); if (clearing != null) return clearing;
        if (specification.width() == 3 && count(Items.CRAFTING_TABLE) == 0 && !hasNearby(Blocks.CRAFTING_TABLE) && !(MC.player.currentScreenHandler instanceof CraftingScreenHandler))
            return materialCraft(Items.CRAFTING_TABLE, 1, chain);
        for (var ingredient : specification.ingredients()) {
            Item source = Registries.ITEM.get(Identifier.of("minecraft", ingredient.itemId()));
            if (count(source) < ingredient.count()) return materialCraft(source, ingredient.count(), chain);
        }
        Item[] cells = specification.cells().stream().map(value -> value.isEmpty() ? null : Registries.ITEM.get(Identifier.of("minecraft", value))).toArray(Item[]::new);
        return begin(new Recipe(item, specification.width(), cells));
    }

    private Result bootstrap() {
        if (count(Items.CRAFTING_TABLE) == 0 && !hasNearby(Blocks.CRAFTING_TABLE)) {
            if (planks() < 4) return makePlanks();
            return begin(new Recipe(Items.CRAFTING_TABLE, 2, plankIngredients(4)));
        }
        if (hasPick(Items.WOODEN_PICKAXE) || hasPick(Items.STONE_PICKAXE) || hasPick(Items.IRON_PICKAXE) || hasPick(Items.DIAMOND_PICKAXE) || hasPick(Items.NETHERITE_PICKAXE)) return finish("Crafting table and mining tools ready");
        if (count(Items.STICK) < 2) return sticks();
        if (planks() < 3) return makePlanks();
        Item[] heads = plankIngredients(3);
        return begin(new Recipe(Items.WOODEN_PICKAXE, 3, new Item[]{heads[0], heads[1], heads[2], null, Items.STICK, null, null, Items.STICK, null}));
    }
    private Result stoneTools() {
        if (count(Items.FURNACE) == 0 && !hasNearby(Blocks.FURNACE)) {
            if (count(Items.COBBLESTONE) < 8) return Result.need("Need 8 cobblestone for a furnace.");
            return begin(new Recipe(Items.FURNACE, 3, new Item[]{Items.COBBLESTONE, Items.COBBLESTONE, Items.COBBLESTONE, Items.COBBLESTONE, null, Items.COBBLESTONE, Items.COBBLESTONE, Items.COBBLESTONE, Items.COBBLESTONE}));
        }
        if (hasPick(Items.STONE_PICKAXE) || hasPick(Items.IRON_PICKAXE) || hasPick(Items.DIAMOND_PICKAXE) || hasPick(Items.NETHERITE_PICKAXE)) return finish("Stone tools and furnace ready");
        if (count(Items.COBBLESTONE) < 3) return Result.need("Need 3 cobblestone for a stone pickaxe.");
        if (count(Items.STICK) < 2) return sticks();
        return begin(pickRecipe(Items.STONE_PICKAXE, Items.COBBLESTONE));
    }
    private Result ironTools() {
        if (hasPick(Items.IRON_PICKAXE) || hasPick(Items.DIAMOND_PICKAXE) || hasPick(Items.NETHERITE_PICKAXE)) return finish("Iron-tier mining tool ready");
        if (count(Items.IRON_INGOT) < 3) return smeltIron();
        if (MC.player.currentScreenHandler instanceof AbstractFurnaceScreenHandler && ownsScreen()) {
            for (int i=0;i<2;i++) if(MC.player.currentScreenHandler.getSlot(i).hasStack()) {click(i,0,SlotActionType.QUICK_MOVE);return Result.waitFor("Returning unused smelting ingredients");}
            closeOwned(); return Result.waitFor("Closing completed smelting station");
        }
        if (count(Items.STICK) < 2) return sticks();
        return begin(pickRecipe(Items.IRON_PICKAXE, Items.IRON_INGOT));
    }
    private Result diamondCraft() {
        if (diamondKit()) return finish("Diamond armor, pickaxe and sword crafted");
        if (count(Items.STICK) < 4 && (!hasPick(Items.DIAMOND_PICKAXE) || count(Items.DIAMOND_SWORD) == 0)) return sticks();
        if (!hasPick(Items.DIAMOND_PICKAXE)) return diamondRecipe(pickRecipe(Items.DIAMOND_PICKAXE, Items.DIAMOND));
        if (count(Items.DIAMOND_SWORD) == 0) return diamondRecipe(new Recipe(Items.DIAMOND_SWORD, 3, new Item[]{null, Items.DIAMOND, null, null, Items.DIAMOND, null, null, Items.STICK, null}));
        if (count(ARMOR[0]) == 0) return diamondRecipe(new Recipe(ARMOR[0], 3, new Item[]{Items.DIAMOND, Items.DIAMOND, Items.DIAMOND, Items.DIAMOND, null, Items.DIAMOND, null, null, null}));
        if (count(ARMOR[1]) == 0) return diamondRecipe(new Recipe(ARMOR[1], 3, new Item[]{Items.DIAMOND, null, Items.DIAMOND, Items.DIAMOND, Items.DIAMOND, Items.DIAMOND, Items.DIAMOND, Items.DIAMOND, Items.DIAMOND}));
        if (count(ARMOR[2]) == 0) return diamondRecipe(new Recipe(ARMOR[2], 3, new Item[]{Items.DIAMOND, Items.DIAMOND, Items.DIAMOND, Items.DIAMOND, null, Items.DIAMOND, Items.DIAMOND, null, Items.DIAMOND}));
        return diamondRecipe(new Recipe(ARMOR[3], 3, new Item[]{Items.DIAMOND, null, Items.DIAMOND, Items.DIAMOND, null, Items.DIAMOND, null, null, null}));
    }
    private Result diamondRecipe(Recipe value) { long needed = Arrays.stream(value.cells).filter(i -> i == Items.DIAMOND).count(); if (count(Items.DIAMOND) < needed) return Result.need("Need " + needed + " diamonds for " + value.output.getName().getString()); return begin(value); }
    private Result diamondPickaxe() {
        if (hasPick(Items.DIAMOND_PICKAXE) || hasPick(Items.NETHERITE_PICKAXE)) return finish("Diamond-tier mining tool verified");
        if (count(Items.STICK) < 2) return sticks();
        return diamondRecipe(pickRecipe(Items.DIAMOND_PICKAXE, Items.DIAMOND));
    }
    private Result portalKit() {
        if (durableTool(Items.FLINT_AND_STEEL)) return finish("Durable flint and steel ready");
        if (count(Items.FLINT) == 0) return Result.need("Need one flint or flint and steel to ignite a portal. Gravel drops are random; this task will not report a nonexistent igniter.");
        if (count(Items.IRON_INGOT) == 0) {
            if (count(Items.RAW_IRON) == 0) return Result.need("Need one iron ingot or raw iron to craft flint and steel.");
            return smeltGoal(Items.RAW_IRON, Items.IRON_INGOT, 1);
        }
        Result clearing = clearOwnedFurnace(); if (clearing != null) return clearing;
        return begin(new Recipe(Items.FLINT_AND_STEEL, 2, new Item[]{Items.IRON_INGOT, null, null, Items.FLINT}));
    }
    private Result smeltGoal(Item input, Item output, int required) {
        if (count(output) >= required) {
            Result clearing = clearOwnedFurnace(); if (clearing != null) return clearing;
            return finish("Verified " + required + " " + output.getName().getString());
        }
        return smelt(input, output, required);
    }
    private Result clearOwnedFurnace() {
        if (MC.player.currentScreenHandler instanceof AbstractFurnaceScreenHandler && ownsScreen()) {
            if (freeSlots() < 1) return Result.need("Free an inventory slot to recover unused smelting ingredients.");
            for (int i = 0; i < 3; i++) if (MC.player.currentScreenHandler.getSlot(i).hasStack()) {
                click(i, 0, SlotActionType.QUICK_MOVE);
                return Result.waitFor("Returning unused smelting ingredients and output");
            }
            closeOwned(); return Result.waitFor("Closing the completed furnace");
        }
        return null;
    }
    private Result netheriteIngots() {
        if (netheriteArmor() || count(Items.NETHERITE_INGOT) >= 4) return finish("Four netherite armor upgrade ingots ready");
        if (count(Items.NETHERITE_SCRAP) < 4 || count(Items.GOLD_INGOT) < 4) return Result.need("Each netherite ingot needs four netherite scrap and four gold ingots; smelt the reviewed resource goals first.");
        return begin(new Recipe(Items.NETHERITE_INGOT, 3, new Item[]{Items.NETHERITE_SCRAP, Items.NETHERITE_SCRAP, Items.NETHERITE_SCRAP, Items.NETHERITE_SCRAP, Items.GOLD_INGOT, Items.GOLD_INGOT, Items.GOLD_INGOT, Items.GOLD_INGOT, null}));
    }
    private Result smithArmor() {
        if (netheriteArmor()) return finish("Verified all four netherite armor pieces");
        int missing = 0;
        for (Item item : NETHERITE_ARMOR) if (count(item) == 0) missing++;
        if (count(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE) < missing && !(MC.player.currentScreenHandler instanceof SmithingScreenHandler && ownsScreen())) {
            if (count(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE) < 1) return Result.need("Need an owned Netherite Upgrade template. Bastion discovery and looting are not automated.");
            if (count(Items.DIAMOND) < 7 || count(Items.NETHERRACK) < 1) return Result.need("Duplicate each missing upgrade template with seven diamonds and one netherrack.");
            return begin(new Recipe(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 3, new Item[]{Items.DIAMOND, Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, Items.DIAMOND, Items.DIAMOND, Items.NETHERRACK, Items.DIAMOND, Items.DIAMOND, Items.DIAMOND, Items.DIAMOND}));
        }
        if (count(Items.SMITHING_TABLE) == 0 && !hasNearby(Blocks.SMITHING_TABLE) && !(MC.player.currentScreenHandler instanceof SmithingScreenHandler)) {
            if (count(Items.IRON_INGOT) < 2) {
                if (count(Items.RAW_IRON) == 0) return Result.need("Need two iron ingots for a smithing table.");
                return smeltGoal(Items.RAW_IRON, Items.IRON_INGOT, 2);
            }
            Result clearing = clearOwnedFurnace(); if (clearing != null) return clearing;
            if (planks() < 4) return makePlanks();
            Item[] wood = plankIngredients(4);
            return begin(new Recipe(Items.SMITHING_TABLE, 3, new Item[]{Items.IRON_INGOT, Items.IRON_INGOT, null, wood[0], wood[1], null, wood[2], wood[3], null}));
        }
        Result workstation = ensureStation(Blocks.SMITHING_TABLE, Items.SMITHING_TABLE, SmithingScreenHandler.class);
        if (workstation != null) return workstation;
        var handler = MC.player.currentScreenHandler;
        if (handler.getSlot(3).hasStack()) {
            ItemStack result = handler.getSlot(3).getStack();
            for (Item expected : NETHERITE_ARMOR) if (result.isOf(expected)) {
                int armorIndex = Arrays.asList(NETHERITE_ARMOR).indexOf(expected);
                if (!handler.getSlot(1).getStack().isOf(ARMOR[armorIndex])) return Result.need("Smithing output does not match the requested diamond armor input.");
                for (var enchantment : EnchantmentHelper.getEnchantments(handler.getSlot(1).getStack()).getEnchantmentEntries())
                    if (EnchantmentHelper.getLevel(enchantment.getKey(), result) < enchantment.getIntValue())
                        return Result.need("The smithing output would lose an existing armor enchantment; no output was taken.");
                click(3, 0, SlotActionType.QUICK_MOVE);
                return Result.waitFor("Collecting verified " + result.getName().getString());
            }
            return Result.need("Unexpected smithing output; no item was taken.");
        }
        int next = -1;
        for (int i = 0; i < NETHERITE_ARMOR.length; i++) if (count(NETHERITE_ARMOR[i]) == 0) { next = i; break; }
        if (next < 0) return finish("Netherite armor verified");
        Item input = ARMOR[next];
        Item[] ingredients = {Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, input, Items.NETHERITE_INGOT};
        for (int i = 0; i < ingredients.length; i++) {
            if (handler.getSlot(i).hasStack()) {
                if (!handler.getSlot(i).getStack().isOf(ingredients[i])) return Result.need("The smithing table contains another ingredient. Clear it before resuming.");
                continue;
            }
            final Item ingredient = ingredients[i];
            int inventory = inventorySlot(s -> s.isOf(ingredient));
            if (inventory < 0) {
                if (i == 1) { closeOwned(); return unequip(input); }
                return Result.need("Need " + ingredient.getName().getString() + " for the next armor upgrade.");
            }
            click(inventory, 0, SlotActionType.PICKUP); source = inventory; phase = 20 + i; transactionSync = handler.syncId;
            return Result.waitFor("Loading " + ingredient.getName().getString() + " into the smithing table");
        }
        if (++observationWait > 120) return Result.need("The server did not offer the expected netherite upgrade. Inputs remain in the smithing table.");
        return Result.waitFor("Waiting for server smithing output");
    }
    private Recipe pickRecipe(Item output, Item head) { return new Recipe(output, 3, new Item[]{head, head, head, null, Items.STICK, null, null, Items.STICK, null}); }
    private boolean hasPick(Item item) {
        return count(stack -> stack.isOf(item) && (!stack.isDamageable() || stack.getMaxDamage() - stack.getDamage() >= 16)
                && (!ordinaryMiningDrops || level(stack, "silk_touch") == 0)) > 0;
    }
    private Result sticks() {
        if (planks() < 2) return makePlanks();
        Item[] wood = plankIngredients(2);
        return begin(new Recipe(Items.STICK, 2, new Item[]{wood[0], null, wood[1], null}));
    }
    private int planks() { return count(s -> Registries.ITEM.getId(s.getItem()).getPath().endsWith("_planks")); }
    private Item[] plankIngredients(int amount) {
        Item[] result = new Item[amount]; int next = 0;
        for (int slot = 0; slot < 36 && next < amount; slot++) {
            ItemStack stack = MC.player.getInventory().getStack(slot);
            if (!Registries.ITEM.getId(stack.getItem()).getPath().endsWith("_planks")) continue;
            for (int count = 0; count < stack.getCount() && next < amount; count++) result[next++] = stack.getItem();
        }
        if (next < amount) throw new IllegalStateException("The checked plank ingredients changed before crafting.");
        return result;
    }
    private Result makePlanks() {
        for (int i=0;i<36;i++) {
            ItemStack s=MC.player.getInventory().getStack(i); String id=Registries.ITEM.getId(s.getItem()).getPath();
            if (!id.endsWith("_log") && !id.endsWith("_stem")) continue;
            String output=id.substring(0,id.lastIndexOf('_')) + "_planks";
            Item item=Registries.ITEM.get(Identifier.of("minecraft",output));
            if (item != Items.AIR) return begin(new Recipe(item,2,new Item[]{s.getItem(),null,null,null}));
        }
        return Result.need("Need logs to craft planks. Gather wood and resume.");
    }
    private Result shelterMaterials() {
        int blocks = count(Items.COBBLESTONE);
        if (blocks < ShelterSchematic.MATERIAL_COUNT) return Result.need("Need " + ShelterSchematic.MATERIAL_COUNT + " cobblestone for the 7 × 7 shelter (have " + blocks + ").");
        return finish("Shelter materials ready");
    }

    private Result preparePickaxe() {
        if (!(MC.player.currentScreenHandler instanceof PlayerScreenHandler)) { closeOwned(); return Result.waitFor("Closing workstation for mining"); }
        for (Item item : new Item[]{Items.NETHERITE_PICKAXE,Items.DIAMOND_PICKAXE,Items.IRON_PICKAXE,Items.STONE_PICKAXE,Items.WOODEN_PICKAXE}) {
            for (int i=0;i<9;i++) { var stack = MC.player.getInventory().getStack(i); if(stack.isOf(item) && stack.getMaxDamage() - stack.getDamage() >= 16 && (!ordinaryMiningDrops || level(stack,"silk_touch")==0)) return Result.finished("Mining tool is in the hotbar"); }
            int slot=inventorySlot(s->s.isOf(item) && s.getMaxDamage() - s.getDamage() >= 16 && (!ordinaryMiningDrops || level(s,"silk_touch")==0));
            if(slot>=0) { click(slot,8,SlotActionType.SWAP); return Result.waitFor("Moving the mining tool into the hotbar"); }
        }
        return Result.finished("No mining pickaxe to prepare"); // Logs can be mined by hand; ore checks are made by the executor.
    }
    private Result prepareHotbar(Item material,int required) {
        if (!(MC.player.currentScreenHandler instanceof PlayerScreenHandler)) {closeOwned();return Result.waitFor("Closing workstation for building");}
        int hotbar=0;for(int i=0;i<9;i++)if(MC.player.getInventory().getStack(i).isOf(material))hotbar+=MC.player.getInventory().getStack(i).getCount();
        if(hotbar>=required)return Result.finished("Building materials are available in the hotbar");
        int sourceSlot=-1;for(var slot:MC.player.currentScreenHandler.slots)if(slot.inventory==MC.player.getInventory()&&slot.getIndex()>=9&&slot.getIndex()<36&&slot.getStack().isOf(material)){sourceSlot=slot.id;break;}
        if(sourceSlot<0)return Result.need("Need " + required + " building blocks in inventory or hotbar.");
        for(int target=0;target<9;target++)if(!MC.player.getInventory().getStack(target).isOf(material)&&!MC.player.getInventory().getStack(target).isOf(Items.IRON_PICKAXE)&&!MC.player.getInventory().getStack(target).isOf(Items.DIAMOND_PICKAXE)&&!MC.player.getInventory().getStack(target).isOf(Items.NETHERITE_PICKAXE)){click(sourceSlot,target,SlotActionType.SWAP);return Result.waitFor("Moving shelter materials into the hotbar");}
        return Result.need("Free three hotbar slots for shelter materials.");
    }

    private Result begin(Recipe value) {
        if (freeSlots() < 2) return Result.need("Make at least two empty inventory slots before crafting.");
        if (value.width == 3) {
            if (!(MC.player.currentScreenHandler instanceof CraftingScreenHandler) && count(Items.CRAFTING_TABLE) == 0 && !hasNearby(Blocks.CRAFTING_TABLE)) {
                if (planks() < 4) return makePlanks();
                return begin(new Recipe(Items.CRAFTING_TABLE, 2, plankIngredients(4)));
            }
            Result workstation=ensureStation(Blocks.CRAFTING_TABLE, Items.CRAFTING_TABLE, CraftingScreenHandler.class); if (workstation != null) return workstation;
        }
        else if (!(MC.player.currentScreenHandler instanceof PlayerScreenHandler) && !(MC.player.currentScreenHandler instanceof CraftingScreenHandler)) { closeOwned(); return Result.waitFor("Returning to the player crafting grid"); }
        ScreenHandler handler=MC.player.currentScreenHandler;
        if (!handler.getCursorStack().isEmpty()) return Result.need("Put the item on your cursor back into the inventory, then resume.");
        int grid=handler instanceof CraftingScreenHandler ? 9 : 4;
        for(int i=1;i<=grid;i++) if(handler.getSlot(i).hasStack()) {
            if (!ownsScreen() && !(handler instanceof PlayerScreenHandler)) return Result.need("Clear the crafting grid before starting.");
            click(i,0,SlotActionType.QUICK_MOVE); return Result.waitFor("Returning previous crafting ingredients");
        }
        Map<Item,Integer> required=new HashMap<>(); for(Item i:value.cells) if(i!=null) required.merge(i,1,Integer::sum);
        for(var entry:required.entrySet()) if(count(entry.getKey()) < entry.getValue()) return Result.need("Need " + entry.getValue() + " " + entry.getKey().getName().getString() + " for " + value.output.getName().getString());
        recipe=value; cell=0; phase=0; transactionSync=handler.syncId; observationWait=0;
        return Result.waitFor("Crafting " + value.output.getName().getString());
    }
    private Result craftTick() {
        ScreenHandler h=MC.player.currentScreenHandler;
        int actualWidth=h instanceof CraftingScreenHandler ? 3 : 2;
        if(cell>=recipe.cells.length) {
            if (!h.getCursorStack().isEmpty()) return returnCursor();
            ItemStack output=h.getSlot(0).getStack();
            if (output.isOf(recipe.output)) { click(0,0,SlotActionType.QUICK_MOVE); recipe=null; transactionSync=-1; return Result.waitFor("Collecting the crafted item"); }
            if (++observationWait > 120) { resetTransaction(); return Result.need("The server did not accept this crafting recipe. Ingredients remain in the grid."); }
            return Result.waitFor("Waiting for crafting output");
        }
        Item ingredient=recipe.cells[cell];
        if(ingredient==null) {cell++; return Result.waitFor("Arranging recipe");}
        int slot=1+(cell / recipe.width)*actualWidth+(cell % recipe.width);
        if(phase==0) {
            if(!h.getCursorStack().isEmpty()) return returnCursor();
            source=inventorySlot(s -> s.isOf(ingredient));
            if(source<0) return Result.need("Missing " + ingredient.getName().getString());
            click(source,0,SlotActionType.PICKUP); phase=1;
        } else if(phase==1) {
            if(!h.getCursorStack().isOf(ingredient)) {resetTransaction(); return Result.need("Crafting cursor changed; resume after checking the inventory.");}
            if(h.getSlot(slot).hasStack()) {resetTransaction(); return Result.need("Crafting grid changed; clear it before resuming.");}
            click(slot,1,SlotActionType.PICKUP); phase=2;
        } else {
            if(!h.getSlot(slot).getStack().isOf(ingredient)) return Result.need("The server rejected an ingredient placement.");
            if(!h.getCursorStack().isEmpty()) click(source,0,SlotActionType.PICKUP);
            cell++; phase=0;
        }
        return Result.waitFor("Crafting " + recipe.output.getName().getString());
    }
    private Result smeltIron() { return smelt(Items.RAW_IRON, Items.IRON_INGOT, 3); }
    private Result smelt(Item input, Item output, int required) {
        if (!(MC.player.currentScreenHandler instanceof AbstractFurnaceScreenHandler) && count(Items.FURNACE) == 0 && !hasNearby(Blocks.FURNACE)) {
            if (count(Items.COBBLESTONE) < 8) return Result.need("Need eight cobblestone to recover a furnace at this location.");
            return begin(new Recipe(Items.FURNACE, 3, new Item[]{Items.COBBLESTONE, Items.COBBLESTONE, Items.COBBLESTONE, Items.COBBLESTONE, null, Items.COBBLESTONE, Items.COBBLESTONE, Items.COBBLESTONE, Items.COBBLESTONE}));
        }
        Result workstation=ensureStation(Blocks.FURNACE,Items.FURNACE,AbstractFurnaceScreenHandler.class); if(workstation!=null) return workstation;
        ScreenHandler h=MC.player.currentScreenHandler;
        if(h.getSlot(2).hasStack() && !h.getSlot(2).getStack().isOf(output)) return Result.need("The furnace contains another output. Choose an empty furnace.");
        if(h.getSlot(2).getStack().isOf(output)) { click(2,0,SlotActionType.QUICK_MOVE); return Result.waitFor("Collecting " + output.getName().getString()); }
        if(h.getSlot(0).hasStack() && !h.getSlot(0).getStack().isOf(input)) return Result.need("The furnace contains another input. Choose an empty furnace.");
        if(!h.getSlot(0).hasStack()) {
            int slot=inventorySlot(s -> s.isOf(input)); if(slot<0) return Result.need("Need " + input.getName().getString() + " to smelt " + required + " " + output.getName().getString());
            click(slot,0,SlotActionType.QUICK_MOVE); return Result.waitFor("Loading " + input.getName().getString());
        }
        if(!h.getSlot(1).hasStack()) {
            int slot=inventorySlot(s -> s.isOf(Items.COAL)||s.isOf(Items.CHARCOAL)); if(slot<0) return Result.need("Need coal or charcoal for smelting.");
            click(slot,0,SlotActionType.QUICK_MOVE); return Result.waitFor("Loading furnace fuel");
        }
        return Result.waitFor("Smelting " + output.getName().getString() + ": " + count(output) + "/" + required);
    }
    private Result equip() { return equipArmor(ARMOR, "diamond"); }
    private Result equipArmor(Item[] armor, String material) {
        if(!(MC.player.currentScreenHandler instanceof PlayerScreenHandler)) {closeOwned();return Result.waitFor("Returning to inventory to equip armor");}
        for(int i=0;i<armor.length;i++) {
            if(MC.player.getEquippedStack(EQUIPMENT[i]).isOf(armor[i])) continue;
            int target=5+i;
            if(MC.player.currentScreenHandler.getSlot(target).hasStack()) {
                if(freeSlots()<1) return Result.need("Need one empty slot to store current armor.");
                click(target,0,SlotActionType.QUICK_MOVE); return Result.waitFor("Storing previous armor");
            }
            final Item armorItem=armor[i]; int slot=inventorySlot(s -> s.isOf(armorItem)); if(slot<0) return Result.need("Missing " + armor[i].getName().getString());
            click(slot,0,SlotActionType.QUICK_MOVE); return Result.waitFor("Equipping " + material + " armor");
        }
        return finish("All four " + material + " armor pieces equipped");
    }

    /** Table enchant unenchanted pieces, then apply matching available books through a real anvil. */
    private Result enchant(int minimumLevel) {
        if (MC.player.currentScreenHandler instanceof EnchantmentScreenHandler h && h.getSlot(0).hasStack()) return tableEnchant(h.getSlot(0).getStack().getItem(), minimumLevel);
        if(goodArmor()) return finish("Verified Protection IV + Unbreaking III on all four diamond pieces");
        if(MC.player.currentScreenHandler instanceof AnvilScreenHandler && ownsScreen())return bookUpgrade();
        for(Item item:ARMOR){ItemStack stack=findItem(item);String wanted=level(stack,"protection")<4?"protection":level(stack,"unbreaking")<3?"unbreaking":null;if(wanted!=null && count(book->book.isOf(Items.ENCHANTED_BOOK)&&level(book,wanted)>=(wanted.equals("protection")?4:3))>0)return bookUpgrade();}
        for(int i=0;i<ARMOR.length;i++) {
            ItemStack stack=findItem(ARMOR[i]); if(stack.isEmpty()) return Result.need("Craft diamond armor before enchanting.");
            if(!stack.hasEnchantments()) return tableEnchant(ARMOR[i],minimumLevel);
        }
        return bookUpgrade();
    }
    private Result tableEnchant(Item item,int minimumLevel) {
        Result workstation=ensureStation(Blocks.ENCHANTING_TABLE,Items.ENCHANTING_TABLE,EnchantmentScreenHandler.class); if(workstation!=null)return workstation;
        var h=(EnchantmentScreenHandler)MC.player.currentScreenHandler;
        ItemStack input=h.getSlot(0).getStack();
        if(!input.isEmpty() && input.hasEnchantments()) {click(0,0,SlotActionType.QUICK_MOVE);return Result.waitFor("Collecting enchanted armor");}
        if(input.isEmpty()) {
            int slot=inventorySlot(s->s.isOf(item));
            if(slot<0) {closeOwned();return unequip(item);}
            click(slot,0,SlotActionType.QUICK_MOVE);return Result.waitFor("Loading armor into enchanting table");
        }
        if(!input.isOf(item)) return Result.need("The enchanting table contains another item.");
        if(h.getLapisCount()<3) {
            int slot=inventorySlot(s->s.isOf(Items.LAPIS_LAZULI));if(slot<0)return Result.need("Need lapis lazuli for level-30 armor enchanting.");
            click(slot,0,SlotActionType.QUICK_MOVE);return Result.waitFor("Loading lapis lazuli");
        }
        if(h.enchantmentPower[2]==0 && ++observationWait<200)return Result.waitFor("Waiting for server enchantment offers");
        if(h.enchantmentPower[2]<minimumLevel) return Result.need("Need a table with enough unobstructed bookshelves (top offer " + h.enchantmentPower[2] + ", required " + minimumLevel + ").");
        if(MC.player.experienceLevel<h.enchantmentPower[2] && !MC.player.getAbilities().creativeMode) return Result.need("Need " + h.enchantmentPower[2] + " XP levels (have " + MC.player.experienceLevel + ").");
        MC.interactionManager.clickButton(h.syncId,2); clickCount++; throttle();
        return Result.waitFor("Applying the server's level-30 enchantment offer");
    }
    private Result bookUpgrade() {
        Item target=null;String enchantment=null;int needed=0;
        for(Item item:ARMOR) {
            ItemStack stack=findItem(item);
            if(level(stack,"protection")<4){target=item;enchantment="protection";needed=4;break;}
            if(level(stack,"unbreaking")<3){target=item;enchantment="unbreaking";needed=3;break;}
        }
        if(target==null)return finish("Quality armor verified");
        final String key=enchantment; final int required=needed;
        Predicate<ItemStack> book=s -> s.isOf(Items.ENCHANTED_BOOK) && level(s,key)>=required;
        boolean loadedBook = MC.player.currentScreenHandler instanceof AnvilScreenHandler && ownsScreen() && book.test(MC.player.currentScreenHandler.getSlot(1).getStack());
        if(count(book)==0 && !loadedBook) return Result.need("Need an enchanted " + (key.equals("protection")?"Protection IV":"Unbreaking III") + " book for " + target.getName().getString() + ". Table outcomes are random; this goal stays incomplete.");
        Result workstation=ensureStation(Blocks.ANVIL,Items.ANVIL,AnvilScreenHandler.class); if(workstation!=null)return workstation;
        var h=(AnvilScreenHandler)MC.player.currentScreenHandler;
        if(h.getSlot(2).hasStack()) {
            if(h.getLevelCost()>=40&&!MC.player.getAbilities().creativeMode)return Result.need("Anvil reports Too Expensive; this piece cannot accept this upgrade in Survival.");
            if(MC.player.experienceLevel<h.getLevelCost()&&!MC.player.getAbilities().creativeMode)return Result.need("Need " + h.getLevelCost() + " XP levels for this anvil upgrade.");
            ItemStack output=h.getSlot(2).getStack();
            if(!output.isOf(target)||level(output,key)<required)return Result.need("Anvil output does not satisfy the requested armor upgrade. Check conflicting enchantments.");
            click(2,0,SlotActionType.QUICK_MOVE);return Result.waitFor("Collecting upgraded armor");
        }
        if(!h.getSlot(0).hasStack()) {
            final Item targetItem=target;int slot=inventorySlot(s->s.isOf(targetItem));
            if(slot<0){closeOwned();return unequip(target);}
            click(slot,0,SlotActionType.PICKUP);source=slot;phase=10;transactionSync=h.syncId;return Result.waitFor("Loading armor for anvil upgrade");
        }
        if(!h.getSlot(1).hasStack()) {
            int slot=inventorySlot(book);if(slot<0)return Result.need("The required enchanted book is no longer in inventory.");
            click(slot,0,SlotActionType.PICKUP);source=slot;phase=11;transactionSync=h.syncId;return Result.waitFor("Loading upgrade book");
        }
        if(++observationWait>100)return Result.need("Anvil did not produce a compatible output; check enchantment conflicts and repair cost.");
        return Result.waitFor("Waiting for the anvil's verified output");
    }
    private Result unequip(Item item) {
        if(!(MC.player.currentScreenHandler instanceof PlayerScreenHandler))return Result.waitFor("Closing workstation");
        for(int i=0;i<ARMOR.length;i++)if(ARMOR[i]==item && MC.player.currentScreenHandler.getSlot(5+i).getStack().isOf(item)) {
            if(freeSlots()<1)return Result.need("Need an empty inventory slot to enchant equipped armor.");
            click(5+i,0,SlotActionType.QUICK_MOVE);return Result.waitFor("Moving equipped armor to inventory for enchanting");
        }
        return Result.waitFor("Waiting for armor inventory update");
    }

    /** Handle the two explicit anvil pickup placements before choosing another action. */
    public Result tickTransfer() {
        if(phase!=10 && phase!=11 && (phase<20 || phase>22))return null;
        if(wait>0){wait--;return Result.waitFor("Waiting for server slot update");}
        if(MC.player==null || MC.player.currentScreenHandler.syncId!=transactionSync){resetTransaction();return Result.need("The workstation window changed.");}
        if(!MC.player.currentScreenHandler.getCursorStack().isEmpty()) {
            int slot = phase >= 20 ? phase - 20 : phase == 10 ? 0 : 1;
            click(slot,0,SlotActionType.PICKUP);phase=0;transactionSync=-1;return Result.waitFor("Placing the workstation input");
        }
        phase=0;transactionSync=-1;return Result.need("The server rejected the workstation input pickup.");
    }

    private Result ensureStation(Block block,Item item,Class<? extends ScreenHandler> type) {
        if(type.isInstance(MC.player.currentScreenHandler)) {
            if(!ownedScreen)return Result.need("Close your current workstation before starting; Analyzer only owns windows it opens.");
            ownedSync=MC.player.currentScreenHandler.syncId;return null;
        }
        if(!(MC.player.currentScreenHandler instanceof PlayerScreenHandler)) {closeOwned();return Result.waitFor("Closing the previous workstation");}
        if(MC.currentScreen!=null && !ownedScreen)return Result.need("Close the menu to continue.");
        if(station==null || !MC.world.getBlockState(station).isOf(block))station=findStation(block);
        if(station==null) {
            if(count(item)<1)return Result.need("Need a nearby " + block.getName().getString() + " or one in inventory.");
            return workstations.place(block, item, placementExclusion);
        }
        Result approach = workstations.approach(station); if (approach != null) return approach;
        BlockHitResult observed = workstations.interactionHit(station);
        if (observed == null) return Result.waitFor("Turning toward a visible face of " + block.getName().getString());
        MC.interactionManager.interactBlock(MC.player,Hand.MAIN_HAND,observed);
        ownedScreen=true;ownedSync=-1;expectedHandler=type;wait=8;observationWait=0;
        return Result.waitFor("Opening " + block.getName().getString());
    }
    private boolean hasNearby(Block block) { return findStation(block)!=null; }
    private BlockPos findStation(Block block) {
        BlockPos p=MC.player.getBlockPos(),best=null;double distance=Double.MAX_VALUE;
        for(BlockPos test:BlockPos.iterate(p.add(-8,-3,-8),p.add(8,3,8))) {
            if(!MC.world.isChunkLoaded(test)||!MC.world.getBlockState(test).isOf(block))continue;
            double d=test.getSquaredDistance(p);if(d<distance){distance=d;best=test.toImmutable();}
        }
        return best;
    }
    private Result restoreSwap() {
        if(!(MC.player.currentScreenHandler instanceof PlayerScreenHandler)) {closeOwned();return Result.waitFor("Restoring the previous hotbar item");}
        MC.interactionManager.clickSlot(0,swapSource,swapHotbar,SlotActionType.SWAP,MC.player);clickCount++;swapSource=-1;swapHotbar=-1;throttle();
        return Result.waitFor("Previous hotbar item restored");
    }
    private int inventorySlot(Predicate<ItemStack> test) {
        for(var s:MC.player.currentScreenHandler.slots) if(s.inventory==MC.player.getInventory() && s.getIndex()<36 && test.test(s.getStack()))return s.id;
        return -1;
    }
    private int freeSlots(){int n=0;for(int i=0;i<36;i++)if(MC.player.getInventory().getStack(i).isEmpty())n++;return n;}
    private void click(int slot,int button,SlotActionType action){
        ScreenHandler h=MC.player.currentScreenHandler;
        if(action==SlotActionType.PICKUP && button==0 && h.getCursorStack().isEmpty() && h.getSlot(slot).hasStack() && h.getSlot(slot).inventory==MC.player.getInventory()) {cursorOwned=true;cursorSource=slot;cursorSync=h.syncId;cursorWorld=MC.world;}
        MC.interactionManager.clickSlot(h.syncId,slot,button,action,MC.player);clickCount++;throttle();
        if(h.getCursorStack().isEmpty()){cursorOwned=false;recovery="";}
    }
    private void throttle(){int latency=0;if(MC.getNetworkHandler()!=null&&MC.player!=null){var entry=MC.getNetworkHandler().getPlayerListEntry(MC.player.getUuid());if(entry!=null)latency=entry.getLatency();}wait=Math.max(actionDelayTicks,Math.min(60,(latency+49)/50+3));}
    private Result returnCursor(){
        ScreenHandler h=MC.player.currentScreenHandler;
        if(!cursorOwned || h.syncId!=cursorSync || MC.world!=cursorWorld)return Result.need("The item on the cursor belongs to you; put it back before resuming.");
        ItemStack cursor=h.getCursorStack();
        int slot=-1;
        if(cursorSource>=0 && cursorSource<h.slots.size()) {var original=h.getSlot(cursorSource);ItemStack stack=original.getStack();if(original.inventory==MC.player.getInventory() && original.canInsert(cursor) && (stack.isEmpty() || ItemStack.areItemsAndComponentsEqual(stack,cursor) && stack.getCount()+cursor.getCount()<=stack.getMaxCount()))slot=cursorSource;}
        if(slot<0)slot=inventorySlot(ItemStack::isEmpty);
        if(slot<0){recovery="Make an empty inventory slot and put the held crafting item back; Stop preserved it on the cursor.";return Result.need(recovery);}
        click(slot,0,SlotActionType.PICKUP);return Result.waitFor("Returning owned cursor item to inventory");
    }
    private Result finish(String text){finishingText=text;Result cleanup=finishWorkstations();if(!cleanup.done())return cleanup;finishingText="";closeOwned();resetTransaction();return Result.finished(text);}
    private void closeOwned(){if(ownedScreen && MC.player!=null && MC.player.currentScreenHandler!=MC.player.playerScreenHandler && MC.player.currentScreenHandler.getCursorStack().isEmpty()){MC.player.closeHandledScreen();throttle();}ownedScreen=false;ownedSync=-1;expectedHandler=null;station=null;}
    private void resetTransaction(){recipe=null;cell=0;source=-1;phase=0;transactionSync=-1;observationWait=0;}
    public void stop(){
        workstations.stop();
        if(MC.player!=null && MC.interactionManager!=null){
            if(cursorOwned && !MC.player.currentScreenHandler.getCursorStack().isEmpty()) {Result restored=returnCursor();if(restored.blocked())return;}
            if(swapSource>=0 && MC.player.currentScreenHandler instanceof PlayerScreenHandler)restoreSwap();
            closeOwned();
        }
        resetTransaction();job="";finishingText="";
    }
}
