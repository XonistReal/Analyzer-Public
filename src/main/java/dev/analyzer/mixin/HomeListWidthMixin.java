package dev.analyzer.mixin;

import dev.analyzer.ui.HomePages;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerServerListWidget;
import net.minecraft.client.gui.screen.world.WorldListWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native fixed row widths adapt only inside Analyzer's home page panes. */
@Mixin({WorldListWidget.class, MultiplayerServerListWidget.class})
public abstract class HomeListWidthMixin {
    @Inject(method = "getRowWidth", at = @At("RETURN"), cancellable = true)
    private void analyzer$homeRowWidth(CallbackInfoReturnable<Integer> callback) {
        callback.setReturnValue(HomePages.rowWidth(this, callback.getReturnValue()));
    }
}
