package dev.analyzer.mixin;

import dev.analyzer.ui.HomePages;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.SliderWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Style the actual home option slider, retaining all native value updates and input. */
@Mixin(SliderWidget.class)
public abstract class HomeSliderMixin {
    @Shadow protected double value;

    @Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true)
    private void analyzer$homeSlider(DrawContext draw, int mouseX, int mouseY, float delta, CallbackInfo callback) {
        if (HomePages.ownsWidget(this)) {
            HomePages.drawNativeSlider(draw, (SliderWidget) (Object) this, value, mouseX, mouseY);
            callback.cancel();
        }
    }
}
