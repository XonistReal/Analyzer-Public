package dev.analyzer.core;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Module identity and settings are immutable; runtime values live in AnalyzerCore. */
public record ModuleDefinition(String id, String name, String description, Category category,
                               boolean defaultEnabled, List<Setting<?>> settings) {
    public ModuleDefinition {
        if (id == null || !id.matches("[a-z][a-z0-9-]*")) {
            throw new IllegalArgumentException("Invalid module id: " + id);
        }
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(category, "category");
        settings = List.copyOf(settings);
        var ids = new HashSet<String>();
        for (Setting<?> setting : settings) {
            if (!ids.add(setting.id())) {
                throw new IllegalArgumentException("Duplicate setting in " + id + ": " + setting.id());
            }
        }
    }

    public Setting<?> setting(String settingId) {
        return settings.stream().filter(setting -> setting.id().equals(settingId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown setting: " + id + "/" + settingId));
    }
}
