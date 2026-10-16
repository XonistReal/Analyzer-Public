package dev.analyzer.finder;

import dev.analyzer.finder.FinderData.Cell;
import dev.analyzer.finder.FinderData.Grid;
import dev.analyzer.finder.FinderData.Mark;
import dev.analyzer.finder.FinderData.Options;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Pure local predicates. Unknown cells are never interpreted as air, floor or an absent block. */
public final class BlockFinderRules {
    private static final int[][] CARDINAL = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int[][] FACES = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
    private static final Set<String> AMETHYST = Set.of("budding_amethyst", "amethyst_block", "small_amethyst_bud", "medium_amethyst_bud", "large_amethyst_bud", "amethyst_cluster");
    private static final Set<String> WORKSTATIONS = Set.of("crafting_table", "furnace", "blast_furnace", "smoker", "smithing_table", "enchanting_table", "anvil", "chipped_anvil", "damaged_anvil", "brewing_stand", "stonecutter", "grindstone", "loom", "cartography_table", "fletching_table", "composter", "lectern", "crafter");
    private static final Set<String> STORAGE = Set.of("chest", "trapped_chest", "ender_chest", "barrel", "hopper", "dispenser", "dropper", "decorated_pot", "chiseled_bookshelf", "furnace", "blast_furnace", "smoker", "brewing_stand", "crafter");
    private static final Set<String> NATURAL_LIGHT = Set.of("lava", "magma_block", "glow_lichen", "fire", "soul_fire", "brown_mushroom", "shroomlight", "cave_vines", "cave_vines_plant", "sea_pickle", "glowstone", "ochre_froglight", "verdant_froglight", "pearlescent_froglight", "crying_obsidian");
    private static final Set<String> COVER_BLOCKS = Set.of("cobblestone", "mossy_cobblestone", "cobbled_deepslate", "polished_deepslate", "deepslate_bricks", "deepslate_tiles", "bricks", "stone_bricks", "polished_andesite", "polished_diorite", "polished_granite", "obsidian", "netherrack", "nether_bricks", "end_stone_bricks", "smooth_stone");
    private BlockFinderRules() { }

    /** Conservative palette filter. Light can include propagated light on any received block type. */
    public static boolean candidate(String module, Cell cell) {
        if (cell == null || !cell.loaded()) return false;
        return switch (module) {
            case "amethyst-finder" -> named(cell, AMETHYST);
            case "bedrock-void-esp", "hole-tunnel-stairs-esp", "one-by-one-holes" -> cell.air() && !cell.fluid();
            case "beehive-esp" -> cell.is("beehive") || cell.is("bee_nest");
            case "block-notifier" -> targetBlock(cell, "Valuable blocks") || targetBlock(cell, "Workstations") || targetBlock(cell, "Storage") || cell.is("nether_portal");
            case "covered-hole-esp" -> solid(cell);
            case "deepslate-esp" -> deepslate(cell, "Both");
            case "dripstone-esp" -> cell.is("pointed_dripstone");
            case "light-esp" -> true;
            case "piston-esp" -> cell.is("piston") || cell.is("sticky_piston") || cell.is("piston_head");
            case "rotated-deepslate-esp" -> cell.is("deepslate") && (cell.property("axis").equals("x") || cell.property("axis").equals("z"));
            case "spawner-notifier" -> cell.is("spawner") || cell.is("trial_spawner");
            case "sweet-berry-esp" -> cell.is("sweet_berry_bush");
            case "vine-esp" -> cell.is("vine");
            default -> false;
        };
    }

