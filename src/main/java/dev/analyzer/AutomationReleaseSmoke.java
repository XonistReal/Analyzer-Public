package dev.analyzer;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.opengl.GL11;
/** Local development harness only; installed clients never enter DevSmoke. */
final class AutomationReleaseSmoke {
    private static boolean complete;
    private static int safetyStart=-1,guardStart=-1;
    static void tick(MinecraftClient mc,int t) {
        if(complete)return;
        if(Boolean.getBoolean("analyzer.safetyOnly")){safety(mc,t);return;}
        if(t<=EspSmoke.LAST_TICK){EspSmoke.tick(mc,t);return;}
        int bridgeTick=t-EspSmoke.LAST_TICK-1;
        if(bridgeTick<=AutomationBridgeSmoke.LAST_TICK){AutomationBridgeSmoke.tick(mc,bridgeTick);return;}
        int inventoryTick=bridgeTick-AutomationBridgeSmoke.LAST_TICK-1;
        if(!AutomationInventorySmoke.done())AutomationInventorySmoke.tick(mc,inventoryTick);
        if(AutomationInventorySmoke.done()) {
            if(safetyStart<0)safetyStart=t;
            safety(mc,t-safetyStart);
        }
    }
    private static void safety(MinecraftClient mc,int t){
        if(!SafetyProfileSmoke.done()){SafetyProfileSmoke.tick(mc,t);return;}
        if(guardStart<0)guardStart=t;
        SurvivalGuardSmoke.tick(mc,t-guardStart);
        if(SurvivalGuardSmoke.done()){
            if(GL11.glGetError()!=GL11.GL_NO_ERROR)throw new IllegalStateException("Final automation release GL state");
            complete=true;AnalyzerClient.LOGGER.info("ANALYZER_051_COMPLETE: ESP, Baritone, real inventory, UI and active Survival safety checks passed; final GL=0");mc.scheduleStop();
        }
    }
}
