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
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
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
    private static final EquipmentSlot[] EQUIPMENT = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private final BaritoneBridge bridge;
    private String job = "";
    private Recipe recipe;
    private int cell, source = -1, phase, wait, ownedSync = -1, transactionSync = -1;
    private int swapSource = -1, swapHotbar = -1, oldSelected = -1;
    private long clickCount;
    private boolean ownedScreen;
    private Class<? extends ScreenHandler> expectedHandler;
    private BlockPos station;
    private int observationWait;
    private boolean cursorOwned;
    private int cursorSource=-1,cursorSync=-1;
    private Object cursorWorld;
    private String recovery="";
    private int buildAmount=ShelterSchematic.MATERIAL_COUNT;
    public InventoryAutomation(BaritoneBridge bridge) { this.bridge = bridge; }
    public long clicks() { return clickCount; }
    public boolean ownsScreen() { return ownedScreen && MC.player != null && MC.player.currentScreenHandler.syncId == ownedSync || ownedScreen && MC.player != null && ownedSync < 0 && expectedHandler != null && expectedHandler.isInstance(MC.player.currentScreenHandler); }
    public String currentJob() { return job; }
    public String recovery() { return recovery; }
    public Result prepareBuild(int amount) {buildAmount=Math.clamp(amount,0,ShelterSchematic.MATERIAL_COUNT);return tick("build-hotbar",30); }

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
    public static boolean diamondKit() { for (Item item : ARMOR) if (count(item) < 1) return false; return count(Items.DIAMOND_PICKAXE) > 0 && count(Items.DIAMOND_SWORD) > 0; }
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
        if (transactionSync >= 0 && MC.player.currentScreenHandler.syncId != transactionSync) { resetTransaction(); return Result.need("The inventory window changed; resume after closing it."); }
        if (swapSource >= 0) return restoreSwap();
        if (recipe != null) return craftTick();
        return switch (requested) {
            case "bootstrap" -> bootstrap();
            case "stone-tools" -> stoneTools();
            case "iron-tools" -> ironTools();
            case "diamond-kit" -> diamondCraft();
            case "shelter-materials" -> shelterMaterials();
            case "build-hotbar" -> prepareHotbar(Items.COBBLESTONE, buildAmount);
            case "mining-hotbar" -> preparePickaxe();
            case "equip", "diamond-armor-equip" -> equip();
            case "diamond-armor", "enchanted-armor", "good-armor" -> enchant(enchantLevel);
            default -> Result.need("Unsupported inventory task: " + requested);
        };
    }

    private Result bootstrap() {
        if (count(Items.CRAFTING_TABLE) == 0 && !hasNearby(Blocks.CRAFTING_TABLE)) {
            if (planks() < 4) return makePlanks();
            return begin(new Recipe(Items.CRAFTING_TABLE, 2, new Item[]{plank(), plank(), plank(), plank()}));
        }
        if (count(Items.STICK) < 4) { if (planks() < 2) return makePlanks(); return begin(new Recipe(Items.STICK, 2, new Item[]{plank(), null, plank(), null})); }
        if (hasPick(Items.WOODEN_PICKAXE) || hasPick(Items.STONE_PICKAXE) || hasPick(Items.IRON_PICKAXE) || hasPick(Items.DIAMOND_PICKAXE) || hasPick(Items.NETHERITE_PICKAXE)) return finish("Crafting table and mining tools ready");
        if (planks() < 3) return makePlanks();
        return begin(pickRecipe(Items.WOODEN_PICKAXE, plank()));
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
        if (count(Items.STICK) < 4 && (count(Items.DIAMOND_PICKAXE) == 0 || count(Items.DIAMOND_SWORD) == 0)) return sticks();
        if (count(Items.DIAMOND_PICKAXE) == 0) return diamondRecipe(pickRecipe(Items.DIAMOND_PICKAXE, Items.DIAMOND));
        if (count(Items.DIAMOND_SWORD) == 0) return diamondRecipe(new Recipe(Items.DIAMOND_SWORD, 3, new Item[]{null, Items.DIAMOND, null, null, Items.DIAMOND, null, null, Items.STICK, null}));
        if (count(ARMOR[0]) == 0) return diamondRecipe(new Recipe(ARMOR[0], 3, new Item[]{Items.DIAMOND, Items.DIAMOND, Items.DIAMOND, Items.DIAMOND, null, Items.DIAMOND, null, null, null}));
        if (count(ARMOR[1]) == 0) return diamondRecipe(new Recipe(ARMOR[1], 3, new Item[]{Items.DIAMOND, null, Items.DIAMOND, Items.DIAMOND, Items.DIAMOND, Items.DIAMOND, Items.DIAMOND, Items.DIAMOND, Items.DIAMOND}));
        if (count(ARMOR[2]) == 0) return diamondRecipe(new Recipe(ARMOR[2], 3, new Item[]{Items.DIAMOND, Items.DIAMOND, Items.DIAMOND, Items.DIAMOND, null, Items.DIAMOND, Items.DIAMOND, null, Items.DIAMOND}));
        return diamondRecipe(new Recipe(ARMOR[3], 3, new Item[]{Items.DIAMOND, null, Items.DIAMOND, Items.DIAMOND, null, Items.DIAMOND, null, null, null}));
    }
    private Result diamondRecipe(Recipe value) { long needed = Arrays.stream(value.cells).filter(i -> i == Items.DIAMOND).count(); if (count(Items.DIAMOND) < needed) return Result.need("Need " + needed + " diamonds for " + value.output.getName().getString()); return begin(value); }
    private Recipe pickRecipe(Item output, Item head) { return new Recipe(output, 3, new Item[]{head, head, head, null, Items.STICK, null, null, Items.STICK, null}); }
    private boolean hasPick(Item item) { return count(item) > 0; }
    private Result sticks() { if (planks() < 2) return makePlanks(); return begin(new Recipe(Items.STICK, 2, new Item[]{plank(), null, plank(), null})); }
    private int planks() { return count(s -> Registries.ITEM.getId(s.getItem()).getPath().endsWith("_planks")); }
    private Item plank() { for (int i=0;i<36;i++) { ItemStack s=MC.player.getInventory().getStack(i); if (Registries.ITEM.getId(s.getItem()).getPath().endsWith("_planks") && s.getCount() >= 2) return s.getItem(); } return Items.OAK_PLANKS; }
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
            for (int i=0;i<9;i++) if(MC.player.getInventory().getStack(i).isOf(item)) return Result.finished("Mining tool is in the hotbar");
            int slot=inventorySlot(s->s.isOf(item));
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
        if (value.width == 3) { Result workstation=ensureStation(Blocks.CRAFTING_TABLE, Items.CRAFTING_TABLE, CraftingScreenHandler.class); if (workstation != null) return workstation; }
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
    private Result smeltIron() {
        Result workstation=ensureStation(Blocks.FURNACE,Items.FURNACE,AbstractFurnaceScreenHandler.class); if(workstation!=null) return workstation;
        ScreenHandler h=MC.player.currentScreenHandler;
        if(h.getSlot(2).getStack().isOf(Items.IRON_INGOT)) { click(2,0,SlotActionType.QUICK_MOVE); return Result.waitFor("Collecting smelted iron"); }
        if(h.getSlot(0).hasStack() && !h.getSlot(0).getStack().isOf(Items.RAW_IRON)) return Result.need("The furnace contains another input. Choose an empty furnace.");
        if(!h.getSlot(0).hasStack()) {
            int slot=inventorySlot(s -> s.isOf(Items.RAW_IRON)); if(slot<0) return Result.need("Need raw iron for three iron ingots.");
            click(slot,0,SlotActionType.QUICK_MOVE); return Result.waitFor("Loading raw iron");
        }
        if(!h.getSlot(1).hasStack()) {
            int slot=inventorySlot(s -> s.isOf(Items.COAL)||s.isOf(Items.CHARCOAL)); if(slot<0) return Result.need("Need coal or charcoal for smelting.");
            click(slot,0,SlotActionType.QUICK_MOVE); return Result.waitFor("Loading furnace fuel");
        }
        return Result.waitFor("Smelting iron: " + count(Items.IRON_INGOT) + "/3 ingots");
    }
    private Result equip() {
        if(!(MC.player.currentScreenHandler instanceof PlayerScreenHandler)) {closeOwned();return Result.waitFor("Returning to inventory to equip armor");}
        for(int i=0;i<ARMOR.length;i++) {
            if(MC.player.getEquippedStack(EQUIPMENT[i]).isOf(ARMOR[i])) continue;
            int target=5+i;
            if(MC.player.currentScreenHandler.getSlot(target).hasStack()) {
                if(freeSlots()<1) return Result.need("Need one empty slot to store current armor.");
                click(target,0,SlotActionType.QUICK_MOVE); return Result.waitFor("Storing previous armor");
            }
            final Item armorItem=ARMOR[i]; int slot=inventorySlot(s -> s.isOf(armorItem)); if(slot<0) return Result.need("Missing " + ARMOR[i].getName().getString());
            click(slot,0,SlotActionType.QUICK_MOVE); return Result.waitFor("Equipping diamond armor");
        }
        return finish("All four diamond armor pieces equipped");
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
        if(phase!=10 && phase!=11)return null;
        if(wait>0){wait--;return Result.waitFor("Waiting for server slot update");}
        if(MC.player==null || MC.player.currentScreenHandler.syncId!=transactionSync){resetTransaction();return Result.need("Anvil window changed.");}
        if(!MC.player.currentScreenHandler.getCursorStack().isEmpty()) {
            click(phase==10?0:1,0,SlotActionType.PICKUP);phase=0;transactionSync=-1;return Result.waitFor("Placing the anvil input");
        }
        phase=0;transactionSync=-1;return Result.need("The server rejected the anvil input pickup.");
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
            return placeStation(item);
        }
        if(MC.player.getEyePos().squaredDistanceTo(station.toCenterPos())>16) {
            var status=bridge.status();
            if(!status.owned() && !bridge.travelXZ(station.getX()+1,station.getZ()+1))return Result.need(bridge.status().message());
            return Result.waitFor("Walking to " + block.getName().getString());
        }
        if(bridge.status().owned())bridge.cancel();
        MC.interactionManager.interactBlock(MC.player,Hand.MAIN_HAND,new BlockHitResult(station.toCenterPos(),Direction.UP,station,false));
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
    private Result placeStation(Item item) {
        BlockPos p=MC.player.getBlockPos(),target=null;
        for(BlockPos test:BlockPos.iterate(p.add(-3,-1,-3),p.add(3,0,3))) {
            if(test.getSquaredDistance(p)<4 || !MC.world.getBlockState(test).isReplaceable() || MC.world.getBlockState(test.down()).isAir() || !MC.world.getFluidState(test).isEmpty())continue;
            if(MC.player.getEyePos().squaredDistanceTo(test.down().toCenterPos())<=16){target=test.toImmutable();break;}
        }
        if(target==null)return Result.need("Need a clear, reachable solid surface to place a workstation.");
        int inv=-1;for(int i=0;i<36;i++)if(MC.player.getInventory().getStack(i).isOf(item)){inv=i;break;}
        if(inv<0)return Result.need("Workstation item is missing.");
        oldSelected=MC.player.getInventory().selectedSlot;
        if(inv>=9) {
            swapSource=inv;swapHotbar=oldSelected;
            MC.interactionManager.clickSlot(0,inv,oldSelected,SlotActionType.SWAP,MC.player);clickCount++;
        } else MC.player.getInventory().selectedSlot=inv;
        BlockPos ground=target.down();
        MC.interactionManager.interactBlock(MC.player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(ground).add(0,0.5,0),Direction.UP,ground,false));
        MC.player.getInventory().selectedSlot=oldSelected;
        station=target;throttle();
        return Result.waitFor("Placing the workstation through normal block interaction");
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
    private void throttle(){int latency=0;if(MC.getNetworkHandler()!=null&&MC.player!=null){var entry=MC.getNetworkHandler().getPlayerListEntry(MC.player.getUuid());if(entry!=null)latency=entry.getLatency();}wait=Math.max(5,Math.min(60,(latency+49)/50+3));}
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
    private Result finish(String text){closeOwned();resetTransaction();return Result.finished(text);}
    private void closeOwned(){if(ownedScreen && MC.player!=null && MC.player.currentScreenHandler!=MC.player.playerScreenHandler && MC.player.currentScreenHandler.getCursorStack().isEmpty())MC.player.closeHandledScreen();ownedScreen=false;ownedSync=-1;expectedHandler=null;station=null;}
    private void resetTransaction(){recipe=null;cell=0;source=-1;phase=0;transactionSync=-1;observationWait=0;}
    public void stop(){
        if(MC.player!=null && MC.interactionManager!=null){
            if(cursorOwned && !MC.player.currentScreenHandler.getCursorStack().isEmpty()) {Result restored=returnCursor();if(restored.blocked())return;}
            if(swapSource>=0 && MC.player.currentScreenHandler instanceof PlayerScreenHandler)restoreSwap();
            closeOwned();
        }
        resetTransaction();job="";
    }
}
