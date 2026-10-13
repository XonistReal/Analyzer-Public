package dev.analyzer.mixin;

import dev.analyzer.privacy.PrivacyTextProtection;
import net.minecraft.client.resource.language.TranslationStorage;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.function.BiConsumer;

/** The final winning resource source determines whether a remotely supplied translation is legitimate. */
@Mixin(TranslationStorage.class)
public abstract class PrivacyLanguageStorageMixin {
    private static final String FULL_LOAD = "load(Lnet/minecraft/resource/ResourceManager;Ljava/util/List;Z)Lnet/minecraft/client/resource/language/TranslationStorage;";
    private static final String FILE_LOAD = "load(Ljava/lang/String;Ljava/util/List;Ljava/util/Map;)V";

    @Inject(method = FULL_LOAD, at = @At("HEAD"))
    private static void analyzer$beginOrigins(ResourceManager resources, List<String> languages, boolean rtl, CallbackInfoReturnable<TranslationStorage> cir) {
        PrivacyTextProtection.beginLanguageLoad();
    }
    @Redirect(method = FILE_LOAD, at = @At(value = "INVOKE", target = "Lnet/minecraft/resource/Resource;getInputStream()Ljava/io/InputStream;"))
    private static InputStream analyzer$tagSource(Resource resource) throws IOException {
        return PrivacyTextProtection.languageStream(resource);
    }
    @Redirect(method = FILE_LOAD, at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Language;load(Ljava/io/InputStream;Ljava/util/function/BiConsumer;)V"))
    private static void analyzer$collectSource(InputStream stream, BiConsumer<String, String> target) {
        PrivacyTextProtection.loadLanguage(stream, target);
    }
    @Inject(method = FULL_LOAD, at = @At("RETURN"))
    private static void analyzer$finishOrigins(ResourceManager resources, List<String> languages, boolean rtl, CallbackInfoReturnable<TranslationStorage> cir) {
        PrivacyTextProtection.finishLanguageLoad();
    }
}
