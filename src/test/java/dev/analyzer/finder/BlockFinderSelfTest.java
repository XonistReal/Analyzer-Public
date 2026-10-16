package dev.analyzer.finder;

import dev.analyzer.core.BlockFinderModules;
import dev.analyzer.core.Category;
import dev.analyzer.core.ModuleDefinition;
import dev.analyzer.core.Setting;
import dev.analyzer.finder.FinderData.Cell;
import dev.analyzer.finder.FinderData.Grid;
import dev.analyzer.finder.FinderData.Mark;
import dev.analyzer.finder.FinderData.Options;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** Hand-built observation fixtures exercise evidence, settings and unknown boundaries without Minecraft. */
public final class BlockFinderSelfTest {
    private static final Cell STONE = block("stone");
    private static final Cell AIR = new Cell("minecraft:air", true, true, false, false, 0, 0, Map.of());
    private static int assertions;
    private BlockFinderSelfTest() { }

    public static void main(String[] args) {
        definitions();
        exactBlocks();
        blocksAndGroups();
        voidsAndPockets();
        shaftsAndPassages();
        columns();
        light();
        unknownAndBudgets();
        System.out.println("Analyzer block finder checks passed: " + assertions + " assertions");
    }

    private static void definitions() {
        var ids = new HashSet<String>();
        check(BlockFinderModules.definitions().size() == 15, "All 15 block finder modules are defined");
        for (ModuleDefinition module : BlockFinderModules.definitions()) {
            check(ids.add(module.id()), "Module identity is unique: " + module.id());
            check(module.category() == Category.VISUAL && !module.defaultEnabled(), "Finder remains opt-in: " + module.id());
            var settingIds = new HashSet<String>();
            for (Setting<?> setting : module.settings()) {
                check(settingIds.add(setting.id()), "Specific and shared settings do not collide: " + module.id() + "/" + setting.id());
                check(setting.normalize(setting.defaultValue()).equals(setting.defaultValue()), "Defaults are normalized: " + setting.id());
            }
            check(module.setting("max-markers").max() == 512, "Marker count has a finite bound");
            check(module.setting("scan-budget").max() == 16384, "Scan work has a finite bound");
        }
    }

    private static void exactBlocks() {
        Values values = new Values();
        Fixture world = new Fixture();
        String id = "amethyst-finder";
        world.put(0, 20, 0, block("budding_amethyst"));
        check(found(id, world, values), "Budding amethyst is the useful default target");
        world.put(0, 20, 0, block("amethyst_block"));
        check(!found(id, world, values), "Ordinary amethyst stays off under budding-only selection");
        values.set(id, "target", "All amethyst");
        check(found(id, world, values), "All-amethyst mode includes the actual block");
        values.set(id, "max-y", 19);
        check(!found(id, world, values), "Amethyst respects its selected height band");

        id = "beehive-esp";
        world.put(0, 20, 0, block("beehive", "honey_level", "4"));
        check(!found(id, world, values), "Almost-full hive is excluded by full-honey default");
        world.put(0, 20, 0, block("beehive", "honey_level", "5"));
        check(found(id, world, values), "Actual full hive matches");
        values.set(id, "beehives", false);
        check(!found(id, world, values), "Crafted-hive filter is independent");
        world.put(0, 20, 0, block("bee_nest", "honey_level", "5"));
        check(found(id, world, values), "Natural nests remain selected");
        world.put(0, 20, 0, block("bee_nest"));
        values.set(id, "minimum-honey", 0);
        check(!found(id, world, values), "Missing received honey property is not invented as zero");

        id = "sweet-berry-esp";
        world.put(0, 20, 0, plant("sweet_berry_bush", "age", "1"));
        check(!found(id, world, values), "Young berry bush is excluded by harvestable default");
        world.put(0, 20, 0, plant("sweet_berry_bush", "age", "2"));
        check(found(id, world, values), "Harvestable berry bush matches");
        values.set(id, "minimum-age", 3);
        check(!found(id, world, values), "Berry age threshold is applied");

        id = "spawner-notifier";
        world.put(0, 20, 0, block("spawner"));
        check(found(id, world, values), "Received normal spawner matches");
        world.put(0, 20, 0, block("trial_spawner"));
        check(found(id, world, values), "Trial spawners are configurable");
        values.set(id, "trial-spawners", false);
        check(!found(id, world, values), "Trial-spawner opt-out is applied");
    }

