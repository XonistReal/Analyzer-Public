package dev.analyzer.mixin;

import dev.analyzer.privacy.PrivacyTextOrigin;
import dev.analyzer.privacy.PrivacyTextProtection;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Language;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Preserve Minecraft's fallback formatting; suppress only remote non-vanilla/non-server-pack keys. */
@Mixin(TranslatableTextContent.class)
public abstract class PrivacyTextTranslationMixin implements PrivacyTextOrigin {
    @Shadow private Language languageCache;
    @Unique private boolean analyzer$remote;
    @Unique private long analyzer$epoch = Long.MIN_VALUE;
    @Override public void analyzer$markRemote() { analyzer$remote = true; languageCache = null; }
    @Override public boolean analyzer$isRemote() { return analyzer$remote; }

    @Inject(method = "updateTranslations", at = @At("HEAD"))
    private void analyzer$policyCache(CallbackInfo ci) {
        if (!analyzer$remote) return;
        long epoch = PrivacyTextProtection.policyEpoch();
        if (epoch != analyzer$epoch) { analyzer$epoch = epoch; languageCache = null; }
    }
    @Redirect(method = "updateTranslations", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Language;get(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"))
    private String analyzer$explicitFallback(Language language, String key, String fallback) {
        return PrivacyTextProtection.hideTranslation(key, analyzer$remote) ? fallback : language.get(key, fallback);
    }
    @Redirect(method = "updateTranslations", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Language;get(Ljava/lang/String;)Ljava/lang/String;"))
    private String analyzer$rawKey(Language language, String key) {
        return PrivacyTextProtection.hideTranslation(key, analyzer$remote) ? key : language.get(key);
    }
}
