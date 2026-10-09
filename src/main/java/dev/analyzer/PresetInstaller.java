package dev.analyzer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.FileAlreadyExistsException;

/** Seed curated profiles once, retaining edits and deletions on later launches. */
final class PresetInstaller {
    static void install(Path directory) throws IOException {
        installOnce(directory, ".presets-v1", new String[]{"aurora", "ember", "minimal"});
        installOnce(directory, ".presets-v2", new String[]{"showcase", "arctic", "cinder"});
        installOnce(directory, ".presets-v3", new String[]{"cinematic", "classic", "neon", "hvh"});
        installOnce(directory, ".presets-v4", new String[]{"studio"});
    }
    private static void installOnce(Path directory, String markerName, String[] names) throws IOException {
        Path marker = directory.resolve(markerName);
        if (Files.exists(marker)) return;
        Path profiles = directory.resolve("profiles");
        Files.createDirectories(profiles);
        for (String name : names) {
            try (var stream = PresetInstaller.class.getResourceAsStream("/assets/analyzer/profiles/" + name + ".json")) {
                if (stream == null) throw new IOException("Missing built-in profile " + name);
                try { Files.copy(stream, profiles.resolve(name + ".json")); }
                catch (FileAlreadyExistsException ignored) { }
            }
        }
        Files.writeString(marker, "Analyzer starter profiles " + markerName + "\n");
    }
}
