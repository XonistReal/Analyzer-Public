package dev.analyzer.probe;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import java.io.File;
import java.lang.reflect.Method;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Original test-only probe. No Minecraft types are linked in its descriptors. */
public final class ProductionProbe implements ClientModInitializer {
    private final AtomicBoolean finished = new AtomicBoolean();
    private int stage, stable;
    private String screenshot;
    private Object client;
    private Class<?> minecraft;
    public void onInitializeClient() {
        if (FabricLoader.getInstance().isDevelopmentEnvironment()) throw new IllegalStateException("Expected real production Fabric environment");
        System.out.println("ANALYZER_PRODUCTION_PROBE_ENVIRONMENT: development=false");
        Thread thread = new Thread(() -> {
            try {
                minecraft = Class.forName("net.minecraft.class_310");
                client = minecraft.getMethod("method_1551").invoke(null);
                for (int i=0; i<180 && !finished.get(); i++) {
                    ((Executor)client).execute(this::inspect);
                    Thread.sleep(500);
                }
                if (!finished.get()) {
                    System.out.println("ANALYZER_PRODUCTION_PROBE_FAILED: title/automation rendering deadline");
                    ((Executor)client).execute(this::stop);
                }
            } catch (Throwable problem) {
                problem.printStackTrace();
                System.out.println("ANALYZER_PRODUCTION_PROBE_FAILED: " + problem.getClass().getName());
            }
        }, "Analyzer isolated production probe");
        thread.setDaemon(true); thread.start();
    }
    private void inspect() {
        if (finished.get()) return;
        try {
            Object screen = minecraft.getField("field_1755").get(client);
            if (stage==0) {
                if (screen==null || !screen.getClass().getName().equals("net.minecraft.class_442")) {stable=0; return;}
                if (++stable<4) return;
                boolean expected = Boolean.getBoolean("analyzer.probeBaritone");
                boolean loaded = FabricLoader.getInstance().isModLoaded("baritone");
                Class<?> engineClass = Class.forName("dev.analyzer.automation.AutomationEngine");
                Object controller = engineClass.getField("INSTANCE").get(null);
                boolean available = (Boolean)engineClass.getMethod("available").invoke(controller);
                if (loaded!=expected || available!=expected) throw new IllegalStateException("Optional dependency state mismatched: loaded="+loaded+", available="+available);
                if (expected && !FabricLoader.getInstance().isModLoaded("dev_babbaj_nether-pathfinder")) throw new IllegalStateException("Official nested helper was not loaded");
                Class<?> coreClass = Class.forName("dev.analyzer.core.AnalyzerCore");
                Object core = Class.forName("dev.analyzer.AnalyzerClient").getField("CORE").get(null);
                int count = ((java.util.List<?>)coreClass.getMethod("modules").invoke(core)).size();
                if (count!=123) throw new IllegalStateException("Expected 123 production modules");
                Object view = engineClass.getMethod("snapshot").invoke(controller);
                if ((Boolean)view.getClass().getMethod("baritoneAvailable").invoke(view)!=expected) throw new IllegalStateException("Automation screen dependency indicator mismatched");
                System.out.println("ANALYZER_PRODUCTION_PROBE_TITLE: production title rendered; modules="+count+", baritone="+available+", nestedHelper="+FabricLoader.getInstance().isModLoaded("dev_babbaj_nether-pathfinder"));
                Class<?> screenClass = Class.forName("net.minecraft.class_437");
                Class<?> automation = Class.forName("dev.analyzer.ui.AutomationScreen");
                Class<?> controllerType = Class.forName("dev.analyzer.ui.AutomationScreen$Controller");
                Object menu = automation.getConstructor(screenClass,controllerType,coreClass).newInstance(screen,controller,core);
                minecraft.getMethod("method_1507",screenClass).invoke(client,menu);
                screenshot = expected ? "analyzer-production-baritone.png" : "analyzer-production-without-baritone.png";
                stable=0;stage=1;
            } else if (stage==1) {
                if (screen==null || !screen.getClass().getName().equals("dev.analyzer.ui.AutomationScreen")) throw new IllegalStateException("Expected actual Automation screen");
                if (++stable<4) return;
                int error=(Integer)Class.forName("org.lwjgl.opengl.GL11").getMethod("glGetError").invoke(null);
                if (error!=0) throw new IllegalStateException("Production GL error "+error);
                Object framebuffer = minecraft.getMethod("method_1522").invoke(client);
                File directory = (File)minecraft.getField("field_1697").get(client);
                Method save = null;
                for (Method method : Class.forName("net.minecraft.class_318").getMethods())
                    if (method.getName().equals("method_22690") && method.getParameterCount()==4) {save=method;break;}
                if (save==null) throw new IllegalStateException("Production screenshot method unavailable");
                Consumer<Object> callback = text -> System.out.println("ANALYZER_PRODUCTION_PROBE_CAPTURE_SAVED: "+screenshot);
                save.invoke(null,directory,screenshot,framebuffer,callback);
                System.out.println("ANALYZER_PRODUCTION_PROBE_SCREEN: Automation rendered; GL=0; screenshot="+screenshot);
                stable=0;stage=2;
            } else if (stage==2 && ++stable>=4) {
                System.out.println("ANALYZER_PRODUCTION_PROBE_COMPLETE: production startup and optional automation UI passed");
                finished.set(true);stop();
            }
        } catch (Throwable problem) {
            finished.set(true);problem.printStackTrace();
            System.out.println("ANALYZER_PRODUCTION_PROBE_FAILED: "+problem.getClass().getName());stop();
        }
    }
    private void stop() {
        try {minecraft.getMethod("method_1592").invoke(client);}
        catch(Throwable problem){problem.printStackTrace();}
    }
}
