package dev.analyzer.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.analyzer.privacy.NameChanger;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.EditBoxWidget;
import org.spongepowered.asm.mixin.Mixin;

/** Keep the native multiline text, caret and selection geometry in the same raw coordinate system. */
@Mixin(EditBoxWidget.class)
public abstract class NameChangerEditBoxWidgetMixin {
    @WrapMethod(method = "renderContents")
    private void analyzer$rawContents(DrawContext context, int mouseX, int mouseY, float delta, Operation<Void> original) {
        try (var scope = NameChanger.editable()) { original.call(context, mouseX, mouseY, delta); }
    }
}
