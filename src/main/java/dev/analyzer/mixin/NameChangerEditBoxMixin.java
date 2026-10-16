package dev.analyzer.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.analyzer.privacy.NameChanger;
import net.minecraft.client.gui.EditBox;
import org.spongepowered.asm.mixin.Mixin;

/** Multiline editing indexes and line selection always refer to the original unmasked buffer. */
@Mixin(EditBox.class)
public abstract class NameChangerEditBoxMixin {
    @WrapMethod(method = "moveCursorLine")
    private void analyzer$rawLineCursor(int offset, Operation<Void> original) {
        try (var scope = NameChanger.editable()) { original.call(offset); }
    }
    @WrapMethod(method = "moveCursor(DD)V")
    private void analyzer$rawMouseCursor(double x, double y, Operation<Void> original) {
        try (var scope = NameChanger.editable()) { original.call(x, y); }
    }
    @WrapMethod(method = "rewrap")
    private void analyzer$rawLines(Operation<Void> original) {
        try (var scope = NameChanger.editable()) { original.call(); }
    }
}
