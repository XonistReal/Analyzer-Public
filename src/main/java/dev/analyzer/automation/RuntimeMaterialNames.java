package dev.analyzer.automation;

import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import java.util.LinkedHashMap;

/** Names come from the running game's vanilla item registry, independently of implemented supply jobs. */
public final class RuntimeMaterialNames {
    private RuntimeMaterialNames() { }
    public static void refresh() {
        var names = new LinkedHashMap<String, String>();
        var stacks = new LinkedHashMap<String, Integer>();
        Registries.ITEM.forEach(item -> {
            var id = Registries.ITEM.getId(item);
            if (item != Items.AIR && id.getNamespace().equals("minecraft")) {
                names.put(id.getPath(), item.getName().getString());
                stacks.put(id.getPath(), new net.minecraft.item.ItemStack(item).getMaxCount());
            }
        });
        ResourceCatalog.registerItemNames(names);
        ResourceCatalog.registerStackSizes(stacks);
    }
}