    public static List<Mark> inspect(String module, int x, int y, int z, Grid grid, Options options) {
        Cell cell = at(grid, x, y, z);
        if (!candidate(module, cell)) return List.of();
        return switch (module) {
            case "amethyst-finder" -> inHeight(module, y, options) && (cell.is("budding_amethyst") || options.choice(module, "target").equals("All amethyst") && named(cell, AMETHYST))
                    ? one(module, x, y, z, "Received " + path(cell)) : List.of();
            case "bedrock-void-esp" -> bedrockOpening(module, x, y, z, grid, options);
            case "beehive-esp" -> ((cell.is("beehive") && options.bool(module, "beehives")) || (cell.is("bee_nest") && options.bool(module, "bee-nests"))) && cell.number("honey_level") >= bounded(options.number(module, "minimum-honey"), 0, 5)
                    ? one(module, x, y, z, "Honey level " + cell.number("honey_level")) : List.of();
            case "block-notifier" -> notifiedBlock(module, x, y, z, grid, options, cell);
            case "covered-hole-esp" -> coveredShaft(module, x, y, z, grid, options, cell);
            case "deepslate-esp" -> inHeight(module, y, options) && deepslate(cell, options.choice(module, "target"))
                    ? one(module, x, y, z, "Received " + path(cell) + " at Y " + y) : List.of();
            case "dripstone-esp" -> dripstone(module, x, y, z, grid, options, cell);
            case "hole-tunnel-stairs-esp" -> passages(module, x, y, z, grid, options, cell);
            case "light-esp" -> light(module, x, y, z, options, cell);
            case "one-by-one-holes" -> cell.air() && walls(grid, x, y, z) && solid(at(grid, x, y - 1, z))
                    && (options.bool(module, "covered-only") ? solid(at(grid, x, y + 1, z)) : dryKnown(at(grid, x, y + 1, z)))
                    ? one(module, x, y, z, "Observed one-cell enclosed pocket; origin unknown") : List.of();
            case "piston-esp" -> piston(module, x, y, z, options, cell);
            case "rotated-deepslate-esp" -> rotated(module, x, y, z, options, cell);
            case "spawner-notifier" -> cell.is("spawner") || cell.is("trial_spawner") && options.bool(module, "trial-spawners")
                    ? one(module, x, y, z, "Received " + path(cell) + "; contents and activation unverified") : List.of();
            case "sweet-berry-esp" -> cell.is("sweet_berry_bush") && cell.number("age") >= bounded(options.number(module, "minimum-age"), 0, 3)
                    ? one(module, x, y, z, "Berry age " + cell.number("age")) : List.of();
            case "vine-esp" -> vines(module, x, y, z, grid, options, cell);
            default -> List.of();
        };
    }

    private static List<Mark> bedrockOpening(String id, int x, int y, int z, Grid grid, Options options) {
        if (y != grid.bottomY() || !at(grid, x, y, z).air()) return List.of();
        // A neighboring real bedrock cell is required even in dimensions without a conventional bedrock floor.
        boolean bedrock = false;
        for (int[] dir : CARDINAL) bedrock |= at(grid, x + dir[0], y, z + dir[1]).is("bedrock");
        if (!bedrock) return List.of();
        int minimum = bounded(options.number(id, "minimum-size"), 1, 16);
        ArrayDeque<int[]> pending = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        pending.add(new int[]{x, z});
        while (!pending.isEmpty() && seen.size() < 32) {
            int[] pos = pending.removeFirst();
            long key = ((long) pos[0] << 32) ^ (pos[1] & 0xffffffffL);
            if (!at(grid, pos[0], y, pos[1]).air() || !seen.add(key)) continue;
            if (seen.size() >= minimum) return one(id, x, y, z, "Known bottom-layer air opening beside bedrock");
            for (int[] dir : CARDINAL) pending.addLast(new int[]{pos[0] + dir[0], pos[1] + dir[1]});
        }
        return List.of();
    }

    private static List<Mark> notifiedBlock(String id, int x, int y, int z, Grid grid, Options options, Cell cell) {
        String target = options.choice(id, "target");
        if (!targetBlock(cell, target)) return List.of();
        int hits = 1;
        for (int[] dir : FACES) if (targetBlock(at(grid, x + dir[0], y + dir[1], z + dir[2]), target)) hits++;
        return hits >= bounded(options.number(id, "minimum-neighbors"), 1, 7)
                ? one(id, x, y, z, "Received " + path(cell) + "; " + hits + " matching local cells") : List.of();
    }

