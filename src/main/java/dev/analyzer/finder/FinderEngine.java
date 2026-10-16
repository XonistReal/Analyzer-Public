package dev.analyzer.finder;

import dev.analyzer.AnalyzerClient;
import dev.analyzer.core.AnalyzerCore;
import dev.analyzer.core.BlockFinderModules;
import dev.analyzer.core.EspSettings;
import dev.analyzer.core.ModuleDefinition;
import dev.analyzer.finder.FinderData.Cell;
import dev.analyzer.finder.FinderData.ChunkFacts;
import dev.analyzer.finder.FinderData.Mark;
import dev.analyzer.render.FinderOverlay;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Main-thread, read-only surveys of chunks actually received by the client. No requests load chunks. */
public final class FinderEngine {
    private static final MinecraftClient MC = MinecraftClient.getInstance();
    private static final List<String> BLOCK_IDS = BlockFinderModules.definitions().stream().map(ModuleDefinition::id).toList();
    private static final List<String> SURVEY_IDS = List.of("advanced-stash-finder", "chunk-finder", "kelp-esp");
    private static final List<String> IDS;
    private static final int MAX_LOOKUPS = 16_384, MAX_MARKERS = 512, MAX_CHUNKS = 1_225;
    private static final Map<String, LinkedHashMap<String, Observed>> RESULTS = new LinkedHashMap<>();
    private static final IdentityHashMap<BlockState, Cell> PALETTE = new IdentityHashMap<>();
    private static final ArrayDeque<ChunkPos> QUEUE = new ArrayDeque<>(), FAST_QUEUE = new ArrayDeque<>(), PRIORITY = new ArrayDeque<>();
    private static final ArrayDeque<ChunkPos> FAST_PRIORITY = new ArrayDeque<>();
    private static final Set<Long> PRIORITY_KEYS = new HashSet<>(), FAST_PRIORITY_KEYS = new HashSet<>();
    private static final LinkedHashMap<Long, Long> DIRTY = new LinkedHashMap<>(), REVISIONS = new LinkedHashMap<>();
    private static final LinkedHashMap<Long, Long> FAST_REVISIONS = new LinkedHashMap<>();
    private static final Map<Long, Integer> DIRTY_FLAGS = new HashMap<>();
    private static final Set<Long> AGGREGATE_AFFECTED = new HashSet<>(), FAST_AFFECTED = new HashSet<>();
    private static final Map<Long, Integer> AFFECTED = new HashMap<>();
    private static final LinkedHashSet<Long> SEEN_CHUNKS = new LinkedHashSet<>();
    private static final Set<Long> NATIVE_FACT_LOGGED = new HashSet<>();
    private static final LinkedHashSet<String> NOTICED = new LinkedHashSet<>();
    private static AnalyzerCore core;
    private static ClientWorld world;
    private static Settings settings = new Settings(Map.of());
    private static ChunkPos center;
    private static ScanJob scan;
    private static FastJob fast;
    private static StashJournal journal;
    private static String journalWarning = "", runtimeWarning = "";
    private static boolean registered, protectedJournal, loggedFailure, lastPriority, lastFastPriority, invalidateAll;
    private static long ticks, lastQueue, lastFastQueue, nextNotice, revision;
    private static int scannedChunks, fastChunks;
    private static String lastKelpFacts = "";

    static {
        List<String> ids = new ArrayList<>(BLOCK_IDS);
        ids.addAll(SURVEY_IDS); ids.add("collectible-esp");
        IDS = List.copyOf(ids);
        for (String id : IDS) RESULTS.put(id, new LinkedHashMap<>());
    }

    private FinderEngine() { }

