package dev.analyzer.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.analyzer.privacy.NameChanger;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Mixin;

/** Editable contents use real UTF-16 positions, so redaction must not alter caret or submission semantics. */
@Mixin(TextFieldWidget.class)
public abstract class NameChangerEditableMixin {
    @WrapMethod(method = {"setText", "write"})
    private void analyzer$rawInput(String text, Operation<Void> original) {
        try (var scope = NameChanger.editable()) { original.call(text); }
    }
    @WrapMethod(method = "renderWidget")
    private void analyzer$rawEditor(DrawContext context, int mouseX, int mouseY, float delta, Operation<Void> original) {
        try (var scope = NameChanger.editable()) { original.call(context, mouseX, mouseY, delta); }
    }
    @WrapMethod(method = "onClick")
    private void analyzer$rawClick(double mouseX, double mouseY, Operation<Void> original) {
        try (var scope = NameChanger.editable()) { original.call(mouseX, mouseY); }
    }
    @WrapMethod(method = "updateFirstCharacterIndex")
    private void analyzer$rawScroll(int cursor, Operation<Void> original) {
        try (var scope = NameChanger.editable()) { original.call(cursor); }
    }
    @WrapMethod(method = "getCharacterX")
    private int analyzer$rawCharacterPosition(int index, Operation<Integer> original) {
        try (var scope = NameChanger.editable()) { return original.call(index); }
    }
}
