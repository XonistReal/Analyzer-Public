package dev.analyzer.mixin;

import dev.analyzer.privacy.PackPrivacy;
import dev.analyzer.privacy.ServerPrivacy;
import net.minecraft.SharedConstants;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Map;

@Mixin(targets = "net.minecraft.client.resource.server.ServerResourcePackLoader$4")
public abstract class PrivacyPackHeadersMixin {
    @Inject(method = "getHeaders", at = @At("RETURN"), cancellable = true)
    private void analyzer$scrubIdentifyingHeaders(CallbackInfoReturnable<Map<String, String>> cir) {
        if (ServerPrivacy.option("scrub-pack-headers"))
            cir.setReturnValue(PackPrivacy.scrub(cir.getReturnValue(), SharedConstants.getGameVersion().getName()));
    }
}
