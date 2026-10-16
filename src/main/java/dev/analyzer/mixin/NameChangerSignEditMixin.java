package dev.analyzer.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.analyzer.privacy.NameChanger;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.AbstractSignEditScreen;
import org.spongepowered.asm.mixin.Mixin;

/** Only the editable sign face uses raw drawing/selection widths; the surrounding screen stays masked. */
@Mixin(AbstractSignEditScreen.class)
public abstract class NameChangerSignEditMixin {
    @WrapMethod(method = "renderSignText")
    private void analyzer$rawSignFace(DrawContext context, Operation<Void> original) {
        try (var scope = NameChanger.editable()) { original.call(context); }
    }
}
