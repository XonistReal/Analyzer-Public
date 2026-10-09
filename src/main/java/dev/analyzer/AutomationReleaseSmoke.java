package dev.analyzer;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.opengl.GL11;
/** Local development harness only; installed clients never enter DevSmoke. */
final class AutomationReleaseSmoke {
    private static boolean complete;
    static void tick(MinecraftClient mc,int t) {
        if(complete)return;
        if(t<=EspSmoke.LAST_TICK){EspSmoke.tick(mc,t);return;}
        int bridgeTick=t-EspSmoke.LAST_TICK-1;
        if(bridgeTick<=AutomationBridgeSmoke.LAST_TICK){AutomationBridgeSmoke.tick(mc,bridgeTick);return;}
        int inventoryTick=bridgeTick-AutomationBridgeSmoke.LAST_TICK-1;
        AutomationInventorySmoke.tick(mc,inventoryTick);
        if(AutomationInventorySmoke.done()) {
            if(GL11.glGetError()!=GL11.GL_NO_ERROR)throw new IllegalStateException("Final automation release GL state");
            complete=true;AnalyzerClient.LOGGER.info("ANALYZER_050_COMPLETE: ESP, official Baritone, real inventory and plan UI checks passed; final GL=0");mc.scheduleStop();
        }
    }
}
