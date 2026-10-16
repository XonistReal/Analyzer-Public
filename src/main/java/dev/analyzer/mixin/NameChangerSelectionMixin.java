package dev.analyzer.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.analyzer.privacy.NameChanger;
import net.minecraft.client.util.SelectionManager;
import org.spongepowered.asm.mixin.Mixin;

/** Book/sign input filters must validate actual submitted text, regardless of the shorter display alias. */
@Mixin(SelectionManager.class)
public abstract class NameChangerSelectionMixin {
    @WrapMethod(method = "insert(Ljava/lang/String;Ljava/lang/String;)V")
    private void analyzer$rawValidation(String previous, String insertion, Operation<Void> original) {
        try (var scope = NameChanger.editable()) { original.call(previous, insertion); }
    }
}
