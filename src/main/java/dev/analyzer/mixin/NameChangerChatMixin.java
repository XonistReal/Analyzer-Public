package dev.analyzer.mixin;

import dev.analyzer.privacy.NameChanger;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Retain original signed/source messages, refreshing only their cached visual lines after edits. */
@Mixin(ChatHud.class)
public abstract class NameChangerChatMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void analyzer$refreshLocalAlias(CallbackInfo ci) { NameChanger.refreshChat(MinecraftClient.getInstance()); }
}
