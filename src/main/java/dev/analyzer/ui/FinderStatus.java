package dev.analyzer.ui;

import dev.analyzer.core.AnalyzerCore;
import dev.analyzer.core.ModuleDefinition;
import dev.analyzer.core.EntityFinderModules;
import dev.analyzer.finder.FinderEngine;
import dev.analyzer.render.EntityFinderRenderer;

/** Short live feedback instead of implying that incremental surveys complete instantly. */
final class FinderStatus {
    private static final java.util.Set<String> ENTITY_IDS = EntityFinderModules.definitions().stream()
            .map(ModuleDefinition::id).collect(java.util.stream.Collectors.toUnmodifiableSet());
    private FinderStatus() { }
    static String description(AnalyzerCore core, ModuleDefinition module) {
        if (!core.isEnabled(module.id()) || module.id().equals("region-map")
                || module.settings().stream().noneMatch(value -> value.id().equals("scan-budget"))) return module.description();
        if (ENTITY_IDS.contains(module.id()))
            return EntityFinderRenderer.status(module.id());
        return FinderEngine.status(module.id());
    }
}
