package dev.analyzer.automation;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Original bounded import of Litematica, legacy MCEdit/Schematica and Sponge block data. */
public final class SchematicImporter {
    public static final int MAX_VOLUME = 262_144, MAX_SIDE = 256, MAX_PALETTE = 16_384, MAX_REGIONS = 64;
    public static final long MAX_FILE_BYTES = 64L * 1024 * 1024;
    private SchematicImporter() { }
    public static SchematicData load(Path path) throws IOException {
        if (path == null || !Files.isRegularFile(path)) throw new IOException("Choose an existing .litematic, .schematic or .schem file");
        long size = Files.size(path);
        if (size <= 0 || size > MAX_FILE_BYTES) throw new IOException("Schematic file must be between 1 byte and 64 MiB");
        String filename = path.getFileName().toString(), lower = filename.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".litematic") && !lower.endsWith(".schematic") && !lower.endsWith(".schem"))
            throw new IOException("Supported schematic files: .litematic, .schematic, .schem");
        byte[] bytes;
        try (InputStream file = Files.newInputStream(path)) {
            bytes = file.readNBytes((int)MAX_FILE_BYTES + 1);
            if (bytes.length > MAX_FILE_BYTES) throw new IOException("Schematic file exceeds 64 MiB");
        }
        Map<String, Object> nbt = BoundedSchematicNbt.read(new ByteArrayInputStream(bytes));
        try {
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            if (lower.endsWith(".litematic")) return litematic(filename, hash, nbt);
            if (lower.endsWith(".schem")) return sponge(filename, hash, nbt);
            return legacy(filename, hash, nbt);
        } catch (IllegalArgumentException failure) { throw new IOException(failure.getMessage(), failure); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    static int volume(int x, int y, int z) {
        long count = (long)x * y * z;
        if (x < 1 || y < 1 || z < 1 || x > MAX_SIDE || y > MAX_SIDE || z > MAX_SIDE || count > MAX_VOLUME)
            throw new IllegalArgumentException("Schematic dimensions exceed 256 per side or 262,144 total blocks");
        return (int)count;
    }
    private record Region(int x, int y, int z, int width, int height, int length, List<SchematicData.State> palette, int[] states) { }
    private static SchematicData litematic(String name, String hash, Map<String, Object> root) {
        int version = integer(root, "Version");
        if (version < 4 || version > 7) throw new IllegalArgumentException("Supported Litematica format versions are 4–7; this file is " + version);
        Map<String, Object> tags = compound(root, "Regions");
        if (tags.isEmpty() || tags.size() > MAX_REGIONS) throw new IllegalArgumentException("Schematic must contain 1–64 regions");
        List<Region> regions = new ArrayList<>(); List<String> warnings = new ArrayList<>();
        long total = 0;
        for (var entry : tags.entrySet()) {
            Map<String, Object> region = map(entry.getValue(), "Region " + entry.getKey());
            Map<String, Object> pos = compound(region, "Position"), size = compound(region, "Size");
            int sx = integer(size, "x"), sy = integer(size, "y"), sz = integer(size, "z");
            if (sx == Integer.MIN_VALUE || sy == Integer.MIN_VALUE || sz == Integer.MIN_VALUE) throw new IllegalArgumentException("Invalid signed region size");
            int width = Math.abs(sx), height = Math.abs(sy), length = Math.abs(sz), count = volume(width, height, length);
            if ((total += count) > MAX_VOLUME) throw new IllegalArgumentException("Combined region volume exceeds 262,144 blocks");
            int x = regionMinimum(integer(pos, "x"), sx), y = regionMinimum(integer(pos, "y"), sy), z = regionMinimum(integer(pos, "z"), sz);
            List<?> sourcePalette = list(region, "BlockStatePalette");
            paletteSize(sourcePalette.size()); List<SchematicData.State> palette = new ArrayList<>();
            for (Object element : sourcePalette) {
                Map<String, Object> block = map(element, "BlockStatePalette entry"); Map<String, String> props = new TreeMap<>();
                if (block.containsKey("Properties")) compound(block, "Properties").forEach((key, value) -> {
                    if (!(value instanceof String text)) throw new IllegalArgumentException("Block properties must be strings"); props.put(key, text);
                });
                palette.add(new SchematicData.State(string(block, "Name"), props));
            }
            int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(palette.size() - 1));
            long[] packed = longs(region, "BlockStates");
            int required = (int)(((long)count * bits + 63) / 64);
            if (packed.length != required) throw new IllegalArgumentException("BlockStates length does not match region volume and palette");
            int[] states = new int[count]; long mask = (1L << bits) - 1;
            for (int i = 0; i < count; i++) {
                long bit = (long)i * bits; int word = (int)(bit / 64), shift = (int)(bit % 64);
                long value = packed[word] >>> shift;
                if (shift + bits > 64) value |= packed[word + 1] << (64 - shift);
                states[i] = (int)(value & mask);
                if (states[i] >= palette.size()) throw new IllegalArgumentException("BlockStates references a missing palette entry");
            }
            ignoredPayload(region, warnings);
            regions.add(new Region(x, y, z, width, height, length, palette, states));
        }
        int minX = regions.stream().mapToInt(Region::x).min().orElseThrow(), minY = regions.stream().mapToInt(Region::y).min().orElseThrow(), minZ = regions.stream().mapToInt(Region::z).min().orElseThrow();
        long maxX = regions.stream().mapToLong(r -> (long)r.x + r.width).max().orElseThrow(), maxY = regions.stream().mapToLong(r -> (long)r.y + r.height).max().orElseThrow(), maxZ = regions.stream().mapToLong(r -> (long)r.z + r.length).max().orElseThrow();
        if (maxX - minX > MAX_SIDE || maxY - minY > MAX_SIDE || maxZ - minZ > MAX_SIDE) throw new IllegalArgumentException("Combined region bounds exceed 256 per side");
        int width = (int)(maxX - minX), height = (int)(maxY - minY), length = (int)(maxZ - minZ);
        int[] states = new int[volume(width, height, length)]; Arrays.fill(states, -1);
        List<SchematicData.State> palette = new ArrayList<>(); Map<SchematicData.State, Integer> ids = new LinkedHashMap<>();
        for (Region region : regions) {
            int[] remap = new int[region.palette.size()];
            for (int i = 0; i < remap.length; i++) {
                SchematicData.State state = region.palette.get(i);
                Integer id = ids.get(state);
                if (id == null) { id = palette.size(); paletteSize(id + 1); ids.put(state, id); palette.add(state); }
                remap[i] = id;
            }
            for (int y = 0; y < region.height; y++) for (int z = 0; z < region.length; z++) for (int x = 0; x < region.width; x++) {
                int src = x + region.width * (z + region.length * y);
                int target = region.x - minX + x + width * (region.z - minZ + z + length * (region.y - minY + y));
                int id = remap[region.states[src]];
                if (states[target] >= 0 && states[target] != id) throw new IllegalArgumentException("Overlapping Litematica regions disagree at the same block");
                states[target] = id;
            }
        }
        return new SchematicData(name, "Litematica v" + version, hash, width, height, length, minX, minY, minZ,
                optionalInteger(root, "MinecraftDataVersion", -1), palette, states, unique(warnings));
    }
    private static int regionMinimum(int position, int signedSize) {
        long lower = (long)position + (signedSize < 0 ? signedSize + 1L : 0);
        if (Math.abs(lower) > 30_000_000L || Math.abs((long)position) > 30_000_000L) throw new IllegalArgumentException("Region offset exceeds supported coordinate bounds");
        return (int)lower;
    }
    private static SchematicData sponge(String name, String hash, Map<String, Object> root) {
        if (root.containsKey("Schematic")) root = compound(root, "Schematic");
        int version = integer(root, "Version");
        if (version < 1 || version > 3) throw new IllegalArgumentException("Supported Sponge schematic versions are 1–3");
        int width = unsignedShort(root, "Width"), height = unsignedShort(root, "Height"), length = unsignedShort(root, "Length"), count = volume(width, height, length);
        Map<String, Object> blocks = version == 3 ? compound(root, "Blocks") : root;
        Map<String, Object> sourcePalette = compound(blocks, "Palette"); paletteSize(sourcePalette.size());
        Map<Integer, SchematicData.State> entries = new TreeMap<>();
        for (var entry : sourcePalette.entrySet()) {
            if (!(entry.getValue() instanceof Integer index) || index < 0 || index >= MAX_PALETTE) throw new IllegalArgumentException("Invalid Sponge palette index");
            if (entries.put(index, parseState(entry.getKey())) != null) throw new IllegalArgumentException("Duplicate Sponge palette index");
        }
        List<SchematicData.State> palette = new ArrayList<>(entries.values()); Map<Integer, Integer> remap = new HashMap<>(); int next = 0;
        for (int id : entries.keySet()) remap.put(id, next++);
        byte[] encoded = bytes(blocks, version == 3 ? "Data" : "BlockData"); int cursor = 0; int[] states = new int[count];
        for (int i = 0; i < count; i++) {
            int value = 0, shift = 0;
            while (true) {
                if (cursor == encoded.length || shift > 28) throw new IllegalArgumentException("Truncated or oversized Sponge palette varint");
                int part = encoded[cursor++] & 255;
                if (shift == 28 && (part & 0xf8) != 0) throw new IllegalArgumentException("Sponge palette varint overflows a positive integer");
                value |= (part & 127) << shift;
                if ((part & 128) == 0) break;
                shift += 7;
            }
            Integer id = remap.get(value);
            if (id == null) throw new IllegalArgumentException("Sponge block data references a missing palette entry"); states[i] = id;
        }
        if (cursor != encoded.length) throw new IllegalArgumentException("Sponge block data has extra palette entries");
        int[] offset = root.containsKey("Offset") ? ints(root, "Offset") : new int[]{0, 0, 0};
        if (offset.length != 3) throw new IllegalArgumentException("Sponge Offset must have exactly three integers");
        for (int value : offset) if (Math.abs((long)value) > 30_000_000) throw new IllegalArgumentException("Sponge offset exceeds coordinate bounds");
        List<String> warnings = new ArrayList<>(); ignoredPayload(root, warnings); if (blocks != root) ignoredPayload(blocks, warnings);
        return new SchematicData(name, "Sponge v" + version, hash, width, height, length, offset[0], offset[1], offset[2], optionalInteger(root, "DataVersion", -1), palette, states, unique(warnings));
    }
    static SchematicData.State parseState(String text) {
        int bracket = text.indexOf('['); String name = bracket < 0 ? text : text.substring(0, bracket); Map<String, String> props = new TreeMap<>();
        if (bracket >= 0) {
            if (!text.endsWith("]") || text.indexOf('[', bracket + 1) >= 0) throw new IllegalArgumentException("Malformed block state: " + text);
            String body = text.substring(bracket + 1, text.length() - 1);
            if (!body.isEmpty()) for (String pair : body.split(",", -1)) {
                String[] split = pair.split("=", -1);
                if (split.length != 2 || props.put(split[0], split[1]) != null) throw new IllegalArgumentException("Malformed block properties: " + text);
            }
        }
        return new SchematicData.State(name, props);
    }
    private static SchematicData legacy(String name, String hash, Map<String, Object> root) {
        if (!string(root, "Materials").equals("Alpha")) throw new IllegalArgumentException("Only legacy Alpha/MCEdit schematic block IDs are supported");
        int width = unsignedShort(root, "Width"), height = unsignedShort(root, "Height"), length = unsignedShort(root, "Length"), count = volume(width, height, length);
        byte[] blocks = bytes(root, "Blocks"), metadata = bytes(root, "Data"), extra = root.containsKey("AddBlocks") ? bytes(root, "AddBlocks") : new byte[0];
        if (blocks.length != count || metadata.length != count || (extra.length != 0 && extra.length != (count + 1) / 2)) throw new IllegalArgumentException("Legacy block arrays do not match schematic dimensions");
        List<SchematicData.State> palette = new ArrayList<>(); Map<SchematicData.State, Integer> ids = new LinkedHashMap<>(); int[] states = new int[count];
        for (int i = 0; i < count; i++) {
            int high = extra.length == 0 ? 0 : ((extra[i / 2] & 255) >>> ((i & 1) * 4)) & 15;
            int id = (blocks[i] & 255) | (high << 8), meta = metadata[i] & 255;
            if (meta > 15) throw new IllegalArgumentException("Legacy block metadata exceeds four bits");
            SchematicData.State state = LegacySchematicBlocks.resolve(id, meta);
            Integer mapped = ids.get(state);
            if (mapped == null) { mapped = palette.size(); paletteSize(mapped + 1); ids.put(state, mapped); palette.add(state); }
            states[i] = mapped;
        }
        List<String> warnings = new ArrayList<>(); warnings.add("Legacy IDs are converted explicitly to supported modern block states; unavailable IDs or metadata stop import."); ignoredPayload(root, warnings);
        int offsetX = optionalInteger(root, "WEOffsetX", 0), offsetY = optionalInteger(root, "WEOffsetY", 0), offsetZ = optionalInteger(root, "WEOffsetZ", 0);
        for (int offset : new int[]{offsetX, offsetY, offsetZ}) if (Math.abs((long)offset) > 30_000_000) throw new IllegalArgumentException("Legacy offset exceeds coordinate bounds");
        return new SchematicData(name, "Legacy MCEdit/Schematica", hash, width, height, length,
                offsetX, offsetY, offsetZ, -1, palette, states, unique(warnings));
    }
    private static List<String> unique(List<String> values) { return List.copyOf(new LinkedHashSet<>(values)); }
    private static void ignoredPayload(Map<String, Object> root, List<String> warnings) {
        for (String name : List.of("Entities", "TileEntities", "BlockEntities", "PendingBlockTicks", "PendingFluidTicks")) {
            if (root.containsKey(name) && (!(root.get(name) instanceof List<?> list) || !list.isEmpty())) warnings.add("Entity, block-entity and scheduled-tick payloads are ignored; only ordinary player-placed blocks are built.");
        }
    }
    private static void paletteSize(int count) { if (count < 1 || count > MAX_PALETTE) throw new IllegalArgumentException("Schematic palette must contain 1–16,384 states"); }
    @SuppressWarnings("unchecked") private static Map<String, Object> map(Object value, String field) { if (!(value instanceof Map<?, ?>)) throw new IllegalArgumentException("Missing or invalid compound: " + field); return (Map<String, Object>)value; }
    private static Map<String, Object> compound(Map<String, Object> root, String field) { return map(root.get(field), field); }
    private static int integer(Map<String, Object> root, String field) { if (!(root.get(field) instanceof Integer value)) throw new IllegalArgumentException("Missing or invalid integer: " + field); return value; }
    private static int optionalInteger(Map<String, Object> root, String field, int fallback) { return root.containsKey(field) ? integer(root, field) : fallback; }
    private static int unsignedShort(Map<String, Object> root, String field) { if (!(root.get(field) instanceof Short value)) throw new IllegalArgumentException("Missing or invalid short: " + field); return value & 65535; }
    private static String string(Map<String, Object> root, String field) { if (!(root.get(field) instanceof String value)) throw new IllegalArgumentException("Missing or invalid string: " + field); return value; }
    private static List<?> list(Map<String, Object> root, String field) { if (!(root.get(field) instanceof List<?> value)) throw new IllegalArgumentException("Missing or invalid list: " + field); return value; }
    private static byte[] bytes(Map<String, Object> root, String field) { if (!(root.get(field) instanceof byte[] value)) throw new IllegalArgumentException("Missing or invalid byte array: " + field); return value; }
    private static int[] ints(Map<String, Object> root, String field) { if (!(root.get(field) instanceof int[] value)) throw new IllegalArgumentException("Missing or invalid integer array: " + field); return value; }
    private static long[] longs(Map<String, Object> root, String field) { if (!(root.get(field) instanceof long[] value)) throw new IllegalArgumentException("Missing or invalid long array: " + field); return value; }
}