    private static boolean targetBlock(Cell cell, String target) {
        if (!cell.loaded()) return false;
        return switch (target) {
            case "Diamond ore" -> cell.is("diamond_ore") || cell.is("deepslate_diamond_ore");
            case "Ancient debris" -> cell.is("ancient_debris");
            case "Beacons" -> cell.is("beacon");
            case "Nether portals" -> cell.is("nether_portal");
            case "Spawners" -> cell.is("spawner") || cell.is("trial_spawner");
            case "Workstations" -> named(cell, WORKSTATIONS);
            case "Storage" -> named(cell, STORAGE) || cell.is("shulker_box") || path(cell).endsWith("_shulker_box");
            case "Valuable blocks" -> cell.is("ancient_debris") || cell.is("beacon") || cell.is("spawner") || cell.is("trial_spawner") || cell.is("diamond_ore") || cell.is("deepslate_diamond_ore") || cell.is("diamond_block") || cell.is("netherite_block");
            default -> false;
        };
    }

    private static List<Mark> coveredShaft(String id, int x, int y, int z, Grid grid, Options options, Cell cover) {
        if (!solid(cover) || options.bool(id, "manufactured-cover-only") && !buildingCover(cover)) return List.of();
        int depth = shaftDepth(grid, x, y - 1, z, bounded(options.number(id, "maximum-depth"), 2, 32));
        return depth >= bounded(options.number(id, "minimum-depth"), 2, 16)
                ? List.of(new Mark(id, x, y - depth, z, 1, depth + 1, 1, "Covered enclosed shaft, " + depth + " air cells; origin unknown", 0)) : List.of();
    }

    private static boolean buildingCover(Cell cell) {
        String name = path(cell);
        return named(cell, COVER_BLOCKS) || name.endsWith("_planks") || name.endsWith("_concrete") || name.endsWith("_terracotta");
    }

    private static boolean deepslate(Cell cell, String choice) {
        boolean raw = cell.is("deepslate");
        String name = path(cell);
        boolean building = name.contains("deepslate") && !raw && !name.endsWith("_ore");
        return switch (choice) { case "Raw deepslate" -> raw; case "Building variants" -> building; case "Both" -> raw || building; default -> false; };
    }

    private static List<Mark> dripstone(String id, int x, int y, int z, Grid grid, Options options, Cell cell) {
        if (!cell.is("pointed_dripstone")) return List.of();
        String direction = cell.property("vertical_direction");
        int step = direction.equals("up") ? 1 : direction.equals("down") ? -1 : 0;
        if (step == 0 || !options.bool(id, step > 0 ? "stalagmites" : "stalactites")) return List.of();
        Cell root = at(grid, x, y - step, z);
        if (!solid(root) || root.is("pointed_dripstone")) return List.of();
        int length = 0;
        for (; length < 32; length++) {
            Cell next = at(grid, x, y + step * length, z);
            if (!next.is("pointed_dripstone") || !next.property("vertical_direction").equals(direction)) break;
        }
        return length >= bounded(options.number(id, "minimum-length"), 1, 32)
                ? List.of(new Mark(id, x, step > 0 ? y : y - length + 1, z, 1, length, 1, (step > 0 ? "Stalagmite" : "Stalactite") + ", " + length + " observed cells", 0)) : List.of();
    }