    private static void blocksAndGroups() {
        Values values = new Values();
        Fixture world = new Fixture();
        String id = "block-notifier";
        for (String name : List.of("diamond_ore", "deepslate_diamond_ore", "ancient_debris", "beacon", "spawner", "netherite_block")) {
            world.put(0, 20, 0, block(name));
            check(found(id, world, values), "Valuable target matches actual registry block " + name);
        }
        world.put(0, 20, 0, block("coal_ore"));
        check(!found(id, world, values), "Unselected ores do not become valuable hits");
        values.set(id, "target", "Storage");
        for (String name : List.of("barrel", "chest", "trapped_chest", "ender_chest", "red_shulker_box", "decorated_pot", "crafter", "chiseled_bookshelf")) {
            world.put(0, 20, 0, block(name));
            check(found(id, world, values), "Storage includes " + name);
        }
        values.set(id, "target", "Workstations");
        world.put(0, 20, 0, block("enchanting_table"));
        check(found(id, world, values), "Workstation group includes enchanting tables");
        values.set(id, "target", "Diamond ore");
        values.set(id, "minimum-neighbors", 3);
        world.put(0, 20, 0, block("diamond_ore"));
        world.put(1, 20, 0, block("diamond_ore"));
        check(!found(id, world, values), "Cluster threshold excludes insufficient matching neighbors");
        world.put(0, 21, 0, block("deepslate_diamond_ore"));
        check(found(id, world, values), "Cluster threshold includes both diamond-ore forms");

        id = "deepslate-esp";
        world.put(0, 20, 0, block("deepslate"));
        check(found(id, world, values), "High raw deepslate matches default height");
        check(BlockFinderRules.inspect(id, 0, 0, 0, new Fixture(STONE).put(0, 0, 0, block("deepslate")), values).isEmpty(), "Low natural deepslate is excluded by default");
        values.set(id, "target", "Building variants");
        world.put(0, 20, 0, block("deepslate_tiles"));
        check(found(id, world, values), "Building variant matches selected type");
        world.put(0, 20, 0, block("deepslate_iron_ore"));
        check(!found(id, world, values), "Deepslate ores do not masquerade as building blocks");

        id = "rotated-deepslate-esp";
        world.put(0, 20, 0, block("deepslate", "axis", "y"));
        check(!found(id, world, values), "Ordinary vertical deepslate is excluded");
        world.put(0, 20, 0, block("deepslate", "axis", "x"));
        check(found(id, world, values), "Actual horizontal axis matches");
        values.set(id, "orientation", "Z axis");
        check(!found(id, world, values), "Selected axis is respected");
        world.put(0, 20, 0, block("polished_deepslate"));
        check(!found(id, world, values), "Axisless vanilla decorative blocks are never claimed rotated");

        id = "piston-esp";
        world.put(0, 20, 0, block("piston", "extended", "false"));
        check(found(id, world, values), "Regular piston is selected");
        values.set(id, "extended-only", true);
        check(!found(id, world, values), "Retracted piston is excluded in extended-only mode");
        world.put(0, 20, 0, block("sticky_piston", "extended", "true"));
        check(found(id, world, values), "Extended sticky piston matches");
        values.set(id, "sticky", false);
        check(!found(id, world, values), "Sticky filter is independent");
        world.put(0, 20, 0, block("piston_head"));
        check(found(id, world, values), "Piston head is an observed extended mechanism");
    }

