package dev.analyzer.mixin;

import dev.analyzer.privacy.PrivacyTextOrigin;
import dev.analyzer.privacy.PrivacyTextProtection;
import net.minecraft.text.KeybindTextContent;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Local key labels and actual vanilla bindings retain their normal behavior. */
@Mixin(KeybindTextContent.class)
public abstract class PrivacyTextKeybindMixin implements PrivacyTextOrigin {
    @Shadow @Final private String key;
    @Unique private boolean analyzer$remote;
    @Override public void analyzer$markRemote() { analyzer$remote = true; }
    @Override public boolean analyzer$isRemote() { return analyzer$remote; }

    @Inject(method = "getTranslated", at = @At("HEAD"), cancellable = true)
    private void analyzer$remoteKeybind(CallbackInfoReturnable<Text> cir) {
        if (PrivacyTextProtection.hideKeybind(key, analyzer$remote)) cir.setReturnValue(Text.literal(key));
    }
}
