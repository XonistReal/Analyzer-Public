package dev.analyzer.mixin;

import dev.analyzer.privacy.PrivacyTextProtection;
import dev.analyzer.privacy.PrivacyTextSignAccess;
import net.minecraft.client.gui.screen.ingame.AbstractSignEditScreen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** NBT sign lines acquire origin before the vanilla constructor maps each Text into an echoed String. */
@Mixin(AbstractSignEditScreen.class)
public abstract class PrivacyTextSignMixin implements PrivacyTextSignAccess {
    @Shadow @Final private String[] messages;
    @Override public String[] analyzer$privacyLines() { return messages.clone(); }

    @Inject(method = "method_49914", at = @At("RETURN"), cancellable = true)
    private void analyzer$remoteSignLine(boolean filtered, int row, CallbackInfoReturnable<Text> cir) {
        cir.setReturnValue(PrivacyTextProtection.markSignLine(cir.getReturnValue()));
    }
}
