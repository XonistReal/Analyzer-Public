package dev.analyzer.mixin;

import dev.analyzer.ui.HomePages;
import dev.analyzer.ui.HomeScreen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.PressableWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Retain ClickableWidget's hover, tooltip and narration lifecycle around home button visuals. */
@Mixin(PressableWidget.class)
public abstract class HomeWidgetMixin {
    @Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true)
    private void analyzer$homeButton(DrawContext draw, int mouseX, int mouseY, float delta, CallbackInfo callback) {
        if ((Object) this instanceof ButtonWidget button && HomePages.ownsWidget(button)) {
            HomeScreen.drawNativeButton(draw, button, mouseX, mouseY);
            callback.cancel();
        }
    }
}
