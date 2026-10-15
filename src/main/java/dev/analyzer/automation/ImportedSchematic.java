package dev.analyzer.automation;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/** Validated exact-state target for Analyzer's ordinary player-placement builder. */
public final class ImportedSchematic implements BuildSchematic {
    private final SchematicData data;
    private final BlockState[] palette;
    private final Map<Item, Integer> requiredItems;
    private final int requiredBlocks;
    private final boolean previewOnly;
    private ImportedSchematic(SchematicData data, BlockState[] palette, Map<Item, Integer> requiredItems, int requiredBlocks, boolean previewOnly) {
        this.data = data; this.palette = palette; this.requiredItems = Collections.unmodifiableMap(requiredItems); this.requiredBlocks = requiredBlocks; this.previewOnly = previewOnly;
    }
    public static ImportedSchematic from(SchematicData data) { return resolve(data, false); }
    /** Exact captured states for read-only ghosts, even when construction is unsupported. */
    public static ImportedSchematic preview(SchematicData data) { return resolve(data, true); }
    private static ImportedSchematic resolve(SchematicData data, boolean previewOnly) {
        Objects.requireNonNull(data, "schematic");
        String legacyProblem = LegacySchematicBlocks.buildProblem(data);
        if (!previewOnly && !legacyProblem.isEmpty()) throw new IllegalArgumentException(legacyProblem);
        BlockState[] palette = new BlockState[data.palette().size()];
        Map<SchematicData.State, BlockState> resolved = new HashMap<>();
        for (int i = 0; i < palette.length; i++) {
            SchematicData.State entry = data.palette().get(i); Identifier id = Identifier.tryParse(entry.name());
            if (id == null || !Registries.BLOCK.containsId(id)) throw new IllegalArgumentException("Block is unavailable in this Minecraft installation: " + entry.name());
            BlockState state = Registries.BLOCK.get(id).getDefaultState();
            for (var property : entry.properties().entrySet()) {
                Property<?> type = state.getBlock().getStateManager().getProperty(property.getKey());
                if (type == null) throw new IllegalArgumentException("Unknown property " + property.getKey() + " for " + entry.name());
                state = property(state, type, property.getValue());
            }
            if (!previewOnly) {
                SchematicData.State construction = SchematicStatePolicy.construction(entry);
                BlockState target = Registries.BLOCK.get(Identifier.of(construction.name())).getDefaultState();
                for (var property : construction.properties().entrySet()) target = property(target, target.getBlock().getStateManager().getProperty(property.getKey()), property.getValue());
                state = target;
            }
            palette[i] = state; resolved.put(entry, state);
        }
        Map<Item, Integer> items = new TreeMap<>(Comparator.comparing(item -> Registries.ITEM.getId(item).toString())); int blocks = 0;
        for (int y = 0; y < data.height(); y++) for (int z = 0; z < data.length(); z++) for (int x = 0; x < data.width(); x++) {
            SchematicData.State entry = data.stateAt(x, y, z);
            if (entry == null || entry.air()) continue;
            BlockState state = resolved.get(entry);
            if (state.isAir()) continue;
            if (!previewOnly) { validatePlacement(entry, state); validatePair(data, resolved, entry, x, y, z); }
            int quantity = quantity(state);
            if (quantity > 0 && state.getBlock().asItem() != Items.AIR) items.merge(state.getBlock().asItem(), quantity, Integer::sum);
            if (needsBucket(state)) items.put(Items.BUCKET, 1);
            blocks++;
        }
        if (blocks == 0) throw new IllegalArgumentException("Schematic has no nonair blocks to build");
        return new ImportedSchematic(data, palette, items, blocks, previewOnly);
    }
    private static void validatePlacement(SchematicData.State entry, BlockState state) {
        if (state.getBlock().asItem() == Items.AIR && !SchematicStatePolicy.fluid(entry.name()))
            throw new IllegalArgumentException("Cannot obtain/place " + entry.canonical() + " normally. The ghost is available; export a construction version without generated/non-item blocks.");
        String name = entry.name();
        if (name.contains("command_block") || Set.of("minecraft:bedrock", "minecraft:barrier", "minecraft:structure_block", "minecraft:structure_void", "minecraft:jigsaw", "minecraft:light", "minecraft:end_portal_frame", "minecraft:spawner", "minecraft:trial_spawner", "minecraft:vault", "minecraft:reinforced_deepslate").contains(name))
            throw new IllegalArgumentException("This block cannot be obtained and built normally in Survival: " + name + ". The ghost remains available.");
    }
    private static void validatePair(SchematicData data, Map<SchematicData.State, BlockState> resolved, SchematicData.State entry, int x, int y, int z) {
        BlockState state = resolved.get(entry);
        String half = value(state, "half");
        if (half.equals("upper") || half.equals("lower")) {
            SchematicData.State other = data.stateAt(x, y + (half.equals("upper") ? -1 : 1), z);
            if (other == null || !other.name().equals(entry.name()) || !value(resolved.get(other), "half").equals(half.equals("upper") ? "lower" : "upper"))
                throw new IllegalArgumentException("Schematic contains an incomplete two-block plant or door: " + entry.name());
            for (String property : List.of("facing","hinge","open")) {
                if (!value(state,property).equals(value(resolved.get(other),property)))
                    throw new IllegalArgumentException("Door halves disagree on " + property + " at local " + x + ", " + y + ", " + z + ". Export matching lower/upper construction states; the ghost still shows the original file.");
            }
        }
        String part = value(state, "part");
        if (part.equals("head") || part.equals("foot")) {
            String facing = value(state, "facing"); int sign = part.equals("foot") ? 1 : -1;
            int dx = facing.equals("east") ? sign : facing.equals("west") ? -sign : 0;
            int dz = facing.equals("south") ? sign : facing.equals("north") ? -sign : 0;
            SchematicData.State other = data.stateAt(x + dx, y, z + dz);
            if (other == null || !other.name().equals(entry.name()) || !value(resolved.get(other), "part").equals(part.equals("head") ? "foot" : "head") || !value(resolved.get(other), "facing").equals(facing))
                throw new IllegalArgumentException("Schematic contains an incomplete bed: " + entry.name());
        }
    }
    private static <T extends Comparable<T>> BlockState property(BlockState state, Property<T> property, String value) {
        T parsed = property.parse(value).orElseThrow(() -> new IllegalArgumentException("Invalid " + property.getName() + " value " + value));
        return state.with(property, parsed);
    }
    static String value(BlockState state, String name) {
        Property<?> property = state.getBlock().getStateManager().getProperty(name);
        return property == null ? "" : value(state, property);
    }
    private static <T extends Comparable<T>> String value(BlockState state, Property<T> property) { return property.name(state.get(property)); }
    static boolean secondary(BlockState state) { return value(state, "half").equals("upper") || value(state, "part").equals("head"); }
    static boolean flowing(BlockState state) { return SchematicStatePolicy.fluid(Registries.BLOCK.getId(state.getBlock()).toString()) && !value(state, "level").equals("0"); }
    static boolean source(BlockState state) { return SchematicStatePolicy.fluid(Registries.BLOCK.getId(state.getBlock()).toString()) && value(state, "level").equals("0"); }
    static boolean needsBucket(BlockState state) { return source(state) || value(state, "waterlogged").equals("true"); }
    static int quantity(BlockState state) {
        if (secondary(state) || SchematicStatePolicy.fluid(Registries.BLOCK.getId(state.getBlock()).toString())) return 0;
        if (value(state, "type").equals("double") && Registries.BLOCK.getId(state.getBlock()).getPath().endsWith("_slab")) return 2;
        for (String field : List.of("layers", "candles", "pickles", "eggs")) {
            String value = value(state, field); if (!value.isEmpty()) return Integer.parseInt(value);
        }
        return 1;
    }
    static BlockPos partner(BlockPos pos, BlockState state) {
        if (value(state, "half").equals("lower")) return pos.up();
        if (value(state, "part").equals("foot")) {
            net.minecraft.util.math.Direction facing = net.minecraft.util.math.Direction.byName(value(state, "facing"));
            return facing == null ? null : pos.offset(facing);
        }
        return null;
    }
    public SchematicData data() { return data; }
    public Map<Item, Integer> requiredItems() { return requiredItems; }
    /** Remaining actual item costs, including partially stacked blocks and linked door/bed halves. */
    public Map<Item, Integer> remainingItems(ClientWorld world, BlockPos origin) {
        Map<Item, Integer> items = new TreeMap<>(Comparator.comparing(item -> Registries.ITEM.getId(item).toString()));
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int y = 0; y < heightY(); y++) for (int z = 0; z < lengthZ(); z++) for (int x = 0; x < widthX(); x++) {
            int index = data.indexAt(x, y, z); if (index < 0 || !inSchematic(x, y, z, null)) continue; BlockState desired = palette[index];
            pos.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
            if (!world.isChunkLoaded(pos) || !world.isInBuildLimit(pos) || !world.getWorldBorder().contains(pos)) throw new IllegalArgumentException("Load the complete selected schematic inside the world border and build height before checking remaining materials");
            if (desired.isAir() || secondary(desired)) continue;
            BlockState current = world.getBlockState(pos);
            boolean matched = NativeBuilder.matches(current, desired);
            BlockPos partner = partner(pos, desired);
            if (partner != null) {
                if (!world.isChunkLoaded(partner)) throw new IllegalArgumentException("Load every paired bed or door before checking remaining materials");
                int dx = partner.getX() - origin.getX(), dy = partner.getY() - origin.getY(), dz = partner.getZ() - origin.getZ();
                matched &= NativeBuilder.matches(world.getBlockState(partner), desiredState(dx, dy, dz, null, List.of()));
            }
            if (matched) continue;
            if (needsBucket(desired)) items.put(Items.BUCKET, 1);
            int quantity = quantity(desired);
            if (current.isOf(desired.getBlock()) && NativeBuilder.placementCompatible(current, desired) && !NativeBuilder.canStack(current, desired)) quantity = 0;
            if (current.isOf(desired.getBlock()) && value(desired, "waterlogged").equals("true") && value(current, "waterlogged").equals("false")) quantity = 0;
            if (current.isOf(desired.getBlock())) {
                if (value(desired, "type").equals("double") && !value(current, "type").equals("double")) quantity = 1;
                for (String field : List.of("layers", "candles", "pickles", "eggs")) {
                    String target = value(desired, field), existing = value(current, field);
                    if (!target.isEmpty() && !existing.isEmpty()) {
                        int difference = Integer.parseInt(target) - Integer.parseInt(existing);
                        if (difference > 0) quantity = difference;
                    }
                }
            }
            if (quantity > 0 && desired.getBlock().asItem() != Items.AIR) items.merge(desired.getBlock().asItem(), quantity, Integer::sum);
        }
        return Collections.unmodifiableMap(items);
    }
    public int requiredBlocks() { return requiredBlocks; }
    @Override public int widthX() { return data.width(); }
    @Override public int heightY() { return data.height(); }
    @Override public int lengthZ() { return data.length(); }
    @Override public boolean inSchematic(int x, int y, int z, BlockState current) {
        SchematicData.State snapshot = data.stateAt(x, y, z);
        return snapshot != null && (previewOnly || !SchematicStatePolicy.derived(snapshot.name()));
    }
    @Override public BlockState desiredState(int x, int y, int z, BlockState current, List<BlockState> placeable) {
        int index = data.indexAt(x, y, z);
        if (index < 0) return current == null ? Blocks.AIR.getDefaultState() : current;
        return palette[index];
    }
    public record Progress(int placed, int required, int matching, int observed, int volume) {
        public boolean complete() { return observed == volume && matching == volume; }
        public double fraction() { return required == 0 ? 1 : placed / (double)required; }
        public int missing() { return Math.max(0, required - placed); }
    }
    public Progress progress(ClientWorld world, BlockPos origin) {
        int placed = 0, matching = 0, observed = 0, volume = 0; BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int y = 0; y < heightY(); y++) for (int z = 0; z < lengthZ(); z++) for (int x = 0; x < widthX(); x++) {
            if (!inSchematic(x, y, z, null)) continue; volume++;
            pos.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
            if (!world.isChunkLoaded(pos)) continue; observed++;
            BlockState desired = desiredState(x, y, z, null, List.of());
            if (NativeBuilder.matches(world.getBlockState(pos), desired)) {
                matching++; if (!desired.isAir()) placed++;
            }
        }
        return new Progress(placed, requiredBlocks, matching, observed, volume);
    }
    public String siteProblem(ClientWorld world, BlockPos origin) { return AutoSiteSelector.siteProblem(world, origin, this); }
}
