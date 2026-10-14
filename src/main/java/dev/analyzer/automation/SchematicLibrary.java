package dev.analyzer.automation;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** A bounded folder index; schematic contents are parsed only by the explicit plan review. */
public final class SchematicLibrary {
    public static final int MAX_ENTRIES = 4096, MAX_SCANNED = 32768;
    private static final LinkOption[] NO_LINKS = {LinkOption.NOFOLLOW_LINKS};
    public record Entry(Path path, String name, boolean directory, long bytes, long modifiedMillis) {
        public String format() {
            if (directory) return "Folder";
            String lower = name.toLowerCase(Locale.ROOT);
            return lower.endsWith(".litematic") ? "Litematica" : lower.endsWith(".schematic") ? "Schematica / MCEdit" : "Sponge";
        }
    }
    public record Listing(Path root, Path folder, List<Entry> entries, boolean limited, int unreadable) {
        public Listing { entries = List.copyOf(entries); }
        public boolean canGoUp() { return !folder.equals(root); }
    }
    private SchematicLibrary() { }
    public static Path defaultRoot(Path gameDirectory) {
        return gameDirectory.toAbsolutePath().normalize().resolve("schematics");
    }
    public static boolean supported(Path path) {
        if (path == null || path.getFileName() == null) return false;
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".litematic") || name.endsWith(".schematic") || name.endsWith(".schem");
    }
    public static Listing scan(Path root, Path folder) throws IOException {
        Path realRoot = root.toRealPath(), realFolder = folder.toRealPath();
        if (!Files.isDirectory(realRoot) || !realFolder.startsWith(realRoot) || !Files.isDirectory(realFolder))
            throw new IOException("Choose a folder inside the schematics folder.");
        List<Entry> entries = new ArrayList<>(); int scanned = 0, unreadable = 0; boolean limited = false;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(realFolder)) {
            for (Path path : stream) {
                if (++scanned > MAX_SCANNED) { limited = true; break; }
                try {
                    BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class, NO_LINKS);
                    // Never follow child links outside the library or traverse a directory cycle.
                    if (attrs.isSymbolicLink() || !attrs.isDirectory() && (!attrs.isRegularFile() || !supported(path))) continue;
                    if (entries.size() == MAX_ENTRIES) { limited = true; break; }
                    entries.add(new Entry(path.toAbsolutePath().normalize(), path.getFileName().toString(), attrs.isDirectory(), attrs.size(), attrs.lastModifiedTime().toMillis()));
                } catch (IOException | SecurityException failure) { unreadable++; }
            }
        } catch (DirectoryIteratorException failure) { throw failure.getCause(); }
        entries.sort(Comparator.comparing(Entry::directory).reversed()
                .thenComparing(Entry::name, String.CASE_INSENSITIVE_ORDER).thenComparing(Entry::name));
        return new Listing(realRoot, realFolder, entries, limited, unreadable);
    }
    public static List<Entry> filter(List<Entry> entries, String query) {
        String text = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        return entries.stream().filter(entry -> entry.name().toLowerCase(Locale.ROOT).contains(text)).toList();
    }
    public static Path selectedFile(Path root, Path selected) throws IOException {
        Path realRoot = root.toRealPath();
        if (selected == null || !supported(selected) || !Files.isRegularFile(selected, NO_LINKS))
            throw new IOException("Choose an existing schematic file from the list.");
        Path realFile = selected.toRealPath();
        if (!realFile.startsWith(realRoot)) throw new IOException("This schematic is outside the schematics folder. Refresh the list.");
        return realFile;
    }
}
