package dev.analyzer.automation;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;

/** Synthetic NBT fixtures exercise the file formats without a Minecraft registry or saved worlds. */
public final class SchematicSelfTest {
    private static int assertions;
    private static Path directory;
    public static void main(String[] args) throws Exception {
        directory = Files.createTempDirectory("analyzer-schematic-fixtures-");
        try {
            litematic(); sponge(); legacy(); malformed(); sitePolicy();
            System.out.println("SchematicSelfTest: " + assertions + " assertions passed");
        } finally {
            try (var files = Files.list(directory)) { for (Path file : files.toList()) Files.delete(file); }
            Files.delete(directory);
        }
    }
    private static void litematic() throws Exception {
        List<Object> palette = List.of(block("air"), block("stone"), Map.of("Name", "minecraft:oak_log", "Properties", Map.of("axis", "x")));
        Map<String, Object> region = region(4, 8, -2, -3, -2, -1, palette, new int[]{1, 2, 0, 2, 1, 0});
        SchematicData data = load("signed.litematic", Map.of("Version", 6, "MinecraftDataVersion", 4189, "Regions", Map.of("base", region)), true);
        check(data.width() == 3 && data.height() == 2 && data.length() == 1, "Signed sizes normalize to absolute dimensions");
        check(data.offsetX() == 2 && data.offsetY() == 7 && data.offsetZ() == -2, "Negative sizes include both selected end blocks");
        check(data.stateAt(0, 0, 0).name().equals("minecraft:stone") && data.stateAt(1, 0, 0).properties().get("axis").equals("x"), "Signed regions retain storage order and block properties");
        check(data.stateAt(0, 1, 0).name().equals("minecraft:oak_log"), "Litematic storage order is x then z then y");
        check(data.materialCounts().get("minecraft:stone") == 2 && data.materialCounts().get("minecraft:oak_log[axis=x]") == 2, "Exact palette materials exclude air");
        check(data.sha256().length() == 64 && data.dataVersion() == 4189, "File digest and Minecraft data version retained");
        SchematicData immutable = data;
        rejectsRuntime(() -> immutable.palette().clear(), "Palette immutable"); rejectsRuntime(() -> immutable.materialCounts().clear(), "Material list immutable");
        List<Object> many = new ArrayList<>(); for (int i = 0; i < 17; i++) many.add(block("test_" + i));
        int[] entries = new int[43]; for (int i = 0; i < entries.length; i++) entries[i] = i % 17;
        data = load("packed.litematic", Map.of("Version", 6, "Regions", Map.of("base", region(0, 0, 0, 43, 1, 1, many, entries))), false);
        for (int i = 0; i < entries.length; i++) check(data.stateAt(i, 0, 0).name().equals("minecraft:test_" + entries[i]), "Five-bit entries correctly cross packed-long boundaries: " + i);
        var left = region(0, 0, 0, 1, 1, 1, List.of(block("stone")), new int[]{0});
        var right = region(2, 0, 0, 1, 1, 1, List.of(block("oak_planks")), new int[]{0});
        data = load("regions.litematic", Map.of("Version", 7, "Regions", Map.of("left", left, "right", right)), true);
        check(data.width() == 3 && data.stateAt(1, 0, 0) == null, "Space outside subregions remains unmanaged rather than air to clear");
        check(data.stateAt(2, 0, 0).name().equals("minecraft:oak_planks"), "Multiple independent palettes remap correctly");
        rejects("overlap.litematic", Map.of("Version", 6, "Regions", Map.of("left", left, "right", region(0, 0, 0, 1, 1, 1, List.of(block("dirt")), new int[]{0}))), "Conflicting overlaps rejected");
        rejects("bad-palette.litematic", Map.of("Version", 6, "Regions", Map.of("bad", region(0, 0, 0, 1, 1, 1, List.of(block("air")), new int[]{2}))), "Out-of-range palette index rejected");
        var entityRegion = new LinkedHashMap<>(left); entityRegion.put("TileEntities", List.of(Map.of("Command", "/op anyone", "id", "minecraft:command_block")));
        data = load("payload.litematic", Map.of("Version", 6, "Regions", Map.of("base", entityRegion)), true);
        check(data.warnings().size() == 1 && data.stateAt(0, 0, 0).name().equals("minecraft:stone"), "Block entity payload never becomes executable content or block state");
    }
    private static void sponge() throws Exception {
        Map<String, Object> v2 = dimensions(2, 1, 2); v2.put("Version", 2); v2.put("DataVersion", 4189);
        v2.put("Palette", Map.of("minecraft:air", 0, "minecraft:oak_log[axis=z]", 128)); v2.put("BlockData", new byte[]{(byte)128, 1, 0, 0, (byte)128, 1}); v2.put("Offset", new int[]{-3, 4, 7});
        SchematicData data = load("sponge2.schem", v2, true);
        check(data.offsetX() == -3 && data.offsetY() == 4 && data.offsetZ() == 7, "Sponge offset retained");
        check(data.materialCounts().get("minecraft:oak_log[axis=z]") == 2, "Sparse Sponge palette IDs use bounded varints");
        check(data.stateAt(1, 0, 1).properties().get("axis").equals("z"), "Sponge x then z then y storage order");
        Map<String, Object> v3 = dimensions(1, 2, 1); v3.put("Version", 3); v3.put("DataVersion", 4189);
        v3.put("Blocks", Map.of("Palette", Map.of("minecraft:stone", 0), "Data", new byte[]{0, 0}));
        data = load("sponge3.schem", Map.of("Schematic", v3), true);
        check(data.height() == 2 && data.materialCounts().get("minecraft:stone") == 2, "Sponge v3 nested container accepted");
        var invalid = new LinkedHashMap<>(v2); invalid.put("BlockData", new byte[]{(byte)128}); rejects("truncated.schem", invalid, "Truncated varint rejected");
        invalid.put("BlockData", new byte[]{0, 0, 0, 0, 0}); rejects("extra.schem", invalid, "Extra varints rejected");
        invalid.put("BlockData", new byte[]{1, 0, 0, 0}); rejects("missing.schem", invalid, "Unknown palette ID rejected");
        invalid.put("BlockData", new byte[]{(byte)128, (byte)128, (byte)128, (byte)128, 8}); rejects("overflow.schem", invalid, "Positive integer varint overflow rejected");
        invalid.put("Palette", Map.of("minecraft:air", 0, "minecraft:stone", 0)); rejects("duplicate.schem", invalid, "Duplicate palette IDs rejected");
        rejectsRuntime(() -> SchematicImporter.parseState("minecraft:stone[axis=x,axis=y]"), "Duplicate state properties rejected");
        rejectsRuntime(() -> SchematicImporter.parseState("minecraft:stone[axis=x"), "Unclosed state properties rejected");
    }
    private static void legacy() throws Exception {
        Map<String, Object> nbt = dimensions(2, 1, 2); nbt.put("Materials", "Alpha"); nbt.put("Blocks", new byte[]{1, 5, 17, 53}); nbt.put("Data", new byte[]{2, 4, 4, 5});
        SchematicData data = load("legacy.schematic", nbt, true);
        check(data.stateAt(0, 0, 0).name().equals("minecraft:polished_granite"), "Legacy stone variant retained");
        check(data.stateAt(1, 0, 0).name().equals("minecraft:acacia_planks"), "Legacy wood variant retained");
        check(data.stateAt(0, 0, 1).properties().get("axis").equals("x"), "Legacy log axis retained");
        check(data.stateAt(1, 0, 1).properties().get("half").equals("top") && data.stateAt(1, 0, 1).properties().get("facing").equals("west"), "Legacy stair half and facing retained");
        var unsupported = dimensions(1, 1, 1); unsupported.put("Materials", "Alpha"); unsupported.put("Blocks", new byte[]{(byte)255}); unsupported.put("Data", new byte[]{0});
        rejects("unknown.schematic", unsupported, "Unavailable legacy ID rejected explicitly");
        unsupported.put("Blocks", new byte[]{1}); unsupported.put("AddBlocks", new byte[]{1}); rejects("extended.schematic", unsupported, "AddBlocks nibble contributes the high ID bits");
        unsupported.put("AddBlocks", new byte[]{0, 0}); rejects("bad-add.schematic", unsupported, "AddBlocks must match packed volume");
        rejectsRuntime(() -> LegacySchematicBlocks.resolve(1, 7), "Unknown legacy metadata does not become default stone");
        check(LegacySchematicBlocks.resolve(126, 10).properties().get("type").equals("top"), "Legacy top slab converts explicitly");
        check(LegacySchematicBlocks.resolve(125, 2).properties().get("type").equals("double"), "Legacy double slab remains double for material accounting");
        check(LegacySchematicBlocks.resolve(44, 2).name().equals("minecraft:petrified_oak_slab"), "Legacy stone-family wooden slabs do not silently become craftable oak slabs");
        var fence = dimensions(1, 1, 1); fence.put("Materials", "Alpha"); fence.put("Blocks", new byte[]{85}); fence.put("Data", new byte[]{0});
        SchematicData fenceData = load("legacy-fence.schematic", fence, true);
        check(!LegacySchematicBlocks.buildProblem(fenceData).isEmpty(), "Legacy connections must be re-exported before a potentially wrong build starts");
        check(LegacySchematicBlocks.buildProblem(data).isEmpty(), "Supported isolated legacy stone/log/plank/straight-stair fixture remains buildable");
    }
    private static void malformed() throws Exception {
        var volume = dimensions(256, 256, 256); volume.put("Materials", "Alpha");
        rejects("volume.schematic", volume, "Volume bound precedes missing arrays");
        var zero = dimensions(0, 1, 1); zero.put("Materials", "Alpha");
        rejects("zero.schematic", zero, "Zero dimensions rejected");
        var overflowRegion = region(0, 0, 0, 1, 1, 1, List.of(block("stone")), new int[]{0}); overflowRegion.put("Size", Map.of("x", Integer.MIN_VALUE, "y", 1, "z", 1));
        rejects("integer-min.litematic", Map.of("Version", 6, "Regions", Map.of("bad", overflowRegion)), "Integer minimum signed dimension rejected");
        rejects("new-format.litematic", Map.of("Version", 99, "Regions", Map.of()), "Unrecognized newer format fails explicitly");
        Path truncated = directory.resolve("broken.schem"); Files.write(truncated, new byte[]{10, 0, 0, 7, 0, 1, 'x', -1, -1, -1, -1});
        rejectsFile(truncated, "Negative NBT array size rejected before allocation");
        Path huge = directory.resolve("huge-array.schem"); Files.write(huge, new byte[]{10, 0, 0, 7, 0, 1, 'x', 127, -1, -1, -1});
        rejectsFile(huge, "Oversized NBT arrays rejected before allocation");
        Map<String, Object> nested = Map.of("x", 1); for (int i = 0; i < 40; i++) nested = Map.of("x", nested);
        rejects("deep.schem", nested, "NBT depth bounded");
        Path extra = fixture("trailing.schem", Map.of("Version", 2), false); Files.write(extra, new byte[]{4}, StandardOpenOption.APPEND); rejectsFile(extra, "Trailing NBT payload rejected");
        Path invalidExtension = directory.resolve("script.txt"); Files.writeString(invalidExtension, "#goto 0 0"); rejectsFile(invalidExtension, "Non-schematic files are not accepted");
        try { SchematicImporter.load(directory.resolve("missing.litematic")); throw new AssertionError("Missing file accepted"); } catch (IOException expected) { assertions++; }
    }
    private static void sitePolicy() {
        var good = survey(true, true, 0, 0, 0, 0, 49, 49, 0, true, 10);
        check(BuildSitePolicy.evaluate(good).accepted(), "Loaded level clear supported reachable site accepted");
        check(BuildSitePolicy.evaluate(good).score() > BuildSitePolicy.evaluate(survey(true, true, 0, 0, 0, 0, 49, 49, 1, true, 20)).score(), "Distance and slope reduce score");
        check(!BuildSitePolicy.evaluate(survey(false, true, 0, 0, 0, 0, 49, 49, 0, true, 10)).accepted(), "Unknown chunks rejected");
        check(!BuildSitePolicy.evaluate(survey(true, false, 0, 0, 0, 0, 49, 49, 0, true, 10)).accepted(), "Build limit and border rejected");
        check(!BuildSitePolicy.evaluate(survey(true, true, 1, 0, 0, 0, 49, 49, 0, true, 10)).accepted(), "Occupied base never automatically cleared");
        check(!BuildSitePolicy.evaluate(survey(true, true, 0, 1, 0, 0, 49, 49, 0, true, 10)).accepted(), "Fluid rejected");
        check(!BuildSitePolicy.evaluate(survey(true, true, 0, 0, 1, 0, 49, 49, 0, true, 10)).accepted(), "Hazard rejected");
        check(!BuildSitePolicy.evaluate(survey(true, true, 0, 0, 0, 1, 49, 49, 0, true, 10)).accepted(), "Nearby hostiles rejected");
        check(!BuildSitePolicy.evaluate(survey(true, true, 0, 0, 0, 0, 49, 48, 0, true, 10)).accepted(), "Unsupported foundation cell rejected");
        check(!BuildSitePolicy.evaluate(survey(true, true, 0, 0, 0, 0, 49, 49, 2, true, 10)).accepted(), "Excess slope rejected");
        check(!BuildSitePolicy.evaluate(survey(true, true, 0, 0, 0, 0, 49, 49, 0, false, 10)).accepted(), "Inaccessible footprint rejected");
        check(!BuildSitePolicy.evaluate(survey(true, true, 0, 0, 0, 0, 49, 49, 0, true, Double.NaN)).accepted(), "Non-finite site score rejected");
        check(!BuildSitePolicy.evaluate(survey(true, true, -1, 0, 0, 0, 49, 49, 0, true, 10)).accepted(), "Negative survey counts rejected");
    }
    private static BuildSitePolicy.Survey survey(boolean loaded, boolean bounds, int occupied, int fluids, int hazards, int mobs, int ground, int supported, int slope, boolean access, double distance) { return new BuildSitePolicy.Survey(loaded, bounds, occupied, fluids, hazards, mobs, ground, supported, slope, access, distance); }
    private static Map<String, Object> block(String name) { return Map.of("Name", "minecraft:" + name); }
    private static Map<String, Object> dimensions(int width, int height, int length) { var nbt = new LinkedHashMap<String, Object>(); nbt.put("Width", (short)width); nbt.put("Height", (short)height); nbt.put("Length", (short)length); return nbt; }
    private static Map<String, Object> region(int x, int y, int z, int sx, int sy, int sz, List<Object> palette, int[] states) {
        int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(palette.size() - 1)); long[] packed = new long[(states.length * bits + 63) / 64];
        for (int i = 0; i < states.length; i++) { long offset = (long)i * bits; int word = (int)(offset / 64), shift = (int)(offset % 64); packed[word] |= (long)states[i] << shift; if (shift + bits > 64) packed[word + 1] |= (long)states[i] >>> (64 - shift); }
        var region = new LinkedHashMap<String, Object>(); region.put("Position", Map.of("x", x, "y", y, "z", z)); region.put("Size", Map.of("x", sx, "y", sy, "z", sz)); region.put("BlockStatePalette", palette); region.put("BlockStates", packed); return region;
    }
    private static SchematicData load(String name, Map<String, Object> nbt, boolean gzip) throws Exception { return SchematicImporter.load(fixture(name, nbt, gzip)); }
    private static Path fixture(String name, Map<String, Object> nbt, boolean gzip) throws Exception {
        Path file = directory.resolve(name);
        try (OutputStream raw = Files.newOutputStream(file); DataOutputStream output = new DataOutputStream(gzip ? new GZIPOutputStream(raw) : raw)) { output.writeByte(10); output.writeUTF("Schematic"); write(output, nbt); }
        return file;
    }
    private static int type(Object value) { if (value instanceof Byte) return 1; if (value instanceof Short) return 2; if (value instanceof Integer) return 3; if (value instanceof Long) return 4; if (value instanceof byte[]) return 7; if (value instanceof String) return 8; if (value instanceof List<?>) return 9; if (value instanceof Map<?, ?>) return 10; if (value instanceof int[]) return 11; if (value instanceof long[]) return 12; throw new AssertionError("Unknown fixture tag: " + value); }
    private static void write(DataOutputStream output, Object value) throws IOException {
        switch (type(value)) {
            case 1 -> output.writeByte((Byte)value); case 2 -> output.writeShort((Short)value); case 3 -> output.writeInt((Integer)value); case 4 -> output.writeLong((Long)value); case 8 -> output.writeUTF((String)value);
            case 7 -> { byte[] array = (byte[])value; output.writeInt(array.length); output.write(array); }
            case 11 -> { int[] array = (int[])value; output.writeInt(array.length); for (int item : array) output.writeInt(item); }
            case 12 -> { long[] array = (long[])value; output.writeInt(array.length); for (long item : array) output.writeLong(item); }
            case 9 -> { List<?> list = (List<?>)value; output.writeByte(list.isEmpty() ? 10 : type(list.getFirst())); output.writeInt(list.size()); for (Object entry : list) write(output, entry); }
            case 10 -> { for (var entry : ((Map<?, ?>)value).entrySet()) { output.writeByte(type(entry.getValue())); output.writeUTF((String)entry.getKey()); write(output, entry.getValue()); } output.writeByte(0); }
            default -> throw new AssertionError("Unknown fixture type");
        }
    }
    private static void rejects(String name, Map<String, Object> nbt, String description) throws Exception { rejectsFile(fixture(name, nbt, true), description); }
    private static void rejectsFile(Path path, String description) throws Exception { try { SchematicImporter.load(path); throw new AssertionError(description); } catch (IOException expected) { assertions++; } }
    private static void rejectsRuntime(Runnable run, String description) { try { run.run(); throw new AssertionError(description); } catch (IllegalArgumentException | UnsupportedOperationException expected) { assertions++; } }
    private static void check(boolean condition, String description) { if (!condition) throw new AssertionError(description); assertions++; }
}
