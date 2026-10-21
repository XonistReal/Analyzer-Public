package dev.analyzer.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/** Dependency-free assertions; run with the project's coreTest Gradle task. */
public final class CoreSelfTest {
    private static int assertions;
    public static void main(String[] args) throws Exception {
        Path temporary = Files.createTempDirectory("analyzer-core-test-");
        try {
            AnalyzerCore core = new AnalyzerCore(temporary);
            check(core.modules().size() == 160, "Release catalogue includes 160 registered modules");
            check(core.modules().stream().map(ModuleDefinition::id).distinct().count() == core.modules().size(), "Unique module IDs");
            for (Category category : Category.values())
                check(core.modules().stream().anyMatch(module -> module.category() == category), "Populated category " + category);
            for (ModuleDefinition module : core.modules()) {
                check(module.settings().stream().map(Setting::id).distinct().count() == module.settings().size(), "Unique settings " + module.id());
                if (module.category() == Category.COMBAT || module.category() == Category.MOVEMENT || module.category() == Category.HVH || module.category() == Category.PLAYER)
                    check(!core.isEnabled(module.id()), "Gameplay opt-in " + module.id());
            }
            check(!core.isEnabled("expedition"), "Automation never starts by default");
            check(core.getNumber("entity-esp", "label-range") < core.getNumber("entity-esp", "range"), "ESP labels have an independent nearer range");
            check(core.getChoice("entity-esp", "style").equals("Boxes") && core.getChoice("storage-esp", "style").equals("Boxes"),
                    "Fresh ESP uses complete debug boxes");
            check(core.getChoice("menu", "material").equals("Glass"), "Glass interface default");
            check(core.getChoice("menu", "layout").equals("Studio"), "New installs open the Studio menu");
            check(core.getChoice("menu", "accent").equals("Rose"), "Studio defaults to the Rose accent");
            equal(0.6, core.getNumber("hud", "scale"), "HUD defaults to 60 percent");
            core.setValue("menu", "accent", "Gold");
            check(core.getChoice("menu", "accent").equals("Gold"), "Interface theme choices");
            check(core.isEnabled("sky") && core.getChoice("sky", "preset").equals("Aurora"), "Visible sky defaults");
            core.setValue("sky", "intensity", 8.0);
            equal(1, core.getNumber("sky", "intensity"), "Sky intensity clamps");
            core.setValue("sky", "speed", 0.0);
            equal(0, core.getNumber("sky", "speed"), "Sky animation can be frozen");
            core.setValue("entity-esp", "style", "Screen");
            check(core.getChoice("entity-esp", "style").equals("Screen"), "Projected ESP style");
            core.setValue("shader", "preset", "Bloom");
            check(core.getChoice("shader", "preset").equals("Bloom"), "Neutral bloom preset");
            check(core.isEnabled("coordinates"), "Coordinate defaults");
            check(core.keybind("entity-esp") == -1, "Unbound key default");
            check(!core.getBoolean("entity-esp", "labels") && !core.getBoolean("entity-esp", "health")
                    && !core.getBoolean("entity-esp", "radar") && !core.getBoolean("storage-esp", "labels"),
                    "Fresh debug ESP has no forced labels, health bars or radar");
            check(core.getBoolean("tracers", "match-esp-colors"), "Tracers use the entity palette by default");
            core.setValue("entity-esp", "range", 999);
            equal(256, core.getNumber("entity-esp", "range"), "Upper clamping");
            core.setValue("entity-esp", "range", -100);
            equal(16, core.getNumber("entity-esp", "range"), "Lower clamping");
            core.setValue("entity-esp", "line-width", 1.62);
            equal(1.5, core.getNumber("entity-esp", "line-width"), "Step snapping");
            rejects(() -> core.setValue("entity-esp", "range", Double.NaN), "Nonfinite values");
            rejects(() -> core.setValue("entity-esp", "players", "true"), "Typed booleans");
            rejects(() -> core.setValue("shader", "preset", "Unknown"), "Choice validation");
            rejects(() -> core.setKeybind("entity-esp", 900), "Key validation");
            core.setEnabled("entity-esp", true);
            core.setKeybind("entity-esp", 71);
            core.setValue("entity-esp", "range", 96);
            core.setValue("shader", "preset", "Ember");
            core.setValue("entity-esp", "players-color", "Custom");
            core.setValue("entity-esp", "players-red", 17);
            core.setValue("entity-esp", "players-green", 34);
            core.setValue("entity-esp", "players-blue", 51);
            core.setValue("entity-esp", "labels", true);
            core.setValue("storage-esp", "barrels-color", "Blue");
            core.setValue("storage-esp", "shulkers", false);
            core.save();
            AnalyzerCore restored = new AnalyzerCore(temporary);
            restored.load();
            check(restored.isEnabled("entity-esp"), "Enabled round trip");
            check(restored.keybind("entity-esp") == 71, "Keybind round trip");
            equal(96, restored.getNumber("entity-esp", "range"), "Numeric round trip");
            check(restored.getChoice("entity-esp", "players-color").equals("Custom")
                    && restored.getNumber("entity-esp", "players-red") == 17
                    && restored.getNumber("entity-esp", "players-green") == 34
                    && restored.getNumber("entity-esp", "players-blue") == 51, "Independent entity RGB round trip");
            check(restored.getBoolean("entity-esp", "labels") && restored.getChoice("entity-esp", "style").equals("Screen"),
                    "Explicit existing label and box preferences survive updated defaults");
            check(restored.getChoice("storage-esp", "barrels-color").equals("Blue")
                    && !restored.getBoolean("storage-esp", "shulkers"), "Storage group colors and filters round trip");
            check(restored.getChoice("shader", "preset").equals("Ember"), "Choice round trip");
            restored.saveProfile("Night Travel");
            check(restored.profiles().equals(java.util.List.of("night-travel")), "Normalized profile list");
            restored.setValue("entity-esp", "range", 200);
            restored.loadProfile("Night Travel");
            equal(96, restored.getNumber("entity-esp", "range"), "Named profile loading");
            rejects(() -> restored.saveProfile("../outside"), "Path traversal prevention");
            rejects(() -> restored.saveProfile("CON"), "Reserved Windows filenames");
            restored.deleteProfile("Night Travel");
            check(restored.profiles().isEmpty(), "Profile deletion");
            Files.writeString(temporary.resolve("config.json"), "{broken");
            ioFailure(restored::load, "Malformed config must fail");
            equal(96, restored.getNumber("entity-esp", "range"), "Malformed load preserves current values");
            restored.save();
            check(recoveryFiles(temporary).size() == 1, "Malformed document preserved before automatic save");
            check(Files.readString(recoveryFiles(temporary).getFirst()).equals("{broken"), "Recovery preserves original bytes");
            Files.writeString(temporary.resolve("config.json"), """
                    {"schemaVersion":1,"modules":{"entity-esp":{"enabled":true,"keybind":71.5,
                    "settings":{"range":1000,"players":"yes","style":"Unknown"}}}}
                    """);
            restored.load();
            equal(256, restored.getNumber("entity-esp", "range"), "Disk values clamp");
            check(restored.getBoolean("entity-esp", "players"), "Invalid setting retains default");
            check(restored.keybind("entity-esp") == -1, "Fractional key rejected");
            check(restored.loadWarnings().size() == 3, "Invalid values diagnosed");
            Files.writeString(temporary.resolve("config.json"), "{\"schemaVersion\":99,\"modules\":{}}");
            ioFailure(restored::load, "Future schema must fail");
            equal(256, restored.getNumber("entity-esp", "range"), "Future schema preserves values");
            restored.save();
            check(recoveryFiles(temporary).size() == 2, "Future schema preserved before automatic save");
            check(recoveryFiles(temporary).stream().anyMatch(path -> {
                try { return Files.readString(path).contains("\"schemaVersion\":99"); }
                catch (IOException exception) { throw new java.io.UncheckedIOException(exception); }
            }), "Recovery retains unsupported schema");
            Files.writeString(temporary.resolve("config.json"), "{\"schemaVersion\":1}");
            ioFailure(restored::load, "Structurally invalid document must fail");
            equal(256, restored.getNumber("entity-esp", "range"), "Rejected document preserves values");
            restored.save();
            check(recoveryFiles(temporary).size() == 3, "Rejected document preserved before automatic save");
            check(recoveryFiles(temporary).stream().anyMatch(path -> {
                try { return Files.readString(path).equals("{\"schemaVersion\":1}"); }
                catch (IOException exception) { throw new java.io.UncheckedIOException(exception); }
            }), "Recovery retains structurally invalid document");
            try (var files = Files.list(temporary)) {
                check(files.noneMatch(path -> path.toString().endsWith(".tmp")), "Atomic write cleans temporary files");
            }
            String[] names = {"cinematic", "classic", "neon", "studio", "hvh"};
            String[] layouts = {"Studio", "Studio", "Studio", "Studio", "Studio"};
            Files.createDirectories(temporary.resolve("profiles"));
            for (int i = 0; i < names.length; i++) {
                try (var resource = CoreSelfTest.class.getResourceAsStream("/assets/analyzer/profiles/" + names[i] + ".json")) {
                    if (resource == null) throw new AssertionError("Missing starter profile " + names[i]);
                    Files.copy(resource, temporary.resolve("profiles/" + names[i] + ".json"));
                }
                restored.loadProfile(names[i]);
                check(restored.loadWarnings().isEmpty(), "Starter profile values load without warnings: " + names[i]);
                equal(0.6, restored.getNumber("hud", "scale"), "Starter HUD at sixty percent: " + names[i]);
                check(restored.getChoice("menu", "layout").equals(layouts[i]), "Starter profile layout: " + names[i]);
                if (!names[i].equals("hvh"))
                    check(restored.modules().stream().filter(module -> module.category() == Category.COMBAT
                        || module.category() == Category.HVH || module.category() == Category.MOVEMENT
                        || module.category() == Category.PLAYER || (module.category() == Category.WORLD && !module.id().equals("sky")))
                        .noneMatch(module -> restored.isEnabled(module.id())), "Visual starter profile keeps gameplay opt-in: " + names[i]);
            }
            check(restored.isEnabled("kill-aura") && restored.isEnabled("crystal-aura") && restored.isEnabled("surround")
                && restored.isEnabled("auto-totem"), "Explicit HVH profile enables its documented modules");
            menuPreferenceAliases(temporary.resolve("menu-aliases"));
            homePreferences(temporary.resolve("home-preferences"));
            retiredLayoutMigration(temporary.resolve("retired-layouts"));
            System.out.println("Analyzer core: " + assertions + " assertions passed.");
        } finally {
            try (var files = Files.walk(temporary)) {
                for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(file);
            }
        }
    }

