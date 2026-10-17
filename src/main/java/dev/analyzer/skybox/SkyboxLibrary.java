package dev.analyzer.skybox;

import java.io.IOException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Bounded metadata-only shelf. Selecting a folder explicitly validates its six images. */
public final class SkyboxLibrary {
    public static final int MAX_ENTRIES = 4096, MAX_SCANNED = 32768;
    public record Entry(Path path, String name, boolean directory, long bytes) { }
    public record Listing(Path root, Path folder, List<Entry> entries, boolean limited, int unreadable) {
        public Listing { entries = List.copyOf(entries); }
        public boolean canGoUp() { return !root.equals(folder); }
    }
    private SkyboxLibrary() { }
    public static Path defaultRoot(Path game) { return game.toAbsolutePath().normalize().resolve("skyboxes"); }
    public static boolean supported(Path path) {
        if (path == null || path.getFileName() == null) return false;
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg");
    }
    public static Listing scan(Path root, Path folder) throws IOException {
        Path realRoot = root.toRealPath(), realFolder = folder.toRealPath();
        if (!realFolder.startsWith(realRoot) || !Files.isDirectory(realFolder)) throw new IOException("Choose a folder inside skyboxes.");
        List<Entry> entries = new ArrayList<>(); int scanned = 0, unreadable = 0; boolean limited = false;
        try (var stream = Files.newDirectoryStream(realFolder)) {
            for (Path path : stream) {
                if (++scanned > MAX_SCANNED) { limited = true; break; }
                try {
                    var attrs = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                    if (attrs.isSymbolicLink() || !attrs.isDirectory() && (!attrs.isRegularFile() || !supported(path))) continue;
                    // Windows junctions can be directories without isSymbolicLink(). Check resolved paths too.
                    if (!path.toRealPath().startsWith(realRoot)) continue;
                    if (entries.size() == MAX_ENTRIES) { limited = true; break; }
                    entries.add(new Entry(path.toAbsolutePath().normalize(), path.getFileName().toString(), attrs.isDirectory(), attrs.size()));
                } catch (IOException | SecurityException failure) { unreadable++; }
            }
        } catch (DirectoryIteratorException failure) { throw failure.getCause(); }
        entries.sort(Comparator.comparing(Entry::directory).reversed().thenComparing(Entry::name, String.CASE_INSENSITIVE_ORDER).thenComparing(Entry::name));
        return new Listing(realRoot, realFolder, entries, limited, unreadable);
    }
    public static List<Entry> filter(List<Entry> entries, String query) {
        String text = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        return entries.stream().filter(entry -> entry.name().toLowerCase(Locale.ROOT).contains(text)).toList();
    }
    public static Path selected(Path root, Path selected) throws IOException {
        Path real = selected.toRealPath();
        if (!real.startsWith(root.toRealPath()) || Files.isSymbolicLink(selected)
                || !Files.isDirectory(real) && (!Files.isRegularFile(real) || !supported(real)))
            throw new IOException("Refresh and select a local skybox image or cube folder.");
        return real;
    }
}
