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
import net.minecraft.item.Items;
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
    private final BaritoneBridge bridge = new BaritoneBridge();
    private final InventoryAutomation inventory = new InventoryAutomation(bridge);
    private final SurvivalGuard guard = new SurvivalGuard();
    private AnalyzerCore core;
    private TaskPlanner.Plan plan;
    private String state="READY",detail="Describe a task, review its stages, then press Start.",worldKey="";
    private String[] stepDetails=new String[0];
    private int current,ticks,lastProgress,lastPersist;
    private Object world;
    private double bestDistance=Double.MAX_VALUE,lastBridgeProgress=-1;
    private int bestCount=-1,arrivalY=Integer.MIN_VALUE;
    private long lastClicks;
    private boolean dispatched, activeTask;
    private BlockPos shelterOrigin;
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
            if(INSTANCE.activeTask && (INSTANCE.state.equals("PROTECTING") || INSTANCE.state.equals("BLOCKED")))
                SafetyHud.render(context,core,INSTANCE.detail,INSTANCE.state.equals("BLOCKED"));
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client->{INSTANCE.pause();INSTANCE.stopOwned();INSTANCE.persist();});
    }
    public boolean available(){return bridge.available();}
    public boolean isRunning(){return activeTask && (state.equals("RUNNING") || state.equals("PROTECTING"));}
    public int currentStep(){return current;}
    public String state(){return state;}
    public String detail(){return detail;}
    public long inventoryClicks(){return inventory.clicks();}
    public BaritoneBridge.Status pathStatus(){return bridge.status();}
    public SurvivalGuard.Diagnostics safetyDiagnostics(){return guard.diagnostics();}
    public BaritoneBridge.SafetyProfileStatus routingSafety(){return bridge.safetyProfileStatus();}
    @Override public AutomationScreen.View snapshot() {
        List<AutomationScreen.StepView> stages=new ArrayList<>();
        if(plan!=null)for(int i=0;i<plan.steps().size();i++)stages.add(new AutomationScreen.StepView(plan.steps().get(i).label(),i<current?"Done":i==current?state:"Queued",i<stepDetails.length?stepDetails[i]:""));
        return new AutomationScreen.View(plan==null?"Analyzer Automation":plan.title(),state,detail,current,List.copyOf(stages),isRunning(),state.equals("PAUSED")||state.equals("BLOCKED"),available());
    }
    @Override public void submit(TaskPlanner.Plan requested) {
        validate(requested);
        if(mc.player==null||mc.world==null)throw new IllegalArgumentException("Join a world before starting a task.");
        if(!available())throw new IllegalArgumentException(bridge.status().message());
        stopOwned();
        if(!inventory.recovery().isBlank()){state="STOPPED";detail=inventory.recovery();persist();throw new IllegalArgumentException(detail);}
        plan=requested;current=0;stepDetails=new String[plan.steps().size()];Arrays.fill(stepDetails,"");
        world=mc.world;worldKey=worldKey();arrivalY=Integer.MIN_VALUE;shelterOrigin=null;suspendConflicts();
        activeTask=true;state="RUNNING";detail="Starting " + plan.steps().getFirst().label();core.setEnabled("expedition",true);startSafetyProfile();resetProgress();persist();
    }
    @Override public void pause(){if(isRunning()){guard.cleanup();bridge.endSafetyProfile();bridge.pause();state="PAUSED";detail="Paused. Resume continues the current stage.";persist();}}
    @Override public void resume() {
        if(plan==null||current>=plan.steps().size()){detail="Choose and start a new task.";return;}
        if(mc.world==null||mc.player==null){detail="Join the original world before resuming.";return;}
        if(!worldKey.equals(worldKey())){state="BLOCKED";detail="This plan belongs to a different world or dimension. Start a new plan for this location.";return;}
        if(!available()){state="BLOCKED";detail=bridge.status().message();return;}
        world=mc.world;suspendConflicts();core.setEnabled("expedition",true);activeTask=true;state="RUNNING";
        startSafetyProfile();
        if(bridge.status().paused())bridge.resume();
        resetProgress();detail="Resuming " + plan.steps().get(current).label();persist();
    }
    @Override public void stop(){stopOwned();state="STOPPED";detail=inventory.recovery().isBlank()?"Stopped. Owned pathing and inventory actions have been released.":inventory.recovery();if(core!=null)core.setEnabled("expedition",false);persist();}
    private void stopOwned(){activeTask=false;guard.cleanup();bridge.endSafetyProfile();bridge.cancel();inventory.stop();dispatched=false;restoreConflicts();}
    private void startSafetyProfile(){if(core.getBoolean("expedition","safe-routing"))bridge.beginSafetyProfile();else bridge.endSafetyProfile();}
    private void suspendConflicts(){
        java.util.Set<String> actions=new java.util.HashSet<>(CONFLICTS);
        for(var module:dev.analyzer.core.GameplayModules.definitions())actions.add(module.id());
        for(var module:dev.analyzer.core.UtilityModules.definitions())actions.add(module.id());
        for(String id:actions)if(hasModule(id)&&core.isEnabled(id)){suspended.putIfAbsent(id,true);core.setEnabled(id,false);}
    }
    private void restoreConflicts(){if(core!=null){for(var entry:suspended.entrySet())if(hasModule(entry.getKey())&&!core.isEnabled(entry.getKey()))core.setEnabled(entry.getKey(),entry.getValue());}suspended.clear();}

    private void block(String reason){guard.cleanup();bridge.endSafetyProfile();bridge.pause();state="BLOCKED";detail=reason;if(current<stepDetails.length)stepDetails[current]=reason;persist();}
    private void resetProgress(){lastProgress=ticks;bestCount=-1;bestDistance=Double.MAX_VALUE;lastBridgeProgress=-1;lastClicks=inventory.clicks();dispatched=bridge.status().active();}
    private void progress(){lastProgress=ticks;}
    private void tick() {
        ticks++;bridge.tick();
        // Paused live tasks still own pathing, workstations and suspended modules.
        // Restored checkpoints are inactive until the user explicitly resumes.
        if(activeTask && !core.isEnabled("expedition")){stop();return;}
        if(activeTask && (mc.world==null||mc.player==null||mc.interactionManager==null)){stopOwned();block("Disconnected. Join the original world and explicitly resume.");return;}
        if(activeTask && world!=mc.world){stopOwned();block("The world or dimension changed. Review the plan before resuming.");return;}
        if(activeTask && !mc.player.isAlive()){stopOwned();block("The player died. Review inventory and position before resuming.");return;}
        if(!isRunning())return;
        if(mc.player.isSpectator()){block("Automation cannot act in Spectator mode.");return;}
        if(mc.getOverlay()!=null){pause();detail="Paused for a resource reload. Resume after loading finishes.";persist();return;}
        if(core.getBoolean("expedition","pause-on-menu") && mc.currentScreen!=null && !inventory.ownsScreen()){pause();detail="Paused while a menu is open. Press Resume when ready.";persist();return;}
        for(String id:CONFLICTS)if(hasModule(id)&&core.isEnabled(id)){block("Disable " + core.definition(id).name() + " before resuming; it can interfere with this task.");return;}
        if(bridge.status().state()==BaritoneBridge.State.INTERRUPTED){dispatched=false;block(bridge.status().message());return;}
        if(bridge.manualTaskActive()){block("A manual Baritone task is active. Analyzer released safety control; finish it before resuming this plan.");return;}
        if(core.getBoolean("expedition","safe-routing") != bridge.safetyProfileStatus().enabled())startSafetyProfile();
        var protection=guard.tick(core,bridge,inventory);
        if(protection.fatalBlock()){block(protection.message());return;}
        if(protection.intervening()){
            boolean changed=!state.equals("PROTECTING");state="PROTECTING";detail=protection.message();progress();
            if(changed){AnalyzerClient.LOGGER.info("Automation protection: {}",detail);persist();}
            return;
        }
        if(protection.resumeReady()){
            if(bridge.status().paused())bridge.resume();state="RUNNING";resetProgress();detail=protection.message();persist();
        }
        if(mc.player.getHealth()<core.getNumber("expedition","min-health")){block("Low health: " + Math.round(mc.player.getHealth()) + " HP. Recover, then resume.");return;}
        if(!mc.player.getAbilities().creativeMode && mc.player.getHungerManager().getFoodLevel()<core.getNumber("expedition","min-hunger")){block("Low hunger. Eat, then resume.");return;}
        if(ticks-lastProgress>core.getNumber("expedition","stuck-timeout")*20){block("No measurable progress for " + (int)core.getNumber("expedition","stuck-timeout") + " seconds. Check the route, tools or workstation, then resume.");return;}
        if(current>=plan.steps().size()){completePlan();return;}
        TaskPlanner.Step step=plan.steps().get(current);
        try {
            switch(step.action()) {
                case TRAVEL -> travel(step);
                case MINE -> mine(step);
                case BUILD_SHELTER -> build(step);
                case CRAFT, EQUIP, ENCHANT -> inventory(step);
            }
        } catch(RuntimeException|LinkageError error){block("Task paused: " + error.getClass().getSimpleName() + ". Check the local game log.");AnalyzerClient.LOGGER.warn("Automation stage failed: {}",step.id(),error);}
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
        // Completed equipment credits prerequisite stages when a kit task is already satisfied.
        if(plan.steps().stream().anyMatch(s->s.action()==TaskPlanner.Action.CRAFT&&s.resource().equals("diamond-kit")) && InventoryAutomation.diamondKit() && !plan.steps().stream().anyMatch(s->s.action()==TaskPlanner.Action.BUILD_SHELTER)) {advance("Existing diamond kit satisfies this prerequisite");return;}
        int count=InventoryAutomation.countResource(step.resource());
        if(count>bestCount){bestCount=count;progress();}
        if(count>=step.count()){advance("Inventory verified: " + count + "/" + step.count());return;}
        if(step.resource().equals("diamond") && InventoryAutomation.count(Items.IRON_PICKAXE)+InventoryAutomation.count(Items.DIAMOND_PICKAXE)+InventoryAutomation.count(Items.NETHERITE_PICKAXE)==0){block("Diamond ore needs an iron-tier pickaxe. Craft or repair one before resuming.");return;}
        if(step.resource().equals("raw_iron") && InventoryAutomation.count(Items.STONE_PICKAXE)+InventoryAutomation.count(Items.IRON_PICKAXE)+InventoryAutomation.count(Items.DIAMOND_PICKAXE)+InventoryAutomation.count(Items.NETHERITE_PICKAXE)==0){block("Iron ore needs a stone-tier pickaxe. Craft or repair one before resuming.");return;}
        if(!dispatched){var preparation=inventory.tick("mining-hotbar",30);if(preparation.blocked()){block(preparation.detail());return;}if(!preparation.done()){detail=preparation.detail();return;} bridge.clearStatus();if(!bridge.mineByName(step.count(),blocks(step.resource()))){block(bridge.status().message());return;}dispatched=true;}
        var path=bridge.status();detail=step.label()+" · " + count+"/"+step.count()+" in inventory";
        if(path.failed()||path.complete()){dispatched=false;if(count>=step.count())advance("Required items received");else block(path.message()+". Inventory is " + count+"/"+step.count());}
    }
    private String[] blocks(String resource){return switch(resource){case "logs"->new String[]{"oak_log","spruce_log","birch_log","jungle_log","acacia_log","dark_oak_log","mangrove_log","cherry_log","pale_oak_log","crimson_stem","warped_stem"};case "cobblestone"->new String[]{"stone","cobblestone"};case "raw_iron"->new String[]{"iron_ore","deepslate_iron_ore"};case "coal"->new String[]{"coal_ore","deepslate_coal_ore"};case "diamond"->new String[]{"diamond_ore","deepslate_diamond_ore"};default->throw new IllegalArgumentException("Unknown resource");};}
    private void inventory(TaskPlanner.Step step) {
        if(step.action()==TaskPlanner.Action.CRAFT && InventoryAutomation.diamondKit() && !plan.steps().stream().anyMatch(s->s.action()==TaskPlanner.Action.BUILD_SHELTER)) {advance("Existing diamond kit satisfies crafting prerequisites");return;}
        String task=switch(step.action()){case EQUIP->"equip";case ENCHANT->"diamond-armor";default->step.resource();};
        var result=inventory.tickTransfer();if(result==null)result=inventory.tick(task,30);
        detail=result.detail();
        // Slot clicks alone are not evidence of durable progression: inventory changes drive the watchdog too.
        long fingerprint=inventoryFingerprint();
        if(fingerprint!=lastInventoryFingerprint){lastInventoryFingerprint=fingerprint;progress();}
        if(result.blocked()){block(result.detail());return;}
        if(result.done())advance(result.detail());
    }
    private long lastInventoryFingerprint;
    private long inventoryFingerprint(){long hash=1;for(int i=0;i<mc.player.getInventory().size();i++){var s=mc.player.getInventory().getStack(i);hash=31*hash+s.getItem().hashCode();hash=31*hash+s.getCount();hash=31*hash+s.getDamage();hash=31*hash+s.getComponents().hashCode();}return hash;}
    private void build(TaskPlanner.Step step) {
        if(shelterOrigin==null) {
            int y=arrivalY==Integer.MIN_VALUE?step.y():arrivalY;
            // Explicit footprint is offset from the arrival point, so the player does not start inside a wall.
            shelterOrigin=new BlockPos(step.x()+3,y-1,step.z()+3);
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
    private void advance(String evidence){if(current<stepDetails.length)stepDetails[current]=evidence;bridge.cancel();bridge.clearStatus();inventory.stop();current++;dispatched=false;resetProgress();detail=evidence;if(current>=plan.steps().size())completePlan();persist();}
    private void completePlan(){stopOwned();state="COMPLETED";detail="Every stage completed and its game state was verified.";core.setEnabled("expedition",false);persist();}
    private String worldKey(){if(mc.world==null)return "";String server=mc.getCurrentServerEntry()!=null?"server:"+mc.getCurrentServerEntry().address:mc.getServer()!=null?"local:"+mc.getServer().getSavePath(WorldSavePath.ROOT).toAbsolutePath():"unknown";return server+"|"+mc.world.getRegistryKey().getValue();}
    private void validate(TaskPlanner.Plan candidate){Objects.requireNonNull(candidate);if(candidate.steps().size()>64)throw new IllegalArgumentException("A plan can have at most 64 stages.");for(var step:candidate.steps()){Objects.requireNonNull(step.action());if(Math.abs((long)step.x())>29_999_000||Math.abs((long)step.z())>29_999_000||step.y() < -2048||step.y()>2048||step.count()<0||step.count()>4096)throw new IllegalArgumentException("A task has invalid coordinates or count.");}}
    private void persist(){if(checkpoint==null||plan==null)return;try{Files.createDirectories(checkpoint.getParent());Path pending=checkpoint.resolveSibling("automation-task.json.pending");BlockPosData position=shelterOrigin==null?null:new BlockPosData(shelterOrigin.getX(),shelterOrigin.getY(),shelterOrigin.getZ());Files.writeString(pending,JSON.toJson(new Saved(plan,current,worldKey,detail,arrivalY,position,Map.copyOf(suspended))),StandardCharsets.UTF_8);Files.move(pending,checkpoint,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(Exception error){AnalyzerClient.LOGGER.warn("Could not save the paused automation checkpoint",error);}}
    private void restore(){activeTask=false;if(checkpoint==null||!Files.isRegularFile(checkpoint))return;try{if(Files.size(checkpoint)>100_000)throw new IllegalArgumentException("Checkpoint too large");Saved saved=JSON.fromJson(Files.readString(checkpoint,StandardCharsets.UTF_8),Saved.class);validate(saved.plan);if(saved.current<0||saved.current>saved.plan.steps().size()||saved.worldKey==null)throw new IllegalArgumentException("Invalid task cursor");if(saved.suspended!=null){suspended.putAll(saved.suspended);restoreConflicts();}plan=saved.plan;current=saved.current;worldKey=saved.worldKey;arrivalY=saved.arrivalY;shelterOrigin=saved.shelter==null?null:new BlockPos(saved.shelter.x,saved.shelter.y,saved.shelter.z);stepDetails=new String[plan.steps().size()];Arrays.fill(stepDetails,"");state=current==plan.steps().size()?"COMPLETED":"PAUSED";detail=current==plan.steps().size()?"Previous task completed.":"Saved task restored paused. Join the original world, review it and press Resume.";}catch(Exception error){AnalyzerClient.LOGGER.warn("Ignored invalid automation checkpoint",error);}}
}
