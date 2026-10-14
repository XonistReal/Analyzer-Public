package dev.analyzer.automation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/** Real temporary folders exercise the picker independently of Minecraft or schematic parsing. */
public final class SchematicLibrarySelfTest {
    private static int assertions;
    public static void main(String[] args) throws Exception {
        Path temporary = Files.createTempDirectory("analyzer-schematic-library-");
        try {
            Path root = Files.createDirectory(temporary.resolve("schematics"));
            Path nested = Files.createDirectory(root.resolve("village"));
            Path castle = Files.write(root.resolve("Castle.LITEMATIC"), new byte[]{1, 2, 3});
            Path house = Files.write(root.resolve("house.schem"), new byte[]{4});
            Files.write(root.resolve("tower.schematic"), new byte[]{5});
            Files.writeString(root.resolve("instructions.md"), "Not a schematic");
            Files.writeString(root.resolve("house.schem.exe"), "Not a schematic");
            Path selected = Files.write(nested.resolve("small house.litematic"), new byte[]{6, 7});
            Path outside = Files.write(temporary.resolve("outside.schem"), new byte[]{8});
            check(SchematicLibrary.defaultRoot(temporary).equals(root), "Uses Minecraft's ordinary schematics folder");
            var listing = SchematicLibrary.scan(root, root);
            check(listing.entries().stream().map(SchematicLibrary.Entry::name).toList().equals(List.of("village", "Castle.LITEMATIC", "house.schem", "tower.schematic")), "Directories first and supported files sorted case-insensitively");
            check(!listing.canGoUp() && !listing.limited() && listing.unreadable() == 0, "Root has no upward escape and no silent limit");
            check(listing.entries().get(1).bytes() == 3 && listing.entries().get(1).modifiedMillis() > 0, "File metadata comes from disk");
            check(listing.entries().get(1).format().equals("Litematica") && listing.entries().get(3).format().equals("Schematica / MCEdit"), "Format labels match their extensions");
            check(SchematicLibrary.filter(listing.entries(), " HOUSE ").getFirst().path().equals(house), "Search is trimmed and case-insensitive");
            check(SchematicLibrary.filter(listing.entries(), "not present").isEmpty(), "Empty search result is explicit");
            check(SchematicLibrary.filter(listing.entries(), null).size() == 4, "Empty search restores all entries");
            var sub = SchematicLibrary.scan(root, nested);
            check(sub.canGoUp() && sub.entries().size() == 1 && sub.folder().equals(nested.toRealPath()), "Subfolders can be browsed without flattening paths");
            check(SchematicLibrary.selectedFile(root, selected).equals(selected.toRealPath()), "Selection returns the actual absolute file path");
            rejects(() -> SchematicLibrary.scan(root, temporary), "Parent traversal is rejected");
            rejects(() -> SchematicLibrary.scan(root, nested.resolve("../../")), "Normalized traversal is rejected");
            rejects(() -> SchematicLibrary.selectedFile(root, outside), "File outside library is rejected");
            rejects(() -> SchematicLibrary.selectedFile(root, nested), "Directories cannot be selected as files");
            rejects(() -> SchematicLibrary.selectedFile(root, root.resolve("instructions.md")), "Unrelated files cannot be selected");
            Files.delete(castle);
            rejects(() -> SchematicLibrary.selectedFile(root, castle), "Removed files cannot be selected from stale rows");
            check(SchematicLibrary.scan(root, root).entries().size() == 3, "Refresh sees files removed from disk");
            Files.write(root.resolve("new.schem"), new byte[]{9});
            check(SchematicLibrary.scan(root, root).entries().size() == 4, "Refresh sees files newly added to disk");
            try { listing.entries().clear(); throw new AssertionError("Mutable listing"); } catch (UnsupportedOperationException expected) { assertions++; }
            Path empty = Files.createDirectory(root.resolve("empty"));
            check(SchematicLibrary.scan(root, empty).entries().isEmpty(), "Empty subfolders remain usable");
            Files.delete(empty);
            rejects(() -> SchematicLibrary.scan(root, empty), "Removed directories fail explicitly");
            rejects(() -> SchematicLibrary.selectedFile(root, null), "No selection cannot resolve to the current working directory");
            check(!SchematicLibrary.supported(Path.of("fake.litematic.zip")), "An extension inside another filename is not accepted");
            Path crowded = Files.createDirectory(root.resolve("crowded"));
            for (int i = 0; i <= SchematicLibrary.MAX_ENTRIES; i++) Files.createFile(crowded.resolve("entry-" + i + ".schem"));
            var bounded = SchematicLibrary.scan(root, crowded);
            check(bounded.limited() && bounded.entries().size() == SchematicLibrary.MAX_ENTRIES, "Oversized folders have a visible bounded result rather than unbounded indexing");
            // Windows may deny symlink creation to an ordinary user; run these cases when supported.
            Path link = root.resolve("linked.schem");
            try {
                Files.createSymbolicLink(link, outside);
                check(SchematicLibrary.scan(root, root).entries().stream().noneMatch(entry -> entry.path().equals(link)), "Child file links are omitted");
                rejects(() -> SchematicLibrary.selectedFile(root, link), "Child file links cannot bypass selection");
                Path folderLink = root.resolve("linked-folder"); Files.createSymbolicLink(folderLink, temporary);
                check(SchematicLibrary.scan(root, root).entries().stream().noneMatch(entry -> entry.path().equals(folderLink)), "Child folder links are omitted");
                rejects(() -> SchematicLibrary.scan(root, folderLink), "Resolved linked folder cannot escape library");
            } catch (UnsupportedOperationException | SecurityException | java.nio.file.FileSystemException unavailable) {
                System.out.println("SchematicLibrarySelfTest: symlink creation unavailable on this platform");
            }
            System.out.println("SchematicLibrarySelfTest: " + assertions + " assertions passed");
        } finally {
            try (var files = Files.walk(temporary)) { for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(file); }
        }
    }
    private interface Operation { void run() throws IOException; }
    private static void rejects(Operation operation, String label) throws IOException {
        try { operation.run(); throw new AssertionError(label); } catch (IOException expected) { assertions++; }
    }
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); assertions++; }
}
