package dev.analyzer;

import dev.analyzer.core.AnalyzerCore;
import dev.analyzer.effects.GpuEffects;
import dev.analyzer.render.AnalyzerRenderer;
import dev.analyzer.ui.MenuScreens;
import dev.analyzer.render.VisualEffects;
import dev.analyzer.gameplay.GameplayEngine;
import dev.analyzer.gameplay.UtilityEngine;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public final class AnalyzerClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("Analyzer");
    public static AnalyzerCore CORE;
    private final Map<String, Boolean> held = new HashMap<>();
    private static KeyBinding open;
    private int saveCountdown;

    @Override public void onInitializeClient() {
        CORE = new AnalyzerCore(FabricLoader.getInstance().getConfigDir().resolve("analyzer"));
        try { CORE.load(); for (String warning : CORE.loadWarnings()) LOGGER.warn(warning); }
        catch (IOException ex) { LOGGER.error("Could not load Analyzer configuration; using defaults", ex); }
        dev.analyzer.privacy.ServerPrivacy.initialize(CORE);
        dev.analyzer.automation.RuntimeMaterialNames.refresh();
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> dev.analyzer.automation.RuntimeMaterialNames.refresh());
        ClientLifecycleEvents.CLIENT_STARTED.register(dev.analyzer.privacy.PrivacyTextProtection::initialize);
        try { PresetInstaller.install(FabricLoader.getInstance().getConfigDir().resolve("analyzer")); }
        catch (IOException ex) { LOGGER.warn("Could not install Analyzer starter profiles", ex); }
        open = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.analyzer.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, "key.categories.analyzer"));
        new AnalyzerRenderer(CORE).register();
        GameplayEngine.register(CORE);
        UtilityEngine.register(CORE);
        dev.analyzer.automation.AutomationEngine.register(CORE);
        VisualEffects.register(CORE);
        dev.analyzer.render.CosmeticRenderer.register(CORE);
        dev.analyzer.render.HvhPreview.register(CORE);
        dev.analyzer.render.XrayRenderer.register(CORE);
        dev.analyzer.render.SchematicPreviewRenderer.register(CORE);
        dev.analyzer.render.EntityChamsRenderer.register(CORE);
        dev.analyzer.skybox.CustomSkybox.register();
        dev.analyzer.finder.FinderEngine.register(CORE);
        dev.analyzer.render.EntityFinderRenderer.register(CORE);
        dev.analyzer.render.RegionMapHud.register(CORE);
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen instanceof TitleScreen) {
                Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal("Analyzer"), b -> client.setScreen(MenuScreens.create(CORE))).dimensions(w - 108, 10, 98, 20).build());
                Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal("Custom Home"), b -> {
                    CORE.setValue("menu", "home-enabled", true);saveConfig();client.setScreen(new dev.analyzer.ui.HomeScreen(CORE));
                }).dimensions(w - 108, 34, 98, 20).build());
            }
        });
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            public Identifier getFabricId() { return Identifier.of("analyzer", "effects"); }
            public void reload(ResourceManager manager) { MinecraftClient.getInstance().execute(() -> {GpuEffects.close();dev.analyzer.effects.UiShader.close();dev.analyzer.effects.HomeEffects.close();dev.analyzer.effects.LiquidGlass.close();}); }
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> { saveConfig(); GpuEffects.close(); dev.analyzer.effects.UiShader.close(); dev.analyzer.effects.HomeEffects.close(); dev.analyzer.effects.LiquidGlass.close(); });
        LOGGER.info("Analyzer initialized: {} modules, Minecraft 1.21.4", CORE.modules().size());
    }

    private void tick(MinecraftClient client) {
        while (open.wasPressed()) {
            if (client.currentScreen == null) client.setScreen(MenuScreens.create(CORE));
            else if (MenuScreens.isAnalyzerScreen(client.currentScreen)) client.currentScreen.close();
        }
        long window = client.getWindow().getHandle();
        for (var module : CORE.modules()) {
            int key = CORE.keybind(module.id());
            boolean down = key >= GLFW.GLFW_KEY_SPACE && InputUtil.isKeyPressed(window, key);
            boolean previous = held.getOrDefault(module.id(), false);
            held.put(module.id(), down);
            if (client.currentScreen == null && client.player != null && client.getOverlay() == null && down && !previous && key != KeyBindingHelper.getBoundKeyOf(open).getCode()) {
                CORE.toggle(module.id()); saveCountdown = 20;
                client.inGameHud.setOverlayMessage(Text.literal(module.name() + (CORE.isEnabled(module.id()) ? " enabled" : " disabled")), false);
            }
        }
        if (saveCountdown > 0 && --saveCountdown == 0) saveConfig();
        if (FabricLoader.getInstance().isDevelopmentEnvironment() && Boolean.getBoolean("analyzer.smoke")) {
            if (Boolean.getBoolean("analyzer.wingsSmoke")) WingsSmoke.tick(client);
            else if (Boolean.getBoolean("analyzer.skyboxSmoke")) SkyboxSmoke.tick(client);
            else if (Boolean.getBoolean("analyzer.silentAimSmoke")) SilentAimSmoke.tick(client);
            else if (Boolean.getBoolean("analyzer.tracerPrivacySmoke")) TracerPrivacySmoke.tick(client);
            else if (Boolean.getBoolean("analyzer.findersSmoke")) FindersSmoke.tick(client);
            else if (Boolean.getBoolean("analyzer.chamsSmoke")) ChamsSmoke.tick(client);
            else if (Boolean.getBoolean("analyzer.betaSmoke")) BetaSmoke.tick(client);
            else if (Boolean.getBoolean("analyzer.privacySmoke")) PrivacySmoke.tick(client);
            else if (Boolean.getBoolean("analyzer.homeSmoke")) HomeSmoke.tick(client);
            else DevSmoke.tick(client);
        }
    }

    public static void saveConfig() {
        if (CORE == null) return;
        try { CORE.save(); } catch (IOException ex) { LOGGER.error("Could not save Analyzer configuration", ex); }
    }

    public static int getMenuKey() { return open == null ? GLFW.GLFW_KEY_RIGHT_SHIFT : KeyBindingHelper.getBoundKeyOf(open).getCode(); }
}
