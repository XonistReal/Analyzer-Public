package dev.analyzer.automation;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Resolves a spoken local schematic name to a real file, never a guessed filename or remote path. */
public final class LocalSchematicLookup {
    public record Resolution(List<Path> matches, boolean limited) {
        public Resolution { matches = List.copyOf(matches); }
        public Optional<Path> unique() { return !limited && matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty(); }
    }
    private record Folder(Path path, int depth) { }
    private LocalSchematicLookup() { }
    public static Resolution find(Path gameDirectory, String requestedName) throws IOException {
        String name = normalize(requestedName);
        if (name.isBlank()) return new Resolution(List.of(), false);
        if (name.startsWith("/") || name.contains(":") || List.of(name.split("/")).contains(".."))
            throw new IOException("Choose a local schematic name or select its real file from Browse.");
        Path root = SchematicLibrary.defaultRoot(gameDirectory);
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) return new Resolution(List.of(), false);
        root = root.toRealPath(); var queue = new ArrayDeque<Folder>(); queue.add(new Folder(root, 0));
        var matches = new ArrayList<Path>(); int directories = 0, scanned = 0; boolean limited = false;
        while (!queue.isEmpty()) {
            if (++directories > 256) { limited = true; break; }
            Folder folder = queue.removeFirst();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder.path())) {
                for (Path entry : stream) {
                    if (++scanned > 16384) { limited = true; break; }
                    if (Files.isSymbolicLink(entry)) continue;
                    if (Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)) {
                        if (folder.depth() < 8) queue.addLast(new Folder(entry, folder.depth() + 1)); else limited = true;
                    } else if (Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS) && SchematicLibrary.supported(entry)) {
                        String filename = normalize(entry.getFileName().toString()), relative = normalize(root.relativize(entry).toString());
                        if (name.equals(filename) || name.equals(withoutExtension(filename)) || name.equals(relative) || name.equals(withoutExtension(relative)))
                            matches.add(SchematicLibrary.selectedFile(root, entry));
                    }
                }
            }
            if (scanned > 16384) break;
        }
        matches.sort(Comparator.comparing(Path::toString, String.CASE_INSENSITIVE_ORDER));
        return new Resolution(matches, limited);
    }
    private static String normalize(String name) { return name == null ? "" : name.strip().toLowerCase(Locale.ROOT).replace('\\', '/'); }
    private static String withoutExtension(String filename) { int dot = filename.lastIndexOf('.'); return dot < 0 ? filename : filename.substring(0, dot); }
}
