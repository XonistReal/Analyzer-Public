package dev.analyzer.automation;

import java.util.*;

/** Parsed local artwork, without Minecraft objects, entities, commands or block-entity data. */
public final class SchematicData {
    public record State(String name, Map<String, String> properties) {
        public State {
            if (name == null || !name.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) throw new IllegalArgumentException("Invalid block identifier: " + name);
            properties = Collections.unmodifiableMap(new TreeMap<>(properties));
            for (var entry : properties.entrySet()) {
                if (!entry.getKey().matches("[a-z0-9_]+") || !entry.getValue().matches("[a-z0-9_-]+"))
                    throw new IllegalArgumentException("Invalid block property in " + name);
            }
        }
        public boolean air() { return name.equals("minecraft:air") || name.equals("minecraft:cave_air") || name.equals("minecraft:void_air"); }
        public String canonical() {
            if (properties.isEmpty()) return name;
            StringJoiner join = new StringJoiner(",", name + "[", "]");
            properties.forEach((key, value) -> join.add(key + "=" + value));
            return join.toString();
        }
    }
    private final String name, format, sha256;
    private final int width, height, length, offsetX, offsetY, offsetZ, dataVersion;
    private final List<State> palette;
    private final int[] states;
    private final List<String> warnings;
    SchematicData(String name, String format, String sha256, int width, int height, int length,
                  int offsetX, int offsetY, int offsetZ, int dataVersion, List<State> palette, int[] states, List<String> warnings) {
        if (states.length != SchematicImporter.volume(width, height, length)) throw new IllegalArgumentException("Invalid schematic volume");
        this.name = name; this.format = format; this.sha256 = sha256;
        this.width = width; this.height = height; this.length = length;
        this.offsetX = offsetX; this.offsetY = offsetY; this.offsetZ = offsetZ; this.dataVersion = dataVersion;
        this.palette = List.copyOf(palette); this.states = states.clone(); this.warnings = List.copyOf(warnings);
        for (int index : states) if (index < -1 || index >= palette.size()) throw new IllegalArgumentException("Invalid palette index");
    }
    public String name() { return name; }
    public String format() { return format; }
    public String sha256() { return sha256; }
    public int width() { return width; }
    public int height() { return height; }
    public int length() { return length; }
    public int volume() { return states.length; }
    public int offsetX() { return offsetX; }
    public int offsetY() { return offsetY; }
    public int offsetZ() { return offsetZ; }
    public int dataVersion() { return dataVersion; }
    public List<State> palette() { return palette; }
    public List<String> warnings() { return warnings; }
    public boolean contains(int x, int y, int z) { return x >= 0 && x < width && y >= 0 && y < height && z >= 0 && z < length; }
    public State stateAt(int x, int y, int z) {
        int index = indexAt(x, y, z);
        return index < 0 ? null : palette.get(index);
    }
    int indexAt(int x, int y, int z) { return contains(x, y, z) ? states[x + width * (z + length * y)] : -1; }
    /** Counts intended nonair block states. Native item quantities are calculated separately. */
    public Map<String, Integer> materialCounts() {
        Map<String, Integer> counts = new TreeMap<>();
        for (int index : states) if (index >= 0 && !palette.get(index).air()) counts.merge(palette.get(index).canonical(), 1, Integer::sum);
        return Collections.unmodifiableMap(counts);
    }
}
