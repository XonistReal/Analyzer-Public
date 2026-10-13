package dev.analyzer.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Version-independent module state and configuration facade.
 * UI and rendering code share this single source of values. File I/O is explicit.
 */
public final class AnalyzerCore {
    public static final int UNBOUND_KEY = -1;
    private static final int MAX_KEY = 348;

    private final List<ModuleDefinition> modules;
    private final Map<String, ModuleDefinition> definitions = new LinkedHashMap<>();
    private final Map<String, ModuleState> states = new LinkedHashMap<>();
    private final Map<SettingKey, SettingKey> aliases = new LinkedHashMap<>();
    private final ConfigStore config;
    private List<String> loadWarnings = List.of();

    public AnalyzerCore(Path configDirectory) {
        this(configDirectory, BuiltinModules.definitions());
    }

    public AnalyzerCore(Path configDirectory, List<ModuleDefinition> modules) {
        this.modules = List.copyOf(modules);
        for (ModuleDefinition module : this.modules) {
            if (definitions.putIfAbsent(module.id(), module) != null) {
                throw new IllegalArgumentException("Duplicate module: " + module.id());
            }
        }
        // HUD Studio exposes the same menu controls as Interface, with one stored value.
        registerHudMenuAlias("menu-layout", "layout");
        registerHudMenuAlias("menu-material", "material");
        registerHudMenuAlias("menu-theme", "theme");
        registerHudMenuAlias("menu-accent", "accent");
        for (String home : List.of("home-enabled", "home-background", "home-motion", "home-blur")) registerHudMenuAlias(home, home);
        for (ModuleDefinition module : this.modules) states.put(module.id(), defaults(module));
        config = new ConfigStore(configDirectory);
    }

    public List<ModuleDefinition> modules() { return modules; }

    public ModuleDefinition definition(String moduleId) {
        ModuleDefinition module = definitions.get(moduleId);
        if (module == null) throw new IllegalArgumentException("Unknown module: " + moduleId);
        return module;
    }

    public synchronized boolean isEnabled(String moduleId) { return state(moduleId).enabled; }
    public synchronized void setEnabled(String moduleId, boolean enabled) { state(moduleId).enabled = enabled; }
    public synchronized void toggle(String moduleId) { ModuleState state = state(moduleId); state.enabled = !state.enabled; }
    public synchronized int keybind(String moduleId) { return state(moduleId).keybind; }

    /** GLFW keyboard key code, or -1 for unbound. Dispatch belongs to the game adapter. */
    public synchronized void setKeybind(String moduleId, int key) {
        if (key < UNBOUND_KEY || key > MAX_KEY) throw new IllegalArgumentException("Invalid keyboard key: " + key);
        state(moduleId).keybind = key;
    }

    public synchronized Object getValue(String moduleId, String settingId) {
        definition(moduleId).setting(settingId);
        SettingKey storage = storageKey(moduleId, settingId);
        return state(storage.module()).values.get(storage.setting());
    }

    public synchronized <T> T getValue(String moduleId, Setting<T> setting) {
        Setting<?> registered = definition(moduleId).setting(setting.id());
        if (registered != setting) throw new IllegalArgumentException("Setting does not belong to " + moduleId);
        return setting.valueType().cast(getValue(moduleId, setting.id()));
    }

    public boolean getBoolean(String moduleId, String settingId) {
        return Boolean.class.cast(getValue(moduleId, settingId));
    }

    public double getNumber(String moduleId, String settingId) {
        return Double.class.cast(getValue(moduleId, settingId));
    }

    public String getChoice(String moduleId, String settingId) {
        return String.class.cast(getValue(moduleId, settingId));
    }

    public synchronized void setValue(String moduleId, String settingId, Object value) {
        Setting<?> setting = definition(moduleId).setting(settingId);
        Object normalized = setting.normalize(value);
        SettingKey storage = storageKey(moduleId, settingId);
        Setting<?> canonical = definition(storage.module()).setting(storage.setting());
        state(storage.module()).values.put(storage.setting(), canonical.normalize(normalized));
    }

