package dev.analyzer.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.analyzer.privacy.NameChanger;
import net.minecraft.client.font.TextHandler;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.BookEditScreen;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Preserve book page/title UTF-16 geometry while its ordinary author/header labels remain masked. */
@Mixin(BookEditScreen.class)
public abstract class NameChangerBookEditMixin {
    @WrapOperation(method = "createPageContent", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/font/TextRenderer;getWidth(Ljava/lang/String;)I"))
    private int analyzer$rawPageWidth(TextRenderer renderer, String text, Operation<Integer> original) {
        try (var scope = NameChanger.editable()) { return original.call(renderer, text); }
    }
    @WrapOperation(method = {"createPageContent", "getLineSelectionRectangle"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/font/TextHandler;getWidth(Ljava/lang/String;)F"))
    private float analyzer$rawSelectionWidth(TextHandler handler, String text, Operation<Float> original) {
        try (var scope = NameChanger.editable()) { return original.call(handler, text); }
    }
    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/font/TextRenderer;getWidth(Lnet/minecraft/text/OrderedText;)I"))
    private int analyzer$rawTitleWidth(TextRenderer renderer, OrderedText text, Operation<Integer> original) {
        try (var scope = NameChanger.editable()) { return original.call(renderer, text); }
    }
    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/DrawContext;drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/OrderedText;IIIZ)I"))
    private int analyzer$rawTitle(DrawContext context, TextRenderer renderer, OrderedText text, int x, int y, int color,
                                 boolean shadow, Operation<Integer> original) {
        try (var scope = NameChanger.editable()) { return original.call(context, renderer, text, x, y, color, shadow); }
    }
    // The first three Text calls are the ordinary edit-title, signed-by and page-number labels.
    @WrapOperation(method = "render", at = @At(value = "INVOKE", ordinal = 3,
            target = "Lnet/minecraft/client/gui/DrawContext;drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;IIIZ)I"))
    private int analyzer$rawPage(DrawContext context, TextRenderer renderer, Text text, int x, int y, int color,
                                boolean shadow, Operation<Integer> original) {
        try (var scope = NameChanger.editable()) { return original.call(context, renderer, text, x, y, color, shadow); }
    }
}