    private static void homePreferences(Path directory) throws Exception {
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("config.json"), """
                {"schemaVersion":1,"modules":{"menu":{"settings":{"layout":"Classic","accent":"Mint"}},
                  "hud":{"settings":{"theme":"Neon","scale":1.15}}}}
                """);
        AnalyzerCore core = new AnalyzerCore(directory);core.load();
        check(core.getBoolean("menu", "home-enabled"), "Old configs receive the enabled custom home default");
        check(core.getChoice("hud", "home-background").equals("Panorama"), "Old configs receive the user-selected Panorama home default");
        equal(.65, core.getNumber("hud", "home-motion"), "Home motion default is independent of menu motion");
        check(core.getBoolean("hud", "home-blur"), "Home submenu blur defaults enabled");
        core.setValue("hud", "home-enabled", false);
        core.setValue("hud", "home-background", "Orbit");
        core.setValue("hud", "home-motion", 0D);
        core.setValue("hud", "home-blur", false);
        check(!core.getBoolean("menu", "home-enabled") && core.getChoice("menu", "home-background").equals("Orbit"), "HUD home aliases change canonical title preferences");
        equal(0, core.getNumber("menu", "home-motion"), "HUD reduced motion alias updates the canonical value");
        check(!core.getBoolean("menu", "home-blur"), "HUD blur alias updates its canonical value");
        core.save();
        var document = com.google.gson.JsonParser.parseString(Files.readString(directory.resolve("config.json"))).getAsJsonObject().getAsJsonObject("modules");
        for (String id : java.util.List.of("home-enabled", "home-background", "home-motion", "home-blur")) {
            check(document.getAsJsonObject("menu").getAsJsonObject("settings").has(id), "Saved canonical home field: " + id);
            check(!document.getAsJsonObject("hud").getAsJsonObject("settings").has(id), "Saved HUD has no duplicate home field: " + id);
        }
        AnalyzerCore loaded = new AnalyzerCore(directory);loaded.load();
        check(!loaded.getBoolean("hud", "home-enabled") && loaded.getChoice("hud", "home-background").equals("Orbit"), "Vanilla selection and last custom background reload through aliases");
        equal(0, loaded.getNumber("hud", "home-motion"), "Static home preference reloads");
        check(!loaded.getBoolean("hud", "home-blur"), "Home blur preference reloads");
        check(loaded.getChoice("menu", "layout").equals("Studio") && loaded.getChoice("menu", "accent").equals("Mint"), "Adding Home migrates the retired layout and preserves its accent");
        check(loaded.getChoice("hud", "theme").equals("Neon"), "Adding Home preserves the HUD theme");
        equal(1.15, loaded.getNumber("hud", "scale"), "Adding Home preserves saved HUD scale");
        rejects(() -> loaded.setValue("hud", "home-background", "Unknown"), "Home background validates its choices");
        loaded.reset("menu");
        check(loaded.getBoolean("hud", "home-enabled") && loaded.getChoice("hud", "home-background").equals("Panorama"), "Interface reset updates Home aliases coherently");
        check(loaded.getChoice("hud", "theme").equals("Neon"), "Interface reset preserves independent HUD styling");
    }

    private static void menuPreferenceAliases(Path directory) throws Exception {
        Files.createDirectories(directory);
        AnalyzerCore core = new AnalyzerCore(directory);
        String[] aliases = {"menu-layout", "menu-material", "menu-theme", "menu-accent"};
        String[] canonical = {"layout", "material", "theme", "accent"};
        for (int i = 0; i < aliases.length; i++) {
            check(core.getChoice("hud", aliases[i]).equals(core.getChoice("menu", canonical[i])), "HUD menu alias default: " + aliases[i]);
            Setting<?> alias = core.definition("hud").setting(aliases[i]);
            Setting<?> target = core.definition("menu").setting(canonical[i]);
            check(alias.choices().equals(target.choices()), "HUD menu alias exposes the canonical choices: " + aliases[i]);
            check(alias.defaultValue().equals(target.defaultValue()), "HUD menu alias resets to the canonical default: " + aliases[i]);
        }
        core.setValue("hud", "menu-layout", "Studio");
        core.setValue("hud", "menu-accent", "Rose");
        core.save();
        AnalyzerCore studio = new AnalyzerCore(directory);
        studio.load();
        check(studio.getChoice("menu", "layout").equals("Studio") && studio.getChoice("hud", "menu-accent").equals("Rose"), "Studio and Rose preferences survive a saved HUD alias edit");
        core.setValue("hud", "menu-layout", "Click GUI");
        check(core.getChoice("menu", "layout").equals("Click GUI"), "HUD menu layout writes canonical Interface value");
        check(core.values("hud").get("menu-layout").equals("Click GUI"), "HUD value snapshot resolves menu aliases");
        @SuppressWarnings("unchecked") Setting<String> layout = (Setting<String>) core.definition("hud").setting("menu-layout");
        check(core.getValue("hud", layout).equals("Click GUI"), "Typed HUD menu setting resolves aliases");
        core.setValue("menu", "layout", "Studio");
        check(core.getChoice("hud", "menu-layout").equals("Studio"), "Interface layout edits are reflected in HUD Studio");
        core.setValue("hud", "theme", "Neon");
        check(core.getChoice("menu", "theme").equals("Glass"), "HUD theme does not change menu theme");
        core.setValue("hud", "menu-theme", "Classic");
        check(core.getChoice("menu", "theme").equals("Classic") && core.getChoice("hud", "theme").equals("Neon"), "Menu theme remains independent of HUD theme");
        core.setValue("hud", "menu-material", "Solid");
        core.setValue("hud", "menu-accent", "Gold");
        rejects(() -> core.setValue("hud", "menu-layout", "Unknown"), "HUD layout alias validates choices");
        check(core.getChoice("menu", "layout").equals("Studio"), "Rejected alias edit preserves canonical value");
        core.save();
        var saved = com.google.gson.JsonParser.parseString(Files.readString(directory.resolve("config.json"))).getAsJsonObject().getAsJsonObject("modules");
        for (String alias : aliases)
            check(!saved.getAsJsonObject("hud").getAsJsonObject("settings").has(alias), "Saved config has no duplicated HUD menu authority: " + alias);
        check(saved.getAsJsonObject("menu").getAsJsonObject("settings").get("theme").getAsString().equals("Classic"), "Menu theme saved under Interface");
        AnalyzerCore restored = new AnalyzerCore(directory);
        restored.load();
        for (int i = 0; i < aliases.length; i++)
            check(restored.getChoice("hud", aliases[i]).equals(core.getChoice("menu", canonical[i])), "HUD alias round trip: " + aliases[i]);
        check(restored.getChoice("hud", "theme").equals("Neon"), "Independent HUD theme round trip");
        core.saveProfile("Appearance");
        core.resetAll();
        core.loadProfile("Appearance");
        check(core.getChoice("hud", "menu-layout").equals("Studio") && core.getChoice("menu", "theme").equals("Classic"), "Appearance profile restores canonical menu values through HUD aliases");
        core.setValue("menu", "motion", 0.35);
        core.reset("hud");
        check(core.getChoice("menu", "layout").equals("Studio") && core.getChoice("menu", "material").equals("Glass")
                && core.getChoice("menu", "theme").equals("Glass") && core.getChoice("menu", "accent").equals("Rose"), "Reset HUD Studio resets its visible menu controls");
        equal(0.35, core.getNumber("menu", "motion"), "HUD reset preserves Interface settings outside HUD Studio");
        core.setValue("hud", "theme", "Neon");
        core.setValue("hud", "menu-layout", "Click GUI");
        core.reset("menu");
        check(core.getChoice("hud", "menu-layout").equals("Studio") && core.getChoice("hud", "theme").equals("Neon"), "Interface reset updates menu aliases while preserving independent HUD theme");

        Files.writeString(directory.resolve("config.json"), """
                {"schemaVersion":1,"modules":{
                  "menu":{"settings":{"layout":"Workspace","accent":"Mint"}}}}
                """);
        restored.load();
        check(restored.getChoice("hud", "menu-layout").equals("Studio") && restored.getChoice("hud", "menu-accent").equals("Mint"), "Retired Workspace migrates to Studio while retaining Mint");
        check(restored.loadWarnings().isEmpty(), "Retired Workspace migrates without warnings");

        Files.writeString(directory.resolve("config.json"), """
                {"schemaVersion":1,"modules":{
                  "menu":{"settings":{"layout":"Classic","material":"Solid","accent":"Gold","motion":0.4}},
                  "hud":{"settings":{"theme":"Minimal","scale":1.15,"coordinates-x":7.4}}}}
                """);
        restored.load();
        check(restored.getChoice("hud", "menu-layout").equals("Studio") && restored.getChoice("hud", "menu-material").equals("Solid")
                && restored.getChoice("hud", "menu-accent").equals("Gold"), "Legacy Interface appearance survives new HUD controls");
        check(restored.getChoice("menu", "theme").equals("Glass") && restored.getChoice("hud", "theme").equals("Minimal"), "Legacy configs keep HUD theme and default added menu theme");
        equal(1.15, restored.getNumber("hud", "scale"), "Legacy HUD scale survives menu additions");
        equal(7.4, restored.getNumber("hud", "coordinates-x"), "Legacy HUD widget position survives menu additions");
        check(restored.loadWarnings().isEmpty(), "Legacy menu config loads without warnings");
        Files.writeString(directory.resolve("config.json"), """
                {"schemaVersion":1,"modules":{"hud":{"settings":{
                  "menu-layout":"Workspace","menu-material":"Solid","menu-theme":"Neon","menu-accent":"Violet","theme":"Classic"}}}}
                """);
        restored.load();
        check(restored.getChoice("menu", "layout").equals("Studio") && restored.getChoice("menu", "material").equals("Solid")
                && restored.getChoice("menu", "theme").equals("Neon") && restored.getChoice("menu", "accent").equals("Violet"), "Alias-only imports recover menu preferences");
        check(restored.getChoice("hud", "theme").equals("Classic") && restored.loadWarnings().isEmpty(), "Alias imports preserve independent HUD settings");
        restored.save();
        restored.resetAll();
        restored.load();
        check(restored.getChoice("menu", "layout").equals("Studio") && restored.getChoice("hud", "menu-theme").equals("Neon"), "Alias-only import saves back as canonical values");
        Files.writeString(directory.resolve("config.json"), """
                {"schemaVersion":1,"modules":{
                  "menu":{"settings":{"layout":"Workspace","material":"Solid","theme":"Minimal","accent":"Violet"}},
                  "hud":{"settings":{"menu-layout":"Classic","menu-material":"Glass","menu-theme":"Neon","menu-accent":"Gold"}}}}
                """);
        restored.load();
        check(restored.getChoice("menu", "layout").equals("Studio") && restored.getChoice("menu", "material").equals("Solid")
                && restored.getChoice("menu", "theme").equals("Minimal") && restored.getChoice("menu", "accent").equals("Violet"), "Valid canonical appearance wins conflicting imported HUD aliases");
        check(restored.loadWarnings().isEmpty(), "Valid alias conflicts do not create warnings");
        Files.writeString(directory.resolve("config.json"), """
                {"schemaVersion":1,"modules":{
                  "menu":{"settings":{"layout":"Unknown","material":7,"theme":"Neon"}},
                  "hud":{"settings":{"menu-layout":"Classic","menu-material":"Glass","menu-theme":null,"menu-accent":"Unknown"}}}}
                """);
        restored.load();
        check(restored.getChoice("menu", "layout").equals("Studio") && restored.getChoice("menu", "material").equals("Glass"), "Valid aliases recover malformed canonical settings");
        check(restored.getChoice("menu", "theme").equals("Neon") && restored.getChoice("menu", "accent").equals("Rose"), "Invalid aliases cannot overwrite canonical values or defaults");
        check(restored.loadWarnings().size() == 4, "Invalid canonical and alias fields are each diagnosed");
    }

    private static void retiredLayoutMigration(Path directory) throws Exception {
        Files.createDirectories(directory.resolve("profiles"));
        AnalyzerCore core = new AnalyzerCore(directory);
        check(core.definition("menu").setting("layout").choices().equals(java.util.List.of("Studio", "Click GUI", "Compact")),
                "Only shader layouts are offered by Interface: Studio, the floating Click GUI and the Compact board");
        check(core.definition("hud").setting("menu-layout").choices().equals(java.util.List.of("Studio", "Click GUI", "Compact")),
                "HUD Studio cannot offer a retired layout");
        Files.writeString(directory.resolve("config.json"), """
                {"schemaVersion":1,"modules":{"menu":{"settings":{"layout":"Compact","accent":"Gold"}}}}
                """);
        core.load();
        check(core.getChoice("menu", "layout").equals("Compact") && core.getChoice("hud", "menu-layout").equals("Compact")
                && core.loadWarnings().isEmpty(), "Compact is a current layout again and loads as the framed board");
        for (String retired : java.util.List.of("Workspace", "Classic")) {
            for (String authority : java.util.List.of("menu", "hud")) {
                var document = com.google.gson.JsonParser.parseString("""
                        {"schemaVersion":1,"modules":{
                          "menu":{"enabled":false,"keybind":71,"settings":{"material":"Solid","theme":"Neon","accent":"Gold","home-background":"Orbit","motion":0.35}},
                          "hud":{"settings":{"theme":"Classic","scale":1.15,"coordinates-x":7.4}},
                          "watermark":{"settings":{"logo":"A logo","x":8.2}},
                          "kill-aura":{"enabled":true,"keybind":72,"settings":{"range":4.25,"pause-using":false}}}}
                        """).getAsJsonObject();
                var settings = document.getAsJsonObject("modules").getAsJsonObject(authority).getAsJsonObject("settings");
                settings.addProperty(authority.equals("menu") ? "layout" : "menu-layout", retired);
                String original = document.toString();
                Files.writeString(directory.resolve("config.json"), original);
                core.load();
                check(core.getChoice("menu", "layout").equals("Studio") && core.getChoice("hud", "menu-layout").equals("Studio"),
                        authority + " saved " + retired + " migrates to Studio");
                check(core.loadWarnings().isEmpty(), "Supported migration does not diagnose legacy values as invalid");
                check(Files.readString(directory.resolve("config.json")).equals(original), "Migration reads without overwriting the source config");
                check(!core.isEnabled("menu") && core.keybind("menu") == 71 && core.isEnabled("kill-aura") && core.keybind("kill-aura") == 72,
                        "Migration retains independent module flags and bindings");
                check(core.getChoice("menu", "material").equals("Solid") && core.getChoice("menu", "theme").equals("Neon")
                                && core.getChoice("menu", "accent").equals("Gold") && core.getChoice("menu", "home-background").equals("Orbit"),
                        "Migration retains menu material, theme, accent and Home preference");
                equal(.35, core.getNumber("menu", "motion"), "Migration preserves menu motion");
                equal(1.15, core.getNumber("hud", "scale"), "Migration preserves HUD scale");
                equal(7.4, core.getNumber("hud", "coordinates-x"), "Migration preserves HUD position");
                equal(4.25, core.getNumber("kill-aura", "range"), "Migration preserves gameplay setting");
                check(!core.getBoolean("kill-aura", "pause-using") && core.getChoice("hud", "theme").equals("Classic")
                                && core.getChoice("watermark", "logo").equals("A logo"), "Migration preserves toggles, independent theme and one-logo watermark");
                core.save();
                var saved = com.google.gson.JsonParser.parseString(Files.readString(directory.resolve("config.json"))).getAsJsonObject();
                check(saved.get("schemaVersion").getAsInt() == ConfigStore.SCHEMA_VERSION, "Layout migration retains the existing schema version");
                var savedModules = saved.getAsJsonObject("modules");
                check(savedModules.getAsJsonObject("menu").getAsJsonObject("settings").get("layout").getAsString().equals("Studio")
                                && !savedModules.getAsJsonObject("hud").getAsJsonObject("settings").has("menu-layout"),
                        "A later save uses canonical Studio without duplicate alias authority");
                core.load();
                check(core.getChoice("menu", "layout").equals("Studio"), "Migrated value survives a config round trip");
            }
            rejects(() -> core.setValue("menu", "layout", retired), "Live canonical edits cannot resurrect " + retired);
            rejects(() -> core.setValue("hud", "menu-layout", retired), "Live alias edits cannot resurrect " + retired);
        }
        Files.writeString(directory.resolve("config.json"), """
                {"schemaVersion":1,"modules":{"menu":{"settings":{"layout":"Classic"}},
                  "hud":{"settings":{"menu-layout":"Click GUI"}}}}
                """);
        core.load();
        check(core.getChoice("menu", "layout").equals("Studio") && core.loadWarnings().isEmpty(),
                "Valid legacy canonical layout wins a conflicting current alias after migration");
        Files.writeString(directory.resolve("config.json"), """
                {"schemaVersion":1,"modules":{"menu":{"settings":{"layout":"Click GUI"}},
                  "hud":{"settings":{"menu-layout":"Workspace"}}}}
                """);
        core.load();
        check(core.getChoice("menu", "layout").equals("Click GUI") && core.loadWarnings().isEmpty(),
                "Current canonical Click GUI wins a migrated legacy alias");
        for (String malformed : java.util.List.of("7", "null", "{}", "\"Unknown\"")) {
            Files.writeString(directory.resolve("config.json"), "{\"schemaVersion\":1,\"modules\":{\"menu\":{\"settings\":{\"layout\":"
                    + malformed + "}},\"hud\":{\"settings\":{\"menu-layout\":\"Workspace\"}}}}");
            core.load();
            check(core.getChoice("menu", "layout").equals("Studio") && core.loadWarnings().size() == 1,
                    "A migrated valid alias recovers malformed canonical layout " + malformed);
        }
        try (var resource = CoreSelfTest.class.getResourceAsStream("/assets/analyzer/profiles/hvh.json")) {
            if (resource == null) throw new AssertionError("Missing HVH starter profile");
            var profile = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(resource, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            profile.getAsJsonObject("modules").getAsJsonObject("menu").getAsJsonObject("settings").addProperty("layout", "Classic");
            Path profilePath = directory.resolve("profiles/legacy-hvh.json");
            String original = profile.toString(); Files.writeString(profilePath, original);
            core.loadProfile("legacy-hvh");
            check(core.getChoice("menu", "layout").equals("Studio") && core.loadWarnings().isEmpty(),
                    "An actual premade HVH snapshot with the retired layout loads as Studio");
            for (ModuleDefinition module : core.modules()) {
                var entry = profile.getAsJsonObject("modules").getAsJsonObject(module.id());
                if (entry == null) {
                    check(core.isEnabled(module.id()) == module.defaultEnabled(), "A module added after the legacy profile keeps its safe default: " + module.id());
                    for (Setting<?> setting : module.settings())
                        check(core.getValue(module.id(), setting.id()).equals(setting.defaultValue()), "Missing legacy-profile setting keeps its default: " + module.id() + "/" + setting.id());
                    continue;
                }
                check(core.isEnabled(module.id()) == entry.get("enabled").getAsBoolean()
                                && core.keybind(module.id()) == entry.get("keybind").getAsInt(),
                        "Legacy HVH profile preserves real flags and bindings: " + module.id());
                var settings = entry.getAsJsonObject("settings");
                for (Setting<?> setting : module.settings()) {
                    if (!settings.has(setting.id()) || module.id().equals("menu") && setting.id().equals("layout")) continue;
                    var value = settings.get(setting.id());
                    Object expected = switch (setting.kind()) {
                        case BOOLEAN -> value.getAsBoolean(); case NUMBER -> value.getAsDouble(); case CHOICE, TEXT -> value.getAsString();
                    };
                    check(core.getValue(module.id(), setting.id()).equals(expected), "Legacy HVH profile preserves setting " + module.id() + "/" + setting.id());
                }
            }
            core.save(); core.load();
            check(core.getChoice("hud", "menu-layout").equals("Studio") && core.isEnabled("crystal-aura"),
                    "Legacy HVH profile survives active config save and reload in Studio");
            check(Files.readString(profilePath).equals(original), "Config save never rewrites the user's legacy profile file");
            core.saveProfile("migrated-hvh"); core.resetAll(); core.loadProfile("migrated-hvh");
            check(core.getChoice("menu", "layout").equals("Studio") && core.isEnabled("kill-aura") && core.isEnabled("surround"),
                    "A newly saved migrated profile retains Studio and HVH module flags");
        }
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        assertions++;
    }

    private static void equal(double expected, double actual, String label) {
        check(Math.abs(expected - actual) < 0.000001, label + ": expected " + expected + ", got " + actual);
    }

    private static void rejects(ThrowingAction action, String label) throws Exception {
        try { action.run(); } catch (IllegalArgumentException expected) { assertions++; return; }
        throw new AssertionError(label);
    }

    private static void ioFailure(ThrowingAction action, String label) throws Exception {
        try { action.run(); } catch (IOException expected) { assertions++; return; }
        throw new AssertionError(label);
    }

    private static java.util.List<Path> recoveryFiles(Path directory) throws IOException {
        try (var files = Files.list(directory)) {
            return files.filter(path -> path.getFileName().toString().startsWith("config-recovery-")).toList();
        }
    }

    @FunctionalInterface private interface ThrowingAction { void run() throws Exception; }
}
