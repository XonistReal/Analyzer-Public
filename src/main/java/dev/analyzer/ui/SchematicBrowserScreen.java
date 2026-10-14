package dev.analyzer.ui;

import dev.analyzer.automation.SchematicImporter;
import dev.analyzer.automation.SchematicLibrary;
import dev.analyzer.core.AnalyzerCore;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

/** Studio's local schematic shelf; choosing a file returns to the explicit plan review. */
public final class SchematicBrowserScreen extends CatalogScreen {
    private static final Area LIST = new Area(43, 214, 594, 330);
    private static final DateTimeFormatter MODIFIED = DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm").withZone(ZoneId.systemDefault());
    private final Path root;
    private final Consumer<Path> choose;
    private Path folder;
    private SchematicLibrary.Listing listing;
    private List<SchematicLibrary.Entry> visible = List.of();
    private SchematicLibrary.Entry selected;
    private double scroll;

    public SchematicBrowserScreen(AnalyzerCore core, Screen parent, Path gameDirectory, Consumer<Path> choose) {
        super(core, "Schematics");
        withParent(parent); this.root = SchematicLibrary.defaultRoot(gameDirectory); this.folder = root;
        this.choose = Objects.requireNonNull(choose); refresh();
    }
    @Override protected String layout() { return "Studio"; }
    @Override protected float canvasScale() { return StudioAuxiliary.canvasScale(client, W, H, .84f, .88f); }
    @Override protected void backdrop(DrawContext draw) { StudioAuxiliary.backdrop(draw, core, width, height); }
    @Override protected void header(DrawContext draw) {
        GuiDraw.heading(draw, textRenderer, "Schematics", 43, 31, 1.85f, StudioAuxiliary.TEXT);
        label(draw, "Choose a file from your Minecraft schematics folder", 44, 64, .96f, StudioAuxiliary.MUTED);
        button(draw, "back", "Back to task", 809, 30, 148, 32, false, this::close);
    }
    @Override protected void button(DrawContext draw, String id, String value, int x, int y, int w, int h, boolean primary, Runnable action) {
        enabledButton(draw, id, value, x, y, w, h, true, primary, action);
    }
    private void enabledButton(DrawContext draw, String id, String value, int x, int y, int w, int h, boolean enabled, boolean primary, Runnable action) {
        Area area = new Area(x, y, w, h);
        StudioAuxiliary.button(draw, core, textRenderer, x, y, w, h, value, enabled, primary, enabled && area.contains(mx, my));
        if (enabled) control(id, area, GLFW.GLFW_MOUSE_BUTTON_LEFT, action);
    }
    @Override protected void body(DrawContext draw) {
        GuiDraw.shaderCard(draw, 43, 97, 914, 53, 13, accent, glass(), theme(), 0, false);
        label(draw, "CURRENT FOLDER", 57, 108, .79f, StudioAuxiliary.SUBTLE);
        label(draw, GuiDraw.truncate(textRenderer, folder.toString(), 785, .95f), 57, 127, .95f, StudioAuxiliary.TEXT);
        enabledButton(draw, "folder-root", "Root", 870, 109, 73, 29, listing == null || listing.canGoUp(), false, () -> navigate(root));
        StudioAuxiliary.field(draw, core, 43, 165, 450, 35, searchFocused);
        label(draw, GuiDraw.truncate(textRenderer, query.isEmpty() ? "Search this folder…" : query, 398, .97f), 56, 177, .97f, query.isEmpty() ? StudioAuxiliary.MUTED : StudioAuxiliary.TEXT);
        control("search", new Area(43, 165, 405, 35), 0, () -> {searchFocused = true; selectAll = false;});
        button(draw, "search-clear", "×", 457, 170, 27, 25, false, () -> {query = ""; searchChanged();});
        enabledButton(draw, "folder-up", "Up", 505, 165, 80, 35, listing != null && listing.canGoUp(), false, () -> navigate(folder.getParent()));
        button(draw, "refresh", "Refresh", 597, 165, 110, 35, false, this::refresh);
        button(draw, "open-folder", "Open folder", 719, 165, 238, 35, false, this::openFolder);

        double max = Math.max(0, visible.size() * 59 - LIST.h()); scroll = Math.clamp(scroll, 0, max);
        draw.enableScissor(LIST.x(), LIST.y(), LIST.right(), LIST.bottom());
        if (visible.isEmpty()) {
            GuiDraw.heading(draw, textRenderer, query.isBlank() ? "No schematics here yet" : "No matching files", 63, 247, 1.28f, StudioAuxiliary.TEXT);
            lines(draw, query.isBlank() ? "Use Open folder and put your .litematic, .schem or .schematic files here, then choose Refresh. You can also organize them in subfolders." : "Change the search text or clear it to see the files in this folder.", 63, 282, 548, .97f, StudioAuxiliary.MUTED, 18, 4);
        }
        for (int i = 0; i < visible.size(); i++) {
            SchematicLibrary.Entry entry = visible.get(i); int y = LIST.y() + i * 59 - (int) scroll;
            if (y + 51 < LIST.y() || y >= LIST.bottom()) continue;
            Area row = new Area(43, y, 584, 51); boolean active = selected != null && entry.path().equals(selected.path());
            GuiDraw.shaderCard(draw, row.x(), row.y(), row.w(), row.h(), 12, accent, glass(), theme(), row.contains(mx, my) ? 1 : 0, active);
            String title = entry.directory() ? entry.name() + "  ›" : entry.name();
            GuiDraw.heading(draw, textRenderer, GuiDraw.truncate(textRenderer, title, 540, 1.13f), 58, y + 9, 1.13f, active ? accent : StudioAuxiliary.TEXT);
            label(draw, entry.directory() ? "Folder · click to open" : entry.format() + " · " + size(entry.bytes()), 59, y + 32, .86f, StudioAuxiliary.MUTED);
            clippedControl("entry:" + entry.name(), row, LIST, 0, () -> {
                searchFocused = false; if (entry.directory()) navigate(entry.path()); else {selected = entry; status = "Choose Use selected to review this schematic."; failed = false;}
            });
        }
        draw.disableScissor();
        if (max > 0) {
            int h = Math.max(24, (int)(LIST.h() * LIST.h() / (LIST.h() + max)));
            StudioAuxiliary.surface(draw, LIST.right() - 4, LIST.y() + (int)((LIST.h() - h) * scroll / max), 3, h, 1.5f, accent, accent, 0, 0, 0, 0);
        }
        GuiDraw.shaderCard(draw, 658, 214, 299, 330, 17, accent, glass(), theme(), 0, false);
        GuiDraw.heading(draw, textRenderer, selected == null ? "Choose a schematic" : selected.directory() ? "Selected folder" : "Selected schematic", 677, 235, 1.25f, StudioAuxiliary.TEXT);
        if (selected == null) {
            lines(draw, "Select a file on the left. Its actual path is passed to the plan review; you do not need to type a filename.", 677, 273, 259, .99f, StudioAuxiliary.MUTED, 19, 5);
        } else {
            lines(draw, selected.name(), 677, 272, 259, 1.04f, StudioAuxiliary.TEXT, 18, 3);
            label(draw, selected.directory() ? "Folder · Enter to open" : selected.format() + " · " + size(selected.bytes()), 677, 337, .91f, accent);
            label(draw, "Modified " + MODIFIED.format(Instant.ofEpochMilli(selected.modifiedMillis())), 677, 358, .84f, StudioAuxiliary.MUTED);
            lines(draw, selected.directory() ? "Open this folder to see its schematics. Backspace returns to its parent; Root returns to your schematics folder." : "Review verifies the file, chosen site, block states and materials before you start.", 677, 392, 259, .94f, StudioAuxiliary.MUTED, 17, 3);
            if (!selected.directory() && !usable()) lines(draw, "The schematic must be between 1 byte and 64 MiB.", 677, 443, 259, .88f, Palette.ERROR, 15, 2);
        }
        enabledButton(draw, "use-selected", "Use selected", 677, 487, 260, 35, usable(), true, this::useSelected);
    }
    @Override protected void footer(DrawContext draw) {
        String count = visible.size() + " item" + (visible.size() == 1 ? "" : "s");
        String text = status.isBlank() ? count + " · Choose a file, then Use selected. Dropping a file also works." : status;
        label(draw, GuiDraw.truncate(textRenderer, text, 911, .88f), 43, 570, .88f, failed ? Palette.ERROR : StudioAuxiliary.MUTED);
    }
    private void lines(DrawContext draw, String text, int x, int y, int width, float size, int color, int pitch, int maxLines) {
        var lines = GuiDraw.wrap(textRenderer, text, width, size);
        for (int i = 0; i < Math.min(maxLines, lines.size()); i++)
            label(draw, i == maxLines - 1 && lines.size() > maxLines ? GuiDraw.truncate(textRenderer, lines.get(i) + "…", width, size) : lines.get(i), x, y + i * pitch, size, color);
    }
    private void refresh() {
        try {
            Files.createDirectories(root);
            listing = SchematicLibrary.scan(root, folder); folder = listing.folder();
            Path previous = selected == null ? null : selected.path();
            selected = listing.entries().stream().filter(entry -> entry.path().equals(previous)).findFirst().orElse(null);
            searchChanged(); failed = false;
            status = listing.limited() ? "Folder limit reached. Organize files into smaller subfolders to see every entry."
                    : listing.unreadable() > 0 ? listing.unreadable() + " entries could not be read. Refresh after checking file permissions." : "";
        } catch (IOException | SecurityException failure) { listing = null; visible = List.of(); selected = null; failed = true; status = "Could not read this folder: " + failure.getMessage(); }
    }
    private void navigate(Path directory) { folder = directory; selected = null; query = ""; scroll = 0; refresh(); }
    private void openFolder() {
        try { Files.createDirectories(root); Util.getOperatingSystem().open(folder.toFile()); status = "Folder opened. Add schematics and choose Refresh."; failed = false; }
        catch (IOException | RuntimeException failure) { failed = true; status = "Could not open folder: " + failure.getMessage(); }
    }
    private boolean usable() { return selected != null && !selected.directory() && selected.bytes() > 0 && selected.bytes() <= SchematicImporter.MAX_FILE_BYTES; }
    private void useSelected() {
        if (!usable() || client == null) return;
        try { Path path = SchematicLibrary.selectedFile(root, selected.path()); client.setScreen(returnParent); choose.accept(path); }
        catch (IOException | RuntimeException failure) { failed = true; status = "Could not select schematic: " + failure.getMessage(); }
    }
    @Override protected void searchChanged() {
        visible = listing == null ? List.of() : SchematicLibrary.filter(listing.entries(), query); scroll = 0;
        if (selected != null && visible.stream().noneMatch(entry -> entry.path().equals(selected.path()))) selected = null;
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (LIST.contains((x - ox) / scale, (y - oy) / scale)) {scroll = Math.max(0, scroll - vertical * 36); return true;}
        return false;
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (!searchFocused && key == GLFW.GLFW_KEY_F5) { refresh(); return true; }
        if (!searchFocused && key == GLFW.GLFW_KEY_BACKSPACE && listing != null && listing.canGoUp()) {navigate(folder.getParent()); return true;}
        if (!searchFocused && (key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_UP) && !visible.isEmpty()) {
            int index = selected == null ? -1 : visible.indexOf(selected);
            index = Math.clamp(index + (key == GLFW.GLFW_KEY_DOWN ? 1 : -1), 0, visible.size() - 1);
            selected = visible.get(index); focusedControl = "entry:" + selected.name();
            if (index * 59 < scroll) scroll = index * 59;
            if (index * 59 + 51 > scroll + LIST.h()) scroll = index * 59 + 51 - LIST.h();
            return true;
        }
        if (!searchFocused && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && selected != null && (focusedControl == null || focusedControl.startsWith("entry:"))) {
            if (selected.directory()) navigate(selected.path()); else useSelected(); return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }
    @Override public void onFilesDropped(List<Path> files) {
        if (files.size() != 1 || !SchematicLibrary.supported(files.getFirst())) {failed = true; status = "Drop one .litematic, .schem or .schematic file."; return;}
        if (client != null) {client.setScreen(returnParent); choose.accept(files.getFirst().toAbsolutePath().normalize());}
    }
    public List<String> entryNames() { return visible.stream().map(SchematicLibrary.Entry::name).toList(); }
    public Path currentFolder() { return folder; }
    public Path selectedFile() { return selected == null || selected.directory() ? null : selected.path(); }
    private static String size(long bytes) { return bytes >= 1024 * 1024 ? String.format(Locale.ROOT, "%.1f MiB", bytes / (1024.0 * 1024)) : bytes >= 1024 ? String.format(Locale.ROOT, "%.1f KiB", bytes / 1024.0) : bytes + " B"; }
}