    private static List<Mark> passages(String id, int x, int y, int z, Grid grid, Options options, Cell cell) {
        if (!cell.air()) return List.of();
        List<Mark> marks = new ArrayList<>(3);
        if (options.bool(id, "holes") && air(at(grid, x, y + 1, z)) && openMouth(grid, x, y + 1, z)) {
            int depth = shaftDepth(grid, x, y, z, bounded(options.number(id, "maximum-depth"), 2, 32));
            if (depth >= bounded(options.number(id, "minimum-depth"), 2, 16))
                marks.add(new Mark(id, x, y - depth + 1, z, 1, depth, 1, "Enclosed vertical shaft, " + depth + " cells; origin unknown", 0));
        }
        int minimum = bounded(options.number(id, "minimum-length"), 3, 16);
        for (int axis = 0; axis < 2; axis++) {
            int dx = axis == 0 ? 1 : 0, dz = axis == 1 ? 1 : 0;
            if (options.bool(id, "tunnels") && corridor(grid, x, y, z, dx, dz) && !corridor(grid, x - dx, y, z - dz, dx, dz)) {
                int length = 0;
                while (length < 32 && corridor(grid, x + dx * length, y, z + dz * length, dx, dz)) length++;
                if (length >= minimum) marks.add(new Mark(id, x, y, z, dx == 1 ? length : 1, 2, dz == 1 ? length : 1, "Enclosed two-high tunnel pattern, " + length + " cells; origin unknown", 0));
            }
        }
        if (options.bool(id, "stairs")) for (int[] dir : CARDINAL) {
            if (!stairSlice(grid, x, y, z, dir[0], dir[1]) || stairSlice(grid, x - dir[0], y - 1, z - dir[1], dir[0], dir[1])) continue;
            int length = 0;
            while (length < 16 && stairSlice(grid, x + dir[0] * length, y + length, z + dir[1] * length, dir[0], dir[1])) length++;
            if (length >= minimum) marks.add(new Mark(id, x + Math.min(0, dir[0] * (length - 1)), y, z + Math.min(0, dir[1] * (length - 1)), dir[0] == 0 ? 1 : length, length + 1, dir[1] == 0 ? 1 : length, "Enclosed rising passage, " + length + " steps; origin unknown", 0));
        }
        return List.copyOf(marks);
    }

    /** Returns the entire observed depth only when a solid bottom is reached within the check limit. */
    private static int shaftDepth(Grid grid, int x, int top, int z, int maximum) {
        for (int depth = 0; depth <= maximum; depth++) {
            Cell cell = at(grid, x, top - depth, z);
            if (solid(cell)) return depth;
            if (depth == maximum || !air(cell) || !walls(grid, x, top - depth, z)) return 0;
        }
        return 0;
    }

    private static boolean corridor(Grid grid, int x, int y, int z, int dx, int dz) {
        return floorSpace(grid, x, y, z) && solid(at(grid, x, y + 2, z))
                && solid(at(grid, x + dz, y, z + dx)) && solid(at(grid, x - dz, y, z - dx))
                && solid(at(grid, x + dz, y + 1, z + dx)) && solid(at(grid, x - dz, y + 1, z - dx));
    }

    private static boolean stairSlice(Grid grid, int x, int y, int z, int dx, int dz) {
        return floorSpace(grid, x, y, z) && solid(at(grid, x + dz, y, z + dx)) && solid(at(grid, x - dz, y, z - dx));
    }

    private static boolean floorSpace(Grid grid, int x, int y, int z) {
        return air(at(grid, x, y, z)) && air(at(grid, x, y + 1, z)) && solid(at(grid, x, y - 1, z));
    }

    private static List<Mark> light(String id, int x, int y, int z, Options options, Cell cell) {
        int level = options.bool(id, "source-only") ? cell.luminance() : Math.max(cell.blockLight(), cell.luminance());
        if (level < bounded(options.number(id, "minimum-light"), 1, 15)) return List.of();
        if (!options.bool(id, "include-natural") && (named(cell, NATURAL_LIGHT) || named(cell, AMETHYST))) return List.of();
        String name = path(cell);
        if ((name.contains("torch") || name.contains("lantern") && !name.equals("sea_lantern")) && !options.bool(id, "torches")) return List.of();
        if ((cell.is("glowstone") || cell.is("sea_lantern")) && !options.bool(id, "glowstone")) return List.of();
        if (name.contains("redstone") && !options.bool(id, "redstone")) return List.of();
        if ((cell.is("beacon") || cell.is("conduit")) && !options.bool(id, "beacons")) return List.of();
        int color = options.bool(id, "thermal-colors") ? thermal(level) : 0;
        return List.of(new Mark(id, x, y, z, 1, 1, 1, "Block light " + level + (cell.luminance() > 0 ? "; emitting source" : "; propagated received light"), color));
    }