    /** An immutable snapshot suitable for exporting or displaying the current settings. */
    public synchronized Map<String, Object> values(String moduleId) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (Setting<?> setting : definition(moduleId).settings()) values.put(setting.id(), getValue(moduleId, setting.id()));
        return Collections.unmodifiableMap(values);
    }

    public synchronized void reset(String moduleId) {
        states.put(moduleId, defaults(definition(moduleId)));
        for (var alias : aliases.entrySet()) if (alias.getKey().module().equals(moduleId)) {
            SettingKey target = alias.getValue();
            state(target.module()).values.put(target.setting(), definition(target.module()).setting(target.setting()).defaultValue());
        }
    }

    public synchronized void resetAll() {
        for (ModuleDefinition module : modules) states.put(module.id(), defaults(module));
    }

    /** A missing config preserves defaults. Malformed files leave current values intact. */
    public synchronized void load() throws IOException {
        try {
            JsonObject document = config.readActive();
            if (document != null) apply(document);
        } catch (IOException ex) {
            // Include structurally rejected documents, such as a missing modules
            // object, in the same recovery policy as parse and version failures.
            config.preserveActiveBeforeWrite();
            throw ex;
        }
    }

    public synchronized void save() throws IOException { config.writeActive(snapshot()); }
    public List<String> profiles() throws IOException { return config.profiles(); }
    public synchronized void saveProfile(String name) throws IOException { config.writeProfile(name, snapshot()); }

    /** Loads all settings from a named snapshot. Call save() to make it the active config. */
    public synchronized void loadProfile(String name) throws IOException { apply(config.readProfile(name)); }
    public void deleteProfile(String name) throws IOException { config.deleteProfile(name); }
    public synchronized List<String> loadWarnings() { return loadWarnings; }

    private ModuleState state(String moduleId) {
        ModuleState state = states.get(moduleId);
        if (state == null) throw new IllegalArgumentException("Unknown module: " + moduleId);
        return state;
    }

    private ModuleState defaults(ModuleDefinition module) {
        ModuleState state = new ModuleState();
        state.enabled = module.defaultEnabled();
        for (Setting<?> setting : module.settings()) if (!aliases.containsKey(new SettingKey(module.id(), setting.id()))) {
            state.values.put(setting.id(), setting.defaultValue());
        }
        return state;
    }

    private void registerHudMenuAlias(String aliasId, String canonicalId) {
        ModuleDefinition hud = definitions.get("hud"), menu = definitions.get("menu");
        if (hud == null || menu == null) return;
        Setting<?> alias = hud.settings().stream().filter(setting -> setting.id().equals(aliasId)).findFirst().orElse(null);
        Setting<?> canonical = menu.settings().stream().filter(setting -> setting.id().equals(canonicalId)).findFirst().orElse(null);
        if (alias == null || canonical == null) return;
        if (alias.kind() != canonical.kind() || alias.valueType() != canonical.valueType()
                || !alias.choices().equals(canonical.choices())) {
            throw new IllegalArgumentException("HUD menu alias must match menu/" + canonicalId);
        }
        aliases.put(new SettingKey("hud", aliasId), new SettingKey("menu", canonicalId));
    }

    private SettingKey storageKey(String moduleId, String settingId) {
        SettingKey key = new SettingKey(moduleId, settingId);
        return aliases.getOrDefault(key, key);
    }

    private JsonObject snapshot() {
        JsonObject document = new JsonObject();
        document.addProperty("schemaVersion", ConfigStore.SCHEMA_VERSION);
        JsonObject moduleData = new JsonObject();
        for (ModuleDefinition module : modules) {
            ModuleState state = states.get(module.id());
            JsonObject entry = new JsonObject();
            entry.addProperty("enabled", state.enabled);
            entry.addProperty("keybind", state.keybind);
            JsonObject settings = new JsonObject();
            state.values.forEach((id, value) -> {
                if (value instanceof Boolean bool) settings.addProperty(id, bool);
                else if (value instanceof Number number) settings.addProperty(id, number);
                else settings.addProperty(id, (String) value);
            });
            entry.add("settings", settings);
            moduleData.add(module.id(), entry);
        }
        document.add("modules", moduleData);
        return document;
    }

    private void apply(JsonObject document) throws IOException {
        JsonElement moduleElement = document.get("modules");
        if (moduleElement == null || !moduleElement.isJsonObject()) {
            throw new IOException("Analyzer config is missing the modules object");
        }
        JsonObject moduleData = moduleElement.getAsJsonObject();
        Map<String, ModuleState> loaded = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        for (ModuleDefinition module : modules) {
            ModuleState state = defaults(module);
            loaded.put(module.id(), state);
            JsonElement entryElement = moduleData.get(module.id());
            if (entryElement == null) continue;
            if (!entryElement.isJsonObject()) {
                warnings.add("Ignored invalid module " + module.id());
                continue;
            }
            JsonObject entry = entryElement.getAsJsonObject();
            JsonElement enabled = entry.get("enabled");
            if (enabled instanceof JsonPrimitive primitive && primitive.isBoolean()) {
                state.enabled = primitive.getAsBoolean();
            }
            JsonElement key = entry.get("keybind");
            if (key instanceof JsonPrimitive primitive && primitive.isNumber()) {
                double numericKey = primitive.getAsDouble();
                if (Double.isFinite(numericKey) && numericKey == Math.rint(numericKey)
                        && numericKey >= UNBOUND_KEY && numericKey <= MAX_KEY) {
                    state.keybind = (int) numericKey;
                } else warnings.add("Ignored invalid keybind for " + module.id());
            }
            JsonElement settingsElement = entry.get("settings");
            if (settingsElement == null) continue;
            if (!settingsElement.isJsonObject()) {
                warnings.add("Ignored invalid settings for " + module.id());
                continue;
            }
            JsonObject settings = settingsElement.getAsJsonObject();
            for (Setting<?> setting : module.settings()) {
                if (aliases.containsKey(new SettingKey(module.id(), setting.id()))) continue;
                JsonElement value = settings.get(setting.id());
                if (value == null) continue;
                try {
                    Object decoded = decode(setting, value);
                    state.values.put(setting.id(), setting.normalize(decoded));
                } catch (IllegalArgumentException | IllegalStateException ex) {
                    warnings.add("Ignored invalid setting " + module.id() + "/" + setting.id());
                }
            }
        }
        applyAliases(moduleData, loaded, warnings);
        states.clear();
        states.putAll(loaded);
        loadWarnings = List.copyOf(warnings);
    }

    /** Legacy Interface values win; alias-only imports can still set menu preferences. */
    private void applyAliases(JsonObject moduleData, Map<String, ModuleState> loaded, List<String> warnings) {
        for (var alias : aliases.entrySet()) {
            SettingKey source = alias.getKey(), target = alias.getValue();
            JsonElement aliasValue = settingValue(moduleData, source);
            if (aliasValue == null) continue;
            Setting<?> aliasSetting = definition(source.module()).setting(source.setting());
            Setting<?> targetSetting = definition(target.module()).setting(target.setting());
            Object normalized;
            try { normalized = aliasSetting.normalize(decode(aliasSetting, aliasValue)); }
            catch (IllegalArgumentException | IllegalStateException ex) {
                warnings.add("Ignored invalid setting " + source.module() + "/" + source.setting());
                continue;
            }
            JsonElement canonicalValue = settingValue(moduleData, target);
            if (canonicalValue != null) {
                try { targetSetting.normalize(decode(targetSetting, canonicalValue)); continue; }
                catch (IllegalArgumentException | IllegalStateException ex) { /* A valid alias recovers an invalid canonical field. */ }
            }
            loaded.get(target.module()).values.put(target.setting(), targetSetting.normalize(normalized));
        }
    }

    private static JsonElement settingValue(JsonObject modules, SettingKey key) {
        JsonElement module = modules.get(key.module());
        if (module == null || !module.isJsonObject()) return null;
        JsonElement settings = module.getAsJsonObject().get("settings");
        return settings != null && settings.isJsonObject() ? settings.getAsJsonObject().get(key.setting()) : null;
    }

    private static Object decode(Setting<?> setting, JsonElement value) {
        if (!(value instanceof JsonPrimitive primitive)) throw new IllegalArgumentException("Expected primitive");
        return switch (setting.kind()) {
            case BOOLEAN -> {
                if (!primitive.isBoolean()) throw new IllegalArgumentException("Expected boolean");
                yield primitive.getAsBoolean();
            }
            case NUMBER -> {
                if (!primitive.isNumber()) throw new IllegalArgumentException("Expected number");
                yield primitive.getAsDouble();
            }
            case CHOICE -> {
                if (!primitive.isString()) throw new IllegalArgumentException("Expected string");
                yield primitive.getAsString();
            }
        };
    }

    private record SettingKey(String module, String setting) { }

    private static final class ModuleState {
        private boolean enabled;
        private int keybind = UNBOUND_KEY;
        private final Map<String, Object> values = new LinkedHashMap<>();
    }
}
