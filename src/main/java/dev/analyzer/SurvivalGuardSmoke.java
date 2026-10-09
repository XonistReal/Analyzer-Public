package dev.analyzer;

import dev.analyzer.automation.AutomationEngine;
import dev.analyzer.automation.InventoryAutomation;
import dev.analyzer.automation.TaskPlanner;
import dev.analyzer.automation.SafetyHud;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Items;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import org.lwjgl.opengl.GL11;
import java.util.List;

/** Real local night-time Survival tests for the stationary-miner attack regression. */
final class SurvivalGuardSmoke {
    private static final AutomationEngine ENGINE=AutomationEngine.INSTANCE;
    private static int stage, since, assertions, screenshots;
    private static long attacks, retreat, foodUses;
    private static boolean protectedMine, damagedMob, resumedMine, captured, foodEnabled, criticalDamage, done;
    private static Vec3d pausedAt;
    private static double initialDistance;
    static boolean done(){return done;}
    static void tick(MinecraftClient mc,int t){
        if(done)return;
        var core=AnalyzerClient.CORE;
        int age=t-since;
        switch(stage){
            case 0 -> {
                ENGINE.stop();core.resetAll();mc.setScreen(null);mc.options.setPerspective(Perspective.FIRST_PERSON);
                core.setValue("expedition","resume-delay",.5);core.setValue("expedition","auto-food",false);
                command(mc,"kill @e[type=!minecraft:player]");command(mc,"gamemode creative AnalyzerDev");
                command(mc,"clear AnalyzerDev");command(mc,"effect clear AnalyzerDev");command(mc,"difficulty normal");
                command(mc,"fill 130 160 130 185 160 175 minecraft:bedrock");command(mc,"fill 130 161 130 185 170 175 minecraft:air");
                command(mc,"fill 142 161 138 143 162 142 minecraft:stone");command(mc,"tp AnalyzerDev 140.5 161 140.5 -90 12");
                command(mc,"gamerule doMobSpawning false");command(mc,"gamerule doDaylightCycle false");command(mc,"gamerule naturalRegeneration false");
                command(mc,"time set 18000");command(mc,"weather clear");
                command(mc,"gamemode survival AnalyzerDev");command(mc,"item replace entity AnalyzerDev hotbar.0 with minecraft:diamond_pickaxe");
                command(mc,"item replace entity AnalyzerDev hotbar.1 with minecraft:diamond_sword");
                command(mc,"effect give AnalyzerDev minecraft:instant_health 1 5 true");command(mc,"effect give AnalyzerDev minecraft:saturation 1 5 true");
                next(t);
            }
            case 1 -> {if(age>=20){
                require(mc.interactionManager.getCurrentGameMode()==GameMode.SURVIVAL,"Safety fixture runs in real Survival mode");
                require(mc.world.isNight(),"Stationary-miner defense fixture is at night");mc.player.getInventory().selectedSlot=0;
                ENGINE.submit(new TaskPlanner.Plan("Night mining safety","Actual mining while attacked",List.of(new TaskPlanner.Step("stone",TaskPlanner.Action.MINE,"Mine stone safely","cobblestone",8,0,0,0)),List.of()));
                attacks=ENGINE.safetyDiagnostics().attacks();next(t);
            }}
            case 2 -> {if(age>=8 && ENGINE.pathStatus().owned()){
                require(ENGINE.routingSafety().applied(),"Reviewed task applies scoped safer Baritone routing while mining");
                var p=mc.player.getPos();command(mc,"summon minecraft:zombie "+p.x+" "+p.y+" "+(p.z+2.2)+" {CustomName:'\"AnalyzerSafetyZombie\"',PersistenceRequired:1b}");next(t);
            }else timeout(age,120,"Mining process never became owned");}
            case 3 -> {
                LivingEntity zombie=mob(mc,"AnalyzerSafetyZombie");
                if(ENGINE.state().equals("PROTECTING")){
                    if(!protectedMine){require(ENGINE.pathStatus().paused(),"Nearby AI zombie immediately pauses the owned mining process");require(ENGINE.currentStep()==0,"Defense retains the original mining stage");protectedMine=true;}
                    if(!captured && ENGINE.safetyDiagnostics().attacks()>attacks && SafetyHud.renderedFrames()>0){
                        var bounds=SafetyHud.lastBounds();require(bounds!=null && bounds.nominalTextPixels()>=14 && bounds.x()>=0 && bounds.x()+bounds.width()<=mc.getWindow().getFramebufferWidth(),"Actual safety banner renders readable text within the framebuffer");
                        capture(mc,"analyzer-safety-zombie-defense.png");captured=true;
                    }
                }
                if(zombie!=null && zombie.getHealth()<20)damagedMob=true;
                if(protectedMine && zombie==null && ENGINE.safetyDiagnostics().attacks()>attacks && age>20){
                    require(damagedMob,"Ordinary combat damages the actual server zombie");require(mc.player.isAlive(),"Player survives the night-time mining attack");
                    require(ENGINE.safetyDiagnostics().attacks()-attacks>=2,"Defense uses multiple real cooldown-limited attacks");next(t);
                }else timeout(age,300,"Zombie defense did not clear the real attacker");
            }
            case 4 -> {
                if(ENGINE.state().equals("RUNNING"))resumedMine=true;
                if(ENGINE.state().equals("COMPLETED")){
                    require(resumedMine,"Threat-free delay automatically resumes the original mining task");
                    require(InventoryAutomation.count(Items.COBBLESTONE)>=8,"Resumed mining obtains eight actual cobblestone drops");
                    require(!ENGINE.routingSafety().enabled() && !ENGINE.routingSafety().applied(),"Completed task restores temporary routing settings");
                    capture(mc,"analyzer-safety-mining-resumed.png");
                    command(mc,"tp AnalyzerDev 150.5 161 155.5 -90 15");command(mc,"effect give AnalyzerDev minecraft:instant_health 1 5 true");
                    for(int i=2;i<9;i++)command(mc,"item replace entity AnalyzerDev hotbar."+i+" with minecraft:stick");
                    command(mc,"item replace entity AnalyzerDev inventory.0 with minecraft:cooked_beef 5");
                    mc.getServer().execute(()->{var player=mc.getServer().getPlayerManager().getPlayer("AnalyzerDev");player.getHungerManager().setFoodLevel(8);player.getHungerManager().setSaturationLevel(0);});
                    next(t);
                }else timeout(age,600,"Original stone mining did not finish after defense");
            }
            case 5 -> {if(age>=20){
                require(mc.player.getHungerManager().getFoodLevel()==8,"Food fixture uses authoritative low hunger");mc.player.getInventory().selectedSlot=2;
                require(mc.player.getInventory().getStack(9).isOf(Items.COOKED_BEEF),"Usable food starts in main inventory, outside a full hotbar");
                foodUses=ENGINE.safetyDiagnostics().foodUses();ENGINE.submit(travel("Food recovery",170,155));next(t);
            }}
            case 6 -> {
                if(!foodEnabled && ENGINE.pathStatus().owned()){core.setValue("expedition","auto-food",true);foodEnabled=true;}
                if(ENGINE.safetyDiagnostics().consuming()){
                    require(ENGINE.state().equals("PROTECTING"),"Low hunger interrupts a reviewed task for normal food use");
                    require(ENGINE.pathStatus().paused(),"Food recovery pauses an actual owned Baritone route");
                    capture(mc,"analyzer-safety-eating.png");ENGINE.pause();next(t);
                }else timeout(age,80,"Ordinary food consumption never started");
            }
            case 7 -> {if(age>=12){
                require(ENGINE.state().equals("PAUSED") && !ENGINE.safetyDiagnostics().consuming(),"Manual Pause releases owned food use and safety control");
                require(!mc.player.isUsingItem() && !mc.options.useKey.isPressed(),"Pause stops the guard's held-use input");
                require(mc.player.getInventory().selectedSlot==2 && mc.player.getInventory().getStack(2).isOf(Items.STICK),"Pause restores the selected slot and swapped hotbar item");
                require(mc.player.getInventory().getStack(9).isOf(Items.COOKED_BEEF),"Pause returns uneaten food to its original inventory slot");
                require(!ENGINE.routingSafety().applied(),"Manual Pause restores safer-route settings");ENGINE.resume();next(t);
            }}
            case 8 -> {
                if(mc.player.getHungerManager().getFoodLevel()>=12 && InventoryAutomation.count(Items.COOKED_BEEF)<5 && !ENGINE.safetyDiagnostics().intervening()){
                    require(ENGINE.safetyDiagnostics().foodUses()>foodUses,"Recovery resumes ordinary food consumption after explicit Resume");
                    require(InventoryAutomation.count(Items.COOKED_BEEF)<5,"Recovery consumes actual food instead of changing inventory locally");
                    require(mc.player.currentScreenHandler.getCursorStack().isEmpty(),"Food swapping leaves no held inventory cursor");
                    ENGINE.stop();command(mc,"tp AnalyzerDev 154.5 161 155.5 -90 15");command(mc,"effect give AnalyzerDev minecraft:saturation 1 5 true");
                    command(mc,"setblock 153 160 155 minecraft:lava");command(mc,"item replace entity AnalyzerDev hotbar.1 with minecraft:diamond_sword");next(t);
                }else timeout(age,240,"Food recovery did not consume server food and release intervention");
            }
            case 9 -> {if(age>=20){
                ENGINE.submit(travel("Creeper retreat",179,155));retreat=ENGINE.safetyDiagnostics().retreatTicks();next(t);
            }}
            case 10 -> {if(age>=6 && ENGINE.pathStatus().owned()){
                var p=mc.player.getPos();command(mc,"summon minecraft:creeper "+(p.x+4.5)+" "+p.y+" "+p.z+" {CustomName:'\"AnalyzerSafetyCreeper\"',PersistenceRequired:1b}");
                initialDistance=4.5;next(t);
            }else timeout(age,100,"Creeper test route never became owned");}
            case 11 -> {
                var creeper=mob(mc,"AnalyzerSafetyCreeper");
                if(ENGINE.state().equals("PROTECTING") && !criticalDamage){command(mc,"damage AnalyzerDev 15 minecraft:generic");criticalDamage=true;}
                if(!mc.player.isAlive() || mc.player.isInLava() || mc.player.getY()<160.9)throw new IllegalStateException("Retreat entered unsafe ground");
                if(creeper!=null && ENGINE.safetyDiagnostics().retreatTicks()-retreat>=12 && mc.player.distanceTo(creeper)>initialDistance+1){
                    require(mc.player.getHealth()<core.getNumber("expedition","min-health"),"Creeper test receives real damage below the task health limit");
                    require(mc.player.isAlive() && !mc.player.isInLava() && mc.player.getY()>160.9,"Critical-health retreat remains alive on supported non-lava ground");
                    require(ENGINE.state().equals("PROTECTING") && ENGINE.currentStep()==0,"Creeper threat holds the original route without replacing its stage");
                    require(ENGINE.pathStatus().paused(),"Creeper retreat uses the paused Analyzer-owned lease");
                    capture(mc,"analyzer-safety-creeper-retreat.png");ENGINE.pause();command(mc,"kill @e[name=AnalyzerSafetyCreeper]");command(mc,"setblock 153 160 155 minecraft:bedrock");next(t);
                }else timeout(age,150,"Creeper retreat failed to increase real separation");
            }
            case 12 -> {if(age==10)pausedAt=mc.player.getPos();if(age>=25){
                require(mc.player.getPos().distanceTo(pausedAt)<.25,"Manual Pause releases retreat movement after momentum settles");
                require(!mc.options.forwardKey.isPressed() && !mc.options.sprintKey.isPressed(),"Guard-owned movement keys are released");
                ENGINE.stop();command(mc,"tp AnalyzerDev 150.5 161 155.5 -90 30");
                command(mc,"effect give AnalyzerDev minecraft:instant_health 1 5 true");
                command(mc,"summon minecraft:enderman 150.5 161 159.5 {PersistenceRequired:1b}");command(mc,"summon minecraft:cow 149.5 161 159.5 {PersistenceRequired:1b}");next(t);
            }}
            case 13 -> {if(age>=15){ENGINE.submit(travel("Neutral mob filtering",169,155));attacks=ENGINE.safetyDiagnostics().attacks();next(t);}}
            case 14 -> {if(age>=25){
                require(ENGINE.isRunning(),"Healthy task continues beside unprovoked neutral mobs");
                require(!ENGINE.safetyDiagnostics().intervening() && ENGINE.safetyDiagnostics().threatCount()==0,"Unprovoked enderman and passive cow do not trigger defense");
                require(ENGINE.safetyDiagnostics().attacks()==attacks,"Neutral mobs receive no safety attacks");
                ENGINE.stop();require(!ENGINE.pathStatus().owned() && !ENGINE.routingSafety().enabled(),"Stop releases task pathing and temporary safety profile");
                require(!ENGINE.safetyDiagnostics().intervening(),"Stop releases the survival guard");
                command(mc,"kill @e[type=!minecraft:player]");command(mc,"gamerule doDaylightCycle true");command(mc,"gamerule doMobSpawning true");command(mc,"gamerule naturalRegeneration true");command(mc,"gamemode creative AnalyzerDev");
                require(GL11.glGetError()==GL11.GL_NO_ERROR,"Survival guard final GL state");
                done=true;AnalyzerClient.LOGGER.info("ANALYZER_SURVIVAL_GUARD_COMPLETE: {} assertions, {} screenshots; real AI zombie night mining, actual food, creeper retreat, neutral filtering and input cleanup",assertions,screenshots);
            }}
        }
    }
    private static void next(int t){stage++;since=t;}
    private static TaskPlanner.Plan travel(String title,int x,int z){return new TaskPlanner.Plan(title,"Local safety fixture",List.of(new TaskPlanner.Step("travel",TaskPlanner.Action.TRAVEL,"Walk to the goal","",0,x,161,z)),List.of());}
    private static LivingEntity mob(MinecraftClient mc,String name){for(var e:mc.world.getEntities())if(e instanceof LivingEntity living && e.getName().getString().equals(name) && living.isAlive())return living;return null;}
    private static void timeout(int age,int limit,String message){if(age>limit)throw new IllegalStateException(message+" / "+ENGINE.state()+" / "+ENGINE.detail()+" / "+ENGINE.pathStatus()+" / "+ENGINE.safetyDiagnostics());}
    private static void command(MinecraftClient mc,String command){mc.getServer().execute(()->mc.getServer().getCommandManager().executeWithPrefix(mc.getServer().getCommandSource().withSilent(),command));}
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException("Survival guard stage "+stage+": "+message+" / "+ENGINE.detail());assertions++;AnalyzerClient.LOGGER.info("Survival guard check passed: {}",message);}
    private static void capture(MinecraftClient mc,String name){require(GL11.glGetError()==GL11.GL_NO_ERROR,"GL state before "+name);ScreenshotRecorder.saveScreenshot(mc.runDirectory,name,mc.getFramebuffer(),text->AnalyzerClient.LOGGER.info("Survival safety screenshot: {}",text.getString()));screenshots++;}
}
