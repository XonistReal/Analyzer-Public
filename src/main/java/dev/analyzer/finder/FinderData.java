package dev.analyzer.finder;

import java.util.List;
import java.util.Map;

/** Small immutable observations; UNKNOWN never counts as air or a missing supporting block. */
public final class FinderData {
    private FinderData() { }
    public record Cell(String id, boolean loaded, boolean air, boolean solid, boolean fluid,
                       int blockLight, int luminance, Map<String, String> properties) {
        public static final Cell UNKNOWN = new Cell("", false, false, false, false, 0, 0, Map.of());
        public String property(String key) { return properties.getOrDefault(key, ""); }
        public int number(String key) { try { return Integer.parseInt(property(key)); } catch (NumberFormatException ignored) { return -1; } }
        public boolean is(String path) { return id.equals("minecraft:" + path); }
    }
    public interface Grid {
        Cell cell(int x, int y, int z);
        int bottomY();
        int topY();
    }
    public interface Options {
        boolean bool(String module, String setting);
        double number(String module, String setting);
        String choice(String module, String setting);
    }
    public record Mark(String module, int x, int y, int z, double width, double height, double depth,
                       String evidence, int color) {
        public Mark(String module, int x, int y, int z, String evidence) {
            this(module, x, y, z, 1, 1, 1, evidence, 0);
        }
    }
    /** Chunk aggregates only retain received block counts, not private container contents. */
    public record ChunkFacts(int x, int z, int bottomY, int topY, Map<String, Integer> counts,
                             int storage, int spawners, int amethyst, int budding, int darkGeode,
                             int kelpColumns, int tallKelpColumns, int seaLevelKelpColumns,
                             List<Mark> markers) { }
}
