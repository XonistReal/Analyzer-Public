package dev.analyzer.ui;

import dev.analyzer.core.AnalyzerCore;
import dev.analyzer.finder.FinderEngine;
import dev.analyzer.finder.StashJournal;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Local historical observations, using Studio's native shader surfaces and input handling. */
public final class FinderHistoryScreen extends CatalogScreen {
    private static final Area LIST = new Area(40, 153, 579, 397);
    private static final Area COUNTS = new Area(660, 238, 279, 171);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MMM d, HH:mm:ss").withZone(ZoneId.systemDefault());
    private List<StashJournal.Entry> entries = List.of();
    private String selectedKey = "";
    private boolean currentDimensionOnly;
    private double scroll;
    private double countScroll;
    private int refreshTicks;

    public FinderHistoryScreen(AnalyzerCore core, Screen parent) {
        super(core, "Local findings");
        withParent(parent);
        refresh();
    }

    @Override protected String layout() { return "Studio"; }
    @Override protected void init() { super.init(); refresh(); }
    @Override protected float canvasScale() { return StudioAuxiliary.canvasScale(client, W, H, .72f, .84f); }
    @Override protected void backdrop(DrawContext draw) { StudioAuxiliary.backdrop(draw, core, width, height); }

    @Override protected void header(DrawContext draw) {
        GuiDraw.heading(draw, textRenderer, "Local findings", 43, 31, 2, StudioAuxiliary.TEXT);
        label(draw, "Storage observations saved for this world or server", 44, 66, .96f, StudioAuxiliary.MUTED);
        button(draw, "findings-back", "Back to modules", 787, 31, 169, 31, false, this::close);
    }

    @Override protected void button(DrawContext draw, String id, String text, int x, int y, int w, int h, boolean selected, Runnable action) {
        Area area = new Area(x, y, w, h);
        StudioAuxiliary.button(draw, core, textRenderer, x, y, w, h, text, true, selected, area.contains(mx, my));
        control(id, area, 0, action);
    }

    @Override protected void body(DrawContext draw) {
        button(draw, "findings-dimension", currentDimensionOnly ? "This dimension" : "All dimensions", 43, 104, 180, 33,
                currentDimensionOnly, () -> { currentDimensionOnly = !currentDimensionOnly; scroll = 0; refresh(); });
        button(draw, "findings-refresh", "Refresh", 234, 104, 105, 33, false, this::refresh);
        label(draw, entries.size() + " recorded chunks", 356, 116, .94f, StudioAuxiliary.MUTED);
        double maxScroll = Math.max(0, entries.size() * 77 - LIST.h());
        scroll = Math.clamp(scroll, 0, maxScroll);
        draw.enableScissor(LIST.x(), LIST.y(), LIST.right(), LIST.bottom());
        if (entries.isEmpty()) {
            String message = client == null || client.world == null ? "Join a world to review its findings" : "No saved storage clusters";
            GuiDraw.heading(draw, textRenderer, message, 61, 190, 1.25f, StudioAuxiliary.TEXT);
            List<String> explanation = GuiDraw.wrap(textRenderer,
                    "Enable Advanced Stash Finder to record loaded storage clusters. History stays on this computer and is separate for every world or server.", 508, .96f);
            for (int i = 0; i < explanation.size(); i++) label(draw, explanation.get(i), 61, 226 + i * 18, .96f, StudioAuxiliary.MUTED);
        }
        for (int i = 0; i < entries.size(); i++) {
            StashJournal.Entry entry = entries.get(i);
            int y = LIST.y() + 3 + i * 77 - (int) scroll;
            if (y + 65 < LIST.y() || y >= LIST.bottom()) continue;
            boolean selected = key(entry).equals(selectedKey);
            Area card = new Area(43, y, 571, 65);
            GuiDraw.shaderCard(draw, card.x(), card.y(), card.w(), card.h(), 15, accent, glass(), theme(), card.contains(mx, my) ? 1 : 0, selected);
            GuiDraw.heading(draw, textRenderer, center(entry) + "  ·  " + entry.total() + " blocks", 59, y + 12, 1.15f,
                    selected ? accent : StudioAuxiliary.TEXT);
            label(draw, dimensionLabel(entry.dimension()), 59, y + 38, .86f, StudioAuxiliary.MUTED);
            String seen = "Seen " + format(entry.lastSeen());
            label(draw, seen, 598 - GuiDraw.width(textRenderer, seen, .84f), y + 39, .84f, StudioAuxiliary.MUTED);
            clippedControl("finding:" + key(entry), card, LIST, 0, () -> { selectedKey = key(entry); countScroll = 0; });
        }
        draw.disableScissor();
        if (maxScroll > 0) {
            int height = Math.max(24, (int) (LIST.h() * LIST.h() / (LIST.h() + maxScroll)));
            StudioAuxiliary.surface(draw, LIST.right() - 3, LIST.y() + (int) ((LIST.h() - height) * scroll / maxScroll),
                    3, height, 1.5f, accent, accent, 0, 0, 0, 0);
        }
        details(draw);
    }