    private static void voidsAndPockets() {
        Values values = new Values();
        String id = "bedrock-void-esp";
        Fixture world = new Fixture(AIR);
        world.put(1, -64, 0, block("bedrock"));
        check(!BlockFinderRules.inspect(id, 0, -63, 0, world, values).stream().findAny().isPresent(), "Ordinary air above the floor never becomes a bedrock void");
        check(!BlockFinderRules.inspect(id, 0, -64, 0, new Fixture(AIR), values).stream().findAny().isPresent(), "Dimensions without neighboring received bedrock do not generate void hits");
        check(!BlockFinderRules.inspect(id, 0, -64, 0, world, values).isEmpty(), "Known connected bottom-layer opening beside bedrock matches");
        world = new Fixture(STONE).put(0, -64, 0, AIR).put(1, -64, 0, block("bedrock"));
        check(BlockFinderRules.inspect(id, 0, -64, 0, world, values).isEmpty(), "Single missing floor cell does not meet two-cell default");
        world.put(0, -64, 1, AIR);
        check(!BlockFinderRules.inspect(id, 0, -64, 0, world, values).isEmpty(), "Second observed adjacent air cell satisfies threshold");
        world.put(0, -64, 1, Cell.UNKNOWN);
        check(BlockFinderRules.inspect(id, 0, -64, 0, world, values).isEmpty(), "An unloaded neighbor never counts as a missing floor cell");

        id = "one-by-one-holes";
        world = new Fixture(STONE).put(0, 20, 0, AIR);
        check(found(id, world, values), "Actual sealed one-cell pocket matches");
        world.put(1, 20, 0, Cell.UNKNOWN);
        check(!found(id, world, values), "Unknown side cannot be claimed a solid wall");
        world.put(1, 20, 0, STONE).put(0, 21, 0, AIR);
        check(!found(id, world, values), "Solid ceiling is required by default");
        values.set(id, "covered-only", false);
        check(found(id, world, values), "Open-top mode deliberately includes four-wall pockets");
        world.put(0, 19, 0, new Cell("minecraft:water", true, false, false, true, 0, 0, Map.of()));
        check(!found(id, world, values), "Fluid floor is not solid support");
    }

    private static void shaftsAndPassages() {
        Values values = new Values();
        Fixture shaft = new Fixture(STONE).put(0, 20, 0, block("cobblestone"));
        for (int y = 16; y < 20; y++) shaft.put(0, y, 0, AIR);
        String id = "covered-hole-esp";
        var marks = BlockFinderRules.inspect(id, 0, 20, 0, shaft, values);
        check(marks.size() == 1 && marks.getFirst().y() == 16 && marks.getFirst().height() == 5, "Covered shaft marker includes verified void and its cover");
        shaft.put(0, 20, 0, block("dirt"));
        check(!found(id, shaft, values), "Natural-type cover is excluded in building-block mode");
        values.set(id, "manufactured-cover-only", false);
        check(found(id, shaft, values), "Natural cover can be explicitly included without claiming player origin");
        shaft.put(1, 17, 0, AIR);
        check(!found(id, shaft, values), "Side opening invalidates an enclosed shaft");
        shaft.put(1, 17, 0, STONE).put(0, 15, 0, AIR);
        values.set(id, "maximum-depth", 4);
        check(!found(id, shaft, values), "A bottom beyond the checked depth is not invented");

        id = "hole-tunnel-stairs-esp";
        Fixture tunnel = new Fixture(STONE);
        for (int x = 0; x < 6; x++) for (int y = 10; y <= 11; y++) tunnel.put(x, y, 0, AIR);
        marks = BlockFinderRules.inspect(id, 0, 10, 0, tunnel, values);
        check(marks.stream().anyMatch(mark -> mark.evidence().contains("tunnel") && mark.width() == 6), "Enclosed two-high straight tunnel is found");
        check(BlockFinderRules.inspect(id, 1, 10, 0, tunnel, values).stream().noneMatch(mark -> mark.evidence().contains("tunnel")), "One start represents a straight corridor instead of one duplicate per cell");
        values.set(id, "tunnels", false);
        check(BlockFinderRules.inspect(id, 0, 10, 0, tunnel, values).isEmpty(), "Tunnel subfilter disables its marks");
        values.set(id, "tunnels", true);
        tunnel.put(2, 12, 0, AIR);
        check(BlockFinderRules.inspect(id, 0, 10, 0, tunnel, values).isEmpty(), "Missing roof breaks short tunnel pattern before its threshold");

        Fixture stairs = new Fixture(STONE);
        for (int step = 0; step < 5; step++) stairs.put(step, 10 + step, 0, AIR).put(step, 11 + step, 0, AIR);
        marks = BlockFinderRules.inspect(id, 0, 10, 0, stairs, values);
        check(marks.stream().anyMatch(mark -> mark.evidence().contains("rising") && mark.width() == 5 && mark.height() == 6), "Supported enclosed rising passage matches actual steps");
        stairs.put(1, 10, 0, AIR);
        check(BlockFinderRules.inspect(id, 0, 10, 0, stairs, values).isEmpty(), "Missing stair support prevents a rising passage claim");

        Fixture openShaft = new Fixture(STONE);
        for (int y = 16; y <= 20; y++) openShaft.put(0, y, 0, AIR);
        openShaft.put(1, 20, 0, AIR);
        marks = BlockFinderRules.inspect(id, 0, 19, 0, openShaft, values);
        check(marks.stream().anyMatch(mark -> mark.evidence().contains("shaft") && mark.height() == 4), "Shaft mouth opens into actual known neighboring air");
        check(BlockFinderRules.inspect(id, 0, 18, 0, openShaft, values).isEmpty(), "Interior shaft cells do not emit nested duplicate shaft boxes");
    }

