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
    private ImportedSchematic(SchematicData data, BlockState[] palette, Map<Item, Integer> requiredItems, int requiredBlocks) {
        this.data = data; this.palette = palette; this.requiredItems = Collections.unmodifiableMap(requiredItems); this.requiredBlocks = requiredBlocks;
    }
    public static ImportedSchematic from(SchematicData data) {
        Objects.requireNonNull(data, "schematic");
        String legacyProblem = LegacySchematicBlocks.buildProblem(data);
        if (!legacyProblem.isEmpty()) throw new IllegalArgumentException(legacyProblem);
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
            palette[i] = state; resolved.put(entry, state);
        }
        Map<Item, Integer> items = new TreeMap<>(Comparator.comparing(item -> Registries.ITEM.getId(item).toString())); int blocks = 0;
        for (int y = 0; y < data.height(); y++) for (int z = 0; z < data.length(); z++) for (int x = 0; x < data.width(); x++) {
            SchematicData.State entry = data.stateAt(x, y, z);
            if (entry == null || entry.air()) continue;
            BlockState state = resolved.get(entry); validatePlacement(entry, state);
            validatePair(data, resolved, entry, x, y, z);
            int quantity = quantity(state);
            if (quantity > 0) items.merge(state.getBlock().asItem(), quantity, Integer::sum);
            blocks++;
        }
        if (blocks == 0) throw new IllegalArgumentException("Schematic has no nonair blocks to build");
        return new ImportedSchematic(data, palette, items, blocks);
    }
    private static void validatePlacement(SchematicData.State entry, BlockState state) {
        if (state.getBlock().asItem() == Items.AIR || !state.getFluidState().isEmpty())
            throw new IllegalArgumentException("This block state needs a special world interaction and cannot be built by ordinary block placement: " + entry.canonical());
        String name = entry.name();
        if (name.contains("command_block") || Set.of("minecraft:bedrock", "minecraft:barrier", "minecraft:structure_block", "minecraft:structure_void", "minecraft:jigsaw", "minecraft:light", "minecraft:end_portal_frame", "minecraft:spawner", "minecraft:trial_spawner", "minecraft:vault", "minecraft:reinforced_deepslate").contains(name))
            throw new IllegalArgumentException("This block cannot be obtained and built normally in Survival: " + name);
        // These are simulation states, rather than properties a player's placement can select.
        BlockState defaults = state.getBlock().getDefaultState();
        for (String field : List.of("powered", "lit", "age", "moisture", "level", "signal_fire", "unstable")) {
            Property<?> property = state.getBlock().getStateManager().getProperty(field);
            if (property != null && !state.get(property).equals(defaults.get(property)))
                throw new IllegalArgumentException("State requires a simulation/workstation change after placement: " + entry.canonical());
        }
    }
    private static void validatePair(SchematicData data, Map<SchematicData.State, BlockState> resolved, SchematicData.State entry, int x, int y, int z) {
        BlockState state = resolved.get(entry);
        String half = value(state, "half");
        if (half.equals("upper") || half.equals("lower")) {
            SchematicData.State other = data.stateAt(x, y + (half.equals("upper") ? -1 : 1), z);
            if (other == null || !other.name().equals(entry.name()) || !value(resolved.get(other), "half").equals(half.equals("upper") ? "lower" : "upper"))
                throw new IllegalArgumentException("Schematic contains an incomplete two-block plant or door: " + entry.name());
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
    static int quantity(BlockState state) {
        if (secondary(state)) return 0;
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
            int index = data.indexAt(x, y, z); if (index < 0) continue; BlockState desired = palette[index];
            pos.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
            if (!world.isChunkLoaded(pos) || !world.isInBuildLimit(pos) || !world.getWorldBorder().contains(pos)) throw new IllegalArgumentException("Load the complete selected schematic inside the world border and build height before checking remaining materials");
            if (desired.isAir() || secondary(desired)) continue;
            BlockState current = world.getBlockState(pos);
            boolean matched = current.equals(desired) || NativeBuilder.placementCompatible(current, desired) && !NativeBuilder.canStack(current, desired);
            BlockPos partner = partner(pos, desired);
            if (partner != null) {
                if (!world.isChunkLoaded(partner)) throw new IllegalArgumentException("Load every paired bed or door before checking remaining materials");
                int dx = partner.getX() - origin.getX(), dy = partner.getY() - origin.getY(), dz = partner.getZ() - origin.getZ();
                matched &= world.getBlockState(partner).equals(desiredState(dx, dy, dz, null, List.of()));
            }
            if (matched) continue;
            int quantity = quantity(desired);
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
            if (quantity > 0) items.merge(desired.getBlock().asItem(), quantity, Integer::sum);
        }
        return Collections.unmodifiableMap(items);
    }
    public int requiredBlocks() { return requiredBlocks; }
    @Override public int widthX() { return data.width(); }
    @Override public int heightY() { return data.height(); }
    @Override public int lengthZ() { return data.length(); }
    @Override public boolean inSchematic(int x, int y, int z, BlockState current) { return data.stateAt(x, y, z) != null; }
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
            if (world.getBlockState(pos).equals(desired) || desired.isAir() && world.getBlockState(pos).isAir()) {
                matching++; if (!desired.isAir()) placed++;
            }
        }
        return new Progress(placed, requiredBlocks, matching, observed, volume);
    }
    public String siteProblem(ClientWorld world, BlockPos origin) { return AutoSiteSelector.siteProblem(world, origin, this); }
}
