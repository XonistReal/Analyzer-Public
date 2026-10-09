package dev.analyzer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.FileAlreadyExistsException;

/** Seed curated profiles once, retaining edits and deletions on later launches. */
final class PresetInstaller {
    static void install(Path directory) throws IOException {
        Path marker = directory.resolve(".presets-v1");
        if (Files.exists(marker)) return;
        Path profiles = directory.resolve("profiles");
        Files.createDirectories(profiles);
        for (String name : new String[]{"aurora", "ember", "minimal"}) {
            try (var stream = PresetInstaller.class.getResourceAsStream("/assets/analyzer/profiles/" + name + ".json")) {
                if (stream == null) throw new IOException("Missing built-in profile " + name);
                try { Files.copy(stream, profiles.resolve(name + ".json")); }
                catch (FileAlreadyExistsException ignored) { }
            }
        }
        Files.writeString(marker, "Analyzer starter profiles v1\n");
    }
}
