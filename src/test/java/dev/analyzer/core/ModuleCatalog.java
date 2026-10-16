package dev.analyzer.core;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;

/** Export the actual typed registry for release documentation and tooling. */
public final class ModuleCatalog {
    public static void main(String[] args) throws Exception {
        var gson = new GsonBuilder().setPrettyPrinting().create();
        JsonArray catalog = new JsonArray();
        var modules = BuiltinModules.definitions();
        StringBuilder markdown = new StringBuilder("# Analyzer module catalogue\n\n" + modules.size() + " registered modules. Descriptions state the implemented scope; matching reference names do not imply feature parity. Gameplay automation is opt-in.\n\n");
        for (Category category : Category.values()) {
            markdown.append("## ").append(category.label()).append("\n\n| Module | Behavior | Default |\n| --- | --- | --- |\n");
            for (var module : modules) if (module.category() == category) {
                markdown.append("| ").append(module.name()).append(" | ").append(module.description().replace("|", "\\|")).append(" | ").append(module.defaultEnabled() ? "On" : "Off").append(" |\n");
            }
            markdown.append("\n");
        }
        for (var module : modules) {
            JsonObject entry = new JsonObject();
            entry.addProperty("id", module.id()); entry.addProperty("name", module.name());
            entry.addProperty("category", module.category().label()); entry.addProperty("description", module.description());
            entry.addProperty("defaultEnabled", module.defaultEnabled());
            JsonArray settings = new JsonArray();
            for (var setting : module.settings()) {
                JsonObject data = new JsonObject(); data.addProperty("id", setting.id()); data.addProperty("label", setting.label());
                data.addProperty("kind", setting.kind().name()); data.add("default", gson.toJsonTree(setting.defaultValue()));
                if (setting.kind() == Setting.Kind.NUMBER) { data.addProperty("min", setting.min()); data.addProperty("max", setting.max()); data.addProperty("step", setting.step()); }
                if (setting.kind() == Setting.Kind.CHOICE) data.add("choices", gson.toJsonTree(setting.choices()));
                if (setting.kind() == Setting.Kind.TEXT) { data.addProperty("maxLength", (int)setting.max()); data.addProperty("format", "ASCII letters, numbers, underscore"); }
                settings.add(data);
            }
            entry.add("settings", settings); catalog.add(entry);
        }
        Files.writeString(Path.of(args[0]), gson.toJson(catalog) + "\n");
        Files.writeString(Path.of(args[1]), markdown);
        System.out.println("Exported " + modules.size() + " real module definitions.");
    }
}