    private static void columns() {
        Values values = new Values();
        String id = "dripstone-esp";
        Fixture world = new Fixture(AIR).put(0, 19, 0, STONE);
        for (int y = 20; y < 25; y++) world.put(0, y, 0, plant("pointed_dripstone", "vertical_direction", "up"));
        var marks = BlockFinderRules.inspect(id, 0, 20, 0, world, values);
        check(marks.size() == 1 && marks.getFirst().height() == 5, "Supported stalagmite uses actual contiguous length");
        check(BlockFinderRules.inspect(id, 0, 21, 0, world, values).isEmpty(), "Column emits one rooted marker");
        values.set(id, "minimum-length", 6);
        check(!found(id, world, values), "Short columns do not satisfy longer thresholds");
        values.set(id, "minimum-length", 4);
        world = new Fixture(AIR).put(0, 21, 0, STONE);
        for (int y = 17; y <= 20; y++) world.put(0, y, 0, plant("pointed_dripstone", "vertical_direction", "down"));
        marks = BlockFinderRules.inspect(id, 0, 20, 0, world, values);
        check(marks.size() == 1 && marks.getFirst().y() == 17, "Hanging stalactite box extends down from its actual root");
        values.set(id, "stalactites", false);
        check(!found(id, world, values), "Hanging and rising columns have independent filters");

        id = "vine-esp";
        world = new Fixture(AIR).put(0, 19, 0, STONE);
        for (int y = 20; y < 24; y++) world.put(0, y, 0, plant("vine"));
        marks = BlockFinderRules.inspect(id, 0, 20, 0, world, values);
        check(marks.size() == 1 && marks.getFirst().height() == 4, "Grounded vine column matches once at its base");
        world.put(0, 19, 0, AIR);
        check(!found(id, world, values), "Floating vines do not match ground-only mode");
        values.set(id, "grounded-only", false);
        check(found(id, world, values), "Floating vines can be deliberately included");
        world.put(0, 19, 0, Cell.UNKNOWN);
        check(!found(id, world, values), "Unknown vine base is never treated as a column end");
        world.put(0, 19, 0, STONE);
        for (int y = 20; y < 90; y++) world.put(0, y, 0, plant("vine"));
        marks = BlockFinderRules.inspect(id, 0, 20, 0, world, values);
        check(marks.getFirst().height() == 32, "Very long column checks and marker height stop at explicit bound");
    }

    private static void light() {
        String id = "light-esp";
        Values values = new Values();
        Fixture world = new Fixture();
        world.put(0, 20, 0, new Cell("minecraft:stone", true, false, true, false, 12, 0, Map.of()));
        check(!found(id, world, values), "Propagated light is excluded in source mode");
        values.set(id, "source-only", false);
        check(found(id, world, values), "Received propagated block light is available explicitly");
        values.set(id, "source-only", true);
        world.put(0, 20, 0, new Cell("minecraft:torch", true, false, false, false, 14, 14, Map.of()));
        check(found(id, world, values), "Actual light source uses luminance");
        values.set(id, "torches", false);
        check(!found(id, world, values), "Torch family opt-out applies");
        values.set(id, "torches", true);
        values.set(id, "minimum-light", 15);
        check(!found(id, world, values), "Luminance threshold excludes insufficient sources");
        values.set(id, "minimum-light", 8);
        world.put(0, 20, 0, new Cell("minecraft:lava", true, false, false, true, 15, 15, Map.of()));
        check(!found(id, world, values), "Natural source types can be suppressed");
        values.set(id, "include-natural", true);
        values.set(id, "thermal-colors", true);
        var marks = BlockFinderRules.inspect(id, 0, 20, 0, world, values);
        check(marks.size() == 1 && marks.getFirst().color() == 0xffff0000, "Maximum-light thermal color is an explicit opaque red override");
        world.put(0, 20, 0, AIR);
        check(!found(id, world, values), "Ordinary unlit air has no source or propagated-light evidence");
    }