    private void details(DrawContext draw) {
        GuiDraw.shaderCard(draw, 643, 105, 314, 445, 18, accent, glass(), theme(), 0, false);
        GuiDraw.heading(draw, textRenderer, "Observed storage", 663, 126, 1.45f, StudioAuxiliary.TEXT);
        StashJournal.Entry selected = entries.stream().filter(e -> key(e).equals(selectedKey)).findFirst().orElse(null);
        if (selected == null) {
            label(draw, "Select a recorded chunk", 663, 162, .97f, StudioAuxiliary.MUTED);
        } else {
            label(draw, center(selected), 663, 162, 1.08f, accent);
            label(draw, GuiDraw.truncate(textRenderer, selected.dimension(), 272, .84f), 663, 187, .84f, StudioAuxiliary.MUTED);
            label(draw, "Chunk " + selected.chunkX() + ", " + selected.chunkZ(), 663, 211, .88f, StudioAuxiliary.MUTED);
            var counts = selected.counts().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).toList();
            countScroll = Math.clamp(countScroll, 0, Math.max(0, counts.size() * 19 - COUNTS.h()));
            draw.enableScissor(COUNTS.x(), COUNTS.y(), COUNTS.right(), COUNTS.bottom());
            for (int i = 0; i < counts.size(); i++) {
                int row = 242 + i * 19 - (int) countScroll;
                if (row < COUNTS.y() - 15 || row >= COUNTS.bottom()) continue;
                var count = counts.get(i);
                label(draw, GuiDraw.truncate(textRenderer, blockLabel(count.getKey()), 222, .88f), 663, row, .88f, StudioAuxiliary.TEXT);
                String amount = count.getValue().toString();
                label(draw, amount, 935 - GuiDraw.width(textRenderer, amount, .88f), row, .88f, accent);
            }
            draw.disableScissor();
            if (counts.size() * 19 > COUNTS.h()) {
                int height = Math.max(18, COUNTS.h() * COUNTS.h() / (counts.size() * 19));
                StudioAuxiliary.surface(draw, COUNTS.right() - 2,
                        COUNTS.y() + (int) ((COUNTS.h() - height) * countScroll / (counts.size() * 19 - COUNTS.h())),
                        2, height, 1, accent, accent, 0, 0, 0, 0);
            }
            label(draw, "First " + format(selected.firstSeen()), 663, 425, .84f, StudioAuxiliary.MUTED);
            label(draw, "Last  " + format(selected.lastSeen()), 663, 445, .84f, StudioAuxiliary.MUTED);
        }
        label(draw, "History is not a live container inventory", 663, 478, .81f, StudioAuxiliary.MUTED);
        button(draw, "findings-clear", "Clear this world's history", 663, 506, 272, 28, false, () -> {
            FinderEngine.clearHistory();
            selectedKey = "";
            scroll = 0;
            refresh();
            status = FinderEngine.status("advanced-stash-finder");
        });
    }

    @Override protected void footer(DrawContext draw) {
        String message = status.isBlank() ? FinderEngine.status("advanced-stash-finder") : status;
        if (message.isBlank()) message = "Saved observations can be outdated; only received blocks are counted";
        label(draw, GuiDraw.truncate(textRenderer, message, 897, .84f), 43, 570, .84f, StudioAuxiliary.MUTED);
    }

    private void refresh() {
        String dimension = client != null && client.world != null ? client.world.getRegistryKey().getValue().toString() : "";
        entries = dimension.isEmpty() ? List.of() : FinderEngine.history().stream()
                .filter(e -> !currentDimensionOnly || e.dimension().equals(dimension)).toList();
        if (entries.stream().noneMatch(e -> key(e).equals(selectedKey))) selectedKey = entries.isEmpty() ? "" : key(entries.getFirst());
    }

    @Override public void tick() {
        super.tick();
        if (++refreshTicks >= 20) { refreshTicks = 0; refresh(); }
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (LIST.contains((x - ox) / scale, (y - oy) / scale)) { scroll = Math.max(0, scroll - vertical * 38); return true; }
        if (COUNTS.contains((x - ox) / scale, (y - oy) / scale)) { countScroll = Math.max(0, countScroll - vertical * 19); return true; }
        return false;
    }

    @Override public void close() {
        Screen parent = parentScreen();
        if (parent == null || MenuScreens.matchesLayout(core, parent)) { super.close(); return; }
        Screen next = MenuScreens.create(core, parent instanceof CatalogScreen catalog ? catalog.selectedModuleId() : null);
        if (next instanceof CatalogScreen catalog && parent instanceof CatalogScreen previous) {
            catalog.restoreQueryText(previous.queryText());
            catalog.withParent(previous.parentScreen());
        }
        client.setScreen(next);
    }

    private static String key(StashJournal.Entry entry) { return entry.dimension() + "/" + entry.chunkX() + "/" + entry.chunkZ(); }
    private static String center(StashJournal.Entry entry) { return "X " + ((long) entry.chunkX() * 16 + 8) + "  Z " + ((long) entry.chunkZ() * 16 + 8); }
    private static String format(long millis) { try { return TIME.format(Instant.ofEpochMilli(millis)); } catch (RuntimeException ignored) { return "Unknown time"; } }
    private static String dimensionLabel(String id) { return blockLabel(id); }
    private static String blockLabel(String id) {
        String text = id.startsWith("minecraft:") ? id.substring(10) : id;
        text = text.replace('_', ' ');
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
