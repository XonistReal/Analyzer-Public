package dev.analyzer.mixin;

import dev.analyzer.privacy.NameChanger;
import net.minecraft.client.font.TextHandler;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Width, styled hit testing and visual wrapping use exactly the same disposable alias as drawing. */
@Mixin(TextHandler.class)
public abstract class NameChangerTextHandlerMixin {
    @ModifyVariable(method = {"getWidth(Ljava/lang/String;)F",
            "wrapLines(Ljava/lang/String;ILnet/minecraft/text/Style;)Ljava/util/List;"},
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private String analyzer$localAliasString(String text) { return NameChanger.render(text); }

    @ModifyVariable(method = {"getWidth(Lnet/minecraft/text/StringVisitable;)F",
            "getStyleAt(Lnet/minecraft/text/StringVisitable;I)Lnet/minecraft/text/Style;",
            "trimToWidth(Lnet/minecraft/text/StringVisitable;ILnet/minecraft/text/Style;)Lnet/minecraft/text/StringVisitable;",
            "wrapLines(Lnet/minecraft/text/StringVisitable;ILnet/minecraft/text/Style;)Ljava/util/List;",
            "wrapLines(Lnet/minecraft/text/StringVisitable;ILnet/minecraft/text/Style;Lnet/minecraft/text/StringVisitable;)Ljava/util/List;",
            "wrapLines(Lnet/minecraft/text/StringVisitable;ILnet/minecraft/text/Style;Ljava/util/function/BiConsumer;)V"},
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private StringVisitable analyzer$localAliasVisitable(StringVisitable text) { return NameChanger.render(text); }

    @ModifyVariable(method = {"getWidth(Lnet/minecraft/text/OrderedText;)F", "getStyleAt(Lnet/minecraft/text/OrderedText;I)Lnet/minecraft/text/Style;"},
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private OrderedText analyzer$localAliasOrdered(OrderedText text) { return NameChanger.render(text); }
}
