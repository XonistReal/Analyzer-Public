package dev.analyzer.mixin;

import dev.analyzer.AnalyzerClient;
import dev.analyzer.ui.HomeScreen;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Replace only Minecraft's ordinary title, leaving mod subclasses and demo rules intact. */
@Mixin(MinecraftClient.class)
public abstract class HomeTitleMixin {
    @Shadow private boolean disconnecting;
    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen analyzer$home(Screen screen) {
        var client = (MinecraftClient) (Object) this;
        var core = AnalyzerClient.CORE;
        if (core == null || !core.getBoolean("menu", "home-enabled") || client.isDemo()) return screen;
        if (FabricLoader.getInstance().isDevelopmentEnvironment() && Boolean.getBoolean("analyzer.smoke") && !Boolean.getBoolean("analyzer.homeSmoke")) return screen;
        if ((screen != null && screen.getClass() == TitleScreen.class) || (screen == null && client.world == null && !disconnecting)) return new HomeScreen(core);
        return screen;
    }
}
