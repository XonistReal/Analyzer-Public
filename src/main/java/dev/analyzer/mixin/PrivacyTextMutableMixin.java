package dev.analyzer.mixin;

import dev.analyzer.privacy.PrivacyTextOrigin;
import dev.analyzer.privacy.PrivacyTextProtection;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.util.Language;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A reused ordered-text cache must not defeat later policy or language-provenance changes. */
@Mixin(MutableText.class)
public abstract class PrivacyTextMutableMixin implements PrivacyTextOrigin {
    @Shadow private OrderedText ordered;
    @Shadow private Language language;
    @Unique private boolean analyzer$remote;
    @Unique private long analyzer$epoch = Long.MIN_VALUE;
    @Override public void analyzer$markRemote() { analyzer$remote = true; ordered = null; language = null; }
    @Override public boolean analyzer$isRemote() { return analyzer$remote; }

    @Inject(method = "asOrderedText", at = @At("HEAD"))
    private void analyzer$orderedPolicyCache(CallbackInfoReturnable<OrderedText> cir) {
        if (!PrivacyTextProtection.hasRemote((MutableText) (Object) this)) return;
        long epoch = PrivacyTextProtection.policyEpoch();
        if (epoch != analyzer$epoch) { analyzer$epoch = epoch; ordered = null; language = null; }
    }
}