    private static int thermal(int light) {
        double strength = Math.clamp(light / 15d, 0d, 1d);
        return 0xff000000 | (int) (255 * strength) << 16 | (int) (210 * (1 - Math.abs(2 * strength - 1))) << 8 | (int) (255 * (1 - strength));
    }

    private static List<Mark> piston(String id, int x, int y, int z, Options options, Cell cell) {
        boolean body = cell.is("piston") && options.bool(id, "regular") || cell.is("sticky_piston") && options.bool(id, "sticky");
        boolean head = cell.is("piston_head") && options.bool(id, "heads");
        if (!(head || body) || body && options.bool(id, "extended-only") && !cell.property("extended").equals("true")) return List.of();
        return one(id, x, y, z, "Received " + path(cell) + ", facing " + cell.property("facing"));
    }

    private static List<Mark> rotated(String id, int x, int y, int z, Options options, Cell cell) {
        if (!cell.is("deepslate")) return List.of();
        String axis = cell.property("axis"), choice = options.choice(id, "orientation");
        boolean match = choice.equals("Horizontal") ? axis.equals("x") || axis.equals("z") : choice.equals("X axis") ? axis.equals("x") : choice.equals("Z axis") && axis.equals("z");
        return match ? one(id, x, y, z, "Actual raw deepslate axis " + axis + "; placement unverified") : List.of();
    }

    private static List<Mark> vines(String id, int x, int y, int z, Grid grid, Options options, Cell cell) {
        if (!cell.is("vine")) return List.of();
        Cell below = at(grid, x, y - 1, z);
        if (!below.loaded() || below.is("vine") || options.bool(id, "grounded-only") && !solid(below)) return List.of();
        int length = 0;
        while (length < 32 && at(grid, x, y + length, z).is("vine")) length++;
        return length >= bounded(options.number(id, "minimum-length"), 1, 32)
                ? List.of(new Mark(id, x, y, z, 1, length, 1, (solid(below) ? "Grounded" : "Hanging") + " vine column, " + length + " observed cells", 0)) : List.of();
    }

    private static boolean inHeight(String id, int y, Options options) { return y >= options.number(id, "min-y") && y <= options.number(id, "max-y"); }
    private static List<Mark> one(String id, int x, int y, int z, String evidence) { return List.of(new Mark(id, x, y, z, evidence)); }
    private static boolean named(Cell cell, Set<String> names) { return cell.loaded() && cell.id().startsWith("minecraft:") && names.contains(path(cell)); }
    private static String path(Cell cell) { return cell.id().startsWith("minecraft:") ? cell.id().substring(10) : cell.id(); }
    private static boolean solid(Cell cell) { return cell.loaded() && cell.solid() && !cell.fluid(); }
    private static boolean air(Cell cell) { return cell.loaded() && cell.air() && !cell.fluid(); }
    private static boolean dryKnown(Cell cell) { return cell.loaded() && !cell.fluid(); }
    private static boolean walls(Grid grid, int x, int y, int z) {
        for (int[] dir : CARDINAL) if (!solid(at(grid, x + dir[0], y, z + dir[1]))) return false;
        return true;
    }
    private static boolean openMouth(Grid grid, int x, int y, int z) {
        boolean open = false;
        for (int[] dir : CARDINAL) {
            Cell cell = at(grid, x + dir[0], y, z + dir[1]);
            if (!cell.loaded()) return false;
            open |= air(cell);
        }
        return open;
    }
    private static Cell at(Grid grid, int x, int y, int z) {
        if (y < grid.bottomY() || y >= grid.topY()) return Cell.UNKNOWN;
        Cell cell = grid.cell(x, y, z);
        return cell == null || !cell.loaded() ? Cell.UNKNOWN : cell;
    }
    private static int bounded(double value, int min, int max) { return Double.isFinite(value) ? Math.clamp((int) value, min, max) : min; }
}