    private static void unknownAndBudgets() {
        Values values = new Values();
        for (ModuleDefinition module : BlockFinderModules.definitions()) {
            check(BlockFinderRules.inspect(module.id(), 0, 20, 0, new Fixture(Cell.UNKNOWN), values).isEmpty(), "Unreceived target never matches " + module.id());
            check(!BlockFinderRules.candidate(module.id(), Cell.UNKNOWN), "Unknown palette cannot become candidate " + module.id());
            check(BlockFinderRules.inspect(module.id(), 0, -65, 0, new Fixture(AIR), values).isEmpty(), "Below-world cells never match " + module.id());
            check(BlockFinderRules.inspect(module.id(), 0, 320, 0, new Fixture(AIR), values).isEmpty(), "Above-world cells never match " + module.id());
        }
        Fixture world = new Fixture(STONE).put(0, 20, 0, block("cobblestone"));
        for (int y = 0; y < 20; y++) world.put(0, y, 0, AIR);
        BlockFinderRules.inspect("covered-hole-esp", 0, 20, 0, world, values);
        check(world.reads <= 170, "Maximum-depth shaft performs a bounded small neighborhood probe");
        Fixture tunnel = new Fixture(STONE);
        for (int x = 0; x < 100; x++) tunnel.put(x, 20, 0, AIR).put(x, 21, 0, AIR);
        var marks = BlockFinderRules.inspect("hole-tunnel-stairs-esp", 0, 20, 0, tunnel, values);
        check(marks.stream().anyMatch(mark -> mark.width() == 32), "Long tunnel is capped at 32 observed cells");
        check(tunnel.reads <= 512, "Combined passage probe stays under 512 local lookups");
        RuntimeException exhausted = new RuntimeException("fixture lookup budget");
        Grid exhaustedGrid = new Grid() {
            public Cell cell(int x, int y, int z) { throw exhausted; }
            public int bottomY() { return -64; }
            public int topY() { return 320; }
        };
        try {
            BlockFinderRules.inspect("vine-esp", 0, 20, 0, exhaustedGrid, values);
            throw new AssertionError("Lookup budget must propagate");
        } catch (RuntimeException actual) {
            check(actual == exhausted, "Scanner-owned lookup budget reaches its owner without suppression");
        }
    }

    private static boolean found(String id, Fixture world, Values values) { return !BlockFinderRules.inspect(id, 0, 20, 0, world, values).isEmpty(); }
    private static Cell block(String id, String... properties) { return cell(id, true, properties); }
    private static Cell plant(String id, String... properties) { return cell(id, false, properties); }
    private static Cell cell(String id, boolean solid, String... properties) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < properties.length; i += 2) map.put(properties[i], properties[i + 1]);
        return new Cell("minecraft:" + id, true, false, solid, false, 0, 0, Map.copyOf(map));
    }
    private static void check(boolean actual, String description) { assertions++; if (!actual) throw new AssertionError(description); }

    private static final class Fixture implements Grid {
        private final Map<String, Cell> cells = new HashMap<>();
        private final Cell fallback;
        private int reads;
        Fixture() { this(AIR); }
        Fixture(Cell fallback) { this.fallback = fallback; }
        Fixture put(int x, int y, int z, Cell cell) { cells.put(x + "/" + y + "/" + z, cell); return this; }
        public Cell cell(int x, int y, int z) { reads++; return cells.getOrDefault(x + "/" + y + "/" + z, fallback); }
        public int bottomY() { return -64; }
        public int topY() { return 320; }
    }

    private static final class Values implements Options {
        private final Map<String, ModuleDefinition> definitions = new HashMap<>();
        private final Map<String, Object> settings = new HashMap<>();
        Values() {
            for (ModuleDefinition module : BlockFinderModules.definitions()) {
                definitions.put(module.id(), module);
                for (Setting<?> setting : module.settings()) settings.put(module.id() + "/" + setting.id(), setting.defaultValue());
            }
        }
        void set(String module, String setting, Object value) { settings.put(module + "/" + setting, definitions.get(module).setting(setting).normalize(value)); }
        public boolean bool(String module, String setting) { return (Boolean) settings.get(module + "/" + setting); }
        public double number(String module, String setting) { return ((Number) settings.get(module + "/" + setting)).doubleValue(); }
        public String choice(String module, String setting) { return (String) settings.get(module + "/" + setting); }
    }
}
