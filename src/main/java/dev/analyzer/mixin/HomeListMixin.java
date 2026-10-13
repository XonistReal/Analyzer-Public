package dev.analyzer.mixin;

import dev.analyzer.ui.HomePages;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.EntryListWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The home pane supplies glass behind its real lists; other list backgrounds remain native. */
@Mixin(EntryListWidget.class)
public abstract class HomeListMixin {
    @Inject(method = {"drawMenuListBackground", "drawHeaderAndFooterSeparators"}, at = @At("HEAD"), cancellable = true)
    private void analyzer$homeListBackground(DrawContext draw, CallbackInfo callback) {
        if (HomePages.ownsList(this)) callback.cancel();
    }
}
