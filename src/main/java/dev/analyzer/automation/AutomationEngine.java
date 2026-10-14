package dev.analyzer.automation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.analyzer.AnalyzerClient;
import dev.analyzer.core.AnalyzerCore;
import dev.analyzer.ui.AutomationScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DownloadingTerrainScreen;
import net.minecraft.item.Items;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.WorldSavePath;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Observable local task queue. Start/resume are explicit; worlds and inventory determine completion. */
public final class AutomationEngine implements AutomationScreen.Controller {
    public static final AutomationEngine INSTANCE = new AutomationEngine();
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<String> CONFLICTS = List.of("auto-walk","fly","speed","scaffold","tower","nuker","vein-miner","auto-tool","auto-armor","auto-weapon","auto-eat","auto-gapple","chest-stealer","refill","kill-aura","crystal-aura","anchor-aura","surround","auto-trap","auto-web","hole-filler","auto-fish","auto-clicker","trigger-bot","aim-assist");
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final AutomationNavigator bridge = new AutomationNavigator();
    private final InventoryAutomation inventory = new InventoryAutomation(bridge);
    private final SurvivalGuard guard = new SurvivalGuard();
    private final DimensionAutomation dimensions = new DimensionAutomation(bridge, inventory);
    private final ToolRecovery tools = new ToolRecovery(bridge,inventory);
    private AnalyzerCore core;
    private TaskPlanner.Plan plan;
    private String state="READY",detail="Describe a task, review its stages, then press Start.",worldKey="";
    private String[] stepDetails=new String[0];
    private int current,ticks,lastProgress,lastPersist,startedAt;
    private Object world;
    private double bestDistance=Double.MAX_VALUE,lastBridgeProgress=-1;
    private int bestCount=-1,arrivalY=Integer.MIN_VALUE;
    private long lastClicks;
    private boolean dispatched, activeTask;
    private boolean returnBlocked;
    private String blockedDetail="";
    private BlockPos shelterOrigin;
    private ImportedSchematic imported;
    private BlockPos importedOrigin;
    private int transitionTicks;
    private int portalScreenGrace,portalLoadingTicks;
    private String supplyResource="";
    private int supplyGoal;
    private boolean materialCrafting;
    private Item supplyItem;
    private Path checkpoint;
    private final Map<String,Boolean> suspended=new LinkedHashMap<>();
    private record Saved(TaskPlanner.Plan plan,int current,String worldKey,String detail,int arrivalY,BlockPosData shelter,Map<String,Boolean> suspended) { }
    private record BlockPosData(int x,int y,int z) { }
    private AutomationEngine() { }
    public static void register(AnalyzerCore core) {
        INSTANCE.core=core;
        INSTANCE.checkpoint=FabricLoader.getInstance().getConfigDir().resolve("analyzer/automation-task.json");
        INSTANCE.restore();
        // A stored toggle never starts a plan or resumes movement after launching Minecraft.
        core.setEnabled("expedition",false);
        ClientTickEvents.END_CLIENT_TICK.register(client->INSTANCE.tick());
        HudRenderCallback.EVENT.register((context,counter)->{
            if(INSTANCE.activeTask && (INSTANCE.protecting() || INSTANCE.state.equals("BLOCKED")))
                SafetyHud.render(context,core,INSTANCE.detail,INSTANCE.state.equals("BLOCKED") || INSTANCE.state.equals("SAFETY_HOLD"));
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client->{INSTANCE.pause();INSTANCE.stopOwned();INSTANCE.persist();});
    }
    public boolean available(){return bridge.available();}
    public boolean isRunning(){return activeTask && (state.equals("RUNNING") || protecting());}
    private boolean protecting(){return state.equals("PROTECTING") || state.equals("SAFETY_HOLD");}
    public int currentStep(){return current;}
    public String state(){return state;}
    public String detail(){return detail;}
    public long inventoryClicks(){return inventory.clicks();}
    public AutomationNavigator.Status pathStatus(){return bridge.status();}
    public SurvivalGuard.Diagnostics safetyDiagnostics(){return guard.diagnostics();}
    public AutomationNavigator.SafetyProfileStatus routingSafety(){return bridge.safetyProfileStatus();}
    private record SchematicRequest(String file,String sha256) { }
    private record ReviewedBuild(ImportedSchematic schematic,BlockPos origin) { }
    private ReviewedBuild reviewedBuild(TaskPlanner.Plan candidate) {
        for(var step:candidate.steps())if(step.action()==TaskPlanner.Action.BUILD_SCHEMATIC) {
            try {
                SchematicRequest request=JSON.fromJson(new String(Base64.getUrlDecoder().decode(step.resource()),StandardCharsets.UTF_8),SchematicRequest.class);
                var data=SchematicImporter.load(Path.of(request.file));
                if(!data.sha256().equals(request.sha256))throw new IllegalArgumentException("The schematic file changed after review. Stop and review it again.");
                return new ReviewedBuild(ImportedSchematic.from(data),new BlockPos(step.x(),step.y(),step.z()));
            } catch(java.io.IOException error){throw new IllegalArgumentException("Cannot reopen the reviewed schematic: "+error.getMessage());}
        }
        return null;
    }
    private void protectBuild(ReviewedBuild selected) {
        imported=selected==null?null:selected.schematic();importedOrigin=selected==null?null:selected.origin();
        var bounds=selected==null?null:new net.minecraft.util.math.Box(importedOrigin.getX(),importedOrigin.getY()-1,importedOrigin.getZ(),
                importedOrigin.getX()+imported.widthX(),importedOrigin.getY()+imported.heightY(),importedOrigin.getZ()+imported.lengthZ());
        bridge.setMiningExclusion(bounds);inventory.setPlacementExclusion(bounds);
    }
    @Override public TaskPlanner.Plan schematicPlan(Path file,TaskPlanner.Options options,boolean autoSite) {
        if(mc.world==null || mc.player==null)throw new IllegalArgumentException("Join a world to review a build site.");
        try {
            SchematicData data=SchematicImporter.load(file);
            ImportedSchematic schematic=ImportedSchematic.from(data);
            BlockPos origin=new BlockPos(options.travelX(),options.travelY(),options.travelZ());
            if(autoSite) {
                var site=AutoSiteSelector.choose(mc.world,mc.player.getBlockPos(),schematic,(int)core.getNumber("expedition","site-radius"),(int)core.getNumber("expedition","avoid-range"));
                origin=site.origin().orElseThrow(()->new IllegalArgumentException(site.message()));
            }
            String problem=schematic.siteProblem(mc.world,origin);
            if(!problem.isEmpty())throw new IllegalArgumentException(problem);
            String resource=Base64.getUrlEncoder().withoutPadding().encodeToString(JSON.toJson(new SchematicRequest(file.toAbsolutePath().normalize().toString(),data.sha256())).getBytes(StandardCharsets.UTF_8));
            var requirements=new ArrayList<String>();
            requirements.add("Reviewed site: "+origin.toShortString()+". The source file hash and actual footprint are checked again before building.");
            for(var entry:schematic.requiredItems().entrySet())requirements.add(entry.getValue()+" × "+entry.getKey().getName().getString()+"; owned materials are credited, supported sources can be restocked in batches.");
            requirements.addAll(data.warnings());
            var stages=new ArrayList<TaskPlanner.Step>();
            int materialIndex=0;
            var remaining=schematic.remainingItems(mc.world,origin);
            long stacks=remaining.entrySet().stream().mapToLong(entry->(entry.getValue()+new net.minecraft.item.ItemStack(entry.getKey()).getMaxCount()-1)/new net.minecraft.item.ItemStack(entry.getKey()).getMaxCount()).sum();
            if(stacks>32)throw new IllegalArgumentException("This build needs "+stacks+" stacks before construction. Split it into smaller regions; automatic supply-depot storage is not implemented.");
            for(var entry:remaining.entrySet()) {
                if(entry.getValue()>4096)throw new IllegalArgumentException("A material needs more than 4096 items. Split this schematic into smaller build regions before reviewing.");
                stages.add(new TaskPlanner.Step("material-"+(materialIndex++),TaskPlanner.Action.SUPPLY_MATERIAL,"Gather "+entry.getValue()+" "+entry.getKey().getName().getString(),Registries.ITEM.getId(entry.getKey()).toString(),entry.getValue(),origin.getX(),origin.getY(),origin.getZ()));
            }
            stages.add(new TaskPlanner.Step("schematic",TaskPlanner.Action.BUILD_SCHEMATIC,"Build at "+origin.toShortString(),resource,1,origin.getX(),origin.getY(),origin.getZ()));
            if(stages.size()>64)throw new IllegalArgumentException("This palette needs more than 63 separate materials. Split it into smaller build regions.");
            requirements.add("All remaining materials are gathered before construction. They must fit in the player inventory; automatic supply-depot storage is not implemented.");
            return new TaskPlanner.Plan("Build "+data.name(),data.width()+" × "+data.height()+" × "+data.length()+" · "+schematic.requiredBlocks()+" blocks · "+data.format(),stages,requirements);
        } catch(java.io.IOException error) { throw new IllegalArgumentException("Cannot import schematic: "+error.getMessage()); }
    }
    @Override public AutomationScreen.View snapshot() {
        List<AutomationScreen.StepView> stages=new ArrayList<>();
        if(plan!=null)for(int i=0;i<plan.steps().size();i++)stages.add(new AutomationScreen.StepView(plan.steps().get(i).label(),i<current?"Done":i==current?state:"Queued",i<stepDetails.length?stepDetails[i]:""));
        return new AutomationScreen.View(plan==null?"Analyzer Automation":plan.title(),state,detail,current,List.copyOf(stages),isRunning(),state.equals("PAUSED")||state.equals("BLOCKED"),available());
    }
    @Override public void submit(TaskPlanner.Plan requested) {
        validate(requested);
        if(mc.player==null||mc.world==null)throw new IllegalArgumentException("Join a world before starting a task.");
        if(!available())throw new IllegalArgumentException(bridge.status().message());
        if(bridge.manualTaskActive())throw new IllegalArgumentException("Release manual movement before starting an Analyzer plan.");
        ReviewedBuild selected=reviewedBuild(requested);
        stopOwned();
        clearIdleInterruption();
        if(!inventory.recovery().isBlank()){state="STOPPED";detail=inventory.recovery();persist();throw new IllegalArgumentException(detail);}
        plan=requested;current=0;stepDetails=new String[plan.steps().size()];Arrays.fill(stepDetails,"");
        world=mc.world;worldKey=worldKey();arrivalY=Integer.MIN_VALUE;shelterOrigin=null;imported=null;importedOrigin=null;supplyResource="";supplyItem=null;transitionTicks=0;suspendConflicts();
        protectBuild(selected);
        activeTask=true;startedAt=ticks;state="RUNNING";detail="Starting " + plan.steps().getFirst().label();core.setEnabled("expedition",true);startSafetyProfile();resetProgress();persist();
    }
    @Override public void pause(){if(activeTask && !state.equals("PAUSED")){guard.cleanup();bridge.endSafetyProfile();bridge.pause();returnBlocked=false;blockedDetail="";state="PAUSED";detail="Paused. Resume continues the current stage.";persist();}}
    @Override public void resume() {
        if(plan==null||current>=plan.steps().size()){detail="Choose and start a new task.";return;}
        if(mc.world==null||mc.player==null){detail="Join the original world before resuming.";return;}
        if(!worldKey.equals(worldKey())){state="BLOCKED";detail="This plan belongs to a different world or dimension. Start a new plan for this location.";return;}
        if(!available()){state="BLOCKED";detail=bridge.status().message();return;}
        if(bridge.manualTaskActive()){detail="Release manual movement before resuming this plan.";persist();return;}
        try{protectBuild(reviewedBuild(plan));}catch(RuntimeException error){state="BLOCKED";detail=error.getMessage();persist();return;}
        clearIdleInterruption();
        world=mc.world;suspendConflicts();core.setEnabled("expedition",true);activeTask=true;startedAt=ticks;state="RUNNING";
        startSafetyProfile();
        if(bridge.status().paused())bridge.resume();
        resetProgress();detail="Resuming " + plan.steps().get(current).label();persist();
    }
    @Override public void stop(){stopOwned();state="STOPPED";detail=inventory.recovery().isBlank()?"Stopped. Owned pathing and inventory actions have been released.":inventory.recovery();if(core!=null)core.setEnabled("expedition",false);persist();}
    private void stopOwned(){activeTask=false;guard.cleanup();dimensions.stop();tools.reset();AutomationMotion.reset();bridge.endSafetyProfile();bridge.cancel();bridge.setMiningExclusion(null);inventory.setPlacementExclusion(null);inventory.stop();dispatched=false;supplyResource="";supplyGoal=0;materialCrafting=false;portalScreenGrace=portalLoadingTicks=transitionTicks=0;returnBlocked=false;blockedDetail="";restoreConflicts();}
    private void startSafetyProfile(){if(core.getBoolean("expedition","safe-routing"))bridge.beginSafetyProfile();else bridge.endSafetyProfile();}
    /** Only an explicit Start/Resume may retry after external control has ended. */
    private void clearIdleInterruption(){if((bridge.status().state()==AutomationNavigator.State.INTERRUPTED || bridge.status().state()==AutomationNavigator.State.FAILED) && !bridge.status().owned())bridge.clearStatus();}
    private void suspendConflicts(){
        java.util.Set<String> actions=new java.util.HashSet<>(CONFLICTS);
        for(var module:dev.analyzer.core.GameplayModules.definitions())actions.add(module.id());
        for(var module:dev.analyzer.core.UtilityModules.definitions())actions.add(module.id());
        for(String id:actions)if(hasModule(id)&&core.isEnabled(id)){suspended.putIfAbsent(id,true);core.setEnabled(id,false);}
    }
    private void restoreConflicts(){if(core!=null){for(var entry:suspended.entrySet())if(hasModule(entry.getKey())&&!core.isEnabled(entry.getKey()))core.setEnabled(entry.getKey(),entry.getValue());}suspended.clear();}

    private void block(String reason){guard.cleanup();bridge.endSafetyProfile();bridge.pause();returnBlocked=false;blockedDetail="";state="BLOCKED";detail=reason;if(current<stepDetails.length)stepDetails[current]=reason;persist();}
    private void resetProgress(){lastProgress=ticks;bestCount=-1;bestDistance=Double.MAX_VALUE;lastBridgeProgress=-1;lastClicks=inventory.clicks();dispatched=bridge.status().active();}
    private void progress(){lastProgress=ticks;}
    private void tick() {
        ticks++;
        bridge.configureMotion(new AutomationNavigator.MotionProfile(core.getBoolean("expedition","smooth-turns"),core.getNumber("expedition","turn-speed"),core.getNumber("expedition","turn-acceleration"),core.getNumber("expedition","movement-angle"),(int)core.getNumber("expedition","break-delay"),(int)core.getNumber("expedition","place-delay")));
        inventory.actionDelayTicks((int)core.getNumber("expedition","place-delay"));
        try { bridge.tick(); }
        catch(RuntimeException | LinkageError error) {
            bridge.cancel();inventory.stop();
            if(activeTask)block("Native task paused after an execution error. Check the local game log before resuming.");
            AnalyzerClient.LOGGER.warn("Native automation worker failed",error);return;
        }
        // Paused live tasks still own pathing, workstations and suspended modules.
        // Restored checkpoints are inactive until the user explicitly resumes.
        if(activeTask && !core.isEnabled("expedition")){stop();return;}
        if(activeTask && (mc.world==null||mc.player==null||mc.interactionManager==null)){
            if(dimensions.awaitingTransition() && mc.getNetworkHandler()!=null && ++transitionTicks<=200){detail="Waiting for the authorized portal transfer.";return;}
            stopOwned();block("Disconnected. Join the original world and explicitly resume.");return;
        }
        if(activeTask && world!=mc.world){
            String newKey=worldKey();
            if(plan!=null && current<plan.steps().size() && plan.steps().get(current).action()==TaskPlanner.Action.DIMENSION
                    && session(worldKey).equals(session(newKey)) && dimensions.expectsTransition(dimension(worldKey),dimension(newKey))) {
                guard.cleanup();bridge.cancel();bridge.clearStatus();inventory.stop();dimensions.acceptTransition(dimension(newKey));world=mc.world;worldKey=newKey;transitionTicks=0;portalScreenGrace=200;dispatched=false;resetProgress();persist();
            } else {stopOwned();block("The world or an unexpected dimension changed. Review the plan before resuming.");return;}
        }
        if(activeTask && !mc.player.isAlive()){stopOwned();block("The player died. Review inventory and position before resuming.");return;}
        // A live blocked task still watches for danger. User Pause and restored checkpoints do not act.
        if(!activeTask || state.equals("PAUSED"))return;
        if(mc.currentScreen instanceof DownloadingTerrainScreen && (dimensions.awaitingTransition() || portalScreenGrace>0)) {
            if(++portalLoadingTicks>200){pause();detail="Portal terrain loading did not finish. Resume after the world is ready.";persist();}
            else{detail="Waiting for the authorized portal terrain to finish loading.";progress();}
            return;
        }
        portalLoadingTicks=0;if(portalScreenGrace>0)portalScreenGrace--;
        if(ticks-startedAt>10 && core.getBoolean("expedition","pause-on-input") && bridge.manualInputActive()) {
            pause();detail="Paused for manual movement or interaction. Resume when ready.";persist();return;
        }
        if(mc.player.isSpectator()){block("Automation cannot act in Spectator mode.");return;}
        if(mc.getOverlay()!=null){pause();detail="Paused for a resource reload. Resume after loading finishes.";persist();return;}
        if(core.getBoolean("expedition","pause-on-menu") && mc.currentScreen!=null && !inventory.ownsScreen()){pause();detail="Paused while a menu is open. Press Resume when ready.";persist();return;}
        for(String id:CONFLICTS)if(hasModule(id)&&core.isEnabled(id)){block("Disable " + core.definition(id).name() + " before resuming; it can interfere with this task.");return;}
        if(bridge.status().state()==AutomationNavigator.State.INTERRUPTED){dispatched=false;block(bridge.status().message());return;}
        if(bridge.manualTaskActive()){block("Manual movement took control. Release it before resuming this plan.");return;}
        if(!state.equals("BLOCKED") && core.getBoolean("expedition","safe-routing") != bridge.safetyProfileStatus().enabled())startSafetyProfile();
        var protection=guard.tick(core,bridge,inventory);
        if(protection.intervening() || protection.fatalBlock()){
            if(!protecting()){returnBlocked=state.equals("BLOCKED");blockedDetail=returnBlocked?detail:"";}
            String next=protection.fatalBlock()?"SAFETY_HOLD":"PROTECTING";
            boolean changed=!state.equals(next);state=next;detail=protection.message();progress();
            if(changed){AnalyzerClient.LOGGER.info("Automation protection: {}",detail);persist();}
            return;
        }
        if(protection.resumeReady()){
            if(returnBlocked){state="BLOCKED";detail=blockedDetail;returnBlocked=false;blockedDetail="";bridge.endSafetyProfile();persist();return;}
            if(bridge.status().paused())bridge.resume();state="RUNNING";resetProgress();detail=protection.message();persist();
        } else if(protecting()) {
            // A menu or Creative mode can stop the guard; never leave a stale protection state.
            pause();detail="Protection paused because it cannot act in the current screen or game mode. Close the screen and press Resume when ready.";persist();return;
        }
        if(state.equals("BLOCKED"))return;
        if(mc.player.getHealth()<core.getNumber("expedition","min-health")){block("Low health: " + Math.round(mc.player.getHealth()) + " HP. Recover, then resume.");return;}
        if(!mc.player.getAbilities().creativeMode && mc.player.getHungerManager().getFoodLevel()<core.getNumber("expedition","min-hunger")){block("Low hunger. Eat, then resume.");return;}
        if(ticks-lastProgress>core.getNumber("expedition","stuck-timeout")*20){block("No measurable progress for " + (int)core.getNumber("expedition","stuck-timeout") + " seconds. Check the route, tools or workstation, then resume.");return;}
        if(current>=plan.steps().size()){completePlan();return;}
        TaskPlanner.Step step=plan.steps().get(current);
        try {
            if(step.id().startsWith("portal-") && (step.action()==TaskPlanner.Action.MINE || step.action()==TaskPlanner.Action.CRAFT)
                    && (mc.world.getRegistryKey().getValue().getPath().equals("the_nether") || portalStockReady())) {
                advance("Actual dimension or owned complete portal kit covers this prerequisite");return;
            }
            switch(step.action()) {
                case TRAVEL -> travel(step);
                case MINE -> mine(step);
                case BUILD_SHELTER -> build(step);
                case BUILD_SCHEMATIC -> buildImported(step);
                case SUPPLY_MATERIAL -> materialStep(step);
                case DIMENSION -> dimensionStep(step);
                case CRAFT, EQUIP, ENCHANT -> inventory(step);
            }
        } catch(IllegalArgumentException error){block(Objects.requireNonNullElse(error.getMessage(),"The task requirements changed. Stop and review the plan again."));}
        catch(RuntimeException|LinkageError error){block("Task paused: " + error.getClass().getSimpleName() + ". Check the local game log.");AnalyzerClient.LOGGER.warn("Automation stage failed: {}",step.id(),error);}
        if(ticks-lastPersist>=100){persist();lastPersist=ticks;}
    }
    private boolean hasModule(String id){return core.modules().stream().anyMatch(m->m.id().equals(id));}
    private void travel(TaskPlanner.Step step) {
        boolean horizontal=step.id().equals("escape");
        int y=step.id().equals("home")&&arrivalY!=Integer.MIN_VALUE?arrivalY:step.y();
        double distance=Math.hypot(mc.player.getX()-(step.x()+0.5),mc.player.getZ()-(step.z()+0.5));
        if(!horizontal)distance=Math.hypot(distance,mc.player.getY()-y);
        if(distance<bestDistance-0.25){bestDistance=distance;progress();}
        if(!dispatched){bridge.clearStatus();boolean accepted=horizontal?bridge.travelXZ(step.x(),step.z()):bridge.travelXYZ(step.x(),y,step.z());if(!accepted){block(bridge.status().message());return;}dispatched=true;}
        var path=bridge.status();detail=step.label()+" · " + Math.round(distance)+" blocks remaining";
        if(path.failed()){dispatched=false;block(path.message());return;}
        if(path.complete()) {if(step.id().equals("escape"))arrivalY=mc.player.getBlockY();advance("Reached the actual target coordinates");}
    }
    private void mine(TaskPlanner.Step step) {
        observeActionProgress();
        // Completed equipment credits prerequisite stages when a kit task is already satisfied.
        int count=InventoryAutomation.countResource(step.resource());
        if(count>bestCount){bestCount=count;progress();}
        if(count>=step.count()){advance("Inventory verified: " + count + "/" + step.count());return;}
        ResourceCatalog.Resource resource=ResourceCatalog.get(step.resource());
        boolean ordinaryDrops=NativeMiner.requiresOrdinaryDrops(resource.blockIds());
        if(!ToolRecovery.hasTier(resource.pickaxeTier(),ordinaryDrops)){
            var recovery=tools.tick(resource.pickaxeTier(),ordinaryDrops);dispatched=false;detail=recovery.detail();
            if(inventoryFingerprint()!=lastInventoryFingerprint){lastInventoryFingerprint=inventoryFingerprint();progress();}
            if(recovery.blocked())block(recovery.detail());return;
        }
        if(tools.active())dispatched=false;
        tools.reset();
        if(!dispatched){
            if(resource.pickaxeTier()>0){var preparation=inventory.tick("mining-hotbar",30);if(preparation.blocked()){block(preparation.detail());return;}if(!preparation.done()){detail=preparation.detail();return;}}
            bridge.clearStatus();boolean accepted=step.resource().equals("logs")?bridge.mineByName(step.count(),blocks(step.resource())):bridge.mineForItem(step.count(),"minecraft:"+step.resource(),blocks(step.resource()));
            if(!accepted){block(bridge.status().message());return;}dispatched=true;
        }
        var path=bridge.status();detail=step.label()+" · " + count+"/"+step.count()+" in inventory";
        if(path.failed()||path.complete()){dispatched=false;if(count>=step.count())advance("Required items received");else block(path.message()+". Inventory is " + count+"/"+step.count());}
    }
    private String[] blocks(String resource){return ResourceCatalog.get(resource).blockIds();}
    private boolean portalStockReady(){return InventoryAutomation.count(Items.OBSIDIAN)>=10&&InventoryAutomation.count(Items.COBBLESTONE)>=13&&InventoryAutomation.count(stack->stack.isOf(Items.FLINT_AND_STEEL)&&InventoryAutomation.durableTool(stack.getItem()))>0;}
    private void inventory(TaskPlanner.Step step) {
        String task=switch(step.action()){case EQUIP->step.resource().equals("netherite-armor")?"netherite-equip":"equip";case ENCHANT->"diamond-armor";default->step.resource();};
        var result=inventory.tickTransfer();if(result==null)result=inventory.tick(task,30);
        detail=result.detail();
        // Slot clicks alone are not evidence of durable progression: inventory changes drive the watchdog too.
        long fingerprint=inventoryFingerprint();
        if(fingerprint!=lastInventoryFingerprint){lastInventoryFingerprint=fingerprint;progress();}
        if(result.blocked()){block(result.detail());return;}
        if(result.done())advance(result.detail());
    }
    private long lastInventoryFingerprint;
    private net.minecraft.util.math.Vec3d lastActionPosition;
    private void observeActionProgress(){
        long fingerprint=inventoryFingerprint();
        if(fingerprint!=lastInventoryFingerprint){lastInventoryFingerprint=fingerprint;progress();}
        if(bridge.status().owned() && (lastActionPosition==null || mc.player.getPos().squaredDistanceTo(lastActionPosition)>.25)) {lastActionPosition=mc.player.getPos();progress();}
    }
    private void dimensionStep(TaskPlanner.Step step) {
        observeActionProgress();
        var result=dimensions.tick(step.resource(),(int)core.getNumber("expedition","place-delay"));
        detail=result.detail();
        if(result.blocked()){block(result.detail());return;}
        if(result.done()){dimensions.stop();advance(result.detail());}
    }
    private static String session(String key){int at=key.lastIndexOf('|');return at<0?key:key.substring(0,at);}
    private static String dimension(String key){int at=key.lastIndexOf('|');return at<0?"":key.substring(at+1);}
    private record RawSupply(Item item,int target,String[] blocks) { }
    private RawSupply missingRaw(Item item,int target,int depth) {
        if(depth>12 || InventoryAutomation.count(item)>=target)return null;
        var spec=BuildingMaterialCatalog.find(Registries.ITEM.getId(item).toString()).orElse(null);
        if(spec==null)throw new IllegalArgumentException("Supply "+target+" "+item.getName().getString()+". Its gathering or crafting recipe is not supported yet.");
        if(spec.job()==BuildingMaterialCatalog.Job.DIRECT)return new RawSupply(item,target,spec.blockIds());
        if(spec.job()==BuildingMaterialCatalog.Job.SMELT && !inventory.workstationAvailable(net.minecraft.block.Blocks.FURNACE,Items.FURNACE)) {
            RawSupply station=missingRaw(Items.FURNACE,1,depth+1);if(station!=null)return station;
        }
        if((spec.width()==3 || spec.job()==BuildingMaterialCatalog.Job.SMELT && !inventory.workstationAvailable(net.minecraft.block.Blocks.FURNACE,Items.FURNACE))
                && !inventory.workstationAvailable(net.minecraft.block.Blocks.CRAFTING_TABLE,Items.CRAFTING_TABLE)) {
            RawSupply station=missingRaw(Items.CRAFTING_TABLE,1,depth+1);if(station!=null)return station;
        }
        int batches=(target-InventoryAutomation.count(item)+spec.outputPerRecipe()-1)/spec.outputPerRecipe();
        for(var ingredient:spec.ingredients()) {
            Item input=Registries.ITEM.get(net.minecraft.util.Identifier.of("minecraft",ingredient.itemId()));
            int reserve=plan==null?0:plan.steps().stream().filter(stage->stage.action()==TaskPlanner.Action.SUPPLY_MATERIAL&&stage.resource().equals(Registries.ITEM.getId(input).toString())).mapToInt(TaskPlanner.Step::count).max().orElse(0);
            int needed=Math.min(4096,reserve+Math.min(64,Math.max(ingredient.count(),batches*ingredient.count())));
            RawSupply raw=missingRaw(input,needed,depth+1);if(raw!=null)return raw;
        }
        if(spec.job()==BuildingMaterialCatalog.Job.SMELT && InventoryAutomation.count(Items.COAL)==0)
            return new RawSupply(Items.COAL,Math.min(16,Math.max(1,(target+7)/8)),ResourceCatalog.get("coal").blockIds());
        return null;
    }
    private void materialStep(TaskPlanner.Step step) {
        observeActionProgress();
        Item wanted=Registries.ITEM.get(net.minecraft.util.Identifier.of(step.resource()));
        if(wanted==Items.AIR){block("Unsupported schematic material: "+step.resource());return;}
        int count=InventoryAutomation.count(wanted);
        if(count>bestCount){bestCount=count;progress();}
        if(materialCrafting){
            var result=inventory.tickTransfer();if(result==null)result=inventory.tickMaterial(wanted,step.count());
            detail=result.detail();
            if(result.done()){materialCrafting=false;return;}
            if(result.blocked()){
                inventory.stop();materialCrafting=false;
                if(!inventory.recovery().isBlank()){block(inventory.recovery());return;}
                if(missingRaw(wanted,step.count(),0)==null)block(result.detail());
            }
            return;
        }
        if(count>=step.count()){advance("All "+step.count()+" "+wanted.getName().getString()+" verified in inventory");return;}
        if(!supplyResource.isEmpty()) {
            Item raw=Registries.ITEM.get(net.minecraft.util.Identifier.of(supplyResource));
            String[] rawBlocks=BuildingMaterialCatalog.find(supplyResource).map(BuildingMaterialCatalog.Material::blockIds).orElse(new String[0]);
            int rawTier=Arrays.stream(rawBlocks).map(net.minecraft.util.Identifier::of).map(Registries.BLOCK::get).mapToInt(NativeMiner::minimumTier).max().orElse(0);
            boolean ordinaryDrops=NativeMiner.requiresOrdinaryDrops(rawBlocks);
            if(rawTier>0&&!ToolRecovery.hasTier(rawTier,ordinaryDrops)) {
                bridge.cancel();supplyResource="";dispatched=false;
                var recovery=tools.tick(rawTier,ordinaryDrops);detail=recovery.detail();if(recovery.blocked())block(recovery.detail());return;
            }
            if(InventoryAutomation.count(raw)>=supplyGoal){bridge.cancel();bridge.clearStatus();supplyResource="";dispatched=false;inventory.stop();return;}
            if(bridge.status().failed()){dispatched=false;supplyResource="";block(bridge.status().message());return;}
            detail="Preparing "+wanted.getName().getString()+": "+InventoryAutomation.count(raw)+"/"+supplyGoal+" "+raw.getName().getString();return;
        }
        RawSupply raw=missingRaw(wanted,step.count(),0);
        if(raw!=null) {
            int tier=Arrays.stream(raw.blocks).map(net.minecraft.util.Identifier::of).map(Registries.BLOCK::get).mapToInt(NativeMiner::minimumTier).max().orElse(0);
            boolean ordinaryDrops=NativeMiner.requiresOrdinaryDrops(raw.blocks);
            if(tier>0 && !ToolRecovery.hasTier(tier,ordinaryDrops)){var recovery=tools.tick(tier,ordinaryDrops);detail=recovery.detail();if(recovery.blocked())block(recovery.detail());return;}
            tools.reset();
            var hotbar=inventory.tick("mining-hotbar",30);
            if(tier>0&&!hotbar.done()){detail=hotbar.detail();if(hotbar.blocked())block(hotbar.detail());return;}
            inventory.stop();bridge.clearStatus();
            if(!bridge.mineForItem(raw.target,Registries.ITEM.getId(raw.item).toString(),raw.blocks)){block(bridge.status().message());return;}
            supplyResource=Registries.ITEM.getId(raw.item).toString();supplyGoal=raw.target;dispatched=true;detail="Gathering schematic supplies";return;
        }
        materialCrafting=true;
        var result=inventory.tickTransfer();if(result==null)result=inventory.tickMaterial(wanted,step.count());
        detail=result.detail();if(result.blocked()){block(result.detail());return;}
        if(inventoryFingerprint()!=lastInventoryFingerprint){lastInventoryFingerprint=inventoryFingerprint();progress();}
    }
    private void buildImported(TaskPlanner.Step step) {
        try {
            if(imported==null) {
                SchematicRequest request=JSON.fromJson(new String(Base64.getUrlDecoder().decode(step.resource()),StandardCharsets.UTF_8),SchematicRequest.class);
                var data=SchematicImporter.load(Path.of(request.file));
                if(!data.sha256().equals(request.sha256)){block("The schematic file changed after review. Stop and review it again.");return;}
                imported=ImportedSchematic.from(data);importedOrigin=new BlockPos(step.x(),step.y(),step.z());
            }
            var observed=imported.progress(mc.world,importedOrigin);
            if(observed.complete()){advance("Every selected schematic block state verified in the world");return;}
            if(observed.fraction()>lastBridgeProgress){lastBridgeProgress=observed.fraction();progress();}
            if(!dispatched) {
                ReviewedBuild source=reviewedBuild(plan);
                if(source==null||!source.schematic().data().sha256().equals(imported.data().sha256())){block("The reviewed schematic source changed before construction. Stop and review it again.");return;}
                String problem=imported.siteProblem(mc.world,importedOrigin);if(!problem.isEmpty()){block(problem);return;}
                for(var entry:imported.remainingItems(mc.world,importedOrigin).entrySet())if(InventoryAutomation.count(entry.getKey())<entry.getValue()){
                    int cursor=-1;String itemId=Registries.ITEM.getId(entry.getKey()).toString();
                    for(int index=0;index<current;index++)if(plan.steps().get(index).action()==TaskPlanner.Action.SUPPLY_MATERIAL&&plan.steps().get(index).resource().equals(itemId)){cursor=index;break;}
                    if(cursor<0){block("Need "+entry.getValue()+" "+entry.getKey().getName().getString()+" before construction. Review the changed supplies.");return;}
                    bridge.cancel();inventory.stop();tools.reset();supplyResource="";supplyGoal=0;current=cursor;dispatched=false;resetProgress();
                    detail="Reconciling every material before construction: "+entry.getKey().getName().getString();persist();return;
                }
                bridge.clearStatus();if(!bridge.buildSchematic(imported.data().name(),imported,importedOrigin)){block(bridge.status().message());return;}dispatched=true;
            }
            detail="Building "+imported.data().name()+" · "+observed.placed()+"/"+observed.required()+" blocks verified";
            if(bridge.status().failed()){dispatched=false;block(bridge.status().message());}
        } catch(java.io.IOException error){block("Cannot reopen the reviewed schematic: "+error.getMessage());}
    }
    private long inventoryFingerprint(){long hash=1;for(int i=0;i<mc.player.getInventory().size();i++){var s=mc.player.getInventory().getStack(i);hash=31*hash+s.getItem().hashCode();hash=31*hash+s.getCount();hash=31*hash+s.getDamage();hash=31*hash+s.getComponents().hashCode();}return hash;}
    private void build(TaskPlanner.Step step) {
        if(shelterOrigin==null) {
            int y=arrivalY==Integer.MIN_VALUE?step.y():arrivalY;
            // Explicit footprint is offset from the arrival point, so the player does not start inside a wall.
            shelterOrigin=new BlockPos(step.x()+3,y-1,step.z()+3);
            if(core.getBoolean("expedition","auto-site")) {
                var site=AutoSiteSelector.choose(mc.world,new BlockPos(step.x(),y,step.z()),new ShelterSchematic(net.minecraft.block.Blocks.COBBLESTONE.getDefaultState()),(int)core.getNumber("expedition","site-radius"),(int)core.getNumber("expedition","avoid-range"));
                if(site.origin().isEmpty()){block(site.message());return;}shelterOrigin=site.origin().get();
            }
        }
        var schematic=new ShelterSchematic(net.minecraft.block.Blocks.COBBLESTONE.getDefaultState());
        var observed=schematic.progress(mc.world,shelterOrigin);
        if(observed.complete()){advance("Verified the completed 7 × 7 shelter in the world");return;}
        if(observed.fraction()>lastBridgeProgress){lastBridgeProgress=observed.fraction();progress();}
        if(!dispatched){
            if(InventoryAutomation.count(Items.COBBLESTONE)<observed.missing()){block("Need " + observed.missing()+" more cobblestone for this shelter (have " + InventoryAutomation.count(Items.COBBLESTONE)+").");return;}
            var preparation=inventory.prepareBuild(observed.missing());if(preparation.blocked()){block(preparation.detail());return;}if(!preparation.done()){detail=preparation.detail();return;}
            bridge.clearStatus();if(!bridge.buildShelter(shelterOrigin,"minecraft:cobblestone")){block(bridge.status().message());return;}dispatched=true;
        }
        detail="Building shelter · " + observed.placed()+"/"+observed.required()+" blocks verified";
        if(bridge.status().failed()){dispatched=false;block(bridge.status().message());}
    }
    private void advance(String evidence){if(current<stepDetails.length)stepDetails[current]=evidence;bridge.cancel();bridge.clearStatus();inventory.stop();tools.reset();supplyResource="";supplyGoal=0;materialCrafting=false;current++;dispatched=false;resetProgress();detail=evidence;if(current>=plan.steps().size())completePlan();persist();}
    private void completePlan(){stopOwned();state="COMPLETED";detail="Every stage completed and its game state was verified.";core.setEnabled("expedition",false);persist();}
    private String worldKey(){if(mc.world==null)return "";String server=mc.getCurrentServerEntry()!=null?"server:"+mc.getCurrentServerEntry().address:mc.getServer()!=null?"local:"+mc.getServer().getSavePath(WorldSavePath.ROOT).toAbsolutePath():"unknown";return server+"|"+mc.world.getRegistryKey().getValue();}
    private void validate(TaskPlanner.Plan candidate){Objects.requireNonNull(candidate);if(candidate.steps()==null||candidate.steps().isEmpty()||candidate.steps().size()>64)throw new IllegalArgumentException("A plan needs between 1 and 64 stages.");for(var step:candidate.steps()){Objects.requireNonNull(step.action());if(step.id()==null||step.resource()==null||Math.abs((long)step.x())>29_999_000||Math.abs((long)step.z())>29_999_000||step.y() < -2048||step.y()>2048||step.count()<0||step.count()>4096)throw new IllegalArgumentException("A task has invalid coordinates or count.");}}
    private void persist(){if(checkpoint==null||plan==null)return;try{Files.createDirectories(checkpoint.getParent());Path pending=checkpoint.resolveSibling("automation-task.json.pending");BlockPosData position=shelterOrigin==null?null:new BlockPosData(shelterOrigin.getX(),shelterOrigin.getY(),shelterOrigin.getZ());Files.writeString(pending,JSON.toJson(new Saved(plan,current,worldKey,detail,arrivalY,position,Map.copyOf(suspended))),StandardCharsets.UTF_8);Files.move(pending,checkpoint,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(Exception error){AnalyzerClient.LOGGER.warn("Could not save the paused automation checkpoint",error);}}
    private void restore(){activeTask=false;if(checkpoint==null||!Files.isRegularFile(checkpoint))return;try{if(Files.size(checkpoint)>100_000)throw new IllegalArgumentException("Checkpoint too large");Saved saved=JSON.fromJson(Files.readString(checkpoint,StandardCharsets.UTF_8),Saved.class);validate(saved.plan);if(saved.current<0||saved.current>saved.plan.steps().size()||saved.worldKey==null)throw new IllegalArgumentException("Invalid task cursor");if(saved.suspended!=null){suspended.putAll(saved.suspended);restoreConflicts();}plan=saved.plan;current=saved.current;worldKey=saved.worldKey;arrivalY=saved.arrivalY;shelterOrigin=saved.shelter==null?null:new BlockPos(saved.shelter.x,saved.shelter.y,saved.shelter.z);stepDetails=new String[plan.steps().size()];Arrays.fill(stepDetails,"");state=current==plan.steps().size()?"COMPLETED":"PAUSED";detail=current==plan.steps().size()?"Previous task completed.":"Saved task restored paused. Join the original world, review it and press Resume.";}catch(Exception error){AnalyzerClient.LOGGER.warn("Ignored invalid automation checkpoint",error);}}
}
