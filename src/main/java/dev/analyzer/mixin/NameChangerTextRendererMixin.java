package dev.analyzer.mixin;

import dev.analyzer.privacy.NameChanger;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.OrderedText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Mask at the rendering boundary, before bidi mirroring or glyph visitation. */
@Mixin(TextRenderer.class)
public abstract class NameChangerTextRendererMixin {
    @ModifyVariable(method = {
            "draw(Ljava/lang/String;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/font/TextRenderer$TextLayerType;II)I",
            "trimToWidth(Ljava/lang/String;IZ)Ljava/lang/String;", "trimToWidth(Ljava/lang/String;I)Ljava/lang/String;",
            "getWrappedLinesHeight(Ljava/lang/String;I)I"
    }, at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private String analyzer$localAliasString(String text) { return NameChanger.render(text); }

    @ModifyVariable(method = {
            "drawInternal(Lnet/minecraft/text/OrderedText;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/font/TextRenderer$TextLayerType;IIZ)I",
            "drawWithOutline(Lnet/minecraft/text/OrderedText;FFIILorg/joml/Matrix4f;Lnet/minecraft/client/render/VertexConsumerProvider;I)V"
    }, at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private OrderedText analyzer$localAliasOrdered(OrderedText text) { return NameChanger.render(text); }
}
