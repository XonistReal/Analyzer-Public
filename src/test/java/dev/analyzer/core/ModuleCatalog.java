package dev.analyzer.core;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Export the actual typed registry for release documentation and tooling. */
public final class ModuleCatalog {
    public static void main(String[] args) throws Exception {
        var gson = new GsonBuilder().setPrettyPrinting().create();
        JsonArray catalog = new JsonArray();
        var modules = BuiltinModules.definitions();
        int settingCount = modules.stream().mapToInt(module -> module.settings().size()).sum();
        StringBuilder markdown = new StringBuilder("# Analyzer modules and settings\n\n")
                .append(modules.size()).append(" registered modules and ").append(settingCount)
                .append(" exposed settings. Generated from the actual in-game registry; defaults describe a fresh configuration. HUD Studio appearance/Home aliases share their canonical Interface values.\n\n")
                .append("[Using modules and profiles](docs/MODULE-GUIDE.md) · [Install](docs/INSTALLATION.md) · [README](README.md)\n\n")
                .append("Choose a category, then click a module for its complete settings. Descriptions state implemented scope; reference names do not imply feature parity. Gameplay automation is opt-in. Some controls appear in game only for their relevant mode.\n\n");
        for (Category category : Category.values()) {
            markdown.append("[").append(category.label()).append("](#")
                    .append(category.label().toLowerCase(Locale.ROOT)).append(") · ");
        }
        markdown.setLength(markdown.length() - 3);
        markdown.append("\n\n");
        for (Category category : Category.values()) {
            markdown.append("## ").append(category.label()).append("\n\n| Module | Behavior | Default |\n| --- | --- | --- |\n");
            for (var module : modules) if (module.category() == category) {
                markdown.append("| [").append(cell(module.name())).append("](#module-").append(module.id())
                        .append(") | ").append(cell(module.description())).append(" | ")
                        .append(module.defaultEnabled() ? "On" : "Off").append(" |\n");
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
                if (setting.kind() == Setting.Kind.TEXT) {
                    data.addProperty("maxLength", (int)setting.max());
                    data.addProperty("format", setting.min() == 0
                            ? "Local file path; no control characters or section sign"
                            : "ASCII letters, numbers, underscore");
                }
                settings.add(data);
            }
            entry.add("settings", settings); catalog.add(entry);
        }
        markdown.append("## Settings reference\n\n")
                .append("Numeric values are inclusive and use the listed step. Text trims outer whitespace. ")
                .append("File selections use their dedicated browser. Per-module keybinds and the module's enable switch are separate controls.\n\n");
        for (var module : modules) {
            markdown.append("<a id=\"module-").append(module.id()).append("\"></a>\n\n### ")
                    .append(cell(module.name())).append("\n\n**")
                    .append(module.category().label()).append("** · ID: `").append(module.id())
                    .append("` · Default: **").append(module.defaultEnabled() ? "On" : "Off").append("**\n\n")
                    .append(module.description()).append("\n\n");
            if (module.settings().isEmpty()) {
                markdown.append("No additional module settings.\n\n");
            } else {
                markdown.append("| Setting | Type | Default | Allowed values |\n| --- | --- | --- | --- |\n");
                for (var setting : module.settings()) {
                    markdown.append("| ").append(cell(setting.label())).append(" (`").append(setting.id())
                            .append("`) | ").append(kindLabel(setting.kind())).append(" | ")
                            .append(defaultLabel(setting)).append(" | ").append(allowedValues(setting)).append(" |\n");
                }
                markdown.append("\n");
            }
            markdown.append("[Back to ").append(module.category().label()).append("](#")
                    .append(module.category().label().toLowerCase(Locale.ROOT)).append(")\n\n");
        }
        markdown.append("---\n\nGenerated by `./gradlew moduleCatalog` from `BuiltinModules.definitions()`. ")
                .append("Edit the typed definitions and regenerate both catalogue files when controls change.\n");
        Files.writeString(Path.of(args[0]), gson.toJson(catalog) + "\n");
        Files.writeString(Path.of(args[1]), markdown);
        System.out.println("Exported " + modules.size() + " real module definitions.");
    }

    private static String cell(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("|", "\\|").replace("\r", " ").replace("\n", " ");
    }

    private static String number(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private static String kindLabel(Setting.Kind kind) {
        return switch (kind) {
            case BOOLEAN -> "Boolean";
            case NUMBER -> "Number";
            case CHOICE -> "Choice";
            case TEXT -> "Text";
        };
    }

    private static String defaultLabel(Setting<?> setting) {
        return switch (setting.kind()) {
            case BOOLEAN -> Boolean.TRUE.equals(setting.defaultValue()) ? "On" : "Off";
            case NUMBER -> number(((Number)setting.defaultValue()).doubleValue());
            case CHOICE, TEXT -> setting.defaultValue().toString().isEmpty() ? "Empty" : cell(setting.defaultValue().toString());
        };
    }

    private static String allowedValues(Setting<?> setting) {
        return switch (setting.kind()) {
            case BOOLEAN -> "On / Off";
            case NUMBER -> number(setting.min()) + " to " + number(setting.max()) + "; step " + number(setting.step());
            case CHOICE -> cell(String.join(" / ", setting.choices()));
            case TEXT -> setting.min() == 0
                    ? "Local path, 0–" + (int)setting.max() + " characters; no control characters or §"
                    : "1–" + (int)setting.max() + " ASCII letters, numbers or underscore";
        };
    }
}
