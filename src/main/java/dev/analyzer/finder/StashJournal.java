package dev.analyzer.finder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Local historical observations scoped by an opaque server/world hash, never a server address. */
public final class StashJournal {
    public static final int MAX_ENTRIES = 2048;
    private static final long MAX_BYTES = 8L * 1024 * 1024;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path file;
    private final String scopeHash;
    private final Map<Key, Entry> records = new LinkedHashMap<>();
    private boolean dirty;
    private boolean invalidFile;

    public record Entry(String scopeHash, String dimension, int chunkX, int chunkZ,
                        Map<String, Integer> counts, long firstSeen, long lastSeen) {
        public Entry {
            validateScope(scopeHash);
            if (dimension == null || dimension.length() > 128 || !dimension.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+"))
                throw new IllegalArgumentException("Invalid dimension identifier");
            if (Math.abs((long) chunkX) > 1875000 || Math.abs((long) chunkZ) > 1875000)
                throw new IllegalArgumentException("Chunk outside the supported world border");
            if (firstSeen < 0 || lastSeen < firstSeen) throw new IllegalArgumentException("Invalid observation times");
            if (counts == null || counts.isEmpty() || counts.size() > 64) throw new IllegalArgumentException("Invalid storage counts");
            Map<String, Integer> checked = new TreeMap<>();
            for (var entry : counts.entrySet()) {
                if (ChunkFinderRules.storageGroup(entry.getKey()) == null && !isSpawner(entry.getKey()))
                    throw new IllegalArgumentException("Journal accepts only observed storage/spawner block counts");
                if (entry.getValue() == null || entry.getValue() <= 0 || entry.getValue() > 1000000)
                    throw new IllegalArgumentException("Invalid observed block count");
                checked.put(entry.getKey(), entry.getValue());
            }
            counts = Map.copyOf(checked);
        }

        public int total() { return counts.values().stream().mapToInt(Integer::intValue).sum(); }
    }

    public record LoadResult(List<Entry> entries, String warning) {
        public LoadResult { entries = List.copyOf(entries); warning = warning == null ? "" : warning; }
    }

    private record Key(String dimension, int chunkX, int chunkZ) { }

    public StashJournal(Path file, String scopeHash) {
        this.file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
        validateScope(scopeHash);
        this.scopeHash = scopeHash;
    }

    /** Reading never changes the existing file. Invalid history is retained until explicitly cleared. */
    public synchronized LoadResult load() {
        records.clear();
        dirty = false;
        invalidFile = false;
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return new LoadResult(List.of(), "");
        try {
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > MAX_BYTES)
                throw new IOException("History must be a regular file smaller than 8 MiB");
            JsonElement parsed = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) throw new IOException("History root must be an object");
            JsonObject document = parsed.getAsJsonObject();
            if (integer(document.get("schemaVersion")) != 1 || !scopeHash.equals(string(document.get("scopeHash"))))
                throw new IOException("History version or world scope does not match");
            JsonElement raw = document.get("entries");
            if (raw == null || !raw.isJsonArray() || raw.getAsJsonArray().size() > MAX_ENTRIES)
                throw new IOException("Invalid or oversized history entries");
            for (JsonElement element : raw.getAsJsonArray()) {
                if (!element.isJsonObject()) throw new IOException("Invalid history entry");
                JsonObject object = element.getAsJsonObject();
                JsonElement rawCounts = object.get("counts");
                if (rawCounts == null || !rawCounts.isJsonObject() || rawCounts.getAsJsonObject().size() > 64)
                    throw new IOException("Invalid storage counts");
                Map<String, Integer> counts = new TreeMap<>();
                for (var count : rawCounts.getAsJsonObject().entrySet()) counts.put(count.getKey(), exactInt(count.getValue()));
                Entry entry = new Entry(scopeHash, string(object.get("dimension")), exactInt(object.get("chunkX")),
                        exactInt(object.get("chunkZ")), counts, integer(object.get("firstSeen")), integer(object.get("lastSeen")));
                Key key = new Key(entry.dimension(), entry.chunkX(), entry.chunkZ());
                if (records.putIfAbsent(key, entry) != null) throw new IOException("Duplicate chunk history entry");
            }
            return new LoadResult(entries(), "");
        } catch (IOException | RuntimeException ex) {
            records.clear();
            invalidFile = true;
            return new LoadResult(List.of(), "Stash history could not be read; its file was preserved. " + readable(ex));
        }
    }

    /** Returns true only for a newly observed chunk or changed counts, not a repeated unchanged scan. */
    public synchronized boolean observe(String dimension, FinderData.ChunkFacts facts, long observedAt) {
        Objects.requireNonNull(facts, "facts");
        if (observedAt < 0) throw new IllegalArgumentException("Invalid observation time");
        Map<String, Integer> counts = new TreeMap<>();
        for (var count : facts.counts().entrySet()) {
            if ((ChunkFinderRules.storageGroup(count.getKey()) != null || isSpawner(count.getKey())) && count.getValue() != null && count.getValue() > 0)
                counts.put(count.getKey(), count.getValue());
        }
        if (facts.spawners() > 0 && !counts.containsKey("minecraft:spawner") && !counts.containsKey("minecraft:trial_spawner"))
            counts.put("minecraft:spawner", facts.spawners());
        if (counts.isEmpty()) return false;
        Key key = new Key(dimension, facts.x(), facts.z());
        Entry previous = records.get(key);
        long first = previous == null ? observedAt : previous.firstSeen();
        long last = previous == null ? observedAt : Math.max(observedAt, previous.lastSeen());
        Entry next = new Entry(scopeHash, dimension, facts.x(), facts.z(), counts, first, last);
        boolean changed = previous == null || !previous.counts().equals(next.counts());
        if (!next.equals(previous)) { records.put(key, next); dirty = true; }
        while (records.size() > MAX_ENTRIES) {
            Key oldest = records.entrySet().stream().min(Comparator
                    .comparingLong((Map.Entry<Key, Entry> e) -> e.getValue().lastSeen())
                    .thenComparing(e -> e.getKey().dimension())
                    .thenComparingInt(e -> e.getKey().chunkX())
                    .thenComparingInt(e -> e.getKey().chunkZ())).orElseThrow().getKey();
            records.remove(oldest);
        }
        return changed;
    }

    public synchronized List<Entry> entries() {
        return records.values().stream().sorted(Comparator.comparingLong(Entry::lastSeen).reversed()
                .thenComparing(Entry::dimension).thenComparingInt(Entry::chunkX).thenComparingInt(Entry::chunkZ)).toList();
    }

    public synchronized boolean dirty() { return dirty; }

    /** Only explicit history clearing permits replacing a malformed preserved journal. */
    public synchronized void clear() { records.clear(); invalidFile = false; dirty = true; }

    public synchronized void save() throws IOException {
        if (invalidFile) throw new IOException("Invalid stash history was preserved; clear its history explicitly before replacing it");
        if (Files.exists(file, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Stash history destination is not a regular file");
        JsonObject document = new JsonObject();
        document.addProperty("schemaVersion", 1);
        document.addProperty("scopeHash", scopeHash);
        JsonArray array = new JsonArray();
        for (Entry entry : entries()) {
            JsonObject object = new JsonObject();
            object.addProperty("dimension", entry.dimension());
            object.addProperty("chunkX", entry.chunkX());
            object.addProperty("chunkZ", entry.chunkZ());
            object.addProperty("firstSeen", entry.firstSeen());
            object.addProperty("lastSeen", entry.lastSeen());
            JsonObject counts = new JsonObject();
            for (var count : new TreeMap<>(entry.counts()).entrySet()) counts.addProperty(count.getKey(), count.getValue());
            object.add("counts", counts);
            array.add(object);
        }
        document.add("entries", array);
        byte[] bytes = (GSON.toJson(document) + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) throw new IOException("Stash history exceeds the 8 MiB limit");
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), ".stash-", ".tmp");
        try {
            Files.write(temporary, bytes);
            try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
            dirty = false;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void validateScope(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("World scope must be a SHA-256 hash");
    }

    private static boolean isSpawner(String id) { return "minecraft:spawner".equals(id) || "minecraft:trial_spawner".equals(id); }

    private static String string(JsonElement element) throws IOException {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) throw new IOException("Invalid history text");
        return element.getAsString();
    }

    private static long integer(JsonElement element) throws IOException {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) throw new IOException("Invalid history number");
        try { return element.getAsBigDecimal().longValueExact(); }
        catch (ArithmeticException | NumberFormatException ex) { throw new IOException("History number must be an exact integer", ex); }
    }

    private static int exactInt(JsonElement element) throws IOException {
        long value = integer(element);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) throw new IOException("History number is out of range");
        return (int) value;
    }

    private static String readable(Exception ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? ex.getClass().getSimpleName() : message.substring(0, Math.min(180, message.length()));
    }
}