    public static void register(AnalyzerCore analyzer) {
        core = analyzer;
        if (registered) return;
        registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
        ClientChunkEvents.CHUNK_UNLOAD.register((clientWorld, chunk) -> {
            if (clientWorld != world) return;
            long key = chunk.getPos().toLong();
            for (String id : IDS) removeModuleChunk(id, key);
            if (scan != null && scan.position.toLong() == key) scan = null;
            if (fast != null && fast.position.toLong() == key) fast = null;
            SEEN_CHUNKS.remove(key); REVISIONS.remove(key); FAST_REVISIONS.remove(key);
            changedChunk(key, 3);
        });
        ClientChunkEvents.CHUNK_LOAD.register((clientWorld, chunk) -> {
            if (clientWorld == world && SEEN_CHUNKS.contains(chunk.getPos().toLong())) changedChunk(chunk.getPos().toLong(), 3);
        });
        WorldRenderEvents.LAST.register(context -> render(context, false));
        WorldRenderEvents.END.register(context -> render(context, true));
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> { saveJournal(); clearRuntime(); });
    }

    public static int count(String module) { return RESULTS.containsKey(module) ? (int) RESULTS.get(module).values().stream().filter(FinderEngine::current).count() : 0; }
    public static List<Mark> marks(String module) {
        var cache = RESULTS.get(module);
        return cache == null ? List.of() : cache.values().stream().filter(FinderEngine::current).map(Observed::mark).toList();
    }
    public static List<StashJournal.Entry> history() { return journal == null ? List.of() : journal.entries(); }

    /** Invoked only by the explicit history UI action; this also permits replacing a corrupt journal. */
    public static void clearHistory() {
        if (journal == null) { journalWarning = "Join a world to clear that world's local history."; return; }
        journal.clear(); protectedJournal = false; journalWarning = "";
        saveJournal();
    }

    public static String status(String module) {
        if (world == null || MC.player == null) return "Join a world to survey received data.";
        String state = settings.enabled(module) ? count(module) + " current markers; "
                + (module.equals("advanced-stash-finder") || module.equals("collectible-esp") ? fastChunks : scannedChunks)
                + " received chunks checked" : "Disabled";
        if (module.equals("advanced-stash-finder") && !journalWarning.isBlank()) state += "; " + journalWarning;
        if (settings.enabled(module) && !runtimeWarning.isBlank()) state += "; " + runtimeWarning;
        if (module.equals("kelp-esp") && !lastKelpFacts.isBlank()) state += "; " + lastKelpFacts;
        return state;
    }

    /** Native block-packet hook. Coalesce changes by chunk; no scans or marker walks occur here. */
    public static void blockChanged(BlockPos position) {
        blockChanged(position, null, null);
    }

    /** State-only animation and growth packets refresh block markers without cancelling stable counts. */
    public static void blockChanged(BlockPos position, BlockState before, BlockState after) {
        if (position == null || world == null || world != MC.world || MC.player == null) return;
        if (!MC.isOnThread()) { BlockPos copy = position.toImmutable(); MC.execute(() -> blockChanged(copy, before, after)); return; }
        // Even an acknowledgement matching client prediction can retire an older block marker.
        // Its stable IDs still preserve count aggregates under the classification below.
        ChunkPos chunk = new ChunkPos(position);
        if (!withinChunk(chunk, maximumRange() + 32)) return;
        int flags = 3;
        if (before != null && after != null) {
            Cell previous = policyCell(before, position), current = policyCell(after, position);
            var change = FinderUpdatePolicy.classify(previous.id(), current.id(), previous.property("axis"), current.property("axis"));
            flags = ((settings.enabled("chunk-finder") && change.chunkEvidence()
                    || settings.enabled("kelp-esp") && change.kelpColumns()) ? 1 : 0) | (change.fastSurvey() ? 2 : 0);
            if (flags == 0 && !before.equals(after) && !FinderUpdatePolicy.blockMarkersChanged(previous, current)) return;
        }
        changedChunk(chunk.toLong(), flags);
    }

    private static Cell policyCell(BlockState state, BlockPos position) {
        Cell template = palette(state);
        return new Cell(template.id(), true, template.air(), state.isFullCube(world, position), template.fluid(),
                0, template.luminance(), template.properties());
    }

    private static void changedChunk(long key, int flags) {
        if (invalidateAll) return;
        if (DIRTY.size() >= 1024 && !DIRTY.containsKey(key)) { invalidateAll = true; return; }
        long next = ++revision;
        if ((flags & 1) != 0) { REVISIONS.put(key, next); AGGREGATE_AFFECTED.add(key); }
        if ((flags & 2) != 0) { FAST_REVISIONS.put(key, next); FAST_AFFECTED.add(key); }
        while (REVISIONS.size() > 2048) REVISIONS.remove(REVISIONS.keySet().iterator().next());
        while (FAST_REVISIONS.size() > 2048) FAST_REVISIONS.remove(FAST_REVISIONS.keySet().iterator().next());
        DIRTY_FLAGS.merge(key, flags, (previous, current) -> previous | current);
        if (DIRTY.put(key, next) == null) forNeighbors(key, affected -> AFFECTED.merge(affected, 1, Integer::sum));
    }

    private static boolean current(Observed observed) {
        if (invalidateAll) return false;
        return switch (observed.mark().module()) {
            case "advanced-stash-finder", "collectible-esp" -> !FAST_AFFECTED.contains(observed.chunk());
            case "chunk-finder", "kelp-esp" -> !AGGREGATE_AFFECTED.contains(observed.chunk());
            default -> !AFFECTED.containsKey(observed.chunk());
        };
    }

    private static void processChanges() {
        if (invalidateAll) {
            clearResults(); DIRTY.clear(); DIRTY_FLAGS.clear(); AFFECTED.clear(); AGGREGATE_AFFECTED.clear(); FAST_AFFECTED.clear();
            REVISIONS.clear(); FAST_REVISIONS.clear(); invalidateAll = false; center = null; return;
        }
        if (DIRTY.isEmpty()) return;
        Set<Long> affected = new HashSet<>();
        List<Long> batch = new ArrayList<>();
        Set<Long> aggregate = new HashSet<>(), fastChanges = new HashSet<>();
        Iterator<Long> pending = DIRTY.keySet().iterator();
        while (pending.hasNext() && batch.size() < 8) {
            long key = pending.next(); batch.add(key); pending.remove();
            forNeighbors(key, affected::add);
            int flags = DIRTY_FLAGS.getOrDefault(key, 3); DIRTY_FLAGS.remove(key);
            if ((flags & 1) != 0) aggregate.add(key);
            if ((flags & 2) != 0) { fastChanges.add(key); offerPriority(new ChunkPos(key), true); }
        }
        for (String id : BLOCK_IDS) RESULTS.get(id).values().removeIf(observed -> affected.contains(observed.chunk()));
        for (String id : List.of("chunk-finder", "kelp-esp")) RESULTS.get(id).values().removeIf(observed -> aggregate.contains(observed.chunk()));
        for (String id : List.of("advanced-stash-finder", "collectible-esp")) RESULTS.get(id).values().removeIf(observed -> fastChanges.contains(observed.chunk()));
        if (scan != null && scan.revision != REVISIONS.getOrDefault(scan.position.toLong(), 0L)) scan = null;
        if (fast != null && fast.revision != FAST_REVISIONS.getOrDefault(fast.position.toLong(), 0L)) fast = null;
        for (long key : affected) offerPriority(new ChunkPos(key), false);
        for (long key : batch) forNeighbors(key, neighbor -> {
            Integer references = AFFECTED.get(neighbor);
            if (references == null || references <= 1) AFFECTED.remove(neighbor); else AFFECTED.put(neighbor, references - 1);
        });
        AGGREGATE_AFFECTED.removeAll(aggregate); FAST_AFFECTED.removeAll(fastChanges);
    }

    private static void forNeighbors(long key, java.util.function.LongConsumer action) {
        int cx = (int) key, cz = (int) (key >> 32);
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) action.accept(ChunkPos.toLong(cx + dx, cz + dz));
    }

    private static void tick() {
        ticks++;
        if (MC.world != world) {
            saveJournal(); clearRuntime(); world = MC.world;
            journal = null; journalWarning = ""; protectedJournal = false;
            if (world != null) openJournal();
        }
        if (world == null || MC.player == null || core == null) return;
        try {
            Settings next = readSettings();
            if (!next.equals(settings)) { clearResults(); settings = next; center = null; }
            processChanges();
            int budget = settings.modules().values().stream().filter(Config::enabled)
                    .mapToInt(value -> (int) value.number("scan-budget")).max().orElse(0);
            if (budget > 0) {
                WorldGrid grid = new WorldGrid(Math.clamp(budget, 512, MAX_LOOKUPS));
                refreshQueues();
                if (ticks % 10 == 0) prune(grid);
                // At the smallest selected budget, reserve periodic complete ticks for a neighbor-heavy
                // predicate instead of indefinitely retrying it behind the fast container sweep.
                if (ticks % 4 != 0) fastSurvey(grid, Math.min(4096, Math.max(64, budget / 4)));
                survey(grid);
            }
            if (ticks % 100 == 0) saveJournal();
        } catch (BudgetExceeded ignored) {
            // The current cell and module are retained; another tick receives a fresh finite budget.
        } catch (RuntimeException failure) {
            if (!loggedFailure) { loggedFailure = true; AnalyzerClient.LOGGER.warn("Finder survey paused after a local observation failure", failure); }
            runtimeWarning = "An observation failed; the bounded survey will retry.";
            scan = null; fast = null;
        }
    }

    private static Settings readSettings() {
        Map<String, Config> modules = new LinkedHashMap<>();
        for (String id : IDS) {
            boolean enabled = core.isEnabled(id);
            if (id.equals("collectible-esp")) enabled &= core.getBoolean(id, "banners");
            modules.put(id, new Config(enabled, core.values(id)));
        }
        return new Settings(Map.copyOf(modules));
    }

    private static void clearResults() {
        RESULTS.values().forEach(Map::clear); QUEUE.clear(); FAST_QUEUE.clear(); PRIORITY.clear(); FAST_PRIORITY.clear();
        PRIORITY_KEYS.clear(); FAST_PRIORITY_KEYS.clear(); lastPriority = lastFastPriority = false;
        scan = null; fast = null; NOTICED.clear(); scannedChunks = fastChunks = 0;
        lastQueue = lastFastQueue = -1000;
    }

    private static void clearRuntime() {
        clearResults(); PALETTE.clear(); settings = new Settings(Map.of()); center = null;
        DIRTY.clear(); DIRTY_FLAGS.clear(); AFFECTED.clear(); AGGREGATE_AFFECTED.clear(); FAST_AFFECTED.clear();
        REVISIONS.clear(); FAST_REVISIONS.clear(); SEEN_CHUNKS.clear(); invalidateAll = false; lastKelpFacts = "";
        NATIVE_FACT_LOGGED.clear();
        loggedFailure = false; runtimeWarning = "";
    }

    private static void refreshQueues() {
        ChunkPos current = MC.player.getChunkPos();
        boolean moved = !current.equals(center);
        List<ChunkPos> nearby = null;
        if (moved || QUEUE.isEmpty() && scan == null && ticks - lastQueue >= 100
                || FAST_QUEUE.isEmpty() && fast == null && ticks - lastFastQueue >= 40 || ticks % 200 == 0)
            nearby = nearbyChunks(current);
        if (moved) {
            center = current; QUEUE.clear(); FAST_QUEUE.clear(); PRIORITY.clear(); FAST_PRIORITY.clear(); PRIORITY_KEYS.clear(); FAST_PRIORITY_KEYS.clear();
            if (scan != null && !withinChunk(scan.position, maximumRange())) scan = null;
            if (fast != null && !withinChunk(fast.position, maximumRange())) fast = null;
        }
        if (nearby == null) return;
        if (moved || QUEUE.isEmpty() && scan == null && ticks - lastQueue >= 100) {
            QUEUE.addAll(nearby); lastQueue = ticks;
        }
        if (moved || FAST_QUEUE.isEmpty() && fast == null && ticks - lastFastQueue >= 40) {
            FAST_QUEUE.addAll(nearby); lastFastQueue = ticks;
        }
        if (ticks % 200 == 0 && PRIORITY.isEmpty()) nearby.stream().limit(4).forEach(position -> offerPriority(position, false));
    }

    private static void offerPriority(ChunkPos position, boolean fast) {
        var queue = fast ? FAST_PRIORITY : PRIORITY;
        var keys = fast ? FAST_PRIORITY_KEYS : PRIORITY_KEYS;
        if (queue.size() < 128 && withinChunk(position, maximumRange()) && keys.add(position.toLong())) queue.addLast(position);
    }

    /** Every expedited job yields the next selection to the ordinary queue while it has work. */
    private static ChunkPos pollWork(boolean fast) {
        var ordinary = fast ? FAST_QUEUE : QUEUE;
        var expedited = fast ? FAST_PRIORITY : PRIORITY;
        var keys = fast ? FAST_PRIORITY_KEYS : PRIORITY_KEYS;
        boolean prior = fast ? lastFastPriority : lastPriority;
        boolean priority = !expedited.isEmpty() && (!prior || ordinary.isEmpty());
        ChunkPos position = priority ? expedited.removeFirst() : ordinary.pollFirst();
        if (priority) keys.remove(position.toLong());
        if (fast) lastFastPriority = priority; else lastPriority = priority;
        return position;
    }

    private static List<ChunkPos> nearbyChunks(ChunkPos current) {
        double range = maximumRange();
        int radius = Math.min(17, (int) Math.ceil(range / 16) + 1);
        List<ChunkPos> chunks = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            ChunkPos position = new ChunkPos(current.x + dx, current.z + dz);
            if (withinChunk(position, range)) chunks.add(position);
        }
        chunks.sort(Comparator.comparingDouble(position -> chunkDistance(position, MC.player.getPos())));
        return chunks.stream().limit(MAX_CHUNKS).toList();
    }

    private static double maximumRange() {
        return settings.modules().values().stream().filter(Config::enabled).mapToDouble(value -> value.number("range")).max().orElse(16);
    }

    private static boolean needsFast() { return settings.enabled("advanced-stash-finder") || settings.enabled("collectible-esp"); }
    private static boolean needsFull() {
        return settings.enabled("chunk-finder") || settings.enabled("kelp-esp") || BLOCK_IDS.stream().anyMatch(settings::enabled);
    }

    /** Block entities are surveyed independently, so a long terrain scan cannot delay nearby stashes. */
    private static void fastSurvey(WorldGrid grid, int allowance) {
        if (!needsFast()) { fast = null; FAST_QUEUE.clear(); FAST_PRIORITY.clear(); FAST_PRIORITY_KEYS.clear(); return; }
        int before = grid.used, transitions = 0;
        while (grid.used - before < allowance && transitions < 64) {
            if (fast == null) {
                ChunkPos position = pollWork(true);
                if (position == null) return;
                transitions++;
                WorldChunk chunk = grid.chunk(position.x, position.z);
                if (chunk == null) continue;
                fast = new FastJob(position, chunk);
            }
            if (grid.chunk(fast.position.x, fast.position.z) != fast.chunk
                    || fast.revision != FAST_REVISIONS.getOrDefault(fast.position.toLong(), 0L)) { removeChunk(fast.position.toLong(), true); fast = null; continue; }
            try {
                if (fast.pending == null) {
                    if (!fast.positions.hasNext()) { completeFast(fast); fast = null; fastChunks++; continue; }
                    fast.pending = fast.positions.next().toImmutable();
                }
                BlockPos pos = fast.pending;
                Cell cell = grid.cell(pos.getX(), pos.getY(), pos.getZ());
                if (ChunkFinderRules.storageGroup(cell.id()) != null) {
                    fast.counts.merge(cell.id(), 1, Integer::sum); fast.observeHeight(pos.getY());
                }
                if (cell.is("spawner") || cell.is("trial_spawner")) {
                    fast.spawners++;
                    fast.counts.merge(cell.id(), 1, Integer::sum);
                    fast.observeHeight(pos.getY());
                }
                if (settings.enabled("collectible-esp") && banner(cell.id()) && inRange("collectible-esp", pos.getX(), pos.getY(), pos.getZ()))
                    store(new Mark("collectible-esp", pos.getX(), pos.getY(), pos.getZ(), "Received placed banner"));
                fast.pending = null;
                if (++fast.observations > 65_536) {
                    // A chunk cannot contain more than its finite block positions; malformed observations are withheld.
                    fast = null;
                }
            } catch (ConcurrentModificationException failure) {
                FAST_QUEUE.addFirst(fast.position); fast = null; return;
            }
        }
    }

    private static void completeFast(FastJob completed) {
        int bottom = completed.minY == Integer.MAX_VALUE ? world.getBottomY() : completed.minY;
        int top = completed.maxY == Integer.MIN_VALUE ? bottom + 1 : completed.maxY + 1;
        ChunkFacts facts = new ChunkFacts(completed.position.x, completed.position.z, bottom,
                top, Map.copyOf(completed.counts),
                completed.counts.entrySet().stream().filter(entry -> ChunkFinderRules.storageGroup(entry.getKey()) != null)
                        .mapToInt(Map.Entry::getValue).sum(), completed.spawners, 0, 0, 0, 0, 0, 0, List.of());
        removeModuleChunk("advanced-stash-finder", completed.position.toLong());
        if (!settings.enabled("advanced-stash-finder") || !withinChunk(completed.position, settings.number("advanced-stash-finder", "range"))) return;
        List<Mark> found = ChunkFinderRules.inspect("advanced-stash-finder", facts, settings);
        found.forEach(FinderEngine::store);
        if (found.stream().anyMatch(mark -> markInRange(mark, settings.number("advanced-stash-finder", "range")))
                && journal != null && settings.bool("advanced-stash-finder", "remember-stashes"))
            journal.observe(dimension(), facts, System.currentTimeMillis());
    }

    private static void survey(WorldGrid grid) {
        if (!needsFull()) { scan = null; QUEUE.clear(); PRIORITY.clear(); PRIORITY_KEYS.clear(); return; }
        int transitions = 0;
        while (grid.used < grid.limit && transitions < 64) {
            if (scan == null) {
                ChunkPos position = pollWork(false);
                if (position == null) return;
                WorldChunk chunk = grid.chunk(position.x, position.z); transitions++;
                if (chunk == null || !withinChunk(position, maximumRange())) continue;
                scan = new ScanJob(position, chunk);
                removeChunk(position.toLong(), false);
            }
            if (grid.chunk(scan.position.x, scan.position.z) != scan.chunk
                    || scan.revision != REVISIONS.getOrDefault(scan.position.toLong(), 0L)) { removeChunk(scan.position.toLong(), false); scan = null; continue; }
            if (!scan.prepareSection()) { completeScan(scan); scan = null; scannedChunks++; transitions++; continue; }
            if (scan.skipSection) { scan.advanceSection(); transitions++; continue; }
            int local = scan.cellIndex;
            int x = scan.position.getStartX() + (local & 15), z = scan.position.getStartZ() + ((local >> 4) & 15);
            int y = scan.sectionY + scan.yOrder[(local >> 8) & 15];
            if (!scan.counted) {
                Cell cell = grid.cell(x, y, z);
                int kelpLength = 0;
                if (settings.enabled("kelp-esp") && cell.is("kelp")) {
                    kelpLength = 1;
                    while (kelpLength < 32) {
                        Cell below = grid.cell(x, y - kelpLength, z);
                        if (!below.is("kelp_plant") && !below.is("kelp")) break;
                        kelpLength++;
                    }
                }
                if (scan.fullFacts && !cell.air() && cell.loaded()) {
                    scan.counts.merge(cell.id(), 1, Integer::sum);
                    if (ChunkFinderRules.relevantEvidence(cell.id())
                            && (!cell.is("deepslate") || y >= 8 || !cell.property("axis").equals("y"))
                            && (!cell.is("end_stone") || !dimension().equals("minecraft:the_end")))
                        scan.observeEvidenceHeight(y);
                    if (cell.is("deepslate") && y >= 8) scan.counts.merge("analyzer:unusual_deepslate", 1, Integer::sum);
                    if (cell.is("deepslate") && !cell.property("axis").isBlank() && !cell.property("axis").equals("y"))
                        scan.counts.merge("analyzer:rotated_deepslate", 1, Integer::sum);
                    if (cell.is("end_stone") && !dimension().equals("minecraft:the_end")) scan.counts.merge("analyzer:end_stone_outside_end", 1, Integer::sum);
                    if (kelpLength > 0) {
                        int column = (x & 15) | ((z & 15) << 4);
                        KelpTop previous = scan.kelpTops.get(column);
                        if (previous == null || y > previous.y()) scan.kelpTops.put(column, new KelpTop(y, kelpLength));
                    }
                }
                scan.cell = cell; scan.counted = true;
            }
            while (scan.moduleIndex < scan.modules.size()) {
                String id = scan.modules.get(scan.moduleIndex);
                if (BlockFinderRules.candidate(id, scan.cell) && inRange(id, x, y, z))
                    BlockFinderRules.inspect(id, x, y, z, grid, settings).forEach(FinderEngine::store);
                scan.moduleIndex++;
            }
            scan.counted = false; scan.moduleIndex = 0; scan.cell = null;
            if (++scan.cellIndex == 4096) { scan.advanceSection(); transitions++; }
        }
    }

    private static void completeScan(ScanJob completed) {
        if (!completed.fullFacts) return;
        int storage = 0, spawners = 0, amethyst = 0, budding = 0;
        for (var entry : completed.counts.entrySet()) {
            if (ChunkFinderRules.storageGroup(entry.getKey()) != null) storage += entry.getValue();
            if (entry.getKey().equals("minecraft:spawner") || entry.getKey().equals("minecraft:trial_spawner")) spawners += entry.getValue();
            if (entry.getKey().equals("minecraft:amethyst_block")) amethyst += entry.getValue();
            if (entry.getKey().equals("minecraft:budding_amethyst")) budding += entry.getValue();
        }
        int tall = 0, matching = 0;
        for (KelpTop top : completed.kelpTops.values()) {
            if (top.length() >= settings.number("kelp-esp", "min-length")) {
                tall++;
                if (top.y() == (int) settings.number("kelp-esp", "top-y")) matching++;
            }
        }
        if (settings.enabled("kelp-esp") && completed.kelpTops.size() > 0)
            lastKelpFacts = "Completed chunk " + completed.position.x + ", " + completed.position.z + ": "
                    + completed.kelpTops.size() + " kelp columns, " + tall + " tall, " + matching + " matching top Y";
        ChunkFacts facts = new ChunkFacts(completed.position.x, completed.position.z, world.getBottomY(),
                world.getTopYInclusive() + 1, Map.copyOf(completed.counts), storage, spawners, amethyst, budding,
                0, completed.kelpTops.size(), tall, matching, List.of());
        if (FabricLoader.getInstance().isDevelopmentEnvironment() && Boolean.getBoolean("analyzer.findersSmoke")
                && NATIVE_FACT_LOGGED.size() < 64 && NATIVE_FACT_LOGGED.add(completed.position.toLong()))
            AnalyzerClient.LOGGER.info("Finder completed native chunk {}, {}: storage={}, kelp={}/{}/{}, authored={}, raw kelp tips={}, chunk enabled={}, kelp enabled={}",
                    completed.position.x, completed.position.z, storage, completed.kelpTops.size(), tall, matching,
                    completed.counts.getOrDefault("minecraft:polished_deepslate", 0), completed.counts.getOrDefault("minecraft:kelp", 0),
                    settings.enabled("chunk-finder"), settings.enabled("kelp-esp"));
        for (String id : List.of("chunk-finder", "kelp-esp")) if (settings.enabled(id)) {
            removeModuleChunk(id, completed.position.toLong());
            for (Mark mark : ChunkFinderRules.inspect(id, facts, settings)) {
                if (id.equals("chunk-finder")) {
                    int height = completed.evidenceY == Integer.MIN_VALUE ? MC.player.getBlockY() : completed.evidenceY;
                    store(new Mark(id, mark.x(), height, mark.z(), mark.width(), .05, mark.depth(), mark.evidence(), mark.color()));
                } else store(mark);
            }
        }
    }

    private static void store(Mark mark) {
        Config config = settings.modules().get(mark.module());
        Observed observed = new Observed(mark, ChunkPos.toLong(mark.x() >> 4, mark.z() >> 4), ticks);
        if (config == null || !config.enabled() || !markInRange(mark, config.number("range")) || !current(observed)) return;
        var cache = RESULTS.get(mark.module());
        if (cache == null) return;
        String key = mark.x() + "/" + mark.y() + "/" + mark.z() + "/" + mark.width() + "/" + mark.height() + "/" + mark.depth();
        cache.put(key, observed);
        int maximum = Math.clamp((int) config.number("max-markers"), 1, MAX_MARKERS);
        while (cache.size() > maximum) {
            String farthest = cache.entrySet().stream().max(Comparator.comparingDouble(entry -> markDistance(entry.getValue().mark()))).orElseThrow().getKey();
            cache.remove(farthest);
        }
        if (settings.bool(mark.module(), "notify") && ticks >= nextNotice) {
            String notice = mark.module() + "/" + key;
            if (NOTICED.add(notice)) {
                nextNotice = ticks + 20;
                while (NOTICED.size() > 2048) NOTICED.remove(NOTICED.iterator().next());
                MC.player.sendMessage(Text.literal(core.definition(mark.module()).name() + ": " + mark.evidence()
                        + " at " + mark.x() + ", " + mark.y() + ", " + mark.z()), false);
            }
        }
    }

    private static void prune(WorldGrid grid) {
        for (String id : IDS) {
            Config config = settings.modules().get(id);
            RESULTS.get(id).values().removeIf(value -> !config.enabled() || !markInRange(value.mark(), config.number("range"))
                    || grid.chunk((int) value.chunk(), (int) (value.chunk() >> 32)) == null);
        }
    }

    private static void removeChunk(long chunk, boolean onlyFast) {
        if (onlyFast) { removeModuleChunk("advanced-stash-finder", chunk); removeModuleChunk("collectible-esp", chunk); return; }
        for (String id : BLOCK_IDS) removeModuleChunk(id, chunk);
    }
    private static void removeModuleChunk(String id, long chunk) { RESULTS.get(id).values().removeIf(value -> value.chunk() == chunk); }

    private static void render(WorldRenderContext context, boolean throughWalls) {
        if (world == null || world != MC.world || MC.player == null || core == null) return;
        for (String id : IDS) {
            // Live enable/pass checks stop the overlay immediately when a menu toggle or profile changes.
            if (!core.isEnabled(id) || core.getBoolean(id, "through-walls") != throughWalls) continue;
            int rgb = EspSettings.color(core.getChoice(id, "marker-color"), core.getNumber(id, "marker-red"),
                    core.getNumber(id, "marker-green"), core.getNumber(id, "marker-blue"));
            int alpha = (int) Math.round(255 * core.getNumber(id, "opacity") / 100);
            boolean tracers = core.getBoolean(id, "tracers");
            List<FinderOverlay.Shape> shapes = new ArrayList<>();
            for (Observed value : RESULTS.get(id).values()) {
                if (!current(value)) continue;
                Mark mark = value.mark();
                if (!markInRange(mark, core.getNumber(id, "range"))) continue;
                int color = alpha << 24 | ((mark.color() == 0 ? rgb : mark.color()) & 0xffffff);
                shapes.add(new FinderOverlay.Shape(new Box(mark.x(), mark.y(), mark.z(), mark.x() + mark.width(),
                        mark.y() + mark.height(), mark.z() + mark.depth()).expand(.002), color, tracers));
            }
            FinderOverlay.draw(context, shapes, throughWalls, (float) core.getNumber(id, "line-width"),
                    core.getChoice(id, "tracer-origin"), id);
        }
    }

    private static boolean banner(String id) { return id.startsWith("minecraft:") && (id.endsWith("_banner") || id.endsWith("_wall_banner")); }
    private static String dimension() { return world.getRegistryKey().getValue().toString(); }
    private static boolean inRange(String module, int x, int y, int z) {
        double range = settings.number(module, "range");
        return Vec3d.ofCenter(new BlockPos(x, y, z)).squaredDistanceTo(MC.player.getPos()) <= range * range;
    }
    private static double markDistance(Mark mark) {
        Vec3d origin = MC.player.getPos();
        double x = Math.clamp(origin.x, mark.x(), mark.x() + mark.width()) - origin.x;
        double y = Math.clamp(origin.y, mark.y(), mark.y() + mark.height()) - origin.y;
        double z = Math.clamp(origin.z, mark.z(), mark.z() + mark.depth()) - origin.z;
        return x * x + y * y + z * z;
    }
    private static boolean markInRange(Mark mark, double range) { return markDistance(mark) <= range * range; }
    private static boolean withinChunk(ChunkPos chunk, double range) { return chunkDistance(chunk, MC.player.getPos()) <= range * range; }
    private static double chunkDistance(ChunkPos chunk, Vec3d point) {
        double dx = Math.clamp(point.x, chunk.getStartX(), chunk.getStartX() + 16) - point.x;
        double dz = Math.clamp(point.z, chunk.getStartZ(), chunk.getStartZ() + 16) - point.z;
        return dx * dx + dz * dz;
    }

    private static void openJournal() {
        String source = MC.getServer() != null ? "world:" + MC.getServer().getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize()
                : MC.getCurrentServerEntry() != null ? "server:" + MC.getCurrentServerEntry().address.toLowerCase(java.util.Locale.ROOT)
                : MC.getNetworkHandler() != null ? "connection:" + MC.getNetworkHandler().getConnection().getAddress()
                : "session:" + System.identityHashCode(world);
        try {
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8)));
            Path destination = FabricLoader.getInstance().getConfigDir().resolve("analyzer/findings").resolve(hash + ".json");
            journal = new StashJournal(destination, hash);
            var loaded = journal.load(); journalWarning = loaded.warning(); protectedJournal = !journalWarning.isBlank();
            if (protectedJournal) AnalyzerClient.LOGGER.warn(journalWarning);
        } catch (NoSuchAlgorithmException failure) { throw new IllegalStateException("SHA-256 unavailable", failure); }
    }

    private static void saveJournal() {
        if (journal == null || protectedJournal || !journal.dirty()) return;
        try { journal.save(); journalWarning = ""; }
        catch (IOException failure) {
            journalWarning = "Local history could not be saved; existing files were preserved.";
            if (!loggedFailure) { loggedFailure = true; AnalyzerClient.LOGGER.warn(journalWarning, failure); }
        }
    }

    private record Observed(Mark mark, long chunk, long seenTick) { }
    private record KelpTop(int y, int length) { }
    private record Config(boolean enabled, Map<String, Object> values) {
        boolean bool(String key) { return Boolean.TRUE.equals(values.get(key)); }
        double number(String key) { return values.get(key) instanceof Number value ? value.doubleValue() : 0; }
        String choice(String key) { return values.get(key) instanceof String value ? value : ""; }
    }
    private record Settings(Map<String, Config> modules) implements FinderData.Options {
        boolean enabled(String id) { return modules.containsKey(id) && modules.get(id).enabled(); }
        public boolean bool(String module, String setting) { return modules.containsKey(module) && modules.get(module).bool(setting); }
        public double number(String module, String setting) { return modules.containsKey(module) ? modules.get(module).number(setting) : 0; }
        public String choice(String module, String setting) { return modules.containsKey(module) ? modules.get(module).choice(setting) : ""; }
    }

    private static final class FastJob {
        final ChunkPos position;
        final WorldChunk chunk;
        final Iterator<BlockPos> positions;
        final long revision;
        final Map<String, Integer> counts = new HashMap<>();
        int spawners, observations, minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        BlockPos pending;
        FastJob(ChunkPos position, WorldChunk chunk) {
            this.position = position; this.chunk = chunk;
            revision = FAST_REVISIONS.getOrDefault(position.toLong(), 0L); rememberChunk(position.toLong());
            positions = chunk.getBlockEntities().keySet().iterator();
            removeModuleChunk("collectible-esp", position.toLong());
        }
        void observeHeight(int y) { minY = Math.min(minY, y); maxY = Math.max(maxY, y); }
    }

    private static final class ScanJob {
        final ChunkPos position;
        final WorldChunk chunk;
        final boolean fullFacts;
        final long revision;
        final int[] sectionOrder;
        final Map<String, Integer> counts = new HashMap<>();
        final Map<Integer, KelpTop> kelpTops = new HashMap<>();
        int[] yOrder;
        List<String> modules = List.of();
        int sectionIndex, sectionY, cellIndex, moduleIndex, evidenceY = Integer.MIN_VALUE;
        boolean prepared, skipSection, counted;
        Cell cell;
        ScanJob(ChunkPos position, WorldChunk chunk) {
            this.position = position; this.chunk = chunk;
            revision = REVISIONS.getOrDefault(position.toLong(), 0L); rememberChunk(position.toLong());
            fullFacts = settings.enabled("chunk-finder") || settings.enabled("kelp-esp");
            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < chunk.getSectionArray().length; i++) order.add(i);
            order.sort(Comparator.comparingDouble(index -> Math.abs(chunk.getBottomY() + index * 16 + 8 - MC.player.getY())));
            sectionOrder = order.stream().mapToInt(Integer::intValue).toArray();
        }
        boolean prepareSection() {
            if (prepared) return true;
            if (sectionIndex >= sectionOrder.length) return false;
            int index = sectionOrder[sectionIndex];
            sectionY = chunk.getBottomY() + index * 16;
            ChunkSection section = chunk.getSection(index);
            List<String> interested = new ArrayList<>();
            for (String id : BLOCK_IDS) {
                if (!settings.enabled(id) || !withinChunk(position, settings.number(id, "range"))) continue;
                if (sectionY > MC.player.getY() + settings.number(id, "range") || sectionY + 16 < MC.player.getY() - settings.number(id, "range")) continue;
                if ((id.equals("amethyst-finder") || id.equals("deepslate-esp"))
                        && (sectionY > settings.number(id, "max-y") || sectionY + 15 < settings.number(id, "min-y"))) continue;
                boolean allowEmpty = id.equals("bedrock-void-esp") && sectionY <= world.getBottomY()
                        || id.equals("light-esp") && !settings.bool(id, "source-only");
                if (section.isEmpty() && !allowEmpty) continue;
                boolean found = section.hasAny(state -> {
                    Cell palette = palette(state);
                    if (id.equals("light-esp") && settings.bool(id, "source-only"))
                        return palette.luminance() >= settings.number(id, "minimum-light");
                    return BlockFinderRules.candidate(id, palette);
                });
                if (found) interested.add(id);
            }
            modules = List.copyOf(interested);
            skipSection = modules.isEmpty() && (!fullFacts || section.isEmpty());
            List<Integer> ys = new ArrayList<>();
            for (int y = 0; y < 16; y++) ys.add(y);
            ys.sort(Comparator.comparingDouble(y -> Math.abs(sectionY + y + .5 - MC.player.getY())));
            yOrder = ys.stream().mapToInt(Integer::intValue).toArray();
            prepared = true;
            return true;
        }
        void advanceSection() {
            sectionIndex++; prepared = false; cellIndex = moduleIndex = 0; counted = false; cell = null;
        }
        void observeEvidenceHeight(int y) {
            if (evidenceY == Integer.MIN_VALUE || Math.abs(y - MC.player.getY()) < Math.abs(evidenceY - MC.player.getY())) evidenceY = y;
        }
    }

    private static Cell palette(BlockState state) {
        Cell cached = PALETTE.get(state);
        if (cached != null) return cached;
        Map<String, String> properties = new HashMap<>();
        for (var entry : state.getEntries().entrySet()) properties.put(entry.getKey().getName(), property(entry.getKey(), entry.getValue()));
        Cell template = new Cell(Registries.BLOCK.getId(state.getBlock()).toString(), true, state.isAir(),
                !state.isAir() && state.getFluidState().isEmpty(), !state.getFluidState().isEmpty(),
                0, state.getLuminance(), Map.copyOf(properties));
        if (PALETTE.size() >= 4096) PALETTE.clear();
        PALETTE.put(state, template);
        return template;
    }

    private static void rememberChunk(long key) {
        SEEN_CHUNKS.add(key);
        while (SEEN_CHUNKS.size() > 2048) SEEN_CHUNKS.remove(SEEN_CHUNKS.iterator().next());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String property(Property property, Comparable value) { return property.name(value); }

    private static final class WorldGrid implements FinderData.Grid {
        final int limit;
        int used;
        final Map<Long, WorldChunk> chunks = new HashMap<>();
        final Map<Long, Cell> cells = new HashMap<>();
        WorldGrid(int limit) { this.limit = limit; }
        WorldChunk chunk(int x, int z) {
            long key = ChunkPos.toLong(x, z);
            if (!chunks.containsKey(key)) chunks.put(key, world.getChunkManager().getChunk(x, z, ChunkStatus.FULL, false));
            return chunks.get(key);
        }
        public Cell cell(int x, int y, int z) {
            if (used >= limit) throw BudgetExceeded.INSTANCE;
            used++;
            if (y < bottomY() || y >= topY()) return Cell.UNKNOWN;
            long key = BlockPos.asLong(x, y, z);
            Cell cached = cells.get(key);
            if (cached != null) return cached;
            WorldChunk chunk = chunk(x >> 4, z >> 4);
            if (chunk == null) { cells.put(key, Cell.UNKNOWN); return Cell.UNKNOWN; }
            BlockPos position = new BlockPos(x, y, z);
            BlockState state = chunk.getBlockState(position);
            Cell template = palette(state);
            Cell observation = new Cell(template.id(), true, template.air(), state.isFullCube(world, position),
                    template.fluid(), world.getLightLevel(LightType.BLOCK, position), template.luminance(), template.properties());
            cells.put(key, observation);
            return observation;
        }
        public int bottomY() { return world.getBottomY(); }
        public int topY() { return world.getTopYInclusive() + 1; }
    }

    private static final class BudgetExceeded extends RuntimeException {
        static final BudgetExceeded INSTANCE = new BudgetExceeded();
        private BudgetExceeded() { super(null, null, false, false); }
    }
}
