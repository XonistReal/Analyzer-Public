package dev.analyzer.automation;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import java.util.*;

/** A local prerequisite queue rebuilt from real inventory when a required tool is absent or broken. */
public final class ToolRecovery {
    private record Stage(String resource,int count,String craft) { }
    private final AutomationNavigator navigator;
    private final InventoryAutomation inventory;
    private int targetTier, index;
    private boolean dispatched;
    private boolean ordinaryDrops;
    private String directJob="";
    private List<Stage> stages=List.of();
    public ToolRecovery(AutomationNavigator navigator,InventoryAutomation inventory) { this.navigator=navigator;this.inventory=inventory; }
    public static boolean hasTier(int tier) { return hasTier(tier, false); }
    public static boolean hasTier(int tier, boolean ordinaryDrops) {
        var mc=MinecraftClient.getInstance(); if(mc.player==null)return false;
        if(tier<=0)return true;
        for(int i=0;i<mc.player.getInventory().size();i++) {
            var stack=mc.player.getInventory().getStack(i);
            if(stack.isEmpty() || stack.isDamageable() && stack.getMaxDamage()-stack.getDamage()<16)continue;
            if(ordinaryDrops && InventoryAutomation.level(stack,"silk_touch")>0)continue;
            String id=Registries.ITEM.getId(stack.getItem()).getPath();
            int level=id.equals("netherite_pickaxe")?5:id.equals("diamond_pickaxe")?4:id.equals("iron_pickaxe")?3:id.equals("stone_pickaxe")?2:id.equals("wooden_pickaxe")||id.equals("golden_pickaxe")?1:0;
            if(level>=tier)return true;
        }
        return false;
    }
    public boolean active() { return targetTier > 0; }
    public boolean ownsRoute() { return dispatched && navigator.status().owned(); }
    /** A newly crafted tool must not skip pickup of the task's own table. */
    public InventoryAutomation.Result finishIfUsable() {
        return active() && hasTier(targetTier, ordinaryDrops) ? tick(targetTier, ordinaryDrops) : null;
    }
    public InventoryAutomation.Result tick(int tier) { return tick(tier, false); }
    public InventoryAutomation.Result tick(int tier, boolean requiredOrdinaryDrops) {
        if(tier<1 || tier>4)return new InventoryAutomation.Result(false,true,"Cannot craft this tool tier automatically.");
        if(hasTier(tier,requiredOrdinaryDrops)) {
            if(active()) {
                var cleanup=inventory.finishWorkstations();
                if(!cleanup.done())return cleanup;
            }
            reset();return new InventoryAutomation.Result(true,false,"Usable mining tool verified");
        }
        if(targetTier!=tier || ordinaryDrops!=requiredOrdinaryDrops || stages.isEmpty()) {
            reset();navigator.cancel();inventory.stop();targetTier=tier;ordinaryDrops=requiredOrdinaryDrops;index=0;dispatched=false;
            var queue=new ArrayList<Stage>();
            queue.add(new Stage("logs",8,""));queue.add(new Stage("",0,"bootstrap"));
            if(tier>=2){queue.add(new Stage("cobblestone",16,""));queue.add(new Stage("",0,"stone-tools"));}
            if(tier>=3){queue.add(new Stage("raw_iron",3,""));queue.add(new Stage("coal",1,""));queue.add(new Stage("",0,"iron-tools"));}
            if(tier>=4){queue.add(new Stage("diamond",3,""));queue.add(new Stage("",0,"diamond-pickaxe"));}
            stages=List.copyOf(queue);
            boolean table=InventoryAutomation.count(Items.CRAFTING_TABLE)>0||nearbyTable();
            int planks=InventoryAutomation.count(stack->Registries.ITEM.getId(stack.getItem()).getPath().endsWith("_planks"));
            boolean sticks=InventoryAutomation.count(Items.STICK)>=2;
            if(tier==1&&(InventoryAutomation.countResource("logs")>=3||planks>=9))directJob="bootstrap";
            else if(table&&sticks&&tier==2&&InventoryAutomation.count(Items.COBBLESTONE)>=11)directJob="stone-tools";
            else if(table&&sticks&&tier==3&&InventoryAutomation.count(Items.IRON_INGOT)>=3)directJob="iron-tools";
            else if(table&&sticks&&tier==4&&InventoryAutomation.count(Items.DIAMOND)>=3)directJob="diamond-pickaxe";
            else directJob="";
        }
        inventory.requireOrdinaryMiningDrops(ordinaryDrops);
        if(!directJob.isEmpty()) {
            var result=inventory.tickTransfer();if(result==null)result=inventory.tick(directJob,30);
            if(result.done()){reset();return new InventoryAutomation.Result(true,false,"Replacement tool crafted from owned materials");}
            if(result.blocked()){directJob="";inventory.stop();}
            else return new InventoryAutomation.Result(false,false,"Replacing tool from owned supplies: "+result.detail());
        }
        if(index>=stages.size())return new InventoryAutomation.Result(false,true,"Tool crafting finished without a usable required pickaxe. Check the server inventory.");
        Stage stage=stages.get(index);
        if(!stage.craft.isEmpty()) {
            inventory.requireOrdinaryMiningDrops(ordinaryDrops || index < stages.size()-1);
            var result=inventory.tickTransfer();if(result==null)result=inventory.tick(stage.craft,30);
            if(result.done()){index++;dispatched=false;inventory.stop();}
            return new InventoryAutomation.Result(false,result.blocked(),"Replacing mining tool: "+result.detail());
        }
        int count=InventoryAutomation.countResource(stage.resource);
        if(count>=stage.count) {navigator.cancel();index++;dispatched=false;return new InventoryAutomation.Result(false,false,"Tool recovery materials verified: "+stage.resource);}
        int requiredTier=ResourceCatalog.get(stage.resource).pickaxeTier();
        boolean resourceDrops=NativeMiner.requiresOrdinaryDrops(ResourceCatalog.get(stage.resource).blockIds());
        if(requiredTier>0 && !hasTier(requiredTier,resourceDrops)) {
            navigator.cancel();inventory.stop();dispatched=false;
            // Revisit the complete prerequisite chain using the inventory now present.
            // Retrying only the pickaxe recipe cannot recover its consumed wood/stone.
            index=requiredTier==1?0:requiredTier==2?2:requiredTier==3?4:7;
            return new InventoryAutomation.Result(false,false,"A prerequisite pickaxe wore out; recovering its actual materials and crafting stages");
        }
        inventory.requireOrdinaryMiningDrops(resourceDrops);
        if(!dispatched) {
            if(ResourceCatalog.get(stage.resource).pickaxeTier()>0) {
                var preparation=inventory.tick("mining-hotbar",30);
                if(!preparation.done())return preparation;
            }
            navigator.clearStatus();
            if(!navigator.mineByName(stage.count,ResourceCatalog.get(stage.resource).blockIds()))return new InventoryAutomation.Result(false,true,navigator.status().message());
            dispatched=true;
        }
        if(navigator.status().failed()){dispatched=false;return new InventoryAutomation.Result(false,true,"Tool recovery: "+navigator.status().message());}
        return new InventoryAutomation.Result(false,false,"Replacing mining tool: gathering "+stage.resource+" "+count+"/"+stage.count);
    }
    private boolean nearbyTable(){var mc=MinecraftClient.getInstance();if(mc.world==null||mc.player==null)return false;var origin=mc.player.getBlockPos();for(int x=-4;x<=4;x++)for(int y=-2;y<=2;y++)for(int z=-4;z<=4;z++){var pos=origin.add(x,y,z);if(mc.world.isChunkLoaded(pos)&&mc.world.getBlockState(pos).isOf(net.minecraft.block.Blocks.CRAFTING_TABLE))return true;}return false;}
    public void reset(){
        boolean wasActive=active();
        if(ownsRoute())navigator.cancel();
        if(wasActive)inventory.stop();
        inventory.requireOrdinaryMiningDrops(false);
        targetTier=index=0;dispatched=false;ordinaryDrops=false;directJob="";stages=List.of();
    }
}
