package dev.analyzer.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;

/** Small, bounded JSON documents saved with same-directory atomic replacement. */
final class ConfigStore {
    static final int SCHEMA_VERSION = 1;
    private static final long MAX_CONFIG_BYTES = 2 * 1024 * 1024;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Path directory;
    private final Path profileDirectory;
    private boolean recoverActiveBeforeWrite;

    ConfigStore(Path directory) {
        this.directory = directory.toAbsolutePath().normalize();
        profileDirectory = this.directory.resolve("profiles");
    }

    JsonObject readActive() throws IOException {
        Path file = directory.resolve("config.json");
        try {
            JsonObject document = Files.exists(file) ? read(file) : null;
            recoverActiveBeforeWrite = false;
            return document;
        } catch (IOException ex) {
            // Startup falls back to defaults, but automatic saves must preserve the
            // unreadable or newer document so a downgrade never destroys it.
            recoverActiveBeforeWrite = true;
            throw ex;
        }
    }

    void writeActive(JsonObject document) throws IOException {
        Path file = directory.resolve("config.json");
        if (recoverActiveBeforeWrite && Files.exists(file)) {
            Path recovery = Files.createTempFile(directory, "config-recovery-", ".json");
            try {
                Files.copy(file, recovery, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                Files.deleteIfExists(recovery);
                throw new IOException("Could not preserve the existing Analyzer configuration", ex);
            }
        }
        write(file, document);
        recoverActiveBeforeWrite = false;
    }

    void preserveActiveBeforeWrite() { recoverActiveBeforeWrite = true; }
    JsonObject readProfile(String name) throws IOException { return read(profilePath(name)); }
    void writeProfile(String name, JsonObject document) throws IOException { write(profilePath(name), document); }
    void deleteProfile(String name) throws IOException { Files.deleteIfExists(profilePath(name)); }

    List<String> profiles() throws IOException {
        if (!Files.exists(profileDirectory)) return List.of();
        try (var files = Files.list(profileDirectory)) {
            return files.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".json"))
                    .map(name -> name.substring(0, name.length() - 5))
                    .filter(name -> name.matches("[a-z0-9][a-z0-9_-]{0,47}"))
                    .sorted().toList();
        }
    }

    private Path profilePath(String name) {
        if (name == null || !name.matches("[A-Za-z0-9][A-Za-z0-9 _-]{0,47}")) {
            throw new IllegalArgumentException("Profile names use 1–48 letters, numbers, spaces, underscores or hyphens");
        }
        String normalized = name.toLowerCase(Locale.ROOT).replace(' ', '-');
        // Windows device filenames remain reserved even when a suffix is present.
        if (normalized.matches("con|prn|aux|nul|com[1-9]|lpt[1-9]")) {
            throw new IllegalArgumentException("Reserved profile name: " + name);
        }
        return profileDirectory.resolve(normalized + ".json");
    }

    private static JsonObject read(Path file) throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Analyzer config is not a regular file: " + file.getFileName());
        }
        if (Files.size(file) > MAX_CONFIG_BYTES) throw new IOException("Analyzer config exceeds the 2 MiB size limit");
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) throw new IOException("Analyzer config must be a JSON object");
            JsonObject document = root.getAsJsonObject();
            JsonElement version = document.get("schemaVersion");
            // Missing version identifies the initial, unversioned layout.
            int schema = 0;
            if (version != null) {
                if (!(version instanceof JsonPrimitive primitive) || !primitive.isNumber()) {
                    throw new IOException("Invalid Analyzer config schema version");
                }
                double numericVersion = primitive.getAsDouble();
                if (!Double.isFinite(numericVersion) || numericVersion != Math.rint(numericVersion)
                        || numericVersion < 0 || numericVersion > SCHEMA_VERSION) {
                    throw new IOException("Unsupported Analyzer config schema version: " + numericVersion);
                }
                schema = (int) numericVersion;
            }
            if (schema == 0) document.addProperty("schemaVersion", SCHEMA_VERSION);
            return document;
        } catch (JsonParseException | IllegalStateException ex) {
            throw new IOException("Could not parse Analyzer config " + file.getFileName(), ex);
        }
    }

    private static void write(Path file, JsonObject document) throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), ".analyzer-", ".tmp");
        try {
            Files.writeString(temporary, GSON.toJson(document) + System.lineSeparator(), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
